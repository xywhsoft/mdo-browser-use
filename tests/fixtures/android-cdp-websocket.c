#define _GNU_SOURCE
#define XRT_MODULE_WEBSOCKET_HANDSHAKE
#define XRT_MODULE_WEBSOCKET_MESSAGE
#define XRT_MODULE_RANDOM_SECURE
#define XRT_MODULE_JSON_READ
#include <assert.h>
#include <dirent.h>
#include <pthread.h>
#include <stdatomic.h>
#include <sys/wait.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

static atomic_int Allocated,Freed,FailAllocation,FailRandom;
static int Checks;
static void* Allocate(size_t Size);
static void Release(void* Data);
static int RandomBytes(void* Data,size_t Size);
#define MDO_CDP_ALLOC Allocate
#define MDO_CDP_FREE Release
#define MDO_CDP_RANDOM RandomBytes
#include "../../host/android/MdoBrowserCdpJni.inc.c"

typedef struct Allocation {size_t Size;unsigned char Data[];} Allocation;
static void* Allocate(size_t Size)
{
    if ( FailAllocation && !--FailAllocation ) return NULL;
    Allocation* Block=malloc(sizeof(*Block)+Size);assert(Block);Block->Size=Size;
    memset(Block->Data,0xAD,Size);Allocated++;return Block->Data;
}
static void Release(void* Data)
{
    Allocation* Block=(Allocation*)((unsigned char*)Data-sizeof(Allocation));
    for ( size_t I=0;I<Block->Size;I++ ) assert(Block->Data[I]==0);
    Freed++;free(Block);
}
static int RandomBytes(void* Data,size_t Size)
{
    if ( FailRandom ) {FailRandom=0;return 0;}
    return xrtSecureRandom(Data,Size);
}
static void Checked(int True,int Line){if ( !True ) {fprintf(stderr,"WebSocket gate failed at line %d, check %d\n",Line,Checks+1);abort();}Checks++;}
#define Check(Value) Checked((Value),__LINE__)
static uint64_t Clock(void){uint64_t Now;assert(MdoCdpNow(&Now));return Now;}
static int Fds(void)
{
    DIR* Directory=opendir("/proc/self/fd");assert(Directory);int Count=0;struct dirent* Row;
    while ( (Row=readdir(Directory)) ) if ( Row->d_name[0]!='.' ) Count++;
    closedir(Directory);return Count;
}
static int Listen(pid_t Owner)
{
    int Fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);assert(Fd>=0);
    struct sockaddr_un Address={0};Address.sun_family=AF_UNIX;
    int Size=snprintf(Address.sun_path+1,sizeof(Address.sun_path)-1,"webview_devtools_remote_%ld",(long)Owner);
    assert(Size>0 && (size_t)Size<sizeof(Address.sun_path)-1);
    assert(!bind(Fd,(struct sockaddr*)&Address,(socklen_t)(offsetof(struct sockaddr_un,sun_path)+1+(size_t)Size)));
    assert(!listen(Fd,8));return Fd;
}
static int Write(int Fd,const void* Data,size_t Size)
{
    size_t Position=0;
    while ( Position<Size ) {ssize_t N=send(Fd,(const unsigned char*)Data+Position,Size-Position,MSG_NOSIGNAL);if ( N<=0 ) return 0;Position+=(size_t)N;}
    return 1;
}
static int Read(int Fd,void* Data,size_t Size)
{
    size_t Position=0;
    while ( Position<Size ) {ssize_t N=recv(Fd,(unsigned char*)Data+Position,Size-Position,0);if ( N<=0 ) return 0;Position+=(size_t)N;}
    return 1;
}
typedef struct Server {
    int Listener,Accepted,Mode,Valid,Frames;
    const char* Invalid;
    atomic_int Ready,Release,Observed;
} Server;
static int ServerFrame(int Fd,unsigned Opcode,int Final,const void* Data,size_t Size)
{
    xwsframe Frame={0};Frame.Opcode=Opcode;Frame.Flags=Final?XWS_FRAME_FIN:0;Frame.PayloadSize=Size;
    unsigned char Head[14];size_t N;assert(xrtWsFrameWrite(&Frame,NULL,Head,sizeof(Head),&N));
    return Write(Fd,Head,N)&&Write(Fd,Data,Size);
}
static int ClientFrame(int Fd,unsigned Opcode,const void* Data,size_t Size)
{
    unsigned char Head[14],Body[MDO_CDP_WS_SEND_MAX+1];size_t Used=0;xwsframe Frame={0};
    xwsframeconfig Config;xrtWsFrameConfigInit(&Config);Config.Mask=XWS_MASK_REQUIRED;Config.MaxPayload=MDO_CDP_WS_SEND_MAX;
    for (;;) {
        if ( Used==sizeof(Head) || !Read(Fd,Head+Used,1) ) return 0;
        Used++;xwsframestatus Status=xrtWsFrameParse((xbytesview){Head,Used},&Frame,&Config,NULL);
        if ( Status==XWS_FRAME_ERROR ) return 0;
        if ( Status==XWS_FRAME_READY ) break;
    }
    if ( Frame.Opcode!=Opcode || Frame.PayloadSize!=Size || !Read(Fd,Body,Size) || !xrtWsMask(Body,Size,Frame.Mask,0) ) return 0;
    return !Size || !memcmp(Body,Data,Size);
}
static int ClientCommand(int Fd,int Id,const char* Method,const char* Session)
{
    char Data[256];int Size=snprintf(Data,sizeof(Data),"{\"id\":%d,\"method\":\"%s\",\"params\":{}%s%s%s}",Id,Method,
        Session?",\"sessionId\":\"":"",Session?Session:"",Session?"\"":"");
    assert(Size>0&&(size_t)Size<sizeof(Data));return ClientFrame(Fd,XWS_OPCODE_TEXT,Data,(size_t)Size);
}
static int ClientFetchControlId(int Fd,int Id,int Abort,const char* Session)
{
    char Data[384];int Size=snprintf(Data,sizeof(Data),
        "{\"id\":%d,\"method\":\"Fetch.%s\",\"params\":{\"requestId\":\"interception-job-7.0\"%s}%s%s%s}",
        Id,Abort?"failRequest":"continueRequest",Abort?",\"errorReason\":\"Aborted\"":"",
        Session?",\"sessionId\":\"":"",Session?Session:"",Session?"\"":"");
    assert(Size>0&&(size_t)Size<sizeof(Data));return ClientFrame(Fd,XWS_OPCODE_TEXT,Data,(size_t)Size);
}
static int ClientFetchControl(int Fd,int Abort,const char* Session){return ClientFetchControlId(Fd,2,Abort,Session);}
static int Packet(int Fd,const char* Text){return ServerFrame(Fd,XWS_OPCODE_TEXT,1,Text,strlen(Text));}
static int ClientBodyCommand(int Fd,int Id,const char* Method,const char* Params)
{
    char Data[512];int Size=snprintf(Data,sizeof(Data),"{\"id\":%d,\"method\":\"%s\",\"params\":%s}",Id,Method,Params);
    assert(Size>0&&(size_t)Size<sizeof(Data));return ClientFrame(Fd,XWS_OPCODE_TEXT,Data,(size_t)Size);
}
static int Handshake(Server* Fixture,int Fd)
{
    char Request[1024]={0};size_t Used=0;
    while ( Used<sizeof(Request)-1 ) {
        if ( !Read(Fd,Request+Used,1) ) return 0;
        Used++;if ( strstr(Request,"\r\n\r\n") ) break;
    }
    char* Key=strstr(Request,"\r\nSec-WebSocket-Key: ");
    if ( !Key ) return 0;
    Key+=21;char Accept[XWS_ACCEPT_CAPACITY];
    if ( !xrtWsAccept((xstrview){Key,XWS_KEY_SIZE},Accept,sizeof(Accept)) ) return 0;
    char Expected[512];int Length=snprintf(Expected,sizeof(Expected),"GET /devtools/page/test-001 HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: %.*s\r\nSec-WebSocket-Version: 13\r\n\r\n",XWS_KEY_SIZE,Key);
    Fixture->Valid=Length==(int)Used&&!memcmp(Expected,Request,Used);
    char Reply[8192];
    if ( Fixture->Invalid ) {
        Length=snprintf(Reply,sizeof(Reply),Fixture->Invalid,Accept);
    } else Length=snprintf(Reply,sizeof(Reply),"HTTP/1.1 101 WebSocket Protocol Handshake\r\nUpgrade: WebSocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\n\r\n",Accept);
    assert(Length>0&&(size_t)Length<sizeof(Reply));
    /* Fragment every handshake byte in one mode, preserving real kernel I/O. */
    if ( Fixture->Mode==1 || Fixture->Mode==9 ) {
        for ( int I=0;I<Length;I++ ) {if ( Fixture->Mode==9 ) usleep(10000);if ( !Write(Fd,Reply+I,1) ) return 0;}
        return 1;
    }
    return Write(Fd,Reply,(size_t)Length);
}
static void* Serve(void* Data)
{
    Server* Fixture=Data;int Fd=accept(Fixture->Listener,NULL,NULL);assert(Fd>=0);Fixture->Accepted=Fd;
    if ( Fixture->Mode!=34 && Fixture->Mode!=38 ) close(Fixture->Listener);
    struct timeval Time={Fixture->Mode==11?12:3,0};assert(!setsockopt(Fd,SOL_SOCKET,SO_RCVTIMEO,&Time,sizeof(Time)));
    if ( !Handshake(Fixture,Fd) || Fixture->Invalid ) {if ( Fixture->Mode==34 || Fixture->Mode==38 ) close(Fixture->Listener);goto Done;}
    if ( Fixture->Mode<=1 ) {
        Fixture->Frames+=ClientFrame(Fd,XWS_OPCODE_TEXT,"{\"id\":1}",8);
        const unsigned char First[]={0xE4,0xBD},Second[]={0xA0,'!'};
        if ( !ServerFrame(Fd,XWS_OPCODE_TEXT,0,First,sizeof(First)) || !ServerFrame(Fd,XWS_OPCODE_PING,1,"hi",2) ||
             !ServerFrame(Fd,XWS_OPCODE_CONTINUATION,1,Second,sizeof(Second)) || !ServerFrame(Fd,XWS_OPCODE_TEXT,1,"second",6) ) goto Done;
        Fixture->Frames+=ClientFrame(Fd,XWS_OPCODE_PONG,"hi",2);
        unsigned char Close[]={3,232};ServerFrame(Fd,XWS_OPCODE_CLOSE,1,Close,sizeof(Close));
        Fixture->Frames+=ClientFrame(Fd,XWS_OPCODE_CLOSE,Close,sizeof(Close));
    } else if ( Fixture->Mode==2 ) {
        Write(Fd,"\x81\x05he",4);atomic_store(&Fixture->Ready,1);
        while ( !atomic_load(&Fixture->Release) ) usleep(1000);
        Write(Fd,"llo",3);while ( !atomic_load(&Fixture->Observed) ) usleep(1000);
    } else if ( Fixture->Mode==3 ) {
        atomic_store(&Fixture->Ready,1);char Byte;atomic_store(&Fixture->Observed,recv(Fd,&Byte,1,0)==0);
    } else if ( Fixture->Mode==4 ) {
        ServerFrame(Fd,XWS_OPCODE_TEXT,1,"message",7);
        char Byte;(void)recv(Fd,&Byte,1,0);
    } else if ( Fixture->Mode==5 ) {
        /* Wait without reading, forcing a large client frame into backpressure. */
        atomic_store(&Fixture->Ready,1);while ( !atomic_load(&Fixture->Release) ) usleep(1000);
    } else if ( Fixture->Mode==6 ) {
        atomic_store(&Fixture->Ready,1);while ( !atomic_load(&Fixture->Release) ) usleep(1000);
    } else if ( Fixture->Mode==7 ) {
        Fixture->Frames+=ClientFrame(Fd,XWS_OPCODE_TEXT,"ok",2);
        while ( !atomic_load(&Fixture->Release) ) usleep(1000);
    } else if ( Fixture->Mode==8 ) {
        /* Guarantee at least three grow allocations even when a separate
         * header read permits the first full 8192-byte payload batch. Its NUL
         * terminator rounds initial capacity to 16384, so 30000 bytes could
         * finish after only two allocations depending on socket coalescing. */
        char* Body=malloc(66000);assert(Body);memset(Body,'z',66000);
        ServerFrame(Fd,XWS_OPCODE_TEXT,0,Body,33000);ServerFrame(Fd,XWS_OPCODE_CONTINUATION,1,Body+33000,33000);free(Body);
        while ( !atomic_load(&Fixture->Release) ) usleep(1000);
    } else if ( Fixture->Mode==10 ) {
        ServerFrame(Fd,XWS_OPCODE_TEXT,1,NULL,0);ServerFrame(Fd,XWS_OPCODE_TEXT,1,"next",4);
    } else if ( Fixture->Mode==11 ) {
        Write(Fd,"\x81\x05he",4);atomic_store(&Fixture->Ready,1);
        char Byte;(void)recv(Fd,&Byte,1,0);
    } else if ( Fixture->Mode==34 ) {
        /* Two real connections on the same native worker: the first command
         * cannot complete until an event on the second is continued. */
        int Second=accept(Fixture->Listener,NULL,NULL);assert(Second>=0);close(Fixture->Listener);
        assert(!setsockopt(Second,SOL_SOCKET,SO_RCVTIMEO,&Time,sizeof(Time)));
        int FirstValid=Fixture->Valid;
        if ( !Handshake(Fixture,Second) ) {Fixture->Valid=0;close(Second);goto Done;}
        Fixture->Valid=Fixture->Valid&&FirstValid;
        Fixture->Frames+=ClientCommand(Fd,1,"Runtime.evaluate",NULL);
        Packet(Second,"{\"method\":\"Fetch.requestPaused\",\"params\":{\"requestId\":\"interception-job-7.0\",\"responseStatusCode\":200}}");
        Fixture->Frames+=ClientFetchControlId(Second,1,0,NULL);
        Packet(Second,"{\"id\":1,\"result\":{}}");Packet(Fd,"{\"id\":1,\"result\":{}}");
        char Byte;if ( recv(Fd,&Byte,1,0)>0 || recv(Second,&Byte,1,0)>0 ) Fixture->Valid=0;
        close(Second);
    } else if ( Fixture->Mode==38 ) {
        int Page=accept(Fixture->Listener,NULL,NULL);assert(Page>=0);close(Fixture->Listener);assert(Handshake(Fixture,Page));
        Fixture->Frames+=ClientCommand(Fd,1,"Runtime.evaluate",NULL);
        Packet(Fd,"{\"method\":\"Fetch.requestPaused\",\"params\":{\"requestId\":\"interception-job-7.0\",\"responseStatusCode\":200,\"resourceType\":\"Document\",\"request\":{\"method\":\"POST\"},\"responseHeaders\":[{\"name\":\"Content-Disposition\",\"value\":\"attachment\"}]}}");
        Fixture->Frames+=ClientBodyCommand(Fd,2,"Fetch.takeResponseBodyAsStream","{\"requestId\":\"interception-job-7.0\"}");
        Packet(Fd,"{\"id\":2,\"result\":{\"stream\":\"stream-7.0\"}}");Packet(Fd,"{\"id\":1,\"result\":{}}");
        Fixture->Frames+=ClientBodyCommand(Fd,3,"IO.read","{\"handle\":\"stream-7.0\",\"size\":64}");
        /* A synchronous body wait on the common worker would prevent this
         * independent page command from ever being sent. */
        Fixture->Frames+=ClientCommand(Page,1,"Runtime.evaluate",NULL);Packet(Page,"{\"id\":1,\"result\":{}}");
        Packet(Fd,"{\"id\":3,\"result\":{\"data\":\"AAH/gA==\",\"base64Encoded\":true,\"eof\":true}}");
        Fixture->Frames+=ClientBodyCommand(Fd,4,"IO.close","{\"handle\":\"stream-7.0\"}");Packet(Fd,"{\"id\":4,\"result\":{}}");
        Fixture->Frames+=ClientFetchControlId(Fd,5,1,NULL);Packet(Fd,"{\"id\":5,\"result\":{}}");
        char Byte;if ( recv(Fd,&Byte,1,0)>0 || recv(Page,&Byte,1,0)>0 ) Fixture->Valid=0;close(Page);
    } else if ( Fixture->Mode==39 ) {
        Fixture->Frames+=ClientCommand(Fd,1,"Runtime.evaluate",NULL);
        Packet(Fd,"{\"method\":\"Fetch.requestPaused\",\"params\":{\"requestId\":\"interception-job-7.0\",\"responseStatusCode\":200,\"resourceType\":\"Document\",\"request\":{\"method\":\"POST\"},\"responseHeaders\":[{\"name\":\"Content-Disposition\",\"value\":\"attachment\"}]}}");
        Fixture->Frames+=ClientBodyCommand(Fd,2,"Fetch.takeResponseBodyAsStream","{\"requestId\":\"interception-job-7.0\"}");
        Packet(Fd,"{\"id\":2,\"result\":{\"stream\":\"stream-7.0\"}}");Packet(Fd,"{\"id\":1,\"result\":{}}");
        Fixture->Frames+=ClientBodyCommand(Fd,3,"IO.read","{\"handle\":\"stream-7.0\",\"size\":64}");
        Fixture->Frames+=ClientBodyCommand(Fd,4,"IO.close","{\"handle\":\"stream-7.0\"}");Packet(Fd,"{\"id\":4,\"result\":{}}");
        Fixture->Frames+=ClientFetchControlId(Fd,5,1,NULL);Packet(Fd,"{\"id\":5,\"result\":{}}");
        /* Cleanup is acknowledged, but the old read is not answered until a
         * new page command proves cancellation did not block the connection. */
        Fixture->Frames+=ClientCommand(Fd,6,"Runtime.evaluate",NULL);
        Packet(Fd,"{\"id\":3,\"result\":{\"data\":\"private-late-data\",\"eof\":true}}");Packet(Fd,"{\"id\":6,\"result\":{}}");
        char Byte;if ( recv(Fd,&Byte,1,0)>0 ) Fixture->Valid=0;
    } else if ( Fixture->Mode==42 || Fixture->Mode==43 ) {
        Fixture->Frames+=ClientBodyCommand(Fd,1,"Page.navigate","{\"url\":\"about:blank\",\"frameId\":\"frame-owned\"}");
        if ( Fixture->Mode==42 ) {
            Packet(Fd,"{\"method\":\"Fetch.requestPaused\",\"params\":{\"requestId\":\"interception-job-7.0\",\"responseStatusCode\":200,\"resourceType\":\"Document\",\"request\":{\"method\":\"POST\"},\"responseHeaders\":[{\"name\":\"Content-Disposition\",\"value\":\"attachment\"}]}}");
            Fixture->Frames+=ClientBodyCommand(Fd,2,"Fetch.takeResponseBodyAsStream","{\"requestId\":\"interception-job-7.0\"}");
            Packet(Fd,"{\"id\":2,\"result\":{\"stream\":\"stream-7.0\"}}");
            Fixture->Frames+=ClientBodyCommand(Fd,3,"IO.read","{\"handle\":\"stream-7.0\",\"size\":64}");
            Packet(Fd,"{\"id\":3,\"result\":{\"data\":\"CQ==\",\"base64Encoded\":true,\"eof\":true}}");
            Fixture->Frames+=ClientBodyCommand(Fd,4,"IO.close","{\"handle\":\"stream-7.0\"}");Packet(Fd,"{\"id\":4,\"result\":{}}");
            Fixture->Frames+=ClientFetchControlId(Fd,5,1,NULL);Packet(Fd,"{\"id\":5,\"result\":{}}");
            /* The navigation ACK is withheld until the exact same connection
             * has completed its original stream/request cleanup. */
            Packet(Fd,"{\"id\":1,\"result\":{\"frameId\":\"frame-owned\",\"isDownload\":true,\"errorText\":\"net::ERR_ABORTED\"}}");
            Fixture->Frames+=ClientCommand(Fd,6,"Runtime.evaluate",NULL);Packet(Fd,"{\"id\":6,\"result\":{}}");
        } else {
            Fixture->Frames+=ClientCommand(Fd,2,"Runtime.evaluate",NULL);
            Packet(Fd,"{\"id\":1,\"result\":{\"frameId\":\"frame-owned\",\"errorText\":\"net::ERR_NAME_NOT_RESOLVED\"}}");
            Packet(Fd,"{\"id\":2,\"result\":{}}");
        }
        {char Byte;if ( recv(Fd,&Byte,1,0)>0 ) Fixture->Valid=0;}
    } else if ( Fixture->Mode==40 || Fixture->Mode==41 ) {
        Fixture->Frames+=ClientCommand(Fd,1,"Runtime.evaluate",NULL);
        Packet(Fd,"{\"method\":\"Fetch.requestPaused\",\"params\":{\"requestId\":\"interception-job-7.0\",\"responseStatusCode\":200,\"resourceType\":\"Document\",\"request\":{\"method\":\"POST\",\"url\":\"https://private.invalid/one-shot\",\"postData\":\"private-body\"},\"responseHeaders\":[{\"name\":\"Content-Disposition\",\"value\":\"attachment\"}]}}");
        Packet(Fd,"{\"id\":1,\"result\":{}}");
        Fixture->Frames+=ClientCommand(Fd,2,"Runtime.evaluate",NULL);Packet(Fd,"{\"id\":2,\"result\":{}}");
        if ( Fixture->Mode==40 ) {
            Fixture->Frames+=ClientBodyCommand(Fd,3,"Fetch.takeResponseBodyAsStream","{\"requestId\":\"interception-job-7.0\"}");
            Packet(Fd,"{\"id\":3,\"result\":{\"stream\":\"stream-7.0\"}}");
            Fixture->Frames+=ClientBodyCommand(Fd,4,"IO.read","{\"handle\":\"stream-7.0\",\"size\":64}");
            Packet(Fd,"{\"id\":4,\"result\":{\"data\":\"AQID\",\"base64Encoded\":true,\"eof\":true}}");
            Fixture->Frames+=ClientBodyCommand(Fd,5,"IO.close","{\"handle\":\"stream-7.0\"}");Packet(Fd,"{\"id\":5,\"result\":{}}");
            Fixture->Frames+=ClientFetchControlId(Fd,6,1,NULL);Packet(Fd,"{\"id\":6,\"result\":{}}");
        } else {Fixture->Frames+=ClientFetchControlId(Fd,3,1,NULL);Packet(Fd,"{\"id\":3,\"result\":{}}");}
        {char Byte;if ( recv(Fd,&Byte,1,0)>0 ) Fixture->Valid=0;}
    } else if ( Fixture->Mode>=35 && Fixture->Mode<=37 ) {
        Fixture->Frames+=ClientCommand(Fd,1,"Runtime.evaluate",NULL);
        Packet(Fd,"{\"method\":\"Fetch.requestPaused\",\"params\":{\"requestId\":\"interception-job-7.0\",\"responseStatusCode\":200,\"resourceType\":\"Document\",\"request\":{\"method\":\"POST\",\"url\":\"https://private.invalid/one-shot\",\"postData\":\"private-body\"},\"responseHeaders\":[{\"name\":\"Content-Disposition\",\"value\":\"attachment; filename=private.bin\"}]}}");
        Fixture->Frames+=ClientBodyCommand(Fd,2,"Fetch.takeResponseBodyAsStream","{\"requestId\":\"interception-job-7.0\"}");
        Packet(Fd,"{\"id\":2,\"result\":{\"stream\":\"stream-7.0\"}}");Packet(Fd,"{\"id\":1,\"result\":{}}");
        if ( Fixture->Mode!=36 ) {
            Fixture->Frames+=ClientBodyCommand(Fd,3,"IO.read","{\"handle\":\"stream-7.0\",\"size\":64}");
            if ( Fixture->Mode==37 ) goto BodyDone;
            Packet(Fd,"{\"id\":3,\"result\":{\"data\":\"AAH/gA==\",\"base64Encoded\":true,\"eof\":false}}");
            Fixture->Frames+=ClientBodyCommand(Fd,4,"IO.read","{\"handle\":\"stream-7.0\",\"size\":60}");
            Packet(Fd,"{\"id\":4,\"result\":{\"data\":\"5Lit\",\"base64Encoded\":true,\"eof\":false}}");
            Fixture->Frames+=ClientBodyCommand(Fd,5,"IO.read","{\"handle\":\"stream-7.0\",\"size\":57}");
            Packet(Fd,"{\"id\":5,\"result\":{\"data\":\"\",\"base64Encoded\":false,\"eof\":true}}");
        }
        int CloseId=Fixture->Mode==36?3:6,AbortId=CloseId+1;
        Fixture->Frames+=ClientBodyCommand(Fd,CloseId,"IO.close","{\"handle\":\"stream-7.0\"}");
        char Reply[64];snprintf(Reply,sizeof(Reply),"{\"id\":%d,\"result\":{}}",CloseId);Packet(Fd,Reply);
        Fixture->Frames+=ClientFetchControlId(Fd,AbortId,1,NULL);
        snprintf(Reply,sizeof(Reply),"{\"id\":%d,\"result\":{}}",AbortId);Packet(Fd,Reply);
BodyDone:
        {char Byte;if ( recv(Fd,&Byte,1,0)>0 ) Fixture->Valid=0;} /* No read/request replay. */
    } else if ( Fixture->Mode>=28 && Fixture->Mode<=33 ) {
        const char* Session=Fixture->Mode==30||Fixture->Mode==31?"child-001":NULL;
        Fixture->Frames+=ClientCommand(Fd,1,"Runtime.evaluate",NULL);
        char Event[384];int Length=snprintf(Event,sizeof(Event),
            "{\"method\":\"Fetch.requestPaused\",\"params\":{\"requestId\":\"interception-job-7.0\"%s}%s%s%s}",
            Fixture->Mode==33?"":",\"responseStatusCode\":200",
            Session?",\"sessionId\":\"":"",Session?Session:"",Session?"\"":"");
        assert(Length>0&&(size_t)Length<sizeof(Event));Packet(Fd,Event);
        /* Withhold the ordinary command reply until its original paused event
         * is continued/aborted. A serialized-only dispatcher would deadlock. */
        Fixture->Frames+=ClientFetchControl(Fd,Fixture->Mode==31,Session);
        if ( Fixture->Mode==31 ) Packet(Fd,"{\"id\":2,\"sessionId\":\"child-001\",\"error\":{\"code\":-32601,\"message\":\"private-provider-data\"}}");
        else if ( Fixture->Mode!=32 ) {
            if ( Fixture->Mode==29 ) Packet(Fd,"{\"id\":1,\"result\":{}}");
            /* Mode 30 intentionally strips the child source from its ACK. */
            Packet(Fd,"{\"id\":2,\"result\":{}}");
            if ( Fixture->Mode!=29 && Fixture->Mode!=30 ) Packet(Fd,"{\"id\":1,\"result\":{}}");
        }
        char Byte;if ( recv(Fd,&Byte,1,0)>0 ) Fixture->Valid=0;
    } else if ( Fixture->Mode>=20 && Fixture->Mode<=27 ) {
        if ( Fixture->Mode==20 || Fixture->Mode==27 ) {
            Fixture->Frames+=ClientCommand(Fd,1,"Runtime.enable",NULL);
            Packet(Fd,"{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"id\":41}}}");
            Packet(Fd,"{\"id\":1,\"result\":{}}");
            if ( Fixture->Mode==20 ) {
                Fixture->Frames+=ClientCommand(Fd,2,"Runtime.evaluate",NULL);
                Packet(Fd,"{\"id\":2,\"result\":{\"result\":{\"type\":\"number\",\"value\":2}}}");
            }
        } else if ( Fixture->Mode==22 ) {
            Fixture->Frames+=ClientCommand(Fd,1,"Runtime.evaluate",NULL);
            Packet(Fd,"{\"id\":1,\"error\":{\"code\":-32601,\"message\":\"private-provider-message\"}}");
            Fixture->Frames+=ClientCommand(Fd,2,"Runtime.enable",NULL);Packet(Fd,"{\"id\":2,\"result\":{}}");
            Fixture->Frames+=ClientCommand(Fd,3,"Runtime.evaluate",NULL);
            Packet(Fd,"{\"id\":3,\"error\":{\"code\":0,\"message\":\"private-provider-message\"}}");
            Fixture->Frames+=ClientCommand(Fd,4,"Runtime.enable",NULL);Packet(Fd,"{\"id\":4,\"result\":{}}");
        } else if ( Fixture->Mode==24 ) {
            Fixture->Frames+=ClientCommand(Fd,1,"Runtime.enable","child-001");
            Packet(Fd,"{\"sessionId\":\"child-001\",\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"id\":71}}}");
            Packet(Fd,"{\"id\":1,\"sessionId\":\"child-001\",\"result\":{}}");
        } else {
            Fixture->Frames+=ClientCommand(Fd,1,"Runtime.evaluate",NULL);
            if ( Fixture->Mode==21 ) Packet(Fd,"{\"id\":99,\"result\":{}}");
            if ( Fixture->Mode==23 ) Packet(Fd,"{\"method\":\"Runtime.executionContextCreated\",\"sessionId\":\"foreign\",\"params\":{}}");
            if ( Fixture->Mode==25 ) Packet(Fd,"{\"id\":1,\"id\":2,\"result\":{}}");
            /* Mode 26 withholds the result until the command deadline. */
        }
        char Byte;if ( recv(Fd,&Byte,1,0)>0 ) Fixture->Valid=0; /* No effect replay. */
    } else if ( Fixture->Mode>=100 ) {
        static const unsigned char* Invalid[]={
            (const unsigned char*)"\x81\x80\0\0\0\0", /* masked server */
            (const unsigned char*)"\xC1\0", /* RSV */
            (const unsigned char*)"\x82\0", /* binary */
            (const unsigned char*)"\x80\0", /* stray continuation */
            (const unsigned char*)"\x81\x02\xC0\x80", /* noncanonical UTF-8 */
            (const unsigned char*)"\x81\x7e\0\x01x", /* nonminimal length */
            (const unsigned char*)"\x81\x7f\0\0\0\0\0\x40\0\x01", /* over 4 MiB */
            (const unsigned char*)"\x09\0", /* fragmented ping */
            (const unsigned char*)"\x88\x02\x03\xED", /* reserved close 1005 */
            (const unsigned char*)"\x81\x01\xE4", /* unfinished UTF-8 */
            (const unsigned char*)"\x81\x01x\x81\0"};
        static const size_t Sizes[]={6,2,2,2,4,5,10,2,4,3,5};
        size_t Index=(size_t)Fixture->Mode-100;assert(Index<sizeof(Sizes)/sizeof(Sizes[0]));Write(Fd,Invalid[Index],Sizes[Index]);
        if ( Index==10 ) { /* separately valid messages, only used as a close race */ }
    }
Done:
    close(Fd);return NULL;
}
static pthread_t Start(Server* Fixture,int Mode,const char* Invalid)
{
    memset(Fixture,0,sizeof(*Fixture));Fixture->Mode=Mode;Fixture->Invalid=Invalid;Fixture->Listener=Listen(getpid());
    pthread_t Thread;assert(!pthread_create(&Thread,NULL,Serve,Fixture));return Thread;
}
static uint64_t Open(void)
{
    uint64_t Handle=0;enum MdoCdpSocketCode Code=mdoCdpWsOpen("test-001",8,1000,&Handle);
    if ( Code!=MDO_CDP_SOCKET_OK ) fprintf(stderr,"Open failed: %s\n",mdoCdpSocketCode(Code));
    Check(Code==MDO_CDP_SOCKET_OK);Check(Handle!=0);return Handle;
}
static void Join(pthread_t Thread,Server* Fixture)
{
    atomic_store(&Fixture->Release,1);atomic_store(&Fixture->Observed,1);
    assert(!pthread_join(Thread,NULL));Check(Fixture->Valid);
}
static void InvalidHandshake(const char* Reply)
{
    Server Fixture;pthread_t Thread=Start(&Fixture,0,Reply);uint64_t Handle=999;
    Check(mdoCdpWsOpen("test-001",8,1000,&Handle)==MDO_CDP_SOCKET_HTTP);Check(!Handle);Join(Thread,&Fixture);
}
static void* BlockedRead(void* Data)
{
    uint64_t Handle=*(uint64_t*)Data;MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,5000);
    assert(Reply.Code==MDO_CDP_SOCKET_CLOSED);mdoCdpDiscoveryFree(&Reply);return NULL;
}
/* Fault the actual JNI conversion functions while retaining the real socket
 * underneath. JVM tests below additionally execute the normal JNI ABI path. */
