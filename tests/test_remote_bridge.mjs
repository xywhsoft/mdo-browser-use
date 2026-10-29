import assert from "node:assert/strict";
import { test } from "node:test";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { open } from "node:fs/promises";
import { createBrowserUseClient } from "../sdk/client.mjs";
import { nativeBridge, remoteSdk } from "../tools/remote_bridge_client.mjs";

const context = { extension: "browser-use-native-remote-qa", project: "default", session: "native-remote-task" };

test("production JS and native relay bridge reach the actual C SDK and preserve browser scope", async () => {
  const native = await nativeBridge(); let transport, client, browser;
  try {
    ({ transport } = await native.connect());
    const sdk = await remoteSdk(transport, context); client = createBrowserUseClient(sdk);
    const before = (await client.tabs({ action: "list" })).tabs;
    await sdk.call("client.hello", { client: "browser-use-native-remote-window", context: {} });
    const original = "sdk-" + crypto.randomUUID().replaceAll("-", "");
    const result = await client.tabs({ action: "create", client: "browser-use-native-remote-window", key: "native-remote-qa" }, { operation: original, retain: true });
    browser = result.id;
    assert.equal(typeof browser, "string"); assert.equal(result.operation, original);
    assert.equal((await client.tabs({ action: "list" })).tabs.length, before.length + 1);
    const foreign = await remoteSdk(transport, { ...context, session: "another-native-remote-task" });
    await assert.rejects(foreign.call("browseruse.operation", { action: "get", operation: original }), { code: "context_mismatch" });
    await client.operation({ action: "release", operation: original });
    await client.tabs({ action: "close", browser }); browser = null;
    await native.drained();
    assert.ok(native.metrics.input_frames > 6 && native.metrics.output_frames > 6);
  } finally {
    if (browser && client) await client.tabs({ action: "close", browser });
    transport?.close(); await native.close();
  }
});

test("a completed native relay write lost on disconnect recovers by its two original receipts", async () => {
  const native = await nativeBridge(); let connection, client, browser, sdk, operation, submitted = false, problem;
  try {
    connection = await native.connect(); sdk = await remoteSdk(connection.transport, context);
    await sdk.call("client.hello", { client: "browser-use-native-remote-window", context: {} });
    const before = (await sdk.call("browseruse.tabs", { action: "list" })).tabs;
    let submissions = 0;
    client = createBrowserUseClient({ call(method, params, options) {
      if (method === "browseruse.operation" && params.action === "submit") submissions++;
      return sdk.call(method, params, options);
    }});
    let target;
    native.intercept(frame => {
      if (frame.binary) return !target || frame.data.subarray(5, 21).toString("hex") !== target;
      const value = JSON.parse(frame.data);
      if (value.type === "request_ready" && !target) target = value.id;
      if (value.id !== target || ["request_ready", "upload_ack"].includes(value.type)) return true;
      if (value.type === "end") { native.intercept(null); connection.socket.close(); }
      return false;
    });
    operation = "sdk-" + crypto.randomUUID().replaceAll("-", ""); submitted = true;
    let lost;
    await assert.rejects(client.tabs({ action: "create", client: "browser-use-native-remote-window", key: "lost-native-remote-reply" }, { operation, retain: true }), error => {
      lost = error; return error.code === "remote_result_unconfirmed" && error.operation === operation;
    });
    await connection.reconnect();
    const receipt = await connection.transport.queryReceipt(lost.details);
    assert.equal(receipt.id, target); assert.equal(receipt.state, "done"); assert.equal(receipt.status, 200);
    const recovered = await client.recover(operation, { retain: true }); browser = recovered.id;
    assert.equal(submissions, 1); assert.equal(recovered.operation, operation);
    const after = (await sdk.call("browseruse.tabs", { action: "list" })).tabs;
    assert.equal(after.length, before.length + 1);
    assert.equal(after.filter(row => row.id === browser).length, 1);
    await client.operation({ action: "release", operation }); submitted = false;
    await client.tabs({ action: "close", browser }); browser = null;
    await native.drained();
  } catch (error) { problem = error; throw error;
  } finally {
    native.intercept(null);
    try {
      if (submitted || browser) {
        if (!connection.transport.state()) await connection.reconnect();
        if (!browser) browser = (await client.recover(operation, { retain: true })).id;
        if (submitted) await client.operation({ action: "release", operation });
        if (browser) await client.tabs({ action: "close", browser });
      }
    } catch (error) { if (!problem) throw error; problem.cleanup_error = error.message; }
    finally { connection?.transport.close(); await native.close(); }
  }
});

