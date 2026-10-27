// The fixture URL is synthetic. Authentication remains in memory.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {createBrowserUseClient} from '../sdk/client.mjs';
const root=new URL('../',import.meta.url),env=JSON.parse(await readFile(new URL('.mdo/environment.json',root),'utf8'));
const origin=`http://127.0.0.1:${env.port}`,bootstrap=await fetch(origin+'/api/v1/bootstrap');
const token=bootstrap.headers.get('x-mdo-write-token');await bootstrap.arrayBuffer();
const context={extension:'browser-use-download-sdk-qa',session:'public-download-js'};
const sdk={async call(method,params,scope=context){
  const response=await fetch(origin+'/api/v1/application/call',{method:'POST',signal:AbortSignal.timeout(25000),headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},body:JSON.stringify({method,params,context:scope})});
  const reply=await response.json();if(!response.ok)throw Object.assign(Error(reply.error?.message||'SDK call failed'),{code:reply.error?.code});return reply.data;
}};
const client=createBrowserUseClient(sdk),url=process.argv[2],initialOnly=process.argv.includes('--initial-only');
await writeFile(new URL('.mdo/download-client-result.json',root),'{"passed":false,"state":"running"}\n');
let stage='bootstrap';
assert.match(url,/^http:\/\/127\.0\.0\.1:\d+\/page$/);
const bytes=Uint8Array.from({length:131075},(_,i)=>i%251),sha=createHash('sha256').update(bytes).digest('hex');let browser,initial;
try{
  const{clients}=await client.tabs({action:'clients'});assert.equal(clients.length,1);
  for(const route of ['/binary?initial=public','/auto-download']){
    stage='initial:'+route;
    initial=await client.tabs({action:'create',client:clients[0].id,url:url.replace('/page',route),key:'download-js-initial'});
    assert.equal(initial.download_policy,'intercept');let pending;
    const until=performance.now()+10000;
    while(performance.now()<until){pending=(await client.download({browser:initial.id,action:'list'})).downloads.find(row=>row.state==='pending');if(pending)break;await new Promise(r=>setTimeout(r,50));}
    assert(pending,'Initial navigation did not retain its response before Agent input');
    await client.download({browser:initial.id,action:'accept',download:pending.id});
    const completed=await client.download({browser:initial.id,action:'wait',download:pending.id});assert.equal(completed.download.sha256,sha);
    await client.download({browser:initial.id,action:'release',download:pending.id});await client.tabs({action:'close',browser:initial.id});initial=null;
  }
  browser=await client.tabs({action:'create',client:clients[0].id,url,key:'download-js-native'});
  let page=await client.observe({browser:browser.id});assert(page.text.includes('Browser Use download fixture'));
  const offer=async(selector)=>{
    stage='offer:'+selector;let clicked;
    try{clicked=await client.act({browser:browser.id,document:page.document,op:'click',target:{css:selector}});}
    catch(error){
      await writeFile(new URL('.mdo/evidence/public-download-action-failure.json',root),JSON.stringify({selector,code:error.code,details:error.details,after:await client.download({browser:browser.id,action:'list'})},null,2)+'\n');throw error;
    }
    if(clicked.outcome!=='download_pending')await writeFile(new URL('.mdo/evidence/public-download-checkpoint-failure.json',root),JSON.stringify({selector,clicked,after:await client.download({browser:browser.id,action:'list'})},null,2)+'\n');
    assert.equal(clicked.outcome,'download_pending',selector);assert.equal(clicked.completed,false);assert.equal(clicked.dispatched,true);
    const list=await client.download({browser:browser.id,action:'list'}),pending=list.downloads.filter(row=>row.state==='pending');assert.equal(pending.length,1);
    const blocked=await client.act({browser:browser.id,document:page.document,op:'click',target:{css:selector}});
    assert.equal(blocked.outcome,'download_pending');assert.equal(blocked.dispatched,false);
    assert.equal((await client.observe({browser:browser.id})).downloads[0].id,pending[0].id);
    assert(!JSON.stringify(list).includes('fixture-query'));assert(!JSON.stringify(list).includes('payload'));return pending[0];
  };
  for(const selector of (initialOnly?[]:['#binary','#blob','#post','#unknown'])){
    const pending=await offer(selector),operation=`sdk-${crypto.randomUUID().replaceAll('-','')}`;
    const params={browser:browser.id,action:'accept',download:pending.id,max_bytes:1048576,transfer_timeout_ms:5000};
    const accepted=await client.download(params,{operation,retain:true});assert(accepted.download.id===pending.id);
    const completed=await client.download({browser:browser.id,action:'wait',download:pending.id,timeout_ms:5000});
    assert.equal(completed.verified,true);assert.equal(completed.download.sha256,sha);assert.equal(completed.download.size,bytes.length);
    assert.deepEqual(await client.download(params,{operation,retain:true}),accepted,'Acceptance was replayed after completion');
    await assert.rejects(sdk.call('browseruse.download',{browser:browser.id,action:'get',download:pending.id},{...context,session:'foreign'}),{code:'context_mismatch'});
    const raw=await client.wait(await sdk.call('browseruse.download',{browser:browser.id,action:'get',download:pending.id}));
    assert.equal(raw.download.sha256,sha);
    await client.download({browser:browser.id,action:'release',download:pending.id});
    await client.operation({action:'release',operation});page=await client.observe({browser:browser.id});
  }
  stage='popup:open';
  const popup=await client.act({browser:browser.id,document:page.document,op:'click',target:{css:'#post-popup'}});assert.equal(popup.outcome,'popup_open');
  stage='popup:admission';
  let child;const popupDeadline=performance.now()+10000;
  while(performance.now()<popupDeadline){child=(await client.tabs({action:'list'})).tabs.find(row=>row.opener===browser.id);if(child)break;await new Promise(r=>setTimeout(r,50));}
  assert(child,'The POST popup did not remain available for its response');assert.equal(child.download_policy,'intercept');
  try{
    stage='popup:list';let pending;const until=performance.now()+10000;
    while(performance.now()<until){pending=(await client.download({browser:child.id,action:'list'})).downloads.find(row=>row.state==='pending');if(pending)break;await new Promise(r=>setTimeout(r,50));}
    assert(pending,'The original POST response was lost during popup adoption');
    stage='popup:accept';await client.download({browser:child.id,action:'accept',download:pending.id});
    stage='popup:wait';const completed=await client.download({browser:child.id,action:'wait',download:pending.id});assert.equal(completed.download.sha256,sha);
    stage='popup:retained';const retained=await client.observe({browser:child.id});assert.equal(retained.outcome,'download_retained');assert.equal(retained.page_closed,true);assert.equal(retained.downloads[0].id,pending.id);
    stage='popup:release';await client.download({browser:child.id,action:'release',download:pending.id});
    stage='popup:auto-close';const closingDeadline=performance.now()+6000;let remains=true;
    while(performance.now()<closingDeadline){remains=(await client.tabs({action:'list'})).tabs.some(row=>row.id===child.id);if(!remains)break;await new Promise(r=>setTimeout(r,100));}
    assert(!remains,'The completed release did not resume automatic popup closure');
  }finally{try{await client.tabs({action:'close',browser:child.id});}catch(error){if(error.code!=='resource_missing')throw error;}}
  if(!initialOnly){
  stage='cancellation';const pending=await offer('#slow-public');
  await client.download({browser:browser.id,action:'accept',download:pending.id});
  const operation=`sdk-${crypto.randomUUID().replaceAll('-','')}`,params={browser:browser.id,action:'cancel',download:pending.id};
  const cancelled=await client.download(params,{operation,retain:true});
  assert.deepEqual(await client.download(params,{operation,retain:true}),cancelled);
  const cancelDeadline=performance.now()+5000;
  while(performance.now()<cancelDeadline){const current=(await client.download({browser:browser.id,action:'get',download:pending.id})).download;assert.notEqual(current.state,'completed','The test response finished before its cancellation could be exercised');if(current.state==='cancelled')break;await new Promise(r=>setTimeout(r,30));}
  assert.equal((await client.download({browser:browser.id,action:'get',download:pending.id})).download.state,'cancelled');
  await assert.rejects(client.download({browser:browser.id,action:'wait',download:pending.id}),{code:'cancelled'});
  await client.download({browser:browser.id,action:'release',download:pending.id});await client.operation({action:'release',operation});
  const navigated=await client.navigate({browser:browser.id,action:'goto',url:url.replace('/page','/binary')});
  assert.equal(navigated.outcome,'download_pending');assert.equal(navigated.dispatched,true);
  const direct=(await client.download({browser:browser.id,action:'list'})).downloads.find(row=>row.state==='pending');assert(direct);
  await client.download({browser:browser.id,action:'cancel',download:direct.id});await client.download({browser:browser.id,action:'release',download:direct.id});
  }
  stage='complete';await writeFile(new URL('.mdo/download-client-result.json',root),JSON.stringify({passed:true,test_scope:initialOnly?'initial-and-popup':'all-downloads',backend:'actual-c-js-webview2',automatic_interception:true,download_checkpoint:true,
    pending_blocks_repeated_click:!initialOnly,observation_metadata:!initialOnly,verified_post_blob_unknown_binary:!initialOnly,durable_accept_and_cancel_once:!initialOnly,foreign_scope_denied:!initialOnly,
    raw_c_service:!initialOnly,direct_navigation_checkpoint:!initialOnly,initial_url_and_script_intercepted:true,original_post_popup_intercepted:true})+'\n');
}catch(error){await writeFile(new URL('.mdo/evidence/public-initial-download-failure.json',root),JSON.stringify({passed:false,stage,code:error.code,message:String(error.message||error).slice(0,512)},null,2)+'\n');throw error;}finally{if(initial)await client.tabs({action:'close',browser:initial.id});if(browser)await client.tabs({action:'close',browser:browser.id});}
