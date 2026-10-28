"""Exercise the production cache-root normalizer and real extended Windows I/O."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class NativeUploadPathTests(unittest.TestCase):
    @unittest.skipUnless(os.name=='nt','Native Windows namespace and long-path I/O')
    def test_cache_namespace_preserves_names_and_round_trips_long_native_paths(self):
        source=(dev.ROOT/'host/native-upload.inc.hpp').read_text(encoding='utf-8')
        start=source.index('static std::wstring xb_upload_cache_path(')
        helper=source[start:source.index('static bool xb_upload_private_root(',start)]
        program=r'''
#define NOMINMAX
#include <windows.h>
#include <string>
#include <algorithm>
#include <cstring>
'''+helper+r'''
int wmain(int argc,wchar_t** argv){
  if(argc!=2)return 1;
  const std::wstring root=xb_upload_cache_path(argv[1]);
  if(root.rfind(L"\\\\?\\",0)!=0||xb_upload_cache_path(root)!=root)return 2;
  if(xb_upload_cache_path(L"\\\\server\\share\\folder")!=L"\\\\?\\UNC\\server\\share\\folder")return 3;
  if(!xb_upload_cache_path(L"\\\\.\\NUL").empty()||!xb_upload_cache_path(L"").empty())return 4;
  std::wstring nul=L"C:\\safe";nul.push_back(0);nul+=L"tail";
  if(!xb_upload_cache_path(nul).empty()||!xb_upload_cache_path(std::wstring(32767,L'x')).empty())return 5;
  const std::wstring directory=root+L"\\"+std::wstring(180,L'd');
  const std::wstring paths[]={directory+L"\\"+std::wstring(240,L'n'),directory+L"\\"+std::wstring(78,L'\u6587')+L".txt"};
  if(paths[0].size()<=260||paths[1].size()<=260)return 6;
  bool rootOwned=false,directoryOwned=false,owned[2]={false,false};HANDLE file=INVALID_HANDLE_VALUE;int result=0;
  if(!CreateDirectoryW(root.c_str(),nullptr)){result=10;goto cleanup;}rootOwned=true;
  if(!CreateDirectoryW(directory.c_str(),nullptr)){result=11;goto cleanup;}directoryOwned=true;
  for(unsigned i=0;i<2;i++){
    file=CreateFileW(paths[i].c_str(),GENERIC_READ|GENERIC_WRITE,FILE_SHARE_READ,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL|FILE_FLAG_OPEN_REPARSE_POINT,nullptr);
    if(file==INVALID_HANDLE_VALUE){result=12;goto cleanup;}owned[i]=true;
    {DWORD count=0;char buffer[6]{};LARGE_INTEGER zero{};
      if(!WriteFile(file,"bytes",5,&count,nullptr)||count!=5||!SetFilePointerEx(file,zero,nullptr,FILE_BEGIN)||
         !ReadFile(file,buffer,5,&count,nullptr)||count!=5||std::memcmp(buffer,"bytes",5)){result=13;goto cleanup;}}
    if(!CloseHandle(file)){file=INVALID_HANDLE_VALUE;result=14;goto cleanup;}file=INVALID_HANDLE_VALUE;
    if(GetFileAttributesW(paths[i].c_str())==INVALID_FILE_ATTRIBUTES){result=15;goto cleanup;}
  }
cleanup:
  if(file!=INVALID_HANDLE_VALUE)CloseHandle(file);
  // Only entries created by this invocation are removed; no recursive deletion.
  for(unsigned i=0;i<2;i++)if(owned[i]&&!DeleteFileW(paths[i].c_str()))result=20;
  if(directoryOwned&&!RemoveDirectoryW(directory.c_str()))result=21;
  if(rootOwned&&!RemoveDirectoryW(root.c_str()))result=22;
  return result;
}
'''
        output=dev.ENV/'qa';output.mkdir(exist_ok=True);fixture=output/'native-upload-long-paths'
        self.assertFalse(fixture.exists(),'Preserve an interrupted or unknown native path fixture')
        cpp=output/'native-upload-paths.cpp';binary=output/'native-upload-paths.exe'
        cpp.write_text(program,encoding='utf-8',newline='\n');compiler=Path('E:/software/w64devkit/bin/g++.exe')
        built=subprocess.run([str(compiler),'-std=c++17','-O2','-municode',str(cpp),'-o',str(binary)],capture_output=True,text=True)
        self.assertEqual(built.returncode,0,built.stderr)
        result=subprocess.run([str(binary),str(fixture)],capture_output=True,text=True,
            env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertFalse(fixture.exists(),'Native long-path test left owned entries behind')


if __name__=='__main__':unittest.main()
