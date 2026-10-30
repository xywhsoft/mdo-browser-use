    // UI-owned physical view/profile/channel associations. Navigation changes
    // document authority, never this original network owner. No public wire IDs.
    private final Map<String,NetworkEntry> networkViews=new HashMap<>();
    private final android.os.Handler networkHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean networkCleanupFailed;
    private final class NetworkEntry {
        final String key,id,target;final WebView view;final MdoBrowserDocument profile;
        final MdoBrowserChannel<WebMessagePort>.Lease channelLease;
        final MdoBrowserCdpMarker.DocumentQuota quota;
        final java.util.concurrent.atomic.AtomicBoolean live=new java.util.concurrent.atomic.AtomicBoolean(true);
        final long born=android.os.SystemClock.elapsedRealtime();final Runnable watch=()->networkWatch(this);
        MdoBrowserCdpMarker marker;MdoBrowserCdpDownloads downloads;MdoBrowserCdpDownloads.Connection downloadConnection;long warmupEpoch,retiredAt;
        NativeNavigation navigation;
        boolean warming=true,starting,ready,created,retired,draining,rearm,responsesDisabled,protocolDrained=true,destroyed,replySent,targetMayHaveOccurred,cleanupObservationExpired;
        String code;
        NetworkEntry(String key,WebView view,MdoBrowserDocument profile,MdoBrowserChannel<WebMessagePort>.Lease lease,String id,String target){
            this(key,view,profile,lease,id,target,new MdoBrowserCdpMarker.DocumentQuota());
        }
        NetworkEntry(String key,WebView view,MdoBrowserDocument profile,MdoBrowserChannel<WebMessagePort>.Lease lease,String id,String target,MdoBrowserCdpMarker.DocumentQuota quota){
            this.key=key;this.view=view;this.profile=profile;channelLease=lease;this.id=id;this.target=target;this.quota=quota;
        }
    }
    private boolean networkCurrent(NetworkEntry entry){
        return networkViews.get(entry.key)==entry&&!stopped&&!browserPaused&&views.owns(entry.key,entry.view)&&
            documents.get(entry.key)==entry.profile&&entry.profile.profileAssigned()&&(!manual.contains(entry.key)||entry.draining||entry.rearm&&entry.starting)&&!pageClosed.contains(entry.key)&&
            channel.current()==entry.channelLease&&channel.active(entry.channelLease,host.getUrl());
    }
    private int networkRetiring(){int count=0;for(NetworkEntry entry:networkViews.values())if(entry.draining||entry.retired&&!entry.protocolDrained)count++;return count;}
    private int downloadRetiring(){int count=0;for(NetworkEntry entry:networkViews.values())if(entry.downloads!=null)count+=entry.downloads.retiringFiles();return count;}
    private boolean networkClean(){return !networkCleanupFailed&&networkRetiring()==0&&downloadRetiring()==0;}
    private void networkAttach(String key,WebView view,boolean required){
        NetworkEntry entry=networkViews.get(key);
        if(entry!=null&&entry.view==view&&(!entry.ready||entry.retired||!networkCurrent(entry)))throw new IllegalStateException("network_owner_unavailable");
        if(required&&(entry==null||entry.view!=view))throw new IllegalStateException("network_owner_unavailable");
    }
    private void networkCommand(String key,WebView view,String command,boolean agent){
        NetworkEntry entry=networkViews.get(key);if(entry==null||entry.view!=view)return;
        if("downloads".equals(command)&&entry.downloads!=null)return;
        if(entry.navigation!=null&&entry.navigation.ticket!=null&&entry.navigation.ticket.result()==null&&(agent||!manual.contains(key))&&Arrays.asList("navigate","back","forward","reload","automation","input","wheel","drag","upload").contains(command))throw new IllegalStateException("navigation_pending");
        if(entry.retired&&(agent||Arrays.asList("automation","frames","input","wheel","drag","upload").contains(command)))throw new IllegalStateException("network_owner_unavailable");
        if(agent&&entry.downloads!=null&&entry.downloads.pending()&&Arrays.asList("navigate","back","forward","reload","automation","input","wheel","drag","upload").contains(command))
            throw new IllegalStateException(entry.downloads.failure()==null?"download_pending":entry.downloads.failure());
        if(entry.draining){if(!agent)return;throw new IllegalStateException("network_retiring");}
        if(entry.ready||entry.retired)return;
        if(!Arrays.asList("state","bounds","close","takeover","stop").contains(command))throw new IllegalStateException("network_starting");
    }
    private void networkStop(String key,WebView view){NetworkEntry entry=networkViews.get(key);if(entry!=null&&entry.view==view){nativeNavigationRetire(entry);if(!entry.ready)networkRetire(entry,"network_start_cancelled");}}
    private void networkTakeover(String key,WebView view){
        NetworkEntry entry=networkViews.get(key);if(entry==null||entry.view!=view||entry.retired||entry.draining)return;
        if(!entry.ready||entry.marker==null||entry.downloads==null){networkRetire(entry,"network_user_takeover");return;}
        entry.draining=true;entry.ready=false;entry.retiredAt=android.os.SystemClock.elapsedRealtime();entry.code="network_user_takeover";
        nativeNavigationRetire(entry);
        // Explicit human takeover releases only the original UI reservation;
        // its native receipt remains retained and cannot restore a later page.
        cancelNavigationRequest(key,view);
        networkHandler.removeCallbacks(entry.watch);
        try{
            entry.downloads.quiesce();cdpWorker.retireNetwork(entry.marker.lease());
            if(!networkHandler.postDelayed(entry.watch,100))throw new IllegalStateException("network_retirement_scheduler_unavailable");
        }catch(RuntimeException|Error failure){networkRetire(entry,"network_retirement_failed");}
    }
    /** Human return-control is asynchronous: keep manual admission blocked
     * until fresh native proof/frame bootstrap/Fetch.enable are acknowledged. */
    private boolean networkReleaseControl(String key,WebView view,String id,boolean agent)throws java.io.IOException {
        NetworkEntry entry=networkViews.get(key);if(entry==null||entry.view!=view)return false;
        if(entry.draining)throw new IllegalStateException("network_retiring");
        if(!entry.retired){if(entry.rearm&&entry.starting)throw new IllegalStateException("network_starting");return false;}
        if(agent)throw new IllegalStateException("user_takeover");
        if(!entry.protocolDrained||!networkClean())throw new IllegalStateException("network_retiring");
        if(!manual.contains(key)||stopped||browserPaused||!views.owns(key,view)||documents.get(key)!=entry.profile||!entry.profile.profileAssigned()||
            pageClosed.contains(key)||channel.current()!=entry.channelLease||!channel.active(entry.channelLease,host.getUrl()))throw new IllegalStateException("network_owner_unavailable");
        if(Boolean.TRUE.equals(loading.get(key)))throw new IllegalStateException("network_rearm_page_loading");
        if(entry.downloads==null)throw new IllegalStateException("network_owner_unavailable");
        NetworkEntry next=new NetworkEntry(key,view,entry.profile,entry.channelLease,id,null,entry.quota);
        next.downloads=entry.downloads;next.rearm=true;next.warming=false;next.starting=true;next.created=true;next.warmupEpoch=views.epoch(key,view);
        next.navigation=entry.navigation;
        next.downloadConnection=entry.downloads.rearm();
        networkHandler.removeCallbacks(entry.watch);networkViews.put(key,next);
        networkStart(next);return true;
    }
    private void networkBegin(String key,WebView view,String id,String target){
        MdoBrowserDocument profile=documents.get(key);MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        if(profile==null||!profile.profileAssigned()||lease==null||!channel.active(lease,host.getUrl()))throw new IllegalStateException("network_profile_or_channel_unconfirmed");
        if(networkViews.containsKey(key)||networkViews.size()>=8||!networkClean())throw new IllegalStateException("network_owner_unavailable");
        for(NetworkEntry old:networkViews.values())if(old.view==view)throw new IllegalStateException("network_owner_already_registered");
        NetworkEntry entry=new NetworkEntry(key,view,profile,lease,id,target);networkViews.put(key,entry);
        try{
            if(!networkCurrent(entry))throw new IllegalStateException("network_owner_changed");
            if(!networkHandler.postDelayed(entry.watch,100))throw new IllegalStateException("network_scheduler_unavailable");
            view.loadUrl("about:blank");
        }catch(RuntimeException|Error failure){networkRetire(entry,"network_warmup_failed");}
    }
    private void networkNavigation(String key,WebView view,String address){
        NetworkEntry entry=networkViews.get(key);if(entry==null||entry.view!=view||entry.retired||entry.ready)return;
        if(entry.rearm){networkRetire(entry,"network_rearm_navigation_changed");return;}
        if(!entry.warming||entry.warmupEpoch!=0||!"about:blank".equals(address)||!networkCurrent(entry)){
            networkRetire(entry,"network_bootstrap_navigation_changed");return;
        }
        entry.warmupEpoch=views.epoch(key,view);
    }
    /** Return true while this completion belongs to the initial native blank
     * document, so it cannot clear the target navigation's loading state. */
    private boolean networkLoaded(String key,WebView view,String address){
        NetworkEntry entry=networkViews.get(key);if(entry==null||entry.view!=view||entry.retired||entry.ready)return false;
        if(!entry.warming)return true;
        if(!"about:blank".equals(address)||!"about:blank".equals(view.getUrl())||entry.warmupEpoch<=0||views.epoch(key,view)!=entry.warmupEpoch||!networkCurrent(entry)){
            networkRetire(entry,"network_bootstrap_navigation_changed");return true;
        }
        entry.warming=false;entry.starting=true;
        networkStart(entry);return true;
    }
    private void networkStart(NetworkEntry entry){
        try{
            final WebView view=entry.view;
            int budget=networkRemaining(entry,100);
            if(!networkCurrent(entry)||entry.rearm&&(Boolean.TRUE.equals(loading.get(entry.key))||views.epoch(entry.key,view)!=entry.warmupEpoch))throw new IllegalStateException("network_owner_changed");
            entry.marker=MdoBrowserCdpView.network(view,owned->owned==view&&networkCurrent(entry)&&entry.live.get(),entry.quota).marker();
            // The worker callback uses only atomic admission and the original
            // native connection. Never consult UI objects from this thread.
            final java.util.concurrent.atomic.AtomicBoolean live=entry.live;
            if(entry.downloads==null){MdoBrowserDownloadRuntime service=MdoBrowserDownloadRuntime.get(activity);
                entry.downloads=new MdoBrowserCdpDownloads(service.files,service.budget);entry.downloadConnection=entry.downloads.connection();}
            final MdoBrowserCdpDownloads.Connection downloads=entry.downloadConnection;
            entry.protocolDrained=false;
            if(!networkHandler.postDelayed(entry.watch,100))throw new IllegalStateException("network_scheduler_unavailable");
            cdpWorker.startNetwork(entry.marker,budget,new MdoBrowserCdpNetworks.Events(){
                public void event(MdoBrowserCdpBinding.Network connection,String method,String child,JSONObject params)throws java.io.IOException {
                    if(!live.get())throw new java.io.IOException("cdp_network_cancelled");downloads.event(connection,method,child,params);
                }
                public void turn(MdoBrowserCdpBinding.Network connection)throws java.io.IOException {
                    if(!live.get())throw new java.io.IOException("cdp_network_cancelled");downloads.turn(connection);
                }
                public boolean navigationAllowed(){return live.get()&&downloads.navigationAllowed();}
            },result->networkResult(entry,result));
        }catch(java.io.IOException|RuntimeException|Error failure){
            entry.protocolDrained=entry.marker==null||!cdpWorker.networkPending(entry.marker);
            if(entry.protocolDrained&&entry.downloadConnection!=null)entry.downloadConnection.disconnect(true);
            String code=failure instanceof java.io.IOException?networkProtocolCode(failure.getMessage(),"network_start_failed"):"network_start_failed";
            networkRetire(entry,code);
        }
    }
    private int networkRemaining(NetworkEntry entry,int minimum){
        long used=android.os.SystemClock.elapsedRealtime()-entry.born;
        if(used<0||used>15000-minimum)throw new IllegalStateException("network_start_timeout");return (int)(15000-used);
    }
    private void networkResult(NetworkEntry entry,MdoBrowserCdpNetworks.Result result){
        if(networkViews.get(entry.key)!=entry)return;
        if(result.ready){
            if(entry.ready&&!entry.retired)return;
            try{
                networkRemaining(entry,10);
                if(entry.retired||!networkCurrent(entry)||views.epoch(entry.key,entry.view)!=entry.warmupEpoch||
                    (entry.rearm?Boolean.TRUE.equals(loading.get(entry.key)):!"about:blank".equals(entry.view.getUrl())))throw new IllegalStateException("network_owner_changed");
                if(entry.rearm){
                    JSONObject outcome=new JSONObject().put("manual",false).put("downloads_intercepted",true);
                    entry.profile.block(MdoBrowserDocument.MANUAL,false);views.manual(entry.key,entry.view,false);manual.remove(entry.key);
                    if(!networkCurrent(entry)||entry.retired||views.epoch(entry.key,entry.view)!=entry.warmupEpoch||Boolean.TRUE.equals(loading.get(entry.key)))throw new IllegalStateException("network_owner_changed");
                    entry.starting=false;entry.ready=true;networkHandler.removeCallbacks(entry.watch);entry.replySent=true;
                    reply(entry.channelLease,entry.id,outcome,null);return;
                }
                entry.starting=false;entry.ready=true;networkHandler.removeCallbacks(entry.watch);
                if(!"about:blank".equals(entry.target)){entry.targetMayHaveOccurred=true;entry.view.loadUrl(entry.target);}else loading.put(entry.key,false);
                if(!networkCurrent(entry)||entry.retired)throw new IllegalStateException("network_owner_changed");
                entry.created=true;entry.replySent=true;
                reply(entry.channelLease,entry.id,new JSONObject().put("created",true).put("downloads_intercepted",true),null);
            }catch(JSONException|RuntimeException|Error failure){
                if(entry.rearm&&views.owns(entry.key,entry.view)){manual.add(entry.key);
                    try{entry.profile.block(MdoBrowserDocument.MANUAL,true);views.manual(entry.key,entry.view,true);}catch(RuntimeException|Error ignored){}}
                String code="network_start_timeout".equals(failure.getMessage())?"network_start_timeout":"network_start_failed";
                networkRetire(entry,entry.targetMayHaveOccurred?"network_navigation_uncertain":code);
            }
            return;
        }
        entry.protocolDrained=result.cleanupConfirmed;
        entry.responsesDisabled=result.responsesDisabled;
        if(!result.cleanupConfirmed)networkCleanupFailed=true;
        if(!entry.retired){entry.retired=true;entry.retiredAt=android.os.SystemClock.elapsedRealtime();
            if(!entry.draining||!"cdp_network_retired".equals(result.code))entry.code=networkProtocolCode(result.code,"network_connection_failed");}
        entry.draining=false;entry.ready=false;
        entry.live.set(false);networkHandler.removeCallbacks(entry.watch);
        if(entry.marker!=null)try{entry.marker.close();}catch(RuntimeException|Error failure){networkCleanupFailed=true;}
        if(entry.downloadConnection!=null)try{entry.downloadConnection.disconnect(result.cleanupConfirmed);}catch(RuntimeException|Error failure){networkCleanupFailed=true;}
        if(!entry.replySent)networkReplyFailure(entry);
        else if(entry.protocolDrained)networkDiscardUnsent(entry);
        networkRelease(entry);
    }
    private void networkRetire(NetworkEntry entry,String reason){
        if(networkViews.get(entry.key)!=entry)return;
        nativeNavigationRetire(entry);
        entry.draining=false;
        if(!entry.retired){entry.retired=true;entry.ready=false;entry.code=reason;entry.retiredAt=android.os.SystemClock.elapsedRealtime();}
        entry.live.set(false);networkHandler.removeCallbacks(entry.watch);
        try{
            if(entry.marker!=null){entry.marker.close();cdpWorker.cancelNetwork(entry.marker.lease());}
            if(entry.downloadConnection!=null)entry.downloadConnection.disconnect();
        }catch(RuntimeException|Error failure){networkCleanupFailed=true;}
        if(entry.protocolDrained){if(!entry.replySent)networkReplyFailure(entry);networkRelease(entry);}
        else if(!networkHandler.postDelayed(entry.watch,100)){
            entry.cleanupObservationExpired=true;if(!entry.replySent)networkReplyFailure(entry);
        }
    }
    private void networkRetire(String key,WebView view,String reason){NetworkEntry entry=networkViews.get(key);if(entry!=null&&entry.view==view)networkRetire(entry,reason);}
    private void networkRetireAll(String reason){for(NetworkEntry entry:new ArrayList<>(networkViews.values()))networkRetire(entry,reason);}
    private void networkWatch(NetworkEntry entry){
        if(networkViews.get(entry.key)!=entry)return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(entry.draining){
            if(!networkCurrent(entry)||now-entry.retiredAt<0||now-entry.retiredAt>=5500){networkRetire(entry,!networkCurrent(entry)?"network_owner_changed":"network_retirement_timeout");return;}
        }else if(entry.retired){
            if(!entry.protocolDrained&&cdpWorker.terminated()){
                if(entry.marker==null||!cdpWorker.networkPending(entry.marker))entry.protocolDrained=true;else networkCleanupFailed=true;
            }
            if(entry.protocolDrained){if(!entry.replySent)networkReplyFailure(entry);networkRelease(entry);return;}
            if(now-entry.retiredAt<0||now-entry.retiredAt>=5000){entry.cleanupObservationExpired=true;if(!entry.replySent)networkReplyFailure(entry);return;}
        }else if(!networkCurrent(entry)||now-entry.born<0||now-entry.born>=15000){
            networkRetire(entry,!networkCurrent(entry)?"network_owner_changed":"network_start_timeout");return;
        }
        if(!networkHandler.postDelayed(entry.watch,100)){entry.cleanupObservationExpired=true;networkRetire(entry,"network_scheduler_unavailable");}
    }
    private void networkReplyFailure(NetworkEntry entry){
        if(entry.replySent)return;entry.replySent=true;
        if(entry.rearm){
            try{reply(entry.channelLease,entry.id,new JSONObject().put("ok",false).put("manual",true)
                .put("error",new JSONObject().put("code",entry.code==null?"network_start_failed":entry.code))
                .put("network_cleanup_confirmed",entry.protocolDrained&&!networkCleanupFailed),null);}catch(JSONException|RuntimeException|Error ignored){}
            return;
        }
        networkDiscardUnsent(entry);
        if(views.owns(entry.key,entry.view)){pageClosed.add(entry.key);loading.put(entry.key,false);errors.put(entry.key,entry.code==null?"network_start_failed":entry.code);}
        try{reply(entry.channelLease,entry.id,new JSONObject().put("ok",false).put("created",false)
            .put("error",new JSONObject().put("code",entry.code==null?"network_start_failed":entry.code))
            .put("native_create",new JSONObject().put("created_may_have_occurred",true).put("navigation_may_have_occurred",entry.targetMayHaveOccurred)
                .put("network_cleanup_confirmed",entry.protocolDrained&&!networkCleanupFailed).put("view_cleanup_confirmed",entry.destroyed&&!nativeViewCleanupFailed)),null);
        }catch(JSONException|RuntimeException|Error ignored){}
    }
    private void networkDiscardUnsent(NetworkEntry entry){
        if(!entry.rearm&&!entry.created&&!entry.targetMayHaveOccurred&&entry.protocolDrained&&views.owns(entry.key,entry.view)){
            try{closeAndDestroyDocument(entry.key,entry.view);}catch(RuntimeException|Error failure){nativeViewCleanupFailed=true;}
            if(views.get(entry.key)==null){manual.remove(entry.key);loading.remove(entry.key);errors.remove(entry.key);errorCodes.remove(entry.key);crashed.remove(entry.key);locations.remove(entry.key);}
        }
    }
    private void networkDestroyed(String key,WebView view){
        NetworkEntry entry=networkViews.get(key);if(entry==null||entry.view!=view)return;
        entry.destroyed=true;entry.quota.close();networkRelease(entry);
    }
    private void networkDestroyed(WebView view){for(NetworkEntry entry:new ArrayList<>(networkViews.values()))if(entry.view==view){networkDestroyed(entry.key,view);return;}}
    private void networkRelease(NetworkEntry entry){
        if(entry.destroyed&&entry.downloads!=null)try{entry.downloads.close();}catch(RuntimeException|Error failure){networkCleanupFailed=true;}
        if(entry.destroyed&&entry.protocolDrained){
            networkHandler.removeCallbacks(entry.watch);
            if(entry.downloads==null||entry.downloads.retiringFiles()==0){networkViews.remove(entry.key,entry);return;}
            long age=android.os.SystemClock.elapsedRealtime()-entry.retiredAt;
            if(age<0||age>=5000){entry.cleanupObservationExpired=true;networkCleanupFailed=true;return;}
            if(!networkHandler.postDelayed(entry.watch,100)){entry.cleanupObservationExpired=true;networkCleanupFailed=true;}
        }
    }
    private String networkProtocolCode(String code,String fallback){return code!=null&&code.matches("(?:cdp|download_frame)_[a-z_]{1,80}")?code:fallback;}
    private JSONObject networkState(String key)throws JSONException {
        NetworkEntry entry=networkViews.get(key);
        return new JSONObject().put("installed",entry!=null).put("ready",entry!=null&&entry.ready&&!entry.retired)
            .put("phase",entry==null?"none":entry.draining?"draining":entry.retired?"retired":entry.ready?"ready":entry.rearm?"rearming":entry.warming?"warming":"starting")
            .put("responses_disabled",entry!=null&&entry.responsesDisabled)
            .put("code",entry==null||entry.code==null?JSONObject.NULL:entry.code)
            .put("cleanup_observation_expired",entry!=null&&entry.cleanupObservationExpired)
            .put("cleanup_failed",networkCleanupFailed).put("retiring",networkRetiring());
    }
