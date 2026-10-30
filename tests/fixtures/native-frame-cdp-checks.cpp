#include <map>
#include <string>
#include <memory>
#include <functional>
#include <atomic>
#include <cstring>
#include <cassert>
#include <iostream>
#include <stdexcept>
#define STDMETHODCALLTYPE
using HRESULT=long;using ULONG=unsigned long;using LPCWSTR=const wchar_t*;using IID=int;using REFIID=const int&;
static constexpr HRESULT S_OK=0,E_FAIL=-1,E_POINTER=-2,E_NOINTERFACE=-3;
static constexpr IID xs_wv_iid_unknown=0,IID_ICoreWebView2CallDevToolsProtocolMethodCompletedHandler=1;
static bool IsEqualIID(REFIID a,REFIID b){return a==b;}
[[maybe_unused]] static bool SUCCEEDED(HRESULT value){return value>=0;}static bool FAILED(HRESULT value){return value<0;}
struct ICoreWebView2CallDevToolsProtocolMethodCompletedHandler{
  virtual HRESULT QueryInterface(REFIID,void**)=0;virtual ULONG AddRef()=0;virtual ULONG Release()=0;virtual HRESULT Invoke(HRESULT,LPCWSTR)=0;
};
static bool utf8Overflow=false,wideFails=false;
namespace webview{namespace detail{
// INSERT_PARSER
static std::wstring widen_string(const std::string& text){if(wideFails)throw std::bad_alloc();return std::wstring(text.begin(),text.end());}
static std::string narrow_string(LPCWSTR text){if(utf8Overflow)return std::string(2097153,'x');std::wstring wide(text);return std::string(wide.begin(),wide.end());}
}}
static std::string xb_get(const std::string& text,const char* key){return webview::detail::json_parse(text,key,0);}
// INSERT_CALLBACK
// INSERT_OWNER
struct core{
  ICoreWebView2CallDevToolsProtocolMethodCompletedHandler* held=nullptr;unsigned calls=0;bool reject=false,throwBefore=false,throwAfter=false;std::wstring session;
  HRESULT CallDevToolsProtocolMethod(LPCWSTR,LPCWSTR,ICoreWebView2CallDevToolsProtocolMethodCompletedHandler* callback){
    calls++;if(throwBefore)throw std::bad_alloc();if(reject)return E_FAIL;held=callback;held->AddRef();if(throwAfter)throw std::bad_alloc();return S_OK;
  }
  HRESULT CallDevToolsProtocolMethodForSession(LPCWSTR source,LPCWSTR method,LPCWSTR params,ICoreWebView2CallDevToolsProtocolMethodCompletedHandler* callback){session=source;return CallDevToolsProtocolMethod(method,params,callback);}
  void clear(){auto* previous=held;held=nullptr;if(previous)previous->Release();}
  ~core(){clear();}
};
struct frames{core* source;std::map<std::string,bool> sessions;};
struct xb_view{core* core;std::shared_ptr<::frames> frames;};
using xb_cdp_result=std::function<void(bool,const std::string&)>;
// INSERT_DISPATCH
int main(){try{
  ::core root,child;auto view=std::make_shared<xb_view>();view->core=&root;view->frames=std::make_shared<frames>();view->frames->source=&child;view->frames->sessions["original-child"]=true;
  unsigned checks=0,replies=0;bool accepted=true;std::string receipt;
  const auto check=[&](bool value,const char* reason){checks++;if(!value)throw std::runtime_error(reason);};
  const auto done=[&](bool ok,const std::string& result){replies++;accepted=ok;receipt=result;};
  const std::wstring error=L"{\"error\":{\"code\":-32602,\"message\":\"invalid uniqueContextId\"}}";
  xb_frame_cdp(view,"original-child",L"Runtime.evaluate","{}",done);
  check(child.calls==1&&root.calls==0&&child.session==L"original-child","Routed call left its original source");
  check(child.held->Invoke(E_FAIL,error.c_str())==S_OK,"Failed native callback crossed COM");
  check(replies==1&&!accepted&&receipt==std::string(error.begin(),error.end()),"Failed HRESULT erased the original bounded protocol reason");
  child.held->Invoke(S_OK,L"{\"result\":{}}");check(replies==1,"A duplicate callback republished its receipt");child.clear();
  xb_frame_cdp(view,"original-child",L"Runtime.evaluate","{}",done);child.held->Invoke(S_OK,error.c_str());
  check(replies==2&&!accepted&&xb_get(xb_get(receipt,"error"),"code")=="-32602","Protocol failure claimed transport success or lost its reason");child.clear();
  xb_frame_cdp(view,"",L"Runtime.evaluate","{}",done);root.held->Invoke(S_OK,L"{\"result\":{\"value\":1}}");
  check(replies==3&&accepted&&xb_get(receipt,"result")=="{\"value\":1}","Successful root result changed");root.clear();
  auto before=child.calls;xb_frame_cdp(view,"unknown-child",L"Runtime.evaluate","{}",done);
  check(replies==4&&!accepted&&receipt=="{}"&&child.calls==before,"Unknown native source was retargeted");
  child.reject=true;xb_frame_cdp(view,"original-child",L"Runtime.evaluate","{}",done);child.reject=false;
  check(replies==5&&!accepted&&receipt=="{}"&&!child.held,"Rejected dispatch retained a live callback");
  xb_frame_cdp(view,"original-child",L"Runtime.evaluate","{}",done);std::wstring oversized(1048577,L'x');child.held->Invoke(E_FAIL,oversized.c_str());
  check(replies==6&&!accepted&&receipt=="{}","Oversized failed output bypassed the UTF-16 bound");child.clear();
  xb_frame_cdp(view,"original-child",L"Runtime.evaluate","{}",done);utf8Overflow=true;child.held->Invoke(S_OK,L"small");utf8Overflow=false;
  check(replies==7&&!accepted&&receipt=="{}","UTF-8 expansion bypassed the result bound");child.clear();
  xb_frame_cdp(view,"original-child",L"Runtime.evaluate","{}",done);child.held->Invoke(E_FAIL,nullptr);
  check(replies==8&&!accepted&&receipt=="{}","An empty failed output invented a protocol receipt");child.clear();
  unsigned throws=0;xb_frame_cdp(view,"original-child",L"Runtime.evaluate","{}",[&](bool,const std::string&){throws++;throw std::runtime_error("lost receipt delivery");});
  check(child.held->Invoke(S_OK,L"{}") == E_FAIL,"Caller exception escaped COM");child.held->Invoke(S_OK,L"{}");
  check(throws==1,"Throwing receipt delivery was replayed");child.clear();
  auto pin=std::make_shared<int>(1);std::weak_ptr<int> weak=pin;wideFails=true;auto dispatches=root.calls;
  try{xb_frame_cdp(view,"",L"Runtime.evaluate","parameters",[pin,&done](bool ok,const std::string& value){done(ok,value);});}catch(const std::bad_alloc&){}
  wideFails=false;pin.reset();check(weak.expired()&&root.calls==dispatches,"Parameter conversion failure leaked the original routed callback");
  for(bool acceptedBeforeThrow:{false,true}){
    pin=std::make_shared<int>(1);weak=pin;root.throwBefore=!acceptedBeforeThrow;root.throwAfter=acceptedBeforeThrow;
    try{xb_frame_cdp(view,"",L"Runtime.evaluate","parameters",[pin,&done](bool ok,const std::string& value){done(ok,value);});}catch(const std::bad_alloc&){}
    pin.reset();if(acceptedBeforeThrow){check(!weak.expired(),"Accepted provider work released its original callback early");root.clear();}
    check(weak.expired(),"Provider dispatch exception leaked its caller-owned callback");
  }
  root.throwBefore=root.throwAfter=false;
  std::cout<<"{\"passed\":true,\"checks\":"<<checks<<",\"routed_failure_receipt_preserved\":true}";return 0;
}catch(const std::exception& error){std::cerr<<error.what()<<"\n";return 1;}}
