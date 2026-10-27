import { safeBrowserUrl } from "./model.js";
import { ensureNativeBrowser } from "./native-view.js";

// The native message only schedules reconciliation. Ownership, URLs and request
// identities are read again from the original native opener and broker scope.
export function createBrowserPopups({ bridge, api, views, clientId, alive, open, closePanels, onError }) {
  const queued = new Set(), errors = new Map(); let disposed = false, draining;
  const live = () => !disposed && alive();
  const scoped = row => api.bind({ ...row.context, extension: row.owner });
  const report = (browser, error) => { if (errors.get(browser) !== error.message) { errors.set(browser,error.message);onError?.(error); } };
  async function reconcile(browser) {
    const view = views.get(browser);if (!view || !live()) return;
    const state = await view.execute("state");if (!live()) return;
    const sdk = scoped(view.row);
    if (state.closed_by_page) {
      // The final release removes native ownership before client.ack commits
      // its UI receipt. Automatic closure must not cancel that same action.
      const pending = await sdk.call("client.poll", { client: clientId });
      if (!live() || pending.some(action => action.action === "workbench.browser" && action.params?.id === browser)) return;
      await sdk.call("browser.close", { id: browser });if (!live()) return;
      await bridge.call("close", { browser });views.delete(browser);
      if (live()) await closePanels(browser);return;
    }
    if (!state.popup_capability) return;
    const pending = await bridge.call("popups", { browser, action: "list" });
    if (!pending.popups?.length) errors.delete(browser);
    for (const request of (pending.popups || []).slice(0, 4)) {
      if (!live()) return;
      let child, adopted = false;
      try {
        const url = request.url === "about:blank" ? "about:blank" : safeBrowserUrl(request.url);
        child = await sdk.call("browser.popup.admit", { opener: browser, client: clientId, popup: request.id, url });
        if (!live()) return;
        if (!child.runtime?.popup_request) {
          // A settled admission is a receipt, never permission to recreate a
          // missing native window after a lost reply or restart.
          const existing = await bridge.call("state", { browser: child.id });
          if (existing.opener !== browser) throw new Error("弹出页的原生连接已失效，请核实标签页状态。");
        } else await ensureNativeBrowser({ bridge, sdk, browser: child, clientId, alive: live, requireDownloads:state.downloads_armed===true,
          bounds: { x: 0, y: 0, width: 1024, height: 768, scale: 1 } });
        adopted = true;
        if (!live()) return;
        if (!views.has(child.id)) views.set(child.id, { row: child,
          execute: (command, params = {}) => bridge.call(command, { browser: child.id, ...params }) });
        await open(child, sdk, request.user_initiated ? "activate" : "background");
      } catch (error) {
        if (!live()) return;
        if (!adopted) {
          // Dismiss only the still-pending native request. Keep any admitted
          // child receipt when its effect is uncertain; never create a new ID.
          try { await bridge.call("popups", { browser, action: "dismiss", popup: request.id }); } catch {}
        }
        if (live()) report(browser,new Error(adopted ? "弹出页已打开，但面板未能显示。可从已有浏览器标签中重新打开。" :
          child ? "弹出页执行结果未确认，请检查已有标签后再操作。" : `未能接管网页新窗口：${error.message}`));
      }
    }
  }
  function pump() {
    if (draining) return draining;
    draining = (async () => {
      while (live() && queued.size) {
        const browser = queued.values().next().value;queued.delete(browser);
        try { await reconcile(browser); } catch (error) { if (live() && views.has(browser)) report(browser,error); }
      }
    })().finally(() => { draining = null; });
    return draining;
  }
  const request = browser => { if (live() && views.has(browser)) queued.add(browser);return pump(); };
  return Object.freeze({ reconcile: request,
    scan() { if (live()) for (const browser of views.keys()) queued.add(browser);return pump(); },
    dispose() { disposed = true;queued.clear();errors.clear(); }
  });
}
