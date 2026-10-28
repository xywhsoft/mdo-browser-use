"""Exercise public file transfer and managed permissions in the single mdo UI."""
import hashlib
import json
import subprocess
import sys
import time

import dev
from managed_probe import tool,value,FULL,READ_ONLY,SCOPE
from upload_cache_probe import inspect_cache
from upload_source_fixture import source_tree


def main():
    output=dev.ENV/'public-upload-result.json'
    output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    (dev.SOURCE/'app/web/__browser_use_upload__.html').write_bytes((dev.ROOT/'tests/fixtures/upload.html').read_bytes())
    directory=dev.ENV/'qa';directory.mkdir(exist_ok=True)
    source=directory/'public-upload.bin';empty=directory/'public-upload-empty.bin'
    data=bytes(i%251 for i in range(131075));source.write_bytes(data);empty.write_bytes(b'')
    tree,expected=source_tree()
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
        cleared=tool('browser_upload',{'browser':browser,'action':'clear','document':page['document'],'target':{'name':'Multiple attachments'}})
        assert cleared['success'] and value(cleared)['cleared'] and value(cleared)['events_trusted'] is False
        current=value(tool('browser_observe',{'browser':browser}))
        assert next(row for row in current['elements'] if row['name']=='Multiple attachments')['selected_count']==0
        assert value(tool('browser_upload_status',{'browser':browser,'action':'status','ticket':transfer['ticket']}))['retained']
        assert tool('browser_upload',{'browser':browser,'action':'release','ticket':transfer['ticket'],'confirmed':True})['success']
        directory_result=tool('browser_upload',{'browser':browser,'action':'set','document':page['document'],
            'target':{'css':'#directory'},'directory':{'path':'.mdo/qa/public-upload-directory','space':'workspace','name':'managed-directory'},'timeout_ms':15000})
        assert directory_result['success'],directory_result['text']
        directory_assigned=value(directory_result);assert directory_assigned['verification']=='relative_file_list' and directory_assigned['transfer']['retained']
        assert tool('browser_wait',{'browser':browser,'until':'text','text':'managed-directory/nested/'+'n'*240})['success']
        directory_page=value(tool('browser_observe',{'browser':browser}))
        rows=json.loads(directory_page['text'][directory_page['text'].index('[{'):])
        actual=next(row['files'] for row in reversed(rows) if row['id']=='directory')
        assert len(actual)==len(expected)
        for relative,entry in expected.items():
            matched=[file for file in actual if file['path']=='managed-directory/'+relative]
            assert len(matched)==1 and matched[0]['size']==entry['size'] and matched[0]['sha256']==entry['sha256']
        assert tool('browser_upload',{'browser':browser,'action':'release','ticket':directory_assigned['transfer']['ticket'],'confirmed':True})['success']
        picker=value(tool('browser_act',{'browser':browser,'op':'click','document':page['document'],'target':{'css':'#choose-hidden'}}))
        assert picker['outcome']=='file_chooser_open' and not picker['completed']
        pending=value(tool('browser_choosers',{'browser':browser,'action':'list'}))['choosers'][0]
        picked=tool('browser_upload',{'browser':browser,'action':'set','document':page['document'],'chooser':pending['id'],'files':[{'path':str(empty),'name':'managed-picker.txt'}]})
        assert picked['success'] and value(picked)['verified'],picked['text']
        assert not value(tool('browser_choosers',{'browser':browser,'action':'list'}))['choosers']
        assert tool('browser_wait',{'browser':browser,'until':'text','text':'managed-picker.txt'})['success']
        assert tool('browser_upload',{'browser':browser,'action':'release','ticket':value(picked)['transfer']['ticket'],'confirmed':True})['success']
        assert value(tool('browser_act',{'browser':browser,'op':'click','document':page['document'],'target':{'css':'#choose-hidden'}}))['outcome']=='file_chooser_open'
        pending=value(tool('browser_choosers',{'browser':browser,'action':'list'}))['choosers'][0]
        assert tool('browser_chooser_cancel',{'browser':browser,'action':'cancel','chooser':pending['id']})['success']
        readonly=dev.call('browser.create',{'client':clients[0]['id'],'url':url},ro_owner)['id']
        ro_page=value(tool('browser_observe',{'browser':readonly},READ_ONLY))
        assert tool('browser_choosers',{'browser':readonly,'action':'list'},READ_ONLY)['success']
        ro_cancel=tool('browser_chooser_cancel',{'browser':readonly,'action':'cancel','chooser':'fake-picker'},READ_ONLY)
        assert not ro_cancel['success'] and 'context_mismatch' not in ro_cancel['text'],'Read-only session cancelled a picker or ownership hid the permission check'
        denied=tool('browser_upload',{'browser':readonly,'action':'set','document':ro_page['document'],
            'target':{'css':'#single'},'files':[{'path':str(source)}]},READ_ONLY)
        assert not denied['success'],'Read-only managed tool assigned a file'
        assert 'context_mismatch' not in denied['text'],'Foreign ownership hid the permission check'
        denied_clear=tool('browser_upload',{'browser':readonly,'action':'clear','document':ro_page['document'],'target':{'css':'#single'}},READ_ONLY)
        assert not denied_clear['success'] and 'context_mismatch' not in denied_clear['text'],'Read-only session cleared a file input or ownership hid the permission check'
        denied_directory=tool('browser_upload',{'browser':readonly,'action':'set','document':ro_page['document'],
            'target':{'css':'#directory'},'directory':{'path':str(tree)}},READ_ONLY)
        assert not denied_directory['success'] and 'context_mismatch' not in denied_directory['text'],'Read-only session assigned a directory'
        dev.call('browser.close',{'id':readonly},ro_owner);readonly=None
        dev.call('browser.close',{'id':browser},owner);browser=None
        cache=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers')
        assert cache['remaining_reservations']==0
        result={'passed':True,'backend':'actual-public-and-managed-webview2',**js,'managed_upload_and_status':True,
            'read_only_upload_denied_on_owned_tab':True,'managed_clear_and_read_only_denial':True,'full_access_no_extra_prompt':True,
            'managed_chooser_accept_and_cancel':True,'read_only_chooser_list_allowed_cancel_denied':True,
            'managed_directory_relative_paths_and_hashes':True,'read_only_directory_denied':True,'cache':cache}
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
