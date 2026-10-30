    // One retained navigation record per physical owner. The UI only submits
    // decisions/reads immutable results; the existing worker performs all I/O.
    private final class NativeNavigation {
        final String id="nv-"+java.util.UUID.randomUUID();final NetworkEntry owner;
        final NavigationRequest reservation;final int timeout;final boolean initial;final long born=android.os.SystemClock.elapsedRealtime();
        final Runnable watch=()->nativeNavigationPoll(this);
        MdoBrowserCdpNetworks.Navigation ticket;boolean revoked,settled,pageJoined;long revokedAt;String pageError;
        NativeNavigation(NetworkEntry owner,NavigationRequest reservation,int timeout,boolean initial){this.owner=owner;this.reservation=reservation;this.timeout=timeout;this.initial=initial;}
    }
    private boolean networkNavigate(String key,WebView view,String replyId,String target,boolean agent,long timeout)throws Exception {
        NetworkEntry owner=networkViews.get(key);if(owner==null||owner.view!=view||manual.contains(key)&&!agent)return false;
        NativeNavigation operation=beginNativeNavigation(owner,target,timeout,false);
        try{reply(owner.channelLease,replyId,new JSONObject().put("accepted",true).put("navigation_ref",operation.id),null);return true;}
        catch(Exception|Error failure){nativeNavigationRevoke(operation);throw failure;}
    }
    private NativeNavigation beginNativeNavigation(NetworkEntry owner,String target,long timeout,boolean initial)throws Exception {
        String key=owner.key;WebView view=owner.view;long epoch=views.epoch(key,view);
        if(!owner.ready||owner.retired||!networkCurrent(owner)||owner.marker==null)throw new IllegalStateException("network_owner_unavailable");
        if(timeout<100||timeout>600000)throw new IllegalArgumentException("cdp_navigation_arguments_invalid");
        if(navigationRequests.containsKey(key)||owner.navigation!=null&&owner.navigation.ticket!=null&&owner.navigation.ticket.result()==null)throw new IllegalStateException("navigation_pending");
        cancelPopup(key);cancelDialog(key);cancelCapture(key);views.stop(key,view);
        if(!networkCurrent(owner)||owner.retired||manual.contains(key)||views.epoch(key,view)!=epoch)throw new IllegalStateException("network_owner_changed");
        if(!dialogReady(key,view))throw new IllegalStateException("dialog_pending_or_unclean");
        NavigationRequest reservation=new NavigationRequest(view,views.epoch(key,view),Boolean.TRUE.equals(loading.get(key)));
        NativeNavigation operation=new NativeNavigation(owner,reservation,(int)timeout,initial);
        navigationRequests.put(key,reservation);owner.navigation=operation;
        try{
            loading.put(key,true);owner.profile.block(MdoBrowserDocument.NAVIGATION,true);
            if(!networkCurrent(owner)||owner.retired||manual.contains(key)||views.epoch(key,view)!=epoch)throw new IllegalStateException("network_owner_changed");
            operation.ticket=cdpWorker.navigateNetwork(owner.marker.lease(),target,(int)timeout);
            if(!networkHandler.postDelayed(operation.watch,20)){
                operation.ticket.cancel();networkRetire(owner,"cdp_navigation_scheduler_failed");throw new IllegalStateException("cdp_navigation_scheduler_failed");
            }
            return operation;
        }catch(Exception|Error failure){
            if(operation.ticket==null){
                if(views.owns(key,view)&&views.epoch(key,view)==reservation.epoch&&documents.get(key)==owner.profile)cancelNavigationRequest(key,view);
                else navigationRequests.remove(key,reservation);
                if(owner.navigation==operation)owner.navigation=null;
            }
            else{operation.ticket.cancel();nativeNavigationRevoke(operation);}
            throw failure;
        }
    }
    private boolean networkTargetMayHaveOccurred(NetworkEntry owner){
        NativeNavigation operation=owner.navigation;return operation!=null&&operation.initial&&operation.ticket!=null&&operation.ticket.dispatched();
    }
    private boolean networkInitialNavigationPending(String key){
        NetworkEntry owner=networkViews.get(key);NativeNavigation operation=owner==null?null:owner.navigation;
        if(operation==null||operation.owner!=owner||!operation.initial||operation.revoked||operation.pageError!=null||operation.ticket==null)return false;
        MdoBrowserCdpSession.NavigationResult result=operation.ticket.result();
        if(result!=null&&result.acknowledged&&result.ok&&operation.ticket.pageCommitted()&&operation.ticket.pageCurrent()&&navigationRequests.get(key)!=operation.reservation)operation.pageJoined=true;
        if(operation.pageJoined)return false;
        return result==null||result.effectMayHaveOccurred&&!result.acknowledged||result.ok&&
            (!(result.downloadKnown&&result.download)&&(!operation.ticket.pageCommitted()||!operation.ticket.pageCurrent()||navigationRequests.get(key)==operation.reservation));
    }
    private boolean nativeNavigationScope(NativeNavigation operation){
        NetworkEntry owner=operation.owner;
        return networkViews.get(owner.key)==owner&&owner.navigation==operation&&networkCurrent(owner)&&!manual.contains(owner.key)&&!owner.retired&&!owner.draining;
    }
    private void nativeNavigationRevoke(NativeNavigation operation){
        if(!operation.revoked){operation.revoked=true;operation.revokedAt=android.os.SystemClock.elapsedRealtime();}
        if(operation.ticket!=null)operation.ticket.cancel();
    }
    private void nativeNavigationRetire(NetworkEntry owner){if(owner.navigation!=null&&!owner.navigation.settled)nativeNavigationRevoke(owner.navigation);}
    private void nativeNavigationPageStarted(String key,WebView view){
        NetworkEntry owner=networkViews.get(key);NativeNavigation operation=owner==null||owner.view!=view?null:owner.navigation;
        if(operation!=null&&!operation.settled&&(operation.ticket==null||!operation.ticket.dispatched()))nativeNavigationRevoke(operation);
    }
    private void nativeNavigationRestore(NativeNavigation operation,MdoBrowserCdpSession.NavigationResult result){
        NetworkEntry owner=operation.owner;NavigationRequest original=operation.reservation;
        if(navigationRequests.get(owner.key)!=original||!views.owns(owner.key,owner.view)||views.epoch(owner.key,owner.view)!=original.epoch||documents.get(owner.key)!=owner.profile)return;
        // Exact terminal proof may explain the unchanged original document.
        // Unknown dispatched effects never release its input admission fence.
        // A target opened from the native warmup is cross-document. Even a
        // non-download ACK without loader metadata cannot prove that transition.
        if(!result.effectMayHaveOccurred||result.acknowledged&&(!result.ok||result.downloadKnown&&(result.download||!result.loaderPresent&&!operation.initial))){
            cancelNavigationRequest(owner.key,owner.view);
            if(!operation.revoked&&result.acknowledged&&!result.ok)errors.put(owner.key,result.nativeError==null?result.code:result.nativeError);
        }
    }
    private void nativeNavigationPoll(NativeNavigation operation){
        networkHandler.removeCallbacks(operation.watch);if(operation.settled)return;
        if(!nativeNavigationScope(operation))nativeNavigationRevoke(operation);
        MdoBrowserCdpSession.NavigationResult result=operation.ticket==null?null:operation.ticket.result();
        long now=android.os.SystemClock.elapsedRealtime(),age=now-operation.born;
        if(result!=null){
            nativeNavigationRestore(operation,result);
            boolean needsCommit=result.acknowledged&&result.ok&&!(result.downloadKnown&&result.download)&&(result.loaderPresent||operation.initial);
            if(needsCommit&&!operation.revoked){
                if(!result.loaderPresent)operation.pageError="cdp_navigation_commit_unproven";
                else if(!operation.pageJoined&&operation.ticket.pageCommitted()&&!operation.ticket.pageCurrent())operation.pageError="cdp_navigation_commit_revoked";
                else if(!operation.pageJoined&&(!operation.ticket.pageCommitted()||navigationRequests.get(operation.owner.key)==operation.reservation)){
                    if(age<0||age>=operation.timeout){operation.pageError="cdp_navigation_commit_timeout";networkRetire(operation.owner,operation.pageError);}
                    else{if(!networkHandler.postDelayed(operation.watch,20)){operation.pageError="cdp_navigation_commit_timeout";nativeNavigationRevoke(operation);networkRetire(operation.owner,"cdp_navigation_scheduler_failed");operation.settled=true;}return;}
                }else operation.pageJoined=true;
            }
            operation.settled=true;return;
        }
        if(!operation.revoked&&(age<0||age>=operation.timeout)){nativeNavigationRevoke(operation);networkRetire(operation.owner,"cdp_navigation_timeout");}
        if(operation.revoked&&(now-operation.revokedAt<0||now-operation.revokedAt>=5500)){operation.settled=true;return;}
        if(!networkHandler.postDelayed(operation.watch,20)){nativeNavigationRevoke(operation);networkRetire(operation.owner,"cdp_navigation_scheduler_failed");operation.settled=true;}
    }
    private JSONObject networkNavigationState(String key)throws JSONException {
        NetworkEntry owner=networkViews.get(key);NativeNavigation operation=owner==null?null:owner.navigation;
        if(operation==null||operation.ticket==null)return null;
        MdoBrowserCdpSession.NavigationResult result=operation.ticket.result();
        JSONObject value=new JSONObject().put("id",operation.id).put("phase",result==null?(operation.ticket.dispatched()?"sent":"queued"):result.ok?"acknowledged":"failed")
            .put("scope_revoked",operation.revoked||operation.owner!=owner).put("dispatched",operation.ticket.dispatched()).put("initial",operation.initial)
            .put("page_commit_supported",true).put("page_committed",operation.ticket.pageCommitted()).put("page_current",operation.ticket.pageCurrent())
            .put("page_document",operation.ticket.pageDocument()==null?JSONObject.NULL:operation.ticket.pageDocument()).put("page_error",operation.pageError==null?JSONObject.NULL:operation.pageError);
        if(result!=null)value.put("acknowledged",result.acknowledged).put("ok",result.ok).put("download_known",result.downloadKnown).put("download",result.download)
            .put("loader_present",result.loaderPresent).put("code",result.code==null?JSONObject.NULL:result.code).put("native_error",result.nativeError==null?JSONObject.NULL:result.nativeError);
        return value;
    }
    private void executeNavigationCancel(String key,String id,WebView view,JSONObject request)throws JSONException {
        NetworkEntry owner=networkViews.get(key);NativeNavigation operation=owner==null?null:owner.navigation;
        boolean matches=operation!=null&&operation.ticket!=null&&owner.view==view&&views.owns(key,view)&&operation.id.equals(request.optString("navigation_ref"))&&
            channel.current()==operation.owner.channelLease&&channel.active(operation.owner.channelLease,host.getUrl());
        if(matches)nativeNavigationRevoke(operation);
        reply(id,new JSONObject().put("cancelled",matches).put("dispatched",matches&&operation.ticket.dispatched()),null);
    }
