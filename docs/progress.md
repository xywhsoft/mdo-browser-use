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
| E | Android implementation and explicit platform limits | In progress |
| F | Plugin lifecycle, end-to-end checks and deliverable | In progress |

Latest verified milestones:

- **Windows F15:** correct retained screenshot/native callback ownership and
  parameter-conversion callback leaks. Controlled production C++ checks cover
  63 assertions and 19 one-shot allocation failure sites. Complete regression
  passes 262 JS cases and 142/144 Python cases (two symlink-privilege skips).
  Actual native, first public screenshot, public privacy and public OOPIF
  input/upload/permission gates pass. F14 separately verifies the real 120s
  dialog lease and full native transfer cleanup. These are scoped results,
  not a claim of exhaustive OOM/performance qualification.
- **Artifacts F16/F18:** the refreshed Windows executable includes F15/P2 and passes
  ten packed runtime checks, including paired source/installed/host runtime
  equality. Refresh a stale portable `page/runtime.js`; native import/restart/
  export verifies all 22 files, and altered paired payloads are rejected before
  application launch. E28/E29's reviewed canonical workspace
  SDK/download UI passed legacy, existing-root and missing-root native gates.
- **Permissions P1/P2:** fix managed-tool cancellation being reported as a
  generic executor failure. A retained before-tool marker separates cancellation
  before permission admission from possible dispatched effects. All nine actual
  balanced-profile/decision-API/native-effect scenarios pass, including late
  grants, replay, stale documents and subsequent-tool recovery. Complete
  regression passes 144/146 Python cases (two symlink-privilege skips) and all
  262 JS cases. The refreshed paired executable passes all ten actual runtime
  checks. Normal-desktop prompt pixels remain unverified.
- **Android E30/E31:** implement retained original popup transport through the
  existing C/JS admission flow, correct ownership-query failure, and build the
  raw popup content/lifecycle device fixture. The controller/Chrome target pass
  103 contract assertions; the actual Activity/hooks compile. Current ADB has
  no device, so native popup/framework/host routing qualification remains false.
  Preserve the installed WebView; its earlier measured provider lacks isolated
  worlds. Overall Android automation stays disabled, including on newer
  providers until adapter integration and qualification are complete.
- **Remaining full-goal work:** Android frame/transfer integration and installed
  provider checks; remote transport and visual ask-profile interaction; broader
  lifetime/OOM/performance qualification and normal-desktop visual checks;
  final delivery verification. Keep the full scope active and do not infer
  completion from green component or packaged smoke checks.
- **Transport P3:** fix the shared JS client's unbounded in-flight admission,
  response and cleanup waits. Preserve original operation/receipt IDs and known
  terminal outcomes; never resubmit effects. Six new gates pass, including actual
  C admission with a held loopback HTTP reply and original-browser recovery.
  All 268 JS cases pass. Refresh the paired plugin, verify its 22-file actual
  import/export, and pass all ten packed-program checks with the current pair.
  Authenticated relay/direct-device qualification remains separate.
- **Transport R0:** run the production JS remote adapter through the actual C
  bridge/receipt/loopback/socket code into this deployment's C SDK. Four gates
  verify browser/task scope, original-receipt recovery after a lost completed
  write, both read-only guards and multi-chunk UTF-8 file bytes. All 272 JS cases
  pass. This private-pipe fixture has a host-lease seam and preauthorized peers;
  account authentication, physical-device paths and headed browser operations
  still need their own evidence.
- **Transport R1:** the same production JS/native bridge now drives the actual
  C plugin/workbench/WebView2 browser through eight complete workflows, including
  frozen private task binding, trusted input, capture, cancellation/recovery,
  stale documents and full-hash upload/retained-POST download/exact workspace
  save. Exactly one POST is observed and the same headless deployment is
  restored. Authentication, physical devices and normal-desktop pixels remain
  separately unverified.
- **Verifier P4:** align three executable C/JS transfer probes with the public
  SDK's third-argument options contract, including the directory subclient.
  Preserve task/private/workspace scope and propagate cancellation; use an
  explicit QA-only helper for deliberate foreign-scope calls. Actual complete
  upload/download/save gates pass with zero cache reservations, and all 275 JS
  cases pass. Artifact/runtime payloads are unchanged.
- **Android E32:** retain/list/cancel original native file-chooser callbacks,
  with bounded metadata, a 120s lease, exact view/document ownership and once-only
  cancellation. The actual Activity/hooks compile and 505 chooser contract
  assertions pass. URI assignment, source-frame identity, framework qualification
  and global Android automation remain disabled; retain the installed WebView.

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

## D12t — review and confirm legacy task directories in the download tray

- Add `读取原任务目录` and explicit `确认使用此目录` to legacy download trays.
  Display only the server-derived original task directory. Saving remains
  disabled before confirmation. Rebind the trusted file SDK to the confirmed
  original context while preserving owner/device/project/session/private member.
  An already committed binding is recovered by reading it after a lost reply;
  the UI never repeats adoption or switches to the selected chat's directory.
- Manual saves verify the persisted physical root pin before creating a byte
  stream. Replacement fails without a native download read; ordinary directory
  size/time changes remain valid. Missing original task/root remains an explicit
  limitation with the response retained, rather than an invented association.
- Five new JS cases cover review-before-adoption, lost binding reply, foreign or
  malformed context, replaced root and legitimate directory metadata changes.
  The full JS gate passes all 98 cases. An added actual SDK restart case proves
  binding/private scope/pin persistence without replay or rebinding. Full Python
  gate finishes 102 cases (100 pass, two Windows symlink privilege skips).
- The actual mounted WebView2 gate passes 17 checks using a real owned legacy
  row and persistent QA task: accept an original response, review/confirm its
  original directory, save exact bytes/hash under manual control, maintain
  focused controls/path, update native bounds and reconcile staging. Download
  URL executes once; cache and receiver inventory end empty. The generated QA
  module now has non-overlapping markers and a syntax preflight; its owned tab
  is cleaned even if the test page never starts.
- Contracts and deployed overlays match. Same single deployment restored
  headless, about 836 MiB; no extra native build/Home or published release.
  Remaining full-goal work includes ask-profile interaction, remote transport,
  additional crash/network faults, complete frames/vision/drag/Android support
  and final lifetime/OOM/performance/package checks. Goal remains active.

## D12u — preserve primitive JSON values in isolated native replies

- Fix the private CDP return-value extraction shared by trusted automation.
  The previous helper decoded string tokens before embedding them in a JSON
  reply: empty strings became missing values and other strings lost their
  quoting/escaping. Retain the raw JSON token instead; absent/unserializable
  values still fail explicitly.
- Add seven actual WebView2 root-world cases for empty/escaped Unicode strings,
  null, booleans and numbers. The complete native gate passes 1,371 assertions,
  including existing input/dialog/popup/upload/download/capture checks, with no
  new exception diagnostics and empty final transfer caches. The focused
  download gate also passes all 308 assertions. Same Home restored headless.
- An earlier complete run timed out during the download phase; it did not pass.
  Its exact native command was not retained, and the cause is not established.
  Failed probes now include bounded command/action/kind metadata and an ordinal,
  without scripts, form data or credentials. The successful later runs do not
  establish that the intermittent timeout is permanently resolved.
- Cross-site frame routing has separate development changes awaiting its own
  review/commit. This bug fix does not claim public cross-origin frame support
  or completion of the full Browser Use goal.

## D13a — private native cross-site frame/session foundation

- Add a per-view native registry and isolated execution for same-site frames,
  cross-site OOPIFs and nested frame processes. Attach only related iframe
  targets, route session events explicitly and validate ancestry against the
  original view. No global target enumeration, universal-origin worlds, new
  browser dependency or weakened site isolation is used.
- Give each frame/view lifetime and document fresh public aliases; reject
  foreign/recreated-view identities and old child-document tokens before script.
  Close/navigation/manual takeover terminate pending jobs. Sent evaluations
  retain uncertainty on errors. New callback captures are weak and replies are
  one-shot; a full native allocation/lifetime adversarial audit is still open.
- Bound frames/sessions at 64 each, pending jobs at 16, tree depth at 32,
  scripts at 128 KiB, protocol copies and job time. Explicit quota errors report
  bounded usage and recover on later navigation. Detach over-quota sessions.
- The actual WebView2 gate passes 41 assertions: four document contexts with a
  confirmed OOPIF and nested ancestry, forged page-world runtime rejection,
  primitives, navigation/foreign-view/manual/close/recreate checks, mutation
  uncertainty and both 70-frame saturation fixtures followed by recovery.
  One HTTP worker bounds idle preconnect sockets so the fixture itself cannot
  block the remaining requests. No model/external-site calls or crash artifacts.
- Full Python gate: 102 cases (100 pass, two Windows symlink privilege skips).
  Full JS gate: 98 pass. Actual existing public C/JS UI gate passes 19 actions,
  four navigations and five wait conditions. Native baseline passes 1,371
  assertions; the earlier intermittent download timeout remains unroot-caused
  as recorded in D12u. Contracts and deployed overlays match.
- This commit adds private native primitives and their build wiring only.
  Public observation/reference/geometry/input/upload/privacy routing across
  these frame contexts is the next stage. Android and remaining full-goal
  lifecycle/OOM/transport/performance/package gates remain open. The single
  approximately 835 MiB deployment is restored headless; nothing is published.

## D13b — combined isolated frame observations and exact reference resolution

- Add an internal read-only coordinator over the native frame registry. Each
  document runs a local-only DOM runtime, so same-origin descendants are not
  copied again from their parent. Qualified references bind native frame/doc
  and local runtime/element identities; child navigation cannot redirect one
  frame's `e1` to another document's `e1`.
- Semantic lookup proves uniqueness across searched frames, reports globally
  distinct ambiguity candidates and supports narrowing by exact frame. Loading
  or inaccessible frames prevent a false unique match. Revalidate root and
  relevant tree identities before delivering a combined observation/location.
- Apply aggregate limits for frames, nodes, elements, UTF-8 text and serialized
  metadata; check one operation deadline and caller checkpoint between native
  calls. Partial observations report their unavailable/budgeted frames. Strip
  URL credentials and preserve the existing omission of form values.
- Twelve adversarial coordinator cases pass; the complete sequential JS gate
  now passes 110 cases. The actual WebView2 frame gate passes 54 assertions,
  including unique combined traversal, cross-frame ambiguity, exact qualified
  references, child/root invalidation, local node budgets and form privacy.
- Strengthen hostile-page tests with the current runtime version and
  nonconfigurable markers for both runtime modes. The native baseline passes
  1,373 assertions; actual public C/JS UI passes 19 actions, four navigations and
  five wait conditions. No new exception diagnostics or retained transfer
  reservations. Contracts/deployed overlays match; same Home is headless.
- These helpers are deliberately internal until native parent geometry, hit
  testing and per-frame input guards are connected. Public observations/actions,
  option/selection, upload and capture adapters still need that integration.
  Earlier intermittent startup/download timing issues and the remaining full
  platform/lifecycle/OOM/transport/performance/package gates remain open.

## D13c — resolve and hit-test real iframe owners in parent processes

- Add private native `frames.owner`, pinning child and parent documents while
  resolving the browser's actual owner node into the parent isolated world.
  Read content-box geometry with fractional borders/padding, map a child point,
  and require it to hit that owner through the visible parent/shadow path.
  Hidden/inert/covered/clipped owners, excessive ancestry and unsupported CSS
  transforms/zoom fail explicitly. No model/node/protocol IDs are substituted.
- Temporary remote objects use a per-job object group released at terminal
  success/failure. Child/parent navigation or session/control changes reject
  pending geometry. Existing frame/job/copy/time quotas remain in force.
- Actual native frame gate passes 70 assertions. A new two-level OOPIF fixture
  proves exact parent routing, fractional coordinates and one trusted target
  click. Invalid/root points, overlay/hidden/transform refusal and fresh moved
  geometry pass without repeating the target effect. An initial test read a
  page-world JavaScript counter from an isolated world; the trusted DOM result
  proved the click arrived. The fixture now exposes its synthetic count in the
  DOM, and only bounded read polling is used after the single click.
- Complete native baseline passes 1,345 assertions with no new exceptions and
  no retained upload/download reservations. Counts include bounded poll checks
  and vary with timing. Single approximately 835 MiB deployment is headless.
- Public dispatch and native per-frame input fencing are still pending; this
  geometry primitive does not alone make cross-origin actions production-ready.
  Full goal remains active, with earlier timing/lifetime/OOM/transport/Android/
  vision/upload/capture/performance/package work still outstanding.

## D13d — native input pins and cleanup ownership across frame changes

- Add a bounded, typed private `frame_chain` from the exact target through its
  original parents/root. Validate shape, live aliases/documents, adjacency,
  loading/session/error state at admission and each native sequence step.
  Malformed, incomplete, reordered, foreign and over-budget chains fail before
  input. Child-only navigation rejects old input without relying on a changed
  top-level navigation token. Existing unpinned trusted-native callers remain
  compatible until the public frame adapter is connected.
- Fix existing cleanup ownership: an interrupted/skipped press previously
  still emitted its up event. Track the press actually admitted by this
  sequence, release it after interruption, and skip cleanup for presses never
  sent (including a skipped second click). A failed/lost press acknowledgement
  remains conservatively owned and receives one cleanup attempt.
- A compiled test executes the actual C++ guard/sequence with document/control
  changes between steps: no later press/text, cleanup of admitted keys/buttons,
  no unpaired up events, double-click interruption and uncertain press receipt.
  Full Python gate finishes 103 cases (101 pass, two Windows privilege skips).
- Actual frame gate passes 82 checks, including valid nested input, malformed
  chain refusal and child-only replacement with a stable top navigation token;
  the replacement document receives zero old-input clicks. Full native baseline
  passes 1,354 poll-inclusive assertions with no new exceptions or retained
  transfer reservations. Actual public C/JS UI passes 19 actions, four
  navigations and five wait conditions. Last complete unchanged JS gate: 110.
- Next connect fixed-operation frame prepare/verify/options/selection and
  parent geometry to public dispatch, supplying these pins on every input.
  Preserve root upload/capture behavior while adapting their local worlds;
  verify ancestor focus before keyboard input. Complete the remaining input
  cancellation/lifetime/OOM, remote transport, Android, vision and package/
  performance gates. This is active development, not the completed full goal.

## D13e — correct page identities, labels and inherited focus

- Generate document UUIDs from cryptographic `getRandomValues` bytes with v4
  version/variant bits. Initialization no longer depends on secure-context-only
  `randomUUID`. Two tests execute the actual self-contained runtime without
  that API, check stable/current and distinct/replaced document identities,
  and reject missing cryptographic randomness rather than using a weak fallback.
- Extract associated/referenced label text with bounded traversal, pruning
  embedded controls and their values. Wrapped selects keep their human labels
  without concatenated option text. Unlabelled textareas and control-valued
  label references do not expose initial form contents. Option display labels
  remain available through their normal metadata/discovery paths.
- Verify active elements through open shadow roots and same-origin frame
  ancestors. A child document remembering its last active input no longer
  authorizes typing after its parent has focused something else.
- The complete actual native gate passes 1,350 poll-inclusive assertions,
  including wrapped-label selection, initial-text privacy and ancestor focus
  regressions. Existing public C/JS UI passes 19 actions, four navigations and
  five waits. No new native diagnostics or retained transfer reservations;
  same Home restored headless. Multi-frame driver integration is separate work.

## D13e2 — omit editable DOM values from observations

- Exclude editable-region text from readable observations and fallback labels,
  including noneditable chips nested inside an editing ancestor. Retain control
  roles/references and explicit labels/placeholders. Bound composed-ancestry
  inspection and fail closed on excessive depth; do not copy or clone editor
  subtrees while extracting labels.
- A real page fixture includes private editor text and a nested noneditable
  span. The complete native gate passes 1,361 poll-inclusive assertions and
  confirms neither value appears in the structured/readable result. The 122
  sequential JS checks also pass; no new native exceptions or retained transfer
  reservations. This does not claim arbitrary website content is private or
  replace the documented screenshot masking policy.

## D13f — fixed frame operations through the actual dispatcher

- Add `frame-driver.js` over the bounded reader/native registry. Resolve each
  operation into its exact local world, map fresh prepared geometry through
  parent owners, revalidate ancestry and supply the complete input document
  chain. Support verify, option pages/selection, hidden-file input metadata and
  the existing root capture mask. No model-supplied script or raw protocol ID.
- Extend native owner metadata with deep active-element focus. Keyboard checks
  require the intended child and every parent iframe to remain focused. The
  dispatcher now rechecks focus after Select All before sending replacement
  text, and forwards pins on mouse, key and text steps. Transient frame readiness
  remains a bounded wait; cancellation/dialog checkpoints are preserved across
  both success and failure packets. Mutations retain uncertainty without replay.
- Nine targeted driver cases and the full 123-case JS gate pass. Actual
  WebView2 frame gate passes 96 checks using the real dispatcher with a
  controlled UI-receipt status: nested trusted Unicode fill, ancestor/selection
  focus fences, option pages/exact selection, wait, hidden upload pins, root
  screenshot masking and cancellation before input. It also proves runtime
  initialization when `randomUUID` is absent. Preflight now uses explicit UTF-8
  for the astral Unicode fixture rather than the Windows default code page.
- Full Python gate: 103 cases (101 pass, two privilege skips). Existing normal
  public C/JS UI gate passes 19 actions, four navigations and five waits after
  the page fixes; native baseline passes 1,361 poll-inclusive checks. No new
  native exceptions or retained transfer reservations. Source/contracts match
  the one headless deployment, approximately 840 MiB.
- Normal workbench activation is intentionally still pending migration of all
  dependent operations. Next route ticketed upload/clear into pinned frame
  contexts/sessions, adapt picker target/event ownership, then add frame-aware
  drag guards and aggregate screenshot privacy. Do not silently switch upload
  targets into the old root world or bypass their operation receipts. The
  managed cross-frame C/JS path, Android, vision, lifecycle/OOM/network/performance
  and final package gates still belong to the unchanged full objective.

## D13g — ticketed upload and clear in exact frame worlds

- Bind staged transfers to the original local input reference and complete native
  frame document chain at admission, not just the top-level navigation token.
  Check that chain while receiving/sealing bytes and before each native step.
  Unassigned staging expires when any ancestor document changes; dispatched
  website bytes remain retained until confirmed release. Identity comparisons
  use bounded parsed tuples independently of current document liveness, so
  looking up a completed receipt never replays an assignment into a new page.
- Resolve the input in its selected isolated frame world and route DOM delivery,
  verification and object-group release through its owning CDP session. Clear
  uses the same tracked operation key and frame pins, preserving its explicitly
  synthetic input/change event receipt. Reject qualified targets without native
  bindings before reading source files; revalidate the same tuple after sealing.
- The actual WebView2 frame gate passes 111 checks: hidden nested OOPIF file
  assignment, exact complete SHA-256, one trusted upload event pair, one
  synthetic clear pair, cached receipt after clear/navigation without replay,
  different-chain identity conflict and child navigation before delivery. Cache
  ACLs remain private and all reservations drain on close. The test compares
  event fields independently of JSON object member ordering.
- Sequential gates: 127 JS checks; 103 Python cases (101 pass, two Windows
  symlink privilege skips); native baseline 1,358 poll-inclusive assertions with
  no new native diagnostics or retained caches. The normal public C/JS and
  managed upload gate also passes, including original picker acceptance/cancel,
  clear, retained-byte status/release and owned-tab read-only denial.
- Normal workbench activation still awaits frame-owned picker interception,
  target/cancel routing, frame-aware drag and screenshot privacy aggregation.
  Parent-frame inert policy for hidden file inputs must also be covered before
  activation. These checks exercise the real frame driver/dispatcher with a
  controlled UI-operation status; they are not the final managed frame gate.

## D13h — frame-owned picker interception, acceptance and cancellation

- Arm/disarm the root and its bounded attached iframe sessions sequentially.
  New OOPIFs inherit Agent picker suppression before becoming input-ready.
  Session-aware events retain only the owning frame/document chain and opaque
  picker identity; raw backend nodes, session IDs and device paths stay native.
- Pin the real intercepted input in that frame's local isolated world, then
  reuse the ticketed upload pipeline with the exact native chain. A different
  document or world cannot claim an already resolved picker. Readiness, byte
  staging and assignment also compare the picker binding key. Cancellation
  resolves the original backend node through its own session and isolated
  context. Child/ancestor navigation expires the picker without sending cancel
  events to its replacement document. Manual takeover disarms all known
  sessions and cancels the pending original input exactly once.
- The real frame gate passes 138 checks, adding trusted nested picker opening,
  repeat-click blocking, full SHA-256 upload/consumption, explicit and takeover
  cancellation, child-navigation expiry and inheritance by a newly attached
  OOPIF. All native reservations drain; no new crash diagnostics. Native
  baseline passes 1,371 poll-inclusive checks. Normal public C/JS/managed upload,
  legacy pickers and owned-tab read-only denial also pass.
- Sequential JS gate: 130 pass. Python: 103 total, 101 pass and two symlink
  privilege skips. Shared contracts, reviewed overlays and binary/source receipt
  checks remain required. The normal workbench is still pending parent-inert
  upload policy, frame drag and screenshot privacy aggregation before activation;
  this milestone does not claim the managed frame path or Android is complete.

## D13i — enforce composed parent-frame upload policy

- Add a fixed, read-only native owner policy for hidden file inputs. Resolve
  the real iframe owner in each parent process and inspect bounded composed
  ancestry for `inert`, including assigned slots and shadow hosts. Hidden
  inputs/frames remain supported; pixel visibility is not an upload permission.
  Return only policy state and pinned owner identities, not DOM values.
- Apply the policy before resolving picker targets or exposing direct native
  upload bindings, and revalidate the complete document chain afterwards.
  Existing post-seal target revalidation performs it again before assignment.
  This is fresh actionability checking, not a claim that a website cannot change
  its DOM after the last check. Native dispatch continues to fence original
  frame documents and local input identities independently.
- Real WebView2 frame gate: 143 pass, including inert outer/inner parents rejected
  before any file digest/read, and a hidden enabled iframe still accepted for
  hidden-input preparation. All previous frame picker/upload/input cases pass;
  no new native diagnostics or retained cache reservations. Sequential JS gate:
  132 pass. The previous unchanged full Python (101 pass/two privilege skips),
  native baseline (1,371 poll-inclusive checks) and normal public upload gates
  remain the last full baseline; they were not rerun for this fixed frame policy.
