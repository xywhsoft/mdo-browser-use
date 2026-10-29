/* Production relay bridge/HTTP loopback/socket implementation, connected to
 * the one development service. Only the host lease lookup is a fixture seam.
 * The private inherited pipe supplies already-authorized peers; this is not an
 * account/relay authentication test. It exposes no listener or public QA route. */
#define XRT_MODULE_ALL
#include <xrt.h>
#include <xsbase.h>
#include <stdio.h>
#include <stdlib.h>
#include <io.h>
#include <fcntl.h>

static XS_ServerInfo QaServer;
static xatomic32 QaLeases;
static xcancel* QaConnection;
XS_ServerInfo* xsServerRetain(XS_ServerInfo* Server){
    if(Server!=&QaServer)return NULL;xrtAtomic32FetchAdd(&QaLeases,1u,XMEMORY_ACQ_REL);return Server;
}
XS_ServerInfo* xsServerFind(const char* Name){return Name&&!strcmp(Name,QaServer.Name)?xsServerRetain(&QaServer):NULL;}
void xsServerRelease(XS_ServerInfo* Server){if(Server==&QaServer)xrtAtomic32FetchSub(&QaLeases,1u,XMEMORY_ACQ_REL);}

#include "account/client.c"
#include "remote/receipts.c"
#include "remote/loopback.c"
#include "remote/websocket.c"
#include "remote/bridge.c"

/* Unused transitive account/file-transfer entry points must never reach an
 * account or Home from this transport client. Fail if this assumption changes. */
static _Noreturn void QaForbidden(void){fputs("Unexpected account/Home API in native bridge fixture\n",stderr);abort();}
bool xsFetch(const XS_FetchRequest* Request,XS_FetchResponse* Response){(void)Request;(void)Response;QaForbidden();}
void xsFetchResponseUnit(XS_FetchResponse* Response){(void)Response;QaForbidden();}
xfile MdoHomeOpenWrite(cstr Path,uint32 Flags){(void)Path;(void)Flags;QaForbidden();}
bool MdoHomeRemove(cstr Path,bool Backup){(void)Path;(void)Backup;QaForbidden();}

