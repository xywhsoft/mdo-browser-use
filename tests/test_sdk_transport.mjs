import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { createServer } from "node:http";
import { createBrowserUseClient } from "../sdk/client.mjs";

const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
const outcome = promise => promise.then(value => ({ value }), error => ({ error }));

test("cancelling an in-flight admission settles even when its adapter ignores abort", async () => {
  const started = deferred(), admission = deferred(), calls = [];
  const sdk = { async call(method, params, options) {
    calls.push({ method, params, signal: options?.signal });
    if (params.action === "submit") { started.resolve(); return admission.promise; }
    return { state: "pending" };
  }};
  const controller = new AbortController(), operation = "sdk-" + "a1".repeat(16);
  const pending = outcome(createBrowserUseClient(sdk).act({ browser: "fixture" }, { operation, signal: controller.signal }));
  await started.promise; controller.abort();
  let early;
  try { early = await Promise.race([pending, delay(150).then(() => null)]); }
  finally { admission.resolve({ state: "pending" }); await pending; }
  assert.equal(early?.error?.code, "cancelled", "public cancellation waited for the blocked adapter");
  assert.equal(early.error.operation, operation);
  assert.equal(calls[0].signal.aborted, true, "SDK adapter did not receive its abort signal");
  assert.equal(calls.filter(row => row.params.action === "submit").length, 1);
  assert.equal(calls.filter(row => row.params.action === "cancel").length, 1);
});

test("an in-flight result read obeys the whole-call deadline and retains its receipt", async () => {
  const reading = deferred(), response = deferred(), calls = [];
  const sdk = { async call(method, params, options) {
    calls.push({ method, params, signal: options?.signal });
    if (params.cancel) return { state: "cancelled" };
    reading.resolve(); return response.promise;
  }};
  const pending = outcome(createBrowserUseClient(sdk).wait({ id: "held-result" }, { timeout: 30 }));
  await reading.promise;
  let early;
  try { early = await Promise.race([pending, delay(200).then(() => null)]); }
  finally { response.resolve({ state: "pending" }); await pending; }
  assert.equal(early?.error?.code, "timed_out", "deadline did not include the blocked result read");
  assert.equal(early.error.receipt, "held-result");
  assert.equal(calls[0].signal.aborted, true);
  assert.equal(calls.filter(row => row.params.cancel).length, 1);
  assert.equal(calls.at(-1).signal.aborted, false, "cleanup reused the expired signal");
});

test("blocked release cannot hide a known browser success or its original failure", async () => {
  for (const state of ["succeeded", "failed"]) {
    const releasing = deferred(), held = deferred(), calls = [];
    const sdk = { async call(method, params) {
      calls.push(params);
      if (params.release) { releasing.resolve(); return held.promise; }
      return { state, result: { ok: true }, error: { code: "manual_control", message: "User takeover" } };
    }};
    const pending = outcome(createBrowserUseClient(sdk).wait({ id: "cleanup-" + state }, { timeout: 50 }));
    await releasing.promise;
    let early;
    try { early = await Promise.race([pending, delay(1400).then(() => null)]); }
    finally { held.resolve({}); await pending; }
    assert.notEqual(early, null, "receipt cleanup blocked the observed outcome");
    if (state === "succeeded") assert.equal(early.value.ok, true);
    else assert.equal(early.error.code, "manual_control");
    assert.ok(!calls.some(row => row.cancel), "a known terminal result was cancelled after cleanup expired");
  }
});

test("a blocked cancellation acknowledgement is bounded independently of its caller", async () => {
  const reading = deferred(), read = deferred(), cancellation = deferred(); let attempts = 0;
  const client = createBrowserUseClient({ async call(method, params) {
    if (params.cancel) { attempts++; return cancellation.promise; }
    reading.resolve(); return read.promise;
  }});
  const controller = new AbortController();
  const pending = outcome(client.wait({ id: "held-cancellation" }, { signal: controller.signal }));
  await reading.promise; controller.abort();
  let early;
  try { early = await Promise.race([pending, delay(1400).then(() => null)]); }
  finally { read.resolve({ state: "pending" }); cancellation.resolve({}); await pending; }
  assert.equal(early?.error?.code, "cancelled"); assert.equal(early.error.receipt, "held-cancellation");
  assert.equal(attempts, 1);
});

test("the deadline includes admission time and invalid durations never dispatch", async () => {
  const calls = [], result = deferred();
  const client = createBrowserUseClient({ async call(method, params) {
    calls.push({ method, params });
    if (params.cancel) return {};
    if (method === "browseruse.observe") { await delay(40); return { id: "slow-admission" }; }
    return result.promise;
  }});
  const started = performance.now();
  const pending = outcome(client.observe({ browser: "fixture" }, { timeout: 70 }));
  let early;
  try { early = await Promise.race([pending, delay(300).then(() => null)]); }
  finally { result.resolve({ state: "pending" }); await pending; }
  assert.equal(early?.error?.code, "timed_out"); assert.equal(early.error.receipt, "slow-admission");
  assert.ok(performance.now() - started < 250);
  calls.length = 0;
  for (const timeout of [-1, Infinity, NaN, "100", 2147483648])
    await assert.rejects(client.act({ browser: "fixture" }, { timeout }), TypeError);
  const controller = new AbortController(); controller.abort();
  await assert.rejects(client.tabs({ action: "list" }, { signal: controller.signal }), { code: "cancelled" });
  assert.equal(calls.length, 0);
});

