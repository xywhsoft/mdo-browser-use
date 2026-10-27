import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const source = (await readFile(new URL("../host/automation-dispatch.js", import.meta.url), "utf8"))
  .replace('import { executeDownloadSave, downloadSaveState } from "./download-save.js";',`import {executeDownloadSave,downloadSaveState} from ${JSON.stringify(new URL('../host/download-save.js',import.meta.url).href)};`)
  .replace('import { browserRuntime } from "./automation-runtime.js";', 'function browserRuntime() {}')
  .replace('import { prepareScreenshot } from "./capture-image.js";', 'async function prepareScreenshot(){throw Error("An unapproved image path was reached")}')
  .replace('import { executeBrowserDownload } from "./download-dispatch.js";', 'async function executeBrowserDownload(){throw Error("Unexpected download path")}')
  .replace('import { executeFileUpload } from "./upload-dispatch.js";', 'async function executeFileUpload(){throw Error("Unexpected upload path")}');
const {executeBrowserAutomation:dispatch}=await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const executeBrowserAutomation=options=>dispatch({...options,browser:options.browser||options.view,view:{async execute(command,params){
  if(command==='downloads')return{ok:true,armed:true,downloads:[]};
  const result=await options.view.execute(command,params);
  return command==='state'?{download_capability:true,downloads_armed:true,...result}:result;
}}});

test('interception is acknowledged before input and a late picker event is a checkpoint',async()=>{
  let armed=false,open=false;const commands=[];
  const result=await executeBrowserAutomation({command:'act',params:{op:'click',document:'d1',target:{ref:'e1'}},action:{id:'late-picker'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
      commands.push(command+(params?.action?':'+params.action:''));
      if(command==='state')return{loading:false,automation:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:armed,chooser_open:open};
      if(command==='chooser'){if(params.action==='arm'){armed=true;return{ok:true,armed:true};}return{ok:true,choosers:[{id:'pending-picker'}]};}
      if(command==='input'){assert(armed);return{accepted:true,trusted_input:true};}
      if(params.script.includes('.observe(')){open=true;return{ok:true,value:{ok:true,document:'d1',ready:'complete'}};}
      return{ok:true,value:{ok:true,element:{ref:'e1'},point:{x:10,y:10},geometry:[10,10,20,20]}};
    }}});
  assert.equal(result.result.outcome,'file_chooser_open');assert.equal(result.result.completed,false);assert.equal(result.result.dispatched,true);
  assert.equal(result.result.choosers[0].id,'pending-picker');assert(commands.indexOf('chooser:arm')<commands.indexOf('input'));
  assert.equal(commands.filter(x=>x==='input').length,1);
});
test('pending pickers stop another action but remain observable with a fresh document',async()=>{
  let input=0;
  const base={params:{},action:{id:'pending-picker'},api:{async call(){return{state:'pending'};}},view:{async execute(command){
    if(command==='state')return{loading:false,automation:true,chooser_open:true};
    if(command==='chooser')return{ok:true,choosers:[{id:'first-picker'}]};
    if(command==='automation')return{ok:true,value:{ok:true,document:'fresh-doc',ready:'complete'}};
    input++;throw Error('A pending picker allowed another input');
  }}};
  const checkpoint=await executeBrowserAutomation({...base,command:'act'});assert.equal(checkpoint.result.outcome,'file_chooser_open');assert.equal(checkpoint.result.dispatched,false);
  const observed=await executeBrowserAutomation({...base,command:'observe'});assert.equal(observed.result.document,'fresh-doc');assert.equal(observed.result.choosers[0].id,'first-picker');assert.equal(input,0);
});
test('picker listing is read-only during takeover while Agent cancellation is blocked',async()=>{
  let mutations=0;const base={action:{id:'manual-picker'},api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
    if(command==='state')return{manual:true,chooser_capability:true};if(params.action!=='list')mutations++;return{ok:true,choosers:[]};
  }}};
  assert.deepEqual((await executeBrowserAutomation({...base,command:'chooser',params:{action:'list'}})).result.choosers,[]);
  assert.equal((await executeBrowserAutomation({...base,command:'chooser',params:{action:'cancel',chooser:'pending'}})).result.error.code,'manual_control');assert.equal(mutations,0);
});
test('unsupported interception refuses Agent input before page preparation',async()=>{
  let evaluations=0;const result=await executeBrowserAutomation({command:'act',params:{op:'click'},action:{id:'unsafe-host'},api:{async call(){return{state:'pending'};}},view:{async execute(command){
    if(command==='state')return{loading:false,automation:true,navigation:'1',chooser_capability:false};evaluations++;throw Error('Unsafe host received input');
  }}});assert.equal(result.result.error.code,'unsupported_capability');assert.equal(evaluations,0);
});
test('a modal checkpoint is resolved before cancelling a still-pending picker',async()=>{
  let cancelled=0;const result=await executeBrowserAutomation({command:'chooser',params:{action:'cancel',chooser:'picker'},action:{id:'modal-picker'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command){
      if(command==='state')return{chooser_capability:true,dialog_open:true};
      if(command==='dialog')return{ok:true,dialogs:[{id:'modal'}]};cancelled++;throw Error('Picker cancellation reached a modal page');
    }}});
  assert.equal(result.result.outcome,'dialog_open');assert.equal(result.result.completed,false);assert.equal(cancelled,0);
});

