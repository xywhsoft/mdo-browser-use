// Private pipe adapter around the production native bridge. No account or
// relay authentication is replaced/claimed: the pipe admits synthetic peers.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { spawn } from "node:child_process";
import { createInterface } from "node:readline";
import { EventEmitter } from "node:events";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { createRemoteTransport } from "../.mdo/source/app/web/js/api/remote-transport.js";

const root = fileURLToPath(new URL("../", import.meta.url));
const sha = data => createHash("sha256").update(data).digest("hex");
const pause = ms => new Promise(done => setTimeout(done, ms));

export async function nativeBridge() {
  const receipt = JSON.parse(await readFile(new URL("../.mdo/qa/remote-bridge/build.json", import.meta.url)));
  const env = JSON.parse(await readFile(new URL("../.mdo/environment.json", import.meta.url)));
  for (const [name, hash] of Object.entries(receipt.sources))
    assert.equal(sha(await readFile(resolve(root, name))), hash, "Rebuild the changed native bridge fixture input: " + name);
  assert.equal(sha(await readFile(resolve(root, receipt.binary))), receipt.sha256, "Native bridge binary changed");
  const child = spawn(resolve(root, receipt.binary), [String(env.port)], { cwd: root, stdio: ["pipe", "pipe", "pipe"], windowsHide: true });
  const events = new EventEmitter(), commands = new Map(), sockets = new Map(), inputHistory = [];
  const metrics = { input_frames: 0, output_frames: 0, input_bytes: 0, output_bytes: 0 };
  const states = [];
  let serial = 0, stopped, stderr = "", failure, exit, intercept = null;
  const terminal = new Promise((done, reject) => {
    child.on("error", reject); child.on("exit", (code, signal) => { exit = { code, signal }; done(exit); });
  });
  terminal.catch(error => { failure = error; });
  child.stderr.on("data", bytes => { stderr = (stderr + bytes.toString()).slice(-2048); });
  const rejectAll = error => { failure = error; for (const entry of commands.values()) entry.reject(error); commands.clear(); events.emit("failure", error); };
  const lines = createInterface({ input: child.stdout });
  const ready = new Promise((done, reject) => { events.once("ready", done); events.once("failure", reject); });
  lines.on("line", line => {
    try {
      const row = JSON.parse(line);
      if (row.type === "ready") { events.emit("ready", row); return; }
      if (row.type === "stopped") { stopped = row; return; }
      if (row.type === "ack" || row.type === "stats") {
        const entry = commands.get(row.id); commands.delete(row.id);
        if (!entry) throw Error("Unmatched native bridge acknowledgement");
        if (row.type === "ack" && !row.ok) entry.reject(Error("Native bridge fixture command refused"));
        else entry.resolve(row);
        return;
      }
      assert.equal(row.type, "frame");
      const frame = Buffer.from(row.base64, "base64");
      assert.ok(frame.length >= 21 && frame.subarray(0, 4).equals(Buffer.from("MDR1")));
      const peer = frame.subarray(4, 20).toString("hex"), kind = frame[20];
      assert.ok(kind === 1 || kind === 2);
      const payload = frame.subarray(21);
      const item = { peer, route: row.route, binary: kind === 2, data: payload };
      metrics.output_frames++; metrics.output_bytes += payload.length;
      events.emit("frame", item);
      if (intercept?.(item) === false) return;
      const socket = sockets.get(peer);
      if (socket?.readyState === 1 && row.route === socket.route)
        socket.onmessage?.({ data: kind === 2 ? payload.buffer.slice(payload.byteOffset, payload.byteOffset + payload.length) : payload.toString("utf8") });
    } catch (error) { rejectAll(error); }
  });
  child.on("exit", () => { if (commands.size) rejectAll(Error("Native bridge exited with commands pending")); });
  function command(command, fields = {}) {
    if (failure || exit) return Promise.reject(failure || Error("Native bridge is no longer running"));
    const id = ++serial;
    return new Promise((resolve, reject) => {
      commands.set(id, { resolve, reject });
      child.stdin.write(JSON.stringify({ command, ...fields, id }) + "\n", error => { if (error) { commands.delete(id); reject(error); } });
    });
  }
  function message(peer, data, route = 0) {
    const binary = typeof data !== "string", bytes = binary ? Buffer.from(data) : Buffer.from(data, "utf8");
    metrics.input_frames++; metrics.input_bytes += bytes.length;
    if (!binary) {
      const value = JSON.parse(data);
      inputHistory.push({ peer, route, type: value.type, id: value.id, method: value.method, path: value.path });
      if (inputHistory.length > 512) inputHistory.shift();
    }
    return command("message", { peer, route, binary, base64: bytes.toString("base64") });
  }
  const fixture = {
    env, metrics, inputHistory, command, message, events, states,
    intercept(fn) { intercept = fn; },
    async connect({ peer = "c1".repeat(16), mode = "control" } = {}) {
      let active;
      const transport = createRemoteTransport({ onState(value) {
        states.push({ connected: value.connected, code: value.error?.code, message: value.error?.message });
      }, socket() {
        const socket = { readyState: 1, route: 0, onmessage: null, onerror: null, onclose: null,
          send(data) { void message(peer, data, socket.route).catch(error => { socket.onerror?.(error); }); },
          close() {
            if (socket.readyState !== 1) return;
            socket.readyState = 3;
            void command("close", { peer }).catch(error => { failure = error; });
            queueMicrotask(() => socket.onclose?.({ code: 1000 }));
          }
        };
        active = socket; sockets.set(peer, socket);
        queueMicrotask(() => {
          socket.onmessage?.({ data: JSON.stringify({ type: "ready", version: 1, device_id: "d1".repeat(16), mode, peer_id: peer, payload_limit: 262123 }) });
          void command("open", { peer, read_only: mode === "view" }).catch(error => { socket.onerror?.(error); });
        });
        return socket;
      }});
      const details = { origin: "http://127.0.0.1/", deviceId: "d1".repeat(16), mode,
        ticket: { path: "/api/v1/devices/connect", protocol: "xadmin.device-relay.v1", ticket: "0".repeat(64), payload_limit: 262123 } };
      await transport.connect(details);
      return { transport, peer, get socket() { return active; }, reconnect: () => transport.connect(details) };
    },
    async drained() {
      const deadline = performance.now() + 5000; let current;
      do {
        current = await command("stats");
        if (!current.requests && !current.threads && !current.upload_bytes && !current.packets && !current.queue_bytes) return current;
        await pause(20);
      } while (performance.now() < deadline);
      throw Object.assign(Error("Native bridge work did not drain"), { stats: current, pid: child.pid });
    },
    async close() {
      for (const socket of sockets.values()) socket.close();
      child.stdin.end();
      let alarm;
      // Timeout reports the live PID; it never kills/restarts a process on an
      // expired observation. A failing live process remains available to inspect.
      try { await Promise.race([terminal, new Promise((_, reject) => { alarm = setTimeout(() => reject(Object.assign(Error("Native bridge still live after EOF"), { pid: child.pid })), 12000); })]); }
      finally { clearTimeout(alarm); lines.close(); }
      assert.equal(exit.code, 0, "Native bridge did not stop cleanly: " + JSON.stringify({ stopped, stderr }));
      assert.ok(stopped && !stopped.leases && stopped.engine_clean && !stopped.input_failed && stopped.input_peak <= 16);
      if (failure) throw failure;
    }
  };
  let alarm;
  try { await Promise.race([ready, terminal.then(() => { throw Error("Native bridge exited before ready"); }),
    new Promise((_, reject) => { alarm = setTimeout(() => reject(Error("Native bridge readiness is still unconfirmed")), 10000); })]); }
  catch (error) { await fixture.close(); throw error; }
  finally { clearTimeout(alarm); }
  return fixture;
}

export async function remoteSdk(transport, context) {
  const bootstrap = await transport.fetch("/api/v1/bootstrap");
  const token = bootstrap.headers.get("X-Mdo-Write-Token"); await bootstrap.arrayBuffer();
  assert.equal(bootstrap.status, 200); assert.ok(token);
  return { async call(method, params = {}, options = {}) {
    const response = await transport.fetch("/api/v1/application/call", { method: "POST", signal: options.signal,
      headers: { "Content-Type": "application/json", "X-Mdo-Write-Token": token }, body: JSON.stringify({ method, params, context }) });
    const value = await response.json();
    if (!response.ok || !value.ok) throw Object.assign(new Error(value.error?.message || "Native target refused SDK call"), { code: value.error?.code, status: response.status, details: value.error?.details });
    return value.data;
  }};
}
