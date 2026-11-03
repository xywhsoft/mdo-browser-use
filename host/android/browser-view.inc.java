    /** Private coordinator seam. A configured child is not yet published,
     * navigated, granted a document world or sent to its native transport. */
    private MdoBrowserCdpPopupNative.Created constructProtocolPopup(MdoBrowserCdpPopupNative source,
            MdoBrowserCdpPopupWorker.Allocation allocation,String key)throws java.io.IOException {
        nativeOwnerAdmission();
        if(source==null)throw new java.io.IOException("cdp_popup_native_source_invalid");
        final WebView opener=source.opener();String found=null;
        for(Map.Entry<String,MdoBrowserDocument> item:documents.entrySet())if(views.owns(item.getKey(),opener)&&item.getValue().profileAssigned()){found=item.getKey();break;}
        if(found==null)throw new java.io.IOException("browser_profile_source_invalid");
        final String parentKey=found;final MdoBrowserDocument parent=documents.get(parentKey);final MdoBrowserChannel<WebMessagePort>.Lease originalChannel=channel.current();
        MdoBrowserCdpPopupNative.Created created=source.construct(allocation,views,key,(exactKey,exactView)->{
            WebView current=views.get(exactKey);
            if(current!=null&&current!=exactView)throw new IllegalStateException("popup_binding_mismatch");
            NativeDestruction row=retireNativeDocument(exactKey,exactView);
            if(nativeViewCleanupFailed||row!=null&&row.failed)throw new IllegalStateException("native_view_cleanup_failed");
        });
        try{
            WebView view=created.view();
            MdoBrowserDocument document=prepareDocument(key,view);
            java.util.function.BooleanSupplier admitted=()->!stopped&&documents.get(parentKey)==parent&&parent.profileAssigned()&&views.owns(parentKey,opener)&&source.current()&&channel.current()==originalChannel&&channel.active(originalChannel,host.getUrl());
            document.initializeInheritedProfile(WebView.getWebViewClassLoader(),parent,opener,admitted);
            if(!admitted.getAsBoolean()||documents.get(key)!=document||created.view()!=view)throw new IllegalStateException("popup_binding_mismatch");
            documentFailures.put(key,"popup_world_unqualified");
            configureBrowserView(key,view);
            if(!admitted.getAsBoolean()||documents.get(key)!=document||created.view()!=view)throw new IllegalStateException("popup_binding_mismatch");
            return created;
        }catch(MdoBrowserWorld.Failure failure){
            try{created.close();}catch(java.io.IOException cleanup){java.io.IOException combined=new java.io.IOException("native_view_cleanup_failed",failure);combined.addSuppressed(cleanup);throw combined;}
            throw new java.io.IOException(failure.code);
        }catch(RuntimeException|Error failure){
            try{created.close();}
            catch(java.io.IOException cleanup){
                IllegalStateException combined=new IllegalStateException("native_view_cleanup_failed",failure);
                combined.addSuppressed(cleanup);throw combined;
            }
            throw failure;
        }
    }
    /** Native ownership handoff only. The complete coordinator must separately
     * transfer the protected original protocol session and bootstrap a world. */
    private MdoBrowserCdpPopupNative.Promotion promoteProtocolPopup(String key,MdoBrowserCdpPopupNative source,
            MdoBrowserCdpPopupNative.Created original,MdoBrowserCdpPopupNative.Child proof)throws java.io.IOException {
        return transferProtocolPopup(key,source,original,proof,false);
    }
    /** Retain the same protected paused Browser attachment independently of
     * the opener. No debugger resume, request continuation or world bootstrap. */
    private MdoBrowserCdpPopupNative.Promotion handoffProtocolPopup(String key,MdoBrowserCdpPopupNative source,
            MdoBrowserCdpPopupNative.Created original,MdoBrowserCdpPopupNative.Child proof)throws java.io.IOException {
        return transferProtocolPopup(key,source,original,proof,true);
    }
    /** Queue fixed debugger/request flow only for the committed native owner.
     * This neither publishes a tab nor grants document/world/body access. */
    private void activateProtocolPopup(MdoBrowserCdpPopupNative.Promotion owner,MdoBrowserCdpPopupBroker.Handoff handoff)throws java.io.IOException {
        nativeOwnerAdmission();if(handoff==null||!handoff.owns(owner)||!owner.current())throw new java.io.IOException("cdp_popup_activation_unavailable");handoff.activate();
    }
    private MdoBrowserCdpMarker protocolDocumentMarker(String key,MdoBrowserCdpPopupNative.Promotion owner,MdoBrowserCdpPopupBroker.Handoff handoff){
        nativeOwnerAdmission();if(owner==null||handoff==null||!handoff.owns(owner)||!handoff.current())throw new IllegalStateException("cdp_popup_document_owner_invalid");
        final WebView exact=owner.view();final long epoch=views.epoch(key,exact);if(!views.owns(key,exact)||epoch<=0)throw new IllegalStateException("cdp_popup_document_owner_invalid");
        return cdpViews.marker(key,exact,epoch,handoff,()->!stopped&&!browserPaused&&!pageClosed.contains(key)&&views.owns(key,exact)&&views.epoch(key,exact)==epoch&&handoff.current());
    }
    private MdoBrowserCdpView protocolSourceAdapter(String key,MdoBrowserCdpPopupNative.Promotion owner,MdoBrowserCdpPopupBroker.Handoff handoff){
        nativeOwnerAdmission();if(owner==null||handoff==null||!handoff.owns(owner)||!handoff.current())throw new IllegalStateException("cdp_popup_source_owner_invalid");
        final WebView exact=owner.view();if(!views.owns(key,exact))throw new IllegalStateException("cdp_popup_source_owner_invalid");
        return MdoBrowserCdpView.network(exact,v->v==exact&&!stopped&&!browserPaused&&!pageClosed.contains(key)&&views.owns(key,exact)&&handoff.current(),new MdoBrowserCdpMarker.DocumentQuota());
    }
    private MdoBrowserCdpPopupNative.Promotion transferProtocolPopup(String key,MdoBrowserCdpPopupNative source,
            MdoBrowserCdpPopupNative.Created original,MdoBrowserCdpPopupNative.Child proof,boolean protectedAttachment)throws java.io.IOException {
        nativeOwnerAdmission();
        if(source==null||original==null)throw new java.io.IOException("cdp_popup_native_promotion_unavailable");
        WebView exact;try{exact=original.view();}catch(IllegalStateException stale){throw new java.io.IOException("cdp_popup_native_promotion_unavailable");}
        if(!views.owns(key,exact)||popupLinks.containsKey(key))throw new java.io.IOException("cdp_popup_native_promotion_unavailable");
        MdoBrowserCdpPopupNative.Promotion promoted=protectedAttachment?source.handoff(original,proof):source.promote(original,proof);
        try{if(promoted.view()!=exact||!views.owns(key,exact))throw new IllegalStateException("popup_binding_mismatch");return promoted;}
        catch(RuntimeException|Error failure){promoted.close();throw failure;}
    }
    /** Common native policy/listeners, with no navigation or UI publication. */
    private static final class InitialScaleCallback extends WebView.VisualStateCallback {
        private final MdoBrowserLifecycle.InitialScale<WebView> original;
        InitialScaleCallback(MdoBrowserLifecycle.InitialScale<WebView> original){this.original=original;}
        @Override public void onComplete(long id){
            if(id!=original.epoch)original.cancel();
            MdoBrowserLifecycle.InitialScale<WebView> next=original.complete(WebView::getScale);
            if(next!=null)postInitialScale(next.currentView(),next);
        }
    }
    static void postInitialScale(WebView view,MdoBrowserLifecycle.InitialScale<WebView> original){
        if(original==null||!original.submit(view))return;
        try{view.postVisualStateCallback(original.epoch,new InitialScaleCallback(original));}
        catch(RuntimeException failure){original.cancel();}
    }
    private void configureBrowserView(String key,WebView web){
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setSupportMultipleWindows(true);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        bindDialogs(key,web);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onScaleChanged(WebView view,float oldScale,float newScale){if(views.owns(key,view))cancelCapture(key);views.scaleChanged(key,view,newScale);}
            @Override public void onPageStarted(WebView view,String address,Bitmap icon){if(!views.owns(key,view))return;documentNavigation(key,view);if(!views.navigation(key,view))return;loading.put(key,true);errors.remove(key);errorCodes.remove(key);locations.put(key,address);networkNavigation(key,view,address);}
            @Override public void onPageFinished(WebView view,String address){if(views.owns(key,view)){documentLoaded(key,view);if(!networkLoaded(key,view,address))loading.put(key,false);postInitialScale(view,views.initialScale(key,view));}}
            @Override public void onReceivedError(WebView view,WebResourceRequest navigation,WebResourceError error){
                if(views.owns(key,view)&&navigation.isForMainFrame()){cancelNavigationRequest(key,view);loading.put(key,false);errors.put(key,error.getDescription().toString());errorCodes.put(key,error.getErrorCode());}
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();if(!views.owns(key,view))return;cancelNavigationRequest(key,view);loading.put(key,false);errors.put(key,"TLS certificate validation failed");errorCodes.put(key,error.getPrimaryError());}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest navigation,WebResourceResponse response){
                if(views.owns(key,view)&&navigation.isForMainFrame())errorCodes.put(key,response.getStatusCode());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest navigation){
                String address=navigation.getUrl().toString();if("about:blank".equals(address))return false;
                try{url(address);return false;}catch(IllegalArgumentException rejected){return true;}
            }
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                if(!views.owns(key,view))return true;manual.remove(key);loading.put(key,false);errors.put(key,"Browser process stopped; reload to restore the page");crashed.add(key);closeAndDestroyDocument(key,view);return true;
            }
        });
    }
