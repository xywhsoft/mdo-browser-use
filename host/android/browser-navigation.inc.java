    private static final class NavigationRequest {
        final WebView view;final long epoch;final boolean loading;
        NavigationRequest(WebView view,long epoch,boolean loading){this.view=view;this.epoch=epoch;this.loading=loading;}
    }
    private final Map<String,NavigationRequest> navigationRequests=new HashMap<>();
    private void cancelNavigationRequest(String key,WebView view){
        NavigationRequest pending=navigationRequests.get(key);
        if(pending==null||pending.view!=view||!views.owns(key,view)||pending.epoch!=views.epoch(key,view))return;
        NetworkEntry owner=networkViews.get(key);NativeNavigation operation=owner==null?null:owner.navigation;
        if(operation!=null&&operation.reservation==pending&&!operation.revoked&&operation.ticket!=null&&operation.ticket.result()==null)return;
        navigationRequests.remove(key);loading.put(key,pending.loading);
        MdoBrowserDocument document=documents.get(key);if(document!=null)document.block(MdoBrowserDocument.NAVIGATION,false);
    }
    private void performNavigation(String key,WebView view,String command,String location){
        if(!views.owns(key,view))throw new IllegalStateException("frame_changed");
        if(navigationRequests.containsKey(key))throw new IllegalStateException("navigation_pending");
        if("back".equals(command)&&!view.canGoBack()||"forward".equals(command)&&!view.canGoForward())return;
        if(!Arrays.asList("navigate","back","forward","reload").contains(command))throw new IllegalArgumentException("navigation_action_invalid");
        cancelPopup(key);cancelDialog(key);cancelCapture(key);views.stop(key,view);
        if(!dialogReady(key,view))throw new IllegalStateException("dialog_pending_or_unclean");
        navigationRequests.put(key,new NavigationRequest(view,views.epoch(key,view),Boolean.TRUE.equals(loading.get(key))));
        try{
            loading.put(key,true);
            MdoBrowserDocument document=documents.get(key);if(document!=null)document.block(MdoBrowserDocument.NAVIGATION,true);
            if("navigate".equals(command))view.loadUrl(location);
            else if("back".equals(command))view.goBack();
            else if("forward".equals(command))view.goForward();
            else view.reload();
        }catch(RuntimeException error){cancelNavigationRequest(key,view);throw error;}
    }