async function observe(state, receipt = "pending", timeout_ms = 50) {
  let evaluations = 0;
  const result = await executeBrowserAutomation({
    command: "observe", params: { timeout_ms }, action: { id: "fixture" },
    api: { async call() { return { state: receipt }; } },
    view: { async execute(command) {
      if (command === "state") return state;
      evaluations++;
      return { ok: true, value: { ok: true, ready: "complete", text: "fixture" } };
    } }
  });
  return { ...result, evaluations };
}

test('fill rechecks focus after selection before sending replacement text',async()=>{
  let focused=true;const inputs=[];
  const result=await executeBrowserAutomation({command:'act',params:{op:'fill',document:'d1',target:{name:'Fixture'},text:'synthetic'},action:{id:'selection-focus'},api:{async call(){return{state:'pending'};}},
    view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='input'){inputs.push(params);if(params.kind==='key')focused=false;return{accepted:true,trusted_input:true};}
      if(params.script.includes('.verify('))return{ok:true,value:{ok:true,focused,verified:true}};
      return{ok:true,value:{ok:true,element:{ref:'e1'},point:{x:10,y:10},geometry:[10,10,20,20]}};
    }}});
  assert.equal(result.result.error.code,'focus_changed');assert.equal(result.result.error.effect_may_have_occurred,true);
  assert.deepEqual(inputs.map(row=>row.kind),['click','key']);
});

test('frame driver pins reach every native mouse/key/text step',async()=>{
  const pins=[{frame:'f1',document:'fd1'},{frame:'f0',document:'fd0'}],inputs=[];
  const pageDriver={async read(method,options,control){await control.checkpoint();
    if(method==='prepare')return{ok:true,element:{ref:'qualified'},point:{x:15,y:25},geometry:[15,25,10,20],frame_chain:pins};
    if(method==='verify')return{ok:true,focused:true,verified:true};return{ok:true,document:'doc',ready:'complete',text:'Fixture'};
  }};
  const result=await executeBrowserAutomation({command:'act',params:{op:'fill',document:'doc',target:{name:'Fixture'},text:'synthetic'},pageDriver,action:{id:'frame-pin-forwarding'},api:{async call(){return{state:'pending'};}},
    view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='input'){inputs.push(params);return{accepted:true,trusted_input:true};}throw Error('Unexpected legacy page evaluation');
    }}});
  assert(result.result.ok);assert.deepEqual(inputs.map(row=>row.kind),['click','key','text']);assert(inputs.every(row=>JSON.stringify(row.frame_chain)===JSON.stringify(pins)));
});

test("observe waits for readiness and reports bounded timeout", async () => {
  const result = await observe({ loading: true, automation: true });
  assert.equal(result.result.error.code, "timed_out");
  assert.equal(result.evaluations, 0);
  assert.equal((await observe({ loading: false, automation: true })).result.text, "fixture");
  const initial=await observe({loading:false,automation:true,initial_navigation_pending:true});
  assert.equal(initial.result.error.code,"timed_out");assert.equal(initial.evaluations,0);
});

test("manual takeover, cancellation and stopped renderer prevent page evaluation", async () => {
  for (const [state, receipt, code] of [
    [{ loading: false, manual: true }, "pending", "manual_control"],
    [{ loading: false }, "cancelled", "cancelled"],
    [{ loading: false, process_failed: true }, "pending", "browser_unavailable"]
  ]) {
    const result = await observe(state, receipt);
    assert.equal(result.result.error.code, code);
    assert.equal(result.evaluations, 0);
  }
});

