"""Run the page engine in the project's real WebView2, then reuse headless mode."""
import argparse
import json
from pathlib import Path
import sys
import time

import dev

ROOT = Path(__file__).resolve().parents[1]
REPORT = {"extension": "browser-use-qa"}
REPORT_PATH = "qa/page-observation.json"

SCRIPT = r'''
import { createNativeBrowserBridge } from '/js/features/workbench/native-browser.js';
import { createBrowserDialogs } from '/js/features/workbench/dialog-ui.js';
import { prepareScreenshot } from '/js/features/workbench/capture-image.js';
const runtimeSource = RUNTIME_SOURCE;
const expiryProbe = DIALOG_EXPIRY;
let dialogExpiryMs = null;
const bridge = createNativeBrowserBridge(), browser = 'browser-use-page-probe';
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let checks=0;
const assert = (test, message) => { checks++; if (!test) throw Error(message); };
let report;
async function trace(phase) {
  const response=await fetch('/api/v1/bootstrap'), token=response.headers.get('x-mdo-write-token');await response.arrayBuffer();
  await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
    body:JSON.stringify({method:'storage.write',context:{extension:'browser-use-qa'},params:{path:'qa/page-trace.json',text:JSON.stringify({phase})}})});
}
async function evaluate(expression) {
  const result = await bridge.call('evaluate', { browser, script: expression });
  assert(result.ok, 'Native evaluation failed');
  return result.value;
}
const engine = async expression => {
  const result=await bridge.call('automation',{browser,agent:true,script:`(${runtimeSource})().${expression}`});
  assert(result.ok,'Native isolated evaluation failed');return result.value;
};
try {
  const raceBrowser='browser-use-creation-race';
  const interrupted=bridge.call('create',{browser:raceBrowser,url:location.origin+'/__browser_use_page__.html?creation=old',visible:false})
    .then(()=>({created:true}),error=>({cancelled:true,message:error.message}));
  await bridge.call('close',{browser:raceBrowser});
  await bridge.call('create',{browser:raceBrowser,url:location.origin+'/__browser_use_page__.html?creation=new',visible:false});
  const oldCreation=await interrupted;
  assert(oldCreation.cancelled,'Closing in-flight creation did not cancel its original callback');
  let replacement;
  for(let i=0;i<100;i++){
    replacement=await bridge.call('state',{browser:raceBrowser});
    if(!replacement.loading)break;await pause(20);
  }
  assert(replacement.url.includes('creation=new'),'A stale creation callback replaced the new native browser');
  await bridge.call('close',{browser:raceBrowser});
  await bridge.call('create', { browser, url:location.origin+'/__browser_use_page__.html',
    visible:true, bounds:{ x:10,y:70,width:1000,height:650,scale:devicePixelRatio } });
  let ready = false;
  for (let i=0;i<100;i++) {
    const snapshot = await bridge.call('snapshot', {browser});
    if (snapshot.value?.text?.includes('Browser Use fixture')) {
      const complete = await evaluate('document.readyState');
      if (complete === 'complete') { ready = true; break; }
    }
    await pause(100);
  }
  assert(ready,'Page did not load');
  await evaluate(`globalThis.__mdo_browser_use_runtime_v1={version:2,documentNode:document,observe:()=>({ok:true,text:'forged observation'})}`);
  const first = await engine('observe({})');
  assert(first.ok && first.document,'No structured observation');
  assert(first.text.includes('Readable fixture text'),'Missing readable text');
  assert(!first.text.includes('forged observation'),'Page-world runtime spoofed isolated observations');
  assert(!JSON.stringify(first).includes('fixture-private-value'),'Password value was exposed');
  assert(!JSON.stringify(first).includes('Hidden action'),'Hidden action was exposed');
  assert(first.elements.some(e=>e.role==='textbox'&&e.name==='订单号'),'Label-based accessible name missing');
  assert(first.elements.some(e=>e.name==='Shadow action'),'Open shadow root was omitted');
  assert(first.elements.some(e=>e.name==='Frame action'&&e.frame!=='f0'),'Same-origin iframe was omitted');
  assert(first.frames.some(f=>!f.dom),'Opaque frame capability was misreported');
  const query=first.elements.find(e=>e.name==='查询');
  assert(query?.ref,'Missing query reference');
  const located=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:query.ref}})})`);
  assert(located.ok && located.element.bounds.width>0,'Reference lookup failed');
  const state=await bridge.call('state',{browser});
  assert(state.automation&&state.navigation,'Native automation capability missing');
  const point=located.element.bounds;
  const clicked=await bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,
    x:point.x+point.width/2,y:point.y+point.height/2});
  assert(clicked.trusted_input,'Click was not dispatched through native input');
  assert(await evaluate('document.body.dataset.clicked')==='trusted','Click event was synthetic');
  assert((await bridge.call('state',{browser})).advanced_mouse,'Advanced native mouse capability missing');
  for(const [button,click_count,expected] of [['left',2,'dblclick:0:true'],['right',1,'contextmenu:2:true'],['middle',1,'auxclick:1:true']]){
    const gesture=await engine(`prepare(${JSON.stringify({document:first.document,op:'click',target:{name:'Mouse gestures'},scroll:true})})`);
    assert(gesture.ok,'Mouse gesture target was not actionable');
    await trace({phase:'native-mouse-'+button,point:gesture.point,hit:await evaluate(`document.elementFromPoint(${gesture.point.x},${gesture.point.y})?.id`)});
    await bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,button,click_count,...gesture.point});
    assert(await evaluate("document.querySelector('#mouse-result').textContent")===expected,'Native '+button+' gesture did not dispatch trusted events');
  }
  for(const fields of [{button:'forged'},{click_count:3},{click_count:1.5}]){
    let invalid=false;try{await bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,x:10,y:10,...fields})}catch{invalid=true}
    assert(invalid,'Invalid native mouse gesture was accepted');
  }
  const input=first.elements.find(e=>e.name==='订单号').bounds;
  await bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,x:input.x+input.width/2,y:input.y+input.height/2});
  await bridge.call('input',{browser,agent:true,kind:'key',navigation:state.navigation,key:'Control+A'});
  await bridge.call('input',{browser,agent:true,kind:'text',navigation:state.navigation,text:'订单 🦉 42'});
  assert(await evaluate('document.querySelector("#order").value')==='订单 🦉 42','Native Unicode replacement failed');
  assert(await evaluate('document.body.dataset.input')==='trusted','Input event was synthetic');
  let staleInput=false;
  try{await bridge.call('input',{browser,agent:true,kind:'click',navigation:'stale',x:10,y:10})}catch{staleInput=true}
  assert(staleInput,'Native input accepted a stale navigation identity');
  await bridge.call('takeover',{browser});
  let manualInput=false;
  try{await bridge.call('input',{browser,agent:true,kind:'text',navigation:state.navigation,text:'must not type'})}catch{manualInput=true}
  assert(manualInput,'Input ignored manual takeover');
  await bridge.call('release',{browser});
  const prepare=options=>engine(`prepare(${JSON.stringify({document:first.document,...options})})`);
  for(const target of [{label:'订单号'},{placeholder:'Order number'},{testid:'order-field'},{css:'#order'}]) {
    assert((await prepare({op:'fill',target})).ok,'Locator failed: '+JSON.stringify(target));
  }
  assert((await prepare({op:'click',target:{css:'#disabled'}})).error?.code==='element_disabled','Disabled button admitted');
  assert((await prepare({op:'fill',target:{name:'Read only'}})).error?.code==='element_not_editable','Read-only field admitted');
  assert((await prepare({op:'click',target:{css:'['}})).error?.code==='locator_invalid','Invalid CSS locator admitted');
  const frameTarget=first.elements.find(e=>e.name==='Frame action');
  assert((await prepare({op:'click',target:{ref:frameTarget.ref},scroll:true})).point,'Frame point translation failed');
  const shadowTarget=first.elements.find(e=>e.name==='Shadow action');
  assert((await prepare({op:'click',target:{ref:shadowTarget.ref},scroll:true})).point,'Shadow hit test failed');
  await evaluate(`document.body.insertAdjacentHTML('beforeend','<div id="cover" style="position:fixed;inset:0;z-index:99999;background:white"></div>')`);
  assert((await prepare({op:'click',target:{ref:query.ref}})).error?.code==='element_obscured','Obscured button admitted');
  await evaluate(`document.querySelector('#cover').remove()`);
  const verified=await engine(`verify(${JSON.stringify({document:first.document,target:{testid:'order-field'},op:'fill',text:'订单 🦉 42'})})`);
  assert(verified.verified&&!JSON.stringify(verified).includes('订单 🦉 42'),'Input verification leaked field text');
  assert((await bridge.call('state',{browser})).capture_capability,'Typed screenshot capability missing');
  const capture=async(area,format='png',key='native-capture',limit=131072)=>{
    const live=await bridge.call('state',{browser}),dpr=area.device_pixel_ratio||1;
    return bridge.call('capture',{browser,agent:true,navigation:live.navigation,capture_key:key,format,max_bytes:limit,
      clip:{...area.region,scale:Math.min(1,1280/(area.region.width*dpr),960/(area.region.height*dpr))}});
  };
  const captureArea=await engine('captureArea({mode:"viewport"})');
  assert(captureArea.ok&&captureArea.redactions.length>=3,'Known form controls and opaque frames were not masked');
  const rawCapture=await capture(captureArea);
  const masked=await prepareScreenshot(rawCapture,captureArea,captureArea,{format:'png'});
  assert(masked.image.media_type==='image/png'&&masked.image.bytes<=131072&&masked.image.width<=1280&&masked.image.height<=960,'Typed screenshot budgets or format were incorrect');
  const pixels=await createImageBitmap(new Blob([Uint8Array.from(atob(masked.image.base64),c=>c.charCodeAt(0))],{type:'image/png'}));
  const canvas=document.createElement('canvas');canvas.width=pixels.width;canvas.height=pixels.height;
  const drawing=canvas.getContext('2d');drawing.drawImage(pixels,0,0);pixels.close();
  const field=captureArea.redactions[0],mx=Math.floor((field.x+field.width/2-captureArea.region.x)*canvas.width/captureArea.region.width),my=Math.floor((field.y+field.height/2-captureArea.region.y)*canvas.height/captureArea.region.height);
  assert([...drawing.getImageData(mx,my,1,1).data].slice(0,3).every(value=>value===32),'Screenshot field masking did not change the actual pixels');
  assert(drawing.getImageData(1,1,1,1).data[0]>200,'Screenshot was empty or entirely masked');
  const imageBootstrap=await fetch('/api/v1/bootstrap'),imageToken=imageBootstrap.headers.get('x-mdo-write-token');await imageBootstrap.arrayBuffer();
  const savedImage=await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':imageToken},
    body:JSON.stringify({method:'storage.write',context:{extension:'browser-use-qa'},params:{path:'qa/capture-fixture.png',base64:masked.image.base64}})});
  assert(savedImage.ok,'Could not save the bounded masked fixture image');
  const elementArea=await engine(`captureArea(${JSON.stringify({mode:'element',document:first.document,target:{ref:query.ref}})})`);
  const elementCapture=await prepareScreenshot(await capture(elementArea,'jpeg','element-capture',8192),elementArea,elementArea,{format:'jpeg',max_bytes:8192});
  assert(elementCapture.image.media_type==='image/jpeg'&&elementCapture.image.bytes<=8192&&elementCapture.image.width<captureArea.region.width,'Element JPEG screenshot was not cropped or bounded');
  const oldViewport=await evaluate('({width:innerWidth,height:innerHeight,scroll:scrollY})');
  await evaluate("document.body.style.minHeight='2000px'");
  await evaluate("(()=>{const field=document.createElement('input');field.id='fixed-private-capture';field.value='fixture-private-fixed';field.style.cssText='position:fixed;bottom:0;left:700px;width:100px;height:30px;background:rgb(250,20,150);z-index:9999';document.body.append(field);return true})()");
  const fullArea=await engine('captureArea({mode:"full_page"})');
  const fullCapture=await prepareScreenshot(await capture(fullArea,'png','full-capture'),fullArea,fullArea,{format:'png'});
  assert(fullArea.region.height>=2000&&fullCapture.scaled&&fullCapture.image.height<=960,'Full-page capture did not include the document or respect scaling');
  const fullBitmap=await createImageBitmap(new Blob([Uint8Array.from(atob(fullCapture.image.base64),c=>c.charCodeAt(0))],{type:'image/png'}));
  const fullCanvas=document.createElement('canvas');fullCanvas.width=fullBitmap.width;fullCanvas.height=fullBitmap.height;
  const fullDrawing=fullCanvas.getContext('2d');fullDrawing.drawImage(fullBitmap,0,0);fullBitmap.close();
  const fullPixels=fullDrawing.getImageData(0,0,fullCanvas.width,fullCanvas.height).data;let exposedFixed=0;
  for(let i=0;i<fullPixels.length;i+=4)if(fullPixels[i]>=235&&fullPixels[i+1]<=30&&fullPixels[i+2]>=140&&fullPixels[i+2]<=160)exposedFixed++;
  assert(exposedFixed===0,'Full-page capture exposed a fixed-position form control');
  assert(JSON.stringify(await evaluate('({width:innerWidth,height:innerHeight,scroll:scrollY})'))===JSON.stringify(oldViewport),'Screenshot capture changed the page viewport or scrolling');
  await evaluate("document.querySelector('#fixed-private-capture').remove()");
  await evaluate("document.body.style.minHeight=''");
  let invalidCapture=false;try{await bridge.call('capture',{browser,agent:true,navigation:state.navigation,capture_key:'bad-capture',format:'png',clip:{x:0,y:0,width:1000000,height:1000,scale:1}})}catch{invalidCapture=true}
  assert(invalidCapture,'A screenshot bypassed its native raster limits');
  const pendingCapture=bridge.call('capture',{browser,agent:true,navigation:state.navigation,capture_key:'cancel-capture',format:'png',clip:{...captureArea.region,scale:0.5}}).then(()=>({completed:true}),()=>({cancelled:true}));
  await bridge.call('capture_cancel',{browser,capture_key:'cancel-capture'});
  const cancelResult=await pendingCapture;assert(cancelResult.cancelled||cancelResult.completed,'Screenshot cancellation had no terminal receipt');
  for(let i=0;i<100&&(await bridge.call('state',{browser})).capture_busy;i++)await pause(20);
  assert(!(await bridge.call('state',{browser})).capture_busy,'Cancelled screenshot retained its native budget');
  const openDialog=async selector=>{
    const prepared=await prepare({op:'click',target:{css:selector},scroll:true});
    assert(prepared.ok,'Dialog button was not actionable');
    const input=bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,...prepared.point});
    let pending;
    for(let i=0;i<50;i++){
      const snapshot=await bridge.call('state',{browser});
      if(snapshot.dialog_open){pending=(await bridge.call('dialog',{browser,action:'list',agent:true})).dialogs[0];break}
      await pause(20);
    }
    assert(pending?.id,'Native script dialog was not captured');
    const outcome=await Promise.race([input,pause(1000).then(()=>{throw Error('Input did not yield at the dialog boundary')})]);
    assert(outcome.accepted&&outcome.trusted_input,'Dialog input was not acknowledged');
    return pending;
  };
  assert((await bridge.call('state',{browser})).dialog_capability,'Native dialog capability missing');
  const alertDialog=await openDialog('#dialog-alert');
  assert(alertDialog.kind==='alert'&&alertDialog.message==='Fixture alert','Alert metadata was incorrect');
  await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:alertDialog.id,accept:true});
  assert(await evaluate("document.querySelector('#dialog-result').textContent")==='alert:closed','Alert did not resume page execution');
  const confirmDialog=await openDialog('#dialog-confirm');
  assert(confirmDialog.kind==='confirm','Confirm kind missing');
  let staleDialog=false;
  try{await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:alertDialog.id,accept:true})}catch{staleDialog=true}
  assert(staleDialog,'An obsolete dialog identity was accepted');
  await bridge.call('takeover',{browser});
  assert((await bridge.call('dialog',{browser,agent:true,action:'list'})).dialogs.length===1,'Read-only dialog metadata disappeared during takeover');
  let protectedDialog=false;
  try{await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:confirmDialog.id,accept:true})}catch{protectedDialog=true}
  assert(protectedDialog,'Agent answered a dialog while the user had control');
  await bridge.call('dialog',{browser,action:'respond',dialog:confirmDialog.id,accept:false});
  assert(await evaluate("document.querySelector('#dialog-result').textContent")==='confirm:false','User dismissal did not reach confirm');
  await bridge.call('release',{browser});
  const promptDialog=await openDialog('#dialog-prompt');
  assert(promptDialog.kind==='prompt'&&!JSON.stringify(promptDialog).includes('fixture-private-default'),'Prompt default value was exposed');
  await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:promptDialog.id,accept:true,text:'响应 🦉'});
  assert(await evaluate("document.querySelector('#dialog-result').textContent")==='prompt:响应 🦉','Prompt Unicode response was lost');
  assert((await bridge.call('dialog',{browser,action:'list'})).dialogs.length===0,'Closed prompt was retained');
  const defaultPrompt=await openDialog('#dialog-prompt');
  await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:defaultPrompt.id,accept:true});
  assert(await evaluate("document.querySelector('#dialog-result').textContent")==='prompt:fixture-private-default','Omitted prompt text did not preserve its native default');
  const emptyPrompt=await openDialog('#dialog-prompt');
  await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:emptyPrompt.id,accept:true,text:'',has_text:true});
  assert(await evaluate("document.querySelector('#dialog-result').textContent")==='prompt:','Explicit empty prompt text did not clear its value');
  if(expiryProbe){
    await trace('waiting-for-real-dialog-expiry');
    await evaluate("document.querySelector('#dialog-result').textContent='expiry-waiting'");
    const expiring=await openDialog('#dialog-confirm'),started=performance.now();
    let expired=false;
    while(performance.now()-started<126000){
      await pause(1000);
      const pending=(await bridge.call('dialog',{browser,action:'list'})).dialogs;
      if(!pending.length){expired=true;break;}
      assert(pending[0].id===expiring.id,'An unrelated dialog replaced the expiry target');
    }
    dialogExpiryMs=Math.round(performance.now()-started);
    assert(expired&&dialogExpiryMs>=118000&&dialogExpiryMs<126000,'The real 120-second dialog lease did not expire within its bound');
    assert(await evaluate("document.querySelector('#dialog-result').textContent")==='confirm:false','Expired confirm did not dismiss and resume its page');
    let stale=false;try{await bridge.call('dialog',{browser,action:'respond',dialog:expiring.id,accept:true})}catch{stale=true}
    assert(stale,'An expired dialog identity was accepted');
    assert((await engine('observe({})')).document===first.document,'The expired dialog prevented fresh page observation');
    await trace('real-dialog-expiry-passed');
  }
  const readModal=bridge.call('automation',{browser,agent:true,script:"(()=>{alert('Isolated read dialog');return {ok:true}})()"});
  const readBoundary=await Promise.race([readModal,pause(1000).then(()=>{throw Error('Isolated reading did not yield at its dialog')})]);
  assert(readBoundary.value?.blocked_by_dialog,'Isolated reading did not report its modal boundary');
  const readDialog=(await bridge.call('dialog',{browser,action:'list'})).dialogs[0];
  await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:readDialog.id,accept:true});
  assert((await engine('observe({})')).document===first.document,'Dialog interrupted observation corrupted its runtime');
  const humanPrompt=await openDialog('#dialog-prompt');
  let humanControl=0,layoutChanges=0,humanError;
  const widget=createBrowserDialogs({execute:(command,params={})=>bridge.call(command,{browser,...params}),
    onControl:()=>humanControl++,onChange:()=>layoutChanges++,onError:error=>{humanError=error}});
  document.body.append(widget.element);
  await widget.refresh(await bridge.call('state',{browser}));
  assert(!widget.element.hidden&&widget.element.querySelector('p').textContent==='Fixture prompt','Human dialog UI did not show the pending prompt');
  const humanInput=widget.element.querySelector('input');
  assert(humanInput.value===''&&!humanInput.hidden,'Human dialog UI exposed a private default');
  humanInput.value='手动回复 🦉';
  widget.element.querySelectorAll('button')[1].click();
  for(let i=0;i<100&&!widget.element.hidden&&!humanError;i++)await pause(20);
  assert(!humanError&&widget.element.hidden&&humanInput.value==='','Human prompt response failed or retained its text');
  assert(humanControl===1&&(await bridge.call('state',{browser})).manual,'Human dialog response did not take over browser control');
  assert(await evaluate("document.querySelector('#dialog-result').textContent")==='prompt:手动回复 🦉','Human UI reply was not delivered to the native prompt');
  const changesBeforeDispose=layoutChanges;widget.dispose();
  assert(layoutChanges===changesBeforeDispose,'Disposed dialog UI changed native layout');
  widget.element.remove();await bridge.call('release',{browser});
  assert((await bridge.call('state',{browser})).popup_capability,'Native popup capability missing');
  const popupButton=await prepare({op:'click',target:{css:'#popup-open'},scroll:true});
  const popupInput=bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,...popupButton.point});
  let popup;
  for(let i=0;i<100;i++){
    popup=(await bridge.call('popups',{browser,action:'list'})).popups[0];
    if(popup)break;await pause(20);
  }
  assert(popup?.id&&popup.user_initiated,'New-window request did not preserve its trusted user gesture');
  const child='browser-use-native-child';
  await bridge.call('create',{browser:child,popup:popup.id,opener:browser,visible:false});
  assert((await bridge.call('create',{browser:child,popup:popup.id,opener:browser,visible:false})).attached,'Repeating the same native adoption did not attach to its original view');
  const popupBoundary=await popupInput;assert(popupBoundary.popup_open,'Native input did not yield at the new-window boundary');
  assert((await bridge.call('state',{browser})).url===first.url,'A popup navigated its opener');
  let childState;
  for(let i=0;i<100;i++){
    childState=await bridge.call('state',{browser:child});
    if(!childState.loading&&childState.title==='Browser Use popup fixture')break;await pause(30);
  }
  assert(childState.opener===browser&&childState.title==='Browser Use popup fixture','Popup was not attached to an independent native browser');
  const relation=await bridge.call('evaluate',{browser:child,script:'Boolean(window.opener)'});
  assert(relation.value===true,'The adopted popup lost window.opener');
  assert(await evaluate("document.querySelector('#popup-result').textContent")==='Popup connected','Popup could not send its normal message to the opener');
  let reused=false;try{await bridge.call('create',{browser:'browser-use-duplicate-child',popup:popup.id,opener:browser,visible:false})}catch{reused=true}
  assert(reused,'A popup request was adopted more than once');
  await evaluate("document.querySelector('#popup-open').click()");
  assert((await bridge.call('popups',{browser,action:'list'})).popups.length===0,'An existing named window was opened as a duplicate tab');
  for(let i=0;i<100;i++){
    const current=await bridge.call('state',{browser:child});
    if(!current.loading)break;await pause(20);
  }
  await bridge.call('bounds',{browser:child,visible:true,bounds:{x:10,y:70,width:1000,height:650,scale:devicePixelRatio}});
  const childTarget=await bridge.call('automation',{browser:child,agent:true,script:`(${runtimeSource})().observe({})`});
  const closeButton=childTarget.value.elements.find(e=>e.name==='Close popup').bounds;
  const childNavigation=(await bridge.call('state',{browser:child})).navigation;
  await bridge.call('input',{browser:child,agent:true,kind:'click',navigation:childNavigation,x:closeButton.x+closeButton.width/2,y:closeButton.y+closeButton.height/2});
  for(let i=0;i<100&&!(await bridge.call('state',{browser:child})).closed_by_page;i++)await pause(20);
  assert((await bridge.call('state',{browser:child})).closed_by_page,'Native window.close did not request tab closure');
  await bridge.call('close',{browser:child});
  const floodInput=bridge.call('evaluate',{browser,script:"(()=>{for(let i=0;i<5;i++)window.open('/__browser_use_popup__.html?flood='+i,'');return true})()"});
  let dismissed=0;
  for(let attempt=0;attempt<250&&dismissed<5;attempt++){
    const flooded=await bridge.call('popups',{browser,action:'list'});
    assert(flooded.popups.length<=4,'Popup flood bypassed the bounded pending-window limit');
    for(const pending of flooded.popups){await bridge.call('popups',{browser,action:'dismiss',popup:pending.id});dismissed++;}
    if(dismissed<5)await pause(20);
  }
  assert(dismissed===5,'The browser did not complete all five serialized new-window requests');
  assert((await floodInput).ok,'Popup flood evaluation did not resume after dismissal');
  assert((await bridge.call('popups',{browser,action:'list'})).popups.length===0,'Dismissed popup requests were retained');
  const capacity=[];
  for(let i=0;i<7;i++){const id='browser-use-capacity-'+i;await bridge.call('create',{browser:id,url:'about:blank',visible:false});capacity.push(id)}
  let capacityBlocked=false;try{await bridge.call('create',{browser:'browser-use-over-capacity',url:'about:blank',visible:false})}catch{capacityBlocked=true}
  assert(capacityBlocked,'The native browser count exceeded eight');
  await evaluate("(()=>{window.open('about:blank','capacity-blocked');return true})()");
  const atCapacity=await bridge.call('popups',{browser,action:'list'});
  assert(atCapacity.popups.length===0&&atCapacity.error.includes('limit'),'Popup creation bypassed the native browser capacity');
  for(const id of capacity)await bridge.call('close',{browser:id});
  await bridge.call('takeover',{browser});
  const blankInput=bridge.call('evaluate',{browser,script:"(()=>{window.open('about:blank','manual-blank');return true})()"});
  let blank;
  for(let i=0;i<100&&!blank;i++){blank=(await bridge.call('popups',{browser,agent:true,action:'list'})).popups[0];if(!blank)await pause(20)}
  assert(blank?.url==='about:blank','Blank popup request was lost');
  await bridge.call('create',{browser:child,popup:blank.id,opener:browser,visible:false});
  assert((await blankInput).ok,'Blank-popup evaluation did not resume');
  const blankState=await bridge.call('state',{browser:child});
  assert(blankState.manual&&!blankState.loading&&blankState.url==='about:blank','Blank popup did not inherit user control or readiness');
  await bridge.call('close',{browser:child});await bridge.call('release',{browser});
  const expiryInput=bridge.call('evaluate',{browser,script:"(()=>{window.open('about:blank','expired-popup');return true})()"},{timeout:30000});
  let expiring;
  for(let i=0;i<100&&!expiring;i++){expiring=(await bridge.call('popups',{browser,action:'list'})).popups[0];if(!expiring)await pause(20)}
  assert(expiring?.id,'Expiry fixture did not create its pending popup');
  await trace('waiting-for-popup-lease-expiry');
  for(let i=0;i<115;i++){
    if(!(await bridge.call('popups',{browser,action:'list'})).popups.some(p=>p.id===expiring.id))break;
    await pause(200);
  }
  const expired=await bridge.call('popups',{browser,action:'list'});
  assert((await expiryInput).ok,'Popup lease expiry did not resume the opener');
  assert(!expired.popups.some(p=>p.id===expiring.id)&&expired.error.includes('expired'),'Unadopted popup did not expire and release its deferral');
  let stalePopup=false;try{await bridge.call('create',{browser:child,popup:expiring.id,opener:browser,visible:false})}catch{stalePopup=true}
  assert(stalePopup,'An expired popup request was adopted');
  const ambiguous=await engine(`locate(${JSON.stringify({document:first.document,target:{role:'button',name:'Duplicate'}})})`);
  assert(ambiguous.error?.code==='element_ambiguous','Duplicate locator silently selected an element');
  const limited=await engine('observe({limit:2})');
  assert(limited.truncated && limited.elements.length===2,'Budget truncation was not explicit');
  await evaluate(`document.body.appendChild(document.createElement('p')).textContent='Later text'`);
  const stable=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:query.ref}})})`);
  assert(stable.ok,'Unrelated DOM update invalidated a stable reference');
  const oldFrame=first.elements.find(e=>e.name==='Frame action');
  await evaluate(`document.querySelector('iframe').srcdoc='<button>New frame action</button>'`);
  let changedFrame;
  for(let i=0;i<50;i++) {
    changedFrame=await engine('observe({})');
    if(changedFrame.elements.some(e=>e.name==='New frame action'))break;
    await pause(50);
  }
  assert(changedFrame.elements.some(e=>e.name==='New frame action'),'Frame did not reload');
  const staleFrame=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:oldFrame.ref}})})`);
  assert(['stale_frame','element_detached'].includes(staleFrame.error?.code),'An old iframe document reference was reused');
  assert(changedFrame.document===first.document,'Frame navigation invalidated the whole top document');
  await evaluate(`document.querySelector('#search').remove()`);
  const removed=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:query.ref}})})`);
  assert(removed.error?.code==='element_detached','Detached element reference was reused');
  const image=await bridge.call('screenshot',{browser});
  assert(image.data?.length>1000,'Native screenshot was empty');
  await evaluate(`window.addEventListener('beforeunload',e=>{e.preventDefault();e.returnValue=''})`);
  const pendingUnload=async()=>{
    for(let i=0;i<100;i++){
      const pending=await bridge.call('dialog',{browser,action:'list'});
      if(pending.dialogs.length)return pending.dialogs[0];
      await pause(20);
    }
    throw Error('Before-unload dialog was not captured');
  };
  await trace('before-cancel-navigation');
  await bridge.call('navigate',{browser,url:location.origin+'/__browser_use_page__.html?cancelled=1'});
  const cancelledUnload=await pendingUnload();
  assert(cancelledUnload.kind==='beforeunload','Before-unload kind was incorrect');
  await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:cancelledUnload.id,accept:false});
  await trace('after-beforeunload-dismiss');
  assert(!(await bridge.call('state',{browser})).url.includes('cancelled=1'),'Dismissed before-unload navigation was performed');
  await evaluate('document.readyState');
  await trace('after-document-resumed');
  const rearm=await prepare({op:'click',target:{css:'#order'},scroll:true});
  const rearmState=await bridge.call('state',{browser});
  await bridge.call('input',{browser,agent:true,kind:'click',navigation:rearmState.navigation,...rearm.point});
  await trace('after-rearm-input');
  await bridge.call('navigate',{browser,url:location.origin+'/__browser_use_page__.html?next=1'});
  const acceptedUnload=await pendingUnload();
  await trace('second-beforeunload-captured');
  assert(acceptedUnload.id!==cancelledUnload.id,'Before-unload reused its previous identity');
  await bridge.call('dialog',{browser,agent:true,action:'respond',dialog:acceptedUnload.id,accept:true});
  await trace('after-beforeunload-accept');
  let second;
  for(let i=0;i<100;i++) {
    await pause(100);
    try { second=await engine('observe({})'); if(second.document!==first.document&&second.ready==='complete')break; }
    catch { /* Execution context can disappear during navigation. */ }
  }
  assert(second?.document!==first.document,'Navigation reused the previous document identity');
  await trace('after-new-document');
  const stale=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:query.ref}})})`);
  assert(stale.error?.code==='stale_document','Navigation did not reject an old reference');
  const pendingClose=await engine(`prepare(${JSON.stringify({document:second.document,op:'click',target:{css:'#dialog-confirm'},scroll:true})})`);
  const currentState=await bridge.call('state',{browser});
  await bridge.call('input',{browser,agent:true,kind:'click',navigation:currentState.navigation,...pendingClose.point});
  assert((await bridge.call('dialog',{browser,action:'list'})).dialogs.length===1,'Close fixture had no pending dialog');
  await trace('before-close-with-dialog');
  await bridge.call('close',{browser});
  await trace('after-close-with-dialog');
  await bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html',visible:true,
    bounds:{x:10,y:70,width:1000,height:650,scale:devicePixelRatio}});
  assert((await bridge.call('dialog',{browser,action:'list'})).dialogs.length===0,'A closed native view retained its dialog');
  assert((await bridge.call('popups',{browser,action:'list'})).popups.length===0,'A closed native view retained its pending popup');
  report={passed:true,elements:first.elements.length,frames:first.frames.length,
    screenshot_base64_bytes:image.data.length,checks,backend:'actual-webview2',isolated_world:true,native_input:true,native_dialogs:true,native_popups:true,dialog_expiry_ms:dialogExpiryMs};
} catch(error) { report={passed:false,error:String(error.stack||error)}; }
finally {
  try { await bridge.call('close',{browser}); } catch {}
  bridge.dispose();
}
try {
  const bootstrap=await fetch('/api/v1/bootstrap'); const token=bootstrap.headers.get('x-mdo-write-token');
  await bootstrap.arrayBuffer();
  const response=await fetch('/api/v1/application/call',{method:'POST',
    headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
    body:JSON.stringify({method:'storage.write',context:{extension:'browser-use-qa'},
      params:{path:'qa/page-observation.json',text:JSON.stringify(report)}})});
  if(!response.ok)throw Error('Cannot save native report');
  document.querySelector('#status').textContent=report.passed?'Native checks passed':report.error;
} catch(error) { document.querySelector('#status').textContent=String(error); }
'''


