    private boolean chooserFramesAllowed(String key,WebView view,JSONObject request){
        if(!"frames".equals(request.opt("command"))||!chooserContinuationReady(key,view)||views.inputBusy(key,view)||!views.inputClean(key,view))return false;
        try{return MdoBrowserCdpFrameRequest.parse(request).chooserContinuation();}
        catch(java.io.IOException|RuntimeException invalid){return false;}
    }
    private void executeFrames(String key,String id,WebView view,JSONObject request)throws JSONException {
        final MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();final long epoch=views.epoch(key,view);
        try{
            if(uploadBusy(key,view))throw new java.io.IOException("cdp_upload_busy");
            final MdoBrowserCdpFrameRequest operation=MdoBrowserCdpFrameRequest.parse(request);
            // Product/profile admission runs in request() before this branch.
            // No request field can enable debugging or bypass that admission.
            boolean blocked=!chooserReady(dialogs.get(key)),continuation=operation.chooserContinuation();
            if(blocked&&(!continuation||!chooserContinuationReady(key,view)||views.inputBusy(key,view)||!views.inputClean(key,view)))throw new java.io.IOException("cdp_chooser_scope_unavailable");
            final MdoBrowserCdpMarker marker=continuation?cdpViews.existing(key,view,epoch,original):cdpMarker(key,view);
            MdoBrowserCdpWorker.Completion complete=result->{
                try{
                    JSONObject response=!views.owns(key,view)||views.epoch(key,view)!=epoch?
                        MdoBrowserCdpFrameRequest.failure("cdp_worker_cancelled",result.effectMayHaveOccurred):MdoBrowserCdpFrameRequest.response(result);
                    reply(original,id,response,null);
                }catch(JSONException error){reply(original,id,null,"frame_result_invalid");}
            };
            if(continuation)cdpWorker.submitExisting(marker,operation.timeout,operation.effect,operation::run,complete);
            else cdpWorker.submit(marker,operation.timeout,operation.effect,operation::run,complete);
        }catch(java.io.IOException error){reply(original,id,MdoBrowserCdpFrameRequest.failure(error.getMessage(),false),null);}
        catch(RuntimeException error){reply(original,id,MdoBrowserCdpFrameRequest.failure("cdp_binding_owner_changed",false),null);}
    }
