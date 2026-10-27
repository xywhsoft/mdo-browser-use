import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { createBrowserUseClient } from "../sdk/client.mjs";
import { services } from "../sdk/contracts.mjs";

test("JavaScript client reaches the same actual C service contract", async () => {
  const env = JSON.parse(await readFile(new URL("../.mdo/environment.json", import.meta.url), "utf8"));
  const origin = `http://127.0.0.1:${env.port}`;
  const bootstrap = await fetch(`${origin}/api/v1/bootstrap`);
  assert.equal(bootstrap.status, 200);
  const token = bootstrap.headers.get("x-mdo-write-token");
  await bootstrap.arrayBuffer();
  const sdk = { async call(method, params) {
    const response = await fetch(`${origin}/api/v1/application/call`, {
      method: "POST", headers: { "Content-Type": "application/json", "X-Mdo-Write-Token": token },
      body: JSON.stringify({ method, params, context: { extension: "browser-use-js-check" } })
    });
    const result = await response.json();
    assert.equal(response.status, 200, result.error?.message);
    return result.data;
  }};
  const client = createBrowserUseClient(sdk);
  assert.deepEqual(client.services, services.map(s => s.id));
  const result = await client.tabs({ action: "list" });
  assert.ok(Array.isArray(result.tabs));
  const description = await sdk.call("sdk.describe", { id: "browseruse.tabs" });
  assert.deepEqual(description.input_schema, services[0].input);
});

test("result cleanup cannot hide the original browser failure", async () => {
  const client = createBrowserUseClient({ async call(method, params) {
    if (method === "browseruse.observe") return { id: "test-receipt", state: "pending" };
    if (params.release) throw Error("Release connection failed");
    return { state: "failed", error: { code: "manual_control", message: "User takeover" } };
  }});
  await assert.rejects(client.observe({ browser: "test" }), { code: "manual_control", message: "User takeover" });
});

test("actual mdo UI journal does not persist even a small browser observation", async () => {
  const source = await readFile(new URL("../.mdo/source/app/web/js/api/application.js", import.meta.url), "utf8");
  const entry = source.slice(source.indexOf("export async function applyApplicationAction"));
  const { applyApplicationAction } = await import(`data:text/javascript;base64,${Buffer.from(entry).toString("base64")}`);
  const records = new Map();
  const storage = { getItem: key => records.get(key) ?? null, setItem: (key, value) => records.set(key, value) };
  let executions = 0;
  const action = { id: "private-page-read", action: "workbench.browser" };
  const ack = await applyApplicationAction(action, async () => {
    executions++;
    return { ok: true, text: "Private fixture page text" };
  }, storage, "journal");
  assert.equal(ack.result.text, "Private fixture page text");
  assert.ok(!storage.getItem("journal").includes("Private fixture page text"));
  const duplicate = await applyApplicationAction(action, () => { throw Error("Repeated an effect"); }, storage, "journal");
  assert.equal(duplicate.result.ephemeral, true);
  assert.equal(executions, 1);
  const screenshot = { id: "private-page-image", action: "workbench.browser" };
  const image = await applyApplicationAction(screenshot, async () => ({ok:true,image:{base64:"Private fixture screenshot bytes"}}), storage, "journal");
  assert.equal(image.result.image.base64,"Private fixture screenshot bytes");
  assert.ok(!storage.getItem("journal").includes("Private fixture screenshot bytes"));
  assert.equal((await applyApplicationAction(screenshot,()=>{throw Error("Repeated screenshot read");},storage,"journal")).result.ephemeral,true);
});

test("reader cancellation reaches the owned host receipt", async () => {
  const calls = [];
  const client = createBrowserUseClient({ async call(method, params) {
    calls.push({ method, params });
    return { state: "pending" };
  }});
  await assert.rejects(client.wait({ id: "cancellation-fixture" }, { timeout: 0 }), { code: "timed_out", receipt: "cancellation-fixture" });
  assert.deepEqual(calls[0], { method: "browseruse.result", params: { id: "cancellation-fixture", cancel: true } });
  const controller = new AbortController(); controller.abort(); calls.length = 0;
  await assert.rejects(client.observe({ browser: "unused" }, { signal: controller.signal }), { code: "cancelled" });
  await assert.rejects(client.screenshot({ browser: "unused" }, { signal: controller.signal }), { code: "cancelled" });
  assert.equal(calls.length, 0);
});

test("lost admission reply retains the identity and recovery never resubmits it", async () => {
  const calls = [];
  const operation = "sdk-00000000000000000000000000000001";
  const client = createBrowserUseClient({ async call(method, params) {
    calls.push({ method, params });
    if (params.action === "submit") throw Error("Admission reply lost");
    return { operation, state: "succeeded", result: { ok: true } };
  }});
  await assert.rejects(client.act({ browser: "fixture" }, { operation }), { operation, message: "Admission reply lost" });
  assert.equal((await client.recover(operation)).ok, true);
  assert.deepEqual(calls.map(row => row.params.action), ["submit", "get", "release"]);
});

test("expired operation recovery never creates a replacement action", async () => {
  const actions = [];
  const operation = "sdk-00000000000000000000000000000002";
  const client = createBrowserUseClient({ async call(method, params) {
    actions.push(params.action);
    return { operation, state: "uncertain", error: { code: "result_expired", message: "Inspect the tab" } };
  }});
  await assert.rejects(client.recover(operation), { operation, code: "result_expired" });
  assert.deepEqual(actions, ["get", "release"]);
});

test("save uses a durable write identity and a consistent one-minute default",async()=>{
  const calls=[];const client=createBrowserUseClient({async call(method,params){
    calls.push({method,params});return{state:"succeeded",result:{ok:true,saved:true}};
  }});
  const input={browser:"tab",download:"response",path:"new.bin",timeout_ms:undefined};
  assert.equal((await client.save(input)).saved,true);
  assert.equal(calls[0].params.action,"submit");assert.equal(calls[0].params.method,"browseruse.save");
  assert.equal(calls[0].params.arguments.timeout_ms,60000);assert.equal(input.timeout_ms,undefined);
  assert.deepEqual(calls.map(row=>row.params.action),["submit","get","release"]);
});
