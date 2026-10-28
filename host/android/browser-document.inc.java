    private final Map<String,MdoBrowserDocument> documents=new HashMap<>();
    private final Map<String,String> documentFailures=new HashMap<>();
    private final MdoBrowserWorld.Budget worldBudget=new MdoBrowserWorld.Budget(32);
    private boolean browserPaused;
    private void bindDocument(String key,WebView view,boolean automation){
        if(documents.containsKey(key))closeDocument(key);
        documentFailures.remove(key);
        if(android.os.Build.VERSION.SDK_INT<28){documentFailures.put(key,"provider_loader_unsupported");return;}
        try{
            MdoBrowserDocument document=new MdoBrowserDocument(view,WebView.getWebViewClassLoader(),new MdoBrowserWorld.Scheduler(){
                private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
                public long now(){return android.os.SystemClock.uptimeMillis();}
                public void post(Runnable task,long delay){if(!handler.postDelayed(task,delay))throw new IllegalStateException("World scheduler stopped");}
                public void remove(Runnable task){handler.removeCallbacks(task);}
            },()->!stopped&&views.owns(key,view),()->views.epoch(key,view),automation?"mdo.browser-use":null,worldBudget);
            document.block(MdoBrowserDocument.BACKGROUND,browserPaused);
            document.block(MdoBrowserDocument.CHANNEL,channel.current()==null);
            documents.put(key,document);
        }catch(MdoBrowserWorld.Failure failure){
            documentFailures.put(key,failure.code);
            if(automation){views.remove(key);documentFailures.remove(key);view.destroy();throw new UnsupportedOperationException(failure.code);}
        }
    }
    private boolean closeDocument(String key){
        MdoBrowserDialogTarget dialog=dialogs.get(key);if(dialog!=null)dialog.close();
        CaptureEntry capture=captures.get(key);if(capture!=null)capture.capture.close();
        MdoBrowserDocument document=documents.remove(key);
        documentFailures.remove(key);
        if(document==null)return dialog==null||dialog.dialogs.clean();document.close();return document.cleanupConfirmed()&&(dialog==null||dialog.dialogs.clean());
    }
    private boolean closeAndDestroyDocument(String key,WebView view){
        MdoBrowserDialogTarget dialog=dialogs.remove(key);if(dialog!=null)dialog.close();
        CaptureEntry capture=captures.remove(key);if(capture!=null)capture.capture.close();
        MdoBrowserDocument document=documents.remove(key);documentFailures.remove(key);
        if(document!=null)document.close();root.removeView(view);view.destroy();
        if(dialog!=null&&dialog.view==view)dialog.viewDestroyed();
        if(capture!=null&&capture.view==view)capture.capture.viewDestroyed();
        if(document!=null)document.viewDestroyed(view);
        return document==null||document.cleanupConfirmed();
    }
    private void documentNavigation(String key,WebView view){
        if(!views.owns(key,view))return;
        cancelDialog(key);
        cancelCapture(key);
        MdoBrowserDocument document=documents.get(key);if(document!=null)document.navigation();
    }
    private void documentLoaded(String key,WebView view){
        if(!views.owns(key,view))return;
        MdoBrowserDocument document=documents.get(key);if(document!=null)document.loaded();
    }
    private void blockDocuments(int reason,boolean value){
        if(value)cancelCaptures();
        for(MdoBrowserDocument document:new ArrayList<>(documents.values()))document.block(reason,value);
    }
    private JSONObject documentState(String key) throws JSONException {
        MdoBrowserDocument document=documents.get(key);
        return new JSONObject().put("installed",document!=null).put("ready",document!=null&&document.ready())
            .put("phase",document==null?documentFailures.getOrDefault(key,"world_unavailable"):document.phase())
            .put("pending",document==null?0:document.pending()).put("native_outstanding",document==null?0:document.outstanding())
            .put("native_budget_used",worldBudget.used()).put("native_budget_limit",worldBudget.limit())
            .put("native_document",document==null||document.identity()==null?JSONObject.NULL:document.identity())
            .put("adapter","android-main-document-v1")
            .put("profile_isolation",document!=null&&document.profileAssigned());
    }
    private void documentFailure(MdoBrowserChannel<WebMessagePort>.Lease lease,String id,String identity,long epoch,String code,boolean sent){
        try{reply(lease,id,new JSONObject().put("ok",false).put("native_document",identity==null?JSONObject.NULL:identity).put("navigation",epoch)
            .put("error",new JSONObject().put("code",code).put("evaluation_may_have_occurred",sent)),null);}
        catch(JSONException error){reply(lease,id,null,"world_result_invalid");}
    }
    private void executeDocument(String key,String id,JSONObject request) throws Exception {
        MdoBrowserDocument document=documents.get(key);
        if(document==null)throw new UnsupportedOperationException(documentFailures.getOrDefault(key,"world_unavailable"));
        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        final String identity=document.identity();final long epoch=views.epoch(key,views.get(key));
        final boolean pinned=request.has("native_document")||request.has("navigation");
        String expected=null;
        if(pinned){
            expected=request.getString("native_document");
            if(!(request.get("navigation") instanceof Number)||request.getDouble("navigation")!=epoch||!expected.equals(identity)){
                documentFailure(lease,id,identity,epoch,"frame_changed",false);return;
            }
        }
        String expression="(()=>{try{return {ok:true,value:("+request.getString("script")+")}}catch(e){return {ok:false,error:String(e.message||e)}}})()";
        try{document.evaluate(expected,expression,Math.min(10000,Math.max(1,request.optLong("timeout_ms",10000))),result->{
            if(!result.ok){if(pinned)documentFailure(lease,id,identity,epoch,result.code,result.evaluationMayHaveOccurred);else reply(lease,id,null,result.code);return;}
            try{JSONObject value=new JSONObject(result.value);
                if(pinned&&!value.optBoolean("ok")){documentFailure(lease,id,identity,epoch,"document_script_failed",result.evaluationMayHaveOccurred);return;}
                value.put("native_document",identity).put("navigation",epoch);reply(lease,id,value,value.optBoolean("ok")?null:value.optString("error","World execution failed"));}
            catch(JSONException error){reply(lease,id,null,"world_result_invalid");}
        });}catch(MdoBrowserWorld.Failure failure){if(pinned)documentFailure(lease,id,identity,epoch,failure.code,false);else throw failure;}
    }
