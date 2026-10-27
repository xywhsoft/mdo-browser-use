import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { ensureNativeBrowser } from "../host/native-view.js";

const original = await readFile(new URL("../host/popup-manager.js",import.meta.url),"utf8");
const source = original.replace('import { safeBrowserUrl } from "./model.js";',
  'const safeBrowserUrl = value => {const u=new URL(value);if(!["http:","https:"].includes(u.protocol)||u.username||u.password)throw Error("Unsafe URL");return u.href;};')
  .replace('import { ensureNativeBrowser } from "./native-view.js";',`const ensureNativeBrowser=${ensureNativeBrowser.toString()};`);
const { createBrowserPopups } = await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const ticket = "popup-{00000000-0000-0000-0000-000000000001}";
const context = { device:"original-device",project:"original-project",session:"original-session",run:"" };
const descriptor = { id:ticket,opener:"source",client:"display" };

test("ephemeral native tickets require the original display client and stop on a device change",async()=>{
  const calls=[];let live=true;
  const options={browser:{id:"child",opener:"source",client:"display",runtime:{popup_request:descriptor}},clientId:"display",
    bridge:{async call(command){calls.push(command);live=false;return{created:true};}},
    sdk:{call(){throw Error("A changed device must not settle its old admission");}},alive:()=>live};
  await assert.rejects(ensureNativeBrowser(options),{name:"AbortError"});assert.deepEqual(calls,["create"]);
  live=true;calls.length=0;
  await assert.rejects(ensureNativeBrowser({...options,clientId:"another-display"}),/之前的窗口/);
  assert.equal(calls.length,0);
});

function fixture({settled=false,queued=[]}={}){
  const calls=[],opened=[],closed=[],errors=[];
  let pending=[{id:ticket,url:"https://example.com/popup",user_initiated:true}],closeRequested=false;
  const row={id:"child",owner:"original-extension",opener:"source",client:"display",context,
    ...(settled?{}:{runtime:{popup_request:descriptor}})};
  const states=new Map([["child",{opener:"source"}]]);
  const api={bind(bound){assert.deepEqual(bound,{...context,extension:"original-extension"});return{async call(method,params){
    calls.push({method,params});if(method==="browser.popup.admit")return row;
    if(method==="browser.popup.settle")return{settled:true};if(method==="browser.close")return{};
    if(method==="client.poll"){assert.equal(params.client,"display");return queued;}
    throw Error("Unexpected SDK call "+method);
  }}}};
  const views=new Map([["source",{row:{id:"source-panel",owner:"original-extension",context},
    async execute(){return{popup_capability:true,closed_by_page:closeRequested};}}]]);
  const bridge={async call(method,params){calls.push({method,params});
    if(method==="popups"&&params.action==="list")return{popups:pending};
    if(method==="popups"&&params.action==="dismiss"){pending=[];return{dismissed:true};}
    if(method==="state")return states.get(params.browser);
    if(method==="create"){assert.equal(params.popup,ticket);assert.equal(params.opener,"source");pending=[];return{created:true};}
    if(method==="close")return{};throw Error("Unexpected native call "+method);
  }};
  const manager=createBrowserPopups({api,bridge,views,clientId:"display",alive:()=>true,
    async open(child,sdk,reveal){opened.push({child,reveal});},async closePanels(id){closed.push(id);},onError:error=>errors.push(error)});
  return{manager,calls,opened,closed,errors,views,bridge,api,setClose(){closeRequested=true;},clear(){pending=[];},row};
}

test('automatic popup close waits for its final UI acknowledgement',async()=>{
  const queued=[{action:'workbench.browser',params:{id:'source',command:'download',action:'release'}}];
  const f=fixture({queued});f.setClose();await f.manager.reconcile('source');
  assert(f.views.has('source'));assert.equal(f.closed.length,0);assert(!f.calls.some(call=>call.method==='browser.close'));
  queued[0].params.id='another-browser';await f.manager.reconcile('source');
  assert(!f.views.has('source'));assert.deepEqual(f.closed,['source']);
  f.manager.dispose();
});

