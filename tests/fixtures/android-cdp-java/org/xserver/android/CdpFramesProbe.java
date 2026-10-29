package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual worker/binding/session/frame graph/strict JNI with synthetic related
 * renderer targets. No installed Android provider/frame behavior is claimed. */
public final class CdpFramesProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean value){if(!value)throw new AssertionError("Frames check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Checked {void run()throws Exception;}
    interface Operation {JSONObject run(MdoBrowserCdpBinding binding,MdoBrowserCdpWorker.Deadline deadline)throws Exception;}
    private static void rejects(String code,Checked operation)throws Exception {
        try{operation.run();throw new AssertionError("Missing frame rejection");}catch(IOException error){check(code.equals(error.getMessage()));}
    }
    private static JSONObject frame(String id,String parent,String loader)throws Exception {
        JSONObject frame=new JSONObject().put("id",id).put("loaderId",loader);if(parent!=null)frame.put("parentId",parent);
        return new JSONObject().put("frame",frame);
    }
    private static JSONObject event(String method,String session,JSONObject params)throws Exception {
        JSONObject packet=new JSONObject().put("method",method).put("params",params);if(session!=null)packet.put("sessionId",session);return packet;
    }
    private static JSONObject attached(String parent,String child,String target)throws Exception {
        return event("Target.attachedToTarget",parent,new JSONObject().put("sessionId",child).put("waitingForDebugger",false)
            .put("targetInfo",new JSONObject().put("type","iframe").put("targetId",target)));
    }
    static final class Plan implements CdpBindingProbe.Factory.Extra {
        CdpBindingProbe.Factory.Extra extra;
        final Set<String> armed=new HashSet<>();final ArrayDeque<JSONObject> pending=new ArrayDeque<>();final List<String> methods=new ArrayList<>();
        String childLoader="child-load",leafParent="child-local",attachType="iframe";
        boolean badChildRoot,badParent,duplicateTree,rootWithParent,includeLocal=true,extraSource,omitChild,navigateDuringSnapshot,detachDuringSnapshot,cycle;
        int extraFrames,extraSessions,depth;int snapshotCount;boolean treeRaced;
        JSONObject tree(String source)throws Exception {
            if(source==null){
                JSONObject tree=frame("frame-owned",rootWithParent?"foreign":null,"load-owned");JSONArray children=new JSONArray();
                if(includeLocal)children.put(frame("root-local","frame-owned","local-load"));
                for(int i=0;i<extraFrames;i++)children.put(frame("extra-"+i,"frame-owned","extra-load"));
                if(duplicateTree)children.put(frame("root-local","frame-owned","local-load"));
                if(depth>0){JSONObject top=frame("deep-0","frame-owned","deep-load"),at=top;children.put(top);
                    for(int i=1;i<depth;i++){JSONObject next=frame("deep-"+i,"deep-"+(i-1),"deep-load");at.put("childFrames",new JSONArray().put(next));at=next;}}
                if(children.length()>0)tree.put("childFrames",children);return tree;
            }
            if(source.equals("child-session"))return frame(badChildRoot?"foreign-frame":"child-frame",badParent?"foreign-parent":"frame-owned",childLoader)
                .put("childFrames",new JSONArray().put(frame("child-local","child-frame","child-local-load")));
            if(source.equals("leaf-session"))return frame("leaf-frame",cycle?"leaf-frame":leafParent,"leaf-load");
            if(source.equals("extra-session"))return frame("root-local","frame-owned","extra-local-load");
            throw new AssertionError("Unknown synthetic source");
        }
        public boolean send(JSONObject request,CdpBindingProbe.Wire wire)throws Exception {
            if(extra!=null&&extra.send(request,wire))return true;
            String method=request.getString("method"),source=request.has("sessionId")?request.getString("sessionId"):null;
            JSONObject params=request.getJSONObject("params"),reply=new JSONObject();methods.add(method);
            if(method.equals("Page.enable")){}
            else if(method.equals("Runtime.enable")){
                check(source!=null); // The proven root Runtime domain is already enabled.
                wire.queue(event("Runtime.executionContextCreated",source,new JSONObject().put("context",new JSONObject().put("id",1)
                    .put("uniqueId","ctx-"+source).put("auxData",new JSONObject().put("isDefault",true).put("frameId",source.equals("child-session")?"child-frame":source.equals("extra-session")?"root-local":"leaf-frame")))));
            }else if(method.equals("Target.setAutoAttach")){
                check(params.getBoolean("autoAttach")&&!params.getBoolean("waitForDebuggerOnStart")&&params.getBoolean("flatten"));
                check(params.getJSONArray("filter").length()==1&&"iframe".equals(params.getJSONArray("filter").getJSONObject(0).getString("type")));
                if(armed.add(source==null?"root":source)){
                    if(source==null&&!omitChild){JSONObject child=attached(null,"child-session","child-frame");
                        child.getJSONObject("params").getJSONObject("targetInfo").put("type",attachType);wire.queue(child);}
                    if("child-session".equals(source))wire.queue(attached(source,"leaf-session","leaf-frame"));
                    if(source==null&&extraSource)wire.queue(attached(null,"extra-session","root-local"));
                    if(source==null)for(int i=0;i<extraSessions;i++)wire.queue(attached(null,"overflow-session-"+i,"overflow-target-"+i));
                }
            }else if(method.equals("Page.getFrameTree")){
                snapshotCount++;
                while(!pending.isEmpty())wire.queue(pending.removeFirst());
                if(source==null&&navigateDuringSnapshot&&!treeRaced){treeRaced=true;wire.queue(event("Page.frameStartedLoading",null,new JSONObject().put("frameId","root-local")));}
                if("child-session".equals(source)&&detachDuringSnapshot){wire.queue(event("Target.detachedFromTarget",null,new JSONObject().put("sessionId","child-session")));}
                reply.put("frameTree",tree(source));
            }else throw new AssertionError("Frame graph attempted non-metadata protocol work");
            JSONObject result=new JSONObject().put("id",request.getInt("id")).put("result",reply);if(source!=null)result.put("sessionId",source);wire.queue(result);return true;
        }
    }
    static final class Case {
        boolean lastEffect;
        final WebView view=new WebView();final MdoBrowserCdpMarker marker=new MdoBrowserCdpView(view,v->v==view).marker();
        final Plan plan=new Plan();final List<CdpBindingProbe.Factory> peers=new ArrayList<>();
        final MdoBrowserCdpWorker worker=new MdoBrowserCdpWorker(new MdoBrowserCdpWorker.Ui(){
            final Handler handler=new Handler(Looper.getMainLooper());public boolean onThread(){return Looper.myLooper()==Looper.getMainLooper();}
            public boolean post(Runnable task){return handler.post(task);}public void remove(Runnable task){handler.removeCallbacks(task);}
        },(owned,budget)->{
            CdpBindingProbe.Factory peer=new CdpBindingProbe.Factory(view);peers.add(peer);
            MdoBrowserCdpBinding binding=MdoBrowserCdpBinding.bind(owned,budget,(method,session,params)->{},timeout->Arrays.asList("owned"),peer);
            plan.armed.clear();peer.extra=plan;return binding;
        },()->System.nanoTime()/1000000L);
        JSONObject run(Operation operation)throws Exception {
            return run(false,operation);
        }
        JSONObject run(boolean effect,Operation operation)throws Exception {
            AtomicReference<JSONObject> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();AtomicReference<Throwable> defect=new AtomicReference<>();
            worker.submit(marker,12000,effect,(binding,deadline)->{
                try{return operation.run(binding,deadline).toString().getBytes(StandardCharsets.UTF_8);}
                catch(IOException failure){throw failure;}catch(Throwable failure){defect.set(failure);throw new IOException("cdp_fixture_failed");}
            },value->{lastEffect=value.effectMayHaveOccurred;if(value.code!=null)error.set(value.code);else try{result.set(new JSONObject(new String(value.bytes,StandardCharsets.UTF_8)));}catch(Exception failure){defect.set(failure);}});
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(result.get()==null&&error.get()==null&&defect.get()==null&&System.nanoTime()<end){Handler.drain();Thread.sleep(1);}
            if(defect.get()!=null)throw new AssertionError(defect.get());if(error.get()!=null)throw new IOException(error.get());check(result.get()!=null);return result.get();
        }
        void close()throws Exception {
            worker.close();marker.close();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(!worker.terminated()&&System.nanoTime()<end){Handler.drain();Thread.sleep(1);}check(worker.terminated());Handler.drain();for(CdpBindingProbe.Factory peer:peers)peer.balanced();
        }
    }
    private static JSONObject byParent(JSONObject snapshot,String parent)throws Exception {
        JSONArray rows=snapshot.getJSONArray("frames");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(parent==null?row.isNull("parent"):parent.equals(row.opt("parent")))return row;}
        throw new AssertionError("Missing public parent");
    }
    private static void nested()throws Exception {
        Case sample=new Case();try{
            sample.run((binding,deadline)->{
                MdoBrowserCdpFrames frames=binding.frames();check(frames==binding.frames());JSONObject inventory=frames.refresh(deadline);
                check(inventory.getBoolean("ok")&&inventory.getBoolean("complete")&&inventory.getJSONArray("frames").length()==5);
                JSONObject root=byParent(inventory,null);check(root.getString("id").equals("f0"));
                for(int i=0;i<inventory.getJSONArray("frames").length();i++){
                    JSONObject row=inventory.getJSONArray("frames").getJSONObject(i);check(!row.getBoolean("isolated"));
                    check(row.getString("document").matches("fd-[a-f0-9-]{36}"));
                    MdoBrowserCdpFrames.Route route=frames.select(row.getString("id"),row.getString("document"));frames.validate(route);
                }
                String publicResult=inventory.toString();check(!publicResult.contains("frame-owned")&&!publicResult.contains("child-session")&&!publicResult.contains("child-load"));
                check(!sample.plan.methods.contains("Target.getTargets")&&!sample.plan.methods.contains("Target.attachToTarget")&&!sample.plan.methods.contains("Runtime.evaluate"));
                return inventory;
            });check(sample.peers.size()==1);
        }finally{sample.close();}
    }
    private static void lifecycle()throws Exception {
        Case sample=new Case();try{
            sample.run((binding,deadline)->{
                MdoBrowserCdpFrames frames=binding.frames();JSONObject first=frames.refresh(deadline);JSONArray rows=first.getJSONArray("frames");
                MdoBrowserCdpFrames.Route child=null,leaf=null,local=null;
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);MdoBrowserCdpFrames.Route route=frames.select(row.getString("id"),row.getString("document"));
                    if(route.raw.equals("child-frame"))child=route;if(route.raw.equals("leaf-frame"))leaf=route;if(route.raw.equals("root-local"))local=route;
                }
                check(child!=null&&leaf!=null&&local!=null);final MdoBrowserCdpFrames.Route pinnedChild=child,pinnedLeaf=leaf;
                sample.plan.pending.add(event("Page.frameStartedLoading","child-session",new JSONObject().put("frameId","child-frame")));
                binding.call("Page.getFrameTree",new JSONObject(),1000);rejects("cdp_frame_not_ready",()->frames.validate(pinnedChild));
                sample.plan.pending.add(event("Page.frameStoppedLoading","child-session",new JSONObject().put("frameId","child-frame")));
                binding.call("Page.getFrameTree",new JSONObject(),1000);frames.refresh(deadline);frames.validate(child);frames.validate(leaf);
                sample.plan.childLoader="child-load-next";
                sample.plan.pending.add(event("Page.frameNavigated","child-session",new JSONObject().put("frame",sample.plan.tree("child-session").getJSONObject("frame"))));
                sample.plan.pending.add(event("Page.frameStoppedLoading","child-session",new JSONObject().put("frameId","child-frame")));
                binding.call("Page.getFrameTree",new JSONObject(),1000);rejects("cdp_frame_stale",()->frames.validate(pinnedChild));rejects("cdp_frame_stale",()->frames.validate(pinnedLeaf));
                frames.refresh(deadline);frames.validate(local);
                rejects("cdp_frame_stale",()->frames.select("child-frame",pinnedChild.document));
                return frames.refresh(deadline);
            });
        }finally{sample.close();}
    }
    private static void faults()throws Exception {
        for(int mode=0;mode<7;mode++){
            Case sample=new Case();sample.plan.badChildRoot=mode==0;sample.plan.badParent=mode==1;sample.plan.duplicateTree=mode==2;
            sample.plan.rootWithParent=mode==3;sample.plan.attachType=mode==4?"worker":"iframe";sample.plan.extraFrames=mode==5?33:0;sample.plan.cycle=mode==6;
            String expected=mode==0?"cdp_frame_target_mismatch":mode==1||mode==6?"cdp_frame_parent_unproven":mode==2?"cdp_frame_tree_invalid":mode==3?"cdp_frame_root_changed":mode==4?"cdp_event_failed":"cdp_frame_limit";
            try{rejects(expected,()->sample.run((binding,deadline)->binding.frames().refresh(deadline)));check(sample.peers.size()==1);}
            finally{sample.close();}
        }
        Case raced=new Case();raced.plan.navigateDuringSnapshot=true;
        try{raced.run((binding,deadline)->{JSONObject result=binding.frames().refresh(deadline);check(raced.plan.treeRaced&&raced.plan.snapshotCount>3);return result;});}finally{raced.close();}
        Case detached=new Case();detached.plan.detachDuringSnapshot=true;
        try{rejects("cdp_session_detached",()->detached.run((binding,deadline)->binding.frames().refresh(deadline)));check(detached.peers.size()==1);}finally{detached.close();}
    }
    private static void swapsAndRetirement()throws Exception {
        Case sample=new Case();try{
            sample.run((binding,deadline)->{
                MdoBrowserCdpFrames frames=binding.frames();JSONObject inventory=frames.refresh(deadline);MdoBrowserCdpFrames.Route original=null;
                for(int i=0;i<inventory.getJSONArray("frames").length();i++){
                    JSONObject row=inventory.getJSONArray("frames").getJSONObject(i);MdoBrowserCdpFrames.Route route=frames.select(row.getString("id"),row.getString("document"));
                    if(route.raw.equals("root-local"))original=route;
                }
                check(original!=null);final MdoBrowserCdpFrames.Route old=original;
                sample.plan.includeLocal=false;
                sample.plan.pending.add(event("Page.frameDetached",null,new JSONObject().put("frameId","root-local").put("reason","swap")));
                sample.plan.pending.add(attached(null,"extra-session","root-local"));
                binding.call("Page.getFrameTree",new JSONObject(),1000);rejects("cdp_frame_stale",()->frames.validate(old));
                inventory=frames.refresh(deadline);MdoBrowserCdpFrames.Route remote=null;
                for(int i=0;i<inventory.getJSONArray("frames").length();i++){
                    JSONObject row=inventory.getJSONArray("frames").getJSONObject(i);MdoBrowserCdpFrames.Route route=frames.select(row.getString("id"),row.getString("document"));
                    if(route.raw.equals("root-local"))remote=route;
                }
                check(remote!=null&&remote.session.equals("extra-session")&&!remote.document.equals(old.document));final MdoBrowserCdpFrames.Route previousRemote=remote;
                sample.plan.includeLocal=true;
                sample.plan.pending.add(event("Target.detachedFromTarget",null,new JSONObject().put("sessionId","extra-session")));
                // Retired-session traffic must be drained, never delivered to a
                // new local frame with the same native raw ID.
                sample.plan.pending.add(event("Page.frameStartedLoading","extra-session",new JSONObject().put("frameId","root-local")));
                sample.plan.pending.add(event("Page.frameAttached",null,new JSONObject().put("frameId","root-local").put("parentFrameId","frame-owned")));
                sample.plan.pending.add(event("Page.frameNavigated",null,new JSONObject().put("frame",frame("root-local","frame-owned","local-load").getJSONObject("frame"))));
                sample.plan.pending.add(event("Page.frameStoppedLoading",null,new JSONObject().put("frameId","root-local")));
                binding.call("Page.getFrameTree",new JSONObject(),1000);rejects("cdp_frame_stale",()->frames.validate(previousRemote));
                inventory=frames.refresh(deadline);check(inventory.getJSONArray("frames").length()==5);
                sample.plan.pending.add(event("Target.attachedToTarget",null,attached(null,"extra-session","root-local").getJSONObject("params")));
                rejects("cdp_event_failed",()->binding.call("Page.getFrameTree",new JSONObject(),1000));
                return new JSONObject().put("ok",true);
            });
        }finally{sample.close();}
    }
    private static void earlySubtreeAndThread()throws Exception {
        Case sample=new Case();AtomicReference<MdoBrowserCdpFrames> retained=new AtomicReference<>();try{
            sample.run((binding,deadline)->{
                MdoBrowserCdpFrames graph=binding.frames();retained.set(graph);JSONObject rows=graph.refresh(deadline);MdoBrowserCdpFrames.Route child=null;
                for(int i=0;i<rows.getJSONArray("frames").length();i++){
                    JSONObject row=rows.getJSONArray("frames").getJSONObject(i);MdoBrowserCdpFrames.Route route=graph.select(row.getString("id"),row.getString("document"));if(route.raw.equals("child-frame"))child=route;
                }
                check(child!=null);final MdoBrowserCdpFrames.Route old=child;
                sample.plan.pending.add(event("Page.frameSubtreeWillBeDetached","child-session",new JSONObject().put("frameId","child-frame")));
                binding.call("Page.getFrameTree",new JSONObject(),1000);rejects("cdp_frame_stale",()->graph.validate(old));
                return new JSONObject().put("ok",true);
            });
            rejects("cdp_wrong_worker",()->retained.get().select("f0","fd-not-owned"));
        }finally{sample.close();}
    }
    private static void limitsAndUnready()throws Exception {
        Case sessions=new Case();sessions.plan.extraSessions=65;
        try{rejects("cdp_event_failed",()->sessions.run((binding,deadline)->binding.frames().refresh(deadline)));}finally{sessions.close();}
        Case deep=new Case();deep.plan.depth=35;
        try{rejects("cdp_transport_failed",()->deep.run((binding,deadline)->binding.frames().refresh(deadline)));}finally{deep.close();}
        Case unready=new Case();unready.plan.childLoader="";
        try{unready.run((binding,deadline)->{
            MdoBrowserCdpFrames graph=binding.frames();JSONObject result=graph.refresh(deadline);boolean found=false;
            for(int i=0;i<result.getJSONArray("frames").length();i++){
                JSONObject row=result.getJSONArray("frames").getJSONObject(i);if(row.optBoolean("loading")){
                    found=true;rejects("cdp_frame_not_ready",()->graph.select(row.getString("id"),row.getString("document")));
                }
            }check(found);return result;
        });}finally{unready.close();}
        Case wrongParent=new Case();
        try{wrongParent.run((binding,deadline)->{
            MdoBrowserCdpFrames graph=binding.frames();graph.refresh(deadline);
            wrongParent.plan.pending.add(event("Page.frameNavigated","child-session",new JSONObject().put("frame",frame("child-local","root-local","forged-load").getJSONObject("frame"))));
            rejects("cdp_event_failed",()->binding.call("Page.getFrameTree",new JSONObject(),1000));return new JSONObject().put("ok",true);
        });}finally{wrongParent.close();}
    }
    public static void main(String[] ignored)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");nested();lifecycle();faults();swapsAndRetirement();earlySubtreeAndThread();limitsAndUnready();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_ui_calls_doubled\":true,\"protocol_peers_synthetic\":true,\"provider_qualified\":false}");
    }
}
