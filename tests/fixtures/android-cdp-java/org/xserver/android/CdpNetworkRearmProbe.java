package org.xserver.android;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Same actual view/controller/files across fresh native connection proofs.
 * Controlled renderer/transport/UI peers do not qualify an installed provider. */
public final class CdpNetworkRearmProbe {
    private static int checks;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Network rearm check "+(checks+1));checks++;}
    private static void refused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected "+code);}catch(IOException failure){check(code.equals(failure.getMessage())&&failure.getCause()==null);}
    }
    private static void await(BooleanSupplier condition)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()){
            if(System.nanoTime()>=until)throw new AssertionError("Rearm condition timed out");android.os.Handler.drain();Thread.sleep(2);
        }checks++;
    }
    private static JSONObject chunk(int value)throws Exception{return new JSONObject().put("data",java.util.Base64.getEncoder().encodeToString(new byte[]{(byte)value})).put("base64Encoded",true).put("eof",true);}
    private static void retire(CdpDownloadsProbe.Case test,CdpDownloadsProbe.Source source)throws Exception{
        source.controller.quiesce();test.host.worker.retireNetwork(source.context.adapter.marker().lease());
        await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
        MdoBrowserCdpNetworks.Result result=test.host.states.get(test.host.states.size()-1);check(result.cleanupConfirmed&&result.responsesDisabled);
    }
    private static MdoBrowserCdpBinding.Network network(CdpDownloadsProbe.Source source)throws Exception{
        java.lang.reflect.Field field=source.controller.getClass().getDeclaredField("original");field.setAccessible(true);
        await(()->{try{return field.get(source.controller)!=null;}catch(Exception failure){throw new AssertionError(failure);}});
        return (MdoBrowserCdpBinding.Network)field.get(source.controller);
    }
    private static void retainedAndStale()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("same-page")){
            CdpDownloadsProbe.Source old=test.source();String first=old.offer("first-original");old.chunks.add(chunk(3));old.controller.accept(first,8,60000);old.complete(first);
            MdoBrowserCdpBinding.Network original=network(old);retire(test,old);
            final String[] background={null};Thread foreign=new Thread(()->{try{old.controller.rearm();}catch(IOException failure){background[0]=failure.getMessage();}});
            foreign.start();foreign.join();check("download_requires_ui_thread".equals(background[0]));
            CdpDownloadsProbe.Source next=test.rearm(old);MdoBrowserCdpBinding.Network current=network(next);
            check(next.context.view==old.context.view&&next.controller==old.controller&&next.quota==old.quota&&old.context.view.evaluations==2);
            check(next.connection!=old.connection&&next.controller.list().length==1&&next.controller.get(first).verified&&!next.controller.get(first).frameCurrent&&next.controller.failure()==null);
            old.connection.disconnect(true);check(next.controller.failure()==null);
            refused("cdp_download_connection_changed",()->old.connection.turn(current));
            refused("cdp_download_connection_changed",()->old.connection.event(current,"Fetch.requestPaused",null,new JSONObject()));
            refused("cdp_download_owner_changed",()->next.connection.turn(original));
            check(next.claims.get()==0&&next.reads.get()==0&&next.controller.failure()==null);
            String second=next.offer("second-original");next.chunks.add(chunk(7));next.controller.accept(second,8,60000);next.complete(second);
            MdoBrowserCdpDownloads.Snapshot[] rows=next.controller.list();check(rows.length==2&&rows[0].id.equals(first)&&rows[1].id.equals(second)&&rows[0].sequence<rows[1].sequence&&test.budget.used()==2);
            MdoBrowserDownloadQueue.Read read=next.controller.read(first,0,1);await(read::finished);byte[] bytes=read.take();check(Arrays.equals(bytes,new byte[]{3}));Arrays.fill(bytes,(byte)0);
            check(old.claims.get()==1&&old.reads.get()==1&&old.closes.get()==1&&old.aborts.get()==1&&next.claims.get()==1&&next.reads.get()==1);
            next.release(first);next.release(second);check(test.budget.used()==0&&test.host.threads.size()==1);
        }
    }
    private static void verifying(boolean tamper)throws Exception{
        CdpDownloadsProbe.VerifyBarrier barrier=new CdpDownloadsProbe.VerifyBarrier();
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("same-page-verifying-"+tamper,barrier)){
            try{
                CdpDownloadsProbe.Source old=test.source();String id=old.offer("original-before-rearm");old.chunks.add(chunk(4));old.controller.accept(id,8,60000);
                check(barrier.entered.await(3,TimeUnit.SECONDS)&&old.snapshot(id).sourceCleanupConfirmed&&!old.snapshot(id).verified);retire(test,old);
                CdpDownloadsProbe.Source next=test.rearm(old);check(next.context.view==old.context.view&&!next.snapshot(id).verified&&test.budget.used()==1);
                // Old source verification never requests old stream data from
                // the newly proved physical native connection.
                String fresh=next.offer("fresh-while-old-verifies");next.controller.cancel(fresh);await(()->next.state(fresh,"cancelled"));next.release(fresh);
                if(tamper){Path leaf;try(java.util.stream.Stream<Path> paths=Files.list(test.root)){leaf=paths.filter(path->path.getFileName().toString().startsWith("mdo-bu-download-")).findFirst().get();}
                    try(java.nio.channels.SeekableByteChannel file=Files.newByteChannel(leaf,java.nio.file.StandardOpenOption.WRITE)){file.position(MdoBrowserUploadFiles.HEADER_BYTES);file.write(java.nio.ByteBuffer.wrap(new byte[]{9}));}}
                barrier.resume.countDown();
                if(tamper){await(()->next.state(id,"failed"));check(next.snapshot(id).error.equals("download_hash_mismatch")&&!next.snapshot(id).verified);}
                else{next.complete(id);check(next.snapshot(id).verified);}
                check(next.claims.get()==0&&next.reads.get()==0&&old.claims.get()==1&&old.reads.get()==1);next.release(id);
            }finally{barrier.resume.countDown();}
        }
    }
    private static void cleanupAndLimits()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("rearm-limits")){
            MdoBrowserCdpDownloads unbound=new MdoBrowserCdpDownloads(test.files,test.budget);
            try{unbound.connection().disconnect();refused("download_connection_retiring",unbound::rearm);
                unbound.connection().disconnect(true);unbound.rearm();refused("download_connection_retiring",unbound::rearm);
            }finally{unbound.close();}
            CdpDownloadsProbe.Source source=test.source();String id=source.offer("unconfirmed-source");
            source.connection.disconnect();refused("download_connection_retiring",source.controller::rearm);
            test.host.worker.cancelNetwork(source.context.adapter.marker().lease());await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
            refused("download_source_cleanup_unconfirmed",source.controller::rearm);check(!source.snapshot(id).sourceCleanupConfirmed);
        }
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("shared-generation-budget")){
            CdpDownloadsProbe.Source old=test.source();String id=old.offer("retained-budget");old.chunks.add(chunk(5));old.controller.accept(id,8,60000);old.complete(id);retire(test,old);
            CdpDownloadsProbe.Source next=test.rearm(old);for(int i=0;i<31;i++)check(test.budget.acquire());
            next.context.wire().queue(new JSONObject().put("method","Fetch.requestPaused").put("params",CdpBodyProbe.paused("new-over-budget").put("frameId","frame-owned")));
            await(()->next.aborts.get()==1);check(next.controller.list().length==1&&next.snapshot(id).verified&&next.controller.failure().equals("download_receipt_limit"));
            for(int i=0;i<31;i++)test.budget.release();next.release(id);retire(test,next);
            java.lang.reflect.Field count=next.controller.getClass().getDeclaredField("generations");count.setAccessible(true);count.setInt(next.controller,4096);
            refused("download_generation_limit",next.controller::rearm);
        }
    }
    private static void sameDocumentNamespace()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("same-dom-namespace")){
            CdpDownloadsProbe.Source current=test.source();for(int i=1;i<16;i++){retire(test,current);current=test.rearm(current);}
            check(current.context.view.evaluations==16);retire(test,current);
            CdpNetworkBindingProbe.Context context=test.host.source(current.context.view,current.quota);
            MdoBrowserCdpDownloads.Connection ticket=current.controller.rearm();int before=test.host.states.size();
            test.host.worker.startNetwork(context.adapter.marker(),1500,ticket,state->{test.host.states.add(state);if(!state.ready)ticket.disconnect(state.cleanupConfirmed);});
            await(()->{test.host.ui.drain();return test.host.states.size()>before;});MdoBrowserCdpNetworks.Result result=test.host.states.get(before);
            check(!result.ready&&result.code.equals("cdp_binding_document_limit")&&result.cleanupConfirmed&&context.view.evaluations==16&&context.methods.stream().noneMatch("Fetch.enable"::equals));
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();Path root=Paths.get(args[0]);if(Files.exists(root))throw new AssertionError("Interrupted rearm evidence preserved");Files.createDirectory(root);CdpDownloadsProbe.ownBase(root);
        retainedAndStale();verifying(false);verifying(true);cleanupAndLimits();sameDocumentNamespace();Files.delete(root);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"same_physical_view_rearm\":true,\"retained_original_downloads\":true,\"stale_callbacks_fenced\":true,\"same_document_quota\":true,\"real_guarded_files\":true,\"framework_verified\":false,\"provider_qualified\":false}");
    }
}
