"""Actual mounted browser download tray; same deployment, synthetic files only."""
import json
import subprocess
import sys
import time
import dev
from download_fixture import DownloadFixture,BODY
from upload_cache_probe import inspect_cache

def main():
    directory=dev.ENV/'qa'/'download-ui';directory.mkdir(parents=True,exist_ok=True)
    files=[directory/'ui-saved.bin',directory/'ui-recovered.bin']
    if any(path.exists() for path in files):raise RuntimeError('Previous download UI evidence exists; inspect before rerunning')
    scope={'extension':'download-ui-qa','device':'local','project':'default','session':'b0a00000000000000000000000000004','workspace':'','namespace':'original-private','member':'original-worker'}
    session=dev.call('session.create',{'body':{'project_id':'default','client_session_id':scope['session'],'workspace_root':str(directory),
      'title':'Browser Use download UI legacy QA','permission_profile':'full-access'}},scope)['data']
    if session['workspace_root']!=str(directory):raise RuntimeError('Persistent UI QA task changed; refusing to overwrite it')
    fixture=DownloadFixture().start();report_path=dev.ENV/'download-ui-result.json';report_path.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    dev.call('client.hello',{'client':'download-ui-qa'})
    for previous in dev.call('browser.list',{},scope):
        if previous.get('key')=='download-ui-qa' and previous.get('context',{}).get('session')==scope['session']:
            dev.call('browser.close',{'id':previous['id']},scope)
    browser=dev.call('browser.create',{'client':'download-ui-qa','url':fixture.origin+'/auto-download','download_policy':'intercept','key':'download-ui-qa'},scope)
    script=(dev.ROOT/'tools/download_ui_probe.js').read_text(encoding='utf-8')
    for marker,value in {'PROBE_CONTEXT':scope,'PROBE_HTTP_ORIGIN':fixture.origin,'PROBE_DIRECTORY':str(directory),'PROBE_BROWSER':browser['id']}.items():
        if script.count(marker)!=1:raise RuntimeError('UI probe placeholder changed: '+marker)
        script=script.replace(marker,json.dumps(value))
    subprocess.run(['node','--input-type=module','--check'],input=script,text=True,check=True,capture_output=True,cwd=dev.ROOT)
    page=dev.SOURCE/'app/web/__browser_use_download_ui__.html'
    page.write_text('<!doctype html><meta charset="utf-8"><link rel="stylesheet" href="/css/workbench.css">'
      '<style>:root{--surface:#171717;--border:#333;--ink:#eee;--muted:#aaa;--focus:#62adff;}body{margin:0;background:#171717;color:#eee;font-family:system-ui}#browser{position:absolute;inset:45px 0 0;display:flex;flex-direction:column;overflow:hidden}#status{margin:8px;font-size:14px}</style>'
      '<h1 id="status">Browser download UI regression</h1><section id="browser" class="wb-view"></section><script type="module">'+script+'</script>',encoding='utf-8')
    dev.call('storage.write',{'path':'qa/download-ui.json','text':'null'},scope);dev.stop();passed=False
    try:
        # Upgrade fixture: remove only this owned tab's root while stopped.
        path=dev.HOME/'data/workbench.json';before=path.read_bytes();state=json.loads(before)
        row=next(row for row in state['browsers'] if row['id']==browser['id'])
        if row['owner']!=scope['extension'] or row['context']['session']!=scope['session'] or path.read_bytes()!=before:
            raise RuntimeError('Owned legacy browser fixture changed before preparation')
        row['context'].pop('workspace',None);row['context'].pop('workspace_snapshot',None)
        temporary=path.with_suffix('.download-ui.tmp');temporary.write_text(json.dumps(state,ensure_ascii=False)+'\n',encoding='utf-8');temporary.replace(path)
        dev.start(window=True,page='/__browser_use_download_ui__.html');end=time.monotonic()+90;report=None
        while time.monotonic()<end:
            report=json.loads(dev.call('storage.read',{'path':'qa/download-ui.json'},scope)['text'])
            if report is not None:break
            time.sleep(.2)
        if not report:report={'passed':False,'error':'Mounted UI probe did not return a terminal report'}
        if report['passed']:
            assert files[0].read_bytes()==BODY
            assert files[1].read_bytes()==b'recovery-ui-fixture'
            assert dev.call('files.receive.list',{}, {**scope,'workspace':str(directory)})==[]
            report['cache']=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-downloads')
            assert report['cache']['remaining_reservations']==0
            assert fixture.counts.get('/binary')==1
        report['download_requests']=dict(fixture.counts);report_path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2));passed=report['passed']
        if not passed:raise RuntimeError('Actual download UI regression failed; inspect retained report and files')
    finally:
        try:
            status,_,reply=dev.request('POST','/api/v1/application/call',{'method':'browser.close','params':{'id':browser['id']},'context':scope})
            if status not in (200,404):raise RuntimeError('UI fixture browser cleanup was not confirmed')
        finally:dev.stop();dev.start();fixture.close()
        if passed:
            for path in files:path.unlink()

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,AssertionError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
