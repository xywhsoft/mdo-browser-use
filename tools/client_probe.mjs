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
const client = createBrowserUseClient({ async call(method, params) {
  const response = await fetch(`${origin}/api/v1/application/call`, {
    method: "POST", signal: AbortSignal.timeout(20000),
    headers: { "Content-Type": "application/json", "X-Mdo-Write-Token": token },
    body: JSON.stringify({ method, params, context: { extension: "browser-use-js-native-qa", session: "native-client" } })
  });
  const reply = await response.json();
  if (!response.ok) throw Object.assign(new Error(reply.error?.message || "SDK call failed"), { code: reply.error?.code });
  return reply.data;
}});
let browser;
try {
  const { clients } = await client.tabs({ action: "clients" }); assert.equal(clients.length, 1);
  browser = await client.tabs({ action: "create", client: clients[0].id,
    url: `${origin}/__browser_use_page__.html`, key: "js-native-client" });
  const page = await client.observe({ browser: browser.id });
  assert.ok(page.text.includes("Readable fixture text"));
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
  const report = { passed: true, backend: "actual-js-client-mdo-webview2", duplicate_native_clicks: 0,
    released_identity_not_replayed: true, navigation_completed: true };
  await writeFile(output, JSON.stringify(report, null, 2) + "\n");
  process.stdout.write(JSON.stringify(report) + "\n");
} catch (error) {
  await writeFile(output, JSON.stringify({ passed: false, code: error.code || "probe_failed", message: error.message }) + "\n");
  throw error;
} finally {
  if (browser) await client.tabs({ action: "close", browser: browser.id });
}
