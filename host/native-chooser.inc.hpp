/* Private interception. No selected paths or raw protocol IDs cross the public
 * boundary. Suppressed pickers are represented by one view/document ticket. */
static constexpr UINT_PTR xb_chooser_timer=0xBADD6;
struct xb_chooser_owner{std::weak_ptr<xb_host> host;std::weak_ptr<xb_view> view;};
static std::map<HWND,xb_chooser_owner> xb_chooser_views;
static void xb_chooser_maintain(const std::shared_ptr<xb_host>&,const std::shared_ptr<xb_view>&);
static bool xb_chooser_blocked(const std::shared_ptr<xb_view>& view){auto state=view->chooser;return state&&(state->pending||state->changing||state->policyFailed||state->objectFailed);}
struct xb_chooser_operation:std::enable_shared_from_this<xb_chooser_operation>{
  std::shared_ptr<xb_host> host;std::shared_ptr<xb_view> view;std::shared_ptr<xb_chooser_state> state;std::shared_ptr<xb_chooser_pending> pending;
  std::string id,kind,group,error,document;UINT64 navigation=0;uint64_t epoch=0;ULONGLONG deadline=0;
  bool done=false,stopped=false,inFlight=false,replied=false,dispatched=false;
  void releaseGroup()noexcept{if(group.empty()||view->closed||!host->alive)return;try{std::weak_ptr<xb_chooser_state> weak=state;
    xb_cdp(view,L"Runtime.releaseObjectGroup","{\"objectGroup\":"+xb_quote(group)+"}",[weak](bool ok,const std::string& result){if(!ok||!xb_get(result,"error").empty())if(auto active=weak.lock())active->objectFailed=true;});
  }catch(...){state->objectFailed=true;}}
  ~xb_chooser_operation(){if(!done){if(state->epoch==epoch){state->changing=false;state->operation.reset();}if(kind=="arm"||kind=="disarm")state->policyFailed=true;releaseGroup();}}
  bool live()const{return !done&&!stopped&&host->alive&&!view->closed&&!view->failed&&GetTickCount64()<deadline&&
    ((kind=="disarm")||view->navigation==navigation)&&((kind=="cancel"||kind=="disarm")||!view->manual)&&
    (kind!="arm"||state->wanted)&&(kind!="target"||state->pending==pending);}
  void reply(const std::string& result){if(replied)return;replied=true;if(!id.empty())host->reply(id,result);}
  std::string failure(const std::string& message)const{return "{\"ok\":false,\"error\":{\"code\":"+xb_quote(dispatched?"action_uncertain":"chooser_unavailable")+",\"message\":"+xb_quote(message)+",\"effect_may_have_occurred\":"+(dispatched?"true":"false")+"}}";}
  void finish(const std::string& result){if(done)return;reply(result);done=true;if(state->epoch==epoch){state->changing=false;state->operation.reset();}releaseGroup();}
  void stop(const std::string& reason){if(done)return;stopped=true;error=reason;if(kind=="arm"||kind=="disarm")state->policyFailed=true;reply(failure(reason));
    state->error=reason;
    if(!inFlight||view->closed||!host->alive)finish(failure(reason));}
  void boundary(bool popup){if(kind=="cancel"&&dispatched)reply("{\"ok\":true,\"cancelled\":true,\"completed\":false,\"events_trusted\":false,\"outcome\":"+xb_quote(popup?"popup_open":"dialog_open")+"}");}
  void call(const wchar_t* method,const std::string& params,std::function<void(const std::string&)> next){
    if(!live()){stop("File-picker context, control or deadline changed");return;}
    inFlight=true;auto self=shared_from_this();xb_cdp(view,method,params,[self,next](bool ok,const std::string& result){
      self->inFlight=false;if(self->done)return;if(self->stopped){self->finish(self->failure(self->error));return;}
      if(!self->live()){self->stop("File-picker context changed during delivery; inspect before repeating");return;}
      if(!ok||!xb_get(result,"error").empty()||!xb_get(result,"exceptionDetails").empty()){
        if(self->kind=="arm"||self->kind=="disarm")self->state->policyFailed=true;
        self->state->error="The native file-picker operation was not confirmed";
        self->finish(self->failure("The native file-picker operation was not confirmed"));return;
      }next(result);
    });
  }
  void policy(){auto self=shared_from_this();const bool enable=kind=="arm";
    auto set=[self,enable](const std::string&){self->dispatched=true;
      self->call(L"Page.setInterceptFileChooserDialog",std::string("{\"enabled\":")+(enable?"true":"false")+"}",[self,enable](const std::string&){
        self->state->enabled=enable;self->state->policyFailed=false;self->state->error.clear();self->finish(std::string("{\"ok\":true,\"armed\":")+(enable?"true":"false")+"}");});};
    if(enable)call(L"Page.enable","{}",set);else set("{}");
  }
  void resolveInContext(int context){
    if(!pending||!pending->backend){finish(failure("This picker has no supported HTML input; use manual control"));return;}
    if(kind=="target"&&(!view->automationContext||document.empty())){finish(failure("Observe the current page before resolving its file picker"));return;}
    group="mdo-"+pending->id+"-"+std::to_string(epoch);auto self=shared_from_this();
    const auto params="{\"backendNodeId\":"+std::to_string(pending->backend)+",\"objectGroup\":"+xb_quote(group)+
      ",\"executionContextId\":"+std::to_string(context)+"}";
    call(L"DOM.resolveNode",params,[self](const std::string& result){
      const auto object=xb_get(xb_get(result,"object"),"objectId");if(object.empty()){self->finish(self->failure("The file-picker input no longer exists"));return;}
      if(self->kind=="cancel"){
        self->dispatched=true;const auto function="function(){if(!this.isConnected||this.ownerDocument.defaultView?.document!==this.ownerDocument||this.localName!=='input'||this.type!=='file')return {sent:false};this.dispatchEvent(new Event('cancel',{bubbles:true}));return {sent:true}}";
        self->call(L"Runtime.callFunctionOn","{\"objectId\":"+xb_quote(object)+",\"functionDeclaration\":"+xb_quote(function)+",\"returnByValue\":true}",[self](const std::string& value){
          auto sent=xb_get(xb_get(xb_get(value,"result"),"value"),"sent");self->finish("{\"ok\":true,\"cancelled\":true,\"events_trusted\":false,\"cancel_event_sent\":"+std::string(sent=="true"?"true":"false")+"}");});
      }else{
        const auto function="function(node,document){const r=globalThis.__mdo_browser_use_runtime_v1;return r&&r.version>=8&&r.document===document?r.pinUploadNode(node):{ok:false,error:{code:'stale_document',message:'Observe the current page before selecting files'}}}";
        const auto args="[{\"objectId\":"+xb_quote(object)+"},{\"value\":"+xb_quote(self->document)+"}]";
        self->call(L"Runtime.callFunctionOn","{\"executionContextId\":"+std::to_string(self->view->automationContext)+",\"functionDeclaration\":"+xb_quote(function)+",\"arguments\":"+args+",\"returnByValue\":true}",[self](const std::string& result){
          const auto value=xb_get(xb_get(result,"result"),"value");if(xb_get(value,"ok")=="true"){self->pending->ref=xb_get(xb_get(value,"element"),"ref");self->pending->document=self->document;}
          self->finish(value.empty()?self->failure("The picker input could not be pinned"):value);
        });
      }
    });
  }
  void resolve(){
    if(kind=="target"){resolveInContext(view->automationContext);return;}
    // Cancellation is a fixed isolated-world DOM operation. A website cannot
    // replace Event/dispatchEvent in its main world to hijack this transaction.
    auto self=shared_from_this();call(L"Page.createIsolatedWorld","{\"frameId\":"+xb_quote(pending->frame)+",\"worldName\":\"mdo.browser-use.chooser\"}",[self](const std::string& result){
      uint64_t context=0;if(!xb_upload_uint(xb_get(result,"executionContextId"),context)||!context||context>INT32_MAX){self->finish(self->failure("The file-picker document no longer exists"));return;}
      self->resolveInContext(static_cast<int>(context));
    });
  }
};
static void CALLBACK xb_chooser_tick(HWND window,UINT,UINT_PTR,DWORD){auto at=xb_chooser_views.find(window);if(at==xb_chooser_views.end()){KillTimer(window,xb_chooser_timer);return;}
  auto host=at->second.host.lock();auto view=at->second.view.lock();if(!host||!view||view->closed){KillTimer(window,xb_chooser_timer);xb_chooser_views.erase(at);return;}xb_chooser_maintain(host,view);}
