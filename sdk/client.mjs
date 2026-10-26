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
    const error = new Error(signal?.aborted ? "Browser observation cancelled" : "Browser observation timed out");
    error.code = signal?.aborted ? "cancelled" : "timed_out";
    throw error;
  }
  return Object.freeze({
    tabs: params => invoke("tabs", params),
    observe: async (params, options = {}) => wait(await invoke("observe", params), {
      timeout: (params.timeout_ms || 10000) + 5000, ...options
    }),
    result: params => invoke("result", params),
    wait,
    services: Object.freeze(services.map(row => row.id))
  });
}
