"""Loopback-only two-site frame fixture; synthetic content, one HTTP worker."""
from http.server import BaseHTTPRequestHandler,HTTPServer
import threading
import re
from pathlib import Path
from urllib.parse import urlsplit


class FrameFixture:
    def __init__(self):
        fixture=self;self.counts={}
        version=re.search(r'const version = (\d+);',(Path(__file__).resolve().parents[1]/'page/runtime.js').read_text(encoding='utf-8'))
        if not version:raise RuntimeError('Cannot pin the fixture spoof to the current runtime version')
        runtime_version=version.group(1)
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def setup(self):
                super().setup()
                # Chromium may preconnect without sending a request. Bound that
                # idle socket so this single HTTP worker can serve the rest.
                self.connection.settimeout(.5)
            def handle(self):
                try:super().handle()
                except (BrokenPipeError,ConnectionResetError):pass
            def do_GET(self):
                path=urlsplit(self.path).path;fixture.counts[path]=fixture.counts.get(path,0)+1
                fake="<script>for(const marker of ['__mdo_browser_use_runtime_v1','__mdo_browser_use_local_frame_v1'])Object.defineProperty(window,marker,{value:{version:"+runtime_version+",documentNode:document,observe:()=>({forged:'forged-frame-observation'})},configurable:false})</script>"
                if path=='/root':
                    body=f'<h1>Root site A</h1><button>Root action</button><button>Shared action</button><iframe id="same" name="same" src="/same"></iframe><iframe id="cross" name="cross" src="{fixture.other}/cross"></iframe>'
                elif path=='/cross':
                    body=f'<h1>Cross site B</h1><label>Cross field<input id="cross-input" value="synthetic-private-value"></label><button>Cross action</button><button>Shared action</button><iframe id="nested" name="nested" src="{fixture.origin}/nested"></iframe>'
                elif path=='/same':body='<h1>Same site child</h1><button>Same action</button>'
                elif path=='/nested':body='<h1>Nested site A</h1><button>Nested action</button>'
                elif path=='/cross-next':body='<h1>Cross site next document</h1><button>Next action</button>'
                elif path=='/foreign':body='<h1>Foreign browser content</h1>'
                elif path=='/many':body='<h1>Bounded frame fixture</h1>'+''.join('<iframe srcdoc="<button>bounded</button>"></iframe>' for _ in range(70))
                elif path=='/many-cross':body='<h1>Bounded OOPIF fixture</h1>'+''.join(f'<iframe src="{fixture.other}/leaf?frame={index}"></iframe>' for index in range(70))
                elif path=='/leaf':body='<h1>Bounded cross-site child</h1>'
                elif path=='/geometry':
                    body=f'<style>body{{margin:0}}iframe{{display:block;margin:150px 0 0 80px;width:520px;height:340px;border:3.5px solid;padding:5.25px}}#cover{{position:fixed;inset:0;z-index:9999;background:#555}}</style><button id="root-focus">Root focus action</button><iframe id="outer" src="{fixture.other}/geometry-cross"></iframe><div id="cover" hidden></div>'
                elif path=='/geometry-cross':
                    body=f'<style>body{{margin:0}}iframe{{display:block;margin:20px;width:360px;height:200px;border:2.5px solid;padding:4.25px}}</style><iframe id="inner" src="{fixture.origin}/geometry-leaf"></iframe>'
                elif path=='/scroll-root':
                    body=f'<style>body{{margin:0}}iframe{{display:block;margin:1800px 0 1200px 50px;width:520px;height:340px;border:3.5px solid;padding:5.25px}}</style><h1>Offscreen root</h1><iframe src="{fixture.other}/scroll-cross"></iframe>'
                elif path=='/scroll-cross':
                    body=f'<style>body{{margin:0}}iframe{{display:block;margin:1400px 20px 800px;width:360px;height:200px;border:2.5px solid;padding:4.25px}}</style><h1>Offscreen parent</h1><iframe src="{fixture.origin}/scroll-leaf"></iframe>'
                elif path=='/scroll-leaf':
                    body='<style>body{margin:0}button{display:block;margin:1000px 15px 500px}</style><button id="target">Offscreen nested action</button><output id="result"></output><script>let n=0;document.querySelector("#target").onclick=e=>{document.querySelector("#result").textContent=e.isTrusted?"Trusted nested scroll count "+(++n):"Synthetic input"}</script>'
                elif path=='/geometry-leaf':
                    body='<style>body{margin:0}button{margin:20px}</style><button id="target">Nested geometry action</button><output id="result"></output><label>Nested field<input id="field"></label><label>Nested choice<select id="choice"><option>First</option><option>Second</option></select></label><input type="file" id="files" hidden aria-label="Hidden nested upload"><script>document.body.dataset.geometryClicks="0";document.querySelector("#target").onclick=e=>{document.body.dataset.geometryClicks=String(Number(document.body.dataset.geometryClicks)+1);document.querySelector("#result").textContent=e.isTrusted?"trusted":"synthetic"};document.body.dataset.uploadEvents="[]";for(const type of ["input","change"])document.querySelector("#files").addEventListener(type,async e=>{const events=JSON.parse(document.body.dataset.uploadEvents);events.push({type,trusted:e.isTrusted});document.body.dataset.uploadEvents=JSON.stringify(events);if(type!=="change")return;const file=e.target.files[0];document.body.dataset.uploadHash=file?Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",await file.arrayBuffer())),n=>n.toString(16).padStart(2,"0")).join(""):""})</script>'
                    body+='<button id="choose-file" onclick="document.querySelector(\'#files\').click()">Choose nested file</button><script>document.body.dataset.cancelEvents="[]";document.querySelector("#files").addEventListener("cancel",e=>{const events=JSON.parse(document.body.dataset.cancelEvents);events.push(e.isTrusted);document.body.dataset.cancelEvents=JSON.stringify(events)})</script>'
                    body+='<output id="upload-digest"></output><script>document.querySelector("#files").addEventListener("change",async e=>{const file=e.target.files[0];document.querySelector("#upload-digest").textContent=file?Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",await file.arrayBuffer())),n=>n.toString(16).padStart(2,"0")).join(""):""})</script>'
                else:self.send_error(404);return
                payload=('<!doctype html><meta charset="utf-8"><title>Frame fixture</title>'+body+fake).encode()
                self.send_response(200);self.send_header('Content-Type','text/html;charset=utf-8');self.send_header('Content-Length',str(len(payload)));self.send_header('Connection','close');self.end_headers();self.wfile.write(payload)
        class Server(HTTPServer):request_queue_size=64
        self.server=Server(('127.0.0.1',0),Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,kwargs={'poll_interval':.05},daemon=True)
    @property
    def origin(self):return f'http://127.0.0.1:{self.server.server_port}'
    @property
    def other(self):return f'http://localhost:{self.server.server_port}'
    def start(self):self.thread.start();return self
    def close(self):self.server.shutdown();self.server.server_close();self.thread.join(5)