- Next migrate frame drag and screenshot privacy aggregation, activate the
  normal workbench adapter, then verify managed C/JS frame workflows and the
  remaining platform/lifecycle/network/vision/package scope of the full goal.

## D13j — pin native frame drags and preserve intercepted HTML5 payloads

- Route stable source/destination preparation through the frame driver and
  submit both original native document chains with one drag identity. Native
  admission rejects malformed, partial or replaced chains before input. Every
  later phase checks both chains; cancellation/navigation preserves effect
  uncertainty and drains the mouse release independently of frame liveness.
- Enable bounded drag interception for the main view and selected source
  session. Accept payloads only from that view-level input handler or the
  selected source session, never a different OOPIF. Release both interception
  policies separately; vanished sessions have no remaining controller to reset.
  Native calls now use the exactly-once CDP completion wrapper.
- An actual regression initially showed a child `dragstart`/`dragend` with no
  `drop` and a misleading pointer-only delivery result when interception was
  limited to the selected session. The combined view/source policy and routing
  fixes this observed path. The bounded failure receipt is retained privately at
  `.mdo/native-frames-html5-failure.json`; no payload or source file is logged.
- Actual WebView2 frame gate: 164 pass. It verifies trusted nested pointer and
  HTML5 gestures, one drop/end, exact payload, OOPIF-to-root and root-to-OOPIF
  delivery, receipt cancellation after mouse-down, stale admission, child-only
  navigation during a held drag, no replacement-page click and subsequent input
  recovery. All prior frame upload/picker/owner tests pass, with no new native
  diagnostics and no retained transfer reservations.
- Sequential JS gate: 134 pass. Python: 103 total (101 pass, two privilege
  skips). Native baseline: 1,362 poll-inclusive checks, including existing drag
  rejection/cleanup, root uploads/pickers/downloads, no diagnostics or cache
  remnants. Cross-frame screenshot mask aggregation remains before activating
  the normal workbench; public managed frame checks and the remaining Android,
  vision, lifecycle/OOM, network/performance and package gates are still pending.

## S1 — mask rendered controls independently of semantic visibility

- Screenshot masking reused the interaction predicate, which intentionally
  excludes inert/aria-hidden/hidden elements from Agent locators. That omitted
  private controls which still painted, including a CSS override of `hidden`.
  A regression against the real self-contained runtime reproduced zero masks
  for three rendered controls before the fix.
- Mask traversal now checks computed painting visibility and client rectangles,
  independently of semantic/action availability. Keep the existing locator
  behavior and increment the isolated runtime version so old instances refresh.
- Actual native baseline passes 1,369 poll-inclusive assertions, checking that
  each new fixture control is visible in raw pixels and fully covered in the
  returned masked PNG. The full 164-check frame gate and 135 sequential JS
  checks pass, with no new native diagnostics or retained native reservations.
  Previous unchanged Python baseline remains 101 pass/two privilege skips; no
  unrelated rerun was needed. Cross-frame mask aggregation is still pending.

## S2 — exact local mask/iframe-owner identities for capture composition

- Local-frame capture scans only its own document and open shadow roots. Each
  iframe stays an opaque mask carrying that exact local element reference;
  capture never accesses its `contentDocument`. Report bounded node usage,
  viewport and scroll metadata so a coordinator can share one global budget.
  Exceeding the assigned node budget fails without returning partial masks.
- A fixed native `capture_owner` operation resolves the real iframe node in
  its parent isolated world and pins the same local reference/document. Return
  fractional content/border boxes, scroll and viewport, with composed-ancestor
  transform checks. Inert/aria-hidden state does not suppress rendered pixels.
  Unsupported geometry retains the owner identity so its opaque mask can stay
  intact. No root/global protocol IDs or arbitrary selectors are exposed.
- Actual frame gate: 173 pass, adding exact outer/nested mask identity, local
  scan boundaries, fractional coordinate precision, painted inert owners,
  unsupported-geometry identity and stale-child rejection. Native baseline:
  1,383 poll-inclusive assertions, including actual private pixel masking. All
  reservations drain and no native diagnostics are generated. Sequential JS:
  136 pass. Previous unchanged Python baseline remains 101 pass/two skips.
- This is composition infrastructure only: it does not yet remove any iframe
  mask or enable element screenshots through child frames. Next aggregate
  readable-frame masks with clipping, unchanged-document/layout checks and
  opaque fallback, then enable and verify the normal public C/JS/managed path.

## S3 — compose exact cross-frame privacy masks and element images

- Add a bounded frame capture coordinator. Resolve masks in each local world,
  replace only the opaque mask bearing its real native owner reference/document,
  clip at each child viewport/content box and translate fractional CSS coordinates
  into the root document. Share a 6,000-node scan budget and a 256-mask result
  limit; unreadable/budget-limited/unsupported child content stays opaque.
- Crop nested element images through the same ancestry. Compare owner boxes with
  their original masks, validate scroll/viewport/DPR, and revalidate the frame
  tree. Before/after capture stamps include documents and layout; a child change
  withholds the image even when top-level navigation/region are unchanged. Caller
  cancellation still wins over an unreadable-frame fallback. No scripts or
  protocol IDs are accepted from the model.
- Seven coordinator regressions cover fractional nested clipping, element crops,
  wrong/missing owner identity despite identical rectangles, opaque transforms,
  unreadable/budget fallback, cancellation, changed scroll/documents, DPR and
  aggregate mask limits. Full sequential JS gate: 143 pass. Python: 103 total,
  101 pass/two Windows privilege skips.
- The actual frame gate passes 181 checks, including visible unmasked green
  pixels in a nested OOPIF, its private input pixels still covered, an exact
  nested element crop, and a child navigation deliberately inserted between
  native capture and the final mask scan returning no image. No new native
  diagnostics or retained transfer reservations. Last unchanged native baseline:
  1,383 poll-inclusive checks; normal public frame activation is next.
- The normal workbench still uses the legacy adapter in this commit. Strict
  inventory/readiness is retained; incomplete process attachment can return a
  bounded readiness error. Public C/JS/managed frame integration, Android,
  vision, lifecycle/OOM, network/performance and final packaging remain active.

## S4 — enable the frame driver in the normal workbench

- Advertise a per-content-view native `frame_automation` flag and enable the
  fixed driver for supported ordinary workbench operations. Older backends keep
  their prior adapter. Public SDK/Agent calls use real UI receipts and original
  ownership/permission scopes; no controlled receipt shortcut is used there.
- Add the normal nested-OOPIF C SDK gate: 17 operations, Unicode fill, selection,
  private element screenshot, workspace and picker uploads with complete SHA-256,
  clear/release and picker cancel. Managed nested fill/images pass; an owned
  read-only browser allows observations/images and rejects fill.
- Integration found two download defects. The fixed post-input 1.1-second window
  expired during an original slow POST navigation. Wait only on native metadata
  until that navigation settles, then drain a bounded callback turn, obeying the
  original deadline/cancellation and never resending input. A deterministic
  slow-response regression failed before the fix; real delayed POST passes with
  exactly one server request, as does POST popup/download cancellation.
- NavigationStarting also discarded frame identities when a file response left
  the source DOM unchanged. Retain at most one bounded previous inventory and
  reuse identities only on a fresh matching raw frame, parent and nonempty
  native loader. Real loader changes, empty proof, reparenting and detachment
  invalidate them. Compiled actual-source rules and a real direct-download test
  verify this without relying on URL equality. Pending jobs still expire at
  navigation start; no old evaluation survives it.
- One earlier first public image returned `capture_changed`; its exact cause was
  not captured. Add structural-only mismatch categories, excluding page text or
  identity values. Three independent first-image measurements and later SDK/
  OOPIF images passed. This does not establish a root cause for that transient.
- Sequential checks: 145 JS pass; Python 104 total, 102 pass/two Windows symlink
  privilege skips; private frame gate 182 pass; native baseline 1,369
  poll-inclusive checks. Normal C/JS SDK, managed root and OOPIF, uploads, all
  downloads, five exact-byte saves and 17 mounted download-UI checks pass.
  Native upload/download reservations drain, with no new native diagnostics.
- Source and binary stay in the single existing deployment/Home. No model calls,
  runtime dependency installation, extra checkout or publication. Next implement
  offscreen parent scrolling, vision input and Android with explicit capability
  limits, followed by lifecycle/OOM, network/performance and packaging gates.

## S5 — verify offscreen scrolling before adding another input path

- A focused normal-workbench C SDK test places the root iframe, nested iframe
  and target button below three separate document viewports, across two real
  OOPIFs. Read-only observation leaves the initial root scroll at zero.
- Existing `scrollIntoView` already traverses these browser ancestors. The real
  action passes final geometry/document-chain checks, emits one trusted click,
  reports the exact count of one and preserves the qualified element reference.
  Root scroll is measured at 1,690 CSS pixels in the 1,024×768 fixture view.
  No extra native scroll command or duplicate implementation was introduced.
- Add the reusable sequential probe and correct the previously unverified
  offscreen-parent limitation. This is a bounded layout regression, not a claim
  about all transforms, smooth scripts or offscreen drag endpoints. No model
  calls or additional deployment/Home. The S4 product source is unchanged;
  its 145 JS, 102 Python pass/two skips and native gates remain applicable.

## V0 — bounded completed-image references and private tile fingerprints

- Supported screenshots now issue a 60-second, browser-scoped visual reference
  with the exact delivered image dimensions/coordinate system. Only the newest
  image per browser is retained; references cap at 32 entries, 192 KiB each and
  2 MiB total. Expiry/eviction/one-shot consumption are implemented internally.
  Public coordinate input is intentionally not exposed before native fences.
- Hash padded 64-pixel tiles only after privacy masking and final sizing, using
  SHA-256. Keep hashes/document/frame/layout metadata in trusted-UI memory;
  retain no bitmap, base64, page text or profile path. Tests detect local and
  neighboring-tile changes, tolerate an unrelated distant tile change, reject
  invalid rasters and enforce lifetime, browser scope, immutable snapshots,
  quotas and failed-admission preservation. Bitmap cleanup still runs on a
  failed raster inspection, and the inspector sees both privacy scans' masks.
- The existing managed C image formatter dropped new screenshot metadata. Add
  strict ID/coordinate/dimension/lease validation and copy only the five public
  descriptor fields to Agent text, alongside the binary image. Malformed
  descriptors withhold the image; extra private fields are omitted. Test through
  the real worker and writer, including old-host images with no descriptor.
- Sequential JS: 151 pass. Python: 105 total, 103 pass/two Windows symlink
  privilege skips. Three normal first captures pass with bound descriptors
  (2,156 / 2,062 / 1,953 ms; these are measurements, not a performance budget).
  Actual normal public/managed OOPIF input/upload/private-image gate passes,
  including managed descriptor dimensions matching its image metadata.
- Contracts and deployed overlays match. The same Home/deployment is reused;
  no native rebuild, new runtime dependency or model call. Next add exact native
  frame-snapshot input fences and scene/point validation before coordinate input.

## V1 — fence native input against the complete captured frame tree

- Add the private `frame_snapshot` input proof and explicit native capability.
  Admission and every input step require the exact bounded set of native
  documents, original parents, ready sessions and ancestry reaching `f0`.
  Duplicates, missing/new frames, foreign documents, reparenting, loading or
  detachment refuse delivery. Legacy trusted-native and DOM calls without this
  field keep their existing chain behavior; no public coordinate API yet.
- Between steps the snapshot guard performs no allocation. Compile the actual
  guard with allocation deliberately refused to verify this path. Further
  actual-source tests change a child after mouse move, a parent after press,
  and add another frame mid-sequence: subsequent presses stop, admitted release
  still runs once, and an interrupted double-click never sends its second press.
- The real native frame gate passes 190 checks, adding valid snapshot input,
  malformed/duplicate/missing/foreign/reparented snapshots and rejection after
  child-only navigation while the root navigation token stays unchanged.
  The unchanged browser baseline passes 1,368 poll-inclusive checks, with no
  native exception diagnostics and zero remaining upload/download reservations.
- Python: 105 total, 103 pass/two Windows symlink privilege skips. Last unchanged
  JS baseline: 151 pass. Deployed overlays, native input fragment and final
  binary hash receipts match. One native source/object tree and Home are reused;
  no model calls. Next wire scene/coordinate validation to the shared action API.

## V2 — internal screenshot-point preflight and one native gesture

- Add a fixed internal visual pointer path for click/double/right/middle click,
  hover and wheel. It binds integer image pixels to the actual crop/output
  dimensions, root scroll and viewport; checks document/navigation/frame/layout/
  DPR; rejects masked or off-viewport points; and prepares complete native pins.
  Public C/JS/Agent coordinate admission is still the next milestone.
- Before input, recapture that same bounded scene with the same privacy masks
  and sizing. Compare only the chosen padded tile with the saved SHA-256, so a
  distant animation need not stop the gesture. Current point verification reads
  one small tile rather than hashing another full image. Consume the exact image
  lease before one gesture, share the global screenshot budget, obey cancellation
  and the original deadline, and drain only this validation capture on exit.
- The first real integration safely rejected before input because composed
  captures did not carry their root scroll/viewport metadata. Add those fields
  and a root-versus-child-offset regression. No coordinate fallback or guessed
  frame geometry was introduced. Final privacy checks remain in document space
  even when the root is scrolled.
- Six preflight regressions cover coordinate mapping, one-shot leases, foreign/
  expired/wrong-document references, unsupported input, bounds, crop/DPR/layout,
  masking, changed child context, local versus distant pixel changes and cancel.
  Another check fences overlapping visual validation and normal screenshots
  across browsers before native work. Full sequential JS gate: 158 pass.
- Actual native frame gate: 197 pass, including a trusted click into an exact
  cropped nested OOPIF image, used-image rejection, changed pixels rejected and
  private field mask denial without extra input. No new native diagnostics or
  retained transfer reservations. The native binary is unchanged from V1;
  previous 103 Python pass/two privilege skips and 1,368-check native baseline
  remain applicable. Same deployment/Home, no model calls or added dependency.
- This preflight is not an atomic website transaction. Public pointer contracts,
  visual keyboard/drag, Android and final lifecycle/performance/package gates
  remain active.

## V3 — public C/JS/Agent visual pointer actions

- Extend the existing shared action target with a completed-image ID and integer
  image-pixel x/y, without adding a separate service or permission bypass. C
  rejects missing/mixed selectors, malformed IDs, noninteger/out-of-range points
  and unsupported field/keyboard/drag operations before browser lookup. Generate
  the matching C registration and JS schema from the same contract; reuse the
  screenshot formatter's strict ID helper. Update the Agent skill/description.
- Add a loopback Canvas fixture in the root and a true cross-process child.
  Normal public C/JS tests pass a scaled canvas click, nested canvas click,
  double/right/middle click, hover and wheel with trusted website events.
  A retained operation ID recovers the original result without another click;
  a new operation cannot reuse the consumed image. Foreign-browser images and
  changed selected pixels refuse input; a fresh image recovers the changed scene.
- Actual Agent tools pass the same canvas image/point path. An owned read-only
  browser permits screenshots but rejects the point action, and its click count
  remains zero. Full-access actions add no extra prompt. Test credentials remain
  in memory; no external website or model request is used.
- Sequential checks: 158 JS pass; Python 106 total, 104 pass/two Windows symlink
  privilege skips. Public normal workbench canvas gate passes all six pointer/
  wheel operations, C/JS recovery and managed permissions. Native binary is
  unchanged from V1 (last native baseline 1,368 and frame gate 197); contracts
  and deployed overlays match after applying the final comment-only host edit.
- Same project deployment/Home/source/object cache. No release/publication.
  Visual keyboard/drag, directory upload coverage, Android, lifecycle/OOM and
  final network/performance/package checks remain active.

## E0 — dependency-free Android isolated-world boundary foundation

- Inspect the actual Android browser source and the installed phone's metadata.
  Its existing manual browser uses page-world click/type and has no typed native
  Browser Use state/input/transfer pipeline. ADB now sees one authorized API 36
  phone with system WebView 138.0.7204.179; installed mdo apps/data are unchanged.
  Do not infer execution-world support from that version; probe provider features.
- Implement an own-code adapter to the installed WebView's published support
  boundary. Resolve methods in the provider's actual class loader, without an
  AndroidX AAR or hidden framework call. Pin the consulted Chromium interface
  revision/URLs/hashes. Non-default world creation and isolated message listeners
  require the explicit feature; a separate profile feature gates profile setup.
- Admit only the exact view/main frame and canonical original reply peer.
  Navigation/destroyed frames retire peers and jobs; no late message can rebind
  a retired document. Enforce message/script/result/job/deadline limits, one
  completion, monotonic time and possible-effect uncertainty after dispatch.
  Failed timer admission sends no script. Partial registration and failed cleanup
  remain unconfirmed; cleanup can retry only its owned listener/script.
- Contract gate passes 29 checks using a genuinely separate provider class
  loader. The actual production adapter also compiles with Java 8, matching the
  APK builder target, using the existing WSL JDK. Reuse one small QA classes
  directory; add only the Java source to the reviewed source overlay.
- This is a protocol/lifetime foundation, not an APK/framework/device pass and
  not Android automation activation. SDK/NDK paths remain unresolved (the old
  documented cache is absent); no toolchain/browser was downloaded. Native UI
  integration, trusted input, capture/privacy, dialogs/pickers/transfers and
  real provider capability checks are next. Windows product source is unchanged:
  last JS 158 pass, Python 104 pass/two skips and public visual gate still apply.

## E1 — minimal Android APK and real unsupported-provider receipt

- Reuse the existing WSL JDK and the pinned SDK archive installer. Download only
  API 36 and Linux build-tools 36.0.0: 129,615,669 bytes; 247.3 MiB installed,
  approximately 60 GiB free disk remains. Verify archive hashes and ownership,
  discard completed archives, retain one tool cache and no NDK/browser/runtime.
- Build/dex/align/sign a 33,171-byte Java dev client containing the actual world
  adapter. One stable package/output/signing key is reused; it contains no mdo
  Home/service/account data. Its manifest has no network permission. Source/APK
  and toolchain hash receipts gate device execution. No release signer is used.
- The phone required the OS installation confirmation; after the user completed
  it, verify the actual installed APK bytes and reuse it without a second install.
  Collect only a fresh nonce-bound log from the dev app's own PID/tag, destroy
  its owned view and force-stop only this dev app afterward. Preserve installed
  production mdo and knowledge QA apps/data. Never clear global device logs.
- Actual API 36 / system WebView 138.0.7204.179 reports 72 features. Profile and
  message-listener features are present; isolated frame/world injection is absent.
  The real production adapter correctly returns `isolated_world_unsupported`
  before page execution. This is a verified unsupported-provider outcome, **not**
  Android automation passing. Supported-world isolation remains unverified on a
  device; the user has been offered an optional system-provider update and the
  rest of development continues. No system component is automatically replaced.
- Re-run 29 JVM protocol/lifetime checks successfully; compile the actual Java
  adapter and Activity with Java 8/API 36, verify the signed APK, and execute the
  actual device feature gate. Windows/product C and JS sources/binaries remain
  unchanged; their previous gates remain applicable. Full Android integration,
  visual keyboard/drag, directory uploads and final lifecycle/performance/package
  gates remain active.

## E2 — product Android capability admission and no page-world fallback

- Separate bounded installed-provider feature inspection from content-provider
  creation. Unsupported inspection/open creates no content adapter. Keep stable
  feature guards (development aliases do not enable production contracts) and
  add malformed/oversized feature-list coverage. JVM contract gate: 34 pass.
- Apply reviewed Android capability/admission fragments to the actual pinned
  `MdoBrowser.java`, and copy the world helper in the existing APK overlay. Native
  capabilities report readiness separately from world/profile support; manual
  state explicitly advertises `automation:false`. Supporting providers still
  remain unavailable until the full native adapter is implemented and verified.
- Shared workbench preflight rejects unsupported automation before sending a
  URL, creating a native view or revealing an action panel. Preserve a structured
  C/JS result, allowlisted capability reason and no-effect receipt. Existing
  manual tabs refuse page evaluation/input, and native admission independently
  blocks automated/intercepted creation and legacy Agent page-world operations.
  Manual browsing/takeover/stop/close/cleanup retain their normal path.
- Build and verify the 45,459-byte dev APK with the actual product browser source
  and adapter. Require deployed-overlay checks and source/APK receipts. The real
  phone confirms the product's unavailable descriptor, all three admission
  rejections and allowed manual/cleanup admission. This is an actual framework
  capability/admission gate, not complete Android UI transport/automation passing.
- The user elects to keep system WebView 138. Do not upgrade it or keep prompting
  for an update. Its unsupported result remains explicit while independent work
  continues. Existing mdo apps and data remain unchanged; only the same tiny
  development client is updated, inspected and stopped.
- Sequential JS regressions: 161 pass plus the new shared preflight test (162
  unique checks total). Windows actual public C/JS SDK gate passes 19 actions,
  four navigations, five wait conditions, DOM/frame routing, screenshot modes,
  drag, dialogs, popups, cancellation and duplicate-operation recovery. Native
  binaries and C are unchanged; previous Python 104 pass/two privilege skips
  remain applicable. Overlays match; same Home is restored to headless mode.
- Full Android native input/capture/frames/transfers, visual keyboard/drag,
  directory uploads and final lifecycle/performance/package gates remain active.

## V4 — shared image-point dragging and original-source cleanup

- Extend the shared C/JS/Agent drag target with two integer points from one
  completed screenshot. Reject mixed selectors, malformed/out-of-range points,
  different image IDs, masked/offscreen endpoints and short drags before input.
  Generate C/JS declarations and tool descriptions from the same contract; keep
  one gesture per image lease and durable operation recovery without replay.
- Validate both padded pixel neighborhoods in one bounded native recapture.
  Fixed isolated hit testing follows the exact native iframe owner at each
  point, checks its document and ancestry, and returns private source/destination
  chains. No model script, native session or frame pin is accepted. Native drag
  admission and every step also validate the entire screenshot frame snapshot;
  changes outside the two selected chains interrupt the gesture.
- Reuse one drag dispatcher for DOM and image targets, including original action
  cancellation, native delivery uncertainty, picker/download checkpoints and
  cleanup. A distinct native `frame_snapshot_drag` feature refuses older hosts.
  The actual public gate covers root/child Canvas pointer drags, HTML5 drags in
  each frame and both cross-process directions, retained-result recovery and
  consumed-image rejection. Local/network sibling replacement releases the
  original press and preserves possible-effect uncertainty.
