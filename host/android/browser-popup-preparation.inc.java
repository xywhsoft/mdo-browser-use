    // The UI keeps this bounded scope; worker callbacks retain it weakly. A new
    // request never replaces an unretired source under a reused browser key.
    private final class PopupPreparation {
        final String key;final WebView view;final long epoch;final NetworkEntry network;final PopupNetwork protectedOwner;
        MdoBrowserCdpPopupPreparation preparation;
        PopupPreparation(String key,WebView view,NetworkEntry network){this.key=key;this.view=view;this.network=network;protectedOwner=network.popup;epoch=views.epoch(key,view);}
        boolean current(){return protocolPopupPreparations.get(key)==this&&epoch>0&&views.owns(key,view)&&views.epoch(key,view)==epoch&&networkViews.get(key)==network&&
            network.ready&&!network.retired&&!network.draining&&networkCurrent(network)&&!manual.contains(key)&&!pageClosed.contains(key)&&
            (protectedOwner==null||network.popup==protectedOwner&&protectedOwner.current()&&protectedOwner.page!=null&&
                (protectedOwner.page.documentReady()||protectedOwner.page.continuingNativePreparation(this))&&protectedOwner.sourceProof!=null&&protectedOwner.sourceProof.current());}
        void lost(String reason){if(protocolPopupPreparations.get(key)!=this||!views.owns(key,view))return;
            views.input(key,view).invalidate("input_cancelled");cdpViews.invalidate(key,view,views.epoch(key,view),"input_cancelled");
            if(protectedOwner!=null)protocolPopupDocumentInvalidated(key,view,"popup_preparation_retired");}
    }
    private final Map<String,PopupPreparation> protocolPopupPreparations=new HashMap<>();
    interface ProtocolPopupReady {void ready(MdoBrowserCdpPopupNative.Promotion owner,MdoBrowserCdpPopupBroker.Handoff handoff)throws java.io.IOException;}
    interface ProtocolPopupNetworkReady {void ready(PopupNetwork network)throws java.io.IOException;}
    interface ProtocolPopupPageReady {void ready(PopupPage page)throws java.io.IOException;}
    private boolean protocolPopupCreatePending(String key){return protocolPopupHandoffs.containsKey(key);}
    /** Retain only immutable layout data. A popup URL must never be replayed. */
    private JSONObject protocolPopupLayout(String key,JSONObject request)throws JSONException {
        if(request.has("visible")&&!(request.opt("visible") instanceof Boolean)||request.has("bounds")&&!(request.opt("bounds") instanceof JSONObject))throw new IllegalArgumentException("popup_layout_invalid");
        JSONObject supplied=request.optJSONObject("bounds"),box=new JSONObject();
        String[] names={"x","y","width","height","scale"};double[] defaults={0,0,640,480,1};
        for(int i=0;i<names.length;i++){
            Object value=supplied==null?null:supplied.opt(names[i]);
            if(value!=null&&(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())))throw new IllegalArgumentException("popup_layout_invalid");
            box.put(names[i],value==null?defaults[i]:((Number)value).doubleValue());
        }
        return new JSONObject().put("browser",key).put("bounds",box).put("visible",request.optBoolean("visible",true));
    }
    /** The normal create command joins the exact pending native transport;
     * global automation admission still precedes this private product route. */
    private void executeProtocolPopupCreate(String key,String id,JSONObject request)throws java.io.IOException,JSONException {
        nativeOwnerAdmission();
        String pending=request.optString("popup"),opener=request.optString("opener");WebView exactOpener=views.get(opener);
        if(key.equals(opener)||pending.isEmpty()||opener.isEmpty()||exactOpener==null||views.get(key)!=null)throw new java.io.IOException("cdp_popup_handoff_arguments_invalid");
        final JSONObject layout=protocolPopupLayout(key,request);final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        final java.util.concurrent.atomic.AtomicBoolean replied=new java.util.concurrent.atomic.AtomicBoolean();
        handoffPreparedProtocolPopupPage(opener,exactOpener,pending,key,9000,page->{try{
            page.remaining();
            PopupNetwork network=page.network;WebView exact=network.entry.view;
            if(!page.current()||!page.downloadOnly&&!page.documentReady()||!network.entry.key.equals(key)||!views.owns(key,exact)||channel.current()!=lease||!channel.active(lease,host.getUrl())||popupLinks.containsKey(key))throw new java.io.IOException("cdp_popup_publication_owner_changed");
            JSONObject result=new JSONObject().put("ok",true).put("created",true).put("popup_attached",true).put("downloads_intercepted",true).put("initial_navigation_pending",false);
            PopupLink link=new PopupLink(exact,opener,pending);popupLinks.put(key,link);
            crashed.remove(key);errors.remove(key);errorCodes.remove(key);pageClosed.remove(key);
            String address=exact.getUrl();locations.put(key,address==null?"about:blank":address);
            root.addView(exact);
            page.remaining();
            if(!page.current()||!views.owns(key,exact)||popupLinks.get(key)!=link||channel.current()!=lease||!channel.active(lease,host.getUrl()))throw new java.io.IOException("cdp_popup_publication_owner_changed");
            bounds(exact,layout);
            page.remaining();
            if(!page.current()||layout.optBoolean("visible",true)&&!page.downloadOnly&&!page.documentReady()||popupLinks.get(key)!=link||channel.current()!=lease||!channel.active(lease,host.getUrl()))throw new java.io.IOException("cdp_popup_publication_owner_changed");
            link.ready=true;if(replied.compareAndSet(false,true))reply(lease,id,result,null);
        }catch(JSONException error){throw new java.io.IOException("cdp_popup_publication_result_invalid");}},(code,effect)->{
            if(!replied.compareAndSet(false,true))return;
            try{reply(lease,id,new JSONObject().put("ok",false).put("created",false).put("popup_attached",false)
                .put("error",new JSONObject().put("code",code!=null&&code.matches("cdp_[a-z_]{1,80}")?code:"cdp_popup_create_failed"))
                .put("native_create",new JSONObject().put("created_may_have_occurred",effect).put("navigation_may_have_occurred",effect)
                    .put("network_cleanup_confirmed",false).put("view_cleanup_confirmed",!effect&&!nativeViewCleanupFailed&&MdoBrowserCdpPopupNative.creationConfirmed()&&views.get(key)==null)),null);}
            catch(JSONException error){reply(lease,id,null,"popup_result_invalid");}
        });
    }
    /** Fixed worker-only world warmup. No UI owner, caller script or target ID. */
    private static final class PopupPageWarmup implements MdoBrowserCdpWorker.Operation {
        static byte inventoryState(JSONObject inventory)throws java.io.IOException {
            org.json.JSONArray frames=inventory.optJSONArray("frames");JSONObject root=null;
            if(!Boolean.TRUE.equals(inventory.opt("ok"))||frames==null||frames.length()>32)throw new java.io.IOException("cdp_popup_page_inventory_invalid");
            for(int i=0;i<frames.length();i++){
                JSONObject frame=frames.optJSONObject(i);if(frame==null)throw new java.io.IOException("cdp_popup_page_inventory_invalid");
                if("f0".equals(frame.opt("id"))){if(root!=null)throw new java.io.IOException("cdp_popup_page_inventory_invalid");root=frame;}
            }
            if(root==null||!(root.opt("loading") instanceof Boolean)||!(root.opt("isolated") instanceof Boolean))throw new java.io.IOException("cdp_popup_page_inventory_invalid");
            if(root.optBoolean("loading"))return 0;
            if(!root.optBoolean("isolated"))throw new java.io.IOException("cdp_popup_page_world_unavailable");
            return 1;
        }
        public byte[] run(MdoBrowserCdpBinding binding,MdoBrowserCdpWorker.Deadline budget)throws java.io.IOException {
            return new byte[]{inventoryState(binding.frames().inventory(budget))};
        }
    }
    private static final class PopupPageForward implements MdoBrowserCdpWorker.Completion {
        private final java.lang.ref.WeakReference<PopupPage> owner;
        PopupPageForward(PopupPage owner){this.owner=new java.lang.ref.WeakReference<>(owner);}
        public void done(MdoBrowserCdpWorker.Result result){PopupPage value=owner.get();if(value!=null)value.completed(result);}
    }
    /** One UI-owned startup on the original guarded network. Download-only
     * popups do not need a document world. A ready world certifies only its
     * captured native document epoch, never a later navigation. */
    private static final class PopupPageNavigationForward implements MdoBrowserCdpWorker.Completion {
        private final java.lang.ref.WeakReference<PopupPage> owner;private final java.lang.ref.WeakReference<NativeNavigation> navigation;private final java.lang.ref.WeakReference<MdoBrowserCdpMarker> marker;
        PopupPageNavigationForward(PopupPage owner,NativeNavigation navigation,MdoBrowserCdpMarker marker){this.owner=new java.lang.ref.WeakReference<>(owner);this.navigation=new java.lang.ref.WeakReference<>(navigation);this.marker=new java.lang.ref.WeakReference<>(marker);}
        public void done(MdoBrowserCdpWorker.Result result){PopupPage value=owner.get();NativeNavigation original=navigation.get();MdoBrowserCdpMarker document=marker.get();if(value!=null&&original!=null&&document!=null)value.navigationCompleted(original,document,result);}
    }
    private final class PopupPage implements AutoCloseable {
        final PopupNetwork network;final Runnable watch=this::poll;
        ProtocolPopupPageReady ready;MdoBrowserCdpPopupPreparation.Failure failure;
        MdoBrowserCdpMarker marker;MdoBrowserCdpPopupNative.Document proof;
        int phase;long epoch;boolean inFlight,downloadOnly,worldReady,suspended,restoring;NativeNavigation navigating;
        boolean naturalReadRecovery;MdoBrowserCdpWorker.DocumentRead restoringRead;
        // An original onCreateWindow callback revokes document execution, but
        // its exact prepared native transport must still be consumable once.
        // UI-only receipt: it never keeps a DOM marker or input reference alive.
        PopupPreparation pendingPreparation;
        PopupPage(PopupNetwork network,ProtocolPopupPageReady ready,MdoBrowserCdpPopupPreparation.Failure failure){this.network=network;this.ready=ready;this.failure=failure;}
        boolean current(){return phase<3&&network.page==this&&network.current();}
        boolean documentCurrent(){return marker!=null&&marker.lease().current()&&views.owns(network.entry.key,network.entry.view)&&views.epoch(network.entry.key,network.entry.view)==epoch;}
        boolean documentReady(){return phase==2&&!downloadOnly&&worldReady&&!restoring&&current()&&documentCurrent();}
        boolean continuingNativePreparation(PopupPreparation original){return original!=null&&pendingPreparation==original&&protocolPopupPreparations.get(network.entry.key)==original&&original.protectedOwner==network&&original.epoch==epoch&&phase==2&&!downloadOnly&&current()&&views.epoch(network.entry.key,network.entry.view)==epoch;}
        void invalidated(String reason){
            if(phase!=2||downloadOnly)return;
            PopupPreparation original=protocolPopupPreparations.get(network.entry.key);
            boolean ownPopup="popup_pending".equals(reason)&&original!=null&&original.protectedOwner==network&&original.epoch==epoch&&
                (worldReady||continuingNativePreparation(original))&&current()&&views.epoch(network.entry.key,network.entry.view)==epoch&&original.preparation.originalNativePending()!=null;
            pendingPreparation=ownPopup?original:null;
            worldReady=false;restoring=false;restoringRead=null;
            // Remember only retry intent when preparation ends under takeover
            // or another admission loss. A later explicit inventory must still
            // prove this original physical Source and its current document.
            if("popup_preparation_retired".equals(reason)&&!current()&&views.owns(network.entry.key,network.entry.view)&&views.epoch(network.entry.key,network.entry.view)==epoch)naturalReadRecovery=true;
            // UI-only loss can rearm this exact epoch. A different document
            // additionally needs explicit inventory and an original-source guard;
            // pinned actions never create replacement authority here.
            suspended=(ownPopup||"input_view_unavailable".equals(reason)||"input_viewport_changed".equals(reason)||"popup_preparation_retired".equals(reason))&&current()&&
                views.owns(network.entry.key,network.entry.view)&&views.epoch(network.entry.key,network.entry.view)==epoch;
            NativeNavigation originalNavigation=navigating;
            if("frame_changed".equals(reason)&&originalNavigation!=null&&nativeNavigationScope(originalNavigation)&&!originalNavigation.revoked&&originalNavigation.pageError==null&&originalNavigation.ticket!=null&&originalNavigation.ticket.pageCurrent()){
                originalNavigation.settled=false;
                if(!networkHandler.postDelayed(originalNavigation.watch,20)){originalNavigation.pageError="cdp_navigation_scheduler_failed";nativeNavigationRevoke(originalNavigation);}
            }
        }
        /** A different UI epoch requires this original source's native commit;
         * document/loader/destination proof runs before world inventory. */
        boolean joinNavigation(NativeNavigation operation){
            if(!current()||operation.owner!=network.entry||!nativeNavigationScope(operation)||operation.revoked||operation.ticket==null||!operation.ticket.pageCurrent()){
                operation.pageError="cdp_navigation_document_changed";return false;
            }
            if(navigating==operation&&documentReady())return true;
            String key=network.entry.key;WebView view=network.entry.view;long observed=views.epoch(key,view);
            if(!cdpEnvironmentCurrent(key,view,observed,network.entry.channelLease)||!chooserReady(dialogs.get(key))||views.inputBusy(key,view)||!views.inputClean(key,view))return false;
            if(inFlight||cdpWorker.pending(marker))return false;
            // Old preparation may still deliver its original invalidation.
            // Preserve this navigation's deadline; never warm a replacement
            // marker while that previous action can retire it.
            PopupPreparation previous=protocolPopupPreparations.get(key);
            if(previous!=null&&!previous.preparation.settled())return false;
            try{
                long used=android.os.SystemClock.elapsedRealtime()-operation.born;if(used<0||used>operation.timeout-100)throw new java.io.IOException("cdp_navigation_document_timeout");
                if(navigating!=operation||!documentCurrent()||!restoring){
                    epoch=observed;marker=protocolDocumentMarker(key,network.owner,network.handoff);proof=network.owner.document(marker);
                    navigating=operation;worldReady=false;restoring=true;suspended=false;pendingPreparation=null;downloadOnly=false;restoringRead=null;naturalReadRecovery=false;
                }
                inFlight=true;
                cdpWorker.submitDocumentGuarded(marker,network.handoff,proof,Math.min(3000,(int)(operation.timeout-used)),false,operation.ticket,new PopupPageWarmup(),new PopupPageNavigationForward(this,operation,marker));
            }catch(java.io.IOException|RuntimeException|Error failure){inFlight=false;operation.pageError=reason(failure);restoreFailed(marker);}
            return false;
        }
        void navigationCompleted(NativeNavigation operation,MdoBrowserCdpMarker original,MdoBrowserCdpWorker.Result result){
            if(navigating!=operation||marker!=original)return;inFlight=false;
            if(!nativeNavigationScope(operation)||operation.revoked||!current())return;
            if(!documentCurrent()){
                worldReady=false;restoring=false;
                if(views.epoch(network.entry.key,network.entry.view)==epoch)operation.pageError="cdp_navigation_document_changed";return;
            }
            if(result.code!=null||operation.ticket==null||!operation.ticket.pageCurrent()||!cdpEnvironmentCurrent(network.entry.key,network.entry.view,epoch,network.entry.channelLease)){
                operation.pageError=result.code==null?"cdp_navigation_document_changed":result.code;restoreFailed(original);return;
            }
            if(result.bytes==null||result.bytes.length!=1||(result.bytes[0]!=0&&result.bytes[0]!=1)){operation.pageError="cdp_popup_page_result_invalid";restoreFailed(original);return;}
            if(result.bytes[0]==1){worldReady=true;restoring=false;pendingPreparation=null;}
        }
        void navigationRejected(NativeNavigation operation){
            if(current()&&operation.owner==network.entry&&network.entry.navigation==operation&&!downloadOnly&&views.epoch(network.entry.key,network.entry.view)==operation.reservation.epoch){
                navigating=null;worldReady=false;restoring=false;suspended=true;pendingPreparation=null;
            }
        }
        void restoreFailed(MdoBrowserCdpMarker original){
            if(marker!=original||!restoring)return;
            worldReady=false;restoring=false;suspended=false;pendingPreparation=null;restoringRead=null;
            cdpViews.invalidate(network.entry.key,network.entry.view,epoch,"popup_restore_failed");
        }
        void restored(MdoBrowserCdpMarker original,JSONObject inventory)throws java.io.IOException {
            if(marker!=original||!restoring||!current()||!documentCurrent())throw new java.io.IOException("cdp_worker_cancelled");
            if(restoringRead!=null&&!restoringRead.current())throw new java.io.IOException("cdp_navigation_document_changed");
            if(PopupPageWarmup.inventoryState(inventory)==1){worldReady=true;restoring=false;pendingPreparation=null;restoringRead=null;naturalReadRecovery=false;}
            // Loading inventory is useful, but does not certify a world. A later
            // explicit read may continue the same marker; never poll or replay.
        }
        int remaining()throws java.io.IOException {
            if(!current())throw new java.io.IOException("cdp_popup_page_owner_changed");
            long left=network.deadline-android.os.SystemClock.elapsedRealtime();if(left<100)throw new java.io.IOException("cdp_popup_page_timeout");return (int)Math.min(3000,left);
        }
        void schedule(){networkHandler.removeCallbacks(watch);if(!networkHandler.postDelayed(watch,50))throw new IllegalStateException("cdp_popup_page_scheduler_failed");}
        void start(){
            try{
                remaining();if(!loading.containsKey(network.entry.key))loading.put(network.entry.key,true);
                activateProtocolPopup(network.owner,network.handoff);schedule();
            }
            catch(java.io.IOException|RuntimeException|Error error){fail(reason(error));}
        }
        private String reason(Throwable error){String code=error.getMessage();return code!=null&&code.matches("(?:cdp|network)_[a-z_]{1,80}")?code:"cdp_popup_page_failed";}
        void poll(){
            if(phase>=2)return;
            try{
                MdoBrowserCdpSession.ControlResult flow=network.handoff.flowFailure();
                if(flow!=null&&!flow.ok)throw new java.io.IOException(flow.code==null?"cdp_popup_page_activation_failed":flow.code);
                int timeout=remaining();
                MdoBrowserCdpSession.ControlResult active=network.handoff.activationResult();
                if(active==null){schedule();return;}
                if(!active.ok)throw new java.io.IOException(active.code==null?"cdp_popup_page_activation_failed":active.code);
                if(phase==0)phase=1;
                // An offer from this exact physical root is useful without DOM.
                // Never replace the offer, consume its bytes or decide for user.
                for(MdoBrowserCdpDownloads.Snapshot item:network.entry.downloads.list())if(item.root&&item.frameCurrent){
                    if(!inFlight&&marker==null){downloadOnly=true;accepted();return;}
                }
                if(inFlight||Boolean.TRUE.equals(loading.get(network.entry.key))){schedule();return;}
                if(marker==null){
                    if(nativeViewCleanupFailed||networkCleanupFailed)nativeOwnerAdmission();
                    // Another original view may still be retiring on this
                    // shared wire. Keep this ready owner's original deadline
                    // instead of failing its document bootstrap on a busy gate.
                    if(!nativeRetiringViews.isEmpty()||networkRetiring()!=0||downloadRetiring()!=0){schedule();return;}
                }
                if(marker==null){epoch=views.epoch(network.entry.key,network.entry.view);marker=protocolDocumentMarker(network.entry.key,network.owner,network.handoff);proof=network.owner.document(marker);}
                if(!documentCurrent())throw new java.io.IOException("cdp_popup_page_document_changed");
                inFlight=true;schedule();
                cdpWorker.submitDocument(marker,network.handoff,proof,Math.min(timeout,remaining()),false,new PopupPageWarmup(),new PopupPageForward(this));
            }catch(java.io.IOException|RuntimeException|Error error){fail(reason(error));}
        }
        void completed(MdoBrowserCdpWorker.Result result){
            if(phase>=2)return;inFlight=false;
            if(result.code!=null){fail(result.code);return;}
            try{
                remaining();if(!documentCurrent())throw new java.io.IOException("cdp_popup_page_document_changed");
                if(result.bytes==null||result.bytes.length!=1||(result.bytes[0]!=0&&result.bytes[0]!=1))throw new java.io.IOException("cdp_popup_page_result_invalid");
                if(result.bytes[0]==0){schedule();return;}accepted();
            }catch(java.io.IOException|RuntimeException|Error error){fail(reason(error));}
        }
        private void accepted()throws java.io.IOException {
            remaining();if(!downloadOnly&&!documentCurrent())throw new java.io.IOException("cdp_popup_page_document_changed");
            phase=2;worldReady=!downloadOnly;networkHandler.removeCallbacks(watch);ProtocolPopupPageReady callback=ready;ready=null;
            if(callback==null){fail("cdp_popup_page_callback_failed");return;}
            try{callback.ready(this);}catch(java.io.IOException|RuntimeException|Error error){fail("cdp_popup_page_callback_failed");return;}
            failure=null;
        }
        void retired(String code){
            if(phase==3)return;phase=3;inFlight=false;worldReady=false;restoring=false;suspended=false;pendingPreparation=null;navigating=null;restoringRead=null;naturalReadRecovery=false;networkHandler.removeCallbacks(watch);ready=null;
            if(marker!=null){
                try{marker.close();}catch(RuntimeException|Error error){networkCleanupFailed=true;code="cdp_popup_page_cleanup_unconfirmed";}
                try{cdpWorker.cancel(marker.lease());}catch(RuntimeException|Error error){networkCleanupFailed=true;code="cdp_popup_page_cleanup_unconfirmed";}
            }proof=null;
            MdoBrowserCdpPopupPreparation.Failure callback=failure;failure=null;
            if(callback!=null)try{callback.failed(code,true);}catch(RuntimeException|Error ignored){/* one startup outcome */}
        }
        void fail(String code){if(phase==3)return;network.retire(code);}
        @Override public void close(){fail("cdp_popup_page_cancelled");}
    }
    private PopupPage prepareProtocolPopupPage(PopupNetwork network,ProtocolPopupPageReady ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();if(network==null||ready==null||failure==null||network.phase!=1||!network.current())throw new java.io.IOException("cdp_popup_page_arguments_invalid");
        if(network.page!=null)throw new java.io.IOException("cdp_popup_page_busy");
        PopupPage page=new PopupPage(network,ready,failure);network.page=page;page.start();return page;
    }
    private void protocolPopupDocumentInvalidated(String key,WebView view,String reason){
        NetworkEntry entry=networkViews.get(key);if(entry!=null&&entry.view==view&&entry.popup!=null&&entry.popup.page!=null)entry.popup.page.invalidated(reason);
    }
    /** Dispatcher family only, never readiness or public automation admission. */
    private boolean protocolPopupFrameRouting(String key){NetworkEntry entry=networkViews.get(key);return entry!=null&&entry.popup!=null||protocolPopupHandoffs.containsKey(key);}
    private void requireLegacyDocumentRoute(String key)throws java.io.IOException {
        if(protocolPopupFrameRouting(key))throw new java.io.IOException("cdp_popup_document_route_required");
    }
    /** UI snapshot only: never prepare, restore, submit a job or create a realm.
     * Native popup frames have no legacy single-world document identity. */
    private JSONObject protocolPopupDocumentState(String key)throws JSONException {
        NetworkEntry entry=networkViews.get(key);
        if(entry==null||entry.popup==null){
            PopupHandoff pending=protocolPopupHandoffs.get(key);if(pending==null)return null;
            return new JSONObject().put("adapter","android-native-frames-v1").put("installed",false).put("ready",false).put("phase","starting")
                .put("pending",0).put("pending_scope","document_worker").put("native_document",JSONObject.NULL)
                .put("profile_isolation",pending.profile!=null&&pending.profile.profileAssigned());
        }
        PopupNetwork owner=entry.popup;PopupPage page=owner.page;
        boolean installed=page!=null&&page.documentReady()&&page.proof!=null&&owner.handoff.owns(page.proof)&&page.proof.current();
        boolean pending=page!=null&&cdpWorker.pending(page.marker);
        MdoBrowserPopups<WebView> core=windows.get(key);boolean popupPending=core!=null&&core.busy();
        boolean ready=installed&&!pending&&cdpEnvironmentCurrent(key,entry.view,page.epoch,entry.channelLease)&&chooserReady(dialogs.get(key))&&
            !views.inputBusy(key,entry.view)&&views.inputClean(key,entry.view)&&!nativeViewCleanupFailed&&!networkCleanupFailed&&
            MdoBrowserCdpPopupNative.creationConfirmed()&&nativeRetiringViews.isEmpty()&&networkRetiring()==0&&downloadRetiring()==0;
        String phase=!owner.current()?"owner_unavailable":page==null||page.phase<2?"starting":page.downloadOnly?"download_only":
            views.epoch(key,entry.view)!=page.epoch?"document_changed":popupPending?"popup_pending":page.restoring?"restoring":pending?"busy":ready?"ready":page.suspended?"suspended":"document_unavailable";
        return new JSONObject().put("adapter","android-native-frames-v1").put("installed",installed).put("ready",ready).put("phase",phase)
            .put("pending",pending?1:0).put("pending_scope","document_worker").put("native_document",JSONObject.NULL)
            .put("profile_isolation",entry.profile.profileAssigned());
    }
    private void retireProtocolPopupAuthority(WebView view){
        for(NetworkEntry entry:networkViews.values())if(entry.view==view&&entry.popup!=null)entry.popup.handoff.retireAuthority();
    }
    /** Select only the original prepared document. Fresh explicit inventory may
     * rearm a UI-suspended epoch; pinned operations never create a new realm. */
    private PopupPage protocolPopupDocument(String key,WebView view)throws java.io.IOException {
        return protocolPopupDocument(key,view,false);
    }
    private PopupPage protocolPopupDocument(String key,WebView view,boolean inventory)throws java.io.IOException {
        NetworkEntry entry=networkViews.get(key);if(entry==null||entry.popup==null)return null;
        PopupNetwork network=entry.popup;PopupPage page=network.page;
        if(entry.view!=view||network.entry!=entry||page==null||page.network!=network||page.phase!=2||page.downloadOnly||!page.current())throw new java.io.IOException("cdp_popup_page_document_unavailable");
        long observed=views.epoch(key,view);
        boolean lost=inventory&&page.worldReady&&cdpWorker.documentUnavailable(page.marker,page.proof);
        if(observed!=page.epoch||page.naturalReadRecovery&&!page.restoring||lost){
            if(!inventory||!cdpEnvironmentCurrent(key,view,observed,entry.channelLease)||!chooserReady(dialogs.get(key))||views.inputBusy(key,view)||!views.inputClean(key,view))throw new java.io.IOException("cdp_popup_page_document_unavailable");
            if(page.inFlight||cdpWorker.pending(page.marker))throw new java.io.IOException("cdp_popup_page_restore_busy");
            PopupPreparation pending=protocolPopupPreparations.get(key);
            PopupPreparation continuation=lost&&pending!=null&&pending.current()&&pending.protectedOwner==network&&pending.epoch==observed&&pending.preparation.nativeTurnSettled()?pending:null;
            if(pending!=null&&!pending.preparation.settled()&&continuation==null)throw new java.io.IOException("cdp_popup_page_restore_busy");
            nativeOwnerAdmission();MdoBrowserCdpWorker.DocumentRead guard=cdpWorker.currentPopupDocument(entry.marker,network.handoff,network.sourceProof);
            if(lost)cdpViews.invalidate(key,view,observed,"popup_document_context_lost");
            page.epoch=observed;page.worldReady=false;page.suspended=false;page.restoring=true;page.naturalReadRecovery=true;page.restoringRead=guard;page.navigating=null;page.pendingPreparation=continuation;
            try{page.marker=protocolDocumentMarker(key,network.owner,network.handoff);page.proof=network.owner.document(page.marker);}
            catch(java.io.IOException|RuntimeException|Error error){page.restoreFailed(page.marker);throw error;}
            return page;
        }
        if(page.documentReady()||inventory&&page.restoring){
            if(!page.documentCurrent()||page.proof==null||!network.handoff.owns(page.proof)||!page.proof.current())throw new java.io.IOException("cdp_popup_page_document_unavailable");
            return page;
        }
        if(!inventory||!page.suspended||!cdpEnvironmentCurrent(key,view,page.epoch,entry.channelLease)||!chooserReady(dialogs.get(key))||
            views.inputBusy(key,view)||!views.inputClean(key,view))throw new java.io.IOException("cdp_popup_page_document_unavailable");
        if(cdpWorker.pending(page.marker))throw new java.io.IOException("cdp_popup_page_restore_busy");
        PopupPreparation preparing=protocolPopupPreparations.get(key);
        if(preparing!=null&&!preparing.preparation.settled()&&!(page.continuingNativePreparation(preparing)&&preparing.preparation.nativeTurnSettled()))throw new java.io.IOException("cdp_popup_page_restore_busy");
        nativeOwnerAdmission();page.suspended=false;page.restoring=true;page.worldReady=false;
        try{
            page.marker=protocolDocumentMarker(key,network.owner,network.handoff);page.proof=network.owner.document(page.marker);
        }catch(java.io.IOException|RuntimeException|Error error){page.restoreFailed(page.marker);throw error;}
        return page;
    }
    private static final class PopupNetworkAttach implements MdoBrowserCdpWorker.Operation {
        private final MdoBrowserCdpDownloads.Connection original;
        PopupNetworkAttach(MdoBrowserCdpDownloads.Connection original){this.original=original;}
        public byte[] run(MdoBrowserCdpBinding binding,MdoBrowserCdpWorker.Deadline budget)throws java.io.IOException {binding.attachDownloads(original);return new byte[]{1};}
    }
    private static final class PopupNetworkForward implements MdoBrowserCdpWorker.Completion {
        private final java.lang.ref.WeakReference<PopupNetwork> owner;
        PopupNetworkForward(PopupNetwork owner){this.owner=new java.lang.ref.WeakReference<>(owner);}
        public void done(MdoBrowserCdpWorker.Result result){PopupNetwork value=owner.get();if(value!=null)value.completed(result);}
    }
    /** UI-owned association with the real physical network registry. The worker
     * retains only a weak completion and the original data-only download link.
     * Source bootstrap never reloads/resumes the protected native popup. */
    private final class PopupNetwork implements AutoCloseable {
        final NetworkEntry entry;final MdoBrowserCdpPopupNative.Promotion owner;final MdoBrowserCdpPopupBroker.Handoff handoff;
        MdoBrowserCdpPopupNative.Source sourceProof;
        final long deadline;ProtocolPopupNetworkReady ready;MdoBrowserCdpPopupPreparation.Failure failure;
        int phase;boolean disposing,downloadsClosed,human;PopupPage page;
        PopupNetwork(NetworkEntry entry,MdoBrowserCdpPopupNative.Promotion owner,MdoBrowserCdpPopupBroker.Handoff handoff,long deadline,
                ProtocolPopupNetworkReady ready,MdoBrowserCdpPopupPreparation.Failure failure){
            this.entry=entry;this.owner=owner;this.handoff=handoff;this.deadline=deadline;this.ready=ready;this.failure=failure;
        }
        boolean current(){return phase<2&&networkCurrent(entry)&&entry.live.get()&&owner.current()&&handoff.current()&&handoff.owns(owner);}
        int remaining()throws java.io.IOException {
            if(!current())throw new java.io.IOException("cdp_popup_network_owner_changed");
            long left=deadline-android.os.SystemClock.elapsedRealtime();if(left<100)throw new java.io.IOException("cdp_popup_network_timeout");return (int)Math.min(3000,left);
        }
        private String reason(Throwable error){String code=error.getMessage();return code!=null&&code.matches("(?:cdp|network)_[a-z_]{1,80}")?code:"cdp_popup_network_failed";}
        void start(){
            try{
                int timeout=remaining();WebView exact=entry.view;
                entry.adapter=MdoBrowserCdpView.network(exact,v->v==exact&&current(),v->v==exact&&current()&&phase==1&&entry.ready,entry.quota);
                entry.marker=entry.adapter.marker();sourceProof=owner.source(entry.marker);
                entry.downloads=newNetworkDownloads();entry.downloadConnection=entry.downloads.connection();
                final MdoBrowserCdpDownloads.Connection original=entry.downloadConnection;
                schedule();
                cdpWorker.submitSource(entry.marker,handoff,sourceProof,Math.min(timeout,remaining()),new PopupNetworkAttach(original),new PopupNetworkForward(this));
            }catch(java.io.IOException|RuntimeException|Error error){retire(reason(error));}
        }
        void completed(MdoBrowserCdpWorker.Result result){
            if(phase!=0)return;
            if(result.code!=null){retire(result.code);return;}
            try{
                remaining();if(result.bytes==null||result.bytes.length!=1||result.bytes[0]!=1)throw new java.io.IOException("cdp_popup_network_result_invalid");
                phase=1;entry.starting=false;entry.ready=true;
                ProtocolPopupNetworkReady callback=ready;ready=null;
                if(callback==null)throw new java.io.IOException("cdp_popup_network_callback_failed");
                try{callback.ready(this);}catch(java.io.IOException|RuntimeException|Error error){retire("cdp_popup_network_callback_failed");return;}
                failure=null; // an accepted page must not retain its opener transaction
            }catch(java.io.IOException|RuntimeException|Error error){retire(reason(error));}
        }
        void schedule(){networkHandler.removeCallbacks(entry.watch);if(!networkHandler.postDelayed(entry.watch,100))throw new IllegalStateException("network_scheduler_unavailable");}
        /** Internal protected-page coordinator. The public route is still
         * gated until same-view return and descendant retirement qualify. */
        void takeControl()throws java.io.IOException {
            if(phase!=1||page==null||page.phase!=2||!current())throw new java.io.IOException("cdp_popup_human_owner_invalid");
            try{if(owner.view()!=entry.view)throw new IllegalStateException("popup_child_owner_lost");}
            catch(RuntimeException error){throw new java.io.IOException("cdp_popup_human_owner_invalid");}
            // Quiesce before revoking the source, preserving user cancellation
            // and original completed bytes rather than connection-loss text.
            entry.downloads.quiesce();handoff.takeControl(owner);human=true;
            // The native outcome remains retained, but its exact UI loading
            // reservation must not outlive a committed human transfer.
            nativeNavigationRetire(entry);cancelNavigationRequest(entry.key,entry.view);
            retire("network_user_takeover");handoff.detachForHuman(owner);
        }
        /** Historical retirement permits only a fresh same-view network
         * proof. It never revives this popup's document or native authority. */
        boolean returnable(){return human&&phase==3&&entry.retired&&entry.protocolDrained&&!entry.destroyed&&owner.humanTransferred()&&
            owner.ended()&&!owner.failed()&&!owner.nativeDestroyed()&&handoff.humanDetached()&&views.inputClean(entry.key,entry.view);}
        void retire(String code){
            if(phase>=2)return;
            boolean starting=phase==0||page!=null&&page.phase<2;
            MdoBrowserCdpSession.ControlResult original=handoff.flowFailure();if(starting&&original!=null&&!original.ok&&original.code!=null)code=original.code;
            else if(starting&&handoff.detached())code="cdp_session_detached";
            phase=2;entry.retired=true;entry.ready=false;entry.starting=false;entry.draining=false;entry.code=code;entry.retiredAt=android.os.SystemClock.elapsedRealtime();
            ready=null;disposing=true;networkHandler.removeCallbacks(entry.watch);
            try{
                // Revoke the original handoff first: otherwise marker/source
                // invalidation could be mistaken for an unexplained owner loss.
                try{handoff.close();}catch(RuntimeException|Error error){networkCleanupFailed=true;}sourceProof=null;
                try{if(page!=null)page.retired(code);}catch(RuntimeException|Error error){networkCleanupFailed=true;}
                entry.live.set(false);
                try{if(entry.downloads!=null)entry.downloads.quiesce();}catch(RuntimeException|Error error){networkCleanupFailed=true;}
                try{if(entry.marker!=null){entry.marker.close();cdpWorker.cancel(entry.marker.lease());}}catch(RuntimeException|Error error){networkCleanupFailed=true;}
                try{if(entry.downloadConnection!=null)entry.downloadConnection.disconnect();}catch(RuntimeException|Error error){networkCleanupFailed=true;}
            }finally{disposing=false;}
            MdoBrowserCdpPopupPreparation.Failure callback=failure;failure=null;
            if(callback!=null)try{callback.failed(code,true);}catch(RuntimeException|Error ignored){/* one outcome, never replay native creation */}
            watch();
        }
        void destroyed(){entry.destroyed=true;entry.quota.close();if(phase==3&&human){phase=2;entry.retiredAt=android.os.SystemClock.elapsedRealtime();}
            if(phase<2)retire("cdp_popup_network_native_closed");else if(!disposing)watch();}
        void watch(){
            if(phase==3||networkViews.get(entry.key)!=entry||disposing)return;
            if(phase<2){
                try{if(phase==0)remaining();else if(!current())throw new java.io.IOException("cdp_popup_network_owner_changed");schedule();}
                catch(java.io.IOException|RuntimeException|Error error){retire(reason(error));}return;
            }
            boolean nativeClean=entry.destroyed&&owner.nativeDestroyed()&&!owner.failed();
            // A real later physical close releases human-owned cache files
            // independently of an uncertain shared protocol cleanup receipt.
            if(human&&nativeClean)try{closeDownloads();}catch(RuntimeException|Error error){networkCleanupFailed=true;}
            MdoBrowserCdpSession.ControlResult transfer=human?handoff.humanDetachResult():null;if(transfer!=null&&!transfer.ok)entry.code=transfer.code;
            boolean sourceClean=entry.downloads==null||entry.downloads.sourcesClean();
            boolean humanClean=human&&owner.humanTransferred()&&owner.ended()&&!owner.failed()&&!entry.destroyed&&!owner.nativeDestroyed()&&
                views.owns(entry.key,entry.view)&&documents.get(entry.key)==entry.profile&&handoff.humanDetached();
            entry.protocolDrained=handoff.detached()&&(nativeClean||humanClean)&&sourceClean;
            if(entry.protocolDrained){
                try{if(entry.downloadConnection!=null)entry.downloadConnection.disconnect(true);if(nativeClean)closeDownloads();}
                catch(RuntimeException|Error error){networkCleanupFailed=true;}
                if(humanClean){phase=3;networkHandler.removeCallbacks(entry.watch);return;}
                if(entry.downloads==null||entry.downloads.retiringFiles()==0){phase=3;networkHandler.removeCallbacks(entry.watch);networkViews.remove(entry.key,entry);return;}
            }
            long age=android.os.SystemClock.elapsedRealtime()-entry.retiredAt;
            if(age<0||age>=5000){entry.cleanupObservationExpired=true;networkCleanupFailed=true;try{if(!human||entry.destroyed)closeDownloads();}catch(RuntimeException|Error ignored){}return;}
            try{schedule();}catch(RuntimeException|Error error){entry.cleanupObservationExpired=true;/* the retained retiring row fences creation until recovery observes its receipts */}
        }
        private void closeDownloads(){if(!downloadsClosed&&entry.downloads!=null){downloadsClosed=true;entry.downloads.close();}}
        @Override public void close(){retire("cdp_popup_network_cancelled");}
    }
    private PopupNetwork prepareProtocolPopupNetwork(String key,MdoBrowserCdpPopupNative.Promotion owner,MdoBrowserCdpPopupBroker.Handoff handoff,long deadline,
            ProtocolPopupNetworkReady ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();if(owner==null||handoff==null||!handoff.current()||!handoff.owns(owner)||!owner.current()||ready==null||failure==null)throw new java.io.IOException("cdp_popup_network_arguments_invalid");
        long left=deadline-android.os.SystemClock.elapsedRealtime();if(left<100||left>9000)throw new java.io.IOException("cdp_popup_network_timeout");
        WebView view=owner.view();MdoBrowserDocument profile=documents.get(key);MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        if(!views.owns(key,view)||profile==null||!profile.profileAssigned()||lease==null||!channel.active(lease,host.getUrl())||manual.contains(key)||pageClosed.contains(key))throw new java.io.IOException("cdp_popup_network_owner_changed");
        if(networkViews.containsKey(key)||networkViews.size()>=8||!networkClean())throw new java.io.IOException("cdp_popup_network_busy");
        for(NetworkEntry existing:networkViews.values())if(existing.view==view)throw new java.io.IOException("cdp_popup_network_busy");
        NetworkEntry entry=new NetworkEntry(key,view,profile,lease,null,null);entry.warming=false;entry.starting=true;entry.protocolDrained=false;
        PopupNetwork result=new PopupNetwork(entry,owner,handoff,deadline,ready,failure);entry.popup=result;networkViews.put(key,entry);result.start();return result;
    }
    private void resumePopupNetworks(){for(NetworkEntry entry:new ArrayList<>(networkViews.values()))if(entry.popup!=null)entry.popup.watch();}
    private final Map<String,PopupHandoff> protocolPopupHandoffs=new HashMap<>();
    /** One original transaction through native adoption, source/world readiness
     * and optional UI publication. No phase renews its budget or replays a URL. */
    private final class PopupHandoff implements AutoCloseable {
        final String key,opener;final PopupPreparation parent;final MdoBrowserChannel<WebMessagePort>.Lease lease;
        final MdoBrowserPopups.Snapshot pending;
        final long deadline;final ProtocolPopupReady ready;final MdoBrowserCdpPopupPreparation.Failure failure;
        MdoBrowserCdpPopupNative source;MdoBrowserCdpPopupNative.Created created;WebView view;MdoBrowserDocument profile;
        MdoBrowserCdpView adapter;MdoBrowserCdpPopupNative.Child proof;
        MdoBrowserCdpPopupNative.Promotion promoted;MdoBrowserCdpPopupBroker.Handoff handoff;
        ProtocolPopupNetworkReady networkReady;PopupNetwork network;ProtocolPopupPageReady pageReady;PopupPage page;
        int phase;boolean ended,possible;
        PopupHandoff(String key,String opener,PopupPreparation parent,MdoBrowserPopups.Snapshot pending,int timeout,ProtocolPopupReady ready,MdoBrowserCdpPopupPreparation.Failure failure){
            this.key=key;this.opener=opener;this.parent=parent;this.pending=pending;lease=channel.current();deadline=android.os.SystemClock.elapsedRealtime()+timeout;this.ready=ready;this.failure=failure;
        }
        boolean current(){
            if(ended||protocolPopupHandoffs.get(key)!=this||stopped||browserPaused||channel.current()!=lease||!channel.active(lease,host.getUrl()))return false;
            if(view!=null&&(!views.owns(key,view)||profile==null||documents.get(key)!=profile||!profile.profileAssigned()||manual.contains(key)||pageClosed.contains(key)))return false;
            return promoted!=null?promoted.current()&&handoff!=null&&handoff.current():protocolPopupPreparations.get(opener)==parent&&parent.current();
        }
        int remaining()throws java.io.IOException {
            if(!current())throw new java.io.IOException("cdp_popup_handoff_owner_changed");
            long left=deadline-android.os.SystemClock.elapsedRealtime();if(!current())throw new java.io.IOException("cdp_popup_handoff_owner_changed");
            if(left<1)throw new java.io.IOException("cdp_popup_handoff_timed_out");return (int)Math.min(3000,left);
        }
        void start(){
            try{constructPreparedProtocolPopup(opener,parent.view,pending.id,key,remaining(),this::constructing,this::constructed,this::fail);}
            catch(java.io.IOException|RuntimeException|Error error){fail(reason(error),false);}
        }
        void constructing(MdoBrowserCdpPopupNative original,MdoBrowserCdpPopupWorker.Allocation allocation)throws java.io.IOException {
            remaining();if(phase!=0)throw new java.io.IOException("cdp_popup_handoff_replayed");possible=true;
        }
        void constructed(MdoBrowserCdpPopupNative original,MdoBrowserCdpPopupNative.Created child)throws java.io.IOException {
            if(ended)return;possible=true;if(phase!=0)throw new java.io.IOException("cdp_popup_handoff_replayed");
            source=original;created=child;view=child.view();profile=documents.get(key);remaining();phase=1;
            adoptPreparedProtocolPopup(opener,parent.view,child,remaining(),this::consumed,this::fail);
        }
        void consumed(MdoBrowserCdpPopupNative original,MdoBrowserCdpPopupNative.Created child,MdoBrowserPopups.Adoption receipt)throws java.io.IOException {
            if(ended)return;if(phase!=1||source!=original||created!=child)throw new java.io.IOException("cdp_popup_handoff_replayed");remaining();phase=2;
            adapter=MdoBrowserCdpView.network(view,exact->exact==view&&phase==2&&current(),new MdoBrowserCdpMarker.DocumentQuota());
            proof=source.child(receipt,created,adapter);provePreparedProtocolPopup(opener,parent.view,proof,remaining(),this::proved,this::fail);
        }
        void proved(MdoBrowserCdpPopupNative.Child original)throws java.io.IOException {
            if(ended)return;if(phase!=2||proof!=original)throw new java.io.IOException("cdp_popup_handoff_replayed");remaining();
            handoff=proof.handoff();promoted=handoffProtocolPopup(key,source,created,proof);remaining();phase=3;
            if(!disposeProof())throw new java.io.IOException("cdp_popup_handoff_cleanup_unconfirmed");
            if(networkReady!=null||pageReady!=null){
                try{network=prepareProtocolPopupNetwork(key,promoted,handoff,deadline,this::networked,this::fail);}
                catch(java.io.IOException|RuntimeException|Error error){fail(reason(error),true);}return;
            }
            try{ready.ready(promoted,handoff);}
            catch(java.io.IOException|RuntimeException|Error error){fail("cdp_popup_handoff_callback_failed",true);return;}
            if(ended)return;
            ended=true;phase=4;protocolPopupHandoffs.remove(key,this);
        }
        void networked(PopupNetwork original)throws java.io.IOException {
            if(ended)return;remaining();if(phase!=3||network!=null&&network!=original||original.owner!=promoted||original.handoff!=handoff||!original.current())throw new java.io.IOException("cdp_popup_network_owner_changed");
            if(pageReady!=null){network=original;phase=4;page=prepareProtocolPopupPage(original,this::pageReadied,this::fail);return;}
            network=original;ProtocolPopupNetworkReady callback=networkReady;networkReady=null;
            try{callback.ready(original);}catch(java.io.IOException|RuntimeException|Error error){fail("cdp_popup_handoff_callback_failed",true);return;}
            if(ended)return;
            ended=true;phase=4;protocolPopupHandoffs.remove(key,this);
        }
        void pageReadied(PopupPage original)throws java.io.IOException {
            if(ended)return;remaining();if(phase!=4||page!=null&&page!=original||original.network!=network||!original.current())throw new java.io.IOException("cdp_popup_page_owner_changed");
            page=original;ProtocolPopupPageReady callback=pageReady;pageReady=null;
            try{callback.ready(original);}catch(java.io.IOException|RuntimeException|Error error){fail("cdp_popup_handoff_callback_failed",true);return;}
            if(ended)return;ended=true;phase=5;protocolPopupHandoffs.remove(key,this);
        }
        private String reason(Throwable error){String code=error.getMessage();return code!=null&&code.matches("cdp_[a-z_]{1,80}")?code:"cdp_popup_handoff_failed";}
        private boolean disposeProof(){
            boolean clean=true;if(proof!=null){try{proof.close();}catch(RuntimeException|Error error){clean=false;}proof=null;}
            if(adapter!=null){try{adapter.close();}catch(RuntimeException|Error error){clean=false;}adapter=null;}return clean;
        }
        /** A pending create's close must also observe its original source and
         * independent child receipts; removing its map row is not cleanup. */
        boolean cleanupConfirmed(){
            if(!ended||!parent.preparation.settled()||nativeViewCleanupFailed||networkCleanupFailed||!MdoBrowserCdpPopupNative.creationConfirmed())return false;
            if(network!=null)return network.phase==3;
            if(promoted!=null)return promoted.nativeDestroyed()&&!promoted.failed()&&handoff!=null&&handoff.detached();
            return view==null||views.nativeDestroyed(view);
        }
        void fail(String code,boolean effect){
            if(ended)return;ended=true;phase=5;possible|=effect;protocolPopupHandoffs.remove(key,this);boolean clean=true;
            networkReady=null;pageReady=null;if(network!=null)try{network.close();}catch(RuntimeException|Error error){clean=false;}
            if(handoff!=null)try{handoff.close();}catch(RuntimeException|Error error){clean=false;}
            if(!disposeProof())clean=false;
            try{parent.preparation.close(code);}catch(RuntimeException|Error error){clean=false;}
            try{failure.failed(clean?code:"cdp_popup_handoff_cleanup_unconfirmed",possible);}catch(RuntimeException|Error ignored){/* one outcome; never replay creation */}
        }
        @Override public void close(){fail("cdp_popup_handoff_cancelled",possible);}
    }
    private PopupHandoff handoffPreparedProtocolPopup(String opener,WebView exact,String pending,String key,int timeout,
            ProtocolPopupReady ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        return handoffPreparedProtocolPopup(opener,exact,pending,key,timeout,ready,null,null,failure);
    }
    private PopupHandoff handoffPreparedProtocolPopupNetwork(String opener,WebView exact,String pending,String key,int timeout,
            ProtocolPopupNetworkReady ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        return handoffPreparedProtocolPopup(opener,exact,pending,key,timeout,null,ready,null,failure);
    }
    private PopupHandoff handoffPreparedProtocolPopupPage(String opener,WebView exact,String pending,String key,int timeout,
            ProtocolPopupPageReady ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        return handoffPreparedProtocolPopup(opener,exact,pending,key,timeout,null,null,ready,failure);
    }
    private PopupHandoff handoffPreparedProtocolPopup(String opener,WebView exact,String pending,String key,int timeout,
            ProtocolPopupReady ready,ProtocolPopupNetworkReady networkReady,ProtocolPopupPageReady pageReady,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();
        if((ready==null?0:1)+(networkReady==null?0:1)+(pageReady==null?0:1)!=1||failure==null||timeout<1||timeout>9000||pending==null||pending.isEmpty())throw new java.io.IOException("cdp_popup_handoff_arguments_invalid");
        PopupPreparation parent=protocolPopupPreparations.get(opener);
        if(parent==null||parent.view!=exact||!parent.current()||!parent.preparation.ready())throw new java.io.IOException("cdp_popup_preparation_required");
        MdoBrowserPopups<WebView> core=windows.get(opener);MdoBrowserPopups.Snapshot request=core==null?null:core.snapshot();
        if(request==null||!request.id.equals(pending))throw new java.io.IOException("cdp_popup_native_request_changed");
        if(protocolPopupHandoffs.containsKey(key))throw new java.io.IOException("cdp_popup_handoff_busy");
        for(PopupHandoff original:protocolPopupHandoffs.values())if(original.parent==parent&&original.pending==request)throw new java.io.IOException("cdp_popup_handoff_busy");
        if(protocolPopupHandoffs.size()>=7)throw new java.io.IOException("cdp_popup_handoff_limit");views.checkNew(key);
        PopupHandoff result=new PopupHandoff(key,opener,parent,request,timeout,ready,failure);result.networkReady=networkReady;result.pageReady=pageReady;protocolPopupHandoffs.put(key,result);result.start();return result;
    }
    interface ProtocolPopupCreated {void ready(MdoBrowserCdpPopupNative source,MdoBrowserCdpPopupNative.Created child)throws java.io.IOException;}
    /** Product creation uses only the original pending callback and guarded
     * pre-birth capture. No CDP target ID or supplied WebView enters this API. */
    private void constructPreparedProtocolPopup(String opener,WebView exact,String pendingId,String key,int timeout,
            ProtocolPopupCreated ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        constructPreparedProtocolPopup(opener,exact,pendingId,key,timeout,(source,allocation)->{},ready,failure);
    }
    private void constructPreparedProtocolPopup(String opener,WebView exact,String pendingId,String key,int timeout,
            MdoBrowserCdpPopupPreparation.Allocated constructing,ProtocolPopupCreated ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();PopupPreparation original=protocolPopupPreparations.get(opener);
        if(original==null||original.view!=exact||!original.current()||!original.preparation.ready())throw new java.io.IOException("cdp_popup_preparation_required");
        if(ready==null||constructing==null)throw new java.io.IOException("cdp_popup_delivery_arguments_invalid");
        original.preparation.allocate(pendingId,timeout,(source,allocation)->{
            if(!original.current())throw new java.io.IOException("cdp_popup_preparation_owner_changed");
            constructing.ready(source,allocation);
            if(!original.current())throw new java.io.IOException("cdp_popup_preparation_owner_changed");
            ready.ready(source,constructProtocolPopup(source,allocation,key));
        },failure);
    }
    /** Accept proof only through the original worker's UI delivery, never by
     * polling a completed marker or rebinding a later child/controller. */
    private void provePreparedProtocolPopup(String opener,WebView exact,MdoBrowserCdpPopupNative.Child child,int timeout,
            MdoBrowserCdpPopupPreparation.Proven ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();PopupPreparation original=protocolPopupPreparations.get(opener);
        if(original==null||original.view!=exact||!original.current()||!original.preparation.ready())throw new java.io.IOException("cdp_popup_preparation_required");
        if(ready==null)throw new java.io.IOException("cdp_popup_delivery_arguments_invalid");
        original.preparation.prove(child,timeout,proof->{
            if(!original.current())throw new java.io.IOException("cdp_popup_preparation_owner_changed");ready.ready(proof);
        },failure);
    }
    /** Native adoption is sent once; only its original consumption notification
     * may advance the configured child into proof and independent ownership. */
    private void adoptPreparedProtocolPopup(String opener,WebView exact,MdoBrowserCdpPopupNative.Created child,int timeout,
            MdoBrowserCdpPopupPreparation.Consumed ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();PopupPreparation original=protocolPopupPreparations.get(opener);
        if(original==null||original.view!=exact||!original.current()||!original.preparation.ready())throw new java.io.IOException("cdp_popup_preparation_required");
        if(ready==null)throw new java.io.IOException("cdp_popup_delivery_arguments_invalid");
        original.preparation.adopt(child,timeout,(source,created,receipt)->{
            if(!original.current())throw new java.io.IOException("cdp_popup_preparation_owner_changed");ready.ready(source,created,receipt);
        },failure);
    }
    private JSONObject popupPreparationState(String key)throws JSONException {
        PopupPreparation original=protocolPopupPreparations.get(key);MdoBrowserCdpPopupPreparation value=original==null?null:original.preparation;
        boolean ready=value!=null&&value.ready();return new JSONObject().put("phase",value==null?"none":value.phase()).put("ready",ready)
            .put("cleanup_confirmed",value==null||value.settled()).put("error",value==null||value.failure()==null?JSONObject.NULL:value.failure());
    }
    private boolean protocolPopupCreationBlocked(){
        boolean blocked=false;for(PopupPreparation original:new ArrayList<>(protocolPopupPreparations.values())){
            if(original.preparation.settled())protocolPopupPreparations.remove(original.key,original);else blocked=true;
        }return blocked||MdoBrowserCdpPopupBroker.creationBlocked();
    }
    private void requireProtocolPopupPreparation(String key,WebView view)throws java.io.IOException {
        PopupPreparation original=protocolPopupPreparations.get(key);
        if(original==null)throw new java.io.IOException("cdp_popup_preparation_required");
        if(original.view!=view||!original.current()||!original.preparation.ready()){original.preparation.close("cdp_popup_preparation_owner_changed");throw new java.io.IOException("cdp_popup_preparation_required");}
    }
    private void executePopupPreparation(String key,String id,WebView view,JSONObject request)throws JSONException {
        final MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();final long epoch=views.epoch(key,view);
        if(!request.optBoolean("agent")||!(request.opt("navigation") instanceof Number)||request.getDouble("navigation")!=epoch||epoch<=0)throw new IllegalArgumentException("popup_preparation_scope_invalid");
        final double requested=request.has("timeout_ms")?request.getDouble("timeout_ms"):3000;
        if(request.has("timeout_ms")&&!(request.opt("timeout_ms") instanceof Number)||!Double.isFinite(requested)||requested<1||requested>3000||requested!=Math.floor(requested))throw new IllegalArgumentException("popup_preparation_scope_invalid");
        final java.util.concurrent.atomic.AtomicBoolean replied=new java.util.concurrent.atomic.AtomicBoolean();
        prepareProtocolPopupAction(key,view,(int)requested,()->{
            if(!replied.compareAndSet(false,true))return;
            try{if(channel.current()!=original||!views.owns(key,view)||views.epoch(key,view)!=epoch)throw new IllegalStateException("popup_preparation_scope_changed");
                reply(original,id,new JSONObject().put("ok",true).put("ready",true).put("navigation",epoch),null);}
            catch(JSONException|RuntimeException error){reply(original,id,null,"popup_preparation_scope_changed");}
        },(code,effect)->{
            if(!replied.compareAndSet(false,true))return;
            try{reply(original,id,new JSONObject().put("ok",false).put("ready",false).put("error",new JSONObject().put("code",code).put("effect_may_have_occurred",false)),null);}
            catch(JSONException error){reply(original,id,null,"popup_preparation_failed");}
        });
    }
    private void prepareProtocolPopupAction(String key,WebView view,Runnable effect,MdoBrowserCdpPopupPreparation.Failure failure){
        prepareProtocolPopupAction(key,view,3000,effect,failure);
    }
    private void prepareProtocolPopupAction(String key,WebView view,int timeout,Runnable effect,MdoBrowserCdpPopupPreparation.Failure failure){
        try{
            PopupPreparation old=protocolPopupPreparations.get(key);
            if(old!=null&&!old.current())old.preparation.close("cdp_popup_preparation_owner_changed");
            if(old!=null&&old.preparation.settled()){protocolPopupPreparations.remove(key,old);old=null;}
            if(old==null){
                if(timeout<100)throw new java.io.IOException("cdp_popup_preparation_timeout");
                NetworkEntry network=networkViews.get(key);MdoBrowserPopups<WebView> core=windows.get(key);
                if(network==null||network.view!=view||network.adapter==null||!network.ready||network.retired||network.draining||core==null||!networkCurrent(network)||manual.contains(key)||pageClosed.contains(key))throw new java.io.IOException("cdp_popup_network_owner_unavailable");
                PopupNetwork protectedOwner=network.popup;
                if(protectedOwner!=null){
                    nativeOwnerAdmission();PopupPage page=protocolPopupDocument(key,view);
                    if(page==null||page.network!=protectedOwner||protectedOwner.sourceProof==null||!protectedOwner.handoff.owns(protectedOwner.sourceProof)||!protectedOwner.sourceProof.current()||
                        !cdpEnvironmentCurrent(key,view,views.epoch(key,view),channel.current())||!chooserReady(dialogs.get(key)))throw new java.io.IOException("cdp_popup_attached_opener_unavailable");
                }
                if(protocolPopupPreparations.size()>=8)throw new java.io.IOException("cdp_popup_preparation_limit");
                PopupPreparation added=new PopupPreparation(key,view,network);
                MdoBrowserCdpPopupNative source=new MdoBrowserCdpPopupNative(network.adapter,view,core);
                added.preparation=new MdoBrowserCdpPopupPreparation(source,added::current,added::lost);protocolPopupPreparations.put(key,added);
                try{if(protectedOwner==null)added.preparation.start(cdpWorker,timeout);else added.preparation.startAttached(cdpWorker,timeout,protectedOwner.handoff,protectedOwner.sourceProof);}
                catch(java.io.IOException|RuntimeException|Error error){if(added.preparation.settled())protocolPopupPreparations.remove(key,added);throw error;}old=added;
            }
            if(old.view!=view)throw new java.io.IOException("cdp_popup_preparation_owner_changed");old.preparation.await(effect,failure);
        }catch(java.io.IOException error){failure.failed(error.getMessage(),false);}
        catch(RuntimeException|Error error){failure.failed("cdp_popup_preparation_failed",false);}
    }
    private void retireProtocolPopupPreparation(String key,WebView view,String reason){
        PopupPreparation original=protocolPopupPreparations.get(key);if(original!=null&&original.view==view){original.preparation.close(reason);if(original.preparation.settled())protocolPopupPreparations.remove(key,original);}
    }
