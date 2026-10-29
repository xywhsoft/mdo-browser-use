package org.xserver.android;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real production queue/store/leases/provider/proxy/Chrome target; Android
 * callback/system types are doubles. No nonce or opaque URI is ever printed. */
public final class AndroidPrivatePickerContractTest {
    static int checks;static final String NONCE=MdoBrowserPrivatePicker.PREFIX+"00000000000000000000000000000001";
    interface Checked {void run()throws Exception;}
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Private picker check "+checks+" failed");}
    static void refused(String code,Checked call)throws Exception {try{call.run();throw new AssertionError("Expected "+code);}catch(IOException failure){check(code.equals(failure.getMessage()));}}
    static final class Reply implements ValueCallback<Uri[]> {
        int calls;Uri[] files;boolean fail,oom;Runnable hook;
        public void onReceiveValue(Uri[] value){calls++;files=value;if(hook!=null)hook.run();if(oom)throw new OutOfMemoryError("Synthetic response failure");if(fail)throw new IllegalStateException("Synthetic response failure");}
    }
    static final class Params extends WebChromeClient.FileChooserParams {
        int mode;String[] types={NONCE};boolean capture,fail;
        public int getMode(){if(fail)throw new OutOfMemoryError("Synthetic callback metadata failure");return mode;}
        public String[] getAcceptTypes(){return types;}
        public boolean isCaptureEnabled(){return capture;}
    }
    static final class Gate {
        boolean owned=true,current=true;long epoch;final WebView view;final MdoBrowserChooserTarget target;
        final List<MdoBrowserPrivatePicker.Result> completions=new ArrayList<>();final AtomicReference<MdoBrowserPrivatePicker.Checked> proof=new AtomicReference<>();
        Gate(Context context,long epoch){this.epoch=epoch;view=new WebView(context);target=new MdoBrowserChooserTarget(view,()->owned,()->this.epoch,value->{},value->{},null,value->{});}
        MdoBrowserPrivatePicker picker(){return target.privatePicker;}
        MdoBrowserPrivatePicker.Lease begin(Fixture f,int timeout)throws Exception {return picker().begin(f.queue,f.scope,new MdoBrowserUploadStore.ReadLease[]{f.reader},()->current,proof::set,timeout,completions::add);}
        void close(){target.close();target.viewDestroyed();}
    }
    static final class Fixture implements AutoCloseable {
        final Path root;final Context context=new Context();final MdoBrowserUploadProvider provider=new MdoBrowserUploadProvider();
        final ConcurrentLinkedQueue<Runnable> ui=new ConcurrentLinkedQueue<>();final MdoBrowserUploadQueue queue;
        volatile boolean refusePost,latePost;
        final AtomicReference<Runnable> clockHook=new AtomicReference<>();
        MdoBrowserUploadStore.Scope scope;MdoBrowserUploadStore.ReadLease reader;String ticket;int epoch;byte[] data;
        Fixture(Path root)throws Exception {
            this.root=root;Files.createDirectory(root);
            queue=new MdoBrowserUploadQueue(()->{MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root);files.acquireCacheLease();return files;},task->{if(refusePost){if(latePost)ui.add(task);return false;}ui.add(task);return true;},()->{Runnable hook=clockHook.get();if(hook!=null)hook.run();return SystemClock.elapsedRealtime();});
            MdoBrowserUploadRuntime.queue=queue;ProviderInfo info=new ProviderInfo();info.authority=context.getPackageName()+".mdo.browseruse.upload";provider.attachInfo(context,info);
        }
        MdoBrowserUploadQueue.Outcome call(MdoBrowserUploadQueue.Request request)throws Exception {
            AtomicReference<MdoBrowserUploadQueue.Outcome> result=new AtomicReference<>();check(queue.submit(scope,request,result::set));long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(result.get()==null&&System.nanoTime()<end){Runnable task=ui.poll();if(task!=null)task.run();else Thread.sleep(1);}check(result.get()!=null);return result.get();
        }
        static String sha(byte[] bytes)throws Exception {StringBuilder out=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();}
        String stage(String name,byte[] bytes)throws Exception {
            MdoBrowserUploadStore.Spec[] specs={new MdoBrowserUploadStore.Spec(name,bytes.length,sha(bytes))};
            MdoBrowserUploadQueue.Outcome begin=call(MdoBrowserUploadQueue.Request.begin("private-"+UUID.randomUUID(),specs,SystemClock.now+120000));check(begin.ok());String id=begin.value.ticket;
            for(int at=0;at<bytes.length;at+=65536)check(call(MdoBrowserUploadQueue.Request.chunk(id,0,at,Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,at,Math.min(bytes.length,at+65536))))).ok());
            check(call(MdoBrowserUploadQueue.Request.ticket("seal",id)).value.sealed);return id;
        }
        void prepare(int size)throws Exception {
            Handler.reset();SystemClock.now=1000;scope=new MdoBrowserUploadStore.Scope(++epoch);data=new byte[size];for(int i=0;i<size;i++)data[i]=(byte)(i*13+7);
            ticket=stage("native-\u5185\u5bb9 #% +.bin",data);reader=call(MdoBrowserUploadQueue.Request.access(ticket,0)).reader;check(reader!=null);
        }
        void consume(Uri uri)throws Exception {
            android.database.Cursor row=provider.query(uri,null,null,null,null);check(row!=null&&row.moveToFirst());check(row.getString(row.getColumnIndexOrThrow("_display_name")).equals(reader.file.name));check(row.getLong(row.getColumnIndexOrThrow("_size"))==data.length);row.close();
            AtomicReference<Throwable> failed=new AtomicReference<>();Thread worker=new Thread(()->{try{
                ParcelFileDescriptor file=provider.openFile(uri,"r");try{check(file.callback.onGetSize()==data.length);byte[] bytes=new byte[data.length];int offset=0;
                    while(offset<bytes.length){byte[] part=new byte[Math.min(65536,bytes.length-offset)];int got=file.callback.onRead(offset,part.length,part);check(got==part.length);System.arraycopy(part,0,bytes,offset,got);offset+=got;}
                    check(Arrays.equals(bytes,data));check(sha(bytes).equals(reader.file.sha256));
                }finally{file.close();}
            }catch(Throwable error){failed.set(error);}},"private-picker-native-file-reader");worker.start();worker.join(5000);check(!worker.isAlive());if(failed.get()!=null)throw new AssertionError("Native picker payload read failed",failed.get());
        }
        void retire()throws Exception {if(ticket!=null){
            if(scope.live())check(call(MdoBrowserUploadQueue.Request.ticket("cancel",ticket)).value.cleanupConfirmed);
            else{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(queue.activeFiles()!=0&&System.nanoTime()<end)Thread.sleep(1);}
            ticket=null;}scope.cancel();check(queue.activeFiles()==0&&queue.reservedBytes()==0&&queue.unfinishedReads()==0);}
        public void close()throws Exception {try{if(ticket!=null)retire();}finally{queue.close();check(queue.awaitStopped(5000));Runnable task;while((task=ui.poll())!=null)task.run();check(queue.unfinishedDeliveries()==0);}
            Files.delete(root.resolve(MdoBrowserUploadFiles.OWNER_FILE));Files.delete(root);}
    }
    static void expected(Fixture f,Gate g,MdoBrowserPrivatePicker.Lease lease)throws Exception {g.picker().expect(lease,NONCE,false);check(g.picker().manifest(lease)[0]==f.reader.file);check(g.picker().remaining(lease)<=120000);}
    static void success(Fixture f)throws Exception {
        f.prepare(131075);Gate g=new Gate(f.context,f.scope.navigation);MdoBrowserPrivatePicker.Lease lease=g.begin(f,120000);expected(f,g,lease);
        Reply reply=new Reply();Params params=new Params();check(g.target.onShowFileChooser(g.view,reply,params));check(reply.calls==0&&g.proof.get()!=null&&g.picker().pending());
        params.fail=true;check(g.target.onShowFileChooser(g.view,reply,params)&&reply.calls==0);g.proof.get().done(true);
        check(reply.calls==1&&reply.files!=null&&reply.files.length==1&&g.completions.size()==1&&!g.picker().pending());
        MdoBrowserPrivatePicker.Result result=g.picker().status(lease);check(result.ok&&result.filesGranted&&result.callbackAnswered&&result.cleanupConfirmed&&result.grant!=null);
        g.proof.get().done(true);check(reply.calls==1&&g.completions.size()==1);f.consume(reply.files[0]);
        // Neither another operation nor another native gate may steal/revoke an
        // already granted original read lease, even with equal numeric epochs.
        refused("upload_picker_files_claimed",()->g.begin(f,1000));check(f.reader.live(SystemClock.now));
        Gate foreignGate=new Gate(f.context,f.scope.navigation);refused("upload_picker_files_claimed",()->foreignGate.begin(f,1000));foreignGate.close();f.consume(reply.files[0]);
        // A partial multi-file claim rolls back only its own fresh permissions.
        String extra=f.stage("fresh.bin",new byte[]{1,2,3});MdoBrowserUploadStore.ReadLease fresh=f.call(MdoBrowserUploadQueue.Request.access(extra,0)).reader;
        refused("upload_picker_files_claimed",()->g.picker().begin(f.queue,f.scope,new MdoBrowserUploadStore.ReadLease[]{fresh,f.reader},()->true,done->{},1000,value->{}));
        check(!fresh.live(SystemClock.now)&&f.reader.live(SystemClock.now));check(f.call(MdoBrowserUploadQueue.Request.ticket("cancel",extra)).value.cleanupConfirmed);f.consume(reply.files[0]);
        // Stop between publication of the first and second native URI. Only
        // the newly claimed permissions may be revoked; the older grant lives.
        String one=f.stage("one.bin",new byte[]{1}),two=f.stage("two.bin",new byte[]{2});
        MdoBrowserUploadStore.ReadLease r1=f.call(MdoBrowserUploadQueue.Request.access(one,0)).reader,r2=f.call(MdoBrowserUploadQueue.Request.access(two,0)).reader;
        Gate partial=new Gate(f.context,f.scope.navigation);MdoBrowserPrivatePicker.Lease partialLease=partial.picker().begin(f.queue,f.scope,new MdoBrowserUploadStore.ReadLease[]{r1,r2},()->partial.current,partial.proof::set,1000,partial.completions::add);
        partial.picker().expect(partialLease,NONCE,true);Reply partialReply=new Reply();MdoBrowserPrivatePickerReply nativeReply=new MdoBrowserPrivatePickerReply(f.context,partialReply);int[] fences={0};
        check(partial.picker().open(partial.view,partialReply,(files,current)->nativeReply.send(files,()->{if(++fences[0]==2)partial.current=false;return current.getAsBoolean();}),1,new String[]{NONCE},false));
        partial.proof.get().done(true);MdoBrowserPrivatePicker.Result partialResult=partial.picker().status(partialLease);
        check(partialReply.calls==1&&partialReply.files==null&&partialResult.callbackAnswered&&!partialResult.effectMayHaveOccurred&&partialResult.cleanupConfirmed);
        check(!r1.live(SystemClock.now)&&!r2.live(SystemClock.now)&&f.reader.live(SystemClock.now));check(MdoBrowserUploadUriRuntime.existing(f.context).grants()==1);partial.close();f.consume(reply.files[0]);
        check(f.call(MdoBrowserUploadQueue.Request.ticket("cancel",one)).value.cleanupConfirmed);check(f.call(MdoBrowserUploadQueue.Request.ticket("cancel",two)).value.cleanupConfirmed);
        g.close();check(!f.reader.live(SystemClock.now)&&f.provider.query(reply.files[0],null,null,null,null)==null);check(g.target.onShowFileChooser(g.view,reply,params)==false);f.retire();
    }
    static void mismatchAndCancellation(Fixture f)throws Exception {
        for(String mode:new String[]{"nonce","extra","mode","capture","metadata","null-params","validation","owner","navigation","scope","expiry","clock","queue"}){
            f.prepare(7);Gate g=new Gate(f.context,f.scope.navigation);MdoBrowserPrivatePicker.Lease lease=g.begin(f,1000);expected(f,g,lease);
            Reply reply=new Reply();Params params=new Params();if(mode.equals("nonce"))params.types=new String[]{NONCE+"x"};if(mode.equals("extra"))params.types=new String[]{NONCE,"text/plain"};
            if(mode.equals("mode"))params.mode=1;if(mode.equals("capture"))params.capture=true;if(mode.equals("metadata"))params.fail=true;
            check(g.target.onShowFileChooser(g.view,reply,mode.equals("null-params")?null:params));
            if(reply.calls==0){
                if(mode.equals("owner"))g.current=false;if(mode.equals("navigation"))g.epoch++;if(mode.equals("scope"))f.scope.cancel();
                if(mode.equals("expiry"))SystemClock.now=2000;if(mode.equals("clock")){SystemClock.now=1500;check(g.picker().status(lease)==null);SystemClock.now=1400;}
                if(mode.equals("queue"))g.owned=false;g.proof.get().done(!mode.equals("validation"));
            }
            check(reply.calls==1&&reply.files==null&&!f.reader.live(SystemClock.now)&&g.completions.size()==1&&!g.picker().pending());
            check(!g.picker().status(lease).ok);if(g.proof.get()!=null)g.proof.get().done(true);check(reply.calls==1);
            check(g.target.onShowFileChooser(g.view,reply,new Params())&&reply.calls==1);g.close();f.retire();
        }
    }
    static void duplicateAndLate(Fixture f)throws Exception {
        f.prepare(9);Gate g=new Gate(f.context,f.scope.navigation);MdoBrowserPrivatePicker.Lease lease=g.begin(f,5000);expected(f,g,lease);
        Reply first=new Reply(),second=new Reply();check(!g.target.onShowFileChooser(new WebView(f.context),first,new Params())&&first.calls==0);
        check(g.target.onShowFileChooser(g.view,first,new Params())&&first.calls==0);check(g.target.onShowFileChooser(g.view,second,new Params()));
        check(first.calls==1&&first.files==null&&second.calls==1&&second.files==null);check(g.picker().status(lease).code.equals("upload_picker_callback_ambiguous"));
        g.proof.get().done(true);check(first.calls==1&&second.calls==1);Reply late=new Reply();check(g.target.onShowFileChooser(g.view,late,new Params())&&late.calls==1&&late.files==null);
        g.target.close();Reply closed=new Reply();check(g.target.onShowFileChooser(g.view,closed,new Params())&&closed.calls==1&&closed.files==null);
        check(g.target.onShowFileChooser(g.view,closed,new Params())&&closed.calls==1);g.target.viewDestroyed();f.retire();
    }
    static void reentryAndFailure(Fixture f)throws Exception {
        for(String fault:new String[]{"response","oom","reenter-close","reenter-cancel","reenter-destroy","completion","publish","timer","verify","verify-after-success"}){
            f.prepare(11);Gate g=new Gate(f.context,f.scope.navigation);AtomicReference<MdoBrowserPrivatePicker.Checked> proof=g.proof;
            MdoBrowserPrivatePicker.Lease lease=g.picker().begin(f.queue,f.scope,new MdoBrowserUploadStore.ReadLease[]{f.reader},()->g.current,
                done->{if(fault.equals("verify"))throw new IllegalStateException("Synthetic verifier failure");if(fault.equals("verify-after-success")){done.done(true);throw new OutOfMemoryError("Synthetic late verifier failure");}proof.set(done);},1000,
                result->{g.completions.add(result);if(fault.equals("completion"))throw new OutOfMemoryError("Synthetic completion failure");});expected(f,g,lease);
            Reply reply=new Reply();reply.fail=fault.equals("response");reply.oom=fault.equals("oom");
            if(fault.equals("reenter-close"))reply.hook=g.target::close;if(fault.equals("reenter-cancel"))reply.hook=()->g.picker().cancel(lease);
            if(fault.equals("reenter-destroy"))reply.hook=g::close;
            check(g.target.onShowFileChooser(g.view,reply,new Params()));
            if(fault.equals("publish"))f.context.name="invalid.context";
            if(fault.equals("timer")){SystemClock.now=2000;for(Runnable timer:new ArrayList<>(Handler.tasks.keySet()))timer.run();}
            else if(proof.get()!=null)proof.get().done(true);
            f.context.name="org.xleaves.mdo.browseruse.dev";
            check(reply.calls==1&&!f.reader.live(SystemClock.now)&&g.completions.size()==1);
            if(!fault.equals("reenter-destroy"))check(!g.picker().status(lease).ok);
            if(fault.equals("response")||fault.equals("oom")||fault.equals("completion"))check(!g.picker().clean());
            if(fault.equals("publish")){check(g.picker().clean());MdoBrowserPrivatePicker.Result result=g.picker().status(lease);check(result.callbackAnswered&&!result.effectMayHaveOccurred&&result.cleanupConfirmed&&result.code.equals("upload_picker_grant_unconfirmed"));}
            if(fault.equals("verify-after-success"))check(reply.files==null);
            check(g.target.onShowFileChooser(g.view,reply,new Params())==!fault.equals("reenter-destroy")&&reply.calls==1);g.close();f.retire();
        }
    }
    static void admission(Fixture f)throws Exception {
        f.prepare(0);Gate g=new Gate(f.context,f.scope.navigation);
        refused("upload_picker_files_invalid",()->g.picker().begin(f.queue,new MdoBrowserUploadStore.Scope(f.scope.navigation),new MdoBrowserUploadStore.ReadLease[]{f.reader},()->true,done->{},1000,value->{}));
        MdoBrowserPrivatePicker.Lease lease=g.begin(f,1000);expected(f,g,lease);long deadline=lease.expires;SystemClock.now=1100;g.picker().expect(lease,NONCE,false);check(lease.expires==deadline);
        refused("upload_picker_busy",()->g.begin(f,1000));refused("upload_picker_identity_conflict",()->g.picker().expect(lease,NONCE.substring(0,NONCE.length()-1)+"2",false));
        g.picker().cancel();check(g.completions.size()==1&&!g.picker().status(lease).ok);Reply ordinary=new Reply();Params normal=new Params();normal.types=new String[]{"text/plain"};check(!g.target.onShowFileChooser(g.view,ordinary,normal)&&ordinary.calls==0);
        g.close();f.retire();
    }
    static void cancellationAcknowledgement(Fixture f)throws Exception {
        for(String fault:new String[]{"metadata-throw","metadata-reentrant-throw","cancel-reentrant-throw","cancel-reentrant"}){
            f.prepare(1);Gate g=new Gate(f.context,f.scope.navigation);MdoBrowserPrivatePicker.Lease lease=g.begin(f,1000);expected(f,g,lease);
            Reply reply=new Reply();reply.fail=!fault.equals("cancel-reentrant");Params params=new Params();params.fail=fault.startsWith("metadata");
            if(fault.contains("reentrant"))reply.hook=()->{check(g.completions.size()==0);g.target.close();check(g.completions.size()==0);};
            check(g.target.onShowFileChooser(g.view,reply,params));
            if(fault.startsWith("cancel"))g.picker().cancel();
            check(reply.calls==1&&reply.files==null&&g.completions.size()==1&&!f.reader.live(SystemClock.now));
            MdoBrowserPrivatePicker.Result result=g.completions.get(0);check(result.cleanupConfirmed==fault.equals("cancel-reentrant"));check(result.callbackAnswered==fault.equals("cancel-reentrant"));
            if(g.proof.get()!=null)g.proof.get().done(true);check(reply.calls==1);g.close();f.retire();
        }
    }
    static void threadsAndBounds(Fixture f)throws Exception {
        f.prepare(1);Gate g=new Gate(f.context,f.scope.navigation);MdoBrowserPrivatePicker.Lease lease=g.begin(f,1000);expected(f,g,lease);
        Reply reply=new Reply();check(g.target.onShowFileChooser(g.view,reply,new Params()));AtomicReference<Throwable> error=new AtomicReference<>();
        Thread foreign=new Thread(()->{try{g.proof.get().done(true);}catch(Throwable failure){error.set(failure);}});foreign.start();foreign.join(5000);
        check(error.get() instanceof IllegalStateException&&reply.calls==0);g.picker().cancel();g.proof.get().done(true);check(reply.calls==1&&reply.files==null);g.close();f.retire();
        // Retired operation quota is per original native view, not reset by
        // cancelling callbacks, changing source tickets or reusing a nonce.
        Gate bounded=new Gate(f.context,f.epoch+1);
        for(int i=0;i<64;i++){
            f.prepare(0);bounded.epoch=f.scope.navigation;MdoBrowserPrivatePicker.Lease value=bounded.begin(f,1000);expected(f,bounded,value);bounded.picker().cancel(value);f.retire();
        }
        f.prepare(0);bounded.epoch=f.scope.navigation;refused("upload_picker_resource_limit",()->bounded.begin(f,1000));check(f.reader.live(SystemClock.now));bounded.close();f.retire();
        Gate callbacks=new Gate(f.context,f.epoch+1);List<Reply> retained=new ArrayList<>();
        for(int i=0;i<128;i++){Reply late=new Reply();retained.add(late);check(callbacks.target.onShowFileChooser(callbacks.view,late,new Params())&&late.calls==1&&late.files==null);}
        Reply excess=new Reply();check(!callbacks.target.onShowFileChooser(callbacks.view,excess,new Params())&&excess.calls==0&&!callbacks.picker().clean());
        for(Reply late:retained)check(callbacks.target.onShowFileChooser(callbacks.view,late,new Params())&&late.calls==1);callbacks.close();
    }
    public static void main(String[] args)throws Exception {
        Looper.ui=Thread.currentThread();Path root=Paths.get(args[0]);try(Fixture fixture=new Fixture(root)){success(fixture);mismatchAndCancellation(fixture);duplicateAndLate(fixture);reentryAndFailure(fixture);admission(fixture);cancellationAcknowledgement(fixture);threadsAndBounds(fixture);}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_file_queue_leases_provider_proxy\":true,\"original_chrome_callback\":true,\"android_types_doubled\":true,\"native_dom_verifier_doubled\":true,\"framework_verified\":false,\"uri_assignment_verified\":false}");
    }
}
