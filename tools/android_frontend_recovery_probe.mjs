// Run after android_cdp_socket_probe.py has compiled the shared QA classes.
// One real dispatcher/driver -> JVM product fragments -> original JNI/worker;
// controlled UI/provider, not Android framework/device qualification.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {readFile,writeFile,readdir} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const qa=path.join(root,'.mdo/qa/android-cdp-native');
const resultPath=path.join(root,'.mdo/android-frontend-recovery-result.json');
await writeFile(resultPath,JSON.stringify({passed:false,state:'running',framework_qualified:false,provider_qualified:false})+'\n');
const unix=value=>process.platform==='win32'?'/mnt/'+value[0].toLowerCase()+value.slice(2).replaceAll('\\','/'):value;
const hash=async file=>createHash('sha256').update(await readFile(file)).digest('hex');
const moduleUrl=source=>`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
const source=async name=>(await readFile(path.join(root,'host',name+'.js'),'utf8'))
  .replace(/from (["'])\.\/automation-runtime\.js\1/g,`from ${JSON.stringify(new URL('../page/runtime.js',import.meta.url).href)}`);
let dispatcher=await source('automation-dispatch');
for(const name of ['capture-image','visual-snapshot','visual-dispatch','upload-dispatch','download-dispatch','download-save'])
  dispatcher=dispatcher.replace(`from "./${name}.js"`,`from ${JSON.stringify(new URL('../host/'+name+'.js',import.meta.url).href)}`);
const {executeBrowserAutomation}=await import(moduleUrl(dispatcher));
const driver=(await source('frame-driver')).replace("from './frame-reader.js'",`from ${JSON.stringify(moduleUrl(await source('frame-reader')))}`)
  .replace("from './frame-capture.js'",`from ${JSON.stringify(new URL('../host/frame-capture.js',import.meta.url).href)}`);
const {createFrameDriver}=await import(moduleUrl(driver));
const setup=JSON.parse(await readFile(path.join(root,'.mdo/android-client-tools/receipt.json'),'utf8'));
const timeout=async(promise,ms,label)=>{let timer;try{return await Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error(label+' timed out')),ms);})]);}finally{clearTimeout(timer);}};

async function classHashes(directory){
  const result={};for(const item of await readdir(directory,{withFileTypes:true})){
    const file=path.join(directory,item.name);if(item.isDirectory())Object.assign(result,await classHashes(file));
    else if(item.isFile()&&item.name.endsWith('.class'))result[path.relative(qa,file).replaceAll('\\','/')]=await hash(file);
  }return result;
}
async function run(mode){
  const args=[setup.java_home+'/bin/java','-Xcheck:jni','-Djava.library.path='+unix(qa),'-cp',unix(path.join(qa,'classes')),
    'org.xserver.android.CdpPopupWorkerProbe','preparation-publication-frontend-'+mode,unix(path.join(qa,'popup-files'))];
  const child=spawn(process.platform==='win32'?'wsl.exe':args.shift(),process.platform==='win32'?['--exec',...args]:args,{cwd:root,windowsHide:true,stdio:['pipe','pipe','pipe']});
  let buffer='',stderr='',report,started=false,finished=false,serial=0,bytes=0,readyResolve,readyReject;
  const pending=new Map(),ready=new Promise((resolve,reject)=>{readyResolve=resolve;readyReject=reject;});
  const closed=new Promise(resolve=>child.once('close',(code,signal)=>{
    const error=Error(`JVM fixture closed (${code??signal}): ${stderr.slice(-2000)}`);if(!started)readyReject(error);
    for(const item of pending.values())item.reject(error);pending.clear();resolve({code,signal});
  }));
  child.on('error',error=>readyReject(error));child.stdin.on('error',()=>{});
  child.stderr.on('data',data=>{stderr=(stderr+data.toString('utf8')).slice(-16384);});
  child.stdout.setEncoding('utf8');child.stdout.on('data',data=>{
    bytes+=Buffer.byteLength(data);if(bytes>2097152){readyReject(Error('Fixture output limit'));child.stdin.end();return;}buffer+=data;
    for(let end;(end=buffer.indexOf('\n'))>=0;){
      const line=buffer.slice(0,end).trim();buffer=buffer.slice(end+1);if(!line)continue;
      let packet;try{packet=JSON.parse(line);}catch{readyReject(Error('Invalid fixture packet'));child.stdin.end();continue;}
      if(packet.event==='ready'){started=true;if(packet.provider_qualified!==false)readyReject(Error('Fixture cannot qualify a provider'));else readyResolve();}
      else if(packet.passed===true&&packet.mode){report=packet;}
      else {const item=pending.get(packet.id);if(item){pending.delete(packet.id);item.resolve(packet.result);}}
    }
  });
  const rpc=async(command,params={})=>{
    const id=++serial,line=JSON.stringify({id,command,params});assert(Buffer.byteLength(line)<=300000);
    const promise=new Promise((resolve,reject)=>{pending.set(id,{resolve,reject});});child.stdin.write(line+'\n');
    try{return await timeout(promise,10000,'Original '+command+' receipt');}finally{pending.delete(id);}
  };
  let result,primary,cancelled=false;const calls=[];
  try{
    await timeout(ready,20000,'Original native popup startup');const initial=await rpc('state');assert.equal(initial.isolated_world.ready,false);
    assert.equal(initial.frame_automation,true);assert.equal(initial.isolated_world.adapter,'android-native-frames-v1');
    const prepareReplies=[];
    const execute=async(command,params)=>{calls.push(command==='frames'?'frames:'+params.action:command);const value=await rpc(command,params);
      if(command==='popup_prepare')prepareReplies.push(value?.error?.code||'ready');
      if(mode==='cancel'&&command==='popup_prepare')cancelled=true;return value;};
    const page=createFrameDriver({execute,nativeCapture:{navigation:initial.navigation}});
    const pageDriver=mode==='old-order'?{read:page.read}:page;
    result=await executeBrowserAutomation({command:mode==='wait'?'wait_for':'observe',params:{timeout_ms:8000,...(mode==='wait'?{until:'text',text:'Restored original native popup'}:{})},
      browser:'native-frontend-'+mode,action:{id:'native-frontend-'+mode},api:{async call(){return{state:cancelled?'cancelled':'pending'};}},view:{execute},pageDriver});
    if(mode==='old-order')assert.equal(result.result.error.code,'popup_protection_unavailable');
    else if(mode==='cancel')assert.equal(result.result.error.code,'cancelled');
    else {assert(result.result.ok,JSON.stringify({result:result.result,prepareReplies}));assert.equal(mode==='wait'?result.result.observation.text:result.result.text,'Restored original native popup');
      assert(calls.indexOf('frames:list')<calls.indexOf('popup_prepare'));}
    if(mode==='old-order'||mode==='cancel')assert.equal(result.result.error.effect_may_have_occurred,false);
  }catch(error){primary=error;throw error;}
  finally{
    try{if(started&&child.exitCode===null){await rpc('fixture_finish');finished=true;}child.stdin.end();const ended=await timeout(closed,20000,'Original native cleanup');
      assert.equal(ended.code,0,stderr);assert(finished&&report?.passed);assert.equal(report.product_frontend_recovery_tested,true);
      assert.equal(report.product_publication.cleanup_confirmed,true);assert.equal(report.product_publication.close_replies,1);
      const proof=report.product_frame_route;assert.equal(proof.actual_product_frontend,true);assert.equal(proof.browser_connections,1);assert.equal(proof.native_proof_connections,1);
      assert.equal(proof.provider_qualified,false);assert.equal(proof.worlds,mode==='old-order'?1:2);
      if(mode==='old-order'||mode==='cancel')assert.equal(proof.expressions,0);else assert(proof.expressions>=2);
    }catch(error){if(primary)primary.message+='; cleanup: '+error.message;else throw error;}
  }
  return{mode,passed:true,actual_dispatcher:true,original_jvm_product:report.product_frame_route,native_cleanup_confirmed:true,provider_qualified:false};
}
try{
  const classes=await classHashes(path.join(qa,'classes')),native=await hash(path.join(qa,'libxs.so'));
  const inputs=['host/automation-dispatch.js','host/frame-driver.js','host/frame-reader.js','host/frame-capture.js','page/runtime.js','tools/android_frontend_recovery_probe.mjs',
    'tests/fixtures/android-cdp-java/PopupProductHost.java.in','tests/fixtures/android-cdp-java/org/xserver/android/CdpPopupWorkerProbe.java',
    'host/android/browser-popup-preparation.inc.java','host/android/browser-document.inc.java','host/android/browser-frame-router.inc.java'];
  const source_sha256={},rows=[];for(const file of inputs)source_sha256[file]=await hash(path.join(root,file));
  for(const mode of ['old-order','observe','wait','cancel']){rows.push(await run(mode));process.stderr.write('Native frontend recovery '+mode+' passed\n');}
  assert.deepEqual(await classHashes(path.join(qa,'classes')),classes);assert.equal(await hash(path.join(qa,'libxs.so')),native);
  for(const [file,digest] of Object.entries(source_sha256))assert.equal(await hash(path.join(root,file)),digest,file);
  const result={passed:true,rows,original_jvm_jni:true,framework_qualified:false,provider_qualified:false,source_sha256,class_sha256:classes,native_library_sha256:native};
  await writeFile(resultPath,JSON.stringify(result,null,2)+'\n');
  process.stdout.write(JSON.stringify({passed:true,modes:rows.length,original_jvm_jni:true,framework_qualified:false,provider_qualified:false})+'\n');
}catch(error){await writeFile(resultPath,JSON.stringify({passed:false,state:'failed',error:String(error.message||error).slice(0,512),framework_qualified:false,provider_qualified:false})+'\n');
  process.stderr.write(String(error.stack||error)+'\n');process.exitCode=1;}
