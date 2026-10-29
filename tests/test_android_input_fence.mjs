import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import {browserRuntime} from '../page/runtime.js';

// Execute the production fixed scripts. The root-only composition below models
// the Java guard ordering, not native geometry, framework focus or event trust.
const java=readFileSync(new URL('../host/android/MdoBrowserCdpInput.java',import.meta.url),'utf8');
function constant(name){
  const expression=java.match(new RegExp('\\b'+name+'=([\\s\\S]+?);\\s*(?:static final|final String)'))[1];
  let position=0;const chunks=[];
  for(const match of expression.matchAll(/"(?:\\.|[^"\\])*"|\+/g)){
    assert.equal(expression.slice(position,match.index).trim(),'');position=match.index+match[0].length;
    if(match[0]!=='+')chunks.push(JSON.parse(match[0]));
  }
  assert.equal(expression.slice(position).trim(),'');return chunks.join('');
}
const root=constant('ROOT'),target=constant('TARGET'),id='d-12345678-1234-4123-8123-123456789abc';
function composed(context,witness){
  const run=source=>JSON.parse(JSON.stringify(vm.runInContext(`${source}(${JSON.stringify(witness)})`,context)));
  const before=run(root);if(!before.ok)return before;
  const local=run(target);if(!local.ok)return local;
  if(['click','move','wheel'].includes(witness.kind)&&(Math.abs(local.point.x-witness.x)>.5||Math.abs(local.point.y-witness.y)>.5))return{ok:false,code:'input_target_changed'};
  return run(root);
}
function fixture(kind='click'){
  const calls=[];
  const document={hasFocus:()=>true};
  const located={ok:true,element:{ref:'e7',frame:'f0'}};
  const runtime={documentNode:document,document:id,
    locate(options){calls.push(['locate',options]);return located;},
    prepare(options){calls.push(['prepare',options]);return{...located,point:{x:20,y:30},document:id};},
    focus(options){calls.push(['focus',options]);return{ok:true,focused:true};},
    visualPoint(options){calls.push(['visualPoint',options]);return{ok:true,frame_ref:null};},
    scrollPoint(options){calls.push(['scrollPoint',options]);return{ok:true,hit_ref:'e7',point:{x:20,y:30}};},
    verify(options){calls.push(['verify',options]);return{ok:true,focused:true};}};
  const context=vm.createContext({document,innerWidth:320,innerHeight:240,scrollX:10,scrollY:20,
    visualViewport:{width:320,height:240,scale:1,offsetLeft:0,offsetTop:0},
    __mdo_browser_use_local_frame_v1:runtime});
  vm.runInContext('self=globalThis;top=self',context);
  const witness={kind,document:id,ref:'e7',x:20,y:30,point:{x:20,y:30},width:640,height:480,scrollX:20,scrollY:40,scale:2};
  return{context,runtime,calls,located,witness,run(){return composed(context,witness);}};
}

test('Android native fence pins its exact root document, reference and CSS point',()=>{
  const f=fixture();assert.deepEqual(f.run(),{ok:true});
  assert.deepEqual(f.calls.map(row=>row[0]),['locate','prepare','visualPoint']);
  for(const [,options] of f.calls){assert.equal(options.document,id);if(options.target)assert.equal(options.target.ref,'e7');}
  assert.equal(f.calls[1][1].scroll,false);
  f.runtime.prepare=()=>({...f.located,point:{x:21,y:30}});
  assert.equal(f.run().code,'input_target_changed');
});

test('Android hover fence rechecks its exact hover target and rejects child-document hits',()=>{
  const f=fixture('move');assert.deepEqual(f.run(),{ok:true});
  assert.deepEqual(f.calls.map(row=>row[0]),['locate','prepare','visualPoint']);
  assert.equal(f.calls[1][1].op,'hover');assert.equal(f.calls[1][1].scroll,false);
  f.runtime.visualPoint=()=>({ok:true,frame_ref:'e8'});assert.equal(f.run().code,'input_target_changed');
  f.runtime.visualPoint=()=>({ok:true,frame_ref:null});f.runtime.prepare=()=>({...f.located,point:{x:21,y:30}});
  assert.equal(f.run().code,'input_target_changed');assert(!f.calls.some(row=>['focus','verify'].includes(row[0])));
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
    const f=fixture(kind);assert.deepEqual(f.run(),{ok:true});assert.equal(f.calls.at(-2)[0],kind==='focus'?'focus':'verify');assert.equal(f.calls.at(-1)[0],'prepare');
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
  f.runtime.scrollPoint=()=>({ok:false,error:{code:'input_target_changed'}});assert.equal(f.run().code,'input_target_changed');
  const moved=fixture('wheel');moved.context.scrollY++;assert.equal(moved.run().code,'input_viewport_changed');assert.equal(moved.calls.length,0);
});

