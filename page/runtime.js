/* This function is deliberately self-contained: native hosts embed its source
 * in a browser execution context. Page data never grants host authority. */
export function browserRuntime() {
  const version = 1;
  const marker = "__mdo_browser_use_runtime_v1";
  const previous = Object.getOwnPropertyDescriptor(globalThis, marker)?.value;
  if (previous?.version === version && previous.documentNode === document) return previous;
  const documentId = `d-${crypto.randomUUID()}`;
  const refs = new Map(), identities = new WeakMap(), frameIds = new WeakMap();
  let nextRef = 1, nextFrame = 1;
  const maxRefs = 2000, maxFrames = 32;
  const normalize = text => String(text || "").replace(/\s+/g, " ").trim();
  const fail = (code, message, details = {}) => ({ ok: false, error: { code, message, ...details } });
  function clip(text, maximum) {
    const bytes = new TextEncoder().encode(text);
    if (bytes.length <= maximum) return { text, truncated: false };
    let end = maximum;
    while (end > 0) {
      try { return { text: new TextDecoder("utf-8", { fatal: true }).decode(bytes.subarray(0, end)), truncated: true }; }
      catch { end--; }
    }
    return { text: "", truncated: true };
  }
  function visible(element) {
    if (!element.isConnected || element.closest("[hidden],[inert],[aria-hidden='true']")) return false;
    const style = element.ownerDocument.defaultView.getComputedStyle(element);
    return style.display !== "none" && style.visibility !== "hidden" && style.visibility !== "collapse"
      && Number(style.opacity) !== 0 && element.getClientRects().length > 0;
  }
  function role(element) {
    const explicit = normalize(element.getAttribute("role")).split(" ")[0];
    if (explicit) return explicit;
    const tag = element.localName;
    if (tag === "a" && element.hasAttribute("href")) return "link";
    if (tag === "button" || tag === "summary") return "button";
    if (tag === "textarea" || element.isContentEditable) return "textbox";
    if (tag === "select") return element.multiple ? "listbox" : "combobox";
    if (tag === "option") return "option";
    if (tag === "input") {
      const type = element.type;
      if (type === "hidden") return "";
      if (["checkbox", "radio"].includes(type)) return type;
      if (["submit", "button", "reset", "image"].includes(type)) return "button";
      if (type === "range") return "slider";
      if (type === "number") return "spinbutton";
      if (type === "file") return "fileinput";
      return "textbox";
    }
    if (/^h[1-6]$/.test(tag)) return "heading";
    return "";
  }
  function name(element) {
    const labelled = normalize(element.getAttribute("aria-labelledby"));
    if (labelled) {
      const root = element.getRootNode();
      const text = labelled.split(" ").slice(0, 16)
        .map(id => root.getElementById?.(id)?.textContent || "").join(" ");
      if (normalize(text)) return normalize(text).slice(0, 512);
    }
    const aria = element.getAttribute("aria-label");
    if (aria) return normalize(aria).slice(0, 512);
    if (element.labels?.length) return normalize([...element.labels].map(label => label.textContent).join(" ")).slice(0, 512);
    if (element.localName === "input") {
      if (["submit", "reset", "button"].includes(element.type)) return normalize(element.value).slice(0, 512);
      return normalize(element.getAttribute("placeholder") || element.getAttribute("title") || "").slice(0, 512);
    }
    return normalize(element.getAttribute("alt") || element.textContent || element.getAttribute("title") || "").slice(0, 512);
  }
  function reference(element, frame) {
    let id = identities.get(element);
    if (id && refs.has(id)) return id;
    if (refs.size >= maxRefs) {
      for (const [key, row] of refs) if (!row.element.isConnected) refs.delete(key);
      if (refs.size >= maxRefs) return null;
    }
    id = `e${nextRef++}`;
    refs.set(id, { element, frame, documentNode: element.ownerDocument });
    identities.set(element, id);
    return id;
  }
  function frameIdentity(node) {
    let id = frameIds.get(node);
    if (!id) { id = `f${nextFrame++}`; frameIds.set(node, id); }
    return id;
  }
  function inspect(element, frame) {
    const rect = element.getBoundingClientRect();
    const item = {
      ref: reference(element, frame), frame, role: role(element), name: name(element),
      disabled: element.matches(":disabled") || element.getAttribute("aria-disabled") === "true",
      editable: (element.isContentEditable || ["input", "textarea"].includes(element.localName))
        && !element.readOnly && !element.disabled,
      bounds: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
      coordinate_space: "frame-css"
    };
    if (element.localName === "input") item.input_type = element.type;
    if (element.matches("input[type=checkbox],input[type=radio]")) item.checked = element.checked;
    if (element.localName === "a") {
      try { const url = new URL(element.href); url.username = ""; url.password = ""; item.url = url.href.slice(0, 2048); }
      catch { /* Invalid links are still locatable, but have no usable URL. */ }
    }
    // Input values are intentionally omitted, including password field values.
    return item;
  }
  function scan(options = {}) {
    const limit = Math.min(400, Math.max(1, Number(options.limit) || 200));
    const frames = [{ id: "f0", url: location.href, parent: null, dom: true }];
    const elements = [], text = [];
    let visited = 0, incomplete = false;
    const started = performance.now();
    function walk(root, frame, includeText) {
      const doc = root.ownerDocument || root;
      const walker = doc.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
      let element;
      while ((element = walker.nextNode())) {
        if (++visited > 6000 || performance.now() - started > 100) { incomplete = true; return; }
        if (["script", "style", "noscript", "template"].includes(element.localName)) continue;
        const shown = visible(element);
        if (shown && includeText && !element.children.length && !["input", "textarea", "select", "option"].includes(element.localName)) {
          const value = normalize(element.textContent); if (value) text.push(value.slice(0, 2048));
        }
        const elementRole = shown ? role(element) : "";
        if (elementRole && elements.length < limit) {
          const item = inspect(element, frame);
          if (item.ref) elements.push(item); else incomplete = true;
        } else if (elementRole) incomplete = true;
        if (shown && element.shadowRoot) walk(element.shadowRoot, frame, includeText);
        if (shown && ["iframe", "frame"].includes(element.localName)) {
          if (frames.length >= maxFrames) { incomplete = true; continue; }
          const id = frameIdentity(element);
          let child = null;
          try { child = element.contentDocument; } catch { /* Cross-origin frame. */ }
          const entry = { id, parent: frame, url: element.getAttribute("src") || "about:srcdoc", dom: Boolean(child) };
          frames.push(entry);
          if (child) walk(child, id, includeText);
          else entry.reason = "cross_origin_or_unavailable";
        }
      }
    }
    walk(document, "f0", true);
    const readable = clip(text.join("\n"), Math.min(32768, Math.max(256, Number(options.max_bytes) || 16384)));
    return {
      ok: true, document: documentId, url: location.href, title: document.title,
      ready: document.readyState, text: readable.text, elements, frames,
      truncated: incomplete || readable.truncated, visited,
      semantics: "dom-subset-v1",
      viewport: { width: innerWidth, height: innerHeight, scroll_x: scrollX, scroll_y: scrollY,
                  scale: visualViewport?.scale || 1, device_pixel_ratio: devicePixelRatio }
    };
  }
  function locate(options = {}) {
    if (options.document !== documentId) return fail("stale_document", "Observe the current document before acting");
    const target = options.target || {};
    let candidates = [];
    if (target.ref) {
      const row = refs.get(target.ref);
      if (!row || !row.element.isConnected || row.element.ownerDocument !== row.documentNode) {
        return fail("element_detached", "The referenced element is no longer present");
      }
      try {
        const view = row.documentNode.defaultView;
        if (!view || view.document !== row.documentNode ||
            (row.frame !== "f0" && view.frameElement?.contentDocument !== row.documentNode)) {
          return fail("stale_frame", "The referenced frame has navigated; observe it again");
        }
      } catch { return fail("stale_frame", "The referenced frame is no longer accessible"); }
      candidates = [row];
    } else {
      const snapshot = scan({ limit: 400 });
      if (snapshot.truncated) return fail("observation_incomplete", "Narrow the search in a fresh observation");
      candidates = snapshot.elements.filter(item => {
        if (target.frame && item.frame !== target.frame) return false;
        if (target.role && item.role !== target.role) return false;
        if (target.name !== undefined && item.name !== target.name) return false;
        return Boolean(target.role || target.name !== undefined);
      }).map(item => refs.get(item.ref));
    }
    candidates = candidates.filter(row => visible(row.element));
    if (!candidates.length) return fail("element_not_found", "No visible element matches the target");
    if (candidates.length > 1) return fail("element_ambiguous", "More than one element matches", {
      candidates: candidates.slice(0, 8).map(row => inspect(row.element, row.frame))
    });
    const row = candidates[0], item = inspect(row.element, row.frame);
    return { ok: true, document: documentId, element: item };
  }
  const runtime = Object.freeze({ version, documentNode: document, document: documentId, observe: scan, locate });
  Object.defineProperty(globalThis, marker, { value: runtime, configurable: true });
  return runtime;
}
