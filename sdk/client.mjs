import { services } from "./contracts.mjs";

export function createBrowserUseClient(sdk) {
  if (typeof sdk?.call !== "function") throw new TypeError("An mdo Application SDK is required");
  const invoke = (name, params) => sdk.call(`browseruse.${name}`, params);
  const complete = name => async (params, options = {}) => {
    if (options.signal?.aborted) throw Object.assign(new Error("Browser call cancelled"), { code: "cancelled" });
    return wait(await invoke(name, params), { timeout: (params.timeout_ms || 10000) + 5000, ...options });
  };
  const callError = current => {
    const error = new Error(current.error?.message || current.error || "Browser operation failed");
    error.code = current.error?.code || current.state;
    error.details = current.result || current.error;
    if (current.operation) error.operation = current.operation;
    return error;
  };
  async function recover(operation, { signal, timeout = 20000, retain = false } = {}) {
    const deadline = Date.now() + timeout;
    try {
      while (!signal?.aborted && Date.now() < deadline) {
        const current = await invoke("operation", { action: "get", operation });
        if (current.state !== "pending") {
          try {
            if (current.state !== "succeeded") throw callError(current);
            return { ...current.result, operation };
          } finally {
            if (!retain) try { await invoke("operation", { action: "release", operation }); } catch {}
          }
        }
        await new Promise(resolve => setTimeout(resolve, 50));
      }
      try { await invoke("operation", { action: "cancel", operation }); } catch {}
      throw Object.assign(new Error("Browser operation stopped; inspect any dispatched effects before retrying"), {
        code: signal?.aborted ? "cancelled" : "timed_out"
      });
    } catch (error) {
      error.operation = operation;
      throw error;
    }
  }
  const perform = name => async (params, options = {}) => {
    if (options.signal?.aborted) throw Object.assign(new Error("Browser call cancelled"), { code: "cancelled" });
    const operation = options.operation ?? `sdk-${crypto.randomUUID().replaceAll("-", "")}`;
    try {
      await invoke("operation", { action: "submit", operation, method: `browseruse.${name}`, arguments: params });
      return await recover(operation, { timeout: (params.timeout_ms || 10000) + 5000, ...options });
    } catch (error) {
      error.operation = operation; // Retain identity even when admission's reply was lost.
      throw error;
    }
  };
  async function wait(receipt, { signal, timeout = 20000 } = {}) {
    const deadline = Date.now() + timeout;
    while (!signal?.aborted && Date.now() < deadline) {
      const current = await invoke("result", { id: receipt.id });
      if (["succeeded", "failed", "cancelled"].includes(current.state)) {
        try {
          if (current.state !== "succeeded") {
            throw callError(current);
          }
          return current.result;
        } finally {
          // Cleanup failure must not turn an observed success into a failed
          // action or hide the actual page error. The host receipt is bounded
          // and expires if a disconnected client cannot release it.
          try { await invoke("result", { id: receipt.id, release: true }); } catch {}
        }
      }
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    try { await invoke("result", { id: receipt.id, cancel: true }); } catch {}
    const error = new Error(signal?.aborted ? "Browser call cancelled; verify any dispatched effects" : "Browser call timed out; verify any dispatched effects");
    error.code = signal?.aborted ? "cancelled" : "timed_out";
    error.receipt = receipt.id;
    throw error;
  }
  return Object.freeze({
    tabs: (params, options) => ["create", "reveal", "close"].includes(params.action) ? perform("tabs")(params, options) : invoke("tabs", params),
    observe: complete("observe"),
    result: params => invoke("result", params),
    act: perform("act"),
    navigate: perform("navigate"),
    waitFor: complete("wait"),
    dialog: (params, options) => params.action === "respond" ? perform("dialog")(params, options) : complete("dialog")(params, options),
    wait,
    operation: params => invoke("operation", params),
    recover,
    services: Object.freeze(services.map(row => row.id))
  });
}
