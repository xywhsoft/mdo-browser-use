    void pause(){browserPaused=true;cancelDialogs();blockDocuments(MdoBrowserDocument.BACKGROUND,true);views.background(true);}
    void resume(){browserPaused=false;views.background(false);blockDocuments(MdoBrowserDocument.BACKGROUND,false);}
    void destroy(){
        stopped=true;channel.close();if(port!=null)port.close();
        List<MdoBrowserDialogTarget> dialogRetiring=new ArrayList<>(dialogs.values());
        for(MdoBrowserDialogTarget target:dialogRetiring)target.close();
        List<CaptureEntry> captureRetiring=new ArrayList<>(captures.values());
        for(CaptureEntry entry:captureRetiring)entry.capture.close();
        List<WebView> owned=views.values();Map<WebView,MdoBrowserDocument> retiring=new IdentityHashMap<>();
        for(Map.Entry<String,MdoBrowserDocument> entry:documents.entrySet()){WebView view=views.get(entry.getKey());if(view!=null)retiring.put(view,entry.getValue());}
        views.close();
        for(MdoBrowserDocument document:new ArrayList<>(documents.values()))document.close();documents.clear();documentFailures.clear();
        for(WebView web:owned){root.removeView(web);web.destroy();MdoBrowserDocument document=retiring.get(web);if(document!=null)document.viewDestroyed(web);
            for(MdoBrowserDialogTarget target:dialogRetiring)if(target.view==web)target.viewDestroyed();
            for(CaptureEntry entry:captureRetiring)if(entry.view==web)entry.capture.viewDestroyed();}
        captures.clear();
        dialogs.clear();
        navigationRequests.clear();
        manual.clear();loading.clear();errors.clear();errorCodes.clear();crashed.clear();locations.clear();
    }
