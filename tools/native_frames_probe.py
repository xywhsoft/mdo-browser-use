"""Read actual cross-site/OOPIF frame contexts in the one native deployment."""
import json
import subprocess
import sys
import time
import dev
from frame_fixture import FrameFixture


def main():
    output=dev.ENV/'native-frames-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    scope={'extension':'browser-frame-qa'};fixture=FrameFixture().start();page=dev.SOURCE/'app/web/__browser_use_native_frames__.html'
    before={p.name:(p.stat().st_size,p.stat().st_mtime_ns) for p in (dev.ENV/'host').glob('crash_*')}
    try:
        runtime=(dev.ROOT/'page/runtime.js').read_text(encoding='utf-8')
        if runtime.count('export function browserRuntime(')!=1:raise RuntimeError('Frame runtime export changed')
        runtime=runtime.replace('export function browserRuntime(','function browserRuntime(')
        script=(dev.ROOT/'tools/native_frames_probe.js').read_text(encoding='utf-8')
        for marker,value in {'FRAME_FIXTURE_ORIGIN':fixture.origin,'FRAME_RUNTIME_SOURCE':runtime}.items():
            if script.count(marker)!=1:raise RuntimeError('Frame probe placeholder changed')
            script=script.replace(marker,json.dumps(value).replace('<','\\u003c'))
        subprocess.run(['node','--input-type=module','--check'],input=script,text=True,encoding='utf-8',capture_output=True,check=True,cwd=dev.ROOT)
        page.write_text('<!doctype html><meta charset="utf-8"><h1 id="status">Native frame checks</h1><script type="module">'+script+'</script>',encoding='utf-8')
        dev.call('storage.write',{'path':'qa/native-frames.json','text':'null'},scope);dev.stop()
        dev.start(window=True,page='/__browser_use_native_frames__.html');end=time.monotonic()+120;report=None
        while time.monotonic()<end:
            report=json.loads(dev.call('storage.read',{'path':'qa/native-frames.json'},scope)['text'])
            if report is not None:break
            time.sleep(.2)
        if not report:report={'passed':False,'error':'Native frame checks did not return a terminal report'}
        report['new_native_diagnostics']=[p.name for p in (dev.ENV/'host').glob('crash_*') if before.get(p.name)!=(p.stat().st_size,p.stat().st_mtime_ns)]
        if report['new_native_diagnostics']:report.update(passed=False,error='Native exception diagnostics were generated')
        report['download_or_model_calls']=False;report['fixture_requests']=dict(fixture.counts)
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
        if not report['passed']:raise RuntimeError('Native frame regression failed; inspect retained evidence')
    finally:
        dev.stop();dev.start();fixture.close()

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
