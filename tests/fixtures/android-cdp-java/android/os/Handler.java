package android.os;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Framework calls are doubled; the real adapter and binding core run. */
public final class Handler {
    private static final ConcurrentLinkedQueue<Runnable> QUEUE=new ConcurrentLinkedQueue<>();
    public Handler(Looper looper){if(looper!=Looper.getMainLooper())throw new AssertionError("Unexpected test Looper");}
    public boolean post(Runnable task){QUEUE.add(task);return true;}
    public void removeCallbacks(Runnable task){QUEUE.remove(task);}
    public static int drain(){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Test queue needs UI thread");
        int count=0;Runnable task;while(count<128&&(task=QUEUE.poll())!=null){task.run();count++;}return count;
    }
}
