#ifndef MDO_BROWSER_CDP_JNI_INC_C
#define MDO_BROWSER_CDP_JNI_INC_C
#include <jni.h>
#include "MdoBrowserCdpSocket.c"

JNIEXPORT jbyteArray JNICALL
Java_org_xserver_android_MdoBrowserCdpNative_discoverNative(JNIEnv* Env,jclass Type,jint Timeout)
{
    (void)Type;
    MdoCdpDiscovery Reply=mdoCdpDiscover(Timeout);
    if ( Reply.Code!=MDO_CDP_SOCKET_OK ) {
        const char* Code=mdoCdpSocketCode(Reply.Code);
        mdoCdpDiscoveryFree(&Reply);
        jclass Error=(*Env)->FindClass(Env,"java/io/IOException");
        if ( Error ) {(*Env)->ThrowNew(Env,Error,Code);(*Env)->DeleteLocalRef(Env,Error);}
        return NULL;
    }
    jbyteArray Bytes=(*Env)->NewByteArray(Env,(jsize)Reply.Size);
    if ( Bytes ) (*Env)->SetByteArrayRegion(Env,Bytes,0,(jsize)Reply.Size,(const jbyte*)Reply.Data);
    mdoCdpDiscoveryFree(&Reply); /* Also on Java allocation/copy exceptions. */
    return (*Env)->ExceptionCheck(Env)?NULL:Bytes;
}
#endif
