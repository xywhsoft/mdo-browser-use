"""Compile actual frame identity rules: native loader proof, never URL inference."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class NativeFrameIdentityTests(unittest.TestCase):
    def test_non_document_response_and_real_document_replacement(self):
        source=(dev.ROOT/'host/native-frames.inc.hpp').read_text(encoding='utf-8')
        state=(dev.ROOT/'host/native-frames-state.inc.hpp').read_text(encoding='utf-8')
        node='struct xb_frame_node'+state.split('struct xb_frame_node',1)[1].split('struct xb_frame_session',1)[0]
        upsert='static xb_frame_node* xb_frame_upsert'+source.split('static xb_frame_node* xb_frame_upsert',1)[1].split('static bool xb_frame_attached',1)[0]
        navigation='static void xb_frames_navigation'+source.split('static void xb_frames_navigation',1)[1].split('static void xb_frames_close',1)[0]
        program=r'''
#include <map>
#include <vector>
#include <memory>
#include <string>
#include <utility>
'''+node+r'''
struct xb_frame_job{int finished=0;void finish(const std::string&,const char*){finished++;}};
struct xb_frame_state{unsigned generation=0;std::string root,error;std::map<std::string,xb_frame_node> nodes,previousNodes;
 std::map<std::string,bool> loading;std::map<std::string,std::shared_ptr<xb_frame_job>> jobs;};
struct xb_view{std::shared_ptr<xb_frame_state> frames=std::make_shared<xb_frame_state>();};
static int serial=0;
static std::string xb_frame_id(const char* prefix){return std::string(prefix)+std::to_string(++serial);}
static std::string xb_bounded_text(const std::string& value,unsigned limit){return value.substr(0,limit);}
'''+upsert+navigation+r'''
int main(){
 auto view=std::make_shared<xb_view>();auto state=view->frames;state->root="root";
 auto old=*xb_frame_upsert(state,"root","","loader-one","https://same.test/","","");
 auto job=std::make_shared<xb_frame_job>();state->jobs["pending"]=job;state->loading["root"]=true;
 state->error="frame_limit";xb_frames_navigation(view);
 if(job->finished!=1||state->generation!=1||!state->nodes.empty()||!state->loading.empty()||!state->root.empty()||!state->error.empty())return 1;
 state->root="root";
 auto unchanged=xb_frame_upsert(state,"root","","loader-one","https://changed-url.test/","","");
 if(unchanged->document!=old.document||unchanged->id!=old.id||!state->previousNodes.empty())return 2;
 xb_frames_navigation(view);state->root="root";
 auto changed=*xb_frame_upsert(state,"root","","loader-two","https://same.test/","","");
 if(changed.document==old.document)return 3;
 xb_frames_navigation(view);state->root="root";
 auto unproven=*xb_frame_upsert(state,"root","","","https://same.test/","","");
 if(unproven.document==changed.document)return 4;
 auto child=*xb_frame_upsert(state,"child","root","child-loader","https://same.test/","","child-session");
 xb_frames_navigation(view);state->root="root";
 auto reparented=*xb_frame_upsert(state,"child","other-parent","child-loader","https://same.test/","","child-session");
 if(reparented.document==child.document||reparented.id==child.id)return 5;
 xb_frames_navigation(view);xb_frame_detach(state,"child");
 if(state->previousNodes.count("child"))return 6;
 auto detached=*xb_frame_upsert(state,"child","other-parent","child-loader","https://same.test/","","child-session");
 if(detached.document==reparented.document)return 7;
 for(int i=0;i<100;i++){xb_frames_navigation(view);state->root="root";xb_frame_upsert(state,"root","","loader-"+std::to_string(i),"same","","");if(state->previousNodes.size()>1)return 8;}
 return 0;
}
'''
        directory=dev.ENV/'qa';directory.mkdir(exist_ok=True)
        path=directory/'native-frame-identity.cpp';binary=directory/'native-frame-identity.exe'
        path.write_text(program,encoding='utf-8',newline='\n');compiler=Path('E:/software/w64devkit/bin/g++.exe')
        subprocess.run([str(compiler),'-std=c++17','-O2','-s',str(path),'-o',str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')),check=True,capture_output=True)


if __name__=='__main__':unittest.main()
