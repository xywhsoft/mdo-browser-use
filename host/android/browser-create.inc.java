            if("create".equals(command)) {
                nativeCreationAdmission();
                final boolean networkRequired=request.optBoolean("automation")||request.optBoolean("intercept_downloads");
                if(web!=null){
                    networkAttach(key,web,networkRequired);
                    PopupLink link=popupLinks.get(key);
                    if(request.has("popup")&&(link==null||link.view!=web||!link.id.equals(request.optString("popup"))||!link.opener.equals(request.optString("opener"))))
                        throw new IllegalStateException("popup_binding_mismatch");
                    if(link!=null&&!link.ready)throw new IllegalStateException("popup_transport_unconfirmed");
                    bounds(web,request);reply(id,new JSONObject().put("attached",true),null);return;
                }
                final String popupId=request.optString("popup"),opener=request.optString("opener");
                final MdoBrowserPopups<WebView> popupSource=popupId.isEmpty()?null:windows.get(opener);
                if(!popupId.isEmpty()){
                    MdoBrowserPopups.Snapshot pending=popupSource==null?null:popupSource.snapshot();
                    if(opener.equals(key)||pending==null||!pending.id.equals(popupId))throw new IllegalStateException("popup_request_unavailable");
                }else if(!opener.isEmpty())throw new IllegalArgumentException("popup_binding_mismatch");
                if(popupSource!=null&&networkRequired)throw new UnsupportedOperationException("popup_network_unqualified");
                if(views.size()>=8)throw new IllegalStateException("Native browser view limit reached (8)");
                String target=popupSource==null?request.optString("url","about:blank"):"about:blank";if(!"about:blank".equals(target))target=url(target);
                web=new WebView(activity);final WebView createdView=web;
                try{
                    views.put(key,web);
                    if(popupSource==null)bindDocument(key,web,networkRequired);
                    else documentFailures.put(key,"popup_world_unqualified");
                    web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
                    web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
                    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                    web.getSettings().setSupportMultipleWindows(true);
                    web.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
                    bindDialogs(key,web);
                    web.setWebViewClient(new WebViewClient(){
                        @Override public void onScaleChanged(WebView view,float oldScale,float newScale){if(views.owns(key,view))cancelCapture(key);views.scaleChanged(key,view,newScale);}
                        @Override public void onPageStarted(WebView view,String address,Bitmap icon){if(!views.owns(key,view))return;documentNavigation(key,view);if(!views.navigation(key,view))return;loading.put(key,true);errors.remove(key);errorCodes.remove(key);locations.put(key,address);networkNavigation(key,view,address);}
                        @Override public void onPageFinished(WebView view,String address){if(views.owns(key,view)){documentLoaded(key,view);if(!networkLoaded(key,view,address))loading.put(key,false);}}
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
                    crashed.remove(key);pageClosed.remove(key);locations.put(key,target);root.addView(web);bounds(web,request);loading.put(key,true);
                    if(popupSource!=null){
                        final PopupLink link=new PopupLink(web,opener,popupId);popupLinks.put(key,link);
                        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
                        popupSource.adopt(popupId,web,(ok,code,mayHaveOccurred)->{
                            if(!views.owns(key,createdView)||popupLinks.get(key)!=link){reply(lease,id,null,"popup_binding_lost");return;}
                            if(!ok){pageClosed.add(key);createdView.setVisibility(View.INVISIBLE);notifyPopup(key);reply(lease,id,null,code);return;}
                            link.ready=true;
                            try{reply(lease,id,new JSONObject().put("created",true).put("popup_attached",true),null);}
                            catch(JSONException error){reply(lease,id,null,"popup_result_invalid");}
                        });
                    }else if(networkRequired){networkBegin(key,web,id,target);}
                    else{web.loadUrl(target);reply(id,new JSONObject().put("created",true),null);}
                }catch(RuntimeException|JSONException|OutOfMemoryError failure){
                    // Once a transport may have been sent, preserve its exact
                    // child until consumption/explicit close. Never reload a URL.
                    if(!popupLinks.containsKey(key)){
                        try{if(!closeAndDestroyDocument(key,createdView))throw new IllegalStateException("native_view_cleanup_failed");}
                        catch(RuntimeException cleanup){
                            IllegalStateException combined=new IllegalStateException("native_view_cleanup_failed",failure);
                            combined.addSuppressed(cleanup);throw combined;
                        }
                    }
                    throw failure;
                }
                return;
            }
