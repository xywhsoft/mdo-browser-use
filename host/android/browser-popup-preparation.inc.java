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
            try{ready.ready(promoted,handoff);}
            catch(java.io.IOException|RuntimeException|Error error){fail("cdp_popup_handoff_callback_failed",true);return;}
            ended=true;phase=4;protocolPopupHandoffs.remove(key,this);
        }
        private String reason(Throwable error){String code=error.getMessage();return code!=null&&code.matches("cdp_[a-z_]{1,80}")?code:"cdp_popup_handoff_failed";}
        private boolean disposeProof(){
            boolean clean=true;if(proof!=null){try{proof.close();}catch(RuntimeException|Error error){clean=false;}proof=null;}
            if(adapter!=null){try{adapter.close();}catch(RuntimeException|Error error){clean=false;}adapter=null;}return clean;
        }
        void fail(String code,boolean effect){
            if(ended)return;ended=true;phase=5;possible|=effect;protocolPopupHandoffs.remove(key,this);boolean clean=true;
            if(handoff!=null)try{handoff.close();}catch(RuntimeException|Error error){clean=false;}
            if(!disposeProof())clean=false;
            try{parent.preparation.close(code);}catch(RuntimeException|Error error){clean=false;}
            try{failure.failed(clean?code:"cdp_popup_handoff_cleanup_unconfirmed",possible);}catch(RuntimeException|Error ignored){/* one outcome; never replay creation */}
        }
        @Override public void close(){fail("cdp_popup_handoff_cancelled",possible);}
    }
    private PopupHandoff handoffPreparedProtocolPopup(String opener,WebView exact,String pending,String key,int timeout,
            ProtocolPopupReady ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();
        if(ready==null||failure==null||timeout<1||timeout>9000||pending==null||pending.isEmpty())throw new java.io.IOException("cdp_popup_handoff_arguments_invalid");
        PopupPreparation parent=protocolPopupPreparations.get(opener);
        if(parent==null||parent.view!=exact||!parent.current()||!parent.preparation.ready())throw new java.io.IOException("cdp_popup_preparation_required");
        MdoBrowserPopups<WebView> core=windows.get(opener);MdoBrowserPopups.Snapshot request=core==null?null:core.snapshot();
        if(request==null||!request.id.equals(pending))throw new java.io.IOException("cdp_popup_native_request_changed");
        if(protocolPopupHandoffs.containsKey(key))throw new java.io.IOException("cdp_popup_handoff_busy");
        for(PopupHandoff original:protocolPopupHandoffs.values())if(original.parent==parent&&original.pending==request)throw new java.io.IOException("cdp_popup_handoff_busy");
        if(protocolPopupHandoffs.size()>=7)throw new java.io.IOException("cdp_popup_handoff_limit");views.checkNew(key);
        PopupHandoff result=new PopupHandoff(key,opener,parent,request,timeout,ready,failure);protocolPopupHandoffs.put(key,result);result.start();return result;
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
