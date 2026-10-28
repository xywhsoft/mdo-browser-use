"""Actual public C/JS/WebView2 invalid-certificate refusal and explicit recovery.

All browser destinations are loopback. TLS credentials stay in the owned helper's
memory. No trust-store change, browser certificate override or model call occurs.
"""
import json
import sys
import time
import dev
from sdk_probe import SCOPE,wait_receipt
from network_fixture import NetworkFixture
from tls_fixture import TlsFixture
from upload_cache_probe import inspect_cache


def main():
    output=dev.ENV/'tls-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    browser=None;cases=[];stage='startup';tls=TlsFixture().start();fixture=NetworkFixture().start();fixture.redirect_url=tls.origin+'/redirected'
    def execute(method,params,raw=False):
        started=time.monotonic();queued=dev.call('browser.command' if raw else method,{'id':browser,**params} if raw else {'browser':browser,**params},SCOPE)
        try:
            result=wait_receipt(queued['id'],service='browser.result' if raw else 'browseruse.result',allow_failure=True,timeout=20)
            return result,round((time.monotonic()-started)*1000)
        finally:dev.call('browser.result' if raw else 'browseruse.result',{'id':queued['id'],'release':True},SCOPE)
    def native():
        receipt,_=execute('',{'command':'state'},raw=True)
        if receipt['state']!='succeeded':raise RuntimeError('The original native browser state was unavailable')
        return receipt['result']
    def refused(name,method,params):
        receipt,elapsed=execute(method,params);state=native();stats=tls.stats()
        error=receipt.get('error');code=error.get('code') if isinstance(error,dict) else None
        cases.append({'case':name,'state':receipt['state'],'code':code,'native_error_code':state.get('error_code'),
                      'elapsed_ms':elapsed,'tls_connections':stats['connections'],'tls_http_requests':stats['http_requests'],'tls_failures':stats['failures'],
                      'tls_last_ssl_reason':stats.get('last_ssl_reason')})
        # Pinned WebView2 header: CERTIFICATE_IS_INVALID=5. A generic failure or
        # unopened server is insufficient evidence of certificate enforcement.
        if receipt['state']!='failed' or state.get('error_code')!=5 or state.get('process_failed') is not False or stats['connections']<1 or stats['http_requests']!=0:
            raise RuntimeError('The invalid certificate was not refused before HTTP application data')
    def recover(name):
        receipt,elapsed=execute('browseruse.navigate',{'action':'goto','url':fixture.origin+'/ok?case='+name,'timeout_ms':8000})
        page=receipt.get('result',{}).get('observation',{})
        cases.append({'case':name,'state':receipt['state'],'elapsed_ms':elapsed})
        if receipt['state']!='succeeded' or page.get('title')!='Network fixture' or page.get('frame_routing')!='native-isolated-v1':
            raise RuntimeError('The same browser did not recover to its isolated HTTP fixture')
        state=native()
        if state.get('error_code')!=0 or state.get('error'):raise RuntimeError('Recovery retained the previous certificate failure')
        return page
    try:
        dev.stop();dev.start(window=True);clients=[];end=time.monotonic()+30
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The ordinary mdo workbench did not register')
        browser=dev.call('browseruse.tabs',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/ok','key':'tls-refusal-public'},SCOPE)['id']
        initial,_=execute('browseruse.observe',{})
        if initial['state']!='succeeded':raise RuntimeError('The initial HTTP fixture did not become ready')
        stage='direct-invalid-tls';refused(stage,'browseruse.navigate',{'action':'goto','url':tls.origin+'/direct','timeout_ms':6000})
        stage='capture-after-certificate-error'
        captured,_=execute('browseruse.screenshot',{'max_width':320,'max_height':240,'max_bytes':8192,'timeout_ms':400})
        cases.append({'case':stage,'state':captured['state'],'image_published':bool(captured.get('result',{}).get('image'))})
        if captured['state']!='failed' or captured.get('result',{}).get('image'):raise RuntimeError('A certificate error published browser pixels')
        recover('after-direct-invalid-tls')
        stage='redirect-invalid-tls';refused(stage,'browseruse.navigate',{'action':'goto','url':fixture.origin+'/tls-redirect','timeout_ms':6000})
        if fixture.counts.get('/tls-redirect')!=1:raise RuntimeError('The intended HTTP redirect was not followed once')
        recover('after-redirect-invalid-tls')
        dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE);browser=None

        stage='initial-invalid-tls'
        browser=dev.call('browseruse.tabs',{'action':'create','client':clients[0]['id'],'url':tls.origin+'/initial','key':'tls-refusal-initial'},SCOPE)['id']
        refused(stage,'browseruse.observe',{'timeout_ms':6000});recover('after-initial-invalid-tls')
        state=native();flags={key:state.get(key) for key in ('loading','input_busy','capture_busy','dialog_open','popup_open','chooser_cleanup_failed','upload_cleanup_failed','drag_cleanup_failed')}
        if any(value is not False for value in flags.values()):raise RuntimeError('TLS refusal/recovery retained native work or cleanup faults')
        dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE);browser=None
        stats=tls.stats()
        if stats['http_requests']!=0 or not stats['key_ephemeral']:raise RuntimeError('TLS credentials or refused request proof was invalid')
        caches={name:inspect_cache(dev.HOME/'data/cache/webview2-content'/directory) for name,directory in
            [('upload','mdo-browser-use-transfers'),('download','mdo-browser-use-downloads')]}
        if any(cache['remaining_reservations'] for cache in caches.values()):raise RuntimeError('TLS recovery left transfer reservations')
        report={'passed':False,'state':'browser_checks_passed','backend':'actual-public-c-js-webview2-tls','cases':cases,'tls_stats':stats,'native_flags':flags,'native_caches':caches,
                'trust_store_modified_by_probe':False,'certificate_override_added':False,'private_key_written':False,'model_calls':0,'external_sites':0}
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'stage':stage,'error':str(error)[:512],'cases':cases},indent=2)+'\n',encoding='utf-8');raise
    finally:
        if browser:
            try:dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE)
            except (RuntimeError,OSError):pass
        try:
            try:dev.stop();dev.start()
            finally:
                try:fixture.close()
                finally:tls.close()
        except Exception:
            output.write_text(json.dumps({'passed':False,'state':'cleanup_failed','stage':stage,'cases':cases})+'\n',encoding='utf-8');raise
    report.update({'passed':True,'state':'passed','headless_restored':True,'tls_fixture_stopped':tls.child.poll()==0})
    output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError) as error:print(error,file=sys.stderr);raise SystemExit(1)
