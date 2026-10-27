// Inserted into the existing workbench executor. Actions run in the visible
// native viewport, with its ordinary toolbar and user takeover affordance.
if(['observe','act','navigate_to','wait_for','dialog','chooser','capture','upload','download'].includes(command)&&!browserViews.has(id)){
  const caller=api.bind(action.context||{}),browser=await caller.call('browser.get',{id});
  if(isRemoteTarget()&&browser.url&&['localhost','127.0.0.1','[::1]'].includes(new URL(browser.url).hostname))throw new Error('目标设备的本地网址需要预览服务映射');
  await ensureNativeBrowser({bridge,sdk:caller,browser,clientId,requireDownloads:true,
    alive:()=>!stopped&&displayTarget===(isRemoteTarget()?targetState().selected.id:'local'),bounds:{x:0,y:0,width:1024,height:768,scale:1}});
  browserViews.set(id,{row:browser,execute:(operation,parameters={})=>bridge.call(operation,{browser:id,...parameters})});
}
if (command === "act" || command === "navigate_to" || command === "capture" || command === "upload" && ["set","clear"].includes(params.action)) {
  const caller = api.bind(action.context || {});
  const panel = await caller.call("panel.open", { type: "mdo.browser", client: clientId,
    key: id, resource: { id }, reveal: "activate" });
  await sync(); reveal(panel.id);
  const deadline = performance.now() + Math.min(10000, params.timeout_ms || 10000);
  let visible = false, phase = "waiting_for_panel_mount";
  while (!stopped && performance.now() < deadline) {
    const current = browserViews.get(id);
    if (current?.row.id === panel.id) {
      const state = await current.execute("state");
      const mounted = mounts.get(panel.id);
      phase = `visible=${Boolean(state.visible)},document_hidden=${document.hidden},active_matches=${layout.active===panel.id},root_hidden=${mounted?.root.hidden},mount_status=${mounted?.status?.status},mount_error=${mounted?.status?.message||""},viewport=${state.controller_bounds?.width}x${state.controller_bounds?.height},renderer_failed=${Boolean(state.process_failed)}`;
      if (state.visible) { visible = true; break; }
    }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  if (!visible) throw Error(`The browser action viewport could not be shown (${phase})`);
}
