"""Loopback-only two-site frame fixture; synthetic content, one HTTP worker."""
from http.server import BaseHTTPRequestHandler,HTTPServer
import threading
from urllib.parse import urlsplit


class FrameFixture:
    def __init__(self):
        fixture=self;self.counts={}
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
                fake="<script>Object.defineProperty(window,'__mdo_browser_use_runtime_v1',{value:{version:8,documentNode:document,observe:()=>({forged:'forged-frame-observation'})}})</script>"
                if path=='/root':
                    body=f'<h1>Root site A</h1><button>Root action</button><iframe id="same" name="same" src="/same"></iframe><iframe id="cross" name="cross" src="{fixture.other}/cross"></iframe>'
                elif path=='/cross':
                    body=f'<h1>Cross site B</h1><label>Cross field<input id="cross-input"></label><button>Cross action</button><iframe id="nested" name="nested" src="{fixture.origin}/nested"></iframe>'
                elif path=='/same':body='<h1>Same site child</h1><button>Same action</button>'
                elif path=='/nested':body='<h1>Nested site A</h1><button>Nested action</button>'
                elif path=='/cross-next':body='<h1>Cross site next document</h1><button>Next action</button>'
                elif path=='/foreign':body='<h1>Foreign browser content</h1>'
                elif path=='/many':body='<h1>Bounded frame fixture</h1>'+''.join('<iframe srcdoc="<button>bounded</button>"></iframe>' for _ in range(70))
                elif path=='/many-cross':body='<h1>Bounded OOPIF fixture</h1>'+''.join(f'<iframe src="{fixture.other}/leaf?frame={index}"></iframe>' for index in range(70))
                elif path=='/leaf':body='<h1>Bounded cross-site child</h1>'
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
