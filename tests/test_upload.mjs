import assert from 'node:assert/strict';
import {test} from 'node:test';
import {executeFileUpload} from '../host/upload-dispatch.js';

function fixture(){
  const ticket='u-00000000-0000-0000-0000-000000000000',calls=[],native=[];
  const state={navigation:'1',file_input_capability:true,upload_capability:true};
  let cancelled=false,dispatched=false;
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
  const read=async(method,options)=>{assert.equal(method,'uploadInfo');return{ok:true,document:'doc',element:{ref:'e1',multiple:true}};};
  const run=()=>executeFileUpload({params,view,api,action:{id:'frozen-action'},guard,read,deadline:performance.now()+2000,setDispatched:value=>{dispatched=value;},fail});
  return{ticket,calls,native,state,params,api,view,run,cancel(){cancelled=true;}};
}
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
