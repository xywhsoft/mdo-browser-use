package org.xserver.android;

/** Exact product fragments with controlled UI/native dependencies. */
public final class AndroidCreateContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static AndroidCreateHost.JSONObject request(boolean automation)throws Exception{return new AndroidCreateHost.JSONObject().put("automation",automation).put("url","https://fixture.invalid/");}
    static void profileFailure()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();
        try{host.create("tab",request(true));throw new AssertionError("Unconfirmed profile opened");}
        catch(UnsupportedOperationException expected){check("browser_profile_unconfirmed".equals(expected.getMessage()),"Original profile failure was hidden");}
        AndroidCreateHost.WebView original=host.created.get(0);
        check(original.destroys==1,"Profile failure destroyed the original native view more than once");
        check(original.loads==0&&original.touches==0&&host.replies==0,"Failed profile touched settings, loaded a page or replied success");
        check(host.views.size()==0&&host.documents.isEmpty()&&host.documentFailures.isEmpty()&&host.root.children.isEmpty(),"Failed create kept UI bindings");
        check(host.profileRequests.size()==1&&"mdo.browser-use".equals(host.profileRequests.get(0))&&host.retired==1,"Failure changed profile or retired the wrong data owner");
    }
    static void ordinaryFailures()throws Exception{
        for(String fault:new String[]{"input","settings","attach","bounds","load","reply"}){
            AndroidCreateHost host=new AndroidCreateHost();host.fault=fault;
            try{host.create("tab",request(false));throw new AssertionError("Fixture create fault ignored");}
            catch(RuntimeException|AndroidCreateHost.JSONException expected){check(true,"expected failure");}
            AndroidCreateHost.WebView original=host.created.get(0);check(original.destroys==1,"Create fault did not retire exactly one original view: "+fault);
            check(host.views.size()==0&&host.documents.isEmpty()&&host.dialogs.isEmpty()&&host.root.children.isEmpty(),"Create fault retained active bindings: "+fault);
            host.fault="";host.create("explicit-retry",request(false));
            check(host.created.size()==2&&!Boolean.TRUE.equals(host.capabilities().data.get("native_view_cleanup_failed")),"Clean failure cleanup poisoned explicit new creation: "+fault);
        }
    }
    static void manualAndPopup()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();host.fault="manual_world";host.create("tab",request(false));
        AndroidCreateHost.WebView original=host.created.get(0);
        check(original.loads==1&&original.destroys==0&&host.views.size()==1,"Unsupported isolated world broke manual browsing");
        check(host.profileRequests.size()==1&&host.profileRequests.get(0)==null,"Manual view requested an automation profile");
        host.create("tab",request(false));check(host.created.size()==1&&original.loads==1,"Reattachment recreated or reloaded the page");
        AndroidCreateHost.MdoBrowserPopups<AndroidCreateHost.WebView> parent=new AndroidCreateHost.MdoBrowserPopups<>();parent.deferred=true;host.windows.put("opener",parent);
        host.create("child",new AndroidCreateHost.JSONObject().put("popup","popup").put("opener","opener"));AndroidCreateHost.WebView child=host.created.get(1);
        check(parent.adopts==1&&child.loads==0&&child.destroys==0&&host.popupLinks.containsKey("child"),"Pending native popup was replayed or destroyed");
        parent.pending.done(true,null,true);check(host.popupLinks.get("child").ready&&child.loads==0,"Popup confirmation navigated to another page");
    }
    static void failedCleanup()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();host.fault="destroy";
        try{host.create("failed",request(true));throw new AssertionError("Unconfirmed native destruction ignored");}
        catch(IllegalStateException expected){
            check("native_view_cleanup_failed".equals(expected.getMessage()),"Cleanup uncertainty was not preserved as a typed result");
            check(expected.getCause() instanceof UnsupportedOperationException&&expected.getSuppressed().length==1,"Original profile/cleanup reasons were lost");
        }
        check(host.created.get(0).destroys==1&&host.views.size()==0,"Failed cleanup replayed destruction or left a usable view");
        check(Boolean.TRUE.equals(host.capabilities().data.get("native_view_cleanup_failed"))&&Integer.valueOf(0).equals(host.capabilities().data.get("native_view_retiring")),"Cleanup capability was hidden or kept a phantom running retirement");
        host.fault="";
        try{host.create("replacement",request(false));throw new AssertionError("Unknown native release admitted another physical view");}
        catch(IllegalStateException expected){check("native_view_cleanup_failed".equals(expected.getMessage()),"Uncertain resource fence was not sticky");}
        check(host.created.size()==1,"Erased UI map reset the native resource count");
        check(!host.closeCommand("failed"),"Repeated close reported unknown native cleanup as confirmed");
    }
    static void reentrantCleanup()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();final int[] refused={0};
        host.root.beforeRemove=()->{
            try{check(Integer.valueOf(1).equals(host.capabilities().data.get("native_view_retiring")),"Active native retirement was not visible");}catch(Exception error){throw new AssertionError(error);}
            check(!host.closeCommand("failed"),"Reentrant empty-map close reported native retirement complete");
            try{host.create("reentrant",request(false));throw new AssertionError("New view created during native retirement");}
            catch(IllegalStateException expected){check("native_view_retirement_busy".equals(expected.getMessage()),"Wrong native retirement fence");refused[0]++;}
            catch(Exception unexpected){throw new AssertionError(unexpected);}
        };
        try{host.create("failed",request(true));throw new AssertionError("Profile failure disappeared");}
        catch(UnsupportedOperationException expected){check("browser_profile_unconfirmed".equals(expected.getMessage()),"Cleanup reentrance hid the original failure");}
        check(refused[0]==1&&host.created.size()==1&&host.views.size()==0&&host.created.get(0).destroys==1,"Native retirement reservation did not cover UI callbacks");
        host.create("after",request(false));check(host.created.size()==2&&host.views.size()==1,"Clean retirement poisoned an explicit later creation");
    }
    static void cleanupFaultsAndQuota()throws Exception{
        for(String cleanup:new String[]{"detach","destroy","document","oom"}){
            AndroidCreateHost host=new AndroidCreateHost();host.fault="load";host.cleanupFault=cleanup;
            try{host.create("tab",request(false));throw new AssertionError("Unconfirmed retirement was ignored: "+cleanup);}
            catch(IllegalStateException expected){check(!cleanup.equals("oom")&&"native_view_cleanup_failed".equals(expected.getMessage()),"Wrong cleanup priority: "+cleanup);}
            catch(OutOfMemoryError expected){check(cleanup.equals("oom"),"Unexpected cleanup allocation failure");}
            check(Boolean.TRUE.equals(host.capabilities().data.get("native_view_cleanup_failed")),"Native cleanup failure was not latched: "+cleanup);
            host.fault=host.cleanupFault="";
            try{host.create("new",request(false));throw new AssertionError("Failed cleanup admitted new view");}
            catch(IllegalStateException expected){check("native_view_cleanup_failed".equals(expected.getMessage()),"Resource fence was reset");}
            check(host.created.size()==1,"Unknown native release reset the view limit");
        }
        AndroidCreateHost host=new AndroidCreateHost();for(int i=0;i<8;i++)host.create("tab"+i,request(false));
        try{host.create("overflow",request(false));throw new AssertionError("Native quota was ignored");}catch(IllegalStateException expected){check(true,"bounded");}
        check(host.created.size()==8&&host.views.size()==8,"Quota checked after native allocation");
        AndroidCreateHost.WebView original=host.created.get(0);host.closeCommand("tab0");host.create("after",request(false));
        check(original.destroys==1&&host.created.size()==9&&host.views.size()==8,"Confirmed native release did not free exactly one slot");
        host.stopped=true;try{host.create("after",request(false));throw new AssertionError("Stopped owner reattached a view");}
        catch(IllegalStateException expected){check("native_browser_closed".equals(expected.getMessage()),"Stopped owner was not fenced");}
        AndroidCreateHost chooser=new AndroidCreateHost();chooser.create("tab",request(false));chooser.views.remove("tab");chooser.cleanupFault="chooser";
        check(!chooser.closeCommand("tab"),"Logical close lost the existing chooser-cleanup predicate");
    }
    static void inputRetirementCallback()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();host.create("tab",request(false));AndroidCreateHost.WebView original=host.created.get(0);final int[] callbacks={0};
        host.views.input("tab",original).tap(1,1,result->{
            callbacks[0]++;
            try{host.create("during-input-close",request(false));throw new AssertionError("Input close escaped the native reservation");}
            catch(IllegalStateException expected){check("native_view_retirement_busy".equals(expected.getMessage()),"Input callback lost retirement reservation");}
            catch(Exception error){throw new AssertionError(error);}
            check(!host.retire("tab",original),"Recursive close of the same native view was accepted");
        });
        check(host.closeCommand("tab"),"Clean input retirement was unconfirmed");
        check(callbacks[0]==1&&host.created.size()==1&&original.destroys==1&&host.views.size()==0,"Native input cleanup reentered creation or destroyed twice");
        host.create("explicit-after",request(false));check(host.views.size()==1,"Completed input retirement retained a phantom reservation");
    }
    static AndroidCreateHost networkHost(){android.os.Handler.reset();AndroidCreateHost host=new AndroidCreateHost();host.allowProfile=true;return host;}
    static AndroidCreateHost.NetworkCall startNetwork(AndroidCreateHost host,String key)throws Exception{
        host.create(key,request(true));AndroidCreateHost.WebView view=host.views.get(key);
        check(view.targets.size()==1&&view.targets.get(0).equals("about:blank")&&host.replies==0,"Target opened or replied before native warmup");
        try{host.pendingCommand(key,"navigate");throw new AssertionError("Pending startup admitted another URL");}catch(IllegalStateException expected){check(expected.getMessage().equals("network_starting"),"Wrong startup command fence");}
        host.startPage(key,"about:blank");host.finishPage(key,"about:blank");
        check(host.cdpWorker.calls.size()>0&&Boolean.TRUE.equals(host.loading.get(key)),"Warmup cleared target loading before readiness");
        return host.cdpWorker.calls.get(host.cdpWorker.calls.size()-1);
    }
    static AndroidCreateHost.JSONObject createReceipt(AndroidCreateHost host){return (AndroidCreateHost.JSONObject)host.lastReply.data.get("native_create");}
    static void networkReadinessAndClose()throws Exception{
        AndroidCreateHost host=networkHost();AndroidCreateHost.NetworkCall call=startNetwork(host,"tab");AndroidCreateHost.WebView view=host.views.get("tab");
        call.ready();check(host.replies==1&&view.targets.size()==1&&host.cdpWorker.navigations.size()==1&&host.initialPending("tab"),"Initial target bypassed native admission or replayed WebView.loadUrl");
        check(Boolean.TRUE.equals(host.lastReply.data.get("downloads_intercepted")),"Original interception was not acknowledged");
        host.cdpWorker.navigations.get(0).issued=true;host.startPage("tab","https://fixture.invalid/");host.finishPage("tab","https://fixture.invalid/");
        check(host.initialPending("tab"),"Page callbacks alone invented an original native navigation ACK");
        host.cdpWorker.navigations.get(0).ack(true,false,true,null);host.cdpWorker.navigations.get(0).committed=true;host.cdpWorker.navigations.get(0).current=true;
        check(!host.initialPending("tab"),"Actual page completion and original native ACK stayed pending");
        check(!Boolean.TRUE.equals(host.loading.get("tab"))&&Boolean.TRUE.equals(host.network("tab").data.get("ready")),"Ordinary target navigation invalidated physical network ownership");
        call.ready();check(view.targets.size()==1&&host.cdpWorker.navigations.size()==1&&host.replies==1&&Boolean.TRUE.equals(host.network("tab").data.get("ready")),"Duplicate ready replayed or retired the target navigation");
        check(!host.closeCommand("tab")&&view.destroys==1,"Close confirmed before original network I/O drained");
        try{host.create("during",request(false));throw new AssertionError("Pending network retirement admitted a native view");}
        catch(IllegalStateException expected){check(expected.getMessage().equals("native_view_retirement_busy"),"Network retirement was not visible to creation admission");}
        call.closed(true);check(!Boolean.TRUE.equals(host.network("tab").data.get("installed"))&&host.closeCommand("tab"),"Drained original source retained a phantom retirement");
        host.create("after",request(false));check(host.views.size()==1,"Confirmed network retirement poisoned explicit later creation");
        AndroidCreateHost blank=networkHost();blank.create("tab",new AndroidCreateHost.JSONObject().put("automation",true));blank.startPage("tab","about:blank");blank.finishPage("tab","about:blank");blank.cdpWorker.calls.get(0).ready();
        check(blank.created.get(0).loads==1&&!Boolean.TRUE.equals(blank.loading.get("tab")),"Blank target reloaded its warmup or stayed loading");
    }
    static void networkCancellationAndTimeout()throws Exception{
        AndroidCreateHost host=networkHost();AndroidCreateHost.NetworkCall call=startNetwork(host,"tab");AndroidCreateHost.WebView view=host.views.get("tab");
        host.revoke("tab");call.ready();check(view.loads==1&&host.replies==0,"Cancelled startup accepted a late native ready");call.closed(true);
        check(view.destroys==1&&host.replies==1&&Boolean.FALSE.equals(createReceipt(host).data.get("navigation_may_have_occurred")),"Cancelled startup lost its original unsent receipt");
        AndroidCreateHost timeout=networkHost();timeout.create("tab",request(true));AndroidCreateHost.WebView first=timeout.views.get("tab");android.os.SystemClock.now+=15000;android.os.Handler.tick();
        check(first.destroys==1&&timeout.views.size()==0&&timeout.replies==1&&timeout.cdpWorker.calls.isEmpty(),"Warmup timeout sent a target or leaked its native view");
        check(timeout.loading.isEmpty()&&timeout.locations.isEmpty()&&android.os.Handler.pending()==0,"Failed native startup retained per-tab metadata or watchdog");
        AndroidCreateHost wrong=networkHost();wrong.create("tab",request(true));wrong.startPage("tab","https://unexpected.invalid/");
        check(wrong.views.size()==0&&wrong.cdpWorker.calls.isEmpty()&&wrong.created.get(0).loads==1,"Unexpected initial navigation was adopted as native warmup");
        AndroidCreateHost stop=networkHost();stop.create("tab",request(true));stop.stopNetwork("tab");check(stop.views.size()==0&&stop.created.get(0).destroys==1,"Stop did not retire pre-network warmup");
        AndroidCreateHost failed=networkHost();failed.cdpWorker.failStart=true;failed.create("tab",request(true));failed.startPage("tab","about:blank");failed.finishPage("tab","about:blank");
        check(failed.views.size()==0&&failed.created.get(0).destroys==1&&failed.replies==1,"Rejected network start leaked or destroyed twice");
        AndroidCreateHost unsupported=networkHost();AndroidCreateHost.NetworkCall rejected=startNetwork(unsupported,"tab");rejected.failed("cdp_method_unsupported",true);
        check(((AndroidCreateHost.JSONObject)unsupported.lastReply.data.get("error")).data.get("code").equals("cdp_method_unsupported"),"Confirmed protocol failure was masked by generic startup text");
        AndroidCreateHost frames=networkHost();AndroidCreateHost.NetworkCall invalidFrames=startNetwork(frames,"tab");invalidFrames.failed("download_frame_root_changed",true);
        check(((AndroidCreateHost.JSONObject)frames.lastReply.data.get("error")).data.get("code").equals("download_frame_root_changed"),"Native frame failure was masked by generic startup text");
        check(unsupported.created.get(0).destroys==1&&unsupported.created.get(0).loads==1,"Known unsupported transport sent or repeated a target request");
        AndroidCreateHost drain=networkHost();AndroidCreateHost.NetworkCall pending=startNetwork(drain,"tab");drain.revoke("tab");android.os.SystemClock.now+=5000;android.os.Handler.tick();
        AndroidCreateHost.JSONObject originalReceipt=createReceipt(drain);
        check(Boolean.FALSE.equals(drain.capabilities().data.get("network_cleanup_failed"))&&Boolean.FALSE.equals(originalReceipt.data.get("network_cleanup_confirmed")),"Observation timeout permanently poisoned the native host without a cleanup failure");
        try{drain.create("pending",request(false));throw new AssertionError("Unknown original I/O released native capacity");}
        catch(IllegalStateException expected){check(expected.getMessage().equals("native_view_retirement_busy"),"Observation timeout lost its pending retirement fence");}
        pending.closed(true);check(Boolean.FALSE.equals(drain.capabilities().data.get("network_cleanup_failed"))&&drain.created.get(0).destroys==1,"Confirmed late cleanup could not reclaim an unsent startup view");
        check(Boolean.FALSE.equals(originalReceipt.data.get("network_cleanup_confirmed")),"Late cleanup rewrote the original uncertain receipt");
        drain.create("explicit-retry",request(false));check(drain.views.size()==1,"Known drained cleanup prevented an explicit later creation");
    }
    static void networkUncertaintyAndScope()throws Exception{
        AndroidCreateHost host=networkHost();AndroidCreateHost.NetworkCall call=startNetwork(host,"tab");AndroidCreateHost.WebView view=host.views.get("tab");
        host.beforeScopedReply=()->{host.cdpWorker.navigations.get(0).issued=true;host.revoke("tab");};call.ready();call.closed(true);
        check(view.loads==1&&view.destroys==0&&host.replies==1,"Uncertain original target was replayed, destroyed or duplicated");
        try{host.create("tab",request(true));throw new AssertionError("Uncertain existing view was reattached");}catch(IllegalStateException expected){check(view.loads==1,"Reattachment replayed an uncertain navigation");}
        AndroidCreateHost source=networkHost();AndroidCreateHost.NetworkCall old=startNetwork(source,"tab");source.channel.lease=source.channel.new Lease();old.ready();old.closed(true);
        check(source.created.get(0).loads==1&&source.created.get(0).destroys==1&&source.replies==0,"Changed host channel received a stale ready/creation receipt");
        AndroidCreateHost unexpected=networkHost();AndroidCreateHost.NetworkCall changed=startNetwork(unexpected,"tab");unexpected.created.get(0).url="https://unobserved.invalid/";changed.ready();changed.closed(true);
        check(unexpected.created.get(0).loads==1&&unexpected.created.get(0).destroys==1,"Unobserved native URL replacement was adopted as blank readiness");
        AndroidCreateHost cleanup=networkHost();AndroidCreateHost.NetworkCall unknown=startNetwork(cleanup,"tab");cleanup.closeCommand("tab");unknown.closed(false);
        check(Boolean.TRUE.equals(cleanup.capabilities().data.get("network_cleanup_failed")),"Unconfirmed protocol cleanup was hidden");
        try{cleanup.create("new",request(false));throw new AssertionError("Unconfirmed original network cleanup freed native capacity");}
        catch(IllegalStateException expected){check(expected.getMessage().equals("network_cleanup_failed"),"Wrong network cleanup fence");}
        AndroidCreateHost callback=networkHost();AndroidCreateHost.NetworkCall active=startNetwork(callback,"tab");active.ready();AndroidCreateHost.MdoBrowserCdpBinding.Network nativeConnection=new AndroidCreateHost.MdoBrowserCdpBinding.Network();
        Thread worker=new Thread(()->{try{active.events.event(nativeConnection,"Fetch.requestPaused",null,new AndroidCreateHost.JSONObject());active.events.turn(nativeConnection);}catch(Exception e){throw new AssertionError(e);}});worker.start();worker.join();
        check(nativeConnection.downloads==1&&nativeConnection.turns==1&&nativeConnection.continued==0,"Network events/turns bypassed the private download controller");callback.revoke("tab");
        try{active.events.event(nativeConnection,"Fetch.requestPaused",null,new AndroidCreateHost.JSONObject());throw new AssertionError("Revoked callback used UI state or continued a request");}
        catch(java.io.IOException expected){check(nativeConnection.downloads==1,"Retired callback performed another native download action");}
        try{active.events.turn(nativeConnection);throw new AssertionError("Revoked owner ran another download turn");}catch(java.io.IOException expected){check(nativeConnection.turns==1,"Retired callback performed another download turn");}
        active.closed(true);
        AndroidCreateHost takeover=networkHost();AndroidCreateHost.NetworkCall original=startNetwork(takeover,"tab");original.ready();final int[] cancellations={0};
        takeover.beforeControlCancel=()->{check(original.marker.lease.live&&original.retired==1&&original.cancelled==0&&AndroidCreateHost.MdoBrowserCdpDownloads.latest.quiesces==1,"Takeover did not retain cleanup-only authority before cancelling UI work");cancellations[0]++;};
        takeover.takeover("tab");check(cancellations[0]==3,"Exact takeover prefix did not include original navigation, chooser and world cancellation");original.closed(true);
    }
    static void orderlyNetworkTakeover()throws Exception{
        AndroidCreateHost host=networkHost();AndroidCreateHost.NetworkCall original=startNetwork(host,"tab");original.ready();AndroidCreateHost.WebView view=host.views.get("tab");
        host.startPage("tab",view.url);host.finishPage("tab",view.url);
        AndroidCreateHost.MdoBrowserCdpDownloads downloads=AndroidCreateHost.MdoBrowserCdpDownloads.latest;int loads=view.loads;
        host.takeover("tab");check(host.manual.contains("tab")&&host.network("tab").data.get("phase").equals("draining"),"Takeover lost manual state or hid its cleanup phase");
        check(host.cdpViews.invalidated==1&&host.cdpViews.reason.equals("input_cancelled"),"Takeover retained ordinary frame execution admission while draining network cleanup");
        check(original.retired==1&&original.cancelled==0&&original.marker.current.getAsBoolean()&&!downloads.disconnected,"Orderly cleanup closed its original native connection too early");
        android.os.SystemClock.now+=100;android.os.Handler.tick();host.takeover("tab");check(original.retired==1&&downloads.quiesces==1&&view.loads==loads,"Repeated takeover restarted cleanup or navigation");
        host.pendingCommand("tab","navigate");
        try{host.returnControl("tab");throw new AssertionError("Unfinished retirement returned control");}catch(IllegalStateException expected){check(expected.getMessage().equals("network_retiring")&&host.manual.contains("tab"),"Wrong in-flight release boundary");}
        original.retired();check(host.network("tab").data.get("phase").equals("retired")&&Boolean.TRUE.equals(host.network("tab").data.get("responses_disabled"))&&downloads.disconnected,"Confirmed disable/close receipt was lost");
        check(view.loads==loads&&view.destroys==0&&android.os.Handler.pending()==0,"Takeover reloaded/destroyed the original page or retained a timer");
        int replies=host.replies;host.returnControl("tab");AndroidCreateHost.NetworkCall next=host.cdpWorker.calls.get(1);
        check(next!=original&&host.manual.contains("tab")&&host.documents.get("tab").manualBlocked&&host.replies==replies,"Release cleared manual state or replied before fresh native readiness");
        check(host.network("tab").data.get("phase").equals("rearming")&&view.loads==loads&&view.destroys==0&&downloads.rearms==1,"Same-page rearm navigated, destroyed, or discarded its original download controller");
        check(original.marker.quota==next.marker.quota&&!next.marker.quota.closed,"Rearm reset or retired the same-document lexical namespace budget");
        try{host.returnControl("tab");throw new AssertionError("Repeated release started another connection");}catch(IllegalStateException expected){check(expected.getMessage().equals("network_starting")&&downloads.rearms==1,"Repeated release changed the original rearm budget");}
        original.retired();check(!downloads.disconnected&&host.network("tab").data.get("phase").equals("rearming"),"Late original terminal callback disconnected its replacement");
        next.ready();check(!host.manual.contains("tab")&&!host.documents.get("tab").manualBlocked&&Boolean.TRUE.equals(host.network("tab").data.get("ready")),"Fresh native readiness failed to return control");
        check(Boolean.FALSE.equals(host.lastReply.data.get("manual"))&&Boolean.TRUE.equals(host.lastReply.data.get("downloads_intercepted"))&&host.replies==replies+1&&view.loads==loads,"Fresh control receipt was wrong or replayed navigation");
        next.ready();check(host.replies==replies+1&&view.loads==loads,"Duplicate rearm readiness replayed control or navigation");
        host.closeCommand("tab");
        AndroidCreateHost timeout=networkHost();AndroidCreateHost.NetworkCall slow=startNetwork(timeout,"tab");slow.ready();timeout.takeover("tab");
        android.os.SystemClock.now+=5500;android.os.Handler.tick();check(slow.cancelled==1&&!slow.marker.lease.live&&timeout.network("tab").data.get("code").equals("network_retirement_timeout"),"Expired retirement did not fall back to bounded native teardown");
        check(Boolean.FALSE.equals(timeout.network("tab").data.get("responses_disabled")),"Timeout invented a disable acknowledgement");slow.closed(true);timeout.closeCommand("tab");
    }
    static void rearmFailuresKeepManualPage()throws Exception{
        for(int mode=0;mode<8;mode++){
            AndroidCreateHost host=networkHost();AndroidCreateHost.NetworkCall original=startNetwork(host,"tab");original.ready();AndroidCreateHost.WebView view=host.views.get("tab");
            host.startPage("tab",view.url);host.finishPage("tab",view.url);host.takeover("tab");original.retired();int loads=view.loads,replies=host.replies;
            try{host.agentReturnControl("tab");throw new AssertionError("Agent ended user takeover");}catch(IllegalStateException expected){check(expected.getMessage().equals("user_takeover")&&host.manual.contains("tab"),"Agent release changed manual authority");}
            if(mode==0){host.startPage("tab","https://manual.invalid/next");
                try{host.returnControl("tab");throw new AssertionError("Loading manual page was rebound");}catch(IllegalStateException expected){check(expected.getMessage().equals("network_rearm_page_loading")&&host.cdpWorker.calls.size()==1,"Loading-page release consumed a native proof attempt");}
                host.finishPage("tab",view.url);host.returnControl("tab");host.cdpWorker.calls.get(1).ready();
                check(view.loads==loads&&!host.manual.contains("tab"),"Stable manual-page completion was replaced by navigation");host.closeCommand("tab");continue;
            }
            if(mode==1)host.cdpWorker.failStart=true;
            if(mode==2)android.os.Handler.fail=true;
            host.returnControl("tab");
            AndroidCreateHost.NetworkCall next=host.cdpWorker.calls.size()>1?host.cdpWorker.calls.get(1):null;
            if(mode==3){host.startPage("tab","https://manual.invalid/during-proof");next.ready();next.closed(true);}
            if(mode==4){android.os.SystemClock.now+=15000;android.os.Handler.tick();next.ready();next.closed(true);}
            if(mode==5){host.channel.lease=host.channel.new Lease();android.os.SystemClock.now+=100;android.os.Handler.tick();next.ready();next.closed(true);}
            if(mode==6){host.allowProfile=false;next.ready();next.closed(true);}
            if(mode==7){next.failed("cdp_binding_document_limit",true);}
            check(host.manual.contains("tab")&&host.documents.get("tab").manualBlocked&&view.loads==loads&&view.destroys==0&&!host.pageClosed.contains("tab"),"Failed rearm changed or destroyed the manual page: "+mode);
            check(Boolean.FALSE.equals(host.network("tab").data.get("ready"))&&host.network("tab").data.get("phase").equals("retired"),"Failed rearm reported active interception: "+mode);
            if(mode!=5)check(host.replies==replies+1&&Boolean.TRUE.equals(host.lastReply.data.get("manual"))&&Boolean.FALSE.equals(host.lastReply.data.get("ok")),"Failed rearm lost its manual-state receipt: "+mode);
            else check(host.replies==replies,"New main channel received an old control receipt");
            android.os.Handler.fail=false;host.closeCommand("tab");
        }
    }
    static void downloadRetirementAndServiceFailure()throws Exception{
        AndroidCreateHost host=networkHost();AndroidCreateHost.NetworkCall call=startNetwork(host,"tab");call.ready();
        host.cdpWorker.navigations.get(0).issued=true;host.cdpWorker.navigations.get(0).ack(true,false,true,null);host.startPage("tab","https://fixture.invalid/");host.finishPage("tab","https://fixture.invalid/");
        AndroidCreateHost.MdoBrowserCdpDownloads downloads=AndroidCreateHost.MdoBrowserCdpDownloads.latest;downloads.retiring=1;
        downloads.pending=true;try{host.pendingAgentCommand("tab","navigate");throw new AssertionError("Pending original response admitted another agent URL");}catch(IllegalStateException expected){check(expected.getMessage().equals("download_pending"),"Wrong download admission fence");}
        host.pendingAgentCommand("tab","state");host.pendingAgentCommand("tab","downloads");downloads.pending=false;
        check(!host.closeCommand("tab"),"Close confirmed before original network retirement");call.closed(true);
        check(downloads.closed&&downloads.disconnected&&Boolean.TRUE.equals(host.network("tab").data.get("installed")),"Private cache retirement lost its owner observation");
        check(((Number)host.capabilities().data.get("download_retiring")).intValue()==1,"Cache retirement disappeared from native capability state");
        check(!host.closeCommand("tab")&&((Number)host.capabilities().data.get("download_retiring")).intValue()==1,"Repeated close invented cleanup after the view disappeared");
        try{host.create("early",request(false));throw new AssertionError("Cache retirement admitted another browser");}catch(IllegalStateException expected){check(expected.getMessage().equals("native_view_retirement_busy"),"Wrong cache retirement fence");}
        downloads.retiring=0;android.os.SystemClock.now+=100;android.os.Handler.tick();
        check(!Boolean.TRUE.equals(host.network("tab").data.get("installed"))&&android.os.Handler.pending()==0,"Confirmed cache cleanup retained its observation timer");
        host.create("after",request(false));check(host.views.size()==1,"Confirmed cache cleanup poisoned subsequent creation");
        AndroidCreateHost failed=networkHost();failed.fault="download_service";failed.create("tab",request(true));failed.startPage("tab","about:blank");failed.finishPage("tab","about:blank");
        check(failed.created.get(0).loads==1&&failed.created.get(0).destroys==1&&failed.cdpWorker.calls.isEmpty(),"Cache authority failure sent a target or retained a view");
        AndroidCreateHost slow=networkHost();AndroidCreateHost.NetworkCall original=startNetwork(slow,"tab");original.ready();AndroidCreateHost.MdoBrowserCdpDownloads.latest.retiring=1;
        slow.closeCommand("tab");original.closed(true);android.os.SystemClock.now+=5000;android.os.Handler.tick();
        check(Boolean.TRUE.equals(slow.capabilities().data.get("network_cleanup_failed"))&&android.os.Handler.pending()==0,"Expired private cache observation was hidden or looped forever");
    }
    static AndroidCreateHost navigationHost()throws Exception{
        AndroidCreateHost host=networkHost();host.create("tab",new AndroidCreateHost.JSONObject().put("automation",true));
        host.startPage("tab","about:blank");host.finishPage("tab","about:blank");host.cdpWorker.calls.get(0).ready();
        host.startPage("tab","https://fixture.invalid/");host.finishPage("tab","https://fixture.invalid/");return host;
    }
    static void nativeNavigationRecords()throws Exception{
        for(int mode=0;mode<5;mode++){
            AndroidCreateHost host=navigationHost();AndroidCreateHost.WebView view=host.views.get("tab");int loads=view.loads;
            host.gotoPage("tab","https://navigation.invalid/target",30000);
            AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=host.cdpWorker.navigations.get(0);
            String id=(String)host.navigationState("tab").data.get("id");
            check(Boolean.TRUE.equals(host.lastReply.data.get("accepted"))&&id.equals(host.lastReply.data.get("navigation_ref"))&&view.loads==loads,"Native goto replayed WebView.loadUrl or lost its original accepted ID");
            check(host.documents.get("tab").navigationBlocked&&Boolean.TRUE.equals(host.loading.get("tab")),"Queued native goto lost its pre-navigation fence");
            try{host.gotoPage("tab","https://navigation.invalid/second",30000);throw new AssertionError("Pending native goto admitted another navigation");}catch(IllegalStateException expected){check(expected.getMessage().equals("navigation_pending")&&host.cdpWorker.navigations.size()==1,"Pending navigation replaced its original admission");}
            ticket.issued=true;host.legacyNavigationError("tab");check(host.documents.get("tab").navigationBlocked,"Unidentified legacy error released a pending native operation");
            if(mode==0)ticket.ack(true,true,false,null);
            if(mode==1)ticket.ack(true,false,false,null);
            if(mode==2)ticket.ack(true,false,true,null);
            if(mode==3)ticket.ack(false,false,false,"cdp_navigation_failed");
            if(mode==4)ticket.outcome=new AndroidCreateHost.MdoBrowserCdpSession.NavigationResult(false,false,false,false,true,false,"cdp_navigation_connection_closed");
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            check(host.navigationState("tab").data.get("id").equals(id)&&view.loads==loads,"Native completion changed its ID or reissued its target");
            if(mode==0||mode==3)check(!host.documents.get("tab").navigationBlocked&&!Boolean.TRUE.equals(host.loading.get("tab")),"Proven unchanged original document remained fenced");
            else check(host.documents.get("tab").navigationBlocked&&Boolean.TRUE.equals(host.loading.get("tab")),"Native ACK or uncertain effects were mistaken for page completion");
            if(mode==2){host.startPage("tab","https://navigation.invalid/target");host.finishPage("tab",view.url);check(!host.documents.get("tab").navigationBlocked&&!Boolean.TRUE.equals(host.loading.get("tab")),"Actual page callbacks failed to release committed navigation");}
            if(mode==3)check(host.errors.get("tab").equals("cdp_navigation_failed"),"Original native failure was masked");
            host.stopNavigation("tab");host.closeCommand("tab");
        }
    }
    static void nativeNavigationScopeAndCancel()throws Exception{
        for(int mode=0;mode<5;mode++){
            AndroidCreateHost host=navigationHost();AndroidCreateHost.WebView view=host.views.get("tab");int loads=view.loads;
            host.gotoPage("tab","https://navigation.invalid/original",30000);AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=host.cdpWorker.navigations.get(0);
            if(mode==0)host.stopNavigation("tab");
            if(mode==1){host.startPage("tab","https://new-page.invalid/");host.errors.put("tab","new-page-error");}
            if(mode==2)host.takeover("tab");
            if(mode==3)host.channel.lease=host.channel.new Lease();
            if(mode==4){ticket.issued=true;host.startPage("tab","https://new-page.invalid/");host.errors.put("tab","new-page-error");ticket.ack(true,true,false,null);}
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            if(mode!=4)check(ticket.cancelled&&!ticket.dispatched(),"Revoked queued navigation reached native dispatch");
            check(view.loads==loads,"Cancellation/scope change replayed a native target");
            if(mode==1||mode==4)check(Boolean.TRUE.equals(host.loading.get("tab"))&&host.errors.get("tab").equals("new-page-error"),"Old completion restored a newer document's loading/error state");
            if(mode==2)check(host.manual.contains("tab")&&host.documents.get("tab").manualBlocked,"Cancelled navigation released manual control");
            if(mode==0){host.gotoPage("tab","https://navigation.invalid/new",30000);check(host.cdpWorker.navigations.size()==2,"Confirmed no-effect cancellation poisoned explicit subsequent navigation");host.stopNavigation("tab");}
            host.closeCommand("tab");
        }
    }
    static void nativeControlRecords()throws Exception{
        for(String action:new String[]{"back","forward","reload"})for(int mode=0;mode<9;mode++){
            AndroidCreateHost host=navigationHost();AndroidCreateHost.WebView view=host.views.get("tab");long epoch=host.views.epoch("tab",view);int loads=view.loads;
            host.historyPage("tab",action,100,true);AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=host.cdpWorker.navigations.get(0);
            AndroidCreateHost.JSONObject queued=host.navigationState("tab");String reference=(String)queued.data.get("id");
            check(reference.equals(host.lastReply.data.get("navigation_ref"))&&action.equals(queued.data.get("action"))&&"control".equals(queued.data.get("receipt_kind"))&&Boolean.TRUE.equals(queued.data.get("control_supported")),"Native control lost its original action/kind/receipt");
            check(host.cdpWorker.navigationActions.equals(java.util.Arrays.asList(action))&&host.cdpWorker.navigationUrls.get(0)==null&&host.cdpWorker.navigationBudgets.get(0)==100,"Native control forwarded a caller URL or changed its budget");
            ticket.issued=true;ticket.ack(false,false,false,null);android.os.SystemClock.now+=20;android.os.Handler.tick();
            check(Boolean.TRUE.equals(host.loading.get("tab"))&&host.documents.get("tab").navigationBlocked,"An empty control ACK released page admission");
            readProofFails(host,"tab",reference,"cdp_navigation_document_unproven");
            if(mode==4)android.os.SystemClock.now+=100;
            else{ticket.committed=true;ticket.current=mode!=3;ticket.within=mode==1&&!action.equals("reload");}
            if(mode==2){host.startPage("tab","https://native-control.invalid/");host.finishPage("tab","https://native-control.invalid/");}
            if(mode==5)host.cancelNativeNavigation("tab",reference);
            if(mode==6)host.takeover("tab");
            if(mode==7)host.beforeNativeCommit=()->{host.channel.lease=host.channel.new Lease();host.errors.put("tab","new-owner-error");};
            if(mode==8)host.beforeNativeCommit=()->{throw new IllegalStateException("fixture adoption failure");};
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            AndroidCreateHost.JSONObject state=host.navigationState("tab");
            check(view.historyCalls==0&&view.loads==loads&&host.cdpWorker.navigations.size()==1,"Native control replayed through a WebView method");
            if(mode<3){
                check(!Boolean.TRUE.equals(host.loading.get("tab"))&&!host.documents.get("tab").navigationBlocked&&host.readProof("tab",reference)==ticket,"Original control proof was not joined for guarded reading");
                check(host.views.epoch("tab",view)==epoch+(mode==1&&!action.equals("reload")?0:1),"Native control failed to invalidate old document/input references");
                check(host.documents.get("tab").nativeCommits==(mode==0||mode==1&&action.equals("reload")?1:0),"Native commitment was invented or applied twice after WebView callbacks");
                if(mode==1&&!action.equals("reload")){host.startPage("tab","https://unexpected.invalid/");readProofFails(host,"tab",reference,"cdp_navigation_document_changed");}
            }else if(mode==3||mode==4)check((mode==3?"cdp_navigation_commit_revoked":"cdp_navigation_commit_timeout").equals(state.data.get("page_error")),"Control proof failure lost its original fixed reason");
            else check(Boolean.TRUE.equals(state.data.get("scope_revoked")),"A cancelled or changed control owner was adopted");
            if(mode==7)check("new-owner-error".equals(host.errors.get("tab")),"Old native proof cleared a new owner's error");
            if(mode==8)check("cdp_navigation_ui_join_failed".equals(state.data.get("page_error")),"Native UI adoption failure escaped its retained receipt");
            host.beforeNativeCommit=null;host.stopNavigation("tab");host.closeCommand("tab");
        }
        for(int mode=0;mode<3;mode++){
            AndroidCreateHost invalid=navigationHost();invalid.historyPage("tab","reload",1000,true);AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=invalid.cdpWorker.navigations.get(0);
            ticket.issued=true;ticket.committed=true;ticket.current=true;ticket.within=mode==2;ticket.ack(mode==0,false,mode==1,null);
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            check("cdp_navigation_control_reply_invalid".equals(invalid.navigationState("tab").data.get("page_error"))&&ticket.cancelled&&invalid.documents.get("tab").nativeCommits==0,"Malformed control metadata released the original realm or escaped its receipt");
            invalid.stopNavigation("tab");invalid.closeCommand("tab");
        }
        for(String action:new String[]{"back","forward","reload"}){
            AndroidCreateHost failed=navigationHost();failed.cdpWorker.failNavigation=true;
            try{failed.historyPage("tab",action,1000,true);throw new AssertionError("Rejected native control fell back to WebView");}catch(java.io.IOException expected){}
            check(failed.views.get("tab").historyCalls==0&&!Boolean.TRUE.equals(failed.loading.get("tab")),"Unsent native control left a fence or a fallback mutation");failed.closeCommand("tab");
            AndroidCreateHost manual=navigationHost();manual.takeover("tab");manual.historyPage("tab",action,1000,false);
            check(manual.views.get("tab").historyCalls==1&&manual.cdpWorker.navigations.isEmpty()&&manual.manual.contains("tab"),"Human manual navigation gained native automation admission");manual.closeCommand("tab");
        }
    }
    private static void readProofFails(AndroidCreateHost host,String key,String reference,String code)throws Exception{
        try{host.readProof(key,reference);throw new AssertionError("Unrelated navigation read gained authority");}
        catch(java.io.IOException expected){check(expected.getMessage().equals(code),"Navigation read failure lost its fixed reason");}
    }
    private static void sameDocumentProof()throws Exception{
        for(int mode=0;mode<4;mode++){
            AndroidCreateHost host=navigationHost();int loads=host.views.get("tab").loads;
            host.gotoPage("tab","https://navigation.invalid/#target",100);
            AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=host.cdpWorker.navigations.get(0);ticket.issued=true;ticket.ack(true,false,false,null);
            String reference=(String)host.navigationState("tab").data.get("id");
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            check(host.documents.get("tab").navigationBlocked&&Boolean.TRUE.equals(host.loading.get("tab")),"Same-document ACK alone released the original reservation");
            check(Boolean.TRUE.equals(host.navigationState("tab").data.get("same_document_supported"))&&Boolean.TRUE.equals(host.navigationState("tab").data.get("same_document")),"Same-document native support or original ACK identity missing");
            if(mode==2)android.os.SystemClock.now+=100;
            else{ticket.committed=true;ticket.current=mode!=1;}
            if(mode==3)host.startPage("tab","https://unrelated.invalid/");
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            if(mode==0){
                check(!host.documents.get("tab").navigationBlocked&&!Boolean.TRUE.equals(host.loading.get("tab")),"Proven same-document destination did not restore the original page readiness");
                check(host.readProof("tab",reference)==ticket,"Proven same-document read lost its original ticket");
                host.startPage("tab","https://unrelated.invalid/");readProofFails(host,"tab",reference,"cdp_navigation_document_changed");
            }else{
                check(host.navigationState("tab").data.get("page_error").equals(mode==2?"cdp_navigation_commit_timeout":"cdp_navigation_commit_revoked"),"Same-document failure adopted another page or lost its deadline");
            }
            check(host.views.get("tab").loads==loads,"Same-document proof replayed its target");host.stopNavigation("tab");host.closeCommand("tab");
        }
    }
    private static void originalNavigationReadProof()throws Exception{
        for(int mode=0;mode<5;mode++){
            AndroidCreateHost host=navigationHost();host.gotoPage("tab","https://native-read.invalid/",1000);
            AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=host.cdpWorker.navigations.get(0);ticket.issued=true;ticket.ack(true,false,true,null);
            String reference=(String)host.navigationState("tab").data.get("id");
            check(host.readProof("tab",null)==null,"Ordinary reads invented a navigation constraint");
            readProofFails(host,"tab",reference,"cdp_navigation_document_unproven");
            ticket.committed=true;ticket.current=true;
            readProofFails(host,"tab",reference,"cdp_navigation_document_unproven");
            host.startPage("tab","https://native-read.invalid/");host.finishPage("tab","https://native-read.invalid/");
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            check(host.readProof("tab",reference)==ticket,"Original native ticket was not retained for the reading connection");
            readProofFails(host,"tab","nv-unrelated","cdp_navigation_cancelled");
            readProofFails(host,"unrelated",reference,"cdp_navigation_cancelled");
            if(mode==0){ticket.current=false;readProofFails(host,"tab",reference,"cdp_navigation_document_unproven");}
            if(mode==1){host.takeover("tab");readProofFails(host,"tab",reference,"cdp_navigation_cancelled");}
            if(mode==2){host.cancelNativeNavigation("tab",reference);readProofFails(host,"tab",reference,"cdp_navigation_cancelled");}
            if(mode==3){host.gotoPage("tab","https://explicit-next.invalid/",1000);readProofFails(host,"tab",reference,"cdp_navigation_cancelled");}
            if(mode==4){host.revoke("tab");readProofFails(host,"tab",reference,"cdp_navigation_cancelled");}
            host.stopNavigation("tab");host.closeCommand("tab");
        }
    }
    public static void main(String[] args)throws Exception{
        for(int mode=0;mode<5;mode++){
            AndroidCreateHost host=navigationHost();AndroidCreateHost.WebView view=host.views.get("tab");int loads=view.loads;
            host.gotoPage("tab","https://native-commit.invalid/",100);
            AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=host.cdpWorker.navigations.get(0);ticket.issued=true;ticket.ack(true,false,true,null);
            Object original=ticket.result();
            if(mode==0||mode==4){host.startPage("tab","https://native-commit.invalid/");host.finishPage("tab","https://native-commit.invalid/");}
            if(mode==1||mode==2){ticket.committed=true;ticket.current=mode==1;}
            if(mode==4){host.gotoPage("tab","https://explicit-next.invalid/",100);ticket.committed=true;ticket.current=false;}
            android.os.SystemClock.now+=mode==3?101:20;android.os.Handler.tick();
            if(mode<2){
                check(host.navigationState("tab").data.get("page_error")==AndroidCreateHost.JSONObject.NULL,"Half a native/UI join invented a failure");
                if(mode==0){ticket.committed=true;ticket.current=true;}else{host.startPage("tab","https://native-commit.invalid/");host.finishPage("tab","https://native-commit.invalid/");}
                android.os.SystemClock.now+=20;android.os.Handler.tick();
                check(Boolean.TRUE.equals(host.navigationState("tab").data.get("page_current"))&&host.navigationState("tab").data.get("page_document").equals("nd-fixture-original"),"Exact original native/UI page proof was lost");
            }
            if(mode==2)check(host.navigationState("tab").data.get("page_error").equals("cdp_navigation_commit_revoked"),"Superseded original commit was treated as current");
            if(mode==3)check(host.navigationState("tab").data.get("page_error").equals("cdp_navigation_commit_timeout")&&ticket.result()==original,"Missing commit rewrote the original ACK or extended its deadline");
            if(mode==4)check(ticket.cancelled&&!host.cdpWorker.navigations.get(1).cancelled&&Boolean.TRUE.equals(host.loading.get("tab")),"Old commit watcher revoked or restored the explicit next operation");
            check(view.loads==loads,"Commit matching replayed a URL through WebView");host.stopNavigation("tab");host.closeCommand("tab");
        }
        for(int mode=0;mode<7;mode++){
            AndroidCreateHost host=networkHost();AndroidCreateHost.NetworkCall source=startNetwork(host,"tab");
            android.os.SystemClock.now+=1000;source.ready();AndroidCreateHost.WebView view=host.views.get("tab");
            AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=host.cdpWorker.navigations.get(0);
            check(Boolean.TRUE.equals(host.lastReply.data.get("created"))&&Boolean.TRUE.equals(host.lastReply.data.get("initial_navigation_pending"))&&
                host.lastReply.data.get("navigation_ref").equals(host.navigationState("tab").data.get("id")),"Create lost its original pending navigation reference");
            check(Boolean.TRUE.equals(host.navigationState("tab").data.get("initial"))&&view.loads==1&&host.initialPending("tab"),"Initial target used legacy loadUrl or lost its waiting fence");
            check(host.cdpWorker.navigationUrls.get(0).equals("https://fixture.invalid/")&&host.cdpWorker.navigationBudgets.get(0)==14000,"Initial native queue reset the original startup deadline or URL");
            host.finishPage("tab","about:blank");check(host.initialPending("tab"),"Late warmup completion hid a pending target");
            ticket.issued=mode!=4&&mode!=5;
            if(mode==0)ticket.ack(true,false,true,null);
            if(mode==1)ticket.ack(true,true,false,null);
            if(mode==2)ticket.ack(false,false,false,"cdp_navigation_failed");
            if(mode==3)ticket.outcome=new AndroidCreateHost.MdoBrowserCdpSession.NavigationResult(false,false,false,false,true,false,"cdp_navigation_connection_closed");
            if(mode==4)ticket.outcome=new AndroidCreateHost.MdoBrowserCdpSession.NavigationResult(true,false,false,false,false,false,"cdp_navigation_unsupported");
            if(mode==5)host.cancelNativeNavigation("tab",(String)host.navigationState("tab").data.get("id"));
            if(mode==6)ticket.ack(true,false,false,null);
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            if(mode==0){check(host.initialPending("tab"),"Original ACK was mistaken for page commit");host.startPage("tab","https://fixture.invalid/");host.finishPage("tab","https://fixture.invalid/");check(host.initialPending("tab"),"UI callback invented a native loader match");ticket.committed=true;ticket.current=true;check(!host.initialPending("tab"),"Original page and ACK did not settle");ticket.current=false;check(!host.initialPending("tab"),"A later ordinary page change resurrected the completed initial operation");}
            else check(host.initialPending("tab")== (mode==3),"Initial typed outcome lost its uncertainty/completion distinction");
            if(mode==6){check(host.navigationState("tab").data.get("page_error").equals("cdp_navigation_commit_unproven"),"Missing initial loader was promoted to a proven native commit");}
            check(view.loads==1&&host.replies==(mode==5?2:1),"Initial terminal event replayed target or creation receipt");
            host.closeCommand("tab");source.closed(true);
        }
        AndroidCreateHost refused=networkHost();AndroidCreateHost.NetworkCall admission=startNetwork(refused,"tab");refused.cdpWorker.failNavigation=true;admission.ready();
        check(refused.replies==0&&refused.views.size()==1,"Initial admission failure destroyed before native cleanup");admission.closed(true);
        check(refused.replies==1&&refused.views.size()==0&&Boolean.FALSE.equals(createReceipt(refused).data.get("navigation_may_have_occurred")),"Confirmed unsent initial target was retained or marked dispatched");
        AndroidCreateHost reentrant=networkHost();AndroidCreateHost.NetworkCall changing=startNetwork(reentrant,"tab");
        reentrant.beforeControlCancel=()->{reentrant.beforeControlCancel=null;reentrant.channel.lease=reentrant.channel.new Lease();};changing.ready();
        check(reentrant.cdpWorker.navigations.isEmpty()&&reentrant.created.get(0).loads==1,"Reentrant channel loss admitted the initial native target");changing.closed(true);
        check(reentrant.views.size()==0&&reentrant.replies==0,"Revoked initial channel retained an unsent view or received a stale reply");
        for(boolean schedule:new boolean[]{false,true}){
            AndroidCreateHost failed=networkHost();AndroidCreateHost.NetworkCall nativeOwner=startNetwork(failed,"tab");
            if(schedule)android.os.Handler.fail=true;else android.os.SystemClock.now+=14950;
            nativeOwner.ready();android.os.Handler.fail=false;
            check(failed.replies==(schedule?1:0)&&failed.created.get(0).loads==1&&failed.views.size()==1,"Initial failed admission sent a legacy target or invented cleanup");
            AndroidCreateHost.JSONObject early=schedule?createReceipt(failed):null;
            nativeOwner.closed(true);
            check(failed.replies==1&&failed.views.size()==0&&!Boolean.TRUE.equals(createReceipt(failed).data.get("navigation_may_have_occurred")),"Original initial budget/scheduler failure lost known unsent cleanup");
            check(schedule?failed.cdpWorker.navigations.size()==1:failed.cdpWorker.navigations.isEmpty(),"Expired initial budget issued a native command or scheduler failure replayed it");
            if(schedule)check(early==createReceipt(failed)&&Boolean.FALSE.equals(early.data.get("network_cleanup_confirmed"))&&Boolean.FALSE.equals(early.data.get("view_cleanup_confirmed")),"Late cleanup rewrote the original uncertain scheduler receipt");
        }
        for(int mode=0;mode<4;mode++){
            AndroidCreateHost host=navigationHost();AndroidCreateHost.WebView view=host.views.get("tab");int loads=view.loads;
            host.gotoPage("tab","https://navigation.invalid/original",30000);String id=(String)host.navigationState("tab").data.get("id");
            AndroidCreateHost.MdoBrowserCdpNetworks.Navigation ticket=host.cdpWorker.navigations.get(0);
            if(mode==1)host.channel.lease=host.channel.new Lease();
            if(mode==2){ticket.issued=true;host.takeover("tab");
                host.gotoPage("tab","https://human-page.invalid/",30000,false);
                host.startPage("tab","https://human-page.invalid/");host.errors.put("tab","human-page-error");
                ticket.ack(true,true,false,null);
                check(view.loads==loads+1&&host.cdpWorker.navigations.size()==1,"Original pending native ACK blocked or replayed human navigation");
            }
            host.cancelNativeNavigation("tab",mode==3?"nv-unrelated":id);
            boolean matched=mode==0||mode==2;
            check(Boolean.valueOf(matched).equals(host.lastReply.data.get("cancelled")),"Navigation cancellation adopted an unrelated alias/channel");
            check(ticket.cancelled==matched&&view.loads==loads+(mode==2?1:0),"Reference cleanup changed the current page or missed the queued operation");
            if(mode==2)check(host.manual.contains("tab")&&host.documents.get("tab").manualBlocked,"Agent cleanup released human control");
            android.os.SystemClock.now+=20;android.os.Handler.tick();
            if(mode==2)check(Boolean.TRUE.equals(host.loading.get("tab"))&&host.errors.get("tab").equals("human-page-error"),"Old result restored the manually opened page");
            host.closeCommand("tab");
        }
        profileFailure();ordinaryFailures();manualAndPopup();failedCleanup();reentrantCleanup();cleanupFaultsAndQuota();inputRetirementCallback();
        networkReadinessAndClose();networkCancellationAndTimeout();networkUncertaintyAndScope();downloadRetirementAndServiceFailure();orderlyNetworkTakeover();rearmFailuresKeepManualPage();nativeNavigationRecords();nativeNavigationScopeAndCancel();originalNavigationReadProof();sameDocumentProof();nativeControlRecords();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"exact_product_fragments\":true,\"native_control_ui_join\":true,\"framework_verified\":false,\"network_calls\":0}");
    }
}
