// The normal public C/JS route must focus a control without activating it.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {createBrowserUseClient} from '../sdk/client.mjs';
const root=new URL('../',import.meta.url),env=JSON.parse(await readFile(new URL('.mdo/environment.json',root),'utf8')),
  endpoint=`http://127.0.0.1:${env.port}`,bootstrap=await fetch(endpoint+'/api/v1/bootstrap'),token=bootstrap.headers.get('x-mdo-write-token');
await bootstrap.arrayBuffer();
const context={extension:'browser-use-dom-keyboard-sdk-qa',session:'public-dom-keyboard-js'};
const sdk={async call(method,params){const response=await fetch(endpoint+'/api/v1/application/call',{method:'POST',signal:AbortSignal.timeout(25000),
  headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},body:JSON.stringify({method,params,context})});
  const reply=await response.json();if(!response.ok)throw Object.assign(Error(reply.error?.message||'SDK failed'),{code:reply.error?.code});return reply.data;}};
const client=createBrowserUseClient(sdk),url=process.argv[2],output=new URL('.mdo/dom-keyboard-client-result.json',root);
assert.match(url,/^http:\/\/127\.0\.0\.1:\d+\/dom-key-root$/);
let browser,page,stage='startup',evidence={};const cases=[];
await writeFile(output,'{"passed":false,"state":"running"}\n');
const operation=()=>`sdk-${crypto.randomUUID().replaceAll('-','')}`;
async function has(text){assert((await client.waitFor({browser:browser.id,until:'text',text,timeout_ms:15000})).matched,text);}
async function navigate(query=''){await client.navigate({browser:browser.id,action:'goto',url:url+query});page=await client.observe({browser:browser.id});}
async function act(op,target,extra={}){const result=await client.act({browser:browser.id,document:page.document,op,target,timeout_ms:15000,...extra});
  evidence.last_action={keys:Object.keys(result),verification:result.verification,dispatched:result.dispatched,outcome:result.outcome};return result;}
