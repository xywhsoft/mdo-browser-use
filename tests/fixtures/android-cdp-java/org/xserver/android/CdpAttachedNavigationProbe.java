package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Actual session and JNI envelopes. Controlled transport/retained events prove
 * receipt isolation, never native WebView ownership, frame or UI admission. */
public final class CdpAttachedNavigationProbe {
    private static int checks;
    private interface Work {void run()throws Exception;}
    private interface Send {void run(JSONObject command)throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Attached navigation check "+(checks+1));checks++;}
    private static void reject(String code,boolean effect,Work work)throws Exception {
        try{work.run();throw new AssertionError("Expected "+code);}catch(MdoBrowserCdpSession.Failure error){check(code.equals(error.getMessage())&&error.effectMayHaveOccurred==effect&&error.getCause()==null);}
    }
    private static JSONObject ack(int id,String child,JSONObject result)throws Exception {JSONObject reply=new JSONObject().put("id",id).put("result",result);if(child!=null)reply.put("sessionId",child);return reply;}
    private static JSONObject navigation(boolean download)throws Exception {return new JSONObject().put("frameId","same-root").put("loaderId","next-loader").put("isDownload",download);}
    private static final class Wire implements MdoBrowserCdpSession.Wire,MdoBrowserCdpSession.Clock {
        final ArrayDeque<byte[]> packets=new ArrayDeque<>();final List<byte[]> buffers=new ArrayList<>();final List<JSONObject> commands=new ArrayList<>();long time;int closes;Send after;
        void add(JSONObject value){packets.add(value.toString().getBytes(StandardCharsets.UTF_8));}
        public long now(){return time;}
        public void send(byte[] bytes,int timeout)throws IOException {
            buffers.add(bytes);try{JSONObject command=new JSONObject(new String(bytes,StandardCharsets.UTF_8));commands.add(command);if(after!=null)after.run(command);}
            catch(IOException error){throw error;}catch(RuntimeException|Error error){throw error;}catch(Exception error){throw new IOException("fixture_send_failed");}
        }
        public byte[] receive(int timeout){byte[] bytes=packets.poll();if(bytes==null)time+=timeout;else buffers.add(bytes);return bytes;}
        public void close(){closes++;}
        void cleared(){for(byte[] bytes:buffers){boolean zero=true;for(byte value:bytes)if(value!=0)zero=false;check(zero);}}
    }
    private static final class Case implements AutoCloseable {
        final Wire wire=new Wire();final Map<String,MdoBrowserCdpSession.AttachedTarget> targets=new HashMap<>();
        final Map<String,AtomicBoolean> owners=new HashMap<>(),retiring=new HashMap<>();final MdoBrowserCdpSession session;
        Work eventAction;
        Case(){session=MdoBrowserCdpSession.ownedBrowser(wire,()->true,(method,child,params)->{
            if(method.equals("Target.attachedToTarget")){
                MdoBrowserCdpSession.AttachedTarget original=wireTarget();String key=params.optString("sessionId");targets.put(key,original);
                // Caller/event mutation cannot retarget this native receipt.
                try{params.put("sessionId","forged");params.getJSONObject("targetInfo").put("targetId","forged");}catch(Exception error){throw new IOException("fixture_mutation_failed");}
            }else if(eventAction!=null)try{eventAction.run();}catch(IOException error){throw error;}catch(Exception error){throw new IOException("fixture_event_failed");}
        },wire);wire.after=command->{String method=command.getString("method");if(method.equals("Fetch.enable")||method.equals("Runtime.runIfWaitingForDebugger")||method.equals("Browser.getVersion"))
            wire.add(ack(command.getInt("id"),command.optString("sessionId",null),new JSONObject()));
        };}
        MdoBrowserCdpSession.AttachedTarget wireTarget()throws IOException{return session.retainAttachedTarget();}
        void prepare(String key,boolean activate)throws Exception {
            owners.put(key,new AtomicBoolean(true));retiring.put(key,new AtomicBoolean(false));
            wire.add(new JSONObject().put("method","Target.attachedToTarget").put("params",new JSONObject().put("sessionId",key).put("waitingForDebugger",true)
                .put("targetInfo",new JSONObject().put("targetId","target-"+key).put("type","page").put("openerId","native-opener"))));
            check(session.poll(10,100));session.protectAttachedTarget(targets.get(key),100);
            if(activate)session.resumeProtectedAttachment(targets.get(key),owners.get(key)::get,100);
        }
        MdoBrowserCdpSession.Navigation go(String key,int timeout)throws Exception {return session.navigateAttached(targets.get(key),owners.get(key)::get,retiring.get(key)::get,"https://private.invalid/"+key,"same-root",timeout);}
        JSONObject last(){return wire.commands.get(wire.commands.size()-1);}
        void answer(JSONObject command,JSONObject value)throws Exception {wire.add(ack(command.getInt("id"),command.getString("sessionId"),value));check(session.poll(10,100));}
        void healthy()throws Exception {session.call("Browser.getVersion",new JSONObject(),null,100,false);check(wire.closes==0&&session.idle());}
        public void close(){session.close();check(wire.closes==1);wire.cleared();}
    }
    private static void parallelAndOrdering()throws Exception {
        try(Case c=new Case()){
            c.prepare("first",true);c.prepare("second",true);int before=c.wire.commands.size();
            MdoBrowserCdpSession.Navigation first=c.go("first",1000);JSONObject one=c.last();
            MdoBrowserCdpSession.Navigation second=c.session.reloadAttached(c.targets.get("second"),c.owners.get("second")::get,c.retiring.get("second")::get,"same-root","old-loader",1000);JSONObject two=c.last();
            check(c.session.pendingControls()==2&&first.pending()&&second.pending()&&c.wire.commands.size()==before+2);
            check(one.getString("sessionId").equals("first")&&two.getString("sessionId").equals("second")&&one.getInt("id")!=two.getInt("id"));
            check(one.getJSONObject("params").getString("frameId").equals("same-root")&&two.getJSONObject("params").length()==1&&two.getJSONObject("params").getString("loaderId").equals("old-loader"));
            reject("cdp_navigation_pending",false,()->c.go("first",1000));
            c.answer(two,new JSONObject());check(second.result().acknowledged&&second.result().ok&&first.pending());
            c.wire.add(ack(one.getInt("id"),"first",navigation(false)));c.healthy();
            check(first.result().acknowledged&&first.result().ok&&first.matchesCommittedLoader("same-root","next-loader")&&c.session.pendingControls()==0);
            MdoBrowserCdpSession.Navigation history=c.session.historyAttached(c.targets.get("first"),c.owners.get("first")::get,c.retiring.get("first")::get,"same-root",29,1000);
            JSONObject request=c.last();check(request.getString("method").equals("Page.navigateToHistoryEntry")&&request.getJSONObject("params").getInt("entryId")==29);
            c.answer(request,new JSONObject());check(history.result().ok&&history.receiptOnly);
        }
    }
    private static void admission()throws Exception {
        try(Case c=new Case();Case foreign=new Case()){
            c.prepare("first",false);foreign.prepare("foreign",true);MdoBrowserCdpSession.AttachedTarget original=c.targets.get("first");int before=c.wire.commands.size();
            reject("cdp_popup_activation_required",false,()->c.go("first",1000));
            reject("cdp_attach_event_invalid",false,()->c.session.navigateAttached(foreign.targets.get("foreign"),()->true,()->false,"about:blank","same-root",1000));
            c.session.resumeProtectedAttachment(original,()->true,100);before=c.wire.commands.size();
            for(String url:new String[]{"javascript:void(0)","file:///private","data:text/html,x","https://u:p@private.invalid/","https://host/\n",null})
                reject("cdp_navigation_arguments_invalid",false,()->c.session.navigateAttached(original,()->true,()->false,url,"same-root",1000));
            reject("cdp_navigation_arguments_invalid",false,()->c.go("first",99));reject("cdp_navigation_arguments_invalid",false,()->c.go("first",600001));
            reject("cdp_popup_navigation_owner_invalid",false,()->c.session.navigateAttached(original,()->true,null,"about:blank","same-root",1000));
            reject("cdp_popup_navigation_owner_invalid",false,()->c.session.navigateAttached(original,()->true,()->{throw new IllegalStateException("private");},"about:blank","same-root",1000));
            reject("cdp_popup_navigation_retired",false,()->c.session.navigateAttached(original,()->true,()->true,"about:blank","same-root",1000));
            reject("cdp_arguments_invalid",false,()->c.session.navigateAttached(original,null,()->false,"about:blank","same-root",1000));
            reject("cdp_attach_owner_changed",false,()->c.session.navigateAttached(original,()->false,()->false,"about:blank","same-root",1000));
            reject("cdp_attach_owner_changed",false,()->c.session.navigateAttached(original,()->{throw new IllegalStateException("private");},()->false,"about:blank","same-root",1000));
            reject("cdp_navigation_arguments_invalid",false,()->c.session.reloadAttached(original,()->true,()->false,"same-root","bad loader",1000));
            check(c.wire.commands.size()==before&&c.session.pendingControls()==0);
            c.eventAction=()->reject("cdp_reentry_forbidden",false,()->c.go("first",1000));c.wire.add(new JSONObject().put("method","Runtime.event").put("params",new JSONObject()));check(c.session.poll(10,100));
            final String[] code={null};Thread other=new Thread(()->{try{c.go("first",1000);}catch(Exception error){code[0]=error.getMessage();}});other.start();other.join(2000);check(!other.isAlive()&&"cdp_wrong_worker".equals(code[0])&&c.wire.commands.size()==before);
            java.lang.reflect.Field count=MdoBrowserCdpSession.class.getDeclaredField("navigationCount");count.setAccessible(true);count.setInt(c.session,4096);reject("cdp_navigation_limit",false,()->c.go("first",1000));
        }
        try(Case c=new Case()){
            for(int i=0;i<9;i++)c.prepare("p"+i,true);
            for(int i=0;i<8;i++)c.go("p"+i,1000);int before=c.wire.commands.size();reject("cdp_navigation_limit",false,()->c.go("p8",1000));check(c.wire.commands.size()==before&&c.session.pendingControls()==8);
            JSONObject first=c.wire.commands.get(before-8);c.answer(first,navigation(false));check(c.go("p8",1000).pending()&&c.session.pendingControls()==8);
        }
    }
    private static void scopedRetirement()throws Exception {
        for(String mode:Arrays.asList("cancel","owner","retiring","throwing-owner","throwing-retirement","timeout","clock"))try(Case c=new Case()){
            c.prepare("first",true);c.prepare("second",true);AtomicBoolean live=c.owners.get("first");
            MdoBrowserCdpSession.Navigation first=mode.equals("throwing-owner")?c.session.navigateAttached(c.targets.get("first"),()->{if(!live.get())throw new IllegalStateException("private");return true;},()->false,"about:blank","same-root",100):
                mode.equals("throwing-retirement")?c.session.navigateAttached(c.targets.get("first"),live::get,()->{if(c.retiring.get("first").get())throw new IllegalStateException("private");return false;},"about:blank","same-root",100):c.go("first",100);
            JSONObject one=c.last();MdoBrowserCdpSession.Navigation second=c.go("second",1000);JSONObject two=c.last();
            if(mode.equals("cancel")){Thread ui=new Thread(first::cancelAttached);ui.start();ui.join(2000);check(!ui.isAlive());}
            else if(mode.equals("timeout"))c.wire.time=100;
            else if(mode.equals("clock"))c.wire.time=-1;
            else{live.set(false);c.retiring.get("first").set(mode.equals("retiring")||mode.equals("throwing-retirement"));}
            c.session.poll(10,100);MdoBrowserCdpSession.NavigationResult terminal=first.result();String code=mode.equals("cancel")?"cdp_navigation_cancelled":mode.equals("retiring")?"cdp_popup_navigation_retired":mode.equals("throwing-retirement")?"cdp_popup_navigation_owner_invalid":mode.equals("timeout")||mode.equals("clock")?"cdp_navigation_timeout":"cdp_navigation_owner_changed";
            check(terminal!=null&&terminal.code.equals(code)&&!terminal.acknowledged&&terminal.effectMayHaveOccurred&&c.wire.closes==0&&c.session.pendingControls()==2);
            if(mode.equals("clock"))c.wire.time=0;
            // Retired data cannot be used, and its original reply may only drain.
            c.wire.add(ack(one.getInt("id"),"first",new JSONObject().put("retired","ignored")));check(c.session.poll(10,100));check(first.result()==terminal&&c.session.pendingControls()==1);
            if(mode.equals("cancel")||mode.equals("timeout")||mode.equals("clock"))reject(code,false,()->c.go("first",1000));
            c.answer(two,navigation(false));check(second.result()!=null&&c.session.pendingControls()==0);
            if(!mode.equals("clock"))check(second.result().ok&&second.result().acknowledged);
            c.healthy();
        }
    }
    private static void localRetireAndSendLoss()throws Exception {
        try(Case c=new Case()){
            c.prepare("first",true);c.prepare("second",true);MdoBrowserCdpSession.Navigation first=c.go("first",1000);JSONObject one=c.last();
            c.session.retireAttachedTarget(c.targets.get("first"));MdoBrowserCdpSession.NavigationResult terminal=first.result();check(terminal!=null&&!terminal.acknowledged&&terminal.effectMayHaveOccurred&&c.session.pendingControls()==0);
            c.wire.add(ack(one.getInt("id"),"first",navigation(false)));check(!c.session.poll(10,100)&&first.result()==terminal);MdoBrowserCdpSession.Navigation second=c.go("second",1000);c.answer(c.last(),navigation(false));check(second.result().ok);c.healthy();
        }
        try(Case c=new Case()){
            c.prepare("first",true);c.prepare("second",true);MdoBrowserCdpSession.Navigation first=c.go("first",1000);
            Send prior=c.wire.after;c.wire.after=request->{if(request.getString("method").equals("Page.navigate"))throw new IOException("private partial send");prior.run(request);};
            reject("cdp_navigation_send_failed",true,()->c.go("second",1000));check(c.wire.closes==1&&first.result().code.equals("cdp_navigation_connection_closed")&&c.session.pendingControls()==0);
        }
        try(Case c=new Case()){
            c.prepare("first",true);AtomicBoolean live=c.owners.get("first");Send prior=c.wire.after;
            c.wire.after=request->{if(request.getString("method").equals("Page.navigate")){live.set(false);c.retiring.get("first").set(true);}else prior.run(request);};
            MdoBrowserCdpSession.Navigation first=c.go("first",1000);check(first.result()!=null&&first.result().code.equals("cdp_popup_navigation_retired")&&c.wire.closes==0);
        }
    }
    private static void negativeAcknowledgements()throws Exception {
        for(int error:new int[]{-32601,-32000})try(Case c=new Case()){
            c.prepare("first",true);c.prepare("second",true);MdoBrowserCdpSession.Navigation first=c.go("first",1000);JSONObject one=c.last();MdoBrowserCdpSession.Navigation second=c.go("second",1000);JSONObject two=c.last();
            c.wire.add(new JSONObject().put("id",one.getInt("id")).put("sessionId","first").put("error",new JSONObject().put("code",error).put("message","private provider prose")));check(c.session.poll(10,100));
            check(first.result().acknowledged&&!first.result().ok&&first.result().effectMayHaveOccurred==(error!=-32601)&&first.result().code.equals(error==-32601?"cdp_navigation_unsupported":"cdp_navigation_rejected"));
            MdoBrowserCdpSession.Navigation next=c.go("first",1000);JSONObject replacement=c.last();c.answer(two,navigation(false));check(second.result().ok);
            c.answer(replacement,navigation(false).put("errorText","net::ERR_CONNECTION_RESET"));check(next.result().acknowledged&&!next.result().ok&&next.result().nativeError.equals("net::ERR_CONNECTION_RESET"));c.healthy();
        }
        try(Case c=new Case()){
            c.prepare("first",true);c.prepare("second",true);MdoBrowserCdpSession.Navigation first=c.go("first",1000);JSONObject one=c.last();first.cancelAttached();c.session.poll(10,100);MdoBrowserCdpSession.NavigationResult terminal=first.result();
            c.wire.add(ack(one.getInt("id"),"second",navigation(false)));reject("cdp_navigation_reply_mismatch",false,()->c.session.poll(10,100));check(first.result()==terminal&&terminal.code.equals("cdp_navigation_cancelled"));
        }
    }
    private static void malformed()throws Exception {
        for(String mode:Arrays.asList("frame","download","loader","error","control","wrong-session","missing-session"))try(Case c=new Case()){
            c.prepare("first",true);c.prepare("second",true);MdoBrowserCdpSession.Navigation original=mode.equals("control")?c.session.historyAttached(c.targets.get("first"),()->true,()->false,"same-root",2,1000):c.go("first",1000);JSONObject command=c.last(),value=navigation(false);
            if(mode.equals("frame"))value.put("frameId","foreign-root");if(mode.equals("download"))value.put("isDownload","false");if(mode.equals("loader"))value.put("loaderId",JSONObject.NULL);if(mode.equals("error"))value.put("errorText",1);
            c.wire.add(ack(command.getInt("id"),mode.equals("missing-session")?null:mode.equals("wrong-session")?"second":"first",value));
            reject(mode.endsWith("session")?"cdp_navigation_reply_mismatch":"cdp_navigation_reply_invalid",false,()->c.session.poll(10,100));check(original.result().code.equals("cdp_navigation_reply_invalid")&&!original.result().acknowledged&&original.result().effectMayHaveOccurred);
        }
    }
    public static void main(String[] ignored)throws Exception {
        Looper.getMainLooper();java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{parallelAndOrdering();admission();scopedRetirement();localRetireAndSendLoss();negativeAcknowledgements();malformed();return null;}catch(Exception error){throw new RuntimeException(error);}}).get(40,TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));}
        System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("original_attachment_navigation_receipts",true).put("independent_source_quarantine",true).put("late_receipts_drained",true).put("real_jvm_jni_json",true).put("native_frame_or_ownership_proved",false).put("provider_qualified",false));
    }
}