- The longer sequence exposed a real native cleanup defect: the Canvas source
  reported two trusted starts but only one end after cross-widget drops, and a
  subsequent pointer sequence could be acknowledged without its expected page
  effect. Blindly cancelling at the source repaired that scene but produced two
  ends for one start in the older DIV-source regression. Add a bounded original-
  document isolated observer and check actual trusted source state before ending
  a still-active source. Remove its listeners/state before admitting another
  gesture, and block unconfirmed cleanup instead of claiming success. Both
  Canvas sources now have exactly two starts/two ends; legacy source checks and
  explicit observer-removal checks also pass. No extra drop is sent.
- Keep the failed evidence and distinguish it from assumptions: the first
  network-only interruption check had no source-end/overlap evidence. Stronger
  source counters and local `srcdoc` replacement exposed and verified the cleanup
  issue. The test collector's 16 KiB default also truncated a larger failure
  report; request/verify an explicit 256 KiB bounded receipt. Correct the managed
  fixture oracle to use observed viewport dimensions, since Agent image metadata
  intentionally omits the private capture-region fields.
- Sequential verification: JS 166 pass; Python 107 total, 105 pass/two Windows
  symlink privilege skips. Actual native frame gate 217 pass and native baseline
  1,318 pass (both counts include polling, not a performance comparison). No new
  native diagnostics; private upload/download reservations return to zero.
  Full normal public C/JS visual gate and actual managed visual click/drag and
  owned read-only denial pass after the final native fix. Focused gates preserve
  their own scope; the final full public receipt is passing. No models/external
  websites or dependencies. Same single object variant/deployment/Home.
- Visual keyboard, directory uploads, full Android integration and final
  lifecycle/OOM/network/performance/package checks remain active. In particular,
  audit original-response downloads that start navigation but retain the DOM:
  the source-observer cleanup readiness check needs a dedicated lifetime gate
  before any claim of full lifecycle completion. Android's retained WebView 138
  remains explicitly unsupported, as chosen by the user.

## V5 — retained-document drag observer cleanup

- Confirm a real lifecycle defect in the V4 source observer: a drag starting an
  original POST download advances native navigation readiness but keeps its DOM
  and isolated execution context. Cleanup previously skipped that context; the
  following drag failed before input with `Cannot arm the original drag source
  observer`. Before the fix, the retained document had one press/release and a
  stale observer. This was an actual native fixture failure, not a speculative
  provider/browser explanation.
- Separate own-resource removal from input authority. Cleanup reuses the exact
  captured context/session and operation key even when navigation readiness or
  frame inventory changes. It never creates a replacement world or sends input.
  Native source-end events still require the original document. Missing inventory
  and changed aliases are not proof of a replaced source; failed cleanup without
  positive loader replacement evidence retains a blocked view. A lost arm reply
  followed by confirmed removal no longer incorrectly leaves a permanent block.
  Dispose the owned listeners on trusted `pagehide` as well.
- Add a compiled actual-source regression for retained/absent/replaced contexts,
  lost replies, delayed cleanup, malformed responses, closed views and active
  intercepted sources. Add a real original-response/navigation/history gate.
  After the fix, the original DOM is retained, the original response is requested
  exactly once and its size/SHA-256 matches. A following drag succeeds with two
  presses/two releases and no observer. Real navigation/back also recovers with
  one additional gesture. The reported BFCache document comparison is false:
  this does not prove BFCache restoration handling.
- Sequential verification: Python 108 total, 106 pass/two Windows symlink
  privilege skips; JS 166 pass. Actual native frame gate 217 pass (poll-inclusive,
  not performance evidence). Full normal public C/JS visual gate passes seven
  operations and six root/child/cross-process drag combinations, plus managed
  click/drag and owned read-only denial. Lifetime gate passes 16 assertions with
  no new native diagnostics and zero remaining transfer reservations. Reuse the
  same native object variant, deployment and Home; no model/external-site calls.
- Android keeps the user's current WebView 138 with its explicit unsupported
  automation result. Visual keyboard, directory uploads, full Android transport,
  BFCache/adversarial lifecycle, OOM/network/performance and final packaging
  remain open. This fix does not complete the long-term goal.

## V6 — focus-bound image keys through C/JS/Agent

- Allow the existing shared `key` action to use one completed image point.
  Preserve browser/document/image ownership, pixel/privacy validation, one image
  lease per operation, durable recovery and read-only permission admission.
  Text entry/fill, selection and check continue to require observed DOM targets;
  masked fields are not made accessible through coordinates.
- Fixed isolated hit testing binds the visible focusable control through exact
  native iframe owners and open shadow roots. Return its qualified element and
  local document only to the trusted dispatcher, never the model. The entire
  screenshot frame snapshot and original leaf-to-root chain fence focus/input.
  Require the separate native `frame_snapshot_keyboard` feature before effects.
- Add an internal native focus operation using the original view/controller and
  a fixed isolated `focus({preventScroll:true})` helper. It does not activate the
  control by an extra click. Immediately before the key press, verify the exact
  element's deep active-element focus, document focus and native frame/document
  snapshot again. Keep an admitted key release even after interrupted delivery;
  failed/late focus cannot admit a press in a replaced frame.
- The first working key path used click-to-focus. Review identified that this
  could activate a button before Enter activated it again. Replace that path
  before committing this feature, and add actual one-activation/no-click evidence.
  Focus itself can run page handlers: a stolen focus stops key delivery with
  possible-effect uncertainty. A dialog/window checkpoint stops the operation;
  responding does not resume the old key. Recovery never repeats focus or keys.
- Actual public C/JS and Agent WebView2 gate passes root, OOPIF, open-shadow,
  Control+A and button Enter cases. Trusted key/keyup counters match, Enter
  activates exactly once and there are no focus clicks. Native focus hijacking
  sends no key, retained failed operations do not refocus, and a focus-opened
  dialog returns an incomplete checkpoint with zero subsequent keys. Nonfocusable
  backgrounds, masked controls and owned read-only calls are denied; read-only
  denial leaves the focus/key counters at zero. No model/external-site calls.
- Sequential regressions: JS 171 pass; Python 109 total, 107 pass/two Windows
  symlink privilege skips. Actual-source native tests exercise original session/
  context binding, missing/malformed/failed replies, late frame changes, focus
  rechecks and release after cancellation. Native frame gate passes 217
  poll-inclusive checks; full normal C/JS visual and managed gate passes the
  existing pointer/wheel and six drag combinations. No new native diagnostics;
  transfer reservations return to zero. Same source/object tree, deployment/Home.
- Next audit the older ordinary DOM fill/type/key click-to-focus sequence with
  the same single-activation fixture and migrate it as a separate update.
  Directory uploads, full Android integration (the current phone retains its
  explicitly unsupported WebView 138), BFCache/adversarial lifetime, OOM/network/
  performance and final packaging remain open. This is a development milestone.

## V7 — ordinary DOM input without activation clicks

- Reproduce the older ordinary DOM path's actual bug before changing it: one
  public `key Enter` call produced `Activations 2 trusted`. Preserve that private
  receipt separately from later test-harness failures. The dispatcher clicked the
  target before sending Enter; this also added unnecessary clicks to fill/type.
- Route DOM fill/type/key through the same nonactivating native focus primitive.
  Isolated preparation supplies an exact qualified target/local document, which
  is validated before effects and pinned on every key/text request. A distinct
  `frame_target_keyboard` capability refuses old backends without fallback clicks.
  Keep the target's original leaf-to-root chain; DOM input must not inherit an
  unrelated complete screenshot tree. Image keys retain their full snapshot fence.
- Preserve cancellation, takeover, admitted-key release and post-selection focus
  checks. Focus can itself start a download: retain its original input/checkpoint
  window even if the native acknowledgement is lost, without re-focusing or
  sending the remaining key/text sequence.
- Actual normal C/JS/Agent regression passes root and OOPIF Enter with exactly
  one activation; Unicode fill/append/clear; open-shadow and contenteditable
  fields; zero activation clicks; focus hijack, replacement and Control+A focus
  loss with no text reaching another control. A focus-opened dialog does not
  resume text after responding. Unrelated sibling replacement does not invalidate
  the original root field. An original POST is sent once, its exact size/hash
  verifies and its download is released; the retained original DOM accepts an
  explicit new fill. Owned read-only denial leaves focus/input counters at zero.
- Correct two harness assumptions without misreporting them as product defects:
  precheck the generated fixture scripts after a quoting error prevented event
  installation; assert the published `verification:field_state` receipt rather
  than an undocumented fill boolean. Keep these separate from the original real
  double-activation evidence. Include a bounded current observation in failures.
- Sequential checks: JS 175 pass; Python 109 total, 107 pass/two Windows symlink
  privilege skips. Compiled actual-source focus checks confirm DOM input is not
  blocked by unrelated snapshot changes. Native frame gate passes 217
  poll-inclusive checks; normal DOM C/JS/Agent gate passes with no new native
  diagnostics and zero transfer reservations. The image-key recheck stopped
  before input on a first-opening `capture_changed (viewport)` receipt. Preserve
  that failure and address read-only viewport settling as the next separate fix;
  do not count the failed image-key recheck as passed here.
- Same single deployment/Home/source/object tree. Android keeps the user's
  current explicitly unsupported WebView 138. Directory uploads, Android native
  integration, adversarial lifetime/OOM/network/performance and final packaging
  remain active; this is not completion of the long-term goal.

## V8 — bounded read-only capture settling

- Preserve the real first-opening image-key failure from V7: the initial
  screenshot was rejected with `capture_changed (viewport)` before any input.
  Page readiness could complete while the newly revealed native pane was still
  resizing. Do not weaken screenshot/scene validation or count an external
  second screenshot as a successful first request.
- Retry only a viewport-only mismatch, at most three native captures within the
  same read-only operation, original deadline/receipt and first document pin.
  Discard invalidated bytes before any raster/publication/image-lease step.
  Recheck cancellation, takeover and navigation before the next attempt. Frame/
  document identity, mask coverage, scroll, DPR or mixed changes remain failures;
  continuous resizing stops at the bound. No browser input or business effect
  is replayed. Native binaries and focus/input behavior are unchanged.
- Add five deterministic dispatcher gates for one settling resize, continuous
  resizing, protected context changes, cancellation/deadline/takeover/navigation
  and attempted document rebinding. Actual native frame testing forces a resize
  between privacy scans and confirms exactly two native captures under one
  original operation, one stable image reference, the same document and an empty
  native capture slot. The earlier child-document-change image-withholding gate
  also remains passing.
- The normal public C/JS/Agent image-key gate now passes all five root/child/
  shadow/selection/button cases, focus hijack/dialog checks, durable recovery and
  owned read-only denial. Three normal-workbench first public screenshot calls
  succeed with zero initial failures; the existing collector continues to mark
  a failed first public receipt as failure even if a later caller recovery works.
- Sequential final checks: JS 180 pass; Python 109 total, 107 pass/two Windows
  symlink privilege skips. Native frame gate 220 poll-inclusive assertions;
  actual image-key and first-capture gates pass, without model/external-site
  calls or new native diagnostics. Transfer reservations return to zero. Preserve
  the initial failed receipt separately from the passing results. Same single
  deployment/Home/source/object tree; restore headless service mode.
- Directory uploads, full Android integration with explicit current-provider
  limits, adversarial lifetime/OOM/network/performance and final packaging remain
  open. This is an additional bug fix, not completion of the long-term goal.

## W1 — anchored directory-source manifests

- Add the shared read-only `files.tree` C service, generated `mdoFilesTree` and
  JS `files.tree` helper/discovery contract. Return a complete deterministic
  manifest with relative paths, regular-file SHA-256/revisions, empty-directory
  metadata, totals and a composite tree revision. Do not return contents or
  absolute source paths; do not expose browser directory assignment yet.
- Bound files/subdirectories to 128 each, depth to 16, individual files to
  32 MiB and total bytes to 64 MiB; bound UTF-8 components/relative paths too.
  Pin the selected physical root, every scanned directory and regular file;
  enumerate by anchored handles and recheck named entries/handles before
  returning. Reject links/reparse/special entries, unavailable metadata,
  changed identity/metadata and incomplete scans instead of omitting entries.
  An expected tree revision performs a new complete scan/rehash. This is an
  observed consistency check, not an atomic snapshot or retained source lease.
- Actual C/XRT gates cover deterministic Unicode/empty directory manifests,
  content changes with restored size/mtime, add/remove/rename changes, caps and
  exact count/depth boundaries, oversized source files, name-byte bounds,
  unprivileged Windows directory junction rejection and active-writer denial.
  Every failure has no partial manifest; subsequent calls/renames still work.
- Compile the production fragment against deterministic fault seams: all 111
  allocation failure positions and 15 identity/replacement/cancellation/cleanup
  scenarios release every file/directory/value owner with no partial result.
  This caught and fixed a missing specific error on path allocation failure.
  Separate fake filesystem/hash seams from the actual XRT/OS test evidence.
- Sequential final checks: JS 180 pass; Python 120 total, 118 pass/two existing
  Windows symlink privilege skips. The first full run stopped only because the
  new long-name fixture hit Python's legacy Win32 path limit; preserve that log
  separately, use the extended native path for fixture creation/cleanup, and
  rerun the full gate successfully. No native binary change/rebuild was needed.
- Keep the same single deployment/Home/source/object tree and the user's
  existing Android WebView 138. Browser directory upload still needs native
  hierarchy staging, cleanup inventories, exact relative-path assignment and
  chooser/C/JS/Agent end-to-end gates. Android integration, broader lifetime/
  OOM/network/performance and final packaging remain active; this is a tested
  foundation milestone, not completion of the long-term goal.

## W2 — reclaim failed native upload admissions

- During directory-staging work, reproduce a real existing admission leak in
  WebView2. A selected safe basename triggers the current v1 Win32 cache-path
  length failure after the reservation directory/lease is created. The native
  error is returned, but an otherwise idle browser retains one reservation for
  the full two-second observation; closing that browser finally reclaims it.
  Preserve this original failed receipt separately from passing rechecks.
- The job was registered after directory/lease creation and its cleanup timer
  was armed only after all files were created. Rejection occurs while admission
  still holds the private cache guard, so nested cleanup cannot acquire that
  guard. Publish the owned job and arm the collector before the first reservation
  filesystem effect, routing directory/lease failures through the same rejection
  path. Later ticks reclaim partial admissions after the guard is released;
  timer setup failure has no reservation filesystem effect. Preserve unknown
  cache entries and all existing retained-assignment/consumption semantics.
- Add an explicit-phase actual native regression. Wait after failed admission
  without any upload/status/close request, inspect the real private cache/ACL,
  and close the browser only after the independent observation is acknowledged.
  Both loopback HTTP and about:blank cases now have zero reservations before
  close (250/218 ms in the final checks), no new native diagnostics, and zero
  reservations after close. Earlier repeated checks also passed. Keep the first
  uninstrumented no-receipt observation separately; it is not a diagnosed second
  product defect. The stable explicit-phase variants are the acceptance gates.
- Native build succeeds and reuses the single object variant. An interrupted
  prior build produced no new receipt/binary; confirm its missing handle and
  absent build processes before continuing, rather than starting a second live
  build. Normal public C/JS/Agent upload, clear, picker acceptance/cancellation,
  recovery and read-only denial all pass on the rebuilt host, with zero cache
  reservations. Both compiled native upload policy/resource-ownership gates
  pass. No contracts or page-input behavior changed.
- Directory hierarchy staging/assignment still remains open. Implement proper
  extended native cache paths instead of lowering accepted filename limits;
  update the deliberate failure seam when that limitation is fixed. Full Android
  integration, broader lifetime/OOM/network/performance and final packaging
  remain active. Restore the same Home to headless mode; do not mark the overall
  Browser Use goal complete for this bug fix.

## W3 — preserve valid names through long native cache paths

- Reproduce the public C/JS long-name failure before rebuilding: a valid
  240-byte basename is rejected at `native_admission` with `Cannot create a
  regular private upload file`, with no website assignment. Keep that receipt
  and its log apart from the passing post-fix evidence; do not lower the name
  budget to avoid the failure.
- Normalize only the trusted browser-profile cache root into the extended
  Windows file namespace. Keep basename validation and the opaque GUID/index
  layout; staging, complete hashing, native assignment, live cleanup and the
  startup collector now use the same native root. No unchecked wire path is
  prefixed and no private path is returned to the caller. Preserve an existing
  native profile path's semantics; ordinary paths are made full before prefixing.
- Compile the actual normalizer and exercise real Windows create/read/write/
  inspect/delete operations beyond legacy MAX_PATH with ASCII and Unicode
  filenames, plus empty/NUL/device-path and size rejection. The fixture deletes
  only entries it created, without recursive deletion. UNC formatting is tested
  as normalization only; no network share was used or claimed verified.
- Public C-service/JS completion and managed-tool upload gate passes on the
  rebuilt host. The page receives exact 240-byte ASCII and 238-byte Unicode
  names and reads their complete source hashes; retained bytes are explicitly
  released after consumption. Existing clear, duplicate recovery, picker,
  ownership and read-only checks pass, with zero private cache reservations.
- Replace the former legacy-path failure seam with a deterministic real I/O
  denial: after warming/emptying this process's cache, temporarily deny
  subdirectory creation in newly inherited GUID reservations. This fails at
  numeric file-directory creation after registration. Constrain the exact
  project QA cache, require its expected private inherited ACL, and keep the
  original descriptor only in memory. Restore on both normal completion and
  deliberate exception, including after an ACL API error that may be partial;
  independently check the protected original ACL and a subsequent successful
  admission. No access rights are broadened. HTTP and about:blank gates pass
  without an upload/status/close command masking cleanup before observation.
- Strengthen the actual forced-restart gate to use a 240-byte name. Complete
  bytes survive a held lease; unknown/renamed files and a real local junction
  are preserved; orphan collection then completes with zero reservations and
  no new native diagnostics. QA evidence operations use extended paths too.
- Sequential final Python checks: 121 total, 119 pass/two existing Windows
  symlink-privilege skips. Native public upload, forced restart and both I/O
  failure/ACL restoration gates pass. Same single deployment/Home/object
  variant, restored to headless. Directory hierarchy staging/assignment,
  Android integration under the existing provider limit, broader lifetime/
  OOM/network/performance and final packaging remain open; this does not
  complete the long-term Browser Use goal.

## W4 — native directory staging and restart collection

- Add the real Windows directory reservation path, reusing bounded chunks,
  complete SHA-256 sealing, opaque tickets and existing ownership/expiry rules.
  Preserve the selected root basename, relative files and nested empty
  directories. Keep v1 regular-file reservations compatible. Do not advertise
  directory upload or permit assignment until its separate end-to-end gate.
- Validate the typed, bounded manifest with the production WebView JSON parser
  and Windows ordinal case comparison. Reject traversal, reserved/ambiguous
  components, undeclared parents, file/directory collisions and Unicode case
  aliases. Bound manifests to 240 KiB, 128 files/128 subdirectories, 16 directory
  levels, 32 MiB per file and 64 MiB total. Limit retained payload directories
  to 512 per host in addition to existing job/file/byte budgets.
- Flush a v2 cleanup inventory before materializing payloads. Inspect the
  complete layout before deleting known files, including ancestor files; reject
  reparse points and preserve unknown or renamed entries. Reuse the registered
  pre-effect collector so failed partial creation and expiry are reclaimed while
  the browser remains open. Complete sealing checks the layout and exact pinned
  file sizes/hashes; enumeration metadata is not treated as authoritative size
  while a writer is open.
- Compile the actual production parser/validator/cleanup with real Windows I/O:
  46 adversarial validations and all 63 cleanup allocation failure positions
  preserve ownership without partial deletion or enumeration-handle leaks.
  A real WebView2 staging gate passes 46 assertions for nested Unicode/empty
  directories, a 240-byte leaf, chunk/key replay, hash failure cleanup, the
  128-file boundary, global directory quota, expiry and regular v1 compatibility.
  Independent filesystem observation occurs before native status/close, so it
  cannot hide a missing collector. Directory assignment explicitly rejects with
  no dispatch or retained bytes at this milestone.
- Actual forced-restart gates preserve a live v2 lease, unknown/renamed files,
  ancestor files and a real local directory junction; after fixture restoration,
  orphan collection leaves zero reservations. Private ACLs remain protected and
  no new native diagnostics appear. Reuse the same deployment/Home/native object
  variant and restore headless operation.
- Sequential Python regression: 122 total, 120 pass/two existing Windows
  symlink-privilege skips. Reviewed host overlays and shared contracts match;
  the binary and expanded upload helpers match their build receipt. The actual
  public C/JS/Agent regular-file gate also passes, including long names, clear,
  intercepted pickers and read-only denial, with zero private reservations.
- Public directory source streaming, relative-path FileList verification,
  intercepted pickers and C/JS/Agent coverage remain next. Android integration
  with the user's existing WebView, broader lifecycle/OOM/network/performance
  and final packaging remain active; this is not a completed goal or release.

## W5 — native directory selection and trusted completion

- Assign the sealed selected root once through the existing native input slot,
  using the same extended Windows namespace as staging. Verify every complete
  selected-root-relative path, basename and size exactly once; browser directory
  enumeration order is independent of the source manifest. The compiled
  production matcher rejects omitted/extra/duplicate entries, flattened paths,
  wrong roots/case/names/sizes and coerced numeric metadata.
- Acknowledge an operation-keyed isolated observer before native dispatch. Wait
  for a trusted input event and the following task turn before verifying the
  FileList. Keep bytes before dispatch, preserve uncertain effects and existing
  idempotent receipts, and remove observers/listeners/timers after cancellation,
  timeout, normal completion or page departure. A matching old FileList plus
  an injected untrusted event cannot pass this fence; the actual browser gate
  deliberately suppresses the trusted observer event to prove that behavior.
- Preserve the original failed receipts. A hidden controller received no File
  objects/events and timed out after about ten seconds. The revealed ordinary
  path comparison enumerated four files but reported the long leaf as zero bytes
  and raised NotFoundError on page reads. The final revealed extended-path
  variant reads all complete hashes, including a 240-byte leaf and a 240-byte
  selected-root name with the resulting full path beyond MAX_PATH. These are
  actual environment observations, not a claimed diagnosis of Chromium internals.
  Reject new hidden directory assignments before dispatch; public mutations
  already reveal their owned browser panel. Keep historical replay available.
