/* Root-world admission. Runtime events, the creation reply and a negative and
 * positive unique-ID probe must agree before any caller script can execute.
 * No provider fallback may turn a unique-world failure into numeric execution. */
static constexpr UINT_PTR xb_world_timer=0xBADD9;
static std::map<HWND,std::weak_ptr<xb_view>> xb_world_views;
using xb_world_result=std::function<void(std::shared_ptr<xb_world_context>)>;

// BROWSER_WORLD_REGISTRY_BEGIN
static int xb_world_number(const std::string& text){
  if(text.empty()||text.size()>10)return 0;uint64_t value=0;
  for(char digit:text){if(digit<'0'||digit>'9')return 0;value=value*10+digit-'0';if(value>INT32_MAX)return 0;}
  return static_cast<int>(value);
}
static bool xb_world_live(const std::shared_ptr<xb_view>& view,const std::shared_ptr<xb_world_context>& pin){
  if(!view||view->closed||!view->core||!view->worlds||view->worlds->closed||!view->worlds->error.empty()||!pin||!pin->alive||!pin->registered||!pin->admitted)return false;
  auto found=view->worlds->names.find(pin->name);return found!=view->worlds->names.end()&&found->second.lock()==pin;
}
static void xb_world_metadata(const std::shared_ptr<xb_world_state>& state,const std::string& method,const std::string& data){
  if(state->closed||!state->error.empty())return;
  if(method=="Runtime.executionContextCreated"){
    const auto context=xb_get(data,"context"),name=xb_get(context,"name");auto at=state->names.find(name);
    if(at==state->names.end())return;auto pin=at->second.lock();if(!pin||!pin->alive)return;
    const auto aux=xb_get(context,"auxData"),unique=xb_get(context,"uniqueId");const auto numeric=xb_world_number(xb_get(context,"id"));
    const auto frame=xb_get(aux,"frameId");
    if(!numeric||unique.empty()||unique.size()>256||frame.empty()||frame.size()>128||xb_get(aux,"isDefault")!="false"||xb_get(aux,"type")!="isolated"){
      pin->alive=false;state->error="world_context_metadata_invalid";return;
    }
    // Accessing a same-origin child WindowProxy may instantiate this named
    // isolated world in that child as well. Its native identity grants no
    // authority over the requested root and must not overwrite that pin.
    if(frame!=pin->frame){if(state->otherFrames<1000000)state->otherFrames++;return;}
    if(pin->registered){
      if(pin->numeric==numeric&&pin->unique==unique){if(state->refreshes<1000000)state->refreshes++;return;}
      pin->alive=false;state->error="world_context_metadata_rebound";return;
    }
    if(state->seen.count(unique)||state->seen.size()>=1024){pin->alive=false;state->error="world_context_metadata_invalid";return;}
    // Reuse of a numeric renderer-local ID is legitimate after process swaps.
    // Retire its previous object; never rewrite or revive an original pin.
    for(auto& item:state->names)if(auto previous=item.second.lock())if(previous!=pin&&previous->registered&&previous->numeric==numeric)previous->alive=false;
    state->seen.emplace(unique,true);pin->numeric=numeric;pin->unique=unique;pin->registered=true;return;
  }
  const auto unique=xb_get(data,"executionContextUniqueId");const int numeric=xb_world_number(xb_get(data,"executionContextId"));
  for(auto& item:state->names)if(auto pin=item.second.lock()){
    if(method=="Runtime.executionContextsCleared"||method=="Runtime.executionContextDestroyed"&&
       (!unique.empty()?pin->unique==unique:numeric&&pin->registered&&pin->numeric==numeric))pin->alive=false;
  }
}
static std::string xb_world_selector(const std::shared_ptr<xb_world_context>& pin){return "\"uniqueContextId\":"+xb_quote(pin->unique);}
static bool xb_world_negative_proof(bool ok,const std::string& result){
  const auto error=xb_get(result,"error"),message=xb_get(error,"message");
  return !ok&&xb_get(error,"code")=="-32602"&&(message=="invalid uniqueContextId"||message=="uniqueContextId not found");
}
// BROWSER_WORLD_REGISTRY_END

