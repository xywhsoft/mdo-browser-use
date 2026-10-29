    private boolean chooserReady(MdoBrowserDialogTarget target){
        if(!(target instanceof MdoBrowserChooserTarget))return true;
        MdoBrowserChoosers binding=((MdoBrowserChooserTarget)target).choosers;
        return binding.ready();
    }
    private void chooserBlocked(String key,WebView view,boolean blocked){
        // A retired target must not update a replacement document with the same key.
        if(!views.owns(key,view))return;
        MdoBrowserDocument document=documents.get(key);if(document!=null)document.block(MdoBrowserDocument.CHOOSER,blocked);
        if(blocked){cancelCapture(key);if(views.owns(key,view))views.input(key,view).invalidate("file_chooser_open");}
    }
    private void cancelChooser(String key){
        MdoBrowserDialogTarget target=dialogs.get(key);
        if(target instanceof MdoBrowserChooserTarget)((MdoBrowserChooserTarget)target).choosers.cancel();
    }
    private void cancelChoosers(){
        for(MdoBrowserDialogTarget target:new ArrayList<>(dialogs.values()))
            if(target instanceof MdoBrowserChooserTarget)((MdoBrowserChooserTarget)target).choosers.cancel();
    }
    private boolean chooserClean(MdoBrowserDialogTarget target){
        if(!(target instanceof MdoBrowserChooserTarget))return true;
        MdoBrowserChoosers binding=((MdoBrowserChooserTarget)target).choosers;
        return binding.ready();
    }
    private JSONObject chooserState(String key,JSONObject state)throws JSONException {
        MdoBrowserDialogTarget target=dialogs.get(key);
        MdoBrowserChoosers binding=target instanceof MdoBrowserChooserTarget?((MdoBrowserChooserTarget)target).choosers:null;
        MdoBrowserChoosers.Snapshot pending=binding==null?null:binding.snapshot();
        return uploadState(state).put("chooser_capability",binding!=null).put("chooser_upload_capability",false)
            .put("chooser_armed",binding!=null&&binding.intercepting()).put("chooser_open",pending!=null)
            .put("chooser_cleanup_failed",binding!=null&&!binding.clean()).put("chooser_frame_identity_verified",false);
    }
    private void executeChooser(String key,String id,JSONObject request)throws JSONException {
        MdoBrowserDialogTarget original=dialogs.get(key);
        if(!(original instanceof MdoBrowserChooserTarget)||!views.owns(key,original.view))throw new IllegalStateException("chooser_unavailable");
        MdoBrowserChoosers binding=((MdoBrowserChooserTarget)original).choosers;
        String action=request.optString("action");
        if("set_interception".equals(action)){
            Object enabled=request.opt("enabled");if(!(enabled instanceof Boolean))throw new IllegalArgumentException("chooser_interception_invalid");
            binding.interception((Boolean)enabled);
            if(Boolean.TRUE.equals(enabled)&&!binding.intercepting())throw new IllegalStateException("chooser_cleanup_failed");
            reply(id,new JSONObject().put("ok",true).put("enabled",binding.intercepting()).put("assignment_supported",false),null);return;
        }
        if("list".equals(action)){
            JSONArray rows=new JSONArray();MdoBrowserChoosers.Snapshot pending=binding.snapshot();
            if(pending!=null){
                JSONArray accept=new JSONArray();for(String type:pending.accept)accept.put(type);
                rows.put(new JSONObject().put("id",pending.id).put("mode",pending.mode).put("multiple",pending.multiple)
                    .put("directory",pending.directory).put("capture",pending.capture).put("accept",accept).put("navigation",pending.navigation)
                    .put("cancelable",pending.cancelable).put("frame_scope","owned-webview-unidentified-frame").put("assignment_supported",false));
            }
            reply(id,new JSONObject().put("ok",true).put("choosers",rows).put("assignment_supported",false),null);return;
        }
        if(!"cancel".equals(action))throw new UnsupportedOperationException("chooser_assignment_unsupported");
        Object chooser=request.opt("chooser");if(!(chooser instanceof String)||((String)chooser).length()>100)
            throw new IllegalArgumentException("chooser_identity_invalid");
        MdoBrowserChoosers.Result result=binding.cancel((String)chooser);
        JSONObject value=new JSONObject().put("ok",result.ok).put("cancelled",result.ok)
            .put("effect_may_have_occurred",result.effectMayHaveOccurred).put("cleanup_confirmed",result.cleanupConfirmed);
        if(!result.ok)value.put("error",new JSONObject().put("code",result.code).put("effect_may_have_occurred",result.effectMayHaveOccurred));
        reply(id,value,null);
    }