- Real WebView2 directory gate passes 396 assertions: duplicate basenames in
  distinct directories, long root/leaf names, zero-byte files, the 128-file
  boundary, shadow/same-origin frame inputs, trusted events once, exact saved
  replay, hidden-view denial, observer cleanup, delayed reads after expiry,
  cancellation retaining already assigned bytes, page-cleared uncertainty,
  timeout without replay and a resumed change-handler dialog. Empty directories
  remain in native staging; HTML FileList exposes files only.
- Final sequential gates: JS 180 pass; Python 122 total, 120 pass/two existing
  Windows symlink-privilege skips; the full actual native regression passes
  1,361 assertions, including file input/clear, choosers, downloads and late cache
  cleanup. The 46-assertion directory staging regression also passes. No new
  native diagnostics, private ACLs unchanged, zero upload/download reservations.
  Reviewed host/shared contracts and combined source/binary build receipts match.
  Restore the same single deployment to headless mode.
- Directory-only probes now skip unrelated screenshot evidence instead of
  depending on an old capture fixture, and keep their final failed receipt too.
  Public C/JS/Agent directory source streaming, routed-frame/picker acceptance,
  Android integration under the retained WebView limit and final lifecycle/
  OOM/network/performance/package checks remain active. No directory capability
  is advertised to callers yet; this milestone does not complete the goal.

## W6 — long source snapshot paths

- Public directory integration exposed an existing source SDK defect: anchored
  tree enumeration hashes a 240-byte leaf correctly, but the ordinary full-path
  revision read returns a metadata-open failure. Reproduce with both relative
  and absolute paths; short sibling files read normally. Preserve the actual
  failed public transfer log and its no-assignment receipt.
- XRT's Windows path-stat/open adapters pass the UTF-16 path directly to Win32.
  Snapshot digest/range/tree calls now prepare ordinary long paths in the
  extended namespace after absolute normalization and separator conversion.
  Keep short and explicitly prefixed paths unchanged; do not alter the public
  path resolver, retained metadata formats, or other platforms. The original
  task host performs this conversion, independently of the browser's platform.
  Native path preparation never changes regular-file, no-follow, sharing,
  identity, revision, complete-hash or byte-budget checks.
- Actual C/XRT regression passes for a 240-byte source directory plus a 240-byte
  leaf: whole digest, complete anchored tree, absolute and original-workspace
  relative revision reads, and denial after a source change. File snapshot gates
  pass six cases with the existing Windows symlink-privilege skip; all ten tree
  snapshot cases pass. Compile the production adapter to check drive/UNC spelling,
  explicit/device paths and both adapter allocation failures with no leak.
  These seams do not claim real network-share access.
- The existing tree fault gate still passes all 111 allocation positions and
  15 replacement/cancellation/cleanup scenarios. Its path-conversion stub is
  explicit; the adapter and long paths have their separate real/compiled gates.
  Keep the original failed evidence. Public directory integration remains under
  development and is committed separately after its complete end-to-end gates.

## W7 — public C/JS/Agent directory uploads and frame pickers

- Extend the shared upload contract with a mutually exclusive directory
  descriptor (path/resource space/optional root name), using the same C service,
  generated SDK/tool schemas, live permissions, durable operation identities and
  original frozen task context. Status/release/clear cannot take directory
  sources. Update the official Skill; legacy hosts without the independent
  directory capability reject before source path resolution or reads.
- Resolve the default selected-root name in the original task workspace,
  including a relative dot path. Validate a complete bounded source tree and
  native manifest, stream revision-pinned chunks, seal complete hashes, and
  rescan the original full tree with its revision before exact target/frame
  revalidation and one assignment. Reject empty roots without clearing old
  selections; changed/partial/unsafe sources cancel unassigned staging. Preserve
  retained bytes and uncertain effects after a dispatched/lost reply.
- Actual normal-workbench C service, JS client and managed tools pass directory
  paths and complete per-file hashes, duplicate basenames, empty files and a
  real 240-byte source leaf. Verify the JS client's original directory workspace
  with a dot source path, default/overridden root names, saved-operation recovery
  without duplicate assignment, raw C acceptance, directory picker consumption
  and explicit release. Full-access sessions need no extra prompt; owned
  read-only sessions cannot assign directories.
- Public routed-frame regression passes 26 operations across two actual
  cross-origin native-process frames. Directory input and intercepted picker
  assignment preserve all relative paths and byte hashes. Managed frame
  directories work and read-only denial occurs on an owned frame. C/JS/Agent
  receipts, private ACLs and zero reservations are checked. No model or external
  website calls are involved.
- Preserve the first source-path failure, fixed separately in W6. The next
  observation failure was a test-page issue: a single accumulated JSON text leaf
  exceeded the documented observation bound. Render complete bounded JSON tokens
  in separate leaves and preserve empty-list tokens. Do not widen production
  observation budgets. Native File objects and their complete hashes remain the
  success evidence. A pending plugin installation required its reported restart;
  check the active schema after restart instead of treating old code as new.
- Final JS 188 pass; Python 124 total, 122 pass/two existing Windows symlink
  privilege skips. Shared generated contracts and reviewed overlays match.
  Both public upload and public frame gates pass and restore the same headless
  deployment, with zero upload reservations and protected ACLs.
- The broad native gate subsequently FAILED before its first assertion: the
  third command (replacement creation after closing an in-flight create) timed
  out. It had not entered upload tests; no new native diagnostics appeared and
  both transfer caches are empty after restoration. Preserve its full failed
  receipt/log. Investigate this creation path with bounded phase observations;
  do not present this run as green or infer a local root cause from one timeout.
  Android/native lifecycle/OOM/network/performance/package work remains active.

## W8 — measured creation cancellation and late-reply observations

- Preserve W7's original failed broad-native receipt. Add a same-profile
  cancel/recreate gate with eight sequential hidden/visible replacements. The
  first run passes with complete replacement readiness in 257–415 ms and
  explicit native cancellation receipts; the final expanded run passes all
  eight cases in 361–726 ms. Do not infer the first timeout's root cause or a
  permanent fix from these successful samples.
- Exercise a deliberate one-millisecond bridge deadline on an owned local
  create. Observe only that same identity afterwards: the first status is not
  ready, and after 510 ms it is ready with its requested document. No duplicate
  create or navigation occurs. Clearly distinguish this synthetic missed reply
  from the original default-budget failure; this is a diagnostic gate rather
  than a change to production timing or behavior.
- The broad native harness now preserves bounded read-only post-timeout
  observations for the failed creation identity, without replaying it. Any late
  readiness leaves the original gate failed. Preserve the mode-selection marker
  used by chooser/download/startup sub-gates. The four-case startup mode passes
  after this change.
- A subsequent full actual native run passes 1,391 assertions with no new native
  diagnostics, empty protected upload/download caches and successful late empty
  download-directory reclamation. Retain its structured receipt separately from
  the earlier failure and later startup/creation results. The original timeout
  remains unreproduced and unclassified; it is not erased or labelled fixed.
  The same deployment is headless again. Android implementation, lifecycle/
  OOM/network/performance and final deliverable gates remain active.

## E3 — bounded Android native input foundation

- Add the actual Java 8 input sequencer and WebView framework target: frozen
  touch paths, allowlisted content keys and Unicode InputConnection commits,
  without DOM input/global injection/runtime dependencies. Pin document epoch,
  viewport, foreground visibility and native focus; timer admission precedes
  dispatch. Cancel/navigation/takeover/close release owned held input. Preserve
  uncertain effects and permanently refuse new input after unconfirmed cleanup.
- The production sequencer passes 125 deterministic assertions covering
  bounds/Unicode, busy admission, monotonic expiry, late callbacks, reentrant
  invalidation, timer and dispatch faults, held-input cleanup, changed document/
  viewport/focus and completion ownership. This is a JVM contract gate, not a
  mocked claim of Android framework behavior. The 34-assertion isolated-world
  protocol gate and shared unsupported-provider UI test still pass.
- Deploy both reviewed classes through the single Android host overlay. Add a
  native-input mode to the existing tiny QA app, reusing its own network-free
  fixture/package and separate terminal receipt. Compile/align/sign its 74,131 B
  APK with the existing SDK/JDK. It adds no Home, engine, provider or runtime
  dependency. Existing world mode remains separate from input verification.
- ADB reports no phone; the new APK has not been installed or executed. Record
  framework verification as unavailable, with automation/input verified false.
  Keep the user's current WebView and existing mdo installation/data unchanged.
  Higher-level isolated-world focus/frame/geometry integration and the remaining
  Android contracts are still pending; automation is not enabled by this stage.
- Reviewed overlay/generated shared contracts match and the same loopback
  deployment is restored to headless service. The original Windows creation
  timeout remains unclassified. Lifecycle/OOM/network/performance and final
  delivery remain active; this is not a release or completion claim.

## E4 — actual Android view/input lifecycle and retired-callback fences

- Replace the actual Android browser's key-only view map with an exact-instance
  ownership registry. Old navigation/completion/error/HTTP/renderer callbacks
  cannot overwrite or remove a replacement sharing the tab ID. Cancel a TLS
  handler independently of whether its original view is still current. Pin
  asynchronous legacy script acknowledgements to the original instance/epoch;
  changed documents return unconfirmed results instead of stale success.
- Bind the actual input adapter to each registered view's original epoch and
  admission. Navigation intent/start, hide, takeover, stop, close and Activity
  pause invalidate pending input; resume/show/release admit only fresh work.
  Close drains input before the native WebView is detached/destroyed. Failed
  cleanup refuses further input on that adapter; callback failure cannot stop
  cleanup of other owned views. No Android automation capability is enabled.
- The actual registry/sequencer pass 67 new lifecycle assertions, plus the
  existing 125 input assertions. Cover same-ID replacement, value-equal foreign
  views, reentrant replacement, stale callbacks, all cancellation transitions,
  slot limits, throwing completion and owner-thread checks. These use a synthetic
  native target and are not an Android framework pass.
- Apply and compile the actual browser overlay against the pinned Activity and
  its real Java dependencies in the existing owned build directory. Hash the
  script/templates; do not package this compile-only Activity/service into a
  second app. The tiny fixture APK itself uses the same lifecycle registry and
  builds/aligns/signs at 82,323 bytes, Java 8/min API 26/API 36, without added
  runtime dependencies or NDK.
- Shared unsupported-provider UI regression passes; overlays/contracts match.
  ADB still has no phone: new APK installation/framework execution unavailable,
  never counted as passed. Retain the user's current WebView. Restore the same
  headless loopback deployment. Android world/focus/frame/capture/transfer work,
  original Windows creation diagnosis and final lifecycle/OOM/network/performance/
  package verification remain active.

## E5 — Android main-document connection and stale-reply isolation

- Bind actual incoming messages and initialization transfers to their original
  native port/document lease. Main navigation start and renderer loss invalidate
  it before a same-URL reload can finish. Detachment cancels original content
  input; the host admits no new input while disconnected. Dispose permanently
  retires the channel. A late handshake closes only its owned, untransferred
  endpoints and cannot install the old channel into the replacement document.
- Pin asynchronous legacy script replies to the original main-document lease,
  alongside E4's content-view/epoch pin. Old completions cannot reply through
  the current port after reload/reconnect or an identifier/URL reuse. Keep only
  one pending main-page initialization listener, preserving the null-native-
  source check that rejects iframe messages and the one-transfer handshake.
- Actual Java gates pass 22 channel assertions, 69 lifecycle assertions and 125
  input assertions. Two Node tests extract the applied Java's real bootstrap
  and verify repeated installation (128), invalid/foreign messages, listener
  cleanup, port/result forwarding and route replacement. These are protocol
  checks; no Android framework pass is inferred.
- Actual pinned Activity/hooks and the tiny 86,419 B APK compile; align/sign
  succeeds with the same private dev key. No additional app/Home/runtime
  dependency/provider is introduced. ADB still has no phone; new APK execution
  and framework verification remain unavailable. Keep current WebView and
  explicit Android automation degradation.
- Full JS 190/190 pass; Python 124 total/122 pass/two existing Windows symlink
  skips. Reviewed overlays/shared generated C/JS contracts match. The same
  headless deployment is healthy after the checks. Full Android integration,
  original Windows creation diagnosis, final lifecycle/OOM/network/performance
  gates and package delivery remain active; this is not a release.

## W9 — settle cancelled native creations without waiting for the provider

- W8's retained actual receipt shows all eight closed creations still waiting
  for WebView2's later callbacks. Preserve it as
  `.mdo/native-creation-before-cancel-fix.json`. The native close handler only
  marked the view closed; the original request identity remained in callbacks.
- Give creation its own once-only original reply identity. Every terminal
  environment/controller/interception/adoption result consumes that identity
  before posting its reply. Close marks the original view closed and settles
  cancellation before its own acknowledgement, without waiting for the provider.
  Late callbacks cannot reply twice or use a replacement's identity. A creating
  popup's original deferral is completed immediately, including if posting the
  cancellation itself fails. No creation/navigation is retried.
- Compile and execute the actual production helper with reentrant/duplicate/
  late-success/late-error replies, replacement ownership, terminal failure and
  lost acknowledgement fault seams. Rebuild the same pinned native host using
  its single object variant; binary/source hashes match the build receipt.
- The actual same-profile gate passes eight close/recreate cases, all with
  `old_settled_at_close:true`; replacement readiness is 303–473 ms. A creating
  popup's request settles at close and its opener is unblocked. Nine unique
  native cancellation receipts are observed. The deliberate one-millisecond
  lost-reply case remains a separate diagnostic: the same identity becomes ready
  after 513 ms with no create/navigation replay.
- The complete actual native gate passes 1,383 assertions, all prior feature
  flags still present, no new native diagnostics, protected upload/download
  caches empty, and late empty download-directory reclamation in 203 ms.
  Preserve its structured receipt separately as
  `.mdo/native-creation-cancel-full-result.json`. The prior 20-second creation
  timeout remains unreproduced/unclassified and is not claimed fixed by this
  separate confirmed cancellation repair.
- Reviewed overlays/shared contracts match; the same headless service is
  restored and healthy. Android integration, outstanding native lifecycle/
  OOM/network/performance checks and final deliverable remain active.

## W10 — bound retained provider work and wait on observable creation phases

- The previous eight-view limit covered only the active map. A cancelled view
  retained by its provider callback could continue to consume resources after
  removal, allowing more unfinished creations. Add a separate per-native-window
  budget of 16 original creations, admitted before native viewport allocation,
  popup claiming or navigation. Embed a noncopyable reservation in each original
  view and keep a shared budget lifetime rather than borrowing a dead host.
  Cancellation settles its reply without releasing unfinished work. Terminal
  provider callbacks or view destruction drain the reservation once.
- Expose private read-only `creation_capacity` and `state.creation` diagnostics.
  State is now readable while creating, with environment/controller/interception
  phases and monotonic elapsed time. Creation-ready is distinct from document
  readiness. Preserve bounded post-timeout observations of the same identity;
  late readiness never converts an original failure to success or replays it.
- The shared dispatcher waits on creation state under its existing deadline,
  cancellation receipt and original device/context. It checks deadline/context
  again after a delayed state reply. Once input or a document was pinned, a
  replacement fails stale rather than waiting and continuing on another view.
  No model script or native creation/navigation replay is introduced.
- Production C++ helpers pass deterministic delayed/dropped/duplicate provider
  seams, 16-slot exhaustion/recovery, cancellation-retained ownership, shared
  counter lifetime, phase progression, stable completion time and once-only
  replies. This is a resource-lifetime seam, not 16 stalled real WebViews.
  Four dispatcher tests cover readiness, deadline, cancellation, takeover, lost
  context, delayed ready replies and no further input after recreation.
- The actual focused native gate passes all eight close/recreate cases and popup
  cancellation. Cancelled work is still counted immediately after close, and
  final pending count is zero (peak observed in this run: one). The actual shared
  dispatcher consumes three creating-state reads, seven state reads total and
  one page observation, with no creation replay. The deliberate one-millisecond
  missed reply exposes controller/ready phases and recovers the same identity.
- Preserve two first readiness failures: the QA wrapper incorrectly classified
  its normal read-only download-list query as a mutation. Diagnostic evidence
  showed the current dispatcher had waited and observed correctly. Whitelist
  only state/automation and read-only transfer lists before forwarding; do not
  weaken production guards. The corrected actual fixture passes.
- Full native 1,374 assertions pass, all prior feature flags retained, no new
  diagnostics, protected upload/download caches empty. JS 194/194 pass; Python
  126 total/124 pass/two existing Windows symlink skips. Generated contracts and
  reviewed overlays match; native source/binary hashes match their receipt.
  Preserve focused/full structured evidence separately; restore the same healthy
  headless deployment. The original intermittent creation delay remains
  unclassified. Android integration and final lifecycle/OOM/network/performance/
  package delivery remain active; no release has been published.

## F1 — deterministic portable plugin and real ecosystem installation

- Build one `mdo.extension.v2` artifact directly from an explicit 22-file source
  allowlist: C plugin, C/JS SDK, page runtime, Skill, installation requirements
  and MIT license. Normalize UTF-8/LF and stable ordering; reject missing entry
  files, unsafe/duplicate names, links/reparse points and format limits. Produce
  a SHA-256 artifact/file inventory without scanning Home or staging another app.
- Mark this as an enhanced-host Windows development preview. The ordinary 0.7.0
  plugin-format minimum is not proof that stock mdo has the required host/native
  changes. Android remains unadvertised/incomplete and its current provider is
  preserved. No production store/download/updater publication occurs.
- Move the development installer onto the actual local ecosystem plan/import
  path, with explicit C trust, expected-plan digest and ordinary plugin enable
  plan/commit. Repeated installations retain ecosystem ownership instead of
  replacing its recorded revision via `plugin.apply`.
- In the same existing Home, verify the exact known 20-file legacy development
  revision before unregistering it, retain its immutable files and migration
  receipt, then import/enable/restart the portable revision. The real native
  parser accepts it. Stale plans and missing C trust reject without configuration
  changes. All shared C service contracts register after restart, every decoded
  byte survives native export, and reinstall retains the identical revision.
- The first probe failed before any mutation because the QA script expected a
  `plugins` field instead of `plugin.list.items`; fix the probe and retain its
  first log. This was not an importer/product failure. The corrected real gate
  passes nine checks on the first migration, and eight on repeat installation
  (no migration is needed). The current receipt is
  `.mdo/package-install-result.json`; the original revision/inventory is retained
  in `.mdo/package-development-migration.json`. Four packaging integrity tests,
  12 actual installed-C-plugin regressions and shared-contract verification pass.
  The final 241,343-byte artifact SHA-256 is
  `6930d76d8fa02be9b0a9cde060a0064ad40b35832f05358bab4fbba613cdab33`.
  No external catalog, model, second Home or application copy was used.
- The final enhanced program artifact, uninstall/recovery verification, Android
  integration and remaining lifecycle/OOM/network/performance gates remain active.

## F2 — self-contained enhanced Windows program, packed and tested in place

- Package the same existing application/native host directly, without the
  upstream full application staging copy. Check the pinned baseline, reviewed
  overlays, both native binary receipts and mechanically generated SDK surface.
  Reuse the pinned generator and build the small Windows command forwarder in
  the existing app. Unknown files or unrelated tracked changes stop packaging.
- Journal hashes before temporarily moving only 13 known QA/config/build-receipt
  files outside the app and adding the ordinary desktop-edition define to its
  generated unity. Restore every original hash in `finally`; `--restore` handles
  an interrupted build. Validate all recovery destinations before moving any
  file and preserve intervening edits. Two fault/path tests verify partial
  interruption recovery, changed-destination preservation and repeatability.
- Generate `dist/mdo-browser-use-dev-windows-x86_64.exe`, 6,869,925 bytes, SHA-256
  `c722be46f66409f1ef7b9124b9b2c1221a096298968106fe05f20e00bb3623b5`.
  The production native pack provider inventory matches all 746 included files;
  original unity and all 13 exclusions are restored. No extra application/Home
  copy was made. Inventory and runtime qualification are separate receipt fields.
- Start the actual executable from `dist/`, without an unpacked app config or
  generated tree there, on the existing loopback port and exact same Home. All
  six runtime checks pass: archive-root boot, real UI registration, portable C
  plugin/native observation, Unicode fill plus trusted click, synthetic PNG
  capture, and browser close. Restore the original healthy headless deployment.
  Retain `.mdo/program-runtime-result.json` and the artifact's inventory receipt.
- Preserve initial packaging-tool failures: explicit UTF-8 fixes the Windows
  default-codepage read; generated `workbench-services.js` is accepted only after
  comparing it with the reviewed generator without writes; GUI `xsw.exe`
  redirects stdout, so inventory uses the same pack provider in console `xs.exe`.
  The first generated archive was independently verified before reclaiming its
  pending file; inputs had already restored correctly. These were build-tool
  checks, not native browser defects.
- These are development artifacts requiring the paired plugin/enhanced host.
  No production store/website/updater publication occurs. Android, uninstall/
  recovery and outstanding final lifecycle/OOM/network/performance work remain
  active; the full goal is not marked complete.

## F3 — repair same-version reinstall and verify package lifecycle/recovery

- The real local lifecycle gate reproduced a host defect: uninstall deliberately
  retains immutable plugin revisions for live code/UI and rollback, but removes
  their installation receipt. Reimporting the same portable package then failed
  with `Resource already exists` on the retained LICENSE before activation.
  Preserve `.mdo/package-lifecycle-first-failure.json` and its log. Browser data
  and immutable files remained intact; the failed gate cleaned up its own tab.
- Represent the confirmed host repair in `host/ecosystem-current.inc.c` and the
  reviewed overlay manifest. After the existing destination/owner checks, a
  plugin revision without a receipt can reuse only bytes whose full SHA-256
  matches the incoming immutable file. Retain those exact original bytes in the
  rollback journal. The reader is binary-safe and bounded to 64 MiB. Ordinary
  unowned paths still conflict; the existing MCP-specific behavior is retained.
- The actual same-Home gate passes ten checks: native uninstall requests restart
  and retains immutable files, C services disappear after restart, the existing
  browser record survives, same-version reimport works, a second generation
  with 16,640 bytes of embedded NUL/non-UTF-8 binary data also reinstalls, and
  a changed retained binary is rejected without overwriting it. Restore the
  ordinary 22-file portable plugin and clean up the fixture tab.
