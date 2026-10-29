// Synthetic download bodies only. Bootstrap token stays in memory.
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {createBrowserUseClient} from '../sdk/client.mjs';
const root=new URL('../',import.meta.url),env=JSON.parse(await readFile(new URL('.mdo/environment.json',root),'utf8'));
const origin=`http://127.0.0.1:${env.port}`,boot=await fetch(origin+'/api/v1/bootstrap'),token=boot.headers.get('x-mdo-write-token');await boot.arrayBuffer();
const directory=new URL('.mdo/qa/saved/',root);await mkdir(directory,{recursive:true});
const context={extension:'browser-save-js-qa',project:'default',session:'save-public-js',namespace:'save-private',member:'save-worker',workspace:fileURLToPath(directory).replace(/[\\/]$/,'')};
const callInScope=async(method,params,scope=context,options={})=>{
 const signal=options.signal?AbortSignal.any([options.signal,AbortSignal.timeout(25000)]):AbortSignal.timeout(25000);
 const response=await fetch(origin+'/api/v1/application/call',{
 method:'POST',signal,headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},body:JSON.stringify({method,params,context:scope})});
 const reply=await response.json();if(!response.ok)throw Object.assign(Error(reply.error?.message||'SDK failed'),{code:reply.error?.code});return reply.data;};
const sdk={call:(method,params,options)=>callInScope(method,params,context,options)};
const client=createBrowserUseClient(sdk),url=process.argv[2],output=new URL('.mdo/save-client-result.json',root);
const bytes=Buffer.from(Uint8Array.from({length:131075},(_,i)=>i%251)),hash=createHash('sha256').update(bytes).digest('hex');
let browser,child,stage='startup';const paths=[];let saved=0;
await writeFile(output,'{"passed":false,"state":"running"}\n');
try{
 const {clients}=await client.tabs({action:'clients'});assert.equal(clients.length,1);
 browser=await client.tabs({action:'create',client:clients[0].id,url,key:'save-public-js'});
 let page=await client.observe({browser:browser.id});
 for(const selector of ['#binary','#blob','#post','#unknown']){
  stage='offer:'+selector;
  const offered=await client.act({browser:browser.id,document:page.document,op:'click',target:{css:selector}});assert.equal(offered.outcome,'download_pending');
  const pending=(await client.download({browser:browser.id,action:'list'})).downloads.find(row=>row.state==='pending');assert(pending);
  await client.download({browser:browser.id,action:'accept',download:pending.id});
  assert.equal((await client.download({browser:browser.id,action:'wait',download:pending.id})).download.sha256,hash);
  const path='saved-'+selector.slice(1)+'.bin',destination=new URL(path,directory);paths.push(destination);
  const operation='sdk-'+crypto.randomUUID().replaceAll('-',''),params={browser:browser.id,download:pending.id,path};
  stage='save:'+selector;const result=await client.save(params,{operation,retain:true});
  assert.equal(result.saved,true);assert.equal(result.path,path);assert.equal(result.file.sha256,hash);assert.equal(result.source_retained,true);
  assert.deepEqual(await readFile(destination),bytes);assert.equal((await sdk.call('files.digest',{path})).sha256,hash);
  assert.deepEqual(await client.save(params,{operation,retain:true}),result,'The same save identity was replayed');
  assert.equal((await client.download({browser:browser.id,action:'get',download:pending.id})).download.state,'completed');
  assert(!JSON.stringify(result).includes('base64'));assert(!JSON.stringify(result).includes('.mdo-receive-'));
  assert.equal(result.transfer.cleanup_pending,false);
  await client.operation({action:'release',operation});saved++;
  stage='conflict:'+selector;await assert.rejects(client.save(params),{code:'sdk_file_exists'});assert.deepEqual(await readFile(destination),bytes);
  await assert.rejects(callInScope('browseruse.save',params,{...context,namespace:'foreign'}),{code:'context_mismatch'});
  await assert.rejects(callInScope('browseruse.save',params,{...context,workspace:fileURLToPath(root).replace(/[\\/]$/,'')}),{code:'context_mismatch'});
  await client.download({browser:browser.id,action:'release',download:pending.id});page=await client.observe({browser:browser.id});
 }
 stage='popup';assert.equal((await client.act({browser:browser.id,document:page.document,op:'click',target:{css:'#post-popup'}})).outcome,'popup_open');
 const end=performance.now()+10000;while(performance.now()<end){child=(await client.tabs({action:'list'})).tabs.find(row=>row.opener===browser.id);if(child)break;await new Promise(r=>setTimeout(r,50));}assert(child);
 let pending;while(performance.now()<end){pending=(await client.download({browser:child.id,action:'list'})).downloads.find(row=>row.state==='pending');if(pending)break;await new Promise(r=>setTimeout(r,50));}assert(pending);
 await client.download({browser:child.id,action:'accept',download:pending.id});await client.download({browser:child.id,action:'wait',download:pending.id});
 const destination=new URL('saved-popup.bin',directory);paths.push(destination);
 const result=await client.save({browser:child.id,download:pending.id,path:'saved-popup.bin'});assert.equal(result.saved,true);assert.deepEqual(await readFile(destination),bytes);saved++;
 await client.download({browser:child.id,action:'release',download:pending.id});child=null;
 assert.deepEqual(await sdk.call('files.receive.list',{}),[]);
 await writeFile(output,JSON.stringify({passed:true,backend:'actual-public-c-js-webview2',saved,exact_binary_bytes:true,post_blob_unknown_saved:true,
 original_download_retained:true,existing_targets_preserved:true,same_identity_not_replayed:true,foreign_context_denied:true,closed_popup_saved:true,no_private_bytes_or_paths:true})+'\n');
}catch(error){await writeFile(output,JSON.stringify({passed:false,stage,code:error.code,message:String(error.message||error).slice(0,512),details:error.details})+'\n');throw error;}
finally{
 for(const id of [child?.id,browser?.id])if(id)try{await client.tabs({action:'close',browser:id});}catch{}
 for(const path of paths)await unlink(path).catch(error=>{if(error.code!=='ENOENT')throw error;});
}
