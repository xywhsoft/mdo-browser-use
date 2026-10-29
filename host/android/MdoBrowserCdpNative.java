package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/** Private native-worker boundary, never an Agent/extension protocol. Target
 * discovery alone does not establish owned WebView identity. Bind the target
 * before browser effects. Caller parses/clears all protocol bytes and keeps
 * target lists/commands/replies out of tool JSON, logs and persistent storage.
 * This class neither enables debugging nor changes the installed provider. */
final class MdoBrowserCdpNative {
    private MdoBrowserCdpNative() {}
    private static final class Library {
        static { System.loadLibrary("xs"); }
        static void ready() {}
    }
    static byte[] discover(int timeoutMs) throws IOException {
        worker(timeoutMs);
        try {
            Library.ready();
            return discoverNative(timeoutMs);
        } catch (UnsatisfiedLinkError failure) {
            throw new IOException("cdp_native_unavailable");
        }
    }
    private static void worker(int timeoutMs) throws IOException {
        if (Looper.myLooper()==Looper.getMainLooper())throw new IOException("cdp_requires_background_thread");
        if (timeoutMs<10||timeoutMs>5000)throw new IOException("cdp_arguments_invalid");
    }
    static Channel open(String targetId,int timeoutMs) throws IOException {
        worker(timeoutMs);
        if (targetId==null||targetId.length()<1||targetId.length()>128)throw new IOException("cdp_arguments_invalid");
        for(int i=0;i<targetId.length();i++) {
            char c=targetId.charAt(i);
            if (!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-'||c=='_'))
                throw new IOException("cdp_arguments_invalid");
        }
        byte[] id=targetId.getBytes(StandardCharsets.US_ASCII);
        // Allocate the wrapper/atomic before opening: OOM cannot lose a handle.
        Channel pending=new Channel(Thread.currentThread());
        try {
            Library.ready();
            pending.handle.set(openNative(id,timeoutMs));
            return pending;
        } catch (UnsatisfiedLinkError failure) {
            throw new IOException("cdp_native_unavailable");
        } finally {Arrays.fill(id,(byte)0);}
    }
    static void validate(byte[] bytes,int maximum) throws IOException {
        worker(10);
        try {Library.ready();validateNative(bytes,maximum);}
        catch(UnsatisfiedLinkError failure){throw new IOException("cdp_native_unavailable");}
    }
    static final class Channel implements MdoBrowserCdpSession.Wire {
        private final Thread owner;
        private final AtomicLong handle=new AtomicLong();
        private Channel(Thread owner){this.owner=owner;}
        private long current(int timeoutMs) throws IOException {
            worker(timeoutMs);
            if(Thread.currentThread()!=owner)throw new IOException("cdp_wrong_worker");
            long value=handle.get();
            if(value==0)throw new IOException("cdp_session_closed");
            return value;
        }
        /** Caller exclusively owns this array for the duration of the call.
         * A failure can mean uncertain delivery. Never resend automatically. */
        @Override public void send(byte[] bytes,int timeoutMs) throws IOException {
            long value=current(timeoutMs);
            if(bytes==null||bytes.length<1||bytes.length>262144)throw new IOException("cdp_arguments_invalid");
            sendNative(value,bytes,timeoutMs);
            if(handle.get()!=value)throw new IOException("cdp_session_closed");
        }
        /** null is an idle timeout; every returned array must be cleared after
         * parsing. Partial packets are retained only in bounded native memory. */
        @Override public byte[] receive(int timeoutMs) throws IOException {
            long value=current(timeoutMs);byte[] reply=receiveNative(value,timeoutMs);
            if(handle.get()!=value) {
                if(reply!=null)Arrays.fill(reply,(byte)0);
                throw new IOException("cdp_session_closed");
            }
            return reply;
        }
        /** May run on a cancelling/UI thread; never waits for the worker. */
        @Override public void close() {
            long value=handle.getAndSet(0);
            if(value!=0)closeNative(value);
        }
    }
    private static native byte[] discoverNative(int timeoutMs) throws IOException;
    private static native long openNative(byte[] targetId,int timeoutMs) throws IOException;
    private static native void sendNative(long handle,byte[] bytes,int timeoutMs) throws IOException;
    private static native byte[] receiveNative(long handle,int timeoutMs) throws IOException;
    private static native void closeNative(long handle);
    private static native void validateNative(byte[] bytes,int maximum) throws IOException;
}
