  async function browserView(root, row, signal, mount) {
    const sameTarget = () => displayTarget === (isRemoteTarget() ? targetState().selected.id : "local");
    const preferred = () => layout.active === row.id || layout.secondary === row.id && mounts.get(layout.active)?.browserId !== row.resource.id;
    const lifetime = createBrowserViewLifetime({ row, signal, mount, mounts, entries, views: browserViews, close,
      alive: () => !stopped && sameTarget(), cleanupAlive: sameTarget,
      reconcile: id => popupManager.reconcile(id) });
    if (!lifetime.current()) return;
    const view = await mountBrowserView(root, { row, sdk: bound(row), signal, mount, bridge, clientId,
      alive: lifetime.current,
      canHide: lifetime.canHide,
      onDispose: lifetime.release,
      onBind: view => (preferred() || !browserViews.has(view.id)) && lifetime.bind(view),
      onRetired: () => { if (preferred() && lifetime.canRemount()) remount(row.id); },
      shown: () => preferred() && !root.hidden && layout.open && !document.hidden && navigation.get().view !== "settings",
      updateTitle: title => update(row.id, { title }, lifetime.current), openExternal: target => layoutApi.call("system.open", { target }), addContext,
      onEnd: lifetime.end });
    // A task can publish its dispatcher while this pane awaits native startup.
    // Keep that dispatcher; restart only the preferred, still-scoped UI mount
    // so an early retirement cannot leave an empty pane without callbacks.
    if (!lifetime.register(view) && preferred() && lifetime.canRemount()) remount(row.id);
  }
