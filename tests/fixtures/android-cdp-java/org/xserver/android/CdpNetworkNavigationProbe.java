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
    private static void documentGuards()throws Exception{
        for(int mode=0;mode<7;mode++)try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("document-guard-"+mode)){
            final int scenario=mode;CdpDownloadsProbe.Source source=test.source();
            CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","original-loader").put("isDownload",false)));return true;
            };
            MdoBrowserCdpNetworks.Navigation original=navigate(test,source,10000);
            await(()->original.result()!=null);commit(source,"original-loader",false,false);await(original::pageCurrent);
            MdoBrowserCdpMarker document=test.host.document();CdpBindingProbe.Factory peer=test.host.documents.get(document);
            java.util.concurrent.atomic.AtomicReference<String> loader=new java.util.concurrent.atomic.AtomicReference<>(mode==1?"wrong-loader":"original-loader");
            if(mode==2)test.host.documentTargets.put(document,"foreign-target");
            peer.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.getFrameTree"))return false;
                wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameTree",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId",loader.get())))));return true;
            };
            if(mode==6)original.cancel();
            AtomicInteger evaluations=new AtomicInteger();java.util.concurrent.atomic.AtomicReference<byte[]> payload=new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> receipt=new java.util.concurrent.atomic.AtomicReference<>();
            test.host.worker.submitGuarded(document,1500,true,original,(binding,deadline)->{
                evaluations.incrementAndGet();byte[] bytes=new byte[]{7,8,9};payload.set(bytes);
                if(scenario==3)loader.set("next-loader");
                if(scenario==4)original.cancel();
                if(scenario==5)source.context.adapter.marker().close();
                return bytes;
            },result->{
                check(!result.effectMayHaveOccurred||evaluations.get()==1);
                check((result.bytes!=null)==(scenario==0));if(result.bytes!=null)check(result.bytes[0]==7);
                receipt.set(result);
            });
            await(()->{test.host.ui.drain();return receipt.get()!=null;});
            MdoBrowserCdpWorker.Result result=receipt.get();
            check(evaluations.get()==(mode==1||mode==2||mode==6?0:1));
            check(result.effectMayHaveOccurred==(evaluations.get()==1));
            check(mode==0?result.code==null:result.code!=null);
            if(mode==1||mode==2||mode==3)check("cdp_navigation_document_changed".equals(result.code));
            if(mode==4||mode==6)check("cdp_navigation_cancelled".equals(result.code));
            if(payload.get()!=null)for(byte value:payload.get())check(value==0);
        }
    }
    private static JSONObject within(String frame,String url)throws Exception{
        return new JSONObject().put("method","Page.navigatedWithinDocument").put("params",new JSONObject().put("frameId",frame).put("url",url).put("navigationType","fragment"));
    }
    private static void sameDocumentDestinations()throws Exception{
        final String url="https://navigation.invalid/original#target";
        for(int mode=0;mode<18;mode++)try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("same-document-"+mode)){
            final int scenario=mode;CdpDownloadsProbe.Source source=test.source();AtomicInteger request=new AtomicInteger();
            CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            source.context.peer.extra=(command,wire)->{if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                request.set(command.getInt("id"));if(scenario==1)wire.queue(within("frame-owned",url));
                if(scenario==16)wire.queue(new JSONObject().put("method","Page.documentOpened").put("params",new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","load-owned"))));
                if(scenario==6){wire.queue(within("frame-owned",url));for(int i=0;i<32;i++)wire.queue(within("frame-owned",url+i));}
                JSONObject ack=answer(command.getInt("id"),scenario==4);if(scenario==5)ack.getJSONObject("result").remove("isDownload");wire.queue(ack);return true;
            };
            if(mode==17){
                source.context.wire().queue(within("frame-owned",url));
                MdoBrowserCdpMarker barrier=test.host.document();java.util.concurrent.atomic.AtomicReference<Boolean> drained=new java.util.concurrent.atomic.AtomicReference<>();
                test.host.worker.submit(barrier,1500,false,(binding,deadline)->new byte[]{1},result->{check(result.code==null);drained.set(true);});
                await(()->{test.host.ui.drain();return drained.get()!=null;});check(source.context.wire().replies.isEmpty());
            }
            MdoBrowserCdpNetworks.Navigation original=test.host.worker.navigateNetwork(source.context.adapter.marker().lease(),url,10000);
            await(()->original.result()!=null);check(original.sameDocument()==(mode!=4&&mode!=5));
            if(mode==0||mode==2||mode==3||mode==6||mode==17){
                check(!original.pageCommitted());
                source.context.wire().queue(within(mode==3?"unrelated-child":"frame-owned",mode==2?url+"wrong":url));
                if(mode==2||mode==3){source.context.wire().queue(within("frame-owned",url));}
            }else if(mode!=1)source.context.wire().queue(within("frame-owned",url));
            if(mode==4||mode==5||mode==16){
                MdoBrowserCdpMarker barrier=test.host.document();java.util.concurrent.atomic.AtomicReference<Boolean> done=new java.util.concurrent.atomic.AtomicReference<>();
                test.host.worker.submit(barrier,1500,false,(binding,deadline)->new byte[]{1},result->{check(result.code==null);done.set(true);});
                await(()->{test.host.ui.drain();return done.get()!=null;});check(!original.pageCommitted());continue;
            }
            await(original::pageCurrent);String document=original.pageDocument();
            check(document.startsWith("nd-")&&!document.contains("navigation.invalid")&&!document.contains("target"));
            MdoBrowserCdpMarker marker=test.host.document();CdpBindingProbe.Factory peer=test.host.documents.get(marker);
            java.util.concurrent.atomic.AtomicReference<String> fragment=new java.util.concurrent.atomic.AtomicReference<>(mode==7?"#wrong":"#target");
            java.util.concurrent.atomic.AtomicBoolean operationsEntered=new java.util.concurrent.atomic.AtomicBoolean();
            peer.extra=(command,wire)->{if(!command.getString("method").equals("Page.getFrameTree"))return false;
                JSONObject root=new JSONObject().put("id","frame-owned").put("loaderId","load-owned").put("url","https://navigation.invalid/original");
                if(scenario==10||scenario==12)root.put("url","https://navigation.invalid/original#target");else root.put("urlFragment",fragment.get());
                if(scenario==11)root.put("urlFragment",2);if(scenario==12)root.put("urlFragment","#target");if(scenario==13)root.remove("url");if(scenario==14)root.put("url","data:text/plain,untrusted");if(scenario==15&&operationsEntered.get())root.put("loaderId","changed-loader");
                wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameTree",new JSONObject().put("frame",root))));return true;
            };
            AtomicInteger operations=new AtomicInteger();java.util.concurrent.atomic.AtomicReference<byte[]> payload=new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> receipt=new java.util.concurrent.atomic.AtomicReference<>();
            test.host.worker.submitGuarded(marker,1500,true,original,(binding,deadline)->{
                operations.incrementAndGet();operationsEntered.set(true);byte[] bytes={3,4};payload.set(bytes);if(scenario==8)fragment.set("#wrong");if(scenario==9)original.cancel();return bytes;
            },result->{if(result.code==null)check(result.bytes!=null&&result.bytes[0]==3);else check(result.bytes==null);receipt.set(result);});
            await(()->{test.host.ui.drain();return receipt.get()!=null;});
            boolean refused=mode==7||mode==8||mode==9||mode>=11&&mode<=15;
            check((receipt.get().code!=null)==refused);check(operations.get()==(mode==7||mode>=11&&mode<=14?0:1));check(receipt.get().effectMayHaveOccurred==(operations.get()==1));
            if(mode==7||mode==8)check(receipt.get().code.equals("cdp_navigation_destination_changed"));
            if(payload.get()!=null)for(byte value:payload.get())check(value==0);
            source.context.wire().queue(within("frame-owned",url+"changed"));await(()->!original.pageCurrent());
            source.context.wire().queue(within("frame-owned",url));check(!original.pageCurrent()&&document.equals(original.pageDocument()));
        }
    }
    private static JSONObject controlHistory(int current,int replacement)throws Exception{
        org.json.JSONArray entries=new org.json.JSONArray();
        for(int i=0;i<3;i++)entries.put(new JSONObject().put("id",replacement!=0&&i==current?replacement:17+i).put("url",i==1?"https://history.invalid/current":"https://history.invalid/target").put("title","private-title"));
        return new JSONObject().put("currentIndex",current).put("entries",entries);
    }
    private static JSONObject nativeFrame(String loader,String url)throws Exception{return new JSONObject().put("id","frame-owned").put("loaderId",loader).put("url",url);}
    private static JSONObject started(String loader,String url,String type)throws Exception{
        return new JSONObject().put("method","Page.frameStartedNavigating").put("params",new JSONObject().put("frameId","frame-owned").put("loaderId",loader).put("url",url).put("navigationType",type));
    }
    private static void historyProofs()throws Exception{
        for(int mode=0;mode<14;mode++)try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("history-proof-"+mode)){
            final int scenario=mode;boolean reload=mode==3,within=mode==1,restored=mode==2;String action=reload?"reload":mode==4?"forward":"back";
            String destination=reload?"https://history.invalid/current":"https://history.invalid/target";
            CdpDownloadsProbe.Source source=test.source();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;
            AtomicInteger index=new AtomicInteger(1),replacement=new AtomicInteger(),mutations=new AtomicInteger(),historyQueries=new AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<String> loader=new java.util.concurrent.atomic.AtomicReference<>("load-owned"),url=new java.util.concurrent.atomic.AtomicReference<>("https://history.invalid/current");
            source.context.peer.extra=(command,wire)->{
                String method=command.getString("method");int id=command.getInt("id");
                if(method.equals("Page.getNavigationHistory")){historyQueries.incrementAndGet();wire.queue(new JSONObject().put("id",id).put("result",controlHistory(index.get(),replacement.get())));return true;}
                if(method.equals("Page.getFrameTree")){wire.queue(new JSONObject().put("id",id).put("result",new JSONObject().put("frameTree",new JSONObject().put("frame",nativeFrame(loader.get(),url.get())))));return true;}
                if(method.equals("Page.navigateToHistoryEntry")||method.equals("Page.reload")){
                    mutations.incrementAndGet();check(command.length()==3&&command.getJSONObject("params").length()==1);
                    check(reload?command.getJSONObject("params").getString("loaderId").equals("load-owned"):command.getJSONObject("params").getInt("entryId")== (action.equals("back")?17:19));
                    index.set(reload?1:action.equals("back")?0:2);url.set(destination);loader.set(within||restored?"load-owned":"control-loader");
                    if(scenario%2==0)wire.queue(new JSONObject().put("id",id).put("result",new JSONObject()));
                    wire.queue(started(within||restored?"request-token":"control-loader",destination,reload?"reload":within?"historySameDocument":"historyDifferentDocument"));
                    if(within)wire.queue(within("frame-owned",destination));
                    else wire.queue(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",nativeFrame(loader.get(),destination)).put("type",restored?"BackForwardCacheRestore":"Navigation")));
                    if(scenario%2!=0)wire.queue(new JSONObject().put("id",id).put("result",new JSONObject()));return true;
                }return previous.send(command,wire);
            };
            MdoBrowserCdpNetworks.Navigation original=test.host.worker.traverseNetwork(source.context.adapter.marker().lease(),action,10000);
            await(original::pageCurrent);check(original.result().ok&&original.result().acknowledged&&!original.result().loaderPresent&&!original.result().downloadKnown&&original.sameDocument()==within&&mutations.get()==1);
            check(historyQueries.get()==2);String originalDocument=original.pageDocument();
            MdoBrowserCdpMarker document=test.host.document();CdpBindingProbe.Factory reader=test.host.documents.get(document);
            if(mode==6)test.host.documentTargets.put(document,"foreign-target");
            reader.extra=(command,wire)->{
                String method=command.getString("method");int id=command.getInt("id");
                if(method.equals("Page.getNavigationHistory")){wire.queue(new JSONObject().put("id",id).put("result",controlHistory(index.get(),replacement.get())));return true;}
                if(method.equals("Page.getFrameTree")){wire.queue(new JSONObject().put("id",id).put("result",new JSONObject().put("frameTree",new JSONObject().put("frame",nativeFrame(loader.get(),url.get())))));return true;}
                return false;
            };
            if(mode==5)replacement.set(777);if(mode==7)url.set("https://history.invalid/other");if(mode==13)original.cancel();
            AtomicInteger evaluations=new AtomicInteger();java.util.concurrent.atomic.AtomicReference<byte[]> payload=new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> receipt=new java.util.concurrent.atomic.AtomicReference<>();
            JSONObject replacementStart=started("another-token",destination,"historyDifferentDocument");
            test.host.worker.submitGuarded(document,1500,true,original,(binding,deadline)->{
                evaluations.incrementAndGet();byte[] bytes=new byte[]{31,32};payload.set(bytes);
                if(scenario==8)replacement.set(778);
                if(scenario==9)loader.set("other-loader");
                if(scenario==10)original.cancel();
                if(scenario==11){source.context.wire().queue(replacementStart);}
                if(scenario==12)url.set("https://history.invalid/changed-after-read");
                return bytes;
            },result->{if(result.bytes!=null)check(result.bytes[0]==31);receipt.set(result);});
            await(()->{test.host.ui.drain();return receipt.get()!=null;});
            MdoBrowserCdpWorker.Result result=receipt.get();boolean success=mode<5;
            check((result.code==null)==success&&(result.bytes!=null)==success);
            check(evaluations.get()==(mode==5||mode==6||mode==7||mode==13?0:1));
            if(result.effectMayHaveOccurred!=(evaluations.get()==1))throw new AssertionError("History mode "+mode+" uncertainty "+result.code);
            check(true);if(payload.get()!=null)for(byte value:payload.get()){if(value!=0)throw new AssertionError("History mode "+mode+" uncleared payload, result "+result.code);check(true);}
            check(original.result().ok&&mutations.get()==1&&original.pageDocument().equals(originalDocument));
            if(mode==11)check(!original.pageCurrent());
        }
    }
    private static JSONObject requestEvent(String id,String loader)throws Exception{
        return new JSONObject().put("method","Network.requestWillBeSent").put("params",new JSONObject().put("requestId",id).put("loaderId",loader).put("frameId","frame-owned").put("type","Document").put("request",new JSONObject().put("url","https://same-url.invalid/never-retain").put("postData","fixture-only")));
    }
    private static JSONObject responseEvent(String id,String network)throws Exception{
        return new JSONObject().put("method","Fetch.requestPaused").put("params",CdpBodyProbe.paused(id).put("frameId","frame-owned").put("resourceType","Document").put("networkId",network));
    }
    private static void originalRequestAssociation()throws Exception{
        for(int mode=0;mode<5;mode++)try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("original-request-"+mode)){
            final int scenario=mode;CdpDownloadsProbe.Source source=test.source();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;AtomicInteger originalId=new AtomicInteger(),sends=new AtomicInteger();
            source.context.peer.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                originalId.set(command.getInt("id"));sends.incrementAndGet();
                if(scenario<3)wire.queue(requestEvent("network-original","download-loader"));wire.queue(responseEvent("interception-original","network-original"));
                if(scenario==0||scenario>=3)wire.queue(new JSONObject().put("id",originalId.get()).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","download-loader").put("isDownload",true)));
                return true;
            };
            MdoBrowserCdpNetworks.Navigation navigation=navigate(test,source,15000);
            await(()->{try{return source.controller.list().length==1;}catch(IOException error){throw new AssertionError(error);}});
            String original=source.controller.list()[0].id;check(source.claims.get()==0&&source.reads.get()==0&&navigation.requestReference()!=null);
            if(mode==1||mode==2){
                check(source.snapshot(original).sourceRequest==null&&navigation.result()==null);
                source.context.wire().queue(new JSONObject().put("id",originalId.get()).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId",mode==2?"unrelated-loader":"download-loader").put("isDownload",true)));
            }
            await(()->navigation.result()!=null);check(navigation.result().ok&&!navigation.pageCommitted());
            if(mode>=3){
                check(source.snapshot(original).sourceRequest==null);
                if(mode==4){source.chunks.add(new JSONObject().put("data","dGVzdA==").put("base64Encoded",true).put("eof",true));source.controller.accept(original,64,3000);source.complete(original);check(source.snapshot(original).sourceRequest==null);}
                source.context.wire().queue(requestEvent("network-original","download-loader"));await(()->source.snapshot(original).sourceRequest!=null);
            }
            if(mode==2)check(source.snapshot(original).sourceRequest==null);
            else check(navigation.requestReference().equals(source.snapshot(original).sourceRequest));
            source.context.wire().queue(requestEvent("network-unrelated","other-loader"));source.context.wire().queue(responseEvent("interception-unrelated","network-unrelated"));
            await(()->{try{return source.controller.list().length==2;}catch(IOException error){throw new AssertionError(error);}});
            String unrelated=source.controller.list()[1].id;check(source.snapshot(unrelated).sourceRequest==null&&source.claims.get()==(mode==4?1:0));
            if(mode==1){
                source.context.wire().queue(requestEvent("network-original","rebound-loader"));
                await(()->navigation.requestAssociationError()!=null);check(source.snapshot(original).sourceRequest.equals(navigation.requestReference()));
            }
            if(mode!=4){source.chunks.add(new JSONObject().put("data","dGVzdA==").put("base64Encoded",true).put("eof",true));source.controller.accept(original,64,3000);source.complete(original);}
            check(source.snapshot(original).verified&&source.snapshot(original).received==4&&sends.get()==1);
            String json=source.snapshot(original).json().toString();check(!json.contains("network-original")&&!json.contains("download-loader")&&!json.contains("same-url.invalid"));
            source.release(original);source.controller.cancel(unrelated);await(()->source.state(unrelated,"cancelled"));source.release(unrelated);
        }
    }
    private static void overlappingRequestAssociation()throws Exception{
        for(int mode=0;mode<3;mode++)try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("overlap-request-"+mode)){
            CdpDownloadsProbe.Source source=test.source();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;AtomicInteger sends=new AtomicInteger();
            source.context.peer.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                int ordinal=sends.incrementAndGet();wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId",ordinal==1?"older-loader":"newer-loader").put("isDownload",true)));return true;
            };
            MdoBrowserCdpNetworks.Navigation older=navigate(test,source,15000);await(()->older.result()!=null);check(older.result().ok);
            if(mode==0)source.context.wire().queue(requestEvent("older-network","older-loader"));
            MdoBrowserCdpNetworks.Navigation newer=navigate(test,source,15000);await(()->newer.result()!=null);check(newer.result().ok);
            if(mode==1)source.context.wire().queue(requestEvent("older-network","older-loader"));
            source.context.wire().queue(responseEvent("older-response","older-network"));
            await(()->{try{return source.controller.list().length==1;}catch(IOException error){throw new AssertionError(error);}});
            String id=source.controller.list()[0].id;if(mode==2)source.context.wire().queue(requestEvent("older-network","older-loader"));
            await(()->source.snapshot(id).sourceRequest!=null);check(source.snapshot(id).sourceRequest.equals(older.requestReference())&&!source.snapshot(id).sourceRequest.equals(newer.requestReference()));
            check(source.claims.get()==0&&source.reads.get()==0&&sends.get()==2);
            source.chunks.add(new JSONObject().put("data","dGVzdA==").put("base64Encoded",true).put("eof",true));source.controller.accept(id,64,3000);source.complete(id);
            check(source.snapshot(id).verified&&source.snapshot(id).received==4&&source.snapshot(id).sourceRequest.equals(older.requestReference()));source.release(id);
        }
    }
    private static void sameLocationNoop()throws Exception{
        for(int mode=0;mode<26;mode++)try(CdpNetworkWorkerProbe.Case test=new CdpNetworkWorkerProbe.Case()){
            final int scenario=mode;String url=mode==17?"https://noop.invalid/current":"https://noop.invalid/current#same",target=mode==2?"https://noop.invalid/other#target":url;
            CdpNetworkBindingProbe.Context source=test.source();CdpBindingProbe.Factory.Extra previous=source.peer.extra;AtomicInteger sends=new AtomicInteger(),snapshots=new AtomicInteger(),evaluations=new AtomicInteger();
            source.peer.extra=(command,wire)->{
                String method=command.getString("method");int id=command.getInt("id");
                if(method.equals("Page.enable")&&scenario==22){wire.queue(started("prior-start","https://noop.invalid/pending","differentDocument"));wire.queue(new JSONObject().put("id",id).put("result",new JSONObject()));return true;}
                if(method.equals("Page.getFrameTree")){
                    boolean after=sends.get()>0;JSONObject root=nativeFrame(after&&scenario==7?"wrong-loader":after&&scenario==23?"seed-loader":"load-owned",after&&(scenario==6||scenario==25&&snapshots.get()==0)?"https://noop.invalid/wrong":"https://noop.invalid/current");
                    if(scenario!=17)root.put("urlFragment","#same");
                    if(!after&&(scenario==1||scenario==23||scenario==24))root.remove("url");if(!after&&scenario==15||after&&scenario==11)root.put("urlFragment",17);
                    if(!after&&scenario==16||after&&scenario==10)root.put("unreachableUrl","https://private.invalid/error");if(after&&scenario==18)root.put("id","foreign-root");
                    if(after){snapshots.incrementAndGet();if(scenario==8)wire.queue(started("other-start",url,"differentDocument"));
                        if(scenario==9)wire.queue(new JSONObject().put("method","Page.documentOpened").put("params",new JSONObject().put("frame",nativeFrame("load-owned",url))));}
                    wire.queue(new JSONObject().put("id",id).put("result",new JSONObject().put("frameTree",new JSONObject().put("frame",root))));
                    if(after&&scenario==25&&snapshots.get()==1)wire.queue(new JSONObject().put("method","Page.frameAttached").put("params",new JSONObject().put("frameId","late-child").put("parentFrameId","frame-owned")));return true;
                }
                if(method.equals("Page.navigate")){
                    sends.incrementAndGet();check(command.getJSONObject("params").getString("url").equals(target));JSONObject reply=answer(id,scenario==4);
                    if(sends.get()==1&&scenario==23){wire.queue(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",nativeFrame("seed-loader",url))));reply.getJSONObject("result").put("loaderId","seed-loader");}
                    if(sends.get()==1&&scenario==24)wire.queue(within("frame-owned",url));
                    if(scenario==3)reply.getJSONObject("result").remove("isDownload");if(scenario==5)reply.getJSONObject("result").put("loaderId","cross-document-loader");wire.queue(reply);return true;
                }
                if(sends.get()>0&&method.equals("Runtime.evaluate"))evaluations.incrementAndGet();
                return previous.send(command,wire);
            };
            test.ready(source);
            if(mode==23||mode==24){MdoBrowserCdpNetworks.Navigation seed=test.worker.navigateNetwork(source.adapter.marker().lease(),target,10000);await(seed::pageCurrent);check(seed.result().ok&&seed.pageCommitted()&&snapshots.get()==0);}
            int expectedSends=mode==23||mode==24?2:1;
            MdoBrowserCdpNetworks.Navigation navigation=test.worker.navigateNetwork(source.adapter.marker().lease(),target,10000);
            await(()->navigation.result()!=null);check(navigation.result().ok&&navigation.result().acknowledged);
            boolean qualified=java.util.Arrays.asList(0,12,13,14,17,19,20,21,23,24).contains(mode);
            MdoBrowserCdpMarker document=test.document();CdpBindingProbe.Factory reader=test.documents.get(document);
            java.util.concurrent.atomic.AtomicReference<MdoBrowserCdpWorker.Result> receipt=new java.util.concurrent.atomic.AtomicReference<>();
            reader.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.getFrameTree"))return false;
                wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameTree",new JSONObject().put("frame",nativeFrame(scenario==20?"wrong-loader":scenario==23?"seed-loader":"load-owned",scenario==19?"https://noop.invalid/replaced":url)))));return true;
            };
            if(qualified){await(navigation::pageCurrent);check(navigation.pageCommitted()&&navigation.sameDocument()&&snapshots.get()==1);}
            else{
                test.worker.submit(document,1500,false,(binding,deadline)->new byte[]{1},result->receipt.set(result));await(()->{test.ui.drain();return receipt.get()!=null;});
                if(mode==25){receipt.set(null);test.worker.submit(document,1500,false,(binding,deadline)->new byte[]{2},result->receipt.set(result));await(()->{test.ui.drain();return receipt.get()!=null;});}
                check(!navigation.pageCommitted()&&!navigation.pageCurrent());
            }
            check(sends.get()==expectedSends&&evaluations.get()==0&&snapshots.get()<=1);
            if(!qualified)continue;
            if(mode==12){source.wire().queue(within("frame-owned",url+"-away"));source.wire().queue(within("frame-owned",url));await(()->!navigation.pageCurrent());}
            if(mode==13){source.wire().queue(started("next-start",url,"differentDocument"));await(()->!navigation.pageCurrent());}
            if(mode==14)source.wire().queue(new JSONObject().put("method","Page.frameAttached").put("params",new JSONObject().put("frameId","unrelated-child").put("parentFrameId","frame-owned")));
            if(mode==21)navigation.cancel();
            receipt.set(null);AtomicInteger reads=new AtomicInteger();
            test.worker.submitGuarded(document,1500,true,navigation,(binding,deadline)->{reads.incrementAndGet();return new byte[]{11};},result->receipt.set(result));
            await(()->{test.ui.drain();return receipt.get()!=null;});boolean allowed=mode==0||mode==14||mode==17||mode==23||mode==24;
            check((receipt.get().code==null)==allowed&&reads.get()==(allowed?1:0));check(navigation.result().ok&&sends.get()==expectedSends);
        }
    }
    private static void lateResponseEpoch()throws Exception{
        for(int mode=0;mode<6;mode++)try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("late-response-epoch-"+mode)){
            final int scenario=mode;CdpDownloadsProbe.Source source=test.source();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;AtomicInteger originalId=new AtomicInteger();
            source.context.peer.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                originalId.set(command.getInt("id"));if(scenario!=1&&scenario!=3)wire.queue(requestEvent("old-network","old-download-loader"));
                if(scenario!=2)wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","old-download-loader").put("isDownload",true)));return true;
            };
            MdoBrowserCdpNetworks.Navigation older=navigate(test,source,15000);await(()->originalId.get()>0);if(mode!=2){await(()->older.result()!=null);check(older.result().ok);}
            String initial=source.offer("original-epoch-marker"),oldDocument=source.snapshot(initial).sourceDocument;source.controller.cancel(initial);await(()->source.state(initial,"cancelled"));source.release(initial);
            if(mode<4)source.context.wire().queue(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",nativeFrame("new-root-loader","https://changed.invalid/"))));
            if(mode==4)source.context.wire().queue(within("frame-owned","https://position.invalid/#moved"));
            String marker=source.offer("new-epoch-marker"),newDocument=source.snapshot(marker).sourceDocument;check((!newDocument.equals(oldDocument))==(mode<4));source.controller.cancel(marker);await(()->source.state(marker,"cancelled"));source.release(marker);
            if(mode==1)source.context.wire().queue(requestEvent("old-network","old-download-loader"));
            source.context.wire().queue(responseEvent("late-old-response","old-network"));
            await(()->{try{return source.controller.list().length==1;}catch(IOException error){throw new AssertionError(error);}});
            String id=source.controller.list()[0].id;MdoBrowserCdpDownloads.Snapshot row=source.snapshot(id);
            check(row.sourceDocument.equals(oldDocument)&&row.frameCurrent==(mode>=4));
            if(mode==2||mode==3)check(row.sourceRequest==null&&row.sourceEpochKind.equals("navigation_candidates"));
            if(mode<4){
                await(()->source.state(id,"failed"));check(source.snapshot(id).error.equals("download_frame_changed")&&source.snapshot(id).sourceCleanupConfirmed);
                if(mode==2)source.context.wire().queue(new JSONObject().put("id",originalId.get()).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","old-download-loader").put("isDownload",true)));
                if(mode==3)source.context.wire().queue(requestEvent("old-network","old-download-loader"));
                await(()->older.requestReference()!=null&&older.requestReference().equals(source.snapshot(id).sourceRequest));
                check(source.snapshot(id).sourceDocument.equals(oldDocument)&&source.snapshot(id).sourceEpochKind.equals("original_navigation"));
                source.controller.accept(id,64,3000);check(source.claims.get()==0&&source.reads.get()==0);
            }else{
                check(row.sourceEpochKind.equals("original_navigation")&&older.requestReference().equals(row.sourceRequest));
                source.chunks.add(new JSONObject().put("data","dGVzdA==").put("base64Encoded",true).put("eof",true));source.controller.accept(id,64,3000);source.complete(id);
                if(mode==5){source.context.wire().queue(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",nativeFrame("after-completion","https://completed.invalid/"))));await(()->!source.snapshot(id).frameCurrent);}
                MdoBrowserDownloadQueue.Read range=source.controller.read(id,0,4);await(range::finished);byte[] bytes=range.take();check(java.util.Arrays.equals(bytes,new byte[]{116,101,115,116}));java.util.Arrays.fill(bytes,(byte)0);range.close();
                check(source.snapshot(id).sourceDocument.equals(oldDocument)&&source.snapshot(id).verified&&source.claims.get()==1);
            }
            source.release(id);
        }
    }
    private static void ambiguousResponseEpoch()throws Exception{
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("ambiguous-response-epoch")){
            CdpDownloadsProbe.Source source=test.source();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;AtomicInteger sends=new AtomicInteger(),held=new AtomicInteger(),abort=new AtomicInteger();
            source.context.peer.extra=(command,wire)->{
                if(command.getString("method").equals("Fetch.failRequest")&&command.getJSONObject("params").getString("requestId").equals("ambiguous-response")){source.aborts.incrementAndGet();abort.set(command.getInt("id"));return true;}
                if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                int ordinal=sends.incrementAndGet();if(ordinal==2){held.set(command.getInt("id"));return true;}
                if(ordinal==3)wire.queue(requestEvent("fresh-network","fresh-loader"));
                wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId",ordinal==1?"older-loader":"fresh-loader").put("isDownload",true)));return true;
            };
            MdoBrowserCdpNetworks.Navigation older=navigate(test,source,15000);await(()->older.result()!=null);
            source.context.wire().queue(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject().put("frame",nativeFrame("current-loader","https://current.invalid/"))));
            String marker=source.offer("current-epoch-marker"),currentDocument=source.snapshot(marker).sourceDocument;check(source.snapshot(marker).sourceEpochKind.equals("response_frame"));source.controller.cancel(marker);await(()->source.state(marker,"cancelled"));source.release(marker);
            MdoBrowserCdpNetworks.Navigation newer=navigate(test,source,15000);await(()->held.get()>0);check(newer.result()==null);
            source.context.wire().queue(responseEvent("ambiguous-response","unknown-network"));
            await(()->{try{return source.controller.list().length==1;}catch(IOException error){throw new AssertionError(error);}});String id=source.controller.list()[0].id;
            await(()->abort.get()>0);MdoBrowserCdpDownloads.Snapshot cancelling=source.snapshot(id);check(cancelling.state.equals("cancelling")&&cancelling.error.equals("download_frame_unproven")&&!cancelling.sourceCleanupConfirmed);
            refused("download_frame_unproven",()->source.controller.accept(id,64,3000));check(source.claims.get()==0&&source.reads.get()==0);
            source.context.wire().queue(new JSONObject().put("id",abort.get()).put("result",new JSONObject()));
            await(()->source.state(id,"failed"));MdoBrowserCdpDownloads.Snapshot rejected=source.snapshot(id);
            check(rejected.error.equals("download_frame_unproven")&&rejected.failureSource.equals("native_frame")&&rejected.sourceEpochKind.equals("unproven"));
            check(!rejected.frameCurrent&&rejected.sourceDocument.isEmpty()&&rejected.sourceRequest==null&&rejected.sourceCleanupConfirmed);
            check(rejected.json().getJSONObject("native").opt("source_document")==JSONObject.NULL);source.controller.accept(id,64,3000);check(source.claims.get()==0&&source.reads.get()==0&&source.controller.failure()==null);source.release(id);
            source.context.wire().queue(new JSONObject().put("id",held.get()).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","newer-loader").put("isDownload",true)));await(()->newer.result()!=null);
            MdoBrowserCdpNetworks.Navigation fresh=navigate(test,source,15000);await(()->fresh.result()!=null);check(fresh.result().ok&&sends.get()==3);
            source.context.wire().queue(responseEvent("fresh-response","fresh-network"));await(()->{try{return source.controller.list().length==1;}catch(IOException error){throw new AssertionError(error);}});String next=source.controller.list()[0].id;
            check(source.snapshot(next).sourceDocument.equals(currentDocument)&&source.snapshot(next).frameCurrent&&fresh.requestReference().equals(source.snapshot(next).sourceRequest));
            source.chunks.add(new JSONObject().put("data","dGVzdA==").put("base64Encoded",true).put("eof",true));source.controller.accept(next,64,3000);source.complete(next);check(source.snapshot(next).verified&&source.claims.get()==1&&source.controller.failure()==null);source.release(next);
        }
    }
    private static void releasedRequestLinks()throws Exception{
        for(boolean complete:new boolean[]{false,true})try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("released-request-links-"+complete)){
            CdpDownloadsProbe.Source source=test.source();CdpBindingProbe.Factory.Extra previous=source.context.peer.extra;AtomicInteger sends=new AtomicInteger();
            source.context.peer.extra=(command,wire)->{
                if(!command.getString("method").equals("Page.navigate"))return previous.send(command,wire);
                sends.incrementAndGet();wire.queue(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject().put("frameId","frame-owned").put("loaderId","original-loader").put("isDownload",true)));return true;
            };
            MdoBrowserCdpNetworks.Navigation navigation=navigate(test,source,15000);await(()->navigation.result()!=null);check(navigation.result().ok);
            for(int i=0;i<40;i++){
                source.context.wire().queue(responseEvent("released-response-"+i,"missing-network-"+i));
                await(()->{try{return source.controller.list().length==1;}catch(IOException error){throw new AssertionError(error);}});
                String id=source.controller.list()[0].id;MdoBrowserCdpDownloads.Snapshot row=source.snapshot(id);
                check(row.state.equals("pending")&&row.frameCurrent&&row.sourceEpochKind.equals("navigation_candidates")&&row.sourceRequest==null&&source.controller.failure()==null);
                if(complete){source.chunks.add(new JSONObject().put("data","dGVzdA==").put("base64Encoded",true).put("eof",true));source.controller.accept(id,64,3000);source.complete(id);check(source.snapshot(id).verified);}
                else{source.controller.cancel(id);await(()->source.state(id,"cancelled"));}
                source.release(id);check(source.controller.list().length==0&&test.budget.used()==0&&test.files.reservations()==0&&test.files.reservedBytes()==0);
            }
            check(sends.get()==1&&source.claims.get()==(complete?40:0)&&source.reads.get()==(complete?40:0)&&source.controller.failure()==null);
            source.context.wire().queue(requestEvent("fresh-proven-network","original-loader"));source.context.wire().queue(responseEvent("fresh-proven-response","fresh-proven-network"));
            await(()->{try{return source.controller.list().length==1;}catch(IOException error){throw new AssertionError(error);}});String fresh=source.controller.list()[0].id;
            check(navigation.requestReference().equals(source.snapshot(fresh).sourceRequest)&&source.snapshot(fresh).sourceEpochKind.equals("original_navigation"));source.controller.cancel(fresh);await(()->source.state(fresh,"cancelled"));source.release(fresh);
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();Path root=Paths.get(args[0]);if(Files.exists(root))throw new AssertionError("Interrupted navigation evidence preserved");
        Files.createDirectory(root);CdpDownloadsProbe.ownBase(root);originalDownloadProgress();queuedDecisions();inFlightCancelAndReplacement();commitOrdering();documentGuards();sameDocumentDestinations();historyProofs();originalRequestAssociation();overlappingRequestAssociation();sameLocationNoop();lateResponseEpoch();ambiguousResponseEpoch();releasedRequestLinks();Files.delete(root);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"original_ui_admission\":true,\"shared_worker_body_progress\":true,\"exact_queued_cancellation\":true,\"original_native_commit_matching\":true,\"bounded_commit_history\":true,\"history_completion_guard\":true,\"original_request_download_join\":true,\"late_metadata_after_body_completion\":true,\"overlapping_navigation_downloads\":true,\"bfcache_and_same_document_history\":true,\"same_document_destination_guard\":true,\"same_location_noop_proof\":true,\"original_download_epoch\":true,\"released_request_links\":true,\"real_guarded_files\":true,\"framework_verified\":false,\"provider_qualified\":false}");
    }
}
