import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
import {createLocalApplicationClient} from '/js/api/application.js';
import {createFrameReader} from '/js/features/workbench/frame-reader.js';
import {createFrameDriver} from '/js/features/workbench/frame-driver.js';
import {executeBrowserAutomation} from '/js/features/workbench/automation-dispatch.js';
const origin=FRAME_FIXTURE_ORIGIN,runtime=FRAME_RUNTIME_SOURCE,uploadWorkspace=FRAME_UPLOAD_WORKSPACE;
const bridge=createNativeBrowserBridge(),api=createLocalApplicationClient(()=>({extension:'browser-frame-qa'})),browser='browser-frame-native-qa',foreign='browser-frame-foreign-qa';
const filesApi=createLocalApplicationClient(()=>({extension:'browser-frame-qa',workspace:uploadWorkspace}));
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));let checks=0,stage='startup',report,lastInventory,geometryEvidence;
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
  assert((await call('state')).frame_automation===true,'The native view did not advertise its runtime-supported frame automation');
  assert((await call('state')).frame_snapshot_input===true,'The native view did not advertise visual frame-snapshot input fences');
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
  const foreignTree=await frames(foreign),foreignRoot=foreignTree.frames.find(row=>row.id==='f0');
  const noUuid=await evaluate(foreignRoot,`(()=>{Object.defineProperty(globalThis.crypto,'randomUUID',{value:undefined,configurable:true});const r=(${runtime})({local_frame:true});return{ok:true,document:r.document,no_random_uuid:typeof crypto.randomUUID==='undefined'}})()`,foreign);
  assert(noUuid.ok&&noUuid.value.ok&&noUuid.value.no_random_uuid&&/^d-[a-f0-9-]{36}$/i.test(noUuid.value.document),'A frame without randomUUID could not initialize its document identity');
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
  stage='owner-geometry';await navigate(origin+'/geometry');let geometryTree;
  await until(async()=>{geometryTree=await frames();return geometryTree.ok&&geometryTree.complete&&geometryTree.frames.length===3&&!geometryTree.frames.some(row=>row.loading);},'Nested geometry fixture did not settle');
  const geometryRoot=geometryTree.frames.find(row=>row.id==='f0'),outer=geometryTree.frames.find(row=>row.url.endsWith('/geometry-cross')),leaf=geometryTree.frames.find(row=>row.url.endsWith('/geometry-leaf'));
  assert(outer.separate_process&&leaf.separate_process,'Geometry fixture did not create nested cross-site processes');
  const leafObservation=await evaluate(leaf,`(${runtime})({local_frame:true}).observe({})`);
  const prepared=await evaluate(leaf,`(${runtime})({local_frame:true}).prepare(${JSON.stringify({document:leafObservation.value.document,op:'click',target:{name:'Nested geometry action'},scroll:true})})`);
  assert(prepared.ok&&prepared.value.ok,'Nested target was not locally actionable');
  const owner=(frame,point)=>call('frames',{action:'owner',agent:true,frame:frame.id,document:frame.document,point});
  const innerMapping=await owner(leaf,prepared.value.point),outerMapping=await owner(outer,innerMapping.value?.point);
  assert(innerMapping.ok&&innerMapping.value.ok&&innerMapping.parent===outer.id&&innerMapping.parent_document===outer.document,'Nested iframe owner did not resolve in its parent process');
  assert(outerMapping.ok&&outerMapping.value.ok&&outerMapping.parent==='f0'&&outerMapping.parent_document===geometryRoot.document,'Outer frame owner did not map to the main viewport');
  const expected=await call('evaluate',{script:`(()=>{const frame=document.querySelector('#outer'),box=frame.getBoundingClientRect(),style=getComputedStyle(frame);return{x:box.x+parseFloat(style.borderLeftWidth)+parseFloat(style.paddingLeft),y:box.y+parseFloat(style.borderTopWidth)+parseFloat(style.paddingTop)}})()`});
  assert(expected.ok&&Math.abs(expected.value.x-outerMapping.value.content.x)<.01&&Math.abs(expected.value.y-outerMapping.value.content.y)<.01,'Fractional iframe borders/padding lost coordinate precision');
  const chain=[leaf,outer,geometryRoot].map(frame=>({frame:frame.id,document:frame.document}));
  const snapshot=geometryTree.frames.map(frame=>({frame:frame.id,document:frame.document,parent:frame.parent||''}));
  const state=await call('state'),clicked=await call('input',{agent:true,kind:'click',navigation:state.navigation,frame_chain:chain,frame_snapshot:snapshot,...outerMapping.value.point});
  assert(clicked.accepted&&clicked.trusted_input,'Mapped nested point did not dispatch native input');
  geometryEvidence={prepared,innerMapping,outerMapping,clicked};let effect;
  for(let index=0;index<75;index++){
    effect=await evaluate(leaf,`({clicks:Number(document.body.dataset.geometryClicks),result:document.querySelector('#result').textContent})`);
    if(effect.value?.clicks===1)break;await pause(40);
  }
  geometryEvidence.effect=effect;
  geometryEvidence.rootHit=await call('evaluate',{script:`document.elementFromPoint(${outerMapping.value.point.x},${outerMapping.value.point.y})?.id`});
  assert(effect.ok&&effect.value.clicks===1&&effect.value.result==='trusted','Mapped point did not reach the exact nested target with a trusted event');
  const rootOwner=await owner(geometryRoot,{x:10,y:10});assert(!rootOwner.ok&&rootOwner.error.code==='root_has_no_frame_owner','The root frame invented a parent owner');
  for(const point of [{x:-1,y:0},{x:1e9,y:0},{x:null,y:0}]){
    const invalid=await owner(outer,point);assert(!invalid.ok&&invalid.error.code==='frame_point_invalid','Invalid frame coordinates were accepted');
  }
  await call('evaluate',{script:`(()=>{document.querySelector('#cover').hidden=false;return true})()`});
  const obscured=await owner(outer,innerMapping.value.point);assert(obscured.ok&&!obscured.value.ok&&obscured.value.error.code==='frame_owner_obscured','A parent overlay did not block the frame point');
  await call('evaluate',{script:`(()=>{document.querySelector('#cover').hidden=true;document.querySelector('#outer').style.transform='scale(.95)';return true})()`});
  const transformed=await owner(outer,innerMapping.value.point);assert(transformed.ok&&!transformed.value.ok&&transformed.value.error.code==='unsupported_geometry','Unsupported transformed coordinates were silently approximated');
  await call('evaluate',{script:`(()=>{const frame=document.querySelector('#outer');frame.style.transform='none';frame.style.visibility='hidden';return true})()`});
  const hidden=await owner(outer,innerMapping.value.point);assert(hidden.ok&&!hidden.value.ok&&hidden.value.error.code==='frame_owner_hidden','Hidden parent frame remained actionable');
  await call('evaluate',{script:`(()=>{const frame=document.querySelector('#outer');frame.style.visibility='visible';frame.style.marginLeft='110px';return true})()`});
  const moved=await owner(outer,innerMapping.value.point);assert(moved.ok&&moved.value.ok&&moved.value.geometry[0]!==outerMapping.value.geometry[0],'Owner geometry reused a stale layout snapshot');
  assert((await evaluate(leaf,'Number(document.body.dataset.geometryClicks)')).value===1,'Read-only owner failures repeated the target click');
  stage='frame-input-guard';
  const rejectInput=async(frame_chain)=>{
    let denied=false;try{await call('input',{agent:true,kind:'click',navigation:state.navigation,frame_chain,...outerMapping.value.point});}catch{denied=true;}
    assert(denied,'Invalid or stale native frame chain accepted input');
  };
  for(const invalid of [[],null,[null],'raw-protocol-id',chain.slice(0,2),[chain[1],chain[0],chain[2]],
      [{...chain[0],document:'fd-foreign'},...chain.slice(1)],Array.from({length:33},()=>chain[2])])await rejectInput(invalid);
  const rejectSnapshot=async(frame_snapshot)=>{
    let denied=false;try{await call('input',{agent:true,kind:'move',navigation:state.navigation,frame_snapshot,...outerMapping.value.point});}catch{denied=true;}
    assert(denied,'Invalid or stale visual frame snapshot accepted input');
  };
  for(const invalid of [[],snapshot.slice(1),[snapshot[0],snapshot[0],snapshot[2]],snapshot.map(row=>row.frame===leaf.id?{...row,document:'fd-foreign'}:row),
    snapshot.map(row=>row.frame===leaf.id?{...row,parent:'f0'}:row),'invalid'])await rejectSnapshot(invalid);
  assert((await evaluate(leaf,'Number(document.body.dataset.geometryClicks)')).value===1,'Rejected native frame chains dispatched an extra click');
  await call('evaluate',{script:`(()=>{document.querySelector('#outer').style.marginLeft='80px';return true})()`});
  await evaluate(outer,`document.querySelector('#inner').src=${JSON.stringify(origin+'/geometry-leaf?next=1')};true`);
  let replacementLeaf;
  await until(async()=>{const tree=await frames();replacementLeaf=tree.frames?.find(row=>row.url===origin+'/geometry-leaf?next=1');return tree.ok&&replacementLeaf&&!replacementLeaf.loading;},'Child-only input guard fixture did not navigate');
  assert((await call('state')).navigation===state.navigation,'Child-only navigation changed the top-level token and masked the frame guard');
  await rejectInput(chain);
  await rejectSnapshot(snapshot);
  assert((await evaluate(replacementLeaf,'Number(document.body.dataset.geometryClicks)')).value===0,'Old frame input reached the replacement child document');
  stage='frame-dispatch';let dispatchCounter=0,cancelDispatch=false,inputRequests=0,fileRequests=0,captureRequests=0,cancelHeldFrame=null,navigateHeldFrame=null,dragReplacement=null,afterCapture=null;
  const routedView={execute:async(command,params)=>{
    if(command==='input')inputRequests++;
    if(command==='capture')captureRequests++;
    const result=await call(command,params);
    if(command==='capture'&&afterCapture){const next=afterCapture;afterCapture=null;await next();}
    if(cancelDispatch&&command==='frames'&&params.script?.includes('({local_frame:true}).prepare('))cancelDispatch='cancelled';
    return result;
  }};
  const driver=createFrameDriver({execute:(command,params)=>routedView.execute(command,params)});
  const dispatch=(command,params)=>executeBrowserAutomation({command,params,browser,view:routedView,pageDriver:driver,
    action:{id:'frame-dispatch-'+(++dispatchCounter)},api:{call:async(method,params)=>{
      if(method==='ui.result'){
        if(cancelHeldFrame&&(await evaluate(cancelHeldFrame,'document.body.dataset.dragHeld')).value==='true'){cancelHeldFrame=null;cancelDispatch='cancelled';}
        if(navigateHeldFrame&&(await evaluate(navigateHeldFrame,'document.body.dataset.dragHeld')).value==='true'){
          navigateHeldFrame=null;dragReplacement=await advanceLeaf('during-native-drag');
        }
        return{state:cancelDispatch==='cancelled'?'cancelled':'pending'};
      }
      if(method==='files.digest'||method==='files.read'){fileRequests++;return filesApi.call(method,params);}
      throw Error('Unexpected fixture SDK method');
    }}});
  const page=await dispatch('observe',{limit:100,max_bytes:4096});
  assert(page.result.ok&&page.result.frames.length===3&&page.result.elements.some(row=>row.name==='Nested field'&&row.ref.startsWith('r1|')),'Dispatcher did not use combined frame observations');
  stage='native-scroll-point';
  const originalFocus=(await evaluate(replacementLeaf,'document.activeElement.id')).value;
  const scrollPrepared=await driver.read('prepare',{document:page.result.document,target:{name:'Nested field'},op:'scroll',scroll:true});
  assert(scrollPrepared.ok&&scrollPrepared.frame_chain.length===3,'The nested scroll target did not retain its complete original chain');
  const scrollPoint=await driver.read('scrollPoint',{document:page.result.document,point:scrollPrepared.point});
  assert(scrollPoint.ok&&scrollPoint.document===page.result.document&&JSON.stringify(scrollPoint.frame_chain)===JSON.stringify(scrollPrepared.frame_chain),
    'The actual native scroll point did not route through both original iframe owners');
  assert(scrollPoint.native_target.document===scrollPrepared.native_target.document&&scrollPoint.native_target.ref===scrollPrepared.native_target.ref&&scrollPoint.hit_ref===scrollPrepared.element.ref,
    'The native scroll route substituted a root document or another leaf hit');
  assert((await evaluate(replacementLeaf,'document.activeElement.id')).value===originalFocus,'Read-only scroll routing changed DOM focus');
  stage='frame-dispatch';
  const filled=await dispatch('act',{op:'fill',document:page.result.document,target:{name:'Nested field'},text:'Frame 🦉 input'});
  assert(filled.result.ok&&filled.result.verification==='field_state','Routed nested fill did not complete');
  assert((await evaluate(replacementLeaf,`document.querySelector('#field').value`)).value==='Frame 🦉 input','Routed native Unicode text did not reach its exact nested input');
  await call('evaluate',{script:`(()=>{document.querySelector('#root-focus').focus();return true})()`});
  const blurred=await driver.read('verify',{document:page.result.document,target:{name:'Nested field'},op:'fill',text:'Frame 🦉 input'});
  assert(blurred.ok&&blurred.verified&&!blurred.focused,'An unfocused ancestor still authorized keyboard input');
  await evaluate(replacementLeaf,`(()=>{document.querySelector('#field').addEventListener('keydown',e=>{if(e.ctrlKey&&e.key.toLowerCase()==='a')document.querySelector('#field').blur()},{once:true});return true})()`);
  const focusChanged=await dispatch('act',{op:'fill',document:page.result.document,target:{name:'Nested field'},text:'must-not-be-written'});
  assert(!focusChanged.result.ok&&focusChanged.result.error.code==='focus_changed','Selection-time focus change did not stop replacement text');
  assert((await evaluate(replacementLeaf,`document.querySelector('#field').value`)).value==='Frame 🦉 input','Replacement text was sent after selection changed focus');
  const optionPage=await dispatch('observe',{mode:'options',document:page.result.document,target:{label:'Nested choice'}});
  geometryEvidence.optionPage=optionPage;
  assert(optionPage.result.ok&&optionPage.result.options.length===2&&optionPage.result.ref.startsWith('r1|'),'Routed option page lost its frame identity');
  const selected=await dispatch('act',{op:'select',document:page.result.document,target:{label:'Nested choice'},choices:[{index:1}]});
  assert(selected.result.ok&&selected.result.verified&&selected.result.input_method==='dom_select','Routed selection transaction failed');
  assert((await evaluate(replacementLeaf,`document.querySelector('#choice').selectedIndex`)).value===1,'Routed selection changed the wrong frame');
  const waited=await dispatch('wait_for',{until:'visible',document:page.result.document,target:{name:'Nested field'},timeout_ms:2000});
  assert(waited.result.ok&&waited.result.matched&&waited.result.element.ref.startsWith('r1|'),'Routed wait did not resolve a qualified frame reference');
  const uploadTarget=await driver.read('uploadInfo',{document:page.result.document,target:{name:'Hidden nested upload'}});
  assert(uploadTarget.ok&&uploadTarget.element.hidden&&uploadTarget.native_target.frame_chain.length===3,'Hidden cross-frame upload preparation lost its native pins');
  const captureProof=await evaluate(replacementLeaf,`(()=>{
    document.querySelector('#field').style.background='rgb(250,20,150)';
    const proof=document.createElement('div');proof.id='capture-proof';proof.role='img';proof.setAttribute('aria-label','Frame screenshot proof');proof.style.cssText='position:absolute;left:280px;top:10px;width:40px;height:30px;background:rgb(20,180,70)';proof.dataset.visionClicks='0';proof.onclick=e=>{proof.dataset.visionClicks=String(Number(proof.dataset.visionClicks)+1);proof.dataset.visionTrusted=String(e.isTrusted)};document.body.append(proof);return true;
  })()`);assert(captureProof.ok&&captureProof.value,'Nested screenshot pixel fixture did not initialize');
  const fieldPoint=await driver.read('prepare',{document:page.result.document,target:{name:'Nested field'},op:'click',scroll:false});
  const proofPoint=await driver.read('prepare',{document:page.result.document,target:{css:'#capture-proof'},op:'click',scroll:false});
  assert(fieldPoint.ok&&proofPoint.ok,'Nested screenshot sample coordinates could not be pinned');
  const capture=await dispatch('capture',{document:page.result.document,max_width:600,max_height:400});
  assert(capture.result.ok&&capture.result.image.media_type==='image/png'&&capture.result.redaction==='form-controls-and-opaque-content','Root capture lost its existing privacy mask');
  const sampleImage=async(result,points)=>{
    const bitmap=await createImageBitmap(new Blob([Uint8Array.from(atob(result.image.base64),c=>c.charCodeAt(0))],{type:'image/png'}));
    try{const canvas=document.createElement('canvas');canvas.width=bitmap.width;canvas.height=bitmap.height;const context=canvas.getContext('2d');context.drawImage(bitmap,0,0);
      return points.map(point=>{const x=Math.floor((point.x-result.region.x)*canvas.width/result.region.width),y=Math.floor((point.y-result.region.y)*canvas.height/result.region.height);return[...context.getImageData(x,y,1,1).data].slice(0,3);});
    }finally{bitmap.close();}
  };
  const screenshotPixels=await sampleImage(capture.result,[proofPoint.point,fieldPoint.point]);
  geometryEvidence.capturePixels=screenshotPixels;
  assert(screenshotPixels[0].every((value,index)=>value===[20,180,70][index]),'Readable nested frame pixels remained under the whole-iframe opaque mask');
  assert(screenshotPixels[1].every(value=>value===32),'Nested private input pixels were exposed after frame composition');
  const nestedImage=await dispatch('capture',{mode:'element',document:page.result.document,target:{css:'#capture-proof'},max_width:100,max_height:100});
  geometryEvidence.nestedCapture={ok:nestedImage.result.ok,error:nestedImage.result.error,region:nestedImage.result.region};
  assert(nestedImage.result.ok&&nestedImage.result.region.width>=40&&nestedImage.result.region.width<=42&&nestedImage.result.region.height>=30&&nestedImage.result.region.height<=32,'Nested element screenshot was not cropped through its original ancestors');
  assert((await sampleImage(nestedImage.result,[proofPoint.point]))[0].every((value,index)=>value===[20,180,70][index]),'Nested element crop sampled the wrong frame pixels');
  stage='visual-pointer';const beforeVisualInput=inputRequests;
  const visualArgs={op:'click',document:page.result.document,target:{image:nestedImage.result.visual_reference.id,x:10,y:8},timeout_ms:15000};
  const visualClick=await dispatch('act',visualArgs);
  geometryEvidence.visualClick={ok:visualClick.result.ok,error:visualClick.result.error};
  assert(visualClick.result.ok&&visualClick.result.scene_validated&&inputRequests===beforeVisualInput+1,'A fresh nested visual image did not deliver one native input');
  const visualEffect=await evaluate(replacementLeaf,`({count:document.querySelector('#capture-proof').dataset.visionClicks,trusted:document.querySelector('#capture-proof').dataset.visionTrusted})`);
  assert(visualEffect.value.count==='1'&&visualEffect.value.trusted==='true','Visual coordinates did not reach the exact nested proof with one trusted click');
  const repeatedVisual=await dispatch('act',visualArgs);
  assert(!repeatedVisual.result.ok&&repeatedVisual.result.error.code==='visual_reference_expired'&&inputRequests===beforeVisualInput+1,'A consumed screenshot reference repeated visual input');
  const changedScene=await dispatch('capture',{mode:'element',document:page.result.document,target:{css:'#capture-proof'},max_width:100,max_height:100});
  assert(changedScene.result.ok,'The visual scene-change fixture could not capture its original pixels');
  await evaluate(replacementLeaf,`document.querySelector('#capture-proof').style.background='rgb(30,50,200)';true`);
  const rejectedScene=await dispatch('act',{...visualArgs,target:{image:changedScene.result.visual_reference.id,x:10,y:8}});
  geometryEvidence.visualScene={ok:rejectedScene.result.ok,error:rejectedScene.result.error};
  assert(!rejectedScene.result.ok&&rejectedScene.result.error.code==='visual_scene_changed'&&inputRequests===beforeVisualInput+1,'A changed visual neighborhood still dispatched input');
  await evaluate(replacementLeaf,`document.querySelector('#capture-proof').style.background='rgb(20,180,70)';true`);
  const maskedScene=await dispatch('capture',{mode:'element',document:page.result.document,target:{name:'Nested field'},max_width:300,max_height:100});
  assert(maskedScene.result.ok,'The visual privacy fixture could not capture its masked field');
  const maskedVisual=await dispatch('act',{...visualArgs,target:{image:maskedScene.result.visual_reference.id,x:5,y:5}});
  assert(!maskedVisual.result.ok&&maskedVisual.result.error.code==='visual_region_masked'&&inputRequests===beforeVisualInput+1,'A masked private field admitted visual point input');
  const beforeCancelledInput=inputRequests;cancelDispatch=true;
  const cancelled=await dispatch('act',{op:'click',document:page.result.document,target:{name:'Nested geometry action'}});
  assert(!cancelled.result.ok&&cancelled.result.error.code==='cancelled'&&!cancelled.result.error.effect_may_have_occurred&&inputRequests===beforeCancelledInput,'An internal frame checkpoint was swallowed and allowed input');
  cancelDispatch=false;
  stage='framed-upload';
  const sourcePath='.mdo/qa/frame-upload-qa.bin',sourceBytes=Uint8Array.from({length:131075},(_,i)=>i%251);
  const expectedHash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',sourceBytes)),n=>n.toString(16).padStart(2,'0')).join('');
  const uploadOptions={document:page.result.document,target:{name:'Hidden nested upload'}};
  const originalUploadTarget=await driver.read('uploadInfo',uploadOptions);
  const upload=await dispatch('upload',{action:'set',...uploadOptions,files:[{path:sourcePath,space:'workspace',name:'frame-中文.bin'}],timeout_ms:15000});
  geometryEvidence.upload=upload;
  assert(upload.result.ok&&upload.result.verified&&upload.result.retained&&upload.result.input_method==='native_file_input','Routed hidden OOPIF upload was not verified');
  await until(async()=>{const result=await evaluate(replacementLeaf,'document.body.dataset.uploadHash');return result.ok&&result.value===expectedHash;},'Nested document did not consume the exact complete file bytes');
  const uploadEvents=await evaluate(replacementLeaf,'JSON.parse(document.body.dataset.uploadEvents)');
  geometryEvidence.uploadEvents=uploadEvents;
  const exactEvents=(events,trust)=>Array.isArray(events)&&events.length===trust.length&&events.every((event,i)=>event.type===(i%2?'change':'input')&&event.trusted===trust[i]);
  assert(exactEvents(uploadEvents.value,[true,true]),'Native framed upload did not deliver exactly one trusted event pair');
  const cleared=await dispatch('upload',{action:'clear',...uploadOptions,timeout_ms:15000});
  geometryEvidence.cleared=cleared;
  assert(cleared.result.ok&&cleared.result.verified&&cleared.result.cleared&&cleared.result.events_trusted===false,'Routed hidden OOPIF clear was not verified');
  const clearEvents=await evaluate(replacementLeaf,'({count:document.querySelector("#files").files.length,events:JSON.parse(document.body.dataset.uploadEvents)})');
  geometryEvidence.clearEvents=clearEvents;
  assert(clearEvents.value.count===0&&exactEvents(clearEvents.value.events,[true,true,false,false]),'Framed clear did not deliver exactly its documented synthetic event pair');
  const ticket=upload.result.transfer.ticket,oldAssignment={action:'assign',ticket,navigation:state.navigation,...originalUploadTarget.native_target};
  const duplicate=await call('upload',oldAssignment);
  assert(duplicate.accepted&&duplicate.verified&&duplicate.retained,'Completed assignment did not retain its original receipt');
  assert((await evaluate(replacementLeaf,'JSON.parse(document.body.dataset.uploadEvents).length')).value===4,'Receipt lookup replayed the upload after clear');
  const advanceLeaf=async suffix=>{
    const url=origin+'/geometry-leaf?upload='+suffix;await evaluate(outer,`document.querySelector('#inner').src=${JSON.stringify(url)};true`);let next;
    await until(async()=>{const current=await frames();next=current.frames?.find(row=>row.url===url);return current.ok&&next&&!next.loading;},'Upload child navigation did not settle');
    assert((await call('state')).navigation===state.navigation,'Upload child navigation changed the top-level token');return next;
  };
  const afterUpload=await advanceLeaf('after-assignment');
  const oldReceipt=await call('upload',oldAssignment);
  assert(oldReceipt.accepted&&oldReceipt.verified&&oldReceipt.retained,'Child navigation erased an already dispatched upload receipt');
  assert((await evaluate(afterUpload,'document.querySelector("#files").files.length+JSON.parse(document.body.dataset.uploadEvents).length')).value===0,'Old upload receipt was replayed into a replacement child');
  const newTarget=await driver.read('uploadInfo',uploadOptions);
  const conflict=await call('upload',{...oldAssignment,frame_chain:newTarget.native_target.frame_chain});
  assert(!conflict.accepted&&conflict.error.code==='upload_identity_conflict','An old transfer ticket accepted a different frame document chain');
  const released=await call('upload',{action:'release',ticket,confirmed:true});assert(released.released,'Consumed framed upload bytes were not released');
  stage='framed-upload-navigation-before-dispatch';
  const emptyHash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new Uint8Array())),n=>n.toString(16).padStart(2,'0')).join('');
  const staged=await call('upload',{action:'begin',upload_key:'framed-undispatched',navigation:state.navigation,...newTarget.native_target,
    files:[{name:'never-assigned.bin',size:0,sha256:emptyHash}],timeout_ms:10000});
  assert((await call('upload',{action:'seal',ticket:staged.ticket,navigation:state.navigation})).sealed,'Undispatched frame transfer did not seal');
  const afterStaging=await advanceLeaf('after-staging');
  const staleAssignment=await call('upload',{action:'assign',ticket:staged.ticket,navigation:state.navigation,...newTarget.native_target});
  assert(!staleAssignment.accepted&&!staleAssignment.dispatched&&['upload_not_ready','upload_ticket_missing'].includes(staleAssignment.error.code),'Staged upload crossed its original child document boundary');
  assert((await evaluate(afterStaging,'document.querySelector("#files").files.length+JSON.parse(document.body.dataset.uploadEvents).length')).value===0,'Rejected staged upload reached a replacement child');
  stage='framed-picker';
  const openPicker=async()=>{
    const opened=await dispatch('act',{op:'click',document:page.result.document,target:{name:'Choose nested file'}});
    geometryEvidence.pickerOpened=opened;
    assert(opened.result.ok&&opened.result.outcome==='file_chooser_open'&&!opened.result.completed,'Nested trusted click did not intercept its file picker');
    const listed=await dispatch('chooser',{action:'list'});assert(listed.result.ok&&listed.result.choosers.length===1,'Nested picker was not retained exactly once');
    return listed.result.choosers[0];
  };
  let picker=await openPicker();
  assert(picker.frame===afterStaging.id&&picker.frame_document===afterStaging.document,'Native picker metadata lost its original child frame document');
  const beforeRepeat=inputRequests,blocked=await dispatch('act',{op:'click',document:page.result.document,target:{name:'Choose nested file'}});
  assert(blocked.result.outcome==='file_chooser_open'&&inputRequests===beforeRepeat,'A pending picker repeated its opening click');
  const picked=await dispatch('upload',{action:'set',document:page.result.document,chooser:picker.id,files:[{path:sourcePath,space:'workspace',name:'picker-中文.bin'}],timeout_ms:15000});
  geometryEvidence.picked=picked;
  assert(picked.result.ok&&picked.result.verified&&picked.result.transfer.retained,'The actual nested picker did not accept its frame-owned upload');
  await until(async()=>{const result=await evaluate(afterStaging,'document.body.dataset.uploadHash');return result.value===expectedHash;},'Nested picker did not consume the complete verified file bytes');
  assert(exactEvents((await evaluate(afterStaging,'JSON.parse(document.body.dataset.uploadEvents)')).value,[true,true]),'Nested picker upload duplicated or synthesized its event pair');
  assert((await call('chooser',{action:'list',agent:true})).choosers.length===0,'Consumed nested picker remained pending');
  assert((await call('upload',{action:'release',ticket:picked.result.transfer.ticket,confirmed:true})).released,'Consumed picker transfer was not released');
  picker=await openPicker();
  const pickerCancelled=await dispatch('chooser',{action:'cancel',chooser:picker.id});
  assert(pickerCancelled.result.ok&&pickerCancelled.result.cancel_event_sent&&pickerCancelled.result.events_trusted===false,'Nested picker cancel did not reach its original process');
  assert(JSON.stringify((await evaluate(afterStaging,'JSON.parse(document.body.dataset.cancelEvents)')).value)==='[false]','Nested picker cancellation did not emit exactly its documented synthetic cancel event');
  picker=await openPicker();await call('takeover');
  await until(async()=>{const result=await call('chooser',{action:'list'});return result.ok&&!result.changing&&!result.armed&&!result.choosers.length;},'Manual control did not disarm all nested picker sessions');
  assert(JSON.stringify((await call('evaluate',{script:'true'})).value)==='true','Manual browser stopped being inspectable by the trusted host');
  await call('release');
  assert(JSON.stringify((await evaluate(afterStaging,'JSON.parse(document.body.dataset.cancelEvents)')).value)==='[false,false]','Manual control lost or duplicated cancellation of the pending nested picker');
  picker=await openPicker();const afterPicker=await advanceLeaf('pending-picker-navigation');
  assert((await call('chooser',{action:'list',agent:true})).choosers.length===0,'Navigated nested picker remained usable');
  const obsolete=await driver.read('chooserInfo',{document:page.result.document,chooser:picker.id});
  assert(!obsolete.ok&&obsolete.error.code==='chooser_changed','An old picker resolved in a replacement child document');
  assert((await evaluate(afterPicker,'JSON.parse(document.body.dataset.cancelEvents).length+document.querySelector("#files").files.length')).value===0,'Picker cleanup sent an old cancel or file assignment into the replacement document');
  stage='new-oopif-picker-policy';const newFrameUrl=origin+'/geometry-leaf?upload=new-oopif';
  await evaluate(outer,`(()=>{const old=document.querySelector('#inner'),next=old.cloneNode(false);next.src=${JSON.stringify(newFrameUrl)};old.replaceWith(next);return true})()`);
  let newLeaf;await until(async()=>{const current=await frames();newLeaf=current.frames?.find(row=>row.url===newFrameUrl);return current.ok&&newLeaf&&!newLeaf.loading&&newLeaf.isolated;},'Replacement OOPIF session did not become ready');
  assert(newLeaf.id!==afterPicker.id&&newLeaf.separate_process,'New picker fixture did not attach a new cross-process frame');
  picker=await openPicker();assert(picker.frame===newLeaf.id&&picker.frame_document===newLeaf.document,'A newly attached OOPIF did not inherit file-picker suppression');
  assert((await dispatch('chooser',{action:'cancel',chooser:picker.id})).result.cancel_event_sent,'New OOPIF picker could not be cancelled in its original process');
  stage='parent-upload-policy';
  for(const level of ['outer','inner']){
    if(level==='outer')await call('evaluate',{script:`(()=>{document.querySelector('#outer').inert=true;return true})()`});
    else await evaluate(outer,`(()=>{document.querySelector('#inner').inert=true;return true})()`);
    const beforeFiles=fileRequests,denied=await dispatch('upload',{action:'set',...uploadOptions,files:[{path:sourcePath,space:'workspace',name:'not-read.bin'}],timeout_ms:2000});
    assert(!denied.result.ok&&denied.result.error.code==='element_disabled'&&!denied.result.error.effect_may_have_occurred,'An inert iframe ancestor still allowed upload');
    assert(fileRequests===beforeFiles,'An inert iframe upload read local file bytes before failing');
    if(level==='outer')await call('evaluate',{script:`(()=>{document.querySelector('#outer').inert=false;return true})()`});
    else await evaluate(outer,`(()=>{document.querySelector('#inner').inert=false;document.querySelector('#inner').hidden=true;return true})()`);
  }
  const hiddenOwner=await driver.read('uploadInfo',uploadOptions);
  assert(hiddenOwner.ok&&hiddenOwner.native_target.frame_chain.length===3,'A hidden but enabled iframe was confused with an inert upload ancestor');
  await evaluate(outer,`(()=>{document.querySelector('#inner').hidden=false;return true})()`);
  stage='framed-drag';
  const installDrag=async(html=false)=>{
    const setup=await evaluate(newLeaf,`(()=>{
      document.body.innerHTML='<style>body{margin:0}.drag-item{position:absolute;width:90px;height:50px;background:#ddd;user-select:none}#drag-source{left:20px;top:20px}#drag-drop{left:230px;top:120px}</style><div id="drag-source" class="drag-item" role="button" aria-label="Nested drag source" draggable="${html}">Source</div><div id="drag-drop" class="drag-item" role="button" aria-label="Nested drag destination">Drop</div>';
      Object.assign(document.body.dataset,{dragHeld:'false',dragReleases:'0',dragTrusted:'true',dragDx:'0',dragDrops:'0',dragStarts:'0',dragEnds:'0',dragText:''});
      const source=document.querySelector('#drag-source'),drop=document.querySelector('#drag-drop');let start=0;
      source.addEventListener('pointerdown',e=>{if(${html})return;e.preventDefault();start=e.clientX;document.body.dataset.dragHeld='true';document.body.dataset.dragTrusted=String(e.isTrusted);source.setPointerCapture(e.pointerId)});
      source.addEventListener('pointermove',e=>{if(document.body.dataset.dragHeld!=='true')return;document.body.dataset.dragDx=String(e.clientX-start);document.body.dataset.dragTrusted=String(document.body.dataset.dragTrusted==='true'&&e.isTrusted)});
      for(const type of ['pointerup','pointercancel'])source.addEventListener(type,e=>{if(document.body.dataset.dragHeld!=='true')return;document.body.dataset.dragHeld='false';document.body.dataset.dragReleases=String(Number(document.body.dataset.dragReleases)+1);document.body.dataset.dragTrusted=String(document.body.dataset.dragTrusted==='true'&&e.isTrusted)});
      source.addEventListener('dragstart',e=>{document.body.dataset.dragStarts=String(Number(document.body.dataset.dragStarts)+1);document.body.dataset.dragTrusted=String(e.isTrusted);e.dataTransfer.effectAllowed='copyMove';e.dataTransfer.setData('text/plain','frame-drag-qa')});
      source.addEventListener('dragend',()=>{document.body.dataset.dragEnds=String(Number(document.body.dataset.dragEnds)+1)});
      drop.addEventListener('dragover',e=>{e.preventDefault();e.dataTransfer.dropEffect='move'});
      drop.addEventListener('drop',e=>{e.preventDefault();document.body.dataset.dragDrops=String(Number(document.body.dataset.dragDrops)+1);document.body.dataset.dragTrusted=String(document.body.dataset.dragTrusted==='true'&&e.isTrusted);document.body.dataset.dragText=e.dataTransfer.getData('text/plain')});return true;
    })()`);assert(setup.ok&&setup.value,'Nested drag fixture could not initialize');
  };
  const dragEffect=async()=>evaluate(newLeaf,'({...document.body.dataset})');
  const dragParams={op:'drag',document:page.result.document,target:{name:'Nested drag source'},destination:{name:'Nested drag destination'},timeout_ms:5000};
  await installDrag();const pointerDragged=await dispatch('act',dragParams);geometryEvidence.pointerDragged=pointerDragged;
  assert(pointerDragged.result.ok&&pointerDragged.result.drag_kind==='pointer','Nested pointer drag was not delivered');
  assert((await evaluate(newLeaf,"'__mdo_native_drag_watch_v1' in globalThis")).value===false,'Completed pointer drag retained its isolated source observer');
  let dragged=await dragEffect();geometryEvidence.pointerEffect=dragged;
  assert(dragged.value.dragHeld==='false'&&dragged.value.dragReleases==='1'&&dragged.value.dragTrusted==='true'&&Number(dragged.value.dragDx)>100&&dragged.value.dragDrops==='0','Nested pointer drag lost its trusted stream or exact release');
  await installDrag(true);const htmlDragged=await dispatch('act',dragParams);geometryEvidence.htmlDragged=htmlDragged;
  dragged=await dragEffect();geometryEvidence.htmlEffect=dragged;
  assert(htmlDragged.result.ok&&htmlDragged.result.drag_kind==='html5','Nested HTML5 drag did not retain its intercepted browser payload');
  assert(dragged.value.dragStarts==='1'&&dragged.value.dragEnds==='1'&&dragged.value.dragDrops==='1'&&dragged.value.dragText==='frame-drag-qa'&&dragged.value.dragTrusted==='true','Nested HTML5 drag did not reach its exact target once with trusted events');
  assert((await evaluate(newLeaf,"'__mdo_native_drag_watch_v1' in globalThis")).value===false,'Completed HTML drag retained its source observer');
  stage='cross-document-drag';
  const rootDragSetup=await call('evaluate',{script:`(()=>{
    document.querySelectorAll('.root-drag-qa').forEach(node=>node.remove());
    const target=document.createElement('div');target.className='root-drag-qa';target.id='root-drop';target.role='button';target.setAttribute('aria-label','Root drag destination');
    target.style.cssText='position:absolute;left:700px;top:100px;width:120px;height:60px;background:#ddd';target.textContent='Root drop';document.body.append(target);
    Object.assign(document.body.dataset,{rootDragDrops:'0',rootDragText:'',rootDragTrusted:'false',rootDragStarts:'0',rootDragEnds:'0'});
    target.addEventListener('dragover',e=>{e.preventDefault();e.dataTransfer.dropEffect='move'});target.addEventListener('drop',e=>{e.preventDefault();document.body.dataset.rootDragDrops=String(Number(document.body.dataset.rootDragDrops)+1);document.body.dataset.rootDragText=e.dataTransfer.getData('text/plain');document.body.dataset.rootDragTrusted=String(e.isTrusted)});
    const source=target.cloneNode(true);source.id='root-source';source.setAttribute('aria-label','Root drag source');source.style.top='240px';source.draggable=true;source.textContent='Root source';document.body.append(source);
    source.addEventListener('dragstart',e=>{document.body.dataset.rootDragStarts=String(Number(document.body.dataset.rootDragStarts)+1);e.dataTransfer.effectAllowed='copyMove';e.dataTransfer.setData('text/plain','frame-drag-qa')});source.addEventListener('dragend',()=>{document.body.dataset.rootDragEnds=String(Number(document.body.dataset.rootDragEnds)+1)});return true;
  })()`});assert(rootDragSetup.ok&&rootDragSetup.value,'Cross-document drag fixture could not initialize');
  await installDrag(true);const toRoot=await dispatch('act',{...dragParams,destination:{name:'Root drag destination'}});geometryEvidence.dragToRoot=toRoot;
  const rootDrop=await call('evaluate',{script:'({...document.body.dataset})'});geometryEvidence.rootDrop=rootDrop;
  assert(toRoot.result.ok&&toRoot.result.drag_kind==='html5'&&rootDrop.value.rootDragDrops==='1'&&rootDrop.value.rootDragText==='frame-drag-qa'&&rootDrop.value.rootDragTrusted==='true','OOPIF-to-root HTML5 drag did not deliver exactly one trusted drop');
  assert((await dragEffect()).value.dragEnds==='1','OOPIF-to-root drag did not terminate its source');
  await installDrag(true);const fromRoot=await dispatch('act',{...dragParams,target:{name:'Root drag source'}});geometryEvidence.dragFromRoot=fromRoot;
  dragged=await dragEffect();geometryEvidence.dragFromRootEffect=dragged;
  const rootSource=await call('evaluate',{script:'({starts:document.body.dataset.rootDragStarts,ends:document.body.dataset.rootDragEnds})'});
  geometryEvidence.rootDragSourceTermination=rootSource;
  assert((await evaluate(geometryRoot,"'__mdo_native_drag_watch_v1' in globalThis")).value===false,'Root source observer survived cross-document drag cleanup');
  assert(fromRoot.result.ok&&fromRoot.result.drag_kind==='html5'&&dragged.value.dragDrops==='1'&&dragged.value.dragText==='frame-drag-qa'&&dragged.value.dragTrusted==='true'&&rootSource.value.starts==='1'&&rootSource.value.ends==='1','Root-to-OOPIF drag lost its trusted payload or source termination');
  stage='frame-drag-cancel-and-navigation';
  const visualDragArgs=async()=>{
    const image=await dispatch('capture',{document:page.result.document,max_width:1200,max_height:1000});assert(image.result.ok,'Visual drag image was not completed');
    const pin=async(target)=>{const located=await driver.read('prepare',{document:page.result.document,target,op:'drag',scroll:false});assert(located.ok,'Visual drag endpoint was not visible');
      return{image:image.result.visual_reference.id,x:Math.round(located.point.x*image.result.image.width/image.result.region.width),y:Math.round(located.point.y*image.result.image.height/image.result.region.height)};};
    return{...dragParams,target:await pin(dragParams.target),destination:await pin(dragParams.destination)};
  };
  await installDrag();const visualPointer=await dispatch('act',await visualDragArgs());geometryEvidence.visualPointerDrag=visualPointer;
  assert(visualPointer.result.ok&&visualPointer.result.scene_validated&&visualPointer.result.drag_kind==='pointer','Nested image-point pointer drag failed');
  await installDrag(true);const visualHtml=await dispatch('act',await visualDragArgs());geometryEvidence.visualHtmlDrag=visualHtml;
  assert(visualHtml.result.ok&&visualHtml.result.drag_kind==='html5'&&(await dragEffect()).value.dragDrops==='1','Nested image-point HTML drag lost its native payload');
  await installDrag();const visualCancelledArgs=await visualDragArgs();cancelHeldFrame=newLeaf;
  const visualCancelled=await dispatch('act',visualCancelledArgs);geometryEvidence.visualCancelledDrag=visualCancelled;
  assert(!visualCancelled.result.ok&&visualCancelled.result.error.code==='cancelled'&&visualCancelled.result.error.effect_may_have_occurred,'Image-point drag did not retain originating cancellation');cancelDispatch=false;
  await until(async()=>!(await call('state')).input_busy,'Cancelled visual drag retained its native slot');
  const visualCancelledEffect=await dragEffect();assert(visualCancelledEffect.value.dragHeld==='false'&&visualCancelledEffect.value.dragReleases==='1'&&visualCancelledEffect.value.dragDrops==='0','Cancelled image-point drag leaked its press or drop');
  const staleVisualSnapshot=(await call('frames',{action:'list',agent:true})).frames.map(row=>({frame:row.id,document:row.document,parent:row.parent||''}));
  staleVisualSnapshot.find(row=>row.frame!==newLeaf.id).document='fd-00000000-0000-4000-8000-000000000099';
  const staleVisualDrag=await call('drag',{agent:true,drag_key:'stale-visual-drag',navigation:state.navigation,x:100,y:100,to_x:200,to_y:200,frame_snapshot:staleVisualSnapshot});
  assert(!staleVisualDrag.accepted&&staleVisualDrag.dispatched===false,'A changed screenshot sibling admitted native drag input');
  await installDrag();cancelHeldFrame=newLeaf;
  const cancelledDrag=await dispatch('act',dragParams);geometryEvidence.cancelledDrag=cancelledDrag;
  assert(!cancelledDrag.result.ok&&cancelledDrag.result.error.code==='cancelled'&&cancelledDrag.result.error.effect_may_have_occurred,'The original receipt did not cancel an admitted nested drag');cancelDispatch=false;
  await until(async()=>!(await call('state')).input_busy,'Cancelled nested drag did not release the native input slot');
  dragged=await dragEffect();geometryEvidence.cancelledDragEffect=dragged;
  assert(dragged.value.dragHeld==='false'&&dragged.value.dragReleases==='1'&&dragged.value.dragDrops==='0','Cancelled nested drag left a button down or issued a drop');
  const invalidDrag=await call('drag',{agent:true,drag_key:'stale-frame-drag',navigation:state.navigation,x:100,y:100,to_x:200,to_y:200,
    source_frame_chain:[{frame:newLeaf.id,document:'fd-foreign'}],destination_frame_chain:[{frame:'f0',document:geometryRoot.document}]});
  assert(!invalidDrag.accepted&&invalidDrag.dispatched===false,'A stale native drag frame chain was not rejected before input');
  await installDrag();navigateHeldFrame=newLeaf;
  const navigatedDrag=await dispatch('act',dragParams);geometryEvidence.navigatedDrag=navigatedDrag;
  assert(dragReplacement&&!navigatedDrag.result.ok&&navigatedDrag.result.error.effect_may_have_occurred,'Child navigation during an admitted drag lost effect uncertainty');
  await until(async()=>!(await call('state')).input_busy,'Child navigation left the native drag input slot occupied');
  assert((await evaluate(dragReplacement,'Number(document.body.dataset.geometryClicks)')).value===0,'Drag cleanup issued a new click into the replacement document');
  const afterDrag=await dispatch('act',{op:'click',document:page.result.document,target:{name:'Nested geometry action'},timeout_ms:5000});
  assert(afterDrag.result.ok&&(await evaluate(dragReplacement,'Number(document.body.dataset.geometryClicks)')).value===1,'Native input did not recover after the child-navigation drag interruption');
  stage='capture-owner-bindings';
  const rootArea=await evaluate(geometryRoot,`(${runtime})({local_frame:true}).captureArea({mode:'viewport'})`),outerArea=await evaluate(outer,`(${runtime})({local_frame:true}).captureArea({mode:'viewport'})`);
  assert(rootArea.ok&&rootArea.value.ok&&outerArea.ok&&outerArea.value.ok,'Local frame privacy masks could not be prepared');
  const rootMasks=rootArea.value.redactions.filter(row=>row.frame_ref),outerMasks=outerArea.value.redactions.filter(row=>row.frame_ref);
  assert(rootMasks.length===1&&outerMasks.length===1,'Local capture traversed or omitted an iframe instead of making one opaque node mask');
  const captureOwner=frame=>call('frames',{action:'capture_owner',agent:true,frame:frame.id,document:frame.document});
  const outerCapture=await captureOwner(outer),innerCapture=await captureOwner(dragReplacement);
  geometryEvidence.captureOwners={outerCapture,innerCapture};
  assert(outerCapture.ok&&outerCapture.value.ok&&outerCapture.value.owner.document===rootArea.value.document&&outerCapture.value.owner.ref===rootMasks[0].frame_ref,'Native outer owner did not bind the exact root-world iframe mask');
  assert(innerCapture.ok&&innerCapture.value.ok&&innerCapture.value.owner.document===outerArea.value.document&&innerCapture.value.owner.ref===outerMasks[0].frame_ref,'Native nested owner did not bind the exact parent-world iframe mask');
  assert(outerCapture.parent==='f0'&&innerCapture.parent===outer.id&&outerCapture.value.painted&&innerCapture.value.painted,'Capture ownership or painting metadata changed processes');
  assert(Math.abs(outerCapture.value.content.x-expected.value.x)<.01&&Math.abs(outerCapture.value.content.y-expected.value.y)<.01,'Capture owner mapping lost fractional border/padding precision');
  await call('evaluate',{script:`(()=>{document.querySelector('#outer').inert=true;return true})()`});
  const inertCapture=await captureOwner(outer),inertArea=await evaluate(geometryRoot,`(${runtime})({local_frame:true}).captureArea({mode:'viewport'})`);
  assert(inertCapture.ok&&inertCapture.value.ok&&inertCapture.value.painted&&inertArea.value.redactions.some(row=>row.frame_ref===inertCapture.value.owner.ref),'An inert painted iframe lost its privacy mask/owner mapping');
  await call('evaluate',{script:`(()=>{document.querySelector('#outer').inert=false;document.querySelector('#outer').style.transform='scale(.95)';return true})()`});
  const unsupportedCapture=await captureOwner(outer);
  assert(unsupportedCapture.ok&&!unsupportedCapture.value.ok&&unsupportedCapture.value.error.code==='unsupported_geometry'&&unsupportedCapture.value.owner.ref===rootMasks[0].frame_ref,'Unsupported capture geometry lost the exact opaque-mask identity');
  await call('evaluate',{script:`(()=>{document.querySelector('#outer').style.transform='none';return true})()`});
  const oldCapture=await captureOwner(newLeaf);assert(!oldCapture.ok&&oldCapture.error.code==='stale_frame','A capture owner accepted an earlier child document');
  stage='capture-document-change';afterCapture=()=>advanceLeaf('after-screenshot');
  const changedImage=await dispatch('capture',{document:page.result.document,max_width:500,max_height:400,timeout_ms:15000});
  geometryEvidence.changedImage={ok:changedImage.result.ok,error:changedImage.result.error,hasImage:!!changedImage.result.image};
  assert(!changedImage.result.ok&&changedImage.result.error.code==='capture_changed'&&!changedImage.result.image,'Child navigation between screenshot privacy scans returned an image with mismatched document masks');
  stage='capture-viewport-settling';await navigate(origin+'/foreign');
  await call('bounds',{visible:true,bounds:{x:0,y:50,width:1000,height:650,scale:1}});
  await until(async()=>{const current=await driver.read('observe',{limit:5,max_bytes:256});return current.ok&&current.viewport.width===1000;},'The initial capture viewport did not settle');
  const capturePage=await dispatch('observe',{limit:5,max_bytes:256}),beforeResizeCapture=captureRequests;
  afterCapture=async()=>{
    await call('bounds',{visible:true,bounds:{x:0,y:50,width:900,height:650,scale:1}});
    await until(async()=>{const current=await driver.read('observe',{limit:5,max_bytes:256});return current.ok&&current.viewport.width===900;},'The forced native viewport change did not arrive');
  };
  const settledImage=await dispatch('capture',{document:capturePage.result.document,max_width:500,max_height:400,timeout_ms:15000});
  assert(settledImage.result.ok&&settledImage.result.visual_reference&&settledImage.result.region.width===900,'A bounded native viewport retry did not return the new stable image');
  assert(captureRequests-beforeResizeCapture===2,'Viewport settling did not use exactly two native captures in its original operation');
  assert(settledImage.result.document===capturePage.result.document&&!(await call('state')).capture_busy,'Viewport settling changed document or retained a native capture slot');
  report={passed:true,checks,backend:'actual-webview2-frame-sessions',frames:4,oopif_confirmed:true,nested_frame:true,isolated_runtime:true,
    foreign_view_denied:true,navigation_invalidates_old_document:true,evaluation_uncertainty_retained:true,manual_control:true,close_cancels_pending:true,recreated_view_rejects_old_identity:true,bounded_frames_recover_after_navigation:true,bounded_oopif_sessions:true,
    combined_observation:true,global_ambiguity:true,qualified_references:true,native_owner_geometry:true,nested_trusted_input:true,native_frame_input_guard:true,
    routed_dispatcher:true,native_nested_scroll_point:true,scroll_route_retains_leaf_identity:true,scroll_route_does_not_focus:true,
    nested_native_fill:true,ancestor_focus:true,selection_focus_fence:true,routed_options_and_selection:true,visual_frame_snapshot_guard:true,
    framed_hidden_upload:true,framed_clear:true,complete_upload_sha256:true,exact_upload_events:true,upload_receipt_without_replay:true,child_navigation_cancels_staging:true,
    frame_owned_picker:true,picker_bound_upload:true,picker_cancel_in_own_process:true,manual_disarms_frame_pickers:true,child_navigation_expires_picker:true,new_oopif_inherits_picker_policy:true,
    inert_parent_upload_denied_before_file_reads:true,hidden_enabled_upload_owner_supported:true,
    nested_pointer_drag:true,nested_html5_drag:true,cross_document_html5_drag_both_directions:true,frame_drag_cancellation_releases_mouse:true,frame_drag_pre_dispatch_guard:true,
    child_navigation_stops_drag:true,input_recovers_after_interrupted_frame_drag:true,
    exact_iframe_mask_bindings:true,local_capture_does_not_enter_children:true,capture_owner_fractional_geometry:true,inert_capture_owner_preserved:true,
    cross_frame_capture_pixels:true,nested_input_pixels_masked:true,nested_element_image_crop:true,changed_frame_image_withheld:true,
    internal_visual_pointer_once:true,consumed_image_rejected:true,changed_visual_tile_rejected:true,masked_visual_point_denied:true,
    viewport_capture_retries_in_same_operation:true,only_stable_viewport_image_published:true};
}catch(error){report={passed:false,stage,checks,error:String(error.message||error).slice(0,512),inventory:lastInventory,geometry_evidence:geometryEvidence};}
finally{for(const id of [browser,foreign])try{await call('close',{},id);}catch{}bridge.dispose();}
await api.call('storage.write',{path:'qa/native-frames.json',text:JSON.stringify(report)});document.querySelector('#status').textContent=report.passed?'Native frame checks passed':report.error;
