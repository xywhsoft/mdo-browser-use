"""Exercise the real pre-execution gate with controlled runtime retirement."""
import os
from pathlib import Path
import subprocess
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev

class ToolAdmissionTests(unittest.TestCase):
    def test_runtime_busy_wait_is_bounded_cancelled_and_never_reexecutes(self):
        source=(dev.ROOT/'host/tool-admission.inc.c').read_text(encoding='utf-8')
        program=r'''
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cassert>
#include <initializer_list>
using uint64=uint64_t;using cstr=const char*;
enum{XWORK_ERROR_CONTEXT=1,XWORK_ERROR_TIMEOUT=2,XWORK_ERROR_CANCELLED=3,XWORK_ERROR_IO=4};
struct xwork_error{int eCode=0;char sMessage[100]{};};struct mdo_sdk_context_v1{uint64 Deadline=0;};
struct MdoSession{};struct MdoSessionRuntimeOptions{};
static uint64 now=0,readyAt=0,cancelAt=UINT64_MAX;static int attempts=0,mode=0;static MdoSession owned;
static uint64 MdoDeadlineAfterUs(uint64 us){return now+us;}
static bool MdoDeadlineExpired(uint64 end){return now>=end;}
static bool SdkCancelled(const mdo_sdk_context_v1* c){return now>=cancelAt||(c->Deadline&&MdoDeadlineExpired(c->Deadline));}
static void xrtSleep(unsigned ms){now+=ms*1000;}
static void xworkErrorInit(xwork_error* e){*e={};}
static MdoSession* MdoSessionOpen(cstr project,cstr session,const MdoSessionRuntimeOptions* options,xwork_error* e){
  assert(!strcmp(project,"original-project")&&!strcmp(session,"original-task")&&options);attempts++;xworkErrorInit(e);
  if(mode==1){e->eCode=XWORK_ERROR_IO;strcpy(e->sMessage,"disk failure");return nullptr;}
  if(mode==2){e->eCode=XWORK_ERROR_CONTEXT;strcpy(e->sMessage,"session changed while opening its runtime");return nullptr;}
  if(now>=readyAt)return &owned;e->eCode=XWORK_ERROR_CONTEXT;strcpy(e->sMessage,"session already has an active runtime");return nullptr;
}
static void reset(){now=0;readyAt=0;cancelAt=UINT64_MAX;attempts=mode=0;}
'''+source+r'''
int main(){
  mdo_sdk_context_v1 c;MdoSessionRuntimeOptions options;xwork_error error;
  assert(BuToolSessionOpen(&c,"original-project","original-task",&options,&error)==&owned&&attempts==1);
  reset();readyAt=60000;assert(BuToolSessionOpen(&c,"original-project","original-task",&options,&error)==&owned&&attempts==7&&now==60000);
  reset();readyAt=1000000;assert(!BuToolSessionOpen(&c,"original-project","original-task",&options,&error)&&now==500000&&attempts==51&&error.eCode==XWORK_ERROR_CONTEXT);
  for(int failure:{1,2}){reset();mode=failure;assert(!BuToolSessionOpen(&c,"original-project","original-task",&options,&error)&&attempts==1&&now==0);}
  reset();readyAt=1000000;cancelAt=20000;assert(!BuToolSessionOpen(&c,"original-project","original-task",&options,&error)&&attempts==2&&error.eCode==XWORK_ERROR_CANCELLED);
  reset();readyAt=1000000;c.Deadline=30000;assert(!BuToolSessionOpen(&c,"original-project","original-task",&options,&error)&&attempts==3&&error.eCode==XWORK_ERROR_TIMEOUT);
  reset();c.Deadline=0;cancelAt=0;assert(!BuToolSessionOpen(&c,"original-project","original-task",&options,&error)&&attempts==0&&error.eCode==XWORK_ERROR_CANCELLED);
}
'''
        directory=dev.ENV/'qa';directory.mkdir(exist_ok=True);cpp=directory/'tool-admission.cpp';binary=directory/'tool-admission.exe'
        cpp.write_text(program,encoding='utf-8',newline='\n');compiler=Path('E:/software/w64devkit/bin/g++.exe')
        subprocess.run([str(compiler),'-std=c++17','-O2','-s',str(cpp),'-o',str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],check=True,capture_output=True,env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))

if __name__=='__main__':unittest.main()
