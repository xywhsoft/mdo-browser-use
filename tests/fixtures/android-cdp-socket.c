#define _GNU_SOURCE
#include <assert.h>
#include <dirent.h>
#include <errno.h>
#include <pthread.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static int FailAllocation,Allocated,Freed,Checks;
static void* Allocate(size_t Bytes);
static void Release(void* Data);
#define MDO_CDP_ALLOC Allocate
#define MDO_CDP_FREE Release
#include "../../host/android/MdoBrowserCdpJni.inc.c"

typedef struct Allocation {size_t Bytes;unsigned char Data[];} Allocation;
static void* Allocate(size_t Bytes)
{
    if ( FailAllocation ) {FailAllocation=0;return NULL;}
    Allocation* Block=malloc(sizeof(*Block)+Bytes);assert(Block);Block->Bytes=Bytes;
    memset(Block->Data,0xAD,Bytes);Allocated++;return Block->Data;
}
static void Release(void* Data)
{
    Allocation* Block=(Allocation*)((unsigned char*)Data-sizeof(Allocation));
    for ( size_t I=0;I<Block->Bytes;I++ ) assert(Block->Data[I]==0);
    Freed++;free(Block);
}
static void Check(int Value){assert(Value);Checks++;}
static uint64_t Milliseconds(void){uint64_t Now=0;assert(MdoCdpNow(&Now));return Now;}
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
    assert(!listen(Fd,4));return Fd;
}
typedef struct Server {int Listener;const char* Reply;size_t Bytes,Chunk;unsigned Delay;int RequestValid;} Server;
static void* Serve(void* Raw)
{
    Server* Fixture=Raw;int Fd=accept(Fixture->Listener,NULL,NULL);assert(Fd>=0);close(Fixture->Listener);
    struct timeval Wait={1,0};assert(!setsockopt(Fd,SOL_SOCKET,SO_RCVTIMEO,&Wait,sizeof(Wait)));
    char Request[1024]={0};size_t Size=0;
    while ( Size<sizeof(Request)-1 ) {
        ssize_t Read=recv(Fd,Request+Size,sizeof(Request)-1-Size,0);if ( Read<=0 ) break;
        Size+=(size_t)Read;Request[Size]=0;if ( strstr(Request,"\r\n\r\n") ) break;
    }
    Fixture->RequestValid=!strcmp(Request,"GET /json/list HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nAccept: application/json\r\n\r\n");
    for ( size_t Done=0;Done<Fixture->Bytes; ) {
        if ( Fixture->Delay ) usleep(Fixture->Delay*1000u);
        size_t Chunk=Fixture->Chunk?Fixture->Chunk:Fixture->Bytes;
        if ( Chunk>Fixture->Bytes-Done ) Chunk=Fixture->Bytes-Done;
        ssize_t Sent=send(Fd,Fixture->Reply+Done,Chunk,MSG_NOSIGNAL);if ( Sent<=0 ) break;
        Done+=(size_t)Sent;
    }
    close(Fd);return NULL;
}
static MdoCdpDiscovery Request(const char* Reply,size_t Size,size_t Chunk,unsigned Delay,int Timeout)
{
    int Before=Fds();Server Fixture={Listen(getpid()),Reply,Size,Chunk,Delay,0};pthread_t Thread;
    assert(!pthread_create(&Thread,NULL,Serve,&Fixture));MdoCdpDiscovery Value=mdoCdpDiscover(Timeout);
    assert(!pthread_join(Thread,NULL));Check(Fixture.RequestValid);Check(Fds()==Before);return Value;
}
static void Invalid(const char* Reply,enum MdoCdpSocketCode Expected)
{
    MdoCdpDiscovery Value=Request(Reply,strlen(Reply),0,0,500);
    Check(Value.Code==Expected);Check(!Value.Data && !Value.Size && !Value.Capacity);mdoCdpDiscoveryFree(&Value);
}
/* Test the exact JNI conversion with the actual JNI ABI declarations. These
 * few function-table seams inject Java allocation/copy/class-lookup faults;
 * the discovery socket and C allocator underneath remain real. */