test('Android click fence refuses an iframe hit even when the prepared parent contains it',()=>{
  const f=fixture();f.runtime.visualPoint=()=>({ok:true,frame_ref:'e8'});
  assert.equal(f.run().code,'input_target_changed');
  f.runtime.visualPoint=()=>({ok:false,error:{code:'visual_point_unavailable'}});
  assert.equal(f.run().code,'input_target_changed');
});

test('actual runtime parent preparation cannot bypass the native local-frame click boundary',()=>{
  for(const kind of ['span','iframe','frame','shadow-iframe']){
    const document={hasFocus:()=>true};let hit;
    const rectangle={x:10,y:20,left:10,top:20,right:30,bottom:40,width:20,height:20};
    const element=name=>({ownerDocument:document,localName:name,isConnected:true,parentElement:null,shadowRoot:null,
      getAttribute:attribute=>attribute==='aria-label'?'Original parent':null,hasAttribute:()=>false,
      matches:()=>false,closest:()=>null,getRootNode:()=>document,getClientRects:()=>[rectangle],getBoundingClientRect:()=>rectangle,
      contains(node){for(let n=node;n;n=n.parentElement)if(n===this)return true;return false;}});
    const parent=element('div'),inner=element(kind==='shadow-iframe'?'iframe':kind);inner.parentElement=parent;hit=parent;
    document.elementFromPoint=()=>hit;
    const context=vm.createContext({document,crypto:webcrypto,performance,TextEncoder,TextDecoder,URL,
      innerWidth:320,innerHeight:240,scrollX:0,scrollY:0,visualViewport:{width:320,height:240,scale:1,offsetLeft:0,offsetTop:0},
      getComputedStyle:()=>({display:'block',visibility:'visible',opacity:'1'})});
    vm.runInContext('self=globalThis;top=self',context);document.defaultView=vm.runInContext('globalThis',context);
    const runtime=vm.runInContext(`(${browserRuntime.toString()})({local_frame:true})`,context);
    const pin=runtime.scrollPoint({document:runtime.document,point:{x:20,y:30}});assert(pin.ok);
    if(kind==='shadow-iframe'){
      const host=element('div');host.parentElement=parent;host.shadowRoot={elementFromPoint:()=>inner};
      inner.parentElement=null;inner.getRootNode=()=>({host});hit=host;
    }else hit=inner;
    // The shared point helper deliberately accepts a descendant of the target;
    // only the local-frame native fence must reject the iframe boundary.
    const prepared=runtime.prepare({document:runtime.document,target:{ref:pin.hit_ref},op:'click',scroll:false});
    assert(prepared.ok,kind);assert.equal(prepared.point.x,20);assert.equal(prepared.point.y,30);
    const witness={kind:'click',document:runtime.document,ref:pin.hit_ref,x:20,y:30,width:640,height:480,scrollX:0,scrollY:0,scale:2};
    const out=JSON.parse(JSON.stringify(composed(context,witness)));
    if(kind==='span')assert.deepEqual(out,{ok:true},kind);else assert.equal(out.code,'input_target_changed',kind);
  }
});

test('local target script supports a proved child world without touching the top page',()=>{
  const f=fixture('focus');f.context.top={};
  const result=JSON.parse(JSON.stringify(vm.runInContext(`${target}(${JSON.stringify(f.witness)})`,f.context)));
  assert.equal(result.ok,true);assert.deepEqual(result.point,{x:20,y:30});
  assert.equal(composed(f.context,f.witness).code,'frame_changed','Only the separate root geometry script requires the top world');
});
