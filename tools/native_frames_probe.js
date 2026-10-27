import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
import {createLocalApplicationClient} from '/js/api/application.js';
import {createFrameReader} from '/js/features/workbench/frame-reader.js';
const origin=FRAME_FIXTURE_ORIGIN,runtime=FRAME_RUNTIME_SOURCE;
const bridge=createNativeBrowserBridge(),api=createLocalApplicationClient(()=>({extension:'browser-frame-qa'})),browser='browser-frame-native-qa',foreign='browser-frame-foreign-qa';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));let checks=0,stage='startup',report,lastInventory;
const assert=(value,text)=>{checks++;if(!value)throw Error(text);};
async function until(fn,text,ms=15000){const end=performance.now()+ms;while(performance.now()<end){if(await fn())return;await pause(40);}throw Error(text);}
const call=(command,params={},target=browser)=>bridge.call(command,{browser:target,...params});
const frames=async(target=browser)=>{lastInventory=await call('frames',{action:'list',agent:true},target);return lastInventory;};
const evaluate=(frame,script,target=browser)=>call('frames',{action:'evaluate',frame:frame.id,document:frame.document,script,agent:true},target);
const observe=frame=>evaluate(frame,`(${runtime})().observe({limit:20,max_bytes:1024})`);
const reader=createFrameReader({execute:(command,params)=>call(command,params)});
async function navigate(url){
  const before=(await call('state')).navigation;await call('navigate',{url});
  await until(async()=>{const state=await call('state');return state.navigation!==before&&state.url===url&&!state.loading;},'Requested navigation did not reach its own ready document');
}
try{
  await call('create',{url:origin+'/root',visible:true,bounds:{x:0,y:50,width:1000,height:650,scale:1}});
  await until(async()=>!(await call('state')).loading,'Root page did not load');
  stage='inventory';let tree;
  await until(async()=>{tree=await frames();return tree.ok&&tree.complete&&tree.frames.length===4&&!tree.frames.some(row=>row.loading);},'Frame inventory did not become complete');
  const root=tree.frames.find(row=>row.id==='f0'),same=tree.frames.find(row=>row.url===origin+'/same'),cross=tree.frames.find(row=>row.url.endsWith('/cross')),nested=tree.frames.find(row=>row.url.endsWith('/nested'));
  const version=Number(runtime.match(/const version = (\d+);/)[1]);
  const forged=await call('evaluate',{script:`['__mdo_browser_use_runtime_v1','__mdo_browser_use_local_frame_v1'].every(marker=>Object.getOwnPropertyDescriptor(globalThis,marker)?.value.version===${version}&&!Object.getOwnPropertyDescriptor(globalThis,marker)?.configurable)`});
  assert(forged.ok&&forged.value,'The matching-version, nonconfigurable page spoof was not installed');
  assert(root&&same&&cross&&nested,'Native tree omitted a frame');assert(cross.separate_process,'Cross-site fixture did not route to an OOPIF session');
  assert(same.parent===root.id&&cross.parent===root.id&&nested.parent===cross.id,'Native frame ancestry is incorrect');
  assert(!JSON.stringify(tree).includes('sessionId')&&!JSON.stringify(tree).includes('executionContextId'),'Raw protocol identities leaked');
  stage='isolated-read';
  for(const [frame,label]of [[root,'Root site A'],[same,'Same site child'],[cross,'Cross site B'],[nested,'Nested site A']]){
    const result=await observe(frame);assert(result.ok&&result.value.ok,`Frame runtime failed (${label})`);
    assert(result.value.text.includes(label),`Wrong frame document (${label})`);assert(!JSON.stringify(result.value).includes('forged-frame-observation'),'Page replaced isolated runtime');
    assert(result.frame===frame.id&&result.frame_document===frame.document,'Isolated receipt lost its original frame identity');
  }
  stage='primitive-results';
  for(const value of ['', '原生 JSON "转义"\n结果',null]){
    const result=await evaluate(cross,JSON.stringify(value));assert(result.ok&&result.value===value,'Frame primitive value lost its JSON type or escaping');
    const top=await call('automation',{agent:true,script:JSON.stringify(value)});assert(top.ok&&top.value===value,'Root primitive value lost its JSON type or escaping');
  }
  stage='combined-read';const combined=await reader.observe({limit:100,max_bytes:4096});
  assert(combined.ok&&combined.frames.length===4&&combined.frames.every(row=>row.dom),'Combined isolated frame observation failed');
  assert(combined.elements.filter(row=>row.name==='Root action').length===1&&combined.elements.filter(row=>row.name==='Same action').length===1&&
    combined.elements.filter(row=>row.name==='Cross action').length===1&&combined.elements.filter(row=>row.name==='Nested action').length===1,'Frames were duplicated or omitted');
  assert(new Set(combined.elements.map(row=>row.ref)).size===combined.elements.length,'Local frame references collided');
  assert(!JSON.stringify(combined).includes('synthetic-private-value'),'Combined frame observation exposed a form value');
  const ambiguous=await reader.locate({document:combined.document,target:{name:'Shared action'}});
  assert(ambiguous.error?.code==='element_ambiguous'&&ambiguous.error.candidates.length===2&&
    ambiguous.error.candidates[0].frame!==ambiguous.error.candidates[1].frame,'Cross-frame duplicate names were not ambiguous');
  const exact=await reader.locate({document:combined.document,target:{name:'Shared action',frame:cross.id}});
  assert(exact.ok&&exact.route.frame===cross.id,'Explicit cross-frame semantic location failed');
  const pinned=await reader.locate({document:combined.document,target:{ref:exact.element.ref}});
  assert(pinned.ok&&pinned.element.ref===exact.element.ref,'A qualified frame reference did not resolve in its original world');
  const tiny=await reader.observe({limit:1,max_bytes:256});
  assert(tiny.ok&&tiny.truncated&&tiny.elements.length===1&&new TextEncoder().encode(tiny.text).length<=256,'Combined observation did not obey its global budget');
  const nodeBound=await evaluate(cross,`(${runtime})({local_frame:true}).observe({_max_nodes:2})`);
  assert(nodeBound.ok&&nodeBound.value.truncated&&nodeBound.value.visited===2&&nodeBound.value.frames.length===1,'The local runtime exceeded its assigned traversal budget');
  stage='foreign-view';await call('create',{url:origin+'/foreign',visible:false},foreign);
  await until(async()=>!(await call('state',{},foreign)).loading,'Foreign fixture did not load');
  const rejected=await evaluate(cross,'true',foreign);assert(!rejected.ok&&rejected.error.code==='stale_frame','Frame identity crossed browser views');
  stage='frame-navigation';
  const changed=await call('automation',{agent:true,script:`document.querySelector('#cross').src=${JSON.stringify(origin.replace('127.0.0.1','localhost')+'/cross-next')};true`});assert(changed.ok,'Fixture navigation was not sent');
  let fresh;await until(async()=>{const tree=await frames();fresh=tree.frames?.find(row=>row.url.endsWith('/cross-next'));return tree.ok&&fresh&&!fresh.loading;},'Child navigation did not settle');
  const old=await evaluate(cross,'globalThis.staleFrameWrite=1;true');assert(!old.ok&&old.error.code==='stale_frame','Navigated frame accepted its previous identity');
  assert(old.error.evaluation_may_have_occurred===false,'Pre-evaluation stale rejection lost its no-dispatch receipt');
  assert((await evaluate(fresh,'globalThis.staleFrameWrite||0')).value===0,'Stale frame evaluation wrote into the new document');
  assert((await observe(fresh)).value.text.includes('Cross site next document'),'Fresh child observation failed');
  const staleReference=await reader.locate({document:combined.document,target:{ref:exact.element.ref}});
  assert(!staleReference.ok&&staleReference.error.code==='stale_frame','Combined reference crossed child navigation');
  const nextElement=await reader.locate({document:combined.document,target:{name:'Next action'}});
  assert(nextElement.ok&&nextElement.route.frame===fresh.id,'Fresh cross-frame semantic location failed after navigation');
  const uncertain=await evaluate(fresh,'globalThis.confirmedFrameEffect=1;throw Error("fixture failure")');
  assert(!uncertain.ok&&uncertain.error.code==='frame_evaluation_failed'&&uncertain.error.evaluation_may_have_occurred===true,'A dispatched frame exception falsely claimed no evaluation');
  assert((await evaluate(fresh,'globalThis.confirmedFrameEffect')).value===1,'Frame exception fixture did not actually execute');
  stage='manual-control';await call('takeover');let denied=false;
  try{const result=await observe(fresh);denied=!result.ok;}catch{denied=true;}
  assert(denied,'Manual takeover did not fence isolated frame reads');await call('release');
  stage='close-pending';const pending=evaluate(fresh,'(()=>{const end=Date.now()+300;while(Date.now()<end){};return "late"})()');await pause(20);await call('close');
  const closed=await pending;assert(!closed.ok,'Closing a view returned a successful pending frame read');
  await call('create',{url:origin+'/root',visible:true,bounds:{x:0,y:50,width:1000,height:650,scale:1}});
  await until(async()=>!(await call('state')).loading,'Replacement browser did not load');
  const previous=await evaluate(fresh,'true');assert(!previous.ok&&previous.error.code==='stale_frame','Recreated browser accepted an old frame identity');
  stage='frame-bound';await navigate(origin+'/many');
  const bounded=await frames();assert(!bounded.ok&&['frame_limit','frame_tree_invalid'].includes(bounded.error.code),'Frame limit was not explicit');
  assert(bounded.usage.frames<=64&&bounded.usage.sessions<=64&&bounded.usage.pending===0,'Frame quota retained excess records or its terminal job');
  await navigate(origin+'/root');
  await until(async()=>{const tree=await frames();return tree.ok&&tree.complete&&tree.frames.length===4;},'Frame resource error poisoned the following document');
  stage='session-bound';await navigate(origin+'/many-cross');const saturated=await frames();
  assert(!saturated.ok&&['frame_limit','frame_session_limit','frame_tree_invalid'].includes(saturated.error.code),'OOPIF session limit was not explicit');
  assert(saturated.usage.frames<=64&&saturated.usage.sessions<=64&&saturated.usage.pending===0,'OOPIF session quota retained excess records or its terminal job');
  await navigate(origin+'/root');await until(async()=>{const tree=await frames();return tree.ok&&tree.complete&&tree.frames.length===4;},'OOPIF session resource error poisoned the following document');
  const staleRoot=await reader.locate({document:combined.document,target:{name:'Root action'}});
  assert(!staleRoot.ok&&staleRoot.error.code==='stale_document','Combined root identity survived top-level navigation');
  report={passed:true,checks,backend:'actual-webview2-frame-sessions',frames:4,oopif_confirmed:true,nested_frame:true,isolated_runtime:true,
    foreign_view_denied:true,navigation_invalidates_old_document:true,evaluation_uncertainty_retained:true,manual_control:true,close_cancels_pending:true,recreated_view_rejects_old_identity:true,bounded_frames_recover_after_navigation:true,bounded_oopif_sessions:true,
    combined_observation:true,global_ambiguity:true,qualified_references:true};
}catch(error){report={passed:false,stage,checks,error:String(error.message||error).slice(0,512),inventory:lastInventory};}
finally{for(const id of [browser,foreign])try{await call('close',{},id);}catch{}bridge.dispose();}
await api.call('storage.write',{path:'qa/native-frames.json',text:JSON.stringify(report)});document.querySelector('#status').textContent=report.passed?'Native frame checks passed':report.error;