static bool xb_chooser_watch(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view){xb_chooser_views[view->window]={host,view};
  if(SetTimer(view->window,xb_chooser_timer,100,xb_chooser_tick))return true;xb_chooser_views.erase(view->window);view->chooser->policyFailed=true;return false;}
static void xb_chooser_start(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,const std::string& id,const std::string& kind,const std::shared_ptr<xb_chooser_pending>& pending={},const std::string& document=""){
  auto state=view->chooser;auto op=std::make_shared<xb_chooser_operation>();op->host=host;op->view=view;op->state=state;op->id=id;op->kind=kind;op->pending=pending;op->document=document;
  op->navigation=view->navigation;op->deadline=GetTickCount64()+5000;op->epoch=++state->epoch;
  if(kind=="disarm")state->cleanupWanted=false;
  state->changing=true;state->operation=op;if(!xb_chooser_watch(host,view)){op->stop("Cannot retain the file-picker operation deadline");return;}
  if(kind=="arm"||kind=="disarm")op->policy();else op->resolve();
}
static void xb_chooser_maintain(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view){auto state=view->chooser;if(!state)return;
  if(state->pending&&(view->navigation!=state->pending->navigation||GetTickCount64()>=state->pending->expires)){
    if(view->navigation==state->pending->navigation)state->deferredCancel=state->pending;state->pending.reset();}
  if(auto op=state->operation.lock()){
    if(GetTickCount64()>=op->deadline||!op->live()){
      if(op->kind=="cancel"&&(view->dialog||xb_popup_blocked(view))&&GetTickCount64()<op->deadline)op->boundary(xb_popup_blocked(view));
      else op->stop("File-picker operation interrupted or timed out; inspect its effects");
    }
  }
  if(!state->changing){
    // At most one automatic policy cleanup. A failed CDP acknowledgement must
    // block input and remain inspectable, never turn into a 100 ms retry loop.
    if(state->cleanupWanted){xb_chooser_start(host,view,"","disarm");return;}
    if(state->deferredCancel){auto pending=std::move(state->deferredCancel);if(view->navigation==pending->navigation&&pending->backend){xb_chooser_start(host,view,"","cancel",pending);return;}}
  }
  if(!state->changing&&!state->pending&&!state->deferredCancel){KillTimer(view->window,xb_chooser_timer);xb_chooser_views.erase(view->window);}
}
static void xb_chooser_close(const std::shared_ptr<xb_view>& view){KillTimer(view->window,xb_chooser_timer);xb_chooser_views.erase(view->window);if(!view->chooser)return;
  auto state=view->chooser;if(auto op=state->operation.lock())op->stop("The browser was closed during file-picker processing");state->pending.reset();state->deferredCancel.reset();state->unsubscribe();}
