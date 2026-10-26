# Implementation progress

Goal: implement the Browser Use design agreed on 2026-10-26, using one sequential
development stream and the single deployment inside this project.

| Stage | Work | State |
| --- | --- | --- |
| A0 | Pin mdo, bootstrap reusable development environment, real startup | Complete |
| A1 | Shared contracts and actual C plugin/SDK registration | Complete |
| B | Windows observation, element references and native input | In progress |
| C | Waiting, action identity, ownership, cancellation and recovery | In progress |
| D | Frames, tabs, dialogs, upload/download and visible UI | In progress |
| E | Android implementation and explicit platform limits | Pending |
| F | Plugin lifecycle, end-to-end checks and deliverable | Pending |

## Working boundaries

`D:\GIT\mdo-browser-use` is the development root. `D:\GIT\mdo` is read-only
bootstrap input; its existing `app/xs.json` and untracked tests are not copied as
local modifications. Do not rebuild or replace an existing user's running app.

Tests reuse `.mdo/home` and fixed project fixtures. No per-test temporary Homes,
full application copies or downloaded browser distributions are used. Only the
latest generated result is kept unless a failure needs investigation.

## A0 — reusable development deployment

- Pinned mdo at `a7c736458df003954ba7b88ed191f4b985419686` and reused the
  SHA-256 checked existing xs host. A single sparse source checkout shares local
  Git objects instead of copying the source history or dependency build trees.
- Generated the actual mdo unity/SDK source using upstream `prepare`, then
  started the real xs/TCC HTTP application with one worker and this project's Home.
- `tests/test_environment.py` checks the real bootstrap, plugin/browser/operation
  service discovery, bound configuration, and pinned source identity.
- This is a development deployment, not a published client or completed Browser Use.
- The first plugin installation caught a deployment root error: bootstrap HTTP
  and SDK discovery can respond even when Home initialization failed. Moved the
  configuration into the single source app directory so `/app/default-home`
  resolves correctly; readiness now also requires a nonempty application root
  and a working plugin manager. The first shallow readiness check was insufficient.

## A1 — native plugin and shared tab service

- Added an installable `mdo.plugin.v1` package, Skill and C entry using the public
  Application SDK. Real plugin apply/plan/commit and a process restart compile
  and register `browseruse.tabs` through TCC.
- One JSON definition generates the native service description, C convenience
  header and JavaScript declaration. The C boundary validates the used schema
  subset and rejects unknown/incorrect arguments before side effects.
- Implemented owned list/get/create/reveal/close. Explicit display-client
  selection is required. The wrapper additionally checks task/extension scope
  when listing with a client filter, and rejects cross-task access and embedded
  URL credentials. A created tab record is honestly reported as suspended.
- Seven Python tests cover the actual SDK/deployment and installed C plugin;
  one Node test calls the same service over HTTP and compares the discovered
  contract. The headless display-client fixture tests records and ownership;
  actual native pixels, page observation and inputs are not yet validated.
- The tab `reveal` probe requires the standard browser type normally registered
  by the real UI, so the headless fixture explicitly supplies that contribution.

## B0 — shared page observation engine

- Added a bounded, self-contained JavaScript page runtime with readable text,
  DOM-subset roles/names, stable element references, frame-local CSS geometry,
  explicit truncation and no input/password values in observations.
- Open shadow roots and same-origin frames are traversed. Opaque frames are
  explicitly reported as inaccessible. Duplicate semantic matches return
  candidates; removed elements, changed top documents and navigated child
  frames reject old references without invalidating unrelated live references.
- `tools/native_probe.py` runs this engine inside the actual project WebView2,
  checks native screenshot output, then restores the same Home/service to its
  headless SDK mode. It creates only two small fixture pages in the existing
  app directory and overwrites one report, with no copied applications/Homes.
- The page runtime is tested directly via the existing native evaluate channel.
  This is not yet the public observe service or an isolated-world/native-input
  implementation; those remain pending. Page execution results are untrusted data.

## B1 — public observation service through the actual mdo UI

- Added `browseruse.observe/result`, matching generated C/JS definitions and a
  JavaScript completion helper. Results are scoped by extension/device/project/
  session; omitted default-project identity is normalized consistently.
- The existing workbench owns native browser creation/closing. The broker loads
  the shared page runtime from a trusted application asset; the UI request carries
  only the browser/options (82 bytes in the native probe), not the whole script.
