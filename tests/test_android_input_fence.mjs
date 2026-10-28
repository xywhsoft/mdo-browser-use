import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

// Run the production Java constant. This tests its document/geometry protocol,
// not native WebView focus, scale reporting or trusted Android event behavior.
const java=readFileSync(new URL('../host/android/MdoBrowserNativeInput.java',import.meta.url),'utf8');
const expression=java.match(/\bFENCE=([\s\S]+?);\s*private final/)[1];
let position=0;
const chunks=[];
for(const match of expression.matchAll(/"(?:\\.|[^"\\])*"|\+/g)){
  assert.equal(expression.slice(position,match.index).trim(),'');position=match.index+match[0].length;
  if(match[0]!=='+')chunks.push(JSON.parse(match[0]));
}
assert.equal(expression.slice(position).trim(),'');
const script=chunks.join(''),id='d-12345678-1234-4123-8123-123456789abc';
function fixture(kind='click'){
  const calls=[];
  const document={hasFocus:()=>true};
  const located={ok:true,element:{ref:'e7',frame:'f0'}};
  const runtime={documentNode:document,document:id,
    locate(options){calls.push(['locate',options]);return located;},
    prepare(options){calls.push(['prepare',options]);return{...located,point:{x:20,y:30}};},
    focus(options){calls.push(['focus',options]);return{ok:true,focused:true};},
    scrollPoint(options){calls.push(['scrollPoint',options]);return{ok:true,hit_ref:'e7'};},
    verify(options){calls.push(['verify',options]);return{ok:true,focused:true};}};
  const context=vm.createContext({document,innerWidth:320,innerHeight:240,scrollX:10,scrollY:20,
    visualViewport:{width:320,height:240,scale:1,offsetLeft:0,offsetTop:0},
    __mdo_browser_use_local_frame_v1:runtime});
  vm.runInContext('self=globalThis;top=self',context);
  const witness={kind,document:id,ref:'e7',x:20,y:30,width:640,height:480,scrollX:20,scrollY:40,scale:2};
  return{context,runtime,calls,located,witness,run(){return JSON.parse(JSON.stringify(vm.runInContext(`${script}(${JSON.stringify(witness)})`,context)));}};
}

test('Android native fence pins its exact root document, reference and CSS point',()=>{
  const f=fixture();assert.deepEqual(f.run(),{ok:true});
  assert.deepEqual(f.calls.map(row=>row[0]),['locate','prepare']);
  for(const [,options] of f.calls){assert.equal(options.document,id);assert.equal(options.target.ref,'e7');}
  assert.equal(f.calls[1][1].scroll,false);
  f.runtime.prepare=()=>({...f.located,point:{x:21,y:30}});
  assert.equal(f.run().code,'input_target_changed');
});

test('Android native fence rejects changed document, subframe, missing runtime or detached target before focus',()=>{
  for(const change of [f=>{f.runtime.document='d-other';},f=>{f.runtime.documentNode={};},
    f=>{f.context.top={};},f=>{delete f.context.__mdo_browser_use_local_frame_v1;},
    f=>{f.located.ok=false;},f=>{f.located.element.frame='f1';}]){
    const f=fixture('focus');change(f);assert.equal(f.run().ok,false);
    assert.equal(f.calls.some(row=>row[0]==='focus'),false);
  }
});

test('Android native fence rejects viewport, zoom-offset, scroll and non-finite geometry before input',()=>{
  for(const change of [f=>{f.context.innerWidth=330;},f=>{f.context.innerHeight=250;},
    f=>{f.context.scrollY=50;},f=>{f.context.visualViewport.width=250;},
    f=>{f.context.visualViewport.height=200;},f=>{f.context.visualViewport.offsetTop=1;},
    f=>{f.context.visualViewport.offsetLeft=1;},f=>{f.context.visualViewport.scale=NaN;},
    f=>{f.context.innerWidth=Infinity;},f=>{f.context.visualViewport=null;}]){
    const f=fixture('focus');change(f);assert.equal(f.run().ok,false);assert.equal(f.calls.length,0);
  }
  const initial=fixture();initial.context.visualViewport.scale=0.5;
  assert.deepEqual(initial.run(),{ok:true},'An initially fitted layout is not a pinch-offset mismatch');
});

test('Android native fence checks the original keyboard target each time and observes focus side effects',()=>{
  for(const kind of ['focus','key','text']){
    const f=fixture(kind);assert.deepEqual(f.run(),{ok:true});assert.equal(f.calls.at(-1)[0],kind==='focus'?'focus':'verify');
    f.runtime.verify=f.runtime.focus=()=>({ok:true,focused:false});assert.equal(f.run().code,'input_focus_lost');
  }
  const f=fixture('focus');f.runtime.focus=()=>{f.context.scrollY++;return{ok:true,focused:true};};
  assert.equal(f.run().code,'input_viewport_changed');
  const changed=fixture('focus');changed.runtime.focus=()=>{changed.runtime.documentNode={};return{ok:true,focused:true};};
  assert.equal(changed.run().code,'frame_changed');
  const unfocused=fixture('text');unfocused.context.document.hasFocus=()=>false;
  assert.equal(unfocused.run().code,'input_focus_lost');
});
test('Android wheel fence requires the same root hit and viewport without focusing or clicking',()=>{
  const f=fixture('wheel');assert.deepEqual(f.run(),{ok:true});assert.deepEqual(f.calls.map(row=>row[0]),['scrollPoint']);
  assert.equal(f.calls[0][1].ref,'e7');assert.equal(f.calls[0][1].document,id);
  f.runtime.scrollPoint=()=>({ok:true,hit_ref:'e8'});assert.equal(f.run().code,'input_target_changed');
  f.runtime.scrollPoint=()=>({ok:false,error:{code:'android_frames_unavailable'}});assert.equal(f.run().code,'android_frames_unavailable');
  const moved=fixture('wheel');moved.context.scrollY++;assert.equal(moved.run().code,'input_viewport_changed');assert.equal(moved.calls.length,0);
});
