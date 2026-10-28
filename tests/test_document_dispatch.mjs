import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import {fixture,createDocumentDriver,doc,fd,ref} from './fixtures/document-driver.mjs';

let source=readFileSync(new URL('../host/automation-dispatch.js',import.meta.url),'utf8');
source=source.replace('from "./automation-runtime.js"',`from ${JSON.stringify(new URL('../page/runtime.js',import.meta.url).href)}`);
for(const name of ['capture-image','visual-snapshot','visual-dispatch','upload-dispatch','download-dispatch','download-save'])
  source=source.replace(`from "./${name}.js"`,`from ${JSON.stringify(new URL(`../host/${name}.js`,import.meta.url).href)}`);
const {executeBrowserAutomation}=await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);

// Couple the production driver and shared dispatcher to a synthetic native
// protocol. These receipts do not certify Android framework input/interception.
function coupled(){
  const f=fixture(),inputs=[];let downloads=false,chooser=false,focused=false,text='';
  f.state.hook=({method,options,result})=>{
    if(method==='verify'){result.value.focused=focused;result.value.verified=options.op!=='fill'||text===options.text;}
    return result;
  };
  const execute=async(command,params)=>{
    if(command==='state')return{...await f.execute(command,params),automation:f.state.available,
      browser_use:{available:f.state.available,reason:'isolated_world_unsupported'},frame_target_keyboard:true,
      wheel_input:f.state.wheel===true,
      download_capability:true,downloads_armed:downloads,chooser_capability:true,chooser_upload_capability:true,chooser_armed:chooser};
    if(command==='downloads'){if(params.action==='arm')downloads=true;return{ok:true,armed:downloads,downloads:[]};}
    if(command==='chooser'){if(params.action==='arm')chooser=true;return{ok:true,armed:chooser,choosers:[]};}
    if(command==='input'){
      assert(downloads&&chooser,'Native input preceded interception acknowledgement');
      assert.equal(params.input_document,doc);assert.equal(params.input_ref,'e7');assert.equal(params.navigation,7);
      assert.deepEqual(params.frame_chain,[{frame:'f0',document:fd}]);
      if(['focus','key','text'].includes(params.kind)){assert.equal(params.focus_document,doc);assert.equal(params.focus_ref,'e7');}
      inputs.push(params);if(typeof f.state.inputReply==='function')return f.state.inputReply(params,inputs);
      if(params.kind==='focus')focused=true;if(params.kind==='text')text=params.text;
      return{accepted:true,target_focused:focused,trusted_input:params.kind!=='focus'};
    }
    return f.execute(command,params);
  };
  const view={execute},pageDriver=createDocumentDriver({execute});
  const run=(command,params,other={})=>executeBrowserAutomation({command,params,view,pageDriver,browser:view,
    action:{id:'synthetic-main-document'},api:{async call(method){assert.equal(method,'ui.result');return{state:'pending'};}},...other});
  return{...f,inputs,run};
}

test('shared observation and waits consume the qualified main-document protocol',async()=>{
  const f=coupled(),observed=await f.run('observe',{});assert(observed.result.ok);assert.equal(observed.result.elements[0].ref,ref);
  assert.equal(observed.result.frame_scope,'main_document');
  const waited=await f.run('wait_for',{document:doc,until:'visible',target:{ref}});assert(waited.result.matched);
  assert.equal(f.inputs.length,0);
});

test('shared click, fill and key carry the same exact native document and isolated target',async()=>{
  for(const params of [{op:'click'},{op:'fill',text:'中文🙂'},{op:'key',key:'Enter'}]){
    const f=coupled(),out=await f.run('act',{document:doc,target:{ref},...params});assert(out.result.ok,JSON.stringify(out.result));
    assert.deepEqual(f.inputs.map(row=>row.kind),params.op==='click'?['click']:params.op==='fill'?['focus','key','text']:['focus','key']);
    assert.equal(f.state.calls.filter(row=>row.command==='evaluate'||row.command==='click'||row.command==='type').length,0);
  }
});

test('unsupported Android input kinds and unavailable automation stop before native work',async()=>{
  for(const op of ['hover','scroll']){
    const f=coupled(),out=await f.run('act',{op,document:doc,target:{ref},dy:100});
    assert.equal(out.result.error.code,'unsupported_capability');assert.equal(out.result.error.effect_may_have_occurred,false);
    assert.equal(f.inputs.length,0);assert.equal(f.state.calls.filter(row=>row.command==='automation').length,0);
  }
  const f=coupled();f.state.available=false;const out=await f.run('observe',{});
  assert.equal(out.result.error.code,'unsupported_capability');assert.equal(f.inputs.length,0);
  assert.equal(f.state.calls.filter(row=>row.command==='automation').length,0);
});

test('context cancellation during preparation never dispatches the partially prepared target',async()=>{
  const f=coupled();let alive=true;f.state.hook=({method,result})=>{if(method==='prepare')alive=false;return result;};
  const out=await f.run('act',{document:doc,target:{ref},op:'click'},{alive:()=>alive});
  assert.equal(out.result.error.code,'context_changed');assert.equal(out.result.error.effect_may_have_occurred,false);
  assert.equal(f.inputs.length,0);
});
test('Android viewport and target scrolling pin a root hit and send one native wheel without a focus click',async()=>{
  for(const target of [undefined,{ref}]){
    const f=coupled();f.state.wheel=true;const out=await f.run('act',{op:'scroll',document:doc,target,dx:97,dy:131});
    assert(out.result.ok,JSON.stringify(out.result));assert.deepEqual(f.inputs.map(row=>row.kind),['wheel']);
    assert.equal(f.inputs[0].dx,97);assert.equal(f.inputs[0].dy,131);
    assert(!Object.hasOwn(f.inputs[0],'focus_ref'));assert.equal(f.state.calls.filter(row=>row.method==='scrollPoint').length,1);
  }
  const f=coupled();f.state.wheel=true;
  f.state.hook=({method,result})=>method==='scrollPoint'?{...result,value:{ok:false,error:{code:'android_frames_unavailable'}}}:result;
  const out=await f.run('act',{op:'scroll',document:doc,dx:0,dy:100});assert.equal(out.result.error.code,'android_frames_unavailable');assert.equal(f.inputs.length,0);
});

