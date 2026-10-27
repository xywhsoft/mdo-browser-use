"""Actual mounted browser download tray; same deployment, synthetic files only."""
import json
import sys
import time
import dev
from download_fixture import DownloadFixture,BODY
from upload_cache_probe import inspect_cache

def main():
    directory=dev.ENV/'qa'/'download-ui';directory.mkdir(parents=True,exist_ok=True)
    files=[directory/'ui-saved.bin',directory/'ui-recovered.bin']
    if any(path.exists() for path in files):raise RuntimeError('Previous download UI evidence exists; inspect before rerunning')
    scope={'extension':'download-ui-qa','device':'local','project':'default','session':'download-ui-qa','workspace':str(directory),'namespace':'original-private','member':'original-worker'}
    fixture=DownloadFixture().start();report_path=dev.ENV/'download-ui-result.json';report_path.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    script=(dev.ROOT/'tools/download_ui_probe.js').read_text(encoding='utf-8').replace('PROBE_CONTEXT',json.dumps(scope)).replace('PROBE_ORIGIN',json.dumps(fixture.origin))
    page=dev.SOURCE/'app/web/__browser_use_download_ui__.html'
    page.write_text('<!doctype html><meta charset="utf-8"><link rel="stylesheet" href="/css/workbench.css">'
      '<style>:root{--surface:#171717;--border:#333;--ink:#eee;--muted:#aaa;--focus:#62adff;}body{margin:0;background:#171717;color:#eee;font-family:system-ui}#browser{position:absolute;inset:45px 0 0;display:flex;flex-direction:column;overflow:hidden}#status{margin:8px;font-size:14px}</style>'
      '<h1 id="status">Browser download UI regression</h1><section id="browser" class="wb-view"></section><script type="module">'+script+'</script>',encoding='utf-8')
    dev.call('storage.write',{'path':'qa/download-ui.json','text':'null'},scope);dev.stop();passed=False
    try:
        dev.start(window=True,page='/__browser_use_download_ui__.html');end=time.monotonic()+90;report=None
        while time.monotonic()<end:
            report=json.loads(dev.call('storage.read',{'path':'qa/download-ui.json'},scope)['text'])
            if report is not None:break
            time.sleep(.2)
        if not report:report={'passed':False,'error':'Mounted UI probe did not return a terminal report'}
        if report['passed']:
            assert files[0].read_bytes()==BODY
            assert files[1].read_bytes()==b'recovery-ui-fixture'
            assert dev.call('files.receive.list',{},scope)==[]
            report['cache']=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-downloads')
            assert report['cache']['remaining_reservations']==0
            assert fixture.counts.get('/binary')==1
        report['download_requests']=dict(fixture.counts);report_path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2));passed=report['passed']
        if not passed:raise RuntimeError('Actual download UI regression failed; inspect retained report and files')
    finally:
        dev.stop();dev.start();fixture.close()
        if passed:
            for path in files:path.unlink()

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,AssertionError,ValueError) as error:print(error,file=sys.stderr);raise SystemExit(1)
