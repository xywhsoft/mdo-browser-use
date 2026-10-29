package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual owner/frame/world/worker/session/strict JNI. DOM behavior and Android
 * UI are doubled; native protocol argument-world checks are modeled explicitly. */
public final class CdpOwnerProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean value){if(!value)throw new AssertionError("Owner check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Checked {void run()throws Exception;}
    private static void rejects(String code,Checked operation)throws Exception {
        try{operation.run();throw new AssertionError("Missing owner rejection");}catch(IOException error){check(code.equals(error.getMessage()));}
    }
    private static JSONObject event(String method,String source,JSONObject params)throws Exception {
        JSONObject value=new JSONObject().put("method",method).put("params",params);if(source!=null)value.put("sessionId",source);return value;
    }
    private static final class ObjectRef {
        final String source,group,child;final CdpWorldsProbe.Context context;
        ObjectRef(String source,String group,String child,CdpWorldsProbe.Context context){this.source=source;this.group=group;this.child=child;this.context=context;}
    }
    static final class Server implements CdpBindingProbe.Factory.Extra {
        final CdpWorldsProbe.Engine worlds;final CdpFramesProbe.Case host;final Map<String,ObjectRef> objects=new HashMap<>();
        final Map<Integer,String> backends=new HashMap<>();String pendingChild;int queries,resolves,negative,proofs,helpers,releases,disconnects;
        boolean badBackend,badNode,wrongRealm,ignoreUnique,helperException,badValue,resolveNavigation,parentNavigation,releaseFailure,timeout;
        Server(CdpFramesProbe.Case host)throws Exception {this.host=host;worlds=new CdpWorldsProbe.Engine();}
        private String parent(String child){return child.equals("leaf-frame")?"child-local":child.equals("child-local")?"child-frame":"frame-owned";}
        private String parentSource(String child){return child.equals("leaf-frame")||child.equals("child-local")?"child-session":null;}
        private void result(CdpBindingProbe.Wire wire,JSONObject request,JSONObject reply)throws Exception {
            JSONObject value=new JSONObject().put("id",request.getInt("id")).put("result",reply);if(request.has("sessionId"))value.put("sessionId",request.getString("sessionId"));wire.queue(value);
        }
        private void error(CdpBindingProbe.Wire wire,JSONObject request,int code,String message)throws Exception {
            JSONObject value=new JSONObject().put("id",request.getInt("id")).put("error",new JSONObject().put("code",code).put("message",message));
            if(request.has("sessionId"))value.put("sessionId",request.getString("sessionId"));wire.queue(value);
        }
        public void closed(CdpBindingProbe.Wire wire){objects.clear();disconnects++;}
        public boolean send(JSONObject request,CdpBindingProbe.Wire wire)throws Exception {
            String method=request.getString("method"),source=request.has("sessionId")?request.getString("sessionId"):null;JSONObject params=request.getJSONObject("params");
            if(method.equals("DOM.getFrameOwner")){
                queries++;pendingChild=params.getString("frameId");check(!pendingChild.equals("frame-owned")&&Objects.equals(source,parentSource(pendingChild)));
                int backend=42+queries;backends.put(backend,pendingChild);result(wire,request,new JSONObject().put("backendNodeId",badBackend?-1:backend));return true;
            }
            if(method.equals("DOM.resolveNode")){
                resolves++;String child=backends.get(params.getInt("backendNodeId")),group=params.getString("objectGroup");
                check(child!=null&&Objects.equals(source,parentSource(child))&&group.matches("mdo-frame-owner-[a-f0-9-]{36}"));
                CdpWorldsProbe.Context context=null;for(CdpWorldsProbe.Context row:worlds.contexts.values())if(Objects.equals(source,row.source)&&row.id==params.getInt("executionContextId"))context=row;
                check(context!=null&&context.frame.equals(parent(child)));String object="owner-object-"+resolves;
                objects.put(object,new ObjectRef(source,group,child,wrongRealm?null:context));
                if(resolveNavigation)wire.queue(event("Page.frameNavigated","child-session",new JSONObject().put("frame",new JSONObject().put("id","child-frame").put("parentId","frame-owned").put("loaderId","changed-load"))));
                result(wire,request,new JSONObject().put("object",new JSONObject().put("type","object").put("subtype",badNode?"array":"node").put("objectId",object)));return true;
            }
            if(method.equals("Runtime.callFunctionOn")){
                check(!params.has("objectId")&&!params.has("executionContextId")&&!params.has("contextId"));
                check(params.getBoolean("returnByValue")&&params.getBoolean("silent")&&!params.getBoolean("awaitPromise"));
                JSONArray args=params.getJSONArray("arguments");ObjectRef object=objects.get(args.getJSONObject(0).getString("objectId"));
                check(object!=null&&Objects.equals(source,object.source)&&params.getString("objectGroup").equals(object.group));String unique=params.getString("uniqueContextId");
                String declaration=params.getString("functionDeclaration");
                if(unique.isEmpty()){
                    negative++;check(params.getBoolean("throwOnSideEffect")&&declaration.equals("function(element){return 0;}"));
                    if(ignoreUnique)result(wire,request,new JSONObject().put("result",new JSONObject().put("type","number").put("value",0)));
                    else error(wire,request,-32602,"invalid uniqueContextId");return true;
                }
                if(object.context==null||!unique.equals(object.context.unique)){
                    // Native same-world argument rejection happens before the
                    // supplied DOM function is run. No default-world fallback.
                    error(wire,request,-32000,"Argument should belong to the same JavaScript world as target object");return true;
                }
                if(params.getBoolean("throwOnSideEffect")){
                    proofs++;check(declaration.equals("function(element){return 0;}"));result(wire,request,new JSONObject().put("result",new JSONObject().put("type","number").put("value",0)));return true;
                }
                helpers++;JSONObject value=new JSONObject().put("ok",true);
                if(declaration.contains(MdoBrowserCdpOwnerScripts.GEOMETRY)){
                    check(args.length()==2);JSONObject point=args.getJSONObject(1).getJSONObject("value");
                    value.put("point",new JSONObject().put("x",point.getDouble("x")+10).put("y",point.getDouble("y")+20)).put("focused",true)
                        .put("geometry",new JSONArray().put(1).put(2).put(30).put(40).put(1).put(2).put(30).put(40));
                }else if(declaration.contains(MdoBrowserCdpOwnerScripts.CAPTURE)){check(args.length()==1);value.put("owner",new JSONObject().put("ok",true).put("ref","synthetic-owner"))
                    .put("painted",true).put("content",new JSONObject().put("x",10).put("y",20).put("width",300).put("height",200));}
                else {check(args.length()==1&&declaration.contains(MdoBrowserCdpOwnerScripts.UPLOAD_POLICY));}
                if(parentNavigation)wire.queue(event("Page.frameNavigated","child-session",new JSONObject().put("frame",new JSONObject().put("id","child-local").put("parentId","child-frame").put("loaderId","changed-parent-load"))));
                if(timeout)host.clockOffset.addAndGet(20000);
                if(badValue)value.put("ok","ambiguous");JSONObject reply=new JSONObject().put("result",new JSONObject().put("type","object").put("value",value));
                if(helperException)reply.put("exceptionDetails",new JSONObject().put("text","private owner detail"));result(wire,request,reply);return true;
            }
            if(method.equals("Runtime.releaseObjectGroup")){
                releases++;String group=params.getString("objectGroup");boolean found=false;
                for(ObjectRef object:objects.values())if(object.group.equals(group)){check(Objects.equals(source,object.source));found=true;}
                check(found);
                if(releaseFailure){error(wire,request,-32000,"private cleanup detail");return true;}
                objects.entrySet().removeIf(row->row.getValue().group.equals(group));result(wire,request,new JSONObject());return true;
            }
            return worlds.send(request,wire);
        }
    }
    static final class Case {
        final CdpFramesProbe.Case host=new CdpFramesProbe.Case();final Server server=new Server(host);
        Case()throws Exception{host.plan.extra=server;}
        JSONObject owner(String raw,String action)throws Exception {
            return host.run((binding,deadline)->{
                MdoBrowserCdpFrames graph=binding.frames();JSONArray rows=graph.refresh(deadline).getJSONArray("frames");JSONObject selected=null;
                for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(graph.select(row.getString("id"),row.getString("document")).raw.equals(raw))selected=row;}
                check(selected!=null);return graph.owner(action,selected.getString("id"),selected.getString("document"),action.equals("owner")?new JSONObject().put("x",4).put("y",6):null,deadline);
            });
        }
        void close()throws Exception{host.close();check(server.objects.isEmpty());}
    }
    private static void routing()throws Exception {
        Case sample=new Case();try{
            for(String raw:new String[]{"root-local","child-frame","child-local","leaf-frame"})for(String action:new String[]{"owner","upload_policy","capture_owner"}){
                JSONObject result=sample.owner(raw,action);check(result.getBoolean("ok")&&result.getJSONObject("value").getBoolean("ok"));
                check(result.getString("frame").matches("f-[a-f0-9-]{36}")&&result.getString("parent_document").matches("fd-[a-f0-9-]{36}"));
                check(!result.toString().contains("owner-object-")&&!result.toString().contains("mdo-frame-owner-")&&!result.toString().contains("isolated-root"));
                check(sample.server.objects.isEmpty()&&!sample.host.lastEffect);
            }
            check(sample.server.helpers==12&&sample.server.releases==12&&sample.host.peers.size()==1);
        }finally{sample.close();}
    }
    private static void failures()throws Exception {
        for(int mode=0;mode<8;mode++){
            Case sample=new Case();Server server=sample.server;server.badBackend=mode==0;server.badNode=mode==1;server.wrongRealm=mode==2;server.ignoreUnique=mode==3;
            server.helperException=mode==4;server.badValue=mode==5;server.releaseFailure=mode==6;server.timeout=mode==7;
            String code=mode==0?"cdp_world_metadata_invalid":mode==1?"cdp_owner_node_invalid":mode==2?"cdp_command_rejected":mode==3?"cdp_owner_unique_context_unsupported":
                mode==4?"cdp_world_evaluation_failed":mode==5?"cdp_owner_result_invalid":mode==6?"cdp_owner_cleanup_failed":"cdp_worker_timeout";
            try{rejects(code,()->sample.owner("leaf-frame","owner"));check(!sample.host.lastEffect&&sample.host.peers.size()==1);
                check(server.helpers==(mode<4?0:1));check(server.releases==(mode==0||mode==7?0:1));check(server.objects.isEmpty());}
            finally{sample.close();}
        }
        for(int mode=0;mode<2;mode++){
            Case sample=new Case();sample.server.resolveNavigation=mode==0;sample.server.parentNavigation=mode==1;
            try{rejects("cdp_frame_stale",()->sample.owner(sample.server.parentNavigation?"leaf-frame":"child-frame","owner"));check(sample.server.releases==1&&sample.server.objects.isEmpty());}
            finally{sample.close();}
        }
    }
    private static void inputShape()throws Exception {
        Case sample=new Case();try{sample.host.run((binding,deadline)->{
            MdoBrowserCdpFrames graph=binding.frames();JSONObject root=graph.refresh(deadline).getJSONArray("frames").getJSONObject(0);
            rejects("cdp_owner_point_invalid",()->graph.owner("owner","f0",root.getString("document"),new JSONObject().put("x",-1).put("y",2),deadline));
            rejects("cdp_owner_point_invalid",()->graph.owner("upload_policy","f0",root.getString("document"),new JSONObject(),deadline));
            rejects("cdp_owner_root_unavailable",()->graph.owner("capture_owner","f0",root.getString("document"),null,deadline));
            rejects("cdp_owner_action_invalid",()->graph.owner("arbitrary","f0",root.getString("document"),null,deadline));check(sample.server.queries==0);
            return new JSONObject().put("ok",true);
        });}finally{sample.close();}
    }
    public static void main(String[] ignored)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");routing();failures();inputShape();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_ui_calls_doubled\":true,\"protocol_peers_synthetic\":true,\"provider_qualified\":false}");
    }
}
