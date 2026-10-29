package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real sequencer/worker/frame/world/owner/JNI. Android events and DOM provider
 * are test doubles; this does not qualify installed geometry or focus. */
public final class CdpInputProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean value){if(!value)throw new AssertionError("Input bridge check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Checked {void run()throws Exception;}
    private static void rejects(String code,Checked task)throws Exception {try{task.run();throw new AssertionError("Missing input rejection");}catch(IOException error){check(code.equals(error.getMessage()));}}
    private static final String DOC="d-00000000-0000-4000-8000-000000000001";
    private static final class Scheduler implements MdoBrowserInput.Scheduler {
        long time=100;final TreeMap<Long,List<Runnable>> tasks=new TreeMap<>();
        public long now(){return time;}public void post(Runnable task,long delay){tasks.computeIfAbsent(time+delay,k->new ArrayList<>()).add(task);}
        public void remove(Runnable task){for(List<Runnable> list:tasks.values())list.removeIf(row->row==task);tasks.entrySet().removeIf(row->row.getValue().isEmpty());}
        void ready(){while(!tasks.isEmpty()&&tasks.firstKey()<=time){List<Runnable> due=tasks.pollFirstEntry().getValue();for(Runnable task:due)task.run();}}
        void next(){if(!tasks.isEmpty())time=Math.max(time,tasks.firstKey());ready();}
    }
    private static final class Target implements MdoBrowserInput.Target {
        final List<String> sent=new ArrayList<>();boolean focused=true,available=true,accept=true;Runnable afterDown;
        public long document(){return 1;}public int width(){return 640;}public int height(){return 480;}
        public boolean interactive(){return available;}public boolean focused(){return focused;}
        public boolean acquireFocus(){sent.add("focus");focused=true;return true;}
        public boolean touch(int action,long down,long time,float x,float y){sent.add("touch:"+action+":"+x+":"+y);if(action==0&&afterDown!=null)afterDown.run();return accept;}
        public boolean wheel(long time,float x,float y,float dx,float dy){sent.add("wheel:"+x+":"+y+":"+dx+":"+dy);return accept;}
        public boolean hover(long time,float x,float y){sent.add("hover:"+x+":"+y);return accept;}
        public boolean key(boolean down,long started,long time,String key,int modifiers){sent.add("key:"+down+":"+key+":"+modifiers);return accept;}
        public boolean text(String value){sent.add("text:"+value);return accept;}
    }
    private static final class Case implements AutoCloseable {
        final CdpOwnerProbe.Case peer=new CdpOwnerProbe.Case();final Scheduler scheduler=new Scheduler();final Target target=new Target();
        final MdoBrowserInput input=new MdoBrowserInput(target,scheduler);final List<MdoBrowserInput.Result> results=new ArrayList<>();
        final List<String> scripts=new ArrayList<>(),sources=new ArrayList<>();JSONObject tree;String rejection,guardFailure;Runnable onTarget;
        final MdoBrowserCdpInput.Layout layout=new MdoBrowserCdpInput.Layout(640,480,0,0,2);
        Case()throws Exception {
            peer.server.worlds.evaluation=(context,expression)->{
                scripts.add(expression);sources.add(context.frame);
                JSONObject value=new JSONObject().put("ok",true);
                if(expression.startsWith(MdoBrowserCdpInput.ROOT+"(")){check(context.frame.equals("frame-owned"));return value;}
                check(expression.startsWith(MdoBrowserCdpInput.TARGET+"("));
                if(onTarget!=null)onTarget.run();if(guardFailure!=null)return new JSONObject().put("ok",false).put("code",guardFailure);
                JSONObject witness=new JSONObject(expression.substring(MdoBrowserCdpInput.TARGET.length()+1,expression.length()-1));
                check(witness.getString("document").equals(DOC)&&witness.getString("ref").equals("e7"));
                return value.put("point",witness.getString("kind").equals("wheel")?witness.getJSONObject("point"):new JSONObject().put("x",20).put("y",30));
            };
            tree=peer.host.run((binding,deadline)->binding.frames().inventory(deadline));
        }
        JSONObject row(String raw)throws Exception {return peer.host.run((binding,deadline)->{
            JSONArray rows=binding.frames().refresh(deadline).getJSONArray("frames");for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);if(binding.frames().select(row.getString("id"),row.getString("document")).raw.equals(raw))return row;
            }throw new AssertionError("Missing fixture native frame");});}
        JSONObject request(String raw,String kind,boolean full)throws Exception {
            JSONObject leaf=row(raw);JSONArray chain=new JSONArray(),rows=tree.getJSONArray("frames");JSONObject at=leaf;
            while(at!=null){chain.put(new JSONObject().put("frame",at.getString("id")).put("document",at.getString("document")));Object parent=at.opt("parent");at=null;
                for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(parent))at=rows.getJSONObject(i);}
            JSONObject request=new JSONObject().put("command","input").put("agent",true).put("navigation",1).put("kind",kind)
                .put("input_document",DOC).put("input_ref","e7").put("frame_chain",chain);
            if(full){JSONArray snapshot=new JSONArray();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);snapshot.put(new JSONObject().put("frame",row.getString("id"))
                    .put("document",row.getString("document")).put("parent",row.opt("parent")));}request.put("frame_snapshot",snapshot);}
            if(kind.matches("click|move|wheel"))request.put("x",20+(chain.length()-1)*10).put("y",30+(chain.length()-1)*20).put("button","left").put("click_count",1).put("dx",2).put("dy",-3);
            if(kind.matches("focus|key|text"))request.put("focus_document",DOC).put("focus_ref","e7");
            if(kind.equals("text"))request.put("text","Unicode 中文 🙂");if(kind.equals("key"))request.put("key","Control+A");return request;
        }
        void deliver(MdoBrowserCdpInput operation)throws Exception {
            int count=results.size();operation.run(input,peer.host.worker,peer.host.marker,()->rejection,results::add);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(results.size()==count&&System.nanoTime()<end){scheduler.ready();Handler.drain();
                if(peer.host.worker.pending()==0)scheduler.next();else Thread.sleep(1);}
            check(results.size()==count+1);Handler.drain();
        }
        public void close()throws Exception {input.close();peer.close();check(!input.busy()&&scheduler.tasks.isEmpty());}
    }
    private static void modes()throws Exception {
        try(Case sample=new Case()){
            for(String raw:Arrays.asList("frame-owned","child-frame","child-local","leaf-frame"))for(String kind:Arrays.asList("click","move","wheel","focus","text","key")){
                JSONObject request=sample.request(raw,kind,true);MdoBrowserCdpInput operation=MdoBrowserCdpInput.parse(request,sample.layout);
                int count=sample.target.sent.size();sample.deliver(operation);MdoBrowserInput.Result result=sample.results.get(sample.results.size()-1);
                check(result.ok&&result.cleanupConfirmed&&result.effectMayHaveOccurred);
                List<String> sent=sample.target.sent.subList(count,sample.target.sent.size());check(sent.size()==(kind.equals("key")||kind.equals("click")?2:1));
                if(kind.equals("text"))check(sent.get(0).equals("text:Unicode 中文 🙂"));
                if(kind.equals("key"))check(sent.get(0).equals("key:true:A:2")&&sent.get(1).equals("key:false:A:2"));
                if(kind.equals("wheel"))check(sent.get(0).endsWith(":4.0:-6.0"));
            }
            check(sample.peer.server.objects.isEmpty());check(sample.peer.host.peers.size()==1);
        }
    }
    private static void immutableAndFailures()throws Exception {
        try(Case sample=new Case()){
            JSONObject request=sample.request("leaf-frame","click",true);MdoBrowserCdpInput operation=MdoBrowserCdpInput.parse(request,sample.layout);
            request.put("x",9999).put("kind","text");request.getJSONArray("frame_chain").getJSONObject(0).put("document","caller-changed");
            sample.deliver(operation);check(sample.results.get(0).ok);
            sample.guardFailure="input_target_changed";int count=sample.target.sent.size();sample.deliver(MdoBrowserCdpInput.parse(sample.request("leaf-frame","click",false),sample.layout));
            check(!sample.results.get(1).ok&&!sample.results.get(1).effectMayHaveOccurred&&sample.results.get(1).cleanupConfirmed&&sample.target.sent.size()==count);
            sample.guardFailure=null;
        }
        try(Case sample=new Case()){
            sample.target.afterDown=()->sample.peer.host.plan.childLoader="new-child-loader";
            sample.deliver(MdoBrowserCdpInput.parse(sample.request("leaf-frame","click",true),sample.layout));MdoBrowserInput.Result result=sample.results.get(0);
            check(!result.ok&&result.effectMayHaveOccurred&&result.cleanupConfirmed&&result.deliveredSteps==1);
            check(sample.target.sent.size()==2&&sample.target.sent.get(0).startsWith("touch:0:")&&sample.target.sent.get(1).startsWith("touch:3:"));
        }
        try(Case sample=new Case()){
            sample.onTarget=()->sample.rejection="user_taken_control";sample.deliver(MdoBrowserCdpInput.parse(sample.request("leaf-frame","text",true),sample.layout));
            MdoBrowserInput.Result result=sample.results.get(0);check(!result.ok&&!result.effectMayHaveOccurred&&sample.target.sent.isEmpty());
        }
        try(Case sample=new Case()){
            sample.guardFailure="input_focus_lost";sample.deliver(MdoBrowserCdpInput.parse(sample.request("leaf-frame","focus",false),sample.layout));
            MdoBrowserInput.Result result=sample.results.get(0);check(!result.ok&&result.effectMayHaveOccurred&&sample.target.sent.equals(Arrays.asList("focus")));
        }
        try(Case sample=new Case()){
            sample.rejection="input_viewport_changed";sample.deliver(MdoBrowserCdpInput.parse(sample.request("leaf-frame","click",false),sample.layout));
            check(!sample.results.get(0).ok&&!sample.results.get(0).effectMayHaveOccurred&&sample.scripts.isEmpty()&&sample.target.sent.isEmpty());
        }
        try(Case sample=new Case()){
            JSONObject shifted=sample.request("leaf-frame","click",false);shifted.put("x",shifted.getDouble("x")+1);
            sample.deliver(MdoBrowserCdpInput.parse(shifted,sample.layout));check(!sample.results.get(0).ok&&sample.target.sent.isEmpty());
        }
    }
    private static void ancestryAndGrammar()throws Exception {
        try(Case sample=new Case()){
            for(String field:Arrays.asList("script","sessionId","uniqueContextId","objectId","debug","profile")){
                JSONObject request=sample.request("leaf-frame","click",false).put(field,"caller-picked");rejects("cdp_input_request_invalid",()->MdoBrowserCdpInput.parse(request,sample.layout));
            }
            JSONObject bad=sample.request("leaf-frame","click",false);bad.put("agent","true");rejects("cdp_input_request_invalid",()->MdoBrowserCdpInput.parse(bad,sample.layout));
            JSONObject badText=sample.request("leaf-frame","text",false).put("text","\uD800");rejects("cdp_input_request_invalid",()->MdoBrowserCdpInput.parse(badText,sample.layout));
            JSONObject right=sample.request("leaf-frame","click",false).put("button","right");rejects("cdp_input_kind_unsupported",()->MdoBrowserCdpInput.parse(right,sample.layout));
            JSONObject mismatch=sample.request("leaf-frame","key",false).put("focus_ref","e8");rejects("cdp_input_focus_mismatch",()->MdoBrowserCdpInput.parse(mismatch,sample.layout));
            for(Object value:Arrays.asList("4",-1,500)){
                JSONObject coordinate=sample.request("leaf-frame","click",false).put("x",value);
                rejects(value instanceof String?"cdp_input_request_invalid":"cdp_input_coordinate_invalid",()->MdoBrowserCdpInput.parse(coordinate,sample.layout));
            }
            JSONObject hugeText=sample.request("leaf-frame","text",false);char[] oversized=new char[8193];Arrays.fill(oversized,'x');hugeText.put("text",new String(oversized));
            rejects("cdp_input_request_invalid",()->MdoBrowserCdpInput.parse(hugeText,sample.layout));
            JSONObject wrongRoot=sample.request("leaf-frame","click",false);wrongRoot.getJSONArray("frame_chain").getJSONObject(0).put("frame","f0");
            rejects("cdp_input_frame_pins_invalid",()->MdoBrowserCdpInput.parse(wrongRoot,sample.layout));
            JSONObject duplicate=sample.request("leaf-frame","click",false);JSONArray duplicatePins=duplicate.getJSONArray("frame_chain");duplicatePins.put(0,duplicatePins.get(1));
            rejects("cdp_input_frame_pins_invalid",()->MdoBrowserCdpInput.parse(duplicate,sample.layout));
            JSONObject extra=sample.request("leaf-frame","click",true);extra.getJSONArray("frame_snapshot").getJSONObject(0).put("raw","forbidden");rejects("cdp_input_frame_pins_invalid",()->MdoBrowserCdpInput.parse(extra,sample.layout));
            JSONObject broken=sample.request("leaf-frame","click",false);JSONArray pins=broken.getJSONArray("frame_chain");pins.remove(1);
            MdoBrowserCdpInput wrongParent=MdoBrowserCdpInput.parse(broken,sample.layout);
            sample.deliver(wrongParent);check(!sample.results.get(0).ok&&!sample.results.get(0).effectMayHaveOccurred&&sample.target.sent.isEmpty());
        }
        try(Case sample=new Case()){
            JSONObject request=sample.request("frame-owned","click",true);JSONArray snapshot=request.getJSONArray("frame_snapshot");snapshot.remove(snapshot.length()-1);
            sample.deliver(MdoBrowserCdpInput.parse(request,sample.layout));check(!sample.results.get(0).ok&&sample.target.sent.isEmpty());
        }
    }
    private static void cancelPending(boolean timeout)throws Exception {
        try(Case sample=new Case()){
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);CdpWorldsProbe.Engine.Evaluation original=sample.peer.server.worlds.evaluation;
            sample.peer.server.worlds.evaluation=(context,expression)->{if(expression.startsWith(MdoBrowserCdpInput.ROOT+"(")){
                entered.countDown();check(release.await(3,TimeUnit.SECONDS));}return original.value(context,expression);};
            MdoBrowserCdpInput operation=MdoBrowserCdpInput.parse(sample.request("leaf-frame","focus",false),sample.layout);
            operation.run(sample.input,sample.peer.host.worker,sample.peer.host.marker,()->null,sample.results::add);sample.scheduler.ready();
            check(entered.await(2,TimeUnit.SECONDS));if(timeout){sample.scheduler.time+=600;sample.scheduler.ready();}else sample.input.cancel();
            check(sample.results.size()==1&&sample.results.get(0).effectMayHaveOccurred);
            check(sample.results.get(0).code.equals(timeout?"input_timed_out":"input_cancelled"));
            release.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(sample.peer.host.worker.pending()!=0&&System.nanoTime()<end){Handler.drain();Thread.sleep(1);}
            check(sample.peer.host.worker.pending()==0);check(sample.target.sent.equals(Arrays.asList("focus")));check(sample.scripts.stream().noneMatch(value->value.startsWith(MdoBrowserCdpInput.TARGET+"(")));
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");modes();immutableAndFailures();ancestryAndGrammar();cancelPending(false);cancelPending(true);
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_input_sequencer\":true,\"native_ui_calls_doubled\":true,\"protocol_peers_synthetic\":true,\"provider_qualified\":false}");
    }
}