test("duplicate native notifications adopt one child in the original task",async()=>{
  const f=fixture();await Promise.all([f.manager.reconcile("source"),f.manager.reconcile("source")]);
  assert.equal(f.errors.length,0);assert.equal(f.calls.filter(c=>c.method==="create").length,1);
  assert.equal(f.calls.filter(c=>c.method==="browser.popup.admit").length,1);
  assert.equal(f.opened.length,1);assert.equal(f.opened[0].reveal,"activate");assert.ok(f.views.has("child"));
  f.manager.dispose();
});

test("settled admissions are inspected instead of recreating a native window",async()=>{
  const f=fixture({settled:true});await f.manager.reconcile("source");
  assert.equal(f.errors.length,0);assert.equal(f.calls.filter(c=>c.method==="create").length,0);
  assert.equal(f.opened.length,1);f.manager.dispose();
});

test("uncertain native effects retain their admitted receipt without replacement",async()=>{
  const f=fixture();const original=f.bridge.call;
  f.bridge.call=async(method,params)=>{if(method==="create")throw Error("Native reply lost");return original(method,params);};
  await f.manager.reconcile("source");await f.manager.reconcile("source");
  assert.equal(f.calls.filter(c=>c.method==="browser.popup.admit").length,1);
  assert.equal(f.calls.filter(c=>c.method==="browser.close").length,0);
  assert.equal(f.opened.length,0);assert.equal(f.errors.length,1);f.manager.dispose();
});

test("page close requests remove exactly their scoped browser and panels",async()=>{
  const f=fixture();f.setClose();await f.manager.reconcile("source");
  assert.deepEqual(f.closed,["source"]);assert.ok(!f.views.has("source"));
  assert.deepEqual(f.calls.map(c=>c.method),["client.poll","browser.close","close"]);f.manager.dispose();
});

test("the actual native bridge serializes concurrent creation and rejects a changed popup identity",async()=>{
  const code=await readFile(new URL("../.mdo/source/app/web/js/features/workbench/native-browser.js",import.meta.url),"utf8");
  const { createNativeBrowserBridge }=await import(`data:text/javascript;base64,${Buffer.from(code).toString("base64")}`);
  const savedWindow=globalThis.window,savedChrome=globalThis.chrome;
  const events=new EventTarget(),sent=[];let receive;
  globalThis.window=events;globalThis.chrome={webview:{addEventListener(name,listener){receive=listener;},removeEventListener(){},postMessage(json){sent.push(JSON.parse(json));}}};
  const bridge=createNativeBrowserBridge();
  try{
    const params={browser:"child",popup:ticket,opener:"source"};
    const first=bridge.call("create",params),second=bridge.call("create",params);
    assert.equal(sent.length,1);
    await assert.rejects(bridge.call("create",{...params,intercept_downloads:true}),/下载拦截策略/);
    assert.equal(sent.length,1,"A conflicting policy must not dispatch another native creation");
    await assert.rejects(bridge.call("create",{...params,opener:"another-source"}),/另一个弹出请求/);
    receive({data:{channel:"mdo-browser",id:sent[0].id,result:{created:true}}});await first;
    assert.equal((await second).created,true);
    assert.equal(sent.length,1,"A concurrent reader must not reset the newly displayed view's visibility or bounds");
    const start=sent.length;
    const cancelled=bridge.call("create",{browser:"replace"}).catch(error=>error);
    const closing=bridge.call("close",{browser:"replace"});
    receive({data:{channel:"mdo-browser",id:sent[start+1].id,result:{}}});await closing;
    const replacement=bridge.call("create",{browser:"replace"});
    assert.equal(sent.length,start+3,"Close did not detach the old in-flight creation promise");
    receive({data:{channel:"mdo-browser",id:sent[start].id,error:"Browser creation was cancelled"}});
    assert.equal((await cancelled).message,"Browser creation was cancelled");
    receive({data:{channel:"mdo-browser",id:sent[start+2].id,result:{created:true}}});
    assert.equal((await replacement).created,true);
    const uploadIndex=sent.length;
    const upload=bridge.call("upload",{browser:"replace",action:"chunk"});
    assert.equal(bridge.busy(),true,"A pending native file chunk was omitted from the bridge busy state");
    receive({data:{channel:"mdo-browser",id:sent[uploadIndex].id,result:{}}});await upload;
    assert.equal(bridge.busy(),false);
  }finally{bridge.dispose();globalThis.window=savedWindow;globalThis.chrome=savedChrome;}
});


