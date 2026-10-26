"""Compile the actual bounded byte decoder used by native browser staging."""
import base64
import os
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import dev


class NativeUploadPolicyTests(unittest.TestCase):
    def test_owned_native_resources_unwind_once_after_allocation_failure(self):
        source=(dev.ROOT/"host/native-upload.inc.hpp").read_text(encoding="utf-8")
        owned=source[source.index("struct xb_upload_handle{"):source.index("static bool xb_upload_uint(")]
        owned+=source[source.index("struct xb_upload_bytes:"):source.index("struct xb_upload_guard{")]
        program=r'''
#include <cstdint>
#include <cstring>
#include <new>
#include <type_traits>
#include <vector>
using HANDLE=void*;using HLOCAL=void*;using HCRYPTPROV=uintptr_t;using HCRYPTHASH=uintptr_t;
#define INVALID_HANDLE_VALUE ((void*)(intptr_t)-1)
static unsigned order[8]{},count=0,zeroed=0;
static bool CloseHandle(HANDLE value){order[count++]=reinterpret_cast<uintptr_t>(value);return true;}
static void LocalFree(HLOCAL value){order[count++]=reinterpret_cast<uintptr_t>(value);}
static void CryptReleaseContext(HCRYPTPROV value,unsigned){order[count++]=value;}
static void CryptDestroyHash(HCRYPTHASH value){order[count++]=value;}
static void FindClose(HANDLE value){order[count++]=reinterpret_cast<uintptr_t>(value);}
static void SecureZeroMemory(void* bytes,size_t size){zeroed+=size;std::memset(bytes,0,size);}
'''+owned+r'''
static_assert(!std::is_copy_constructible<xb_upload_handle>::value);
static_assert(!std::is_copy_constructible<xb_upload_local>::value);
static_assert(!std::is_copy_constructible<xb_upload_provider>::value);
static_assert(!std::is_copy_constructible<xb_upload_hasher>::value);
static_assert(!std::is_copy_constructible<xb_upload_finder>::value);
int main(){
  {xb_upload_handle a;xb_upload_local b(nullptr);xb_upload_provider c;xb_upload_hasher d;xb_upload_finder e;}
  if(count)return 1;
  try{
    xb_upload_handle a;a.value=(void*)1;xb_upload_local b((void*)2);
    xb_upload_provider c;c.value=3;xb_upload_hasher d;d.value=4;xb_upload_finder e;e.value=(void*)5;
    xb_upload_bytes bytes(3,127);throw std::bad_alloc();
  }catch(const std::bad_alloc&){}
  if(count!=5||zeroed!=3)return 2;
  for(unsigned i=0;i<5;i++)if(order[i]!=5-i)return 3;
  return 0;
}
'''
        directory=dev.ENV/"qa";directory.mkdir(exist_ok=True)
        cpp=directory/"native-upload-ownership.cpp";binary=directory/"native-upload-ownership.exe"
        cpp.write_text(program,encoding="utf-8",newline="\n")
        compiler=Path("E:/software/w64devkit/bin/g++.exe")
        subprocess.run([str(compiler),"-std=c++17","-O2","-s",str(cpp),"-o",str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get("PATH","")),check=True,capture_output=True)

    def test_actual_base64_and_integer_bounds(self):
        source=(dev.ROOT/"host/native-upload.inc.hpp").read_text(encoding="utf-8")
        functions=source[source.index("static bool xb_upload_uint("):source.index("static bool xb_upload_name(")]
        functions+=source[source.index("static bool xb_upload_hash("):source.index("static bool xb_upload_remove_file(")]
        program='#include <cstdint>\n#include <string>\n#include <vector>\n'+functions+r'''
int main(){
  uint64_t number=0;std::vector<unsigned char> data;
  if(!xb_upload_uint("18446744073709551615",number)||number!=UINT64_MAX)return 1;
  for(auto text:{"","-1","+1","1.0","1x","18446744073709551616"})if(xb_upload_uint(text,number))return 2;
  if(!xb_upload_decode("ALL_BYTES",data)||data.size()!=256)return 3;
  for(unsigned i=0;i<256;i++)if(data[i]!=i)return 4;
  for(auto text:{"","Zg=","Zg===","Zg==\n","Z g=","Zh==","Zm9=","=m9v","Zm=v","Zm9v====","!!!!"})if(xb_upload_decode(text,data))return 5;
  if(!xb_upload_decode("Zg==",data)||data!=std::vector<unsigned char>{102})return 6;
  if(!xb_upload_decode("Zm8=",data)||data!=std::vector<unsigned char>{102,111})return 7;
  if(!xb_upload_decode("LIMIT_BYTES",data)||data.size()!=65536)return 8;
  if(xb_upload_decode("OVER_BYTES",data))return 9;
  if(!xb_upload_hash(std::string(64,'a'))||xb_upload_hash(std::string(64,'A'))||xb_upload_hash(std::string(63,'a')))return 10;
  return 0;
}
'''
        for name,data in (("ALL_BYTES",bytes(range(256))),("LIMIT_BYTES",b"x"*65536),("OVER_BYTES",b"x"*65537)):
            program=program.replace(name,base64.b64encode(data).decode())
        directory=dev.ENV/"qa";directory.mkdir(exist_ok=True)
        cpp=directory/"native-upload-policy.cpp";binary=directory/"native-upload-policy.exe"
        cpp.write_text(program,encoding="utf-8",newline="\n")
        compiler=Path("E:/software/w64devkit/bin/g++.exe")
        subprocess.run([str(compiler),"-std=c++17","-O2","-s",str(cpp),"-o",str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get("PATH","")),check=True,capture_output=True)


if __name__=="__main__":unittest.main()
