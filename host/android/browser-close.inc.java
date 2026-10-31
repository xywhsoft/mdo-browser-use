    /** Bounded final receipts for the original close, never a second teardown.
     * The UI handler is shared with existing native cleanup observation. */
    private final Map<String,BrowserClose> browserCloses=new HashMap<>();
    private final class BrowserClose {
        final String id;final MdoBrowserChannel<WebMessagePort>.Lease lease;final NetworkEntry network;
        final MdoBrowserDocument document;final MdoBrowserDialogTarget dialog;
        final long born=android.os.SystemClock.elapsedRealtime();final Runnable watch=()->browserClosePoll(this);
        NativeDestruction nativeClose;boolean prepared;
        BrowserClose(String id,MdoBrowserChannel<WebMessagePort>.Lease lease,NetworkEntry network,MdoBrowserDocument document,MdoBrowserDialogTarget dialog){this.id=id;this.lease=lease;this.network=network;this.document=document;this.dialog=dialog;}
    }
    private void executeBrowserClose(String key,String id){
        if(id==null||id.isEmpty()||id.length()>512||browserCloses.containsKey(id)||browserCloses.size()>=32)throw new IllegalStateException("browser_close_busy");
        MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
        if(stopped||lease==null||!channel.active(lease,host.getUrl()))throw new IllegalStateException("browser_close_channel_changed");
        WebView view=views.get(key);NativeDestruction pending=null;
        if(view==null)for(NativeDestruction row:nativeDestructions.values())if(key.equals(row.key)){pending=row;break;}
        NetworkEntry network=networkViews.get(key);WebView exact=view!=null?view:pending==null?null:pending.view;
        if(network!=null&&exact!=null&&network.view!=exact)network=null;
        BrowserClose operation=new BrowserClose(id,lease,network,pending==null?documents.get(key):pending.document,pending==null?dialogs.get(key):pending.dialog);
        try{
            browserCloses.put(id,operation);
            if(view!=null)operation.nativeClose=retireNativeDocument(key,view);
            else if(pending!=null)operation.nativeClose=pending;
            else closeDocument(key);
            // Logical retirement may call into UI callbacks. Do not clear a
            // replacement's metadata if one was registered by such a callback.
            if(views.get(key)==null){manual.remove(key);loading.remove(key);errors.remove(key);errorCodes.remove(key);crashed.remove(key);locations.remove(key);}
            operation.prepared=true;browserClosePoll(operation);
        }catch(RuntimeException|Error failure){browserCloseDone(operation);reply(operation.lease,operation.id,null,"browser_close_unconfirmed");}
    }
    private void browserClosePoll(BrowserClose operation){
        if(browserCloses.get(operation.id)!=operation||!operation.prepared)return;
        networkHandler.removeCallbacks(operation.watch);
        if(stopped||channel.current()!=operation.lease||!channel.active(operation.lease,host.getUrl())){browserCloseDone(operation);return;}
        try{
            NativeDestruction row=operation.nativeClose;NetworkEntry network=operation.network;
            boolean physical=row==null||row.complete;
            boolean networkDone=network==null||network.protocolDrained&&(network.downloads==null||network.downloads.retiringFiles()==0);
            boolean failed=nativeViewCleanupFailed||networkCleanupFailed||row!=null&&row.failed;
            boolean clean=physical&&networkDone&&!failed&&(operation.document==null||operation.document.cleanupConfirmed())&&
                (operation.dialog==null||operation.dialog.dialogs.clean())&&chooserClean(operation.dialog);
            long used=android.os.SystemClock.elapsedRealtime()-operation.born;
            boolean expired=used<0||used>=5000;
            clean=clean&&!expired;
            boolean schedulerFailed=false;
            if(!physical||!networkDone){
                if(!failed&&!expired){if(networkHandler.postDelayed(operation.watch,100))return;schedulerFailed=true;}
            }
            JSONObject result=new JSONObject().put("world_cleanup_confirmed",clean).put("native_view_cleanup_failed",nativeViewCleanupFailed)
                .put("native_view_retiring",nativeRetiringViews.size()).put("network_cleanup_failed",networkCleanupFailed).put("network_retiring",networkRetiring())
                .put("close_observation_expired",!clean&&expired).put("close_observation_failed",schedulerFailed).put("native_view_closed",physical).put("network_cleanup_confirmed",networkDone&&!networkCleanupFailed);
            browserCloseDone(operation);reply(operation.lease,operation.id,result,null);
        }catch(JSONException|RuntimeException|Error failure){browserCloseDone(operation);reply(operation.lease,operation.id,null,"browser_close_unconfirmed");}
    }
    private void browserCloseDone(BrowserClose operation){browserCloses.remove(operation.id,operation);networkHandler.removeCallbacks(operation.watch);}
    private void cancelBrowserCloses(){for(BrowserClose operation:new ArrayList<>(browserCloses.values()))browserCloseDone(operation);}
