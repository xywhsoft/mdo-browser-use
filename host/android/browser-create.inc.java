            if("create".equals(command)) {
                if(web==null)nativeCreationAdmission();else nativeOwnerAdmission();
                final boolean networkRequired=request.optBoolean("automation")||request.optBoolean("intercept_downloads");
                if(web!=null){
                    if(!views.owns(key,web))throw new IllegalStateException("native_view_owner_changed");
                    networkAttach(key,web,networkRequired);
                    PopupLink link=popupLinks.get(key);
                    if(request.has("popup")&&(link==null||link.view!=web||!link.id.equals(request.optString("popup"))||!link.opener.equals(request.optString("opener"))))
                        throw new IllegalStateException("popup_binding_mismatch");
                    if(link!=null&&!link.ready)throw new IllegalStateException("popup_transport_unconfirmed");
                    bounds(web,request);reply(id,new JSONObject().put("attached",true),null);return;
                }
                if(!MdoBrowserCdpPopupNative.creationConfirmed())throw new IllegalStateException("native_popup_creation_unconfirmed");
                final String popupId=request.optString("popup"),opener=request.optString("opener");
                final MdoBrowserPopups<WebView> popupSource=popupId.isEmpty()?null:windows.get(opener);
                if(!popupId.isEmpty()){
                    MdoBrowserPopups.Snapshot pending=popupSource==null?null:popupSource.snapshot();
                    if(opener.equals(key)||views.get(opener)==null||pending==null||!pending.id.equals(popupId))throw new IllegalStateException("popup_request_unavailable");
                }else if(!opener.isEmpty())throw new IllegalArgumentException("popup_binding_mismatch");
                if(popupSource!=null&&networkRequired)throw new UnsupportedOperationException("popup_network_unqualified");
                views.checkNew(key);
                String target=popupSource==null?request.optString("url","about:blank"):"about:blank";if(!"about:blank".equals(target))target=url(target);
                web=new WebView(activity);final WebView createdView=web;
                try{
                    final WebView exactOpener=popupSource==null?null:views.get(opener);
                    final MdoBrowserLifecycle.Fresh<WebView> fresh=popupSource==null?null:views.putPopup(key,exactOpener,web);
                    if(fresh==null)views.put(key,web);
                    if(popupSource==null)bindDocument(key,web,networkRequired);
                    else documentFailures.put(key,"popup_world_unqualified");
                    configureBrowserView(key,web);
                    crashed.remove(key);pageClosed.remove(key);locations.put(key,target);root.addView(web);bounds(web,request);loading.put(key,true);
                    if(popupSource!=null){
                        final WebView freshChild=fresh.claim(exactOpener);
                        final PopupLink link=new PopupLink(web,opener,popupId);popupLinks.put(key,link);
                        final MdoBrowserChannel<WebMessagePort>.Lease lease=channel.current();
                        popupSource.adopt(popupId,freshChild,(ok,code,mayHaveOccurred)->{
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
