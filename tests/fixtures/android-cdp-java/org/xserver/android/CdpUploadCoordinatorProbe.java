package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** Actual native coordinator, picker, file store/worker/read authority and
 * original-world CDP/JNI. Android UI/callback and renderer selection are peers,
 * not installed-framework/URI/DOM-consumption qualification. */
public final class CdpUploadCoordinatorProbe {
    private static int checks;private static Path root;
    private static void check(boolean value){if(!value)throw new AssertionError("Upload coordinator check "+(checks+1));checks++;}
    private static String sha(byte[] bytes)throws Exception {StringBuilder out=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}
    private static final class Case implements AutoCloseable {
        final CdpUploadProbe.Case nativePeer;final MdoBrowserUploadQueue queue;final MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(1);
        final MdoBrowserUploadStore.RetentionOwner dataOwner=new MdoBrowserUploadStore.RetentionOwner();
        final Map<Runnable,Long> timers=new LinkedHashMap<>();final Handler handler=new Handler(Looper.getMainLooper());
        final MdoBrowserPrivatePicker picker;final MdoBrowserChoosers ledger;final byte[] bytes=new byte[131075];
        final AtomicLong now=new AtomicLong(1000);final AtomicReference<Throwable> callbackFailure=new AtomicReference<>();
        final List<String> events=new ArrayList<>();final List<MdoBrowserUploadCoordinator.Result> completed=new ArrayList<>();
        final Thread ui=Thread.currentThread();volatile boolean expiredNativeClock;
        MdoBrowserUploadCoordinator coordinator;String ticket,mode="";boolean current=true,dropFileReplies,callback=true,select=true;int responses,grants;long epoch=1;
        MdoBrowserUploadStore.ReadLease[] delivered;
        Case(String frame)throws Exception {
            nativePeer=new CdpUploadProbe.Case(frame);nativePeer.selected=false;
            Files.createDirectory(root);for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i*13+7);
            queue=new MdoBrowserUploadQueue(()->{MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root);files.acquireCacheLease();return files;},task->!dropFileReplies&&handler.post(task),now::get);
            MdoBrowserChoosers.Scheduler ui=new MdoBrowserChoosers.Scheduler(){public long now(){return now.get();}public void at(Runnable task,long time){timers.put(task,time);}public void remove(Runnable task){timers.remove(task);}};
            ledger=new MdoBrowserChoosers(nativePeer.peer.peer.host.view,ui,()->current,()->epoch,blocked->{});
            picker=new MdoBrowserPrivatePicker(nativePeer.peer.peer.host.view,ledger,new MdoBrowserPrivatePicker.Scheduler(){public long now(){return now.get();}public void at(Runnable task,long time){timers.put(task,time);}public void remove(Runnable task){timers.remove(task);}},()->current,()->epoch);
            MdoBrowserUploadStore.Spec[] specs={new MdoBrowserUploadStore.Spec("native-\u5185\u5bb9 #% +.bin",bytes.length,sha(bytes)),new MdoBrowserUploadStore.Spec("empty.bin",0,sha(new byte[0]))};
            ticket=call(MdoBrowserUploadQueue.Request.begin("coordinator",specs,121000)).value.ticket;
            for(int at=0;at<bytes.length;at+=65536)check(call(MdoBrowserUploadQueue.Request.chunk(ticket,0,at,Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,at,Math.min(bytes.length,at+65536))))).ok());
            check(call(MdoBrowserUploadQueue.Request.ticket("seal",ticket)).value.sealed);
            nativePeer.onOpen=()->{if(callback)handler.post(()->{try{opened();}catch(Throwable error){callbackFailure.set(error);}});};
        }
        void pump()throws Exception {
            Handler.drain();for(Map.Entry<Runnable,Long> timer:new ArrayList<>(timers.entrySet()))if(timer.getValue()<=now.get()&&timers.remove(timer.getKey())!=null)timer.getKey().run();
            Handler.drain();if(callbackFailure.get()!=null)throw new AssertionError(callbackFailure.get());
        }
        void until(java.util.function.BooleanSupplier done)throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!done.getAsBoolean()&&System.nanoTime()<end){pump();Thread.sleep(1);}check(done.getAsBoolean());
        }
        MdoBrowserUploadQueue.Outcome call(MdoBrowserUploadQueue.Request request)throws Exception {
            AtomicReference<MdoBrowserUploadQueue.Outcome> result=new AtomicReference<>();check(queue.submit(scope,request,result::set));until(()->result.get()!=null);return result.get();
        }
        MdoBrowserUploadCoordinator create(int timeout,MdoBrowserUploadCoordinator.Completion completion)throws Exception {
            return new MdoBrowserUploadCoordinator(queue,scope,ticket,dataOwner,nativePeer.peer.peer.host.worker,nativePeer.peer.peer.host.marker,picker,
                MdoBrowserCdpFileInput.parse(nativePeer.peer.request(nativePeer.peer.raw)),()->current,new MdoBrowserUploadCoordinator.Scheduler(){
                    public void at(Runnable task,long due){timers.put(task,due);}public void remove(Runnable task){timers.remove(task);}
                },()->expiredNativeClock&&Thread.currentThread()!=ui?121001:now.get(),timeout,completion);
        }
        void start()throws Exception {coordinator=create(120000,result->{events.add("done");completed.add(result);if(mode.equals("completion-throws"))throw new OutOfMemoryError("Synthetic UI completion loss");});coordinator.start();}
        void opened()throws Exception {
            events.add("native-callback");check(grants==0&&completed.isEmpty());
            check(picker.open(nativePeer.peer.peer.host.view,new Object(),(files,current)->{
                responses++;events.add(files==null?"null":"files");if(files==null)return;
                check(current.getAsBoolean()&&files.length==2);grants++;delivered=files;
                if(mode.equals("drop-retain"))dropFileReplies=true;
                if(mode.equals("cancel-in-response"))coordinator.close();
                if(mode.equals("response-throws"))throw new IllegalStateException("Synthetic native response failure");
                if(select)nativePeer.selected=true;
            },mode.equals("wrong-mode")?0:1,new String[]{"application/x-mdo-browseruse-00000000000000000000000000000001"},false));
        }
        void consume()throws Exception {
            check(delivered!=null&&delivered.length==2);AtomicReference<Throwable> failed=new AtomicReference<>();Thread reader=new Thread(()->{try{
                byte[] result=new byte[bytes.length];for(int at=0;at<result.length;at+=65536){byte[] part=queue.read(delivered[0],at,Math.min(65536,result.length-at),5000);System.arraycopy(part,0,result,at,part.length);}
                check(Arrays.equals(result,bytes));check(queue.read(delivered[1],0,1,5000).length==0);
            }catch(Throwable error){failed.set(error);}},"coordinator-file-reader");reader.start();reader.join(6000);check(!reader.isAlive());if(failed.get()!=null)throw new AssertionError(failed.get());
        }
        void release()throws Exception {
            MdoBrowserUploadStore.Retention value=coordinator.retained();check(value!=null);check(call(MdoBrowserUploadQueue.Request.retained(value,true)).retainedValue.cleanupConfirmed);
            check(queue.activeFiles()==0&&queue.reservedBytes()==0);
        }
        public void close()throws Exception {
            try{if(coordinator!=null)coordinator.close();dataOwner.close();scope.cancel();picker.close();picker.viewDestroyed(nativePeer.peer.peer.host.view);ledger.close();ledger.viewDestroyed(nativePeer.peer.peer.host.view);}
            finally{queue.close();check(queue.awaitStopped(5000));Handler.drain();nativePeer.close();}
            timers.clear();Files.delete(root.resolve(MdoBrowserUploadFiles.OWNER_FILE));Files.delete(root);
        }
    }
    private static void success()throws Exception {
        for(String frame:Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame"))try(Case sample=new Case(frame)){
            sample.start();sample.until(()->sample.coordinator.done());MdoBrowserUploadCoordinator.Result result=sample.coordinator.result();
            check(result.ok&&result.accepted&&result.verified&&result.completed&&result.dispatched&&result.retained&&result.cleanupConfirmed);
            check(sample.grants==1&&sample.responses==1&&sample.completed.size()==1&&sample.nativePeer.commits==1&&sample.nativePeer.opens==1&&sample.nativePeer.disposals==1);
            check(sample.events.indexOf("native-callback")<sample.events.indexOf("files")&&sample.events.indexOf("files")<sample.events.indexOf("done"));
            sample.scope.cancel();sample.now.set(122001);sample.consume();check(sample.coordinator.result().retained);sample.release();
            check(!sample.coordinator.result().retained);
        }
    }
    private static void duplicateClaim()throws Exception {
        try(Case sample=new Case("leaf-frame")){
            sample.callback=false;sample.start();sample.until(()->sample.nativePeer.opens==1);sample.pump();
            MdoBrowserUploadCoordinator other=sample.create(120000,result->{});other.start();sample.until(other::done);
            check(other.result().code.equals("upload_already_assigning")&&!other.result().accepted&&sample.queue.activeFiles()==2);
            sample.callback=true;sample.opened();sample.until(()->sample.coordinator.done());check(sample.coordinator.result().ok&&sample.nativePeer.commits==1);sample.consume();sample.release();
        }
    }
    private static void failures()throws Exception {
        for(String mode:Arrays.asList("wrong-mode","cancel-in-response","response-throws","completion-throws"))try(Case sample=new Case("child-local")){
            sample.mode=mode;sample.start();sample.until(()->sample.coordinator.done());MdoBrowserUploadCoordinator.Result result=sample.coordinator.result();
            check(sample.completed.size()==1&&sample.nativePeer.opens==1);
            if(mode.equals("completion-throws")){check(result.ok&&result.retained&&sample.nativePeer.commits==1);sample.consume();sample.release();}
            else{check(!result.ok&&!result.dispatched&&!result.retained&&sample.nativePeer.commits==0);sample.until(()->sample.queue.activeFiles()==0);}
        }
        try(Case sample=new Case("leaf-frame")){
            sample.nativePeer.failure="partial";sample.start();sample.until(()->sample.coordinator.done());MdoBrowserUploadCoordinator.Result result=sample.coordinator.result();
            check(result.ok&&result.accepted&&!result.completed&&!result.verified&&result.retained&&result.outcome.equals("target_changed_after_input"));sample.consume();sample.release();
        }
    }
    private static void cancellationAndLostReplies()throws Exception {
        try(Case sample=new Case("leaf-frame")){
            // The queued worker observes expiry before another UI admission
            // sample. A fresh worker-local timer cannot authorize page effects.
            sample.expiredNativeClock=true;sample.start();sample.until(()->sample.coordinator.done());
            check(!sample.coordinator.result().ok&&sample.nativePeer.opens==0&&sample.nativePeer.commits==0&&!sample.coordinator.result().retained);
            sample.until(()->sample.queue.activeFiles()==0);
        }
        try(Case sample=new Case("leaf-frame")){
            sample.start();sample.coordinator.close();sample.until(()->sample.coordinator.done());check(!sample.coordinator.result().ok&&sample.nativePeer.opens==0&&sample.nativePeer.commits==0);
            sample.until(()->sample.queue.activeFiles()==0);
        }
        try(Case sample=new Case("leaf-frame")){
            sample.nativePeer.onCommit=()->sample.handler.post(sample.coordinator::close);sample.start();sample.until(()->sample.coordinator.done());
            check(!sample.coordinator.result().ok&&sample.coordinator.result().retained&&sample.nativePeer.commits==1&&sample.coordinator.result().dispatched);
            sample.scope.cancel();sample.now.set(122001);sample.consume();sample.release();
        }
        try(Case sample=new Case("leaf-frame")){
            sample.callback=false;sample.start();sample.until(()->sample.nativePeer.opens==1);sample.now.set(121001);sample.pump();sample.until(()->sample.coordinator.done());
            check(!sample.coordinator.result().ok&&!sample.coordinator.result().retained&&sample.nativePeer.commits==0);
        }
        try(Case sample=new Case("leaf-frame")){
            sample.dropFileReplies=true;sample.start();sample.now.set(121001);sample.pump();sample.now.set(122002);sample.pump();sample.until(()->sample.coordinator.done());
            check(!sample.coordinator.result().ok&&!sample.coordinator.result().stagingCleanupConfirmed&&sample.nativePeer.opens==0);
        }
        try(Case sample=new Case("leaf-frame")){
            sample.mode="drop-retain";sample.start();sample.until(()->sample.grants==1&&sample.nativePeer.statuses>=2);sample.until(()->sample.queue.activeFiles()==0);
            sample.now.set(121001);sample.pump();sample.until(()->!sample.coordinator.done()&&sample.nativePeer.disposals==1&&sample.timers.containsValue(122001L));
            sample.now.set(122002);sample.pump();sample.until(()->sample.coordinator.done());
            check(!sample.coordinator.result().ok&&!sample.coordinator.result().retained&&sample.nativePeer.commits==0);
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");root=Paths.get(args[0]);if(Files.exists(root))throw new IllegalStateException("Interrupted coordinator fixture preserved");
        success();duplicateClaim();failures();cancellationAndLostReplies();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_jvm_jni_file_queue\":true,\"original_callback_core\":true,\"retained_bytes_verified\":true,\"framework_verified\":false,\"android_uri_assignment_verified\":false,\"renderer_dom_synthetic\":true}");
    }
}
