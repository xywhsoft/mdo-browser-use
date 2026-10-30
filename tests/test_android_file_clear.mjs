import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';

const source=await readFile(new URL('../host/android/MdoBrowserCdpFileInput.java',import.meta.url),'utf8');
function constant(name,known={}){
  const body=source.match(new RegExp(`static final String ${name}=([\\s\\S]*?);\\r?\\n`))?.[1];
  assert(body,`Missing fixed native script ${name}`);
  let remaining=body;
  for(const [token] of body.matchAll(/"(?:\\.|[^"\\])*"|\bINFO\b/g))remaining=remaining.replace(token,'');
  assert.match(remaining,/^[+\s]*$/,'Unexpected native script initializer');
  // Concatenation operators belong to Java, not to the actual fixed JS source.
  return [...body.matchAll(/"(?:\\.|[^"\\])*"|\bINFO\b/g)].map(([token])=>token==='INFO'?known.INFO:JSON.parse(token)).join('');
}
const script=constant('CLEAR_STATUS',{INFO:constant('INFO')});
function fixture(files={length:0}){
  let reads=0;const document={},params={document:'d-original',ref:'e7'};
  function Input(){}
  Object.defineProperty(Input.prototype,'files',{configurable:true,get(){reads++;return files;}});
  const element=Object.assign(Object.create(Input.prototype),{ownerDocument:document,isConnected:true,multiple:false,webkitdirectory:false});
  const runtime={documentNode:document,document:params.document,uploadInfo:()=>({ok:true,document:params.document,element:{ref:params.ref,hidden:true}}),uploadNode:()=>element};
  const run=()=>Function('document','HTMLInputElement','globalThis',`return (${script});`)(document,Input,{__mdo_browser_use_local_frame_v1:runtime})(element,params);
  return{document,params,element,runtime,Input,run,reads:()=>reads};
}

test('native clear verification reads the original platform FileList getter and requires zero files',()=>{
  for(const [files,empty] of [[{length:0},true],[{length:1},false],[null,false]]){
    const f=fixture(files),result=f.run();assert.equal(result.ok,true);assert.equal(result.empty,empty);
    assert.equal(result.document,f.params.document);assert.equal(result.ref,'e7');assert.equal(f.reads(),1);
  }
  const f=fixture({length:2});Object.defineProperty(f.element,'files',{value:{length:0}});
  assert.equal(f.run().empty,false,'A wrapper property concealed the native files');assert.equal(f.reads(),1);
});

test('clear verification refuses changed documents and replaced nodes before reading file state',()=>{
  for(const mode of ['owner','runtime','ref','disconnected','node']){
    const f=fixture();
    if(mode==='owner')f.element.ownerDocument={};if(mode==='runtime')f.runtime.document='changed';
    if(mode==='ref')f.runtime.uploadInfo=()=>({ok:true,document:f.params.document,element:{ref:'e8'}});
    if(mode==='disconnected')f.element.isConnected=false;if(mode==='node')f.runtime.uploadNode=()=>({});
    assert.equal(f.run().ok,false,mode);assert.equal(f.reads(),0,mode);
  }
});

test('missing or failed native getters never produce a confirmed clear',()=>{
  const f=fixture();delete f.Input.prototype.files;
  assert.deepEqual(f.run(),{ok:false,code:'clear_unconfirmed'});
  Object.defineProperty(f.Input.prototype,'files',{get(){throw Error('Native getter unavailable');}});
  assert.throws(f.run,/Native getter unavailable/);assert.equal(f.reads(),0);
});
