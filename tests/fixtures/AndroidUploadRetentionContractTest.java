package org.xserver.android;

import android.os.Looper;
import android.os.SystemClock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Native callback proof + real files/queue/provider/proxy. Android types and
 * renderer validation are doubles; actual DOM assignment is NOT qualified. */
public final class AndroidUploadRetentionContractTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Retention check "+checks+" failed");}
    static final class Prepared {
        final AndroidPrivatePickerContractTest.Gate gate;final AndroidPrivatePickerContractTest.Reply reply;
        final MdoBrowserPrivatePicker.Lease lease;final MdoBrowserUploadStore.PickerGrant grant;
        Prepared(AndroidPrivatePickerContractTest.Fixture f,int size)throws Exception {
            f.prepare(size);gate=new AndroidPrivatePickerContractTest.Gate(f.context,f.scope.navigation);lease=gate.begin(f,120000);
            AndroidPrivatePickerContractTest.expected(f,gate,lease);reply=new AndroidPrivatePickerContractTest.Reply();
            check(gate.target.onShowFileChooser(gate.view,reply,new AndroidPrivatePickerContractTest.Params()));gate.proof.get().done(true);
            MdoBrowserPrivatePicker.Result result=gate.picker().status(lease);check(result.ok&&result.callbackAnswered&&result.filesGranted&&result.grant!=null);grant=result.grant;
        }
    }
    static MdoBrowserUploadStore.Retention prepare(AndroidPrivatePickerContractTest.Fixture f,Prepared source,MdoBrowserUploadStore.RetentionOwner owner)throws Exception {
        MdoBrowserUploadQueue.Outcome result=f.call(MdoBrowserUploadQueue.Request.retain(source.grant,owner));
        check(result.ok()&&result.retention!=null&&result.retainedValue.provisional&&!result.retainedValue.retained);return result.retention;
    }
    static MdoBrowserUploadQueue.Outcome retained(AndroidPrivatePickerContractTest.Fixture f,MdoBrowserUploadStore.Retention value,boolean release)throws Exception {
        return f.call(MdoBrowserUploadQueue.Request.retained(value,release));
    }
    static void release(AndroidPrivatePickerContractTest.Fixture f,MdoBrowserUploadStore.Retention value)throws Exception {
        MdoBrowserUploadQueue.Outcome result=retained(f,value,true);check(result.ok()&&result.retainedValue.cleanupConfirmed&&!result.retainedValue.retained&&!value.adopt());
        check(f.queue.activeFiles()==0&&f.queue.reservedBytes()==0&&!f.reader.live(SystemClock.now));
    }
    static void inputAndExpiry(AndroidPrivatePickerContractTest.Fixture f)throws Exception {
        Prepared source=new Prepared(f,131075);MdoBrowserUploadStore.RetentionOwner owner=new MdoBrowserUploadStore.RetentionOwner();
        MdoBrowserUploadStore.Retention value=prepare(f,source,owner);check(value.adopt()&&value.adopt());
        MdoBrowserUploadQueue.Outcome cancel=f.call(MdoBrowserUploadQueue.Request.ticket("cancel",f.ticket));check(cancel.ok()&&cancel.value.retained&&!cancel.value.cleanupConfirmed);
        source.gate.picker().cancel(source.lease);check(f.reader.live(SystemClock.now));
        f.scope.cancel();SystemClock.now=121001;
        MdoBrowserUploadQueue.Outcome status=retained(f,value,false);check(status.ok()&&status.retainedValue.retained&&!status.retainedValue.provisional);
        check(f.queue.activeFiles()==1&&f.queue.reservedBytes()==f.data.length);f.consume(source.reply.files[0]);
        MdoBrowserUploadQueue.Outcome repeat=f.call(MdoBrowserUploadQueue.Request.retain(source.grant,owner));check(repeat.ok()&&repeat.retention==value&&repeat.retainedValue.retained);
        MdoBrowserUploadStore.RetentionOwner other=new MdoBrowserUploadStore.RetentionOwner();
        MdoBrowserUploadQueue.Outcome refused=f.call(MdoBrowserUploadQueue.Request.retain(source.grant,other));check(!refused.ok()&&refused.code.equals("upload_retention_identity_conflict")&&f.reader.live(SystemClock.now));other.close();
        // Identical numeric epoch is not the exact source scope capability.
        AtomicReference<MdoBrowserUploadQueue.Outcome> wrong=new AtomicReference<>();MdoBrowserUploadStore.Scope alias=new MdoBrowserUploadStore.Scope(f.scope.navigation);
        check(f.queue.submit(alias,MdoBrowserUploadQueue.Request.retained(value,true),wrong::set));drain(f,wrong);check(wrong.get().code.equals("upload_retention_scope_invalid")&&f.reader.live(SystemClock.now));
        status.retainedValue.files[0]=null;check(retained(f,value,false).retainedValue.files[0]!=null);
        release(f,value);check(retained(f,value,false).retainedValue.cleanupConfirmed&&retained(f,value,true).retainedValue.cleanupConfirmed);
        check(f.provider.query(source.reply.files[0],null,null,null,null)==null);source.gate.close();owner.close();f.retire();
    }
    static void provisional(AndroidPrivatePickerContractTest.Fixture f)throws Exception {
        for(String mode:new String[]{"scope","expiry","owner","abandon","picker-cancel","picker-close"}){
            Prepared source=new Prepared(f,mode.equals("owner")?0:17);MdoBrowserUploadStore.RetentionOwner owner=new MdoBrowserUploadStore.RetentionOwner();
            MdoBrowserUploadStore.Retention value=prepare(f,source,owner);
            if(mode.equals("scope"))f.scope.cancel();if(mode.equals("expiry"))SystemClock.now=121000;if(mode.equals("owner"))owner.close();
            if(mode.equals("abandon"))value.abandon();if(mode.equals("picker-cancel"))source.gate.picker().cancel(source.lease);if(mode.equals("picker-close"))source.gate.target.close();
            check(!value.adopt());MdoBrowserUploadQueue.Outcome result=retained(f,value,false);check(result.ok()&&result.retainedValue.cleanupConfirmed);
            check(f.queue.activeFiles()==0&&f.queue.reservedBytes()==0&&!f.reader.live(SystemClock.now));check(f.provider.query(source.reply.files[0],null,null,null,null)==null);
            source.gate.close();owner.close();f.retire();
        }
    }
    static void lifecycle(AndroidPrivatePickerContractTest.Fixture f)throws Exception {
        for(String mode:new String[]{"cancel","close-input","view-destroyed","data-owner","explicit-source-release","abandon"}){
            Prepared source=new Prepared(f,31);MdoBrowserUploadStore.RetentionOwner owner=new MdoBrowserUploadStore.RetentionOwner();
            MdoBrowserUploadStore.Retention value=prepare(f,source,owner);check(value.adopt());
            if(mode.equals("cancel"))source.gate.picker().cancel(source.lease);if(mode.equals("close-input"))source.gate.target.close();
            if(mode.equals("view-destroyed"))source.gate.close();if(mode.equals("data-owner"))owner.close();if(mode.equals("abandon"))value.abandon();
            if(mode.equals("explicit-source-release"))check(f.call(MdoBrowserUploadQueue.Request.ticket("release",f.ticket)).value.cleanupConfirmed);
            boolean kept=mode.equals("cancel")||mode.equals("close-input")||mode.equals("abandon");
            MdoBrowserUploadQueue.Outcome state=retained(f,value,false);check(state.ok()&&state.retainedValue.retained==kept&&f.reader.live(SystemClock.now)==kept);
            if(kept){f.consume(source.reply.files[0]);release(f,value);}else check(state.retainedValue.cleanupConfirmed&&f.queue.activeFiles()==0);
            source.gate.close();owner.close();f.retire();
        }
    }
    static void drain(AndroidPrivatePickerContractTest.Fixture f,AtomicReference<MdoBrowserUploadQueue.Outcome> result)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(result.get()==null&&System.nanoTime()<end){Runnable task=f.ui.poll();if(task!=null)task.run();else Thread.sleep(1);}check(result.get()!=null);
    }
    static void delivery(AndroidPrivatePickerContractTest.Fixture f)throws Exception {
        for(String mode:new String[]{"throw-before-adopt","throw-after-adopt","post-refused","post-refused-late","duplicate-handoff-throws","status-throws"}){
            Prepared source=new Prepared(f,23);MdoBrowserUploadStore.RetentionOwner owner=new MdoBrowserUploadStore.RetentionOwner();
            AtomicReference<MdoBrowserUploadStore.Retention> permit=new AtomicReference<>();int[] calls={0};
            if(mode.startsWith("post-refused")){f.refusePost=true;f.latePost=mode.endsWith("late");}
            check(f.queue.submit(f.scope,MdoBrowserUploadQueue.Request.retain(source.grant,owner),result->{
                calls[0]++;permit.set(result.retention);check(result.ok());if(mode.equals("throw-after-adopt"))check(result.retention.adopt());
                if(mode.startsWith("throw-"))throw new IllegalStateException("Synthetic UI completion failure");
            }));
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while((f.queue.unfinishedDeliveries()!=0||!f.ui.isEmpty())&&System.nanoTime()<end){Runnable task=f.ui.poll();if(task!=null)task.run();else Thread.sleep(1);}
            check(f.queue.unfinishedDeliveries()==0);f.refusePost=false;f.latePost=false;
            boolean retained=mode.equals("throw-after-adopt");
            if(mode.startsWith("post-refused")){check(calls[0]==0&&permit.get()==null);while(f.queue.activeFiles()!=0&&System.nanoTime()<end)Thread.sleep(1);check(f.queue.activeFiles()==0);}
            else{
                check(calls[0]==1&&permit.get()!=null);
                if(mode.equals("duplicate-handoff-throws")||mode.equals("status-throws")){
                    check(f.queue.submit(f.scope,mode.equals("status-throws")?MdoBrowserUploadQueue.Request.retained(permit.get(),false):MdoBrowserUploadQueue.Request.retain(source.grant,owner),result->{throw new OutOfMemoryError("Synthetic observer failure");}));
                    end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(f.queue.unfinishedDeliveries()!=0&&System.nanoTime()<end){Runnable task=f.ui.poll();if(task!=null)task.run();else Thread.sleep(1);}
                    check(permit.get().adopt());retained=true;
                }
                if(retained){f.scope.cancel();SystemClock.now=121001;check(f.reader.live(SystemClock.now));f.consume(source.reply.files[0]);release(f,permit.get());}
                else check(retained(f,permit.get(),false).retainedValue.cleanupConfirmed);
            }
            source.gate.close();owner.close();f.retire();
        }
    }
    static void authority(AndroidPrivatePickerContractTest.Fixture f,Path base)throws Exception {
        Prepared source=new Prepared(f,7);MdoBrowserUploadStore.RetentionOwner owner=new MdoBrowserUploadStore.RetentionOwner();
        MdoBrowserUploadStore.Retention value=prepare(f,source,owner);check(value.adopt());
        try(AndroidPrivatePickerContractTest.Fixture foreign=new AndroidPrivatePickerContractTest.Fixture(base.resolveSibling("foreign-files"))){
            foreign.prepare(0);MdoBrowserUploadRuntime.queue=f.queue;AtomicReference<MdoBrowserUploadQueue.Outcome> result=new AtomicReference<>();
            check(foreign.queue.submit(f.scope,MdoBrowserUploadQueue.Request.retained(value,true),result::set));drain(foreign,result);check(result.get().code.equals("upload_retention_invalid")&&f.reader.live(SystemClock.now));
            result.set(null);check(foreign.queue.submit(f.scope,MdoBrowserUploadQueue.Request.retain(source.grant,owner),result::set));drain(foreign,result);check(result.get().code.equals("upload_read_lease_invalid")&&f.reader.live(SystemClock.now));
        }
        MdoBrowserUploadRuntime.queue=f.queue;f.consume(source.reply.files[0]);release(f,value);source.gate.close();owner.close();f.retire();
    }
    static void snapshotFailure(AndroidPrivatePickerContractTest.Fixture f)throws Exception {
        for(boolean adopted:new boolean[]{false,true}){
            Prepared source=new Prepared(f,19);MdoBrowserUploadStore.RetentionOwner owner=new MdoBrowserUploadStore.RetentionOwner();
            MdoBrowserUploadStore.Retention value=adopted?prepare(f,source,owner):null;if(value!=null)check(value.adopt());
            java.util.concurrent.atomic.AtomicBoolean fired=new java.util.concurrent.atomic.AtomicBoolean();
            // Fault at the real worker snapshot boundary after creation/lookup,
            // without exhausting the machine or adding production test hooks.
            f.clockHook.set(()->{for(StackTraceElement frame:Thread.currentThread().getStackTrace())if(frame.getClassName().equals("org.xserver.android.MdoBrowserUploadStore")&&frame.getMethodName().equals("retained")&&fired.compareAndSet(false,true))throw new OutOfMemoryError("Synthetic retained-result allocation failure");});
            MdoBrowserUploadQueue.Outcome failed=f.call(MdoBrowserUploadQueue.Request.retain(source.grant,owner));f.clockHook.set(null);
            check(fired.get()&&!failed.ok());
            if(adopted){check(f.reader.live(SystemClock.now));f.consume(source.reply.files[0]);release(f,value);}
            else{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(f.queue.activeFiles()!=0&&System.nanoTime()<end)Thread.sleep(1);check(f.queue.activeFiles()==0&&!f.reader.live(SystemClock.now));}
            source.gate.close();owner.close();f.retire();
        }
    }
    static void adoptionCancellationRace(AndroidPrivatePickerContractTest.Fixture f)throws Exception {
        for(int iteration=0;iteration<32;iteration++){
            Prepared source=new Prepared(f,1);MdoBrowserUploadStore.RetentionOwner owner=new MdoBrowserUploadStore.RetentionOwner();
            MdoBrowserUploadStore.Retention value=prepare(f,source,owner);
            java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
            AtomicReference<Throwable> failure=new AtomicReference<>();java.util.concurrent.atomic.AtomicBoolean adopted=new java.util.concurrent.atomic.AtomicBoolean();
            Thread adoption=new Thread(()->{try{start.await();adopted.set(value.adopt());}catch(Throwable error){failure.set(error);}},"native-data-adoption");
            Thread cancellation=new Thread(()->{try{start.await();f.reader.revokeStaging();}catch(Throwable error){failure.set(error);}},"transient-picker-cancel");
            adoption.start();cancellation.start();start.countDown();adoption.join(5000);cancellation.join(5000);
            check(!adoption.isAlive()&&!cancellation.isAlive()&&failure.get()==null);
            // Cancellation may win before adoption. Once adoption wins, its
            // bytes must remain readable despite the competing input cleanup.
            check(f.reader.live(SystemClock.now)==adopted.get());
            if(adopted.get()){f.consume(source.reply.files[0]);release(f,value);}
            else check(retained(f,value,false).retainedValue.cleanupConfirmed);
            source.gate.close();owner.close();f.retire();
        }
    }
    static void multipleFiles(AndroidPrivatePickerContractTest.Fixture f)throws Exception {
        f.prepare(131075);byte[] original=f.data;
        check(f.call(MdoBrowserUploadQueue.Request.ticket("release",f.ticket)).value.cleanupConfirmed);
        MdoBrowserUploadStore.Spec[] specs={f.reader.file,new MdoBrowserUploadStore.Spec("empty.bin",0,AndroidPrivatePickerContractTest.Fixture.sha(new byte[0]))};
        MdoBrowserUploadQueue.Outcome started=f.call(MdoBrowserUploadQueue.Request.begin("multiple-native-files",specs,SystemClock.now+120000));
        check(started.ok());f.ticket=started.value.ticket;
        for(int at=0;at<original.length;at+=65536)check(f.call(MdoBrowserUploadQueue.Request.chunk(f.ticket,0,at,java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(original,at,Math.min(original.length,at+65536))))).ok());
        check(f.call(MdoBrowserUploadQueue.Request.ticket("seal",f.ticket)).value.sealed);
        MdoBrowserUploadStore.ReadLease first=f.call(MdoBrowserUploadQueue.Request.access(f.ticket,0)).reader,second=f.call(MdoBrowserUploadQueue.Request.access(f.ticket,1)).reader;
        AndroidPrivatePickerContractTest.Gate gate=new AndroidPrivatePickerContractTest.Gate(f.context,f.scope.navigation);
        MdoBrowserPrivatePicker.Lease lease=gate.picker().begin(f.queue,f.scope,new MdoBrowserUploadStore.ReadLease[]{first,second},()->gate.current,gate.proof::set,120000,gate.completions::add);
        gate.picker().expect(lease,AndroidPrivatePickerContractTest.NONCE,true);
        AndroidPrivatePickerContractTest.Reply reply=new AndroidPrivatePickerContractTest.Reply();AndroidPrivatePickerContractTest.Params params=new AndroidPrivatePickerContractTest.Params();params.mode=1;
        check(gate.target.onShowFileChooser(gate.view,reply,params));gate.proof.get().done(true);
        MdoBrowserPrivatePicker.Result granted=gate.picker().status(lease);check(granted.ok&&granted.grant!=null&&reply.files.length==2);
        MdoBrowserUploadStore.RetentionOwner owner=new MdoBrowserUploadStore.RetentionOwner();MdoBrowserUploadQueue.Outcome kept=f.call(MdoBrowserUploadQueue.Request.retain(granted.grant,owner));
        check(kept.ok()&&kept.retainedValue.files.length==2&&kept.retention.adopt());
        gate.target.close();f.scope.cancel();SystemClock.now=121001;
        check(first.live(SystemClock.now)&&second.live(SystemClock.now)&&f.queue.activeFiles()==2&&f.queue.reservedBytes()==original.length);
        f.reader=first;f.data=original;f.consume(reply.files[0]);f.reader=second;f.data=new byte[0];f.consume(reply.files[1]);
        release(f,kept.retention);check(!first.live(SystemClock.now)&&!second.live(SystemClock.now));gate.close();owner.close();f.retire();
    }
    public static void main(String[] args)throws Exception {
        Looper.ui=Thread.currentThread();Path root=Paths.get(args[0]);try(AndroidPrivatePickerContractTest.Fixture fixture=new AndroidPrivatePickerContractTest.Fixture(root)){
            inputAndExpiry(fixture);provisional(fixture);lifecycle(fixture);delivery(fixture);authority(fixture,root);snapshotFailure(fixture);adoptionCancellationRace(fixture);multipleFiles(fixture);
        }
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"file_queue_provider_proxy_real\":true,\"picker_grant_from_original_callback\":true,\"staging_expiry_and_input_cancel_survived\":true,\"explicit_release_and_browser_teardown_verified\":true,\"framework_verified\":false,\"renderer_assignment_verified\":false}");
    }
}
