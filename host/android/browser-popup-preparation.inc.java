    // The UI keeps this bounded scope; worker callbacks retain it weakly. A new
    // request never replaces an unretired source under a reused browser key.
    private final class PopupPreparation {
        final String key;final WebView view;final long epoch;final NetworkEntry network;
        MdoBrowserCdpPopupPreparation preparation;
        PopupPreparation(String key,WebView view,NetworkEntry network){this.key=key;this.view=view;this.network=network;epoch=views.epoch(key,view);}
        boolean current(){return protocolPopupPreparations.get(key)==this&&epoch>0&&views.owns(key,view)&&views.epoch(key,view)==epoch&&networkViews.get(key)==network&&
            network.ready&&!network.retired&&!network.draining&&networkCurrent(network)&&!manual.contains(key)&&!pageClosed.contains(key);}
        void lost(String reason){if(protocolPopupPreparations.get(key)!=this||!views.owns(key,view))return;
            views.input(key,view).invalidate("input_cancelled");cdpViews.invalidate(key,view,views.epoch(key,view),"input_cancelled");}
    }
    private final Map<String,PopupPreparation> protocolPopupPreparations=new HashMap<>();
    interface ProtocolPopupReady {void ready(MdoBrowserCdpPopupNative.Promotion owner,MdoBrowserCdpPopupBroker.Handoff handoff)throws java.io.IOException;}
    interface ProtocolPopupNetworkReady {void ready(PopupNetwork network)throws java.io.IOException;}
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
        final long deadline;ProtocolPopupNetworkReady ready;MdoBrowserCdpPopupPreparation.Failure failure;
        int phase;boolean disposing,downloadsClosed;
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
                entry.marker=entry.adapter.marker();MdoBrowserCdpPopupNative.Source proof=owner.source(entry.marker);
                entry.downloads=newNetworkDownloads();entry.downloadConnection=entry.downloads.connection();
                final MdoBrowserCdpDownloads.Connection original=entry.downloadConnection;
                schedule();
                cdpWorker.submitSource(entry.marker,handoff,proof,Math.min(timeout,remaining()),new PopupNetworkAttach(original),new PopupNetworkForward(this));
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
        void retire(String code){
            if(phase>=2)return;
            MdoBrowserCdpSession.ControlResult original=handoff.flowFailure();if(phase==0&&original!=null&&!original.ok&&original.code!=null)code=original.code;
            else if(phase==0&&handoff.detached())code="cdp_session_detached";
            phase=2;entry.retired=true;entry.ready=false;entry.starting=false;entry.draining=false;entry.code=code;entry.retiredAt=android.os.SystemClock.elapsedRealtime();
            ready=null;disposing=true;networkHandler.removeCallbacks(entry.watch);
            try{
                // Revoke the original handoff first: otherwise marker/source
                // invalidation could be mistaken for an unexplained owner loss.
                try{handoff.close();}catch(RuntimeException|Error error){networkCleanupFailed=true;}
                entry.live.set(false);
                try{if(entry.downloads!=null)entry.downloads.quiesce();}catch(RuntimeException|Error error){networkCleanupFailed=true;}
                try{if(entry.marker!=null){entry.marker.close();cdpWorker.cancel(entry.marker.lease());}}catch(RuntimeException|Error error){networkCleanupFailed=true;}
                try{if(entry.downloadConnection!=null)entry.downloadConnection.disconnect();}catch(RuntimeException|Error error){networkCleanupFailed=true;}
            }finally{disposing=false;}
            MdoBrowserCdpPopupPreparation.Failure callback=failure;failure=null;
            if(callback!=null)try{callback.failed(code,true);}catch(RuntimeException|Error ignored){/* one outcome, never replay native creation */}
            watch();
        }
        void destroyed(){entry.destroyed=true;entry.quota.close();if(phase<2)retire("cdp_popup_network_native_closed");else if(!disposing)watch();}
        void watch(){
            if(phase==3||networkViews.get(entry.key)!=entry||disposing)return;
            if(phase<2){
                try{if(phase==0)remaining();else if(!current())throw new java.io.IOException("cdp_popup_network_owner_changed");schedule();}
                catch(java.io.IOException|RuntimeException|Error error){retire(reason(error));}return;
            }
            boolean nativeClean=entry.destroyed&&owner.nativeDestroyed()&&!owner.failed();
            boolean sourceClean=entry.downloads==null||entry.downloads.sourcesClean();
            entry.protocolDrained=handoff.detached()&&nativeClean&&sourceClean;
            if(entry.protocolDrained){
                try{if(entry.downloadConnection!=null)entry.downloadConnection.disconnect(true);closeDownloads();}
                catch(RuntimeException|Error error){networkCleanupFailed=true;}
                if(entry.downloads==null||entry.downloads.retiringFiles()==0){phase=3;networkHandler.removeCallbacks(entry.watch);networkViews.remove(entry.key,entry);return;}
            }
            long age=android.os.SystemClock.elapsedRealtime()-entry.retiredAt;
            if(age<0||age>=5000){entry.cleanupObservationExpired=true;networkCleanupFailed=true;try{closeDownloads();}catch(RuntimeException|Error ignored){}return;}
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
    /** One original product transaction, ending before network activation or
     * UI publication. No phase can renew the overall budget or replay a URL. */
    private final class PopupHandoff implements AutoCloseable {
        final String key,opener;final PopupPreparation parent;final MdoBrowserChannel<WebMessagePort>.Lease lease;
        final MdoBrowserPopups.Snapshot pending;
        final long deadline;final ProtocolPopupReady ready;final MdoBrowserCdpPopupPreparation.Failure failure;
        MdoBrowserCdpPopupNative source;MdoBrowserCdpPopupNative.Created created;WebView view;MdoBrowserDocument profile;
        MdoBrowserCdpView adapter;MdoBrowserCdpPopupNative.Child proof;
        MdoBrowserCdpPopupNative.Promotion promoted;MdoBrowserCdpPopupBroker.Handoff handoff;
        ProtocolPopupNetworkReady networkReady;PopupNetwork network;
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
            if(networkReady!=null){
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
            network=original;ProtocolPopupNetworkReady callback=networkReady;networkReady=null;
            try{callback.ready(original);}catch(java.io.IOException|RuntimeException|Error error){fail("cdp_popup_handoff_callback_failed",true);return;}
            if(ended)return;
            ended=true;phase=4;protocolPopupHandoffs.remove(key,this);
        }
        private String reason(Throwable error){String code=error.getMessage();return code!=null&&code.matches("cdp_[a-z_]{1,80}")?code:"cdp_popup_handoff_failed";}
        private boolean disposeProof(){
            boolean clean=true;if(proof!=null){try{proof.close();}catch(RuntimeException|Error error){clean=false;}proof=null;}
            if(adapter!=null){try{adapter.close();}catch(RuntimeException|Error error){clean=false;}adapter=null;}return clean;
        }
        void fail(String code,boolean effect){
            if(ended)return;ended=true;phase=5;possible|=effect;protocolPopupHandoffs.remove(key,this);boolean clean=true;
            networkReady=null;if(network!=null)try{network.close();}catch(RuntimeException|Error error){clean=false;}
            if(handoff!=null)try{handoff.close();}catch(RuntimeException|Error error){clean=false;}
            if(!disposeProof())clean=false;
            try{parent.preparation.close(code);}catch(RuntimeException|Error error){clean=false;}
            try{failure.failed(clean?code:"cdp_popup_handoff_cleanup_unconfirmed",possible);}catch(RuntimeException|Error ignored){/* one outcome; never replay creation */}
        }
        @Override public void close(){fail("cdp_popup_handoff_cancelled",possible);}
    }
    private PopupHandoff handoffPreparedProtocolPopup(String opener,WebView exact,String pending,String key,int timeout,
            ProtocolPopupReady ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        return handoffPreparedProtocolPopup(opener,exact,pending,key,timeout,ready,null,failure);
    }
    private PopupHandoff handoffPreparedProtocolPopupNetwork(String opener,WebView exact,String pending,String key,int timeout,
            ProtocolPopupNetworkReady ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        return handoffPreparedProtocolPopup(opener,exact,pending,key,timeout,null,ready,failure);
    }
    private PopupHandoff handoffPreparedProtocolPopup(String opener,WebView exact,String pending,String key,int timeout,
            ProtocolPopupReady ready,ProtocolPopupNetworkReady networkReady,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();
        if((ready==null)==(networkReady==null)||failure==null||timeout<1||timeout>9000||pending==null||pending.isEmpty())throw new java.io.IOException("cdp_popup_handoff_arguments_invalid");
        PopupPreparation parent=protocolPopupPreparations.get(opener);
        if(parent==null||parent.view!=exact||!parent.current()||!parent.preparation.ready())throw new java.io.IOException("cdp_popup_preparation_required");
        MdoBrowserPopups<WebView> core=windows.get(opener);MdoBrowserPopups.Snapshot request=core==null?null:core.snapshot();
        if(request==null||!request.id.equals(pending))throw new java.io.IOException("cdp_popup_native_request_changed");
        if(protocolPopupHandoffs.containsKey(key))throw new java.io.IOException("cdp_popup_handoff_busy");
        for(PopupHandoff original:protocolPopupHandoffs.values())if(original.parent==parent&&original.pending==request)throw new java.io.IOException("cdp_popup_handoff_busy");
        if(protocolPopupHandoffs.size()>=7)throw new java.io.IOException("cdp_popup_handoff_limit");views.checkNew(key);
        PopupHandoff result=new PopupHandoff(key,opener,parent,request,timeout,ready,failure);result.networkReady=networkReady;protocolPopupHandoffs.put(key,result);result.start();return result;
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
                if(protocolPopupPreparations.size()>=8)throw new java.io.IOException("cdp_popup_preparation_limit");
                PopupPreparation added=new PopupPreparation(key,view,network);
                MdoBrowserCdpPopupNative source=new MdoBrowserCdpPopupNative(network.adapter,view,core);
                added.preparation=new MdoBrowserCdpPopupPreparation(source,added::current,added::lost);protocolPopupPreparations.put(key,added);
                try{added.preparation.start(cdpWorker,timeout);}catch(java.io.IOException|RuntimeException|Error error){if(added.preparation.settled())protocolPopupPreparations.remove(key,added);throw error;}old=added;
            }
            if(old.view!=view)throw new java.io.IOException("cdp_popup_preparation_owner_changed");old.preparation.await(effect,failure);
        }catch(java.io.IOException error){failure.failed(error.getMessage(),false);}
        catch(RuntimeException|Error error){failure.failed("cdp_popup_preparation_failed",false);}
    }
    private void retireProtocolPopupPreparation(String key,WebView view,String reason){
        PopupPreparation original=protocolPopupPreparations.get(key);if(original!=null&&original.view==view){original.preparation.close(reason);if(original.preparation.settled())protocolPopupPreparations.remove(key,original);}
    }
