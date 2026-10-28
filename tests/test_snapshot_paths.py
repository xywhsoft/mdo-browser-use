"""Compile the production C snapshot path adapter with bounded allocation seams."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class SnapshotPathTests(unittest.TestCase):
    def test_windows_namespace_policy_and_all_adapter_allocation_failures(self):
        source=(dev.ROOT/'host/files-digest.inc.c').read_text(encoding='utf-8')
        adapter=source[source.index('static bool BuFileSnapshotPath('):source.index('static bool BuFileSame(')]
        program=r'''
#define _WIN32 1
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <assert.h>
typedef const char* cstr;typedef struct{const char* code;}mdo_error_v1;
static int fail=-1,attempts=0,live=0;static const char* absolute;
static void* allocation(size_t size){if(fail>=0&&attempts++==fail)return NULL;void* p=malloc(size);assert(p);live++;return p;}
static void xrtFree(void* p){if(p){live--;free(p);}}
static char* xrtPathAbs(cstr path){(void)path;size_t n=strlen(absolute)+1;char* p=allocation(n);if(p)memcpy(p,absolute,n);return p;}
static char* xrtFormat(cstr format,...){va_list args;va_start(args,format);char buffer[8192];int n=vsnprintf(buffer,sizeof(buffer),format,args);va_end(args);assert(n>0&&n<(int)sizeof(buffer));char* p=allocation((size_t)n+1);if(p)memcpy(p,buffer,(size_t)n+1);return p;}
static bool SdkNativeFailure(mdo_error_v1* e,cstr area){(void)area;e->code="native_failed";return false;}
static bool MdoApplicationError(mdo_error_v1* e,cstr code,cstr message,cstr area,int status,bool effect){(void)message;(void)area;(void)status;assert(!effect);e->code=code;return false;}
'''+adapter+r'''
int main(void){
  char source[600],*owned=NULL;mdo_error_v1 error={0};memset(source,'n',sizeof(source)-1);source[sizeof(source)-1]=0;
  assert(BuFileSnapshotPath("C:/short",&owned,&error)&&!owned&&!live);
  absolute="D:/original/long-source";assert(BuFileSnapshotPath(source,&owned,&error));
  assert(!strcmp(owned,"\\\\?\\D:\\original\\long-source"));xrtFree(owned);assert(!live);
  absolute="//server/share/selected";assert(BuFileSnapshotPath(source,&owned,&error));
  assert(!strcmp(owned,"\\\\?\\UNC\\server\\share\\selected"));xrtFree(owned);assert(!live);
  for(int fault=0;fault<2;fault++){fail=fault;attempts=0;owned=(char*)1;error.code=NULL;
    assert(!BuFileSnapshotPath(source,&owned,&error)&&owned==NULL&&error.code&&!live);}
  fail=-1;memcpy(source,"\\\\?\\D:\\",7);assert(BuFileSnapshotPath(source,&owned,&error)&&owned==NULL&&!live);
  memcpy(source,"\\\\.\\D:\\",7);assert(!BuFileSnapshotPath(source,&owned,&error)&&owned==NULL&&!strcmp(error.code,"sdk_arguments_invalid")&&!live);
  return 0;
}
'''
        output=dev.ENV/'qa';output.mkdir(exist_ok=True)
        cpp=output/'snapshot-paths.c';binary=output/'snapshot-paths.exe';cpp.write_text(program,encoding='utf-8',newline='\n')
        compiler=Path('E:/software/w64devkit/bin/gcc.exe')
        built=subprocess.run([str(compiler),'-std=c11','-O2','-Wall','-Wextra',str(cpp),'-o',str(binary)],capture_output=True,text=True)
        self.assertEqual(built.returncode,0,built.stderr)
        result=subprocess.run([str(binary)],capture_output=True,text=True,env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))
        self.assertEqual(result.returncode,0,result.stderr)
