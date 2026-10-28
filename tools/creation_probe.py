"""Measure cancel/recreate in the same real native profile without replaying timeouts."""
import json
import sys

import dev
import native_probe

SCRIPT=r'''
import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
import {executeBrowserAutomation} from '/js/features/workbench/automation-dispatch.js';
const bridge=createNativeBrowserBridge(),browser='browser-use-creation-race';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms)),cases=[],cancellations=[];let phase='start',report,sharedReadinessEvidence;
const nativeReply=event=>{try{const value=typeof event.data==='string'?JSON.parse(event.data):event.data;
  if(value.channel==='mdo-browser'&&value.error?.includes('creation was cancelled'))cancellations.push(value.id);}catch{}};
chrome.webview.addEventListener('message',nativeReply);
let maximumPending=0;
async function capacity(){
  const value=(await bridge.call('capabilities')).creation_capacity;
  if(value?.limit!==16||!Number.isInteger(value.pending)||value.pending<0||value.pending>value.limit)throw Error('Invalid native creation capacity receipt');
  maximumPending=Math.max(maximumPending,value.pending);return value;
}
async function observeCreation(){
  const observations=[],started=performance.now();
  do{
    try{
      const state=await bridge.call('state',{browser},{timeout:2000});
      observations.push({elapsed_ms:Math.round(performance.now()-started),ready:state.creating!==true,creating:state.creating,
        creation:state.creation,loading:state.loading,navigation:state.navigation,error:state.error});
      if(!state.loading)break;
    }catch(error){observations.push({elapsed_ms:Math.round(performance.now()-started),ready:false,error:error.message});}
    await pause(500);
  }while(performance.now()-started<15000);
  return observations;
}
try{
  await capacity();
  for(let cycle=0;cycle<8;cycle++){
    phase='create-old';const started=performance.now();let oldReply,oldReceived;
    const old=bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html?old='+cycle,visible:false})
      .then(value=>{oldReceived=performance.now();oldReply={state:'created',elapsed_ms:Math.round(oldReceived-started)}},
        error=>{oldReceived=performance.now();oldReply={state:'cancelled',elapsed_ms:Math.round(oldReceived-started),message:error.message}});
    const preClose=await bridge.call('state',{browser});
    if(typeof preClose.creating!=='boolean'||!['environment','controller','interception','ready'].includes(preClose.creation?.phase)||
       !Number.isSafeInteger(preClose.creation.elapsed_ms)||preClose.creation.elapsed_ms<0)throw Error('Native creation phase is not observable');
    await capacity();
    phase='close-old';await bridge.call('close',{browser});
    const closed=performance.now(),row={cycle,visible:cycle%2===1,old_settled_at_close:!!oldReply,
      pre_close_creation:{creating:preClose.creating,...preClose.creation}};
    cases.push(row);
    if(!row.old_settled_at_close)throw Error('Closing creation still waits for its provider callback');
    row.pending_after_close=(await capacity()).pending;
    phase='create-replacement';const replacement=performance.now();
    await bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html?replacement='+cycle,visible:row.visible});
    row.replacement_create_ms=Math.round(performance.now()-replacement);
    phase='old-receipt';await old;row.old_reply=oldReply;row.old_reply_after_close_ms=Math.round(oldReceived-closed);
    if(oldReply.state!=='cancelled'||!oldReply.message.includes('cancelled'))throw Error('The closed create did not receive an explicit native cancellation receipt');
    phase='replacement-ready';let state;
    do{state=await bridge.call('state',{browser});if(!state.loading)break;await pause(25);}while(performance.now()-replacement<10000);
    row.replacement_ready_ms=Math.round(performance.now()-replacement);row.navigation=state.navigation;
    row.url_matches=state.url.endsWith('?replacement='+cycle);row.loading=state.loading;
    if(!row.url_matches||state.loading||row.replacement_ready_ms>10000)throw Error('The replacement exceeded the existing default readiness bound');
    phase='close-replacement';await bridge.call('close',{browser});
  }
  phase='shared-dispatch-readiness';let creatingReads=0,sharedReads=0,sharedPageReads=0;
  const sharedCreation=bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html?shared-creation-wait',visible:false})
    .then(value=>({value}),error=>({error:error.message}));
  const sharedObservation=await executeBrowserAutomation({command:'observe',params:{timeout_ms:10000},browser,
    action:{id:'qa-native-creation-readiness'},api:{async call(method){if(method!=='ui.result')throw Error('Unexpected readiness service');return{state:'pending'};}},
    view:{async execute(command,params={}){
      if(!['state','automation'].includes(command)&&!(['downloads','chooser'].includes(command)&&params.action==='list'))
        throw Error('Shared creation wait attempted a native mutation');
      const result=await bridge.call(command,{browser,...params});
      if(command==='state'){sharedReads++;if(result.creating)creatingReads++;}
      else if(command==='automation')sharedPageReads++;
      return result;
    }}});
  const sharedCreated=await sharedCreation;
  sharedReadinessEvidence={creating_reads:creatingReads,state_reads:sharedReads,page_reads:sharedPageReads,
    create_error:sharedCreated.error,observation:sharedObservation.result,
    dispatcher_has_creation_wait:executeBrowserAutomation.toString().includes('creating!==true')};
  if(sharedCreated.error||!sharedObservation.result?.ok||creatingReads<1||sharedPageReads<1)throw Error('The shared dispatcher did not wait for the original native creation');
  const sharedReadiness={creating_state_reads:creatingReads,state_reads:sharedReads,page_reads:sharedPageReads,no_create_replay:true};
  await bridge.call('close',{browser});
  phase='popup-create-cancel';const opener='browser-use-create-opener',child='browser-use-create-popup';
  await bridge.call('create',{browser:opener,url:location.origin+'/__browser_use_page__.html',visible:true});
  let openerState;const readyUntil=performance.now()+10000;
  do{openerState=await bridge.call('state',{browser:opener});if(!openerState.loading)break;await pause(25);}while(performance.now()<readyUntil);
  if(openerState.loading)throw Error('The popup opener did not become ready');
  const popupInput=bridge.call('evaluate',{browser:opener,script:"(()=>{window.open('/__browser_use_popup__.html?cancel-create','');return true})()"})
    .then(value=>({value}),error=>({error:error.message}));
  let ticket;const popupUntil=performance.now()+5000;
  do{ticket=(await bridge.call('popups',{browser:opener,action:'list'})).popups[0];if(ticket)break;await pause(20);}while(performance.now()<popupUntil);
  if(!ticket)throw Error('No original popup ticket was retained');
  let popupReply;const popupCreation=bridge.call('create',{browser:child,popup:ticket.id,opener,visible:false})
    .then(value=>{popupReply={state:'created'}},error=>{popupReply={state:'cancelled',message:error.message}});
  await bridge.call('close',{browser:child});
  const popupCancellation={settled_at_close:!!popupReply,opener_unblocked:!(await bridge.call('state',{browser:opener})).popup_open};
  if(!popupCancellation.settled_at_close||popupReply.state!=='cancelled'||!popupReply.message.includes('cancelled')||!popupCancellation.opener_unblocked)
    throw Error('Closing popup creation did not settle its original request and deferral immediately');
  await popupCreation;await popupInput;await bridge.call('close',{browser:opener});
  phase='deliberate-missed-reply';let timedOut=false;
  try{await bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html?deliberate-missed-reply',visible:false},{timeout:1});}
  catch(error){if(!error.message.includes('超时'))throw error;timedOut=true;}
  if(!timedOut)throw Error('The deliberate one-millisecond fixture did not lose its reply; keep this check unverified');
  const observations=await observeCreation(),last=observations.at(-1),settled=await bridge.call('state',{browser});
  if(!last?.ready||last.loading||!settled.url.endsWith('?deliberate-missed-reply'))throw Error('The same native identity did not become observable after its missed reply');
  if(!observations.some(row=>row.creating===true))throw Error('The deliberate missed reply did not exercise readable in-flight creation');
  const drainUntil=performance.now()+5000;let finalCapacity;
  do{finalCapacity=await capacity();if(!finalCapacity.pending)break;await pause(50);}while(performance.now()<drainUntil);
  if(finalCapacity.pending)throw Error('Original provider creation reservations did not drain after terminal callbacks');
  if(cancellations.length!==9||new Set(cancellations).size!==9)throw Error('A creation cancellation was duplicated or missing in native replies');
  report={passed:true,backend:'actual-webview2-cancel-recreate',cases,loops:cases.length,popup_cancellation:popupCancellation,
    observed_unique_cancellations:cancellations.length,
    shared_readiness:sharedReadiness,
    creation_capacity:{limit:finalCapacity.limit,maximum_pending_observed:maximumPending,final_pending:finalCapacity.pending},
    deliberate_missed_reply:{deadline_ms:1,timeout_observed:true,same_identity_ready:true,no_create_replay:true,observations}};
}catch(error){
  report={passed:false,backend:'actual-webview2-cancel-recreate',phase,cases,error:String(error.stack||error),shared_readiness_evidence:sharedReadinessEvidence,post_timeout_observations:[]};
  // Observe the same identity only. A late completion never converts failure
  // into success, and no new create/navigation request is sent after timeout.
  report.post_timeout_observations=await observeCreation();
}finally{for(const current of [browser,'browser-use-create-popup','browser-use-create-opener'])try{await bridge.call('close',{browser:current});}catch{}
  chrome.webview.removeEventListener('message',nativeReply);bridge.dispose();}
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
