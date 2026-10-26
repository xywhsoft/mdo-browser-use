/* Included in the native browser namespace. CDP remains private to the trusted
 * application document. Models receive typed operations, never CDP/script. */
using xb_cdp_result = std::function<void(bool,const std::string&)>;
static bool xb_popup_blocked(const std::shared_ptr<xb_view>& view){
  for(const auto& item:view->popups)if(!item.second->completed)return true;return false;
}
static void xb_cdp(const std::shared_ptr<xb_view>& view,const wchar_t* method,
                   const std::string& params,xb_cdp_result done){
  auto* callback=xb_cb<ICoreWebView2CallDevToolsProtocolMethodCompletedHandler,HRESULT,LPCWSTR>(
    IID_ICoreWebView2CallDevToolsProtocolMethodCompletedHandler,
    [done](HRESULT status,LPCWSTR output){
      done(SUCCEEDED(status),webview::detail::narrow_string(output?output:L"{}"));return S_OK;
    });
  HRESULT status=view->core->CallDevToolsProtocolMethod(method,webview::detail::widen_string(params).c_str(),callback);
  callback->Release();if(FAILED(status))done(false,"{}");
}
static bool xb_automation_live(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,UINT64 navigation){
  return host->alive&&!view->closed&&!view->failed&&!view->manual&&!view->dialog&&!xb_popup_blocked(view)&&view->navigation==navigation;
}
static void xb_automation_reply(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,
                                const std::string& id,const std::string& result,const std::string& error=""){
  if(view->automationRequests.erase(id))host->reply(id,result,error);
}
static void xb_abort_automation(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,int interruption){
  auto pending=std::move(view->automationRequests);view->automationRequests.clear();
  const auto result=interruption==2?"{\"ok\":true,\"value\":{\"ok\":true,\"blocked_by_popup\":true}}":"{\"ok\":true,\"value\":{\"ok\":true,\"blocked_by_dialog\":true}}";
  for(const auto& item:pending)host->reply(item.first,interruption?result:"",interruption?"":"The browser was closed");
}
static void xb_automation(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,
                          const std::string& id,const std::string& script){
  if(script.size()>131072){host->reply(id,"","Automation script exceeds 128 KiB");return;}
  if(view->automationRequests.size()>=32){host->reply(id,"","Native automation request limit reached");return;}
  view->automationRequests[id]=true;
  const auto navigation=view->navigation;
  const auto evaluate=[host,view,id,script,navigation](int context){
    if(!view->automationRequests.count(id))return;
    if(!xb_automation_live(host,view,navigation)){xb_automation_reply(host,view,id,"","Browser control or document changed");return;}
    const auto params="{\"expression\":"+xb_quote(script)+",\"contextId\":"+std::to_string(context)+
      ",\"returnByValue\":true,\"awaitPromise\":false,\"timeout\":3000}";
    xb_cdp(view,L"Runtime.evaluate",params,[host,view,id,navigation](bool ok,const std::string& result){
      if(!xb_automation_live(host,view,navigation)){xb_automation_reply(host,view,id,"","Browser control or document changed during evaluation");return;}
      const auto value=xb_get(xb_get(result,"result"),"value");
      if(!ok||!xb_get(result,"exceptionDetails").empty()||value.empty())xb_automation_reply(host,view,id,"","Isolated page evaluation failed");
      else xb_automation_reply(host,view,id,"{\"ok\":true,\"value\":"+value+"}");
    });
  };
  if(view->automationContext){evaluate(view->automationContext);return;}
  xb_cdp(view,L"Page.getFrameTree","{}",[host,view,id,navigation,evaluate](bool ok,const std::string& tree){
    if(!view->automationRequests.count(id))return;
    if(!ok||!xb_automation_live(host,view,navigation)){xb_automation_reply(host,view,id,"","Cannot resolve current document");return;}
    const auto frame=xb_get(xb_get(xb_get(tree,"frameTree"),"frame"),"id");
    if(frame.empty()){xb_automation_reply(host,view,id,"","Current document has no frame identity");return;}
    xb_cdp(view,L"Page.createIsolatedWorld","{\"frameId\":"+xb_quote(frame)+",\"worldName\":\"mdo.browser-use.v1\"}",
      [host,view,id,navigation,evaluate](bool ready,const std::string& result){
        int context=0;try{context=std::stoi(xb_get(result,"executionContextId"));}catch(...){}
        if(!view->automationRequests.count(id))return;
        if(!ready||context<=0||!xb_automation_live(host,view,navigation)){xb_automation_reply(host,view,id,"","Cannot create isolated browser world");return;}
        view->automationContext=context;evaluate(context);
      });
  });
}

