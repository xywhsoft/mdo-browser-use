    private boolean openManualChooser(String key,WebView view,ValueCallback<Uri[]> callback,WebChromeClient.FileChooserParams params){
        final MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();final long epoch=views.epoch(key,view);
        if(stopped||browserPaused||!view.isShown()||epoch<=0||original==null||!channel.active(original,host.getUrl())){callback.onReceiveValue(null);return true;}
        return manualChooser.open(view,callback,params,
            ()->!stopped&&views.owns(key,view)&&views.epoch(key,view)==epoch&&view.isShown()&&channel.active(original,host.getUrl()),
            ()->chooserBlocked(key,view,true),()->chooserBlocked(key,view,false));
    }
    Boolean openHostChooser(WebView source,ValueCallback<Uri[]> callback,WebChromeClient.FileChooserParams params){
        if(source!=host||callback==null)return Boolean.FALSE;
        final MdoBrowserChannel<WebMessagePort>.Lease original=channel.current();
        return manualChooser.openHost(host,callback,params,
            ()->!stopped&&original!=null&&channel.active(original,host.getUrl()));
    }
    void manualChooserRestore(android.os.Bundle saved){manualChooser.restore(saved);}
    void manualChooserSave(android.os.Bundle saved){manualChooser.save(saved);}
    boolean manualChooserResult(int request,int result,android.content.Intent data){return manualChooser.result(request,result,data);}
