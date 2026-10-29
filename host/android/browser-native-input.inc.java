    private void nativeInputResult(MdoBrowserChannel<WebMessagePort>.Lease lease,String id,boolean focusing,MdoBrowserInput.Result result){
        try{
            JSONObject value=new JSONObject().put("accepted",result.ok).put("trusted_input",result.ok&&!focusing)
                .put("target_focused",result.ok&&focusing).put("effect_may_have_occurred",result.effectMayHaveOccurred)
                .put("cleanup_confirmed",result.cleanupConfirmed).put("delivered_steps",result.deliveredSteps);
            if(result.code!=null)value.put("error",new JSONObject().put("code",result.code));
            reply(lease,id,value,null);
        }catch(JSONException error){reply(lease,id,null,"input_result_invalid");}
    }
    private void executeNativeInput(String key,String id,WebView view,JSONObject request)throws Exception {
        final long epoch=views.epoch(key,view);
        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        final MdoBrowserInput input=views.input(key,view);
        try{
            if(!dialogReady(key,view))throw new java.io.IOException("cdp_input_dialog_pending_or_unclean");
            CaptureEntry capture=captures.get(key);
            if(capture!=null&&(capture.capture.busy()||!capture.capture.cleanupConfirmed()))throw new java.io.IOException("cdp_input_capture_busy_or_unclean");
            if(!(request.opt("navigation") instanceof Number)||request.getDouble("navigation")!=epoch||epoch==0)
                throw new java.io.IOException("cdp_input_frame_changed");
            MdoBrowserNativeInput bridge=new MdoBrowserNativeInput(view,cdpWorker,cdpMarker(key,view),()->!stopped&&views.owns(key,view)&&
                views.epoch(key,view)==epoch&&channel.current()==lease&&channel.active(lease,host.getUrl()),()->views.scale(key,view),request);
            final boolean focusing=bridge.focusing();bridge.run(input,result->nativeInputResult(lease,id,focusing,result));
        }catch(java.io.IOException|MdoBrowserInput.Failure error){String code=error.getMessage();
            nativeInputResult(lease,id,false,new MdoBrowserInput.Result(code!=null&&code.matches("[a-z_]{1,63}")?code:"input_guard_failed",false,input.cleanupConfirmed(),0));}
    }
