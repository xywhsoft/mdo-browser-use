"""Actual mounted browser download tray; same deployment, synthetic files only."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import dev
from download_fixture import DownloadFixture,BODY
from upload_cache_probe import inspect_cache

def main():
    parser=argparse.ArgumentParser();modes=parser.add_mutually_exclusive_group()
    modes.add_argument('--default-workspace',action='store_true');modes.add_argument('--missing-default-workspace',action='store_true');args=parser.parse_args()
    args.default_workspace=args.default_workspace or args.missing_default_workspace
    directory=Path(dev.call('sdk.context',context={'project':'default'})['workspace']) if args.default_workspace else dev.ENV/'qa'/'download-ui'
    if args.default_workspace and directory.resolve()!=(dev.HOME/'workspace').resolve():raise RuntimeError('Default UI gate escaped this QA Home')
    if not args.default_workspace:directory.mkdir(parents=True,exist_ok=True)
    if directory.is_symlink() or getattr(directory,'is_junction',lambda:False)():raise RuntimeError('UI gate root is a link')
    prefix='browser-use-missing-default-' if args.missing_default_workspace else 'browser-use-default-' if args.default_workspace else ''
    files=[directory/(prefix+'ui-saved.bin'),directory/(prefix+'ui-recovered.bin')]
    if any(path.exists() for path in files):raise RuntimeError('Previous download UI evidence exists; inspect before rerunning')
    scope={'extension':'download-ui-qa','device':'local','project':'default','session':'e2900000000000000000000000000001' if args.default_workspace else 'b0a00000000000000000000000000004',
      'workspace':str(directory) if args.default_workspace else '','namespace':'original-private','member':'original-worker'}
    session=dev.call('session.create',{'body':{'project_id':'default','client_session_id':scope['session'],**({} if args.default_workspace else {'workspace_root':str(directory)}),
      'title':'Browser Use download UI legacy QA','permission_profile':'full-access'}},scope)['data']
    if session['workspace_root']!=str(directory):raise RuntimeError('Persistent UI QA task changed; refusing to overwrite it')
    output='qa/download-missing-default-ui.json' if args.missing_default_workspace else 'qa/download-default-ui.json' if args.default_workspace else 'qa/download-ui.json'
    fixture=DownloadFixture().start();report_path=dev.ENV/('missing-default-download-ui-result.json' if args.missing_default_workspace else 'default-download-ui-result.json' if args.default_workspace else 'download-ui-result.json');report_path.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    dev.call('client.hello',{'client':'download-ui-qa'})
    for previous in dev.call('browser.list',{},scope):
        if previous.get('key')=='download-ui-qa' and previous.get('context',{}).get('session')==scope['session']:
            dev.call('browser.close',{'id':previous['id']},scope)
    browser=dev.call('browser.create',{'client':'download-ui-qa','url':fixture.origin+'/auto-download','download_policy':'intercept','key':'download-ui-qa'},scope)
    script=(dev.ROOT/'tools/download_ui_probe.js').read_text(encoding='utf-8')
    for marker,value in {'PROBE_CONTEXT':scope,'PROBE_HTTP_ORIGIN':fixture.origin,'PROBE_DIRECTORY':str(directory),'PROBE_BROWSER':browser['id'],
      'PROBE_DEFAULT_ROOT':args.default_workspace,'PROBE_MISSING_ROOT':args.missing_default_workspace,'PROBE_OUTPUT':output}.items():
        if script.count(marker)!=1:raise RuntimeError('UI probe placeholder changed: '+marker)
        script=script.replace(marker,json.dumps(value))
    subprocess.run(['node','--input-type=module','--check'],input=script,text=True,check=True,capture_output=True,cwd=dev.ROOT)
    page=dev.SOURCE/'app/web/__browser_use_download_ui__.html'
    page.write_text('<!doctype html><meta charset="utf-8"><link rel="stylesheet" href="/css/workbench.css">'
      '<style>:root{--surface:#171717;--border:#333;--ink:#eee;--muted:#aaa;--focus:#62adff;}body{margin:0;background:#171717;color:#eee;font-family:system-ui}#browser{position:absolute;inset:45px 0 0;display:flex;flex-direction:column;overflow:hidden}#status{margin:8px;font-size:14px}</style>'
      '<h1 id="status">Browser download UI regression</h1><section id="browser" class="wb-view"></section><script type="module">'+script+'</script>',encoding='utf-8')
    held=dev.HOME/'workspace.browser-use-ui-held';evidence=dev.ENV/'qa/download-ui/missing-root-failed'
    if args.missing_default_workspace and (held.exists() or held.is_symlink() or evidence.exists() or evidence.is_symlink()):raise RuntimeError('Previous missing-root evidence exists; preserve it')
    dev.call('storage.write',{'path':output,'text':'null'},scope);dev.stop();passed=False;root_identity=None
    try:
        # Upgrade fixture: remove only this owned tab's root while stopped.
        path=dev.HOME/'data/workbench.json';before=path.read_bytes();state=json.loads(before)
        row=next(row for row in state['browsers'] if row['id']==browser['id'])
        if row['owner']!=scope['extension'] or row['context']['session']!=scope['session'] or path.read_bytes()!=before:
            raise RuntimeError('Owned legacy browser fixture changed before preparation')
        if not args.default_workspace:row['context'].pop('workspace',None)
        row['context'].pop('workspace_snapshot',None)
        temporary=path.with_suffix('.download-ui.tmp');temporary.write_text(json.dumps(state,ensure_ascii=False)+'\n',encoding='utf-8');temporary.replace(path)
        if args.missing_default_workspace:
            if directory.resolve()!=(dev.HOME/'workspace').resolve() or held.resolve().parent!=dev.HOME.resolve():raise RuntimeError('Missing-root move escaped the QA Home')
            info=os.stat(directory,follow_symlinks=False);directory.rename(held);root_identity=(info.st_dev,info.st_ino)
        dev.start(window=True,page='/__browser_use_download_ui__.html');end=time.monotonic()+90;report=None
        while time.monotonic()<end:
            report=json.loads(dev.call('storage.read',{'path':output},scope)['text'])
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
        passed=bool(report['passed']);report.update(passed=False,ui_checks_passed=passed,download_requests=dict(fixture.counts))
        report_path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        if not passed:raise RuntimeError('Actual download UI regression failed; inspect retained report and files')
    finally:
        try:
            status,_,reply=dev.request('POST','/api/v1/application/call',{'method':'browser.close','params':{'id':browser['id']},'context':scope})
            if status not in (200,404):raise RuntimeError('UI fixture browser cleanup was not confirmed')
        finally:
            dev.stop()
            try:
                if passed:
                    for path,expected in zip(files,(BODY,b'recovery-ui-fixture')):
                        if path.is_symlink() or path.parent.resolve()!=directory.resolve() or path.read_bytes()!=expected:
                            raise RuntimeError('Unexpected UI output preserved for inspection')
                    for path in files:path.unlink()
                if root_identity is not None:
                    if directory.is_symlink() or getattr(directory,'is_junction',lambda:False)():raise RuntimeError('Unexpected replacement root link preserved')
                    if directory.exists():
                        if passed:directory.rmdir()  # Empty only; preserve unknown contents.
                        else:
                            if not evidence.resolve().is_relative_to(dev.ENV.resolve()) or evidence.resolve().parent!=(dev.ENV/'qa/download-ui').resolve() or evidence.exists():raise RuntimeError('Failure evidence target changed')
                            evidence.parent.mkdir(parents=True,exist_ok=True);directory.rename(evidence)
                    held.rename(directory);info=os.stat(directory,follow_symlinks=False)
                    if root_identity!=(info.st_dev,info.st_ino):raise RuntimeError('Original workspace identity was not restored')
                    if passed:report['original_default_root_restored']=True
            finally:dev.start();fixture.close()
    report.update(passed=passed,headless_restored=True)
    report_path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,AssertionError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