static bool QaEmit(uint64 Route,xbytesview Bytes,void* Data){
    (void)Data;char* Encoded=xrtBase64EncodeNew(Bytes.Data,Bytes.Size,NULL);size_t Size=Encoded?strlen(Encoded):0;
    if(!Encoded)return false;
    printf("{\"type\":\"frame\",\"route\":%llu,\"base64\":\"%s\"}\n",(unsigned long long)Route,Encoded);
    xrtSecureZero(Encoded,Size);xrtFree(Encoded);return fflush(stdout)==0;
}
static void QaStats(MdoRemoteBridge* Bridge,uint64 Id){
    size_t Requests=0,Threads=0,Upload=0,Packets=0,Bytes=0;
    xrtMutexLock(Bridge->Lock);
    for(size_t I=0;I<MDO_BRIDGE_REQUESTS;I++)if(Bridge->Requests[I].Id[0]){
        Requests++;if(Bridge->Requests[I].Thread)Threads++;
    }
    Upload=Bridge->UploadBytes;Packets=Bridge->Packets;Bytes=Bridge->Bytes;
    xrtMutexUnlock(Bridge->Lock);
    printf("{\"type\":\"stats\",\"id\":%llu,\"requests\":%zu,\"threads\":%zu,\"upload_bytes\":%zu,\"packets\":%zu,\"queue_bytes\":%zu}\n",
        (unsigned long long)Id,Requests,Threads,Upload,Packets,Bytes);fflush(stdout);
}
typedef struct QaInput {xmutex* Lock;xcond* Available;char* Lines[16];size_t Count,Peak,Waits;xatomic32 End,Stop,Failed;} QaInput;
static int32 QaRead(void* Data){
    QaInput* Input=Data;char* Line=xrtMalloc(196608u);
    if(!Line){xrtAtomic32Store(&Input->Failed,1u,XMEMORY_RELEASE);goto done;}
    while(fgets(Line,196608,stdin)){
        size_t Length=strlen(Line);char* Copy=NULL;
        if(!Length||Line[Length-1]!='\n'){xrtAtomic32Store(&Input->Failed,1u,XMEMORY_RELEASE);break;}
        xrtMutexLock(Input->Lock);
        while(Input->Count==16u&&!xrtAtomic32Load(&Input->Stop,XMEMORY_ACQUIRE)){
            Input->Waits++;
            if(xrtCondWait(Input->Available,Input->Lock)!=XWAIT_OK){xrtAtomic32Store(&Input->Failed,1u,XMEMORY_RELEASE);break;}
        }
        bool Stop=xrtAtomic32Load(&Input->Stop,XMEMORY_ACQUIRE)||xrtAtomic32Load(&Input->Failed,XMEMORY_ACQUIRE);
        if(!Stop&&Input->Count<16u){Copy=xrtStrDup(Line);if(Copy){Input->Lines[Input->Count++]=Copy;if(Input->Count>Input->Peak)Input->Peak=Input->Count;}}
        xrtMutexUnlock(Input->Lock);
        xrtSecureZero(Line,Length);
        if(Stop)break;
        if(!Copy){xrtAtomic32Store(&Input->Failed,1u,XMEMORY_RELEASE);break;}
    }
    if(ferror(stdin))xrtAtomic32Store(&Input->Failed,1u,XMEMORY_RELEASE);
    xrtFree(Line);
done:xrtAtomic32Store(&Input->End,1u,XMEMORY_RELEASE);return 0;
}
static bool QaCommand(MdoRemoteBridge* Bridge,const char* Line){
    xjsonreadconfig Limits;xrtJsonReadConfigInit(&Limits);Limits.MaxInputBytes=196607u;Limits.MaxStringBytes=180000u;Limits.MaxValues=32u;Limits.MaxDepth=4u;
    xvalue* Value=xrtJsonRead(xrtStrView(Line),&Limits);
    cstr Command=MdoAccountText(Value,"command",24),Peer=MdoAccountText(Value,"peer",32);
    uint64 Id=0,Route=0;bool Ok=false;const char* Stage="parse";size_t Decoded=0;
    if(!Command||!MdoAccountGetUInt(xrtValueObjectGet(Value,XRT_STR_LITERAL("id")),&Id))goto done;
    (void)MdoAccountGetUInt(xrtValueObjectGet(Value,XRT_STR_LITERAL("route")),&Route);
    if(!strcmp(Command,"stats")){QaStats(Bridge,Id);Ok=true;goto done;}
    if(!strcmp(Command,"open")){
        Stage="open";
        bool ReadOnly=false;
        (void)xrtValueGetBool(xrtValueObjectGet(Value,XRT_STR_LITERAL("read_only")),&ReadOnly);
        Ok=MdoRemoteBridgePeerOpen(Bridge,Peer,ReadOnly,QaConnection);
    }else if(!strcmp(Command,"message")){
        Stage="decode";
        cstr Encoded=MdoAccountText(Value,"base64",180000u);bool Binary=false;size_t Size=0;
        (void)xrtValueGetBool(xrtValueObjectGet(Value,XRT_STR_LITERAL("binary")),&Binary);
        uint8* Bytes=Encoded?xrtBase64DecodeNew(Encoded,strlen(Encoded),&Size,NULL):NULL;
        if(Bytes){Stage="bridge_input";Decoded=Size;Ok=MdoRemoteBridgeInputRouted(Bridge,Peer,Route,Binary,(xbytesview){Bytes,Size});xrtSecureZero(Bytes,Size);xrtFree(Bytes);}
    }else if(!strcmp(Command,"close")){MdoRemoteBridgePeerClose(Bridge,Peer);Ok=true;
    }else if(!strcmp(Command,"disconnect")){MdoRemoteBridgeDisconnect(Bridge);Ok=true;
    }else if(!strcmp(Command,"new_route")){Route=MdoRemoteBridgeRoute(Bridge);Ok=Route!=0;
    }else if(!strcmp(Command,"close_route")){MdoRemoteBridgeRouteClose(Bridge,Route);Ok=true;}
    printf("{\"type\":\"ack\",\"id\":%llu,\"ok\":%s,\"route\":%llu}\n",(unsigned long long)Id,Ok?"true":"false",(unsigned long long)Route);fflush(stdout);
done:
    if(!Ok)fprintf(stderr,"Native fixture refused stage=%s command=%s id=%llu line_bytes=%zu decoded_bytes=%zu\n",Stage,Command?Command:"unparsed",(unsigned long long)Id,strlen(Line),Decoded);
    xrtValueRelease(Value);return Ok;
}
int main(int Count,char** Args){
    if(Count!=2)return 2;unsigned long Port=strtoul(Args[1],NULL,10);if(!Port||Port>65535)return 2;
    _setmode(_fileno(stdin),_O_BINARY);_setmode(_fileno(stdout),_O_BINARY);
    xnetengineconfig Config;xrtNetEngineConfigInit(&Config);Config.Workers=1;
    QaServer.Name="browser-use-native-bridge-qa";QaServer.IP="127.0.0.1";QaServer.PortBound=(uint16)Port;
    QaServer.Engine=xrtNetEngineCreate(&Config);xrtAtomic32Init(&QaLeases,0u);
    if(!QaServer.Engine||!xrtNetEngineStart(QaServer.Engine))return 3;
    QaConnection=xrtCancelCreate();if(!QaConnection)return 4;
    MdoRemoteNet Net={0};Net.Engine=QaServer.Engine;
    MdoRemoteBridge* Bridge=MdoRemoteBridgeCreate(&QaServer,&Net);if(!Bridge)return 4;
    QaInput Input={0};Input.Lock=xrtMutexCreate();Input.Available=xrtCondCreate();xrtAtomic32Init(&Input.End,0u);xrtAtomic32Init(&Input.Stop,0u);xrtAtomic32Init(&Input.Failed,0u);
    xthread* Reader=Input.Lock&&Input.Available?xrtThreadCreate(QaRead,&Input,0):NULL;if(!Reader)return 5;
    printf("{\"type\":\"ready\",\"scope\":\"production-native-bridge-with-host-lease-seam\"}\n");fflush(stdout);
    bool Ok=true;
    while(Ok){
        char* Line=NULL;bool End=false;
        xrtMutexLock(Input.Lock);
        if(Input.Count){Line=Input.Lines[0];Input.Count--;memmove(Input.Lines,Input.Lines+1,Input.Count*sizeof(char*));xrtCondBroadcast(Input.Available);}
        End=!Input.Count&&xrtAtomic32Load(&Input.End,XMEMORY_ACQUIRE)!=0u;
        xrtMutexUnlock(Input.Lock);
        if(Line){Ok=QaCommand(Bridge,Line);xrtSecureZero(Line,strlen(Line));xrtFree(Line);}
        if(!MdoRemoteBridgePumpRouted(Bridge,QaEmit,NULL))Ok=false;
        if(End&&!Line)break;
        xrtSleep(2);
    }
    xrtMutexLock(Input.Lock);xrtAtomic32Store(&Input.Stop,1u,XMEMORY_RELEASE);xrtCondBroadcast(Input.Available);xrtMutexUnlock(Input.Lock);
    MdoRemoteBridgeDestroy(Bridge);
    xrtCancelDestroy(QaConnection);
    // The parent closes the inherited input on normal shutdown. It must not
    // terminate/restart a live process merely because a receipt poll timed out.
    xrtThreadWait(Reader);xrtThreadDestroy(Reader);
    for(size_t I=0;I<Input.Count;I++){xrtSecureZero(Input.Lines[I],strlen(Input.Lines[I]));xrtFree(Input.Lines[I]);}
    xrtCondDestroy(Input.Available);xrtMutexDestroy(Input.Lock);
    bool EngineClean=xrtNetEngineDestroy(QaServer.Engine);
    uint32 Leases=xrtAtomic32Load(&QaLeases,XMEMORY_ACQUIRE);
    bool Failed=xrtAtomic32Load(&Input.Failed,XMEMORY_ACQUIRE)!=0u;
    printf("{\"type\":\"stopped\",\"leases\":%u,\"engine_clean\":%s,\"input_failed\":%s,\"input_peak\":%zu,\"input_waits\":%zu}\n",Leases,EngineClean?"true":"false",Failed?"true":"false",Input.Peak,Input.Waits);fflush(stdout);
    return Ok&&EngineClean&&!Leases&&!Failed?0:6;
}
