import { services } from "./contracts.mjs";

export function createBrowserUseClient(sdk) {
  if (typeof sdk?.call !== "function") throw new TypeError("An mdo Application SDK is required");
  const invoke = (name, params) => sdk.call(`browseruse.${name}`, params);
  async function wait(receipt, { signal, timeout = 20000 } = {}) {
    const deadline = Date.now() + timeout;
    while (!signal?.aborted && Date.now() < deadline) {
      const current = await invoke("result", { id: receipt.id });
      if (["succeeded", "failed", "cancelled"].includes(current.state)) {
        try {
          if (current.state !== "succeeded") {
            const error = new Error(current.error?.message || current.error || "Browser operation failed");
            error.code = current.error?.code || current.state;
            error.details = current.result || current.error;
            throw error;
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
    tabs: params => invoke("tabs", params),
    observe: async (params, options = {}) => {
      if (options.signal?.aborted) throw Object.assign(new Error("Browser call cancelled"), { code: "cancelled" });
      return wait(await invoke("observe", params), { timeout: (params.timeout_ms || 10000) + 5000, ...options });
    },
    result: params => invoke("result", params),
    act: async (params, options = {}) => {
      if (options.signal?.aborted) throw Object.assign(new Error("Browser call cancelled"), { code: "cancelled" });
      return wait(await invoke("act", params), { timeout: (params.timeout_ms || 10000) + 5000, ...options });
    },
    wait,
    services: Object.freeze(services.map(row => row.id))
  });
}