def main(dialog_expiry=False):
    output = dev.ENV / "page-observation-result.json"
    output.write_text('{"passed":false,"state":"running"}\n', encoding="utf-8")
    dump_root = dev.ENV / "host"
    def dumps():
        return {p.name: (p.stat().st_size, p.stat().st_mtime_ns)
                for p in dump_root.glob("crash_*") if p.suffix in (".dmp", ".txt")}
    previous_dumps = dumps()
    web = dev.SOURCE / "app/web"
    runtime = (ROOT / "page/runtime.js").read_text(encoding="utf-8")
    if runtime.count("export function browserRuntime()") != 1:
        raise RuntimeError("The injectable page entry changed")
    runtime = runtime.replace("export function browserRuntime()", "function browserRuntime()")
    script = SCRIPT.replace("RUNTIME_SOURCE", json.dumps(runtime).replace("<", "\\u003c")).replace("DIALOG_EXPIRY", json.dumps(dialog_expiry))
    (web / "__browser_use_page__.html").write_bytes((ROOT / "tests/fixtures/page.html").read_bytes())
    (web / "__browser_use_popup__.html").write_bytes((ROOT / "tests/fixtures/popup.html").read_bytes())
    (web / "__browser_use_probe__.html").write_text(
        '<!doctype html><meta charset="utf-8"><h1 id="status">Browser Use native checks</h1>'
        '<script type="module">' + script + '</script>', encoding="utf-8")
    exclude = dev.SOURCE / ".git/info/exclude"
    text = exclude.read_text(encoding="utf-8")
    if "/app/web/__browser_use_" not in text:
        exclude.write_text(text + "/app/web/__browser_use_*.html\n", encoding="utf-8")
    # A stale result must never make this run appear to pass.
    dev.call("storage.write", {"path": REPORT_PATH, "text": "null"}, REPORT)
    dev.stop()
    result = None
    try:
        dev.start(window=True, page="/__browser_use_probe__.html")
        deadline = time.monotonic() + (200 if dialog_expiry else 70)
        while time.monotonic() < deadline:
            try:
                saved = dev.call("storage.read", {"path": REPORT_PATH}, REPORT)
                result = json.loads(saved["text"])
                if result is not None:
                    break
            except (OSError, RuntimeError, ValueError, KeyError):
                pass
            time.sleep(0.2)
        if not result:
            output.write_text('{"passed":false,"error":"Native checks did not return a result"}\n', encoding="utf-8")
            raise RuntimeError("Native page checks did not return a result; inspect .mdo/server.log")
        result["new_native_diagnostics"] = [name for name, fingerprint in dumps().items()
                                            if previous_dumps.get(name) != fingerprint]
        if result["new_native_diagnostics"]:
            result["passed"] = False
            result["error"] = "The host generated new exception diagnostics; inspect them before accepting the native result"
        # A saved page result alone must not hide a host failure afterwards.
        if not dev.call("runtime.info").get("application_root"):
            result["passed"] = False
            result["error"] = "The host was not healthy after native completion"
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        if not result.get("passed"):
            raise RuntimeError(result.get("error", "Native page checks failed"))
        image = dev.call("storage.read", {"path": "qa/capture-fixture.png", "limit": 262144}, REPORT)
        import base64
        (dev.ENV / "capture-fixture.png").write_bytes(base64.b64decode(image["base64"], validate=True))
        print(json.dumps(result, indent=2))
        return 0
    finally:
        dev.stop()
        dev.start()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dialog-expiry", action="store_true", help="Also wait for the actual 120-second native dialog lease")
    args = parser.parse_args()
    try:
        raise SystemExit(main(args.dialog_expiry))
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
