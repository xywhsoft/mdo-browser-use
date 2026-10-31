package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Actual shared worker/proof/codec/JNI, controlled UI and metadata peers. No
 * provider qualification, physical-view product routing or response storage. */
public final class CdpNetworkWorkerProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private interface Action {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Network worker check "+(checks.get()+1));checks.incrementAndGet();}
    private static void rejected(String code,Action action)throws Exception{
        try{action.run();throw new AssertionError("Missing network worker refusal: "+code);}catch(IOException error){check(code.equals(error.getMessage()));}
    }
    private static void await(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()&&System.nanoTime()<end){Handler.drain();Thread.sleep(1);}check(condition.getAsBoolean());
    }
    private static JSONObject response(int id)throws Exception{return new JSONObject().put("id",id).put("result",new JSONObject());}
    private static JSONObject paused(String id)throws Exception{return new JSONObject().put("method","Fetch.requestPaused").put("params",new JSONObject().put("requestId",id).put("responseStatusCode",200));}
    static final class Ui implements MdoBrowserCdpWorker.Ui {
        final Thread owner=Thread.currentThread();final ConcurrentLinkedQueue<Runnable> tasks=new ConcurrentLinkedQueue<>();boolean reject;
        public boolean onThread(){return Thread.currentThread()==owner;}
        public boolean post(Runnable task){if(reject)return false;tasks.add(task);return true;}
        public void remove(Runnable task){tasks.remove(task);}
        void drain(){Runnable task;while((task=tasks.poll())!=null)task.run();}
    }
    static final class Case implements AutoCloseable {
        final Ui ui=new Ui();final AtomicLong time=new AtomicLong(100);final Set<Thread> threads=ConcurrentHashMap.newKeySet();
        final Map<MdoBrowserCdpMarker,CdpNetworkBindingProbe.Context> sources=new ConcurrentHashMap<>();
        final List<CdpNetworkBindingProbe.Context> owned=new ArrayList<>();final List<MdoBrowserCdpNetworks.Result> states=new ArrayList<>();
        final List<MdoBrowserCdpWorker.Result> documentResults=new ArrayList<>();
        final Map<MdoBrowserCdpMarker,CdpBindingProbe.Factory> documents=new ConcurrentHashMap<>();
        final Map<MdoBrowserCdpMarker,String> documentTargets=new ConcurrentHashMap<>();
        final Map<MdoBrowserCdpMarker,String> networkTargets=new ConcurrentHashMap<>();
        final AtomicInteger controls=new AtomicInteger();final MdoBrowserCdpWorker worker;
        boolean uncertainCleanup;
        Case(){this(MdoBrowserCdpPopupBroker::new);}
        Case(MdoBrowserCdpPopupWorker.Factory popupFactory){worker=new MdoBrowserCdpWorker(ui,(marker,budget)->{
            check(!ui.onThread());threads.add(Thread.currentThread());CdpBindingProbe.Factory peer=documents.get(marker);
            return MdoBrowserCdpBinding.bind(marker,budget,(m,s,p)->{},t->Arrays.asList(documentTargets.getOrDefault(marker,"owned")),
                (id,timeout,current,events)->peer.open("owned",timeout,current,events));
        },time::get,(marker,budget,events)->{
            check(!ui.onThread());threads.add(Thread.currentThread());CdpNetworkBindingProbe.Context source=sources.get(marker);
            return MdoBrowserCdpBinding.bindNetwork(marker,budget,events,t->Arrays.asList(networkTargets.getOrDefault(marker,"owned")),
                (id,timeout,current,forward)->source.peer.open("owned",timeout,current,forward));
        },popupFactory);}
        CdpNetworkBindingProbe.Context source(){CdpNetworkBindingProbe.Context c=new CdpNetworkBindingProbe.Context(true);owned.add(c);sources.put(c.adapter.marker(),c);return c;}
        CdpNetworkBindingProbe.Context source(WebView original,MdoBrowserCdpMarker.DocumentQuota quota){CdpNetworkBindingProbe.Context c=new CdpNetworkBindingProbe.Context(true,original,quota);owned.add(c);sources.put(c.adapter.marker(),c);return c;}
        MdoBrowserCdpMarker document(){WebView view=new WebView();MdoBrowserCdpMarker marker=new MdoBrowserCdpView(view,v->true).marker();documents.put(marker,new CdpBindingProbe.Factory(view));return marker;}
        void start(CdpNetworkBindingProbe.Context source,int timeout)throws Exception {
            worker.startNetwork(source.adapter.marker(),timeout,(connection,method,child,params)->{
                check(!ui.onThread());threads.add(Thread.currentThread());
                if(method.equals("Fetch.requestPaused")){source.controls.add(connection.continuePausedRequest());controls.incrementAndGet();}
            },state->{check(ui.onThread());states.add(state);});
        }
        void deliver(int count)throws Exception{await(()->ui.tasks.size()>=count);ui.drain();}
        void ready(CdpNetworkBindingProbe.Context source)throws Exception{start(source,1500);deliver(1);check(states.get(states.size()-1).ready);}
        void cancelled()throws Exception{await(()->{ui.drain();return worker.networkPending()==0;});}
        @Override public void close()throws Exception{
            worker.close();await(worker::terminated);ui.drain();Handler.drain();
            if(worker.pending()!=0||!uncertainCleanup&&worker.networkPending()!=0)throw new AssertionError("Native worker cleanup retained jobs="+worker.pending()+", sources="+worker.networkPending());
            check(worker.pending()==0&&worker.popupPending()==0&&(uncertainCleanup||worker.networkPending()==0));
            for(CdpNetworkBindingProbe.Context c:owned)c.close();
            for(Map.Entry<MdoBrowserCdpMarker,CdpBindingProbe.Factory> row:documents.entrySet()){row.getKey().close();row.getValue().balanced();}
            check(threads.size()<=1);
        }
    }
    private static void activationAndIdle()throws Exception{
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();source.pausedEnable=true;c.ready(source);
            check(c.controls.get()==1&&source.controls.get(0).result()!=null&&source.controls.get(0).result().ok);
            source.wire().queue(paused("interception-job-idle.0"));await(()->c.controls.get()==2&&source.controls.get(1).result()!=null);
            check(source.controls.get(1).result().ok&&c.worker.networkPending()==1&&c.states.size()==1);
            c.worker.cancelNetwork(source.adapter.marker().lease());c.cancelled();
            check(c.states.size()==2&&!c.states.get(1).ready&&c.states.get(1).cleanupConfirmed&&c.states.get(1).code.equals("cdp_network_cancelled"));
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();c.start(source,1500);await(()->!c.ui.tasks.isEmpty());
            c.worker.cancelNetwork(source.adapter.marker().lease());c.ui.drain();c.cancelled();
            check(c.states.size()==1&&!c.states.get(0).ready&&c.states.get(0).code.equals("cdp_network_cancelled"));
        }
    }
    private static void cooperativeDocumentWait()throws Exception{
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);MdoBrowserCdpMarker marker=c.document();
            final CdpBindingProbe.Wire[] blocked={null};final int[] request={0};
            CdpBindingProbe.Factory.Extra original=source.peer.extra;
            source.peer.extra=(command,wire)->{
                boolean result=original.send(command,wire);
                if(command.getString("method").equals("Fetch.continueRequest")&&blocked[0]!=null)blocked[0].queue(response(request[0]));return result;
            };
            c.documents.get(marker).extra=(command,wire)->{
                if(command.getString("method").equals("Runtime.evaluate")&&command.getJSONObject("params").optString("expression").equals("native-fixture-wait")){
                    blocked[0]=wire;request[0]=command.getInt("id");source.wire().queue(paused("interception-job-cooperative.0"));return true;
                }return false;
            };
            final JSONObject waitParams=new JSONObject().put("expression","native-fixture-wait");
            c.worker.submit(marker,1500,true,(binding,deadline)->{
                binding.call("Runtime.evaluate",waitParams,null,Math.min(1000,deadline.remaining()),true);return new byte[]{1};
            },result->c.documentResults.add(result));
            await(()->!c.ui.tasks.isEmpty());c.ui.drain();await(()->c.controls.get()==1&&source.controls.get(0).result()!=null);
            check(c.documentResults.size()==1&&c.documentResults.get(0).code==null&&source.controls.get(0).result().ok);
            check(c.threads.size()==1&&c.worker.networkPending()==1);
        }
    }
    private static void queueAndCapacity()throws Exception{
        try(Case c=new Case()){
            CountDownLatch release=new CountDownLatch(1),entered=new CountDownLatch(1);MdoBrowserCdpMarker doc=c.document();
            c.worker.submit(doc,1500,false,(binding,deadline)->{
                entered.countDown();try{check(release.await(4,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("cdp_worker_cancelled");}return new byte[]{1};
            },result->c.documentResults.add(result));
            try{
                await(()->entered.getCount()==0);List<CdpNetworkBindingProbe.Context> batch=new ArrayList<>();
                for(int i=0;i<8;i++){CdpNetworkBindingProbe.Context source=c.source();batch.add(source);c.start(source,1500);}
                CdpNetworkBindingProbe.Context ninth=c.source();rejected("cdp_network_owner_limit",()->c.start(ninth,1500));check(c.worker.networkPending()==8);
                c.worker.cancelNetwork(batch.get(0).adapter.marker().lease());check(c.worker.networkPending()==8);c.ui.drain();check(c.worker.networkPending()==7);
                c.start(ninth,1500);release.countDown();await(()->c.ui.tasks.size()>=9);c.ui.drain();check(c.worker.networkPending()==8);
                check(c.states.stream().filter(state->state.ready).count()==8&&c.states.stream().filter(state->!state.ready).count()==1);
                for(CdpNetworkBindingProbe.Context source:c.owned)c.worker.cancelNetwork(source.adapter.marker().lease());c.cancelled();
            }finally{release.countDown();}
        }
        try(Case c=new Case()){
            CountDownLatch release=new CountDownLatch(1),entered=new CountDownLatch(1);MdoBrowserCdpMarker doc=c.document();
            c.worker.submit(doc,1500,false,(binding,deadline)->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new byte[]{1};},result->c.documentResults.add(result));
            try{await(()->entered.getCount()==0);CdpNetworkBindingProbe.Context source=c.source();c.start(source,500);c.time.addAndGet(1000);release.countDown();
                await(()->c.ui.tasks.size()>=2);c.ui.drain();check(c.states.size()==1&&c.states.get(0).code.equals("cdp_network_start_timeout")&&!c.states.get(0).enabledMayHaveOccurred);
                check(source.view.evaluations==0&&source.peer.wires.isEmpty()&&c.worker.networkPending()==0);
            }finally{release.countDown();}
        }
    }
    private static void failuresAndClose()throws Exception{
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);
            source.wire().queue(new JSONObject().put("method","Page.frameAttached").put("params",new JSONObject().put("frameId","unproved-child").put("parentFrameId","unproved-parent")));
            c.deliver(1);check(c.states.size()==2&&!c.states.get(1).ready&&c.states.get(1).code.equals("download_frame_parent_unproven")&&c.states.get(1).cleanupConfirmed);
            check(source.wire().closed.get()&&c.worker.networkPending()==0);
        }
        for(int mode=0;mode<3;mode++)try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();source.rejectPage=mode==0;source.brokenTree=mode==1;source.treeRaces=mode==2?8:0;c.start(source,1500);c.deliver(1);
            check(!c.states.get(0).ready&&!c.states.get(0).enabledMayHaveOccurred&&c.states.get(0).cleanupConfirmed&&c.worker.networkPending()==0);
            check(c.states.get(0).code.equals(mode==0?"cdp_method_unsupported":mode==1?"download_frame_root_changed":"download_frame_bootstrap_changed"));
            check(source.methods.stream().noneMatch("Fetch.enable"::equals));
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();source.rejectEnable=true;c.start(source,1500);c.deliver(1);
            check(!c.states.get(0).ready&&c.states.get(0).code.equals("cdp_method_unsupported")&&!c.states.get(0).enabledMayHaveOccurred&&c.worker.networkPending()==0);
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();source.view.defer=true;c.start(source,1500);await(()->source.view.evaluations==1);
            c.worker.cancelNetwork(source.adapter.marker().lease());c.deliver(1);check(c.states.size()==1&&!c.states.get(0).ready&&!c.states.get(0).enabledMayHaveOccurred);
            source.view.finish("null");Handler.drain();check(c.worker.networkPending()==0&&source.peer.wires.isEmpty());
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);
            source.wire().queue(new JSONObject().put("method","Inspector.targetCrashed").put("params",new JSONObject()));
            c.deliver(1);check(c.states.size()==2&&!c.states.get(1).ready&&c.states.get(1).cleanupConfirmed&&c.worker.networkPending()==0);
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();c.start(source,1500);await(()->!c.ui.tasks.isEmpty());c.worker.close();await(c.worker::terminated);c.ui.drain();
            check(c.states.isEmpty()&&c.worker.networkPending()==0);
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();source.rejectEnable=true;c.start(source,1500);await(()->!c.ui.tasks.isEmpty());
            c.worker.close();await(c.worker::terminated);c.ui.drain();check(c.states.isEmpty()&&c.worker.networkPending()==0);
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();c.ui.reject=true;c.start(source,1500);await(c.worker::terminated);
            check(c.states.isEmpty()&&c.worker.networkPending()==0&&source.wire().closed.get());
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();c.worker.startNetwork(source.adapter.marker(),1500,(connection,m,s,p)->{},result->{throw new IllegalStateException("private-ui-callback");});
            await(()->!c.ui.tasks.isEmpty());c.ui.drain();c.cancelled();check(source.wire().closed.get());
        }
        try(Case c=new Case()){
            CdpNetworkBindingProbe.Context source=c.source();c.uncertainCleanup=true;c.ready(source);
            CdpBindingProbe.Factory.Extra original=source.peer.extra;source.peer.extra=new CdpBindingProbe.Factory.Extra(){
                public boolean send(JSONObject command,CdpBindingProbe.Wire wire)throws Exception{return original.send(command,wire);}
                public void closed(CdpBindingProbe.Wire wire){throw new IllegalStateException("private-native-close-failure");}
            };
            c.worker.cancelNetwork(source.adapter.marker().lease());c.deliver(1);
            check(!c.states.get(1).cleanupConfirmed&&c.worker.networkPending()==1);
        }
    }
    public static void main(String[] ignored)throws Exception{
        Looper.getMainLooper();System.loadLibrary("xs");activationAndIdle();cooperativeDocumentWait();queueAndCapacity();failuresAndClose();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"shared_protocol_worker\":true,\"cooperative_page_wait\":true,\"native_ui_calls_doubled\":true,\"provider_qualified\":false,\"product_downloads_activated\":false}");
    }
}
