    std::shared_ptr<xb_popup> popup;std::shared_ptr<xb_view> opener;
    const auto popupId=xb_get(json,"popup");
    if(!popupId.empty()){
      auto source=host->views.find(xb_get(json,"opener"));
      if(source==host->views.end()||source->second->closed||!source->second->environment){host->reply(id,"","Popup opener is no longer available");return;}
      opener=source->second;auto item=opener->popups.find(popupId);
      if(item==opener->popups.end()||item->second->completed||item->second->claimed||GetTickCount64()>=item->second->expires){host->reply(id,"","Popup request is no longer available");return;}
      popup=item->second;
    }
    const auto url=popup?popup->url:xb_get(json,"url");const bool visible=xb_get(json,"visible")!="false";
    if(!xb_url(url)){host->reply(id,"","Only HTTP(S) navigation is supported");return;}
    const bool interceptDownloads=xb_get(json,"intercept_downloads")=="true"||(opener&&opener->downloads&&opener->downloads->armed);
