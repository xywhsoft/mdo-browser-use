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
        final Set<Thread> threads=ConcurrentHashMap.newKeySet();volatile Wire wire;volatile CountDownLatch armEntered,armRelease;
        final Map<String,CdpBindingProbe.Factory> nativeTargets=new ConcurrentHashMap<>();final AtomicInteger nativeOpens=new AtomicInteger(),barriers=new AtomicInteger();
        volatile String barrierMode="normal";volatile Runnable proofConfirmed;
        volatile String guardMode="inline";volatile Runnable guardTimeout;volatile CountDownLatch guardEntered,guardRelease;
        public MdoBrowserCdpPopupBroker create(BooleanSupplier current,LongSupplier clock){creates.incrementAndGet();threads.add(Thread.currentThread());Wire next=new Wire();wire=next;
            return new MdoBrowserCdpPopupBroker(current,clock,(timeout,alive,events)->{opens.incrementAndGet();return MdoBrowserCdpSession.ownedBrowser(next,alive,events,clock::getAsLong);},
                (target,timeout,alive,events)->{threads.add(Thread.currentThread());nativeOpens.incrementAndGet();CdpBindingProbe.Factory peer=nativeTargets.get(target);
                    if(peer==null)throw new IOException("cdp_fixture_unregistered_target");return peer.open(target.equals("target-proof-real")?"owned":"other",timeout,alive,events);});}
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
                    }else if(method.equals("Runtime.runIfWaitingForDebugger")){resumes.incrementAndGet();add(ack(id,command.getString("sessionId")));}
                    else if(method.equals("Target.getTargetInfo")){
                        JSONObject p=command.getJSONObject("params");if(command.length()!=3||p.length()!=1||!p.getString("targetId").equals("target-proof-real"))throw new AssertionError("Caller-selected target barrier");barriers.incrementAndGet();
                        if(barrierMode.equals("detach"))add(detached("proof-real"));
                        add(new JSONObject().put("id",id).put("result",new JSONObject().put("targetInfo",new JSONObject().put("targetId",barrierMode.equals("wrong")?"foreign":p.getString("targetId")).put("type","page"))));
                        Runnable callback=proofConfirmed;if(callback!=null)callback.run();
                    }
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
        final List<MdoBrowserCdpPopupNative.Child> provenChildren=new ArrayList<>();
        Observer(CdpNetworkWorkerProbe.Case host){this.host=host;}
        public void state(MdoBrowserCdpPopupWorker.Result result){check(host.ui.onThread());states.add(result);}
        public void candidate(MdoBrowserCdpPopupBroker.Candidate candidate){check(host.ui.onThread());candidates.add(candidate);}
        public void protection(MdoBrowserCdpPopupBroker.Candidate candidate){check(host.ui.onThread());protectedTargets.add(candidate);}
        public void nativeProof(MdoBrowserCdpPopupNative.Child child){check(host.ui.onThread());provenChildren.add(child);}
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
            backend.wire.add(attached("free-1","foreign-root"));waitFor(()->a.candidates.size()==1&&b.candidates.size()==1,c,true);
            check(backend.detaches.get()==0&&backend.resumes.get()==0&&a.candidates.get(0)==b.candidates.get(0));
            backend.wire.add(detached("free-1"));waitFor(()->!a.candidates.get(0).current(),c,true);
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
            CdpNetworkBindingProbe.Context source=c.source();c.ready(source);
            MdoBrowserCdpMarker document=c.document();final CdpBindingProbe.Wire[] blocked={null};final int[] id={0};
            Observer observer=new Observer(c){@Override public void candidate(MdoBrowserCdpPopupBroker.Candidate candidate){
                super.candidate(candidate);check(c.documentResults.isEmpty()&&backend.resumes.get()==0&&backend.detaches.get()==0);
                // The controlled native source closes its original child; the
                // Browser broker must not resume an unidentified target to make
                // a parent RPC return. This callback requires cooperative turns.
                try{backend.wire.add(detached("cooperative-held"));blocked[0].queue(ack(id[0],null));}catch(Exception error){throw new AssertionError(error);}
            }};
            MdoBrowserCdpPopupWorker.Request request=prepare(c,source,observer);ready(c,request,observer);
            c.documents.get(document).extra=(command,wire)->{
                if(command.getString("method").equals("Runtime.evaluate")&&command.getJSONObject("params").optString("expression").equals("popup-fixture-wait")){
                    blocked[0]=wire;id[0]=command.getInt("id");backend.wire.add(attached("cooperative-held","foreign-root"));return true;
                }return false;
            };
            c.worker.submit(document,1500,true,(binding,deadline)->{try{binding.call("Runtime.evaluate",new JSONObject().put("expression","popup-fixture-wait"),null,1000,true);}catch(org.json.JSONException error){throw new IOException("fixture_arguments_failed");}return new byte[]{1};},c.documentResults::add);
            waitFor(()->!c.documentResults.isEmpty()&&observer.candidates.size()==1&&!observer.candidates.get(0).current(),c,true);
            check(c.documentResults.get(0).code==null&&backend.resumes.get()==0&&backend.detaches.get()==0&&backend.threads.size()==1&&c.threads.containsAll(backend.threads));
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
        boolean allocationFail;Runnable allocationHook;final MdoBrowserLifecycle<WebView> views=PopupLifecycleFixture.registry(()->allocationFail,()->{if(allocationHook!=null)allocationHook.run();});int births;
        final MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(8);final WebView view;int sends;Runnable consumed;boolean fail;WebView adopting;
        NativeSource(CdpNetworkWorkerProbe.Case host,CdpNetworkBindingProbe.Context network)throws Exception{
            this.host=host;view=network.view;views.put("root",view);
            core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
                public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}
            },budget,()->true,()->1L,blocked->check(host.ui.onThread()));
            source=new MdoBrowserCdpPopupNative(network.adapter,view,core);
        }
        void open(){check(core.open(new MdoBrowserPopups.Transport<WebView>(){
            private final Object token=new Object();public Object token(){return token;}
            public void send(WebView child,Runnable callback){check(host.ui.onThread()&&child==adopting);sends++;consumed=callback;if(fail)throw new IllegalStateException("controlled-native-send-failure");}
        },false));}
        void dispose(String key,WebView exact){if(views.owns(key,exact))views.remove(key);exact.destroy();views.viewDestroyed(exact);}
        MdoBrowserCdpPopupNative.Created create(MdoBrowserCdpPopupWorker.Request request)throws Exception{return source.construct(request.allocateNative(source),views,"child"+(++births),this::dispose);}
        MdoBrowserLifecycle.Fresh<WebView> fresh(WebView child){return views.putPopup("child"+(++births),view,child);}
        void ack(){check(host.ui.onThread());consumed.run();}
        boolean blocked(){return !core.open(new MdoBrowserPopups.Transport<WebView>(){
            public Object token(){return this;}public void send(WebView child,Runnable callback){throw new AssertionError("Blocked callback dispatched");}
        },false);}
        public void close(){PopupLifecycleFixture.destroyAll(views);core.close();core.viewDestroyed(view);}
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
    private static CdpBindingProbe.Factory proofPeer(WebView view,String mode,CountDownLatch entered,CountDownLatch release){
        CdpBindingProbe.Factory peer=new CdpBindingProbe.Factory(view);peer.ignoreUnique=mode.equals("unique");peer.wrongProof=mode.equals("missing");
        peer.extra=(command,wire)->{
            String method=command.getString("method");
            if(entered!=null&&method.equals("Runtime.enable")){entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new IOException("cdp_fixture_native_wait_expired");}
            if(method.equals("Runtime.disable")){check(command.getJSONObject("params").length()==0&&wire.proofRead&&wire.cleanupAttempts==1);wire.queue(ack(command.getInt("id"),null));return true;}
            return false;
        };return peer;
    }
    private static void nativeProof(String mode)throws Exception{
        String variant=mode.equals("native-proof")?"success":mode.substring("native-proof-".length());Backend backend=new Backend();
        boolean ordinarySuccess=variant.equals("success")||variant.equals("no-opener")||variant.equals("foreign-opener");
        if(variant.equals("barrier"))backend.barrierMode="wrong";if(variant.equals("detach"))backend.barrierMode="detach";
        try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);
            try(NativeSource source=new NativeSource(c,network)){
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);
                source.open();backend.wire.add(attached("proof-decoy","owned"));JSONObject realTarget=attached("proof-real",variant.equals("foreign-opener")?"unregistered-root":"owned");
                if(variant.equals("no-opener"))realTarget.getJSONObject("params").getJSONObject("targetInfo").remove("openerId");
                backend.wire.add(realTarget);waitFor(()->observer.candidates.size()==2,c,true);
                if(variant.equals("no-guard")){
                    int before=WebView.constructions;failure("cdp_popup_interception_required",()->request.allocateNative(source.source));
                    check(WebView.constructions==before&&source.sends==0&&backend.nativeOpens.get()==0);
                    request.close();waitFor(()->source.sends==1,c,true);source.ack();backend.wire.add(detached("proof-decoy"));backend.wire.add(detached("proof-real"));terminal(c,request,observer);
                    check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&backend.resumes.get()==0);return;
                }
                if(!variant.equals("no-guard")){for(MdoBrowserCdpPopupBroker.Candidate candidate:observer.candidates)check(candidate.requestProtection());waitFor(()->observer.protectedTargets.size()==2,c,true);}
                MdoBrowserCdpPopupNative.Created created=source.create(request);WebView childView=created.view();source.adopting=childView;MdoBrowserPopups.Adoption adoption=source.source.adopt(created);source.ack();
                check(adoption.ok()&&adoption.nativeConsumed()&&source.sends==1);
                try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(childView,v->v==childView,new MdoBrowserCdpMarker.DocumentQuota());
                    MdoBrowserCdpPopupNative.Child child=source.source.child(adoption,created,adapter)){
                    CountDownLatch entered=variant.equals("owner")||variant.equals("cancel")?new CountDownLatch(1):null,release=entered==null?null:new CountDownLatch(1);
                    CdpBindingProbe.Factory real=proofPeer(childView,variant,entered,release),decoy=proofPeer(new WebView(),"success",null,null);
                    if(variant.equals("between"))decoy.extra=new CdpBindingProbe.Factory.Extra(){
                        public boolean send(JSONObject command,CdpBindingProbe.Wire wire){return false;}
                        public void closed(CdpBindingProbe.Wire wire){request.close();}
                    };
                    childView.badReply=variant.equals("marker");
                    backend.nativeTargets.put("target-proof-decoy",decoy);backend.nativeTargets.put("target-proof-real",real);
                    if(ordinarySuccess)rejectForeignNative(c,backend,request);
                    Runnable releaseDocument=()->{};
                    if(variant.equals("cooperative")){
                        MdoBrowserCdpMarker document=c.document();final CdpBindingProbe.Wire[] waiting={null};final int[] id={0};CountDownLatch started=new CountDownLatch(1);
                        java.util.concurrent.atomic.AtomicBoolean released=new java.util.concurrent.atomic.AtomicBoolean();
                        releaseDocument=()->{if(released.compareAndSet(false,true)&&waiting[0]!=null)try{waiting[0].queue(ack(id[0],null));}catch(Exception error){throw new AssertionError(error);}};
                        backend.proofConfirmed=releaseDocument;
                        c.documents.get(document).extra=(command,wire)->{
                            if(command.getString("method").equals("Runtime.evaluate")&&command.getJSONObject("params").optString("expression").equals("native-proof-fixture-wait")){
                                waiting[0]=wire;id[0]=command.getInt("id");started.countDown();return true;
                            }return false;
                        };
                        c.worker.submit(document,10000,true,(binding,deadline)->{try{binding.call("Runtime.evaluate",new JSONObject().put("expression","native-proof-fixture-wait"),null,5000,false);}catch(org.json.JSONException error){throw new IOException("fixture_arguments_failed");}return new byte[]{1};},c.documentResults::add);
                        waitFor(()->started.getCount()==0,c,true);check(c.documentResults.isEmpty()&&backend.nativeOpens.get()==0);
                    }
                    check(request.proveNative(child)&&!request.proveNative(child));
                    try{
                        if(entered!=null){waitFor(()->entered.getCount()==0,c,true);if(variant.equals("owner")){network.popupOwned=false;source.core.cancel();}else request.close();child.close();release.countDown();}
                        if(variant.equals("late-ui")){
                            waitFor(()->child.proof()!=null&&!c.ui.tasks.isEmpty(),c,false);request.close();c.ui.drain();check(observer.provenChildren.isEmpty());
                        }else waitFor(()->child.proof()!=null,c,true);
                        if(entered==null&&!variant.equals("late-ui")&&!variant.equals("between"))waitFor(()->observer.provenChildren.size()==1,c,true);
                        MdoBrowserCdpSession.ControlResult proof=child.proof();
                        if(ordinarySuccess||variant.equals("late-ui")||variant.equals("cooperative")){
                            check(proof.ok&&proof.effectMayHaveOccurred&&proof.code==null&&backend.barriers.get()==1);
                            if(ordinarySuccess)check(!observer.candidates.get(0).nativeChildProven()&&observer.candidates.get(1).nativeChildProven());
                            check(real.wires.get(0).proofRead&&real.wires.get(0).cleanupAttempts==1&&decoy.wires.get(0).closed.get()&&!decoy.wires.get(0).otherSawName);
                            if(ordinarySuccess)check(!request.proveNative(child)&&backend.nativeOpens.get()==2);
                            if(variant.equals("cooperative")){waitFor(()->!c.documentResults.isEmpty(),c,true);check(c.documentResults.get(0).code==null&&backend.threads.size()==1&&c.threads.containsAll(backend.threads));}
                        }else{
                            check(!proof.ok&&proof.code!=null&&proof.code.matches("cdp_[a-z_]+"));
                            for(MdoBrowserCdpPopupBroker.Candidate candidate:observer.candidates)check(!candidate.nativeChildProven());
                            if(variant.equals("unique"))check(proof.code.equals("cdp_binding_unique_context_unsupported")&&!real.wires.get(0).proofRead);
                            if(variant.equals("missing"))check(proof.code.equals("cdp_binding_proof_invalid"));
                            if(variant.equals("barrier"))check(proof.code.equals("cdp_attach_confirmation_invalid")&&backend.barriers.get()==1);
                            if(variant.equals("detach"))check(proof.code.equals("cdp_attach_event_invalid")&&!observer.candidates.get(1).current());
                            if(variant.equals("no-guard"))check(proof.code.equals("cdp_popup_interception_required")&&backend.nativeOpens.get()==0&&childView.evaluations==0);
                            if(variant.equals("marker"))check(proof.code.equals("cdp_binding_marker_failed")&&backend.nativeOpens.get()==0&&childView.evaluations==1);
                            if(variant.equals("between"))check(backend.nativeOpens.get()==1&&real.wires.isEmpty()&&observer.provenChildren.isEmpty());
                            if(entered!=null)check(observer.provenChildren.isEmpty()&&proof.effectMayHaveOccurred);
                        }
                    }finally{if(release!=null)release.countDown();releaseDocument.run();}
                    child.close();backend.wire.add(detached("proof-decoy"));if(!variant.equals("detach"))backend.wire.add(detached("proof-real"));
                    request.close();terminal(c,request,observer);
                    check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&backend.resumes.get()==0&&backend.detaches.get()==0&&source.sends==1);
                    check(backend.nativeOpens.get()==(variant.equals("no-guard")||variant.equals("marker")?0:variant.equals("between")?1:2)&&backend.guards.get()==(variant.equals("no-guard")?0:2)&&backend.threads.size()==1);
                    for(CdpBindingProbe.Factory peer:new CdpBindingProbe.Factory[]{real,decoy})for(CdpBindingProbe.Wire wire:peer.wires)check(wire.closed.get());
                    check(!observer.candidates.get(1).nativeChildProven()&&!child.current());
                }
            }
        }
    }
    private static void nativeLateTarget()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context root=c.source();c.ready(root);try(NativeSource source=new NativeSource(c,root)){
                Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);source.open();
                backend.wire.add(attached("proof-decoy","owned"));waitFor(()->observer.candidates.size()==1,c,true);
                check(observer.candidates.get(0).requestProtection());waitFor(()->observer.protectedTargets.size()==1,c,true);
                MdoBrowserCdpPopupNative.Created created=source.create(request);WebView view=created.view();source.adopting=view;
                MdoBrowserPopups.Adoption adoption=source.source.adopt(created);source.ack();check(adoption.ok());
                // The matching target appears only after child construction.
                // Its marker may be the initial WebView, not transferred contents.
                backend.wire.add(attached("proof-real","owned"));waitFor(()->observer.candidates.size()==2,c,true);
                check(observer.candidates.get(1).requestProtection());waitFor(()->observer.protectedTargets.size()==2,c,true);
                CdpBindingProbe.Factory decoy=proofPeer(new WebView(),"success",null,null),late=proofPeer(view,"success",null,null);
                backend.nativeTargets.put("target-proof-decoy",decoy);backend.nativeTargets.put("target-proof-real",late);
                try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                    MdoBrowserCdpPopupNative.Child child=source.source.child(adoption,created,adapter)){
                    check(request.proveNative(child));waitFor(()->observer.provenChildren.size()==1,c,true);
                    check(!child.proof().ok&&backend.nativeOpens.get()==1&&late.wires.isEmpty()&&!observer.candidates.get(1).nativeChildProven());
                    child.close();backend.wire.add(detached("proof-decoy"));backend.wire.add(detached("proof-real"));request.close();terminal(c,request,observer);
                    check(request.result().sourceReleased&&request.result().processCleanupConfirmed&&backend.resumes.get()==0&&backend.detaches.get()==0);
                }
            }
        }
    }
    private static final class OwnershipCase implements AutoCloseable {
        final Backend backend=new Backend();final CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend);
        final NativeSource source;final Observer observer;final MdoBrowserCdpPopupWorker.Request request;final MdoBrowserCdpPopupWorker.Allocation allocation;
        OwnershipCase()throws Exception{
            CdpNetworkBindingProbe.Context root=host.source();host.ready(root);source=new NativeSource(host,root);observer=new Observer(host);
            request=host.worker.prepareNativePopups(source.source,1500,observer);ready(host,request,observer);source.open();
            backend.wire.add(attached("proof-real","owned"));waitFor(()->observer.candidates.size()==1,host,true);
            check(observer.candidates.get(0).requestProtection());waitFor(()->observer.protectedTargets.size()==1,host,true);allocation=request.allocateNative(source.source);
        }
        void cancel(boolean alreadyAcknowledged)throws Exception{
            request.close();waitFor(()->source.sends==1,host,true);if(!alreadyAcknowledged){
                int before=backend.receives.get();waitFor(()->backend.receives.get()>=before+2,host,true);
                check(!request.result().terminal&&backend.closes.get()==0&&source.source.rejectionPending());source.ack();
            }backend.wire.add(detached("proof-real"));terminal(host,request,observer);
        }
        public void close()throws Exception{try{source.close();}finally{host.close();}}
    }
    private static void ownership(String mode)throws Exception{
        if(!mode.equals("ownership"))try(OwnershipCase c=new OwnershipCase()){
            if(mode.equals("ownership-constructor-failure")){
                int before=WebView.constructions;WebView.failConstruction=true;
                try{failure("cdp_popup_native_constructor_unconfirmed",()->c.source.source.construct(c.allocation,c.source.views,"unknown"));}
                finally{WebView.failConstruction=false;}
                check(WebView.constructions==before+1&&!MdoBrowserCdpPopupNative.creationConfirmed()&&c.source.views.size()==2&&c.source.views.values().size()==1);
                failure("cdp_popup_native_allocation_unavailable",()->c.source.source.construct(c.allocation,c.source.views,"retry"));check(WebView.constructions==before+1);
                c.source.views.viewDestroyed(new WebView());check(c.source.views.size()==2);
                c.cancel(false);check(c.request.result().code.equals("cdp_popup_native_constructor_unconfirmed")&&!c.request.result().processCleanupConfirmed);
            }else{
                final boolean[] fail={true};final int[] attempts={0};
                MdoBrowserCdpPopupNative.Created created=c.source.source.construct(c.allocation,c.source.views,"owned",(key,view)->{
                    attempts[0]++;if(fail[0]){if(mode.equals("ownership-disposal-no-ack"))return;throw new IllegalStateException("Controlled host disposal failure");}
                    if(c.source.views.owns(key,view))c.source.views.remove(key);view.destroy();c.source.views.viewDestroyed(view);
                });WebView view=created.view();c.cancel(false);
                check(attempts[0]==1&&view.destroyed==0&&c.source.views.get("owned")==view&&!MdoBrowserCdpPopupNative.creationConfirmed());
                check(c.request.result().code.equals("cdp_popup_native_child_cleanup_unconfirmed")&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
                failure("cdp_popup_native_child_cleanup_unconfirmed",created::close);check(attempts[0]==1);
                fail[0]=false;created.retryCleanup();check(attempts[0]==2&&view.destroyed==1&&c.source.views.size()==1&&!MdoBrowserCdpPopupNative.creationConfirmed());
            }
            check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0);return;
        }
        for(int kind=0;kind<8;kind++)try(OwnershipCase c=new OwnershipCase()){
            final int variant=kind;final int[] callbacks={0};
            MdoBrowserCdpPopupNative.Disposal disposal=(key,view)->{
                callbacks[0]++;check(key.equals("owned")&&c.source.views.get(key)==view);
                if(variant==1){c.source.source.reject();c.source.ack();check(!c.source.source.confirmed());}
                c.source.views.remove(key);view.destroy();c.source.views.viewDestroyed(view);
            };
            MdoBrowserCdpPopupNative.Created created=c.source.source.construct(c.allocation,c.source.views,"owned",variant==0||variant==6?null:disposal);
            WebView view;
            if(variant==0||variant==6){
                try{created.view();throw new AssertionError("View exported before host disposal was registered");}catch(IllegalStateException expected){check(expected.getMessage().equals("cdp_popup_native_disposal_required"));}
                view=WebView.lastConstructed;
            }else view=created.view();
            if(variant==2){c.source.views.manual("owned",view,true);c.source.views.manual("owned",view,false);}
            if(variant==3){
                c.source.adopting=view;MdoBrowserPopups.Adoption adoption=c.source.source.adopt(created);
                failure("cdp_popup_native_handoff_retained",created::close);check(view.destroyed==0&&c.source.views.owns("owned",view));c.source.ack();check(adoption.nativeConsumed());
                failure("cdp_popup_native_handoff_retained",created::close);c.request.close();
                waitFor(c.source.source::confirmed,c.host,true);check(view.destroyed==0&&callbacks[0]==0&&!c.request.result().terminal);
                c.source.views.remove("owned");view.destroy();c.source.views.viewDestroyed(view);created.close();check(view.destroyed==1);
                c.backend.wire.add(detached("proof-real"));terminal(c.host,c.request,c.observer);
            }else if(variant==7){
                check(c.source.core.dismiss(c.source.source.pending().id));c.source.ack();c.source.open();MdoBrowserPopups.Pending next=c.source.source.pending();
                created.close();check(view.destroyed==1&&callbacks[0]==1&&c.source.sends==1&&next==c.source.source.pending()&&next.current());
                c.request.close();waitFor(()->c.source.sends==2,c.host,true);c.source.ack();c.backend.wire.add(detached("proof-real"));terminal(c.host,c.request,c.observer);
            }else{
                WebView replacement=null;
                if(variant==4)created.close();
                if(variant==5){c.source.views.remove("owned");view.destroy();c.source.views.viewDestroyed(view);replacement=new WebView();c.source.views.put("owned",replacement);}
                if(variant==6){c.source.views.visibility("owned",view,false);c.source.views.background(true);}
                c.cancel(variant==1);created.close();
                if(variant==2)check(view.destroyed==0&&callbacks[0]==0&&c.source.views.get("owned")==view);
                else if(variant==5)check(view.destroyed==1&&callbacks[0]==0&&replacement.destroyed==0&&c.source.views.get("owned")==replacement);
                else check(view.destroyed==1&&callbacks[0]==(variant==0||variant==6?0:1)&&c.source.views.get("owned")==null);
            }
            check(c.request.result().processCleanupConfirmed&&c.request.result().sourceReleased&&MdoBrowserCdpPopupNative.creationConfirmed());
            check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0);
        }
    }
    private static void nativeAllocation(String mode)throws Exception{
        for(int kind=0;kind<(mode.equals("allocation-cleanup-failure")?1:5);kind++){
            final int variant=mode.equals("allocation-cleanup-failure")?4:kind==4?5:kind;Backend backend=new Backend();
            try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
                CdpNetworkBindingProbe.Context root=c.source();c.ready(root);try(NativeSource source=new NativeSource(c,root)){
                    Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);
                    failure("cdp_popup_native_allocation_unavailable",()->request.allocateNative(source.source));source.open();
                    failure("cdp_popup_native_capture_pending",()->request.allocateNative(source.source));
                    backend.wire.add(attached("proof-real","owned"));waitFor(()->observer.candidates.size()==1,c,true);
                    failure("cdp_popup_interception_required",()->request.allocateNative(source.source));
                    check(observer.candidates.get(0).requestProtection());waitFor(()->observer.protectedTargets.size()==1,c,true);
                    MdoBrowserCdpPopupWorker.Allocation allocation=request.allocateNative(source.source);
                    check(allocation==request.allocateNative(source.source));int before=WebView.constructions;
                    if(variant==0){
                        failure("cdp_popup_native_child_unavailable",()->source.source.construct(allocation,source.views,"root"));check(WebView.constructions==before);
                        MdoBrowserCdpPopupNative.Created created=source.source.construct(allocation,source.views,"child1");created.attachDisposal(source::dispose);WebView view=created.view();source.adopting=view;
                        check(WebView.constructions==before+1&&view!=root.view&&view.getContext()==root.view.getContext()&&source.views.owns("child1",view));
                        failure("cdp_popup_native_allocation_replayed",()->request.allocateNative(source.source));
                        failure("cdp_popup_native_allocation_unavailable",()->source.source.construct(allocation,source.views,"duplicate"));check(WebView.constructions==before+1);
                        MdoBrowserPopups.Adoption adoption=source.source.adopt(created);source.ack();check(adoption.ok());
                        backend.nativeTargets.put("target-proof-real",proofPeer(view,"success",null,null));
                        try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                            MdoBrowserCdpPopupNative.Child child=source.source.child(adoption,created,adapter)){
                            check(request.proveNative(child));waitFor(()->observer.provenChildren.size()==1,c,true);check(child.proof().ok);
                            source.open();int next=WebView.constructions;
                            failure("cdp_popup_native_capture_pending",()->request.allocateNative(source.source));check(WebView.constructions==next);
                            child.close();source.adopting=null;request.close();
                            waitFor(()->source.sends==2,c,true);source.ack();
                            backend.wire.add(detached("proof-real"));terminal(c,request,observer);
                        }
                    }else if(variant==1){
                        request.close();failure("cdp_popup_native_allocation_unavailable",()->source.source.construct(allocation,source.views,"cancelled"));check(WebView.constructions==before);
                        waitFor(()->source.sends==1,c,true);source.ack();backend.wire.add(detached("proof-real"));terminal(c,request,observer);
                    }else if(variant==2||variant==4){
                        source.allocationFail=true;WebView.failNewDestroy=variant==4;
                        try{failure(variant==4?"cdp_popup_native_child_cleanup_unconfirmed":"cdp_popup_native_construct_failed",()->source.source.construct(allocation,source.views,"failed"));}
                        finally{WebView.failNewDestroy=false;source.allocationFail=false;}
                        WebView failed=WebView.lastConstructed;check(failed!=root.view&&failed.destroyed==1&&root.view.destroyed==0&&WebView.constructions==before+1);
                        check(source.views.get("failed")==null&&source.views.size()==(variant==4?2:1));
                        if(variant==4){check(source.views.values().stream().anyMatch(view->view==failed));failed.destroyFailure=false;failed.destroy();source.views.viewDestroyed(failed);check(source.views.size()==1);}
                        check(source.sends==1);source.ack();backend.wire.add(detached("proof-real"));request.close();terminal(c,request,observer);
                        if(variant==4)check(!request.result().processCleanupConfirmed&&!request.result().sourceReleased&&request.result().code.equals("cdp_popup_native_child_cleanup_unconfirmed"));
                    }else if(variant==5){
                        source.allocationHook=()->{
                            check(source.views.size()==2);List<WebView> owned=source.views.values();source.views.close();
                            for(WebView view:owned){view.destroy();source.views.viewDestroyed(view);}
                        };
                        failure("cdp_popup_native_construct_failed",()->source.source.construct(allocation,source.views,"reentrant"));
                        WebView failed=WebView.lastConstructed;check(failed!=root.view&&failed.destroyed==1&&source.views.nativeDestroyed(failed)&&source.views.size()==0);
                        source.allocationHook=null;check(source.sends==1);source.ack();backend.wire.add(detached("proof-real"));request.close();terminal(c,request,observer);
                    }else{
                        // Legacy adoption has consumption/birth evidence but no
                        // preconstruction target receipt; it cannot be upgraded.
                        WebView view=new WebView();source.adopting=view;MdoBrowserLifecycle.Fresh<WebView> fresh=source.fresh(view);
                        MdoBrowserPopups.Adoption adoption=source.source.adopt(source.source.pending(),fresh);source.ack();
                        try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                            MdoBrowserCdpPopupNative.Child child=source.source.child(adoption,fresh,adapter)){
                            failure("cdp_popup_native_proof_unavailable",()->request.proveNative(child));check(child.proof()==null&&backend.nativeOpens.get()==0&&view.evaluations==0);
                        }
                        backend.wire.add(detached("proof-real"));request.close();terminal(c,request,observer);
                    }
                    if(variant!=4)check(request.result().processCleanupConfirmed&&request.result().sourceReleased);
                    check(backend.resumes.get()==0&&backend.detaches.get()==0);
                }
            }
        }
    }
    private static void nativeMultiple()throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context one=c.source(),two=c.source();c.networkTargets.put(two.adapter.marker(),"other-root");c.ready(one);c.ready(two);
            try(NativeSource a=new NativeSource(c,one);NativeSource b=new NativeSource(c,two)){
                Observer oa=new Observer(c),ob=new Observer(c);
                MdoBrowserCdpPopupWorker.Request ra=c.worker.prepareNativePopups(a.source,1500,oa);ready(c,ra,oa);
                MdoBrowserCdpPopupWorker.Request rb=c.worker.prepareNativePopups(b.source,1500,ob);ready(c,rb,ob);
                b.open();backend.wire.add(attached("proof-decoy","owned"));backend.wire.add(attached("proof-real","owned"));
                waitFor(()->oa.candidates.size()==2&&ob.candidates.size()==2,c,true);
                for(MdoBrowserCdpPopupBroker.Candidate candidate:oa.candidates)check(candidate.requestProtection());
                waitFor(()->oa.protectedTargets.size()==2&&ob.protectedTargets.size()==2,c,true);
                MdoBrowserCdpPopupNative.Created created=b.create(rb);WebView view=created.view();b.adopting=view;
                MdoBrowserPopups.Adoption adoption=b.source.adopt(created);b.ack();check(adoption.ok());
                CdpBindingProbe.Factory decoy=proofPeer(new WebView(),"success",null,null),real=proofPeer(view,"success",null,null);
                backend.nativeTargets.put("target-proof-decoy",decoy);backend.nativeTargets.put("target-proof-real",real);
                try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                    MdoBrowserCdpPopupNative.Child child=b.source.child(adoption,created,adapter)){
                    check(rb.proveNative(child));waitFor(()->ob.provenChildren.size()==1,c,true);
                    check(child.proof().ok&&oa.provenChildren.isEmpty()&&oa.candidates.get(1)==ob.candidates.get(1)&&ob.candidates.get(1).nativeChildProven());
                    backend.wire.add(detached("proof-decoy"));waitFor(()->!oa.candidates.get(0).current(),c,true);
                    ra.close();terminal(c,ra,oa);
                    check(ra.result().sourceReleased&&!ra.result().processCleanupConfirmed&&ob.candidates.get(1).current()&&child.current());
                    child.close();backend.wire.add(detached("proof-real"));rb.close();terminal(c,rb,ob);
                    check(rb.result().sourceReleased&&rb.result().processCleanupConfirmed&&backend.resumes.get()==0&&backend.detaches.get()==0&&backend.guards.get()==2&&backend.nativeOpens.get()==2);
                }
            }
        }
    }
    private static void rejectForeignNative(CdpNetworkWorkerProbe.Case c,Backend backend,MdoBrowserCdpPopupWorker.Request original)throws Exception{
        CdpNetworkBindingProbe.Context other=c.source();
        try(NativeSource source=new NativeSource(c,other)){
            source.source.claim(other.adapter.marker().lease());check(source.source.ready());source.open();WebView view=new WebView();source.adopting=view;
            MdoBrowserLifecycle.Fresh<WebView> fresh=source.fresh(view);MdoBrowserPopups.Adoption adoption=source.source.adopt(source.source.pending(),fresh);source.ack();
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                MdoBrowserCdpPopupNative.Child child=source.source.child(adoption,fresh,adapter)){
                failure("cdp_popup_native_proof_unavailable",()->original.proveNative(child));check(child.proof()==null&&backend.nativeOpens.get()==0&&view.evaluations==0);
            }
            source.source.reject();check(source.source.confirmed());source.source.finish(true);
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
        if(mode.startsWith("ownership"))ownership(mode);
        else if(mode.startsWith("allocation"))nativeAllocation(mode);
        else if(mode.equals("native-proof-post-birth"))nativeLateTarget();
        else if(mode.equals("native-proof-multiple"))nativeMultiple();
        else if(mode.startsWith("native-proof"))nativeProof(mode);
        else if(mode.equals("protection"))protectionSuite();
        else if(mode.startsWith("protection-"))protectionFailure(mode);
        else if(mode.equals("suite")){suite();cooperative();candidates();queued(false);queued(true);armingCancellation();callbackFailure();nativeJoin(false);nativeJoin(true);nativeTerminalBeforeUiClose();nativeOwnerChanged();}
        else if(mode.startsWith("native-")&&!mode.equals("native-cancel"))nativeFailure(mode);
        else poison(mode);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"mode\":\""+mode+"\",\"one_original_executor\":true,\"ui_delivery\":true,\"cooperative_popup_pump\":true,\"protective_interception_scope_tested\":"+mode.startsWith("protection")+",\"retained_target_marker_scope_tested\":"+mode.startsWith("native-proof")+",\"native_popup_adoption_qualified\":false,\"provider_qualified\":false}");
    }
}
