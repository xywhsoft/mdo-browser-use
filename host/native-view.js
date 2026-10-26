// The UI and Agent dispatcher use the same admission descriptor. Concurrent
// mounts must attach to one native view, not navigate a second popup target.
export async function ensureNativeBrowser({ bridge, sdk, browser, clientId, bounds, visible = false, alive = () => true }) {
  const pending = browser.runtime?.popup_request;
  const check = () => { if (!alive()) throw new DOMException("浏览器所属窗口或设备已改变", "AbortError"); };
  check();
  if (pending && (pending.opener !== browser.opener || pending.client !== clientId || browser.client !== clientId))
    throw new Error("此弹出页的原生请求属于之前的窗口，请关闭失效标签后重新操作。");
  const result = await bridge.call("create", { browser: browser.id, url: browser.url || "about:blank", bounds, visible,
    ...(pending ? { popup: pending.id, opener: pending.opener } : {}) });
  check();
  if (pending) await sdk.call("browser.popup.settle", { id: browser.id, client: clientId, popup: pending.id });
  check();return result;
}
