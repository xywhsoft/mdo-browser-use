import { browserRuntime } from "./automation-runtime.js";
import { prepareScreenshot } from "./capture-image.js";
import { visualSnapshots, hashVisualRaster } from "./visual-snapshot.js";
import { executeVisualAction } from "./visual-dispatch.js";
import { executeFileUpload } from "./upload-dispatch.js";
import { executeBrowserDownload } from "./download-dispatch.js";
import { executeDownloadSave, downloadSaveState } from "./download-save.js";

const writers = new Set();
let capturing = false;
const sameGeometry = (a, b) => a?.element.ref === b?.element.ref &&
  a.geometry?.every((number, index) => Math.abs(number - b.geometry[index]) <= 0.5);
const captureChanges=(before,after)=>{
  try{
    if(typeof before!=='string'||typeof after!=='string'||before.length>65536||after.length>65536)return'context';
    const a=JSON.parse(before),b=JSON.parse(after);if(!Array.isArray(a)||!Array.isArray(b)||a.length>32||b.length>32)return'context';
    const old=new Map(a.map(row=>[row[0],row])),changes=new Set(),fields=['frame-tree','frame-document','frame-tree','frame-readiness','frame-readiness','mask-coverage','isolated-document','scroll','viewport','iframe-layout'];
    if(a.length!==b.length)changes.add('frame-tree');
    for(const row of b){const previous=old.get(row[0]);if(!previous){changes.add('frame-tree');continue;}
      for(let index=1;index<10;index++)if(JSON.stringify(previous[index])!==JSON.stringify(row[index]))changes.add(fields[index]);}
    return[...changes].sort().join(', ')||'context';
  }catch{return'context';}
};

