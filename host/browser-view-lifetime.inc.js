  async function browserView(root, row, signal, mount) {
    const lifetime = createBrowserViewLifetime({ row, signal, mount, mounts, entries, views: browserViews, close,
      alive: () => !stopped && displayTarget === (isRemoteTarget() ? targetState().selected.id : "local"),
      reconcile: id => popupManager.reconcile(id) });
    if (!lifetime.current()) return;
    const view = await mountBrowserView(root, { row, sdk: bound(row), signal, mount, bridge, clientId,
      alive: lifetime.current,
      shown: () => !root.hidden && layout.open && !document.hidden && navigation.get().view !== "settings",
      updateTitle: title => update(row.id, { title }, lifetime.current), openExternal: target => layoutApi.call("system.open", { target }), addContext,
      onEnd: lifetime.end });
    lifetime.register(view);
  }
