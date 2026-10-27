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

## D6 — bounded source snapshots for file transfers

- Added host `files.digest` with generated C/JS discovery/contracts. It hashes
  one regular file with a 64 KiB buffer, a 32 MiB default/64 MiB maximum budget,
  inherited cancellation/deadline and named/handle identity checks before/after.
  It returns metadata plus SHA-256, never contents or a digest of a prefix.
- Fixed an existing substrate gap needed by uploads: `files.read` already
  advertised revision but ignored it. Both files/fs range reads now enforce an
  optional revision before and after reading, reject out-of-snapshot offsets,
  and return no bytes on conflict. Ordinary unconditioned reads retain their
  behavior. Windows snapshot handles allow read sharing only during each call.
- The actual TCC/XRT tests cover binary/empty hashing, exact multi-chunk
  reconstruction, bounded EOF/budgets, directory rejection, same-size/same-time
  path replacement, and an open Windows writer followed by recovery. Full Python
  run: 41 tests, 40 passed, one symlink case skipped because this process lacks
  symlink creation privilege (1314). That unavailable case is not a pass.
- Applied only reviewed overlays to the one pinned deployment; generated
  `mdoFilesDigest` and overlay checks pass. Browser-native staging/assignment,
  downloads and cross-device transport integration remain the next work.

## D7 — private browser-device byte staging and crash-safe collection

- Added internal native upload staging (begin/chunk/seal/status/cancel), bound
  to the exact view/document. It accepts bytes and safe basenames, never task-host
  filesystem paths. Canonical Base64 chunks are capped at 64 KiB; a file is at
  most 32 MiB, a 16-file reservation at most 64 MiB, and this host reserves at
  most 128 MiB/eight tickets with two per view. Leases are 1–120 seconds.
- Same-key/same-manifest admission and matching chunk replay reuse their native
  state. Conflicts, gaps, excessive data, bad hashes and sealed writes fail
  explicitly. Complete SHA-256 is checked against the read-locked staged files.
  Partial initialization is never reused as a prepared admission. Closing,
  navigation, takeover, expiry and cancellation reclaim unassigned staging.
- The cache grants access only to the current user and SYSTEM. A cross-process
  guard protects creation/collection; a held reservation lease is preserved.
  Original file names/sizes/digests are retained in its private lease inventory.
  Collection validates the entire inventory and directory layout before deleting
  exact owned names, and refuses unknown names, extra entries and reparse points.
- A real adversarial fixture found premature deletion while enumerating a
  malformed cache directory. The failed result is private evidence; validation
  now precedes all deletes. Actual restart gates preserve unknown extra files,
  a single renamed file and an external directory-junction target, then restore
  those same project-local fixtures and prove final collection.
- File/token/local-allocation/hash/find handles have exception-safe ownership;
  byte buffers clear their data on destruction. Compiling the actual owners
  verifies one release each during a simulated allocation exception. This is
  an ownership unit gate, not a claim of exhaustive live-process OOM testing.
- Rebuilt one native host and passed the full real WebView2 run (409 assertions,
  no new diagnostics). The force-stop/restart gate passes with held-lease,
  immutable-inventory, unknown-entry and junction protections, an independently
  checked private ACL and zero remaining reservations. No extra Home/app used.
- Full local run: 43 Python checks (42 passed, the unavailable file-symlink
  privilege case skipped), plus 33 JS checks passed. The real directory-junction
  gate is separate and did pass. Contracts/deployed overlays match. Range-read
  revision is advertised only for filesystem/storage reads, not the VFS backend.
- This is staging infrastructure. Website input assignment/chooser handling,
  submission retention, public C/JS/Agent upload, downloads, complete frames,
  Android and final lifecycle/package validation remain required.
- Filename rules: [Microsoft Win32 naming conventions](https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file).

## D8 — native file-input assignment and website-byte retention

- Added a private typed assignment adapter. A fresh isolated-runtime document
  and pinned file-input reference resolve the actual DOM node; only internally
  sealed cache paths reach `DOM.setFileInputFiles`. Agent-supplied JavaScript,
  DOM object IDs and browser-device filesystem paths are never accepted.
- Observation reports bounded file-input constraints and hidden inputs without
  exposing existing filenames or values. Assignment supports hidden inputs,
  open Shadow DOM and accessible same-origin frames. Disabled/inert, stale,
  non-file, directory and insufficient multiple-file targets fail before delivery.
- Success verifies the exact resulting FileList names/sizes/count, rather than
  claiming website acceptance. The real fixture also reads all File bytes and
  independently compares their SHA-256, including binary and empty files, and
  observes exactly one trusted input/change event pair. Repeated assignment
  returns its saved receipt; conflicting targets cannot reuse that effect.
- Retention begins before the native call. Cancellation, staging expiry,
  takeover and navigation preserve potentially assigned bytes for delayed page
  submission. Explicit release requires consumption confirmation; closing the
  owned browser reclaims its transfers. Retained reservations are bounded to
  sixteen per view/thirty-two per host, 128 files and the existing 128 MiB limit.
- Native input, drag and capture cannot overlap assignment. Dialog/new-window
  boundaries yield checkpoints without repeating assignment. Cancellation and
  timeout preserve conservative effect uncertainty until callbacks drain. Unique
  object groups and view identities prevent old callbacks affecting replacement
  views; failed object-group cleanup blocks further uploads in that view.
- Rebuilt the same native host and passed 658 real WebView2 assertions, including
  delayed File reads beyond the staging TTL, page-cleared FileList uncertainty,
  dialog/popup interruption, cancellation, deadline expiry and close/recreate.
  No new native diagnostics; private ACL and zero cache reservations verified.
  Actual crash/restart inventory/held-lease/junction collection gates pass again.
  All 43 Python checks (42 passed, one unavailable symlink privilege skipped)
  and 33 JS checks passed. No additional app/Home/build tree was created.
- Public C/JS/Agent upload transport, file-chooser interception, clearing inputs,
  directory/OOPIF handling, downloads, Android and final packaging remain open.
  This commit does not advertise a public upload service yet.
