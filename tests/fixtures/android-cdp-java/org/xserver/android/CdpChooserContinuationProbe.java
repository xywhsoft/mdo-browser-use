package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exact host admission/router/invalidation fragments, actual native callback
 * ledger and JVM/JNI/CDP worker. Android UI and renderer/DOM are synthetic. */
public final class CdpChooserContinuationProbe {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Chooser continuation check "+(checks+1));checks++;}
    private interface Checked {void run()throws Exception;}
    private static void rejected(Checked call)throws Exception {try{call.run();throw new AssertionError("Missing scoped chooser rejection");}catch(IllegalStateException expected){check(true);}}
    private static JSONObject request(String action)throws Exception {return new JSONObject().put("command","frames").put("agent",true).put("action",action);}
    private static void waitFor(java.util.function.BooleanSupplier condition)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!condition.getAsBoolean()&&System.nanoTime()<end){Handler.drain();Thread.sleep(1);}check(condition.getAsBoolean());
    }
    private static JSONObject call(ChooserContinuationHost host,JSONObject request)throws Exception {
        int before=host.replies.size();host.dispatch(request);waitFor(()->host.replies.size()==before+1);return host.replies.get(before);
    }
    private static void bind(ChooserContinuationHost host)throws Exception {
        JSONObject frames=call(host,request("list"));check(frames.getBoolean("ok"));AtomicReference<Throwable> failure=new AtomicReference<>();AtomicReference<Boolean> done=new AtomicReference<>();
        host.cdpWorker.submitExisting(host.saved(),12000,false,(binding,deadline)->{
            try{MdoBrowserCdpFrames graph=binding.frames();JSONArray rows=graph.inventory(deadline).getJSONArray("frames");
                for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);host.peer.nodes.put(graph.select(row.getString("id"),row.getString("document")).raw,row);}
            }catch(Throwable error){failure.set(error);throw new java.io.IOException("cdp_fixture_inventory_failed");}
            return "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        },result->{done.set(result.code==null);});waitFor(()->done.get()!=null);if(failure.get()!=null)throw new AssertionError(failure.get());check(done.get());
    }
    private static void pendingEvent(CdpChooserProbe.Case sample,String raw)throws Exception {
        String source=raw.equals("leaf-frame")?"leaf-session":raw.equals("child-frame")||raw.equals("child-local")?"child-session":null;
        JSONObject packet=new JSONObject().put("method","Page.fileChooserOpened").put("params",new JSONObject().put("frameId",raw).put("backendNodeId",77).put("mode","selectMultiple"));
        if(source!=null)packet.put("sessionId",source);sample.waiting.add(packet);
    }
    private static void flow()throws Exception {
        for(String raw:Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame")){
            CdpChooserProbe.Case sample=new CdpChooserProbe.Case(false);try(ChooserContinuationHost host=new ChooserContinuationHost(sample.inputs)){
                bind(host);check(call(host,request("chooser_arm")).getBoolean("armed"));MdoBrowserCdpMarker original=host.saved();int declarations=host.view.evaluations,worlds=sample.inputs.peer.server.worlds.created;
                final boolean[] inputStopped={false};host.views.input("tab",host.view).tap(1,1,result->inputStopped[0]=!result.ok);
                pendingEvent(sample,raw);host.pending(1,false);
                check(inputStopped[0]&&!host.views.inputBusy("tab",host.view)&&host.views.inputClean("tab",host.view));
                check(original.lease().current()&&host.saved()==original&&host.documents.get("tab").blocked&&host.cancellations==0);
                rejected(host::original); // Ordinary marker admission remains blocked.
                JSONObject listed=call(host,request("chooser_list"));check(listed.getBoolean("event_verified")&&listed.getJSONArray("choosers").length()==1);
                JSONObject target=sample.target(raw,listed.getJSONArray("choosers").getJSONObject(0));JSONObject pinned=call(host,target);
                check(pinned.getBoolean("ok")&&pinned.getJSONObject("value").getJSONObject("element").getString("ref").equals("e7"));
                for(String action:Arrays.asList("list","evaluate","owner","file_input","upload_policy","capture_owner","chooser_arm")){
                    JSONObject blocked=new JSONObject(target.toString()).put("action",action);int peers=sample.inputs.peer.host.peers.size(),count=sample.inputs.peer.host.plan.methods.size();
                    rejected(()->host.dispatch(blocked));check(sample.inputs.peer.host.peers.size()==peers&&sample.inputs.peer.host.plan.methods.size()==count);
                }
                for(String extra:Arrays.asList("script","userGesture","native_node","socket","objectId"))rejected(()->host.dispatch(request("chooser_list").put(extra,"untrusted")));
                check(!call(host,request("chooser_disarm")).getBoolean("armed")&&host.cancellations==0); // No callback/frame timing association or URI authority.
                check(host.view.evaluations==declarations&&sample.inputs.peer.server.worlds.created==worlds&&sample.inputs.peer.host.peers.size()==1);
                host.chooser.choosers.cancel();check(host.cancellations==1&&!host.documents.get("tab").blocked&&host.original()==original);
                check(call(host,request("list")).getBoolean("ok")&&host.view.evaluations==declarations);
            }
        }
    }
    private static void noFreshBinding()throws Exception {
        CdpChooserProbe.Case sample=new CdpChooserProbe.Case(false);try(ChooserContinuationHost host=new ChooserContinuationHost(sample.inputs)){
            host.pending(1,false);JSONObject refused=call(host,request("chooser_list"));check(!refused.getBoolean("ok")&&host.view.evaluations==0&&sample.inputs.peer.host.peers.isEmpty());
        }
        sample=new CdpChooserProbe.Case(false);try(ChooserContinuationHost host=new ChooserContinuationHost(sample.inputs)){
            bind(host);call(host,request("chooser_arm"));MdoBrowserCdpMarker old=host.saved();host.pending(1,false);host.channel.attach(host.host.getUrl(),new ChooserContinuationHost.WebMessagePort());
            JSONObject refused=call(host,request("chooser_list"));check(!refused.getBoolean("ok")&&sample.inputs.peer.host.peers.size()==1);check(old.lease().current());
        }
        sample=new CdpChooserProbe.Case(false);try(ChooserContinuationHost host=new ChooserContinuationHost(sample.inputs)){
            bind(host);call(host,request("chooser_arm"));host.pending(1,false);int declarations=host.view.evaluations;host.views.navigation("tab",host.view);
            JSONObject refused=call(host,request("chooser_list"));check(!refused.getBoolean("ok")&&host.view.evaluations==declarations&&sample.inputs.peer.host.peers.size()==1&&host.cancellations==1);
        }
    }
    private static void allOtherLosses()throws Exception {
        for(String mode:Arrays.asList("directory","capture","dialog","popup","manual-picker","hidden","manual","loading","background","stopped","expired","clock-owner")){
            CdpChooserProbe.Case sample=new CdpChooserProbe.Case(false);try(ChooserContinuationHost host=new ChooserContinuationHost(sample.inputs)){
                bind(host);call(host,request("chooser_arm"));MdoBrowserCdpMarker old=host.saved();
                if(mode.equals("dialog"))host.chooser.dialogs.pending=true;
                if(mode.equals("popup")){ChooserContinuationHost.DialogState state=new ChooserContinuationHost.DialogState();state.pending=true;host.windows.put("tab",state);}
                if(mode.equals("manual-picker"))host.manualChooser.ready=false;if(mode.equals("hidden"))host.view.shown=false;
                if(mode.equals("manual"))host.manual.add("tab");if(mode.equals("loading"))host.loading.put("tab",true);if(mode.equals("background"))host.browserPaused=true;if(mode.equals("stopped"))host.stopped=true;
                host.pending(mode.equals("directory")?2:1,mode.equals("capture"));
                if(mode.equals("expired")){host.time=121001;host.invalidate("file_chooser_open");}
                if(mode.equals("clock-owner")){host.channel.invalidate();host.invalidate("file_chooser_open");}
                check(!old.lease().current());int declarations=host.view.evaluations,peers=sample.inputs.peer.host.peers.size();
                try{JSONObject response=call(host,request("chooser_list"));check(!response.getBoolean("ok"));}catch(IllegalStateException blocked){check(true);}
                check(host.view.evaluations==declarations&&sample.inputs.peer.host.peers.size()==peers);
            }
        }
        for(String reason:Arrays.asList("input_cancelled","input_view_unavailable","input_viewport_changed","user_taken_control","input_dialog_open","popup_pending")){
            CdpChooserProbe.Case sample=new CdpChooserProbe.Case(false);try(ChooserContinuationHost host=new ChooserContinuationHost(sample.inputs)){
                bind(host);call(host,request("chooser_arm"));MdoBrowserCdpMarker old=host.saved();host.pending(1,false);check(old.lease().current());host.invalidate(reason);check(!old.lease().current());
            }
        }
    }
    private static void inFlightJob()throws Exception {
        CdpChooserProbe.Case sample=new CdpChooserProbe.Case(false);try(ChooserContinuationHost host=new ChooserContinuationHost(sample.inputs)){
            bind(host);call(host,request("chooser_arm"));MdoBrowserCdpMarker old=host.saved();
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            AtomicReference<MdoBrowserCdpWorker.Result> result=new AtomicReference<>();
            try{
                host.cdpWorker.submitExisting(old,12000,false,(binding,deadline)->{
                    entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException("cdp_fixture_wait_timeout");}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException("cdp_fixture_interrupted");}
                    return "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                },result::set);
                waitFor(()->entered.getCount()==0);check(host.cdpWorker.pending(old));host.pending(1,false);waitFor(()->result.get()!=null);
                check(!old.lease().current()&&result.get().code.equals("cdp_worker_cancelled"));
                int peers=sample.inputs.peer.host.peers.size();JSONObject refused=call(host,request("chooser_list"));check(!refused.getBoolean("ok")&&sample.inputs.peer.host.peers.size()==peers);
            }finally{release.countDown();}
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");flow();noFreshBinding();allOtherLosses();inFlightJob();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_jvm_jni_json\":true,\"exact_host_fragments\":true,\"read_only_chooser_continuation\":true,\"android_callback_frame_association\":false,\"files_granted\":0,\"framework_verified\":false}");
    }
}
