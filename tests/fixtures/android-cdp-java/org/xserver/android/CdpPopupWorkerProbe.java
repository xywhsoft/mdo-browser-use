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
        final Set<String> proofTargets=ConcurrentHashMap.newKeySet();{proofTargets.add("target-proof-real");}
        volatile String barrierMode="normal";volatile Runnable proofConfirmed,closeHook;volatile boolean closeFailure;
        volatile boolean unsupportedArm;
        volatile String guardMode="inline";volatile Runnable guardTimeout;volatile CountDownLatch guardEntered,guardRelease;
        volatile String managedResumeMode,managedFlowMode="normal";volatile Runnable managedResumeHook,managedFlowHook;
        final AtomicInteger continues=new AtomicInteger();final ConcurrentLinkedQueue<String> continuedIds=new ConcurrentLinkedQueue<>();
        volatile String responseMode,responseStream="original-child-stream";volatile boolean holdRead;final AtomicInteger responsePasses=new AtomicInteger(),responseAborts=new AtomicInteger(),bodyClaims=new AtomicInteger(),bodyReads=new AtomicInteger(),bodyCloses=new AtomicInteger(),lateRead=new AtomicInteger();
        volatile Runnable responseIo;
        DocumentRenderer documentRenderer;
        SourceRenderer sourceRenderer;
        static final class SourceRenderer {
            final String mode;int calls,trees;SourceRenderer(String mode){this.mode=mode;}
            void command(JSONObject command,Wire output)throws Exception {
                check(command.length()==4&&command.getString("sessionId").equals("proof-real"));String method=command.getString("method");JSONObject params=command.getJSONObject("params"),result=new JSONObject();calls++;
                if(method.equals("Page.enable")){
                    check(params.length()==0);output.add(new JSONObject().put("method","Page.frameStartedNavigating").put("sessionId","proof-real").put("params",new JSONObject().put("frameId","frame-owned").put("loaderId","load-pending").put("navigationType","differentDocument").put("url","https://private.invalid/pending")));
                    output.add(new JSONObject().put("method","Page.frameStartedLoading").put("sessionId","proof-real").put("params",new JSONObject().put("frameId","frame-owned")));
                    if(mode.equals("source-nonempty"))result.put("unexpected",true);
                }else if(method.equals("Page.getFrameTree")){
                    check(params.length()==0);trees++;JSONObject frame=new JSONObject().put("id","frame-owned").put("loaderId","load-owned");
                    if(mode.equals("source-changed")||mode.equals("source-race")&&trees==1)output.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId","proof-real").put("params",new JSONObject().put("frame",frame)));
                    result.put("frameTree",new JSONObject().put("frame",frame));
                }else{
                    check(method.equals("Network.enable")&&params.length()==3&&params.getInt("maxTotalBufferSize")==0&&params.getInt("maxResourceBufferSize")==0&&params.getInt("maxPostDataSize")==0);
                    if(mode.equals("source-unsupported")){output.add(new JSONObject().put("id",command.getInt("id")).put("sessionId","proof-real").put("error",new JSONObject().put("code",-32601).put("message","synthetic-unread")));return;}
                }
                output.add(ack(command.getInt("id"),"proof-real").put("result",result));
            }
        }
        static final class DocumentRenderer {
            final CdpFramesProbe.Plan frames=new CdpFramesProbe.Plan();final CdpWorldsProbe.Engine worlds;
            final CdpBindingProbe.Wire wire;int resets,runtime;boolean binding=true;
            DocumentRenderer(WebView view)throws Exception{worlds=new CdpWorldsProbe.Engine();worlds.evaluation=(context,expression)->{check(expression.equals("({ok:true,text:'managed'})"));return new JSONObject().put("ok",true).put("text","managed");};frames.extra=worlds;CdpBindingProbe.Factory factory=new CdpBindingProbe.Factory(view);wire=new CdpBindingProbe.Wire(factory,"owned");}
            void loseWorld(String raw,Wire output)throws Exception {
                CdpWorldsProbe.Context found=null;for(CdpWorldsProbe.Context context:worlds.contexts.values())if(context.source==null&&context.frame.equals(raw)){check(found==null);found=context;}
                check(found!=null);final CdpWorldsProbe.Context original=found;worlds.contexts.values().removeIf(context->context==original);
                output.add(new JSONObject().put("method","Runtime.executionContextDestroyed").put("sessionId","proof-real").put("params",new JSONObject().put("executionContextId",original.id)));
            }
            void command(JSONObject command,Wire output)throws Exception{
                JSONObject request=new JSONObject(command.toString());String original=request.getString("sessionId");if(original.equals("proof-real"))request.remove("sessionId");else if(resets>0){String prefix="generation-"+resets+"-";check(original.startsWith(prefix));request.put("sessionId",original.substring(prefix.length()));}
                String method=request.getString("method");JSONObject params=request.getJSONObject("params");
                if(method.equals("Page.enable")&&!request.has("sessionId"))binding=false;
                if(binding&&(method.equals("Runtime.enable")||method.equals("Runtime.globalLexicalScopeNames")||method.equals("Runtime.evaluate")||method.equals("Page.getFrameTree"))){if(method.equals("Runtime.enable"))runtime++;byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);try{wire.send(bytes,1000);}finally{java.util.Arrays.fill(bytes,(byte)0);}}
                else if(method.equals("Runtime.disable")){check(!request.has("sessionId")&&params.length()==0);resets++;binding=true;worlds.namespaces.remove(wire);wire.queue(ack(request.getInt("id"),null));}
                else if(method.equals("Target.setAutoAttach")&&!params.getBoolean("autoAttach")){check(!request.has("sessionId")&&!params.getBoolean("waitForDebuggerOnStart")&&params.getBoolean("flatten")&&params.length()==3);frames.armed.clear();wire.queue(ack(request.getInt("id"),null));}
                else if(!frames.send(request,wire))throw new AssertionError("Unapproved document command");
                byte[] bytes;while((bytes=wire.replies.pollFirst())!=null){JSONObject packet=new JSONObject(new String(bytes,StandardCharsets.UTF_8));java.util.Arrays.fill(bytes,(byte)0);
                    if(!packet.has("sessionId"))packet.put("sessionId","proof-real");else if(resets>0)packet.put("sessionId","generation-"+resets+"-"+packet.getString("sessionId"));
                    if(resets>0&&packet.optString("method").startsWith("Target.")&&packet.optJSONObject("params")!=null&&packet.getJSONObject("params").has("sessionId"))packet.getJSONObject("params").put("sessionId","generation-"+resets+"-"+packet.getJSONObject("params").getString("sessionId"));output.add(packet);}
            }
        }
        public MdoBrowserCdpPopupBroker create(BooleanSupplier current,LongSupplier clock){creates.incrementAndGet();threads.add(Thread.currentThread());Wire next=new Wire();wire=next;
            return new MdoBrowserCdpPopupBroker(current,clock,(timeout,alive,events)->{opens.incrementAndGet();return MdoBrowserCdpSession.ownedBrowser(next,alive,events,clock::getAsLong);},
                (target,timeout,alive,events)->{threads.add(Thread.currentThread());nativeOpens.incrementAndGet();CdpBindingProbe.Factory peer=nativeTargets.get(target);
                    if(peer==null)throw new IOException("cdp_fixture_unregistered_target");return peer.open(proofTargets.contains(target)?"owned":"other",timeout,alive,events);});}
        private final class Wire implements MdoBrowserCdpSession.Wire {
            final ConcurrentLinkedQueue<byte[]> incoming=new ConcurrentLinkedQueue<>();
            void add(JSONObject packet){incoming.add(packet.toString().getBytes(StandardCharsets.UTF_8));}
            public void send(byte[] bytes,int timeout)throws IOException{
                threads.add(Thread.currentThread());try{JSONObject command=new JSONObject(new String(bytes,StandardCharsets.UTF_8));String method=command.getString("method");int id=command.getInt("id");
                    if(sourceRenderer!=null&&command.has("sessionId")&&(method.equals("Page.enable")||method.equals("Page.getFrameTree")||method.equals("Network.enable"))){sourceRenderer.command(command,this);return;}
                    if(documentRenderer!=null&&command.has("sessionId")&&!method.startsWith("Fetch.")&&!method.startsWith("IO.")&&!method.equals("Runtime.runIfWaitingForDebugger")){documentRenderer.command(command,this);return;}
                    if(responseMode!=null&&(method.equals("Fetch.takeResponseBodyAsStream")||method.equals("Fetch.failRequest")||method.startsWith("IO.")||method.equals("Fetch.continueRequest")&&continues.get()>0)){
                        Runnable audit=responseIo;if(audit!=null)audit.run();
                        JSONObject params=command.getJSONObject("params");String child=command.getString("sessionId");check(command.length()==4&&child.equals("proof-real"));JSONObject value=new JSONObject();
                        if(method.startsWith("IO.")){check(params.getString("handle").equals(responseStream));
                            if(method.equals("IO.read")){check(params.length()==2&&params.getInt("size")==65536);bodyReads.incrementAndGet();if(holdRead){lateRead.set(id);return;}value.put("data","AAH/").put("base64Encoded",true).put("eof",true);}
                            else{check(method.equals("IO.close")&&params.length()==1);bodyCloses.incrementAndGet();}
                        }else{check(params.getString("requestId").equals("initial-0")||params.getString("requestId").startsWith("response-extra"));
                            if(method.equals("Fetch.takeResponseBodyAsStream")){check(params.length()==1);bodyClaims.incrementAndGet();value.put("stream",responseStream);}
                            else if(method.equals("Fetch.failRequest")){check(params.length()==2&&params.getString("errorReason").equals("Aborted"));responseAborts.incrementAndGet();}
                            else{check(params.length()==1);responsePasses.incrementAndGet();}
                        }add(new JSONObject().put("id",id).put("sessionId",child).put("result",value));return;
                    }
                    if(method.equals("Target.setAutoAttach")){
                        if(unsupportedArm&&command.getJSONObject("params").getBoolean("autoAttach")){add(new JSONObject().put("id",id).put("error",new JSONObject().put("code",-32601).put("message","synthetic-unread")));return;}
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
                    }else if(method.equals("Runtime.runIfWaitingForDebugger")){
                        resumes.incrementAndGet();String child=command.getString("sessionId");
                        if(managedResumeMode!=null){
                            if(command.length()!=4||command.getJSONObject("params").length()!=0||!child.equals("proof-real"))throw new AssertionError("Unapproved protected resume");
                            Runnable hook=managedResumeHook;if(hook!=null)hook.run();
                            if(managedResumeMode.equals("send-failure"))throw new IOException("controlled_partial_resume_send");
                            if(managedResumeMode.equals("timeout"))return;
                            if(managedResumeMode.equals("unsupported")||managedResumeMode.equals("rejected")){add(new JSONObject().put("id",id).put("sessionId",child).put("error",new JSONObject().put("code",managedResumeMode.equals("unsupported")?-32601:-32000).put("message","synthetic-unread")));return;}
                            JSONObject result=ack(id,managedResumeMode.equals("mismatch")?null:child);if(managedResumeMode.equals("nonempty"))result.getJSONObject("result").put("unexpected",true);add(result);
                        }else add(ack(id,child));
                    }
                    else if(method.equals("Fetch.continueRequest")){
                        JSONObject params=command.getJSONObject("params");String request=params.getString("requestId"),child=command.getString("sessionId");
                        if(managedResumeMode==null||command.length()!=4||params.length()!=1||!child.equals("proof-real")||!request.equals("initial-0")&&!request.equals("managed-next"))throw new AssertionError("Unapproved original request continuation");
                        continues.incrementAndGet();continuedIds.add(request);Runnable hook=managedFlowHook;if(hook!=null)hook.run();
                        if(managedFlowMode.equals("send-failure"))throw new IOException("controlled_partial_continue_send");
                        if(managedFlowMode.equals("timeout"))return;
                        if(managedFlowMode.equals("rejected")){add(new JSONObject().put("id",id).put("sessionId",child).put("error",new JSONObject().put("code",-32000).put("message","synthetic-unread")));return;}
                        if(managedFlowMode.equals("limit"))for(int i=0;i<33;i++)add(handoffPaused("response-overflow-"+i,true));
                        else add((responseMode==null?handoffPaused(request,true):responsePaused(request,responseMode)).put("sessionId",managedFlowMode.equals("foreign")?"foreign":child));
                        if("source-captured-epoch".equals(responseMode))add(new JSONObject().put("method","Page.frameNavigated").put("sessionId",child).put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","load-replaced"))));
                        JSONObject result=ack(id,child);if(managedFlowMode.equals("nonempty"))result.getJSONObject("result").put("unexpected",true);add(result);
                    }
                    else if(method.equals("Target.getTargetInfo")){
                        JSONObject p=command.getJSONObject("params");if(command.length()!=3||p.length()!=1||!proofTargets.contains(p.getString("targetId")))throw new AssertionError("Caller-selected target barrier");barriers.incrementAndGet();
                        if(barrierMode.equals("detach"))add(detached("proof-real"));
                        add(new JSONObject().put("id",id).put("result",new JSONObject().put("targetInfo",new JSONObject().put("targetId",barrierMode.equals("wrong")?"foreign":p.getString("targetId")).put("type","page"))));
                        Runnable callback=proofConfirmed;if(callback!=null)callback.run();
                    }
                    else if(method.equals("Target.detachFromTarget")){detaches.incrementAndGet();add(ack(id,null));}
                    else throw new AssertionError("Unapproved popup worker command");
                }catch(IOException error){throw error;}catch(RuntimeException|Error error){throw error;}catch(Exception error){throw new IOException("fixture_command_failed");}
            }
            public byte[] receive(int timeout){threads.add(Thread.currentThread());receives.incrementAndGet();byte[] value=incoming.poll();if(value==null)try{Thread.sleep(1);}catch(InterruptedException error){Thread.currentThread().interrupt();}return value;}
            public void close(){closes.incrementAndGet();Runnable hook=closeHook;if(hook!=null)hook.run();if(closeFailure)throw new IllegalStateException("controlled_original_transport_close_failure");}
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
        final MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(8);final WebView view;int sends;Runnable consumed,sendHook;boolean fail;WebView adopting;
        NativeSource(CdpNetworkWorkerProbe.Case host,CdpNetworkBindingProbe.Context network)throws Exception{
            this.host=host;view=network.view;views.put("root",view);
            core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
                public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}
            },budget,()->true,()->1L,blocked->check(host.ui.onThread()));
            source=new MdoBrowserCdpPopupNative(network.adapter,view,core);
        }
        void open(){check(core.open(new MdoBrowserPopups.Transport<WebView>(){
            private final Object token=new Object();public Object token(){return token;}
            public void send(WebView child,Runnable callback){check(host.ui.onThread()&&child==adopting);sends++;consumed=callback;Runnable hook=sendHook;sendHook=null;if(hook!=null)hook.run();if(fail)throw new IllegalStateException("controlled-native-send-failure");}
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
                created.close();created.close();check(view.destroyed==0&&c.source.views.owns("owned",view)&&c.source.source.rejectionPending());c.request.close();
                int before=c.backend.receives.get();waitFor(()->c.backend.receives.get()>=before+2,c.host,true);check(!c.request.result().terminal&&view.destroyed==0);
                c.source.ack();check(adoption.nativeConsumed());waitFor(c.source.source::confirmed,c.host,true);
                check(view.destroyed==1&&callbacks[0]==1&&!c.request.result().terminal);created.close();check(view.destroyed==1);
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
    private static void retirement(String mode)throws Exception{
        if(!mode.equals("retirement"))try(OwnershipCase c=new OwnershipCase()){
            final int[] attempts={0};final boolean[] fail={true};
            MdoBrowserCdpPopupNative.Created created=c.source.source.construct(c.allocation,c.source.views,"owned",(key,view)->{
                attempts[0]++;if(fail[0]&&!mode.equals("retirement-no-consumption")&&!mode.equals("retirement-opener-loss")){
                    if(mode.equals("retirement-disposal-no-ack"))return;throw new IllegalStateException("controlled adopted disposal failure");
                }c.source.dispose(key,view);
            });WebView view=created.view();c.source.adopting=view;MdoBrowserPopups.Adoption adopted=c.source.source.adopt(created);
            if(mode.equals("retirement-opener-loss"))c.source.source.lease().invalidate();
            c.request.close();
            if(mode.equals("retirement-opener-loss")){
                terminal(c.host,c.request,c.observer);check(c.request.result().code.equals("cdp_popup_broker_opener_lost")&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
                check(view.destroyed==0&&attempts[0]==0&&c.source.source.rejectionPending());c.source.ack();
                check(view.destroyed==1&&attempts[0]==1&&adopted.nativeConsumed()&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
                check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0);return;
            }
            waitFor(c.source.source::rejectionPending,c.host,true);
            int before=c.backend.receives.get();waitFor(()->c.backend.receives.get()>=before+2,c.host,true);
            check(!c.request.result().terminal&&view.destroyed==0&&attempts[0]==0&&c.source.sends==1);
            if(mode.equals("retirement-no-consumption")){
                c.host.time.addAndGet(5001);terminal(c.host,c.request,c.observer);
                check(c.request.result().code.equals("cdp_popup_native_cancel_timeout")&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
                check(view.destroyed==0&&attempts[0]==0&&!adopted.nativeConsumed()&&c.source.views.size()==2);
                c.source.ack();check(view.destroyed==1&&attempts[0]==1&&adopted.nativeConsumed());
                created.close();c.source.ack();check(view.destroyed==1&&attempts[0]==1);
            }else{
                c.source.ack();terminal(c.host,c.request,c.observer);
                check(c.request.result().code.equals("cdp_popup_native_child_cleanup_unconfirmed")&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
                check(attempts[0]==1&&view.destroyed==0&&c.source.views.size()==2&&!MdoBrowserCdpPopupNative.creationConfirmed());
                failure("cdp_popup_native_child_cleanup_unconfirmed",created::close);check(attempts[0]==1);
                fail[0]=false;created.retryCleanup();check(view.destroyed==1&&attempts[0]==2&&c.source.views.size()==1&&!MdoBrowserCdpPopupNative.creationConfirmed());
            }
            check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0);return;
        }
        for(int kind=0;kind<7;kind++)try(OwnershipCase c=new OwnershipCase()){
            final int variant=kind==6?7:kind;final int[] calls={0};final MdoBrowserCdpPopupNative.Child[] proof={null};
            MdoBrowserCdpPopupNative.Created created=c.source.source.construct(c.allocation,c.source.views,"owned",(key,view)->{
                calls[0]++;check(!c.source.source.confirmed());check(c.source.source.rejectionPending());
                if(proof[0]!=null)check(!proof[0].current());
                c.source.dispose(key,view);
            });WebView view=created.view();c.source.adopting=view;
            if(variant==5)c.source.sendHook=()->{c.request.close();c.source.source.reject();check(view.destroyed==0&&c.source.source.rejectionPending());c.source.ack();check(view.destroyed==1);};
            MdoBrowserPopups.Adoption adopted=c.source.source.adopt(created);
            check(adopted.dispatched()&&c.source.sends==1);
            if(variant==7){
                c.source.views.manual("owned",view,true);c.source.views.manual("owned",view,false);c.request.close();terminal(c.host,c.request,c.observer);
                check(view.destroyed==0&&calls[0]==0&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
                c.source.ack();check(view.destroyed==0&&adopted.nativeConsumed());continue;
            }
            if(variant==3){
                c.source.ack();try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota())){
                    proof[0]=c.source.source.child(adopted,created,adapter);check(proof[0].current());created.close();check(!proof[0].current()&&view.destroyed==1);proof[0].close();
                }
            }
            if(variant==4){c.source.views.remove("owned");view.destroy();c.source.views.viewDestroyed(view);}
            if(variant==0||variant==4){created.close();created.close();
                try{created.view();throw new AssertionError("Retiring creation remained exportable");}catch(IllegalStateException expected){check(true);}}
            c.request.close();
            if(variant!=5){int before=c.backend.receives.get();try{waitFor(()->c.backend.receives.get()>=before+2,c.host,true);}
                catch(AssertionError error){throw new AssertionError("Retirement variant "+variant+" result="+c.request.result().code+" terminal="+c.request.result().terminal+" native="+c.source.source.failure()+" pending="+c.source.source.rejectionPending(),error);}}
            if(variant!=3&&variant!=5){
                check(!c.request.result().terminal&&c.source.source.rejectionPending()&&calls[0]==0&&view.destroyed==(variant==4?1:0));
                if(variant==2){c.backend.wire.add(detached("proof-real"));waitFor(()->!c.observer.candidates.get(0).current(),c.host,true);check(!c.request.result().terminal);}
                c.source.ack();
            }
            waitFor(c.source.source::confirmed,c.host,true);check(adopted.nativeConsumed()&&view.destroyed==1&&calls[0]==(variant==4?0:1));
            if(variant!=2){check(!c.request.result().terminal);c.backend.wire.add(detached("proof-real"));}
            terminal(c.host,c.request,c.observer);created.close();c.source.ack();
            check(view.destroyed==1&&c.source.sends==1&&c.request.result().sourceReleased&&c.request.result().processCleanupConfirmed&&MdoBrowserCdpPopupNative.creationConfirmed());
            check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0&&c.source.views.size()==1);
        }
    }
    private static void admissionFence()throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            final boolean[] fail={true};
            MdoBrowserCdpPopupNative.Created old=c.source.source.construct(c.allocation,c.source.views,"older",(key,view)->{
                if(fail[0])throw new IllegalStateException("controlled previous creation cleanup failure");c.source.dispose(key,view);
            });WebView first=old.view();check(c.source.core.dismiss(c.source.source.pending().id));c.source.ack();c.source.open();
            MdoBrowserCdpPopupWorker.Allocation allocation=c.request.allocateNative(c.source.source);
            MdoBrowserCdpPopupNative.Created next=c.source.source.construct(allocation,c.source.views,"newer",c.source::dispose);WebView child=next.view();
            MdoBrowserPopups.Pending pending=c.source.source.pending();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            c.host.worker.submit(c.host.document(),10000,false,(binding,deadline)->{
                entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("fixture_fence_wait_expired");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("fixture_interrupted");}return new byte[]{1};
            },c.host.documentResults::add);
            waitFor(()->entered.getCount()==0,c.host,true);
            try{
                failure("cdp_popup_native_child_cleanup_unconfirmed",old::close);check(!c.source.source.current()&&!allocation.current()&&pending.current()&&c.source.source.pending()==pending);
                c.source.adopting=child;
                try{c.source.source.adopt(next);throw new AssertionError("Fenced native source dispatched a previously allocated child");}
                catch(IllegalStateException expected){check(expected.getMessage().equals("popup_request_unavailable"));}
                check(c.source.sends==1&&pending.current()&&child.destroyed==0&&first.destroyed==0);
            }finally{c.source.adopting=null;c.request.close();release.countDown();}
            waitFor(()->c.source.sends==2,c.host,true);c.source.ack();terminal(c.host,c.request,c.observer);
            check(child.destroyed==1&&first.destroyed==0&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
            fail[0]=false;old.retryCleanup();check(first.destroyed==1&&!MdoBrowserCdpPopupNative.creationConfirmed()&&c.source.views.size()==1);
            check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0);
        }
    }
    /** Native UI ownership terminal is controlled here; the Browser attachment
     * is deliberately not promoted or represented as a clean protocol handoff. */
    private static void nativePromotion(String mode)throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView view=created.view();
            c.source.adopting=view;MdoBrowserPopups.Adoption adoption=c.source.source.adopt(created);c.source.ack();
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                MdoBrowserCdpPopupNative.Child child=c.source.source.child(adoption,created,adapter)){
                failure("cdp_popup_native_promotion_unavailable",()->product.promote("owned",c.source.source,created,child));
                CdpBindingProbe.Factory peer=proofPeer(view,"success",null,null);c.backend.nativeTargets.put("target-proof-real",peer);
                check(c.request.proveNative(child));waitFor(()->!c.observer.provenChildren.isEmpty(),c.host,true);check(child.proof().ok);
                MdoBrowserCdpPopupNative.Created second=null;MdoBrowserCdpPopupNative.Child secondProof=null;MdoBrowserCdpView secondAdapter=null;WebView secondView=null;
                if(mode.equals("promotion-multiple")){
                    c.source.open();c.backend.wire.add(attached("proof-second","owned"));waitFor(()->c.observer.candidates.size()==2,c.host,true);check(c.observer.candidates.get(1).requestProtection());waitFor(()->c.observer.protectedTargets.size()==2,c.host,true);
                    second=product.construct(c.source.source,c.request.allocateNative(c.source.source),"second");secondView=second.view();c.source.adopting=secondView;
                    MdoBrowserPopups.Adoption next=c.source.source.adopt(second);c.source.ack();final WebView exactSecond=secondView;
                    secondAdapter=MdoBrowserCdpView.network(exactSecond,v->v==exactSecond,new MdoBrowserCdpMarker.DocumentQuota());secondProof=c.source.source.child(next,second,secondAdapter);
                    c.backend.proofTargets.add("target-proof-second");c.backend.nativeTargets.put("target-proof-second",proofPeer(secondView,"success",null,null));check(c.request.proveNative(secondProof));waitFor(()->c.observer.provenChildren.size()==2,c.host,true);
                }
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                c.host.worker.submit(c.host.document(),10000,false,(binding,deadline)->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}return new byte[]{1};},c.host.documentResults::add);
                try{
                    waitFor(()->entered.getCount()==0,c.host,true);int evaluations=view.evaluations;
                    if(mode.equals("promotion-denied")){
                        failure("cdp_popup_native_promotion_unavailable",()->product.promote("wrong",c.source.source,created,child));
                        c.source.views.visibility("owned",view,false);failure("cdp_popup_native_promotion_unavailable",()->product.promote("owned",c.source.source,created,child));
                        check(MdoBrowserCdpPopupNative.promotedUiOwners()==0&&view.destroyed==0);return;
                    }
                    MdoBrowserCdpPopupNative.Retirement original=c.source.source.retirement();MdoBrowserCdpPopupNative.Promotion promoted=product.promote("owned",c.source.source,created,child);
                    check(promoted.current()&&promoted.matches(child)&&promoted.view()==view&&!child.current()&&!promoted.ended()&&!promoted.nativeDestroyed());
                    for(String name:new String[]{"owner","book","allocation"}){java.lang.reflect.Field field=MdoBrowserCdpPopupNative.Created.class.getDeclaredField(name);field.setAccessible(true);Object value=field.get(created);check(value==null||value instanceof java.lang.ref.WeakReference&&((java.lang.ref.WeakReference<?>)value).get()==null);}
                    for(String name:new String[]{"opener","allocation"}){java.lang.reflect.Field field=MdoBrowserCdpPopupNative.Child.class.getDeclaredField(name);field.setAccessible(true);check(field.get(child)==null);}
                    check(MdoBrowserCdpPopupNative.promotedUiOwners()==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==1&&view.evaluations==evaluations&&product.documentFailures.get("owned").equals("popup_world_unqualified"));
                    MdoBrowserCdpPopupNative.Promotion other=second==null?null:product.promote("second",c.source.source,second,secondProof);if(other!=null){check(other.current()&&other.matches(secondProof)&&MdoBrowserCdpPopupNative.promotedUiOwners()==2);secondProof.close();secondAdapter.close();}
                    failure("cdp_popup_native_promotion_unavailable",()->c.source.source.promote(created,child));child.close();
                    original.request(true);original.deliver();check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==(other==null?1:2)&&promoted.current()&&promoted.view()==view&&view.destroyed==0);
                    if(mode.equals("promotion-parent-reuse")){c.source.source.claim(c.source.source.lease());c.source.source.retainUiOwner();check(c.source.source.ready());}
                    if(mode.equals("promotion-parent-close")){
                        product.windows.put("root",c.source.core);product.retire("root",c.source.view);c.source.source.lease().invalidate();
                        check(c.source.view.destroyed==1&&promoted.current()&&promoted.view()==view&&view.destroyed==0);
                    }
                    if(other!=null){check(other.current()&&secondView.destroyed==0&&c.backend.closes.get()==0);c.host.worker.close();check(c.backend.closes.get()==0&&!other.current()&&secondView.destroyed==0);Handler.drain();check(other.ended()&&other.nativeDestroyed()&&secondView.destroyed==1&&c.backend.closes.get()==1);}
                    if(mode.equals("promotion-human")){c.source.views.manual("owned",view,true);check(!promoted.current()&&view.destroyed==0);}
                    if(mode.equals("promotion-global")||mode.equals("promotion-ui-loss")){
                        Handler.rejectPost=mode.equals("promotion-ui-loss");c.host.worker.close();check(!promoted.current()&&c.backend.closes.get()==0&&view.destroyed==0);
                        if(Handler.rejectPost){Handler.drain();check(view.destroyed==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==1);Handler.rejectPost=false;product.resume();}
                        else Handler.drain();
                        check(promoted.ended()&&promoted.nativeDestroyed()&&view.destroyed==1&&c.backend.closes.get()==1);
                    }else{
                        if(mode.equals("promotion-failure"))product.failClose=true;
                        MdoBrowserPopups<WebView> nestedCore=null;MdoBrowserCdpView nestedAdapter=null;final Runnable[] nestedAck={null};final int[] nestedSends={0};
                        MdoBrowserPopups.Budget nestedBudget=new MdoBrowserPopups.Budget(8);
                        if(mode.equals("promotion-nested")){
                            nestedCore=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){public long now(){return c.host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}},nestedBudget,()->true,()->1L,blocked->{});
                            nestedAdapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());MdoBrowserCdpPopupNative nested=new MdoBrowserCdpPopupNative(nestedAdapter,view,nestedCore);
                            nested.claim(nestedAdapter.marker().lease());nested.retainUiOwner();check(nested.ready());product.windows.put("owned",nestedCore);
                            check(nestedCore.open(new MdoBrowserPopups.Transport<WebView>(){public Object token(){return this;}public void send(WebView ignored,Runnable ack){nestedSends[0]++;nestedAck[0]=ack;}},false));
                        }
                        promoted.close();Handler.drain();
                        if(nestedCore!=null){check(!promoted.ended()&&!promoted.failed()&&view.destroyed==0&&nestedBudget.used()==1&&nestedSends[0]==1);nestedAck[0].run();check(view.destroyed==1&&nestedBudget.used()==0&&nestedSends[0]==1);nestedAdapter.close();nestedCore.close();nestedCore.viewDestroyed(view);}
                        if(mode.equals("promotion-failure")){
                            check(promoted.failed()&&!promoted.ended()&&!promoted.nativeDestroyed()&&view.destroyed==0&&!MdoBrowserCdpPopupNative.creationConfirmed());
                            promoted.close();Handler.drain();check(view.destroyed==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==1);
                            c.source.dispose("owned",view);MdoBrowserCdpPopupNative.viewDestroyed(view);
                            check(promoted.failed()&&promoted.ended()&&promoted.nativeDestroyed()&&view.destroyed==1&&!MdoBrowserCdpPopupNative.creationConfirmed());
                        }else if(mode.equals("promotion-human")){
                            check(promoted.ended()&&!promoted.nativeDestroyed()&&view.destroyed==0&&c.source.views.owns("owned",view));product.retire("owned",view);check(promoted.nativeDestroyed()&&view.destroyed==1);
                        }else check(promoted.ended()&&promoted.nativeDestroyed()&&view.destroyed==1);
                    }
                    if(mode.equals("promotion-parent-reuse")){check(c.source.source.current()&&c.source.source.failure()==null);MdoBrowserCdpPopupNative.Retirement fresh=c.source.source.retirement();fresh.request(true);fresh.deliver();}
                    promoted.close();Handler.drain();MdoBrowserCdpPopupNative.reconcileRetirements();
                    check(MdoBrowserCdpPopupNative.promotedUiOwners()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&view.destroyed==1&&c.backend.resumes.get()==0&&c.backend.detaches.get()==0&&view.evaluations==evaluations);
                }finally{Handler.rejectPost=false;release.countDown();}
            }
        }
    }
    /** Natural parent terminal while the same Browser attachment and its
     * requests stay paused. No controlled executor/native terminal here. */
    private static JSONObject handoffPaused(String id,boolean response)throws Exception{JSONObject params=new JSONObject().put("requestId",id);if(response)params.put("responseStatusCode",200);return new JSONObject().put("method","Fetch.requestPaused").put("sessionId","proof-real").put("params",params);}
    private static void protectedHandoff(String mode)throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView view=created.view();
            c.source.adopting=view;MdoBrowserPopups.Adoption adoption=c.source.source.adopt(created);c.source.ack();
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                MdoBrowserCdpPopupNative.Child child=c.source.source.child(adoption,created,adapter)){
                failure("cdp_popup_handoff_unavailable",()->product.handoff("owned",c.source.source,created,child));
                c.backend.nativeTargets.put("target-proof-real",proofPeer(view,"success",null,null));check(c.request.proveNative(child));waitFor(()->!c.observer.provenChildren.isEmpty(),c.host,true);
                MdoBrowserCdpPopupBroker.Candidate candidate=c.observer.candidates.get(0);MdoBrowserCdpPopupBroker.Handoff handoff=child.handoff();
                check(child.proof().ok&&handoff!=null&&!handoff.current()&&!handoff.transferred()&&candidate.nativeChildProven());
                // The cross-thread receipt must not retain a broker/session,
                // original Child/allocation, registration or SDK object.
                for(java.lang.reflect.Field field:MdoBrowserCdpPopupBroker.Handoff.class.getDeclaredFields()){
                    Class<?> kind=field.getType();check(kind==Object.class||kind==java.util.concurrent.atomic.AtomicBoolean.class||kind==java.util.concurrent.atomic.AtomicInteger.class||kind==java.util.concurrent.atomic.AtomicReference.class||kind==long.class||kind==LongSupplier.class);
                }
                if(mode.equals("handoff-denied")){
                    handoff.close();failure("cdp_popup_handoff_unavailable",()->product.handoff("owned",c.source.source,created,child));check(!handoff.current()&&!handoff.transferred()&&child.current()&&view.destroyed==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0);return;
                }
                int evaluations=view.evaluations,opens=c.backend.opens.get(),nativeOpens=c.backend.nativeOpens.get();
                MdoBrowserCdpPopupNative.Promotion promoted=product.handoff("owned",c.source.source,created,child);
                check(handoff.current()&&handoff.transferred()&&promoted.current()&&candidate.nativeChildProven()&&!child.current()&&view.evaluations==evaluations);
                failure("cdp_popup_handoff_unavailable",()->c.source.source.handoff(created,child));
                c.request.close();terminal(c.host,c.request,c.observer);
                check(c.request.result().code.equals("cdp_popup_prepare_cancelled")&&c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed&&handoff.current()&&candidate.nativeChildProven());
                check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==1&&view.destroyed==0&&c.backend.closes.get()==0&&c.backend.disables.get()==0);
                for(String name:new String[]{"nativeBound","nativeChild","provenOpener","possibleOpeners"}){java.lang.reflect.Field field=MdoBrowserCdpPopupBroker.Candidate.class.getDeclaredField(name);field.setAccessible(true);Object retained=field.get(candidate);check(retained==null||retained instanceof Object[]&&((Object[])retained).length==0);}
                c.backend.wire.add(handoffPaused("handoff-first",false));waitFor(()->candidate.retainedRequestCount()==2,c.host,true);
                check(handoff.current()&&candidate.nativeChildProven()&&c.backend.opens.get()==opens&&c.backend.nativeOpens.get()==nativeOpens&&c.backend.guards.get()==1);
                if(mode.equals("handoff-parent-close")){
                    product.windows.put("root",c.source.core);product.retire("root",c.source.view);c.source.source.lease().invalidate();
                    check(c.source.view.destroyed==1&&handoff.current()&&candidate.nativeChildProven()&&view.destroyed==0);
                }
                if(mode.equals("handoff-parent-reuse")){
                    Observer next=new Observer(c.host);MdoBrowserCdpPopupWorker.Request request=c.host.worker.prepareNativePopups(c.source.source,1500,next);ready(c.host,request,next);request.close();terminal(c.host,request,next);
                    check(request.result().sourceReleased&&!request.result().processCleanupConfirmed&&handoff.current()&&c.backend.opens.get()==opens&&c.backend.enables.get()==1);
                }
                boolean failing=mode.equals("handoff-expiry")||mode.equals("handoff-response")||mode.equals("handoff-limit");
                if(failing){
                    if(mode.equals("handoff-expiry"))c.host.time.addAndGet(15000);
                    else if(mode.equals("handoff-response"))c.backend.wire.add(handoffPaused("handoff-first",true));
                    else for(int i=1;i<=32;i++)c.backend.wire.add(handoffPaused("handoff-limit-"+i,false));
                    waitFor(()->!handoff.current()&&promoted.ended(),c.host,true);check(view.destroyed==1&&c.backend.closes.get()==1&&c.backend.disables.get()==0);
                }else if(mode.equals("handoff-global")||mode.equals("handoff-ui-loss")||mode.equals("handoff-disposal-failure")){
                    Handler.rejectPost=mode.equals("handoff-ui-loss");product.failClose=mode.equals("handoff-disposal-failure");c.host.worker.close();check(!handoff.current()&&view.destroyed==0&&c.backend.closes.get()==0);
                    if(Handler.rejectPost){Handler.drain();check(view.destroyed==0);Handler.rejectPost=false;product.resume();}else Handler.drain();
                    if(product.failClose){check(promoted.failed()&&!promoted.ended()&&view.destroyed==0&&c.backend.closes.get()==0);c.source.dispose("owned",view);MdoBrowserCdpPopupNative.viewDestroyed(view);check(promoted.failed()&&!MdoBrowserCdpPopupNative.creationConfirmed());}
                    check(promoted.ended()&&view.destroyed==1&&c.backend.closes.get()==1);
                }else{
                    // Acknowledge the actual original Browser detach. No
                    // manufactured debugger resume/Fetch disable or reattach.
                    c.backend.wire.add(detached("proof-real"));waitFor(()->promoted.ended()&&c.backend.closes.get()==1,c.host,true);
                    check(view.destroyed==1&&!handoff.current()&&!candidate.current()&&c.backend.disables.get()==1);
                }
                check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0&&view.evaluations==evaluations&&MdoBrowserCdpPopupNative.promotedUiOwners()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0);
            }finally{Handler.rejectPost=false;}
        }
    }
    private static void managedActivation(String mode)throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView view=created.view();c.source.adopting=view;
            MdoBrowserPopups.Adoption adoption=c.source.source.adopt(created);c.source.ack();
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());MdoBrowserCdpPopupNative.Child child=c.source.source.child(adoption,created,adapter)){
                c.backend.nativeTargets.put("target-proof-real",proofPeer(view,"success",null,null));check(c.request.proveNative(child));waitFor(()->!c.observer.provenChildren.isEmpty(),c.host,true);
                MdoBrowserCdpPopupBroker.Candidate candidate=c.observer.candidates.get(0);MdoBrowserCdpPopupBroker.Handoff handoff=child.handoff();
                failure("cdp_popup_activation_unavailable",()->product.activate(null,handoff));check(c.backend.resumes.get()==0&&c.backend.continues.get()==0);
                MdoBrowserCdpPopupNative.Promotion promoted=product.handoff("owned",c.source.source,created,child);c.request.close();terminal(c.host,c.request,c.observer);
                check(handoff.current()&&candidate.retainedRequestCount()==1&&candidate.retainedResponseCount()==0&&handoff.activationResult()==null&&handoff.flowFailure()==null);
                int evaluations=view.evaluations,opens=c.backend.opens.get(),proofs=c.backend.nativeOpens.get();check(product.documentFailures.get("owned").equals("popup_world_unqualified"));
                if(mode.equals("activation-parent-close")){product.windows.put("root",c.source.core);product.retire("root",c.source.view);c.source.source.lease().invalidate();check(c.source.view.destroyed==1&&handoff.current());}
                String resume=mode.startsWith("activation-resume-")?mode.substring("activation-resume-".length()):"normal";
                String flow=mode.startsWith("activation-flow-")?mode.substring("activation-flow-".length()):"normal";
                c.backend.managedResumeMode=resume;c.backend.managedFlowMode=flow;
                if(resume.equals("timeout"))c.backend.managedResumeHook=()->c.host.time.addAndGet(300);
                if(flow.equals("timeout"))c.backend.managedFlowHook=()->c.host.time.addAndGet(300);
                if(mode.equals("activation-owner-loss"))c.backend.managedResumeHook=handoff::close;
                if(mode.equals("activation-flow-owner-loss"))c.backend.managedFlowHook=handoff::close;
                product.activate(promoted,handoff);waitFor(()->handoff.activationResult()!=null,c.host,true);
                boolean failure=!resume.equals("normal")||!flow.equals("normal")||mode.equals("activation-owner-loss");
                if(failure){
                    waitFor(()->handoff.flowFailure()!=null&&promoted.ended(),c.host,true);
                    MdoBrowserCdpSession.ControlResult result=handoff.flowFailure();check(!result.ok&&result.code.matches("cdp_[a-z_]{1,80}")&&c.backend.resumes.get()==1&&c.backend.continues.get()==(resume.equals("normal")&&!mode.equals("activation-owner-loss")?1:0));
                    String expected=resume.equals("unsupported")?"cdp_method_unsupported":resume.equals("rejected")||flow.equals("rejected")?"cdp_command_rejected":resume.equals("nonempty")||flow.equals("nonempty")?"cdp_attach_control_reply_invalid":resume.equals("mismatch")?"cdp_reply_mismatch":flow.equals("foreign")?"cdp_session_mismatch":resume.equals("timeout")||flow.equals("timeout")?"cdp_command_timeout":resume.equals("send-failure")||flow.equals("send-failure")?"cdp_transport_failed":flow.equals("limit")?"cdp_attach_request_limit":"cdp_owner_changed";
                    check(result.code.equals(expected)&&result.effectMayHaveOccurred!=resume.equals("unsupported"));
                    check(handoff.activationResult().ok==(!flow.equals("normal"))&&view.destroyed==1&&!handoff.current()&&c.backend.closes.get()==1&&c.backend.disables.get()==0);
                    c.backend.wire.add(ack(999,"proof-real"));Handler.drain();check(handoff.flowFailure()==result&&!handoff.current());
                }else{
                    waitFor(()->handoff.continuedRequests()==1&&candidate.retainedRequestCount()==0&&candidate.retainedResponseCount()==1,c.host,true);
                    check(handoff.activationResult().ok&&handoff.activationResult().effectMayHaveOccurred&&handoff.flowFailure()==null&&handoff.current()&&view.destroyed==0&&c.backend.resumes.get()==1&&c.backend.continues.get()==1);
                    failure("cdp_popup_activation_replayed",()->product.activate(promoted,handoff));
                    c.backend.wire.add(handoffPaused("managed-next",false));waitFor(()->handoff.continuedRequests()==2&&candidate.retainedResponseCount()==2,c.host,true);
                    check(candidate.retainedRequestCount()==0&&c.backend.continuedIds.toString().equals("[initial-0, managed-next]")&&handoff.current());
                    if(mode.equals("activation-expiry")){c.host.time.addAndGet(15000);waitFor(()->promoted.ended(),c.host,true);check(c.backend.disables.get()==0);}
                    else{c.backend.wire.add(detached("proof-real"));waitFor(()->promoted.ended()&&c.backend.closes.get()==1,c.host,true);check(c.backend.disables.get()==1);}
                    check(view.destroyed==1&&!handoff.current()&&c.backend.closes.get()==1);
                }
                check(c.backend.opens.get()==opens&&c.backend.nativeOpens.get()==proofs&&c.backend.guards.get()==1&&c.backend.detaches.get()==0&&view.evaluations==evaluations);
                check(MdoBrowserCdpPopupNative.promotedUiOwners()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0);
            }
        }
    }
    private static void managedDocument(String mode)throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);product.cdpViews.worker=c.host.worker;
            MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView view=created.view();c.source.adopting=view;
            MdoBrowserPopups.Adoption adoption=c.source.source.adopt(created);c.source.ack();
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());MdoBrowserCdpPopupNative.Child child=c.source.source.child(adoption,created,adapter)){
                c.backend.nativeTargets.put("target-proof-real",proofPeer(view,"success",null,null));check(c.request.proveNative(child));waitFor(()->!c.observer.provenChildren.isEmpty(),c.host,true);
                MdoBrowserCdpPopupBroker.Handoff handoff=child.handoff();MdoBrowserCdpPopupBroker.Candidate candidate=c.observer.candidates.get(0);
                MdoBrowserCdpPopupNative.Promotion promoted=product.handoff("owned",c.source.source,created,child);c.request.close();terminal(c.host,c.request,c.observer);
                c.backend.managedResumeMode="normal";product.activate(promoted,handoff);waitFor(()->handoff.continuedRequests()==1&&candidate.retainedResponseCount()==1,c.host,true);
                MdoBrowserCdpMarker marker=product.documentMarker("owned",promoted,handoff);MdoBrowserCdpPopupNative.Document proof=promoted.document(marker);
                check(proof.current()&&proof.matches(promoted)&&marker.lease().attached());failure("cdp_popup_document_owner_invalid",()->promoted.document(marker));
                try(MdoBrowserCdpView foreign=new MdoBrowserCdpView(c.source.view,v->true)){
                    failure("cdp_popup_document_owner_invalid",()->promoted.document(foreign.marker()));check(!foreign.marker().lease().attached());
                }
                for(java.lang.reflect.Field field:MdoBrowserCdpPopupNative.Document.class.getDeclaredFields())check(field.getType()==MdoBrowserCdpPopupNative.Promotion.class||field.getType()==MdoBrowserCdpMarker.Lease.class||field.getType()==Object.class);
                Backend.DocumentRenderer renderer=new Backend.DocumentRenderer(view);c.backend.documentRenderer=renderer;
                if(mode.equals("document-rebind")||mode.equals("document-navigation"))renderer.frames.omitChild=true;
                if(mode.equals("document-unique"))renderer.worlds.ignoreUnique=true;
                if(mode.equals("document-metadata"))renderer.worlds.missingUnique=true;
                int evaluations=view.evaluations,opens=c.backend.opens.get(),nativeOpens=c.backend.nativeOpens.get();
                java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> result=new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicReference<JSONObject> data=new java.util.concurrent.atomic.AtomicReference<>();
                MdoBrowserCdpWorker.Completion complete=value->{check(c.host.ui.onThread());if(value.bytes!=null)try{data.set(new JSONObject(new String(value.bytes,StandardCharsets.UTF_8)));}catch(Exception error){throw new AssertionError(error);}result.set(value);};
                if(mode.equals("document-no-fallback")){
                    c.host.worker.submit(marker,3000,false,(binding,deadline)->new byte[]{1},complete);waitFor(()->result.get()!=null,c.host,true);
                    check(result.get().code.equals("cdp_attached_binding_required")&&view.evaluations==evaluations&&handoff.current());result.set(null);
                }
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                if(mode.equals("document-navigation"))renderer.worlds.evaluation=(context,expression)->{entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new IOException("cdp_fixture_wait_expired");return new JSONObject().put("ok",true);};
                if(mode.equals("document-owner-loss"))renderer.worlds.evaluation=(context,expression)->{handoff.close();return new JSONObject().put("ok",true);};
                MdoBrowserCdpWorker.Operation operation=(binding,deadline)->{
                    JSONObject inventory=binding.frames().inventory(deadline);
                    if(mode.equals("document-evaluate")||mode.equals("document-navigation")||mode.equals("document-owner-loss")){
                        JSONObject root=inventory.optJSONArray("frames").optJSONObject(0);return binding.frames().evaluate(root.optString("id"),root.optString("document"),"({ok:true,text:'managed'})",false,deadline).toString().getBytes(StandardCharsets.UTF_8);
                    }if(mode.equals("document-scope"))binding.call("Page.getFrameTree",new JSONObject(),"proof-real",1000,false);return inventory.toString().getBytes(StandardCharsets.UTF_8);
                };
                c.host.worker.submitDocument(marker,handoff,proof,3000,false,operation,complete);
                if(mode.equals("document-navigation")){
                    try{waitFor(()->entered.getCount()==0,c.host,true);check(c.source.views.navigation("owned",view));product.cdpViews.invalidate("owned",view,c.source.views.epoch("owned",view),"frame_changed");}
                    finally{release.countDown();}
                }
                waitFor(()->result.get()!=null,c.host,true);
                if(mode.equals("document-unique")||mode.equals("document-metadata")){
                    check(result.get().code.equals(mode.equals("document-unique")?"cdp_world_unique_context_unsupported":"cdp_world_context_unsupported")&&!renderer.worlds.seenPageCode&&handoff.current()&&c.backend.closes.get()==0&&!marker.lease().busy());
                }else if(mode.equals("document-scope"))check(result.get().code.equals("cdp_popup_document_session_invalid")&&data.get()==null&&handoff.current()&&c.backend.closes.get()==0);
                else if(mode.equals("document-navigation"))check(result.get().code.equals("cdp_worker_cancelled")&&data.get()==null&&handoff.current()&&c.backend.closes.get()==0);
                else if(mode.equals("document-owner-loss")){waitFor(()->promoted.ended(),c.host,true);check(result.get().code!=null&&data.get()==null&&!handoff.current()&&view.destroyed==1);}
                else{
                    if(result.get().code!=null)throw new AssertionError("Managed document result "+result.get().code);check(data.get()!=null&&handoff.current()&&c.backend.closes.get()==0);
                    if(mode.equals("document-evaluate"))check(data.get().getBoolean("ok")&&renderer.worlds.evaluations==1);
                    else{check(data.get().getBoolean("complete"));org.json.JSONArray frames=data.get().getJSONArray("frames");check(frames.length()==(renderer.frames.omitChild?2:5));for(int i=0;i<frames.length();i++)check(frames.getJSONObject(i).getBoolean("isolated"));check(!data.get().toString().contains("child-session")&&!data.get().toString().contains("frame-owned"));}
                }
                if(mode.equals("document-reuse")){
                    JSONObject root=data.get().getJSONArray("frames").getJSONObject(0);String frame=root.getString("id"),document=root.getString("document");result.set(null);data.set(null);
                    c.host.worker.submitDocument(marker,handoff,proof,3000,false,(binding,deadline)->binding.frames().evaluate(frame,document,"({ok:true,text:'managed'})",false,deadline).toString().getBytes(StandardCharsets.UTF_8),complete);
                    waitFor(()->result.get()!=null,c.host,true);check(result.get().code==null&&renderer.worlds.evaluations==1&&renderer.resets==0&&handoff.current());
                }
                if(mode.equals("document-rebind")||mode.equals("document-rebind-frames")||mode.equals("document-navigation")){
                    if(!mode.equals("document-navigation"))product.cdpViews.invalidate("owned",view,c.source.views.epoch("owned",view),"permission_changed");
                    MdoBrowserCdpMarker next=product.documentMarker("owned",promoted,handoff);MdoBrowserCdpPopupNative.Document nextProof=promoted.document(next);check(next!=marker&&!proof.current()&&nextProof.current());result.set(null);data.set(null);
                    c.host.worker.submitDocument(next,handoff,nextProof,3000,false,(binding,deadline)->binding.frames().inventory(deadline).toString().getBytes(StandardCharsets.UTF_8),complete);
                    waitFor(()->result.get()!=null,c.host,true);check(result.get().code==null&&renderer.resets==1&&handoff.current()&&c.backend.closes.get()==0&&next.lease().busy());
                }
                check(c.backend.opens.get()==opens&&c.backend.nativeOpens.get()==nativeOpens&&view.evaluations==evaluations+(mode.equals("document-rebind")||mode.equals("document-rebind-frames")||mode.equals("document-navigation")?2:1)&&candidate.retainedResponseCount()==(promoted.ended()?0:1));
                if(handoff.current()){handoff.close();waitFor(promoted::ended,c.host,true);}check(view.destroyed==1&&c.backend.closes.get()==1&&c.backend.detaches.get()==0);
                renderer.wire.close();check(MdoBrowserCdpPopupNative.promotedUiOwners()==0);
            }
        }
    }
    private interface IoWork {void run()throws IOException;}
    private static void refused(String code,IoWork work)throws IOException{try{work.run();throw new AssertionError("Expected "+code);}catch(IOException error){check(code.equals(error.getMessage())&&error.getCause()==null);}}
    private static JSONObject responsePaused(String id,String mode)throws Exception{
        JSONObject params=CdpBodyProbe.paused(id).put("frameId",mode.equals("response-frame")?"foreign-frame":"frame-owned");
        if(mode.equals("transfer-frame-epoch")||mode.equals("transfer-ancestor")||mode.equals("transfer-world-local")||mode.equals("transfer-world-ancestor"))params.put("frameId","root-local");
        if(mode.equals("response-pass")||mode.equals("response-arguments")||mode.equals("transfer-pass")||mode.equals("source-pass"))params.put("responseHeaders",new org.json.JSONArray().put(new JSONObject().put("name","Content-Type").put("value","text/html")));
        else if(mode.startsWith("transfer"))params.getJSONArray("responseHeaders").put(new JSONObject().put("name","Content-Length").put("value","3"));
        return new JSONObject().put("method","Fetch.requestPaused").put("sessionId","proof-real").put("params",params);
    }
    private static void managedResponse(String mode,String directory)throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);product.cdpViews.worker=c.host.worker;
            MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView view=created.view();c.source.adopting=view;
            MdoBrowserPopups.Adoption adoption=c.source.source.adopt(created);c.source.ack();
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());MdoBrowserCdpPopupNative.Child child=c.source.source.child(adoption,created,adapter)){
                c.backend.nativeTargets.put("target-proof-real",proofPeer(view,"success",null,null));check(c.request.proveNative(child));waitFor(()->!c.observer.provenChildren.isEmpty(),c.host,true);
                MdoBrowserCdpPopupBroker.Handoff handoff=child.handoff();MdoBrowserCdpPopupBroker.Candidate candidate=c.observer.candidates.get(0);
                MdoBrowserCdpPopupNative.Promotion promoted=product.handoff("owned",c.source.source,created,child);c.request.close();terminal(c.host,c.request,c.observer);
                c.backend.responseMode=mode;c.backend.managedResumeMode="normal";product.activate(promoted,handoff);waitFor(()->candidate.retainedResponseCount()==1,c.host,true);
                MdoBrowserCdpMarker marker=product.documentMarker("owned",promoted,handoff);MdoBrowserCdpPopupNative.Document proof=promoted.document(marker);
                Backend.DocumentRenderer renderer=new Backend.DocumentRenderer(view);renderer.frames.omitChild=true;c.backend.documentRenderer=renderer;
                int opens=c.backend.opens.get(),nativeOpens=c.backend.nativeOpens.get();
                java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> result=new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpSession.AttachedResponse> response=new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpSession.Offer> offer=new java.util.concurrent.atomic.AtomicReference<>();
                MdoBrowserCdpWorker.Completion complete=value->{check(c.host.ui.onThread());result.set(value);};
                c.host.worker.submitDocument(marker,handoff,proof,3000,true,(binding,deadline)->{
                    refused("cdp_popup_response_admission_required",()->binding.nextAttachedResponse());
                    binding.frames().inventory(deadline);
                    if(mode.equals("response-frame")){refused("cdp_popup_response_frame_unproven",()->binding.nextAttachedResponse());return new byte[]{1};}
                    MdoBrowserCdpSession.AttachedResponse row=binding.nextAttachedResponse();response.set(row);check(row!=null&&binding.nextAttachedResponse()==null&&row.result()==null);
                    check(row.metadata().frame.equals("frame-owned")&&candidate.retainedResponseCount()==1&&c.backend.bodyClaims.get()==0&&c.backend.responsePasses.get()==0);
                    if(mode.equals("response-pass")||mode.equals("response-arguments")){
                        check(row.metadata().kind.equals("pass"));refused("cdp_body_not_candidate",()->row.offer());
                        if(mode.equals("response-arguments"))refused("cdp_arguments_invalid",()->row.pass(1));row.pass(1000);
                        check(row.result().ok&&candidate.retainedResponseCount()==0&&binding.nextAttachedResponse()==null);
                        refused("cdp_attach_response_unavailable",()->row.pass(1000));refused("cdp_attach_response_unavailable",()->row.abort(1000));
                    }else if(mode.equals("response-abort")){row.abort(1000);check(row.result().ok&&candidate.retainedResponseCount()==0);}
                    else if(mode.equals("response-document-loss")){
                        marker.lease().invalidate();refused("cdp_attach_response_admission_required",()->row.offer());refused("cdp_attach_response_admission_required",()->row.pass(1000));row.abort(1000);check(row.result().ok);
                    }else{
                        check(row.metadata().kind.equals("candidate"));refused("cdp_attach_response_not_passable",()->row.pass(1000));
                        MdoBrowserCdpSession.Offer next=row.offer();offer.set(next);check(candidate.retainedResponseCount()==0&&next.result()==null);
                        refused("cdp_attach_response_unavailable",()->row.offer());refused("cdp_offer_arguments_invalid",()->next.accept(0,3000));
                    }return new byte[]{1};
                },complete);
                waitFor(()->result.get()!=null,c.host,true);
                if(mode.equals("response-document-loss"))check(result.get().code.equals("cdp_worker_cancelled")&&handoff.current());
                else if(result.get().code!=null)throw new AssertionError("Response result "+result.get().code);
                if(offer.get()!=null&&mode.equals("response-offer-cancel")){
                    offer.get().cancel();waitFor(()->offer.get().result()!=null,c.host,true);check(!offer.get().result().accepted&&offer.get().result().requestAbortConfirmed&&offer.get().result().body==null&&c.backend.bodyClaims.get()==0&&c.backend.responseAborts.get()==1);
                }else if(offer.get()!=null){
                    MdoBrowserCdpSession.Offer next=offer.get();next.accept(65536,3000);waitFor(()->next.result()!=null,c.host,true);check(next.result().accepted&&next.result().body!=null);
                    MdoBrowserCdpSession.Body body=next.result().body;check(body.ready()&&c.backend.bodyClaims.get()==1);MdoBrowserCdpSession.AttachedResponse row=response.get();
                    if(mode.equals("response-body-cancel")||mode.equals("response-late-foreign"))c.backend.holdRead=true;
                    result.set(null);c.host.worker.submitDocument(marker,handoff,proof,3000,false,(binding,deadline)->{refused("cdp_body_owner_mismatch",()->row.requestRead(null));row.requestRead(body);return new byte[]{1};},complete);
                    waitFor(()->result.get()!=null&&c.backend.bodyReads.get()==1,c.host,true);check(result.get().code==null);
                    if(c.backend.holdRead){
                        result.set(null);c.host.worker.submitDocument(marker,handoff,proof,3000,false,(binding,deadline)->{row.cancel(body);return new byte[]{1};},complete);
                        waitFor(()->result.get()!=null&&body.result()!=null,c.host,true);check(result.get().code==null&&!body.result().ok&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed&&body.result().bytes==0);
                        JSONObject late=ack(c.backend.lateRead.get(),mode.equals("response-late-foreign")?"foreign":"proof-real");late.put("result",new JSONObject().put("data","unread-late-bytes").put("eof",true));c.backend.wire.add(late);
                        if(mode.equals("response-late-foreign")){waitFor(promoted::ended,c.host,true);check(!handoff.current()&&c.backend.closes.get()==1);}
                        else{int before=c.backend.receives.get();waitFor(()->c.backend.receives.get()>before+2,c.host,true);check(handoff.current()&&c.backend.closes.get()==0);}
                    }else{
                        java.nio.file.Path root=java.nio.file.Paths.get(directory);java.nio.file.Files.createDirectories(root);
                        try(MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->new MdoBrowserDownloadFiles(root),c.host.time::get)){
                            MdoBrowserDownloadQueue.Ticket ticket=queue.begin(body);waitFor(()->{try{return queue.writable(ticket);}catch(IOException error){throw new AssertionError(error);}},c.host,true);
                            result.set(null);c.host.worker.submitDocument(marker,handoff,proof,3000,false,(binding,deadline)->{
                                MdoBrowserCdpSession.BodyChunk chunk=row.takeChunk(body);check(chunk!=null&&chunk.eof&&java.util.Arrays.equals(chunk.data,new byte[]{0,1,(byte)255}));check(queue.offer(ticket,chunk));return new byte[]{1};},complete);
                            waitFor(()->result.get()!=null&&ticket.snapshot().completed(),c.host,true);check(result.get().code==null&&ticket.snapshot().bytes==3&&ticket.snapshot().source==body.result());
                            check(body.result().eof&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed&&body.result().bytes==3&&ticket.snapshot().sha256.equals("26a66b061e8f48f39927c312f25293959729eee95978e2892d49d3512a5cc092"));
                            MdoBrowserDownloadQueue.Read read=queue.read(ticket,0,10);waitFor(read::finished,c.host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);
                            queue.release(ticket);waitFor(()->queue.retainedTickets()==0,c.host,true);queue.close();check(queue.awaitStopped(3000)&&!queue.cleanupFailed()&&queue.reservations()==0&&queue.retainedReads()==0);
                        }finally{java.nio.file.Files.deleteIfExists(root.resolve(".mdo-browser-download-owner-v1"));java.nio.file.Files.delete(root);}
                    }
                    check(c.backend.bodyCloses.get()==1&&c.backend.responseAborts.get()==1);
                }else if(mode.equals("response-pass")||mode.equals("response-arguments")){check(c.backend.responsePasses.get()==1&&c.backend.responseAborts.get()==0);c.host.time.addAndGet(16000);Thread.sleep(50);check(handoff.current());}
                else if(mode.equals("response-frame"))check(candidate.retainedResponseCount()==1&&c.backend.responsePasses.get()==0&&c.backend.responseAborts.get()==0);
                else check(c.backend.responseAborts.get()==1&&c.backend.responsePasses.get()==0);
                check(c.backend.opens.get()==opens&&c.backend.nativeOpens.get()==nativeOpens&&c.backend.continues.get()==1);
                if(handoff.current()){handoff.close();waitFor(promoted::ended,c.host,true);}check(view.destroyed==1&&c.backend.closes.get()==1&&c.backend.detaches.get()==0);renderer.wire.close();
            }
        }
    }
    private static void managedTransfers(String mode,String directory)throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);product.cdpViews.worker=c.host.worker;
            MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView view=created.view();c.source.adopting=view;
            MdoBrowserPopups.Adoption adoption=c.source.source.adopt(created);c.source.ack();
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());MdoBrowserCdpPopupNative.Child child=c.source.source.child(adoption,created,adapter)){
                c.backend.nativeTargets.put("target-proof-real",proofPeer(view,"success",null,null));check(c.request.proveNative(child));waitFor(()->!c.observer.provenChildren.isEmpty(),c.host,true);
                MdoBrowserCdpPopupBroker.Handoff handoff=child.handoff();MdoBrowserCdpPopupBroker.Candidate candidate=c.observer.candidates.get(0);
                MdoBrowserCdpPopupNative.Promotion promoted=product.handoff("owned",c.source.source,created,child);c.request.close();terminal(c.host,c.request,c.observer);
                c.backend.responseMode=mode;c.backend.managedResumeMode="normal";product.activate(promoted,handoff);waitFor(()->candidate.retainedResponseCount()==1,c.host,true);
                MdoBrowserCdpMarker marker=product.documentMarker("owned",promoted,handoff);MdoBrowserCdpPopupNative.Document proof=promoted.document(marker);
                Backend.DocumentRenderer renderer=new Backend.DocumentRenderer(view);renderer.frames.omitChild=true;c.backend.documentRenderer=renderer;
                int opens=c.backend.opens.get(),nativeOpens=c.backend.nativeOpens.get();java.nio.file.Path root=java.nio.file.Paths.get(directory);java.nio.file.Files.createDirectories(root);
                MdoBrowserCdpDownloads.Budget budget=new MdoBrowserCdpDownloads.Budget();
                if(mode.equals("transfer-budget"))for(int i=0;i<32;i++)check(budget.acquire());
                Throwable primary=null;
                try(MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->new MdoBrowserDownloadFiles(root),c.host.time::get);MdoBrowserCdpDownloads downloads=new MdoBrowserCdpDownloads(queue,budget)){
                    MdoBrowserCdpDownloads.Connection connection=downloads.connection();java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> result=new java.util.concurrent.atomic.AtomicReference<>();
                    java.lang.reflect.Field gateField=MdoBrowserCdpDownloads.class.getDeclaredField("gate");gateField.setAccessible(true);Object controllerGate=gateField.get(downloads);c.backend.responseIo=()->check(!Thread.holdsLock(controllerGate));
                    MdoBrowserCdpWorker.Completion complete=value->{check(c.host.ui.onThread());result.set(value);};
                    c.host.worker.submitDocument(marker,handoff,proof,3000,false,(binding,deadline)->{
                        refused("cdp_popup_response_admission_required",()->binding.attachDownloads(connection));binding.frames().inventory(deadline);binding.attachDownloads(connection);binding.attachDownloads(connection);return new byte[]{1};
                    },complete);waitFor(()->result.get()!=null,c.host,true);if(result.get().code!=null)throw new AssertionError("Transfer bind "+result.get().code);
                    if(mode.equals("transfer-pending-limit")){
                        for(int i=0;i<8;i++)c.backend.wire.add(responsePaused("response-extra-"+i,mode));waitFor(()->c.backend.responseAborts.get()==1,c.host,true);
                        check(downloads.list().length==8&&budget.used()==8&&downloads.failure().equals("download_pending_limit")&&c.backend.bodyClaims.get()==0&&candidate.retainedResponseCount()==0);
                        MdoBrowserCdpDownloads.Snapshot[] rows=downloads.list();for(MdoBrowserCdpDownloads.Snapshot row:rows)downloads.cancel(row.id);
                        waitFor(()->c.backend.responseAborts.get()==9,c.host,true);
                        for(MdoBrowserCdpDownloads.Snapshot row:rows){waitFor(()->{try{return downloads.get(row.id).terminal();}catch(IOException error){throw new AssertionError(error);}},c.host,true);check(downloads.get(row.id).sourceCleanupConfirmed);MdoBrowserCdpDownloads.Release release=downloads.release(row.id);waitFor(release::finished,c.host,true);check(release.released());}
                        check(downloads.list().length==0&&budget.used()==0&&queue.retainedTickets()==0);
                    }else if(mode.equals("transfer-pass")||mode.equals("transfer-budget")){
                        waitFor(()->c.backend.responsePasses.get()+c.backend.responseAborts.get()==1,c.host,true);check(downloads.list().length==0&&candidate.retainedResponseCount()==0&&c.backend.bodyClaims.get()==0);
                        if(mode.equals("transfer-pass"))check(c.backend.responsePasses.get()==1&&budget.used()==0);
                        else{check(downloads.failure().equals("download_receipt_limit")&&budget.used()==32);for(int i=0;i<32;i++)budget.release();}
                    }else{
                        waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},c.host,true);
                        MdoBrowserCdpDownloads.Snapshot pending=downloads.list()[0];String id=pending.id;
                        java.lang.reflect.Field rowsField=MdoBrowserCdpDownloads.class.getDeclaredField("rows");rowsField.setAccessible(true);Object retained=((Object[])rowsField.get(downloads))[0];
                        java.lang.reflect.Field responseField=retained.getClass().getDeclaredField("response");responseField.setAccessible(true);Object frozen=responseField.get(retained);java.lang.reflect.Field admission=frozen.getClass().getDeclaredField("admission");admission.setAccessible(true);check(admission.get(frozen)==null);
                        java.lang.reflect.Field originField=retained.getClass().getDeclaredField("origin");originField.setAccessible(true);Object origin=originField.get(retained);java.lang.reflect.Field ownerField=origin.getClass().getDeclaredField("documentOwner");ownerField.setAccessible(true);check(ownerField.get(origin) instanceof java.lang.ref.WeakReference&&((java.lang.ref.WeakReference<?>)ownerField.get(origin)).get()==marker.lease());
                        check(pending.state.equals("pending")&&pending.expected==3&&pending.frameCurrent&&pending.root!=java.util.Arrays.asList("transfer-frame-epoch","transfer-ancestor","transfer-world-local","transfer-world-ancestor").contains(mode)&&(pending.root?pending.sourceFrame.equals("f0"):pending.sourceFrame.startsWith("f-"))&&pending.sourceDocument.startsWith("fd-")&&pending.sourceRequest==null&&pending.sourceEpochKind.equals("response_frame")&&budget.used()==1);
                        String metadata=pending.json().toString();check(!metadata.contains("private.invalid")&&!metadata.contains("proof-real")&&!metadata.contains("frame-owned")&&!metadata.contains("initial-0"));
                        check(c.backend.bodyClaims.get()==0&&c.backend.bodyReads.get()==0&&queue.retainedTickets()==0);
                        if(mode.equals("transfer-other-controller")){
                            try(MdoBrowserCdpDownloads other=new MdoBrowserCdpDownloads(queue,budget)){
                                result.set(null);c.host.worker.submitDocument(marker,handoff,proof,3000,false,(binding,deadline)->{refused("cdp_controller_busy",()->binding.attachDownloads(other.connection()));return new byte[]{1};},complete);
                                waitFor(()->result.get()!=null,c.host,true);check(result.get().code==null&&other.list().length==0&&downloads.list().length==1&&budget.used()==1);
                            }
                        }
                        boolean cancel=mode.equals("transfer-cancel")||mode.equals("transfer-other-controller"),limit=mode.equals("transfer-limit");
                        boolean pendingEpoch=mode.equals("transfer-pending-epoch"),frameEpoch=mode.equals("transfer-frame-epoch")||mode.equals("transfer-ancestor");
                        boolean world=mode.startsWith("transfer-world-"),pendingWorld=mode.equals("transfer-world-pending"),unrelated=mode.equals("transfer-world-unrelated");
                        if(mode.equals("transfer-retired-binding")){
                            result.set(null);c.host.worker.submitDocument(marker,handoff,proof,3000,false,(binding,deadline)->{marker.lease().invalidate();check(!connection.attachedTurn(handoff,binding,100));return new byte[]{1};},complete);
                            waitFor(()->result.get()!=null,c.host,true);check(result.get().code.equals("cdp_worker_cancelled")&&handoff.current());pendingEpoch=true;
                        }
                        if(pendingWorld)renderer.loseWorld("frame-owned",c.backend.wire);
                        else if(pendingEpoch){marker.lease().invalidate();failure("download_frame_changed",()->downloads.accept(id,65536,3000));}
                        else if(cancel)downloads.cancel(id);else if(limit)downloads.accept(id,2,3000);
                        else{
                            c.backend.holdRead=mode.equals("transfer-epoch")||mode.equals("transfer-detach")||frameEpoch||world;downloads.accept(id,65536,3000);
                            if(c.backend.holdRead){waitFor(()->c.backend.lateRead.get()>0,c.host,true);
                                if(world){if(unrelated){c.backend.wire.add(new JSONObject().put("method","Runtime.executionContextDestroyed").put("sessionId","proof-real").put("params",new JSONObject().put("executionContextId",10000)));c.backend.wire.add(ack(c.backend.lateRead.get(),"proof-real").put("result",new JSONObject().put("data","AAH/").put("base64Encoded",true).put("eof",true)));}
                                    else{int received=c.backend.receives.get();renderer.loseWorld(mode.equals("transfer-world-local")?"root-local":"frame-owned",c.backend.wire);waitFor(()->c.backend.receives.get()>received+2,c.host,true);if(downloads.get(id).frameCurrent)throw new AssertionError("Destroyed isolated world left original download epoch current");}}
                                else if(mode.equals("transfer-epoch"))marker.lease().invalidate();else if(frameEpoch)c.backend.wire.add(new JSONObject().put("method","Page.frameStartedLoading").put("sessionId","proof-real").put("params",new JSONObject().put("frameId",mode.equals("transfer-ancestor")?"frame-owned":"root-local")));else c.backend.wire.add(detached("proof-real"));
                            }
                        }
                        waitFor(()->{try{return downloads.get(id).terminal();}catch(IOException error){throw new AssertionError(error);}},c.host,true);MdoBrowserCdpDownloads.Snapshot done=downloads.get(id);
                        check(responseField.get(retained)==null);
                        if(cancel||limit||mode.equals("transfer-epoch")||pendingEpoch||frameEpoch||world&&!unrelated){
                            check(!done.verified&&done.sourceCleanupConfirmed&&done.cacheCleanupConfirmed&&c.backend.responseAborts.get()==1);
                            if(mode.equals("transfer-epoch")||frameEpoch||world&&!pendingWorld){check(!done.frameCurrent&&done.error.equals("download_frame_changed")&&c.backend.bodyCloses.get()==1&&handoff.current());
                                c.backend.wire.add(ack(c.backend.lateRead.get(),"proof-real").put("result",new JSONObject().put("data","unread-late-bytes").put("eof",true)));int before=c.backend.receives.get();waitFor(()->c.backend.receives.get()>before+2,c.host,true);check(handoff.current());
                            }else check(c.backend.bodyClaims.get()==0&&queue.retainedTickets()==0&&done.error.equals(pendingEpoch||pendingWorld?"download_frame_changed":limit?"download_limit":"cancelled"));
                        }else if(mode.equals("transfer-detach")){waitFor(promoted::ended,c.host,true);check(!done.verified&&!done.sourceCleanupConfirmed&&!handoff.current()&&c.backend.closes.get()==1);}
                        else{
                            check(done.state.equals("completed")&&done.verified&&done.sourceCleanupConfirmed&&done.received==3&&done.sha256.equals("26a66b061e8f48f39927c312f25293959729eee95978e2892d49d3512a5cc092"));
                            if(mode.equals("transfer-close-retain")){handoff.close();waitFor(promoted::ended,c.host,true);check(downloads.get(id).verified&&downloads.get(id).sourceCleanupConfirmed&&!downloads.get(id).frameCurrent);}
                            MdoBrowserDownloadQueue.Read read=downloads.read(id,0,10);waitFor(read::finished,c.host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);
                            if(mode.equals("transfer-rebind")){
                                marker.lease().invalidate();MdoBrowserCdpMarker next=product.documentMarker("owned",promoted,handoff);MdoBrowserCdpPopupNative.Document nextProof=promoted.document(next);result.set(null);
                                c.host.worker.submitDocument(next,handoff,nextProof,3000,false,(binding,deadline)->{binding.frames().inventory(deadline);binding.attachDownloads(connection);return new byte[]{1};},complete);
                                waitFor(()->result.get()!=null,c.host,true);check(result.get().code==null&&renderer.resets==1&&downloads.get(id).verified&&!downloads.get(id).frameCurrent);
                                c.backend.wire.add(responsePaused("response-extra",mode));waitFor(()->{try{return downloads.list().length==2;}catch(IOException error){throw new AssertionError(error);}},c.host,true);
                                MdoBrowserCdpDownloads.Snapshot second=downloads.list()[1];check(second.state.equals("pending")&&second.frameCurrent&&!second.sourceDocument.equals(done.sourceDocument)&&budget.used()==2);
                                downloads.cancel(second.id);waitFor(()->{try{return downloads.get(second.id).terminal();}catch(IOException error){throw new AssertionError(error);}},c.host,true);
                                MdoBrowserCdpDownloads.Release released=downloads.release(second.id);waitFor(released::finished,c.host,true);check(released.released()&&budget.used()==1&&downloads.get(id).verified);
                            }
                        }
                        if(mode.equals("transfer-world-recover")){
                            c.backend.holdRead=false;result.set(null);c.host.worker.submitDocument(marker,handoff,proof,3000,false,(binding,deadline)->{binding.frames().inventory(deadline);binding.attachDownloads(connection);return new byte[]{1};},complete);
                            waitFor(()->result.get()!=null,c.host,true);check(result.get().code==null&&!downloads.get(id).frameCurrent&&handoff.current());
                            c.backend.responseStream="original-child-stream-recovered";c.backend.wire.add(responsePaused("response-extra-recovered",mode));waitFor(()->{try{return downloads.list().length==2;}catch(IOException error){throw new AssertionError(error);}},c.host,true);
                            MdoBrowserCdpDownloads.Snapshot next=downloads.list()[1];check(next.frameCurrent&&!next.sourceDocument.equals(done.sourceDocument)&&!downloads.get(id).frameCurrent);downloads.accept(next.id,65536,3000);
                            waitFor(()->{try{return downloads.get(next.id).terminal();}catch(IOException error){throw new AssertionError(error);}},c.host,true);check(downloads.get(next.id).verified&&downloads.get(next.id).sourceCleanupConfirmed&&!downloads.get(id).verified&&!downloads.get(id).frameCurrent);
                            MdoBrowserCdpDownloads.Release release=downloads.release(next.id);waitFor(release::finished,c.host,true);check(release.released()&&budget.used()==1);
                        }
                        if(!mode.equals("transfer-detach")){MdoBrowserCdpDownloads.Release release=downloads.release(id);waitFor(release::finished,c.host,true);check(release.released()&&budget.used()==0&&downloads.list().length==0);}
                    }
                    check(c.backend.opens.get()==opens&&c.backend.nativeOpens.get()==nativeOpens&&c.backend.continues.get()==1);
                    if(handoff.current()){handoff.close();waitFor(promoted::ended,c.host,true);}check(view.destroyed==1&&c.backend.closes.get()==1&&c.backend.detaches.get()==0);
                    downloads.close();queue.close();check(queue.awaitStopped(3000)&&!queue.cleanupFailed()&&queue.reservations()==0&&queue.retainedReads()==0&&budget.used()==0);
                }catch(Exception|Error error){primary=error;throw error;}
                finally{try{java.nio.file.Files.deleteIfExists(root.resolve(".mdo-browser-download-owner-v1"));java.nio.file.Files.delete(root);}
                    catch(Exception cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}finally{renderer.wire.close();}}
            }
        }
    }
    private static void transportQuiescence(String mode)throws Exception{
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context one=c.source(),two=c.source();c.networkTargets.put(two.adapter.marker(),"other-root");c.ready(one);c.ready(two);
            try(NativeSource a=new NativeSource(c,one);NativeSource b=new NativeSource(c,two)){
                Observer oa=new Observer(c),ob=new Observer(c);MdoBrowserCdpPopupWorker.Request ra=c.worker.prepareNativePopups(a.source,1500,oa);ready(c,ra,oa);
                MdoBrowserCdpPopupWorker.Request rb=c.worker.prepareNativePopups(b.source,1500,ob);ready(c,rb,ob);a.open();b.open();
                boolean sent=mode.equals("quiescence-sent"),physicalFailure=mode.equals("quiescence-physical-failure");
                PopupProductHost product=new PopupProductHost(a.views);WebView child=null;
                if(sent||physicalFailure){
                    backend.wire.add(attached("held-native","owned"));waitFor(()->oa.candidates.size()==1,c,true);check(oa.candidates.get(0).requestProtection());waitFor(()->oa.protectedTargets.size()==1,c,true);
                    MdoBrowserCdpPopupNative.Created created=product.construct(a.source,ra.allocateNative(a.source),"owned");child=created.view();
                    if(sent){a.adopting=child;a.source.adopt(created);}else product.failClose=true;
                }
                backend.closeFailure=mode.equals("quiescence-close-failure");
                if(mode.equals("quiescence-reentrant"))backend.closeHook=()->{check(c.ui.onThread()&&a.budget.used()==0&&b.budget.used()==0);MdoBrowserCdpPopupNative.reconcileRetirements();c.worker.close();};
                boolean shutdown=mode.equals("quiescence-shutdown"),lost=mode.equals("quiescence-ui-loss");
                if(shutdown||lost){c.ui.reject=lost;c.worker.close();}else{
                    JSONObject invalid=attached("invalid-global","owned");invalid.getJSONObject("params").getJSONObject("targetInfo").put("type","worker");backend.wire.add(invalid);
                }
                waitFor(()->ra.result().terminal&&rb.result().terminal,c,false);
                check(backend.closes.get()==0&&a.sends==(sent?1:0)&&b.sends==0);
                if(lost){waitFor(c.worker::terminated,c,false);c.ui.reject=false;product.resume();}
                if(mode.equals("quiescence-timeout"))c.time.addAndGet(600000);
                c.ui.drain();check(a.sends==1&&b.sends==1&&backend.closes.get()==0&&a.budget.used()==1&&b.budget.used()==1);
                a.ack();check(backend.closes.get()==0&&b.budget.used()==1);b.ack();
                check(backend.closes.get()==(physicalFailure?0:1)&&a.budget.used()==0&&b.budget.used()==0);
                if(child!=null)check(child.destroyed==(physicalFailure?0:1));
                a.ack();b.ack();MdoBrowserCdpPopupNative.reconcileRetirements();
                check(backend.closes.get()==(physicalFailure?0:1)&&MdoBrowserCdpPopupNative.retainedUiOwners()==(physicalFailure?1:0));
                check(MdoBrowserCdpPopupNative.creationConfirmed()==!(physicalFailure||backend.closeFailure));
                check(!ra.result().sourceReleased&&!rb.result().sourceReleased&&!ra.result().processCleanupConfirmed&&!rb.result().processCleanupConfirmed&&backend.resumes.get()==0&&backend.detaches.get()==0);
            }
        }
    }
    private static void deliveryLoss(String mode)throws Exception{
        if(mode.equals("delivery-loss-scope")){
            Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
                CdpNetworkBindingProbe.Context network=c.source();c.ready(network);try(NativeSource source=new NativeSource(c,network)){
                    Observer observer=new Observer(c);MdoBrowserCdpPopupWorker.Request request=c.worker.prepareNativePopups(source.source,1500,observer);ready(c,request,observer);
                    backend.wire.add(attached("old-action-candidate","owned"));waitFor(()->observer.candidates.size()==1,c,true);
                    check(observer.candidates.get(0).requestProtection());waitFor(()->observer.protectedTargets.size()==1,c,true);
                    CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                    c.worker.submit(c.document(),10000,false,(binding,deadline)->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("fixture_wait_expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("fixture_interrupted");}return new byte[]{1};},c.documentResults::add);
                    try{
                        waitFor(()->entered.getCount()==0,c,true);source.source.abandon();source.source.claim(network.adapter.marker().lease());source.source.retainUiOwner();check(source.source.ready());source.open();
                        MdoBrowserPopups.Pending fresh=source.source.pending();check(fresh!=null&&fresh.current());
                        failure("cdp_popup_native_allocation_unavailable",()->request.allocateNative(source.source));
                        release.countDown();terminal(c,request,observer);new PopupProductHost(source.views).resume();
                        check(source.source.current()&&source.source.pending()==fresh&&fresh.current()&&source.sends==0&&source.budget.used()==1);
                        check(!request.result().sourceReleased&&!request.result().processCleanupConfirmed&&request.result().code.equals("cdp_popup_native_scope_changed"));
                        source.source.reject();check(source.sends==1&&source.budget.used()==1);source.ack();source.source.finish(true);
                        check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&source.budget.used()==0&&MdoBrowserCdpPopupNative.creationConfirmed());
                    }finally{release.countDown();}
                }
            }return;
        }
        if(mode.equals("delivery-loss-sent")||mode.equals("delivery-loss-unsent"))try(OwnershipCase c=new OwnershipCase()){
            boolean sent=mode.equals("delivery-loss-sent");PopupProductHost product=new PopupProductHost(c.source.views);
            MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView child=created.view();
            if(sent){c.source.adopting=child;c.source.source.adopt(created);}
            c.host.ui.reject=true;c.host.worker.close();waitFor(c.host.worker::terminated,c.host,false);
            check(c.request.result().terminal&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed&&MdoBrowserCdpPopupNative.pendingRetirements()==1);
            c.host.ui.reject=false;c.host.ui.drain();product.resume();
            check(c.host.worker.popupPending()==0&&MdoBrowserCdpPopupNative.pendingRetirements()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==1&&c.source.budget.used()==1);
            check(child.destroyed==(sent?0:1)&&c.source.sends==1);
            c.source.ack();check(child.destroyed==1&&product.retired==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&c.source.budget.used()==0);
            c.source.ack();product.resume();check(child.destroyed==1&&c.source.sends==1&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed&&c.backend.resumes.get()==0&&c.backend.detaches.get()==0);return;
        }
        if(mode.equals("delivery-loss-multiple")){
            Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
                CdpNetworkBindingProbe.Context one=c.source(),two=c.source();c.networkTargets.put(two.adapter.marker(),"other-root");c.ready(one);c.ready(two);
                try(NativeSource a=new NativeSource(c,one);NativeSource b=new NativeSource(c,two)){
                    Observer oa=new Observer(c),ob=new Observer(c);MdoBrowserCdpPopupWorker.Request ra=c.worker.prepareNativePopups(a.source,1500,oa);ready(c,ra,oa);
                    MdoBrowserCdpPopupWorker.Request rb=c.worker.prepareNativePopups(b.source,1500,ob);ready(c,rb,ob);a.open();b.open();
                    c.ui.reject=true;c.worker.close();waitFor(c.worker::terminated,c,false);check(MdoBrowserCdpPopupNative.pendingRetirements()==2&&MdoBrowserCdpPopupNative.retainedUiOwners()==2);
                    c.ui.reject=false;c.ui.drain();new PopupProductHost(a.views).resume();
                    check(a.sends==1&&b.sends==1&&a.budget.used()==1&&b.budget.used()==1&&c.worker.popupPending()==0&&MdoBrowserCdpPopupNative.pendingRetirements()==0);
                    a.ack();check(MdoBrowserCdpPopupNative.retainedUiOwners()==1&&b.budget.used()==1);b.ack();
                    check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&a.budget.used()==0&&b.budget.used()==0&&oa.states.size()==1&&ob.states.size()==1);
                    check(!ra.result().sourceReleased&&!rb.result().sourceReleased&&!ra.result().processCleanupConfirmed&&!rb.result().processCleanupConfirmed);
                }
            }return;
        }
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case c=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=c.source();c.ready(network);try(NativeSource source=new NativeSource(c,network)){
                PopupProductHost product=new PopupProductHost(source.views);Observer observer=new Observer(c);
                boolean retry=mode.equals("delivery-loss-retry"),queued=mode.equals("delivery-loss-queued");
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                if(queued){c.worker.submit(c.document(),10000,false,(binding,deadline)->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}return new byte[]{1};},result->{});waitFor(()->entered.getCount()==0,c,true);}
                if(retry)c.ui.rejectNext.set(1);else c.ui.reject=true;
                MdoBrowserCdpPopupWorker.Request request;
                try{request=c.worker.prepareNativePopups(source.source,1500,observer);if(queued)c.worker.close();release.countDown();waitFor(c.worker::terminated,c,retry);}
                finally{release.countDown();}
                check(request.result()!=null&&request.result().terminal&&observer.states.isEmpty());
                if(!retry)check(MdoBrowserCdpPopupNative.pendingRetirements()==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==1);
                c.ui.reject=false;c.ui.drain();if(mode.equals("delivery-loss-create"))product.checkCreation();else product.resume();
                check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&source.sends==0&&source.budget.used()==0&&c.worker.popupPending()==0);
                check(request.result().sourceReleased==queued&&request.result().processCleanupConfirmed==queued);
            }
        }
    }
    private static void productDisposal(String mode)throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);
            if(mode.equals("product-disposal-configuration")){
                product.failConfiguration=true;
                try{product.construct(c.source.source,c.allocation,"owned");throw new AssertionError("Product configuration fault was lost");}
                catch(IllegalStateException expected){check(expected.getMessage().equals("controlled_product_configuration_failure"));}
                check(WebView.lastConstructed.destroyed==1&&product.retired==1&&product.retiring()==0&&!product.failed()&&MdoBrowserCdpPopupNative.creationConfirmed());
                c.cancel(false);check(c.request.result().sourceReleased&&c.request.result().processCleanupConfirmed);return;
            }
            MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView child=created.view();
            check(product.configured==1&&!product.failed()&&child.destroyed==0);
            boolean sent=mode.equals("product-disposal-sent")||mode.equals("product-disposal-consumed");
            if(sent){c.source.adopting=child;c.source.source.adopt(created);if(mode.endsWith("-consumed"))c.source.ack();}
            if(mode.equals("product-disposal-network"))product.networkReady=false;
            if(mode.equals("product-disposal-failure")||mode.equals("product-disposal-logical-failure")){
                boolean logical=mode.equals("product-disposal-logical-failure");int attempts=logical?0:1;
                product.failClose=logical;child.destroyFailure=!logical;failure("cdp_popup_native_child_cleanup_unconfirmed",created::close);
                check(child.destroyed==attempts&&product.failed()&&product.retired==0&&!c.source.views.nativeDestroyed(child)&&!MdoBrowserCdpPopupNative.creationConfirmed());
                product.failClose=false;child.destroyFailure=false;failure("cdp_popup_native_child_cleanup_unconfirmed",created::close);check(child.destroyed==attempts);
                c.request.close();c.source.ack();terminal(c.host,c.request,c.observer);
                check(!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed&&child.destroyed==attempts&&c.backend.resumes.get()==0&&c.backend.detaches.get()==0);return;
            }
            if(mode.equals("product-disposal-nested")){
                MdoBrowserCdpView adapter=MdoBrowserCdpView.network(child,v->v==child,new MdoBrowserCdpMarker.DocumentQuota());
                MdoBrowserPopups<WebView> core=new MdoBrowserPopups<>(child,new MdoBrowserPopups.Scheduler(){public long now(){return c.host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}},new MdoBrowserPopups.Budget(8),()->true,()->1L,blocked->{});
                MdoBrowserCdpPopupNative nested=new MdoBrowserCdpPopupNative(adapter,child,core);nested.claim(adapter.marker().lease());nested.retainUiOwner();check(nested.ready());
                final Runnable[] callback={null};check(core.open(new MdoBrowserPopups.Transport<WebView>(){public Object token(){return this;}public void send(WebView exact,Runnable ack){check(exact==null&&callback[0]==null);callback[0]=ack;}},false));
                product.windows.put("owned",core);created.close();
                check(callback[0]!=null&&child.destroyed==0&&product.retiring()==1&&!product.failed()&&MdoBrowserCdpPopupNative.creationConfirmed());
                created.close();check(child.destroyed==0&&product.root.removes==0);callback[0].run();callback[0].run();nested.finish(false);core.close();core.viewDestroyed(child);
                check(child.destroyed==1&&product.retired==1&&product.retiring()==0&&!product.failed()&&MdoBrowserCdpPopupNative.creationConfirmed());
                c.cancel(false);check(c.request.result().sourceReleased&&c.request.result().processCleanupConfirmed&&MdoBrowserCdpPopupNative.retainedUiOwners()==0);return;
            }
            boolean reentrant=mode.equals("product-disposal-reentrant");
            if(reentrant)product.root.callback=()->{
                try{created.close();}catch(IOException error){throw new AssertionError(error);}check(!product.retire("owned",child));
                c.source.source.reject();c.source.ack();check(!c.source.source.confirmed());
            };
            created.close();
            if(mode.equals("product-disposal-sent")){check(child.destroyed==0&&product.root.removes==0);c.source.ack();}
            check(child.destroyed==1&&product.retired==1&&product.retiring()==0&&!product.failed()&&MdoBrowserCdpPopupNative.creationConfirmed());
            created.close();check(child.destroyed==1&&product.root.removes==1&&product.cdpViews.invalidations==1);
            if(sent){c.request.close();c.backend.wire.add(detached("proof-real"));terminal(c.host,c.request,c.observer);}
            else c.cancel(reentrant);
            check(c.request.result().sourceReleased&&c.request.result().processCleanupConfirmed&&c.backend.resumes.get()==0&&c.backend.detaches.get()==0);
        }
    }
    private static void hostDestruction(String mode)throws Exception{
        check(MdoBrowserCdpPopupNative.retainedUiOwners()==0);
        boolean human=mode.contains("-human"),failure=mode.endsWith("-failure"),nested=mode.contains("-nested");
        final int kind=mode.endsWith("-unsent")||failure?0:mode.endsWith("-inline")?2:1;
        try(OwnershipCase c=new OwnershipCase()){
            final boolean sent=kind!=0;final int[] fallback={0};
            MdoBrowserCdpPopupNative.Created created=c.source.source.construct(c.allocation,c.source.views,"owned",(key,view)->{fallback[0]++;c.source.dispose(key,view);});
            WebView child=created.view();MdoBrowserPopups.Adoption adoption=null;
            if(sent){c.source.adopting=child;adoption=c.source.source.adopt(created);}
            if(human){c.source.views.manual("owned",child,true);created.close();check(child.destroyed==0);}
            final Runnable[] nestedAck={null};final int[] nestedSends={0};
            MdoBrowserCdpPopupNative nestedSource=null;MdoBrowserPopups<WebView> nestedCore=null;
            if(nested){
                MdoBrowserCdpView adapter=MdoBrowserCdpView.network(child,v->v==child,new MdoBrowserCdpMarker.DocumentQuota());
                nestedCore=new MdoBrowserPopups<>(child,new MdoBrowserPopups.Scheduler(){public long now(){return c.host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}},new MdoBrowserPopups.Budget(8),()->true,()->1L,blocked->{});
                nestedSource=new MdoBrowserCdpPopupNative(adapter,child,nestedCore);nestedSource.claim(adapter.marker().lease());nestedSource.retainUiOwner();check(nestedSource.ready());
                check(nestedCore.open(new MdoBrowserPopups.Transport<WebView>(){
                    public Object token(){return this;}public void send(WebView exact,Runnable ack){check(exact==null);nestedSends[0]++;nestedAck[0]=ack;}
                },false));check(MdoBrowserCdpPopupNative.retainedUiOwners()==2);
            }
            class Close implements MdoBrowserCdpPopupNative.Destruction {
                final String key;final WebView exact;boolean prepared,running,done;int calls;
                Close(String key,WebView exact){this.key=key;this.exact=exact;}
                public boolean ready(){return prepared&&!running;}
                public void destroy(){
                    if(done||running)return;running=true;try{
                        calls++;if(failure)throw new IllegalStateException("Controlled host destruction failure");
                        check(prepared);if(exact==child&&sent)check(c.source.sends==1);c.source.dispose(key,exact);MdoBrowserCdpPopupNative.viewDestroyed(exact);
                        if(exact==c.source.view)c.source.core.viewDestroyed(exact);done=true;
                    }finally{running=false;if(done)try{MdoBrowserCdpPopupNative.activateDestruction(exact,this);}catch(IOException error){throw new AssertionError(error);}}
                }
            }
            Close root=new Close("root",c.source.view),row=new Close("owned",child);
            check(MdoBrowserCdpPopupNative.registerDestruction(child,row)&&MdoBrowserCdpPopupNative.registerDestruction(c.source.view,root));
            check(!c.source.source.current()&&child.destroyed==0&&c.source.view.destroyed==0&&fallback[0]==0);
            MdoBrowserCdpPopupNative.activateDestruction(child,row);MdoBrowserCdpPopupNative.activateDestruction(c.source.view,root);
            check(child.destroyed==0&&c.source.view.destroyed==0&&c.source.sends==1);
            if(kind==2){c.source.ack();check(child.destroyed==0&&c.source.view.destroyed==0);}
            row.prepared=true;root.prepared=true;
            if(failure){
                failure("cdp_popup_native_child_cleanup_unconfirmed",()->MdoBrowserCdpPopupNative.activateDestruction(child,row));
                check(row.calls==1&&child.destroyed==0&&!MdoBrowserCdpPopupNative.creationConfirmed());
                failure("cdp_popup_native_child_cleanup_unconfirmed",()->MdoBrowserCdpPopupNative.activateDestruction(child,row));check(row.calls==1);
                c.source.ack();c.source.source.lease().invalidate();c.request.close();terminal(c.host,c.request,c.observer);
                check(!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed&&fallback[0]==0&&child.destroyed==0);return;
            }
            MdoBrowserCdpPopupNative.activateDestruction(child,row);MdoBrowserCdpPopupNative.activateDestruction(c.source.view,root);
            if(nested){
                check(nestedSends[0]==1&&nestedAck[0]!=null&&child.destroyed==0&&c.source.view.destroyed==0);
                if(mode.endsWith("-parent")){c.source.ack();check(child.destroyed==0&&c.source.view.destroyed==1);nestedAck[0].run();}
                else{nestedAck[0].run();check(child.destroyed==0&&c.source.view.destroyed==0);c.source.ack();}
                nestedAck[0].run();nestedSource.finish(false);nestedCore.close();nestedCore.viewDestroyed(child);
            }else if(kind!=2){check(c.source.view.destroyed==0&&child.destroyed==(sent?0:1));c.source.ack();}
            check(child.destroyed==1&&c.source.view.destroyed==1&&row.calls==1&&root.calls==1&&fallback[0]==0);
            if(adoption!=null)check(adoption.nativeConsumed()&&!adoption.ok());
            created.close();c.source.ack();MdoBrowserCdpPopupNative.activateDestruction(child,row);MdoBrowserCdpPopupNative.activateDestruction(c.source.view,root);
            check(child.destroyed==1&&c.source.view.destroyed==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&c.source.budget.used()==0);
            c.source.source.lease().invalidate();c.request.close();terminal(c.host,c.request,c.observer);check(!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
            check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0);
        }
    }
    private static void ownerLifetime(boolean sent)throws Exception{
        check(MdoBrowserCdpPopupNative.retainedUiOwners()==0);
        try(OwnershipCase c=new OwnershipCase()){
            check(MdoBrowserCdpPopupNative.retainedUiOwners()==1);
            MdoBrowserCdpPopupNative.Created created=c.source.source.construct(c.allocation,c.source.views,"owned",c.source::dispose);
            WebView child=created.view();MdoBrowserPopups.Adoption adoption=null;
            if(sent){c.source.adopting=child;adoption=c.source.source.adopt(created);}
            c.source.core.close();c.source.views.remove("root");c.source.view.destroy();c.source.views.viewDestroyed(c.source.view);
            MdoBrowserCdpPopupNative.viewDestroyed(c.source.view);c.source.core.viewDestroyed(c.source.view);c.source.core.viewDestroyed(c.source.view);
            check(c.source.sends==1&&c.source.budget.used()==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==1);
            check(c.source.view.destroyed==1&&child.destroyed==(sent?0:1));
            c.source.source.lease().invalidate();c.request.close();terminal(c.host,c.request,c.observer);
            check(!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed&&MdoBrowserCdpPopupNative.retainedUiOwners()==1);
            c.source.ack();check(c.source.budget.used()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&child.destroyed==1);
            if(adoption!=null)check(adoption.nativeConsumed()&&!adoption.ok());
            c.source.ack();created.close();check(child.destroyed==1&&c.source.view.destroyed==1&&!c.request.result().sourceReleased&&!c.request.result().processCleanupConfirmed);
            check(c.backend.resumes.get()==0&&c.backend.detaches.get()==0);
        }
        check(MdoBrowserCdpPopupNative.retainedUiOwners()==0);
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
    private static void managedSource(String mode,String directory)throws Exception {
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);product.cdpViews.worker=c.host.worker;
            MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView view=created.view();c.source.adopting=view;
            MdoBrowserPopups.Adoption adoption=c.source.source.adopt(created);c.source.ack();
            try(MdoBrowserCdpView nativeMarker=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());MdoBrowserCdpPopupNative.Child child=c.source.source.child(adoption,created,nativeMarker)){
                c.backend.nativeTargets.put("target-proof-real",proofPeer(view,"success",null,null));check(c.request.proveNative(child));waitFor(()->!c.observer.provenChildren.isEmpty(),c.host,true);
                MdoBrowserCdpPopupBroker.Handoff handoff=child.handoff();MdoBrowserCdpPopupBroker.Candidate candidate=c.observer.candidates.get(0);
                MdoBrowserCdpPopupNative.Promotion promoted=product.handoff("owned",c.source.source,created,child);c.request.close();terminal(c.host,c.request,c.observer);
                int opens=c.backend.opens.get(),nativeOpens=c.backend.nativeOpens.get();c.backend.managedResumeMode="normal";c.backend.responseMode=mode;
                if(mode.equals("source-activated")){product.activate(promoted,handoff);waitFor(()->candidate.retainedResponseCount()==1,c.host,true);}
                try(MdoBrowserCdpView adapter=product.sourceAdapter("owned",promoted,handoff)){
                    MdoBrowserCdpMarker marker=adapter.marker();MdoBrowserCdpPopupNative.Source source=promoted.source(marker);
                    check(source.current()&&handoff.owns(source));failure("cdp_popup_source_owner_invalid",()->promoted.source(marker));
                    MdoBrowserCdpMarker document=product.documentMarker("owned",promoted,handoff);MdoBrowserCdpPopupNative.Document doc=promoted.document(document);
                    failure("cdp_popup_source_owner_invalid",()->promoted.source(document));check(doc.current());
                    try(MdoBrowserCdpView foreign=MdoBrowserCdpView.network(new WebView(new android.content.Context()),v->true,new MdoBrowserCdpMarker.DocumentQuota())){failure("cdp_popup_source_owner_invalid",()->promoted.source(foreign.marker()));}
                    java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> result=new java.util.concurrent.atomic.AtomicReference<>();
                    MdoBrowserCdpWorker.Completion complete=value->{check(c.host.ui.onThread());result.set(value);};
                    failure("cdp_network_scope_invalid",()->c.host.worker.submit(marker,3000,false,(binding,deadline)->new byte[]{1},complete));
                    c.backend.sourceRenderer=new Backend.SourceRenderer(mode);
                    java.nio.file.Path root=java.nio.file.Paths.get(directory);java.nio.file.Files.createDirectories(root);MdoBrowserCdpDownloads.Budget budget=new MdoBrowserCdpDownloads.Budget();
                    try(MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->new MdoBrowserDownloadFiles(root),c.host.time::get);MdoBrowserCdpDownloads downloads=new MdoBrowserCdpDownloads(queue,budget)){
                        MdoBrowserCdpDownloads.Connection connection=downloads.connection();
                        c.host.worker.submitSource(marker,handoff,source,3000,(binding,deadline)->{
                            int before=c.backend.sourceRenderer.calls;refused("cdp_network_scope_invalid",()->binding.frames());refused("cdp_network_scope_invalid",()->binding.call("Runtime.evaluate",new JSONObject(),null,1000,true));
                            check(c.backend.sourceRenderer.calls==before&&binding.next()==null);binding.attachDownloads(connection);binding.attachDownloads(connection);return new byte[]{1};
                        },complete);waitFor(()->result.get()!=null,c.host,true);
                        boolean rejected=mode.equals("source-nonempty")||mode.equals("source-changed")||mode.equals("source-unsupported")||mode.equals("source-activated");
                        if(rejected){String expected=mode.equals("source-nonempty")?"cdp_popup_source_reply_invalid":mode.equals("source-changed")?"cdp_worker_failed":mode.equals("source-unsupported")?"cdp_method_unsupported":"cdp_popup_source_activation_started";
                            if(!expected.equals(result.get().code))throw new AssertionError("Source rejection "+result.get().code);check(!result.get().effectMayHaveOccurred);
                            if(!mode.equals("source-activated")){waitFor(promoted::ended,c.host,true);check(c.backend.resumes.get()==0&&c.backend.continues.get()==0);}
                            check(c.backend.bodyClaims.get()==0&&downloads.list().length==0);
                        }else{
                            if(result.get().code!=null)throw new AssertionError("Source bind "+result.get().code);check(c.backend.sourceRenderer.calls==(mode.equals("source-race")?4:3)&&c.backend.resumes.get()==0&&c.backend.continues.get()==0);
                            document.lease().invalidate();check(!doc.current()&&source.current());
                            if(mode.equals("source-replay"))try(MdoBrowserCdpView second=product.sourceAdapter("owned",promoted,handoff)){
                                MdoBrowserCdpPopupNative.Source replay=promoted.source(second.marker());int before=c.backend.sourceRenderer.calls;result.set(null);
                                c.host.worker.submitSource(second.marker(),handoff,replay,3000,(binding,deadline)->{throw new AssertionError("Duplicate source entered");},complete);waitFor(()->result.get()!=null,c.host,true);check(result.get().code.equals("cdp_controller_busy")&&source.current()&&c.backend.sourceRenderer.calls==before);
                            }
                            if(mode.equals("source-controller")){
                                try(MdoBrowserCdpDownloads other=new MdoBrowserCdpDownloads(queue,budget)){result.set(null);c.host.worker.submitSource(marker,handoff,source,3000,(binding,deadline)->{refused("cdp_controller_busy",()->binding.attachDownloads(other.connection()));return new byte[]{1};},complete);waitFor(()->result.get()!=null,c.host,true);check(result.get().code==null);}
                            }
                            product.activate(promoted,handoff);
                            if(mode.equals("source-pass")){waitFor(()->c.backend.responsePasses.get()==1,c.host,true);check(downloads.list().length==0&&c.backend.bodyClaims.get()==0);}
                            else{
                                waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},c.host,true);
                                MdoBrowserCdpDownloads.Snapshot pending=downloads.list()[0];String id=pending.id;boolean captured=mode.equals("source-captured-epoch");check((captured?!pending.frameCurrent:pending.state.equals("pending")&&pending.frameCurrent)&&pending.sourceFrame.startsWith("nf-")&&pending.sourceDocument.startsWith("nd-")&&pending.sourceEpochKind.equals("response_frame")&&c.backend.bodyClaims.get()==0&&queue.retainedTickets()==0);
                                if(captured){/* Original epoch is already retired before the controller selected the response. */}
                                else if(mode.equals("source-epoch")){
                                    c.backend.wire.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId","proof-real").put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","load-replaced"))));
                                    waitFor(()->{try{return downloads.get(id).terminal();}catch(IOException error){throw new AssertionError(error);}},c.host,true);check(downloads.accept(id,65536,3000).error.equals("download_frame_changed"));
                                }else if(mode.equals("source-cancel")||mode.equals("source-controller"))downloads.cancel(id);
                                else if(mode.startsWith("source-stream-")){
                                    c.backend.holdRead=true;downloads.accept(id,65536,3000);waitFor(()->c.backend.lateRead.get()>0,c.host,true);
                                    if(mode.equals("source-stream-epoch"))c.backend.wire.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId","proof-real").put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","load-replaced"))));
                                    else if(mode.equals("source-stream-cancel"))downloads.cancel(id);else marker.lease().invalidate();
                                }else downloads.accept(id,65536,3000);
                                waitFor(()->{try{return downloads.get(id).terminal();}catch(IOException error){throw new AssertionError(error);}},c.host,true);MdoBrowserCdpDownloads.Snapshot done=downloads.get(id);
                                if(mode.startsWith("source-stream-")){
                                    boolean ownerLost=mode.equals("source-stream-owner");check(!done.verified&&done.cacheCleanupConfirmed&&c.backend.bodyClaims.get()==1&&c.backend.bodyReads.get()==1);
                                    if(ownerLost){waitFor(promoted::ended,c.host,true);check(!done.sourceCleanupConfirmed&&!handoff.current());failure("download_source_cleanup_unconfirmed",()->downloads.release(id));}
                                    else{check(done.sourceCleanupConfirmed&&c.backend.bodyCloses.get()==1&&c.backend.responseAborts.get()==1&&handoff.current());
                                        if(mode.equals("source-stream-epoch"))check(!done.frameCurrent&&java.util.Arrays.asList("download_frame_changed","cdp_body_owner_changed").contains(done.error));else check(done.frameCurrent&&done.error.equals("cancelled"));
                                        c.backend.wire.add(ack(c.backend.lateRead.get(),"proof-real").put("result",new JSONObject().put("data","unread-late-bytes").put("eof",true)));int received=c.backend.receives.get();waitFor(()->c.backend.receives.get()>received+2,c.host,true);check(handoff.current());}
                                }else if(captured||mode.equals("source-epoch")||mode.equals("source-cancel")||mode.equals("source-controller"))check(!done.verified&&done.sourceCleanupConfirmed&&done.cacheCleanupConfirmed&&c.backend.bodyClaims.get()==0&&c.backend.responseAborts.get()==1);
                                else{check(done.verified&&done.sourceCleanupConfirmed&&done.received==3);MdoBrowserDownloadQueue.Read read=downloads.read(id,0,10);waitFor(read::finished,c.host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);}
                                if(!mode.equals("source-stream-owner")){MdoBrowserCdpDownloads.Release released=downloads.release(id);waitFor(released::finished,c.host,true);check(released.released()&&budget.used()==0);}
                            }
                            check(c.backend.documentRenderer==null&&candidate.retainedResponseCount()==0&&handoff.current()!=mode.equals("source-stream-owner"));
                        }
                        check(c.backend.opens.get()==opens&&c.backend.nativeOpens.get()==nativeOpens&&c.backend.detaches.get()==0);
                        if(handoff.current()){handoff.close();waitFor(promoted::ended,c.host,true);}check(view.destroyed==1&&c.backend.closes.get()==1);
                        downloads.close();queue.close();check(queue.awaitStopped(3000)&&!queue.cleanupFailed()&&queue.reservations()==0&&budget.used()==0);
                    }finally{java.nio.file.Files.deleteIfExists(root.resolve(".mdo-browser-download-owner-v1"));java.nio.file.Files.delete(root);}
                }
            }
        }
    }
    private static void preparedAdmission(String mode)throws Exception {
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=host.source();host.ready(network);try(NativeSource source=new NativeSource(host,network)){
                PopupProductHost product=new PopupProductHost(source.views);product.bindPreparation(source.view,network.adapter,source.core,host.worker);
                AtomicInteger effects=new AtomicInteger();product.prepare(effects::incrementAndGet,(code,effect)->{throw new AssertionError("Preparation failed "+code);});
                waitFor(()->effects.get()==1,host,true);check(product.preparationReady());
                MdoBrowserCdpPopupNative original=product.preparationOriginal("source",MdoBrowserCdpPopupNative.class);
                MdoBrowserCdpPopupWorker.Request request=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class);
                source.open();backend.wire.add(attached("proof-real","owned"));
                java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpPopupWorker.Allocation> captured=new java.util.concurrent.atomic.AtomicReference<>();
                waitFor(()->{try{captured.set(request.allocateNative(original));return true;}catch(IOException waiting){
                    if(!java.util.Arrays.asList("cdp_popup_native_capture_pending","cdp_popup_interception_required").contains(waiting.getMessage()))throw new AssertionError(waiting);return false;}},host,true);
                try{product.checkCreation();throw new AssertionError("Unrelated construction bypassed active preparation");}catch(IllegalStateException expected){check(expected.getMessage().equals("native_popup_protection_busy"));}
                int before=WebView.constructions;MdoBrowserCdpPopupNative.Created created=product.construct(original,captured.get(),"owned");WebView view=created.view();
                check(WebView.constructions==before+1&&product.preparationReady());source.adopting=view;
                MdoBrowserPopups.Adoption adoption=original.adopt(created);source.ack();check(adoption.ok()&&adoption.nativeConsumed()&&source.sends==1);
                try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                    MdoBrowserCdpPopupNative.Child child=original.child(adoption,created,adapter)){
                    backend.nativeTargets.put("target-proof-real",proofPeer(view,"success",null,null));check(request.proveNative(child));
                    waitFor(()->child.proof()!=null,host,true);check(child.proof().ok&&child.handoff()!=null);
                    if(mode.equals("prepared-admission-view"))product.nativeViewCleanupFailed=true;
                    if(mode.equals("prepared-admission-network"))product.networkCleanupFailed=true;
                    if(mode.equals("prepared-admission-retiring"))product.nativeRetiringViews.add(source.view);
                    if(!mode.equals("prepared-admission")&&!mode.equals("prepared-admission-mismatch")){
                        try{product.handoff("owned",original,created,child);throw new AssertionError("Cleanup fence was bypassed");}
                        catch(IllegalStateException expected){check(expected.getMessage().equals(mode.endsWith("view")?"native_view_cleanup_failed":mode.endsWith("network")?"network_cleanup_failed":"native_view_retirement_busy"));}
                        check(!child.handoff().transferred()&&view.destroyed==0&&source.sends==1);
                        product.nativeViewCleanupFailed=false;product.networkCleanupFailed=false;product.nativeRetiringViews.clear(); // controlled receipt peers only
                    }
                    if(mode.equals("prepared-admission-mismatch")){
                        failure("cdp_popup_handoff_unavailable",()->product.handoff("owned",source.source,created,child));
                        check(!child.handoff().transferred()&&product.preparationReady());
                    }
                    MdoBrowserCdpPopupBroker.Handoff handoff=child.handoff();MdoBrowserCdpPopupNative.Promotion promoted=product.handoff("owned",original,created,child);
                    check(promoted.current()&&handoff.current()&&product.preparationReady()&&source.sends==1&&WebView.constructions==before+1);
                    try{product.checkCreation();throw new AssertionError("Handoff removed unrelated creation fence");}catch(IllegalStateException expected){check(expected.getMessage().equals("native_popup_protection_busy"));}
                    if(java.util.Arrays.asList("prepared-admission-view","prepared-admission-network","prepared-admission-retiring").contains(mode)){
                        if(mode.endsWith("view"))product.nativeViewCleanupFailed=true;
                        else if(mode.endsWith("network"))product.networkCleanupFailed=true;
                        else product.nativeRetiringViews.add(source.view);
                        String refusal=mode.endsWith("view")?"native_view_cleanup_failed":mode.endsWith("network")?"network_cleanup_failed":"native_view_retirement_busy";
                        try{product.activate(promoted,handoff);throw new AssertionError("Activation bypassed cleanup");}catch(IllegalStateException expected){check(expected.getMessage().equals(refusal));}
                        try{product.documentMarker("owned",promoted,handoff);throw new AssertionError("Document bypassed cleanup");}catch(IllegalStateException expected){check(expected.getMessage().equals(refusal));}
                        try{product.sourceAdapter("owned",promoted,handoff);throw new AssertionError("Source bypassed cleanup");}catch(IllegalStateException expected){check(expected.getMessage().equals(refusal));}
                        check(backend.resumes.get()==0&&handoff.current()&&promoted.current());
                        product.nativeViewCleanupFailed=false;product.networkCleanupFailed=false;product.nativeRetiringViews.clear(); // controlled receipt peers only
                    }
                    if(mode.equals("prepared-admission-mismatch")){
                        failure("cdp_popup_activation_unavailable",()->product.activate(promoted,null));
                        try{product.documentMarker("root",promoted,handoff);throw new AssertionError("Foreign document owner admitted");}catch(IllegalStateException expected){check(expected.getMessage().equals("cdp_popup_document_owner_invalid"));}
                        try{product.sourceAdapter("root",promoted,handoff);throw new AssertionError("Foreign physical owner admitted");}catch(IllegalStateException expected){check(expected.getMessage().equals("cdp_popup_source_owner_invalid"));}
                        check(handoff.activationResult()==null&&handoff.current()&&promoted.current()&&source.sends==1);
                    }
                    MdoBrowserCdpMarker document=product.documentMarker("owned",promoted,handoff);check(promoted.document(document).current());
                    try(MdoBrowserCdpView physical=product.sourceAdapter("owned",promoted,handoff)){check(promoted.source(physical.marker()).current());}
                    backend.managedResumeMode="normal";product.activate(promoted,handoff);waitFor(()->handoff.activationResult()!=null,host,true);check(handoff.activationResult().ok&&backend.resumes.get()==1);
                    product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);check(handoff.current()&&promoted.current());
                    handoff.close();waitFor(promoted::ended,host,true);check(view.destroyed==1&&source.sends==1&&MdoBrowserCdpPopupNative.promotedUiOwners()==0);product.checkCreation();
                    check(backend.opens.get()==1&&backend.closes.get()==1&&backend.detaches.get()==0);
                }finally{product.retirePreparation("cdp_popup_preparation_cancelled");}
            }
        }
    }
    private static void productPreparation(String mode)throws Exception {
        Backend backend=new Backend();try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=host.source();host.ready(network);try(NativeSource source=new NativeSource(host,network)){
                PopupProductHost product=new PopupProductHost(source.views);product.bindPreparation(source.view,network.adapter,source.core,host.worker);
                AtomicInteger effects=new AtomicInteger(),failures=new AtomicInteger();java.util.concurrent.atomic.AtomicReference<String> reason=new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicBoolean possible=new java.util.concurrent.atomic.AtomicBoolean();
                MdoBrowserCdpPopupPreparation.Failure failed=(code,effect)->{check(host.ui.onThread());failures.incrementAndGet();reason.set(code);possible.set(effect);};
                if(mode.equals("preparation-rpc-invalid")){
                    for(JSONObject request:new JSONObject[]{new JSONObject().put("navigation",1),new JSONObject().put("agent",true).put("navigation",0),new JSONObject().put("agent",true).put("navigation","1")}){
                        try{product.preparationRpc(request);throw new AssertionError("Invalid popup preparation admitted");}catch(IllegalArgumentException expected){check(expected.getMessage().equals("popup_preparation_scope_invalid"));}
                    }
                    failure("cdp_popup_preparation_required",product::requirePreparation);check(backend.creates.get()==0&&backend.opens.get()==0);return;
                }
                if(mode.equals("preparation-input-unready")){
                    product.inputRpc(new JSONObject().put("agent",true).put("navigation",1));
                    check(product.preparationReplies==1&&!product.preparationReply.getBoolean("accepted")&&!product.preparationReply.getBoolean("effect_may_have_occurred"));
                    check(product.preparationReply.getJSONObject("error").getString("code").equals("cdp_popup_preparation_required")&&backend.creates.get()==0&&backend.opens.get()==0);return;
                }
                boolean held=java.util.Arrays.asList("preparation-cancel","preparation-owner","preparation-channel","preparation-epoch","preparation-timeout").contains(mode);
                if(held){backend.armEntered=new CountDownLatch(1);backend.armRelease=new CountDownLatch(1);}
                backend.unsupportedArm=mode.equals("preparation-failure");
                Runnable effect=()->{check(host.ui.onThread());effects.incrementAndGet();
                    if(mode.equals("preparation-throw"))throw new IllegalStateException("synthetic effect outcome unavailable");
                    if(mode.equals("preparation-reentrant"))product.prepare(effects::incrementAndGet,failed);
                };
                if(mode.equals("preparation-rpc"))product.preparationRpc(new JSONObject().put("agent",true).put("navigation",1));else product.prepare(effect,failed);
                check(effects.get()==0); // The original UI has not delivered readiness.
                if(held){waitFor(()->backend.armEntered.getCount()==0,host,false);
                    if(mode.equals("preparation-cancel"))product.retirePreparation("cdp_popup_preparation_cancelled");
                    else if(mode.equals("preparation-owner"))product.browserPaused=true;
                    else if(mode.equals("preparation-channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                    else if(mode.equals("preparation-epoch"))check(source.views.navigation("root",source.view));
                    else host.time.addAndGet(4000);
                    backend.armRelease.countDown();
                }
                if(held||mode.equals("preparation-failure")||mode.equals("preparation-throw")){
                    waitFor(()->failures.get()==1,host,true);check(effects.get()==(mode.equals("preparation-throw")?1:0)&&possible.get()==mode.equals("preparation-throw"));
                    if(mode.equals("preparation-timeout")){waitFor(()->!product.preparationReady(),host,true);check(reason.get()!=null);}
                    else waitFor(product::preparationSettled,host,true);
                    int before=backend.receives.get();waitFor(()->backend.receives.get()>before+2||backend.closes.get()>0,host,true);check(failures.get()==1&&effects.get()==(mode.equals("preparation-throw")?1:0));
                }else{
                    if(mode.equals("preparation-rpc")){waitFor(()->product.preparationReplies==1,host,true);check(product.preparationReply.getBoolean("ok")&&product.preparationReply.getBoolean("ready")&&product.preparationReply.getInt("navigation")==1);}
                    else waitFor(()->effects.get()==1,host,true);
                    check(product.preparationReady());product.requirePreparation();
                    if(mode.equals("preparation-reentrant"))check(failures.get()==1&&reason.get().equals("cdp_popup_preparation_busy")&&!possible.get());else check(failures.get()==0);
                    try{product.checkCreation();throw new AssertionError("Uncoordinated native creation entered protected process");}catch(IllegalStateException expected){check(expected.getMessage().equals("native_popup_protection_busy"));}
                    if(mode.equals("preparation-input-invalid")||mode.equals("preparation-input-retired")){
                        boolean retired=mode.endsWith("retired");if(retired)product.retirePreparation("cdp_popup_preparation_cancelled");
                        int inputs=backend.nativeOpens.get();product.inputRpc(new JSONObject().put("agent",true).put("navigation",0));
                        check(product.preparationReplies==1&&!product.preparationReply.getBoolean("accepted")&&!product.preparationReply.getBoolean("effect_may_have_occurred"));
                        check(product.preparationReply.getJSONObject("error").getString("code").equals(retired?"cdp_popup_preparation_required":"cdp_input_frame_changed"));
                        check(backend.nativeOpens.get()==inputs&&product.preparationReply.getInt("delivered_steps")==0);
                    }
                    if(mode.startsWith("preparation-protection")){
                        backend.guardMode=mode.equals("preparation-protection-failure")?"unsupported":"inline";source.open();backend.wire.add(attached("preparation-child","owned"));waitFor(()->backend.guards.get()==1,host,true);
                        if(mode.equals("preparation-protection-failure")){waitFor(()->source.sends==1,host,true);check(!product.preparationReady());}
                        else{check(product.preparationReady());product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(()->source.sends==1,host,true);}
                        source.ack();backend.wire.add(detached("preparation-child"));
                    }else{
                        int before=backend.opens.get();if(!mode.equals("preparation-input-retired")){product.prepare(effects::incrementAndGet,failed);check(effects.get()==(mode.equals("preparation-rpc")?1:2)&&backend.opens.get()==before);}
                        product.retirePreparation("cdp_popup_preparation_cancelled");
                    }
                    waitFor(product::preparationSettled,host,true);check(backend.opens.get()==1&&backend.resumes.get()==0&&backend.detaches.get()==0);
                    if(mode.equals("preparation-protection-failure"))check(product.preparationCode().equals("cdp_popup_interception_unsupported"));
                    if(mode.equals("preparation-restart")){int before=effects.get();product.prepare(effects::incrementAndGet,failed);waitFor(()->effects.get()==before+1,host,true);check(backend.opens.get()==2);product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);}
                }
                check(backend.resumes.get()==0&&backend.detaches.get()==0&&backend.nativeOpens.get()==0);
                if(product.preparationReady())product.retirePreparation("cdp_popup_preparation_cancelled");
            }
        }
    }
    public static void main(String[] args)throws Exception{
        Looper.getMainLooper();System.loadLibrary("xs");String mode=args.length==0?"suite":args[0];
        if(mode.startsWith("prepared-admission"))preparedAdmission(mode);
        else if(mode.startsWith("preparation"))productPreparation(mode);
        else if(mode.startsWith("source"))managedSource(mode,args[1]);
        else if(mode.startsWith("transfer"))managedTransfers(mode,args[1]);
        else if(mode.startsWith("response"))managedResponse(mode,args[1]);
        else if(mode.startsWith("document"))managedDocument(mode);
        else if(mode.startsWith("activation"))managedActivation(mode);
        else if(mode.startsWith("handoff"))protectedHandoff(mode);
        else if(mode.startsWith("promotion"))nativePromotion(mode);
        else if(mode.startsWith("quiescence"))transportQuiescence(mode);
        else if(mode.startsWith("delivery-loss"))deliveryLoss(mode);
        else if(mode.startsWith("product-disposal"))productDisposal(mode);
        else if(mode.startsWith("host-destruction"))hostDestruction(mode);
        else if(mode.startsWith("owner-lifetime"))ownerLifetime(mode.equals("owner-lifetime-sent"));
        else if(mode.equals("admission-fence"))admissionFence();
        else if(mode.startsWith("retirement"))retirement(mode);
        else if(mode.startsWith("ownership"))ownership(mode);
        else if(mode.startsWith("allocation"))nativeAllocation(mode);
        else if(mode.equals("native-proof-post-birth"))nativeLateTarget();
        else if(mode.equals("native-proof-multiple"))nativeMultiple();
        else if(mode.startsWith("native-proof"))nativeProof(mode);
        else if(mode.equals("protection"))protectionSuite();
        else if(mode.startsWith("protection-"))protectionFailure(mode);
        else if(mode.equals("suite")){suite();cooperative();candidates();queued(false);queued(true);armingCancellation();callbackFailure();nativeJoin(false);nativeJoin(true);nativeTerminalBeforeUiClose();nativeOwnerChanged();}
        else if(mode.startsWith("native-")&&!mode.equals("native-cancel"))nativeFailure(mode);
        else poison(mode);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"mode\":\""+mode+"\",\"one_original_executor\":true,\"ui_delivery\":true,\"cooperative_popup_pump\":true,\"native_ui_promotion_tested\":"+mode.startsWith("promotion")+",\"protected_network_source_tested\":"+mode.startsWith("source")+",\"attached_download_controller_tested\":"+mode.startsWith("transfer")+",\"protected_response_routing_tested\":"+mode.startsWith("response")+",\"protected_document_binding_tested\":"+mode.startsWith("document")+",\"protected_attachment_activation_tested\":"+mode.startsWith("activation")+",\"protected_attachment_handoff_tested\":"+mode.startsWith("handoff")+",\"protocol_promotion_qualified\":false,\"transport_quiescence_tested\":"+mode.startsWith("quiescence")+",\"retirement_delivery_scope_tested\":"+mode.startsWith("delivery-loss")+",\"product_prepared_admission_tested\":"+mode.startsWith("prepared-admission")+",\"product_popup_preparation_tested\":"+mode.startsWith("preparation")+",\"product_disposer_join_tested\":"+mode.startsWith("product-disposal")+",\"protective_interception_scope_tested\":"+mode.startsWith("protection")+",\"retained_target_marker_scope_tested\":"+mode.startsWith("native-proof")+",\"native_popup_adoption_qualified\":false,\"provider_qualified\":false}");
    }
}
