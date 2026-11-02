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
            final MdoBrowserCdpNetworks.Navigation proof=navigationReadProof(key,view,operation.navigationRef);
            // The ordinary frame reader has no UI mutation reservation. Only
            // upload.clear may join the fixed primitive to its original record.
            if(operation.action.equals("file_clear"))throw new java.io.IOException("cdp_file_clear_requires_upload_route");
            if(operation.effect&&request.optBoolean("agent"))requireProtocolPopupPreparation(key,view);
            // Product/profile admission runs in request() before this branch.
            // No request field can enable debugging or bypass that admission.
            boolean blocked=!chooserReady(dialogs.get(key)),continuation=operation.chooserContinuation();
            if(blocked&&(!continuation||!chooserContinuationReady(key,view)||views.inputBusy(key,view)||!views.inputClean(key,view)))throw new java.io.IOException("cdp_chooser_scope_unavailable");
            final PopupPage popup=protocolPopupDocument(key,view,"list".equals(operation.action)&&proof==null);
            final boolean restoring=popup!=null&&popup.restoring;
            final MdoBrowserCdpWorker.DocumentRead restoringRead=restoring?popup.restoringRead:null;
            final MdoBrowserCdpMarker marker;
            if(popup!=null){
                if(continuation){if(!popup.marker.lease().busy())throw new java.io.IOException("cdp_worker_binding_unavailable");}
                else if(!cdpEnvironmentCurrent(key,view,epoch,original))throw new java.io.IOException("cdp_popup_page_document_unavailable");
                marker=popup.marker;
            }else marker=continuation?cdpViews.existing(key,view,epoch,original):cdpMarker(key,view);
            MdoBrowserCdpWorker.Completion complete=result->{
                try{
                    JSONObject response;
                    try{
                        if(!views.owns(key,view)||views.epoch(key,view)!=epoch)throw new java.io.IOException("cdp_worker_cancelled");
                        if(popup!=null&&(popup.marker!=marker||!(restoring&&popup.restoring&&popup.current()&&popup.documentCurrent()||popup.documentReady())||!continuation&&!cdpEnvironmentCurrent(key,view,epoch,original)))throw new java.io.IOException("cdp_worker_cancelled");
                        if(proof!=null&&navigationReadProof(key,view,operation.navigationRef)!=proof)throw new java.io.IOException("cdp_navigation_cancelled");
                        if(restoringRead!=null&&(popup.restoringRead!=restoringRead||result.code==null&&!restoringRead.current()))throw new java.io.IOException("cdp_navigation_document_changed");
                        response=MdoBrowserCdpFrameRequest.response(result);
                        if(restoring){if(response.optBoolean("ok"))popup.restored(marker,response);else popup.restoreFailed(marker);}
                    }catch(java.io.IOException changed){response=MdoBrowserCdpFrameRequest.failure(changed.getMessage(),result.effectMayHaveOccurred);}
                    if(restoring&&!response.optBoolean("ok"))popup.restoreFailed(marker);
                    reply(original,id,response,null);
                }catch(JSONException error){reply(original,id,null,"frame_result_invalid");}
            };
            if(popup!=null){
                if(continuation)cdpWorker.submitExisting(marker,operation.timeout,operation.effect,operation::run,complete);
                else if(proof!=null)cdpWorker.submitDocumentGuarded(marker,popup.network.handoff,popup.proof,operation.timeout,operation.effect,proof,operation::run,complete);
                else if(restoringRead!=null)cdpWorker.submitDocumentGuarded(marker,popup.network.handoff,popup.proof,operation.timeout,false,restoringRead,operation::run,complete);
                else cdpWorker.submitDocument(marker,popup.network.handoff,popup.proof,operation.timeout,operation.effect,operation::run,complete);
            }
            else if(proof!=null)cdpWorker.submitGuarded(marker,operation.timeout,operation.effect,proof,operation::run,complete);
            else if(continuation)cdpWorker.submitExisting(marker,operation.timeout,operation.effect,operation::run,complete);
            else cdpWorker.submit(marker,operation.timeout,operation.effect,operation::run,complete);
        }catch(java.io.IOException error){reply(original,id,MdoBrowserCdpFrameRequest.failure(error.getMessage(),false),null);}
        catch(RuntimeException error){reply(original,id,MdoBrowserCdpFrameRequest.failure("cdp_binding_owner_changed",false),null);}
    }
