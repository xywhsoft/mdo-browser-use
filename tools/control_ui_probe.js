// Actual component, DOM, broker and WebView2. Only failed control replies and
// delayed UI delivery below are injected; successful control uses the host.
import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
import {createLocalApplicationClient} from '/js/api/application.js';
import {mountBrowserView} from '/js/features/workbench/browser-view.js';
const context={extension:'browser-control-ui-qa',project:'default'},client='browser-control-ui-qa';
const api=createLocalApplicationClient(()=>context),native=createNativeBrowserBridge(),abort=new AbortController(),mount={};
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let browser,view,checks=0,mode='',releaseDelivery,stage='startup',report;
const calls=[];
const assert=(value,message)=>{checks++;if(!value)throw Error(message);};
async function until(predicate,message){const end=performance.now()+15000;while(performance.now()<end){if(await predicate())return;await pause(30);}throw Error(message);}
const bridge={available:()=>native.available(),async call(command,params,options){
  if(command==='release'&&mode==='structured')return{ok:false,manual:true,error:{code:'network_owner_changed'},network_cleanup_confirmed:false};
  if(command==='release'&&mode==='malformed')return{manual:'false',error:{code:'bad_reply'}};
  if(command==='release'&&mode==='scalar')throw Error('network_return_blocked');
  calls.push(command);const result=await native.call(command,params,options);
  if(command==='release'&&mode==='held')await new Promise(resolve=>{releaseDelivery=resolve;});
  return result;
}};
const style=document.createElement('style');style.textContent='body{margin:0;background:#171717;color:#eee;font-family:system-ui}#browser{position:absolute;inset:40px 0 0;display:flex;flex-direction:column;overflow:hidden}';document.head.append(style);
const css=document.createElement('link');css.rel='stylesheet';css.href='/css/workbench.css';document.head.append(css);
const root=document.createElement('section');root.id='browser';root.className='wb-view';document.body.append(root);
try{
  await api.call('client.hello',{client});
  for(const old of await api.call('browser.list',{}))if(old.key===client)await api.call('browser.close',{id:old.id});
  browser=await api.call('browser.create',{client,key:client,url:location.origin+'/__browser_use_page__.html'});
  view=await mountBrowserView(root,{row:{resource:{id:browser.id},owner:browser.owner,context:browser.context},sdk:api,
    signal:abort.signal,mount,bridge,clientId:client,shown:()=>true,updateTitle:()=>{},openExternal:()=>{},onEnd:()=>{},addContext:()=>{}});
  await until(()=>root.dataset.browserPhase==='ready','Mounted page did not become ready');
  const button=root.querySelector('.wb-browser-manual'),status=root.querySelector('.wb-browser-status');
  const page=await native.call('state',{browser:browser.id});
  stage='native-takeover';button.click();await until(()=>button.getAttribute('aria-pressed')==='true'&&!button.disabled,'Actual takeover failed');
  assert((await native.call('state',{browser:browser.id})).manual===true,'Host did not transfer manual control');
  for(const failure of ['structured','malformed','scalar']){
    stage='injected-'+failure;mode=failure;button.click();
    await until(()=>!button.disabled&&status.textContent.includes('交还控制失败'),'Failed control notice missing');
    assert(button.getAttribute('aria-pressed')==='true','Failed reply granted UI authority');
    assert(root.dataset.browserPhase==='ready'&&root.querySelector('.wb-browser-message').hidden,'Failed control hid the webpage');
    assert((await native.call('state',{browser:browser.id})).manual===true,'Fault injection changed actual native ownership');
    if(failure==='structured')assert(status.textContent.includes('清理尚未确认'),'Unconfirmed network cleanup was hidden');
    if(failure==='scalar')assert(status.textContent.includes('文件选择'),'Canonical refusal did not explain recovery');
    await pause(1600);assert(root.dataset.browserPhase==='ready'&&mount.status.status==='ready','Polling converted a control error into a page error');
  }
  stage='native-return-held';mode='held';button.click();await until(()=>releaseDelivery,'Native return was not acknowledged');
  assert(button.disabled&&button.getAttribute('aria-pressed')==='true','UI granted control before its original reply');
  assert((await native.call('state',{browser:browser.id})).manual===false,'Held return did not exercise actual native release');
  button.click();await pause(1700);assert(button.disabled&&button.getAttribute('aria-pressed')==='true','Early poll overwrote pending UI control');
  assert(calls.filter(c=>c==='release').length===1,'Disabled control replayed native release');
  releaseDelivery();await until(()=>!button.disabled&&button.getAttribute('aria-pressed')==='false','Original receipt did not update UI control');
  const after=await native.call('state',{browser:browser.id});
  assert(after.url===page.url&&after.title===page.title&&after.viewport_attached,'Same native page was lost');
  assert(calls.filter(c=>c==='create').length===1&&!calls.includes('navigate'),'Control recreated or navigated the webpage');
  assert(status.hidden&&mount.status.message==='','Successful return retained an obsolete failure notice');
  report={passed:true,checks,backend:'actual-mounted-browser-view-webview2',native_takeover:true,native_return:true,
    same_page:true,injected_control_errors:['structured','malformed','scalar'],early_native_state_cannot_grant_ui:true,
    duplicate_native_release:false,control_errors_are_not_page_errors:true,android_provider_qualified:false};
}catch(error){report={passed:false,stage,checks,error:String(error.message||error).slice(0,512)};}
finally{
  releaseDelivery?.();abort.abort();mount.dispose?.();mount.observer?.disconnect();
  if(browser){try{await native.call('close',{browser:browser.id});await api.call('browser.close',{id:browser.id});}catch{}}
  native.dispose();
}
// native_probe.py owns this existing scoped report and restores headless mode.
const reports=createLocalApplicationClient(()=>({extension:'browser-use-qa'}));
await reports.call('storage.write',{path:'qa/page-observation.json',text:JSON.stringify(report)});
document.querySelector('#status').textContent=report.passed?'Browser control UI checks passed':report.error;
