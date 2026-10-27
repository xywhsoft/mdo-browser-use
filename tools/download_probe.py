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

def main():
    output=dev.ENV/'public-download-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
        created=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,'title':'Browser Use managed QA','permission_profile':permission,'workspace_root':str(dev.ROOT)}},SCOPE)
        if created['data']['permission_profile']!=permission:raise RuntimeError('Existing QA permission changed; refusing to overwrite it')
    fixture=DownloadFixture().start();browser=readonly=None
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
        subprocess.run(['node','tools/download_client_probe.mjs',url],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=240)
        js=json.loads((dev.ENV/'download-client-result.json').read_text(encoding='utf-8'));assert js['passed']
        browser=value(tool('browser_open',{'action':'create','client':clients[0]['id'],'url':url,'key':'managed-download'}))['id']
        page=value(tool('browser_observe',{'browser':browser}))
        checkpoint=value(tool('browser_act',{'browser':browser,'op':'click','document':page['document'],'target':{'css':'#binary'}}))
        assert checkpoint['outcome']=='download_pending' and not checkpoint['completed']
        pending=value(tool('browser_downloads',{'browser':browser,'action':'list'}))['downloads'][0]
        accepted=tool('browser_download',{'browser':browser,'action':'accept','download':pending['id'],'max_bytes':1048576})
        assert accepted['success'],accepted['text']
        completed=tool('browser_downloads',{'browser':browser,'action':'wait','download':pending['id']})
        assert completed['success'] and value(completed)['download']['sha256']==hashlib.sha256(BODY).hexdigest(),completed['text']
        assert tool('browser_download',{'browser':browser,'action':'release','download':pending['id']})['success']
        readonly=dev.call('browser.create',{'client':clients[0]['id'],'url':url},ro_owner)['id']
        assert tool('browser_observe',{'browser':readonly},READ_ONLY)['success']
        assert tool('browser_downloads',{'browser':readonly,'action':'list'},READ_ONLY)['success']
        for action in ['accept','cancel','release']:
            denied=tool('browser_download',{'browser':readonly,'action':action,'download':'d-fake'},READ_ONLY)
            assert not denied['success'] and 'context_mismatch' not in denied['text'],'Read-only policy was bypassed or hidden by ownership'
        dev.call('browser.close',{'id':readonly},ro_owner);readonly=None
        dev.call('browser.close',{'id':browser},owner);browser=None
        cache=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-downloads');assert cache['remaining_reservations']==0
        assert fixture.counts.get('/post')==1
        result={**js,'managed_accept_wait_release':True,'read_only_list_allowed_mutations_denied':True,'full_access_no_extra_prompt':True,'native_cache':cache,'download_requests':dict(fixture.counts)}
        output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'error':str(error)[:512]})+'\n',encoding='utf-8')
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
                if row.get('key')=='download-js-native' and row.get('context',{}).get('session')==js_owner['session']:dev.call('browser.close',{'id':row['id']},js_owner)
        except (RuntimeError,OSError):pass
        dev.stop();dev.start();fixture.close()

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,AssertionError,subprocess.SubprocessError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
