package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual worker/graph/unique worlds/owners/JNI; native protocol DOM is a
 * synthetic peer. Never evidence of an installed WebView or file delivery. */
public final class CdpFileInputProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static final String DOC="d-00000000-0000-4000-8000-000000000001";
    private static void check(boolean value){if(!value)throw new AssertionError("File input check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Checked {void run()throws Exception;}
    private static void rejects(String code,Checked task)throws Exception {
        try{task.run();throw new AssertionError("Missing file-input rejection");}catch(IOException error){check(code==null||code.equals(error.getMessage()));}
    }
    static final class ObjectRef {
        final String group,source;final CdpWorldsProbe.Context context;
        ObjectRef(String group,String source,CdpWorldsProbe.Context context){this.group=group;this.source=source;this.context=context;}
    }
    static final class Case implements AutoCloseable,CdpBindingProbe.Factory.Extra {
        final CdpOwnerProbe.Case peer=new CdpOwnerProbe.Case();final Map<String,JSONObject> nodes=new HashMap<>();
        final Map<String,ObjectRef> objects=new HashMap<>();final Set<String> groups=new HashSet<>();
        String failure="",navigationAt="",raw;int getters,proofs,helpers,describes,releases,disconnects,fileBackend=77;
        Case()throws Exception {
            peer.host.plan.extra=this;
            peer.host.run((binding,deadline)->{MdoBrowserCdpFrames graph=binding.frames();JSONObject inventory=graph.inventory(deadline);
                JSONArray rows=inventory.getJSONArray("frames");for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);nodes.put(graph.select(row.getString("id"),row.getString("document")).raw,row);}
                return new JSONObject().put("ok",true);});
        }
        private void result(CdpBindingProbe.Wire wire,JSONObject request,JSONObject reply)throws Exception {
            JSONObject packet=new JSONObject().put("id",request.getInt("id")).put("result",reply);
            if(request.has("sessionId"))packet.put("sessionId",request.getString("sessionId"));wire.queue(packet);
        }
        private void error(CdpBindingProbe.Wire wire,JSONObject request,int code,String message)throws Exception {
            JSONObject packet=new JSONObject().put("id",request.getInt("id")).put("error",new JSONObject().put("code",code).put("message",message));
            if(request.has("sessionId"))packet.put("sessionId",request.getString("sessionId"));wire.queue(packet);
        }
        private void navigate(CdpBindingProbe.Wire wire,String phase,String source)throws Exception {
            if(!navigationAt.equals(phase))return;JSONObject frame=new JSONObject().put("id",raw).put("loaderId","replacement-loader");
            JSONObject row=nodes.get(raw);String parent=row.isNull("parent")?null:row.getString("parent");
            for(Map.Entry<String,JSONObject> item:nodes.entrySet())if(item.getValue().getString("id").equals(parent))frame.put("parentId",item.getKey());
            JSONObject event=new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",frame));
            if(source!=null)event.put("sessionId",source);wire.queue(event);
        }
        @Override public boolean send(JSONObject request,CdpBindingProbe.Wire wire)throws Exception {
            String method=request.getString("method"),source=request.optString("sessionId",null);JSONObject params=request.getJSONObject("params");
            if(method.equals("Runtime.evaluate")&&params.optString("expression").startsWith(MdoBrowserCdpFileInput.NODE)){
                getters++;String expression=params.getString("expression"),group=params.getString("objectGroup");
                check(!params.getBoolean("returnByValue")&&!params.getBoolean("awaitPromise")&&params.getBoolean("silent")&&!params.getBoolean("throwOnSideEffect"));
                check(!params.has("contextId")&&!params.has("executionContextId")&&group.matches("mdo-file-input-[a-f0-9-]{36}"));groups.add(group);
                CdpWorldsProbe.Context context=null;for(CdpWorldsProbe.Context candidate:peer.server.worlds.contexts.values())
                    if(candidate.unique.equals(params.getString("uniqueContextId"))&&Objects.equals(candidate.source,source))context=candidate;
                check(context!=null&&context.frame.equals(raw));JSONObject pin=new JSONObject(expression.substring(MdoBrowserCdpFileInput.NODE.length()+1,expression.length()-1));
                check(pin.getString("document").equals(DOC)&&pin.getString("ref").equals("e7"));
                String object="private-file-object-"+getters;objects.put(object,new ObjectRef(group,source,failure.equals("realm")?null:context));
                JSONObject remote=new JSONObject().put("type","object").put("subtype",failure.equals("node")?"array":"node").put("objectId",object);
                JSONObject reply=new JSONObject().put("result",remote);if(failure.equals("getter_exception"))reply.put("exceptionDetails",new JSONObject().put("text","private DOM detail"));
                navigate(wire,"getter",source);result(wire,request,reply);return true;
            }
            if(method.equals("DOM.describeNode")){
                describes++;ObjectRef object=objects.get(params.getString("objectId"));check(object!=null&&Objects.equals(object.source,source));
                check(params.length()==3&&params.getInt("depth")==0&&!params.getBoolean("pierce"));
                JSONObject node=new JSONObject().put("nodeType",1).put("nodeName",failure.equals("tag")?"SPAN":failure.equals("lowercase")?"input":"INPUT")
                    .put("localName",failure.equals("tag")?"span":"input").put("backendNodeId",failure.equals("backend")?-1:fileBackend);
                navigate(wire,"describe",source);result(wire,request,new JSONObject().put("node",node));return true;
            }
            if(method.equals("Runtime.callFunctionOn")&&params.optString("objectGroup").startsWith("mdo-file-input-")){
                check(!params.has("objectId")&&!params.has("executionContextId")&&!params.has("contextId"));
                check(params.getBoolean("returnByValue")&&params.getBoolean("silent")&&!params.getBoolean("awaitPromise"));
                JSONArray args=params.getJSONArray("arguments");ObjectRef object=objects.get(args.getJSONObject(0).getString("objectId"));
                check(object!=null&&Objects.equals(object.source,source)&&object.group.equals(params.getString("objectGroup")));
                String declaration=params.getString("functionDeclaration"),unique=params.getString("uniqueContextId");
                if(unique.isEmpty()){
                    check(declaration.equals("function(element){return 0;}")&&params.getBoolean("throwOnSideEffect"));
                    if(failure.equals("unique"))result(wire,request,new JSONObject().put("result",new JSONObject().put("type","number").put("value",0)));
                    else error(wire,request,-32602,"invalid uniqueContextId");return true;
                }
                if(object.context==null||!unique.equals(object.context.unique)){error(wire,request,-32000,"Argument should belong to the same JavaScript world as target object");return true;}
                if(params.getBoolean("throwOnSideEffect")){
                    proofs++;check(declaration.equals("function(element){return 0;}")&&args.length()==1);
                    result(wire,request,new JSONObject().put("result",new JSONObject().put("type","number").put("value",0)));return true;
                }
                boolean pinNode=declaration.equals(MdoBrowserCdpFileInput.PIN);
                helpers++;check((pinNode||declaration.equals(MdoBrowserCdpFileInput.INFO))&&args.length()==2);
                JSONObject pin=args.getJSONObject(1).getJSONObject("value");check(pin.getString("document").equals(DOC)&&(pinNode?!pin.has("ref"):pin.getString("ref").equals("e7")));
                JSONObject info=new JSONObject().put("ok",true).put("document",DOC).put("ref","e7").put("multiple",true).put("directory",false).put("hidden",true);
                if(failure.equals("blocked"))info=new JSONObject().put("ok",false).put("code","element_disabled");
                if(failure.equals("ref"))info.put("ref","e8");if(failure.equals("document"))info.put("document","d-00000000-0000-4000-8000-000000000002");
                if(failure.equals("invalid_ref"))info.put("ref","e0");if(failure.equals("single"))info.put("multiple",false);
                if(failure.equals("flag"))info.put("hidden","false");
                JSONObject reply=new JSONObject().put("result",new JSONObject().put("type","object").put("value",info));
                if(failure.equals("helper_exception"))reply.put("exceptionDetails",new JSONObject().put("text","private DOM detail"));
                if(failure.equals("timeout"))peer.host.clockOffset.addAndGet(20000);
                if(failure.equals("cancel"))peer.host.marker.close();
                navigate(wire,"helper",source);result(wire,request,reply);return true;
            }
            if(method.equals("Runtime.releaseObjectGroup")&&params.optString("objectGroup").startsWith("mdo-file-input-")){
                releases++;String group=params.getString("objectGroup");check(groups.contains(group));
                for(ObjectRef object:objects.values())if(object.group.equals(group))check(Objects.equals(object.source,source));
                if(failure.equals("release")){error(wire,request,-32000,"private release detail");return true;}
                objects.entrySet().removeIf(row->row.getValue().group.equals(group));groups.remove(group);result(wire,request,new JSONObject());return true;
            }
            return peer.server.send(request,wire);
        }
        @Override public void closed(CdpBindingProbe.Wire wire){disconnects++;objects.clear();groups.clear();peer.server.closed(wire);}
        JSONObject request(String raw)throws Exception {
            this.raw=raw;JSONObject leaf=nodes.get(raw);JSONArray chain=new JSONArray();JSONObject row=leaf;
            while(row!=null){chain.put(new JSONObject().put("frame",row.getString("id")).put("document",row.getString("document")));
                String parent=row.isNull("parent")?null:row.getString("parent");row=null;
                if(parent!=null)for(JSONObject candidate:nodes.values())if(candidate.getString("id").equals(parent))row=candidate;
            }
            return new JSONObject().put("command","frames").put("agent",true).put("action","file_input").put("frame",leaf.getString("id"))
                .put("document",leaf.getString("document")).put("local_document",DOC).put("ref","e7").put("frame_chain",chain);
        }
        JSONObject inspect(JSONObject request)throws Exception {
            MdoBrowserCdpFrameRequest operation=MdoBrowserCdpFrameRequest.parse(request);check(!operation.effect);
            return peer.host.run((binding,deadline)->new JSONObject(new String(operation.run(binding,deadline),StandardCharsets.UTF_8)));
        }
        @Override public void close()throws Exception {peer.close();check(objects.isEmpty()&&groups.isEmpty());}
    }
    private static void routing()throws Exception {
        try(Case sample=new Case()){
            for(String raw:Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame")){
                JSONObject result=sample.inspect(sample.request(raw)),value=result.getJSONObject("value");check(result.getBoolean("ok")&&value.getBoolean("ok"));
                check(value.getString("document").equals(DOC)&&value.getJSONObject("element").getString("ref").equals("e7"));
                check(value.getJSONObject("element").getBoolean("hidden")&&!value.getJSONObject("element").getBoolean("directory"));
                check(sample.objects.isEmpty()&&sample.groups.isEmpty()&&!sample.peer.host.lastEffect);
                for(String forbidden:Arrays.asList("objectId","backendNodeId","private-file-object","isolated-root","mdo-file-input-","sessionId"))check(!result.toString().contains(forbidden));
            }
            check(sample.getters==5&&sample.proofs==5&&sample.helpers==5&&sample.describes==5&&sample.releases==5&&sample.peer.host.peers.size()==1);
        }
        try(Case sample=new Case()){sample.failure="lowercase";check(sample.inspect(sample.request("frame-owned")).getBoolean("ok"));}
    }
    private static void failures()throws Exception {
        Map<String,String> codes=new HashMap<>();
        codes.put("node","cdp_file_input_node_invalid");codes.put("realm","cdp_command_rejected");codes.put("unique","cdp_file_input_unique_context_unsupported");
        codes.put("getter_exception","cdp_file_input_node_invalid");codes.put("tag","cdp_file_input_node_invalid");codes.put("backend","cdp_world_metadata_invalid");
        for(String key:Arrays.asList("ref","document","flag"))codes.put(key,"cdp_file_input_result_invalid");
        codes.put("blocked","cdp_file_input_element_disabled");codes.put("helper_exception","cdp_world_evaluation_failed");codes.put("release","cdp_file_input_cleanup_failed");
        codes.put("timeout","cdp_worker_timeout");codes.put("cancel","cdp_worker_cancelled");
        for(String mode:Arrays.asList("node","realm","unique","getter_exception","tag","backend","ref","document","flag","blocked","helper_exception","release","timeout","cancel"))
            try(Case sample=new Case()){
                sample.failure=mode;rejects(codes.get(mode),()->sample.inspect(sample.request("leaf-frame")));
                check(sample.getters==1&&!sample.peer.host.lastEffect&&sample.peer.host.peers.size()==1);
                check(sample.objects.isEmpty()&&sample.groups.isEmpty());
                if(Arrays.asList("node","realm","unique","getter_exception","tag","backend").contains(mode))check(sample.helpers==0);
            }
        for(String phase:Arrays.asList("getter","describe","helper"))try(Case sample=new Case()){
            sample.navigationAt=phase;rejects(null,()->sample.inspect(sample.request("child-frame")));check(sample.objects.isEmpty()&&!sample.peer.host.lastEffect);
        }
        try(Case sample=new Case()){
            sample.peer.server.ownerValue=(raw,value)->new JSONObject().put("ok",false).put("error",new JSONObject().put("code","element_disabled"));
            rejects("cdp_file_input_ancestor_blocked",()->sample.inspect(sample.request("leaf-frame")));check(sample.getters==0);
        }
        try(Case sample=new Case()){
            sample.peer.server.ownerValue=(raw,value)->sample.helpers==0?value:new JSONObject().put("ok",false).put("error",new JSONObject().put("code","element_disabled"));
            rejects("cdp_file_input_ancestor_blocked",()->sample.inspect(sample.request("leaf-frame")));check(sample.getters==1&&sample.helpers==1&&sample.objects.isEmpty());
        }
    }
    private static void grammar()throws Exception {
        try(Case sample=new Case()){
            for(String extra:Arrays.asList("script","objectId","backendNodeId","sessionId","uniqueContextId","paths","files","url","point")){
                JSONObject request=sample.request("leaf-frame").put(extra,"untrusted");rejects(null,()->MdoBrowserCdpFrameRequest.parse(request));}
            for(Object invalid:Arrays.asList("e0","e-1","e1;code",1,JSONObject.NULL)){
                JSONObject request=sample.request("leaf-frame").put("ref",invalid);rejects("cdp_file_input_request_invalid",()->MdoBrowserCdpFrameRequest.parse(request));}
            JSONObject mismatched=sample.request("leaf-frame").put("frame","f0");rejects("cdp_file_input_request_invalid",()->MdoBrowserCdpFrameRequest.parse(mismatched));
            JSONObject request=sample.request("leaf-frame");MdoBrowserCdpFrameRequest operation=MdoBrowserCdpFrameRequest.parse(request);
            request.put("ref","e8").put("local_document","changed");request.getJSONArray("frame_chain").getJSONObject(0).put("frame","f0");
            JSONObject result=sample.peer.host.run((binding,deadline)->new JSONObject(new String(operation.run(binding,deadline),StandardCharsets.UTF_8)));
            check(result.getJSONObject("value").getJSONObject("element").getString("ref").equals("e7"));
            for(String nativeCode:Arrays.asList("cdp_file_input_ancestor_blocked","cdp_file_input_element_disabled","cdp_file_input_node_invalid","cdp_file_input_cleanup_failed","cdp_file_input_request_invalid")){
                JSONObject mapped=MdoBrowserCdpFrameRequest.failure(nativeCode,false).getJSONObject("error");
                check(mapped.getString("native_code").equals(nativeCode)&&!mapped.getBoolean("evaluation_may_have_occurred"));
                check(mapped.getString("code").equals(nativeCode.endsWith("blocked")||nativeCode.endsWith("disabled")?"element_disabled":nativeCode.endsWith("node_invalid")?"upload_target_changed":nativeCode.substring(4)));
            }
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");routing();failures();grammar();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_file_input_inspection\":true,\"protocol_peers_synthetic\":true,\"files_delivered\":0,\"provider_qualified\":false}");
    }
}
