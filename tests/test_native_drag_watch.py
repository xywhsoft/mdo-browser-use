"""Compile the actual drag observer ownership/retained-context cleanup methods."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class NativeDragWatchTests(unittest.TestCase):
    def test_retained_cleanup_never_creates_a_world_or_sends_input(self):
        source=(dev.ROOT/'host/native-drag.inc.hpp').read_text(encoding='utf-8')
        methods=source[source.index('  bool sourceLive()const'):source.index('  void receive(')]
        program=r'''
#include <string>
#include <vector>
#include <map>
#include <memory>
#include <functional>
#include <utility>
using UINT64=unsigned long long;
struct node{std::string raw="root",id="f0",document="fd-original",loader="original-loader",session;};
struct frames{std::map<std::string,node> nodes;};
struct xb_view{bool closed=false,core=true,ready=true;UINT64 navigation=1;std::shared_ptr<frames> framesState=std::make_shared<::frames>();std::shared_ptr<::frames> frames=framesState;};
struct xb_host{bool alive=true;};
using xb_input_frame_guard=std::vector<std::pair<std::string,std::string>>;
static bool xb_input_frames_live(const std::shared_ptr<xb_view>& view,const xb_input_frame_guard& guard){
 if(!view->ready)return false;for(const auto& pin:guard){bool found=false;for(const auto& entry:view->frames->nodes)found|=entry.second.id==pin.first&&entry.second.document==pin.second;if(!found)return false;}return true;
}
static node* xb_frame_alias(const std::shared_ptr<frames>& state,const std::string& id){for(auto& entry:state->nodes)if(entry.second.id==id)return &entry.second;return nullptr;}
static unsigned long long GetTickCount64(){return 100;}
static std::string xb_quote(const std::string& value){return "\""+value+"\"";}
static std::string xb_get(const std::string& value,const std::string& key){
 if(key=="executionContextId")return value=="context"?"77":"";
 if(key=="result")return value;
 if(key=="type")return value.rfind("number:",0)==0?"number":"object";
 if(key=="value")return value.rfind("number:",0)==0?value.substr(7):"";
 return "";
}
struct command{std::wstring method;std::string params,session;};
static std::vector<command> commands;static bool fail=false,delay=false;static std::string response="number:1";
static std::function<void(bool,const std::string&)> pending;
static void xb_frame_cdp(const std::shared_ptr<xb_view>&,const std::string& session,const wchar_t* method,const std::string& params,std::function<void(bool,const std::string&)> done){
 commands.push_back({method,params,session});if(std::wstring(method)==L"Page.createIsolatedWorld"){done(true,"context");return;}
 if(std::wstring(method)!=L"Runtime.evaluate")throw 99;
 if(delay){pending=done;return;}done(!fail,fail?"failure":response);
}
struct xb_drag_job:std::enable_shared_from_this<xb_drag_job>{
 std::shared_ptr<xb_host> host=std::make_shared<xb_host>();std::shared_ptr<xb_view> view=std::make_shared<xb_view>();
 xb_input_frame_guard sourceGuard={{"f0","fd-original"}};std::string sourceRaw="root",sourceLoader="original-loader",sourceSession,key="owned-job",error;
 UINT64 navigation=1,nextAt=0;int watchContext=0;bool done=false,inFlight=false,watchStarted=false,watchInstalled=false,watchChecked=false,watchActive=false,watchCleared=false,cleanupFailed=false,intercepted=false;
 xb_drag_job(){view->frames->nodes["root"]={};}
 void stop(const std::string& value){if(error.empty())error=value;}
'''+methods+r'''
};
static std::shared_ptr<xb_drag_job> fixture(){commands.clear();fail=false;delay=false;pending={};response="number:1";auto job=std::make_shared<xb_drag_job>();job->watchContext=77;job->sourceSession="captured-source-session";return job;}
static bool onlyRetained(){return commands.size()==1&&commands[0].method==L"Runtime.evaluate"&&commands[0].session=="captured-source-session"&&commands[0].params.find("\"contextId\":77")!=std::string::npos;}
int main(){
 auto retained=fixture();retained->view->navigation=2;retained->view->ready=false;retained->view->frames->nodes.clear();retained->clearWatch();
 if(!onlyRetained()||retained->cleanupFailed||!retained->watchCleared)return 1;
 auto absent=fixture();absent->view->navigation=2;response="number:0";absent->clearWatch();if(!onlyRetained()||absent->cleanupFailed)return 2;
 auto replaced=fixture();replaced->view->navigation=2;replaced->view->frames->nodes["root"].loader="replacement-loader";fail=true;replaced->clearWatch();if(!onlyRetained()||replaced->cleanupFailed)return 3;
 auto rotated=fixture();rotated->view->navigation=2;rotated->view->frames->nodes["root"].document="rotated-alias";fail=true;rotated->clearWatch();if(!rotated->cleanupFailed||!onlyRetained())return 4;
 auto unknown=fixture();unknown->view->navigation=2;unknown->view->frames->nodes.clear();fail=true;unknown->clearWatch();if(!unknown->cleanupFailed||!onlyRetained())return 5;
 auto uncreated=fixture();uncreated->watchContext=0;uncreated->clearWatch();if(!commands.empty()||uncreated->cleanupFailed)return 6;
 auto active=fixture();active->intercepted=true;response="number:2";active->clearWatch();if(!active->cleanupFailed||!onlyRetained())return 7;
 auto late=fixture();delay=true;late->clearWatch();late->view->navigation=2;late->view->frames->nodes.clear();if(!pending||!late->inFlight)return 8;pending(true,"number:1");pending={};if(late->inFlight||late->cleanupFailed||!onlyRetained())return 9;
 auto lost=fixture();lost->watchContext=0;fail=true;lost->armWatch();if(lost->error.empty()||lost->cleanupFailed||commands.size()!=2)return 10;
 fail=false;lost->clearWatch();if(lost->cleanupFailed||commands.size()!=3||commands.back().method!=L"Runtime.evaluate")return 11;
 auto malformed=fixture();response="wrong-type";malformed->clearWatch();if(!malformed->cleanupFailed||!onlyRetained())return 12;
 auto closed=fixture();closed->view->closed=true;closed->clearWatch();if(!commands.empty()||closed->cleanupFailed)return 13;
 return 0;
}
'''
        directory=dev.ENV/'qa';directory.mkdir(exist_ok=True);cpp=directory/'native-drag-watch.cpp';binary=directory/'native-drag-watch.exe'
        cpp.write_text(program,encoding='utf-8',newline='\n');compiler=Path('E:/software/w64devkit/bin/g++.exe')
        compiled=subprocess.run([str(compiler),'-std=c++17','-O2','-s',str(cpp),'-o',str(binary)],capture_output=True,text=True,timeout=60)
        self.assertEqual(compiled.returncode,0,compiled.stderr[-3000:])
        completed=subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')),capture_output=True,text=True,timeout=15)
        self.assertEqual(completed.returncode,0,'Actual source cleanup branch failed: '+str(completed.returncode))


if __name__=='__main__':unittest.main()
