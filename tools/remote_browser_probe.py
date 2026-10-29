"""Production remote bridge -> actual headed WebView2 in the one deployment."""
import hashlib
import json
import subprocess
import sys

import dev
from download_fixture import DownloadFixture, BODY


def main():
    root=dev.ENV/'qa/remote-bridge';root.mkdir(parents=True,exist_ok=True)
    upload=root/'upload.bin';destination=root/'saved-download.bin'
    if destination.exists():raise RuntimeError('Existing remote save fixture preserved; inspect it before this gate')
    if upload.exists() and upload.read_bytes()!=BODY:raise RuntimeError('Unknown remote upload fixture preserved')
    pages=[]
    for source,name in (('page.html','__browser_use_remote__.html'),('upload.html','__browser_use_remote_upload__.html')):
        data=(dev.ROOT/'tests/fixtures'/source).read_bytes();target=dev.SOURCE/'app/web'/name
        if target.exists() and target.read_bytes()!=data:raise RuntimeError('Unrelated QA HTML preserved: '+name)
        pages.append((target,data))
    fixture=None;report=dev.ENV/'remote-browser-result.json';took_deployment=False
    try:
        fixture=DownloadFixture().start()
        dev.stop();took_deployment=True
        for target,data in pages:target.write_bytes(data)
        upload.write_bytes(BODY)
        dev.start(window=True)
        node=subprocess.Popen(['node','tools/remote_browser_probe.mjs',fixture.origin],cwd=dev.ROOT)
        # The command/tool observation may yield while this exact handle remains
        # live. Do not kill or restart it merely because observation timed out.
        code=node.wait()
        result=json.loads(report.read_text(encoding='utf-8'))
        if code or not result.get('passed'):raise RuntimeError('Remote native workflow failed; inspect its bounded report')
        if fixture.counts.get('/post')!=1:raise RuntimeError('Original POST download was repeated or never delivered')
        if not destination.is_file() or destination.read_bytes()!=BODY:raise RuntimeError('Remote saved bytes differ from the retained response')
        result.update(post_requests=1,exact_saved_bytes=len(BODY),exact_saved_sha256=hashlib.sha256(BODY).hexdigest())
        report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    finally:
        # Keep changed/unknown files as evidence. Delete only the fixed owned
        # successful synthetic download whose full bytes match the fixture.
        if destination.is_file() and destination.read_bytes()==BODY:destination.unlink()
        if fixture:fixture.close()
        if took_deployment:
            dev.stop();dev.start()
            if report.is_file():
                result=json.loads(report.read_text(encoding='utf-8'));result['headless_restored']=True
                report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'passed':True,'cases':len(result['cases']),'post_requests':result['post_requests'],'headless_restored':True}))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError) as error:print(str(error),file=sys.stderr);raise SystemExit(1)
