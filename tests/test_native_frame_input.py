"""Compile the actual sequence guard and interrupt it between native steps."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class NativeFrameInputTests(unittest.TestCase):
    def test_frame_chain_and_release_after_interruption(self):
        source=(dev.ROOT/'host/native-automation.inc.hpp').read_text(encoding='utf-8')
        fragment=source.split('// BROWSER_FRAME_INPUT_GUARD_BEGIN',1)[1].split('// BROWSER_FRAME_INPUT_GUARD_END',1)[0]
        program=r'''
#include <map>
#include <vector>
#include <memory>
#include <string>
#include <functional>
#include <utility>
#include <new>
#include <cstdlib>
static bool refuseAlloc=false;
void* operator new(std::size_t size){if(refuseAlloc)throw std::bad_alloc();if(auto value=std::malloc(size))return value;throw std::bad_alloc();}
void operator delete(void* value)noexcept{std::free(value);}
void operator delete(void* value,std::size_t)noexcept{std::free(value);}
using UINT64=unsigned long long;
struct xb_frame_node{std::string raw,parent,id,document,session;bool loading=false;};
struct xb_frame_session{bool ready=true;std::string error;};
struct xb_frame_state{bool closed=false;std::string root="root",error;std::map<std::string,xb_frame_node> nodes;std::map<std::string,xb_frame_session> sessions;};
struct xb_view{bool live=true;std::shared_ptr<xb_frame_state> frames=std::make_shared<xb_frame_state>();};
struct xb_host{int replies=0;std::string value,error;void reply(const std::string&,const std::string& result,const std::string& failure=""){replies++;value=result;error=failure;}};
static bool xb_automation_live(const std::shared_ptr<xb_host>&,const std::shared_ptr<xb_view>& view,UINT64){return view->live;}
static std::vector<std::string> sent;
static std::function<void(const std::string&)> between;
static bool failPress=false;
static void xb_cdp(const std::shared_ptr<xb_view>&,const wchar_t*,const std::string& params,std::function<void(bool,const std::string&)> done){sent.push_back(params);if(between)between(params);done(!failPress||params!="press","{}");}
'''+fragment+r'''
int main(){
 auto view=std::make_shared<xb_view>();auto state=view->frames;
 state->nodes["root"]={"root","","f0","d0","",false};
 state->nodes["parent"]={"parent","root","f1","d1","parent-session",false};
 state->nodes["child"]={"child","parent","f2","d2","child-session",false};
 state->sessions["parent-session"]={};state->sessions["child-session"]={};
 xb_input_frame_guard pins={{"f2","d2"},{"f1","d1"},{"f0","d0"}};
 if(!xb_input_frames_live(view,pins)||!xb_input_frames_live(view,{}))return 1;
 if(xb_input_frames_live(view,{{"f2","d2"},{"f0","d0"}})||xb_input_frames_live(view,{{"f1","d1"},{"f2","d2"},{"f0","d0"}})||
    xb_input_frames_live(view,{{"f2","wrong"},{"f1","d1"},{"f0","d0"}})||xb_input_frames_live(view,{{"f2","d2"},{"f1","d1"}}))return 2;
 state->nodes["parent"].loading=true;if(xb_input_frames_live(view,pins))return 3;state->nodes["parent"].loading=false;
 state->sessions["child-session"].ready=false;if(xb_input_frames_live(view,pins))return 4;state->sessions["child-session"].ready=true;
 state->error="frame_limit";if(xb_input_frames_live(view,pins))return 9;state->error.clear();
 auto run=[&](std::vector<xb_input_step> steps,xb_input_frame_guard guard,xb_input_frame_snapshot snapshot={}){
   auto host=std::make_shared<xb_host>();auto sequence=std::make_shared<xb_input_sequence>();sequence->host=host;sequence->view=view;
   sequence->id="fixture";sequence->navigation=1;sequence->frameGuard=guard;sequence->frameSnapshot=snapshot;sequence->steps=steps;sent.clear();sequence->run();return host;
 };
 between=[&](const std::string& step){if(step=="move")state->nodes["child"].document="new-child";};
 auto rejected=run({{L"mouse","move"},{L"mouse","press",false,true},{L"mouse","release",true}},pins);
 if(sent!=std::vector<std::string>{"move"}||rejected->replies!=1||rejected->error.empty())return 5;
 state->nodes["child"].document="d2";
 between=[&](const std::string& step){if(step=="keyDown")state->nodes["parent"].document="new-parent";};
 auto interrupted=run({{L"key","keyDown",false,true},{L"text","text"},{L"key","keyUp",true}},pins);
 if(sent!=std::vector<std::string>{"keyDown","keyUp"}||interrupted->replies!=1||interrupted->error.empty())return 6;
 state->nodes["parent"].document="d1";between={};
 auto live=run({{L"mouse","move"},{L"mouse","press",false,true},{L"mouse","release",true}},pins);
 if(sent.size()!=3||live->replies!=1||!live->error.empty())return 7;
 between=[&](const std::string& step){if(step=="keyDown")view->live=false;};
 auto manual=run({{L"key","keyDown",false,true},{L"text","text"},{L"key","keyUp",true}},pins);
 if(sent!=std::vector<std::string>{"keyDown","keyUp"}||manual->error.empty())return 8;
 view->live=true;between={};state->nodes["child"].document="stale";
 auto before=run({{L"mouse","move"},{L"mouse","press",false,true},{L"mouse","release",true}},pins);
 if(!sent.empty()||before->error.empty())return 10;state->nodes["child"].document="d2";
 between=[&](const std::string& step){if(step=="release")state->nodes["child"].document="next";};
 auto doubleClick=run({{L"mouse","move"},{L"mouse","press",false,true},{L"mouse","release",true},{L"mouse","press",false,true},{L"mouse","release",true}},pins);
 if(sent!=std::vector<std::string>{"move","press","release"}||doubleClick->error.empty())return 11;
 state->nodes["child"].document="d2";between={};failPress=true;
 auto unknown=run({{L"mouse","press",false,true},{L"mouse","release",true}},pins);
 if(sent!=std::vector<std::string>{"press","release"}||unknown->error.empty())return 12;
 failPress=false;between={};
 xb_input_frame_snapshot snapshot={{"f2","d2","f1"},{"f1","d1","f0"},{"f0","d0",""}};
 if(!xb_input_snapshot_live(view,snapshot))return 13;
 refuseAlloc=true;bool noAllocation=false;try{noAllocation=xb_input_snapshot_live(view,snapshot);}catch(...){refuseAlloc=false;return 22;}refuseAlloc=false;
 if(!noAllocation)return 23;
 auto duplicate=snapshot;duplicate[1]=duplicate[0];if(xb_input_snapshot_live(view,duplicate))return 14;
 auto reparent=snapshot;reparent[0].parent="f0";if(xb_input_snapshot_live(view,reparent))return 15;
 auto missing=snapshot;missing.pop_back();if(xb_input_snapshot_live(view,missing))return 16;
 state->nodes["child"].document="changed";if(xb_input_snapshot_live(view,snapshot))return 17;state->nodes["child"].document="d2";
 between=[&](const std::string& step){if(step=="move")state->nodes["child"].document="changed";};
 auto visualBefore=run({{L"mouse","move"},{L"mouse","press",false,true},{L"mouse","release",true}},{{"f0","d0"}},snapshot);
 if(sent!=std::vector<std::string>{"move"}||visualBefore->error.empty()||visualBefore->replies!=1)return 18;
 state->nodes["child"].document="d2";
 between=[&](const std::string& step){if(step=="press")state->nodes["parent"].document="changed";};
 auto visualHeld=run({{L"mouse","press",false,true},{L"mouse","release",true},{L"mouse","press",false,true},{L"mouse","release",true}},{{"f0","d0"}},snapshot);
 if(sent!=std::vector<std::string>{"press","release"}||visualHeld->error.empty()||visualHeld->replies!=1)return 19;
 state->nodes["parent"].document="d1";
 between=[&](const std::string& step){if(step=="move")state->nodes["extra"]={"extra","root","extra","extra-doc","",false};};
 auto extra=run({{L"mouse","move"},{L"mouse","press",false,true},{L"mouse","release",true}},{{"f0","d0"}},snapshot);
 if(sent!=std::vector<std::string>{"move"}||extra->error.empty())return 20;state->nodes.erase("extra");between={};
 auto visualLive=run({{L"mouse","move"},{L"mouse","press",false,true},{L"mouse","release",true}},{{"f0","d0"}},snapshot);
 if(sent.size()!=3||!visualLive->error.empty())return 21;
 return 0;
}
'''
        directory=dev.ENV/'qa';directory.mkdir(exist_ok=True)
        source_path=directory/'native-frame-input.cpp';binary=directory/'native-frame-input.exe'
        source_path.write_text(program,encoding='utf-8',newline='\n');compiler=Path('E:/software/w64devkit/bin/g++.exe')
        subprocess.run([str(compiler),'-std=c++17','-O2','-s',str(source_path),'-o',str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')),check=True,capture_output=True)


if __name__=='__main__':unittest.main()
