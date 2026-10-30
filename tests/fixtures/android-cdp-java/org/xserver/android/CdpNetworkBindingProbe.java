package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual target proof and dispatcher, controlled UI/metadata peers. Physical
 * scope is distinct from document execution; no installed provider is proven. */
public final class CdpNetworkBindingProbe {
    private static int checks;private static ExecutorService worker;
    private interface Operation {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Network binding check "+(checks+1));checks++;}
    private static IOException rejected(String code,Operation work)throws Exception{
        try{work.run();throw new AssertionError("Missing network binding failure: "+code);}
        catch(IOException error){check(code.equals(error.getMessage()));check(error.getCause()==null);return error;}
    }
    private static <T>T run(Callable<T> work)throws Exception{
        Future<T> future=worker.submit(work);long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!future.isDone()&&System.nanoTime()<until){Handler.drain();Thread.sleep(1);}
        try{return future.get(2,TimeUnit.SECONDS);}
        catch(ExecutionException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw error;}
        finally{Handler.drain();}
    }
    private static JSONObject response(int id)throws Exception{return new JSONObject().put("id",id).put("result",new JSONObject());}
    private static JSONObject paused(String id)throws Exception{return new JSONObject().put("method","Fetch.requestPaused").put("params",new JSONObject().put("requestId",id).put("frameId","frame-owned").put("responseStatusCode",200)
        .put("resourceType","Document").put("request",new JSONObject().put("method","POST").put("url","https://private.invalid/token"))
        .put("responseHeaders",new JSONArray().put(new JSONObject().put("name","Content-Disposition").put("value","attachment; filename=private.bin"))));}
    static final class Context implements AutoCloseable{
        final WebView view;final MdoBrowserCdpView adapter;
        final CdpBindingProbe.Factory peer;
        final List<String> methods=new ArrayList<>();final List<JSONObject> commands=new ArrayList<>();
        final List<MdoBrowserCdpSession.ControlReceipt> controls=new ArrayList<>();
        final List<MdoBrowserCdpNetworkFrames.Origin> origins=new ArrayList<>();
        MdoBrowserCdpBinding.Network network;boolean rejectDisable,rejectEnable,breakEnable,pausedEnable,abort,rejectPage,brokenTree,treeRaceApplied,expectSubframe;int treeRaces;
        Context(boolean physical){this(physical,new WebView(),new MdoBrowserCdpMarker.DocumentQuota());}
        Context(boolean physical,WebView original,MdoBrowserCdpMarker.DocumentQuota quota){view=original;peer=new CdpBindingProbe.Factory(view);
            adapter=physical?MdoBrowserCdpView.network(view,v->v==view,quota):new MdoBrowserCdpView(view,v->v==view);
            peer.extra=(command,wire)->{
                String method=command.getString("method");methods.add(method);commands.add(command);int id=command.getInt("id");
                if("Page.enable".equals(method)){
                    check(wire.proofRead&&wire.cleanupAttempts==1&&wire.id.equals("owned")&&command.getJSONObject("params").length()==0);
                    wire.queue(rejectPage?new JSONObject().put("id",id).put("error",new JSONObject().put("code",-32601).put("message","private-page-error")):response(id));return true;
                }
                if("Page.getFrameTree".equals(method)&&wire.frameQueries>=2&&brokenTree){wire.queue(new JSONObject().put("id",id).put("result",new JSONObject().put("frameTree",new JSONObject().put("frame",new JSONObject().put("id","unproved-root").put("loaderId","load-owned")))));return true;}
                if("Page.getFrameTree".equals(method)&&wire.frameQueries>=2&&(treeRaces>0||treeRaceApplied)){
                    boolean changed=treeRaces>0;
                    if(changed){treeRaces--;treeRaceApplied=true;wire.queue(new JSONObject().put("method","Page.frameAttached").put("params",new JSONObject().put("frameId","race-child").put("parentFrameId","frame-owned")));}
                    JSONObject tree=new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","load-owned"));
                    if(!changed)tree.put("childFrames",new JSONArray().put(new JSONObject().put("frame",new JSONObject().put("id","race-child").put("parentId","frame-owned").put("loaderId","child-load"))));
                    wire.queue(new JSONObject().put("id",id).put("result",new JSONObject().put("frameTree",tree)));return true;
                }
                if("Fetch.disable".equals(method)){check(command.getJSONObject("params").length()==0);wire.queue(response(id));return true;}
                if("Runtime.disable".equals(method)||"Fetch.enable".equals(method)){
                    check(wire.proofRead&&wire.cleanupAttempts==1&&wire.id.equals("owned"));
                    if("Fetch.enable".equals(method)){
                        JSONObject params=command.getJSONObject("params");JSONArray patterns=params.getJSONArray("patterns");JSONObject p=patterns.getJSONObject(0);
                        check(params.length()==2&&!params.getBoolean("handleAuthRequests"));check(patterns.length()==1&&p.length()==2);
                        check(p.getString("urlPattern").equals("*")&&p.getString("requestStage").equals("Response"));
                        if(breakEnable)throw new IOException("private-partial-write");
                        if(pausedEnable)wire.queue(paused("interception-job-1.0"));
                    }else check(command.getJSONObject("params").length()==0);
                    if((method.equals("Runtime.disable")&&rejectDisable)||(method.equals("Fetch.enable")&&rejectEnable))
                        wire.queue(new JSONObject().put("id",id).put("error",new JSONObject().put("code",-32601).put("message","private-provider-data")));
                    else wire.queue(response(id));return true;
                }
                if(method.startsWith("Fetch.")){
                    check(method.equals(abort?"Fetch.failRequest":"Fetch.continueRequest"));
                    JSONObject p=command.getJSONObject("params");check(p.getString("requestId").startsWith("interception-job-"));
                    check(p.length()==(abort?2:1));if(abort)check(p.getString("errorReason").equals("Aborted"));
                    check(!command.has("sessionId"));wire.queue(response(id));return true;
                }
                return false;
            };
        }
        void bind()throws Exception{network=run(()->MdoBrowserCdpBinding.bindNetwork(adapter.marker(),1500,(m,s,p)->{
            if(m.equals("Fetch.requestPaused")){
                MdoBrowserCdpResponse original=network.pausedResponse();check(original.kind.equals("candidate")&&original.reason.equals("attachment")&&original.status==200);
                p.remove("responseHeaders");p.remove("request");try{p.put("frameId","unowned-forged-frame");}catch(org.json.JSONException error){throw new IOException(error);}
                check(network.pausedResponse()==original);MdoBrowserCdpNetworkFrames.Origin origin=network.pausedOrigin();origins.add(origin);check(origin.root!=expectSubframe&&origin.current());
                controls.add(abort?network.abortPausedRequest():network.continuePausedRequest());
            }
        },t->Arrays.asList("unowned","owned"),peer));}
        CdpBindingProbe.Wire wire(){return peer.wires.get(peer.wires.size()-1);}
        void enable()throws Exception{run(()->{network.enableResponses(500);return null;});}
        public void close(){adapter.close();if(network!=null)network.close();peer.balanced();check(peer.dispatchDefect==null);}
    }
    private static void scope()throws Exception{
        try(Context document=new Context(false)){
            run(()->{rejected("cdp_network_scope_invalid",()->MdoBrowserCdpBinding.bindNetwork(document.adapter.marker(),500,(m,s,p)->{},t->Arrays.asList("owned"),document.peer));return null;});
            check(document.peer.wires.isEmpty()&&document.view.evaluations==0);
        }
        try(Context physical=new Context(true)){
            run(()->{
                rejected("cdp_network_scope_invalid",()->MdoBrowserCdpBinding.bind(physical.adapter.marker(),500,(m,s,p)->{},t->Arrays.asList("owned"),physical.peer));
                rejected("cdp_network_scope_invalid",()->physical.adapter.marker().lease().worldName());return null;
            });check(physical.peer.wires.isEmpty()&&physical.view.evaluations==0);
            physical.bind();check(physical.view.evaluations==1&&physical.peer.wires.size()==2);
            check(physical.view.lexical.values().stream().allMatch(WebView.UNDEFINED::equals));
            check(physical.methods.stream().filter("Runtime.disable"::equals).count()==1&&physical.methods.stream().noneMatch("Fetch.enable"::equals));
            run(()->{rejected("cdp_network_not_enabled",()->physical.network.poll(100));rejected("cdp_network_not_enabled",()->physical.network.continuePausedRequest());
                rejected("cdp_arguments_invalid",()->physical.network.enableResponses(9));return null;});
            physical.enable();run(()->{rejected("cdp_network_enable_replayed",()->physical.network.enableResponses(100));return null;});
            physical.network.close();int evaluations=physical.view.evaluations;
            run(()->{rejected("cdp_network_rebind_forbidden",()->MdoBrowserCdpBinding.bindNetwork(physical.adapter.marker(),500,(m,s,p)->{},t->Arrays.asList("owned"),physical.peer));return null;});
            check(physical.view.evaluations==evaluations);rejected("cdp_wrong_worker",()->physical.network.poll(100));
        }
    }
    private static void navigation()throws Exception{
        try(Context c=new Context(true)){
            c.treeRaces=1;c.expectSubframe=true;c.bind();c.enable();
            JSONObject event=paused("interception-job-raced");event.getJSONObject("params").put("frameId","race-child");c.wire().queue(event);
            run(()->{check(c.network.poll(100));check(c.network.poll(100));return null;});check(c.origins.size()==1&&!c.origins.get(0).root&&c.controls.get(0).result().ok);
            check(c.methods.stream().filter("Page.getFrameTree"::equals).count()==5); // unowned, proof twice, raced and stable
        }
        for(boolean abort:new boolean[]{false,true})try(Context c=new Context(true)){
            c.abort=abort;c.pausedEnable=true;c.bind();c.enable();check(c.controls.size()==1&&c.controls.get(0).pending());
            run(()->{check(c.network.poll(100));check(c.network.pendingControls()==0);return null;});
            check(c.controls.get(0).result().ok);
            // Metadata is intentionally ignored after physical proof, including
            // recycled context IDs and more than one document's 256-name cap.
            for(int i=0;i<300;i++){
                c.wire().queue(new JSONObject().put("method","Runtime.executionContextDestroyed").put("params",new JSONObject().put("executionContextId",1)));
                c.wire().queue(new JSONObject().put("method","Runtime.executionContextsCleared").put("params",new JSONObject()));
                c.wire().queue(new JSONObject().put("method","Runtime.executionContextCreated").put("params",new JSONObject().put("context",new JSONObject().put("id",1))));
            }
            run(()->{for(int i=0;i<900;i++)check(c.network.poll(100));return null;});
            c.wire().queue(paused("interception-job-2.0"));run(()->{check(c.network.poll(100));check(c.network.poll(100));return null;});
            check(c.controls.size()==2&&c.controls.get(1).result().ok);
            check(c.view.evaluations==1&&c.methods.stream().filter("Runtime.evaluate"::equals).count()==4);
            int before=c.methods.size();c.adapter.close();for(MdoBrowserCdpNetworkFrames.Origin origin:c.origins)check(!origin.current());
            run(()->{rejected("cdp_binding_owner_changed",()->c.network.poll(100));return null;});check(c.methods.size()==before);
        }
        for(String event:new String[]{"Inspector.detached","Inspector.targetCrashed","Inspector.targetReloadedAfterCrash"})try(Context c=new Context(true)){
            c.bind();c.enable();c.wire().queue(new JSONObject().put("method",event).put("params",new JSONObject().put("reason","private-reason")));
            run(()->{rejected("cdp_owner_changed",()->c.network.poll(100));return null;});check(c.wire().closed.get());
        }
        try(Context c=new Context(true)){
            c.bind();c.enable();c.wire().queue(paused("interception-job-3.0").put("sessionId","foreign"));
            run(()->{rejected("cdp_session_mismatch",()->c.network.poll(100));return null;});check(c.controls.isEmpty()&&c.wire().closed.get());
        }
    }
    private static void failures()throws Exception{
        for(int mode=0;mode<3;mode++)try(Context c=new Context(true)){
            c.rejectDisable=mode==0;c.rejectEnable=mode==1;c.breakEnable=mode==2;
            if(mode==0){rejected("cdp_method_unsupported",c::bind);check(c.methods.stream().noneMatch("Fetch.enable"::equals));}
            else{
                c.bind();IOException error=rejected(mode==1?"cdp_method_unsupported":"cdp_transport_failed",c::enable);
                check(error instanceof MdoBrowserCdpSession.Failure&&((MdoBrowserCdpSession.Failure)error).effectMayHaveOccurred==(mode==2));
                run(()->{rejected("cdp_binding_owner_changed",()->c.network.enableResponses(100));return null;});
                check(c.methods.stream().filter("Fetch.enable"::equals).count()==1);
            }
            check(c.wire().closed.get()&&c.view.evaluations==1);
        }
        for(int mode=0;mode<4;mode++)try(Context c=new Context(true)){
            c.peer.ignoreUnique=mode==0;c.peer.wrongProof=mode==1;c.peer.changedLoader=mode==2;c.peer.loseContext=mode==3;
            rejected(mode==0?"cdp_binding_unique_context_unsupported":mode==1?"cdp_binding_proof_invalid":mode==2?"cdp_binding_context_changed":"cdp_binding_target_not_found",c::bind);
            check(c.methods.stream().noneMatch(method->method.equals("Runtime.disable")||method.startsWith("Fetch.")));
        }
    }
    private static void rootNavigationReceipt()throws Exception{
        try(Context c=new Context(true)){
            c.bind();run(()->{rejected("cdp_network_not_enabled",()->c.network.navigateRoot("about:blank",1000));return null;});c.enable();
            CdpBindingProbe.Factory.Extra previous=c.peer.extra;final int[] dispatched={0};
            c.peer.extra=(command,wire)->{
                if(command.getString("method").equals("Page.navigate")){
                    JSONObject params=command.getJSONObject("params");check(command.length()==3&&params.length()==2&&params.getString("url").equals("about:blank")&&params.getString("frameId").equals("frame-owned"));dispatched[0]++;
                    wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameId","frame-owned").put("isDownload",true)));return true;
                }return previous.send(command,wire);
            };
            rejected("cdp_wrong_worker",()->c.network.navigateRoot("about:blank",1000));
            MdoBrowserCdpSession.Navigation receipt=run(()->{MdoBrowserCdpSession.Navigation ticket=c.network.navigateRoot("about:blank",1000);check(ticket.pending());c.network.poll(100);return ticket;});
            check(receipt.result().acknowledged&&receipt.result().ok&&receipt.result().downloadKnown&&receipt.result().download&&dispatched[0]==1);
            run(()->{c.network.cancelTransfers();rejected("cdp_network_retiring",()->c.network.navigateRoot("about:blank",1000));return null;});
            check(dispatched[0]==1);
        }
    }
    public static void main(String[] ignored)throws Exception{
        Looper.getMainLooper();System.loadLibrary("xs");worker=Executors.newSingleThreadExecutor();
        try{scope();navigation();failures();rootNavigationReceipt();}
        finally{worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));Handler.drain();}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_jvm_jni_json\":true,\"physical_scope_separate\":true,\"document_navigation_tested\":true,\"native_ui_calls_doubled\":true,\"provider_qualified\":false,\"product_downloads_activated\":false}");
    }
}
