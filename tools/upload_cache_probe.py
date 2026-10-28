"""Inspect the real Windows cache ACL without logging user identities or bytes."""
import ctypes
from ctypes import wintypes
from contextlib import contextmanager
from pathlib import Path


def inspect_cache(root: Path):
    advapi=ctypes.WinDLL("advapi32",use_last_error=True);kernel=ctypes.WinDLL("kernel32",use_last_error=True)
    pointer=ctypes.c_void_p;out=ctypes.POINTER(pointer)
    advapi.GetNamedSecurityInfoW.argtypes=[wintypes.LPWSTR,wintypes.DWORD,wintypes.DWORD,out,out,out,out,out]
    advapi.GetNamedSecurityInfoW.restype=wintypes.DWORD
    advapi.OpenProcessToken.argtypes=[wintypes.HANDLE,wintypes.DWORD,ctypes.POINTER(wintypes.HANDLE)]
    advapi.GetTokenInformation.argtypes=[wintypes.HANDLE,wintypes.DWORD,pointer,wintypes.DWORD,ctypes.POINTER(wintypes.DWORD)]
    advapi.ConvertSidToStringSidW.argtypes=[pointer,ctypes.POINTER(wintypes.LPWSTR)]
    advapi.GetAce.argtypes=[pointer,wintypes.DWORD,out]
    advapi.GetSecurityDescriptorControl.argtypes=[pointer,ctypes.POINTER(wintypes.WORD),ctypes.POINTER(wintypes.DWORD)]
    kernel.GetCurrentProcess.restype=wintypes.HANDLE;kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    kernel.LocalFree.argtypes=[pointer];kernel.LocalFree.restype=pointer

    def sid_text(sid):
        text=wintypes.LPWSTR()
        if not advapi.ConvertSidToStringSidW(sid,ctypes.byref(text)):raise ctypes.WinError(ctypes.get_last_error())
        try:return text.value
        finally:kernel.LocalFree(ctypes.cast(text,pointer))

    token=wintypes.HANDLE()
    if not advapi.OpenProcessToken(kernel.GetCurrentProcess(),8,ctypes.byref(token)):raise ctypes.WinError(ctypes.get_last_error())
    try:
        size=wintypes.DWORD();advapi.GetTokenInformation(token,1,None,0,ctypes.byref(size))
        data=ctypes.create_string_buffer(size.value)
        if not advapi.GetTokenInformation(token,1,data,size,ctypes.byref(size)):raise ctypes.WinError(ctypes.get_last_error())
        current=sid_text(ctypes.cast(data,ctypes.POINTER(pointer))[0])
    finally:kernel.CloseHandle(token)
    descriptor=pointer();dacl=pointer()
    status=advapi.GetNamedSecurityInfoW(str(root.resolve()),1,4,None,None,ctypes.byref(dacl),None,ctypes.byref(descriptor))
    if status:raise ctypes.WinError(status)
    try:
        control=wintypes.WORD();revision=wintypes.DWORD()
        if not advapi.GetSecurityDescriptorControl(descriptor,ctypes.byref(control),ctypes.byref(revision)):raise ctypes.WinError(ctypes.get_last_error())
        if not dacl.value:raise RuntimeError("The native upload cache has a null access list")
        count=ctypes.c_ushort.from_address(dacl.value+4).value
        allowed=set()
        for index in range(count):
            ace=pointer()
            if not advapi.GetAce(dacl,index,ctypes.byref(ace)):raise ctypes.WinError(ctypes.get_last_error())
            kind=ctypes.c_ubyte.from_address(ace.value).value
            mask=ctypes.c_uint32.from_address(ace.value+4).value
            if kind!=0 or mask&0x1F01FF!=0x1F01FF:raise RuntimeError("Unexpected native upload cache access entry")
            allowed.add(sid_text(pointer(ace.value+8)))
        if not control.value&0x1000 or count!=2 or allowed!={current,"S-1-5-18"}:
            raise RuntimeError("The native upload cache is not restricted to the current user and SYSTEM")
    finally:kernel.LocalFree(descriptor)
    entries=[path for path in root.iterdir() if path.name!=".guard"]
    return {"private_acl":True,"remaining_reservations":len(entries)}


