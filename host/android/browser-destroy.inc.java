    void pause(){browserPaused=true;cancelPopups();cancelDialogs();blockDocuments(MdoBrowserDocument.BACKGROUND,true);views.background(true);}
    void resume(){browserPaused=false;views.background(false);blockDocuments(MdoBrowserDocument.BACKGROUND,false);}
    void destroy(){
        stopped=true;cdpWorker.close();cdpViews.close();channel.close();if(port!=null)port.close();
        Map<WebView,MdoBrowserPopups<WebView>> popupRetiring=new IdentityHashMap<>();
        for(Map.Entry<String,MdoBrowserPopups<WebView>> entry:windows.entrySet()){
            WebView view=views.get(entry.getKey());entry.getValue().close();if(view!=null)popupRetiring.put(view,entry.getValue());}
        List<MdoBrowserDialogTarget> dialogRetiring=new ArrayList<>(dialogs.values());
        for(MdoBrowserDialogTarget target:dialogRetiring)target.close();
        List<CaptureEntry> captureRetiring=new ArrayList<>(captures.values());
        for(CaptureEntry entry:captureRetiring)entry.capture.close();
        List<WebView> owned=views.values();Map<WebView,MdoBrowserDocument> retiring=new IdentityHashMap<>();
        for(Map.Entry<String,MdoBrowserDocument> entry:documents.entrySet()){WebView view=views.get(entry.getKey());if(view!=null)retiring.put(view,entry.getValue());}
        views.close();
        for(MdoBrowserDocument document:new ArrayList<>(documents.values()))document.close();documents.clear();documentFailures.clear();
        for(WebView web:owned){root.removeView(web);web.destroy();MdoBrowserDocument document=retiring.get(web);if(document!=null)document.viewDestroyed(web);
            MdoBrowserPopups<WebView> popup=popupRetiring.get(web);if(popup!=null)popup.viewDestroyed(web);
            for(MdoBrowserDialogTarget target:dialogRetiring)if(target.view==web)target.viewDestroyed();
            for(CaptureEntry entry:captureRetiring)if(entry.view==web)entry.capture.viewDestroyed();}
        captures.clear();
        dialogs.clear();
        navigationRequests.clear();
        windows.clear();popupLinks.clear();pageClosed.clear();
        manual.clear();loading.clear();errors.clear();errorCodes.clear();crashed.clear();locations.clear();
    }
