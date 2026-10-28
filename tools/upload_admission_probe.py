"""Check failed native admission drains itself while its browser stays open."""
import argparse
import json
import subprocess
import sys
import time

import dev
from upload_cache_probe import inspect_cache,deny_child_directory_creation


SCRIPT=r'''
import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
const bridge=createNativeBrowserBridge(),browser='browser-use-admission-failure';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function sdk(method,params){
  const boot=await fetch('/api/v1/bootstrap'),token=boot.headers.get('x-mdo-write-token');await boot.arrayBuffer();
  const response=await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
    body:JSON.stringify({method,context:{extension:'browser-use-admission-qa'},params})});
  const parsed=await response.json();if(!response.ok)throw Error('Cannot collect native admission evidence');return parsed.data;
}
const save=value=>sdk('storage.write',{path:'qa/upload-admission.json',text:JSON.stringify(value)});
async function acknowledged(phase){
  for(let i=0;i<200;i++){
    const value=await sdk('storage.read',{path:'qa/upload-admission-ack.json'}),ack=JSON.parse(value.text);
    if(ack?.phase===phase)return ack;await pause(50);
  }throw Error('Admission cleanup observer did not acknowledge '+phase);
}
let result;
try{
  await save({phase:'entered'});
  await bridge.call('create',{browser,url:FIXTURE_URL,visible:false},{timeout:10000});
  await save({phase:'created'});
  let state;for(let i=0;i<150;i++){state=await bridge.call('state',{browser});if(!state.loading)break;await pause(20);}
  if(state.loading||!state.upload_capability)throw Error('Native upload fixture did not become ready');
  const call=params=>bridge.call('upload',{browser,navigation:state.navigation,...params});
  const files=[{name:'admission-io-failure.txt',size:0,sha256:'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'}];
  const warm=await call({action:'begin',upload_key:'admission-warmup',files});
  await call({action:'cancel',ticket:warm.ticket});
  await save({phase:'cache-ready'});await acknowledged('fault-active');
  let rejected=false,message='';
  try{await call({action:'begin',upload_key:'failed-idle-admission',timeout_ms:60000,files});}
  catch(error){rejected=true;message=String(error.message||error);}
  if(!rejected||!message.includes('Cannot reserve an upload file directory'))throw Error('The bounded inherited-directory-creation denial did not reach its expected admission phase');
  await save({phase:'failed-admission',rejected,stage:'create-file-directory'});
  // No upload/status/close requests here: another request must not perform the
  // cleanup that the failed admission's own timer is meant to guarantee.
  const ack=await acknowledged('observed');
  const recovered=await call({action:'begin',upload_key:'admission-after-fault',files});
  await call({action:'cancel',ticket:recovered.ticket});
  result={passed:ack.remaining_reservations===0,phase:'completed',before_close:ack,rejected,
    recovered_after_acl_restore:true,
    error:ack.remaining_reservations?'Failed admission retained a reservation until another browser/upload action':undefined};
}catch(error){result={passed:false,phase:'completed',error:String(error.stack||error)};}
finally{try{await bridge.call('close',{browser},{timeout:3000});}catch{}bridge.dispose();}
await save(result);
'''


