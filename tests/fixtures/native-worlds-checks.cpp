#define NOMINMAX
#include <windows.h>
#include <cstdint>
#include <cstring>
#include <string>
#include <map>
#include <vector>
#include <memory>
#include <functional>
#include <stdexcept>
#include <iostream>
#include <algorithm>
#include <cassert>
namespace webview{namespace detail{
// INSERT_PARSER
static std::string narrow_string(const std::wstring& text){return std::string(text.begin(),text.end());}
}}
static std::string xb_get(const std::string& text,const char* key){return webview::detail::json_parse(text,key,0);}
static std::string xb_quote(const std::string& text){return webview::detail::json_escape(text);}
// INSERT_STATE
struct xb_view{
  bool closed=false,core=true,manual=false;UINT64 navigation=1;HWND window=reinterpret_cast<HWND>(1);int automationContext=0;
  std::shared_ptr<xb_world_state> worlds;std::shared_ptr<xb_world_context> automationWorld;std::map<std::string,bool> automationRequests;
};
struct xb_host{
  bool alive=true;unsigned replies=0;std::string result,error;
  void reply(const std::string&,const std::string& value,const std::string& failure=""){replies++;result=value;error=failure;}
};
static bool xb_automation_live(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,UINT64 navigation){return host->alive&&!view->closed&&!view->manual&&view->navigation==navigation;}
static unsigned long long clockNow=100;static bool timerWorks=true;static unsigned kills=0;
static ULONGLONG fakeClock(){return clockNow;}
static UINT_PTR fakeTimer(HWND,UINT_PTR id,UINT,TIMERPROC){return timerWorks?id:0;}
static BOOL fakeKill(HWND,UINT_PTR){kills++;return TRUE;}
#define GetTickCount64 fakeClock
#define SetTimer fakeTimer
#define KillTimer fakeKill
static constexpr UINT_PTR xb_world_timer=0xBADD9;
static std::map<HWND,std::weak_ptr<xb_view>> xb_world_views;
using xb_world_result=std::function<void(std::shared_ptr<xb_world_context>)>;
using xb_cdp_result=std::function<void(bool,const std::string&)>;
// INSERT_REGISTRY
static bool xb_worlds_install(const std::shared_ptr<xb_view>& view){if(!view->worlds)view->worlds=std::make_shared<xb_world_state>();return view->worlds->error.empty()&&!view->worlds->closed;}
static void xb_cdp(const std::shared_ptr<xb_view>&,const wchar_t*,const std::string&,xb_cdp_result);
// INSERT_EXECUTION
// INSERT_AUTOMATION
struct command{std::wstring method;std::string params;};
static std::vector<command> commands;static unsigned effects=0,wrongEffects=0,serial=0;static int mode=0;
static xb_cdp_result held;static std::shared_ptr<xb_world_context> providerWorld;
static std::string creation(const std::shared_ptr<xb_world_context>& pin,int numeric=7,const std::string& unique=""){
  return "{\"context\":{\"id\":"+std::to_string(numeric)+",\"uniqueId\":"+xb_quote(unique.empty()?"realm-"+std::to_string(++serial):unique)+",\"name\":"+xb_quote(pin->name)+",\"auxData\":{\"frameId\":"+xb_quote(pin->frame)+",\"isDefault\":false,\"type\":\"isolated\"}}}";
}
static void xb_cdp(const std::shared_ptr<xb_view>& view,const wchar_t* raw,const std::string& params,xb_cdp_result complete){
  const std::wstring method=raw;commands.push_back({method,params});
  if(method==L"Runtime.enable"){complete(mode!=1,mode==1?"{}":"{}");return;}
  if(method==L"Page.getFrameTree"){complete(true,"{\"frameTree\":{\"frame\":{\"id\":\"original-frame\",\"loaderId\":\"original-loader\"}}}");return;}
  if(method==L"Page.createIsolatedWorld"){
    auto pin=view->worlds->pending->pin;providerWorld=pin;
    if(mode==2){held=complete;return;}
    if(mode==3){complete(true,"{\"executionContextId\":7}");xb_world_metadata(view->worlds,"Runtime.executionContextCreated",creation(pin));return;}
    if(mode==4){complete(true,"{\"executionContextId\":7}");return;}
    auto data=creation(pin);if(mode==5)data.replace(data.find("\"isDefault\":false"),17,"\"isDefault\":true");
    xb_world_metadata(view->worlds,"Runtime.executionContextCreated",data);
    complete(true,mode==6?"{\"executionContextId\":8}":"{\"executionContextId\":7}");return;
  }
  if(method!=L"Runtime.evaluate")throw std::runtime_error("Unexpected protocol work");
  if(mode==11)throw std::bad_alloc();
  const auto unique=xb_get(params,"uniqueContextId"),expression=xb_get(params,"expression");
  if(expression=="void 0"){
    if(unique.empty()){complete(mode==7,mode==7?"{\"result\":{\"type\":\"undefined\"}}":"{\"error\":{\"code\":-32602,\"message\":\"invalid uniqueContextId\"}}");return;}
    complete(mode!=8,mode==8?"{}":"{\"result\":{\"type\":\"undefined\"}}");return;
  }
  if(mode==9){held=complete;return;}
  if(mode==10){providerWorld->alive=false;complete(true,"{\"result\":{\"type\":\"number\",\"value\":1}}");return;}
  if(unique.empty()||unique!=providerWorld->unique){
    if(xb_get(params,"contextId")==std::to_string(providerWorld->numeric))wrongEffects++;
    else{complete(false,"{\"error\":{\"code\":-32602,\"message\":\"uniqueContextId not found\"}}");return;}
  }else effects++;
  complete(true,"{\"result\":{\"type\":\"number\",\"value\":1}}");
}
static unsigned checks=0;static void check(bool value,const char* text){checks++;if(!value)throw std::runtime_error(text);}
static std::shared_ptr<xb_view> fixture(){clockNow=100;timerWorks=true;commands.clear();held={};mode=0;providerWorld.reset();return std::make_shared<xb_view>();}
int main(){try{
  auto host=std::make_shared<xb_host>();auto view=fixture();
  xb_automation(host,view,"one","1");auto original=view->automationWorld;
  check(original&&xb_world_live(view,original)&&view->automationContext==7,"Original native world was not admitted");
  check(host->error.empty()&&host->result=="{\"ok\":true,\"value\":1}","Caller result changed");
  check(commands.size()==6&&commands[3].params.find("\"uniqueContextId\":\"\"")!=std::string::npos,"Missing negative unique-ID proof");
  check(commands.back().params.find("\"contextId\"")==std::string::npos&&commands.back().params.find(original->unique)!=std::string::npos,"Caller selected a numeric/default realm");
  auto count=commands.size();xb_automation(host,view,"two","1");check(commands.size()==count+1&&view->automationWorld==original,"Stable original world was recreated");
  auto child=creation(original,8,"child-realm");auto childFrame=child.find("original-frame");child.replace(childFrame,14,"child-frame");
  xb_world_metadata(view->worlds,"Runtime.executionContextCreated",child);
  check(xb_world_live(view,original)&&original->numeric==7&&view->worlds->otherFrames==1,"A same-name child world rebound or invalidated the original root");
  xb_world_metadata(view->worlds,"Runtime.executionContextCreated",creation(original,7,original->unique));
  check(xb_world_live(view,original)&&view->worlds->refreshes==1,"An identical native context refresh was mistaken for a replacement");
  auto names=view->worlds->created;view->navigation++;view->automationWorld.reset();view->automationContext=0;
  xb_automation(host,view,"original-response","1");
  check(view->automationWorld==original&&view->worlds->created==names&&view->worlds->documentWorlds==1,"An original-response navigation allocated a new realm in the same DOM");
  xb_world_metadata(view->worlds,"Runtime.executionContextDestroyed","{\"executionContextId\":7,\"executionContextUniqueId\":\"unrelated-old-realm\"}");
  check(xb_world_live(view,original),"A stale numeric destruction retired a different unique context");
  xb_world_metadata(view->worlds,"Runtime.executionContextDestroyed","{\"executionContextId\":7,\"executionContextUniqueId\":"+xb_quote(original->unique)+"}");
  check(!xb_world_live(view,original),"Original unique-context destruction was ignored");
  xb_automation(host,view,"fresh","1");auto fresh=view->automationWorld;
  check(fresh&&fresh!=original&&fresh->numeric==original->numeric&&fresh->unique!=original->unique,"Legitimate renderer numeric reuse was not admitted with a fresh pin");
  check(!original->alive&&!xb_world_live(view,original)&&xb_world_selector(original).find(original->unique)!=std::string::npos,"Numeric reuse rebound the old original pin");
  check(wrongEffects==0,"A caller effect escaped its original realm");
  // Renderer reuse can happen before the native event reaches the UI queue.
  // The provider now maps numeric 7 to a new realm; the original unique ID
  // must reject the request, with no fallback or replay into that new realm.
  auto replacement=std::make_shared<xb_world_context>();replacement->numeric=7;replacement->unique="replacement-before-event";providerWorld=replacement;
  auto dispatched=commands.size();auto beforeEffects=effects;xb_automation(host,view,"reuse-before-event","page-effect");
  check(!host->error.empty()&&effects==beforeEffects&&wrongEffects==0&&commands.size()==dispatched+1,"Numeric reuse before its event replayed into the new realm");
  xb_cdp(view,L"Runtime.evaluate","{\"expression\":\"negative-control\",\"contextId\":7}",[](bool,const std::string&){});
  check(wrongEffects==1,"The fixture did not reproduce numeric-ID redirection in its negative control");wrongEffects=0;providerWorld=fresh;
  auto duplicate=std::make_shared<xb_world_context>();duplicate->name="duplicate";duplicate->frame=fresh->frame;view->worlds->names[duplicate->name]=duplicate;
  xb_world_metadata(view->worlds,"Runtime.executionContextCreated",creation(duplicate,8,fresh->unique));
  check(!duplicate->alive&&!view->worlds->error.empty(),"Provider unique-ID reuse was accepted");
  for(int failure:{1,5,6,7,8}){
    view=fixture();mode=failure;host->error.clear();auto before=effects;xb_automation(host,view,"rejected","page-effect");
    check(!host->error.empty()&&!view->automationWorld&&effects==before,"Admission failure still executed the caller");
    check(!view->worlds->pending&&xb_world_views.empty(),"Rejected admission retained callbacks or its timer");
  }
  view=fixture();mode=3;xb_automation(host,view,"late-event","1");check(view->worlds->pending!=nullptr,"Late creation event was not retained");
  xb_world_tick(view->window,0,0,0);check(view->automationWorld&&host->error.empty(),"Ordered-late context metadata could not complete admission");
  view=fixture();mode=4;xb_automation(host,view,"absent-event","page-effect");clockNow=3101;auto before=effects;
  xb_world_tick(view->window,0,0,0);check(!view->worlds->pending&&effects==before&&!host->error.empty(),"Missing metadata did not expire without effects");
  view=fixture();mode=3;xb_automation(host,view,"timer-fault","page-effect");mode=11;
  xb_world_tick(view->window,0,0,0);check(!view->worlds->pending&&!host->error.empty(),"An allocation failure escaped the native timer or retained the request");
  view=fixture();mode=2;unsigned completions=0;
  xb_world_root(host,view,view->navigation,[&](std::shared_ptr<xb_world_context> pin){completions++;check(pin!=nullptr,"Original waiter failed");throw std::runtime_error("lost delivery");});
  xb_world_root(host,view,view->navigation,[&](std::shared_ptr<xb_world_context> pin){completions++;check(pin!=nullptr,"Another waiter was lost with the first delivery");});
  xb_world_metadata(view->worlds,"Runtime.executionContextCreated",creation(view->worlds->pending->pin));mode=0;held(true,"{\"executionContextId\":7}");
  check(completions==2&&!view->worlds->pending&&xb_world_views.empty(),"A throwing waiter replayed or abandoned another delivery");
  view=fixture();mode=2;xb_automation(host,view,"navigation","page-effect");auto abandoned=view->worlds->pending->pin;
  view->navigation++;xb_world_tick(view->window,0,0,0);auto replies=host->replies;held(true,"{\"executionContextId\":7}");
  check(!abandoned->alive&&!view->automationWorld&&host->replies==replies,"Late creation callback revived a navigated request");
  view=fixture();mode=2;for(int index=0;index<33;index++)xb_automation(host,view,"waiter-"+std::to_string(index),"1");
  check(view->worlds->pending->waiters.size()==32&&view->automationRequests.size()==32,"Root admission waiters exceeded their bound");
  auto pending=view->worlds->pending;view->closed=true;xb_worlds_close(view);
  check(pending->done&&pending->waiters.empty()&&view->automationRequests.empty()&&xb_world_views.empty(),"Close did not drain original waiters");
  view=fixture();timerWorks=false;xb_automation(host,view,"timer","1");check(commands.empty()&&!view->worlds->pending,"Failed timer still dispatched protocol work");
  view=fixture();xb_worlds_install(view);view->worlds->created=1024;before=effects;xb_automation(host,view,"limit","page-effect");
  check(effects==before&&view->worlds->error=="world_identity_limit"&&!view->worlds->pending,"Identity-history bound was not enforced");
  view=fixture();xb_worlds_install(view);view->worlds->documentProof="original-frame\noriginal-loader";view->worlds->documentWorlds=32;before=effects;
  xb_automation(host,view,"document-limit","page-effect");check(effects==before&&view->worlds->error=="world_identity_limit","One surviving DOM exceeded its world-name budget");
  view=fixture();xb_automation(host,view,"ready","1");mode=9;host->error.clear();xb_automation(host,view,"late-value","page-effect");original=view->automationWorld;
  xb_world_metadata(view->worlds,"Runtime.executionContextsCleared","{}");held(true,"{\"result\":{\"value\":1}}");
  check(!host->error.empty()&&!original->alive&&view->automationRequests.empty(),"An old caller acknowledgement crossed realm retirement");
  check(xb_world_number("2147483647")==INT32_MAX&&xb_world_number("2147483648")==0&&xb_world_number("7x")==0&&xb_world_number("-1")==0,"Numeric metadata parser accepted an ambiguous ID");
  check(!xb_world_negative_proof(false,"{\"error\":{\"code\":-32602,\"message\":\"unknown parameter\"}}")&&!xb_world_negative_proof(false,"{}"),"An unrelated failure qualified unique-ID support");
  view=fixture();xb_automation(host,view,"pinned","1");original=view->automationWorld;
  xb_world_metadata(view->worlds,"Runtime.executionContextCreated",creation(original,7,"new-unique-for-old-name"));
  check(!original->alive&&view->worlds->error=="world_context_metadata_rebound","A new unique identity rebound an admitted name");
  xb_world_metadata(view->worlds,"Runtime.executionContextCreated",creation(original,7,original->unique));
  check(!original->alive,"An identical late metadata refresh revived a retired pin");
  count=commands.size();xb_automation(host,view,"rebound-denied","page-effect");
  check(commands.size()==count&&!host->error.empty(),"A failed identity admission dispatched fallback work");
  xb_abort_automation(host,view,0);xb_worlds_close(view);std::cout<<"{\"passed\":true,\"checks\":"<<checks<<",\"wrong_realm_effects\":"<<wrongEffects<<"}";return 0;
}catch(const std::exception& error){std::cerr<<error.what()<<"\n";return 1;}}