typedef struct JavaBytes {jsize Size;unsigned char* Data;} JavaBytes;
static int JavaRaised,JavaAllocationFault,JavaCopyFault,JavaClassFault;
static char JavaError[64];static JavaBytes* LastJavaBytes;
static jclass JavaFind(JNIEnv* Env,const char* Name)
{
    (void)Env;assert(!strcmp(Name,"java/io/IOException"));
    if ( JavaClassFault ) {JavaRaised=1;return NULL;}
    return (jclass)(uintptr_t)1;
}
static jint JavaThrow(JNIEnv* Env,jclass Type,const char* Message)
{(void)Env;assert(Type==(jclass)(uintptr_t)1);JavaRaised=1;snprintf(JavaError,sizeof(JavaError),"%s",Message);return 0;}
static void JavaDelete(JNIEnv* Env,jobject Value){(void)Env;assert(Value==(jobject)(uintptr_t)1);}
static jbyteArray JavaNew(JNIEnv* Env,jsize Size)
{
    (void)Env;if ( JavaAllocationFault ) {JavaRaised=1;return NULL;}
    assert(Size>0 && Size<=262144);LastJavaBytes=calloc(1,sizeof(*LastJavaBytes));assert(LastJavaBytes);
    LastJavaBytes->Size=Size;LastJavaBytes->Data=malloc((size_t)Size);assert(LastJavaBytes->Data);
    return (jbyteArray)LastJavaBytes;
}
static void JavaSet(JNIEnv* Env,jbyteArray Array,jsize Start,jsize Size,const jbyte* Data)
{
    (void)Env;assert((JavaBytes*)Array==LastJavaBytes && !Start && Size==LastJavaBytes->Size);
    if ( JavaCopyFault ) {JavaRaised=1;return;}
    memcpy(LastJavaBytes->Data,Data,(size_t)Size);
}
static jboolean JavaException(JNIEnv* Env){(void)Env;return JavaRaised?JNI_TRUE:JNI_FALSE;}
static const struct JNINativeInterface_ JavaTable={.FindClass=JavaFind,.ThrowNew=JavaThrow,.DeleteLocalRef=JavaDelete,
    .NewByteArray=JavaNew,.SetByteArrayRegion=JavaSet,.ExceptionCheck=JavaException};
