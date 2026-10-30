package org.xserver.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Actual common worker/dispatcher/source receipts/files, controlled peer/UI. */
public final class CdpNetworkRetireProbe {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Network retirement check "+(checks+1));checks++;}
    private static void await(BooleanSupplier condition)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()){
            if(System.nanoTime()>=until)throw new AssertionError("Retirement condition timed out");android.os.Handler.drain();Thread.sleep(2);
        }checks++;
    }
    private static JSONObject chunk(String value,boolean eof)throws Exception{return new JSONObject().put("data",value).put("base64Encoded",true).put("eof",eof);}
    private static void retire(CdpDownloadsProbe.Case test,CdpDownloadsProbe.Source source)throws Exception{
        source.controller.quiesce();test.host.worker.retireNetwork(source.context.adapter.marker().lease());
        test.host.worker.retireNetwork(source.context.adapter.marker().lease()); // same original deadline
        await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
    }
    private static MdoBrowserCdpNetworks.Result terminal(CdpDownloadsProbe.Case test){return test.host.states.get(test.host.states.size()-1);}
    private static void successful(int mode)throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("retire-"+mode)){
            CdpDownloadsProbe.Source source=test.source();String identity=null;
            if(mode!=0)identity=source.offer("original-retirement-"+mode);
            if(mode==2){source.chunks.add(chunk("AQ==",false));source.controller.accept(identity,8,60000);String id=identity;await(()->source.reads.get()>=2&&source.snapshot(id).received==1);}
            if(mode==3){source.chunks.add(chunk("Ag==",true));source.controller.accept(identity,8,60000);source.complete(identity);}
            source.controller.quiesce();
            // A future manual-page response is continued, never captured as
            // another Agent download or re-fetched with substituted credentials.
            source.context.wire().queue(new JSONObject().put("method","Fetch.requestPaused").put("params",CdpBodyProbe.paused("manual-after-retire").put("frameId","frame-owned")));
            test.host.worker.retireNetwork(source.context.adapter.marker().lease());
            test.host.worker.retireNetwork(source.context.adapter.marker().lease());
            await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
            MdoBrowserCdpNetworks.Result result=terminal(test);check(result.code.equals("cdp_network_retired")&&result.cleanupConfirmed&&result.responsesDisabled&&!result.ready);
            check(source.controller.list().length==(mode==0?0:1)&&source.continues.get()==1);
            ArrayList<String> trace=new ArrayList<>(source.trace);check(trace.stream().filter("Fetch.disable"::equals).count()==1);
            if(mode==2)check(trace.indexOf("Fetch.disable")>trace.indexOf("IO.close")&&trace.indexOf("Fetch.disable")>trace.indexOf("Fetch.failRequest"));
            if(mode==0)check(source.claims.get()==0&&source.reads.get()==0);
            else{
                String id=identity;
                if(mode==3){check(source.snapshot(id).state.equals("completed")&&source.snapshot(id).verified);MdoBrowserDownloadQueue.Read read=source.controller.read(id,0,1);await(read::finished);byte[] bytes=read.take();check(Arrays.equals(bytes,new byte[]{2}));Arrays.fill(bytes,(byte)0);}
                else{await(()->source.state(id,"cancelled"));check(source.snapshot(id).sourceCleanupConfirmed&&source.snapshot(id).cacheCleanupConfirmed&&!source.snapshot(id).verified);}
                source.release(id);
            }
            check(test.budget.used()==0&&test.files.retainedReads()==0);
        }
    }
    private static void unconfirmedDisable()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("disable-unconfirmed")){
            CdpDownloadsProbe.Source source=test.source();CdpBindingProbe.Factory.Extra original=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{if(command.getString("method").equals("Fetch.disable")){source.trace.add("Fetch.disable");return true;}return original.send(command,wire);};
            retire(test,source);MdoBrowserCdpNetworks.Result result=terminal(test);
            check(!result.responsesDisabled&&result.cleanupConfirmed&&!result.code.equals("cdp_network_retired"));
            check(source.trace.stream().filter("Fetch.disable"::equals).count()==1);check(source.controller.list().length==0);
        }
    }
    private static void unconfirmedSource()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("source-unconfirmed")){
            CdpDownloadsProbe.Source source=test.source();source.holdClose=true;String id=source.offer("held-retirement-source");source.chunks.add(chunk("Aw==",false));source.controller.accept(id,8,60000);
            await(()->source.reads.get()>=2);source.controller.quiesce();test.host.worker.retireNetwork(source.context.adapter.marker().lease());await(()->source.withheldClose!=0);
            test.host.time.addAndGet(5001);await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
            MdoBrowserCdpNetworks.Result result=terminal(test);check(result.code.equals("cdp_network_retirement_timeout")&&!result.responsesDisabled&&result.cleanupConfirmed);
            check(source.trace.stream().noneMatch("Fetch.disable"::equals));check(!source.snapshot(id).sourceCleanupConfirmed&&!source.snapshot(id).verified);
            try{source.controller.release(id);throw new AssertionError("Unconfirmed source was released");}catch(java.io.IOException expected){check(expected.getMessage().equals("download_source_cleanup_unconfirmed"));}
        }
    }
    private static void verifyingSource(boolean tamper)throws Exception{
        CdpDownloadsProbe.VerifyBarrier barrier=new CdpDownloadsProbe.VerifyBarrier();
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("retire-verifying-"+tamper,barrier)){
            try{
                CdpDownloadsProbe.Source source=test.source();String id=source.offer("verifying-before-retirement");source.chunks.add(chunk("BA==",true));source.controller.accept(id,8,60000);
                check(barrier.entered.await(3,TimeUnit.SECONDS)&&source.snapshot(id).state.equals("verifying")&&source.snapshot(id).sourceCleanupConfirmed);
                retire(test,source);check(terminal(test).responsesDisabled&&terminal(test).cleanupConfirmed&&!source.snapshot(id).verified);
                if(tamper){Path leaf;try(java.util.stream.Stream<Path> paths=Files.list(test.root)){leaf=paths.filter(path->path.getFileName().toString().startsWith("mdo-bu-download-")).findFirst().get();}
                    try(java.nio.channels.SeekableByteChannel output=Files.newByteChannel(leaf,java.nio.file.StandardOpenOption.WRITE)){
                        output.position(MdoBrowserUploadFiles.HEADER_BYTES);output.write(java.nio.ByteBuffer.wrap(new byte[]{9}));
                    }
                }
                barrier.resume.countDown();
                if(tamper){await(()->source.state(id,"failed"));check(source.snapshot(id).error.equals("download_hash_mismatch")&&!source.snapshot(id).verified);}
                else{source.complete(id);check(source.snapshot(id).verified&&source.snapshot(id).sourceCleanupConfirmed);}
                source.release(id);check(test.budget.used()==0);
            }finally{barrier.resume.countDown();}
        }
    }
    private static void preservePriorCacheFailure()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("retire-cache-failed")){
            java.lang.reflect.Field factory=test.files.getClass().getDeclaredField("factory");factory.setAccessible(true);
            factory.set(test.files,(MdoBrowserDownloadQueue.Factory)()->{throw new java.io.IOException("private fixture storage detail");});
            CdpDownloadsProbe.Source source=test.source();source.holdClose=true;String id=source.offer("failed-cache-before-takeover");source.controller.accept(id,8,60000);
            await(()->"download_storage_failed".equals(source.snapshot(id).error)&&source.withheldClose!=0);check(source.snapshot(id).failureSource.equals("cache"));
            source.controller.quiesce();test.host.worker.retireNetwork(source.context.adapter.marker().lease());
            source.context.wire().queue(new JSONObject().put("id",source.withheldClose).put("result",new JSONObject()));
            await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});await(()->source.snapshot(id).terminal());
            MdoBrowserCdpDownloads.Snapshot failed=source.snapshot(id);
            if(!"download_storage_failed".equals(failed.error)||!failed.failureSource.equals("cache")||!failed.state.equals("failed"))throw new AssertionError("Original cache failure masked: "+failed.json());checks++;
            check(failed.sourceCleanupConfirmed&&failed.cacheCleanupConfirmed&&!failed.verified);source.release(id);
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();Path root=Paths.get(args[0]);if(Files.exists(root))throw new AssertionError("Interrupted retirement evidence preserved");Files.createDirectory(root);CdpDownloadsProbe.ownBase(root);
        for(int mode=0;mode<4;mode++)successful(mode);unconfirmedDisable();unconfirmedSource();verifyingSource(false);verifyingSource(true);preservePriorCacheFailure();Files.delete(root);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"original_retirement_receipts\":true,\"shared_protocol_worker\":true,\"real_guarded_files\":true,\"framework_verified\":false,\"provider_qualified\":false}");
    }
}
