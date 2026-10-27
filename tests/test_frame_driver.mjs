import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';

const url=source=>`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
const reader=(await readFile(new URL('../host/frame-reader.js',import.meta.url),'utf8'))
  .replace("import {browserRuntime} from './automation-runtime.js';",'function browserRuntime(){}');
const driver=(await readFile(new URL('../host/frame-driver.js',import.meta.url),'utf8'))
  .replace("import {browserRuntime} from './automation-runtime.js';",'function browserRuntime(){}')
  .replace("import {createFrameReader} from './frame-reader.js';",`import {createFrameReader} from ${JSON.stringify(url(reader))};`);
const {createFrameDriver}=await import(url(driver));
const guid=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const doc='d-'+guid(1),local='d-'+guid(2),child='f-'+guid(2),fd='fd-'+guid(2),rootFd='fd-'+guid(1);

function fixture(){
  const rows=[{id:'f0',document:rootFd,parent:null,isolated:true,loading:false,url:'https://root.test'},
    {id:child,document:fd,parent:'f0',isolated:true,loading:false,url:'https://child.test'}];
  const state={calls:[],effect:0,parentFocused:true,onRead:null,checkpoint:null};
  const api=createFrameDriver({checkpoint:async()=>{await state.checkpoint?.();},execute:async(command,params)=>{
    assert.equal(command,'frames');state.calls.push(params);
    if(params.action==='list')return{ok:true,complete:true,frames:structuredClone(rows)};
    if(params.action==='owner'){
      await state.onRead?.({method:'owner',rows});
      return{ok:true,frame:child,frame_document:fd,parent:'f0',parent_document:rootFd,
        value:{ok:true,focused:state.parentFocused,point:{x:120,y:210},geometry:[100,200,300,200,100,200,300,200]}};
    }
    const tail=params.script.split('({local_frame:true}).').at(-1),method=tail.slice(0,tail.indexOf('(')),options=JSON.parse(tail.slice(tail.indexOf('(')+1,-1));
    const document=params.frame==='f0'?doc:local,element={ref:'e1',frame:'f0',name:'Child',role:'textbox',editable:true};
    let value={ok:true,document,url:'https://fixture.test',ready:'complete',viewport:{width:800,height:600}};
    if(method==='locate')value=params.frame===child?{ok:true,document,element,visited:1}:{ok:false,error:{code:'element_not_found'},visited:1};
    if(method==='prepare')value={ok:true,document,element,point:{x:20,y:10},geometry:[10,0,20,20,20,10]};
    if(method==='verify')value={ok:true,document,focused:true,verified:true};
    if(method==='optionList')value={ok:true,document,ref:'e1',frame:'f0',options:[{index:0,label:'First'}]};
    if(method==='uploadInfo')value={ok:true,document,element:{...element,role:'fileinput',hidden:true}};
    if(method==='selectOptions'){state.effect++;value={ok:true,document,dispatched:true,verified:true};}
    if(method==='captureArea')value={ok:true,document,redactions:[{x:1,y:1,width:10,height:10}],region:{x:0,y:0,width:800,height:600}};
    const custom=await state.onRead?.({method,options,value,rows});
    return custom||{ok:true,frame:params.frame,frame_document:params.document,value};
  }});
  return{state,api};
}

test('prepare maps a qualified target and pins the complete document chain',async()=>{
  const {api}=fixture(),out=await api.read('prepare',{document:doc,target:{name:'Child'},op:'fill',scroll:true});
  assert(out.ok);assert.equal(out.document,doc);assert.equal(out.element.frame,child);assert(out.element.ref.startsWith('r1|'));
  assert.deepEqual(out.point,{x:120,y:210});assert.equal(out.geometry.length,16);
  assert.deepEqual(out.frame_chain,[{frame:child,document:fd},{frame:'f0',document:rootFd}]);
});

test('parent focus participates in keyboard verification without discarding field state',async()=>{
  const {api,state}=fixture();let out=await api.read('verify',{document:doc,target:{name:'Child'},op:'fill',text:'synthetic'});
  assert(out.ok&&out.focused&&out.verified);state.parentFocused=false;
  out=await api.read('verify',{document:doc,target:{name:'Child'},op:'fill',text:'synthetic'});
  assert(out.ok&&out.verified);assert.equal(out.focused,false);
});

test('option pages use public references while hidden file targets retain native-local pins',async()=>{
  const {api,state}=fixture(),options=await api.read('optionList',{document:doc,target:{name:'Child'}});
  assert(options.ok);assert(options.ref.startsWith('r1|'));assert.equal(options.frame,child);
  const file=await api.read('uploadInfo',{document:doc,target:{name:'Child'}});assert(file.ok&&file.element.hidden);
  assert.deepEqual(file.native_target,{document:local,ref:'e1',frame_chain:[{frame:child,document:fd},{frame:'f0',document:rootFd}]});
  assert(state.calls.some(call=>call.script?.includes('"_upload":true')));
});

test('a sent mutation retains uncertainty on native error and is never replayed',async()=>{
  const {api,state}=fixture();state.onRead=({method})=>method==='selectOptions'?{ok:false,error:{code:'frame_interrupted',evaluation_may_have_occurred:true}}:null;
  const out=await api.read('selectOptions',{document:doc,target:{name:'Child'},choices:[{index:0}]});
  assert.equal(out.error.code,'frame_interrupted');assert.equal(out.error.effect_may_have_occurred,true);assert.equal(state.effect,1);
});

test('a native pre-evaluation rejection accurately reports no mutation',async()=>{
  const {api,state}=fixture();state.onRead=({method})=>method==='selectOptions'?{ok:false,error:{code:'stale_frame',evaluation_may_have_occurred:false}}:null;
  const out=await api.read('selectOptions',{document:doc,target:{name:'Child'}});
  assert.equal(out.error.code,'stale_frame');assert.equal(out.error.effect_may_have_occurred,false);
});

test('lost mutation delivery is uncertain while caller cancellation preserves its checkpoint',async()=>{
  const first=fixture();first.state.onRead=({method})=>{if(method==='selectOptions')throw Error('Synthetic lost reply');};
  const out=await first.api.read('selectOptions',{document:doc,target:{name:'Child'}});assert(out.error.effect_may_have_occurred);assert.equal(first.state.effect,1);
  const second=fixture(),sentinel=Object.assign(Error('cancelled'),{browserBoundary:{handled:true,result:{ok:false}}});
  second.state.checkpoint=()=>{if(second.state.effect)throw sentinel;};
  await assert.rejects(second.api.read('selectOptions',{document:doc,target:{name:'Child'}}),error=>error===sentinel);assert.equal(second.state.effect,1);
});

test('changed parent identity rejects mapped coordinates before any input exists',async()=>{
  const {api,state}=fixture();state.onRead=({method,rows})=>{if(method==='owner')rows[0].document='fd-'+guid(9);};
  const out=await api.read('prepare',{document:doc,target:{name:'Child'}});
  assert.equal(out.error.code,'stale_frame');assert.equal(out.error.effect_may_have_occurred,false);assert.equal(state.effect,0);
});

test('a native failure packet still checks authoritative cancellation before returning',async()=>{
  const {api,state}=fixture(),boundary=Object.assign(Error('Original action cancelled'),{browserBoundary:{handled:true,result:{ok:false}}});let cancelled=false;
  state.onRead=({method})=>{if(method==='selectOptions'){cancelled=true;return{ok:false,error:{code:'frame_interrupted',evaluation_may_have_occurred:true}};}};
  await assert.rejects(api.read('selectOptions',{document:doc,target:{name:'Child'}},{checkpoint:()=>{if(cancelled)throw boundary;}}),error=>error===boundary);
  assert.equal(state.effect,1);
});

test('the driver rejects arbitrary methods and keeps cross-frame capture masking explicit',async()=>{
  const {api,state}=fixture();assert.equal((await api.read('evaluate',{script:'forged'})).error.code,'frame_method_invalid');assert.equal(state.calls.length,0);
  assert.equal((await api.read('clearFiles',{})).error.code,'frame_method_invalid');assert.equal(state.effect,0);
  assert.equal((await api.read('captureArea',{document:doc,target:{name:'Child'},mode:'element'})).error.code,'frame_capture_pending');
  const root=await api.read('captureArea',{document:doc,mode:'viewport'});assert(root.ok);assert.equal(root.redactions.length,1);
});