- Protocol reference: [CDP DOM file input assignment](https://chromedevtools.github.io/devtools-protocol/tot/DOM/#method-setFileInputFiles).

## D9a — preserve the original task workspace across UI continuations

- An actual relative-file upload probe failed before file assignment: the
  workbench passed its global SDK client, and HTTP SDK context reconstruction
  selected the project default instead of the native caller's session root.
  The bounded failed report is retained privately as reproduction evidence.
- The workbench now binds automation to the action's frozen context. Native UI
  admission includes its original workspace; the HTTP adapter preserves that
  explicit absolute root instead of recomputing it from the selected project.
  Empty context retains existing default behavior. Invalid types, embedded NUL,
  control characters, oversized, relative and Windows drive-relative roots fail
  explicitly rather than falling back to a different directory.
- Actual SDK tests verify context, UI queuing and relative digest continuations
  against a distinct root. The actual managed upload with a workspace-relative
  source now passes. Existing public C/UI regression (19 actions, four
  navigations, five waits) and the real JS client pass with the bound SDK.
  This changes path context, not the caller's filesystem authorization.

## D9b — shared public C/JS/Agent file-input uploads

- Added the generated `browseruse.upload` / `mdoBrowserUseUpload` contract with
  set/status/confirmed-release actions. C validates precise targets and action
  fields before UI queuing. The JS client uses the existing durable operation
  journal for set/release; raw C callers use the ordinary scoped UI receipts.
  Added managed `browser_upload` and read-only `browser_upload_status` tools.
- The visible workbench checks native capabilities and the input before file
  access, hashes bounded original-task sources, streams revision-pinned 64 KiB
  ranges to the displaying device, seals complete SHA-256, then revalidates the
  original reference for one assignment. No browser-device path is supplied by
  an Agent. Staging failure cancels its reservation; uncertain assigned bytes
  remain available for website consumption. Release needs explicit confirmation.
- Failures retain the SDK code, stage, source index and bounded original cause.
  Lost assignment/release replies retain conservative uncertainty rather than
  becoming a generic source-read error. Unsupported devices fail before source
  reads; cancellation/document change during transfer cannot deliver another
  chunk or silently select a replacement input.
- Actual normal-UI C-service/JS-client tests verify binary and empty File bytes,
  one assignment across duplicate durable submissions, foreign-task rejection,
  confirmed release and non-replay after releasing an operation result. Actual
  managed tools verify workspace-relative and absolute sources, status, release,
  full access without extra prompts and read-only denial on an owned tab.
  The private ACL check passes and no transfer reservations remain afterwards.
- Existing public C/UI regression (19 actions, four navigations, five waits),
  real JS client (including one native drag and duplicate suppression), and the
  prior managed tools pass; eleven managed tools now exist. Local unit/contract
  checks: 47 Python cases (46 passed, one unavailable symlink privilege skipped)
  and 42 JS cases. Contracts and deployed overlays match. The updated plugin and
  Skill are installed in the single Home; no model calls or extra app copy used.
- Full Browser Use remains in progress: chooser interception, clearing inputs,
  directory/OOPIF support, downloads, vision coordinates, offscreen drag,
  Android and final lifecycle/performance/package validation are still required.

## D10 — clear file selections without losing retained website files

- Added `browseruse.upload(action="clear")` through the shared C/JS/Agent
  contract. It takes an exact document/input, rejects source-file and transfer
  arguments, checks `file_clear_capability`, and never reads task files or
  reserves disk space. It shares the native input/cancellation lifecycle.
- Actual WebView2 evidence showed that empty `DOM.setFileInputFiles` leaves a
  nonempty FileList unchanged. That failed receipt is preserved privately.
  Clearing now uses a fixed isolated DOM transaction with one controlled
  input/change pair and post-handler verification. Results explicitly say
  `input_method:"dom_file_clear",events_trusted:false`; an already empty input
  emits no events. The underlying protocol limitation is not claimed fixed.
- A clear identity returns its original receipt even after a later selection;
  it cannot erase that later file by replay. In-memory clear receipts are bounded
  to 32 per view/64 per host and expire by their deadline, independently of file
  staging counts/bytes. Failed and cancelled clear preparation has an exact-key
  cleanup path. Old uploaded files remain retained for a site's delayed File
  reads, until explicit consumption-confirmed release or browser closure.
- File-input metadata no longer marks file controls as text-editable. Upload
  validation follows inert ancestors through open shadow slots/hosts and
  accessible frame owners. Native object checks also reject obsolete owner
  documents. Disabled, stale, foreign and unsupported paths remain explicit.
- Rebuilt the same host. Final full WebView2 gate: 870 assertions, no new native
  diagnostics, private ACL and zero cache reservations. The focused input gate
  also passed 470 assertions. Tests include late-replay protection, delayed
  reads after clearing, exact event trust/counts, no-op/directory clear, inert
  shadow/frame ancestors, cancellation/dialog recovery and independent quota.
- One intervening broad probe failed an initial locator preflight without its
  error details; it did not reproduce in focused/final broad runs. The probe now
  includes bounded locator failure metadata. No local root cause is claimed for
  that isolated observation; startup/latency stability remains a final audit gate.
- Actual public C/JS/Agent tests pass, including duplicate operation recovery,
  controlled clear/no-op semantics, preserved old transfer state, and read-only
  denial on an owned tab. Force-stop/restart inventory/junction/lease collection
  passes again. Local checks: 47 Python cases (46 passed, one unavailable
  symlink privilege skipped) and 45 JS cases passed. All use the single Home.
- Chooser interception, directory/OOPIF uploads, downloads, vision coordinates,
  offscreen dragging, Android and final lifecycle/performance/package validation
  remain open; the full goal is still active.
- Reference for the protocol special case: [Puppeteer file input implementation](https://github.com/puppeteer/puppeteer/blob/main/packages/puppeteer-core/src/cdp/ElementHandle.ts).

## D10a — readable-text clipping cannot invalidate exact locators

- A separate actual long-page regression confirmed that private locator scans
  collected readable text unnecessarily. Clipping that copy at 16 KiB marked
  the entire search incomplete, so a uniquely matching file input returned
  `observation_incomplete`. The failed bounded report is preserved privately.
- Locator scans now collect matching metadata without copying readable text.
  Normal observations still enforce their text limit; node, frame, time and
  candidate limits remain unchanged. Runtime version advances to seven.
- A local fixture with sixteen long paragraphs proves observations still clip
  at exactly 16 KiB while both general and file-input exact CSS locators succeed.
  Focused input tests and the full actual WebView2 run pass (905 assertions in
  the final broad run), with no new native diagnostics and zero cache leftovers.
  This is distinct from the earlier isolated startup lookup failure; its cause
  was not captured and is not attributed to this text-clipping defect.
- Installed the updated runtime in the same Home; source and generated host
  checks match. The chooser/download/frame/platform work listed above remains
  required before the full Browser Use goal can be marked complete.

## D11a — native file-picker interception and bounded lifecycle

- Added private native arm/list/target/cancel/disarm operations. Interception is
  off by default and enabled only after an acknowledged protocol call. One
  opaque picker ticket per view carries a bounded lease; raw protocol node/frame
  identities and selected paths never cross its metadata boundary. Pending
  pickers fence competing native input, drag and file-selection changes.
- Exact hidden, shadow and same-origin frame inputs resolve in an isolated
  world, using page runtime version eight. An invalid document argument cannot
  pin/poison the ticket. Later picker requests cannot replace the first ticket.
  Navigation invalidates it; closing/recreating a view drops its subscription,
  timer and pending callbacks. This step does not yet claim OOPIF support.
- Cancellation dispatches one explicitly untrusted cancel event without
  changing a preexisting FileList or releasing website-owned bytes. Page-world
  EventTarget overrides cannot hijack the fixed isolated transaction. Dialogs
  and popups opened by a cancel handler yield checkpoints while callbacks drain.
  Expiry cancels once; takeover disables interception and cancels the old
  request without opening another OS picker or returning control automatically.
  Failed policy cleanup blocks input instead of retrying every timer tick.
- Actual WebView2 confirmed mouseReleased acknowledgement may precede the
  fileChooserOpened event. The native input receipt alone cannot guarantee a
  picker checkpoint; bounded failure evidence is retained privately. The next
  shared-dispatcher stage must inspect picker state after input/verification.
- Rebuilt the same native host. The final full WebView2 gate passed 1020
  assertions, with no new native diagnostics, a private cache ACL and zero
  remaining file reservations. The focused chooser lifecycle also passed.
  Local checks passed: 47 Python cases (46 passed, one unavailable Windows
  symlink privilege skipped), 45 JS cases and generated/deployed contract checks.
- This is the native foundation, not the completed public chooser feature.
  Automatic Agent arming, C/JS/managed chooser APIs, chooser-bound upload
  acceptance and visible pending-picker controls remain the next vertical step.
  Downloads, full frame/directory support, vision coordinates, offscreen drag,
  Android and final stability/package gates are still required for the full goal.

## D11b — shared chooser workflows and exact picker-bound upload acceptance

- Added generated `browseruse.chooser` / `mdoBrowserUseChooser`, the JS
  `chooser` completion helper and managed `browser_choosers` (read) /
  `browser_chooser_cancel` (external effect). Cancellation uses the existing
  durable operation journal; foreign scopes and invalid conditional arguments
  fail in C. Thirteen managed Browser Use tools now exist.
- Agent actions acknowledge interception before sending trusted native input.
  Pending pickers return `file_chooser_open,completed:false` and fence another
  click; observation remains available with a fresh document and opaque picker
  metadata. The dispatcher checks after input and after its final observation
  to cover the observed mouse-ack/event ordering. Older hosts without safe
  interception fail explicitly before sending Agent input.
- Upload set accepts exactly one `target` or `chooser`. The chooser resolves its
  actual hidden/shadow/same-origin frame input before source reads. Native
  admission, chunks, sealing and assignment retain the original chooser,
  document and reference. Cancellation/expiry/navigation cannot deliver an old
  staged file to a replacement target. Assignment consumes its picker once,
  without an additional cancel event; retained File bytes follow the existing
  explicit website-consumption release rules.
- Added trusted side-pane Cancel selection / Take over browser controls. Their
  actual DOM/native tests cover cancellation, takeover, stale refresh disposal
  and hiding controls over modal checkpoints. Public cancellation yields an
  existing dialog/popup checkpoint before attempting another page transaction.
- The new UI test exposed a takeover cleanup gap: busy became false between
  disabling interception and deferred cancellation. Its bounded failed report
  is retained privately. Busy/input fences now cover both deferred phases, and
  automatic policy cleanup waits for existing input/drag/upload releases. The
  exact failed UI sequence now passes, without opening/replaying an OS picker.
- Final full native gate passed 1027 assertions, with no new native diagnostics,
  private cache ACL and zero file reservations. The focused chooser/visible-UI
  gate also passes. Actual C-service/JS-client and managed tests pass for
  automatic interception, pending-click fencing, fresh observation, exact picker
  acceptance, durable cancel-once, file preservation and scope denial. Read-only
  owned tabs allow listing and deny cancellation/upload; full access adds no
  extra prompt. The existing SDK suite (19 actions, four navigations, five
  waits), JS client and managed tools (including selection and drag) pass again.
- Local checks pass: 48 Python cases (47 passed, one Windows symlink-privilege
  skip), 52 JS cases and current generated/deployed host checks. Updated plugin
  and Skill are installed in the single Home, using the same rebuilt native
  host. No model calls, extra application copy or downloaded browser used.
- The full goal remains active. Directory/OOPIF uploads, downloads, complete
  frame/AX support, vision coordinates, offscreen drag, Android and final
  lifecycle/performance/packaging validation remain open. The separate earlier
  isolated startup lookup failure still lacks a captured cause; this chooser
  work does not claim to resolve it.


## D12a — native response downloads and verified private bytes

- Added private, explicitly armed WebView2 download interception, bounded opaque
  tickets, pending/receive/verify/cancel/release lifecycle and 64 KiB reads of
  completed SHA-256-verified bytes. Native browser responses retain POST/blob
  semantics; there is no refetch with copied cookies. The public C/JS/Agent
  download/save workflow is the next stage and is not claimed complete here.
- A real truncated response exposed WebView2 auto-resumption: a second native
  starting callback could retain an obsolete in-progress COM wrapper. Fresh
  scoped Page download GUIDs now gate admission; the authoritative terminal page
  progress closes a stale wrapper without publishing another ticket. HTTP EOF
  can still cause a browser-internal retry; the plugin does not repeat a click.
  Forced reset and authenticated POST each execute once in the fixture.
- Actual unknown-length responses report total zero on this runtime. Zero is
  treated as unknown, while completed physical size, received bytes and SHA-256
  remain exact. Close drains confirmed native cancellations after controller
  closure, fixing the captured last-download lease leak. Takeover rejects new
  identity admission and processes only existing owned progress events.
- Cache directories use private user/SYSTEM ACLs, exclusive leases, validated
  cleanup inventory and fixed filenames. Unknown/reparse layouts are preserved.
  Limits: sixteen tickets/view, thirty-two/host, 64 MiB/file, 128 MiB reservations;
  deadlines and a 256 MiB free-space margin apply. Progress cancellation is not
  a hard filesystem quota: an unknown-length response may overshoot before its
  callback. Only within-limit verified bytes are readable. Adversarial lifecycle,
  quota overshoot and cleanup backoff remain final audit items.
- The focused real download gate passed 218 assertions, including equal-URL
  independent identities, foreign-view denial, exact binary/empty/blob/POST and
  unknown-length bytes, ranges, oversize denial, cancellation, expiry, reset,
  truncated resumption, navigation, takeover and close/recreate. Final full
  WebView2 gate passed 1248 assertions, with no new native diagnostics, private
  upload/download ACLs and zero remaining reservations in either cache.
- Rebuilt the existing host and reused the same deployment/Home/object tree.
  Local checks pass: 48 Python cases (47 passed, one Windows symlink-privilege
  skip), 52 JS cases and generated/deployed host checks. Remaining public
  downloads/safe save, directory/
  OOPIF frames/uploads, vision coordinates, offscreen drag, Android and final
  stability/performance/package gates keep the original goal active.


## D12b — a file response must not poison its source page

- The actual public POST workflow exposed WebView2 navigation error 9 after the
  response had become an intercepted download. The prior UI treated that as a
  broken webpage and hid its viewport. A bounded failing native report is kept
  privately at `.mdo/evidence/download-navigation-aborted-before-fix.json`.
- Track the current navigation ID and URI privately and mark only its matching
  native DownloadStarting response. Only CONNECTION_ABORTED for that confirmed
  navigation is classified as a download transition. Keep the raw error code;
  DNS/TLS/reset/disconnect and later navigations still report their errors.
- The focused real download gate passes 242 assertions. It now asserts that
  POST leaves the source page usable and that an unrelated connection reset
  still fails while an older completed download is retained. Cache ACL remains
  private, no new native diagnostics were created and no reservations remain.
- The broader native gate is not green yet: it reproduced intermittent hidden
  fixture startup with an interactive document and pending top-level completion,
  before reaching downloads. State and DOM/load timing evidence are retained; a separate focused hidden/visible
  run completes normally (218–690 ms). This does not establish a cause or a fix
  for that earlier startup issue. Public download integration remains in progress.


## D12c — shared download receipt and Agent workflows

- Added generated `browseruse.download` / `mdoBrowserUseDownload`, JS `download`,
  and managed `browser_downloads` (list/get/wait, read) / `browser_download`
  (accept/cancel/release, external effect). All use the original extension/task
  scope, native browser identity and existing live permissions. There are now
  fifteen managed tools. No response URL refetch or raw bytes enter this API.
- Acceptance/cancel/release use durable operation identities. Acceptance starts
  the existing response within separate byte/transfer-time budgets. Waiting is
  read-only and can return still pending/receiving; verified completion includes
  exact size/SHA-256. Native failures retain their receipt/reason/source. Read-only
  tasks inspect their owned tabs but cannot mutate transfers; full access adds
  no extra prompt. Terminal release discards bytes rather than saving a file.
- Agent input and explicit navigation acknowledge interception first. Pending
  offers fence repeat input/navigation and appear in fresh observations. A real
  POST callback after the final page read exposed an immediate event-order gap;
  native download checkpoints drain their identity, and the dispatcher coalesces
  immediate post-input events for at most 150 ms. Arbitrary later website activity
  still belongs to the next observation. Delivery is not claimed as completion.
- Integration caught two new admission mismatches: the durable-method enum and
  a shared workbench byte-limit field. Both now match the public contract; the
  internal download byte budget has a separate field from observation/image
  limits. An explicit budget mapping allocation failure stops before admission.
- Actual public C/JS/WebView2 and managed tests pass for binary/blob/POST/unknown
  responses, exact verified hashes, repeated accept/cancel identities, raw C
  receipts, original scope, pending-repeat fencing, direct navigation, managed
  accept/wait/release and read-only denial. POST is requested once; native cache
  ACL is private and no reservations remain. A longer bounded local cancellation
  fixture tests a genuinely in-progress transfer instead of a file already done.
- Local checks pass: 50 Python cases (49 passed, one Windows symlink-privilege
  skip), 60 JS cases, matching generated/deployed contracts. Existing public SDK
  (19 actions, four navigations, five waits), JS client and managed tools including
  exact selection/drag pass again. Public upload/clear/picker acceptance/cancel,
  scope/permission and cache-release regression also passes in the same Home.
- Updated plugin and Skill are installed in this project deployment. Still open:
  safe save into the original task workspace, visible download controls and safe
  interception of initial/popup navigation before Agent input. Downloads are not
  yet advertised as user-delivered files. The broad native startup failure above
  also remains an unresolved final stability gate; successful focused/API checks
  do not replace it. Remaining frame/directory/vision/drag/Android/lifecycle and
  final packaging work keeps the original full goal active.


## D12d — bounded startup diagnostics without a passing fallback

- Added `--startup-only`: four sequential hidden/visible starts in the same Home.
  It captures a slow document's readiness/frame/load timing, waits a bounded
  interval, then records whether showing that same controller changes readiness.
  A late recovery/show cannot turn a start exceeding the 10-second default into
  a passing measurement. CLI focused modes are mutually exclusive.
- The broad upload/race startup assertions now retain full native state, elapsed
  time and DOM/load evidence instead of the old generic capability error. They
  still fail their original assertion when showing a delayed view is needed.
  No reload, additional browser distribution or extra application/Home is used.
- The latest standalone four-start check passes with no new native diagnostics.
  Earlier broader failures are preserved privately and remain unresolved. The
  evidence shows interactive top documents before native completion, but does
  not isolate whether visibility, frame scheduling or another timing condition
  caused the delay. This diagnostic stage makes no product startup-fix claim.


## D12e — operation queries must use the worker readiness gate

- A real Browser Use tab creation exposed `sdk_operation_pending` after the
  prior operation query had advertised terminal success. The SDK persists final
  metadata before event delivery and project-lease teardown, then marks its
  worker releasable later. Queries cloned the former flag while result/release
  checked the latter. Private failure evidence is retained at
  `.mdo/evidence/operation-terminal-result-race-before-fix.json`.
- Runtime start/get/wait/list snapshots now use the same worker gate as result
  admission. While teardown is pending they report running, nonterminal and no
  available result; final outcome/error/byte metadata stays hidden. The durable
  ledger and existing event/teardown ordering are preserved, so recovery still
  cannot re-execute an admitted identity.
- The actual helper is exercised with final persisted metadata and an unfinished
  worker, completed snapshots and allocation denials. Actual host tests pass
  for twelve sequential fast operations (terminal query followed immediately by
  result), scope denial and released-identity non-replay. TCC startup and applied
  host checks pass in the same deployment. This is a separate SDK defect fix;
  the wider download/Browser Use goal and final stability gates remain active.


## D12f — do not inspect receipt storage during job disposal

- Inspection of the completion path found another lifetime defect: release marks
  a job disposing, drops the job-table lock, and frees its metadata before
  reclaiming the table slot. List and the added ownership check could still read
  that metadata. This is a code-proven race; no crash reproduction is claimed.
- List now skips disposing slots before checking their scope. Other operations
  reach the existing disposing/busy gate before inspecting scope storage. No
  additional job identity, retry or mutable ownership state is introduced.
- The actual host scope, released-admission and twelve sequential fast-result
  checks pass again; deployed overlays match. The original full goal continues.


## D12g — bounded, effect-free managed tool session admission

- The expanded managed regression captured `session already has an active
  runtime` before `browser_open` execution. The existing SDK returned it as
  a generic transport failure. Runtime exclusivity can cover transient readers
  and event retirement; the particular competing owner is not proven here.
- The SDK tool utility now waits at most 500 ms at that exact session-open
  conflict. No executor/tool invocation is repeated. Other context changes, IO
  errors, cancellation and deadlines stop admission; a genuinely busy session
  remains `sdk_tool_busy`, with `tool_admission` stage and no possible effect.
- The actual helper's controlled-clock tests cover immediate/retiring/busy
  runtimes, nonretryable errors, cancellation and deadlines. Real TCC startup
  and the complete fifteen-tool managed WebView2 regression pass afterwards,
  including permissions, popup ownership, dialogs, screenshots and gestures.
  No claim is made that concurrent model runs can share a session runtime.


## D12h — intercept initial/popup responses before navigation

- Browser Use tabs carry the required interception policy. The native host
  acknowledges Page identity events before its first URL or adoption of the
  original popup response. POST/body/cookies stay with that response. Policies
  inherit from the actual opener; manual control is never silently returned.
  Old/unsupported hosts fail before navigation; concurrent creates cannot join
  conflicting policies. A bounded five-second watchdog covers lost/late replies.
- Controlled lifecycle/fault tests found and fixed shared-timer reset, identity
  allocation and terminal-publication cleanup gaps in that policy gate. The
  native helper releases its timer/map ownership before publishing an error.
  This is not a claim that every native creation allocation path is audited.
- A real completed POST-popup response was being removed by WindowCloseRequested
  before its waiting caller got the receipt. Keep the broker while any response
  is owned, fence further page input, and resume automatic close only after the
  final release acknowledgement. An unpublished identity also holds closure,
  with bounded close-event ordering grace. Explicit user close still reclaims.
- Actual POST navigation/read callbacks could precede download identity and hide
  a valid pending response behind stale-document/uncertain errors. A bounded
  metadata-only post-input checkpoint now catches either order without another
  input, page evaluation or URL request; cancellation/deadlines remain enforced.
- Real focused native download coverage passes 295 assertions. Normal public
  C/JS/managed download coverage passes initial URL, automatic-script, exact POST
  popup, verified hashes, durable accept/cancel, scope/permissions, pending fences
  and automatic close. Both POST endpoints execute once; that gate leaves zero
  reservations. Existing public SDK, fifteen managed tools and upload/clear/
  chooser APIs pass; local checks pass 55 Python cases (one Windows symlink skip)
  and 70 JS cases. The single deployment is about 833 MiB.
- The broader native run executes 1312 assertions without new exception
  diagnostics, but its final cache gate FAILS: one empty download GUID directory
  remains (no payload/lease). Chromium history identifies a cancelled 3 MiB
  response with zero received bytes. Late native directory recreation is an
  unconfirmed hypothesis. Evidence is retained privately at
  `.mdo/evidence/empty-download-directory-after-full-gate.json`; the full gate
  is not claimed passed. Idle cleanup is the immediate next investigation.
- Safe task-workspace saving, visible download UI, frame/directory/vision/drag/
  Android work and final lifecycle/performance/package gates still keep the
  full Browser Use goal active. Earlier intermittent hidden startup evidence
  also remains open; this stage does not invent a root cause for it.


## D12i — collect late cache directories after the last download retires

- The last cancelled native download can leave an empty GUID directory after
  its first cleanup. The old watcher stopped as soon as no job remained, so no
  later idle collection ran. Its fixture waited six seconds and still failed.
  That failure is retained at
  `.mdo/evidence/late-download-directory-before-idle-reaper.json`. The exact OS/
  Chromium operation creating the late empty directory is still not traced.
- Retiring an owned cache now starts a five-second maintenance window with
  exponential backoff (100 ms to 1 second), on the existing native timer/store.
  The existing guarded inventory collector skips live leases and preserves
  unexpected/reparse layouts. There is no new thread, application copy or
  unlimited background loop. Explicit release still removes its native receipt.
- The real fixture first requires native cleanup, then recreates only that
  retired GUID's empty directory. The harness performs no deletion. The full
  native gate passes 1341 assertions with no new exception diagnostics, zero
  upload/download reservations and removal of the injected directory in 110 ms.
  A further focused gate passes 308 assertions, verifies that another live
  lease's exact bytes/hash survive idle collection, and reaps the injected
  directory in 203 ms. These are observed timings, not a latency guarantee.
- The binary and composed native helper match the build receipt; ordinary
  headless service is restored in the same Home. This clears the D12h cache
  failure for the tested paths. Unexpected-layout/OOM/quota audits, the earlier
  intermittent hidden startup evidence, safe task-workspace saving, visible
  download controls and the remaining full Browser Use stages stay open.


## D12j — retain task identity through UI/RPC context reconstruction

- Preparing the download sink exposed a confirmed continuation defect: the
  local SDK HTTP route discarded namespace/member fields, and workbench records
  omitted the original workspace and subtask identity. Native UI actions already
  carried those fields, but reconstructing a caller could silently broaden it.
- Preserve validated namespace/member strings for local continuations; malformed,
  NUL/control-containing or oversized values fail instead of becoming global.
  Existing server-bound ApplicationContext authority remains unchanged. New
  workbench records retain workspace/namespace/member; task ownership rules are
  not changed. Legacy records lacking a frozen root still need explicit handling
  before exposing save controls; no default-root reconstruction is claimed here.
- Actual SDK checks cover a custom workspace and subtask through tab creation,
  queued UI action and reconstructed file digest context. Operation read/cancel/
  release/list scope checks now exercise foreign namespaces and members too.
  Targeted tests and TCC startup pass; the accompanying local run passes 61
  Python cases (one Windows symlink skip), 70 JS cases and public upload/picker
  regression. No file-save API is advertised yet. The full goal continues.


## D12k — task-owned bounded byte streams for the download sink

- The existing stream service will carry file chunks into the original task
  host; it avoids whole-file UI/model buffers and adds no runtime dependency.
  An actual SDK test demonstrated that a foreign extension/task could read,
  append, close and release another stream if handed its handle. The retained
  failing fixture uses synthetic bytes only.
- Each in-memory stream now captures extension/device/project/session/namespace/
  member at creation. All later methods check that scope before consumption,
  mutation or lease renewal. Null/default project spellings normalize, and
  invocation/run changes within that same task are allowed. Free/expiry/shutdown
  also release the captured scope. Ownership allocation failure frees the slot.
- Actual SDK tests prove foreign operations leave exact original bytes and EOF
  intact, and exercise private subtasks, backpressure, default normalization and
  explicit close/drain. The combined local gate passes 61 Python cases (60 pass,
  one Windows symlink skip), 70 JS cases and public upload/picker regression.
  This establishes byte transport, not a claim that downloads can be saved yet.
- Next: implement the C file consumer and atomic, no-overwrite publication under
  the frozen task root, then wire shared save APIs/tools and visible controls.
  Use bounded streaming and complete SHA-256/size checks; preserve source bytes
  on failure, and audit operation/crash recovery and legacy root resolution.
  XRT supplies pinned directory roots and root-relative hard-link publication;
  unsupported filesystems must fail explicitly rather than fall back to unsafe
  path append/rename. Actual filesystem validation remains work to do.


## D12l — verify and publish a staging file without replacing destinations

- Add the shared C/JS host SDK `files.publish` primitive. It pins the original
  absolute workspace directory identity, validates a source revision and exact
  size/SHA-256 with bounded reads, then uses XRT's root-relative, no-replace hard
  link. It checks the published entry against the held file before success.
  Source bytes remain owned by the caller; the primitive never unlinks either
  path, so an alias or uncertain cleanup cannot delete another entry. A hard
  link is explicitly not an independent copy or a power-loss durability claim.
- Existing targets, root replacement, size/hash/revision mismatches, invalid
  paths and active Windows writers fail before publication. Missing native
  identity information fails closed. Unsupported hard-link filesystems have no
  unsafe rename/copy fallback. Failures after a possible publication are marked
  uncertain; the caller must inspect instead of blindly replaying effects.
- Actual SDK coverage passes binary/empty/Unicode files, identical-inode/case
  aliases, physical root replacement and all tested precondition failures.
  Initial test expectations for schema-layer validation (400 rather than 422)
  were corrected. The final local gate passes 69 Python cases (67 pass, two
  Windows symlink-privilege skips); the existing 70 JS cases and deployed overlay/
  shared contract checks pass. No extra application/Home or native rebuild was
  needed; the single development deployment remains about 832 MiB.
- This is the publication building block, not the complete save feature. The
  scoped streaming consumer, durable partial-file manifest and recovery/quota
  handling, shared save tool/API, visible controls and the remaining Browser Use
  goal stages still require implementation and verification.


## D12m — prepare stream acknowledgements before committing byte changes

- Reviewing the consumer path found a concrete failure order in the existing
  stream implementation: read consumed/zeroed its range and write appended
  input before allocating their reply. The read path also ignored byte-result
  construction failure. A caller could therefore receive failure or incomplete
  metadata after its byte state had changed, undermining safe file reception.
- Build checked metadata and byte acknowledgement first, then perform a
  non-allocating commit under the existing stream lock. Failed result creation
  does not read, append, close or release. Failed open acknowledgements unwind
  their new slot. A transport-level lost reply remains uncertain; the upcoming
  file producer must abort that receiver rather than blindly resend chunks.
- A compiled harness executes the actual reviewed exchange fragment while
  faulting result-builder allocations. All 52 failure positions preserve exact
  ring/EOF/release state and free their allocations; all five successful paths
  commit the expected state. These are result-builder fault simulations, not a
  claim that every XRT/TCC application allocation has been audited.
- Actual SDK regression verifies wraparound, exact binary bytes, committed
  metadata, EOF, scope and backpressure. The local gate passes 71 Python cases
  (69 pass, two Windows symlink skips), 70 JS cases, contracts and overlays.
  Real public C/JS WebView2 regression passes 19 actions, four navigations, five
  waits, screenshots, exact-once selection/drag/dialog handling and popup cleanup.
  The same Home is restored headless; no additional deployment was created.
- Safe reception with durable partial-file ownership/recovery and quota remains
  the next step before exposing workspace saves. The full goal remains active.


## D12n — receive exact scoped streams into recoverable, bounded task staging

- Add generated C/JS `files.receive.start/get/list/release` host SDK services.
  Start is admitted through an explicit SDK operation identity; the receiver
  consumes task-owned 16 KiB ranges until explicit EOF, enforces exact size and
  SHA-256, flushes bytes, and persists a ready source revision for publication.
  No download URL is fetched and no file bytes enter the returned receipt.
- A small Home intent precedes exclusive workspace staging creation. The opened
  file identity is saved before nonempty writes. Root identity and task scope
  remain frozen, including namespace/member. Eight retained receipts and 128 MiB
  of expected-size reservations bound active, ready and unresolved staging.
  Cancel, wrong hash/size and idle/deadline failures clean only proven ownership.
- Process restart preserves ready files and reports partial receivers as
  interrupted. New admission collects only identified, inactive partial files;
  unknown/replaced files or roots are preserved. Windows cleanup uses XRT's
  delete-open primitive so it deletes the checked object rather than reopening
  an unverified pathname. Preparatory zero-byte entries without a durable inode
  remain for review. Non-Windows reception explicitly fails unsupported until
  its cleanup namespace guarantees have an implemented and tested adaptation.
- Twelve actual SDK tests cover binary/text/empty ranges, publish then release,
  hash/size/EOF, cancel/idle, live release fencing, foreign tasks and workspace,
  physical file/root replacement, unknown inventory, both quota gates and a hard
  stop/restart of this same Home. Ready bytes survive; identified partial bytes
  are collected; the old operation identity stays interrupted without replay.
  Test fixtures leave no retained receiver ledger or staging bytes behind.
- The first SDK generation attempt exposed its method/namespace collision rule;
  `files.receive` was changed to `files.receive.start` before successful
  generation and testing. No generator workaround or parallel namespace API was
  introduced. Source overlays were checked before recovering the failed prepare
  receipt; no unreviewed host edits were overwritten.
- Final checks pass 83 Python cases (81 pass, two Windows symlink skips), 70 JS
  cases, shared contracts and deployed overlays. Real public C/JS WebView2
  regression again passes 19 actions, four navigations, five waits, screenshots,
  exact-once selection/drag/dialog handling and popup cleanup. Same deployment
  restored headless, about 835 MiB; no extra Home or native build was created.
- Next connect the retained native response to this sink, preserve byte-write
  uncertainty and the displaying window's frozen target, add the dedicated
  workspace-write save API/tool, then visible download/recovery controls. This
  host primitive does not by itself complete user-facing download saving or the
  remaining frame/vision/drag/Android/lifecycle/performance/package goal stages.


## D12o — shared download-save API and managed workspace-write tool

- Add C `mdoBrowserUseSave`, JS `browser.save`, service `browseruse.save`, and
  managed `browser_save_download`. The C boundary validates the relative target,
  requires the tab's original workspace/private subtask, captures its physical
  root and queues one save action. The displaying UI transfers only that native
  response's existing verified bytes, then requests a separate no-overwrite
  publication. Native response queries, cookies and cache paths are never used
  as a model-visible file or a replacement URL request.
- The producer feeds bounded 64 KiB ranges into the scoped receiver and retries
  only explicit zero-write backpressure. Lost write/EOF acknowledgements abort;
  uncertain publication retains staging and cancels the exact publisher without
  claiming cancellation erased its effects. Child identities derive from the
  immutable UI action. Normal completion cleans staging and preserves the native
  response until explicit release. Confirmed saved files stay successful when
  cleanup needs attention; late cancellation still reports an already saved file.
- A real startup check rejected the initial tool descriptor: two mutating effect
  kinds require permission discovery. Add the SDK permission callback declaring
  the exact resolved destination path plus browser-use access. The plugin now
  registers all 16 tools; full access saves without another prompt, and the real
  read-only managed gate denies saving before browser execution. Ask-profile UI
  approval still needs its own final-stage interaction regression.
- Actual public C/JS and managed WebView2 save gates pass binary, blob, original
  POST, unknown-length and download-only POST popup delivery. All five public
  files match exact fixture bytes and full hashes. Same operation identity does
  not save twice; existing targets remain unchanged; original responses remain
  completed; foreign namespace/workspace access fails. Fixture POST and popup
  POST endpoints each execute once. Managed saving also writes exact bytes.
  The final native cache and scoped receiver inventory contain zero reservations.
- Final local checks pass 85 Python cases (83 pass, two Windows symlink skips),
  85 JS cases, generated C/JS contracts and deployed overlays. The JS suite covers
  lost acknowledgements, malformed ranges, manual/display-target cancellation,
  conflicts, cleanup failure and source closure without a false retention claim.
  Real transport-loss/crash injection into this new save orchestration remains
  a stronger final lifecycle gate; unit fault cases are not claimed as that gate.
  Same single deployment is restored headless; no extra environment/native build.
- Update Skill/API/README instructions for actual file saving and separate it
  from native download completion. Next implement visible download/recovery
  controls, including original destination intent across reload and legacy-root
  handling. Cross-device save transport, ask-profile interaction, remaining
  frame/vision/drag/Android work and final lifecycle/performance/package gates
  remain part of the active full goal.

## D12p — retain publication intent and reconcile uncertain saves

- Record the optional destination at receiver admission. The new shared C/JS
  `files.receive.publish` adapter durably records destination and original SDK
  publication identity before file effects, then records the verified receipt.
  Complete ready/publishing/published staging survives restart and quota reaping.
  The official save producer now uses this journaled adapter.
- Add explicit `files.receive.reconcile`: pin the original root, compare opened
  file identity and full hash, and inspect existing target/source bytes. It never
  creates/replaces a target or replays the previous operation. Missing/different
  targets require explicit review and a new publication; edited files survive.
- Six new actual SDK regressions cover intent, conflicts, missing/changed files,
  reconciliation, non-replay and foreign scope. The full local gates pass 91
  Python cases (89 pass, two Windows symlink privilege skips), 85 JS cases,
  contracts, deployed overlays and whitespace checks.
- Actual public C/JS and managed save probes again pass all five response types,
  exact hashes, original-response retention, duplicate identity, conflict and
  read-only denial. POST/popup POST execute once. Cache/staging inventory is zero.
- A real 32 MiB fault probe blocks only final ledger replacement after the file
  is published. The SDK reports uncertain completion; after hard stop/restart,
  destination/publication identity and complete bytes survive new admission.
  Reconciliation verifies the existing inode and full hash without recreation;
  the original failed identity is not replayed. Releasing staging preserves the
  saved file. Fixtures are removed after success in this single deployment.
- This gate does not prove crash-before-link or real network-loss behavior.
  Visible download/recovery controls, legacy-root handling, ask-profile UI,
  cross-device save, frame/vision/drag/Android and final lifecycle/performance/
  packaging checks remain open. The full goal remains active.

## D12q — share save ownership between trusted UI and Agent calls

- Add one in-memory save fence per browser shared by manual UI and Agent
  producers. Read-only progress contains bounded metadata only; explicit cancel
  targets this attempt's admitted receiver/publisher and never claims rollback.
  Other Agent mutations are fenced while a UI save is completing.
- Reuse the same verified producer for trusted UI with native Agent mode off.
  This allows user saving during manual control without taking over the browser
  (takeover deliberately cancels unfinished downloads). Public Agent permission
  and manual-control gates remain unchanged.
- Three new regressions prove actor flags, cross-producer exclusion and exact
  child cancellation/lock release. The focused 54-case JS gate passes, followed
  by the actual public C/JS and managed five-response save gate. Single Home is
  restored headless and the final cache/receiver inventory is empty.
- These helpers are the download panel's backend wiring, not the visible panel.
  Visible controls and the full remaining goal stages are still open.

## D12r — visible browser download and original-task recovery tray

- Add a compact SVG toolbar button with record badge and a collapsible in-flow
  tray. Show offer/progress/verification/error, accept/cancel/save/clear, inline
  relative paths, shared save progress/cancel and original-task staging recovery.
  Polling preserves edited path values and focused controls; collapse preserves
  responses. Native bounds follow the tray rather than overlapping its content.
- Trusted UI accepts without takeover and saves verified original bytes even
  under manual control, using frozen owner/task/private scope. Missing legacy
  roots fail explicitly. Recovery requires deliberate inspection/new publication;
  cleanup of staging or unsaved responses needs inline second confirmation.
  Confirmed files survive cleanup. Common error codes have actionable text.
- Five new JS cases cover frozen context/manual actor, missing root/changed
  device, fresh no-replay review identities, lost admission cancellation and
  disposed views. All 93 JS cases pass; all 91 Python cases finish (89 pass,
  two Windows symlink privilege skips). Contracts and applied overlays match.
- The actual mounted browser-view/WebView2 gate passes 13 checks: one original
  response request, accept without takeover, exact manual-save bytes/hash,
  retained input/button focus, real native bounds/controller height, explicit
  staging publication/reconciliation, saved-file retention and collapse. Native
  cache and receiver inventory end empty. The first probe assertion checked an
  asynchronous button too early; it now waits for the observed collapsed state.
- A normal-main-window visual check could not run: the desktop helper returned
  `failed to activate captured window`, then `FrameArrived timed out`. No visual
  screenshot pass is claimed. The single 835 MiB deployment is restored headless.
- Remaining work includes legacy-root handling, ask-profile interaction,
  cross-device save transport, additional crash/transport faults, frame/vision/
  drag/Android adaptation and final lifecycle/performance/package gates. The
  full long-term goal remains active; this is not a product release.

## D12s — recover a legacy tab root from its original persisted task

- Add generated shared C/JS host `browser.workspace.lookup/adopt`. Lookup reads
  the original session catalog without Agent/model startup and creates nothing.
  Adoption requires explicit confirmation, current browser/session revisions,
  the reviewed path and the same physical root; it only fills an empty binding.
  Strict owner/device/task/namespace/member matching prevents scope substitution.
  Existing bindings, missing/trash tasks and unavailable/reparse roots fail.
- Persist the adopted physical root pin. The C save boundary rejects its later
  replacement before response reads or browser dispatch, while normal directory
  timestamp/size changes remain valid. No navigation, POST replay or response
  recreation is involved in recovery.
- Ten actual SDK tests prepare only their own pre-upgrade browser rows while
  the single host is stopped, then verify original-versus-current task roots,
  fill-only persistence, scope, all confirmation/revision/path preconditions,
  missing root/task, physical replacement and the C save fence. Test restart
  client leases are explicitly renewed. All ten cases pass.
- The real public C/JS and managed five-response save gate passes again with
  the updated plugin and host, including exact hashes, original-response
  retention, duplicate identity, conflicts and read-only denial. POST/popup POST
  each execute once; final cache/receiver inventory is empty. Same Home restored
  headless, without another native build or environment.
- Next integrate visible review/adoption and manual-save pin validation, then
  complete the remaining full-goal platform/lifecycle/performance/package gates.
