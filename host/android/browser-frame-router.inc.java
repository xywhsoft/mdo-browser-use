    private void executeFrames(String key,String id,WebView view,JSONObject request)throws JSONException {
        final MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();final long epoch=views.epoch(key,view);
        try{
            final MdoBrowserCdpFrameRequest operation=MdoBrowserCdpFrameRequest.parse(request);
            // Product/profile admission runs in request() before this branch.
            // No request field can enable debugging or bypass that admission.
            final MdoBrowserCdpMarker marker=cdpMarker(key,view);
            cdpWorker.submit(marker,operation.timeout,operation.effect,operation::run,result->{
                try{
                    JSONObject response=!views.owns(key,view)||views.epoch(key,view)!=epoch?
                        MdoBrowserCdpFrameRequest.failure("cdp_worker_cancelled",result.effectMayHaveOccurred):MdoBrowserCdpFrameRequest.response(result);
                    reply(original,id,response,null);
                }catch(JSONException error){reply(original,id,null,"frame_result_invalid");}
            });
        }catch(java.io.IOException error){reply(original,id,MdoBrowserCdpFrameRequest.failure(error.getMessage(),false),null);}
        catch(RuntimeException error){reply(original,id,MdoBrowserCdpFrameRequest.failure("cdp_binding_owner_changed",false),null);}
    }
