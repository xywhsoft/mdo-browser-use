"""Compile the actual COM callback/dispatch code and inject conversion faults."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


@unittest.skipUnless(os.name=='nt' and Path('E:/software/w64devkit/bin/g++.exe').is_file(),'Windows native compiler unavailable')
class NativeCdpLifetimeTests(unittest.TestCase):
    def test_callback_reference_is_owned_across_conversion_and_dispatch_faults(self):
        directory=dev.ENV/'qa/native-cdp-lifetime';directory.mkdir(parents=True,exist_ok=True)
        source=directory/'dispatch.cpp';binary=directory/'dispatch.exe'
        production=(dev.ROOT/'host/native-automation.inc.hpp').read_text(encoding='utf-8')
        dispatch=production[production.index('static void xb_cdp('):production.index('static bool xb_automation_live(')]
        native=(dev.ENV/'native-source/src/webview/xs_browser.inc.hpp').read_text(encoding='utf-8')
        callback=native[native.index('template<class I,typename... Args> class xb_callback'):native.index('struct xb_input_sequence;')]
        support=(dev.ENV/'native-source/src/webview/xs_directory_drop.inc.hpp').read_text(encoding='utf-8')
        owner=support[support.index('template <typename T> struct xs_wv_com'):support.index('struct xs_wv_text')]
        prefix=r'''
#include <memory>
#include <functional>
#include <atomic>
#include <string>
#include <stdexcept>
#include <iostream>
#define STDMETHODCALLTYPE
using HRESULT=long;using ULONG=unsigned long;using LPCWSTR=const wchar_t*;using IID=int;using REFIID=const int&;
static constexpr HRESULT S_OK=0,E_FAIL=-1,E_POINTER=-2,E_NOINTERFACE=-3;
static constexpr IID xs_wv_iid_unknown=0,IID_ICoreWebView2CallDevToolsProtocolMethodCompletedHandler=1;
static bool IsEqualIID(REFIID a,REFIID b){return a==b;}
static bool SUCCEEDED(HRESULT value){return value>=0;}static bool FAILED(HRESULT value){return value<0;}
struct ICoreWebView2CallDevToolsProtocolMethodCompletedHandler {
 virtual HRESULT QueryInterface(REFIID,void**)=0;virtual ULONG AddRef()=0;virtual ULONG Release()=0;virtual HRESULT Invoke(HRESULT,LPCWSTR)=0;
};
static bool wideFails=false,narrowFails=false;static unsigned checks=0;
namespace webview {namespace detail {
static std::wstring widen_string(const std::string& value){if(wideFails)throw std::bad_alloc();return std::wstring(value.begin(),value.end());}
static std::string narrow_string(LPCWSTR value){if(narrowFails)throw std::bad_alloc();std::wstring text(value);return std::string(text.begin(),text.end());}
}}
struct Core {
 ICoreWebView2CallDevToolsProtocolMethodCompletedHandler* held=nullptr;bool fail=false,throwBefore=false,throwAfter=false;unsigned calls=0;
 HRESULT CallDevToolsProtocolMethod(LPCWSTR,LPCWSTR,ICoreWebView2CallDevToolsProtocolMethodCompletedHandler* callback){
  calls++;if(throwBefore)throw std::bad_alloc();if(fail)return E_FAIL;
  held=callback;held->AddRef();if(throwAfter)throw std::bad_alloc();return S_OK;
 }
 void release(){auto* original=held;held=nullptr;if(original)original->Release();}
 ~Core(){release();}
};
struct xb_view {Core* core;};using xb_cdp_result=std::function<void(bool,const std::string&)>;
static void check(bool value,const char* reason){checks++;if(!value)throw std::runtime_error(reason);}
'''
        program=r'''
int main(){try{
 Core core;auto view=std::make_shared<xb_view>();view->core=&core;unsigned replies=0;
 auto pin=std::make_shared<int>(1);std::weak_ptr<int> weak=pin;wideFails=true;
 try{xb_cdp(view,L"Page.captureScreenshot","parameters",[pin,&replies](bool,const std::string&){replies++;});}catch(const std::bad_alloc&){}
 wideFails=false;pin.reset();check(weak.expired(),"Parameter conversion failure leaked the native CDP callback");check(core.calls==0&&replies==0,"Conversion failure dispatched native work");
 for(unsigned kind=0;kind<3;kind++){
  core.throwBefore=kind==0;core.throwAfter=kind==1;core.fail=kind==2;
  pin=std::make_shared<int>(1);weak=pin;
  try{xb_cdp(view,L"Page.captureScreenshot","parameters",[pin,&replies](bool ok,const std::string&){check(!ok,"Failed native dispatch claimed success");replies++;});}catch(const std::bad_alloc&){}
  pin.reset();if(kind==1){check(!weak.expired(),"Accepted native callback returned ownership early");core.release();}
  check(weak.expired(),"Native dispatch failure leaked its caller callback reference");
 }
 core.throwBefore=core.throwAfter=core.fail=false;
 pin=std::make_shared<int>(1);weak=pin;
 xb_cdp(view,L"Page.captureScreenshot","parameters",[pin,&replies](bool ok,const std::string& text){check(ok&&text=="native-data","Native CDP reply changed");replies++;});
 pin.reset();check(!weak.expired(),"Native dispatch released its retained callback");auto before=replies;
 check(core.held->Invoke(S_OK,L"native-data")==S_OK&&replies==before+1,"Native callback did not terminate");core.release();check(weak.expired(),"Completed native callback leaked");
 pin=std::make_shared<int>(1);weak=pin;
 xb_cdp(view,L"Page.captureScreenshot","parameters",[pin,&replies](bool,const std::string&){replies++;});pin.reset();before=replies;narrowFails=true;
 check(core.held->Invoke(S_OK,L"native-data")==E_FAIL&&replies==before,"Conversion fault crossed COM or replayed a callback");
 narrowFails=false;core.release();check(weak.expired(),"Failed result conversion retained native callback ownership");
 std::cout<<"{\"passed\":true,\"checks\":"<<checks<<"}";return 0;
}catch(const std::exception& error){std::cerr<<error.what()<<"\n";return 1;}}
'''
        source.write_text(prefix+owner+callback+dispatch+program,encoding='utf-8',newline='\n')
        build=subprocess.run(['E:/software/w64devkit/bin/g++.exe','-std=c++17','-O1','-Wall','-Wextra','-Werror',
            '-Wno-misleading-indentation',str(source),'-o',str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=45)
        self.assertEqual(build.returncode,0,build.stderr[-2200:])
        run=subprocess.run([str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=10)
        self.assertEqual(run.returncode,0,run.stderr[-1600:]);print(run.stdout)


if __name__=='__main__':unittest.main()
