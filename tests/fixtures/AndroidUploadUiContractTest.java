package org.xserver.android;

import android.webkit.*;
import org.json.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Actual native fragment with view/JSON/UI doubles and real worker/file I/O.
 * No Android framework/private Context/routing qualification is inferred. */
public final class AndroidUploadUiContractTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Upload UI check "+checks+" failed");}
    private static final class Views {
        final Map<String,WebView> map=new HashMap<>();long epoch=1;
        WebView get(String key){return map.get(key);}boolean owns(String key,WebView view){return map.get(key)==view;}
        long epoch(String key,WebView view){return owns(key,view)?epoch:0;}
    }
    private static final class Reply {
        final Object value;final String error;Reply(Object value,String error){this.value=value;this.error=error;}
    }
    private final Views views=new Views();private final Set<String> manual=new HashSet<>();
    private final Map<String,Boolean> loading=new HashMap<>();
    private final Object activity=new Object();private boolean stopped,browserPaused;
    private final WebView host=new WebView();private final MdoBrowserChannel<WebMessagePort> channel=new MdoBrowserChannel<>();
    private final List<Reply> replies=new ArrayList<>();private boolean dialog=true;
    private boolean dialogReady(String key,WebView view){return dialog;}
    // This gate exercises staging only. The full assignment fragment is run by
    // the actual JNI/CDP host integration fixture, not these JSON/UI doubles.
    private boolean uploadCommand(String key,String id,WebView view,JSONObject request){return false;}
    private Object reserveUploadTarget(String key,UploadEntry source,JSONObject request,MdoBrowserUploadQueue.Request queued){return null;}
    private boolean uploadTargetReady(Object reserved,MdoBrowserUploadQueue.Outcome outcome){return false;}
    private void cancelUploadTargets(UploadEntry entry){}
    private void retireUploadTargets(WebView view){}
    private void reply(MdoBrowserChannel<WebMessagePort>.Lease lease,String id,Object value,String error){
        if(channel.active(lease,host.getUrl()))replies.add(new Reply(value,error));
    }
    // NATIVE_UPLOAD_FRAGMENT
    private static JSONObject object(Object... fields)throws Exception{
        JSONObject value=new JSONObject();for(int at=0;at<fields.length;at+=2)value.put((String)fields[at],fields[at+1]);return value;
    }
    private static JSONObject begin(String key,String name,long size,String sha)throws Exception{
        return object("action","begin","navigation",1,"upload_key",key,"files",new JSONArray().put(object("name",name,"size",size,"sha256",sha)));
    }
    private static String hash(byte[] bytes)throws Exception{
        StringBuilder text=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format("%02x",b&255));return text.toString();
    }
    private static void waitFor(java.util.function.BooleanSupplier test)throws Exception{
        long end=System.nanoTime()+5000000000L;while(!test.getAsBoolean()){if(System.nanoTime()>=end)throw new AssertionError("Native upload UI wait timed out");Thread.sleep(10);}
    }
    private interface Checked {void run()throws Exception;}
    private static void rejects(String code,Checked action)throws Exception{
        try{action.run();throw new AssertionError("Expected "+code);}catch(RuntimeException error){check(code.equals(error.getMessage()));}
    }
    private JSONObject execute(WebView view,JSONObject request,ConcurrentLinkedQueue<Runnable> main)throws Exception{
        int previous=replies.size();executeUpload("owned","request",view,request);waitFor(()->!main.isEmpty());
        Runnable task;while((task=main.poll())!=null)task.run();check(replies.size()==previous+1&&replies.get(previous).error==null);
        JSONObject result=(JSONObject)replies.get(previous).value;check(Boolean.TRUE.equals(result.get("ok")));return result;
    }
    private MdoBrowserUploadQueue.Outcome nativeFile(MdoBrowserUploadQueue queue,MdoBrowserUploadStore.Scope scope,MdoBrowserUploadQueue.Request request,ConcurrentLinkedQueue<Runnable> main)throws Exception{
        AtomicReference<MdoBrowserUploadQueue.Outcome> result=new AtomicReference<>();check(queue.submit(scope,request,result::set));
        waitFor(()->!main.isEmpty());Runnable task;while((task=main.poll())!=null)task.run();check(result.get()!=null&&result.get().ok());return result.get();
    }
    private void retainedLifecycle(WebView view,MdoBrowserUploadQueue queue,ConcurrentLinkedQueue<Runnable> main)throws Exception{
        JSONObject source=execute(view,begin("retained","native-data.bin",1,hash(new byte[]{0})),main);String ticket=(String)source.get("ticket");
        execute(view,object("action","chunk","ticket",ticket,"navigation",1,"file",0,"offset",0,"base64","AA=="),main);
        execute(view,object("action","seal","ticket",ticket,"navigation",1),main);
        UploadEntry original=uploadOwners.get("owned");MdoBrowserUploadStore.RetentionOwner data=original.data;
        MdoBrowserUploadStore.ReadLease reader=nativeFile(queue,original.scope,MdoBrowserUploadQueue.Request.access(ticket,0),main).reader;
        // Controlled native grant, never public/caller URI authority. This tests
        // the actual host's data owner with real retained file-worker state.
        Object claim=new Object();check(reader.claimPicker(claim,1000));
        MdoBrowserUploadStore.PickerGrant grant=MdoBrowserUploadStore.pickerGrant(new MdoBrowserUploadStore.ReadLease[]{reader},claim,1000);
        MdoBrowserUploadStore.Retention retained=nativeFile(queue,original.scope,MdoBrowserUploadQueue.Request.retain(grant,data),main).retention;
        check(retained.adopt());cancelUpload("owned",view);check(!original.scope.live()&&data.live());
        views.epoch=2;execute(view,begin("next-document","empty",0,hash(new byte[0])).put("navigation",2),main);
        check(uploadOwners.get("owned")!=original&&uploadOwners.get("owned").data==data);
        cancelUploads();check(data.live()&&retained.live(122001));
        AtomicReference<Throwable> failure=new AtomicReference<>();Thread read=new Thread(()->{try{check(Arrays.equals(new byte[]{0},queue.read(reader,0,1,5000)));}catch(Throwable error){failure.set(error);}},"retained-native-ui-file");
        read.start();read.join(6000);check(!read.isAlive());if(failure.get()!=null)throw new AssertionError(failure.get());
        retireUploadData(new WebView());check(data.live());
        WebView replacement=new WebView();views.map.put("owned",replacement);
        rejects("upload_data_owner_changed",()->executeUpload("owned","request",replacement,begin("foreign","empty",0,hash(new byte[0])).put("navigation",2)));
        check(data.live());views.map.remove("owned");retireUploadData(view);check(!data.live());waitFor(()->queue.activeFiles()==0);
        check(nativeFile(queue,original.scope,MdoBrowserUploadQueue.Request.retained(retained,false),main).retainedValue.cleanupConfirmed);
        views.map.put("owned",replacement);views.epoch=1;
        execute(replacement,begin("new-browser","empty",0,hash(new byte[0])),main);
        check(uploadOwners.get("owned").data!=data&&uploadOwners.get("owned").data.live()&&!retained.live(1000));
        rejects("upload_data_owner_changed",()->uploadData("alias",replacement));
        List<WebView> extra=new ArrayList<>();for(int i=0;i<7;i++){WebView other=new WebView();extra.add(other);check(uploadData("other-"+i,other).live());}
        rejects("upload_data_owner_limit",()->uploadData("ninth",new WebView()));
        retireUploadData(view);check(uploadOwners.get("owned").data.live());
        for(WebView other:extra)retireUploadData(other);retireUploadData(replacement);check(uploadDataOwners.isEmpty()&&uploadOwners.isEmpty());
        waitFor(()->queue.activeFiles()==0);check(queue.reservedBytes()==0);
    }
    private void run(Path root)throws Exception{
        Files.createDirectory(root);ConcurrentLinkedQueue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger opened=new AtomicInteger();
        MdoBrowserUploadQueue queue=new MdoBrowserUploadQueue(()->{opened.incrementAndGet();return new MdoBrowserUploadFiles(root);},task->{main.add(task);return true;},android.os.SystemClock::elapsedRealtime);
        MdoBrowserUploadRuntime.queue=queue;WebView view=new WebView();views.map.put("owned",view);channel.attach(host.getUrl(),new WebMessagePort());
        try{
            String digest=hash(new byte[]{0});JSONObject request=begin("good","\u6587\u6863.txt",1,digest);
            rejects("upload_text_invalid",()->executeUpload("owned","request",view,object("action",42)));
            rejects("upload_number_invalid",()->executeUpload("owned","request",view,begin("bad","one",1,digest).put("navigation","1")));
            rejects("upload_number_invalid",()->executeUpload("owned","request",view,begin("bad","one",1,digest).put("navigation",1.5)));
            rejects("upload_field_unsupported",()->executeUpload("owned","request",view,begin("bad","one",1,digest).put("path","untrusted")));
            rejects("upload_field_unsupported",()->executeUpload("owned","request",view,begin("bad","one",1,digest).put("chooser","unproved")));
            rejects("upload_assignment_unsupported",()->executeUpload("owned","request",view,object("action","assign")));
            rejects("upload_assignment_unsupported",()->executeUpload("owned","request",view,object("action","access")));
            rejects("upload_assignment_unsupported",()->executeUpload("owned","request",view,object("action","read")));
            manual.add("owned");rejects("upload_owner_changed",()->executeUpload("owned","request",view,request));manual.clear();
            browserPaused=true;rejects("upload_owner_changed",()->executeUpload("owned","request",view,request));browserPaused=false;
            dialog=false;rejects("upload_document_unavailable",()->executeUpload("owned","request",view,request));dialog=true;
            check(opened.get()==0);
            JSONObject admitted=execute(view,request,main);String ticket=(String)admitted.get("ticket");
            check(Boolean.FALSE.equals(admitted.get("assignment_supported"))&&Boolean.TRUE.equals(admitted.get("staged_only")));
            check(Boolean.FALSE.equals(admitted.get("effect_may_have_occurred")));
            check(ticket.equals(execute(view,request,main).get("ticket"))&&opened.get()==1);
            execute(view,object("action","chunk","ticket",ticket,"navigation",1,"file",0,"offset",0,"base64","AA=="),main);
            check(Boolean.TRUE.equals(execute(view,object("action","seal","ticket",ticket,"navigation",1),main).get("sealed")));
            rejects("upload_confirmation_required",()->executeUpload("owned","request",view,object("action","release","ticket",ticket)));
            check(Boolean.TRUE.equals(execute(view,object("action","release","ticket",ticket,"confirmed",true),main).get("cleanup_confirmed")));
            JSONObject state=uploadState(new JSONObject());check(Boolean.FALSE.equals(state.get("upload_capability"))&&Boolean.FALSE.equals(state.get("file_input_capability")));
            // A queued result cannot silently bind to a replacement native
            // view that reused the same tab name and navigation number.
            int previous=replies.size();executeUpload("owned","request",view,begin("old","empty",0,hash(new byte[0])));
            waitFor(()->!main.isEmpty());cancelUpload("owned",view);WebView replacement=new WebView();views.map.put("owned",replacement);retireUploadData(view);
            main.poll().run();check(replies.size()==previous+1&&"upload_owner_changed".equals(replies.get(previous).error));
            JSONObject current=execute(replacement,begin("current","empty",0,hash(new byte[0])),main);cancelUpload("owned",view);
            check(Boolean.TRUE.equals(execute(replacement,object("action","seal","ticket",current.get("ticket"),"navigation",1),main).get("sealed")));
            previous=replies.size();executeUpload("owned","request",replacement,object("action","status","ticket",current.get("ticket")));
            waitFor(()->!main.isEmpty());channel.attach(host.getUrl(),new WebMessagePort());main.poll().run();check(replies.size()==previous);
            cancelUploads();waitFor(()->queue.activeFiles()==0);check(queue.reservedBytes()==0);
            retainedLifecycle(replacement,queue,main);
        }finally{
            cancelUploads();for(UploadDataEntry entry:new ArrayList<>(uploadDataOwners.values()))retireUploadData(entry.view);queue.close();check(queue.awaitStopped(5000));Runnable task;while((task=main.poll())!=null)task.run();
            check(queue.unfinishedDeliveries()==0);Files.delete(root);
        }
    }
    public static void main(String[] args)throws Exception{
        new AndroidUploadUiContractTest().run(Paths.get(args[0]));
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false,\"native_cache_authority_verified\":false}");
    }
}
