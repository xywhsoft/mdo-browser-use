/* Included in the native browser namespace. CDP remains private to the trusted
 * application document. Models receive typed operations, never CDP/script. */
using xb_cdp_result = std::function<void(bool,const std::string&)>;
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
  return host->alive&&!view->closed&&!view->failed&&!view->manual&&view->navigation==navigation;
}
static void xb_automation(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,
                          const std::string& id,const std::string& script){
  if(script.size()>131072){host->reply(id,"","Automation script exceeds 128 KiB");return;}
  const auto navigation=view->navigation;
  const auto evaluate=[host,view,id,script,navigation](int context){
    if(!xb_automation_live(host,view,navigation)){host->reply(id,"","Browser control or document changed");return;}
    const auto params="{\"expression\":"+xb_quote(script)+",\"contextId\":"+std::to_string(context)+
      ",\"returnByValue\":true,\"awaitPromise\":false,\"timeout\":3000}";
    xb_cdp(view,L"Runtime.evaluate",params,[host,view,id,navigation](bool ok,const std::string& result){
      if(!xb_automation_live(host,view,navigation)){host->reply(id,"","Browser control or document changed during evaluation");return;}
      const auto value=xb_get(xb_get(result,"result"),"value");
      if(!ok||!xb_get(result,"exceptionDetails").empty()||value.empty())host->reply(id,"","Isolated page evaluation failed");
      else host->reply(id,"{\"ok\":true,\"value\":"+value+"}");
    });
  };
  if(view->automationContext){evaluate(view->automationContext);return;}
  xb_cdp(view,L"Page.getFrameTree","{}",[host,view,id,navigation,evaluate](bool ok,const std::string& tree){
    if(!ok||!xb_automation_live(host,view,navigation)){host->reply(id,"","Cannot resolve current document");return;}
    const auto frame=xb_get(xb_get(xb_get(tree,"frameTree"),"frame"),"id");
    if(frame.empty()){host->reply(id,"","Current document has no frame identity");return;}
    xb_cdp(view,L"Page.createIsolatedWorld","{\"frameId\":"+xb_quote(frame)+",\"worldName\":\"mdo.browser-use.v1\"}",
      [host,view,id,navigation,evaluate](bool ready,const std::string& result){
        int context=0;try{context=std::stoi(xb_get(result,"executionContextId"));}catch(...){}
        if(!ready||context<=0||!xb_automation_live(host,view,navigation)){host->reply(id,"","Cannot create isolated browser world");return;}
        view->automationContext=context;evaluate(context);
      });
  });
}

struct xb_input_step{const wchar_t* method;std::string params;bool release=false;};
struct xb_input_sequence:std::enable_shared_from_this<xb_input_sequence>{
  std::shared_ptr<xb_host> host;std::shared_ptr<xb_view> view;std::string id,error;
  UINT64 navigation;std::vector<xb_input_step> steps;size_t index=0;
  void run(){
    if(index==steps.size()){host->reply(id,error.empty()?"{\"accepted\":true,\"trusted_input\":true}":"",error);return;}
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
  sequence->run();
}
