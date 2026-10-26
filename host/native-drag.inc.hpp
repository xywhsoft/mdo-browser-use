/* One bounded native gesture. Drag data comes only from the page's intercepted
 * browser event; callers cannot supply data, files, scripts or protocol names. */
static constexpr UINT_PTR xb_drag_timer=0xBADD4;
struct xb_drag_job;
static std::map<HWND,std::weak_ptr<xb_drag_job>> xb_drag_jobs;
struct xb_drag_job:std::enable_shared_from_this<xb_drag_job>{
  std::shared_ptr<xb_host> host;std::shared_ptr<xb_view> view;
  ICoreWebView2DevToolsProtocolEventReceiver* receiver=nullptr;EventRegistrationToken token{};
  std::string id,key,error,dragData;UINT64 navigation=0;ULONGLONG expires=0,nextAt=0;
  double x=0,y=0,toX=0,toY=0,currentX=0,currentY=0;unsigned phase=0,moved=0,cleanup=0;
  bool done=false,inFlight=false,replied=false,dispatched=false,intercepting=false,pressed=false,dropped=false,subscribed=false,cleanupFailed=false;
  ~xb_drag_job(){if(receiver){if(subscribed)receiver->remove_DevToolsProtocolEventReceived(token);receiver->Release();}}
  bool live()const{return xb_automation_live(host,view,navigation);}
  void reply(){
    if(replied)return;replied=true;
    host->reply(id,"{\"accepted\":"+std::string(error.empty()?"true":"false")+",\"trusted_input\":true,\"dispatched\":"+
      (dispatched?"true":"false")+",\"drag_kind\":"+xb_quote(dragData.empty()?"pointer":"html5")+
      (error.empty()?"":",\"error\":"+xb_quote(error))+"}");
  }
  void detach(bool retain=false){
    done=true;KillTimer(view->window,xb_drag_timer);xb_drag_jobs.erase(view->window);
    if(receiver){if(subscribed)receiver->remove_DevToolsProtocolEventReceived(token);receiver->Release();receiver=nullptr;}
    if(!retain&&view->drag.get()==this)view->drag.reset();
  }
  void close(){if(error.empty())error="Browser was closed during drag; inspect any delivered effects";reply();detach();}
  void stop(const std::string& reason){if(error.empty())error=reason;reply();nextAt=0;}
  std::string point()const{return ",\"x\":"+std::to_string(currentX)+",\"y\":"+std::to_string(currentY);}
  void call(const wchar_t* method,const std::string& params){
    inFlight=true;auto self=shared_from_this();
    xb_cdp(view,method,params,[self](bool ok,const std::string& result){
      self->inFlight=false;if(self->done)return;
      if(!ok||!xb_get(result,"error").empty()){
        if(self->cleanup)self->cleanupFailed=true;
        self->stop("Native drag delivery was not confirmed; inspect before repeating");
      }
      self->nextAt=GetTickCount64()+20;
    });
  }
  void receive(const std::string& json){
    if(done||!error.empty()||!dragData.empty())return;
    if(phase>=8){stop("Native drag data arrived after gesture cleanup; no drop was issued");return;}
    const auto data=xb_get(json,"data"),files=xb_get(data,"files"),items=xb_get(data,"items"),mask=xb_get(data,"dragOperationsMask");
    if(data.empty()||data.size()>65536||(!files.empty()&&files!="[]")||items.empty()||items.front()!='['){stop("File drag or oversized native drag data is not allowed through this gesture");return;}
    unsigned operations=0;try{size_t used=0;auto parsed=std::stoul(mask,&used);if(used!=mask.size()||parsed>19||(parsed&~19ul))throw 0;operations=static_cast<unsigned>(parsed);}catch(...){stop("Invalid native drag operation mask");return;}
    std::string safe;
    for(int n=0;n<17;n++){
      const auto item=webview::detail::json_parse(items,"",n);if(item.empty())break;
      const auto mime=xb_get(item,"mimeType"),value=xb_get(item,"data"),title=xb_get(item,"title"),base=xb_get(item,"baseURL");
      if(n==16||mime.empty()||mime.size()>128||value.size()>16384||title.size()>2048||base.size()>8192){stop("Native drag item exceeds its resource bound");return;}
      if(!safe.empty())safe+=",";safe+="{\"mimeType\":"+xb_quote(mime)+",\"data\":"+xb_quote(value);
      if(!title.empty())safe+=",\"title\":"+xb_quote(title);if(!base.empty())safe+=",\"baseURL\":"+xb_quote(base);safe+="}";
    }
    dragData="{\"items\":["+safe+"],\"dragOperationsMask\":"+std::to_string(operations)+"}";
  }
  void run(){
    if(done||inFlight||GetTickCount64()<nextAt)return;
    if(!live())stop("Browser control or document changed during drag; inspect its effects");
    if(GetTickCount64()>=expires)stop("Native drag deadline expired; inspect its effects");
    if(!error.empty()||phase>=8){
      // Cleanup is attempted once per step; it cannot issue another drop.
      if(cleanup==0){cleanup++;if(!error.empty()||!dropped){call(L"Input.cancelDragging","{}");return;}}
      if(cleanup==1){cleanup++;if(pressed){pressed=false;call(L"Input.dispatchMouseEvent","{\"type\":\"mouseReleased\",\"button\":\"left\",\"buttons\":0,\"clickCount\":1"+point()+"}");return;}}
      if(cleanup==2){cleanup++;if(intercepting){intercepting=false;call(L"Input.setInterceptDrags","{\"enabled\":false}");return;}}
      reply();detach(cleanupFailed);return;
    }
    if(phase==0){phase=1;intercepting=true;call(L"Input.setInterceptDrags","{\"enabled\":true}");return;}
    if(phase==1){phase=2;dispatched=true;currentX=x;currentY=y;call(L"Input.dispatchMouseEvent","{\"type\":\"mouseMoved\""+point()+"}");return;}
    if(phase==2){phase=3;pressed=true;call(L"Input.dispatchMouseEvent","{\"type\":\"mousePressed\",\"button\":\"left\",\"buttons\":1,\"clickCount\":1"+point()+"}");return;}
    if(phase==3){
      if(!dragData.empty()){phase=5;currentX=toX;currentY=toY;}
      else if(moved<16){moved++;currentX=x+(toX-x)*moved/16;currentY=y+(toY-y)*moved/16;
        call(L"Input.dispatchMouseEvent","{\"type\":\"mouseMoved\",\"button\":\"left\",\"buttons\":1"+point()+"}");return;}
      else{phase=4;nextAt=GetTickCount64()+200;return;}
    }
    if(phase==4){if(dragData.empty()){phase=8;run();return;}phase=5;currentX=toX;currentY=toY;}
    if(phase>=5&&phase<=7){
      const char* kind=phase==5?"dragEnter":phase==6?"dragOver":"drop";
      if(phase==7)dropped=true;phase++;
      call(L"Input.dispatchDragEvent","{\"type\":"+xb_quote(kind)+point()+",\"data\":"+dragData+"}");return;
    }
  }
};
static void CALLBACK xb_drag_tick(HWND window,UINT,UINT_PTR,DWORD){
  auto found=xb_drag_jobs.find(window);if(found==xb_drag_jobs.end()){KillTimer(window,xb_drag_timer);return;}
  auto job=found->second.lock();if(!job){xb_drag_jobs.erase(found);KillTimer(window,xb_drag_timer);return;}
  // Deadlines/cancellation are reported even while a renderer callback drains.
  if(!job->live())job->stop("Browser control or document changed during drag; inspect its effects");
  if(GetTickCount64()>=job->expires)job->stop("Native drag deadline expired; inspect its effects");
  job->run();
}
static void xb_close_drag(const std::shared_ptr<xb_view>& view){auto job=view->drag;if(job)job->close();}
static void xb_drag(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,const std::string& id,const std::string& json){
  if(view->drag||!view->input.expired()||view->capture){host->reply(id,"","Another native input or capture is still completing");return;}
  const auto key=xb_get(json,"drag_key");
  double x=xb_capture_number(json,"x",-1),y=xb_capture_number(json,"y",-1),toX=xb_capture_number(json,"to_x",-1),toY=xb_capture_number(json,"to_y",-1),timeout=xb_capture_number(json,"timeout_ms",10000);
  if(key.empty()||key.size()>100||x<0||y<0||toX<0||toY<0||x>16384||y>16384||toX>16384||toY>16384||timeout<1||timeout>60000||xb_get(json,"navigation")!=std::to_string(view->navigation)||!xb_automation_live(host,view,view->navigation)){
    host->reply(id,"","Invalid drag target, document, control or resource budget");return;
  }
  auto job=std::make_shared<xb_drag_job>();job->host=host;job->view=view;job->id=id;job->key=key;job->navigation=view->navigation;
  job->x=x;job->y=y;job->toX=toX;job->toY=toY;job->expires=GetTickCount64()+static_cast<ULONGLONG>(timeout);
  HRESULT received=view->core->GetDevToolsProtocolEventReceiver(L"Input.dragIntercepted",&job->receiver);
  if(FAILED(received)||!job->receiver){host->reply(id,"","This browser does not support native drag interception");return;}
  std::weak_ptr<xb_drag_job> weak=job;
  auto* handler=xb_cb<ICoreWebView2DevToolsProtocolEventReceivedEventHandler,ICoreWebView2*,ICoreWebView2DevToolsProtocolEventReceivedEventArgs*>(
    IID_ICoreWebView2DevToolsProtocolEventReceivedEventHandler,[weak](ICoreWebView2*,ICoreWebView2DevToolsProtocolEventReceivedEventArgs* args){
      if(auto active=weak.lock()){
        xs_wv_text text;if(FAILED(args->get_ParameterObjectAsJson(&text.p))||!text.p){active->stop("Cannot inspect native drag data");return S_OK;}
        size_t size=0;while(size<=65536&&text.p[size])size++;if(size>65536){active->stop("Native drag data exceeds 64 KiB");return S_OK;}
        active->receive(webview::detail::narrow_string(text.p));
      }return S_OK;
    });
  job->subscribed=SUCCEEDED(job->receiver->add_DevToolsProtocolEventReceived(handler,&job->token));handler->Release();
  if(!job->subscribed){job->receiver->Release();job->receiver=nullptr;host->reply(id,"","Cannot subscribe to native drag data");return;}
  view->drag=job;xb_drag_jobs[view->window]=job;
  if(!SetTimer(view->window,xb_drag_timer,20,xb_drag_tick)){job->error="Cannot retain native drag deadline";job->reply();job->detach();return;}
  job->run();
}
