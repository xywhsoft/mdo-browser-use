"""Bounded loopback navigation/POST faults; one sequential server, no app copy."""
from http.server import BaseHTTPRequestHandler,HTTPServer
from urllib.parse import urlsplit
import os
import socket
import struct
import threading


class NetworkFixture:
    def __init__(self):
        self.counts={};self.valid_posts=0
        self.seen={name:threading.Event() for name in ('get','post')}
        self.release={name:threading.Event() for name in ('get','post')}
        self.finished={name:threading.Event() for name in ('get','post')}
        fixture=self
        class Handler(BaseHTTPRequestHandler):
            protocol_version='HTTP/1.1'
            def setup(self):
                super().setup();self.connection.settimeout(2)
            def log_message(self,*args):pass
            def handle(self):
                try:super().handle()
                except (ConnectionResetError,BrokenPipeError,TimeoutError,OSError):pass
            def do_GET(self):self.reply()
            def do_POST(self):self.reply()
            def reply(self):
                path=urlsplit(self.path).path
                fixture.counts[path]=fixture.counts.get(path,0)+1
                if path=='/reset':
                    self.close_connection=True
                    self.connection.setsockopt(socket.SOL_SOCKET,socket.SO_LINGER,struct.pack('HH' if os.name=='nt' else 'ii',1,0))
                    socket.close(self.connection.detach());return
                if path not in ('/ok','/get-stall','/post-stall'):
                    self.send_error(404);return
                mode='post' if path=='/post-stall' else 'get' if path=='/get-stall' else None
                if mode=='post':
                    size=int(self.headers.get('Content-Length','0'))
                    if self.command!='POST' or not 0<size<=128 or self.rfile.read(size)!=b'order=fixture':
                        self.send_error(400);return
                    fixture.valid_posts+=1
                if mode:
                    fixture.seen[mode].set();fixture.release[mode].wait(8)
                title='Network fixture' if mode is None else 'Released '+mode+' fixture'
                body=('<!doctype html><meta charset="utf-8"><title>'+title+'</title><h1>'+title+'</h1>'+
                    '<form method="POST" action="/post-stall"><input name="order" value="fixture" type="hidden">'+
                    '<button id="submit-once">Submit delayed once</button></form>').encode()
                try:
                    self.send_response(200);self.send_header('Content-Type','text/html;charset=utf-8');self.send_header('Content-Length',str(len(body)))
                    self.send_header('Cache-Control','no-store');self.send_header('Connection','close');self.end_headers();self.wfile.write(body)
                except (BrokenPipeError,ConnectionResetError,OSError):pass
                finally:
                    self.close_connection=True
                    if mode:fixture.finished[mode].set()
        self.server=HTTPServer(('127.0.0.1',0),Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,kwargs={'poll_interval':.05},daemon=True)
    @property
    def origin(self):return f'http://127.0.0.1:{self.server.server_port}'
    def start(self):self.thread.start();return self
    def close(self):
        for event in self.release.values():event.set()
        self.server.shutdown();self.server.server_close();self.thread.join(3)
