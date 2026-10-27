import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
import {createLocalApplicationClient} from '/js/api/application.js';
import {mountBrowserView} from '/js/features/workbench/browser-view.js';
const context=PROBE_CONTEXT,origin=PROBE_HTTP_ORIGIN,originalWorkspace=PROBE_DIRECTORY,client='download-ui-qa',output='qa/download-ui.json';
let api=createLocalApplicationClient(()=>context);const bridge=createNativeBrowserBridge(),abort=new AbortController(),mount={};
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));let browser,view,checks=0,stage='startup',receiver;
const assert=(value,text)=>{checks++;if(!value)throw Error(text);};
const root=document.querySelector('#browser');
async function until(predicate,text,ms=15000){const end=performance.now()+ms;while(performance.now()<end){if(await predicate())return;await pause(50);}throw Error(text);}
const button=(text,scope=root)=>[...scope.querySelectorAll('button')].find(node=>node.textContent===text&&!node.disabled&&!node.closest('[hidden]'));
async function operation(method,params){const id='sdk-'+crypto.randomUUID().replaceAll('-','');await api.start(method,params,id);
  await until(async()=>{const state=await api.call('operation.get',{id});if(!state.terminal)return false;assert(state.state==='succeeded','Fixture SDK job failed');return true;},'Fixture SDK job timed out');return{id,result:await api.result(id)};}
