import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const source = (await readFile(new URL("../host/automation-dispatch.js", import.meta.url), "utf8"))
  .replace('import { browserRuntime } from "./automation-runtime.js";', 'function browserRuntime() {}');
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