- A journaled synthetic partial payload/receipt transaction is recovered by the
  actual startup loader before plugin initialization. Original file bytes and
  all original receipt records return, the pending journal is removed, and a
  second startup leaves them unchanged. This is a deliberate documented crash
  seam, not a timeout interpreted as a crash. No extra Home/app copy is used.
- Four packaging integrity tests and 12 actual C-plugin regressions pass. Repack
  the enhanced Windows preview with the fix: 6,870,079 bytes, SHA-256
  `d02380ac414660c3cd7aedac2f81917b871a081eac1509c848f7a32c08689a13`.
  Its six actual packed-program C/JS/native checks pass again, all 13 temporarily
  excluded inputs restore, and the original headless service is healthy.
  Preserve the initial F2 inventory receipt privately before replacing the binary.
- Android native integration and remaining final adversarial/OOM/network/
  performance qualification continue. No production release is published.
- Full post-repair regression: Python 132 total/130 pass/two existing Windows
  symlink-privilege skips in 51.341 s; JavaScript 194/194 pass in 9.173 s, run
  sequentially. Preserve `.mdo/F3-python-full.log` and `.mdo/F3-js-full.log`.
  Afterwards the real service is HTTP 200, the ordinary 22-file plugin is enabled
  with no pending selection/recovery journal, all packaging inputs are restored,
  and the updated executable still matches its runtime-qualified SHA-256. No
  additional `dist/mdo-home` was created. This does not qualify the unfinished
  Android/native OOM/network/performance scope as complete.

## E6 — integrate isolated worlds with the production Android content view

- Add a production document controller and wire it into actual content creation
  before the first URL load. Keep manual-view profiles unchanged. Isolated
  execution uses native main-frame peers and exact view/document epochs; its
  bootstrap announces once and skips subframes. The private native automation
  route and per-view diagnostic state now use this controller. Overall product
  automation/Agent admission remain explicitly disabled while other Android
  contracts are unfinished; no page-world fallback is introduced.
- Integrate navigation, load, channel detach/reattach, pause/resume, takeover,
  stop, renderer loss, close and destroy. Admission changes precede external
  cleanup callbacks, independent blockers do not erase one another, and late
  results cannot reply on a replacement main connection. Retire the previously
  admitted peer specifically so an early new peer survives a later native start
  callback. Closed-view failure metadata is removed and native close includes a
  world-cleanup confirmation.
- The actual production Java passes 34 boundary plus 47 document assertions,
  including callback reentry, early peers, cancellation causes, epochs/owners,
  profiles, cleanup and unsupported providers. Existing 125 input/69 lifecycle/
  22 channel checks pass. Five targeted Node tests pass, including the actual
  Java bootstrap constant and existing channel/preflight contracts. These are
  protocol/lifetime checks; no Android framework pass is claimed.
- Compile the real Activity and hooks with the new helper. Preserve the first
  missing-helper compile log; fix the actual Android overlay's copy list and
  include the helper in source hashing. Build/align/sign the same owned tiny
  dev package (94,611 bytes), SHA-256
  `61e4cca296a5e2dbb2394ae2bab0d698bd63e0ae1b22b1b0f3ca862c462881e6`.
  It now exercises the production document controller when a capable provider
  is available. Reuse the existing output/SDK/JDK; no NDK, runtime dependency,
  extra Home or application installation is introduced.
- ADB still has no device. The world/document and native-input device receipts
  are `unavailable`, with every verification flag false. Preserve the historical
  provider receipt separately; do not update/reselect the system WebView or touch
  the existing mdo package/data. The same headless service remains healthy.
- Full JavaScript regression passes 196/196; generated shared contracts and all
  deployed overlays match. The current APK's source/binary hashes and actual
  Activity compile receipt match. The runtime-qualified Windows artifact is
  unchanged by these Android-only enhancements.
- Native focus/geometry, frame/capture/privacy, transfer integration and retained
  provider-callback/resource qualification remain active, along with supported-
  provider framework verification and final delivery. The goal remains active.

## E7 — retain native world reservations until actual completion or teardown

- Reproduce a production-Java admission defect with a deferred provider: client
  deadlines reduced the pending count to zero and admitted 17 native evaluations
  through the 16-job limit. Retain the original failure receipt/log. Provider
  acknowledgement loss is also explicitly uncertain rather than proof that
  native execution did not start.
- Separate client jobs from unfinished native callbacks. Keep a per-view limit
  of 16 and share a 32-job budget across content views, including cancelled and
  timed-out calls. Release each original reservation exactly once, on a terminal
  callback or after the exact closed WebView is physically destroyed. Listener
  removal or a foreign/replacement view cannot release it. Late callbacks do not
  replay client replies or decrement replacement work. Drop the completed
  session/UI callback and retain only a weak world reference with the small
  budget in the provider's original handler.
- Add 18 native-budget assertions covering timeouts, cancellations, shared
  saturation, exact/foreign teardown, replacement work, late callbacks and a
  scheduler-allocation OOM before dispatch. Production Java target 8 passes,
  alongside 35 boundary/47 document assertions and the existing 125 input,
  69 lifecycle and 22 channel assertions. All 196 JavaScript tests pass.
- Compile the actual Activity/hooks and rebuild the same owned tiny QA package:
  98,707 bytes, SHA-256
  `5412d32b1fef1740a1645eee38706ee91248801bf7803d46e05cc30c3a0b650e`.
  No additional app/Home/toolchain or runtime dependency is introduced. ADB has
  no device, so both framework receipts remain unavailable, not passed. Keep
  the current WebView and existing mdo installation/data unchanged.
- Reviewed overlays match and the original headless loopback service is healthy
  (HTTP 200). Windows artifacts are unaffected. Continue Android native focus/
  geometry, frame/capture/privacy, transfers and framework qualification, plus
  final adversarial/network/performance work; product admission remains disabled
  and the full goal is active.

## E8 — connect asynchronous document guards to Android native input

- Add a per-action asynchronous check before every native press/release or text
  commit, with a separate bounded watchdog. Delayed/duplicate replies cannot
  act on a cancelled, expired or replacement job. Reject changed view/document/
  focus after the check, and release original held touch/keys on rejection or
  timeout. Timer admission also rolls back queued tasks/state on OOM. Native
  view focus is reserved in the same input slot until isolated DOM focus is
  acknowledged; it is not a click used to activate the control.
- Wire the actual private native `input` command to a main-frame adapter. It
  requires the original isolated document/reference and matching keyboard
  target, verifies root-frame ownership, live hit geometry and DOM focus, and
  converts admitted CSS points to native view pixels. Use exact-view native
  scale callbacks rather than guessing unknown initial scale or trusting the
  deprecated scale getter. A scale change cancels original input; stale view
  callbacks cannot update a replacement. Cross-check viewport/scroll/zoom and
  padding before dispatch. Unsupported frames, visual snapshots, offset pinch
  viewports, mouse variants and unknown inputs reject explicitly. No synthetic
  DOM click/value assignment or page-world fallback is added to this bridge.
- Preserve the original main-document reply lease, cancellation and effect/
  cleanup receipts. Native state now reports the actual navigation epoch, input
  busy/cleanup state and whether scale is known, without throwing on a poisoned
  input binding. The main-document adapter is private integration work:
  shared Android frame-driver/Agent integration, capture/privacy, transfers and
  real framework qualification remain pending. The overall Android Browser Use
  admission flag stays false. Add a separate always-false guarded-input device
  qualification field so the older low-level fixture cannot certify this path.
- Actual Java target 8 passes 63 new deferred-guard assertions, 81 lifecycle
  assertions (including native scale ownership), and the existing 125 input and
  22 channel assertions. Four Node tests execute the actual Java fence constant
  for document/target/focus, viewport/scroll/zoom and focus-handler side effects.
  Full JavaScript regression passes 200/200; both real deployment/SDK smoke
  tests pass. Preserve the initial fixture compile and scroll-tolerance test
  logs; the corrected checks pass.
- The actual pinned Activity/hooks and native input bridge compile. The same
  tiny QA APK builds/aligns/signs to 110,995 bytes, SHA-256
  `68c100a30ca85ffb16c3e071854ba7cbb62c9fae4b25c74baa32abb85f58afc2`.
  No additional SDK/Home/app copy or runtime dependency is introduced. ADB has
  no device; both new device receipts are unavailable with all verification
  flags false. No package is installed and the current WebView is preserved.
- Reviewed host overlays match, the single headless service is HTTP 200 and
  deployment use is about 1,109 MiB. The previously qualified Windows artifacts
  are unaffected; no production release is published. The full goal is active.

## E9 — pin native document identities and fix resume/start ordering

- Reproduce a production document-controller race: an early replacement peer
  arrives while backgrounded, resume adopts it, then its delayed native start
  retires that same new peer. The once-per-document bootstrap cannot announce
  again, leaving the document permanently unready. Retain the failing gate log
  `.mdo/E9-document-resume-before.log`. Blocker release now retains the candidate
  until the original navigation transition, rather than adopting it over the
  older active peer. The exact failure fixture passes after the fix.
- Give each newly admitted native document an independent `fd-` identity.
  Duplicate load events and ordinary blocker release keep that identity;
  navigation/replacement rotate it, and blocked/closed/unowned documents expose
  none. Pin private automation requests to both that native identity and the
  native navigation epoch, return both with the original lease's reply, and
  reject mismatches before evaluation. The legacy trusted executor remains
  available internally; this does not enable Agent admission.
- Production Java target 8 passes 59 document assertions, 35 boundary assertions
  and 18 retained-budget assertions. Nine Android protocol/JS checks pass.
  The actual Activity/bridge compile; the same QA APK is 115,091 bytes, SHA-256
  `d83a3fa49612cdcb37a7f5cafb5fa513dc15b452fca98a7e53c003b527c70836`.
  Reviewed overlays match. ADB still has no device, so the current provider is
  preserved and the new world/framework gate is unavailable, not passed.
- Main-document shared-driver integration, Android capture/privacy/transfers,
  supported-provider framework qualification and final product gates continue.
  The goal remains active and no public release is published.

## E10 — connect the main-document driver to the shared C/JS execution path

- Add the Android document driver to the real workbench's native descriptor
  selection. It calls only fixed isolated runtime methods, pins native identity
  and epoch on every call and reply, qualifies original element references and
  forwards the same root frame chain/local document/reference to native input.
  Observations and results explicitly declare `main_document` scope and missing
  child-frame support. No fictitious complete frame tree is supplied.
- Cover observation, exact/semantic location, preparation, verification, option
  listing and selection through the existing dispatcher. Bound native reads and
  external checkpoints by the original deadline; late results do not issue more
  work. Preserve cancellation boundaries, native refusal codes and mutation
  uncertainty without replay. Structured pinned native failures distinguish
  refused admission from possibly executed work and withhold page exception text.
- Bind Android native input to the one exact root native document chain and the
  same local-frame runtime namespace. A replaced document reports `frame_changed`;
  unsupported frame/visual or input types reject separately. The shared input
  request now carries the prepared local identity for both backends. Android
  hover/scroll are explicitly unfinished. Global Android Agent admission and
  first-navigation interception requirements remain unchanged and disabled;
  capture/privacy, transfers, child frames and supported-provider qualification
  remain pending.
- Thirteen new JS tests cover the production document driver and its coupling
  to the actual shared dispatcher: qualified reads/waits, click/fill/key metadata,
  stale/changed/bad replies, blockers, result bounds, timeout, late mutation
  replies, original checkpoint ownership and unsupported capability admission.
  Synthetic native/interception acknowledgements prove protocol wiring only,
  not actual Android input or transfer behavior. Full JS regression passes
  213/213; Java passes 59 document, 35 boundary, 18 budget, 125 input, 81 lifecycle,
  22 channel and 63 deferred-guard assertions. Shared C/JS contracts match.
- The actual Windows C -> JS -> WebView2 DOM keyboard gate passes against these
  changes: root/child clicks activate once, Unicode fill/type/clear, shadow and
  contenteditable input, focus hijack/replacement/selection loss, dialog
  boundaries, original POST download bytes and read-only admission. No model or
  external site is used; no new native diagnostics or transfer reservations
  remain. Restore the same healthy headless service. Receipt:
  `.mdo/dom-keyboard-result.json` and `.mdo/E10-windows-dom-keyboard.log`.
- The actual Android Activity/bridge compile. The same tiny QA APK is 115,091
  bytes, SHA-256
  `b06ba67c02d33f2cd2070662be1345428b2445073c685d83133bc1290323fcd9`.
  ADB has no device; both latest device receipts remain unavailable, all relevant
  verification flags false. Keep the current WebView and existing mdo/data.
- Reviewed overlays match. Refresh the packed Windows preview separately before
  claiming its archive contains the new shared modules. The goal remains active.

## F4 — refresh the packed preview after the shared driver integration

- Rebuild the Windows preview from the same application/native host after E10,
  using the existing in-place packaging journal and no app/Home staging copy.
  The native inventory verifies 747 files, including the actual shared document
  driver. Restore the original unity and all 13 excluded development files.
- The refreshed executable is 6,873,520 bytes, SHA-256
  `aefae21cfa554bf2ae3db957be5c1a9b2ee1b30c96c9eaa97192075456e431dc`.
  The receipt independently records inventory and actual runtime verification.
  Boot that artifact from `dist/` on the same Home/port, without an unpacked app
  configuration or generated tree, and pass all six checks: archive boot, real
  UI registration, C/JS/native observation, Unicode fill/trusted click, synthetic
  capture and browser close. Receipts/logs: `.mdo/program-runtime-result.json`,
  `.mdo/F4-program-package.log`, `.mdo/F4-program-runtime.log` and the adjacent
  artifact receipt. Hash and included-module checks match after the run.
- Restore the ordinary healthy headless service (HTTP 200), verify reviewed
  overlays and generated C/JS contracts, and confirm no extra `dist/mdo-home`
  was created. Deployment use is about 1,109 MiB. The portable plugin is unchanged.
  Android remains unqualified/disabled, the current WebView is preserved, final
  product gates remain pending and the full goal stays active. No store, website
  or updater publication occurs.

## E11 — pin native Android wheel input without touch/click fallback

- Add one native mouse `ACTION_SCROLL` event through the existing input
  sequencer, with bounded deltas, no pressed buttons and no focus click. Convert
  public right/down CSS pixels using the admitted native scale and Chromium's
  Activity-theme wheel factor for both axes. Record reviewed primary-source
  mappings/hashes in `host/android/scroll-protocol.json`; actual installed
  provider behavior remains a separate qualification gate.
- Add a fixed isolated `scrollPoint` operation and original hit-element pin to
  the shared document driver. Recheck document/frame, hit, viewport, scroll and
  scale before native dispatch; reject child frames or changed targets. Open
  shadow roots and retained-reference bounds are covered. Runtime 18 invalidates
  older runtime observations. Native wheel state does not enable overall
  Android Agent admission, interception or unsupported provider operation.
- Pass 66 new Java wheel assertions with the existing 125 input, 81 lifecycle,
  22 channel and 63 deferred-guard assertions. Full JS regression passes 220/220.
  Logs: `.mdo/E11-wheel-contract.log`, `.mdo/E11-wheel-js.log` and
  `.mdo/E11-js-full.log`. The real Activity/bridge and wheel path compile; the
  single QA APK is 123,283 bytes, SHA-256
  `1de0ef332c0ecb0907470fae09a9806f707f755e3fd11f471270e226baffc31c`.
- Extend that device fixture with a nested two-axis scroller to verify actual
  trusted events, coordinates/deltas, target scrolling and absence of extra
  clicks. Neither device gate can run because ADB has no device; all framework
  verification fields remain false. Preserve the current WebView and mdo/data.
  Do not treat compilation or synthetic protocol assertions as framework passes.
- Pass the actual Windows C -> JS -> WebView2 DOM keyboard gate with runtime 18,
  including root/child/shadow/contenteditable input, focus/permission boundaries
  and original POST download bytes. No models or external sites are called, no
  native diagnostics or transfer reservations remain, and the same headless
  service is HTTP 200. C/JS contracts and reviewed overlays match; whitespace
  checks pass. Logs: `.mdo/E11-wheel-build.log`, `.mdo/E11-device-world.log`,
  `.mdo/E11-device-input.log` and `.mdo/E11-windows-dom-keyboard.log`.
- Deployment use is about 1,112 MiB. No extra application/Home/native build is
  created. The portable C plugin is unchanged and the previously qualified
  packed preview predates E11. Final product gates and Android work continue;
  the goal stays active and nothing is published to production.

## E12 — preserve native input refusal causes and action-wide uncertainty

- Reproduce a shared-dispatch bug using the actual Android native reply schema:
  even `accepted:false`, zero effects/steps and confirmed cleanup became generic
  `action_uncertain`, discarding `input_target_changed`. Partial failures also
  lost their native cause and cleanup/step evidence. Four new targeted checks
  fail before the fix; preserve `.mdo/E12-native-receipt-before.log`.
- Accept only internally consistent typed failure receipts with bounded cause
  codes and boolean/integer proof fields. Preserve their cause plus a nested
  `error.native_input` failed-step receipt. Clear uncertainty only for a proven
  zero-effect/zero-step, cleaned-up step, retaining earlier focus/selection
  effects. Unconfirmed cleanup remains conservative. Malformed or contradictory
  receipts, missing trusted-input proof and lost transport remain uncertain.
  No page-supplied exception text or automatic retry is introduced.
- Five new coupled production-driver/dispatcher checks cover click/wheel
  refusal, independently requested recovery, later key/text refusal after
  earlier effects, partial delivery, cleanup failure, malformed/contradictory
  replies and lost acknowledgement. Focus/input acceptance now requires actual
  booleans. Targeted shared/visual checks pass 54/54 and complete JS regression
  passes 225/225. Logs: `.mdo/E12-native-receipt-after.log` and
  `.mdo/E12-js-full.log`.
- Pass the actual Windows C/JS/WebView2 DOM keyboard gate: root/child input,
  shadow/contenteditable, focus/selection replacement, permission boundaries
  and original POST download bytes. No model/external-site calls, diagnostics
  or leaked transfer reservations. The initial invocation did not enter the
  gate because its prerequisite headless service had been stopped while
  applying overlays; preserve that startup failure separately. Starting from
  the restored healthy service passes in `.mdo/E12-windows-dom-keyboard-after.log`
  and `.mdo/dom-keyboard-result.json`.
- Shared C/JS contracts, reviewed overlays and whitespace checks pass. Restore
  the same headless service (HTTP 200), about 1,112 MiB in the one deployment.
  No Android/native rebuild is required for this shared JS fix. Android actual
  framework qualification remains pending/disabled with the current WebView
  preserved. The packed Windows preview still predates E11/E12; no production
  publication occurs and the full goal remains active.

## E13 — refuse child-frame hits inside a prepared Android parent target

- Reproduce a main-document click boundary defect: normal point preparation
  accepts a descendant of the target, including an iframe inside a parent
  container. Android's native click fence checked the prepared parent and point
  but did not detect that the actual input would enter an unsupported child.
  The actual runtime/fence fixture fails before the fix; retain
  `.mdo/E13-parent-frame-before.log`.
- Check that exact click point with the fixed isolated `visualPoint` helper
  after preparation. Reject a frame owner as `android_frames_unavailable` and
  a missing hit as `input_target_changed` before sending native touch. Retain
  ordinary descendant clicks and the original document/geometry checks. The
  actual runtime fixture covers a normal descendant, iframe/frame, and an iframe
  inside an open shadow root. Global Android admission remains disabled.
- All seven fence checks and complete JS regression 227/227 pass. The actual
  Activity/native input bridge compiles in the reused SDK/output tree. The same
  QA APK is 123,283 bytes, SHA-256
  `1f03c2948f09cec06104dc423576fb01bda0db31b367e1b19748081329a018fa`.
  Logs: `.mdo/E13-parent-frame-after.log`, `.mdo/E13-js-full.log` and
  `.mdo/E13-android-build.log`. Android framework verification is still pending:
  both refreshed device gates are unavailable because ADB has no device. Their
  verification fields remain false; no package/provider is changed.
- Shared contracts, reviewed overlays and whitespace checks pass; the same
  headless service is restored to HTTP 200. No Windows execution code changed
  after E12's actual DOM keyboard pass, so that gate is not repeated. No extra
  Home/app/native build or production publication is created. Refresh the packed
  Windows preview separately for E11/E12. Final product/Android work continues
  and the full goal remains active.

## F5 — refresh and verify the packed preview after E11/E12

- Repack from the single existing application/native host, with the existing
  in-place exclusion journal and no staging Home/app tree. The inventory checks
  747 files; actual runtime 18, shared document driver and E12 dispatch hashes
  match reviewed deployed inputs. Restore all excluded development files and
  original unity content after packing.
- The executable is 6,874,349 bytes, SHA-256
  `a1864fd98e14e711281532942b49745a68b02699769244bc743e59fbbe10df71`.
  Start that exact `dist/` artifact on the same Home/port, without unpacked
  application input. All six actual runtime checks pass: self-contained boot,
  UI registration, C/JS/native observation, native Unicode fill/trusted click,
  synthetic capture and close. Native inventory/runtime verification are both
  true in the adjacent receipt; recomputing the artifact hash matches.
  Logs: `.mdo/F5-program-package.log`, `.mdo/F5-program-runtime.log` and
  `.mdo/program-runtime-result.json`.
- Restore the ordinary healthy headless service (HTTP 200), about 1,110 MiB.
  Shared contracts and reviewed overlays match. No extra `dist/mdo-home` is
  created. The portable plugin is unchanged; Android is still unqualified and
  disabled, using the user's preserved provider. Remaining product/platform
  gates continue, the goal stays active and no production publication occurs.

## E14 — prepare Android screenshot privacy and pin native image replies

- Add visible viewport/element capture preparation to the actual main-document
  driver. Validate bounded region, density, node usage and mask data, preserve
  every child frame's opaque mask and require unique original owner references.
  Refuse unknown scale, unsettled input/cleanup, offscreen/full-page modes and
  mismatched original native/isolated documents. The root-only capture stamp
  changes with viewport, density, scroll and opaque owner identity.
- Carry an internal original native document/epoch, isolated document and
  viewport/scroll context to the shared native capture request. Refuse wrong
  request/document replies before bitmap decoding, apply before/after masks on
  the trusted host canvas and discard changed/cancelled/late images without
  replay. Do not expose the private context or opaque owner aliases in public
  images. Do not issue misleading image-action references before Android image
  operations exist. Windows frame routing/visual references are preserved.
