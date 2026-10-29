package org.xserver.android;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Private per-frame isolated execution. Metadata IDs never grant authority on
 * their own: a native frame route, matching creation reply/unique context and
 * negative unique-ID capability proof are all required before page code. */
final class MdoBrowserCdpWorlds {
    private static final int ACTIVE_LIMIT=256,HISTORY_LIMIT=1024,WORLD_LIMIT=32,RESULT_LIMIT=1048576,SCRIPT_LIMIT=196608;
    private static final class Context {
        final int id;final String session,unique,frame;final Name name;String admittedDocument;
        Context(int id,String session,String unique,String frame,Name name){this.id=id;this.session=session;this.unique=unique;this.frame=frame;this.name=name;}
    }
    private static final class Name {
        final String value,raw,session,loader;
        Name(String value,MdoBrowserCdpFrames.Route route){this.value=value;raw=route.raw;session=route.session;loader=route.loader;}
    }
    private static final class World {
        final MdoBrowserCdpFrames.Route route;final Context context;
        World(MdoBrowserCdpFrames.Route route,Context context){this.route=route;this.context=context;}
    }
    private final MdoBrowserCdpBinding binding;private final MdoBrowserCdpFrames frames;
    private final Map<String,Context> contexts=new HashMap<>();private final Set<String> seen=new HashSet<>(),qualified=new HashSet<>();
    private final Map<String,World> worlds=new HashMap<>();
    private final Map<String,Name> names=new HashMap<>(),namesByProof=new HashMap<>();
    MdoBrowserCdpWorlds(MdoBrowserCdpBinding binding,MdoBrowserCdpFrames frames)throws IOException {
        this.binding=binding;this.frames=frames;
        // Runtime was enabled while proving the root, before this listener
        // existed. Retain minimal active identities, no origin/URL/page name.
        for(Map.Entry<Integer,String> row:binding.contextIdentities().entrySet())add(new Context(row.getKey(),null,row.getValue(),null,null));
    }
    private static IOException problem(String code){return new IOException(code);}
    private static String key(String source,int id){return(source==null?"":source)+":"+id;}
    private static String scope(String source){return source==null?"":source;}
    private static int number(Object value)throws IOException {
        if(!(value instanceof Integer)||((Integer)value)<=0)throw problem("cdp_world_metadata_invalid");return(Integer)value;
    }
    private static boolean identity(Object value){
        if(!(value instanceof String)||((String)value).isEmpty()||((String)value).length()>128)return false;
        String text=(String)value;for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||"_.:-".indexOf(c)>=0))return false;}return true;
    }
    private void add(Context context)throws IOException {
        String key=key(context.session,context.id);
        if(seen.contains(key))throw problem("cdp_world_context_reused");
        if(contexts.size()>=ACTIVE_LIMIT||seen.size()>=HISTORY_LIMIT)throw problem("cdp_world_context_limit");
        if(context.unique!=null)for(Context other:contexts.values())if(context.unique.equals(other.unique))throw problem("cdp_world_context_ambiguous");
        seen.add(key);contexts.put(key,context);
    }
    void event(String method,String source,JSONObject params)throws IOException {
        try{
            if("Runtime.executionContextCreated".equals(method)){
                JSONObject value=params.getJSONObject("context"),aux=value.optJSONObject("auxData");int id=number(value.opt("id"));
                Name name=names.get(value.opt("name"));String unique=identity(value.opt("uniqueId"))?(String)value.opt("uniqueId"):null;
                String raw=aux!=null&&aux.opt("frameId") instanceof String&&MdoBrowserCdpJson.token((String)aux.opt("frameId"))?(String)aux.opt("frameId"):null;
                if(name!=null&&(unique==null||raw==null||!Boolean.FALSE.equals(aux.opt("isDefault"))||!"isolated".equals(aux.opt("type"))||
                    !name.raw.equals(raw)||!Objects.equals(name.session,source)))throw problem("cdp_world_context_unsupported");
                add(new Context(id,source,unique,raw,name));return;
            }
            if("Runtime.executionContextDestroyed".equals(method)){
                Context gone=contexts.remove(key(source,number(params.opt("executionContextId"))));
                if(gone!=null&&gone.name!=null&&gone.admittedDocument!=null)frames.worldLost(gone.frame,source,gone.name.loader,gone.admittedDocument);prune();return;
            }
            if("Runtime.executionContextsCleared".equals(method)){
                for(Context context:new ArrayList<>(contexts.values()))if(Objects.equals(source,context.session)){
                    contexts.remove(key(context.session,context.id));if(context.name!=null&&context.admittedDocument!=null)frames.worldLost(context.frame,source,context.name.loader,context.admittedDocument);
                }qualified.remove(scope(source));prune();
            }
        }catch(JSONException error){throw problem("cdp_world_metadata_invalid");}
    }
    void retire(String source){
        contexts.entrySet().removeIf(row->Objects.equals(source,row.getValue().session));qualified.remove(scope(source));prune();
    }
    private boolean active(World world){
        if(world==null||contexts.get(key(world.context.session,world.context.id))!=world.context)return false;
        try{frames.validate(world.route);return true;}catch(IOException failure){return false;}
    }
    private void prune(){worlds.entrySet().removeIf(row->!active(row.getValue()));}
    boolean ready(String frame,String document){World world=worlds.get(frame);return world!=null&&world.route.document.equals(document)&&active(world);}
    private void guard(MdoBrowserCdpFrames.Route route,Context context)throws IOException {
        frames.validate(route);if(context!=null&&contexts.get(key(context.session,context.id))!=context)throw problem("cdp_world_context_changed");
    }
    private JSONObject call(MdoBrowserCdpFrames.Route route,Context context,String method,JSONObject params,boolean effect,MdoBrowserCdpWorker.Deadline deadline)throws IOException {
        guard(route,context);JSONObject reply=binding.call(method,params,route.session,Math.min(5000,deadline.remaining()),effect);guard(route,context);return reply;
    }
    private JSONObject evaluation(String expression,String unique,boolean readOnly,MdoBrowserCdpWorker.Deadline deadline)throws JSONException,IOException {
        return new JSONObject().put("expression",expression).put("uniqueContextId",unique).put("returnByValue",true).put("silent",true)
            .put("awaitPromise",false).put("throwOnSideEffect",readOnly).put("timeout",Math.min(5000,deadline.remaining()));
    }
    void prepare(MdoBrowserCdpFrames.Route route,MdoBrowserCdpWorker.Deadline deadline)throws IOException {
        guard(route,null);prune();World existing=worlds.get(route.frame);if(active(existing))return;
        if(worlds.size()>=WORLD_LIMIT)throw problem("cdp_world_limit");
        try{
            String nativeProof=scope(route.session)+"\u0000"+route.raw+"\u0000"+route.loader;Name name=namesByProof.get(nativeProof);
            if(name==null){if(!binding.reserveWorldName())throw problem("cdp_world_document_limit");
                name=new Name(binding.worldName+"."+java.util.UUID.randomUUID(),route);names.put(name.value,name);namesByProof.put(nativeProof,name);}
            JSONObject reply=call(route,null,"Page.createIsolatedWorld",new JSONObject().put("frameId",route.raw).put("worldName",name.value)
                .put("grantUniveralAccess",false),false,deadline);int id=number(reply.opt("executionContextId"));
            Context context=contexts.get(key(route.session,id));long started=System.nanoTime();
            while(context==null&&(System.nanoTime()-started)/1000000L<500){
                guard(route,null);binding.poll(Math.min(20,deadline.remaining()));context=contexts.get(key(route.session,id));
            }
            if(context==null||context.name!=name||!route.raw.equals(context.frame)||!Objects.equals(context.session,route.session))throw problem("cdp_world_context_unsupported");
            guard(route,context);
            if(!qualified.contains(scope(route.session))){
                boolean rejected=false;
                try{call(route,context,"Runtime.evaluate",evaluation("void 0","",true,deadline),false,deadline);}
                catch(MdoBrowserCdpSession.Failure failure){if(failure.uniqueContextRejected)rejected=true;else throw failure;}
                if(!rejected)throw problem("cdp_world_unique_context_unsupported");qualified.add(scope(route.session));
            }
            JSONObject proof=call(route,context,"Runtime.evaluate",evaluation("void 0",context.unique,true,deadline),false,deadline);
            JSONObject value=proof.optJSONObject("result");
            if(proof.has("exceptionDetails")||value==null||!"undefined".equals(value.opt("type")))throw problem("cdp_world_context_unsupported");
            guard(route,context);context.admittedDocument=route.document;worlds.put(route.frame,new World(route,context));
        }catch(JSONException error){throw problem("cdp_world_metadata_invalid");}
    }
    JSONObject evaluate(MdoBrowserCdpFrames.Route route,String expression,boolean effect,MdoBrowserCdpWorker.Deadline deadline)throws IOException {
        if(expression==null||expression.isEmpty()||expression.length()>SCRIPT_LIMIT||expression.getBytes(StandardCharsets.UTF_8).length>SCRIPT_LIMIT)
            throw problem("cdp_world_script_invalid");
        prepare(route,deadline);World world=worlds.get(route.frame);if(!active(world))throw problem("cdp_world_context_changed");
        try{
            // DOM readers necessarily invoke DOM accessors; throwOnSideEffect
            // is used for the fixed capability probes only, never as a policy.
            JSONObject reply=call(route,world.context,"Runtime.evaluate",evaluation(expression,world.context.unique,false,deadline),effect,deadline);
            JSONObject remote=reply.optJSONObject("result");
            if(reply.has("exceptionDetails")||remote==null||!remote.has("value")||remote.has("unserializableValue"))throw problem("cdp_world_evaluation_failed");
            Object value=remote.get("value"),type=remote.opt("type");boolean valid=
                "object".equals(type)&&(value==JSONObject.NULL||value instanceof JSONObject||value instanceof org.json.JSONArray)||
                "string".equals(type)&&value instanceof String||"boolean".equals(type)&&value instanceof Boolean||
                "number".equals(type)&&value instanceof Number&&Double.isFinite(((Number)value).doubleValue());
            if(!valid)throw problem("cdp_world_result_invalid");
            JSONObject result=new JSONObject().put("ok",true).put("frame",route.frame).put("frame_document",route.document).put("value",value);
            if(result.toString().getBytes(StandardCharsets.UTF_8).length>RESULT_LIMIT)throw problem("cdp_world_result_limit");guard(route,world.context);return result;
        }catch(JSONException error){throw problem("cdp_world_result_invalid");}
    }
}
