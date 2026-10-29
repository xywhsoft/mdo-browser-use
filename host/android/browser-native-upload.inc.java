    private static final class UploadEntry {
        final WebView view;final MdoBrowserUploadStore.Scope scope;
        UploadEntry(WebView view,long epoch){this.view=view;scope=new MdoBrowserUploadStore.Scope(epoch);}
    }
    private final Map<String,UploadEntry> uploadOwners=new HashMap<>();
    private MdoBrowserUploadQueue uploads;
    private void cancelUpload(String key){UploadEntry entry=uploadOwners.remove(key);if(entry!=null)entry.scope.cancel();}
    private void cancelUpload(String key,WebView expected){UploadEntry entry=uploadOwners.get(key);if(entry!=null&&entry.view==expected)cancelUpload(key);}
    private void cancelUploads(){for(UploadEntry entry:uploadOwners.values())entry.scope.cancel();uploadOwners.clear();}
    private JSONObject uploadState(JSONObject state)throws JSONException {
        return state.put("upload_staging_capability",true).put("upload_staging_ready",uploads!=null&&uploads.ready())
            .put("upload_staging_cleanup_failed",uploads!=null&&uploads.cleanupFailed()).put("upload_staging_files",uploads==null?0:uploads.activeFiles())
            .put("upload_staging_bytes",uploads==null?0:uploads.reservedBytes()).put("upload_capability",false)
            .put("file_input_capability",false).put("file_clear_capability",false).put("directory_upload_capability",false);
    }
    private static long uploadNumber(JSONObject value,String key,long minimum,long maximum)throws JSONException {
        Object raw=value.get(key);
        if(!(raw instanceof Number))throw new IllegalArgumentException("upload_number_invalid");
        double number=((Number)raw).doubleValue();long integer=((Number)raw).longValue();
        if(!Double.isFinite(number)||number!=integer||integer<minimum||integer>maximum)throw new IllegalArgumentException("upload_number_invalid");
        return integer;
    }
    private static String uploadText(JSONObject value,String key)throws JSONException {
        Object raw=value.get(key);if(!(raw instanceof String))throw new IllegalArgumentException("upload_text_invalid");return (String)raw;
    }
    private static void uploadFields(JSONObject request,String... allowed){
        Set<String> names=new HashSet<>(Arrays.asList("channel","id","command","browser","agent"));names.addAll(Arrays.asList(allowed));
        for(Iterator<String> at=request.keys();at.hasNext();)if(!names.contains(at.next()))throw new IllegalArgumentException("upload_field_unsupported");
    }
    private void executeUpload(String key,String id,WebView view,JSONObject request)throws Exception {
        String action=uploadText(request,"action");long epoch=views.epoch(key,view);
        if(!views.owns(key,view)||epoch<=0||stopped||browserPaused||manual.contains(key)||channel.current()==null)throw new IllegalStateException("upload_owner_changed");
        if(!Arrays.asList("begin","chunk","seal","status","cancel","release").contains(action))throw new UnsupportedOperationException("upload_assignment_unsupported");
        // This path receives bytes only. A source ref, frame chain, chooser or
        // directory must not be mistaken for a verified Android grant target.
        MdoBrowserUploadQueue.Request queued;
        if("begin".equals(action)){
            uploadFields(request,"action","upload_key","files","navigation","timeout_ms");
            if(Boolean.TRUE.equals(loading.get(key))||!dialogReady(key,view))throw new IllegalStateException("upload_document_unavailable");
            if(uploadNumber(request,"navigation",1,9007199254740991L)!=epoch)throw new IllegalStateException("upload_owner_changed");
            long timeout=request.has("timeout_ms")?uploadNumber(request,"timeout_ms",1000,120000):60000;
            long now=android.os.SystemClock.elapsedRealtime();if(now<0||now>Long.MAX_VALUE-timeout)throw new IllegalStateException("upload_clock_invalid");
            JSONArray rows=request.getJSONArray("files");if(rows.length()<1||rows.length()>16)throw new IllegalArgumentException("upload_metadata_invalid");
            MdoBrowserUploadStore.Spec[] specs=new MdoBrowserUploadStore.Spec[rows.length()];
            for(int index=0;index<specs.length;index++){
                JSONObject row=rows.getJSONObject(index);for(Iterator<String> fields=row.keys();fields.hasNext();)if(!Arrays.asList("name","size","sha256").contains(fields.next()))throw new IllegalArgumentException("upload_field_unsupported");
                specs[index]=new MdoBrowserUploadStore.Spec(uploadText(row,"name"),uploadNumber(row,"size",0,33554432L),uploadText(row,"sha256"));
            }
            queued=MdoBrowserUploadQueue.Request.begin(uploadText(request,"upload_key"),specs,now+timeout);
        }else if("chunk".equals(action)){
            uploadFields(request,"action","ticket","navigation","file","offset","base64");
            if(uploadNumber(request,"navigation",1,9007199254740991L)!=epoch)throw new IllegalStateException("upload_owner_changed");
            queued=MdoBrowserUploadQueue.Request.chunk(uploadText(request,"ticket"),(int)uploadNumber(request,"file",0,15),uploadNumber(request,"offset",0,33554432L),uploadText(request,"base64"));
        }else{
            uploadFields(request,"action","ticket","navigation","confirmed");
            if(request.has("navigation")&&uploadNumber(request,"navigation",1,9007199254740991L)!=epoch)throw new IllegalStateException("upload_owner_changed");
            if("release".equals(action)&&!Boolean.TRUE.equals(request.opt("confirmed")))throw new IllegalArgumentException("upload_confirmation_required");
            queued=MdoBrowserUploadQueue.Request.ticket(action,uploadText(request,"ticket"));
        }
        UploadEntry entry=uploadOwners.get(key);
        if(entry==null||entry.view!=view||entry.scope.navigation!=epoch||!entry.scope.live()){
            if(entry!=null)entry.scope.cancel();entry=new UploadEntry(view,epoch);uploadOwners.put(key,entry);
        }
        final UploadEntry original=entry;final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        if(uploads==null)uploads=MdoBrowserUploadRuntime.get(activity);
        if(!uploads.submit(entry.scope,queued,outcome->{
            if(!views.owns(key,view)||views.epoch(key,view)!=epoch||uploadOwners.get(key)!=original||!original.scope.live()){
                reply(lease,id,null,"upload_owner_changed");return;
            }
            try{
                MdoBrowserUploadStore.Snapshot value=outcome.value;
                if(outcome.ok()&&value!=null&&!Arrays.asList("status","cancel","release").contains(action)&&android.os.SystemClock.elapsedRealtime()>=value.expires){reply(lease,id,null,"upload_expired");return;}
                JSONObject result=new JSONObject().put("ok",outcome.ok()).put("staged_only",true).put("assignment_supported",false)
                    .put("retained",false).put("effect_may_have_occurred",false).put("staging_may_have_changed",outcome.stagingMayHaveChanged);
                if(!outcome.ok()){
                    JSONObject error=new JSONObject().put("code",outcome.code).put("effect_may_have_occurred",false);
                    if(outcome.cause!=null)error.put("cause_code",outcome.cause);result.put("error",error);
                }else if(value!=null){
                    JSONArray files=new JSONArray();for(int index=0;index<value.files.length;index++){
                        MdoBrowserUploadStore.Spec spec=value.files[index];files.put(new JSONObject().put("name",spec.name).put("size",spec.size).put("sha256",spec.sha256).put("received",value.received[index]));
                    }
                    result.put("ticket",value.ticket).put("upload_key",value.key).put("state",value.state).put("navigation",value.navigation)
                        .put("sealed",value.sealed).put("size",value.size).put("files",files).put("cleanup_confirmed",value.cleanupConfirmed);
                }
                reply(lease,id,result,null);
            }catch(JSONException|RuntimeException|OutOfMemoryError failure){reply(lease,id,null,"upload_result_unconfirmed");}
        }))throw new IllegalStateException("upload_worker_busy");
    }
