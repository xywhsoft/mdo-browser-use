"""Exercise public file transfer and managed permissions in the single mdo UI."""
import hashlib
import json
import subprocess
import sys
import time

import dev
from managed_probe import tool,value,FULL,READ_ONLY,SCOPE
from upload_cache_probe import inspect_cache


def main():
    output=dev.ENV/'public-upload-result.json'
    output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    (dev.SOURCE/'app/web/__browser_use_upload__.html').write_bytes((dev.ROOT/'tests/fixtures/upload.html').read_bytes())
    directory=dev.ENV/'qa';directory.mkdir(exist_ok=True)
    source=directory/'public-upload.bin';empty=directory/'public-upload-empty.bin'
    data=bytes(i%251 for i in range(131075));source.write_bytes(data);empty.write_bytes(b'')
    for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
        created=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,
            'title':'Browser Use managed QA','permission_profile':permission,'workspace_root':str(dev.ROOT)}},SCOPE)
        if created['data']['permission_profile']!=permission:raise RuntimeError('Existing QA permission changed; refusing to overwrite it')
    dev.stop();browser=None;readonly=None
    owner={'extension':'plugin.mdo.browser-use','project':'default','session':FULL}
    ro_owner=dict(owner,session=READ_ONLY)
    try:
        dev.start(window=True)
        for _ in range(150):
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The normal mdo UI did not register its display client')
        subprocess.run(['node','tools/upload_client_probe.mjs'],cwd=dev.ROOT,check=True,capture_output=True,text=True)
        js=json.loads((dev.ENV/'upload-client-result.json').read_text(encoding='utf-8'))
        assert js['passed']
        url=f"http://127.0.0.1:{dev.state()['port']}/__browser_use_upload__.html"
        browser=value(tool('browser_open',{'action':'create','client':clients[0]['id'],'url':url,'key':'managed-upload'}))['id']
        page=value(tool('browser_observe',{'browser':browser}))
        assigned=tool('browser_upload',{'browser':browser,'action':'set','document':page['document'],
            'target':{'name':'Multiple attachments'},'files':[{'path':'.mdo/qa/public-upload.bin','space':'workspace'},{'path':str(empty)}],'timeout_ms':15000})
        assert assigned['success'],assigned['text']
        transfer=value(assigned)['transfer'];assert transfer['retained']
        status=tool('browser_upload_status',{'browser':browser,'action':'status','ticket':transfer['ticket']})
        assert status['success'] and value(status)['verified']
        consumed=tool('browser_wait',{'browser':browser,'until':'text','text':hashlib.sha256(data).hexdigest()})
        assert consumed['success'],consumed['text']
        assert tool('browser_upload',{'browser':browser,'action':'release','ticket':transfer['ticket'],'confirmed':True})['success']
        readonly=dev.call('browser.create',{'client':clients[0]['id'],'url':url},ro_owner)['id']
        ro_page=value(tool('browser_observe',{'browser':readonly},READ_ONLY))
        denied=tool('browser_upload',{'browser':readonly,'action':'set','document':ro_page['document'],
            'target':{'css':'#single'},'files':[{'path':str(source)}]},READ_ONLY)
        assert not denied['success'],'Read-only managed tool assigned a file'
        assert 'context_mismatch' not in denied['text'],'Foreign ownership hid the permission check'
        dev.call('browser.close',{'id':readonly},ro_owner);readonly=None
        dev.call('browser.close',{'id':browser},owner);browser=None
        cache=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers')
        assert cache['remaining_reservations']==0
        result={'passed':True,'backend':'actual-public-and-managed-webview2',**js,'managed_upload_and_status':True,
            'read_only_upload_denied_on_owned_tab':True,'full_access_no_extra_prompt':True,'cache':cache}
        output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'error':str(error)[:512]})+'\n',encoding='utf-8')
        if isinstance(error,subprocess.CalledProcessError):print((error.stderr or '')[-2000:],file=sys.stderr)
        raise
    finally:
        if readonly:
            try:dev.call('browser.close',{'id':readonly},ro_owner)
            except (RuntimeError,OSError):pass
        if browser:
            try:dev.call('browser.close',{'id':browser},owner)
            except (RuntimeError,OSError):pass
        dev.stop();dev.start()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,AssertionError,subprocess.CalledProcessError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
