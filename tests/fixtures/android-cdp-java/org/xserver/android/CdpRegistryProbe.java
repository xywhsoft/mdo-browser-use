package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual registry/lifecycle/input/marker adapters; UI framework is doubled.
 * This proves host ownership and boundedness, not installed provider behavior. */
public final class CdpRegistryProbe {
    private static int checks;private static ExecutorService worker;
    private static void check(boolean value){if(!value)throw new AssertionError("Registry check "+(checks+1));checks++;}
    private static void rejects(Runnable operation){try{operation.run();throw new AssertionError("Missing registry rejection");}
        catch(IllegalStateException|IllegalArgumentException expected){checks++;}}
    private static <T>T run(Callable<T> operation)throws Exception {
        Future<T> result=worker.submit(operation);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!result.isDone()&&System.nanoTime()<deadline){Handler.drain();Thread.sleep(1);}
        try{return result.get(2,TimeUnit.SECONDS);}finally{Handler.drain();}
    }
    private static final class Case {
        final Object channel=new Object();final List<Runnable> tasks=new ArrayList<>();final AtomicInteger closed=new AtomicInteger();
        final MdoBrowserCdpRegistry<WebView> registry=new MdoBrowserCdpRegistry<>((view,document,current)->
            new MdoBrowserCdpView(view,owned->owned==view&&current.getAsBoolean(),document).marker());
        final MdoBrowserLifecycle<WebView> views=new MdoBrowserLifecycle<>((view,epoch,admitted)->new MdoBrowserInput(new MdoBrowserInput.Target(){
            public long document(){return epoch.getAsLong();}public int width(){return 100;}public int height(){return 100;}
            public boolean interactive(){return admitted.getAsBoolean();}public boolean focused(){return true;}
            public boolean touch(int action,long down,long time,float x,float y){return true;}
            public boolean key(boolean down,long started,long time,String name,int modifiers){return true;}
            public boolean text(String value){return true;}
        },new MdoBrowserInput.Scheduler(){
            public long now(){return 100;}public void post(Runnable task,long delay){tasks.add(task);}public void remove(Runnable task){tasks.remove(task);}
        }),registry::invalidate);
        MdoBrowserCdpMarker marker(String key,WebView view){return registry.marker(key,view,views.epoch(key,view),channel,()->views.owns(key,view));}
        void close(){views.close();registry.close();check(tasks.isEmpty()&&registry.size()==0);}
    }
    private static void lifecycle()throws Exception {
        for(String reason:Arrays.asList("navigation","hidden","manual","background","scale","stop","dialog","chooser","popup","remove","close")){
            Case sample=new Case();WebView view=new WebView();sample.views.put("tab",view);
            MdoBrowserCdpMarker marker=sample.marker("tab",view);check(marker==sample.marker("tab",view));
            run(()->{marker.lease().track(()->sample.closed.incrementAndGet());return null;});
            AtomicBoolean completed=new AtomicBoolean();sample.views.input("tab",view).tap(1,1,result->{
                check(!marker.lease().current()&&sample.closed.get()==1);completed.set(true);
            });
            if(reason.equals("navigation"))sample.views.navigation("tab",view);
            else if(reason.equals("hidden"))sample.views.visibility("tab",view,false);
            else if(reason.equals("manual"))sample.views.manual("tab",view,true);
            else if(reason.equals("background"))sample.views.background(true);
            else if(reason.equals("scale"))sample.views.scaleChanged("tab",view,2);
            else if(reason.equals("stop"))sample.views.stop("tab",view);
            else if(reason.equals("remove"))sample.views.remove("tab");
            else if(reason.equals("close"))sample.views.close();
            else sample.views.input("tab",view).invalidate(reason.equals("dialog")?"input_dialog_open":reason.equals("chooser")?"file_chooser_open":"popup_pending");
            check(completed.get()&&sample.tasks.isEmpty()&&!marker.lease().current());sample.close();check(sample.closed.get()==1);
        }
    }
    private static void quota()throws Exception {
        Case sample=new Case();WebView view=new WebView();sample.views.put("tab",view);
        for(int i=0;i<16;i++){
            MdoBrowserCdpMarker marker=sample.marker("tab",view);
            run(()->{MdoBrowserCdpMarker.Ticket ticket=marker.request();check(ticket.await(200));ticket.close();return null;});
            if(i%2==0)sample.registry.invalidateAll();else sample.views.input("tab",view).invalidate("file_chooser_open");
            check(!marker.lease().current());
        }
        MdoBrowserCdpMarker exhausted=sample.registry.marker("tab",view,1,new Object(),()->true);
        run(()->{MdoBrowserCdpMarker.Ticket ticket=exhausted.request();check(!ticket.await(200)&&"cdp_binding_document_limit".equals(ticket.failure()));ticket.close();return null;});
        check(view.evaluations==16);rejects(()->sample.registry.marker("tab",view,2,sample.channel,()->true));
        sample.views.navigation("tab",view);MdoBrowserCdpMarker fresh=sample.marker("tab",view);
        run(()->{MdoBrowserCdpMarker.Ticket ticket=fresh.request();check(ticket.await(200));ticket.close();return null;});
        check(view.evaluations==17&&!exhausted.lease().current());sample.close();
    }
    private static void identity()throws Exception {
        Case sample=new Case();WebView original=new WebView(),next=new WebView();sample.views.put("tab",original);
        MdoBrowserCdpMarker old=sample.marker("tab",original);sample.views.remove("tab");sample.views.put("tab",next);
        MdoBrowserCdpMarker replacement=sample.marker("tab",next);
        sample.registry.invalidate("tab",original,90,"input_closed");check(replacement.lease().current()&&!old.lease().current());
        rejects(()->sample.registry.marker("tab",original,1,sample.channel,()->true));
        rejects(()->sample.registry.marker("other",next,1,sample.channel,()->true));
        rejects(()->sample.registry.marker("bad",new WebView(),1,sample.channel,()->false));
        for(int i=1;i<8;i++){String key="tab"+i;WebView view=new WebView();sample.views.put(key,view);sample.marker(key,view);}
        check(sample.registry.size()==8);rejects(()->sample.registry.marker("overflow",new WebView(),1,sample.channel,()->true));
        run(()->{rejects(()->sample.registry.invalidateAll());return null;});sample.close();
        rejects(()->sample.registry.marker("late",new WebView(),1,sample.channel,()->true));
    }
    private static void lateCallback()throws Exception {
        Case sample=new Case();WebView view=new WebView();view.defer=true;sample.views.put("tab",view);
        MdoBrowserCdpMarker marker=sample.marker("tab",view);
        MdoBrowserCdpMarker.Ticket ticket=run(()->{MdoBrowserCdpMarker.Ticket pending=marker.request();check(!pending.await(30));return pending;});
        sample.views.navigation("tab",view);view.finish("null");check(!ticket.confirmed()&&!marker.lease().current());
        sample.close();
        MdoBrowserCdpMarker.DocumentQuota quota=new MdoBrowserCdpMarker.DocumentQuota();quota.close();
        MdoBrowserCdpView retired=new MdoBrowserCdpView(new WebView(),v->true,quota);
        run(()->{MdoBrowserCdpMarker.Ticket denied=retired.marker().request();check(!denied.await(200)&&"cdp_binding_document_limit".equals(denied.failure()));denied.close();return null;});retired.close();
    }
    public static void main(String[] ignored)throws Exception {
        Looper.getMainLooper();worker=Executors.newSingleThreadExecutor();
        try{lifecycle();quota();identity();lateCallback();}
        finally{worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));Handler.drain();}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"native_ui_calls_doubled\":true,\"provider_qualified\":false}");
    }
}
