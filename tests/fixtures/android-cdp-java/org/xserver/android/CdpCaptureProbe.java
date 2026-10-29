package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real frame/world/owner/session/worker/JNI and native capture transaction.
 * DOM, Android UI and raster bytes are doubled, not provider qualification. */
public final class CdpCaptureProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean value){if(!value)throw new AssertionError("Capture proof check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Checked {void run()throws Exception;}
    private static void rejects(Checked task)throws Exception {try{task.run();throw new AssertionError("Missing capture refusal");}catch(IOException error){check(error.getMessage().startsWith("cdp_"));}}
    private static String dom(String raw){int index=Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame").indexOf(raw)+1;
        if(index<1)throw new AssertionError("Unknown native frame");return String.format("d-00000000-0000-4000-8000-%012d",index);}
    private static String parent(String raw){return raw.equals("leaf-frame")?"child-local":raw.equals("child-local")?"child-frame":"frame-owned";}
    private static String ref(String raw){return "e"+(Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame").indexOf(raw)+1);}
    private static JSONObject point(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    private static JSONObject viewport(String raw)throws Exception{return new JSONObject().put("width",raw.equals("frame-owned")?320:300).put("height",raw.equals("frame-owned")?240:200);}
    private static JSONObject box(double width)throws Exception{return point(10,20).put("width",width).put("height",200);}
    private static final class Clock implements MdoBrowserCapture.Scheduler {
        long time=100;final TreeMap<Long,List<Runnable>> tasks=new TreeMap<>();
        public long now(){return time;}public void post(Runnable task,long delay){at(task,time+delay);}
        public void at(Runnable task,long deadline){tasks.computeIfAbsent(deadline,key->new ArrayList<>()).add(task);}
        public void remove(Runnable task){for(List<Runnable> rows:tasks.values())rows.removeIf(row->row==task);tasks.entrySet().removeIf(row->row.getValue().isEmpty());}
        void ready(){while(!tasks.isEmpty()&&tasks.firstKey()<=time)for(Runnable task:tasks.pollFirstEntry().getValue())task.run();}
    }
    private static final class Surface implements MdoBrowserCapture.Target {
        final String document;final Clock clock;int draws,encodes,closes;long navigation=1;byte[] source;Runnable onEncode;
        Surface(String document,Clock clock){this.document=document;this.clock=clock;}
        public MdoBrowserCapture.Snapshot snapshot(){return new MdoBrowserCapture.Snapshot(document,navigation,640,480,0,0,2);}
        public void ready(Runnable complete){clock.post(complete,0);}
        public MdoBrowserCapture.Raster draw(MdoBrowserCapture.Plan plan){draws++;return new MdoBrowserCapture.Raster(){
            public byte[] encode(String format,int limit){encodes++;check(format.equals("png")&&limit==262144);if(onEncode!=null)onEncode.run();return source=new byte[]{1,2,3};}
            public void close(){closes++;}};}
    }
    private static final class Case implements AutoCloseable {
        final CdpOwnerProbe.Case peer=new CdpOwnerProbe.Case();final Map<String,JSONObject> nodes=new LinkedHashMap<>();
        final List<String> evaluated=new ArrayList<>();boolean opaque,badWorld,changedOwner;Runnable onWorld;
        Case()throws Exception {
            peer.server.worlds.evaluation=(context,expression)->{
                check(expression.startsWith(MdoBrowserCdpCapture.FENCE+"("));evaluated.add(context.frame);
                JSONObject witness=new JSONObject(expression.substring(MdoBrowserCdpCapture.FENCE.length()+1,expression.length()-1));
                check(witness.getString("document").equals(dom(context.frame))&&witness.getBoolean("root")==context.frame.equals("frame-owned"));
                if(onWorld!=null)onWorld.run();return new JSONObject().put("ok",!badWorld).put("code","capture_changed");
            };
            peer.server.ownerValue=(child,value)->{
                JSONObject owner=new JSONObject().put("ok",true).put("document",dom(parent(child))).put("ref",ref(child));
                if(opaque&&child.equals("child-frame"))return new JSONObject().put("ok",false).put("owner",owner).put("error",new JSONObject().put("code","unsupported_geometry"));
                return value.put("ok",true).put("owner",owner).put("painted",true).put("content",box(changedOwner?301:300)).put("box",box(300))
                    .put("scroll",point(0,0)).put("viewport",viewport(parent(child)));
            };
            peer.host.run((binding,deadline)->{JSONArray rows=binding.frames().inventory(deadline).getJSONArray("frames");for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);nodes.put(binding.frames().select(row.getString("id"),row.getString("document")).raw,row);}
                return new JSONObject().put("ok",true);});
        }
        JSONObject request()throws Exception {
            JSONArray stamp=new JSONArray();for(Map.Entry<String,JSONObject> entry:nodes.entrySet()){
                String raw=entry.getKey();JSONObject node=entry.getValue();boolean root=raw.equals("frame-owned"),hidden=opaque&&(raw.equals("child-local")||raw.equals("leaf-frame"));
                String status=hidden?"opaque_ancestor":opaque&&raw.equals("child-frame")?"opaque_geometry":"readable";boolean readable=status.equals("readable");
                JSONObject edge=root||hidden?null:new JSONObject().put("owner",new JSONObject().put("ok",true).put("document",dom(parent(raw))).put("ref",ref(raw)));
                if(edge!=null&&!status.equals("opaque_geometry"))edge.put("content",box(300)).put("box",box(300));
                stamp.put(new JSONArray().put(node.getString("id")).put(node.getString("document")).put(node.opt("parent"))
                    .put(false).put(true).put(status).put(readable?dom(raw):JSONObject.NULL).put(readable?point(0,0):JSONObject.NULL)
                    .put(readable?viewport(raw):JSONObject.NULL).put(edge==null?JSONObject.NULL:edge));
            }
            JSONObject context=new JSONObject().put("adapter","android-native-frames-v1").put("native_document",nodes.get("frame-owned").getString("document"))
                .put("document",dom("frame-owned")).put("navigation",1).put("viewport",viewport("frame-owned")).put("scroll",point(0,0)).put("device_pixel_ratio",2).put("frame_stamp",stamp.toString());
            return new JSONObject().put("command","capture").put("agent",true).put("navigation",1).put("capture_key","capture-fixture")
                .put("clip",point(20,30).put("width",64).put("height",32).put("scale",1)).put("capture_context",context).put("format","png").put("max_bytes",262144).put("timeout_ms",10000);
        }
        JSONObject fence(MdoBrowserCdpCapture operation)throws Exception {
            return peer.host.run((binding,deadline)->new JSONObject(new String(operation.fence(binding,deadline),StandardCharsets.UTF_8)));
        }
        public void close()throws Exception {peer.close();check(peer.server.objects.isEmpty());}
    }
    private static void proofs()throws Exception {
        try(Case sample=new Case()){
            JSONObject request=sample.request();MdoBrowserCdpCapture operation=MdoBrowserCdpCapture.parse(request);
            request.getJSONObject("capture_context").put("frame_stamp","caller-mutated");request.getJSONObject("clip").put("width",9999);
            check(sample.fence(operation).getBoolean("ok")&&!sample.peer.host.lastEffect);check(sample.evaluated.containsAll(sample.nodes.keySet()));
            MdoBrowserCapture.Snapshot snapshot=new MdoBrowserCapture.Snapshot(operation.nativeDocument,1,640,480,0,0,2);MdoBrowserCapture.Plan plan=operation.plan(snapshot);
            check(plan.x==40&&plan.y==60&&plan.width==128&&plan.height==64&&plan.outputWidth==128&&operation.reportedScale(plan)==1);
            check(!sample.fence(operation).toString().contains("fd-"));check(sample.peer.host.peers.size()==1);
        }
        try(Case sample=new Case()){
            sample.opaque=true;check(sample.fence(MdoBrowserCdpCapture.parse(sample.request())).getBoolean("ok"));
            check(!sample.evaluated.contains("child-frame")&&!sample.evaluated.contains("child-local")&&!sample.evaluated.contains("leaf-frame"));
        }
        for(String failure:Arrays.asList("world","owner","navigation"))try(Case sample=new Case()){
            MdoBrowserCdpCapture operation=MdoBrowserCdpCapture.parse(sample.request());
            if(failure.equals("world"))sample.badWorld=true;if(failure.equals("owner"))sample.changedOwner=true;
            if(failure.equals("navigation"))sample.onWorld=()->sample.peer.host.plan.childLoader="changed-child-loader";
            rejects(()->sample.fence(operation));
        }
    }
    private static void grammar()throws Exception {
        try(Case sample=new Case()){
            for(String key:Arrays.asList("script","sessionId","uniqueContextId","worldName","profile","url","debug")){
                JSONObject request=sample.request().put(key,"untrusted");rejects(()->MdoBrowserCdpCapture.parse(request));}
            JSONObject old=sample.request();old.getJSONObject("capture_context").put("adapter","android-main-document-v1");rejects(()->MdoBrowserCdpCapture.parse(old));
            JSONObject lossy=sample.request().put("format","jpeg");rejects(()->MdoBrowserCdpCapture.parse(lossy));
            JSONObject wrong=sample.request();wrong.getJSONObject("capture_context").put("document",dom("child-frame"));rejects(()->MdoBrowserCdpCapture.parse(wrong));
            JSONObject extra=sample.request();extra.getJSONObject("capture_context").put("objectId","raw-object");rejects(()->MdoBrowserCdpCapture.parse(extra));
            JSONObject malformed=sample.request();malformed.getJSONObject("capture_context").put("frame_stamp","[]");rejects(()->MdoBrowserCdpCapture.parse(malformed));
            for(String tail:Arrays.asList("[]","!","true")){
                JSONObject trailing=sample.request();JSONObject context=trailing.getJSONObject("capture_context");
                context.put("frame_stamp",context.getString("frame_stamp")+tail);rejects(()->MdoBrowserCdpCapture.parse(trailing));}
            for(String stamp:Arrays.asList(new String(new char[2000]).replace('\0','[')+new String(new char[2000]).replace('\0',']'),
                "["+new String(new char[600]).replace("\0","[],")+"[]]","[]/*hidden*/","['unquoted']")){
                JSONObject bounded=sample.request();bounded.getJSONObject("capture_context").put("frame_stamp",stamp);rejects(()->MdoBrowserCdpCapture.parse(bounded));}
            for(Object nav:Arrays.asList(0,-1,1.5,"1",9007199254740992d)){
                JSONObject bounded=sample.request().put("navigation",nav);bounded.getJSONObject("capture_context").put("navigation",nav);
                rejects(()->MdoBrowserCdpCapture.parse(bounded));}
            JSONObject maximum=sample.request().put("navigation",9007199254740991L);maximum.getJSONObject("capture_context").put("navigation",9007199254740991L);
            check(MdoBrowserCdpCapture.parse(maximum).navigation==9007199254740991L);
            JSONObject region=sample.request();region.getJSONObject("clip").put("width",1000);MdoBrowserCdpCapture full=MdoBrowserCdpCapture.parse(region);
            rejects(()->full.plan(new MdoBrowserCapture.Snapshot(full.nativeDocument,1,640,480,0,0,2)));
            JSONObject badParent=sample.request();JSONArray rows=new JSONArray(badParent.getJSONObject("capture_context").getString("frame_stamp"));rows.getJSONArray(1).put(2,rows.getJSONArray(1).get(0));
            badParent.getJSONObject("capture_context").put("frame_stamp",rows.toString());rejects(()->MdoBrowserCdpCapture.parse(badParent));
        }
    }
    private static void roundtrip(String mode)throws Exception {
        try(Case sample=new Case()){
            MdoBrowserCdpCapture operation=MdoBrowserCdpCapture.parse(sample.request());Clock clock=new Clock();Surface surface=new Surface(operation.nativeDocument,clock);
            List<Runnable> encoders=new ArrayList<>();MdoBrowserCapture.Budget budget=new MdoBrowserCapture.Budget(2);
            MdoBrowserCapture capture=new MdoBrowserCapture(surface,clock,encoders::add,budget);List<MdoBrowserCapture.Result> results=new ArrayList<>();
            MdoBrowserCdpCapture.Session session=operation.session(sample.peer.host.worker,sample.peer.host.marker,()->null);
            if(mode.equals("owner"))surface.onEncode=()->sample.changedOwner=true;
            if(mode.equals("navigation"))surface.onEncode=()->sample.peer.host.plan.childLoader="changed-child-loader";
            capture.capture(operation.plan(surface.snapshot()),session::check,result->{session.close();results.add(result);});
            if(mode.equals("cancel"))capture.cancel();
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(results.isEmpty()&&System.nanoTime()<end){Handler.drain();clock.ready();if(!encoders.isEmpty())encoders.remove(0).run();Thread.sleep(1);}
            check(results.size()==1);MdoBrowserCapture.Result result=results.get(0);
            if(mode.equals("ok")){check(result.ok&&Arrays.equals(result.data,new byte[]{1,2,3}));check(surface.draws==1&&surface.encodes==1&&surface.closes==1);}
            else{check(!result.ok&&result.data==null);if(surface.source!=null)for(byte value:surface.source)check(value==0);}
            capture.close();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(sample.peer.host.worker.pending()!=0&&System.nanoTime()<until){Handler.drain();Thread.sleep(1);}
            check(sample.peer.host.worker.pending()==0&&!capture.busy()&&capture.cleanupConfirmed()&&budget.pending()==0&&clock.tasks.isEmpty());
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");proofs();grammar();for(String mode:Arrays.asList("ok","owner","navigation","cancel"))roundtrip(mode);
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_capture_transaction\":true,\"native_ui_calls_doubled\":true,\"protocol_peers_synthetic\":true,\"provider_qualified\":false}");
    }
}
