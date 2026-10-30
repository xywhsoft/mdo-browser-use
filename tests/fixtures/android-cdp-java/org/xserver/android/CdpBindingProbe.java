package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
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
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual adapter/marker/binder/dispatcher/strict JNI reader, with Android UI
 * and protocol metadata peers doubled. No installed provider is qualified. */
public final class CdpBindingProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static ExecutorService worker;
    private interface Operation {void run() throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Binding gate check "+(checks.get()+1));checks.incrementAndGet();}
    private static void rejected(String code,Operation operation) throws Exception {
        try {operation.run();throw new AssertionError("Missing binding failure: "+code);}
        catch(IOException failure){check(code.equals(failure.getMessage()));check(failure.getCause()==null);}
    }
    private static <T>T run(Callable<T> operation) throws Exception {
        Future<T> result=worker.submit(operation);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!result.isDone()&&System.nanoTime()<end){Handler.drain();Thread.sleep(1);}
        try{return result.get(2,TimeUnit.SECONDS);}finally{Handler.drain();}
    }
    static final class Factory implements MdoBrowserCdpBinding.Factory {
        interface Extra {boolean send(JSONObject request,Wire wire)throws Exception;default void closed(Wire wire){}}
        final WebView owned;final List<Wire> wires=new ArrayList<>();
        Extra extra;volatile AssertionError dispatchDefect;
        boolean ignoreUnique,wrongProof,cleanupFails,missingUnique,emptyFirst,loseContext,failOpen,changedLoader,unsupportedLexical;
        Factory(WebView owned){this.owned=owned;}
        public MdoBrowserCdpBinding.Peer open(String id,int timeout,java.util.function.BooleanSupplier current,MdoBrowserCdpSession.Events events) throws IOException {
            if(failOpen&&"broken".equals(id))throw new IOException("cdp_local_unavailable");
            Wire wire=new Wire(this,id);wires.add(wire);
            MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,current,events,()->System.nanoTime()/1000000L);
            return new MdoBrowserCdpBinding.Peer(){
                public JSONObject call(String method,JSONObject params,int wait)throws IOException{return session.call(method,params,null,wait,false);}
                public JSONObject call(String method,JSONObject params,String child,int wait,boolean effect)throws IOException{return session.call(method,params,child,wait,effect);}
                public void admitChild(String child)throws IOException{session.admitChild(child);}
                public void retireChild(String child)throws IOException{session.retireChild(child);}
                public boolean poll(int wait)throws IOException{return session.poll(wait);}
                public boolean poll(int receive,int wait)throws IOException{return session.poll(receive,wait);}
                public boolean idle()throws IOException{return session.idle();}
                public MdoBrowserCdpSession.ControlReceipt continuePausedRequest()throws IOException{return session.continuePausedRequest();}
                public MdoBrowserCdpSession.ControlReceipt abortPausedRequest()throws IOException{return session.abortPausedRequest();}
                public MdoBrowserCdpResponse pausedResponse()throws IOException{return session.pausedResponse();}
                public MdoBrowserCdpSession.Offer retainPausedResponse()throws IOException{return session.retainPausedResponse();}
                public int pendingOffers()throws IOException{return session.pendingOffers();}
                public void cancelTransfers()throws IOException{session.cancelTransfers();}
                public MdoBrowserCdpSession.Body takePausedBody(long limit,int timeout)throws IOException{return session.takePausedBody(limit,timeout);}
                public MdoBrowserCdpSession.BodyChunk readBody(MdoBrowserCdpSession.Body body,int timeout)throws IOException{return session.readBody(body,timeout);}
                public void cancelBody(MdoBrowserCdpSession.Body body,int timeout)throws IOException{session.cancelBody(body,timeout);}
                public int pendingBodies()throws IOException{return session.pendingBodies();}
                public void requestBodyRead(MdoBrowserCdpSession.Body body)throws IOException{session.requestBodyRead(body);}
                public MdoBrowserCdpSession.BodyChunk takeBodyChunk(MdoBrowserCdpSession.Body body)throws IOException{return session.takeBodyChunk(body);}
                public boolean serviceBodies(int timeout)throws IOException{return session.serviceBodies(timeout);}
                public int pendingControls()throws IOException{return session.pendingControls();}
                public MdoBrowserCdpSession.Navigation navigateRoot(String url,String frame,int timeout)throws IOException{return session.navigateRoot(url,frame,timeout);}
                public void close(){session.close();}
            };
        }
        void balanced(){for(Wire wire:wires){check(wire.closed.get());check(!wire.otherSawName);wire.cleared();}}
    }
    static final class Wire implements MdoBrowserCdpSession.Wire {
        final Factory factory;final String id;final AtomicBoolean closed=new AtomicBoolean();
        final java.util.concurrent.ConcurrentLinkedDeque<byte[]> replies=new java.util.concurrent.ConcurrentLinkedDeque<>();final List<byte[]> packets=new ArrayList<>(),sent=new ArrayList<>();
        boolean otherSawName,proofRead;int frameQueries,cleanupAttempts;
        Wire(Factory factory,String id){this.factory=factory;this.id=id;}
        void queue(JSONObject value){replies.add(value.toString().getBytes(StandardCharsets.UTF_8));}
        private JSONObject result(String type,Object value)throws Exception{
            JSONObject result=new JSONObject().put("type",type);if(value!=null)result.put("value",value);return new JSONObject().put("result",result);
        }
        public void send(byte[] data,int timeout)throws IOException {
            sent.add(data);
            try {
                JSONObject request=new JSONObject(new String(data,StandardCharsets.UTF_8));int sequence=request.getInt("id");
                if(factory.extra!=null&&factory.extra.send(request,this))return;
                String method=request.getString("method");JSONObject params=request.getJSONObject("params"),reply=new JSONObject();
                if("Runtime.enable".equals(method)) {
                    if(!("empty".equals(id)&&factory.emptyFirst)) {
                        JSONObject context=new JSONObject().put("id",1).put("auxData",new JSONObject().put("isDefault",true).put("frameId","frame-"+id));
                        if(!factory.missingUnique)context.put("uniqueId","-7."+("owned".equals(id)?42:43));
                        queue(new JSONObject().put("method","Runtime.executionContextCreated").put("params",new JSONObject().put("context",context)));
                    }
                } else if("Page.getFrameTree".equals(method)) {
                    frameQueries++;String loader="load-"+id+(factory.changedLoader&&frameQueries>1?"-changed":"");
                    reply.put("frameTree",new JSONObject().put("frame",new JSONObject().put("id","frame-"+id).put("loaderId",loader)));
                }
                else if("Runtime.globalLexicalScopeNames".equals(method)) {
                    check(params.length()==1&&params.getInt("executionContextId")==1);
                    if(factory.unsupportedLexical){queue(new JSONObject().put("id",sequence).put("error",new JSONObject().put("code",-32601).put("message","Method not found")));return;}
                    JSONArray names=new JSONArray();if("owned".equals(id))for(String name:factory.owned.lexical.keySet())names.put(name);
                    reply.put("names",names);
                    if(factory.loseContext)queue(new JSONObject().put("method","Runtime.executionContextDestroyed").put("params",new JSONObject().put("executionContextId",1)));
                } else if("Runtime.evaluate".equals(method)) {
                    String expression=params.getString("expression"),unique=params.getString("uniqueContextId");
                    check(params.getBoolean("returnByValue")&&params.getBoolean("silent"));
                    if(!"owned".equals(id)&&expression.contains("__mdo_cdp_"))otherSawName=true;
                    if(unique.isEmpty()&&!factory.ignoreUnique) {
                        queue(new JSONObject().put("id",sequence).put("error",new JSONObject().put("code",-32602).put("message","invalid uniqueContextId")));return;
                    }
                    if(!unique.isEmpty())check(unique.equals("-7.42"));
                    if("void 0".equals(expression))reply=result("undefined",null);
                    else if(expression.endsWith("=void 0")) {
                        cleanupAttempts++;
                        check(!params.getBoolean("throwOnSideEffect"));
                        if(factory.cleanupFails){queue(new JSONObject().put("id",sequence).put("error",new JSONObject().put("code",-32000).put("message","private-cleanup-detail")));return;}
                        factory.owned.lexical.put(expression.substring(0,expression.length()-7),WebView.UNDEFINED);reply=result("undefined",null);
                    } else {
                        proofRead=true;check(params.getBoolean("throwOnSideEffect")&&"owned".equals(id));
                        String value=factory.owned.lexical.get(expression);
                        reply=result("string",factory.wrongProof?"synthetic-wrong-value":value);
                    }
                } else throw new AssertionError("Unexpected protocol method; no Debugger/effect commands allowed");
                queue(new JSONObject().put("id",sequence).put("result",reply));
            }catch(IOException failure){throw failure;}catch(AssertionError defect){factory.dispatchDefect=defect;throw defect;}catch(Exception failure){throw new IOException("synthetic-peer-invalid");}
        }
        public byte[] receive(int timeout)throws IOException {
            if(closed.get())throw new IOException("cdp_session_closed");byte[] value=replies.pollFirst();if(value!=null)packets.add(value);
            else try{Thread.sleep(1);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("cdp_binding_cancelled");}return value;
        }
        public void close(){if(closed.compareAndSet(false,true)){
            try{if(factory.extra!=null)factory.extra.closed(this);}finally{while(!replies.isEmpty())Arrays.fill(replies.removeFirst(),(byte)0);}}}
        void cleared(){for(byte[] bytes:sent)zero(bytes);for(byte[] bytes:packets)zero(bytes);}
        private void zero(byte[] bytes){boolean clear=true;for(byte b:bytes)if(b!=0)clear=false;check(clear);}
    }
    private static void binder()throws Exception {
        WebView view=new WebView();AtomicBoolean owned=new AtomicBoolean(true);
        MdoBrowserCdpView adapter=new MdoBrowserCdpView(view,v->v==view&&owned.get());Factory factory=new Factory(view);factory.emptyFirst=true;factory.failOpen=true;
        MdoBrowserCdpBinding bound=run(()->MdoBrowserCdpBinding.bind(adapter.marker(),1500,(m,s,p)->{},t->Arrays.asList("broken","empty","unowned","owned"),factory));
        check(bound.rootFrame.equals("frame-owned")&&bound.uniqueContext.equals("-7.42")&&bound.contextId==1);
        check(view.evaluations==1&&view.lexical.size()==1&&view.lexical.values().stream().allMatch(WebView.UNDEFINED::equals));
        check(factory.wires.size()==3&&!factory.wires.get(0).proofRead&&!factory.wires.get(1).proofRead&&factory.wires.get(2).proofRead);
        run(()->{rejected("cdp_binding_busy",()->MdoBrowserCdpBinding.bind(adapter.marker(),500,(m,s,p)->{},t->Arrays.asList("owned"),factory));return null;});
        adapter.close();check(factory.wires.get(2).closed.get());bound.close();factory.balanced();
        for(int mode=0;mode<7;mode++) {
            WebView next=new WebView();MdoBrowserCdpView owner=new MdoBrowserCdpView(next,v->v==next);Factory peer=new Factory(next);
            peer.ignoreUnique=mode==0;peer.wrongProof=mode==1;peer.cleanupFails=mode==2;peer.missingUnique=mode==3;peer.loseContext=mode==4;peer.changedLoader=mode==5;peer.unsupportedLexical=mode==6;
            String code=mode==0?"cdp_binding_unique_context_unsupported":mode==1?"cdp_binding_proof_invalid":mode==2?"cdp_command_rejected":mode==3||mode==6?"cdp_binding_context_unsupported":mode==4?"cdp_binding_target_not_found":"cdp_binding_context_changed";
            run(()->{rejected(code,()->MdoBrowserCdpBinding.bind(owner.marker(),1500,(m,s,p)->{},t->Arrays.asList("owned"),peer));return null;});
            if(mode==0)check(!peer.wires.get(0).proofRead);
            boolean cleared=mode==1||mode==5;check(next.lexical.values().stream().allMatch(value->WebView.UNDEFINED.equals(value)==cleared));
            if(mode==2)check(peer.wires.get(0).cleanupAttempts==1);
            check(next.cleanup==0);owner.close();peer.balanced();
        }
        WebView changed=new WebView();AtomicBoolean current=new AtomicBoolean(true);MdoBrowserCdpView original=new MdoBrowserCdpView(changed,v->current.get());
        changed.beforeReply=()->current.set(false);Factory peer=new Factory(changed);
        run(()->{rejected("cdp_binding_owner_changed",()->MdoBrowserCdpBinding.bind(original.marker(),1500,(m,s,p)->{},t->Arrays.asList("owned"),peer));return null;});
        check(peer.wires.isEmpty());original.close();
        WebView sleeping=new WebView();MdoBrowserCdpView sleeper=new MdoBrowserCdpView(sleeping,v->true);Factory sleepPeer=new Factory(sleeping);
        android.os.SystemClock.now=1000;
        try{
            run(()->{rejected("cdp_binding_timeout",()->MdoBrowserCdpBinding.bind(sleeper.marker(),500,(m,s,p)->{},t->{
                android.os.SystemClock.now=2000;return Arrays.asList("owned");
            },sleepPeer));return null;});
            check(sleepPeer.wires.isEmpty()&&sleeping.evaluations==0);
        }finally{android.os.SystemClock.now=Long.MIN_VALUE;sleeper.close();sleepPeer.balanced();}
    }
    private static MdoBrowserCdpMarker marker(WebView view,MdoBrowserCdpMarker.Budget budget) {
        Handler handler=new Handler(Looper.getMainLooper());
        return new MdoBrowserCdpMarker(new MdoBrowserCdpMarker.Ui(){
            public boolean onThread(){return Looper.myLooper()==Looper.getMainLooper();}
            public boolean post(Runnable task){return handler.post(task);}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        },new MdoBrowserCdpMarker.View(){
            public boolean current(){return true;}
            public void evaluate(String text,MdoBrowserCdpMarker.Reply callback){view.evaluateJavascript(text,callback==null?null:callback::done);}
        },budget);
    }
    private static void callbacks()throws Exception {
        WebView view=new WebView();MdoBrowserCdpMarker.Budget budget=new MdoBrowserCdpMarker.Budget(1);MdoBrowserCdpMarker marker=marker(view,budget);
        MdoBrowserCdpMarker.Ticket cancelled=worker.submit(()->{MdoBrowserCdpMarker.Ticket t=marker.request();t.close();return t;}).get(2,TimeUnit.SECONDS);
        Handler.drain();check(!cancelled.confirmed()&&view.evaluations==0&&budget.used()==0);
        view.defer=true;
        MdoBrowserCdpMarker.Ticket pending=run(()->{MdoBrowserCdpMarker.Ticket t=marker.request();check(!t.await(40));marker.discard(t);return t;});
        check(budget.used()==1&&!pending.confirmed());
        run(()->{rejected("cdp_binding_callback_limit",marker::request);return null;});
        view.finish("null");check(budget.used()==0&&!pending.confirmed());marker.close();
        WebView uncertain=new WebView();uncertain.throwAfterRetain=true;MdoBrowserCdpMarker.Budget held=new MdoBrowserCdpMarker.Budget(1);MdoBrowserCdpMarker root=marker(uncertain,held);
        MdoBrowserCdpMarker.Ticket thrown=run(()->{MdoBrowserCdpMarker.Ticket t=root.request();check(!t.await(100));root.discard(t);return t;});
        check(held.used()==1&&!thrown.confirmed());uncertain.finish("null");check(held.used()==0&&!thrown.confirmed());root.close();
        WebView limited=new WebView();MdoBrowserCdpMarker.Budget callbacks=new MdoBrowserCdpMarker.Budget(1);MdoBrowserCdpMarker limitedRoot=marker(limited,callbacks);
        for(int i=0;i<16;i++)run(()->{MdoBrowserCdpMarker.Ticket t=limitedRoot.request();check(t.await(200));t.close();return null;});
        run(()->{MdoBrowserCdpMarker.Ticket t=limitedRoot.request();check(!t.await(200)&&"cdp_binding_document_limit".equals(t.failure()));t.close();return null;});
        check(limited.evaluations==16&&callbacks.used()==0);limitedRoot.close();
    }
    private static void discovery()throws Exception {
        byte[] bytes=("[{\"id\":\"owned\",\"type\":\"page\",\"url\":\"https://untrusted.invalid/\",\"webSocketDebuggerUrl\":\"ws://other.invalid/path\"},"+
                      "{\"id\":\"worker\",\"type\":\"service_worker\"}]").getBytes(StandardCharsets.UTF_8);
        check(MdoBrowserCdpBinding.parseTargets(bytes).equals(Arrays.asList("owned")));for(byte b:bytes)check(b==0);
        for(String text:new String[]{"{}","[{\"id\":\"a/b\",\"type\":\"page\"}]","[{\"id\":\"x\",\"type\":\"page\"},{\"id\":\"x\",\"type\":\"page\"}]"}) {
            byte[] bad=text.getBytes(StandardCharsets.UTF_8);rejected("cdp_binding_discovery_invalid",()->MdoBrowserCdpBinding.parseTargets(bad));
            for(byte b:bad)check(b==0);
        }
    }
    public static void main(String[] ignored)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");worker=Executors.newSingleThreadExecutor();
        try{run(()->{discovery();return null;});binder();callbacks();}
        finally{worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));Handler.drain();}
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_ui_calls_doubled\":true,\"protocol_peers_synthetic\":true,\"provider_qualified\":false}");
    }
}