test('required initial interception rejects an old host before sending its URL',async()=>{
  const calls=[];
  await assert.rejects(ensureNativeBrowser({browser:{id:'owned',url:'https://example.com/file',download_policy:'intercept'},clientId:'display',
    bridge:{async call(command){calls.push(command);return{};}},sdk:{call(){throw Error('No admission should be settled');}}}),/打开网址前/);
  assert.deepEqual(calls,['capabilities']);
});

test('unsupported Android automation sends no URL and preserves manual browsing',async()=>{
  for(const reason of ['isolated_world_unsupported','android_adapter_incomplete','unknown-private-provider-error']){
    const calls=[];const bridge={async call(command,params){calls.push(command);if(command==='capabilities')return{browser_use:{available:false,reason}};
      assert.equal(params.automation,undefined);return{created:true};}};
    await assert.rejects(ensureNativeBrowser({browser:{id:'android',url:'https://example.com'},bridge,requireAutomation:true,requireDownloads:true}),error=>{
      assert.equal(error.code,'unsupported_capability');assert(!error.message.includes('unknown-private'));return true;
    });assert.deepEqual(calls,['capabilities']);
    await ensureNativeBrowser({browser:{id:'manual',url:'https://example.com'},bridge});assert.deepEqual(calls,['capabilities','create']);
  }
});

test('automation admission retains download acknowledgement on supported existing hosts',async()=>{
  const calls=[];await ensureNativeBrowser({browser:{id:'windows'},requireAutomation:true,requireDownloads:true,bridge:{async call(command,params){
    calls.push(command);if(command==='capabilities')return{initial_download_interception:true};
    assert.equal(params.automation,true);assert.equal(params.intercept_downloads,true);return{created:true,downloads_intercepted:true};
  }}});assert.deepEqual(calls,['capabilities','create']);
});

test('creation acknowledgement and popup settlement preserve the required policy',async()=>{
  const calls=[];
  const browser={id:'child',opener:'source',client:'display',url:'https://example.com/file',download_policy:'intercept',runtime:{popup_request:descriptor}};
  const result=await ensureNativeBrowser({browser,clientId:'display',bridge:{async call(command,params){calls.push(command);
    if(command==='capabilities')return{initial_download_interception:true};assert.equal(params.intercept_downloads,true);return{created:true,downloads_intercepted:true};}},
    sdk:{async call(method){calls.push(method);return{settled:true};}}});
  assert(result.downloads_intercepted);assert.deepEqual(calls,['capabilities','create','browser.popup.settle']);
});

test('missing policy confirmation does not settle or replay a popup',async()=>{
  const calls=[];
  await assert.rejects(ensureNativeBrowser({browser:{id:'child',opener:'source',client:'display',download_policy:'intercept',runtime:{popup_request:descriptor}},clientId:'display',
    bridge:{async call(command){calls.push(command);return command==='capabilities'?{initial_download_interception:true}:{created:true};}},
    sdk:{call(){throw Error('Unconfirmed adoption must not be settled');}}}),/未确认/);
  assert.deepEqual(calls,['capabilities','create']);
});

test('a manual popup never returns control to the Agent while acknowledging creation',async()=>{
  const calls=[];
  const result=await ensureNativeBrowser({browser:{id:'child',download_policy:'intercept'},clientId:'display',bridge:{async call(command){calls.push(command);
    return command==='capabilities'?{initial_download_interception:true}:{created:true,manual:true,downloads_intercepted:false};}},sdk:{call(){throw Error('No popup admission');}}});
  assert.equal(result.manual,true);assert.deepEqual(calls,['capabilities','create']);
});
