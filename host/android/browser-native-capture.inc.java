    private static final class CaptureEntry {
        final WebView view;final MdoBrowserCaptureTarget target;final MdoBrowserCapture capture;String requestKey;
        CaptureEntry(WebView view,MdoBrowserCaptureTarget target){
            this.view=view;this.target=target;capture=new MdoBrowserCapture(target,MdoBrowserCaptureRuntime.scheduler(),
                MdoBrowserCaptureRuntime.WORKER,MdoBrowserCaptureRuntime.BUDGET);
        }
    }
    private final Map<String,CaptureEntry> captures=new HashMap<>();
    private CaptureEntry captureEntry(String key,WebView view,MdoBrowserDocument document){
        CaptureEntry existing=captures.get(key);
        if(existing!=null){if(existing.view!=view)throw new IllegalStateException("capture_view_changed");return existing;}
        MdoBrowserCaptureTarget target=new MdoBrowserCaptureTarget(view,document::identity,()->views.epoch(key,view),()->views.scale(key,view),
            ()->!stopped&&!browserPaused&&views.owns(key,view)&&!manual.contains(key)&&!views.inputBusy(key,view)&&views.inputClean(key,view)&&
                channel.current()!=null&&channel.active(channel.current(),host.getUrl()));
        CaptureEntry entry=new CaptureEntry(view,target);captures.put(key,entry);return entry;
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
        MdoBrowserDocument document=documents.get(key);
        if(document==null)throw new UnsupportedOperationException(documentFailures.getOrDefault(key,"world_unavailable"));
        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        CaptureEntry entry=captureEntry(key,view,document);
        MdoBrowserNativeCapture proof=new MdoBrowserNativeCapture(document,entry.target,
            ()->!stopped&&views.owns(key,view)&&channel.current()==lease&&channel.active(lease,host.getUrl()),request);
        if(entry.capture.busy())throw new IllegalStateException("capture_busy");
        entry.requestKey=proof.captureKey;
        try{entry.capture.capture(proof.plan,proof::check,result->{
            if(proof.captureKey.equals(entry.requestKey))entry.requestKey=null;
            try{
                JSONObject value=new JSONObject().put("ok",result.ok).put("native_document",proof.nativeDocument).put("navigation",proof.navigation)
                    .put("document",proof.document).put("capture_key",proof.captureKey);
                if(result.ok)value.put("data",Base64.encodeToString(result.data,Base64.NO_WRAP)).put("scale",proof.reportedScale).put("format","png");
                else value.put("error",new JSONObject().put("code",result.code));
                reply(lease,id,value,null);
            }catch(JSONException|OutOfMemoryError error){reply(lease,id,null,"capture_result_unavailable");}
        });}catch(MdoBrowserCapture.Failure failure){entry.requestKey=null;throw new IllegalStateException(failure.code);}
    }
    private JSONObject captureState(String key,JSONObject state)throws JSONException {
        CaptureEntry entry=captures.get(key);boolean busy=entry!=null&&entry.capture.busy();
        boolean clean=entry==null||entry.capture.cleanupConfirmed();
        return state.put("capture_capability",documents.containsKey(key)).put("capture_busy",busy).put("capture_cleanup_confirmed",clean)
            .put("capture_cleanup_failed",!busy&&!clean).put("capture_budget_used",MdoBrowserCaptureRuntime.BUDGET.pending());
    }
