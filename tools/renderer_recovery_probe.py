"""Fail only one newly created owned QA renderer; verify manual reload recovery.

Same deployment/Home, loopback page, no models or user browser processes.
Require exactly one new renderer and recheck its creation/ancestry before stop.
"""
import json
from pathlib import Path
import subprocess
import sys
import time

import dev

SCOPE={'extension':'browser-use-qa'}
STATE='qa/renderer-recovery-state.json'
CONTROL='qa/renderer-recovery-control.json'
PAGE='/__browser_use_renderer_recovery__.html'
MARKER='<!-- mdo-browser-use-owned-renderer-recovery -->'
SCRIPT=r'''
import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
const bridge=createNativeBrowserBridge(),run=RUN_ID,browser='browser-use-renderer-recovery';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const scope={extension:'browser-use-qa'};let checks=0,report,opened=false;
const check=(value,code)=>{if(!value)throw Error(code);checks++;};
async function call(method,params){
  const bootstrap=await fetch('/api/v1/bootstrap'),token=bootstrap.headers.get('x-mdo-write-token');await bootstrap.arrayBuffer();
  const reply=await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
    body:JSON.stringify({method,params,context:scope})});const value=await reply.json();
  if(!reply.ok)throw Error('QA storage request failed');return value.data;
}
async function publish(value){await call('storage.write',{path:'qa/renderer-recovery-state.json',text:JSON.stringify({run,...value})});}
async function control(){try{return JSON.parse((await call('storage.read',{path:'qa/renderer-recovery-control.json'})).text);}catch{return{};}}
async function wait(test,timeout=10000){const end=performance.now()+timeout;while(performance.now()<end){if(await test())return;await pause(30);}throw Error('renderer_fixture_wait_timed_out');}
try{
  await publish({phase:'baseline'});await wait(async()=>{const c=await control();return c.run===run&&c.start===true;});
  await bridge.call('create',{browser,url:location.origin+'/__browser_use_renderer_fixture__.html',visible:false});opened=true;
  await wait(async()=>{const s=await bridge.call('state',{browser});return !s.loading&&!s.creating;});
  const initial=await bridge.call('evaluate',{browser,script:"document.body.dataset.fixture==='renderer-recovery'"});
  check(initial.ok&&initial.value===true,'renderer_fixture_not_loaded');
  await publish({phase:'ready_to_fail',checks});
  await wait(async()=>Boolean((await bridge.call('state',{browser})).process_failed));
  const failed=await bridge.call('state',{browser});check(failed.process_failed&&!failed.loading,'renderer_failure_not_reported');
  await bridge.call('reload',{browser});
  await wait(async()=>{const s=await bridge.call('state',{browser});if(s.loading)return false;
    try{const p=await bridge.call('evaluate',{browser,script:"document.body.dataset.fixture==='renderer-recovery'"});return p.ok&&p.value===true;}catch{return false;}});
  const restored=await bridge.call('state',{browser});
  check(!restored.process_failed,'renderer_failure_flag_survived_successful_reload');
  check(!restored.error,'renderer_failure_error_survived_successful_reload');
  const isolated=await bridge.call('automation',{browser,agent:true,script:'6*7'});
  check(isolated.ok&&isolated.value===42,'renderer_isolated_execution_not_recovered');
  report={passed:true,checks,manual_reload_recovered:true,isolated_execution_recovered:true};
}catch(error){report={passed:false,checks,error:String(error.message).slice(0,240)};}
finally{
  if(opened)try{await bridge.call('close',{browser});}catch{report={...report,passed:false,error:'renderer_fixture_close_failed'};}
  await publish({phase:'complete',...report});
}
'''


def powershell(source):
    completed=subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',source],cwd=dev.ROOT,
        check=True,capture_output=True,text=True,timeout=15)
    return completed.stdout.strip()


