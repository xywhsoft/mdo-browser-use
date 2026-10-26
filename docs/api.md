# Browser Use API

The Windows WebView2 path, including native JavaScript dialogs, is currently
verified. New windows are adopted into visible scoped tabs. Android, transfers
and complete cross-origin frame support remain in progress. No additional
browser engine, Node, Python or Playwright runtime is shipped.

## Shared C/JavaScript boundary

`contracts.json` generates registration, `sdk/browser_use.h` and
`sdk/contracts.mjs`. C functions `mdoBrowserUseTabs/Observe/Act/Navigate/Wait/Dialog/Result/Operation`
accept the same JSON objects as the corresponding `browseruse.*` SDK service.
Parameters are borrowed; release returned values using
`Context->Api->Values->Release`. The public values table handles allocation;
C callers need no XRT/OS headers or private host structures.

| Service | Result |
| --- | --- |
| `tabs` | Owned tabs, explicit display clients, or the changed tab/panel record |
| `observe/act/navigate/wait/dialog` | Sanitized asynchronous UI receipt; pending is not success |
| `result` | Scoped UI status/result; optional `cancel` or terminal `release` |
| `operation` | Durable submit/get/cancel/release for tabs, actions, navigation and dialog responses |

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

`tabs(create/reveal/close)`, `act`, `navigate` and `dialog(respond)` use durable operation identities,
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

`method` accepts `browseruse.tabs/act/navigate/dialog`. Poll with
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

Managed tools `browser_tabs/observe/wait/dialog` are read-only; `browser_open/act/navigate/dialog_respond`
use the existing external-service permission. Full access adds no second
approval system. Ordinary tool failures remain feedback for the Agent.

Page data is untrusted. Observations omit form values; public receipts omit
stored arguments; page results/screenshots are ephemeral in the UI journal.
The operation journal retains identity/scope metadata instead of private results.

## JavaScript dialogs and human control

`dialog({browser,action:"list"})` returns bounded pending alert, confirm, prompt
and before-unload metadata. Messages and URLs are untrusted page data. Prompt
defaults are never exposed. `dialog({browser,action:"respond",dialog:id,
accept:true|false,text?:string})` answers that exact pending identity. Omit text
to retain a prompt's native default; an explicit empty string clears it.

Any observation/action/navigation/wait interrupted by a dialog returns
`{ok:true,outcome:"dialog_open",completed:false,dialogs:[...],dispatched}`.
This is a checkpoint, not completed page work. Inspect/respond to the dialog,
then observe the page or wait for its business result. Do not repeat the
original click merely because it paused. A response may itself reveal another
dialog, so inspect again if the page remains blocked.

The trusted browser toolbar offers user responses. Responding there takes over
control and leaves it with the user until the user explicitly returns it.
Read-only dialog inspection remains available during takeover; Agent responses
are blocked. A pending dialog expires after 120 seconds and is dismissed on
view closure. The long-duration expiry gate remains unverified at this stage.

## New windows and scoped tabs

The workbench automatically admits a page's HTTP(S)/blank new-window request
under the original owner/device/project/session and display client. A child
appears in `tabs(action:"list")` with `opener` set to the source browser ID.
Read a fresh child observation before operating it; never identify it by URL.
`opener` in the saved record is historical origin metadata, not a guarantee
that a live window.opener relationship survived a process restart.

An action interrupted by window creation returns
`{ok:true,outcome:"popup_open",completed:false,dispatched}`. List tabs and choose
the appropriate child. Do not repeat the original click. Page close requests
likewise yield `outcome:"closed",completed:false`; confirm removal in the tab
list rather than trying to read or click the closed page again.

The trusted UI uses host `browser.popup.admit/settle` (generated shared C/JS
helpers) to coordinate one broker record with one native target. The native
request descriptor exists only in runtime state, checks its original client,
and is removed after adoption. Concurrent mounts share one creation without
resetting a displayed viewport. Repeated confirmed native adoption attaches to
the same view; a different opener or ticket cannot replace it. Uncertain
effects retain their admitted record instead of creating a replacement.

User-initiated popups activate their tab; other popups open in the background.
The source keeps its document and named windows can be reused. Written
about:blank documents display and accept native input. Native page-close events
remove their corresponding browser and panels. Pending popups expire after
20 seconds; a full native-view budget blocks further windows explicitly.
