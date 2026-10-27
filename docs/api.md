# Browser Use API

The Windows WebView2 path includes verified native JavaScript dialogs, visible
scoped popup tabs, regular file-input/picker uploads, intercepted response
downloads, task-workspace saves, nested cross-origin frame routing and explicit
recovery controls. Android and the remaining vision/lifecycle gates are in progress. No additional
browser engine, Node, Python or Playwright runtime is shipped.

Page document identities use cryptographic random bytes rather than relying on
secure-context-only `randomUUID`; see the [Crypto interface](https://w3c.github.io/webcrypto/#Crypto-interface).
Associated labels omit embedded controls/values, and focus verification follows
same-origin/shadow ancestors. These remain bounded DOM-subset semantics, not a
claim of complete browser accessibility-tree equivalence.
Readable observations and fallback labels also omit editable DOM text,
including noneditable descendants within an editing ancestor. Explicit control
labels, roles and references remain available.

### Native frame routing foundation (private, not a public SDK operation)

The Windows host now has a trusted-main-document `frames` command. `list`
returns a bounded frame tree with per-view aliases, per-document identities,
ancestry and loading/process-route state. `evaluate` requires one of those
aliases and can pin its document identity. It creates an isolated world in the
owning frame/session, including OOPIFs; raw CDP session, execution context and
target identifiers are never exposed by this inventory. Models cannot submit
script or CDP through the public C/JS services.

The backend auto-attaches only iframe targets related to this content view,
without global target discovery, universal-origin access or disabled site
isolation. Frame/session records are capped at 64 each, pending frame jobs at
16, tree depth at 32, script size at 128 KiB and job lifetime at 10 seconds.
Limits fail explicitly; navigation resets resource errors. Navigation,
detachment, manual takeover and close invalidate affected pending evaluations.
After `Runtime.evaluate` was sent, a failed receipt conservatively retains
`evaluation_may_have_occurred:true`; it must not be used to replay a mutation.
This is not a transactional guarantee about JavaScript handlers.

The actual WebView2 frame gate covers cross-site/nested contexts, hostile
page-world runtime markers, primitive return encoding, foreign view aliases,
child navigation, close/recreation and frame/session saturation/recovery.
The normal Windows workbench enables this fixed frame driver when its content
view advertises `frame_automation:true`. Public observations, qualified element
actions, frame uploads/pickers and composed screenshot privacy use the same
private routing. Existing backends without this flag keep their original path.
Models still cannot request raw script, sessions or protocol operations.

The internal `frame-reader.js` now combines one isolated local-frame runtime
per document. It prevents duplicate same-origin traversal, namespaces element
references by native frame/document and local runtime identity, and detects
semantic ambiguity across frames. A loading/unavailable searched frame cannot
produce a falsely unique match; a narrowed frame may still be read. Combined
results revalidate the original root and relevant frame tree before delivery.
Limits apply across the operation: 32 frames, 6,000 visited elements, 400
returned elements, 32 KiB readable text and 256 KiB serialized metadata. The
deadline and originating-action checkpoint are checked between bounded native
calls. Reader coordinates remain frame-local. The internal fixed-operation
driver maps them through the native owner checks and supplies document chains
to the input dispatcher.

The private native `frames` action `owner` now pins a child document and resolves
its actual iframe owner in the parent session/isolated world. It returns the
parent identity, content box and mapped frame-local point after checking
visibility, clipping/occlusion and current ancestry. Fractional borders/padding
are retained. Unsupported transforms/zoom and points outside the parent viewport
fail explicitly. Temporary remote DOM objects are released as a group on every
terminal path. No DOM object or backend node identity is returned to callers.

This uses the official [DOM protocol](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/DOM.pdl)
frame-owner/isolated-object operations; final input coordinates follow the
[Input protocol](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/Input.pdl)
main-viewport convention. The same installed WebView2 was tested against nested
cross-site frames with a trusted click. The internal driver performs fresh
geometry checks and supplies per-frame input fences. These checks are not a
transactional click guarantee about arbitrary website handlers.

Native `input` now accepts a private `frame_chain` of `{frame,document}` pins
from the target frame through every parent to `f0` (maximum 32). It validates
the complete chain at admission and before every subsequent input step, and
refuses missing/reordered/loading/replaced/error-state frames. A child-only
navigation invalidates the chain even when the top navigation token is stable.
An interrupted sequence releases only a press it actually admitted; skipped
presses cause no extra up events, while a lost press acknowledgement still
requires its cleanup. The existing trusted-native calls may omit the field;
the public multi-frame adapter must supply it for routed input.

`frame-driver.js` now provides fixed observe/locate/prepare/verify, option
discovery/selection, hidden-upload target preparation and root capture-area
operations. It maps qualified references back into their pinned local worlds,
checks ancestor focus and carries cancellation checkpoints through failed
native replies as well as successful ones. Selection mutations are never
replayed after uncertainty. File clearing stays in its ticketed upload path.

The dispatcher accepts this driver internally and forwards its chain on every
mouse/key/text input. It rechecks focus after Select All before replacement text.
Its actual WebView2 gate exercises nested fill, global references, waits,
option transactions, root masking and cancellation with controlled UI-receipt
status. Ticketed upload/clear and picker acceptance/cancel now route through the
exact isolated frame world/session and original ancestor document chain.
New OOPIFs inherit picker interception while Agent control is active. Child
navigation expires unresolved pickers/staging; completed upload receipts can
be queried without replay and retain website bytes until confirmed release.
Hidden uploads also check real parent iframe owners for composed `inert`
ancestry before source reads and on post-seal target revalidation. Visibility
alone does not disable a hidden file input. The normal workbench now uses this
driver for its supported Windows content views. The
capture coordinator now composes exact local masks through native owners and
clips nested element screenshots; unsupported/unreadable frames remain opaque.
Before/after frame/layout stamps withhold a captured image if its child
documents changed while the top-level navigation stayed the same.
The internal drag adapter already sends source and destination document chains,
fences both throughout the native gesture, and drains mouse/interception state
on cancellation or child navigation. Actual tests cover nested pointer/HTML5
and root/OOPIF HTML5 delivery in both directions without repeating a drop.
Public C SDK and managed Agent tests cover nested Unicode fill/selection,
private element images, complete upload SHA-256, exact picker delivery/cancel,
and owned read-only inspection/images versus denied mutation through that UI.
Offscreen parent scrolling and model-supplied vision coordinates are not yet
implemented; unsupported geometry remains an explicit bounded failure.

Native top-level navigation invalidates pending operations immediately. A file
response can leave the original DOM alive: a fresh native inventory may preserve
its frame/document reference only when the exact frame, parent and nonempty
loader identity match. A URL match alone is never sufficient. Slow submitted
responses remain fenced until native navigation settles, bounded by the original
action deadline and cancellation. The original POST/input is never replayed.

## Shared C/JavaScript boundary

`contracts.json` generates registration, `sdk/browser_use.h` and
`sdk/contracts.mjs`. C functions `mdoBrowserUseTabs/Observe/Act/Navigate/Wait/Dialog/Chooser/Screenshot/Upload/Download/Save/Result/Operation`
accept the same JSON objects as the corresponding `browseruse.*` SDK service.
Parameters are borrowed; release returned values using
`Context->Api->Values->Release`. The public values table handles allocation;
C callers need no XRT/OS headers or private host structures.

| Service | Result |
| --- | --- |
| `tabs` | Owned tabs, explicit display clients, or the changed tab/panel record |
| `observe/act/navigate/wait/dialog/chooser/screenshot/upload/download/save` | Sanitized asynchronous UI receipt; pending is not success |
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

`tabs(create/reveal/close)`, `act`, `navigate`, `dialog(respond)` and `upload(set/release)` use durable operation identities,
wait for completion and release terminal in-memory results by default. Pass
`retain:true` to recover the same result later. `observe`, `waitFor` and `screenshot` wait for
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

`method` accepts `browseruse.tabs/act/navigate/dialog/upload`. Poll with
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

Actions: `click`, `double_click`, `right_click`, `middle_click`, `hover`, `fill`,
`type`, `key`, `check`, `select`, `scroll`, `drag`.
Fill/type require text; check requires a boolean `checked`; key uses the generated
bounded enum. Viewport scrolling may omit a target. Timeouts are 1–60000 ms,
default 10000. Each action requires a fresh observed document.

Mouse gestures use native press/release sequences after fresh hit testing and
stable geometry. Double-click performs two left clicks with native click counts;
right/middle use their actual buttons. A backend must advertise `advanced_mouse`
for these gestures; otherwise it fails before scrolling/evaluating/input rather
than substituting a left click. Page event delivery does not prove the website's
business operation succeeded. Vision-coordinate input is a later stage.

## Native dragging through the shared action service

```javascript
await browser.act({ browser: tab.id, document: page.document, op: "drag",
  target: { role: "button", name: "Card to move" },
  destination: { role: "button", name: "Drop target" } });
```

C callers pass the same object to `mdoBrowserUseAct`; Agent calls use
`browser_act`. Both targets must resolve precisely in the same fresh observed
document. `destination` supports the same locator fields as `target`, requires
a selector beyond `frame`, and is only valid for drag. There is no supplied
JavaScript, raw drag payload, local file path or coordinate escape hatch.

The source can scroll into view during preparation. Both elements must then be
visible together, stable and hit-testable; this version does not drag across an
offscreen/autoscrolling region. An unsupported backend fails before reading or
scrolling the targets. Native input owns one view slot through completion and
cleanup, and refuses conflicting input/capture. Viewport resize, navigation,
user takeover, cancellation and deadlines stop further delivery and release
the pointer. An uncertain outcome must be inspected before another attempt.

Success reports `input_method:"native_drag",verification:"native_delivery"`
and `drag_kind:"pointer"|"html5"`, with a fresh observation. Pointer gestures
deliver trusted native press/move/release events. HTML5 data comes only from
the intercepted browser event and stays in memory; it is not echoed in results
or stored as operation arguments. File lists, local/non-HTTP or credential URI
lists, download payloads and excessive data are refused. Files require the
separate transfer capability, which is still under development.

Native delivery does not prove the website accepted the drop or saved a change.
Verify the business result. The normal durable operation identity/recovery rules
apply to drag, so recovering the same operation cannot issue a second gesture.
If native cleanup cannot be confirmed, `drag_cleanup_failed` blocks further
mutations/capture in that view; inspect and end that browser before reopening.

## Select option discovery and exact selection

```javascript
const options = await browser.observe({ browser: tab.id, mode: "options",
  document: page.document, target: { label: "Delivery method" }, limit: 16 });
await browser.act({ browser: tab.id, document: options.document, op: "select",
  target: { ref: options.ref }, choices: [{ index: 3 }] });
```

Ordinary observations describe each HTML select's `multiple` and `option_count`.
`mode:"options"` requires a fresh `document` and exact select target; it returns
labels, indices and disabled states without stored option values or current
selection. Follow `next_offset` when `truncated` is true. `offset` is 0–256,
the select is bounded to 256 options, and `max_bytes` bounds this whole option
result. A label may have `label_truncated:true`; use its observed index.

`choices` is a required array of at most 32 objects. Each object has exactly one
`label`, `value` or `index`. Labels collapse whitespace; values are exact and may
be empty. A single-select needs one choice. A multi-select accepts an exact set,
including `[]` to clear it. Missing, ambiguous, duplicate or disabled choices
fail before selection. Use visible controls for custom ARIA dropdowns instead
of treating them as HTML selects.

After the normal fresh-target/stability/hit-testing and live permission checks,
selection uses one controlled DOM transaction and one input/change pair. It
returns `input_method:"dom_select",events_trusted:false,verification:"field_state"`.
Keyboard navigation is not substituted: the real WebView2 probe showed that
it can commit unwanted intermediate choices. An unchanged exact set emits no
events. These DOM events are not native trusted input, and field verification
does not prove a website's business result; inspect that result separately.

Option nodes, labels, values, disabled states, select type and final selection
are checked again after page handlers. A page override returns
`action_uncertain` with `effect_may_have_occurred:true`, never an automatic retry.
Metadata has bounded length/total inspection cost. Submitted choice data stays
out of public receipt arguments and UI journals. The same `browser_observe`
read-only / `browser_act` mutation permissions and C/JS contracts apply.

Input waits for actionability and stable geometry, checks hit testing, document,
one writer, cancellation and user takeover. Fill/check verify the field state
without returning its value. Other actions report `native_delivery`; verify the
website's business result in the returned observation. Distinct errors include
`stale_document`, `element_ambiguous`, `element_obscured`, `focus_changed` and
`unsupported_geometry`. Inspect any `effect_may_have_occurred` outcome before
another effect. Only a user can return manual control.

Managed tools `browser_tabs/observe/wait/dialog/screenshot` are read-only; `browser_open/act/navigate/dialog_respond`
use the existing external-service permission. Full access adds no second
approval system. Ordinary tool failures remain feedback for the Agent.

Page data is untrusted. Observations omit form values; public receipts omit
stored arguments; page results/screenshots are ephemeral in the UI journal.
The operation journal retains identity/scope metadata instead of private results.

## Bounded screenshots

```javascript
const shot = await browser.screenshot({ browser: tab.id,
  document: page.document, mode: "element", target: { ref: page.elements[0].ref },
  format: "jpeg", max_width: 1280, max_height: 960, max_bytes: 131072 });
// shot.image: { media_type, base64, width, height, bytes }
```

`mode` is `viewport` (default), `element` or `full_page`. A target implies
`element` and requires a fresh observed document. The element must already be
visible; capture does not scroll the page. Viewport/full-page may omit document,
but supplying it rejects accidental capture of a newer page. Coordinates in
`region` describe the CSS crop of the source document; image dimensions describe
the bounded raster. There is no arbitrary CDP/script or clip parameter.

PNG is the default; JPEG is optional. Width/height limits are 64–2048 (defaults
1280/960), and the byte budget is 8192–262144 (default 131072). Full-page source
area, frame traversal, raster allocation, concurrent capture and elapsed time
are bounded. Unsupported geometry, excessive area or a changed document returns
an explicit error. Cancellation/takeover stops delivery, and native resource
admission remains held until its callback drains.

The managed `browser_screenshot` tool returns a real image through mdo's existing
image-result channel, plus small text metadata. C/JS SDK callers receive the
image envelope in memory and release its receipt normally. Image bytes are not
written to the UI journal, even for tiny captures. Invalid envelopes become an
ordinary failed tool result, and the Agent can continue with another call.

Known form controls, editable regions, `data-mdo-private`, opaque frames and
closed custom-element content are masked on the trusted UI canvas using bounded
pre/post layout scans. Mask eligibility follows rendered pixels, including
inert/aria-hidden controls and controls whose `hidden` attribute is overridden
by CSS. It does not reuse the narrower interaction-visibility predicate.
This is not a universal secret detector: arbitrary page
text, canvas content or moving sensitive content may require additional care.
Do not capture credentials or treat page pixels as instructions. No screenshot
is saved to disk by this API; file artifacts/transfer are a later stage.

Internal local-frame capture now labels opaque iframe masks with an exact local
element reference and reports node/viewport/scroll usage. Native `capture_owner`
resolves that same node in its parent isolated world and returns fractional
content geometry plus the owner document/reference, including unsupported
geometry responses. The internal coordinator uses those bindings for safe mask
composition. Its real pixel gate covers readable nested content, hidden input
values, exact nested crops and child-navigation withholding; the normal adapter
still waits for its public C/JS/managed integration regression.

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
view closure. The real expiry gate has been verified in WebView2: the page
resumed after dismissal, the obsolete identity was rejected, and fresh
observation still worked.

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

Closing a broker browser also removes its owned browser-renderer panels in the
same saved-state update and cancels pending UI work for that exact task/browser.
It does not close unrelated tabs or custom panels that merely contain the same
ID as data. New panel references/commands for a closed browser are rejected;
late UI acknowledgements cannot revive a cancelled receipt. An input already
delivered before closure may have taken effect, so cancellation is not rollback.
Closing/hiding only a panel remains distinct from ending its browser session.

## File snapshots for transfer staging

The host now provides `files.digest({path,space?,revision?,max_bytes?})` and the
generated C `mdoFilesDigest` helper. It returns regular-file metadata/revision
and complete lowercase SHA-256, without contents. The default byte budget is
32 MiB and the maximum is 64 MiB. Directories/links, changed identities and
oversized/growing files do not produce a partial digest.

`files.read` and `fs.read` accept an optional `revision` precondition. With it,
the host checks the named entry and opened handle before/after the bounded
range, and returns no bytes on mismatch. EOF is explicit; an offset beyond the
selected size is an error. Reads without a revision retain their existing
behavior. Windows snapshot calls deny write/delete sharing while their handle
is open; POSIX relies on identity/metadata checks. A revision is not a permanent
lock across separate calls, and the staged copy must still match the complete
digest before it can be assigned to a website.

These calls run against the original SDK task's filesystem. They do not treat
a server path as a path on the browser's displaying device. UI continuations
preserve the original C task's absolute workspace in their frozen context.
Relative source paths resolve there, even when a different task is selected.

The Windows host now includes the private native staging foundation: bounded
`begin/chunk/seal/status/cancel` requests, opaque browser/document-bound tickets,
canonical 64 KiB byte chunks and complete SHA-256 verification. Its private
current-user/SYSTEM cache uses per-reservation leases and cleanup inventories.
Unknown/renamed entries and directory reparse points block collection instead
of being followed or guessed. Unassigned expired/cancelled and closed reservations
are reclaimed, and a new host can collect an abandoned process's files.

## File-input uploads

`browseruse.upload` / `mdoBrowserUseUpload` accepts `action:"set"` with an owned
browser, fresh document, precise target and `files:[{path,space?,name?}]`. Sources
belong to the original task host. `space` supports native/workspace/home/application;
the default resolves relative paths against the original workspace. An optional
safe `name` overrides the source basename. Arbitrary scripts, raw file bytes and
browser-device paths are not part of this public contract.

The displaying device must advertise `file_input_capability` and
`upload_capability` before any task file is read. Source snapshots are pinned by
revision, streamed in 64 KiB ranges and sealed against complete SHA-256. Limits
are sixteen files, 32 MiB each and 64 MiB total, within the normal 16 KiB UI
argument budget. Native retained-file limits are sixteen reservations per view,
thirty-two per host, 128 files and 128 MiB. Resource exhaustion is explicit.

```javascript
const page = await browser.observe({ browser: tab.id });
const assigned = await browser.upload({ browser: tab.id, action: "set",
  document: page.document, target: { role: "fileinput", name: "Attachment" },
  files: [{ path: "report.txt", space: "workspace" }], timeout_ms: 30000 });
const ticket = assigned.transfer.ticket;
const status = await browser.upload({ browser: tab.id, action: "status", ticket });
// Submit through the site's observed controls and verify its consumption first.
await browser.upload({ browser: tab.id, action: "release", ticket, confirmed: true });
```

`set/clear/release` use durable operation identities in the JS client; `status` waits
for its read-only UI receipt. Raw C calls return the same kind of receipt as
other queued services. Agent tools are `browser_upload` (set/clear/release, external
effect) and `browser_upload_status` (read). They use the existing live permissions;
full access needs no additional prompt and read-only tools cannot assign files.

FileList verification confirms exact names/sizes/count, not website acceptance.
Hidden inputs, open Shadow DOM and native-routed cross-origin frame inputs work
on the enhanced Windows backend.
Disabled/inert, stale, non-file, directory and single-file capacity mismatches
fail before assignment. Assigned bytes survive cancellation, navigation and
staging expiry because a website can submit its File objects later. Retain the
ticket until `release(confirmed:true)` after consumption or close the browser.
Release is refused while assignment is in flight. A dialog/new-window checkpoint,
timeout or lost native reply can mean the files were already assigned; inspect
the page and transfer instead of repeating that effect.

For `action:"clear"`, provide a fresh document and exact input without `files`,
`ticket` or `confirmed`. It uses a fixed isolated DOM transaction to empty the
value, emits one input/change pair and checks the final FileList. Chromium's
empty `DOM.setFileInputFiles` does not clear it, so the result explicitly says
`input_method:"dom_file_clear",events_trusted:false`. An empty input is a no-op;
repeating an operation returns its receipt even if a later file was selected.
Clears have no filesystem reservation. Their in-memory receipts are limited to
32 per view and 64 per host, expire by the operation deadline and do not consume
the independent file-staging ticket/byte budget. Clearing never releases a
previous upload: the site can retain old File objects. Status is historical
transfer state; a fresh observation provides the current `selected_count`.

Directory uploads and Android transfer support remain unfinished. Windows
OOPIF uploads and intercepted downloads have separate real-browser gates.
Unsupported devices fail before source reads; there is no browser-device path fallback.

## Intercepted file pickers

The Windows dispatcher acknowledges native interception before Agent input. A
click can return `outcome:"file_chooser_open",completed:false,choosers:[...]`;
this is a checkpoint, not a completed upload. It does not repeat the click or
open an OS dialog. Mouse acknowledgement can precede the picker event, so the
dispatcher also inspects state after input and observation. Later asynchronous
page activity is inspected before subsequent input. Native delivery never
claims website completion.

Use `browseruse.chooser` / `mdoBrowserUseChooser` (`list` or `cancel`). The JS
client's `chooser` waits for a read receipt or uses a durable cancellation
identity. Managed tools are `browser_choosers` and `browser_chooser_cancel`.
List is read-only; cancellation is an external effect and respects live tool
permissions. Foreign task/browser tickets are rejected. Observation remains
available while a picker is pending and includes its opaque metadata.

```javascript
const clicked = await browser.act({browser:tab.id,document:page.document,
  op:"click",target:{role:"button",name:"Choose file"}});
if (clicked.outcome === "file_chooser_open") {
  const current = await browser.observe({browser:tab.id});
  const picker = (await browser.chooser({browser:tab.id,action:"list"})).choosers[0];
  const selected = await browser.upload({browser:tab.id,action:"set",
    document:current.document,chooser:picker.id,
    files:[{path:"report.txt",space:"workspace"}]});
  // Or cancel this exact picker instead of assigning files:
  // await browser.chooser({browser:tab.id,action:"cancel",chooser:picker.id});
  // Retain selected.transfer.ticket until verified website consumption.
}
```

`chooser` replaces `target` only for upload `set`. The actual input is resolved
and pinned before source reads; its document, picker and reference are checked
through native staging/assignment. Cancelled, expired or replaced tickets
cannot deliver files. Successful assignment consumes that picker once without
also emitting cancel. The same file-operation receipt cannot select another
input. Hidden/shadow/native-routed frame inputs work; unsupported frame/picker kinds
fail before source reads and require manual control.

Cancelling preserves the preexisting FileList and retained website File bytes.
It emits one fixed isolated `cancel` event (`events_trusted:false`), which may
open a dialog or popup and yield another checkpoint. Picker leases are at most
120 seconds. Pending pickers block competing actions; explicit cancellation,
navigation, expiry or browser close releases their metadata. Native policy or
object cleanup failure fences further input rather than retrying indefinitely.

The trusted side pane shows Cancel selection and Take over browser controls.
Takeover disables interception after in-flight input releases drain and
cancels the old picker without clearing files. Busy state includes both
deferred phases. To use the OS picker, the user clicks the website again after
takeover. Returning control never replays that click or reopens the picker.


## Intercepted downloads

`browseruse.download` / `mdoBrowserUseDownload` share six actions:
`list`, `get`, `accept`, `wait`, `cancel`, `release`. Except `list`, each requires
an exact opaque `download` ID from this owned tab. Only `accept` takes
`max_bytes` (1..64 MiB; default 32 MiB) and `transfer_timeout_ms` (1000..600000;
default 60000). `timeout_ms` instead bounds this UI call/wait, up to 60000.

Browser Use tabs require native interception before their first URL or adopted
popup response runs. The original POST/body/cookies stay in the browser; the
host never navigates a second time to recreate a download. Older hosts reject
the capability before sending the initial URL. Ordinary manually opened browser
tabs keep their existing native behavior. Agent input and explicit navigation
also acknowledge native interception first.
A response can return `outcome:"download_pending",completed:false,downloads`.
It is an existing browser response, including POST/blob; never re-click or
refetch its URL. Pending offers fence subsequent input/navigation, while fresh
observation includes download metadata. Immediate post-input callbacks are
coalesced for at most 150 ms; arbitrary later website activity is inspected by
later calls. Native input delivery is never claimed as file completion.
If a submitted response starts navigation or interrupts the final page read,
the same input's metadata checkpoint can wait up to 1100 ms for its download
identity, within the call deadline. It performs no extra input/evaluation and
still obeys cancellation, manual takeover and modal/window boundaries.

```javascript
const checkpoint = await browser.act({browser:tab.id,document:page.document,
  op:"click",target:{role:"button",name:"Export"}});
const response = (await browser.download({browser:tab.id,action:"list"}))
  .downloads.find(row => row.state === "pending");
await browser.download({browser:tab.id,action:"accept",download:response.id,
  max_bytes:8*1024*1024,transfer_timeout_ms:60000});
const completed = await browser.download({browser:tab.id,action:"wait",
  download:response.id,timeout_ms:10000});
// completed:false means the same response is still pending/receiving.
// A completed result carries exact size and SHA-256, without raw bytes/paths.
// Keep it until it can be saved; release discards its retained bytes.
```

`wait` never accepts, replays or cancels a response. Its bounded poll can return
`completed:false` and `download_pending`/`download_in_progress`. A failed/cancelled
response returns a tool error with its native receipt/reason/source. `accept`
returns current state; it does not wait for all bytes. Native completion is
verified against physical size and SHA-256 before `verified:true`. Progress byte
limits are cancellation thresholds, not hard filesystem quotas; unknown-length
responses can overshoot before callback delivery and are not exposed if over limit.

C calls return scoped UI receipts read through `browseruse.result`. The JS
`download` helper uses durable identities for accept/cancel/release and read
completion receipts for list/get/wait. Managed tools are `browser_downloads`
(read: list/get/wait) and `browser_download` (external effect: accept/cancel/release).
Read-only tasks may inspect their tabs but cannot change downloads. Full access
adds no extra confirmation. Takeover cancels unfinished owned responses; only
the user returns control. Waiting-call cancellation does not delete a download;
use explicit `cancel`, wait for terminal state, then `release`.

This operation retains verified bytes in the browser's private cache. Use the
separate `save` operation below to deliver a file into the original workspace;
download completion alone does not prove file delivery. No private cache path, response URL query, cookies
or raw bytes are part of this public contract. Closing a browser reclaims its
owned native bytes. A terminal release that loses confirmation requires receipt/
list inspection rather than replaying a download.

A download-only popup may request page closure before its response is consumed.
The browser record remains while any response identity/bytes are owned, including
an unpublished identity candidate. Page commands then return
`outcome:"download_retained",page_closed:true,completed:false,downloads`; they
do not evaluate or send input to that page. Download inspect/accept/wait/release
remain available. Releasing the last response resumes automatic closure after
the UI acknowledgement, so closure cannot cancel the release receipt. Explicit
user/browser closure still cancels and reclaims owned responses immediately.

Native cache maintenance continues for a bounded five-second quiet window
after a transfer owner retires, using backoff. This catches late empty-directory
creation after cancellation/closure. It reuses the exclusive-lease and validated
inventory collector; active leases and unexpected/reparse layouts are preserved.

## Task-owned byte transport

The host SDK's existing `stream.open/read/write/stat/close/release` methods are
bound to extension, device, project, session, namespace and member. Keep the
original SDK context for every producer/consumer call. A different invocation
or run in the same task can continue the stream; changing its task or private
subtask returns `context_mismatch` before reading, writing, closing or releasing.
Handles remain ephemeral across application restart. Capacity, backpressure and
explicit EOF behavior are unchanged; bytes are not model results.

Inside the stream service, read/write/close/release acknowledgements are fully
prepared before committing byte/EOF/storage changes. A result-allocation
failure returns `sdk_stream_result_failed` without consuming or appending
bytes. A failed open acknowledgement also frees its newly allocated slot.
This does not make a lost HTTP reply safe to replay: a producer that loses its
write acknowledgement must stop/cancel that receive operation and retain the
original download instead of blindly resending a chunk.

Local UI/RPC context reconstruction preserves validated `namespace` and `member`
strings (at most 512 UTF-8 bytes, without control characters). New workbench
records also retain the original `workspace`. A context supplied by the host
server remains authoritative. Missing fields in legacy records do not prove a
custom task workspace; future save controls must resolve that case explicitly.

## Verified staging publication primitive

The host SDK `files.publish` (C `mdoFilesPublish`, JS `api.files.publish`) is a
workspace-write primitive for the upcoming file consumer. It accepts `source`
and `path` as relative paths in the original task's absolute workspace,
`source_revision` from the staging file's metadata, `root_snapshot` from
`files.stat({path:"."})`, exact `size` (0 through 64 MiB), and lowercase `sha256`.
The root snapshot pins directory device/identity, so adding staging entries may
change its directory time without invalidating the selected physical root.

It holds an XRT root handle, rejects non-regular sources, reads at most 64 KiB
at a time, verifies the complete size/hash and source revision, then creates an
anchored hard link only if the destination does not exist. It verifies the new
entry's identity before reporting `published:true`. Existing destinations,
including source aliases, are never replaced. Unsupported filesystems return an
explicit error; no path-based rename/copy fallback is used. A post-publication
failure returns `may_have_completed:true`; inspect both entries before retrying.
The primitive never deletes either path on failure.

**This is a hard link, not a copy.** `source_retained:true` means the trusted
caller still owns its private staging entry; both names refer to the same bytes.
The later file consumer must safely discard its own staging name after durable
receipt/recovery checks. This primitive alone provides neither a download-save
UI nor a crash recovery ledger, and does not promise power-loss durability.
Managed tools use the separate save adapter below, rather than exposing this
low-level staging method directly.

## Bounded task-file reception

The generated C/JS host SDK exposes `files.receive.start/publish/reconcile/get/list/release`
(`mdoFilesReceiveStart`, etc.; JS `api.files.receive.start`, etc.). This is the
receiving half of workspace saving, currently implemented and verified on
Windows. It consumes an existing task-owned stream, not a URL. Start it through
`operation.start` with an explicit `sdk-` identity; a direct synchronous call is
rejected before creating staging. Operation admission receipts prevent replay
of the same request after a process crash or release.

```javascript
const original = api.bind(frozenTaskContext);
const root = await original.call("files.stat", {path:"."});
const {handle} = await original.call("stream.open", {capacity:65536});
const operation = `sdk-${crypto.randomUUID().replaceAll("-", "")}`;
await original.start("files.receive.start", {
  handle, root_snapshot:root.revision, destination:"exports/report.bin",
  size:verifiedSize, sha256:verifiedSha256,
  timeout_ms:120000, idle_timeout_ms:30000
}, operation);
// Feed bounded original response chunks with stream.write; inspect confirmed
// backpressure only. Lost write acknowledgements are not safe retries.
// Explicitly close the stream after all bytes and fresh ownership/cancel checks.
await original.call("stream.close", {handle});
await original.wait(operation, 1000); // Wait until the same operation is terminal.
const staging = await original.result(operation); // No raw file bytes.
```

`start` requires exact `size` (0 through 64 MiB), lowercase `sha256`, the frozen
physical `root_snapshot`, and a stream in the same extension/device/project/
session/namespace/member. Its total timeout is 100 through 600000 ms and idle
timeout is 100 through 60000 ms. It reads and decodes at most 16 KiB per range,
requires explicit EOF at exactly the selected size, verifies the full SHA-256,
flushes the staging file and stores a `ready` receipt with `source_revision`.
The optional `destination` records a validated relative destination before any
staging bytes are written. The receipt records relative staging name, expected metadata, original workspace
and scope; it never persists file bytes, response headers, cookies or a stream
credential. These are private host SDK results, not model tool results.

There are at most eight retained receivers with 128 MiB of total expected-size
reservations, including active, ready and unresolved records. A small Home intent
precedes exclusive staging creation; its physical file identity is persisted
before any nonempty write. Incomplete records from a previous process are
reported as `interrupted`, never resumed. A subsequent receive admission can
collect them only when the original root and staging identity are still known.
Complete `ready`, `publishing` and `published` records survive restart until
explicit release; admission never reaps complete uncertain publications. A preparatory
crash with no recorded inode may leave a zero-byte entry; preserve it for review
instead of deleting an unproven owner. Unknown inventory entries block admission.

`get/list` read metadata only. They require the original task and workspace;
`release` also refuses active operations. Cancel and wait before release. Cleanup
uses XRT's verified opened-file deletion on Windows. Replaced files/roots and
unreadable ownership records are preserved and reported as cleanup failures.
Non-Windows start currently returns `sdk_file_receive_unsupported`; its safe
namespace cleanup still needs platform implementation and verification.

Publication remains a separate explicit workspace write. Start
`files.receive.publish({id:receiverId,path:relativeDestination})` through a new
SDK operation. It persists `publishing`, the destination and original publication
operation identity before calling the verified no-overwrite publisher, then
records `published` and verified `publication_file` metadata before reporting
success. The staging source remains available. A failed final receipt write
reports `may_have_completed:true`, retaining the earlier intent and bytes.

For review, start `files.receive.reconcile({id:receiverId})` through a new SDK
operation. This inspects the original physical root and checks complete bytes
and file identity; it never creates or replaces a destination. A verified same
inode becomes `published` with verdict `verified`. A missing or different target
requires a verified original staging source and becomes `ready` with verdict
`missing` or `different_file`. Only an explicit new publication operation may
then create a new destination. The previous publication identity always returns
its retained outcome and is never replayed. Changed files or roots are preserved
and inspection fails; historical success is not proof of current unchanged bytes.

After confirmed publication, release the receiver; this removes its staging
link while preserving the published file. The actual recovery gate injects a
failed final ledger replacement after a 32 MiB publication, restarts this same
Home, and verifies destination intent, retained bytes, no-effect reconciliation
and original-identity non-replay. It does not establish power-loss durability,
a crash before the atomic link or arbitrary network-loss behavior. The official
save API/tool below and trusted browser download tray use this journaled adapter.

## Save a completed response into its original task

Use C `mdoBrowserUseSave`, service `browseruse.save`, JS `browser.save`, or the
managed `browser_save_download` tool with `browser`, `download`, `path` and an
optional `timeout_ms` (1000 through 60000; default 60000). `path` must be a new
relative entry beneath the original task workspace with existing parent
directories. No traversal, absolute path, alternate stream or ambiguous trailing
dot/space is accepted. Saving is a separate write gate from download inspection.

```javascript
const saved = await browser.save({browser:tab.id, download:completed.download.id,
  path:"exports/report.bin"});
// saved.saved === true, saved.file = {size,sha256,revision}, saved.path.
// Original response remains retained; release only after confirmed delivery.
```

The C boundary captures the original physical workspace root and private subtask
before queueing the UI action. Missing legacy roots are rejected instead of
using the selected chat. The UI producer verifies the bound task context, reads
at most 64 KiB from the retained native response, and feeds its scoped receiver
with backpressure. It never refetches a URL or exports bytes to a model result.
After exact EOF/hash verification, a separate `files.receive.publish` SDK
operation journals the destination and creates it without replacement. Receiver and publisher identities derive
from the immutable UI action identity; expired/interrupted admissions are never
replayed. Normal completion releases staging while preserving the original
native download for explicit release.

Managed registration declares workspace-write plus external-browser effects and
discovers both the exact destination path and browser-use permission resources.
Full access adds no extra prompt; read-only tasks fail before browser execution.
Manual takeover, task cancellation and displaying-target changes stop saving.
Only an explicit zero-write backpressure result is retried. Lost byte/EOF
acknowledgements abort reception; lost publication confirmation retains staging
and requests cancellation of that exact publisher, while preserving uncertainty.

Success returns `saved:true`, relative `path`, verified `file` metadata,
`source_retained:true` and `transfer` recovery identities. It contains no raw
bytes, private staging/cache path, cookies or URL query. A confirmed saved file
stays successful if cleanup fails; `transfer.cleanup_pending` identifies the
remaining work. A cancellation arriving after confirmed publication reports the
existing `saved:true` file even though the action is cancelled. Uncertain
publication requires inspection of its identity and destination before another
save; it never deletes an existing target or repeats the opening download click.
Windows is verified; unsupported receiver platforms report their explicit limit.

## Trusted download and recovery controls

The browser toolbar includes a download SVG button with a bounded record count.
Its tray stays collapsed until opened. It occupies layout space above the native
surface, updating the real native viewport bounds; it does not float over or
obscure the separate WebView window. Polling retains path inputs and focused
controls. Collapsing the tray preserves responses and records.

Pending responses can be accepted or cancelled without taking over the browser.
Verified completed responses can be saved to an explicit new path in the tab's
original task directory. Trusted user saving also works during manual control;
the Agent's manual-control and managed permission gates remain unchanged. Both
producers share one per-browser save fence, progress and exact child cancellation.
Closing this panel stops its own manual attempt; it does not attach the panel's
AbortSignal to independent Agent actions. Cancel does not remove a committed file.

The recovery group lists only the original extension/device/project/session/
namespace/member/workspace. It distinguishes active, interrupted, ready and
uncertain/published records. `核对结果` performs no file creation; a ready record
can be explicitly saved with a new operation identity and no overwrite. Cleanup
requires an inline second confirmation for retained staging or an unsaved native
response. Saved files are preserved. Errors expose bounded cause codes with
actionable text; raw internal paths or transport messages are not displayed.

Legacy tabs without an original workspace offer `读取原任务目录`, followed by
`确认使用此目录` after review. Saving stays disabled until the binding is confirmed.
If the original task or directory cannot be established, the response is retained
and an explicit error is shown; the current task is never substituted. Nonexistent
default-root preparation, remote save transport and Android transfers still need
their own implementation or final verification. The mounted WebView2 UI gate proves exact
manual-save bytes, acceptance without takeover, focus retention, actual native
viewport bounds and recovery publication/reconciliation. A normal-main-window
desktop screenshot check was attempted but failed with `failed to activate
captured window` and `FrameArrived timed out`; it is not recorded as passed.

## Recover a legacy tab's original task directory

The shared host C/JS SDK now provides `browser.workspace.lookup` and
`browser.workspace.adopt` (`mdoBrowserWorkspaceLookup/Adopt` and
`api.browser.workspace.lookup/adopt`). They are trusted extension/UI services,
not an Agent tool for changing another task's directory.

Lookup takes `{id}` and reads the exact original project/session from the
persisted session catalog without opening an Agent/model. The caller must match
the original owner, device, task, namespace and member. Only a missing/empty tab
workspace can be recovered. It returns browser `revision`, `session_revision`,
`workspace` and physical `root_snapshot`. No current-chat/project-default fallback,
directory creation or browser navigation occurs. Missing/trash tasks, missing or
replaced/reparse roots and malformed context fail explicitly.

After reviewing the returned directory, call adopt with `{id,revision,
session_revision,workspace,root_snapshot,confirmed:true}`. The host re-derives
the original task/root, checks both revisions and physical identity, then
atomically fills only `context.workspace` and `context.workspace_snapshot`.
It cannot replace a nonempty binding. Private scope, tab URL and retained browser
response stay unchanged. A lost reply requires reading `browser.get`; the next
call must not blindly repeat adoption.

The physical pin persists with the browser. The C save boundary rejects a later
root replacement before reading any response bytes or queueing browser actions;
ordinary directory size/time changes do not invalidate its identity. The trusted
UI uses the same pin before starting a manual byte stream. A lost binding reply
is recovered with a strict original-scope `browser.get`, then a new bound SDK
client; adoption is not retried. Model/context selection in the chat is unrelated
to this frozen binding. The actual UI gate confirms one retained response is
accepted, bound to its original task and saved without another download request.
The binding, private scope and physical pin survive restart; the tab returns
suspended and requires normal explicit browser reattachment.
