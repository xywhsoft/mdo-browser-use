"""Compile the native focus check and exercise its actual CDP admission paths."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class NativeFocusTests(unittest.TestCase):
    def test_original_world_focus_and_late_document_fences(self):
        source=(dev.ROOT/'host/native-automation.inc.hpp').read_text(encoding='utf-8')
        helper=source[source.index('static void xb_input_focus('):source.index('static bool xb_input_read_frames(')]
        program=r'''
#include <map>
#include <memory>
#include <string>
#include <vector>
#include <functional>
#include <utility>
using UINT64=unsigned long long;
struct xb_frame_node{std::string id,raw,session;};
struct xb_frame_state{std::map<std::string,xb_frame_node> nodes;};
struct xb_view{bool live=true,framesLive=true,snapshotLive=true;std::shared_ptr<xb_frame_state> frames=std::make_shared<xb_frame_state>();};
struct xb_host{};
using xb_input_frame_guard=std::vector<std::pair<std::string,std::string>>;
using xb_input_frame_snapshot=std::vector<std::string>;
static bool xb_automation_live(const std::shared_ptr<xb_host>&,const std::shared_ptr<xb_view>& view,UINT64){return view->live;}
static bool xb_input_frames_live(const std::shared_ptr<xb_view>& view,const xb_input_frame_guard&){return view->framesLive;}
static bool xb_input_snapshot_live(const std::shared_ptr<xb_view>& view,const xb_input_frame_snapshot& snapshot){return snapshot.empty()||view->snapshotLive;}
static std::string xb_quote(const std::string& text){return "\""+text+"\"";}
static std::string xb_get(const std::string& text,const std::string& key){
 if(key=="executionContextId")return text=="context"?"77":"";
 if(key=="result")return text;
 if(key=="type")return text=="number1"||text=="number0"?"number":"object";
 if(key=="value")return text=="number1"?"1":"0";
 if(key=="exceptionDetails")return text=="exception"?"exception":"";
 return "";
}
struct command{std::wstring method;std::string params,session;};
static std::vector<command> commands;
static bool fail=false,failWorld=false,delayWorld=false,delayValue=false;
static std::string response="number1";
static std::function<void(bool,const std::string&)> pending;
static void xb_frame_cdp(const std::shared_ptr<xb_view>&,const std::string& session,const wchar_t* method,const std::string& params,std::function<void(bool,const std::string&)> done){
 commands.push_back({method,params,session});const bool world=std::wstring(method)==L"Page.createIsolatedWorld";
 if(!world&&std::wstring(method)!=L"Runtime.evaluate")throw 99;
 if(world&&delayWorld||!world&&delayValue){pending=done;return;}
 done(!(world?failWorld:fail),world?"context":response);
}
'''+helper+r'''
static std::shared_ptr<xb_view> fixture(){commands.clear();fail=failWorld=delayWorld=delayValue=false;response="number1";pending={};auto view=std::make_shared<xb_view>();view->frames->nodes["child"]={"child-alias","raw-child","original-child-session"};return view;}
int main(){
 auto host=std::make_shared<xb_host>();xb_input_frame_guard guard={{"child-alias","original-native-doc"},{"f0","root-doc"}};xb_input_frame_snapshot snapshot={"child","root"};
 int completions=0;bool accepted=false;auto done=[&](bool value){accepted=value;completions++;};
 auto view=fixture();xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done);
 if(!accepted||completions!=1||commands.size()!=2)return 1;
 for(const auto& command:commands)if(command.session!="original-child-session")return 2;
 if(commands[0].params.find("raw-child")==std::string::npos||commands[1].params.find("contextId\":77")==std::string::npos||commands[1].params.find("document.hasFocus()") ==std::string::npos||commands[1].params.find("e7")==std::string::npos)return 3;
 for(const auto& value:std::vector<std::string>{"number0","wrong-type","exception"}){view=fixture();response=value;xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done);if(accepted)return 4;}
 view=fixture();fail=true;xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done);if(accepted)return 5;
 view=fixture();failWorld=true;xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done);if(accepted||commands.size()!=1)return 6;
 for(int mode=0;mode<3;mode++){view=fixture();if(mode==0)view->live=false;if(mode==1)view->framesLive=false;if(mode==2)view->snapshotLive=false;xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done);if(accepted||!commands.empty())return 7;}
 view=fixture();view->frames->nodes.clear();xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done);if(accepted||!commands.empty())return 8;
 view=fixture();delayWorld=true;xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done);view->framesLive=false;pending(true,"context");pending={};if(accepted||commands.size()!=1)return 9;
 view=fixture();delayValue=true;xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done);view->snapshotLive=false;pending(true,"number1");pending={};if(accepted||commands.size()!=2)return 10;
 view=fixture();xb_input_focus(host,view,1,{},snapshot,"d-original","e7",done);if(accepted||!commands.empty())return 11;
 view=fixture();xb_input_focus(host,view,1,guard,snapshot,"d-original","e7",done,true);
 if(!accepted||commands.size()!=2||commands[1].params.find("r.focus(")==std::string::npos)return 12;
 view=fixture();view->snapshotLive=false;xb_input_focus(host,view,1,guard,{},"d-original","e7",done);
 if(!accepted||commands.size()!=2)return 13; // DOM targets do not pin unrelated siblings.
 return 0;
}
'''
        directory=dev.ENV/'qa';directory.mkdir(exist_ok=True);cpp=directory/'native-focus.cpp';binary=directory/'native-focus.exe'
        cpp.write_text(program,encoding='utf-8',newline='\n');compiler=Path('E:/software/w64devkit/bin/g++.exe')
        compiled=subprocess.run([str(compiler),'-std=c++17','-O2','-s',str(cpp),'-o',str(binary)],capture_output=True,text=True,timeout=60)
        self.assertEqual(compiled.returncode,0,compiled.stderr[-3000:])
        completed=subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')),capture_output=True,text=True,timeout=15)
        self.assertEqual(completed.returncode,0,'Native focus branch failed: '+str(completed.returncode))


if __name__=='__main__':unittest.main()
