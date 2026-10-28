"""Compile production directory validation/parser/cleanup against real Windows I/O."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class NativeUploadTreeTests(unittest.TestCase):
    @unittest.skipUnless(os.name=='nt','Actual Windows filename comparison and directory ownership')
    def test_manifest_and_cleanup_reject_ambiguous_paths_without_partial_deletion(self):
        web=(dev.ENV/'native-source/lib/webview/webview/webview.h').read_text(encoding='utf-8')
        start=web.index('inline std::wstring widen_string(')
        strings=web[start:web.index('\n#endif',start)]
        start=web.index('inline int json_parse_c(')
        parser=web[start:web.index('// Holds a symbol name',start)]
        upload=(dev.ROOT/'host/native-upload.inc.hpp').read_text(encoding='utf-8')
        finder=upload[upload.index('struct xb_upload_finder{'):upload.index('static bool xb_upload_uint(')]
        validation=upload[upload.index('static bool xb_upload_uint('):upload.index('static bool xb_upload_decode(')]
        paths=upload[upload.index('static bool xb_upload_remove_file('):upload.index('static bool xb_upload_private_root(')]
        program=r'''
#define NOMINMAX
#include <windows.h>
#include <cstdint>
#include <string>
#include <vector>
#include <algorithm>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cassert>
#include <new>
#include <utility>
static long failAt=-1,attempts=0,liveAllocations=0;static bool triggered=false;
void* operator new(std::size_t size){if(failAt>=0&&attempts++==failAt){triggered=true;throw std::bad_alloc();}auto p=std::malloc(size?size:1);if(!p)throw std::bad_alloc();liveAllocations++;return p;}
void operator delete(void* p) noexcept{if(p){liveAllocations--;std::free(p);}}
void operator delete(void* p,std::size_t) noexcept{::operator delete(p);}
void* operator new[](std::size_t n){return ::operator new(n);}
void operator delete[](void* p) noexcept{::operator delete(p);}
void operator delete[](void* p,std::size_t) noexcept{::operator delete(p);}
namespace webview{namespace detail{
'''+strings+parser+r'''
}}
static std::string xb_get(const std::string& s,const char* key){return webview::detail::json_parse(s,key,0);}
static std::string xb_quote(const std::string& s){return webview::detail::json_escape(s);}
'''+finder+validation+paths+(dev.ROOT/'host/native-upload-tree.inc.hpp').read_text(encoding='utf-8')
        program+=(dev.ROOT/'tests/fixtures/upload-tree-checks.cpp').read_text(encoding='utf-8')
        output=dev.ENV/'qa';output.mkdir(exist_ok=True);fixture=output/'native-upload-tree-unit'
        self.assertFalse(fixture.exists(),'Preserve an interrupted/unknown native tree fixture')
        cpp=output/'native-upload-tree.cpp';binary=output/'native-upload-tree.exe'
        cpp.write_text(program,encoding='utf-8',newline='\n');compiler=Path('E:/software/w64devkit/bin/g++.exe')
        built=subprocess.run([str(compiler),'-std=c++17','-O2','-municode','-static-libstdc++','-static-libgcc',str(cpp),'-o',str(binary)],capture_output=True,text=True)
        self.assertEqual(built.returncode,0,built.stderr)
        result=subprocess.run([str(binary),str(fixture)],capture_output=True,text=True,
            env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertFalse(fixture.exists(),'Native tree gate left owned data behind')
        self.assertIn('closed all enumeration handles',result.stdout)
        (dev.ENV/'native-upload-tree-unit.txt').write_text(result.stdout,encoding='utf-8')


if __name__=='__main__':unittest.main()
