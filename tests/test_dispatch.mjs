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
