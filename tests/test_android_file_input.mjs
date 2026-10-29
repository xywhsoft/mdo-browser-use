import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {test} from 'node:test';

// Execute the actual fixed Java helper strings, without an Android/DOM claim.
const java=readFileSync(new URL('../host/android/MdoBrowserCdpFileInput.java',import.meta.url),'utf8');
function constant(name,next){
  const body=java.match(new RegExp(`\\b${name}=([\\s\\S]+?);\\s*${next}`))[1],parts=[];let offset=0;
  for(const token of body.matchAll(/"(?:\\.|[^"\\])*"|\+/g)){
    assert.equal(body.slice(offset,token.index).trim(),'');offset=token.index+token[0].length;
    if(token[0]!=='+')parts.push(JSON.parse(token[0]));
  }
  assert.equal(body.slice(offset).trim(),'');return parts.join('');
}
const node=constant('NODE','static final String INFO'),info=constant('INFO','final String document'),documentId='d-00000000-0000-4000-8000-000000000001';
function fixture(){
  const document={},pin={document:documentId,ref:'e7'},state={infoCalls:0,nodeCalls:0,fail:null,substitute:false};
  const element={ownerDocument:document,isConnected:true,multiple:true,webkitdirectory:false,
    get files(){throw Error('Inspection cannot read selected files');},set value(_){throw Error('Inspection cannot assign');},dispatchEvent(){throw Error('Inspection cannot dispatch');}};
  const runtime={documentNode:document,document:documentId,
    uploadNode(options){state.nodeCalls++;assert.deepEqual(JSON.parse(JSON.stringify(options)),{document:documentId,target:{ref:'e7'}});return state.substitute?{}:element;},
    uploadInfo(options){state.infoCalls++;assert.deepEqual(JSON.parse(JSON.stringify(options)),{document:documentId,target:{ref:'e7'}});
      return state.fail||{ok:true,document:documentId,element:{ref:'e7',hidden:true,privateField:'private-selected-file-name'}};}};
  const context=vm.createContext({document,__mdo_browser_use_local_frame_v1:runtime,pin,element});
  return{context,document,runtime,element,pin,state,node:()=>vm.runInContext(`${node}(pin)`,context),
    info:()=>JSON.parse(JSON.stringify(vm.runInContext(`(${info})(element,pin)`,context)))};
}

test('file input helpers return only the pinned native object and bounded public flags without reading files or dispatching',()=>{
  const f=fixture();assert.equal(f.node(),f.element);assert.deepEqual(f.info(),{ok:true,document:documentId,ref:'e7',multiple:true,directory:false,hidden:true});
  assert(!JSON.stringify(f.info()).includes('private-selected'));f.element.webkitdirectory=true;assert.equal(f.info().directory,true);
});

test('file input getters refuse a replaced runtime or local document before resolving an element',()=>{
  for(const mode of ['absent','document','native-document']){
    const f=fixture();if(mode==='absent')delete f.context.__mdo_browser_use_local_frame_v1;
    if(mode==='document')f.runtime.document='changed';if(mode==='native-document')f.runtime.documentNode={};
    assert.equal(f.node(),null);assert.deepEqual(f.info(),{ok:false,code:'target_changed'});assert.equal(f.state.infoCalls+f.state.nodeCalls,0);
  }
});

test('disconnected, foreign, disabled, inert or substituted file inputs cannot pass the final object proof',()=>{
  for(const mode of ['disconnected','foreign','blocked','substituted']){
    const f=fixture();if(mode==='disconnected')f.element.isConnected=false;if(mode==='foreign')f.element.ownerDocument={};
    if(mode==='blocked')f.state.fail={ok:false,error:{code:'element_disabled'}};if(mode==='substituted')f.state.substitute=true;
    assert.deepEqual(f.info(),{ok:false,code:mode==='blocked'?'element_disabled':'target_changed'});
    if(mode==='disconnected'||mode==='foreign')assert.equal(f.state.infoCalls+f.state.nodeCalls,0);
  }
});
