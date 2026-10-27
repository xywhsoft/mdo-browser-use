"""Public and managed save gate, using the same application and Home."""
import hashlib
import json
import subprocess
import sys
import time
import dev
from download_fixture import DownloadFixture,BODY
from managed_probe import tool,value,FULL,READ_ONLY,SCOPE
from upload_cache_probe import inspect_cache

def main():
    output=dev.ENV/'public-save-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    directory=dev.ENV/'qa'/'saved';directory.mkdir(parents=True,exist_ok=True)
    target=directory/'managed-save.bin';browser=None;stage='startup'
    fixture=DownloadFixture().start();owner={'extension':'plugin.mdo.browser-use','project':'default','session':FULL}
    for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
        created=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,'title':'Browser Use managed QA','permission_profile':permission,'workspace_root':str(dev.ROOT)}},SCOPE)
        if created['data']['permission_profile']!=permission:raise RuntimeError('Refusing to overwrite QA permission scope')
    dev.stop()
    try:
        dev.start(window=True)
        end=time.monotonic()+15;clients=[]
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        assert len(clients)==1
        stage='public-c-js'
        subprocess.run(['node','tools/save_client_probe.mjs',fixture.origin+'/page'],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=240)
        public=json.loads((dev.ENV/'save-client-result.json').read_text(encoding='utf-8'));assert public['passed']
        stage='managed-open';browser=value(tool('browser_open',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/binary?managed-save=1','key':'managed-save'}))['id']
        end=time.monotonic()+10;pending=None
        while time.monotonic()<end:
            pending=next((row for row in value(tool('browser_downloads',{'browser':browser,'action':'list'}))['downloads'] if row['state']=='pending'),None)
            if pending:break
            time.sleep(.05)
        assert pending
        assert tool('browser_download',{'browser':browser,'action':'accept','download':pending['id']})['success']
        assert tool('browser_downloads',{'browser':browser,'action':'wait','download':pending['id']})['success']
        stage='managed-save';result=tool('browser_save_download',{'browser':browser,'download':pending['id'],'path':target.relative_to(dev.ROOT).as_posix()})
        assert result['success'],result['text'];saved=value(result);assert saved['saved'] and saved['file']['sha256']==hashlib.sha256(BODY).hexdigest()
        assert target.read_bytes()==BODY
        stage='read-only-save';denied=tool('browser_save_download',{'browser':browser,'download':pending['id'],'path':'denied.bin'},READ_ONLY)
        assert not denied['success'] and 'context_mismatch' not in denied['text'] and 'browser_missing' not in denied['text'],denied['text']
        assert target.read_bytes()==BODY
        assert tool('browser_download',{'browser':browser,'action':'release','download':pending['id']})['success']
        dev.call('browser.close',{'id':browser},owner);browser=None
        cache=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-downloads');assert cache['remaining_reservations']==0
        assert list((dev.HOME/'data/application/receivers').iterdir())==[]
        assert fixture.counts.get('/post')==1 and fixture.counts.get('/post-popup')==1,fixture.counts
        report={**public,'managed_save':True,'read_only_denied_before_browser_access':True,'full_access_no_extra_prompt':True,'native_cache':cache,'download_requests':dict(fixture.counts)}
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'stage':stage,'error':str(error)[:512],'download_requests':dict(fixture.counts)})+'\n',encoding='utf-8')
        if isinstance(error,subprocess.CalledProcessError):print((error.stderr or '')[-2500:],file=sys.stderr)
        raise
    finally:
        if browser:
            try:dev.call('browser.close',{'id':browser},owner)
            except (RuntimeError,OSError):pass
        target.unlink(missing_ok=True);dev.stop();dev.start();fixture.close()

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,AssertionError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
