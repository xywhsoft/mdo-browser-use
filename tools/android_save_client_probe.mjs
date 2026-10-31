// Developer integration only: exact Android command fragments and actual C file
// services, with controlled renderer/framework peers and synthetic body bytes.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {readFile,writeFile,mkdir,unlink,lstat} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import {fileURLToPath} from 'node:url';
import {executeDownloadSave,cancelDownloadSave} from '../host/download-save.js';

const root=new URL('../',import.meta.url),env=JSON.parse(await readFile(new URL('.mdo/environment.json',root),'utf8'));
const output=new URL('.mdo/android-save-result.json',root),directory=new URL('.mdo/qa/android-save/',root);
await mkdir(directory,{recursive:true});assert(!(await lstat(directory)).isSymbolicLink());
const scope={extension:'android-save-integration-qa',project:'default',session:'android-save-integration',namespace:'private-save',member:'save-worker',workspace:fileURLToPath(directory).replace(/[\\/]$/,'')};
const origin=`http://127.0.0.1:${env.port}`,bootstrap=await fetch(origin+'/api/v1/bootstrap',{signal:AbortSignal.timeout(10000)}),token=bootstrap.headers.get('x-mdo-write-token');await bootstrap.arrayBuffer();
let checks=0,phase='startup';const check=(condition,message)=>{assert(condition,message);checks++;};
const api={async call(method,params={},options={}){
  const signal=options.signal?AbortSignal.any([options.signal,AbortSignal.timeout(15000)]):AbortSignal.timeout(15000);
  const response=await fetch(origin+'/api/v1/application/call',{method:'POST',signal,headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},body:JSON.stringify({method,params,context:scope})});
  const reply=await response.json();if(!response.ok)throw Object.assign(Error('C file service refused the integration operation'),{code:reply.error?.code});return reply.data;
}};
const context=await api.call('sdk.context');
await writeFile(output,'{"passed":false,"state":"running"}\n');
const linux=path=>'/mnt/'+path[0].toLowerCase()+path.slice(2).replaceAll('\\','/'),home=linux(fileURLToPath(root).replace(/[\\/]$/,'')),native=home+'/.mdo/qa/android-cdp-native';
const peer=spawn('wsl.exe',['--exec','/usr/lib/jvm/java-17-openjdk-amd64/bin/java','-Xcheck:jni','-Djava.library.path='+native,'-cp',native+'/classes','org.xserver.android.CdpDownloadSavePeer',native+'/download-save-files'],{cwd:fileURLToPath(root),stdio:['pipe','pipe','pipe'],windowsHide:true});
let serial=0,ready,stderr='',quit=false,download,cancelOnRead=false,corruptRead=false,sourceReads=0,publications=0,report;
const waiting=new Map(),readyPromise=new Promise((resolve,reject)=>{ready={resolve,reject};});
const failPending=error=>{ready.reject(error);for(const row of waiting.values()){clearTimeout(row.timer);row.reject(error);}waiting.clear();};
const completion=new Promise(resolve=>peer.once('close',(code,signal)=>{failPending(Error('Android save peer terminated: '+code+'/'+signal));resolve({code,signal});}));
peer.on('error',failPending);peer.stdin.on('error',failPending);peer.stderr.on('data',data=>{stderr=(stderr+data).slice(-8192);});
const lines=createInterface({input:peer.stdout});lines.on('line',line=>{
  try{const value=JSON.parse(line);if(value.id===0){ready.resolve(value.result);return;}const row=waiting.get(value.id);if(!row)return;waiting.delete(value.id);clearTimeout(row.timer);value.error?row.reject(Object.assign(Error('Native download command failed'),{code:value.error})):row.resolve(value.result);}
  catch(error){failPending(error);}
});
const rpc=(command,fields={})=>new Promise((resolve,reject)=>{if(peer.exitCode!==null||peer.signalCode!==null||peer.stdin.destroyed||peer.stdin.writableEnded){reject(Error('The original native save peer is closed'));return;}const id=++serial,timer=setTimeout(()=>{waiting.delete(id);reject(Error('Native save command observation expired'));},10000);waiting.set(id,{resolve,reject,timer});peer.stdin.write(JSON.stringify({id,command,...fields})+'\n');});
const expected=Buffer.from(Uint8Array.from({length:131075},(_,i)=>i%251)),hash=createHash('sha256').update(expected).digest('hex'),owned=[];
const absent=async path=>{try{await lstat(path);return false;}catch(error){if(error.code==='ENOENT')return true;throw error;}};
const view={async execute(command,params){
  check(command==='downloads','Only original retained-download commands are permitted');
  const result=await rpc('execute',{params});
  if(params.action==='read'){
    sourceReads++;if(cancelOnRead){cancelOnRead=false;await cancelDownloadSave('android-save-integration');}
    if(corruptRead)return{...result,id:'d-unrelated'};
  }
  return result;
}};
const run=async(name,options={})=>{
  const destination=new URL(name,directory);check(await absent(destination),'Interrupted output must be inspected before rerunning');
  let dispatched=false;const fail=(code,message)=>({handled:true,result:{ok:false,error:{code,message,effect_may_have_occurred:dispatched}}});
  const original=api.call;api.call=async(method,params,settings)=>{if(method==='operation.start'&&params.method==='files.receive.publish')publications++;return original(method,params,settings);};
  try{
    const rootSnapshot=await api.call('files.stat',{path:'.'});
    const result=await executeDownloadSave({browser:'android-save-integration',view,api,params:{download,path:name,root_snapshot:rootSnapshot.revision},
      action:{id:'uia-'+crypto.randomUUID().replaceAll('-',''),context},nativeAgent:false,deadline:performance.now()+20000,setDispatched:value=>{dispatched=value;},fail,guard:async()=>({state:{download_capability:true,manual:!!options.manual}})});
    if(result.result.saved){owned.push(destination);check((await readFile(destination)).equals(expected),'The C publisher lost original Android bytes');}
    check((await api.call('files.receive.list')).length===0,'A receiver reservation survived producer cleanup');return result.result;
  }finally{api.call=original;}
};
try{
  const initial=await readyPromise;check(initial.ready&&initial.framework_verified===false&&initial.provider_qualified===false,'Controlled peer scope changed');
  ({download}=await rpc('seed'));
  await rpc('execute',{params:{action:'accept',download,max_bytes:1048576,timeout_ms:10000}});
  let metadata;const end=performance.now()+10000;
  while(performance.now()<end){metadata=await rpc('execute',{params:{action:'get',download}});if(metadata.state==='completed')break;await new Promise(resolve=>setTimeout(resolve,10));}
  check(metadata.state==='completed'&&metadata.sha256===hash&&metadata.size===expected.length,'Original Android response did not verify');
  phase='save';const firstReads=sourceReads,saved=await run('verified.bin');check(saved.ok&&saved.saved&&saved.source_retained&&!saved.transfer.cleanup_pending,'Shared producer did not save');check(sourceReads-firstReads===3,'The saved original response did not traverse all three native ranges');
  check(!JSON.stringify(saved).includes('base64')&&!JSON.stringify(saved).includes('.mdo-receive-'),'Private source bytes/paths leaked into the save result');
  phase='conflict';const rootSnapshot=await api.call('files.stat',{path:'.'}),fail=(code,message)=>({handled:true,result:{ok:false,error:{code,message}}});
  const conflict=await executeDownloadSave({browser:'android-save-integration',view,api,params:{download,path:'verified.bin',root_snapshot:rootSnapshot.revision},action:{id:'uia-'+crypto.randomUUID().replaceAll('-',''),context},nativeAgent:false,deadline:performance.now()+20000,setDispatched:()=>{},fail,guard:async()=>({state:{download_capability:true}})});
  check(!conflict.result.ok&&conflict.result.error.code==='sdk_file_exists','An existing destination was overwritten');check((await readFile(new URL('verified.bin',directory))).equals(expected),'Conflict changed verified destination bytes');
  phase='source-mismatch';corruptRead=true;const beforeMismatch=publications,mismatch=await run('rejected.bin');corruptRead=false;check(!mismatch.ok&&mismatch.error.code==='download_snapshot_changed'&&publications===beforeMismatch,'A foreign native range reached publication');check(await absent(new URL('rejected.bin',directory)),'A rejected range published a destination');
  phase='cancel';cancelOnRead=true;const beforeCancel=publications,cancelled=await run('cancelled.bin');check(!cancelled.ok&&cancelled.error.code==='cancelled'&&publications===beforeCancel,'Producer cancellation admitted publication');check(await absent(new URL('cancelled.bin',directory)),'Cancellation published a new destination');
  phase='retired-source';check((await rpc('retire')).retired,'Original network did not retire');const retained=await run('retired.bin',{manual:true});check(retained.ok&&retained.saved&&retained.source_retained,'Completed original bytes could not be saved after network retirement');
  const beforeRelease=await rpc('stats');check(beforeRelease.claims===1&&beforeRelease.records===1&&beforeRelease.retained_reads===0,'Saving replayed the source or leaked range leases');
  await rpc('execute',{params:{action:'release',download}});download=null;const final=await rpc('quit');quit=true;peer.stdin.end();const exited=await completion;check(exited.code===0&&final.closed,'Native/controller cleanup did not complete');
  check(final.record_reservations===0&&final.file_reservations===0&&final.reserved_bytes===0&&final.pending_reads===0&&final.pending_releases===0,'Native cleanup retained resources');
  report={passed:true,exact_android_commands:true,actual_jvm_jni_controller_files:true,actual_shared_save_producer:true,actual_c_file_services:true,bytes:expected.length,sha256:hash,multi_range_reads:true,existing_destination_preserved:true,foreign_range_rejected:true,cancellation_before_publication:true,retired_completed_source_saved:true,original_source_claims:final.claims,native_cleanup:final,framework_verified:false,provider_qualified:false,public_android_qualified:false,model_calls:0};
}catch(error){await writeFile(output,JSON.stringify({passed:false,phase,code:error.code||'',message:String(error.message).slice(0,512),peer_stderr:stderr.slice(-2048)})+'\n');throw error;}
finally{
  if(!quit){if(download)try{await rpc('execute',{params:{action:'cancel',download}});await rpc('execute',{params:{action:'release',download}});}catch{}try{await rpc('quit');quit=true;peer.stdin.end();await completion;}catch{peer.stdin.end();}}
  for(const path of owned){const current=await readFile(path);check(current.equals(expected),'Changed destination evidence preserved');await unlink(path);}
  lines.close();
}
await writeFile(output,JSON.stringify({...report,checks},null,2)+'\n');