try{
  const {clients}=await client.tabs({action:'clients'});assert.equal(clients.length,1);
  browser=await client.tabs({action:'create',client:clients[0].id,url,key:'public-dom-keyboard-js'});
  page=await client.observe({browser:browser.id});assert.equal(page.frame_routing,'native-isolated-v1');
  for(const name of ['Root','Child']){
    stage=name+'-button-enter';const args={browser:browser.id,document:page.document,op:'key',key:'Enter',target:{role:'button',name:name+' activate'}},identity=operation();
    try{const key=await client.act(args,{operation:identity,retain:true});assert(key.dispatched);await has(name+' activations 1 trusted');
      assert.deepEqual(await client.act(args,{operation:identity,retain:true}),key);await has(name+' activations 1 trusted');
    }finally{await client.operation({action:'release',operation:identity});}
    cases.push(name+'-button');
    stage=name+'-field';const text='测试A🙂',suffix='-append',target={label:name+' field'};
    const filled=await act('fill',target,{text});assert.equal(filled.verification,'field_state');await has(name+' field inputs 1 length '+text.length+' trusted');
    await act('type',target,{text:suffix});await has(name+' field inputs 2 length '+(text+suffix).length+' trusted');
    const cleared=await act('fill',target,{text:''});assert.equal(cleared.verification,'field_state');await has(name+' field inputs 3 length 0 trusted');await has(name+' field clicks 0');cases.push(name+'-fill-type-clear');
  }
  stage='shadow-field';await act('fill',{role:'textbox',name:'Root shadow'},{text:'shadow-文字'});await has('Root shadow inputs 1 length 9 trusted');await has('Root shadow clicks 0');
  stage='contenteditable';await act('fill',{role:'textbox',name:'Root editor'},{text:'rich-文本'});await has('Root editor inputs 1 length 7 trusted');await has('Root editor clicks 0');
  for(const query of ['hijack','replace','selection']){
    stage=query;await navigate('?'+query+'=1');const args={browser:browser.id,document:page.document,op:'fill',target:{label:'Root field'},text:'must-not-reach-sink',timeout_ms:15000},identity=operation();let failure;
    try{
      try{await client.act(args,{operation:identity,retain:true});}catch(error){failure=error;}
      assert.equal(failure?.code,query==='selection'?'focus_changed':'action_uncertain');assert.equal(failure.details.error.effect_may_have_occurred,true);
      await assert.rejects(client.act(args,{operation:identity,retain:true}),{code:failure.code});
      await has('Root field focuses 1');await has('Root field clicks 0');await has('Root field inputs 0');await has('Root sink inputs 0');
      await has('Root field keydowns '+(query==='selection'?1:0));
      if(query==='replace')await has('Root replacement inputs 0');
    }finally{await client.operation({action:'release',operation:identity});}
  }
  stage='focus-dialog';await navigate('?dialog=1');const dialogArgs={browser:browser.id,document:page.document,op:'fill',target:{label:'Root field'},text:'not-resumed',timeout_ms:15000},dialogOp=operation();
  try{
    const checkpoint=await client.act(dialogArgs,{operation:dialogOp,retain:true});assert.equal(checkpoint.outcome,'dialog_open');assert.equal(checkpoint.completed,false);
    const list=await client.dialog({browser:browser.id,action:'list'});assert.equal(list.dialogs.length,1);
    await client.dialog({browser:browser.id,action:'respond',dialog:list.dialogs[0].id,accept:true});
    assert.deepEqual(await client.act(dialogArgs,{operation:dialogOp,retain:true}),checkpoint);
    await has('Root field focuses 1');await has('Root field inputs 0');await has('Root field keydowns 0');
  }finally{await client.operation({action:'release',operation:dialogOp});}
  stage='unrelated-sibling';await navigate('?sibling=1');const sibling=await act('fill',{label:'Root field'},{text:'sibling-safe'});assert.equal(sibling.verification,'field_state');await has('Root field inputs 1 length 12 trusted');
  stage='focus-original-response';await navigate('?download=1');const original=page.document;
  const downloadArgs={browser:browser.id,document:page.document,op:'fill',target:{label:'Root field'},text:'not-resumed',timeout_ms:15000},downloadOp=operation();
  try{
    const checkpoint=await client.act(downloadArgs,{operation:downloadOp,retain:true});assert.equal(checkpoint.outcome,'download_pending');assert.equal(checkpoint.completed,false);
    const list=await client.download({browser:browser.id,action:'list'}),pending=list.downloads.filter(row=>row.state==='pending');assert.equal(pending.length,1);
    await client.download({browser:browser.id,action:'accept',download:pending[0].id});const done=await client.download({browser:browser.id,action:'wait',download:pending[0].id});
    const expected=new Uint8Array([...new TextEncoder().encode('browser-use-original-drag-response'),0,255]),sha=[...new Uint8Array(await crypto.subtle.digest('SHA-256',expected))].map(n=>n.toString(16).padStart(2,'0')).join('');
    assert.equal(done.download.size,expected.length);assert.equal(done.download.sha256,sha);await client.download({browser:browser.id,action:'release',download:pending[0].id});
    assert.deepEqual(await client.act(downloadArgs,{operation:downloadOp,retain:true}),checkpoint);page=await client.observe({browser:browser.id});assert.equal(page.document,original);
    await has('Root field inputs 0');await has('Root field keydowns 0');const resumed=await act('fill',{label:'Root field'},{text:'explicit-new-action'});assert.equal(resumed.verification,'field_state');await has('Root field inputs 1 length 19 trusted');
  }finally{await client.operation({action:'release',operation:downloadOp});}
  const result={passed:true,backend:'normal-public-c-js-webview2',dom_button_activates_once:true,cases,shadow_fill:true,contenteditable_fill:true,
    focus_hijack_and_replacement_denied:true,selection_focus_loss_sends_no_text:true,focus_dialog_not_resumed:true,unrelated_sibling_does_not_invalidate_target:true,
    original_response_download_no_refetch:true,retained_document_explicit_action_recovers:true,no_focus_clicks:true,model_calls:0};
  await writeFile(output,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));
}catch(error){if(browser)try{evidence.current=await client.observe({browser:browser.id,timeout_ms:3000,limit:80,max_bytes:8192});}catch{}
  await writeFile(output,JSON.stringify({passed:false,stage,error:error.message,code:error.code,evidence},null,2)+'\n');throw error;}
finally{if(browser)await client.tabs({action:'close',browser:browser.id});}
