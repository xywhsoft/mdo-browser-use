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
    interface ProtocolPopupCreated {void ready(MdoBrowserCdpPopupNative source,MdoBrowserCdpPopupNative.Created child)throws java.io.IOException;}
    /** Product creation uses only the original pending callback and guarded
     * pre-birth capture. No CDP target ID or supplied WebView enters this API. */
    private void constructPreparedProtocolPopup(String opener,WebView exact,String pendingId,String key,int timeout,
            ProtocolPopupCreated ready,MdoBrowserCdpPopupPreparation.Failure failure)throws java.io.IOException {
        nativeOwnerAdmission();PopupPreparation original=protocolPopupPreparations.get(opener);
        if(original==null||original.view!=exact||!original.current()||!original.preparation.ready())throw new java.io.IOException("cdp_popup_preparation_required");
        if(ready==null)throw new java.io.IOException("cdp_popup_delivery_arguments_invalid");
        original.preparation.allocate(pendingId,timeout,(source,allocation)->{
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
        }return blocked;
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
