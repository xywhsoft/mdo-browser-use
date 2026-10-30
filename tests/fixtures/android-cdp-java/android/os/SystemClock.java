package android.os;
/** Explicit fake elapsed clock, never an Android sleep/Doze qualification. */
public final class SystemClock {
    public static volatile long now=Long.MIN_VALUE;
    public static volatile Runnable afterRead;
    public static long elapsedRealtime(){
        long value=now==Long.MIN_VALUE?System.nanoTime()/1000000L:now;Runnable next=afterRead;
        if(next!=null){afterRead=null;next.run();}
        return value;
    }
}