- Nine new production-driver/shared-executor tests pass, with malformed privacy
  proofs, resource limits, unsupported modes, changed identities, opaque frame
  changes, exact native context and cancelled/late images. Targeted screenshot
  and document checks pass 38/38; complete JS regression passes 236/236. Logs:
  `.mdo/E14-document-capture.log` and `.mdo/E14-js-full.log`.
- The actual Windows C/JS/WebView2 public capture gate passes all three first
  screenshots with zero initial failures and correct visual-reference bindings;
  no model or external site is used. Receipt/log:
  `.mdo/capture-readiness-result.json` and `.mdo/E14-windows-capture.log`.
  Restore the same HTTP-200 headless service, about 1,110 MiB. Shared contracts,
  reviewed overlays and whitespace checks match.
- This is the screenshot preparation/protocol stage, not native Android capture.
  The actual draw/encode/cancel adapter, full privacy/framework qualification,
  image actions and other Android features remain pending; global admission
  stays disabled. No existing manual screenshot fallback, SDK/native rebuild,
  new Home/app copy or production publication occurs. The F5 packed preview
  predates E14; the full goal remains active.

## E15 — bound native capture transactions and retain unfinished resources

- Add the production Java capture transaction and bounded encoder output stream.
  Pin native document/navigation, viewport/scroll/scale; validate cropped raster
  dimensions/bytes and original deadlines. An isolated guard and native visual
  readiness precede drawing; an injected worker then owns the raster. This core
  contains no Android bitmap codec or page-world fallback.
- Keep a per-controller native reservation and shared hard budget through
  cancelled/timed-out native callbacks and active encoding. Exact-view destroy
  retires readiness callbacks, but does not recycle a worker's bitmap. Cancelled
  queued work becomes inert; ambiguous submission cannot double-use/release it.
  Duplicate/late callbacks, reentrant cancellation, post-callback throws and lost
  UI replies cannot replay work or publish stale pixels. A failed raster release
  poisons that controller and conservatively retains its shared slot until the
  runtime resets. Native callbacks use weak controller references.
- Pass 182 Java-target-8 contract assertions, including real background-thread
  ownership, OOM, output bounds, worker/ready/timer failure, changed original
  snapshots, close/cancel/deadline, shared quota and stale callback isolation.
  An adversarial foreign-thread native callback reproduced a state transition
  before thread validation and a retained lease which would not drain after
  view destruction. Move thread validation before the transition; preserve
  `.mdo/E15-callback-thread-before.log`. Final gate:
  `.mdo/E15-capture-contract-final.log` and `.mdo/android-capture-result.json`.
  The initial test run also exposed a test fixture reusing its first callback
  for a deliberately new request; the corrected fixture passes.
- Reuse one small `.mdo/qa/android-capture` class output and the existing WSL JDK;
  no APK, provider, app/Home copy, native binary, signing key or runtime dependency
  is changed. The previously passed 236 JS checks and actual Windows capture
  gate are unchanged by this Java component, so they are not repeated.
- Actual Android visual-ready/draw/encode targets, native channel/lifecycle
  wiring and pixel/privacy/framework qualification remain pending. This core
  is not deployed or advertised as working Android screenshots. Admission stays
  disabled and the user's current WebView is preserved. Continue the full goal;
  no production publication occurs.

## E16 — integrate actual Android drawing, encoding and capture lifecycle

- Wire native `capture`/keyed `capture_cancel` into the actual browser, with
  one controller per exact content view. Validate native/isolated document,
  epoch, CSS viewport/scroll/density and clip geometry. Route only the original
  lease's response. Cancel on navigation, bounds/scale, background, channel
  replacement, takeover or stop; exact view destruction retires visual callbacks
  while a worker-owned bitmap remains reserved until its terminal cleanup.
  Expose truthful capture busy/cleanup/budget state and prevent new input from
  bypassing retained or unclean screenshot resources.
- Draw the visible attached owned view after its actual visual-state callback
  has returned. Use one bounded bitmap, a single process encoder with a one-task
  queue and the shared two-slot resource budget. Exact cancelled queue items
  are removed; callbacks retain no content view. PNG compression runs on the
  worker, and sources stay lossless/bounded until trusted host-side privacy masks
  are applied. Public PNG/JPEG and output byte limits apply after masking.
- Add runtime 19's fixed native viewport checks and opaque media masks. Initial
  fitted scale is accepted only with matching complete viewport geometry;
  offset/cropped pinch still refuses. Child frames and video/object/embed remain
  opaque; no page-world fallback or image-action reference is introduced.
  Structured native failures retain their bounded cause without publishing pixels.
- Reproduce expired timer admission dispatching a new guard with the full old
  timeout. Preserve `.mdo/E16-deadline-before.log`, use an absolute uptime
  watchdog and pass only remaining time. Actual Java-target-8 checks pass 194,
  complete JS checks pass 243. Logs: `.mdo/E16-capture-contract-final.log` and
  `.mdo/E16-js-full-final.log`. Preserve the earlier Node VM getter fixture
  failure separately; a throwing object-property fixture verifies the intended
  exception boundary and passes.
- Pass the actual Windows WebView2 full gate (1,369 checks): PNG/JPEG redaction,
  crop/full-page/cancel, input, frames, dialogs/popups and transfers. No new native
  diagnostics, no model/external-site calls and zero upload/download reservations
  remain. Receipt/log: `.mdo/page-observation-result.json` and
  `.mdo/E16-windows-native.log`. Subsequent Java deadline and JS retained-capture
  admission changes also pass their targeted/full tests; the packed program gate
  will exercise the latest shared host before delivery.
- Compile the actual Android Activity/hooks and capture bridge/target in the
  reused SDK/output. The tiny QA APK is 160,147 bytes, SHA-256
  `2674a22ed6b02a87fae6cc7d2f7604e6e51b69f219076352a24454499a9b936c`.
  Add the no-network native crop/PNG/JPEG/cancel pixel gate. It is distinct from
  isolated-guard or full privacy qualification; those flags remain false even
  on a pixel-only pass. ADB has no device, so the current capture gate is
  unavailable with all verification flags false. No installation/provider
  update occurs. Logs: `.mdo/E16-android-build-final.log` and
  `.mdo/E16-device-capture-final.log`.
- Shared contracts, reviewed overlays and whitespace checks pass. Restore the
  same healthy headless service, without an extra Home/app/native build or new
  runtime dependency. The current WebView and production clients are preserved.
  Actual Android pixels/privacy/framework qualification, remaining platform
  features and final product gates continue. Admission stays disabled, the goal
  remains active and nothing is published to production.

## F6 — qualify the packed preview with the current capture protocol

- Repack in place from the same source/native host, with no app/Home staging
  copy. The inventory verifies 747 files; deployed and packed hashes match for
  runtime 19, the document driver, image preparation and shared dispatcher.
  Restore the original unity and all known excluded development inputs.
- The executable is 6,875,876 bytes, SHA-256
  `136bfa62c3e68e8fa1ba00795b4573fa76245fbf3ade283afd00c10bd53fe36f`.
  Recomputed hash, inventory and actual runtime verification all match. Boot
  this `dist/` artifact on the same Home/port without unpacked app input and pass
  all six checks: archive boot, UI registration, C/JS/native observation,
  Unicode fill/trusted click, synthetic capture and close. Logs/receipt:
  `.mdo/F6-program-package.log`, `.mdo/F6-program-runtime.log` and
  `.mdo/program-runtime-result.json` plus the adjacent artifact inventory.
- Restore the healthy HTTP-200 headless service, about 1,110 MiB in the single
  deployment. Shared contracts and reviewed overlays match; no extra
  `dist/mdo-home` exists. The portable plugin is unchanged. Android actual
  pixels/privacy/framework qualification remains unavailable, and its global
  admission remains disabled with the current provider preserved. Remaining
  platform/product gates continue; no production publication and no goal
  completion is claimed.

## E17 — mask private pixels before lossy screenshot encoding

- Reproduce a shared screenshot privacy defect with actual Chromium codecs and
  the production shared executor. Changing only pixels inside three masked
  regions changes 456 public JPEG pixels when JPEG compression precedes masking;
  PNG remains identical. Preserve `.mdo/E17-privacy-before.log`,
  `.mdo/E17-privacy-before.json` and the two failing source-contract tests in
  `.mdo/E17-source-contract-before.log`. This is synthetic private data only.
- Always request a lossless PNG native source with its own 256 KiB budget, then
  mask before applying the original public format and output byte limit. Refuse
  an explicit non-PNG native reply before decoding/publication. Reuse Windows'
  existing bounded source-scale reduction; no native binary or Android Java
  change is needed. Android already supplies lossless sources.
- Pass 245 complete sequential JS checks, including the new source-codec and
  refusal cases. The actual codec regression now has zero changed public pixels
  for both PNG and JPEG. Logs/results: `.mdo/E17-js-full.log`,
  `.mdo/E17-privacy-after.log` and `.mdo/capture-privacy-result.json`.
- Independently pass the actual public C service → ordinary JS workbench → native
  WebView2 gate. The two private-pixel variants produce byte-identical 457×384
  outputs with three redactions: PNG 5,052 bytes and JPEG 2,256 bytes, within the
  caller's 8 KiB limit. Receipt/log: `.mdo/capture-privacy-public-result.json`
  and `.mdo/E17-privacy-public.log`. No images, model calls or external sites are
  retained/used. These regressions prove this compression boundary, not every
  possible privacy layout.
- Shared contracts and applied overlays match. Reuse the same Home/deployment
  and restore the healthy headless service. The Windows executable must be
  refreshed after this fix; Android actual pixels/privacy/framework qualification
  and the remaining full product gates continue. Preserve the current Android
  provider, keep admission disabled and the full goal active. No production
  publication occurs.

## F7 — deliver the packed screenshot privacy fix

- Repack the Windows preview in place with E17's lossless native capture source.
  The executable is 6,876,009 bytes, SHA-256
  `e63290e76ad033c01fa610e9f25149568b182a71fd3358d261293faaed29db16`.
  The 747-file inventory and runtime verification both pass; packed shared
  dispatcher/runtime hashes match the deployed reviewed files.
- Boot the actual executable from `dist/` with the same Home and loopback port.
  All six archive/UI/C/JS/native observation, Unicode fill/trusted click,
  synthetic capture and close checks pass. Logs/receipt:
  `.mdo/F7-program-package.log`, `.mdo/F7-program-runtime.log` and
  `.mdo/program-runtime-result.json`, plus the adjacent artifact inventory.
- Restore the healthy headless service. Shared contracts and overlays match;
  the single deployment remains about 1,110 MiB, with no extra `dist/mdo-home`.
  The portable plugin and Android QA APK are unchanged. Remaining Android
  qualification and full product gates continue; no production publication or
  completion is claimed.

## E18 — exercise fractional and downscaled public privacy masks

- Extend the same loopback fixture with fractional CSS positions/sizes. Compare
  private-black/private-white variants through the actual public C/JS/native
  screenshot path for PNG and JPEG, with ordinary and small output bounds.
  The six pairs are byte-identical, retain all three masks and respect both
  dimensions and the final 8 KiB limit, including 87×73 downscaled output.
- Receipt/log: `.mdo/capture-privacy-public-result.json` and
  `.mdo/E18-privacy-scaled-public.log`. Reuse the same window, fixture, Home and
  native host; restore the healthy headless service. No product change,
  model/external-site call, provider change or new artifact is needed. These
  specific layouts pass; other privacy layouts and the full goal remain under
  development, with Android qualification unavailable.

## E19 — bound retained native Android JavaScript dialogs

- Add the production UI-thread dialog controller for exact view/navigation
  ownership, opaque modal IDs, one pending native result and absolute 120-second
  expiry. Metadata is bounded without splitting Unicode; prompt defaults/replies
  remain private and enforce an 8 KiB UTF-8 bound. Do not infer a root frame from
  the callback URL.
- Retain only the original result. Reject stale IDs and duplicate callbacks;
  cancel on navigation/ownership loss/close. Late/removed timers use weak exact-ID
  callbacks. Native acknowledgement loss preserves effect uncertainty and blocks
  new admission until exact view teardown, without replaying confirm/cancel.
  Reentrant cleanup, worker-thread and foreign-view boundaries are covered.
- The actual Java 8 source passes 83 contract checks in the existing WSL JDK,
  reusing one small QA class output. Receipt/log:
  `.mdo/android-dialog-result.json` and `.mdo/E19-dialog-contract.log`.
  This proves the controller contract, not framework behavior. Native host/UI
  integration, APK compilation and actual-device qualification are next; global
  Android admission stays disabled and the current provider is preserved.
  No app/Home copy, runtime dependency or production publication is introduced.

## E20 — wire native Android dialogs into the actual host

- Install the native WebChromeClient target on each exact content view, with
  alert/confirm/prompt/before-unload retained results and the existing trusted UI
  list/respond protocol. Keep private prompt defaults out of metadata and check
  response types. Advertise owned-WebView scope instead of inventing frame IDs.
- Block isolated execution and cancel capture/held input at modal admission.
  Navigation, hiding, stop, channel detach, background and close cancel only the
  original pending result. Preserve the modal during human takeover, with Agent
  responses denied. Uncertain cleanup blocks new native work; real view teardown
  confirms release. Overall Android automation admission remains disabled.
- Pass 83 dialog checks, 35 world/64 document/18 native-budget checks and the
  existing input 125/lifecycle 81/channel 22/guard 63/wheel 66 checks. Pass all
  245 sequential JS checks. Reused logs: `.mdo/E20-dialog-contract.log`,
  `.mdo/E20-world-contract.log`, `.mdo/E20-input-contract.log` and
  `.mdo/E20-js-full.log`.
- Compile the actual pinned Activity, hooks and native dialog target with Java 8,
  API 36/minimum 26. The same tiny QA APK is 172,435 bytes, SHA-256
  `7d89de1dd50d36214f49ed2e065691bb046a3e2bf17252fd353b1bb6d4c1a65a`.
  Build log: `.mdo/E20-android-build.log`. Current ADB inventory has no device;
  actual dialog/UI/before-unload recovery qualification remains unavailable.
  No install/provider change, native binary rebuild or production publication
  occurs. Reuse the same Home/deployment and healthy headless service. Remaining
  Android and full product work continues with the goal active.

## E21 — preserve the original document after cancelled navigation

- Reproduce the old host callback sequence with the actual production document
  controller: it is ready before a navigation request and `world_not_ready`
  after before-unload cancellation, despite the unchanged DOM/peer and a loaded
  notification. Failing evidence: `.mdo/E21-navigation-before.log`. This is a
  deterministic component reproduction, not an Android device observation.
- Separate request blocking from actual native navigation start. Stop outstanding
  work immediately, keep the original peer blocked, and retire/advance its native
  epoch only at `onPageStarted`. Confirmed cancellation restores only the same
  view/epoch request. Keep an early replacement fenced until actual start; never
  replay cancelled evaluations. Stop/errors/teardown settle request state, and
  repeated pending navigation or uncertain dialog cleanup cannot admit more work.
- Pass 81 document, 35 world/18 native-budget, 83 dialog and 125 input/85 lifecycle/
  22 channel/63 guard/66 wheel contract checks. The 17 targeted JS document and
  dispatcher checks pass. Logs: `.mdo/E21-world-contract.log`,
  `.mdo/E21-dialog-contract.log`, `.mdo/E21-input-contract.log` and
  `.mdo/E21-js-document.log`.
- Compile the actual Activity/browser/navigation/dialog hooks in the same SDK.
  The tiny QA APK is 172,435 bytes, SHA-256
  `e361fa79ccb5e18b010ca34cf78cdf111550cccc8562a09ac30918396f0dca50`;
  build log `.mdo/E21-android-build.log`. Restore the healthy headless service;
  contracts and reviewed overlays match. Windows F7 remains unchanged and its
  artifact/inventory/runtime receipts still match. Current ADB has no device;
  native framework/host-UI/before-unload qualification and remaining full-goal
  work continue. Keep Android admission disabled and the current WebView intact.
  No app/Home copy or production publication occurs.

## E22 — add the native dialog framework gate to the same QA client

- Add `--mode native-dialog` to the existing tiny APK/runner. It exercises the
  actual WebChromeClient target and retained-result controller on synthetic local
  data: alert, confirm, prompt default/empty/Unicode, stale IDs and close cancel.
  Actual native touch must activate the fixture before before-unload cancellation
  preserves the original DOM/URL/epoch. Require balanced blockers and bounded
  callbacks; background/teardown cannot claim a pass.
- Separate native-dialog and before-unload framework verification from host UI,
  isolated-world and full Agent qualification. The latter flags remain false
  even on a native-only pass. Reuse the existing package, output, signer, SDK and
  no-network-permission manifest; no app/Home/provider or dependency is added.
- Compile the actual Activity/hooks and fixture with Java 8, API 36/minimum 26.
  The tiny QA APK is 188,819 bytes, SHA-256
  `17f7dd8241ef590de5365d897c737c3a0e8b17922601d885723d548d7d1fd874`.
  Build log: `.mdo/E22-android-build.log`. The exact runner finds no authorized
  device and exits unavailable; `.mdo/android-device-dialog-result.json` has all
  verification flags false, with `.mdo/E22-device-dialog.log` as evidence. No
  installation or WebView change occurs. Android admission and the full goal
  remain open; framework behavior has not been verified.

## E23 — stop native dialog callbacks after confirmed view destruction

- Reproduce a lifecycle defect by executing the actual WebChromeClient target
  and retained controller against deterministic native callback fixtures. A late
  callback after exact view destruction still calls native cancel, and a thrown
  acknowledgement poisons already confirmed cleanup. Preserve the failing
  `.mdo/E23-dialog-target-before.log`.
- Ignore result callbacks after exact native teardown. Preserve that teardown
  if rejection cancellation itself destroys the view and then throws. Keep the
  necessary cancellation for closed views whose native teardown is not yet
  confirmed. No response is replayed and no replacement view is touched.
- Pass all 83 controller and 43 actual target callback checks with Java 8. Cover
  result subtypes, prompt data, exact owned/foreign views, original navigation
  epoch, duplicate/late callbacks, timer faults and uncertain native delivery.
  Reuse the same small QA output. Receipt/log: `.mdo/android-dialog-result.json`
  and `.mdo/E23-dialog-target-after.log`. Framework signatures are synthetic in
  this JVM gate; Android runtime behavior is not being declared verified.
- Compile the actual pinned Activity/hooks/target and device fixture in the
  existing SDK. The tiny APK is 188,819 bytes, SHA-256
  `ddb803dbbb733082cced434a525a015177932c24df4704534823a486cd069b0e`;
  build log `.mdo/E23-android-build.log`. The actual device gate remains
  unavailable with every verification flag false, recorded in
  `.mdo/E23-device-dialog.log` and `.mdo/android-device-dialog-result.json`.
  Contracts/overlays match and the same headless service is healthy. Preserve
  the current provider and keep Android admission disabled. Remaining full-goal
  work continues without production publication or an extra app/Home.

## N1 — recover explicit navigation after a page connection failure

- Reproduce an actual public C → ordinary workbench → native WebView2 defect:
  a connection reset returns `navigation_failed`, but the next explicit recovery
  is blocked before sending its GET. The page's own error card hides its native
  surface; action viewport admission mistakes that for an unavailable pane.
  Preserve `.mdo/N1-network-before.json`, `.mdo/N1-network-public-diagnosis.log`
  and the failing `.mdo/N1-reveal-before.log`. The first harness attempt also
  exposed an error-envelope type assumption; its log is preserved separately.
- Permit explicit navigation to replace that failed page only in the original
  connected/active/shown, unobstructed pane with valid DOM/native bounds and a
  matching native page error. Keep renderer failure, manual control, modal/popup,
  hidden/detached panes and bridge failures outside this admission. Input and
  capture still require native visibility; normal dispatcher guards remain.
- Pass the actual network gate: reset failure, explicit recovery, stalled GET
  timeout and recovery, read cancellation, and cancel/repeated durable admission
  after the original POST. Server evidence contains exactly one POST; recovery
  does not resubmit it. The transport made six reset GET attempts; do not confuse
  Chromium's GET retries with an application repeating the POST.
- Observed end-to-end times include UI admission/polling: reset 1,625 ms,
  recovery 1,641 ms; the 800 ms requested operation timeout returns after 2,344 ms
  end-to-end, then recovers in 1,578 ms. Read/POST cancellation takes 31/16 ms in
  this run. These are measured samples and a bounded fault gate, not a latency
  guarantee or arbitrary-network-loss qualification. Native input/capture/modal/
  popup busy and cleanup flags are clear; upload/download reservations are zero.
  Receipt/log: `.mdo/network-result.json` and `.mdo/N1-network-public-after.log`.
- All 249 sequential JS checks pass, including four new viewport tests covering
  23 refusal variations. Shared contracts and overlays match. Reuse the same
  Home/host and restore healthy headless operation. No external site or model
  call is used. Refresh the packed Windows program after this UI fix; remaining
  full-goal work and unavailable Android framework qualification continue.

## F8 — qualify the packed Windows page-error recovery fix

- Refresh the same Windows artifact with N1's workbench admission fix. The
  executable is 6,876,390 bytes, SHA-256
  `1bc3eb7911dfdf665f2fecb89012872b153d4b2ad94c81a0040471050f0d4e19`.
  Its 747-file native inventory passes and the packed panel manager/shared
  dispatcher hashes match the reviewed deployment.
- Extend the actual packed-program gate with a real loopback connection reset
  and subsequent explicit recovery navigation. The error must be reported and
  the same tab must reach the normal fixture with a fresh document. All eight
  archive/UI/C/JS/native observation, Unicode input, capture, reset/recovery and
  close checks pass. Logs/receipt: `.mdo/F8-program-package.log`,
  `.mdo/F8-program-runtime.log`, `.mdo/program-runtime-result.json` and the
  adjacent artifact inventory. This checks the delivered executable itself.
- Restore the healthy headless service, one Home and about 1,110 MiB deployment;
  no `dist/mdo-home`, app staging copy or new engine exists. Shared contracts and
  overlays match. The portable plugin is unchanged. Android's current tiny QA
  APK remains E23, with actual framework/host-UI qualification unavailable.
  Preserve the current provider and disabled Android automation admission.
  Remaining full-goal work continues; no production publication or completion
  is claimed.

## N2 — verify certificate refusal and recovery without persisted test keys