typedef struct FakeBytes {jsize Size;unsigned char* Data;} FakeBytes;
static int JniPending,JniFault;static FakeBytes* JniOut;static char JniError[64];
static jclass JniFind(JNIEnv* Env,const char* Name)
{(void)Env;assert(!strcmp(Name,"java/io/IOException"));return (jclass)(uintptr_t)1;}
static jint JniThrow(JNIEnv* Env,jclass Type,const char* Text)
{(void)Env;assert(Type==(jclass)(uintptr_t)1);JniPending=1;snprintf(JniError,sizeof(JniError),"%s",Text);return 0;}
static void JniDelete(JNIEnv* Env,jobject Type){(void)Env;assert(Type==(jobject)(uintptr_t)1);}
static jboolean JniException(JNIEnv* Env){(void)Env;return JniPending?JNI_TRUE:JNI_FALSE;}
static jsize JniLength(JNIEnv* Env,jarray Value){(void)Env;assert(Value);return ((FakeBytes*)Value)->Size;}
static jbyteArray JniNew(JNIEnv* Env,jsize Size)
{
    (void)Env;if ( JniFault==1 ) {JniPending=1;return NULL;}
    assert(Size>=0&&Size<=(jsize)MDO_CDP_WS_MESSAGE);JniOut=calloc(1,sizeof(*JniOut));assert(JniOut);
    JniOut->Size=Size;JniOut->Data=calloc(1,Size?(size_t)Size:1);assert(JniOut->Data);return (jbyteArray)JniOut;
}
static void JniSet(JNIEnv* Env,jbyteArray Value,jsize Begin,jsize Size,const jbyte* Data)
{
    (void)Env;assert(!Begin&&Value==(jbyteArray)JniOut&&Size==JniOut->Size);
    if ( JniFault==2 ) {JniPending=1;return;}memcpy(JniOut->Data,Data,(size_t)Size);
}
static void JniGet(JNIEnv* Env,jbyteArray Value,jsize Begin,jsize Size,jbyte* Data)
{
    (void)Env;assert(!Begin&&Value&&Size==((FakeBytes*)Value)->Size);
    if ( JniFault==3 ) {JniPending=1;return;}memcpy(Data,((FakeBytes*)Value)->Data,(size_t)Size);
}
static const struct JNINativeInterface_ JniTable={.FindClass=JniFind,.ThrowNew=JniThrow,.DeleteLocalRef=JniDelete,
    .ExceptionCheck=JniException,.GetArrayLength=JniLength,.NewByteArray=JniNew,
    .SetByteArrayRegion=JniSet,.GetByteArrayRegion=JniGet};
