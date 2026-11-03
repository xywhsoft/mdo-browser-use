"""Production SDK terminal capture: exact identity, retained lifetime, one sink."""
import os
from pathlib import Path
import subprocess
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


@unittest.skipUnless(os.name=='nt' and Path('E:/software/w64devkit/bin/gcc.exe').is_file(),'Windows C compiler unavailable')
class ToolTerminalBoundaryTests(unittest.TestCase):
    def test_terminal_identity_lifetime_and_persistence_failure(self):
        directory=dev.ENV/'qa/tool-outcome';directory.mkdir(parents=True,exist_ok=True)
        source=directory/'terminal.c';binary=directory/'terminal.exe'
        prefix=r'''
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>
typedef uint32_t uint32;typedef _Atomic(uint32) xatomic32;typedef const char* cstr;
#define XMEMORY_ACQUIRE memory_order_acquire
#define XMEMORY_RELEASE memory_order_release
#define XMEMORY_ACQ_REL memory_order_acq_rel
#define xrtAtomic32Init atomic_init
#define xrtAtomic32Load atomic_load_explicit
#define xrtAtomic32Store atomic_store_explicit
#define xrtAtomic32FetchSub atomic_fetch_sub_explicit
#define xrtAtomic32CompareExchange atomic_compare_exchange_strong_explicit
static unsigned live,checks,publishes;static bool failAllocation,failSink;
static void* xrtCalloc(size_t n,size_t size){if(failAllocation)return NULL;void* p=calloc(n,size);if(p)live++;return p;}
static void xrtFree(void* p){if(p){live--;free(p);}}
typedef enum {XWORK_HOOK_BEFORE_TOOL,XWORK_HOOK_AFTER_TOOL} xwork_hook_phase;
typedef enum {XWORK_HOOK_CONTINUE} xwork_hook_action;
typedef struct {xwork_hook_phase ePhase;} xwork_hook_event;
typedef struct {bool* Cancel;} MdoApplicationInvocation;
typedef struct {void* Opaque;uint64_t Deadline;} mdo_sdk_context_v1;
typedef struct {char sMessage[512];} xwork_error;
typedef struct {uint32 Size;char Code[64],Message[512],Cause[1024];unsigned Status;bool MayHaveCompleted;} mdo_error_v1;
static bool xrtCancelRequested(bool* cancel){return *cancel;}
static bool MdoDeadlineExpired(uint64_t deadline){return deadline<=100;}
static int64_t MdoUtcUs(void){return 234567;}
static bool MdoApplicationError(mdo_error_v1* E,cstr Code,cstr Message,cstr Stage,unsigned Status,bool Possible){
 (void)Stage;if(E){memset(E,0,sizeof(*E));snprintf(E->Code,sizeof(E->Code),"%s",Code);snprintf(E->Message,sizeof(E->Message),"%s",Message);E->Status=Status;E->MayHaveCompleted=Possible;}return false;}
#define XWORK_EVENT_SCHEMA_VERSION 3
enum {XWORK_EVENT_TOOL_START=5,XWORK_EVENT_TOOL_DONE=6};
typedef struct {unsigned eKind,uAgentDepth,uSchemaVersion;uint64_t uAgentId,uRunId,uAgentTurn,uParentRunId,uTaskId,uDelegationId,uParentAgentTurn,uEffects;
 cstr sToolCallId,sToolName,sText;size_t iTextLength;int64_t iOccurredAtUs;bool bSuccess,bEffectApplied;} xwork_event;
typedef struct {int unused;} MdoAgentSession;
static bool MdoAgentSessionRecordToolFailure(MdoAgentSession*,const xwork_event*,xwork_error*);
static void check(bool value){checks++;if(!value){fprintf(stderr,"Terminal check %u failed\n",checks);exit(1);}}
'''
        program=r'''
static BuToolTerminal* owner;static xwork_event captured;static char message[768];
static bool MdoAgentSessionRecordToolFailure(MdoAgentSession* agent,const xwork_event* event,xwork_error* error){
 (void)agent;publishes++;if(failSink){snprintf(error->sMessage,sizeof(error->sMessage),"sink failed");return false;}
 captured=*event;snprintf(message,sizeof(message),"%s",event->sText);return BuToolTerminalEvent(owner,event);
}
int main(void){
 BuToolBoundary* boundary=BuToolBoundaryCreate();check(boundary&&live==1);
 failAllocation=true;check(!BuToolTerminalCreate(boundary,"call","tool")&&live==1);failAllocation=false;
 check(!BuToolTerminalCreate(boundary,"","tool"));check(!BuToolTerminalCreate(boundary,"call",NULL));
 char longId[258];memset(longId,'a',sizeof(longId));check(!BuToolTerminalCreate(boundary,longId,"tool"));
 char call[]="call",tool[]="tool";owner=BuToolTerminalCreate(boundary,call,tool);check(owner&&live==2);
 call[0]='x';tool[0]='x';check(!strcmp(owner->Call,"call")&&!strcmp(owner->Tool,"tool"));
 check(BuToolTerminalRetain(owner));BuToolTerminalRelease(owner);check(live==2);
 bool cancel=true;MdoApplicationInvocation invocation={&cancel};mdo_sdk_context_v1 context={&invocation,0};xwork_error error={"original error"};MdoAgentSession agent;
 check(BuToolTerminalFinish(owner,&agent,&context,&error)&&publishes==0);
 xwork_event start={.eKind=XWORK_EVENT_TOOL_START,.uAgentId=7,.uRunId=0,.uAgentTurn=2,.uEffects=64,.sToolCallId="call",.sToolName="tool",.sText="borrowed"};
 xwork_event foreign=start;foreign.sToolCallId="foreign";check(BuToolTerminalEvent(owner,&foreign));
 foreign=start;foreign.uAgentDepth=1;check(BuToolTerminalEvent(owner,&foreign));
 check(BuToolTerminalFinish(owner,&agent,&context,&error)&&publishes==0);
 check(BuToolTerminalEvent(owner,&start));foreign=start;foreign.eKind=XWORK_EVENT_TOOL_DONE;foreign.uAgentId=8;check(BuToolTerminalEvent(owner,&foreign));
 check(BuToolTerminalFinish(owner,&agent,&context,&error)&&publishes==1);
 check(captured.eKind==XWORK_EVENT_TOOL_DONE&&!captured.bSuccess&&!captured.bEffectApplied);
 check(captured.uAgentId==7&&captured.uRunId==0&&captured.uAgentTurn==2&&captured.uEffects==64);
 check(!strcmp(captured.sToolCallId,"call")&&!strcmp(captured.sToolName,"tool"));
 check(strstr(message,"cancelled before execution")&&!strstr(message,"may have occurred"));
 check(BuToolTerminalFinish(owner,&agent,&context,&error)&&publishes==1);
 BuToolTerminalRelease(owner);check(live==1);
 owner=BuToolTerminalCreate(boundary,"call","tool");check(BuToolTerminalEvent(owner,&start));
 xwork_hook_event hook={XWORK_HOOK_BEFORE_TOOL};BuToolBoundaryHook(boundary,&hook);
 failSink=true;check(!BuToolTerminalFinish(owner,&agent,&context,&error)&&publishes==2);
 check(!strcmp(error.sMessage,"sink failed"));failSink=false;
 check(!BuToolTerminalFinish(owner,&agent,&context,&error)&&publishes==2);
 BuToolTerminalRelease(owner);
 owner=BuToolTerminalCreate(boundary,"call","tool");check(BuToolTerminalEvent(owner,&start));
 cancel=false;context.Deadline=99;check(BuToolTerminalFinish(owner,&agent,&context,&error)&&publishes==3);
 check(strstr(message,"deadline expired")&&strstr(message,"effects may have occurred"));
 BuToolTerminalRelease(owner);
 owner=BuToolTerminalCreate(boundary,"call","tool");check(BuToolTerminalEvent(owner,&start));
 xwork_event done=start;done.eKind=XWORK_EVENT_TOOL_DONE;done.bSuccess=true;check(BuToolTerminalEvent(owner,&done));
 check(BuToolTerminalFinish(owner,&agent,&context,&error)&&publishes==3);
 BuToolBoundaryRelease(boundary);check(live==2); // retained owner keeps hook storage alive.
 check(BuToolTerminalEvent(owner,NULL));check(BuToolTerminalEvent(NULL,&done));
 check(!BuToolTerminalRetain(NULL));BuToolTerminalRelease(owner);check(live==0);
 printf("{\"passed\":true,\"checks\":%u,\"terminal_writes\":%u}\n",checks,publishes);return 0;
}
'''
        source.write_text(prefix+(dev.ROOT/'host/tool-outcome.inc.c').read_text()+
                          (dev.ROOT/'host/tool-terminal.inc.c').read_text()+program,encoding='utf-8',newline='\n')
        build=subprocess.run(['E:/software/w64devkit/bin/gcc.exe','-std=c11','-O1','-Wall','-Wextra','-Werror',
            '-Wno-misleading-indentation',str(source),'-o',str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=45)
        self.assertEqual(build.returncode,0,build.stderr[-2500:])
        run=subprocess.run([str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=10)
        self.assertEqual(run.returncode,0,run.stderr[-1600:]);print(run.stdout)


if __name__=='__main__':unittest.main()