def renderers(root):
    # Output only process identity. Command lines are inspected in memory and
    # never returned, persisted or forwarded to a model/provider.
    raw=powershell(f'$ownedRoot={int(root)};'+r'''
$rows=Get-CimInstance Win32_Process;$byId=@{};foreach($p in $rows){$byId[[int]$p.ProcessId]=$p}
$found=@(foreach($p in $rows){
 if($p.Name -ine 'msedgewebview2.exe' -or $p.CommandLine -notmatch '--type=renderer(?:\s|$)'){continue}
 $at=[int]$p.ParentProcessId;$visited=@{};while($at -ne $ownedRoot -and $byId.ContainsKey($at) -and -not $visited.ContainsKey($at)){
  $visited[$at]=$true;$at=[int]$byId[$at].ParentProcessId
 }
 if($at -eq $ownedRoot){[pscustomobject]@{pid=[int]$p.ProcessId;parent=[int]$p.ParentProcessId;created=$p.CreationDate.ToUniversalTime().Ticks.ToString()}}
});ConvertTo-Json -InputObject @($found) -Compress
''')
    return json.loads(raw)


def stop_renderer(root,record):
    pid=int(record['pid']);created=record['created']
    if not created.isdigit():raise RuntimeError('Invalid owned renderer creation identity')
    if record not in renderers(root):raise RuntimeError('Owned renderer changed before failure injection')
    source=f'$ownedRoot={int(root)};$ownedPid={pid};$ownedParent={int(record["parent"])};$ownedCreated="{created}";'+r'''
$rows=Get-CimInstance Win32_Process;$byId=@{};foreach($p in $rows){$byId[[int]$p.ProcessId]=$p};$p=$byId[$ownedPid]
if(!$p -or $p.Name -ine 'msedgewebview2.exe' -or $p.CommandLine -notmatch '--type=renderer(?:\s|$)' -or
 [int]$p.ParentProcessId -ne $ownedParent -or $p.CreationDate.ToUniversalTime().Ticks.ToString() -ne $ownedCreated){throw 'Renderer identity changed'}
$at=$ownedParent;$visited=@{};while($at -ne $ownedRoot -and $byId.ContainsKey($at) -and -not $visited.ContainsKey($at)){
 $visited[$at]=$true;$at=[int]$byId[$at].ParentProcessId
};if($at -ne $ownedRoot){throw 'Renderer no longer belongs to QA host'}
Stop-Process -Id $ownedPid -ErrorAction Stop
'''
    powershell(source)


def main():
    output=dev.ENV/'renderer-recovery-result.json';run=str(time.time_ns());killed=False;result=None
    output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    web=dev.SOURCE/'app/web';page=web/PAGE[1:];fixture=web/'__browser_use_renderer_fixture__.html'
    contents={page:MARKER+'<meta charset="utf-8"><script type="module">'+SCRIPT.replace('RUN_ID',json.dumps(run))+'</script>',
        fixture:MARKER+'<body data-fixture="renderer-recovery">Synthetic renderer recovery</body>'}
    for path in contents:
        if path.exists() and not path.read_text(encoding='utf-8').startswith(MARKER):raise RuntimeError('Unrecognized QA web file preserved')
    try:
        dev.stop()
        for path,text in contents.items():path.write_text(text,encoding='utf-8')
        dev.start(window=True,page=PAGE);root=dev.state()['pid'];end=time.monotonic()+60;baseline=None
        while time.monotonic()<end:
            try:state=json.loads(dev.call('storage.read',{'path':STATE},SCOPE)['text'])
            except (OSError,RuntimeError,ValueError,KeyError):time.sleep(.1);continue
            if state.get('run')!=run:time.sleep(.1);continue
            if state.get('phase')=='complete':result=state;break
            if state.get('phase')=='baseline' and baseline is None:
                baseline=renderers(root)
                dev.call('storage.write',{'path':CONTROL,'text':json.dumps({'run':run,'start':True})},SCOPE)
            if state.get('phase')=='ready_to_fail' and not killed:
                candidates=[row for row in renderers(root) if row not in baseline]
                if len(candidates)!=1:raise RuntimeError(f'Require exactly one new owned renderer; found {len(candidates)}; no process stopped')
                stop_renderer(root,candidates[0]);killed=True
            time.sleep(.1)
        if not result:raise RuntimeError('Renderer recovery returned no terminal receipt')
        result.pop('run',None);result.update(owned_renderer_stopped=killed,model_calls=0,external_sites=0,desktop_pixels_verified=False)
        output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
        if not result.get('passed'):raise RuntimeError(result.get('error','Renderer recovery failed'))
    except Exception as error:
        if result is None:output.write_text(json.dumps({'passed':False,'error':str(error)[:240],'owned_renderer_stopped':killed})+'\n',encoding='utf-8')
        raise
    finally:dev.stop();dev.start()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
