import { services } from "./contracts.mjs";

export function createBrowserUseClient(sdk) {
  if (typeof sdk?.call !== "function") throw new TypeError("An mdo Application SDK is required");
  const invoke = (name, params) => sdk.call(`browseruse.${name}`, params);
  return Object.freeze({
    tabs: params => invoke("tabs", params),
    services: Object.freeze(services.map(row => row.id))
  });
}
