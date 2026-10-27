"""One bounded local HTTP fixture; no browser/deployment/Home copies."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import threading
import time
import os
import socket
import struct
from urllib.parse import urlsplit

BODY=bytes(index%251 for index in range(131075))

class DownloadFixture:
    def __init__(self):
        self.counts={}
        fixture=self
        class Handler(BaseHTTPRequestHandler):
            protocol_version='HTTP/1.1'
            def log_message(self,*args): pass
            def handle(self):
                try: super().handle()
                except (ConnectionResetError,BrokenPipeError): pass
            def do_GET(self): self.reply()
            def do_POST(self): self.reply()
            def reply(self):
                path=urlsplit(self.path).path
                fixture.counts[path]=fixture.counts.get(path,0)+1
                if path=='/broken-navigation':
                    self.close_connection=True
                    self.connection.setsockopt(socket.SOL_SOCKET,socket.SO_LINGER,struct.pack('HH' if os.name=='nt' else 'ii',1,0))
                    socket.close(self.connection.detach());return
                if path=='/page':
                    body=(Path(__file__).resolve().parents[1]/'tests/fixtures/download.html').read_bytes()
                    self.send_response(200);self.send_header('Content-Type','text/html;charset=utf-8');self.send_header('Set-Cookie','browser_download_fixture=1; SameSite=Lax; Path=/')
                    self.send_header('Content-Length',str(len(body)));self.send_header('Connection','close');self.end_headers();self.wfile.write(body);return
                if path=='/post':
                    size=int(self.headers.get('Content-Length','0'))
                    if self.command!='POST' or size>4096 or self.rfile.read(size)!=b'order=fixture' or 'browser_download_fixture=1' not in self.headers.get('Cookie',''):
                        self.send_error(403);return
                if path not in ('/binary','/empty','/unknown','/large','/slow','/interrupt','/truncated','/post'):
                    self.send_error(404);return
                self.send_response(200);self.send_header('Content-Type','application/octet-stream');self.send_header('Content-Disposition','attachment; filename="fixture-'+path[1:]+'.bin"')
                self.send_header('Cache-Control','no-store');self.send_header('Connection','close')
                size=0 if path=='/empty' else 3*1024*1024 if path=='/slow' else 65*1024*1024 if path=='/large' else 524288 if path in ('/interrupt','/truncated') else len(BODY)
                if path!='/unknown':self.send_header('Content-Length',str(size))
                self.end_headers()
                try:
                    if path=='/slow':
                        for _ in range(48):self.wfile.write(BODY[:65536]);self.wfile.flush();time.sleep(.05)
                    elif path=='/unknown':
                        for offset in range(0,len(BODY),32768):self.wfile.write(BODY[offset:offset+32768]);self.wfile.flush();time.sleep(.05)
                    elif path in ('/interrupt','/truncated'):
                        self.wfile.write(BODY[:65536]);self.wfile.flush()
                        if path=='/interrupt':
                            # A real TCP reset is distinct from an EOF/content-
                            # length mismatch, which Chromium may auto-resume.
                            self.connection.setsockopt(socket.SOL_SOCKET,socket.SO_LINGER,struct.pack('HH' if os.name=='nt' else 'ii',1,0))
                            socket.close(self.connection.detach())
                    elif path!='/empty':self.wfile.write(BODY)
                except (BrokenPipeError,ConnectionResetError,OSError): pass
                self.close_connection=True
        self.server=HTTPServer(('127.0.0.1',0),Handler)
        self.server.timeout=1
        self.thread=threading.Thread(target=self.server.serve_forever,kwargs={'poll_interval':.05},daemon=True)
    @property
    def origin(self): return f'http://127.0.0.1:{self.server.server_port}'
    def start(self): self.thread.start();return self
    def close(self): self.server.shutdown();self.server.server_close();self.thread.join(5)
