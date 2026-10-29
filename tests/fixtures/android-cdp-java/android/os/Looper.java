package android.os;

/** Only the Android thread identity call is doubled; native JNI/kernel I/O is
 * real. This desktop JVM gate does not qualify the Android framework/provider. */
public final class Looper {
    private static final Thread MAIN_THREAD=Thread.currentThread();
    private static final Looper MAIN=new Looper();
    public static Looper getMainLooper(){return MAIN;}
    public static Looper myLooper(){return Thread.currentThread()==MAIN_THREAD?MAIN:null;}
}
