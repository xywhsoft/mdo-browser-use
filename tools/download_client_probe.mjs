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
const client=createBrowserUseClient(sdk),url=process.argv[2];
assert.match(url,/^http:\/\/127\.0\.0\.1:\d+\/page$/);
const bytes=Uint8Array.from({length:131075},(_,i)=>i%251),sha=createHash('sha256').update(bytes).digest('hex');let browser;
try{
  const{clients}=await client.tabs({action:'clients'});assert.equal(clients.length,1);
  browser=await client.tabs({action:'create',client:clients[0].id,url,key:'download-js-native'});
  let page=await client.observe({browser:browser.id});assert(page.text.includes('Browser Use download fixture'));
  const offer=async(selector)=>{
    const clicked=await client.act({browser:browser.id,document:page.document,op:'click',target:{css:selector}});
    if(clicked.outcome!=='download_pending')await writeFile(new URL('.mdo/evidence/public-download-checkpoint-failure.json',root),JSON.stringify({selector,clicked,after:await client.download({browser:browser.id,action:'list'})},null,2)+'\n');
    assert.equal(clicked.outcome,'download_pending',selector);assert.equal(clicked.completed,false);assert.equal(clicked.dispatched,true);
    const list=await client.download({browser:browser.id,action:'list'}),pending=list.downloads.filter(row=>row.state==='pending');assert.equal(pending.length,1);
    const blocked=await client.act({browser:browser.id,document:page.document,op:'click',target:{css:selector}});
    assert.equal(blocked.outcome,'download_pending');assert.equal(blocked.dispatched,false);
    assert.equal((await client.observe({browser:browser.id})).downloads[0].id,pending[0].id);
    assert(!JSON.stringify(list).includes('fixture-query'));assert(!JSON.stringify(list).includes('payload'));return pending[0];
  };
  for(const selector of ['#binary','#blob','#post','#unknown']){
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
  const pending=await offer('#slow-public');
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
  await writeFile(new URL('.mdo/download-client-result.json',root),JSON.stringify({passed:true,backend:'actual-c-js-webview2',automatic_interception:true,download_checkpoint:true,
    pending_blocks_repeated_click:true,observation_metadata:true,verified_post_blob_unknown_binary:true,durable_accept_and_cancel_once:true,foreign_scope_denied:true,
    raw_c_service:true,direct_navigation_checkpoint:true})+'\n');
}finally{if(browser)await client.tabs({action:'close',browser:browser.id});}
