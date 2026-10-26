import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const source = (await readFile(new URL("../host/automation-dispatch.js", import.meta.url), "utf8"))
  .replace('import { browserRuntime } from "./automation-runtime.js";', 'function browserRuntime() {}')
  .replace('import { prepareScreenshot } from "./capture-image.js";', 'async function prepareScreenshot(){throw Error("An unapproved image path was reached")}');
const { executeBrowserAutomation } = await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);

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

test("observe waits for readiness and reports bounded timeout", async () => {
  const result = await observe({ loading: true, automation: true });
  assert.equal(result.result.error.code, "timed_out");
  assert.equal(result.evaluations, 0);
  assert.equal((await observe({ loading: false, automation: true })).result.text, "fixture");
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
    if (command === "state") return { loading: false, automation: true, navigation: "1" };
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
        if(command==="state")return{loading:false,automation:true,navigation:"1"};
        evaluated++;throw Error("Unsupported mouse input reached the page");
      }}});
    assert.equal(result.result.error.code,"unsupported_capability");assert.equal(evaluated,0);
  }
});

test("mouse gestures deliver one typed sequence to the pinned fresh element",async()=>{
  for(const [op,button,click_count] of [["double_click","left",2],["right_click","right",1],["middle_click","middle",1]]){
    const inputs=[];
    const view={async execute(command,params){
      if(command==="state")return{loading:false,automation:true,advanced_mouse:true,navigation:"1"};
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
