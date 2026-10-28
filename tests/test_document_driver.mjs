import assert from 'node:assert/strict';
import test from 'node:test';
import {fixture,createDocumentDriver,doc,fd,ref} from './fixtures/document-driver.mjs';

test('main-document observation qualifies references and declares the exact restricted scope',async()=>{
  const f=fixture(),out=await f.driver.read('observe');assert(out.ok);assert.equal(out.elements[0].ref,ref);
  assert.equal(out.url,'https://fixture.test/');assert.equal(out.frame_scope,'main_document');
  assert.deepEqual(out.limitations,['child_frames_unavailable']);assert.equal(out.frames.length,1);assert.equal(out.frames[0].document,fd);
  assert(!f.state.calls.some(row=>row.command==='frames'||row.command==='evaluate'));
});

test('main-document target helpers retain the native chain and use only the original local reference',async()=>{
  for(const method of ['locate','prepare','verify','optionList','selectOptions']){
    const f=fixture(),out=await f.driver.read(method,{document:doc,target:{ref},op:'fill',text:'synthetic'});assert(out.ok);
    assert.equal(f.state.calls.find(row=>row.method===method).options.target.ref,'e7');
    if(method==='prepare'){
      assert.deepEqual(out.frame_chain,[{frame:'f0',document:fd}]);assert.deepEqual(out.native_target,{document:doc,ref:'e7'});
      assert.equal(out.element.ref,ref);
    }
    if(method==='optionList')assert.equal(out.ref,ref);
  }
});

test('stale references and explicit child-frame targets cannot select another world',async()=>{
  for(const target of [{ref:ref.replace(fd,'fd-00000000-0000-4000-8000-000000000000')},
    {ref:ref.replace(doc,'d-00000000-0000-4000-8000-000000000000')},{ref:'e7'},
    {ref,name:'conflict'},{frame:'f-child',name:'Root'}]){
    const f=fixture(),out=await f.driver.read('prepare',{document:doc,target,op:'click'});assert.equal(out.ok,false);
    assert(!f.state.calls.some(row=>row.method==='prepare'));assert.equal(out.error.effect_may_have_occurred,false);
  }
  const f=fixture();assert.equal((await f.driver.read('locate',{target:{ref}})).error.code,'stale_document');assert.equal(f.state.calls.length,0);
});

test('native replacement or mismatched reply fails before publishing another document',async()=>{
  for(const kind of ['native','navigation','reply-native','reply-navigation','dom','local-frame']){
    const f=fixture();f.state.hook=({method,result})=>{
      if(method==='observe'){
        if(kind==='native')f.state.fd='fd-00000000-0000-4000-8000-000000000000';
        if(kind==='navigation')f.state.navigation++;
        if(kind==='reply-native')result.native_document='fd-other';
        if(kind==='reply-navigation')result.navigation++;
        if(kind==='dom')result.value.document='d-other';
        if(kind==='local-frame')result.value.elements=[{ref:'e7',frame:'f-child'}];
      }return result;
    };
    const out=await f.driver.read('observe');assert.equal(out.ok,false,kind);assert.equal(out.error.effect_may_have_occurred,false);
  }
});

test('malformed and oversized observations never become model-visible successful results',async()=>{
  for(const change of [value=>{value.elements=Array(401).fill({ref:'e7',frame:'f0'});},
    value=>{value.frames=[];},value=>{value.text='x'.repeat(262145);},value=>{value.elements[0]={ref:'guess',frame:'f0'};}]){
    const f=fixture();f.state.hook=({method,result})=>{if(method==='observe')change(result.value);return result;};
    const out=await f.driver.read('observe');assert.equal(out.ok,false);assert.equal(out.error.effect_may_have_occurred,false);
  }
});

test('unsupported providers, blockers, unknown scale and unconfirmed cleanup refuse without target evaluation',async()=>{
  for(const kind of ['provider','blocked','scale','cleanup']){
    const f=fixture();
    if(kind==='provider')f.state.available=false;
    if(kind==='blocked'){f.state.ready=false;f.state.phase='world_view_unavailable';}
    if(kind==='scale')f.state.scale=false;
    if(kind==='cleanup')f.state.clean=false;
    const out=await f.driver.read('prepare',{document:doc,target:{ref},op:'click'});assert.equal(out.ok,false);
    assert(!f.state.calls.some(row=>row.command==='automation'));
  }
  for(const method of ['focus','captureArea','uploadInfo','visualPoint','arbitrary']){
    const f=fixture();assert.equal((await f.driver.read(method)).error.code,'unsupported_capability');assert.equal(f.state.calls.length,0);
  }
});

test('native mutation failures retain certainty and cause without replay or page-error leakage',async()=>{
  for(const sent of [true,false]){
    const f=fixture();f.state.hook=({method,result})=>method==='selectOptions'?
      {ok:false,error:{code:'world_timed_out',evaluation_may_have_occurred:sent}}:result;
    const out=await f.driver.read('selectOptions',{document:doc,target:{ref},values:['one']});
    assert.equal(out.error.code,'world_timed_out');assert.equal(out.error.effect_may_have_occurred,sent);
    assert.equal(f.state.calls.filter(row=>row.method==='selectOptions').length,1);
  }
  const f=fixture();f.state.hook=({method,result})=>{if(method==='selectOptions')throw Error('private page-handler text');return result;};
  const out=await f.driver.read('selectOptions',{document:doc,target:{ref}});assert(out.error.effect_may_have_occurred);
  assert(!out.error.message.includes('private page-handler text'));
});

test('expired native reads and checkpoints settle without dispatching more work',async()=>{
  let calls=0;const driver=createDocumentDriver({execute:()=>{calls++;return new Promise(()=>{});}});
  const out=await driver.read('observe',{timeout_ms:10});assert.equal(out.error.code,'timed_out');assert.equal(calls,1);
  const checkpoint=createDocumentDriver({execute:()=>{throw Error('Must not dispatch');},checkpoint:()=>new Promise(()=>{})});
  assert.equal((await checkpoint.read('observe',{timeout_ms:10})).error.code,'timed_out');
  const f=fixture();let finish;
  f.state.hook=({method,result})=>method==='selectOptions'?new Promise(resolve=>{finish=()=>resolve(result);}):result;
  const mutation=await f.driver.read('selectOptions',{document:doc,target:{ref},timeout_ms:20});
  assert.equal(mutation.error.code,'timed_out');assert(mutation.error.effect_may_have_occurred);
  const count=f.state.calls.length;finish();await new Promise(resolve=>setTimeout(resolve,1));assert.equal(f.state.calls.length,count);
});

test('original checkpoint boundaries propagate unchanged and never enter another input path',async()=>{
  const boundary={browserBoundary:{handled:true,result:{ok:false,error:{code:'cancelled'}}}};
  const f=fixture();await assert.rejects(f.driver.read('observe',{}, {checkpoint:()=>{throw boundary;}}),error=>error===boundary);
  assert.equal(f.state.calls.length,0);
  const control={calls:0,checkpoint(){this.calls++;}};
  assert((await f.driver.read('observe',{},control)).ok);assert(control.calls>0);
});
