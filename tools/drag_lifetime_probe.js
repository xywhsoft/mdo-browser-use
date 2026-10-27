import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
import {createLocalApplicationClient} from '/js/api/application.js';
import {createFrameDriver} from '/js/features/workbench/frame-driver.js';
const origin=DRAG_LIFETIME_ORIGIN,bridge=createNativeBrowserBridge(),api=createLocalApplicationClient(()=>({extension:'browser-drag-lifetime-qa'})),browser='browser-drag-lifetime-qa';
const call=(command,params={})=>bridge.call(command,{browser,...params}),pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const driver=createFrameDriver({execute:call});let stage='startup',checks=0,report,evidence={};
const assert=(ok,message)=>{checks++;if(!ok)throw Error(message);};
async function until(read,message){const end=performance.now()+15000;while(performance.now()<end){const value=await read();if(value)return value;await pause(30);}throw Error(message);}
async function root(){return until(async()=>{const tree=await call('frames',{action:'list',agent:true});return tree.ok&&tree.complete&&tree.frames.length===1&&!tree.frames[0].loading&&tree.frames[0].url.startsWith(origin+'/drag-')?tree.frames[0]:null;},'The original root did not become ready');}
const evaluate=(frame,script)=>call('frames',{action:'evaluate',agent:true,frame:frame.id,document:frame.document,script});
async function drag(page,key){
  const from=await driver.read('prepare',{document:page.document,op:'drag',target:{css:'#source'},scroll:false});
  const to=await driver.read('prepare',{document:page.document,op:'drop',target:{css:'#destination'},scroll:false});
  assert(from.ok&&to.ok,'The lifetime drag targets were not ready');const state=await call('state');
  return call('drag',{agent:true,drag_key:key,navigation:state.navigation,...from.point,to_x:to.point.x,to_y:to.point.y,
    source_frame_chain:from.frame_chain,destination_frame_chain:to.frame_chain,timeout_ms:10000});
}
try{
  await call('create',{url:origin+'/drag-download-root',intercept_downloads:true,visible:true,bounds:{x:0,y:50,width:1000,height:650,scale:1}});
  const original=await root(),page=await driver.read('observe');assert(page.ok,'The lifetime page was not observed');
  stage='original-post-download';evidence.first=await drag(page,'lifetime-original-post');
  assert(evidence.first.dispatched&&evidence.first.trusted_input&&!evidence.first.accepted,'The download did not preserve the interrupted gesture receipt');
  const offer=await until(async()=>{const list=await call('downloads',{action:'list',agent:true});return list.downloads?.find(row=>row.state==='pending');},'The original POST response was not intercepted');
  await until(async()=>!(await call('state')).input_busy,'The interrupted drag retained its native slot');
  await call('downloads',{action:'accept',download:offer.id,agent:true});
  const completed=await until(async()=>{const value=await call('downloads',{action:'get',download:offer.id,agent:true});return value.state==='completed'?value:null;},'The original response did not complete');
  const expected=new Uint8Array([...new TextEncoder().encode('browser-use-original-drag-response'),0,255]);
  const sha=[...new Uint8Array(await crypto.subtle.digest('SHA-256',expected))].map(n=>n.toString(16).padStart(2,'0')).join('');
  assert(completed.size===expected.length&&completed.sha256===sha,'The original response bytes were not verified');
  await call('downloads',{action:'release',download:offer.id,agent:true});
  const current=await root();evidence.document=await evaluate(current,'({marker:document.body.dataset.lifetime,downs:document.body.dataset.downs,ups:document.body.dataset.ups,watch:!!globalThis.__mdo_native_drag_watch_v1})');
  assert(current.document===original.document&&evidence.document.value.marker==='original','The file response replaced the original document');
  stage='following-drag';evidence.following=await drag(await driver.read('observe'),'lifetime-following-drag');
  const final=await evaluate(current,'({downs:document.body.dataset.downs,ups:document.body.dataset.ups,watch:!!globalThis.__mdo_native_drag_watch_v1})');evidence.final=final;
  assert(evidence.document.value.watch===false,'The retained original document still owns a stale drag observer');
  assert(evidence.following.accepted&&evidence.following.trusted_input,'The following drag did not recover after the original response download');
  assert(final.value.downs==='2'&&final.value.ups==='2'&&!final.value.watch,'The following gesture was duplicated or retained its source observer');
  stage='real-navigation-and-back';await call('close');
  await call('create',{url:origin+'/drag-navigation-root',intercept_downloads:true,visible:true,bounds:{x:0,y:50,width:1000,height:650,scale:1}});
  await root();
  const beforeHistory=await driver.read('observe');assert(beforeHistory.ok,'History fixture was not observed');evidence.navigation=await drag(beforeHistory,'lifetime-real-navigation');
  assert(evidence.navigation.dispatched&&evidence.navigation.trusted_input&&!evidence.navigation.accepted,'Real navigation did not preserve the interrupted gesture receipt');
  await until(async()=>{const state=await call('state');return state.url===origin+'/foreign'&&!state.loading&&!state.input_busy;},'Real navigation did not settle its old drag');
  assert(!(await call('state')).drag_cleanup_failed,'Document replacement falsely retained unconfirmed source cleanup');
  await call('back');await until(async()=>{const state=await call('state');return state.url===origin+'/drag-navigation-root'&&!state.loading;},'History did not return to the source');
  const historyRoot=await root(),historyPage=await driver.read('observe'),historyBefore=await evaluate(historyRoot,'({downs:Number(document.body.dataset.downs),ups:Number(document.body.dataset.ups),watch:!!globalThis.__mdo_native_drag_watch_v1})');
  assert(!historyBefore.value.watch,'History restored an old isolated drag observer');
  evidence.historyDrag=await drag(historyPage,'lifetime-history-drag');evidence.historyFinal=await evaluate(historyRoot,'({downs:Number(document.body.dataset.downs),ups:Number(document.body.dataset.ups),watch:!!globalThis.__mdo_native_drag_watch_v1})');
  assert(evidence.historyDrag.accepted&&evidence.historyFinal.value.downs===historyBefore.value.downs+1&&evidence.historyFinal.value.ups===historyBefore.value.ups+1&&!evidence.historyFinal.value.watch,'History recovery repeated input or retained an old observer');
  report={passed:true,checks,original_post_response:true,original_document_retained:true,following_drag_recovers:true,history_drag_recovers:true,bfcache_document_restored:historyPage.document===beforeHistory.document,evidence};
}catch(error){report={passed:false,stage,checks,error:String(error.message||error).slice(0,512),evidence};}
finally{
  try{await call('close');}catch{}
  await api.call('storage.write',{path:'qa/drag-lifetime.json',text:JSON.stringify(report)});
  document.querySelector('#status').textContent=JSON.stringify({passed:report.passed,stage:report.stage,checks});bridge.dispose();
}
