// Keep executable QA adapters aligned with the public Application SDK's
// third argument (call options), without performing a browser/file effect.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

for (const name of ["download", "upload", "save"]) {
  test(name + " probe preserves bound context while forwarding SDK call options", async () => {
    const source = await readFile(new URL(`../tools/${name}_client_probe.mjs`, import.meta.url), "utf8");
    const scoped = source.indexOf("const callInScope="), sdkStart = source.indexOf("const sdk=");
    const start = scoped < 0 ? sdkStart : scoped, end = source.indexOf("const client=", start);
    assert.ok(start >= 0 && end > start);
    const body = source.slice(start, end);
    const env = JSON.parse(await readFile(new URL("../.mdo/environment.json", import.meta.url)));
    const origin = `http://127.0.0.1:${env.port}`;
    const bootstrap = await fetch(origin + "/api/v1/bootstrap"), token = bootstrap.headers.get("X-Mdo-Write-Token");
    await bootstrap.arrayBuffer(); assert.equal(bootstrap.status, 200); assert.ok(token);
    const context = { extension: "browser-use-probe-sdk-qa", project: "default", session: "probe-sdk-task",
      workspace: fileURLToPath(new URL("../", import.meta.url)).replace(/[\\/]$/, ""), namespace: "probe-private", member: "probe-member" };
    const load = new Function("origin", "token", "context", body + ";return {sdk,callInScope:typeof callInScope==='undefined'?null:callInScope};");
    const { sdk, callInScope } = load(origin, token, context);
    const controller = new AbortController();
    const actual = await sdk.call("sdk.context", {}, { signal: controller.signal });
    for (const key of ["extension", "project", "session", "workspace", "namespace", "member"])
      assert.equal(actual[key], context[key], "SDK options replaced the probe's original " + key);
    controller.abort();
    await assert.rejects(sdk.call("sdk.context", {}, { signal: controller.signal }), { name: "AbortError" });
    assert.equal(typeof callInScope, "function", "Scope overrides must use the explicit QA-only helper");
    const foreign = await callInScope("sdk.context", {}, { ...context, namespace: "foreign-probe-private" });
    assert.equal(foreign.namespace, "foreign-probe-private"); assert.equal(foreign.session, context.session);
    if (name === "upload") {
      const at = source.indexOf("directoryClient=createBrowserUseClient"), end = source.indexOf(";", at);
      assert.ok(at >= 0 && end > at);
      const bind = new Function("createBrowserUseClient", "callInScope", "sdk", "directoryContext",
        "let directoryClient;" + source.slice(at, end + 1) + ";return directoryClient;");
      const directoryContext = { ...context, workspace: context.workspace + "\\.mdo\\qa\\public-upload-directory" };
      const directory = bind(value => value, callInScope, sdk, directoryContext);
      const actual = await directory.call("sdk.context", {}, { signal: new AbortController().signal });
      assert.equal(actual.workspace, directoryContext.workspace); assert.equal(actual.extension, context.extension);
    }
  });
}
