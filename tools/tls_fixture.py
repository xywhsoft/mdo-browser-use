"""Bounded loopback TLS through the existing WSL Python/OpenSSL runtime.

Only anonymous memory descriptors hold key material. Nothing is exported to disk
or a trust store. The Windows supervisor owns the helper through private stdio.
"""
import json
import os
from pathlib import Path
import queue
import shutil
import socket
import ssl
import subprocess
import sys
import threading


class TlsFixture:
    def __init__(self):
        self.child=None;self.lines=queue.Queue(maxsize=16);self.reader=None;self.port=None
    def _read(self):
        try:
            for line in self.child.stdout:
                if len(line)>4096:break
                self.lines.put(line,timeout=1)
        finally:
            try:self.lines.put(None,timeout=1)
            except queue.Full:pass
    def _reply(self):
        try:line=self.lines.get(timeout=12)
        except queue.Empty:raise RuntimeError('Loopback TLS fixture did not reply')
        if line is None:raise RuntimeError('Loopback TLS fixture exited before its reply')
        value=json.loads(line)
        if value.get('key_ephemeral') is not True:raise RuntimeError('TLS fixture did not confirm memory-only credentials')
        return value
    def start(self):
        if os.name!='nt' or not shutil.which('wsl.exe'):raise RuntimeError('The prepared WSL runtime is required for this TLS gate')
        path=Path(__file__).resolve().as_posix();path='/mnt/'+path[0].lower()+path[2:]
        self.child=subprocess.Popen(['wsl.exe','--exec','python3',path,'--serve'],
            stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding='utf-8',
            creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        self.reader=threading.Thread(target=self._read,daemon=True);self.reader.start()
        try:
            ready=self._reply();self.port=ready.get('port')
            if ready.get('ready') is not True or type(self.port) is not int or not 0<self.port<=65535:raise RuntimeError('TLS fixture startup proof was invalid')
            return self
        except Exception:self.close();raise
    @property
    def origin(self):return f'https://127.0.0.1:{self.port}'
    def stats(self):
        self.child.stdin.write('stats\n');self.child.stdin.flush();return self._reply()
    def close(self):
        if self.child is None:return
        if self.child.poll() is None:
            try:self.child.stdin.write('stop\n');self.child.stdin.flush();self.child.stdin.close()
            except (BrokenPipeError,OSError):pass
        try:self.child.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.child.kill();self.child.wait(timeout=5);raise RuntimeError('The owned TLS fixture required forced shutdown')
        finally:
            if self.reader:self.reader.join(timeout=2)
            self.child.stdout.close();self.child.stderr.close()
        if self.child.returncode!=0:raise RuntimeError('The owned TLS fixture did not shut down cleanly')


def serve():
    if not hasattr(os,'memfd_create') or not shutil.which('openssl'):raise RuntimeError('Anonymous memory/OpenSSL support is unavailable')
    # Consume the fixed-size generated PEM from a private pipe into a bounded
    # buffer. The only load_cert_chain path is a kernel anonymous memory fd.
    key=bytearray(8192);used=0;descriptor=None
    command=['openssl','req','-x509','-newkey','rsa:2048','-nodes','-sha256','-days','1','-config','/dev/null',
             '-subj','/CN=127.0.0.1','-addext','subjectAltName=IP:127.0.0.1','-addext','basicConstraints=critical,CA:FALSE',
             '-addext','keyUsage=critical,digitalSignature,keyEncipherment','-addext','extendedKeyUsage=serverAuth',
             '-keyout','/dev/stdout','-out','/dev/stdout']
    generator=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
    try:
        pem=generator.communicate(timeout=10)[0]
        if not pem or len(pem)>len(key) or generator.returncode!=0:raise RuntimeError('Memory-only TLS credential creation failed')
        used=len(pem);key[:used]=pem;del pem
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2
        descriptor=os.memfd_create('mdo-browser-use-tls',os.MFD_CLOEXEC)
        if os.write(descriptor,memoryview(key)[:used])!=used:raise RuntimeError('Anonymous TLS credential write was incomplete')
        context.load_cert_chain('/proc/self/fd/'+str(descriptor))
    finally:
        key[:]=b'\0'*len(key)
        if descriptor is not None:os.close(descriptor)
        generator.stdout.close()
        if generator.poll() is None:generator.kill();generator.wait(timeout=5)
    listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen(4);listener.settimeout(.1)
    lock=threading.Lock();slots=threading.BoundedSemaphore(4);stop=threading.Event();clients={};workers={}
    stats={'connections':0,'handshakes':0,'http_requests':0,'failures':0,'refused':0,'last_ssl_reason':None,'key_ephemeral':True}
    body=b'<title>Invalid TLS fixture must never be loaded</title>\n'
    response=b'HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: text/html\r\nContent-Length: '+str(len(body)).encode()+b'\r\n\r\n'+body
    def connection(identity,raw):
        channel=raw
        try:
            channel=context.wrap_socket(raw,server_side=True,do_handshake_on_connect=False);channel.settimeout(3)
            with lock:clients[identity]=channel
            channel.do_handshake()
            with lock:stats['handshakes']+=1
            if channel.recv(4096):
                with lock:stats['http_requests']+=1
                channel.sendall(response)
        except (OSError,ssl.SSLError) as error:
            reason=getattr(error,'reason',None)
            with lock:
                stats['failures']+=1;stats['last_ssl_reason']=reason if isinstance(reason,str) and len(reason)<=64 and all(c in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789' for c in reason) else 'OTHER_TLS_ERROR'
        finally:
            channel.close()
            with lock:clients.pop(identity,None);workers.pop(identity,None)
            slots.release()
    def accept():
        serial=0
        while not stop.is_set():
            try:client,_=listener.accept()
            except socket.timeout:continue
            except OSError:return
            with lock:stats['connections']+=1
            if not slots.acquire(blocking=False):
                with lock:stats['refused']+=1
                client.close();continue
            serial+=1;worker=threading.Thread(target=connection,args=(serial,client),daemon=True)
            with lock:clients[serial]=client;workers[serial]=worker
            worker.start()
    thread=threading.Thread(target=accept,daemon=True);thread.start()
    watchdog=threading.Timer(120,lambda:os._exit(3));watchdog.daemon=True;watchdog.start()
    try:
        print(json.dumps({'ready':True,'port':listener.getsockname()[1],'key_ephemeral':True}),flush=True)
        for command in sys.stdin:
            if command.strip()=='stop':break
            if command.strip()!='stats':raise RuntimeError('Unknown private TLS command')
            with lock:result={**stats,'active':len(clients)}
            print(json.dumps(result),flush=True)
    finally:
        watchdog.cancel();stop.set();listener.close();thread.join(timeout=2)
        with lock:remaining=list(clients.values());running=list(workers.values())
        for client in remaining:
            try:client.shutdown(socket.SHUT_RDWR)
            except OSError:pass
            client.close()
        for worker in running:worker.join(timeout=4)
        if thread.is_alive() or any(worker.is_alive() for worker in running):raise RuntimeError('TLS workers did not settle')


if __name__=='__main__':
    try:
        if sys.argv[1:]!=['--serve']:raise RuntimeError('Private TLS helper mode is required')
        serve()
    except Exception:
        # Never print generation/SSL exceptions containing credential material.
        print('Memory-only loopback TLS fixture failed',file=sys.stderr);raise SystemExit(1)
