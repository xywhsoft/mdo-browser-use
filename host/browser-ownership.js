// UI instance identity only. Broker/native permission checks remain authoritative.
export function browserScopeKey(row) {
  return JSON.stringify([row?.owner || "", ...["device","project","session","run","workspace","namespace","member"]
    .map(key => row?.context?.[key] || (key === "project" ? "default" : ""))]);
}
export function browserViewKey(row) {
  return JSON.stringify([row?.resource?.id || row?.id, row?.client, browserScopeKey(row)]);
}

/** The UI owns one mount; the task's native command dispatcher outlives it. */
export function createBrowserViewLifetime({ row, signal, mount, mounts, entries, views, alive, close, reconcile }) {
  const browser = row.resource?.id, entry = entries.get(row.id), original = views.get(browser);
  const scope = browserScopeKey(row);
  const panelKey = value => JSON.stringify([browserViewKey(value), value?.type, value?.params?.url || ""]);
  const identity = panelKey(entry);
  let registered, ended = false;
  const mounted = () => alive() && !signal.aborted && mounts.get(row.id) === mount && entry &&
    browserScopeKey(entries.get(row.id)) === scope && panelKey(entries.get(row.id)) === identity;
  const current = () => !ended && Boolean(mounted()) && (registered ? views.get(browser) === registered :
    views.get(browser) === original && (!original || browserViewKey(original.row) === browserViewKey(row)));
  return Object.freeze({ current,
    register(view) {
      if (!current() || !browser || view.id !== browser || typeof view.execute !== "function") return false;
      if (registered) return registered.execute === view.execute;
      registered = { execute: view.execute, row };views.set(browser, registered);
      void reconcile(browser);return true;
    },
    async end(id) {
      if (id !== browser || !current() || !registered && views.has(id)) return;
      ended = true;if (registered) views.delete(id);
      await close(row.id, () => Boolean(mounted()) && !views.has(id));
    }
  });
}
