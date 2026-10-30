package org.xserver.android;

import android.os.Looper;
import android.os.SystemClock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Actual product fragments, JNI/CDP/native callback core and real file queue.
 * Controlled surrounding UI/DOM is not an installed Android qualification. */
public final class CdpUploadHostProbe {
    private static int checks;private static Path root;private static final byte[] BYTES=new byte[131075];
    private interface Checked {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Product upload check "+(checks+1));checks++;}
    private static void reject(String code,Checked call)throws Exception {try{call.run();throw new AssertionError("Missing product upload rejection: "+code);}catch(IllegalStateException|UnsupportedOperationException|IllegalArgumentException error){if(code!=null&&!code.equals(error.getMessage()))throw new AssertionError("Wrong native rejection "+error.getMessage());check(true);}}
    private static String sha(byte[] bytes)throws Exception {StringBuilder text=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();}
    private static JSONObject copy(JSONObject value)throws Exception {return new JSONObject(value.toString());}
    private static JSONObject begin(String key,JSONObject target)throws Exception {
        JSONObject value=new JSONObject().put("action","begin").put("navigation",1).put("upload_key",key).put("timeout_ms",120000).put("files",new JSONArray()
            .put(new JSONObject().put("name","native-\u5185\u5bb9 #% +.bin").put("size",BYTES.length).put("sha256",sha(BYTES)))
            .put(new JSONObject().put("name","empty.bin").put("size",0).put("sha256",sha(new byte[0]))));
        if(target!=null)for(Iterator<String> keys=target.keys();keys.hasNext();){String name=keys.next();value.put(name,target.get(name));}return value;
    }
    private static JSONObject assign(String ticket,JSONObject target)throws Exception {JSONObject value=copy(target);return value.put("action","assign").put("ticket",ticket).put("navigation",1).put("timeout_ms",120000);}
    private static JSONObject control(String action,String ticket)throws Exception {return new JSONObject().put("action",action).put("ticket",ticket);}
    private static String stage(UploadAssignmentHost host,String frame)throws Exception {
        JSONObject target=host.target(frame),request=begin("original",copy(target));String id=host.dispatch(request);
        // UI caller mutation after native admission cannot retarget/alter bytes.
        request.put("ref","e8");request.getJSONArray("frame_chain").getJSONObject(0).put("document","fd-00000000-0000-4000-8000-000000000099");
        request.getJSONArray("files").getJSONObject(0).put("name","changed.bin");
        host.until(()->host.replies.containsKey(id));JSONObject admitted=host.replies.get(id);check(admitted.getBoolean("ok")&&admitted.getBoolean("assignment_supported"));String ticket=admitted.getString("ticket");
        for(int at=0;at<BYTES.length;at+=65536){check(host.call(new JSONObject().put("action","chunk").put("ticket",ticket).put("navigation",1).put("file",0).put("offset",at)
            .put("base64",Base64.getEncoder().encodeToString(Arrays.copyOfRange(BYTES,at,Math.min(BYTES.length,at+65536))))).getBoolean("ok"));}
        check(host.call(control("seal",ticket).put("navigation",1)).getBoolean("sealed"));return ticket;
    }
    private static UploadAssignmentHost ready()throws Exception {UploadAssignmentHost host=new UploadAssignmentHost(root);host.qualified=true;host.bind();return host;}
    private static void flow()throws Exception {
        for(String frame:Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame"))try(UploadAssignmentHost host=ready()){
            String ticket=stage(host,frame);JSONObject request=assign(ticket,host.target(frame));JSONObject done=host.call(copy(request));
            check(done.getBoolean("ok")&&done.getBoolean("accepted")&&done.getBoolean("completed")&&done.getBoolean("verified")&&done.getBoolean("retained")&&done.getBoolean("cleanup_confirmed"));
            check(!done.getBoolean("events_trusted")&&done.getBoolean("receipt_only")&&host.grants==1&&host.responses==1&&host.nativePeer.commits==1&&host.nativePeer.opens==1);
            int before=host.nativePeer.calls.size();JSONObject repeated=host.call(copy(request));check(repeated.getBoolean("completed")&&host.nativePeer.calls.size()==before);
            host.navigate();host.reconnect();SystemClock.now=122001;host.consume(BYTES);
            JSONObject status=host.call(control("status",ticket));check(status.getBoolean("retained")&&status.getLong("navigation")==1&&status.getBoolean("completed"));
            reject("upload_target_changed",()->host.dispatch(copy(request)));
            host.manual.add("tab");host.browserPaused=true;check(host.call(control("cancel",ticket)).getBoolean("retained"));
            reject("upload_confirmation_required",()->host.dispatch(control("release",ticket)));
            JSONObject released=host.call(control("release",ticket).put("confirmed",true));check(released.getBoolean("released")&&!released.getBoolean("retained"));
            check(host.call(control("release",ticket).put("confirmed",true)).getBoolean("released")&&host.nativePeer.commits==1);host.until(()->host.activeFiles()==0);
        }
    }
    private static void admission()throws Exception {
        try(UploadAssignmentHost host=new UploadAssignmentHost(root)){
            check(!host.state().getBoolean("upload_capability"));
            reject("upload_assignment_unsupported",()->host.dispatch(new JSONObject().put("action","assign")));
            host.bind();reject("upload_assignment_unsupported",()->host.dispatch(begin("closed",host.target("leaf-frame"))));check(host.nativePeer.opens==0&&host.activeFiles()==0);
        }
        try(UploadAssignmentHost host=ready()){
            JSONObject target=host.target("leaf-frame");JSONObject plain=host.call(begin("plain",null));check(plain.getBoolean("ok"));
            reject("upload_target_changed",()->host.dispatch(begin("plain",copy(target))));
            JSONObject accepted=host.call(begin("bound",copy(target)));check(accepted.getBoolean("ok"));
            reject("upload_target_changed",()->host.dispatch(begin("bound",null)));
            reject("upload_target_changed",()->host.dispatch(begin("bound",copy(target).put("ref","e8"))));
            reject("upload_field_unsupported",()->host.dispatch(begin("raw",copy(target)).put("path","not-native-authority")));
            reject("upload_field_unsupported",()->host.dispatch(assign(accepted.getString("ticket"),copy(target)).put("userGesture",true)));
            check(host.nativePeer.opens==0);
        }
    }
    private static void pendingAndLostReply()throws Exception {
        try(UploadAssignmentHost host=ready()){
            String ticket=stage(host,"leaf-frame");host.callback=false;JSONObject request=assign(ticket,host.target("leaf-frame"));String original=host.dispatch(copy(request));
            host.until(()->host.nativePeer.opens==1);check(host.busy()&&host.state().getBoolean("upload_busy"));
            check(host.call(copy(request)).getBoolean("pending")&&host.nativePeer.opens==1);
            reject("upload_operation_busy",()->host.dispatch(new JSONObject().put("command","frames").put("action","list")));
            reject("upload_operation_busy",()->host.dispatch(begin("second",null)));
            JSONObject cancelled=host.call(control("cancel",ticket));check(cancelled.getBoolean("ok")&&cancelled.getBoolean("cancel_requested"));host.until(()->host.replies.containsKey(original));
            check(!host.replies.get(original).getBoolean("completed")&&host.nativePeer.commits==0);host.until(()->host.activeFiles()==0);
        }
        try(UploadAssignmentHost host=ready()){
            String ticket=stage(host,"child-local");JSONObject request=assign(ticket,host.target("child-local"));host.dropReplies=true;host.dispatch(copy(request));
            host.until(()->!host.busy()&&host.nativePeer.commits==1);host.dropReplies=false;
            JSONObject status=host.call(control("status",ticket));check(status.getBoolean("completed")&&status.getBoolean("retained"));
            check(host.call(copy(request)).getBoolean("completed")&&host.nativePeer.commits==1);host.consume(BYTES);
            check(host.call(control("release",ticket).put("confirmed",true)).getBoolean("released"));
        }
        try(UploadAssignmentHost host=ready()){
            String ticket=stage(host,"leaf-frame");host.invalidateBinding();
            reject(null,()->host.dispatch(assign(ticket,host.target("leaf-frame"))));check(host.nativePeer.opens==0&&host.nativePeer.commits==0);
        }
    }
    private static void lifecycle()throws Exception {
        try(UploadAssignmentHost host=ready()){
            String ticket=stage(host,"leaf-frame");host.onGrant=host::destroyOriginal;
            JSONObject done=host.call(assign(ticket,host.target("leaf-frame")));
            check(!done.getBoolean("ok")&&!done.getBoolean("retained")&&host.grants==1&&host.responses==1&&host.nativePeer.commits==0&&host.books()==0);
            host.until(()->host.activeFiles()==0);
        }
        try(UploadAssignmentHost host=ready()){
            String ticket=stage(host,"leaf-frame");host.nativePeer.onCommit=()->new android.os.Handler(Looper.getMainLooper()).post(host::navigate);
            JSONObject done=host.call(assign(ticket,host.target("leaf-frame")));check(!done.getBoolean("ok")&&!done.getBoolean("completed")&&done.getBoolean("retained")&&host.nativePeer.commits==1);
            host.consume(BYTES);JSONObject status=host.call(control("status",ticket));check(status.getBoolean("ok")&&status.getBoolean("retained")&&!status.getBoolean("assignment_ok")&&status.has("assignment_error"));host.destroyOriginal();host.until(()->host.activeFiles()==0);check(host.books()==0);
        }
        try(UploadAssignmentHost host=ready()){
            String ticket=stage(host,"leaf-frame");check(host.call(assign(ticket,host.target("leaf-frame"))).getBoolean("completed"));
            String id=host.dispatch(control("status",ticket));host.destroyOriginal();host.until(()->host.replies.containsKey(id));
            check(!host.replies.get(id).getBoolean("ok")&&host.replies.get(id).getString("error").equals("upload_owner_changed"));host.until(()->host.activeFiles()==0);
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");root=Paths.get(args[0]);if(Files.exists(root))throw new IllegalStateException("Interrupted product upload fixture preserved");
        for(int i=0;i<BYTES.length;i++)BYTES[i]=(byte)(i*13+7);
        admission();flow();pendingAndLostReply();lifecycle();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"exact_product_fragments\":true,\"real_jvm_jni_file_queue\":true,\"native_callback_core\":true,\"no_effect_replay\":true,\"framework_verified\":false,\"uri_assignment_verified\":false,\"renderer_dom_synthetic\":true}");
    }
}
