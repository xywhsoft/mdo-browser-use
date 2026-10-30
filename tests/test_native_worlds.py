"""Compile the actual root-world admission, metadata and caller dispatch code."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


@unittest.skipUnless(os.name=='nt','Pinned Windows native compiler/SDK required')
class NativeWorldTests(unittest.TestCase):
    def test_numeric_reuse_never_rebinds_original_execution(self):
        web=(dev.ENV/'native-source/lib/webview/webview/webview.h').read_text(encoding='utf-8')
        start=web.index('inline int json_parse_c(')
        parser=web[start:web.index('// Holds a symbol name',start)]
        state=(dev.ROOT/'host/native-world-state.inc.hpp').read_text(encoding='utf-8')
        state=state[:state.index('struct xb_world_receiver{')]+'struct xb_world_receiver{};\n'+state[state.index('struct xb_world_request;'):]
        source=(dev.ROOT/'host/native-worlds.inc.hpp').read_text(encoding='utf-8')
        registry=source[source.index('// BROWSER_WORLD_REGISTRY_BEGIN'):source.index('static bool xb_worlds_install(')]
        execution=source[source.index('struct xb_world_request:'):]
        automation=(dev.ROOT/'host/native-automation.inc.hpp').read_text(encoding='utf-8')
        value=automation[automation.index('static std::string xb_cdp_value('):automation.index('static bool xb_popup_blocked(')]
        dispatch=automation[automation.index('static void xb_automation_reply('):automation.index('// BROWSER_FRAME_INPUT_GUARD_BEGIN')]
        fixture=(dev.ROOT/'tests/fixtures/native-worlds-checks.cpp').read_text(encoding='utf-8')
        fixture=fixture.replace('// INSERT_PARSER',parser).replace('// INSERT_STATE',state).replace('// INSERT_REGISTRY',registry)
        fixture=fixture.replace('// INSERT_EXECUTION',execution).replace('// INSERT_AUTOMATION',value+dispatch)
        directory=dev.ENV/'qa/native-worlds';directory.mkdir(parents=True,exist_ok=True)
        cpp=directory/'worlds.cpp';binary=directory/'worlds.exe';cpp.write_text(fixture,encoding='utf-8',newline='\n')
        compiler=Path('E:/software/w64devkit/bin/g++.exe')
        build=subprocess.run([str(compiler),'-std=c++17','-O1','-Wall','-Wextra','-Werror','-Wno-misleading-indentation',
            '-Wno-parentheses',str(cpp),'-lole32','-o',str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=60)
        self.assertEqual(build.returncode,0,build.stderr[-3500:])
        result=subprocess.run([str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=15,
            env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))
        self.assertEqual(result.returncode,0,result.stdout[-1000:]+result.stderr[-2500:]);print(result.stdout)


if __name__=='__main__':unittest.main()
