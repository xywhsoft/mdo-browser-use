    // UI routing only. Native controller decisions and snapshot reads do no
    // socket/disk work; release waits observe the original opaque receipt.
    private final Map<String,DownloadRelease> downloadReleases=new HashMap<>();
    private final class DownloadRelease {
        final String id;final NetworkEntry entry;MdoBrowserCdpDownloads.Release release;
        final MdoBrowserChannel<WebMessagePort>.Lease lease;final long born=android.os.SystemClock.elapsedRealtime();
        final Runnable watch=()->downloadReleasePoll(this);
        DownloadRelease(String id,NetworkEntry entry,MdoBrowserChannel<WebMessagePort>.Lease lease){
            this.id=id;this.entry=entry;this.lease=lease;
        }
    }
    private JSONArray downloadList(String key)throws JSONException {
        JSONArray result=new JSONArray();NetworkEntry entry=networkViews.get(key);
        if(entry==null||entry.downloads==null)return result;
        try{for(MdoBrowserCdpDownloads.Snapshot row:entry.downloads.list())result.put(row.json());}catch(java.io.IOException ignored){}
        return result;
    }
    private boolean downloadControlReady(String key){
        NetworkEntry entry=networkViews.get(key);return entry!=null&&entry.downloads!=null&&entry.ready&&!entry.retired&&networkCurrent(entry);
    }
    private String downloadFailure(String key){
        NetworkEntry entry=networkViews.get(key);return entry==null||entry.downloads==null||entry.downloads.failure()==null?"":entry.downloads.failure();
    }
    private static long downloadInteger(JSONObject request,String key,long fallback,long minimum,long maximum)throws JSONException {
        if(!request.has(key))return fallback;Object value=request.opt(key);
        if(!(value instanceof Integer)&&!(value instanceof Long))throw new IllegalArgumentException("download_arguments_invalid");
        long number=((Number)value).longValue();if(number<minimum||number>maximum)throw new IllegalArgumentException("download_arguments_invalid");return number;
    }
    private void executeDownloads(String key,String id,WebView view,JSONObject request)throws Exception {
        NetworkEntry entry=networkViews.get(key);
        if(entry==null||entry.view!=view||entry.downloads==null||!views.owns(key,view))throw new IllegalStateException("download_owner_unavailable");
        String action=request.optString("action");
        if(action.equals("list")){reply(id,new JSONObject().put("downloads",downloadList(key)).put("error",downloadFailure(key)),null);return;}
        String identity=request.optString("download");
        if(action.equals("get")){reply(id,entry.downloads.get(identity).json(),null);return;}
        if(action.equals("accept")){
            if(!entry.ready||entry.retired||!networkCurrent(entry))throw new IllegalStateException("download_connection_unavailable");
            long maximum=downloadInteger(request,"max_bytes",33554432,1,67108864),timeout=downloadInteger(request,"timeout_ms",60000,1000,600000);
            reply(id,entry.downloads.accept(identity,maximum,(int)timeout).json(),null);return;
        }
        if(action.equals("cancel")){reply(id,entry.downloads.cancel(identity).json(),null);return;}
        if(action.equals("release")){
            if(id==null||id.isEmpty()||id.length()>512||downloadReleases.containsKey(id)||downloadReleases.size()>=32)throw new IllegalStateException("download_release_busy");
            MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
            if(lease==null||!channel.active(lease,host.getUrl()))throw new IllegalStateException("download_owner_channel_changed");
            // Prepare the watcher before recording an effect, so allocation
            // failure cannot lose the original release association.
            DownloadRelease operation=new DownloadRelease(id,entry,lease);downloadReleases.put(id,operation);
            try{operation.release=entry.downloads.release(identity);}
            catch(Exception|Error failure){downloadReleases.remove(id);throw failure;}
            downloadReleasePoll(operation);return;
        }
        throw new UnsupportedOperationException(action.equals("read")?"download_read_adapter_incomplete":"download_action_unsupported");
    }
    private void downloadReleasePoll(DownloadRelease operation){
        if(downloadReleases.get(operation.id)!=operation)return;
        networkHandler.removeCallbacks(operation.watch);
        if(stopped||channel.current()!=operation.lease||!channel.active(operation.lease,host.getUrl())){downloadReleases.remove(operation.id);return;}
        if(networkViews.get(operation.entry.key)!=operation.entry||!views.owns(operation.entry.key,operation.entry.view)){
            downloadReleases.remove(operation.id);reply(operation.lease,operation.id,null,"download_owner_changed");return;
        }
        try{
            if(operation.release.finished()){
                downloadReleases.remove(operation.id);
                if(operation.release.released())reply(operation.lease,operation.id,new JSONObject().put("ok",true).put("released",true),null);
                else reply(operation.lease,operation.id,null,"download_release_unconfirmed");return;
            }
            long used=android.os.SystemClock.elapsedRealtime()-operation.born;
            if(used<0||used>=5000){downloadReleases.remove(operation.id);reply(operation.lease,operation.id,null,"download_release_unconfirmed");return;}
            if(!networkHandler.postDelayed(operation.watch,20)){downloadReleases.remove(operation.id);reply(operation.lease,operation.id,null,"download_release_unconfirmed");}
        }catch(JSONException|RuntimeException|Error failure){downloadReleases.remove(operation.id);reply(operation.lease,operation.id,null,"download_release_unconfirmed");}
    }
    private void cancelDownloadReleases(){
        for(DownloadRelease operation:new ArrayList<>(downloadReleases.values()))networkHandler.removeCallbacks(operation.watch);downloadReleases.clear();
    }