static void xb_chooser_takeover(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view){if(!view->chooser)return;auto state=view->chooser;state->wanted=false;
  state->cleanupWanted=state->enabled||state->policyFailed||state->changing;
  if(state->pending)state->deferredCancel=std::move(state->pending);if(auto op=state->operation.lock())if(op->kind=="arm"||op->kind=="target")op->stop("The user took control during file-picker preparation");
  if(xb_chooser_watch(host,view))xb_chooser_maintain(host,view);
}
static void xb_chooser_boundary(const std::shared_ptr<xb_view>& view,bool popup){if(view->chooser)if(auto op=view->chooser->operation.lock())op->boundary(popup);}
static void xb_install_chooser(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view){auto state=std::make_shared<xb_chooser_state>();view->chooser=state;
  if(FAILED(view->core->GetDevToolsProtocolEventReceiver(L"Page.fileChooserOpened",&state->receiver))||!state->receiver)return;
  std::weak_ptr<xb_view> weak=view;std::weak_ptr<xb_host> owner=host;
  auto* handler=xb_cb<ICoreWebView2DevToolsProtocolEventReceivedEventHandler,ICoreWebView2*,ICoreWebView2DevToolsProtocolEventReceivedEventArgs*>(
    IID_ICoreWebView2DevToolsProtocolEventReceivedEventHandler,[weak,owner](ICoreWebView2*,ICoreWebView2DevToolsProtocolEventReceivedEventArgs* args){
      auto view=weak.lock();auto host=owner.lock();if(!view||!host||!host->alive||view->closed||!view->chooser)return S_OK;auto state=view->chooser;
      xs_wv_text text;if(FAILED(args->get_ParameterObjectAsJson(&text.p))||!text.p)return S_OK;
      size_t length=0;while(length<=16384&&text.p[length])length++;if(length>16384){state->error="File-picker metadata exceeded its bound";return S_OK;}
      const auto data=webview::detail::narrow_string(text.p),mode=xb_get(data,"mode"),frame=xb_get(data,"frameId");uint64_t backend=0;
      if(frame.empty()||frame.size()>128||(mode!="selectSingle"&&mode!="selectMultiple")||(!xb_get(data,"backendNodeId").empty()&&!xb_upload_uint(xb_get(data,"backendNodeId"),backend))||backend>INT32_MAX){state->error="Invalid file-picker metadata was ignored";return S_OK;}
      if(state->pending||state->deferredCancel||state->changing){if(state->ignored<65535)state->ignored++;state->error="Another picker was ignored while one is pending";return S_OK;}
      auto pending=std::make_shared<xb_chooser_pending>();GUID guid{};wchar_t identifier[40]{};
      if(FAILED(CoCreateGuid(&guid))||!StringFromGUID2(guid,identifier,40)){state->error="Cannot retain a file-picker identity";return S_OK;}
      pending->id="chooser-"+webview::detail::narrow_string(identifier);pending->frame=frame;pending->backend=static_cast<int>(backend);pending->multiple=mode=="selectMultiple";
      pending->navigation=view->navigation;pending->expires=GetTickCount64()+state->lease;
      if(!state->wanted||view->manual){state->deferredCancel=pending;}else{state->error.clear();state->pending=pending;if(auto input=view->input.lock())input->boundary("file_chooser");if(view->drag)view->drag->stop("A file picker interrupted the drag; inspect before continuing");}
      if(!xb_chooser_watch(host,view)){state->pending.reset();state->deferredCancel.reset();}return S_OK;
    });
  state->subscribed=SUCCEEDED(state->receiver->add_DevToolsProtocolEventReceived(handler,&state->token));handler->Release();state->supported=state->subscribed;
}
static void xb_chooser_request(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,const std::string& id,const std::string& json){
  auto state=view->chooser;const auto action=xb_get(json,"action");if(!state||!state->supported){host->reply(id,"","This browser cannot intercept file pickers");return;}
  xb_chooser_maintain(host,view);
  if(action=="list"){
    auto pending=state->pending;const auto item=pending?"{\"id\":"+xb_quote(pending->id)+",\"multiple\":"+(pending->multiple?"true":"false")+",\"supported\":"+(pending->backend?"true":"false")+",\"navigation\":"+xb_quote(std::to_string(pending->navigation))+"}":"";
    host->reply(id,"{\"ok\":true,\"armed\":"+std::string(state->enabled?"true":"false")+",\"changing\":"+(state->changing?"true":"false")+",\"cleanup_failed\":"+(state->policyFailed||state->objectFailed?"true":"false")+",\"ignored\":"+std::to_string(state->ignored)+",\"choosers\":["+item+"],\"error\":"+xb_quote(state->error)+"}");return;
  }
  if(view->closed||view->failed||(view->manual&&xb_get(json,"agent")=="true")){host->reply(id,"","The browser is unavailable or controlled by the user");return;}
  if(state->changing){host->reply(id,"","A file-picker operation is still completing");return;}
  if(action=="arm"){
    uint64_t lease=120000;if((!xb_get(json,"timeout_ms").empty()&&!xb_upload_uint(xb_get(json,"timeout_ms"),lease))||lease<1000||lease>120000){host->reply(id,"","File-picker lease must be 1000..120000 ms");return;}
    if(view->manual||state->pending||state->deferredCancel||state->objectFailed||view->loading||view->initialNavigationPending||view->dialog||xb_popup_blocked(view)||!view->input.expired()||view->uploadBusy||view->drag){host->reply(id,"","The browser input slot is not ready for file-picker interception");return;}
    state->lease=static_cast<unsigned>(lease);state->wanted=true;if(state->enabled&&!state->policyFailed){host->reply(id,"{\"ok\":true,\"armed\":true}");return;}xb_chooser_start(host,view,id,"arm");return;
  }
  if(action=="disarm"){
    state->wanted=false;if(state->pending)state->deferredCancel=std::move(state->pending);xb_chooser_start(host,view,id,"disarm");return;
  }
  auto pending=state->pending;if(!pending||pending->id!=xb_get(json,"chooser")){host->reply(id,"","The file-picker ticket expired or belongs to another request");return;}
  if(action=="target"){
    const auto document=xb_get(json,"document");if(document.empty()||document.size()>100){host->reply(id,"","Observe the current document before resolving its picker");return;}
    if(!pending->document.empty()&&pending->document!=document){host->reply(id,"","The picker is pinned to a different document");return;}
    xb_chooser_start(host,view,id,"target",pending,document);return;
  }
  if(action=="cancel"){
    state->pending.reset();if(!pending->backend){host->reply(id,"{\"ok\":true,\"cancelled\":true,\"cancel_event_sent\":false}");return;}xb_chooser_start(host,view,id,"cancel",pending);return;
  }
  host->reply(id,"","Unsupported file-picker action");
}
