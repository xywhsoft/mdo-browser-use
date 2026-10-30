"""Compile actual routed COM dispatch and preserve bounded protocol failures."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


@unittest.skipUnless(os.name=='nt','Pinned Windows native compiler required')
class NativeFrameCdpTests(unittest.TestCase):
    def test_routed_failures_keep_the_original_bounded_protocol_receipt(self):
        native=(dev.ENV/'native-source/src/webview/xs_browser.inc.hpp').read_text(encoding='utf-8')
        callback=native[native.index('template<class I,typename... Args> class xb_callback'):native.index('struct xb_input_sequence;')]
        support=(dev.ENV/'native-source/src/webview/xs_directory_drop.inc.hpp').read_text(encoding='utf-8')
        owner=support[support.index('template <typename T> struct xs_wv_com'):support.index('struct xs_wv_text')]
        source=(dev.ROOT/'host/native-frames.inc.hpp').read_text(encoding='utf-8')
        dispatch=source[source.index('struct xb_frame_cdp_completion{'):source.index('static bool xb_frame_source(')]
        parser=(dev.ENV/'native-source/lib/webview/webview/webview.h').read_text(encoding='utf-8')
        parser=parser[parser.index('inline int json_parse_c('):parser.index('// Holds a symbol name')]
        fixture=(dev.ROOT/'tests/fixtures/native-frame-cdp-checks.cpp').read_text(encoding='utf-8')
        fixture=fixture.replace('// INSERT_PARSER',parser).replace('// INSERT_CALLBACK',callback).replace('// INSERT_DISPATCH',dispatch)
        fixture=fixture.replace('// INSERT_OWNER',owner)
        directory=dev.ENV/'qa/native-frame-cdp';directory.mkdir(parents=True,exist_ok=True)
        cpp=directory/'dispatch.cpp';binary=directory/'dispatch.exe';cpp.write_text(fixture,encoding='utf-8',newline='\n')
        compiler=Path('E:/software/w64devkit/bin/g++.exe')
        built=subprocess.run([str(compiler),'-std=c++17','-O1','-Wall','-Wextra','-Werror','-Wno-misleading-indentation',
            str(cpp),'-o',str(binary)],capture_output=True,text=True,timeout=60,cwd=dev.ROOT)
        self.assertEqual(built.returncode,0,built.stderr[-2500:])
        result=subprocess.run([str(binary)],capture_output=True,text=True,timeout=15,cwd=dev.ROOT,
            env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))
        self.assertEqual(result.returncode,0,result.stdout[-600:]+result.stderr[-1400:]);print(result.stdout)


if __name__=='__main__':unittest.main()
