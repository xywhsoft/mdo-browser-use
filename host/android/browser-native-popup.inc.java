    private static final class PopupLink {
        final WebView view;final String opener,id;boolean ready;
        PopupLink(WebView view,String opener,String id){this.view=view;this.opener=opener;this.id=id;}
    }
    private final Map<String,MdoBrowserPopups<WebView>> windows=new HashMap<>();
    private final Map<String,PopupLink> popupLinks=new HashMap<>();
    private final Set<String> pageClosed=new HashSet<>();
    private final MdoBrowserPopups.Budget popupBudget=new MdoBrowserPopups.Budget(8);
    private MdoBrowserWindowTarget.Windows windowCallbacks(String key,WebView view){
        if(windows.containsKey(key))throw new IllegalStateException("Popup view identity is already bound");
        final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
        final MdoBrowserPopups<WebView> core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
            public long now(){return android.os.SystemClock.uptimeMillis();}
            public void at(Runnable task,long uptime){if(!handler.postAtTime(task,uptime))throw new IllegalStateException("Popup scheduler stopped");}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        },popupBudget,()->!stopped&&!browserPaused&&!pageClosed.contains(key)&&views.owns(key,view)&&view.isShown()&&
            channel.current()!=null&&channel.active(channel.current(),host.getUrl()),()->views.epoch(key,view),blocked->{
                MdoBrowserPopups<WebView> binding=windows.get(key);
                MdoBrowserDocument document=documents.get(key);if(document!=null)document.block(MdoBrowserDocument.POPUP,blocked||binding!=null&&!binding.clean());
                if(blocked){cancelCapture(key);if(views.owns(key,view))views.input(key,view).invalidate("popup_pending");}
            });
        windows.put(key,core);
        return new MdoBrowserWindowTarget.Windows(){
            public boolean create(WebView source,boolean userGesture,MdoBrowserPopups.Transport<WebView> transport){
                if(source!=view||!views.owns(key,view))return false;
                boolean accepted=core.open(transport,userGesture);
                if(accepted)notifyPopup(key);
                return accepted;
            }
            public void close(WebView source){
                if(source!=view||!views.owns(key,view))return;
                pageClosed.add(key);
                try{networkRetire(key,view,"network_page_closed");}catch(RuntimeException|Error failure){networkCleanupFailed=true;}
                core.close();cancelDialog(key);cancelCapture(key);views.stop(key,view);
                MdoBrowserDocument document=documents.get(key);if(document!=null)document.block(MdoBrowserDocument.POPUP,true);
                view.stopLoading();views.visibility(key,view,false);view.setVisibility(View.INVISIBLE);notifyPopup(key);
            }
        };
    }
    private void notifyPopup(String key){
        if(stopped||port==null||!channel.owns(channel.current(),port,host.getUrl()))return;
        try{port.postMessage(new WebMessage(new JSONObject().put("channel","mdo-browser-popup").put("browser",key).toString()));}
        catch(RuntimeException|JSONException|OutOfMemoryError ignored){/* The trusted manager also scans retained native state. */}
    }
    private void cancelPopup(String key){MdoBrowserPopups<WebView> core=windows.get(key);if(core!=null)core.cancel();}
    private void cancelPopups(){for(MdoBrowserPopups<WebView> core:new ArrayList<>(windows.values()))core.cancel();}
    private JSONObject popupState(String key,JSONObject state)throws JSONException {
        MdoBrowserPopups<WebView> core=windows.get(key);PopupLink link=popupLinks.get(key);boolean requested=pageClosed.contains(key);
        state.put("popup_capability",core!=null&&!pageClosed.contains(key)).put("popup_pending",core!=null&&core.busy())
            .put("popup_cleanup_failed",core!=null&&!core.clean()).put("popup_budget_used",popupBudget.used())
            .put("popup_budget_limit",popupBudget.limit).put("close_requested",requested)
            .put("closed_by_page",requested&&downloadPageCloseReady(key));
        if(link!=null)state.put("opener",link.opener).put("popup",link.id).put("popup_attached",link.ready);
        return state;
    }
    private void executePopups(String key,String id,JSONObject request)throws JSONException {
        MdoBrowserPopups<WebView> core=windows.get(key);if(core==null)throw new IllegalStateException("popup_unavailable");
        if("list".equals(request.optString("action"))){
            JSONArray rows=new JSONArray();MdoBrowserPopups.Snapshot pending=core.snapshot();
            // WebChromeClient supplies no trustworthy URL/frame. The placeholder
            // is admission metadata only; create must transfer original contents.
            if(pending!=null)rows.put(new JSONObject().put("id",pending.id).put("url","about:blank").put("url_known",false)
                .put("user_initiated",pending.userInitiated).put("navigation",pending.epoch));
            reply(id,new JSONObject().put("popups",rows),null);return;
        }
        if(!"dismiss".equals(request.optString("action")))throw new IllegalArgumentException("popup_action_invalid");
        reply(id,new JSONObject().put("dismissed",core.dismiss(request.optString("popup"))),null);
    }