const nativeRefusal=(code,overrides={})=>({accepted:false,trusted_input:false,target_focused:false,
  effect_may_have_occurred:false,cleanup_confirmed:true,delivered_steps:0,error:{code},...overrides});

test('native guard refusal retains its cause and confirmed zero effects without replay',async()=>{
  for(const op of ['click','scroll']){
    const f=coupled();f.state.wheel=true;
    f.state.inputReply=()=>nativeRefusal('input_target_changed',{error:{code:'input_target_changed',message:'page-provided exception must not escape'}});
    const out=await f.run('act',{op,document:doc,target:{ref},dy:100});
    assert.equal(out.result.error.code,'input_target_changed');assert.equal(out.result.error.effect_may_have_occurred,false);
    assert.deepEqual(out.result.error.native_input,{kind:op==='scroll'?'wheel':'click',effect_may_have_occurred:false,cleanup_confirmed:true,delivered_steps:0});
    assert(!JSON.stringify(out).includes('page-provided'));assert.equal(f.inputs.length,1);
    f.state.inputReply=null;
    const next=await f.run('act',{op,document:doc,target:{ref},dy:100});assert(next.result.ok);
    assert.equal(f.inputs.length,2,'Only the independently requested next action may send more input');
  }
});

test('a later native refusal cannot erase effects from earlier focus or selection',async()=>{
  for(const refusedKind of ['key','text']){
    const f=coupled(),hook=f.state.hook;
    f.state.hook=args=>{const result=hook(args);if(args.method==='verify')result.value.focused=true;return result;};
    f.state.inputReply=params=>params.kind===refusedKind?nativeRefusal('input_focus_lost'):
      {accepted:true,target_focused:params.kind==='focus',trusted_input:params.kind!=='focus'};
    const out=await f.run('act',{op:'fill',document:doc,target:{ref},text:'must not be replayed'});
    assert.equal(out.result.error.code,'input_focus_lost');assert.equal(out.result.error.effect_may_have_occurred,true);
    assert.equal(out.result.error.native_input.kind,refusedKind);assert.equal(out.result.error.native_input.effect_may_have_occurred,false);
    assert.deepEqual(f.inputs.map(row=>row.kind),refusedKind==='key'?['focus','key']:['focus','key','text']);
  }
});

test('partial native input preserves its cause, cleanup and delivered-step receipt',async()=>{
  const f=coupled();f.state.inputReply=()=>nativeRefusal('input_cleanup_failed',{
    effect_may_have_occurred:true,cleanup_confirmed:false,delivered_steps:1});
  const out=await f.run('act',{op:'click',document:doc,target:{ref}});
  assert.equal(out.result.error.code,'input_cleanup_failed');assert.equal(out.result.error.effect_may_have_occurred,true);
  assert.deepEqual(out.result.error.native_input,{kind:'click',effect_may_have_occurred:true,cleanup_confirmed:false,delivered_steps:1});
  assert.equal(f.inputs.length,1);
});

test('malformed or untrusted native replies retain conservative input uncertainty',async()=>{
  const valid=nativeRefusal('input_target_changed');
  for(const reply of [null,{}, {...valid,accepted:true}, {...valid,effect_may_have_occurred:undefined},
      {...valid,cleanup_confirmed:undefined},{...valid,delivered_steps:1},{...valid,delivered_steps:-1},
      {...valid,delivered_steps:1.5},{...valid,delivered_steps:2147483648},
      {...valid,error:{code:'unbounded\npage text'}},{...valid,error:{code:'a'.repeat(65)}}]){
    const f=coupled();f.state.inputReply=()=>reply;
    const out=await f.run('act',{op:'click',document:doc,target:{ref}});
    assert.equal(out.result.error.code,'action_uncertain');assert.equal(out.result.error.effect_may_have_occurred,true);
    assert(!Object.hasOwn(out.result.error,'native_input'));assert.equal(f.inputs.length,1);
  }
});

test('unconfirmed cleanup and lost transport cannot claim zero effects or replay input',async()=>{
  const f=coupled();f.state.inputReply=()=>nativeRefusal('input_cleanup_failed',{cleanup_confirmed:false});
  const out=await f.run('act',{op:'click',document:doc,target:{ref}});
  assert.equal(out.result.error.code,'input_cleanup_failed');assert.equal(out.result.error.effect_may_have_occurred,true);
  assert.equal(out.result.error.native_input.cleanup_confirmed,false);assert.equal(f.inputs.length,1);
  const lost=coupled();lost.state.inputReply=()=>{throw Error('Native acknowledgement lost');};
  const uncertain=await lost.run('act',{op:'click',document:doc,target:{ref}});
  assert.equal(uncertain.result.error.code,'action_uncertain');assert.equal(uncertain.result.error.effect_may_have_occurred,true);
  assert.equal(lost.inputs.length,1);
});
