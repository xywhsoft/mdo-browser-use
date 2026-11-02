    void pause(){browserPaused=true;cancelPopupControls();cancelPopups();cancelDialogs();blockDocuments(MdoBrowserDocument.BACKGROUND,true);views.background(true);}
    void resume(){browserPaused=false;MdoBrowserCdpPopupNative.reconcileRetirements();views.background(false);blockDocuments(MdoBrowserDocument.BACKGROUND,false);resumePopupNetworks();}
    void destroy(){
        stopped=true;cancelBrowserCloses();
        // Capture and register every exact physical owner before any logical
        // close can synchronously acknowledge a native child transport.
        Map<WebView,MdoBrowserPopups<WebView>> popupRetiring=new IdentityHashMap<>();
        for(Map.Entry<String,MdoBrowserPopups<WebView>> entry:windows.entrySet()){
            WebView view=views.get(entry.getKey());if(view!=null)popupRetiring.put(view,entry.getValue());}
        List<MdoBrowserDialogTarget> dialogRetiring=new ArrayList<>(dialogs.values());
        List<CaptureEntry> captureRetiring=new ArrayList<>(captures.values());
        List<WebView> owned=views.values();Map<WebView,MdoBrowserDocument> retiring=new IdentityHashMap<>();
        for(Map.Entry<String,MdoBrowserDocument> entry:documents.entrySet()){WebView view=views.get(entry.getKey());if(view!=null)retiring.put(view,entry.getValue());}
        List<NativeDestruction> closing=new ArrayList<>();boolean outerBulk=bulkNativeDestruction;bulkNativeDestruction=true;
        try{
            for(WebView web:owned){
                MdoBrowserDialogTarget dialog=null;for(MdoBrowserDialogTarget target:dialogRetiring)if(target.view==web){dialog=target;break;}
                CaptureEntry capture=null;for(CaptureEntry entry:captureRetiring)if(entry.view==web){capture=entry;break;}
                closing.add(prepareNativeDestruction(null,web,popupRetiring.get(web),dialog,capture,retiring.get(web)));
            }
            cancelPopupControls();manualChooser.close();cancelUploads();networkRetireAll("network_host_closed");cdpWorker.close();cdpViews.close();channel.close();if(port!=null)port.close();
            for(MdoBrowserPopups<WebView> popup:new ArrayList<>(windows.values()))popup.close();
            for(MdoBrowserDialogTarget target:dialogRetiring)target.close();
            for(CaptureEntry entry:captureRetiring)entry.capture.close();
            views.close();
            for(MdoBrowserDocument document:new ArrayList<>(documents.values()))document.close();documents.clear();documentFailures.clear();
            captures.clear();dialogs.clear();navigationRequests.clear();windows.clear();popupLinks.clear();pageClosed.clear();
            manual.clear();loading.clear();errors.clear();errorCodes.clear();crashed.clear();locations.clear();
            // A callback may close a sibling. Every row must be prepared before
            // activating even the first physical destructor.
            for(NativeDestruction row:closing)row.prepared=true;
            for(NativeDestruction row:closing)executeNativeDestruction(row);
        }catch(RuntimeException|Error failure){nativeViewCleanupFailed=true;throw failure;}
        finally{
            bulkNativeDestruction=outerBulk;
            if(!bulkNativeDestruction)for(NativeDestruction row:closing)if(row.complete)nativeDestructions.remove(row.view,row);
        }
    }
