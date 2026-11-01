    private void nativeInputResult(MdoBrowserChannel<WebMessagePort>.Lease lease,String id,boolean focusing,MdoBrowserInput.Result result){
        try{
            JSONObject value=new JSONObject().put("accepted",result.ok).put("trusted_input",result.ok&&!focusing)
                .put("target_focused",result.ok&&focusing).put("effect_may_have_occurred",result.effectMayHaveOccurred)
                .put("cleanup_confirmed",result.cleanupConfirmed).put("delivered_steps",result.deliveredSteps);
            if(result.code!=null)value.put("error",new JSONObject().put("code",result.code));
            reply(lease,id,value,null);
        }catch(JSONException error){reply(lease,id,null,"input_result_invalid");}
    }
    private interface NativeInputCompletion {void done(boolean focusing,MdoBrowserInput.Result result);}
    private void executeNativeInput(String key,String id,WebView view,JSONObject request)throws Exception {
        final MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();final long epoch=views.epoch(key,view);
        final JSONObject frozen=new JSONObject(request.toString());
        final java.util.concurrent.atomic.AtomicBoolean replied=new java.util.concurrent.atomic.AtomicBoolean();
        final NativeInputCompletion complete=(focusing,result)->{if(replied.compareAndSet(false,true))nativeInputResult(original,id,focusing,result);};
        Runnable run=()->{try{executePreparedNativeInput(key,view,frozen,original,epoch,complete);}
            catch(Exception error){complete.done(false,new MdoBrowserInput.Result("input_guard_failed",true,views.inputClean(key,view),0));}};
        if(frozen.optBoolean("agent"))try{requireProtocolPopupPreparation(key,view);}catch(java.io.IOException error){complete.done(false,new MdoBrowserInput.Result(error.getMessage(),false,views.inputClean(key,view),0));return;}
        run.run();
    }
    private void executePreparedNativeInput(String key,WebView view,JSONObject request,MdoBrowserChannel<WebMessagePort>.Lease lease,long epoch,NativeInputCompletion complete)throws Exception {
        final MdoBrowserInput input=views.input(key,view);
        try{
            if(!views.owns(key,view)||views.epoch(key,view)!=epoch||channel.current()!=lease||!channel.active(lease,host.getUrl()))throw new java.io.IOException("cdp_input_frame_changed");
            if(!dialogReady(key,view))throw new java.io.IOException("cdp_input_dialog_pending_or_unclean");
            CaptureEntry capture=captures.get(key);
            if(capture!=null&&(capture.capture.busy()||!capture.capture.cleanupConfirmed()))throw new java.io.IOException("cdp_input_capture_busy_or_unclean");
            if(!(request.opt("navigation") instanceof Number)||request.getDouble("navigation")!=epoch||epoch==0)
                throw new java.io.IOException("cdp_input_frame_changed");
            final PopupPage popup=protocolPopupDocument(key,view);
            final MdoBrowserCdpMarker marker=popup==null?cdpMarker(key,view):popup.marker;
            MdoBrowserNativeInput bridge=new MdoBrowserNativeInput(view,cdpWorker,marker,
                ()->cdpEnvironmentCurrent(key,view,epoch,lease)&&chooserReady(dialogs.get(key))&&
                    (popup==null||popup.marker==marker&&popup.documentReady()),()->views.scale(key,view),request,
                popup==null?null:popup.network.handoff,popup==null?null:popup.proof);
            final boolean focusing=bridge.focusing();bridge.run(input,result->complete.done(focusing,result));
        }catch(java.io.IOException|MdoBrowserInput.Failure error){String code=error.getMessage();
            complete.done(false,new MdoBrowserInput.Result(code!=null&&code.matches("[a-z_]{1,63}")?code:"input_guard_failed",false,input.cleanupConfirmed(),0));}
    }
