package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual native worker/graph/world/owner/JNI with synthetic chooser/DOM peers.
 * No Android ValueCallback is answered and no file is ever delivered. */
public final class CdpChooserProbe {
    private static final String DOC="d-00000000-0000-4000-8000-000000000001";
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean value){if(!value)throw new AssertionError("Chooser check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Checked {void run()throws Exception;}
    private static void rejects(String code,Checked task)throws Exception {
        try{task.run();throw new AssertionError("Missing chooser rejection");}catch(IOException error){
            if(code!=null&&!code.equals(error.getMessage()))throw new AssertionError("Expected "+code+", got "+error.getMessage());check(true);}
    }
    private static JSONObject event(String source,JSONObject params)throws Exception {
        JSONObject result=new JSONObject().put("method","Page.fileChooserOpened").put("params",params);if(source!=null)result.put("sessionId",source);return result;
    }
    private static JSONObject metadata(String raw)throws Exception {return new JSONObject().put("frameId",raw).put("backendNodeId",77).put("mode","selectMultiple");}
    static final class Case implements AutoCloseable,CdpBindingProbe.Factory.Extra {
        final CdpFileInputProbe.Case inputs;final ArrayDeque<JSONObject> waiting=new ArrayDeque<>();final Set<String> listeners=new HashSet<>();
        boolean armingEvent,unsupported,wrongContext,unrelatedBackend,duplicateDuringResolve;int enables,disables,resolves;
        Case()throws Exception {this(true);}
        Case(boolean initialize)throws Exception {inputs=new CdpFileInputProbe.Case(initialize);inputs.peer.host.plan.extra=this;}
        private static String source(String raw){return raw.equals("child-frame")||raw.equals("child-local")?"child-session":raw.equals("leaf-frame")?"leaf-session":null;}
        private void result(CdpBindingProbe.Wire wire,JSONObject request,JSONObject body)throws Exception {
            JSONObject reply=new JSONObject().put("id",request.getInt("id")).put("result",body);if(request.has("sessionId"))reply.put("sessionId",request.getString("sessionId"));wire.queue(reply);
        }
        @Override public boolean send(JSONObject request,CdpBindingProbe.Wire wire)throws Exception {
            String method=request.getString("method"),source=request.optString("sessionId",null);JSONObject params=request.getJSONObject("params");
            check(!method.equals("DOM.setFileInputFiles")&&!method.equals("Page.setInterceptFileChooserDialog"));
            if(method.equals("Page.enable")&&params.has("enableFileChooserOpenedEvent")){
                check(params.length()==1);boolean enabled=params.getBoolean("enableFileChooserOpenedEvent");if(enabled)enables++;else disables++;
                if(unsupported){JSONObject packet=new JSONObject().put("id",request.getInt("id")).put("error",new JSONObject().put("code",-32602).put("message","fixture unsupported event parameter"));
                    if(source!=null)packet.put("sessionId",source);wire.queue(packet);return true;}
                if(enabled)listeners.add(source==null?"root":source);else listeners.remove(source==null?"root":source);
                if(armingEvent&&enabled)wire.queue(event(source,metadata(source==null?"frame-owned":source.equals("child-session")?"child-frame":"leaf-frame")));
            }
            if(method.equals("Page.getFrameTree"))while(!waiting.isEmpty())wire.queue(waiting.remove());
            if(method.equals("DOM.resolveNode")&&params.optString("objectGroup").startsWith("mdo-file-input-")){
                resolves++;String group=params.getString("objectGroup");check(params.length()==3&&params.getInt("backendNodeId")==77);
                check(!params.has("uniqueContextId")&&!params.has("objectId"));
                CdpWorldsProbe.Context context=null;for(CdpWorldsProbe.Context row:inputs.peer.server.worlds.contexts.values())
                    if(Objects.equals(row.source,source)&&row.id==params.getInt("executionContextId"))context=row;
                check(context!=null&&context.frame.equals(inputs.raw));inputs.groups.add(group);
                String object="private-chooser-object-"+resolves;inputs.objects.put(object,new CdpFileInputProbe.ObjectRef(group,source,wrongContext?null:context));
                if(unrelatedBackend)inputs.fileBackend=78;
                if(duplicateDuringResolve)wire.queue(event(source,metadata(inputs.raw)));
                JSONObject remote=new JSONObject().put("type","object").put("subtype",inputs.failure.equals("node")?"array":"node").put("objectId",object);
                result(wire,request,new JSONObject().put("object",remote));return true;
            }
            return inputs.send(request,wire);
        }
        @Override public void closed(CdpBindingProbe.Wire wire){inputs.closed(wire);}
        JSONObject call(String action)throws Exception {return call(new JSONObject().put("command","frames").put("agent",true).put("action",action));}
        JSONObject call(JSONObject request)throws Exception {
            MdoBrowserCdpFrameRequest operation=MdoBrowserCdpFrameRequest.parse(request);check(!operation.effect);
            return inputs.peer.host.run((binding,deadline)->new JSONObject(new String(operation.run(binding,deadline),StandardCharsets.UTF_8)));
        }
        JSONObject opened(String raw)throws Exception {waiting.add(event(source(raw),metadata(raw)));return call("chooser_list").getJSONArray("choosers").getJSONObject(0);}
        JSONObject target(String raw,JSONObject chooser)throws Exception {
            JSONObject request=inputs.request(raw).put("action","chooser_target").put("chooser",chooser.getString("id"));request.remove("ref");return request;
        }
        @Override public void close()throws Exception {inputs.close();}
    }
    private static void routes()throws Exception {
        for(String raw:Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame"))try(Case sample=new Case()){
            JSONObject armed=sample.call("chooser_arm");check(armed.getBoolean("armed")&&!armed.getBoolean("event_verified")&&armed.getJSONArray("choosers").length()==0);
            check(sample.enables==3&&sample.listeners.size()==3);sample.call("chooser_arm");check(sample.enables==3);
            JSONObject picker=sample.opened(raw);check(picker.getString("id").matches("nc-[a-f0-9-]{36}"));
            check(picker.getString("frame").equals(sample.inputs.nodes.get(raw).getString("id"))&&picker.getString("frame_document").equals(sample.inputs.nodes.get(raw).getString("document")));
            check(picker.getLong("remaining_ms")>0&&picker.getLong("remaining_ms")<=120000&&!picker.getBoolean("assignment_supported"));
            JSONObject reply=sample.call(sample.target(raw,picker));check(reply.getString("chooser").equals(picker.getString("id")));
            JSONObject value=reply.getJSONObject("value");check(value.getString("document").equals(DOC)&&value.getJSONObject("element").getString("ref").equals("e7")&&!value.getBoolean("assignment_supported"));
            check(sample.resolves==1&&sample.inputs.helpers==1&&sample.inputs.getters==0&&sample.inputs.objects.isEmpty()&&!sample.inputs.peer.host.lastEffect);
            for(String privateId:Arrays.asList("backendNodeId","objectId","sessionId","private-chooser-object","mdo-file-input-","isolated-root","child-session"))check(!reply.toString().contains(privateId)&&!picker.toString().contains(privateId));
            JSONObject repeated=sample.call("chooser_arm").getJSONArray("choosers").getJSONObject(0);check(repeated.getString("id").equals(picker.getString("id"))&&repeated.getLong("remaining_ms")<=picker.getLong("remaining_ms"));
            check(!sample.call("chooser_disarm").getBoolean("armed")&&sample.disables==3&&sample.listeners.isEmpty());
            rejects("cdp_chooser_not_armed",()->sample.call(sample.target(raw,picker)));
        }
        try(Case sample=new Case()){
            sample.call("chooser_arm");sample.waiting.add(event("leaf-session",metadata("leaf-frame").put("mode","selectSingle")));
            JSONObject picker=sample.call("chooser_list").getJSONArray("choosers").getJSONObject(0);sample.inputs.failure="single";
            check(!sample.call(sample.target("leaf-frame",picker)).getJSONObject("value").getJSONObject("element").getBoolean("multiple"));
        }
    }
    private static void failures()throws Exception {
        for(String mode:Arrays.asList("arming","unsupported","missing_backend","backend_type","mode","extra","wrong_source","unknown_frame","duplicate"))try(Case sample=new Case()){
            if(mode.equals("arming"))sample.armingEvent=true;if(mode.equals("unsupported"))sample.unsupported=true;
            if(mode.equals("arming")||mode.equals("unsupported")){
                rejects(mode.equals("arming")?"cdp_chooser_arming_event":"cdp_chooser_events_unsupported",()->sample.call("chooser_arm"));continue;
            }
            sample.call("chooser_arm");JSONObject meta=metadata("child-frame");
            if(mode.equals("missing_backend"))meta.remove("backendNodeId");if(mode.equals("backend_type"))meta.put("backendNodeId","77");
            if(mode.equals("mode"))meta.put("mode","directory");if(mode.equals("extra"))meta.put("url","https://untrusted.invalid/");
            if(mode.equals("unknown_frame"))meta.put("frameId","unknown-frame");
            sample.waiting.add(event(mode.equals("wrong_source")?null:"child-session",meta));
            if(mode.equals("duplicate"))sample.waiting.add(event("child-session",meta));
            rejects(mode.equals("duplicate")?"cdp_chooser_ambiguous":mode.equals("wrong_source")||mode.equals("unknown_frame")?"cdp_chooser_frame_unproven":"cdp_chooser_metadata_invalid",()->sample.call("chooser_list"));
            check(sample.resolves==0&&!sample.inputs.peer.host.lastEffect);
        }
        for(String mode:Arrays.asList("realm","backend","duplicate","blocked","flag","single","invalid_ref","release","cancel","timeout"))try(Case sample=new Case()){
            sample.call("chooser_arm");JSONObject picker=sample.opened("leaf-frame");
            if(mode.equals("realm"))sample.wrongContext=true;else if(mode.equals("backend"))sample.unrelatedBackend=true;
            else if(mode.equals("duplicate"))sample.duplicateDuringResolve=true;else sample.inputs.failure=mode;
            rejects(null,()->sample.call(sample.target("leaf-frame",picker)));check(sample.inputs.objects.isEmpty()&&!sample.inputs.peer.host.lastEffect);
        }
        try(Case sample=new Case()){
            sample.call("chooser_arm");JSONObject picker=sample.opened("child-frame");sample.inputs.peer.host.plan.childLoader="changed-loader";
            rejects("cdp_frame_stale",()->sample.call("chooser_list"));check(sample.resolves==0);
        }
    }
    private static void grammarAndClock()throws Exception {
        try(Case sample=new Case()){
            sample.call("chooser_arm");JSONObject picker=sample.opened("leaf-frame");
            for(String key:Arrays.asList("backendNodeId","objectId","script","ref","paths","files","sessionId","uniqueContextId")){
                JSONObject request=sample.target("leaf-frame",picker).put(key,"untrusted");rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(request));}
            JSONObject original=sample.target("leaf-frame",picker);MdoBrowserCdpFrameRequest frozen=MdoBrowserCdpFrameRequest.parse(original);
            original.put("chooser","changed").put("local_document","changed");original.getJSONArray("frame_chain").getJSONObject(0).put("frame","f0");
            JSONObject reply=sample.inputs.peer.host.run((binding,deadline)->new JSONObject(new String(frozen.run(binding,deadline),StandardCharsets.UTF_8)));
            check(reply.getString("chooser").equals(picker.getString("id"))&&reply.getJSONObject("value").getString("document").equals(DOC));
            JSONObject foreign=sample.target("leaf-frame",picker).put("chooser","nc-00000000-0000-4000-8000-000000000001");rejects("cdp_chooser_changed",()->sample.call(foreign));
        }
        try(Case sample=new Case()){
            sample.inputs.peer.host.run((binding,deadline)->{
                MdoBrowserCdpFrames frames=binding.frames();AtomicLong clock=new AtomicLong(-10);MdoBrowserCdpChoosers ledger=new MdoBrowserCdpChoosers(frames,clock::get);
                check(ledger.begin());ledger.armed();ledger.event("child-session",metadata("child-frame"));
                JSONObject first=ledger.snapshot().getJSONArray("choosers").getJSONObject(0);String id=first.getString("id");check(first.getLong("remaining_ms")==120000);
                clock.addAndGet(119999);check(!ledger.begin()&&ledger.snapshot().getJSONArray("choosers").getJSONObject(0).getLong("remaining_ms")==1);
                clock.incrementAndGet();check(ledger.snapshot().getJSONArray("choosers").length()==0);rejects("cdp_chooser_expired",()->ledger.require(id));
                rejects("cdp_chooser_ambiguous",()->ledger.event("child-session",metadata("child-frame")));ledger.disable();
                clock.set(1);check(ledger.begin());ledger.armed();ledger.event("child-session",metadata("child-frame"));clock.set(0);rejects("cdp_chooser_clock_changed",ledger::snapshot);
                ledger.disable();clock.set(Long.MAX_VALUE-119999);ledger.begin();ledger.armed();rejects("cdp_chooser_clock_invalid",()->ledger.event("child-session",metadata("child-frame")));
                return new JSONObject().put("ok",true);
            });
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");routes();failures();grammarAndClock();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_chooser_events\":true,\"original_android_callback_joined\":false,\"files_delivered\":0,\"provider_qualified\":false}");
    }
}
