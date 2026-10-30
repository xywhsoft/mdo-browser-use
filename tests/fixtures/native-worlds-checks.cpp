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
struct xb_frame_source_identity{bool alive=true;};
struct xb_frame_session{std::shared_ptr<xb_frame_source_identity> identity=std::make_shared<xb_frame_source_identity>();};
struct xb_frame_state{bool closed=false;std::map<std::string,xb_frame_session> sessions;};
// INSERT_STATE
struct xb_view{
  bool closed=false,core=true,manual=false,failed=false,dialog=false;UINT64 navigation=1;HWND window=reinterpret_cast<HWND>(1);int automationContext=0;
  std::shared_ptr<xb_world_state> worlds;std::shared_ptr<xb_world_context> automationWorld;std::map<std::string,bool> automationRequests;
  std::shared_ptr<xb_frame_state> frames=std::make_shared<xb_frame_state>();
};
struct xb_host{
  bool alive=true;unsigned replies=0;std::string result,error;
  void reply(const std::string&,const std::string& value,const std::string& failure=""){replies++;result=value;error=failure;}
};
static bool xb_automation_live(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,UINT64 navigation){return host->alive&&!view->closed&&!view->manual&&view->navigation==navigation;}
static bool xb_popup_blocked(const std::shared_ptr<xb_view>&){return false;}
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
static void xb_cdp(const std::shared_ptr<xb_view>&,const wchar_t*,const std::string&,xb_cdp_result);
static void xb_frame_cdp(const std::shared_ptr<xb_view>&,const std::string&,const wchar_t*,const std::string&,xb_cdp_result);
// INSERT_ROUTING
// INSERT_REGISTRY
static bool xb_worlds_install(const std::shared_ptr<xb_view>& view){if(!view->worlds)view->worlds=std::make_shared<xb_world_state>();return view->worlds->error.empty()&&!view->worlds->closed;}
// INSERT_EXECUTION
// INSERT_AUTOMATION
struct command{std::wstring method;std::string params,session;};
static std::vector<command> commands;static unsigned effects=0,wrongEffects=0,serial=0;static int mode=0;
static xb_cdp_result held;static std::shared_ptr<xb_world_context> providerWorld;
static std::string creation(const std::shared_ptr<xb_world_context>& pin,int numeric=7,const std::string& unique=""){
  return "{\"context\":{\"id\":"+std::to_string(numeric)+",\"uniqueId\":"+xb_quote(unique.empty()?"realm-"+std::to_string(++serial):unique)+",\"name\":"+xb_quote(pin->name)+",\"auxData\":{\"frameId\":"+xb_quote(pin->frame)+",\"isDefault\":false,\"type\":\"isolated\"}}}";
}
static void fakeProtocol(const std::shared_ptr<xb_view>& view,const std::string& session,const wchar_t* raw,const std::string& params,xb_cdp_result complete){
  const std::wstring method=raw;commands.push_back({method,params,session});
  if(method==L"Runtime.enable"){complete(mode!=1,mode==1?"{}":"{}");return;}
  if(method==L"Page.getFrameTree"){
    std::shared_ptr<xb_world_context> proving;for(const auto& entry:view->worlds->framePending)if(entry.second->session==session&&entry.second->proving)proving=entry.second->pin;
    if(proving)complete(true,"{\"frameTree\":{\"frame\":{\"id\":"+xb_quote(proving->frame)+",\"loaderId\":"+xb_quote(mode==15?"replaced-native-loader":proving->loader)+"}}}");
    else complete(true,"{\"frameTree\":{\"frame\":{\"id\":\"original-frame\",\"loaderId\":\"original-loader\"}}}");return;
  }
  if(method==L"Page.createIsolatedWorld"){
    auto pin=view->worlds->names.at(xb_get(params,"worldName")).lock();providerWorld=pin;int numeric=7;
    for(;;){bool occupied=false;for(const auto& entry:view->worlds->names)if(auto other=entry.second.lock())occupied|=other!=pin&&other->alive&&other->registered&&other->session==session&&other->numeric==numeric;if(!occupied)break;numeric++;}
    if(mode==2){held=complete;return;}
    if(mode==3){complete(true,"{\"executionContextId\":7}");xb_world_metadata(view->worlds,"Runtime.executionContextCreated",creation(pin));return;}
    if(mode==4){complete(true,"{\"executionContextId\":7}");return;}
    auto data=creation(pin,numeric);if(mode==5)data.replace(data.find("\"isDefault\":false"),17,"\"isDefault\":true");
    xb_world_metadata(view->worlds,"Runtime.executionContextCreated",data,session);
    complete(true,"{\"executionContextId\":"+std::to_string(numeric+(mode==6?1:0))+"}");return;
  }
  if(method==L"Runtime.callFunctionOn"){
    const auto unique=xb_get(params,"uniqueContextId"),function=xb_get(params,"functionDeclaration");
    if(function=="function(){return 0;}"){
      if(unique.empty()){complete(mode==12,mode==12?"{\"result\":{\"type\":\"number\",\"value\":0}}":"{\"error\":{\"code\":-32602,\"message\":\"invalid uniqueContextId\"}}");return;}
      complete(mode!=13,mode==13?"{}":"{\"result\":{\"type\":\"number\",\"value\":0}}");return;
    }
    if(unique!=providerWorld->unique){wrongEffects++;complete(false,"{}");return;}effects++;complete(true,"{\"result\":{\"type\":\"number\",\"value\":1}}");return;
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
static void xb_cdp(const std::shared_ptr<xb_view>& view,const wchar_t* method,const std::string& params,xb_cdp_result complete){fakeProtocol(view,"",method,params,std::move(complete));}
static void xb_frame_cdp(const std::shared_ptr<xb_view>& view,const std::string& session,const wchar_t* method,const std::string& params,xb_cdp_result complete){fakeProtocol(view,session,method,params,std::move(complete));}
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
  view=fixture();xb_automation(host,view,"root-before-child","1");original=view->automationWorld;
  view->frames->sessions["child"]={};std::shared_ptr<xb_world_context> leaf;unsigned worldReplies=0;
  const auto guard=[](){return true;};const auto admitted=[&](std::shared_ptr<xb_world_context> value){worldReplies++;leaf=value;};
  xb_world_frame(host,view,view->navigation,"child","leaf","loader-1",guard,admitted);
  check(leaf&&leaf->numeric==original->numeric&&leaf->session=="child"&&leaf->unique!=original->unique&&xb_world_live(view,original),"Child numeric reuse invalidated or rebound the root source");
  for(const auto& command:commands)if(command.params.find(leaf->name)!=std::string::npos)check(command.session=="child","Frame-world creation left its original native source");
  count=commands.size();auto firstLeaf=leaf;xb_world_frame(host,view,view->navigation,"child","leaf","loader-1",guard,admitted);
  check(leaf==firstLeaf&&commands.size()==count&&worldReplies==2,"A stable original child world was recreated");
  xb_world_metadata(view->worlds,"Runtime.executionContextDestroyed","{\"executionContextId\":7,\"executionContextUniqueId\":"+xb_quote(leaf->unique)+"}","wrong-source");
  check(xb_world_live(view,leaf)&&xb_world_live(view,original),"A foreign source retired an unrelated original world");
  unsigned functions=0;auto nodeParams=xb_world_node_params(leaf,"opaque-original-object","function(){return 1;}");
  xb_world_function(view,leaf,nodeParams,guard,[&](bool ok,const std::string&){functions++;check(ok,"Original node function failed");});
  check(functions==1&&leaf->functions&&commands.size()==count+3,"Node function missed unique-context negative/positive qualification");
  check(commands.back().params.find("\"uniqueContextId\"")!=std::string::npos&&xb_get(commands.back().params,"objectId").empty()&&commands.back().params.find("node.ownerDocument===document")!=std::string::npos,"Object ID selected a function realm or lacked original-document validation");
  count=commands.size();xb_world_function(view,leaf,nodeParams,guard,[&](bool ok,const std::string&){check(ok,"Cached function admission failed");});
  check(commands.size()==count+1,"A confirmed function capability was reprobed on every call");
  xb_world_retire_frame(view,"leaf","loader-2");check(leaf->retired&&!leaf->alive&&xb_world_live(view,original),"Positive child-loader replacement did not retire only the original child");
  count=commands.size();xb_world_function(view,leaf,nodeParams,guard,[&](bool ok,const std::string&){check(!ok,"Retired child executed a node function");});
  check(commands.size()==count,"Retired cleanup selected or created a new realm");
  xb_world_frame(host,view,view->navigation,"child","leaf","loader-2",guard,admitted);check(leaf&&leaf!=firstLeaf&&leaf->unique!=firstLeaf->unique,"Explicit new child admission revived the old pin");
  auto oldSource=leaf->sourceOwner;xb_world_retire_source(view,"child",oldSource);check(!oldSource->alive&&leaf->retired&&!xb_world_live(view,leaf),"Detached native source retained execution authority");
  view->frames->sessions["child"]={};count=commands.size();xb_world_protocol(view,"child",oldSource,L"Runtime.evaluate","{}",[&](bool ok,const std::string&){check(!ok,"A reused session string revived its old source owner");});
  check(commands.size()==count,"Old source cleanup dispatched into a replacement native session");
  xb_world_frame(host,view,view->navigation,"child","leaf","loader-3",guard,admitted);check(leaf&&leaf->sourceOwner!=oldSource&&xb_world_live(view,original),"A fresh native-source incarnation was not admitted independently");
  auto beforeClear=leaf;xb_world_metadata(view->worlds,"Runtime.executionContextsCleared","{}","child");
  check(beforeClear->inventoryLost&&!beforeClear->retired&&!xb_world_live(view,beforeClear)&&xb_world_cleanup_live(view,beforeClear)&&xb_world_live(view,original),"Context inventory clearing claimed teardown, lost retained cleanup, or crossed its source boundary");
  for(int badFunction:{12,13}){
    xb_world_frame(host,view,view->navigation,"child","leaf","loader-4",guard,admitted);check(leaf!=nullptr,"Cannot prepare function capability fault");leaf->functions=false;mode=badFunction;
    auto effectCount=effects;xb_world_function(view,leaf,xb_world_node_params(leaf,"object","function(){return 1;}"),guard,[&](bool ok,const std::string&){check(!ok,"Unconfirmed unique function capability executed a payload");});
    check(effects==effectCount,"A function capability failure delivered caller effects");mode=0;
  }
  worldReplies=0;xb_world_frame(host,view,view->navigation,"child","leaf","loader-4",[](){return false;},admitted);
  check(!leaf&&worldReplies==1,"A rejected original document guard returned an active world");
  view=fixture();view->frames->sessions["child"]={};mode=15;
  xb_world_frame(host,view,view->navigation,"child","leaf","old-native-loader",guard,admitted);
  check(!leaf&&view->worlds->framePending.empty(),"A world created after child navigation adopted a replacement native loader before its event arrived");
  view=fixture();view->frames->sessions["child"]={};mode=2;worldReplies=0;
  auto guardOwner=std::make_shared<int>(1);std::weak_ptr<int> weakGuard=guardOwner;
  for(int index=0;index<32;index++)xb_world_frame(host,view,view->navigation,"child","pending-"+std::to_string(index),"loader",[guardOwner](){return *guardOwner==1;},admitted);
  check(view->worlds->framePending.size()==32&&worldReplies==0,"Frame preparation exceeded or lost its original pending budget");
  count=commands.size();xb_world_frame(host,view,view->navigation,"child","over-limit","loader",guard,admitted);
  check(worldReplies==1&&!leaf&&commands.size()==count,"A frame beyond the pending quota dispatched provider work");
  guardOwner.reset();clockNow=3101;xb_world_tick(view->window,0,0,0);
  check(view->worlds->framePending.empty()&&worldReplies==33&&weakGuard.expired()&&xb_world_views.empty(),"Concurrent frame deadlines retained guards, callbacks or a timer");
  held(true,"{\"executionContextId\":7}");check(worldReplies==33,"A late quota-expired callback replayed original delivery");held={};
  view=fixture();view->frames->sessions["child"]={};mode=2;worldReplies=0;
  xb_world_frame(host,view,view->navigation,"child","leaf","loader",guard,admitted);auto sourceBeforeReply=view->frames->sessions.at("child").identity;
  xb_world_retire_source(view,"child",sourceBeforeReply);view->frames->sessions["child"]={};held(true,"{\"executionContextId\":7}");held={};
  check(!leaf&&worldReplies==1&&view->worlds->framePending.empty(),"A late creation reply rebound a replacement source incarnation");
  view=fixture();view->frames->sessions["child"]={};xb_world_frame(host,view,view->navigation,"child","leaf","loader",guard,admitted);auto manualWorld=leaf;
  view->manual=true;count=commands.size();xb_world_frame(host,view,view->navigation,"child","leaf","loader",guard,admitted);
  check(!leaf&&commands.size()==count,"Cached world admission bypassed manual takeover");
  xb_world_frame(host,view,view->navigation,"child","leaf","loader",guard,admitted,"frames",true);
  check(leaf==manualWorld&&commands.size()==count,"Fixed original cleanup created a new world or lost its admitted pin during takeover");view->manual=false;
  const std::string nestedTree="{\"frameTree\":{\"frame\":{\"id\":\"root\",\"loaderId\":\"root-loader\"},\"childFrames\":[{\"frame\":{\"id\":\"leaf\",\"loaderId\":\"leaf-loader\"}}]}}";
  check(xb_world_tree_matches(nestedTree,"leaf","leaf-loader")&&!xb_world_tree_matches(nestedTree,"leaf","new-loader")&&!xb_world_tree_matches(nestedTree,"missing","leaf-loader"),"Native loader proof lost its exact nested frame binding");
  check(!xb_world_tree_matches("{\"frameTree\":{\"frame\":{\"id\":\"leaf\",\"loaderId\":\"leaf-loader\"},\"childFrames\":[{\"frame\":{\"id\":\"leaf\",\"loaderId\":\"leaf-loader\"}}]}}","leaf","leaf-loader"),"Duplicate native frame identity qualified a world");
  xb_abort_automation(host,view,0);xb_worlds_close(view);std::cout<<"{\"passed\":true,\"checks\":"<<checks<<",\"wrong_realm_effects\":"<<wrongEffects<<"}";return 0;
}catch(const std::exception& error){std::cerr<<error.what()<<"\n";return 1;}}
