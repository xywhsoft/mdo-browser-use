"""Exercise actual native screenshot ownership with retained/dropped callbacks."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


@unittest.skipUnless(os.name=='nt' and Path('E:/software/w64devkit/bin/g++.exe').is_file(),'Windows native compiler unavailable')
class NativeCaptureLifetimeTests(unittest.TestCase):
    def test_provider_acknowledgement_owns_capture_reservation(self):
        directory=dev.ENV/'qa/native-capture-lifetime';directory.mkdir(parents=True,exist_ok=True)
        source=directory/'capture.cpp';binary=directory/'capture.exe'
        prefix=r'''
#include <memory>
#include <map>
#include <vector>
#include <functional>
#include <string>
#include <stdexcept>
#include <iostream>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <new>
#include <type_traits>
static long allocationFailure=-1;static bool allocationTriggered=false;
void* operator new(std::size_t size){
 if(allocationFailure>=0&&allocationFailure--==0){allocationFailure=-1;allocationTriggered=true;throw std::bad_alloc();}
 if(void* p=std::malloc(size?size:1))return p;throw std::bad_alloc();
}
void* operator new[](std::size_t size){return ::operator new(size);}
void operator delete(void* p)noexcept{std::free(p);}void operator delete[](void* p)noexcept{std::free(p);}
void operator delete(void* p,std::size_t)noexcept{std::free(p);}void operator delete[](void* p,std::size_t)noexcept{std::free(p);}
#define CALLBACK
using UINT_PTR=std::uintptr_t;using HWND=std::uintptr_t;using UINT=unsigned;using DWORD=unsigned long;using UINT64=unsigned long long;using ULONGLONG=unsigned long long;
static ULONGLONG now=100;static bool timerFails=false,parseFails=false,throwBefore=false,throwAfter=false;
static unsigned timers=0,kills=0,dispatches=0,checks=0;
static ULONGLONG GetTickCount64(){return now;}
static bool SetTimer(HWND,UINT_PTR,unsigned,void(*)(HWND,UINT,UINT_PTR,DWORD)){timers++;return !timerFails;}
static void KillTimer(HWND,UINT_PTR){kills++;}
struct xb_capture_job;
struct xb_view {bool closed=false,requestedClose=false,failed=false,dialog=false,manual=false,uploadBusy=false,drag=false;UINT64 navigation=7;HWND window=1;std::shared_ptr<xb_capture_job> capture;};
struct xb_host {bool alive=true,failReply=false;unsigned captures=0;std::vector<std::string> replies;
 void reply(const std::string&,const std::string& result,const std::string& error=""){if(failReply)throw std::bad_alloc();replies.push_back(error.empty()?result:error);}};
static bool xb_popup_blocked(const std::shared_ptr<xb_view>&){return false;}
static std::string xb_quote(const std::string& value){return "\""+value+"\"";}
static std::string xb_get(const std::string& text,const char* key){
 const std::string k=key;
 if(text=="pixels"&&k=="data"){if(parseFails)throw std::bad_alloc();return std::string(4096,'A');}
 if(text=="huge"&&k=="data")return std::string(256000,'A');
 if(text=="clip")return k=="width"||k=="height"?"100":"";
 if(text=="request"){
  if(k=="clip")return "clip";if(k=="format")return "png";if(k=="capture_key")return "original-capture";
  if(k=="navigation")return "7";if(k=="agent")return "true";
 }return "";
}
using Reply=std::function<void(bool,const std::string&)>;static std::vector<Reply> provider;
static void xb_cdp(const std::shared_ptr<xb_view>&,const wchar_t*,const std::string&,Reply reply){
 dispatches++;if(throwBefore)throw std::bad_alloc();provider.push_back(reply);if(throwAfter)throw std::bad_alloc();
}
static void check(bool value,const char* reason){checks++;if(!value)throw std::runtime_error(reason);}
'''
        program=r'''
static std::shared_ptr<xb_view> start(const std::shared_ptr<xb_host>& host){auto view=std::make_shared<xb_view>();xb_capture(host,view,"original","request");return view;}
int main(){try{
 static_assert(!std::is_copy_constructible<xb_capture_job>::value,"Screenshot reservation is copyable");
 static_assert(!std::is_copy_constructible<xb_capture_callback>::value,"Native callback identity is copyable");
 auto host=std::make_shared<xb_host>();auto view=start(host);
 check(host->captures==1&&view->capture&&provider.size()==1,"Native capture was not reserved");
 view->closed=true;xb_cancel_capture(view);
 check(host->captures==1,"Closing released a still-retained native screenshot reservation");
 check(!view->capture,"Closed view retained its active capture cycle");
 auto denied=start(host);check(!denied->capture&&dispatches==1,"Replacement bypassed retained callback budget");
 Reply old=provider[0];old(true,"pixels");check(host->captures==0&&host->replies.size()==2,"Native acknowledgement did not release exactly once");
 view=start(host);check(host->captures==1&&provider.size()==2,"Native capture did not recover");
 old(true,"pixels");check(host->captures==1,"Duplicate old callback consumed a new screenshot reservation");
 view->closed=true;xb_cancel_capture(view);check(host->captures==1&&!view->capture,"Closed replacement lost native ownership");
 provider.clear();check(host->captures==0,"Dropped provider callback leaked its native reservation");old=nullptr;

 view=start(host);check(host->captures==1,"Live capture not admitted");
 xb_cancel_capture(view);check(host->captures==1&&view->capture,"Cancellation released native work before acknowledgement");
 provider.clear();check(host->captures==0&&!view->capture,"Dropped cancelled callback retained an active-view cycle");
 view=start(host);provider.clear();check(host->captures==0&&!view->capture,"Dropped live callback retained an active-view cycle");
 check(host->replies.back()=="Native screenshot callback was released","Dropped callback did not produce a terminal failure");

 view=start(host);Reply retry=provider.back();auto replies=host->replies.size();retry(true,"huge");
 check(host->captures==1&&view->capture&&view->capture->attempts==2,"Byte-budget retry did not retain one native reservation");
 retry(true,"pixels");check(host->captures==1&&host->replies.size()==replies,"Old callback completed a newer screenshot attempt");
 provider.back()(true,"pixels");check(host->captures==0&&!view->capture,"New screenshot attempt did not complete");provider.clear();retry=nullptr;

 view=start(host);Reply completed=provider.back();completed(true,"pixels");
 check(host->captures==0&&!view->capture&&host->replies.back().find("\"ok\":true")!=std::string::npos,"Completed screenshot did not settle");
 replies=host->replies.size();completed(true,"pixels");check(host->replies.size()==replies,"Duplicate screenshot callback published twice");provider.clear();completed=nullptr;

 timerFails=true;view=start(host);timerFails=false;check(host->captures==0&&!view->capture,"Timer admission leaked screenshot ownership");
 throwBefore=true;view=start(host);throwBefore=false;check(host->captures==0&&!view->capture,"Pre-provider allocation failure leaked screenshot ownership");
 throwAfter=true;view=start(host);throwAfter=false;
 check(host->captures==1&&view->capture,"Uncertain native dispatch returned its reservation early");
 provider.clear();check(host->captures==0&&!view->capture,"Uncertain native dispatch did not settle on callback destruction");

 view=start(host);parseFails=true;provider.back()(true,"pixels");parseFails=false;
 check(host->captures==0&&!view->capture,"Native result allocation failure leaked screenshot ownership");provider.clear();
 view=start(host);host->failReply=true;provider.back()(true,"pixels");host->failReply=false;
 check(host->captures==0&&!view->capture,"Lost screenshot reply retained native ownership");provider.clear();
 view=start(host);host->failReply=true;provider.clear();host->failReply=false;
 check(host->captures==0&&!view->capture,"Dropped callback/reply failure retained native ownership");
 view=start(host);host->alive=false;xb_cancel_capture(view);check(host->captures==1&&!view->capture,"Host shutdown released retained native work early");
 provider.clear();check(host->captures==0&&xb_capture_jobs.empty(),"Host shutdown did not drain native work");
 unsigned admissionFaults=0,resultFaults=0;
 for(unsigned stage=0;stage<2;stage++)for(long index=0;index<256;index++){
  std::vector<Reply>().swap(provider);auto scoped=std::make_shared<xb_host>();auto target=std::make_shared<xb_view>();
  if(stage==1)xb_capture(scoped,target,"original","request");
  allocationFailure=index;allocationTriggered=false;
  try{if(stage==0)xb_capture(scoped,target,"original","request");else provider.back()(true,"pixels");}
  catch(const std::bad_alloc&){}allocationFailure=-1;
  bool injected=allocationTriggered;if(injected)(stage==0?admissionFaults:resultFaults)++;
  target->closed=true;xb_cancel_capture(target);provider.clear();
  check(scoped->captures==0&&!target->capture&&xb_capture_jobs.empty(),"Allocation fault leaked native screenshot ownership");
  if(!injected)break;if(index==255)throw std::runtime_error("Screenshot allocation sweep did not terminate");
 }
 check(admissionFaults>0&&resultFaults>0,"Native screenshot allocation fault seam was inactive");
 std::cout<<"{\"passed\":true,\"checks\":"<<checks<<",\"native_dispatches\":"<<dispatches
  <<",\"admission_allocation_faults\":"<<admissionFaults<<",\"result_allocation_faults\":"<<resultFaults<<"}";return 0;
}catch(const std::exception& error){std::cerr<<error.what()<<"\n";return 1;}}
'''
        source.write_text(prefix+(dev.ROOT/'host/native-capture.inc.hpp').read_text(encoding='utf-8')+program,encoding='utf-8',newline='\n')
        build=subprocess.run(['E:/software/w64devkit/bin/g++.exe','-std=c++17','-O1','-Wall','-Wextra','-Werror',
            '-Wno-misleading-indentation','-Wno-mismatched-new-delete',str(source),'-o',str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=45)
        self.assertEqual(build.returncode,0,build.stderr[-2200:])
        run=subprocess.run([str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=10)
        self.assertEqual(run.returncode,0,run.stderr[-1600:]);print(run.stdout)


if __name__=='__main__':unittest.main()