function actionFixture() {
  let cancelled = false, moving = false, focus = true, samples = 0;
  const inputs = [];
  const view = { async execute(command, params) {
    if (command === "state") return { loading: false, automation: true, chooser_capability:true,chooser_upload_capability:true,chooser_armed:true, navigation: "1" };
    if (command === "input") { inputs.push(params); cancelled = true; return { accepted: true, trusted_input: true }; }
    if (params.script.includes(".prepare(")) {
      samples++;
      return { ok: true, value: { ok: true, element: { ref: "e1" }, point: { x: 10, y: 10 },
        geometry: [moving ? samples * 10 : 10, 10, 20, 20, 10, 10] } };
    }
    return { ok: true, value: { ok: true, focused: focus, verified: true } };
  } };
  return { view, inputs, setMoving() { moving = true; }, setBlurred() { focus = false; },
    execute: () => executeBrowserAutomation({ command: "act", params: { op: "fill", document: "d1", target: { ref: "e1" }, text: "fixture", timeout_ms: 250 },
      view, action: { id: "action-fixture" }, api: { async call() { return { state: cancelled ? "cancelled" : "pending" }; } } }) };
}

test("cancellation after click prevents subsequent keyboard input", async () => {
  const fixture = actionFixture();
  const result = await fixture.execute();
  assert.equal(result.result.error.code, "cancelled");
  assert.equal(result.result.error.effect_may_have_occurred, true);
  assert.deepEqual(fixture.inputs.map(input => input.kind), ["click"]);
});

test("moving targets time out without input and concurrent writers are refused", async () => {
  const fixture = actionFixture(); fixture.setMoving();
  const first = fixture.execute();
  const second = await fixture.execute();
  assert.equal(second.result.error.code, "browser_busy");
  assert.equal((await first).result.error.code, "timed_out");
  assert.equal(fixture.inputs.length, 0);
});

test("changing the displaying device rejects work before the native bridge", async () => {
  const result = await executeBrowserAutomation({ command: "act", params: {}, action: { id: "old-device" },
    view: { execute() { throw Error("Native bridge must not be reached"); } },
    api: { call() { throw Error("Wrong device must not be contacted"); } }, alive: () => false });
  assert.equal(result.result.error.code, "context_changed");
  assert.equal(result.result.error.effect_may_have_occurred, false);
});

test("a pending dialog is a normal checkpoint and manual control protects responses", async () => {
  let responses = 0;
  const view = { async execute(command) {
    if (command === "state") return { loading: false, automation: true, dialog_capability: true, dialog_open: true, manual: false };
    if (command === "automation") throw Error("A modal page must not be evaluated");
    responses++; return { ok: true, dialogs: [{ id: "pending-dialog", kind: "confirm" }] };
  }};
  const base = { params: {}, view, action: { id: "dialog-boundary" }, api: { async call() { return { state: "pending" }; } } };
  const checkpoint = await executeBrowserAutomation({ ...base, command: "observe" });
  assert.equal(checkpoint.result.outcome, "dialog_open");
  assert.equal(checkpoint.result.completed, false);
  view.execute = async command => {
    if (command === "state") return { manual: true, dialog_capability: true };
    responses++; return { ok: true, dialogs: [{ id: "pending-dialog" }] };
  };
  assert.equal((await executeBrowserAutomation({ ...base, command: "dialog", params: { action: "list" } })).result.dialogs.length, 1);
  const before = responses;
  const blocked = await executeBrowserAutomation({ ...base, command: "dialog", params: { action: "respond" } });
  assert.equal(blocked.result.error.code, "manual_control");
  assert.equal(responses, before);
});

test("a dialog opening during isolated reading yields without retrying the script", async () => {
  let evaluations = 0;
  const result = await executeBrowserAutomation({ command: "observe", params: {}, action: { id: "read-dialog" },
    api: { async call() { return { state: "pending" }; } }, view: { async execute(command) {
      if (command === "state") return { loading: false, automation: true };
      if (command === "automation") { evaluations++; return { ok: true, value: { ok: true, blocked_by_dialog: true } }; }
      return { ok: true, dialogs: [{ id: "read-race-dialog" }] };
    } } });
  assert.equal(result.result.outcome, "dialog_open");
  assert.equal(evaluations, 1);
});