test("read-only mode is enforced by the production controller and native bridge", async () => {
  const native = await nativeBridge(); let connection;
  try {
    connection = await native.connect({ mode: "view" });
    const sdk = await remoteSdk(connection.transport, context);
    const before = native.inputHistory.length;
    await assert.rejects(sdk.call("browseruse.tabs", { action: "create" }), { code: "read_only" });
    assert.equal(native.inputHistory.length, before, "view-only SDK mutation reached the native target");
    // Bypass only the JS guard to test the production C guard too. The body is
    // empty and no token is sent; native read-only rejection precedes dispatch.
    const id = crypto.randomUUID().replaceAll("-", "");
    let detach, alarm;
    const refused = new Promise((done, reject) => {
      const receive = frame => {
        if (frame.binary) return;
        const value = JSON.parse(frame.data);
        if (value.id === id) { detach(); done(value); }
      };
      detach = () => { native.events.removeListener("frame", receive); clearTimeout(alarm); };
      native.events.on("frame", receive); alarm = setTimeout(() => { detach(); reject(Error("Native view-only refusal not received")); }, 3000);
    });
    try {
      await native.message(connection.peer, JSON.stringify({ type: "request", id, runtime_id: connection.transport.state().runtime_id,
        client_id: "e1".repeat(16), sequence: 1, method: "POST", path: "/api/v1/application/call", headers: [], bytes: 0,
        sha256: createHash("sha256").digest("hex") }));
      const result = await refused;
      assert.equal(result.type, "error"); assert.equal(result.code, "read_only"); assert.equal(result.outcome, "not_started");
    } finally { detach(); }
    await native.drained();
  } finally { connection?.transport.close(); await native.close(); }
});

test("native bridge verifies multiple upload/download chunks without another app or Home", async () => {
  const native = await nativeBridge(); let transport, sdk, written, problem;
  const path = fileURLToPath(new URL("../.mdo/qa/remote-bridge/chunks.txt", import.meta.url));
  const text = "Synthetic bridge UTF-8 \u96e8\ud83e\udd89\n".repeat(5000);
  try {
    ({ transport } = await native.connect()); sdk = await remoteSdk(transport, context);
    // Exclusive creation preserves an unrelated or retained existing entry.
    const file = await open(path, "wx"); await file.close();
    written = await sdk.call("fs.stat", { path });
    written = await sdk.call("fs.write", { path, text, revision: written.revision });
    assert.equal(written.bytes, Buffer.byteLength(text));
    const read = await sdk.call("fs.read", { path, limit: 256 * 1024 });
    assert.equal(read.text, text); assert.equal(read.next, Buffer.byteLength(text));
    assert.ok(native.metrics.input_bytes > 65536 && native.metrics.output_bytes > 65536);
    await sdk.call("fs.remove", { path, revision: written.revision }); written = null;
    await native.drained();
  } catch (error) { problem = error; error.transport_states = native.states; throw error;
  } finally {
    try {
      if (written && sdk) {
        if (!transport.state()) { ({ transport } = await native.connect()); sdk = await remoteSdk(transport, context); }
        await sdk.call("fs.remove", { path, revision: written.revision });
      }
    } catch (error) { if (!problem) throw error; problem.cleanup_error = error.message; }
    finally {
      transport?.close();
      try { await native.close(); } catch (error) { if (!problem) throw error; problem.native_cleanup_error = error.message; }
    }
  }
});