- Tracked host overlays add the typed command/description, frozen device identity
  and small-result privacy. Even a short page result is ephemeral in the UI
  intent journal; a replayed placeholder is explicitly `result_expired`.
- `tools/sdk_probe.py` opens the normal mdo frontend, selects its actual client,
  exercises the existing ready/wait path, then obtains structured page text,
  shadow-root and frame elements through the real public C service and releases
  its receipt. This is in addition to the direct page-engine/native test.
- Automatic readiness inside observe, Agent tool execution, isolated worlds and
  reliable native inputs are still pending. Manual control currently blocks the
  underlying evaluate operation; no unrestricted evaluate tool is exposed.

## Overlay update safety

Host enhancements are now regenerated from the pinned baseline rather than
patching already-modified text repeatedly. Previous deployed hashes permit a
reviewed update; an unrecognized local edit is preserved and blocks all writes.
`python tools/apply_host.py --check` compares the single deployment to the
current reviewed files without stopping it. Existing nine Python/three JS
checks passed after reapplying the overlays.

## B2 — bounded automatic observation readiness

- Observation now waits for document readiness itself, with typed timeout,
  renderer failure, user-control and originating-call cancellation results.
  The first receipt check is unconditional, including a newly started UI process.
- The real normal-frontend SDK probe passed without a preliminary wait command.
  Targeted dispatch checks cover timeout, takeover, cancellation and renderer
  failure; none of these paths evaluates the page after its stop condition.
- Native input and the remaining stages are still in progress.

## B3 — real isolated-world execution and native input

- Built the pinned xs SDK plus its existing verified compatibility patch in one
  shared-object checkout under `.mdo/native-source`. Build objects and the host
  are reused in place. Setup preserves a locally built host with its valid
  binary receipt instead of overwriting it with the bootstrap executable.
- Windows observations now execute in a named CDP isolated world. A page-world
  imitation of the runtime does not influence observations. The context resets
  on navigation, and callbacks recheck document/control ownership.
- Added private, constrained native mouse/wheel/key/text commands using CDP
  Input. Public model operations do not expose raw scripts or CDP. Input checks
  navigation identity and manual takeover; release steps survive interruption.
- Direct real-WebView2 checks passed (49 checks): trusted click and input events,
  Unicode replacement, spoof isolation, stale navigation, takeover, earlier
  reference/frame checks and screenshot. The normal-frontend public observation
  probe also passed with automatic readiness and isolated execution.
- Element actionability, public actions, Agent tools and Android are not yet
  complete. Low-level transport success alone is not an action's semantic result.

