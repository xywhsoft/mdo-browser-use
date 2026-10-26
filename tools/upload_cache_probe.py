"""Inspect the real Windows cache ACL without logging user identities or bytes."""
import ctypes
from ctypes import wintypes
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
