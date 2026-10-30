package org.xserver.android;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Actual shared worker, native connection/controller and guarded file queue.
 * Controlled UI/renderer peers do not qualify an installed Android provider. */
public final class CdpNetworkNavigationProbe {
    private static int checks;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Network navigation check "+(checks+1));checks++;}
    private static void await(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean()){
            if(System.nanoTime()>=end)throw new AssertionError("Network navigation wait expired");
            android.os.Handler.drain();Thread.sleep(2);
        }checks++;
    }
    private static void refused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected "+code);}catch(IOException failure){check(code.equals(failure.getMessage())&&failure.getCause()==null);}
    }
    private static Object gate(CdpDownloadsProbe.Source source)throws Exception{
        java.lang.reflect.Field field=source.controller.getClass().getDeclaredField("gate");field.setAccessible(true);return field.get(source.controller);
    }
    private static JSONObject answer(int id,boolean download)throws Exception{
        return new JSONObject().put("id",id).put("result",new JSONObject().put("frameId","frame-owned").put("isDownload",download));
    }
    private static MdoBrowserCdpNetworks.Navigation navigate(CdpDownloadsProbe.Case test,CdpDownloadsProbe.Source source,int timeout)throws Exception{
        return test.host.worker.navigateNetwork(source.context.adapter.marker().lease(),"https://navigation.invalid/original",timeout);
    }
    private static void originalDownloadProgress()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("original-body-progress")){
            CdpDownloadsProbe.Source source=test.source(),other=test.source();AtomicInteger request=new AtomicInteger();
            CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                JSONObject params=command.getJSONObject("params");
                check(!test.host.ui.onThread()&&params.length()==2&&params.getString("frameId").equals("frame-owned")&&params.getString("url").equals("https://navigation.invalid/original")&&!command.has("sessionId"));
                request.set(command.getInt("id"));return true;
            };
            refused("cdp_navigation_arguments_invalid",()->test.host.worker.navigateNetwork(source.context.adapter.marker().lease(),"file:///private",1000));
            MdoBrowserCdpNetworks.Navigation original=navigate(test,source,10000);await(()->request.get()>0);
            check(original.dispatched()&&original.result()==null);
            refused("cdp_navigation_pending",()->navigate(test,source,10000));
            String a=source.offer("original-navigation-body"),b=other.offer("independent-body");
            source.chunks.add(new JSONObject().put("data","AQI=").put("base64Encoded",true).put("eof",true));
            other.chunks.add(new JSONObject().put("data","Aw==").put("base64Encoded",true).put("eof",true));
            source.controller.accept(a,8,60000);other.controller.accept(b,8,60000);source.complete(a);other.complete(b);
            check(original.result()==null&&source.claims.get()==1&&source.reads.get()==1&&source.aborts.get()==1&&test.host.threads.size()==1);
            source.context.wire().queue(answer(request.get(),true));await(()->original.result()!=null);
            MdoBrowserCdpSession.NavigationResult result=original.result();
            check(result.acknowledged&&result.ok&&result.downloadKnown&&result.download&&source.snapshot(a).verified);
            original.cancel();check(original.result()==result);source.release(a);other.release(b);
        }
    }
    private static void queuedDecisions()throws Exception{
        for(int mode=0;mode<3;mode++)try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("queued-"+mode)){
            CdpDownloadsProbe.Source source=test.source();AtomicInteger sends=new AtomicInteger();
            CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{if(command.getString("method").equals("Page.navigate")){sends.incrementAndGet();wire.queue(answer(command.getInt("id"),false));return true;}return previous.send(command,wire);};
            String pending=mode==2?source.offer("pending-before-navigation"):null;
            MdoBrowserCdpNetworks.Navigation original;
            synchronized(gate(source)){
                original=navigate(test,source,1000);
                if(mode==0){long before=System.nanoTime();original.cancel();check(System.nanoTime()-before<TimeUnit.MILLISECONDS.toNanos(100));}
                if(mode==1)test.host.time.addAndGet(1000);
            }
            await(()->original.result()!=null);
            check(!original.dispatched()&&!original.result().acknowledged&&!original.result().effectMayHaveOccurred&&sends.get()==0);
            check(original.result().code.equals(mode==0?"cdp_navigation_cancelled":mode==1?"cdp_navigation_timeout":"cdp_navigation_download_pending"));
            if(pending!=null){source.controller.cancel(pending);await(()->source.state(pending,"cancelled"));source.release(pending);}
        }
    }
    private static void inFlightCancelAndReplacement()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("sent-original")){
            CdpDownloadsProbe.Source source=test.source();CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1);AtomicInteger sends=new AtomicInteger();
            CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                sends.incrementAndGet();entered.countDown();if(!resume.await(3,TimeUnit.SECONDS))throw new IOException("fixture_barrier_timeout");wire.queue(answer(command.getInt("id"),false));return true;
            };
            MdoBrowserCdpNetworks.Navigation original=navigate(test,source,10000);
            try{
                check(entered.await(3,TimeUnit.SECONDS));long before=System.nanoTime();original.cancel();
                check(System.nanoTime()-before<TimeUnit.MILLISECONDS.toNanos(100)&&original.dispatched()&&original.result()==null);
            }finally{resume.countDown();}
            await(()->original.result()!=null);MdoBrowserCdpSession.NavigationResult terminal=original.result();
            check(terminal.acknowledged&&terminal.ok&&!terminal.download&&sends.get()==1);
            source.controller.quiesce();test.host.worker.retireNetwork(source.context.adapter.marker().lease());
            await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
            CdpDownloadsProbe.Source next=test.rearm(source);
            refused("cdp_network_owner_unavailable",()->navigate(test,source,1000));
            original.cancel();check(original.result()==terminal&&next.context.view==source.context.view&&next.controller.failure()==null&&sends.get()==1);
        }
    }
    private static void commit(CdpDownloadsProbe.Source source,String loader,boolean opened,boolean child)throws Exception{
        JSONObject frame=new JSONObject().put("id",child?"child-proof":"frame-owned").put("loaderId",loader);
        if(child)frame.put("parentId","frame-owned");
        source.context.wire().queue(new JSONObject().put("method",opened?"Page.documentOpened":"Page.frameNavigated").put("params",new JSONObject().put("frame",frame)));
    }
    private static void commitOrdering()throws Exception{
        for(int mode=0;mode<6;mode++)try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("commit-order-"+mode)){
            CdpDownloadsProbe.Source source=test.source();AtomicInteger request=new AtomicInteger();
            CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);request.set(command.getInt("id"));return true;};
            MdoBrowserCdpNetworks.Navigation original=navigate(test,source,10000);await(()->request.get()>0);
            JSONObject ack=new JSONObject().put("id",request.get()).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","original-loader").put("isDownload",false));
            if(mode==0)source.context.wire().queue(ack);
            if(mode==2)commit(source,"wrong-loader",false,false);
            if(mode==3)commit(source,"original-loader",true,false);
            if(mode==4)commit(source,"original-loader",false,true);
            if(mode!=0)source.context.wire().queue(ack);
            await(()->original.result()!=null);check(!original.pageCommitted()&&!original.pageCurrent()&&original.pageDocument()==null);
            commit(source,"original-loader",false,false);await(original::pageCommitted);
            String document=original.pageDocument();check(original.pageCurrent()&&document.startsWith("nd-")&&!document.contains("original-loader")&&!document.contains("frame-owned"));
            if(mode==5)commit(source,"original-loader",true,false);else commit(source,"next-loader",false,false);
            await(()->!original.pageCurrent());check(original.pageCommitted()&&original.pageDocument().equals(document));
        }
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("commit-before-ack")){
            CdpDownloadsProbe.Source source=test.source();AtomicInteger request=new AtomicInteger();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                wire.queue(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","original-loader"))));
                wire.queue(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","newer-loader"))));
                request.set(command.getInt("id"));return true;};
            MdoBrowserCdpNetworks.Navigation original=navigate(test,source,10000);await(()->request.get()>0);
            source.context.wire().queue(new JSONObject().put("id",request.get()).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","original-loader")));
            await(original::pageCommitted);check(original.result().acknowledged&&!original.pageCurrent());
        }
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("commit-before-admission")){
            CdpDownloadsProbe.Source source=test.source();String old=source.offer("old-root-before-navigation");source.controller.cancel(old);await(()->source.state(old,"cancelled"));
            commit(source,"reused-loader",false,false);await(()->!source.snapshot(old).frameCurrent);source.release(old);
            CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","reused-loader")));return true;};
            MdoBrowserCdpNetworks.Navigation original=navigate(test,source,10000);await(()->original.result()!=null);check(!original.pageCommitted());
            commit(source,"reused-loader",false,false);await(original::pageCommitted);check(original.pageCurrent());
        }
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("commit-bounded-history")){
            CdpDownloadsProbe.Source source=test.source(),other=test.source();AtomicInteger request=new AtomicInteger();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);request.set(command.getInt("id"));return true;};
            MdoBrowserCdpNetworks.Navigation original=navigate(test,source,10000);await(()->request.get()>0);
            commit(source,"evicted-loader",false,false);for(int i=0;i<33;i++)commit(source,"churn-"+i,false,false);
            commit(other,"evicted-loader",false,false);
            source.context.wire().queue(new JSONObject().put("id",request.get()).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","evicted-loader")));
            await(()->original.result()!=null);check(!original.pageCommitted()&&original.pageDocument()==null);
            commit(source,"evicted-loader",false,false);await(original::pageCommitted);check(original.pageCurrent());
        }
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("committed-source-close")){
            CdpDownloadsProbe.Source source=test.source();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","source-close-loader")));
                wire.queue(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","source-close-loader"))));return true;};
            MdoBrowserCdpNetworks.Navigation original=navigate(test,source,10000);await(original::pageCurrent);String document=original.pageDocument();
            MdoBrowserCdpSession.NavigationResult result=original.result();test.host.worker.cancelNetwork(source.context.adapter.marker().lease());
            await(()->{test.host.ui.drain();return test.host.worker.networkPending()==0;});
            check(original.pageCommitted()&&!original.pageCurrent()&&document.equals(original.pageDocument())&&original.result()==result);
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();Path root=Paths.get(args[0]);if(Files.exists(root))throw new AssertionError("Interrupted navigation evidence preserved");
        Files.createDirectory(root);CdpDownloadsProbe.ownBase(root);originalDownloadProgress();queuedDecisions();inFlightCancelAndReplacement();commitOrdering();Files.delete(root);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"original_ui_admission\":true,\"shared_worker_body_progress\":true,\"exact_queued_cancellation\":true,\"original_native_commit_matching\":true,\"bounded_commit_history\":true,\"real_guarded_files\":true,\"framework_verified\":false,\"provider_qualified\":false}");
    }
}
