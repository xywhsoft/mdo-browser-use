package org.xserver.android;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class AndroidUploadQueueContractTest {
    interface Checked {void run()throws Exception;}
    static int checks;static Path base;static final List<Path> directories=new ArrayList<>();
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Upload queue check "+checks+" failed");}
    static void refused(String code,Checked action)throws Exception{
        try{action.run();throw new AssertionError("Expected "+code);}catch(MdoBrowserUploadFiles.Failure failure){check(code.equals(failure.code));}
    }
    static String sha(byte[] data)throws Exception{
        StringBuilder value=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))value.append(String.format("%02x",b&255));return value.toString();
    }
    static MdoBrowserUploadStore.Spec spec(String name,byte[] data)throws Exception{return new MdoBrowserUploadStore.Spec(name,data.length,sha(data));}
    static Path directory(String name)throws Exception{Path root=base.resolve(name);Files.createDirectory(root);directories.add(root);return root;}
    static void waitFor(java.util.function.BooleanSupplier test)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!test.getAsBoolean()){if(System.nanoTime()>=deadline)throw new AssertionError("Upload wait timed out");Thread.sleep(10);}
    }
    static void ownerLease()throws Exception{
        Path root=directory("lease");
        try(MdoBrowserUploadFiles first=new MdoBrowserUploadFiles(root)){
            first.acquireCacheLease();try(MdoBrowserUploadFiles second=new MdoBrowserUploadFiles(root)){refused("upload_cache_busy",second::acquireCacheLease);}
        }
        try(MdoBrowserUploadFiles next=new MdoBrowserUploadFiles(root)){next.acquireCacheLease();check(next.reservations()==0);}
        Path marker=root.resolve(MdoBrowserUploadFiles.OWNER_FILE);check(new String(Files.readAllBytes(marker),java.nio.charset.StandardCharsets.US_ASCII).equals("mdo-browser-use-private-upload-cache-v1\n"));Files.delete(marker);
        Files.write(marker,new byte[]{1});try(MdoBrowserUploadFiles bad=new MdoBrowserUploadFiles(root)){refused("upload_cache_owner_invalid",bad::acquireCacheLease);}
        check(Arrays.equals(Files.readAllBytes(marker),new byte[]{1}));Files.delete(marker);
        Path foreign=root.resolve("unrecognized");Files.write(foreign,new byte[]{2});
        try(MdoBrowserUploadFiles bad=new MdoBrowserUploadFiles(root)){refused("upload_cache_owner_invalid",bad::acquireCacheLease);}
        check(Files.readAllBytes(foreign)[0]==2&&!Files.exists(marker));Files.delete(foreign);
    }
    static void tickets()throws Exception{
        Path root=directory("tickets");AtomicLong now=new AtomicLong(1000);byte[] data={1,2,3};
        try(MdoBrowserUploadStore store=new MdoBrowserUploadStore(new MdoBrowserUploadFiles(root),now::get)){
            MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(9),foreign=new MdoBrowserUploadStore.Scope(9);
            MdoBrowserUploadStore.Spec[] specs={spec("\u6587\u6863\ud83e\udd89.txt",data),spec("empty",new byte[0])};
            MdoBrowserUploadStore.Snapshot created=store.begin(scope,"original",specs,6000);check(created.ticket.startsWith("u-")&&created.navigation==9&&store.reservations()==2&&created.size==3);
            now.set(2000);check(store.begin(scope,"original",specs,12000).ticket.equals(created.ticket));check(store.status(scope,created.ticket,now.get()).expires==6000);
            refused("upload_identity_conflict",()->store.begin(scope,"original",new MdoBrowserUploadStore.Spec[]{spec("other",data)},6000));
            refused("upload_ticket_not_found",()->store.cancel(foreign,created.ticket));refused("upload_ticket_not_found",()->store.status(foreign,created.ticket,now.get()));
            refused("upload_incomplete",()->store.seal(scope,created.ticket));check(store.reservations()==2);
            MdoBrowserUploadStore.Snapshot partial=store.chunk(scope,created.ticket,0,0,data);check(partial.received[0]==3);
            check(store.chunk(scope,created.ticket,0,0,data).received[0]==3);refused("upload_chunk_conflict",()->store.chunk(scope,created.ticket,0,0,new byte[]{3,2,1}));
            check(store.seal(scope,created.ticket).sealed);refused("upload_already_sealed",()->store.chunk(scope,created.ticket,0,0,data));
            MdoBrowserUploadStore.Snapshot done=store.cancel(scope,created.ticket);check(done.cleanupConfirmed&&done.state.equals("released")&&!done.sealed&&store.reservedBytes()==0);
            check(store.cancel(scope,created.ticket).cleanupConfirmed);refused("upload_operation_retired",()->store.begin(scope,"original",specs,12000));
            MdoBrowserUploadStore.Snapshot expiring=store.begin(scope,"expire",new MdoBrowserUploadStore.Spec[]{spec("empty",new byte[0])},3000);
            now.set(3000);refused("upload_expired",()->store.seal(scope,expiring.ticket));check(store.status(scope,expiring.ticket,now.get()).cleanupConfirmed&&store.reservations()==0);
            MdoBrowserUploadStore.Snapshot cancelled=store.begin(scope,"cancel",new MdoBrowserUploadStore.Spec[]{spec("empty",new byte[0])},6000);scope.cancel();
            store.collect(now.get());check(store.reservations()==0);refused("upload_ticket_not_found",()->store.status(scope,cancelled.ticket,now.get()));
            refused("upload_owner_changed",()->store.begin(scope,"changed",specs,6000));
        }
    }
    static void limitsAndBadBytes()throws Exception{
        Path root=directory("limits");AtomicLong now=new AtomicLong(1);String empty=sha(new byte[0]);
        try(MdoBrowserUploadStore store=new MdoBrowserUploadStore(new MdoBrowserUploadFiles(root),now::get)){
            MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(1);
            refused("upload_metadata_invalid",()->new MdoBrowserUploadStore.Spec("../outside",0,empty));
            refused("upload_metadata_invalid",()->new MdoBrowserUploadStore.Spec("CON.txt",0,empty));
            refused("upload_metadata_invalid",()->new MdoBrowserUploadStore.Spec("bad\ud800",0,empty));
            MdoBrowserUploadStore.Spec huge=new MdoBrowserUploadStore.Spec("large",33554432L,empty);
            refused("upload_budget_exceeded",()->store.begin(scope,"too-large",new MdoBrowserUploadStore.Spec[]{huge,huge,huge},5000));check(store.reservations()==0);
            MdoBrowserUploadStore.Snapshot bad=store.begin(scope,"bad-bytes",new MdoBrowserUploadStore.Spec[]{spec("bad",new byte[]{1})},5000);
            store.chunk(scope,bad.ticket,0,0,new byte[]{2});refused("upload_hash_mismatch",()->store.seal(scope,bad.ticket));check(store.reservations()==0&&store.status(scope,bad.ticket,now.get()).cleanupConfirmed);
            // Retired admissions remain fenced for the original scope. Count
            // the one failed admission above as part of the 64 bounded records.
            for(int at=1;at<64;at++){
                MdoBrowserUploadStore.Snapshot item=store.begin(scope,"empty-"+at,new MdoBrowserUploadStore.Spec[]{spec("empty",new byte[0])},5000);store.cancel(scope,item.ticket);
            }
            refused("upload_operation_limit",()->store.begin(scope,"overflow",new MdoBrowserUploadStore.Spec[]{spec("empty",new byte[0])},5000));
            scope.cancel();store.collect(now.get());check(store.reservations()==0);
            MdoBrowserUploadStore.Scope next=new MdoBrowserUploadStore.Scope(2);
            check(store.begin(next,"new-document",new MdoBrowserUploadStore.Spec[]{spec("empty",new byte[0])},5000).navigation==2);
        }
    }
    static final class Main implements MdoBrowserUploadQueue.Ui {
        final Thread thread=Thread.currentThread();final ConcurrentLinkedQueue<Runnable> callbacks=new ConcurrentLinkedQueue<>();boolean refuse;
        public boolean post(Runnable task){if(refuse)return false;callbacks.add(task);return true;}
        void drain(){check(Thread.currentThread()==thread);Runnable task;while((task=callbacks.poll())!=null)task.run();}
    }
    static MdoBrowserUploadQueue.Outcome call(MdoBrowserUploadQueue queue,Main ui,MdoBrowserUploadStore.Scope scope,MdoBrowserUploadQueue.Request request)throws Exception{
        AtomicReference<MdoBrowserUploadQueue.Outcome> outcome=new AtomicReference<>();check(queue.submit(scope,request,outcome::set));
        waitFor(()->!ui.callbacks.isEmpty());ui.drain();check(outcome.get()!=null);return outcome.get();
    }
    static void queueFlow()throws Exception{
        Path root=directory("queue");AtomicLong now=new AtomicLong(1000);AtomicReference<Thread> factoryThread=new AtomicReference<>();Main ui=new Main();AtomicInteger opened=new AtomicInteger();
        MdoBrowserUploadQueue queue=new MdoBrowserUploadQueue(()->{opened.incrementAndGet();factoryThread.set(Thread.currentThread());return new MdoBrowserUploadFiles(root);},ui,now::get);
        MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(1);
        try{
            MdoBrowserUploadQueue.Outcome begin=call(queue,ui,scope,MdoBrowserUploadQueue.Request.begin("queue",new MdoBrowserUploadStore.Spec[]{spec("one",new byte[]{0})},3000));
            check(begin.ok()&&opened.get()==1&&factoryThread.get()!=Thread.currentThread());String id=begin.value.ticket;
            MdoBrowserUploadQueue.Outcome invalid=call(queue,ui,scope,MdoBrowserUploadQueue.Request.chunk(id,0,0,"AB=="));check(invalid.code.equals("upload_chunk_invalid"));
            check(call(queue,ui,scope,MdoBrowserUploadQueue.Request.chunk(id,0,0,"AA==")).value.received[0]==1);
            check(call(queue,ui,scope,MdoBrowserUploadQueue.Request.ticket("seal",id)).value.sealed);
            check(call(queue,ui,scope,MdoBrowserUploadQueue.Request.ticket("release",id)).value.cleanupConfirmed);
            check(queue.activeFiles()==0&&queue.reservedBytes()==0);
        }finally{scope.cancel();queue.close();check(queue.awaitStopped(5000));ui.drain();check(queue.unfinishedDeliveries()==0);}
    }
    static void inboxLimit()throws Exception{
        Path root=directory("inbox");Main ui=new Main();AtomicInteger opens=new AtomicInteger();
        MdoBrowserUploadQueue queue=new MdoBrowserUploadQueue(()->{opens.incrementAndGet();return new MdoBrowserUploadFiles(root);},ui,()->1);
        MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(1);MdoBrowserUploadQueue.Request request=MdoBrowserUploadQueue.Request.ticket("status","u-00000000-0000-4000-8000-000000000000");
        try{
            for(int at=1;at<=9;at++){check(queue.submit(scope,request,value->{}));final int expected=at;waitFor(()->ui.callbacks.size()==expected);}
            check(queue.unfinishedDeliveries()==9&&!queue.submit(scope,request,value->{}));check(opens.get()==0);
            ui.drain();check(queue.unfinishedDeliveries()==0);check(call(queue,ui,scope,request).code.equals("upload_ticket_not_found"));
        }finally{queue.close();check(queue.awaitStopped(5000));ui.drain();}
    }
    static void cancellationAndLostReply()throws Exception{
        Path root=directory("cancel-queue");AtomicLong now=new AtomicLong(1000);Main ui=new Main();CountDownLatch started=new CountDownLatch(1),continueOpen=new CountDownLatch(1);
        MdoBrowserUploadQueue queue=new MdoBrowserUploadQueue(()->{
            started.countDown();try{if(!continueOpen.await(5,TimeUnit.SECONDS))throw new IOException("Synthetic factory wait timed out");}catch(InterruptedException failure){throw new IOException(failure);}
            return new MdoBrowserUploadFiles(root);
        },ui,now::get);
        MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(1);AtomicReference<MdoBrowserUploadQueue.Outcome> outcome=new AtomicReference<>();
        try{
            check(queue.submit(scope,MdoBrowserUploadQueue.Request.begin("cancel",new MdoBrowserUploadStore.Spec[]{spec("empty",new byte[0])},2000),outcome::set));
            check(started.await(5,TimeUnit.SECONDS));scope.cancel();continueOpen.countDown();waitFor(()->!ui.callbacks.isEmpty());ui.drain();
            check(outcome.get().code.equals("upload_owner_changed")&&queue.activeFiles()==0);
            MdoBrowserUploadStore.Scope next=new MdoBrowserUploadStore.Scope(2);ui.refuse=true;
            check(queue.submit(next,MdoBrowserUploadQueue.Request.begin("lost",new MdoBrowserUploadStore.Spec[]{spec("empty",new byte[0])},2000),value->{}));
            waitFor(()->queue.activeFiles()==1&&queue.unfinishedDeliveries()==0);now.set(3000);waitFor(()->queue.activeFiles()==0);
            check(queue.reservedBytes()==0);next.cancel();
        }finally{continueOpen.countDown();queue.close();check(queue.awaitStopped(5000));ui.drain();}
    }
    public static void main(String[] args)throws Exception{
        base=Paths.get(args[0]);Files.createDirectory(base);Throwable primary=null;
        try{ownerLease();tickets();limitsAndBadBytes();queueFlow();inboxLimit();cancellationAndLostReply();}
        catch(Exception|Error failure){primary=failure;throw failure;}
        finally{
            try{for(int at=directories.size()-1;at>=0;at--)Files.delete(directories.get(at));Files.delete(base);}
            catch(Exception cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false,\"native_cache_authority_verified\":false,\"uri_assignment_verified\":false}");
    }
}
