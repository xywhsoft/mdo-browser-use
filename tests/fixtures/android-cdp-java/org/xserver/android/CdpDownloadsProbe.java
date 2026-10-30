package org.xserver.android;

import android.os.Handler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Actual controller/network scope/cooperative worker/strict JNI/files. UI and
 * renderer metadata are controlled; no Android provider or public UI claim. */
public final class CdpDownloadsProbe {
    private static int checks;private static Path base;
    private interface Work {void run()throws Exception;}
    private static void check(boolean condition){if(!condition)throw new AssertionError("Downloads check "+(checks+1));checks++;}
    private static void refused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected "+code);}catch(IOException error){check(code.equals(error.getMessage()));check(error.getCause()==null);}
    }
    private static void await(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()){
            if(System.nanoTime()>=end)throw new AssertionError("Download controller wait expired");Handler.drain();Thread.sleep(2);
        }checks++;
    }
    private static JSONObject ack(int id)throws Exception{return new JSONObject().put("id",id).put("result",new JSONObject());}
    private static JSONObject chunk(String base64,boolean eof)throws Exception{return new JSONObject().put("data",base64).put("base64Encoded",true).put("eof",eof);}
    static final class Case implements AutoCloseable {
        final CdpNetworkWorkerProbe.Case host=new CdpNetworkWorkerProbe.Case();
        final Path root;final MdoBrowserDownloadQueue files;final MdoBrowserCdpDownloads.Budget budget=new MdoBrowserCdpDownloads.Budget();
        final java.util.List<Source> sources=new java.util.ArrayList<>();
        boolean expectedCleanupFailure;
        Case(String name)throws Exception{this(name,null);}
        Case(String name,VerifyBarrier barrier)throws Exception{root=base.resolve(name);Files.createDirectory(root);files=new MdoBrowserDownloadQueue(()->{
            MdoBrowserDownloadFiles opened=new MdoBrowserDownloadFiles(root);
            try{if(barrier!=null)barrier.install(opened);return opened;}catch(Exception failure){opened.close();throw new IOException("download_fixture_setup_failed");}
        },()->System.nanoTime()/1000000L);}
        Source source()throws Exception{Source value=new Source(this);sources.add(value);return value;}
        @Override public void close()throws Exception{
            for(Source source:sources)source.controller.close();host.close();files.close();check(files.awaitStopped(3000));
            check(files.cleanupFailed()==expectedCleanupFailure&&files.reservations()==0&&files.reservedBytes()==0&&budget.used()==0);
            for(Source source:sources)check(source.controller.retiringFiles()==0);
            Files.deleteIfExists(root.resolve(".mdo-browser-download-owner-v1"));Files.delete(root);
        }
    }
    static final class Source {
        final Case test;final CdpNetworkBindingProbe.Context context;final MdoBrowserCdpDownloads controller;
        final AtomicInteger claims=new AtomicInteger(),reads=new AtomicInteger(),closes=new AtomicInteger(),aborts=new AtomicInteger(),continues=new AtomicInteger();
        final ConcurrentLinkedQueue<JSONObject> chunks=new ConcurrentLinkedQueue<>();volatile int withheldClose;volatile boolean holdClose;
        Source(Case test)throws Exception{
            this.test=test;context=test.host.source();controller=new MdoBrowserCdpDownloads(test.files,test.budget);
            CdpBindingProbe.Factory.Extra original=context.peer.extra;
            context.peer.extra=(command,wire)->{
                String method=command.getString("method");int id=command.getInt("id");
                if(method.equals("Fetch.takeResponseBodyAsStream")){
                    wire.queue(new JSONObject().put("id",id).put("result",new JSONObject().put("stream","download-stream-"+claims.incrementAndGet())));return true;
                }
                if(method.equals("IO.read")){
                    reads.incrementAndGet();JSONObject value=chunks.poll();if(value!=null)wire.queue(new JSONObject().put("id",id).put("result",value));return true;
                }
                if(method.equals("IO.close")){closes.incrementAndGet();if(holdClose)withheldClose=id;else wire.queue(ack(id));return true;}
                if(method.equals("Fetch.failRequest")){aborts.incrementAndGet();wire.queue(ack(id));return true;}
                if(method.equals("Fetch.continueRequest")){continues.incrementAndGet();wire.queue(ack(id));return true;}
                return original.send(command,wire);
            };
            int initial=test.host.states.size();
            test.host.worker.startNetwork(context.adapter.marker(),1500,controller,state->{test.host.states.add(state);if(!state.ready)controller.disconnect();});
            await(()->{test.host.ui.drain();return test.host.states.size()>initial;});check(test.host.states.get(initial).ready);
        }
        String offer(String id)throws Exception{
            int before=controller.list().length;context.wire().queue(new JSONObject().put("method","Fetch.requestPaused").put("params",CdpBodyProbe.paused(id)));
            await(()->{try{return controller.list().length==before+1;}catch(IOException failure){throw new AssertionError(failure);}});
            return controller.list()[before].id;
        }
        boolean state(String id,String value){try{return controller.get(id).state.equals(value);}catch(IOException failure){throw new AssertionError(failure);}}
        void complete(String id)throws Exception{await(()->state(id,"completed"));}
        void release(String id)throws Exception{MdoBrowserCdpDownloads.Release release=controller.release(id);await(release::finished);check(release.released()&&release.snapshot().cacheCleanupConfirmed&&release.snapshot().sourceCleanupConfirmed);}
    }
    static void ownBase(Path owned){base=owned;}
    private static void originalSuccess()throws Exception{
        try(Case test=new Case("success")){
            Source source=test.source();String id=source.offer("original-controller-1.0");MdoBrowserCdpDownloads.Snapshot pending=source.controller.get(id);
            check(pending.state.equals("pending")&&source.controller.pending()&&test.budget.used()==1);
            check(source.claims.get()==0&&source.reads.get()==0&&test.files.reservations()==0);
            String json=pending.json().toString();check(pending.name.equals("private.bin")&&pending.mime.isEmpty()&&!json.contains("private.invalid")&&!json.contains("private-request")&&!json.contains("requestId")&&!json.contains("stream")&&!json.contains("POST"));
            refused("download_not_terminal",()->source.controller.release(id));source.holdClose=true;
            source.chunks.add(chunk("AAH/",false));source.chunks.add(chunk("AgM=",true));source.controller.accept(id,8,60000);source.controller.accept(id,64,60000);
            await(()->source.withheldClose>0);await(()->source.state(id,"verifying"));
            check(!source.controller.get(id).verified&&!source.controller.get(id).json().has("sha256")&&test.files.reservedBytes()==8);
            check(source.claims.get()==1&&source.reads.get()==2&&source.aborts.get()==0);
            source.context.wire().queue(ack(source.withheldClose));source.complete(id);MdoBrowserCdpDownloads.Snapshot result=source.controller.get(id);
            check(result.verified&&result.received==5&&result.maximum==8&&result.sourceCleanupConfirmed&&!result.cacheCleanupConfirmed);
            check(result.json().getLong("size")==5&&result.json().getString("sha256").length()==64&&!source.controller.pending());
            check(source.controller.cancel(id)==result&&source.claims.get()==1&&source.aborts.get()==1);source.release(id);
            check(source.controller.list().length==0&&test.budget.used()==0&&test.files.reservations()==0);
        }
    }
    private static void cancellationAndPass()throws Exception{
        try(Case test=new Case("cancel")){
            Source source=test.source();String id=source.offer("original-controller-2.0");source.controller.cancel(id);await(()->source.state(id,"cancelled"));
            check(source.controller.get(id).sourceCleanupConfirmed&&source.claims.get()==0&&source.reads.get()==0&&source.aborts.get()==1&&test.files.reservations()==0);
            source.release(id);check(test.budget.used()==0);
            JSONObject pass=CdpBodyProbe.paused("ordinary-response");pass.remove("responseHeaders");source.context.wire().queue(new JSONObject().put("method","Fetch.requestPaused").put("params",pass));
            await(()->source.continues.get()==1);check(source.controller.list().length==0&&test.budget.used()==0);
        }
    }
    private static void commonWorkerAndOwners()throws Exception{
        try(Case test=new Case("owners")){
            Source stalled=test.source(),other=test.source();String a=stalled.offer("original-controller-3.0"),b=other.offer("original-controller-4.0");
            refused("download_identity_unavailable",()->other.controller.accept(a,8,60000));
            stalled.controller.accept(a,8,60000);await(()->stalled.reads.get()==1);
            other.chunks.add(chunk("CQ==",true));other.controller.accept(b,8,60000);other.complete(b);
            check(stalled.reads.get()==1&&stalled.closes.get()==0&&test.host.threads.size()==1);
            stalled.controller.cancel(a);await(()->stalled.state(a,"cancelled"));check(stalled.closes.get()==1&&stalled.aborts.get()==1);
            stalled.release(a);other.release(b);check(test.budget.used()==0);
        }
    }
    private static void disconnectPreservesReceipts()throws Exception{
        try(Case test=new Case("disconnect")){
            Source completed=test.source();String id=completed.offer("original-controller-5.0");completed.chunks.add(chunk("BA==",true));completed.controller.accept(id,8,60000);completed.complete(id);
            test.host.worker.cancelNetwork(completed.context.adapter.marker().lease());await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
            check(completed.controller.get(id).verified&&completed.controller.get(id).state.equals("completed"));completed.release(id);check(test.budget.used()==0);
        }
        try(Case test=new Case("lost")){
            Source pending=test.source();String id=pending.offer("original-controller-6.0");test.host.worker.cancelNetwork(pending.context.adapter.marker().lease());
            await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});await(()->pending.state(id,"failed"));
            check(!pending.controller.get(id).sourceCleanupConfirmed&&pending.controller.list().length==1&&test.budget.used()==1);
            refused("download_source_cleanup_unconfirmed",()->pending.controller.release(id));pending.controller.close();check(test.budget.used()==0);
        }
    }
    private static void globalLimit()throws Exception{
        try(Case test=new Case("limit")){
            Source source=test.source();for(int i=0;i<32;i++)check(test.budget.acquire());
            source.context.wire().queue(new JSONObject().put("method","Fetch.requestPaused").put("params",CdpBodyProbe.paused("over-budget")));
            await(()->source.aborts.get()==1);check(source.controller.list().length==0&&source.controller.failure().equals("download_receipt_limit")&&source.controller.pending());
            check(source.claims.get()==0&&test.files.reservations()==0);for(int i=0;i<32;i++)test.budget.release();
        }
    }
    private static void emptyAndKnownLimit()throws Exception{
        try(Case test=new Case("empty")){
            Source source=test.source();String id=source.offer("empty-download");source.chunks.add(chunk("",true));source.controller.accept(id,1,60000);source.complete(id);
            check(source.controller.get(id).received==0&&source.controller.get(id).json().getLong("size")==0&&test.files.reservedBytes()==0);source.release(id);
        }
        try(Case test=new Case("known-limit")){
            Source source=test.source();JSONObject params=CdpBodyProbe.paused("oversized-download");
            params.getJSONArray("responseHeaders").put(new JSONObject().put("name","Content-Length").put("value","9"));
            source.context.wire().queue(new JSONObject().put("method","Fetch.requestPaused").put("params",params));
            await(()->{try{return source.controller.list().length==1;}catch(IOException failure){throw new AssertionError(failure);}});
            String id=source.controller.list()[0].id;source.controller.accept(id,8,60000);await(()->source.state(id,"failed"));
            check(source.controller.get(id).error.equals("download_limit")&&source.controller.get(id).failureSource.equals("policy")&&source.controller.get(id).sourceCleanupConfirmed);
            check(source.claims.get()==0&&source.reads.get()==0&&test.files.reservations()==0);source.release(id);
        }
    }
    private static void partialConnectionLoss()throws Exception{
        try(Case test=new Case("partial-loss")){
            Source source=test.source();String id=source.offer("partial-response");source.chunks.add(chunk("AQ==",false));source.controller.accept(id,8,60000);
            await(()->source.reads.get()==2);test.host.worker.cancelNetwork(source.context.adapter.marker().lease());
            await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});await(()->source.state(id,"failed"));
            MdoBrowserCdpDownloads.Snapshot result=source.controller.get(id);
            check(!result.verified&&result.received==1&&result.cacheCleanupConfirmed&&!result.sourceCleanupConfirmed&&source.controller.list().length==1);
            check(result.error.equals("cdp_body_connection_closed")&&result.failureSource.equals("native_protocol"));
            refused("download_source_cleanup_unconfirmed",()->source.controller.release(id));source.controller.close();check(test.budget.used()==0);
        }
    }
    private static final class VerifyBarrier {
        final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),resume=new java.util.concurrent.CountDownLatch(1);
        final AtomicInteger reopens=new AtomicInteger();
        void install(MdoBrowserDownloadFiles files)throws Exception{
            java.lang.reflect.Field backend=MdoBrowserDownloadFiles.class.getDeclaredField("files");backend.setAccessible(true);Object core=backend.get(files);
            java.lang.reflect.Field directory=MdoBrowserUploadFiles.class.getDeclaredField("directory");directory.setAccessible(true);Object real=directory.get(core);
            Object proxy=java.lang.reflect.Proxy.newProxyInstance(CdpDownloadsProbe.class.getClassLoader(),new Class<?>[]{java.nio.file.SecureDirectoryStream.class},(p,m,args)->{
                Object result;try{result=m.invoke(real,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
                if(m.getName().equals("newByteChannel")&&args[0].toString().startsWith("mdo-bu-download-")&&
                    ((java.util.Set<?>)args[1]).contains(java.nio.file.StandardOpenOption.READ)&&!((java.util.Set<?>)args[1]).contains(java.nio.file.StandardOpenOption.WRITE)&&reopens.incrementAndGet()==2){
                    entered.countDown();if(!resume.await(5,TimeUnit.SECONDS))throw new IOException("download_fixture_verify_timeout");
                }return result;
            });directory.set(core,proxy);
        }
    }
    private static void verificationSurvivesDisconnect(boolean tamper)throws Exception{
        VerifyBarrier barrier=new VerifyBarrier();
        try(Case test=new Case(tamper?"verification-tamper":"verification-loss",barrier)){
            try{
                Source source=test.source();String id=source.offer("complete-source-verifying");source.chunks.add(chunk("BQ==",true));source.controller.accept(id,8,60000);
                check(barrier.entered.await(3,TimeUnit.SECONDS));check(source.controller.get(id).state.equals("verifying")&&source.controller.get(id).sourceCleanupConfirmed);
                test.host.worker.cancelNetwork(source.context.adapter.marker().lease());await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
                check(!source.controller.get(id).verified);
                if(tamper)try(java.util.stream.Stream<Path> paths=Files.list(test.root)){
                    Path leaf=paths.filter(path->path.getFileName().toString().startsWith("mdo-bu-download-")).findFirst().get();
                    try(java.nio.channels.SeekableByteChannel output=Files.newByteChannel(leaf,java.nio.file.StandardOpenOption.WRITE)){
                        output.position(MdoBrowserUploadFiles.HEADER_BYTES);output.write(java.nio.ByteBuffer.wrap(new byte[]{9}));
                    }
                }
                barrier.resume.countDown();
                if(tamper){await(()->source.state(id,"failed"));check(source.controller.get(id).error.equals("download_hash_mismatch")&&source.controller.get(id).failureSource.equals("cache")&&!source.controller.get(id).verified);}
                else{try{source.complete(id);}catch(AssertionError failure){throw new AssertionError("Complete source verification discarded: "+source.controller.get(id).json(),failure);}
                    check(source.controller.get(id).verified&&source.controller.get(id).received==1);}
                source.release(id);check(test.budget.used()==0);
            }finally{barrier.resume.countDown();}
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();base=Paths.get(args[0]);if(Files.exists(base))throw new AssertionError("Interrupted download controller evidence preserved");Files.createDirectory(base);
        if(args.length>1){verificationSurvivesDisconnect(false);verificationSurvivesDisconnect(true);}else{originalSuccess();cancellationAndPass();commonWorkerAndOwners();disconnectPreservesReceipts();globalLimit();emptyAndKnownLimit();partialConnectionLoss();verificationSurvivesDisconnect(false);verificationSurvivesDisconnect(true);}Files.delete(base);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"actual_download_controller\":true,\"shared_protocol_worker\":true,\"real_guarded_files\":true,\"native_receipts_preserved\":true,\"framework_verified\":false,\"provider_qualified\":false,\"product_downloads_activated\":false}");
    }
}
