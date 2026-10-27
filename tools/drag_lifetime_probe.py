"""Actual native original-response/observer lifetime gate in the single Home."""
import json
import subprocess
import sys
import time
import dev
from frame_fixture import FrameFixture
from upload_cache_probe import inspect_cache


def main():
    output=dev.ENV/'drag-lifetime-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    scope={'extension':'browser-drag-lifetime-qa'};fixture=FrameFixture().start()
    previous={p.name:(p.stat().st_size,p.stat().st_mtime_ns) for p in (dev.ENV/'host').glob('crash_*')}
    try:
        source=(dev.ROOT/'tools/drag_lifetime_probe.js').read_text(encoding='utf-8')
        if source.count('DRAG_LIFETIME_ORIGIN')!=1:raise RuntimeError('Lifetime probe placeholder changed')
        source=source.replace('DRAG_LIFETIME_ORIGIN',json.dumps(fixture.origin))
        subprocess.run(['node','--input-type=module','--check'],input=source,text=True,encoding='utf-8',capture_output=True,check=True,cwd=dev.ROOT)
        (dev.SOURCE/'app/web/__browser_use_drag_lifetime__.html').write_text('<!doctype html><h1 id="status">Drag lifetime gate</h1><script type="module">'+source+'</script>',encoding='utf-8')
        dev.call('storage.write',{'path':'qa/drag-lifetime.json','text':'null'},scope);dev.stop();dev.start(window=True,page='/__browser_use_drag_lifetime__.html')
        report=None;end=time.monotonic()+80
        while time.monotonic()<end:
            saved=dev.call('storage.read',{'path':'qa/drag-lifetime.json','limit':262144},scope)
            if saved['bytes']!=saved['size']:raise RuntimeError('The lifetime report exceeds its 256 KiB budget')
            report=json.loads(saved['text'])
            if report is not None:break
            time.sleep(.15)
        if report is None:report={'passed':False,'error':'No terminal lifetime report'}
        report['fixture_requests']=dict(fixture.counts)
        report['new_native_diagnostics']=[p.name for p in (dev.ENV/'host').glob('crash_*') if previous.get(p.name)!=(p.stat().st_size,p.stat().st_mtime_ns)]
        report['transfer_cache']=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers')
        if fixture.counts.get('POST /drag-download')!=1 or report['new_native_diagnostics'] or report['transfer_cache']['remaining_reservations']:report['passed']=False
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
        if not report['passed']:raise RuntimeError('Drag lifetime gate failed; inspect retained evidence')
    finally:dev.stop();dev.start();fixture.close()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
