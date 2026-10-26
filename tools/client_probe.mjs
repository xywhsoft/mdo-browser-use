// Invoked while sdk_probe.py has the one normal mdo/WebView2 UI running.
import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { createBrowserUseClient } from "../sdk/client.mjs";

const output = new URL("../.mdo/client-native-result.json", import.meta.url);
await writeFile(output, '{"passed":false,"state":"running"}\n');
const env = JSON.parse(await readFile(new URL("../.mdo/environment.json", import.meta.url), "utf8"));
const origin = `http://127.0.0.1:${env.port}`;
const response = await fetch(`${origin}/api/v1/bootstrap`);
const token = response.headers.get("x-mdo-write-token"); await response.arrayBuffer();
const sdk = { async call(method, params) {
  const response = await fetch(`${origin}/api/v1/application/call`, {
    method: "POST", signal: AbortSignal.timeout(20000),
    headers: { "Content-Type": "application/json", "X-Mdo-Write-Token": token },
    body: JSON.stringify({ method, params, context: { extension: "browser-use-js-native-qa", session: "native-client" } })
  });
  const reply = await response.json();
  if (!response.ok) throw Object.assign(new Error(reply.error?.message || "SDK call failed"), { code: reply.error?.code });
  return reply.data;
}};
const client = createBrowserUseClient(sdk);
let browser;
try {
  const { clients } = await client.tabs({ action: "clients" }); assert.equal(clients.length, 1);
  browser = await client.tabs({ action: "create", client: clients[0].id,
    url: `${origin}/__browser_use_page__.html`, key: "js-native-client" });
  const page = await client.observe({ browser: browser.id });
  assert.ok(page.text.includes("Readable fixture text"));
  const choices=await client.observe({browser:browser.id,mode:"options",document:page.document,target:{label:"Delivery method"}});
  assert.equal(choices.options[3].label,"Express");assert.equal(choices.values_exposed,false);
  const selectionOperation=`sdk-${crypto.randomUUID().replaceAll("-","")}`;
  const selectParams={browser:browser.id,document:page.document,op:"select",target:{ref:choices.ref},choices:[{index:3}]};
  const selected=await client.act(selectParams,{operation:selectionOperation,retain:true});
  assert.equal(selected.input_method,"dom_select");assert.equal(selected.events_trusted,false);
  assert.ok(selected.observation.text.includes("Selection events 1/1"));
  assert.deepEqual(await client.act(selectParams,{operation:selectionOperation,retain:true}),selected);
  assert.ok((await client.observe({browser:browser.id})).text.includes("Selection events 1/1"));
  await client.operation({action:"release",operation:selectionOperation});
  const screenshotModes=[];
  for(const parameters of [{mode:"viewport",format:"png"},{mode:"element",format:"jpeg",target:{name:"查询"}},
    {mode:"full_page",format:"png",max_height:640}]){
    const capture=await client.screenshot({browser:browser.id,document:page.document,max_width:800,max_height:600,...parameters});
    assert.equal(capture.image.media_type,`image/${parameters.format}`);
    assert.ok(capture.image.width<=800&&capture.image.height<=(parameters.max_height||600));
    assert.equal(Buffer.from(capture.image.base64,"base64").length,capture.image.bytes);
    assert.equal(capture.document,page.document);screenshotModes.push(capture.mode);
  }
  const operation = `sdk-${crypto.randomUUID().replaceAll("-", "")}`;
  const params = { browser: browser.id, document: page.document, op: "click", target: { name: "查询" } };
  const first = await client.act(params, { operation, retain: true });
  assert.ok(first.observation.text.includes("Trusted click 1"));
  const repeated = await client.act(params, { operation, retain: true });
  assert.equal(repeated.action, first.action);
  assert.ok((await client.observe({ browser: browser.id })).text.includes("Trusted click 1"));
  await client.operation({ action: "release", operation });
  await assert.rejects(client.act(params, { operation }), { code: "result_expired", operation });
  assert.ok((await client.observe({ browser: browser.id })).text.includes("Trusted click 1"));
  const navigated = await client.navigate({ browser: browser.id, action: "goto", url: `${origin}/__browser_use_page__.html?js=1` });
  assert.ok(navigated.observation.url.includes("?js=1"));
  const checkpoint = await client.act({ browser: browser.id, document: navigated.observation.document,
    op: "click", target: { name: "Prompt dialog" } });
  assert.equal(checkpoint.outcome, "dialog_open"); assert.equal(checkpoint.completed, false);
  const { dialogs } = await client.dialog({ browser: browser.id, action: "list" });
  assert.equal(dialogs.length, 1); assert.equal(dialogs[0].kind, "prompt");
  assert.ok(!JSON.stringify(dialogs).includes("fixture-private-default"));
  const dialogOperation = `sdk-${crypto.randomUUID().replaceAll("-", "")}`;
  const responseParams = { browser: browser.id, action: "respond", dialog: dialogs[0].id, accept: true, text: "JS 回复 🦉" };
  const answered = await client.dialog(responseParams, { operation: dialogOperation, retain: true });
  assert.equal(answered.ok, true);
  const repeatedResponse = await client.dialog(responseParams, { operation: dialogOperation, retain: true });
  assert.deepEqual(repeatedResponse, answered);
  await client.operation({ action: "release", operation: dialogOperation });
  assert.equal((await client.waitFor({ browser: browser.id, until: "text", text: "prompt:JS 回复 🦉" })).matched, true);
  const childIds = new Set();
  async function openPopup(name, expectedText) {
    const observed = await client.observe({ browser: browser.id });
    const checkpoint = await client.act({ browser: browser.id, document: observed.document, op: "click", target: { name } });
    assert.equal(checkpoint.outcome, "popup_open"); assert.equal(checkpoint.completed, false);
    let child;
    for (let i = 0; i < 100; i++) {
      child = (await client.tabs({ action: "list" })).tabs.find(tab => tab.opener === browser.id && !childIds.has(tab.id));
      if (child) break; await new Promise(resolve => setTimeout(resolve, 50));
    }
    assert.ok(child?.id, "The actual workbench did not admit a child tab");childIds.add(child.id);
    assert.equal(child.owner, browser.owner);assert.deepEqual(child.context, browser.context);
    const page = await client.observe({ browser: child.id });assert.ok(page.text.includes(expectedText));
    let mounted = false;
    for (let i = 0; i < 100; i++) {
      const panels = await sdk.call("panel.list", { client: child.client });
      if (panels.some(panel => panel.type === "mdo.browser" && panel.resource?.id === child.id)) { mounted = true;break; }
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    assert.ok(mounted, "The workbench did not display the popup panel");
    return { child, page };
  }
  const popup = await openPopup("Open popup", "Independent popup");
  assert.ok((await client.observe({ browser: browser.id })).text.includes("Popup connected"));
  const closing = await client.act({ browser: popup.child.id, document: popup.page.document, op: "click", target: { name: "Close popup" } });
  assert.equal(closing.outcome, "closed");
  let closed = false;
  for (let i = 0; i < 100; i++) {
    if (!(await client.tabs({ action: "list" })).tabs.some(tab => tab.id === popup.child.id)) { closed = true;break; }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  assert.ok(closed, "window.close did not remove the scoped tab");
  const blank = await openPopup("Open blank popup", "Written blank popup");
  const blankClick = await client.act({ browser: blank.child.id, document: blank.page.document, op: "click", target: { name: "Blank action" } });
  assert.ok(blankClick.observation.text.includes("Blank action verified"));
  await client.tabs({ action: "close", browser: blank.child.id });
  assert.ok(!(await sdk.call("panel.list", {client:browser.client})).some(panel=>panel.resource?.id===blank.child.id),
    "Broker close left a dangling popup panel");
  const interrupted=await sdk.call("browseruse.wait",{browser:browser.id,until:"text",text:"never-in-this-fixture",timeout_ms:60000});
  await new Promise(resolve=>setTimeout(resolve,1000));
  const closedId=browser.id;
  await client.tabs({action:"close",browser:closedId});browser=null;
  await new Promise(resolve=>setTimeout(resolve,500));
  assert.equal((await client.result({id:interrupted.id})).state,"cancelled");
  assert.ok(!(await sdk.call("panel.list")).some(panel=>panel.resource?.id===closedId),"Browser close left a panel");
  await client.result({id:interrupted.id,release:true});
  browser=await client.tabs({action:"create",client:clients[0].id,url:`${origin}/__browser_use_drag__.html`,key:"js-native-drag"});
  const dragPage=await client.observe({browser:browser.id});
  const pointerDrag=await client.act({browser:browser.id,document:dragPage.document,op:"drag",target:{name:"Pointer handle"},destination:{name:"Drop target"}});
  assert.equal(pointerDrag.drag_kind,"pointer");assert.ok(pointerDrag.observation.text.includes("Pointer release true"));
  const dragOperation=`sdk-${crypto.randomUUID().replaceAll("-","")}`;
  const dragParams={browser:browser.id,document:dragPage.document,op:"drag",target:{name:"HTML handle"},destination:{name:"Drop target"}};
  const dropped=await client.act(dragParams,{operation:dragOperation,retain:true});
  assert.equal(dropped.drag_kind,"html5");assert.ok(dropped.observation.text.includes("Drops 1"));
  assert.deepEqual(await client.act(dragParams,{operation:dragOperation,retain:true}),dropped);
  assert.ok((await client.observe({browser:browser.id})).text.includes("Drops 1"));
  await client.operation({action:"release",operation:dragOperation});
  const report = { passed: true, backend: "actual-js-client-mdo-webview2", duplicate_native_clicks: 0,
    released_identity_not_replayed: true, navigation_completed: true, native_dialog_checkpoint_and_response: true,
    duplicate_dialog_responses: 0, scoped_popup_adoption: true, page_close_cleanup: true, written_blank_popup_action: true,
    closed_panels_removed:true,closed_pending_wait_cancelled:true,exact_selection_once:true,native_drag_once:true,screenshot_modes:screenshotModes };
  await writeFile(output, JSON.stringify(report, null, 2) + "\n");
  process.stdout.write(JSON.stringify(report) + "\n");
} catch (error) {
  await writeFile(output, JSON.stringify({ passed: false, code: error.code || "probe_failed", message: error.message }) + "\n");
  throw error;
} finally {
  if (browser) {
    for (const child of (await client.tabs({ action: "list" })).tabs)
      if (child.opener === browser.id) await client.tabs({ action: "close", browser: child.id });
    await client.tabs({ action: "close", browser: browser.id });
  }
}
