// Headed C/JS/WebView2 workflow over the production remote transport/bridge.
// Synthetic loopback pages and fixed owned task only; never a model request.
import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { createBrowserUseClient } from "../sdk/client.mjs";
import { nativeBridge, remoteSdk } from "./remote_bridge_client.mjs";

const root = fileURLToPath(new URL("../", import.meta.url)).replace(/[\\/]$/, "");
const output = new URL("../.mdo/remote-browser-result.json", import.meta.url);
const fixtureOrigin = process.argv[2]; assert.match(fixtureOrigin, /^http:\/\/127\.0\.0\.1:[0-9]+$/);
const session = createHash("sha256").update("mdo-browser-use/remote-native-browser-qa/v1").digest("hex").slice(0, 32);
const context = { extension: "browser-use-remote-browser-qa", project: "default", session, workspace: root,
  namespace: "remote-native-qa", member: "remote-native-worker" };
const report = { passed: false, state: "running", stage: "startup", cases: [], model_calls: 0,
  account_authentication_verified: false, physical_devices_verified: false, desktop_pixels_verified: false };
const save = () => writeFile(output, JSON.stringify(report, null, 2) + "\n");
const mark = async name => { report.cases.push(name); report.stage = name; await save(); console.log(name); };
const pause = ms => new Promise(done => setTimeout(done, ms));
const operations = new Set(); let native, connection, sdk, client, browser, ticket;
await save();
try {
  native = await nativeBridge(); connection = await native.connect();
  const base = await remoteSdk(connection.transport, context);
  sdk = { async call(method, params = {}, options) {
    if (method === "browseruse.operation" && params.action === "submit") operations.add(params.operation);
    const result = await base.call(method, params, options);
    if (method === "browseruse.operation" && params.action === "release") operations.delete(params.operation);
    return result;
  }};
  client = createBrowserUseClient(sdk);
  const task = await sdk.call("session.create", { body: { project_id: "default", client_session_id: session,
    title: "Browser Use remote native QA", permission_profile: "full-access", workspace_root: root } });
  assert.equal(task.data.permission_profile, "full-access"); assert.equal(task.data.workspace_root, root);
  let clients;
  const until = performance.now() + 30000;
  do { clients = (await client.tabs({ action: "clients" })).clients; if (clients.length) break; await pause(100); } while (performance.now() < until);
  assert.equal(clients.length, 1, "Expected the single actual workbench display client");
  const origin = `http://127.0.0.1:${native.env.port}`;
  const created = await client.tabs({ action: "create", client: clients[0].id, key: "remote-native-browser-qa", url: origin + "/__browser_use_remote__.html" });
  browser = created.id;
  let page = await client.observe({ browser });
  assert.equal(page.ok, true); assert.ok(page.text.includes("Readable fixture text"));
  const view = await sdk.call("browser.get", { id: browser });
  assert.equal(view.context.workspace, root); assert.equal(view.context.session, session);
  assert.equal(view.context.namespace, context.namespace); assert.equal(view.context.member, context.member);
  await mark("remote_c_js_native_observation_and_original_task_binding");

  const filled = await client.act({ browser, document: page.document, op: "fill", target: { css: "#order" }, text: "Relay \u96e8\ud83e\udd89" });
  assert.equal(filled.verification, "field_state");
  const clicked = await client.act({ browser, document: page.document, op: "click", target: { css: "#search" } });
  assert.ok(clicked.observation.text.includes("Trusted click 1"));
  page = await client.observe({ browser }); assert.ok(page.text.includes("Trusted click 1"));
  await mark("remote_trusted_native_fill_and_exactly_one_click");

  const capture = await client.screenshot({ browser, document: page.document, max_width: 800, max_height: 600 });
  const bytes = Buffer.from(capture.image.base64, "base64");
  assert.equal(capture.image.media_type, "image/png"); assert.ok(bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])));
  report.synthetic_capture = { bytes: bytes.length, sha256: createHash("sha256").update(bytes).digest("hex") };
  await mark("remote_native_synthetic_capture");

  const controller = new AbortController();
  const waiting = client.waitFor({ browser, until: "visible", target: { css: "#never-created-remote-qa" }, timeout_ms: 10000 }, { signal: controller.signal });
  // Handle the rejection immediately, including while inspecting original dispatch.
  const waited = waiting.then(value => ({ value }), error => ({ error }));
  let dispatched = false;
  const deadline = performance.now() + 5000;
  do {
    const rows = await sdk.call("client.poll", { client: clients[0].id });
    dispatched = rows.some(row => row.action === "workbench.browser" && row.params?.id === browser && row.params.command === "wait_for");
    if (dispatched) break; await pause(50);
  } while (performance.now() < deadline);
  controller.abort(); const cancelled = await waited;
  assert.ok(dispatched); assert.equal(cancelled.error?.code, "cancelled");
  assert.equal(typeof cancelled.error.receipt, "string");
  const cancelledReceipt = await client.result({ id: cancelled.error.receipt });
  assert.equal(cancelledReceipt.state, "cancelled");
  await client.result({ id: cancelled.error.receipt, release: true });
  assert.ok((await client.observe({ browser })).text.includes("Trusted click 1"));
  await mark("remote_cancel_original_wait_and_continue_without_input_replay");

  report.stage = "remote_navigation_and_stale_input"; await save();
  const original = page.document;
  const navigation = await client.navigate({ browser, action: "goto", url: origin + "/__browser_use_remote_upload__.html" });
  page = navigation.observation;
  assert.ok(page.document !== original);
  await assert.rejects(client.act({ browser, document: original, op: "click", target: { css: "#search" } }), { code: "stale_document" });
  assert.ok((await client.observe({ browser })).text.includes("Browser Use upload fixture"));
  await mark("remote_navigation_retires_stale_input_and_next_observation_works");

  report.stage = "remote_upload_and_full_hash"; await save();
  const source = new URL("../.mdo/qa/remote-bridge/upload.bin", import.meta.url);
  const expected = await readFile(source), hash = createHash("sha256").update(expected).digest("hex");
  const assigned = await client.upload({ browser, action: "set", document: page.document, target: { css: "#single" },
    files: [{ path: ".mdo/qa/remote-bridge/upload.bin", space: "workspace", name: "remote-upload.bin" }], timeout_ms: 15000 });
  ticket = assigned.transfer.ticket; assert.equal(assigned.verified, true); assert.equal(assigned.transfer.retained, true);
  const read = await client.waitFor({ browser, until: "text", text: hash, timeout_ms: 15000 });
  assert.ok(read.observation.text.includes(hash)); assert.ok(read.observation.text.includes(String(expected.length)));
  await mark("remote_frozen_workspace_upload_and_page_full_sha256");

  report.stage = "remote_post_download_and_save"; await save();
  const next = await client.navigate({ browser, action: "goto", url: fixtureOrigin + "/page" }); page = next.observation;
  await client.upload({ browser, action: "release", ticket, confirmed: true }); ticket = null;
  const beforePost = await client.act({ browser, document: page.document, op: "click", target: { css: "#post" } });
  assert.ok(["download_pending", "download_in_progress"].includes(beforePost.outcome));
  let download;
  const downloadDeadline = performance.now() + 10000;
  do {
    download = (await client.download({ browser, action: "list" })).downloads.find(row => row.state === "pending");
    if (download) break; await pause(50);
  } while (performance.now() < downloadDeadline);
  assert.ok(download);
  await client.download({ browser, action: "accept", download: download.id, max_bytes: 1048576 });
  const complete = await client.download({ browser, action: "wait", download: download.id, timeout_ms: 10000 });
  assert.equal(complete.completed, true);
  const saved = await client.save({ browser, download: download.id, path: ".mdo/qa/remote-bridge/saved-download.bin" });
  assert.equal(saved.saved, true); assert.equal(saved.file.size, expected.length); assert.equal(saved.file.sha256, hash);
  assert.ok((await readFile(new URL("../.mdo/qa/remote-bridge/saved-download.bin", import.meta.url))).equals(expected));
  await client.download({ browser, action: "release", download: download.id });
  assert.deepEqual(await sdk.call("files.receive.list", {}), []);
  await mark("remote_retained_post_download_and_target_workspace_exact_save");

  await client.tabs({ action: "close", browser }); browser = null;
  await native.drained(); report.transport = { ...native.metrics };
  await mark("remote_browser_closed_and_native_bridge_work_drained");
  report.passed = true; report.state = "checks_passed";
} catch (error) {
  report.state = "failed"; report.error = { code: error.code, message: String(error.message || error).slice(0, 512), operation: error.operation, receipt: error.receipt };
} finally {
  try {
    if (client && connection) {
      if (!connection.transport.state()) await connection.reconnect();
      if (ticket && browser) await client.upload({ browser, action: "release", ticket, confirmed: true });
      if (browser) await client.tabs({ action: "close", browser });
      for (const id of operations) {
        let current = await sdk.call("operation.get", { id });
        if (!current.terminal) await sdk.call("operation.cancel", { id });
        const deadline = performance.now() + 5000;
        while (!current.terminal && performance.now() < deadline) { await pause(50); current = await sdk.call("operation.get", { id }); }
        assert.ok(current.terminal); await sdk.call("operation.release", { id });
      }
      operations.clear();
    }
  } catch (error) { report.passed = false; report.cleanup_error = String(error.message || error).slice(0, 512); }
  connection?.transport.close();
  try { if (native) await native.close(); }
  catch (error) { report.passed = false; report.native_cleanup_error = String(error.message || error).slice(0, 512); }
  report.state = report.passed ? "passed" : "failed"; await save();
}
if (!report.passed) throw Error("Remote native workflow failed at " + report.stage + ": " + (report.error?.message || report.cleanup_error || report.native_cleanup_error));
console.log(JSON.stringify({ passed: report.passed, cases: report.cases.length, model_calls: 0 }));
