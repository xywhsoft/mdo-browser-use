    private static final class CaptureEntry {
        final WebView view;final MdoBrowserCaptureTarget target;final MdoBrowserCapture capture;final CaptureIdentity identity;String requestKey;
        CaptureEntry(WebView view,MdoBrowserCaptureTarget target,CaptureIdentity identity){
            this.view=view;this.target=target;this.identity=identity;capture=new MdoBrowserCapture(target,MdoBrowserCaptureRuntime.scheduler(),
                MdoBrowserCaptureRuntime.WORKER,MdoBrowserCaptureRuntime.BUDGET);
        }
    }
    private static final class CaptureIdentity {String document;}
    private final Map<String,CaptureEntry> captures=new HashMap<>();
    private boolean captureMutationReady(String key,WebView view){
        CaptureEntry entry=captures.get(key);
        return entry==null||entry.view==view&&!entry.capture.busy()&&entry.capture.cleanupConfirmed();
    }
    private CaptureEntry captureEntry(String key,WebView view){
        CaptureEntry existing=captures.get(key);
        if(existing!=null){if(existing.view!=view)throw new IllegalStateException("capture_view_changed");return existing;}
        CaptureIdentity identity=new CaptureIdentity();
        MdoBrowserCaptureTarget target=new MdoBrowserCaptureTarget(view,()->identity.document,()->views.epoch(key,view),()->views.scale(key,view),
            ()->!stopped&&!browserPaused&&views.owns(key,view)&&dialogReady(key,view)&&!manual.contains(key)&&!views.inputBusy(key,view)&&views.inputClean(key,view)&&
                channel.current()!=null&&channel.active(channel.current(),host.getUrl()));
        CaptureEntry entry=new CaptureEntry(view,target,identity);captures.put(key,entry);return entry;
    }
    private void cancelCapture(String key){CaptureEntry entry=captures.get(key);if(entry!=null)entry.capture.cancel();}
    private void cancelCaptures(){for(CaptureEntry entry:new ArrayList<>(captures.values()))entry.capture.cancel();}
    private void executeCaptureCancel(String key,String id,JSONObject request)throws JSONException {
        CaptureEntry entry=captures.get(key);Object original=request.opt("capture_key");
        boolean matches=entry!=null&&original instanceof String&&original.equals(entry.requestKey);
        if(matches)entry.capture.cancel();
        reply(id,new JSONObject().put("cancelled",matches).put("pending_native",entry!=null&&entry.capture.busy()),null);
    }
    private void executeNativeCapture(String key,String id,WebView view,JSONObject request)throws Exception {
        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();final long epoch=views.epoch(key,view);
        MdoBrowserCdpCapture operation=MdoBrowserCdpCapture.parse(request);CaptureEntry entry=captureEntry(key,view);
        if(entry.capture.busy())throw new IllegalStateException("capture_busy");
        if(!entry.capture.cleanupConfirmed())throw new IllegalStateException("capture_cleanup_failed");
        entry.identity.document=operation.nativeDocument;
        MdoBrowserNativeCapture proof=new MdoBrowserNativeCapture(operation,entry.target,cdpWorker,cdpMarker(key,view),
            ()->!stopped&&views.owns(key,view)&&views.epoch(key,view)==epoch&&channel.current()==lease&&channel.active(lease,host.getUrl()));
        entry.requestKey=proof.captureKey;
        try{entry.capture.capture(proof.plan,proof::check,result->{
            if(proof.captureKey.equals(entry.requestKey))entry.requestKey=null;proof.close();
            try{
                JSONObject value=new JSONObject().put("ok",result.ok).put("native_document",proof.nativeDocument).put("navigation",proof.navigation)
                    .put("document",proof.document).put("capture_key",proof.captureKey);
                if(result.ok)value.put("data",Base64.encodeToString(result.data,Base64.NO_WRAP)).put("scale",proof.reportedScale).put("format","png");
                else value.put("error",new JSONObject().put("code",result.code));
                reply(lease,id,value,null);
            }catch(JSONException|OutOfMemoryError error){reply(lease,id,null,"capture_result_unavailable");}
            finally{if(result.data!=null)java.util.Arrays.fill(result.data,(byte)0);}
        });}catch(MdoBrowserCapture.Failure|RuntimeException|Error failure){entry.requestKey=null;proof.close();throw failure;}
    }
    private JSONObject captureState(String key,JSONObject state)throws JSONException {
        CaptureEntry entry=captures.get(key);boolean busy=entry!=null&&entry.capture.busy();
        boolean clean=entry==null||entry.capture.cleanupConfirmed();
        return dialogState(key,state).put("capture_capability",views.get(key)!=null).put("capture_busy",busy).put("capture_cleanup_confirmed",clean)
            .put("capture_cleanup_failed",!busy&&!clean).put("capture_budget_used",MdoBrowserCaptureRuntime.BUDGET.pending());
    }
