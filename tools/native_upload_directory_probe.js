import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
const bridge=createNativeBrowserBridge(),browser='browser-use-directory-assignment',runtimeSource=RUNTIME_SOURCE;
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));let checks=0,sequence=0,state,report,lastJob=null,assignmentStarted=null;
const assert=(value,message)=>{checks++;if(!value)throw Error(message);};
const hash=async bytes=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(v=>v.toString(16).padStart(2,'0')).join('');
const call=params=>bridge.call('upload',{browser,navigation:state.navigation,...params});
const read=async script=>{const output=await bridge.call('evaluate',{browser,script});assert(output.ok,'Cannot read the actual directory fixture');return output.value;};
const engine=async expression=>{const output=await bridge.call('automation',{browser,agent:true,script:`(${runtimeSource})().${expression}`});assert(output.ok,'Cannot read the isolated directory runtime');return output.value;};
const isolated=async script=>{const output=await bridge.call('automation',{browser,agent:true,script});assert(output.ok,'Cannot inspect directory observer cleanup');return output.value;};
async function save(value){
  const boot=await fetch('/api/v1/bootstrap'),token=boot.headers.get('x-mdo-write-token');await boot.arrayBuffer();
  const response=await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},body:JSON.stringify({method:'storage.write',context:{extension:'browser-use-qa'},params:{path:'qa/page-observation.json',text:JSON.stringify(value)}})});
  if(!response.ok)throw Error('Cannot save actual directory assignment evidence');
}
async function target(css){
  const page=await engine('observe({})'),found=await engine(`uploadInfo(${JSON.stringify({document:page.document,target:{css}})})`);
  assert(found.ok,'Cannot pin the exact directory fixture '+css);return{document:page.document,ref:found.element.ref};
}
async function idle(){
  for(let i=0;i<150;i++){if(!(await bridge.call('state',{browser})).input_busy)break;await pause(20);}
  assert(!(await bridge.call('state',{browser})).input_busy,'Directory assignment retained its native input slot');
  for(let i=0;i<100;i++){if(await isolated('globalThis.__mdo_native_upload_watch_v1===undefined'))return;await pause(20);}
  assert(false,'Directory event observer survived completion/cancellation');
}
async function stage(files,extra={},name='selected-中文'){
  const entries=[];for(const file of files)entries.push({path:file.path,size:file.data.length,sha256:await hash(file.data)});
  const tree={name,directories:['nested','nested/empty','other','空目录'],files:entries};
  const job=await call({action:'begin',upload_key:'directory-select-'+(++sequence),directory:tree,timeout_ms:60000,...extra});
  for(let file=0;file<files.length;file++)for(let offset=0;offset<files[file].data.length;offset+=65536)
    await call({action:'chunk',ticket:job.ticket,file,offset,base64:btoa(String.fromCharCode(...files[file].data.subarray(offset,offset+65536)))});
  assert((await call({action:'seal',ticket:job.ticket})).sealed,'Directory snapshot did not seal');return{ticket:job.ticket,tree};
}
const assign=(job,pinned,extra={})=>{lastJob=job;assignmentStarted=performance.now();return call({action:'assign',ticket:job.ticket,...pinned,...extra});};
const release=job=>call({action:'release',ticket:job.ticket,confirmed:true});
async function consumed(id,job,minimum){
  let values;for(let i=0;i<200;i++){values=await read(`uploadReads.filter(row=>row.id===${JSON.stringify(id)})`);if(values.length>=minimum)break;await pause(25);}
  const result=values?.at(-1);assert(result&&!result.error,'The page could not read its directory File objects: '+JSON.stringify(result));
  assert(result.files.length===job.tree.files.length,'Directory enumeration omitted or added files');
  for(const file of job.tree.files){const path=job.tree.name+'/'+file.path,actual=result.files.filter(row=>row.path===path);assert(actual.length===1&&actual[0].size===file.size&&actual[0].sha256===file.sha256,'The page read a wrong relative path or byte hash: '+path);}
}
try{
  await bridge.call('create',{browser,url:location.origin+'/__browser_use_upload__.html',visible:true});
  for(let i=0;i<150;i++){state=await bridge.call('state',{browser});if(!state.loading&&await read('fixtureReady===true'))break;await pause(20);}
  assert(!state.loading&&state.file_input_capability,'Directory browser did not become ready');
  const data=Uint8Array.from({length:131075},(_,i)=>i%251),empty=new Uint8Array();
  const files=[{path:'nested/same.txt',data},{path:'other/same.txt',data:new TextEncoder().encode('distinct')},{path:'zero.txt',data:empty},{path:'nested/'+'n'.repeat(240),data:new TextEncoder().encode('long')}];
  const first=await stage(files),folder=await target('#directory');
  await bridge.call('bounds',{browser,visible:false,bounds:{x:0,y:0,width:640,height:480,scale:devicePixelRatio}});
  const hiddenResult=await assign(first,folder);assert(!hiddenResult.dispatched&&hiddenResult.error?.code==='upload_view_hidden','A hidden directory view was dispatched and left to time out');
  await bridge.call('bounds',{browser,visible:true,bounds:{x:0,y:0,width:640,height:480,scale:devicePixelRatio}});
  const regular=await assign(first,await target('#multiple'));assert(!regular.dispatched&&regular.error?.code==='upload_target_invalid','A directory reached a regular input');
  const stale=await assign(first,{...folder,document:'stale-document'});assert(!stale.dispatched&&!stale.accepted,'A stale document received a directory');
  let result=await assign(first,folder);assert(result.accepted&&result.verified&&result.retained&&result.verification==='relative_file_list','Native directory selection failed: '+JSON.stringify(result));
  await consumed('directory',first,1);await idle();
  const events=await read('uploadEvents.filter(row=>row.id==="directory")');assert(events.length===2&&events.every(row=>row.trusted)&&events.map(row=>row.type).join(',')==='input,change','Directory input/change events were missing, untrusted or repeated');
  assert((await assign(first,folder)).verified,'Directory replay lost its saved receipt');
  assert((await read('uploadEvents.filter(row=>row.id==="directory")')).length===2,'Directory replay repeated website input');
  const moved=await assign(first,await target('#directory-hidden'));assert(!moved.dispatched&&moved.error.code==='upload_identity_conflict','A directory ticket moved to another input');await release(first);
  for(const [css,id] of [['#shadow-directory','shadow-directory'],['#framed-directory','framed-directory']]){
    const job=await stage(files),pinned=await target(css);assert((await assign(job,pinned)).verified,'Shadow/same-origin frame directory selection failed: '+css);await consumed(id,job,1);await idle();await release(job);
  }
  const delayed=await stage(files,{timeout_ms:2000}),hidden=await target('#directory-hidden');await read('document.querySelector("#directory-hidden").dataset.mode="delayed"');
  assert((await assign(delayed,hidden)).verified,'Hidden directory selection failed');
  await consumed('directory-hidden',delayed,1);assert((await call({action:'status',ticket:delayed.ticket})).retained,'Expiry deleted directory File bytes before delayed page consumption');
  await call({action:'cancel',ticket:delayed.ticket});assert((await call({action:'status',ticket:delayed.ticket})).retained,'Cancellation deleted already assigned directory bytes');await idle();await release(delayed);
  const changed=await stage(files);await read('document.querySelector("#directory").dataset.mode="clear"');
  result=await assign(changed,await target('#directory'));assert(!result.accepted&&result.dispatched&&result.retained&&result.error.effect_may_have_occurred,'A page-cleared directory was incorrectly successful/replayable');
  await consumed('directory',changed,2);await idle();await release(changed);
  const timed=await stage(files);await read('document.querySelector("#directory").dataset.mode="busy"');
  result=await assign(timed,await target('#directory'),{timeout_ms:250});assert(!result.accepted&&result.dispatched&&result.retained&&result.error.effect_may_have_occurred,'A timed-out directory lost its uncertain page effect');
  await idle();await consumed('directory',timed,3);
  assert((await assign(timed,await target('#directory'))).error.effect_may_have_occurred,'A timed-out directory was sent again');
  assert((await read('uploadEvents.filter(row=>row.id==="directory"&&row.type==="change")')).length===3,'Directory timeout replay duplicated its input');await release(timed);
  const dialog=await stage(files);await read('document.querySelector("#directory").dataset.mode="dialog"');
  result=await assign(dialog,await target('#directory'));assert(result.outcome==='dialog_open'&&result.retained&&result.dispatched,'Directory change dialog did not preserve a resumable effect');
  const dialogs=await bridge.call('dialog',{browser,action:'list'});await bridge.call('dialog',{browser,action:'respond',dialog:dialogs.dialogs[0].id,accept:true});
  await idle();await consumed('directory',dialog,4);assert((await call({action:'status',ticket:dialog.ticket})).verified,'Directory did not finish after dialog dismissal');await release(dialog);
  await read('document.querySelector("#directory").dataset.mode=""');
  // A matching FileList and an untrusted synthetic event are insufficient.
  await read('document.querySelector("#directory").addEventListener("input",function blocked(event){event.stopImmediatePropagation();queueMicrotask(()=>this.dispatchEvent(new Event("input",{bubbles:true})))},{capture:true,once:true})');
  const blocked=await stage(files);result=await assign(blocked,await target('#directory'),{timeout_ms:400});
  assert(!result.accepted&&result.dispatched&&result.retained,'An old matching FileList or untrusted event bypassed the directory completion fence');
  await idle();await consumed('directory',blocked,5);await release(blocked);
  const longRoot=await stage(files,{},'r'.repeat(240));result=await assign(longRoot,await target('#directory'));
  assert(result.verified,'A selected directory root beyond MAX_PATH was unreadable');await consumed('directory',longRoot,6);await idle();await release(longRoot);
  const maximum=await stage(Array.from({length:128},(_,i)=>({path:'f'+i,data:empty})));
  result=await assign(maximum,await target('#directory'));assert(result.verified,'128-file directory assignment was truncated');await consumed('directory',maximum,7);await idle();await release(maximum);
  report={passed:true,checks,backend:'actual-webview2-directory-assignment',native_upload_staging:true,native_directory_file_inputs:true,
    complete_relative_paths_and_hashes:true,same_basename_distinct_paths:true,long_leaf_bytes:240,maximum_files:128,shadow_and_same_origin_frames:true,
    trusted_events_once:true,trusted_completion_fence:true,hidden_view_rejected_before_dispatch:true,long_selected_root_bytes:240,replay_without_dispatch:true,observer_cleanup:true,retained_delayed_reads:true,page_change_uncertain:true,timeout_without_replay:true,dialog_checkpoint:true};
}catch(error){
  report={passed:false,checks,error:String(error.stack||error),assignment_elapsed_ms:assignmentStarted===null?null:Math.round(performance.now()-assignmentStarted)};
  try{
    report.native_state=await bridge.call('state',{browser});
    if(lastJob)report.transfer=await call({action:'status',ticket:lastJob.ticket});
    if(!report.native_state.dialog_open){
      report.fixture=await read('({files:[...document.querySelector("#directory").files].map(f=>({name:f.name,path:f.webkitRelativePath,size:f.size})),events:uploadEvents,reads:uploadReads})');
      report.observer=await isolated('({present:!!globalThis.__mdo_native_upload_watch_v1,seen:globalThis.__mdo_native_upload_watch_v1?.seen})');
    }
  }catch(diagnosticError){report.diagnostic_error=String(diagnosticError.message||diagnosticError);}
}
finally{try{await bridge.call('close',{browser});}catch{}bridge.dispose();}
await save(report);