Native API references: [CDP Input](https://chromedevtools.github.io/devtools-protocol/tot/Input/),
[Page isolated worlds](https://chromedevtools.github.io/devtools-protocol/tot/Page/#method-createIsolatedWorld),
[Runtime evaluation](https://chromedevtools.github.io/devtools-protocol/tot/Runtime/#method-evaluate).

## B4 — locator and actionability preparation

- Added exact label, placeholder, test-id, CSS and smallest-text matching across
  the bounded document/frame/shadow traversal. Invalid selectors, ambiguous
  matches and incomplete scans remain explicit failures.
- Preparation checks enabled/editable/checkable state, scrolls the target when
  requested, checks actual hit testing and translates same-origin frame CSS
  points. Unsupported transformed-frame geometry is rejected before input.
- Fill/check verification returns a boolean without returning the field value.
  The real native fixture now passes 73 checks including covered/disabled/read-only
  controls, CSS errors, semantic locators, frame and shadow hit tests.
- This is the page engine; the public operation dispatcher still needs stable
  geometry sampling, one writer, cancellation and effect-result handling.

## C0 — cancellation reaches the UI operation

- Added a scoped native `ui.cancel` service and matching generated host contract.
  The plugin checks receipt ownership before cancellation and returns only its
  sanitized status. Late acknowledgements cannot revive cancelled work.
- JavaScript abort/timeout now requests host cancellation and retains the receipt
  ID for inspection; a signal cancelled before invocation queues no work.
- Ten real SDK/C tests and six JS checks passed, including foreign cancellation,
  late delivery, sanitized cancellation replies and reader-to-host propagation.
- Already dispatched effects cannot be rolled back by cancellation. Native
  mutation dispatch will recheck cancellation between steps and report uncertain
  outcomes rather than automatically repeating an input.

## B5 — public native actions through C and JavaScript

- Added `browseruse.act` with generated C/JS schemas and wrappers: click, hover,
  fill, type, key, check and scroll. Arguments are validated before queueing;
  browser receipts remain scoped and private. The JS client waits for terminal
  outcomes and preserves uncertainty details on failures.
- The workbench shows the actual browser panel before actions. A hidden WebView
  could dispatch a click yet fail to focus its input; this was found in the real
  UI probe and fixed by using the visible native viewport.
- Actions wait for an actionable target and stable geometry, pin its reference
  immediately before input, use one writer per view, and recheck cancellation,
  document, device and user control before each native input step. Once input
  was dispatched, failures report possible effects and do not replay it.
- Fill/check verify the requested field state. Other operations explicitly
  report native delivery, leaving website-specific semantic success to the
  returned fresh observation. Checking an already-correct checkbox is a no-op;
  attempting to uncheck a radio is rejected before input.
- The actual normal mdo frontend passed ten public actions, including Unicode
  fill, empty fill, native type/key, iframe input, shadow hover, trusted click,
  checkbox/no-op and scrolling. Targeted dispatcher tests cover moving targets,
  concurrent writers and cancellation between click and keyboard input.
- Cross-request idempotency/recovery, navigation APIs, Agent tools, dialogs,
  file transfer and Android are still outstanding.

## B6 — navigation and condition waits

- Added generated `browseruse.navigate/wait` services and C/JS wrappers. Navigation
  supports explicit HTTP(S) URLs, back/forward/reload and waits for the new native
  document to finish loading. Old document references cannot silently target it.
- Waiting supports page readiness, bounded readable text, visible/hidden and
  enabled elements. Every loop respects timeout, cancellation and manual control;
  locator errors remain distinguishable from an unmet condition.
- Writer ownership now keys on the browser identity, surviving a UI view remount.
  Navigation shares this lock with input actions. Navigating away from a failed
  URL is allowed; renderer crash recovery remains a separate pending capability.
- The normal mdo UI/public C probe passed ten actions, four navigations and five
  condition waits, plus unmet-condition timeout and stale-document rejection.
- HTTP response diagnostics, persistent operation identity, Agent tools, native
  dialogs/transfers, out-of-process frames and Android remain outstanding.

## C1 — managed Agent tools and live permission integration

- Registered six real tools from the same contracts: `browser_tabs`,
  `browser_open`, `browser_observe`, `browser_act`, `browser_navigate` and
  `browser_wait`. Read-only tab actions are separated from browser mutations;
  mutating tools use the existing external-service permission and serial group.
- C tools wait through the existing cancellable SDK sleep service, keeping the
  plugin independent of XRT/OS headers and additional runtime dependencies.
  They publish actual terminal results and preserve typed browser failures.
- `tools/managed_probe.py` uses the normal UI and actual `tool.call` executor
  through an asynchronous SDK operation. It reuses two named QA sessions in the
  same Home. Read-only reads succeeded and writes were denied; full-access
  calls completed without a second confirmation system. An ordinary stale-page
  tool failure was followed by a successful observation and verified fill.
- All six tools were exercised, including navigation, condition wait and tab
  cleanup. No provider/model call was made; this does not claim a model-driven
  end-to-end task or Android validation.

## C2 — confirmed operation-receipt scope defect

- Before extending recovery, reproduced a host defect in this QA deployment:
  a different extension/session could read an SDK operation receipt by ID.
  Existing checks protected repeated admission hashes, but did not authorize
  get/result/cancel/release/list against the originating task.
- Operation receipts now carry versioned extension/device/project/session and
  collaboration identity metadata. Live and persisted reads/mutations check
  that scope; list filters it. Worker contexts retain the original device.
  Older unverifiable receipts fail closed and are not replayed.
- Two actual SDK regressions cover foreign extension/session access across the
  receipt methods, list isolation and released admission identities. Reusing a
  released identity returns its retained admission with no result, never a new
  execution. The full managed native-tool probe still passed after this fix.
- This is isolation between local SDK scopes, not evidence of an anonymous or
  cross-account network vulnerability. No production service was changed.

## C2a — frozen empty UI scope fixes a real viewport failure

- The expanded native recovery probe reproduced a second-step viewport failure
  after another QA conversation had been opened. The page was loaded and its
  renderer alive, but browser reporting failed context validation and the UI
  then hid the native view.
- Root cause: UI receipts omitted empty session/project fields. Frontend SDK
  binding filled those missing fields from the currently selected conversation,
  creating the browser panel in a different session from the browser itself.
- UI origins now freeze project/session/run/collaboration fields explicitly,
  including empty values. Invocation parameters cannot substitute a different
  origin. A real SDK regression checks empty fields and forged input context.
- The normal UI probe then passed all ten actions, four navigations and five
  waits, followed by an actual JavaScript-client native click/recovery probe.
  Viewport failure messages retain bounded mount/readiness diagnostics.

## C3 — durable shared operation recovery

- Added `browseruse.operation` and generated C/JS definitions, reusing mdo's
  durable admission journal rather than adding another queue or storage engine.
  Submit/get/cancel/release preserve the original identity and task scope.
  Different parameters conflict; released/interrupted results never replay.
- The JS client uses this service for mutations, preserves the ID when a reply
  is lost and exposes `recover`. Low-level queued receipts no longer echo their
  arguments. Confirmed argument failures remain distinct from uncertain effects.
- Three actual C/broker tests cover duplicate admission, changed arguments,
  scope, release and known no-effect failure. JS tests cover lost replies and
  expiry without replacement submission. All 17 Python/11 JS checks passed.
- A real JS client operating the normal native UI clicked once across repeated
  submissions, including after release, and completed navigation. A real restart
  of this same host/Home retained admission and reported the missing result;
  re-submission produced no duplicate tab even with a live replacement client.
- API usage and explicit recovery semantics are documented in `docs/api.md`.
  Dialogs, native transfer, frame completion, platform adapters and remaining
  lifecycle/adversarial validation still prevent a completion claim.

## D0 — native JavaScript dialogs and closing in-flight input

- WebView2 script-dialog events now retain an actual deferral for alert, confirm,
  prompt and before-unload. Each pending dialog has a non-reused GUID; metadata
  is bounded and omits prompt defaults. One dialog per view has a 120-second
  lease, with cleanup on response, view closure and host shutdown. Long-duration
  expiry validation remains part of the final lifecycle gate.
- An input interrupted by a dialog yields a native checkpoint instead of
  holding the caller until a generic timeout. Read-only metadata remains
  available under user takeover; Agent responses are blocked until control is
  returned by the user. Native capability reflects handler installation.
- A close/recreate regression found that closing only erased the registry entry:
  callbacks could retain the controller, preventing prompt replacement. Close
  now closes the controller immediately and hides its child viewport; callback
  references finish without keeping the native view open.
- The real native probe passed 108 checks, including all four dialog kinds,
  Unicode prompt response, stale identity, takeover, before-unload dismissal/
  acceptance, and closing/recreating a view with a pending dialog. It re-arms
  a trusted user gesture before a second before-unload attempt.
- This is the native layer. Shared public dialog services, Agent tools and the
  human toolbar response UI are the next stage; no complete-dialog-UX claim yet.

## D1 — shared dialogs, managed tools and trusted human response UI

- Added `browseruse.dialog`, the generated C/JS contracts, JS completion/recovery,
  and separate read-only `browser_dialog` / mutating `browser_dialog_respond`
  tools. Responses use the existing scoped operation identity and permissions.
- Interrupted observations/actions/navigation/waits yield an explicit
  `dialog_open, completed:false` checkpoint. A native modal interrupt now ends
  any pending isolated reads once, without replaying the script or subsequent
  input. Omitted prompt text preserves its private native default; explicit
  empty text clears it without exposing that default.
- The trusted toolbar response UI uses text nodes, clears replies after use,
  discards stale async refreshes and cancels layout work on disposal. A human
  response takes over first and keeps that control until explicitly returned.
- Passed 18 actual C/broker checks, 13 JS checks, 134 native WebView2 checks,
  all eight actual managed tools, and the normal UI C/JS client probes. These
  cover dialog checkpoints, Unicode response, duplicate-response admission,
  read-only denial, actual human-widget response and takeover, plus existing
  ten actions/four navigations/five condition waits. No real model call used.
- Long-duration expiry, new-window ownership, file transfers, broader frame
  support and platform adapters remain outstanding. No production deployment.

## D1a — cancelled browser creation cannot attach to a replacement

- A real create/close/recreate sequence reproduced success from an obsolete
  creation callback. It checked only whether an ID was present, so a new
  instance under the same ID could satisfy the old callback's condition.
- Environment/controller completion now checks the retained instance pointer
  and closed state before registration, cleanup or response. An obsolete
  controller is closed and its original request reports cancellation. A second
  create cannot report attachment while the first instance is still creating.
- The same native probe then passed 136 checks, including cancellation of the
  old creation and verification that the replacement kept its own target URL.
  The failed pre-fix result is retained privately under `.mdo/evidence/`.

## D2 — native popup ownership and bounded deferrals

- Removed the old behavior that navigated the opener to a popup URL. A pending
  new-window request is now retained under its exact native opener; a trusted
  client can adopt it only once into a fresh WebView in that opener's environment.
  Settings/handlers are installed before adoption, preserving normal opener,
  named-window reuse and postMessage behavior. The native layer signals page
  close requests and inherits user takeover into the child.
- Pending requests have GUID identities, a 20-second lease, per-opener/global
  bounds and the existing eight-view limit. Dismissal, timeout and source closure
  complete their deferrals. Interrupted input/isolated reads return an explicit
  `popup_open, completed:false` checkpoint instead of waiting for generic timeout.
- The real native probe passed 169 checks, including independent popup content,
  postMessage, duplicate-adoption rejection, named-window reuse, trusted close,
  five serialized window opens, actual eight-view capacity, blank-page readiness,
  takeover inheritance and real lease expiry with opener resumption. Fourteen JS
  checks also passed. No per-test application/Home was created.
- Automatic scoped tab adoption/display in the normal workbench is the next
  stage. This native milestone does not claim complete public popup support.
- Native contract reference: [Microsoft WebView2 new-window arguments](https://learn.microsoft.com/en-us/microsoft-edge/webview2/reference/win32/icorewebview2newwindowrequestedeventargs).

## D2a — native result acceptance and historical dump storage

- The disk audit found three 117–122 MiB dumps from 20:48–20:56, before the
  21:05 controller-close fix, rather than fresh output from D2. Their sidecars
  and memory-module records identify the same access-violation location in
  `EmbeddedBrowserWebView.dll`. The pinned host records first-chance exceptions;
  a dump alone does not establish process termination or a local root cause.
- Preserved all six historical dump/sidecar files in one private archive under
  `.mdo/evidence/native-dialog-pre-fix.zip`. Verified every archived byte with
  SHA-256 before removing the redundant originals, saving about 232 MiB.
- Native acceptance now rejects new/changed exception diagnostic files even
  when the page wrote a passing result, and checks host readiness afterwards.
  The full 169-check native run passed again with no new diagnostics and a
  healthy host. This does not suppress or alter native exception handling.

## D3 — broker popup admission under the original task

- Added shared host `browser.popup.admit/settle` contracts and generated C/JS
  helpers. Admission atomically checks the opener's owner/device/project/session
  and exact display client, saves one child record, and retains only a volatile
  native request descriptor. The durable record keeps its original opener.
- Repeated admission returns the same child. A changed opener, foreign task,
  different client, malformed request or unsafe URL is rejected. Settlement
  never recreates a released native ticket. Saved workbench state contains no
  runtime deferral descriptor, so a restart cannot replay pending native work.
- Three actual broker regressions cover identity, ownership, URL policy,
  volatile descriptors, settlement and repeat admission. This stage supplies
  the race-safe broker boundary; normal-workbench automatic adoption is next.

## D4 — automatic popup tabs in the normal workbench

- The workbench now reconciles native notifications and periodically checks
  hidden/visible browser views. It admits the child in the opener's frozen
  scope, adopts one fresh target, settles the volatile ticket and opens an
  ordinary browser panel. User gestures activate it; other windows stay in the
  background. Page close requests remove the exact browser and its panels.
- UI mounts and Agent dispatch share native creation. Concurrent identical
  calls share one promise, so a later reader cannot hide/reset a displayed
  viewport. A close detaches its old creation promise; the existing real
  create/close/recreate regression caught and verified this bridge correction.
  Confirmed repeated popup adoption can only attach to the same ticket/opener.
- About-blank popups with written content remain real visible pages, distinct
  from the new-browser landing state. Device/window changes stop subsequent
  settlement, and a ticket from another display client cannot be instantiated.
  Lost effects retain their child receipt; the coordinator never substitutes
  another native target or erases an uncertain child.
- Passed 21 actual broker/C checks, 20 JS checks, the native 169-check run with
  no new exception diagnostics, the actual normal UI C/JS probe and eight
  managed tools. Verified independent child content/postMessage, panel display,
  page-close cleanup, a real action in a written blank popup, and managed-task
  isolation of the child. Existing ten actions/four navigations/five waits pass.
- File transfers, complete frame/observation coverage, richer actions/screenshot
  services, Android and the final lifecycle/package gates remain unfinished.

## S0 — bounded screenshots through C, JS and managed image tools

- Added the shared `browseruse.screenshot` service and read-only
  `browser_screenshot` tool. Viewport, visible-element and bounded full-page
  capture support PNG/JPEG, explicit dimensions/byte budgets and unchanged-page
  checks. Native CDP calls are typed; callers cannot provide scripts or CDP.
- Native capture holds one physical request budget until its callback drains,
  including cancellation. Trusted UI raster validation runs before decoding;
  bounded pre/post scans mask known form controls and opaque content on canvas.
  Arbitrary page text/canvas is not covered by a universal privacy guarantee.
- Managed calls use the existing image-result channel with small text metadata;
  malformed images become ordinary tool errors. C/JS receive in-memory image
  envelopes. UI journals exclude screenshots even when their bytes are tiny.
- Passed 23 Python and 23 JS checks; 192 actual native checks with no new
  diagnostics; normal mdo UI C/JS viewport/element/full-page calls; and nine
  managed tools including actual PNG/JPEG, read-only capture, and failure then
  recovery. Verified masked synthetic pixels, fixed controls on full-page
  capture, cancellation and bounded images. No live model/provider call used.
- A state audit found a separate existing close defect: broker browser closure
  can leave its panels and queued UI work behind. Fixing that lifecycle path is
  next. Transfers, richer input, complete frames, Android and final packaging
  remain unfinished; no release or completion claim.

## L0 — closing a browser cleans up panels and pending UI work

- Reproduced both defects through the actual broker: `browser.close` left its
  browser-renderer panel behind and left queued UI work pending. A late success
  acknowledgement could otherwise complete that work after closure.
- Closure now saves browser/panel removal together, then cancels matching work
  under the established workbench-to-UI lock order. Command admission keeps the
  same workbench lock through enqueue, eliminating the check/close/enqueue race.
  Browser panel open/update rejects removed or foreign browser references.
- UI cleanup accepts an already-removed panel's 404 and removes stale views
  instead of remounting error placeholders. Unrelated browsers, custom panels,
  other task receipts and already-terminal results are preserved. No bulk
  deletion of pre-existing saved records or user state was performed.
- Passed 26 Python broker/C checks and the actual normal UI C/JS probe,
  including a live pending wait cancelled by closure, atomic panel cleanup,
  rejected stale references and late acknowledgements. Existing page-close,
  popup, screenshot, dialog, navigation and action cases remain passing.
  Transfer/frame/platform/package work remains unfinished.

## L1 — actual native dialog expiry gate

- Added an optional `native_probe.py --dialog-expiry` gate instead of shortening
  the production timer for tests. The same WebView2/Home waited for its actual
  lease: the confirm expired after 120931 ms and dismissed as false.
- Verified that the pending identity stayed stable during the lease, its late
  response was rejected after expiry, page execution resumed and a fresh isolated
  observation succeeded. The complete native run passed with 322 assertions
  (including repeated identity checks) and no new exception diagnostics.
- The gate restores this same service to headless mode. Android and the remaining
  transfer/frame/richer-input/package stages are still outstanding.

## B7 — native double, right and middle mouse gestures

- Added typed `double_click`, `right_click` and `middle_click` to the shared
  C/JS action contract and existing managed `browser_act` tool. Native input
  validates the button/count and sends bounded press/release sequences; releases
  remain part of interrupted-input cleanup. No page-dispatched mouse event is
  used as a replacement for native input.
- Capability admission rejects an older/unsupported host before page evaluation,
  scrolling or input. Existing document, stable geometry, hit testing, writer,
  takeover, cancellation and durable-operation identity checks remain in effect.
- Passed 26 Python and 25 JS checks, 210 actual native checks without new
  diagnostics, 13 public C/UI actions and actual managed tool calls. Verified
  trusted dblclick/contextmenu/auxclick events and malformed native arguments.
  The initial low-level fixture run timed out using cached coordinates; its
  failure report is retained privately. The probe now prepares and hit-tests a
  fresh target, as the production action dispatcher already does.
- Drag/select, vision coordinates, transfers, full frames, Android and packaging
  are still pending. No live model call or production release was performed.

## K0 — verified native selection keyboard primitives

- Added bounded Control+Home/End/ArrowUp/ArrowDown/Space and Space native key
  mappings. The real WebView2 probe selected a single option while skipping
  disabled options/groups, and selected an exact multi-select set using native
  keyboard input. Both emitted trusted change events.
- The native run passed 223 checks without new diagnostics. Low-level fixture
  input now re-prepares its point after earlier scrolling rather than reusing
  an observation's historical coordinates. This commit supplies the native
  primitives only; the scoped public selection action, option discovery,
  change detection and managed-tool validation are the next stage.

## Dev1 — Windows test cleanup tolerates an already-ended process tree

- A normal UI SDK run completed its checks, but cleanup failed when Windows
  `taskkill /T` returned errors for Chromium children already ending. The host
  PID and project browser processes were confirmed absent; the stale saved PID
  prevented restoration of the one headless deployment.
- On tree-command failure, the helper now checks the root PID again. It accepts
  only absence as termination, preserves live/reused identities and never tries
  to kill an unrelated process. Three focused process-control tests cover these
  cases. Public selection development remains uncommitted until its full gate.

## L2 — popup queries release expired native deferrals immediately

- A real native run caught an expiry-boundary inconsistency: listing filtered
  out an expired ticket before the queued timer released its deferral, so the
  visible list/error and actual opener completion did not describe one state.
  The failed result is preserved privately in `.mdo/evidence/`.
- Popup requests now run the existing expiry cleanup before listing or acting
  on a ticket. The real 20-second lease, opener resumption, expired-ID rejection
  and the full native run then passed (271 assertions, no new diagnostics).
  The timer remains the idle cleanup path; the production lease was unchanged.

## B8 — exact select transactions and paged option discovery

- Added shared `browseruse.act(op="select",choices=[...])` and read-only
  `browseruse.observe(mode="options",document,target,offset)` through the C/JS
  services and existing managed tools. C validation now covers bounded typed
  arrays; choices resolve by exactly one label/value/index. Discovery returns
  labels/indices/disabled states without stored values or selected state.
- The native prototype proved that navigating to a choice can commit three
  intermediate change events. The public action instead applies one complete
  selection and one standard DOM input/change pair. It explicitly reports
  `input_method:"dom_select",events_trusted:false`; unchanged sets emit nothing.
  Fresh target geometry, scope, permissions, takeover/cancellation and durable
  operation admission remain enforced. Website business success is separate.
- Selection verifies the final set, option node identities/labels/values/disabled
  state and select type after handlers. Page overrides return an uncertain
  effect, and are never retried automatically. Inspection/mutation count,
  metadata, choice and result-byte budgets fail explicitly before effects.
- Passed 31 Python and 28 JS checks, 282 actual native assertions with no new
  diagnostics, 17 public C/UI actions, the actual JS client (duplicate selection
  delivered once), and nine managed tools. Covered single/multiple/clear,
  unchanged sets, disabled groups, ambiguity/duplicate choices, page overrides,
  oversized metadata/count, pagination/byte limits, failure recovery and real
  read-only denial. No live provider/model call or new application/Home used.
- Drag, vision coordinates, upload/download, complete cross-origin/frame support,
  Android and final lifecycle/package gates remain outstanding.

## D5 — bounded native pointer and HTML5 drag lifecycle

- Added one native drag job per view, with exact request-key cancellation,
  deadlines, paced pointer motion and release/drag cancellation/interception
  cleanup. A cleanup failure retains the blocked native slot until view closure.
  Competing native input/capture is refused, and bridge busy state includes drag.
- Pointer gestures emit trusted events. HTML5 drag data is obtained from that
  page's intercepted browser event and delivered through typed native drag
  events. Caller-supplied data, file lists, oversized payloads/items and late
  data arriving after cleanup cannot become a successful drop. Data stays in
  memory and is never echoed in result text.
- Passed 342 actual native checks without new diagnostics: pointer/HTML5
  delivery, matching/mismatching cancellation, takeover, deadline-before-input,
  oversized data rejection then recovery, and closing/recreating a view during
  a gesture (including a busy renderer handler). Native creation checks now wait
  for the requested URL within the normal 10-second readiness budget and record
  elapsed time, rather than treating an initial about:blank snapshot as a final
  replacement result. Failed fixture results remain private evidence.
- This is the native foundation. Shared C/JS `act` integration, final target
  preparation and managed-tool drag tests are the next stage; transfers,
  complete frame support, Android and packaging remain unfinished.
- Protocol contract: [official Chrome DevTools Input definitions](https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/master/json/browser_protocol.json).

## L3 — initial navigation remains pending through its matching completion

- A real first-observation run read an initial empty document; a replacement
  view also reached its target title/URL while retaining a mismatched loading
  state. Requested nonblank navigation now has an explicit pending/start gate.
  Default blank starts/completions cannot consume it, and stale completion IDs
  cannot finish a newer navigation. Explicit blank popups remain supported.
- The toolbar preserves the requested address while this initial navigation is
  pending. The workbench and automation dispatcher both treat it as loading.
  Failed native event identity reads stop the request with an explicit error.
  Twelve bounded event metadata records aid diagnosis without URLs or content.
- Compiled the actual native gate against default/late blank events, stale and
  overlapping IDs and the bounded history. Dispatcher/model checks also pass.
  Rebuilt the single host and passed the real WebView2 run (360 assertions,
  no new exception diagnostics); the preceding public C/JS/managed run passed
  automatic first observation, navigation, written blank popups and dragging.
- Public drag integration and transfer/frame/platform/package work remain in
  progress; this entry does not declare the full Browser Use goal complete.

## L4 — release intercepted HTML5 drag sources after rejection

- A strengthened recovery check found that `Input.cancelDragging` alone left
  an intercepted HTML5 source active after payload rejection. The next gesture
  returned pointer delivery without a new HTML5 start/drop. The failing result
  and bounded source start/end metadata are retained privately as evidence.
- Cleanup now also sends `dispatchDragEvent(dragCancel)` for intercepted sources,
  using an empty payload even when the original data was rejected. Late first
  interception during cleanup schedules that source cancellation before release.
  No cleanup branch issues a drop or repeats the original action.
- Local/non-HTTP URI lists, credential URLs, download payloads and file lists
  are rejected before drop. Gesture viewport resizing cancels its old coordinates.
  A compiled test exercises the actual URI policy, including values Chromium
  can strip before interception. The native fixture checks actual received data.
- Rebuilt and passed 360 real native assertions with no new diagnostics,
  including successful HTML5 drop after rejection, resize/cancel release and
  file URI filtering. All checks reuse the same host/Home/output directories.
- Implementation evidence: [Chromium input handler](https://raw.githubusercontent.com/chromium/chromium/main/content/browser/devtools/protocol/input_handler.cc).

## B9 — shared C/JS drag actions and managed Agent integration

- Added `browseruse.act(op="drag",target,destination)` to the generated shared
  contract, C adapter, JS completion client and existing `browser_act` tool.
  Destination validation rejects absent/imprecise/mixed/unsafe locators before
  admission. Both targets retain the original frozen task scope.
- Preparation waits for two stable, visible hit-tested targets, then pins and
  rechecks their references immediately before one native gesture. Old backend
  capabilities fail before reading/scrolling. This stage requires both targets
  visible together; automatic scrolling during a held drag remains unfinished.
- Cancellation reaches the exact native key, including exceptional exits;
  native cleanup remains admitted until callbacks finish. Results distinguish
  pointer/HTML5 native delivery from business success, and retain conservative
  uncertainty. Normal operation identity/recovery prevents duplicate dragging.
- Final build passed 35 Python and 33 JS tests, 360 actual native assertions
  with no new diagnostics, 19 public C/UI actions and the real JS client. The
  duplicate retained HTML5 operation produced one drop. All nine actual managed
  tools passed, including pointer/HTML5 delivery and read-only denial on an
  owned tab, so foreign ownership cannot hide that permission check.
- Contracts/deployed overlay checks pass. Reinstalled the plugin and Skill in
  the single Home; tests used no live model call or additional app copy.
  Vision coordinates, offscreen dragging, file transfers, complete frames,
  Android and final lifecycle/package validation remain required.
