#ifndef MDO_BROWSER_CDP_JNI_INC_C
#define MDO_BROWSER_CDP_JNI_INC_C
#include <jni.h>
#include "MdoBrowserCdpWebSocket.c"

static void MdoCdpJniThrow(JNIEnv* Env,enum MdoCdpSocketCode Code)
{
    if ( (*Env)->ExceptionCheck(Env) ) return;
    jclass Error=(*Env)->FindClass(Env,"java/io/IOException");
    if ( Error ) {(*Env)->ThrowNew(Env,Error,mdoCdpSocketCode(Code));(*Env)->DeleteLocalRef(Env,Error);}
}
static jbyteArray MdoCdpJniBytes(JNIEnv* Env,MdoCdpDiscovery* Reply,uint64_t Handle)
{
    jbyteArray Bytes=(*Env)->NewByteArray(Env,(jsize)Reply->Size);
    if ( Bytes ) (*Env)->SetByteArrayRegion(Env,Bytes,0,(jsize)Reply->Size,(const jbyte*)Reply->Data);
    mdoCdpDiscoveryFree(Reply);
    if ( !Bytes || (*Env)->ExceptionCheck(Env) ) {
        /* A lost CDP event/reply invalidates the dispatcher stream. */
        mdoCdpWsClose(Handle);return NULL;
    }
    return Bytes;
}

JNIEXPORT jbyteArray JNICALL
Java_org_xserver_android_MdoBrowserCdpNative_discoverNative(JNIEnv* Env,jclass Type,jint Timeout)
{
    (void)Type;
    MdoCdpDiscovery Reply=mdoCdpDiscover(Timeout);
    if ( Reply.Code!=MDO_CDP_SOCKET_OK ) {
        enum MdoCdpSocketCode Code=Reply.Code;
        mdoCdpDiscoveryFree(&Reply);
        MdoCdpJniThrow(Env,Code);
        return NULL;
    }
    return MdoCdpJniBytes(Env,&Reply,0);
}

JNIEXPORT jlong JNICALL
Java_org_xserver_android_MdoBrowserCdpNative_openNative(JNIEnv* Env,jclass Type,jbyteArray Id,jint Timeout)
{
    (void)Type;unsigned char Text[128];uint64_t Handle=0;
    enum MdoCdpSocketCode Code=MDO_CDP_SOCKET_ARGUMENTS;
    jsize Size=Id?(*Env)->GetArrayLength(Env,Id):0;
    if ( (*Env)->ExceptionCheck(Env) ) return 0;
    if ( Size<1 || Size>(jsize)sizeof(Text) || Timeout<10 || Timeout>5000 ) goto Done;
    (*Env)->GetByteArrayRegion(Env,Id,0,Size,(jbyte*)Text);
    if ( !(*Env)->ExceptionCheck(Env) ) Code=mdoCdpWsOpen((const char*)Text,(size_t)Size,Timeout,&Handle);
Done:
    MdoCdpClear(Text,sizeof(Text));
    if ( Code!=MDO_CDP_SOCKET_OK ) MdoCdpJniThrow(Env,Code);
    return (jlong)Handle;
}

JNIEXPORT void JNICALL
Java_org_xserver_android_MdoBrowserCdpNative_sendNative(JNIEnv* Env,jclass Type,jlong Handle,jbyteArray Bytes,jint Timeout)
{
    (void)Type;jsize Size=Bytes?(*Env)->GetArrayLength(Env,Bytes):0;
    if ( (*Env)->ExceptionCheck(Env) ) return;
    if ( Size<1 || Size>(jsize)MDO_CDP_WS_SEND_MAX || Timeout<10 || Timeout>5000 || Handle<=0 ) {
        MdoCdpJniThrow(Env,MDO_CDP_SOCKET_ARGUMENTS);return;
    }
    unsigned char* Data=MDO_CDP_ALLOC((size_t)Size);
    if ( !Data ) {mdoCdpWsClose((uint64_t)Handle);MdoCdpJniThrow(Env,MDO_CDP_SOCKET_MEMORY);return;}
    (*Env)->GetByteArrayRegion(Env,Bytes,0,Size,(jbyte*)Data);
    enum MdoCdpSocketCode Code=MDO_CDP_SOCKET_IO;
    if ( !(*Env)->ExceptionCheck(Env) ) Code=mdoCdpWsSend((uint64_t)Handle,Data,(size_t)Size,Timeout);
    else mdoCdpWsClose((uint64_t)Handle);
    MdoCdpClear(Data,(size_t)Size);MDO_CDP_FREE(Data);
    if ( Code!=MDO_CDP_SOCKET_OK ) MdoCdpJniThrow(Env,Code);
}

JNIEXPORT jbyteArray JNICALL
Java_org_xserver_android_MdoBrowserCdpNative_receiveNative(JNIEnv* Env,jclass Type,jlong Handle,jint Timeout)
{
    (void)Type;MdoCdpDiscovery Reply=mdoCdpWsReceive((uint64_t)Handle,Timeout);
    if ( Reply.Code!=MDO_CDP_SOCKET_OK ) {
        enum MdoCdpSocketCode Code=Reply.Code;mdoCdpDiscoveryFree(&Reply);
        if ( Code!=MDO_CDP_SOCKET_TIMEOUT ) MdoCdpJniThrow(Env,Code);
        return NULL; /* A normal idle timeout never publishes partial text. */
    }
    return MdoCdpJniBytes(Env,&Reply,(uint64_t)Handle);
}

JNIEXPORT void JNICALL
Java_org_xserver_android_MdoBrowserCdpNative_closeNative(JNIEnv* Env,jclass Type,jlong Handle)
{
    (void)Env;(void)Type;mdoCdpWsClose((uint64_t)Handle);
}
#endif
