    // UI-owned physical view/profile/channel associations. Navigation changes
    // document authority, never this original network owner. No public wire IDs.
    private final Map<String,NetworkEntry> networkViews=new HashMap<>();
    private final android.os.Handler networkHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean networkCleanupFailed;
    private final class NetworkEntry {
        final String key,id,target;final WebView view;final MdoBrowserDocument profile;
        final MdoBrowserChannel<WebMessagePort>.Lease channelLease;
        final MdoBrowserCdpMarker.DocumentQuota quota=new MdoBrowserCdpMarker.DocumentQuota();
        final java.util.concurrent.atomic.AtomicBoolean live=new java.util.concurrent.atomic.AtomicBoolean(true);
        final long born=android.os.SystemClock.elapsedRealtime();final Runnable watch=()->networkWatch(this);
        MdoBrowserCdpMarker marker;MdoBrowserCdpDownloads downloads;long warmupEpoch,retiredAt;
        boolean warming=true,starting,ready,created,retired,protocolDrained=true,destroyed,replySent,targetMayHaveOccurred,cleanupObservationExpired;
        String code;
        NetworkEntry(String key,WebView view,MdoBrowserDocument profile,MdoBrowserChannel<WebMessagePort>.Lease lease,String id,String target){
            this.key=key;this.view=view;this.profile=profile;channelLease=lease;this.id=id;this.target=target;
        }
    }
    private boolean networkCurrent(NetworkEntry entry){
        return networkViews.get(entry.key)==entry&&!stopped&&!browserPaused&&views.owns(entry.key,entry.view)&&
            documents.get(entry.key)==entry.profile&&entry.profile.profileAssigned()&&!manual.contains(entry.key)&&!pageClosed.contains(entry.key)&&
            channel.current()==entry.channelLease&&channel.active(entry.channelLease,host.getUrl());
    }
    private int networkRetiring(){int count=0;for(NetworkEntry entry:networkViews.values())if(entry.retired&&!entry.protocolDrained)count++;return count;}
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
        if(entry.retired&&(agent||Arrays.asList("automation","frames","input","wheel","drag","upload").contains(command)))throw new IllegalStateException("network_owner_unavailable");
        if(agent&&entry.downloads!=null&&entry.downloads.pending()&&Arrays.asList("navigate","back","forward","reload","automation","input","wheel","drag","upload").contains(command))
            throw new IllegalStateException(entry.downloads.failure()==null?"download_pending":entry.downloads.failure());
        if(entry.ready||entry.retired)return;
        if(!Arrays.asList("state","bounds","close","takeover","stop").contains(command))throw new IllegalStateException("network_starting");
    }
    private void networkStop(String key,WebView view){NetworkEntry entry=networkViews.get(key);if(entry!=null&&entry.view==view&&!entry.ready)networkRetire(entry,"network_start_cancelled");}
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
        try{
            int budget=networkRemaining(entry,100);
            entry.marker=MdoBrowserCdpView.network(view,owned->owned==view&&networkCurrent(entry)&&entry.live.get(),entry.quota).marker();
            // The worker callback uses only atomic admission and the original
            // native connection. Never consult UI objects from this thread.
            final java.util.concurrent.atomic.AtomicBoolean live=entry.live;
            MdoBrowserDownloadRuntime service=MdoBrowserDownloadRuntime.get(activity);
            final MdoBrowserCdpDownloads downloads=new MdoBrowserCdpDownloads(service.files,service.budget);entry.downloads=downloads;
            entry.protocolDrained=false;
            cdpWorker.startNetwork(entry.marker,budget,new MdoBrowserCdpNetworks.Events(){
                public void event(MdoBrowserCdpBinding.Network connection,String method,String child,JSONObject params)throws java.io.IOException {
                    if(!live.get())throw new java.io.IOException("cdp_network_cancelled");downloads.event(connection,method,child,params);
                }
                public void turn(MdoBrowserCdpBinding.Network connection)throws java.io.IOException {
                    if(!live.get())throw new java.io.IOException("cdp_network_cancelled");downloads.turn(connection);
                }
            },result->networkResult(entry,result));
        }catch(java.io.IOException|RuntimeException|Error failure){
            entry.protocolDrained=entry.marker==null||!cdpWorker.networkPending(entry.marker);
            String code=failure instanceof java.io.IOException?networkProtocolCode(failure.getMessage(),"network_start_failed"):"network_start_failed";
            networkRetire(entry,code);
        }
        return true;
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
                if(entry.retired||!networkCurrent(entry)||!"about:blank".equals(entry.view.getUrl())||views.epoch(entry.key,entry.view)!=entry.warmupEpoch)throw new IllegalStateException("network_owner_changed");
                entry.starting=false;entry.ready=true;networkHandler.removeCallbacks(entry.watch);
                if(!"about:blank".equals(entry.target)){entry.targetMayHaveOccurred=true;entry.view.loadUrl(entry.target);}else loading.put(entry.key,false);
                if(!networkCurrent(entry)||entry.retired)throw new IllegalStateException("network_owner_changed");
                entry.created=true;entry.replySent=true;
                reply(entry.channelLease,entry.id,new JSONObject().put("created",true).put("downloads_intercepted",true),null);
            }catch(JSONException|RuntimeException|Error failure){
                String code="network_start_timeout".equals(failure.getMessage())?"network_start_timeout":"network_start_failed";
                networkRetire(entry,entry.targetMayHaveOccurred?"network_navigation_uncertain":code);
            }
            return;
        }
        entry.protocolDrained=result.cleanupConfirmed;
        if(!result.cleanupConfirmed)networkCleanupFailed=true;
        if(!entry.retired){entry.retired=true;entry.retiredAt=android.os.SystemClock.elapsedRealtime();entry.code=networkProtocolCode(result.code,"network_connection_failed");}
        entry.live.set(false);networkHandler.removeCallbacks(entry.watch);
        if(entry.marker!=null)try{entry.marker.close();}catch(RuntimeException|Error failure){networkCleanupFailed=true;}
        if(entry.downloads!=null)try{entry.downloads.disconnect();}catch(RuntimeException|Error failure){networkCleanupFailed=true;}
        if(!entry.replySent)networkReplyFailure(entry);
        else if(entry.protocolDrained)networkDiscardUnsent(entry);
        networkRelease(entry);
    }
    private void networkRetire(NetworkEntry entry,String reason){
        if(networkViews.get(entry.key)!=entry)return;
        if(!entry.retired){entry.retired=true;entry.ready=false;entry.code=reason;entry.retiredAt=android.os.SystemClock.elapsedRealtime();}
        entry.live.set(false);networkHandler.removeCallbacks(entry.watch);
        try{
            if(entry.marker!=null){entry.marker.close();cdpWorker.cancelNetwork(entry.marker.lease());}
            if(entry.downloads!=null)entry.downloads.disconnect();
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
        if(entry.retired){
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
        networkDiscardUnsent(entry);
        if(views.owns(entry.key,entry.view)){pageClosed.add(entry.key);loading.put(entry.key,false);errors.put(entry.key,entry.code==null?"network_start_failed":entry.code);}
        try{reply(entry.channelLease,entry.id,new JSONObject().put("ok",false).put("created",false)
            .put("error",new JSONObject().put("code",entry.code==null?"network_start_failed":entry.code))
            .put("native_create",new JSONObject().put("created_may_have_occurred",true).put("navigation_may_have_occurred",entry.targetMayHaveOccurred)
                .put("network_cleanup_confirmed",entry.protocolDrained&&!networkCleanupFailed).put("view_cleanup_confirmed",entry.destroyed&&!nativeViewCleanupFailed)),null);
        }catch(JSONException|RuntimeException|Error ignored){}
    }
    private void networkDiscardUnsent(NetworkEntry entry){
        if(!entry.created&&!entry.targetMayHaveOccurred&&entry.protocolDrained&&views.owns(entry.key,entry.view)){
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
    private String networkProtocolCode(String code,String fallback){return code!=null&&code.matches("cdp_[a-z_]{1,80}")?code:fallback;}
    private JSONObject networkState(String key)throws JSONException {
        NetworkEntry entry=networkViews.get(key);
        return new JSONObject().put("installed",entry!=null).put("ready",entry!=null&&entry.ready&&!entry.retired)
            .put("phase",entry==null?"none":entry.retired?"retired":entry.ready?"ready":entry.warming?"warming":"starting")
            .put("code",entry==null||entry.code==null?JSONObject.NULL:entry.code)
            .put("cleanup_observation_expired",entry!=null&&entry.cleanupObservationExpired)
            .put("cleanup_failed",networkCleanupFailed).put("retiring",networkRetiring());
    }
