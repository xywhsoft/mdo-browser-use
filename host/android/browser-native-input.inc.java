    private void executeNativeInput(String key,String id,WebView view,JSONObject request)throws Exception {
        if(!dialogReady(key,view))throw new IllegalStateException("dialog_pending_or_unclean");
        CaptureEntry capture=captures.get(key);
        if(capture!=null&&(capture.capture.busy()||!capture.capture.cleanupConfirmed()))throw new IllegalStateException("capture_busy_or_unclean");
        final MdoBrowserDocument document=documents.get(key);
        if(document==null)throw new UnsupportedOperationException(documentFailures.getOrDefault(key,"world_unavailable"));
        final long epoch=views.epoch(key,view);
        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        if(!(request.get("navigation") instanceof Number)||request.getDouble("navigation")!=epoch||epoch==0)
            throw new IllegalStateException("frame_changed");
        MdoBrowserNativeInput bridge=new MdoBrowserNativeInput(view,document,()->!stopped&&views.owns(key,view)&&
            views.epoch(key,view)==epoch&&channel.current()==lease&&channel.active(lease,host.getUrl()),()->views.scale(key,view),request);
        final boolean focusing=request.getString("kind").equals("focus");
        bridge.run(views.input(key,view),request,result->{
            try{
                JSONObject value=new JSONObject().put("accepted",result.ok).put("trusted_input",result.ok&&!focusing)
                    .put("target_focused",result.ok&&focusing).put("effect_may_have_occurred",result.effectMayHaveOccurred)
                    .put("cleanup_confirmed",result.cleanupConfirmed).put("delivered_steps",result.deliveredSteps);
                if(result.code!=null)value.put("error",new JSONObject().put("code",result.code));
                reply(lease,id,value,null);
            }catch(JSONException error){reply(lease,id,null,"input_result_invalid");}
        });
    }
