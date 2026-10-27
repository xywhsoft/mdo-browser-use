// The UI and Agent dispatcher use the same admission descriptor. Concurrent
// mounts must attach to one native view, not navigate a second popup target.
export async function ensureNativeBrowser({ bridge, sdk, browser, clientId, bounds, visible = false, requireDownloads = false, alive = () => true }) {
  const pending = browser.runtime?.popup_request;
  const check = () => { if (!alive()) throw new DOMException("浏览器所属窗口或设备已改变", "AbortError"); };
  check();
  if (pending && (pending.opener !== browser.opener || pending.client !== clientId || browser.client !== clientId))
    throw new Error("此弹出页的原生请求属于之前的窗口，请关闭失效标签后重新操作。");
  const required=requireDownloads||browser.download_policy==='intercept';
  if(required){
    const capability=await bridge.call('capabilities');check();
    if(capability.initial_download_interception!==true)throw new Error('当前客户端不能在打开网址前安全拦截下载，请更新客户端后重试。');
  }
  const result = await bridge.call("create", { browser: browser.id, url: browser.url || "about:blank", bounds, visible, ...(required?{intercept_downloads:true}:{}),
    ...(pending ? { popup: pending.id, opener: pending.opener } : {}) });
  check();
  if(required&&result.created&&result.downloads_intercepted!==true&&!result.manual)throw new Error('浏览器未确认首次导航前的下载拦截；请检查已有标签，不要重新打开网址。');
  if (pending) await sdk.call("browser.popup.settle", { id: browser.id, client: clientId, popup: pending.id });
  check();return result;
}