let report;
try{
  await api.call('client.hello',{client,context:{}});
  browser=await api.call('browser.get',{id:PROBE_BROWSER});
  view=await mountBrowserView(root,{row:{resource:{id:browser.id},owner:browser.owner,context:browser.context,title:'Download QA'},sdk:api,signal:abort.signal,mount,bridge,clientId:client,
    shown:()=>true,alive:()=>!abort.signal.aborted,updateTitle:()=>{},openExternal:()=>{},onEnd:()=>{},addContext:()=>{}});
  stage='offer';await until(()=>button('接受下载')||root.querySelector('.wb-browser-download-toggle')?.dataset.count==='1','No download badge');
  assert(root.querySelector('.wb-downloads').hidden,'Downloads opened without user input');
  const surface=root.querySelector('.wb-browser-surface'),before=surface.getBoundingClientRect().top;
  root.querySelector('.wb-browser-download-toggle').click();
  await until(()=>button('接受下载'),'Pending response did not appear');
  const pending=button('接受下载');pending.focus();const identity=pending;
  await pause(1700);assert(button('接受下载')===identity&&document.activeElement===identity,'Polling replaced a focused control');
  const tray=root.querySelector('.wb-downloads'),after=surface.getBoundingClientRect().top;
  assert(after>before&&after>=tray.getBoundingClientRect().bottom-1,'Tray overlaps native surface');
  await until(async()=>Math.abs((await view.execute('state')).bounds.y-Math.round(surface.getBoundingClientRect().top)*devicePixelRatio)<=2,'Native viewport did not follow tray bounds');
  const native=await view.execute('state');assert(native.viewport_attached&&native.controller_bounds.height===native.bounds.height,'Native controller viewport not resized');
  stage='accept';pending.click();await until(()=>root.querySelector('[data-kind="download"] .wb-download-state')?.textContent.includes('已校验'),'Accept did not complete');
  assert(!(await view.execute('state')).manual,'Accept unexpectedly took manual control');
  assert(!button('保存到任务')&&!(await api.call('browser.get',{id:browser.id})).context.workspace,'Legacy save was enabled before root confirmation');
  stage='legacy-workspace';button('读取原任务目录').click();await until(()=>button('确认使用此目录'),'Original task review not visible');
  assert(root.querySelector('.wb-download-binding').textContent.includes(originalWorkspace),'Review substituted a current/default workspace');
  assert(!(await api.call('browser.get',{id:browser.id})).context.workspace,'Lookup changed the original binding without confirmation');
  button('确认使用此目录').click();await until(()=>button('保存到任务'),'Confirmation did not enable saving');
  browser=await api.call('browser.get',{id:browser.id});assert(browser.context.workspace===originalWorkspace&&browser.context.workspace_snapshot,'Confirmed binding was not persisted');
  api=api.bind({...browser.context,extension:browser.owner});
  await view.execute('takeover');assert((await view.execute('state')).manual,'Manual control fixture not established');
  stage='save';button('保存到任务').click();const form=root.querySelector('[data-kind="download"] .wb-download-save'),path=form.querySelector('input');
  path.value='ui-saved.bin';path.focus();await pause(1700);assert(path.value==='ui-saved.bin'&&document.activeElement===path,'Polling lost the edited path or focus');
  form.requestSubmit();await until(()=>root.querySelector('[data-kind="download"] .wb-download-state').textContent.includes('已保存：ui-saved.bin'),'Manual UI save did not confirm');
  const source=(await view.execute('downloads',{action:'list'})).downloads[0],file=await api.call('files.digest',{path:'ui-saved.bin'});
  assert(file.sha256===source.sha256&&file.size===source.size,'UI saved bytes differ');assert((await view.execute('state')).manual,'Save handed control to Agent');
  stage='recovery';const body=new TextEncoder().encode('recovery-ui-fixture'),sha=[...new Uint8Array(await crypto.subtle.digest('SHA-256',body))].map(n=>n.toString(16).padStart(2,'0')).join(''),
    rootInfo=await api.call('files.stat',{path:'.'}),stream=await api.call('stream.open',{capacity:65536});
  receiver='sdk-'+crypto.randomUUID().replaceAll('-','');await api.start('files.receive.start',{handle:stream.handle,root_snapshot:rootInfo.revision,destination:'ui-recovered.bin',size:body.length,sha256:sha,timeout_ms:15000},receiver);
  await api.call('stream.write',{handle:stream.handle,base64:btoa('recovery-ui-fixture')});await api.call('stream.close',{handle:stream.handle});
  await until(async()=>(await api.call('operation.get',{id:receiver})).terminal,'Receiver did not finish');await api.call('stream.release',{handle:stream.handle});
  await until(()=>button('保存暂存文件'),'Ready staging not visible');button('保存暂存文件').click();
  const recovery=root.querySelector('[data-kind="recovery"]');recovery.querySelector('form').requestSubmit();
  await until(()=>recovery.querySelector('.wb-download-state').textContent.includes('上次已确认保存'),'Recovered publication not visible');
  assert((await api.call('files.digest',{path:'ui-recovered.bin'})).sha256===sha,'Recovered file bytes differ');
  button('核对结果',recovery).click();await until(()=>root.querySelector('.wb-download-notice').textContent.includes('完整哈希一致'),'Reconciliation not visible');
  assert((await api.call('files.receive.get',{id:receiver})).publication_verdict==='verified','UI reconciliation not recorded');
  await api.call('files.receive.release',{id:receiver});await api.call('operation.release',{id:receiver});receiver=null;
  assert((await api.call('files.digest',{path:'ui-recovered.bin'})).sha256===sha,'Staging cleanup removed saved file');
  const count=root.querySelector('.wb-browser-download-toggle').dataset.count;
  root.querySelector('.wb-browser-download-toggle').click();await until(()=>tray.hidden,'Toggle did not collapse tray');
  assert(root.querySelector('.wb-browser-download-toggle').dataset.count===count,'Collapse cleared retained metadata');
  report={passed:true,checks,backend:'actual-mounted-browser-view-webview2',accept_without_takeover:true,manual_save_exact_bytes:true,
    frozen_workspace:true,legacy_original_task_review:true,adoption_without_response_recreation:true,focused_controls_stable:true,tray_resizes_native_surface:true,recovery_publish_and_reconcile:true,collapse_retains_download:true};
}catch(error){report={passed:false,stage,checks,error:String(error.message||error).slice(0,512)};}
finally{
  abort.abort();mount.dispose?.();mount.observer?.disconnect();
  if(receiver)try{await api.call('files.receive.release',{id:receiver});await api.call('operation.release',{id:receiver});}catch{}
  if(browser){try{await bridge.call('close',{browser:browser.id});await api.call('browser.close',{id:browser.id});}catch{}}
  bridge.dispose();
}
await api.call('storage.write',{path:output,text:JSON.stringify(report)});document.querySelector('#status').textContent=report.passed?'Download UI checks passed':report.error;
