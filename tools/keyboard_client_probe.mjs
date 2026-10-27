// Actual public C admission and JS SDK completion against synthetic pages only.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {createBrowserUseClient} from '../sdk/client.mjs';

const root=new URL('../',import.meta.url),env=JSON.parse(await readFile(new URL('.mdo/environment.json',root),'utf8')),
  endpoint=`http://127.0.0.1:${env.port}`,bootstrap=await fetch(endpoint+'/api/v1/bootstrap'),token=bootstrap.headers.get('x-mdo-write-token');
await bootstrap.arrayBuffer();
const context={extension:'browser-use-keyboard-sdk-qa',session:'public-keyboard-js'};
const sdk={async call(method,params){const response=await fetch(endpoint+'/api/v1/application/call',{method:'POST',signal:AbortSignal.timeout(25000),
  headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},body:JSON.stringify({method,params,context})});
  const reply=await response.json();if(!response.ok)throw Object.assign(Error(reply.error?.message||'SDK failed'),{code:reply.error?.code});return reply.data;}};
const client=createBrowserUseClient(sdk),url=process.argv[2],output=new URL('.mdo/keyboard-client-result.json',root);
assert.match(url,/^http:\/\/127\.0\.0\.1:\d+\/visual-key-root$/);
let browser,page,stage='startup';const cases=[];
await writeFile(output,'{"passed":false,"state":"running"}\n');
async function target(x,y){
  const image=await client.screenshot({browser:browser.id,document:page.document,max_width:1200,max_height:800});
  assert(image.visual_reference);return{image:image.visual_reference.id,x:Math.round(x*image.image.width/image.region.width),y:Math.round(y*image.image.height/image.region.height)};
}
const operation=()=>`sdk-${crypto.randomUUID().replaceAll('-','')}`;
try{
  const {clients}=await client.tabs({action:'clients'});assert.equal(clients.length,1);
  browser=await client.tabs({action:'create',client:clients[0].id,url,key:'public-keyboard-js'});
  page=await client.observe({browser:browser.id});assert.equal(page.frame_routing,'native-isolated-v1');assert(page.frames.some(row=>row.separate_process));
  for(const [label,x,y,key,text] of [
    ['root',70,50,'ArrowRight','Root keys 1 ArrowRight trusted'],
    ['child',292,62,'Enter','Child keys 1 Enter trusted'],
    ['shadow',70,180,'ArrowLeft','Shadow keys 1 ArrowLeft trusted'],
    ['selection',70,50,'Control+A','Root keys 2 a trusted'],
    ['button',440,235,'Enter','Activations 1 trusted']]){
    stage=label;const params={browser:browser.id,document:page.document,op:'key',key,target:await target(x,y),timeout_ms:15000},identity=operation();
    try{
      const result=await client.act(params,{operation:identity,retain:true});assert(result.scene_validated&&result.dispatched);assert.equal(result.input_method,'native_key');
      assert((await client.waitFor({browser:browser.id,until:'text',text})).matched);
      assert.deepEqual(await client.act(params,{operation:identity,retain:true}),result,'A retained key operation was replayed');
      await assert.rejects(client.act(params),{code:'visual_reference_expired'});
      cases.push(label);
    }finally{await client.operation({action:'release',operation:identity});}
  }
  await client.waitFor({browser:browser.id,until:'text',text:'Root keyups 2 trusted'});
  await client.waitFor({browser:browser.id,until:'text',text:'Child keyups 1 trusted'});
  await client.waitFor({browser:browser.id,until:'text',text:'Root clicks 0'});
  stage='focus-hijack';await client.navigate({browser:browser.id,action:'goto',url:url+'?hijack=1'});page=await client.observe({browser:browser.id});
  const params={browser:browser.id,document:page.document,op:'key',key:'Enter',target:await target(70,50),timeout_ms:15000},identity=operation();let failure;
  try{
    try{await client.act(params,{operation:identity,retain:true});}catch(error){failure=error;}
    assert.equal(failure?.code,'action_uncertain');assert.equal(failure.details.error.effect_may_have_occurred,true);
    await assert.rejects(client.act(params,{operation:identity,retain:true}),{code:'action_uncertain'});
    await client.waitFor({browser:browser.id,until:'text',text:'Root clicks 0'});
    await client.waitFor({browser:browser.id,until:'text',text:'Root focuses 1'});
    await client.waitFor({browser:browser.id,until:'text',text:'Root keys 0'});
  }finally{await client.operation({action:'release',operation:identity});}
  stage='focus-dialog';await client.navigate({browser:browser.id,action:'goto',url:url+'?dialog=1'});page=await client.observe({browser:browser.id});
  const dialogArgs={browser:browser.id,document:page.document,op:'key',key:'Enter',target:await target(70,50),timeout_ms:15000},dialogOp=operation();
  try{
    const checkpoint=await client.act(dialogArgs,{operation:dialogOp,retain:true});assert.equal(checkpoint.outcome,'dialog_open');assert.equal(checkpoint.completed,false);
    const list=await client.dialog({browser:browser.id,action:'list'});assert.equal(list.dialogs.length,1);
    await client.dialog({browser:browser.id,action:'respond',dialog:list.dialogs[0].id,accept:true});
    assert.deepEqual(await client.act(dialogArgs,{operation:dialogOp,retain:true}),checkpoint);
    await client.waitFor({browser:browser.id,until:'text',text:'Root focuses 1'});await client.waitFor({browser:browser.id,until:'text',text:'Root keys 0'});
  }finally{await client.operation({action:'release',operation:dialogOp});}
  stage='nonfocusable';await client.navigate({browser:browser.id,action:'goto',url});page=await client.observe({browser:browser.id});
  await assert.rejects(client.act({browser:browser.id,document:page.document,op:'key',key:'Enter',target:await target(210,210)}),{code:'visual_focus_unavailable'});
  await client.waitFor({browser:browser.id,until:'text',text:'Root clicks 0'});
  stage='masked-control';await assert.rejects(client.act({browser:browser.id,document:page.document,op:'key',key:'Enter',target:await target(530,230)}),{code:'visual_region_masked'});
  const result={passed:true,backend:'normal-public-c-js-webview2',visual_keys:cases,focus_hijack_sends_no_key:true,failed_operation_not_replayed:true,
    original_frame_focus:true,button_activates_once:true,no_focus_click:true,focus_dialog_checkpoint:true,consumed_image_rejected:true,nonfocusable_denied:true,masked_control_denied:true,model_calls:0};
  await writeFile(output,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));
}catch(error){await writeFile(output,JSON.stringify({passed:false,stage,error:error.message,code:error.code,details:error.details},null,2)+'\n');throw error;}
finally{if(browser)await client.tabs({action:'close',browser:browser.id});}
