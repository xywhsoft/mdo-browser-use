import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

// Execute the production Java constant. This proves the isolated document and
// viewport protocol, not actual Android drawing, clipping or bitmap encoding.
const java=readFileSync(new URL('../host/android/MdoBrowserNativeCapture.java',import.meta.url),'utf8');
const expression=java.match(/\bFENCE=([\s\S]+?);\s*final MdoBrowserCapture/)[1],chunks=[];let position=0;
for(const match of expression.matchAll(/"(?:\\.|[^"\\])*"|\+/g)){
  assert.equal(expression.slice(position,match.index).trim(),'');position=match.index+match[0].length;
  if(match[0]!=='+')chunks.push(JSON.parse(match[0]));
}
assert.equal(expression.slice(position).trim(),'');
const script=chunks.join(''),id='d-12345678-1234-4123-8123-123456789abc';
function fixture(){
  const document={},runtime={documentNode:document,document:id};
  const context=vm.createContext({document,innerWidth:320,innerHeight:240,scrollX:10,scrollY:20,devicePixelRatio:2,
    visualViewport:{width:320,height:240,scale:1,offsetLeft:0,offsetTop:0},__mdo_browser_use_local_frame_v1:runtime});
  vm.runInContext('self=globalThis;top=self',context);
  const proof={document:id,viewport:{width:320,height:240},scroll:{x:10,y:20},device_pixel_ratio:2};
  return{context,runtime,proof,run(){return JSON.parse(JSON.stringify(vm.runInContext(`${script}(${JSON.stringify(proof)})`,context)));}};
}

test('Android capture fence admits only its exact original root isolated document',()=>{
  const f=fixture();assert.deepEqual(f.run(),{ok:true});
  for(const change of [f=>{f.context.top={};},f=>{f.runtime.document='other';},f=>{f.runtime.documentNode={};},
    f=>{delete f.context.__mdo_browser_use_local_frame_v1;}]){
    const changed=fixture();change(changed);assert.equal(changed.run().code,'frame_changed');
  }
});

test('Android capture fence rejects changed scroll, viewport, density and pinch offsets',()=>{
  for(const change of [f=>{f.context.innerWidth++;},f=>{f.context.innerHeight++;},f=>{f.context.scrollX++;},
    f=>{f.context.scrollY++;},f=>{f.context.devicePixelRatio=3;},f=>{f.context.visualViewport.offsetLeft=1;},
    f=>{f.context.visualViewport.offsetTop=1;},f=>{f.context.visualViewport.width=160;},
    f=>{f.context.visualViewport.height=120;},f=>{f.context.visualViewport.scale=0;},
    f=>{f.context.visualViewport.scale=NaN;},f=>{f.context.scrollY=Infinity;},f=>{f.context.visualViewport=null;}]){
    const f=fixture();change(f);assert.equal(f.run().ok,false);
  }
  const fitted=fixture();fitted.context.visualViewport.scale=.5;assert.deepEqual(fitted.run(),{ok:true});
});

test('capture guard exceptions reveal no page text and cannot be mistaken for a valid viewport',()=>{
  const f=fixture();Object.defineProperty(f.context.visualViewport,'width',{get(){throw Error('private page exception');}});
  const out=f.run();assert.equal(out.code,'capture_guard_failed');assert(!JSON.stringify(out).includes('private'));
});
