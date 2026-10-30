package android.os;
/** Explicit fake elapsed clock, never an Android sleep/Doze qualification. */
public final class SystemClock {
    public static long now=1000;
    public static long elapsedRealtime(){return now;}
}
