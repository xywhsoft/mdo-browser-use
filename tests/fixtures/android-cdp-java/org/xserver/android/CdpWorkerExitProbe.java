package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Hold the real native-retirement gate after ThreadPoolExecutor terminates.
 * No product test hook or extra executor; this deterministically exposes the
 * difference between the pool's task loop and the original outer worker. */
public final class CdpWorkerExitProbe {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Worker exit check "+(checks+1));checks++;}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void waitFor(java.util.function.BooleanSupplier condition)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean()){if(System.nanoTime()>=until)throw new AssertionError("Worker exit wait expired");Handler.drain();Thread.sleep(1);}checks++;
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case()){
            @SuppressWarnings("unchecked") java.util.Set<Thread> records=(java.util.Set<Thread>)field(host.worker,"nativeThreads");
            // A factory thread whose start failed owns no native I/O. Inject
            // that data-only state, not an extra running test thread.
            Thread neverStarted=new Thread(()->{});records.add(neverStarted);check(neverStarted.getState()==Thread.State.NEW);
            CdpNetworkBindingProbe.Context source=host.source();host.ready(source);
            check(!records.contains(neverStarted)&&records.size()==1);
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            host.worker.submit(host.document(),10000,false,(binding,deadline)->{
                entered.countDown();boolean interrupted=false;
                for(;;)try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException("fixture_release_timeout");break;}catch(InterruptedException error){interrupted=true;}
                if(interrupted)Thread.currentThread().interrupt();return new byte[]{1};
            },result->{});
            try{
                waitFor(()->entered.getCount()==0);host.worker.close();
                Object networks=field(host.worker,"networks"),gate=field(networks,"gate");
                ThreadPoolExecutor executor=(ThreadPoolExecutor)field(host.worker,"executor");
                synchronized(gate){
                    release.countDown();waitFor(executor::isTerminated);
                    check(!host.worker.terminated()); // Native retirement is blocked by this exact gate.
                    check(host.worker.networkPending()==1);
                }
                waitFor(host.worker::terminated);host.ui.drain();check(host.worker.networkPending()==0&&host.worker.pending()==0&&host.worker.popupPending()==0);
            }finally{release.countDown();}
        }
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"original_worker_exit_fence\":true,\"provider_qualified\":false}");
    }
}
