// Runs against the same normal UI used by upload_probe.py. Credentials stay in memory.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {createBrowserUseClient} from '../sdk/client.mjs';
const root=new URL('../',import.meta.url),env=JSON.parse(await readFile(new URL('.mdo/environment.json',root),'utf8'));
const origin=`http://127.0.0.1:${env.port}`,response=await fetch(origin+'/api/v1/bootstrap');
const token=response.headers.get('x-mdo-write-token');await response.arrayBuffer();
const context={extension:'browser-use-upload-sdk-qa',session:'public-upload-js'};
const sdk={async call(method,params,scope=context){
  const response=await fetch(origin+'/api/v1/application/call',{method:'POST',signal:AbortSignal.timeout(20000),
    headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},body:JSON.stringify({method,params,context:scope})});
  const reply=await response.json();if(!response.ok)throw Object.assign(new Error(reply.error?.message||'SDK call failed'),{code:reply.error?.code});return reply.data;
}};
const client=createBrowserUseClient(sdk),file=new URL('.mdo/qa/public-upload.bin',root),empty=new URL('.mdo/qa/public-upload-empty.bin',root);
const nativePath=url=>decodeURIComponent(url.pathname).replace(/^\/([A-Za-z]:)/,'$1');
const hash=createHash('sha256').update(await readFile(file)).digest('hex');let browser;
const reads=page=>JSON.parse(page.text.slice(page.text.indexOf('[{')));
try{
  const{clients}=await client.tabs({action:'clients'});assert.equal(clients.length,1);
  browser=await client.tabs({action:'create',client:clients[0].id,url:origin+'/__browser_use_upload__.html',key:'upload-js-native'});
  const page=await client.observe({browser:browser.id});
  const operation=`sdk-${crypto.randomUUID().replaceAll('-','')}`;
  const params={browser:browser.id,action:'set',document:page.document,target:{css:'#multiple'},files:[
    {path:nativePath(file),space:'native',name:'snapshot-中文.bin'},{path:nativePath(empty),name:'empty.txt'}],timeout_ms:15000};
  const assigned=await client.upload(params,{operation,retain:true});
  assert.equal(assigned.verified,true);assert.equal(assigned.transfer.retained,true);
  const ticket=assigned.transfer.ticket;
  let observed=await client.waitFor({browser:browser.id,until:'text',text:hash});
  assert.equal(reads(observed.observation).length,1);assert.equal(reads(observed.observation)[0].files[1].size,0);
  assert.deepEqual(await client.upload(params,{operation,retain:true}),assigned);
  assert.equal(reads(await client.observe({browser:browser.id})).length,1,'Repeated operation assigned its files again');
  const clearOperation=`sdk-${crypto.randomUUID().replaceAll('-','')}`;
  const clearParams={browser:browser.id,action:'clear',document:page.document,target:{css:'#multiple'}};
  const cleared=await client.upload(clearParams,{operation:clearOperation,retain:true});
  assert.equal(cleared.cleared,true);assert.equal(cleared.verified,true);assert.equal(cleared.input_method,'dom_file_clear');assert.equal(cleared.events_trusted,false);
  await client.waitFor({browser:browser.id,until:'text',text:'"files":[]'});
  assert.equal(reads(await client.observe({browser:browser.id})).length,2);
  assert.deepEqual(await client.upload(clearParams,{operation:clearOperation,retain:true}),cleared);
  assert.equal(reads(await client.observe({browser:browser.id})).length,2,'Repeated clear dispatched new events');
  const emptySelection=await client.upload(clearParams);assert.equal(emptySelection.changed,false);assert.equal(emptySelection.dispatched,false);
  await client.operation({action:'release',operation:clearOperation});
  assert.equal((await client.upload({browser:browser.id,action:'status',ticket})).verified,true);
  await assert.rejects(sdk.call('browseruse.upload',{browser:browser.id,action:'status',ticket},{...context,session:'foreign'}),{code:'context_mismatch'});
  await assert.rejects(client.upload({browser:browser.id,action:'release',ticket}),{code:'upload_confirmation_required'});
  assert.equal((await client.upload({browser:browser.id,action:'status',ticket})).retained,true);
  assert.equal((await client.upload({browser:browser.id,action:'release',ticket,confirmed:true})).released,true);
  await client.operation({action:'release',operation});
  await assert.rejects(client.upload(params,{operation}),{code:'result_expired'});
  assert.equal(reads(await client.observe({browser:browser.id})).length,2);
  // Raw SDK reaches the same installed C service without the JS completion helper.
  const queued=await sdk.call('browseruse.upload',{browser:browser.id,action:'set',document:page.document,target:{css:'#hidden'},
    files:[{path:nativePath(empty),name:'zero.txt'}]});
  const raw=await client.wait(queued);assert.equal(raw.verified,true);
  const emptyHash=createHash('sha256').update('').digest('hex');
  await client.waitFor({browser:browser.id,until:'text',text:'"id":"hidden"'});
  const completed=reads(await client.observe({browser:browser.id}));assert.equal(completed.length,3);
  assert.equal(completed[2].files[0].sha256,emptyHash);
  await client.upload({browser:browser.id,action:'release',ticket:raw.transfer.ticket,confirmed:true});
  await writeFile(new URL('.mdo/upload-client-result.json',root),JSON.stringify({passed:true,backend:'actual-c-service-js-client-webview2',
    exact_binary_and_empty_bytes:true,retained_until_confirmed_release:true,durable_duplicate_once:true,foreign_scope_denied:true,raw_c_service:true,
    controlled_clear_once:true,clear_events_trusted:false,empty_clear_no_events:true})+'\n');
}finally{if(browser)await client.tabs({action:'close',browser:browser.id});}
