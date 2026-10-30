import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';

const host=new URL('../host/',import.meta.url);
const source=(await readFile(new URL('automation-dispatch.js',host),'utf8'))
  .replace('import { browserRuntime } from "./automation-runtime.js";','function browserRuntime() {}')
  .replace(/from (["'])\.\/([^"']+)\1/g,(_,quote,path)=>`from ${JSON.stringify(new URL(path,host).href)}`);
const {executeBrowserAutomation:dispatch}=await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const reasons=['world_events_unavailable','world_event_routing_unavailable','world_event_metadata_unavailable',
  'world_event_limit','world_context_metadata_invalid','world_context_metadata_rebound','world_unique_context_unsupported',
  'world_unique_context_unconfirmed','world_runtime_unavailable','world_identity_limit'];
function fixture(reason=''){
  const state={loading:false,automation:true,navigation:1,automation_unique_world:false,automation_world_error:reason,
    download_capability:true,downloads_armed:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,
    file_input_capability:true,upload_capability:true,capture_capability:true,frame_target_keyboard:true};
  const calls=[];let receipt='pending';
  const view={async execute(command,params){
    calls.push({command,params});if(command==='state')return{...state};
    if(command==='automation')return{ok:true,value:{ok:true,document:'d1',ready:'complete',text:'fixture'}};
    if(command==='input')return{accepted:true,trusted_input:true};
    if(command==='chooser')return{ok:true,choosers:[]};
    if(command==='dialog')return{ok:true,dialogs:[]};
    if(command==='downloads')return{ok:true,downloads:[],released:params.action==='release'};
    if(command==='upload')return{ok:true,released:params.action==='release',retained:params.action==='status'};
    throw Error('Unexpected native command: '+command);
  }};
  const run=(command='observe',params={},extra={})=>dispatch({command,params,view,action:{id:'world-recovery'},
    api:{async call(){return{state:receipt};}},...extra});
  return{state,calls,view,run,cancel(){receipt='cancelled';}};
}

test('persistent native world failures stop before page work and identify the required explicit recovery',async()=>{
  for(const reason of reasons)for(const command of ['observe','wait_for','capture','act','navigate_to']){
    const f=fixture(reason),out=await f.run(command,{op:'click',url:'https://fixture.invalid',until:'ready'});
    assert.equal(out.result.ok,false,`${command}: ${reason}`);
    assert.equal(out.result.error.code,reason==='world_identity_limit'?'browser_world_limit':'browser_world_unavailable');
    assert.equal(out.result.error.world_reason,reason);assert.equal(out.result.error.effect_may_have_occurred,false);
    assert.deepEqual(out.result.error.recovery,{action:'manual_or_close',automatic_retry:false});
    assert.match(out.result.error.message,/manual|close/i);
    assert(f.calls.every(row=>row.command==='state'||row.command==='downloads'&&row.params.action==='list'),`${command} touched ${JSON.stringify(f.calls)}`);
  }
});

test('the first failed admission is classified from its native state without repeating the script',async()=>{
  const f=fixture(),original=f.view.execute;let scripts=0;
  f.view.execute=async(command,params)=>{
    if(command==='automation'){scripts++;f.state.automation_world_error='world_unique_context_unsupported';throw Error('Observe the current page before another action');}
    return original(command,params);
  };
  const out=await f.run();assert.equal(out.result.error.code,'browser_world_unavailable');
  assert.equal(out.result.error.world_reason,'world_unique_context_unsupported');
  assert.equal(out.result.error.effect_may_have_occurred,false);assert.equal(scripts,1);
  assert(!out.result.error.message.includes('Observe the current page'));
});

test('world loss after input retains uncertainty and never replays the original effect',async()=>{
  const f=fixture(),original=f.view.execute;let inputs=0;
  f.view.execute=async(command,params)=>{
    if(command==='input'){inputs++;f.state.automation_world_error='world_context_metadata_rebound';}
    return original(command,params);
  };
  const pageDriver={async read(method,options,control){await control.checkpoint();
    if(method==='prepare')return{ok:true,element:{ref:'e1'},point:{x:10,y:10},geometry:[10,10,20,20],frame_chain:[{frame:'f0',document:'original-frame'}]};
    return{ok:true,document:'d1',ready:'complete',text:'fixture'};
  }};
  const out=await f.run('act',{op:'click',document:'d1',target:{ref:'e1'}},{pageDriver});
  assert.equal(out.result.error.code,'action_uncertain');assert.equal(out.result.error.effect_may_have_occurred,true);
  assert.equal(out.result.error.world_reason,'world_context_metadata_rebound');assert.equal(inputs,1);
});

test('world faults leave native metadata and explicit release available without new page effects',async()=>{
  for(const [command,params] of [['dialog',{action:'list'}],['chooser',{action:'list'}],
    ['upload',{action:'status',ticket:'original'}],['upload',{action:'release',ticket:'original',confirmed:true}],
    ['download',{action:'list'}],['download',{action:'release',download:'original',confirmed:true}]]){
    const f=fixture('world_identity_limit');f.state.dialog_capability=true;
    const out=await f.run(command,params);assert.equal(out.result.ok,true,JSON.stringify(out));
    assert(!f.calls.some(row=>['automation','input','navigate'].includes(row.command)));
  }
});

test('cancellation and takeover keep priority, and absent or unrecognized metadata adds no authority',async()=>{
  for(const mode of ['cancelled','manual_control']){
    const f=fixture('world_identity_limit');if(mode==='cancelled')f.cancel();else f.state.manual=true;
    const out=await f.run();assert.equal(out.result.error.code,mode);assert.equal(out.result.error.world_reason,undefined);
    assert(!f.calls.some(row=>row.command==='automation'));
  }
  for(const reason of ['', 'private-value-that-must-not-be-echoed']){
    const f=fixture(reason),out=await f.run();assert.equal(out.result.ok,true);
    assert(!JSON.stringify(out).includes('private-value'));assert(f.calls.some(row=>row.command==='automation'));
  }
});

test('existing native boundaries remain visible before world recovery advice',async()=>{
  for(const [field,outcome] of [['dialog_open','dialog_open'],['popup_open','popup_open'],['chooser_open','file_chooser_open']]){
    const f=fixture('world_identity_limit');f.state[field]=true;
    const out=await f.run('act',{op:'click'});assert.equal(out.result.outcome,outcome);
    assert(!f.calls.some(row=>['automation','input','navigate'].includes(row.command)));
  }
});

test('a failed admission cannot diagnose a cancelled or replaced original scope',async()=>{
  for(const mode of ['cancelled','context_changed']){
    const f=fixture(),original=f.view.execute;let live=true,scripts=0;
    f.view.execute=async(command,params)=>{
      if(command==='automation'){
        scripts++;f.state.automation_world_error='world_identity_limit';
        if(mode==='cancelled')f.cancel();else live=false;
        throw Error('Original admission failed');
      }
      return original(command,params);
    };
    const out=await f.run('observe',{}, {alive:()=>live});assert.equal(out.result.error.code,mode);
    assert.equal(out.result.error.world_reason,undefined);assert.equal(scripts,1);
  }
});
