"""Exercise the actual snapshot helper during the published-info/worker gap."""
import os
from pathlib import Path
import subprocess
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev

class OperationSnapshotTests(unittest.TestCase):
    def test_runtime_completion_snapshot_matches_result_admission(self):
        source=(dev.ROOT/'host/operation-snapshot.inc.c').read_text(encoding='utf-8')
        program=r'''
#include <map>
#include <string>
#include <cassert>
using cstr=const char*;
#define XRT_STR_LITERAL(value) value
struct xvalue{std::map<std::string,std::string> fields;};
struct SdkJob{xvalue* Info;bool Terminal;};
static int failAt=-1;
static xvalue* xrtValueClone(const xvalue* v){return failAt==0?nullptr:new xvalue(*v);}
static xvalue* xrtValueBool(bool b){return new xvalue{{{"",b?"true":"false"}}};}
static xvalue* xrtValueString(cstr s){return new xvalue{{{"",s}}};}
static bool xrtValueObjectSetNew(xvalue* v,cstr key,xvalue* item){bool ok=failAt--!=1;if(ok)v->fields[key]=item->fields.at("");delete item;return ok;}
static void xrtValueRelease(xvalue* v){delete v;}
static cstr xrtStrView(cstr s){return s;}
static void xrtValueObjectRemove(xvalue* v,cstr key){v->fields.erase(key);}
'''+source+r'''
int main(){
  xvalue info{{{"id","original"},{"state","succeeded"},{"terminal","true"},{"result_available","true"},{"outcome","succeeded"},{"finished_us","123"},{"result_bytes","42"},{"error","final-error"},{"receipt_uncertain","true"}}};
  const auto before=info.fields;SdkJob job{&info,false};
  auto* pending=BuOperationSnapshot(&job);assert(pending&&pending->fields.at("terminal")=="false"&&pending->fields.at("result_available")=="false"&&pending->fields.at("state")=="running");
  assert(pending->fields.size()==4&&pending->fields.at("id")=="original"&&info.fields==before);delete pending;
  job.Terminal=true;auto* completed=BuOperationSnapshot(&job);assert(completed&&completed->fields==before);delete completed;
  job.Terminal=false;for(int fault=0;fault<4;fault++){failAt=fault;auto* denied=BuOperationSnapshot(&job);assert(!denied&&info.fields==before);}
}
'''
        directory=dev.ENV/'qa';directory.mkdir(exist_ok=True)
        cpp=directory/'operation-snapshot.cpp';binary=directory/'operation-snapshot.exe'
        cpp.write_text(program,encoding='utf-8',newline='\n');compiler=Path('E:/software/w64devkit/bin/g++.exe')
        subprocess.run([str(compiler),'-std=c++17','-O2','-s',str(cpp),'-o',str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],check=True,capture_output=True,env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))

if __name__=='__main__':unittest.main()
