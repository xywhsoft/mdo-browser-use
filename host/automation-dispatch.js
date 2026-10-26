import { browserRuntime } from "./automation-runtime.js";

// Frozen workbench instances supply view/action. No selected-chat identity is
// substituted, and this helper never exposes arbitrary script input to a model.
export async function executeBrowserAutomation({ command, params, view, action, api }) {
  if (command !== "observe") return { handled: false };
  const timeout = Math.min(60000, Math.max(1, params.timeout_ms || 10000));
  const deadline = performance.now() + timeout;
  const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
  const fail = (code, message) => ({ handled: true, result: { ok: false, error: { code, message } } });
  let lastReceipt = -Infinity;
  try {
    while (performance.now() < deadline) {
      if (performance.now() - lastReceipt > 200) {
        const receipt = await api.call("ui.result", { id: action.id });
        lastReceipt = performance.now();
        if (receipt.state === "cancelled") return fail("cancelled", "The originating browser call was cancelled");
      }
      const state = await view.execute("state");
      if (state.error || state.process_failed) return fail("browser_unavailable", state.error || "The browser process stopped");
      if (state.manual) return fail("manual_control", "The user currently controls the browser");
      if (typeof state.loading !== "boolean") return fail("unsupported_capability", "This backend does not report page readiness");
      if (!state.loading) {
        const options = { mode: params.mode, limit: params.limit, max_bytes: params.max_bytes };
        const result = await view.execute("evaluate", {
          agent: true, script: `(${browserRuntime.toString()})().observe(${JSON.stringify(options)})`
        });
        if (!result.ok || !result.value?.ok) return fail("observation_failed", "The browser returned an invalid observation");
        if (["interactive", "complete"].includes(result.value.ready)) return { handled: true, result: result.value };
      }
      await pause(Math.min(50, Math.max(0, deadline - performance.now())));
    }
    return fail("timed_out", "The page was not ready before the observation deadline");
  } catch (error) {
    return fail("browser_unavailable", String(error?.message || error).slice(0, 512));
  }
}
