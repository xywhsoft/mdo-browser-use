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
  assert.equal(calls.length, 0);
});