test("new-window boundaries stop page reading without retry or an ordinary tool error", async () => {
  const pending = await observe({ loading: false, automation: true, popup_open: true });
  assert.equal(pending.result.outcome, "popup_open");
  assert.equal(pending.result.completed, false); assert.equal(pending.evaluations, 0);
  let evaluations = 0;
  const raced = await executeBrowserAutomation({ command: "observe", params: {}, action: { id: "read-popup" },
    api: { async call() { return { state: "pending" }; } }, view: { async execute(command) {
      if (command === "state") return { loading: false, automation: true };
      evaluations++; return { ok: true, value: { ok: true, blocked_by_popup: true } };
    } } });
  assert.equal(raced.result.outcome, "popup_open"); assert.equal(evaluations, 1);
});

test("unsupported screenshots do not evaluate a page or return an image",async()=>{
  let evaluations=0;
  const result=await executeBrowserAutomation({command:"capture",params:{},action:{id:"unsupported-capture"},
    api:{async call(){return{state:"pending"};}},view:{async execute(command){
      if(command==="state")return{loading:false,automation:true,capture_capability:false};
      evaluations++;throw Error("Unsupported capture must not touch page content");
    }}});
  assert.equal(result.result.error.code,"unsupported_capability");assert.equal(evaluations,0);assert.equal(result.result.image,undefined);
});

