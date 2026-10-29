package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Real private worker + marker/binder/strict JNI, controlled UI delivery and
 * synthetic protocol peers. Does not qualify Android/provider behavior. */
public final class CdpWorkerProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean value){if(!value)throw new AssertionError("Worker check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Action {void run()throws Exception;}
    private static void rejects(String code,Action action)throws Exception {
        try{action.run();throw new AssertionError("Missing worker rejection");}catch(IOException error){check(code.equals(error.getMessage()));}
    }
    private static void waitFor(BooleanSupplier done)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!done.getAsBoolean()&&System.nanoTime()<end){Handler.drain();Thread.sleep(1);}check(done.getAsBoolean());
    }
    private static void zero(byte[] bytes){for(byte value:bytes)check(value==0);}
    private static final class Ui implements MdoBrowserCdpWorker.Ui {
        final Thread owner=Thread.currentThread();final ConcurrentLinkedQueue<Runnable> tasks=new ConcurrentLinkedQueue<>();
        volatile boolean reject,fail;
        public boolean onThread(){return Thread.currentThread()==owner;}
        public boolean post(Runnable task){if(fail)throw new IllegalStateException("Synthetic scheduler failure");if(reject)return false;tasks.add(task);return true;}
        public void remove(Runnable task){tasks.remove(task);}
        void drain(){Runnable task;while((task=tasks.poll())!=null)task.run();}
    }
    private static final class Case {
        final Ui ui=new Ui();final AtomicLong time=new AtomicLong(100);final AtomicInteger connects=new AtomicInteger();
        final AtomicReference<Thread> owner=new AtomicReference<>();final List<MdoBrowserCdpWorker.Result> results=new ArrayList<>();
        final ConcurrentHashMap<MdoBrowserCdpMarker,WebView> views=new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<CdpBindingProbe.Factory> peers=new ConcurrentLinkedQueue<>();
        final MdoBrowserCdpWorker worker=new MdoBrowserCdpWorker(ui,(marker,budget)->{
            Thread current=Thread.currentThread();owner.compareAndSet(null,current);check(owner.get()==current&&!ui.onThread());connects.incrementAndGet();
            CdpBindingProbe.Factory factory=new CdpBindingProbe.Factory(views.get(marker));peers.add(factory);
            return MdoBrowserCdpBinding.bind(marker,budget,(method,session,params)->{},timeout->Arrays.asList("owned"),factory);
        },time::get);
        final MdoBrowserCdpRegistry<WebView> registry=new MdoBrowserCdpRegistry<>((view,document,current)->{
            MdoBrowserCdpMarker marker=new MdoBrowserCdpView(view,v->current.getAsBoolean(),document).marker();views.put(marker,view);return marker;
        },worker::cancel);
        MdoBrowserCdpMarker marker(int number){return registry.marker("view"+number,new WebView(),1,new Object(),()->true);}
        MdoBrowserCdpMarker extra(){WebView view=new WebView();MdoBrowserCdpMarker marker=new MdoBrowserCdpView(view,v->true).marker();views.put(marker,view);return marker;}
        void submit(MdoBrowserCdpMarker marker,boolean effect,MdoBrowserCdpWorker.Operation operation)throws Exception {
            worker.submit(marker,1500,effect,operation,result->{check(ui.onThread());results.add(result);});
        }
        void deliver(int count)throws Exception {waitFor(()->ui.tasks.size()>=count);ui.drain();}
        void close()throws Exception {
            worker.close();registry.close();for(MdoBrowserCdpMarker marker:views.keySet())marker.close();
            waitFor(worker::terminated);ui.drain();Handler.drain();check(worker.pending()==0&&ui.tasks.isEmpty());
            for(CdpBindingProbe.Factory peer:peers)peer.balanced();views.clear();
        }
    }
    private static byte[] value(){return new byte[]{1,2,3,4};}
    private static void reuse()throws Exception {
        Case sample=new Case();MdoBrowserCdpMarker marker=sample.marker(0);List<byte[]> retained=new ArrayList<>();
        for(int i=0;i<3;i++){
            sample.worker.submit(marker,1500,i==2,(binding,deadline)->{
                check(Thread.currentThread()==sample.owner.get());
                JSONObject frame=binding.call("Page.getFrameTree",new JSONObject(),Math.min(5000,deadline.remaining()));
                check(frame.has("frameTree"));return value();
            },result->{check(result.code==null&&Arrays.equals(result.bytes,value()));retained.add(result.bytes);sample.results.add(result);});
            waitFor(()->sample.ui.tasks.size()==1);check(sample.worker.pending()==1);
            rejects("cdp_worker_busy",()->sample.submit(marker,false,(binding,deadline)->value()));
            sample.ui.drain();check(sample.worker.pending()==0);
        }
        check(sample.connects.get()==1&&sample.results.size()==3&&sample.results.get(2).effectMayHaveOccurred);
        for(byte[] bytes:retained)zero(bytes);sample.close();
    }
    private static void capacity()throws Exception {
        Case sample=new Case();CountDownLatch release=new CountDownLatch(1);AtomicInteger ran=new AtomicInteger();
        List<MdoBrowserCdpMarker> markers=new ArrayList<>();for(int i=0;i<8;i++)markers.add(sample.marker(i));
        sample.submit(markers.get(0),true,(binding,deadline)->{ran.incrementAndGet();
            try{check(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("cdp_worker_cancelled");}return value();});
        waitFor(()->ran.get()==1);
        for(int i=1;i<8;i++)sample.submit(markers.get(i),false,(binding,deadline)->{ran.incrementAndGet();return value();});
        MdoBrowserCdpMarker overflow=sample.extra();rejects("cdp_worker_limit",()->sample.submit(overflow,false,(binding,deadline)->value()));
        sample.worker.cancel(markers.get(1).lease());check(sample.worker.pending()==8&&sample.ui.tasks.size()==1);
        rejects("cdp_worker_limit",()->sample.submit(overflow,false,(binding,deadline)->value()));
        sample.ui.drain();check(sample.worker.pending()==7&&sample.results.size()==1&&"cdp_worker_cancelled".equals(sample.results.get(0).code)&&!sample.results.get(0).effectMayHaveOccurred);
        sample.submit(overflow,false,(binding,deadline)->{ran.incrementAndGet();return value();});release.countDown();
        sample.deliver(8);check(ran.get()==8&&sample.connects.get()==8&&sample.results.size()==9);sample.close();
    }
    private static void queueDeadline()throws Exception {
        Case sample=new Case();CountDownLatch release=new CountDownLatch(1);AtomicInteger ran=new AtomicInteger();
        MdoBrowserCdpMarker first=sample.marker(0),second=sample.marker(1);
        sample.submit(first,true,(binding,deadline)->{ran.incrementAndGet();
            try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}return value();});
        waitFor(()->ran.get()==1);sample.submit(second,false,(binding,deadline)->{ran.incrementAndGet();return value();});
        sample.time.addAndGet(2000);release.countDown();sample.deliver(2);
        check(ran.get()==1&&sample.connects.get()==1&&sample.results.size()==2);
        check(sample.results.stream().allMatch(result->"cdp_worker_timeout".equals(result.code)&&result.bytes==null));
        check(sample.results.get(0).effectMayHaveOccurred&&!sample.results.get(1).effectMayHaveOccurred);sample.close();
    }
    private static void cancelAndLate()throws Exception {
        Case sample=new Case();MdoBrowserCdpMarker marker=sample.marker(0);CountDownLatch release=new CountDownLatch(1);AtomicInteger ran=new AtomicInteger();AtomicReference<byte[]> late=new AtomicReference<>();
        sample.submit(marker,true,(binding,deadline)->{ran.incrementAndGet();
            try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}
            byte[] bytes=value();late.set(bytes);return bytes;});
        waitFor(()->ran.get()==1);WebView view=sample.views.get(marker);sample.registry.invalidate("view0",view,2,"frame_changed");
        check(sample.results.isEmpty());sample.ui.drain();check(sample.results.size()==1&&"cdp_worker_cancelled".equals(sample.results.get(0).code)&&sample.results.get(0).effectMayHaveOccurred);
        release.countDown();waitFor(()->late.get()!=null);sample.close();zero(late.get());check(sample.results.size()==1);
    }
    private static void failures()throws Exception {
        Case sample=new Case();MdoBrowserCdpMarker marker=sample.marker(0);AtomicReference<byte[]> large=new AtomicReference<>();
        sample.submit(marker,true,(binding,deadline)->{byte[] bytes=new byte[1024*1024+1];Arrays.fill(bytes,(byte)9);large.set(bytes);return bytes;});
        sample.deliver(1);check("cdp_worker_result_invalid".equals(sample.results.get(0).code)&&sample.results.get(0).effectMayHaveOccurred);
        boolean allZero=true;for(byte b:large.get())if(b!=0)allZero=false;check(allZero);
        sample.submit(marker,false,(binding,deadline)->{throw new IOException("private-provider-text");});sample.deliver(1);
        check("cdp_worker_failed".equals(sample.results.get(1).code));
        sample.submit(marker,false,(binding,deadline)->{throw new OutOfMemoryError("synthetic");});sample.deliver(1);
        check("cdp_worker_failed".equals(sample.results.get(2).code));
        AtomicReference<byte[]> returned=new AtomicReference<>();sample.worker.submit(marker,1500,false,(binding,deadline)->value(),result->{returned.set(result.bytes);throw new IllegalStateException("synthetic UI callback failure");});
        sample.deliver(1);zero(returned.get());check(!marker.lease().current());
        sample.submit(sample.marker(1),false,(binding,deadline)->value());sample.deliver(1);check(sample.results.get(3).code==null);sample.close();
    }
    private static void bindingCancellation()throws Exception {
        Case sample=new Case();MdoBrowserCdpMarker marker=sample.marker(0);WebView view=sample.views.get(marker);view.defer=true;
        AtomicInteger ran=new AtomicInteger();sample.submit(marker,true,(binding,deadline)->{ran.incrementAndGet();return value();});
        waitFor(()->view.evaluations==1);sample.registry.invalidateAll();sample.deliver(1);
        check(sample.results.size()==1&&!sample.results.get(0).effectMayHaveOccurred&&"cdp_worker_cancelled".equals(sample.results.get(0).code));
        view.finish("null");sample.close();check(ran.get()==0&&sample.results.size()==1);
        Case late=new Case();MdoBrowserCdpMarker original=late.marker(0);AtomicReference<byte[]> data=new AtomicReference<>();
        late.submit(original,false,(binding,deadline)->{byte[] value=value();data.set(value);return value;});waitFor(()->late.ui.tasks.size()==1);
        original.close();late.ui.drain();check("cdp_worker_cancelled".equals(late.results.get(0).code));zero(data.get());late.close();
    }
    private static void invalidAdmission()throws Exception {
        Case sample=new Case();MdoBrowserCdpMarker marker=sample.marker(0);
        rejects("cdp_worker_arguments_invalid",()->sample.worker.submit(marker,99,false,(binding,deadline)->value(),result->{}));
        rejects("cdp_worker_arguments_invalid",()->sample.worker.submit(marker,15001,false,(binding,deadline)->value(),result->{}));
        AtomicInteger wrongThread=new AtomicInteger();Thread other=new Thread(()->{
            try{sample.worker.submit(marker,1000,false,(binding,deadline)->value(),result->{});}
            catch(IllegalStateException expected){wrongThread.incrementAndGet();}catch(IOException unexpected){throw new AssertionError(unexpected);}
        });other.start();other.join();check(wrongThread.get()==1&&sample.worker.pending()==0&&sample.connects.get()==0);sample.close();
    }
    private static void scheduler()throws Exception {
        for(int mode=0;mode<3;mode++){
            Case sample=new Case();MdoBrowserCdpMarker marker=sample.marker(0);AtomicReference<byte[]> data=new AtomicReference<>();
            sample.ui.reject=mode==0;sample.ui.fail=mode==1;
            sample.submit(marker,false,(binding,deadline)->{byte[] value=value();data.set(value);return value;});
            if(mode==2){waitFor(()->sample.ui.tasks.size()==1);sample.close();}
            else {waitFor(sample.worker::terminated);check(sample.worker.pending()==0&&!marker.lease().current());
                rejects("cdp_worker_closed",()->sample.submit(marker,false,(binding,deadline)->value()));sample.close();}
            check(sample.results.isEmpty());zero(data.get());
        }
    }
    public static void main(String[] ignored)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");reuse();capacity();queueDeadline();cancelAndLate();failures();bindingCancellation();invalidAdmission();scheduler();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_ui_calls_doubled\":true,\"provider_qualified\":false}");
    }
}
