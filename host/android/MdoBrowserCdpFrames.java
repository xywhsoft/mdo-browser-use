package org.xserver.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Worker-owned frame graph of one proven WebView. A renderer's tree omits
 * remote frames: recursively auto-attach only related iframe targets and join
 * their native parent/frame IDs. URLs/titles/opener hints grant no authority.
 * This layer performs metadata only; isolated-world execution is separate. */
final class MdoBrowserCdpFrames implements MdoBrowserCdpSession.Events {
    private static final int FRAME_LIMIT=32,SESSION_LIMIT=64,DEPTH_LIMIT=32;
    private static final class Source {
        final String session,target,parent;boolean armed,started,ready;
        final Map<String,Boolean> loading=new LinkedHashMap<>();
        Source(String session,String target,String parent){this.session=session;this.target=target;this.parent=parent;}
    }
    private static final class Node {
        final String raw,alias;String parent,loader,session,document;boolean loading,swapped;
        Node(String raw,String parent,String loader,String session,boolean root){this.raw=raw;this.parent=parent;this.loader=loader;this.session=session;
            alias=root?"f0":"f-"+UUID.randomUUID();document="fd-"+UUID.randomUUID();}
    }
    static final class Route {
        final String raw,frame,document,session;private final List<String> ancestors;
        Route(Node node,List<String> ancestors){raw=node.raw;frame=node.alias;document=node.document;session=node.session;this.ancestors=ancestors;}
    }
    private final MdoBrowserCdpBinding binding;private final Thread owner=Thread.currentThread();
    private final Map<String,Source> sources=new LinkedHashMap<>();private final Map<String,Node> nodes=new LinkedHashMap<>();
    private final Set<String> seenSessions=new LinkedHashSet<>();private long revision;private String fault;
    MdoBrowserCdpFrames(MdoBrowserCdpBinding binding)throws IOException {
        if(binding==null)throw problem("cdp_frame_arguments_invalid");this.binding=binding;
        sources.put(null,new Source(null,binding.rootFrame,null));
        nodes.put(binding.rootFrame,new Node(binding.rootFrame,null,binding.rootLoader,null,true));binding.observe(this);
    }
    private static IOException problem(String code){return new IOException(code);}
    private void current()throws IOException {
        if(Thread.currentThread()!=owner)throw problem("cdp_wrong_worker");if(fault!=null)throw problem(fault);binding.current();
    }
    private IOException invalid(String code){fault=code;return problem(code);}
    private static String id(JSONObject value,String field)throws IOException {
        Object text=value.opt(field);if(!(text instanceof String)||!MdoBrowserCdpJson.token((String)text))throw problem("cdp_frame_metadata_invalid");return(String)text;
    }
    private static String loader(JSONObject frame)throws IOException {
        Object value=frame.opt("loaderId");if(!(value instanceof String)||((String)value).length()>128)throw problem("cdp_frame_metadata_invalid");
        String text=(String)value;for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(!((c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||"_.:-".indexOf(c)>=0))throw problem("cdp_frame_metadata_invalid");}
        return text;
    }
    private Source source(String session)throws IOException {Source source=sources.get(session);if(source==null)throw invalid("cdp_frame_source_invalid");return source;}
    private boolean within(Node node,Source source){
        for(int depth=0;node!=null&&depth<DEPTH_LIMIT;depth++){
            if(!Objects.equals(node.session,source.session))return false;
            if(node.raw.equals(source.target))return true;node=nodes.get(node.parent);
        }return false;
    }
    private Node upsert(String raw,String parent,String load,String session,boolean committed)throws IOException {
        Node node=nodes.get(raw);
        if(node==null){if(nodes.size()>=FRAME_LIMIT)throw invalid("cdp_frame_limit");node=new Node(raw,parent,load,session,raw.equals(binding.rootFrame));nodes.put(raw,node);revision++;}
        else if(committed||!Objects.equals(parent,node.parent)||!Objects.equals(session,node.session)||load.isEmpty()||!load.equals(node.loader)){
            node.document="fd-"+UUID.randomUUID();node.parent=parent;node.loader=load;node.session=session;node.swapped=false;revision++;
        }
        return node;
    }
    private void remove(String raw,boolean includeRoot)throws IOException {
        if(raw.equals(binding.rootFrame)&&includeRoot)throw invalid("cdp_frame_root_changed");
        List<String> remove=new ArrayList<>();remove.add(raw);
        for(int i=0;i<remove.size();i++)for(Node node:nodes.values())if(Objects.equals(node.parent,remove.get(i))&&!remove.contains(node.raw))remove.add(node.raw);
        for(String key:remove)if(includeRoot||!key.equals(raw)){nodes.remove(key);for(Source source:sources.values())source.loading.remove(key);}revision++;
    }
    @Override public void event(String method,String session,JSONObject params)throws IOException {
        current();Source source=source(session);
        try{
            if("Target.attachedToTarget".equals(method)){
                String child=id(params,"sessionId");JSONObject target=params.getJSONObject("targetInfo");String raw=id(target,"targetId");
                if(!source.armed||!"iframe".equals(target.opt("type"))||!Boolean.FALSE.equals(params.opt("waitingForDebugger"))||raw.equals(binding.rootFrame))
                    throw invalid("cdp_frame_attachment_invalid");
                if(seenSessions.contains(child)||sources.size()>=SESSION_LIMIT||seenSessions.size()>=256)throw invalid("cdp_frame_session_limit");
                for(Source row:sources.values())if(raw.equals(row.target))throw invalid("cdp_frame_session_conflict");
                // Related-target events arrive only on a source we armed. Admit
                // metadata transport now; commands wait for a native tree join.
                binding.admitChild(child);seenSessions.add(child);sources.put(child,new Source(child,raw,session));revision++;return;
            }
            if("Target.detachedFromTarget".equals(method)){
                String child=id(params,"sessionId");Source gone=sources.get(child);
                if(gone==null){if(seenSessions.contains(child))return;throw invalid("cdp_frame_session_invalid");}
                if(child.equals(session)||!Objects.equals(gone.parent,session))throw invalid("cdp_frame_session_invalid");
                List<String> retired=new ArrayList<>();retired.add(child);
                for(int i=0;i<retired.size();i++)for(Source row:sources.values())if(Objects.equals(row.parent,retired.get(i))&&!retired.contains(row.session))retired.add(row.session);
                for(String key:retired){Source row=sources.remove(key);binding.retireChild(key);Node node=nodes.get(row.target);if(node!=null){remove(node.raw,true);}}
                revision++;return;
            }
            if(!method.startsWith("Page.frame")&&!"Page.documentOpened".equals(method))return;
            JSONObject frame=params.optJSONObject("frame");String raw=frame==null?id(params,"frameId"):id(frame,"id");Node node=nodes.get(raw);
            if("Page.frameAttached".equals(method)){
                String parent=id(params,"parentFrameId");Node parentNode=nodes.get(parent);
                if(raw.equals(binding.rootFrame)||raw.equals(parent))throw invalid("cdp_frame_tree_invalid");
                if(parentNode==null){source.ready=false;revision++;return;}
                if(!within(parentNode,source))throw invalid("cdp_frame_parent_unproven");
                for(Node at=parentNode;at!=null;at=nodes.get(at.parent))if(at.raw.equals(raw))throw invalid("cdp_frame_tree_invalid");
                if(node!=null&&!Objects.equals(node.session,session))throw invalid("cdp_frame_source_invalid");
                upsert(raw,parent,"",session,false).loading=true;return;
            }
            if("Page.frameStartedLoading".equals(method)||"Page.frameStoppedLoading".equals(method)){
                if(!source.loading.containsKey(raw)&&source.loading.size()>=FRAME_LIMIT)throw invalid("cdp_frame_limit");
                source.loading.put(raw,"Page.frameStartedLoading".equals(method));
            }
            if(node==null){source.ready=false;revision++;return;} // No alias from an unjoined/late event.
            if(!Objects.equals(node.session,session)){
                // A parent renderer may report its own remote child detaching
                // during a process swap; it cannot mutate the child's DOM.
                if(!("Page.frameDetached".equals(method)||"Page.frameSubtreeWillBeDetached".equals(method))||!Objects.equals(source(node.session).parent,session))throw invalid("cdp_frame_source_invalid");
            }
            if("Page.frameSubtreeWillBeDetached".equals(method)){remove(raw,true);return;}
            if("Page.frameDetached".equals(method)){
                String reason=params.getString("reason");if("remove".equals(reason)){remove(raw,true);return;}
                if(!"swap".equals(reason)||raw.equals(binding.rootFrame))throw invalid("cdp_frame_detach_invalid");
                remove(raw,false);node.document="fd-"+UUID.randomUUID();node.loading=true;node.swapped=true;revision++;return;
            }
            if("Page.frameNavigated".equals(method)||"Page.documentOpened".equals(method)){
                if(raw.equals(binding.rootFrame))throw invalid("cdp_frame_root_changed");
                String parent=frame.has("parentId")?id(frame,"parentId"):node.parent;
                Node parentNode=nodes.get(parent);Source parentSource=raw.equals(source.target)?source(source.parent):source;
                if(parentNode==null||!within(parentNode,parentSource)||raw.equals(parent))throw invalid("cdp_frame_parent_unproven");
                remove(raw,false);node=upsert(raw,parent,loader(frame),session,true);node.loading=true;source.loading.put(raw,true);return;
            }
            if("Page.frameStartedLoading".equals(method)){if(!node.loading){node.loading=true;revision++;}}
            else if("Page.frameStoppedLoading".equals(method)){if(node.loading){node.loading=false;revision++;}}
        }catch(JSONException error){throw invalid("cdp_frame_metadata_invalid");}
    }
    private JSONObject call(Source source,String method,JSONObject params,MdoBrowserCdpWorker.Deadline deadline)throws IOException {
        current();if(sources.get(source.session)!=source)throw problem("cdp_frame_changed");
        JSONObject reply=binding.call(method,params,source.session,Math.min(5000,deadline.remaining()),false);
        current();if(sources.get(source.session)!=source)throw problem("cdp_frame_changed");return reply;
    }
    private void collect(Source source,JSONObject tree,Set<String> found,int depth,String expectedParent)throws IOException,JSONException {
        if(depth>=DEPTH_LIMIT)throw invalid("cdp_frame_depth_limit");JSONObject frame=tree.getJSONObject("frame");String raw=id(frame,"id"),load=loader(frame);
        if(!found.add(raw))throw invalid("cdp_frame_tree_invalid");
        String parent=frame.has("parentId")?id(frame,"parentId"):expectedParent;
        if(depth==0){
            if(!raw.equals(source.target))throw invalid("cdp_frame_target_mismatch");
            if(source.session==null){if(parent!=null||!load.equals(binding.rootLoader))throw invalid("cdp_frame_root_changed");}
            else {if(parent==null){Node old=nodes.get(raw);parent=old==null?null:old.parent;}
                Node ancestor=nodes.get(parent);if(ancestor==null||!within(ancestor,source(source.parent)))throw invalid("cdp_frame_parent_unproven");}
        }else if(!Objects.equals(parent,expectedParent))throw invalid("cdp_frame_tree_invalid");
        Node current=nodes.get(raw);
        if(current!=null&&!Objects.equals(current.session,source.session)&&!(depth==0&&source.session!=null&&Objects.equals(current.session,source.parent)))
            throw invalid("cdp_frame_source_conflict");
        Node node=upsert(raw,parent,load,source.session,false);node.swapped=false;
        if(source.loading.containsKey(raw))node.loading=source.loading.get(raw);
        JSONArray children=tree.optJSONArray("childFrames");if(tree.has("childFrames")&&children==null)throw invalid("cdp_frame_tree_invalid");
        if(children!=null){if(children.length()>FRAME_LIMIT)throw invalid("cdp_frame_limit");for(int i=0;i<children.length();i++)collect(source,children.getJSONObject(i),found,depth+1,raw);}
    }
    /** Read-only bootstrap/refresh. Every OOPIF tree must join a native parent
     * inside the exact auto-attachment source. No guessed/missing ancestry. */
    JSONObject refresh(MdoBrowserCdpWorker.Deadline deadline)throws IOException {
        current();try{
            for(int round=0;round<=SESSION_LIMIT;round++){
                for(Source source:new ArrayList<>(sources.values()))if(!source.started){
                    call(source,"Page.enable",new JSONObject(),deadline);
                    if(source.session!=null)call(source,"Runtime.enable",new JSONObject(),deadline);
                    source.armed=true;
                    call(source,"Target.setAutoAttach",new JSONObject().put("autoAttach",true).put("waitForDebuggerOnStart",false).put("flatten",true)
                        .put("filter",new JSONArray().put(new JSONObject().put("type","iframe"))),deadline);source.started=true;
                }
                for(Source source:new ArrayList<>(sources.values()))if(source.started){
                    long before=revision;source.ready=false;
                    JSONObject tree=call(source,"Page.getFrameTree",new JSONObject(),deadline).getJSONObject("frameTree");Set<String> found=new LinkedHashSet<>();
                    if(revision!=before)continue; // Discard a raced read; no effects are replayed.
                    collect(source,tree,found,0,null);
                    for(Node node:new ArrayList<>(nodes.values()))if(Objects.equals(node.session,source.session)&&!found.contains(node.raw))remove(node.raw,true);
                    source.loading.keySet().retainAll(found);
                    source.ready=true;
                }
                if(sources.values().stream().allMatch(source->source.started&&source.ready))return snapshot();
            }
            throw problem("cdp_frame_tree_busy");
        }catch(JSONException error){throw invalid("cdp_frame_metadata_invalid");}
    }
    private boolean attached(Node node,boolean requireReady){
        Set<String> seen=new LinkedHashSet<>();
        for(int depth=0;node!=null&&depth<DEPTH_LIMIT;depth++){
            Source source=sources.get(node.session);
            if(requireReady&&(source==null||!source.ready||node.loading||node.swapped||node.loader.isEmpty()))return false;
            if(!seen.add(node.raw))return false;if(node.raw.equals(binding.rootFrame))return node.parent==null&&node.session==null;
            node=nodes.get(node.parent);
        }return false;
    }
    private JSONObject snapshot()throws IOException,JSONException {
        current();JSONArray frames=new JSONArray();boolean complete=true;
        for(Node node:nodes.values()){
            Source source=sources.get(node.session);Node parent=nodes.get(node.parent);
            boolean linked=attached(node,false)&&source!=null&&source.ready&&!node.swapped;if(!linked)complete=false;
            frames.put(new JSONObject().put("id",node.alias).put("document",node.document).put("parent",parent==null?JSONObject.NULL:parent.alias)
                .put("loading",!linked||!attached(node,true)).put("isolated",false));
        }
        return new JSONObject().put("ok",true).put("complete",complete).put("frames",frames).put("generation",revision);
    }
    Route select(String frame,String document)throws IOException {
        current();Node selected=null;for(Node node:nodes.values())if(node.alias.equals(frame)&&node.document.equals(document))selected=node;
        if(selected==null)throw problem("cdp_frame_stale");List<String> chain=new ArrayList<>();Node node=selected;
        for(int depth=0;node!=null&&depth<DEPTH_LIMIT;depth++){
            Source source=sources.get(node.session);if(source==null||!source.ready||node.loading||node.swapped||node.loader.isEmpty())throw problem("cdp_frame_not_ready");
            chain.add(node.alias);chain.add(node.document);if(node.raw.equals(binding.rootFrame))return new Route(selected,chain);node=nodes.get(node.parent);
        }throw problem("cdp_frame_parent_unproven");
    }
    void validate(Route route)throws IOException {
        current();if(route==null)throw problem("cdp_frame_arguments_invalid");Route now=select(route.frame,route.document);
        if(!now.raw.equals(route.raw)||!Objects.equals(now.session,route.session)||!now.ancestors.equals(route.ancestors))throw problem("cdp_frame_stale");
    }
}
