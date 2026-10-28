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
      download_capability:true,downloads_armed:downloads,chooser_capability:true,chooser_upload_capability:true,chooser_armed:chooser};
    if(command==='downloads'){if(params.action==='arm')downloads=true;return{ok:true,armed:downloads,downloads:[]};}
    if(command==='chooser'){if(params.action==='arm')chooser=true;return{ok:true,armed:chooser,choosers:[]};}
    if(command==='input'){
      assert(downloads&&chooser,'Native input preceded interception acknowledgement');
      assert.equal(params.input_document,doc);assert.equal(params.input_ref,'e7');assert.equal(params.navigation,7);
      assert.deepEqual(params.frame_chain,[{frame:'f0',document:fd}]);
      if(['focus','key','text'].includes(params.kind)){assert.equal(params.focus_document,doc);assert.equal(params.focus_ref,'e7');}
      if(params.kind==='focus')focused=true;if(params.kind==='text')text=params.text;
      inputs.push(params);return{accepted:true,target_focused:focused,trusted_input:params.kind!=='focus'};
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
