package android.os;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.LinkedHashMap;
import java.util.Map;

/** Framework calls are doubled; the real adapter and binding core run. */
public final class Handler {
    public static boolean fail;
    private static final ConcurrentLinkedQueue<Runnable> QUEUE=new ConcurrentLinkedQueue<>();
    private static final Map<Runnable,Long> TIMERS=new java.util.concurrent.ConcurrentHashMap<>();
    public Handler(Looper looper){if(looper!=Looper.getMainLooper())throw new AssertionError("Unexpected test Looper");}
    public boolean post(Runnable task){QUEUE.add(task);return true;}
    public boolean postDelayed(Runnable task,long delay){if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Timer requires UI owner");if(fail)return false;TIMERS.put(task,SystemClock.elapsedRealtime()+delay);return true;}
    public void removeCallbacks(Runnable task){QUEUE.remove(task);TIMERS.remove(task);}
    public static int drain(){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Test queue needs UI thread");
        int count=0;Runnable task;while(count<128&&(task=QUEUE.poll())!=null){task.run();count++;}
        for(Map.Entry<Runnable,Long> timer:new LinkedHashMap<>(TIMERS).entrySet())if(timer.getValue()<=SystemClock.elapsedRealtime()&&TIMERS.remove(timer.getKey())!=null){timer.getKey().run();count++;}return count;
    }
}
