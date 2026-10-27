"""Compile the real policy lifecycle against controlled protocol/timer callbacks."""
import os
from pathlib import Path
import subprocess
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev

class DownloadPolicyTests(unittest.TestCase):
    def test_policy_acknowledgement_timeout_and_lifetime(self):
        source=(dev.ROOT/'host/native-download-arm.inc.hpp').read_text(encoding='utf-8')
        program=r'''
#include <memory>
#include <string>
#include <functional>
#include <map>
#include <vector>
#include <cassert>
#include <cstdlib>
#include <new>
static int failAfter=-1;
void* operator new(std::size_t bytes){if(failAfter>=0&&failAfter--==0){failAfter=-1;throw std::bad_alloc();}if(auto value=std::malloc(bytes))return value;throw std::bad_alloc();}
void operator delete(void* value)noexcept{std::free(value);}
void operator delete(void* value,std::size_t)noexcept{std::free(value);}
using HWND=unsigned long long;using UINT_PTR=unsigned long long;using ULONGLONG=unsigned long long;using UINT=unsigned;using DWORD=unsigned long;
#define CALLBACK
static ULONGLONG now=0;static bool failTimer=false,timerLive=false;
static ULONGLONG GetTickCount64(){return now;}
static UINT_PTR SetTimer(HWND,UINT_PTR id,UINT,void(*)(HWND,UINT,UINT_PTR,DWORD)){timerLive=!failTimer;return failTimer?0:id;}
static void KillTimer(HWND,UINT_PTR){timerLive=false;}
struct xb_download_arm_job;
struct policy_state{bool supported=true,beginSubscribed=true,progressSubscribed=true,arming=false,protocolReady=false,armed=false;std::weak_ptr<xb_download_arm_job> arm;std::string error;};
struct xb_view{bool closed=false,failed=false,manual=false,requestedClose=false;std::shared_ptr<policy_state> downloads=std::make_shared<policy_state>();};
struct xb_host{bool alive=true;HWND window=1;std::map<std::string,std::shared_ptr<xb_view>> views;};
static std::vector<std::function<void(bool,const std::string&)>> callbacks;
static void xb_cdp(const std::shared_ptr<xb_view>&,const wchar_t* method,const std::string& params,std::function<void(bool,const std::string&)> done){assert(std::wstring(method)==L"Page.enable"&&params=="{}");callbacks.push_back(std::move(done));}
static std::string xb_get(const std::string& value,const char* key){return value=="protocol-error"&&std::string(key)=="error"?"error":value=="error"&&std::string(key)=="message"?"synthetic":std::string{};}
'''+source+r'''
int main(){
  auto reset=[](){assert(xb_download_policies.empty());callbacks.clear();now=0;failTimer=false;timerLive=false;};
  auto host=std::make_shared<xb_host>();auto view=std::make_shared<xb_view>();host->views["owned"]=view;
  int calls=0;bool accepted=false;auto done=[&](bool ok,const std::string&){calls++;accepted=ok;};
  view->downloads->supported=false;xb_download_arm(host,view,done);assert(calls==1&&!accepted&&!timerLive&&callbacks.empty());view->downloads->supported=true;calls=0;
  xb_download_arm(host,view,done);assert(view->downloads->arming&&!view->downloads->armed&&timerLive&&callbacks.size()==1);
  callbacks[0](true,"{}");callbacks[0](false,"protocol-error");assert(calls==1&&accepted&&view->downloads->armed&&view->downloads->protocolReady&&!timerLive);reset();
  view->downloads=std::make_shared<policy_state>();calls=0;xb_download_arm(host,view,done);now=5001;xb_download_policy_tick(1,0,0,0);
  assert(calls==1&&!accepted&&!view->downloads->armed&&!view->downloads->arming&&!timerLive);callbacks[0](true,"{}");assert(calls==1&&!view->downloads->armed);reset();
  view->downloads=std::make_shared<policy_state>();calls=0;xb_download_arm(host,view,done);view->manual=true;xb_download_abort_arm(view,"takeover");callbacks[0](true,"{}");
  assert(calls==1&&!accepted&&!view->downloads->armed);view->manual=false;reset();
  view->downloads=std::make_shared<policy_state>();calls=0;xb_download_arm(host,view,done);view->closed=true;xb_download_abort_arm(view,"closed");callbacks[0](true,"{}");assert(calls==1&&!accepted);view->closed=false;reset();
  view->downloads=std::make_shared<policy_state>();calls=0;xb_download_arm(host,view,done);
  auto replacement=std::make_shared<xb_view>();host->views["owned"]=replacement;int second=0;xb_download_arm(host,replacement,[&](bool ok,const std::string&){assert(ok);second++;});
  callbacks[0](true,"{}");assert(calls==1&&!accepted&&!view->downloads->armed&&replacement->downloads->arming);
  callbacks[1](true,"{}");assert(second==1&&replacement->downloads->armed);host->views["owned"]=view;reset();
  view->downloads=std::make_shared<policy_state>();calls=0;xb_download_arm(host,view,done);int busy=0;
  xb_download_arm(host,view,[&](bool ok,const std::string&){assert(!ok);busy++;});assert(busy==1&&view->downloads->arming&&callbacks.size()==1);callbacks[0](true,"{}");assert(calls==1&&accepted);reset();
  view->downloads=std::make_shared<policy_state>();calls=0;failTimer=true;xb_download_arm(host,view,done);assert(calls==1&&!accepted&&callbacks.empty()&&!view->downloads->arming);reset();
  view->downloads=std::make_shared<policy_state>();calls=0;xb_download_arm(host,view,done);auto sibling=std::make_shared<xb_view>();host->views["sibling"]=sibling;failTimer=true;xb_download_arm(host,sibling,done);
  if(!timerLive||callbacks.size()!=2||calls)return 71;
  now=5001;xb_download_policy_tick(1,0,0,0);assert(calls==2&&!accepted);for(auto& callback:callbacks)callback(true,"{}");assert(calls==2);host->views.erase("sibling");reset();
  host->views.erase("owned");host->views["browser-"+std::string(32,'a')]=view;
  for(int fault=0;fault<8;fault++){
    view->downloads=std::make_shared<policy_state>();calls=0;failAfter=fault;
    try{xb_download_arm(host,view,done);}catch(...){failAfter=-1;return 72;}
    failAfter=-1;if(!callbacks.empty())callbacks[0](true,"{}");
    if(calls!=1||view->downloads->arming||timerLive||!xb_download_policies.empty())return 73;
    reset();
  }
  auto fatDone=[padding=std::string(64,'x'),&calls,&accepted](bool ok,const std::string&){assert(padding.size()==64);calls++;accepted=ok;};
  for(int fault=0;fault<12;fault++){
    view->downloads=std::make_shared<policy_state>();calls=0;failAfter=fault;
    try{xb_download_arm(host,view,fatDone);}catch(...){failAfter=-1;return 74;}
    failAfter=-1;if(!callbacks.empty())callbacks[0](true,"{}");
    if(calls!=1||view->downloads->arming||timerLive||!xb_download_policies.empty())return 75;reset();
  }
  const std::string failureText(100,'f');
  for(int fault=0;fault<8;fault++){
    view->downloads=std::make_shared<policy_state>();calls=0;xb_download_arm(host,view,done);auto job=view->downloads->arm.lock();failAfter=fault;
    try{job->finish(false,failureText);}catch(...){failAfter=-1;return 76;}
    failAfter=-1;if(calls!=1||view->downloads->arming||timerLive||!xb_download_policies.empty())return 77;reset();
  }
  for(bool nativeFailure:{false,true}){view->downloads=std::make_shared<policy_state>();calls=0;xb_download_arm(host,view,done);callbacks[0](!nativeFailure,nativeFailure?"{}":"protocol-error");assert(calls==1&&!accepted&&!view->downloads->armed);reset();}
  calls=0;view->downloads=std::make_shared<policy_state>();xb_download_arm(host,view,done);host->views.clear();view.reset();callbacks[0](true,"{}");assert(calls==1&&!accepted);reset();
  calls=0;for(int i=0;i<8;i++){auto item=std::make_shared<xb_view>();host->views[std::to_string(i)]=item;xb_download_arm(host,item,done);}assert(callbacks.size()==8);
  host->alive=false;xb_download_policy_shutdown(host);assert(calls==8&&!accepted&&!timerLive&&xb_download_policies.empty());for(auto& callback:callbacks)callback(true,"{}");assert(calls==8);reset();
  return 0;
}
'''
        directory=dev.ENV/'qa';directory.mkdir(exist_ok=True)
        cpp=directory/'native-download-policy.cpp';binary=directory/'native-download-policy.exe'
        cpp.write_text(program,encoding='utf-8',newline='\n')
        compiler=Path('E:/software/w64devkit/bin/g++.exe')
        subprocess.run([str(compiler),'-std=c++17','-O2','-s',str(cpp),'-o',str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],check=True,capture_output=True,env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))

if __name__=='__main__':unittest.main()
