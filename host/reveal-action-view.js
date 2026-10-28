// Inserted into the existing workbench executor. Actions run in the visible
// native viewport, with its ordinary toolbar and user takeover affordance.
if(['observe','act','navigate_to','wait_for','dialog','chooser','capture','upload','download'].includes(command)&&!browserViews.has(id)){
  const caller=api.bind(action.context||{}),browser=await caller.call('browser.get',{id});
  if(isRemoteTarget()&&browser.url&&['localhost','127.0.0.1','[::1]'].includes(new URL(browser.url).hostname))throw new Error('目标设备的本地网址需要预览服务映射');
  try{await ensureNativeBrowser({bridge,sdk:caller,browser,clientId,requireDownloads:true,requireAutomation:true,
    alive:()=>!stopped&&displayTarget===(isRemoteTarget()?targetState().selected.id:'local'),bounds:{x:0,y:0,width:1024,height:768,scale:1}});}
  catch(error){if(error.code==='unsupported_capability')return{ok:false,action:action.id,error:{code:error.code,message:error.message,capability_reason:error.capability_reason,effect_may_have_occurred:false}};throw error;}
  browserViews.set(id,{row:browser,execute:(operation,parameters={})=>bridge.call(operation,{browser:id,...parameters})});
}
if (command === "act" || command === "navigate_to" || command === "capture" || command === "upload" && ["set","clear"].includes(params.action)) {
  const caller = api.bind(action.context || {});
  const panel = await caller.call("panel.open", { type: "mdo.browser", client: clientId,
    key: id, resource: { id }, reveal: "activate" });
  await sync(); reveal(panel.id);
  const deadline = performance.now() + Math.min(10000, params.timeout_ms || 10000);
  let ready = false, phase = "waiting_for_panel_mount";
  while (!stopped && performance.now() < deadline) {
    const current = browserViews.get(id);
    if (current?.row.id === panel.id) {
      const state = await current.execute("state");
      const mounted = mounts.get(panel.id);
      phase = `visible=${Boolean(state.visible)},document_hidden=${document.hidden},active_matches=${layout.active===panel.id},root_hidden=${mounted?.root.hidden},mount_status=${mounted?.status?.status},mount_error=${mounted?.status?.message||""},viewport=${state.controller_bounds?.width}x${state.controller_bounds?.height},renderer_failed=${Boolean(state.process_failed)}`;
      // A page-network error intentionally hides the native surface behind its
      // error card. Explicit navigation must be able to replace that page; it
      // sends no pointer input and its normal dispatcher guards still apply.
      let navigationReady = false;
      if(command==='navigate_to'&&typeof state.error==='string'&&state.error&&state.automation===true&&
         !state.process_failed&&!state.manual&&!state.creating&&!state.close_requested&&!state.closed_by_page&&!state.dialog_open&&!state.popup_open&&
         !document.hidden&&layout.open&&layout.active===panel.id&&mounted?.root.isConnected===true&&mounted.root.hidden===false&&
         mounted.status?.status==='error'&&mounted.status.message===state.error){
        const surface=mounted.root.querySelector('.wb-browser-surface')?.getBoundingClientRect(),bounds=state.controller_bounds;
        const unobstructed=![...document.querySelectorAll('dialog[open], [role=menu]:not([hidden])')].some(node=>node.getClientRects().length);
        navigationReady=unobstructed&&[surface?.width,surface?.height,bounds?.width,bounds?.height].every(number=>Number.isFinite(number)&&number>0);
      }
      if (state.visible===true||navigationReady) { ready = true; break; }
    }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  if (!ready) throw Error(`The browser action viewport could not be shown (${phase})`);
}
