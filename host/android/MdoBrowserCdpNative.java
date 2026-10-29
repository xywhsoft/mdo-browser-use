package org.xserver.android;

import android.os.Looper;
import java.io.IOException;

/** Private read-only discovery boundary. No debug setting, URL or raw CDP
 * command is accepted. Caller parses/clears these bytes and never exposes the
 * target list through Agent/tool JSON, logs or persistent storage. */
final class MdoBrowserCdpNative {
    private MdoBrowserCdpNative() {}
    private static final class Library {
        static { System.loadLibrary("xs"); }
        static void ready() {}
    }
    static byte[] discover(int timeoutMs) throws IOException {
        if (Looper.myLooper()==Looper.getMainLooper())
            throw new IOException("cdp_requires_background_thread");
        if (timeoutMs<10||timeoutMs>5000)throw new IOException("cdp_arguments_invalid");
        try {
            Library.ready();
            return discoverNative(timeoutMs);
        } catch (UnsatisfiedLinkError failure) {
            throw new IOException("cdp_native_unavailable");
        }
    }
    private static native byte[] discoverNative(int timeoutMs) throws IOException;
}
