package android.os;
/** Explicit fake elapsed clock, never an Android sleep/Doze qualification. */
public final class SystemClock {
    public static volatile long now=1000;
    public static volatile Runnable afterRead;
    public static long elapsedRealtime(){
        long value=now;Runnable next=afterRead;
        if(next!=null){afterRead=null;next.run();}
        return value;
    }
}
