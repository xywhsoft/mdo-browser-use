    private final Map<String,MdoBrowserDocument> documents=new HashMap<>();
    private final Map<String,String> documentFailures=new HashMap<>();
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
            },()->!stopped&&views.owns(key,view),()->views.epoch(key,view),automation?"mdo.browser-use":null);
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
            .put("pending",document==null?0:document.pending()).put("profile_isolation",document!=null&&document.profileAssigned());
    }
    private void executeDocument(String key,String id,JSONObject request) throws Exception {
        MdoBrowserDocument document=documents.get(key);
        if(document==null)throw new UnsupportedOperationException(documentFailures.getOrDefault(key,"world_unavailable"));
        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        String expression="(()=>{try{return {ok:true,value:("+request.getString("script")+")}}catch(e){return {ok:false,error:String(e.message||e)}}})()";
        document.evaluate(expression,Math.min(10000,Math.max(1,request.optLong("timeout_ms",10000))),result->{
            if(!result.ok){reply(lease,id,null,result.code);return;}
            try{JSONObject value=new JSONObject(result.value);reply(lease,id,value,value.optBoolean("ok")?null:value.optString("error","World execution failed"));}
            catch(JSONException error){reply(lease,id,null,"world_result_invalid");}
        });
    }
