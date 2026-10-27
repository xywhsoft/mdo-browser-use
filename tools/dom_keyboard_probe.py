"""Actual ordinary DOM keyboard regression in the single development Home."""
import json
import re
import subprocess
import sys
import time
from urllib.request import urlopen
import dev
from frame_fixture import FrameFixture
from managed_probe import FULL,READ_ONLY,SCOPE,tool,value
from upload_cache_probe import inspect_cache


def main():
    fixture=FrameFixture().start();output=dev.ENV/'dom-keyboard-result.json';browser=readonly=None;stage='startup';report=None
    owner={'extension':'plugin.mdo.browser-use','project':'default','session':FULL};ro_owner=dict(owner,session=READ_ONLY)
    previous={p.name:(p.stat().st_size,p.stat().st_mtime_ns) for p in (dev.ENV/'host').glob('crash_*')}
    output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    try:
        for route in ('/dom-key-root','/dom-key-child'):
            source=urlopen(fixture.origin+route,timeout=5).read().decode('utf-8')
            scripts='\n'.join(re.findall(r'<script>(.*?)</script>',source,re.S))
            if not scripts:raise RuntimeError('DOM fixture has no executable script')
            checked=subprocess.run(['node','--input-type=module','--check'],input=scripts,capture_output=True,text=True,cwd=dev.ROOT)
            if checked.returncode:raise RuntimeError('DOM fixture script syntax failed before native execution: '+checked.stderr[-800:])
        for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
            saved=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,'title':'Browser Use managed QA','permission_profile':permission,'workspace_root':str(dev.ROOT)}},SCOPE)['data']
            if saved['permission_profile']!=permission:raise RuntimeError('Persistent QA permission changed; refusing to overwrite it')
        dev.stop();dev.start(window=True);clients=[];end=time.monotonic()+30
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The normal workbench did not register')
        stage='public-c-js';completed=subprocess.run(['node','tools/dom_keyboard_client_probe.mjs',fixture.origin+'/dom-key-root'],cwd=dev.ROOT,capture_output=True,text=True,timeout=240)
        report=json.loads((dev.ENV/'dom-keyboard-client-result.json').read_text(encoding='utf-8'))
        if not completed.returncode and report['passed']:
            stage='managed-dom-input'
            opened=tool('browser_open',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/dom-key-root','key':'managed-dom-key'});assert opened['success'];browser=value(opened)['id']
            page=value(tool('browser_observe',{'browser':browser}));acted=tool('browser_act',{'browser':browser,'document':page['document'],'op':'fill','target':{'label':'Root field'},'text':'managed-synthetic'});assert acted['success'] and value(acted)['verification']=='field_state',acted['text']
            assert tool('browser_wait',{'browser':browser,'until':'text','text':'Root field clicks 0'})['success']
            acted=tool('browser_act',{'browser':browser,'document':page['document'],'op':'key','key':'Enter','target':{'role':'button','name':'Root activate'}});assert acted['success'],acted['text']
            assert tool('browser_wait',{'browser':browser,'until':'text','text':'Root activations 1 trusted'})['success']
            dev.call('browser.close',{'id':browser},owner);browser=None
            stage='owned-readonly';readonly=dev.call('browser.create',{'client':clients[0]['id'],'url':fixture.origin+'/dom-key-root'},ro_owner)['id'];page=value(tool('browser_observe',{'browser':readonly},READ_ONLY))
            denied=tool('browser_act',{'browser':readonly,'document':page['document'],'op':'fill','target':{'label':'Root field'},'text':'must-not-be-sent'},READ_ONLY)
            assert not denied['success'] and 'context_mismatch' not in denied['text'] and 'browser_missing' not in denied['text'],denied['text']
            text=value(tool('browser_observe',{'browser':readonly},READ_ONLY))['text'];assert 'Root field focuses 0' in text and 'Root field inputs 0' in text
            dev.call('browser.close',{'id':readonly},ro_owner);readonly=None
            report.update(managed_dom_fill_and_key=True,owned_readonly_denied_before_focus=True)
        report['fixture_requests']=dict(fixture.counts)
        report['new_native_diagnostics']=[p.name for p in (dev.ENV/'host').glob('crash_*') if previous.get(p.name)!=(p.stat().st_size,p.stat().st_mtime_ns)]
        report['transfer_cache']=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers')
        if fixture.counts.get('POST /drag-download')!=1 or report['new_native_diagnostics'] or report['transfer_cache']['remaining_reservations']:report['passed']=False
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
        if completed.returncode or not report['passed']:raise RuntimeError('DOM keyboard regression failed; inspect retained evidence')
    except Exception as error:
        failure=report or {};failure.update(passed=False,stage=failure.get('stage',stage),error=failure.get('error') or str(error)[:512])
        output.write_text(json.dumps(failure,indent=2)+'\n',encoding='utf-8');raise
    finally:
        for identity,scope in ((browser,owner),(readonly,ro_owner)):
            if identity:
                try:dev.call('browser.close',{'id':identity},scope)
                except (RuntimeError,OSError):pass
        dev.stop();dev.start();fixture.close()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