static bool xb_worlds_install(const std::shared_ptr<xb_view>& view){
  if(view->worlds)return !view->worlds->closed&&view->worlds->error.empty();
  auto state=std::make_shared<xb_world_state>();view->worlds=state;state->error="world_events_unavailable";
  for(const auto* event:{L"Runtime.executionContextCreated",L"Runtime.executionContextDestroyed",L"Runtime.executionContextsCleared"}){
    auto receiver=std::make_shared<xb_world_receiver>();
    if(FAILED(view->core->GetDevToolsProtocolEventReceiver(event,&receiver->source))||!receiver->source){state->error="world_events_unavailable";return false;}
    const auto method=webview::detail::narrow_string(event);std::weak_ptr<xb_view> weak=view;std::weak_ptr<xb_world_state> held=state;
    auto* handler=xb_cb<ICoreWebView2DevToolsProtocolEventReceivedEventHandler,ICoreWebView2*,ICoreWebView2DevToolsProtocolEventReceivedEventArgs*>(
      IID_ICoreWebView2DevToolsProtocolEventReceivedEventHandler,[weak,held,method](ICoreWebView2*,ICoreWebView2DevToolsProtocolEventReceivedEventArgs* args){
        auto view=weak.lock();auto state=held.lock();if(!view||!state||view->closed||view->worlds!=state||state->closed)return S_OK;
        xs_wv_com<ICoreWebView2DevToolsProtocolEventReceivedEventArgs2> routed;xs_wv_text session,text;
        if(FAILED(args->QueryInterface(IID_ICoreWebView2DevToolsProtocolEventReceivedEventArgs2,reinterpret_cast<void**>(routed.out())))||FAILED(routed->get_SessionId(&session.p))){state->error="world_event_routing_unavailable";return S_OK;}
        if(session.p&&*session.p)return S_OK; // Child sessions have their own realm namespace.
        if(FAILED(args->get_ParameterObjectAsJson(&text.p))){state->error="world_event_metadata_unavailable";return S_OK;}
        size_t length=0;if(text.p)while(length<=32768&&text.p[length])length++;
        if(length>32768){state->error="world_event_limit";return S_OK;}
        xb_world_metadata(state,method,webview::detail::narrow_string(text.p?text.p:L"{}"));return S_OK;
      });
    receiver->subscribed=SUCCEEDED(receiver->source->add_DevToolsProtocolEventReceived(handler,&receiver->token));handler->Release();
    if(!receiver->subscribed){state->error="world_events_unavailable";return false;}state->receivers.push_back(receiver);
  }
  state->error.clear();return true;
}
struct xb_world_request:std::enable_shared_from_this<xb_world_request>{
  std::weak_ptr<xb_host> host;std::weak_ptr<xb_view> view;std::weak_ptr<xb_world_state> state;
  std::shared_ptr<xb_world_context> pin;std::vector<xb_world_result> waiters;
  UINT64 navigation=0;ULONGLONG deadline=0;int replyId=0;bool done=false,proving=false,creating=false;
  bool live()const{
    auto owner=host.lock();auto active=view.lock();auto current=state.lock();
    return !done&&owner&&active&&current&&active->worlds==current&&!current->closed&&current->error.empty()&&
      current->pending.get()==this&&xb_automation_live(owner,active,navigation)&&GetTickCount64()<deadline&&(!pin||pin->alive);
  }
  void finish(bool admitted,const char* error=""){
    if(done)return;done=true;auto active=view.lock();auto current=state.lock();
    if(!admitted&&pin)pin->alive=false;
    if(active&&current&&active->worlds==current&&current->pending.get()==this){
      current->pending.reset();current->enabling=false;
      if(admitted){pin->admitted=true;current->original=pin;active->automationWorld=pin;active->automationContext=pin->numeric;}
      if(*error)current->error=error;
      KillTimer(active->window,xb_world_timer);xb_world_views.erase(active->window);
    }
    auto callbacks=std::move(waiters);for(auto& callback:callbacks)try{callback(admitted?pin:nullptr);}catch(...){/* A delivery failure cannot replay another caller. */}
  }
  void qualify(){
    if(proving||!replyId||!pin||!pin->registered)return;
    if(!live()||pin->numeric!=replyId){finish(false);return;}proving=true;
    auto active=view.lock();auto self=shared_from_this();
    const auto proof=std::string("{\"expression\":\"void 0\",\"returnByValue\":true,\"silent\":true,\"throwOnSideEffect\":true,\"timeout\":1000,");
    // An empty unique ID must be rejected, not ignored in favor of a default
    // main-world evaluation. Probe contains no caller/page script or side effect.
    xb_cdp(active,L"Runtime.evaluate",proof+"\"uniqueContextId\":\"\"}",[self,proof](bool ok,const std::string& result){
      if(!self->live()){self->finish(false);return;}
      if(!xb_world_negative_proof(ok,result)){self->finish(false,"world_unique_context_unsupported");return;}
      xb_cdp(self->view.lock(),L"Runtime.evaluate",proof+xb_world_selector(self->pin)+"}",[self](bool ok,const std::string& result){
        if(!self->live()){self->finish(false);return;}
        if(!ok||!xb_get(result,"error").empty()||!xb_get(result,"exceptionDetails").empty()||xb_get(xb_get(result,"result"),"type")!="undefined"){self->finish(false,"world_unique_context_unconfirmed");return;}
        self->finish(true);
      });
    });
  }
  void create(){
    if(creating)return;creating=true;auto self=shared_from_this();auto active=view.lock();
    xb_cdp(active,L"Page.getFrameTree","{}",[self](bool ok,const std::string& tree){
      if(!self->live()){self->finish(false);return;}
      const auto node=xb_get(xb_get(tree,"frameTree"),"frame"),frame=xb_get(node,"id"),loader=xb_get(node,"loaderId");
      if(!ok||frame.empty()||frame.size()>128||loader.size()>128){self->finish(false);return;}
      auto current=self->state.lock();
      // NavigationStarting can deliver a response/download without replacing
      // the DOM. Reuse only the exact still-live original native world after a
      // fresh nonempty loader/frame match; never infer survival from the URL.
      auto original=current->original;
      if(original&&original->alive&&original->admitted&&original->frame==frame&&!loader.empty()&&original->loader==loader){
        self->pin=original;self->replyId=original->numeric;self->qualify();return;
      }
      for(auto item=current->names.begin();item!=current->names.end();)if(item->second.expired())item=current->names.erase(item);else ++item;
      const auto proof=frame+"\n"+loader;
      if(!loader.empty()&&current->documentProof!=proof){current->documentProof=proof;current->documentWorlds=0;}
      if(current->created>=1024||current->names.size()>=32||current->documentWorlds>=32){self->finish(false,"world_identity_limit");return;}
      GUID guid{};wchar_t text[40]{};if(FAILED(CoCreateGuid(&guid))||!StringFromGUID2(guid,text,40)){self->finish(false);return;}
      self->pin=std::make_shared<xb_world_context>();self->pin->frame=frame;self->pin->loader=loader;
      self->pin->name="mdo.browser-use.root."+webview::detail::narrow_string(text);current->created++;current->documentWorlds++;
      current->names.emplace(self->pin->name,self->pin);
      xb_cdp(self->view.lock(),L"Page.createIsolatedWorld","{\"frameId\":"+xb_quote(frame)+",\"worldName\":"+xb_quote(self->pin->name)+",\"grantUniveralAccess\":false}",
        [self](bool ok,const std::string& result){
          if(!self->live()){self->finish(false);return;}self->replyId=xb_world_number(xb_get(result,"executionContextId"));
          if(!ok||!self->replyId){self->finish(false);return;}self->qualify();
        });
    });
  }
};
static void CALLBACK xb_world_tick(HWND window,UINT,UINT_PTR,DWORD){
  auto at=xb_world_views.find(window);if(at==xb_world_views.end()){KillTimer(window,xb_world_timer);return;}
  auto view=at->second.lock();if(!view||!view->worlds){KillTimer(window,xb_world_timer);xb_world_views.erase(at);return;}
  auto request=view->worlds->pending;if(!request){KillTimer(window,xb_world_timer);xb_world_views.erase(at);return;}
  try{if(!request->live())request->finish(false);else request->qualify();}catch(...){request->finish(false);}
}
static void xb_world_root(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,UINT64 navigation,xb_world_result complete){
  std::shared_ptr<xb_world_request> request;
  try{
  if(!xb_automation_live(host,view,navigation)||!xb_worlds_install(view)){auto callback=std::move(complete);callback(nullptr);return;}
  if(xb_world_live(view,view->automationWorld)){auto callback=std::move(complete);callback(view->automationWorld);return;}
  auto state=view->worlds;
  if(state->pending&&state->pending->navigation!=navigation){auto previous=state->pending;previous->finish(false);}
  if(state->pending){if(state->pending->waiters.size()>=32){auto callback=std::move(complete);callback(nullptr);return;}state->pending->waiters.push_back(complete);return;}
  request=std::make_shared<xb_world_request>();request->host=host;request->view=view;request->state=state;
  request->navigation=navigation;request->deadline=GetTickCount64()+3000;request->waiters.push_back(complete);complete=nullptr;state->pending=request;
  xb_world_views[view->window]=view;
  if(!SetTimer(view->window,xb_world_timer,20,xb_world_tick)){request->finish(false);return;}
  if(state->enabled){request->create();return;}state->enabling=true;
  xb_cdp(view,L"Runtime.enable","{}",[request](bool ok,const std::string& result){
    if(!request->live()){request->finish(false);return;}
    auto state=request->state.lock();state->enabling=false;
    if(!ok||!xb_get(result,"error").empty()){request->finish(false,"world_runtime_unavailable");return;}
    state->enabled=true;request->create();
  });
  }catch(...){if(request&&!request->waiters.empty())request->finish(false);else if(complete){auto callback=std::move(complete);try{callback(nullptr);}catch(...){}}}
}
static void xb_worlds_close(const std::shared_ptr<xb_view>& view){
  if(!view->worlds)return;auto state=view->worlds;state->closed=true;
  for(auto& item:state->names)if(auto pin=item.second.lock())pin->alive=false;
  if(state->pending){auto pending=state->pending;pending->finish(false);}
  state->receivers.clear();state->original.reset();view->automationWorld.reset();view->automationContext=0;
  KillTimer(view->window,xb_world_timer);xb_world_views.erase(view->window);
}
