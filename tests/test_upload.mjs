import assert from 'node:assert/strict';
import {test} from 'node:test';
import {executeFileUpload} from '../host/upload-dispatch.js';

function fixture(){
  const ticket='u-00000000-0000-0000-0000-000000000000',calls=[],native=[];
  const state={navigation:'1',file_input_capability:true,upload_capability:true,file_clear_capability:true};
  let cancelled=false,dispatched=false,inspections=0;
  const api={async call(method,params){calls.push({method,params});
    if(method==='files.digest')return{size:65539,sha256:'a'.repeat(64),revision:'f-snapshot'};
    if(method==='files.read')return{revision:'f-snapshot',bytes:params.limit,next:params.offset+params.limit,base64:Buffer.alloc(params.limit).toString('base64')};
    throw Error('Unexpected task SDK call');}};
  const view={async execute(command,params){assert.equal(command,'upload');native.push(params);
    if(params.action==='begin')return{ticket};if(params.action==='seal')return{sealed:true};
    if(params.action==='assign')return{accepted:true,verified:true,retained:true,dispatched:true};return{};}};
  const fail=(code,message)=>({handled:true,result:{ok:false,error:{code,message,effect_may_have_occurred:dispatched}}});
  const guard=async()=>cancelled?fail('cancelled','Original operation cancelled'):{state};
  const params={action:'set',document:'doc',target:{css:'#file'},files:[{path:'C:\\task-host\\source.bin'}]};
  const read=async(method,options)=>{if(method==='chooserInfo')return view.execute('chooser',{action:'target',...options,agent:true});inspections++;assert.equal(method,'uploadInfo');const result={ok:true,document:'doc',element:{ref:options.target?.ref||'e1',multiple:true}};return state.pinned?state.pinned(inspections,options,result):result;};
  const run=()=>executeFileUpload({params,view,api,action:{id:'frozen-action'},guard,read,deadline:performance.now()+2000,setDispatched:value=>{dispatched=value;},fail});
  return{ticket,calls,native,state,params,api,view,run,get inspections(){return inspections;},cancel(){cancelled=true;}};
}
test('picker acceptance resolves its actual input before sources and retains its consumed receipt',async()=>{
  const f=fixture(),execute=f.view.execute;delete f.params.target;f.params.chooser='picker';f.state.chooser_upload_capability=true;let present=true;
  f.view.execute=async(command,params)=>{
    if(command==='chooser')return params.action==='list'?{ok:true,choosers:present?[{id:'picker'}]:[]}:{ok:true,element:{ref:'actual-picker-input',multiple:true}};
    if(params.action==='assign'){present=false;await new Promise(resolve=>setTimeout(resolve,55));}
    return execute(command,params);
  };
  const result=(await f.run()).result;assert.equal(result.ok,true);assert.equal(result.verified,true);
  assert.equal(f.native[0].chooser,'picker');assert.equal(f.native[0].document,'doc');assert.equal(f.native[0].ref,'actual-picker-input');assert.equal(f.native.at(-1).ref,'actual-picker-input');
});
test('cancelled or unsupported pickers fail before source reads and cancelled transfers never assign',async()=>{
  const unsupported=fixture();unsupported.params.chooser='picker';delete unsupported.params.target;
  assert.equal((await unsupported.run()).result.error.code,'unsupported_capability');assert.equal(unsupported.calls.length,0);
  const f=fixture(),execute=f.view.execute,call=f.api.call;f.state.chooser_upload_capability=true;f.params.chooser='picker';delete f.params.target;let present=true;
  f.view.execute=async(command,params)=>command==='chooser'?params.action==='list'?{ok:true,choosers:present?[{id:'picker'}]:[]}:{ok:true,element:{ref:'e1',multiple:true}}:execute(command,params);
  f.api.call=async(method,params)=>{const value=await call(method,params);if(method==='files.read')present=false;return value;};
  const result=(await f.run()).result;assert.equal(result.error.code,'chooser_changed');assert.equal(result.error.effect_may_have_occurred,false);
  assert.deepEqual(f.native.map(row=>row.action),['begin','cancel']);
});
test('upload probes capability and exact target before reading any task files',async()=>{
  const f=fixture();f.state.file_input_capability=false;
  assert.equal((await f.run()).result.error.code,'unsupported_capability');assert.equal(f.calls.length,0);assert.equal(f.native.length,0);
  const unsafe=fixture();unsafe.params.files[0].name='../escape';assert.equal((await unsafe.run()).result.error.code,'upload_name_invalid');assert.equal(unsafe.calls.length,0);
});
test('upload streams pinned revision ranges, seals and assigns once without sending source paths',async()=>{
  const f=fixture(),result=(await f.run()).result;
  assert.equal(result.ok,true);assert.equal(result.transfer.retained,true);
  assert.deepEqual(f.calls.filter(x=>x.method==='files.read').map(x=>[x.params.offset,x.params.limit,x.params.revision]),[[0,65536,'f-snapshot'],[65536,3,'f-snapshot']]);
  assert.deepEqual(f.native.map(x=>x.action),['begin','chunk','chunk','seal','assign']);
  assert.equal(f.native.at(-1).ref,'e1');assert(!JSON.stringify(f.native).includes('task-host'));assert(!JSON.stringify(result).includes('base64'));
});

