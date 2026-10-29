import { services } from "./contracts.mjs";

export function createBrowserUseClient(sdk) {
  if (typeof sdk?.call !== "function") throw new TypeError("An mdo Application SDK is required");
  const invoke = (name, params, options) => sdk.call(`browseruse.${name}`, params, options);
  const stoppedError = code => Object.assign(new Error(code === "cancelled" ?
    "Browser call cancelled; inspect any dispatched effects before retrying" :
    "Browser call timed out; inspect any dispatched effects before retrying"), { code });
  // One budget includes admission, response-body reads and every poll. Race the
  // adapter too: forwarding a signal alone cannot bound an adapter that ignores it.
  function budget({ signal, timeout } = {}, fallback = 20000) {
    const duration = timeout === undefined ? fallback : timeout;
    if (!Number.isFinite(duration) || duration < 0 || duration > 2147483647)
      throw new TypeError("Browser call timeout must be a finite nonnegative timer duration");
    const controller = new AbortController(); let stopped = null, timer;
    const stop = code => { if (!stopped) { stopped = code; controller.abort(); } };
    const abort = () => stop("cancelled");
    if (signal?.aborted) abort();
    else if (duration === 0) stop("timed_out");
    else { signal?.addEventListener("abort", abort, { once: true }); timer = setTimeout(() => stop("timed_out"), duration); }
    const race = work => {
      if (stopped) return Promise.reject(stoppedError(stopped));
      return new Promise((resolve, reject) => {
        const cancel = () => { controller.signal.removeEventListener("abort", cancel); reject(stoppedError(stopped)); };
        controller.signal.addEventListener("abort", cancel, { once: true });
        Promise.resolve().then(() => { if (stopped) throw stoppedError(stopped); return work(); }).then(
          value => { controller.signal.removeEventListener("abort", cancel); resolve(value); },
          error => { controller.signal.removeEventListener("abort", cancel); reject(stopped ? stoppedError(stopped) : error); });
      });
    };
    return {
      stopped: () => stopped,
      call: (name, params) => race(() => invoke(name, params, { signal: controller.signal })),
      async pause() { let wake; try { await race(() => new Promise(resolve => { wake = setTimeout(resolve, 50); })); } finally { clearTimeout(wake); } },
      dispose() { clearTimeout(timer); signal?.removeEventListener("abort", abort); }
    };
  }
  async function usingBudget(options, fallback, work) {
    const scope = budget(options, fallback);
    try { return await work(scope); } finally { scope.dispose(); }
  }
  // A cancelled caller's signal is unusable for cleanup. Use a separate bounded
  // attempt on the same SDK; never let a lost release replace an observed result.
  async function cleanup(name, params) {
    try { await usingBudget({}, 1000, scope => scope.call(name, params)); } catch {}
  }
  const single = name => (params, options) => usingBudget(options, 20000, scope => scope.call(name, params));
  const complete = name => (params, options) => usingBudget(options, (params.timeout_ms || 10000) + 5000,
    async scope => settleReceipt(await scope.call(name, params), scope));
  const callError = current => {
    const error = new Error(current.error?.message || current.error || "Browser operation failed");
    error.code = current.error?.code || current.state;
    error.details = current.result || current.error;
    if (current.operation) error.operation = current.operation;
    return error;
  };
  async function settleOperation(operation, scope, retain = false) {
    let terminal = false;
    try {
      for (;;) {
        const current = await scope.call("operation", { action: "get", operation });
        if (current.state !== "pending") {
          terminal = true;
          try {
            if (current.state !== "succeeded") throw callError(current);
            return { ...current.result, operation };
          } finally {
            if (!retain) await cleanup("operation", { action: "release", operation });
          }
        }
        await scope.pause();
      }
    } catch (error) {
      if (!terminal && scope.stopped()) await cleanup("operation", { action: "cancel", operation });
      error.operation = operation;
      throw error;
    }
  }
  const recover = (operation, options = {}) => usingBudget(options, 20000,
    scope => settleOperation(operation, scope, options.retain));
  const perform = name => async (params, options = {}) => {
    if (options.signal?.aborted) throw Object.assign(new Error("Browser call cancelled"), { code: "cancelled" });
    const operation = options.operation ?? `sdk-${crypto.randomUUID().replaceAll("-", "")}`;
    return usingBudget(options, (params.timeout_ms || 10000) + 5000, async scope => {
      let admitted = false;
      try {
        await scope.call("operation", { action: "submit", operation, method: `browseruse.${name}`, arguments: params });
        admitted = true;
        return await settleOperation(operation, scope, options.retain);
      } catch (error) {
        if (!admitted && scope.stopped()) await cleanup("operation", { action: "cancel", operation });
        error.operation = operation; // Admission can arrive after cancellation; recover this ID, never resubmit.
        throw error;
      }
    });
  };
  async function settleReceipt(receipt, scope) {
    let terminal = false;
    try {
      for (;;) {
        const current = await scope.call("result", { id: receipt.id });
        if (["succeeded", "failed", "cancelled"].includes(current.state)) {
          terminal = true;
          try {
            if (current.state !== "succeeded") throw callError(current);
            return current.result;
          } finally {
            await cleanup("result", { id: receipt.id, release: true });
          }
        }
        await scope.pause();
      }
    } catch (error) {
      if (!terminal && scope.stopped()) await cleanup("result", { id: receipt.id, cancel: true });
      error.receipt = receipt.id;
      throw error;
    }
  }
  const wait = (receipt, options) => usingBudget(options, 20000, scope => settleReceipt(receipt, scope));
  return Object.freeze({
    tabs: (params, options) => ["create", "reveal", "close"].includes(params.action) ? perform("tabs")(params, options) : single("tabs")(params, options),
    observe: complete("observe"),
    screenshot: complete("screenshot"),
    upload: (params, options) => params.action === "status" ? complete("upload")(params, options) : perform("upload")(params, options),
    download: (params, options) => ["accept","cancel","release"].includes(params.action) ? perform("download")(params, options) : complete("download")(params, options),
    save: (params, options) => perform("save")({...params,timeout_ms:params.timeout_ms===undefined?60000:params.timeout_ms}, options),
    result: single("result"),
    act: perform("act"),
    navigate: perform("navigate"),
    waitFor: complete("wait"),
    dialog: (params, options) => params.action === "respond" ? perform("dialog")(params, options) : complete("dialog")(params, options),
    chooser: (params, options) => params.action === "cancel" ? perform("chooser")(params, options) : complete("chooser")(params, options),
    wait,
    operation: single("operation"),
    recover,
    services: Object.freeze(services.map(row => row.id))
  });
}
