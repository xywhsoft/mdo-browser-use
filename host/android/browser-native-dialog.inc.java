    private final Map<String,MdoBrowserDialogTarget> dialogs=new HashMap<>();
    private void bindDialogs(String key,WebView view){
        if(dialogs.containsKey(key))throw new IllegalStateException("Dialog view identity is already bound");
        MdoBrowserDialogTarget target=new MdoBrowserDialogTarget(view,
            ()->!stopped&&!browserPaused&&views.owns(key,view)&&view.isShown()&&channel.current()!=null&&channel.active(channel.current(),host.getUrl()),
            ()->views.epoch(key,view),blocked->{
                MdoBrowserDocument document=documents.get(key);
                if(document!=null)document.block(MdoBrowserDocument.DIALOG,blocked);
                if(blocked){cancelCapture(key);if(views.owns(key,view))views.input(key,view).invalidate("input_dialog_open");}
            },epoch->{if(views.owns(key,view)&&views.epoch(key,view)==epoch)cancelNavigationRequest(key,view);});
        dialogs.put(key,target);view.setWebChromeClient(target);
    }
    private boolean dialogReady(String key,WebView view){
        MdoBrowserDialogTarget target=dialogs.get(key);
        return target==null||target.view==view&&target.dialogs.clean()&&target.dialogs.snapshot()==null;
    }
    private void cancelDialog(String key){MdoBrowserDialogTarget target=dialogs.get(key);if(target!=null)target.dialogs.cancel();}
    private void cancelDialogs(){for(MdoBrowserDialogTarget target:new ArrayList<>(dialogs.values()))target.dialogs.cancel();}
    private JSONObject dialogState(String key,JSONObject state)throws JSONException {
        MdoBrowserDialogTarget target=dialogs.get(key);MdoBrowserDialogs.Snapshot pending=target==null?null:target.dialogs.snapshot();
        return state.put("dialog_capability",target!=null).put("dialog_open",pending!=null)
            .put("dialog_cleanup_failed",target!=null&&!target.dialogs.clean());
    }
    private void executeDialog(String key,String id,JSONObject request)throws JSONException {
        MdoBrowserDialogTarget target=dialogs.get(key);if(target==null)throw new IllegalStateException("dialog_unavailable");
        String action=request.optString("action");
        if("list".equals(action)){
            JSONArray rows=new JSONArray();MdoBrowserDialogs.Snapshot pending=target.dialogs.snapshot();
            if(pending!=null)rows.put(new JSONObject().put("id",pending.id).put("kind",pending.kind).put("url",pending.url)
                .put("message",pending.message).put("navigation",pending.navigation).put("frame_scope","owned-webview")
                .put("respondable",pending.respondable));
            reply(id,new JSONObject().put("ok",true).put("dialogs",rows),null);return;
        }
        if(!"respond".equals(action))throw new IllegalArgumentException("dialog_action_invalid");
        if(manual.contains(key)&&request.optBoolean("agent"))throw new IllegalStateException("user_taken_control");
        Object accept=request.opt("accept"),hasText=request.opt("has_text"),text=request.opt("text");
        if(!(accept instanceof Boolean)||hasText!=null&&!(hasText instanceof Boolean)||text!=null&&!(text instanceof String))
            throw new IllegalArgumentException("dialog_response_invalid");
        MdoBrowserDialogs.Result result=target.dialogs.respond(request.optString("dialog"),(Boolean)accept,
            Boolean.TRUE.equals(hasText),text instanceof String?(String)text:null);
        JSONObject value=new JSONObject().put("ok",result.ok).put("responded",result.ok)
            .put("effect_may_have_occurred",result.effectMayHaveOccurred).put("cleanup_confirmed",result.cleanupConfirmed);
        if(!result.ok)value.put("error",new JSONObject().put("code",result.code).put("effect_may_have_occurred",result.effectMayHaveOccurred));
        reply(id,value,null);
    }
