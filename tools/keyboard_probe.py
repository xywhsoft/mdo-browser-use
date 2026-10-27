"""Normal C/JS/Agent image-key regression in the one existing deployment/Home."""
import json
import subprocess
import sys
import time
import dev
from frame_fixture import FrameFixture
from managed_probe import FULL,READ_ONLY,SCOPE,tool,value
from upload_cache_probe import inspect_cache


def main():
    output=dev.ENV/'keyboard-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    fixture=FrameFixture().start();browser=readonly=None;stage='startup';owner={'extension':'plugin.mdo.browser-use','project':'default','session':FULL};ro_owner=dict(owner,session=READ_ONLY)
    previous={p.name:(p.stat().st_size,p.stat().st_mtime_ns) for p in (dev.ENV/'host').glob('crash_*')}
    try:
        for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
            saved=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,'title':'Browser Use managed QA','permission_profile':permission,'workspace_root':str(dev.ROOT)}},SCOPE)['data']
            if saved['permission_profile']!=permission:raise RuntimeError('Persistent QA permission changed; refusing to overwrite it')
        dev.stop();dev.start(window=True);clients=[];end=time.monotonic()+30
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The normal workbench did not register')
        stage='public-c-js';subprocess.run(['node','tools/keyboard_client_probe.mjs',fixture.origin+'/visual-key-root'],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=160)
        report=json.loads((dev.ENV/'keyboard-client-result.json').read_text(encoding='utf-8'));assert report['passed']
        stage='managed-canvas';opened=tool('browser_open',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/visual-key-root','key':'managed-visual-key'});assert opened['success'];browser=value(opened)['id']
        def image_target(identity,session):
            page=value(tool('browser_observe',{'browser':identity},session));image=tool('browser_screenshot',{'browser':identity,'document':page['document'],'max_width':1200,'max_height':800},session);assert image['success'];meta=value(image)['visual_reference']
            return page,{'image':meta['id'],'x':round(70*meta['width']/page['viewport']['width']),'y':round(50*meta['height']/page['viewport']['height'])}
        page,target=image_target(browser,FULL)
        acted=tool('browser_act',{'browser':browser,'document':page['document'],'op':'key','key':'ArrowRight','target':target,'timeout_ms':15000});assert acted['success'] and value(acted)['input_method']=='native_key',acted['text']
        assert tool('browser_wait',{'browser':browser,'until':'text','text':'Root keys 1 ArrowRight trusted'})['success']
        dev.call('browser.close',{'id':browser},owner);browser=None
        stage='readonly-owned-canvas';readonly=dev.call('browser.create',{'client':clients[0]['id'],'url':fixture.origin+'/visual-key-root'},ro_owner)['id']
        page,target=image_target(readonly,READ_ONLY)
        denied=tool('browser_act',{'browser':readonly,'document':page['document'],'op':'key','key':'Enter','target':target},READ_ONLY)
        assert not denied['success'] and 'context_mismatch' not in denied['text'] and 'browser_missing' not in denied['text'],denied['text']
        text=value(tool('browser_observe',{'browser':readonly},READ_ONLY))['text'];assert 'Root focuses 0' in text and 'Root keys 0' in text
        dev.call('browser.close',{'id':readonly},ro_owner);readonly=None
        report.update(managed_visual_key=True,owned_readonly_key_denied_before_focus=True,fixture_requests=dict(fixture.counts))
        report['new_native_diagnostics']=[p.name for p in (dev.ENV/'host').glob('crash_*') if previous.get(p.name)!=(p.stat().st_size,p.stat().st_mtime_ns)]
        report['transfer_cache']=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers')
        if report['new_native_diagnostics'] or report['transfer_cache']['remaining_reservations']:report['passed']=False
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
        if not report['passed']:raise RuntimeError('Keyboard gate retained native diagnostics or transfer reservations')
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'stage':stage,'error':str(error)[:512]})+'\n',encoding='utf-8')
        if isinstance(error,subprocess.CalledProcessError):print((error.stderr or '')[-1800:],file=sys.stderr)
        raise
    finally:
        for identity,scope in ((browser,owner),(readonly,ro_owner)):
            if identity:
                try:dev.call('browser.close',{'id':identity},scope)
                except (RuntimeError,OSError):pass
        dev.stop();dev.start();fixture.close()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
