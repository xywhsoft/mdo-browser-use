package org.xserver.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Physical-target network ancestry across document navigations. This ledger
 * grants response provenance only, never an execution world or input route. */
final class MdoBrowserCdpNetworkFrames implements MdoBrowserCdpSession.Events,AutoCloseable {
    private static final int LIMIT=32,DEPTH=32,LIFETIME=4096;
    /** Private bounded root commit history, not a public wire-ID lookup. */
    private static final class Commit {
        final String loader;final long revision;final Origin origin;final byte[] destination;
        Commit(String loader,long revision,Origin origin){this(loader,revision,origin,null);}
        Commit(String loader,long revision,Origin origin,byte[] destination){this.loader=loader;this.revision=revision;this.origin=origin;this.destination=destination;}
    }
    private final Commit[] commits=new Commit[LIMIT];private int commitCursor;
    private static final class Scope {volatile boolean active=true;}
    static final class Origin {
        final String frame,document;final boolean root;private final Scope[] ancestry;
        private Origin(Frame node,Scope[] ancestry,boolean root){frame=node.alias;document=node.document;this.ancestry=ancestry;this.root=root;}
        boolean current(){for(Scope scope:ancestry)if(!scope.active)return false;return true;}
    }
    private static final class Frame {
        final String raw,parent,alias="nf-"+UUID.randomUUID();String loader,document="nd-"+UUID.randomUUID();Scope scope=new Scope(),position=new Scope();boolean swapped;
        Frame(String raw,String parent,String loader){this.raw=raw;this.parent=parent;this.loader=loader;}
        void revoke(){scope.active=false;position.active=false;}
        void move(){position.active=false;position=new Scope();}
        void navigate(String loader){revoke();scope=new Scope();position=new Scope();document="nd-"+UUID.randomUUID();this.loader=loader;swapped=false;}
    }
    private final Thread worker=Thread.currentThread();private final String root,initialLoader;
    private final Map<String,Frame> frames=new LinkedHashMap<>();private final Scope authority=new Scope();private boolean ready;private volatile boolean closed;private long revision;private int issued;
    MdoBrowserCdpNetworkFrames(String root,String loader)throws IOException {
        if(!identity(root)||!loader(loader))throw fail("download_frame_metadata_invalid");
        this.root=root;initialLoader=loader;frames.put(root,new Frame(root,null,loader));issued=1;
    }
    private static IOException fail(String code){return new IOException(code);}
    static boolean identity(Object value){
        if(!(value instanceof String)||((String)value).isEmpty()||((String)value).length()>128)return false;
        for(int i=0;i<((String)value).length();i++){char c=((String)value).charAt(i);
            if(!(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||"_.:-".indexOf(c)>=0))return false;}
        return true;
    }
    private static boolean loader(Object value){return value instanceof String&&(((String)value).isEmpty()||identity(value));}
    private static String id(JSONObject value,String key)throws IOException {Object raw=value.opt(key);if(!identity(raw))throw fail("download_frame_metadata_invalid");return(String)raw;}
    private void current()throws IOException {if(Thread.currentThread()!=worker)throw fail("cdp_wrong_worker");if(closed)throw fail("download_frame_owner_closed");}
    long revision()throws IOException {current();return revision;}
    String rootLoader()throws IOException {origin(root);return frames.get(root).loader;}
    private void retain(Commit commit){
        Commit old=commits[commitCursor];if(old!=null&&old.destination!=null)java.util.Arrays.fill(old.destination,(byte)0);
        commits[commitCursor]=commit;commitCursor=(commitCursor+1)%LIMIT;
    }
    private void collect(JSONObject tree,String parent,int depth,Map<String,Frame> found)throws IOException,JSONException {
        if(depth>=DEPTH||found.size()>=LIMIT)throw fail("download_frame_limit");
        JSONObject value=tree.getJSONObject("frame");String raw=id(value,"id");Object load=value.opt("loaderId");
        if(!loader(load)||found.containsKey(raw))throw fail("download_frame_metadata_invalid");
        if(value.has("parentId")&&!java.util.Objects.equals(parent,id(value,"parentId")))throw fail("download_frame_parent_unproven");
        if(parent==null&&(!root.equals(raw)||!initialLoader.equals(load)))throw fail("download_frame_root_changed");
        found.put(raw,new Frame(raw,parent,(String)load));
        JSONArray children=tree.optJSONArray("childFrames");if(tree.has("childFrames")&&children==null)throw fail("download_frame_metadata_invalid");
        if(children!=null){if(children.length()>LIMIT)throw fail("download_frame_limit");for(int i=0;i<children.length();i++)collect(children.getJSONObject(i),raw,depth+1,found);}
    }
    void initialize(JSONObject tree,long before)throws IOException {
        current();if(ready)throw fail("download_frame_bootstrap_replayed");if(before!=revision)throw fail("download_frame_bootstrap_changed");
        Map<String,Frame> found=new LinkedHashMap<>();
        try{collect(tree,null,0,found);}catch(JSONException failure){throw fail("download_frame_metadata_invalid");}
        if(issued> LIFETIME-found.size())throw fail("download_frame_lifetime_limit");
        for(Frame frame:frames.values())frame.revoke();frames.clear();frames.putAll(found);issued+=found.size();ready=true;revision++;
    }
    private void descendants(String raw,boolean include){
        ArrayList<String> keys=new ArrayList<>();keys.add(raw);
        for(int i=0;i<keys.size();i++)for(Frame node:frames.values())if(java.util.Objects.equals(node.parent,keys.get(i))&&!keys.contains(node.raw))keys.add(node.raw);
        for(String key:keys)if(include||!key.equals(raw)){Frame removed=frames.remove(key);if(removed!=null)removed.revoke();}
    }
    private Frame attach(String raw,String parent,String load)throws IOException {
        if(raw.equals(root)||raw.equals(parent))throw fail("download_frame_parent_unproven");
        Frame ancestor=frames.get(parent);if(ancestor==null||ancestor.swapped)throw fail("download_frame_parent_unproven");
        int depth=1;for(Frame at=ancestor;at!=null;at=frames.get(at.parent)){
            if(at.raw.equals(raw)||++depth>DEPTH)throw fail("download_frame_parent_unproven");
        }
        Frame existing=frames.get(raw);
        if(existing!=null){if(!parent.equals(existing.parent))throw fail("download_frame_parent_unproven");return existing;}
        if(frames.size()>=LIMIT)throw fail("download_frame_limit");if(issued>=LIFETIME)throw fail("download_frame_lifetime_limit");
        Frame value=new Frame(raw,parent,load);frames.put(raw,value);issued++;return value;
    }
    @Override public void event(String method,String child,JSONObject params)throws IOException {
        current();if(!method.equals("Page.frameAttached")&&!method.equals("Page.frameNavigated")&&!method.equals("Page.documentOpened")&&
            !method.equals("Page.navigatedWithinDocument")&&!method.equals("Page.frameDetached")&&!method.equals("Page.frameSubtreeWillBeDetached"))return;
        try{
            if(child!=null)throw fail("download_frame_session_unproven");
            if(method.equals("Page.navigatedWithinDocument")){
                String raw=id(params,"frameId");if(!raw.equals(root))return;
                Object url=params.opt("url"),type=params.opt("navigationType");
                if(!(url instanceof String)||((String)url).length()>8192||params.has("navigationType")&&
                   !("fragment".equals(type)||"historyApi".equals(type)||"other".equals(type)))throw fail("download_frame_metadata_invalid");
                Frame node=frames.get(root);node.move();revision++;
                if(ready&&MdoBrowserCdpSession.navigationUrl((String)url)){
                    Origin document=origin(root);Scope[] scopes=java.util.Arrays.copyOf(document.ancestry,document.ancestry.length+1);scopes[scopes.length-1]=node.position;
                    retain(new Commit(node.loader,revision,new Origin(node,scopes,true),MdoBrowserCdpSession.navigationDigest((String)url)));
                }return;
            }
            if(method.equals("Page.frameAttached")){String raw=id(params,"frameId"),parent=id(params,"parentFrameId");attach(raw,parent,"");revision++;return;}
            if(method.equals("Page.frameNavigated")||method.equals("Page.documentOpened")){
                JSONObject value=params.getJSONObject("frame");String raw=id(value,"id");Object load=value.opt("loaderId");
                if(!loader(load))throw fail("download_frame_metadata_invalid");Frame node;
                if(raw.equals(root)){if(value.has("parentId"))throw fail("download_frame_root_changed");node=frames.get(root);}
                else{Frame previous=frames.get(raw);String parent=value.has("parentId")?id(value,"parentId"):previous==null?null:previous.parent;
                    if(parent==null)throw fail("download_frame_parent_unproven");node=attach(raw,parent,(String)load);}
                descendants(raw,false);node.navigate((String)load);revision++;
                if(ready&&raw.equals(root)&&method.equals("Page.frameNavigated")){
                    retain(new Commit((String)load,revision,origin(root)));
                }return;
            }
            String raw=id(params,"frameId");if(raw.equals(root))throw fail("download_frame_root_changed");Frame node=frames.get(raw);if(node==null)return;
            String reason=method.equals("Page.frameSubtreeWillBeDetached")?"remove":params.optString("reason","remove");
            if(reason.equals("remove"))descendants(raw,true);
            else if(reason.equals("swap")){descendants(raw,false);node.revoke();node.swapped=true;}
            else throw fail("download_frame_metadata_invalid");revision++;
        }catch(JSONException failure){close();throw new MdoBrowserCdpSession.EventFailure("download_frame_metadata_invalid");}
        catch(IOException failure){close();if(MdoBrowserCdpSession.EventFailure.allowed(failure.getMessage()))throw new MdoBrowserCdpSession.EventFailure(failure.getMessage());throw failure;}
        catch(RuntimeException|Error failure){close();throw failure;}
    }
    Origin origin(String raw)throws IOException {
        current();if(!ready||raw==null)throw fail("download_frame_unproven");Frame selected=frames.get(raw);
        if(selected==null)throw fail("download_frame_unproven");ArrayList<Scope> chain=new ArrayList<>();chain.add(authority);
        for(Frame node=selected;node!=null&&chain.size()<=DEPTH;node=frames.get(node.parent)){
            if(node.swapped||!node.scope.active)throw fail("download_frame_unproven");chain.add(node.scope);
            if(node.raw.equals(root))return new Origin(selected,chain.toArray(new Scope[0]),selected.raw.equals(root));
        }throw fail("download_frame_parent_unproven");
    }
    Origin navigationOrigin(MdoBrowserCdpSession.Navigation navigation,long after)throws IOException {
        current();if(navigation==null||!ready)return null;
        for(int i=0;i<LIMIT;i++){
            Commit commit=commits[(commitCursor+LIMIT-1-i)%LIMIT];
            if(commit!=null&&commit.destination==null&&commit.revision>after&&navigation.matchesCommittedLoader(root,commit.loader))return commit.origin;
        }return null;
    }
    Origin sameDocumentOrigin(MdoBrowserCdpSession.Navigation navigation,long after,String loader,byte[] destination,Origin original)throws IOException {
        current();if(navigation==null||!ready||original==null||!original.current()||!navigation.matchesSameDocument(root))return null;
        for(int i=0;i<LIMIT;i++){
            Commit commit=commits[(commitCursor+LIMIT-1-i)%LIMIT];
            if(commit!=null&&commit.destination!=null&&commit.revision>after&&commit.loader.equals(loader)&&
               java.security.MessageDigest.isEqual(commit.destination,destination))return commit.origin;
        }return null;
    }
    /** Called after the physical native worker is drained, or on that worker.
     * Revoke immutable receipts even when the UI requests target teardown. */
    @Override public void close(){authority.active=false;closed=true;}
}