test("a lost actual C admission reply recovers the original browser without resubmission", async () => {
  const env = JSON.parse(await readFile(new URL("../.mdo/environment.json", import.meta.url), "utf8"));
  const origin = `http://127.0.0.1:${env.port}`;
  const bootstrap = await fetch(origin + "/api/v1/bootstrap");
  const token = bootstrap.headers.get("X-Mdo-Write-Token"); await bootstrap.arrayBuffer();
  assert.equal(bootstrap.status, 200); assert.ok(token);
  const scope = { extension: "browser-use-transport-qa", project: "default", session: "transport-task" };
  const direct = async (method, params, signal) => {
    const response = await fetch(origin + "/api/v1/application/call", { method: "POST", signal,
      headers: { "Content-Type": "application/json", "X-Mdo-Write-Token": token },
      body: JSON.stringify({ method, params, context: scope }) });
    const envelope = await response.json();
    if (!response.ok || !envelope.ok) throw Object.assign(new Error(envelope.error?.message), { code: envelope.error?.code });
    return envelope.data;
  };
  const display = "browser-use-transport-qa-window";
  await direct("client.hello", { client: display, context: {} });
  const before = (await direct("browseruse.tabs", { action: "list" })).tabs;
  const admitted = deferred(), heldReply = deferred(), calls = [], operation = "sdk-" + crypto.randomUUID().replaceAll("-", "");
  let browser, error, result, submitted = false;
  const server = createServer(async (request, response) => {
    try {
      const chunks = []; for await (const part of request) chunks.push(part);
      const { method, params } = JSON.parse(Buffer.concat(chunks)); calls.push({ method, params });
      const value = await direct(method, params);
      if (params.action === "submit") {
        submitted = true;
        // Admission can return while the C operation is still queued. This
        // case exercises a lost reply AFTER the original effect completes;
        // aborting a queued operation is correctly allowed to cancel it.
        let current=value;const deadline=performance.now()+5000;
        while(current.state==='pending'&&performance.now()<deadline){
          current=await direct('browseruse.operation',{action:'get',operation});
          if(current.state==='pending')await delay(10);
        }
        admitted.resolve(current);await heldReply.promise;
      }
      if (!response.destroyed) { response.writeHead(200, { "Content-Type": "application/json" }); response.end(JSON.stringify(value)); }
    } catch { admitted.resolve({state:'failed'});if (!response.destroyed) { response.writeHead(500); response.end("{}"); } }
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const url = `http://127.0.0.1:${server.address().port}/application`;
  const sdk = { async call(method, params, options) {
    const response = await fetch(url, { method: "POST", signal: options?.signal,
      headers: { "Content-Type": "application/json" }, body: JSON.stringify({ method, params }) });
    assert.equal(response.status, 200); return response.json();
  }};
  const controller = new AbortController(), client = createBrowserUseClient(sdk);
  try {
    const pending = outcome(client.tabs({ action: "create", client: display, key: "held-admission" }, { operation, signal: controller.signal }));
    assert.equal((await admitted.promise).state,'succeeded','Original C effect must complete before its admission reply is lost');controller.abort();
    let early;
    try { early = await Promise.race([pending, delay(200).then(() => null)]); }
    finally { heldReply.resolve(); error = (await pending).error; }
    assert.equal(early?.error?.code, "cancelled", "real HTTP admission did not honour cancellation");
    assert.equal(error.operation, operation);
    result = await client.recover(operation);
    browser = result.id;
    assert.equal(typeof browser, "string"); assert.equal(result.operation, operation);
    const after = (await direct("browseruse.tabs", { action: "list" })).tabs;
    assert.equal(after.length, before.length + 1);
    assert.equal(after.filter(row => row.id === browser).length, 1);
    assert.equal(calls.filter(row => row.params.action === "submit").length, 1);
    assert.ok(!calls.some(row => row.params.operation && row.params.operation !== operation));
  } finally {
    heldReply.resolve();
    // Inspect only the original owned receipt after a failed test, never submit again.
    if (submitted && !browser) {
      for (let attempt = 0; attempt < 50; attempt++) {
        result = await direct("browseruse.operation", { action: "get", operation });
        if (result.state !== "pending") { browser = result.result?.id; break; }
        await delay(20);
      }
    }
    if (submitted) await direct("browseruse.operation", { action: "release", operation });
    if (browser) await direct("browseruse.tabs", { action: "close", browser });
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
  }
});
