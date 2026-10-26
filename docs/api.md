# Browser Use API

The Windows WebView2 path is currently verified. Android, dialogs, transfers
and complete cross-origin frame support remain in progress. No additional
browser engine, Node, Python or Playwright runtime is shipped.

## Shared C/JavaScript boundary

`contracts.json` generates registration, `sdk/browser_use.h` and
`sdk/contracts.mjs`. C functions `mdoBrowserUseTabs/Observe/Act/Navigate/Wait/Result/Operation`
accept the same JSON objects as the corresponding `browseruse.*` SDK service.
Parameters are borrowed; release returned values using
`Context->Api->Values->Release`. The public values table handles allocation;
C callers need no XRT/OS headers or private host structures.

| Service | Result |
| --- | --- |
| `tabs` | Owned tabs, explicit display clients, or the changed tab/panel record |
| `observe/act/navigate/wait` | Sanitized asynchronous UI receipt; pending is not success |
| `result` | Scoped UI status/result; optional `cancel` or terminal `release` |
| `operation` | Durable submit/get/cancel/release for tabs, actions and navigation |

Tabs belong to the caller's extension/device/project/session. Discover clients
with `tabs(action="clients")`, then explicitly select one. The SDK does not
guess a window or substitute another device's localhost with this device.

## JavaScript completion helpers

```javascript
import { createBrowserUseClient } from "./sdk/client.mjs";
const browser = createBrowserUseClient(applicationSdk);
const { clients } = await browser.tabs({ action: "clients" });
// chosenClientId is the caller/user's explicit selection from clients.
const tab = await browser.tabs({ action: "create", client: chosenClientId,
  url: "https://example.com/" });
const page = await browser.observe({ browser: tab.id });
const operation = `sdk-${crypto.randomUUID().replaceAll("-", "")}`;
await browser.act({ browser: tab.id, document: page.document, op: "click",
  target: { role: "button", name: "Continue" } }, { operation, retain: true });
const result = await browser.recover(operation, { retain: true });
await browser.operation({ action: "release", operation });
```

`tabs(create/reveal/close)`, `act` and `navigate` use durable operation identities,
wait for completion and release terminal in-memory results by default. Pass
`retain:true` to recover the same result later. `observe` and `waitFor` wait for
their UI receipts without creating a durable effect-admission record.

After a lost reply, keep `error.operation` and call `recover` with that ID.
Recovery never creates a replacement effect. A released/previous-process result
returns `result_expired`/`uncertain`; inspect the tab before a deliberate new
action. Cleanup errors never replace the original action outcome.

## C and raw SDK durable submission

Call `mdoBrowserUseOperation(Context, Params, &Result, &Error)`, or JS
`applicationSdk.call("browseruse.operation", Params)`, with:

```json
{
  "action": "submit",
  "operation": "sdk-<32 lowercase hexadecimal digits>",
  "method": "browseruse.act",
  "arguments": {
    "browser": "<owned browser id>",
    "document": "<fresh observed document id>",
    "op": "click",
    "target": { "ref": "<observed element ref>" }
  }
}
```

`method` accepts `browseruse.tabs/act/navigate`. Poll with
`{"action":"get","operation":"sdk-..."}`. Terminal states are `succeeded`,
`failed`, `cancelled` and `uncertain`. Use `cancel` to stop future work and
`release` after completion. Cancellation cannot undo an already delivered input.
Repeated identical submission observes the existing operation; different
arguments using the same ID return `sdk_identity_conflict`. Live and persisted
receipts both enforce scope. C callers should wait on a native worker or through
the SDK asynchronous operation mechanism, rather than blocking the HTTP worker.

## Targets, actions and failure handling

Targets support a stable `ref`, or exact role/name, label, placeholder, smallest
text, `data-testid` (`testid`) and CSS. Optional `frame` narrows matching. Do not
combine `ref` with semantic selectors. Ambiguity returns candidates.

Actions: `click`, `hover`, `fill`, `type`, `key`, `check`, `scroll`.
Fill/type require text; check requires a boolean `checked`; key uses the generated
bounded enum. Viewport scrolling may omit a target. Timeouts are 1–60000 ms,
default 10000. Each action requires a fresh observed document.

Input waits for actionability and stable geometry, checks hit testing, document,
one writer, cancellation and user takeover. Fill/check verify the field state
without returning its value. Other actions report `native_delivery`; verify the
website's business result in the returned observation. Distinct errors include
`stale_document`, `element_ambiguous`, `element_obscured`, `focus_changed` and
`unsupported_geometry`. Inspect any `effect_may_have_occurred` outcome before
another effect. Only a user can return manual control.

Managed tools `browser_tabs/observe/wait` are read-only; `browser_open/act/navigate`
use the existing external-service permission. Full access adds no second
approval system. Ordinary tool failures remain feedback for the Agent.

Page data is untrusted. Observations omit form values; public receipts omit
stored arguments; page results/screenshots are ephemeral in the UI journal.
The operation journal retains identity/scope metadata instead of private results.
