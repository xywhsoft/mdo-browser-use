package org.xserver.android;

/** Exact asynchronous product close route and cleanup watcher. Controlled SDK
 * callbacks are separate from the actual Native/JNI ownership gate. */
final class AndroidCloseContractTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static AndroidCreateHost host()throws Exception{android.os.Handler.reset();AndroidCreateHost host=new AndroidCreateHost();host.create("tab",AndroidCreateContractTest.request(false));return host;}
    private static AndroidCreateHost.JSONObject result(AndroidCreateHost host,String id){AndroidCreateHost.JSONObject value=host.scopedResults.get(id);check(value!=null&&host.scopedErrors.get(id)==null,"Missing final close receipt: "+id);return value;}
    private static boolean flag(AndroidCreateHost.JSONObject value,String name){return Boolean.TRUE.equals(value.data.get(name));}
    private static void tick(long delay){android.os.SystemClock.now+=delay;android.os.Handler.tick();}
    private static void immediate()throws Exception{
        AndroidCreateHost host=host();AndroidCreateHost.WebView view=host.views.get("tab");int replies=host.replies;
        host.closeRequest("tab","close-now");AndroidCreateHost.JSONObject value=result(host,"close-now");
        check(flag(value,"world_cleanup_confirmed")&&flag(value,"native_view_closed")&&flag(value,"network_cleanup_confirmed"),"Immediate confirmed close became uncertain");
        check(!flag(value,"close_observation_expired")&&host.closeRequests()==0&&view.destroys==1&&host.replies==replies+1,"Immediate close retained or repeated work");
        host.closeRequest("tab","already-closed");check(flag(result(host,"already-closed"),"world_cleanup_confirmed")&&view.destroys==1,"Empty close repeated SDK teardown");host.destroy();
    }
    private static void nativeWait()throws Exception{
        AndroidCreateHost host=host();AndroidCreateHost.WebView view=host.views.get("tab");host.deferredDestruction.add(view);int before=host.replies;
        host.closeRequest("tab","first");host.closeRequest("tab","second");
        check(host.closeRequests()==2&&host.replies==before&&view.destroys==0,"Unconsumed close replied or destroyed early");
        tick(100);check(host.replies==before&&host.closeRequests()==2,"Waiting returned success before its exact native receipt");
        host.acknowledge(view);tick(100);
        check(flag(result(host,"first"),"world_cleanup_confirmed")&&flag(result(host,"second"),"world_cleanup_confirmed"),"Native receipt was not joined to both original requests");
        check(view.destroys==1&&host.closeRequests()==0&&host.replies==before+2&&android.os.Handler.pending()==0,"Native join duplicated effects or retained timers");
        host.create("tab",AndroidCreateContractTest.request(false));AndroidCreateHost.WebView replacement=host.views.get("tab");host.acknowledge(view);tick(100);
        check(replacement.destroys==0&&host.views.owns("tab",replacement)&&host.replies==before+3,"Late old close affected replacement or replied twice");host.destroy();
    }
    private static void networkWait()throws Exception{
        AndroidCreateHost host=AndroidCreateContractTest.navigationHost();AndroidCreateHost.WebView view=host.views.get("tab");AndroidCreateHost.NetworkCall call=host.cdpWorker.calls.get(0);int before=host.replies;
        host.closeRequest("tab","network-close");check(view.destroys==1&&host.closeRequests()==1&&host.replies==before,"Physical close ignored original network retirement");
        tick(100);check(host.closeRequests()==1&&host.replies==before,"Pending network was reported as drained");call.closed(true);tick(100);
        AndroidCreateHost.JSONObject value=result(host,"network-close");check(flag(value,"world_cleanup_confirmed")&&flag(value,"network_cleanup_confirmed")&&host.closeRequests()==0,"Late exact network drain did not complete close");host.destroy();
        AndroidCreateHost files=AndroidCreateContractTest.navigationHost();AndroidCreateHost.MdoBrowserCdpDownloads downloads=AndroidCreateHost.MdoBrowserCdpDownloads.latest;downloads.retiring=1;
        files.closeRequest("tab","files-close");files.cdpWorker.calls.get(0).closed(true);tick(100);
        check(files.closeRequests()==1&&!files.scopedResults.containsKey("files-close"),"Close ignored original download-file retirement");
        downloads.retiring=0;tick(100);check(flag(result(files,"files-close"),"world_cleanup_confirmed")&&files.closeRequests()==0,"Final private-file retirement did not complete close");files.destroy();
        AndroidCreateHost failed=AndroidCreateContractTest.navigationHost();failed.closeRequest("tab","network-failed");failed.cdpWorker.calls.get(0).closed(false);tick(100);
        AndroidCreateHost.JSONObject uncertain=result(failed,"network-failed");check(!flag(uncertain,"world_cleanup_confirmed")&&flag(uncertain,"network_cleanup_failed")&&failed.closeRequests()==0,"Failed network drain was certified or kept observing");
        int delivered=failed.replies;failed.cdpWorker.calls.get(0).closed(true);tick(100);check(!flag(uncertain,"world_cleanup_confirmed")&&failed.replies==delivered,"Late network result rewrote a failed close receipt");failed.destroy();
    }
    private static void uncertain()throws Exception{
        for(int mode=0;mode<5;mode++){
            AndroidCreateHost host=host();AndroidCreateHost.WebView view=host.views.get("tab");host.deferredDestruction.add(view);
            if(mode==2)android.os.Handler.fail=true;
            host.closeRequest("tab","uncertain");android.os.Handler.fail=false;
            if(mode==1){android.os.SystemClock.now--;host.observeCloses();}
            else if(mode==4){android.os.SystemClock.now+=5000;host.acknowledge(view);tick(0);}
            else if(mode!=2)tick(5000);
            AndroidCreateHost.JSONObject value=result(host,"uncertain");
            check(!flag(value,"world_cleanup_confirmed")&&flag(value,"native_view_closed")==(mode==4)&&view.destroys==(mode==4?1:0)&&host.closeRequests()==0,"Timeout/scheduler loss manufactured native cleanup");
            check(flag(value,"close_observation_expired")== (mode!=2)&&flag(value,"close_observation_failed")== (mode==2),"Wrong close observation failure reason");
            int before=host.replies;
            if(mode==3){host.closeRequest("tab","explicit-observation");check(host.closeRequests()==1,"Explicit new observation could not join the original native record");}
            host.acknowledge(view);tick(100);
            check(view.destroys==1&&!flag(value,"world_cleanup_confirmed")&&host.scopedResults.get("uncertain")==value,"Late native ACK rewrote the timed-out receipt or repeated destruction");
            if(mode==3)check(flag(result(host,"explicit-observation"),"world_cleanup_confirmed")&&host.replies==before+1,"New explicit observation did not report its own final result");
            else check(host.replies==before,"Late cleanup delivered a second terminal reply");host.destroy();
        }
    }
    private static void lostOwner()throws Exception{
        for(int mode=0;mode<4;mode++){
            AndroidCreateHost host=host();AndroidCreateHost.WebView view=host.views.get("tab");host.deferredDestruction.add(view);int before=host.replies;host.closeRequest("tab","old-owner");
            if(mode==0)host.channel.lease=host.channel.new Lease();
            if(mode==1)host.channel.open=false;
            if(mode==2)host.destroy();
            if(mode==3)host.beforeScopedReply=()->host.channel.lease=host.channel.new Lease();
            host.acknowledge(view);tick(100);
            check(host.closeRequests()==0&&host.replies==before&&!host.scopedResults.containsKey("old-owner"),"Old close result leaked into a new/dead channel");
            check(view.destroys==1&&android.os.Handler.pending()==0,"Owner loss cancelled actual native retirement or retained its watcher");if(mode!=2)host.destroy();
        }
    }
    private static void boundsAndFailures()throws Exception{
        AndroidCreateHost host=host();AndroidCreateHost.WebView view=host.views.get("tab");host.deferredDestruction.add(view);
        for(int i=0;i<32;i++)host.closeRequest("tab","bounded-"+i);
        for(String invalid:new String[]{"bounded-0","overflow","",new String(new char[513]).replace('\0','x')}){
            try{host.closeRequest("tab",invalid);throw new AssertionError("Unbounded/duplicate close admitted");}catch(IllegalStateException expected){check(expected.getMessage().equals("browser_close_busy"),"Wrong pre-effect close admission error");}
        }
        check(host.closeRequests()==32&&view.destroys==0,"Admission failure repeated teardown or lost existing observations");host.destroy();host.acknowledge(view);
        check(host.closeRequests()==0&&view.destroys==1&&android.os.Handler.pending()==0,"Activity shutdown leaked bounded observations");
        AndroidCreateHost closed=host();AndroidCreateHost.WebView original=closed.views.get("tab");closed.channel.open=false;
        try{closed.closeRequest("tab","no-channel");throw new AssertionError("Close accepted an invalid original channel");}catch(IllegalStateException expected){check(expected.getMessage().equals("browser_close_channel_changed")&&original.destroys==0&&closed.views.owns("tab",original),"Close rejected after its native effect");}closed.destroy();
        AndroidCreateHost failed=host();AndroidCreateHost.WebView broken=failed.views.get("tab");failed.cleanupFault="destroy";failed.closeRequest("tab","failed");
        check("browser_close_unconfirmed".equals(failed.scopedErrors.get("failed"))&&failed.closeRequests()==0&&broken.destroys==1,"Failed native teardown lost the scoped error or replayed");
        failed.cleanupFault="";failed.closeRequest("tab","failed-again");check(!flag(result(failed,"failed-again"),"world_cleanup_confirmed")&&broken.destroys==1,"Explicit observation retried uncertain physical destruction");failed.destroy();
        AndroidCreateHost document=host();document.cleanupFault="document";document.closeRequest("tab","document-failed");
        check(!flag(result(document,"document-failed"),"world_cleanup_confirmed")&&document.closeRequests()==0,"Unconfirmed document cleanup was upgraded");document.cleanupFault="";document.destroy();
    }
    private static void replacement()throws Exception{
        AndroidCreateHost host=host();AndroidCreateHost.WebView original=host.views.get("tab");final AndroidCreateHost.WebView[] replacement={null};
        host.root.beforeRemove=()->{
            replacement[0]=new AndroidCreateHost.WebView(host.activity);host.views.put("tab",replacement[0]);host.manual.add("tab");host.errors.put("tab","new-page-error");host.locations.put("tab","https://replacement.invalid/");
        };
        host.closeRequest("tab","original-close");
        check(flag(result(host,"original-close"),"world_cleanup_confirmed")&&original.destroys==1&&replacement[0].destroys==0&&host.views.owns("tab",replacement[0]),"Original close adopted or destroyed a reentrant replacement");
        check(host.manual.contains("tab")&&"new-page-error".equals(host.errors.get("tab"))&&"https://replacement.invalid/".equals(host.locations.get("tab")),"Original close cleared replacement metadata");host.destroy();
    }
    private static void unrelatedPendingClose()throws Exception{
        AndroidCreateHost host=host();host.create("other",AndroidCreateContractTest.request(false));AndroidCreateHost.WebView other=host.views.get("other");host.deferredDestruction.add(other);
        host.closeRequest("other","other-close");host.closeRequest("tab","own-close");
        check(flag(result(host,"own-close"),"world_cleanup_confirmed")&&host.closeRequests()==1&&other.destroys==0,"Unrelated native retirement replaced the original close's cleanup proof");
        check(Integer.valueOf(1).equals(host.scopedResults.get("own-close").data.get("native_view_retiring")),"Scoped close hid unrelated host retirement diagnostics");
        host.acknowledge(other);tick(100);check(flag(result(host,"other-close"),"world_cleanup_confirmed")&&host.closeRequests()==0,"Independent native close lost its own receipt");host.destroy();
    }
    static int run()throws Exception{immediate();nativeWait();networkWait();uncertain();lostOwner();boundsAndFailures();replacement();unrelatedPendingClose();return checks;}
}