- Add a bounded loopback TLS endpoint using the existing WSL Python/OpenSSL.
  Keep generated credentials in a private pipe, an anonymous memory descriptor
  and the helper's TLS context; erase the staging buffer and close its descriptor.
  Four connection slots, bounded I/O, parent-pipe ownership and a hard helper
  lifetime prevent abandoned work. No key/certificate file, system trust import,
  browser override, application/Home copy or shipped runtime dependency is added.
- The first Windows-only fixture failed credential negotiation and produced
  generic connection-aborted error 9. Preserve `.mdo/N2-tls-fixture-before.json`
  and `.mdo/N2-tls-public.log`; this was insufficient certificate-test evidence,
  not a claimed product TLS defect. The working memory-backed fixture is also
  checked with an ordinary verifying TLS client before the browser gate.
- Pass actual public C → ordinary UI → WebView2 direct, HTTP-redirected and
  initial invalid-certificate cases. Each reports native certificate error 5,
  matching the pinned header. Across five actual TLS connections the endpoint
  receives zero HTTP application requests, with certificate-unknown alerts.
  Screenshot refusal publishes no pixels. Each explicit navigation afterwards
  recovers the same affected browser to a fresh isolated HTTP fixture.
- Final native busy/cleanup flags are clear and both transfer caches have zero
  reservations. Only mark the gate passed after the browser closes, the helper
  terminates and the same healthy headless service is restored. Receipt/log:
  `.mdo/tls-result.json` and `.mdo/N2-tls-public-final.log`.
- Contracts and applied host overlays match. Windows F8 is unchanged at SHA-256
  `1bc3eb7911dfdf665f2fecb89012872b153d4b2ad94c81a0040471050f0d4e19`.
  This is specific invalid-certificate refusal/recovery evidence, not complete
  CA/revocation or arbitrary-network qualification. Current ADB has no device;
  Android qualification and remaining full-goal work continue with the existing
  provider and disabled automation admission preserved. No production publication
  or goal completion is claimed.

## E24 — settle dialog allocation faults and guard uncertain cleanup

- Reproduce timer-allocation OOM escaping the production native dialog controller
  and retaining an unscheduled modal. Separately reproduce shared C/JS page
  preparation despite explicit native dialog-cleanup failure. Preserve failing
  `.mdo/E24-dialog-oom-before.log` and `.mdo/E24-dialog-guard-before.log`.
- Settle rejected/unscheduled native results once across liveness, metadata,
  scheduler, blocker and response allocation faults. Retain lost acknowledgement
  uncertainty instead of replaying confirm/cancel. Weak exact-ID timers stay
  inert after failed removal. Establish an allocation-free last-result tombstone
  before optional bounded history; tracking failure blocks new admission, and
  exact teardown clears retained tokens.
- Add the shared dispatcher barrier before any document preparation/input or
  capture when native modal cleanup is unconfirmed. Preserve read-only inspection
  and existing upload-release cleanup. All 95 controller/43 target checks and
  251 complete sequential JS checks pass. Logs:
  `.mdo/E24-dialog-contract-final.log` and `.mdo/E24-js-full.log`.
- Compile the actual pinned Activity/hooks in the reused SDK with Java 8/API 36,
  minimum 26. Tiny QA APK: 188,819 bytes, SHA-256
  `97c667f18ed6765dc8905b2c4534cd5f59757c4f19b3fde16e59fcc3d9813a74`;
  log `.mdo/E24-android-build.log`. The actual device gate remains unavailable,
  with all verification flags false in `.mdo/E24-device-dialog.log` and its
  receipt. These are injected fault/compile gates, not whole-process OOM or
  Android framework passes. Preserve the current provider and disabled Android
  automation admission. Contracts/overlays match and the same headless service
  is healthy. Refresh the packed shared-host fix before delivery. Full-goal work
  continues without production publication or an additional app/Home.

## E25 — retain native callback ownership before metadata allocation

- Reproduce allocation failure escaping the actual WebChromeClient target before
  its controller receives the native result. The early navigation read/response
  closure boundary was outside the controller's OOM handling. Preserve
  `.mdo/E25-native-admission-before.log`.
- Claim the original result through the controller's allocation-free tombstone
  before direct cancellation. Do not allocate another response closure, borrow
  an unproven navigation epoch, replay an owned/settled result or touch a destroyed
  provider. Unproven admission fails closed until exact view teardown; reentrant
  teardown cannot be poisoned by the later native exception.
- All 95 controller and 68 actual-target callback checks pass, including all four
  dialog kinds, native cancel OOM, duplicate active/settled callbacks and reentrant
  exact teardown. Log: `.mdo/E25-native-admission-after.log`. These injected JVM
  callback faults are not whole-process OOM or Android framework qualification.
- Compile the actual SDK Activity/hooks and reused tiny QA client. APK: 188,819
  bytes, SHA-256
  `2a13a71d8e181e81c69de60c70beaf018f921f3851900ee6607c87b080aea039`;
  source SHA-256
  `6e80cd1d33c25dbee5d846f76781efc642628d68b79b5359a82825912e0b31f2`.
  Logs: `.mdo/E25-android-build.log` and `.mdo/E25-device-dialog.log`. No current
  authorized ADB device is available, so every device-verification flag remains
  false. Preserve the current WebView and disabled global Android automation.
- Shared contracts and applied overlays match; restore the same healthy headless
  deployment. Windows packaging still needs the E24 shared guard refresh, and
  remaining full-goal/platform qualification continues without publication.

## F9 — refresh and qualify the packed shared cleanup barrier

- Repack the reviewed single deployment with E24's early shared dialog-cleanup
  barrier. Windows artifact: 6,876,415 bytes, SHA-256
  `d3b1c8ff6d92e6cbe1f82c4e36b280bd5b421936ebfdaf1fb6a07b79a45c8609`.
  Native inventory verifies all 747 files; shared dispatcher/panel-manager hashes
  match the reviewed deployed source. Artifact and runtime receipts both verify.
- Start that actual self-contained executable directly from `dist/`, in the
  same Home, immediately after packing. All eight archive/UI/C/JS/native checks
  pass: native observation, Unicode input/trusted click, capture, connection-reset
  reporting, explicit recovery navigation and close. The two packaging integrity
  contracts pass. Logs: `.mdo/F9-program-package.log`,
  `.mdo/F9-program-runtime.log` and `.mdo/F9-package-contracts.log`.
- Restore the healthy headless service and matching overlays, one Home and about
  1,110 MiB deployment. No `dist/mdo-home`, application copy or downloaded engine
  exists. The portable plugin stays unchanged. Android tiny QA client remains
  E25 with framework qualification unavailable and overall automation disabled;
  keep the installed provider. Remaining full-goal work continues, with no
  production publication or completion claim.

## F10 — complete sequential regression and native startup samples

- Run the entire current Python suite against this same deployment: 132 cases,
  130 passed and two skipped because Windows does not grant symlink creation
  (error 1314). The unavailable source/upload symlink cases remain unverified;
  they are not passing checks. Log: `.mdo/F10-python-regression.log`.
- All 251 JavaScript checks pass, strictly sequentially, including shared
  dispatch/admission, document/input fences, native failure projections, frames,
  downloads, capture privacy and visual identity.
  Log: `.mdo/F10-js-regression.log`.
- Re-run the existing real WebView2 startup gate against local fixtures, in the
  reused native profile. Hidden/visible/hidden/visible samples are 683/217/204/224
  ms, with no post-deadline show recovery, errors or new diagnostics. All satisfy
  the original 10-second readiness bound. These four sequential samples do not
  prove cold-profile or production-load latency, and do not erase the historical
  unreproduced slow-start evidence. Log: `.mdo/F10-native-startup.log`.
- Shared contracts match and the same healthy headless service is restored.
  Windows F9/Android E25 artifacts stay unchanged. Android framework qualification
  remains unavailable, with its current provider and disabled automation
  preserved. The broader native fault/performance/platform work is still active;
  no production publication or completion is claimed.

## E26 — settle allocation faults after native input admission

- Reproduce OOM escaping a retained native input transaction after dispatch has
  begun. Preserve `.mdo/E26-native-input-oom-before.log`. This can skip original
  held-pointer/key cleanup and leave the caller without a terminal receipt.
- Handle allocation faults in post-admission target/document checks, isolated
  guards, native dispatch, rescheduling and terminal timer/held-state cleanup.
  Release the original touch/key once on failure; never reissue its press, text
  or action. Unconfirmed cleanup blocks further admission. Failed timer removal
  leaves inert callbacks, not a reusable live transaction.
- If result allocation/delivery fails after cleanup, do not replay completion or
  claim a fresh clean state. Fail closed and settle any work a reentrant
  completion had admitted, before that new work sends input. Pre-effect timer
  admission OOM preserves its existing original-error/zero-effects contract.
- Pass 177 input, 85 lifecycle, 22 channel, 63 guard and 66 wheel checks (413
  assertions total). The extended input gate covers touch/key/text dispatch OOM,
  uncertain native releases, scheduler/guard/document faults and lost/reentrant
  completion. Log: `.mdo/E26-native-input-oom-after.log`. These are injected
  production-core faults, not whole-process heap exhaustion or framework proof.
- Actual Activity/hooks compile in the reused SDK. Tiny QA APK: 188,819 bytes,
  SHA-256 `d847e16d857669b18ef15838008248ad86c427ea45f581815e737433054a13db`;
  source SHA-256
  `a6d37968c57596e7070ca0378a4d5e71f7680758b8aad33923b5865b78659708`.
  Log: `.mdo/E26-android-build.log`. The new native-input device receipt is
  unavailable with all verification flags false (`.mdo/E26-device-input.log`);
  no authorized device is present. Preserve the current WebView and disabled
  overall Android automation. Windows F9 is unchanged. Contracts/overlays match
  and the same headless deployment is healthy. Full-goal work continues.

## E27 — implement guarded Android main-document hover

- Add one native mouse `ACTION_HOVER_MOVE` to the owned WebView through generic
  motion dispatch, with zero buttons and mouse tool/source. Preserve normal
  pointer position after completion. No focus, touch, click, held-button cleanup
  sequence, DOM mouse-event synthesis or runtime dependency is introduced.
  Pin consulted AOSP/Chromium sources and hashes in
  `host/android/hover-protocol.json`; this reference is not a provider version gate.
- Connect public C/JS `act(op:"hover")` through shared capability admission,
  original element/document preparation and private native `move`. The native
  fence rechecks exact point/scale/viewport/root ref and refuses child-document
  hits. Old hosts lacking `hover_input` refuse before page preparation. The
  independent overall Android automation gate stays disabled.
- Pass 82 new production-sequencer hover assertions, plus 413 existing input/
  lifecycle/channel/guard/wheel assertions. Cover delayed/stale guards, document/
  viewport/visibility changes, deadline/cancellation/replacement, native refusal,
  allocation failure and reentrancy without press/focus/text events. Log:
  `.mdo/E27-hover-contract.log`. All 255 complete sequential JS checks pass;
  `.mdo/E27-js-full.log`. The first new drift fixture changed a point without
  its correlated runtime geometry; correct that fixture at the final preparation
  boundary, rather than treating its inconsistent protocol as a product defect.
- Extend the reused actual-framework input fixture to require one trusted move
  at the exact CSS coordinates/target, active `:hover` style, zero buttons,
  unchanged focus and no touch/click. `native_hover_verified` starts false and
  stays false in unavailable/installation-wait receipts, separately from overall
  automation and guarded-world qualification.
- Compile actual Activity/hooks and QA fixture with the same SDK. APK: 192,915
  bytes, SHA-256
  `8eb85f17d39053228f713f0750c90c08d75d75e66761d102efa149fc893c0a45`;
  source SHA-256
  `5006f95539006765f05628a26bbaa2bf4381606eb698fada225eaeff0433fe1f`.
  Logs: `.mdo/E27-android-build.log`, `.mdo/E27-device-input.log`. Current ADB
  has no authorized device; actual hover/framework verification remains false.
  Keep the installed provider and disabled Android automation. Refresh the packed
  shared dispatcher in F11; remaining full-goal work stays active.

## F11 — pack the shared hover capability path and verify the Windows artifact

- Refresh the existing Windows executable from the reviewed single deployment:
  6,876,426 bytes, SHA-256
  `6ede1c5194bcba3a6e1bceacf32220ce9a039bdbd3b3b6d6eb99af412eb59c6d`.
  All 747 native inventory entries verify. Packed dispatcher, document driver
  and panel-manager hashes match applied source; artifact/runtime receipts verify.
- Pass all eight checks in the actual self-contained Windows program, started
  directly after packaging in the same Home: archive/UI readiness, C/JS/native
  observation, Unicode input/trusted click, capture, reset error, recovery
  navigation and close. Logs: `.mdo/F11-program-package.log` and
  `.mdo/F11-program-runtime.log`. This checks Windows delivery and does not
  qualify Android native hover or its unverified framework route.
- Restore healthy headless operation, matching shared contracts/overlays and the
  same Home. No `dist/mdo-home`, app copy or browser download is added. The portable
  plugin remains unchanged; the tiny Android QA APK is E27, with current device
  verification unavailable and overall automation disabled. Preserve the current
  provider. Remaining full-goal/platform qualification continues; no online
  publication or completion is claimed.

## Follow-up verification boundaries after F11

- Attempt the ordinary desktop-window visual check with the bundled Computer
  Use workflow. Both available JavaScript UI runtimes fail before initialization
  with `failed to write kernel assets` / Windows path-not-found error 3. No target
  window is selected and no UI input is sent. Actual main-window desktop pixels
  remain unverified; passing native page captures and the packed-program gate
  do not replace this check. The unchanged failure is a tool availability limit,
  not evidence of a new mdo rendering defect.
- Inspect the default-workspace save boundary before adding another API. The
  pinned host already has `files.resolve(path:".",prepare_default:true)`, narrowed
  to the canonical default project root under a project lease; normal default
  session creation also prepares that directory. Manual download-save currently
  requires an existing physical root and has no preparation flow. A future
  integration must preserve existing root identity pins, refuse arbitrary/custom
  path reconstruction and retain response bytes on uncertainty. No directory
  was moved or created during this inspection, and no fix or passing end-to-end
  missing-directory gate is claimed.

## E28 — reviewed canonical default directory preparation in the shared SDK

- Extend read-only `browser.workspace.lookup` with `prepare_default:true` and add
  `browser.workspace.prepare` to the same generated C/JS/discovery contract.
  Review resolves the original persisted task only, reporting current browser/
  task revisions, canonical path, existence and physical identity when present.
  It never opens a model or creates a directory.
- Preparation requires explicit confirmation and the reviewed browser/task/path/
  existence/identity. Only the canonical default project directory is eligible;
  original custom roots, missing tasks, foreign private members and already
  physically pinned bindings cannot be reconstructed. Revalidate task metadata,
  cancellation, original row revision and the named physical root before saving.
- Create a missing default directory through the retained Home root and constant
  storage-relative name, not a caller path, under a project lease. Persist its
  physical pin before acknowledging the browser binding. Allocation/persistence
  failures after a possible directory/binding effect remain uncertain and require
  rereading the binding; no download/request or file publication is replayed.
- Ten actual C-host tests pass in `.mdo/E28-default-workspace-final.log`, covering
  missing-root review/creation, explicit preconditions, physical replacement,
  forbidden recreation of pinned roots, foreign scope, missing tasks, legacy
  empty context, a regular file at the default path and generated SDK parity.
  Missing-root cases rename and restore this single QA Home's exact workspace;
  no copy/Home is added, and original physical identity and contents are retained.
  All 11 existing legacy-root recovery tests also pass
  (`.mdo/E28-legacy-workspace.log`).
- Download UI integration is next. The running single development deployment
  includes the SDK feature; Windows F11 remains the previously verified artifact
  pending refresh. Android framework/normal-desktop visual qualification remains
  unavailable as recorded above. No production publication or completion claimed.

## E29 — complete the download UI's reviewed default-directory workflow

- Add trusted chrome inspection/confirmation for eligible default-task directories.
  Show `检查任务目录` for an unpinned bound directory, or the existing original-task
  lookup for legacy tabs. Missing canonical default directories offer
  `准备并确认此目录`; existing directories offer explicit adoption/pinning. Custom
  roots keep restoration semantics. A stored physical pin cannot be replaced by
  a fresh server row or silently reconstructed.
- Carry only reviewed root fields into the shared preparation service. Recover a
  lost reply by rereading the same browser binding, without repeating preparation,
  requesting the URL again, opening a byte stream early or changing the frozen
  owner/task/private member. Unexpected review errors do not fall back to another
  directory. Add bounded, actionable binding-failure messages.
- Pass 31 focused save/user-action checks and all 260 sequential JS checks.
  Logs: `.mdo/E29-download-workspace-unit.log`, `.mdo/E29-js-full.log`.
  Complete Python regression passes 140 of 142 cases; the two existing Windows
  symlink-privilege cases remain skipped (error 1314), not verified passes.
  Log: `.mdo/E29-python-full.log`.
- In the actual mounted WebView2/native browser, pass 18 legacy/custom, 17
  existing-default and 17 missing-default UI checks. Each accepts and retains one
  original response, confirms the original directory, saves exact bytes/hash,
  preserves editing focus, resizes the native surface, publishes/reconciles
  recovery data and drains cache reservations. Each makes exactly one `/binary`
  request. Logs: `.mdo/E29-legacy-download-ui-final.log`,
  `.mdo/E29-default-download-ui-final.log`,
  `.mdo/E29-missing-default-download-ui-final.log`; corresponding result receipts
  are `.mdo/download-ui-result.json`, `.mdo/default-download-ui-result.json`,
  `.mdo/missing-default-download-ui-result.json`.
- The missing-root UI gate temporarily renames this same Home's exact default
  workspace, then restores its original physical identity and contents. No Home
  or app copy is added. Only mark success after exact synthetic-output checks,
  cleanup, original-root restoration and healthy headless restart; preserve one
  bounded evidence directory on failure. This qualifies the mounted workflow,
  not the unavailable normal-desktop screenshot or Android framework. Refresh
  Windows delivery next; remaining full-goal work stays active.

## F12 — deliver and verify the reviewed directory workflow in the Windows pack

- Refresh the same self-contained Windows program: 6,877,938 bytes, SHA-256
  `ac9f89d0c13041dc40f959ffc00fb3a28e41b53a9ba1957902a8e0a836ab613d`.
  Native inventory verifies all 747 entries, including the reviewed generated C
  workbench header, JS service contract, native directory service and download
  action/UI files. Their artifact/source hashes match.
- Extend the actual packed-program gate with a real default-task read-only
  directory review, using the ordinary registered client and original private
  scope. Confirm that lookup reports the canonical physical directory without
  mutating the browser. Verify the delivered C convenience header and discovered
  preparation schema. All nine packed checks pass, alongside native observation,
  trusted Unicode input/click, capture, reset/recovery and close. This additional
  artifact check is read-only review; the full missing-directory UI workflow has
  its separate actual E29 evidence. Logs: `.mdo/F12-program-package.log` and
  `.mdo/F12-program-runtime.log`; current artifact/runtime receipts verify.
- Restore the healthy headless service and same Home, with both held-workspace
  paths absent and original roots restored. No `dist/mdo-home`, app copy or new
  engine exists; deployment is about 1,110 MiB. Contracts and applied overlays
  match. The portable plugin and Android E27 QA artifact are unchanged. Preserve
  the current WebView and disabled Android automation. Full-goal platform/
  transport/production qualification remains active; no online release or
  completion is claimed.

## E30 — retain and adopt Android native popup contents

- Implement exact-opener `WebChromeClient.onCreateWindow` transport ownership,
  sharing the existing popup admit/create/settle SDK and scoped panel manager.
  Android exposes neither a trustworthy target URL nor a frame identity here;
  admission uses explicitly unknown `about:blank` metadata. Adopt a fresh attached
  unnavigated child through the original framework message, without loading the
  caller's URL again. Manual browsing gains popup transport; automation remains
  disabled and popup isolated-world installation stays explicitly unqualified.
- Retain at most one native request per opener and eight globally, with a 15s
  pending lease and 5s dispatch barrier deadline. Claim each original message
  before dispatch, including null-child rejection. The same UI queue's barrier
  proves callback consumption, not page load or world readiness. Lost dispatch/
  barrier acknowledgement retains uncertainty/resource budget until the exact
  native view is destroyed; no replay is allowed. Navigation, hide, pause,
  channel detach, takeover and teardown invalidate original ownership. Native
  page-close is reconciled by the existing scoped browser/panel manager.
- Block effectful work after uncertain popup cleanup. Test unknown-URL Android
  admission through the existing JS popup flow without invented navigation.
  Pin public API/Chromium evidence and limitations in
  `host/android/popup-protocol.json`.
- Pass 74 production controller and 25 real Chrome-target contract assertions
  (`.mdo/E30-popup-contract.log`). Existing world/document/native-budget (134),
  dialog (163), and input/lifecycle/channel/guard/wheel/hover (495) assertions
  pass in `.mdo/E30-{world,dialog,input}-contract.log`. These use protocol doubles,
  not an installed Android provider. All 262 sequential JS tests pass; Python
  passes 140/142 with the same two Windows symlink privilege skips. Logs:
  `.mdo/E30-js-full.log`, `.mdo/E30-python-full.log`.
- Compile the actual reviewed Activity/hooks and tiny QA APK with Java 8/API 36,
  without another Home, native engine or provider upgrade. APK is 209,299 bytes,
  SHA-256 `38e04e2382cc765f72b6f6b004c57adee5a1e0a24eabb80a10ebe9d5cdbeaf65`
  (`.mdo/E30-android-build.log`). ADB has no device; framework popup qualification
  and raw device fixture are outstanding. Overall Android admission remains
  false. The healthy headless deployment is restored (about 1,110.6 MiB).
  Windows F12 remains its previously qualified artifact, pending later refresh
  for shared cleanup-guard changes. Full goal remains active; no online release.

## E30 correction — keep an accepted transport owned when a host query fails

- A regression reproduced an exception after acquiring a native popup but before
  returning its accepted callback. Escaping to WebView's `false` rejection while
  retaining the original timer could leave two cleanup paths for native pending
  contents. Catch failures of post-admission ownership/epoch queries inside the
  controller, poison the opener, and complete the original transport once with
  null. Retain budget until the same-queue barrier or exact view teardown.
- The regression fails on E30 (`.mdo/E30-popup-ownership-before.log`) and passes
  with the fix (`.mdo/E30-popup-ownership-after.log`): 78 controller and 25 Chrome
  target assertions. This is a protocol contract result, not a framework pass.
  Actual SDK/fixture packaging is refreshed in the following device-gate stage.

