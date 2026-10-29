package org.xserver.android;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Real worker/files/leases. Only main-delivery scheduling is doubled; no
 * Android provider, URI, chooser or framework qualification is inferred. */
public final class AndroidUploadReadContractTest {
    interface Checked {void run()throws Exception;}
    private static int checks;private static Path base;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Native read check "+checks+" failed");}
    private static void refused(String code,Checked action)throws Exception {
        try{action.run();throw new AssertionError("Expected "+code);}catch(MdoBrowserUploadFiles.Failure failure){check(code.equals(failure.code));}
    }
    private static String sha(byte[] bytes)throws Exception {
        StringBuilder text=new StringBuilder(64);for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return text.toString();
    }
    private static void await(java.util.function.BooleanSupplier ready)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!ready.getAsBoolean()){if(System.nanoTime()>=deadline)throw new AssertionError("Native read wait expired");Thread.sleep(5);}
    }
    private static final class Clock implements LongSupplier {
        volatile long now=1000;volatile boolean fatalAfterHold;volatile CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1);final AtomicBoolean armed=new AtomicBoolean();
        void hold(){entered=new CountDownLatch(1);resume=new CountDownLatch(1);armed.set(true);}
        public long getAsLong(){
            if(Thread.currentThread().getName().equals("mdo-browser-upload")&&armed.compareAndSet(true,false)){
                entered.countDown();try{if(!resume.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic worker hold expired");}
                catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("Synthetic worker hold interrupted");}
                if(fatalAfterHold){fatalAfterHold=false;throw new AssertionError("Synthetic fatal worker read");}
            }return now;
        }
    }
    private static final class Fixture implements AutoCloseable {
        final Path root;final Clock clock=new Clock();final ConcurrentLinkedQueue<Runnable> ui=new ConcurrentLinkedQueue<>();
        final AtomicBoolean blockingAllowed=new AtomicBoolean(true);
        final MdoBrowserUploadQueue queue;final MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(1);volatile boolean refuseUi;
        String ticket;MdoBrowserUploadStore.ReadLease reader;
        Fixture(String name)throws Exception {
            root=base.resolve(name);Files.createDirectory(root);AtomicReference<MdoBrowserUploadQueue> original=new AtomicReference<>();
            queue=new MdoBrowserUploadQueue(()->{
                try{original.get().read(null,0,1,10);throw new AssertionError("Worker self-wait admitted");}
                catch(MdoBrowserUploadFiles.Failure failure){if(!failure.code.equals("upload_read_thread_invalid"))throw failure;}
                MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root);
                try{files.acquireCacheLease();return files;}catch(IOException|RuntimeException|Error failure){try{files.close();}catch(IOException ignored){}throw failure;}
            },task->{if(refuseUi)return false;ui.add(task);return true;},clock,blockingAllowed::get);original.set(queue);
        }
        MdoBrowserUploadQueue.Outcome call(MdoBrowserUploadQueue.Request request)throws Exception {
            AtomicReference<MdoBrowserUploadQueue.Outcome> result=new AtomicReference<>();check(queue.submit(scope,request,result::set));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(result.get()==null&&System.nanoTime()<deadline){Runnable task=ui.poll();if(task!=null)task.run();else Thread.sleep(5);}
            check(result.get()!=null);return result.get();
        }
        void seal(byte[] bytes)throws Exception {
            MdoBrowserUploadStore.Spec[] specs={new MdoBrowserUploadStore.Spec("native-\u4e2d.bin",bytes.length,sha(bytes)),new MdoBrowserUploadStore.Spec("empty.bin",0,sha(new byte[0]))};
            MdoBrowserUploadQueue.Outcome started=call(MdoBrowserUploadQueue.Request.begin("native-read",specs,6000));check(started.ok());ticket=started.value.ticket;
            check(call(MdoBrowserUploadQueue.Request.access(ticket,0)).code.equals("upload_not_sealed"));
            for(int at=0;at<bytes.length;at+=65536)check(call(MdoBrowserUploadQueue.Request.chunk(ticket,0,at,Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,at,Math.min(bytes.length,at+65536))))).ok());
            check(call(MdoBrowserUploadQueue.Request.ticket("seal",ticket)).value.sealed);
        }
        void prepare(byte[] bytes)throws Exception {
            seal(bytes);
            MdoBrowserUploadQueue.Outcome access=call(MdoBrowserUploadQueue.Request.access(ticket,0));check(access.ok()&&access.reader!=null&&access.value==null&&!access.stagingMayHaveChanged);reader=access.reader;
        }
        @Override public void close()throws Exception {
            clock.resume.countDown();scope.cancel();queue.close();check(queue.awaitStopped(5000));Runnable task;while((task=ui.poll())!=null)task.run();
            check(queue.unfinishedReads()==0&&queue.unfinishedDeliveries()==0&&queue.activeFiles()==0&&queue.reservedBytes()==0&&!queue.cleanupFailed());
            Files.delete(root.resolve(MdoBrowserUploadFiles.OWNER_FILE));Files.delete(root);
        }
    }
    private static byte[] data(){byte[] bytes=new byte[65539];for(int at=0;at<bytes.length;at++)bytes[at]=(byte)(at*37+9);return bytes;}
    private static void rangesAndRelease()throws Exception {
        try(Fixture fixture=new Fixture("ranges")){
            byte[] bytes=data();fixture.prepare(bytes);MdoBrowserUploadStore.ReadLease lease=fixture.reader;
            check(lease.live(fixture.clock.now)&&lease.expires==6000&&lease.scope==fixture.scope&&lease.file.size==bytes.length&&lease.file.sha256.equals(sha(bytes)));
            check(fixture.call(MdoBrowserUploadQueue.Request.access(fixture.ticket,0)).reader==lease);
            check(fixture.call(MdoBrowserUploadQueue.Request.access(fixture.ticket,15)).code.equals("upload_range_invalid"));
            fixture.blockingAllowed.set(false);refused("upload_read_thread_invalid",()->fixture.queue.read(lease,0,1,1000));
            check(fixture.queue.unfinishedReads()==0);fixture.blockingAllowed.set(true);
            fixture.refuseUi=true;
            check(Arrays.equals(fixture.queue.read(lease,0,65536,5000),Arrays.copyOf(bytes,65536)));
            check(Arrays.equals(fixture.queue.read(lease,65536,65536,5000),Arrays.copyOfRange(bytes,65536,bytes.length)));
            check(fixture.queue.read(lease,bytes.length,1,5000).length==0&&fixture.ui.isEmpty()&&fixture.queue.unfinishedReads()==0);
            refused("upload_range_invalid",()->fixture.queue.read(lease,-1,1,100));refused("upload_range_invalid",()->fixture.queue.read(lease,bytes.length+1,1,100));
            refused("upload_range_invalid",()->fixture.queue.read(lease,0,65537,100));refused("upload_range_invalid",()->fixture.queue.read(lease,0,0,100));
            refused("upload_range_invalid",()->fixture.queue.read(lease,0,1,0));refused("upload_range_invalid",()->fixture.queue.read(lease,0,1,5001));
            check(lease.live(fixture.clock.now)&&fixture.queue.unfinishedReads()==0);fixture.refuseUi=false;
            MdoBrowserUploadStore.ReadLease empty=fixture.call(MdoBrowserUploadQueue.Request.access(fixture.ticket,1)).reader;
            check(empty!=null&&fixture.queue.read(empty,0,1,5000).length==0);
            check(fixture.call(MdoBrowserUploadQueue.Request.ticket("release",fixture.ticket)).value.cleanupConfirmed);
            check(!lease.live(fixture.clock.now)&&!empty.live(fixture.clock.now));refused("upload_read_lease_revoked",()->fixture.queue.read(lease,0,1,100));
        }
    }
    private static void foreignOwner()throws Exception {
        try(Fixture source=new Fixture("original");Fixture other=new Fixture("foreign")){
            source.prepare(new byte[]{1});other.prepare(new byte[]{2});
            refused("upload_read_lease_invalid",()->other.queue.read(source.reader,0,1,1000));
            check(other.queue.activeFiles()==2&&other.reader.live(other.clock.now)&&source.reader.live(source.clock.now));
            check(source.queue.read(source.reader,0,1,1000)[0]==1&&other.queue.read(other.reader,0,1,1000)[0]==2);
        }
    }
    private static void explicitRevocation()throws Exception {
        try(Fixture fixture=new Fixture("native-revoked")){
            fixture.prepare(new byte[]{3});fixture.reader.revoke();
            refused("upload_read_lease_revoked",()->fixture.queue.read(fixture.reader,0,1,1000));
            check(fixture.call(MdoBrowserUploadQueue.Request.access(fixture.ticket,0)).code.equals("upload_read_lease_revoked"));
            check(fixture.queue.activeFiles()==2&&!fixture.queue.cleanupFailed());
            check(fixture.call(MdoBrowserUploadQueue.Request.ticket("cancel",fixture.ticket)).value.cleanupConfirmed);
        }
    }
    private static void boundedTimeouts()throws Exception {
        try(Fixture fixture=new Fixture("timeouts")){
            fixture.prepare(new byte[]{7});fixture.clock.hold();
            for(int at=1;at<=4;at++){
                refused("upload_read_timeout",()->fixture.queue.read(fixture.reader,0,1,20));check(fixture.queue.unfinishedReads()==at);
            }
            check(fixture.clock.entered.await(1,TimeUnit.SECONDS));refused("upload_read_busy",()->fixture.queue.read(fixture.reader,0,1,100));
            check(fixture.queue.unfinishedReads()==4&&fixture.queue.unfinishedDeliveries()==0);
            fixture.clock.resume.countDown();await(()->fixture.queue.unfinishedReads()==0);
            check(fixture.queue.read(fixture.reader,0,1,1000)[0]==7&&fixture.reader.live(fixture.clock.now));
        }
    }
    private static void fullInboxAndStalledUi()throws Exception {
        try(Fixture fixture=new Fixture("full-inbox")){
            fixture.prepare(new byte[]{4});fixture.clock.hold();MdoBrowserUploadQueue.Request status=MdoBrowserUploadQueue.Request.ticket("status",fixture.ticket);
            check(fixture.queue.submit(fixture.scope,status,value->{}));check(fixture.clock.entered.await(2,TimeUnit.SECONDS));
            for(int at=0;at<8;at++)check(fixture.queue.submit(fixture.scope,status,value->{}));
            refused("upload_read_busy",()->fixture.queue.read(fixture.reader,0,1,1000));check(fixture.queue.unfinishedReads()==0&&fixture.queue.unfinishedDeliveries()==9);
            fixture.clock.resume.countDown();await(()->fixture.ui.size()==9);
            check(fixture.queue.read(fixture.reader,0,1,1000)[0]==4&&fixture.queue.unfinishedReads()==0&&fixture.queue.unfinishedDeliveries()==9);
            Runnable task;while((task=fixture.ui.poll())!=null)task.run();check(fixture.queue.unfinishedDeliveries()==0);
        }
    }
    private static void changedBeforeAccess()throws Exception {
        try(Fixture fixture=new Fixture("changed-before-access")){
            fixture.seal(new byte[]{5});Path path=null;try(DirectoryStream<Path> entries=Files.newDirectoryStream(fixture.root)){
                for(Path entry:entries)if(!entry.getFileName().toString().equals(MdoBrowserUploadFiles.OWNER_FILE)&&Files.size(entry)>MdoBrowserUploadFiles.HEADER_BYTES)path=entry;
            }check(path!=null);
            try(java.nio.channels.FileChannel channel=java.nio.channels.FileChannel.open(path,java.nio.file.StandardOpenOption.WRITE)){
                channel.position(MdoBrowserUploadFiles.HEADER_BYTES);channel.write(java.nio.ByteBuffer.wrap(new byte[]{6}));
            }
            MdoBrowserUploadQueue.Outcome result=fixture.call(MdoBrowserUploadQueue.Request.access(fixture.ticket,0));
            check(!result.ok()&&"upload_hash_mismatch".equals(result.code)&&result.reader==null);
            await(()->fixture.queue.activeFiles()==0);check(fixture.queue.reservedBytes()==0&&!fixture.queue.cleanupFailed());
        }
    }
    private static void cancellationAndExpiry(boolean expiry)throws Exception {
        try(Fixture fixture=new Fixture(expiry?"expiry":"scope-cancel")){
            fixture.prepare(new byte[]{8});fixture.clock.hold();AtomicReference<Throwable> error=new AtomicReference<>();
            Thread caller=new Thread(()->{try{fixture.queue.read(fixture.reader,0,1,4000);error.set(new AssertionError("Cancelled read returned bytes"));}catch(Throwable failure){error.set(failure);}},"native-proxy-fixture");caller.start();
            try{
                check(fixture.clock.entered.await(2,TimeUnit.SECONDS));if(expiry)fixture.clock.now=6000;else fixture.scope.cancel();
                check(!fixture.reader.live(fixture.clock.now));fixture.clock.resume.countDown();caller.join(5000);check(!caller.isAlive());
                check(error.get() instanceof MdoBrowserUploadFiles.Failure);
                String code=((MdoBrowserUploadFiles.Failure)error.get()).code;
                check(expiry?code.equals("upload_expired"):code.equals("upload_owner_changed")||code.equals("upload_read_lease_revoked"));
                await(()->fixture.queue.activeFiles()==0&&fixture.queue.unfinishedReads()==0);check(fixture.queue.reservedBytes()==0);
            }finally{fixture.clock.resume.countDown();caller.join(5000);if(caller.isAlive())throw new AssertionError("Owned native read caller still live");}
        }
    }
    private static void interruptedReader()throws Exception {
        try(Fixture fixture=new Fixture("interrupted")){
            fixture.prepare(new byte[]{9});fixture.clock.hold();AtomicReference<String> error=new AtomicReference<>();AtomicBoolean interrupted=new AtomicBoolean();
            Thread caller=new Thread(()->{try{fixture.queue.read(fixture.reader,0,1,4000);error.set("unexpected_success");}
                catch(MdoBrowserUploadFiles.Failure failure){error.set(failure.code);interrupted.set(Thread.currentThread().isInterrupted());}
                catch(IOException failure){error.set("unexpected_io");}},"native-proxy-interrupted");caller.start();
            try{
                check(fixture.clock.entered.await(2,TimeUnit.SECONDS));caller.interrupt();caller.join(5000);
                check(!caller.isAlive()&&"upload_read_cancelled".equals(error.get())&&interrupted.get()&&fixture.queue.unfinishedReads()==1);
                fixture.clock.resume.countDown();await(()->fixture.queue.unfinishedReads()==0);check(fixture.queue.read(fixture.reader,0,1,1000)[0]==9);
            }finally{fixture.clock.resume.countDown();caller.join(5000);if(caller.isAlive())throw new AssertionError("Interrupted native reader still live");}
        }
    }
    private static void closeDuringRead()throws Exception {
        try(Fixture fixture=new Fixture("closing")){
            fixture.prepare(new byte[]{10});fixture.clock.hold();AtomicReference<String> error=new AtomicReference<>();
            Thread caller=new Thread(()->{try{fixture.queue.read(fixture.reader,0,1,4000);error.set("unexpected_success");}catch(MdoBrowserUploadFiles.Failure failure){error.set(failure.code);}catch(IOException failure){error.set("unexpected_io");}},"native-proxy-closing");caller.start();
            try{
                check(fixture.clock.entered.await(2,TimeUnit.SECONDS));fixture.queue.close();fixture.clock.resume.countDown();caller.join(5000);
                check(!caller.isAlive()&&"upload_worker_closed".equals(error.get()));check(fixture.queue.awaitStopped(5000));check(!fixture.reader.live(fixture.clock.now));
            }finally{fixture.clock.resume.countDown();caller.join(5000);if(caller.isAlive())throw new AssertionError("Closing native reader still live");}
        }
    }
    private static void replacedFile()throws Exception {
        try(Fixture fixture=new Fixture("replaced")){
            fixture.prepare(new byte[]{11});Path path=null;try(DirectoryStream<Path> entries=Files.newDirectoryStream(fixture.root)){
                for(Path entry:entries)if(!entry.getFileName().toString().equals(MdoBrowserUploadFiles.OWNER_FILE)&&Files.size(entry)>MdoBrowserUploadFiles.HEADER_BYTES)path=entry;
            }check(path!=null);Path saved=base.resolve("original-sealed-read-file");Files.move(path,saved,StandardCopyOption.ATOMIC_MOVE);Files.write(path,new byte[]{99});
            try{
                refused("upload_file_replaced",()->fixture.queue.read(fixture.reader,0,1,1000));check(!fixture.reader.live(fixture.clock.now));
                await(()->fixture.queue.cleanupFailed());check(fixture.queue.activeFiles()==1&&fixture.queue.reservedBytes()==1&&Files.readAllBytes(path)[0]==99);
            }finally{Files.delete(path);Files.move(saved,path,StandardCopyOption.ATOMIC_MOVE);}
            await(()->fixture.queue.activeFiles()==0);check(fixture.queue.reservedBytes()==0&&fixture.queue.unfinishedReads()==0);
        }
    }
    private static void fatalReadRetirement()throws Exception {
        try(Fixture fixture=new Fixture("fatal-native-read")){
            fixture.prepare(new byte[]{12});fixture.clock.hold();AtomicReference<String> error=new AtomicReference<>();
            Thread caller=new Thread(()->{try{fixture.queue.read(fixture.reader,0,1,4000);error.set("unexpected_success");}
                catch(MdoBrowserUploadFiles.Failure failure){error.set(failure.code);}catch(IOException failure){error.set("unexpected_io");}},"native-proxy-fatal");caller.start();
            try{
                check(fixture.clock.entered.await(2,TimeUnit.SECONDS));fixture.clock.fatalAfterHold=true;fixture.clock.resume.countDown();caller.join(5000);
                check(!caller.isAlive()&&"upload_read_failed".equals(error.get()));check(fixture.queue.awaitStopped(5000));
                check(!fixture.queue.ready()&&!fixture.reader.live(fixture.clock.now)&&fixture.queue.unfinishedReads()==0);
            }finally{fixture.clock.resume.countDown();caller.join(5000);if(caller.isAlive())throw new AssertionError("Fatal native reader still live");}
        }
    }
    public static void main(String[] args)throws Exception {
        base=Paths.get(args[0]);Files.createDirectory(base);
        rangesAndRelease();foreignOwner();explicitRevocation();boundedTimeouts();fullInboxAndStalledUi();changedBeforeAccess();cancellationAndExpiry(false);cancellationAndExpiry(true);interruptedReader();closeDuringRead();replacedFile();fatalReadRetirement();Files.delete(base);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"scope\":\"native-object-read-leases-and-worker-retirement\",\"framework_verified\":false,\"uri_assignment_verified\":false,\"android_automation_enabled\":false}");
    }
}
