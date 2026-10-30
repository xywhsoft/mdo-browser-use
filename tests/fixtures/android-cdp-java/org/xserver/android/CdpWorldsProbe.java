package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual world/frame/binding/worker/session/strict JNI; Android and protocol
 * evaluation peers are doubled. No installed-provider/real DOM claim. */
public final class CdpWorldsProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static final String SCRIPT="({ok:true,text:'fixture'})";
    private static void check(boolean value){if(!value)throw new AssertionError("Worlds check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Checked {void run()throws Exception;}
    private static void rejects(String code,Checked operation)throws Exception {
        try{operation.run();throw new AssertionError("Missing isolated-world failure");}catch(IOException error){check(code.equals(error.getMessage()));}
    }
    private static JSONObject event(String method,String source,JSONObject params)throws Exception {
        JSONObject event=new JSONObject().put("method",method).put("params",params);if(source!=null)event.put("sessionId",source);return event;
    }
    static final class Context {
        final int id;final String source,frame,name,unique;
        Context(int id,String source,String frame,String name){this.id=id;this.source=source;this.frame=frame;this.name=name;unique="isolated-"+(source==null?"root":source)+"-"+id;}
    }
    static final class Engine implements CdpBindingProbe.Factory.Extra {
        interface Evaluation {Object value(Context context,String expression)throws Exception;}
        Evaluation evaluation;
        final Map<String,Context> contexts=new HashMap<>();final Map<String,Integer> ids=new HashMap<>();
        final Map<CdpBindingProbe.Wire,String> namespaces=new java.util.IdentityHashMap<>();
        final ArrayDeque<JSONObject> pending=new ArrayDeque<>();String name;
        int created,createCalls,negative,proofs,evaluations;boolean seenPageCode;
        boolean wrongFrame,wrongSource,wrongName,wrongType,defaultWorld,missingUnique,wrongReply,ignoreUnique,proofException,noContext,lateContext,
            duplicateUnique,destroyDuringProof,destroyDuringEval,clearDuringEval,lossDuringEval,navigateDuringEval,parentDuringEval,exceptionValue,badValue,largeValue,unsupported;
        Object value=new JSONObject();
        Engine()throws Exception {value=new JSONObject().put("ok",true).put("text","isolated fixture");}
        private String key(String source,String frame,String name){return(source==null?"":source)+":"+frame+":"+name;}
        private JSONObject metadata(Context context)throws Exception {
            JSONObject data=new JSONObject().put("id",context.id).put("name",wrongName?"other-world":context.name);
            if(!missingUnique)data.put("uniqueId",duplicateUnique?"-7.42":context.unique);
            data.put("auxData",new JSONObject().put("isDefault",defaultWorld).put("type",wrongType?"default":"isolated").put("frameId",wrongFrame?"other-frame":context.frame));
            return event("Runtime.executionContextCreated",wrongSource?"unadmitted-session":context.source,new JSONObject().put("context",data));
        }
        private void result(CdpBindingProbe.Wire wire,JSONObject request,JSONObject reply)throws Exception {
            JSONObject packet=new JSONObject().put("id",request.getInt("id")).put("result",reply);if(request.has("sessionId"))packet.put("sessionId",request.getString("sessionId"));wire.queue(packet);
        }
        private void error(CdpBindingProbe.Wire wire,JSONObject request,int code,String message)throws Exception {
            JSONObject packet=new JSONObject().put("id",request.getInt("id")).put("error",new JSONObject().put("code",code).put("message",message));
            if(request.has("sessionId"))packet.put("sessionId",request.getString("sessionId"));wire.queue(packet);
        }
        private void destroy(CdpBindingProbe.Wire wire,Context context)throws Exception {
            contexts.remove(key(context.source,context.frame,context.name));wire.queue(event("Runtime.executionContextDestroyed",context.source,new JSONObject().put("executionContextId",context.id)));
        }
        public boolean send(JSONObject request,CdpBindingProbe.Wire wire)throws Exception {
            String method=request.getString("method"),source=request.has("sessionId")?request.getString("sessionId"):null;JSONObject params=request.getJSONObject("params");
            if(method.equals("Page.getFrameTree")){while(!pending.isEmpty())wire.queue(pending.removeFirst());return false;}
            if(method.equals("Page.createIsolatedWorld")){
                createCalls++;check(Boolean.FALSE.equals(params.opt("grantUniveralAccess"))&&!params.has("grantUniversalAccess"));
                String nextName=params.getString("worldName");check(nextName.matches("mdo\\.browser-use\\.frames\\.v1\\.[a-f0-9-]{36}\\.[a-f0-9-]{36}"));
                String namespace=nextName.substring(0,nextName.lastIndexOf('.'));
                String original=namespaces.get(wire);if(original==null){check(!namespaces.containsValue(namespace));namespaces.put(wire,namespace);}else check(original.equals(namespace));
                if(name==null)name=namespace;
                if(unsupported){error(wire,request,-32601,"unsupported fixture");return true;}
                String frame=params.getString("frameId"),key=key(source,frame,nextName);Context context=contexts.get(key);boolean fresh=context==null;
                if(fresh){int id=ids.containsKey(source)?ids.get(source)+1:100;ids.put(source,id);context=new Context(id,source,frame,nextName);contexts.put(key,context);created++;}
                if(fresh&&!noContext&&!lateContext)wire.queue(metadata(context));result(wire,request,new JSONObject().put("executionContextId",wrongReply?999:context.id));
                if(fresh&&!noContext&&lateContext)wire.queue(metadata(context));return true;
            }
            if(!method.equals("Runtime.evaluate"))return false;
            check(!params.has("contextId")&&params.getBoolean("returnByValue")&&params.getBoolean("silent")&&!params.getBoolean("awaitPromise"));
            check(params.getInt("timeout")>0&&params.getInt("timeout")<=5000);
            String expression=params.getString("expression"),unique=params.getString("uniqueContextId");
            if(expression.equals("void 0")&&unique.isEmpty()){
                negative++;check(params.getBoolean("throwOnSideEffect"));
                if(ignoreUnique)result(wire,request,new JSONObject().put("result",new JSONObject().put("type","undefined")));
                else error(wire,request,-32602,"invalid uniqueContextId");return true;
            }
            Context context=null;for(Context row:contexts.values())if(row.unique.equals(unique)&&Objects.equals(row.source,source))context=row;
            if(context==null){error(wire,request,-32602,"uniqueContextId not found");return true;}
            if(expression.equals("void 0")){
                proofs++;check(params.getBoolean("throwOnSideEffect"));if(destroyDuringProof)destroy(wire,context);
                JSONObject reply=new JSONObject().put("result",new JSONObject().put("type","undefined"));if(proofException)reply.put("exceptionDetails",new JSONObject().put("text","private fixture detail"));
                result(wire,request,reply);return true;
            }
            if(evaluation==null)check(expression.equals(SCRIPT));else value=evaluation.value(context,expression);
            check(!params.getBoolean("throwOnSideEffect"));seenPageCode=true;evaluations++;
            if(lossDuringEval){wire.close();return true;}
            if(destroyDuringEval)destroy(wire,context);
            if(navigateDuringEval||parentDuringEval){
                String changed=parentDuringEval?"child-frame":context.frame;
                wire.queue(event("Page.frameNavigated",context.source,new JSONObject().put("frame",new JSONObject().put("id",changed)
                    .put("parentId","frame-owned").put("loaderId","changed-native-load"))));
            }
            if(clearDuringEval){wire.queue(event("Runtime.executionContextsCleared",source,new JSONObject()));contexts.entrySet().removeIf(row->Objects.equals(row.getValue().source,source));}
            JSONObject remote=new JSONObject().put("type",value==JSONObject.NULL||value instanceof JSONObject||value instanceof JSONArray?"object":value instanceof String?"string":value instanceof Boolean?"boolean":"number");
            if(badValue)remote.put("unserializableValue","NaN");else if(largeValue){char[] chars=new char[1048577];java.util.Arrays.fill(chars,'x');remote.put("type","string").put("value",new String(chars));}
            else remote.put("value",value);
            JSONObject reply=new JSONObject().put("result",remote);if(exceptionValue)reply.put("exceptionDetails",new JSONObject().put("text","private fixture evaluation detail"));result(wire,request,reply);return true;
        }
    }
    private static final class Case {
        final CdpFramesProbe.Case host=new CdpFramesProbe.Case();final Engine engine=new Engine();
        Case()throws Exception {host.plan.extra=engine;}
        JSONObject evaluate(boolean effect)throws Exception {
            return evaluate("frame-owned",effect);
        }
        JSONObject evaluate(String target,boolean effect)throws Exception {
            return host.run(effect,(binding,deadline)->{
                MdoBrowserCdpFrames graph=binding.frames();JSONArray rows=graph.refresh(deadline).getJSONArray("frames");JSONObject row=null;
                for(int i=0;i<rows.length();i++){JSONObject candidate=rows.getJSONObject(i);if(graph.select(candidate.getString("id"),candidate.getString("document")).raw.equals(target))row=candidate;}
                check(row!=null);
                return graph.evaluate(row.getString("id"),row.getString("document"),SCRIPT,effect,deadline);
            });
        }
        void close()throws Exception {host.close();}
    }
    private static void nestedAndCache()throws Exception {
        Case sample=new Case();try{
            sample.host.run((binding,deadline)->{
                MdoBrowserCdpFrames graph=binding.frames();JSONObject inventory=graph.inventory(deadline);JSONArray frames=inventory.getJSONArray("frames");check(frames.length()==5&&inventory.getBoolean("complete"));
                for(int i=0;i<frames.length();i++){
                    JSONObject frame=frames.getJSONObject(i);check(frame.getBoolean("isolated"));
                    JSONObject value=graph.evaluate(frame.getString("id"),frame.getString("document"),SCRIPT,false,deadline);
                    check(value.getString("frame").equals(frame.getString("id"))&&value.getString("frame_document").equals(frame.getString("document"))&&value.getJSONObject("value").getBoolean("ok"));
                }
                check(sample.engine.created==5&&sample.engine.createCalls==5&&sample.engine.negative==3&&sample.engine.proofs==5&&sample.engine.evaluations==5);
                graph.inventory(deadline);check(sample.engine.created==5&&sample.engine.createCalls==5);
                check(!inventory.toString().contains(sample.engine.name)&&!inventory.toString().contains("isolated-root"));return inventory;
            });
            check(sample.host.peers.size()==1);
        }finally{sample.close();}
        Case late=new Case();late.engine.lateContext=true;try{check(late.evaluate(false).getJSONObject("value").getBoolean("ok")&&late.engine.evaluations==1);}finally{late.close();}
    }
    private static void proofFailures()throws Exception {
        for(int mode=0;mode<11;mode++){
            Case sample=new Case();Engine engine=sample.engine;engine.wrongFrame=mode==0;engine.wrongName=mode==1;engine.wrongType=mode==2;
            engine.defaultWorld=mode==3;engine.missingUnique=mode==4;engine.wrongReply=mode==5;engine.ignoreUnique=mode==6;
            engine.proofException=mode==7;engine.noContext=mode==8;engine.duplicateUnique=mode==9;engine.wrongSource=mode==10;
            String code=mode==0||mode==2||mode==3||mode==4||mode==9?"cdp_event_failed":mode==6?"cdp_world_unique_context_unsupported":mode==10?"cdp_session_mismatch":"cdp_world_context_unsupported";
            try{rejects(code,()->sample.evaluate(false));check(!engine.seenPageCode&&engine.evaluations==0&&sample.host.peers.size()==1);}finally{sample.close();}
        }
        Case missing=new Case();missing.engine.unsupported=true;try{rejects("cdp_method_unsupported",()->missing.evaluate(false));check(missing.engine.evaluations==0);}finally{missing.close();}
    }
    private static void lossAndEffects()throws Exception {
        for(int mode=0;mode<6;mode++){
            Case sample=new Case();sample.engine.destroyDuringProof=mode==0;sample.engine.destroyDuringEval=mode==1;sample.engine.clearDuringEval=mode==2;sample.engine.lossDuringEval=mode==3;
            sample.engine.navigateDuringEval=mode==4;sample.engine.parentDuringEval=mode==5;
            String code=mode==3?"cdp_transport_failed":mode==0?"cdp_world_context_changed":"cdp_frame_stale";
            String target=mode==5?"child-local":mode==2||mode==4?"child-frame":"frame-owned";
            try{rejects(code,()->sample.evaluate(target,true));check(sample.engine.evaluations==(mode==0?0:1));check(sample.host.lastEffect&&sample.host.peers.size()==1);}finally{sample.close();}
        }
    }
    private static void recoveryAndNames()throws Exception {
        Case sample=new Case();String firstName;try{
            sample.host.run((binding,deadline)->{
                MdoBrowserCdpFrames graph=binding.frames();JSONObject initial=graph.inventory(deadline);JSONObject old=initial.getJSONArray("frames").getJSONObject(0);
                MdoBrowserCdpFrames.Route original=graph.select(old.getString("id"),old.getString("document"));Context context=null;
                for(Context row:sample.engine.contexts.values())if(row.source==null&&row.frame.equals("frame-owned"))context=row;
                check(context!=null);sample.engine.contexts.remove(sample.engine.key(context.source,context.frame,context.name));
                sample.engine.pending.add(event("Runtime.executionContextDestroyed",null,new JSONObject().put("executionContextId",context.id)));
                binding.call("Page.getFrameTree",new JSONObject(),1000);rejects("cdp_frame_stale",()->graph.validate(original));
                JSONObject current=graph.inventory(deadline);check(sample.engine.created==6);
                check(!current.getJSONArray("frames").getJSONObject(0).getString("document").equals(original.document));
                int calls=sample.engine.createCalls;
                sample.engine.pending.add(event("Page.frameStartedLoading",null,new JSONObject().put("frameId","frame-owned")));
                JSONObject loading=graph.refresh(deadline);for(int i=0;i<loading.getJSONArray("frames").length();i++)check(!loading.getJSONArray("frames").getJSONObject(i).getBoolean("isolated"));
                sample.engine.pending.add(event("Page.frameStoppedLoading",null,new JSONObject().put("frameId","frame-owned")));
                JSONObject restored=graph.inventory(deadline);check(sample.engine.createCalls==calls&&sample.engine.created==6);
                for(int i=0;i<restored.getJSONArray("frames").length();i++)check(restored.getJSONArray("frames").getJSONObject(i).getBoolean("isolated"));return restored;
            });firstName=sample.engine.name;
        }finally{sample.close();}
        Case other=new Case();try{other.evaluate(false);check(!firstName.equals(other.engine.name));}finally{other.close();}
    }
    private static void resourceBounds()throws Exception {
        for(int mode=0;mode<2;mode++){
            Case sample=new Case();final boolean churn=mode==1;
            try{rejects("cdp_event_failed",()->sample.host.run((binding,deadline)->{
                MdoBrowserCdpFrames graph=binding.frames();graph.refresh(deadline);
                for(int i=0;i<(churn?1025:257);i++){
                    int id=1000+i;JSONObject context=new JSONObject().put("id",id).put("name","unrelated-utility").put("uniqueId","utility-"+i)
                        .put("auxData",new JSONObject().put("frameId","child-frame").put("isDefault",false).put("type","isolated"));
                    sample.engine.pending.add(event("Runtime.executionContextCreated","child-session",new JSONObject().put("context",context)));
                    if(churn)sample.engine.pending.add(event("Runtime.executionContextDestroyed","child-session",new JSONObject().put("executionContextId",id)));
                }
                binding.call("Page.getFrameTree",new JSONObject(),5000);return new JSONObject().put("ok",true);
            }));check(sample.engine.evaluations==0&&sample.host.peers.size()==1);}finally{sample.close();}
        }
    }
    private static void loaderNamesAndOldEvents()throws Exception {
        Case sample=new Case();try{sample.host.run((binding,deadline)->{
            MdoBrowserCdpFrames graph=binding.frames();graph.inventory(deadline);Context old=null;
            for(Context context:sample.engine.contexts.values())if(context.frame.equals("child-frame"))old=context;
            check(old!=null);sample.host.plan.childLoader="new-native-loader";
            sample.engine.pending.add(event("Page.frameNavigated","child-session",new JSONObject().put("frame",sample.host.plan.tree("child-session").getJSONObject("frame"))));
            sample.engine.pending.add(event("Page.frameStoppedLoading","child-session",new JSONObject().put("frameId","child-frame")));
            JSONObject snapshot=graph.inventory(deadline);MdoBrowserCdpFrames.Route fresh=null;Context current=null;
            for(int i=0;i<snapshot.getJSONArray("frames").length();i++){
                JSONObject row=snapshot.getJSONArray("frames").getJSONObject(i);MdoBrowserCdpFrames.Route route=graph.select(row.getString("id"),row.getString("document"));if(route.raw.equals("child-frame"))fresh=route;
            }
            for(Context context:sample.engine.contexts.values())if(context.frame.equals("child-frame")&&!context.name.equals(old.name))current=context;
            check(fresh!=null&&current!=null&&!old.name.equals(current.name));
            sample.engine.contexts.remove(sample.engine.key(old.source,old.frame,old.name));sample.engine.pending.add(event("Runtime.executionContextDestroyed",old.source,new JSONObject().put("executionContextId",old.id)));
            binding.call("Page.getFrameTree",new JSONObject(),1000);graph.validate(fresh);
            JSONObject after=graph.refresh(deadline);boolean ready=false;for(int i=0;i<after.getJSONArray("frames").length();i++){
                JSONObject row=after.getJSONArray("frames").getJSONObject(i);if(row.getString("id").equals(fresh.frame))ready=row.getBoolean("isolated")&&row.getString("document").equals(fresh.document);
            }check(ready);return after;
        });}finally{sample.close();}
    }
    private static void valuesAndLimits()throws Exception {
        for(Object value:new Object[]{"text","汉字\uD83D\uDE42",true,42,new JSONArray().put("a"),JSONObject.NULL}){
            Case sample=new Case();sample.engine.value=value;try{JSONObject reply=sample.evaluate(false);check(reply.has("value")&&sample.engine.evaluations==1);}finally{sample.close();}
        }
        for(int mode=0;mode<3;mode++){
            Case sample=new Case();sample.engine.exceptionValue=mode==0;sample.engine.badValue=mode==1;sample.engine.largeValue=mode==2;
            try{rejects(mode==2?"cdp_world_result_limit":"cdp_world_evaluation_failed",()->sample.evaluate(true));check(sample.engine.evaluations==1);}finally{sample.close();}
        }
        Case invalid=new Case();try{invalid.host.run((binding,deadline)->{
            MdoBrowserCdpFrames graph=binding.frames();JSONObject row=graph.refresh(deadline).getJSONArray("frames").getJSONObject(0);
            rejects("cdp_world_script_invalid",()->graph.evaluate(row.getString("id"),row.getString("document"),"",false,deadline));
            check(invalid.engine.createCalls==0&&invalid.engine.evaluations==0);return new JSONObject().put("ok",true);
        });}finally{invalid.close();}
    }
    public static void main(String[] ignored)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");nestedAndCache();proofFailures();lossAndEffects();recoveryAndNames();resourceBounds();loaderNamesAndOldEvents();valuesAndLimits();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_ui_calls_doubled\":true,\"protocol_peers_synthetic\":true,\"provider_qualified\":false}");
    }
}
