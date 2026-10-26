import { browserRuntime } from "./automation-runtime.js";

const writers = new WeakSet();
const sameGeometry = (a, b) => a?.element.ref === b?.element.ref &&
  a.geometry?.every((number, index) => Math.abs(number - b.geometry[index]) <= 0.5);

// Frozen workbench instances supply view/action. No selected-chat identity is
// substituted, and this helper never exposes arbitrary script input to a model.
export async function executeBrowserAutomation({ command, params, view, action, api, alive = () => true }) {
  if (!["observe", "act"].includes(command)) return { handled: false };
  const timeout = Math.min(60000, Math.max(1, params.timeout_ms || 10000));
  const deadline = performance.now() + timeout;
  const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
  let dispatched = false;
  const fail = (code, message) => ({ handled: true, result: { ok: false,
    error: { code, message, effect_may_have_occurred: dispatched }, action: action.id } });
  if (command === "act" && writers.has(view)) return fail("browser_busy", "Another action is writing to this tab; inspect its receipt before retrying");
  if (command === "act") writers.add(view);
  let lastReceipt = -Infinity, navigation;
  const read = async (method, options) => {
    const result = await view.execute("automation", { agent: true,
      script: `(${browserRuntime.toString()})().${method}(${JSON.stringify(options)})` });
    if (!result.ok || typeof result.value?.ok !== "boolean") throw Error("The browser returned an invalid automation result");
    return result.value;
  };
  const guard = async (force = false) => {
    if (!alive()) return fail("context_changed", "The displaying device or window changed");
    if (performance.now() >= deadline) return fail("timed_out", "The browser action deadline expired");
    if (force || performance.now() - lastReceipt > 200) {
      const receipt = await api.call("ui.result", { id: action.id });
      lastReceipt = performance.now();
      if (receipt.state === "cancelled") return fail("cancelled", "The originating browser call was cancelled");
    }
    const state = await view.execute("state");
    if (state.error || state.process_failed) return fail("browser_unavailable", state.error || "The browser process stopped");
    if (state.manual) return fail("manual_control", "The user currently controls the browser");
    if (typeof state.loading !== "boolean" || !state.automation) return fail("unsupported_capability", "This host does not provide isolated Browser Use execution and readiness");
    if (navigation !== undefined && state.navigation !== navigation) return fail("stale_document", "The document changed; inspect it before another input");
    return { state };
  };
  const send = async parameters => {
    const stopped = await guard(true); if (stopped.handled) return stopped;
    dispatched = true;
    const delivered = await view.execute("input", { ...parameters, agent: true, navigation });
    if (!delivered.accepted || !delivered.trusted_input) return fail("action_uncertain", "Native input was not confirmed; inspect the page before repeating it");
    return null;
  };
  try {
    while (performance.now() < deadline) {
      const checked = await guard(); if (checked.handled) return checked;
      const { state } = checked;
      if (!state.loading) {
        if (command === "act") { navigation = state.navigation; break; }
        const options = { mode: params.mode, limit: params.limit, max_bytes: params.max_bytes };
        const result = await read("observe", options);
        if (!result.ok) return { handled: true, result };
        if (["interactive", "complete"].includes(result.ready)) return { handled: true, result };
      }
      await pause(Math.min(50, Math.max(0, deadline - performance.now())));
    }
    if (command === "observe" || navigation === undefined) return fail("timed_out", "The page was not ready before the deadline");
    if (params.op === "scroll" && !params.target) {
      const snapshot = await read("observe", { limit: 1, max_bytes: 256 });
      if (snapshot.document !== params.document) return fail("stale_document", "Observe the current document before scrolling");
      const error = await send({ kind: "wheel", x: snapshot.viewport.width / 2, y: snapshot.viewport.height / 2, dx: params.dx || 0, dy: params.dy || 0 });
      if (error) return error;
    } else {
      let previous, prepared;
      while (performance.now() < deadline) {
        const stopped = await guard(); if (stopped.handled) return stopped;
        prepared = await read("prepare", { ...params, scroll: true });
        if (prepared.ok && sameGeometry(prepared, previous)) break;
        if (!prepared.ok && !["element_not_found", "element_disabled", "element_not_editable", "element_obscured", "element_outside_viewport"].includes(prepared.error?.code)) return { handled: true, result: prepared };
        previous = prepared.ok ? prepared : null;
        await pause(Math.min(60, Math.max(0, deadline - performance.now())));
      }
      const stopped = await guard(true); if (stopped.handled) return stopped;
      if (!prepared?.ok || !sameGeometry(prepared, previous)) return fail("timed_out", "The target did not become actionable and stable before the deadline");
      const pinned = { ...params, target: { ref: prepared.element.ref } };
      const fresh = await read("prepare", pinned);
      if (!fresh.ok || !sameGeometry(fresh, prepared)) return fail("target_changed", "The target moved or changed immediately before input");
      if (params.op === "check" && fresh.element.checked === params.checked) return { handled: true, result: { ok: true, changed: false, action: action.id, verified: true } };
      if (params.op === "check" && fresh.element.role === "radio" && !params.checked) return fail("unsupported_action", "A radio input cannot be unchecked by clicking it; select another radio in the group");
      let error = await send({ kind: params.op === "hover" ? "move" : params.op === "scroll" ? "wheel" : "click", ...fresh.point, dx: params.dx || 0, dy: params.dy || 0 });
      if (error) return error;
      if (["fill", "type", "key"].includes(params.op)) {
        const focus = await read("verify", pinned);
        if (!focus.ok || !focus.focused) return fail("focus_changed", "The intended element lost focus before keyboard input");
        if (params.op === "fill") {
          error = await send({ kind: "key", key: "Control+A" }); if (error) return error;
        }
        error = await send(params.op === "key" ? { kind: "key", key: params.key } :
          params.op === "fill" && !params.text ? { kind: "key", key: "Backspace" } : { kind: "text", text: params.text });
        if (error) return error;
      }
      if (["fill", "check"].includes(params.op)) {
        const verified = await read("verify", pinned);
        if (!verified.ok || !verified.verified) return fail("action_uncertain", "Input was dispatched but the requested field state was not confirmed; inspect before repeating it");
      }
    }
    const stopped = await guard(true); if (stopped.handled) return stopped;
    return { handled: true, result: { ok: true, action: action.id, dispatched,
      verification: ["fill", "check"].includes(params.op) ? "field_state" : "native_delivery",
      observation: await read("observe", { limit: 100, max_bytes: 4096 }) } };
  } catch (error) {
    return fail(dispatched ? "action_uncertain" : "browser_unavailable", String(error?.message || error).slice(0, 512));
  } finally {
    if (command === "act") writers.delete(view);
  }
}
