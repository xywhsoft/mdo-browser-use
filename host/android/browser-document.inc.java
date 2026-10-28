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
        MdoBrowserDocument document=documents.remove(key);
        documentFailures.remove(key);
        if(document==null)return true;document.close();return document.cleanupConfirmed();
    }
    private boolean closeAndDestroyDocument(String key,WebView view){
        MdoBrowserDocument document=documents.remove(key);documentFailures.remove(key);
        if(document!=null)document.close();root.removeView(view);view.destroy();
        if(document!=null)document.viewDestroyed(view);
        return document==null||document.cleanupConfirmed();
    }
    private void documentNavigation(String key,WebView view){
        if(!views.owns(key,view))return;
        MdoBrowserDocument document=documents.get(key);if(document!=null)document.navigation();
    }
    private void documentLoaded(String key,WebView view){
        if(!views.owns(key,view))return;
        MdoBrowserDocument document=documents.get(key);if(document!=null)document.loaded();
    }
    private void blockDocuments(int reason,boolean value){
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
    private void executeDocument(String key,String id,JSONObject request) throws Exception {
        MdoBrowserDocument document=documents.get(key);
        if(document==null)throw new UnsupportedOperationException(documentFailures.getOrDefault(key,"world_unavailable"));
        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        final String identity=document.identity();final long epoch=views.epoch(key,views.get(key));
        String expected=null;
        if(request.has("native_document")||request.has("navigation")){
            expected=request.getString("native_document");
            if(!(request.get("navigation") instanceof Number)||request.getDouble("navigation")!=epoch||!expected.equals(identity))
                throw new MdoBrowserWorld.Failure("frame_changed");
        }
        String expression="(()=>{try{return {ok:true,value:("+request.getString("script")+")}}catch(e){return {ok:false,error:String(e.message||e)}}})()";
        document.evaluate(expected,expression,Math.min(10000,Math.max(1,request.optLong("timeout_ms",10000))),result->{
            if(!result.ok){reply(lease,id,null,result.code);return;}
            try{JSONObject value=new JSONObject(result.value);value.put("native_document",identity).put("navigation",epoch);reply(lease,id,value,value.optBoolean("ok")?null:value.optString("error","World execution failed"));}
            catch(JSONException error){reply(lease,id,null,"world_result_invalid");}
        });
    }