struct xb_input_step{const wchar_t* method;std::string params;bool release=false;};
struct xb_input_sequence:std::enable_shared_from_this<xb_input_sequence>{
  std::shared_ptr<xb_host> host;std::shared_ptr<xb_view> view;std::string id,error;
  UINT64 navigation;std::vector<xb_input_step> steps;size_t index=0;bool replied=false;
  void boundary(const char* kind){
    if(replied)return;replied=true;
    error="A new window or JavaScript dialog interrupted input; inspect the page before continuing";
    host->reply(id,std::string("{\"accepted\":true,\"trusted_input\":true,\"")+kind+"_open\":true}");
  }
  void dialogOpened(){boundary("dialog");}
  void popupOpened(){boundary("popup");}
  void run(){
    if(index==steps.size()){if(!replied)host->reply(id,error.empty()?"{\"accepted\":true,\"trusted_input\":true}":"",error);return;}
    const auto step=steps[index++];
    if(!xb_automation_live(host,view,navigation)&&error.empty())error="Browser control or document changed; verify the action result";
    // Releases must run even if a navigation/takeover interrupted a press.
    if(!error.empty()&&!step.release){run();return;}
    auto self=shared_from_this();
    xb_cdp(view,step.method,step.params,[self](bool ok,const std::string&){
      if(!ok&&self->error.empty())self->error="Native input failed; verify the action result";self->run();
    });
  }
};
static void xb_input(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,
                     const std::string& id,const std::string& json){
  if(xb_get(json,"navigation")!=std::to_string(view->navigation)||view->manual||view->closed){host->reply(id,"","Stale document or manual control");return;}
  if(view->dialog){host->reply(id,"","Respond to the pending JavaScript dialog before input");return;}
  if(xb_popup_blocked(view)){host->reply(id,"","A new-window request is still being adopted");return;}
  if(!view->input.expired()){host->reply(id,"","A native input is still completing; inspect the page before another action");return;}
  auto sequence=std::make_shared<xb_input_sequence>();sequence->host=host;sequence->view=view;sequence->id=id;sequence->navigation=view->navigation;
  const auto kind=xb_get(json,"kind");
  if(kind=="click"||kind=="move"||kind=="wheel"){
    double x=-1,y=-1;try{x=std::stod(xb_get(json,"x"));y=std::stod(xb_get(json,"y"));}catch(...){}
    if(!std::isfinite(x)||!std::isfinite(y)||x<0||y<0||x>16384||y>16384){host->reply(id,"","Invalid CSS input coordinates");return;}
    const auto point=",\"x\":"+std::to_string(x)+",\"y\":"+std::to_string(y);
    if(kind=="wheel"){
      double dx=0,dy=0;try{dx=std::stod(xb_get(json,"dx"));dy=std::stod(xb_get(json,"dy"));}catch(...){host->reply(id,"","Invalid wheel delta");return;}
      if(!std::isfinite(dx)||!std::isfinite(dy)||std::abs(dx)>10000||std::abs(dy)>10000){host->reply(id,"","Wheel delta exceeds limit");return;}
      sequence->steps.push_back({L"Input.dispatchMouseEvent","{\"type\":\"mouseWheel\""+point+",\"deltaX\":"+std::to_string(dx)+",\"deltaY\":"+std::to_string(dy)+"}"});
    }else{
      sequence->steps.push_back({L"Input.dispatchMouseEvent","{\"type\":\"mouseMoved\""+point+"}"});
      if(kind=="click"){
        sequence->steps.push_back({L"Input.dispatchMouseEvent","{\"type\":\"mousePressed\",\"button\":\"left\",\"buttons\":1,\"clickCount\":1"+point+"}"});
        sequence->steps.push_back({L"Input.dispatchMouseEvent","{\"type\":\"mouseReleased\",\"button\":\"left\",\"buttons\":0,\"clickCount\":1"+point+"}",true});
      }
    }
  }else if(kind=="text"){
    const auto text=xb_get(json,"text");if(text.size()>32768){host->reply(id,"","Text exceeds 32 KiB");return;}
    sequence->steps.push_back({L"Input.insertText","{\"text\":"+xb_quote(text)+"}"});
  }else if(kind=="key"){
    const auto key=xb_get(json,"key");std::string code=key,actual=key;int vk=0,modifiers=0;
    if(key=="Control+A"){actual="a";code="KeyA";vk=65;modifiers=2;}
    else if(key=="Shift+Tab"){actual="Tab";code="Tab";vk=9;modifiers=8;}
    else if(key=="Enter")vk=13;else if(key=="Tab")vk=9;else if(key=="Escape")vk=27;
    else if(key=="Backspace")vk=8;else if(key=="Delete")vk=46;else if(key=="ArrowLeft")vk=37;
    else if(key=="ArrowUp")vk=38;else if(key=="ArrowRight")vk=39;else if(key=="ArrowDown")vk=40;
    else if(key=="Home")vk=36;else if(key=="End")vk=35;else if(key=="PageUp")vk=33;else if(key=="PageDown")vk=34;
    if(!vk){host->reply(id,"","Unsupported key; use text for character input");return;}
    const auto fields=",\"key\":"+xb_quote(actual)+",\"code\":"+xb_quote(code)+",\"windowsVirtualKeyCode\":"+std::to_string(vk)+",\"modifiers\":"+std::to_string(modifiers);
    sequence->steps.push_back({L"Input.dispatchKeyEvent","{\"type\":\"keyDown\""+fields+(key=="Enter"?",\"text\":\"\\r\"":"")+"}"});
    sequence->steps.push_back({L"Input.dispatchKeyEvent","{\"type\":\"keyUp\""+fields+"}",true});
  }else{host->reply(id,"","Unsupported native input kind");return;}
  view->input=sequence;sequence->run();
}

