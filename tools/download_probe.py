"""Actual public C/JS and managed download permissions, in the existing Home."""
import json
import subprocess
import sys
import time
import dev
from download_fixture import DownloadFixture,BODY
from managed_probe import tool,value,FULL,READ_ONLY,SCOPE
from upload_cache_probe import inspect_cache
import hashlib

def main(initial_only=False,managed_only=False):
    output=dev.ENV/'public-download-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
        created=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,'title':'Browser Use managed QA','permission_profile':permission,'workspace_root':str(dev.ROOT)}},SCOPE)
        if created['data']['permission_profile']!=permission:raise RuntimeError('Existing QA permission changed; refusing to overwrite it')
    fixture=DownloadFixture().start();browser=readonly=None;stage='startup'
    def managed(name,arguments,session=FULL):
        nonlocal stage
        stage=name+':'+arguments.get('action','execute');return tool(name,arguments,session)
    owner={'extension':'plugin.mdo.browser-use','project':'default','session':FULL};ro_owner=dict(owner,session=READ_ONLY)
    dev.stop()
    try:
        dev.start(window=True)
        for _ in range(150):
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The normal UI did not register its display client')
        url=fixture.origin+'/page'
        if managed_only:js={'passed':True,'test_scope':'managed-downloads-only'}
        else:
            subprocess.run(['node','tools/download_client_probe.mjs',url]+(['--initial-only'] if initial_only else []),cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=240)
            js=json.loads((dev.ENV/'download-client-result.json').read_text(encoding='utf-8'));assert js['passed']
        browser=value(managed('browser_open',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/binary?managed-initial=1','key':'managed-initial-download'}))['id']
        initial_deadline=time.monotonic()+10;pending=None
        while time.monotonic()<initial_deadline:
            pending=next((item for item in value(managed('browser_downloads',{'browser':browser,'action':'list'}))['downloads'] if item['state']=='pending'),None)
            if pending:break
            time.sleep(.05)
        assert pending,'Managed initial URL did not retain its original response'
        assert managed('browser_download',{'browser':browser,'action':'accept','download':pending['id'],'max_bytes':1048576})['success']
        initial_completed=managed('browser_downloads',{'browser':browser,'action':'wait','download':pending['id']})
        assert initial_completed['success'] and value(initial_completed)['download']['sha256']==hashlib.sha256(BODY).hexdigest(),initial_completed['text']
        assert managed('browser_download',{'browser':browser,'action':'release','download':pending['id']})['success']
        dev.call('browser.close',{'id':browser},owner);browser=None
        browser=value(managed('browser_open',{'action':'create','client':clients[0]['id'],'url':url,'key':'managed-download'}))['id']
        page=value(managed('browser_observe',{'browser':browser}))
        checkpoint=value(managed('browser_act',{'browser':browser,'op':'click','document':page['document'],'target':{'css':'#binary'}}))
        assert checkpoint['outcome']=='download_pending' and not checkpoint['completed']
        pending=value(managed('browser_downloads',{'browser':browser,'action':'list'}))['downloads'][0]
        accepted=managed('browser_download',{'browser':browser,'action':'accept','download':pending['id'],'max_bytes':1048576})
        assert accepted['success'],accepted['text']
        completed=managed('browser_downloads',{'browser':browser,'action':'wait','download':pending['id']})
        assert completed['success'] and value(completed)['download']['sha256']==hashlib.sha256(BODY).hexdigest(),completed['text']
        assert managed('browser_download',{'browser':browser,'action':'release','download':pending['id']})['success']
        readonly=dev.call('browser.create',{'client':clients[0]['id'],'url':url},ro_owner)['id']
        assert managed('browser_observe',{'browser':readonly},READ_ONLY)['success']
        assert managed('browser_downloads',{'browser':readonly,'action':'list'},READ_ONLY)['success']
        for action in ['accept','cancel','release']:
            denied=managed('browser_download',{'browser':readonly,'action':action,'download':'d-fake'},READ_ONLY)
            assert not denied['success'] and 'context_mismatch' not in denied['text'],'Read-only policy was bypassed or hidden by ownership'
        dev.call('browser.close',{'id':readonly},ro_owner);readonly=None
        dev.call('browser.close',{'id':browser},owner);browser=None
        cache=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-downloads');assert cache['remaining_reservations']==0
        assert fixture.counts.get('/post',0)==(0 if initial_only or managed_only else 1) and fixture.counts.get('/post-popup',0)==(0 if managed_only else 1)
        result={**js,'managed_accept_wait_release':True,'managed_initial_url_intercepted':True,'read_only_list_allowed_mutations_denied':True,'full_access_no_extra_prompt':True,'native_cache':cache,'download_requests':dict(fixture.counts)}
        output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'stage':stage,'error':str(error)[:512],'detail':getattr(error,'detail',{}),'download_requests':dict(fixture.counts)})+'\n',encoding='utf-8')
        if isinstance(error,subprocess.CalledProcessError):print((error.stderr or '')[-2800:],file=sys.stderr)
        raise
    finally:
        for identity,context in [(readonly,ro_owner),(browser,owner)]:
            if identity:
                try:dev.call('browser.close',{'id':identity},context)
                except (RuntimeError,OSError):pass
        js_owner={'extension':'browser-use-download-sdk-qa','session':'public-download-js'}
        try:
            for row in dev.call('browser.list',{},js_owner):
                if (row.get('key') in ('download-js-native','download-js-initial') or row.get('key','').startswith('native-popup-')) and row.get('context',{}).get('session')==js_owner['session']:dev.call('browser.close',{'id':row['id']},js_owner)
        except (RuntimeError,OSError):pass
        dev.stop();dev.start();fixture.close()

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__);modes=parser.add_mutually_exclusive_group();modes.add_argument('--initial-only',action='store_true');modes.add_argument('--managed-only',action='store_true');args=parser.parse_args()
    try:main(args.initial_only,args.managed_only)
    except (RuntimeError,OSError,AssertionError,subprocess.SubprocessError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
