"""Actual public C/JS/WebView2 failures/cancel/recovery; synthetic loopback only."""
import json
import sys
import time
import uuid
import dev
from sdk_probe import SCOPE,wait_receipt
from network_fixture import NetworkFixture
from upload_cache_probe import inspect_cache


def main():
    output=dev.ENV/'network-result.json';browser=None;cases=[];operations=[];stage='startup';last_failure=None
    output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    fixture=NetworkFixture().start()
    def error_code(receipt):
        error=receipt.get('error');return error.get('code') if isinstance(error,dict) else None
    def execute(method,params,timeout=15):
        started=time.monotonic();queued=dev.call(method,{'browser':browser,**params},SCOPE)
        try:
            receipt=wait_receipt(queued['id'],timeout=timeout,allow_failure=True)
            return receipt,round((time.monotonic()-started)*1000)
        finally:dev.call('browseruse.result',{'id':queued['id'],'release':True},SCOPE)
    def success(method,params):
        nonlocal last_failure
        receipt,elapsed=execute(method,params)
        if receipt['state']!='succeeded' or receipt.get('result',{}).get('ok') is not True:
            last_failure={'method':method,'state':receipt['state'],'error':receipt.get('error'),'elapsed_ms':elapsed}
            raise RuntimeError('The intended recovery did not succeed: '+str(receipt.get('error'))[:512])
        return receipt['result'],elapsed
    def recover(name):
        result,elapsed=success('browseruse.navigate',{'action':'goto','url':fixture.origin+'/ok?case='+name,'timeout_ms':8000})
        page=result['observation']
        if page.get('title')!='Network fixture' or page.get('frame_routing')!='native-isolated-v1':raise RuntimeError('Recovery did not reach the actual isolated fixture document')
        cases.append({'case':name,'state':'succeeded','elapsed_ms':elapsed});return page
    def operation(action,identity,**params):return dev.call('browseruse.operation',{'action':action,'operation':identity,**params},SCOPE)
    try:
        dev.stop();dev.start(window=True);clients=[];end=time.monotonic()+30
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The ordinary mdo workbench did not register')
        browser=dev.call('browseruse.tabs',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/ok','key':'network-recovery-public'},SCOPE)['id']
        initial,_=success('browseruse.observe',{});original=initial['document']
        stage='connection-reset'
        failed,elapsed=execute('browseruse.navigate',{'action':'goto','url':fixture.origin+'/reset','timeout_ms':5000})
        code=error_code(failed)
        cases.append({'case':stage,'state':failed['state'],'code':code,'elapsed_ms':elapsed})
        if failed['state']!='failed' or code not in ('navigation_failed','browser_unavailable'):raise RuntimeError('Connection reset was not returned as a bounded network failure')
        page=recover('after-reset')
        if page['document']==original:raise RuntimeError('Recovery reused the failed original document identity')

        stage='request-timeout'
        failed,elapsed=execute('browseruse.navigate',{'action':'goto','url':fixture.origin+'/get-stall','timeout_ms':800})
        code=error_code(failed)
        cases.append({'case':stage,'state':failed['state'],'code':code,'requested_timeout_ms':800,'elapsed_ms':elapsed})
        if failed['state']!='failed' or code!='timed_out' or elapsed>6000 or not fixture.seen['get'].is_set():raise RuntimeError('Stalled navigation did not report a bounded timeout')
        fixture.release['get'].set()
        if not fixture.finished['get'].wait(4):raise RuntimeError('The held GET fixture did not settle')
        page=recover('after-timeout')

        stage='cancel-read'
        queued=dev.call('browseruse.wait',{'browser':browser,'document':page['document'],'until':'text','text':'never-present-fixture-text','timeout_ms':5000},SCOPE)
        try:
            time.sleep(.2);started=time.monotonic();dev.call('browseruse.result',{'id':queued['id'],'cancel':True},SCOPE)
            cancelled=wait_receipt(queued['id'],allow_failure=True,timeout=5)
            elapsed=round((time.monotonic()-started)*1000)
            cases.append({'case':stage,'state':cancelled['state'],'elapsed_ms':elapsed})
            if cancelled['state']!='cancelled' or elapsed>4000:raise RuntimeError('Read cancellation did not settle')
        finally:dev.call('browseruse.result',{'id':queued['id'],'release':True},SCOPE)
        page,_=success('browseruse.observe',{})

        stage='cancel-original-post'
        identity='sdk-'+uuid.uuid4().hex;operations.append(identity)
        request={'method':'browseruse.act','arguments':{'browser':browser,'document':page['document'],'op':'click','target':{'css':'#submit-once'},'timeout_ms':5000}}
        operation('submit',identity,**request);end=time.monotonic()+5
        while time.monotonic()<end and not fixture.seen['post'].is_set():time.sleep(.03)
        if not fixture.seen['post'].is_set():raise RuntimeError('The original native POST was not received')
        started=time.monotonic();operation('cancel',identity);end=time.monotonic()+5
        while time.monotonic()<end:
            cancelled=operation('get',identity)
            if cancelled['state'] in ('succeeded','failed','cancelled','uncertain'):break
            time.sleep(.05)
        elapsed=round((time.monotonic()-started)*1000)
        duplicate=operation('submit',identity,**request)
        cases.append({'case':stage,'state':cancelled['state'],'duplicate_state':duplicate['state'],'elapsed_ms':elapsed})
        if cancelled['state']!='cancelled' or duplicate['state']!='cancelled' or fixture.valid_posts!=1:raise RuntimeError('Cancelled durable admission was replayed or not terminal')
        fixture.release['post'].set()
        if not fixture.finished['post'].wait(4):raise RuntimeError('The original POST response did not settle')
        page=recover('after-cancelled-post')
        if fixture.valid_posts!=1 or fixture.counts.get('/post-stall')!=1:raise RuntimeError('Recovery repeated the submitted POST')
        operation('release',identity);operations.remove(identity)

        stage='native-resource-state'
        queued=dev.call('browser.command',{'id':browser,'command':'state'},SCOPE)
        try:state=wait_receipt(queued['id'],service='browser.result')['result']
        finally:dev.call('browser.result',{'id':queued['id'],'release':True},SCOPE)
        flags={key:state.get(key) for key in ('loading','input_busy','capture_busy','dialog_open','popup_open','chooser_cleanup_failed','upload_cleanup_failed','drag_cleanup_failed')}
        if any(value is not False for value in flags.values()):raise RuntimeError('Native work or cleanup remained after recovery')
        dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE);browser=None
        caches={name:inspect_cache(dev.HOME/'data/cache/webview2-content'/directory) for name,directory in
                [('upload','mdo-browser-use-transfers'),('download','mdo-browser-use-downloads')]}
        if any(cache['remaining_reservations'] for cache in caches.values()):raise RuntimeError('Network recovery left native transfer reservations')
        report={'passed':True,'backend':'actual-public-c-js-webview2-network','cases':cases,'original_posts':fixture.valid_posts,
                'request_counts':dict(fixture.counts),'native_flags':flags,'native_caches':caches,'model_calls':0,'external_sites':0}
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'stage':stage,'error':str(error)[:512],'cases':cases,'last_failure':last_failure,'request_counts':dict(fixture.counts)},indent=2)+'\n',encoding='utf-8');raise
    finally:
        for event in fixture.release.values():event.set()
        for identity in operations:
            try:operation('cancel',identity);operation('release',identity)
            except (RuntimeError,OSError):pass
        if browser:
            try:dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE)
            except (RuntimeError,OSError):pass
        dev.stop();dev.start();fixture.close()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError) as error:print(error,file=sys.stderr);raise SystemExit(1)
