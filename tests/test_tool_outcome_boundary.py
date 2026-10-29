"""Production hook ownership and conservative cancelled/deadline outcomes."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


@unittest.skipUnless(os.name=='nt' and Path('E:/software/w64devkit/bin/gcc.exe').is_file(),'Windows C compiler unavailable')
class ToolOutcomeBoundaryTests(unittest.TestCase):
    def test_retained_owner_hook_and_failure_classification(self):
        root=dev.ENV/'qa/tool-outcome';root.mkdir(parents=True,exist_ok=True)
        source=root/'boundary.c';binary=root/'boundary.exe'
        prefix=r'''
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdatomic.h>
typedef uint32_t uint32;typedef _Atomic(uint32) xatomic32;
#define XMEMORY_ACQUIRE memory_order_acquire
#define XMEMORY_RELEASE memory_order_release
#define XMEMORY_ACQ_REL memory_order_acq_rel
#define xrtAtomic32Init atomic_init
#define xrtAtomic32Load atomic_load_explicit
#define xrtAtomic32Store atomic_store_explicit
#define xrtAtomic32FetchSub atomic_fetch_sub_explicit
#define xrtAtomic32CompareExchange atomic_compare_exchange_strong_explicit
static unsigned allocations=0,checks=0;static bool allocationFails=false;
static void* xrtCalloc(size_t count,size_t size){if(allocationFails)return NULL;void* p=calloc(count,size);if(p)allocations++;return p;}
static void xrtFree(void* p){if(p){allocations--;free(p);}}
typedef enum {XWORK_HOOK_BEFORE_TOOL,XWORK_HOOK_AFTER_TOOL} xwork_hook_phase;
typedef enum {XWORK_HOOK_CONTINUE} xwork_hook_action;
typedef struct {xwork_hook_phase ePhase;} xwork_hook_event;
typedef struct {bool* Cancel;} MdoApplicationInvocation;
typedef struct {void* Opaque;uint64_t Deadline;} mdo_sdk_context_v1;
typedef struct {char sMessage[512];} xwork_error;
typedef struct {char Code[64],Message[1024],Cause[512];unsigned Status;bool MayHaveCompleted;} mdo_error_v1;
static bool xrtCancelRequested(bool* cancel){return *cancel;}
static bool MdoDeadlineExpired(uint64_t deadline){return deadline<=100;}
static bool MdoApplicationError(mdo_error_v1* e,const char* code,const char* message,const char* stage,unsigned status,bool possible){
 (void)stage;if(e){memset(e,0,sizeof(*e));snprintf(e->Code,sizeof(e->Code),"%s",code);snprintf(e->Message,sizeof(e->Message),"%s",message);e->Status=status;e->MayHaveCompleted=possible;}return false;
}
static void check(bool value){checks++;if(!value){fprintf(stderr,"Boundary check %u failed\n",checks);exit(1);}}
'''
        program=r'''
int main(void){
 allocationFails=true;check(BuToolBoundaryCreate()==NULL&&allocations==0);allocationFails=false;
 BuToolBoundary* boundary=BuToolBoundaryCreate();check(boundary&&allocations==1);check(BuToolBoundaryRetain(boundary));
 BuToolBoundaryRelease(boundary);check(allocations==1); // SDK caller released; a retained owner can still invoke the hook.
 bool cancel=true;MdoApplicationInvocation invocation={&cancel};mdo_sdk_context_v1 context={&invocation,0};
 xwork_error error={"original executor failure"};mdo_error_v1 outcome={0};
 check(!BuToolExecutionFailure(&context,boundary,false,&error,&outcome));
 check(outcome.Status==499&&!strcmp(outcome.Code,"sdk_cancelled")&&!outcome.MayHaveCompleted);
 check(strstr(outcome.Message,"before execution")&& !strcmp(outcome.Cause,error.sMessage));
 xwork_hook_event after={XWORK_HOOK_AFTER_TOOL},before={XWORK_HOOK_BEFORE_TOOL};
 check(BuToolBoundaryHook(boundary,&after)==XWORK_HOOK_CONTINUE);
 check(!BuToolExecutionFailure(&context,boundary,false,&error,&outcome)&&!outcome.MayHaveCompleted);
 check(BuToolBoundaryHook(boundary,&before)==XWORK_HOOK_CONTINUE);
 check(!BuToolExecutionFailure(&context,boundary,false,&error,&outcome)&&outcome.Status==499&&outcome.MayHaveCompleted);
 cancel=false;context.Deadline=99;
 check(!BuToolExecutionFailure(&context,boundary,false,&error,&outcome)&&outcome.Status==504&&outcome.MayHaveCompleted&&!strcmp(outcome.Code,"sdk_deadline_expired"));
 cancel=true;check(!BuToolExecutionFailure(&context,boundary,false,&error,&outcome)&&outcome.Status==499);
 cancel=false;context.Deadline=0;
 check(!BuToolExecutionFailure(&context,boundary,false,&error,&outcome)&&outcome.Status==409&&outcome.MayHaveCompleted&&!strcmp(outcome.Message,error.sMessage));
 BuToolBoundaryRelease(boundary);check(allocations==0);
 boundary=BuToolBoundaryCreate();check(!BuToolExecutionFailure(&context,boundary,true,&error,&outcome)&&outcome.MayHaveCompleted);
 context.Deadline=99;check(!BuToolExecutionFailure(&context,boundary,false,&error,&outcome)&&outcome.Status==504&&!outcome.MayHaveCompleted);
 check(!BuToolBoundaryRetain(NULL));check(BuToolBoundaryHook(NULL,&before)==XWORK_HOOK_CONTINUE);check(BuToolBoundaryHook(boundary,NULL)==XWORK_HOOK_CONTINUE);
 BuToolBoundaryRelease(boundary);check(allocations==0);
 printf("{\"passed\":true,\"checks\":%u}\n",checks);return 0;
}
'''
        source.write_text(prefix+(dev.ROOT/'host/tool-outcome.inc.c').read_text(encoding='utf-8')+program,encoding='utf-8',newline='\n')
        build=subprocess.run(['E:/software/w64devkit/bin/gcc.exe','-std=c11','-O1','-Wall','-Wextra','-Werror',
            '-Wno-misleading-indentation',str(source),'-o',str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=45)
        self.assertEqual(build.returncode,0,build.stderr[-2200:])
        run=subprocess.run([str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=10)
        self.assertEqual(run.returncode,0,run.stderr[-1600:]);print(run.stdout)


if __name__=='__main__':unittest.main()