## E31 — add a provider-independent native popup device gate

- Add `native-popup` to the same tiny QA app/device runner. It needs no isolated
  world or provider upgrade and uses synthetic loopback-origin data only. Check
  original `window.open` document-write/opener contents in a fresh attached
  child, original-ticket rejection, native page-close, null-child rejection,
  actual opener-navigation cancellation and released budgets/callback counts.
  Reuse one child; cleanup owns/destroys exact views without another app/Home.
- Separate native transport/content verification from production host routing,
  isolated popup world, pixels and Agent input. The 1px mounted fixture claims
  none of the latter. Backgrounding terminates the gate explicitly. No support
  flag or unavailable/confirmation receipt can imply full automation readiness.
- The actual Activity, all reviewed host hooks, popup transport and raw fixture
  compile in the current minimal SDK (`.mdo/E31-android-build-final.log`). QA APK
  is 225,683 bytes, SHA-256
  `f633c927fb3df4107a83df7bf29f1fe45f7611c1eda10f9c519f2533de22e431`.
  ADB has no device (`.mdo/E31-device-popup.log`); its terminal receipt records
  `android_device_unavailable`, `native_popup_verified:false`, host routing/
  popup-world qualification false and overall automation false. No install or
  component upgrade occurred. The same healthy headless deployment is restored.
  E30's 262 JS and 140/142 Python results remain applicable to unchanged shared
  code, with the separate 103-assertion ownership correction above. Full goal
  remains active; installed-provider/production-popup qualification is pending.

## F13 — refresh the Windows artifact with shared popup cleanup admission

- Refresh the same self-contained program with the shared cleanup guard:
  6,877,954 bytes, SHA-256
  `518e6f1495768cbf4273eaf197d66629c00af34e1fde25f793f53760711262ed`.
  Artifact hash and native inventory verify; all nine actual packed-runtime
  checks pass (`.mdo/F13-program-package.log`,
  `.mdo/F13-program-runtime.log`). The checks exercise ordinary client
  registration, read-only default-directory SDK review, C/JS native observation,
  trusted Unicode fill/click, capture, network-reset reporting, recovery and
  exact browser close. They do not substitute for normal-desktop pixel or
  Android framework qualification.
- Restore the same healthy headless service and Home; deployment is about
  1,112 MiB. Reviewed overlays match, both held-workspace paths are absent and
  no `dist/mdo-home` or extra app/engine copy exists. Repository is on `master`;
  no online publication or provider change. Android's E31 raw fixture is built
  but its installed-provider result remains unavailable/false. Full goal stays
  active while outstanding platform/production qualification continues.

## F14 — full native verification including the production dialog lease

- Run the actual WebView2 gate with `--dialog-expiry` against F13's deployment,
  before the following capture correction. Pass 1,504 assertions, including
  dialogs/popups, native input, file/chooser transfers, cancellation and recovery.
  The real dialog expires after 120,945 ms; one replacement becomes ready in
  131 ms. These are observations for that run, not latency guarantees; assertion
  totals include polling. Log: `.mdo/F14-native-full.log`.
- No new native crash/exception diagnostics appear. Both transfer caches have
  private ACLs and zero remaining reservations. Interrupted and POST responses
  are each requested once, and native cleanup removes a deliberately recreated
  empty retired download directory. Restore the same healthy headless service.
  Resource/OOM/performance qualification continues; this is not full completion.

## F15 — retain screenshot ownership through native callback retirement

- Reproduce early native screenshot admission release when a view closes while
  its CDP callback is retained. Keep the host's single capture reservation until
  the provider invokes or releases that exact callback. Detach closed active
  slots without keeping the view/job cycle. Dropped live/cancelled callbacks
  settle safely; late old callbacks cannot complete a newer byte-budget retry.
  Failures of timer admission, dispatch, result storage and reply publication
  settle logical work without returning retained native ownership early.
- Also reproduce a leaked CDP callback reference when parameter encoding throws
  before dispatch. Use the existing COM ownership wrapper for the caller's
  callback reference, including exceptions and failed native dispatch. Keep the
  provider's accepted reference until its own retirement; never invoke a second
  callback to compensate for a conversion/handler exception.
- Both regressions fail on the previous code (`.mdo/F15-capture-before.log`,
  `.mdo/F15-cdp-before.log`). Actual production C++/COM helper checks pass 50
  screenshot lifetime assertions and 13 callback-reference assertions. A
  one-shot allocator sweep covers 14 admission/dispatch and five result
  allocation sites for the fixed screenshot workload, with no remaining slots.
  Logs: `.mdo/F15-capture-oom.log`, `.mdo/F15-cdp-after.log`. This is controlled
  component OOM evidence, not exhaustive live-process OOM qualification.
- Rebuild both native hosts in the existing source/object root
  (`.mdo/F15-native-build.log`). Pass all 262 JS cases and 142/144 Python cases;
  the same two Windows symlink-privilege cases remain skipped. Logs:
  `.mdo/F15-js-full.log`, `.mdo/F15-python-full.log`. The actual native gate
  passes 1,387 assertions without the long dialog-expiry mode, no new native
  diagnostics, and both upload/download reservations drained
  (`.mdo/F15-native-full.log`). The separate F14 long-lease evidence is above.
- Pass three first public screenshot cases (1,984 / 1,828 / 1,812 ms, zero initial
  failures), six public C/JS privacy cases and normal public C/workbench OOPIF
  input/transfer/managed-permission checks. Logs: `.mdo/F15-capture-readiness.log`,
  `.mdo/F15-public-capture.log`, `.mdo/F15-public-frames.log`. These use the same
  Home and synthetic loopback fixtures, with zero model/external-site calls.
  They do not prove normal-desktop pixel or Android framework qualification.
- Restore the healthy headless service (about 1,117.8 MiB). Android is unchanged,
  ADB has no authorized device, and full automation remains disabled. F13's
  executable is the earlier verified artifact; refresh it next to include this
  native fix. The full goal and remaining platform/transport/permission/
  lifecycle/performance qualification remain active.

## F16 — refresh delivery and reject a stale paired plugin runtime

- Refresh the Windows program with F15's native fix: 6,878,978 bytes, SHA-256
  `ed71fc9ba815ceb796a8605b971bf02f5c16923f22267f7b2b188a88e6c9ba1f`.
  All 747 native inventory entries verify and the packed native-host hash
  matches the rebuilt GUI host. Initial nine packed checks pass
  (`.mdo/F16-program-package.log`, `.mdo/F16-program-runtime.log`).
- Source comparison then finds the old portable package's `page/runtime.js`
  was stale: 37,891 bytes, SHA-256
  `82f12f89d3d1b6c254bc139b520bc3dc759a75598a892df544a08e7daef82133`,
  versus reviewed 39,633 bytes, SHA-256
  `580d95cb3d6a54b0abd3559885611658112a1f15424a0181379ac9228ca757cd`.
  Record the mismatch without copying an app/Home
  (`.mdo/package-runtime-sync-before.json`). Rebuild the portable plugin:
  243,663 bytes, SHA-256
  `3b9a4b949ac94ef8cbecba786ba45793ed47b9e1356ae75fcb7f941ba32e76a6`.
- Pass eight actual local ecosystem import/trust/plan/restart/export/reinstall
  checks for all 22 files in the same Home. Logs:
  `.mdo/F16-plugin-package.log`, `.mdo/F16-plugin-install.log`. Old generations
  remain retained; no unrelated revision or user data is overwritten.
- Make the packed-program gate compare the paired portable artifact to current
  source before starting any app. After boot, require the actual enabled plugin
  manifest/inventory and packed host runtime to match the paired source. Track
  `paired_plugin_verified` and exact artifact metadata separately. A real
  same-size payload alteration is refused before process launch; restore exact
  original bytes (`.mdo/F16-pair-rejection.json`). With the current pair, all ten
  actual packed checks pass (`.mdo/F16-paired-program-runtime.log`). This closes
  a delivery-verification gap; older smoke checks alone did not prove pairing.
- Restore the same healthy headless deployment (about 1,118 MiB). Source overlays,
  artifact hashes and paired receipts verify; no `dist/mdo-home`, extra app or
  browser engine exists. Android E31 is unchanged and no device is available.
  Current WebView/disabled automation and remaining full-goal requirements are
  preserved. No online release or full production/platform completion claimed.

## P1/P2 — real managed approval flow and accurate cancelled tool outcomes

- Reproduce a pending balanced-profile approval cancelled by its parent SDK
  operation. The old host reports `failed`, `sdk_tool_execution_failed`, 409,
  despite cancellation. Preserve the failing actual-host receipt and regression
  (`.mdo/P2-managed-cancel-before.json`,
  `.mdo/P2-cancel-regression-before.log`).
- Add a small atomically retained marker at the actual before-tool hook, after
  permission admission. Bind its lifetime to the Agent owner so retained
  delegated work cannot call a stack pointer. Keep the existing permission
  callback and live run scope. Classify explicit cancellation as 499 and deadline
  expiry as 504, preserving the executor cause. Before admission, no tool was
  dispatched; after admission, a lost executor result retains possible effects.
- Pass 21 controlled production-C assertions for allocation failure, hook order,
  retained-owner lifetime, cancellation/deadline priority, cause and uncertainty.
  The actual SDK cancellation regression now passes and the next managed read
  succeeds (`.mdo/P2-cancel-regression-after.log`).
- Add one headed workflow using a fixed owned balanced-profile QA session and
  synthetic loopback page. Match each decision to the exact original operation,
  tool, arguments, workspace and external-browser resource; never approve
  unrelated pending requests. Verify denial before creation, approved native
  creation, denied input, an approved trusted click exactly once, idempotent and
  conflicting decisions, original operation replay, cancelled approval/late
  grant, dispatched wait cancellation/drained UI work, run-scope retirement,
  document changes during approval, and a fresh approved recovery action.
- All nine scenarios pass (194 checks in this run), no model/external requests,
  owned operations/browsers cleaned and the same headless deployment restored.
  Evidence: `.mdo/P1-ask-permission-dispatch.log`,
  `.mdo/ask-permission-result.json`. The decision API drives the synthetic user
  choices; `visual_prompt_verified:false` explicitly remains recorded.
- Complete regression passes 144/146 Python cases and all 262 JS cases. Two
  existing Windows symlink-privilege cases remain skipped. Logs:
  `.mdo/P2-python-full.log`, `.mdo/P2-js-full.log`. Reviewed host overlays and
  generated C/JS contracts match. F16 artifacts still need refresh for the new
  app C code; the portable plugin payload is unchanged. The full goal remains
  active, with the installed Android provider preserved and automation disabled.

## F17 — deliver the retained permission-admission outcome fix

- Rebuild in place after P2: Windows executable 6,880,340 bytes, SHA-256
  `25fdf76e6d9cfa1dbaa931a4f51731fcebfc551503949dbde56a459396da3494`.
  All 748 native inventory entries match; the new outcome module is included.
  The portable plugin remains byte-for-byte equal to the reviewed source and
  F16's paired 243,663-byte payload. No native rebuild is needed for this app C
  change.
- The actual packed program passes ten runtime checks, including paired plugin,
  default-workspace SDK, C/JS/native observation, Unicode/trusted input, capture,
  network-error reporting and explicit recovery. Both `runtime_verified` and
  `paired_plugin_verified` are true. Logs: `.mdo/P2-program-package.log`,
  `.mdo/P2-program-runtime.log`.
- Restore the same healthy headless deployment, about 1,118.6 MiB. No extra Home,
  app copy or downloaded browser is created. The artifact is a development
  preview; no website/update publication or complete-platform claim is made.

## P3 — bounded JS admission, result reads and cleanup over a stalled transport

- Find that JS deadlines/cancellation were checked only between polls. A held
  admission/result request or release acknowledgement could keep the caller
  pending indefinitely. Four independent regressions fail on the old SDK,
  including one through the actual C service (`.mdo/P3-client-before.log`).
- Apply one timer budget across admission, response reads and every poll.
  Forward an AbortSignal and race the adapter as well, so an ignored signal
  cannot keep the public wrapper blocked. Validate timer durations before an
  effect. Read-only tab/result and direct operation helpers use the same bound.
- Use a separate bounded cleanup attempt on the same SDK; an expired caller's
  signal cannot suppress cancellation. A stalled release cannot hide an observed
  success or original page failure, nor cause a terminal result to be cancelled.
  Preserve durable operation and UI receipt identities on transport failure.
  Late admission/effects remain possible; never infer rollback or resubmit.
- Six new cases pass: ignored-abort admission, held result/deadline, stuck
  release with success/failure, stuck cancellation acknowledgement, combined
  admission/read budget and invalid budgets, and original recovery after a lost
  actual C admission reply. That HTTP fixture uses the same service/Home, no
  second app, no model/external request, and no persisted test token. It verifies
  exactly one original browser; owned browsers/receipts are cleaned afterwards.
- The focused SDK suite passes 13 cases and all 268 JS cases pass. Logs:
  `.mdo/P3-client-after.log`, `.mdo/P3-js-full.log`. This qualifies the shared JS
  boundary with an actual loopback C endpoint, not authenticated native remote
  relay/direct transport. Refresh the paired delivery next; the full goal and
  installed-provider/Android/visual/lifecycle qualification remain active.

## F18 — refresh and verify the bounded-transport plugin pair

- Refresh the portable plugin with P3: 246,883 bytes, SHA-256
  `ad8c4826ed3ed03c0cfc44ddce696ab0bb88b87d9628020e427bc094dd305912`.
  Actual ecosystem import/trust/plan/enable/restart/export/reinstall passes eight
  gates and matches every decoded byte in all 22 files. Logs:
  `.mdo/P3-plugin-package.log`, `.mdo/P3-plugin-runtime.log`.
- Repackage the Windows executable in place. Its app/native bytes are unchanged
  from F17 (6,880,340 bytes, SHA-256
  `25fdf76e6d9cfa1dbaa931a4f51731fcebfc551503949dbde56a459396da3494`);
  this JS SDK change belongs to the paired plugin. Pass all ten actual packed
  checks against the refreshed installed/source/plugin inventory. Both delivery
  flags are true with the exact new paired hash. Logs:
  `.mdo/P3-program-package.log`, `.mdo/P3-program-runtime.log`.
- The same headless Home/service is healthy after restoration. No app/Home copy,
  external-site/model call or online release is made. Current ADB inventory has
  no device; preserve the user's WebView and leave Android automation disabled.
  This stage delivers the tested Windows pair while the full platform/remote/
  visual/lifecycle qualification remains active.

## R0 — production native remote bridge against the existing C SDK

- Add one bounded component executable using the actual pinned XRT and the
  production bridge, HTTP loopback, receipt store and socket sources. The target
  remains the single existing mdo/Home. A narrow host-server lease seam points
  to that loopback port; unused transitive account/Home APIs fail rather than
  reaching an account or filesystem. Private inherited pipes authorize the
  synthetic peers, with no listener, test backdoor or authentication claim.
- Compile once into the existing QA directory, reusing one header/compiler/
  flags/object-hash-checked XRT object. Record all actual compiler dependencies
  and binary SHA-256; refuse changed inputs before probe launch. No app/native
  source/Home copy is made. The executable is about 4 MB; no runtime dependency
  or browser engine is added to the product.
- Run the production JS remote transport through actual C request admission,
  upload hashes, response flow control and receipt lookup into the existing
  Application SDK. Verify owned browser creation, exact original operation ID,
  foreign-task rejection, confirmed completed transport receipt after a dropped
  reply/disconnect, recovery of exactly one original browser, and JS plus native
  read-only rejection. Do not replay the original submit.
- Verify a 155,000-byte synthetic UTF-8 file across multiple binary upload and
  response frames. Create only a fixed QA file exclusively and remove it with
  its returned revision. Retained/unrelated entries are not overwritten. All
  bridge worker/upload/packet counters drain, every host lease retires and the
  private XRT engine stops cleanly.
- Initial large-frame failures expose a saturated 16-entry fixture input queue;
  add bounded pipe backpressure rather than dropping commands. This is a test
  harness issue, not evidence of a product bridge defect. The first exclusive
  create attempt also used an unregistered raw SDK method; use exclusive local
  fixture creation followed by the actual remote stat/write/read/remove APIs.
- All four native integration cases and all 272 JS cases pass. Logs:
  `.mdo/R0-bridge-build.log`, `.mdo/R0-bridge-runtime.log`,
  `.mdo/R0-js-full.log`. Existing program/plugin artifacts are unchanged.
  Normal headed Browser Use through this transport, authenticated relay/direct
  physical-device verification, Android, visual and remaining lifecycle gates
  remain active; the component fixture alone cannot close them.

## R1 — production remote bridge drives the actual Windows browser workflow

- Add a headed workflow using the existing deployment, Home, compiled remote
  component and installed plugin. Use one fixed owned full-access task with
  immutable custom workspace/namespace/member binding. The production remote JS
  transport and C bridge call the actual C SDK/plugin, mounted workbench and
  native WebView2; no fake UI acknowledgement supplies browser results.
- Pass eight workflows: observe real loopback content and original task scope;
  Unicode fill plus exactly one trusted native click; bounded synthetic PNG;
  cancellation of an observed dispatched wait followed by another observation;
  navigation/stale-document refusal and recovery; frozen-workspace upload with
  the page's complete SHA-256; retained original POST download and exact target
  workspace save; browser closure and drained native bridge requests/workers/
  uploads/packets. The output file is removed only if its bytes still match the
  fixed synthetic response. Unrelated existing destinations are preserved.
- Upload/download each verifies 131,075 bytes, SHA-256
  `f6589d93167a38ddd6bd9337c012f163142839caf8667ce80e012469649e58fb`.
  The fixture sees exactly one POST, proving no repeat download request in this
  workflow. The PNG is recorded only by byte count/hash; no page/image content
  enters the durable report. There are zero model or external-site calls.
- Initial save verification used the wrong top-level metadata field; correct
  the assertion to the public `file.size`/`file.sha256` contract and rerun the
  complete headed workflow. This is a probe assumption issue, not a newly
  claimed product defect. Preflight fixture paths before service mutation and
  restore the same headless service even when headed setup fails.
- All eight gates pass. Evidence: `.mdo/R1-remote-browser.log`,
  `.mdo/remote-browser-result.json`. R0's all-272-JS result remains current for
  production code; R1 adds two standalone probe files and documentation. No
  artifact payload or production source changes. This is the preauthorized
  private-pipe bridge path; account/public relay/LAN/XRUDP/two-physical-device
  authentication, normal-desktop pixels, Android and remaining lifecycle/
  production qualification are not inferred from it. The full goal stays active.

## P4 — executable transfer verification preserves the real SDK binding

- Inspection after P3 finds three old probe adapters treating `sdk.call`'s third
  argument as context rather than call options. Read-only actual C SDK tests
  reproduce all three falling back to `local-client`; no browser/file effect is
  needed to diagnose that context loss. Evidence:
  `.mdo/P4-probe-sdk-before.log`.
- Bind each public probe client to its original context, pass the caller's
  cancellation signal with a bounded local transport timer, and move deliberate
  foreign-scope calls to explicit `callInScope`. Correct the nested directory
  client and raw C directory call too; the missed nested override first produces
  a real empty-directory refusal, rather than falsely qualifying that path.
- Reset the upload child receipt to running before effects and write failures
  explicitly. A prior successful child result had remained readable during an
  incomplete new run; the nonzero parent/child process status always prevents a
  final pass. Use current-run receipts plus authoritative terminal process
  evidence for subsequent claims.
- All three actual C context/signal tests pass, including custom directory scope,
  an already-aborted call and explicit foreign-context inspection. Complete
  public C/JS plus managed Windows upload, download and save gates pass with
  the updated SDK: byte/empty/directory hashes, picker lifecycle, long names,
  durable duplicate suppression, raw services, owned read-only permissions,
  retained POST/blob/unknown responses, target workspace, existing-target
  protection and foreign task/private context rejection. Upload, download and
  save cache reservations each return to zero with private ACL verification.
- Logs: `.mdo/P4-probe-sdk-after.log`, `.mdo/P4-upload.log`,
  `.mdo/P4-download.log`, `.mdo/P4-save.log`. All 275 JS cases pass
  (`.mdo/P4-js-full.log`). This fixes verification adapters, not a newly claimed
  product/native bridge defect. The production plugin/program pair and Android
  provider remain unchanged. The same healthy headless deployment is restored;
  authenticated physical-device paths, Android, normal-desktop pixels and
  remaining full-goal qualification remain active.

## E32 — original Android chooser retention and cancellation

- Add a UI-thread controller and actual `onShowFileChooser` target using only
  public framework APIs. Preserve ordinary manual behavior until explicitly
  armed. Retain one original callback per exact view, with an opaque identity,
  navigation epoch, bounded immutable accept metadata and a 120s monotonic lease.
  The controller has no URI assignment/grant API. Do not infer a source frame
  from the callback's view or its URL.
- Wire list/arm/cancel into the reviewed native host, and block document/input/
  capture admission while a chooser is pending. Navigation, ownership loss,
  hiding/background/channel detach, disarm and close cancel the original callback
  once. Track retired live callback identities without replay; result loss and
  capacity/allocation uncertainty require exact view teardown before reuse.
- Refresh lazy expiry before checking readiness. A successful native callback
  followed by a failed document-unblock listener must not certify cleanup;
  a reentrant callback still returning must not appear ready either.
- Pass 471 controller and 34 actual-target callback contract assertions using
  deterministic framework doubles (`.mdo/E32-chooser-contract.log`). Existing
  dialog 95+68, popup 78+25 and document 85/native-budget 18 gates pass. These
  are Java contract checks, not installed-framework verification. All 275 JS
  checks and 144/146 Python checks pass (two symlink-privilege skips).
- Compile the actual pinned Activity and reviewed hooks against Android API 36,
  Java 8/min API 26, reusing the one SDK/build directory and signer. The tiny
  QA APK is 237,971 bytes, SHA-256
  `583d92abd26845ae06e5271f3906e02a8d81e70fbea9c15de3b8760a68c495df`.
  Evidence: `.mdo/E32-android-build.log`, `.mdo/E32-python-full.log`,
  `.mdo/E32-js-full.log`. The same headless service is healthy. This APK is not
  a complete mdo client. Device/frame/URI-transfer qualification stays false,
  overall Android automation stays unavailable, and the current provider is
  preserved as requested. The Windows pair is unaffected by these Java changes.

