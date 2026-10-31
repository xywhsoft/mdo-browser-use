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
        final AtomicInteger creates=new AtomicInteger(),opens=new AtomicInteger(),closes=new AtomicInteger(),enables=new AtomicInteger(),disables=new AtomicInteger(),resumes=new AtomicInteger(),detaches=new AtomicInteger(),receives=new AtomicInteger(),guards=new AtomicInteger();
        final Set<Thread> threads=ConcurrentHashMap.newKeySet();volatile Wire wire;volatile Runnable resumed;volatile CountDownLatch armEntered,armRelease;
        volatile String guardMode="inline";volatile Runnable guardTimeout;volatile CountDownLatch guardEntered,guardRelease;
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
                    }else if(method.equals("Fetch.enable")){
                        JSONObject params=command.getJSONObject("params");org.json.JSONArray patterns=params.getJSONArray("patterns");
                        if(command.length()!=4||params.length()!=2||params.getBoolean("handleAuthRequests")||patterns.length()!=2)throw new AssertionError("Unapproved request guard arguments");
                        for(int i=0;i<2;i++){JSONObject pattern=patterns.getJSONObject(i);if(pattern.length()!=2||!pattern.getString("urlPattern").equals("*")||!pattern.getString("requestStage").equals(i==0?"Request":"Response"))throw new AssertionError("Unapproved request guard pattern");}
                        guards.incrementAndGet();String child=command.getString("sessionId");
                        if(guardEntered!=null){guardEntered.countDown();if(!guardRelease.await(3,TimeUnit.SECONDS))throw new IOException("fixture_guard_wait_expired");}
                        if(guardMode.equals("timeout")){guardTimeout.run();return;}
                        if(guardMode.equals("send-failure"))throw new IOException("controlled-partial-guard-send");
                        if(guardMode.equals("unsupported")){add(new JSONObject().put("id",id).put("sessionId",child).put("error",new JSONObject().put("code",-32601).put("message","synthetic-unread-provider-message")));return;}
                        int packets=guardMode.equals("limit")?33:guardMode.equals("duplicate")?2:1;
                        for(int i=0;i<packets;i++){
                            JSONObject paused=new JSONObject().put("method","Fetch.requestPaused").put("sessionId",child).put("params",new JSONObject().put("requestId",guardMode.equals("duplicate")?"initial-original":"initial-"+i)
                                .put("request",new JSONObject().put("url","https://private.invalid/unread").put("headers",new JSONObject().put("Authorization","synthetic-unread"))));
                            if(guardMode.equals("response"))paused.getJSONObject("params").put("responseStatusCode",200);add(paused);
                        }
                        JSONObject reply=ack(id,guardMode.equals("reply")?null:child);if(guardMode.equals("nonempty"))reply.getJSONObject("result").put("unexpected",true);add(reply);
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
        final List<MdoBrowserCdpPopupBroker.Candidate> protectedTargets=new ArrayList<>();
        Observer(CdpNetworkWorkerProbe.Case host){this.host=host;}
        public void state(MdoBrowserCdpPopupWorker.Result result){check(host.ui.onThread());states.add(result);}
        public void candidate(MdoBrowserCdpPopupBroker.Candidate candidate){check(host.ui.onThread());candidates.add(candidate);}
        public void protection(MdoBrowserCdpPopupBroker.Candidate candidate){check(host.ui.onThread());protectedTargets.add(candidate);}
    }
    private static MdoBrowserCdpPopupWorker.Request prepare(CdpNetworkWorkerProbe.Case c,CdpNetworkBindingProbe.Context source,Observer observer)throws Exception{
        return c.worker.preparePopups(source.adapter.marker().lease(),1500,observer);
    }
    private static void ready(CdpNetworkWorkerProbe.Case c,MdoBrowserCdpPopupWorker.Request request,Observer observer)throws Exception{waitFor(()->request.result()!=null&&request.result().ready&&observer.states.contains(request.result()),c,true);check(!request.result().terminal&&!request.result().sourceReleased);}
    private static void terminal(CdpNetworkWorkerProbe.Case c,MdoBrowserCdpPopupWorker.Request request)throws Exception{waitFor(()->request.result()!=null&&request.result().terminal,c,true);}
    private static void terminal(CdpNetworkWorkerProbe.Case c,MdoBrowserCdpPopupWorker.Request request,Observer observer)throws Exception{
        terminal(c,request);waitFor(()->observer.states.contains(request.result()),c,true);
    }
    private static void suite()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context one=c.source(),two=c.source();c.networkTargets.put(two.adapter.marker(),"other-root");c.ready(one);c.ready(two);
            Observer a=new Observer(c),b=new Observer(c);MdoBrowserCdpPopupWorker.Request first=prepare(c,one,a);ready(c,first,a);
            failure("cdp_popup_prepare_busy",()->prepare(c,one,new Observer(c)));
            MdoBrowserCdpPopupWorker.Request second=prepare(c,two,b);ready(c,second,b);check(backend.creates.get()==1&&backend.opens.get()==1&&backend.enables.get()==1);
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
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);ready(c,request,observer);
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
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);ready(c,request,observer);
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
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);ready(c,request,observer);
            if(mode.equals("native-cancel")){
                backend.wire.add(attached("held-child","owned"));waitFor(()->observer.candidates.size()==1,c,true);request.close();terminal(c,request,observer);
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
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(nativeSource.source,1500,observer);ready(c,request,observer);
                nativeSource.open();backend.wire.add(attached("native-original","owned"));waitFor(()->observer.candidates.size()==1,c,true);
                request.close();waitFor(()->nativeSource.sends==1,c,true);check(nativeSource.blocked()&&!request.result().terminal);
                if(detachFirst){backend.wire.add(detached("native-original"));waitFor(()->!observer.candidates.get(0).current(),c,true);}
                else nativeSource.ack();
                int before=backend.receives.get();waitFor(()->backend.receives.get()>=before+3,c,true);
                check(!request.result().terminal&&backend.disables.get()==0&&nativeSource.blocked());
                if(detachFirst)nativeSource.ack();else backend.wire.add(detached("native-original"));
                terminal(c,request,observer);check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&nativeSource.budget.used()==0&&backend.resumes.get()==0&&backend.detaches.get()==0);
                // A completed original source can be explicitly prepared again.
                Observer nextObserver=new Observer(c);MdoBrowserCdpPopupWorker.Request next=c.worker.prepareNativePopups(nativeSource.source,1500,nextObserver);ready(c,next,nextObserver);next.close();terminal(c,next);
                check(next.result().sourceReleased&&next.result().processCleanupConfirmed&&nativeSource.sends==1&&backend.disables.get()==2&&backend.threads.size()==1);
                check(nativeSource.core.protocolSource(network.view));
            }
        }
    }
    private static void nativeFailure(String mode)throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource source=new NativeSource(c,network)){
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);
                source.open();backend.wire.add(attached("native-held","owned"));waitFor(()->observer.candidates.size()==1,c,true);
                source.fail=mode.equals("native-send-failure");request.close();waitFor(()->source.sends==1,c,true);
                if(!source.fail){if(mode.equals("native-ack-timeout"))backend.wire.add(detached("native-held"));else source.ack();
                    int before=backend.receives.get();waitFor(()->backend.receives.get()>=before+3,c,true);check(!request.result().terminal);c.time.addAndGet(5001);}
                terminal(c,request,observer);check(!request.result().sourceReleased&&!request.result().processCleanupConfirmed&&backend.disables.get()==0&&backend.resumes.get()==0&&backend.detaches.get()==0&&source.blocked());
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
                    check(source.blocked());c.worker.close();c.ui.drain();waitFor(c.worker::terminated,c,true);
                    check(request.result().sourceReleased&&source.sends==0&&backend.creates.get()==0&&observer.states.size()==1);
                }finally{release.countDown();}
            }
        }
    }
    private static void nativeOwnerChanged()throws Exception{
        for(boolean beforeReady:new boolean[]{false,true}){
            Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
                CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
                try(NativeSource source=new NativeSource(c,network)){
                    Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);
                    if(beforeReady)waitFor(()->request.result()!=null&&request.result().ready,c,false);
                    else{
                        ready(c,request,observer);backend.wire.add(attached("stale-ui-candidate","owned"));
                        waitFor(()->!c.ui.tasks.isEmpty(),c,false);
                    }
                    network.popupOwned=false;source.open();String manual=source.core.snapshot().id;
                    check(!source.source.live()&&network.adapter.physicalLease(network.view).current());
                    c.ui.drain();check(observer.candidates.isEmpty()&&observer.protectedTargets.isEmpty());
                    if(!beforeReady)backend.wire.add(detached("stale-ui-candidate"));
                    terminal(c,request,observer);
                    check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&source.sends==0&&source.budget.used()==1);
                    check(manual.equals(source.core.snapshot().id)&&observer.states.size()==(beforeReady?1:2));
                    check(source.core.dismiss(manual));source.ack();check(source.sends==1&&source.budget.used()==0);
                    network.popupOwned=true;Observer fresh=new Observer(c);MdoBrowserCdpPopupWorker.Request next=c.worker.prepareNativePopups(source.source,1500,fresh);ready(c,next,fresh);
                    next.close();terminal(c,next,fresh);check(source.sends==1&&backend.enables.get()==2&&backend.disables.get()==2&&backend.threads.size()==1);
                }
            }
        }
    }
    private static void protectionSuite()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource source=new NativeSource(c,network)){
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);source.open();
                backend.wire.add(attached("guard-one","owned"));backend.wire.add(attached("guard-two","owned"));waitFor(()->observer.candidates.size()==2,c,true);
                for(MdoBrowserCdpPopupBroker.Candidate candidate:observer.candidates)check(candidate.requestProtection()&&!candidate.requestProtection());
                waitFor(()->observer.protectedTargets.size()==2,c,true);
                for(MdoBrowserCdpPopupBroker.Candidate candidate:observer.candidates)check(candidate.protection().ok&&candidate.protection().effectMayHaveOccurred&&candidate.retainedRequestCount()==1&&!candidate.nativeChildProven());
                check(backend.guards.get()==2&&backend.resumes.get()==0&&backend.detaches.get()==0&&backend.threads.size()==1);
                request.close();waitFor(()->source.sends==1,c,true);source.ack();backend.wire.add(detached("guard-one"));waitFor(()->!observer.candidates.get(0).current(),c,true);check(!request.result().terminal);
                backend.wire.add(detached("guard-two"));terminal(c,request);check(request.result().sourceReleased&&request.result().processCleanupConfirmed);
                for(MdoBrowserCdpPopupBroker.Candidate candidate:observer.candidates){check(candidate.retainedRequestCount()==0);failure("cdp_popup_candidate_retired",candidate::requestProtection);}
            }
        }
        Backend denied=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(denied)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=prepare(c,network,observer);ready(c,request,observer);
            denied.wire.add(attached("no-native","owned"));waitFor(()->observer.candidates.size()==1,c,true);check(observer.candidates.get(0).requestProtection());waitFor(()->observer.protectedTargets.size()==1,c,true);
            MdoBrowserCdpSession.ControlResult refused=observer.candidates.get(0).protection();check(!refused.ok&&!refused.effectMayHaveOccurred&&refused.code.equals("cdp_popup_native_source_required")&&denied.guards.get()==0);
            denied.wire.add(detached("no-native"));waitFor(()->!observer.candidates.get(0).current(),c,true);request.close();terminal(c,request);check(request.result().processCleanupConfirmed);
        }
        Backend unsupported=new Backend();unsupported.guardMode="unsupported";
        try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(unsupported)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource source=new NativeSource(c,network)){
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);source.open();
                unsupported.wire.add(attached("unsupported","owned"));waitFor(()->observer.candidates.size()==1,c,true);check(observer.candidates.get(0).requestProtection());waitFor(()->source.sends==1,c,true);
                MdoBrowserCdpSession.ControlResult result=observer.candidates.get(0).protection();check(!result.ok&&!result.effectMayHaveOccurred&&result.protocolCode==-32601);
                source.ack();unsupported.wire.add(detached("unsupported"));terminal(c,request);check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&request.result().code.equals("cdp_popup_interception_unsupported"));
                check(unsupported.guards.get()==1&&unsupported.resumes.get()==0&&unsupported.detaches.get()==0&&!c.worker.terminated());
            }
        }
        protectionCancel();
    }
    private static void protectionCancel()throws Exception{
        Backend backend=new Backend();backend.guardEntered=new CountDownLatch(1);backend.guardRelease=new CountDownLatch(1);
        try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource source=new NativeSource(c,network)){
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);source.open();backend.wire.add(attached("cancel-guard","owned"));waitFor(()->observer.candidates.size()==1,c,true);observer.candidates.get(0).requestProtection();
                try{waitFor(()->backend.guardEntered.getCount()==0,c,true);long before=System.nanoTime();request.close();check(System.nanoTime()-before<TimeUnit.MILLISECONDS.toNanos(100));backend.guardRelease.countDown();waitFor(()->source.sends==1,c,true);
                    source.ack();backend.wire.add(detached("cancel-guard"));terminal(c,request);check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&observer.protectedTargets.isEmpty()&&backend.guards.get()==1&&backend.resumes.get()==0);
                }finally{backend.guardRelease.countDown();}
            }
        }
    }
    private static void protectionFailure(String mode)throws Exception{
        Backend backend=new Backend();backend.guardMode=mode.substring("protection-".length());
        try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            backend.guardTimeout=()->c.time.addAndGet(300);CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource source=new NativeSource(c,network)){
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);source.open();backend.wire.add(attached("failed-guard","owned"));waitFor(()->observer.candidates.size()==1,c,true);check(observer.candidates.get(0).requestProtection());terminal(c,request,observer);waitFor(()->source.sends==1,c,true);
                MdoBrowserCdpPopupBroker.Candidate candidate=observer.candidates.get(0);
                check(!candidate.protection().ok&&candidate.protection().effectMayHaveOccurred&&candidate.retainedRequestCount()==0&&!candidate.nativeChildProven());
                String expected;
                switch(backend.guardMode){
                    case "limit":expected="cdp_attach_request_limit";break;
                    case "response":expected="cdp_attach_response_unproven";break;
                    case "duplicate":expected="cdp_attach_request_duplicate";break;
                    case "timeout":expected="cdp_command_timeout";break;
                    case "send-failure":expected="cdp_transport_failed";break;
                    case "reply":expected="cdp_reply_mismatch";break;
                    case "nonempty":expected="cdp_attach_protection_reply_invalid";break;
                    default:throw new AssertionError("Unknown protection failure fixture");
                }
                check(!request.result().sourceReleased&&!request.result().processCleanupConfirmed&&request.result().code.equals(expected)&&candidate.protection().code.equals(expected)&&observer.protectedTargets.isEmpty());
                source.ack();check(source.blocked()&&backend.guards.get()==1&&backend.resumes.get()==0&&backend.detaches.get()==0&&!c.worker.terminated());
                MdoBrowserCdpPopupWorker.Request refused=prepare(c,network,new Observer(c));terminal(c,refused);check(refused.result().code.equals("cdp_popup_cleanup_unconfirmed")&&backend.creates.get()==1);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        Looper.getMainLooper();System.loadLibrary("xs");String mode=args.length==0?"suite":args[0];
        if(mode.equals("protection"))protectionSuite();
        else if(mode.startsWith("protection-"))protectionFailure(mode);
        else if(mode.equals("suite")){suite();cooperative();candidates();queued(false);queued(true);armingCancellation();callbackFailure();nativeJoin(false);nativeJoin(true);nativeTerminalBeforeUiClose();nativeOwnerChanged();}
        else if(mode.startsWith("native-")&&!mode.equals("native-cancel"))nativeFailure(mode);
        else poison(mode);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"mode\":\""+mode+"\",\"one_original_executor\":true,\"ui_delivery\":true,\"cooperative_popup_pump\":true,\"protective_interception_scope_tested\":"+mode.startsWith("protection")+",\"native_popup_adoption_qualified\":false,\"provider_qualified\":false}");
    }
}
