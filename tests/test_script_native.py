"""Compile the actual private page-context registry, without another SDK/Home."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT=Path(__file__).resolve().parents[1]

@unittest.skipUnless(os.name=='nt','Windows native compiler required')
class ScriptContextTests(unittest.TestCase):
    def test_original_default_contexts_cannot_rebind_across_frames_or_sessions(self):
        web=(ROOT/'.mdo/native-source/lib/webview/webview/webview.h').read_text(encoding='utf-8')
        parser=web[web.index('inline int json_parse_c('):web.index('// Holds a symbol name')]
        native=(ROOT/'host/native-frames.inc.hpp').read_text()
        registry=native[native.index('static void xb_script_metadata('):native.index('static void xb_frame_event(')]
        world=(ROOT/'host/native-worlds.inc.hpp').read_text()
        number=world[world.index('static int xb_world_number('):world.index('static bool xb_world_live(')]
        state=(ROOT/'host/native-frames-state.inc.hpp').read_text()
        context=state[state.index('struct xb_script_budget{'):state.index('struct xb_frame_session{')]
        fixture=(ROOT/'tests/fixtures/script-contexts.cpp').read_text().replace('// PARSER',parser).replace('// CONTEXT',context).replace('// REGISTRY',number+registry)
        directory=ROOT/'.mdo/qa/script-contexts';directory.mkdir(parents=True,exist_ok=True)
        cpp=directory/'contexts.cpp';binary=directory/'contexts.exe';cpp.write_text(fixture,encoding='utf-8')
        compiler=Path('E:/software/w64devkit/bin/g++.exe')
        result=subprocess.run([str(compiler),'-std=c++17','-O1','-Wall','-Wextra','-Werror','-Wno-misleading-indentation','-Wno-parentheses',str(cpp),'-o',str(binary)],capture_output=True,text=True,timeout=60)
        self.assertEqual(result.returncode,0,result.stderr[-3000:])
        result=subprocess.run([str(binary)],capture_output=True,text=True,timeout=10,env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))
        self.assertEqual(result.returncode,0,result.stdout+result.stderr);print(result.stdout)

if __name__=='__main__':unittest.main()
