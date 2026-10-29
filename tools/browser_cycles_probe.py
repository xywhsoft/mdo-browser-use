"""Bounded public C -> workbench -> WebView2 lifecycle/latency measurement.

Eight sequential loopback pages; no model/external site, second app or Home.
Timing includes bootstrap/HTTP/receipt polling. Memory covers only the host
process, not Chromium children; it is evidence, not a leak/performance budget.
"""
import ctypes
from ctypes import wintypes
from http.server import BaseHTTPRequestHandler,HTTPServer
import json
import math
import os
import statistics
import sys
import threading
import time
from urllib.parse import urlsplit
import uuid

import dev
from upload_cache_probe import inspect_cache

SCOPE={'extension':'plugin.mdo.browser-use','workspace':str(dev.ROOT)}
REPORT=dev.ENV/'browser-cycles-result.json'
HTML=b'''<!doctype html><meta charset="utf-8"><title>Bounded lifecycle fixture</title>
<h1>Lifecycle fixture</h1><label for="value">Lifecycle value</label><input id="value">
<button id="commit">Commit probe</button><output id="result">pending</output>
<script>let count=0;document.querySelector('#commit').addEventListener('click',event=>{
document.querySelector('#result').textContent=document.querySelector('#value').value+
'|clicks:'+(++count)+'|trusted:'+event.isTrusted;});</script>'''


class Memory(ctypes.Structure):
    _fields_=[('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD),
              *[(name,ctypes.c_size_t) for name in ('PeakWorkingSetSize','WorkingSetSize','QuotaPeakPagedPoolUsage',
                 'QuotaPagedPoolUsage','QuotaPeakNonPagedPoolUsage','QuotaNonPagedPoolUsage','PagefileUsage','PeakPagefileUsage','PrivateUsage')]]


def host_resources(pid):
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];kernel.OpenProcess.restype=wintypes.HANDLE
    kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    kernel.GetProcessHandleCount.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)]
    kernel.K32GetProcessMemoryInfo.argtypes=[wintypes.HANDLE,ctypes.POINTER(Memory),wintypes.DWORD]
    handle=kernel.OpenProcess(0x410,False,pid)
    if not handle:raise ctypes.WinError(ctypes.get_last_error())
    try:
        memory=Memory();memory.cb=ctypes.sizeof(memory);handles=wintypes.DWORD()
        if not kernel.GetProcessHandleCount(handle,ctypes.byref(handles)) or not kernel.K32GetProcessMemoryInfo(handle,ctypes.byref(memory),memory.cb):
            raise ctypes.WinError(ctypes.get_last_error())
        return {'handles':handles.value,'private_bytes':memory.PrivateUsage,'working_set_bytes':memory.WorkingSetSize}
    finally:kernel.CloseHandle(handle)


