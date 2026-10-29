import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {test} from 'node:test';
import {uploadPickerBroker} from '../host/upload-picker-broker.js';

// Controlled DOM/clock peers execute the production helper; these are not an
// Android framework/URI-provider qualification or a real-browser FileList test.
const id='d-00000000-0000-4000-8000-000000000001',source=`(${uploadPickerBroker.toString()})()`;
function fixture(install=source){
  const state={now:50,shows:0,assigned:0,removeFails:false,setFails:false,clockReads:0},lists=new WeakMap(),names=new WeakMap(),sizes=new WeakMap(),dirs=new WeakMap();
  class Event{constructor(type,options={}){this.type=type;Object.assign(this,options);}stopPropagation(){this.stopped=true;}}
  class EventTarget{constructor(){this.events=new Map();}addEventListener(type,fn){if(!this.events.has(type))this.events.set(type,[]);this.events.get(type).push(fn);}
    removeEventListener(type,fn){this.events.set(type,(this.events.get(type)||[]).filter(f=>f!==fn));}
    dispatchEvent(event){for(const fn of [...(this.events.get(event.type)||[])])fn(event);return true;}}
  class Node extends EventTarget{constructor(owner){super();this.ownerDocument=owner;this.parentNode=null;this.children=[];}
    get isConnected(){return this===this.ownerDocument?.documentElement||!!this.parentNode?.isConnected;}
    appendChild(child){child.parentNode=this;this.children.push(child);return child;}
    removeChild(child){if(state.removeFails)throw Error('private provider error');this.children.splice(this.children.indexOf(child),1);child.parentNode=null;return child;}}
  const roots=new WeakMap();class Element extends Node{constructor(owner){super(owner);this.style={setProperty(){}};}
    attachShadow(options){assert.equal(options.mode,'closed');const root=new Node(this.ownerDocument);root.parentNode=this;roots.set(this,root);return root;}
    get shadowRoot(){return null;}}
  class HTMLInputElement extends Element{constructor(owner){super(owner);lists.set(this,[]);}
    get files(){return lists.get(this);}set files(files){if(state.setFails&&this===target)throw Error('private assignment error');lists.set(this,files);state.assigned++;}
    get value(){return '';}set value(value){assert.equal(value,'');lists.get(this).splice(0);}
    showPicker(){state.shows++;if(state.openFails){const error=Error('private picker error');if(typeof state.openFails==='string')error.name=state.openFails;throw error;}}}
  class Document{constructor(){this.documentElement=new Element(this);}createElement(name){return name==='input'?new HTMLInputElement(this):new Element(this);}}
  class Blob{get size(){return sizes.get(this);}}
  class File extends Blob{constructor(name,size,relative=''){super();names.set(this,name);sizes.set(this,size);dirs.set(this,relative);}get name(){return names.get(this);}get webkitRelativePath(){return dirs.get(this);}}
  const document=new Document(),target=document.createElement('input');target.multiple=true;document.documentElement.appendChild(target);
  const runtime={documentNode:document,document:id,uploadNode:()=>state.replaced?{}:target,
    uploadInfo:()=>state.disabled?{ok:false,error:{code:'element_disabled'}}:{ok:true,element:{multiple:target.multiple,directory:!!target.webkitdirectory}}};
  const context=vm.createContext({document,Document,Node,Element,HTMLInputElement,EventTarget,Event,File,Blob,TextEncoder,
    performance:{now(){state.clockReads++;return state.now;}},crypto:{getRandomValues(bytes){for(let i=0;i<bytes.length;i++)bytes[i]=i;return bytes;}},__mdo_browser_use_local_frame_v1:runtime});
  const api=vm.runInContext(install,context),args={key:'job-1',document:id,ref:'e7',files:[{name:'payload-内容 #% +.bin',size:131075}],timeout_ms:1000};
  const select=(key=args.key,files=args.files)=>{const input=api.node({key});assert(input);lists.set(input,files.map(row=>new File(row.name,row.size,row.relative||'')));return input;};
  return{api,context,args,state,document,target,runtime,roots,lists,select,File,Event,plain:value=>JSON.parse(JSON.stringify(value))};
}

