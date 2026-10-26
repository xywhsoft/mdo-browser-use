/* This function is deliberately self-contained: native hosts embed its source
 * in a browser execution context. Page data never grants host authority. */
export function browserRuntime() {
  const version = 3;
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
        const matched = typeof options._match === "function" ? shown && options._match(element, frame) : Boolean(elementRole);
        if (matched && elements.length < limit) {
          const item = inspect(element, frame);
          if (item.ref) elements.push(item); else incomplete = true;
        } else if (matched) incomplete = true;
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
      let snapshot;
      try { snapshot = scan({ limit: 400, _match: (element, frame) => {
        if (target.frame && frame !== target.frame) return false;
        if (target.name !== undefined && !role(element)) return false;
        if (target.role && role(element) !== target.role) return false;
        if (target.name !== undefined && name(element) !== target.name) return false;
        if (target.label !== undefined && normalize([...element.labels || []].map(label => label.textContent).join(" ")) !== target.label) return false;
        if (target.placeholder !== undefined && element.getAttribute("placeholder") !== target.placeholder) return false;
        if (target.testid !== undefined && element.getAttribute("data-testid") !== target.testid) return false;
        if (target.css && !element.matches(target.css)) return false;
        if (target.text !== undefined && (normalize(element.textContent) !== target.text ||
          [...element.children].some(child => normalize(child.textContent) === target.text))) return false;
        return ["role", "name", "label", "placeholder", "testid", "css", "text"].some(key => target[key] !== undefined);
      } }); } catch { return fail("locator_invalid", "The target contains an invalid CSS selector"); }
      if (snapshot.truncated) return fail("observation_incomplete", "Narrow the search in a fresh observation");
      candidates = snapshot.elements.map(item => refs.get(item.ref));
    }
    candidates = candidates.filter(row => visible(row.element));
    if (!candidates.length) return fail("element_not_found", "No visible element matches the target");
    if (candidates.length > 1) return fail("element_ambiguous", "More than one element matches", {
      candidates: candidates.slice(0, 8).map(row => inspect(row.element, row.frame))
    });
    const row = candidates[0], item = inspect(row.element, row.frame);
    return { ok: true, document: documentId, element: item };
  }
  function deepHit(doc, x, y) {
    let hit = doc.elementFromPoint(x, y);
    for (let depth = 0; depth < 16 && hit?.shadowRoot; depth++) {
      const inner = hit.shadowRoot.elementFromPoint?.(x, y);
      if (!inner || inner === hit) break;
      hit = inner;
    }
    return hit;
  }
  function contains(parent, child) {
    for (let node = child, depth = 0; node && depth < 32; depth++) {
      if (parent === node || parent.contains(node)) return true;
      node = node.getRootNode()?.host;
    }
    return false;
  }
  function pointFor(element, local) {
    let doc = element.ownerDocument, x = local.x, y = local.y;
    if (!contains(element, deepHit(doc, x, y))) return null;
    for (let depth = 0; doc !== document && depth < 16; depth++) {
      const frame = doc.defaultView.frameElement;
      if (!frame?.isConnected) return null;
      for (let ancestor = frame; ancestor; ancestor = ancestor.parentElement) {
        const style = ancestor.ownerDocument.defaultView.getComputedStyle(ancestor);
        if (style.transform !== "none" || (style.zoom && Number(style.zoom) !== 1)) return { unsupported: true };
      }
      const rect = frame.getBoundingClientRect();
      x += rect.x + frame.clientLeft; y += rect.y + frame.clientTop;
      doc = frame.ownerDocument;
      if (deepHit(doc, x, y) !== frame) return null;
    }
    if (doc !== document || x < 0 || y < 0 || x >= innerWidth || y >= innerHeight) return null;
    return { x, y };
  }
  function prepare(options = {}) {
    const found = locate(options); if (!found.ok) return found;
    const row = refs.get(found.element.ref), element = row.element;
    if (found.element.disabled) return fail("element_disabled", "The target is disabled");
    if (["fill", "type"].includes(options.op)) {
      if (!found.element.editable || element.localName === "input" &&
          !["text", "search", "tel", "url", "email", "password", "number"].includes(element.type)) {
        return fail("element_not_editable", "The target does not accept text input");
      }
    }
    if (options.op === "check" && !["checkbox", "radio"].includes(found.element.role)) return fail("element_not_checkable", "The target is not a checkbox or radio input");
    if (options.scroll) element.scrollIntoView({ block: "center", inline: "center", behavior: "instant" });
    const rect = element.getBoundingClientRect();
    const viewport = element.ownerDocument.defaultView;
    const left = Math.max(0, rect.left), top = Math.max(0, rect.top);
    const right = Math.min(viewport.innerWidth, rect.right), bottom = Math.min(viewport.innerHeight, rect.bottom);
    if (right <= left || bottom <= top) return fail("element_outside_viewport", "The target is outside the viewport");
    let point;
    for (const [rx, ry] of [[0.5, 0.5], [0.25, 0.25], [0.75, 0.75]]) {
      point = pointFor(element, { x: left + (right-left)*rx, y: top + (bottom-top)*ry });
      if (point) break;
    }
    if (point?.unsupported) return fail("unsupported_geometry", "Transformed frame coordinates require visual or manual control");
    if (!point) return fail("element_obscured", "Another element covers the target");
    const current = inspect(element, row.frame);
    return { ok: true, document: documentId, element: current, point,
      geometry: [rect.x, rect.y, rect.width, rect.height, point.x, point.y] };
  }
  function verify(options = {}) {
    const found = locate(options); if (!found.ok) return found;
    const element = refs.get(found.element.ref).element;
    let matches = true;
    if (options.op === "fill") matches = (element.isContentEditable ? element.textContent : element.value) === options.text;
    if (options.op === "check") matches = element.checked === options.checked;
    return { ok: true, verified: matches, document: documentId,
      focused: element.getRootNode().activeElement === element,
      sensitive: element.localName === "input" && element.type === "password" };
  }
  function captureArea(options = {}) {
    if (options.document && options.document !== documentId) return fail("stale_document", "Observe the current document before capturing it");
    if (Math.abs((visualViewport?.scale || 1) - 1) > 0.001) return fail("unsupported_geometry", "Pinch-zoomed screenshots require a supported viewport scale");
    const intersect = (a, b) => { const x=Math.max(a.x,b.x),y=Math.max(a.y,b.y);return {x,y,width:Math.max(0,Math.min(a.x+a.width,b.x+b.width)-x),height:Math.max(0,Math.min(a.y+a.height,b.y+b.height)-y)}; };
    const viewport={x:0,y:0,width:innerWidth,height:innerHeight};
    function rootRect(element) {
      let doc=element.ownerDocument,box=element.getBoundingClientRect(),area={x:box.x,y:box.y,width:box.width,height:box.height};
      for(let depth=0;doc!==document&&depth<16;depth++){
        const view=doc.defaultView,frame=view?.frameElement;if(!frame?.isConnected)throw Error("Frame changed during screenshot preparation");
        for(let node=frame;node;node=node.parentElement){const style=node.ownerDocument.defaultView.getComputedStyle(node);if(style.transform!=="none"||(style.zoom&&Number(style.zoom)!==1))throw Error("Transformed frame screenshots require visual/manual control");}
        area=intersect(area,{x:0,y:0,width:view.innerWidth,height:view.innerHeight});
        const parent=frame.getBoundingClientRect();area.x+=parent.x+frame.clientLeft;area.y+=parent.y+frame.clientTop;doc=frame.ownerDocument;
      }
      if(doc!==document)throw Error("Frame nesting exceeds the screenshot budget");
      if(options.mode!=="full_page")area=intersect(area,viewport);
      area.x+=scrollX;area.y+=scrollY;return area;
    }
    let region={x:scrollX,y:scrollY,width:innerWidth,height:innerHeight};
    try {
      if(options.mode==="element"){
        const found=locate({document:documentId,target:options.target});if(!found.ok)return found;
        region=rootRect(refs.get(found.element.ref).element);
      }else if(options.mode==="full_page"){
        if(scrollX<0)return fail("unsupported_geometry", "Full-page capture of a negative horizontal scroll requires a supported layout");
        region={x:0,y:0,width:Math.max(innerWidth,document.documentElement.scrollWidth,document.body?.scrollWidth||0),
          height:Math.max(innerHeight,document.documentElement.scrollHeight,document.body?.scrollHeight||0)};
      }
      region={x:Math.floor(region.x),y:Math.floor(region.y),width:Math.ceil(region.width),height:Math.ceil(region.height)};
      const dpr=devicePixelRatio||1;
      if(region.width<=0||region.height<=0)return fail("element_outside_viewport", "The screenshot target has no visible area");
      if(region.width>32768||region.height>32768||region.width*region.height>16777216||region.width*region.height*dpr*dpr>33554432)
        return fail("capture_area_limit", "The page area exceeds the screenshot raster budget; capture a smaller region");
      const redactions=[];let nodes=0,frames=0;const started=performance.now();
      function mask(element){const area=intersect(rootRect(element),region);if(area.width&&area.height){if(redactions.length>=256)throw Error("Screenshot masking budget exceeded");redactions.push(area);}}
      function walk(root){
        const walker=root.ownerDocument?.createTreeWalker(root,1)||document.createTreeWalker(root,1);
        for(let element=walker.nextNode();element;element=walker.nextNode()){
          if(++nodes>6000||performance.now()-started>100)throw Error("Screenshot masking scan exceeded its budget");
          if(!visible(element))continue;
          const tag=element.localName;
          if(tag==="textarea"||tag==="select"||element.isContentEditable||element.hasAttribute("data-mdo-private")||
            ["textbox","combobox","spinbutton"].includes(element.getAttribute("role"))||
            tag==="input"&&!["button","submit","reset","checkbox","radio","hidden","image"].includes(element.type))mask(element);
          if(element.shadowRoot)walk(element.shadowRoot);
          else if(tag.includes("-"))mask(element); // Closed custom-element internals cannot be inspected here.
          if(["iframe","frame"].includes(tag)){
            if(++frames>32)throw Error("Screenshot frame budget exceeded");let child=null;try{child=element.contentDocument;}catch{}
            if(child)walk(child);else mask(element);
          }
        }
      }
      walk(document);
      return {ok:true,document:documentId,region,redactions,device_pixel_ratio:dpr,redaction:"form-controls-and-opaque-content"};
    } catch(error){return fail("capture_preparation_failed",String(error.message||error).slice(0,256));}
  }
  const runtime = Object.freeze({ version, documentNode: document, document: documentId, observe: scan, locate, prepare, verify, captureArea });
  Object.defineProperty(globalThis, marker, { value: runtime, configurable: true });
  return runtime;
}