test("unsupported mouse gestures fail before scrolling, input or page evaluation",async()=>{
  for(const op of ["double_click","right_click","middle_click"]){
    let evaluated=0;
    const result=await executeBrowserAutomation({command:"act",params:{op,document:"d1",target:{ref:"e1"}},action:{id:`unsupported-${op}`},
      api:{async call(){return{state:"pending"};}},view:{async execute(command){
        if(command==="state")return{loading:false,automation:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1"};
        evaluated++;throw Error("Unsupported mouse input reached the page");
      }}});
    assert.equal(result.result.error.code,"unsupported_capability");assert.equal(evaluated,0);
  }
});

test("mouse gestures deliver one typed sequence to the pinned fresh element",async()=>{
  for(const [op,button,click_count] of [["double_click","left",2],["right_click","right",1],["middle_click","middle",1]]){
    const inputs=[];
    const view={async execute(command,params){
      if(command==="state")return{loading:false,automation:true,advanced_mouse:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1"};
      if(command==="input"){inputs.push(params);return{accepted:true,trusted_input:true};}
      if(params.script.includes(".prepare("))return{ok:true,value:{ok:true,element:{ref:"fresh"},point:{x:20,y:30},geometry:[0,0,40,60,20,30]}};
      return{ok:true,value:{ok:true,text:"after gesture"}};
    }};
    const result=await executeBrowserAutomation({command:"act",params:{op,document:"d1",target:{ref:"fresh"}},view,action:{id:op},
      api:{async call(){return{state:"pending"};}}});
    assert.equal(result.result.verification,"native_delivery");assert.equal(inputs.length,1);
    assert.equal(inputs[0].button,button);assert.equal(inputs[0].click_count,click_count);
    assert.equal(inputs[0].navigation,"1");assert.equal(inputs[0].agent,true);
  }
});

test("selection uses one controlled transaction with fresh document, guard and pinned target",async()=>{
  let commits=0,inputs=0;
  const view={async execute(command,params){
    if(command==="state")return{loading:false,automation:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1"};
    if(command==="input"){inputs++;throw Error("Select must not click through intermediate choices");}
    if(params.script.includes(".prepare("))return{ok:true,value:{ok:true,element:{ref:"select-ref"},point:{x:20,y:30},geometry:[0,0,40,60,20,30]}};
    if(params.script.includes(".selectOptions(")){
      commits++;assert.ok(params.script.includes('"target":{"ref":"select-ref"}'));
      assert.ok(params.script.includes('"choices":[{"label":"Express"}]'));
      return{ok:true,value:{ok:true,changed:true,dispatched:true,verified:true,input_method:"dom_select",events_trusted:false}};
    }
    return{ok:true,value:{ok:true,text:"after selection"}};
  }};
  const result=await executeBrowserAutomation({command:"act",params:{op:"select",document:"d1",target:{name:"Delivery"},choices:[{label:"Express"}]},
    view,action:{id:"select-transaction"},api:{async call(){return{state:"pending"};}}});
  assert.equal(commits,1);assert.equal(inputs,0);assert.equal(result.result.verification,"field_state");
  assert.equal(result.result.events_trusted,false);assert.equal(result.result.input_method,"dom_select");
});

test("options observation is read-only and bypasses ordinary page text collection",async()=>{
  let calls=0;
  const result=await executeBrowserAutomation({command:"observe",params:{mode:"options",document:"d1",target:{ref:"select-ref"},offset:2},
    action:{id:"options-list"},api:{async call(){return{state:"pending"};}},view:{async execute(command,params){
      if(command==="state")return{loading:false,automation:true};
      assert.equal(command,"automation");assert.ok(params.script.includes(".optionList("));
      assert.ok(params.script.includes('"offset":2'));calls++;return{ok:true,value:{ok:true,options:[{index:2,label:"Express",disabled:false}]}};
    }}});
  assert.equal(result.result.options[0].label,"Express");assert.equal(calls,1);
});

test("cancellation or user takeover before selection prevents its transaction",async()=>{
  for(const boundary of ["cancelled","manual_control"]){
    let prepared=0,committed=0;
    const result=await executeBrowserAutomation({command:"act",params:{op:"select",document:"d1",target:{ref:"select"},choices:[{index:0}]},
      action:{id:boundary},api:{async call(){return{state:boundary==="cancelled"&&prepared>=3?"cancelled":"pending"};}},view:{async execute(command,params){
        if(command==="state")return{loading:false,automation:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1",manual:boundary==="manual_control"&&prepared>=3};
        if(params.script.includes(".prepare(")){prepared++;return{ok:true,value:{ok:true,element:{ref:"select"},point:{x:20,y:30},geometry:[0,0,40,60,20,30]}};}
        committed++;throw Error("A stopped selection reached the transaction");
      }}});
    assert.equal(result.result.error.code,boundary);assert.equal(result.result.error.effect_may_have_occurred,false);assert.equal(committed,0);
  }
});

test("screenshot cancellation stops the native request and releases its UI writer",async()=>{
  let started=false,stop;
  const commands=[];
  const view={async execute(command,params){commands.push(command);
    if(command==="state")return{loading:false,automation:true,capture_capability:true,navigation:"1"};
    if(command==="automation")return{ok:true,value:{ok:true,document:"d1",region:{x:0,y:0,width:100,height:100},redactions:[],device_pixel_ratio:1}};
    if(command==="capture"){started=true;return new Promise((resolve,reject)=>{stop=reject;});}
    if(command==="capture_cancel"){assert.equal(params.capture_key,"cancel-native-capture");stop(Error("Cancelled"));return{};}
    throw Error("Unexpected screenshot command");
  }};
  const result=await executeBrowserAutomation({command:"capture",params:{timeout_ms:1000},view,browser:"capture-browser",action:{id:"cancel-native-capture"},
    api:{async call(){return{state:started?"cancelled":"pending"};}}});
  assert.equal(result.result.error.code,"cancelled");assert.ok(commands.includes("capture_cancel"));assert.equal(result.result.image,undefined);
  view.execute=async()=>({loading:false,automation:true,capture_capability:false});
  const next=await executeBrowserAutomation({command:"capture",params:{},view,browser:"capture-browser",action:{id:"next-capture"},api:{async call(){return{state:"pending"};}}});
  assert.equal(next.result.error.code,"unsupported_capability");
});

test('changed capture scope reports only structural causes and withholds the image',async()=>{
  let reads=0;
  const stamp=value=>JSON.stringify([['f0','fd-root',null,false,true,'readable',value,{x:0,y:0},{width:100,height:100},null]]);
  const result=await executeBrowserAutomation({command:'capture',params:{document:'original'},browser:'capture-scope-qa',action:{id:'changed-image'},
    api:{async call(){return{state:'pending'};}},
    pageDriver:{async read(){return{ok:true,document:'original',device_pixel_ratio:1,region:{x:0,y:0,width:100,height:100},redactions:[],frame_stamp:stamp(reads++?'new-private-identity':'old-private-identity')};}},
    view:{async execute(command){if(command==='state')return{loading:false,automation:true,capture_capability:true,navigation:'1'};if(command==='capture')return{data:'private-raw-image'};throw Error('Unexpected capture command');}}});
  assert.equal(result.result.error.code,'capture_changed');assert(result.result.error.message.includes('isolated-document'));
  assert(!JSON.stringify(result).includes('private'));assert.equal(result.result.image,undefined);assert.equal(reads,2);
});
