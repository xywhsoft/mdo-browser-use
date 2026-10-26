import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { createBrowserUseClient } from "../sdk/client.mjs";
import { services } from "../sdk/contracts.mjs";

test("JavaScript client reaches the same actual C service contract", async () => {
  const env = JSON.parse(await readFile(new URL("../.mdo/environment.json", import.meta.url), "utf8"));
  const origin = `http://127.0.0.1:${env.port}`;
  const bootstrap = await fetch(`${origin}/api/v1/bootstrap`);
  assert.equal(bootstrap.status, 200);
  const token = bootstrap.headers.get("x-mdo-write-token");
  await bootstrap.arrayBuffer();
  const sdk = { async call(method, params) {
    const response = await fetch(`${origin}/api/v1/application/call`, {
      method: "POST", headers: { "Content-Type": "application/json", "X-Mdo-Write-Token": token },
      body: JSON.stringify({ method, params, context: { extension: "browser-use-js-check" } })
    });
    const result = await response.json();
    assert.equal(response.status, 200, result.error?.message);
    return result.data;
  }};
  const client = createBrowserUseClient(sdk);
  assert.deepEqual(client.services, services.map(s => s.id));
  const result = await client.tabs({ action: "list" });
  assert.ok(Array.isArray(result.tabs));
  const description = await sdk.call("sdk.describe", { id: "browseruse.tabs" });
  assert.deepEqual(description.input_schema, services[0].input);
});
