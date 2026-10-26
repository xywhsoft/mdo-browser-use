// Inserted into the existing workbench executor. Actions run in the visible
// native viewport, with its ordinary toolbar and user takeover affordance.
if (command === "act" || command === "navigate_to") {
  const caller = api.bind(action.context || {});
  const panel = await caller.call("panel.open", { type: "mdo.browser", client: clientId,
    key: id, resource: { id }, reveal: "activate" });
  await sync(); reveal(panel.id);
  const deadline = performance.now() + Math.min(10000, params.timeout_ms || 10000);
  let visible = false;
  while (!stopped && performance.now() < deadline) {
    const current = browserViews.get(id);
    if (current?.row.id === panel.id) {
      const state = await current.execute("state");
      if (state.visible) { visible = true; break; }
    }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  if (!visible) throw Error("The browser action viewport could not be shown");
}
