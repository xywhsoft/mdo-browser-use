    private boolean nativeViewCleanupFailed;
    private final Set<WebView> nativeRetiringViews=Collections.newSetFromMap(new IdentityHashMap<WebView,Boolean>());
    private final Map<WebView,NativeDestruction> nativeDestructions=new IdentityHashMap<>();
    private boolean bulkNativeDestruction;
    private void nativeCreationAdmission(){
        if(stopped)throw new IllegalStateException("native_browser_closed");
        if(nativeViewCleanupFailed)throw new IllegalStateException("native_view_cleanup_failed");
        if(!nativeRetiringViews.isEmpty())throw new IllegalStateException("native_view_retirement_busy");
        if(networkCleanupFailed)throw new IllegalStateException("network_cleanup_failed");
        if(networkRetiring()!=0||downloadRetiring()!=0)throw new IllegalStateException("native_view_retirement_busy");
    }
    private final Map<String,MdoBrowserDocument> documents=new HashMap<>();
    private final Map<String,String> documentFailures=new HashMap<>();
    private final MdoBrowserWorld.Budget worldBudget=new MdoBrowserWorld.Budget(32);
    private final MdoBrowserCdpWorker cdpWorker=new MdoBrowserCdpWorker();
    private final MdoBrowserCdpRegistry<WebView> cdpViews=new MdoBrowserCdpRegistry<>((view,document,current)->
        new MdoBrowserCdpView(view,owned->owned==view&&current.getAsBoolean(),document).marker(),cdpWorker::cancel);
    private boolean browserPaused;
    // Exact private ownership for the asynchronous frame command route.
    // Product/profile admission is required before requesting this marker.
    private boolean cdpEnvironmentCurrent(String key,WebView view,long epoch,MdoBrowserChannel<WebMessagePort>.Lease original){
        MdoBrowserDialogTarget target=dialogs.get(key);
        return !stopped&&!browserPaused&&views.owns(key,view)&&views.epoch(key,view)==epoch&&view.isShown()&&!manual.contains(key)&&!pageClosed.contains(key)&&
            !loading.getOrDefault(key,true)&&channel.current()==original&&channel.active(original,host.getUrl())&&
            (target==null||target.view==view&&target.dialogs.clean()&&target.dialogs.snapshot()==null)&&
            (!windows.containsKey(key)||windows.get(key).clean()&&windows.get(key).snapshot()==null);
    }
    private MdoBrowserCdpMarker cdpMarker(String key,WebView view){
        final long epoch=views.epoch(key,view);final MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();
        return cdpViews.marker(key,view,epoch,original,()->cdpEnvironmentCurrent(key,view,epoch,original)&&chooserReady(dialogs.get(key)));
    }
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
            // The create route owns the one native retirement attempt. Keep
            // this exact view registered until its failure handler tears down
            // input, document and transfer state together; never destroy here.
            if(automation)throw new UnsupportedOperationException(failure.code);
        }
    }
    private boolean closeDocument(String key){
        WebView networkView=views.get(key);if(networkView!=null)networkRetire(key,networkView,"network_document_closed");
        WebView owned=views.get(key);if(owned!=null)cdpViews.invalidate(key,owned,views.epoch(key,owned),"input_cancelled");
        MdoBrowserPopups<WebView> popup=windows.get(key);if(popup!=null)popup.close();
        navigationRequests.remove(key);
        MdoBrowserDialogTarget dialog=dialogs.get(key);if(dialog!=null)dialog.close();
        CaptureEntry capture=captures.get(key);if(capture!=null)capture.capture.close();
        MdoBrowserDocument document=documents.remove(key);
        documentFailures.remove(key);
        if(document!=null)document.close();
        return !nativeViewCleanupFailed&&nativeRetiringViews.isEmpty()&&networkClean()&&(document==null||document.cleanupConfirmed())&&(dialog==null||dialog.dialogs.clean())&&chooserClean(dialog);
    }
    /** Both single-view and whole-host close use this physical teardown once.
     * Captured resources cannot later be replaced through a reused tab key. */
    private final class NativeDestruction implements MdoBrowserCdpPopupNative.Destruction {
        final WebView view;final MdoBrowserPopups<WebView> popup;final MdoBrowserDialogTarget dialog;final CaptureEntry capture;final MdoBrowserDocument document;
        boolean prepared,running,destroyed,complete,failed,clean,controlled;
        NativeDestruction(WebView view,MdoBrowserPopups<WebView> popup,MdoBrowserDialogTarget dialog,CaptureEntry capture,MdoBrowserDocument document){this.view=view;this.popup=popup;this.dialog=dialog;this.capture=capture;this.document=document;}
        public boolean ready(){return prepared&&!running&&!failed;}
        public void destroy(){
            if(complete||running)return;if(!ready())throw new IllegalStateException("native_view_retirement_busy");running=true;
            try{
                if(!destroyed){root.removeView(view);view.destroy();destroyed=true;}
                retireUploadData(view);
                if(popup!=null)popup.viewDestroyed(view);
                if(dialog!=null&&dialog.view==view)dialog.viewDestroyed();
                if(capture!=null&&capture.view==view)capture.capture.viewDestroyed();
                if(document!=null)document.viewDestroyed(view);
                networkDestroyed(view);
                boolean documentClean=document==null||document.cleanupConfirmed();if(!documentClean)nativeViewCleanupFailed=true;
                clean=documentClean&&networkClean();complete=true;
            }catch(RuntimeException|Error failure){failed=true;nativeViewCleanupFailed=true;throw failure;}
            finally{
                running=false;nativeRetiringViews.remove(view);
                if(complete){
                    try{if(controlled)MdoBrowserCdpPopupNative.activateDestruction(view,this);}catch(java.io.IOException error){nativeViewCleanupFailed=true;}
                    if(!bulkNativeDestruction)nativeDestructions.remove(view,this);
                }
            }
        }
    }
    private NativeDestruction prepareNativeDestruction(WebView view,MdoBrowserPopups<WebView> popup,MdoBrowserDialogTarget dialog,CaptureEntry capture,MdoBrowserDocument document){
        NativeDestruction old=nativeDestructions.get(view);if(old!=null)return old;
        NativeDestruction row=new NativeDestruction(view,popup,dialog,capture,document);nativeDestructions.put(view,row);nativeRetiringViews.add(view);
        row.controlled=MdoBrowserCdpPopupNative.registerDestruction(view,row);return row;
    }
    private boolean executeNativeDestruction(NativeDestruction row){
        if(row.complete)return row.clean;if(row.failed)return false;
        try{if(row.controlled)MdoBrowserCdpPopupNative.activateDestruction(row.view,row);else row.destroy();}
        catch(java.io.IOException error){row.failed=true;nativeViewCleanupFailed=true;nativeRetiringViews.remove(row.view);throw new IllegalStateException("native_view_cleanup_failed",error);}
        return row.complete&&row.clean;
    }
    private boolean closeAndDestroyDocument(String key,WebView view){
        NativeDestruction old=nativeDestructions.get(view);if(old!=null)return old.complete&&old.clean;
        WebView current=views.get(key);if(current!=null&&current!=view)throw new IllegalStateException("native_view_binding_mismatch");
        if(views.nativeDestroyed(view))return !nativeViewCleanupFailed&&networkClean();
        NativeDestruction row=null;
        try{
            row=prepareNativeDestruction(view,windows.get(key),dialogs.get(key),captures.get(key),documents.get(key));
            long epoch=views.epoch(key,view);networkRetire(key,view,"network_view_closed");cancelUpload(key,view);
            if(views.owns(key,view))views.remove(key);cdpViews.invalidate(key,view,epoch,"input_closed");
            windows.remove(key);if(row.popup!=null)row.popup.close();popupLinks.remove(key);pageClosed.remove(key);navigationRequests.remove(key);
            dialogs.remove(key);if(row.dialog!=null)row.dialog.close();captures.remove(key);if(row.capture!=null)row.capture.capture.close();
            documents.remove(key);documentFailures.remove(key);if(row.document!=null)row.document.close();
            row.prepared=true;return executeNativeDestruction(row);
        }catch(RuntimeException|Error failure){nativeViewCleanupFailed=true;if(row!=null){row.failed=true;nativeRetiringViews.remove(view);}throw failure;}
    }
    private void documentNavigation(String key,WebView view){
        if(!views.owns(key,view))return;
        nativeNavigationPageStarted(key,view);
        navigationRequests.remove(key);
        cancelPopup(key);
        cancelDialog(key);
        cancelCapture(key);
        MdoBrowserDocument document=documents.get(key);if(document!=null)document.navigation();
    }
    private void documentLoaded(String key,WebView view){
        if(!views.owns(key,view))return;
        MdoBrowserDocument document=documents.get(key);if(document!=null)document.loaded();
    }
    private void blockDocuments(int reason,boolean value){
        if(value)cancelDownloadReleases();
        if(value)networkRetireAll("network_host_admission_lost");
        if(value)cdpViews.invalidateAll();
        if(value)cancelCaptures();
        for(MdoBrowserDocument document:new ArrayList<>(documents.values()))document.block(reason,value);
    }
    private JSONObject documentState(String key) throws JSONException {
        MdoBrowserDocument document=documents.get(key);
        return new JSONObject().put("installed",document!=null).put("ready",document!=null&&document.ready())
            .put("phase",document==null?documentFailures.getOrDefault(key,"world_unavailable"):document.phase())
            .put("pending",document==null?0:document.pending()).put("native_outstanding",document==null?0:document.outstanding())
            .put("native_budget_used",worldBudget.used()).put("native_budget_limit",worldBudget.limit())
            .put("native_view_cleanup_failed",nativeViewCleanupFailed||!MdoBrowserCdpPopupNative.creationConfirmed()).put("native_view_retiring",nativeRetiringViews.size())
            .put("native_document",document==null||document.identity()==null?JSONObject.NULL:document.identity())
            .put("adapter","android-main-document-v1")
            .put("profile_isolation",document!=null&&document.profileAssigned()).put("network",networkState(key));
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