// Frozen workbench instances supply view/action. No selected-chat identity is
// substituted, and this helper never exposes arbitrary script input to a model.
export async function executeBrowserAutomation({ command, params, view, action, api, browser, alive = () => true, pageDriver = null }) {
  if (!["observe", "act", "navigate_to", "wait_for", "dialog", "chooser", "capture", "upload", "download", "save_download"].includes(command)) return { handled: false };
  const writing = command === "save_download" || command === "act" || command === "navigate_to" || command === "dialog" && params.action === "respond" || command === "chooser" && params.action === "cancel" || command === "upload" && params.action !== "status" || command === "download" && !["list","get","wait"].includes(params.action);
  const writer = browser || view;
  const timeout = Math.min(60000, Math.max(1, params.timeout_ms || 10000));
  const deadline = performance.now() + timeout;
  const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
  let dispatched = false,dragRequested = false,downloadCheckpointUntil=0,checkpointNavigation,inputFrameChain=null,inputFrameSnapshot=null,inputFocus=null,inputTarget=null;
  const framePending=code=>['frame_not_ready','frame_tree_changed'].includes(code);
  const canOpenDownload=command==='act'&&['click','double_click','right_click','middle_click','fill','type','key','drag'].includes(params.op);
  const fail = (code, message) => ({ handled: true, result: { ok: false,
    error: { code, message, effect_may_have_occurred: dispatched }, action: action.id } });
  const exclusive = writing || command === "capture";
  const captures=command==='capture'||command==='act'&&Boolean(params.target?.image);
  if (exclusive && (writers.has(writer) || downloadSaveState(writer)) || captures && capturing) return fail("browser_busy", "Another action or screenshot is still completing; inspect its receipt before retrying");
  if (exclusive) writers.add(writer);
  if (captures) capturing = true;
  let lastReceipt = -Infinity, navigation;
  const dialogBoundary = async () => ({ handled: true, result: { ok: true,
    outcome: "dialog_open", completed: false, dispatched, action: action.id,
    dialogs: (await view.execute("dialog", { action: "list", agent: true })).dialogs || [] } });
  const popupBoundary = () => ({ handled: true, result: { ok: true,
    outcome: "popup_open", completed: false, dispatched, action: action.id } });
  const chooserBoundary = async () => ({handled:true,result:{ok:true,outcome:"file_chooser_open",completed:false,dispatched,action:action.id,
    choosers:(await view.execute("chooser",{action:"list",agent:true})).choosers||[]}});
  const downloadBoundary = (downloads,closed=false) => ({handled:true,result:{ok:true,outcome:closed?'download_retained':"download_pending",completed:false,dispatched,action:action.id,downloads,...(closed?{page_closed:true}:{})}});
  const armDownloads = async () => {
    const checked=await guard(true);if(checked.handled)return checked;
    if(!checked.state.download_capability)return fail('unsupported_capability','This host cannot intercept native downloads safely; no browser input was sent');
    if(!checked.state.downloads_armed){
      const armed=await view.execute('downloads',{action:'arm',agent:true});
      if(!armed.ok||!armed.armed)return fail('download_unavailable','Native response interception was not confirmed; no browser input was sent');
    }
    return null;
  };
  const read = async (method, options) => {
    if(pageDriver)return pageDriver.read(method,{...options,timeout_ms:Math.max(1,Math.ceil(deadline-performance.now()))},{checkpoint:async()=>{
      const checked=await guard(true);if(checked.handled)throw Object.assign(new Error('The browser operation reached a checkpoint'),{browserBoundary:checked});
    }});
    if(method==='chooserInfo')return view.execute('chooser',{action:'target',chooser:options.chooser,document:options.document,agent:true});
    const result = await view.execute("automation", { agent: true,
      script: `(${browserRuntime.toString()})().${method}(${JSON.stringify(options)})` });
    if (!result.ok || typeof result.value?.ok !== "boolean") throw Error("The browser returned an invalid automation result");
    if (result.value.blocked_by_dialog) throw Object.assign(new Error("A JavaScript dialog interrupted page reading"), { dialog: true });
    if (result.value.blocked_by_popup) throw Object.assign(new Error("A new window interrupted page reading"), { popup: true });
    return result.value;
  };
  const guard = async (force = false) => {
    let state;
    for (;;) {
      if (!alive()) return fail("context_changed", "The displaying device or window changed");
      if (performance.now() >= deadline) return fail("timed_out", "The browser action deadline expired");
      if (force || performance.now() - lastReceipt > 200) {
        const receipt = await api.call("ui.result", { id: action.id });
        lastReceipt = performance.now();
        if (receipt.state === "cancelled") return fail("cancelled", "The originating browser call was cancelled");
      }
      const snapshot = await view.execute("state");
      if (!alive()) return fail("context_changed", "The displaying device or window changed");
      if (performance.now() >= deadline) return fail("timed_out", "The browser action deadline expired");
      state = { ...snapshot, loading: snapshot.initial_navigation_pending && typeof snapshot.loading === "boolean" ? true : snapshot.loading };
      if(state.creating!==true)break;
      if(state.error||state.process_failed)return fail('browser_unavailable',state.error||'The browser process stopped during creation');
      if(state.manual)return fail('manual_control','The user currently controls the browser');
      if(dispatched||navigation!==undefined)return fail('stale_document','The native browser was replaced; inspect its new document before further work');
      await pause(Math.min(20,Math.max(0,deadline-performance.now())));force=false;
    }
    if(command==='upload'&&params.action==='status')return{state};
    if(command==='download'||command==='save_download'){
      if(state.manual&&writing)return fail('manual_control','The user currently controls the browser');
      return{state};
    }
    if(state.close_requested){
      if(state.download_capability){
        const downloads=(await view.execute('downloads',{action:'list',agent:true})).downloads||[];
        if(downloads.length)return downloadBoundary(downloads,true);
      }
      return {handled:true,result:{ok:true,outcome:'closed',completed:false,dispatched,action:action.id}};
    }
    if(state.downloads_armed&&['act','navigate_to','wait_for'].includes(command)){
      const downloads=(await view.execute('downloads',{action:'list',agent:true})).downloads||[];
      if(downloads.some(row=>row.state==='pending'))return downloadBoundary(downloads);
    }
    if (state.closed_by_page) return { handled: true, result: { ok: true, outcome: "closed", completed: false, dispatched, action: action.id } };
    // A submitted response can start navigation before DownloadStarting/its
    // scoped identity callback. Keep the original navigation until it settles,
    // then drain one bounded event turn. The caller's deadline/cancel still wins;
    // never evaluate the changed document or send another input in between.
    if(dispatched&&canOpenDownload&&!state.manual&&!state.process_failed&&!state.dialog_open&&!state.popup_open&&
       (state.error||navigation!==undefined&&state.navigation!==navigation)){
      if(checkpointNavigation!==state.navigation||state.loading){
        checkpointNavigation=state.navigation;downloadCheckpointUntil=Math.min(deadline,performance.now()+1100);
      }
      if(performance.now()<downloadCheckpointUntil){await pause(20);return guard(true);}
    }
    if (state.error && !["navigate_to", "dialog"].includes(command) || state.process_failed) return fail("browser_unavailable", state.error || "The browser process stopped");
    if (state.manual && !(["dialog","chooser"].includes(command) && params.action === "list")) return fail("manual_control", "The user currently controls the browser");
    if (state.drag_cleanup_failed && (writing || command === "capture")) return fail("drag_cleanup_failed", "Native drag cleanup was not confirmed; inspect and end this browser before further input");
    if (state.capture_cleanup_failed && (writing || command === 'capture')) return fail('capture_cleanup_failed','Native screenshot cleanup was not confirmed; end this browser before further input');
    if (state.dialog_cleanup_failed && (writing || command === 'capture') && !(command==='upload'&&params.action==='release'))
      return fail('dialog_cleanup_failed','Native dialog cleanup was not confirmed; end this browser before further input');
    if (state.capture_busy && command==='act') return fail('browser_busy','The original native screenshot is still settling; wait for its cleanup before sending input');
    if (command === "dialog") return state.dialog_capability ? { state } : fail("unsupported_capability", "This browser backend cannot inspect native dialogs");
    if (command === "chooser") {
      if(!state.chooser_capability)return fail("unsupported_capability","This browser backend cannot inspect native file pickers");
      if(params.action==='cancel'&&state.dialog_open)return dialogBoundary();
      if(params.action==='cancel'&&state.popup_open)return popupBoundary();
      return {state};
    }
    if (typeof state.loading !== "boolean" || !state.automation) {
      const reason=state.browser_use?.reason;
      return fail("unsupported_capability",reason==='isolated_world_unsupported'?"This device's installed WebView lacks isolated execution worlds; Browser Use automation is unavailable. Use manual browsing; repeating the tool will not add this capability.":
        reason==='android_adapter_incomplete'?"This Android client has not completed the Browser Use native adapter. Use manual browsing; repeating the tool will not add this capability.":"This host does not provide isolated Browser Use execution and readiness");
    }
    if (state.dialog_open) return dialogBoundary();
    if (state.popup_open) return popupBoundary();
    if (state.chooser_cleanup_failed && writing && !(command==='upload'&&params.action==='release')) return fail("chooser_cleanup_failed","File-picker cleanup was not confirmed; end this browser before further input");
    if (state.chooser_open && ["act","navigate_to","wait_for"].includes(command) || state.chooser_open && command==='upload' && params.action!=='release'&&!params.chooser) return chooserBoundary();
    if (navigation !== undefined && state.navigation !== navigation) return fail("stale_document", "The document changed; inspect it before another input");
    return { state };
  };
  const send = async parameters => {
    let stopped = await guard(true); if (stopped.handled) return stopped;
    if(inputFrameSnapshot&&stopped.state.input_busy)return fail('browser_busy','Native input started after visual validation; request a fresh image before another attempt');
    while (stopped.state.input_busy) { await pause(20); stopped = await guard(true); if (stopped.handled) return stopped; }
    const previouslyDispatched=dispatched;
    dispatched = true;
    if(parameters.kind==='focus'&&canOpenDownload)downloadCheckpointUntil=Math.min(deadline,performance.now()+1100);
    const delivered = await view.execute("input", { ...parameters,...(inputTarget||{}),...(inputFrameChain?{frame_chain:inputFrameChain}:{}),
      ...(inputFocus&&['key','text'].includes(parameters.kind)?inputFocus:{}),
      ...(inputFrameSnapshot?{frame_snapshot:inputFrameSnapshot}:{}),agent: true, navigation });
    const code=delivered?.error?.code;
    if(delivered?.accepted===false&&typeof code==='string'&&/^[a-z][a-z0-9_]{0,63}$/.test(code)&&
       typeof delivered.effect_may_have_occurred==='boolean'&&typeof delivered.cleanup_confirmed==='boolean'&&
       Number.isInteger(delivered.delivered_steps)&&delivered.delivered_steps>=0&&delivered.delivered_steps<=2147483647&&
       (delivered.effect_may_have_occurred||delivered.delivered_steps===0)){
      // Only an explicit, internally consistent native receipt can clear this
      // step's uncertainty. Never clear effects from an earlier focus/key step,
      // or infer safe retry from missing/contradictory/transport-failed replies.
      dispatched=previouslyDispatched||delivered.effect_may_have_occurred||!delivered.cleanup_confirmed;
      const failure=fail(code,!delivered.cleanup_confirmed?
        'Native input cleanup was not confirmed; end this browser before further input':dispatched?
        'Native input did not complete; inspect the page for effects before repeating the action':
        'The native guard refused input before dispatch; inspect the current document before another action');
      failure.result.error.native_input={kind:parameters.kind,effect_may_have_occurred:delivered.effect_may_have_occurred,
        cleanup_confirmed:delivered.cleanup_confirmed,delivered_steps:delivered.delivered_steps};
      return failure;
    }
    if(parameters.kind==='focus'&&delivered?.accepted===true){
      if(delivered.dialog_open)return dialogBoundary();
      if(delivered.popup_open)return popupBoundary();
    }
    if (delivered?.accepted!==true || (parameters.kind==='focus'?delivered.target_focused!==true:delivered.trusted_input!==true)) return fail("action_uncertain", "Native input or target focus was not confirmed; inspect the page before repeating it");
    if(canOpenDownload)downloadCheckpointUntil=Math.min(deadline,performance.now()+1100);
    if (delivered.dialog_open) return dialogBoundary();
    if (delivered.popup_open) return popupBoundary();
    if (delivered.file_chooser_open) return chooserBoundary();
    if(delivered.download_open){
      const until=Math.min(deadline,performance.now()+1100);
      do{const checked=await guard(true);if(checked.handled)return checked;await pause(20);}while(performance.now()<until);
      return fail('download_identity_unavailable','The native download started without a retained response identity; do not repeat the input');
    }
    const checked=await guard(true);if(checked.handled)return checked;
    return null;
  };
  const pinScrollPoint=async point=>{
    const hit=await read('scrollPoint',{document:params.document,point});
    if(!hit.ok)return{handled:true,result:hit};
    const pin=hit.native_target,chain=hit.frame_chain;
    if(!/^d-[a-f0-9-]{36}$/i.test(pin?.document)||pin.document!==params.document||!/^e[1-9][0-9]{0,12}$/.test(pin?.ref)||
       !Array.isArray(chain)||chain.length!==1||chain[0]?.frame!=='f0'||!/^fd-[a-f0-9-]{36}$/i.test(chain[0]?.document))
      return fail('frame_response_invalid','The original Android scroll point has no complete document/target pin');
    inputFrameChain=chain;inputTarget={input_document:pin.document,input_ref:pin.ref};return null;
  };
  const sendDrag=async parameters=>{
    const before=await guard(true);if(before.handled)return before;
    if(before.state.input_busy||before.state.capture_busy)return fail('browser_busy','Native input or capture started before dragging');
    let settled=false,native,failure;dispatched=true;dragRequested=true;
    const pending=view.execute('drag',{agent:true,navigation,drag_key:action.id,...parameters,
      timeout_ms:Math.max(1,Math.ceil(deadline-performance.now()))})
      .then(value=>{native=value;settled=true;},error=>{failure=error;settled=true;});
    while(!settled){
      await Promise.race([pending,pause(40)]);const boundary=await guard(true);
      if(boundary.handled){try{await view.execute('drag_cancel',{drag_key:action.id});}catch{}return boundary;}
    }
    if(failure)return fail('action_uncertain',String(failure.message||failure).slice(0,512));
    if(!native.accepted||!native.trusted_input){if(native.dispatched===false)dispatched=false;
      return fail(dispatched?'action_uncertain':'drag_rejected',String(native.error||'Native dragging was not confirmed').slice(0,512));}
    if(canOpenDownload)downloadCheckpointUntil=Math.min(deadline,performance.now()+1100);
    const boundary=await guard(true);if(boundary.handled)return boundary;
    return{drag_kind:native.drag_kind};
  };
  try {
    if(command==='save_download')return await executeDownloadSave({browser:writer,params,view,api,action,guard,deadline,setDispatched:value=>{dispatched=value;},fail});
    if(command==='download')return await executeBrowserDownload({params,view,guard,deadline,setDispatched:value=>{dispatched=value;},fail});
    if(command==='upload')return await executeFileUpload({params,view,api,action,guard,read,deadline,
      setDispatched:value=>{dispatched=value;},fail});
    if (command === "capture") {
      let checked;
      do {
        checked = await guard();if (checked.handled) return checked;
        if (!checked.state.capture_capability) return fail("unsupported_capability", "This browser backend does not provide bounded screenshots");
        if (!checked.state.loading && !checked.state.capture_busy) break;
        await pause(50);
      } while (performance.now() < deadline);
      checked = await guard(true);if (checked.handled) return checked;
      navigation = checked.state.navigation;
      const mode = params.mode || (params.target ? "element" : "viewport");
      let options = { mode, document: params.document, target: params.target };
      // A newly revealed native pane can finish resizing after page readiness.
      // Only read-only viewport settling may retry, within the original receipt,
      // deadline and document. Never reuse or publish an invalidated capture.
      for(let attempt=0;attempt<3;attempt++){
        if(attempt){checked=await guard(true);if(checked.handled)return checked;}
        const area = await read("captureArea",options);if (!area.ok) return { handled:true,result:area };
        if(options.document&&area.document!==options.document)return fail('stale_document','The original screenshot document changed while settling');
        options={...options,document:area.document};
        checked = await guard(true);if (checked.handled) return checked;
        const dpr=area.device_pixel_ratio||1;
        const scale = Math.min(1,(params.max_width||1280)/(area.region.width*dpr),(params.max_height||960)/(area.region.height*dpr));
        let settled = false,native,failure;
        const pending = view.execute("capture",{agent:true,navigation,capture_key:action.id,clip:{...area.region,scale},
          ...(area.native_capture?{capture_context:area.native_capture}:{}),
          // Source pixels must remain lossless until privacy masking. The
          // caller's output codec/byte limit is applied by prepareScreenshot.
          format:'png',max_bytes:262144,timeout_ms:Math.max(1,Math.ceil(deadline-performance.now()))})
          .then(value=>{native=value;settled=true;},error=>{failure=error;settled=true;});
        while (!settled) {
          await Promise.race([pending,pause(50)]);checked = await guard();
          if (checked.handled) {
            try { await view.execute("capture_cancel",{capture_key:action.id}); } catch {}
            return checked;
          }
        }
        if (failure) return fail("capture_failed",String(failure.message||failure).slice(0,512));
        if(area.native_capture&&(native?.native_document!==area.native_capture.native_document||native?.navigation!==area.native_capture.navigation||
           native?.document!==area.document||native?.capture_key!==action.id))
          return fail('capture_changed','The native screenshot reply does not match its original document and request');
        if(area.native_capture&&native.ok!==true){
          const code=native.ok===false&&/^[a-z][a-z0-9_]{0,63}$/.test(native.error?.code||'')?native.error.code:'capture_failed';
          return fail(code,code==='capture_cleanup_failed'?'Native screenshot cleanup was not confirmed; end this browser before another capture':
            'The original native screenshot could not complete; inspect the current browser before another capture');
        }
        if(native?.format!==undefined&&native.format!=='png')
          return fail('capture_source_invalid','The browser did not return the requested lossless PNG source; inspect or update the host before retrying');
        native={...native,format:'png'};
        const after = await read("captureArea",{...options,document:area.document});
        if (!after.ok) return {handled:true,result:after};
        if(area.frame_stamp!==after.frame_stamp){
          const changes=captureChanges(area.frame_stamp,after.frame_stamp);
          if(changes==='viewport'&&attempt<2&&area.document===after.document&&area.device_pixel_ratio===after.device_pixel_ratio){
            native=null;await pause(60);continue;
          }
          return fail('capture_changed',`Screenshot validation changed (${changes}); request a fresh image`);
        }
        if (["x","y","width","height"].some(key=>Math.abs(area.region[key]-after.region[key])>0.5)) return fail("capture_changed", "The screenshot region changed during capture; request a fresh image");
        let raster;
        const image = await prepareScreenshot(native,area,after,params,pageDriver&&!area.native_capture?{raster:async canvas=>{raster=await hashVisualRaster(canvas);}}:{});
        checked = await guard(true);if (checked.handled) return checked;
        const visual=raster?visualSnapshots.issue(writer,{document:area.document,navigation,frame_stamp:area.frame_stamp,
          region:area.region,options,redactions:[...area.redactions,...after.redactions],device_pixel_ratio:area.device_pixel_ratio,
          capture:{format:params.format||'png',max_width:params.max_width||1280,max_height:params.max_height||960,max_bytes:params.max_bytes||131072}},raster):null;
        return {handled:true,result:{ok:true,document:area.document,mode,region:area.region,redaction:area.redaction,...image,
          ...(area.native_capture?{frame_scope:area.frame_scope,limitations:area.limitations}:{}),
          ...(visual?{visual_reference:visual}:{})}};
      }
      return fail('capture_changed','The screenshot viewport did not settle within its bounded attempts');
    }
    if (command === "chooser") {
      const checked=await guard(true);if(checked.handled)return checked;
      if(params.action==='cancel')dispatched=true;
      const result=await view.execute('chooser',{...params,agent:true});
      return {handled:true,result:{...result,action:action.id}};
    }
    if (command === "dialog") {
      const checked = await guard(true); if (checked.handled) return checked;
      if (params.action === "respond") dispatched = true;
      return { handled: true, result: await view.execute("dialog", { ...params, has_text: Object.hasOwn(params, "text"), agent: true }) };
    }
    if (command === "navigate_to") {
      const boundary=await armDownloads();if(boundary)return boundary;
      const checked = await guard(true); if (checked.handled) return checked;
      const oldNavigation = checked.state.navigation;
      if (params.action === "back" && !checked.state.can_go_back || params.action === "forward" && !checked.state.can_go_forward) return fail("history_unavailable", "There is no matching browser history entry");
      dispatched = true;
      const accepted = await view.execute(params.action === "goto" ? "navigate" : params.action, { agent: true, url: params.url });
      if (!accepted.accepted) return fail("navigation_uncertain", "Navigation was not confirmed; inspect the current tab before repeating it");
      while (performance.now() < deadline) {
        const current = await guard(); if (current.handled) return current;
        if (!current.state.loading && current.state.navigation !== oldNavigation) {
          if (current.state.error) return fail("navigation_failed", current.state.error);
          const observation = await read("observe", { limit: 100, max_bytes: 4096 });
          if (observation.ok && observation.ready === "complete") return { handled: true, result: { ok: true, action: action.id, url: current.state.url, observation } };
        }
        await pause(50);
      }
      return fail("timed_out", "Navigation did not complete before its deadline; inspect the tab before retrying");
    }
    if (command === "wait_for") {
      while (performance.now() < deadline) {
        const current = await guard(); if (current.handled) return current;
        if (!current.state.loading) {
          const observation = await read("observe", { limit: 20, max_bytes: params.until === "text" ? 32768 : 1024 });
          if(!observation.ok){if(framePending(observation.error?.code)){await pause(60);continue;}return{handled:true,result:observation};}
          if (params.document && params.document !== observation.document) return fail("stale_document", "The document changed while waiting");
          if (params.until === "ready" && observation.ready === "complete" ||
              params.until === "text" && observation.text.includes(params.text)) return { handled: true, result: { ok: true, matched: true, observation } };
          if (params.target) {
            const located = await read("locate", { document: observation.document, target: params.target });
            if(!located.ok&&framePending(located.error?.code)){await pause(60);continue;}
            const absent = ["element_not_found", "element_detached"].includes(located.error?.code);
            if (params.until === "hidden" && absent || params.until === "visible" && located.ok ||
                params.until === "enabled" && located.ok && !located.element.disabled) return { handled: true, result: { ok: true, matched: true, document: observation.document, ...(located.ok ? { element: located.element } : {}) } };
            if (!located.ok && !absent) return { handled: true, result: located };
          }
        }
        await pause(60);
      }
      return fail("timed_out", "The requested page condition was not met before its deadline");
    }
    while (performance.now() < deadline) {
      const checked = await guard(); if (checked.handled) return checked;
      const { state } = checked;
      if (!state.loading) {
        if (command === "act") {
          if(state.platform==='android-webview'&&(params.op==='hover'&&state.hover_input!==true||params.op==='scroll'&&state.wheel_input!==true))
            return fail('unsupported_capability','This Android adapter does not provide the requested native pointer operation; no browser input was sent');
          if(!params.target?.image&&['fill','type','key'].includes(params.op)&&(!pageDriver||state.frame_target_keyboard!==true))
            return fail('unsupported_capability','This host cannot safely focus and fence an exact keyboard target; update the host or use manual control');
          if (params.op === "drag" && !state.drag_capability) return fail("unsupported_capability", "This browser backend cannot deliver bounded native dragging");
          if (["double_click", "right_click", "middle_click"].includes(params.op) && !state.advanced_mouse)
            return fail("unsupported_capability", "This browser backend cannot deliver the requested mouse gesture");
          navigation = state.navigation; break;
        }
        const options = { mode: params.mode, limit: params.limit, max_bytes: params.max_bytes,document:params.document,target:params.target,offset:params.offset };
        const result = await read(params.mode === "options" ? "optionList" : "observe", options);
        if (!result.ok) {if(framePending(result.error?.code)){await pause(50);continue;}return { handled: true, result };}
        if (params.mode === "options") return { handled:true,result };
        if (["interactive", "complete"].includes(result.ready)) {
          const checked=await guard(true);if(checked.handled)return checked;
          if(checked.state.download_capability)result.downloads=(await view.execute('downloads',{action:'list',agent:true})).downloads||[];
          if(checked.state.chooser_open)result.choosers=(await view.execute('chooser',{action:'list',agent:true})).choosers||[];
          return { handled: true, result };
        }
      }
      await pause(Math.min(50, Math.max(0, deadline - performance.now())));
    }
    if (command === "observe" || navigation === undefined) return fail("timed_out", "The page was not ready before the deadline");
    let policy=await guard(true);if(policy.handled)return policy;
    while(policy.state.input_busy){await pause(20);policy=await guard(true);if(policy.handled)return policy;}
    const downloadPolicy=await armDownloads();if(downloadPolicy)return downloadPolicy;
    if(!policy.state.chooser_capability||!policy.state.chooser_upload_capability)
      return fail('unsupported_capability','This host cannot safely intercept Agent file pickers; update the host or use manual control');
    if(!policy.state.chooser_armed){
      const armed=await view.execute('chooser',{action:'arm',agent:true});
      if(!armed.ok||!armed.armed)return fail('chooser_unavailable','Native file-picker interception was not confirmed; no input was sent');
    }
    if(params.target?.image){
      if(!pageDriver)return fail('unsupported_capability','This browser lacks isolated frame routing for visual input');
      return await executeVisualAction({browser:writer,params,view,read,guard,send,sendDrag,fail,deadline,action,
        setSnapshot:(snapshot,root,chain)=>{inputFrameSnapshot=snapshot;inputFrameChain=chain||[{frame:root.frame,document:root.document}];}});
    }
    if (params.op === "drag") {
      const samePair=(a,b)=>a&&b&&sameGeometry(a.source,b.source)&&sameGeometry(a.destination,b.destination);
      const preparePair=async(source=params.target,destination=params.destination,scroll=true)=>{
        const first=await read("prepare",{document:params.document,op:"drag",target:source,scroll});
        if(!first.ok)return{error:first};
        const second=await read("prepare",{document:params.document,op:"drop",target:destination,scroll:false});
        if(!second.ok)return{error:second};
        return{source:first,destination:second};
      };
      let previous,pair;
      while(performance.now()<deadline){
        const stopped=await guard(true);if(stopped.handled)return stopped;
        if(stopped.state.input_busy){previous=null;await pause(20);continue;}
        pair=await preparePair();
        if(pair.error){
          if(pair.error.error?.code==="element_outside_viewport")return fail("drag_target_outside_viewport","Both drag targets must be visible together; scroll the page before another attempt");
          if(!["element_not_found","element_disabled","element_obscured"].includes(pair.error.error?.code))return{handled:true,result:pair.error};
          previous=null;
        }else if(samePair(pair,previous))break;
        else previous=pair;
        await pause(60);
      }
      const checked=await guard(true);if(checked.handled)return checked;
      if(!pair?.source||!samePair(pair,previous))return fail("timed_out","The drag targets did not become stable and actionable before the deadline");
      const fresh=await preparePair({ref:pair.source.element.ref},{ref:pair.destination.element.ref},false);
      if(fresh.error||!samePair(fresh,pair))return fail("target_changed","A drag target moved or changed immediately before input");
      if(fresh.source.element.ref===fresh.destination.element.ref)return fail("drag_same_target","Choose distinct source and destination elements");
      const from=fresh.source.point,to=fresh.destination.point;
      if(Math.hypot(to.x-from.x,to.y-from.y)<4)return fail("drag_distance_too_small","Choose distinct drag points at least four CSS pixels apart");
      const pins={};
      if(pageDriver){
        const valid=chain=>Array.isArray(chain)&&chain.length>0&&chain.length<=32&&chain.every(row=>typeof row?.frame==='string'&&typeof row?.document==='string')&&chain.at(-1).frame==='f0';
        if(!valid(fresh.source.frame_chain)||!valid(fresh.destination.frame_chain))return fail('frame_response_invalid','Both drag targets need complete original frame document chains');
        pins.source_frame_chain=fresh.source.frame_chain;pins.destination_frame_chain=fresh.destination.frame_chain;
      }
      const delivered=await sendDrag({...from,to_x:to.x,to_y:to.y,...pins});if(delivered.handled)return delivered;
      return{handled:true,result:{ok:true,action:action.id,dispatched,verification:"native_delivery",input_method:"native_drag",
        drag_kind:delivered.drag_kind,observation:await read("observe",{limit:100,max_bytes:4096})}};
    }
    if (params.op === "scroll" && !params.target) {
      const snapshot = await read("observe", { limit: 1, max_bytes: 256 });
      if (snapshot.document !== params.document) return fail("stale_document", "Observe the current document before scrolling");
      if(pageDriver){const root=snapshot.frames?.find(row=>row.id==='f0');if(!root?.document)return fail('frame_response_invalid','The root frame pin is missing');inputFrameChain=[{frame:'f0',document:root.document}];}
      const point={x:snapshot.viewport.width/2,y:snapshot.viewport.height/2};
      if(policy.state.platform==='android-webview'){const checked=await pinScrollPoint(point);if(checked)return checked;}
      const error = await send({ kind: "wheel",...point,dx: params.dx || 0, dy: params.dy || 0 });
      if (error) return error;
    } else {
      let previous, prepared;
      while (performance.now() < deadline) {
        const stopped = await guard(); if (stopped.handled) return stopped;
        prepared = await read("prepare", { ...params, scroll: true });
        if (prepared.ok && sameGeometry(prepared, previous)) break;
        if (!prepared.ok && !["element_not_found", "element_disabled", "element_not_editable", "element_obscured", "element_outside_viewport","frame_owner_hidden","frame_owner_obscured","frame_point_outside_viewport","frame_not_ready","frame_tree_changed"].includes(prepared.error?.code)) return { handled: true, result: prepared };
        previous = prepared.ok ? prepared : null;
        await pause(Math.min(60, Math.max(0, deadline - performance.now())));
      }
      const stopped = await guard(true); if (stopped.handled) return stopped;
      if (!prepared?.ok || !sameGeometry(prepared, previous)) return fail("timed_out", "The target did not become actionable and stable before the deadline");
      const pinned = { ...params, target: { ref: prepared.element.ref } };
      const fresh = await read("prepare", pinned);
      if (!fresh.ok || !sameGeometry(fresh, prepared)) return fail("target_changed", "The target moved or changed immediately before input");
      if(pageDriver){if(!Array.isArray(fresh.frame_chain)||!fresh.frame_chain.length||fresh.frame_chain.length>32)return fail('frame_response_invalid','The prepared target has no complete frame document chain');inputFrameChain=fresh.frame_chain;}
      if(/^d-[a-f0-9-]{36}$/i.test(fresh.native_target?.document)&&/^e[1-9][0-9]{0,12}$/.test(fresh.native_target?.ref))
        inputTarget={input_document:fresh.native_target.document,input_ref:fresh.native_target.ref};
      if (params.op === "select") {
        const checked = await guard(true);if(checked.handled)return checked;
        dispatched = true;
        const selected = await read("selectOptions",pinned);
        if (!selected.ok) { if(selected.error?.effect_may_have_occurred===false)dispatched=false;return {handled:true,result:selected}; }
        dispatched = Boolean(selected.dispatched);
        const stopped = await guard(true);if(stopped.handled)return stopped;
        return {handled:true,result:{...selected,action:action.id,verification:"field_state",observation:await read("observe",{limit:100,max_bytes:4096})}};
      }
      if (params.op === "check" && fresh.element.checked === params.checked) return { handled: true, result: { ok: true, changed: false, action: action.id, verified: true } };
      if (params.op === "check" && fresh.element.role === "radio" && !params.checked) return fail("unsupported_action", "A radio input cannot be unchecked by clicking it; select another radio in the group");
      const keyboard=['fill','type','key'].includes(params.op);
      if(keyboard){
        const target=fresh.native_target,parts=typeof fresh.element.ref==='string'?fresh.element.ref.split('|'):[],leaf=inputFrameChain[0];
        if(!/^d-[a-f0-9-]{36}$/i.test(target?.document)||!/^e[1-9][0-9]{0,12}$/.test(target?.ref)||parts.length!==5||parts[0]!=='r1'||
           parts[1]!==leaf.frame||parts[2]!==leaf.document||parts[3]!==target.document||parts[4]!==target.ref)
          return fail('frame_response_invalid','The keyboard target is not bound to its original isolated frame');
        inputFocus={focus_document:target.document,focus_ref:target.ref};
      }
      if(params.op==='scroll'&&policy.state.platform==='android-webview'){
        const checked=await pinScrollPoint(fresh.point);if(checked)return checked;
      }
      let error = await send(keyboard?{kind:'focus',...inputFocus}:{ kind: params.op === "hover" ? "move" : params.op === "scroll" ? "wheel" : "click", ...fresh.point,
        button: params.op === "right_click" ? "right" : params.op === "middle_click" ? "middle" : "left",
        click_count: params.op === "double_click" ? 2 : 1, dx: params.dx || 0, dy: params.dy || 0 });
      if (error) return error;
      if (["fill", "type", "key"].includes(params.op)) {
        const focus = await read("verify", pinned);
        if (!focus.ok || !focus.focused) return fail("focus_changed", "The intended element lost focus before keyboard input");
        if (params.op === "fill") {
          error = await send({ kind: "key", key: "Control+A" }); if (error) return error;
          const selectedFocus=await read('verify',pinned);
          if(!selectedFocus.ok||!selectedFocus.focused)return fail('focus_changed','The intended element lost focus during selection; no replacement text was sent');
        }
        error = await send(params.op === "key" ? { kind: "key", key: params.key } :
          params.op === "fill" && !params.text ? { kind: "key", key: "Backspace" } : { kind: "text", text: params.text });
        if (error) return error;
      }
      if (["fill", "check"].includes(params.op)) {
        const verified = await read("verify", pinned);
        if (!verified.ok || !verified.verified) return fail("action_uncertain", "Input was dispatched but the requested field state was not confirmed; inspect before repeating it");
      }
    }
    const stopped = await guard(true); if (stopped.handled) return stopped;
    const observation=await read("observe",{limit:100,max_bytes:4096});
    // Native input can precede response/picker callbacks. Coalesce the immediate
    // event turn; arbitrary later website activity is observed on the next call.
    if(dispatched&&['click','double_click','middle_click','key'].includes(params.op))await pause(Math.min(150,Math.max(0,deadline-performance.now()-25)));
    const after=await guard(true);if(after.handled)return after;
    return { handled: true, result: { ok: true, action: action.id, dispatched,
      verification: ["fill", "check"].includes(params.op) ? "field_state" : "native_delivery",
      observation } };
  } catch (error) {
    if(error.browserBoundary)return error.browserBoundary;
    if (error.dialog) return dialogBoundary();
    if (error.popup) return popupBoundary();
    if(dispatched&&downloadCheckpointUntil){
      // A navigation can invalidate an in-flight page read before its response
      // identity is published. Keep the original input; inspect metadata only.
      try{while(performance.now()<downloadCheckpointUntil){const checked=await guard(true);if(checked.handled)return checked;await pause(20);}}catch{}
    }
    return fail(dispatched ? "action_uncertain" : "browser_unavailable", String(error?.message || error).slice(0, 512));
  } finally {
    if(dragRequested)try{await view.execute("drag_cancel",{drag_key:action.id});}catch{}
    if (exclusive) writers.delete(writer);
    if (captures) capturing = false;
  }
}
