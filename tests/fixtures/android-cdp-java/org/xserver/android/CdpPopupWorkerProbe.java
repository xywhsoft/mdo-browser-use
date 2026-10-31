package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Real common executor/network/marker/dispatcher with controlled UI/renderer.
 * No extra product executor or installed-provider/popup adoption qualification. */
public final class CdpPopupWorkerProbe {
    private static int checks;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Popup worker check "+(checks+1));checks++;}
    private static void failure(String code,Work work)throws Exception{try{work.run();throw new AssertionError("Expected "+code);}catch(IOException error){check(code.equals(error.getMessage())&&error.getCause()==null);}}
    private static void waitFor(BooleanSupplier condition,CdpNetworkWorkerProbe.Case c,boolean deliver)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean()){
            if(System.nanoTime()>=end)throw new AssertionError("Popup worker wait expired");Handler.drain();if(deliver)c.ui.drain();Thread.sleep(1);
        }if(deliver)c.ui.drain();checks++;
    }
    private static JSONObject attached(String child,String opener)throws Exception{return new JSONObject().put("method","Target.attachedToTarget").put("params",new JSONObject().put("sessionId",child)
        .put("targetInfo",new JSONObject().put("targetId","target-"+child).put("type","page").put("openerId",opener)).put("waitingForDebugger",true));}
    private static JSONObject ack(int id,String child)throws Exception{JSONObject reply=new JSONObject().put("id",id).put("result",new JSONObject());if(child!=null)reply.put("sessionId",child);return reply;}
    private static final class Backend implements MdoBrowserCdpPopupWorker.Factory {
        final AtomicInteger creates=new AtomicInteger(),opens=new AtomicInteger(),closes=new AtomicInteger(),enables=new AtomicInteger(),disables=new AtomicInteger(),resumes=new AtomicInteger(),detaches=new AtomicInteger(),receives=new AtomicInteger();
        final Set<Thread> threads=ConcurrentHashMap.newKeySet();volatile Wire wire;volatile Runnable resumed;volatile CountDownLatch armEntered,armRelease;
        public MdoBrowserCdpPopupBroker create(BooleanSupplier current,LongSupplier clock){creates.incrementAndGet();threads.add(Thread.currentThread());Wire next=new Wire();wire=next;
            return new MdoBrowserCdpPopupBroker(current,clock,(timeout,alive,events)->{opens.incrementAndGet();return MdoBrowserCdpSession.ownedBrowser(next,alive,events,clock::getAsLong);});}
        private final class Wire implements MdoBrowserCdpSession.Wire {
            final ConcurrentLinkedQueue<byte[]> incoming=new ConcurrentLinkedQueue<>();
            void add(JSONObject packet){incoming.add(packet.toString().getBytes(StandardCharsets.UTF_8));}
            public void send(byte[] bytes,int timeout)throws IOException{
                threads.add(Thread.currentThread());try{JSONObject command=new JSONObject(new String(bytes,StandardCharsets.UTF_8));String method=command.getString("method");int id=command.getInt("id");
                    if(method.equals("Target.setAutoAttach")){
                        if(command.getJSONObject("params").getBoolean("autoAttach")){enables.incrementAndGet();if(armEntered!=null){armEntered.countDown();if(!armRelease.await(3,TimeUnit.SECONDS))throw new IOException("fixture_arm_wait_expired");}}
                        else disables.incrementAndGet();add(ack(id,null));
                    }else if(method.equals("Runtime.runIfWaitingForDebugger")){resumes.incrementAndGet();add(ack(id,command.getString("sessionId")));Runnable callback=resumed;if(callback!=null)callback.run();}
                    else if(method.equals("Target.detachFromTarget")){detaches.incrementAndGet();add(ack(id,null));}
                    else throw new AssertionError("Unapproved popup worker command");
                }catch(IOException error){throw error;}catch(RuntimeException|Error error){throw error;}catch(Exception error){throw new IOException("fixture_command_failed");}
            }
            public byte[] receive(int timeout){threads.add(Thread.currentThread());receives.incrementAndGet();byte[] value=incoming.poll();if(value==null)try{Thread.sleep(1);}catch(InterruptedException error){Thread.currentThread().interrupt();}return value;}
            public void close(){closes.incrementAndGet();}
        }
    }
    private static class Observer implements MdoBrowserCdpPopupWorker.Events {
        final CdpNetworkWorkerProbe.Case host;final List<MdoBrowserCdpPopupWorker.Result> states=new ArrayList<>();final List<MdoBrowserCdpPopupBroker.Candidate> candidates=new ArrayList<>();
        Observer(CdpNetworkWorkerProbe.Case host){this.host=host;}
        public void state(MdoBrowserCdpPopupWorker.Result result){check(host.ui.onThread());states.add(result);}
        public void candidate(MdoBrowserCdpPopupBroker.Candidate candidate){check(host.ui.onThread());candidates.add(candidate);}
    }
    private static MdoBrowserCdpPopupWorker.Request prepare(CdpNetworkWorkerProbe.Case c,CdpNetworkBindingProbe.Context source,Observer observer)throws Exception{
        return c.worker.preparePopups(source.adapter.marker().lease(),1500,observer);
    }
    private static void ready(CdpNetworkWorkerProbe.Case c,MdoBrowserCdpPopupWorker.Request request)throws Exception{waitFor(()->request.result()!=null&&request.result().ready,c,true);check(!request.result().terminal&&!request.result().sourceReleased);}
    private static void terminal(CdpNetworkWorkerProbe.Case c,MdoBrowserCdpPopupWorker.Request request)throws Exception{waitFor(()->request.result()!=null&&request.result().terminal,c,true);}
    private static void suite()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context one=c.source(),two=c.source();c.networkTargets.put(two.adapter.marker(),"other-root");c.ready(one);c.ready(two);
            Observer a=new Observer(c),b=new Observer(c);MdoBrowserCdpPopupWorker.Request first=prepare(c,one,a);ready(c,first);
            failure("cdp_popup_prepare_busy",()->prepare(c,one,new Observer(c)));
            MdoBrowserCdpPopupWorker.Request second=prepare(c,two,b);ready(c,second);check(backend.creates.get()==1&&backend.opens.get()==1&&backend.enables.get()==1);
            backend.wire.add(attached("free-1","foreign-root"));waitFor(()->backend.detaches.get()==1,c,true);check(a.candidates.isEmpty()&&b.candidates.isEmpty());
            first.close();terminal(c,first);check(first.result().sourceReleased&&!first.result().processCleanupConfirmed&&first.result().processMayHaveBeenEnabled);
            second.close();terminal(c,second);check(second.result().sourceReleased&&second.result().processCleanupConfirmed&&backend.disables.get()==1&&backend.closes.get()==1);
            waitFor(()->c.worker.popupPending()==0,c,true);check(c.worker.networkPending()==2&&!c.worker.terminated());
            Observer suppressed=new Observer(c);MdoBrowserCdpPopupWorker.Request third=prepare(c,one,suppressed);
            waitFor(()->third.result()!=null&&third.result().ready,c,false);third.close();waitFor(()->third.result().terminal,c,false);c.ui.drain();
            check(suppressed.states.size()==1&&suppressed.states.get(0).terminal&&c.worker.popupPending()==0);
            check(backend.creates.get()==2&&backend.opens.get()==2&&backend.disables.get()==2);
            failure("cdp_popup_prepare_arguments_invalid",()->c.worker.preparePopups(c.document().lease(),1000,new Observer(c)));
            check(backend.threads.size()==1&&c.threads.containsAll(backend.threads));
        }
    }
    private static void cooperative()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);MdoBrowserCdpPopupWorker.Request request=prepare(c,source,new Observer(c));ready(c,request);
            MdoBrowserCdpMarker document=c.document();final CdpBindingProbe.Wire[] blocked={null};final int[] id={0};
            backend.resumed=()->{try{blocked[0].queue(ack(id[0],null));}catch(Exception error){throw new AssertionError(error);}};
            c.documents.get(document).extra=(command,wire)->{
                if(command.getString("method").equals("Runtime.evaluate")&&command.getJSONObject("params").optString("expression").equals("popup-fixture-wait")){
                    blocked[0]=wire;id[0]=command.getInt("id");backend.wire.add(attached("cooperative-free","foreign-root"));return true;
                }return false;
            };
            c.worker.submit(document,1500,true,(binding,deadline)->{try{binding.call("Runtime.evaluate",new JSONObject().put("expression","popup-fixture-wait"),null,1000,true);}catch(org.json.JSONException error){throw new IOException("fixture_arguments_failed");}return new byte[]{1};},c.documentResults::add);
            waitFor(()->!c.documentResults.isEmpty()&&backend.detaches.get()==1,c,true);
            check(c.documentResults.get(0).code==null&&backend.resumes.get()==1&&backend.threads.size()==1&&c.threads.containsAll(backend.threads));
            request.close();terminal(c,request);check(request.result().processCleanupConfirmed);
        }
    }
    private static void candidates()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);ready(c,request);
            backend.wire.add(attached("owned-child","owned"));waitFor(()->observer.candidates.size()==1,c,true);
            check(!observer.candidates.get(0).nativeChildProven()&&observer.candidates.get(0).current()&&backend.resumes.get()==0);
            Thread.sleep(25);c.ui.drain();check(observer.candidates.size()==1);
            backend.wire.add(new JSONObject().put("method","Target.detachedFromTarget").put("params",new JSONObject().put("sessionId","owned-child")));
            waitFor(()->!observer.candidates.get(0).current(),c,true);request.close();terminal(c,request);check(request.result().processCleanupConfirmed);
        }
    }
    private static void queued(boolean expire)throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            c.worker.submit(c.document(),10000,false,(binding,deadline)->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("fixture_block_expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("fixture_interrupted");}return new byte[]{1};},c.documentResults::add);
            try{waitFor(()->entered.getCount()==0,c,true);Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);
                if(expire)c.time.addAndGet(2000);else request.close();release.countDown();terminal(c,request);
                check(request.result().code.equals(expire?"cdp_popup_prepare_timeout":"cdp_popup_prepare_cancelled")&&request.result().sourceReleased&&request.result().processCleanupConfirmed);
                check(backend.creates.get()==0&&observer.states.size()==1&&observer.states.get(0).terminal);
            }finally{release.countDown();}
        }
    }
    private static void armingCancellation()throws Exception{
        Backend backend=new Backend();backend.armEntered=new CountDownLatch(1);backend.armRelease=new CountDownLatch(1);
        try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);
            try{waitFor(()->backend.armEntered.getCount()==0,c,true);long start=System.nanoTime();request.close();check(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(100));backend.armRelease.countDown();terminal(c,request);
                check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&observer.states.stream().noneMatch(s->s.ready)&&backend.disables.get()==1);
            }finally{backend.armRelease.countDown();}
        }
    }
    private static void poison(String mode)throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);ready(c,request);
            if(mode.equals("native-cancel")){
                backend.wire.add(attached("held-child","owned"));waitFor(()->observer.candidates.size()==1,c,true);request.close();terminal(c,request);
                check(request.result().code.equals("cdp_popup_native_cancel_required")&&!request.result().sourceReleased&&!request.result().processCleanupConfirmed&&!observer.candidates.get(0).current());
                MdoBrowserCdpPopupWorker.Request refused=prepare(c,source,new Observer(c));terminal(c,refused);check(refused.result().code.equals("cdp_popup_cleanup_unconfirmed")&&refused.result().sourceReleased&&backend.creates.get()==1);
                check(!c.worker.terminated());
            }else{
                c.worker.close();terminal(c,request);check(request.result().code.equals("cdp_worker_closed")&&!request.result().processCleanupConfirmed);
                waitFor(c.worker::terminated,c,true);
            }
        }
    }
    private static void callbackFailure()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);Observer observer=new Observer(c){@Override public void state(MdoBrowserCdpPopupWorker.Result result){super.state(result);if(result.ready)throw new IllegalStateException("controlled-callback-failure");}};
            MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);terminal(c,request);check(request.result().processCleanupConfirmed&&!c.worker.terminated());
        }
    }
    private static final class NativeSource implements AutoCloseable {
        final CdpNetworkWorkerProbe.Case host;final MdoBrowserPopups<WebView> core;final MdoBrowserCdpPopupNative source;
        final MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(8);final WebView view;int sends;Runnable consumed;boolean fail;
        NativeSource(CdpNetworkWorkerProbe.Case host,CdpNetworkBindingProbe.Context network)throws Exception{
            this.host=host;view=network.view;
            core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
                public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}
            },budget,()->true,()->1L,blocked->check(host.ui.onThread()));
            source=new MdoBrowserCdpPopupNative(network.adapter,view,core);
        }
        void open(){check(core.open(new MdoBrowserPopups.Transport<WebView>(){
            private final Object token=new Object();public Object token(){return token;}
            public void send(WebView child,Runnable callback){check(host.ui.onThread()&&child==null);sends++;consumed=callback;if(fail)throw new IllegalStateException("controlled-native-send-failure");}
        },false));}
        void ack(){check(host.ui.onThread());consumed.run();}
        boolean blocked(){return !core.open(new MdoBrowserPopups.Transport<WebView>(){
            public Object token(){return this;}public void send(WebView child,Runnable callback){throw new AssertionError("Blocked callback dispatched");}
        },false);}
        public void close(){core.close();core.viewDestroyed(view);}
    }
    private static JSONObject detached(String id)throws Exception{return new JSONObject().put("method","Target.detachedFromTarget").put("params",new JSONObject().put("sessionId",id));}
    private static void nativeJoin(boolean detachFirst)throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource nativeSource=new NativeSource(c,network)){
                failure("cdp_popup_native_source_invalid",()->new MdoBrowserCdpPopupNative(network.adapter,new WebView(),nativeSource.core));
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(nativeSource.source,1500,observer);ready(c,request);
                nativeSource.open();backend.wire.add(attached("native-original","owned"));waitFor(()->observer.candidates.size()==1,c,true);
                request.close();waitFor(()->nativeSource.sends==1,c,true);check(nativeSource.blocked()&&!request.result().terminal);
                if(detachFirst){backend.wire.add(detached("native-original"));waitFor(()->!observer.candidates.get(0).current(),c,true);}
                else nativeSource.ack();
                int before=backend.receives.get();waitFor(()->backend.receives.get()>=before+3,c,true);
                check(!request.result().terminal&&backend.disables.get()==0&&nativeSource.blocked());
                if(detachFirst)nativeSource.ack();else backend.wire.add(detached("native-original"));
                terminal(c,request);check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&nativeSource.budget.used()==0&&backend.resumes.get()==0&&backend.detaches.get()==0);
                // A completed original source can be explicitly prepared again.
                Observer nextObserver=new Observer(c);MdoBrowserCdpPopupWorker.Request next=c.worker.prepareNativePopups(nativeSource.source,1500,nextObserver);ready(c,next);next.close();terminal(c,next);
                check(next.result().sourceReleased&&next.result().processCleanupConfirmed&&nativeSource.sends==1&&backend.disables.get()==2&&backend.threads.size()==1);
                check(nativeSource.core.protocolSource(network.view));
            }
        }
    }
    private static void nativeFailure(String mode)throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource source=new NativeSource(c,network)){
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request);
                source.open();backend.wire.add(attached("native-held","owned"));waitFor(()->observer.candidates.size()==1,c,true);
                source.fail=mode.equals("native-send-failure");request.close();waitFor(()->source.sends==1,c,true);
                if(!source.fail){if(mode.equals("native-ack-timeout"))backend.wire.add(detached("native-held"));else source.ack();
                    int before=backend.receives.get();waitFor(()->backend.receives.get()>=before+3,c,true);check(!request.result().terminal);c.time.addAndGet(5001);}
                terminal(c,request);check(!request.result().sourceReleased&&!request.result().processCleanupConfirmed&&backend.disables.get()==0&&backend.resumes.get()==0&&backend.detaches.get()==0&&source.blocked());
                check(request.result().code.equals(source.fail?"cdp_popup_native_rejection_unconfirmed":"cdp_popup_native_cancel_timeout"));
                if(mode.equals("native-ack-timeout"))source.ack();check(source.blocked());
                MdoBrowserCdpPopupWorker.Request refused=prepare(c,network,new Observer(c));terminal(c,refused);check(refused.result().code.equals("cdp_popup_cleanup_unconfirmed")&&backend.creates.get()==1&&!c.worker.terminated());
            }
        }
    }
    private static void nativeTerminalBeforeUiClose()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource source=new NativeSource(c,network)){
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                c.worker.submit(c.document(),10000,false,(binding,deadline)->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("fixture_wait_expired");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("fixture_interrupted");}return new byte[]{1};},result->{});
                try{
                    waitFor(()->entered.getCount()==0,c,true);Observer observer=new Observer(c);
                    MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);
                    c.time.addAndGet(2000);release.countDown();waitFor(()->request.result()!=null&&request.result().terminal,c,false);
                    source.open();c.worker.close();c.ui.drain();waitFor(c.worker::terminated,c,true);
                    check(request.result().sourceReleased&&source.sends==0&&backend.creates.get()==0&&observer.states.size()==1);
                }finally{release.countDown();}
            }
        }
    }
    public static void main(String[] args)throws Exception{
        Looper.getMainLooper();System.loadLibrary("xs");String mode=args.length==0?"suite":args[0];
        if(mode.equals("suite")){suite();cooperative();candidates();queued(false);queued(true);armingCancellation();callbackFailure();nativeJoin(false);nativeJoin(true);nativeTerminalBeforeUiClose();}
        else if(mode.startsWith("native-")&&!mode.equals("native-cancel"))nativeFailure(mode);
        else poison(mode);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"mode\":\""+mode+"\",\"one_original_executor\":true,\"ui_delivery\":true,\"cooperative_popup_pump\":true,\"native_popup_adoption_qualified\":false,\"provider_qualified\":false}");
    }
}