def main(about_blank=False):
    output=dev.ENV/('upload-admission-blank-result.json' if about_blank else 'upload-admission-result.json')
    output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    scope={'extension':'browser-use-admission-qa'};saved='qa/upload-admission.json';ack='qa/upload-admission-ack.json'
    page=dev.SOURCE/'app/web/__browser_use_upload_admission__.html'
    if SCRIPT.count('FIXTURE_URL')!=1:raise RuntimeError('Admission fixture URL placeholder changed')
    script=SCRIPT.replace('FIXTURE_URL',"'about:blank'" if about_blank else "location.origin+'/__browser_use_page__.html'")
    subprocess.run(['node','--input-type=module','--check'],input=script,text=True,capture_output=True,check=True,cwd=dev.ROOT)
    page.write_text('<!doctype html><meta charset="utf-8"><script type="module">'+script+'</script>',encoding='utf-8')
    (dev.SOURCE/'app/web/__browser_use_page__.html').write_bytes((dev.ROOT/'tests/fixtures/page.html').read_bytes())
    before={p.name:(p.stat().st_size,p.stat().st_mtime_ns) for p in (dev.ENV/'host').glob('crash_*')}
    dev.call('storage.write',{'path':saved,'text':'null'},scope);dev.call('storage.write',{'path':ack,'text':'null'},scope)
    root=dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers';report=None
    try:
        dev.stop();dev.start(window=True,page='/__browser_use_upload_admission__.html')
        end=time.monotonic()+25
        while time.monotonic()<end:
            report=json.loads(dev.call('storage.read',{'path':saved},scope)['text'])
            if report and report.get('phase')=='cache-ready':break
            if report and report.get('phase')=='completed':raise RuntimeError(report.get('error','Unexpected early completion'))
            time.sleep(.1)
        if not report or report.get('phase')!='cache-ready':raise RuntimeError('Native cache was not ready for its bounded I/O fault')
        class UnwindCheck(Exception):pass
        try:
            with deny_child_directory_creation(root):raise UnwindCheck()
        except UnwindCheck:pass
        inspect_cache(root)  # An exception must also restore the original ACL.
        # Constrain only this empty private development cache. The root itself
        # and its guard remain writable; the next GUID cannot create index 0.
        # Always restore the original ACL before independent cleanup inspection.
        with deny_child_directory_creation(root):
            dev.call('storage.write',{'path':ack,'text':json.dumps({'phase':'fault-active'})},scope)
            end=time.monotonic()+15
            while time.monotonic()<end:
                report=json.loads(dev.call('storage.read',{'path':saved},scope)['text'])
                if report and report.get('phase')=='failed-admission':break
                if report and report.get('phase')=='completed':raise RuntimeError(report.get('error','Unexpected early completion'))
                time.sleep(.05)
            if not report or report.get('phase')!='failed-admission':raise RuntimeError('Native admission did not report the expected directory creation failure')
        started=time.monotonic();evidence=inspect_cache(root)
        while evidence['remaining_reservations'] and time.monotonic()-started<2:
            time.sleep(.05);evidence=inspect_cache(root)
        evidence['elapsed_ms']=round((time.monotonic()-started)*1000)
        dev.call('storage.write',{'path':ack,'text':json.dumps({'phase':'observed',**evidence})},scope)
        end=time.monotonic()+10
        while time.monotonic()<end:
            report=json.loads(dev.call('storage.read',{'path':saved},scope)['text'])
            if report and report.get('phase')=='completed':break
            time.sleep(.1)
        if not report or report.get('phase')!='completed':raise RuntimeError('Native admission observer did not finish')
        report['new_native_diagnostics']=[p.name for p in (dev.ENV/'host').glob('crash_*') if before.get(p.name)!=(p.stat().st_size,p.stat().st_mtime_ns)]
        report['after_close']=inspect_cache(root)
        report['fixture']='about:blank' if about_blank else 'loopback-http'
        report['fault']='inherited-directory-create-denial';report['acl_restored']=True
        report['acl_exception_restore']=True
        report['passed']=report['passed'] and not report['new_native_diagnostics'] and not report['after_close']['remaining_reservations']
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        if not report['passed']:raise RuntimeError(report.get('error','Native admission cleanup failed'))
        print('Failed idle upload admission reclaimed its reservation without another upload/status/close request; original ACL restored')
    except BaseException as error:
        if not report or report.get('phase')!='completed':
            output.write_text(json.dumps({'passed':False,'error':str(error),'last_receipt':report},indent=2)+'\n',encoding='utf-8')
        raise
    finally:
        dev.stop();dev.start()


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--about-blank',action='store_true',help='Use the actual empty native document instead of the local HTTP fixture')
    main(parser.parse_args().about_blank)
