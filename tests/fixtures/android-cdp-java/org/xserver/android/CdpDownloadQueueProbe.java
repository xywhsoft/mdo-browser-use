package org.xserver.android;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Original session/strict JSON chunks, real worker and actual guarded files.
 * The response peer is controlled; no Android framework/provider qualification. */
public final class CdpDownloadQueueProbe {
    private static int checks;private static Path base;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Download queue check "+(checks+1));checks++;}
    private static void refused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected "+code);}catch(IOException failure){check(code.equals(failure.getMessage()));check(failure.getCause()==null);}
    }
    private static void await(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!condition.getAsBoolean()){if(System.nanoTime()>=end)throw new AssertionError("Download worker condition timeout");Thread.sleep(2);}checks++;
    }
    private static String hash(byte[] bytes)throws Exception{
        StringBuilder text=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format("%02x",value&255));return text.toString();
    }
    private static Thread worker(MdoBrowserDownloadQueue queue)throws Exception{
        java.lang.reflect.Field field=MdoBrowserDownloadQueue.class.getDeclaredField("worker");field.setAccessible(true);return (Thread)field.get(queue);
    }
    private static void finish(CdpBodyProbe.Context context,MdoBrowserCdpSession.Body body)throws Exception{
        for(int i=0;i<25&&body.result()==null;i++){context.client.serviceBodies(100);context.client.poll(20,100);}check(body.result()!=null);
    }
    private static MdoBrowserCdpSession.BodyChunk read(CdpBodyProbe.Context context,MdoBrowserCdpSession.Body body,byte[] bytes,boolean eof)throws Exception{
        context.wire.chunks.add(new JSONObject().put("data",Base64.getEncoder().encodeToString(bytes)).put("base64Encoded",true).put("eof",eof));
        context.client.requestBodyRead(body);check(context.client.serviceBodies(100));check(context.client.poll(100));
        MdoBrowserCdpSession.BodyChunk chunk=context.client.takeBodyChunk(body);check(chunk!=null&&chunk.eof==eof&&Arrays.equals(bytes,chunk.data));return chunk;
    }
    private static final class Disk implements AutoCloseable {
        final Path root;final CdpBodyProbe.Context context;final MdoBrowserDownloadQueue queue;
        Disk(String name,CdpBodyProbe.Context context)throws Exception{this(name,context,null,null);}
        Disk(String name,CdpBodyProbe.Context context,CountDownLatch entered,CountDownLatch release)throws Exception{
            this.context=context;root=base.resolve(name);Files.createDirectory(root);
            queue=new MdoBrowserDownloadQueue(()->{
                if(entered!=null){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("download_fixture_timeout");}
                    catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IOException("download_fixture_interrupted");}}
                return new MdoBrowserDownloadFiles(root);
            },context.wire::now);
        }
        MdoBrowserDownloadQueue.Ticket begin(MdoBrowserCdpSession.Body body)throws Exception{
            MdoBrowserDownloadQueue.Ticket ticket=queue.begin(body);await(()->queueWritable(ticket));return ticket;
        }
        boolean queueWritable(MdoBrowserDownloadQueue.Ticket ticket){try{return queue.writable(ticket);}catch(IOException error){throw new AssertionError(error);}}
        @Override public void close()throws Exception{
            queue.close();check(queue.awaitStopped(3000));check(!queue.cleanupFailed());check(queue.reservations()==0&&queue.reservedBytes()==0&&queue.retainedTickets()==0);
            check(queue.retainedReads()==0);
            Files.deleteIfExists(root.resolve(".mdo-browser-download-owner-v1"));Files.delete(root);
        }
    }
    private static void success()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            MdoBrowserCdpSession.Body source=context.claim();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            try(Disk disk=new Disk("success",context,entered,release)){
                MdoBrowserDownloadQueue.Ticket ticket=disk.queue.begin(source);check(entered.await(1,TimeUnit.SECONDS));check(ticket.snapshot().state.equals("opening"));
                refused("download_source_already_consumed",()->disk.queue.begin(source));
                // The file worker is actually blocked, while the original
                // protocol owner can still send a page command and get its ACK.
                context.client.call("Runtime.evaluate",null,null,100,false);check(context.wire.count("Runtime.evaluate")==1);release.countDown();await(()->disk.queueWritable(ticket));
                byte[] first={0,1,(byte)255},last={9,8,7};
                MdoBrowserCdpSession.BodyChunk chunk=read(context,source,first,false);check(disk.queue.offer(ticket,chunk));
                await(()->ticket.snapshot().bytes==first.length);await(()->disk.queueWritable(ticket));check(Arrays.equals(chunk.data,new byte[first.length]));
                chunk=read(context,source,last,true);check(disk.queue.offer(ticket,chunk));await(()->ticket.snapshot().state.equals("finishing"));
                MdoBrowserDownloadQueue.Snapshot pending=ticket.snapshot();Thread.sleep(60);check(pending==ticket.snapshot());
                check(!pending.completed()&&!pending.fileVerified&&pending.sha256==null&&source.result()==null);check(disk.queue.reservedBytes()==source.maximumBytes());
                int[] close={0};context.wire.extra=command->{if(command.getString("method").equals("IO.close")){close[0]=command.getInt("id");return true;}return false;};
                for(int i=0;i<3;i++){context.client.serviceBodies(100);context.client.poll(20,100);}check(close[0]>0&&!ticket.snapshot().completed());
                context.wire.add(new JSONObject().put("id",close[0]).put("result",new JSONObject()));finish(context,source);
                await(()->ticket.snapshot().completed());MdoBrowserDownloadQueue.Snapshot result=ticket.snapshot();
                check(result.source==source.result()&&result.source.bytes==6&&result.bytes==6&&result.sha256.equals(hash(new byte[]{0,1,(byte)255,9,8,7})));
                check(!result.fileCleanupConfirmed&&disk.queue.reservedBytes()==6);disk.queue.cancel(ticket);check(ticket.snapshot()==result);
                Thread fileWorker=worker(disk.queue);await(()->fileWorker.getState()==Thread.State.WAITING);
                context.client.close();check(ticket.snapshot().completed());disk.queue.release(ticket);await(()->ticket.snapshot().state.equals("released"));
                check(ticket.snapshot().fileCleanupConfirmed&&!ticket.snapshot().fileVerified&&ticket.snapshot().sha256==null);
                refused("download_source_already_consumed",()->disk.queue.begin(source));
            }
        }
    }
    private static void failedSource()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context();Disk disk=new Disk("failed",context)){
            MdoBrowserCdpSession.Body source=context.claim();MdoBrowserDownloadQueue.Ticket ticket=disk.begin(source);
            check(disk.queue.offer(ticket,read(context,source,new byte[]{1,2},true)));await(()->ticket.snapshot().state.equals("finishing"));
            context.client.close();await(()->ticket.snapshot().state.equals("failed"));check(!ticket.snapshot().completed()&&ticket.snapshot().sha256==null);
            check(ticket.snapshot().source==source.result()&&!ticket.snapshot().source.ok&&ticket.snapshot().fileCleanupConfirmed);
            await(()->disk.queue.reservations()==0);disk.queue.release(ticket);await(()->ticket.snapshot().state.equals("released"));
        }
    }
    private static void ownershipAndCancellation()throws Exception{
        try(CdpBodyProbe.Context first=new CdpBodyProbe.Context();CdpBodyProbe.Context second=new CdpBodyProbe.Context();Disk disk=new Disk("ownership",first);Disk foreign=new Disk("foreign",second)){
            MdoBrowserCdpSession.Body a=first.claim(),b=second.claim();MdoBrowserDownloadQueue.Ticket ta=disk.begin(a),tb=disk.begin(b);
            refused("download_owner_mismatch",()->foreign.queue.writable(ta));
            MdoBrowserCdpSession.BodyChunk other=read(second,b,new byte[]{4},false);
            refused("download_chunk_owner_mismatch",()->disk.queue.offer(ta,other));check(other.data[0]==4);
            check(disk.queue.offer(tb,other));await(()->tb.snapshot().bytes==1);await(()->disk.queueWritable(tb));
            refused("download_chunk_owner_mismatch",()->disk.queue.offer(tb,other));
            MdoBrowserCdpSession.BodyChunk erased=read(first,a,new byte[]{3},false);erased.close();refused("download_chunk_owner_mismatch",()->disk.queue.offer(ta,erased));
            disk.queue.cancel(ta);disk.queue.cancel(tb);finish(first,a);finish(second,b);
            await(()->ta.snapshot().state.equals("cancelled")&&ta.snapshot().source==a.result());
            await(()->tb.snapshot().state.equals("cancelled")&&tb.snapshot().source==b.result());
            check(!ta.snapshot().completed()&&ta.snapshot().fileCleanupConfirmed&&tb.snapshot().fileCleanupConfirmed);await(()->disk.queue.reservations()==0);
        }
    }
    private static void budgetsAndIdleExpiry()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context();Disk disk=new Disk("budgets",context)){
            context.limit=67108864;MdoBrowserCdpSession.Body a=context.claim(),b=context.claim(),c=context.claim();
            MdoBrowserDownloadQueue.Ticket ta=disk.begin(a),tb=disk.begin(b),tc=disk.queue.begin(c);
            await(()->tc.snapshot().state.equals("failed"));check(tc.snapshot().code.equals("download_budget_exceeded"));
            check(tc.snapshot().fileCleanupConfirmed&&disk.queue.reservations()==2&&disk.queue.reservedBytes()==134217728L);finish(context,c);
            context.wire.now=4000;await(()->ta.snapshot().state.equals("failed")&&tb.snapshot().state.equals("failed"));
            check(ta.snapshot().code.equals("download_timeout")&&tb.snapshot().code.equals("download_timeout"));check(context.wire.count("IO.read")==0);
            finish(context,a);finish(context,b);await(()->disk.queue.reservations()==0);
        }
    }
    private static void releaseWaitsForOriginalCleanup()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context();Disk disk=new Disk("release",context)){
            MdoBrowserCdpSession.Body source=context.claim();MdoBrowserDownloadQueue.Ticket ticket=disk.begin(source);
            check(disk.queue.offer(ticket,read(context,source,new byte[]{5},false)));await(()->ticket.snapshot().bytes==1);
            disk.queue.release(ticket);await(()->ticket.snapshot().state.equals("releasing"));
            check(ticket.snapshot().fileCleanupConfirmed&&ticket.snapshot().source==null);MdoBrowserDownloadQueue.Snapshot waiting=ticket.snapshot();
            Thread.sleep(60);check(ticket.snapshot()==waiting);finish(context,source);await(()->ticket.snapshot().state.equals("released"));
            check(ticket.snapshot().source==source.result()&&ticket.snapshot().source.streamCloseConfirmed&&ticket.snapshot().source.requestAbortConfirmed);
            check(ticket.snapshot().fileCleanupConfirmed&&!ticket.snapshot().completed());
        }
    }
    private static void closeDuringBlockedStorage()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            MdoBrowserCdpSession.Body source=context.claim();CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1);
            try(Disk disk=new Disk("blocked-close",context,entered,resume)){
                MdoBrowserDownloadQueue.Ticket ticket=disk.queue.begin(source);check(entered.await(1,TimeUnit.SECONDS));
                disk.queue.close();check(!disk.queue.awaitStopped(30));check(ticket.snapshot().state.equals("opening"));
                refused("download_worker_closed",()->disk.queue.begin(source));resume.countDown();check(disk.queue.awaitStopped(3000));
                check(ticket.snapshot().state.equals("closed")&&ticket.snapshot().fileCleanupConfirmed&&ticket.snapshot().source==null);
                check(disk.queue.retainedTickets()==0);finish(context,source);check(!source.result().ok);
            }
        }
    }
    private static void idleWorkerParks()throws Exception{
        java.util.concurrent.atomic.AtomicInteger opened=new java.util.concurrent.atomic.AtomicInteger();
        MdoBrowserDownloadQueue queue=new MdoBrowserDownloadQueue(()->{opened.incrementAndGet();throw new IOException("download_fixture_unexpected_open");},()->0);
        try{Thread worker=worker(queue);await(()->worker.getState()==Thread.State.WAITING);check(opened.get()==0&&queue.retainedTickets()==0);}
        finally{queue.close();check(queue.awaitStopped(1000)&&!queue.cleanupFailed());}
    }
    private static Object field(Object object,String name)throws Exception{
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static MdoBrowserDownloadQueue.Ticket completed(CdpBodyProbe.Context context,Disk disk,byte[] bytes)throws Exception{
        MdoBrowserCdpSession.Body source=context.claim();MdoBrowserDownloadQueue.Ticket ticket=disk.begin(source);
        check(disk.queue.offer(ticket,read(context,source,bytes,true)));finish(context,source);await(()->ticket.snapshot().completed());return ticket;
    }
    private static void rangeReads()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context();Disk disk=new Disk("ranges",context)){
            byte[] payload=new byte[65536+17];for(int i=0;i<payload.length;i++)payload[i]=(byte)i;
            context.limit=payload.length;
            MdoBrowserCdpSession.Body source=context.claim();MdoBrowserDownloadQueue.Ticket ticket=disk.begin(source);
            refused("download_not_completed",()->disk.queue.read(ticket,0,1));
            check(disk.queue.offer(ticket,read(context,source,Arrays.copyOfRange(payload,0,65536),false)));await(()->ticket.snapshot().bytes==65536&&disk.queueWritable(ticket));
            check(disk.queue.offer(ticket,read(context,source,Arrays.copyOfRange(payload,65536,payload.length),true)));finish(context,source);await(()->ticket.snapshot().completed());
            refused("download_range_invalid",()->disk.queue.read(ticket,-1,1));refused("download_range_invalid",()->disk.queue.read(ticket,0,65537));
            refused("download_range_invalid",()->disk.queue.read(ticket,payload.length+1,1));
            MdoBrowserDownloadQueue foreign=new MdoBrowserDownloadQueue(()->{throw new IOException("Unexpected open");},()->0);
            try{refused("download_owner_mismatch",()->foreign.read(ticket,0,1));}finally{foreign.close();check(foreign.awaitStopped(1000));}
            MdoBrowserDownloadQueue.Read[] ranges=new MdoBrowserDownloadQueue.Read[4];
            for(int i=0;i<ranges.length;i++)ranges[i]=disk.queue.read(ticket,i,32);
            for(MdoBrowserDownloadQueue.Read range:ranges)await(range::finished);
            refused("download_read_busy",()->disk.queue.read(ticket,0,1));check(disk.queue.retainedReads()==4);
            byte[] held=(byte[])field(ranges[0],"bytes");ranges[0].close();check(Arrays.equals(held,new byte[held.length]));
            refused("download_read_cancelled",ranges[0]::take);
            byte[] received=ranges[1].take();check(Arrays.equals(received,Arrays.copyOfRange(payload,1,33)));Arrays.fill(received,(byte)0);
            refused("download_read_cancelled",ranges[1]::take);ranges[2].close();ranges[3].close();check(disk.queue.retainedReads()==0);
            for(int offset=0;offset<payload.length;offset+=65536){
                MdoBrowserDownloadQueue.Read range=disk.queue.read(ticket,offset,65536);await(range::finished);
                check(range.size==payload.length&&range.sha256.equals(hash(payload))&&range.offset==offset);
                byte[] bytes=range.take();check(Arrays.equals(bytes,Arrays.copyOfRange(payload,offset,Math.min(payload.length,offset+65536))));Arrays.fill(bytes,(byte)0);
            }
            MdoBrowserDownloadQueue.Read end=disk.queue.read(ticket,payload.length,1);await(end::finished);check(end.take().length==0);
            MdoBrowserDownloadQueue.Read cancelled=disk.queue.read(ticket,0,1);await(cancelled::finished);byte[] erased=(byte[])field(cancelled,"bytes");
            disk.queue.release(ticket);refused("download_read_cancelled",cancelled::take);check(Arrays.equals(erased,new byte[erased.length]));await(()->ticket.snapshot().state.equals("released"));
            refused("download_not_completed",()->disk.queue.read(ticket,0,1));check(disk.queue.retainedReads()==0);
        }
    }
    static final class ReadBarrier {
        final CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1);
        ReadBarrier(MdoBrowserDownloadQueue.Ticket ticket)throws Exception{
            Object writer=field(ticket,"writer"),leaf=field(writer,"leaf");java.lang.reflect.Field channel=leaf.getClass().getDeclaredField("channel");channel.setAccessible(true);
            java.nio.channels.SeekableByteChannel original=(java.nio.channels.SeekableByteChannel)channel.get(leaf);
            channel.set(leaf,new java.nio.channels.SeekableByteChannel(){
                boolean blocked;
                public int read(java.nio.ByteBuffer bytes)throws IOException{if(!blocked){blocked=true;entered.countDown();try{if(!resume.await(3,TimeUnit.SECONDS))throw new IOException("download_fixture_timeout");}catch(InterruptedException failure){throw new IOException(failure);}}return original.read(bytes);}
                public int write(java.nio.ByteBuffer bytes)throws IOException{return original.write(bytes);}
                public long position()throws IOException{return original.position();}public java.nio.channels.SeekableByteChannel position(long value)throws IOException{original.position(value);return this;}
                public long size()throws IOException{return original.size();}public java.nio.channels.SeekableByteChannel truncate(long value)throws IOException{original.truncate(value);return this;}
                public boolean isOpen(){return original.isOpen();}public void close()throws IOException{original.close();}
            });
        }
    }
    private static void releaseDuringRead()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context();Disk disk=new Disk("read-release",context)){
            MdoBrowserDownloadQueue.Ticket ticket=completed(context,disk,new byte[]{1,2,3});
            ReadBarrier barrier=new ReadBarrier(ticket);CountDownLatch entered=barrier.entered,resume=barrier.resume;
            MdoBrowserDownloadQueue.Read range=disk.queue.read(ticket,0,3);
            try{check(entered.await(1,TimeUnit.SECONDS));check(!range.finished());refused("download_read_pending",range::take);
                disk.queue.release(ticket);check(range.finished()&&disk.queue.retainedReads()==1&&disk.queue.reservations()==1);
                refused("download_read_cancelled",range::take);context.client.call("Runtime.evaluate",null,null,100,false);check(context.wire.count("Runtime.evaluate")==1);
            }finally{resume.countDown();}
            await(()->ticket.snapshot().state.equals("released"));check(disk.queue.retainedReads()==0&&disk.queue.reservations()==0);
        }
    }
    private static void readDetectsTampering()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context();Disk disk=new Disk("read-tamper",context)){
            MdoBrowserDownloadQueue.Ticket ticket=completed(context,disk,new byte[]{1,2,3});
            MdoBrowserDownloadQueue.Read retained=disk.queue.read(ticket,0,3);await(retained::finished);byte[] erased=(byte[])field(retained,"bytes");
            Path leaf;try(java.util.stream.Stream<Path> paths=Files.list(disk.root)){leaf=paths.filter(path->path.getFileName().toString().startsWith("mdo-bu-download-")).findFirst().get();}
            try(java.nio.channels.SeekableByteChannel output=Files.newByteChannel(leaf,java.nio.file.StandardOpenOption.WRITE)){
                output.position(MdoBrowserUploadFiles.HEADER_BYTES);output.write(java.nio.ByteBuffer.wrap(new byte[]{9}));
            }
            MdoBrowserDownloadQueue.Read changed=disk.queue.read(ticket,0,3);await(changed::finished);refused("download_hash_mismatch",changed::take);
            refused("download_read_cancelled",retained::take);check(Arrays.equals(erased,new byte[erased.length]));
            await(()->ticket.snapshot().fileCleanupConfirmed);check(!ticket.snapshot().completed()&&ticket.snapshot().code.equals("download_hash_mismatch"));
            check(disk.queue.retainedReads()==0);disk.queue.release(ticket);await(()->ticket.snapshot().state.equals("released"));
        }
    }
    public static void main(String[] args)throws Exception{
        base=Paths.get(args[0]);if(Files.exists(base))throw new AssertionError("Interrupted download queue evidence preserved");Files.createDirectory(base);
        android.os.Looper.getMainLooper();java.util.concurrent.ExecutorService protocol=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{protocol.submit(()->{try{success();failedSource();ownershipAndCancellation();budgetsAndIdleExpiry();releaseWaitsForOriginalCleanup();closeDuringBlockedStorage();idleWorkerParks();rangeReads();releaseDuringRead();readDetectsTampering();}catch(Exception failure){throw new RuntimeException(failure);}}).get(25,TimeUnit.SECONDS);}
        finally{protocol.shutdownNow();check(protocol.awaitTermination(3,TimeUnit.SECONDS));}
        Files.delete(base);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"original_native_body_joined\":true,\"real_guarded_files\":true,\"blocked_file_worker_page_progress\":true,\"framework_verified\":false,\"provider_qualified\":false,\"product_downloads_activated\":false}");
    }
}
