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
import org.json.JSONException;

/** Real common executor/network/marker/dispatcher with controlled UI/renderer.
 * No extra product executor or installed-provider/popup adoption qualification. */
public final class CdpPopupWorkerProbe {
    private static JSONObject pageEvidence;
    private static JSONObject hostCloseEvidence;
    private static JSONObject loadingRetirementEvidence;
    private static JSONObject isolationEvidence,frameRouteEvidence,publicationEvidence,navigationEvidence;
    private static final AtomicInteger checks=new AtomicInteger();
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Popup worker check "+(checks.get()+1));checks.incrementAndGet();}
    private static void failure(String code,Work work)throws Exception{try{work.run();throw new AssertionError("Expected "+code);}catch(IOException error){check(code.equals(error.getMessage())&&error.getCause()==null);}}
    private static void waitFor(BooleanSupplier condition,CdpNetworkWorkerProbe.Case c,boolean deliver)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean()){
            if(System.nanoTime()>=end)throw new AssertionError("Popup worker wait expired");Handler.drain();if(deliver)c.ui.drain();Thread.sleep(1);
        }if(deliver)c.ui.drain();checks.incrementAndGet();
    }
    /** Native destruction alone cannot acknowledge the Browser process guard.
     * Positive teardown delivers the original authenticated detach; failed
     * connections retain their existing uncertain cleanup result. */
    private static void closeHandoff(CdpNetworkWorkerProbe.Case host,Backend backend,MdoBrowserCdpPopupBroker.Handoff handoff,MdoBrowserCdpPopupNative.Promotion promoted)throws Exception{
        if(handoff.current())handoff.close();waitFor(promoted::ended,host,true);
        check(!handoff.current()&&promoted.nativeDestroyed());
        if(backend.closes.get()==0){
            if(!handoff.retiring())throw new AssertionError("Lost owner was treated as deliberate closure");backend.wire.add(detached("proof-real"));
        }
        waitFor(()->backend.closes.get()==1,host,true);
    }
    private static JSONObject attached(String child,String opener)throws Exception{return new JSONObject().put("method","Target.attachedToTarget").put("params",new JSONObject().put("sessionId",child)
        .put("targetInfo",new JSONObject().put("targetId","target-"+child).put("type","page").put("openerId",opener)).put("waitingForDebugger",true));}
    private static JSONObject ack(int id,String child)throws Exception{JSONObject reply=new JSONObject().put("id",id).put("result",new JSONObject());if(child!=null)reply.put("sessionId",child);return reply;}
    private static final class Backend implements MdoBrowserCdpPopupWorker.Factory {
        final AtomicInteger creates=new AtomicInteger(),opens=new AtomicInteger(),closes=new AtomicInteger(),enables=new AtomicInteger(),disables=new AtomicInteger(),resumes=new AtomicInteger(),detaches=new AtomicInteger(),receives=new AtomicInteger(),guards=new AtomicInteger(),contextEvents=new AtomicInteger();
        final Set<Thread> threads=ConcurrentHashMap.newKeySet();volatile Wire wire;volatile CountDownLatch armEntered,armRelease;
        volatile java.util.function.Consumer<byte[]> receivedHook;
        final AtomicInteger frameLoadingEvents=new AtomicInteger();
        final Map<String,CdpBindingProbe.Factory> nativeTargets=new ConcurrentHashMap<>();final AtomicInteger nativeOpens=new AtomicInteger(),barriers=new AtomicInteger();
        final Set<String> proofTargets=ConcurrentHashMap.newKeySet();{proofTargets.add("target-proof-real");}
        volatile String barrierMode="normal";volatile Runnable proofConfirmed,closeHook;volatile boolean closeFailure;
        volatile String humanDetachMode;volatile JSONObject heldHumanDetach;
        volatile String closeSite;
        volatile String eventFailure,eventFailureSite,sendFailure;
        volatile MdoBrowserCdpPopupBroker originalBroker;final AtomicInteger ledgerBoundaryTrips=new AtomicInteger();
        final Set<String> responseRequestKeys=ConcurrentHashMap.newKeySet();
        volatile boolean unsupportedArm;
        volatile String guardMode="inline";volatile Runnable guardTimeout;volatile CountDownLatch guardEntered,guardRelease;
        volatile String managedResumeMode,managedFlowMode="normal";volatile Runnable managedResumeHook,managedFlowHook;
        java.util.function.Consumer<String> managedResumeChildHook;
        String holdControlMethod;volatile JSONObject heldControl;
        void controlReply(Wire output,String method,String child,JSONObject result){
            if("proof-real".equals(child)&&method.equals(holdControlMethod)){if(heldControl!=null)throw new AssertionError("Duplicate original control reply");heldControl=result;holdControlMethod=null;}
            else output.add(result);
        }
        void releaseControl(){JSONObject original=heldControl;if(original==null)throw new AssertionError("No original control reply");heldControl=null;wire.add(original);}
        final AtomicInteger continues=new AtomicInteger();final ConcurrentLinkedQueue<String> continuedIds=new ConcurrentLinkedQueue<>();
        final Set<String> continuedSessions=ConcurrentHashMap.newKeySet();
        volatile String responseMode,responseStream="original-child-stream";volatile boolean holdRead;final AtomicInteger responsePasses=new AtomicInteger(),responseAborts=new AtomicInteger(),bodyClaims=new AtomicInteger(),bodyReads=new AtomicInteger(),bodyCloses=new AtomicInteger(),lateRead=new AtomicInteger();
        volatile Runnable responseIo;
        volatile Runnable retiringClockHook;volatile String retiringClockScope;final AtomicInteger retiringClockTrips=new AtomicInteger();
        DocumentRenderer documentRenderer;
        final Map<String,DocumentRenderer> documentRenderers=new ConcurrentHashMap<>();
        SourceRenderer sourceRenderer;boolean holdAbort,holdClaim;volatile JSONObject heldAbortReply,heldClaimReply;
        final Map<String,SourceRenderer> sourceRenderers=new ConcurrentHashMap<>();
        final AtomicInteger nativeNavigationCommands=new AtomicInteger();
        static final class SourceRenderer {
            final String mode,session;volatile int calls,trees;Runnable beforeFirst;boolean holdFirst;volatile JSONObject held;
            boolean holdNavigation;volatile JSONObject heldNavigation;
            int navigations,index,sameDocumentTransitions,cacheRestores,transitionNetworkRequests;String loader="load-owned",url="https://private.invalid/initial";
            JSONObject frame()throws JSONException{return new JSONObject().put("id","frame-owned").put("loaderId",loader).put("url",url);}
            void transition(JSONObject command,Wire output)throws Exception {
                String method=command.getString("method");JSONObject params=command.getJSONObject("params"),result=new JSONObject();navigations++;
                boolean same=mode.equals("source-navigation-same"),reload=method.equals("Page.reload"),go=method.equals("Page.navigate");
                if(go){check(params.length()==2&&params.getString("frameId").equals("frame-owned")&&params.getString("url").equals(same?"https://private.invalid/initial#section":"https://private.invalid/destination"));index=1;}
                else if(reload)check(params.length()==1&&params.getString("loaderId").equals(loader));
                else {check(method.equals("Page.navigateToHistoryEntry")&&params.length()==1);int entry=params.getInt("entryId");check(entry==10||entry==20);index=entry==10?0:1;}
                boolean within=same&&!reload,restored=!same&&!go&&!reload;
                if(!within)loader=reload?"load-reload-"+navigations:index==0?"load-owned":"load-cached-destination";
                url=index==0?"https://private.invalid/initial":same?"https://private.invalid/initial#section":"https://private.invalid/destination";
                output.add(new JSONObject().put("method","Page.frameStartedNavigating").put("sessionId",session).put("params",new JSONObject().put("frameId","frame-owned").put("loaderId",within||restored?"request-token-"+navigations:loader).put("navigationType",reload?"reload":within?(go?"sameDocument":"historySameDocument"):go?"differentDocument":"historyDifferentDocument").put("url",url)));
                if(within){sameDocumentTransitions++;output.add(new JSONObject().put("method","Page.navigatedWithinDocument").put("sessionId",session).put("params",new JSONObject().put("frameId","frame-owned").put("url",url).put("navigationType","fragment")));}
                else {if(restored)cacheRestores++;else {transitionNetworkRequests++;output.add(new JSONObject().put("method","Network.requestWillBeSent").put("sessionId",session).put("params",new JSONObject().put("requestId","transition-request-"+navigations).put("frameId","frame-owned").put("loaderId",loader).put("type","Document")));}
                    output.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId",session).put("params",new JSONObject().put("frame",frame()).put("type",restored?"BackForwardCacheRestore":"Navigation")));}
                if(go){result.put("frameId","frame-owned").put("isDownload",false);if(!within)result.put("loaderId",loader);}
                output.add(ack(command.getInt("id"),session).put("result",result));
            }
            void navigation(JSONObject command,Wire output)throws Exception{
                if(mode.equals("source-navigation-same")||mode.equals("source-navigation-bfcache")){transition(command,output);return;}
                String method=command.getString("method");JSONObject params=command.getJSONObject("params"),result=new JSONObject();navigations++;
                if(method.equals("Page.navigate")){check(params.length()==2&&params.getString("frameId").equals("frame-owned")&&params.getString("url").equals("https://private.invalid/destination"));index=1;}
                else if(method.equals("Page.reload"))check(params.length()==1&&params.getString("loaderId").equals(loader));
                else{check(method.equals("Page.navigateToHistoryEntry")&&params.length()==1);int entry=params.getInt("entryId");check(entry==10||entry==20);index=entry==10?0:1;}
                if(mode.equals("source-navigation-rejected")){output.add(new JSONObject().put("id",command.getInt("id")).put("sessionId",session).put("error",new JSONObject().put("code",-32000).put("message","synthetic-unread")));return;}
                loader="load-nav-"+navigations;url=index==0?"https://private.invalid/initial":"https://private.invalid/destination";
                if(!mode.equals("source-navigation-no-commit")&&!mode.equals("source-navigation-cancel")){
                    output.add(new JSONObject().put("method","Page.frameStartedNavigating").put("sessionId",session).put("params",new JSONObject().put("frameId","frame-owned").put("loaderId",loader).put("navigationType",method.equals("Page.reload")?"reload":method.equals("Page.navigate")?"differentDocument":"historyDifferentDocument").put("url",url)));
                    output.add(new JSONObject().put("method","Network.requestWillBeSent").put("sessionId",session).put("params",new JSONObject().put("requestId","nav-request-"+navigations).put("frameId","frame-owned").put("loaderId",loader).put("type","Document")));
                    output.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId",session).put("params",new JSONObject().put("frame",frame())));
                }
                if(method.equals("Page.navigate"))result.put("frameId","frame-owned").put("loaderId",loader).put("isDownload",false);
                JSONObject reply=ack(command.getInt("id"),session).put("result",result);
                if(holdNavigation){check(heldNavigation==null);heldNavigation=reply;}else output.add(reply);
            }
            SourceRenderer(String mode){this(mode,"proof-real");}
            SourceRenderer(String mode,String session){if(!session.equals("proof-real")&&!session.equals("proof-second"))throw new AssertionError("Unregistered source fixture");this.mode=mode;this.session=session;}
            void release(Wire output){JSONObject original=held;check(original!=null);held=null;output.add(original);}
            void command(JSONObject command,Wire output)throws Exception {
                check(command.length()==4&&command.getString("sessionId").equals(session));String method=command.getString("method");JSONObject params=command.getJSONObject("params"),result=new JSONObject();calls++;
                if(method.equals("Page.enable")){
                    Runnable hook=beforeFirst;if(hook!=null)hook.run();
                    check(params.length()==0);output.add(new JSONObject().put("method","Page.frameStartedNavigating").put("sessionId",session).put("params",new JSONObject().put("frameId","frame-owned").put("loaderId","load-pending").put("navigationType","differentDocument").put("url","https://private.invalid/pending")));
                    output.add(new JSONObject().put("method","Page.frameStartedLoading").put("sessionId",session).put("params",new JSONObject().put("frameId","frame-owned")));
                    if(mode.equals("source-nonempty"))result.put("unexpected",true);
                }else if(method.equals("Page.getFrameTree")){
                    check(params.length()==0);trees++;JSONObject frame=mode.startsWith("source-navigation-")?frame():new JSONObject().put("id","frame-owned").put("loaderId","load-owned");
                    if(mode.equals("source-changed")||mode.equals("source-race")&&trees==1)output.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId",session).put("params",new JSONObject().put("frame",frame)));
                    result.put("frameTree",new JSONObject().put("frame",frame));
                }else if(method.equals("Page.getNavigationHistory")){
                    check(mode.startsWith("source-navigation-")&&params.length()==0);result.put("currentIndex",index).put("entries",new org.json.JSONArray().put(new JSONObject().put("id",10).put("url","https://private.invalid/initial")).put(new JSONObject().put("id",20).put("url",mode.equals("source-navigation-same")?"https://private.invalid/initial#section":"https://private.invalid/destination")));
                }else if(method.equals("Page.navigate")||method.equals("Page.navigateToHistoryEntry")||method.equals("Page.reload")){
                    check(mode.startsWith("source-navigation-"));navigation(command,output);return;
                }else{
                    check(method.equals("Network.enable")&&params.length()==3&&params.getInt("maxTotalBufferSize")==0&&params.getInt("maxResourceBufferSize")==0&&params.getInt("maxPostDataSize")==0);
                    if(mode.equals("source-unsupported")){output.add(new JSONObject().put("id",command.getInt("id")).put("sessionId",session).put("error",new JSONObject().put("code",-32601).put("message","synthetic-unread")));return;}
                }
                JSONObject reply=ack(command.getInt("id"),session).put("result",result);
                if(holdFirst&&method.equals("Page.enable")){check(held==null);held=reply;return;}output.add(reply);
            }
        }
        static final class DocumentRenderer {
            final java.util.concurrent.ConcurrentHashMap<String,String> nativeSessions=new java.util.concurrent.ConcurrentHashMap<>();
            final CdpFramesProbe.Plan frames=new CdpFramesProbe.Plan();final CdpWorldsProbe.Engine worlds;
            final CdpBindingProbe.Wire wire;final String root,prefix;int resets,runtime;boolean binding=true;Runnable beforeWorld;
            String holdMethod,holdExpression;volatile JSONObject held;
            volatile String retirementMode;volatile int childStops;volatile JSONObject heldStop;
            final CdpBindingProbe.Factory context;volatile SourceRenderer navigationPeer;boolean wrongNavigationDocument;
            DocumentRenderer(WebView view)throws Exception{this(view,"proof-real","");}
            DocumentRenderer(WebView view,String root)throws Exception{this(view,root,root.equals("proof-real")?"first-":"second-");}
            private DocumentRenderer(WebView view,String root,String prefix)throws Exception{
                if(!root.equals("proof-real")&&!root.equals("proof-second"))throw new AssertionError("Unregistered document renderer");
                this.root=root;this.prefix=prefix;worlds=new CdpWorldsProbe.Engine();worlds.evaluation=(context,expression)->{check(expression.equals("({ok:true,text:'managed'})"));return new JSONObject().put("ok",true).put("text","managed");};frames.extra=worlds;context=new CdpBindingProbe.Factory(view);wire=new CdpBindingProbe.Wire(context,"owned");
            }
            boolean owns(String session){return root.equals(session)||!prefix.isEmpty()&&session.startsWith(prefix);}
            void detachNative(String child,Wire output)throws Exception{
                String parent=nativeSessions.remove(child);if(parent==null)throw new AssertionError("Missing original native child");
                output.add(new JSONObject().put("method","Target.detachedFromTarget").put("sessionId",parent).put("params",new JSONObject().put("sessionId",child)));
            }
            void detachNativeChildren(Wire output)throws Exception{
                while(!nativeSessions.isEmpty()){
                    String leaf=null;for(String child:nativeSessions.keySet())if(!nativeSessions.containsValue(child)){leaf=child;break;}
                    if(leaf==null)throw new AssertionError("Native fixture ancestry cycle");detachNative(leaf,output);
                }
            }
            void release(Wire output){JSONObject original=held;if(original==null)throw new AssertionError("No original document reply");held=null;output.add(original);}
            void loseWorld(String raw,Wire output)throws Exception {
                CdpWorldsProbe.Context found=null;for(CdpWorldsProbe.Context context:worlds.contexts.values())if(context.source==null&&context.frame.equals(raw)){check(found==null);found=context;}
                check(found!=null);final CdpWorldsProbe.Context original=found;worlds.contexts.values().removeIf(context->context==original);
                output.add(new JSONObject().put("method","Runtime.executionContextDestroyed").put("sessionId",root).put("params",new JSONObject().put("executionContextId",original.id)));
            }
            void command(JSONObject command,Wire output)throws Exception{
                String nativeMethod=command.getString("method");SourceRenderer navigator=navigationPeer;
                if(navigator!=null&&command.optString("sessionId").equals(root)&&java.util.Arrays.asList("Page.navigate","Page.navigateToHistoryEntry","Page.reload","Page.getNavigationHistory").contains(nativeMethod)){
                    navigator.command(command,output);frames.rootLoader=context.rootLoader=wrongNavigationDocument?"foreign-navigation-loader":navigator.loader;frames.rootUrl=context.rootUrl=navigator.url;return;
                }
                JSONObject request=new JSONObject(command.toString());String original=request.getString("sessionId");
                if(original.equals(root))request.remove("sessionId");else{
                    String local=original;if(!prefix.isEmpty()){if(!local.startsWith(prefix))throw new AssertionError("Foreign renderer session");local=local.substring(prefix.length());}
                    if(resets>0){String generation="generation-"+resets+"-";check(local.startsWith(generation));local=local.substring(generation.length());}request.put("sessionId",local);
                }
                String method=request.getString("method");JSONObject params=request.getJSONObject("params");
                if(method.equals("Page.createIsolatedWorld")&&beforeWorld!=null){Runnable hook=beforeWorld;beforeWorld=null;hook.run();}
                if(method.equals("Page.enable")&&!request.has("sessionId"))binding=false;
                if(binding&&(method.equals("Runtime.enable")||method.equals("Runtime.globalLexicalScopeNames")||method.equals("Runtime.evaluate")||method.equals("Page.getFrameTree"))){if(method.equals("Runtime.enable"))runtime++;byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);try{wire.send(bytes,1000);}finally{java.util.Arrays.fill(bytes,(byte)0);}}
                else if(method.equals("Runtime.disable")){check(!request.has("sessionId")&&params.length()==0);resets++;binding=true;worlds.namespaces.remove(wire);wire.queue(ack(request.getInt("id"),null));}
                else if(method.equals("Target.setAutoAttach")&&!params.getBoolean("autoAttach")){
                    check(!request.has("sessionId")&&!params.getBoolean("waitForDebuggerOnStart")&&params.getBoolean("flatten")&&params.length()==3);childStops++;
                    String mode=retirementMode;JSONObject stopped=ack(request.getInt("id"),root);
                    if("stop-send".equals(mode))throw new IOException("controlled-stop-send-failed");
                    if("stop-rejected".equals(mode)||"stop-unsupported".equals(mode)){stopped.remove("result");stopped.put("error",new JSONObject().put("code",mode.equals("stop-unsupported")?-32601:-32000).put("message","private-provider-detail"));output.add(stopped);return;}
                    if("stop-malformed".equals(mode)){stopped.getJSONObject("result").put("unexpected",true);output.add(stopped);return;}
                    if("stop-no-ack".equals(mode)){heldStop=stopped;return;}
                    if("held-child".equals(mode)||"missing-child".equals(mode)){output.add(stopped);return;}
                    if("late-attach".equals(mode)){nativeSessions.put("late-retiring-child",root);output.add(new JSONObject().put("method","Target.attachedToTarget").put("sessionId",root).put("params",new JSONObject().put("sessionId","late-retiring-child").put("waitingForDebugger",false).put("targetInfo",new JSONObject().put("type","iframe").put("targetId","late-retiring-target"))));}
                    if("raw-request".equals(mode)||"raw-response".equals(mode)){JSONObject pause=new JSONObject().put("requestId","response-extra-raw-human");if(mode.equals("raw-response"))pause.put("responseStatusCode",200).put("resourceType","Image");output.add(new JSONObject().put("method","Fetch.requestPaused").put("sessionId",root).put("params",pause));}
                    if("wrong-parent".equals(mode)){String child=nativeSessions.keySet().iterator().next();output.add(new JSONObject().put("method","Target.detachedFromTarget").put("sessionId",child).put("params",new JSONObject().put("sessionId",child)));return;}
                    if("ack-first".equals(mode))output.add(stopped);
                    if("parent-first".equals(mode)){while(!nativeSessions.isEmpty()){String parent=nativeSessions.keySet().iterator().next();for(String candidate:nativeSessions.keySet())if(nativeSessions.containsValue(candidate)){parent=candidate;break;}detachNative(parent,output);}}
                    else detachNativeChildren(output);frames.armed.clear();
                    if("held-ack".equals(mode)){heldStop=stopped;return;}
                    if(!"ack-first".equals(mode))output.add(stopped);return;
                }
                else if(!frames.send(request,wire))throw new AssertionError("Unapproved document command");
                byte[] bytes;while((bytes=wire.replies.pollFirst())!=null){JSONObject packet=new JSONObject(new String(bytes,StandardCharsets.UTF_8));java.util.Arrays.fill(bytes,(byte)0);
                    if(!packet.has("sessionId"))packet.put("sessionId",root);else packet.put("sessionId",prefix+(resets>0?"generation-"+resets+"-":"")+packet.getString("sessionId"));
                    if((resets>0||!prefix.isEmpty())&&packet.optString("method").startsWith("Target.")&&packet.optJSONObject("params")!=null&&packet.getJSONObject("params").has("sessionId"))packet.getJSONObject("params").put("sessionId",prefix+(resets>0?"generation-"+resets+"-":"")+packet.getJSONObject("params").getString("sessionId"));
                    if(packet.optString("method").equals("Target.attachedToTarget"))nativeSessions.put(packet.getJSONObject("params").getString("sessionId"),packet.getString("sessionId"));
                    if(method.equals(holdMethod)&&(holdExpression==null||holdExpression.equals(params.optString("expression")))&&packet.optInt("id",-1)==command.getInt("id")&&original.equals(packet.optString("sessionId"))){if(held!=null)throw new AssertionError("Duplicate held reply");held=packet;holdMethod=null;}
                    else output.add(packet);}
            }
        }
        public MdoBrowserCdpPopupBroker create(BooleanSupplier current,LongSupplier clock){creates.incrementAndGet();threads.add(Thread.currentThread());Wire next=new Wire();wire=next;
            final Thread worker=Thread.currentThread();LongSupplier originalClock=()->{
                Runnable work=retiringClockHook;
                if(work!=null&&Thread.currentThread()==worker){
                    boolean eligible=false,scope=false;
                    for(StackTraceElement site:Thread.currentThread().getStackTrace()){
                        if(site.getClassName().equals("org.xserver.android.MdoBrowserCdpPopupBroker$Handoff")&&site.getMethodName().equals("eligible"))eligible=true;
                        boolean sourceScope=retiringClockScope.startsWith("source-");
                        if(site.getClassName().equals("org.xserver.android."+(sourceScope?"MdoBrowserCdpPopupSource":"MdoBrowserCdpPopupBroker"))&&site.getMethodName().equals(sourceScope?retiringClockScope.substring(7):retiringClockScope))scope=true;
                    }
                    if(eligible&&scope){retiringClockHook=null;work.run();retiringClockTrips.incrementAndGet();}
                }
                return clock.getAsLong();
            };
            return originalBroker=new MdoBrowserCdpPopupBroker(current,originalClock,(timeout,alive,events)->{opens.incrementAndGet();return MdoBrowserCdpSession.ownedBrowser(next,alive,(method,child,params)->{
                try{events.event(method,child,params);if(method.equals("Runtime.executionContextsCleared")||method.equals("Runtime.executionContextDestroyed"))contextEvents.incrementAndGet();if(method.equals("Page.frameStartedLoading")||method.equals("Page.frameStoppedLoading")||method.equals("Page.frameNavigated"))frameLoadingEvents.incrementAndGet();}catch(IOException|RuntimeException|Error error){eventFailure=method+":"+error.getClass().getSimpleName()+":"+error.getMessage();StringBuilder site=new StringBuilder();for(StackTraceElement frame:error.getStackTrace())if(frame.getClassName().startsWith("org.xserver.android.MdoBrowser"))site.append(frame.getClassName()).append('.').append(frame.getMethodName()).append('>');eventFailureSite=site.toString();throw error;}
            },clock::getAsLong);},
                (target,timeout,alive,events)->{threads.add(Thread.currentThread());nativeOpens.incrementAndGet();CdpBindingProbe.Factory peer=nativeTargets.get(target);
                    if(peer==null)throw new IOException("cdp_fixture_unregistered_target");return peer.open(proofTargets.contains(target)?"owned":"other",timeout,alive,events);});}
        volatile boolean holdNativeDescendants;
        private final class Wire implements MdoBrowserCdpSession.Wire {
            final ConcurrentLinkedQueue<byte[]> incoming=new ConcurrentLinkedQueue<>();
            void add(JSONObject packet){JSONObject params=packet.optJSONObject("params");
                if(!holdNativeDescendants&&!packet.has("sessionId")&&"Target.detachedFromTarget".equals(packet.optString("method"))&&params!=null){
                    DocumentRenderer renderer=documentRenderers.get(params.optString("sessionId"));if(renderer==null&&documentRenderer!=null&&documentRenderer.root.equals(params.optString("sessionId")))renderer=documentRenderer;
                    if(renderer!=null)try{renderer.detachNativeChildren(this);}catch(Exception error){throw new AssertionError(error);}
                }
                if("Fetch.requestPaused".equals(packet.optString("method"))&&params!=null&&(params.has("responseStatusCode")||params.has("responseErrorReason")))responseRequestKeys.add(packet.optString("sessionId")+"|"+params.optString("requestId"));incoming.add(packet.toString().getBytes(StandardCharsets.UTF_8));}
            public void send(byte[] bytes,int timeout)throws IOException{
                threads.add(Thread.currentThread());try{JSONObject command=new JSONObject(new String(bytes,StandardCharsets.UTF_8));String method=command.getString("method");int id=command.getInt("id");
                    if(method.equals("Page.navigate")||method.equals("Page.navigateToHistoryEntry")||method.equals("Page.reload"))nativeNavigationCommands.incrementAndGet();
                    if(command.has("sessionId")&&(method.equals("Page.enable")||method.equals("Page.getFrameTree")||method.equals("Network.enable")||method.equals("Page.getNavigationHistory")||method.equals("Page.navigate")||method.equals("Page.navigateToHistoryEntry")||method.equals("Page.reload"))){
                        SourceRenderer renderer=sourceRenderers.get(command.getString("sessionId"));if(renderer==null)renderer=sourceRenderer;
                        if(renderer!=null){renderer.command(command,this);return;}
                    }
                    if(command.has("sessionId")&&!method.startsWith("Fetch.")&&!method.startsWith("IO.")&&!method.equals("Runtime.runIfWaitingForDebugger")){
                        DocumentRenderer renderer=documentRenderers.get(command.getString("sessionId"));
                        if(renderer==null)for(DocumentRenderer candidate:documentRenderers.values())if(candidate.owns(command.getString("sessionId"))){if(renderer!=null)throw new AssertionError("Ambiguous document renderer");renderer=candidate;}
                        if(renderer==null)renderer=documentRenderer;if(renderer!=null){renderer.command(command,this);return;}
                    }
                    if(responseMode!=null&&(method.equals("Fetch.takeResponseBodyAsStream")||method.equals("Fetch.failRequest")||method.startsWith("IO.")||method.equals("Fetch.continueRequest")&&continuedSessions.contains(command.getString("sessionId"))&&responseRequestKeys.contains(command.getString("sessionId")+"|"+command.getJSONObject("params").optString("requestId")))){
                        Runnable audit=responseIo;if(audit!=null)audit.run();
                        JSONObject params=command.getJSONObject("params");String child=command.getString("sessionId");check(command.length()==4&&proofTargets.contains("target-"+child));JSONObject value=new JSONObject();
                        if(method.startsWith("IO.")){check(params.getString("handle").equals(responseStream));
                            if(method.equals("IO.read")){check(params.length()==2&&params.getInt("size")==65536);bodyReads.incrementAndGet();if(holdRead){lateRead.set(id);return;}value.put("data","AAH/").put("base64Encoded",true).put("eof",true);}
                            else{check(method.equals("IO.close")&&params.length()==1);bodyCloses.incrementAndGet();}
                        }else{check(params.getString("requestId").equals("initial-0")||params.getString("requestId").startsWith("response-extra"));
                            if(method.equals("Fetch.takeResponseBodyAsStream")){check(params.length()==1);bodyClaims.incrementAndGet();value.put("stream",responseStream);if(holdClaim){heldClaimReply=new JSONObject().put("id",id).put("sessionId",child).put("result",new JSONObject(value.toString()));return;}}
                            else if(method.equals("Fetch.failRequest")){check(params.length()==2&&params.getString("errorReason").equals("Aborted"));responseAborts.incrementAndGet();if(holdAbort){heldAbortReply=new JSONObject().put("id",id).put("sessionId",child).put("result",new JSONObject());return;}}
                            else{check(params.length()==1);responsePasses.incrementAndGet();}
                        }controlReply(this,"response:"+method,child,new JSONObject().put("id",id).put("sessionId",child).put("result",value));return;
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
                            if(command.length()!=4||command.getJSONObject("params").length()!=0||!proofTargets.contains("target-"+child))throw new AssertionError("Unapproved protected resume");
                            if(managedResumeChildHook!=null)managedResumeChildHook.accept(child);
                            Runnable hook=managedResumeHook;if(hook!=null)hook.run();
                            if(managedResumeMode.equals("send-failure"))throw new IOException("controlled_partial_resume_send");
                            if(managedResumeMode.equals("timeout"))return;
                            if(managedResumeMode.equals("unsupported")||managedResumeMode.equals("rejected")){add(new JSONObject().put("id",id).put("sessionId",child).put("error",new JSONObject().put("code",managedResumeMode.equals("unsupported")?-32601:-32000).put("message","synthetic-unread")));return;}
                            JSONObject result=ack(id,managedResumeMode.equals("mismatch")?null:child);if(managedResumeMode.equals("nonempty"))result.getJSONObject("result").put("unexpected",true);controlReply(this,method,child,result);
                        }else add(ack(id,child));
                    }
                    else if(method.equals("Fetch.continueRequest")){
                        JSONObject params=command.getJSONObject("params");String request=params.getString("requestId"),child=command.getString("sessionId");
                        if(managedResumeMode==null||command.length()!=4||params.length()!=1||!proofTargets.contains("target-"+child)||!request.equals("initial-0")&&!request.equals("managed-next"))throw new AssertionError("Unapproved original request continuation");
                        continues.incrementAndGet();continuedSessions.add(child);continuedIds.add(request);Runnable hook=managedFlowHook;if(hook!=null)hook.run();
                        if(managedFlowMode.equals("send-failure"))throw new IOException("controlled_partial_continue_send");
                        if(managedFlowMode.equals("timeout"))return;
                        if(managedFlowMode.equals("rejected")){add(new JSONObject().put("id",id).put("sessionId",child).put("error",new JSONObject().put("code",-32000).put("message","synthetic-unread")));return;}
                        if(managedFlowMode.equals("limit"))for(int i=0;i<33;i++)add(handoffPaused("response-overflow-"+i,true));
                        else add((responseMode==null?handoffPaused(request,true):responsePaused(request,responseMode)).put("sessionId",managedFlowMode.equals("foreign")?"foreign":child));
                        if("source-captured-epoch".equals(responseMode))add(new JSONObject().put("method","Page.frameNavigated").put("sessionId",child).put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","load-replaced"))));
                        JSONObject result=ack(id,child);if(managedFlowMode.equals("nonempty"))result.getJSONObject("result").put("unexpected",true);controlReply(this,method,child,result);
                    }
                    else if(method.equals("Target.getTargetInfo")){
                        JSONObject p=command.getJSONObject("params");if(command.length()!=3||p.length()!=1||!proofTargets.contains(p.getString("targetId")))throw new AssertionError("Caller-selected target barrier");barriers.incrementAndGet();
                        if(barrierMode.equals("detach"))add(detached("proof-real"));
                        add(new JSONObject().put("id",id).put("result",new JSONObject().put("targetInfo",new JSONObject().put("targetId",barrierMode.equals("wrong")?"foreign":p.getString("targetId")).put("type","page"))));
                        Runnable callback=proofConfirmed;if(callback!=null)callback.run();
                    }
                    else if(method.equals("Target.detachFromTarget")){
                        detaches.incrementAndGet();String mode=humanDetachMode;
                        if(mode==null){add(ack(id,null));return;}
                        check(command.length()==3&&command.getJSONObject("params").length()==1&&command.getJSONObject("params").getString("sessionId").equals("proof-real"));
                        JSONObject reply=ack(id,null);
                        if(mode.equals("rejected")||mode.equals("unsupported")){add(new JSONObject().put("id",id).put("error",new JSONObject().put("code",mode.equals("unsupported")?-32601:-32000).put("message","private-renderer-message")));return;}
                        if(mode.equals("malformed")){reply.getJSONObject("result").put("unexpected",true);add(reply);return;}
                        if(mode.equals("event-first")){add(detached("proof-real"));add(reply);return;}
                        if(mode.equals("no-ack")){heldHumanDetach=reply;add(detached("proof-real"));return;}
                        if(mode.equals("late-pause"))add(new JSONObject().put("method","Fetch.requestPaused").put("sessionId","proof-real").put("params",new JSONObject().put("requestId","late-root-detach-pause")));
                        add(reply);if(mode.equals("ack-only"))return;
                        add(detached(mode.equals("wrong-event")?"foreign-human-session":"proof-real"));
                    }
                    else throw new AssertionError("Unapproved popup worker command");
                }catch(IOException error){throw error;}catch(RuntimeException|Error error){sendFailure=error.toString()+java.util.Arrays.toString(error.getStackTrace());throw error;}catch(Exception error){throw new IOException("fixture_command_failed");}
            }
            public byte[] receive(int timeout){threads.add(Thread.currentThread());receives.incrementAndGet();byte[] value=incoming.poll();java.util.function.Consumer<byte[]> hook=receivedHook;if(value!=null&&hook!=null)hook.accept(value);if(value==null)try{Thread.sleep(1);}catch(InterruptedException error){Thread.currentThread().interrupt();}return value;}
            public void close(){StringBuilder site=new StringBuilder();for(StackTraceElement frame:Thread.currentThread().getStackTrace())if(frame.getClassName().startsWith("org.xserver.android.MdoBrowser"))site.append(frame.getClassName()).append('.').append(frame.getMethodName()).append('>');closeSite=site.toString();closes.incrementAndGet();Runnable hook=closeHook;if(hook!=null)hook.run();if(closeFailure)throw new IllegalStateException("controlled_original_transport_close_failure");}
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
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                if(mode.equals("host-close-late-transport")){
                    backend.receivedHook=bytes->{if(new String(bytes,StandardCharsets.UTF_8).contains("host-close-child")){entered.countDown();try{if(!release.await(8,TimeUnit.SECONDS))throw new AssertionError("Host-close receive release expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}}};
                    backend.wire.add(attached("host-close-child","owned"));waitFor(()->entered.getCount()==0,c,true);
                    backend.closeHook=()->{if(c.ui.onThread()){release.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(request.result()==null||!request.result().terminal){if(System.nanoTime()>=end)throw new AssertionError("Original worker did not settle while transport close was returning");try{Thread.sleep(1);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}}}};
                }
                try{c.worker.close();}finally{release.countDown();}terminal(c,request);
                if(!request.result().code.equals("cdp_worker_closed")||request.result().processCleanupConfirmed)throw new AssertionError("Host close receipt: code="+request.result().code+", cleanup="+request.result().processCleanupConfirmed+", source="+request.result().sourceReleased+", stopped="+c.worker.terminated()+", disables="+backend.disables.get()+", closed="+backend.closes.get());
                check(request.result().code.equals("cdp_worker_closed")&&!request.result().processCleanupConfirmed);
                waitFor(c.worker::terminated,c,true);
                if(mode.equals("host-close-late-transport"))hostCloseEvidence=new JSONObject().put("original_close_raced",true).put("code",request.result().code).put("cleanup_confirmed",request.result().processCleanupConfirmed).put("source_released",request.result().sourceReleased).put("wire_closes",backend.closes.get()).put("disables",backend.disables.get()).put("worker_terminated",c.worker.terminated());
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
        boolean allocationFail;Runnable allocationHook;java.util.function.Consumer<WebView> beforeSend;final MdoBrowserLifecycle<WebView> views;int births;
        final MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(8);final WebView view;int sends;Runnable consumed,sendHook;boolean fail;WebView adopting;
        NativeSource(CdpNetworkWorkerProbe.Case host,CdpNetworkBindingProbe.Context network)throws Exception{
            this(host,network,null);
        }
        NativeSource(CdpNetworkWorkerProbe.Case host,CdpNetworkBindingProbe.Context network,MdoBrowserLifecycle.Factory<WebView> inputs)throws Exception{
            this(host,network,inputs,null);
        }
        NativeSource(CdpNetworkWorkerProbe.Case host,CdpNetworkBindingProbe.Context network,MdoBrowserLifecycle.Factory<WebView> inputs,MdoBrowserLifecycle.Invalidated<WebView> invalidated)throws Exception{
            views=inputs==null?PopupLifecycleFixture.registry(()->allocationFail,()->{if(allocationHook!=null)allocationHook.run();}):invalidated==null?new MdoBrowserLifecycle<>(inputs):new MdoBrowserLifecycle<>(inputs,invalidated);
            this.host=host;view=network.view;views.put("root",view);
            core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
                public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}
            },budget,()->true,()->1L,blocked->check(host.ui.onThread()));
            source=new MdoBrowserCdpPopupNative(network.adapter,view,core);
        }
        void open(){check(core.open(new MdoBrowserPopups.Transport<WebView>(){
            private final Object token=new Object();public Object token(){return token;}
            public void send(WebView child,Runnable callback){if(beforeSend!=null)beforeSend.accept(child);check(host.ui.onThread()&&child==adopting);sends++;consumed=callback;Runnable hook=sendHook;sendHook=null;if(hook!=null)hook.run();if(fail)throw new IllegalStateException("controlled-native-send-failure");}
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
                boolean scopedOwnerLoss=mode.equals("activation-owner-loss")||mode.equals("activation-flow-owner-loss");
                product.activate(promoted,handoff);waitFor(()->handoff.activationResult()!=null,c.host,true);
                boolean failure=!resume.equals("normal")||!flow.equals("normal")||mode.equals("activation-owner-loss");
                if(failure){
                    waitFor(()->handoff.flowFailure()!=null&&promoted.ended(),c.host,true);
                    MdoBrowserCdpSession.ControlResult result=handoff.flowFailure();check(!result.ok&&result.code.matches("cdp_[a-z_]{1,80}")&&c.backend.resumes.get()==1&&c.backend.continues.get()==(resume.equals("normal")&&!mode.equals("activation-owner-loss")?1:0));
                    String expected=resume.equals("unsupported")?"cdp_method_unsupported":resume.equals("rejected")||flow.equals("rejected")?"cdp_command_rejected":resume.equals("nonempty")||flow.equals("nonempty")?"cdp_attach_control_reply_invalid":resume.equals("mismatch")?"cdp_reply_mismatch":flow.equals("foreign")?"cdp_session_mismatch":resume.equals("timeout")||flow.equals("timeout")?"cdp_command_timeout":resume.equals("send-failure")||flow.equals("send-failure")?"cdp_transport_failed":flow.equals("limit")?"cdp_attach_request_limit":scopedOwnerLoss?"cdp_popup_control_retired":"cdp_owner_changed";
                    check(result.code.equals(expected)&&result.effectMayHaveOccurred!=resume.equals("unsupported"));
                    check(handoff.activationResult().ok==(!flow.equals("normal"))&&view.destroyed==1&&!handoff.current()&&c.backend.closes.get()==(scopedOwnerLoss?0:1)&&c.backend.disables.get()==0);
                    c.backend.wire.add(ack(999,"proof-real"));Handler.drain();check(handoff.flowFailure()==result&&!handoff.current());
                    if(scopedOwnerLoss){
                        check(!handoff.detached()&&MdoBrowserCdpPopupBroker.creationBlocked());
                        // The transport's close counter is published before
                        // disarm's finally releases the process admission fence.
                        // Join the original completed cleanup, not that earlier
                        // transport notification on another thread.
                        c.backend.wire.add(detached("proof-real"));waitFor(()->handoff.detached()&&c.backend.closes.get()==1&&c.backend.disables.get()==1&&!MdoBrowserCdpPopupBroker.creationBlocked(),c.host,true);
                        check(c.backend.disables.get()==1&&!MdoBrowserCdpPopupBroker.creationBlocked());
                    }
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
                // UI destruction cannot discharge the retained native pause.
                // This owner-loss case has not emitted its native detach yet.
                if(mode.equals("document-owner-loss"))check(c.backend.closes.get()==0&&!handoff.detached()&&candidate.retainedResponseCount()==1);
                check(c.backend.opens.get()==opens&&c.backend.nativeOpens.get()==nativeOpens&&view.evaluations==evaluations+(mode.equals("document-rebind")||mode.equals("document-rebind-frames")||mode.equals("document-navigation")?2:1)&&candidate.retainedResponseCount()==(mode.equals("document-owner-loss")?1:promoted.ended()?0:1));
                closeHandoff(c.host,c.backend,handoff,promoted);check(view.destroyed==1&&c.backend.closes.get()==1&&c.backend.detaches.get()==0&&candidate.retainedResponseCount()==0);
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
                closeHandoff(c.host,c.backend,handoff,promoted);check(view.destroyed==1&&c.backend.closes.get()==1&&c.backend.detaches.get()==0);renderer.wire.close();
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
                        else if(pendingEpoch){marker.lease().invalidate();
                            try{MdoBrowserCdpDownloads.Snapshot retired=downloads.accept(id,65536,3000);
                                check(retired.terminal()&&!retired.verified&&retired.received==0&&"download_frame_changed".equals(retired.error)&&c.backend.bodyClaims.get()==0&&c.backend.bodyReads.get()==0);
                            }catch(IOException error){check("download_frame_changed".equals(error.getMessage()));}
                        }
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
                        }else if(mode.equals("transfer-detach")){
                            waitFor(promoted::ended,c.host,true);
                            waitFor(()->handoff.detached()&&c.backend.closes.get()==1&&c.backend.disables.get()==1&&!MdoBrowserCdpPopupBroker.creationBlocked(),c.host,true);
                            if(done.verified||!done.sourceCleanupConfirmed||!done.cacheCleanupConfirmed||handoff.current()||c.backend.closes.get()!=1||c.backend.disables.get()!=1)
                                throw new AssertionError("Detached original transfer cleanup: source="+done.sourceCleanupConfirmed+" cache="+done.cacheCleanupConfirmed+" wire="+c.backend.closes.get()+" abort="+c.backend.responseAborts.get()+" stream="+c.backend.bodyCloses.get());
                            check(!done.verified&&done.sourceCleanupConfirmed&&done.cacheCleanupConfirmed&&!handoff.current()&&c.backend.closes.get()==1&&c.backend.disables.get()==1);
                            check(c.backend.bodyCloses.get()==1&&c.backend.responseAborts.get()==1);
                            check(!MdoBrowserCdpPopupBroker.creationBlocked());
                        }
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
                    closeHandoff(c.host,c.backend,handoff,promoted);check(view.destroyed==1&&c.backend.closes.get()==1&&c.backend.detaches.get()==0);
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
                c.ui.reject=false;c.ui.drain();if(mode.equals("delivery-loss-create")){
                    try{product.checkCreation();throw new AssertionError("Unknown process guard was treated as cleaned");}
                    catch(IllegalStateException expected){check(expected.getMessage().equals("native_popup_protection_busy"));}
                }else product.resume();
                check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&source.sends==0&&source.budget.used()==0&&c.worker.popupPending()==0);
                check(request.result().sourceReleased==queued&&request.result().processCleanupConfirmed==queued);
            }
        }
    }
    private static void productDisposal(String mode)throws Exception{
        try(OwnershipCase c=new OwnershipCase()){
            PopupProductHost product=new PopupProductHost(c.source.views);
            if(mode.startsWith("product-disposal-profile")){
                int before=WebView.constructions;
                if(mode.endsWith("denied")){
                    product.documents.get("root").assigned=false;failure("browser_profile_source_invalid",()->product.construct(c.source.source,c.allocation,"owned"));
                    check(WebView.constructions==before&&product.documents.size()==1);product.documents.get("root").assigned=true;
                }
                if(mode.endsWith("close")||mode.endsWith("channel"))PopupProductHost.MdoBrowserDocument.profileHook=()->{
                    WebView exact=c.source.views.get("owned");PopupProductHost.MdoBrowserDocument doc=product.documents.get("owned");check(doc.initializing()&&product.configured==0&&exact.destroyed==0);
                    if(mode.endsWith("close")){check(!product.retire("owned",exact));check(exact.destroyed==0&&!doc.cleanupConfirmed());}else product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                };
                if(mode.endsWith("close")||mode.endsWith("channel")){
                    failure("frame_changed",()->product.construct(c.source.source,c.allocation,"owned"));check(WebView.lastConstructed.destroyed==1&&product.configured==0);
                }else{
                    MdoBrowserCdpPopupNative.Created created=product.construct(c.source.source,c.allocation,"owned");WebView child=created.view();
                    check(product.documents.get("owned").profileAssigned()&&product.configured==1&&child.destroyed==0);created.close();check(child.destroyed==1);
                }
                check(product.documents.size()==1&&product.retiring()==0&&!product.failed()&&product.retired==1&&MdoBrowserCdpPopupNative.creationConfirmed());
                c.cancel(false);check(c.request.result().sourceReleased&&c.request.result().processCleanupConfirmed);return;
            }
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
    private static void sourceNavigation(String mode,OwnershipCase c,MdoBrowserCdpMarker marker,MdoBrowserCdpPopupBroker.Handoff handoff,MdoBrowserCdpPopupNative.Source source,MdoBrowserCdpDownloads downloads)throws Exception {
        java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> result=new java.util.concurrent.atomic.AtomicReference<>();
        JSONObject arbitrary=new JSONObject().put("arbitrary",true);
        c.host.worker.submitSource(marker,handoff,source,5000,(binding,deadline)->{
            MdoBrowserCdpBinding.Network network=binding.sourceNavigation();check(network==binding.sourceNavigation());
            int before=c.backend.sourceRenderer.calls;
            refused("cdp_network_scope_invalid",()->network.enableResponses(1000));refused("cdp_network_scope_invalid",()->network.disableResponses(1000));
            refused("cdp_network_scope_invalid",()->network.popupSource());refused("cdp_network_scope_invalid",()->network.cancelTransfers());
            refused("cdp_network_scope_invalid",()->binding.call("Page.getFrameTree",arbitrary,null,1000,false));
            refused("cdp_network_scope_invalid",()->binding.call("Page.getNavigationHistory",new JSONObject(),"foreign",1000,false));
            refused("cdp_network_scope_invalid",()->binding.call("Runtime.evaluate",new JSONObject(),null,1000,false));
            try{network.close();throw new AssertionError("Navigation view closed original source");}catch(IllegalStateException expected){check(source.current());}
            check(c.backend.sourceRenderer.calls==before&&!network.responsesMayHaveOccurred());
            if(mode.equals("source-navigation-pending")){
                refused("cdp_navigation_download_pending",()->network.navigateRoot("https://private.invalid/destination",1000));check(c.backend.sourceRenderer.navigations==0);
            }else{
                MdoBrowserCdpBinding.Network.Navigation original=network.navigateRoot("https://private.invalid/destination",1000);
                if(mode.equals("source-navigation-cancel"))original.cancelAttached();
                while(original.pending())network.poll(10,deadline.remaining());
                MdoBrowserCdpSession.NavigationResult receipt=original.result();
                if(mode.equals("source-navigation-cancel")){
                    check(receipt.code.equals("cdp_navigation_cancelled")&&!receipt.acknowledged&&receipt.effectMayHaveOccurred);
                    network.poll(10,deadline.remaining());check(original.result()==receipt&&network.navigationOrigin(original)==null);
                    refused("cdp_navigation_cancelled",()->network.navigateRoot("https://private.invalid/destination",1000));
                }else if(mode.equals("source-navigation-rejected")){
                    check(receipt.acknowledged&&!receipt.ok&&receipt.code.equals("cdp_navigation_rejected")&&network.navigationOrigin(original)==null);
                }else if(mode.equals("source-navigation-no-commit")){
                    check(receipt.acknowledged&&receipt.ok&&network.navigationOrigin(original)==null);
                }else{
                    MdoBrowserCdpNetworkFrames.Origin previous=network.navigationOrigin(original);check(receipt.acknowledged&&receipt.ok&&previous!=null&&previous.current()&&original.requestAssociationError()==null);
                    for(String action:new String[]{"back","forward","reload"}){
                        MdoBrowserCdpBinding.Network.Navigation next=network.traverseRoot(action,1000);while(next.pending())network.poll(10,deadline.remaining());
                        MdoBrowserCdpNetworkFrames.Origin committed=network.navigationOrigin(next);check(next.result().ok&&next.receiptOnly()&&committed!=null&&committed.current()&&!previous.current());previous=committed;
                    }
                    check(c.backend.sourceRenderer.navigations==4);
                }
            }
            check(source.current()&&handoff.current()&&c.backend.guards.get()==1&&c.backend.resumes.get()==1&&c.backend.disables.get()==0);
            return new byte[]{1};
        },result::set);waitFor(()->result.get()!=null,c.host,true);
        if(result.get().code!=null)throw new AssertionError("Protected source navigation "+mode+": "+result.get().code);
        navigationEvidence=new JSONObject().put("original_source",true).put("shared_ledgers",true).put("navigation_commands",c.backend.sourceRenderer.navigations)
            .put("commit_proved",mode.equals("source-navigation-flow")).put("guard_replacements",0).put("scope_denials_checked",true).put("browser_connections",c.backend.opens.get())
            .put("native_proof_connections",c.backend.nativeOpens.get()).put("ui_navigation_enabled",false).put("provider_qualified",false);
        if(mode.equals("source-navigation-pending")){
            MdoBrowserCdpDownloads.Snapshot pending=downloads.list()[0];downloads.cancel(pending.id);waitFor(()->{try{return downloads.get(pending.id).terminal();}catch(IOException error){throw new AssertionError(error);}},c.host,true);
            MdoBrowserCdpDownloads.Release release=downloads.release(pending.id);waitFor(release::finished,c.host,true);check(release.released());
        }
    }
    private static void waitNavigation(MdoBrowserCdpNetworks.Navigation ticket,boolean commit,CdpNetworkWorkerProbe.Case host)throws Exception {waitFor(()->ticket.result()!=null&&(!commit||ticket.pageCurrent()),host,true);}
    private static void sourceNavigationUi(String mode,OwnershipCase c,MdoBrowserCdpMarker marker,MdoBrowserCdpPopupBroker.Handoff handoff,MdoBrowserCdpPopupNative.Source source,MdoBrowserCdpDownloads downloads)throws Exception {
        String variant=mode.substring("source-navigation-ui-".length());Backend.SourceRenderer renderer=c.backend.sourceRenderer;
        failure("cdp_popup_source_owner_invalid",()->c.host.worker.navigatePopup(marker,handoff,null,"https://private.invalid/destination",null,1000));
        failure("cdp_navigation_arguments_invalid",()->c.host.worker.navigatePopup(marker,handoff,source,"file:///private",null,1000));
        MdoBrowserCdpNetworks.Navigation ticket;boolean recovered=false;
        if(variant.equals("queued-cancel")||variant.equals("queued-timeout")){
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);java.lang.reflect.Field field=MdoBrowserCdpWorker.class.getDeclaredField("executor");field.setAccessible(true);
            ((java.util.concurrent.ThreadPoolExecutor)field.get(c.host.worker)).execute(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Admission queue barrier expired");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            try{
                waitFor(()->entered.getCount()==0,c.host,true);
                ticket=c.host.worker.navigatePopup(marker,handoff,source,"https://private.invalid/destination",null,1000);
                if(variant.equals("queued-cancel"))ticket.cancel();else c.host.time.addAndGet(1000);
                check(!ticket.dispatched()&&renderer.navigations==0&&(variant.equals("queued-cancel")||ticket.result()==null));
            }finally{release.countDown();}
            waitNavigation(ticket,false,c.host);check(!ticket.dispatched()&&renderer.navigations==0&&ticket.result().code.equals(variant.equals("queued-cancel")?"cdp_navigation_cancelled":"cdp_navigation_timeout"));
            waitFor(()->!c.host.worker.pending(marker),c.host,true);
            MdoBrowserCdpNetworks.Navigation recovery=c.host.worker.navigatePopup(marker,handoff,source,"https://private.invalid/destination",null,1000);waitNavigation(recovery,true,c.host);
            check(recovery.result().ok&&renderer.navigations==1&&source.current()&&handoff.current());recovered=true;
        }else{
            renderer.holdNavigation=variant.equals("late-cancel");
            ticket=c.host.worker.navigatePopup(marker,handoff,source,"https://private.invalid/destination",null,1000);
            if(variant.equals("late-cancel")){
                waitFor(()->renderer.heldNavigation!=null,c.host,true);check(ticket.result()==null&&ticket.dispatched());ticket.cancel();waitNavigation(ticket,false,c.host);
                MdoBrowserCdpSession.NavigationResult terminal=ticket.result();check(terminal.code.equals("cdp_navigation_cancelled")&&terminal.effectMayHaveOccurred&&!terminal.acknowledged&&!ticket.pageCommitted());
                int before=c.backend.receives.get();c.backend.wire.add(renderer.heldNavigation);renderer.heldNavigation=null;waitFor(()->c.backend.receives.get()>before+2,c.host,true);
                check(ticket.result()==terminal&&source.current()&&handoff.current()&&c.backend.closes.get()==0&&renderer.navigations==1);
            }else if(variant.equals("pending")){
                waitNavigation(ticket,false,c.host);check(ticket.result().code.equals("cdp_navigation_download_pending")&&!ticket.dispatched()&&renderer.navigations==0);
            }else{
                waitNavigation(ticket,true,c.host);check(ticket.result().ok&&ticket.pageCommitted()&&ticket.pageDocument()!=null&&renderer.navigations==1);
                for(String action:new String[]{"back","forward","reload"}){
                    MdoBrowserCdpNetworks.Navigation previous=ticket;
                    ticket=c.host.worker.navigatePopup(marker,handoff,source,null,action,1000);
                    MdoBrowserCdpNetworks.Navigation next=ticket;waitFor(()->next.result()!=null&&next.pageCurrent(),c.host,true);check(next.result().ok&&!previous.pageCurrent());
                }check(renderer.navigations==4);
            }
        }
        navigationEvidence=new JSONObject().put("original_source",true).put("shared_ledgers",true).put("native_ui_ticket",true).put("queued_recovery",recovered).put("navigation_commands",renderer.navigations)
            .put("commit_proved",variant.equals("flow")).put("guard_replacements",0).put("scope_denials_checked",true).put("browser_connections",c.backend.opens.get())
            .put("native_proof_connections",c.backend.nativeOpens.get()).put("ui_navigation_enabled",false).put("provider_qualified",false);
        if(variant.equals("pending")){
            MdoBrowserCdpDownloads.Snapshot pending=downloads.list()[0];downloads.cancel(pending.id);waitFor(()->{try{return downloads.get(pending.id).terminal();}catch(IOException error){throw new AssertionError(error);}},c.host,true);
            MdoBrowserCdpDownloads.Release release=downloads.release(pending.id);waitFor(release::finished,c.host,true);check(release.released());
        }
    }
    @SuppressWarnings("unchecked") private static <E extends Throwable> long failingNavigationClock(Throwable failure)throws E {throw (E)failure;}
    private static void sourceNavigationClose(String mode,OwnershipCase c,MdoBrowserCdpMarker marker,MdoBrowserCdpPopupBroker.Handoff handoff,MdoBrowserCdpPopupNative.Source source)throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> caught=new java.util.concurrent.atomic.AtomicReference<>();java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> done=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean invalidated=new java.util.concurrent.atomic.AtomicBoolean(),servicing=new java.util.concurrent.atomic.AtomicBoolean();
        boolean spoof=mode.endsWith("-spoof"),sent=mode.endsWith("-sent"),uiRace=mode.endsWith("-ui");
        CountDownLatch closing=new CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicBoolean> sourceClosed=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean retiredBeforeUnlock=new java.util.concurrent.atomic.AtomicBoolean();
        if(uiRace){
            c.host.worker.submitSource(marker,handoff,source,3000,(binding,deadline)->{
                try{
                java.lang.reflect.Field field=MdoBrowserCdpBinding.class.getDeclaredField("peer");field.setAccessible(true);Object original=field.get(binding);
                java.lang.reflect.Field stop=MdoBrowserCdpPopupSource.class.getDeclaredField("closed");stop.setAccessible(true);sourceClosed.set((java.util.concurrent.atomic.AtomicBoolean)stop.get(original));return new byte[]{1};
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}
            },done::set);waitFor(()->done.get()!=null,c.host,true);check(done.get().code==null);
        }
        LongSupplier clock=()->{if(servicing.get()&&invalidated.compareAndSet(false,true)){
            if(uiRace){
                closing.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while(!sourceClosed.get().get()){if(System.nanoTime()>end)throw new AssertionError("UI did not close original source");Thread.yield();}
                retiredBeforeUnlock.set(handoff.draining());
            }else marker.lease().invalidate();
            if(spoof)return CdpPopupWorkerProbe.<RuntimeException>failingNavigationClock(new IOException("cdp_popup_source_retired"));
        }return c.host.time.get();};
        MdoBrowserCdpNetworks.Navigation ticket;boolean direct=mode.endsWith("-direct")||sent;
        if(direct){
            MdoBrowserCdpNetworks.Navigation original=MdoBrowserCdpNetworks.Navigation.attached("https://private.invalid/destination",null,1000,clock);ticket=original;
            c.host.worker.submitSource(marker,handoff,source,3000,(binding,deadline)->{
                binding.startSourceNavigation(original);servicing.set(!sent);
                try{
                    java.lang.reflect.Field field=MdoBrowserCdpBinding.class.getDeclaredField("peer");field.setAccessible(true);MdoBrowserCdpPopupSource peer=(MdoBrowserCdpPopupSource)field.get(binding);peer.turn(1000);
                    if(sent){check(c.backend.sourceRenderer.navigations==1);invalidated.set(true);marker.lease().invalidate();binding.current();}
                }
                catch(Throwable failure){caught.set(failure);}return new byte[]{1};
            },done::set);waitFor(()->done.get()!=null,c.host,true);
        }else{
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);java.lang.reflect.Field executor=MdoBrowserCdpWorker.class.getDeclaredField("executor");executor.setAccessible(true);
            ((java.util.concurrent.ThreadPoolExecutor)executor.get(c.host.worker)).execute(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Source close barrier expired");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            int before=c.backend.receives.get();
            try{
                waitFor(()->entered.getCount()==0,c.host,true);ticket=c.host.worker.navigatePopup(marker,handoff,source,"https://private.invalid/destination",null,1000);
                java.lang.reflect.Field timer=MdoBrowserCdpNetworks.Navigation.class.getDeclaredField("attachedClock");timer.setAccessible(true);timer.set(ticket,clock);servicing.set(true);
            }finally{release.countDown();}
            if(uiRace){waitFor(()->closing.getCount()==0,c.host,true);marker.lease().invalidate();}
            waitFor(()->invalidated.get()&&(c.backend.closes.get()!=0||c.backend.receives.get()>before+4),c.host,true);
        }
        Throwable failure=caught.get();
        JSONObject witness=new JSONObject().put("boundary",mode).put("scope_invalidated",invalidated.get()).put("caught",failure==null?JSONObject.NULL:failure.getClass().getName())
            .put("navigation_commands",c.backend.sourceRenderer.navigations).put("wire_closed",c.backend.closes.get()).put("retiring",handoff.retiring());
        System.err.println("Source navigation close witness: "+witness);
        if(direct&&!(failure instanceof MdoBrowserCdpPopupSource.Retired)||!direct&&c.backend.closes.get()!=(spoof?1:0))throw new AssertionError("Own source closure did not yield its native scoped receipt: "+witness);
        check(invalidated.get()&&!source.current()&&handoff.retiring()&&c.backend.sourceRenderer.navigations==(sent?1:0)&&c.backend.closes.get()==(spoof?1:0));
        if(uiRace&&!retiredBeforeUnlock.get())throw new AssertionError("Source stop was visible before its deliberate retirement receipt");
        check(ticket.result()!=null&&!ticket.result().ok&&ticket.result().code.equals("cdp_navigation_connection_closed")&&(uiRace||ticket.result().effectMayHaveOccurred==sent));
        navigationEvidence=witness.put("original_source",true).put("scoped_close",!spoof).put("unsent",!ticket.result().effectMayHaveOccurred).put("effect_may_have_occurred",ticket.result().effectMayHaveOccurred).put("ui_retired_before_unlock",retiredBeforeUnlock.get()).put("provider_qualified",false);
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
                int opens=c.backend.opens.get(),nativeOpens=c.backend.nativeOpens.get();c.backend.managedResumeMode="normal";c.backend.responseMode=mode.startsWith("source-navigation-")?(mode.endsWith("-pending")?"source-complete":"source-pass"):mode;
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
                            check(c.backend.sourceRenderer.calls==before&&binding.next()==null);binding.attachDownloads(connection);binding.attachDownloads(connection);
                            if(mode.startsWith("source-navigation-"))refused("cdp_popup_activation_required",()->binding.sourceNavigation());return new byte[]{1};
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
                            if(mode.startsWith("source-navigation-")){
                                waitFor(()->{try{return mode.endsWith("-pending")?downloads.list().length==1:c.backend.responsePasses.get()==1;}catch(IOException error){throw new AssertionError(error);}},c.host,true);
                                if(mode.startsWith("source-navigation-close-"))sourceNavigationClose(mode,c,marker,handoff,source);
                                else if(mode.startsWith("source-navigation-ui-"))sourceNavigationUi(mode,c,marker,handoff,source,downloads);else sourceNavigation(mode,c,marker,handoff,source,downloads);
                            }else if(mode.equals("source-pass")){waitFor(()->c.backend.responsePasses.get()==1,c.host,true);check(downloads.list().length==0&&c.backend.bodyClaims.get()==0);}
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
                                    if(ownerLost){waitFor(promoted::ended,c.host,true);check(!handoff.current());
                                        if(done.sourceCleanupConfirmed){
                                            check(c.backend.bodyCloses.get()==1&&c.backend.responseAborts.get()==1);
                                            MdoBrowserCdpDownloads.Release released=downloads.release(id);waitFor(released::finished,c.host,true);check(released.released()&&budget.used()==0);
                                        }else failure("download_source_cleanup_unconfirmed",()->downloads.release(id));}
                                    else{check(done.sourceCleanupConfirmed&&c.backend.bodyCloses.get()==1&&c.backend.responseAborts.get()==1&&handoff.current());
                                        if(mode.equals("source-stream-epoch"))check(!done.frameCurrent&&java.util.Arrays.asList("download_frame_changed","cdp_body_owner_changed").contains(done.error));else check(done.frameCurrent&&done.error.equals("cancelled"));
                                        c.backend.wire.add(ack(c.backend.lateRead.get(),"proof-real").put("result",new JSONObject().put("data","unread-late-bytes").put("eof",true)));int received=c.backend.receives.get();waitFor(()->c.backend.receives.get()>received+2,c.host,true);check(handoff.current());}
                                }else if(captured||mode.equals("source-epoch")||mode.equals("source-cancel")||mode.equals("source-controller"))check(!done.verified&&done.sourceCleanupConfirmed&&done.cacheCleanupConfirmed&&c.backend.bodyClaims.get()==0&&c.backend.responseAborts.get()==1);
                                else{check(done.verified&&done.sourceCleanupConfirmed&&done.received==3);MdoBrowserDownloadQueue.Read read=downloads.read(id,0,10);waitFor(read::finished,c.host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);}
                                if(!mode.equals("source-stream-owner")){MdoBrowserCdpDownloads.Release released=downloads.release(id);waitFor(released::finished,c.host,true);check(released.released()&&budget.used()==0);}
                            }
                            check(c.backend.documentRenderer==null&&candidate.retainedResponseCount()==0&&handoff.current()!= (mode.equals("source-stream-owner")||mode.startsWith("source-navigation-close-")));
                        }
                        check(c.backend.opens.get()==opens&&c.backend.nativeOpens.get()==nativeOpens&&c.backend.detaches.get()==0);
                        closeHandoff(c.host,c.backend,handoff,promoted);check(view.destroyed==1&&c.backend.closes.get()==1);
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
                    handoff.close();waitFor(promoted::ended,host,true);check(view.destroyed==1&&source.sends==1&&MdoBrowserCdpPopupNative.promotedUiOwners()==0);
                    try{product.checkCreation();throw new AssertionError("Native destruction replaced original protocol cleanup");}
                    catch(IllegalStateException expected){check(expected.getMessage().equals("native_popup_protection_busy"));}
                    backend.wire.add(detached("proof-real"));waitFor(()->!MdoBrowserCdpPopupBroker.creationBlocked(),host,true);product.checkCreation();
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
    private static void productChildDelivery(String mode)throws Exception {
        String variant=mode.substring("preparation-child-".length());
        Backend backend=new Backend();android.os.SystemClock.now=1000;
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=host.source();host.ready(network);try(NativeSource source=new NativeSource(host,network)){
                PopupProductHost product=new PopupProductHost(source.views);product.bindPreparation(source.view,network.adapter,source.core,host.worker);
                AtomicInteger prepared=new AtomicInteger(),allocations=new AtomicInteger(),accepted=new AtomicInteger(),failures=new AtomicInteger();
                String[] reason={null};boolean[] possible={false};boolean[] acknowledged={false},detached={false};WebView[] nativeChild={null};
                MdoBrowserCdpPopupPreparation.Failure failed=(code,effect)->{check(host.ui.onThread());failures.incrementAndGet();reason[0]=code;possible[0]=effect;};
                MdoBrowserCdpPopupNative[] original={null};MdoBrowserCdpPopupNative.Created[] created={null};MdoBrowserCdpPopupNative.Promotion[] promoted={null};
                product.prepare(prepared::incrementAndGet,failed);waitFor(()->prepared.get()==1,host,true);source.open();String pending=source.core.snapshot().id;
                Throwable primary=null;
                try{
                    if(variant.equals("request")){
                        failure("cdp_popup_native_request_changed",()->product.constructPrepared("foreign-callback","owned",1000,(s,c)->allocations.incrementAndGet(),failed));
                        for(int timeout:new int[]{0,3001})failure("cdp_popup_delivery_arguments_invalid",()->product.constructPrepared(pending,"owned",timeout,(s,c)->allocations.incrementAndGet(),failed));
                        failure("cdp_popup_delivery_arguments_invalid",()->product.constructPrepared(pending,"owned",1000,null,failed));
                        check(allocations.get()==0&&failures.get()==0&&product.preparationReady());return;
                    }
                    if(variant.equals("ready")){
                        backend.wire.add(attached("proof-real","owned"));waitFor(()->backend.guards.get()==1,host,true);
                        // Drain the protection completion, not only its worker-side request.
                        for(int i=0;i<10;i++){host.ui.drain();Thread.sleep(1);}
                    }
                    int before=WebView.constructions;Handler.fail=variant.equals("timer");
                    product.constructPrepared(pending,"owned",1000,(s,c)->{
                        check(host.ui.onThread());allocations.incrementAndGet();original[0]=s;created[0]=c;nativeChild[0]=c.view();
                        if(variant.equals("allocation-throw"))throw new IOException("controlled_allocation_delivery_failure");
                        if(variant.equals("reentrant"))try{product.constructPrepared(pending,"duplicate",1000,(unused,other)->{throw new AssertionError("Reentrant allocation");},failed);throw new AssertionError("Reentrant allocation accepted");}
                        catch(IOException expected){check(expected.getMessage().equals("cdp_popup_native_allocation_busy"));}
                        catch(Exception unexpected){throw new IOException(unexpected);}
                    },failed);Handler.fail=false;
                    if(java.util.Arrays.asList("timeout","owner","channel","replaced","timer").contains(variant)){
                        check(allocations.get()==0&&WebView.constructions==before);
                        if(variant.equals("owner"))product.browserPaused=true;
                        if(variant.equals("channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                        if(variant.equals("replaced")){check(source.core.dismiss(pending));source.ack();source.consumed=null;source.open();}
                        android.os.SystemClock.now+=variant.equals("timeout")?1100:100;
                        waitFor(()->failures.get()==1,host,true);check(!possible[0]&&allocations.get()==0&&WebView.constructions==before);
                        String expected=variant.equals("timeout")?"cdp_popup_native_allocation_timed_out":variant.equals("replaced")?"cdp_popup_native_request_changed":variant.equals("timer")?"cdp_popup_native_delivery_timer_unavailable":"cdp_popup_preparation_owner_changed";
                        check(reason[0].equals(expected));host.ui.drain();Handler.drain();check(failures.get()==1);return;
                    }
                    if(!variant.equals("ready")){check(allocations.get()==0&&WebView.constructions==before);backend.wire.add(attached("proof-real","owned"));}
                    waitFor(()->allocations.get()==1,host,true);check(WebView.constructions==before+1);
                    if(variant.equals("allocation-throw")){waitFor(()->failures.get()==1,host,true);check(possible[0]&&reason[0].equals("cdp_popup_native_allocation_callback_failed"));return;}
                    failure("cdp_popup_native_allocation_replayed",()->product.constructPrepared(pending,"replayed",1000,(s,c)->allocations.incrementAndGet(),failed));
                    WebView childView=created[0].view();source.adopting=childView;MdoBrowserPopups.Adoption adoption=original[0].adopt(created[0]);
                    source.ack();acknowledged[0]=true;check(adoption.ok()&&adoption.nativeConsumed()&&source.sends==1);
                    try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(childView,v->v==childView,new MdoBrowserCdpMarker.DocumentQuota());
                        MdoBrowserCdpPopupNative.Child child=original[0].child(adoption,created[0],adapter)){
                        backend.nativeTargets.put("target-proof-real",proofPeer(childView,variant.equals("proof-failure")?"missing":"success",null,null));
                        Handler.fail=variant.equals("proof-timer");
                        product.provePrepared(child,1500,proof->{
                            check(host.ui.onThread()&&proof==child);accepted.incrementAndGet();
                            if(variant.equals("proof-throw"))throw new IOException("controlled_proof_delivery_failure");
                            promoted[0]=product.handoff("owned",original[0],created[0],proof);
                        },failed);Handler.fail=false;
                        if(variant.equals("proof-replay"))failure("cdp_popup_native_proof_replayed",()->product.provePrepared(child,1500,p->{throw new AssertionError("Duplicate proof delivery");},failed));
                        if(variant.equals("proof-timeout")||variant.equals("proof-owner")){
                            waitFor(()->child.proof()!=null,host,false);check(accepted.get()==0&&failures.get()==0);
                            if(variant.equals("proof-owner"))product.browserPaused=true;
                            android.os.SystemClock.now+=variant.equals("proof-timeout")?1600:100;Handler.drain();host.ui.drain();
                        }
                        if(variant.startsWith("proof-")&&!variant.equals("proof-replay")){
                            waitFor(()->failures.get()==1,host,true);check(possible[0]&&accepted.get()==(variant.equals("proof-throw")?1:0)&&promoted[0]==null);
                            check(reason[0]!=null);host.ui.drain();Handler.drain();check(failures.get()==1);
                        }else{
                            waitFor(()->promoted[0]!=null,host,true);check(accepted.get()==1&&failures.get()==0&&child.handoff().transferred());
                            product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);check(promoted[0].current());
                            child.handoff().close();waitFor(promoted[0]::ended,host,true);check(childView.destroyed==1);
                        }
                        check(source.sends==1&&backend.resumes.get()==0&&backend.detaches.get()==0);
                    }
                }catch(Exception|Error error){primary=error;System.err.println("Child delivery "+variant+" allocations="+allocations.get()+" accepted="+accepted.get()+" failures="+failures.get()+" reason="+reason[0]);throw error;}
                finally{
                    try{
                        Handler.fail=false;product.retirePreparation("cdp_popup_preparation_cancelled");
                        waitFor(()->{
                            if(!acknowledged[0]&&source.consumed!=null){source.ack();acknowledged[0]=true;}
                            // Controlled provider notification follows its real native
                            // rejection/destruction receipt; it never substitutes for it.
                            if(!detached[0]&&original[0]!=null&&promoted[0]==null&&acknowledged[0]&&
                                    (source.adopting==null||nativeChild[0]!=null&&nativeChild[0].destroyed>0)){
                                try{backend.wire.add(detached("proof-real"));detached[0]=true;}catch(Exception error){throw new AssertionError(error);}
                            }
                            return product.preparationSettled();
                        },host,true);
                        check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0);
                        if(nativeChild[0]!=null)check(nativeChild[0].destroyed==1);
                        check(backend.threads.size()==1&&host.threads.containsAll(backend.threads));
                    }catch(Exception|Error error){
                        System.err.println("Child cleanup "+variant+" reason="+product.preparationCode()+" native="+(original[0]==null?null:original[0].failure())+" retained="+MdoBrowserCdpPopupNative.retainedUiOwners()+" retiring="+product.retiring()+" sends="+source.sends+" ack="+acknowledged[0]);
                        if(primary!=null)primary.addSuppressed(error);else throw error;
                    }
                }
            }
        }finally{Handler.fail=false;android.os.SystemClock.now=Long.MIN_VALUE;}
    }
    private static java.lang.ref.WeakReference<MdoBrowserCdpPopupNative.Consumption> unretainedConsumption(
            MdoBrowserCdpPopupNative source,MdoBrowserCdpPopupNative.Created child,MdoBrowserPopups.Adoption receipt,AtomicInteger callbacks)throws Exception {
        return new java.lang.ref.WeakReference<>(source.consume(child,receipt,new MdoBrowserCdpPopupNative.ConsumptionEvents(){
            public void consumed(MdoBrowserPopups.Adoption original){callbacks.incrementAndGet();}
            public void failed(String code){callbacks.incrementAndGet();}
        }));
    }
    private static void productAdoption(String mode)throws Exception {
        String variant=mode.substring("preparation-adoption-".length());Backend backend=new Backend();android.os.SystemClock.now=1000;
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=host.source();host.ready(network);try(NativeSource source=new NativeSource(host,network)){
                PopupProductHost product=new PopupProductHost(source.views);product.bindPreparation(source.view,network.adapter,source.core,host.worker);
                AtomicInteger prepared=new AtomicInteger(),received=new AtomicInteger(),proven=new AtomicInteger(),failures=new AtomicInteger();
                String[] reason={null};boolean[] possible={false},acknowledged={false},detached={false};
                MdoBrowserCdpPopupPreparation.Failure failed=(code,effect)->{check(host.ui.onThread());failures.incrementAndGet();reason[0]=code;possible[0]=effect;};
                MdoBrowserCdpPopupNative[] original={null};MdoBrowserCdpPopupNative.Created[] created={null};WebView[] exact={null};
                MdoBrowserCdpView[] adapter={null};MdoBrowserCdpPopupNative.Child[] proof={null};MdoBrowserCdpPopupNative.Promotion[] promoted={null};
                product.prepare(prepared::incrementAndGet,failed);waitFor(()->prepared.get()==1,host,true);source.open();
                product.constructPrepared(source.core.snapshot().id,"owned",1000,(s,c)->{original[0]=s;created[0]=c;exact[0]=c.view();},failed);
                backend.wire.add(attached("proof-real","owned"));waitFor(()->created[0]!=null,host,true);
                backend.nativeTargets.put("target-proof-real",proofPeer(exact[0],"success",null,null));Throwable primary=null;
                try{
                    if(variant.equals("arguments")){
                        for(int timeout:new int[]{0,3001})failure("cdp_popup_delivery_arguments_invalid",()->product.adoptPrepared(created[0],timeout,(s,c,r)->received.incrementAndGet(),failed));
                        failure("cdp_popup_delivery_arguments_invalid",()->product.adoptPrepared(created[0],1000,null,failed));
                        check(source.sends==0&&failures.get()==0&&product.preparationReady());return;
                    }
                    source.adopting=exact[0];
                    if(variant.equals("unretained")){
                        MdoBrowserPopups.Adoption receipt=original[0].adopt(created[0]);
                        java.lang.ref.WeakReference<MdoBrowserCdpPopupNative.Consumption> subscription=unretainedConsumption(original[0],created[0],receipt,received);
                        for(int i=0;i<40&&subscription.get()!=null;i++){System.gc();Thread.sleep(5);}check(subscription.get()==null);
                        source.ack();acknowledged[0]=true;check(received.get()==0&&receipt.ok());return;
                    }
                    if(variant.equals("inline"))source.sendHook=()->{source.ack();acknowledged[0]=true;};
                    if(variant.equals("inline-expired"))source.sendHook=()->{android.os.SystemClock.now+=1100;source.ack();acknowledged[0]=true;};
                    source.fail=variant.equals("send-failure");
                    Handler.fail=variant.equals("timer");
                    product.adoptPrepared(created[0],1000,(s,c,receipt)->{
                        check(host.ui.onThread()&&s==original[0]&&c==created[0]&&receipt.ok()&&receipt.nativeConsumed());received.incrementAndGet();
                        if(variant.equals("callback-throw"))throw new IOException("controlled_consumption_delivery_failure");
                        adapter[0]=MdoBrowserCdpView.network(exact[0],v->v==exact[0],new MdoBrowserCdpMarker.DocumentQuota());
                        proof[0]=s.child(receipt,c,adapter[0]);
                        try{product.provePrepared(proof[0],1500,p->{promoted[0]=product.handoff("owned",s,c,p);proven.incrementAndGet();},failed);}
                        catch(Exception error){throw new IOException(error);}
                    },failed);Handler.fail=false;source.fail=false;
                    check(source.sends==1);if(!variant.equals("inline"))check(received.get()==0);
                    if(variant.equals("replay"))failure("cdp_popup_native_adoption_replayed",()->product.adoptPrepared(created[0],1000,(s,c,r)->{throw new AssertionError("Duplicate native adoption");},failed));
                    if(variant.equals("subscription-replay")||variant.equals("wrong-owner")){
                        java.lang.reflect.Field field=MdoBrowserCdpPopupNative.Created.class.getDeclaredField("adoption");field.setAccessible(true);
                        MdoBrowserPopups.Adoption receipt=(MdoBrowserPopups.Adoption)field.get(created[0]);
                        failure(variant.equals("wrong-owner")?"cdp_popup_native_consumption_unavailable":"cdp_popup_native_consumption_replayed",()->
                            (variant.equals("wrong-owner")?source.source:original[0]).consume(created[0],receipt,new MdoBrowserCdpPopupNative.ConsumptionEvents(){
                                public void consumed(MdoBrowserPopups.Adoption value){throw new AssertionError("Replacement listener");}public void failed(String code){throw new AssertionError("Replacement listener");}}));
                        check(source.sends==1&&received.get()==0);
                    }
                    boolean interrupted=java.util.Arrays.asList("timeout","cancel","owner","channel","epoch","timer","native-close").contains(variant);
                    if(interrupted){
                        if(variant.equals("cancel"))product.retirePreparation("cdp_popup_preparation_cancelled");
                        if(variant.equals("owner"))product.browserPaused=true;
                        if(variant.equals("channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                        if(variant.equals("epoch"))check(source.views.navigation("root",source.view));
                        if(variant.equals("native-close"))created[0].close();
                        android.os.SystemClock.now+=variant.equals("timeout")?1100:100;
                        waitFor(()->failures.get()==1,host,true);check(possible[0]&&received.get()==0&&proven.get()==0&&exact[0].destroyed==0);
                    }
                    if(variant.equals("physical-owner"))network.adapter.marker().close();
                    if(variant.equals("late-ack"))android.os.SystemClock.now+=1100;
                    if(!acknowledged[0]){source.ack();acknowledged[0]=true;}
                    if(interrupted||java.util.Arrays.asList("callback-throw","physical-owner","late-ack","inline-expired","send-failure").contains(variant)){
                        waitFor(()->failures.get()==1,host,true);check(possible[0]&&received.get()==(variant.equals("callback-throw")?1:0)&&proven.get()==0&&promoted[0]==null);
                        check(reason[0]!=null);host.ui.drain();Handler.drain();check(failures.get()==1);
                    }else{
                        waitFor(()->proven.get()==1,host,true);check(received.get()==1&&failures.get()==0&&promoted[0].current());
                        product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);check(promoted[0].current());
                        proof[0].handoff().close();waitFor(promoted[0]::ended,host,true);
                    }
                    check(source.sends==1&&backend.resumes.get()==0&&backend.detaches.get()==0);
                }catch(Exception|Error error){primary=error;System.err.println("Adoption "+variant+" received="+received.get()+" proven="+proven.get()+" failures="+failures.get()+" reason="+reason[0]);throw error;}
                finally{
                    try{
                        Handler.fail=false;product.retirePreparation("cdp_popup_preparation_cancelled");
                        if(proof[0]!=null)proof[0].close();if(adapter[0]!=null)adapter[0].close();
                        boolean uncertain=variant.equals("physical-owner")||variant.equals("send-failure");
                        MdoBrowserCdpPopupWorker.Request finishing=uncertain?product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class):null;
                        waitFor(()->{
                            if(!acknowledged[0]&&source.consumed!=null){source.ack();acknowledged[0]=true;}
                            if(!detached[0]&&promoted[0]==null&&acknowledged[0]&&(source.adopting==null||exact[0].destroyed>0)){
                                try{backend.wire.add(detached("proof-real"));detached[0]=true;}catch(Exception error){throw new AssertionError(error);}}
                            if(finishing!=null){MdoBrowserCdpPopupWorker.Result result=finishing.result();
                                return result!=null&&result.terminal&&exact[0].destroyed==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==0;}
                            return product.preparationSettled();
                        },host,true);
                        check(exact[0].destroyed==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0);
                        if(uncertain){
                            MdoBrowserCdpPopupWorker.Result result=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class).result();
                            check(!result.sourceReleased&&result.code.equals(variant.equals("physical-owner")?"cdp_popup_broker_opener_lost":"cdp_popup_native_rejection_unconfirmed")&&!product.preparationSettled());
                            try{product.checkCreation();throw new AssertionError("Unknown process protection was released");}
                            catch(IllegalStateException expected){check(expected.getMessage().equals("native_popup_protection_busy"));}
                        }
                        check(backend.threads.size()==1&&host.threads.containsAll(backend.threads));
                    }catch(Exception|Error error){
                        MdoBrowserCdpPopupWorker.Result result=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class).result();
                        System.err.println("Adoption cleanup "+variant+" reason="+product.preparationCode()+" native="+original[0].failure()+" destroyed="+exact[0].destroyed+" retained="+MdoBrowserCdpPopupNative.retainedUiOwners()+" sourceReleased="+(result==null?null:result.sourceReleased)+" result="+(result==null?null:result.code));
                        if(primary!=null)primary.addSuppressed(error);else throw error;
                    }
                }
            }
        }finally{Handler.fail=false;android.os.SystemClock.now=Long.MIN_VALUE;}
    }
    private static JSONObject capturePopup(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
        waitFor(()->product.preparationReplies==1,host,true);JSONObject root=product.preparationReply.getJSONArray("frames").getJSONObject(0);
        check(product.preparationReply.getBoolean("ok")&&root.getString("id").equals("f0"));
        String local="d-00000000-0000-4000-8000-000000000001",nativeDocument=root.getString("document");
        if(variant.equals("capture-stale"))nativeDocument="fd-00000000-0000-4000-8000-000000000002";
        JSONObject viewport=new JSONObject().put("width",320).put("height",240),scroll=new JSONObject().put("x",0).put("y",0);
        org.json.JSONArray stamp=new org.json.JSONArray().put(new org.json.JSONArray().put("f0").put(nativeDocument).put(JSONObject.NULL)
            .put(false).put(true).put("readable").put(local).put(scroll).put(viewport).put(JSONObject.NULL));
        long epoch=product.views.epoch("owned",view);
        JSONObject context=new JSONObject().put("adapter","android-native-frames-v1").put("native_document",nativeDocument).put("document",local)
            .put("navigation",epoch).put("viewport",viewport).put("scroll",scroll).put("device_pixel_ratio",2).put("frame_stamp",stamp.toString());
        JSONObject request=new JSONObject().put("command","capture").put("agent",true).put("navigation",epoch).put("capture_key","popup-capture")
            .put("clip",new JSONObject().put("x",20).put("y",30).put("width",64).put("height",32).put("scale",1)).put("capture_context",context);
        MdoBrowserCdpCapture operation=MdoBrowserCdpCapture.parse(request);
        renderer.worlds.evaluation=(owner,expression)->{check(expression.startsWith(MdoBrowserCdpCapture.FENCE));return new JSONObject().put("ok",true);};
        if(variant.equals("capture-parent")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);}
        if(variant.equals("capture-hidden")||variant.equals("capture-chooser")){
            if(variant.equals("capture-hidden"))view.shown=false;else product.chooserAllowed=false;
            List<String> blocked=new ArrayList<>();
            try(MdoBrowserCdpCapture.Session stopped=product.captureSession("owned",operation,()->null)){stopped.check(3000,blocked::add);}
            check(blocked.size()==1&&"capture_changed".equals(blocked.get(0)));
            check(renderer.worlds.evaluations==0&&backend.opens.get()==1&&backend.nativeOpens.get()==1);
            return new JSONObject().put("refused_before_protocol",true).put("raster_started",false);
        }
        boolean cancelled=variant.equals("capture-cancel"),late=variant.equals("capture-late");
        if(cancelled||late)renderer.holdMethod="Page.getFrameTree";
        List<String> results=new ArrayList<>();MdoBrowserCdpCapture.Session[] original={null};
        MdoBrowserCdpCapture.Session session=product.captureSession("owned",operation,()->{if(variant.equals("capture-reentrant"))original[0].close();return null;});original[0]=session;
        try{
            session.check(3000,results::add);
            if(cancelled||late){waitFor(()->renderer.held!=null,host,true);check(results.isEmpty());
                if(cancelled)session.close();else check(product.views.navigation("owned",view));renderer.release(backend.wire);}
            waitFor(()->!results.isEmpty(),host,true);check(results.size()==1);
            boolean good=variant.equals("capture-ok")||variant.equals("capture-parent");String code=results.get(0);
            if(good){if(code!=null)throw new AssertionError("Protected popup capture failed: "+code);check(renderer.worlds.evaluations==2);}
            else{check(code!=null&&renderer.worlds.evaluations==(late?2:0));if(cancelled||variant.equals("capture-reentrant"))check(code.equals("capture_cancelled"));}
            check(backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.closes.get()==0&&sourceFree(product,"owned"));
            return new JSONObject().put("original_document_capture_fence",true).put("success",good).put("terminal_callbacks",results.size())
                .put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get())
                .put("world_fences",renderer.worlds.evaluations).put("raster_started",false).put("provider_qualified",false);
        }finally{session.close();}
    }
    private static boolean sourceFree(PopupProductHost product,String key){return product.popupHandoff(key).current()&&product.popupNetworkReady(key);}
    private static JSONObject preparePopupOpener(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer,MdoBrowserCdpPopupNative foreign)throws Exception {
        MdoBrowserPopups<WebView> core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
            public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}
        },new MdoBrowserPopups.Budget(8),()->product.views.owns("owned",view),()->product.views.epoch("owned",view),blocked->check(host.ui.onThread()));
        product.windows.put("owned",core);AtomicInteger ready=new AtomicInteger(),failed=new AtomicInteger();String[] code={null};
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);boolean queued=variant.startsWith("opener-queued-");
        boolean sourceClosed=variant.equals("opener-source-close")||variant.equals("opener-queued-source-close");
        final MdoBrowserCdpPopupBroker.Handoff originalOwner=product.popupHandoff("owned");
        if(variant.equals("opener-hidden"))view.shown=false;if(variant.equals("opener-chooser"))product.chooserAllowed=false;
        boolean refused=variant.equals("opener-hidden")||variant.equals("opener-chooser")||variant.equals("opener-foreign");
        try{
            if(variant.equals("opener-parent")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);}
            if(queued){java.lang.reflect.Field field=MdoBrowserCdpWorker.class.getDeclaredField("executor");field.setAccessible(true);
                ((java.util.concurrent.ThreadPoolExecutor)field.get(host.worker)).execute(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Queued opener barrier expired");}catch(InterruptedException error){Thread.currentThread().interrupt();}});
                waitFor(()->entered.getCount()==0,host,true);}
            if(variant.equals("opener-foreign")){failure("cdp_popup_attached_opener_invalid",()->product.foreignPreparation("owned",foreign));code[0]="cdp_popup_attached_opener_invalid";failed.incrementAndGet();}
            else product.prepareOwned("owned",ready::incrementAndGet,(reason,effect)->{check(!effect);code[0]=reason;failed.incrementAndGet();});
            if(queued){check(ready.get()==0&&failed.get()==0);if(sourceClosed)product.popupHandoff("owned").close();else product.retireOwnedPreparation("owned");release.countDown();}
            waitFor(()->ready.get()!=0||failed.get()!=0,host,true);
            if(refused||queued)check(ready.get()==0&&failed.get()==1&&code[0]!=null);
            else{
                if(ready.get()!=1||failed.get()!=0)throw new AssertionError("Protected opener preparation failed: "+code[0]);
                if(variant.equals("opener-reuse")){product.prepareOwned("owned",ready::incrementAndGet,(reason,effect)->{throw new AssertionError(reason);});check(ready.get()==2);}
                if(variant.startsWith("opener-effect")){
                    product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.preparationReplies==1,host,true);
                    JSONObject frame=product.preparationReply.getJSONArray("frames").getJSONObject(0);
                    boolean cancelEffect=variant.equals("opener-effect-cancel");if(cancelEffect){renderer.holdMethod="Runtime.evaluate";renderer.holdExpression="({ok:true,text:'managed'})";}
                    product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","evaluate").put("frame",frame.getString("id"))
                        .put("document",frame.getString("document")).put("script","({ok:true,text:'managed'})"));
                    if(cancelEffect){waitFor(()->renderer.held!=null,host,true);check(renderer.worlds.evaluations==1);product.retireOwnedPreparation("owned");renderer.release(backend.wire);}
                    waitFor(()->product.preparationReplies==2,host,true);
                    if(cancelEffect)check(!product.preparationReply.getBoolean("ok")&&product.preparationReply.getJSONObject("error").getBoolean("evaluation_may_have_occurred"));
                    else check(product.preparationReply.getBoolean("ok")&&product.preparationReply.getJSONObject("value").getString("text").equals("managed"));
                }
                if(variant.equals("opener-retire-busy")){
                    java.lang.reflect.Field field=MdoBrowserCdpWorker.class.getDeclaredField("executor");field.setAccessible(true);
                    ((java.util.concurrent.ThreadPoolExecutor)field.get(host.worker)).execute(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Retirement barrier expired");}catch(InterruptedException error){Thread.currentThread().interrupt();}});
                    waitFor(()->entered.getCount()==0,host,true);product.retireOwnedPreparation("owned");check(!product.ownedPreparationSettled("owned")&&!product.popupDocumentPending("owned"));
                    product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
                    check(product.preparationReplies==1&&!product.preparationReply.getBoolean("ok")&&product.preparationReply.getJSONObject("error").getString("native_code").equals("cdp_popup_page_restore_busy"));release.countDown();
                }
                if(sourceClosed){originalOwner.close();
                    try{waitFor(()->{product.resume();product.pollPopupPage("owned");product.pollPopupNetwork("owned");return product.ownedPreparationSettled("owned");},host,true);}
                    catch(Exception|Error error){System.err.println("Source retirement observation "+product.ownedPreparationDiagnostic("owned")+" popup="+product.popupDiagnostic("owned")+" closes="+backend.closes.get()+" site="+backend.closeSite);throw error;}}
            }
            check(backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.closes.get()==0);
        }finally{release.countDown();product.retireOwnedPreparation("owned");
            try{waitFor(()->{if(sourceClosed){product.resume();product.pollPopupPage("owned");product.pollPopupNetwork("owned");}return product.ownedPreparationSettled("owned");},host,true);}
            catch(Exception|Error error){System.err.println("Original opener cleanup "+variant+" "+product.ownedPreparationDiagnostic("owned"));throw error;}}
        view.shown=true;product.chooserAllowed=true;
        if(sourceClosed){check(!originalOwner.current()&&originalOwner.retiring()&&!originalOwner.detached()&&renderer.worlds.evaluations==0);
            return new JSONObject().put("original_source_retired",true).put("ready_callbacks",ready.get()).put("failure_callbacks",failed.get())
                .put("original_detach_pending",true).put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("native_effects",0).put("provider_qualified",false);}
        check(sourceFree(product,"owned"));
        int prior=product.preparationReplies;
        product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.preparationReplies==prior+1,host,true);
        if(!product.preparationReply.optBoolean("ok"))throw new AssertionError("Popup document did not recover after opener retirement: "+product.preparationReply);
        if(variant.equals("opener-rearm")){
            try{product.prepareOwned("owned",ready::incrementAndGet,(reason,effect)->{throw new AssertionError(reason);});waitFor(()->ready.get()==2,host,true);}
            finally{product.retireOwnedPreparation("owned");waitFor(()->product.ownedPreparationSettled("owned"),host,true);}
            int replies=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
            waitFor(()->product.preparationReplies==replies+1,host,true);check(product.preparationReply.getBoolean("ok"));
        }
        check(product.popupPageDocumentReady("owned")&&renderer.worlds.evaluations==(variant.startsWith("opener-effect")?1:0));
        return new JSONObject().put("original_opener_prepared",!refused&&!queued).put("ready_callbacks",ready.get()).put("failure_callbacks",failed.get())
            .put("document_restored_after_retirement",true).put("native_effects",renderer.worlds.evaluations).put("browser_connections",backend.opens.get())
            .put("native_proof_connections",backend.nativeOpens.get()).put("provider_qualified",false);
    }
    private static final class InputSurface implements MdoBrowserLifecycle.Factory<WebView>,MdoBrowserInput.Scheduler {
        final java.util.IdentityHashMap<WebView,List<String>> events=new java.util.IdentityHashMap<>();
        final java.util.TreeMap<Long,List<Runnable>> timers=new java.util.TreeMap<>();long time=100;Runnable afterDown;
        final LongSupplier clock;long sampled;
        InputSurface(){this(System::nanoTime);}InputSurface(LongSupplier clock){this.clock=clock;sampled=clock.getAsLong();}
        public long now(){long delta=TimeUnit.NANOSECONDS.toMillis(clock.getAsLong()-sampled);
            if(delta>0){time+=delta;sampled+=TimeUnit.MILLISECONDS.toNanos(delta);}return time;}
        public void post(Runnable task,long delay){timers.computeIfAbsent(now()+delay,k->new ArrayList<>()).add(task);}
        public void remove(Runnable task){for(List<Runnable> row:timers.values())row.removeIf(value->value==task);timers.entrySet().removeIf(row->row.getValue().isEmpty());}
        void pump(){
            // Native guards execute on a real worker. Polling their completion
            // must not fast-forward each newly posted50ms watchdog slice.
            for(int i=0;i<128;i++){
                if(timers.isEmpty()||timers.firstKey()>now())return;
                List<Runnable> due=timers.pollFirstEntry().getValue();for(Runnable task:due)task.run();
            }
            throw new AssertionError("Unbounded native input timer queue");
        }
        public MdoBrowserInput create(WebView view,LongSupplier epoch,BooleanSupplier admitted){
            List<String> sent=new ArrayList<>();events.put(view,sent);
            return new MdoBrowserInput(new MdoBrowserInput.Target(){
                private boolean send(String value){check(Looper.myLooper()==Looper.getMainLooper());sent.add(value);return true;}
                public long document(){return epoch.getAsLong();}public int width(){return view.getWidth();}public int height(){return view.getHeight();}
                public boolean interactive(){return admitted.getAsBoolean()&&view.shown;}public boolean focused(){return true;}
                public boolean acquireFocus(){return send("focus");}public boolean hover(long at,float x,float y){return send("hover:"+x+":"+y);}
                public boolean wheel(long at,float x,float y,float dx,float dy){return send("wheel:"+x+":"+y+":"+dx+":"+dy);}
                public boolean touch(int action,long down,long at,float x,float y){send("touch:"+action);if(action==0&&afterDown!=null){Runnable work=afterDown;afterDown=null;work.run();}return true;}
                public boolean key(boolean down,long started,long at,String key,int modifiers){return send("key:"+down+":"+key);}
                public boolean text(String value){return send("text:"+value);}
            },this);
        }
    }
    private static void inputSurfaceClock()throws Exception {
        int start=checks.get();java.util.concurrent.atomic.AtomicLong nanos=new java.util.concurrent.atomic.AtomicLong();
        InputSurface surface=new InputSurface(nanos::get);WebView view=new WebView();
        MdoBrowserInput input=surface.create(view,()->1,()->true);List<MdoBrowserInput.Result> results=new ArrayList<>();List<MdoBrowserInput.Checked> guards=new ArrayList<>();
        input.hover(20,30,(remaining,done)->{check(remaining>0&&remaining<=500);guards.add(done);},results::add);
        for(int i=0;i<256;i++)surface.pump();
        if(surface.time!=100||!results.isEmpty())throw new AssertionError("InputSurface advanced virtual time without clock ticks: "+surface.time);
        check(guards.size()==1&&input.busy()&&surface.events.get(view).isEmpty());
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));surface.pump();
        check(surface.now()==200&&results.isEmpty()&&input.busy());guards.get(0).complete(null);
        check(results.size()==1&&results.get(0).ok&&surface.events.get(view).size()==1&&input.cleanupConfirmed()&&surface.timers.isEmpty());

        nanos.set(0);InputSurface gesture=new InputSurface(nanos::get);WebView touched=new WebView();MdoBrowserInput tap=gesture.create(touched,()->1,()->true);
        List<MdoBrowserInput.Result> tapped=new ArrayList<>();tap.tap(10,20,tapped::add);for(int i=0;i<256;i++)gesture.pump();
        check(gesture.time==100&&tapped.isEmpty()&&gesture.events.get(touched).equals(java.util.Arrays.asList("touch:0")));
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(47));gesture.pump();check(tapped.isEmpty());
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(1));gesture.pump();
        check(tapped.size()==1&&tapped.get(0).ok&&gesture.events.get(touched).equals(java.util.Arrays.asList("touch:0","touch:1"))&&gesture.timers.isEmpty());

        nanos.set(0);InputSurface expired=new InputSurface(nanos::get);WebView target=new WebView();MdoBrowserInput held=expired.create(target,()->1,()->true);
        List<MdoBrowserInput.Result> failed=new ArrayList<>();List<MdoBrowserInput.Checked> waiting=new ArrayList<>();
        held.hover(20,30,(remaining,done)->waiting.add(done),failed::add);expired.pump();
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(501));expired.pump();check(failed.size()==1&&failed.get(0).code.equals("input_timed_out")&&!failed.get(0).effectMayHaveOccurred);
        waiting.get(0).complete(null);check(failed.size()==1&&expired.events.get(target).isEmpty()&&held.cleanupConfirmed()&&expired.timers.isEmpty());
        System.out.println("{\"passed\":true,\"checks\":"+(checks.get()-start)+",\"no_poll_time_acceleration\":true,\"original_input_deadline\":true,\"framework_verified\":false}");
    }
    private static JSONObject inputPopup(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer,InputSurface surface)throws Exception {
        product.windows.put("owned",new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
            public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}
        },new MdoBrowserPopups.Budget(8),()->product.views.owns("owned",view),()->product.views.epoch("owned",view),blocked->check(host.ui.onThread())));
        product.views.scaleChanged("owned",view,1);
        product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
        waitFor(()->product.preparationReplies==1,host,true);JSONObject root=product.preparationReply.getJSONArray("frames").getJSONObject(0);
        AtomicInteger prepared=new AtomicInteger();product.prepareOwned("owned",prepared::incrementAndGet,(code,effect)->{throw new AssertionError(code);});
        waitFor(()->prepared.get()==1,host,true);
        String local="d-00000000-0000-4000-8000-000000000001";
        String name=variant.substring("input-".length()),kind=name.equals("click")||name.equals("afterdown")?"click":name.equals("wheel")?"wheel":name.equals("key")?"key":name.equals("text")?"text":name.startsWith("focus")?"focus":"move";
        JSONObject request=new JSONObject().put("command","input").put("agent",true).put("navigation",product.views.epoch("owned",view)).put("kind",kind)
            .put("input_document",local).put("input_ref","e7")
            .put("frame_chain",new org.json.JSONArray().put(new JSONObject().put("frame",root.getString("id")).put("document",root.getString("document"))));
        if(kind.matches("move|click|wheel"))request.put("x",20).put("y",30);
        else request.put("focus_document",local).put("focus_ref","e7");
        if(kind.equals("key"))request.put("key","Enter");if(kind.equals("text"))request.put("text","owned text");if(kind.equals("wheel"))request.put("dx",0).put("dy",5);
        if(name.equals("stale"))request.getJSONArray("frame_chain").getJSONObject(0).put("document","fd-00000000-0000-4000-8000-000000000002");
        if(name.equals("hover"))failure("cdp_input_scope_unavailable",()->product.foreignInput("owned",request));
        renderer.worlds.evaluation=(owner,expression)->{
            if(expression.startsWith(MdoBrowserCdpInput.ROOT))return new JSONObject().put("ok",true);
            check(expression.startsWith(MdoBrowserCdpInput.TARGET));return new JSONObject().put("ok",true).put("point",new JSONObject().put("x",20).put("y",30));
        };
        if(name.equals("parent")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);}
        if(name.equals("hidden"))view.shown=false;if(name.equals("chooser"))product.chooserAllowed=false;
        boolean held=java.util.Arrays.asList("cancel","late","focus-timeout","channel").contains(name);
        if(held)renderer.holdMethod="Page.getFrameTree";
        if(name.equals("afterdown"))surface.afterDown=()->product.views.input("owned",view).invalidate("input_cancelled");
        long inputStartedClock=surface.now(),inputStartedAt=System.nanoTime();
        product.inputRpc("owned",request);
        if(name.equals("focus-retired"))product.popupHandoff("owned").close();
        if(held){
            waitFor(()->{surface.pump();return renderer.held!=null;},host,true);
            check(product.publicCounts.getOrDefault("input",0)==0);
            if(name.equals("cancel"))product.views.input("owned",view).invalidate("input_cancelled");
            if(name.equals("late"))check(product.views.navigation("owned",view));
            if(name.equals("channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
            if(name.equals("focus-timeout")){surface.time+=1000;surface.pump();}
            renderer.release(backend.wire);
        }
        waitFor(()->{surface.pump();return name.equals("channel")?product.views.inputClean("owned",view):product.publicCounts.containsKey("input");},host,true);
        boolean success=java.util.Arrays.asList("hover","click","wheel","key","text","focus","parent").contains(name);
        List<String> sent=surface.events.get(view);JSONObject result=product.publicReplies.get("input");
        if(name.equals("channel"))check(result==null&&product.publicCounts.getOrDefault("input",0)==0&&sent.isEmpty());
        else{
            if(result.optBoolean("accepted")!=success)throw new AssertionError("Protected input "+name+" failed: "+result+
                " virtual_ms="+(surface.time-inputStartedClock)+" wall_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-inputStartedAt));
            check(product.publicCounts.get("input")==1&&result.optBoolean("cleanup_confirmed"));
            if(result.optBoolean("effect_may_have_occurred")!=(success||name.equals("afterdown")||name.equals("focus-timeout")))throw new AssertionError("Native effect scope "+name+": "+result+" events="+sent);
            if(!success)check(result.optJSONObject("error")!=null);
            if(name.equals("cancel"))check(result.getJSONObject("error").getString("code").equals("input_cancelled"));
            if(name.equals("focus-timeout"))check(result.getJSONObject("error").getString("code").equals("input_timed_out"));
        }
        List<String> expected=new ArrayList<>();
        if(success){
            if(kind.equals("click")){expected.add("touch:0");expected.add("touch:1");}
            else if(kind.equals("key")){expected.add("key:true:Enter");expected.add("key:false:Enter");}
            else expected.add(kind.equals("focus")?"focus":kind.equals("text")?"text:owned text":kind.equals("wheel")?"wheel:20.0:30.0:0.0:5.0":"hover:20.0:30.0");
            check(result.optBoolean("target_focused")==kind.equals("focus")&&result.optBoolean("trusted_input")!=kind.equals("focus"));
        }else if(name.equals("afterdown")){expected.add("touch:0");expected.add("touch:3");}
        else if(name.equals("focus-timeout"))expected.add("focus");
        check(sent.equals(expected)&&surface.events.get(product.views.get("root")).isEmpty());
        check(backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.closes.get()==0);
        product.retireOwnedPreparation("owned");waitFor(()->product.ownedPreparationSettled("owned"),host,true);
        check(product.views.inputClean("owned",view)||name.equals("focus-retired")&&!product.views.owns("owned",view)&&view.destroyed==1);
        return new JSONObject().put("success",success).put("native_steps",sent.size()).put("terminal_callbacks",name.equals("channel")?0:product.publicCounts.get("input"))
            .put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("provider_qualified",false);
    }
    private static JSONObject navigatePopupProduct(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        String name=variant.substring("navigation-".length());
        Backend.SourceRenderer navigator=new Backend.SourceRenderer(name.equals("rejected")?"source-navigation-rejected":name.equals("no-commit")?"source-navigation-no-commit":"source-navigation-flow");
        renderer.frames.rootUrl=renderer.context.rootUrl=navigator.url;renderer.navigationPeer=navigator;renderer.wrongNavigationDocument=name.equals("mismatch");navigator.holdNavigation=name.equals("cancel");
        long epoch=product.views.epoch("owned",view);int worlds=renderer.worlds.created;
        if(name.equals("overlap"))return overlappingPopupNavigation(host,backend,product,view,renderer,navigator,worlds);
        if(name.equals("from-download")){
            check(product.popupPageDownloadOnly("owned")&&worlds==0);
            try{product.navigate("owned","goto");throw new AssertionError("Pending download was silently replaced");}
            catch(IllegalStateException expected){check(expected.getMessage().equals("download_pending"));}
            check(navigator.navigations==0&&backend.nativeNavigationCommands.get()==0);
            MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");String original=downloads.list()[0].id;
            downloads.cancel(original);waitFor(()->{try{return downloads.get(original).terminal()&&downloads.sourcesClean();}catch(IOException error){throw new AssertionError(error);}},host,true);
            check(downloads.get(original).sourceCleanupConfirmed&&backend.bodyClaims.get()==0);
        }
        String[] actions=name.equals("flow")?new String[]{"goto","back","forward","reload"}:new String[]{"goto"};String reference=null;int staleReferences=0;
        for(String action:actions){
            String previousReference=reference;
            JSONObject accepted=product.navigate("owned",action);check(accepted.optBoolean("accepted"));reference=accepted.getString("navigation_ref");
            if(name.equals("cancel")){
                waitFor(()->navigator.heldNavigation!=null,host,true);product.cancelNavigation("owned",reference);
            }
            if(name.equals("no-commit")){
                waitFor(()->{try{return product.inspectNavigation("owned").optBoolean("acknowledged");}catch(JSONException error){throw new AssertionError(error);}},host,true);android.os.SystemClock.now+=4000;
            }
            waitFor(()->{product.pollNavigation("owned");return product.navigationSettled("owned");},host,true);
            JSONObject state=product.inspectNavigation("owned");
            if(name.equals("mismatch")){
                check(!state.isNull("page_error")&&!product.popupPageDocumentReady("owned")&&renderer.worlds.created==worlds);break;
            }
            if(name.equals("cancel")){
                check(state.optBoolean("scope_revoked")&&state.optBoolean("dispatched")&&!state.optBoolean("ok")&&!product.popupPageDocumentReady("owned"));
                backend.wire.add(navigator.heldNavigation);navigator.heldNavigation=null;int before=backend.receives.get();waitFor(()->backend.receives.get()>before+2,host,true);
                product.pollNavigation("owned");check(!product.popupPageDocumentReady("owned")&&renderer.worlds.created==worlds&&navigator.navigations==1);break;
            }
            if(name.equals("no-commit")){check(!state.optBoolean("page_committed")&&!state.isNull("page_error"));break;}
            if(name.equals("rejected")){
                check(state.optBoolean("acknowledged")&&!state.optBoolean("ok")&&product.views.epoch("owned",view)==epoch);
                int before=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.preparationReplies==before+1,host,true);
                check(product.preparationReply.optBoolean("ok")&&product.popupPageDocumentReady("owned")&&navigator.navigations==1);break;
            }
            check(state.optBoolean("ok")&&state.optBoolean("page_committed")&&state.optBoolean("page_current")&&state.isNull("page_error")&&product.popupPageDocumentReady("owned"));
            int before=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list").put("navigation_ref",reference));waitFor(()->product.preparationReplies==before+1,host,true);
            if(!product.preparationReply.optBoolean("ok"))throw new AssertionError("Protected navigation "+action+" frame join: "+product.preparationReply);
            if(previousReference!=null){
                int prior=product.preparationReplies,queries=renderer.frames.snapshotCount;
                product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list").put("navigation_ref",previousReference));
                waitFor(()->product.preparationReplies==prior+1,host,true);check(!product.preparationReply.optBoolean("ok")&&product.preparationReply.getJSONObject("error").getString("native_code").equals("cdp_navigation_cancelled")&&renderer.frames.snapshotCount==queries);staleReferences++;
            }
            check(product.views.epoch("owned",view)>epoch);epoch=product.views.epoch("owned",view);
        }
        if(name.equals("callback")){
            product.nativePageCallback("owned");check(!product.popupPageDocumentReady("owned"));
            waitFor(()->{product.pollNavigation("owned");return product.popupPageDocumentReady("owned")&&product.navigationSettled("owned");},host,true);check(navigator.navigations==1&&product.views.epoch("owned",view)>epoch);
        }
        JSONObject worldState=null;
        if(name.startsWith("world-")){
            long originalEpoch=product.views.epoch("owned",view);JSONObject before=product.inspectNavigation("owned");
            check(before.getBoolean("page_ready")&&before.getBoolean("page_current")&&before.getString("id").equals(reference));
            int dispatched=backend.contextEvents.get();boolean defaults=name.equals("world-default");
            if(defaults){renderer.worlds.contexts.clear();backend.wire.add(new JSONObject().put("method","Runtime.executionContextsCleared").put("sessionId",renderer.root).put("params",new JSONObject()));}
            else{
                CdpWorldsProbe.Context exact=null;
                for(CdpWorldsProbe.Context candidate:renderer.worlds.contexts.values())if(candidate.source==null&&candidate.frame.equals("frame-owned")&&(exact==null||candidate.id>exact.id))exact=candidate;
                check(exact!=null);final CdpWorldsProbe.Context original=exact;renderer.worlds.contexts.values().removeIf(candidate->candidate==original);
                backend.wire.add(new JSONObject().put("method","Runtime.executionContextDestroyed").put("sessionId",renderer.root).put("params",new JSONObject().put("executionContextId",exact.id)));
            }
            waitFor(()->backend.contextEvents.get()>dispatched,host,true);
            JSONObject lost=product.inspectNavigation("owned"),document=product.inspectDocumentState("owned");
            check(lost.getBoolean("page_current")&&lost.getString("id").equals(reference)&&product.views.epoch("owned",view)==originalEpoch&&!document.getBoolean("ready"));
            if(lost.getBoolean("page_ready"))throw new AssertionError("Navigation reports a lost world ready: navigation="+lost+" document="+document);
            boolean recovered=name.equals("world-recover");
            if(recovered){
                int replies=product.preparationReplies;
                product.frameRpcNamed("owned","world-recover",new JSONObject().put("command","frames").put("agent",true).put("action","list").put("navigation_ref",reference));
                waitFor(()->product.preparationReplies==replies+1,host,true);
                JSONObject after=product.inspectNavigation("owned"),current=product.inspectDocumentState("owned");
                if(!product.publicReplies.get("world-recover").getBoolean("ok")||!current.getBoolean("ready")||!after.getBoolean("page_ready"))throw new AssertionError("Navigation association lost after explicit world recovery: navigation="+after+" document="+current);
                check(after.getBoolean("page_current")&&after.getString("id").equals(reference)&&product.views.epoch("owned",view)==originalEpoch);
            }
            check(navigator.navigations==1&&backend.nativeNavigationCommands.get()==1);
            worldState=new JSONObject().put("default_context",defaults).put("ready_after_loss",false).put("original_native_document_current",true).put("same_ui_epoch",true).put("same_navigation_reference",true).put("explicit_recovery",recovered).put("navigation_replayed",false);
        }
        check(renderer.worlds.evaluations==0&&backend.opens.get()==1&&backend.nativeOpens.get()==1);
        JSONObject evidence=new JSONObject().put("actual_product_navigation",true).put("guarded_new_document",true).put("navigation_commands",navigator.navigations)
            .put("new_worlds",renderer.worlds.created-worlds).put("stale_references_refused",staleReferences).put("ready",product.popupPageDocumentReady("owned")).put("late_callback",name.equals("callback"))
            .put("native_effects",0).put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("provider_qualified",false);
        if(worldState!=null)evidence.put("native_world_readiness",worldState);
        if(name.equals("from-download")){MdoBrowserCdpDownloads.Snapshot original=product.popupDownloads("owned").list()[0];check(original.terminal()&&original.sourceCleanupConfirmed&&backend.bodyClaims.get()==0&&!product.popupPageDownloadOnly("owned"));evidence.put("download_only_origin",true).put("pending_download_denied",true).put("original_download_retained",true).put("body_claims",0);}
        return evidence;
    }
    private static JSONObject overlappingPopupNavigation(CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer,Backend.SourceRenderer navigator,int worlds)throws Exception {
        navigator.holdNavigation=true;renderer.holdMethod="Page.createIsolatedWorld";
        JSONObject first=product.navigate("owned","goto");String oldReference=first.getString("navigation_ref");
        waitFor(()->navigator.heldNavigation!=null,host,true);
        try{product.navigate("owned","goto");throw new AssertionError("Unacknowledged navigation was superseded");}
        catch(IllegalStateException expected){check(expected.getMessage().equals("navigation_pending"));}
        check(navigator.navigations==1&&backend.nativeNavigationCommands.get()==1);
        navigator.holdNavigation=false;backend.wire.add(navigator.heldNavigation);navigator.heldNavigation=null;
        waitFor(()->{product.pollNavigation("owned");return renderer.held!=null;},host,true);
        JSONObject before=product.inspectNavigation("owned");check(before.optBoolean("page_current")&&!before.optBoolean("page_ready")&&!product.navigationSettled("owned"));
        JSONObject second=product.navigate("owned","goto");String currentReference=second.getString("navigation_ref");check(second.optBoolean("accepted")&&!currentReference.equals(oldReference));
        renderer.release(backend.wire);
        waitFor(()->{product.pollNavigation("owned");return product.navigationSettled("owned");},host,true);
        JSONObject state=product.inspectNavigation("owned");
        if(!state.optBoolean("page_ready"))throw new AssertionError("Replacement navigation did not become readable: "+state+" document="+product.inspectDocumentState("owned"));
        check(state.getString("id").equals(currentReference)&&state.optBoolean("page_current")&&state.isNull("page_error")&&product.popupPageDocumentReady("owned"));
        int replies=product.preparationReplies,queries=renderer.frames.snapshotCount;
        product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list").put("navigation_ref",oldReference));waitFor(()->product.preparationReplies==replies+1,host,true);
        check(!product.preparationReply.optBoolean("ok")&&product.preparationReply.getJSONObject("error").getString("native_code").equals("cdp_navigation_cancelled")&&renderer.frames.snapshotCount==queries);
        final int next=product.preparationReplies+1;
        product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list").put("navigation_ref",currentReference));waitFor(()->product.preparationReplies==next,host,true);
        check(product.preparationReply.optBoolean("ok")&&navigator.navigations==2&&backend.nativeNavigationCommands.get()==2&&renderer.worlds.evaluations==0&&backend.opens.get()==1&&backend.nativeOpens.get()==1);
        return new JSONObject().put("actual_product_navigation",true).put("guarded_new_document",true).put("navigation_commands",2).put("new_worlds",renderer.worlds.created-worlds)
            .put("stale_references_refused",1).put("ready",true).put("late_callback",false).put("pending_rejection",true).put("superseded_during_world_join",true)
            .put("native_effects",0).put("browser_connections",1).put("native_proof_connections",1).put("provider_qualified",false);
    }
    private static void naturalCommit(Backend backend,Backend.DocumentRenderer renderer,String loader,boolean commit)throws JSONException {
        String url="https://private.invalid/natural";renderer.frames.rootLoader=renderer.context.rootLoader=loader;renderer.frames.rootUrl=renderer.context.rootUrl=url;
        backend.wire.add(new JSONObject().put("method","Page.frameStartedNavigating").put("sessionId","proof-real").put("params",new JSONObject().put("frameId","frame-owned").put("loaderId",loader).put("navigationType","differentDocument").put("url",url)));
        if(commit)backend.wire.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId","proof-real").put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId",loader).put("url",url))));
    }
    private static JSONObject rejectedPopupControl(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        if(!variant.equals("release"))return acceptedPopupControl(variant,host,backend,product,view,renderer);
        boolean complete=variant.equals("takeover-complete"),read=variant.equals("takeover-read");String command=variant.equals("release")?"release":"takeover";
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");
        MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");String file=null;
        if(complete){backend.wire.add(responsePaused("response-extra-rejected-control","source-complete"));waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);file=downloads.list()[0].id;final String selected=file;
            downloads.accept(file,65536,3000);waitFor(()->{try{return downloads.get(selected).terminal();}catch(IOException error){throw new AssertionError(error);}},host,true);check(downloads.get(selected).verified);}
        int count=product.publicCounts.getOrDefault("frames",0);product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.publicCounts.getOrDefault("frames",0)==count+1,host,true);
        check(product.publicReplies.get("frames").getBoolean("ok"));String document=product.publicReplies.get("frames").getJSONArray("frames").getJSONObject(0).getString("document");
        MdoBrowserCdpMarker marker=product.popupDocumentMarker("owned");PopupProductHost.MdoBrowserDocument profile=product.documents.get("owned");
        java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> operation=new java.util.concurrent.atomic.AtomicReference<>();AtomicInteger callbacks=new AtomicInteger();java.util.concurrent.atomic.AtomicBoolean successfulRead=new java.util.concurrent.atomic.AtomicBoolean();
        if(read){renderer.holdMethod="Runtime.evaluate";renderer.holdExpression="({ok:true,text:'managed'})";product.runPopupPage("owned",false,false,result->{callbacks.incrementAndGet();successfulRead.set(result.code==null&&result.bytes!=null&&result.bytes.length==1&&result.bytes[0]==1);operation.set(result);});waitFor(()->renderer.held!=null,host,true);}
        int invalidations=product.cdpViews.invalidations,blocks=profile.blocks,cancellations=product.manualChooser.cancellations,worlds=renderer.worlds.created,constructions=WebView.constructions;
        try{
            for(int attempt=0;attempt<2;attempt++){
                try{product.publicControl("owned",command,false);throw new AssertionError("Unqualified native control was accepted");}
                catch(UnsupportedOperationException expected){check(expected.getMessage().equals("popup_takeover_unqualified"));}
                if(!marker.lease().current()||!product.popupPageDocumentReady("owned"))throw new AssertionError("Rejected "+command+" retired the original document: nativeOwner="+owner.current()+", manual="+product.manualControl("owned")+", callbacks="+callbacks.get());
                check(product.popupDocumentMarker("owned")==marker&&product.cdpViews.invalidations==invalidations&&profile.blocks==blocks&&product.manualChooser.cancellations==cancellations);
                check(owner.current()&&handoff.current()&&!handoff.retiring()&&!owner.humanTransferred()&&!product.manualControl("owned")&&product.views.inputClean("owned",view)&&view.destroyed==0);
                check(!product.publicCounts.containsKey("control")&&(!read||operation.get()==null));
            }
        }finally{if(renderer.held!=null)renderer.release(backend.wire);}
        if(read){waitFor(()->operation.get()!=null,host,true);check(callbacks.get()==1&&successfulRead.get()&&operation.get().bytes[0]==0);}
        int refreshed=product.publicCounts.getOrDefault("frames",0);product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.publicCounts.getOrDefault("frames",0)==refreshed+1,host,true);
        check(product.publicReplies.get("frames").getBoolean("ok")&&document.equals(product.publicReplies.get("frames").getJSONArray("frames").getJSONObject(0).getString("document")));
        if(file!=null){check(downloads.get(file).verified&&product.downloadBudget.used()==1);MdoBrowserDownloadQueue.Read bytes=downloads.read(file,0,10);waitFor(bytes::finished,host,true);byte[] actual=bytes.take();check(java.util.Arrays.equals(actual,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(actual,(byte)0);}
        check(renderer.worlds.created==worlds&&WebView.constructions==constructions&&backend.detaches.get()==0&&backend.nativeNavigationCommands.get()==0&&backend.closes.get()==0);
        return new JSONObject().put("original_control_branch",true).put("command",command).put("refusal_count",2).put("old_document_preserved",true).put("native_owner_preserved",true)
            .put("manual_unchanged",true).put("profile_unchanged",true).put("input_clean",true).put("control_side_effects",0).put("pending_read_preserved",read).put("completed_bytes",complete)
            .put("new_worlds",0).put("new_views",0).put("navigation_commands",0).put("detach_commands",0).put("provider_qualified",false).put("ui_published",false);
    }
    private static JSONObject acceptedPopupControl(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        boolean complete=variant.equals("takeover-complete"),read=variant.equals("takeover-read");
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");
        MdoBrowserCdpMarker marker=product.popupDocumentMarker("owned");MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");String file=null;
        int worlds=renderer.worlds.created,constructions=WebView.constructions;AtomicInteger callbacks=new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> operation=new java.util.concurrent.atomic.AtomicReference<>();
        if(complete){backend.wire.add(responsePaused("response-extra-public-human","source-complete"));waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);file=downloads.list()[0].id;final String selected=file;downloads.accept(file,65536,3000);waitFor(()->{try{return downloads.get(selected).verified;}catch(IOException error){throw new AssertionError(error);}},host,true);}
        if(read){renderer.holdMethod="Runtime.evaluate";renderer.holdExpression="({ok:true,text:'managed'})";product.runPopupPage("owned",false,false,result->{callbacks.incrementAndGet();operation.set(result);});waitFor(()->renderer.held!=null,host,true);}
        backend.humanDetachMode="flow";
        try{
            product.publicControl("owned","main-takeover","takeover",false);
            check(!marker.lease().current()&&!owner.current()&&!handoff.current());
            if(read){waitFor(()->operation.get()!=null,host,true);check(callbacks.get()==1&&operation.get().code!=null&&operation.get().bytes==null);renderer.release(backend.wire);}
            waitFor(()->{product.pollPublicControl("owned");return product.publicCounts.containsKey("main-takeover");},host,true);
            JSONObject receipt=product.publicReplies.get("main-takeover");check(receipt!=null&&receipt.getBoolean("ok")&&receipt.getBoolean("manual")&&receipt.getBoolean("network_cleanup_confirmed"));
            check(product.publicCounts.get("main-takeover")==1&&!product.publicControlPending("owned")&&owner.humanTransferred()&&product.manualControl("owned")&&handoff.humanDetached()&&product.popupProtocolDrained("owned")&&view.destroyed==0);
            int detaches=backend.detaches.get();product.publicControl("owned","repeated-takeover","takeover",false);check(product.publicCounts.get("repeated-takeover")==1&&product.publicReplies.get("repeated-takeover").getBoolean("manual")&&backend.detaches.get()==detaches);
            if(file!=null){check(downloads.get(file).verified);MdoBrowserDownloadQueue.Read bytes=downloads.read(file,0,10);waitFor(bytes::finished,host,true);byte[] actual=bytes.take();check(java.util.Arrays.equals(actual,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(actual,(byte)0);}
            check(renderer.worlds.created==worlds&&WebView.constructions==constructions&&backend.nativeNavigationCommands.get()==0);
            return new JSONObject().put("actual_public_takeover",true).put("original_control_branch",true).put("manual_committed",true).put("root_ack",true).put("native_event",true)
                .put("cleanup_confirmed",true).put("old_authority_revoked",true).put("reply_once",true).put("idempotent_without_replay",true).put("completed_bytes",complete).put("original_read_cancelled",read)
                .put("new_views",0).put("new_worlds",0).put("navigation_commands",0).put("provider_qualified",false).put("ui_published",false);
        }finally{if(renderer.held!=null)renderer.release(backend.wire);}
    }
    /** Real capture core and budget; only its native visual/raster callbacks and
     * dispatch timing are controlled. No bitmap/framework qualification. */
    private static final class ControlCapture implements MdoBrowserCapture.Target,MdoBrowserCapture.Scheduler,MdoBrowserCapture.Raster {
        final MdoBrowserCapture.Budget budget=new MdoBrowserCapture.Budget(1);
        final MdoBrowserCapture.Worker encoder=new MdoBrowserCapture.Worker(){public void execute(Runnable task){work=task;}public void remove(Runnable task){check(task==work);removals++;}};
        final MdoBrowserCapture core=new MdoBrowserCapture(this,this,encoder,budget);
        final java.util.Map<Runnable,Long> timers=new java.util.LinkedHashMap<>();
        final List<MdoBrowserCapture.Checked> guards=new ArrayList<>();final List<MdoBrowserCapture.Result> results=new ArrayList<>();
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Runnable ready,work,onResult;Thread worker;
        boolean hold,failClose;volatile int draws,encodes,closes,removals;volatile byte[] bytes;
        public MdoBrowserCapture.Snapshot snapshot(){return new MdoBrowserCapture.Snapshot("fd-12345678-1234-4123-8123-123456789abc",1,100,100,0,0,1);}
        public void ready(Runnable done){ready=done;}public MdoBrowserCapture.Raster draw(MdoBrowserCapture.Plan plan){draws++;return this;}
        public long now(){return android.os.SystemClock.elapsedRealtime();}
        public synchronized void post(Runnable task,long delay){timers.put(task,now()+delay);}public synchronized void at(Runnable task,long due){timers.put(task,due);}
        public synchronized void remove(Runnable task){timers.remove(task);}
        void drain(){for(int turns=0;turns<100;turns++){Runnable task=null;synchronized(this){for(java.util.Map.Entry<Runnable,Long> row:timers.entrySet())if(row.getValue()<=now()){task=row.getKey();break;}if(task!=null)timers.remove(task);}if(task==null)return;task.run();}throw new AssertionError("Unbounded capture delivery");}
        public byte[] encode(String format,int limit){encodes++;entered.countDown();if(hold)try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Original encoder wait expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}return bytes=new byte[]{7,8,9};}
        public void close(){closes++;if(failClose)throw new IllegalStateException("controlled_raster_release_failure");}
        void begin()throws Exception{core.capture(new MdoBrowserCapture.Plan(snapshot(),0,0,10,10,1,"png",128,10000),(remaining,done)->guards.add(done),result->{results.add(result);if(onResult!=null)onResult.run();});}
        void visual(){guards.get(0).complete(null);}void queued(){visual();ready.run();}
        void running()throws Exception{queued();hold=true;worker=new Thread(work,"original-capture-encoder");worker.start();if(!entered.await(3,TimeUnit.SECONDS))throw new AssertionError("Original encoder did not start");}
        void finishWorker()throws Exception{release.countDown();if(worker!=null){worker.join(3000);if(worker.isAlive())throw new AssertionError("Original encoder did not stop");}drain();}
        void assertCancelled(){check(results.size()==1&&!results.get(0).ok&&"capture_cancelled".equals(results.get(0).code)&&results.get(0).data==null);}
    }
    private static JSONObject humanResourceControl(String name,String directory,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer,InputSurface surface)throws Exception {
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");MdoBrowserCdpMarker marker=product.popupDocumentMarker("owned");
        ControlCapture capture=new ControlCapture();boolean foreign=name.equals("foreign-capture"),replaced=name.equals("capture-replaced"),channel=name.equals("channel-lost");
        boolean expired=name.equals("expire-running"),closed=name.equals("close-running"),failed=name.equals("cleanup-failed"),aborted=expired||closed||failed||replaced||channel;
        boolean running=name.equals("running")||expired||closed;boolean chooser=name.equals("chooser"),input=name.equals("input");
        int worlds=renderer.worlds.created,constructions=WebView.constructions;String code=null;
        java.nio.file.Path files=java.nio.file.Paths.get(directory).resolveSibling("popup-upload-files");AtomicInteger pickerReplies=new AtomicInteger(),inputReplies=new AtomicInteger();
        if(chooser){product.enableUpload("owned",files);product.openPicker("owned",(value,current)->{check(value==null);pickerReplies.incrementAndGet();});}
        MdoBrowserUploadStore.Scope upload=product.retainUploadScope("owned");
        PopupProductHost.CaptureEntry original=new PopupProductHost.CaptureEntry(foreign?product.views.get("root"):view,capture.core);product.captures.put("owned",original);
        if(replaced)capture.onResult=()->{PopupProductHost.CaptureEntry replacement=new PopupProductHost.CaptureEntry(view);replacement.capture.active=true;product.captures.put("owned",replacement);};
        if(channel)capture.onResult=()->product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
        MdoBrowserCdpPopupNative.HumanControl control=null;boolean waited=false,uploadCancelledAtBoundary=false;
        try{
            capture.begin();if(name.equals("visual"))capture.visual();else if(running)capture.running();
            else if(name.equals("queued")||failed){capture.queued();capture.failClose=failed;}
            else if(name.equals("encoded")||name.equals("postguard")){capture.queued();capture.work.run();check(capture.bytes!=null&&capture.bytes[0]==7&&capture.closes==1);if(name.equals("postguard")){capture.drain();check(capture.guards.size()==2&&capture.results.isEmpty());}}
            if(input){product.views.input("owned",view).tap(20,30,result->{inputReplies.incrementAndGet();check(!result.ok&&result.cleanupConfirmed);});surface.pump();check(surface.events.get(view).equals(java.util.Arrays.asList("touch:0")));}
            if(foreign||replaced||channel){
                failure("cdp_popup_human_owner_invalid",()->product.beginPopupControlAndCancel("owned"));code="cdp_popup_human_owner_invalid";
                uploadCancelledAtBoundary=!upload.live();
                check(!owner.humanTransferred()&&!product.manualControl("owned")&&upload.live()&&product.manualChooser.cancellations==0);
                if(foreign){check(owner.current()&&handoff.current()&&marker.lease().current()&&capture.results.isEmpty()&&capture.core.busy());}
                else {capture.assertCancelled();check(!owner.current()&&!marker.lease().current());if(replaced)check(product.captures.get("owned")!=original&&product.captures.get("owned").capture.active);}
            }else{
                control=product.beginPopupControlAndCancel("owned");final MdoBrowserCdpPopupNative.HumanControl receipt=control;capture.assertCancelled();
                uploadCancelledAtBoundary=!upload.live();
                check(!upload.live()&&product.retainedUploadDataLive("owned")&&!owner.humanTransferred()&&!product.manualControl("owned")&&!marker.lease().current());
                if(input)check(inputReplies.get()==1&&surface.events.get(view).equals(java.util.Arrays.asList("touch:0","touch:3")));
                if(chooser)check(pickerReplies.get()==1);
                waited=name.equals("visual")||running||failed;
                if(waited){check(!product.popupControlReady("owned",control));failure("cdp_popup_human_cleanup_pending",()->product.completePopupControl("owned",receipt));check(!owner.humanTransferred()&&view.destroyed==0);}
                if(expired){android.os.SystemClock.now+=5000;control.expire();code="cdp_popup_human_control_expired";}
                else if(closed){product.retire("owned",view);waitFor(()->view.destroyed==1,host,true);failure("cdp_popup_human_owner_invalid",control::commit);code="cdp_popup_human_owner_invalid";}
                else if(failed){check(!capture.core.busy()&&!capture.core.cleanupConfirmed()&&capture.budget.pending()==1);control.close();code="cdp_popup_human_control_cancelled";}
                if(name.equals("visual"))capture.ready.run();
                if(running)capture.finishWorker();else {if(capture.work!=null)capture.work.run();capture.drain();}
                if(!aborted){
                    waitFor(()->product.popupControlReady("owned",receipt),host,true);check(capture.core.cleanupConfirmed()&&capture.budget.pending()==0);
                    backend.humanDetachMode="flow";product.completePopupControl("owned",control);waitFor(handoff::humanDetached,host,true);product.pollPopupNetwork("owned");
                    check(owner.humanTransferred()&&product.manualControl("owned")&&view.destroyed==0&&product.popupProtocolDrained("owned")&&product.retainedUploadDataLive("owned"));
                    for(MdoBrowserCapture.Checked late:capture.guards)late.complete(null);if(capture.ready!=null)capture.ready.run();capture.assertCancelled();
                }
                if(capture.bytes!=null)for(byte value:capture.bytes)check(value==0);
                if(name.equals("queued"))check(capture.encodes==0&&capture.closes==1&&capture.removals==1);
                if(running||name.equals("encoded")||name.equals("postguard"))check(capture.encodes==1&&capture.closes==1);
            }
            if(aborted){waitFor(()->view.destroyed==1&&owner.nativeDestroyed(),host,true);check(!owner.humanTransferred()&&!product.manualControl("owned"));}
            check(capture.core.cleanupConfirmed()==(!failed&&!foreign)&&capture.budget.pending()==(failed||foreign?1:0));
            check(capture.results.size()==(foreign?0:1)&&capture.results.stream().allMatch(result->!result.ok&&result.data==null));
            check(product.retainedUploadDataLive("owned")==!aborted);
            check(renderer.worlds.created==worlds&&WebView.constructions==constructions&&backend.nativeNavigationCommands.get()==0);
            return new JSONObject().put("actual_resource_cancellation",true).put("variant",name).put("actual_capture_core",true).put("capture_callback_once",!foreign)
                .put("resource_waited",waited).put("capture_clean",capture.core.cleanupConfirmed()).put("retained_budget",capture.budget.pending())
                .put("manual_committed",owner.humanTransferred()).put("old_document_preserved",foreign).put("native_destroyed",view.destroyed).put("code",code==null?JSONObject.NULL:code)
                .put("replacement_untouched",replaced).put("upload_scope_cancelled",uploadCancelledAtBoundary).put("upload_data_preserved",product.retainedUploadDataLive("owned"))
                .put("actual_input_cancelled",input).put("actual_picker_cancelled",chooser).put("new_views",0).put("new_worlds",0).put("navigation_commands",0).put("provider_qualified",false).put("ui_published",false);
        }finally{
            capture.release.countDown();capture.finishWorker();capture.core.cancel();if(capture.ready!=null)capture.ready.run();capture.drain();
            if(foreign)product.captures.remove("owned",original);
            if(chooser){if(view.destroyed==0&&product.views.owns("owned",view))product.retire("owned",view);waitFor(()->view.destroyed==1,host,true);product.stopUpload(files);}
        }
    }
    private static JSONObject publicHumanRequest(String name,String directory,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer,InputSurface surface)throws Exception {
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");MdoBrowserCdpMarker marker=product.popupDocumentMarker("owned");
        PopupProductHost.MdoBrowserDocument originalProfile=product.documents.get("owned");WebView replacementView=name.equals("view-after")?new WebView():null;
        boolean timer=name.equals("timer"),foreign=name.equals("foreign-capture"),input=name.equals("input"),chooser=name.equals("chooser"),running=name.equals("capture-running"),postguard=name.equals("capture-postguard"),failed=name.equals("capture-failure");
        boolean pre=java.util.Arrays.asList("stop","close","channel","pause","timeout").contains(name);
        boolean after=name.endsWith("-after"),heldEvent=name.equals("held-event"),heldAck=name.equals("held-ack"),rejected=name.equals("rejected");
        boolean visual=pre||name.equals("pending-duplicate")||name.equals("capture-visual");
        ControlCapture capture=new ControlCapture();java.nio.file.Path files=java.nio.file.Paths.get(directory).resolveSibling("popup-upload-files");
        AtomicInteger inputReplies=new AtomicInteger(),pickerReplies=new AtomicInteger();int worlds=renderer.worlds.created,constructions=WebView.constructions;String code=null;
        if(chooser){product.enableUpload("owned",files);product.openPicker("owned",(value,current)->{check(value==null);pickerReplies.incrementAndGet();});}
        if(!timer){product.captures.put("owned",new PopupProductHost.CaptureEntry(foreign?product.views.get("root"):view,capture.core));capture.begin();if(visual)capture.visual();else if(running)capture.running();else if(postguard){capture.queued();capture.work.run();capture.drain();check(capture.guards.size()==2);}else if(failed){capture.queued();capture.failClose=true;}}
        if(input){product.views.input("owned",view).tap(20,30,result->{check(!result.ok&&result.cleanupConfirmed);inputReplies.incrementAndGet();});surface.pump();}
        backend.humanDetachMode=rejected?"rejected":after||heldEvent?"ack-only":heldAck?"no-ack":"flow";
        try{
            if(timer){Handler.fail=true;try{failure("network_control_scheduler_unavailable",()->product.publicControl("owned","main-request","takeover",false));}finally{Handler.fail=false;}check(marker.lease().current()&&owner.current()&&handoff.current()&&!product.publicControlPending("owned")&&!product.manualControl("owned"));code="network_control_scheduler_unavailable";}
            else if(foreign){product.publicControl("owned","main-request","takeover",false);JSONObject result=product.publicReplies.get("main-request");check(result!=null&&!result.getBoolean("ok")&&!result.getBoolean("manual")&&!result.has("network_cleanup_confirmed"));check(result.getJSONObject("error").getString("code").equals("cdp_popup_human_owner_invalid")&&marker.lease().current()&&owner.current()&&capture.results.isEmpty()&&capture.core.busy());code="cdp_popup_human_owner_invalid";}
            else{
                product.publicControl("owned","main-request","takeover",false);capture.assertCancelled();check(!marker.lease().current()&&!owner.current());
                if(input)check(inputReplies.get()==1&&surface.events.get(view).equals(java.util.Arrays.asList("touch:0","touch:3")));
                if(chooser)check(pickerReplies.get()==1);
                if(visual||running||failed){check(product.publicControlPending("owned")&&!owner.humanTransferred()&&!product.manualControl("owned")&&!product.publicCounts.containsKey("main-request"));}
                if(name.equals("pending-duplicate")){
                    for(String command:new String[]{"takeover","release","navigate","reload","frames","input","upload","capture"})try{product.requireNetworkCommand("owned",command,false);throw new AssertionError("Pending human control admitted "+command);}catch(IllegalStateException error){check(error.getMessage().equals("browser_control_pending"));}
                    product.requireNetworkCommand("owned","state",false);product.requireNetworkCommand("owned","downloads",false);
                }
                if(name.equals("stop")){product.stopNetwork("owned");code="network_control_cancelled";}
                else if(name.equals("close")){product.publicClose("owned","other-close");code="network_owner_changed";}
                else if(name.equals("channel")){product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());code="network_owner_changed";}
                else if(name.equals("pause")){product.pausePublicControls();code="network_control_cancelled";}
                else if(name.equals("timeout")||failed){android.os.SystemClock.now+=5000;code="network_control_timeout";}
                if(after||heldEvent||heldAck||rejected){
                    waitFor(()->{product.pollPublicControl("owned");return owner.humanTransferred();},host,true);
                    check(product.manualControl("owned"));
                    if(after||heldEvent){waitFor(()->handoff.humanDetachResult()!=null,host,true);check(handoff.humanDetachResult().ok&&!handoff.detached()&&!product.publicCounts.containsKey("main-request"));}
                    if(heldAck){waitFor(()->backend.heldHumanDetach!=null&&handoff.detached(),host,true);check(!product.publicCounts.containsKey("main-request"));backend.wire.add(backend.heldHumanDetach);}
                    if(heldEvent)backend.wire.add(detached("proof-real"));
                    if(name.equals("stop-after")){product.stopNetwork("owned");code="network_control_cancelled";}
                    else if(name.equals("timeout-after")){android.os.SystemClock.now+=5000;code="network_control_timeout";}
                    else if(name.equals("close-after")){product.publicClose("owned","late-close");code="network_owner_changed";}
                    else if(name.equals("pause-after")){product.pausePublicControls();code="network_control_cancelled";}
                    else if(name.equals("channel-after")){product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());code="network_owner_changed";}
                    else if(name.equals("profile-after")){PopupProductHost.MdoBrowserDocument replacement=new PopupProductHost.MdoBrowserDocument(view);replacement.assigned=true;product.documents.put("owned",replacement);code="network_owner_changed";}
                    else if(name.equals("view-after")){product.publicClose("owned","replacement-close");waitFor(()->view.destroyed==1,host,true);product.views.put("owned",replacementView);PopupProductHost.MdoBrowserDocument replacement=new PopupProductHost.MdoBrowserDocument(replacementView);replacement.assigned=true;product.documents.put("owned",replacement);product.root.addView(replacementView);code="network_owner_changed";}
                }
                if(visual&&capture.ready!=null)capture.ready.run();if(running)capture.finishWorker();else {if(capture.work!=null)capture.work.run();capture.drain();}
                boolean lost=name.equals("channel")||name.equals("channel-after");
                waitFor(()->{product.pollPublicControl("owned");return !product.publicControlPending("owned");},host,true);
                if(lost)check(!product.publicCounts.containsKey("main-request"));
                else{
                    check(product.publicCounts.getOrDefault("main-request",0)==1);JSONObject result=product.publicReplies.get("main-request");check(result!=null);
                    boolean successful=code==null&&!rejected;
                    if(successful)check(result.getBoolean("ok")&&result.getBoolean("manual")&&result.getBoolean("network_cleanup_confirmed"));
                    else{check(!result.getBoolean("ok"));String actual=result.getJSONObject("error").getString("code");if(rejected){check(actual.equals("cdp_command_rejected"));code=actual;}else check(actual.equals(code));}
                    if(java.util.Arrays.asList("stop-after","timeout-after","pause-after").contains(name)||rejected)check(result.getBoolean("manual"));
                }
                if(capture.bytes!=null)for(byte value:capture.bytes)check(value==0);
                if(after&&code!=null&&!java.util.Arrays.asList("stop-after","timeout-after","pause-after").contains(name)){waitFor(()->view.destroyed==1,host,true);check(!product.views.owns("owned",view));}
                if(pre||failed)check(!owner.humanTransferred());
                if(name.equals("view-after"))check(product.views.get("owned")==replacementView&&replacementView.destroyed==0&&product.views.get("root").destroyed==0);
                if(name.equals("profile-after"))check(originalProfile.closed&&originalProfile.destroyed==1);
                if(heldEvent||heldAck||name.equals("stop-after")){if(!handoff.detached())backend.wire.add(detached("proof-real"));waitFor(handoff::detached,host,true);product.pollPopupNetwork("owned");}
            }
            check(renderer.worlds.created==worlds&&WebView.constructions==constructions&&backend.nativeNavigationCommands.get()==0);
            boolean replied=product.publicCounts.getOrDefault("main-request",0)==1;JSONObject result=product.publicReplies.get("main-request");
            return new JSONObject().put("actual_public_request",true).put("variant",name).put("original_control_branch",true).put("actual_capture_core",!timer)
                .put("physical_committed",owner.humanTransferred()).put("reply_once",replied).put("reply_ok",result!=null&&result.optBoolean("ok"))
                .put("reply_manual",result!=null&&result.optBoolean("manual")).put("old_authority_revoked",!marker.lease().current()).put("code",code==null?JSONObject.NULL:code)
                .put("actual_human_network",true).put("wire_failed",backend.closes.get()>0).put("provider_qualified",false).put("ui_published",false);
        }finally{
            Handler.fail=false;capture.release.countDown();capture.finishWorker();capture.core.cancel();if(capture.ready!=null)capture.ready.run();capture.drain();
            if(replacementView!=null&&product.views.owns("owned",replacementView))product.retire("owned",replacementView);
            product.browserPaused=false;if(chooser){if(view.destroyed==0&&product.views.owns("owned",view))product.retire("owned",view);waitFor(()->view.destroyed==1,host,true);product.stopUpload(files);}
        }
    }
    private static JSONObject stagedHumanControl(String name,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer,InputSurface surface)throws Exception {
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");MdoBrowserCdpMarker marker=product.popupDocumentMarker("owned");
        boolean prepared=java.util.Arrays.asList("prepared-cancel","prepared-expire","prepared-resume","duplicate","wrong-view","timer-rejected").contains(name);
        boolean aborted=java.util.Arrays.asList("cancel-started","expire-started","resume-started","close-started","channel-changed").contains(name);
        boolean input=name.equals("input"),read=name.equals("read"),complete=name.equals("complete"),capture=name.equals("capture-pending");
        int worlds=renderer.worlds.created,constructions=WebView.constructions;String code=null,file=null;MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");
        MdoBrowserCdpPopupNative.HumanControl control=null;
        if(name.equals("wrong-view")){
            failure("cdp_popup_human_owner_invalid",()->handoff.prepareControl(owner,product.views.get("root")));code="cdp_popup_human_owner_invalid";
        }else if(name.equals("timer-rejected")){
            Handler.fail=true;try{failure("cdp_popup_human_timer_unavailable",()->handoff.prepareControl(owner,view));code="cdp_popup_human_timer_unavailable";}finally{Handler.fail=false;}
        }else if(prepared){
            control=handoff.prepareControl(owner,view);final MdoBrowserCdpPopupNative.HumanControl original=control;
            check(control.current()&&!control.started()&&!control.ready()&&!control.committed()&&control.failure()==null);
            failure("cdp_popup_human_control_not_started",control::commit);
            check(marker.lease().current()&&owner.current()&&handoff.current()&&!handoff.retiring()&&view.destroyed==0&&!product.manualControl("owned"));
            if(name.equals("duplicate")){failure("cdp_popup_human_owner_invalid",()->handoff.prepareControl(owner,view));code="cdp_popup_human_owner_invalid";control.close();}
            else if(name.equals("prepared-cancel")){control.close();code="cdp_popup_human_control_cancelled";}
            else {android.os.SystemClock.now+=5000;if(name.equals("prepared-resume")){failure("cdp_popup_human_control_expired",original::start);MdoBrowserCdpPopupNative.reconcileRetirements();}else Handler.drain();code="cdp_popup_human_control_expired";}
            check(!control.current()&&!control.committed());String terminal=control.failure();check(terminal.equals(name.equals("duplicate")?"cdp_popup_human_control_cancelled":code));failure(terminal,original::start);
            // A new explicit reservation is not a replay of this dead receipt.
            MdoBrowserCdpPopupNative.HumanControl fresh=handoff.prepareControl(owner,view);check(fresh!=control&&fresh.current());fresh.close();
        }else if(aborted&&!name.equals("channel-changed")){
            control=product.beginPopupControl("owned");check(control.started()&&control.current()&&!owner.current()&&!handoff.current()&&handoff.retiring()&&!marker.lease().current());
            if(name.equals("close-started")){product.retire("owned",view);waitFor(()->view.destroyed==1,host,true);failure("cdp_popup_human_owner_invalid",control::commit);code="cdp_popup_human_owner_invalid";}
            else if(name.equals("cancel-started")){control.close();code="cdp_popup_human_control_cancelled";}
            else {android.os.SystemClock.now+=5000;if(name.equals("resume-started"))MdoBrowserCdpPopupNative.reconcileRetirements();else Handler.drain();code="cdp_popup_human_control_expired";}
        }else{
            java.util.concurrent.atomic.AtomicReference<MdoBrowserInput.Result> inputResult=new java.util.concurrent.atomic.AtomicReference<>();AtomicInteger inputCallbacks=new AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> readResult=new java.util.concurrent.atomic.AtomicReference<>();AtomicInteger readCallbacks=new AtomicInteger();
            if(complete){backend.wire.add(responsePaused("response-extra-staged-human","source-complete"));waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);file=downloads.list()[0].id;final String selected=file;downloads.accept(file,65536,3000);
                waitFor(()->{try{return downloads.get(selected).verified;}catch(IOException error){throw new AssertionError(error);}},host,true);}
            if(input){product.views.input("owned",view).tap(20,30,result->{inputCallbacks.incrementAndGet();inputResult.set(result);});surface.pump();
                check(product.views.inputBusy("owned",view)&&surface.events.get(view).equals(java.util.Arrays.asList("touch:0")));failure("cdp_popup_human_owner_invalid",()->handoff.takeControl(owner));check(owner.current()&&marker.lease().current());}
            if(read){renderer.holdMethod="Runtime.evaluate";renderer.holdExpression="({ok:true,text:'managed'})";product.runPopupPage("owned",false,false,result->{readCallbacks.incrementAndGet();readResult.set(result);});waitFor(()->renderer.held!=null,host,true);}
            if(capture){PopupProductHost.CaptureEntry retained=new PopupProductHost.CaptureEntry(view);retained.capture.active=true;product.captures.put("owned",retained);}
            control=product.beginPopupControl("owned");final MdoBrowserCdpPopupNative.HumanControl original=control;
            check(control.current()&&control.started()&&!control.committed()&&!owner.current()&&!handoff.current()&&handoff.retiring()&&!marker.lease().current());
            check(!product.manualControl("owned")&&!owner.humanTransferred()&&!owner.ended()&&view.destroyed==0);
            if(input||capture){check(!product.popupControlReady("owned",control));failure("cdp_popup_human_cleanup_pending",()->product.completePopupControl("owned",original));check(!owner.humanTransferred()&&view.destroyed==0&&control.current());}
            if(input){product.views.stop("owned",view);check(inputCallbacks.get()==1&&!inputResult.get().ok&&inputResult.get().cleanupConfirmed&&surface.events.get(view).equals(java.util.Arrays.asList("touch:0","touch:3")));}
            if(capture)product.captures.get("owned").capture.active=false; // controlled capture peer, not encoder qualification
            if(read){waitFor(()->readResult.get()!=null,host,true);check(readCallbacks.get()==1&&readResult.get().code!=null&&readResult.get().bytes==null&&!readResult.get().effectMayHaveOccurred);renderer.release(backend.wire);}
            if(name.equals("channel-changed")){
                product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());failure("cdp_popup_human_owner_invalid",()->product.completePopupControl("owned",original));code="cdp_popup_human_owner_invalid";
            }else{
                waitFor(()->product.popupControlReady("owned",original),host,true);backend.humanDetachMode="flow";product.completePopupControl("owned",control);
                check(control.committed()&&control.failure()==null&&owner.humanTransferred()&&product.manualControl("owned"));failure("cdp_popup_human_control_replayed",control::commit);control.close();
                waitFor(handoff::humanDetached,host,true);product.pollPopupNetwork("owned");check(product.popupProtocolDrained("owned")&&view.destroyed==0&&!owner.nativeDestroyed());
                if(file!=null){check(downloads.get(file).verified&&product.downloadBudget.used()==1);MdoBrowserDownloadQueue.Read bytes=downloads.read(file,0,10);waitFor(bytes::finished,host,true);byte[] actual=bytes.take();check(java.util.Arrays.equals(actual,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(actual,(byte)0);}
                android.os.SystemClock.now+=5000;Handler.drain();MdoBrowserCdpPopupNative.reconcileRetirements();check(control.committed()&&owner.humanTransferred()&&view.destroyed==0);
            }
        }
        if(prepared){check(owner.current()&&handoff.current()&&marker.lease().current()&&product.popupPageDocumentReady("owned")&&!product.manualControl("owned")&&view.destroyed==0&&backend.detaches.get()==0);}
        if(aborted){
            final MdoBrowserCdpPopupNative.HumanControl original=control;check(control.failure()!=null&&!control.committed());failure(control.failure(),original::commit);
            waitFor(()->view.destroyed==1&&owner.nativeDestroyed(),host,true);check(!owner.humanTransferred()&&!product.manualControl("owned"));
            if(backend.closes.get()==0&&!handoff.detached())backend.wire.add(detached("proof-real"));waitFor(handoff::detached,host,true);
            waitFor(()->{product.pollNetwork("owned");return !product.hasPhysicalNetwork("owned");},host,true);
        }
        check(renderer.worlds.created==worlds&&WebView.constructions==constructions&&backend.nativeNavigationCommands.get()==0);
        return new JSONObject().put("native_human_stage",true).put("variant",name).put("prepared_without_data_loss",prepared).put("data_fenced",!prepared).put("manual_committed",!prepared&&!aborted)
            .put("native_destroyed",view.destroyed).put("observed_code",code==null?JSONObject.NULL:code).put("receipt_failure",control==null||control.failure()==null?JSONObject.NULL:control.failure())
            .put("native_input_cancelled",input).put("original_read_cancelled",read).put("completed_bytes",complete).put("capture_gate_controlled",capture)
            .put("native_event",handoff.detached()).put("root_command_ack",handoff.humanDetachResult()!=null&&handoff.humanDetachResult().ok)
            .put("new_worlds",0).put("new_views",0).put("navigation_commands",0).put("provider_qualified",false).put("ui_published",false);
    }
    private static JSONObject humanPopupTransfer(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer,InputSurface surface)throws Exception {
        if(variant.startsWith("human-stage-"))return stagedHumanControl(variant.substring("human-stage-".length()),host,backend,product,view,renderer,surface);
        if(variant.startsWith("human-navigation-"))return humanPopupNavigation(variant.substring("human-navigation-".length()),host,backend,product,view,renderer);
        if(variant.startsWith("human-return-")){String name=variant.substring("human-return-".length());JSONObject result=returnPopupControl(name,host,backend,product,view,renderer);if(name.startsWith("control-"))result.put("original_control_branch",true);return result;}
        if(variant.startsWith("human-network-"))return humanPopupNetwork(variant.substring("human-network-".length()),host,backend,product,view,renderer);
        String name=variant.substring("human-".length());MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");
        int worlds=renderer.worlds.created;AtomicInteger raced=new AtomicInteger();
        failure("cdp_popup_human_owner_invalid",()->handoff.takeControl(null));failure("cdp_popup_human_owner_invalid",()->owner.takeControl(null));
        check(owner.current()&&handoff.current()&&!handoff.retiring()&&!owner.humanTransferred());
        if(name.equals("hidden")){
            product.bounds(view,new JSONObject().put("browser","owned").put("visible",false));
            failure("cdp_popup_human_owner_invalid",()->handoff.takeControl(owner));check(!handoff.retiring()&&!owner.humanTransferred()&&view.destroyed==0);
            product.bounds(view,new JSONObject().put("browser","owned").put("visible",true));
        }
        java.lang.reflect.Field nativeField=MdoBrowserCdpPopupNative.Promotion.class.getDeclaredField("nativeOwner");nativeField.setAccessible(true);Object independent=nativeField.get(owner);
        java.lang.reflect.Field birthField=independent.getClass().getDeclaredField("birth");birthField.setAccessible(true);Object birth=birthField.get(independent);
        java.lang.reflect.Field weakField=birth.getClass().getDeclaredField("owner");weakField.setAccessible(true);Object original=weakField.get(birth);
        if(name.equals("queued-close")){java.lang.reflect.Method notify=MdoBrowserCdpPopupNative.Promotion.class.getDeclaredMethod("notifyClose");notify.setAccessible(true);notify.invoke(owner);}
        boolean hook=name.equals("late-close")||name.equals("queued-close")||name.equals("view-lost")||name.equals("reentrant");
        if(hook)weakField.set(birth,new java.lang.ref.WeakReference<MdoBrowserLifecycle<WebView>>(product.views){
            @Override public MdoBrowserLifecycle<WebView> get(){MdoBrowserLifecycle<WebView> value=super.get();boolean inside=false;
                for(StackTraceElement frame:Thread.currentThread().getStackTrace())if(frame.getClassName().equals("org.xserver.android.MdoBrowserLifecycle")&&frame.getMethodName().equals("takeIndependentControl"))inside=true;
                if(inside&&raced.compareAndSet(0,1)){
                    check(handoff.retiring()&&!handoff.current()&&!owner.current()&&view.destroyed==0);
                    try{
                        if(name.equals("view-lost"))product.retire("owned",view);
                        else {if(name.equals("reentrant"))failure("cdp_popup_human_owner_invalid",()->handoff.takeControl(owner));owner.close();Handler.drain();check(!owner.ended()&&view.destroyed==0);}
                    }catch(Exception failure){throw new AssertionError(failure);}
                }return value;
            }});
        Handler.rejectPost=name.equals("ui-rejected");
        try{
            if(name.equals("view-lost")){failure("cdp_popup_human_takeover_failed",()->handoff.takeControl(owner));check(!owner.humanTransferred()&&view.destroyed==1);}
            else {handoff.takeControl(owner);check(owner.humanTransferred()&&!owner.current()&&handoff.retiring()&&!handoff.current()&&view.destroyed==0);
                if(Handler.rejectPost){Handler.drain();check(!owner.ended()&&view.destroyed==0);Handler.rejectPost=false;MdoBrowserCdpPopupNative.reconcileRetirements();}
            }
        }finally{Handler.rejectPost=false;weakField.set(birth,original);}
        waitFor(owner::ended,host,true);
        if(!name.equals("view-lost")){
            check(!owner.nativeDestroyed()&&view.destroyed==0&&product.views.owns("owned",view));
            if(name.equals("no-revival")){product.views.manual("owned",view,false);check(!owner.current()&&!handoff.current()&&owner.humanTransferred());}
            failure("cdp_popup_human_owner_invalid",()->handoff.takeControl(owner));
            int replies=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
            waitFor(()->product.preparationReplies==replies+1,host,true);check(!product.preparationReply.optBoolean("ok")&&product.preparationReply.getJSONObject("error").getString("native_code").equals("cdp_popup_page_document_unavailable"));
            failure("cdp_popup_document_route_required",()->product.inspectLegacyRoute("owned"));
        }
        check(renderer.worlds.created==worlds&&renderer.worlds.evaluations==0&&backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.closes.get()==0&&backend.nativeNavigationCommands.get()==0&&backend.detaches.get()==0);
        check(raced.get()==(hook?1:0));
        return new JSONObject().put("actual_human_transfer",true).put("committed",owner.humanTransferred()).put("old_authority_revoked",!owner.current()&&!handoff.current()).put("physical_view_preserved",view.destroyed==0)
            .put("native_destroyed",view.destroyed).put("original_native_ended",owner.ended()).put("raced_closure",raced.get()).put("new_worlds",renderer.worlds.created-worlds).put("native_effects",0)
            .put("navigation_commands",0).put("detach_commands",0).put("browser_connections",1).put("native_proof_connections",1).put("provider_qualified",false);
    }
    private static JSONObject humanPopupNetwork(String name,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        if(name.equals("close-before-detach"))return humanCloseBeforeDetach(host,backend,product,view,renderer);
        if(name.startsWith("descendants-")&&!name.equals("descendants-inflight"))return humanDescendantRetirement(name.substring("descendants-".length()),host,backend,product,view,renderer);
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");
        MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");int worlds=renderer.worlds.created;
        failure("cdp_popup_human_owner_invalid",()->handoff.detachForHuman(owner));
        boolean rootCall=name.startsWith("root-"),rootEffect=name.equals("root-effect");
        boolean complete=name.startsWith("complete")||name.equals("root-complete"),pending=name.equals("pending")||name.equals("root-pending"),body=name.equals("held-body")||name.equals("root-held-body"),descendants=name.startsWith("descendants"),inflight=name.equals("descendants-inflight")||rootCall;String file=null;
        java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> operation=new java.util.concurrent.atomic.AtomicReference<>();
        AtomicInteger operationCallbacks=new AtomicInteger();java.util.concurrent.atomic.AtomicBoolean oldReplyObserved=new java.util.concurrent.atomic.AtomicBoolean();
        if(complete||pending||body){
            backend.wire.add(responsePaused("response-extra-human","source-complete"));waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);
            file=downloads.list()[0].id;
            if(complete||body){backend.holdRead=body;downloads.accept(file,65536,3000);final String selected=file;
                if(complete){waitFor(()->{try{return downloads.get(selected).terminal();}catch(IOException error){throw new AssertionError(error);}},host,true);check(downloads.get(file).verified&&downloads.get(file).sourceCleanupConfirmed);}
                else waitFor(()->backend.lateRead.get()>0,host,true);
            }
            if(pending||body)backend.holdAbort=true;
        }
        if(inflight){renderer.holdMethod="Runtime.evaluate";renderer.holdExpression="({ok:true,text:'managed'})";
            product.runPopupPage("owned",rootEffect,descendants,value->{operationCallbacks.incrementAndGet();operation.set(value);});waitFor(()->renderer.held!=null,host,true);}
        backend.humanDetachMode=name.equals("complete-rejected")?"rejected":name;
        product.takePopupControl("owned");check(owner.humanTransferred()&&!owner.current()&&!handoff.current()&&handoff.retiring()&&view.destroyed==0);
        if(inflight){waitFor(()->operation.get()!=null,host,true);check(operation.get().code!=null&&operation.get().bytes==null&&operation.get().effectMayHaveOccurred==rootEffect);
            if(rootCall){final int originalId=renderer.held.getInt("id");backend.receivedHook=bytes->{try{JSONObject message=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(message.optInt("id",-1)==originalId&&message.optString("sessionId").equals("proof-real"))oldReplyObserved.set(true);}catch(Exception error){throw new AssertionError(error);}};}
            renderer.release(backend.wire);}
        if(!inflight)failure("cdp_popup_human_detach_replayed",()->handoff.detachForHuman(owner));
        if(pending||body){waitFor(()->backend.heldAbortReply!=null,host,true);product.pollPopupNetwork("owned");check(backend.detaches.get()==0&&!product.popupProtocolDrained("owned")&&!downloads.sourcesClean());backend.holdAbort=false;backend.wire.add(backend.heldAbortReply);}
        if(rootCall){waitFor(()->backend.detaches.get()==1||handoff.humanDetachResult()!=null,host,true);if(backend.detaches.get()!=1)throw new AssertionError("Data-only root retirement rejected the original native attachment: "+handoff.humanDetachResult().code);}
        waitFor(()->backend.detaches.get()==1,host,true);check(owner.ended()&&view.destroyed==0&&!owner.nativeDestroyed());
        if(name.equals("no-ack")){
            waitFor(()->backend.heldHumanDetach!=null&&handoff.detached(),host,true);
            check(!handoff.humanDetached()&&!product.popupProtocolDrained("owned"));host.time.addAndGet(300);
        }
        if(name.equals("wrong-event")){waitFor(()->handoff.humanDetachResult()!=null,host,true);check(handoff.humanDetachResult().ok&&!handoff.detached());host.time.addAndGet(5000);}
        boolean failed=name.endsWith("rejected")||name.equals("unsupported")||name.equals("malformed")||name.equals("no-ack")||name.equals("wrong-event");
        if(failed){
            waitFor(()->backend.closes.get()>0,host,true);check(!handoff.humanDetached()&&view.destroyed==0&&!owner.nativeDestroyed());
            product.pollPopupNetwork("owned");check(!product.popupProtocolDrained("owned"));
            failure("download_connection_retiring",downloads::rearm);
        }else{
            waitFor(()->handoff.humanDetachResult()!=null,host,true);check(handoff.humanDetachResult().ok);
            if(name.equals("ack-only")){
                check(!handoff.detached()&&!handoff.humanDetached());product.pollPopupNetwork("owned");check(!product.popupProtocolDrained("owned")&&downloads.list().length==0);failure("download_connection_retiring",downloads::rearm);
                backend.wire.add(detached("proof-real"));
            }
            waitFor(handoff::humanDetached,host,true);product.pollPopupNetwork("owned");
            check(product.popupProtocolDrained("owned")&&product.popupNetworkCount()==1&&product.popupDownloads("owned")==downloads&&downloads.list().length==(file==null?0:1));
            check(view.destroyed==0&&product.views.owns("owned",view)&&backend.closes.get()==0);
            if(file!=null){MdoBrowserCdpDownloads.Snapshot record=downloads.get(file);check(record.sourceCleanupConfirmed&&record.id.equals(file)&&product.downloadBudget.used()==1);
                if(complete){check(record.verified&&record.state.equals("completed")&&record.received==3);MdoBrowserDownloadQueue.Read read=downloads.read(file,0,10);waitFor(read::finished,host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);}
                else {check(record.state.equals("cancelled")&&record.error.equals("cancelled")&&record.failureSource.equals("user")&&!record.verified);}
            }
            // The same controller can rearm only after both native/protocol
            // receipts; this does not reopen old document or native authority.
            MdoBrowserCdpDownloads.Connection next=downloads.rearm();check(next!=null&&!owner.current()&&!handoff.current());
            if(body){int received=backend.receives.get();backend.wire.add(ack(backend.lateRead.get(),"proof-real").put("result",new JSONObject().put("data","discarded-late-body").put("eof",true)));
                waitFor(()->backend.receives.get()>received+2,host,true);check(backend.closes.get()==0&&downloads.get(file).state.equals("cancelled"));}
        }
        MdoBrowserCdpSession.ControlResult outcome=handoff.humanDetachResult();check(outcome!=null);
        if(name.endsWith("rejected")||name.equals("unsupported"))check(!outcome.ok&&outcome.code.equals(name.equals("unsupported")?"cdp_method_unsupported":"cdp_command_rejected")&&outcome.protocolCode==(name.equals("unsupported")?-32601:-32000));
        if(name.equals("malformed"))check(!outcome.ok&&outcome.code.equals("cdp_attach_detach_reply_invalid"));
        if(name.equals("no-ack"))check(!outcome.ok&&outcome.code.equals("cdp_command_timeout")&&outcome.effectMayHaveOccurred);
        if(descendants)check(outcome.ok&&renderer.nativeSessions.isEmpty()&&renderer.childStops==1);
        if(complete&&failed){MdoBrowserCdpDownloads.Snapshot record=downloads.get(file);check(record.verified&&record.state.equals("completed")&&record.received==3&&product.downloadBudget.used()==1);
            MdoBrowserDownloadQueue.Read read=downloads.read(file,0,10);waitFor(read::finished,host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);}
        if(name.equals("no-ack")){MdoBrowserCdpSession.ControlResult receipt=handoff.humanDetachResult();backend.wire.add(backend.heldHumanDetach);host.ui.drain();check(handoff.humanDetachResult()==receipt&&!handoff.humanDetached());}
        if(rootCall){
            waitFor(oldReplyObserved::get,host,true);final int received=backend.receives.get();waitFor(()->backend.receives.get()>received+1,host,true);backend.receivedHook=null;
            check(operationCallbacks.get()==1&&operation.get().code!=null&&operation.get().bytes==null&&operation.get().effectMayHaveOccurred==rootEffect&&backend.closes.get()==0);
            int replies=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
            waitFor(()->product.preparationReplies==replies+1,host,true);check(!product.preparationReply.optBoolean("ok")&&product.preparationReply.getJSONObject("error").getString("native_code").equals("cdp_popup_page_document_unavailable"));
        }
        check(backend.detaches.get()==1&&backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.nativeNavigationCommands.get()==0&&renderer.worlds.created==worlds&&renderer.worlds.evaluations==(inflight?1:0));
        JSONObject evidence=new JSONObject().put("actual_human_network",true).put("detach_commands",backend.detaches.get()).put("acknowledged",handoff.humanDetachResult()!=null&&handoff.humanDetachResult().ok)
            .put("native_event",handoff.detached()).put("cleanup_confirmed",product.popupProtocolDrained("owned")).put("page_preserved",view.destroyed==0)
            .put("wire_failed",failed).put("catalog_preserved",product.popupDownloads("owned")==downloads).put("old_authority_revoked",!owner.current()&&!handoff.current())
            .put("retained_downloads",file==null?0:1).put("verified_original_bytes",complete).put("cancelled_sources",pending||body).put("held_source_cleanup",pending||body)
            .put("code",outcome.code==null?JSONObject.NULL:outcome.code).put("protocol_code",outcome.protocolCode).put("effect_may_have_occurred",outcome.effectMayHaveOccurred)
            .put("new_worlds",renderer.worlds.created-worlds).put("navigation_commands",0).put("provider_qualified",false).put("ui_published",false);
        if(descendants)evidence.put("native_descendants_joined",true).put("root_autoattach_stops",renderer.childStops);
        if(descendants&&inflight)evidence.put("inflight_document_cancelled",true).put("native_descendants_retained",true);
        if(rootCall)evidence.put("data_root_retirement",true).put("original_rpc_retired",true).put("original_rpc_effect",rootEffect).put("original_rpc_callbacks",operationCallbacks.get()).put("old_reply_discarded",oldReplyObserved.get()).put("ordinary_rpc_denied",true);return evidence;
    }
    private static JSONObject humanDescendantRetirement(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        check(renderer.nativeSessions.size()>0);int worlds=renderer.worlds.created;
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");
        MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");String file=null;
        boolean complete=variant.equals("complete"),pending=variant.equals("pending"),body=variant.equals("held-body");
        boolean failed=variant.startsWith("stop-")||variant.equals("missing-child")||variant.equals("wrong-parent");
        if(complete||pending||body){
            backend.wire.add(responsePaused("response-extra-descendant-human","source-complete"));waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);file=downloads.list()[0].id;
            if(complete||body){backend.holdRead=body;downloads.accept(file,65536,3000);final String id=file;
                if(complete){waitFor(()->{try{return downloads.get(id).terminal();}catch(IOException error){throw new AssertionError(error);}},host,true);check(downloads.get(file).verified);}
                else waitFor(()->backend.lateRead.get()>0,host,true);
            }if(pending||body)backend.holdAbort=true;
        }
        renderer.retirementMode=variant;backend.humanDetachMode="flow";
        product.takePopupControl("owned");check(owner.humanTransferred()&&handoff.retiring()&&!owner.current()&&!handoff.current()&&view.destroyed==0);
        if(pending||body){waitFor(()->backend.heldAbortReply!=null,host,true);product.pollPopupNetwork("owned");
            check(renderer.childStops==0&&backend.detaches.get()==0&&!product.popupProtocolDrained("owned")&&!downloads.sourcesClean());
            backend.holdAbort=false;backend.wire.add(backend.heldAbortReply);
        }
        waitFor(()->renderer.childStops==1||backend.closes.get()!=0,host,true);check(renderer.childStops==1);
        if(variant.equals("held-ack")||variant.equals("stop-no-ack")){
            waitFor(()->renderer.heldStop!=null,host,true);product.pollPopupNetwork("owned");
            check(backend.detaches.get()==0&&!handoff.detached()&&!handoff.humanDetached()&&!product.popupProtocolDrained("owned"));
            if(variant.equals("held-ack"))backend.wire.add(renderer.heldStop);else host.time.addAndGet(300);
        }
        if(variant.equals("held-child")||variant.equals("missing-child")){
            int observed=backend.receives.get();waitFor(()->backend.receives.get()>observed+2,host,true);product.pollPopupNetwork("owned");
            check(!renderer.nativeSessions.isEmpty()&&backend.detaches.get()==0&&!handoff.detached()&&!product.popupProtocolDrained("owned"));
            if(variant.equals("held-child"))renderer.detachNativeChildren(backend.wire);else host.time.addAndGet(5001);
        }
        int replies=product.preparationReplies;
        product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
        waitFor(()->product.preparationReplies==replies+1,host,true);
        check(!product.preparationReply.optBoolean("ok")&&product.preparationReply.getJSONObject("error").getString("native_code").equals("cdp_popup_page_document_unavailable"));
        MdoBrowserCdpSession.ControlResult outcome;
        String code=null;
        if(failed){
            waitFor(()->backend.closes.get()!=0,host,true);product.pollPopupNetwork("owned");
            check(!handoff.humanDetached()&&!product.popupProtocolDrained("owned")&&backend.detaches.get()==0&&view.destroyed==0&&!owner.nativeDestroyed());
            failure("download_connection_retiring",downloads::rearm);outcome=handoff.humanDetachResult();
            if(variant.equals("missing-child")){
                check(outcome==null);code=product.preparationWorkerCode();check(code.equals("cdp_popup_handoff_cleanup_unconfirmed"));
            }else if(variant.equals("wrong-parent")){
                check(outcome!=null&&!outcome.ok&&outcome.code.equals("cdp_event_failed")&&backend.eventFailure!=null&&backend.eventFailure.endsWith(":cdp_popup_document_session_invalid"));code=outcome.code;
            }else{
                check(outcome!=null&&!outcome.ok);code=outcome.code;
                check(code.equals(variant.equals("stop-rejected")?"cdp_command_rejected":variant.equals("stop-unsupported")?"cdp_method_unsupported":variant.equals("stop-malformed")?"cdp_attach_autoattach_reply_invalid":variant.equals("stop-send")?"cdp_transport_failed":"cdp_command_timeout"));
                check(outcome.effectMayHaveOccurred!=variant.equals("stop-unsupported"));
            }
            if(renderer.heldStop!=null){backend.wire.add(renderer.heldStop);host.ui.drain();check(handoff.humanDetachResult()==outcome&&!handoff.humanDetached());}
        }else{
            waitFor(handoff::humanDetached,host,true);product.pollPopupNetwork("owned");outcome=handoff.humanDetachResult();
            check(outcome!=null&&outcome.ok&&handoff.detached()&&product.popupProtocolDrained("owned")&&renderer.nativeSessions.isEmpty()&&backend.detaches.get()==1&&backend.closes.get()==0&&downloads.sourcesClean());
            if(file!=null){MdoBrowserCdpDownloads.Snapshot retained=downloads.get(file);check(retained.id.equals(file)&&retained.sourceCleanupConfirmed&&product.downloadBudget.used()==1);
                if(complete){check(retained.verified&&retained.state.equals("completed"));MdoBrowserDownloadQueue.Read read=downloads.read(file,0,10);waitFor(read::finished,host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);}
                else check(!retained.verified&&retained.state.equals("cancelled")&&retained.error.equals("cancelled")&&retained.failureSource.equals("user"));
            }
            check(downloads.rearm()!=null&&!owner.current()&&!handoff.current());
        }
        check(renderer.childStops==1&&renderer.worlds.created==worlds&&renderer.worlds.evaluations==0&&backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.nativeNavigationCommands.get()==0&&view.destroyed==0&&product.popupDownloads("owned")==downloads);
        if(variant.startsWith("raw-"))check(backend.responseAborts.get()==1&&backend.bodyClaims.get()==0&&backend.bodyReads.get()==0);
        return new JSONObject().put("actual_human_network",true).put("descendant_retirement_join",true).put("variant",variant).put("root_autoattach_stops",renderer.childStops)
            .put("detach_commands",backend.detaches.get()).put("acknowledged",outcome!=null&&outcome.ok).put("native_event",handoff.detached()).put("cleanup_confirmed",product.popupProtocolDrained("owned"))
            .put("outcome_present",outcome!=null).put("code",code==null?JSONObject.NULL:code).put("wire_failed",failed).put("page_preserved",true).put("old_authority_revoked",true).put("ordinary_frame_denied",true)
            .put("native_descendants_joined",!failed).put("catalog_preserved",true).put("retained_downloads",file==null?0:1).put("verified_original_bytes",complete).put("cancelled_sources",pending||body)
            .put("raw_pauses_aborted",variant.startsWith("raw-")).put("wrong_parent_denied",variant.equals("wrong-parent")).put("new_worlds",0).put("navigation_commands",0).put("provider_qualified",false).put("ui_published",false);
    }
    private static JSONObject humanCloseBeforeDetach(CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");
        java.util.function.BooleanSupplier drained=product.popupDrainReceipt("owned");int worlds=renderer.worlds.created;
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        backend.retiringClockScope="serviceManaged";backend.retiringClockHook=()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Human close boundary not released");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}};
        try{
            product.takePopupControl("owned");waitFor(()->entered.getCount()==0,host,true);waitFor(owner::ended,host,true);check(owner.humanTransferred()&&view.destroyed==0);
            product.retire("owned",view);check(view.destroyed==1&&owner.nativeDestroyed()&&!drained.getAsBoolean());
            backend.wire.add(detached("proof-real"));release.countDown();
            waitFor(()->backend.closes.get()>0||handoff.detached(),host,true);product.pollPopupNetwork("owned");
            check(backend.closes.get()==0&&handoff.detached()&&handoff.humanDetachResult()==null&&!handoff.humanDetached()&&drained.getAsBoolean()&&product.popupNetworkCount()==0);
            check(backend.detaches.get()==0&&!owner.current()&&!handoff.current()&&renderer.worlds.created==worlds&&renderer.worlds.evaluations==0&&backend.nativeNavigationCommands.get()==0);
            return new JSONObject().put("actual_human_close_race",true).put("native_destroyed",1).put("detach_commands",0).put("acknowledged",false).put("native_event",true)
                .put("physical_close_cleanup",true).put("human_cleanup_invented",false).put("wire_closes",0).put("browser_connections",1).put("native_proof_connections",1)
                .put("new_worlds",0).put("navigation_commands",0).put("provider_qualified",false).put("ui_published",false);
        }finally{release.countDown();backend.retiringClockHook=null;}
    }
    private static JSONObject humanPopupNavigation(String name,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        boolean queued=name.startsWith("queued"),returning=name.endsWith("-return"),complete=name.equals("complete-sent-return"),settled=name.equals("settled-return");
        check(java.util.Arrays.asList("sent","queued","sent-return","queued-return","complete-sent-return","settled-return").contains(name));
        Backend.SourceRenderer navigator=new Backend.SourceRenderer(settled?"source-navigation-flow":"source-navigation-cancel");navigator.holdNavigation=!settled;
        renderer.frames.rootUrl=renderer.context.rootUrl=navigator.url;renderer.navigationPeer=navigator;int worlds=renderer.worlds.created,constructions=WebView.constructions;view.nativeUrl=navigator.url;
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");
        MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");String file=null;Throwable originalFailure=null;CountDownLatch release=new CountDownLatch(1);
        try{
            if(complete){backend.wire.add(responsePaused("response-extra-human-nav","source-complete"));waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);
                file=downloads.list()[0].id;final String selected=file;downloads.accept(file,65536,3000);waitFor(()->{try{return downloads.get(selected).verified;}catch(IOException error){throw new AssertionError(error);}},host,true);}
            if(queued){CountDownLatch entered=new CountDownLatch(1);java.lang.reflect.Field executor=MdoBrowserCdpWorker.class.getDeclaredField("executor");executor.setAccessible(true);
                ((java.util.concurrent.ThreadPoolExecutor)executor.get(host.worker)).execute(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Human navigation queue barrier expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}});
                check(entered.await(3,TimeUnit.SECONDS));}
            JSONObject accepted=product.navigate("owned","goto");check(accepted.getBoolean("accepted"));String reference=accepted.getString("navigation_ref");
            if(settled){waitFor(()->{product.pollNavigation("owned");return product.navigationSettled("owned")&&product.popupPageDocumentReady("owned");},host,true);view.nativeUrl=navigator.url;worlds=renderer.worlds.created;}
            else if(!queued)waitFor(()->navigator.heldNavigation!=null,host,true);
            check(product.navigationReserved("owned")!=settled&&Boolean.TRUE.equals(product.loading.get("owned"))!=settled&&product.inspectNavigation("owned").getBoolean("dispatched")!=queued);
            backend.humanDetachMode="flow";product.takePopupControl("owned");
            if(product.navigationReserved("owned")||Boolean.TRUE.equals(product.loading.get("owned"))||!settled&&!product.inspectNavigation("owned").getBoolean("scope_revoked"))
                throw new AssertionError("Human takeover retained navigation reservation: reserved="+product.navigationReserved("owned")+" loading="+product.loading.get("owned")+" state="+product.inspectNavigation("owned"));
            JSONObject originalReceipt=product.inspectNavigation("owned");release.countDown();if(navigator.heldNavigation!=null){backend.wire.add(navigator.heldNavigation);navigator.heldNavigation=null;}
            waitFor(handoff::humanDetached,host,true);product.pollPopupNetwork("owned");
            check(product.manualControl("owned")&&product.popupProtocolDrained("owned")&&!owner.current()&&!handoff.current()&&view.destroyed==0);
            check(product.inspectNavigation("owned").getString("id").equals(reference)&&!product.navigationReserved("owned")&&!Boolean.TRUE.equals(product.loading.get("owned")));
            if(returning){
                CdpNetworkBindingProbe.Context[] fresh={null};JSONObject[] enabled={null};CdpBindingProbe.Wire[] wire={null};AtomicInteger attempts=new AtomicInteger();
                product.networkStartHook=(marker,exact,quota)->{check(exact==view);attempts.incrementAndGet();CdpNetworkBindingProbe.Context context=host.source(exact,quota);fresh[0]=context;host.sources.put(marker,context);
                    context.peer.rootLoader="load-return-human-nav";context.peer.rootUrl=view.nativeUrl;CdpBindingProbe.Factory.Extra original=context.peer.extra;
                    context.peer.extra=(request,source)->{boolean result=original.send(request,source);if(request.getString("method").equals("Fetch.enable")){byte[] bytes=source.replies.pollLast();check(bytes!=null);enabled[0]=new JSONObject(new String(bytes,StandardCharsets.UTF_8));java.util.Arrays.fill(bytes,(byte)0);wire[0]=source;}return result;};};
                check(product.returnControl("owned","human-navigation-return",false));waitFor(()->enabled[0]!=null,host,true);check(product.manualControl("owned")&&attempts.get()==1);
                wire[0].queue(enabled[0]);waitFor(()->product.publicCounts.containsKey("human-navigation-return"),host,true);
                check(!product.manualControl("owned")&&product.ordinaryNetworkReady("owned")&&!product.navigationReserved("owned")&&!Boolean.TRUE.equals(product.loading.get("owned")));
                check(product.physicalDownloads("owned")==downloads&&product.inspectNavigation("owned").getBoolean("scope_revoked"));
                for(String key:new String[]{"acknowledged","ok","dispatched","code"})check(java.util.Objects.equals(originalReceipt.opt(key),product.inspectNavigation("owned").opt(key)));
                int before=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list").put("navigation_ref",reference));
                waitFor(()->product.preparationReplies==before+1,host,true);check(!product.preparationReply.optBoolean("ok")&&product.preparationReply.getJSONObject("error").getString("native_code").equals("cdp_navigation_cancelled"));
                if(file!=null){check(downloads.get(file).verified&&product.downloadBudget.used()==1);MdoBrowserDownloadQueue.Read read=downloads.read(file,0,10);waitFor(read::finished,host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);}
            }
            check(navigator.navigations==(queued?0:1)&&backend.nativeNavigationCommands.get()==(queued?0:1)&&renderer.worlds.created==worlds&&WebView.constructions==constructions);
            return new JSONObject().put("actual_human_navigation",true).put("dispatched",!queued).put("reservation_released",true).put("record_preserved",true).put("settled_before_takeover",settled)
                .put("fresh_return",returning).put("old_reference_denied",returning).put("completed_bytes",complete).put("manual_until_return",true).put("page_preserved",true)
                .put("navigation_commands",queued?0:1).put("new_views",0).put("native_effects",0).put("provider_qualified",false);
        }catch(Exception|Error problem){originalFailure=problem;throw problem;}finally{
            release.countDown();product.networkStartHook=null;
            if(navigator.heldNavigation!=null){backend.wire.add(navigator.heldNavigation);navigator.heldNavigation=null;}
            try{product.retire("owned",view);waitFor(()->{product.pollNetwork("owned");return !product.hasPhysicalNetwork("owned")&&product.downloadBudget.used()==0;},host,true);}
            catch(Exception|Error cleanup){if(originalFailure!=null)originalFailure.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    private static JSONObject returnPopupControl(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        final String name=variant.startsWith("control-")?variant.substring("control-".length()):variant;product.nativeControlReturns=variant.startsWith("control-");
        MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");
        int worlds=renderer.worlds.created,constructions=WebView.constructions;String file=null;
        int before=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.preparationReplies==before+1,host,true);
        check(product.preparationReply.optBoolean("ok"));JSONObject oldFrame=product.preparationReply.getJSONArray("frames").getJSONObject(0);
        boolean retry=name.equals("retry")||name.equals("complete-retry")||name.equals("cancel-retry"),expectFailure=name.startsWith("failure-"),preflight=name.startsWith("preflight-");
        if(name.startsWith("complete")||name.equals("startup-enable-complete-stop-retry")){
            backend.wire.add(responsePaused("response-extra-return","source-complete"));waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);file=downloads.list()[0].id;
            downloads.accept(file,65536,3000);final String selected=file;waitFor(()->{try{return downloads.get(selected).terminal();}catch(IOException error){throw new AssertionError(error);}},host,true);check(downloads.get(file).verified);
        }
        boolean detachPending=name.equals("preflight-detach");backend.humanDetachMode=detachPending?"ack-only":"flow";product.takePopupControl("owned");
        waitFor(()->detachPending?handoff.humanDetachResult()!=null:handoff.humanDetached(),host,true);product.pollPopupNetwork("owned");
        check(product.manualControl("owned")&&product.popupProtocolDrained("owned")!=detachPending&&view.destroyed==0);
        // Controlled native user navigation changes the physical document;
        // no mdo URL command or replacement view performs this transition.
        view.nativeUrl="https://private.invalid/manual-page#retained";check(product.views.navigation("owned",view));product.loading.put("owned",false);long epoch=product.views.epoch("owned",view);
        if(name.startsWith("startup-"))return startupPopupReturn(name.substring("startup-".length()),host,backend,product,view,renderer,owner,handoff,downloads,file,worlds,constructions,epoch);
        CdpNetworkBindingProbe.Context[] fresh={null};MdoBrowserCdpMarker[] freshMarker={null};JSONObject[] enabled={null};CdpBindingProbe.Wire[] enabledWire={null};
        AtomicInteger attempts=new AtomicInteger();String refusal=null;
        product.networkStartHook=(marker,exact,quota)->{
            int attempt=attempts.incrementAndGet();check(exact==view&&marker.lease().current()&&product.manualControl("owned"));CdpNetworkBindingProbe.Context context=host.source(exact,quota);fresh[0]=context;freshMarker[0]=marker;host.sources.put(marker,context);
            context.peer.wrongProof=name.equals("failure-proof");context.rejectEnable=name.equals("failure-enable")||retry&&!name.equals("cancel-retry")&&attempt==1;
            context.peer.rootLoader="load-manual-return";context.peer.rootUrl=view.nativeUrl;CdpBindingProbe.Factory.Extra original=context.peer.extra;
            context.peer.extra=(request,wire)->{boolean accepted=original.send(request,wire);if(request.getString("method").equals("Fetch.enable")){
                    byte[] reply=wire.replies.pollLast();check(reply!=null);enabled[0]=new JSONObject(new String(reply,StandardCharsets.UTF_8));java.util.Arrays.fill(reply,(byte)0);enabledWire[0]=wire;check(enabled[0].getInt("id")==request.getInt("id"));
                }return accepted;};
        };
        int replies=product.preparationReplies;
        try{
            if(preflight){
                PopupProductHost.MdoBrowserDocument originalProfile=product.documents.get("owned");
                String rejected=name.substring("preflight-".length());if(rejected.equals("loading"))product.loading.put("owned",true);if(rejected.equals("paused"))product.browserPaused=true;
                if(rejected.equals("channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                if(rejected.equals("profile")){PopupProductHost.MdoBrowserDocument other=new PopupProductHost.MdoBrowserDocument(view);other.assigned=true;product.documents.put("owned",other);}
                if(rejected.equals("chooser"))product.chooserAllowed=false;
                if(rejected.equals("capture")){PopupProductHost.CaptureEntry capture=new PopupProductHost.CaptureEntry(view);capture.capture.active=true;product.captures.put("owned",capture);}
                if(rejected.equals("native-failed"))product.nativeViewCleanupFailed=true;
                String code=rejected.equals("agent")?"user_takeover":rejected.equals("loading")?"network_rearm_page_loading":rejected.equals("detach")?"network_retiring":java.util.Arrays.asList("chooser","capture","native-failed").contains(rejected)?"network_return_blocked":"network_owner_unavailable";
                try{product.returnControl("owned","return-control",rejected.equals("agent"));throw new AssertionError("Return preflight granted "+rejected);}catch(IllegalStateException expected){check(code.equals(expected.getMessage()));}
                product.documents.put("owned",originalProfile);
                product.browserPaused=false;product.loading.put("owned",false);
                if(detachPending){check(!handoff.humanDetached()&&!product.popupProtocolDrained("owned"));backend.wire.add(detached("proof-real"));waitFor(handoff::humanDetached,host,true);product.pollPopupNetwork("owned");}
                check(attempts.get()==0&&product.manualControl("owned")&&product.popupDownloads("owned")==downloads&&!owner.current()&&!handoff.current()&&view.destroyed==0&&WebView.constructions==constructions);
                return new JSONObject().put("actual_return_preflight",true).put("code",code).put("new_networks",0).put("manual_retained",true).put("page_preserved",true).put("old_authority_revoked",true).put("provider_qualified",false).put("ui_published",false);
            }
            check(product.returnControl("owned","return-control",false));check(product.manualControl("owned")&&!product.ordinaryNetworkReady("owned"));
            if(!name.equals("failure-proof")){
                waitFor(()->enabled[0]!=null,host,true);check(product.manualControl("owned")&&!product.ordinaryNetworkReady("owned")&&product.preparationReplies==replies);
                if(name.equals("double-return"))try{product.returnControl("owned","duplicate-return",false);throw new AssertionError("Duplicate return started");}catch(IllegalStateException expected){check(expected.getMessage().equals("network_starting")&&attempts.get()==1);}
                enabledWire[0].queue(enabled[0]);enabled[0]=null;waitFor(()->!host.ui.tasks.isEmpty(),host,false);check(product.manualControl("owned")&&!product.ordinaryNetworkReady("owned"));
                if(name.equals("failure-epoch"))check(product.views.navigation("owned",view));
                if(name.equals("failure-loading"))product.loading.put("owned",true);
                if(name.equals("failure-channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                if(name.equals("failure-input")){product.failInputInvalidation=true;try{product.views.stop("owned",view);}finally{product.failInputInvalidation=false;}check(!product.views.inputClean("owned",view));}
                if(name.equals("failure-chooser"))product.chooserAllowed=false;
                if(name.equals("failure-capture")){PopupProductHost.CaptureEntry capture=new PopupProductHost.CaptureEntry(view);capture.capture.active=true;product.captures.put("owned",capture);}
                if(name.equals("failure-retiring"))product.nativeRetiringViews.add(product.views.get("root"));
                if(name.equals("failure-close"))product.retire("owned",view);
                if(name.equals("failure-cancel")||name.equals("cancel-retry"))product.stopNetwork("owned");
                host.ui.drain();
            }
            if(name.equals("failure-close")){
                waitFor(()->product.publicCounts.containsKey("return-control"),host,true);JSONObject failed=product.publicReplies.get("return-control");
                check(view.destroyed==1&&!product.ordinaryNetworkReady("owned")&&!owner.current()&&!handoff.current()&&!failed.getBoolean("ok")&&failed.getBoolean("manual")&&failed.getBoolean("network_cleanup_confirmed"));
                return new JSONObject().put("actual_return_closed",true).put("native_destroyed",1).put("manual_granted",false).put("new_networks",1).put("network_cleanup_confirmed",true).put("native_effects",0).put("provider_qualified",false).put("ui_published",false);
            }
            waitFor(()->product.ordinaryNetworkReplySent("owned"),host,true);
            if(expectFailure||retry){
                check(product.manualControl("owned"));
                waitFor(()->product.ordinaryNetworkRetired("owned"),host,true);
                check(product.manualControl("owned")&&!product.ordinaryNetworkReady("owned")&&!owner.current()&&!handoff.current()&&product.physicalDownloads("owned")==downloads&&view.destroyed==0&&attempts.get()==1);
                if(name.equals("failure-channel")){check(!product.publicCounts.containsKey("return-control"));refusal=product.physicalNetworkCode("owned");}
                else {JSONObject failed=product.publicReplies.get("return-control");check(failed!=null&&!failed.getBoolean("ok")&&failed.getBoolean("manual")&&failed.getBoolean("network_cleanup_confirmed"));refusal=failed.getJSONObject("error").getString("code");}
                check(refusal.equals(name.equals("failure-proof")?"cdp_binding_proof_invalid":name.equals("failure-cancel")||name.equals("cancel-retry")?"network_start_cancelled":name.equals("failure-enable")||retry?"cdp_method_unsupported":"network_owner_changed"));
                if(!retry)return new JSONObject().put("actual_return_failure",true).put("code",refusal==null?JSONObject.NULL:refusal).put("manual_retained",true).put("cleanup_confirmed",true).put("page_preserved",true)
                    .put("new_networks",attempts.get()).put("fetch_enables",fresh[0].methods.stream().filter("Fetch.enable"::equals).count()).put("reply_delivered",!name.equals("failure-channel"))
                    .put("old_authority_revoked",true).put("native_effects",0).put("provider_qualified",false).put("ui_published",false);
                // Only an explicit fresh user attempt can recover a known
                // rejected enable. No automatic request or marker is replayed.
                check(product.returnControl("owned","return-retry",false));waitFor(()->enabled[0]!=null,host,true);check(product.manualControl("owned")&&attempts.get()==2);
                enabledWire[0].queue(enabled[0]);enabled[0]=null;waitFor(()->product.publicCounts.containsKey("return-retry"),host,true);
                check(product.publicCounts.get("return-control")==1&&product.publicCounts.get("return-retry")==1);
            }
            check(!product.manualControl("owned")&&product.ordinaryNetworkReady("owned")&&product.publicCounts.get(retry?"return-retry":"return-control")==1&&product.publicReplies.get(retry?"return-retry":"return-control").getBoolean("downloads_intercepted"));
            check(!owner.current()&&!handoff.current()&&owner.humanTransferred()&&owner.ended()&&!owner.nativeDestroyed()&&view.destroyed==0&&product.views.owns("owned",view));
            check(product.physicalDownloads("owned")==downloads&&view.nativeUrl.equals("https://private.invalid/manual-page#retained")&&product.views.epoch("owned",view)==epoch&&WebView.constructions==constructions);
            MdoBrowserCdpMarker document=product.cdpMarker("owned",view);CdpBindingProbe.Factory peer=new CdpBindingProbe.Factory(view);CdpFramesProbe.Plan frames=new CdpFramesProbe.Plan();frames.omitChild=true;frames.includeLocal=false;frames.rootLoader=peer.rootLoader="load-manual-return";frames.rootUrl=peer.rootUrl=view.nativeUrl;
            CdpWorldsProbe.Engine ordinaryWorlds=new CdpWorldsProbe.Engine();frames.extra=ordinaryWorlds;Set<CdpBindingProbe.Wire> admitted=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<CdpBindingProbe.Wire,Boolean>());
            peer.extra=(request,wire)->{if(request.getString("method").equals("Page.enable"))admitted.add(wire);return admitted.contains(wire)&&frames.send(request,wire);};host.documents.put(document,peer);
            int read=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.preparationReplies==read+1,host,true);
            check(product.preparationReply.optBoolean("ok"));JSONObject newFrame=product.preparationReply.getJSONArray("frames").getJSONObject(0);check(!newFrame.getString("document").equals(oldFrame.getString("document"))&&ordinaryWorlds.created==1&&ordinaryWorlds.evaluations==0);
            int stale=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","capture_owner").put("frame",oldFrame.getString("id")).put("document",oldFrame.getString("document")));
            waitFor(()->product.preparationReplies==stale+1,host,true);check(!product.preparationReply.optBoolean("ok")&&!product.preparationReply.getJSONObject("error").getBoolean("evaluation_may_have_occurred")&&ordinaryWorlds.evaluations==0);
            if(file!=null){MdoBrowserCdpDownloads.Snapshot record=downloads.get(file);check(record.verified&&record.received==3&&record.id.equals(file)&&product.downloadBudget.used()==1);
                MdoBrowserDownloadQueue.Read data=downloads.read(file,0,10);waitFor(data::finished,host,true);byte[] bytes=data.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);}
            if(name.equals("new-download")){
                JSONObject offered=responsePaused("interception-job-return.0","source-complete");offered.remove("sessionId");fresh[0].wire().queue(offered);waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);
                String next=downloads.list()[0].id;fresh[0].abort=true;downloads.cancel(next);waitFor(()->{try{return downloads.get(next).terminal();}catch(IOException error){throw new AssertionError(error);}},host,true);
                check(downloads.get(next).state.equals("cancelled")&&downloads.get(next).sourceCleanupConfirmed&&product.downloadBudget.used()==1);
            }
            check(fresh[0].methods.stream().filter("Fetch.enable"::equals).count()==1&&backend.nativeNavigationCommands.get()==0&&renderer.worlds.created==worlds&&backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.detaches.get()==1&&backend.closes.get()==0);
            return new JSONObject().put("actual_same_view_return",true).put("fresh_network_proof",true).put("fresh_document_proof",true).put("old_reference_denied",true).put("manual_until_ready",true)
                .put("page_preserved",true).put("catalog_preserved",true).put("completed_bytes",file!=null).put("new_views",WebView.constructions-constructions).put("navigation_commands",0).put("fetch_enables",1)
                .put("new_networks",attempts.get()).put("explicit_retry",retry).put("first_rejection",refusal==null?JSONObject.NULL:refusal)
                .put("new_downloads",name.equals("new-download")?1:0)
                .put("fresh_worlds",ordinaryWorlds.created).put("native_effects",ordinaryWorlds.evaluations).put("provider_qualified",false).put("ui_published",false);
        }finally{
            product.networkStartHook=null;if(name.equals("preflight-paused"))product.browserPaused=false;
            if(name.equals("preflight-native-failed"))product.nativeViewCleanupFailed=false;if(name.equals("failure-retiring"))product.nativeRetiringViews.remove(product.views.get("root"));
            if(name.equals("preflight-chooser")||name.equals("failure-chooser"))product.chooserAllowed=true;
            if(name.equals("preflight-capture")||name.equals("failure-capture")){PopupProductHost.CaptureEntry capture=product.captures.get("owned");if(capture!=null)capture.capture.close();}
            product.loading.put("owned",false);if(fresh[0]!=null&&enabled[0]!=null&&product.manualControl("owned"))enabledWire[0].queue(enabled[0]);
            product.retireOrdinaryNetwork("owned");product.retire("owned",view);
            waitFor(()->{product.pollNetwork("owned");return !product.hasPhysicalNetwork("owned")&&product.downloadBudget.used()==0;},host,true);
        }
    }
    /** Cancel the actual product rearm while its native Fetch acknowledgement
     * is still withheld, rather than cancelling a queued ready UI callback. */
    private static JSONObject startupPopupReturn(String name,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer,
        MdoBrowserCdpPopupNative.Promotion owner,MdoBrowserCdpPopupBroker.Handoff handoff,MdoBrowserCdpDownloads downloads,String file,int worlds,int constructions,long epoch)throws Exception {
        CdpNetworkBindingProbe.Context[] fresh={null};JSONObject[] enable={null};CdpBindingProbe.Wire[] wire={null};AtomicInteger attempts=new AtomicInteger();
        MdoBrowserCdpMarker[] originalMarker={null};
        boolean proof=name.startsWith("proof-"),closed=name.endsWith("-close"),channel=name.endsWith("-channel"),retry=name.endsWith("-retry"),queued=name.equals("enable-queued-ack-stop-retry"),uncertain=name.equals("enable-cleanup-failure");
        check(java.util.Arrays.asList("enable-stop","enable-stop-retry","enable-complete-stop-retry","enable-close","proof-stop","proof-stop-retry","proof-close","enable-channel","enable-queued-ack-stop-retry","enable-cleanup-failure").contains(name));
        CountDownLatch released=new CountDownLatch(1);int evaluated=view.evaluations;view.defer=proof;
        product.networkStartHook=(marker,exact,quota)->{
            check(exact==view&&product.manualControl("owned"));if(attempts.incrementAndGet()==1)originalMarker[0]=marker;CdpNetworkBindingProbe.Context context=host.source(exact,quota);fresh[0]=context;host.sources.put(marker,context);
            context.peer.rootLoader="load-manual-return";context.peer.rootUrl=view.nativeUrl;CdpBindingProbe.Factory.Extra original=context.peer.extra;
            context.peer.extra=new CdpBindingProbe.Factory.Extra(){
                public boolean send(JSONObject request,CdpBindingProbe.Wire source)throws Exception {
                    boolean accepted=original.send(request,source);if(request.getString("method").equals("Fetch.enable")){
                        byte[] packet=source.replies.pollLast();check(packet!=null);enable[0]=new JSONObject(new String(packet,StandardCharsets.UTF_8));java.util.Arrays.fill(packet,(byte)0);wire[0]=source;
                        if(queued&&attempts.get()==1&&!released.await(5,TimeUnit.SECONDS))throw new AssertionError("Queued ACK startup barrier expired");
                    }return accepted;
                }
                public void closed(CdpBindingProbe.Wire source){original.closed(source);if(uncertain)throw new IllegalStateException("controlled_native_startup_close_failure");}
            };
        };
        try{
            check(product.returnControl("owned","return-startup",false));
            waitFor(()->proof?view.evaluations>evaluated:enable[0]!=null,host,true);CdpNetworkBindingProbe.Context original=fresh[0];
            String lateMarkerReply=null;
            if(proof){List<String> tokens=new ArrayList<>();for(String value:view.lexical.values())if(!WebView.UNDEFINED.equals(value))tokens.add(value);
                check(tokens.size()==1&&tokens.get(0).matches("[0-9a-f]{32}"));lateMarkerReply="\""+tokens.get(0)+"\"";}
            check(product.manualControl("owned")&&!product.ordinaryNetworkReady("owned")&&!product.publicCounts.containsKey("return-startup"));
            check(!proof||original.peer.wires.isEmpty());
            if(queued){wire[0].queue(enable[0]);enable[0]=null;}
            if(uncertain)host.uncertainCleanup=true;
            if(closed)product.retire("owned",view);
            else if(channel){product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());product.pollNetwork("owned");}
            else product.stopNetwork("owned");
            // The original late proof/ACK cannot complete the cancelled UI
            // grant. Cancellation closes its exact source before worker exit.
            released.countDown();if(proof){view.defer=false;view.finish(lateMarkerReply);Handler.drain();}
            waitFor(()->channel?product.ordinaryNetworkReplySent("owned"):product.publicCounts.containsKey("return-startup"),host,true);
            JSONObject failed=product.publicReplies.get("return-startup");String code=channel?product.physicalNetworkCode("owned"):failed.getJSONObject("error").getString("code");
            if(channel)check(failed==null&&!product.publicCounts.containsKey("return-startup"));
            else check(!failed.getBoolean("ok")&&failed.getBoolean("manual")&&failed.getBoolean("network_cleanup_confirmed")==!uncertain&&product.publicCounts.get("return-startup")==1);
            check(code.equals(channel?"network_owner_changed":closed?"network_view_closed":"network_start_cancelled"));
            check(!product.hasPhysicalNetwork("owned")||!product.ordinaryNetworkReady("owned"));
            check(closed||product.manualControl("owned"));check(proof?original.peer.wires.isEmpty():original.wire().closed.get());
            check(original.methods.stream().filter("Fetch.enable"::equals).count()==(proof?0:1)&&original.methods.stream().noneMatch("Fetch.disable"::equals));
            check(host.worker.networkPending(originalMarker[0])==uncertain);
            check(view.destroyed==(closed?1:0)&&WebView.constructions==constructions&&view.nativeUrl.equals("https://private.invalid/manual-page#retained"));
            check(closed||product.views.epoch("owned",view)==epoch);check(!owner.current()&&!handoff.current()&&renderer.worlds.created==worlds&&backend.nativeNavigationCommands.get()==0&&attempts.get()==1);
            if(!closed){check(product.physicalDownloads("owned")==downloads);if(file!=null){check(downloads.get(file).verified&&product.downloadBudget.used()==1);
                MdoBrowserDownloadQueue.Read read=downloads.read(file,0,10);waitFor(read::finished,host,true);byte[] bytes=read.take();check(java.util.Arrays.equals(bytes,new byte[]{0,1,(byte)255}));java.util.Arrays.fill(bytes,(byte)0);}}
            if(uncertain){check(product.networkCleanupFailed&&!product.ordinaryNetworkRetired("owned"));try{product.returnControl("owned","unsafe-retry",false);throw new AssertionError("Unconfirmed startup cleanup admitted retry");}catch(IllegalStateException expected){check(expected.getMessage().equals("network_retiring"));}}
            if(retry){
                check(product.ordinaryNetworkRetired("owned")&&!product.networkCleanupFailed);enable[0]=null;check(product.returnControl("owned","startup-retry",false));
                waitFor(()->enable[0]!=null,host,true);check(product.manualControl("owned")&&attempts.get()==2);wire[0].queue(enable[0]);enable[0]=null;
                waitFor(()->product.publicCounts.containsKey("startup-retry"),host,true);check(!product.manualControl("owned")&&product.ordinaryNetworkReady("owned")&&product.publicCounts.get("startup-retry")==1);
                check(product.physicalDownloads("owned")==downloads&&(file==null||downloads.get(file).verified));
            }
            return new JSONObject().put("actual_startup_cancel",true).put("phase",proof?"native-proof":"fetch-enable").put("original_ack_received",false).put("queued_ack_drained",queued)
                .put("valid_late_original_proof",proof)
                .put("manual_until_terminal",true).put("cleanup_confirmed",!uncertain).put("uncertain_retry_denied",uncertain).put("page_preserved",!closed).put("physical_close",closed)
                .put("reply_delivered",!channel).put("code",code).put("new_networks",attempts.get()).put("explicit_retry",retry).put("completed_bytes",file!=null)
                .put("original_enable_dispatches",proof?0:1).put("original_disable_dispatches",0).put("retained_uncertain_sources",uncertain?1:0)
                .put("old_authority_revoked",true).put("native_effects",0).put("provider_qualified",false).put("ui_published",false);
        }finally{
            released.countDown();view.defer=false;view.finish("null");product.networkStartHook=null;product.retireOrdinaryNetwork("owned");if(!closed)product.retire("owned",view);
            waitFor(()->{product.pollNetwork("owned");return (uncertain?product.hasPhysicalNetwork("owned")&&product.networkCleanupFailed:!product.hasPhysicalNetwork("owned"))&&product.downloadBudget.used()==0;},host,true);
        }
    }
    private static JSONObject naturalPopupProduct(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        String name=variant.substring("natural-".length());boolean denied=name.equals("hidden")||name.equals("manual")||name.equals("channel"),retry=name.equals("mismatch")||name.equals("pending")||name.equals("changed")||name.equals("late");
        int worlds=renderer.worlds.created;long epoch=product.views.epoch("owned",view);
        int before=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.preparationReplies==before+1,host,true);
        check(product.preparationReply.optBoolean("ok"));JSONObject original=product.preparationReply.getJSONArray("frames").getJSONObject(0);
        String loader="load-natural";int receives=backend.receives.get();naturalCommit(backend,renderer,loader,!name.equals("pending"));
        backend.wire.add(new JSONObject().put("method","Runtime.executionContextsCleared").put("sessionId","proof-real").put("params",new JSONObject()));
        waitFor(()->backend.receives.get()>receives+3,host,true);product.nativePageCallback("owned");
        check(product.views.epoch("owned",view)>epoch&&!product.popupPageDocumentReady("owned")&&product.popupNetworkReady("owned"));
        int pinned=product.preparationReplies,queries=renderer.frames.snapshotCount;
        product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","capture_owner").put("frame",original.getString("id")).put("document",original.getString("document")));
        waitFor(()->product.preparationReplies==pinned+1,host,true);check(!product.preparationReply.optBoolean("ok")&&renderer.frames.snapshotCount==queries&&renderer.worlds.created==worlds&&!product.popupPageDocumentReady("owned"));
        if(name.equals("mismatch"))renderer.frames.rootLoader=renderer.context.rootLoader="foreign-document";
        if(name.equals("hidden"))view.shown=false;if(name.equals("manual"))product.manual.add("owned");if(name.equals("channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
        if(name.equals("changed"))renderer.beforeWorld=()->{try{naturalCommit(backend,renderer,"load-natural-next",true);}catch(JSONException error){throw new AssertionError(error);}};
        host.ui.drain();int replies=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
        if(name.equals("late")){
            waitFor(()->!host.ui.tasks.isEmpty(),host,false);int read=backend.receives.get();naturalCommit(backend,renderer,"load-natural-next",true);waitFor(()->backend.receives.get()>read+2,host,false);check(product.preparationReplies==replies);
        }
        waitFor(()->product.preparationReplies==replies+1,host,true);boolean first=product.preparationReply.optBoolean("ok");
        String firstCode=first?null:product.preparationReply.getJSONObject("error").getString("native_code");
        check(first||!firstCode.equals("cdp_worker_failed"));
        if(first!=(name.equals("flow")))throw new AssertionError("Natural-navigation inventory "+name+": "+product.preparationReply);
        int attempted=renderer.worlds.created-worlds;
        if(retry){
            check(!product.popupPageDocumentReady("owned")&&backend.closes.get()==0&&product.popupOwner("owned").current());
            if(name.equals("mismatch"))renderer.frames.rootLoader=renderer.context.rootLoader=loader;
            if(name.equals("pending")){int read=backend.receives.get();naturalCommit(backend,renderer,loader,true);waitFor(()->backend.receives.get()>read+2,host,true);}
            int prior=product.preparationReplies;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.preparationReplies==prior+1,host,true);
            if(!product.preparationReply.optBoolean("ok"))throw new AssertionError("Explicit natural inventory retry "+name+": "+product.preparationReply);
        }
        int stale=0;
        if(!denied){
            check(product.popupPageDocumentReady("owned")&&renderer.worlds.created==worlds+attempted+(retry?1:0));
            JSONObject fresh=product.preparationReply.getJSONArray("frames").getJSONObject(0);check(!fresh.getString("document").equals(original.getString("document")));
            int prior=product.preparationReplies,evaluations=renderer.worlds.evaluations;
            product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","capture_owner").put("frame",original.getString("id")).put("document",original.getString("document")));
            waitFor(()->product.preparationReplies==prior+1,host,true);check(!product.preparationReply.optBoolean("ok")&&renderer.worlds.evaluations==evaluations);stale++;
            check(product.popupPageDocumentReady("owned")&&product.popupOwner("owned").current()&&backend.closes.get()==0);
            failure("cdp_navigation_read_only",()->product.submitFreshReadEffect("owned"));
        }else check(!product.popupPageDocumentReady("owned")&&attempted==0);
        if(name.equals("hidden"))view.shown=true;if(name.equals("manual"))product.manual.remove("owned");
        check(backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.guards.get()==1&&backend.nativeNavigationCommands.get()==0&&renderer.worlds.evaluations==0);
        return new JSONObject().put("actual_natural_navigation",true).put("fresh_inventory",!denied).put("first_read_ok",first).put("first_native_code",firstCode==null?JSONObject.NULL:firstCode).put("explicit_retry",retry).put("stale_references_refused",stale).put("pinned_rearm_refused",true).put("effect_guard_refused",!denied)
            .put("new_worlds",renderer.worlds.created-worlds).put("attempted_worlds",attempted).put("native_navigation_commands",backend.nativeNavigationCommands.get()).put("native_effects",renderer.worlds.evaluations)
            .put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("provider_qualified",false);
    }
    private static void productPage(String mode,String directory)throws Exception {
        String variant=mode.substring("preparation-page-".length());Backend backend=new Backend();android.os.SystemClock.now=1000;
        boolean download=variant.startsWith("download")||variant.equals("route-download")||variant.equals("navigation-from-download"),held=java.util.Arrays.asList("cancel-activation","channel-activation","deadline-activation").contains(variant);
        boolean worldHeld=java.util.Arrays.asList("world-document-change","world-channel-change","cancel-world","cancel-world-isolated","parent-before-world").contains(variant);
        CountDownLatch worldEntered=new CountDownLatch(1),worldRelease=new CountDownLatch(1);
        boolean navigating=variant.startsWith("navigation-")||variant.startsWith("natural-")||variant.startsWith("human-")||variant.startsWith("control-");PopupProductHost[] originalProduct={null};
        InputSurface inputSurface=variant.startsWith("input-")||navigating?new InputSurface():null;
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context original=host.source();host.ready(original);try(NativeSource source=new NativeSource(host,original,inputSurface,navigating?(key,view,epoch,reason)->{PopupProductHost product=originalProduct[0];if(product!=null)product.lifecycleInvalidated(key,view,epoch,reason);}:null)){
                java.nio.file.Path root=java.nio.file.Paths.get(directory);java.nio.file.Files.createDirectories(root);
                try(MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->new MdoBrowserDownloadFiles(root),host.time::get)){
                    PopupProductHost product=new PopupProductHost(source.views);originalProduct[0]=product;product.bindPreparation(source.view,original.adapter,source.core,host.worker);
                    product.downloadQueue=queue;product.downloadBudget=new MdoBrowserCdpDownloads.Budget();product.loading.put("owned",true);
                    AtomicInteger prepared=new AtomicInteger(),accepted=new AtomicInteger(),failed=new AtomicInteger();String[] reason={null};
                    WebView[] exact={null};Backend.DocumentRenderer[] renderer={null};Backend.SourceRenderer initial=new Backend.SourceRenderer("source-complete");
                    boolean[] acknowledged={false},detachSent={false};AutoCloseable[] handle={null};
                    MdoBrowserCdpPopupPreparation.Failure failure=(code,effect)->{check(host.ui.onThread()&&effect);failed.incrementAndGet();reason[0]=code;};
                    source.beforeSend=child->{
                        check(host.ui.onThread()&&child==source.views.get("owned"));exact[0]=child;source.adopting=child;
                        backend.nativeTargets.put("target-proof-real",proofPeer(child,"success",null,null));backend.sourceRenderer=initial;
                        try{renderer[0]=new Backend.DocumentRenderer(child);renderer[0].frames.omitChild=!variant.equals("route-nested")&&!variant.startsWith("human-network-descendants");renderer[0].frames.includeLocal=false;}
                        catch(Exception error){throw new AssertionError(error);}
                        if(variant.equals("world-unique"))renderer[0].worlds.ignoreUnique=true;
                        if(variant.equals("world-metadata"))renderer[0].worlds.missingUnique=true;
                        if(variant.equals("world-wait"))try{renderer[0].frames.pending.add(new JSONObject().put("method","Page.frameStartedLoading").put("params",new JSONObject().put("frameId","frame-owned")));}catch(Exception error){throw new AssertionError(error);}
                        if(worldHeld)renderer[0].beforeWorld=()->{worldEntered.countDown();try{if(!worldRelease.await(5,TimeUnit.SECONDS))throw new AssertionError("World startup wait expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}};
                        backend.managedResumeMode=held?"timeout":variant.equals("activation-unsupported")?"unsupported":variant.equals("activation-malformed")?"nonempty":"normal";
                        backend.responseMode=download?"source-complete":"source-pass";
                        backend.managedResumeHook=()->{
                            check(initial.calls==3&&backend.guards.get()==1&&backend.resumes.get()==1&&backend.continues.get()==0);
                            backend.sourceRenderer=null;backend.documentRenderer=renderer[0];
                            if(variant.equals("scheduler-failure"))host.ui.post(()->{Handler.fail=true;product.pollPopupPage("owned");});
                        };
                        backend.managedFlowHook=()->{
                            if(!download&&!variant.equals("loading-timeout"))host.ui.post(()->{check(source.views.navigation("owned",child));product.loading.put("owned",false);});
                        };
                    };
                    product.prepare(prepared::incrementAndGet,failure);waitFor(()->prepared.get()==1,host,true);source.open();
                    MdoBrowserCdpPopupWorker.Request request=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class);Throwable primary=null;
                    try{
                        handle[0]=product.handoffPagePrepared(source.core.snapshot().id,"owned",9000,()->{
                            check(host.ui.onThread()&&product.popupPageReady("owned"));accepted.incrementAndGet();
                            if(variant.equals("callback-throw"))throw new IllegalStateException("controlled_page_delivery_failure");
                        },failure);
                        backend.wire.add(attached("proof-real","owned"));waitFor(()->source.sends==1,host,true);source.ack();acknowledged[0]=true;
                        if(held){
                            waitFor(()->backend.resumes.get()==1,host,true);check(accepted.get()==0&&backend.continues.get()==0&&renderer[0].worlds.created==0);
                            if(variant.equals("cancel-activation"))handle[0].close();
                            else if(variant.equals("channel-activation"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                            else android.os.SystemClock.now+=9000;
                            product.pollPopupPage("owned");
                        }
                        if(variant.equals("loading-timeout")){
                            waitFor(()->backend.continues.get()==1,host,true);product.pollPopupPage("owned");check(accepted.get()==0&&renderer[0].worlds.created==0);
                            android.os.SystemClock.now+=9000;product.pollPopupPage("owned");
                        }
                        if(variant.equals("world-wait")){
                            waitFor(()->{if(renderer[0].frames.snapshotCount>0&&!product.popupPageInFlight("owned"))return true;product.pollPopupPage("owned");return false;},host,true);
                            check(accepted.get()==0&&failed.get()==0&&renderer[0].worlds.created==0);
                            backend.wire.add(new JSONObject().put("method","Page.frameStoppedLoading").put("sessionId","proof-real").put("params",new JSONObject().put("frameId","frame-owned")));
                        }
                        if(worldHeld){
                            waitFor(()->{product.pollPopupPage("owned");return worldEntered.getCount()==0;},host,true);check(accepted.get()==0&&product.popupPageInFlight("owned"));
                            if(variant.equals("parent-before-world"))product.retirePreparation("cdp_popup_preparation_cancelled");
                            else if(variant.equals("world-document-change"))check(source.views.navigation("owned",exact[0]));
                            else if(variant.equals("world-channel-change"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                            else handle[0].close();
                            product.pollPopupPage("owned");worldRelease.countDown();
                        }
                        boolean bad=held||worldHeld&&!variant.equals("parent-before-world")||java.util.Arrays.asList("activation-unsupported","activation-malformed","world-unique","world-metadata","callback-throw","scheduler-failure","loading-timeout").contains(variant);
                        waitFor(()->{product.pollPopupPage("owned");return accepted.get()!=0||failed.get()!=0;},host,true);
                        if(variant.equals("cancel-world-isolated")){
                            int before=backend.receives.get();waitFor(()->backend.closes.get()!=0||backend.receives.get()>before+12,host,true);
                            if(backend.closes.get()!=0)throw new AssertionError("Cancelling one world bootstrap closed the shared Browser wire");
                        }
                        if(bad){
                            check(failed.get()==1&&accepted.get()==(variant.equals("callback-throw")?1:0)&&product.pendingHandoffs()==0&&!product.popupPageReady("owned"));
                            if(variant.equals("activation-unsupported"))check(reason[0].equals("cdp_method_unsupported"));
                            if(variant.equals("activation-malformed"))check(reason[0].equals("cdp_attach_control_reply_invalid"));
                            if(variant.equals("deadline-activation")||variant.equals("loading-timeout"))check(reason[0].equals("cdp_popup_page_timeout"));
                        }else{
                            check(accepted.get()==1&&failed.get()==0&&product.pendingHandoffs()==0&&product.popupNetworkReady("owned")&&product.popupPageReady("owned"));
                            check(product.popupPageDownloadOnly("owned")==download&&product.popupPageDocumentReady("owned")!=download);
                            check(initial.calls==3&&backend.resumes.get()==1&&backend.continues.get()==1&&backend.guards.get()==1&&backend.nativeOpens.get()==1&&backend.detaches.get()==0);
                            check(renderer[0].worlds.created==(download?0:variant.equals("route-nested")||variant.startsWith("human-network-descendants")?4:1)&&!renderer[0].worlds.seenPageCode&&renderer[0].worlds.evaluations==0);
                            if(download){
                                MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");check(downloads.list().length==1&&backend.bodyClaims.get()==0);
                                if(variant.equals("download-stream")){String id=downloads.list()[0].id;downloads.accept(id,65536,3000);waitFor(()->{try{return downloads.get(id).terminal();}catch(IOException error){throw new AssertionError(error);}},host,true);check(downloads.get(id).verified&&downloads.get(id).received==3&&downloads.sourcesClean());}
                            }
                            if(variant.equals("parent-close")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);check(product.popupPageReady("owned")&&product.popupOwner("owned").current());}
                            if(variant.equals("parent-before-world")){waitFor(product::preparationSettled,host,true);check(product.popupPageReady("owned")&&product.popupOwner("owned").current());}
                            if(variant.startsWith("capture-"))frameRouteEvidence=capturePopup(variant,host,backend,product,exact[0],renderer[0]);
                            if(variant.startsWith("opener-"))frameRouteEvidence=preparePopupOpener(variant,host,backend,product,exact[0],renderer[0],source.source);
                            if(variant.startsWith("input-"))frameRouteEvidence=inputPopup(variant,host,backend,product,exact[0],renderer[0],inputSurface);
                            if(variant.startsWith("navigation-"))frameRouteEvidence=navigatePopupProduct(variant,host,backend,product,exact[0],renderer[0]);
                            if(variant.startsWith("human-request-"))frameRouteEvidence=publicHumanRequest(variant.substring("human-request-".length()),directory,host,backend,product,exact[0],renderer[0],inputSurface);
                            else if(variant.startsWith("human-resources-"))frameRouteEvidence=humanResourceControl(variant.substring("human-resources-".length()),directory,host,backend,product,exact[0],renderer[0],inputSurface);
                            else if(variant.startsWith("human-"))frameRouteEvidence=humanPopupTransfer(variant,host,backend,product,exact[0],renderer[0],inputSurface);
                            if(variant.startsWith("control-"))frameRouteEvidence=rejectedPopupControl(variant.substring("control-".length()),host,backend,product,exact[0],renderer[0]);
                            if(variant.startsWith("natural-"))frameRouteEvidence=naturalPopupProduct(variant,host,backend,product,exact[0],renderer[0]);
                            if(variant.equals("document-change")){check(source.views.navigation("owned",exact[0]));check(!product.popupPageDocumentReady("owned")&&product.popupNetworkReady("owned")&&exact[0].destroyed==0);}
                            if(variant.startsWith("route-")){
                                int queries=0;boolean success=!java.util.Arrays.asList("route-stale","route-channel","route-hidden","route-manual","route-download","route-effect","route-malformed","route-late","route-late-hidden").contains(variant);
                                if(variant.equals("route-parent")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);check(product.popupPageDocumentReady("owned"));}
                                if(variant.equals("route-stale"))check(source.views.navigation("owned",exact[0]));
                                if(variant.equals("route-channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                                if(variant.equals("route-hidden"))exact[0].shown=false;
                                if(variant.equals("route-manual"))product.manual.add("owned");
                                JSONObject command=new JSONObject().put("command","frames").put("agent",true).put("action","list");
                                if(variant.equals("route-malformed"))command.put("sessionId","proof-real");
                                if(variant.equals("route-effect")){
                                    product.frameRpc("owned",command);queries++;waitFor(()->product.preparationReplies==1,host,true);
                                    JSONObject rootFrame=product.preparationReply.getJSONArray("frames").getJSONObject(0);
                                    command.put("action","evaluate").put("frame",rootFrame.getString("id")).put("document",rootFrame.getString("document")).put("script","({ok:true,text:'managed'})");
                                }
                                boolean hold=variant.equals("route-busy")||variant.startsWith("route-late");if(hold)renderer[0].holdMethod="Page.getFrameTree";
                                int prior=product.preparationReplies;product.frameRpc("owned",command);queries++;
                                if(hold){
                                    waitFor(()->renderer[0].held!=null,host,true);check(product.preparationReplies==prior);
                                    if(variant.equals("route-busy")){product.frameRpc("owned",command);queries++;check(product.preparationReplies==prior+1&&product.preparationReply.getJSONObject("error").getString("native_code").equals("cdp_worker_busy"));}
                                    else if(variant.equals("route-late-hidden"))exact[0].shown=false;
                                    else check(source.views.navigation("owned",exact[0]));
                                    renderer[0].release(backend.wire);
                                }
                                final int expected=queries;waitFor(()->product.preparationReplies==expected,host,true);
                                JSONObject result=product.preparationReply;
                                if(result.optBoolean("ok")!=success)throw new AssertionError("Product frame route outcome: "+variant+" "+result);
                                check(product.preparationReplyError==null&&result.optBoolean("ok")==success);
                                if(success){
                                    org.json.JSONArray frames=result.getJSONArray("frames");check(frames.length()==(variant.equals("route-nested")?4:1));
                                    check(product.popupPageDocumentReady("owned")&&renderer[0].worlds.evaluations==0);
                                    if(variant.equals("route-repeat")){
                                        String first=frames.getJSONObject(0).getString("document");product.frameRpc("owned",command);queries++;
                                        final int repeated=queries;waitFor(()->product.preparationReplies==repeated,host,true);
                                        check(product.preparationReply.optBoolean("ok")&&product.preparationReply.getJSONArray("frames").getJSONObject(0).getString("document").equals(first));
                                    }
                                }else{
                                    String code=result.getJSONObject("error").getString("native_code");
                                    String expectedCode=variant.equals("route-effect")?"cdp_popup_preparation_required":variant.equals("route-malformed")?"cdp_frame_request_invalid":variant.equals("route-stale")?"cdp_binding_owner_changed":variant.startsWith("route-late")?"cdp_worker_cancelled":"cdp_popup_page_document_unavailable";
                                    if(!code.equals(expectedCode))throw new AssertionError("Frame rejection "+variant+": "+code+" expected "+expectedCode);checks.incrementAndGet();
                                    check(!result.getJSONObject("error").getBoolean("evaluation_may_have_occurred")&&renderer[0].worlds.evaluations==0);
                                }
                                check(backend.nativeOpens.get()==1&&backend.opens.get()==1&&backend.closes.get()==0&&source.sends==1);
                                frameRouteEvidence=new JSONObject().put("actual_product_frame_route",true).put("commands",queries).put("reply_ok",success)
                                    .put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get())
                                    .put("native_effects",renderer[0].worlds.evaluations).put("provider_qualified",false).put("ui_published",false);
                            }
                            if(!variant.startsWith("human-")&&!variant.startsWith("control-")&&!variant.equals("route-channel")&&!variant.equals("natural-channel")&&!variant.equals("route-manual")&&!variant.equals("input-channel")&&!variant.equals("input-focus-retired")&&!variant.equals("navigation-no-commit")&&!variant.endsWith("source-close"))failure("cdp_popup_page_busy",()->product.duplicatePopupPage("owned"));
                        }
                        check(source.sends==1&&product.configured==1&&product.popupLinks.isEmpty());
                    }catch(Exception|Error error){primary=error;System.err.println("Product page "+variant+" accepted="+accepted.get()+" failed="+failed.get()+" code="+reason[0]);throw error;}
                    finally{
                        try{
                            worldRelease.countDown();Handler.fail=false;if(handle[0]!=null)handle[0].close();product.closePopupNetwork("owned");
                            if((variant.startsWith("human-")||variant.startsWith("control-"))&&exact[0]!=null&&exact[0].destroyed==0&&product.views.owns("owned",exact[0]))product.retire("owned",exact[0]);
                            product.retirePreparation("cdp_popup_preparation_cancelled");
                            waitFor(()->{
                                if(!acknowledged[0]&&source.consumed!=null){source.ack();acknowledged[0]=true;}
                                if(!detachSent[0]&&exact[0]!=null&&exact[0].destroyed>0&&backend.closes.get()==0){try{backend.wire.add(detached("proof-real"));detachSent[0]=true;}catch(Exception error){throw new AssertionError(error);}}
                                product.resume();product.pollPopupPage("owned");product.pollPopupNetwork("owned");product.pollPublicClose();
                                if(frameRouteEvidence!=null&&frameRouteEvidence.optBoolean("actual_human_network")&&frameRouteEvidence.optBoolean("wire_failed")&&backend.closes.get()!=0){
                                    MdoBrowserCdpPopupWorker.Result receipt=request.result();return receipt!=null&&receipt.terminal&&exact[0]!=null&&exact[0].destroyed==1&&product.downloadBudget.used()==0&&queue.retainedTickets()==0;
                                }
                                if(product.popupNetworkCount()!=0&&backend.closes.get()!=0&&!product.popupHandoff("owned").detached()){
                                    MdoBrowserCdpPopupWorker.Result receipt=request.result();return receipt!=null&&receipt.terminal&&exact[0]!=null&&exact[0].destroyed==1;
                                }
                                return product.preparationSettled()&&product.popupNetworkCount()==0;
                            },host,true);
                            check(product.pendingHandoffs()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0&&exact[0].destroyed==1);
                            if(product.popupNetworkCount()!=0){boolean humanFailure=frameRouteEvidence!=null&&frameRouteEvidence.optBoolean("actual_human_network")&&frameRouteEvidence.optBoolean("wire_failed");
                                if(failed.get()==0&&!humanFailure)throw new AssertionError("Ready-page cleanup lost its detach: "+product.popupDiagnostic("owned")+" close="+backend.closeSite+" sent="+detachSent[0]);check((failed.get()==1||humanFailure)&&!product.popupProtocolDrained("owned")&&MdoBrowserCdpPopupBroker.creationBlocked());}
                            check(product.downloadBudget.used()==0&&queue.retainedTickets()==0&&backend.threads.size()==1&&host.threads.containsAll(backend.threads));
                            pageEvidence=new JSONObject().put("ready_callbacks",accepted.get()).put("failure_callbacks",failed.get())
                                .put("failure",reason[0]==null?JSONObject.NULL:reason[0]).put("download_only",download)
                                .put("worlds_created",renderer[0]==null?0:renderer[0].worlds.created)
                                .put("cleanup_confirmed",product.popupNetworkCount()==0)
                                .put("unconfirmed_creation_fenced",product.popupNetworkCount()!=0&&MdoBrowserCdpPopupBroker.creationBlocked())
                                .put("ui_published",false).put("provider_qualified",false);
                            if(variant.equals("loading-timeout")){
                                if(product.popupNetworkCount()==0&&detachSent[0])waitFor(()->!MdoBrowserCdpPopupBroker.creationBlocked(),host,true);
                                MdoBrowserCdpPopupWorker.Result terminal=request.result();MdoBrowserCdpPopupNative originalSource=product.preparationOriginal("source",MdoBrowserCdpPopupNative.class);
                                loadingRetirementEvidence=new JSONObject().put("transport_closed",backend.closes.get()).put("transport_close_site",backend.closeSite==null?JSONObject.NULL:backend.closeSite)
                                    .put("event_failure",backend.eventFailure==null?JSONObject.NULL:backend.eventFailure).put("event_failure_site",backend.eventFailureSite==null?JSONObject.NULL:backend.eventFailureSite)
                                    .put("popup",product.popupDiagnostic("owned")).put("original_detach_emitted",detachSent[0]).put("native_destroyed",exact[0].destroyed)
                                    .put("source_failure",originalSource.failure()==null?JSONObject.NULL:originalSource.failure()).put("source_pending",originalSource.rejectionPending())
                                    .put("source_confirmed",originalSource.confirmed()).put("source_released",originalSource.released())
                                    .put("worker_code",terminal==null||terminal.code==null?JSONObject.NULL:terminal.code).put("worker_terminal",terminal!=null&&terminal.terminal)
                                    .put("worker_source_released",terminal!=null&&terminal.sourceReleased).put("worker_cleanup_confirmed",terminal!=null&&terminal.processCleanupConfirmed);
                                loadingRetirementEvidence.put("broker_creation_blocked",MdoBrowserCdpPopupBroker.creationBlocked());
                                if(product.popupNetworkCount()!=0||!detachSent[0]||MdoBrowserCdpPopupBroker.creationBlocked())throw new AssertionError("Loading retirement did not drain original detach: "+loadingRetirementEvidence);
                            }
                            if(renderer[0]!=null)renderer[0].wire.close();
                        }catch(Exception|Error error){if(primary!=null)primary.addSuppressed(error);else throw error;}
                    }
                }
            }
        }finally{Handler.fail=false;android.os.SystemClock.now=Long.MIN_VALUE;}
    }
    private static JSONObject restorePublished(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        JSONObject inventory=new JSONObject().put("command","frames").put("agent",true).put("action","list");
        int reads=0;boolean rejected=false;
        if(variant.equals("hidden")){
            product.frameRpc("owned",inventory);reads++;
            check(product.publicCounts.getOrDefault("frames",0)==reads&&!product.publicReplies.get("frames").getBoolean("ok")&&renderer.worlds.created==1&&!product.popupPageDocumentReady("owned"));
        }
        product.bounds(view,new JSONObject().put("browser","owned").put("visible",true));
        if(variant.equals("parent")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);}
        if(variant.equals("cancelled"))product.invalidatePopupDocument("owned","input_cancelled");
        if(variant.equals("stale"))check(product.views.navigation("owned",view));
        if(variant.equals("manual"))product.manual.add("owned");
        if(variant.equals("channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
        if(variant.equals("unsupported"))renderer.worlds.unsupported=true;
        if(variant.equals("unique"))renderer.worlds.ignoreUnique=true;
        boolean held=variant.equals("busy")||variant.equals("late")||variant.equals("close");
        if(held)renderer.holdMethod="Page.createIsolatedWorld";
        product.frameRpc("owned",inventory);reads++;
        if(held){
            waitFor(()->renderer.held!=null,host,true);check(!product.popupPageDocumentReady("owned")&&product.popupDocumentPending("owned"));
            if(variant.equals("busy")){
                product.frameRpc("owned",inventory);reads++;
                check(product.publicCounts.getOrDefault("frames",0)==1&&!product.publicReplies.get("frames").getBoolean("ok")&&product.publicReplies.get("frames").getJSONObject("error").getString("native_code").equals("cdp_worker_busy"));
                check(renderer.worlds.created==2&&!product.popupPageDocumentReady("owned"));
            }else if(variant.equals("late")){
                product.bounds(view,new JSONObject().put("browser","owned").put("visible",false));
                product.bounds(view,new JSONObject().put("browser","owned").put("visible",true));
                product.frameRpc("owned",inventory);reads++;
                check(product.publicCounts.getOrDefault("frames",0)==1&&product.publicReplies.get("frames").getJSONObject("error").getString("native_code").equals("cdp_popup_page_restore_busy"));
            }else product.publicClose("owned");
            renderer.release(backend.wire);
        }
        final int expected=reads;waitFor(()->product.publicCounts.getOrDefault("frames",0)==expected,host,true);
        JSONObject first=product.publicReplies.get("frames");boolean negative=java.util.Arrays.asList("cancelled","stale","manual","channel","unsupported","unique","late","close").contains(variant);
        if(negative){
            check(!first.getBoolean("ok")&&!product.popupPageDocumentReady("owned"));rejected=true;
            if(variant.equals("late")){
                product.frameRpc("owned",inventory);reads++;final int next=reads;waitFor(()->product.publicCounts.getOrDefault("frames",0)==next,host,true);
                check(product.publicReplies.get("frames").getBoolean("ok")&&product.popupPageDocumentReady("owned")&&renderer.worlds.created==3);
            }else if(!variant.equals("close")){
                int calls=renderer.worlds.createCalls;renderer.worlds.unsupported=false;renderer.worlds.ignoreUnique=false;
                product.frameRpc("owned",inventory);reads++;
                check(product.publicCounts.getOrDefault("frames",0)==reads&&!product.publicReplies.get("frames").getBoolean("ok")&&renderer.worlds.createCalls==calls);
            }
        }else{
            if(!first.optBoolean("ok"))throw new AssertionError("Original hidden popup could not restore its document: "+first);
            check(product.popupPageDocumentReady("owned")&&renderer.resets==1&&renderer.worlds.created==2);
            JSONObject oldRoot=first.getJSONArray("frames").getJSONObject(0);
            int cycles=variant.equals("quota")?15:variant.equals("repeat")?3:variant.equals("old-pin")||variant.equals("viewport")?1:0;
            for(int i=0;i<cycles;i++){
                if(variant.equals("viewport"))product.invalidatePopupDocument("owned","input_viewport_changed");
                else {product.bounds(view,new JSONObject().put("browser","owned").put("visible",false));product.bounds(view,new JSONObject().put("browser","owned").put("visible",true));}
                check(!product.popupPageDocumentReady("owned"));product.frameRpc("owned",inventory);reads++;final int next=reads;waitFor(()->product.publicCounts.getOrDefault("frames",0)==next,host,true);
                JSONObject result=product.publicReplies.get("frames");
                if(variant.equals("quota")&&i==14){check(!result.getBoolean("ok")&&!product.popupPageDocumentReady("owned")&&renderer.worlds.created==16);rejected=true;}
                else check(result.getBoolean("ok")&&product.popupPageDocumentReady("owned")&&renderer.worlds.created==3+i);
            }
            if(variant.equals("old-pin")){
                product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","capture_owner").put("frame",oldRoot.getString("id")).put("document",oldRoot.getString("document")));reads++;
                final int next=reads;waitFor(()->product.publicCounts.getOrDefault("frames",0)==next,host,true);
                check(!product.publicReplies.get("frames").getBoolean("ok")&&renderer.worlds.created==3);rejected=true;
            }
        }
        check(backend.opens.get()==1&&backend.nativeOpens.get()==1&&renderer.worlds.evaluations==0&&product.root.adds==1);
        return new JSONObject().put("actual_frame_router",true).put("reads",reads).put("negative_checked",rejected).put("ready",product.popupPageDocumentReady("owned"))
            .put("worlds",renderer.worlds.created).put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("native_effects",renderer.worlds.evaluations)
            .put("no_url_replay",true).put("same_document_quota",true).put("framework_qualified",false).put("provider_qualified",false);
    }
    private static Object originalField(Object owner,String name)throws Exception {java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static boolean insideOriginal(String type,String method){for(StackTraceElement site:Thread.currentThread().getStackTrace())if(site.getClassName().equals("org.xserver.android."+type)&&site.getMethodName().equals(method))return true;return false;}
    private static boolean sourceResponseCapture(MdoBrowserCdpPopupSource source){try{Object paused=originalField(originalField(source,"session"),"paused");return insideOriginal("MdoBrowserCdpPopupSource","capture")&&paused!=null&&String.valueOf(originalField(paused,"key")).startsWith("S|");}catch(Exception error){throw new AssertionError(error);}}
    private static void ledgerRetirementBoundary(Backend backend,MdoBrowserCdpPopupBroker.Handoff exact,String variant)throws Exception {
        @SuppressWarnings("unchecked") List<MdoBrowserCdpPopupBroker.Candidate> candidates=(List<MdoBrowserCdpPopupBroker.Candidate>)originalField(backend.originalBroker,"candidates");
        MdoBrowserCdpPopupSource found=null;for(MdoBrowserCdpPopupBroker.Candidate candidate:candidates)if(originalField(candidate,"handoff")==exact)found=(MdoBrowserCdpPopupSource)originalField(candidate,"sourcePeer");
        check(found!=null);final MdoBrowserCdpPopupSource source=found;
        if(variant.equals("retirement-ledger-listener")){
            java.lang.reflect.Field field=source.getClass().getDeclaredField("receiver");field.setAccessible(true);
            field.set(source,(MdoBrowserCdpSession.Events)(method,child,params)->{backend.ledgerBoundaryTrips.incrementAndGet();source.close();throw new IOException("cdp_request_owner_closed");});return;
        }
        if(variant.startsWith("retirement-ledger-frame")||variant.equals("retirement-ledger-fault")){
            java.lang.reflect.Field field=source.getClass().getDeclaredField("scope");field.setAccessible(true);BooleanSupplier original=(BooleanSupplier)field.get(source);
            MdoBrowserCdpNetworkRequests requests=(MdoBrowserCdpNetworkRequests)originalField(source,"requests");
            // Preserve the exact original guard's boolean snapshot, then retire
            // that same source. This models close immediately after admission;
            // it never creates a positive native proof or replacement binding.
            field.set(source,(BooleanSupplier)()->{boolean live=original.getAsBoolean();if(live&&sourceResponseCapture(source)&&backend.ledgerBoundaryTrips.compareAndSet(0,1)){if(variant.equals("retirement-ledger-fault"))requests.close();else source.close();}return live;});
        }else{
            Object frames=originalField(source,"frames");java.lang.reflect.Field field=frames.getClass().getDeclaredField("frames");field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Object,Object> original=(Map<Object,Object>)field.get(frames);
            field.set(frames,new java.util.AbstractMap<Object,Object>(){
                public Set<Map.Entry<Object,Object>> entrySet(){return original.entrySet();}
                public Object put(Object key,Object value){return original.put(key,value);}public Object remove(Object key){return original.remove(key);}public void clear(){original.clear();}
                public Object get(Object key){if(insideOriginal("MdoBrowserCdpSession","retainSourceRequest")&&backend.ledgerBoundaryTrips.compareAndSet(0,1))source.close();return original.get(key);}
            });
        }
    }
    private static JSONObject nestedPopup(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView opener,Backend.DocumentRenderer parent)throws Exception {
        boolean refused=java.util.Arrays.asList("nested-unrelated","nested-navigation","nested-manual","nested-invalidated").contains(variant),cancelled=variant.equals("nested-cancelled"),parentClosed=variant.equals("nested-parent-close");
        MdoBrowserPopups<WebView> core=new MdoBrowserPopups<>(opener,new MdoBrowserPopups.Scheduler(){
            public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}
        },new MdoBrowserPopups.Budget(8),()->product.views.owns("owned",opener),()->product.views.epoch("owned",opener),blocked->{
            check(host.ui.onThread());if(blocked){product.views.input("owned",opener).invalidate("popup_pending");product.invalidatePopupDocument("owned","popup_pending");}
        });product.windows.put("owned",core);
        AtomicInteger prepared=new AtomicInteger();product.prepareOwned("owned",prepared::incrementAndGet,(code,effect)->{throw new AssertionError(code);});waitFor(()->prepared.get()==1,host,true);
        int before=product.publicCounts.getOrDefault("frames",0);product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
        waitFor(()->product.publicCounts.getOrDefault("frames",0)==before+1,host,true);JSONObject oldRoot=product.publicReplies.get("frames").getJSONArray("frames").getJSONObject(0);
        final WebView[] child={null};final Runnable[] consumed={null};AtomicInteger sent=new AtomicInteger(),acknowledged=new AtomicInteger();boolean[] detached={false};Backend.DocumentRenderer[] renderer={null};
        Backend.SourceRenderer source=new Backend.SourceRenderer("source-complete","proof-second");
        backend.documentRenderers.put("proof-real",parent);backend.managedResumeHook=null;backend.proofTargets.add("target-proof-second");
        backend.managedResumeChildHook=session->{if(session.equals("proof-second")){check(source.calls==3);backend.sourceRenderers.remove("proof-second");backend.documentRenderers.put("proof-second",renderer[0]);}};
        backend.managedFlowHook=()->{if(child[0]!=null)host.ui.post(()->{if(product.views.owns("grandchild",child[0])){check(product.views.navigation("grandchild",child[0]));product.loading.put("grandchild",false);}});};
        boolean[] negative={false},restored={false},published={false},closedParent={false};Throwable primary=null;JSONObject evidence=new JSONObject();
        try{
            if(variant.equals("nested-unrelated")){
                product.invalidatePopupDocument("owned","popup_pending");
                failure("cdp_popup_preparation_required",()->product.publicCreateFrom("owned","unrelated","grandchild",new JSONObject()));
                int count=product.publicCounts.getOrDefault("frames",0);product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
                waitFor(()->product.publicCounts.getOrDefault("frames",0)==count+1,host,true);check(!product.publicReplies.get("frames").getBoolean("ok")&&sent.get()==0&&parent.worlds.created==1);negative[0]=true;
            }else{
            check(core.open(new MdoBrowserPopups.Transport<WebView>(){
                private final Object identity=new Object();public Object token(){return identity;}
                public void send(WebView exact,Runnable receipt){check(host.ui.onThread());sent.incrementAndGet();if(exact==null){acknowledged.incrementAndGet();receipt.run();try{backend.wire.add(detached("proof-second"));detached[0]=true;}catch(Exception error){throw new AssertionError(error);}return;}
                    child[0]=exact;consumed[0]=receipt;backend.nativeTargets.put("target-proof-second",proofPeer(exact,"success",null,null));backend.sourceRenderers.put("proof-second",source);
                    try{renderer[0]=new Backend.DocumentRenderer(exact,"proof-second");renderer[0].frames.omitChild=true;renderer[0].frames.includeLocal=false;}catch(Exception error){throw new AssertionError(error);}product.loading.put("grandchild",true);
                }
            },false));check(core.busy()&&!product.popupPageDocumentReady("owned"));
            if(variant.equals("nested-duplicate"))product.invalidatePopupDocument("owned","popup_pending");
            if(variant.equals("nested-blocked")){
                int count=product.publicCounts.getOrDefault("frames",0),worlds=parent.worlds.createCalls;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
                waitFor(()->product.publicCounts.getOrDefault("frames",0)==count+1,host,true);check(!product.publicReplies.get("frames").getBoolean("ok")&&parent.worlds.createCalls==worlds&&backend.resumes.get()==1);negative[0]=true;
                product.inputRpc("owned",new JSONObject().put("command","input").put("agent",true).put("navigation",product.views.epoch("owned",opener)).put("kind","move").put("x",20).put("y",30)
                    .put("input_document","d-00000000-0000-4000-8000-000000000001").put("input_ref","e7").put("frame_chain",new org.json.JSONArray().put(new JSONObject().put("frame",oldRoot.getString("id")).put("document",oldRoot.getString("document")))));
                waitFor(()->product.publicCounts.getOrDefault("input",0)==1,host,true);JSONObject denied=product.publicReplies.get("input");check(!denied.getBoolean("accepted")&&!denied.getBoolean("effect_may_have_occurred")&&denied.getInt("delivered_steps")==0&&parent.worlds.createCalls==worlds);
            }
            String pending=core.snapshot().id;
            if(variant.equals("nested-foreign")){failure("cdp_popup_native_request_changed",()->product.publicCreateFrom("owned","foreign","grandchild",new JSONObject()));check(sent.get()==0&&core.snapshot().id.equals(pending));negative[0]=true;}
            if(refused){
                if(variant.equals("nested-navigation")){check(product.views.navigation("owned",opener));product.invalidatePopupDocument("owned","input_navigation");}
                else if(variant.equals("nested-manual")){product.manual.add("owned");product.invalidatePopupDocument("owned","manual_takeover");}
                else product.invalidatePopupDocument("owned","input_cancelled");
                failure("cdp_popup_preparation_required",()->product.publicCreateFrom("owned",pending,"grandchild",new JSONObject()));check(sent.get()==0&&product.views.get("grandchild")==null);negative[0]=true;
            }else{
            backend.wire.add(attached("proof-second","target-proof-real"));product.publicCreateFrom("owned",pending,"grandchild",new JSONObject());
            if(cancelled){waitFor(()->sent.get()==1,host,true);product.cancelHandoff("grandchild");}
            waitFor(()->{if(consumed[0]!=null&&acknowledged.get()==0){acknowledged.incrementAndGet();consumed[0].run();}product.pollPopupPage("grandchild");return product.publicCounts.getOrDefault("nested-create",0)==1;},host,true);
            JSONObject created=product.publicReplies.get("nested-create");if(cancelled){check(!created.optBoolean("ok")&&product.root.adds==1&&!product.publicLinkReady("grandchild"));negative[0]=true;}
            else{
            if(!created.optBoolean("ok"))throw new AssertionError("Nested native creation failed: "+created+" proofs="+backend.nativeOpens.get()+" guards="+backend.guards.get()+" view="+(child[0]==null?"none":child[0].destroyed+"/"+child[0].evaluations+"/"+child[0].cleanup)+" preparation="+product.ownedPreparationDiagnostic("owned"));published[0]=true;
            check(sent.get()==1&&acknowledged.get()==1&&child[0]!=null&&product.publicLinkReady("grandchild")&&product.root.children.contains(opener)&&product.root.children.contains(child[0]));
            int calls=product.publicCounts.getOrDefault("frames",0);product.frameRpc("grandchild",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
            waitFor(()->product.publicCounts.getOrDefault("frames",0)==calls+1,host,true);check(product.publicReplies.get("frames").getBoolean("ok")&&product.popupPageDocumentReady("grandchild"));
            int restore=product.publicCounts.getOrDefault("frames",0);product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
            waitFor(()->product.publicCounts.getOrDefault("frames",0)==restore+1,host,true);JSONObject recovered=product.publicReplies.get("frames");
            if(!recovered.optBoolean("ok"))throw new AssertionError("Nested opener did not restore: "+recovered);
            check(product.popupPageDocumentReady("owned")&&!oldRoot.getString("document").equals(recovered.getJSONArray("frames").getJSONObject(0).getString("document")));
            restored[0]=true;
            if(variant.equals("nested-old-pin")){
                int count=product.publicCounts.getOrDefault("frames",0),worlds=parent.worlds.createCalls;product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","capture_owner").put("frame",oldRoot.getString("id")).put("document",oldRoot.getString("document")));
                waitFor(()->product.publicCounts.getOrDefault("frames",0)==count+1,host,true);check(!product.publicReplies.get("frames").getBoolean("ok")&&parent.worlds.createCalls==worlds);negative[0]=true;
            }
            if(parentClosed){
                product.publicClose("owned");boolean[] sentDetach={false};
                waitFor(()->{if(opener.destroyed==1&&!sentDetach[0])try{backend.wire.add(detached("proof-real"));sentDetach[0]=true;}catch(Exception error){throw new AssertionError(error);}product.resume();product.pollPopupNetwork("owned");product.pollPublicClose();return product.views.get("owned")==null&&product.network("owned")==null&&!product.publicClosePending();},host,true);
                check(product.publicCounts.getOrDefault("close",0)==1&&product.publicReplies.get("close").getBoolean("world_cleanup_confirmed")&&product.publicLinkReady("grandchild")&&child[0].destroyed==0&&backend.closes.get()==0);closedParent[0]=true;
                int count=product.publicCounts.getOrDefault("frames",0);product.frameRpc("grandchild",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.publicCounts.getOrDefault("frames",0)==count+1,host,true);check(product.publicReplies.get("frames").getBoolean("ok"));
            }
            check(backend.opens.get()==1&&backend.nativeOpens.get()==2&&backend.resumes.get()==2&&backend.continues.get()==2&&backend.closes.get()==0);
            }}}
            return evidence.put("actual_publication",published[0]).put("opener_document_restored",restored[0]).put("negative_checked",negative[0]).put("independent_opener_close",closedParent[0])
                .put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("provider_qualified",false);
        }catch(Exception|Error error){primary=error;throw error;
        }finally{
            try{
            product.cancelHandoff("grandchild");if(child[0]!=null)product.publicClose("grandchild","nested-close");
            waitFor(()->{if(child[0]!=null&&child[0].destroyed==1&&!detached[0])try{backend.wire.add(detached("proof-second"));detached[0]=true;}catch(Exception error){throw new AssertionError(error);}
                product.resume();product.pollPopupNetwork("grandchild");product.pollPublicClose();return product.views.get("grandchild")==null&&!product.publicClosePending()&&product.network("grandchild")==null;
            },host,true);
            product.retireOwnedPreparation("owned");waitFor(()->product.ownedPreparationSettled("owned"),host,true);
            check(child[0]==null||child[0].destroyed==1);boolean openerLive=product.views.owns("owned",opener);check(product.root.children.size()==(openerLive?1:0)&&(openerLive?product.root.children.contains(opener):opener.destroyed==1));
            if(child[0]!=null)check(product.publicCounts.getOrDefault("nested-close",0)==1&&product.publicReplies.get("nested-close").getBoolean("world_cleanup_confirmed"));
            evidence.put("native_sends",sent.get()).put("native_consumptions",acknowledged.get()).put("native_view_closed",child[0]==null||child[0].destroyed==1).put("native_destroy_count",child[0]==null?0:child[0].destroyed).put("original_prepare_settled",product.ownedPreparationSettled("owned"));
            }catch(Exception|Error error){if(primary!=null)primary.addSuppressed(error);else throw error;}
        }
    }
    private static JSONObject popupDocumentState(String variant,CdpNetworkWorkerProbe.Case host,PopupProductHost product,WebView view,Backend backend,Backend.DocumentRenderer renderer,JSONObject startup)throws Exception {
        boolean download=variant.equals("state-download"),hidden=variant.equals("state-hidden"),viewport=variant.equals("state-viewport"),restoring=variant.equals("state-restoring"),busy=variant.equals("state-busy");
        boolean nativeRestore=variant.equals("state-context-isolated-restore")||variant.equals("state-native-loaded");
        if(nativeRestore){
            // Positive fresh inventory needs an authentic original-source root
            // commit. A UI epoch/URL alone must never satisfy that guard.
            int delivered=backend.frameLoadingEvents.get();
            backend.wire.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId",renderer.root).put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId",renderer.frames.rootLoader).put("url",renderer.frames.rootUrl))));
            waitFor(()->backend.frameLoadingEvents.get()>delivered,host,true);
            product.frameRpcNamed("owned","state-setup",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
            waitFor(()->product.publicCounts.getOrDefault("state-setup",0)==1,host,true);
            check(product.publicReplies.get("state-setup").getBoolean("ok")&&product.inspectDocumentState("owned").getBoolean("ready"));
        }
        PopupProductHost.MdoBrowserDocument original=product.documents.get("owned");int queries=original.worldQueries,calls=renderer.worlds.createCalls,effects=renderer.worlds.evaluations,proofs=backend.nativeOpens.get(),connections=backend.opens.get();
        JSONObject state=product.inspectDocumentState("owned");check(state.getString("adapter").equals("android-native-frames-v1"));
        check(state.getBoolean("installed")==!download&&state.getBoolean("ready")==!download&&state.isNull("native_document")&&product.documents.get("owned").worldQueries==queries&&product.inspectFrameRouting("owned"));
        failure("cdp_popup_document_route_required",()->product.legacyRpc("owned"));
        if(variant.equals("state-starting"))check(startup!=null&&startup.getString("adapter").equals("android-native-frames-v1")&&!startup.getBoolean("installed")&&!startup.getBoolean("ready")&&startup.getString("phase").equals("starting")&&startup.isNull("native_document"));
        String phase=download?"download_only":"ready";boolean expectedReady=!download;
        AtomicInteger rejects=new AtomicInteger();MdoBrowserPopups<WebView> pending=null;
        boolean nativeContext=variant.startsWith("state-context-"),foreignContext=variant.equals("state-context-foreign");
        boolean isolatedContext=variant.startsWith("state-context-isolated"),isolatedChild=variant.equals("state-context-isolated-child");
        boolean nativeLoading=variant.equals("state-native-loading")||variant.equals("state-native-loaded");
        if(nativeContext){
            long epoch=product.views.epoch("owned",view);int delivered=backend.contextEvents.get();
            boolean cleared=variant.equals("state-context-cleared");
            int context=foreignContext?999999:1;
            if(isolatedContext){
                CdpWorldsProbe.Context selected=null;
                for(CdpWorldsProbe.Context row:renderer.worlds.contexts.values())if(row.source==null&&row.frame.equals(isolatedChild?"root-local":"frame-owned")&&(selected==null||row.id>selected.id))selected=row;
                check(selected!=null);final CdpWorldsProbe.Context originalContext=selected;
                context=selected.id;renderer.worlds.contexts.values().removeIf(row->row==originalContext);
            }
            backend.wire.add(new JSONObject().put("method",cleared?"Runtime.executionContextsCleared":"Runtime.executionContextDestroyed")
                .put("sessionId",renderer.root).put("params",cleared?new JSONObject():new JSONObject().put("executionContextId",context)));
            waitFor(()->backend.contextEvents.get()>delivered,host,true);
            check(product.views.epoch("owned",view)==epoch&&product.popupPageDocumentReady("owned"));
            if(!foreignContext&&!isolatedChild){phase="document_unavailable";expectedReady=false;}
        }
        if(nativeLoading){
            long epoch=product.views.epoch("owned",view);int delivered=backend.frameLoadingEvents.get();
            backend.wire.add(new JSONObject().put("method","Page.frameStartedLoading").put("sessionId",renderer.root).put("params",new JSONObject().put("frameId","frame-owned")));
            waitFor(()->backend.frameLoadingEvents.get()>delivered,host,true);
            if(nativeRestore){
                int started=backend.frameLoadingEvents.get();
                backend.wire.add(new JSONObject().put("method","Page.frameStoppedLoading").put("sessionId",renderer.root).put("params",new JSONObject().put("frameId","frame-owned")));
                waitFor(()->backend.frameLoadingEvents.get()>started,host,true);
            }
            check(product.views.epoch("owned",view)==epoch&&product.popupPageDocumentReady("owned"));phase="document_unavailable";expectedReady=false;
        }
        if(hidden)product.bounds(view,new JSONObject().put("browser","owned").put("visible",false));
        if(viewport||restoring)product.invalidatePopupDocument("owned","input_viewport_changed");
        if(hidden||viewport) {phase="suspended";expectedReady=false;}
        if(variant.equals("state-navigation")){check(product.views.navigation("owned",view));phase="document_changed";expectedReady=false;}
        if(variant.equals("state-manual")){product.manual.add("owned");phase="owner_unavailable";expectedReady=false;}
        if(variant.equals("state-channel")){product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());phase="owner_unavailable";expectedReady=false;}
        if(variant.equals("state-retiring")){product.closePopupNetwork("owned");phase="owner_unavailable";expectedReady=false;}
        if(variant.equals("state-pending")){
            pending=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}},new MdoBrowserPopups.Budget(8),()->product.views.owns("owned",view),()->product.views.epoch("owned",view),blocked->{if(blocked){product.views.input("owned",view).invalidate("popup_pending");product.invalidatePopupDocument("owned","popup_pending");}});
            product.windows.put("owned",pending);check(pending.open(new MdoBrowserPopups.Transport<WebView>(){final Object identity=new Object();public Object token(){return identity;}public void send(WebView child,Runnable consumed){check(child==null);rejects.incrementAndGet();consumed.run();}},false));phase="popup_pending";expectedReady=false;
        }
        if(restoring||busy){renderer.holdMethod="Page.getFrameTree";product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->renderer.held!=null,host,true);phase=restoring?"restoring":"busy";expectedReady=false;}
        int worlds=renderer.worlds.createCalls,steps=renderer.worlds.evaluations;
        for(int i=0;i<5;i++){
            JSONObject current=product.inspectDocumentState("owned");if(current.getBoolean("ready")!=expectedReady||!phase.equals(current.getString("phase")))throw new AssertionError("Incorrect popup document state "+variant+": "+current);
            check(product.inspectFrameRouting("owned")&&current.getString("adapter").equals("android-native-frames-v1")&&current.isNull("native_document")&&current.getInt("pending")==((restoring||busy)?1:0));
            check(current.getBoolean("installed")==java.util.Arrays.asList("state-ready","state-starting","state-legacy","state-busy","state-context-foreign","state-context-isolated-child").contains(variant));
        }
        check(renderer.worlds.createCalls==worlds&&renderer.worlds.evaluations==steps&&rejects.get()==0&&original.worldQueries==queries);
        if(hidden){product.bounds(view,new JSONObject().put("browser","owned").put("visible",true));check(!product.inspectDocumentState("owned").getBoolean("ready")&&renderer.worlds.createCalls==worlds);}
        if(hidden||viewport){product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.publicCounts.getOrDefault("frames",0)==1,host,true);check(product.publicReplies.get("frames").getBoolean("ok")&&product.inspectDocumentState("owned").getBoolean("ready"));}
        if(restoring||busy){renderer.release(backend.wire);waitFor(()->product.publicCounts.getOrDefault("frames",0)==1,host,true);check(product.inspectDocumentState("owned").getBoolean("ready"));}
        if(nativeRestore){
            long epoch=product.views.epoch("owned",view);product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));waitFor(()->product.publicCounts.getOrDefault("frames",0)==1,host,true);
            JSONObject reply=product.publicReplies.get("frames"),after=product.inspectDocumentState("owned");
            if(!reply.getBoolean("ok")||!after.getBoolean("ready")||product.views.epoch("owned",view)!=epoch)throw new AssertionError("Native readiness recovery "+variant+": reply="+reply+" state="+after);
            check(true);
        }
        if(pending!=null){pending.cancel();check(rejects.get()==1&&!product.inspectDocumentState("owned").getBoolean("ready"));}
        product.manual.remove("owned");
        if(variant.equals("state-legacy")){
            JSONObject legacy=product.inspectDocumentState("root"),absent=product.inspectDocumentState("missing");product.inspectLegacyRoute("root");
            check(!product.inspectFrameRouting("root")&&!product.inspectFrameRouting("missing")&&legacy.getString("adapter").equals("android-main-document-v1")&&legacy.getBoolean("installed")&&legacy.getBoolean("ready")&&legacy.getString("native_document").startsWith("fd-")&&legacy.getInt("native_budget_limit")==32);
            check(!absent.getBoolean("installed")&&!absent.getBoolean("ready")&&absent.isNull("native_document")&&absent.getString("phase").equals("world_unavailable"));
        }
        check(original.worldQueries==queries);
        if(!hidden&&!viewport&&!restoring&&!nativeRestore)check(renderer.worlds.createCalls==calls);check(renderer.worlds.evaluations==effects&&backend.nativeOpens.get()==proofs&&backend.opens.get()==connections);
        return new JSONObject().put("native_adapter",true).put("read_only",true).put("legacy_world_queries",original.worldQueries-queries).put("phase_checked",phase).put("ready_checked",expectedReady).put("startup_checked",startup!=null).put("explicit_restore_checked",hidden||viewport||restoring||nativeRestore).put("provider_qualified",false);
    }
    private static JSONObject frontendRecovery(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        boolean navigation=variant.startsWith("frontend-navigation-"),cancel=variant.equals("frontend-navigation-cancel"),natural=variant.startsWith("frontend-natural-"),contextLoss=variant.startsWith("frontend-context-");
        long originalEpoch=product.views.epoch("owned",view);int contextLosses=0;boolean contextArmed=false,contextDispatchJoined=false,rawReceiveNotDispatch=false;
        boolean transition=variant.equals("frontend-navigation-same")||variant.equals("frontend-navigation-bfcache");
        Backend.SourceRenderer navigator=navigation?new Backend.SourceRenderer(transition?"source-"+variant.substring("frontend-".length()):variant.equals("frontend-navigation-no-commit")?"source-navigation-no-commit":"source-navigation-flow"):null;
        if(navigation){renderer.navigationPeer=navigator;renderer.frames.rootUrl=renderer.context.rootUrl=navigator.url;renderer.wrongNavigationDocument=variant.equals("frontend-navigation-mismatch");}
        int readyWaits=0,navigationReads=0,deferredPreparations=0,earlyCommits=0;boolean released=false;
        if(navigation&&!transition)renderer.holdMethod="Page.createIsolatedWorld";
        final String document="d-00000000-0000-4000-8000-000000000169";AtomicInteger scripts=new AtomicInteger(),preparations=new AtomicInteger();
        final CdpWorldsProbe.Context[] observedContext={null};
        product.windows.put("owned",new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){public long now(){return host.time.get();}public void at(Runnable task,long at){}public void remove(Runnable task){}},
            new MdoBrowserPopups.Budget(8),()->product.views.owns("owned",view),()->product.views.epoch("owned",view),blocked->{if(blocked){product.views.input("owned",view).invalidate("popup_pending");product.invalidatePopupDocument("owned","popup_pending");}}));
        renderer.worlds.evaluation=(context,expression)->{
            observedContext[0]=context;String prefix="({local_frame:true}).";int begin=expression.lastIndexOf(prefix);if(begin<0)throw new AssertionError("Unexpected frontend expression");
            String tail=expression.substring(begin+prefix.length()),method=tail.substring(0,tail.indexOf('('));
            if(!method.equals("describe")&&!method.equals("observe"))throw new AssertionError("Unexpected frontend method");scripts.incrementAndGet();
            String currentDocument=navigation?String.format(java.util.Locale.ROOT,"d-00000000-0000-4000-8000-%012d",176+navigator.navigations):contextLoss?String.format(java.util.Locale.ROOT,"d-00000000-0000-4000-8000-%012d",500+context.id):natural?"d-00000000-0000-4000-8000-000000000276":document;
            JSONObject result=new JSONObject().put("ok",true).put("document",currentDocument).put("ready","complete").put("url",navigation?navigator.url:natural?renderer.frames.rootUrl:"https://original.invalid/page").put("title","Controlled popup")
                .put("viewport",new JSONObject().put("width",320).put("height",240));
            if(method.equals("observe"))result.put("text",navigation?"Original native navigation "+navigator.navigations:natural?"Natural original native popup":"Restored original native popup").put("elements",new org.json.JSONArray()).put("frames",new org.json.JSONArray().put(new JSONObject().put("id","f0").put("dom",true))).put("visited",1).put("truncated",false);
            return result;
        };
        product.bounds(view,new JSONObject().put("browser","owned").put("visible",false));product.bounds(view,new JSONObject().put("browser","owned").put("visible",true));
        if(natural){int received=backend.receives.get();naturalCommit(backend,renderer,"load-frontend-natural",true);waitFor(()->backend.receives.get()>received+2,host,true);product.nativePageCallback("owned");}
        if(transition||contextLoss&&!variant.endsWith("-unproven")){int received=backend.receives.get();backend.wire.add(new JSONObject().put("method","Page.frameNavigated").put("sessionId",renderer.root).put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId",renderer.frames.rootLoader).put("url",renderer.frames.rootUrl))));waitFor(()->backend.receives.get()>received,host,true);}
        check(!product.inspectDocumentState("owned").getBoolean("ready"));
        System.out.println(new JSONObject().put("event","ready").put("provider_qualified",false));System.out.flush();
        java.io.BufferedReader input=new java.io.BufferedReader(new java.io.InputStreamReader(System.in,StandardCharsets.UTF_8));String line;
        while((line=input.readLine())!=null){
            if(line.length()>300000)throw new AssertionError("Frontend fixture request limit");JSONObject request=new JSONObject(line);int id=request.getInt("id");String command=request.getString("command");
            JSONObject result=null;
            if(navigation||contextLoss){Handler.drain();host.ui.drain();}
            if(command.equals("fixture_finish")){System.out.println(new JSONObject().put("id",id).put("result",new JSONObject().put("ok",true)));System.out.flush();break;}
            if(contextLoss&&command.equals("fixture_context_scope")){
                String scope=request.getJSONObject("params").getString("scope");check(product.views.epoch("owned",view)==originalEpoch&&renderer.worlds.created==2);
                if(scope.equals("hidden"))view.shown=false;else if(scope.equals("manual"))product.manual.add("owned");else {check(scope.equals("restore"));view.shown=true;product.manual.remove("owned");}
                result=new JSONObject().put("ok",true).put("worlds",renderer.worlds.created);
            }else if(contextLoss&&command.equals("fixture_context_loss")){
                check(product.popupPageDocumentReady("owned")&&contextLosses==0);int receives=backend.receives.get(),events=backend.contextEvents.get();
                CountDownLatch received=new CountDownLatch(1),release=new CountDownLatch(1);
                backend.receivedHook=bytes->{try{String method=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).optString("method");if(!method.equals("Runtime.executionContextsCleared")&&!method.equals("Runtime.executionContextDestroyed"))return;received.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Native context fixture release expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}catch(JSONException error){throw new AssertionError(error);}};
                try{
                if(variant.contains("-cleared")){renderer.worlds.contexts.clear();backend.wire.add(new JSONObject().put("method","Runtime.executionContextsCleared").put("sessionId",renderer.root).put("params",new JSONObject()));}
                else {CdpWorldsProbe.Context exact=observedContext[0];check(exact!=null&&exact.source==null&&exact.frame.equals("frame-owned"));renderer.worlds.contexts.values().removeIf(value->value==exact);backend.wire.add(new JSONObject().put("method","Runtime.executionContextDestroyed").put("sessionId",renderer.root).put("params",new JSONObject().put("executionContextId",exact.id)));}
                waitFor(()->received.getCount()==0,host,true);
                check(backend.receives.get()>receives&&backend.contextEvents.get()==events);rawReceiveNotDispatch=true;
                }finally{release.countDown();backend.receivedHook=null;}
                waitFor(()->backend.contextEvents.get()>events,host,true);contextDispatchJoined=true;contextLosses++;
                check(product.views.epoch("owned",view)==originalEpoch&&product.popupPageDocumentReady("owned"));result=new JSONObject().put("ok",true).put("navigation",originalEpoch);
            }else if(contextLoss&&command.equals("fixture_context_arm")){
                check(product.popupPageDocumentReady("owned")&&!contextArmed&&contextLosses==0);contextArmed=true;
                if(variant.contains("-cleared"))renderer.worlds.clearDuringEval=true;else renderer.worlds.destroyDuringEval=true;result=new JSONObject().put("ok",true);
            }else if(contextLoss&&command.equals("fixture_context_disarm")){
                check(contextArmed&&contextLosses==0);renderer.worlds.clearDuringEval=renderer.worlds.destroyDuringEval=false;waitFor(()->backend.contextEvents.get()>0,host,true);contextDispatchJoined=true;contextLosses++;result=new JSONObject().put("ok",true).put("navigation",product.views.epoch("owned",view));
            }else if((navigation||contextLoss)&&command.equals("state"))result=product.frontendState("owned");
            else if(navigation&&java.util.Arrays.asList("navigate","back","forward","reload").contains(command)){if(transition&&product.deferOwnedPreparationWatch("owned"))deferredPreparations++;result=product.frontendNavigate("owned",command,request.getJSONObject("params"));if(transition){long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!product.frontendCommitPublished("owned")){if(System.nanoTime()>=end)throw new AssertionError("Original native transition did not commit before UI delivery");Thread.sleep(1);}earlyCommits++;}}
            else if((navigation||contextLoss)&&command.equals("downloads"))result=product.frontendDownloads("owned",request.getJSONObject("params"));
            else if(navigation&&command.equals("navigation_cancel")){product.cancelNavigation("owned",request.getJSONObject("params").getString("navigation_ref"));result=product.publicReplies.get("cancel-navigation");if(renderer.held!=null){renderer.release(backend.wire);released=true;}}
            else if(command.equals("state"))result=new JSONObject().put("automation",true).put("platform","android-webview").put("loading",false).put("navigation",product.views.epoch("owned",view))
                .put("frame_automation",product.inspectFrameRouting("owned")).put("isolated_world",product.inspectDocumentState("owned"))
                .put("popup_preparation_required",true).put("popup_preparation_capability",true);
            else if(command.equals("popup_prepare")){
                int count=product.publicCounts.getOrDefault("preparation",0);preparations.incrementAndGet();product.preparationRpc("owned",request.getJSONObject("params"));
                waitFor(()->product.publicCounts.getOrDefault("preparation",0)==count+1,host,true);result=product.publicReplies.get("preparation");
            }else if(command.equals("frames")){
                int count=product.publicCounts.getOrDefault("frames",0);JSONObject params=request.getJSONObject("params");params.put("command","frames");product.frameRpc("owned",params);
                if(navigation&&params.has("navigation_ref"))navigationReads++;
                waitFor(()->product.publicCounts.getOrDefault("frames",0)==count+1,host,true);result=product.publicReplies.get("frames");
                if(navigation&&!result.optBoolean("ok"))System.err.println("Frontend inventory denial: action="+params.optString("action")+", code="+result.optJSONObject("error")+", document="+product.inspectDocumentState("owned")+", navigation="+product.inspectNavigation("owned"));
            }else throw new AssertionError("Unsupported frontend fixture command");
            if(navigation&&command.equals("state")){
                JSONObject originalNavigation=result.optJSONObject("native_navigation");
                if(originalNavigation!=null&&originalNavigation.optBoolean("page_current")&&!originalNavigation.optBoolean("page_ready")){
                    readyWaits++;if(!cancel&&readyWaits>=2&&renderer.held!=null){check(navigationReads==0);renderer.release(backend.wire);released=true;}
                }
            }
            System.out.println(new JSONObject().put("id",id).put("result",result));System.out.flush();
        }
        check(backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.closes.get()==0&&view.destroyed==0);
        return new JSONObject().put("actual_product_frontend",true).put("worlds",renderer.worlds.created).put("expressions",scripts.get()).put("preparations",preparations.get())
            .put("document_ready",product.inspectDocumentState("owned").getBoolean("ready")).put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("provider_qualified",false)
            .put("native_navigation_commands",backend.nativeNavigationCommands.get()).put("readiness_waits",readyWaits).put("original_world_released",released).put("navigation_reads",navigationReads)
            .put("context_losses",contextLosses).put("same_ui_epoch",!contextLoss||product.views.epoch("owned",view)==originalEpoch).put("context_dispatch_join",contextDispatchJoined).put("raw_receive_not_dispatch",rawReceiveNotDispatch)
            .put("same_document_transitions",navigator==null?0:navigator.sameDocumentTransitions).put("cache_restores",navigator==null?0:navigator.cacheRestores).put("deferred_preparations",deferredPreparations).put("early_commits",earlyCommits).put("transition_network_requests",navigator==null?0:navigator.transitionNetworkRequests);
    }
    private static JSONObject fileCall(CdpNetworkWorkerProbe.Case host,PopupProductHost product,String id,JSONObject request)throws Exception {
        int count=product.publicCounts.getOrDefault(id,0);product.uploadRpc("owned",id,request);
        waitFor(()->product.publicCounts.getOrDefault(id,0)==count+1,host,true);JSONObject reply=product.publicReplies.get(id);
        if(reply==null)throw new AssertionError("Original popup file reply missing: "+product.preparationReplyError);return reply;
    }
    private static String fileDigest(byte[] bytes)throws Exception {
        StringBuilder value=new StringBuilder();for(byte item:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))value.append(String.format(java.util.Locale.ROOT,"%02x",item&255));return value.toString();
    }
    /** Actual product upload/clear/coordinator/picker/file/URI core, on this
     * original proved popup worker and wire. DOM and native UI callbacks are
     * controlled, so Android framework/provider grants remain unqualified. */
    private static JSONObject protectedFiles(String variant,String directory,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        boolean publicTakeover=variant.equals("files-public-takeover");
        java.nio.file.Path files=java.nio.file.Paths.get(directory).resolveSibling("popup-upload-files");product.enableUpload("owned",files);
        MdoBrowserCdpMarker marker=product.continueDocument("owned",view,product.views.epoch("owned",view),product.channel.current());
        CdpOwnerProbe.Server owners=new CdpOwnerProbe.Server(renderer.worlds,()->host.time.addAndGet(20000));
        CdpFileInputProbe.Nodes nodes=new CdpFileInputProbe.Nodes(owners,()->host.time.addAndGet(20000),marker::close);
        java.util.concurrent.atomic.AtomicReference<String> inventory=new java.util.concurrent.atomic.AtomicReference<>();
        host.worker.submitExisting(marker,5000,false,(binding,deadline)->{
            try{org.json.JSONArray rows=binding.frames().inventory(deadline).getJSONArray("frames");for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);nodes.nodes.put(binding.frames().select(row.getString("id"),row.getString("document")).raw,row);
            }return new byte[]{1};}catch(JSONException error){throw new IOException("cdp_fixture_inventory_invalid",error);}
        },result->{inventory.set(result.code==null?"ok":result.code);});waitFor(()->inventory.get()!=null,host,true);check("ok".equals(inventory.get()));
        CdpUploadProbe.Picker picker=new CdpUploadProbe.Picker(nodes,new java.util.concurrent.atomic.AtomicLong(1000));picker.selected=false;
        CdpFileClearProbe.Peer clear=new CdpFileClearProbe.Peer(nodes,picker);renderer.frames.extra=clear;
        JSONObject raw=nodes.request(variant.equals("files-nested")?"leaf-frame":"frame-owned"),target=new JSONObject().put("document",raw.get("local_document")).put("ref",raw.get("ref")).put("frame_chain",raw.getJSONArray("frame_chain"));
        long epoch=product.views.epoch("owned",view);AtomicInteger grants=new AtomicInteger(),responses=new AtomicInteger();String[] tokens=new String[2];
        MdoBrowserUploadUris uris=product.fileUris();java.util.concurrent.atomic.AtomicReference<Throwable> callbackFailure=new java.util.concurrent.atomic.AtomicReference<>();
        try{
            picker.onOpen=()->{
                if(variant.equals("files-cancel")||variant.equals("files-page-close"))return;
                new Handler(Looper.getMainLooper()).post(()->{try{product.openPicker("owned",(readers,current)->{
                    responses.incrementAndGet();if(readers==null)return;if(!current.getAsBoolean()||readers.length!=2)throw new AssertionError("Foreign popup file grant");
                    try{for(int i=0;i<readers.length;i++)tokens[i]=uris.publish(readers[i]);}catch(IOException error){callbackFailure.set(error);throw new IllegalStateException("Controlled URI publication failed",error);}grants.incrementAndGet();picker.selected=true;
                });}catch(Throwable error){callbackFailure.set(error);}});
            };
            boolean clearing=variant.startsWith("files-clear");JSONObject receipt;byte[] bytes=new byte[131075];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i*17+3);
            if(clearing){
                JSONObject request=new JSONObject(target.toString()).put("action","clear").put("upload_key","popup-clear").put("navigation",epoch);
                if(variant.equals("files-clear-cancel")){
                    renderer.holdMethod="DOM.setFileInputFiles";product.uploadRpc("owned","clear",request);waitFor(()->renderer.held!=null,host,true);
                    JSONObject pending=fileCall(host,product,"clear-status",new JSONObject().put("action","clear_status").put("upload_key","popup-clear"));check(pending.getBoolean("pending")&&clear.clears==1);
                    fileCall(host,product,"clear-cancel",new JSONObject().put("action","clear_cancel").put("upload_key","popup-clear"));renderer.release(backend.wire);
                    waitFor(()->product.publicReplies.get("clear")!=null,host,true);receipt=product.publicReplies.get("clear");check(!receipt.getBoolean("ok")&&receipt.getBoolean("dispatched")&&!receipt.getBoolean("verified"));
                }else{
                    receipt=fileCall(host,product,"clear",request);if(!receipt.getBoolean("ok"))throw new AssertionError("Protected clear failed: "+receipt);
                    check(receipt.getBoolean("cleared")&&receipt.getBoolean("verified")&&receipt.getBoolean("native_job_drained")&&receipt.getBoolean("cleanup_confirmed"));
                    check(fileCall(host,product,"clear-repeat",request).getString("ticket").equals(receipt.getString("ticket"))&&clear.clears==1&&clear.verified==1);
                }
                check(product.uploadFiles()==0&&grants.get()==0&&picker.opens==0&&picker.commits==0);
            }else{
                JSONObject begin=new JSONObject(target.toString()).put("action","begin").put("navigation",epoch).put("upload_key","popup-upload").put("timeout_ms",120000)
                    .put("files",new org.json.JSONArray().put(new JSONObject().put("name","native-\u5185\u5bb9 #% +.bin").put("size",bytes.length).put("sha256",fileDigest(bytes)))
                        .put(new JSONObject().put("name","empty.bin").put("size",0).put("sha256",fileDigest(new byte[0]))));
                JSONObject staged=fileCall(host,product,"begin",begin);check(staged.getBoolean("ok")&&staged.getBoolean("assignment_supported"));String ticket=staged.getString("ticket");
                for(int at=0;at<bytes.length;at+=65536)check(fileCall(host,product,"chunk",new JSONObject().put("action","chunk").put("ticket",ticket).put("navigation",epoch).put("file",0).put("offset",at)
                    .put("base64",java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(bytes,at,Math.min(bytes.length,at+65536))))).getBoolean("ok"));
                check(fileCall(host,product,"seal",new JSONObject().put("action","seal").put("ticket",ticket).put("navigation",epoch)).getBoolean("sealed"));
                JSONObject assign=new JSONObject(target.toString()).put("action","assign").put("ticket",ticket).put("navigation",epoch).put("timeout_ms",120000);
                if(variant.equals("files-cancel")||variant.equals("files-page-close")){
                    product.uploadRpc("owned","assign",assign);waitFor(()->picker.opens==1||callbackFailure.get()!=null,host,true);
                    if(variant.equals("files-page-close"))product.publicClose("owned");else fileCall(host,product,"cancel",new JSONObject().put("action","cancel").put("ticket",ticket));
                    waitFor(()->product.publicReplies.get("assign")!=null,host,true);receipt=product.publicReplies.get("assign");
                    check(!receipt.getBoolean("ok")&&!receipt.getBoolean("accepted")&&!receipt.getBoolean("verified")&&grants.get()==0&&picker.commits==0);
                }else{
                    receipt=fileCall(host,product,"assign",assign);if(!receipt.getBoolean("ok"))throw new AssertionError("Protected upload failed: "+receipt);
                    check(receipt.getBoolean("accepted")&&receipt.getBoolean("completed")&&receipt.getBoolean("verified")&&receipt.getBoolean("retained")&&receipt.getBoolean("cleanup_confirmed"));
                    check(!receipt.getBoolean("events_trusted")&&grants.get()==1&&responses.get()==1&&picker.opens==1&&picker.commits==1);
                    check(fileCall(host,product,"assign-repeat",assign).getBoolean("completed")&&picker.opens==1&&picker.commits==1);
                    if(variant.equals("files-retention"))android.os.SystemClock.now+=120001;
                    if(publicTakeover){
                        backend.humanDetachMode="flow";int worlds=renderer.worlds.created,views=WebView.constructions;
                        product.publicControl("owned","file-takeover","takeover",false);
                        waitFor(()->{product.pollPublicControl("owned");return product.publicCounts.containsKey("file-takeover");},host,true);
                        JSONObject control=product.publicReplies.get("file-takeover");check(control.getBoolean("ok")&&control.getBoolean("manual")&&control.getBoolean("network_cleanup_confirmed"));
                        check(product.publicCounts.get("file-takeover")==1&&!marker.lease().current()&&product.manualControl("owned")&&view.destroyed==0&&renderer.worlds.created==worlds&&WebView.constructions==views&&uris.grants()==2);
                    }
                    java.util.concurrent.atomic.AtomicReference<Throwable> readFailure=new java.util.concurrent.atomic.AtomicReference<>();
                    Thread reader=new Thread(()->{try{
                        for(int i=0;i<tokens.length;i++){
                            MdoBrowserUploadUris.Session session=uris.reserve(tokens[i]);try{
                                if(!session.enter()||!session.returned())throw new AssertionError("Original URI read admission failed");
                                byte[] output=new byte[i==0?bytes.length:1];int count=session.read(0,output.length,output,5000);
                                if(count!=(i==0?bytes.length:0)||i==0&&!java.util.Arrays.equals(output,bytes))throw new AssertionError("Original popup upload bytes changed");
                            }finally{session.released();}
                        }
                    }catch(Throwable error){readFailure.set(error);}},"popup-original-file-reader");reader.start();reader.join(7000);check(!reader.isAlive()&&readFailure.get()==null&&uris.descriptors()==0);
                    JSONObject status=fileCall(host,product,"upload-status",new JSONObject().put("action","status").put("ticket",ticket));check(status.getBoolean("retained")&&status.getBoolean("completed"));
                    if(publicTakeover){product.publicClose("owned","file-native-close");waitFor(()->{product.pollPublicClose();return view.destroyed==1&&product.uploadFiles()==0&&uris.grants()==0;},host,true);check(product.publicCounts.get("file-native-close")==1&&product.publicReplies.get("file-native-close").getBoolean("world_cleanup_confirmed"));}
                    else {JSONObject released=fileCall(host,product,"release",new JSONObject().put("action","release").put("ticket",ticket).put("confirmed",true));check(released.getBoolean("released")&&!released.getBoolean("retained"));}
                    check(uris.grants()==0&&product.uploadFiles()==0&&nodes.objects.isEmpty()&&nodes.groups.isEmpty()&&owners.objects.isEmpty());
                }
            }
            if(callbackFailure.get()!=null)throw new AssertionError("Original popup callback failed",callbackFailure.get());
            check(backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.closes.get()==0);
            JSONObject proof=new JSONObject().put("actual_product_file_flow",true).put("original_file_worker",true).put("native_grants",grants.get()).put("native_responses",responses.get())
                .put("opens",picker.opens).put("commits",picker.commits).put("clears",clear.clears).put("retained_read_verified",!clearing&&!variant.equals("files-cancel")&&!variant.equals("files-page-close"))
                .put("staging_expiry_tested",variant.equals("files-retention")).put("nested",variant.equals("files-nested")).put("framework_grant_qualified",false).put("provider_qualified",false);
            if(publicTakeover)proof.put("public_takeover",true).put("native_close_released_files",true);return proof;
        }finally{uris.close();}
    }
    /** The product upload/clear/chooser routes must continue the already bound
     * popup document, whose registry owner is its native handoff. The old UI
     * channel lookup rejects this exact live document. This tests routing, not
     * Android URI grants or a provider's actual FileList assignment. */
    private static JSONObject uploadContinuation(String variant,CdpNetworkWorkerProbe.Case host,Backend backend,PopupProductHost product,WebView view,Backend.DocumentRenderer renderer)throws Exception {
        long epoch=product.views.epoch("owned",view);MdoBrowserChannel<PopupProductHost.WebMessagePort>.Lease lease=product.channel.current();
        MdoBrowserCdpMarker original=product.continueDocument("owned",view,epoch,lease);
        try{product.ordinaryContinuation("owned",view,epoch);throw new AssertionError("Old channel lookup accepted native handoff");}
        catch(IllegalStateException rejected){check("Original CDP binding unavailable".equals(rejected.getMessage()));}
        check(original.lease().current()&&original.lease().busy()&&renderer.worlds.created==1);
        if(variant.equals("continuation-loss")){
            failure("cdp_binding_owner_changed",()->product.continueDocument("owned",view,epoch+1,lease));
            failure("cdp_binding_owner_changed",()->product.continueDocument("owned",new WebView(),epoch,lease));
            product.manual.add("owned");failure("cdp_binding_owner_changed",()->product.continueDocument("owned",view,epoch,lease));product.manual.remove("owned");
            product.browserPaused=true;failure("cdp_binding_owner_changed",()->product.continueDocument("owned",view,epoch,lease));product.browserPaused=false;
            product.loading.put("owned",true);failure("cdp_binding_owner_changed",()->product.continueDocument("owned",view,epoch,lease));product.loading.put("owned",false);
            product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
            failure("cdp_binding_owner_changed",()->product.continueDocument("owned",view,epoch,lease));
            check(renderer.worlds.created==1&&renderer.worlds.evaluations==0);
        }else{
            if(variant.equals("continuation-parent")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);}
            if(variant.equals("continuation-restore")){
                product.bounds(view,new JSONObject().put("browser","owned").put("visible",false));
                failure("cdp_binding_owner_changed",()->product.continueDocument("owned",view,epoch,lease));
                product.bounds(view,new JSONObject().put("browser","owned").put("visible",true));
                failure("cdp_popup_page_document_unavailable",()->product.continueDocument("owned",view,epoch,lease));
                check(renderer.worlds.created==1&&renderer.worlds.evaluations==0);
                int count=product.publicCounts.getOrDefault("frames",0);product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
                waitFor(()->product.publicCounts.getOrDefault("frames",0)==count+1,host,true);
                if(!product.publicReplies.get("frames").getBoolean("ok"))throw new AssertionError("Original continuation restore failed: "+product.publicReplies.get("frames"));checks.incrementAndGet();
                check(!original.lease().current());original=product.continueDocument("owned",view,epoch,lease);check(renderer.worlds.created==2);
            }
            final MdoBrowserCdpMarker continued=original;java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> receipt=new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<JSONObject> decoded=new java.util.concurrent.atomic.AtomicReference<>();
            host.worker.submitExisting(continued,3000,false,(binding,deadline)->binding.frames().inventory(deadline).toString().getBytes(StandardCharsets.UTF_8),result->{
                // Result bytes belong to the synchronous native completion and
                // are wiped immediately on return. Retain only decoded data.
                if(result.code==null)try{decoded.set(new JSONObject(new String(result.bytes,StandardCharsets.UTF_8)));}catch(JSONException error){throw new AssertionError(error);}receipt.set(result);
            });
            waitFor(()->receipt.get()!=null,host,true);check(receipt.get().code==null&&!receipt.get().effectMayHaveOccurred);
            JSONObject inventory=decoded.get();check(inventory!=null&&inventory.getBoolean("ok")&&inventory.getJSONArray("frames").length()==1);
            check(product.continueDocument("owned",view,epoch,lease)==continued&&renderer.worlds.evaluations==0);
            if(variant.equals("continuation-ready")){product.invalidatePopupDocument("owned","input_cancelled");failure("cdp_popup_page_document_unavailable",()->product.continueDocument("owned",view,epoch,lease));}
        }
        check(backend.opens.get()==1&&backend.nativeOpens.get()==1&&backend.closes.get()==0);
        return new JSONObject().put("original_document_continuation",true).put("old_channel_lookup_refused",true)
            .put("worlds",renderer.worlds.created).put("expressions",renderer.worlds.evaluations).put("provider_assignment_qualified",false);
    }
    private static void productPublication(String mode,String directory)throws Exception {
        boolean restoring=mode.startsWith("preparation-restore-");String variant=mode.substring((restoring?"preparation-restore-":"preparation-publication-").length());Backend backend=new Backend();android.os.SystemClock.now=1000;
        boolean hidden=restoring||variant.equals("hidden"),download=variant.startsWith("download")||variant.equals("state-download"),invalid=variant.equals("invalid-layout")||variant.equals("foreign-pending");
        boolean cancelled=variant.equals("close-add")||variant.equals("close-before-allocation")||variant.equals("close-world");
        boolean changed=variant.equals("channel-add")||variant.equals("stale-add"),broken=variant.equals("add-fail")||variant.equals("bounds-fail")||variant.startsWith("deadline-");
        boolean positive=!invalid&&!cancelled&&!changed&&!broken;
        boolean sourceCrash=variant.equals("source-crash"),ledgerFault=variant.equals("retirement-ledger-fault")||variant.equals("retirement-ledger-listener"),sourceFault=sourceCrash||ledgerFault;
        boolean unconfirmed=sourceFault||variant.equals("download-abort-mismatch")||variant.equals("download-abort-missing")||variant.equals("download-abort-rejected");
        boolean delayed=variant.equals("download-delayed-abort")||unconfirmed&&!sourceFault,claim=variant.equals("download-close-claim"),read=variant.equals("download-close-read");
        boolean frontendNavigation=variant.startsWith("frontend-navigation-")||variant.startsWith("frontend-natural-");PopupProductHost[] originalProduct={null};
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context original=host.source();host.ready(original);try(NativeSource source=new NativeSource(host,original,frontendNavigation?new InputSurface():null,frontendNavigation?(key,view,epoch,reason)->{PopupProductHost current=originalProduct[0];if(current!=null)current.lifecycleInvalidated(key,view,epoch,reason);}:null)){
                java.nio.file.Path root=java.nio.file.Paths.get(directory);java.nio.file.Files.createDirectories(root);
                try(MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->new MdoBrowserDownloadFiles(root),host.time::get)){
                    PopupProductHost product=new PopupProductHost(source.views);originalProduct[0]=product;product.bindPreparation(source.view,original.adapter,source.core,host.worker);
                    product.downloadQueue=queue;product.downloadBudget=new MdoBrowserCdpDownloads.Budget();product.loading.put("owned",true);
                    AtomicInteger prepared=new AtomicInteger();WebView[] exact={null};Backend.DocumentRenderer[] renderer={null};JSONObject[] restoration={null},startup={null};boolean[] detachSent={false},nullConsumed={false},abortDelivered={false},claimDelivered={false},parentClosed={!read},lateSent={false},negativeExpired={false};int[] afterLate={0};
                    Backend.SourceRenderer initial=new Backend.SourceRenderer("source-complete");Throwable primary=null;
                    source.beforeSend=child->{
                        if(child==null){check(invalid||variant.equals("close-before-allocation"));source.adopting=null;return;}
                        check(host.ui.onThread()&&child==source.views.get("owned"));exact[0]=child;source.adopting=child;child.nativeUrl="https://original.invalid/page";
                        backend.nativeTargets.put("target-proof-real",proofPeer(child,"success",null,null));backend.sourceRenderer=initial;
                        try{renderer[0]=new Backend.DocumentRenderer(child);boolean nested=variant.equals("files-nested")||variant.equals("state-context-isolated-child");renderer[0].frames.omitChild=!nested;renderer[0].frames.includeLocal=nested;}catch(Exception error){throw new AssertionError(error);}
                        if(variant.equals("state-starting"))try{check(product.inspectFrameRouting("owned"));startup[0]=product.inspectDocumentState("owned");failure("cdp_popup_document_route_required",()->product.legacyRpc("owned"));check(product.documents.get("owned").worldQueries==0);}catch(Exception error){throw new AssertionError(error);}
                        if(variant.equals("close-world"))renderer[0].holdMethod="Page.createIsolatedWorld";
                        backend.managedResumeMode="normal";
                        backend.responseMode=download?"source-complete":"source-pass";
                        backend.managedResumeHook=()->{backend.sourceRenderer=null;backend.documentRenderer=renderer[0];};
                        backend.managedFlowHook=()->{if(!download)host.ui.post(()->{check(source.views.navigation("owned",child));product.loading.put("owned",false);});};
                    };
                    backend.holdAbort=delayed;backend.holdClaim=claim;backend.holdRead=read;
                    if(variant.equals("add-fail"))product.root.failAdd=true;if(variant.equals("bounds-fail"))product.failBounds=true;
                    if(variant.equals("deadline-add"))product.root.adding=()->android.os.SystemClock.now+=9000;
                    if(variant.equals("deadline-bounds"))product.sizing=()->android.os.SystemClock.now+=9000;
                    if(variant.equals("close-add"))product.root.adding=()->product.publicClose("owned");
                    if(variant.equals("channel-add"))product.root.adding=()->product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                    if(variant.equals("stale-add"))product.root.adding=()->check(source.views.navigation("owned",exact[0]));
                    product.prepare(prepared::incrementAndGet,(code,effect)->{throw new AssertionError("Original preparation failed: "+code);});waitFor(()->prepared.get()==1,host,true);source.open();
                    try{
                        JSONObject box=new JSONObject().put("width",800).put("height",600);JSONObject command=new JSONObject().put("bounds",box).put("visible",!hidden).put("url","https://must-never-replay.invalid/");
                        if(variant.equals("invalid-layout"))box.put("width","invalid");
                        if(invalid){
                            try{product.publicCreate(source.core.snapshot().id+(variant.equals("foreign-pending")?"-foreign":""),"owned",command);throw new AssertionError("Invalid original popup admitted");}
                            catch(IOException|IllegalArgumentException error){check(error.getMessage().equals(variant.equals("invalid-layout")?"popup_layout_invalid":"cdp_popup_native_request_changed"));}
                            check(source.sends==0&&product.root.adds==0&&product.pendingHandoffs()==0&&source.views.get("owned")==null);
                        }else{
                            product.publicCreate(source.core.snapshot().id,"owned",command);
                            check(product.root.adds==0&&product.publicCounts.getOrDefault("create",0)==0);
                            // Mutating the caller's object cannot change the frozen native layout.
                            box.put("width",9999);command.put("visible",hidden);
                            if(variant.equals("duplicate"))failure("cdp_popup_handoff_busy",()->product.publicCreate(source.core.snapshot().id,"owned",command));
                            if(variant.equals("close-before-allocation"))product.publicClose("owned");
                            else{
                                backend.wire.add(attached("proof-real","owned"));waitFor(()->source.sends==1,host,true);source.ack();
                                if(variant.equals("close-world")){
                                    waitFor(()->{product.pollPopupPage("owned");return renderer[0].held!=null;},host,true);check(product.root.adds==0);
                                    product.publicClose("owned");renderer[0].release(backend.wire);
                                }
                            }
                            waitFor(()->{product.pollPopupPage("owned");return variant.equals("channel-add")?product.pendingHandoffs()==0:product.publicCounts.getOrDefault("create",0)==1;},host,true);
                            JSONObject result=product.publicReplies.get("create");
                            if(positive){
                                check(result!=null&&result.getBoolean("ok")&&result.getBoolean("created")&&result.getBoolean("popup_attached")&&result.getBoolean("downloads_intercepted")&&!result.getBoolean("initial_navigation_pending"));
                                check(product.root.adds==1&&product.root.children.contains(exact[0])&&source.views.get("owned")==exact[0]&&product.publicLinkReady("owned"));
                                check(product.locations.get("owned").equals("https://original.invalid/page")&&product.lastBounds.getJSONObject("bounds").getDouble("width")==800&&product.lastBounds.getBoolean("visible")==!hidden&&exact[0].shown==!hidden);
                                check(product.popupNetworkReady("owned")&&product.popupPageDocumentReady("owned")==(!hidden&&!download)&&renderer[0].worlds.evaluations==0);
                                if(hidden)check(!product.popupPageDocumentReady("owned")&&product.popupOwner("owned").current());
                                if(download)check(product.popupDownloads("owned").list().length==1&&backend.bodyClaims.get()==0&&renderer[0].worlds.created==0);
                                if(restoring){
                                    restoration[0]=restorePublished(variant,host,backend,product,exact[0],renderer[0]);
                                }
                                if(variant.startsWith("nested-"))frameRouteEvidence=nestedPopup(variant,host,backend,product,exact[0],renderer[0]);
                                if(variant.startsWith("state-"))frameRouteEvidence=popupDocumentState(variant,host,product,exact[0],backend,renderer[0],startup[0]);
                                if(variant.startsWith("frontend-"))frameRouteEvidence=frontendRecovery(variant,host,backend,product,exact[0],renderer[0]);
                                if(variant.startsWith("continuation-"))frameRouteEvidence=uploadContinuation(variant,host,backend,product,exact[0],renderer[0]);
                                if(variant.startsWith("files-"))frameRouteEvidence=protectedFiles(variant,directory,host,backend,product,exact[0],renderer[0]);
                                if(sourceCrash){
                                    backend.wire.add(new JSONObject().put("method","Inspector.targetCrashed").put("sessionId","proof-real").put("params",new JSONObject()));
                                    waitFor(()->backend.closes.get()>0,host,true);
                                    check("cdp_event_failed".equals(product.preparationWorkerCode())&&MdoBrowserCdpPopupBroker.creationBlocked());
                                }
                                if(variant.startsWith("retirement-ledger-")){
                                    ledgerRetirementBoundary(backend,product.popupHandoff("owned"),variant);
                                    backend.wire.add(variant.equals("retirement-ledger-listener")?new JSONObject().put("method","Runtime.executionContextsCleared").put("sessionId","proof-real").put("params",new JSONObject()):variant.endsWith("-active")?handoffPaused("managed-next",false):responsePaused("retiring-ledger","source-pass"));
                                    waitFor(()->backend.ledgerBoundaryTrips.get()==1||backend.closes.get()>0,host,true);int observed=backend.receives.get();
                                    waitFor(()->backend.receives.get()>observed+8||backend.closes.get()>0,host,true);
                                    if(ledgerFault)check(backend.closes.get()==1&&"cdp_event_failed".equals(product.preparationWorkerCode()));
                                    else if(backend.closes.get()!=0)throw new AssertionError("Exact source-ledger retirement poisoned original wire: "+backend.eventFailure+" "+backend.eventFailureSite+" send="+backend.sendFailure);
                                    check(!backend.continuedIds.contains("retiring-ledger")&&backend.bodyClaims.get()==0&&backend.responsePasses.get()==1);
                                }else if(variant.startsWith("retirement-")){
                                    MdoBrowserCdpPopupBroker.Handoff originalHandoff=product.popupHandoff("owned");
                                    backend.retiringClockScope=variant.substring("retirement-".length());
                                    if(backend.retiringClockScope.equals("managed"))backend.retiringClockScope="serviceManaged";
                                    // A scheduler boundary in the existing clock dependency:
                                    // retire after the first retirement read, inside current().
                                    backend.retiringClockHook=()->{host.time.incrementAndGet();originalHandoff.retireAuthority();};
                                    if(backend.retiringClockScope.endsWith("event"))backend.wire.add(new JSONObject().put("method","Runtime.executionContextsCleared").put("sessionId","proof-real").put("params",new JSONObject()));
                                    if(backend.retiringClockScope.equals("source-capture"))backend.wire.add(handoffPaused("retiring-original",false));
                                    waitFor(()->backend.retiringClockTrips.get()==1,host,true);int observed=backend.receives.get();
                                    waitFor(()->backend.receives.get()>observed+8||backend.closes.get()>0,host,true);
                                    if(backend.closes.get()!=0)throw new AssertionError("Deliberate concurrent retirement poisoned original wire: "+product.preparationWorkerCode());
                                    check(originalHandoff.retiring()&&!originalHandoff.current()&&product.popupOwner("owned").current()&&exact[0].destroyed==0&&backend.disables.get()==0);
                                    if(backend.retiringClockScope.startsWith("source-"))check(!backend.continuedIds.contains("retiring-original")&&backend.bodyClaims.get()==0);
                                }

                                if(claim||read){
                                    MdoBrowserCdpDownloads controller=product.popupDownloads("owned");controller.accept(controller.list()[0].id,65536,3000);
                                    waitFor(()->claim?backend.heldClaimReply!=null:backend.bodyReads.get()==1,host,true);
                                    check(backend.bodyClaims.get()==1&&backend.bodyCloses.get()==0&&controller.list()[0].received==0);
                                }
                                if(!restoring&&variant.equals("parent")){
                                    product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);
                                    product.frameRpc("owned",new JSONObject().put("command","frames").put("agent",true).put("action","list"));
                                    waitFor(()->product.publicCounts.getOrDefault("frames",0)==1,host,true);check(product.publicReplies.get("frames").getBoolean("ok"));
                                }
                            }else if(variant.equals("channel-add"))check(result==null&&product.publicCounts.getOrDefault("create",0)==0);
                            else check(result!=null&&!result.getBoolean("ok")&&!result.getBoolean("created")&&result.getJSONObject("native_create").getBoolean("created_may_have_occurred")==!variant.equals("close-before-allocation")&&!result.getJSONObject("native_create").getBoolean("network_cleanup_confirmed"));
                            check(product.pendingHandoffs()==0&&(variant.equals("close-before-allocation")||source.sends==1));
                        }
                    }catch(Exception|Error error){primary=error;System.err.println("Product publication "+variant+" create="+product.publicReplies.get("create")+" close="+product.publicReplies.get("close"));throw error;}
                    finally{
                        try{
                            if(!product.publicCounts.containsKey("close")&&!product.publicClosePending())product.publicClose("owned");
                            if(parentClosed[0])product.retirePreparation("cdp_popup_preparation_cancelled");
                            waitFor(()->{
                                if(sourceFault&&backend.closes.get()>0&&!negativeExpired[0]){negativeExpired[0]=true;android.os.SystemClock.now+=5001;host.time.addAndGet(5001);}
                                if(exact[0]==null&&!nullConsumed[0]&&source.consumed!=null){nullConsumed[0]=true;source.ack();}
                                if(!detachSent[0]&&exact[0]!=null&&exact[0].destroyed>0&&backend.closes.get()==0){try{backend.wire.add(detached("proof-real"));detachSent[0]=true;}catch(Exception error){throw new AssertionError(error);}}
                                // Queue the held receipt after the authentic
                                // native end event; that event cannot itself
                                // acknowledge this still-held claim/abort.
                                if(detachSent[0]&&product.network("owned")!=null){
                                    if(claim&&!claimDelivered[0]&&backend.heldClaimReply!=null){claimDelivered[0]=true;backend.wire.add(backend.heldClaimReply);}
                                    if(delayed&&!abortDelivered[0]&&backend.heldAbortReply!=null){
                                        check(!product.popupDownloads("owned").sourcesClean()&&!product.popupHandoff("owned").detached()&&product.publicCounts.getOrDefault("close",0)==0&&MdoBrowserCdpPopupBroker.creationBlocked());abortDelivered[0]=true;
                                        try{
                                            JSONObject receipt=new JSONObject(backend.heldAbortReply.toString());
                                            if(variant.equals("download-abort-mismatch"))receipt.put("sessionId","foreign-original");
                                            if(variant.equals("download-abort-rejected")){receipt.remove("result");receipt.put("error",new JSONObject().put("code",-32000).put("message","synthetic"));}
                                            if(!variant.equals("download-abort-missing"))backend.wire.add(receipt);
                                            if(variant.equals("download-abort-missing")){negativeExpired[0]=true;android.os.SystemClock.now+=5001;host.time.addAndGet(5001);}
                                        }catch(JSONException error){throw new AssertionError(error);}
                                    }
                                }
                                if(unconfirmed&&abortDelivered[0]&&!negativeExpired[0]&&backend.closes.get()>0){negativeExpired[0]=true;check(host.time.get()<5001);android.os.SystemClock.now+=5001;}
                                product.resume();product.pollPopupPage("owned");product.pollPopupNetwork("owned");product.pollPublicClose();
                                if(read&&product.popupNetworkCount()==0&&!lateSent[0]){
                                    check(backend.closes.get()==0&&backend.lateRead.get()>0);lateSent[0]=true;afterLate[0]=backend.receives.get()+12;
                                    try{backend.wire.add(new JSONObject().put("id",backend.lateRead.get()).put("sessionId","proof-real").put("result",new JSONObject().put("data","synthetic-late-body").put("eof",true)));}catch(JSONException error){throw new AssertionError(error);}
                                }
                                if(read&&lateSent[0]&&!parentClosed[0]&&backend.receives.get()>afterLate[0]){check(backend.closes.get()==0);parentClosed[0]=true;product.retirePreparation("cdp_popup_preparation_cancelled");}
                                return (unconfirmed||product.preparationSettled())&&(unconfirmed||product.popupNetworkCount()==0)&&product.publicCounts.getOrDefault("close",0)==1;
                            },host,true);
                            check(product.publicReplies.get("close").getBoolean("world_cleanup_confirmed")==!unconfirmed&&product.publicReplies.get("close").getBoolean("popup_cleanup_confirmed"));
                            if(unconfirmed){check(product.popupNetworkCount()==1&&(sourceFault?!product.popupProtocolDrained("owned"):!product.popupDownloads("owned").sourcesClean()));try{product.checkCreation();throw new AssertionError("Unconfirmed source did not fence construction");}catch(IllegalStateException error){check(error.getMessage().equals("network_cleanup_failed"));}}
                            if(delayed)check(abortDelivered[0]&&backend.responseAborts.get()==1);
                            if(claim||read)check(backend.bodyCloses.get()==1&&backend.responseAborts.get()==1&&backend.bodyReads.get()==(read?1:0));
                            if(read)check(lateSent[0]&&parentClosed[0]);
                            check(product.root.children.isEmpty()&&product.popupLinks.isEmpty()&&product.pendingHandoffs()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0);if(unconfirmed)check(MdoBrowserCdpPopupNative.retainedUiOwners()<=1&&MdoBrowserCdpPopupBroker.creationBlocked());else check(MdoBrowserCdpPopupNative.retainedUiOwners()==0);
                            check(source.sends==1&&nullConsumed[0]==(invalid||variant.equals("close-before-allocation")));
                            check(exact[0]==null||exact[0].destroyed==1);check(product.downloadBudget.used()==0&&queue.retainedTickets()==0&&backend.detaches.get()==(variant.equals("files-public-takeover")?1:0)&&backend.threads.size()==1);
                            if(restoring)check(product.retirementBoundaryChecks>=1);
                            publicationEvidence=new JSONObject().put("actual_product_creation",true).put("controlled_ui_publication_confirmed",positive).put("controlled_ui_adds",product.root.adds).put("create_replies",product.publicCounts.getOrDefault("create",0)).put("close_replies",product.publicCounts.getOrDefault("close",0))
                                .put("cleanup_confirmed",!unconfirmed).put("unconfirmed_creation_fenced",unconfirmed).put("active_stream_tested",claim||read).put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("no_url_replay",true).put("framework_qualified",false).put("provider_qualified",false)
                                .put("document_restore",restoration[0]==null?JSONObject.NULL:restoration[0]).put("protected_retirement_boundaries",product.retirementBoundaryChecks)
                                .put("concurrent_retirement",variant.startsWith("retirement-")).put("source_crash_contained",sourceCrash).put("ledger_fault_contained",ledgerFault);
                            product.stopUpload(java.nio.file.Paths.get(directory).resolveSibling("popup-upload-files"));
                            if(renderer[0]!=null)renderer[0].wire.close();
                        }catch(Exception|Error error){System.err.println("Publication cleanup "+variant+" prep="+product.preparationSettled()+" child="+product.popupDiagnostic("owned")+" close="+product.publicReplies.get("close")+" closed="+backend.closes.get()+" abort="+backend.responseAborts.get()+" pass="+backend.responsePasses.get()+" site="+backend.closeSite+" worker="+product.preparationWorkerCode());if(primary!=null)primary.addSuppressed(error);else throw error;}
                    }
                }
            }
        }finally{Handler.fail=false;android.os.SystemClock.now=Long.MIN_VALUE;}
    }
    private static void productDocumentIsolation(String mode,String directory)throws Exception {
        String variant=mode.substring("preparation-isolated-".length());Backend backend=new Backend();android.os.SystemClock.now=1000;
        boolean nativeLedger=variant.startsWith("native-"),grandchild=variant.startsWith("grandchild-");backend.holdNativeDescendants=nativeLedger||grandchild;
        boolean receiveRace=variant.startsWith("receive-");
        boolean control=variant.startsWith("control-");
        boolean flowRootFirst=control&&variant.endsWith("-root-first");String controlKind=flowRootFirst?variant.substring(0,variant.length()-"-root-first".length()):variant;
        boolean rebind=variant.equals("reset-attach")||variant.equals("reset-runtime");
        boolean nested=variant.equals("nested")||variant.equals("detach-nested")||rebind||nativeLedger||grandchild;
        boolean operation=variant.equals("read")||variant.equals("effect")||variant.equals("detach-nested")||rebind||receiveRace||nativeLedger||grandchild;
        boolean effect=variant.equals("effect")||variant.equals("grandchild-effect"),peerDetach=variant.equals("detach")||variant.equals("detach-nested");
        boolean broken=variant.equals("foreign")||variant.equals("expired")||variant.equals("receive-foreign")||variant.startsWith("native-wrong-")||variant.equals("native-limit")||variant.equals("native-retired-parent-attach");
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context original=host.source();host.ready(original);try(NativeSource source=new NativeSource(host,original)){
                java.nio.file.Path root=java.nio.file.Paths.get(directory);java.nio.file.Files.createDirectories(root);
                try(MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->new MdoBrowserDownloadFiles(root),host.time::get)){
                    PopupProductHost product=new PopupProductHost(source.views);product.bindPreparation(source.view,original.adapter,source.core,host.worker);
                    product.downloadQueue=queue;product.downloadBudget=new MdoBrowserCdpDownloads.Budget();
                    String[] keys={"owned","second"},sessions={"proof-real","proof-second"};WebView[] exact=new WebView[2];
                    Backend.DocumentRenderer[] renderers=new Backend.DocumentRenderer[2];AutoCloseable[] handles=new AutoCloseable[2];
                    boolean[] detachedSent=new boolean[2];AtomicInteger[] ready={new AtomicInteger(),new AtomicInteger()},failed={new AtomicInteger(),new AtomicInteger()};
                    AtomicInteger prepared=new AtomicInteger(),networked=new AtomicInteger();backend.proofTargets.add("target-proof-second");
                    backend.managedResumeMode="normal";backend.responseMode="source-pass";backend.managedResumeChildHook=backend.sourceRenderers::remove;
                    source.beforeSend=child->{
                        int index=source.sends;check(index<2&&host.ui.onThread()&&child==source.views.get(keys[index]));exact[index]=child;source.adopting=child;product.loading.put(keys[index],false);
                        backend.nativeTargets.put("target-"+sessions[index],proofPeer(child,"success",null,null));backend.sourceRenderers.put(sessions[index],new Backend.SourceRenderer("source-complete",sessions[index]));
                        try{renderers[index]=new Backend.DocumentRenderer(child,sessions[index]);renderers[index].frames.omitChild=index!=0||!nested;renderers[index].frames.includeLocal=index==0&&nested;}
                        catch(Exception error){throw new AssertionError(error);}
                        backend.documentRenderers.put(sessions[index],renderers[index]);
                    };
                    MdoBrowserCdpPopupPreparation.Failure networkFailure=(code,possible)->{throw new AssertionError("Original network startup failed: "+code);};
                    product.prepare(prepared::incrementAndGet,networkFailure);waitFor(()->prepared.get()==1,host,true);
                    java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> firstResult=new java.util.concurrent.atomic.AtomicReference<>();
                    java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> secondResult=new java.util.concurrent.atomic.AtomicReference<>();
                    AtomicInteger grandchildCallbacks=new AtomicInteger();
                    MdoBrowserCdpPopupWorker.Request request=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class);Throwable primary=null;
                    try{
                        for(int i=0;i<2;i++){
                            final int index=i;source.open();handles[i]=product.handoffNetworkPrepared(source.core.snapshot().id,keys[i],9000,networked::incrementAndGet,networkFailure);
                            backend.wire.add(attached(sessions[i],"owned"));waitFor(()->source.sends==index+1,host,true);source.ack();waitFor(()->networked.get()==index+1,host,true);
                        }
                        check(backend.opens.get()==1&&backend.guards.get()==2&&backend.nativeOpens.get()==2);
                        if(control)backend.holdControlMethod=controlKind.equals("control-resume")?"Runtime.runIfWaitingForDebugger":controlKind.equals("control-request")?"Fetch.continueRequest":"response:Fetch.continueRequest";
                        else if(!operation)renderers[0].holdMethod="Page.createIsolatedWorld";
                        for(int i=0;i<2;i++){final int index=i;product.beginPopupPage(keys[i],ready[i]::incrementAndGet,(code,possible)->{check(host.ui.onThread()&&possible);failed[index].incrementAndGet();});}
                        if(operation){
                            waitFor(()->{for(String key:keys)product.pollPopupPage(key);return ready[0].get()==1&&ready[1].get()==1;},host,true);
                            if(rebind){renderers[0].holdMethod=variant.equals("reset-attach")?"Target.setAutoAttach":"Runtime.disable";product.rebindPopupPage(keys[0],value->{check(host.ui.onThread());firstResult.set(value);});}
                            else if(grandchild){renderers[0].holdMethod="Runtime.evaluate";renderers[0].holdExpression="({ok:true,text:'managed'})";product.runPopupLeaf(keys[0],effect,value->{check(host.ui.onThread());grandchildCallbacks.incrementAndGet();firstResult.set(value);});}
                            else{renderers[0].holdMethod="Runtime.evaluate";renderers[0].holdExpression="({ok:true,text:'managed'})";product.runPopupPage(keys[0],effect,nested,value->{check(host.ui.onThread());firstResult.set(value);});}
                        }
                        waitFor(()->{for(String key:keys)product.pollPopupPage(key);return control?backend.heldControl!=null:renderers[0].held!=null;},host,true);
                        MdoBrowserCdpPopupBroker.Handoff firstHandoff=product.popupHandoff(keys[0]);
                        MdoBrowserCdpPopupBroker.Candidate originalFlowCandidate=null;
                        if(control&&!controlKind.equals("control-resume")){
                            java.lang.reflect.Field candidates=MdoBrowserCdpPopupBroker.class.getDeclaredField("candidates");candidates.setAccessible(true);
                            java.lang.reflect.Field attachment=MdoBrowserCdpPopupBroker.Candidate.class.getDeclaredField("original");attachment.setAccessible(true);
                            for(Object value:(java.util.List<?>)candidates.get(backend.originalBroker)){
                                MdoBrowserCdpPopupBroker.Candidate item=(MdoBrowserCdpPopupBroker.Candidate)value;
                                if(((MdoBrowserCdpSession.AttachedTarget)attachment.get(item)).target.equals("target-"+sessions[0])){check(originalFlowCandidate==null);originalFlowCandidate=item;}
                            }check(originalFlowCandidate!=null);
                        }
                        final MdoBrowserCdpPopupBroker.Candidate flowCandidate=originalFlowCandidate;
                        int startupOutcomeBeforeClose=ready[0].get();
                        check(backend.closes.get()==0&&backend.resumes.get()>=1&&(control?backend.heldControl!=null:renderers[0].held!=null));
                        if(grandchild){
                            check(renderers[0].held.getString("sessionId").equals("first-leaf-session"));
                            renderers[0].detachNative("first-child-session",backend.wire);
                            try{waitFor(()->firstResult.get()!=null,host,true);}catch(AssertionError error){throw new AssertionError("Native ancestor detach left the original grandchild RPC waiting for its held reply",error);}
                            MdoBrowserCdpWorker.Result originalResult=firstResult.get();
                            check(originalResult.code.equals("cdp_session_detached")&&originalResult.bytes==null&&originalResult.effectMayHaveOccurred==effect&&grandchildCallbacks.get()==1);
                            check(firstHandoff.current()&&!firstHandoff.retiring()&&backend.closes.get()==0&&renderers[0].nativeSessions.size()==1&&renderers[0].nativeSessions.containsKey("first-leaf-session"));
                            renderers[1].holdMethod="Runtime.evaluate";renderers[1].holdExpression="({ok:true,text:'managed'})";
                            java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> peer=new java.util.concurrent.atomic.AtomicReference<>();
                            product.runPopupPage(keys[1],false,false,value->{check(host.ui.onThread()&&value.code==null&&value.bytes!=null&&value.bytes[0]==1);peer.set(value);});
                            waitFor(()->renderers[1].held!=null,host,true);
                            renderers[0].release(backend.wire);renderers[1].release(backend.wire);waitFor(()->peer.get()!=null,host,true);
                            check(backend.closes.get()==0&&firstHandoff.current()&&!firstHandoff.detached()&&firstResult.get()==originalResult&&grandchildCallbacks.get()==1);
                        }
                        if(receiveRace){
                            CountDownLatch cancelled=new CountDownLatch(1);java.util.concurrent.atomic.AtomicBoolean once=new java.util.concurrent.atomic.AtomicBoolean();
                            backend.receivedHook=bytes->{
                                try{
                                    JSONObject packet=new JSONObject(new String(bytes,StandardCharsets.UTF_8));boolean match=variant.equals("receive-other")?"proof-second".equals(packet.optString("sessionId"))&&"Runtime.executionContextDestroyed".equals(packet.optString("method")):
                                        variant.equals("receive-detach")?"Target.detachedFromTarget".equals(packet.optString("method")):"first-unadmitted-document-session".equals(packet.optString("sessionId"));
                                    if(match&&once.compareAndSet(false,true)){
                                        if(!host.ui.post(()->{product.closePopupPage(keys[0]);cancelled.countDown();}))throw new AssertionError("Original UI cancellation unavailable");
                                        if(!cancelled.await(3,TimeUnit.SECONDS))throw new AssertionError("Original UI cancellation not delivered");
                                    }
                                }catch(Exception error){throw new AssertionError(error);}
                            };
                            if(variant.equals("receive-other"))renderers[1].loseWorld("frame-owned",backend.wire);
                            else if(variant.equals("receive-detach")){backend.wire.add(detached(sessions[0]));detachedSent[0]=true;}
                            else backend.wire.add(ack(100002,"first-unadmitted-document-session"));
                            waitFor(()->cancelled.getCount()==0,host,true);backend.receivedHook=null;
                        }
                        else if(peerDetach){backend.wire.add(detached(sessions[0]));detachedSent[0]=true;}
                        else product.closePopupPage(keys[0]);
                        if(variant.equals("expired"))host.time.addAndGet(5001);
                        if(flowRootFirst){
                            int observed=backend.receives.get();backend.wire.add(detached(sessions[0]));detachedSent[0]=true;
                            waitFor(()->backend.receives.get()>observed+2,host,true);
                            if(firstHandoff.detached())throw new AssertionError("Native root end acknowledged an outstanding original Fetch command");
                            check(backend.heldControl!=null&&backend.closes.get()==0&&product.popupNetworkCount()==2&&!product.popupProtocolDrained(keys[0]));
                        }
                        if(control)backend.releaseControl();else if(!grandchild)renderers[0].release(backend.wire);
                        if(!broken){
                            waitFor(()->exact[0].destroyed==1||backend.closes.get()!=0,host,true);check(backend.closes.get()==0&&failed[1].get()==0);
                            if(flowCandidate!=null&&!flowRootFirst){
                                waitFor(()->flowCandidate.retainedRequestCount()==0&&flowCandidate.retainedResponseCount()==(controlKind.equals("control-request")?1:0),host,true);
                                check(!firstHandoff.detached()&&!detachedSent[0]&&backend.closes.get()==0);
                            }
                            if(!detachedSent[0]){backend.wire.add(detached(sessions[0]));detachedSent[0]=true;}
                        }
                        if(nested){
                            backend.wire.add(new JSONObject().put("method","Runtime.executionContextDestroyed").put("sessionId","first-child-session").put("params",new JSONObject().put("executionContextId",999)));
                            backend.wire.add(ack(100000,"first-leaf-session"));
                        }
                        if(grandchild){
                            final int received=backend.receives.get();waitFor(()->backend.receives.get()>received+2,host,true);
                            check(!firstHandoff.detached()&&backend.closes.get()==0&&renderers[0].nativeSessions.size()==1);
                            renderers[0].detachNative("first-leaf-session",backend.wire);
                        }
                        if(nativeLedger){
                            if(!detachedSent[0]){backend.wire.add(detached(sessions[0]));detachedSent[0]=true;}
                            final int received=backend.receives.get();waitFor(()->backend.receives.get()>received+2,host,true);
                            if(firstHandoff.detached())throw new AssertionError("Original native root released before its two descendant detach receipts");
                            check(backend.closes.get()==0&&renderers[0].nativeSessions.size()==2&&renderers[1].nativeSessions.isEmpty());
                            if(variant.equals("native-late-attach")){
                                renderers[0].nativeSessions.put("first-late-session",sessions[0]);
                                backend.wire.add(new JSONObject().put("method","Target.attachedToTarget").put("sessionId",sessions[0]).put("params",new JSONObject().put("sessionId","first-late-session").put("waitingForDebugger",false).put("targetInfo",new JSONObject().put("type","iframe").put("targetId","late-native-frame"))));
                            }
                            if(variant.startsWith("native-wrong-"))backend.wire.add(new JSONObject().put("method","Target.detachedFromTarget").put("sessionId",variant.equals("native-wrong-parent")?sessions[1]:sessions[0]).put("params",new JSONObject().put("sessionId","first-leaf-session")));
                            else if(variant.equals("native-retired-parent-attach")){
                                renderers[0].detachNative("first-child-session",backend.wire);
                                backend.wire.add(new JSONObject().put("method","Target.attachedToTarget").put("sessionId","first-child-session").put("params",new JSONObject().put("sessionId","retired-parent-extra").put("waitingForDebugger",false).put("targetInfo",new JSONObject().put("type","iframe").put("targetId","retired-parent-target"))));
                            }else if(variant.equals("native-limit")){
                                for(int i=0;i<63;i++)backend.wire.add(new JSONObject().put("method","Target.attachedToTarget").put("sessionId",sessions[0]).put("params",new JSONObject().put("sessionId","bounded-late-"+i).put("waitingForDebugger",false).put("targetInfo",new JSONObject().put("type","iframe").put("targetId","bounded-target-"+i))));
                            }
                            else{
                                String first=variant.equals("native-parent-first")?"first-child-session":"first-leaf-session";
                                if(variant.equals("native-root-first")){renderers[0].nativeSessions.remove(first);backend.wire.add(detached(first));}
                                else renderers[0].detachNative(first,backend.wire);
                                if(variant.equals("native-duplicate"))backend.wire.add(new JSONObject().put("method","Target.detachedFromTarget").put("sessionId","first-child-session").put("params",new JSONObject().put("sessionId",first)));
                                final int observed=backend.receives.get();waitFor(()->backend.receives.get()>observed+2,host,true);
                                check(!firstHandoff.detached()&&backend.closes.get()==0);
                                renderers[0].detachNativeChildren(backend.wire);
                            }
                        }
                        if(variant.equals("foreign"))backend.wire.add(ack(100001,"first-unadmitted-document-session"));
                        waitFor(()->{for(String key:keys){product.pollPopupNetwork(key);product.pollPopupPage(key);}return broken?backend.closes.get()!=0:ready[1].get()==1&&exact[0].destroyed==1;},host,true);
                        if(!broken){
                            if(backend.closes.get()!=0)throw new AssertionError("Original child retirement poisoned the other popup");
                            check(failed[0].get()==(operation||control&&startupOutcomeBeforeClose!=0?0:1)&&ready[0].get()+failed[0].get()==1&&failed[1].get()==0&&product.popupPageDocumentReady(keys[1])&&!product.popupPageDocumentReady(keys[0]));
                            if(!detachedSent[0]){backend.wire.add(detached(sessions[0]));detachedSent[0]=true;}
                            waitFor(()->{product.pollPopupNetwork(keys[0]);return firstHandoff.detached()&&product.popupNetworkCount()==1;},host,true);
                            check(backend.disables.get()==0&&backend.closes.get()==0&&backend.nativeOpens.get()==2&&MdoBrowserCdpPopupBroker.creationBlocked());
                            product.runPopupPage(keys[1],false,false,value->{check(host.ui.onThread());if(value.code==null)check(value.bytes!=null&&value.bytes.length==1&&value.bytes[0]==1);secondResult.set(value);});waitFor(()->secondResult.get()!=null,host,true);
                            check(secondResult.get().code==null&&secondResult.get().bytes!=null&&secondResult.get().bytes.length==1&&secondResult.get().bytes[0]==0&&!secondResult.get().effectMayHaveOccurred&&renderers[1].worlds.evaluations==(grandchild?2:1));
                            if(variant.equals("receive-other"))check(renderers[1].worlds.created==2);
                            if(operation){waitFor(()->firstResult.get()!=null,host,true);check(firstResult.get().code!=null&&firstResult.get().bytes==null&&firstResult.get().effectMayHaveOccurred==effect&&renderers[0].worlds.evaluations==(rebind?0:1));}
                            check(backend.opens.get()==1&&backend.resumes.get()==2&&backend.continues.get()==(controlKind.equals("control-resume")?1:2)&&backend.detaches.get()==0);
                            if(control)check(firstHandoff.flowFailure()!=null&&firstHandoff.flowFailure().effectMayHaveOccurred);
                        }else check(!product.popupPageReady(keys[1])&&!product.popupPageReady(keys[0])&&backend.disables.get()==0);
                        isolationEvidence=new JSONObject().put("shared_wire_preserved",backend.closes.get()==0).put("other_original_document_verified",secondResult.get()!=null&&secondResult.get().code==null).put("effects_preserved",!operation||firstResult.get()!=null&&firstResult.get().effectMayHaveOccurred==effect)
                            .put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get())
                            .put("late_nested_sessions_tested",nested).put("native_detach_confirmed",firstHandoff.detached()).put("provider_qualified",false).put("ui_published",false);
                        if(nativeLedger)isolationEvidence.put("native_descendant_ledger_tested",true).put("root_receipt_waited_for_descendants",true).put("wrong_parent_denied",variant.startsWith("native-wrong-")).put("native_limit_enforced",variant.equals("native-limit")).put("retired_parent_attach_denied",variant.equals("native-retired-parent-attach"));
                        if(grandchild)isolationEvidence.put("grandchild_rpc_retired",true).put("root_owner_preserved_until_close",true).put("late_reply_during_peer_rpc_discarded",true).put("original_effect_may_have_occurred",effect).put("original_callbacks",grandchildCallbacks.get()).put("leaf_native_receipt_required",true);
                        if(flowCandidate!=null)isolationEvidence.put("cancelled_flow_native_ack_drained",true).put("pause_receipt_before_native_detach",!flowRootFirst).put("startup_outcome_once",true);
                        if(flowRootFirst)isolationEvidence.put("native_root_waited_for_original_flow",true).put("original_fetch_ack_after_native_end",true);
                    }catch(Exception|Error error){primary=error;System.err.println("Document isolation "+variant+" wireClosed="+backend.closes.get()+" first="+failed[0].get()+" secondReady="+ready[1].get()+" secondFailed="+failed[1].get()+" event="+backend.eventFailure+" send="+backend.sendFailure+" close="+backend.closeSite);throw error;}
                    finally{
                        try{
                            backend.receivedHook=null;
                            if(backend.heldControl!=null)backend.releaseControl();
                            for(Backend.DocumentRenderer renderer:renderers)if(renderer!=null&&renderer.held!=null)renderer.release(backend.wire);
                            for(int i=0;i<2;i++){if(handles[i]!=null)handles[i].close();product.closePopupNetwork(keys[i]);}
                            product.retirePreparation("cdp_popup_preparation_cancelled");
                            waitFor(()->{
                                if(!broken)for(int i=0;i<2;i++)if(!detachedSent[i]&&exact[i]!=null&&exact[i].destroyed==1){try{backend.wire.add(detached(sessions[i]));detachedSent[i]=true;}catch(Exception error){throw new AssertionError(error);}}
                                product.resume();for(String key:keys)product.pollPopupNetwork(key);
                                return broken?request.result()!=null&&request.result().terminal&&MdoBrowserCdpPopupNative.promotedUiOwners()==0:product.preparationSettled()&&product.popupNetworkCount()==0;
                            },host,true);
                            for(WebView view:exact)if(view!=null)check(view.destroyed==1);
                            check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0&&product.downloadBudget.used()==0&&queue.retainedTickets()==0);
                            check(backend.threads.size()==1&&host.threads.containsAll(backend.threads));
                            if(!broken){waitFor(()->backend.closes.get()!=0&&!MdoBrowserCdpPopupBroker.creationBlocked(),host,true);check(backend.disables.get()==1&&backend.closes.get()==1);}
                            for(Backend.DocumentRenderer renderer:renderers)if(renderer!=null)renderer.wire.close();
                        }catch(Exception|Error error){if(primary!=null)primary.addSuppressed(error);else throw error;}
                    }
                }
            }
        }finally{android.os.SystemClock.now=Long.MIN_VALUE;}
    }
    private static boolean awaitingProtection(MdoBrowserCdpPopupWorker.Request original)throws Exception {
        java.lang.reflect.Field field=MdoBrowserCdpPopupWorker.Request.class.getDeclaredField("captured");field.setAccessible(true);
        synchronized(original){
            @SuppressWarnings("unchecked") List<MdoBrowserCdpPopupBroker.Candidate> captures=(List<MdoBrowserCdpPopupBroker.Candidate>)field.get(original);
            for(MdoBrowserCdpPopupBroker.Candidate candidate:captures)if(candidate.protectionRequested()&&candidate.protection()==null)return true;
            return false;
        }
    }
    private static void productNetworkConcurrent(String mode,String directory)throws Exception {
        Backend backend=new Backend();android.os.SystemClock.now=1000;
        boolean proofQueued=mode.endsWith("-proof"),detachBusy=mode.endsWith("-detach"),navigationClose=mode.endsWith("-navigation-close");
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context original=host.source();host.ready(original);try(NativeSource source=new NativeSource(host,original)){
                java.nio.file.Path root=java.nio.file.Paths.get(directory);java.nio.file.Files.createDirectories(root);
                try(MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->new MdoBrowserDownloadFiles(root),host.time::get)){
                    PopupProductHost product=new PopupProductHost(source.views);product.bindPreparation(source.view,original.adapter,source.core,host.worker);
                    product.downloadQueue=queue;product.downloadBudget=new MdoBrowserCdpDownloads.Budget();
                    AtomicInteger prepared=new AtomicInteger(),accepted=new AtomicInteger(),failed=new AtomicInteger();String[] reason={null};
                    WebView[] exact=new WebView[2];AutoCloseable[] handles=new AutoCloseable[2];boolean[] detached=new boolean[2];
                    String[] keys={"owned","second"},sessions={"proof-real","proof-second"};
                    Backend.SourceRenderer first=new Backend.SourceRenderer(navigationClose?"source-navigation-flow":"source-complete");first.holdFirst=true;
                    Backend.SourceRenderer second=new Backend.SourceRenderer(navigationClose?"source-navigation-flow":"source-complete","proof-second");second.holdFirst=detachBusy;
                    backend.sourceRenderers.put(sessions[0],first);backend.sourceRenderers.put(sessions[1],second);backend.proofTargets.add("target-proof-second");
                    MdoBrowserCdpPopupPreparation.Failure failure=(code,effect)->{check(host.ui.onThread());failed.incrementAndGet();reason[0]=code;};
                    source.beforeSend=child->{int index=source.sends;check(index<2&&host.ui.onThread()&&child==source.views.get(keys[index]));exact[index]=child;source.adopting=child;backend.nativeTargets.put("target-"+sessions[index],proofPeer(child,"success",null,null));};
                    product.prepare(prepared::incrementAndGet,failure);waitFor(()->prepared.get()==1,host,true);
                    MdoBrowserCdpPopupWorker.Request request=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class);Throwable primary=null;
                    try{
                        source.open();handles[0]=product.handoffNetworkPrepared(source.core.snapshot().id,keys[0],9000,accepted::incrementAndGet,failure);
                        backend.wire.add(attached(sessions[0],"owned"));waitFor(()->source.sends==1,host,true);
                        if(proofQueued){backend.wire.add(attached(sessions[1],"owned"));waitFor(()->backend.guards.get()==2,host,true);}
                        source.ack();
                        waitFor(()->first.held!=null,host,true);check(first.calls==1&&accepted.get()==0&&backend.guards.get()==(proofQueued?2:1));
                        source.open();handles[1]=product.handoffNetworkPrepared(source.core.snapshot().id,keys[1],9000,accepted::incrementAndGet,failure);
                        if(proofQueued){waitFor(()->source.sends==2,host,true);source.ack();}
                        else{
                            backend.wire.add(attached(sessions[1],"owned"));
                            waitFor(()->{try{return failed.get()!=0||awaitingProtection(request);}catch(Exception error){throw new AssertionError(error);}},host,true);
                        }
                        int before=backend.receives.get();waitFor(()->failed.get()!=0||backend.receives.get()>before+12,host,true);
                        if(failed.get()!=0)throw new AssertionError("Concurrent source bootstrap failed: "+reason[0]+" / "+product.preparationCode());
                        check(request.result().ready&&!request.result().terminal&&backend.guards.get()==(proofQueued?2:1)&&backend.nativeOpens.get()==1&&source.sends==(proofQueued?2:1)&&second.calls==0);
                        first.release(backend.wire);if(!proofQueued){waitFor(()->source.sends==2,host,true);source.ack();}
                        if(detachBusy){
                            waitFor(()->accepted.get()==1&&second.held!=null,host,true);
                            MdoBrowserCdpPopupBroker.Handoff held=product.popupHandoff(keys[0]);
                            product.retirePreparation("cdp_popup_preparation_cancelled");
                            product.closePopupNetwork(keys[0]);backend.wire.add(detached(sessions[0]));detached[0]=true;
                            waitFor(held::detached,host,true);check(failed.get()==0&&second.calls==1&&accepted.get()==1&&product.popupOwner(keys[1]).current());
                            product.pollPopupNetwork(keys[0]);check(product.popupNetworkCount()==1&&exact[0].destroyed==1&&backend.disables.get()==0);
                            second.release(backend.wire);
                        }
                        waitFor(()->accepted.get()==2||failed.get()!=0,host,true);
                        check(failed.get()==0&&product.popupNetworkCount()==(detachBusy?1:2)&&product.pendingHandoffs()==0&&first.calls==3&&second.calls==3);
                        check((detachBusy||product.popupNetworkReady(keys[0]))&&product.popupNetworkReady(keys[1])&&backend.guards.get()==2&&backend.nativeOpens.get()==2&&backend.barriers.get()==2);
                        check(backend.resumes.get()==0&&backend.continues.get()==0&&backend.bodyClaims.get()==0&&backend.detaches.get()==0);
                        if(navigationClose){
                            backend.managedResumeMode="normal";backend.responseMode="source-pass";
                            product.activate(product.popupOwner(keys[0]),product.popupHandoff(keys[0]));product.activate(product.popupOwner(keys[1]),product.popupHandoff(keys[1]));
                            waitFor(()->backend.resumes.get()==2&&product.popupHandoff(keys[0]).activationResult()!=null&&product.popupHandoff(keys[1]).activationResult()!=null,host,true);
                            check(product.popupHandoff(keys[0]).activationResult().ok&&product.popupHandoff(keys[1]).activationResult().ok);
                            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);java.lang.reflect.Field executor=MdoBrowserCdpWorker.class.getDeclaredField("executor");executor.setAccessible(true);
                            ((java.util.concurrent.ThreadPoolExecutor)executor.get(host.worker)).execute(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Concurrent navigation barrier expired");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
                            java.util.concurrent.atomic.AtomicBoolean invalidated=new java.util.concurrent.atomic.AtomicBoolean();MdoBrowserCdpNetworks.Navigation retiring;
                            try{
                                waitFor(()->entered.getCount()==0,host,true);retiring=product.navigatePopupSource(keys[0]);
                                java.lang.reflect.Field timer=MdoBrowserCdpNetworks.Navigation.class.getDeclaredField("attachedClock");timer.setAccessible(true);
                                timer.set(retiring,(LongSupplier)()->{if(invalidated.compareAndSet(false,true))product.invalidatePopupSource(keys[0]);return host.time.get();});
                            }finally{release.countDown();}
                            waitFor(()->invalidated.get()||retiring.result()!=null||!product.popupNetworkReady(keys[0])||backend.closes.get()!=0,host,true);
                            if(!invalidated.get()){
                                java.lang.reflect.Field problem=MdoBrowserCdpPopupBroker.class.getDeclaredField("failure");problem.setAccessible(true);
                                throw new AssertionError("Concurrent navigation was not serviced: receipt="+(retiring.result()==null?null:retiring.result().code)+", source="+product.popupNetworkCode(keys[0])+", wire_closed="+backend.closes.get()+", broker="+problem.get(backend.originalBroker));
                            }
                            waitFor(()->retiring.result()!=null,host,true);
                            check(!retiring.dispatched()&&first.navigations==0&&backend.closes.get()==0);
                            MdoBrowserCdpNetworks.Navigation survivor=product.navigatePopupSource(keys[1]);waitNavigation(survivor,true,host);
                            check(survivor.result().ok&&second.navigations==1&&backend.closes.get()==0&&product.popupOwner(keys[1]).current());
                            navigationEvidence=new JSONObject().put("original_source",true).put("scoped_close",true).put("other_original_navigation",true).put("first_navigation_commands",0).put("second_navigation_commands",1)
                                .put("browser_connections",backend.opens.get()).put("native_proof_connections",backend.nativeOpens.get()).put("provider_qualified",false);
                        }
                        product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);
                        if(!detached[0]){product.closePopupNetwork(keys[0]);backend.wire.add(detached(sessions[0]));detached[0]=true;}
                        waitFor(()->{product.pollPopupNetwork(keys[0]);return product.popupNetworkCount()==1;},host,true);
                        check(exact[0].destroyed==1&&product.popupNetworkReady(keys[1])&&product.popupOwner(keys[1]).current()&&backend.disables.get()==0&&MdoBrowserCdpPopupBroker.creationBlocked());
                    }catch(Exception|Error error){primary=error;throw error;}
                    finally{
                        try{
                            if(first.held!=null)first.release(backend.wire);
                            if(second.held!=null)second.release(backend.wire);
                            for(int i=0;i<2;i++){if(handles[i]!=null)handles[i].close();product.closePopupNetwork(keys[i]);}
                            product.retirePreparation("cdp_popup_preparation_cancelled");
                            waitFor(()->{
                                for(int i=0;i<2;i++)if(!detached[i]&&exact[i]!=null&&exact[i].destroyed>0){try{backend.wire.add(detached(sessions[i]));detached[i]=true;}catch(Exception error){throw new AssertionError(error);}}
                                product.resume();for(String key:keys)product.pollPopupNetwork(key);
                                return product.preparationSettled()&&product.popupNetworkCount()==0;
                            },host,true);
                            for(WebView view:exact)if(view!=null)check(view.destroyed==1);
                            check(product.pendingHandoffs()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0);
                            check(product.downloadBudget.used()==0&&queue.retainedTickets()==0&&backend.threads.size()==1&&host.threads.containsAll(backend.threads));
                        }catch(Exception|Error error){if(primary!=null)primary.addSuppressed(error);else throw error;}
                    }
                }
            }
        }finally{android.os.SystemClock.now=Long.MIN_VALUE;}
    }
    private static void productNetwork(String mode,String directory)throws Exception {
        String variant=mode.substring("preparation-network-".length());Backend backend=new Backend();android.os.SystemClock.now=1000;
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        boolean held=java.util.Arrays.asList("cancel-source","owner-channel","owner-profile","deadline-source").contains(variant);
        boolean uncertain=variant.equals("close-timeout")||variant.equals("download-unconfirmed");
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context original=host.source();host.ready(original);try(NativeSource source=new NativeSource(host,original)){
                java.nio.file.Path root=java.nio.file.Paths.get(directory);java.nio.file.Files.createDirectories(root);
                try(MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->new MdoBrowserDownloadFiles(root),host.time::get)){
                    PopupProductHost product=new PopupProductHost(source.views);product.bindPreparation(source.view,original.adapter,source.core,host.worker);
                    product.downloadQueue=queue;product.downloadBudget=new MdoBrowserCdpDownloads.Budget();
                    AtomicInteger prepared=new AtomicInteger(),accepted=new AtomicInteger(),failed=new AtomicInteger();String[] reason={null};boolean[] acknowledged={false},detached={false};WebView[] exact={null};AutoCloseable[] handle={null};
                    MdoBrowserCdpPopupPreparation.Failure failure=(code,effect)->{check(host.ui.onThread()&&effect);failed.incrementAndGet();reason[0]=code;};
                    source.beforeSend=child->{
                        check(host.ui.onThread()&&child==source.views.get("owned")&&product.documents.get("owned").profileAssigned());exact[0]=child;source.adopting=child;
                        backend.nativeTargets.put("target-proof-real",proofPeer(child,"success",null,null));
                        backend.sourceRenderer=new Backend.SourceRenderer(variant.equals("unsupported")?"source-unsupported":variant.equals("nonempty")?"source-nonempty":variant.equals("frame-race")?"source-changed":"source-complete");
                        if(held)backend.sourceRenderer.beforeFirst=()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Source fixture wait expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}};
                        if(variant.equals("peer-detach"))backend.sourceRenderer.beforeFirst=()->{try{backend.wire.add(detached("proof-real"));detached[0]=true;}catch(Exception error){throw new AssertionError(error);}};
                        if(variant.equals("inline"))source.sendHook=()->{source.ack();acknowledged[0]=true;};
                        if(variant.equals("deadline-source"))source.sendHook=()->{android.os.SystemClock.now+=400;};
                    };
                    if(variant.equals("schedule-failure"))product.downloadFactoryHook=()->{Handler.fail=true;};
                    product.prepare(prepared::incrementAndGet,failure);waitFor(()->prepared.get()==1,host,true);source.open();String pending=source.core.snapshot().id;
                    MdoBrowserCdpPopupWorker.Request request=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class);
                    Throwable primary=null;
                    try{
                        handle[0]=product.handoffNetworkPrepared(pending,"owned",variant.equals("deadline-source")?1200:9000,()->{
                            check(host.ui.onThread()&&product.popupNetworkReady("owned"));accepted.incrementAndGet();
                            if(variant.equals("callback-throw"))throw new IllegalStateException("controlled_network_delivery_failure");
                        },failure);
                        backend.wire.add(attached("proof-real","owned"));waitFor(()->source.sends==1,host,true);
                        if(!acknowledged[0]){source.ack();acknowledged[0]=true;}
                        if(held){
                            waitFor(()->entered.getCount()==0,host,true);check(accepted.get()==0&&product.popupNetworkCount()==1);
                            if(variant.equals("cancel-source"))product.closePopupNetwork("owned");
                            else if(variant.equals("owner-channel"))product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());
                            else if(variant.equals("owner-profile"))product.documents.remove("owned");
                            else android.os.SystemClock.now+=900;
                            product.pollPopupNetwork("owned");release.countDown();
                        }
                        boolean failing=held||java.util.Arrays.asList("unsupported","nonempty","frame-race","callback-throw","schedule-failure","peer-detach").contains(variant);
                        if(failing){waitFor(()->failed.get()==1,host,true);check(accepted.get()==(variant.equals("callback-throw")?1:0)&&product.pendingHandoffs()==0);}
                        else{
                            waitFor(()->accepted.get()==1,host,true);check(failed.get()==0&&product.pendingHandoffs()==0&&product.popupNetworkReady("owned")&&product.popupDownloads("owned").sourcesClean());
                            MdoBrowserCdpPopupNative.Promotion owner=product.popupOwner("owned");MdoBrowserCdpPopupBroker.Handoff handoff=product.popupHandoff("owned");
                            check(owner.current()&&handoff.current()&&backend.resumes.get()==0&&backend.continues.get()==0&&backend.bodyClaims.get()==0&&backend.responsePasses.get()==0&&backend.sourceRenderer.calls==3);
                            if(variant.equals("duplicate")){int calls=backend.sourceRenderer.calls;failure("cdp_popup_network_busy",()->product.duplicatePopupNetwork("owned"));check(backend.sourceRenderer.calls==calls&&product.popupNetworkReady("owned"));}
                            if(variant.equals("parent-close")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);check(product.popupNetworkReady("owned")&&owner.current());}
                            if(variant.startsWith("download")){
                                backend.responseMode="source-complete";backend.managedResumeMode="normal";product.activate(owner,handoff);
                                MdoBrowserCdpDownloads downloads=product.popupDownloads("owned");
                                waitFor(()->{try{return downloads.list().length==1;}catch(IOException error){throw new AssertionError(error);}},host,true);
                                String id=downloads.list()[0].id;check(!downloads.sourcesClean()&&backend.bodyClaims.get()==0);
                                if(variant.equals("download-stream")){
                                    downloads.accept(id,65536,3000);waitFor(()->{try{return downloads.get(id).terminal();}catch(IOException error){throw new AssertionError(error);}},host,true);
                                    check(downloads.get(id).verified&&downloads.get(id).received==3&&downloads.sourcesClean()&&backend.bodyCloses.get()==1&&backend.responseAborts.get()==1);
                                }else{
                                    backend.holdAbort=uncertain;downloads.cancel(id);waitFor(()->backend.responseAborts.get()==1,host,true);
                                    if(!uncertain){waitFor(downloads::sourcesClean,host,true);check(downloads.get(id).sourceCleanupConfirmed&&backend.bodyClaims.get()==0);}
                                }
                            }
                            product.closePopupNetwork("owned");waitFor(owner::ended,host,true);check(exact[0].destroyed==1&&!handoff.current()&&!product.popupNetworkReady("owned"));
                            if(!variant.equals("close-timeout")){
                                int observed=backend.receives.get();backend.wire.add(detached("proof-real"));detached[0]=true;
                                if(!uncertain)waitFor(handoff::detached,host,true);
                                else{waitFor(()->backend.receives.get()>observed+2,host,true);check(!handoff.detached()&&!product.popupProtocolDrained("owned")&&backend.heldAbortReply!=null);}
                            }
                            if(uncertain){android.os.SystemClock.now+=5000;host.time.addAndGet(5000);product.pollPopupNetwork("owned");check(product.popupNetworkExpired("owned")&&!product.popupProtocolDrained("owned")&&product.networkCleanupFailed);}
                            else{waitFor(()->{product.pollPopupNetwork("owned");return product.popupNetworkCount()==0;},host,true);check(!product.networkCleanupFailed);}
                        }
                        if(variant.equals("unsupported")&&!"cdp_method_unsupported".equals(reason[0]))throw new AssertionError("Original source rejection was replaced by "+reason[0]);
                        if(variant.equals("nonempty")&&!"cdp_popup_source_reply_invalid".equals(reason[0]))throw new AssertionError("Original source reply error was replaced by "+reason[0]);
                        check(source.sends==1&&product.configured==1&&product.popupLinks.isEmpty()&&backend.detaches.get()==0);
                    }catch(Exception|Error error){primary=error;System.err.println("Product network "+variant+" accepted="+accepted.get()+" failed="+failed.get()+" reason="+reason[0]);throw error;}
                    finally{
                        try{
                            release.countDown();Handler.fail=false;product.downloadFactoryHook=null;
                            if(handle[0]!=null)handle[0].close();product.closePopupNetwork("owned");product.retirePreparation("cdp_popup_preparation_cancelled");
                            waitFor(()->{
                                if(!acknowledged[0]&&source.consumed!=null){source.ack();acknowledged[0]=true;}
                                if(!uncertain&&!detached[0]&&acknowledged[0]&&exact[0]!=null&&exact[0].destroyed>0){try{backend.wire.add(detached("proof-real"));detached[0]=true;}catch(Exception error){throw new AssertionError(error);}}
                                product.resume();product.pollPopupNetwork("owned");
                                MdoBrowserCdpPopupWorker.Result result=request.result();
                                return uncertain?result!=null&&result.terminal:product.preparationSettled()&&product.popupNetworkCount()==0;
                            },host,true);
                            check(product.pendingHandoffs()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0&&exact[0].destroyed==1);
                            if(uncertain){check(product.popupNetworkCount()==1&&!product.popupProtocolDrained("owned"));if(variant.equals("download-unconfirmed"))check(!product.popupDownloads("owned").sourcesClean());}
                            check(product.downloadBudget.used()==0&&queue.retainedTickets()==0&&backend.threads.size()==1&&host.threads.containsAll(backend.threads));
                        }catch(Exception|Error error){
                            MdoBrowserCdpPopupWorker.Result result=request.result();
                            System.err.println("Network cleanup "+variant+" "+product.popupDiagnostic("owned")+" preparation="+(result==null?null:result.code)+" released="+(result==null?null:result.sourceReleased)+" detachedSent="+detached[0]);
                            if(primary!=null)primary.addSuppressed(error);else throw error;
                        }
                    }
                }
            }
        }finally{Handler.fail=false;android.os.SystemClock.now=Long.MIN_VALUE;}
    }
    private static void productCoordinator(String mode)throws Exception {
        String variant=mode.substring("preparation-coordinator-".length());Backend backend=new Backend();android.os.SystemClock.now=1000;
        CountDownLatch proofEntered=new CountDownLatch(1),proofRelease=new CountDownLatch(1);
        boolean held=java.util.Arrays.asList("cancel-proof","proof-owner","deadline-proof").contains(variant);
        try(CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case(backend)){
            CdpNetworkBindingProbe.Context network=host.source();host.ready(network);try(NativeSource source=new NativeSource(host,network)){
                PopupProductHost product=new PopupProductHost(source.views);product.bindPreparation(source.view,network.adapter,source.core,host.worker);
                AtomicInteger prepared=new AtomicInteger(),accepted=new AtomicInteger(),failures=new AtomicInteger();boolean[] possible={false},acknowledged={false},detached={false};String[] reason={null};WebView[] exact={null};
                MdoBrowserCdpPopupNative.Promotion[] promoted={null};MdoBrowserCdpPopupBroker.Handoff[] handed={null};AutoCloseable[] handle={null};
                MdoBrowserCdpPopupPreparation.Failure failed=(code,effect)->{check(host.ui.onThread());failures.incrementAndGet();reason[0]=code;possible[0]=effect;};
                PopupProductHost.ProtocolPopupReady ready=(owner,handoff)->{
                    check(host.ui.onThread()&&owner.current()&&handoff.current()&&handoff.owns(owner));accepted.incrementAndGet();promoted[0]=owner;handed[0]=handoff;
                    if(variant.equals("parent-close"))product.retirePreparation("cdp_popup_preparation_cancelled");
                    if(variant.equals("callback-throw"))throw new IOException("controlled_coordinator_callback_failure");
                };
                source.beforeSend=child->{if(child==null)return;
                    check(host.ui.onThread()&&child==source.views.get("owned")&&product.configured==1&&product.documents.get("owned").profileAssigned());
                    exact[0]=child;source.adopting=child;
                    backend.nativeTargets.put("target-proof-real",proofPeer(child,variant.equals("proof-failure")?"missing":"success",held?proofEntered:null,held?proofRelease:null));
                    if(variant.equals("inline"))source.sendHook=()->{source.ack();acknowledged[0]=true;};
                    if(variant.startsWith("deadline-"))source.sendHook=()->{android.os.SystemClock.now+=500;};
                };
                product.prepare(prepared::incrementAndGet,failed);waitFor(()->prepared.get()==1,host,true);source.open();String pending=source.core.snapshot().id;Throwable primary=null;
                MdoBrowserCdpPopupWorker.Request originalRequest=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class);
                try{
                    if(variant.equals("arguments")||variant.equals("key-conflict")){
                        if(variant.equals("arguments")){
                            for(int timeout:new int[]{0,9001})failure("cdp_popup_handoff_arguments_invalid",()->product.handoffPrepared(pending,"owned",timeout,ready,failed));
                            failure("cdp_popup_handoff_arguments_invalid",()->product.handoffPrepared(pending,"owned",9000,null,failed));
                            failure("cdp_popup_native_request_changed",()->product.handoffPrepared("foreign","owned",9000,ready,failed));
                        }else{try{product.handoffPrepared(pending,"root",9000,ready,failed);throw new AssertionError("Root key was reused");}catch(RuntimeException expected){check(source.views.get("root")==source.view);}}
                        check(source.sends==0&&product.pendingHandoffs()==0&&failures.get()==0&&product.preparationReady());return;
                    }
                    if(variant.equals("profile-cancel")||variant.equals("configuration-failure"))PopupProductHost.MdoBrowserDocument.profileHook=()->{
                        exact[0]=source.views.get("owned");if(variant.equals("profile-cancel"))product.cancelHandoff("owned");};
                    product.failConfiguration=variant.equals("configuration-failure");int before=WebView.constructions;
                    int timeout=variant.equals("deadline-adoption")?600:variant.equals("deadline-proof")?1200:9000;
                    handle[0]=product.handoffPrepared(pending,"owned",timeout,ready,failed);check(product.pendingHandoffs()==1&&WebView.constructions==before);
                    if(variant.equals("busy")){
                        for(String key:new String[]{"owned","duplicate"})failure("cdp_popup_handoff_busy",()->product.handoffPrepared(pending,key,9000,ready,failed));
                        check(product.preparationReady()&&product.pendingHandoffs()==1&&failures.get()==0);
                    }
                    if(variant.equals("cancel-allocation")||variant.equals("allocation-owner")){
                        if(variant.equals("cancel-allocation"))handle[0].close();else{product.browserPaused=true;android.os.SystemClock.now+=100;}
                        waitFor(()->failures.get()==1,host,true);check(!possible[0]&&WebView.constructions==before&&accepted.get()==0&&product.pendingHandoffs()==0);return;
                    }
                    backend.wire.add(attached("proof-real","owned"));
                    if(variant.equals("profile-cancel")||variant.equals("configuration-failure")){
                        waitFor(()->failures.get()==1,host,true);check(possible[0]&&accepted.get()==0&&WebView.constructions==before+1&&product.pendingHandoffs()==0);return;
                    }
                    waitFor(()->source.sends==1,host,true);check(WebView.constructions==before+1&&product.popupLinks.isEmpty());
                    if(variant.equals("cancel-adoption"))handle[0].close();
                    if(variant.equals("adoption-owner")){product.channel.attach(product.host.getUrl(),new PopupProductHost.WebMessagePort());android.os.SystemClock.now+=100;}
                    if(variant.equals("cancel-adoption")||variant.equals("adoption-owner")){
                        waitFor(()->failures.get()==1,host,true);check(possible[0]&&exact[0].destroyed==0&&accepted.get()==0);
                    }
                    if(variant.equals("deadline-adoption"))android.os.SystemClock.now+=200;
                    if(variant.equals("deadline-proof"))android.os.SystemClock.now+=400;
                    if(!acknowledged[0]){source.ack();acknowledged[0]=true;}
                    if(held){
                        waitFor(()->proofEntered.getCount()==0,host,false);check(accepted.get()==0);
                        if(variant.equals("cancel-proof"))handle[0].close();
                        else if(variant.equals("proof-owner")){product.browserPaused=true;android.os.SystemClock.now+=100;}
                        else android.os.SystemClock.now+=400;
                        Handler.drain();proofRelease.countDown();
                    }
                    boolean failing=java.util.Arrays.asList("cancel-adoption","adoption-owner","cancel-proof","proof-owner","deadline-adoption","deadline-proof","proof-failure","callback-throw").contains(variant);
                    if(failing){waitFor(()->failures.get()==1,host,true);check(possible[0]&&accepted.get()==(variant.equals("callback-throw")?1:0)&&product.pendingHandoffs()==0);}
                    else{waitFor(()->accepted.get()==1,host,true);check(failures.get()==0&&product.pendingHandoffs()==0&&promoted[0].current());}
                    if(promoted[0]!=null){
                        if(variant.startsWith("close-")){
                            if(variant.equals("close-no-parent")){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);}
                            try{product.checkCreation();throw new AssertionError("Armed process allowed unrelated creation");}
                            catch(IllegalStateException expected){check(expected.getMessage().equals("native_popup_protection_busy"));}
                            handed[0].close();waitFor(promoted[0]::ended,host,true);check(!handed[0].current()&&!promoted[0].current()&&exact[0].destroyed==1);
                            if(variant.equals("close-timeout")||variant.equals("close-clock")){
                                host.time.addAndGet(3000);handed[0].close();host.time.addAndGet(variant.equals("close-timeout")?2000:-3001);
                                waitFor(()->backend.closes.get()==1,host,true);
                                check(backend.disables.get()==0&&MdoBrowserCdpPopupBroker.creationBlocked());
                            }else{
                                int received=backend.receives.get();backend.wire.add(handoffPaused("closing-request",false));backend.wire.add(detached("foreign"));
                                waitFor(()->backend.receives.get()>received+3,host,true);check(backend.closes.get()==0&&backend.resumes.get()==0&&backend.continues.get()==0);
                                backend.wire.add(detached("proof-real"));detached[0]=true;
                                if(variant.equals("close-no-parent")){waitFor(()->backend.disables.get()==1&&!MdoBrowserCdpPopupBroker.creationBlocked(),host,true);product.checkCreation();check(!MdoBrowserCdpPopupBroker.creationBlocked());}
                                else{int after=backend.receives.get();waitFor(()->backend.receives.get()>after+2,host,true);check(product.preparationReady()&&failures.get()==0&&backend.closes.get()==0);}
                            }
                        }else if(!failing){product.retirePreparation("cdp_popup_preparation_cancelled");waitFor(product::preparationSettled,host,true);check(promoted[0].current());handed[0].close();}
                        waitFor(promoted[0]::ended,host,true);
                    }
                    handle[0].close();check(accepted.get()==(variant.equals("callback-throw")||!failing?1:0)&&failures.get()==(failing?1:0));
                    check(source.sends==1&&backend.resumes.get()==0&&backend.continues.get()==0&&backend.detaches.get()==0);
                }catch(Exception|Error error){primary=error;System.err.println("Coordinator "+variant+" accepted="+accepted.get()+" failed="+failures.get()+" reason="+reason[0]);throw error;}
                finally{
                    try{
                        proofRelease.countDown();PopupProductHost.MdoBrowserDocument.profileHook=null;product.failConfiguration=false;
                        if(handle[0]!=null)handle[0].close();if(handed[0]!=null)handed[0].close();product.retirePreparation("cdp_popup_preparation_cancelled");
                        boolean uncertain=variant.equals("close-timeout")||variant.equals("close-clock");
                        MdoBrowserCdpPopupWorker.Request finishing=originalRequest;
                        waitFor(()->{
                            if(!acknowledged[0]&&source.consumed!=null){source.ack();acknowledged[0]=true;}
                            if(!uncertain&&!detached[0]&&acknowledged[0]&&exact[0]!=null&&exact[0].destroyed>0){try{backend.wire.add(detached("proof-real"));detached[0]=true;}catch(Exception error){throw new AssertionError(error);}}
                            if(uncertain){MdoBrowserCdpPopupWorker.Result result=finishing.result();return result!=null&&result.terminal;}
                            return product.preparationSettled();
                        },host,true);
                        if(uncertain){check(!finishing.result().sourceReleased&&finishing.result().code.equals("cdp_popup_handoff_cleanup_unconfirmed")&&!product.preparationSettled());}
                        check(product.pendingHandoffs()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.promotedUiOwners()==0);
                        if(exact[0]!=null)check(exact[0].destroyed==1);check(backend.threads.size()==1&&host.threads.containsAll(backend.threads));
                    }catch(Exception|Error error){
                        MdoBrowserCdpPopupWorker.Result result=product.preparationOriginal("request",MdoBrowserCdpPopupWorker.Request.class).result();
                        System.err.println("Coordinator cleanup "+variant+" reason="+product.preparationCode()+" destroyed="+(exact[0]==null?null:exact[0].destroyed)+" retained="+MdoBrowserCdpPopupNative.retainedUiOwners()+" promoted="+MdoBrowserCdpPopupNative.promotedUiOwners()+" result="+(result==null?null:result.code)+" released="+(result==null?null:result.sourceReleased)+" detached="+detached[0]);
                        if(primary!=null)primary.addSuppressed(error);else throw error;
                    }
                }
            }
        }finally{proofRelease.countDown();PopupProductHost.MdoBrowserDocument.profileHook=null;Handler.fail=false;android.os.SystemClock.now=Long.MIN_VALUE;}
    }
    public static void main(String[] args)throws Exception{
        Looper.getMainLooper();System.loadLibrary("xs");String mode=args.length==0?"suite":args[0];
        if(mode.equals("input-clock")){inputSurfaceClock();return;}
        if(mode.startsWith("prepared-admission"))preparedAdmission(mode);
        else if(mode.startsWith("preparation-isolated-"))productDocumentIsolation(mode,args[1]);
        else if(mode.startsWith("preparation-page-"))productPage(mode,args[1]);
        else if(mode.startsWith("preparation-publication-")||mode.startsWith("preparation-restore-"))productPublication(mode,args[1]);
        else if(mode.startsWith("preparation-child-"))productChildDelivery(mode);
        else if(mode.startsWith("preparation-adoption-"))productAdoption(mode);
        else if(mode.startsWith("preparation-network-concurrent-"))productNetworkConcurrent(mode,args[1]);
        else if(mode.startsWith("preparation-network-"))productNetwork(mode,args[1]);
        else if(mode.startsWith("preparation-coordinator-"))productCoordinator(mode);
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
        JSONObject report=new JSONObject("{\"passed\":true,\"checks\":"+checks+",\"mode\":\""+mode+"\",\"one_original_executor\":true,\"ui_delivery\":true,\"cooperative_popup_pump\":true,\"native_ui_promotion_tested\":"+mode.startsWith("promotion")+",\"protected_network_source_tested\":"+mode.startsWith("source")+",\"attached_download_controller_tested\":"+mode.startsWith("transfer")+",\"protected_response_routing_tested\":"+mode.startsWith("response")+",\"protected_document_binding_tested\":"+mode.startsWith("document")+",\"protected_attachment_activation_tested\":"+mode.startsWith("activation")+",\"protected_attachment_handoff_tested\":"+mode.startsWith("handoff")+",\"protocol_promotion_qualified\":false,\"transport_quiescence_tested\":"+mode.startsWith("quiescence")+",\"retirement_delivery_scope_tested\":"+mode.startsWith("delivery-loss")+",\"product_prepared_admission_tested\":"+mode.startsWith("prepared-admission")+",\"product_popup_preparation_tested\":"+mode.startsWith("preparation")+",\"product_disposer_join_tested\":"+mode.startsWith("product-disposal")+",\"protective_interception_scope_tested\":"+mode.startsWith("protection")+",\"retained_target_marker_scope_tested\":"+mode.startsWith("native-proof")+",\"native_popup_adoption_qualified\":false,\"provider_qualified\":false}");
        report.put("product_page_startup_tested",mode.startsWith("preparation-page-"));
        if(pageEvidence!=null)report.put("product_page",pageEvidence);
        if(loadingRetirementEvidence!=null)report.put("loading_retirement",loadingRetirementEvidence);
        report.put("product_document_retirement_tested",mode.startsWith("preparation-isolated-"));
        if(isolationEvidence!=null)report.put("product_document_retirement",isolationEvidence);
        report.put("product_frame_route_tested",mode.startsWith("preparation-page-route-")||mode.startsWith("preparation-page-natural-"));
        report.put("product_capture_fence_tested",mode.startsWith("preparation-page-capture-"));
        report.put("product_protected_opener_tested",mode.startsWith("preparation-page-opener-"));
        report.put("product_protected_input_tested",mode.startsWith("preparation-page-input-"));
        report.put("product_nested_adoption_tested",mode.startsWith("preparation-publication-nested-"));
        report.put("product_document_state_tested",mode.startsWith("preparation-publication-state-"));
        report.put("product_frontend_recovery_tested",mode.startsWith("preparation-publication-frontend-"));
        report.put("product_upload_continuation_tested",mode.startsWith("preparation-publication-continuation-"));
        report.put("product_protected_file_flow_tested",mode.startsWith("preparation-publication-files-"));
        if(frameRouteEvidence!=null)report.put("product_frame_route",frameRouteEvidence);
        report.put("product_publication_tested",mode.startsWith("preparation-publication-")||mode.startsWith("preparation-restore-"));
        report.put("product_document_restore_tested",mode.startsWith("preparation-restore-"));
        if(publicationEvidence!=null)report.put("product_publication",publicationEvidence);
        if(navigationEvidence!=null)report.put("source_navigation",navigationEvidence);
        if(hostCloseEvidence!=null)report.put("host_close",hostCloseEvidence);
        System.out.println(report);
    }
}