test('private helper is immutable per isolated document; no getter spoof is invoked',()=>{
  const f=fixture();assert.equal(vm.runInContext(source,f.context),f.api);assert(Object.isFrozen(f.api));
  const context=vm.createContext({document:{}});vm.runInContext(`Object.defineProperty(globalThis,'__mdo_browser_use_upload_picker_v1',{get(){throw Error('getter invoked')}})`,context);
  assert.throws(()=>vm.runInContext(source,context),/upload_picker_runtime_conflict/);
});
test('prepare creates a closed private input with no mutation of the target',()=>{
  const f=fixture(),prepared=f.api.prepare(f.args);assert(prepared.ok);assert.match(prepared.nonce,/^application\/x-mdo-browseruse-[a-f0-9]{32}$/);
  const input=f.api.node(f.args);assert.equal(input.accept,prepared.nonce);assert.equal(input.parentNode.parentNode.shadowRoot,null);
  assert.equal(f.state.assigned,0);assert.equal(f.target.files.length,0);assert.equal(f.target.accept,undefined);
  const status=f.api.status(f.args);assert(!JSON.stringify(status).includes(prepared.nonce));assert.equal(status.opened,false);
  assert.equal(f.api.prepare({...f.args,timeout_ms:120000}).remaining_ms,1000);
  assert.equal(f.api.prepare({...f.args,key:'other'}).error.code,'upload_picker_busy');
  assert.equal(f.api.prepare({...f.args,ref:'e8'}).error.code,'upload_picker_key_conflict');
  assert(f.api.prepare({...f.args,files:[{size:f.args.files[0].size,name:f.args.files[0].name}]}).ok);
});
test('manifest and original target bounds are enforced before creating private nodes',()=>{
  for(const edit of [p=>p.files=[],p=>p.files=Array(17).fill({name:'a',size:1}),p=>p.timeout_ms=120001,p=>p.document='d-short',p=>p.ref='e0',p=>p.script='private',
    p=>p.files=[{name:'../secret',size:1}],p=>p.files=[{name:'CON.txt',size:1}],p=>p.files=[{name:'a.',size:1}],p=>p.files=[{name:'a',size:33554433}],
    p=>p.files=[{name:'a',size:1,path:'private'}],p=>p.files=Array(3).fill({name:'a',size:33554432})]){
    const f=fixture(),p=structuredClone(f.args);edit(p);assert.equal(f.api.prepare(p).error.code,'upload_picker_arguments_invalid');assert.equal(f.document.documentElement.children.length,1);
  }
  for(const mode of ['directory','single','disabled','stale']){
    const f=fixture();if(mode==='directory')f.target.webkitdirectory=true;if(mode==='single'){f.target.multiple=false;f.args.files.push({name:'b',size:1});}
    if(mode==='disabled')f.state.disabled=true;if(mode==='stale')f.runtime.document='changed';
    assert.equal(f.api.prepare(f.args).ok,false);assert.equal(f.document.documentElement.children.length,1);
  }
});
test('one native open and exact FileList metadata are required before one handoff',()=>{
  const f=fixture();f.api.prepare(f.args);f.select();assert.equal(f.api.commit(f.args).error.code,'upload_picker_pending');assert.equal(f.state.assigned,0);
  assert(f.api.open(f.args).opened);assert(f.api.open(f.args).opened);assert.equal(f.state.shows,1);
  // Page-world own getters cannot forge the native name/size descriptors.
  Object.defineProperty(f.api.node(f.args).files[0],'name',{get(){throw Error('page getter');}});
  const events=[];for(const type of ['input','change'])f.target.addEventListener(type,e=>events.push([e.type,e.bubbles,!!e.composed]));
  assert(f.api.status(f.args).selected);const result=f.api.commit(f.args);assert(result.ok&&result.completed&&result.verified);assert.equal(result.events_trusted,false);
  assert.deepEqual(events,[['input',true,true],['change',true,false]]);assert.equal(f.state.assigned,1);assert.equal(f.api.commit(f.args),result);assert.equal(f.api.node(f.args),null);
  assert.equal(f.api.prepare({...f.args,ref:'e8'}).error.code,'upload_picker_key_conflict');
  assert(!JSON.stringify(result).includes('application/x-'));assert(f.api.dispose(f.args).cleanup_confirmed);assert.equal(f.target.files.length,1);
  assert(f.api.status(f.args).completed);assert.equal(f.api.prepare(f.args).error.code,'upload_picker_key_retired');
});
test('mismatched counts, names, sizes or directory selections fail sticky without assignment',()=>{
  for(const files of [[{name:'changed',size:131075}],[{name:'payload-内容 #% +.bin',size:1}],[{name:'payload-内容 #% +.bin',size:131075,relative:'folder/file'}],[{name:'a',size:1},{name:'b',size:1}]]){
    const f=fixture();f.api.prepare(f.args);f.api.open(f.args);f.select(f.args.key,files);const result=f.api.status(f.args);assert.equal(result.error.code,'upload_picker_manifest_changed');
    assert.equal(f.api.commit(f.args),result);assert.equal(f.state.assigned,0);assert(f.api.dispose(f.args).cleanup_confirmed);
  }
});
test('cancel, replacement, disabled target and original deadline never recover by retry',()=>{
  for(const mode of ['cancel','replacement','disabled','disconnected','host-removed','expired','clock']){
    const f=fixture();f.api.prepare(f.args);f.api.open(f.args);const input=f.select();
    if(mode==='cancel')input.dispatchEvent(new f.Event('cancel'));
    if(mode==='replacement')f.state.replaced=true;if(mode==='disabled')f.state.disabled=true;if(mode==='disconnected')f.target.parentNode=null;
    if(mode==='host-removed')input.parentNode.parentNode.parentNode=null;if(mode==='expired')f.state.now=1050;
    if(mode==='clock'){f.state.now=200;assert(f.api.status(f.args).ok);f.state.now=150;}
    const result=f.api.commit(f.args);assert.equal(result.ok,false);assert.equal(result.dispatched,false);assert.equal(f.state.assigned,0);
    f.state.now=50;f.state.replaced=false;f.state.disabled=false;assert.equal(f.api.commit(f.args),result);assert(f.api.dispose(f.args).cleanup_confirmed);
  }
});
test('a page resetting/removing/replacing the target stops the event sequence without claiming verification or replay',()=>{
  for(const type of ['input','change'])for(const change of ['clear','remove','replace']){
    const f=fixture();f.api.prepare(f.args);f.api.open(f.args);f.select();const events=[];
    for(const name of ['input','change'])f.target.addEventListener(name,e=>{events.push(e.type);if(e.type!==type)return;if(change==='clear')f.target.value='';if(change==='remove')f.target.parentNode=null;if(change==='replace')f.state.replaced=true;});
    const result=f.api.commit(f.args);assert(result.ok&&result.accepted&&result.dispatched&&!result.completed&&!result.verified);assert.equal(result.outcome,'target_changed_after_'+type);
    assert.equal(events.length,type==='input'?1:2);assert.equal(f.api.commit(f.args),result);assert.equal(f.state.assigned,1);assert(f.api.dispose(f.args).cleanup_confirmed);
  }
});
test('native open/assignment exceptions stay sanitized and never automatically repeat',()=>{
  for(const mode of ['open','assign']){
    const f=fixture();f.api.prepare(f.args);if(mode==='open')f.state.openFails=true;const opened=f.api.open(f.args);
    if(mode==='assign'){f.select();f.state.setFails=true;}
    const result=mode==='open'?opened:f.api.commit(f.args);assert.equal(result.ok,false);assert.equal(result.error.effect_may_have_occurred,mode==='assign');
    assert(!JSON.stringify(result).includes('private'));assert.equal(f.api.commit(f.args),result);assert.equal(f.api.open(f.args),result);assert.equal(f.state.shows,1);
    assert(f.api.dispose(f.args).cleanup_confirmed);
  }
});
test('native DOM picker refusals have explicit static codes without arbitrary provider names',()=>{
  for(const [name,code]of [['NotAllowedError','upload_picker_activation_required'],['InvalidStateError','upload_picker_not_mutable'],['SecurityError','upload_picker_security_denied'],['constructor','upload_picker_open_unconfirmed'],['private-provider-name','upload_picker_open_unconfirmed']]){
    const f=fixture();f.api.prepare(f.args);f.state.openFails=name;const result=f.api.open(f.args);assert.equal(result.error.code,code);assert.equal(f.api.open(f.args),result);assert.equal(f.state.shows,1);
    assert(!JSON.stringify(result).includes('private-provider'));assert(f.api.dispose(f.args).cleanup_confirmed);
  }
});
test('cleanup failure poisons its original reservation until exact cleanup succeeds',()=>{
  const f=fixture();f.api.prepare(f.args);f.state.removeFails=true;assert.equal(f.api.dispose(f.args).cleanup_confirmed,false);assert.equal(f.api.node(f.args),null);
  assert.equal(f.api.prepare(f.args).error.code,'upload_picker_cleanup_failed');assert.equal(f.api.prepare({...f.args,key:'other'}).error.code,'upload_picker_busy');
  f.state.removeFails=false;assert(f.api.dispose(f.args).cleanup_confirmed);assert(f.api.dispose(f.args).cleanup_confirmed);assert(f.api.prepare({...f.args,key:'other'}).ok);
});
test('retired keys and lifetime resource cap survive later jobs without renewal or replay',()=>{
  const f=fixture();for(let i=0;i<64;i++){const p={...f.args,key:'job-'+i};assert(f.api.prepare(p).ok);assert(f.api.dispose(p).cleanup_confirmed);}
  assert.equal(f.api.prepare({...f.args,key:'job-64'}).error.code,'upload_picker_resource_limit');assert.equal(f.api.prepare({...f.args,key:'job-0'}).error.code,'upload_picker_key_retired');
});

test('canonical helper has no runtime imports or page/Agent data escape',()=>{
  const text=readFileSync(new URL('../host/upload-picker-broker.js',import.meta.url),'utf8');assert.equal(text.includes('import '),false);
  for(const name of ['fetch(','XMLHttpRequest','postMessage(','console.','DataTransfer(','new File(','new Blob('])assert.equal(text.includes(name),false);
});
test('generated Java installer executes the same private helper without a global function declaration',()=>{
  const java=readFileSync(new URL('../host/android/MdoBrowserCdpUploadScripts.java',import.meta.url),'utf8'),script=JSON.parse(java.match(/INSTALL=(.*);/)[1]);
  const f=fixture(script);assert(f.api.prepare(f.args).ok);f.api.open(f.args);f.select();assert(f.api.commit(f.args).completed);assert(f.api.dispose(f.args).cleanup_confirmed);
  assert.equal(vm.runInContext('typeof uploadPickerBroker',f.context),'undefined');assert.equal(vm.runInContext(script,f.context),f.api);
});