def main():
    if os.name!='nt':raise RuntimeError('This probe measures the actual Windows host')
    if dev.state().get('window'):raise RuntimeError('An existing visible development window is preserved; start from the headless deployment')
    REPORT.write_text('{"passed":false,"state":"running"}\n')
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def setup(self):super().setup();self.connection.settimeout(.5)
        def do_GET(self):
            if not urlsplit(self.path).path.startswith('/cycle/'):self.send_error(404);return
            self.send_response(200);self.send_header('Content-Type','text/html;charset=utf-8')
            self.send_header('Content-Length',str(len(HTML)));self.send_header('Connection','close');self.end_headers()
            try:self.wfile.write(HTML)
            except (BrokenPipeError,ConnectionResetError):pass
    server=HTTPServer(('127.0.0.1',0),Handler)
    thread=threading.Thread(target=server.serve_forever,kwargs={'poll_interval':.05},daemon=True);thread.start()
    samples=[];resources=[];pending=set();browser=None;baseline=None;stage='startup';failure=None;restored=False
    released=0;requests=0;captures=0;run=uuid.uuid4().hex
    def call(method,params):
        nonlocal requests
        requests+=1;return dev.call('browseruse.'+method,params,SCOPE)
    def tabs():return {row['id'] for row in call('tabs',{'action':'list'})['tabs']}
    def release(identity):
        nonlocal released
        call('result',{'id':identity,'release':True})
        code,_,body=dev.request('POST','/api/v1/application/call',
            {'method':'browseruse.result','params':{'id':identity},'context':SCOPE})
        if code!=410 or json.loads(body).get('error',{}).get('code')!='sdk_ui_receipt_expired':
            raise RuntimeError('Released original UI receipt remained readable')
        pending.discard(identity);released+=1
    def execute(method,params):
        started=time.perf_counter();queued=call(method,params);identity=queued['id'];pending.add(identity)
        end=time.monotonic()+35
        while time.monotonic()<end:
            receipt=call('result',{'id':identity})
            if receipt['state'] in ('succeeded','failed','cancelled'):
                try:
                    if receipt['state']!='succeeded':raise RuntimeError('Public browser operation failed: '+receipt.get('error',{}).get('code','unknown'))
                    return receipt['result'],(time.perf_counter()-started)*1000
                finally:release(identity)
            time.sleep(.05)
        raise RuntimeError('Original browser operation did not reach a terminal state; effects will not be retried')
    try:
        dev.stop();dev.start(window=True)
        end=time.monotonic()+30;clients=[]
        while time.monotonic()<end:
            clients=call('tabs',{'action':'clients'})['clients']
            if clients:break
            time.sleep(.1)
        if len(clients)!=1:raise RuntimeError('The single owned display client is unavailable/ambiguous')
        pid=dev.state()['pid'];baseline=tabs();resources.append(host_resources(pid))
        cache_root=dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers'
        if inspect_cache(cache_root)['remaining_reservations']:raise RuntimeError('Existing transfer reservations preserved; refusing to attribute them to this probe')
        for index in range(8):
            row={};stage='cycle-'+str(index)+'-create';start=time.perf_counter()
            created=call('tabs',{'action':'create','client':clients[0]['id'],
                'url':f'http://127.0.0.1:{server.server_port}/cycle/{index}','key':f'bounded-cycles-{run}-{index}'})
            if created['id'] in baseline:raise RuntimeError('A create reused an unrelated baseline browser; it is preserved')
            browser=created['id'];row['create_ms']=(time.perf_counter()-start)*1000
            stage='cycle-'+str(index)+'-observe';page,row['initial_observe_ms']=execute('observe',{'browser':browser})
            if 'Lifecycle fixture' not in page['text']:raise RuntimeError('The actual fixture did not load')
            field=next(value for value in page['elements'] if value['name']=='Lifecycle value')
            stage='cycle-'+str(index)+'-fill';marker='cycle'+str(index)
            filled,row['fill_ms']=execute('act',{'browser':browser,'document':page['document'],'op':'fill','target':{'ref':field['ref']},'text':marker})
            if filled.get('verification')!='field_state':raise RuntimeError('Native field state was not verified')
            stage='cycle-'+str(index)+'-click'
            _,row['click_ms']=execute('act',{'browser':browser,'document':page['document'],'op':'click','target':{'role':'button','name':'Commit probe'}})
            stage='cycle-'+str(index)+'-verify';observed,row['verify_observe_ms']=execute('observe',{'browser':browser})
            if marker+'|clicks:1|trusted:true' not in observed['text']:raise RuntimeError('The fixture did not observe exactly one trusted click')
            stage='cycle-'+str(index)+'-capture'
            image,row['capture_ms']=execute('screenshot',{'browser':browser,'document':page['document'],'max_width':800,'max_height':600})
            if image.get('image',{}).get('media_type')!='image/png':raise RuntimeError('Native screenshot did not complete')
            captures+=1;del image
            stage='cycle-'+str(index)+'-close';start=time.perf_counter();call('tabs',{'action':'close','browser':browser})
            if tabs()!=baseline:raise RuntimeError('Closed cycle left an owned tab or changed baseline tabs')
            row['close_ms']=(time.perf_counter()-start)*1000;browser=None
            if pending:raise RuntimeError('A cycle retained a pending UI receipt')
            if inspect_cache(cache_root)['remaining_reservations']:raise RuntimeError('A cycle retained a transfer reservation')
            resources.append(host_resources(pid));samples.append(row)
        stage='completed'
    except Exception as error:failure={'stage':stage,'type':type(error).__name__,'message':str(error)[:256]}
    finally:
        try:
            if browser:call('tabs',{'action':'close','browser':browser})
            for identity in tuple(pending):
                receipt=call('result',{'id':identity,'cancel':True})
                end=time.monotonic()+5
                while receipt['state'] not in ('succeeded','failed','cancelled') and time.monotonic()<end:
                    time.sleep(.05);receipt=call('result',{'id':identity})
                if receipt['state'] in ('succeeded','failed','cancelled'):release(identity)
            if pending:raise RuntimeError('Original receipt cleanup remained unconfirmed before teardown')
        except Exception as error:failure=failure or {'stage':'cleanup','type':type(error).__name__,'message':str(error)[:256]}
        try:dev.stop();dev.start();restored=True
        except Exception as error:failure=failure or {'stage':'restore','type':type(error).__name__,'message':str(error)[:256]}
        server.shutdown();server.server_close();thread.join(5)
    timing={}
    if samples:
        for key in samples[0]:
            values=sorted(row[key] for row in samples)
            timing[key]={'samples':len(values),'first':round(samples[0][key],2),'median':round(statistics.median(values),2),
                         'p95_nearest_rank':round(values[math.ceil(.95*len(values))-1],2),'max':round(max(values),2)}
    report=dict(passed=failure is None and len(samples)==8 and restored,cycles=len(samples),captures=captures,
        terminal_receipts_released_and_unreadable=released,service_requests=requests,remaining_receipts=len(pending),
        baseline_tabs_preserved=stage=='completed',timing_ms=timing,host_process_samples=resources,
        memory_scope='host process only; Chromium children excluded; no leak budget or production qualification',
        timing_scope='preauthorized public C SDK with Python bootstrap/HTTP and 50 ms receipt polling; local fixture only',
        screenshots_persisted=0,model_calls=0,external_sites=0,headless_restored=restored,failure=failure)
    REPORT.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
    if not report['passed']:raise RuntimeError('Bounded browser-cycle probe failed; inspect its retained report')


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError) as error:print(error,file=sys.stderr);raise SystemExit(1)
