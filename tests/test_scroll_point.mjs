import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import vm from 'node:vm';
import test from 'node:test';
import {browserRuntime} from '../page/runtime.js';

function fixture(local_frame=true){
  const document={};let hit;
  const element=(name='div')=>({ownerDocument:document,localName:name,isConnected:true,shadowRoot:null});
  hit=element();document.elementFromPoint=()=>hit;
  const context=vm.createContext({document,crypto:webcrypto,performance,TextEncoder,TextDecoder,URL,innerWidth:320,innerHeight:240});
  const runtime=vm.runInContext(`(${browserRuntime.toString()})(${JSON.stringify({local_frame})})`,context);
  return{runtime,document,element,setHit(value){hit=value;}};
}
test('scroll point creates and verifies an actual root hit identity without reading field values',()=>{
  const f=fixture(),input=f.element('input');Object.defineProperty(input,'value',{get(){throw Error('Scroll read private field');}});f.setHit(input);
  const options={document:f.runtime.document,point:{x:20,y:30}},first=f.runtime.scrollPoint(options);
  assert(first.ok);assert.match(first.hit_ref,/^e[1-9][0-9]*$/);assert.equal(first.document,f.runtime.document);
  assert(f.runtime.scrollPoint({...options,ref:first.hit_ref}).ok);
  f.setHit(f.element());assert.equal(f.runtime.scrollPoint({...options,ref:first.hit_ref}).error.code,'input_target_changed');
});
test('scroll point resolves an open shadow hit and rejects iframe/foreign/detached points',()=>{
  const f=fixture(),inner=f.element(),outer=f.element();outer.shadowRoot={elementFromPoint:()=>inner};f.setHit(outer);
  const options={document:f.runtime.document,point:{x:20,y:30}},first=f.runtime.scrollPoint(options);assert(first.ok);
  f.setHit(inner);assert.equal(f.runtime.scrollPoint(options).hit_ref,first.hit_ref);
  for(const node of [f.element('iframe'),f.element('frame')]){f.setHit(node);assert.equal(f.runtime.scrollPoint(options).error.code,'android_frames_unavailable');}
  const foreign=f.element();foreign.ownerDocument={};f.setHit(foreign);assert.equal(f.runtime.scrollPoint(options).error.code,'input_target_changed');
  const detached=f.element();detached.isConnected=false;f.setHit(detached);assert.equal(f.runtime.scrollPoint(options).error.code,'input_target_changed');
  f.setHit(null);assert.equal(f.runtime.scrollPoint(options).error.code,'input_target_changed');
});
test('scroll point refuses unpinned documents, page-world runtime and out-of-viewport coordinates',()=>{
  const f=fixture();assert.equal(f.runtime.scrollPoint({document:'old',point:{x:0,y:0}}).error.code,'stale_document');
  for(const point of [{x:-1,y:0},{x:320,y:0},{x:0,y:240},{x:NaN,y:0},{x:Infinity,y:0}])
    assert.equal(f.runtime.scrollPoint({document:f.runtime.document,point}).error.code,'input_coordinate_invalid');
  const legacy=fixture(false);assert.equal(legacy.runtime.scrollPoint({document:legacy.runtime.document,point:{x:0,y:0}}).error.code,'frame_method_invalid');
});
test('scroll point reference retention stays bounded and does not recycle live aliases',()=>{
  const f=fixture(),options={document:f.runtime.document,point:{x:1,y:1}},first=f.runtime.scrollPoint(options);assert(first.ok);
  for(let n=1;n<2000;n++){f.setHit(f.element());assert(f.runtime.scrollPoint(options).ok);}
  f.setHit(f.element());assert.equal(f.runtime.scrollPoint(options).error.code,'reference_limit');
});
