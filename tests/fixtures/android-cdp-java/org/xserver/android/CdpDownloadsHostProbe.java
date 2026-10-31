package org.xserver.android;

import android.os.Handler;
import android.os.SystemClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Exact product commands over actual controller/protocol worker/files.
 * View/channel/framework peers are controlled; no installed provider proof. */
public final class CdpDownloadsHostProbe {
    private static int checks;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Download host check "+(checks+1));checks++;}
    private static void refused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected "+code);}catch(Exception failure){check(code.equals(failure.getMessage()));}
    }
    private static void await(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()){
            if(System.nanoTime()>=end)throw new AssertionError("Download host wait expired");Handler.drain();Thread.sleep(2);
        }checks++;
    }
    private static JSONObject command(String action,String identity)throws Exception{return new JSONObject().put("action",action).put("download",identity);}
    private static void commands()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("commands")){
            CdpDownloadsProbe.Source source=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",source.context.view,source.controller);
            String identity=source.offer("host-original-1.0");host.execute("tab","list",new JSONObject().put("action","list"));
            check(host.result("list").getJSONArray("downloads").length()==1&&source.claims.get()==0);
            host.execute("tab","get",command("get",identity));check(host.result("get").getString("id").equals(identity));
            refused("download_arguments_invalid",()->host.execute("tab","fraction",command("accept",identity).put("max_bytes",1.5)));
            refused("download_arguments_invalid",()->host.execute("tab","string",command("accept",identity).put("max_bytes","8")));
            refused("download_arguments_invalid",()->host.execute("tab","timeout",command("accept",identity).put("timeout_ms",999)));
            check(source.claims.get()==0&&source.controller.get(identity).state.equals("pending"));
            refused("download_identity_unavailable",()->host.execute("tab","foreign",command("get","d-unowned")));
            source.chunks.add(new JSONObject().put("data","AQ==").put("base64Encoded",true).put("eof",true));
            host.execute("tab","accept",command("accept",identity).put("max_bytes",8).put("timeout_ms",60000));
            check(host.result("accept").getString("state").equals("receiving"));source.complete(identity);
            host.execute("tab","completed",command("get",identity));check(host.result("completed").getLong("size")==1&&host.result("completed").getString("sha256").length()==64);
            refused("download_arguments_invalid",()->host.execute("tab","fraction-read",command("read",identity).put("offset",0.5)));
            refused("download_arguments_invalid",()->host.execute("tab","string-read",command("read",identity).put("limit","8")));
            refused("download_range_invalid",()->host.execute("tab","range-read",command("read",identity).put("offset",2)));
            host.execute("tab","read",command("read",identity));await(()->host.replies.containsKey("read"));
            JSONObject range=host.result("read");check(range.getString("id").equals(identity)&&range.getString("base64").equals("AQ==")&&range.getLong("offset")==0&&range.getLong("next")==1&&range.getInt("bytes")==1&&range.getBoolean("eof"));
            check(range.getString("sha256").equals(host.result("completed").getString("sha256"))&&host.pendingReads()==0&&test.files.retainedReads()==0);
            host.execute("tab","release",command("release",identity));await(()->host.replies.containsKey("release"));
            check(host.result("release").getBoolean("released")&&host.pendingReleases()==0&&source.controller.list().length==0&&test.budget.used()==0);
        }
    }
    private static void cancelAndDisconnectedInspect()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("cancel-host")){
            CdpDownloadsProbe.Source source=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",source.context.view,source.controller);
            String identity=source.offer("host-original-2.0");host.execute("tab","cancel",command("cancel",identity));
            check(host.result("cancel").getString("state").equals("cancelling"));await(()->source.state(identity,"cancelled"));
            check(source.claims.get()==0&&source.aborts.get()==1);host.execute("tab","release",command("release",identity));check(host.result("release").getBoolean("released"));
        }
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("disconnected-host")){
            CdpDownloadsProbe.Source source=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",source.context.view,source.controller);
            String identity=source.offer("host-original-3.0");source.chunks.add(new JSONObject().put("data","Ag==").put("base64Encoded",true).put("eof",true));source.controller.accept(identity,8,60000);source.complete(identity);
            test.host.worker.cancelNetwork(source.context.adapter.marker().lease());await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});host.networkViews.get("tab").retired=true;
            host.execute("tab","get",command("get",identity));check(host.result("get").getString("state").equals("completed"));
            refused("download_connection_unavailable",()->host.execute("tab","accept",command("accept",identity)));
            host.execute("tab","read",command("read",identity));await(()->host.replies.containsKey("read"));check(host.result("read").getString("base64").equals("Ag=="));
            host.execute("tab","release",command("release",identity));await(()->host.replies.containsKey("release"));check(host.result("release").getBoolean("released"));
        }
    }
    private static MdoBrowserDownloadQueue.Ticket ticket(CdpDownloadsProbe.Source source)throws Exception{
        java.lang.reflect.Field rows=source.controller.getClass().getDeclaredField("rows");rows.setAccessible(true);
        for(Object row:(Object[])rows.get(source.controller))if(row!=null){java.lang.reflect.Field field=row.getClass().getDeclaredField("ticket");field.setAccessible(true);return (MdoBrowserDownloadQueue.Ticket)field.get(row);}
        throw new AssertionError("Missing original ticket");
    }
    private static void revokedRead(int mode)throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("revoked-read-"+mode)){
            CdpDownloadsProbe.Source source=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",source.context.view,source.controller);
            String identity=source.offer("host-read-original-"+mode);source.chunks.add(new JSONObject().put("data","BA==").put("base64Encoded",true).put("eof",true));
            source.controller.accept(identity,8,60000);source.complete(identity);
            CdpDownloadQueueProbe.ReadBarrier barrier=new CdpDownloadQueueProbe.ReadBarrier(ticket(source));
            long originalClock=SystemClock.now;SystemClock.now=SystemClock.elapsedRealtime();
            try{
                host.execute("tab","read",command("read",identity));check(barrier.entered.await(1,TimeUnit.SECONDS)&&host.pendingReads()==1&&!host.replies.containsKey("read"));
                if(mode==0)host.channel.lease=host.channel.new Lease();
                if(mode==1)host.networkViews.remove("tab");
                if(mode==2)host.stopped=true;
                if(mode==3)SystemClock.now+=5000;
                if(mode==4)host.cancelWaiters();
                if(mode==5)Handler.fail=true;
                SystemClock.now+=25;Handler.drain();check(host.pendingReads()==0);
                if(mode==1)check(host.replies.get("read").error.equals("download_owner_changed"));
                else if(mode==3||mode==5)check(host.replies.get("read").error.equals("download_read_timeout"));
                else check(!host.replies.containsKey("read"));
                check(test.files.retainedReads()==1); // in-flight descriptor is still observed
            }finally{barrier.resume.countDown();Handler.fail=false;SystemClock.now=originalClock;host.cancelWaiters();}
            await(()->test.files.retainedReads()==0);check(!host.replies.containsKey("read")||host.replies.get("read").result==null);
            source.release(identity);
        }
    }
    private static void uncertainRelease()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("uncertain-host")){
            // An already verified file is replaced just before release. The
            // guarded backend preserves it, and the UI cannot invent success.
            CdpDownloadsProbe.Source source=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",source.context.view,source.controller);
            String identity=source.offer("host-original-4.0");source.chunks.add(new JSONObject().put("data","Aw==").put("base64Encoded",true).put("eof",true));source.controller.accept(identity,8,60000);source.complete(identity);
            Path leaf;try(java.util.stream.Stream<Path> paths=Files.list(test.root)){leaf=paths.filter(path->path.getFileName().toString().startsWith("mdo-bu-download-")).findFirst().get();}
            Path saved=test.root.resolve("fixture-original");Files.move(leaf,saved);Files.write(leaf,new byte[]{9});
            long originalClock=SystemClock.now;SystemClock.now=SystemClock.elapsedRealtime();
            try{
                host.execute("tab","stale",command("release",identity));await(test.files::cleanupFailed);test.expectedCleanupFailure=true;
                check(!host.replies.containsKey("stale")&&host.pendingReleases()==1&&Files.readAllBytes(leaf)[0]==9);
                host.channel.lease=host.channel.new Lease();SystemClock.now+=25;Handler.drain();check(host.pendingReleases()==0&&!host.replies.containsKey("stale"));
                host.execute("tab","timeout",command("release",identity));check(!host.replies.containsKey("timeout"));SystemClock.now+=5000;Handler.drain();
                check(host.pendingReleases()==0&&host.replies.get("timeout").error.equals("download_release_unconfirmed"));
                check(source.controller.get(identity).state.equals("releasing")&&source.controller.get(identity).error.equals("download_file_replaced")&&source.controller.get(identity).failureSource.equals("cache"));
                check(Files.readAllBytes(leaf)[0]==9&&test.budget.used()==1);
            }finally{SystemClock.now=originalClock;host.cancelWaiters();Files.delete(leaf);Files.move(saved,leaf);}
            await(()->{try{return source.controller.list().length==0;}catch(Exception error){throw new AssertionError(error);}});check(test.budget.used()==0);
        }
    }
    private static void pendingFilesSurviveRearm()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("pending-files-rearm")){
            CdpDownloadsProbe.Source old=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",old.context.view,old.controller);
            String first=old.offer("read-across-rearm");old.chunks.add(new JSONObject().put("data","Bg==").put("base64Encoded",true).put("eof",true));old.controller.accept(first,8,60000);old.complete(first);
            String second=old.offer("release-across-rearm");old.chunks.add(new JSONObject().put("data","Bw==").put("base64Encoded",true).put("eof",true));old.controller.accept(second,8,60000);old.complete(second);
            CdpDownloadQueueProbe.ReadBarrier barrier=new CdpDownloadQueueProbe.ReadBarrier(ticket(old));
            try{
                host.execute("tab","old-read",command("read",first));check(barrier.entered.await(1,TimeUnit.SECONDS)&&host.pendingReads()==1);
                host.execute("tab","old-release",command("release",second));check(host.pendingReleases()==1&&!host.replies.containsKey("old-release"));
                old.controller.quiesce();test.host.worker.retireNetwork(old.context.adapter.marker().lease());await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
                CdpDownloadsProbe.Source next=test.rearm(old);host.add("tab",next.context.view,next.controller);
                check(next.context.view==old.context.view&&next.controller==old.controller);Thread.sleep(25);Handler.drain();
                check(host.pendingReads()==1&&host.pendingReleases()==1&&!host.replies.containsKey("old-read")&&!host.replies.containsKey("old-release"));
                barrier.resume.countDown();await(()->host.replies.containsKey("old-read")&&host.replies.containsKey("old-release"));
                check(host.result("old-read").getString("base64").equals("Bg==")&&host.result("old-release").getBoolean("released"));
                check(host.pendingReads()==0&&host.pendingReleases()==0&&next.controller.get(first).verified&&test.budget.used()==1);next.release(first);
            }finally{barrier.resume.countDown();host.cancelWaiters();}
        }
    }
    private static void closedPageRetainsDownloads()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("closed-page-retained-download")){
            CdpDownloadsProbe.Source source=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",source.context.view,source.controller);
            String id=source.offer("closed-page-original");source.chunks.add(new JSONObject().put("data","AQIDBA==").put("base64Encoded",true).put("eof",true));source.controller.accept(id,64,10000);source.complete(id);
            host.pageClosed.add("tab");JSONObject closed=host.state("tab");
            check(!closed.getBoolean("closed_by_page")&&closed.getBoolean("close_requested")&&closed.getJSONArray("downloads").length()==1);
            test.host.worker.cancelNetwork(source.context.adapter.marker().lease());await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
            host.networkViews.get("tab").retired=true;host.networkViews.get("tab").protocolDrained=true;
            check(!host.state("tab").getBoolean("closed_by_page")&&source.controller.get(id).verified);
            host.execute("tab","closed-readable",command("read",id));await(()->host.replies.containsKey("closed-readable"));
            check(host.result("closed-readable").getString("base64").equals("AQIDBA=="));
            CdpDownloadQueueProbe.ReadBarrier barrier=new CdpDownloadQueueProbe.ReadBarrier(ticket(source));
            try{
                host.execute("tab","close-read",command("read",id));check(barrier.entered.await(1,TimeUnit.SECONDS));
                host.execute("tab","close-release",command("release",id));check(host.pendingReads()==1&&host.pendingReleases()==1);
                MdoBrowserCdpDownloads.Release release=host.releaseReceipt("close-release");barrier.resume.countDown();
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while(!release.finished()){if(System.nanoTime()>=end)throw new AssertionError("Retained page release did not finish");Thread.sleep(2);}
                check(source.controller.list().length==0&&test.budget.used()==0&&host.pendingReads()==1&&host.pendingReleases()==1);
                check(!host.state("tab").getBoolean("closed_by_page"));
                await(()->host.replies.containsKey("close-read")&&host.replies.containsKey("close-release"));
                check("download_read_cancelled".equals(host.replies.get("close-read").error)&&host.result("close-release").getBoolean("released"));
                check(host.state("tab").getBoolean("closed_by_page")&&host.state("tab").getBoolean("close_requested"));
                host.networkViews.get("tab").protocolDrained=false;check(!host.state("tab").getBoolean("closed_by_page"));
                host.networkViews.get("tab").protocolDrained=true;check(host.state("tab").getBoolean("closed_by_page"));
                host.networkCleanupFailed=true;check(!host.state("tab").getBoolean("closed_by_page"));host.networkCleanupFailed=false;
            }finally{barrier.resume.countDown();host.cancelWaiters();}
        }
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("closed-page-no-download")){
            CdpDownloadsProbe.Source source=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",source.context.view,source.controller);
            host.pageClosed.add("tab");check(!host.state("tab").getBoolean("closed_by_page"));
            test.host.worker.cancelNetwork(source.context.adapter.marker().lease());await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
            host.networkViews.get("tab").retired=true;host.networkViews.get("tab").protocolDrained=true;check(host.state("tab").getBoolean("closed_by_page"));
            host.pageClosed.remove("tab");check(!host.state("tab").getBoolean("close_requested")&&!host.state("tab").getBoolean("closed_by_page"));
            host.pageClosed.add("ordinary-manual");check(host.state("ordinary-manual").getBoolean("closed_by_page"));
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();Path root=Paths.get(args[0]);if(Files.exists(root))throw new AssertionError("Interrupted host download evidence preserved");Files.createDirectory(root);CdpDownloadsProbe.ownBase(root);
        commands();cancelAndDisconnectedInspect();uncertainRelease();pendingFilesSurviveRearm();for(int mode=0;mode<6;mode++)revokedRead(mode);closedPageRetainsDownloads();Files.delete(root);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"exact_product_commands\":true,\"real_native_controller_files\":true,\"closed_page_retains_downloads\":true,\"framework_verified\":false,\"provider_qualified\":false,\"product_downloads_activated\":false}");
    }
}
