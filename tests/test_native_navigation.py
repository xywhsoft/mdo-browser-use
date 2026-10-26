"""Compile the actual native event gate, including out-of-order blank events."""
import os
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import dev


class NativeNavigationTests(unittest.TestCase):
    def test_initial_navigation_event_order(self):
        source=(dev.ROOT/"host/native-navigation.inc.hpp").read_text(encoding="utf-8")
        program=r'''
#include <memory>
#include <string>
#include <vector>
using UINT64=unsigned long long;
struct xb_view{UINT64 navigation=0;bool initialNavigationPending=true,initialNavigationStarted=false;std::vector<std::string> navigationEvents;};
static std::string xb_quote(const std::string& value){return "\""+value+"\"";}
'''+source+r'''
int main(){
  auto view=std::make_shared<xb_view>();
  if(xb_navigation_complete(view,0)||!view->initialNavigationPending)return 1;
  if(xb_navigation_start(view,1,true)||xb_navigation_complete(view,1))return 2;
  if(!xb_navigation_start(view,2,false)||!view->initialNavigationPending)return 3;
  if(xb_navigation_start(view,3,true)||xb_navigation_complete(view,3)||view->navigation!=2)return 4;
  if(xb_navigation_complete(view,1)||!view->initialNavigationPending)return 5;
  if(!xb_navigation_complete(view,2)||view->initialNavigationPending)return 6;
  if(!xb_navigation_start(view,4,false)||!xb_navigation_start(view,5,false)||xb_navigation_complete(view,4)||!xb_navigation_complete(view,5))return 7;
  for(int i=0;i<40;i++){xb_navigation_start(view,10+i,false);xb_navigation_complete(view,10+i);}
  if(view->navigationEvents.size()!=12||xb_navigation_events(view).find("http")!=std::string::npos)return 8;
  auto blank=std::make_shared<xb_view>();blank->initialNavigationPending=false;
  if(!xb_navigation_start(blank,1,true)||!xb_navigation_complete(blank,1))return 9;
  return 0;
}
'''
        directory=dev.ENV/"qa";directory.mkdir(exist_ok=True)
        cpp=directory/"native-navigation.cpp";binary=directory/"native-navigation.exe"
        cpp.write_text(program,encoding="utf-8",newline="\n")
        compiler=Path("E:/software/w64devkit/bin/g++.exe")
        subprocess.run([str(compiler),"-std=c++17","-O2","-s",str(cpp),"-o",str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get("PATH","")),check=True,capture_output=True)


if __name__=="__main__":unittest.main()