test('frame-local upload identities reach both admission and assignment',async()=>{
  const f=fixture(),chain=[{frame:'f-child',document:'fd-child'},{frame:'f0',document:'fd-root'}];
  f.state.pinned=()=>({ok:true,element:{ref:'r1|qualified',frame:'f-child',multiple:true},native_target:{document:'local-doc',ref:'e7',frame_chain:chain}});
  assert((await f.run()).result.ok);
  for(const row of f.native.filter(row=>['begin','assign'].includes(row.action))){assert.equal(row.document,'local-doc');assert.equal(row.ref,'e7');assert.deepEqual(row.frame_chain,chain);}
  assert(!JSON.stringify(f.native).includes('r1|qualified'));
});

test('qualified targets cannot silently fall back into the legacy root world',async()=>{
  for(const native_target of [undefined,{document:'local-doc',ref:'e7',frame_chain:[]}]){
    const f=fixture();f.state.pinned=()=>({ok:true,element:{ref:'r1|qualified',frame:'f-child'},...(native_target?{native_target}:{})});
    const result=(await f.run()).result;assert.equal(result.error.code,'upload_target_invalid');assert.equal(f.calls.length,0);assert.equal(f.native.length,0);
  }
});

test('changed frame metadata after sealing cancels only its staged ticket',async()=>{
  const f=fixture();f.state.pinned=count=>({ok:true,element:{ref:'r1|qualified',frame:'f-child',multiple:true},
    native_target:{document:'local-doc',ref:'e7',frame_chain:[{frame:'f-child',document:count===1?'fd-original':'fd-new'},{frame:'f0',document:'fd-root'}]}});
  const result=(await f.run()).result;assert.equal(result.error.code,'upload_target_changed');assert(!result.error.effect_may_have_occurred);
  assert(!f.native.some(row=>row.action==='assign'));assert.equal(f.native.at(-1).action,'cancel');
});

