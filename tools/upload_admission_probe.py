"""Check failed native admission drains itself while its browser stays open."""
import argparse
import json
import subprocess
import sys
import time

import dev
from upload_cache_probe import inspect_cache


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
let result;
try{
  await save({phase:'entered'});
  await bridge.call('create',{browser,url:FIXTURE_URL,visible:false},{timeout:10000});
  await save({phase:'created'});
  let state;for(let i=0;i<150;i++){state=await bridge.call('state',{browser});if(!state.loading)break;await pause(20);}
  if(state.loading||!state.upload_capability)throw Error('Native upload fixture did not become ready');
  let rejected=false,message='';
  try{await bridge.call('upload',{browser,navigation:state.navigation,action:'begin',upload_key:'failed-first-admission',
    timeout_ms:60000,files:[{name:'n'.repeat(240),size:0,sha256:'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'}]});}
  catch(error){rejected=true;message=String(error.message||error);}
  if(!rejected||!message.includes('Cannot create a regular private upload file'))throw Error('The legacy Win32-path failure seam changed; use an explicit admission-I/O failure seam');
  await save({phase:'failed-admission',rejected,stage:'create-file'});
  // No upload/status/close requests here: another request must not perform the
  // cleanup that the failed admission's own timer is meant to guarantee.
  let ack=null;for(let i=0;i<150;i++){
    const value=await sdk('storage.read',{path:'qa/upload-admission-ack.json'});ack=JSON.parse(value.text);
    if(ack)break;await pause(50);
  }
  if(!ack)throw Error('Admission cleanup observer did not acknowledge');
  result={passed:ack.remaining_reservations===0,phase:'completed',before_close:ack,rejected,
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
            if report and report.get('phase')=='failed-admission':break
            if report and report.get('phase')=='completed':raise RuntimeError(report.get('error','Unexpected early completion'))
            time.sleep(.1)
        if not report or report.get('phase')!='failed-admission':raise RuntimeError('Native admission did not return its failure receipt')
        started=time.monotonic();evidence=inspect_cache(root)
        while evidence['remaining_reservations'] and time.monotonic()-started<2:
            time.sleep(.05);evidence=inspect_cache(root)
        evidence['elapsed_ms']=round((time.monotonic()-started)*1000)
        dev.call('storage.write',{'path':ack,'text':json.dumps(evidence)},scope)
        end=time.monotonic()+10
        while time.monotonic()<end:
            report=json.loads(dev.call('storage.read',{'path':saved},scope)['text'])
            if report and report.get('phase')=='completed':break
            time.sleep(.1)
        if not report or report.get('phase')!='completed':raise RuntimeError('Native admission observer did not finish')
        report['new_native_diagnostics']=[p.name for p in (dev.ENV/'host').glob('crash_*') if before.get(p.name)!=(p.stat().st_size,p.stat().st_mtime_ns)]
        report['after_close']=inspect_cache(root)
        report['fixture']='about:blank' if about_blank else 'loopback-http'
        report['passed']=report['passed'] and not report['new_native_diagnostics'] and not report['after_close']['remaining_reservations']
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        if not report['passed']:raise RuntimeError(report.get('error','Native admission cleanup failed'))
        print('Failed first upload admission reclaimed its reservation without another upload/status/close request')
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
