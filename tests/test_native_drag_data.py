"""Exercise the actual C++ URI predicates, including data Chromium strips first."""
import os
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import dev


class NativeDragDataTests(unittest.TestCase):
    def test_compiled_uri_policy(self):
        source=(dev.ROOT/"host/native-drag.inc.hpp").read_text(encoding="utf-8")
        start=source.index("static bool xb_drag_url("),source.index("struct xb_drag_job:std::")
        functions=source[start[0]:start[1]]
        program='#include <string>\n'+functions+r'''
int main(){
  if(!xb_drag_url("https://example.com/a")||!xb_drag_url("http://localhost:1234/")||!xb_drag_url("about:blank"))return 1;
  for(const auto* value:{"file:///C:/private","javascript:alert(1)","data:text/plain,x","https://u:p@example.com/","http://","https://x/\nheader","https://x\\path"})if(xb_drag_url(value))return 2;
  if(!xb_drag_uri_list("# a comment\r\nhttps://example.com/a\r\nhttp://example.org/\n"))return 3;
  if(xb_drag_uri_list("https://example.com/\nfile:///C:/private")||xb_drag_uri_list("https://u:p@example.com/"))return 4;
  std::string many;for(int i=0;i<33;i++)many+="https://example.com/\n";if(xb_drag_uri_list(many))return 5;
  return 0;
}
'''
        directory=dev.ENV/"qa";directory.mkdir(exist_ok=True)
        cpp=directory/"native-drag-policy.cpp";binary=directory/"native-drag-policy.exe"
        cpp.write_text(program,encoding="utf-8",newline="\n")
        compiler=Path("E:/software/w64devkit/bin/g++.exe")
        subprocess.run([str(compiler),"-std=c++17","-O2","-s",str(cpp),"-o",str(binary)],check=True,capture_output=True)
        subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get("PATH","")),check=True,capture_output=True)


if __name__=="__main__":unittest.main()