@contextmanager
def deny_child_directory_creation(root: Path):
    """Deny one native admission phase in this empty QA cache, then restore ACL.

    CI/IO applies FILE_ADD_SUBDIRECTORY (0x4) to a new GUID directory, not the
    cache itself. The GUID and its lease can be created; its numeric file
    directory fails after job registration. No rights are granted or broadened.
    The original descriptor stays in memory only.
    """
    workspace=Path(__file__).resolve().parents[1]
    expected=workspace/'.mdo/home/data/cache/webview2-content/mdo-browser-use-transfers'
    if root.resolve()!=expected or not root.resolve().is_relative_to(workspace/'.mdo'):
        raise RuntimeError('Refusing an ACL fault outside the exact private project QA cache')
    if inspect_cache(root)['remaining_reservations']:
        raise RuntimeError('Refusing to fault a cache with existing reservations')
    advapi=ctypes.WinDLL('advapi32',use_last_error=True);kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    pointer=ctypes.c_void_p;out=ctypes.POINTER(pointer)
    advapi.GetNamedSecurityInfoW.argtypes=[wintypes.LPWSTR,wintypes.DWORD,wintypes.DWORD,out,out,out,out,out]
    advapi.GetNamedSecurityInfoW.restype=wintypes.DWORD
    advapi.SetNamedSecurityInfoW.argtypes=[wintypes.LPWSTR,wintypes.DWORD,wintypes.DWORD,pointer,pointer,pointer,pointer]
    advapi.SetNamedSecurityInfoW.restype=wintypes.DWORD
    advapi.GetAce.argtypes=[pointer,wintypes.DWORD,out]
    advapi.ConvertSidToStringSidW.argtypes=[pointer,ctypes.POINTER(wintypes.LPWSTR)]
    advapi.ConvertStringSecurityDescriptorToSecurityDescriptorW.argtypes=[wintypes.LPCWSTR,wintypes.DWORD,out,pointer]
    advapi.GetSecurityDescriptorDacl.argtypes=[pointer,ctypes.POINTER(wintypes.BOOL),out,ctypes.POINTER(wintypes.BOOL)]
    kernel.LocalFree.argtypes=[pointer];kernel.LocalFree.restype=pointer
    original=pointer();dacl=pointer();fault=pointer();applied=False
    status=advapi.GetNamedSecurityInfoW(str(root),1,4,None,None,ctypes.byref(dacl),None,ctypes.byref(original))
    if status:raise ctypes.WinError(status)
    try:
        user=None
        # inspect_cache has verified exactly the current user and SYSTEM.
        for index in range(2):
            ace=pointer();sid=wintypes.LPWSTR()
            if not advapi.GetAce(dacl,index,ctypes.byref(ace)):raise ctypes.WinError(ctypes.get_last_error())
            if (ctypes.c_ubyte.from_address(ace.value).value!=0 or
                ctypes.c_ubyte.from_address(ace.value+1).value!=3 or
                ctypes.c_uint32.from_address(ace.value+4).value!=0x1F01FF):
                raise RuntimeError('Refusing to broaden a nonstandard QA cache ACL')
            if not advapi.ConvertSidToStringSidW(pointer(ace.value+8),ctypes.byref(sid)):raise ctypes.WinError(ctypes.get_last_error())
            try:
                if sid.value!='S-1-5-18':user=sid.value
            finally:kernel.LocalFree(ctypes.cast(sid,pointer))
        if not user:raise RuntimeError('Private QA cache user ACE is missing')
        sddl=f'D:P(D;CIIO;0x4;;;{user})(A;OICI;FA;;;SY)(A;OICI;FA;;;{user})'
        if not advapi.ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl,1,ctypes.byref(fault),None):
            raise ctypes.WinError(ctypes.get_last_error())
        present=wintypes.BOOL();defaulted=wintypes.BOOL();denied=pointer()
        if not advapi.GetSecurityDescriptorDacl(fault,ctypes.byref(present),ctypes.byref(denied),ctypes.byref(defaulted)) or not present.value:
            raise RuntimeError('Cannot prepare the bounded QA denial ACL')
        applied=True  # Also restore after an API error that may have propagated partially.
        status=advapi.SetNamedSecurityInfoW(str(root),1,0x80000004,None,None,denied,None)
        if status:raise ctypes.WinError(status)
        yield
    finally:
        restore=advapi.SetNamedSecurityInfoW(str(root),1,0x80000004,None,None,dacl,None) if applied else 0
        if fault:kernel.LocalFree(fault)
        if original:kernel.LocalFree(original)
        if restore:raise ctypes.WinError(restore)
    inspect_cache(root)  # The private, protected two-ACE ACL must be back.