static void ResetJni(void)
{
    if ( JniOut ) {MdoCdpClear(JniOut->Data,(size_t)JniOut->Size);free(JniOut->Data);free(JniOut);JniOut=NULL;}
    JniPending=JniFault=0;JniError[0]=0;
}
static void TestJniFaults(void)
{
    JNIEnv Env=&JniTable;
    for ( int Fault=1;Fault<=2;Fault++ ) {
        Server Fixture;pthread_t Thread=Start(&Fixture,4,NULL);uint64_t Handle=Open();JniFault=Fault;
        Check(!Java_org_xserver_android_MdoBrowserCdpNative_receiveNative(&Env,NULL,(jlong)Handle,1000));
        Check(JniPending);Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,100)==MDO_CDP_SOCKET_CLOSED);
        ResetJni();Join(Thread,&Fixture);
    }
    FakeBytes Id={8,(unsigned char*)"test-001"},Command={2,(unsigned char*)"ok"};
    JniFault=3;Check(!Java_org_xserver_android_MdoBrowserCdpNative_openNative(&Env,NULL,(jbyteArray)&Id,100));Check(JniPending);ResetJni();
    for ( int Fault=0;Fault<=1;Fault++ ) {
        Server Fixture;pthread_t Thread=Start(&Fixture,3,NULL);uint64_t Handle=Open();
        if ( Fault ) FailAllocation=1;else JniFault=3;
        Java_org_xserver_android_MdoBrowserCdpNative_sendNative(&Env,NULL,(jlong)Handle,(jbyteArray)&Command,100);
        Check(JniPending);if ( Fault ) Check(!strcmp(JniError,"cdp_allocation_failed"));
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,100)==MDO_CDP_SOCKET_CLOSED);ResetJni();Join(Thread,&Fixture);
    }
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,7,NULL);
        jlong Handle=Java_org_xserver_android_MdoBrowserCdpNative_openNative(&Env,NULL,(jbyteArray)&Id,1000);Check(Handle>0&&!JniPending);
        Java_org_xserver_android_MdoBrowserCdpNative_sendNative(&Env,NULL,Handle,(jbyteArray)&Command,1000);Check(!JniPending);
        Check(!Java_org_xserver_android_MdoBrowserCdpNative_receiveNative(&Env,NULL,Handle,10)&&!JniPending);
        Java_org_xserver_android_MdoBrowserCdpNative_closeNative(&Env,NULL,Handle);Join(Thread,&Fixture);Check(Fixture.Frames==1);
    }
    const char* Good="{\"id\":1,\"result\":{}}";
    FakeBytes Json={(jsize)strlen(Good),(unsigned char*)Good};
    for ( int I=0;I<64;I++ ) {
        Java_org_xserver_android_MdoBrowserCdpNative_validateNative(&Env,NULL,(jbyteArray)&Json,MDO_CDP_WS_MESSAGE);Check(!JniPending);
    }
    const char* Duplicate="{\"id\":1,\"result\":{\"x\":1,\"x\":2}}";
    Json.Size=(jsize)strlen(Duplicate);Json.Data=(unsigned char*)Duplicate;
    Java_org_xserver_android_MdoBrowserCdpNative_validateNative(&Env,NULL,(jbyteArray)&Json,MDO_CDP_WS_MESSAGE);
    Check(JniPending&&!strcmp(JniError,"cdp_json_invalid"));ResetJni();
    Json.Size=(jsize)strlen(Good);Json.Data=(unsigned char*)Good;
    JniFault=3;Java_org_xserver_android_MdoBrowserCdpNative_validateNative(&Env,NULL,(jbyteArray)&Json,MDO_CDP_WS_MESSAGE);
    Check(JniPending&&!JniError[0]);ResetJni();
    FailAllocation=1;Java_org_xserver_android_MdoBrowserCdpNative_validateNative(&Env,NULL,(jbyteArray)&Json,MDO_CDP_WS_MESSAGE);
    Check(JniPending&&!strcmp(JniError,"cdp_allocation_failed"));ResetJni();
    Java_org_xserver_android_MdoBrowserCdpNative_validateNative(&Env,NULL,(jbyteArray)&Json,123);
    Check(JniPending&&!strcmp(JniError,"cdp_json_invalid"));ResetJni();
}
/* The actual JVM loads this small fixture library, not an xs engine copy.
 * Its only extra JNI methods control the local same-process test peer. */
