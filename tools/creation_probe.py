"""Measure cancel/recreate in the same real native profile without replaying timeouts."""
import json
import sys

import dev
import native_probe

SCRIPT=r'''
import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
const bridge=createNativeBrowserBridge(),browser='browser-use-creation-race';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms)),cases=[];let phase='start',report;
async function observeCreation(){
  const observations=[],started=performance.now();
  do{
    try{
      const state=await bridge.call('state',{browser},{timeout:2000});
      observations.push({elapsed_ms:Math.round(performance.now()-started),ready:true,loading:state.loading,navigation:state.navigation,error:state.error});
      if(!state.loading)break;
    }catch(error){observations.push({elapsed_ms:Math.round(performance.now()-started),ready:false,error:error.message});}
    await pause(500);
  }while(performance.now()-started<15000);
  return observations;
}
try{
  for(let cycle=0;cycle<8;cycle++){
    phase='create-old';const started=performance.now();let oldReply;
    const old=bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html?old='+cycle,visible:false})
      .then(value=>{oldReply={state:'created',elapsed_ms:Math.round(performance.now()-started)}},
        error=>{oldReply={state:'cancelled',elapsed_ms:Math.round(performance.now()-started),message:error.message}});
    phase='close-old';await bridge.call('close',{browser});
    const closed=performance.now(),row={cycle,visible:cycle%2===1,old_settled_at_close:!!oldReply};
    cases.push(row);phase='create-replacement';const replacement=performance.now();
    await bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html?replacement='+cycle,visible:row.visible});
    row.replacement_create_ms=Math.round(performance.now()-replacement);
    phase='old-receipt';await old;row.old_reply=oldReply;row.old_reply_after_close_ms=Math.round(performance.now()-closed);
    if(oldReply.state!=='cancelled'||!oldReply.message.includes('cancelled'))throw Error('The closed create did not receive an explicit native cancellation receipt');
    phase='replacement-ready';let state;
    do{state=await bridge.call('state',{browser});if(!state.loading)break;await pause(25);}while(performance.now()-replacement<10000);
    row.replacement_ready_ms=Math.round(performance.now()-replacement);row.navigation=state.navigation;
    row.url_matches=state.url.endsWith('?replacement='+cycle);row.loading=state.loading;
    if(!row.url_matches||state.loading||row.replacement_ready_ms>10000)throw Error('The replacement exceeded the existing default readiness bound');
    phase='close-replacement';await bridge.call('close',{browser});
  }
  phase='deliberate-missed-reply';let timedOut=false;
  try{await bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html?deliberate-missed-reply',visible:false},{timeout:1});}
  catch(error){if(!error.message.includes('超时'))throw error;timedOut=true;}
  if(!timedOut)throw Error('The deliberate one-millisecond fixture did not lose its reply; keep this check unverified');
  const observations=await observeCreation(),last=observations.at(-1),settled=await bridge.call('state',{browser});
  if(!last?.ready||last.loading||!settled.url.endsWith('?deliberate-missed-reply'))throw Error('The same native identity did not become observable after its missed reply');
  report={passed:true,backend:'actual-webview2-cancel-recreate',cases,loops:cases.length,
    deliberate_missed_reply:{deadline_ms:1,timeout_observed:true,same_identity_ready:true,no_create_replay:true,observations}};
}catch(error){
  report={passed:false,backend:'actual-webview2-cancel-recreate',phase,cases,error:String(error.stack||error),post_timeout_observations:[]};
  // Observe the same identity only. A late completion never converts failure
  // into success, and no new create/navigation request is sent after timeout.
  report.post_timeout_observations=await observeCreation();
}finally{try{await bridge.call('close',{browser});}catch{}bridge.dispose();}
const boot=await fetch('/api/v1/bootstrap'),token=boot.headers.get('x-mdo-write-token');await boot.arrayBuffer();
const saved=await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
  body:JSON.stringify({method:'storage.write',context:{extension:'browser-use-qa'},params:{path:'qa/page-observation.json',text:JSON.stringify(report)}})});
if(!saved.ok)throw Error('Cannot retain the actual native creation receipt');
'''


def main():
    native_probe.SCRIPT=SCRIPT
    try:return native_probe.main(capture_evidence=False)
    finally:
        report=json.loads((dev.ENV/'page-observation-result.json').read_text(encoding='utf-8'))
        (dev.ENV/'native-creation-result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':sys.exit(main())
