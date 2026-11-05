#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
typedef uint16_t uint16;typedef uint32_t uint32;typedef uint64_t uint64;typedef int32_t int32;typedef const char* cstr;
typedef struct xvalue xvalue;typedef struct MdoAccountLease MdoAccountLease;
typedef enum {XERR_NONE,XERR_ARGUMENT,XERR_TYPE,XERR_VALUE,XERR_RANGE,XERR_STATE,XERR_MEMORY,XERR_IO,
    XERR_NOT_FOUND,XERR_EXISTS,XERR_PERMISSION,XERR_AGAIN,XERR_TIMEOUT,XERR_CANCELLED,XERR_CLOSED,
    XERR_PROTOCOL,XERR_UNSUPPORTED,XERR_INTERNAL} xerrkind;
typedef struct xerror {xerrkind kind;int code,system;const char* domain;struct xerror* cause;} xerror;
typedef struct {bool cancelled;} xcancel;
typedef struct {uint64 Timeout,IdleTimeout;xcancel* Cancel;} XS_FetchRequest;
typedef struct {int Status;} XS_FetchResponse;
#define XS_FETCH_ERROR_INCOMPLETE 6
#include "../../host/account-transport.h"
static uint64 now;static int calls,units,checks,success_at,cancel_at,wait_cancel;
static bool full_timeout;static xerror* error;static xerror failure;
static const xerror* xrtGetError(void){return error;}
static xerrkind xrtErrorKind(const xerror* e){return e->kind;}
static int xrtErrorCode(const xerror* e){return e->code;}
static int xrtErrorSystemCode(const xerror* e){return e->system;}
static cstr xrtErrorDomain(const xerror* e){return e->domain;}
static const xerror* xrtErrorCause(const xerror* e){return e->cause;}
static bool xrtCancelRequested(xcancel* c){return c->cancelled;}
static uint64 MdoMonotonicUs(void){return now;}
static uint64 MdoDeadlineAfterUs(uint64 delay){return now+delay;}
static uint64 MdoDeadlineRemainingUs(uint64 end){return now<end?end-now:0;}
static bool MdoDeadlineExpired(uint64 end){return now>=end;}
static xcancel cancel;
static void xrtSleep(uint32 ms){now+=(uint64)ms*1000;if(wait_cancel)cancel.cancelled=true;error=NULL;}
static void xrtClearError(void){error=NULL;}
static void xsFetchResponseUnit(XS_FetchResponse* r){units++;r->Status=0;error=NULL;}
static bool xsFetch(const XS_FetchRequest* r,XS_FetchResponse* out){
    calls++;assert(r->Timeout>0&&r->IdleTimeout<=r->Timeout);
    now+=full_timeout?r->Timeout:1000;if(cancel_at==calls)cancel.cancelled=true;
    if(success_at==calls){out->Status=200;return true;}error=&failure;return false;
}
#include "../../host/account-fetch.inc.c"
static void check(bool value){checks++;assert(value);}
static void reset(xerrkind kind){now=0;calls=units=0;success_at=cancel_at=wait_cancel=0;cancel.cancelled=false;full_timeout=false;
    failure=(xerror){kind,22,-104,"native",NULL};error=NULL;}
static bool run(bool read,MdoAccountTransport* info){XS_FetchRequest request={0};XS_FetchResponse response={0};request.Cancel=&cancel;
    return MdoAccountFetch(&request,&response,read,info);}
int main(void){
    MdoAccountTransport d;
    reset(XERR_IO);success_at=2;check(run(true,&d));check(calls==2&&units==1&&d.Failures==1&&d.Attempts==2);
    check(d.CauseCount==1&&d.Causes[0].SystemCode==-104&&!strcmp(d.Phase,"response"));
    for(int i=0;i<4;i++){xerrkind kinds[]={XERR_IO,XERR_AGAIN,XERR_CLOSED,XERR_TIMEOUT};reset(kinds[i]);
        check(!run(true,&d));check(calls==3&&units==2&&d.Attempts==3&&d.Retryable);}
    reset(XERR_IO);check(!run(false,&d));check(calls==1&&units==0&&d.Attempts==1);
    for(int i=0;i<7;i++){xerrkind kinds[]={XERR_PERMISSION,XERR_PROTOCOL,XERR_CANCELLED,XERR_MEMORY,XERR_ARGUMENT,XERR_RANGE,XERR_UNSUPPORTED};
        reset(kinds[i]);check(!run(true,&d));check(calls==1&&!d.Retryable);}
    reset(XERR_PROTOCOL);failure.domain="xs.fetch";failure.code=XS_FETCH_ERROR_INCOMPLETE;success_at=3;
    check(run(true,&d));check(calls==3&&d.Causes[0].Fetch&&d.Failures==2);
    reset(XERR_IO);xerror permission={XERR_PERMISSION,8,0,"certificate",NULL};failure.cause=&permission;
    check(!run(true,&d));check(calls==1&&!d.Retryable&&d.CauseCount==2);
    reset(XERR_TIMEOUT);xerror teardown={XERR_CANCELLED,3,0,"native",NULL};failure.cause=&teardown;success_at=2;
    check(run(true,&d));check(calls==2&&d.CauseCount==2);
    reset(XERR_IO);cancel_at=1;check(!run(true,&d));check(calls==1&&!strcmp(d.Phase,"cancelled"));
    reset(XERR_IO);wait_cancel=1;check(!run(true,&d));check(calls==1&&!strcmp(d.Phase,"cancelled"));
    reset(XERR_IO);cancel.cancelled=true;check(!run(true,&d));check(calls==0&&d.Attempts==0);
    reset(XERR_TIMEOUT);full_timeout=true;check(!run(true,&d));check(calls==3&&now<=15000000u&&d.ElapsedMs<=15000u);
    reset(XERR_IO);success_at=1;check(run(true,&d));check(calls==1&&units==0&&d.Failures==0);
    reset(XERR_IO);failure.cause=&failure;check(!run(false,&d));check(d.CauseCount==8&&calls==1);
    check(!strcmp(MdoAccountErrorDomain("xrt.tls"),"xrt.tls"));
    check(!strcmp(MdoAccountErrorDomain("https://example.invalid/credential"),"native"));
    check(!strcmp(MdoAccountErrorDomain(NULL),"native"));
    reset(XERR_STATE);xerror closed={XERR_STATE,8,0,"xrt.tls",NULL};
    xerror io={XERR_IO,3,0,"xrt.io",&closed};xerror wrapper={XERR_STATE,11,0,"xrt.http.client",&io};failure.cause=&wrapper;success_at=2;
    check(run(true,&d));check(calls==2&&d.CauseCount==4&&d.Retryable);
    reset(XERR_STATE);check(!run(true,&d));check(calls==1&&!d.Retryable);
    printf("{\"passed\":true,\"checks\":%d,\"real_network_calls\":0}\n",checks);
}