test('framed clearing retains its tracked clear operation without reading source files',async()=>{
  const f=fixture();f.params.action='clear';const chain=[{frame:'f-child',document:'fd-child'},{frame:'f0',document:'fd-root'}];
  f.state.pinned=()=>({ok:true,element:{ref:'r1|qualified',frame:'f-child'},native_target:{document:'local-doc',ref:'e7',frame_chain:chain}});
  const execute=f.view.execute;f.view.execute=async(command,params)=>{const result=await execute(command,params);return params.action==='clear'?{accepted:true,verified:true,cleared:true,dispatched:true}:result;};
  assert((await f.run()).result.ok);assert.equal(f.calls.length,0);assert.equal(f.native.length,1);
  assert.equal(f.native[0].action,'clear');assert.equal(f.native[0].upload_key,'frozen-action');assert.deepEqual(f.native[0].frame_chain,chain);
});
test('revision conflict cancels only staging and never assigns a file',async()=>{
  const f=fixture(),call=f.api.call;f.api.call=async(method,params)=>method==='files.read'?{revision:'replacement',bytes:params.limit,next:params.limit,base64:'eA=='}:call(method,params);
  const result=(await f.run()).result;assert.equal(result.error.code,'upload_snapshot_changed');assert.equal(result.error.effect_may_have_occurred,false);
  assert.deepEqual(f.native.map(x=>x.action),['begin','cancel']);
});
test('source failures retain their actual stage, index and bounded cause',async()=>{
  const f=fixture();f.api.call=async()=>{throw Object.assign(new Error('Source file is missing'),{code:'sdk_native_failed'});};
  const result=(await f.run()).result;assert.equal(result.error.code,'sdk_native_failed');assert.equal(result.error.stage,'source_snapshot');
  assert.equal(result.error.source_index,0);assert.equal(result.error.cause.message,'Source file is missing');assert.equal(result.error.effect_may_have_occurred,false);
  assert.equal(f.native.length,0);
});
test('cancellation and document change during a source read prevent native delivery',async()=>{
  for(const boundary of ['cancel','navigate']){
    const f=fixture(),call=f.api.call;f.api.call=async(method,params)=>{const value=await call(method,params);if(method==='files.read'){if(boundary==='cancel')f.cancel();else f.state.navigation='2';}return value;};
    const result=(await f.run()).result;assert.equal(result.error.code,boundary==='cancel'?'cancelled':'stale_document');
    assert(!f.native.some(x=>['chunk','assign'].includes(x.action)));assert.equal(f.native.at(-1).action,'cancel');
  }
});
test('lost native assignment reply preserves the transfer and conservative effect uncertainty',async()=>{
  const f=fixture(),execute=f.view.execute;f.view.execute=async(command,params)=>{if(params.action==='assign')throw Error('reply lost');return execute(command,params);};
  const result=(await f.run()).result;assert.equal(result.error.code,'action_uncertain');assert.equal(result.error.effect_may_have_occurred,true);
  assert.equal(result.transfer.ticket,f.ticket);assert.equal(result.transfer.retained,true);assert.equal(f.native.at(-1).action,'cancel');
});
test('native pre-delivery rejection clears uncertainty and dialog checkpoints retain bytes',async()=>{
  for(const accepted of [false,true]){
    const f=fixture(),execute=f.view.execute;f.view.execute=async(command,params)=>params.action==='assign'?accepted?
      {accepted:true,dispatched:true,retained:true,completed:false,outcome:'dialog_open'}:
      {accepted:false,dispatched:false,error:{code:'upload_target_changed',message:'Fresh reference required'}}:execute(command,params);
    const result=(await f.run()).result;assert.equal(result.ok,accepted);assert.equal(result.transfer.retained,accepted);
    if(accepted)assert.equal(result.outcome,'dialog_open');else assert.equal(result.error.effect_may_have_occurred,false);
  }
});
test('status and confirmed release never read task files',async()=>{
  for(const action of ['status','release']){const f=fixture();f.params.action=action;f.params.ticket=f.ticket;f.params.confirmed=action==='release';
    assert.equal((await f.run()).result.ok,true);assert.equal(f.calls.length,0);assert.equal(f.native.length,1);assert.equal(f.native[0].ticket,f.ticket);}
});
test('a lost release reply reports uncertain cleanup instead of a source-read failure',async()=>{
  const f=fixture();f.params.action='release';f.params.ticket=f.ticket;f.params.confirmed=true;
  f.view.execute=async()=>{throw Error('Release reply lost');};
  const result=(await f.run()).result;assert.equal(result.error.code,'action_uncertain');assert.equal(result.error.stage,'transfer_release');
  assert.equal(result.error.effect_may_have_occurred,true);assert.equal(f.calls.length,0);
});
test('clearing uses the pinned input without hashing or staging task files',async()=>{
  const f=fixture(),execute=f.view.execute,clears=[];f.params.action='clear';delete f.params.files;
  f.view.execute=async(command,params)=>{if(params.action==='clear'){clears.push(params);return{accepted:true,verified:true,cleared:true,dispatched:true,retained:false,ticket:'c-00000000-0000-0000-0000-000000000000'};}return execute(command,params);};
  const result=(await f.run()).result;assert.equal(result.ok,true);assert.equal(result.cleared,true);assert.equal(result.transfer.retained,false);
  assert.equal(f.calls.length,0);assert.equal(f.native.length,0);assert.equal(clears.length,1);assert.equal(clears[0].ref,'e1');assert.equal(clears[0].upload_key,'frozen-action');assert(!('files' in clears[0]));
});
test('unsupported clear fails before page inspection or task file reads',async()=>{
  const f=fixture();f.params.action='clear';delete f.params.files;f.state.file_clear_capability=false;
  const result=(await f.run()).result;assert.equal(result.error.code,'unsupported_capability');assert.equal(f.calls.length,0);assert.equal(f.native.length,0);assert.equal(f.inspections,0);
});
test('a lost clear reply cancels only its exact clear key and preserves uncertainty',async()=>{
  const f=fixture(),execute=f.view.execute;f.params.action='clear';delete f.params.files;
  f.view.execute=async(command,params)=>{if(params.action==='clear')throw Error('Clear reply lost');return execute(command,params);};
  const result=(await f.run()).result;assert.equal(result.error.code,'action_uncertain');assert.equal(result.error.stage,'native_clear');
  assert.equal(result.error.effect_may_have_occurred,true);assert.equal(f.calls.length,0);
  assert.deepEqual(f.native,[{action:'clear_cancel',upload_key:'frozen-action'}]);
});
