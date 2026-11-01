import { ensureNativeBrowser, createNativeBrowserExecutor } from "./native-view.js";
import { browserScopeKey } from "./browser-ownership.js";
import { createBrowserDownloads } from "./download-ui.js";
import { createBrowserChoosers } from "./chooser-ui.js";
import { createBrowserDialogs } from "./dialog-ui.js";
import { browserLocation, browserPhase, safeBrowserUrl } from "./model.js";
import { element as make, icon, iconButton, popupMenu } from "./controls.js";
import { toast } from "../../utils/dom.js";
import { isRemoteTarget } from "../../api/target.js";

const rect = node => { const r = node.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height), scale: devicePixelRatio || 1 }; };
function assertDisplayUrl(url) {
  if (url && isRemoteTarget() && ["localhost", "127.0.0.1", "[::1]"].includes(new URL(url).hostname))
    throw new Error("此地址指向目标设备的本地服务。请先映射预览服务，或提供可访问的网址。");
}

export async function mountBrowserView(root, { row, sdk, signal, mount, bridge, clientId, shown, alive = () => !signal.aborted,
  canHide = () => false, onBind = () => true, onDispose, onRetired, updateTitle, openExternal, onEnd, addContext }) {
  const id = row.resource.id, scope = browserScopeKey(row);
  const execute = createNativeBrowserExecutor(bridge, id), view = { id, execute };
  let disposed = false, retired = false, brokerInvalid = false, initialized = false;
  const current = () => !disposed && !retired && !brokerInvalid && !signal.aborted && alive();
  const check = () => { if (!current()) throw new DOMException("浏览器所属窗口或设备已改变", "AbortError"); };
  const ready = () => { check(); if (!initialized) throw new DOMException("浏览器正在初始化", "AbortError"); };
  const errorToast = error => { if (current() && error.name !== "AbortError") toast(error.message || String(error), "error"); };
  const uiExecute = async (command, params) => { ready(); const result = await execute(command, params); check(); return result; };
  const call = async (method, params) => { check(); const result = await sdk.call(method, params, { signal }); check(); return result; };
  const owned = (value, attached = true) => {
    if (!value || value.id !== id || browserScopeKey(value) !== scope || attached && value.client !== clientId)
      throw Object.assign(new Error("浏览器归属已改变，请重新打开原标签。"), { code: "browser_owner_changed" });
    return value;
  };
  let browser;
  try {
    browser = owned(await call("browser.get", { id }), false);
    if (browser.client !== clientId) browser = owned(await call("browser.attach", { id, revision: browser.revision, client: clientId }));
    assertDisplayUrl(browser.url); check();
  } catch (error) { if (!current()) return view; throw error; }
  mount.browserId = id;
  let state = { url: browser.url || "about:blank", loading: Boolean(browser.url) }, timer, lastBounds = "", lastReport = "", lastTitle = row.title || "", polling = false, connecting = false;
  const toolbar = make("form", "wb-browser-toolbar"), address = make("input", "wb-address"), region = make("div", "wb-browser-region"), surface = make("div", "wb-browser-surface"), message = make("div", "wb-browser-message"), status = make("span", "wb-browser-status");
  address.setAttribute("aria-label", "搜索或输入网址"); address.placeholder = "搜索或输入网址"; address.spellcheck = false; address.autocomplete = "off";
  address.value = browser.url || ""; status.setAttribute("role", "status");
  const action = (name, label, run) => iconButton(name, label, () => { check(); return run(); }, errorToast);
  const back = action("arrow-left", "后退", () => uiExecute("back")), forward = action("arrow-right", "前进", () => uiExecute("forward"));
  const reload = action("refresh", "刷新", () => state.loading ? uiExecute("stop") : recover());
  const takeControl = async () => { const value = await uiExecute(state.manual ? "release" : "takeover"); state = { ...state, ...value }; paint(); };
  const manual = action("hand", "接管浏览器", takeControl);
  const viewport = make("div", "wb-viewport-toolbar"); viewport.hidden = row.type !== "mdo.preview";
  const size = make("select", "wb-viewport-size"); size.setAttribute("aria-label", "预览宽度");
  for (const [value, label] of [["", "自适应"], ["390", "手机 · 390"], ["768", "平板 · 768"], ["1280", "桌面 · 1280"]]) { const option = make("option", "", label); option.value = value; size.append(option); }
  const resize = () => { if (!current()) return; surface.style.width = size.value ? `${size.value}px` : "100%"; surface.style.maxWidth = "100%"; void syncVisible(shown()); };
  size.onchange = resize;
  viewport.append(size, action("monitor", "横竖屏", () => { const width = surface.getBoundingClientRect().height; surface.style.width = `${Math.round(width)}px`; void syncVisible(shown()); }));
  const preview = action("monitor", "设备预览", () => { viewport.hidden = !viewport.hidden; preview.setAttribute("aria-pressed", String(!viewport.hidden)); });
  preview.setAttribute("aria-pressed", String(!viewport.hidden));
  const more = action("more", "浏览器更多操作", () => {});
  forward.classList.add("wb-browser-forward"); manual.classList.add("wb-browser-manual"); preview.classList.add("wb-browser-preview-toggle");
  const downloadButton=action("download","下载",()=>{downloadButton.setAttribute("aria-expanded",String(downloads.toggle()));});
  downloadButton.classList.add("wb-browser-download-toggle");downloadButton.setAttribute("aria-expanded","false");
  toolbar.append(back, forward, reload, address, downloadButton, manual, preview, more);
  const control = () => { if (current()) { state = { ...state, manual: true }; paint(); } }, changed = () => { void syncVisible(shown()); };
  const dialogs = createBrowserDialogs({ execute:uiExecute, onControl:control, onChange:changed, onError:errorToast });
  const choosers = createBrowserChoosers({execute:uiExecute,onControl:control,onChange:changed,onError:errorToast});
  const downloads=createBrowserDownloads({browser,sdk,execute:uiExecute,signal,alive:current,onChange:changed,
    onBadge:(native,recovery)=>{if(!current())return;const count=native+recovery;downloadButton.dataset.count=String(count);downloadButton.title=count?`下载与待处理记录（${count}）`:"下载";downloadButton.setAttribute("aria-label",downloadButton.title);}});
  region.append(surface, message); root.append(toolbar, dialogs.element, choosers.element, downloads.element, viewport, status, region); root.classList.add("wb-browser-view");
  const openSystem = () => { check(); return openExternal(safeBrowserUrl(state.url === "about:blank" ? browserLocation(address.value) : state.url)); };
  const menu = popupMenu(more, () => [
    { label: "前进", execute: () => uiExecute("forward"), disabled: !state.can_go_forward },
    { label: state.manual ? "交还给 Agent" : "接管浏览器", checked: Boolean(state.manual), execute: takeControl },
    { label: "设备预览", checked: !viewport.hidden, execute: () => preview.click() },
    { label: "在系统浏览器打开", execute: openSystem, disabled: !state.url || state.url === "about:blank" },
    { label: "复制网址", execute: () => { check(); return navigator.clipboard.writeText(state.url); }, disabled: !state.url || state.url === "about:blank" },
    { label: "引用页面文字到对话", execute: async () => {
      const result = await uiExecute("snapshot");
      if (!result.ok) throw new Error(result.error || "读取页面失败");
      addContext({ title: state.title || state.url, content: result.value.text, source: state.url, context: row.context });
    }, disabled: browserPhase(state) !== "ready" },
    { label: "复制诊断信息", execute: () => { check(); return navigator.clipboard.writeText(JSON.stringify({ platform: state.platform, phase: browserPhase(state), loading: state.loading, visible: state.visible, bounds: state.bounds, viewport_attached: state.viewport_attached, viewport_topmost: state.viewport_topmost, controller_bounds: state.controller_bounds, error: state.error, error_code: state.error_code }, null, 2)); } },
    { label: "结束浏览", execute: async () => {
      ready();
      await call("browser.close", { id });
      await uiExecute("close"); await onEnd(id);
    } },
  ], { signal, onError: errorToast });
  const title = make("h3"), detail = make("p"), retry = action("refresh", "重新加载", () => recover()), system = action("globe", "在系统浏览器打开", openSystem);
  message.append(icon("globe"), title, detail, retry, system);
  const paint = (sync = true) => {
    if (!current()) return;
    const phase = browserPhase(state); root.dataset.browserPhase = phase;
    mount.status = { status: phase === "error" ? "error" : phase === "loading" ? "loading" : "ready", message: state.error || "" };
    const empty = phase === "empty", failed = phase === "error";
    message.hidden = !empty && !failed; message.classList.toggle("wb-error", failed);
    title.textContent = failed ? "网页未能打开" : "浏览器";
    detail.textContent = failed ? `${state.error}${state.error_code === undefined ? "" : `（${state.platform || "浏览器"} ${state.error_code}）`}` : "粘贴或输入网址，按回车打开网页。";
    retry.hidden = !failed || !bridge.available(); system.hidden = !failed || !state.url || state.url === "about:blank";
    status.textContent = phase === "loading" ? "正在加载…" : state.manual ? "已由你接管 · Agent 暂停操作" : "";
    status.hidden = !status.textContent;
    if (document.activeElement !== address && !state.initial_navigation_pending) address.value = state.url === "about:blank" || empty ? "" : state.url || address.value;
    back.disabled = !state.can_go_back; forward.disabled = !state.can_go_forward;
    reload.setAttribute("aria-label", state.loading ? "停止加载" : "刷新"); reload.title = state.loading ? "停止加载" : "刷新";
    reload.replaceChildren(icon(state.loading ? "stop" : "refresh"));
    manual.setAttribute("aria-pressed", String(Boolean(state.manual))); manual.title = state.manual ? "交还给 Agent" : "接管浏览器"; manual.setAttribute("aria-label", manual.title);
    if (sync) void syncVisible(shown());
  };
  const syncVisible = value => {
    if (!current() || !initialized) return Promise.resolve();
    const bounds = rect(surface), overlay = [...document.querySelectorAll("dialog[open], [role=menu]:not([hidden])")].some(node => node.getClientRects().length);
    const params = { bounds, visible: value && bounds.width > 0 && bounds.height > 0 && !overlay && browserPhase(state) !== "empty" && !state.error };
    const identity = JSON.stringify(params); if (identity === lastBounds || !bridge.available()) return Promise.resolve(); lastBounds = identity;
    return uiExecute("bounds", params).catch(() => { if (current() && lastBounds === identity) lastBounds = ""; });
  };
  mount.visible = value => {
    if (!current()) { if (value && !disposed && !brokerInvalid && !signal.aborted) onRetired?.(); return Promise.resolve(); }
    return syncVisible(value);
  };
  mount.dispose = () => {
    if (disposed) return;
    const hide = !brokerInvalid && canHide(); disposed = true; onDispose?.();
    clearTimeout(timer); downloads.dispose(); choosers.dispose(); dialogs.dispose(); menu.dispose();
    if (hide && bridge.available()) void execute("bounds", { bounds: rect(surface), visible: false }).catch(() => {});
  };
  mount.observer = new ResizeObserver(() => { void syncVisible(shown()); }); mount.observer.observe(surface);
  const initialize = async (refresh = false) => {
    if (refresh) browser = owned(await call("browser.get", { id }));
    await ensureNativeBrowser({bridge,sdk,browser,clientId,bounds:rect(surface),alive:current,cancelOnDetach:false,
      beforeCreate:()=>{check();if(!onBind(view)){retired=true;check();}}});
    check(); initialized = true; lastBounds = ""; await syncVisible(shown()); check();
  };
  const failure = (error, prefix = "") => {
    if (!current()) return;
    state = { ...state, loading: false, error: `${prefix}${error.message}`, bridge_error: true };
    mount.status = { status: "error", message: state.error };
    // A broker rejection is not permission to hide/recreate a foreign native view.
    paint(error.code !== "browser_owner_changed");
    if (error.code === "browser_owner_changed") { brokerInvalid = true; retry.disabled = true; clearTimeout(timer); }
  };
  const recover = async () => {
    if (connecting || !current() || !initialized) return;
    connecting = true;
    try {
      browser = owned(await call("browser.get", { id }));
      if (state.process_failed || state.bridge_error) { await uiExecute("close"); initialized = false; await initialize(true); }
      if (state.url && state.url !== "about:blank") await uiExecute("navigate", { url: safeBrowserUrl(state.url) });
      else if (address.value.trim()) await uiExecute("navigate", { url: browserLocation(address.value) });
      check();
      state = { ...state, error: "", error_code: undefined, bridge_error: false, process_failed: false, loading: true }; paint(); void poll();
    } catch (error) { failure(error); }
    finally { connecting = false; }
  };
  const poll = async () => {
    clearTimeout(timer);
    if (polling || !current()) return;
    polling = true;
    try {
      state = await uiExecute("state"); await dialogs.refresh(state); check(); await choosers.refresh(state); check(); await downloads.refresh(state); check(); paint();
      const location = /^https?:\/\//.test(state.url || "") ? state.url : "";
      const label = state.title || (location && new URL(location).hostname) || "浏览器";
      if (location && (location !== browser.url || label !== browser.title)) {
        try { browser = owned(await call("browser.update", { id, revision: browser.revision, url: location, title: label.slice(0, 256) })); }
        catch (error) { if (error.status === 409) browser = owned(await call("browser.get", { id })); else throw error; }
      }
      if (lastTitle !== label) { await updateTitle(label.slice(0, 256)); check(); lastTitle = label; }
      const report = { state: browserPhase(state), url: location.slice(0, 1024), title: label.slice(0, 256), loading: Boolean(state.loading), error: (state.error || "").slice(0, 512) };
      const identity = JSON.stringify(report);
      if (identity !== lastReport) { await call("browser.report", { id, client: clientId, value: report }); lastReport = identity; }
      mount.status = { status: state.error ? "error" : state.loading ? "loading" : "ready", message: state.error || "" };
    } catch (error) {
      failure(error, "浏览器状态暂不可用：");
    } finally {
      polling = false;
      if (current()) timer = setTimeout(() => { if (!current()) return; if (!root.hidden && !document.hidden) void poll(); else timer = setTimeout(poll, 1500); }, state.loading ? 400 : 1500);
    }
  };
  toolbar.onsubmit = event => {
    event.preventDefault(); if (event.isComposing || !current() || !initialized) return;
    void (async () => {
      const url = browserLocation(address.value); assertDisplayUrl(url);
      await uiExecute("navigate", { url }); state = { ...state, url, error: "", loading: true }; paint(); void poll();
    })().catch(errorToast);
  };
  paint();
  if (!bridge.available()) { state = { ...state, error: "完整浏览需要在 mdo 客户端打开，当前窗口没有原生浏览器后端。", loading: false }; paint(); return view; }
  try { await initialize(); if (current()) void poll(); }
  catch (error) { if (current()) throw error; }
  return view;
}
