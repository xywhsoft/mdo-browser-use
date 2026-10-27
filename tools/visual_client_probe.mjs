// Real JS SDK completion/durable identity; synthetic loopback canvas pixels.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {createBrowserUseClient} from '../sdk/client.mjs';

const root=new URL('../',import.meta.url),env=JSON.parse(await readFile(new URL('.mdo/environment.json',root),'utf8')),
  endpoint=`http://127.0.0.1:${env.port}`,bootstrap=await fetch(endpoint+'/api/v1/bootstrap'),token=bootstrap.headers.get('x-mdo-write-token');
await bootstrap.arrayBuffer();
const context={extension:'browser-use-visual-sdk-qa',session:'public-visual-js'};
const sdk={async call(method,params){const response=await fetch(endpoint+'/api/v1/application/call',{method:'POST',signal:AbortSignal.timeout(25000),
  headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},body:JSON.stringify({method,params,context})});
  const reply=await response.json();if(!response.ok)throw Object.assign(Error(reply.error?.message||'SDK failed'),{code:reply.error?.code});return reply.data;}};
const client=createBrowserUseClient(sdk),url=process.argv[2],output=new URL('.mdo/visual-client-result.json',root);
assert.match(url,/^http:\/\/127\.0\.0\.1:\d+\/visual-root$/);
let browser,foreign,stage='startup';
await writeFile(output,'{"passed":false,"state":"running"}\n');
const capture=async(selector='#root-canvas')=>{
  const image=await client.screenshot({browser:browser.id,mode:'element',target:{css:selector},document:page.document,max_width:200,max_height:120});
  assert.equal(image.visual_reference.coordinates,'image_pixels');assert.equal(image.visual_reference.width,image.image.width);
  return{image:image.visual_reference.id,x:Math.round(60*image.image.width/image.region.width),y:Math.round(40*image.image.height/image.region.height)};
};
const denied=async(params,code)=>{
  const operation='sdk-'+crypto.randomUUID().replaceAll('-','');
  try{await assert.rejects(client.act(params,{operation,retain:true}),{code});}
  finally{try{await client.operation({action:'release',operation});}catch{}}
};
let page;
try{
  const {clients}=await client.tabs({action:'clients'});assert.equal(clients.length,1);
  browser=await client.tabs({action:'create',client:clients[0].id,url,key:'public-visual-js'});
  page=await client.observe({browser:browser.id});assert.equal(page.frame_routing,'native-isolated-v1');assert(page.frames.some(row=>row.separate_process));
  stage='scaled-click';const target=await capture(),operation='sdk-'+crypto.randomUUID().replaceAll('-',''),params={browser:browser.id,document:page.document,op:'click',target,timeout_ms:15000};
  const clicked=await client.act(params,{operation,retain:true});assert(clicked.scene_validated&&clicked.dispatched);
  assert.equal((await client.waitFor({browser:browser.id,until:'text',text:'Root visual clicks 1 trusted'})).matched,true);
  assert.deepEqual(await client.act(params,{operation,retain:true}),clicked,'A retained visual operation was executed again');
  await client.operation({action:'release',operation});
  stage='used-reference';await denied(params,'visual_reference_expired');
  stage='foreign-browser';foreign=await client.tabs({action:'create',client:clients[0].id,url,key:'public-visual-foreign'});
  const foreignTarget=await capture();await denied({...params,browser:foreign.id,target:foreignTarget},'visual_reference_expired');
  await client.tabs({action:'close',browser:foreign.id});foreign=null;
  stage='changed-scene';const old=await capture();
  await client.act({browser:browser.id,document:page.document,op:'click',target:{css:'#change-scene'}});
  await denied({...params,target:old},'visual_scene_changed');
  assert((await client.observe({browser:browser.id})).text.includes('Root visual clicks 1 trusted'));
  stage='fresh-scene';await client.act({...params,target:await capture()});
  await client.waitFor({browser:browser.id,until:'text',text:'Root visual clicks 2 trusted'});
  stage='gestures';
  for(const [op,text] of [['double_click','Root visual clicks 4 trusted'],['right_click','Root context trusted'],['middle_click','Root auxiliary trusted'],['hover','Root hover trusted'],['scroll','Root wheel trusted']]){
    const result=await client.act({...params,op,target:await capture(),...(op==='scroll'?{dy:80}:{})});assert(result.scene_validated&&result.dispatched);
    assert((await client.waitFor({browser:browser.id,until:'text',text})).matched,op);
  }
  stage='nested-canvas';const nested=await client.act({...params,target:await capture('#child-canvas')});assert(nested.scene_validated);
  assert((await client.waitFor({browser:browser.id,until:'text',text:'Nested visual clicks 1 trusted'})).matched);
  const report={passed:true,backend:'normal-public-c-js-webview2',scaled_canvas_click:true,nested_oopif_canvas_click:true,
    image_consumed_once:true,durable_visual_recovery_no_replay:true,foreign_browser_denied:true,changed_scene_denied_fresh_image_recovers:true,
    native_visual_gestures:['click','double_click','right_click','middle_click','hover','scroll'],model_calls:0};
  await writeFile(output,JSON.stringify(report,null,2)+'\n');
}catch(error){await writeFile(output,JSON.stringify({passed:false,stage,code:error.code,message:String(error.message||error).slice(0,512),details:error.details},null,2)+'\n');throw error;}
finally{for(const item of [foreign,browser])if(item)await client.tabs({action:'close',browser:item.id});}
