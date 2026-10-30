package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Exact product upload/capture-reservation/lifecycle fragments, real worker and
 * JNI. UI, renderer DOM and qualification peers remain controlled. */
public final class CdpFileClearHostProbe {
    private static int checks;private static Path root;
    private interface Checked {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Product clear check "+(checks+1));checks++;}
    private static void reject(String code,Checked task)throws Exception {
        try{task.run();throw new AssertionError("Missing product clear rejection "+code);}
        catch(IllegalStateException|IllegalArgumentException|UnsupportedOperationException error){
            if(code!=null&&!code.equals(error.getMessage()))throw new AssertionError("Expected "+code+" but got "+error.getMessage());check(true);
        }
    }
    private static JSONObject copy(JSONObject value)throws Exception {return new JSONObject(value.toString());}
    private static JSONObject control(String action,String key)throws Exception {return new JSONObject().put("action",action).put("upload_key",key);}
    private static JSONObject ticket(String action,String ticket)throws Exception {return new JSONObject().put("action",action).put("ticket",ticket);}
    private static JSONObject clear(UploadAssignmentHost host,String frame,String key)throws Exception {
        JSONObject value=host.target(frame);return value.put("action","clear").put("navigation",1).put("upload_key",key).put("timeout_ms",10000);
    }
    private static final class Ready implements AutoCloseable {
        final UploadAssignmentHost host=new UploadAssignmentHost(root);
        final CdpFileClearProbe.Case peer=new CdpFileClearProbe.Case(host.nativePeer.peer);
        Ready()throws Exception {host.qualified=true;host.bind();}
        public void close()throws Exception {host.close();check(peer.node.objects.isEmpty()&&peer.node.groups.isEmpty());}
    }
    private static void flow()throws Exception {
        try(Ready sample=new Ready()){
            UploadAssignmentHost host=sample.host;check(host.state().getBoolean("file_clear_capability"));
            for(String frame:Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame")){
                JSONObject request=clear(host,frame,"clear-"+frame),result=host.call(request);int sent=sample.peer.clears;
                check(result.getBoolean("ok")&&result.getBoolean("accepted")&&result.getBoolean("cleared")&&result.getBoolean("verified")&&result.getBoolean("native_job_drained"));
                check(!host.busy()&&!result.getBoolean("retained")&&result.getBoolean("cleanup_confirmed")&&host.activeFiles()==0&&host.grants==0);
                String identity=result.getString("ticket");check(identity.matches("u-[a-f0-9-]{36}"));
                check(host.call(copy(request)).getString("ticket").equals(identity)&&sample.peer.clears==sent);
                reject("upload_clear_conflict",()->host.dispatch(copy(request).put("ref","e8")));
                JSONObject status=host.call(ticket("status",identity));check(status.getBoolean("ok")&&status.getBoolean("assignment_ok")&&status.getBoolean("cleared"));
                reject("upload_confirmation_required",()->host.dispatch(ticket("release",identity)));
                check(host.call(ticket("release",identity).put("confirmed",true)).getBoolean("released"));
                check(host.call(copy(request)).getBoolean("released")&&sample.peer.clears==sent);
                for(String forbidden:Arrays.asList("sessionId","uniqueContextId","executionContextId","backendNodeId","objectId","private-file-object","isolated-root"))check(!status.toString().contains(forbidden));
            }
            check(sample.peer.clears==5&&host.nativePeer.opens==0&&host.nativePeer.commits==0);
        }
    }
    private static void admission()throws Exception {
        try(Ready sample=new Ready()){
            UploadAssignmentHost host=sample.host;JSONObject request=clear(host,"leaf-frame","denied");host.qualified=false;
            reject("upload_clear_unsupported",()->host.dispatch(copy(request)));check(sample.peer.clears==0&&!host.state().getBoolean("file_clear_capability"));host.qualified=true;
            for(String name:Arrays.asList("files","directory","script","sessionId","objectId","uniqueContextId"))reject("upload_field_unsupported",()->host.dispatch(copy(request).put(name,"untrusted")));
            for(Object value:Arrays.asList("", "bad key",1,JSONObject.NULL))reject(null,()->host.dispatch(copy(request).put("upload_key",value)));
            host.views.input("tab",host.view).tap(10,10,value->{});reject("upload_operation_busy",()->host.dispatch(copy(request)));host.views.input("tab",host.view).cancel();
            for(String mode:Arrays.asList("busy","dirty","foreign")){
                UploadAssignmentHost.CaptureEntry capture=new UploadAssignmentHost.CaptureEntry(mode.equals("foreign")?new android.webkit.WebView():host.view);
                capture.capture.busy=mode.equals("busy");capture.capture.clean=!mode.equals("dirty");host.captures.put("tab",capture);
                reject("upload_capture_busy_or_unclean",()->host.dispatch(copy(request)));host.captures.clear();
            }
            check(sample.peer.clears==0&&host.activeFiles()==0&&host.books()==0);
        }
    }
    private static void pendingAndRecovery()throws Exception {
        try(Ready sample=new Ready()){
            UploadAssignmentHost host=sample.host;CountDownLatch release=new CountDownLatch(1);
            sample.peer.onClear=()->{try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Held clear peer expired");}catch(InterruptedException error){throw new AssertionError(error);}};
            try{
                JSONObject request=clear(host,"leaf-frame","pending"),duplicate=copy(request);String id=host.dispatch(request);host.until(()->sample.peer.clears==1);
                check(host.busy()&&host.state().getBoolean("upload_busy"));JSONObject status=host.call(control("clear_status","pending"));String identity=status.getString("ticket");
                check(status.getBoolean("pending")&&!status.getBoolean("native_job_drained"));check(host.call(duplicate).getString("ticket").equals(identity));
                reject("upload_clear_pending",()->host.dispatch(ticket("release",identity).put("confirmed",true)));
                reject("upload_operation_busy",()->host.dispatch(new JSONObject().put("action","begin")));
                reject("upload_operation_busy",()->host.dispatch(new JSONObject().put("command","frames").put("agent",true).put("action","list")));
                JSONObject cancelled=host.call(control("clear_cancel","pending"));check(cancelled.getBoolean("cancel_requested"));
                release.countDown();host.until(()->host.replies.containsKey(id)&&!host.busy());
                JSONObject failed=host.replies.get(id);check(!failed.getBoolean("ok")&&failed.getBoolean("dispatched")&&failed.getBoolean("native_job_drained"));
                check(host.call(ticket("status",identity)).has("assignment_error")&&sample.peer.clears==1);
                host.manual.add("tab");host.browserPaused=true;check(host.call(control("clear_status","pending")).getBoolean("ok"));
                check(host.call(ticket("release",identity).put("confirmed",true)).getBoolean("released"));check(sample.peer.clears==1);
                host.manual.clear();host.browserPaused=false;host.bind();
                check(host.call(clear(host,"leaf-frame","explicit-after-cancel")).getBoolean("verified")&&sample.peer.clears==2);
                check(host.call(control("clear_status","pending")).has("assignment_error")&&sample.peer.clears==2);
            }finally{release.countDown();}
        }
        try(Ready sample=new Ready()){
            UploadAssignmentHost host=sample.host;JSONObject request=clear(host,"leaf-frame","lost-reply");host.dropReplies=true;host.dispatch(request);host.until(()->!host.busy());host.dropReplies=false;
            JSONObject recovered=host.call(control("clear_status","lost-reply"));check(recovered.getBoolean("assignment_ok")&&recovered.getBoolean("verified"));
            check(host.call(copy(request)).getString("ticket").equals(recovered.getString("ticket"))&&sample.peer.clears==1);
        }
    }
    private static void lifecycle()throws Exception {
        for(String mode:Arrays.asList("navigate","reconnect","takeover","background","destroy"))try(Ready sample=new Ready()){
            UploadAssignmentHost host=sample.host;JSONObject request=clear(host,"leaf-frame","life");
            sample.peer.onClear=()->new Handler(Looper.getMainLooper()).post(()->{
                if(mode.equals("navigate"))host.navigate();if(mode.equals("reconnect"))host.reconnect();
                if(mode.equals("takeover")){host.manual.add("tab");host.cancelScope();}
                if(mode.equals("background")){host.browserPaused=true;host.cancelScope();}if(mode.equals("destroy"))host.destroyOriginal();
            });
            host.dispatch(request);host.until(()->sample.peer.clears==1&&!host.busy());
            if(mode.equals("destroy")){check(host.books()==0);reject("upload_owner_changed",()->host.dispatch(control("clear_status","life")));}
            else{JSONObject status=host.call(control("clear_status","life"));check(status.getBoolean("ok")&&!status.getBoolean("assignment_ok")&&status.has("assignment_error")&&status.getLong("navigation")==1);}
            check(sample.peer.clears==1&&host.grants==0&&host.activeFiles()==0);
        }
    }
    private static void faultsAndBounds()throws Exception {
        for(String mode:Arrays.asList("native_error","native_reply","not_empty","status_ref","status_document","status_flag","status_failed","status_exception"))try(Ready sample=new Ready()){
            sample.peer.failure=mode;JSONObject result=sample.host.call(clear(sample.host,"leaf-frame","fault"));
            check(!result.getBoolean("ok")&&result.getBoolean("dispatched")&&!result.getBoolean("verified")&&sample.peer.clears==1&&!sample.host.busy());
            check(sample.host.call(ticket("status",result.getString("ticket"))).has("assignment_error"));
        }
        for(String mode:Arrays.asList("", "native_reply"))try(Ready sample=new Ready()){
            sample.peer.failure=mode;sample.peer.node.failure="release";JSONObject result=sample.host.call(clear(sample.host,"leaf-frame","cleanup"));
            check(!result.getBoolean("cleanup_confirmed")&&sample.host.state().getBoolean("upload_cleanup_failed"));
            reject("upload_cleanup_failed",()->sample.host.dispatch(clear(sample.host,"leaf-frame","another")));check(sample.peer.clears==1);
        }
        try(Ready sample=new Ready()){
            for(int i=0;i<64;i++)check(sample.host.call(clear(sample.host,"leaf-frame","bounded-"+i)).getBoolean("verified"));
            reject("upload_clear_limit",()->sample.host.dispatch(clear(sample.host,"leaf-frame","overflow")));check(sample.peer.clears==64);
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");root=Paths.get(args[0]);if(Files.exists(root))throw new IllegalStateException("Interrupted product clear fixture preserved");
        flow();admission();pendingAndRecovery();lifecycle();faultsAndBounds();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"exact_product_fragments\":true,\"real_jvm_jni\":true,\"no_effect_replay\":true,\"source_files_read\":0,\"framework_verified\":false,\"renderer_dom_synthetic\":true}");
    }
}