#ifdef MDO_CDP_JVM_FIXTURE
static Server VmServer;static pthread_t VmThread;static int VmRunning,VmBaseline;
JNIEXPORT void JNICALL Java_org_xserver_android_CdpNativeProbe_start(JNIEnv* Env,jclass Type,jint Mode)
{
    (void)Env;(void)Type;assert(!VmRunning);VmBaseline=Fds();VmThread=Start(&VmServer,Mode,NULL);VmRunning=1;
}
JNIEXPORT jint JNICALL Java_org_xserver_android_CdpNativeProbe_finish(JNIEnv* Env,jclass Type)
{
    (void)Env;(void)Type;assert(VmRunning);Join(VmThread,&VmServer);VmRunning=0;
    assert(Fds()==VmBaseline);assert(Allocated==Freed);
    for ( size_t I=0;I<MDO_CDP_WS_MAX;I++ ) assert(!MdoCdpWsSlots[I].Reserved);
    return VmServer.Valid+(VmServer.Frames<<8);
}
JNIEXPORT void JNICALL Java_org_xserver_android_CdpNativeProbe_fault(JNIEnv* Env,jclass Type,jint Allocation,jboolean Random)
{(void)Env;(void)Type;FailAllocation=Allocation;FailRandom=Random;}
JNIEXPORT void JNICALL Java_org_xserver_android_CdpNativeProbe_awaitRead(JNIEnv* Env,jclass Type)
{
    (void)Env;(void)Type;uint64_t End=Clock()+1000;
    while ( Clock()<End ) {
        int Busy=0;pthread_mutex_lock(&MdoCdpWsGate);
        for ( size_t I=0;I<MDO_CDP_WS_MAX;I++ ) if ( MdoCdpWsSlots[I].Session ) {
            int Got=pthread_mutex_trylock(&MdoCdpWsSlots[I].Session->Io);
            if ( Got ) Busy=1;else pthread_mutex_unlock(&MdoCdpWsSlots[I].Session->Io);
        }
        pthread_mutex_unlock(&MdoCdpWsGate);if ( Busy ) return;usleep(1000);
    }
    assert(!"JVM worker did not enter the actual native read");
}
#endif
int main(void)
{
    signal(SIGPIPE,SIG_IGN);int Before=Fds();uint64_t Handle=77;
    Check(mdoCdpWsOpen("/bad",4,500,&Handle)==MDO_CDP_SOCKET_ARGUMENTS&&!Handle);
    Check(mdoCdpWsOpen("a\r\nb",4,500,&Handle)==MDO_CDP_SOCKET_ARGUMENTS);
    Check(mdoCdpWsOpen("a.b",3,500,&Handle)==MDO_CDP_SOCKET_ARGUMENTS);
    Check(mdoCdpWsOpen("a",1,0,&Handle)==MDO_CDP_SOCKET_ARGUMENTS);
    Check(mdoCdpWsOpen("a",1,100,NULL)==MDO_CDP_SOCKET_ARGUMENTS);
    Check(mdoCdpWsOpen("a",1,100,&Handle)==MDO_CDP_SOCKET_UNAVAILABLE);
    for ( int Mode=0;Mode<=1;Mode++ ) {
        Server Fixture;pthread_t Thread=Start(&Fixture,Mode,NULL);Handle=Open();
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"{\"id\":1}",8,1000)==MDO_CDP_SOCKET_OK);
        MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,1000);
        Check(Reply.Code==MDO_CDP_SOCKET_OK&&Reply.Size==4&&!memcmp(Reply.Data,"\xE4\xBD\xA0!",4));mdoCdpDiscoveryFree(&Reply);
        Reply=mdoCdpWsReceive(Handle,1000);Check(Reply.Code==MDO_CDP_SOCKET_OK&&Reply.Size==6&&!memcmp(Reply.Data,"second",6));mdoCdpDiscoveryFree(&Reply);
        Reply=mdoCdpWsReceive(Handle,1000);Check(Reply.Code==MDO_CDP_SOCKET_CLOSED&&!Reply.Data);
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,100)==MDO_CDP_SOCKET_CLOSED);mdoCdpWsClose(Handle);
        Join(Thread,&Fixture);Check(Fixture.Frames==3);
    }
    InvalidHandshake("HTTP/1.1 200 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: wrong\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nSec-WebSocket-Extensions: permessage-deflate\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nContent-Length: 0\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nTransfer-Encoding: chunked\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nSec-WebSocket-Protocol: cdp\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nConnection: Upgrade\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: keep-alive\r\nSec-WebSocket-Accept: %s\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\n Fold: no\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\n\r\n");
    InvalidHandshake("HTTP/1.1 101 OK\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nSec-WebSocket-Accept: wrong\r\n\r\n");
    for ( int Mode=100;Mode<110;Mode++ ) {
        Server Fixture;pthread_t Thread=Start(&Fixture,Mode,NULL);Handle=Open();MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,1000);
        Check(Reply.Code==MDO_CDP_SOCKET_PROTOCOL);Check(!Reply.Data);mdoCdpWsClose(Handle);Join(Thread,&Fixture);
    }
    /* Timeouts preserve a partial frame, but have no published partial text. */
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,2,NULL);Handle=Open();MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,30);
        Check(Reply.Code==MDO_CDP_SOCKET_TIMEOUT&&!Reply.Data);atomic_store(&Fixture.Release,1);
        Reply=mdoCdpWsReceive(Handle,1000);Check(Reply.Code==MDO_CDP_SOCKET_OK&&Reply.Size==5&&!memcmp(Reply.Data,"hello",5));mdoCdpDiscoveryFree(&Reply);
        mdoCdpWsClose(Handle);Join(Thread,&Fixture);
    }
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,10,NULL);Handle=Open();MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,1000);
        Check(Reply.Code==MDO_CDP_SOCKET_OK&&Reply.Size==0&&Reply.Data&&Reply.Data[0]==0);mdoCdpDiscoveryFree(&Reply);
        Reply=mdoCdpWsReceive(Handle,1000);Check(Reply.Code==MDO_CDP_SOCKET_OK&&Reply.Size==4&&!memcmp(Reply.Data,"next",4));mdoCdpDiscoveryFree(&Reply);
        mdoCdpWsClose(Handle);Join(Thread,&Fixture);
    }
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,9,NULL);uint64_t Started=Clock();
        Check(mdoCdpWsOpen("test-001",8,50,&Handle)==MDO_CDP_SOCKET_TIMEOUT&&!Handle);
        Check(Clock()-Started<500);Join(Thread,&Fixture);
    }
    /* Polling with fresh short budgets cannot keep one partial message alive
     * forever: test the actual production ten-second assembly clock. */
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,11,NULL);Handle=Open();uint64_t Started=Clock();int Expired=0;
        for ( int I=0;I<500;I++ ) {MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,40);
            if ( Reply.Code==MDO_CDP_SOCKET_EXPIRED ) {Expired=1;break;}
            Check(Reply.Code==MDO_CDP_SOCKET_TIMEOUT&&!Reply.Data);}
        Check(Expired);Check(Clock()-Started>=9900&&Clock()-Started<12000);mdoCdpWsClose(Handle);Join(Thread,&Fixture);
    }
    /* A partially written command is retired; no retry can duplicate effects. */
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,5,NULL);Handle=Open();while ( !atomic_load(&Fixture.Ready) ) usleep(1000);
        MdoCdpWsSlot* Slot=MdoCdpWsBorrow(Handle);assert(Slot);int Small=1024;
        assert(!setsockopt(Slot->Session->Fd,SOL_SOCKET,SO_SNDBUF,&Small,sizeof(Small)));MdoCdpWsReturn(Slot,MDO_CDP_SOCKET_OK,0,0);
        unsigned char Data[MDO_CDP_WS_SEND_MAX];memset(Data,'x',sizeof(Data));uint64_t Started=Clock();
        Check(mdoCdpWsSend(Handle,Data,sizeof(Data),40)==MDO_CDP_SOCKET_TIMEOUT);Check(Clock()-Started<500);
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,100)==MDO_CDP_SOCKET_CLOSED);mdoCdpWsClose(Handle);Join(Thread,&Fixture);
    }
    /* Cancellation is safe across a live borrower and is not stalled by Io. */
    for ( int Repeat=0;Repeat<64;Repeat++ ) {
        Server Fixture;pthread_t Thread=Start(&Fixture,3,NULL);Handle=Open();pthread_t Reader;assert(!pthread_create(&Reader,NULL,BlockedRead,&Handle));
        int Busy=0;
        for ( int I=0;I<1000;I++ ) {MdoCdpWsSlot* Slot=MdoCdpWsBorrow(Handle);assert(Slot);
            int Got=pthread_mutex_trylock(&Slot->Session->Io);if ( !Got ) pthread_mutex_unlock(&Slot->Session->Io);
            MdoCdpWsReturn(Slot,MDO_CDP_SOCKET_OK,0,0);if ( Got ) {Busy=1;break;}usleep(1000);}
        Check(Busy);MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,10);Check(Reply.Code==MDO_CDP_SOCKET_BUSY);
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,10)==MDO_CDP_SOCKET_BUSY);
        uint64_t Started=Clock();mdoCdpWsClose(Handle);mdoCdpWsClose(Handle);assert(!pthread_join(Reader,NULL));Check(Clock()-Started<1000);
        Join(Thread,&Fixture);Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,10)==MDO_CDP_SOCKET_CLOSED);Check(Fds()==Before);
    }
    /* Invalid caller text is refused before writing; the channel stays usable. */
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,7,NULL);Handle=Open();
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"\xC0\x80",2,100)==MDO_CDP_SOCKET_ARGUMENTS);
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"ok",2,100)==MDO_CDP_SOCKET_OK);
        mdoCdpWsClose(Handle);Join(Thread,&Fixture);Check(Fixture.Frames==1);
    }
    /* Allocate/grow failure clears all capacities and retires the stream. */
    for ( int Fault=1;Fault<=3;Fault++ ) {
        Server Fixture;pthread_t Thread=Start(&Fixture,8,NULL);Handle=Open();FailAllocation=Fault;
        MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,1000);Check(Reply.Code==MDO_CDP_SOCKET_MEMORY&&!Reply.Data);
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,100)==MDO_CDP_SOCKET_CLOSED);mdoCdpWsClose(Handle);Join(Thread,&Fixture);FailAllocation=0;
    }
    /* A failed random source cannot produce an unmasked or reused-key frame. */
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,3,NULL);Handle=Open();FailRandom=1;
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,100)==MDO_CDP_SOCKET_RANDOM);
        Check(mdoCdpWsSend(Handle,(const unsigned char*)"x",1,100)==MDO_CDP_SOCKET_CLOSED);Join(Thread,&Fixture);
    }
    /* Even opening slots are bounded; old handles cannot touch replacement. */
    uint64_t Handles[MDO_CDP_WS_MAX];Server Fixtures[MDO_CDP_WS_MAX];pthread_t Threads[MDO_CDP_WS_MAX];
    for ( size_t I=0;I<MDO_CDP_WS_MAX;I++ ) {Threads[I]=Start(&Fixtures[I],6,NULL);Handles[I]=Open();while ( !atomic_load(&Fixtures[I].Ready) ) usleep(1000);}
    Check(mdoCdpWsOpen("test-001",8,100,&Handle)==MDO_CDP_SOCKET_LIMIT&&!Handle);
    MdoCdpWsSlot* Pins[MDO_CDP_WS_MAX];
    for ( size_t I=0;I<MDO_CDP_WS_MAX;I++ ) {Pins[I]=MdoCdpWsBorrow(Handles[I]);assert(Pins[I]);mdoCdpWsClose(Handles[I]);Join(Threads[I],&Fixtures[I]);}
    Check(mdoCdpWsOpen("test-001",8,100,&Handle)==MDO_CDP_SOCKET_LIMIT&&!Handle);
    for ( size_t I=0;I<MDO_CDP_WS_MAX;I++ ) Check(MdoCdpWsReturn(Pins[I],MDO_CDP_SOCKET_OK,0,0)==MDO_CDP_SOCKET_CLOSED);
    {
        Server Fixture;pthread_t Thread=Start(&Fixture,6,NULL);Handle=Open();Check(Handle>Handles[MDO_CDP_WS_MAX-1]);
        for ( size_t I=0;I<MDO_CDP_WS_MAX;I++ ) {mdoCdpWsClose(Handles[I]);Check(mdoCdpWsSend(Handles[I],(const unsigned char*)"x",1,10)==MDO_CDP_SOCKET_CLOSED);}
        MdoCdpDiscovery Reply=mdoCdpWsReceive(Handle,10);Check(Reply.Code==MDO_CDP_SOCKET_TIMEOUT);
        mdoCdpWsClose(Handle);Join(Thread,&Fixture);
    }
    /* Kernel peer checks also precede every WebSocket opening request. */
    int Sync[2],Observed[2];assert(!pipe(Sync)&&!pipe(Observed));pid_t Owner=getpid(),Child=fork();assert(Child>=0);
    if ( !Child ) {close(Sync[0]);close(Observed[0]);int Listener=Listen(Owner);assert(write(Sync[1],"r",1)==1);
        int Fd=accept(Listener,NULL,NULL);assert(Fd>=0);char Byte;ssize_t N=recv(Fd,&Byte,1,0);assert(write(Observed[1],&N,sizeof(N))==sizeof(N));close(Fd);close(Listener);_exit(0);}
    close(Sync[1]);close(Observed[1]);char Ready;assert(read(Sync[0],&Ready,1)==1);
    Check(mdoCdpWsOpen("test-001",8,1000,&Handle)==MDO_CDP_SOCKET_PEER&&!Handle);ssize_t N=-1;assert(read(Observed[0],&N,sizeof(N))==sizeof(N));Check(N==0);
    close(Sync[0]);close(Observed[0]);int Status;assert(waitpid(Child,&Status,0)==Child);Check(WIFEXITED(Status)&&!WEXITSTATUS(Status));
    FailAllocation=1;Check(mdoCdpWsOpen("test-001",8,100,&Handle)==MDO_CDP_SOCKET_MEMORY&&!Handle);FailAllocation=0;
    TestJniFaults();
    Check(Fds()==Before);Check(Allocated==Freed);
    for ( size_t I=0;I<MDO_CDP_WS_MAX;I++ ) Check(!MdoCdpWsSlots[I].Reserved);
    printf("{\"passed\":true,\"checks\":%d,\"allocations\":%d,\"releases\":%d,\"cancel_cycles\":64,\"descriptor_balanced\":true,\"xrt_rfc6455\":true,\"jni_compiled\":true,\"device_qualified\":false}\n",Checks,(int)Allocated,(int)Freed);return 0;
}
