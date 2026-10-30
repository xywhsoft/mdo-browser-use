    /** Native original-target/operation records. These are never reconstructed
     * from a caller ticket, URI, callback timing or a replacement CDP world. */
    private static final class UploadAction {
        final UploadEntry source;final MdoBrowserCdpFileInput target;final MdoBrowserCdpMarker marker;
        final Object channel;final String key;String ticket;MdoBrowserUploadCoordinator coordinator;boolean released;
        UploadAction(UploadEntry source,MdoBrowserCdpFileInput target,MdoBrowserCdpMarker marker,Object channel,String key){
            this.source=source;this.target=target;this.marker=marker;this.channel=channel;this.key=key;
        }
    }
    private static final class UploadBook {
        final WebView view;final MdoBrowserUploadStore.RetentionOwner owner;
        final Map<String,UploadAction> keys=new HashMap<>(),tickets=new HashMap<>();UploadAction active;
        final Map<String,ClearAction> clearKeys=new HashMap<>();ClearAction clearing;
        UploadBook(WebView view,MdoBrowserUploadStore.RetentionOwner owner){this.view=view;this.owner=owner;}
    }
    private static final class ClearAction {
        final UploadEntry source;final MdoBrowserCdpFileInput target;final MdoBrowserCdpMarker marker;
        final Object channel;final String key,ticket="u-"+java.util.UUID.randomUUID();
        boolean done,cancelled,ok,dispatched,clean=true,released,registered;String error;
        ClearAction(UploadEntry source,MdoBrowserCdpFileInput target,MdoBrowserCdpMarker marker,Object channel,String key){
            this.source=source;this.target=target;this.marker=marker;this.channel=channel;this.key=key;
        }
    }
    private final Map<MdoBrowserUploadStore.RetentionOwner,UploadBook> uploadBooks=new HashMap<>();
    private UploadBook uploadBook(String key,WebView view){
        UploadDataEntry data=uploadDataOwners.get(key);if(data==null||data.view!=view||!data.owner.live())return null;
        UploadBook book=uploadBooks.get(data.owner);return book!=null&&book.view==view?book:null;
    }
    private boolean uploadBusy(String key,WebView view){UploadBook book=uploadBook(key,view);return book!=null&&(book.clearing!=null&&!book.clearing.done||book.active!=null&&book.active.coordinator!=null&&!book.active.coordinator.done());}
    private void cancelClear(ClearAction clear){if(clear!=null&&!clear.done){clear.cancelled=true;cdpWorker.cancel(clear.marker.lease());}}
    private void cancelTargetedUpload(String key,WebView view,long epoch){
        UploadBook book=uploadBook(key,view);UploadAction active=book==null?null:book.active;
        if(active!=null&&active.source.scope.navigation==epoch&&active.coordinator!=null)active.coordinator.close();
        if(book!=null&&book.clearing!=null&&book.clearing.source.scope.navigation==epoch)cancelClear(book.clearing);
    }
    private void cancelUploadTargets(UploadEntry source){
        UploadBook book=uploadBooks.get(source.data);if(book==null)return;
        UploadAction active=book.active;if(active!=null&&active.source==source&&active.coordinator!=null)active.coordinator.close();
        if(book.clearing!=null&&book.clearing.source==source)cancelClear(book.clearing);
        for(Iterator<UploadAction> at=book.keys.values().iterator();at.hasNext();)if(at.next().source==source)at.remove();
        for(Iterator<UploadAction> at=book.tickets.values().iterator();at.hasNext();){UploadAction row=at.next();if(row.source==source&&row.coordinator==null)at.remove();}
    }
    private void retireUploadTargets(WebView view){
        // Native callback cancellation can synchronously retire another view.
        // Never retain a live map iterator across coordinator.close().
        for(UploadBook book:new ArrayList<>(uploadBooks.values())){if(book.view!=view||!uploadBooks.remove(book.owner,book))continue;
            UploadAction active=book.active;if(active!=null&&active.coordinator!=null)active.coordinator.close();
            cancelClear(book.clearing);
        }
    }
    private static MdoBrowserCdpFileInput uploadTarget(JSONObject request)throws Exception {
        JSONArray chain=request.getJSONArray("frame_chain");if(chain.length()<1)throw new IllegalArgumentException("upload_target_invalid");
        JSONObject leaf=chain.getJSONObject(0);
        return MdoBrowserCdpFileInput.parse(new JSONObject().put("frame",leaf.get("frame")).put("document",leaf.get("document"))
            .put("local_document",uploadText(request,"document")).put("ref",uploadText(request,"ref")).put("frame_chain",chain));
    }
    private static boolean sameUploadTarget(MdoBrowserCdpFileInput left,MdoBrowserCdpFileInput right){
        if(!left.document.equals(right.document)||!left.ref.equals(right.ref)||left.pins.chain.size()!=right.pins.chain.size())return false;
        for(int i=0;i<left.pins.chain.size();i++){MdoBrowserCdpFrameChain.Pin a=left.pins.chain.get(i),b=right.pins.chain.get(i);if(!a.frame.equals(b.frame)||!a.document.equals(b.document))return false;}return true;
    }
    private Object reserveUploadTarget(String key,UploadEntry source,JSONObject request,MdoBrowserUploadQueue.Request queued)throws Exception {
        if(!"begin".equals(queued.action))return null;
        if(uploadBusy(key,source.view))throw new IllegalStateException("upload_operation_busy");
        boolean pinned=request.has("document")||request.has("ref")||request.has("frame_chain");
        UploadBook book=uploadBook(key,source.view);UploadAction old=book==null?null:book.keys.get(queued.key);
        if(!pinned){
            if(old!=null){if(old.target!=null||old.source!=source||old.channel!=channel.current())throw new IllegalStateException("upload_target_changed");return old;}
            if(book==null){book=new UploadBook(source.view,source.data);uploadBooks.put(source.data,book);}
            if(book.keys.size()>=64||book.tickets.size()>=64)throw new IllegalStateException("upload_operation_limit");
            UploadAction created=new UploadAction(source,null,null,channel.current(),queued.key);book.keys.put(queued.key,created);return created;
        }
        if(!browserUseSupport().optBoolean("available"))throw new UnsupportedOperationException("upload_assignment_unsupported");
        MdoBrowserCdpFileInput target=uploadTarget(request);
        MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();
        if(!cdpEnvironmentCurrent(key,source.view,source.scope.navigation,original)||!chooserReady(dialogs.get(key))||views.inputBusy(key,source.view)||!views.inputClean(key,source.view))
            throw new IllegalStateException("upload_document_unavailable");
        MdoBrowserCdpMarker marker=cdpViews.existing(key,source.view,source.scope.navigation,original);
        if(cdpWorker.pending(marker)||uploadBusy(key,source.view))throw new IllegalStateException("upload_operation_busy");
        if(old!=null){if(old.target==null||old.source!=source||old.channel!=original||old.marker!=marker||!sameUploadTarget(old.target,target))throw new IllegalStateException("upload_target_changed");return old;}
        if(book==null){book=new UploadBook(source.view,source.data);uploadBooks.put(source.data,book);}
        if(book.keys.size()>=64||book.tickets.size()>=64)throw new IllegalStateException("upload_operation_limit");
        UploadAction created=new UploadAction(source,target,marker,original,queued.key);book.keys.put(queued.key,created);return created;
    }
    private boolean uploadTargetReady(Object reserved,MdoBrowserUploadQueue.Outcome outcome){
        if(reserved==null||!outcome.ok()||outcome.value==null)return false;UploadAction action=(UploadAction)reserved;
        UploadBook book=uploadBooks.get(action.source.data);MdoBrowserUploadStore.Snapshot value=outcome.value;
        if(book==null||book.keys.get(action.key)!=action||!action.key.equals(value.key)||value.navigation!=action.source.scope.navigation||!action.source.scope.live())return false;
        if(action.ticket!=null&&!action.ticket.equals(value.ticket))throw new IllegalStateException("upload_ticket_changed");
        UploadAction old=book.tickets.get(value.ticket);if(old!=null&&old!=action)throw new IllegalStateException("upload_ticket_changed");
        action.ticket=value.ticket;book.tickets.put(value.ticket,action);return action.target!=null;
    }
    private boolean uploadCurrent(String key,UploadBook book,UploadAction action,MdoBrowserChannel<WebMessagePort>.Lease original){
        UploadDataEntry data=uploadDataOwners.get(key);MdoBrowserDialogTarget dialog=dialogs.get(key);
        return data!=null&&data.view==book.view&&data.owner==book.owner&&book.owner.live()&&book.active==action&&
            uploadOwners.get(key)==action.source&&action.source.scope.live()&&channel.current()==original&&action.channel==original&&
            cdpEnvironmentCurrent(key,book.view,action.source.scope.navigation,original)&&manualChooser.ready()&&
            dialog instanceof MdoBrowserChooserTarget&&dialog.view==book.view&&((MdoBrowserChooserTarget)dialog).choosers.clean()&&
            ((MdoBrowserChooserTarget)dialog).choosers.snapshot()==null&&((MdoBrowserChooserTarget)dialog).privatePicker.clean()&&
            views.inputClean(key,book.view)&&!views.inputBusy(key,book.view);
    }
    private JSONObject uploadReceipt(UploadAction action)throws JSONException {
        MdoBrowserUploadCoordinator.Result value=action.coordinator.result();boolean pending=value==null;
        MdoBrowserUploadStore.Retention kept=action.coordinator.retained();boolean retained=!pending&&value.retained;
        if(pending&&kept!=null)retained=kept.adopted()&&kept.live(android.os.SystemClock.elapsedRealtime());
        boolean released=action.released||!pending&&value.stagingCleanupConfirmed;
        JSONObject result=new JSONObject().put("ok",pending||value.ok).put("ticket",action.ticket).put("upload_key",action.key)
            .put("assignment_ok",!pending&&value.ok)
            .put("navigation",action.source.scope.navigation).put("receipt_only",true).put("assignment_supported",true)
            .put("pending",pending).put("accepted",!pending&&value.accepted).put("completed",!pending&&value.completed)
            .put("verified",!pending&&value.verified).put("dispatched",pending||value.dispatched)
            .put("effect_may_have_occurred",pending||value.dispatched).put("retained",retained).put("released",released)
            .put("state",pending?"assigning":released?"released":retained?"retained":"unretained")
            .put("cleanup_confirmed",!pending&&value.cleanupConfirmed).put("staging_cleanup_confirmed",!pending&&value.stagingCleanupConfirmed)
            .put("events_trusted",false).put("input_method","native_picker_dom_filelist");
        if(!pending&&value.outcome!=null)result.put("outcome",value.outcome);
        if(!pending&&value.code!=null)result.put("error",new JSONObject().put("code",value.code).put("effect_may_have_occurred",value.dispatched));
        return result;
    }
    private JSONObject uploadControlReceipt(UploadAction action)throws JSONException {
        JSONObject value=uploadReceipt(action);if(value.has("error"))value.put("assignment_error",value.remove("error"));return value.put("ok",true);
    }
    private boolean uploadCommand(String key,String id,WebView view,JSONObject request)throws Exception {
        String verb=uploadText(request,"action");
        if(clearCommand(key,id,view,request))return true;
        if("begin".equals(verb)&&uploadBusy(key,view))throw new IllegalStateException("upload_operation_busy");
        if(!Arrays.asList("assign","status","cancel","release").contains(verb))return false;
        if("assign".equals(verb)&&!browserUseSupport().optBoolean("available"))throw new UnsupportedOperationException("upload_assignment_unsupported");
        UploadBook book=uploadBook(key,view);String ticket=uploadText(request,"ticket");UploadAction action=book==null?null:book.tickets.get(ticket);
        MdoBrowserUploadQueue.Request.ticket("status",ticket);
        if(!"assign".equals(verb)){
            if(action==null||action.coordinator==null)return false;
            uploadFields(request,"action","ticket","navigation","confirmed");
            if(request.has("navigation")&&uploadNumber(request,"navigation",1,9007199254740991L)!=action.source.scope.navigation)throw new IllegalStateException("upload_owner_changed");
            final MdoBrowserChannel<WebMessagePort>.Lease replyChannel=channel.current();
            if("cancel".equals(verb)){action.coordinator.close();reply(replyChannel,id,uploadControlReceipt(action).put("cancel_requested",true),null);return true;}
            if("release".equals(verb)&&!Boolean.TRUE.equals(request.opt("confirmed")))throw new IllegalArgumentException("upload_confirmation_required");
            if("release".equals(verb)&&!action.coordinator.done())throw new IllegalStateException("upload_assignment_pending");
            MdoBrowserUploadStore.Retention kept=action.coordinator.retained();
            if(kept==null){reply(replyChannel,id,uploadControlReceipt(action),null);return true;}
            final UploadAction original=action;final boolean release="release".equals(verb);
            if(!uploads.submit(action.source.scope,MdoBrowserUploadQueue.Request.retained(kept,release),outcome->{
                try{
                    if(!views.owns(key,view)||uploadBook(key,view)!=book||book.tickets.get(ticket)!=original){reply(replyChannel,id,null,"upload_owner_changed");return;}
                    if(outcome.ok()&&outcome.retainedValue.cleanupConfirmed)original.released=true;
                    JSONObject result=uploadControlReceipt(original);
                    if(!outcome.ok())result.put("ok",false).put("error",new JSONObject().put("code",outcome.code));
                    else result.put("retained",outcome.retainedValue.retained).put("state",outcome.retainedValue.state).put("released",outcome.retainedValue.cleanupConfirmed);
                    reply(replyChannel,id,result,null);
                }catch(JSONException|RuntimeException|OutOfMemoryError error){reply(replyChannel,id,null,"upload_result_unconfirmed");}
            }))throw new IllegalStateException("upload_worker_busy");return true;
        }
        uploadFields(request,"action","ticket","navigation","document","ref","frame_chain","timeout_ms");
        if(!browserUseSupport().optBoolean("available"))throw new UnsupportedOperationException("upload_assignment_unsupported");
        long epoch=uploadNumber(request,"navigation",1,9007199254740991L);
        if(action==null||action.target==null||epoch!=views.epoch(key,view)||epoch!=action.source.scope.navigation||!sameUploadTarget(action.target,uploadTarget(request)))throw new IllegalStateException("upload_target_changed");
        if(action.coordinator!=null){reply(channel.current(),id,uploadReceipt(action),null);return true;}
        if(uploadBusy(key,view))throw new IllegalStateException("upload_operation_busy");
        final MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();final UploadAction operation=action;final UploadBook originalBook=book;
        if(uploadOwners.get(key)!=action.source||!action.source.scope.live()||action.channel!=original||
            !cdpEnvironmentCurrent(key,view,epoch,original)||!chooserReady(dialogs.get(key))||views.inputBusy(key,view)||!views.inputClean(key,view))throw new IllegalStateException("upload_owner_changed");
        if(cdpViews.existing(key,view,epoch,original)!=action.marker||cdpWorker.pending(action.marker))throw new IllegalStateException("upload_target_changed");
        MdoBrowserDialogTarget dialog=dialogs.get(key);if(!(dialog instanceof MdoBrowserChooserTarget)||dialog.view!=view)throw new IllegalStateException("upload_picker_unavailable");
        int timeout=(int)(request.has("timeout_ms")?uploadNumber(request,"timeout_ms",100,120000):60000);
        android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
        MdoBrowserUploadCoordinator coordinator=new MdoBrowserUploadCoordinator(uploads,action.source.scope,ticket,book.owner,cdpWorker,action.marker,((MdoBrowserChooserTarget)dialog).privatePicker,action.target,
            ()->uploadCurrent(key,originalBook,operation,original),new MdoBrowserUploadCoordinator.Scheduler(){
                public void at(Runnable task,long due){long delay=Math.max(0,due-android.os.SystemClock.elapsedRealtime());if(!handler.postDelayed(task,delay))throw new IllegalStateException("Upload scheduler stopped");}
                public void remove(Runnable task){handler.removeCallbacks(task);}
            },android.os.SystemClock::elapsedRealtime,timeout,value->{
                if(originalBook.active==operation)originalBook.active=null;
                try{
                    JSONObject result=uploadReceipt(operation);
                    if(!views.owns(key,view)||views.epoch(key,view)!=epoch)result.put("ok",false).put("accepted",false).put("completed",false).put("verified",false)
                        .put("error",new JSONObject().put("code","upload_owner_changed").put("effect_may_have_occurred",value.dispatched));
                    reply(original,id,result,null);
                }catch(JSONException|RuntimeException|OutOfMemoryError error){reply(original,id,null,"upload_result_unconfirmed");}
            });
        operation.coordinator=coordinator;book.active=operation;coordinator.start();return true;
    }
    private JSONObject uploadAssignmentState(String key,JSONObject state)throws JSONException {
        UploadBook book=uploadBook(key,views.get(key));boolean failed=false;
        if(book!=null)for(UploadAction action:book.tickets.values())if(action.coordinator!=null&&action.coordinator.done()){
            MdoBrowserUploadCoordinator.Result value=action.coordinator.result();if(value!=null&&!value.cleanupConfirmed){failed=true;break;}}
        boolean supported=browserUseSupport().optBoolean("available");
        if(book!=null)for(ClearAction clear:book.clearKeys.values())if(clear.done&&!clear.clean){failed=true;break;}
        return state.put("upload_busy",uploadBusy(key,views.get(key))).put("upload_cleanup_failed",failed)
            .put("upload_capability",supported).put("file_input_capability",supported).put("file_clear_capability",supported);
    }
    private JSONObject clearReceipt(ClearAction clear,boolean control)throws JSONException {
        JSONObject result=new JSONObject().put("ok",control||!clear.done||clear.ok).put("accepted",clear.done&&clear.ok)
            .put("pending",!clear.done).put("completed",clear.done&&clear.ok).put("cleared",clear.done&&clear.ok).put("verified",clear.done&&clear.ok)
            .put("upload_key",clear.key).put("navigation",clear.source.scope.navigation).put("clear",true)
            .put("receipt_only",true).put("assignment_ok",clear.done&&clear.ok).put("assignment_supported",true)
            .put("dispatched",!clear.done||clear.dispatched).put("effect_may_have_occurred",!clear.done||clear.dispatched)
            .put("retained",false).put("released",clear.released).put("cleanup_confirmed",clear.done&&clear.clean)
            .put("native_job_drained",clear.done).put("state",!clear.done?"clearing":clear.released?"released":clear.ok?"cleared":"failed")
            .put("input_method","native_file_clear");
        if(clear.registered)result.put("ticket",clear.ticket);
        if(clear.error!=null){String message=!clear.clean?"Native file-clear cleanup was not confirmed; end this browser before further input. Its original receipt remains available":
                clear.dispatched?"The original file-clear operation was not confirmed; inspect its receipt and delivered effects before an explicit new action":
                "The native file-clear request was rejected before dispatch; inspect its original scope before another action";
            JSONObject error=new JSONObject().put("code",clear.error).put("message",message).put("effect_may_have_occurred",clear.dispatched);
            result.put(control?"assignment_error":"error",error);}
        return result;
    }
    private boolean clearCurrent(String key,UploadBook book,ClearAction clear,MdoBrowserChannel<WebMessagePort>.Lease channelLease){
        return uploadBook(key,book.view)==book&&book.clearing==clear&&book.clearKeys.get(clear.key)==clear&&!clear.cancelled&&clear.source.scope.live()&&
            uploadOwners.get(key)==clear.source&&channel.current()==channelLease&&clear.channel==channelLease&&
            cdpEnvironmentCurrent(key,book.view,clear.source.scope.navigation,channelLease)&&chooserReady(dialogs.get(key))&&
            !views.inputBusy(key,book.view)&&views.inputClean(key,book.view)&&captureMutationReady(key,book.view);
    }
    private boolean clearCommand(String key,String id,WebView view,JSONObject request)throws Exception {
        String verb=uploadText(request,"action");UploadBook book=uploadBook(key,view);ClearAction clear=null;
        final MdoBrowserChannel<WebMessagePort>.Lease replyChannel=channel.current();
        if(Arrays.asList("status","cancel","release").contains(verb)){
            String ticket=uploadText(request,"ticket");
            if(book!=null)for(ClearAction previous:book.clearKeys.values())if(previous.ticket.equals(ticket)){clear=previous;break;}
            if(clear==null)return false;
            uploadFields(request,"action","ticket","navigation","confirmed");
            if(request.has("navigation")&&uploadNumber(request,"navigation",1,9007199254740991L)!=clear.source.scope.navigation)throw new IllegalStateException("upload_owner_changed");
            if("release".equals(verb)){
                if(!Boolean.TRUE.equals(request.opt("confirmed")))throw new IllegalArgumentException("upload_confirmation_required");
                if(!clear.done)throw new IllegalStateException("upload_clear_pending");clear.released=true;
            }else if("cancel".equals(verb))cancelClear(clear);
            reply(replyChannel,id,clearReceipt(clear,true),null);return true;
        }
        if(!Arrays.asList("clear","clear_status","clear_cancel").contains(verb))return false;
        String clearKey=uploadText(request,"upload_key");if(!clearKey.matches("[a-zA-Z0-9._:-]{1,100}"))throw new IllegalArgumentException("upload_key_invalid");
        clear=book==null?null:book.clearKeys.get(clearKey);
        if(!"clear".equals(verb)){
            uploadFields(request,"action","upload_key");
            if(clear==null){if(verb.equals("clear_cancel")){reply(replyChannel,id,new JSONObject().put("ok",true).put("cancelled",false),null);return true;}throw new IllegalStateException("upload_clear_missing");}
            if(verb.equals("clear_cancel"))cancelClear(clear);
            reply(replyChannel,id,clearReceipt(clear,true).put("cancel_requested",clear.cancelled),null);return true;
        }
        uploadFields(request,"action","upload_key","navigation","document","ref","frame_chain","timeout_ms");
        long epoch=uploadNumber(request,"navigation",1,9007199254740991L);MdoBrowserCdpFileInput target=uploadTarget(request);
        int timeout=(int)(request.has("timeout_ms")?uploadNumber(request,"timeout_ms",100,120000):10000);timeout=Math.min(timeout,15000);
        if(clear!=null){
            if(clear.source.scope.navigation!=epoch||!sameUploadTarget(clear.target,target))throw new IllegalStateException("upload_clear_conflict");
            reply(replyChannel,id,clearReceipt(clear,false),null);return true;
        }
        if(!browserUseSupport().optBoolean("available"))throw new UnsupportedOperationException("upload_clear_unsupported");
        if(uploadBusy(key,view)||views.inputBusy(key,view))throw new IllegalStateException("upload_operation_busy");
        if(book!=null){
            for(ClearAction old:book.clearKeys.values())if(old.done&&!old.clean)throw new IllegalStateException("upload_cleanup_failed");
            for(UploadAction old:book.tickets.values())if(old.coordinator!=null&&old.coordinator.done()&&old.coordinator.result()!=null&&!old.coordinator.result().cleanupConfirmed)throw new IllegalStateException("upload_cleanup_failed");
        }
        if(!views.inputClean(key,view)||!captureMutationReady(key,view))throw new IllegalStateException("upload_capture_busy_or_unclean");
        if(!cdpEnvironmentCurrent(key,view,epoch,replyChannel)||!chooserReady(dialogs.get(key)))throw new IllegalStateException("upload_owner_changed");
        MdoBrowserCdpMarker marker=cdpViews.existing(key,view,epoch,replyChannel);if(cdpWorker.pending(marker))throw new IllegalStateException("upload_operation_busy");
        UploadEntry source=uploadOwners.get(key);
        if(source==null||source.view!=view||source.scope.navigation!=epoch||!source.scope.live()){
            if(source!=null){source.scope.cancel();cancelUploadTargets(source);}source=new UploadEntry(view,epoch,uploadData(key,view));uploadOwners.put(key,source);
        }
        if(book==null){book=new UploadBook(view,source.data);uploadBooks.put(source.data,book);}
        if(book.clearKeys.size()>=64)throw new IllegalStateException("upload_clear_limit");
        final UploadBook originalBook=book;final ClearAction operation=new ClearAction(source,target,marker,replyChannel,clearKey);
        try{
            book.clearing=operation;book.clearKeys.put(clearKey,operation);operation.registered=true;
            cdpWorker.submitExisting(marker,timeout,true,(binding,deadline)->{
                JSONObject result=target.clear(binding.frames(),deadline);return result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            },outcome->{
                operation.dispatched=outcome.effectMayHaveOccurred;operation.error=outcome.code;
                operation.clean=!Arrays.asList("cdp_file_input_cleanup_failed","cdp_worker_failed","cdp_worker_result_invalid").contains(outcome.code);
                try{
                    if(outcome.code==null){
                        JSONObject response=new JSONObject(new String(outcome.bytes,java.nio.charset.StandardCharsets.UTF_8)),value=response.optJSONObject("value");
                        MdoBrowserCdpFrameChain.Pin leaf=target.pins.chain.get(0);JSONObject element=value==null?null:value.optJSONObject("element");
                        operation.ok=Boolean.TRUE.equals(response.opt("ok"))&&leaf.frame.equals(response.opt("frame"))&&leaf.document.equals(response.opt("frame_document"))&&value!=null&&Boolean.TRUE.equals(value.opt("ok"))&&
                            target.document.equals(value.opt("document"))&&element!=null&&target.ref.equals(element.opt("ref"))&&Boolean.TRUE.equals(value.opt("cleared"))&&Boolean.TRUE.equals(value.opt("verified"))&&Boolean.TRUE.equals(value.opt("dispatched"));
                        if(!operation.ok)operation.error="cdp_file_clear_unconfirmed";
                    }
                    if(operation.cancelled){operation.ok=false;operation.error="upload_clear_cancelled";}
                    else if(!clearCurrent(key,originalBook,operation,replyChannel)){operation.ok=false;operation.error="upload_owner_changed";}
                }catch(JSONException|RuntimeException|OutOfMemoryError error){operation.ok=false;operation.error="upload_result_unconfirmed";}
                finally{operation.done=true;if(originalBook.clearing==operation)originalBook.clearing=null;}
                try{reply(replyChannel,id,clearReceipt(operation,false),null);}catch(JSONException|RuntimeException|OutOfMemoryError error){reply(replyChannel,id,null,"upload_result_unconfirmed");}
            });
        }catch(java.io.IOException|RuntimeException|OutOfMemoryError error){
            operation.done=true;operation.error=error.getMessage()!=null&&error.getMessage().matches("(?:cdp_|upload_)[a-z_]{1,80}")?error.getMessage():"upload_clear_unavailable";
            operation.registered=book.clearKeys.get(clearKey)==operation;
            if(book.clearing==operation)book.clearing=null;reply(replyChannel,id,clearReceipt(operation,false),null);
        }
        return true;
    }
