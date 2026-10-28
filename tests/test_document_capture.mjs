import assert from 'node:assert/strict';
import test from 'node:test';
import {fixture,doc,fd,ref} from './fixtures/document-driver.mjs';

test('main-document capture retains opaque frame masks, exact native context and bounded private geometry',async()=>{
  for(const mode of ['viewport','element']){
    const f=fixture(),options={document:doc,mode,...(mode==='element'?{target:{ref}}:{})};
    f.state.hook=({method,result})=>{if(method==='captureArea')result.value.secret='Private page data must not escape';return result;};
    const out=await f.driver.read('captureArea',options);assert(out.ok,JSON.stringify(out));
    assert.equal(out.frame_scope,'main_document');assert(out.limitations.includes('child_frames_masked'));
    assert(out.limitations.includes('image_actions_unavailable'));assert(!JSON.stringify(out).includes('Private page data'));
    assert.equal(out.frame_masks.readable,1);assert.equal(out.frame_masks.opaque,mode==='viewport'?1:0);
    assert.deepEqual(out.native_capture,{adapter:'android-main-document-v1',native_document:fd,navigation:7,document:doc,
      scroll:{x:0,y:0},viewport:{width:320,height:240}});
    assert.equal(out.redactions.length,mode==='viewport'?2:1);
    assert(out.redactions.every(mask=>!Object.hasOwn(mask,'frame_ref')));
    const stamp=JSON.parse(out.frame_stamp);assert.equal(stamp.length,1);assert.equal(stamp[0][1],fd);
    assert.equal(stamp[0][6],doc);assert.deepEqual(stamp[0][9].opaque_frames,mode==='viewport'?['e8']:[]);
    const call=f.state.calls.find(row=>row.method==='captureArea');assert.equal(call.options._max_nodes,6000);
    if(mode==='element')assert.equal(call.options.target.ref,'e7');
    assert(!f.state.calls.some(row=>['frames','capture','screenshot','evaluate'].includes(row.command)));
  }
});

test('main-document capture rejects incomplete privacy geometry and resource proofs',async()=>{
  const cases=[value=>{value.region.width=0;},value=>{value.region.x=NaN;},value=>{value.region.width=32769;},
    value=>{value.region={x:0,y:0,width:16384,height:16384};},value=>{value.viewport.width=0;},
    value=>{value.scroll.x=Infinity;},value=>{value.device_pixel_ratio=0;},value=>{value.device_pixel_ratio=17;},
    value=>{value.device_pixel_ratio=NaN;},value=>{value.visited=6001;},value=>{value.visited=-1;},
    value=>{value.visited=.5;},value=>{value.redaction='unverified';},value=>{value.redactions=null;},
    value=>{value.redactions=Array(257).fill(value.redactions[0]);},value=>{value.redactions[0].x=-10;},
    value=>{value.redactions[0].height=Infinity;},value=>{value.redactions[0].width=400;},
    value=>{value.redactions[1].frame_ref='page-provided-owner';},value=>{value.redactions.push(value.redactions[1]);}];
  for(const mutate of cases){
    const f=fixture();f.state.hook=({method,result})=>{if(method==='captureArea')mutate(result.value);return result;};
    const out=await f.driver.read('captureArea',{document:doc});assert.equal(out.ok,false);
    assert.equal(out.error.effect_may_have_occurred,false);assert(!Object.hasOwn(out,'native_capture'));
  }
});

test('Android capture modes, child targets and unsettled input refuse before privacy evaluation',async()=>{
  for(const mode of ['full_page','invented']){
    const f=fixture(),out=await f.driver.read('captureArea',{document:doc,mode});
    assert.equal(out.error.code,mode==='full_page'?'unsupported_capability':'capture_mode_invalid');assert.equal(f.state.calls.length,0);
  }
  for(const variant of ['scale','busy','cleanup','child']){
    const f=fixture();if(variant==='scale')f.state.scale=false;if(variant==='busy')f.state.inputBusy=true;if(variant==='cleanup')f.state.clean=false;
    const out=await f.driver.read('captureArea',{document:doc,mode:'element',target:variant==='child'?{frame:'f1',name:'Child'}:{ref}});
    assert(!out.ok);assert(!f.state.calls.some(row=>row.method==='captureArea'));
    if(variant!=='child')assert(!f.state.calls.some(row=>row.command==='automation'));
  }
});

test('native replacement and mismatched capture replies never publish private geometry',async()=>{
  for(const change of ['native','navigation','reply-native','reply-navigation','dom']){
    const f=fixture();f.state.hook=({method,result})=>{
      if(method==='captureArea'){
        if(change==='native')f.state.fd='fd-00000000-0000-4000-8000-000000000000';
        if(change==='navigation')f.state.navigation++;
        if(change==='reply-native')result.native_document='fd-other';
        if(change==='reply-navigation')result.navigation++;
        if(change==='dom')result.value.document='d-other';
      }return result;
    };
    const out=await f.driver.read('captureArea',{document:doc});assert.equal(out.ok,false,change);
    assert(!Object.hasOwn(out,'redactions'));assert(!Object.hasOwn(out,'native_capture'));
  }
});

test('viewport, scroll, opaque owner and density changes invalidate the capture stamp',async()=>{
  const f=fixture(),before=await f.driver.read('captureArea',{document:doc});assert(before.ok);
  for(const change of ['viewport','scroll','owner','density']){
    f.state.hook=({method,result})=>{if(method==='captureArea'){
      if(change==='viewport'){result.value.viewport.width=321;result.value.region.width=321;}
      if(change==='scroll'){result.value.scroll.x=1;result.value.region.x=1;for(const mask of result.value.redactions)mask.x++;}
      if(change==='owner')result.value.redactions[1].frame_ref='e9';
      if(change==='density')result.value.device_pixel_ratio=2;
    }return result;};
    const after=await f.driver.read('captureArea',{document:doc});assert(after.ok,change);assert.notEqual(before.frame_stamp,after.frame_stamp,change);
  }
});

test('viewport-only region proof rejects missing pixels or attempted offscreen capture',async()=>{
  for(const mode of ['viewport','element'])for(const variant of ['offscreen','incomplete']){
    const f=fixture();f.state.hook=({method,result})=>{if(method==='captureArea'){
      result.value.redactions=[];result.value.region={x:0,y:0,width:variant==='offscreen'?400:300,height:240};
    }return result;};
    const out=await f.driver.read('captureArea',{document:doc,mode,target:{ref}});
    if(mode==='element'&&variant==='incomplete')assert(out.ok);else assert(!out.ok);
  }
});