static constexpr UINT_PTR xb_dialog_timer=0xBADD1;
static std::map<HWND,std::weak_ptr<xb_view>> xb_dialog_views;
static void xb_dialog_cleanup(HWND window){KillTimer(window,xb_dialog_timer);xb_dialog_views.erase(window);}
static void CALLBACK xb_dialog_expire(HWND window,UINT,UINT_PTR,DWORD){
  auto item=xb_dialog_views.find(window);if(item==xb_dialog_views.end()){KillTimer(window,xb_dialog_timer);return;}
  auto view=item->second.lock();
  if(!view||!view->dialog||GetTickCount64()>=view->dialog->expires){
    auto expired=view?std::move(view->dialog):std::shared_ptr<xb_dialog>{};
    xb_dialog_cleanup(window);expired.reset();
  }
}
static std::string xb_bounded_text(std::string text,size_t maximum){
  if(text.size()>maximum){size_t end=maximum;while(end&&(static_cast<unsigned char>(text[end])&0xC0)==0x80)end--;text.resize(end);}return text;
}
static const char* xb_dialog_kind(COREWEBVIEW2_SCRIPT_DIALOG_KIND kind){
  switch(kind){case COREWEBVIEW2_SCRIPT_DIALOG_KIND_ALERT:return "alert";
    case COREWEBVIEW2_SCRIPT_DIALOG_KIND_CONFIRM:return "confirm";
    case COREWEBVIEW2_SCRIPT_DIALOG_KIND_PROMPT:return "prompt";
    case COREWEBVIEW2_SCRIPT_DIALOG_KIND_BEFOREUNLOAD:return "beforeunload";default:return "unknown";}
}
static void xb_install_dialogs(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view){
  std::weak_ptr<xb_view> weak=view;std::weak_ptr<xb_host> parent=host;
  auto* callback=xb_cb<ICoreWebView2ScriptDialogOpeningEventHandler,ICoreWebView2*,ICoreWebView2ScriptDialogOpeningEventArgs*>(
    IID_ICoreWebView2ScriptDialogOpeningEventHandler,[weak,parent](ICoreWebView2*,ICoreWebView2ScriptDialogOpeningEventArgs* args){
      auto active=weak.lock();if(!active||active->closed||active->dialog)return S_OK;
      auto dialog=std::make_shared<xb_dialog>();GUID guid{};wchar_t identifier[40]{};
      if(FAILED(CoCreateGuid(&guid))||!StringFromGUID2(guid,identifier,40)||FAILED(args->GetDeferral(&dialog->deferral))){active->error="Cannot retain a JavaScript dialog; it was dismissed";return S_OK;}
      args->AddRef();dialog->args=args;dialog->id="dialog-"+webview::detail::narrow_string(identifier);
      dialog->expires=GetTickCount64()+120000;active->dialog=dialog;
      xb_dialog_views[active->window]=active;
      if(!SetTimer(active->window,xb_dialog_timer,1000,xb_dialog_expire)){active->error="Cannot retain a JavaScript dialog; it was dismissed";active->dialog.reset();xb_dialog_cleanup(active->window);return S_OK;}
      if(auto input=active->input.lock())input->dialogOpened();
      if(auto owner=parent.lock())xb_abort_automation(owner,active,true);return S_OK;
    });
  EventRegistrationToken token{};HRESULT status=view->core->add_ScriptDialogOpening(callback,&token);callback->Release();
  if(SUCCEEDED(status)){
    xs_wv_com<ICoreWebView2Settings> settings;
    if(SUCCEEDED(view->core->get_Settings(settings.out()))&&SUCCEEDED(settings->put_AreDefaultScriptDialogsEnabled(FALSE)))view->dialogsEnabled=true;
    else view->core->remove_ScriptDialogOpening(token);
  }
}
static void xb_dialog_request(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,
                              const std::string& id,const std::string& json){
  const auto action=xb_get(json,"action");auto dialog=view->dialog;
  if(action=="list"){
    std::string item;
    if(dialog){xs_wv_text message,url;COREWEBVIEW2_SCRIPT_DIALOG_KIND kind;
      dialog->args->get_Kind(&kind);dialog->args->get_Message(&message.p);dialog->args->get_Uri(&url.p);
      item="{\"id\":"+xb_quote(dialog->id)+",\"kind\":"+xb_quote(xb_dialog_kind(kind))+
        ",\"message\":"+xb_quote(xb_bounded_text(webview::detail::narrow_string(message.p?message.p:L""),4096))+
        ",\"url\":"+xb_quote(xb_bounded_text(webview::detail::narrow_string(url.p?url.p:L""),2048))+"}";
    }
    host->reply(id,"{\"ok\":true,\"dialogs\":["+item+"]}");return;
  }
  if(action!="respond"||!dialog||dialog->id!=xb_get(json,"dialog")){host->reply(id,"","The requested JavaScript dialog is no longer pending");return;}
  if(view->manual&&xb_get(json,"agent")=="true"){host->reply(id,"","The user currently controls this browser");return;}
  const auto accept=xb_get(json,"accept"),text=xb_get(json,"text");
  if((accept!="true"&&accept!="false")||text.size()>8192){host->reply(id,"","A boolean response and at most 8 KiB of text are required");return;}
  COREWEBVIEW2_SCRIPT_DIALOG_KIND kind;dialog->args->get_Kind(&kind);HRESULT status=S_OK;
  if(accept=="true"){
    if(kind==COREWEBVIEW2_SCRIPT_DIALOG_KIND_PROMPT){
      if(xb_get(json,"has_text")=="true"||!text.empty())status=dialog->args->put_ResultText(webview::detail::widen_string(text).c_str());
      else{xs_wv_text initial;status=dialog->args->get_DefaultText(&initial.p);if(SUCCEEDED(status))status=dialog->args->put_ResultText(initial.p?initial.p:L"");}
    }
    if(SUCCEEDED(status))status=dialog->args->Accept();
  }
  if(FAILED(status)){host->reply(id,"","Dialog response was not confirmed; inspect the current page");return;}
  view->dialog.reset();xb_dialog_cleanup(view->window);dialog.reset();
  host->reply(id,"{\"ok\":true,\"responded\":true}");
}