static void TestJava(const char* Good,size_t Bytes,int Fault)
{
    int Before=Fds();JavaRaised=0;JavaError[0]=0;LastJavaBytes=NULL;
    JavaAllocationFault=Fault==1;JavaCopyFault=Fault==2;JavaClassFault=Fault==4;FailAllocation=Fault==3||Fault==4;
    Server Fixture={Listen(getpid()),Good,Bytes,0,0,0};pthread_t Thread;assert(!pthread_create(&Thread,NULL,Serve,&Fixture));
    JNIEnv Env=&JavaTable;jbyteArray Value=Java_org_xserver_android_MdoBrowserCdpNative_discoverNative(&Env,NULL,500);
    assert(!pthread_join(Thread,NULL));Check(Fixture.RequestValid);Check(Fds()==Before);
    if ( !Fault ) {Check(!JavaRaised && Value==(jbyteArray)LastJavaBytes);Check(LastJavaBytes->Size==2 && !memcmp(LastJavaBytes->Data,"[]",2));}
    else {Check(JavaRaised && !Value);if ( Fault==3 ) Check(!strcmp(JavaError,"cdp_allocation_failed"));}
    if ( LastJavaBytes ) {memset(LastJavaBytes->Data,0,(size_t)LastJavaBytes->Size);free(LastJavaBytes->Data);free(LastJavaBytes);LastJavaBytes=NULL;}
    JavaRaised=JavaAllocationFault=JavaCopyFault=JavaClassFault=0;
}
int main(void)
{
    int Before=Fds();
    for ( int Timeout=0;Timeout<10;Timeout++ ) {MdoCdpDiscovery V=mdoCdpDiscover(Timeout);Check(V.Code==MDO_CDP_SOCKET_ARGUMENTS);}
    MdoCdpDiscovery LargeTimeout=mdoCdpDiscover(5001);Check(LargeTimeout.Code==MDO_CDP_SOCKET_ARGUMENTS);
    MdoCdpDiscovery Absent=mdoCdpDiscover(100);Check(Absent.Code==MDO_CDP_SOCKET_UNAVAILABLE);Check(Fds()==Before);
    static const char Good[]="HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n[]";
    for ( size_t Chunk=1;Chunk<=16;Chunk*=2 ) {
        MdoCdpDiscovery Value=Request(Good,sizeof(Good)-1,Chunk,0,500);
        Check(Value.Code==MDO_CDP_SOCKET_OK);Check(Value.Size==2 && !memcmp(Value.Data,"[]",2));
        mdoCdpDiscoveryFree(&Value);Check(!Value.Data && !Value.Size && !Value.Capacity);
    }
    static const char Case[]="HTTP/1.0 200\r\ncOnTeNt-LeNgTh:\t2 \r\nCONTENT-TYPE: APPLICATION/JSON\r\n\r\n[]";
    MdoCdpDiscovery CaseResult=Request(Case,sizeof(Case)-1,0,0,500);Check(CaseResult.Code==MDO_CDP_SOCKET_OK);mdoCdpDiscoveryFree(&CaseResult);
    Invalid("HTTP/1.1 302 Found\r\nContent-Length: 2\r\nContent-Type: application/json\r\n\r\n[]",MDO_CDP_SOCKET_HTTP);
    Invalid("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Length: 2\r\nContent-Type: application/json\r\n\r\n[]",MDO_CDP_SOCKET_HTTP);
    Invalid("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nTransfer-Encoding: identity\r\nContent-Type: application/json\r\n\r\n[]",MDO_CDP_SOCKET_HTTP);
    Invalid("HTTP/1.1 200 OK\r\nContent-Length: 262145\r\nContent-Type: application/json\r\n\r\n",MDO_CDP_SOCKET_LIMIT);
    Invalid("HTTP/1.1 200 OK\r\nContent-Length: -1\r\nContent-Type: application/json\r\n\r\n",MDO_CDP_SOCKET_HTTP);
    Invalid("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nContent-Type: application/json\r\n\r\n",MDO_CDP_SOCKET_HTTP);
    Invalid("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Type: text/plain\r\n\r\n[]",MDO_CDP_SOCKET_HTTP);
    Invalid("HTTP/1.1 200 OK\r\n Content-Length: 2\r\nContent-Type: application/json\r\n\r\n[]",MDO_CDP_SOCKET_HTTP);
    Invalid("HTTP/1.1 200 OK\r\nContent-Length: 10\r\nContent-Type: application/json\r\n\r\n[]",MDO_CDP_SOCKET_HTTP);
    Invalid("HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Type: application/json\r\n\r\n[]",MDO_CDP_SOCKET_HTTP);
    char Header[MDO_CDP_HEADER_MAX+100];memset(Header,'a',sizeof(Header));memcpy(Header,"HTTP/1.1 200 OK\r\nLarge: ",22);
    MdoCdpDiscovery HeaderResult=Request(Header,sizeof(Header),0,0,500);Check(HeaderResult.Code==MDO_CDP_SOCKET_LIMIT);mdoCdpDiscoveryFree(&HeaderResult);
    uint64_t Started=Milliseconds();MdoCdpDiscovery Timeout=Request(Good,sizeof(Good)-1,1,10,50);uint64_t Elapsed=Milliseconds()-Started;
    Check(Timeout.Code==MDO_CDP_SOCKET_TIMEOUT);Check(Elapsed>=40 && Elapsed<1000);Check(!Timeout.Data);mdoCdpDiscoveryFree(&Timeout);
    FailAllocation=1;MdoCdpDiscovery Oom=Request(Good,sizeof(Good)-1,0,0,500);
    Check(Oom.Code==MDO_CDP_SOCKET_MEMORY);Check(!Oom.Data);mdoCdpDiscoveryFree(&Oom);
    /* An unrelated process can guess the name while WebView debugging is off.
     * Reject its kernel credentials before disclosing even the GET request. */
    int Sync[2],Observed[2];assert(!pipe(Sync)&&!pipe(Observed));pid_t Parent=getpid(),Child=fork();assert(Child>=0);
    if ( !Child ) {
        close(Sync[0]);close(Observed[0]);int Listener=Listen(Parent);assert(write(Sync[1],"1",1)==1);
        struct pollfd Poll={Listener,POLLIN,0};assert(poll(&Poll,1,1000)>0);
        int Fd=accept(Listener,NULL,NULL);assert(Fd>=0);char Bytes[256];ssize_t Read=recv(Fd,Bytes,sizeof(Bytes),0);
        int Count=Read>0?(int)Read:0;assert(write(Observed[1],&Count,sizeof(Count))==sizeof(Count));close(Fd);close(Listener);_exit(0);
    }
    close(Sync[1]);close(Observed[1]);char Ready;assert(read(Sync[0],&Ready,1)==1);
    MdoCdpDiscovery Foreign=mdoCdpDiscover(500);Check(Foreign.Code==MDO_CDP_SOCKET_PEER);Check(!Foreign.Data);
    int Count=-1;assert(read(Observed[0],&Count,sizeof(Count))==sizeof(Count));Check(Count==0);
    close(Sync[0]);close(Observed[0]);int Status=0;assert(waitpid(Child,&Status,0)==Child);Check(WIFEXITED(Status)&&WEXITSTATUS(Status)==0);
    for ( int I=0;I<64;I++ ) {MdoCdpDiscovery V=Request(Good,sizeof(Good)-1,0,0,500);Check(V.Code==MDO_CDP_SOCKET_OK);mdoCdpDiscoveryFree(&V);}
    for ( int Fault=0;Fault<5;Fault++ ) TestJava(Good,sizeof(Good)-1,Fault);
    JNIEnv Env=&JavaTable;JavaRaised=0;JavaError[0]=0;
    Check(!Java_org_xserver_android_MdoBrowserCdpNative_discoverNative(&Env,NULL,0));
    Check(JavaRaised && !strcmp(JavaError,"cdp_arguments_invalid"));
    Check(Fds()==Before);Check(Allocated==Freed);
    printf("{\"passed\":true,\"checks\":%d,\"real_abstract_unix\":true,\"same_process\":true,\"foreign_pid_refused_before_request\":true,\"jni_conversion_faults\":4,\"allocations\":%d,\"releases\":%d}\n",Checks,Allocated,Freed);
    return 0;
}
