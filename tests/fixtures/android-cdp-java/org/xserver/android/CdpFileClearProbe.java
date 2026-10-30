package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Production grammar/worker/frame/world/ancestor/JNI methods. Native protocol
 * DOM and UI peers are controlled; no installed provider is qualified here. */
public final class CdpFileClearProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static final String DOC="d-00000000-0000-4000-8000-000000000001";
    private interface Checked {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("File clear check "+(checks.get()+1));checks.incrementAndGet();}
    private static void rejects(String code,Checked operation)throws Exception {
        try{operation.run();throw new AssertionError("Missing file clear rejection");}
        catch(IOException error){if(code!=null&&!code.equals(error.getMessage()))throw new AssertionError("Expected "+code+" but got "+error.getMessage());check(true);}
    }
    static final class Case implements AutoCloseable,CdpBindingProbe.Factory.Extra {
        final CdpFileInputProbe.Case node;
        volatile int clears,verified;volatile Runnable onClear;String failure="";
        final java.util.Set<String> inspected=new java.util.HashSet<>(),described=new java.util.HashSet<>(),proven=new java.util.HashSet<>(),clearedGroups=new java.util.HashSet<>(),statusGroups=new java.util.HashSet<>();
        Case()throws Exception {this(new CdpFileInputProbe.Case());}
        Case(CdpFileInputProbe.Case node)throws Exception {this.node=node;node.peer.host.plan.extra=this;}
        private void reply(CdpBindingProbe.Wire wire,JSONObject request,JSONObject value,boolean error)throws Exception {
            JSONObject packet=new JSONObject().put("id",request.getInt("id")).put(error?"error":"result",value);
            if(request.has("sessionId"))packet.put("sessionId",request.getString("sessionId"));wire.queue(packet);
        }
        private void navigate(CdpBindingProbe.Wire wire,JSONObject request)throws Exception {
            JSONObject frame=new JSONObject().put("id",node.raw).put("loaderId","replacement-loader");
            JSONObject row=node.nodes.get(node.raw);String parent=row.isNull("parent")?null:row.getString("parent");
            for(Map.Entry<String,JSONObject> item:node.nodes.entrySet())if(item.getValue().getString("id").equals(parent))frame.put("parentId",item.getKey());
            JSONObject event=new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",frame));
            if(request.has("sessionId"))event.put("sessionId",request.getString("sessionId"));wire.queue(event);
        }
        @Override public boolean send(JSONObject request,CdpBindingProbe.Wire wire)throws Exception {
            String method=request.getString("method"),source=request.optString("sessionId",null);JSONObject params=request.getJSONObject("params");
            if(method.equals("DOM.setFileInputFiles")){
                clears++;check(params.length()==2&&params.getJSONArray("files").length()==0);
                CdpFileInputProbe.ObjectRef object=node.objects.get(params.getString("objectId"));
                check(object!=null&&object.context!=null&&Objects.equals(object.source,source)&&object.context.frame.equals(node.raw));
                check(inspected.contains(object.group)&&described.contains(object.group)&&proven.contains(object.group)&&clearedGroups.add(object.group));
                if(onClear!=null)onClear.run();
                if(failure.equals("native_error")){reply(wire,request,new JSONObject().put("code",-32000).put("message","private native clear failure"),true);return true;}
                if(failure.equals("native_navigate"))navigate(wire,request);
                if(failure.equals("native_timeout"))node.peer.host.clockOffset.addAndGet(20000);
                if(failure.equals("native_cancel"))node.peer.host.marker.close();
                reply(wire,request,failure.equals("native_reply")?new JSONObject().put("unexpected",true):new JSONObject(),false);return true;
            }
            if(method.equals("Runtime.callFunctionOn")&&params.optString("functionDeclaration").equals(MdoBrowserCdpFileInput.CLEAR_STATUS)){
                verified++;check(verified<=clears&&params.getBoolean("returnByValue")&&!params.getBoolean("throwOnSideEffect")&&params.getBoolean("silent")&&!params.getBoolean("awaitPromise"));
                check(!params.has("objectId")&&!params.has("executionContextId")&&!params.has("contextId"));
                JSONArray args=params.getJSONArray("arguments");CdpFileInputProbe.ObjectRef object=node.objects.get(args.getJSONObject(0).getString("objectId"));
                check(object!=null&&Objects.equals(object.source,source)&&object.context.unique.equals(params.getString("uniqueContextId"))&&object.group.equals(params.getString("objectGroup")));
                check(clearedGroups.contains(object.group)&&statusGroups.add(object.group));
                JSONObject pin=args.getJSONObject(1).getJSONObject("value");check(args.length()==2&&DOC.equals(pin.getString("document"))&&pin.getString("ref").equals("e7"));
                JSONObject status=new JSONObject().put("ok",true).put("document",DOC).put("ref","e7").put("empty",!failure.equals("not_empty"));
                if(failure.equals("status_ref"))status.put("ref","e8");if(failure.equals("status_document"))status.put("document","another-document");
                if(failure.equals("status_flag"))status.put("empty","true");if(failure.equals("status_failed"))status.put("ok",false);
                JSONObject response=new JSONObject().put("result",new JSONObject().put("type","object").put("value",status));
                if(failure.equals("status_exception"))response.put("exceptionDetails",new JSONObject().put("text","private script failure"));
                if(failure.equals("status_cancel"))node.peer.host.marker.close();
                reply(wire,request,response,false);return true;
            }
            if(method.equals("DOM.describeNode")){
                CdpFileInputProbe.ObjectRef object=node.objects.get(params.optString("objectId"));if(object!=null)described.add(object.group);
            }
            if(method.equals("Runtime.callFunctionOn")&&params.optString("objectGroup").startsWith("mdo-file-input-")&&!params.optString("uniqueContextId").isEmpty()){
                if(params.optBoolean("throwOnSideEffect"))proven.add(params.getString("objectGroup"));
                else if(params.optString("functionDeclaration").equals(MdoBrowserCdpFileInput.INFO))inspected.add(params.getString("objectGroup"));
            }
            return node.send(request,wire);
        }
        @Override public void closed(CdpBindingProbe.Wire wire){node.closed(wire);}
        JSONObject request(String raw)throws Exception {return node.request(raw).put("action","file_clear");}
        JSONObject clear(JSONObject request)throws Exception {
            MdoBrowserCdpFrameRequest operation=MdoBrowserCdpFrameRequest.parse(request);check(operation.effect&&!operation.chooserContinuation());
            return node.peer.host.run(operation.effect,(binding,deadline)->new JSONObject(new String(operation.run(binding,deadline),StandardCharsets.UTF_8)));
        }
        @Override public void close()throws Exception {node.close();}
    }
    private static void routing()throws Exception {
        try(Case sample=new Case()){
            for(String raw:Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame")){
                JSONObject result=sample.clear(sample.request(raw)),value=result.getJSONObject("value");
                check(result.getBoolean("ok")&&value.getBoolean("ok")&&value.getBoolean("cleared")&&value.getBoolean("verified")&&value.getBoolean("dispatched"));
                check(value.getString("document").equals(DOC)&&value.getJSONObject("element").getString("ref").equals("e7")&&value.getString("input_method").equals("native_file_clear"));
                check(sample.node.peer.host.lastEffect&&sample.node.objects.isEmpty()&&sample.node.groups.isEmpty());
                for(String forbidden:Arrays.asList("objectId","backendNodeId","private-file-object","isolated-root","mdo-file-input-","sessionId","files"))check(!result.toString().contains(forbidden));
            }
            check(sample.clears==5&&sample.verified==5&&sample.node.releases==5&&sample.node.peer.host.peers.size()==1);
        }
    }
    private static void failures()throws Exception {
        for(String mode:Arrays.asList("node","realm","unique","getter_exception","tag","backend","ref","document","flag","blocked","helper_exception","timeout","cancel"))
            try(Case sample=new Case()){
                sample.node.failure=mode;rejects(null,()->sample.clear(sample.request("leaf-frame")));
                check(sample.clears==0&&sample.verified==0&&sample.node.objects.isEmpty()&&sample.node.groups.isEmpty());
            }
        for(String phase:Arrays.asList("getter","describe","helper"))try(Case sample=new Case()){
            sample.node.navigationAt=phase;rejects(null,()->sample.clear(sample.request("child-frame")));check(sample.clears==0&&sample.node.objects.isEmpty());
        }
        for(boolean after:new boolean[]{false,true})try(Case sample=new Case()){
            sample.node.peer.server.ownerValue=(child,value)->sample.node.helpers>0&&(!after||sample.clears>0)?value.put("ok",false):value;
            rejects("cdp_file_input_ancestor_blocked",()->sample.clear(sample.request("leaf-frame")));
            check(sample.clears==(after?1:0)&&sample.verified==0&&sample.node.objects.isEmpty());
        }
        for(String mode:Arrays.asList("native_error","native_reply","native_navigate","native_timeout","native_cancel","not_empty","status_ref","status_document","status_flag","status_failed","status_exception","status_cancel"))
            try(Case sample=new Case()){
                sample.failure=mode;rejects(null,()->sample.clear(sample.request("leaf-frame")));
                check(sample.clears==1&&sample.node.peer.host.lastEffect&&sample.node.objects.isEmpty()&&sample.node.groups.isEmpty());
                if(mode.startsWith("native_"))check(sample.verified==0);
            }
        try(Case sample=new Case()){
            sample.node.failure="release";rejects("cdp_file_input_cleanup_failed",()->sample.clear(sample.request("leaf-frame")));
            check(sample.clears==1&&sample.verified==1&&sample.node.peer.host.lastEffect&&sample.node.objects.isEmpty());
        }
        try(Case sample=new Case()){
            sample.failure="native_reply";sample.node.failure="release";
            rejects("cdp_file_input_cleanup_failed",()->sample.clear(sample.request("leaf-frame")));
            check(sample.clears==1&&sample.verified==0&&sample.node.objects.isEmpty());
        }
    }
    private static void grammar()throws Exception {
        try(Case sample=new Case()){
            for(String field:Arrays.asList("files","paths","objectId","backendNodeId","executionContextId","uniqueContextId","sessionId","script","target","url","point"))
                rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(sample.request("leaf-frame").put(field,"untrusted")));
            JSONObject input=sample.request("leaf-frame");MdoBrowserCdpFrameRequest operation=MdoBrowserCdpFrameRequest.parse(input);
            input.put("ref","e8").put("local_document","changed");input.getJSONArray("frame_chain").getJSONObject(0).put("frame","f0");
            JSONObject result=sample.node.peer.host.run(operation.effect,(binding,deadline)->new JSONObject(new String(operation.run(binding,deadline),StandardCharsets.UTF_8)));
            check(result.getJSONObject("value").getJSONObject("element").getString("ref").equals("e7")&&sample.clears==1);
            JSONObject detail=MdoBrowserCdpFrameRequest.failure("cdp_file_clear_unconfirmed",true).getJSONObject("error");
            check(detail.getString("code").equals("file_clear_unconfirmed")&&detail.getBoolean("evaluation_may_have_occurred"));
        }
    }
    private static void productAdmission()throws Exception {
        Case sample=new Case();try(ChooserContinuationHost host=new ChooserContinuationHost(sample.node)){
            int declarations=host.view.evaluations,peers=sample.node.peer.host.peers.size();
            host.dispatch(sample.request("leaf-frame"));check(host.replies.size()==1);
            JSONObject result=host.replies.get(0),detail=result.getJSONObject("error");
            check(!result.getBoolean("ok")&&detail.getString("native_code").equals("cdp_file_clear_requires_upload_route"));
            check(detail.getString("code").equals("unsupported_action")&&!detail.getBoolean("evaluation_may_have_occurred"));
            check(host.view.evaluations==declarations&&sample.node.peer.host.peers.size()==peers&&sample.clears==0);
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");routing();failures();grammar();productAdmission();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"native_file_clear\":true,\"real_jvm_jni_json\":true,\"protocol_peers_synthetic\":true,\"files_delivered\":0,\"provider_qualified\":false}");
    }
}
