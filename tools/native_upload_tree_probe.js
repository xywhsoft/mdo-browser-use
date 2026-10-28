import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
const bridge=createNativeBrowserBridge(),browser='browser-use-tree-staging';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));let checks=0,report;
const assert=(value,message)=>{checks++;if(!value)throw Error(message);};
const hash=async bytes=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(v=>v.toString(16).padStart(2,'0')).join('');
async function sdk(method,params){
  const boot=await fetch('/api/v1/bootstrap'),token=boot.headers.get('x-mdo-write-token');await boot.arrayBuffer();
  const response=await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
    body:JSON.stringify({method,context:{extension:'browser-use-qa'},params})});
  const result=await response.json();if(!response.ok)throw Error('Cannot collect native tree gate evidence');return result.data;
}
const save=(value,path='qa/page-observation.json')=>sdk('storage.write',{path,text:JSON.stringify(value)});
const cacheRoot=TREE_CACHE_ROOT;
async function reservations(){
  const value=await sdk('files.directory',{path:cacheRoot,hidden:true,limit:128});
  assert(Array.isArray(value.items)&&!value.more,'Independent filesystem observation was incomplete');
  return value.items.filter(item=>item.name!=='.guard').length;
}
async function emptyCache(){
  const end=performance.now()+2500;let count;
  do{count=await reservations();if(!count)break;await pause(50);}while(performance.now()<end);
  return count===0;
}
async function denied(call,message){let error;try{await call();}catch(value){error=value;}assert(error?.message.includes(message),'Expected native rejection: '+message);}
try{
  await save({state:'entered'},'qa/page-trace.json');
  await bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html',visible:false});
  let state;for(let i=0;i<150;i++){state=await bridge.call('state',{browser});if(!state.loading)break;await pause(20);}
  assert(!state.loading&&state.upload_capability,'Native tree fixture did not become ready');
  const call=params=>bridge.call('upload',{browser,navigation:state.navigation,...params});
  const bytes=Uint8Array.from({length:131075},(_,i)=>i%251),tail=new TextEncoder().encode('end');
  const empty=await hash(new Uint8Array()),long='n'.repeat(240);
  const tree={name:'selected-中文',directories:['nested','nested/empty','空目录'],files:[
    {path:'nested/data.bin',size:bytes.length,sha256:await hash(bytes)},
    {path:'zero.txt',size:0,sha256:empty},{path:'nested/'+long,size:tail.length,sha256:await hash(tail)}]};
  const begin=(key,directory=tree,other={})=>call({action:'begin',upload_key:key,timeout_ms:60000,directory,...other});
  const job=await begin('tree-main');assert(job.directory.name===tree.name&&job.files.length===3,'Tree status lost its hierarchy');
  assert(await reservations()===1,'Native staging did not create exactly one real reservation');
  assert(job.files[0].path==='nested/data.bin'&&job.files[2].name===long,'Tree status changed relative paths/names');
  assert(!JSON.stringify(job).includes('\\\\?\\'),'Tree status exposed a private native path');
  assert((await begin('tree-main')).ticket===job.ticket,'Identical directory admission duplicated its reservation');
  await denied(()=>begin('tree-main',{...tree,name:'changed'}),'identity conflicts');
  let offset=0;
  while(offset<bytes.length){
    const slice=bytes.slice(offset,offset+65536),base64=btoa(String.fromCharCode(...slice));
    const sent=await call({action:'chunk',ticket:job.ticket,file:0,offset,base64});
    assert(sent.files[0].received===offset+slice.length,'Directory byte stream lost its ordered cursor');
    if(!offset){const replay=await call({action:'chunk',ticket:job.ticket,file:0,offset,base64});assert(replay.files[0].received===slice.length,'Identical chunk replay appended bytes twice');}
    offset+=slice.length;
  }
  await call({action:'chunk',ticket:job.ticket,file:2,offset:0,base64:btoa('end')});
  const sealed=await call({action:'seal',ticket:job.ticket});assert(sealed.sealed&&!sealed.dispatched,'A complete directory did not seal without website effects');
  const unsupported=await call({action:'assign',ticket:job.ticket,document:'not-a-page-ref',ref:'e1'});
  assert(!unsupported.accepted&&!unsupported.dispatched&&unsupported.error.code==='unsupported_capability','Unimplemented directory assignment reached native input');
  assert(!(await call({action:'status',ticket:job.ticket})).retained,'Undispatched directory bytes became retained');
  await call({action:'cancel',ticket:job.ticket});
  assert(await emptyCache(),'Cancelled directory required another native upload/status/close to reclaim files');
  const bad=[
    {...tree,name:'..'}, {...tree,files:[{...tree.files[0],path:'../escape'}]},
    {...tree,directories:['nested','NESTED']}, {...tree,files:[{...tree.files[0],path:'nested/a'},{...tree.files[0],path:'nested/A'}]},
    {...tree,files:[{...tree.files[0],path:'missing/a'}]}, {...tree,files:[{...tree.files[0],size:'3'}]},
    {...tree,directories:[123]}, {...tree,name:123}, {...tree,files:[]},
    {...tree,files:[{...tree.files[0],path:'COM¹.txt'}]}, {...tree,files:[{...tree.files[0],path:'nested/a/'}]},
  ];
  for(let i=0;i<bad.length;i++)await denied(()=>begin('tree-invalid-'+i,bad[i]),'bounded, unambiguous relative tree manifest');
  await denied(()=>begin('tree-both',tree,{files:[{name:'a',size:0,sha256:empty}]}),'live document, exact key');
  assert(await reservations()===0,'Invalid directory manifests left physical reservations');
  const wrong={name:'hash-check',directories:[],files:[{path:'a.txt',size:3,sha256:'0'.repeat(64)}]};
  const corrupt=await begin('tree-wrong-hash',wrong);
  await call({action:'chunk',ticket:corrupt.ticket,file:0,offset:0,base64:btoa('abc')});
  await denied(()=>call({action:'seal',ticket:corrupt.ticket}),'complete manifest');
  assert(await emptyCache(),'Rejected directory hash left staged bytes without another native action');
  await denied(()=>call({action:'status',ticket:corrupt.ticket}),'expired');
  const maximum={name:'file-count',directories:[],files:Array.from({length:128},(_,i)=>({path:'f'+i,size:0,sha256:empty}))};
  const count=await begin('tree-count',maximum);assert(count.files.length===128,'Native directory file-count boundary failed');
  assert((await call({action:'seal',ticket:count.ticket})).sealed,'Native empty-file directory hashes did not seal');
  await call({action:'cancel',ticket:count.ticket});
  const many={name:'directory-count',directories:Array.from({length:128},(_,i)=>'d'+i),files:[{path:'a',size:0,sha256:empty}]};
  const quota=[];for(let i=0;i<3;i++)quota.push(await begin('tree-dir-quota-'+i,many));
  await denied(()=>begin('tree-dir-quota-overflow',many),'directory limit reached');
  for(const item of quota)await call({action:'cancel',ticket:item.ticket});
  assert(await emptyCache(),'Directory quota cancellation did not drain its real payloads');
  const expiring=await begin('tree-expiry',{name:'expire',directories:['empty'],files:[{path:'a',size:0,sha256:empty}]},{timeout_ms:1000});
  assert(await reservations()===1,'Expiry fixture was not staged');await pause(1700);
  assert(await reservations()===0,'An expiry/status request masked a missing native directory collector');
  await denied(()=>call({action:'status',ticket:expiring.ticket}),'expired');
  const ordinary=await call({action:'begin',upload_key:'v1-after-v2',files:[{name:'ordinary.txt',size:0,sha256:empty}]});
  assert((await call({action:'seal',ticket:ordinary.ticket})).sealed,'Regular file staging regressed after directory jobs');
  await call({action:'cancel',ticket:ordinary.ticket});
  report={passed:true,checks,backend:'actual-webview2-directory-staging',native_upload_staging:true,
    complete_directory_hashes:true,relative_paths_and_empty_directories:true,idempotent_begin_and_chunks:true,
    assignment_explicitly_unsupported:true,ambiguous_manifests_denied:true,wrong_hash_reclaimed:true,
    file_count_boundary:128,global_payload_directory_limit:512,expiry_reclaimed:true,regular_v1_staging:true};
}catch(error){report={passed:false,checks,error:String(error.stack||error)};}
finally{try{await bridge.call('close',{browser});}catch{}bridge.dispose();}
await save(report);
