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

- **Windows W6:** add bounded repeated cross-process Unicode fill and
  navigation-interrupted drag/recovery cycles to the actual WebView2 regression.
  Six cycles of each pass with 301 total assertions, exactly one drag and one
  later explicit recovery click per interrupted cycle, clear final native input
  fences and zero upload reservations/new native diagnostics. No product code or
  artifact changed. This strengthens current-runtime evidence without attributing
  or claiming permanent resolution of the historical intermittent failures.
- **Android E110:** reclaim deferred request associations when their download
  records are released. Reproduce the old false limit on the 33rd sequential
  cancelled/released record; cancel/release and complete/release now each pass
  40 cycles with zero file/record reservations. Retained cancelled records still
  receive late metadata, and the live association limit remains enforced.
  Broker/request gates pass 1,247/176 assertions; full native gates, 173/175 Python
  cases (two existing privilege skips) and the refreshed actual APK pass.
  Installed-provider/main-UI and broader Browser Use qualification remain open.
- **Android E109:** pin a native goto download to its original captured document,
  rather than a replacement document present when the response arrives. Retain
  ambiguous-source failures without claiming a body or poisoning later actions.
  Seven scope/order/recovery cases bring the broker gate to 915 assertions; the
  real socket/JSON/JNI pipeline also checks its epoch before and after ACK.
  Full native gates, 173/175 Python cases (two existing privilege skips) and the
  rebuilt actual APK pass. Other response-frame snapshots are explicitly weaker
  provenance. Installed-provider/main-UI and remaining goal work stay open.
- **Android E108:** prove an unchanged-location goto without inventing a missing
  navigation event. Require the original known native URL digest/position, a
  typed non-download/no-loader ACK and one fresh matching native frame snapshot.
  Preserve DOM readiness and all original read guards. Twenty-six ordering and
  rejection scenarios bring the broker gate to 817 assertions; complete native
  gates and 173/175 Python cases pass (two existing privilege skips). The full
  Android development APK is rebuilt. Installed/provider/main-UI qualification
  and whole-goal work remain open.
- **Android E107:** remove last-navigation-only download attribution. Retain 32
  original goto candidates and freeze each response's candidate set. Older
  responses can keep their original identity after a newer navigation; future
  commands cannot acquire that response. Request/broker checks pass 132/622,
  native sanitizer/JVM gates pass, Python passes 173/175 (two existing privilege
  skips), and the refreshed real APK passes its DEX/signature/payload checks.
  Unchanged E106 JS/Windows and product evidence is explicitly reused. Installed
  Android/provider/main-UI qualification remains deferred.
- **Android E106:** join original typed goto ACK/loader, root Document request
  identity and the frozen retained response using opaque references. Handle
  metadata after body completion without refetching or borrowing a concurrent
  download. Keep the pre-ACK permission boundary explicit. All 372 JS cases,
  173/175 Python cases (two existing privilege skips), native sanitizer/JVM gates,
  647 product assertions, 12 packed Windows checks and actual APK checks pass.
  Both development programs are refreshed. Installed Android/provider/main-UI
  qualification stays deferred; this is not full-goal completion.
- **Android E99:** privately correlate an original navigation loader with its
  validated root-frame commit, retaining a bounded 32-event history and opaque
  document identity. Reply/event reordering, other owners/frames, replacement,
  document.open and connection loss are covered. Product/UI/JS use the commit
  status separately from ACK/load completion. Final lifecycle gate passes 397,
  JS 344, native broker 86 and four real JNI/socket navigation sessions; refreshed
  programs pass package/identity checks. The separate observation connection still
  needs a direct original-loader guard; Android/provider qualification stays false.
- **Android E98:** managed initial targets now use the same original native
  navigation coordinator as goto. Keep the original startup budget across native
  warmup/activation, distinguish creation admission from page/file completion,
  and cancel only the exact queued target after display loss. Missing loader
  metadata cannot turn the native warmup into a completed initial target.
  Final product lifecycle gate passes 383; all 343 JS tests, native gates and
  refreshed Windows/Android artifact identities pass. Windows has 12 actual
  packed runtime checks; installed Android/provider qualification stays deferred.
- **Android E97:** connect managed product `goto` to an asynchronous original
  native navigation receipt, with exact request cancellation and same-document/
  download/unknown-outcome handling in the shared JS dispatcher. Preserve the
  original deadline and navigation fence without replaying WebView.loadUrl or
  letting old callbacks mutate a new page. Native broker/file gates, 335 product
  lifecycle assertions and all 338 JS tests pass. Both full development programs
  are refreshed; Windows passes 12 actual runtime checks, while Android remains
  compiled-only until the deferred installed-provider/main-UI regression.
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
- **Android E32–E43:** implement native staging, restart recovery, sealed read
  leases and a private seekable URI backend, plus human document picking.
  Share URI validation between host/content WebViews; route ordinary host read
  picks through the original callback controller. Fix loss of cancellation/
  ownership during metadata checks or unblocking. The actual Activity/hooks
  compile; 554 picker/ingress, 513 chooser and 418 URI/provider checks pass with
  Android API doubles. Device/host-UI/automatic assignment and full automation
  remain unqualified/disabled; retain the current WebView. See E43 for evidence.
- **Android E44–E46:** prepare the bounded pinned compiler in the existing cache,
  build/verify the actual ARM64 engine and package the actual mdo Activity/service
  as a separate development APK (complete host, lite tool edition). Signatures,
  exact app/native payloads, DEX classes, ARM64/16 KiB alignment and compiled
  private-provider attributes pass. Python passes 158/160 with two existing
  symlink skips. The user deferred the currently unavailable phone; retain its
  WebView and all framework/device/automation qualification gaps.

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

## E33 — installed-provider chooser framework fixture

- Add `native-chooser` to the existing tiny QA client and nonce-bound runner.
  Use the actual production Chrome target and native touch sequencer on one
  fixed synthetic HTML file input. Page-world JavaScript only prepares/reads
  fixture data; it cannot enable a production Agent execution fallback.
- The fixture requires three distinct original native chooser callbacks:
  single-file explicit cancellation, multiple-file disarm cancellation and
  navigation cancellation. It verifies opaque identities/epochs, stale-ID
  refusal, trusted page clicks, empty selections, balanced blockers and exact
  teardown. It never assigns a URI, reads a user's files or launches a system
  picker. Duplicate page-read replies cannot admit an additional gesture.
- Centralize the runner's unavailable/running/install-pending false flags, and
  keep chooser framework, host routing, assignment, source-frame identity and
  overall automation distinct. Source/APK receipts are checked before any
  installation or device work. The existing signed dev package is the only
  possible installation target; preserve the current provider and other apps.
- The actual SDK Activity/hooks and new fixture compile. Reused QA APK:
  250,259 bytes, SHA-256
  `ab061d51d6b53a43893578e7e5c396a4447454783e00786de35ff0cefadf812f`.
  Evidence: `.mdo/E33-android-build.log`. Python compile checks and reviewed
  overlay checks pass. `--mode native-chooser` currently exits unavailable
  because ADB has no authorized device; `.mdo/E33-device-chooser.log` and
  `.mdo/android-device-chooser-result.json` retain all qualification flags false.
  This is fixture implementation and compile evidence, not a framework pass or
  Android automation completion. Windows product code/artifacts are unchanged.

## N3 — clear a failed renderer only after confirmed new navigation

- Reproduce a real native defect: stop exactly one newly created renderer of
  the owned QA host, observe `process_failed`, manually reload the original
  synthetic page, and confirm the page works while the failure flag remains
  true. That stale flag permanently blocks subsequent isolated automation.
  Evidence: `.mdo/N3-renderer-before.log`; this does not identify the cause of
  any earlier user screenshot or of the vendor renderer exit itself.
- Add an allocation-free native recovery state. Record the failure's navigation
  identity; only a later started navigation and its matching successful native
  completion clear renderer failure. Stale, failed, duplicate and pre-failure
  completions cannot recover it. A browser-process exit or unavailable failure
  classification still requires closing/recreating the view. Correct the native
  recovery message accordingly. No navigation or previous operation is replayed.
- The actual same-Home renderer gate now passes all five checks, including
  restored isolated evaluation after explicit manual reload. Process selection
  requires exactly one new owned renderer; creation time, parent and ancestry
  are rechecked immediately before stopping it. No user browser process, model,
  external site or account data is touched. Desktop pixels remain unverified.
  Evidence: `.mdo/N3-renderer-after.log`.
- Both native navigation contract tests pass, including the recovery state;
  all 145/147 Python checks pass with two symlink-privilege skips. The complete
  real WebView2 gate passes 1,369 assertions, with no new native diagnostics and
  private upload/download caches returning to zero reservations. Evidence:
  `.mdo/N3-navigation-contract.log`, `.mdo/N3-python-full.log`,
  `.mdo/N3-native-full.log`. Reviewed app overlays match; the same headless
  service is healthy and the deployment remains about 1,130 MiB with one native
  object directory. The Windows artifact must be refreshed before this fix is
  claimed in its deliverable. Android's current provider is unchanged.

## N4 — refresh the Windows preview with confirmed renderer recovery

- Refresh the existing self-contained Windows development program with N3:
  6,880,852 bytes, SHA-256
  `cb8d7a111b53e587b863b6436ab9bc732a90a91356071e9565c1ae7e6cd13a4f`.
  Verify the native inventory and matching current host. The paired 22-file
  plugin is unchanged at 246,883 bytes, SHA-256
  `ad8c4826ed3ed03c0cfc44ddce696ab0bb88b87d9628020e427bc094dd305912`.
- All ten packed runtime checks pass, including paired source/installed/host
  runtime identity, actual C/JS observation, native Unicode input/trusted click,
  capture, page-network error/recovery and close. Both `runtime_verified` and
  `paired_plugin_verified` are true. Logs: `.mdo/N4-program-package.log`,
  `.mdo/N4-program-runtime.log`. N3 separately verifies the actual renderer
  stop/manual-reload case in this deployment; these ten packed smoke checks do
  not claim to repeat every failure injection.
- Restore the same healthy headless service and one Home, about 1,130 MiB, with
  one native object directory and no extra application copy. Reviewed overlays
  match. The current 250,259-byte Android QA APK still matches its exact build
  sources/hash, and chooser framework qualification remains false. No WebView
  change, real model call or online release is performed.
- A fresh attempt to initialize the documented desktop Computer Use tool still
  fails with `failed to write kernel assets` / OS error 3. Normal-desktop pixel
  checks remain unverified; native browser images are not substituted for that
  evidence. Android frame/transfer integration and device qualification, actual
  authenticated physical-device transport and remaining lifetime/production
  checks stay in the active full goal.

## E34 — directory-relative Android upload byte staging primitives

- Add worker-confined Java staging over the platform `SecureDirectoryStream`.
  The native caller must supply an app-private cache root; no task-host or wire
  path is accepted. Pin the directory identity, create opaque exclusive leaves
  without following links and refuse further admission/I/O when the root's path
  identity changes. Preserve a replaced leaf and retain its resource reservation
  until actual cleanup is confirmed. Unknown directory contents stay untouched.
- Bound each file to 32 MiB, each store to 128 MiB/16 files and each chunk/range
  to 64 KiB. Require exact sequential offsets and complete SHA-256 verification;
  seal into a read-only channel and provide full pre-grant revalidation. A lost
  write/seal outcome poisons the leaf rather than permitting replay. This is a
  file primitive, not the yet-to-be-integrated ticket/URI/chooser transaction.
- Pass 63 real filesystem assertions in `.mdo/E34-upload-files.log`: bounded
  streaming, zero-byte files, source mismatch and same-size mutation, byte/file
  limits, replaced files/links, changed cache identity, owner thread and cleanup
  accounting. The WSL/NTFS provider forbids renaming a directory with an open
  child; `open_directory_rename_verified` remains false. Android framework and
  private native-cache authority remain unverified. Do not infer either from
  Java compilation or these filesystem checks.
- Preserve earlier fixture failures in `.mdo/E34-upload-files-before.log` and
  `.mdo/E34-upload-files-drvfs.log`. The initial root-rename case exposed an
  `ENXIO` from directory-relative WSL/NTFS I/O; cleanup conservatively retained
  its reservation. Add the root-identity admission fence, preserve primary
  exceptions through fixture cleanup, and use atomic rename for the controlled
  fixture's restoration. This is not evidence about Android's actual provider.
- Reset verification receipts before preflight, preserve unknown/interrupted QA
  outputs, and write explicit failed receipts. An injected existing-fixture
  guard confirms a previous pass cannot survive the failure. The attempted
  physical-directory removal command was rejected before execution; the final
  negative gate changes no fixture directory (`.mdo/E34-upload-interrupted.log`).
- Copy the reviewed primitive into the Android host build. The actual pinned
  Activity/hooks and tiny QA APK compile against API 36/Java 8/min API 26 without
  NDK or runtime dependencies. The APK is 262,547 bytes, SHA-256
  `ca4194bf99c954af593a2d2c1f885eb143b69e0107dbaf102ae2e6bc6efb9e1e`.
  Evidence: `.mdo/E34-android-build.log`. Runtime worker/ticket integration,
  source/chooser binding, provider URI handoff and device qualification still
  need implementation/verification. Every Android automation/assignment gate
  stays disabled; preserve the current WebView. The Windows N4 pair is unchanged.

## E35 — original-scope Android upload staging transactions

- Connect strict native upload begin/chunk/seal/status/cancel/release commands
  to a single worker and real directory-relative storage. Only the native app
  Context/UID can select the private cache; wire paths and assignment targets
  are rejected. Own the cache through a locked marker. Preserve invalid or
  unrecognized entries and report orphan recovery as required, not complete.
- Bound batches to 64 MiB/16 files, operation identities to 64, queued work to
  eight and unfinished UI deliveries to nine. Use immutable original deadlines
  of at most 120s, metadata-checked begin deduplication and identical last-chunk
  receipts without rewriting. Retain terminal identities until their original
  native scope is cancelled. Failed I/O keeps cleanup ownership and reservations
  until actual release, including file creation that failed after touching disk.
- Bind each ticket to the exact WebView/navigation cancellation object and
  every response to its original native channel lease. Navigation, takeover,
  hide, detach, close and destruction invalidate the scope. Stale views or
  reused tab/channel IDs cannot receive a successful response. Worker expiry
  cleanup does not depend on UI progress. Replies expose metadata only and
  explicitly distinguish local staging from webpage assignment/effects.
- Pass 63 primitive filesystem checks and 89 real worker/filesystem checks;
  also pass 37 checks through the actual production command fragment with
  UI/JSON/runtime-factory doubles. Logs: `.mdo/E35-upload-files.log`,
  `.mdo/E35-upload-queue.log`, `.mdo/E35-upload-ui.log`. These do not qualify
  Android framework behavior, private native-cache authority or URI assignment.
- All 275 JS checks pass. All 145/147 Python checks pass with two existing
  symlink-privilege skips. Evidence: `.mdo/E35-js-full.log` and
  `.mdo/E35-python-full.log`; reviewed deployed overlays match the source.
- Actual pinned Activity/hooks and tiny QA APK compile; source receipt matches
  current reviewed inputs. APK: 283,027 bytes, SHA-256
  `34962643858912852ee6479e25adf076e42aeef0a5363304338dd51605a405b4`.
  Evidence: `.mdo/E35-android-build.log`. The same headless service is healthy.
  No extra app/Home/native object directory was created. Windows N4 deliverables
  remain unchanged; all Android model/assignment gates stay disabled.
- Still outstanding: durable orphan recovery, URI/provider handoff and verified
  source/chooser/frame binding, device qualification, real physical-device
  transport and desktop pixel qualification. Retain the installed WebView and
  keep the full goal active; this is a tested staging milestone, not completion.

## E36 — installed-runtime Android staging fixture

- Add `native-upload` to the existing tiny QA client/nonce-bound runner. Reuse
  the production native app-Context/UID cache factory, one worker and actual
  file store. Use synthetic data only; no task files, system picker, URI grants,
  webpage assignment or provider upgrade. Background/Activity loss cancels the
  original scopes. Successful qualification is limited to cache/worker staging.
- Exercise full chunk/tail and zero-byte files, Unicode metadata, original
  operation/chunk deduplication, conflicts/foreign scopes, hash verification,
  explicit release/terminal identities and autonomous cancellation/expiry.
  Require zero final file/byte/UI-delivery reservations. Preserve stable failure
  phase/cause codes without returning platform messages or private paths.
- Execute that exact device fixture through real files/worker with UI/Context
  doubles: 42 flow checks and two negative factory checks pass. Unsupported
  secure-directory support and generic I/O errors cannot become a pass or leak
  their platform text. Evidence: `.mdo/E36-upload-fixture.log`. Framework/native
  Context authority stay unverified; these are deliberate doubles.
- Actual pinned Activity/hooks and fixture compile with the existing API 36
  tools, Java 8/min API 26, no NDK/runtime dependency or extra app/Home checkout.
  Reused QA APK: 299,411 bytes, SHA-256
  `261c5cbbe54292a2a0f53f35230613ff6e1344781e66b477b508eef3712c8ca8`.
  Current source/APK hashes match. Evidence: `.mdo/E36-android-build.log`.
- The real `--mode native-upload` gate is unavailable: ADB has no authorized
  device. `.mdo/E36-device-upload.log` and
  `.mdo/android-device-upload-result.json` keep all qualification flags false.
  No install or WebView change was performed. Host upload routing, source/frame
  identity, URI handoff and overall Android automation remain separate, false
  qualifications. The Windows N4 pair is unaffected and the full goal stays active.

## E37 — reject I/O after the cache owner marker changes

- Reproduce a real file-store defect before changing production code. Move the
  original locked marker, create a replacement marker and let a second store
  acquire that marker's lock. The old store incorrectly creates a new file:
  `.mdo/E37-owner-before.log` fails the required owner-change refusal. This is
  an owned synthetic filesystem case, not evidence of a past user crash.
- Retain the original marker's file identity with the original lease. Before
  create/write/seal/verify/read, require that exact regular marker, open lease
  channel and valid original lock. A moved/replaced/missing/linked marker is
  refused as `upload_cache_owner_changed`. Exact owned-leaf cleanup remains
  possible through the original pinned directory, without regaining admission.
- All 99 worker/filesystem checks pass, including simultaneous replacement
  owner, removal during receive/sealed states and final cleanup. Evidence:
  `.mdo/E37-owner-after-final.log`. Preserve the initial post-fix fixture failure
  in `.mdo/E37-owner-after.log`: renamed-marker metadata was checked while its
  old WSL/NTFS handle was open; final preservation is checked after closing it.
- All 63 primitive checks, 37 production-native-fragment checks and 44 exact
  device-fixture checks with UI/Context doubles pass. Actual pinned Activity/
  hooks compile. Logs: `.mdo/E37-upload-files.log`, `.mdo/E37-upload-ui.log`,
  `.mdo/E37-upload-fixture.log`, `.mdo/E37-android-build.log`. Reused QA APK:
  299,411 bytes, SHA-256
  `65a9e6489bbc63ca9b9926a63f9e43408fcc265e893b2f21432ddf022b902203`.
- Device authority/URI/frame qualification and full Android automation remain
  false. Preserve the current WebView. Durable orphan recovery is still pending;
  this is its ownership prerequisite, not a completed recovery implementation.
  The Windows pair is unaffected and the full goal remains active.

## E38 — recover fully recorded ungranted upload staging

- Add a fixed 1 KiB ownership record to each new cache-managed leaf and force
  it before admitting payload bytes. Bind the exact original cache/leaf file
  identities, opaque name, expected size/hash and metadata checksum. Keep the
  payload API independent of that private prefix. Verify the original record
  during seal/pre-grant checks. There is at most 16 KiB record overhead beyond
  the 128 MiB payload capacity. No runtime dependency or secret is introduced.
- Under the exclusive owner lease, validate the entire bounded inventory before
  deleting anything, then revalidate owner/leaf identity, record and length for
  each directory-relative unlink. Reclaim fully recorded ungranted partial,
  sealed and empty files after process exit. Continue a partially completed
  cleanup without restoring tickets or replaying page effects. The exact worker
  startup flow cleans old files and can accept a fresh independent ticket.
- Preserve unknown/legacy/partial/corrupted records, replacement files, symlinks,
  moved names and mismatched directories. Unproved entries still require explicit
  recovery; source bytes are not accepted until the record is complete. The
  checksum detects damage, not malicious same-UID mutation. Native app-private
  authority remains the boundary; only the reviewed Unix file-key encoding is
  supported. References/limits are in `host/android/upload-protocol.json`.
- Expose only recovered file/payload-byte counts in native state. Count actual
  staged payload removed, excluding header overhead and unwritten capacity.
  Future URI publication must establish retained ownership and use payload
  ranges, never raw metadata-prefixed cache paths. URI assignment remains off.
- Pass 154 real filesystem/process/worker checks with 12 actual JVM `halt`
  exits and a live child proving cache-lock exclusion. Cover recorded partial/
  complete/empty files, interrupted cleanup, worker startup/new ticket, full
  capacity, record corruption, checksum-valid malformed fields and all stated
  preservation boundaries. Evidence: `.mdo/E38-recovery-final.log`. Earlier
  incremental evidence remains in `.mdo/E38-recovery.log`. This does not qualify
  an Android process, framework, Context authority or device.
- All 63 primitive, 99 queue, 37 native-fragment and 44 device-fixture-double
  checks pass. Actual pinned Activity/hooks compile. Tiny QA APK: 307,603 bytes,
  SHA-256 `8b53472aeeb6af85077a5fc5d6b72d89a82497adbf913bca4e2a23f178f7849f`.
  Logs: `.mdo/E38-upload-files.log`, `.mdo/E38-upload-queue.log`,
  `.mdo/E38-upload-ui.log`, `.mdo/E38-upload-fixture.log`,
  `.mdo/E38-android-build.log`. Keep the current WebView and false Android
  assignment/automation gates. Windows deliverables are unaffected. Full device,
  source/frame/chooser/provider and broader delivery qualifications remain in
  the active goal; this is the ungranted staging recovery milestone only.

## E39 — native-only sealed read leases and bounded background replies

- Prepare a non-serializable read lease only after full sealed record/payload
  revalidation. Bind original store-object identity, cancellation scope,
  ticket/file and deadline. Reuse the exact live object without extending it;
  refuse a revoked object. Leases own no View/Activity/thread/file handle.
  Revoke before exact-file cleanup, including cleanup failure. The public JSON
  bridge still has no `access/read` route; add explicit refusal checks.
- Route payload ranges through the same owning worker. Bound each request to
  64 KiB/five seconds and the shared inbox to eight tasks. Count four unfinished
  native reads independently of UI reply slots. Timeout/interruption drops late
  data and retains capacity until actual worker retirement. Reject worker self-
  wait and bind the actual Android runtime's main-Looper blocking guard. Keep
  body replies outside UI/JSON delivery; stalled UI replies cannot leak data or
  grow a separate unbounded read inbox.
- Ensure an unexpected worker unwind still retires its active read waiter in
  `finally`. Whole-queue close, scope loss/expiry, explicit revocation and file
  replacement cannot return late bytes. Preserve a foreign replacement and its
  reservation until exact owned cleanup is possible. A full pre-access hash
  check refuses same-size mutation before any read object is issued.
- Pass 318 real file/worker checks, including full ranges/EOF/zero-byte files,
  cross-store authority, unsealed data, range/wait bounds, blocked wait contexts,
  timed-out outcome quotas, full shared inbox, nine stalled UI receipts,
  cancellation/expiry, interruption, close, cleanup retry and injected worker
  Error. Evidence: `.mdo/E39-native-read-final.log`. UI scheduling is doubled;
  this is not Android UI/FUSE/provider qualification.
- All 99 queue, 154 recovery, 39 production-native-fragment and 44 device-fixture
  checks pass. Logs: `.mdo/E39-upload-queue.log`, `.mdo/E39-recovery.log`,
  `.mdo/E39-upload-ui.log`, `.mdo/E39-upload-fixture.log`. Actual pinned Activity/
  hooks compile; tiny QA APK is 311,699 bytes, SHA-256
  `96d53b15ce5afe775fd16d0193a883f162efb8a120fc202918076b5318348385`.
  Evidence: `.mdo/E39-android-build.log`. Keep the current WebView and disabled
  assignment/automation gates; Windows product files are unaffected.
- Still implement and qualify the native seekable URI/provider adapter, original
  chooser/source/frame binding and complete device path. No raw cache path or
  URI is exposed by this milestone. The full goal remains active, with the
  previous device/desktop/physical-transport verification gaps intact.

## E40 — native private ContentProvider and seekable read-only proxy

- Implement original-object URI permissions and original FD callback sessions
  over E39's worker reads. Keep URI tokens memory-only; accept only the original
  queue's live lease and exact canonical package-bound URI. No cache path,
  metadata prefix or raw cached descriptor is handed to a caller. File lease
  permission survives closing one descriptor, until its original scope or
  deadline ends. URI publication remains native-only, with no public JSON route.
- Register the private provider in the actual mdo packager and tiny QA manifest.
  Validate package authority/export/grant flags at attach, then enforce same UID
  on all public entries. Expose bounded name/size/MIME metadata and exact `r`
  opens; refuse writes, mutation methods and URI aliases. Use the app's default
  process for its non-serializable queue/registry authority.
- Bound 16 file grants, 64 descriptor sessions and one lazy callback thread.
  Assemble at most 1 MiB requests from exact 64 KiB worker reads under one
  callback deadline. Zero failed/revoked partial replies. Recover an allocation
  failure on the original callback thread; do not replace live callback owners.
- Retire reservations before native entry only when no native effect occurred.
  Failed/null/uncertain opens or known-FD close failures stay charged until the
  original release callback. Refuse more opens while uncertain. Preserve the
  descriptor through cancellation-listener detachment/handoff failure, and
  reject duplicate/late callbacks from retiring a new reused slot.
- Execute 418 checks through the actual URI registry/provider/runtime/proxy with
  real files and worker. Android Context/Binder/FD/FUSE/cursor/Looper calls are
  doubled. Cover stated privacy, byte/range, lifetime, cancellation, capacity,
  allocation and original-callback rules. Evidence:
  `.mdo/E40-uri-contract-final.log`. No Android kernel/framework/device or actual
  chooser assignment qualification is implied.
- All 318 native-read, 154 restart/recovery, 99 queue, 39 native-fragment and 44
  device-fixture-double checks pass. Python: 145/147 pass, two existing symlink
  skips. Logs: `.mdo/E40-native-read.log`, `.mdo/E40-recovery.log`,
  `.mdo/E40-upload-queue.log`, `.mdo/E40-upload-ui.log`,
  `.mdo/E40-upload-fixture.log`, `.mdo/E40-python-full.log`.
- Actual pinned Activity/hooks/provider compile; render/check the real mdo
  packager manifest and package the QA declaration. Existing API 26/36 tools
  need no NDK/runtime dependency. Tiny QA APK: 323,987 bytes, SHA-256
  `f79cd265db0f6ae545404487602f9c66f0fec6f4789a277144306487cee8ac5e`.
  Evidence: `.mdo/E40-android-build.log`. This is not a full Android mdo build.
  Keep the current WebView and false device/frame/assignment/automation flags.
- Still implement/execute the real ContentResolver/seek/revocation device gate,
  original chooser/source-frame handoff and complete integration. Windows files
  are unaffected; previous desktop/physical-transport gaps and the full active
  goal remain. No source-user file, external site or real model was used.

## E41 — installed private-provider URI test entry and outcome/teardown gates

- Add `native-upload-uri` to the existing tiny Android QA client. This mode
  does not construct a WebView or require isolated worlds/provider upgrades.
  Use the actual app Context/UID cache factory, staging queue, native read
  leases, private URI runtime and ContentResolver, with two fixed synthetic
  files (150,001 bytes and empty). No chooser, user/source file, external page,
  model or raw cache descriptor is used.
- On one owned background consumer, query Unicode name/size/MIME, read the full
  payload/EOF, seek across a chunk boundary and near EOF, close/reopen the same
  still-live file, read the empty file, and reject writes/noncanonical aliases.
  Revoke the original scope, then reject metadata/new opens and reads on a fresh
  unread descriptor. This deliberately makes no claim about retracting bytes
  already delivered or cached by a consumer. Require zero original descriptor
  sessions/grants, staged files/bytes and pending UI receipts before success.
- Activity pause/destroy/timeout cancels the original scope and in-flight open,
  and closes only the exact owned stream. No UI join or blocking file read is
  introduced. Late consumer completions cannot finish a closed fixture. Do not
  close/replace the process-owned proxy callback thread or registry.
- Execute the exact fixture in six separate original-static JVM runs, with real
  staging/file/worker/provider/proxy code and Android Resolver/FD/UID/Looper
  doubles: 168 checks, including 49 successful-path fixture checks, corrupted
  bytes, absent original release acknowledgment, close-before-start, close
  during read and cancellation during Resolver open. Require consumer/owned-FD
  retirement; unacknowledged native sessions stay counted until the original
  callback. Evidence: `.mdo/E41-uri-fixture.log`. These are not Android/FUSE or
  cache-authority qualifications.
- The actual SDK compiles the pinned Activity/hooks/provider and packages the
  new fixture. Tiny QA APK: 340,371 bytes, SHA-256
  `964579f4eca1d42dac9eb16320d86503546ee7f68656b607b694fe5b368a8d57`.
  Evidence: `.mdo/E41-android-build.log`. This is not a full Android mdo build.
  Python: 145/147 pass, two existing symlink-privilege skips
  (`.mdo/E41-python-full.log`). Windows executable/plugin are unchanged.
- Attempt the real device entry once; ADB has no authorized device. Persist
  `android_device_unavailable` with all framework/assignment/automation flags
  false (`.mdo/E41-device-uri.log`,
  `.mdo/android-device-upload-uri-result.json`). Keep the installed WebView.
  Real provider/seek/revocation, original chooser/source/frame handoff and full
  product Android integration remain unverified; retain the previous desktop
  and physical-transport gaps. The full goal remains active.

## E42 — repair the manual browser file-picker downgrade

- Confirm the child WebView had its own Chrome client, but returned `false`
  when chooser interception was off. Android's documented default cancels file
  requests, so it did not delegate to the Activity's main WebView file picker.
  Add a human `ACTION_OPEN_DOCUMENT` path to the child target. Preserve the
  existing intercepted chooser list/cancel path and disabled automatic URI
  assignment/source-frame/global-automation gates.
- Use one Activity-owned native picker, its exact original WebView/navigation/
  channel, once-only callback identity, and a two-minute uptime deadline.
  Reuse no request codes within the process (range `0x6000..0x7fff`); restore
  only the monotonic counter. Route only this namespace through the actual
  Activity result hook, before the original request-1/request-2 handlers.
  A result without an original pending owner is consumed without delivery.
- Navigation/hide/stop/close/takeover/channel loss/destroy cancel the original
  callback. Pause for the external system picker can return before `onResume`
  without losing that callback. An invalidated/expired/uncertain native launch
  keeps its one original result slot until acknowledgment; refuse replacement
  launches/interception and expose the waiting state. Do not reuse a late old
  result for another request. Callback/unblock uncertainty requires teardown.
- Accept only read-permitted content URIs from external-UID providers. Refuse
  private app-provider URIs (including E40), raw file/data URIs, malformed
  authorities, overlong URIs and oversized/unexpected selection lists. Bound
  selected files to 16, MIME entries to 16 and each URI to 8,192 characters.
  Return no URI/path in browser state or Agent/tool JSON. Folder/save/camera
  automation is not introduced by this human document picker.
- Pass 106 actual-controller checks with Activity/Intent/permission/Looper
  doubles, including normal/multiple selection, private URI/read-permission
  denials, stale/duplicate/cancelled native results, deadlines, quota admission,
  launch/scheduler/callback faults, synchronous completion before a platform
  exception, background return and restored counters. The native chooser has
  471 controller plus 42 target-callback checks, including manual callback
  identity and intercepted-path regression. Evidence:
  `.mdo/E42-manual-chooser.log`, `.mdo/E42-chooser.log`. Framework qualification
  remains false; none of these doubles selects a user's document.
- All 418 URI/provider, 168 URI-device-fixture and 39 upload-native-fragment
  checks pass. Python: 145/147 pass with two existing symlink-privilege skips.
  Evidence: `.mdo/E42-uri-contract.log`, `.mdo/E42-uri-fixture.log`,
  `.mdo/E42-upload-ui.log`, `.mdo/E42-python-full.log`. Actual pinned Activity/
  hooks and manual chooser compile with the existing SDK. Tiny QA APK:
  348,563 bytes, SHA-256
  `ddc1e1b94f1a646406c15cf86598a9f11b1c9ab36561d30863f87117b262eeba`;
  `.mdo/E42-android-build.log`. This is not a full Android mdo release.
- Retain the current installed WebView and false manual-framework/provider/
  chooser-assignment/automation flags. ADB remains unavailable; no device
  result is upgraded to a pass. Keep the single deployment healthy and the
  Windows N4 executable/plugin pair unchanged. The complete Android handoff,
  real-device/desktop/physical-transport verification and full goal remain open.

## E43 — close both human-picker URI ingress paths and preserve handoff ownership

- Compile/run the exact pinned native `FileChooserResult` parser and confirm
  its scheme-only filter accepts the synthetic same-UID provider URI. Evidence:
  `.mdo/E43-picker-baseline.log`. Its old assumption that the shell has no
  private providers no longer applies after E40. This is a parser-boundary
  reproduction, not evidence that an external app knew a live token or read
  private payload bytes.
- Extract `MdoBrowserPickerUris` and use it for both host and child human picker
  results. Refuse same-UID/private-provider URIs and require an explicit URI
  read grant for our actual PID/UID. Do not substitute the app's broad provider
  permissions for a document selection. Bound URI/authority lengths and raw
  selection counts; reject mixed invalid/private selections as a whole, without
  falling back to another Intent field. Provider/permission lookup failures or
  metadata OOM cancel the result. No descriptor, URI permission, path or body is
  opened/persisted/logged by this filter. Cross-user authorities remain unsupported.
- Route standard host read/open-multiple requests through the same original
  Activity picker controller and namespace as child human picks. Bind the host
  callback to the exact native host and channel. Keep nonstandard host modes
  on the pinned native route, under the shared URI result filter. Do not change
  its main export/save handler. This does not implement/qualify folder/save or
  camera automation, nor automatically assign a staged private URI.
- Track at most 128 still-live callback identities in that original controller,
  using weak references and exact identity. Duplicate callbacks cannot launch
  again or receive another result. Identity-bookkeeping allocation failure
  preserves the last claimed callback and refuses further unknown identities;
  quota exhaustion requires teardown rather than forgetting a live callback.
- Reproduce result delivery after ownership loss during URI permission lookup
  (`.mdo/E43-picker-handoff-before.log`, failed check 520). Keep the original
  request until metadata processing finishes; allow cancellation/close to find
  it and suppress duplicate/reentrant results. Recheck original ownership after
  metadata and immediately after unblocking before delivering non-null URIs.
  Loss of ownership yields one null reply without making a normal cancellation
  a permanent controller failure.
- Pass 554 actual-controller/URI-policy checks, including the pinned-parser
  comparison, private-provider aliases, missing explicit grants, lookup/OOM
  failures, bounded/mixed selections, live callback quota, duplicate identity,
  ownership loss/cancel/close during metadata, ownership loss during unblock,
  and reentrant result isolation. Android Activity/UID/permission/Looper calls
  are doubles; this is not main-UI or device qualification. Evidence:
  `.mdo/E43-picker-policy-final.log`. All 513 chooser, 418 URI/provider, 168 exact
  device-URI-fixture-with-doubles and 39 native-upload-fragment checks pass;
  `.mdo/E43-chooser.log`, `.mdo/E43-uri-contract.log`, `.mdo/E43-uri-fixture.log`,
  `.mdo/E43-upload-ui.log`. Python: 145/147 pass, two existing symlink-privilege
  skips (`.mdo/E43-python-full.log`).
- Compile the actual pinned Activity with host/child hooks and shared policy.
  Current tiny QA APK: 348,563 bytes, SHA-256
  `feeaaf4a1b6936cae6868d3ee9e8696764f80f5a35416bd95529016a5e90b14a`;
  `.mdo/E43-android-build-final.log`. Actual Activity classes are a compile gate,
  not a full mdo APK or installed host-UI pass. Main-picker, manual-framework,
  automatic-assignment and global-automation qualification fields stay false.
- Recheck the normal-desktop route through the prescribed `@oai/sky` entry;
  initialization still fails with `failed to write kernel assets` / OS error 3.
  No app/window interaction occurs. Keep normal-desktop pixels unverified;
  do not replace that evidence with native content captures or UIA scripts.
  ADB still has no device. Keep the single deployment healthy, preserve current
  WebView/user data and the N4 Windows pair, and leave the full goal active with
  the previous Android/physical-transport/visual/lifetime verification gaps.

## E44 — prepare a bounded native Android compiler in the existing deployment

- Add optional `tools/setup_android_client.py --native`, using the pinned SDK
  lock and existing WSL JDK. Keep default setup limited to API/build-tools;
  preserve an already installed native compiler on subsequent default runs.
  No browser, additional source/Home tree or application runtime dependency is
  installed. Preserve the current phone WebView as requested.
- Bound each download by its locked size, verify its SHA-1 and record SHA-256,
  preflight ZIP entry/expanded-byte budgets, and publish without overwriting an
  existing cache entry. Interrupted/foreign partials and replacements are
  preserved. Reuse the pinned installer's path/symlink extraction checks.
- Install NDK 30.0.16248370 from the 738,633,529-byte locked Linux archive.
  Tooling occupies 2,486.1 MiB including the existing API/build-tools. Verify
  compiler identity/version and hashes in `.mdo/android-client-tools/native-receipt.json`.
  Both default and native setup reruns finish with zero download bytes and
  retain `ndk_installed: true` (`.mdo/E44-tools-reuse.log`).
- Reproduce Windows status failure on an NDK Linux symlink exposed as a regular
  reparse file. Account for regular files without following links, junctions or
  reparse files. The same deployment returns HTTP 200 again and its status
  reports 3,371.0 MiB (`.mdo/E44-status-final.log`).
- Pass eight archive/accounting tests on Windows and WSL, including bounded
  failures, preserved foreign entries/replacements, cache reuse and Windows
  reparse-point traversal (`.mdo/E44-tooling-tests-final.log`,
  `.mdo/E44-tooling-tests-wsl-final.log`). These are build-tool checks, not a full
  Android host build or device qualification. Build the actual ARM64 host and
  separate development APK next; keep all previous qualification gaps open.

## E45 — compile and verify the actual reusable Android ARM64 host

- Add `tools/build_android_native.py`, reusing the existing native checkout,
  locked compiler, compact profile and one `.mdo/host/android-native` output.
  Hash exact bytes of all 9,562 tracked/additional native inputs, the dependency
  lock, profile and compiler binaries. On WSL, Windows Python reads that same
  checkout to avoid repeated DrvFS path-resolution RPCs; the pre/post source
  digest is identical across both readers. No source/application/Home copy is made.
- Compile the pinned ARM64 TCC runtime/SDK, selected extensions, JNI and actual
  native engine. Pass the mdo dependency checks and complete host-receipt/API
  validation before marking the state verified. The resulting `libxs.so` is
  3,535,872 bytes, SHA-256
  `e9df8cf571bd0022fbeeeda710d3ec6c812459503cfb29912bf579116d0da2e3`.
  Evidence: `.mdo/E45-native-build-final.log` and the adjacent native/build receipts.
- An explicitly stopped first run reached compilation but not final source
  verification. Keep its state unqualified and log in `.mdo/E45-native-build.log`;
  the subsequent complete build and `--reuse` verification succeed. Partial
  state, changed source/compiler/profile and modified binary reuse are rejected
  by two behavior tests (`.mdo/E45-native-tests.log`, `.mdo/E45-native-reuse.log`).
- Retain all intermediate native outputs in that one reusable directory (about
  21 MiB); no further toolchain download is needed. Neither compiling nor ABI
  validation qualifies Android framework/device behavior. Package the actual
  mdo Activity/service next; retain the previous unsupported automation flags,
  current WebView, paired Windows/plugin artifacts and open goal.

## E46 — package the complete Android development host without another app tree

- Add `tools/build_android_program.py`. Validate all current reviewed app files
  and the native XRTPACK inventory against the exercised N4 program, then extract
  its platform-neutral archive byte-for-byte. Reuse the verified E45 ARM64 host,
  one existing SDK/JDK and the QA certificate. No source/Home copy, second native
  checkout, model call, browser download or online publication is introduced.
- Build actual `XsActivity`/`XsService`, the enhanced native browser/chooser/URI
  code, `MdoRuntime` and ARM64 command worker into DEX/native payloads. Keep lite
  tool configuration, QA package `org.xleaves.mdo.browseruse.dev`, serial 1 and
  the same signer as the tiny QA APK; preserve formal mdo and QA data. The two
  QA APKs are alternate launchers for that same test package. The paired portable
  plugin remains a separate import, not an Android automation qualification.
- Verify exact native/app hashes, DEX host classes, ELF AArch64 and 16 KiB load
  alignment, ZIP alignment and v2/v3 signatures. Check actual aapt2-compiled
  package/launcher, debug setting, one correctly named private provider with
  `exported=false` and `grantUriPermissions=false`, and absence of production
  App Links/native updater. Reject extra runtimes, local state or unknown APK
  payload entries. Three compiled-provider tests include wrong identities,
  duplicates, exposed permissions and a peer element supplying missing flags.
- Current APK: `dist/mdo-browser-use-dev-android-arm64-v8a.apk`, 6,292,801 bytes,
  SHA-256 `f3fb3b2efe6753f532b60ffcc2e3d3c32379b0b72e0267542e34c24ecd12da71`.
  Evidence: `.mdo/E46-program-build-final.log`, adjacent artifact receipt and
  `.mdo/E46-program-tests.log`. All 160 Python tests finish, 158 pass with two
  existing symlink-privilege skips (`.mdo/E46-python-full.log`). Windows/plugin
  artifacts are unchanged. Retain the existing Java 8/bootstrap-classpath and
  deprecated-API compiler warnings; do not treat them as a device pass.
- ADB detects zero devices (`.mdo/E46-device-inventory.json`); the user explicitly
  defers connecting and asks to continue other development. Do not ask for a
  WebView upgrade or pause the goal. Main-UI/framework/private-URI assignment,
  Android frame/transfer integration, physical/authenticated transport, broader
  lifetime/OOM/performance and final delivery remain open. All Android automation
  and device/framework qualification fields remain false.

## F19 — bounded public Windows lifecycle and latency measurement

- Add `tools/browser_cycles_probe.py`. Use the one deployment/Home and actual
  preauthorized public C SDK, workbench and WebView2 for eight sequential
  loopback browser cycles. Each creates/observes a page, fills through native
  input, verifies exactly one trusted click, captures a native PNG and closes.
  No model/external site, additional application/Home or persisted screenshot
  is used. Restore the same deployment to headless mode in `finally`.
- All eight cycles pass. Release 40 terminal UI receipts and verify each
  original ID subsequently returns HTTP 410 `sdk_ui_receipt_expired`. Preserve
  baseline tabs after every close, with zero pending owned receipts and zero
  transfer reservations. Evidence: `.mdo/F19-browser-cycles.log` and
  `.mdo/browser-cycles-result.json`.
- Measured median wall times: create 14.68 ms, initial observe 1,654.26 ms,
  native fill 2,018.85 ms, click 1,931.99 ms, verification observe 1,512.42 ms,
  capture 1,712.45 ms and close 35.95 ms. These include Python bootstrap/HTTP
  and 50 ms receipt polling, cover eight local samples only and are not a
  production performance budget. Investigate the repeated 1.5–2 second
  completion latency before attributing it to native browser execution.
- Sample the actual native host process: handles 387 → 388 (maximum 391),
  private bytes 32,387,072 → 35,950,592. Chromium child processes are excluded;
  this cold-start/caching sample is not a memory-leak qualification. Android,
  real visual/authenticated physical-transport and broader lifetime/OOM/
  performance/final delivery scope remain active.

## F20 — wake the serialized UI queue through the existing authenticated socket

- Trace the F19 latency to the application shell's fixed 1,500 ms queue poll
  delay (10,000 ms when hidden). Publish a coalesced `ui_pending` hint through
  the existing authenticated live socket when a UI action is queued, after
  releasing the UI mutex. Keep a separate sequence from product/session changes:
  the hint contains no action, arguments, task identity, credentials or receipt.
  No additional socket, waiter, dependency or faster idle polling loop is added.
- The shell re-reads its original client queue on a hint/reconnect/focus change.
  Keep the existing fallback intervals, frozen ownership, cancellation/deadline
  checks and intent journal. Serialize drains and coalesce hints while one is
  running, preventing an in-flight action from being executed concurrently.
  Guard stale timer generations, dispose listeners/timers and ignore retired
  sockets/owners. A lost hint remains a read/latency fallback, not a resubmission.
- Pass seven pump/live-decoder tests, all 282 JS tests and 158/160 Python tests
  (the same two symlink skips). Evidence: `.mdo/F20-wakeup-tests.log`,
  `.mdo/F20-js-full.log`, `.mdo/F20-python-full.log`. The actual public
  C/workbench/OOPIF gate passes 26 operations, including nested input/capture,
  file/directory uploads, chooser cancellation and read-only denial, with zero
  transfer reservations (`.mdo/F20-public-frames.log`).
- Run the identical F19 probe (SHA-256
  `cd3f1ea8b40d693ffc30c590805e5f2b9071a3a50e8b64207118f284c30c88f7`):
  eight cycles still verify one trusted click per page and all 40 released
  receipts become unreadable. Median initial observe 1,654.26 → 241.79 ms,
  fill 2,018.85 → 561.32 ms, click 1,931.99 → 499.85 ms, verification observe
  1,512.42 → 77.04 ms and capture 1,712.45 → 246.77 ms. Harness service calls
  fall from 1,225 to 365; these exclude the UI client's own socket/HTTP traffic
  and are not a total-network or general production benchmark. Evidence:
  `.mdo/F20-browser-cycles.log`, `.mdo/F20-comparison.json`; retain F19 baseline.
- Restore the same headless deployment. The product change is in reviewed host
  overlays; refresh/exercise the matching program and Android development APK
  before handing out current artifacts. Keep the user's deferred device/current
  WebView, visual/physical transport and broader lifetime/OOM scope unqualified.

## F21 — refresh and verify both development artifacts with UI wakeup

- Pack the current reviewed application in place, preserving/restoring QA
  inputs and source hashes. Windows N5 contains 750 verified files including
  the private C notifier/header, authenticated live decoder, serialized poll
  pump and shell binding. Keep the same native host and paired 22-file plugin.
- Windows development program: 6,881,951 bytes, SHA-256
  `c618d7e990d5c7d178de2ad10325ddf72daca3763cc7e8cce0ab86a418ea218c`.
  All ten actual packed-program checks pass, including same-Home archive boot,
  installed/source/host plugin equality, default workspace review, real C/JS/
  native observation/input/capture and network-error navigation recovery.
  Evidence: `.mdo/F21-win-build.log`, `.mdo/F21-win-runtime.log` and adjacent
  inventory/runtime receipt. No source copy or intervening service start is used.
- Extract that exact N5 archive for the complete Android development host
  (lite tool edition), reusing the E45 ARM64 library and existing QA signer.
  APK: 6,292,801 bytes, SHA-256
  `04dd7dbf1fb2a11305edcfb971e701057fb6ab573cef667862fe884254a9d66c`.
  Native/app byte hashes, actual host DEX classes, compiled private provider,
  package identity, ARM64/16 KiB layout, ZIP alignment and signatures pass.
  Its receipt's source archive equals the Windows artifact exactly. Evidence:
  `.mdo/F21-android-build.log` and adjacent APK receipt. Native and Java inputs
  are unchanged; no fresh compiler download or native rebuild is needed.
- The paired plugin remains 246,883 bytes, SHA-256
  `ad8c4826ed3ed03c0cfc44ddce696ab0bb88b87d9628020e427bc094dd305912`.
  Update current artifact instructions while preserving historical receipts in
  this progress log. Restore the single headless deployment with HTTP 200.
  No online publication, formal app replacement or WebView change occurs.
  Android device/framework/automation fields remain false as the user defers
  the phone; remaining physical/visual/lifetime/OOM and full-goal work continue.

## E47 — implement the app-private local discovery transport for Android CDP

- Reassess the missing Android frame/transfer primitives against the published
  WebView boundary. Start a DevTools backend using the existing WebView, to
  supply the shared Browser Use semantics without another browser/runtime.
  Verify and pin Chromium 138's socket naming/authentication code and Android's
  process-wide debugging-setting behavior in `host/android/cdp-protocol.json`.
  Do not enable debugging, upgrade WebView or advertise automation at this stage.
- Implement the private C discovery transport: derive the abstract Unix socket
  from `getpid()`, verify kernel peer PID/UID before sending, and allow only the
  fixed read-only target-list request. No caller address/path, TCP fallback,
  redirect, credentials or effect command is accepted. Use nonblocking I/O with
  one absolute monotonic 10–5,000 ms budget, 8 KiB headers and 256 KiB body.
  Reject ambiguous framing and non-JSON content types. Clear header/allocated
  response storage and close the owned descriptor on every exit.
- The exact implementation passes 291 checks using actual Linux abstract
  sockets, ASan/UBSan and a foreign-process impersonator. Confirm zero request
  bytes are disclosed to that foreign PID, absolute timeout under trickle input,
  bounded invalid/large/partial replies, allocation-fault cleanup/full clearing,
  64 repeated connections and balanced descriptors/71 allocations. The locked
  ARM64/API 26 compile also passes. Evidence: `.mdo/E47-cdp-socket.log` and
  `.mdo/android-cdp-socket-result.json`.
- This is a transport foundation, not an installed-provider/CDP-command pass.
  Integrate JNI and the actual host next, then owned target/session/frame and
  transfer routing. Keep activation policy/device qualification explicit and
  all Android automation flags false. Preserve the user's deferred phone/current
  WebView and existing N5 development programs until the host integration is built.

## E48 — compile and package the private discovery JNI in the actual Android host

- Add the private `MdoBrowserCdpNative` facade and matching JNI entry. Refuse
  UI-thread calls and invalid timeouts; accept no caller endpoint, path or CDP
  effect. Clear native response storage even when Java allocation/copy fails;
  report static bounded error codes. This does not enable debugging, expose an
  Agent service or qualify a provider/automation backend.
- Prepare reviewed additions in the one pinned native checkout with full
  preflight/hash checks, preserved unknown edits/partials and an atomic receipt.
  The WSL native build uses Windows Git to read that checkout's Windows object
  alternates; do not rewrite the object store or add a checkout. Record the
  initial cross-platform Git failure in `.mdo/E48-native-build.log`, then pass
  preparation, pre/post exact source checks and the complete native/API build
  (`.mdo/E48-native-build-final.log`).
- Extend the exact real-kernel socket gate to 310 checks and 74 balanced
  allocations under ASan/UBSan. The JNI ABI/function-table seams cover Java
  allocation, copying, native allocation and class-lookup exceptions while
  using the real socket underneath. These are conversion faults, not an
  installed JVM/WebView/device qualification (`.mdo/E48-cdp-jni.log`).
- Actual ARM64 library: 3,539,600 bytes, SHA-256
  `88ad2aed743e22defc6706c49606567da3716a672450a1720c08139ac7f2ef9b`.
  Package the actual Java class/JNI library, verify its dynamic JNI export,
  exact app/native payloads, DEX, ARM64/16 KiB layout, compiled private provider
  and signatures. Full APK: 6,309,185 bytes, SHA-256
  `1011566a1abefc130ab6d9fc748a78c2860e6c32271a13ccb380a3666f02f803`
  (`.mdo/E48-android-build.log`, adjacent receipts). Reuse the N5 app archive,
  QA signer/package and one build/cache; no Windows/plugin refresh is needed.
- Full Python regression finishes: 158/160 pass with the two existing symlink
  skips (`.mdo/E48-python-full.log`). The single development service is healthy
  and headless. Preserve the user's deferred phone/current WebView. Next work
  is owned asynchronous routing/target binding and WebSocket/CDP frame/transfer
  integration, with activation policy and real provider checks still explicit.
  Android device/framework/automation flags and the full goal remain incomplete.

## E49 — implement bounded persistent Android CDP WebSocket sessions

- Add the private C session transport, using the pinned XRT handshake, frame,
  message and UTF-8 implementations. Accept a bounded target ID only; derive
  the page path and same-process abstract endpoint internally and verify kernel
  PID/UID before request bytes. Validate a fresh secure nonce/101 response;
  reject unsupported extensions, subprotocols, ambiguous headers and masking.
- Keep at most eight opening/live/draining sessions. Never reuse handles;
  serialize worker I/O and publish retirement before releasing I/O ownership.
  Cancellation invalidates the handle and uses shutdown to wake blocked reads;
  descriptor close/memory clearing wait for all borrowers. Treat failed writes
  as uncertain delivery and retire the channel without replay.
- Bound headers to 8 KiB, outbound text to 256 KiB and inbound messages to
  4 MiB. Preserve partial reads across idle timeouts, but enforce a separate
  absolute ten-second assembly budget across polls/continuations/control frames.
  Use masked client frames, incremental UTF-8 validation, bounded ping/pong and
  close handling. Clear full allocated capacities, request buffers and masks.
- The actual Linux-kernel/XRT transport gate passes over 1,000 assertions under
  ASan/UBSan, including fragmented Unicode/control frames, malformed/oversized
  frames/headers, real ten-second partial-message expiry, forced partial-write
  backpressure, OOM/growth failure, RNG failure, eight draining pinned sessions,
  stale handles and 64 concurrent cancel/read cycles. Its 120 allocations and
  descriptors balance; the previous discovery/JNI gate still passes 310 checks.
  Both transport sources compile with the locked ARM64/API 26 compiler.
  Evidence: `.mdo/E49-websocket.log`, `.mdo/android-cdp-socket-result.json`.
- This stage does not yet expose the session JNI or bind a target to an owned
  WebView; neither arbitrary protocol effects nor automation are enabled. Keep
  existing development programs/current WebView and the deferred phone. Next:
  package the private worker JNI, then owned target/epoch routing and frame/
  transfer integration. Full-goal/device/provider qualification remains pending.

## E50 — package the private worker session JNI and exercise it on a real JVM

- Integrate the persistent transport in the private native JNI translation
  unit. Add bounded target-ID/open, send-copy, read and close entries, using
  static error codes only. Clear all native copies on success/exceptions.
  Retire a session on a dropped Java result or failed JNI command copy; never
  let the eventual dispatcher continue after silently losing an event/reply.
- Add the package-private AutoCloseable Java Channel. Allocate its wrapper
  before opening, bind I/O to the opening worker, enforce timeout/size checks,
  and allow idempotent cross-thread cancellation. Check cancellation after JNI
  return and clear any late returned array. Do not activate debugging or expose
  raw protocol operations to Agent/extensions or the production command router.
- The exact C transport/JNI gate passes 1,073 checks with 129 balanced
  allocations under ASan/UBSan, plus the retained 310-check discovery gate.
  JNI allocation/copy faults use actual kernel sockets. A small local fixture
  library also loads the exact JNI into the existing real desktop JVM under
  `-Xcheck:jni`; the actual Java facade passes 138 checks and 16 cross-thread
  cancel/read cycles. Only Android Looper identity is doubled; no app/engine/
  Home copy or runtime dependency is added. Evidence: `.mdo/E50-jni.log`.
- Apply reviewed overlays after stopping the one service; preserve/hash-check
  all existing native edits. The locked ARM64/API 26 complete host builds and
  passes pre/post source/ABI/compiler checks: 3,546,560 bytes, SHA-256
  `7f5d050ee5011efc62743cb55f2ddef34b22eabee8d568a691fb3bc29f7f3170`
  (`.mdo/E50-native-build.log`). Verify all five dynamic JNI exports in the
  actual APK, Java classes, exact payloads, private provider, 16 KiB layout,
  alignment and signatures. Full development APK remains 6,309,185 bytes,
  SHA-256 `d5d4e2ed983fb626aac34d741504e099289b014ac7ed7bb9cb9aaf08b673e573`
  (`.mdo/E50-android-build.log`, adjacent artifact receipt). Reuse the N5
  Windows/app archive and persistent QA signer; no Windows/plugin rebuild.
- Full Python regression: 158/160 pass, two existing symlink skips
  (`.mdo/E50-python-full.log`). The single headless service is healthy. Preserve
  the user's current WebView/deferred phone and false device/framework/provider/
  automation qualifications. Next work remains CDP dispatch/owned target and
  epoch binding, frame/transfer integration, activation policy and the broader
  outstanding physical/visual/lifetime/full-goal audit. Do not mark complete.

## E51 — implement and package bounded private CDP command/event dispatch

- Add the owning-worker protocol dispatcher with one outstanding RPC, inline
  event delivery, monotonic IDs, exact reply/session matching and one total
  command deadline. Bound incoming packets per RPC to 4,096; reject reentrancy.
  Retire uncertain/malformed/mismatched streams with static errors and an effect
  flag. Do not reconnect/replay an effect. Retain complete protocol errors as
  complete replies without forwarding provider message/data into exceptions.
- Admit at most 64 trusted child sessions and remember at most 256 IDs for the
  connection lifetime. Never rebind a retired ID. Drain explicitly retired
  traffic without delivering it to another owner; reject unknown session IDs.
  Detach of the operated child ends that operation. Fence result/event delivery
  on ownership and clear returned/sent byte arrays on all exits, including
  cancellation, parser errors, callback faults and allocation failures.
- Use the pinned XRT strict JSON reader through one private JNI validation
  entry, ahead of platform JSON materialization. Bound depth/values/container
  items and reject duplicate keys, ambiguous envelopes and unsupported syntax.
  Check outgoing object graphs for limits/cycles/unknown types before encoding.
  Reuse six small hash-pinned, unmodified AOSP sources only for JVM tests in the
  existing SDK cache, retaining license headers; no APK/runtime dependency.
- Actual JVM/JNI/local kernel protocol tests pass 4,303 assertions across seven
  transport sessions with synthetic CDP peers, plus deterministic deadline,
  flood, ownership, stale-session and codec faults. The exact native gate now
  passes 1,141 checks/195 balanced transport-copy allocations under ASan/UBSan,
  including strict-parser/JNI-copy faults. Retained discovery/worker gates pass
  310/138 checks. Evidence: `.mdo/E51-dispatcher.log` and transport receipt.
  This is not an installed WebView/CDP provider qualification.
- Review/apply overlays after stopping the same service and rebuild the locked
  ARM64/API 26 host with pre/post source/ABI checks. Library: 3,547,088 bytes,
  SHA-256 `a21f1120fbaae36bcaa29a472e9eae3a61248a5ca270968fd62b1a677f6e7d90`
  (`.mdo/E51-native-build.log`). Full APK: 6,313,281 bytes, SHA-256
  `f948cec4e293748f80a5d378a5d307b6705dcdb7aafa3c0dbfd5443eb918851a`
  (`.mdo/E51-android-build.log`). Check all six native exports and actual SDK
  DEX class definitions rather than descriptor references; reject accidentally
  bundled JSON/test/browser runtimes. Exact payloads, provider/manifest, 16 KiB
  ELF layout, alignment and signatures pass. Reuse the N5 app archive and signer.
- Full Python regression: 162/164 pass, two existing symlink skips
  (`.mdo/E51-python-full.log`). Keep the one headless service healthy. Current
  WebView/deferred phone and all false device/provider/automation gates remain.
  Next implement exact owned WebView target/epoch binding and asynchronous host
  routing, then trusted frame/transfer integration and activation policy. The
  broader physical/visual/lifetime/completion audit still applies; goal active.

## E52 — implement and package the private native-view binding core

- Inspect/pin Chromium primary-frame evaluation and callback retention, target
  description metadata and its pinned V8 lexical-context APIs. Add the public
  Handler/WebView adapter and a fixed random lexical marker for one exact UI
  view/document generation. No URL/title/geometry match, caller debugger URL,
  navigation replay, page function, DOM property or Debugger activation is used.
  Retain only bounded target IDs; clear the discovery input after parsing.
- The protocol worker probes candidate context metadata/name lists without
  sending the random identifier to a nonmatching target. Require the native UI
  callback, exact root/default context, a negative capability probe proving the
  provider honors uniqueContextId, and a read of the independent marker value in
  that unique context. Clear the primitive and compare root frame/loader again
  before returning. Reject silent default-context fallback, wrong proof,
  navigation/context loss and uncertain cleanup. Unavailable pre-match
  candidates may be skipped; proof failures do not fall back or replay effects.
- Bound 64 sequential candidates, a 100–15,000 ms total budget and 5,000 ms
  phases. Bound native callback retention to 16 shared slots and lexical
  declarations to 16 per adapter generation. Weak provider callbacks cannot hold
  the UI owner or admit late results; retain uncertain callback charges until
  actual return/GC. Empty lexical slots remain until document replacement;
  failed cleanup targets a proven unique context once only. Do not target a
  default UI document or replay uncertain cleanup; unconfirmed private values
  remain bounded until document replacement. Transfer ownership
  atomically to the completed binding, so UI invalidation closes I/O and clears
  the controller event reference. The future UI registry must retain/reuse one
  adapter per native document and close it before admission changes.
- Actual JVM/JNI/strict JSON/adapter/binder/dispatcher checks pass: 474 binding
  assertions with Android UI calls doubled and synthetic protocol metadata.
  Cover ignored unique IDs, wrong targets/values, missing primitives, root loader
  replacement before lifecycle delivery, queued cancellation, native callback
  retention/late return, resource exhaustion and ownership loss. Retained
  native/discovery/worker/dispatcher gates pass (`.mdo/E52-binding.log`). Actual
  installed WebView binding is still unverified; do not qualify the provider.
- Apply reviewed Java overlays only after stopping the single service. Reuse
  the exact verified E51 ARM64 library and N5 app archive. Full APK: 6,321,473
  bytes, SHA-256 `b624a7964dd7355f52df9f7283a392296a6bef9567323949f2eb315e01bb0719`
  (`.mdo/E52-android-build.log`, adjacent receipt). Real SDK compilation, actual
  binding class definitions, six native exports, exact payloads, private
  manifest, ABI/alignment and signatures pass. Reject packaged android.* test
  doubles as well as JSON/AndroidX/Chromium runtimes; add no runtime dependency.
- Full Python regression passes 162/164 with two existing symlink skips
  (`.mdo/E52-python-full.log`); the final private missing-method classification
  and unique-context-only cleanup are additionally covered by the rerun binding
  gate and actual APK rebuild.
  Restore the same headless service. Keep current WebView/deferred phone and
  false framework/provider/automation flags. Remaining work: actual UI lifecycle
  registry/asynchronous host routing, frame/transfer semantics, activation/profile
  qualification and the broader physical/visual/lifetime/full completion audit.

## E53 — fix modal native-input cancellation reasons

- Found actual host chooser/popup callbacks using `file_chooser_open` and
  `popup_pending`, while the native input sequencer rejected both before
  cancelling held input. Admit these two existing static reasons, retaining
  strict rejection of unknown reasons and the existing cancellation receipts.
- Exercise the actual lifecycle/input sequencer with a held pointer for dialog,
  chooser and popup, duplicate cancellation and delayed timer delivery. Verify
  exactly one completion, pointer cancellation and no replay. Java 8 contract
  gate passes: input 177, lifecycle 105, channel 22, guard 63, wheel 66 and hover
  82 checks (`.mdo/android-input-result.json`). Android framework/device checks
  remain false. Host overlay/APK integration follows with the lifecycle stage;
  no formal installation, provider update or publication performed.

## E54 — connect private CDP ownership to native UI lifetimes

- Add an exact-instance, UI-owned registry (eight views maximum), with lazy
  adapters only when privately requested. The native input observer revokes
  bindings before completing cancellation, including direct dialog, chooser
  and popup callbacks as well as navigation, visibility, takeover, background,
  viewport changes, stop and destruction. Host channel blockers revoke all
  leases. Marker retirement drops its UI predicate/view reference immediately.
- Preserve a shared 16-declaration quota across adapter retirement/recreation
  in the same document, including channel replacement; only an observed newer
  navigation or native view destruction retires that quota. Reject silent epoch
  replacement, key/view aliases, stale callbacks, cross-thread registry access,
  closed admission and capacity overflow. Replacing a named-world wrapper does
  not reset the underlying document quota. Add no public CDP route/debug toggle.
- Actual registry/lifecycle/input/marker gate passes 107 checks; retained actual
  JNI/strict JSON/binding gates pass 474 binding, 4,303 dispatcher, 138 worker,
  1,141 WebSocket and 310 discovery checks. Input contracts retain their E53
  pass. Evidence: `.mdo/E54-registry.log` and adjacent native receipt. UI calls
  remain doubled; this does not qualify an installed WebView/provider.
- Build the actual full Android host after stopping/applying reviewed overlays.
  Reuse the verified ARM64 engine and N5 archive. APK: 6,325,569 bytes, SHA-256
  `3bacd544738bc9a59aaf1ace21887ed892497f29c6ea40df47944038e3770c67`
  (`.mdo/E54-android-build.log`). Real SDK compilation, actual registry DEX
  definitions, package/manifest/signatures/native exports/payload checks pass;
  seven positive/adversarial Android packaging unit tests pass. Retain false
  device/provider/automation flags and the unchanged WebView. Next implement
  bounded asynchronous worker dispatch, then frame/transfer/policy integration.

## E55 — package bounded asynchronous private CDP dispatch

- Add one persistent, lazily started protocol worker; preserve thread ownership
  across cached bindings. Bound eight pending jobs including UI delivery and one
  job per lease, with no caller-runs fallback. Include queue/binding time in one
  100–15,000 ms operation deadline. Retire failed bindings; never reconnect/replay
  within a submitted operation. Report potential effects conservatively once a
  declared effect operation has started, including cancellation/late failure.
- Wire registry retirement into queued/running dispatch cancellation. Drop work
  that has not started, close the original marker/transport to wake a binding or
  native I/O, and post one UI receipt rather than synchronously reenter teardown.
  Reject cross-thread UI submission, invalid budgets, busy leases and capacity
  overflow. Keep queued/published results within the same pending-job bound.
- Bound UI output to 1 MiB/job; clear returned buffers on delivery/discard,
  cancellation, oversize, callback failure and shutdown. Clear pending callbacks
  on teardown or failed UI scheduling; stop the worker and refuse reuse if its
  scheduler fails. A throwing completion retires its binding and cannot break
  another job. Workers/queued jobs hold weak marker references; retired markers
  have already dropped UI view/controller predicates.
- Actual worker/registry/binder/strict JNI gate passes 167 worker checks,
  covering cached thread affinity, queue saturation, queue deadline, cancellation
  during binding/effects, late native/UI results, callback faults, synthetic OOM,
  oversize output and scheduler rejection/throw/teardown. Retained 107 registry,
  474 binding, 4,303 dispatcher, 138 JNI-worker, 1,141 WebSocket and 310 discovery
  checks pass (`.mdo/E55-worker.log`, native receipt). Android UI/provider calls
  remain doubled/synthetic; no installed-device qualification is implied.
- Rebuild the complete development APK using the exact existing ARM64 engine
  and N5 app archive, after stopping/applying the reviewed host. APK: 6,329,665
  bytes, SHA-256
  `5d513d5892024b457972b88874e2396fcd889ffc4989b987afeaa5f4dff460a9`
  (`.mdo/E55-android-build.log`, adjacent verified receipt). Actual SDK/D8 class
  definitions, payload/package/native exports/manifest/alignment/signatures pass.
  Full Python regression passes 162/164 with two existing symlink skips, in
  43.640 s (`.mdo/E55-python-full.log`). Overlays/native transport match reviewed
  inputs; the one headless deployment is healthy at HTTP 200, 3,417.5 MiB.
- Keep current WebView/deferred phone, original formal data and all false
  provider/framework/automation qualification flags. No model calls, external
  browsing or publication performed. Typed isolated-frame automation routing,
  transfer semantics, activation/profile qualification and remaining physical,
  visual/lifetime/full requirement audit are still outstanding; goal active.

## E56 — package native frame graph and related process-session routing

- Verify/pin Chromium 138's protocol schema, renderer frame tree, native frame
  target identity and related-target attachment implementation. Each renderer
  omits remote children from its own snapshot. Reconstruct the owned WebView's
  hierarchy from recursively auto-attached iframe sources, joining exact native
  frame/parent/loader identities inside the original source's subtree. No URL,
  title, opener, browser-wide discovery or arbitrary target attachment is used.
- Extend the private binding with explicit session/effect routing, trusted
  session admission/retirement and one worker-owned frame controller. Keep root
  context lifetime checks separate from admitted child-context events. Publish
  opaque frame/document aliases, with private native IDs and pinned ancestry.
  Bound 32 frames, 64 sources including root, 256 lifetime child-session IDs,
  depth 32 and metadata refresh rounds under the original operation deadline.
- Fence references on document commit/open, subtree detach, process swap and
  reverse migration. A parent commit invalidates descendant references even if
  descendants have not delivered their detach yet. Block routes through loading
  or unproven ancestors. Preserve a nonempty unchanged loader/document over a
  short loading interval without a commit. Retired-session traffic is drained,
  never redirected; raced metadata responses are discarded before bounded
  read-only refresh. Never reconnect/replay a native effect within a job.
- Actual Java graph/worker/binding/dispatcher/strict JNI tests pass 172 checks:
  nested process trees with omitted remote anchors, stable aliases, private-ID
  nonexposure, parent/target/source mismatch, wrong target type, duplicate nodes,
  limits/depth, empty loaders, queued loading races, source detach during a read,
  bidirectional process migration, stale ancestry, early subtree retirement,
  old-session traffic/reuse and UI-thread rejection. Retained 167 worker, 107
  registry, 474 binding, 4,303 dispatcher, 138 JNI-worker, 1,141 WebSocket and 310
  discovery checks pass (`.mdo/E56-frames.log`, native receipt). Android UI and
  protocol peers remain doubled/synthetic; this is not a provider/device pass.
- Stop/apply reviewed Java overlays and build the complete Android host using
  the same ARM64 engine/N5 app archive and persistent QA signer. APK: 6,333,761
  bytes, SHA-256
  `6b4914e0cf9d8fbe1aad723cd1925a66c1508a2777bb375a1b33d107c80f991e`
  (`.mdo/E56-android-build.log`, adjacent verified receipt). Real SDK/D8 native
  frame class definitions, package/manifest/signatures/payload/ABI checks pass.
  Full Python regression passes 162/164 with two existing symlink skips, in
  54.950 s (`.mdo/E56-python-full.log`). Source/native overlays match reviewed
  inputs; the single development service is healthy, HTTP 200, 3,417.7 MiB.
- Keep frame inventory `isolated:false` and Android automation disabled. This
  stage performs metadata only; exact isolated unique-context execution, fixed
  shared frame operations, native owner/input/transfer integration and debug/
  profile activation remain, together with the physical/visual/lifetime/full
  completion audit. Preserve current WebView, deferred phone and formal data;
  no model calls, external page tests or publication. Goal remains active.

## E57 — implement and package private isolated frame execution

- Verify/pin native context type/frame/name metadata and isolated-world creation.
  The examined engine reuses named worlds before initializing their security
  origin. Use a fresh binding namespace plus distinct names per original native
  source/frame/loader proof, explicitly denying universal-origin access. Reuse
  a name for the same unchanged native proof, not across changed loaders or
  process sources. Do not adopt default, unrelated or potentially privileged
  old worlds. Add no browser/debug dependency or activation switch.
- Require exact route/ancestry, creation reply, native frame/session/type/name
  and unique-context metadata. Each source must reject an invalid unique ID;
  each world must pass its real unique-context harmless probe before page code.
  Evaluate by unique ID only, with no default/numeric fallback. Retain minimal
  initial root IDs for default/other-context ambiguity checks. Bound delayed
  metadata waiting by 500 ms within the original operation deadline.
- Bound 32 current world routes, 256 active context records, 1,024 lifetime
  context keys, 192 KiB UTF-8 source and 1 MiB result envelopes. Share 256 native
  name reservations across leases/bindings within the UI-owned document
  generation; retain uncertain charges. Registry tests prove this counter does
  not reset on short admission losses. Navigation callback/framework semantics
  remain unqualified rather than being claimed as an actual-provider guarantee.
- Context loss fences only its exact admitted document/loader and descendants;
  old context callbacks cannot retire a newer document. Loader changes get fresh
  world names. A native metadata barrier and original pins surround execution.
  Return only by-value JSON/finite primitives with opaque aliases; omit native
  exception details. Preserve possible effects on loss/navigation/error, and
  never replay a script after uncertainty. Owned result bytes use the existing
  clearing worker; Java JSON/strings remain bounded GC-managed values.
- Actual Java world/frame/binding/session/worker/strict JNI gate passes 539
  isolated-world checks, covering nested process worlds, cached identity and
  probes, delayed metadata, all wrong proof variants, ignored unique IDs,
  context/document/parent loss, lost effects without replay, Unicode/primitive
  values, exceptions/oversize, context resource floods, loader-specific names,
  recovery and late old callbacks. Registry/shared-budget checks increase to
  375. Retained 172 frame, 167 worker, 474 binding, 4,303 dispatcher, 138 JNI-worker,
  1,141 WebSocket and 310 discovery checks pass (`.mdo/E57-worlds.log`, native
  receipt). Android UI/evaluation peers are doubled/synthetic, not a DOM/provider
  or installed-framework pass.
- Stop/apply reviewed host overlays and compile the complete Android development
  host using the existing ARM64 engine, N5 app archive and QA signer. APK:
  6,341,953 bytes, SHA-256
  `0cde1c740c629f458853d69babd7eba47e6cb69a7d979888c5233607f56c405d`
  (`.mdo/E57-android-build.log`, adjacent verified receipt). Real SDK/D8 world
  class definitions, payload/manifest/package/native exports/ABI/signatures pass.
  Full Python regression passes 162/164 with two existing symlink skips in
  46.934 s (`.mdo/E57-python-full.log`). Source/native overlay checks pass; the
  single development deployment remains HTTP 200, 3,417.9 MiB.
- Keep Agent admission/global Android automation disabled. Private inventory and
  evaluation are implemented; typed Agent routing, fixed shared operations,
  owner geometry, guarded native input/transfers, activation/profile policy and
  the broader physical/visual/lifetime/full audit remain. Preserve deferred
  phone/current WebView/formal data. No model calls, external page tests or
  publication performed; goal remains active.

## E58 — route shared frame commands and prove parent-owner queries

- Route the actual Android host's private `frames` command through the existing
  bounded worker, native frame graph and isolated worlds. Preserve original
  channel/view/epoch on delivery. Product/profile admission still runs first;
  no request can enable debugging or bypass disabled automation admission.
- Add an immutable action-specific command grammar for list/evaluate/owner/
  upload-policy/capture-owner. Require literal Agent mode and opaque aliases;
  refuse unknown protocol/debug/profile fields, malformed Unicode, invalid
  points and budgets. Copy geometry before queuing. Retain bounded static native
  causes in typed errors and conservative effect uncertainty for script calls.
- Resolve each child owner on its pinned parent's native source. Resolve the
  node in the parent's isolated world, then select execution by unique context
  and pass the node only as a same-world checked object argument. Require
  negative unique-ID rejection plus harmless same-world argument proof before
  executing a fixed DOM helper. Pin the examined V8 argument context/isolate
  checks; never select execution by object or numeric context fallback.
- Generate Android geometry/upload-policy/capture-owner constants from the
  unchanged reviewed Windows helper bodies. Verify exact parity before native
  tests and APK source hashing. Release every temporary object group on its
  original source within the original deadline; retire binding and withhold
  success after uncertain cleanup. Guard both child and parent throughout.
- Actual Java/JNI/protocol gate passes 451 owner and 49 command checks, plus all
  retained discovery/WebSocket/worker/binding/graph/world gates. New fixtures
  cover nested process parents, wrong-realm objects, ignored unique IDs,
  navigation, cleanup loss and deadline/cancellation, immutable parameters,
  malformed routing and error/effect receipts (`.mdo/E58-owner-router.log`).
  DOM/provider/UI peers remain modeled, not an installed-provider pass. Three
  Python generation contracts pass; complete regression passes 165/167 with two
  existing symlink skips in 46.781 s (`.mdo/E58-python-full.log`).
- Stop/apply reviewed overlays and rebuild the complete development APK using
  the verified existing native engine, N5 app archive and QA signer. APK:
  6,346,049 bytes, SHA-256
  `fb62fdc669592fe37127835b0fbc14b351ca96d4038a85bb8236fa83d695aad3`
  (`.mdo/E58-android-build.log`, adjacent receipt). SDK/D8 classes, native ABI,
  manifest/payload/signatures and reviewed source/native/helper checks pass.
  Single development deployment remains HTTP 200, 3,418.0 MiB.
- Keep global Android automation disabled. Continue native frame-chain input/
  capture, chooser/download/activation policy and installed main-UI/shared C/JS
  qualification. Audit Windows numeric/object realm selection against the same
  unique-context invariant before cross-platform completion. Phone is deferred;
  preserve its provider/formal data. No model calls, external pages or release;
  long-term goal remains active.


## E59 — connect native frame-chain input to the owned WebView sequencer

- Replace the actual host's main-document-only native input bridge with the
  private worker/native frame/world path. Parse immutable bounded kind/target/
  focus/geometry/text/key data and complete ancestor pins; reject caller code,
  raw protocol identities and unknown fields. Validate optional complete visual
  snapshots against every native ready isolated frame, including siblings.
- Guard every click/hover/wheel/focus/text/key step using fixed isolated scripts,
  exact local document/ref, root native viewport/scroll/scale, parent owner
  mapping and full native ancestry. Require the exact mapped root point for
  pointer events and focused local target/every ancestor for keyboard input.
  Reverse/forward-map wheel points through original frame owners. Keep actual
  input on the owned WebView's existing sequencer, with no CDP input, fallback,
  target retargeting or automatic replay. Shared subframe scroll dispatch still
  needs its complete pin; the native adapter is not a claim that flow is done.
- Cancel the exact pending guard lease when input expires/is cancelled; ignore
  late guards and release existing touch/key state. Preserve focus uncertainty,
  original host-channel receipts and typed pre-dispatch admission failures.
  Keep UI geometry/lease validation before and after asynchronous work.
- New actual Java input/sequencer/frame/world/owner/worker/strict-JNI gate passes
  300 checks: all six modes in root and nested process frames; immutable
  parameters, malformed routing/geometry/focus/ancestry/snapshots; changed target
  point, takeover and root layout; navigation after DOWN yields CANCEL without
  a later UP; cancellation and watchdog expiry stop queued DOM focus and late
  input. Retained protocol gates pass (`.mdo/E59-input-core.log`). Android DOM,
  event targets and protocol eval peers are doubled, not provider qualification.
- Nine Node fixed-script/shared-runtime iframe-hit checks pass. Full sequential
  Node regression passes 283/283 in 17.810 s (`.mdo/E59-node-full.log`). Full
  Python regression passes 165/167 with two existing symlink skips in 46.598 s
  (`.mdo/E59-python-full.log`). Reviewed host/helper/native checks pass.
- Stop/apply overlays and compile the actual complete development host with the
  verified existing ARM64 engine, N5 application archive and QA signer. APK:
  6,350,145 bytes, SHA-256
  `78470a77eebd117834b5fd036264248439845cc53b9fe80299ac508d6750d74b`
  (`.mdo/E59-android-build.log`, adjacent receipt). SDK/D8 definitions, payload,
  private provider, ABI/16 KiB layout, alignment and signatures pass. Single
  deployment HTTP 200; footprint 3,418.3 MiB. Windows program/plugin unchanged.
- Keep product/global Android automation disabled. Continue frame-chain capture,
  shared subframe scroll dispatch, chooser/download/profile/activation policy,
  installed main-UI/provider and cross-platform realm/production qualification.
  Phone remains deferred; current provider/formal data are preserved. No model
  calls, external page browsing or online publication. Goal remains active.

## E60 — fix self-rejection while a visual validation screenshot is pending

- Actual WebView2 frame regression caught a visual click failing before input
  with `browser_busy` while its own validation screenshot was still running.
  The wait loop's ordinary action guard rejected `capture_busy` after its first
  40 ms poll. Prior fast captures could finish before that poll, masking the
  deterministic ownership/plumbing error (`.mdo/E60-native-frames.log`).
- Give only the outstanding read-only validation-capture wait a checkpoint that
  tolerates native capture busy. Keep all original cancellation, takeover,
  navigation, cleanup, deadline and authority checks. Initial admission, later
  DOM reads and input keep the ordinary guard; foreign/pending screenshots
  cannot authorize input. Finally cancel only the original capture key.
- Add slow-100-ms-capture and wait-cancellation checks, plus real shared-guard
  tests proving that only busy admission is relaxed and each other boundary
  still refuses. Full sequential Node suite passes 292/292 in 18.494 s
  (`.mdo/E60-node-fixed.log`). Stop/apply the reviewed shared host changes; actual
  WebView2 frame suite passes 224 checks with no remaining upload reservation
  (`.mdo/E60-native-frames-fixed.log`). Four of those checks exercise the
  concurrent next-stage read-only nested scroll route, still pending its own
  feature commit. Tests use local fixture pages and the single existing Home.
- No model calls, external page browsing, provider changes or publication. This
  fixes an observed race; Android/main-UI/normal-desktop visual and whole-goal
  qualification remain incomplete. Refresh development artifacts with the next
  tested shared-driver stage; goal remains active.

## E61 — accept the shared visual snapshot's explicit root-parent representation

- Cross-end audit found the shared visual dispatcher emits `parent:""` for f0,
  while the new Android parser accepted only JSON null. That would reject valid
  visual input when Android admission becomes available. Normalize either exact
  root representation to the same immutable null parent. Do not accept empty
  child parents, foreign root ancestors, missing fields or invalid native trees.
- Add an actual shared-wire-shaped snapshot through the Java worker/frame/world/
  owner/JNI/native-input sequencer gate. Mutating the original JSON after parse
  cannot alter its pins; invalid child/root parents still refuse before input.
  Full retained protocol gates pass (`.mdo/E61-snapshot-wire.log`); native input
  checks increase from 300 to 315. DOM/events/provider remain doubled/unqualified.
- Keep automation disabled, current phone/provider/formal data preserved, and
  refresh the APK with the next shared driver phase. No model calls, external
  page browsing or publication; the full long-term goal remains active.

## E62 — isolate development previews from the official update channel

- Packed verification hit the production updater's correct `update_required`
  gate: the fixed 0.7.0 preview had accepted an official minimum-0.8.0 manifest
  (cache code 05000800/build 70000073). Boot/plugin/UI checks passed but a local
  QA task could not be created (`.mdo/E62-windows-program.log`). Do not relabel
  the preview as a newer official release or clear the accepted policy cache.
- Give only these explicitly named development archives a compile-time preview
  flag. The updater then reports development/disabled before reading or altering
  official update state; it cannot install a stock release over the enhanced
  preview or become subject to that channel. Ordinary builds without the flag
  preserve their existing code path, semantic version and build identifiers.
  Represent the conditional as a reviewed host overlay; retain the single
  in-place packaging journal/restore rather than creating an app/Home copy.
- Share the exact development prefix between Windows packaging and Android
  archive verification. Include its explicit channel policy in the receipt and
  require it before packed-program/Android archive reuse. Actual packed Windows
  verification now passes all 12 checks (the former ten plus disabled official
  updates and byte-identical existing policy), restores the headless deployment
  and verifies paired plugin/C SDK/workspace/native-browser/network recovery
  (`.mdo/E62-windows-development-package.log`,
  `.mdo/E62-windows-development-program.log`).
- The built app also contains the next shared scroll-driver stage, pending its
  feature commit/artifact inventory update. Its Android build and full Python
  regression pass: 165/167 with two existing symlink skips in 52.385 s
  (`.mdo/E63-android-build.log`, `.mdo/E63-python-full.log`). No formal executable,
  account/session data, official cache or server/release policy was changed.
  No model calls or publication; goal remains active.

## E63 — route shared scroll points into exact native child documents

- Runtime 20 returns an exact iframe owner ref only to the trusted native-frame
  scroll driver. It never reads child windows/documents or uses URL/name hints;
  legacy/main-document point checks still refuse a child boundary. Pin each
  local document, join its owner to the native child, translate the point and
  finally forward-map through the original complete chain before input.
- Preserve the leaf document/hit ref at the Android dispatcher instead of
  requiring/replacing it with the root document. Validate qualified hit refs,
  unique bounded chains ending at f0, root observation identity and exact root
  coordinates. Missing, duplicate, changed or malformed pins refuse before any
  wheel. Permit bounded negative content origins for partially clipped iframe
  owners only when the point remains in proved visible content. Visual point
  routing shares this corrected clipping rule; normal privacy/focus guards stay.
- New runtime tests prove iframe routing reads no child content/URL/name, uses
  literal trusted routing mode and retains local hit refs. Driver/dispatcher
  tests cover root/child documents, complete chains, point/document/ref drift,
  invalid ancestry, cancellation and clipped owners. Full sequential Node suite
  passes 292/292 in 18.494 s (`.mdo/E60-node-fixed.log`). Actual WebView2 frame
  fixture passes 224 checks, including new three-level/OOPIF scroll-point proof
  with exact leaf identity and unchanged focus (`.mdo/E60-native-frames-fixed.log`).
- Retain the separately committed E60 own-validation-capture wait fix and E61
  shared root-parent normalization. Android protocol/input gates pass, with 315
  actual Java/JNI/sequencer input checks (`.mdo/E61-snapshot-wire.log`); DOM/
  events/provider remain doubled. Package roundtrip passes all eight checks and
  22 files (`.mdo/E60-package-probe.log`). Shared C/JS/overlay/helper/JNI checks pass.
- Refresh paired development artifacts in place: Windows 6,882,658 bytes,
  SHA-256 `e0d51c6fbd43829372d6a4b588d37cfe8a4acf1d63b21d4c3e60d5d7d209777d`,
  750 files/all 12 actual packed runtime checks; plugin 247,351 bytes,
  SHA-256 `c6b74a2780f13d08570e61bdaf58cbcbc8c4678dc06ce0348de5e8a370ae5d41`;
  complete development Android APK 6,350,145 bytes,
  SHA-256 `850c38c8b70f02b7ecc584d398f928e300efca1f37a107f42e595a603a838a3e`.
  Windows logs are `.mdo/E62-windows-development-{package,program}.log`; actual
  Android SDK/D8/ABI/payload/signature build passes `.mdo/E63-android-build.log`.
  Full Python regression passes 165/167 with two existing symlink skips in
  52.385 s (`.mdo/E63-python-full.log`). Existing deployment HTTP 200, 3,418.8 MiB.
- Goal remains active: native frame-chain capture, chooser/download transfers,
  debugging/profile activation, installed main-UI/provider/event/normal-desktop
  qualification and cross-platform unique-context audit are still open. Phone
  remains deferred, provider/formal data unchanged, Agent automation disabled.
  No model calls, external page browsing, extra app/Home copies or online release.

## E64 — withhold screenshots until their final isolated guard acknowledges

- Extend the existing Android capture transaction from initial admission to a
  second isolated guard after encoding and before image delivery. Reuse the
  original guard, absolute deadline and weak native lease, adding phase/ticket
  validation so an old/duplicate callback cannot authorize the final phase or a
  replacement capture. This prepares the capture core for the native frame-graph
  proof; connecting its capture context/composed masks remains the next stage.
- Release the native bitmap after encoding while retaining the shared capture
  reservation for the bounded private encoded image until validation settles.
  Clear owned bytes after final guard error/cancel/navigation/timeout/close,
  invalid encode, lost scheduling/allocation/delivery or cleanup failure. Keep
  uncertain native cleanup poisoned. Successful output transfers to the caller;
  no draw/encode is replayed and no guard receives a fresh extended timeout.
- Adversarial snapshot-supplier tests exposed reentrant cancellation gaps:
  an old guard could proceed to native readiness, native-ready cancellation
  needed its terminal work acknowledgement, and encoded snapshot cancellation
  needed to clear its local result before abandoning the Job. Fence exact active
  Job/phase after those external calls and settle only the original work record.
- Actual production Java capture core passes 304 checks, including final-guard
  withholding/duplicate/old callbacks, all final error paths and clearing,
  original remaining deadline, private-image global quotas, failed result
  delivery/timer removal and reentrant snapshot cancellation at each stage,
  plus retained lifetime/OOM/worker/cleanup tests (`.mdo/E64-capture-guard.log`).
  Targets/scheduler/bitmaps are synthetic; no Android framework/provider pass.
- Stop/apply reviewed overlays and rebuild the complete development Android APK
  with the exercised E63 app archive and existing native engine/QA signer:
  6,350,145 bytes, SHA-256
  `bba4e663d088552238bea0e8f371cea02dade905fba634db7196e41685bfb297`
  (`.mdo/E64-android-build.log`, adjacent receipt). SDK/D8, native ABI/payload,
  private-provider, alignment/signatures and source/JNI checks pass. Full Python
  regression passes 165/167 with two existing symlink skips in 65.829 s
  (`.mdo/E64-python-full.log`). Single deployment HTTP 200, 3,419.0 MiB; Windows
  program/plugin/JS contracts unchanged from the tested E63 payload.
- Continue actual native frame-chain capture context, fixed per-frame/owner
  geometry checks and shared C/JS mask routing, then transfer/activation/profile
  and platform/realm audits. Keep phone/current provider/formal data unchanged,
  no model calls/external browsing/publication, and Android automation disabled.
  Full goal remains active.

## E65T — distinguish queued admission from a completed lost effect in transport QA

- The full sequential Node run exposed a race in the actual C/HTTP lost-admission
  fixture: submission acknowledges the queued operation, so immediate cancellation
  may correctly cancel it before the effect completes. The test wrongly required
  successful recovery in that case. Retain the lost HTTP reply, inspect only the
  original operation to a bounded terminal state, require success, then cancel
  its waiting caller and verify exact-ID recovery without a second submission.
  Queued cancellation/product SDK semantics are unchanged.
- The focused transport suite passes 6/6 (`.mdo/E65-sdk-admission.log`). Full
  sequential Node regression passes 299/299 in 19.655 s
  (`.mdo/E65-node-fixed.log`); full Python regression passes 165/167 with two
  existing symlink skips in 72.789 s (`.mdo/E65-python-full.log`). These runs also
  cover the next native frame-capture changes, still pending their feature
  commit, SDK build and artifact refresh. Keep the first failing log as evidence
  (`.mdo/E65-node-full.log`). No model calls, provider changes or publication.

## E65 — connect shared privacy composition to native frame-graph capture

- Freeze the shared Android native navigation and send the complete composed
  frame stamp with root native/local identities, viewport/scroll/DPR. Preserve
  all existing shared masks and Windows wire behavior. Parse/copy immutable
  native capture requests with strict fields/rooted ancestry, safe navigation,
  bounded embedded JSON before recursive parsing and exact end-of-input.
- Connect the original owned-WebView raster transaction to the existing bounded
  private worker/native graph/unique worlds. Before draw and after encode, prove
  every readable local document plus parent-owned reference/geometry, then root
  world and complete graph again. Keep exact view/epoch/channel and one absolute
  deadline. Cancel only the original pending lease; withhold/clear private bytes
  on failure and clear after encoded handoff. Unreadable subtrees stay opaque.
  Offscreen/full-page regions are explicitly unsupported on Android; preserve
  native PNG/262,144-byte/4,194,304-pixel caps and existing global capture quotas.
- Allow scoped visual references only for the complete native frame adapter,
  carrying its exact context/receipt through subsequent image validation. Fix
  visual validation's private source to PNG/262,144 bytes independent of public
  JPEG/small-output settings. Changed/failed/lossy replies dispatch no input.
- Actual Java/JNI/JSON/frame-world-owner-worker/capture transaction passes 161
  checks with synthetic protocol peers/UI/raster, retaining balanced cleanup and
  no provider qualification (`.mdo/E65-native-frame-capture-final.log`). Full
  Node passes 299/299 in 19.655 s; full Python passes 165/167 with two existing
  symlink skips in 72.789 s; final Android contracts pass 20/20. Actual Windows
  WebView2 frame checks pass 224 with no crash diagnostics/private reservations;
  portable package import/restart/roundtrip passes 8; packed runtime passes 12.
  Logs: `.mdo/E65-node-fixed.log`, `.mdo/E65-python-full.log`,
  `.mdo/E65-android-contracts-final.log`, `.mdo/E65-native-frames.log`,
  `.mdo/E65-package-probe.log`, `.mdo/E65-windows-{package,program}.log`.
- Refresh the same artifacts in place. Windows: 6,883,165 bytes, SHA-256
  `6b9d858c0d713ee4ba7543060e0f341b56d9d0accb26b3f9edd86aa3625900b6`,
  750 files; plugin unchanged at 247,351 bytes, SHA-256
  `c6b74a2780f13d08570e61bdaf58cbcbc8c4678dc06ce0348de5e8a370ae5d41`;
  complete Android development APK: 6,358,337 bytes, SHA-256
  `5e5edfd69a0044e1b9407261d81a1a144fc43808b5d7a86411c26992d5158aa6`.
  SDK/D8/DEX/native ABI/payload/private-provider/alignment/signature build passes
  (`.mdo/E65-android-build.log`). Reuse the same native engine, QA signer, Home
  and source checkout. Official update caches/formal data unchanged.
- Goal remains active. Continue chooser/source/download transfers, activation/
  profile policy and installed-provider/main-UI/drawing qualification, plus the
  remaining Windows unique-context/realm audit. Phone/current WebView stay
  deferred; Android automation/debugging remain disabled. No model calls,
  external page browsing, extra app/Home copies or online release.

## E66 — bind Android upload preflight to the exact native file input

- Add immutable `frames/file_input` pins and a private native object inspection
  route on the existing worker/graph/world bridge. Prove the complete original
  chain and ancestor upload policy before and after resolving the local retained
  file input. Obtain its native node description only after empty-unique-context
  rejection and a same-world argument proof. Never select an execution realm by
  numeric/object ID or expose the native object/backend/context/session IDs.
- Fixed helpers require the original local document, connected input and exact
  runtime reference/object; return only document/reference and boolean multiple,
  directory and hidden flags. Read no selected file names/bytes and emit no
  assignment/event. Release the exact random group on its original source or
  retire the binding on uncertain cleanup. Preserve specific disabled/inert,
  malformed-input and cleanup codes plus their bounded native causes.
- Enable this inspection in the trusted Android shared driver for direct and
  intercepted-picker target preparation, inheriting only its remaining deadline.
  Reject changed native echoes/flags/ancestry or cancellation before returning a
  target. A composition test uses the actual upload dispatcher and proves zero
  source SDK reads, native upload calls or dispatch markers after rejection.
  Windows defaults/wire behavior are unchanged.
- Actual Java/JNI/JSON/worker/frame/world/owner inspection passes 625 checks with
  synthetic protocol DOM peers and zero files delivered (`.mdo/E66-native-final.log`).
  Production JS helper strings also execute in Node VM tests without touching
  selected files, input value or events. Full sequential Node passes 305/305 in
  22.398 s (`.mdo/E66-node-full.log`); Python passes 165/167 with two existing
  symlink skips in 99.543 s (`.mdo/E66-python-full.log`). Actual WebView2 frame
  checks pass 224 with no new crash diagnostics or private reservations
  (`.mdo/E66-native-frames.log`); refreshed packed runtime passes 12 and restores
  the headless deployment (`.mdo/E66-windows-program.log`). SDK/D8/DEX/native
  ABI/payload/private-provider/alignment/signature build passes
  `.mdo/E66-android-build.log`. Shared contracts, reviewed overlays, JNI transport
  and canonical owner helpers match the single deployment.
- Refresh existing development artifacts in place: Windows 6,883,440 bytes,
  SHA-256 `f5a65dd2753fdf7b6eb39630ad6886b15480cf5922ae55277d5e5a5011fbf050`;
  complete Android APK 6,362,433 bytes, SHA-256
  `4a980cf2e5011ed6e5b275def5d33c32f2f4fb5990ed002adbd0959983c9e6b7`.
  Plugin unchanged at 247,351 bytes, SHA-256
  `c6b74a2780f13d08570e61bdaf58cbcbc8c4678dc06ce0348de5e8a370ae5d41`.
- Goal remains active. Native payload/URI assignment, chooser callback identity,
  downloads and activation/profile policy still need implementation/qualification;
  installed provider/main-UI and Windows unique-context/realm audit remain open.
  Phone/current WebView deferred, Android automation/debugging disabled, formal
  data preserved. No model calls, external page browsing, extra app/Home/source
  copies or online release. Official Chromium source was consulted read-only for
  protocol/assignment research; no external page was operated by the browser.

## E67D — retain reviewed native event diagnoses without exposing provider text

- New chooser-event tests exposed that the protocol dispatcher flattened every
  native event rejection into `cdp_event_failed`, hiding specific ambiguity and
  source/arming failures. Add a typed internal event failure whose constructor
  accepts only six explicit reviewed static chooser codes. Retire the original
  stream and preserve that code and its original effect flag. Ordinary IOException,
  runtime/error and provider messages retain the existing generic sanitized path.
- The actual JVM/JNI/session/strict JSON dispatcher passes 4,310 checks across
  seven protocol sessions, including retained typed read-only failure, unchanged
  private-text masking, stream cleanup and refusal to construct an unreviewed
  code (`.mdo/E67-event-diagnosis.log`). The next chooser-event implementation
  uses this path and separately passes its initial 1,454 checks; that feature
  still awaits its final regression, host build and commit. Android provider
  qualification/admission remain false. No model calls or release.

## E67 — prove native chooser event sources and pin their original input nodes

- Request `Page.fileChooserOpened` reporting on every proved renderer session,
  including related iframes, without enabling CDP chooser suppression/interception.
  Add private typed arm/list/disarm/target commands. Acknowledged event listening
  is separate from an actual verified event. Preserve the last native frame route
  when an event precedes a getFrameTree reply, and require refreshed validation
  before consuming it. Do not infer frame ownership from URL/name/active frame.
- Bound the worker ledger to one original candidate/120-second monotonic lease.
  Repeated arm is idempotent and never extends that lease. Reject arming/duplicate,
  malformed/wrong-source, changed-document, expired and invalid-clock evidence
  without replacing the original candidate. Expose random scoped handles and
  aliases only. Disarm removes metadata/listening; no Android callback is cancelled
  or answered by this class, and no file data authority is minted.
- Resolve the event node on its native source into the original frame world;
  require empty-unique-context rejection, same-world argument proof and an exact
  native node description/backend match before fixed local pinning. Check the
  original chain, ancestor upload policy and candidate before/after preparation.
  Native protocol IDs/scripts/paths are not caller parameters or public results.
  Preserve explicit `assignment_supported:false` until original callback/URI
  delivery is implemented. Reuse E67D's reviewed event errors without forwarding
  arbitrary event/provider text.
- Actual Java/JNI/JSON/worker/frame/world/owner/event path passes 1,624 checks;
  the unchanged direct-file-input path passes 625 and dispatcher 4,310 across
  seven protocol sessions (`.mdo/E67-native-full.log`). DOM/event peers are
  synthetic and no Android callback is joined/no files delivered. Production
  helper strings also execute in Node VM tests. Full sequential Node passes
  306/306 in 21.682 s (`.mdo/E67-node-full.log`); Python passes 165/167 with two
  existing symlink skips in 64.774 s (`.mdo/E67-python-full.log`).
- Stop/apply reviewed overlays, verify canonical owner/JNI helpers, and build the
  full development Android APK with actual SDK/D8/DEX/ABI/payload/private-provider/
  alignment/signature checks (`.mdo/E67-android-build.log`): 6,366,529 bytes,
  SHA-256 `e40ca681586f10cf9d6134548ba7296985d5f0fb6f50b2416d8b5f050af10f9a`.
  Reuse unchanged verified E66 Windows 6,883,440-byte archive, SHA-256
  `f5a65dd2753fdf7b6eb39630ad6886b15480cf5922ae55277d5e5a5011fbf050`,
  and unchanged plugin, validating the current source/receipts rather than
  making another app copy. Restore the same headless deployment.
- Pinned official Chromium source research found that existing opaque URI
  basenames and header-bearing staging files cannot be treated as correctly named
  plain payload paths. Record source fingerprints in the chooser contract. The
  next stage must prove a private nonce-bound native callback/URI delivery and
  exact original DOM target; matching event/callback timing or URLs is insufficient.
  Downloads, activation/profile policy, installed-provider/main-UI verification
  and the Windows unique-context/realm audit also remain. Goal stays active,
  phone/provider/formal data unchanged and Android automation/debugging disabled.
  No model calls, external page operation, extra app/Home/source copies or release.

## E68 — private picker and native FileList handoff, shared Android script

- Add one native-controller-only isolated helper with a closed-shadow file input
  and a fresh 128-bit custom MIME nonce. Bind one exact original local DOM
  document/ref/input, initial File objects and name/size manifest. Keep the
  original deadline, single active reservation and bounded lifetime history.
  No nonce/node/paths/native protocol IDs enter Agent/UI results or evidence.
  Preserve explicit unsupported/cancel/expiry/target-change/cleanup and static
  activation failure results, without automatic effect replay.
- Retain the native browser FileList and deliver it through the native files
  setter with one explicitly synthetic input/change pair. Return local handoff
  verification only; a page changing its input during handlers is a dispatched,
  unverified partial outcome and stops further events. Do not reconstruct bytes
  as File/Blob or pass header-bearing staging/opaque URI paths to CDP assignment.
- Actual browser tests exposed that Blink shares FileList backing state: clearing
  the private input also cleared the real target, and comparing two live shared
  lists could miss a page clearing both. Fix both before integration: verify a
  pinned array of original File objects, and detach the private input onto a
  new empty native FileList at cleanup. Failed cleanup retains its poisoned
  reservation; success leaves the real target's selection intact.
- Generate the exact canonical installer into private Java, include it in the
  reviewed overlay/compile/DEX/input-hash checks, and execute that installer in
  the same native fixture. No added runtime/toolchain dependency or public raw
  script operation. Original Android ValueCallback/URI joining is still the next
  delivery stage, not implemented by the helper or inferred from event timing.
- Actual loopback WebView2 passes 30 focused checks and final full 254 checks
  (`.mdo/E68-windows-broker-only.log`, `.mdo/E68-windows-final.log`): native
  intercepted picker, page-world override/closed-shadow/nonce isolation,
  Unicode/delimiter name, all 131,075 bytes/SHA-256, exact untrusted event pair,
  no receipt replay, selection-preserving cleanup, page-reset partial result
  and zero native staging reservations/new crash diagnostics. Activation for
  this fixture comes from trusted WebView2 host evaluation; it does not prove
  Android Runtime.evaluate/userGesture or hardware-input activation.
- JS passes 319/319 in 21.053 s (`.mdo/E68-node-final.log`). Python passes
  167/169 with two existing symlink skips in 67.788 s; final generator checks
  pass again after helper identity handling changes (`.mdo/E68-python-full.log`,
  `.mdo/E68-codegen-final.log`). Actual Java/JNI/transport/worker/frame/world/
  input/capture/file/chooser gates pass (`.mdo/E68-native-full.log`), retaining
  explicit false device/provider/debug flags. SDK/D8/DEX/ABI/payload/private
  provider/zip alignment/v2/v3 signature build passes `.mdo/E68-android-build.log`.
  Full Android development APK: 6,370,625 bytes, SHA-256
  `45789ecf19ab2977d6fcb1a3771868253ade47531b4f4c945ad9471ae2b615c6`.
- Unchanged Windows E66 program/source/archive and plugin are reused after
  current-source/receipt verification. Restore the same headless deployment.
  Preserve earlier full-run failures in `.mdo/E68-windows-broker.log` (drag
  cleanup after child navigation) and `.mdo/E68-windows-full.log` (nested fill).
  These intermittent harness/input interruptions are not root-cause-resolved by
  the final pass; continue their diagnostics and the Windows unique-context/
  realm audit. Android original callback/URI delivery, downloads, activation/
  profile policy and installed-provider/main-UI qualification also remain open.
  Goal stays active; no phone/provider/formal data changes, model calls, external
  page operations, extra application/Home/source copies or online release.

## E69 — original private chooser callbacks and native read-permission delivery

- Add a UI-only private picker rendezvous and native SDK reply adapter. Route the
  reserved MIME namespace in the actual Chrome target, sharing normal chooser
  callback identities. Match the exact view, native callback, nonce/mode and
  capture/accept metadata; never join on URL/timing/current frame. Keep nonce,
  native callbacks, URI/path and object authority out of public results/commands.
- Require real queue-owned sealed read leases with identical scope, an original
  native-owner predicate and a successful frame/world verifier before answering.
  Freeze the source array and original deadline, clamped to file expiry. Schedule
  elapsed-time expiry on the UI uptime queue and retain weak exact-ID timers.
  Synchronous verifier success cannot grant before submission returns; callback
  duplication, stale proofs, clock/owner/scope/queue/epoch changes grant no data.
- Give native read leases a once-only plain-object picker claim. Another operation
  or gate cannot reuse/revoke a prior grant. Partial claim and URI publication
  failure revoke only newly claimed permissions. The native reply publishes
  private provider URIs from these exact objects, never caller strings/paths.
  Distinguish confirmed null cancellation during grant preparation from uncertain
  native response/completion; only uncertainty poisons a surviving native view.
- Ensure cancellation completion follows the original native callback's return,
  including metadata failure and reentrant close/cancel/destroy. No callback is
  answered twice. Add native lifecycle cancellation, pending/cleanup state and
  actual SDK/DEX/input-fingerprint inclusion. Keep generic chooser admission
  closed during the rendezvous; do not add a broad exemption or advertise upload.
- Real Java/file queue/store/read leases/provider/proxy pass 2,046 private-picker
  cases plus 418 existing URI cases (`.mdo/E69-private-picker-final.log`). The
  production Chrome adapter receives original callback objects, while Android
  types/system calls and the DOM verifier are doubles. Verify original Unicode/
  delimiter name and 131,075 bytes/SHA-256, prior grants surviving partial failure,
  zero-size, cancellation ACK ordering, mismatch/duplicate/late callback refusal,
  time/owner/thread changes, response/OOM/completion faults, no replay and 64/128
  resource bounds. Framework/device/URI-assignment qualifications stay false.
- Existing chooser controller/Chrome cases pass 471/42, manual chooser 554 and
  device resolver/FD/looper fixture 168 (`.mdo/E69-chooser-final.log`,
  `.mdo/E69-manual-chooser.log`, `.mdo/E69-uri-fixture.log`). JS passes 319/319 in
  19.666 s (`.mdo/E69-node-full.log`); final Python passes 167/169 with two existing
  symlink skips in 54.810 s (`.mdo/E69-python-final.log`). Actual SDK/D8/DEX/ABI/
  payload/private-provider/alignment/v2/v3 signature build passes
  `.mdo/E69-android-final.log`: complete development APK 6,374,721 bytes,
  SHA-256 `e217f27f8d78f7bd6b9d15a4cc7bc1cff3c9ac20cd2012ce2307d21e11745b80`.
  Reuse unchanged verified Windows E66 program and paired plugin, without extra
  app/Home/source copies. Restore the same headless development service.
- Still required: the original view/channel/epoch/action/file-source coordinator,
  its scoped CDP admission while ordinary input is blocked, actual native graph/
  world validation, private nonce extraction/opening and E68 DOM handoff/cleanup.
  Upfront source/input leases expire/cancel; separate assigned-data lifetime to
  satisfy retention across navigation/cancellation/staging expiry until confirmed
  release or browser close. Do not equate this component with a completed Android
  upload path. Windows realm audit/intermittent input diagnostics and Android
  downloads/profile/activation/installed-provider/main-UI gates also remain open.
  Goal stays active; no phone/provider/formal data changes, model calls, external
  page operation or online release. Android automation/debug admission stays off.

## E70 — keep assigned file data independent of transient input leases

- Mint a native-only `PickerGrant` after the original callback returns. The worker
  checks the exact queue, ordered complete sealed manifest, original source scope,
  private picker claims and current file hashes before returning provisional data
  retention. Native status/release use object authority, never JSON/restored IDs.
  Duplicate delivery preserves original ownership; equal numeric scopes and
  foreign queues/proofs/owners cannot adopt or release another operation's data.
- Add an explicit native adoption boundary immediately before future DOM dispatch.
  Preparation still expires/cancels with its source. After adoption, data survives
  input cancellation, navigation and staging expiry while consuming the existing
  file/byte budget. Confirmed release, physical browser-owner destruction or worker
  close revokes/reclaims it. Keep immutable cleanup receipts after ticket reaping.
  Data objects retain no native View/Activity/channel. Physical-owner/coordinator
  lifecycle wiring is still required; this stage does not enable product upload.
- Serialize transient picker cleanup with adoption; avoid revoking adopted reads
  through a stale pre-adoption check. Make UI result delivery one-shot even when
  an implementation posts a late task after refusing it. Drop/throw/OOM before
  native handoff reclaims provisional data, while existing ownership/adoption and
  read-only status survive failed duplicate observers or lost replies. Unknown
  assignment outcomes must be retained, not automatically replayed.
- `.mdo/E70-retention-complete.log` passes 418 URI/2,046 private callback checks
  plus 410 recorded retention assertions, including a real 131,075-byte Unicode/
  delimiter file/SHA-256, multiple/empty files, concurrency, scope/TTL loss,
  response faults, original authority and release/teardown. Android/system/DOM
  peers are doubles; renderer/URI assignment and framework qualification stay
  false. `.mdo/E70-recovery.log` passes 161 checks and 13 abrupt JVM exits,
  including adopted data after source cancellation. Recovery reclaims verified
  owned bytes without restoring prior native permission.
- Sequential existing native gates pass reads 318, worker 99, chooser/Chrome
  471/42, manual chooser 554, resolver/FD fixture 168 and upload UI fragment 39
  (`.mdo/E70-android_*_probe.log`). JS passes 319/319 in 26.752 s and Python
  167/169 with two existing symlink skips in 65.541 s (`.mdo/E70-node-final.log`,
  `.mdo/E70-python-full.log`). Initial `.mdo/E70-node-full.log` records loopback
  connection refusal from running JS after stop/apply; restart the same deployment
  and pass the full final run. Retain that failure as a test-ordering diagnosis.
- Stop/apply reviewed overlays and verify generators; actual SDK/D8/DEX/ABI/app
  payload/private-provider/alignment/v2/v3 signature checks pass
  `.mdo/E70-android-build.log`: full development APK 6,378,817 bytes, SHA-256
  `460157be0689c35ada10d990f6c9ab9db8205b242e055509035be0153d957ae5`.
  Reuse unchanged verified Windows E66 program and paired plugin. Restore the same
  headless service. No extra app/Home/source copies, phone/provider/formal data
  changes, model calls, external page operation or online release.
- Next: original frame/channel/epoch/action/file-source coordinator; its exact
  scoped CDP admission while ordinary input is fenced; fixed native prepare/open/
  status/commit/dispose and original world/ref preservation; physical data-owner
  lifecycle and conservative lost-effect recovery. Android downloads/profile/
  activation/installed-provider/main-UI gates and Windows realm/intermittent input
  diagnostics remain. Goal stays active and Android automation/debug admission off.

## E71 — execute private upload steps in their original native frame world

- Add a native-only fixed upload worker component with immutable original local
  document/ref/frame-chain/file manifest and original monotonic lease. Inspect
  the actual native file node and parent policy before/after preparation. Return
  only minimal step state; hand the nonce/mode to the original UI as a private
  native object, never JSON/Agent parameters, raw script or native IDs/paths.
- Pin the exact world manager/world object/unique context/native route and bind
  later steps to the same original connection/worker. Never create or adopt a
  replacement realm for status, assignment or cleanup. Foreign-worker/binding
  attempts cannot poison the surviving operation. Preserve existing unique-ID
  negative proofs and ordinary evaluation behavior. Only the fixed private
  opening expression uses `userGesture:true`; no caller flag can request it.
- Disarm native chooser event reporting after pinning the original target and
  before private opening. Keep open/assignment once-only, pending selection a
  bounded status wait, and partial synthetic-event results distinct from full
  verification. Retain possible-effect flags after time, native clock, ownership,
  realm or result loss. Cleanup ignores transient staging expiry/disabled
  ancestors but still requires the original realm, with no fake confirmation.
- Preserve immutable native handoff and cleanup receipts. Caller JSON mutation
  cannot change them; a handoff followed by worker timeout remains inspectable
  without another protocol call or effect. E70's original data grant must be
  adopted by the future UI coordinator before commit and retained on uncertainty.
  Generic worker exceptions retire bindings: attempt cleanup in the original
  operation when possible, without reconnecting to replace a lost world.
- Actual JNI/strict JSON/worker/graph/world/owner/chooser path passes 1,530 new
  private-upload assertions (`.mdo/E71-native-complete.log`), including five native
  frame routes, nonce/gesture isolation, source/realm/deadline/clock changes,
  partial results, activation/cleanup faults, pending selection, original receipt
  recovery and no effect replay. Existing dispatcher 4,310, graph 172, worlds 539,
  owners 451, command grammar 49, input 315, capture 161, file inspection 625 and
  chooser 1,624 checks pass, alongside ASan/UBSan native transport gates and API 26
  compilation. DOM/provider peers are synthetic; no original Android callback or
  URI/renderer assignment is joined and framework/provider qualifications stay
  false. Initial fixture compilation and unarmed-event assertions are retained in
  `.mdo/E71-native-initial.log`/`.mdo/E71-native-review.log`; final full gate passes.
- Stop/apply reviewed overlays and include the real worker/Ready/Receipt/world
  Pin classes in SDK/DEX/input-fingerprint verification. Full SDK/D8/DEX/ABI/app
  payload/private-provider/alignment/v2/v3 signature checks pass
  `.mdo/E71-android-build.log`: full development APK 6,382,913 bytes, SHA-256
  `b4d9d33a914aa653fa98cd0d0ac56819f599109f51478a32dc6bf7c34c4bb981`.
  Reuse unchanged verified Windows program/plugin. JS passes 319/319 in 19.819 s;
  Python 167/169 with two existing symlink skips in 64.158 s
  (`.mdo/E71-node-full.log`, `.mdo/E71-python-full.log`). Restore the same service.
- Still join the original native view/channel/epoch/action/file-source owner,
  Android callback, data retention and lifecycle, exact admission while ordinary
  input is fenced, early callback ordering and original chooser cancellation.
  Cleanup/gone/uncertain lifetime decisions must preserve potentially consumed
  data without replay. Android downloads/profile/activation/installed-provider/
  main-UI gates and Windows realm/intermittent input work remain. Goal stays
  active; no phone/provider/formal data changes, model calls, external page
  operations, extra app/Home/source copies or online release. Android automation
  and debugging admission remain off.

## E72 — retain the idle original chooser world for read-only continuation

- Diagnose a real integration gap: `file_chooser_open` input invalidation always
  destroyed the already bound CDP graph/world/event/ref state. Preserve that idle
  connection only for the exact original view/epoch/channel and clean, cancelable
  single/multiple native chooser. Keep ordinary input cancellation/release and all
  loading/visibility/manual/background/dialog/popup/capture/directory gates. An
  in-flight CDP job or posted UI result still retires the binding; ordinary work
  cannot continue behind the chooser. Other invalidation reasons are unchanged.
- Apply a narrow exception at the actual outer dialog gate and frame router for
  strict `chooser_list`/`chooser_target`/`chooser_disarm` metadata only, after input
  cleanup. No broad readiness exception, raw script/gesture/node/socket field,
  caller flag or debugging activation. Global product/profile admission remains
  above this gate. Cancelling a clean chooser resumes the same original realm.
- Add exact native registry lookup and worker submission that require the original
  existing binding object, with worker-side revalidation and no connector path.
  Apply it to every chooser continuation, even without a current UI callback,
  so missing/retired sources cannot recreate old candidate/target authority.
- Actual production admission/invalidation/router fragments, native chooser
  ledger and JVM/JNI/CDP path pass 357 scope checks, including five frame routes,
  original refs, pressed-input cleanup, ordinary effect/extra-field denial, missing
  bindings, channel/epoch changes, in-flight cancellation and other lifecycle
  losses. Registry/worker additions pass 388/184; existing dispatcher, graph,
  world/owner/input/capture/file/chooser and 1,530 private-upload checks pass
  `.mdo/E72-native-final.log`. UI surroundings and DOM peers are synthetic;
  native callback/event association, file/URI assignment and framework/provider
  qualifications remain false. No files are granted by this scope.
- Fix the native socket test's allocation fault fixture, not product code: a
  header/payload split could make a 30,000-byte message use only two grow sites
  (first 8,192-byte batch plus NUL rounds to 16,384), so fault three was sometimes
  never reached. Use a 66,000-byte fragmented synthetic message to force all three
  grows regardless of coalescing. Retain `.mdo/E72-native-initial.log` and fixture
  compile correction `.mdo/E72-native-review.log`; final ASan/UBSan and API 26
  gates pass without relaxing timeouts. Add E71's new Java class to reviewed and
  deployed source fingerprints, covering edits that DEX presence alone missed.
- Stop/apply reviewed overlays. Complete SDK/D8/DEX/ABI/app payload/private
  provider/alignment/v2/v3 signature checks pass `.mdo/E72-android-build.log`:
  full development APK 6,387,009 bytes, SHA-256
  `a3b4d97eb072f7a617f07be735824f6243d6fa29512d229ced5137b1f766d7c8`.
  Verify/reuse unchanged Windows executable and paired plugin. JS passes 319/319
  in 20.848 s; Python 167/169 with two existing symlink skips in 61.853 s
  (`.mdo/E72-node-full.log`, `.mdo/E72-python-full.log`). Restore the same service.
- Next implement the UI upload coordinator joining E69's original callback,
  E70's file-data retention and E71's fixed original-world steps: early callback
  ordering, exact source/operation identity, scoped private continuation,
  physical data-owner lifetime, cleanup/gone/lost-reply recovery. Android
  downloads/profile/activation/installed-provider/main-UI and Windows realm/input
  work remain. Goal stays active and Android automation/debug admission off;
  no phone/provider/formal data changes, model calls, external page operation,
  extra app/Home/source copies or online release.

## E73 — join original native upload steps, callback and file retention

- Add an internal UI coordinator over the existing file queue, private picker
  and fixed original-world CDP operations. Capture exact native objects, source
  scope/ticket, immutable target and original bounded deadline. Use existing-only
  worker submission throughout; never reconnect, replace a realm or replay an
  effect. A native assignment claim prevents a second coordinator from consuming
  or cancelling another coordinator's staged source. Claims have no JSON route
  and retain no view/channel/controller references in the file worker.
- Register the nonce-bound original callback before opening. Defer a callback
  arriving ahead of the native open receipt until that original operation has
  completed; verify through the original frame/world before releasing read
  authority. Wait for selected files, retain the original grant, adopt it before
  dispatch, and commit only once. Cancellation or an uncertain commit preserves
  adopted data and immutable handoff evidence; explicit release or the native
  data owner remains responsible for its eventual lifetime.
- Bound lost-result waiting. A missing provisional-retention UI reply cannot
  leave the coordinator waiting forever. Failed/dropped source cleanup replies
  yield an unconfirmed cleanup receipt after a bounded retirement wait. Reject
  pre-assignment data through the exact native claim; do not cancel an unrelated
  source. Keep native cleanup evidence separately from a lost worker reply and
  contain a throwing/OOM final UI callback without revoking adopted files.
- Combined actual JVM/JNI/frame/world/CDP/file-worker/native-callback-core gate
  passes 426 assertions (`.mdo/E73-native-final.log`), covering five frame routes,
  early callbacks, duplicate claims, wrong mode, native response/completion faults,
  partial dispatch, cancellation, expiry and dropped file/retention replies.
  Re-read real Unicode-named and empty files after input cancellation and original
  staging expiry; explicit release returns quotas to zero. The callback and DOM
  surroundings are synthetic; this is not installed-framework/URI/renderer
  assignment qualification. Existing native gates, ASan/UBSan and API 26 compile
  pass. Preserve `.mdo/E73-native-full.log`: its new dropped-retention fixture
  advanced a synthetic clock after worker cleanup but before UI timer registration.
  Wait for the actual UI timer before advancing; focused review passes 426 without
  relaxing product timeouts (`.mdo/E73-coordinator-review.log`).
- File URI/private-picker/retention gates pass 418/2,046/409 assertions, read 318,
  queue 99, exact staging UI 39 and recovery 161 with 13 abrupt JVM exits
  (`.mdo/E73-android_upload_*.log`). Register coordinator source in overlays, both
  reviewed/deployed build fingerprints and DEX verification. Stop/apply/check the
  same deployment; full SDK/D8/DEX/ABI/app/private-provider/alignment/v2/v3 signing
  checks pass `.mdo/E73-android-build.log`: APK 6,391,105 bytes, SHA-256
  `eb672e409a00330c791ea0deee9ff732ca3810e48655a40b02ccbc70a31f57d2`.
  Verify/reuse unchanged Windows executable and paired plugin. JS 319/319 in
  23.325 s; Python 167/169 in 64.923 s with two existing Windows symlink skips
  (`.mdo/E73-node-full.log`, `.mdo/E73-python-full.log`). Same service restored.
- Next wire the actual product route, exact per-view/channel/epoch operation and
  physical browser data-owner lifecycle; this class alone does not expose upload
  assignment. Public intercepted-chooser mapping, Android download/profile/
  activation/provider/main-UI qualification and Windows realm/intermittent-input
  work remain. Goal stays active; Android automation/debug admission stays off.
  Phone remains deferred by the user, preserving the current WebView/formal data.
  No model calls, external page operations, extra app/Home/source copies or
  online release.

## E74 — tie retained files to the actual physical browser lifetime

- Give each exact native WebView/browser generation a plain retained-file data
  owner, independent of its transient upload scope. Navigation, dialog/input
  cancellation, channel loss and background retirement of staging scopes keep
  that data owner alive. Only the UI map holds the view; the worker sees the
  existing native atomic lifetime flag. The registry is bounded to eight physical
  views and rejects aliasing or a replacement view claiming an unretired key.
- Wire exact physical retirement after native destruction in both the actual
  single-view close/crash path and complete-host shutdown loop. Retire only the
  original object, cancel its remaining transient scopes, close its data owner
  and let the existing file worker perform bounded cleanup. A stale close event
  cannot retire a new view reusing the browser key; a new owner cannot revive
  old retained leases.
- Extend the exact production upload-fragment/real-file-worker fixture from 39
  to 80 checks (`.mdo/E74-upload-ui-initial.log`). Construct a controlled native
  grant, adopt actual staged file bytes under the host's owner, cancel the scope,
  rotate the navigation and verify the new scope shares the same data owner.
  Actual file reads survive transient cancellation; foreign retirement and early
  key reuse do not revoke them. Exact physical retirement releases files/quotas;
  replacement owners, stale closes, aliasing and the eight-view bound are checked.
  This uses UI/JSON doubles, not installed-framework/provider qualification.
- Stop/apply/check the same host and rerun the native JVM/JNI/CDP/world/callback/
  file-coordinator/transport gates plus ASan/UBSan/API 26 compile
  (`.mdo/E74-native-full.log`). Full SDK/D8/DEX/ABI/payload/private-provider/
  alignment/v2/v3 signing pass `.mdo/E74-android-build.log`: APK 6,391,105 bytes,
  SHA-256 `3fcd7b9361f2464812426d41990f3707ecdf681f0182fb0ff670de6452aa4b2c`.
  JS passes 319/319 in 22.324 s; Python 167/169 in 101.179 s with the same two
  Windows symlink skips (`.mdo/E74-node-full.log`, `.mdo/E74-python-full.log`).
  The same headless service is restored, HTTP 200; reuse Windows/plugin outputs.
- Still wire the actual assignment route, original target/operation admission,
  ordinary-input fencing and retained ticket status/release across scope loss.
  The physical owner is ready but public upload flags and Android automation/
  debugging remain disabled. Intercepted chooser mapping, Android downloads/
  profile/activation/provider/main-UI and Windows realm/intermittent-input work
  remain. Goal stays active. Phone/provider/formal data remain untouched per the
  user's deferral. No model calls, external page operations, extra app/Home/source
  copies or online release.

## E75a — keep native upload steps on the original elapsed deadline

- Confirm a clock-domain bug in the upload coordinator: its UI/file scope used
  Android elapsed realtime, while the fixed native upload steps silently started
  a separate `System.nanoTime` deadline. Those clocks can diverge across device
  suspend; worker-side expiry must not be extended by a new clock domain.
- Pass the original native clock into `MdoBrowserCdpUpload`. Keep its source
  deadline clamp, same original frame/world guards, one-shot commit and cleanup
  rules. No caller flag or provider setting is introduced.
- Add a queued-native-expiry regression: the worker observes the original elapsed
  deadline passing before another UI admission sample. The old implementation
  performs page work and fails check 340 (`.mdo/E75-clock-before-fix.log`). With
  the shared clock, no picker opens or file commit occurs and staging is reclaimed.
  The complete real JVM/JNI/file/callback coordinator gate passes 444 checks,
  plus existing native/ASan/UBSan/API 26 gates (`.mdo/E75-native-complete.log`).
  UI/DOM peers remain synthetic; this is not physical suspend/Doze qualification.
- Commit this confirmed timing defect separately from the product upload route
  being integrated in E75b. The development APK still needs its next host rebuild;
  no release, provider, phone, formal data, model call or external page change.

## E75b — wire original native target/ticket assignment into the product host

- Join the actual host upload route to the existing native coordinator. Freeze
  each target with its original source, channel, already-bound marker and full
  native frame/document chain before accepting target-bound staging. Reject
  retargeting, including changing an unbound source into a bound one, replacing
  a connection or reconstructing authority from a ticket. Target reservations
  and native records are bounded to 64 per physical browser. No source path,
  script/gesture flag, URI, nonce, protocol node or socket is caller authority.
- One active assignment per native view. Ordinary commands and the frame router
  are fenced; native invalidation cancels the original operation. Navigation,
  takeover, stop and physical teardown retain their existing cancellation paths.
  A duplicate assignment returns its pending/saved receipt without opening another
  picker, reconnecting, granting files again or committing again. Close records
  over a snapshot because native cancellation can reenter another view's teardown.
- Keep native operation/retention records independently of transient input scopes.
  Status, cancellation and explicit confirmed release use the saved native object
  on the same physical view after navigation/channel reconnection, including in
  manual/background states. Cancellation preserves adopted data. Reject release
  while assignment is still pending; retained release remains idempotent. A query
  succeeds independently of a historical assignment failure (`assignment_error`/
  `assignment_ok`). Native replies retain original channel/view ownership; late
  metadata cannot publish as a replacement view's result.
- The exact product fragments, real JVM/JNI/frame/world/file queue and original
  native callback core pass 129 new integration checks, including five frame
  routes, mutated caller JSON, repeated assignment, pending input fencing, dropped
  product replies, cancelled/changed owners, original-byte consumption after scope
  expiry/reconnect, explicit release, teardown during native grant delivery and
  late status replies (`.mdo/E75-native-final.log`). Coordinator gate passes 444,
  chooser continuation 357 and existing native/ASan/UBSan/API 26 gates pass.
  Staging-only exact-fragment gate remains 80 (`.mdo/E75-upload-ui.log`). UI/DOM
  surroundings are controlled; original installed-framework/URI/renderer delivery
  is not qualified. The staging-only fixture stubs the separate assignment hooks;
  the new combined gate executes those exact hooks rather than claiming a stub
  is an integration result.
- Preserve initial fixture failures: `.mdo/E75-native-initial.log` omitted the
  strict agent field and expected an owner marker before its lazy cache opened;
  `.mdo/E75-native-review.log` reused a synthetic protocol engine already bound
  for another fixture. Start that peer unbound and use the actual host's original
  registry/worker. `.mdo/E75-host-focused.log` exposed an inaccurate busy reply
  while the private picker was pending; return the exact active-operation reason
  before generic document readiness. No product admission or timeout was relaxed.
- Stop/apply/check the same deployment. Full SDK/D8/DEX/native/app/private-provider/
  alignment/v2/v3 signature verification passes `.mdo/E75-android-final.log`: APK
  6,395,201 bytes, SHA-256
  `d82fd3fb61f2a71119d9bce456259b70c8dd80713f82d07ac9a1b47165f504e0`.
  The new fragment is covered by reviewed/deployed build fingerprints. JS passes
  319/319 in 20.561 s; Python 167/169 in 58.304 s with two existing Windows symlink
  skips (`.mdo/E75-node-full.log`, `.mdo/E75-python-full.log`). Same service restored,
  HTTP 200; unchanged Windows executable/paired plugin verified and reused.
- Product upload/file-input flags depend on the existing global qualification
  result, which remains false. No caller flag enables Android automation/debugging.
  Native chooser-to-callback mapping, clear/directory/download/profile/activation/
  installed-provider/main-UI work remains; URI/provider/renderer delivery is still
  unverified and the phone remains deferred. Keep the goal active. Also resume the
  known Windows realm audit and intermittent nested-input/drag evidence, rather
  than treating later green runs as a root-cause fix. No phone/provider/formal
  data changes, model calls, external page actions, extra app/Home/source copies
  or online release.

## W1 — recover a completed drag after positive original-source retirement

- Return to actual Windows reliability evidence. The unchanged native WebView2
  frame regression passes 254 checks (`.mdo/W1-frames-before.log`/`.json`); this
  does not explain away E68's intermittent nested-fill and child-navigation-drag
  failures. Keep those original failures open rather than attributing them to a
  later green run.
- Confirm an independent product defect: an unknown original drag-observer
  cleanup sets one undifferentiated failure flag, finishes cleanup, kills its
  timer and keeps the input slot indefinitely. A later positive native document
  replacement cannot release the inert job. Reproduce in the actual WebView2
  private world with a controlled observer-access failure, distinct endpoints and
  exactly one native drag submission. No press is delivered. After an actual
  child-document replacement, the old binary remains input-busy/cleanup-failed
  (`.mdo/W1-retired-source-red.json`, check 223). This reproduces the stuck-slot
  behavior; it is not evidence that all historical E68 failures share that cause.
- Separate observer cleanup uncertainty from unacknowledged native mouse/drag
  interception cleanup. Reap only a completed, non-in-flight job after all five
  cleanup phases, with no native cleanup failure and a positive original native
  source-loader replacement. Keep missing inventory, alias-only changes,
  unfinished steps and unacknowledged native cleanup fenced. Confirmed disposal
  of the original observer can also resolve observer-only uncertainty. Once the
  source is already retired, skip observer evaluation entirely; do not create a
  world or remove state in a replacement document.
- Reap inert state at the existing native command entry, with no new timer,
  protocol command, input, reconnect or gesture retry. Keep the original failure
  receipt/error; only an explicitly new action can use the new document. Existing
  actual-method C++ gate now covers 23 cases, including absent/alias-only sources,
  positive native loader replacement, in-flight/unfinished/native-failure fences,
  no protocol work and original-error preservation
  (`.mdo/W1-drag-watch-focused.log`).
- Rebuild the same native host (`.mdo/W1-native-build.log`). Actual WebView2
  regression passes 260 checks twice (`.mdo/W1-frames-fixed.json`,
  `.mdo/W1-frames-final.json`). Final structured evidence records one rejected
  native drag/no input effect, a busy unconfirmed fence, changed source document,
  recovered idle/clean state, and exactly one fresh explicit click. No native
  exception diagnostic or private upload reservation remains. Capture final input
  state before fixture teardown; do not mistake browser close for recovery.
  Preserve `.mdo/W1-retired-source-before.json`: the first fixture used identical
  endpoints and was correctly rejected before native submission. Distinct
  endpoints and an actual native-submission counter establish the real red test.
- Package in place with no app/Home copy (`.mdo/W1-package.log`). Windows preview
  6,883,440 bytes, SHA-256
  `aaa2b5acd6f8f558d5b6b4db65bb2e5ac8a90458756fc9e7c4b43eb03c5fd2c0`.
  The actual packed program/paired plugin passes all 12 runtime checks
  (`.mdo/W1-program-probe.log`); reuse the unchanged 22-file plugin. JS 319/319 in
  29.273 s; Python 167/169 in 82.274 s with the same two Windows symlink skips
  (`.mdo/W1-node-full.log`, `.mdo/W1-python-full.log`). Restore the same headless
  service, HTTP 200. Previous E75 Android APK remains unchanged/verified at that
  stage; this update changes only the Windows native host.
- Goal remains active. Next finish the Windows original-realm audit and gather
  actionable evidence for the remaining intermittent input failures, then resume
  outstanding Android/provider/platform work. Do not claim permanent resolution
  of historical flakes or full platform qualification. Phone/provider/formal data
  unchanged; no model calls, external page actions, extra app/Home/source copies,
  online release or unchanged desktop-visual-gate retry.

## W2 — bind original root execution to native unique context identities

- Continue the Windows realm audit with a scoped product change. The classic
  root `automation` path previously created a fixed named world and selected
  execution by a cached numeric context ID. Across renderer/process reuse,
  numeric metadata cannot identify the originally admitted execution realm.
  Retain an explicit native context object with a private randomized name,
  native frame/loader and unique context identity. Subscribe to root Runtime
  lifecycle events before enabling Runtime, correlate the creation reply, then
  require both a rejected empty-unique-ID probe and successful original-unique-ID
  probe before evaluating any caller script. Use the original unique ID for
  dispatch and verify the same live pin at callback delivery. Never map an old
  numeric ID to a new context, use the page/default world, retry the action or
  report an uncertain delivered effect as a confirmed success.
- Bound preparation to three seconds/32 waiters, 32 retained names and created
  names per original document, and 1,024 names/unique identities per view.
  Preserve original-response/download DOM lifetimes: reuse only a surviving
  original world after fresh matching nonempty native loader/frame evidence,
  not merely a navigation token or URL. Close drains waiters/listeners/timers;
  timer allocation/dispatch faults and throwing reply deliveries cannot replay
  or abandon other waiting callers. Build fingerprints cover the new reviewed
  native state/helper files. Public C/JS signatures and plugin content unchanged.
- Preserve `.mdo/W2-native-full-failure.json` and `.mdo/W2-native-full.log`:
  the first W2 candidate over-rejected normal same-origin child traversal on
  its first structured observation (check 18, no native exception diagnostic).
  Real WebView2 instantiates this named world in a same-origin child when its
  WindowProxy is accessed. Those native frame identities cannot authorize or
  overwrite the requested root pin. Ignore them for root admission; accept an
  identical native refresh only for a still-live original object. Different
  unique identity on an admitted name and attempted retired-pin revival remain
  rejected. The fixed actual full gate records `other_frame_contexts:1`,
  `original_context_refreshes:0`, and a healthy original unique root binding.
  This was an integration failure in the new W2 candidate, not an attributed
  cause of the historical E68 failures.
- `.mdo/W2-worlds-focused.log`: compile actual registry, preparation, deadline
  and caller-dispatch methods with the pinned JSON parser; 45 controlled checks
  pass. Inject numeric ID reuse before its native event arrives: the original
  unique ID is rejected without effects, world creation or fallback. A separate
  numeric-only negative control redirects into the replacement exactly once,
  demonstrating that the fixture exercises the risk. Also check fresh numeric
  reuse after retirement, stale destruction/late replies, same-name child/refresh,
  rebound identity, limits, absent/late metadata, unsupported probes, close,
  original-response reuse, native-timer allocation fault and throwing waiter.
- Final native build `.mdo/W2-native-frame-events-build.log` uses the same source,
  objects and Home. Actual WebView2 passes 14 focused root checks
  (`.mdo/W2-root-current.log`/`.json`), 1,376 full browser checks
  (`.mdo/W2-native-frame-events.log`, `.mdo/W2-native-full-fixed.json`) and 260
  frame checks (`.mdo/W2-frames-final.log`/`.json`). Native upload/download caches
  have zero reservations and no new exception diagnostics. The prior W1
  original-source drag recovery witness still passes. Cross-site navigation and
  reload retire the old root state; page-world code cannot access its private
  isolated state. Actual numeric reuse is separately fault-injected, not claimed
  as an observed renderer reuse in these native navigation runs.
- Package in place (`.mdo/W2-package.log`): Windows preview 6,902,384 bytes,
  SHA-256 `03317793d7d0ae865ed0b546a4814ecb7de727583b99f305403cc25a3f03471f`.
  Inventory verifies all 750 files; actual program/paired unchanged 22-file
  plugin passes all twelve runtime checks (`.mdo/W2-program-probe.log`). JS
  319/319 in 19.557 s; Python 168/170 in 64.943 s with the same two Windows symlink
  skips (`.mdo/W2-node-full.log`, `.mdo/W2-python-full.log`). Restore the same
  headless service. No formal data/provider/WebView change, model call, external
  page action, extra application/Home/source copy or online release.
- Keep the goal active. This stage migrates root caller evaluation only.
  Frame evaluation/focus/owner, drag-observer cleanup and upload/chooser execution
  still need their original-realm migration; historical intermittent nested
  input failures remain unattributed. Android stays at E75 with phone validation
  deferred by the user; provider/profile/URI/main-UI/clear/directory/download
  qualification remains outstanding. Do not retry the unchanged desktop visual
  gate or claim complete platform qualification.

## W3a — preserve routed protocol failures and own callback references

- During the Windows original-realm migration, actual cross-process reads
  rejected unique-context qualification (`.mdo/W3-frames-first.json`,
  `.mdo/W3-frames-cdp-error.json`, isolated-read/check 16). Root and same-origin
  reads had succeeded. The routed `xb_frame_cdp` callback erased every failed
  HRESULT's response body before its caller could recognize the expected native
  protocol rejection. Keep the original bounded error body with `ok:false`;
  oversized UTF-16 or expanded UTF-8 output is replaced with an empty failure
  receipt. No protocol failure becomes success and no raw protocol receipt is
  added to a public tool result.
- Compile the actual routed completion/dispatch, COM callback class, JSON parser
  and callback owner. `.mdo/W3-frame-cdp-red.log` reproduces loss of the original
  error. The corrected real WebView2 passes 260 frame checks
  (`.mdo/W3-frames-cdp-fixed.log`/`.json`) with no new native diagnostics and zero
  private-upload reservations. The surrounding realm migration is still pending
  and is not established complete by this one gate.
- A second compiled fault reproduces callback/reference leakage when parameter
  conversion throws after callback allocation (`.mdo/W3-frame-cdp-owner-red.log`).
  Use the existing COM reference owner for the dispatching caller, independently
  of any provider-held reference. Pre-acceptance conversion/dispatch exceptions
  release the callback; an already accepted provider callback retains its
  original owner until the provider releases it. Preserve once-only completion
  even if receipt delivery throws or a duplicate callback arrives.
- `.mdo/W3-frame-cdp-fixed.log`: 17 actual-method checks pass, including routed
  source identity, failed-HRESULT and protocol-error bodies, missing sources,
  rejected dispatch, UTF-16/UTF-8 limits, absent output, throwing/duplicate
  delivery, conversion allocation fault and provider exceptions before/after
  acceptance. Final integrated native build/runtime qualification and the
  original-world migration remain in progress. The W2 downloadable program and
  E75 Android build have not been replaced at this substage. Goal active; no
  provider/phone/formal data change, model call or online release.

## W3b — migrate frame, focus, drag and file scripts to original unique worlds

- Extend W2's original-context objects across all existing private isolated
  script paths: frame evaluation, parent-owner functions, keyboard focus,
  retained drag observers, file-input assignment/clear/verification and chooser
  targeting/cancellation. All call sites share the same admitted frame world
  rather than independently creating a fixed named world. Native numeric IDs
  remain metadata for the DOM resolver, not script execution selectors. Node
  functions qualify and select their original unique context and receive opaque
  native objects as arguments. Keep public C/JS signatures and the 22-file
  plugin unchanged; no new runtime dependency or raw-ID capability is added.
- Give each OOPIF native-session incarnation an independent identity object.
  Scope metadata, numeric reuse and execution to that object; original cleanup
  cannot dispatch into a replacement session merely because its string ID
  matches. Positive native frame/loader replacement and source detach retire
  their original pins/caches. Preserve saved failed receipts and never replay
  an input, drag, assignment or chooser action.
- `.mdo/W3-loader-red.log` reproduces a new admission race in the first W3
  candidate: the child already has a replacement loader but its event has not
  updated the UI-side guard. A fresh unique world alone can then belong to the
  new document. After unique admission probes, reread that original source's
  native frame tree and require the captured raw frame/nonempty loader before
  any caller script. Missing, changed, duplicate or over-budget frame evidence
  fails without adopting the current page. This controlled red/green gate is
  not a retrospective attribution of historical E68 failures.
- Runtime reporting loss blocks new effects but does not establish world/DOM
  teardown: the upstream V8 reset/restore code can clear reporting state while
  contexts survive. Preserve an explicit inventory-lost state. Only exact-key
  observer removal may use the still-original qualified unique pin; it creates
  no world or input and requires a confirmed original result. Actual context /
  loader / source retirement skips that evaluation. W1's fully drained native
  cleanup and no-unacknowledged-input conditions still gate inert drag recovery.
- Bound coalesced preparation/deadlines, source/cache/document/name counts and
  retained callbacks. Snapshot at most 33 pending root/frame jobs in a fixed
  local array rather than allocating in the deadline timer. Expiry releases
  guards/waiters even while a provider holds its late callback. Shared source
  dispatch uses W3a's bounded, owned callback helper for root and OOPIF calls.
- `.mdo/W3-native-shadow-failure.json` preserves the first full candidate's
  check-339 failure in its Shadow/frame file-input lifecycle. A blanket root
  document/prototype test was too restrictive for existing same-origin child
  targets. File calls now revalidate the original isolated runtime's exact
  document/ref/node, including its captured owner document. Chooser calls use
  bounded original document ancestry. Keep the strict selected-parent document
  rule for frame owners. Do not declare existing iframe uploads unsupported as
  an alternative to preserving their original binding. Add a focused full
  file-input lifecycle runner using the same deployment/Home.
- Controlled gates: `.mdo/W3-worlds-focused.log` compiles actual registry,
  admission, deadline, source routing and node dispatch methods with the pinned
  JSON parser; 80 checks pass with zero wrong-realm effects. Include equal numeric
  IDs in different sources, source-string reuse, lost/late metadata, native loader
  race, function-selector qualification, exact nested/duplicate-frame evidence,
  manual takeover, 32 pending preparations, deadline guard release and late
  callback suppression. Actual focus tests include retired/late-retired worlds;
  drag observer tests cover 27 cleanup/recovery branches, including reporting
  loss versus confirmed retirement. W3a's 17 callback fault checks still pass.
- Rebuild the same native objects/host (`.mdo/W3-native-node-build.log`). Actual
  WebView2 passes 14 root checks (`.mdo/W3-root-final.log`/`.json`), 470 file-input
  checks (`.mdo/W3-file-input-fixed.log`/`.json`), 1,375 full browser checks
  (`.mdo/W3-native-final.log`/`.json`) and 260 frame checks
  (`.mdo/W3-frames-final.log`/`.json`). Counts in the full lifecycle include
  bounded polling assertions. No new native diagnostics or upload/download cache
  reservations remain. The original W1 controlled stuck-drag recovery remains
  valid. Preserve the initial isolated-read failure and W3a's error-receipt red
  evidence; do not treat failed candidates as green qualification.
- In-place packaging `.mdo/W3-package.log`: Windows preview 6,933,616 bytes,
  SHA-256 `053677eedaa63352b836630f162d588964061cf8d99d29386344d7d3ef954d69`.
  All 750 inventory files and the unchanged paired plugin verify; actual packed
  program passes all twelve runtime checks (`.mdo/W3-program-probe.log`). JS
  319/319 in 23.586 s; Python 169/171 in 71.043 s, with the two existing Windows
  symlink privilege skips (`.mdo/W3-node-full.log`, `.mdo/W3-python-full.log`).
  Restore the same headless service, HTTP 200. No extra app/Home/source copy,
  provider/phone/formal data change, model call, external page action or online
  release. Android remains at E75 and user-deferred phone/provider qualification.
- Goal active. The isolated-script migration now covers the existing Windows
  paths, but the historical intermittent nested-input failures remain
  unattributed. Continue current-state review of recovery/error classification
  and any actionable Windows evidence, then outstanding Android provider/profile/
  URI/main-UI/clear/directory/download work. Do not infer full goal completion,
  retry the unchanged desktop visual gate or count unavailable platform evidence
  as a pass.

## W4 — diagnose persistent world failures without replaying effects

- Current-state review confirms the dispatcher ignored persistent native world
  state and collapsed a first failed root admission into a generic read error,
  potentially advising another observation even though that physical view cannot
  clear the failure. Add a narrow allowlist of the ten retained native reason
  codes. Distinguish retained-world quota exhaustion from unavailable original
  identity/event/provider verification. Return manual-control/explicit-close
  advice with `automatic_retry:false`, without claiming one failed qualification
  proves permanent provider incompatibility. A missing initial pin is normal;
  unknown strings are neither echoed nor used as authority.
- Do not close/reopen/reload, retry a script or resubmit an action. The catch path
  can read only the original scope's guard to diagnose the first recorded error.
  Cancellation, changed scope, manual takeover and existing modal/popup/picker
  boundaries keep priority. Previously dispatched effects keep
  `action_uncertain` and their true uncertainty flag. Leave native metadata,
  upload status/confirmed release, download and publication paths available under
  their original controls; no raw native IDs or new C/JS signatures are exposed.
- `.mdo/W4-recovery-red.log` preserves the pre-fix public-dispatch red evidence:
  ignored known permanent view state, generic first-admission failure, and a
  successful-looking controlled response after the first input lost its world.
  These are controlled native-state peers, not new historical/provider fault
  attribution. Seven focused groups exercise all ten reasons over five page
  operations, first failure, after-input uncertainty, explicit cleanup, pending
  boundaries, unknown/absent metadata and lost original scope. Together with the
  existing dispatcher/Android integration cases, 62/62 pass
  (`.mdo/W4-recovery-focused.log`).
- The actual C operation/UI broker test passes original receipt recovery for
  proven no-effect and uncertain-effect records: exact reason/advice survive
  get/repeated-submit/result, with no action requeued (`.mdo/W4-receipt-focused.log`,
  4/4). Add the matching public JS recovery assertion: inspect the original
  operation only, retain details/uncertainty and never submit it again. The SDK
  file passes 7/7 (`.mdo/W4-sdk-recovery.log`).
- Full JS 326/326 in 19.291 s before the additional SDK-only test; that test and
  its six unchanged companions subsequently pass. Full Python 170/172 in
  55.846 s with the same two Windows symlink-privilege skips
  (`.mdo/W4-node-full.log`, `.mdo/W4-python-full.log`). Apply reviewed host files
  only after stopping the same service; no native binary rebuild is needed.
  Actual WebView2 passes all fourteen original-world checks with no new native
  diagnostics (`.mdo/W4-root-native.log`/`.json`).
- In-place Windows preview: 6,934,261 bytes, SHA-256
  `26bc29244e682f42cbbc30ee1658f652ac9b2494b334f045ea30ff182fa13481`;
  `.mdo/W4-package.log`. All 750 inventory files and the unchanged paired plugin
  match; the actual packed artifact passes all twelve C/JS/native runtime checks,
  including input/capture and explicit page-error navigation recovery
  (`.mdo/W4-program-probe.log`). Restore the existing headless service, HTTP 200.
  No extra application/Home/source copy, formal data/provider changes, model
  calls, external page action or online release. Android stays at E75; phone
  testing and provider update remain deferred at the user's request.
- Goal active. The historical intermittent nested-input failures still lack
  complete attribution. Continue outstanding Android chooser mapping, clear,
  directory/download/profile/activation integration and provider/main-UI/URI
  qualification. Controlled peers and packaging cannot qualify the installed
  Android framework; do not count unavailable evidence or the unchanged desktop
  visual gate as passing completion.

## E76a — fixed original-target Android file-clear worker

- Advance the missing native clear path while keeping the actual product scope
  visible. The pre-change actual grammar run rejects `file_clear`
  (`.mdo/E76-clear-red.log`). Add an immutable effectful native frame/document/ref
  operation using the existing native worker, frame chain, original unique
  world, opaque node and enabled ancestor checks. It cannot run as read-only
  chooser continuation and accepts no paths/files/script/CDP selector parameters.
- Only the fixed native method sends an empty file list to its original opaque
  node. Recheck the original ancestry/world before and after that step. A fixed
  original-world function confirms the same connected runtime ref/document using
  the native FileList getter, not a shadowing wrapper property. A nonempty list,
  replaced target/document, missing/failed getter, malformed native result or
  object-group cleanup failure does not become a confirmed clear. Preserve
  conservative effect uncertainty after worker entry; never replay the command.
- Do not expose an unfenced new product mutation through the ordinary frame
  reader. The exact product router explicitly rejects this primitive before
  native binding with `cdp_file_clear_adapter_incomplete`. The next adapter must
  join UI mutation reservation, retained results/cancellation/cleanup and the
  shared upload-clear route. Global Android automation/provider qualification
  and `file_clear_capability` remain false. This is staged implementation, not
  completion or a substitute for the requested working product clear tool.
- The real JVM/JNI/native transport plus production grammar/frame/world/ancestor
  methods pass 287 clear checks (`.mdo/E76-native-final.log`/`.json`). Include all
  five frame/source routes, early wrong-node/world/input checks, navigation at
  each inspection boundary, ancestor changes before/after assignment, native
  failure/nonempty reply, timeout/cancel, mismatched final proof and failed
  cleanup. No failed clear is resent. The actual product router fragment also
  proves that its incomplete adapter cannot create a binding or dispatch clear.
  Original inspection 625, request grammar 49, chooser continuation 357, upload
  coordinator 444 and product upload 129 remain green, with ASan/UBSan, actual
  socket transport and Android API 26 compilation. DOM/framework peers remain
  controlled; no physical provider, URI delivery or native file-event claim.
- Execute the actual fixed status/inspection strings in JS: native getter versus
  shadowed file properties, changed original ref/document/node, missing and
  throwing getters. Seven combined cases pass
  (`.mdo/E76-clear-script-final.log`). The first full JS run exposed the older
  test's dependence on the next Java field as its constant boundary; preserve
  `.mdo/E76-node-initial.log` and extract the exact string initializer while
  retaining its strict literal grammar. Full JS 330/330 in 19.575 s and Python
  170/172 in 61.624 s pass, with two existing Windows symlink privilege skips
  (`.mdo/E76-node-full.log`, `.mdo/E76-python-full.log`).
- Apply reviewed files only after stopping the same service. The old ARM64 reuse
  receipt correctly refuses the changed native source-tree identity
  (`.mdo/E76-android-build.log`); rebuild in the same output directory rather
  than relabeling it. The verified library is still 3,547,088 bytes, SHA-256
  `a21f1120fbaae36bcaa29a472e9eae3a61248a5ca270968fd62b1a677f6e7d90`
  (`.mdo/E76-android-native.log`). Complete SDK/DEX/ABI/manifest/provider/alignment/
  v2-v3 signing succeeds (`.mdo/E76-android-final.log`), including an explicit DEX
  definition check for the new BeforeClear interface. Current development APK:
  6,399,297 bytes, SHA-256
  `c8932aae32a2fe1eaba9ff35316f70e341517a76a4d92c6407296e855962adc6`.
  Reuse the verified W4 Windows archive and unchanged paired plugin, existing QA
  package/signer/installation serial, native output and single Home/source.
- Goal active. Next complete the native product clear reservation/receipt adapter,
  then intercepted chooser mapping and remaining directory/download/profile/
  activation and installed provider/main-UI/URI verification. Historical Windows
  intermittent input failures remain unattributed. Phone testing is still
  deferred at the user's request; keep the existing WebView and formal data.
  No model calls, external page actions, new application copies, extra browser
  engine, online release or unchanged desktop visual-gate retry.

## E76b1 — retain running native work until cancellation actually drains

- While joining the product clear mutation reservation, confirm that worker
  cancellation could post its terminal callback and remove its pending slot
  before the still-running native operation returned. The real worker regression
  holds that operation in flight, invalidates its original marker, drains UI
  posts and catches the early receipt/pending release
  (`.mdo/E76-drain-red.log`). This is a controlled worker-lifetime defect, not
  attribution of the historical Windows input failures.
- A successfully dequeued cancelled task can complete immediately with no native
  work. A running or just-dequeued task must finish through its original
  `run().finally`. Keep its pending slot and callback until that boundary; a
  posted older result is still converted to cancellation on original UI delivery.
  Never run another operation or replay an effect as cancellation recovery.
- Actual JVM/JNI worker gate passes 186 checks (`.mdo/E76-drain-fixed.log`), including
  queued cancellation, bounded capacity, in-flight cancellation, late bytes zeroed
  exactly once, original scope loss and callback failures. Complete native
  transport/frame/world/input/capture/chooser/upload/coordinator/product-fragment
  regressions remain green (`.mdo/E76-drain-native-full.log`). ASan/UBSan and API 26
  compilation pass; DOM/UI peers remain controlled and provider/framework false.
- Commit this independent bug fix before continuing the product clear adapter.
  The current APK is still the previously verified E76a artifact; package the
  source fix with the upcoming adapter rather than relabeling that old binary.
  Keep the single deployment, deferred phone/current provider and formal data.
  Goal active; product clear reservations, native cancel/status/recovery and
  remaining Android/Windows qualification are still required.

## E76b2 — keep a cleanup failure visible after an earlier clear failure

- The paired fault gate confirms that a malformed native clear response followed
  by failed original object-group release reported only `file_clear_unconfirmed`.
  That hid the cleanup failure from the upcoming product mutation fence
  (`.mdo/E76-cleanup-priority-red.log`). Give failed disposal priority, retaining
  the primary exception as its private cause. Keep timeout/cancellation codes
  when the original cleanup attempt itself expired or was cancelled. No retry,
  new world or replacement source is introduced.
- Actual worker/JNI/graph/world/file-input clear checks now pass 293 and original
  inspection 625 (`.mdo/E76-cleanup-priority-fixed.log`,
  `.mdo/E76-cleanup-inspection.log`). The in-progress product adapter's 232-case
  gate also proves that either standalone or paired failed cleanup remains
  fenced (`.mdo/E76-clear-host-focused.log`); that adapter is committed separately.
  DOM/UI peers are controlled, not installed provider evidence.
- Stage only this independent production fix and its paired-failure regression;
  preserve the product adapter work in progress. The previously verified E76a
  APK is unchanged and does not yet include E76b1/b2 source fixes. Package them
  with the adapter after its final gates. Goal active, phone/provider deferred.

## E76b3 — join native clear to product reservation and original receipts

- Connect the E76a primitive through the existing shared upload clear payload,
  using its original UI action `upload_key`, exact document/ref/frame chain,
  physical view/data owner, channel and existing native marker. No caller CDP
  selector, implicit connect, file staging/grant or new C/JS public signature.
  Keep a single bounded 64-record key index with generated ticket lookup over
  those same records. Released records remain no-replay tombstones. Failed
  publication cannot leave a running operation or claim an unregistered ticket.
- Join clear to the product `upload_busy` reservation and existing command fence.
  Refuse existing input and busy/unclean captures using the actual production
  capture predicate. Include the actual predicate in the product test fragment.
  Only the dedicated upload route admits clear: the ordinary frame reader now
  explains `file_clear_requires_upload_route`, without claiming the implemented
  adapter itself is missing. Capability still follows the globally qualified
  Android adapter; the actual APK remains globally unavailable.
- Freeze target arguments and original result provenance; verify native frame /
  frame-document/local-document/ref and final state again at completion. Same key
  reads its pending/saved original result, changed arguments conflict and no
  effect is replayed. Query/cancel by key or ticket, and confirmed ticket release,
  work without new page execution/provider admission. Historical failure is
  separate from successful record queries. Clear owns no file bytes and cannot
  implicitly release earlier assignments. Physical view retirement removes that
  owner's records rather than attributing late replies to a replacement view.
- Integrate navigation/channel/takeover/background/close cancellation with the
  original upload scope. E76b1 keeps a running native job pending until it drains;
  only then can its result release the reservation. The original failed key stays
  historical after a fresh observation and an explicit new clear. E76b2 gives
  failed disposal priority even when native clear already failed, with private
  primary cause retained. Keep that cleanup fence visible before a failed binding
  lookup can hide its reason. Reject new input after unconfirmed cleanup.
- Initial product log preserves the cleanup-fence ordering failure
  (`.mdo/E76-clear-host-initial.log`). The resume check also exposed a controlled
  peer's one-connection namespace assumption, hidden by protocol error conversion
  (`.mdo/E76-clear-resume-diagnostic.log`). Record test-only dispatch assertions,
  scope namespace assertions by connection and require new connections to use
  different namespaces. Track proof/inspection/clear/status per original object
  group, so a cancelled first clear need not have reached verification. Do not
  weaken same-connection/original-object proof or label fixture faults as a real
  provider cause (`.mdo/E76-clear-resume-fixed.log`).
- Final exact JVM/JNI/transport/worker/frame/world/parent/product gate:
  `.mdo/E76-product-native-final.log`/`.json`, core clear 307, product clear 234,
  worker 186, file inspection 625, upload coordinator 444 and product upload 129.
  Cover all five frame routes, repeated/pending/conflicting keys, dropped replies,
  source changes, cancellation, clean explicit resume, native/verification/cleanup
  faults, required confirmation and bounded records. Source-file reads/grants are
  zero in the clear gate. ASan/UBSan and API 26 compilation pass. Framework/DOM
  peers are controlled; provider, installed UI/URI and file-event claims stay false.
- The existing shared JS clear path accepts the Android-style native ticket /
  drain/result/error fields without source reads (24 focused cases). The actual
  C UI broker preserves and rereads that result without requeueing (3 cases):
  `.mdo/E76-clear-shared-js.log`, `.mdo/E76-clear-shared-c.log`. Full JS 331/331 in
  19.572 s; full Python 171/173 in 71.955 s, with the two existing Windows symlink
  privilege skips (`.mdo/E76-product-node-full.log`,
  `.mdo/E76-product-python-full.log`).
- Apply reviewed sources only with the single service stopped. Build the complete
  Android development APK, reusing the verified ARM64 host and W4 Windows archive
  (`.mdo/E76-product-android.log`). Require actual `MdoBrowser$ClearAction` and
  BeforeClear DEX definitions, ABI/provider/manifest/alignment/v2-v3 signatures.
  Current APK: 6,403,393 bytes, SHA-256
  `085f53278abfb5188d60360cc3a05a42b71ef22f86968372708aef3d8949e518`.
  Same QA package/signer/serial, unchanged paired plugin/Windows artifact, one
  source/Home/native output; no phone install/provider update/model/external
  page/formal data change or online release. Restore headless HTTP 200.
- Goal active. The product clear source path is joined; qualify it on the actual
  Android provider/main UI when the user resumes phone tests. Continue intercepted
  chooser association and remaining directory/download/profile/activation work,
  plus actionable Windows evidence (historical intermittent input faults remain
  unattributed). Do not replace missing device/visual evidence with these peers,
  enable the global gate early or repeat the unchanged desktop visual failure.

## E77a — preserve the production worker deadline across device sleep

- The actual default CDP worker still measured admission/queued work with
  `System.nanoTime`, although the upload coordinator already uses elapsed
  realtime. Android documents that nanoTime stops in deep sleep whereas
  elapsedRealtime includes it. A queued input could therefore start after its
  original elapsed deadline on resume. Use the platform elapsed clock in the
  production constructor; retain explicit private test clocks.
- Add a deterministic production-constructor gate: advance only the controlled
  Android elapsed clock after admission. The prior worker fails check 188
  (`.mdo/E77-worker-clock-red.log`); the fix returns `cdp_worker_timeout` before
  discovery/binding/marker evaluation or effect dispatch. No actual sleep,
  phone/provider behavior or historical failure cause is inferred.
- Focused worker 191 and the complete sequential native/JNI/frame/world/upload/
  clear/ASan/UBSan/API 26 gates pass (`.mdo/E77-worker-clock-fixed.log`,
  `.mdo/E77-worker-native-full.log`). Same output/Home/source, no phone/model/
  external page action. This source fix is not yet packaged into the E76b APK;
  audit the lower native clocks and package the follow-on stage together.
- Goal active. User explicitly defers phone connection/provider changes; leave
  global Android automation disabled and keep all device qualification false.

## E77b — expire native sessions, bindings and continuations after suspend

- Extend E77a's platform elapsed clock to private protocol sessions, native
  binding/marker waits, isolated-world setup, native chooser expiration and the
  standalone upload worker default. Do not change caller-injected clocks or UI
  scheduler contracts. Marker wait uses short uptime waits and rechecks elapsed
  time before accepting even an already delivered callback. Discovery must
  still have budget before asking the UI for a marker.
- An idle event poll remains a normal empty result. A packet received after its
  original poll deadline is refused before decoding or delivering its event;
  close the original stream, with no event replay or implicit replacement.
- Preserve two independent failing regressions: a late controlled poll event
  previously reached native event state (`.mdo/E77-session-poll-red.log`), and an
  actual JVM/JNI/protocol session using the production clock accepted a response
  after a controlled elapsed-time jump (`.mdo/E77-session-platform-red.log`).
  Add default-clock discovery, chooser expiration and upload-expiry checks,
  including no target resolution, picker preparation or file commit after expiry.
- Complete sequential native gate passes `.mdo/E77-java-elapsed-full.log`/`.json`,
  including eight real local protocol sessions, actual Java/JNI/worker/graph/
  world/clear/upload code, controlled Android/DOM metadata and source files,
  ASan/UBSan and Android API 26 compilation. The fake Android clock normally
  advances with JVM time and supports explicit frozen/jump cases; it is never
  packaged. No installed-provider, actual sleep/Doze or framework qualification.
- Source reference: https://developer.android.com/reference/android/os/SystemClock
  (uptime/nanoTime exclude deep sleep, elapsedRealtime includes it). Goal active;
  audit the kernel transport's clock/waits next, then rebuild the same development
  APK. The E76b APK and global unavailable Android qualification remain unchanged.

## E77c — use the boot clock for private socket and WebSocket waits

- The C discovery/WebSocket transport shared `CLOCK_MONOTONIC`, which excludes
  suspend, and accepted poll readiness without rechecking its absolute deadline.
  Align its clock with Android elapsed realtime using `CLOCK_BOOTTIME`. Keep
  relative poll waits to at most 20 ms and recheck the original boot deadline
  before reporting readiness. Interrupted waits keep that same deadline.
- The exact transport's controlled elapsed-clock jump rejects the previous code
  (`.mdo/E77-socket-clock-red.log`); the fixed gate covers elapsed expiry, late
  readiness and EINTR without renewing the budget. Clock/poll jumps are controlled,
  while the existing kernel socket/peer-credential tests remain real. No device
  sleep or provider behavior is claimed.
- Socket 317 with all 74 allocations released, existing WebSocket/JVM/JNI/session/
  binding/worker/frame/world/input/capture/chooser/upload/clear gates, ASan/UBSan
  and ARM64 API 26 compilation pass (`.mdo/E77-socket-clock-fixed.log`,
  `.mdo/E77-bootclock-native-full.log`/`.json`). One shared output directory;
  no app/Home/source copies or model/external page actions.
- Commit the independent C fix, then apply reviewed sources with the service
  stopped and rebuild the one ARM64 host/APK. Current E76b artifact is unchanged
  until its new build receipt passes. Goal active, phone/provider deferred.

## E77d — package the elapsed-deadline fixes and restore the single deployment

- With all test/build handles terminal, stop the development service and apply
  reviewed host/JNI overlays to its existing pinned source checkouts. Rebuild
  the same ARM64 native output; source/compiler/profile/dependency/ABI receipts
  pass (`.mdo/E77-android-native-build.log`). Library 3,547,104 bytes, SHA-256
  `af06385599860d01df28e3c20c052c06e7006d020f408ce502e87487b06536dc`.
- Complete E77 development APK passes actual DEX definitions, manifest/private
  provider, exact native payload, 16 KiB ELF/zip alignment, v2-v3 signatures and
  preserved QA package/certificate/serial (`.mdo/E77-android-program-build.log`).
  Artifact 6,403,393 bytes, SHA-256
  `565f440fbaec9e7bd7ea7184987ef840c9b805a1f5cec4c3e7932115d81fb03e`.
  Verify current Android/builder/native input hashes and every final native-test
  source hash against the final files. Keep device/framework/automation flags
  false. No phone install, provider update or qualification substitution.
- Full public regressions: JS 331/331 in 19.242 s, Python 171/173 in 57.682 s,
  with only the two existing Windows symlink privilege 1314 skips
  (`.mdo/E77-node-full.log`, `.mdo/E77-python-full.log`). Restore the same headless
  deployment: port 63625, HTTP 200, approximately 3,432 MiB. Recheck unchanged W4
  Windows and paired plugin receipts; no product/source/Home copies or online
  release, external page actions, model calls or formal user-data modifications.
- Goal active. Native file clear and suspend-inclusive deadline source paths are
  joined and packaged. Actual Android provider/main-UI/URI/event qualification,
  intercepted chooser association and remaining directory/download/profile/
  activation work stay outstanding. Historical Windows intermittent input
  failures remain unattributed. Respect the user's deferred phone/current-WebView
  decision; continue feasible development without claiming all Browser Use done.

## E78a — prove the original native profile before installing a document world

- Check the pinned Chromium boundary revision and published Android profile
  contract. Pin `ProfileBoundaryInterface.java` SHA/size/URL, with native
  `getProfile`/`getName` signatures. Profile setters must run before other
  WebView use; inspecting the previous profile first would itself lock it.
  No AndroidX/AAR/runtime package or upstream source copy is introduced.
- The previous adapter treated `setProfile` return as successful isolation.
  A provider that silently retains its default profile still let the document
  claim `profileAssigned` (`.mdo/E78-profile-red.log`). Read the association from
  that same original native provider after its one setter invocation, then
  require the exact requested non-default name before installing any listener,
  script or isolated world. Manual views neither inspect nor change profiles.
- Keep same-name requests before installation idempotent, reject another name,
  and forbid profile changes after any native world-install attempt, including
  failed setup. Once profile assignment is dispatched, missing/malformed/throwing
  readback, reentrant retirement or fatal acknowledgement loss stays uncertain.
  Installation and further profile requests are fenced without another setter.
  `close` cannot claim that state clean; only exact original-view destruction
  resolves it. Existing confirmed profile data is not cleared or deleted.
- Extend the foreign-loader controlled provider to associate profiles with
  physical view identities, not a singleton profile. Exact production world /
  document tests pass 35 world, 85 document, 190 profile and 18 native-budget
  checks (`.mdo/E78-profile-lifetime.log`). Cover default/mismatched/missing
  readback, native errors and OOM, duplicates, reentrant setter/getter closure,
  invalid/unsupported requests, separate views and preserved manual profile.
  Java 8 compilation passes; actual Android storage/cookies/provider behavior
  remains unqualified. E77 APK is unchanged until the next packaging gate.
- Goal active. Next join failure teardown in the actual product create path,
  then package the reviewed Android source. No phone/provider/online/model or
  external page changes. Android global automation admission remains disabled.

## E78b — give product creation one native retirement owner

- The actual automation `bindDocument` failure path destroyed its WebView and
  removed the lifecycle binding, then the surrounding create failure handler
  destroyed that same original view again. Move native retirement to that outer
  owner, which already tears down input, document, capture/dialog/popup and file
  ownership together. Keep the original profile refusal and do not load a page
  or silently fall back to a manual/default profile.
- Add a bounded, project-owned exact-fragment JVM gate: inject the real create,
  binding and close/destroy methods, run the real lifecycle/input/wheel cores,
  and control only the UI/provider/data peers. The actual prior product methods
  fail the double-destruction assertion (`.mdo/E78-create-red.log`). Early fixture
  syntax/dependency errors are preserved separately (`.mdo/E78-create-initial.log`,
  `.mdo/E78-create-compiled.log`), not attributed to the product.
- 28 checks pass (`.mdo/E78-create-fixed.log`): original profile failure retires
  exactly once; input setup, settings, attach, bounds, load and reply failures
  leave no active bindings; manual unsupported-world browsing still works;
  reattachment does not reload and deferred popup adoption preserves its exact
  native child. Published source hashes identify the injected methods. This is
  controlled Java 8 execution, not installed Android/framework qualification.
- Goal active. Next fence new native creation if original teardown itself fails,
  rather than treating an erased UI map entry as confirmed native release. Then
  package E78 with all source/build receipts. E77 APK unchanged for now; phone,
  WebView/provider, formal data and online release remain untouched.

## E78c — retain native cleanup uncertainty and reserve retirement callbacks

- A failed original destroy was reported only as a generic cleanup exception,
  and an erased lifecycle map allowed another physical view even though native
  resource release was unconfirmed (`.mdo/E78-cleanup-red.log`). Add a sticky
  per-host native-cleanup fence and expose it in capabilities/document state.
  New creation/reattachment is refused until a fresh host lifecycle; existing
  state/close controls are not disguised as successful cleanup.
- Reserve the exact retiring view before upload/input cancellation and lifecycle
  removal. The helper now owns removal too, so creation cannot reenter from an
  input or UI cleanup callback. Recursive retirement of that same physical view
  returns unconfirmed without another destroy attempt. Normal completed cleanup
  releases the reservation and permits an explicit later creation.
- Move native cancellation into the reviewed method rather than injecting it
  ahead of reservation. Update close/render-failure/create callers to use the
  sole retirement owner. Any thrown cleanup error or unconfirmed final document
  cleanup sets the host fence; OOM preserves it as well. Create failures give
  cleanup uncertainty priority and retain the original primary cause plus cleanup
  exception privately, without exposing provider text in the top-level code.
- Exact product-fragment Java 8 gate passes 70 checks
  (`.mdo/E78-cleanup-lifetime.log`), including failed detach/destroy/document
  cleanup/OOM, reentrant UI and input completion, duplicate retirement, sticky
  resource admission after erased UI bindings, stopped-host refusal, eight-view
  quota before allocation and exactly one reclaimed slot after confirmed cleanup.
  Earlier 39-case evidence is `.mdo/E78-cleanup-fixed.log`. UI/native peers are
  controlled; no actual provider/framework/physical resource release is qualified.
- Goal active. Run the complete native/public gates, apply only with the one
  service stopped and package E78. No device/provider/model/online changes;
  E77 artifact remains current until the replacement receipt passes.

## E78d — preserve unknown cleanup in repeated and reentrant close receipts

- Final product review found that `close` on an already erased UI entry still
  returned `world_cleanup_confirmed:true` after failed native destruction. The
  same false confirmation was possible during a reentrant close while the first
  native retirement was still active. The exact prior route fails
  `.mdo/E78-close-receipt-red.log`; a missing map entry is not release evidence.
- Make logical document cleanup require no native cleanup fault or active
  retirement before confirming closure. Repeated/empty-entry close remains
  idempotent when native cleanup is known clean. Add the cleanup-fault and active
  retirement fields to close receipts, consistent with capabilities/state. Do not
  replay destruction or turn an uncertain original close into later success.
- The exact-fragment gate now injects the actual logical close method and the
  reviewed product close-command overlay, not a synthetic caller that removes a
  lifecycle entry first. 72 checks pass `.mdo/E78-close-receipt-fixed.log`, covering
  the original input-completion path, repeated close after failed destroy and
  empty-entry reentrant close before the original destroy completes.
- Record tool/overlay/input hashes in the product-create result. All peers remain
  controlled; actual Android resource release is still unqualified. The initial
  E78 build already compiled, but this receipt correction requires one final APK
  replacement after its current-source native/public gates. Goal active; no phone,
  provider, model, formal data or online release change.

## E78e — validate complete overlay composition before deployment

- The final apply gate rejected E78d before writing: an older secondary overlay
  still rewrote the previous one-line `closeDocument` body to add chooser cleanup.
  The corrected method no longer contained that old anchor. Preserve the
  `chooserClean` predicate directly in its authoritative source fragment and
  remove the redundant patch, rather than weakening exact matching or overwriting
  the checked deployment. Logs `.mdo/E78-apply-final.log` and
  `.mdo/E78-android-final.log` preserve the refused attempts; neither qualified a
  new artifact. The same source now composes all 112 reviewed deployment files.
- Add `apply_host.py --validate`: compose the entire reviewed plan from the
  pinned Git baseline without checking/changing deployed text, calling a build
  helper or stopping a service. Actual apply retains its separate stop/previous-
  hash/unreviewed-edit protections; validation is not overwrite authorization.
  Two integration regressions pass `.mdo/E78-overlay-plan-tests.log`: valid plan
  with simulated running-service state performs no writes, and a deliberately
  broken anchor fails with deployed source/receipt contents and timestamps intact.
- The exact close/create gate also preserves refusal for an unclean original
  chooser; 73 checks pass `.mdo/E78-create-overlay-final.log`. Include source,
  overlay and tool hashes in its receipt. Add equivalent source provenance to the
  35 world / 85 document / 190 profile / 18 budget gate. Require actual World,
  Failure and Document DEX definitions in the complete APK, not just references.
- Goal active. Reapply the now composed source with the service stopped, finish
  current-source native/public gates and rebuild/verify the same development APK.
  Phone/current provider, formal data, one source/Home and online release remain
  unchanged; no overall Android qualification is inferred from these fixtures.

## E78f — verify and package the profile/retirement product paths

- After the read-only plan passed, apply the reviewed overlays only with the
  existing service stopped (`.mdo/E78-apply-verified.log`). Verify the complete
  current-source native pipeline (`.mdo/E78-native-current.log`/`.json`), exact
  world/document/profile/budget inputs (`.mdo/E78-world-current.json`) and exact
  create/close/retire inputs (`.mdo/E78-create-current.json`). Check every recorded
  source hash against its current file, including tools and overlays.
- Reuse the verified E77 ARM64 library (3,547,104 bytes,
  `af06385599860d01df28e3c20c052c06e7006d020f408ce502e87487b06536dc`).
  Final complete APK passes source/compiler/ABI/private-provider/DEX/alignment/
  v2-v3-signature receipts (`.mdo/E78-android-verified.log`): 6,403,393 bytes,
  SHA-256 `c4c8f55377303c6142898e0a2c0434a0711d2343c43d579debeb2b38f5807fd7`.
  Confirm current builder, Android source and native hashes; actual World/Failure/
  Document DEX definitions are required. QA package, signer and serial are stable;
  device/framework/automation qualification flags remain false.
- Full public regressions: JS 331/331 in 20.419 s; Python 173/175 in 74.961 s,
  with only the two existing Windows symlink privilege 1314 skips
  (`.mdo/E78-node-full.log`, `.mdo/E78-python-full.log`). Native profile checks 190,
  exact product creation/closure checks 73, world/document 35/85, native-budget 18;
  controlled framework peers are not device qualification.
- Restore the same headless deployment to port 63625, HTTP 200, approximately
  3,432 MiB. Revalidate unchanged W4 Windows binary and paired plugin, plus all
  current allowed app-archive source hashes. No extra app/Home/source copy,
  actual phone/provider/model/external page action, formal data change or online
  publication. Update the current APK record and public optional state fields.
- Goal active. Profile association/readback and native-creation retirement paths
  are implemented and packaged. Actual Android provider/main-UI/profile/storage/
  URI/event evidence, intercepted chooser association and remaining directory/
  download/activation work remain pending. Historical Windows intermittent input
  failure causes stay unattributed. Continue feasible development without asking
  again for the deferred phone or enabling unqualified automation.

## E79a — continue original paused requests without ordinary RPC reentry

- Original-response downloads need interception on the original connection,
  rather than fetching a URL again. Before enabling interception, resolve the
  deadlock in which an ordinary page command waits for a paused request while
  the dispatcher refuses every nested command. Add only fixed native
  `Fetch.continueRequest`/`Fetch.failRequest` flow control inside the exact
  `Fetch.requestPaused` callback; ordinary RPC reentry still fails. Pin the
  reviewed Chromium Fetch handler/interceptor sources in the private protocol
  record. No Fetch activation, response stream or public Agent method is added.
- Freeze the original native source, opaque request ID and request/response
  stage before callbacks can mutate JSON. Continuation has no URL/method/header/
  body override; abort uses only fixed `Aborted`. Use unique dispatcher IDs and
  match acknowledgements by ID and original native session. An ordinary result
  may precede its control ACK without losing either; later calls/polls drain
  pending ACKs. Unknown/duplicate/wrong-scope replies still retire the stream.
- Native-only atomic receipts expose no request authority or page data. Preserve
  the first terminal outcome across UI close, ownership loss and child retirement;
  late acknowledgements cannot upgrade it. A known unsupported control has a
  separate no-effect result while an already issued ordinary command remains
  uncertain. Never replay. Bound pending controls to 256, lifetime stage keys to
  4096, send to the remaining outer budget and ACK age to 5000 boot-elapsed ms;
  idle polls/command boundaries never renew the original deadline or replay keys.
- Exact dispatcher/codec/JNI tests pass `.mdo/E79-control-unit.log` and the full
  `.mdo/E79-native-full.log`/`.mdo/E79-native-current.json`: 30,840 assertions
  including capacity/byte-clear checks and six actual local protocol sessions.
  The peer withholds its ordinary result until receiving the fixed control.
  Cover ACK order, frozen event mutation, duplicate stages, root/child isolation,
  abort, provider rejection sanitization, partial send failure, UI cancellation,
  retired-child traffic, original deadlines, clock faults and resource exhaustion.
  The original 4,324-assertion/eight-session dispatcher gate and full native,
  frame/world, upload/clear gates also pass; ASan/UBSan and API 26 compile pass.
  Every recorded input hash matches. Android/DOM peers remain controlled.
- Goal active. Commit this foundation, then compose/apply with the single service
  stopped and verify the actual complete development APK. Original-response
  classification/body/storage/accept/cancel integration and the persistent
  pre-navigation network owner/pump remain unfinished. No device/provider update,
  model/external page action, formal data change or publication; global Android
  automation stays disabled. Do not ask again for the deferred phone.

## E79b — package fixed native control receipts and verify deployment

- Compose all 112 overlays read-only, stop the same development service and
  apply the reviewed source (`.mdo/E79-apply.log`). Reuse the unchanged E77 ARM64
  library and verified W4 app archive/paired plugin, without another app/Home
  copy. Require actual `ControlReceipt` and `ControlResult` definitions in the
  complete APK, not only DEX references. The packaging regressions explicitly
  remove each definition while retaining a reference and confirm refusal.
- Seven packaging-contract checks pass `.mdo/E79-apk-contract.log`. Complete
  APK compiler/actual-DEX/ABI/private-provider/manifest/alignment/v2-v3-signature
  verification passes `.mdo/E79-android-build.log`. Artifact 6,403,393 bytes,
  SHA-256 `30d39890aad191da9b2619933585920638399e55ac0924d971fe3b05aa4bd3ed`.
  QA package/signer/installation serial stay stable. Builder/current Android
  source and all `.mdo/E79-native-current.json` inputs match their receipts;
  device/framework/automation qualification flags remain false.
- Full Python regression passes 173/175 in 55.217 s, with only the two existing
  Windows symlink privilege skips (`.mdo/E79-python-full.log`). Public JS/app
  runtime sources did not change in E79; their E78 331/331 result remains the
  prior relevant evidence. The full current native gate is E79a's result above.
  Restore the same headless service on port 63625 and verify HTTP 200.
- Goal active. Original-response stream/download storage and persistent physical
  network ownership/event pumping are the next unfinished development areas.
  Preserve the deferred phone/current provider and formal data; Android overall
  automation remains disabled. No device install, runtime dependency or online
  release is introduced by this packaging phase.

## E80a — prove a distinct physical-view network connection

- A document-scoped binding deliberately dies at navigation and cannot own an
  original download response across that navigation. Add a separately created
  native physical-network marker, not an upgrade of an existing document marker.
  Use the same native callback/independent nonce/unique-context negative probe,
  original context value, cleanup and stable-root-loader proof before retaining
  the exact original connection. One proof attempt per physical marker; failed
  or closed connections cannot silently rebind that marker to another response.
- Ordinary frame binding refuses the network marker; network binding refuses a
  document marker, and its lease cannot reserve execution-world names. Expose
  only a private network facade: fixed one-time response-stage `Fetch.enable`
  without auth interception, exact paused-event continue/abort and bounded poll/
  pending-control observation. No caller-selected target or arbitrary page/frame
  RPC is added. Disable Runtime metadata after proof and discard its document
  maps instead of accumulating recycled IDs over the physical view lifetime.
- Navigation/context replacement no longer grants or erases network ownership.
  Inspector detach, renderer crash/reload, original native-owner close and lost
  lease retire the original connection with no automatic reconnect/replay.
  A failed/uncertain Fetch enable is never repeated. The native physical owner
  must close this lease on takeover, host admission loss, renderer/view retirement
  or replacement; this private core is not yet routed into the product lifecycle.
- Actual production marker/adapter/binder/dispatcher/strict JSON/JNI gate passes
  1,978 assertions with controlled UI/metadata peers, including 300 document
  context replacements per continuation/abort path, source proof failures,
  activation failure, ACKs during activation, foreign-source traffic and native
  owner close (`.mdo/E80-native-current.log`/`.mdo/E80-native-current.json`).
  Existing eight local dispatcher sessions, six native flow-control sessions,
  full frame/world/upload/file-clear, ASan/UBSan and API 26 gates pass as well.
  Every recorded source hash matches. These are not installed-provider results.
- Goal active. Finish the product physical-owner registry and event pump before
  initial navigation, original-response classification/body storage and retained
  accept/cancel integration. Preserve the user-deferred phone/current provider.
  This phase has not yet changed the E79 APK or deployed source; package after
  validation. No model, external page, device install, formal data or publication.

## E80b — compile and package the physical-network facade

- Validate the full 112-file overlay composition without writes, stop only the
  existing development service, and apply reviewed source (`.mdo/E80-apply.log`).
  Require the actual `MdoBrowserCdpBinding$Network` DEX definition in the complete
  APK. The missing-definition/reference-only regression passes with all seven
  packaging contracts (`.mdo/E80-apk-contract.log`).
- Current complete development APK passes compiler, current source, actual DEX,
  six native JNI entries, private provider/manifest, ARM64 ABI, alignment and
  v2-v3 signature receipts (`.mdo/E80-android-build.log`). Size 6,407,489 bytes,
  SHA-256 `eb6974d81994b20dfa3215c3060692e80bf73449d8741d75f7b3020a6a8b7aa9`.
  Verify current builder/Android/native input hashes and every E80 native gate
  input. Reuse the unchanged E77 ARM64 library, W4 Windows program/source archive
  and paired plugin, plus the same QA signer/package/serial and one source/Home.
- Restore the same headless service to port 63625 / HTTP 200; project deployment
  is about 3,432.7 MiB. E80 changes no public JS/app-runtime source; relevant
  existing E78 JS and E79 Python whole-suite evidence remains historical, not a
  new rerun. This phase's current evidence is the full E80 native pipeline and
  packaging-specific contracts/build/receipts above.
- Goal active. The private physical proof/facade is implemented and packaged;
  product physical-owner registry, pre-navigation startup barrier and persistent
  event pump still require integration before original-response stream/cache/
  accept/cancel can be enabled. Android global automation and provider/framework
  qualification remain false. Honor the deferred phone/current WebView. No
  device install, new runtime dependency, formal data edit or online publication.

## E81a — service physical network owners on the existing protocol worker

- Add the private eight-owner network ledger and one bounded pump on the
  existing CDP executor; no extra protocol threads. Share a 17-entry queue for
  eight ordinary jobs, eight network starts and one pump. Preserve each role's
  separate admission count, including pending UI deliveries. Never run caller
  work on the UI thread or silently reconnect/replay a failed physical marker.
- While an ordinary RPC waits, an explicit worker-local cooperation scope gives
  another idle connection one round-robin receive slice. Busy connections and
  recursive scheduler entry are skipped; ordinary callback RPC reentry remains
  forbidden. A 20 ms kernel receive slice retains an original 250 ms native
  event/control-send budget. Charge scheduler/parser/callback time to original
  deadlines and retain the separate non-renewing control ACK deadline.
- Startup budget includes queue delay, native proof, original response-enable
  ACK and any inline control ACKs before reporting ready. UI states are at most
  ready then terminal, coalesced into one pending runnable. Cancellation closes
  the original handle, drains running I/O and suppresses late queued ready
  messages before terminal delivery/readmission. UI posting failure retires the
  entire common worker. Callback failures isolate their owner. Unknown native
  close keeps a sticky slot and cleanupConfirmed:false; it is not a release.
- The actual shared worker/proof/dispatcher/strict JSON/JNI gate passes 148
  assertions, covering an ordinary page result withheld until another physical
  connection continues its request, readiness ACK drain, idle events, queue
  timeout, eight-owner/UI backpressure, queued/running cancel, renderer loss,
  rejected UI posting, late ready, callback failure and uncertain cleanup.
  Add a real two-connection Unix/WebSocket/JNI pair with the same withheld-result
  condition; it completes on one worker and confirms both native handles/FDs and
  allocations retire. Six earlier native control sessions and eight ordinary
  protocol sessions still pass. Full native/frame/world/upload/file-clear,
  ASan/UBSan and API 26 gates pass `.mdo/E81-native-full.log` and source-hashed
  `.mdo/E81-native-current.json`; every recorded input hash matches.
- Goal active. This is the implemented private owner ledger/pump API; physical
  view/profile lifecycle registration, the product pre-navigation barrier,
  original body/storage and retained accept/cancel remain unfinished. The native
  WebSocket table also needs sufficient capacity for simultaneous physical and
  document bindings before product activation. Prepare that capacity and package
  after validation. E80 APK remains current until replaced; no device/provider,
  model/external page, formal data or online release change. Phone deferred.

## E81b — bound native capacity for network and document connections together

- The private WebSocket table's previous eight slots could all be occupied by
  persistent network owners, preventing any document proof/input connection.
  Set a process-wide hard limit of 17: eight network owners, eight document
  bindings and one shared worker's proof/retirement overlap. This still includes
  draining handles and retains monotonic non-reused handle identities; per-
  message/send/assembly deadlines and byte limits are unchanged.
- The actual kernel-socket gate fills every slot, refuses the next open, retains
  the limit while handles drain and verifies exact final descriptor/allocation
  balance. Current native WebSocket gate: 1,195 assertions, 204 allocations and
  204 releases; 64 cancel cycles. Full current native/Java/JNI/shared-worker/
  cooperative pair/frame/world/upload/file-clear and ASan/UBSan/API 26 gates pass
  `.mdo/E81-native-capacity.log`/`.mdo/E81-native-capacity.json`; every recorded
  input hash matches.
- Require actual network ledger/slot/result/cooperation DEX definitions in the
  complete APK. The seven packaging contracts reject references without each
  implementation (`.mdo/E81-apk-contract.log`). All 113 reviewed deployment
  files compose without writes. Rebuild the existing ARM64 native output and
  complete APK next; the E80 artifact is not yet replaced at this source commit.
- Goal active. No new runtime dependency or app/Home/source copy, phone/provider
  change, model/external page action, formal data edit or online publication.

## E81c — verify native capacity, cooperative classes and the complete APK

- With the existing service stopped, apply all 113 reviewed deployment files
  and checksum-guarded private JNI sources (`.mdo/E81-apply.log`). Rebuild the same
  ARM64 output against the pinned source/compiler/profile, verify its post-build
  source identity and ABI receipt (`.mdo/E81-arm64-native.log`). Engine 3,547,120
  bytes, SHA-256 `d079b2bec8e68661a36921398771b198174f17951282a5958c4d1c0ed5abb4ac`.
  No second source, app/Home, object tree or runtime dependency is introduced.
- Complete APK verifies current native bytes, six original JNI entries, actual
  network/slot/result/cooperation DEX definitions, source/compiler, private
  provider/manifest, ARM64 ABI, zip alignment and v2-v3 signatures
  (`.mdo/E81-android-build.log`). Artifact 6,411,585 bytes, SHA-256
  `1193efdded73a5186e1496670b46b56a29d9b09b3d30d7c44fcec72892925054`.
  Preserve QA package, signer and installation serial; device/framework/automation
  qualification flags remain false. Every recorded native-gate source hash and
  current APK builder/Android source/binary hash matches its receipt.
- Full Python gate passes 173/175 in 57.949 s; only the two existing Windows
  symlink privilege 1314 skips (`.mdo/E81-python-full.log`). Seven APK contracts
  pass. Full E81 capacity/native/dispatcher/shared-worker/frame/world/upload/clear
  evidence is recorded above. Public JS/app runtime did not change; E78's JS
  result remains earlier evidence rather than a new run. W4 Windows binary/app
  archive and paired plugin remain verified/reused.
- Restore the same headless deployment to port 63625 / HTTP 200, approximately
  3,433 MiB. Goal active: private owner scheduling and native capacity are now
  implemented and packaged. Product physical-view/profile lifecycle registration,
  pre-navigation readiness barrier, original-response classification/body/cache
  and retained accept/cancel still require integration and qualification. Phone
  deferred/current WebView retained; no device install, formal data change,
  model/external page action or online release.

## E82a — join physical ownership to product creation and native lifecycle

- Add the reviewed product network fragment: bind exact view, confirmed profile
  document and original host channel, with at most eight physical entries. Agent/
  interception creation confirms the managed profile first, loads only the owned
  blank document, observes its epoch/current native URL and waits for original
  binding/response-enable/control ACKs before sending the target URL at most once.
  Pending startup refuses competing navigation; manual creation keeps its old
  path. Existing manual views cannot be silently upgraded. Managed popup network
  adoption remains explicitly unqualified, rather than bypassing first-response
  interception. Global automation/interception capability admission stays false.
- Join navigation, close/destruction, host channel/background loss, stop during
  startup and takeover. Revoke atomic worker admission before chooser/world/input
  cancellation callbacks. Ordinary document navigation retains the same physical
  source. Duplicate ready, stale channel/epoch/URL, cancellation and late callbacks
  never reissue a URL. View destruction and original protocol drain jointly release
  the original entry/quota; pending cleanup fences new creation and close receipts.
- Failed pre-navigation startup retires only the original view after confirmed
  protocol cleanup. An uncertain target dispatch preserves that view and carries
  a structured native_create receipt. The shared JS create/reveal path forwards
  bounded canonical reasons and exact navigation uncertainty; malformed receipts
  remain uncertain. Guidance distinguishes a clean unsent failure from a possibly
  sent navigation, without provider text or an automatic retry.
- A new controlled time-delay reproduction caught a permanently poisoned host
  after cleanup observation exceeded five seconds (`.mdo/E82-cleanup-observation-red.log`).
  Keep pending capacity after observation expiry; authoritative late cleanup can
  reclaim an unsent view and allow a later explicit creation, while the original
  uncertain receipt remains unchanged. Actual failed/unconfirmed native cleanup
  still keeps its sticky fault. Ready blank views and possibly sent targets are
  not discarded by this delayed cleanup path.
- Exact product create/bind/close/network/takeover fragments with real lifecycle/
  input cores pass 138 assertions (`.mdo/E82-create-network-final.log`,
  `.mdo/E82-create-current.json`). UI/provider/protocol peers are controlled.
  Full JS 333/333 in 19.252 s (`.mdo/E82-node-current.log`), plus final receipt
  guidance tests; actual core/JSON/JNI/kernel/worker/frame/world/upload/clear/
  ASan/UBSan/API 26 gates pass `.mdo/E82-native-current.log`/`.json`. All recorded
  input hashes match. The 113-file overlay plan composes without writes.
- Goal active. Build and exercise the updated app archive because shared create
  handling changed, then compile the complete APK and public gates. Original
  response classification/body/cache/accept/cancel, explicit rearm/recovery and
  managed popup network qualification remain unfinished; current private response
  events pass through on their original connection. No new runtime dependency,
  app/Home/source copy, device/provider update, model/external page call, formal
  data edit or publication. Preserve the deferred phone/current WebView.

## E82b — package and exercise the current creation barrier

- Stop the single development service, compose/apply all 113 reviewed files,
  then pack the updated app archive in place. Preserve and verify its packaging
  journal restoration. Windows output 6,934,716 bytes, SHA-256
  `d6b3822bf5d4c927925f0c1001713d54568ff794defa35621f85654f6512e818`.
  All twelve actual packed-program/runtime checks pass, including matching
  installed plugin inventory, observation, Unicode fill/trusted click, capture,
  connection-reset reporting and explicit navigation recovery
  (`.mdo/E82-windows-build.log`, `.mdo/E82-windows-runtime.log`).
- Reuse the unchanged verified E81 ARM64 engine and compile the complete current
  Android host. Require the actual NetworkEntry DEX definition alongside the
  existing physical-owner/cooperative classes, six JNI entries, private provider,
  exact app/native payload hashes, ARM64/16 KiB alignment and v2-v3 signatures.
  APK 6,415,681 bytes, SHA-256
  `ab6c04fa7c0c7cef7028af351bf2890977621f5d96b4408203f7ffafc6f1d968`
  (`.mdo/E82-android-build.log`). QA identity/signer/serial are preserved.
- Full Python gate passes 173/175 in 64.486 s; only the two existing Windows
  symlink privilege 1314 skips (`.mdo/E82-python-full.log`). Current 138-assertion
  product and full native gate snapshots still match every recorded input hash.
  Verify both artifact/source identities and Windows runtime/paired-plugin
  receipts after packaging. The same headless service returns HTTP 200 on 63625,
  about 3,434.5 MiB. No second app/Home/source tree is created.
- Goal remains active: original-response classification/body/store/accept/cancel,
  explicit network rearm and managed popup integration are unfinished. Device,
  framework and automation qualification remain false. No phone/provider update,
  actual model/external page call, formal data edit or online release.

## E83a — freeze bounded original-response classification

- Add a private immutable response summary at the original paused-event boundary;
  expose it through the exact physical-network facade only during that event.
  Callback JSON edits cannot retarget the summary or its frozen native control.
  Retained results contain four scalar fields and no URL/request/header/cookie/
  filename authority. No ordinary callback RPC reentry or second request is added.
- Classify explicit attachments/known binary full-body 2xx Document responses;
  pass request/error/redirect/non-document/HEAD/CONNECT/no-body/partial cases.
  Validate relevant header duplicates, names, UTF-8 budgets, quoted parameters
  and decimal length/overflow. Ambiguous metadata is invalid, not a stream claim.
  Native download-intent/provider qualification and body capture remain unfinished.
- Exact metadata gate passes 295 assertions; physical network proof/facade passes
  1,986. Full original transport/dispatcher/controls/cooperative worker/frame/world/
  input/chooser/upload/clear, ASan/UBSan and API 26 compilation gates pass
  `.mdo/E83-native-full.log` / `.mdo/E83-native-current.json`; recorded input hashes
  match. Python passes 173/175 in 68.497 s, with only the two existing Windows
  symlink privilege skips (`.mdo/E83-python-full.log`). The 114-file overlay plan
  composes without writes. Existing E82 JS evidence remains valid; JS did not change.
- Goal active. Require the actual response-class DEX definition in the complete
  APK and package current Android sources next. The E82 app archive remains the
  current tested shared frontend; no device/provider change, external page/model
  action, formal data edit, extra app/Home/source copy or online publication.

## E83b — verify current response metadata in the complete APK

- Apply all 114 reviewed files with the single service stopped. Reuse the
  unchanged verified ARM64 engine and E82 Windows app archive/paired plugin;
  application inputs still match that exercised archive. Compile the actual
  response classifier and require its DEX definition, not a mere type reference.
  Existing six JNI entries, private provider/manifest, source/compiler/native/app
  hashes, ARM64/16 KiB alignment, original QA signer/identity/serial and v2-v3
  signatures all pass (`.mdo/E83-android-build.log`). APK 6,419,777 bytes, SHA-256
  `d6ba62cbd529699eb7945540c83bbb16846b5485a525c0b8549709004be90045`.
- After assembly verify the current Android input hash, exact reused app archive,
  both binary hashes and Windows runtime/paired-plugin receipt, plus every E83
  native-gate source hash. Restore the same headless service on port 63625 with
  HTTP 200, approximately 3,434.7 MiB. Current Python/native/metadata checks are
  recorded in E83a; no changed JS or repeated provider qualification is implied.
- Goal remains active: original body claiming/streaming/private cache/accept/
  cancel, explicit rearm, managed popup networking and actual device/provider/UI
  qualification are unfinished. The product continues original responses and
  overall Android automation/interception admission stays disabled. Phone and
  provider update deferred as requested; no install, formal data edit, model/
  external page action, extra app/Home/source copy or online publication.

## E84a — claim and drain original response bodies on the native worker

- Add opaque native Body/BodyResult/BodyChunk objects. Only an original frozen
  candidate callback can issue the fixed stream claim; ordinary callback RPC
  reentry stays forbidden. Validate the asynchronous stream ACK and reject
  reused handles. Exact body ownership, eight active slots and unchanged
  boot-elapsed lifetime prevent selectors/rebinding/replay or capacity eviction.
- Sequential reads cap each chunk at 64 KiB and each body at 64 MiB. Validate
  canonical base64/UTF-8, EOF, result fields and exact remaining byte bounds;
  erase buffers on failure/caller release. Expiry/cancel cleanup has an independent
  bounded budget. EOF success requires original IO.close and Fetch.failRequest
  ACKs. Lost read/cleanup replies retire the connection, preserving uncertain
  effects and separate stream/request cleanup flags. Immutable first receipts
  are never upgraded after close/cancel. Pre-send quota rejection still permits
  the native caller to abort the current original event.
- New body gate passes 571 assertions with actual strict codec/JNI validation and
  three real local kernel sessions: original POST binary/Unicode body, explicit
  cancellation and withheld read reply with no replay. Entire transport/control/
  cooperation/binding/frame/world/input/chooser/upload/clear, ASan/UBSan/API 26
  gates pass `.mdo/E84-native-full.log` / `.mdo/E84-native-current.json`; all
  recorded input hashes match. Python passes 173/175 in 63.761 s, only the two
  existing Windows symlink privilege skips (`.mdo/E84-python-full.log`). The
  114-file reviewed overlay composes without writes. No JS changes this phase.
- Goal active. Private cache/hash/storage, body service/idle expiry, actual
  product list/permission/accept/cancel/save and installed-provider/main-UI
  qualification remain open. Require the three actual body DEX definitions in
  the full development APK next. No new runtime dependency, app/Home/source copy,
  phone/provider update, formal data edit, external page/model action or release.

## E84b — prevent native text serialization loss from becoming file success

- Source inspection of the exact pinned Chromium Fetch/stream-pipe/String8
  serializer found that byte-sized text chunks can split UTF-8 and the JSON
  serializer can skip those bytes. Record primary-source hashes; reconstruct
  the possible original wire results for a split multibyte response. Do not
  claim an installed-provider failure reproduction or an upstream engine fix.
- Preserve a constant stream encoding. Non-base64 data now needs an original
  untransformed length proof and exact complete UTF-8 byte equality. Absent/
  content-coded/transfer-coded length proofs are unavailable; duplicate coding
  and non-ASCII identity spellings cannot grant proof. Lost bytes fail with a
  bounded canonical reason and original cleanup ACKs, rather than an accepted
  truncated file. No refetch/replay is added. General lossless unknown-length or
  transformed text still needs an alternate proven transport/platform downgrade.
- Final 306 metadata and 670 body checks pass, with three real JNI/kernel body
  sessions; full original transport/control/cooperative/binding/frame/world/input/
  chooser/upload/clear and ASan/UBSan/API 26 gates pass
  `.mdo/E84-text-native-final.log` / `.mdo/E84-text-current.json`, with all recorded
  source hashes matching. Seven current APK/DEX contracts pass
  `.mdo/E84-text-apk-contract.log`. E84a's full Python gate remains evidence for
  unchanged Python/public inputs; new Java behavior is covered by the current
  full native gate. Shared JS is unchanged.
- Goal active. Package this guarded native body implementation next; product
  cache/hash/storage, nonblocking body servicing/idle expiry, permission/list/
  accept/cancel/save and provider/main-UI qualification remain unfinished. No
  phone/provider update, formal data edit, extra application/Home/source copy,
  actual model/external page action, new runtime dependency or online release.

## E84c — verify guarded body types in the complete development APK

- Apply the 114 reviewed files with the single service stopped. Reuse the
  verified unchanged ARM64 engine and current exercised E82 app archive/paired
  plugin. Compile exact current body/metadata/ownership code and require actual
  Body, BodyResult and BodyChunk DEX definitions. Source/native/app hashes, six
  JNI entries, private provider/manifest, ARM64/16 KiB alignment and v2-v3 signing
  pass (`.mdo/E84-final-android-build.log`). Preserve QA identity/signer/serial.
  APK 6,423,873 bytes, SHA-256
  `b56f3d164daaf8ece4a22d63f23e13985e02dc11aa91580f4842613520dcac6a`.
- Verify current APK/Android source identities, exact reused app archive and
  Windows binary/runtime/paired-plugin receipt, every E84b native-gate source
  hash, and false device/framework/automation qualification flags. Restore the
  existing headless service to HTTP 200 on 63625, approximately 3,435.0 MiB.
  Seven current APK contracts and full current native gates are recorded above.
- Goal remains active. No full download/product/provider completion claim:
  cache/hash/storage, nonblocking body service/idle expiry, list/permission/
  accept/cancel/save, unsupported text transport handling and actual provider/UI
  qualification remain open. Phone and provider update remain deferred. No
  installation, formal data edit, actual model/external page action, application/
  Home/source copy, new runtime dependency or online publication.

## E85a — service body replies without blocking the common worker

- Replace ordinary waiting reads/cleanup with fixed asynchronous native rows.
  Original body deadlines govern stalled reads; short polls do not renew them.
  Join one bounded send/turn to the existing physical-network/cooperative pump,
  with no executor, UI socket wait or automatic read/request replay. Retain
  blocking diagnostic helpers over the same machine, not a second data path.
- Keep one buffered chunk per body and wait for consumer handoff even at EOF.
  Service idle expiry, atomic cancellation and staged cleanup; retain a pending
  read association until its reply drains or the connection must retire. Wipe
  unconsumed data before failed/cancelled cleanup. Close can interrupt an actual
  send without waiting on the control gate. Exact native rows still route amid
  other outstanding commands and cannot be retargeted by supplied wire IDs.
- Body gate passes 855 checks, three original kernel body sessions and one real
  two-connection asynchronous pair. The peer withholds the read until another
  page command arrives, proving that common-worker progress is possible.
  Controlled tests cover >5 s read wait, original idle/cleanup deadlines, late
  cancelled success/error replies, withheld replies, backpressure and UI close.
  Full control/cooperation/binding/frame/world/input/upload/clear/ASan/UBSan/API26
  gates pass `.mdo/E85-native-final.log` / `.mdo/E85-native-current.json` with
  matching recorded hashes. Python 173/175 in 50.296 s; only existing Windows
  symlink privilege skips (`.mdo/E85-python-full.log`). Overlay composes 114 files.
- Goal active. Source inspection then identified pinned native EOF/close finishing
  details that need the next compatibility fix before packaging. E84 artifacts
  remain the current built outputs. Cache/hash/storage, product permissions/list/
  accept/cancel/save and provider/main-UI qualification remain unfinished. No
  phone/provider update, application/Home/source copy, formal data edit, actual
  model/external page action, runtime dependency or online publication.

## E85b — finish binary EOF and retire only acknowledged cancelled reads

- Match the pinned stream's fast EOF path: it can return an empty EOF with
  binary=false after true binary chunks. Permit only this zero-payload terminal
  shape after existing binary proof; retain encoding and text-integrity guards
  for actual payload. The original kernel body fixture now reproduces separate
  binary/Unicode data followed by that EOF shape.
- Require original IO.close and Fetch.failRequest ACKs before retiring a pending
  cancelled read. Retain a bounded root-ID tombstone instead of waiting for a
  provider callback forever; one per body claim shares the existing 4,096-key
  lifetime bound. Discard one late root reply without data/receipt publication,
  reject wrong sources and preserve monotonic command IDs. Unknown cleanup still
  times out/retire-fences; confirmed cleanup no longer blocks the next page call.
- Body gate passes 900 checks, three original JNI/kernel body sessions, one real
  asynchronous pair and one real cancelled-read session. The last peer withholds
  its read reply until a new page command proves the connection is usable. Full
  control/cooperation/binding/frame/world/input/chooser/upload/clear/ASan/UBSan/
  API26 gates pass `.mdo/E85-finishing-native-final.log` /
  `.mdo/E85-finishing-native-current.json`; every recorded source hash matches.
  Seven current APK contracts pass `.mdo/E85-apk-contract.log`. E85a's full Python
  result remains evidence for unchanged Python/public contracts; new native gate
  policy is exercised by this current full run. Shared JS remains unchanged.
- Goal active. Package current source next. Nonblocking body service and idle
  expiry are joined to the private physical worker; cache/hash/storage, product
  list/permission/accept/cancel/save, unsupported text handling and real provider/
  main-UI qualification remain open. No phone/provider update, extra app/Home/
  source copy, formal data edit, actual model/external page action or release.

## E85c — verify the asynchronous body implementation in the full APK

- Apply all 114 reviewed files with the existing service stopped. Reuse the
  unchanged verified ARM64 engine, exercised E82 app archive and paired plugin;
  compile current session/binding/shared-worker code and verify actual body/
  network/cooperation DEX classes. Six JNI entries, private provider/manifest,
  current source/native/app payload hashes, ARM64/16 KiB alignment and v2-v3
  signatures pass (`.mdo/E85-android-build.log`). Preserve QA identity/signer/
  installation serial. APK 6,423,873 bytes, SHA-256
  `18a4f866390f9e6dfc06d442e4494232c226597d93e864cf0c6ce564474a2d9a`.
- Verify APK/source/archive identities after assembly, unchanged Windows
  runtime/paired-plugin receipt, every recorded E85b native-gate input hash and
  false device/framework/automation qualification flags. Restore the same
  headless service on 63625 with HTTP 200, about 3,435.2 MiB. Current native and
  APK gate evidence is recorded above; no shared JS changes this phase.
- Goal remains active. The private nonblocking body/idle-expiry/finishing layer
  is implemented and packaged. Private cache/hash/storage, exact product
  navigation/download ownership, list/permission/accept/cancel/save, unsupported
  text transport handling and actual provider/main-UI qualification remain open.
  Phone and WebView update stay deferred. No install, formal data edit, extra
  application/Home/source copy, actual model/external page action or publication.

## E86a — cache original download bytes with independent integrity proof

- Reuse the existing directory-relative upload backend, exclusive process lease,
  replacement checks and bounded crash inventory. Downloads have a distinct
  owner/header/leaf purpose, 64 MiB per file, 128 MiB reserved payload and 32
  leaves. Reserve the native maximum before writing; shrink only after sealing.
  Never infer success from an orphan's immutable pending header.
- Add worker-confined download writers: hash the original chunks, flush and hash
  the actual writable file, reopen read-only through the pinned directory and
  hash again. Withhold hash/reads until these agree. This proves storage only;
  product completion must also require the exact original BodyResult success,
  EOF, IO.close and Fetch.failRequest receipts. The file/protocol worker join is
  still open, and product download interception remains disabled.
- Fix leased CREATE_NEW/path replacement admission in the shared backend. Read
  back its unique header before confirming file identity; failed admission cannot
  unlink a replacement merely because attributes were sampled from that path.
  Failed cleanup preserves its slot/byte reservation for later inspection.
- Actual filesystem gate passes 63 checks, including original-byte readback,
  quota/slot limits, empty/binary data, tampering, replacement preservation,
  foreign inventory, abrupt child-JVM recovery and both upload/download creation
  races (`.mdo/E86-download-files.log`). Current upload regression passes
  63 primitive, 161 recovery, 99 queue and 318 native-read checks. These are
  Linux/JVM filesystem proofs, not Android UID/cache/provider qualification.
- Full JNI/protocol/worker/frame/world/input/upload/clear/ASan/UBSan/API26 gates
  pass (`.mdo/E86-native-final.log`, `.mdo/E86-native-current.json`); all recorded
  hashes match. Python passes 173/175 in 52.012 s, with only the two existing
  Windows symlink-privilege skips (`.mdo/E86-python-full.log`). Seven APK contracts
  are included. Overlay composes 115 files; Android builders now require actual
  DownloadFiles/Writer/Failure DEX definitions. E85 APK remains the last built
  output until current assembly completes.
- Goal active. File-worker/body coordination, native cache authority, exact
  navigation/download ownership, list/accept/cancel/read/save, unsupported text
  handling and real provider/main-UI qualification remain open. Phone/provider
  update remain deferred; no install, model/external-page action, formal data
  edit, app/Home/source copy or publication.

## E86b — package guarded download storage in the complete development APK

- With the existing development service stopped, apply the 115 reviewed overlay
  files and assemble the full QA Activity/service/lite-tool APK. Reuse the
  unchanged verified ARM64 engine and current exercised Windows app archive;
  verify actual DownloadFiles/Writer/Failure definitions in DEX, six JNI entries,
  current source/native/app hashes, private provider, 16 KiB alignment and v2-v3
  signatures (`.mdo/E86-android-build.log`). Preserve QA signer and serial.
- Current APK is 6,427,969 bytes, SHA-256
  `e618095e67c9e2cca881d3112f383fa8a9409f47482d4c140740d9019acc5e94`.
  Recheck APK/source/archive identity, every E86 native-gate source hash and false
  device/framework/automation flags. Restore the same headless deployment to
  HTTP 200 on 63625, about 3,435.4 MiB. Windows/plugin artifacts are unchanged.
- Goal active. Storage primitives are packaged; original-body/file-worker and
  native Context/UID joins, download lifecycle/UI/save and provider/main-UI
  qualification remain open. Phone/WebView update stay deferred. No install,
  formal-data edit, model/external-page action, application/Home/source copy or
  publication.


## E87a — join original body proof to a bounded file worker

- Add one download file worker with 32 retained native tickets and at most one
  in-flight chunk per ticket. The existing protocol worker never waits for disk,
  hashes or UI delivery. Bind each ticket once to its original Body; native
  chunks carry exact Body/offset provenance and one-shot ownership. Reject
  replay, erased buffers, another source and another queue before handoff.
- Publish completion only after original EOF, successful BodyResult, exact byte
  count, stream-close/request-abort ACKs and independently verified file sealing.
  A stored EOF remains finishing while original cleanup is pending. Completed
  bytes remain retained until release; cancelling a completed ticket is a no-op.
- Idle expiry uses the original source lifetime. Cancellation is atomic, storage
  cleanup is asynchronous, and release waits for the original terminal receipt.
  A blocked file opener does not block protocol progress or close admission.
  Shutdown confirms actual worker termination separately and retires native
  source references; unknown cleanup never becomes a successful download.
- New controlled-peer/actual-JNI/guarded-files gate passes 126 checks: blocked
  file-worker/page-command progress, separate EOF and cleanup, retained bytes,
  source loss, cancellation, replay/cross-source rejection, quota, idle expiry,
  release receipt ordering and close during blocked storage. It shares the
  existing body fixture rather than creating another app/Home/source deployment.
- Full current native/ASan/UBSan/API26 gate passes (`.mdo/E87-native-final.log`,
  `.mdo/E87-native-current.json`); every recorded input hash matches. Python
  173/175 passes in 59.869 s, with the two existing Windows symlink-privilege
  skips (`.mdo/E87-python-full.log`). Overlay composes 116 files; full APK builders
  require actual Queue/Ticket/Snapshot DEX definitions. E86 remains the last
  built APK until current assembly completes. Shared JS is unchanged.
- Goal active. This native body/storage join is not yet a product download
  action: Context/UID cache authority, exact page/download ownership, list/
  accept/cancel UI, byte-read/save transport, unsupported text handling and
  installed provider/main-UI qualification remain unfinished. Phone/WebView
  update stay deferred. No install, formal-data edit, model/external-page
  action, extra application/Home/source copy or publication.


## E87b — verify the original-body/file-worker join in the full APK

- Apply all 116 reviewed overlay files with the development service stopped;
  assemble the actual QA Activity/service/lite-tool APK with current session,
  storage and queue code. Reuse the unchanged verified ARM64 engine and current
  exercised app archive/paired plugin. Verify actual Queue/Ticket/Snapshot DEX
  definitions, six JNI entries, private provider, source/native/app payloads,
  16 KiB alignment and v2-v3 signing (`.mdo/E87-android-build.log`). Preserve QA
  identity/signer/serial. APK 6,432,065 bytes, SHA-256
  `21445c965817d12c14cc5e68715b80c81af9401da90a734bc23d96c60c16ecdf`.
- Recheck APK/current source/archive identities and Windows runtime/paired-plugin
  receipt, every E87 native-gate source hash and false device/framework/
  automation flags. Restore the same headless service to HTTP 200 on 63625,
  approximately 3,435.6 MiB. Native 126-check queue and complete current Python/
  native gates are recorded above. Shared JS and Windows/plugin outputs remain
  unchanged.
- Goal active: the native body/storage worker join is implemented and packaged;
  Context/UID authority, product ownership/list/permissions/actions/read/save,
  unsupported text handling and installed provider/main-UI qualification remain
  open. Phone and provider update stay deferred. No installation, formal-data
  edit, actual model/external-page action, app/Home/source copy or publication.


## E88a — retain the original response until explicit acceptance

- Close a missing permission boundary: taking the stream directly in its event
  callback could not wait for a later accept or apply that accept's byte/time
  settings. Add eight native Offer permits frozen from exact candidate events.
  Retention sends no continuation, stream claim or body read. It consumes the
  original event once and shares the 4,096 lifetime-key budget.
- Native accept/cancel only record atomic decisions. The existing protocol pump
  sends the fixed original claim/abort later, without supplied request IDs,
  URLs, headers, bodies or generic commands. Apply the accepted byte limit and
  start its transfer lifetime at dispatch; pending permission has an independent
  original 120-second lifetime, not renewed by page calls or inspection. Recheck
  expiry/cancellation at dispatch and rotate pending offers fairly.
- Preserve exact claim/abort acknowledgements and uncertain terminal outcomes.
  An accepted offer carries its original Body, whose separate outcome still
  governs download completion. Cancel during a claim forwards to that body;
  close/malformed/lost ACK never fabricates request cleanup confirmation. Old
  immediate native diagnostic helpers use the same existing body implementation.
- The current deferred-offer gate passes 153 checks and two actual JNI/kernel
  sessions. The peer ACKs an initial page RPC, accepts another page RPC while its
  original response remains held, then requires the exact original claim/read/
  close/abort or cancellation-only sequence. Controlled checks cover no early
  body I/O, atomic UI decisions, metadata freezing, deadlines, duplicates, limits,
  fairness, missing/malformed ACKs and cancellation during send. Full native,
  upload/worker/frame/input/ASan/UBSan/API26 gates pass
  `.mdo/E88-native-final.log` / `.mdo/E88-native-current.json`; every source hash
  matches. Existing body 900 and file-worker 126 gates remain green.
- Python 173/175 passed in 59.875 s (`.mdo/E88-python-full.log`), with only the
  existing Windows symlink-privilege skips. The final native-only fairness change
  is covered by the current full native run above. Builders now require actual
  Offer/OfferDecision/OfferResult DEX definitions. E87 APK is the last built
  output until current assembly. Overlay remains 116 files; no shared JS changes.
- Goal active. Retained response/acceptance is native groundwork; product tab/
  navigation/download ownership, Context/UID cache authority, list/actions/UI,
  private read/save transport, unsupported text handling and installed provider/
  main-UI qualification remain open. Phone/provider update stay deferred. No
  install, formal-data edit, model/external-page action, app/Home/source copy or
  publication.


## E88b — package deferred original response acceptance

- Apply all 116 reviewed files with the development service stopped and build
  the full QA Activity/service/lite-tool APK. Reuse the unchanged verified
  ARM64 engine and current exercised app archive/paired plugin. Verify actual
  Offer/OfferDecision/OfferResult DEX definitions, six JNI entries, private
  provider, source/native/app payloads, 16 KiB alignment and v2-v3 signatures
  (`.mdo/E88-android-build.log`). Preserve QA identity/signer/serial.
- APK 6,436,161 bytes, SHA-256
  `77939136f0ba98ebe6446e6e9aa19b969ce8cdfe72c521bd2f9b83c4f753c13f`.
  Recheck current APK/source/archive and Windows runtime/paired-plugin receipt,
  every E88 native input hash, and false device/framework/automation flags.
  Seven current APK contracts pass (`.mdo/E88-apk-contract.log`). Restore the
  same headless deployment on 63625 with HTTP 200, approximately 3,435.7 MiB.
  Shared JS and Windows/plugin outputs are unchanged.
- Goal active. Deferred native acceptance is packaged; Context/UID authority,
  tab/navigation ownership, product download list/actions/UI, private read/save,
  unsupported text handling and installed provider/main-UI qualification remain
  unfinished. Phone/WebView update stay deferred. No installation, formal-data
  edit, actual model/external-page action, app/Home/source copy or publication.


## E89a — park an idle download file worker

- Remove unconditional 20 ms wakeups when the cache has no unfinished source.
  Empty queues and fully retained completed files now park until an actual
  begin/offer/cancel/release/close notification. Unfinished source deadlines and
  cleanup receipts still get bounded polling; failed cleanup retains backoff.
- The actual JVM queue gate now passes 130 checks, including observed WAITING
  states for empty and retained-completed workers and successful signal/close
  wakeup. This is a worker parking proof, not Android battery/Doze qualification.
  Current full native evidence is `.mdo/E89-native-final.log` and
  `.mdo/E89-native-current.json`. Goal remains active; product Android download
  actions and installed framework/provider qualification are unfinished.


## E89b — drive native download records through the common protocol worker

- Add a physical-owner controller for frozen original offers, atomic acceptance/
  cancellation, bounded file handoff, progress, terminal receipts and confirmed
  release. Share a 32-record metadata budget across controllers; native IDs
  cannot inspect/mutate another controller. Continue ordinary responses and
  abort unproven child/capacity cases with an explicit fault/input fence.
- Add a native consumer turn to the existing physical-network pump, including
  cooperative turns during other page RPCs. It schedules fixed native reads and
  hands one buffer to the file worker without waiting for disk/hash/UI. No new
  protocol executor or re-fetch path. Query/release metadata can settle from
  immutable file/source receipts after the connection retires.
- Preserve incomplete-response failure receipts and verified retained bytes on
  disconnect. Reproduce and fix the complete-source/unfinished-verification race:
  the old disconnect path cancelled a fully received source's independent file
  validation. The real file reopen is paused while the connection closes; valid
  bytes now finish, while payload tampering still fails the independent hash.
  Preserve native connection causes instead of masking them with the queue's
  generic cancellation. Storage/hash failures carry cache origins.
- Actual controller/proof/strict-JNI/shared-worker/guarded-files gate passes 174
  checks: original acceptance, no early I/O, one slow owner vs another's progress,
  cross-owner rejection, empty data, known-size limits, global quota, EOF vs ACK,
  explicit release, retained completion after disconnect, partial loss, the
  deterministic verification race and tampering. Renderer/UI/proof peers are
  controlled; no installed provider/framework/public-UI qualification is implied.
  Failure/fix evidence: `.mdo/E89-verification-loss-repro.log` and
  `.mdo/E89-verification-loss-fixed.log`.
- Full current native/ASan/UBSan/API26 gates pass `.mdo/E89-native-final.log` /
  `.mdo/E89-native-current.json`; all recorded source hashes match. Existing body
  900, deferred-offer 153 and queue 130 checks remain green. Python 173/175 passes
  in 58.886 s (`.mdo/E89-python-full.log`), with only the existing two Windows
  symlink-privilege skips. Overlay composes 117 files and builders require actual
  controller/Snapshot/Release/Budget/Row DEX definitions. E88 remains the last
  built APK until current assembly. Shared JS is unchanged.
- Goal active. The coordinator is integrated with the common pump and exercised
  end to end through private native components; the main Activity still uses its
  previous continue-only callback until Context/UID service ownership and product
  lifecycle/query/action routing join it. Also open: frozen filename/MIME display
  metadata (current core JSON uses placeholders), private read/save transport,
  exact navigation/popup ownership, unsupported text/blob handling and installed
  provider/main-UI qualification. Phone/provider update remain deferred. No
  install, formal-data edit, model/external-page action, extra app/Home/source
  copy or online publication.

## E89c — verify the controller and idle-worker fix in the full APK

- With the existing service stopped, apply all 117 reviewed files and assemble
  the actual QA Activity/service/lite-tool APK. Reuse the unchanged verified
  ARM64 engine and exercised app archive/paired plugin. Verify actual controller/
  Snapshot/Release/Budget/Row DEX implementations, six JNI entries, private
  provider, current source/native/app payloads, 16 KiB alignment and v2-v3 signing
  (`.mdo/E89-android-build.log`). Preserve QA identity/signer/serial.
- APK 6,440,257 bytes, SHA-256
  `4b30e75e27f2ed7855a9a4f2182ecf3aef51f57fdd19fe365550a843fd6ac1b7`.
  Recheck current APK/source/archive and Windows runtime/paired-plugin identities,
  every E89 native input hash and false device/framework/automation flags.
  Seven current APK contracts pass (`.mdo/E89-apk-contract.log`). Restore the
  same headless deployment to HTTP 200 on 63625, about 3,436.0 MiB. Shared JS and
  Windows/plugin outputs are unchanged.
- Goal active. Native controller/core parking is implemented and packaged, but
  main Activity callback/commands, Context/UID service authority and product
  lifecycle/read/save integration remain open, together with frozen display
  filename/MIME, original navigation/popup association, unsupported text/blob
  handling and installed provider/main-UI qualification. Phone/WebView update
  remain deferred. No installation, formal-data edit, actual model/external-page
  action, extra application/Home/source copy or publication.


## E90 — join application-owned download controllers to the actual Activity

- Forward exact native response events/consumer turns to each physical owner's
  controller. Add the Application-Context service with shared metadata/file
  budgets and worker-only cache/UID/mode validation. Native Context authority is
  implemented and SDK-compiled, not yet qualified on an installed Android device.
- Wire native list/get/accept/cancel/release commands with strict integer bounds,
  exact view/channel ownership and bounded receipt observation. Pending downloads
  fence agent mutations. Explicit view close retains file-retirement evidence;
  admission/repeated close cannot claim completion while private files remain.
  Release preserves cache replacement errors and never reports cleanup on a
  new channel. Private reads remain explicitly unsupported.
- Exact create/lifecycle fragments pass 155 checks; the new actual native/file/
  controller/product-command gate passes 30. Full source-hashed JNI/native,
  ASan/UBSan/API26 gates pass (`.mdo/E90-native-final.log` /
  `.mdo/E90-native-current.json`). All 95 native and 11 creation hashes match.
  Python passes 173/175 in 52.191 s, only two existing symlink privilege skips
  (`.mdo/E90-python-full.log`). Seven current APK contracts pass.
- Apply 118 reviewed files with the dev service stopped and build the complete
  QA Activity/service/lite-tools APK (`.mdo/E90-android-build.log`): 6,444,353 bytes,
  SHA-256 `e3436d042cc2de2acd65a419428a532e9957acc3de719324fb7e16214edc4410`.
  Check actual Runtime/Release/controller DEX, provider, six JNI entries,
  source/native/app archive identity, ARM64/16 KiB layout and v2/v3 signing.
  Restore the same service to HTTP 200 on 63625, about 3,436.2 MiB. Windows and
  paired-plugin outputs are unchanged; device/framework/automation flags false.
- Goal remains active. Private byte-read/save transport, frozen filename/MIME,
  exact navigation/popup ownership, unsupported text/blob behavior, orderly
  takeover/rearm and installed provider/main-UI qualification remain open.
  Phone/provider update stay deferred. No install, formal-data edit, model or
  external-page action, additional app/Home/source copy or online publication.


## E91 — read retained verified download bytes without UI/protocol I/O waits

- Add four one-shot native range receipts (65,536 bytes each) to the existing
  file worker. Require original completed source/file proof; retain descriptor
  identity/size checks, start/EOF hash checks and final receiver-stream hashing.
  Alternate reads with other file work. No path/descriptor/digest authority
  comes from a caller. Reads do not wake idle workers once receipts settle.
- Consume each buffer once and erase retained data on cancellation/release/close.
  Observe blocked reads until actual worker unwind; cache tampering revokes
  other retained ranges and invalidates old completion, preserving its cause.
  Wire actual Activity reads to exact view/channel leases with bounded polling,
  cancellation and sanitized native failure receipts. Retained verified bytes
  remain readable after protocol retirement. Full capability stays false.
- File queue passes 237 assertions, controller 186 and exact product commands 73.
  Include real descriptor blocking, continued page RPC progress, release while
  blocked, stale channels/owners, timeouts, scheduler failure, bounded buffers,
  one-shot ownership and same-size tampering. Full current JNI/native/ASan/UBSan/
  API26 gates pass (`.mdo/E91-native-final.log` / current JSON); 95 native and
  11 retained creation input hashes match. Python passes 173/175 in 57.866 s
  (`.mdo/E91-python-full.log`), with two existing Windows symlink privilege skips.
- With the sole dev service stopped, apply 118 reviewed files and build the real
  QA Activity/service/lite-tools APK. Check actual Read/DownloadRead DEX, all
  source/native/app payloads, private provider, six JNI entries, ARM64/16 KiB
  layout and v2-v3 signing (`.mdo/E91-android-build.log`). APK 6,448,449 bytes,
  SHA-256 `7b60130da470e533f702c62b26b27f52bc9c257091e8e5bfb55126f71110ab9f`.
  Seven final APK contracts pass. Restore the same service to HTTP 200 on 63625,
  about 3,436.3 MiB. Windows and paired-plugin hashes remain unchanged.
- Goal active. Native private range transport is implemented and packaged; shared
  Android save/public automation qualification, frozen name/MIME, original
  navigation/popup ownership, unsupported text/blob behavior, orderly takeover/
  rearm and installed provider/main-UI qualification remain open. Phone/provider
  update remain deferred. No install, formal-data edit, model/external-page
  action, extra app/Home/source copy or online publication.


## E92 — freeze original advisory filename and normalized MIME

- Replace download.bin/empty-MIME placeholders with immutable original response
  display metadata. Prefer valid UTF-8 filename* (support legacy ISO-8859-1),
  correctly handle quoted escapes and semicolons, preserve literal plus/ordinary
  percent text, and fall back for unsupported/malformed extended encodings.
  Strip path segments and reject unsafe/control/format/reserved/oversized names.
  Keep names advisory and media types bounded/parameter-free. No URL filename
  fallback, request/body/header retention, content sniffing or path authority.
- Actual parser/dispatcher/JNI metadata checks pass 347 assertions. Original
  offers retain frozen names after event mutation (154 offer checks). Controller
  snapshots expose only this bounded display metadata, not URL/body/native IDs.
  File queue 237, controller 186 and exact product commands 73 remain green.
  Full current native/ASan/UBSan/API26 gates pass (`.mdo/E92-native-final.log` /
  current JSON); all 95 native and 11 retained creation input hashes match.
  Python passes 173/175 in 56.323 s (`.mdo/E92-python-full.log`), only two existing
  Windows symlink privilege skips. Seven final APK contracts pass.
- Stop the sole dev service, apply the same 118 reviewed files and build the
  actual QA Activity/service/lite-tools APK (`.mdo/E92-android-build.log`). Verify
  source/native/app identity, actual DEX, six JNI entries, private provider,
  ARM64/16 KiB layout and v2-v3 signing. APK remains 6,448,449 bytes, new SHA-256
  `70d345bf2f1db9eac2819e723173ccb48d1d4b0e6bc34cec04b5b23f38e7803f`.
  Restore the same service to HTTP 200 on 63625, about 3,436.4 MiB. Windows and
  paired-plugin artifact hashes are unchanged. Device/framework/automation false.
- Goal active. Display metadata and private reads are implemented/packaged;
  original navigation and root/subframe/popup association, shared Android save/
  public automation qualification, unsupported text/blob behavior, orderly
  takeover/rearm and installed provider/main-UI qualification remain open. Phone
  and current WebView stay deferred. No install, formal-data edit, model or
  external browser-page action, additional app/Home/source copy or publication.


## E93 — prove download frame ancestry and retire original navigation scopes

- Fix a provenance gap: a candidate in the root CDP session was not necessarily
  a root-frame response. Freeze native frameId before callbacks, establish a
  native frame tree before Fetch activation, and follow original parent/frame
  events. Joined nested frames now have their own bounded ancestry/navigation
  receipts; unknown/missing/process-swapped sources cannot impersonate root.
  No URL/title comparison or new execution-world authority is introduced.
- Bound the graph to 32 frames/depth and 4,096 lifetime associations. Stable
  bootstrap uses at most eight readonly queries under the original deadline.
  Failed proof records that Fetch activation was never dispatched. Transfer the
  physical marker's cleanup ownership to the complete network facade, so UI
  close immediately revokes provenance with no cross-thread frame-map access.
- Parent navigation/removal cancels pending/partial original downloads and keeps
  native_frame causes. Preserve a complete original source while independent
  file validation is still running; valid bytes finish and same-size tampering
  still fails. Completed data remains readable with a separate source-current
  diagnostic. Download metadata exposes opaque network frame/document tokens,
  never the wire IDs. Native ancestry faults use finite trusted event codes and
  survive the worker and exact Activity failure receipt.
- Exact native ancestry checks pass 50, physical binding 2,022, cooperative
  worker 195, controller/actual guarded files 275, product download commands 73,
  metadata 350 and creation/lifecycle 159. Include genuine descriptor barriers,
  nested sources, parent removal/commit, bootstrap races, admission/depth/lifetime
  limits, stale JSON, source preservation/tampering, and explicit unsupported
  sources. Full native/ASan/UBSan/API26 gates pass (`.mdo/E93-native-final.log` /
  current JSON); all 97 native and 11 creation input hashes match. Python passes
  173/175 in 57.621 s (`.mdo/E93-python-full.log`), only the two existing Windows
  symlink privilege skips. The new ledger's unproved-session revocation failure
  and fix are retained in `.mdo/E93-unproved-session-repro.log` and the final gate.
- Stop the single dev service, apply 119 reviewed files and build the full QA
  Activity/service/lite-tools APK (`.mdo/E93-android-build.log`). Verify actual
  NetworkFrames/Origin/Frame/Scope DEX, all source/native/app payloads, private
  provider, six JNI entries, ARM64/16 KiB layout and v2-v3 signing. APK 6,452,545
  bytes, SHA-256 `de11dfaf2c39a3a36a82cdb902ca09566ca518faa3c56b4daa41ea088002f325`.
  Seven final APK contracts pass. Restore the same service to HTTP 200 on 63625,
  about 3,436.6 MiB. Windows/paired-plugin identities remain unchanged, and
  device/framework/automation flags remain false.
- Goal active. Cross-process child interception, original popup/download-only
  retention, navigation-error association, shared Android save/public automation
  qualification, unsupported text/blob handling, orderly takeover/rearm and
  installed provider/main-UI qualification remain open. Phone/current WebView
  stay deferred. No install, formal-data edit, model or external browser-page
  action, additional app/Home/source copy or online publication.


## E94 — retire original downloads before closing takeover interception

- Add common-worker retirement for the original physical connection. Revoke
  ordinary frame execution immediately, quiesce Agent download admission and
  cancel pending/partial original sources. Keep only cleanup authority while
  manual page use continues. Require source/control drainage before one
  Fetch.disable call; record its ACK independently of native socket close.
  Repeated takeover does not reset the five-second native deadline; UI observes
  a bounded 5.5-second fallback. No URL load, click replay or refetch is added.
- Preserve completed bytes and complete-source/in-progress file verification
  across takeover; valid bytes finish while tampered bytes fail. Unknown cleanup
  cannot produce a released/cancelled-success receipt or a false disabled flag.
  Wire actual Activity takeover to this path with explicit draining/retired
  state. Release keeps manual state until a fresh policy is explicitly rearmed;
  it no longer silently reports control restored on the retired connection.
- Reproduce and fix prior-cause masking through both quiesce and disconnect.
  A controlled cache-open failure was replaced first by user cancellation, then
  by generic native body cancellation. Keep cache/timeout/frame/policy roots
  through later user cleanup (`.mdo/E94-prior-cache-failure-repro.log`,
  `.mdo/E94-prior-cache-failure-native-mask.log` and fixed log). This controlled
  factory fault does not qualify an installed Android cache/UID implementation.
- Retirement/native source/common-worker/real-files gate passes 58 checks;
  exact product creation/takeover/lifecycle fragments pass 175. Cover original
  pending/partial sources, completed data, a blocked real verification reader,
  tampering, future manual responses, missing ACKs, timeouts, repeated calls,
  immediate frame-marker revocation and no page reload/destruction. Full native/
  ASan/UBSan/API26 gates pass (`.mdo/E94-native-final.log` / current JSON); all
  98 native and 11 product input hashes match. Python passes 173/175 in 50.890 s
  (`.mdo/E94-python-full.log`), only two existing Windows symlink privilege skips.
- Stop the sole dev service, apply the same 119 reviewed files and build the
  complete QA Activity/service/lite-tools APK (`.mdo/E94-android-build.log`).
  Actual DEX/source/native/app identity, private provider, six JNI exports,
  ARM64/16 KiB layout and v2-v3 signing pass. APK 6,452,545 bytes, SHA-256
  `2ce13ed7860e583a3c927c22e71e07830ba0b8b8cfd5d2ce227a3583188ac0d3`.
  Seven final APK contracts pass. Restore the same service to HTTP 200 on 63625,
  about 3,436.9 MiB. Windows and paired-plugin identities are unchanged;
  device/framework/automation flags remain false.
- Goal active. Same-page explicit rearm needs new proof without reload/input
  replay, retained old-generation download routing and honest namespace quotas.
  Original popup/download-only lifetime, cross-process interception, navigation
  errors, Android public save/automation qualification, unsupported text/blob
  handling and installed provider/main-UI qualification remain open. Phone and
  current WebView stay deferred. No installation, formal-data edit, model or
  external browser-page action, extra app/Home/source copy or publication.


## E95 — explicitly rearm the original manual page and retain download generations

- Implement asynchronous human return-control on the same physical WebView.
  Require old native/source cleanup, the exact assigned profile/channel and a
  stable loaded page. Keep manual input/document admission blocked until new
  native target/frame proof and Fetch activation ACKs succeed. Never load a URL,
  replay input, reconstruct an old request or destroy the page on rearm failure.
  Navigation, channel/profile loss, scheduler/start failure, quota exhaustion
  and the original fifteen-second deadline preserve manual state. Repeated
  release does not start another connection or reset its deadline.
- Reuse one bounded download controller/file authority across opaque connection
  tickets. Stale events/turns/disconnects cannot acquire the new generation.
  Require a terminal cleanup receipt even before the initial protocol turn.
  Retain old IDs, ordering, files and the shared 32-record budget; old turns
  cannot claim/read old streams through a new connection. Complete sources can
  finish independent verification; altered bytes still fail. Pending private
  reads/releases keep their original view/controller/channel/deadline through
  replacement. Reuse the existing physical owner's sixteen-declaration quota
  conservatively; explicit release does not reset it or reload to bypass it.
- Same-view/common-worker/JNI/guarded-file rearm passes 111 checks; exact product
  download commands pass 81, including a blocked real reader and queued release
  across replacement. Creation/takeover/rearm/lifecycle fragments pass 237.
  Full native/ASan/UBSan/API26 gates pass (`.mdo/E95-native-final.log` / current
  JSON); all 99 native and 11 product input hashes match. Python passes 173/175
  in 54.691 s (`.mdo/E95-python-full.log`), only two existing symlink-privilege
  skips. The full existing controller/retirement/frame/worker gates also pass.
- Stop the single dev instance, apply 119 reviewed files and rebuild the complete
  QA Activity/service/lite-tools APK (`.mdo/E95-android-build.log`). Actual DEX
  includes the new connection ticket; reviewed source/native/app identity,
  private provider, six JNI exports, ARM64/16 KiB layout and v2-v3 signing pass.
  APK 6,456,641 bytes, SHA-256
  `95bfda6db251d7b25fefd4c5dede728d063dd4c5fa725bec62c3cd8f60795a05`.
  Seven final APK contracts pass. Restore the same service to HTTP 200 on 63625,
  about 3,437.0 MiB. Windows/paired-plugin identities remain unchanged;
  device/framework/automation qualification remains false.
- Goal active. Original popup/download-only lifetime, cross-process interception,
  navigation-error association, Android public save/automation qualification,
  unsupported text/blob handling, installed provider/main-UI checks and the other
  documented full-goal qualifications remain open. Phone/current WebView stay
  deferred. No installation, formal-data edit, model or external browser-page
  action, extra app/Home/source copy or online publication.

## E96 — preserve original native navigation acknowledgments alongside downloads

- Implement one fixed asynchronous Page.navigate on the exact original physical
  network connection. Native proof supplies the root frame, never a caller's
  frame/session selector. Keep original Fetch/body cleanup progressing before
  navigation ACKs; retain only bounded flags, fixed outcomes and 22 known safe
  network-error identifiers, with no raw reply, provider prose or URL retention.
  Separate acknowledgment from page/file completion. Native true/false/missing
  download flags remain distinct; malformed/wrong-source replies close the
  connection and terminal results cannot be upgraded by late acknowledgments.
- Bound one pending navigation and 4,096 lifetime issues per session. An original
  configurable 100..600,000 ms deadline survives short polling and other RPCs;
  sending remains bounded to 100 ms. Partial sends/connection loss retain effect
  uncertainty; no implicit replay occurs. Unsupported method receipts record no
  possible navigation effect, and native retirement includes outstanding ACKs.
- Verify pinned primary sources: Chromium 138's Page.navigate has no isDownload
  result, while current protocol makes it optional. Missing classification plus
  ERR_ABORTED returns download-unproven, not invented success. WebView abort
  filtering is not a download proof. Record source revisions/hashes and explicit
  qualification boundaries in `host/android/cdp-protocol.json`; preserve the
  current WebView version and existing product error handling.
- Navigation gate passes 149 checks, including two real local-kernel JNI/socket
  sessions. Hold a navigation reply until the same connection completes original
  body/stream/request cleanup; route a native DNS failure during an ordinary RPC.
  Actual native allocations/descriptors balance. Native binding passes 2,040,
  including proven-root dispatch and retired admission; prior rearm/download/
  worker gates also pass. Full ASan/UBSan/API26 and creation/lifecycle gates pass
  (`.mdo/E96-native-current.json`, `.mdo/E96-create-current.json`); all 101 native
  and 11 product hashes match. Python passes 173/175 in 58.217 s, with only two
  existing Windows symlink-privilege skips (`.mdo/E96-python-full.log`).
- Stop the sole dev instance, apply 119 reviewed files and rebuild the complete
  QA Activity/service/lite-tools APK (`.mdo/E96-android-build.log`). Both actual
  navigation receipt DEX classes, reviewed source/native/app identity, private
  provider, six JNI exports, ARM64/16 KiB layout and v2-v3 signing pass. APK
  6,456,641 bytes, SHA-256
  `a630743650430f802a415eb2f36bfdc68b47328c3d8c526e74a7dccafcf3768b`.
  Seven final APK contracts pass. Restore the same service to HTTP 200 on 63625,
  about 3,437.3 MiB. Windows and paired-plugin identities remain unchanged;
  device/framework/automation qualification stays false.
- Goal active. Next join the original UI navigation operation to these native
  receipts and download state; the legacy product navigation path is not yet
  replaced or qualified. Popup/download-only lifetime, cross-process interception,
  Android public save, text/blob handling, installed provider/main-UI and other
  documented full-goal qualifications remain open. Phone/current WebView stay
  deferred. No install, formal-data edit, model or external browser-page action,
  extra app/Home/source copy or online publication.


## E97 — join managed product goto to its original native navigation receipt

- Route an armed physical Android owner's goto through the existing common
  protocol worker. Return one opaque navigation_ref immediately; keep source
  I/O, Fetch/body progress and original acknowledgments on that same worker.
  Refuse another pending navigation, pending original downloads, stale leases
  and invalid URL/deadline inputs. The original admission deadline includes queue
  time; cancellation/decision monitors never wrap socket I/O. Queued cancellation
  proves no effect, while cancellation after dispatch retains its original result
  and never replays the URL or upgrades a terminal outcome.
- Join the actual product navigation reservation, view/epoch/profile/channel and
  original result. Only exact terminal proof can restore the unchanged original
  document's loading/admission fence. Unidentified WebView errors cannot release
  an active native request, and old results cannot overwrite new page/error state.
  Stop, page replacement, takeover, channel loss and retirement revoke the original
  queue. The private navigation_cancel route matches the exact original alias and
  channel, remains available for cleanup during manual mode, and never stops or
  reloads a later human page. Fix a final admission regression: human takeover
  releases only the original UI reservation and permits manual navigation while
  its native ACK remains pending. Its later result cannot restore the human
  page's loading/error state. Keep native status bounded and free of wire IDs.
- Shared JS handles original acknowledged failure, known download, known
  same-document and actual page completion separately. Keep incomplete dialog/
  chooser/download checkpoints actionable; cancellation/context loss/timeout
  requests exact original queue cleanup in finally. Missing download/loader proof
  remains uncertain. New retained download IDs are returned only as browser-scope
  candidates, explicitly not proof of per-request association; old retained
  completed files cannot be substituted for the new navigation.
- Native broker passes 27 assertions with the actual cooperative worker, original
  controller and guarded file queue: hold one navigation ACK while original and
  independent downloads complete, cancel a queued request without waiting on the
  worker, retain a dispatched result during cancellation, preserve original queue
  deadlines and reject the old lease after same-view rearm. The lower native
  navigation gate passes 156, including two real local JNI/socket sessions;
  binding 2,040, controller 275 and rearm 111 also pass. Full ASan/UBSan/API26
  passes; all 103 native and 13 product input hashes match the retained evidence
  (.mdo/E97-native-current.json, .mdo/E97-create-current.json).
- Actual product fragments pass 335 lifecycle assertions, including reference
  cancellation and manual/channel/epoch races. All 338 JS cases pass sequentially
  (.mdo/E97-node-full.log); Python passes 173/175 in 69.327 seconds, with only two
  existing Windows symlink-privilege skips (.mdo/E97-python-full.log). Seven final
  Android package contracts pass; no unavailable platform check is counted.
- Stop the sole dev instance, apply the reviewed overlays, refresh the Windows
  program and pass all 12 actual packed runtime checks, including C/JS/native
  observation, input, screenshot, reset/error reporting and recovery navigation.
  Windows artifact is 6,935,473 bytes, SHA-256
  02c29e4a9cf055cc7525cfcb47cef83ab56f3679ff6655f2032437ffb76a3dbb.
  Rebuild the complete Android Activity/service/lite-tools APK: 6,460,737 bytes,
  SHA-256 dd8f53b48d6fa32d9588ee7683560fc5df8c3fa1354943b3b070130823f0c557.
  Verify actual new UI/broker DEX definitions, private provider, six JNI exports,
  source/archive identity, ARM64/16 KiB layout and retained v2/v3 signer. Paired
  plugin stays unchanged (247,351 bytes / 22 files, c6b74a2780f13d08570e61bdaf58cbcbc8c4678dc06ce0348de5e8a370ae5d41).
  Evidence: .mdo/E97-windows-runtime.log, .mdo/E97-android-build.log,
  .mdo/E97-final-identities.log and .mdo/E97-android-receipt.json. Restore the same
  headless deployment to HTTP 200 on 63625, about 3,438.8 MiB.
- Goal active. This compiles and tests the armed product goto route; initial
  target creation and back/forward/reload still use the legacy navigation path.
  Installed-provider/main-UI/public JS-C qualification, precise download-to-request
  joining, popup/download-only lifetime, cross-process interception, Android
  public save and text/blob handling remain open alongside the other documented
  full-goal work. Device/framework/automation flags remain false. Phone/current
  WebView stay deferred. No installation, formal-data edit, external page/model
  action, extra app/Home/source copy or online publication.


## E98 — join initial target creation to original native navigation

- After native blank warmup and original Fetch activation, admit the initial
  HTTP(S) target through the same fixed native coordinator as goto. No target
  WebView.loadUrl fallback/replay remains on this managed route; an about:blank
  target reuses the warmup itself. Creation immediately returns created,
  downloads_intercepted, one navigation_ref and initial_navigation_pending.
  This confirms native creation/admission, not page commit or file completion.
- Share the original 15-second startup deadline across warmup, native activation
  and target queueing. The worker receives only the remaining original budget.
  Derive possible target effects from the exact retained native ticket rather
  than a Boolean set before a legacy call. Preserve known unsent cleanup versus
  uncertain dispatched effects and never revise a previously delivered receipt
  when cleanup is confirmed later. Retain the original physical view after an
  uncertain target, with explicit user cleanup/recovery rather than automatic
  recreation or replay.
- Recheck physical owner/profile/channel/epoch after cancellation callbacks and
  after setting the input fence, before native queue admission. Reentrant channel
  loss cannot issue the old target; rollback removes only its own reservation
  and cannot clear a newer document's state. Begin cleanup-only download/native
  retirement before releasing the original UI navigation reservation on human
  takeover, so reentrant UI callbacks cannot acquire obsolete input authority.
- Keep initial navigation pending until both its original native outcome and
  the actual page transition are established. Page callbacks alone do not invent
  an ACK, and a late blank completion cannot expose the warmup as the target.
  Reproduce and fix a missing-loader regression: a non-download ACK from initial
  blank warmup is not the same-document case supported by ordinary goto. It must
  retain the original reservation until an actual target-page event. Preserve
  explicit download and uncertain/failure outcomes without guessed completion.
- Shared JS preserves bounded initial native failure details ahead of generic
  page-error text while keeping original-input effects separate from a read-only
  observation. Missing download proof returns download_identity_unavailable.
  The native create helper validates its optional asynchronous admission fields;
  inconsistent envelopes stay uncertain. On original display loss or failed
  settlement, best-effort exact-reference cancellation is bounded to one second
  and cannot stop/close/reload a physical view now used by a human. Older Windows
  creation and manual popup behavior remain covered by actual/contract checks.
- Final exact product fragments pass 383 assertions, covering initial page/ACK
  ordering, download/failure/unknown/unsupported/cancel outcomes, original startup
  budgets, scheduler failure, reentrant channel loss, duplicate ready and clean
  later admission. The missing-loader regression first fails in
  .mdo/E98-create-loader-regression.log, then passes the final gate. All 343 JS
  cases pass sequentially in 19.772 seconds (.mdo/E98-node-full.log). A rerun
  while the dev service was stopped correctly failed local HTTP-dependent tests;
  preserve .mdo/E98-node-no-service.log and verify all pass after restoring that
  same instance. This was an unavailable local test server, not a product repair.
- Full native ASan/UBSan/API26 gates pass (.mdo/E98-native-current.json): broker
  27, lower navigation 156 with two actual local JNI/socket sessions, binding
  2,040, original controller 275 and rearm 111. All 103 native and 13 final product
  input hashes match. Python passes 173/175 in 53.261 seconds, only two existing
  Windows symlink-privilege skips; final Java regression is additionally verified
  by the 383-assertion gate. Seven final Android package contracts pass.
- Refresh Windows program and pass all 12 actual packed C/JS/native checks,
  including observation, trusted input, synthetic screenshot, network reset and
  recovery navigation; source/installed/host paired-runtime identity and the
  unchanged official-update policy also pass. Artifact: 6,935,916 bytes, SHA-256
  93c4e8848f0dcc316f786323ff71bbf0d60d5eabbad0ece018987b633efc618f.
  Build the final full QA Activity/service/lite-tools Android APK: 6,464,833 bytes,
  SHA-256 60469e437555d67619b358265c69dcc6db67e176cecbc7265cc870a6789413d1.
  Validate actual DEX definitions, private provider, six JNI exports, ARM64/16 KiB
  layout, matching source/archive identities and retained v2/v3 development signer.
  Native engine and portable paired plugin remain unchanged (247,351 bytes,
  22 plugin files). Evidence: .mdo/E98-windows-runtime.log,
  .mdo/E98-android-build.log, .mdo/E98-final-identities.log and
  .mdo/E98-android-receipt.json. Restore the sole headless deployment to HTTP 200
  on port 63625, about 3,440.3 MiB; no extra application/Home/source copy.
- Goal active. Managed initial target and goto are now wired; back/forward/reload
  remain on the legacy route. The current page check joins the original ACK to
  the retained UI reservation/epoch; exact native-loader-to-page association is
  still unqualified. Android installed-provider/main-UI/public JS-C qualification,
  original download/request joining, uncertain-outcome UI polish,
  popup/download-only lifetime, cross-process interception, public Android save
  and unsupported text/blob handling remain open with the other full-goal work.
  Device/framework/automation qualifications remain false. Phone and current
  WebView stay deferred. No install, formal-data edit, model/external page action,
  provider upgrade, additional deployment copy or online publication.


## E99 — correlate original native loader and root commit

- Retain only the validated original native loader privately in its immutable
  navigation ticket. A physical-network wrapper carries exact connection
  ownership and its pre-dispatch frame revision; a different connection cannot
  query it even if frame/loader strings coincide. Match only subsequent validated
  root Page.frameNavigated events. Page.documentOpened and child-frame events
  cannot establish a commit. A 32-entry private root history handles ACK/event
  reordering and bounded overflow; pre-admission or evicted evidence never grants
  completion. Expose only opaque native document aliases and bounded outcome/currentness flags,
  never raw frame/loader/session selectors or replies.
- A matched origin records past commitment separately from current validity.
  Root document replacement, document.open and source teardown revoke current
  proof without upgrading an old receipt. Preserve the original ACK on native
  commit timeout and retain a separate bounded page_error. Use commit_revoked,
  not an asserted page-change cause, because a closed source can revoke proof
  without establishing that the physical page changed. Native file/body cleanup
  and the existing worker remain shared; no URL replay, reconnect, extra executor
  or per-navigation disk work is introduced.
- Join the native commit with the retained UI reservation/epoch before finishing
  the product's initial navigation stage. A completed join is monotonic, so later
  ordinary navigation cannot resurrect the old initial operation. Old watchers
  lose authority when the explicit current operation changes. Missing loader
  metadata on initial cross-document targets now returns commit_unproven rather
  than being promoted by UI callbacks. Original startup/action budgets remain in
  force while commit evidence is absent; takeover/cancellation retain only exact
  original cleanup authority.
- Shared JS distinguishes ACK, known download, same-document handling and native
  cross-document commitment. Require a valid opaque committed identity and current
  proof before reading, then recheck the same original reference/document after
  observation. Revoke only the old queued request on uncertainty; no stop/reload
  fallback or target resubmission. This is an event-stream proof plus client
  checks, not yet a direct guard on FrameDriver's separate evaluation connection.
  That observation connection still needs its own original-loader constraint;
  installed-provider/public JS-C end-to-end qualification remains open.
- Verify the pinned primary Page definition: navigate's optional loader is absent
  for same-document navigation; frameNavigated associates the frame with its new
  loader, and documentOpened is a distinct event. Keep the same pinned source/hash
  in host/android/cdp-protocol.json and update its private commit status/limits.
  No new caller wire selectors or runtime dependencies are exposed.
- Final native broker passes 86 assertions with actual shared worker/controller/
  guarded files: ACK/event order, wrong root loader, child event, document.open,
  pre-admission evidence, history eviction, another physical owner and closing a
  committed source. Binding passes 2,058 including foreign-wrapper rejection.
  Lower navigation passes 166, with two original body/ACK real JNI/socket sessions
  plus two new real commit sessions in opposite event orders. Each new session
  observes document.open revoking its original matched origin; strict native
  allocations, descriptors and socket-slot checks remain balanced.
- Diagnose the initial native descriptor assertion as UUID's JVM entropy source
  initialization: measure 0 then 2 /dev/random or /dev/urandom descriptors on first
  frame-ledger construction. Initialize that process resource before sampling
  the per-session baseline; do not weaken the session allocation/descriptor/
  slot assertions. Preserve .mdo/E99-native-cold-entropy-failure.log and the final
  counters in .mdo/E99-native-current.json. No production descriptor leak is
  claimed or hidden by that test-baseline correction.
- Exact product lifecycle gate passes 397; all 344 JS cases pass sequentially in
  19.601 seconds. Python passes 173/175 in 56.365 seconds, only two existing Windows
  symlink-privilege skips; final revocation wording/current-source tests also pass
  the targeted lifecycle/dispatch gates. Full native ASan/UBSan/API26 passes and
  all 103 native plus 13 product input hashes match. Seven final Android package
  contracts require actual definitions of the new private Network.Navigation and
  frame Commit classes, not references to them.
- Refresh the complete Windows program and pass all 12 actual packed C/JS/native
  runtime checks. Artifact: 6,936,188 bytes, SHA-256
  4e0a260c5334cf1f923143883be3113abec776ebdeedd6c9a8e1303b646785f8.
  Final full QA Activity/service/lite-tools Android APK: 6,464,833 bytes, SHA-256
  0443478181e5182c9e9acff30eb4341ee6e001af926ffa3245e9c69c906cbe1f.
  Verify actual DEX definitions, six JNI exports, private provider, original
  ARM64/16 KiB engine, source/archive identities and retained v2/v3 signer. Native
  engine and 247,351-byte / 22-file paired plugin remain unchanged. Evidence:
  .mdo/E99-windows-runtime.log, .mdo/E99-android-build.log,
  .mdo/E99-final-identities.log and .mdo/E99-android-receipt.json. Restore the same
  headless service to HTTP 200 on 63625, about 3,440.6 MiB.
- Goal active. Next constrain the separate observation/evaluation connection to
  the original navigation loader; do not infer full payload identity merely from
  the two native status snapshots. Same-document event association, history/reload,
  original download/request association, remaining transfer lifetime, Android
  public save, text/blob handling and documented full-goal work remain open.
  Installed Android/provider/main-UI/public JS-C verification is still deferred;
  device/framework/automation flags stay false. No phone/provider upgrade,
  installation, formal-data edit, model/external page action, extra app/Home/source
  copy or online publication.

## E100 — bind goto observation to the original native loader (2026-10-31)

- Close the separate-connection gap for cross-document `navigate_to` observation.
  FrameDriver carries only the original opaque `nv-` reference in native control
  metadata, on every inventory/evaluation/revalidation subread. The reference is
  never serialized into page helper arguments. Ordinary later observations do
  not inherit an old navigation constraint. An adapter without this proof returns
  explicit unsupported capability; it cannot silently execute an unconstrained
  page script. Same-document and initial-create observation correlation remain
  separate work, as recorded in the private protocol inventory.
- Resolve the exact record on the UI thread under its original view, channel,
  profile, current operation, document transition and manual-control fences. Send
  only its native ticket to the shared worker. Before evaluation and after its
  result, require the same discovered physical target, original frame and original
  navigation loader on the document binding; recheck its actual root frame tree
  and the native commit's continued validity. Revalidate the original UI record
  again before delivery. No caller target/loader/session selector is added.
- Add bounded worker guard hooks without another executor or dependency. Failure
  before the operation retains a no-evaluation receipt; failure after the operation
  clears its payload while preserving evaluation uncertainty. Cancellation and
  teardown release stored guards. An unconfirmed constrained navigation observation
  returns its failure immediately without replaying navigation or looping reads.
- Native broker passes 160 assertions, including wrong document loader, colliding
  frame/loader strings on another target, post-operation page replacement, ticket
  cancellation, physical source closure and payload clearing. Command grammar
  passes 61; existing worker passes 191. Exact product lifecycle passes 432,
  including 35 new UI proof assertions for pre-commit/reserved navigation, current
  original record, unrelated reference/view, takeover, cancellation, replacement
  and retirement. The UI/framework peers are controlled; these are not installed
  Android/provider qualification claims.
- All 349 JS cases pass sequentially in 19.491 seconds. Python passes 173/175 in
  55.418 seconds, with only the two existing Windows symlink-privilege skips.
  Full native ASan/UBSan/API26 checks pass; all 103 native and 13 product input
  hashes match. Seven final Android package contracts also pass, requiring the
  actual new private Worker.Guard DEX definition rather than a reference.
- Refresh both development programs and pass all 12 actual packed Windows runtime
  checks. Windows: 6,936,426 bytes, SHA-256
  1078e580d903fbbbf3e1ee8de9aa8ad46820a0b28e1e6d81517d669f23188cfb.
  Full Android QA APK: 6,464,833 bytes, SHA-256
  edc74851714ac3b5ef24c5d648e4835aa503b4d9914570f6be10fac2ffe32ffb.
  Actual DEX, private provider, six JNI exports, ARM64/16 KiB engine, archive/source
  identities and retained v2/v3 signer pass. Engine and paired 247,351-byte/22-file
  plugin remain unchanged. Evidence: .mdo/E100-native-current.json,
  .mdo/E100-product-current.json, .mdo/E100-final-identities.log,
  .mdo/E100-android-receipt.json and corresponding E100 test/build logs.
- Restore the sole headless service: PID 14704, HTTP 200 on 63625, about 3,440.8 MiB.
  Goal remains active. Next join initial-create observation and same-document
  completion to their original native evidence. History/reload, exact download
  association, remaining transfer lifetimes, Android public save/text/blob and the
  documented full-goal work remain open. Device/framework/automation qualification
  remains false; phone and installed WebView checks remain deferred. No formal
  data, installed phone app, online release or provider version was changed.

## E101 — carry initial-create identity into observation and wait (2026-10-31)

- Join initial-target `observe` and `wait_for` to the original native navigation
  proof instead of observing whichever page happens to be current after warmup.
  The exact workbench creation path retains the opaque reference from the validated
  create receipt in this action's local context and passes it to the dispatcher.
  It is not recovered from a later state snapshot or retained globally. This
  closes the race where the initial page changes before the first state read.
- Actions attaching during initial readiness retain the first original record;
  they never adopt another navigation while waiting. Require the original native
  committed-document proof and UI navigation throughout reading and at delivery.
  Feed the original reference into every FrameDriver observation subread through
  E100's native guard. A missing/replaced/revoked record or changed proof discards
  the payload; unproven and legacy-adapter reads cannot fall back to page scripts.
- Preserve cancellation, manual control and native boundaries. An observation
  does not cancel an independent original create operation. Report a replacement
  before considering its failure, so a foreign navigation's error is not attributed
  to the original. A later explicit observation on an already existing, ordinarily
  navigated page does not inherit this old action's startup constraint.
- Treat a typed initial download acknowledgement as a download boundary, never
  a blank committed page. Retained records are explicitly associated at browser
  scope only; missing retained identity is explicit. Exact request/download
  association remains open and is not invented by this stage.
- Add actual dispatcher + FrameDriver/FrameReader tests using the real page helper
  source and a controlled native peer. Execute the exact deployed workbench
  admission and driver-routing blocks under their reviewed projection hash, with
  the actual native-create helper. This covers successful initial reading,
  replacement before the first state read, a missing record, native guard refusal,
  tree/UI changes during reading, the unsupported document adapter and a fresh
  later observation. DOM layout and Android framework/provider behavior remain
  outside these controlled tests.
- Final sequential JS run passes all 362 tests in 21.738 seconds. Python passes
  173/175 in 50.160 seconds, with only the two existing Windows symlink-privilege
  skips. Exact product lifecycle passes 432. Full native ASan/UBSan/API26 checks
  pass (including broker 160, grammar 61 and worker 191); final hashes match all
  103 native and 13 product inputs. Seven final Android package contracts pass.
- Refresh both development programs and pass all 12 actual packed Windows runtime
  checks. Windows: 6,937,103 bytes, SHA-256
  2ff7f9402c8a50993defb7cccc27a5f2c27063094ef140b75304891f788a7979.
  Full Android QA APK: 6,464,833 bytes, SHA-256
  63f4c02cff038f8ac0fdcb896b94f8bdbd7b818ca0f1e2e9787893b9d38de51a.
  Actual DEX definitions, private provider, six JNI exports, original ARM64/16 KiB
  engine, source/archive identities and retained v2/v3 signer pass. Native engine
  and paired 247,351-byte/22-file plugin remain unchanged. Evidence is retained in
  .mdo/E101-node-full.log, .mdo/E101-python-full.log,
  .mdo/E101-initial-frame-focused.log, .mdo/E101-native-current.json,
  .mdo/E101-product-current.json, .mdo/E101-final-identities.log,
  .mdo/E101-android-receipt.json and the E101 build/runtime logs.
- Restore the sole headless service: PID 12856, HTTP 200 on 63625, about 3,441.0 MiB.
  Goal active. Next correlate same-document navigation with original native events
  rather than the ACK alone, then complete history/reload, original download
  association, remaining transfer lifetimes, Android public save/text/blob and
  other documented full-goal work. Installed Android/provider/main-UI/public JS-C
  qualification remains deferred; device/framework/automation flags remain false.
  No phone app/provider update, formal-data change or online publication occurred.

## E102 — prove same-document destination before observing (2026-10-31)

- Replace ACK-only same-document completion with the original typed non-download
  ACK plus a destination-matching root `Page.navigatedWithinDocument` event after
  the worker admission revision. Retain the original document/loader and private
  physical target; never adopt a different document with a reused loader. ACK
  arrival before or after the event is supported. Missing download classification
  cannot be inferred from a missing loader or an event.
- Keep a distinct position scope alongside the document scope. A later root
  position event revokes the original same-document proof without revoking the
  unchanged document's download provenance. Full navigation/document.open still
  revokes the document and all its position proofs. Re-entering an old destination
  does not resurrect a proof that the broker already retained and then revoked.
- Share the existing bounded 32-entry commit history. Retain only a SHA-256
  destination equality digest, not URL text; clear temporary UTF-8 arrays and
  evicted digest arrays. Child events and pre-admission processed history cannot
  grant root completion. No caller wire ID, URL lookup, new worker or dependency
  is introduced. Same-location no-event/no-op behavior remains unqualified; no
  success is invented when required native evidence is absent.
- Extend the reading-connection guard: before and after observation, match the
  original target, frame, loader and current native destination. Accept separate
  `url`/`urlFragment` metadata or an unambiguous full URL without that separate
  field; conflicting/malformed/missing destination metadata is unproven. Native
  guards discard changed-destination payloads and retain evaluation uncertainty
  when the operation already ran. UI delivery also rejects a changed original
  same-document epoch. A same-document ACK alone no longer releases its UI
  reservation; an original current proof must first restore page readiness.
- Canonicalize HTTP(S) goto destinations with the shared JS URL implementation
  before admission, including host/default-port/path/Unicode handling. Reject
  embedded credentials and encoded destinations beyond the existing 8,192-character
  limit before marking input dispatched. Read observation URLs, or the proven
  same-document target, instead of publishing a stale toolbar URL. Old hosts
  without the private same-document proof cannot fall back to page scripts.
- Re-read the pinned primary Page definition for unchanged loaders, within-document
  events and fragment metadata. Also inspect Chromium PageHandler's current
  DispatchNavigateCallback on 2026-10-31: it supplies request->IsDownload() to the
  navigate callback. This supports testing explicit false as well as true; it is
  not a qualification claim about the installed older provider. Primary sources:
  https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/a45ae55ca46eeb4c6768011da4470ec0f103d01c/pdl/domains/Page.pdl
  and https://raw.githubusercontent.com/chromium/chromium/main/content/browser/devtools/protocol/page_handler.cc.
- Native navigation passes 370 assertions, ancestry/position passes 66, command
  grammar passes 63 and shared worker passes 191. New cases include opposite
  ACK/event order, wrong/child/pre-admission evidence, 32-entry eviction, unknown
  or positive download classification, document.open reusing a loader, wrong
  fragment, missing/conflicting metadata, post-read loader/destination change,
  cancellation, payload clearing and non-resurrection. The full gate now requires
  explicit new coverage markers, not just a generic passed flag.
- Preserve the first focused failure log at .mdo/E102-native-focused.log and its
  fixed QA evidence root network-navigation-files-E102-failure. It failed the
  zero-pending cleanup assertion after a controlled document.open case. Inspection
  found a check-then-remove race in the test Wire's concurrent receive/close drain;
  use atomic poll and add the exact outstanding counts to fixture failure messages.
  Keep the original zero-pending assertions; no product shutdown rule is weakened
  or production close defect claimed. The corrected focused 370-assertion run and
  the full native ASan/UBSan/API26 run pass.
- All 364 JS tests pass sequentially in 21.631 seconds. Python passes 173/175 in
  52.857 seconds, with the two existing Windows symlink-privilege skips. Exact
  product lifecycle passes 450; all 103 native and 13 product input hashes match.
  Seven final Android APK contracts and all 12 actual packed Windows runtime
  checks pass. Final Windows artifact: 6,937,299 bytes, SHA-256
  1265e71f1fad8503887dd8c37b5e6144da7e9834a0ce0647efe13ea75b59f99c.
  Full Android QA APK: 6,468,929 bytes, SHA-256
  9d1e8e9570ea44523d8ddac81eb5509abe0e24d4038929df6e39bbcbd7cdd757.
  DEX definitions, private provider, six JNI exports, original ARM64/16 KiB engine,
  source/archive identities and retained v2/v3 signer pass. Native engine and
  paired 247,351-byte/22-file plugin remain unchanged. Evidence is retained in
  .mdo/E102-native-current.json, .mdo/E102-product-current.json,
  .mdo/E102-final-identities.log, .mdo/E102-android-receipt.json and E102 test/build logs.
- Restore the sole headless service: PID 28904, HTTP 200 on 63625, about 3,441.2 MiB.
  Goal active. Same-location no-op qualification, original history/reload receipts,
  exact request/download association, remaining transfers, Android public
  save/text/blob and the documented full-goal work remain open. Installed
  Android/provider/main-UI/public JS-C verification stays deferred, and all three
  qualification flags remain false. No phone/provider update, formal-data change
  or online publication occurred.

## E103 — stage fixed history/reload receipts and native selection (2026-10-31)

- Add fixed private `Page.navigateToHistoryEntry` and `Page.reload` commands to
  the existing asynchronous navigation slot. Retain its 4,096 lifetime limit,
  100 ms send cap, original deadline, typed immutable terminal outcome,
  owner/session checks, cancellation and no replay. Reload always carries the
  original native loader precondition; there is no fallback without it. A
  `-32601` rejection preserves an explicit unsupported/no-effect outcome.
- Keep empty control ACKs distinct from goto replies: no loader, download
  classification or document completion is inferred. Nonempty/mismatched ACKs
  fail closed. The ledger cannot obtain goto or same-document authority from
  these receipt-only tickets, even if a root commit event also arrives.
- Native history preflight reads the original physical target's history and
  selects only its back/forward neighbor or current reload entry. Reject
  malformed indexes/IDs/URLs, duplicate IDs, unavailable entries, more than 512
  entries, and unsupported current/selected destinations before mutation. Match
  the current native root, loader, original document and URL/fragment digest
  again; include all queries in the original timeout. Preflight failures are
  explicitly no-effect. Caller entry/loader IDs, provider prose, raw history URLs
  and titles are neither exposed nor retained in the selection record.
- Reuse the E102 native frame-destination digest validation, one worker and the
  existing connection. Extend both native/test peers without adding a public
  protocol route. Package the new Selection class and require its actual DEX
  definition, including the negative missing-class contract.
- Re-read the pinned primary Page protocol definition (same revision/hash in
  cdp-protocol.json): history entry IDs/currentIndex, empty history/reload ACKs
  and the optional reload loader precondition. Provider support remains
  unqualified; the precondition must not be silently omitted on older providers.
- Full ASan/UBSan, Android API26 compile, real JVM/JNI/JSON and local socket gate
  passes. Original navigation receipts pass 234 assertions, including two new
  real control socket sessions with opposite ACK/root-event order. Native
  physical binding passes 2,327 assertions, including destination/loader/root
  races, expired preflight budget, provider rejection and no mutation on failed
  selection. Socket/native allocation and descriptor checks remain strict.
  Shared worker passes 195; exact product lifecycle passes 450. All 103 native
  and 13 product source hashes match current bytes.
- All 364 JS tests pass sequentially (21.566 seconds); Python passes 173/175
  (48.629 seconds), with the two existing Windows symlink-privilege skips. All
  seven final APK contracts and 12 actual packed Windows runtime checks pass.
  Windows artifact is unchanged: 6,937,299 bytes, SHA-256
  1265e71f1fad8503887dd8c37b5e6144da7e9834a0ce0647efe13ea75b59f99c.
  Full Android QA APK is 6,468,929 bytes, SHA-256
  7697032d6519204329a88e120e48a0c8eed3dfd5ae6262b1867fc08a267b3c81.
  Actual DEX definitions, six JNI exports, original ARM64/16 KiB engine, private
  provider, source/archive identities and retained v2/v3 signer pass. Native
  engine and paired 247,351-byte/22-file plugin remain unchanged. Evidence:
  .mdo/E103-native-current.json, E103-product-current.json,
  E103-android-receipt.json, E103-final-identities.log and E103 test/build logs.
- Restore the sole headless service: PID 5520, HTTP 200 on 63625, about 3,441.4
  MiB. Goal active. This stage does not activate history/reload in UI/JS or claim
  page completion: original post-history/current-entry and event completion proof,
  reload correlation, shared/UI admission, same-location no-op qualification,
  request/download association, remaining transfers and full-goal work remain.
  Phone/main-UI/installed-provider verification is deferred at the user's request;
  device/framework/automation qualification stays false. No provider update,
  formal-data change or online publication occurred.

## E104 — prove native history/reload commitment and guard reads (2026-10-31)

- Join a receipt-only control ACK with a matching post-admission native root
  start and commit. History accepts its typed same-document start plus unchanged
  original document/loader and destination event, or a typed cross-document
  start/loader commit. An explicit BackForwardCacheRestore commit can restore an
  older loader; a new native history entry/frame check is still mandatory.
  Reload requires a reload start and newly committed loader. Missing, wrong,
  pre-admission, child or superseded evidence cannot grant this proof.
- Track one current root start and the existing bounded 32-commit ledger. Retain
  destination equality digests, never raw event URLs or history titles. A later
  root start invalidates the retained control proof even before a new document
  commits. Position/document changes also revoke it; an already retained proof
  cannot be replaced with a later matching result. Ordinary download provenance
  survives a start without a document replacement.
- Before publishing commitment, read current native history and frame metadata
  on the original physical target. Match the originally selected entry ID,
  current loader, URL/fragment digest and stable original ledger revision.
  Duplicate URLs cannot stand in for entry identity. Query only on a new
  candidate revision and within the original preflight/dispatch budget. A local
  proof-budget expiry sends no further work and does not by itself retire a
  still-valid physical connection. ACKs remain immutable and distinct from
  document proof, DOM/load readiness and download completion.
- Add private traverseNetwork admission on the existing single worker. Preserve
  cancellation, queued budget, pending-download admission and no replay. Route
  retained control proofs through before/after read guards: exact physical
  target, document loader, selected history entry and committed destination must
  still match. Reject before evaluation when possible; discard returned bytes
  and retain effect uncertainty if the operation already ran. Superseding starts,
  same-URL entry replacement, post-read URL/loader change and cancellation are
  covered without adopting another current page.
- Check the pinned primary Page protocol for typed starts, possible cancellation,
  reload loader preconditions and explicit BFCache commit type. Inspect Chromium
  138.0.7204.0's PageHandler mapping of reload/history start enums as a reference,
  not proof of the installed provider:
  https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/a45ae55ca46eeb4c6768011da4470ec0f103d01c/pdl/domains/Page.pdl
  and https://raw.githubusercontent.com/chromium/chromium/138.0.7204.0/content/browser/devtools/protocol/page_handler.cc.
- Full ASan/UBSan/API26 and real JVM/JNI/JSON/socket gates pass: navigation
  receipts 238 (including two real control start/commit/revocation socket sessions),
  ancestry 121, physical binding 2,460, navigation broker/guard 536 and shared
  worker 195. Negative proof cases cover unavailable evidence, wrong native
  entry despite matching URL, root/loader/destination changes, document.open,
  malformed history, unreachable pages and original deadline exhaustion.
  Exact product lifecycle remains 450. All 103 native and 13 product input hashes
  match current bytes. No native/UI qualification is inferred from controlled
  renderer or framework peers.
- Preserve .mdo/E104-navigation-first-failure.log and
  E104-navigation-fixture-failure.log. A newly added post-read case initially
  dereferenced Context.network, which direct Context.bind sets but the shared
  worker connector does not. That fixture threw before returning its buffer.
  Remove that invalid direct poll and exercise the real cooperative RPC turn;
  the 536-assertion focused/full runs pass without weakening byte clearing or
  uncertainty checks. Only the verified empty fixed QA directory was removed
  between attempts; no application/Home/source copy was created.
- All 364 JS tests pass sequentially (21.429 seconds); Python passes 173/175
  (47.176 seconds), with the two existing Windows symlink-privilege skips. Seven
  final APK contracts and 12 actual packed Windows runtime checks pass. Require
  actual DEX definitions for the new native Start and Traversal classes, including
  missing-class rejection. Full Android QA APK: 6,473,025 bytes, SHA-256
  c92a805866ccdd3374e96b11c3dc40a4c655c86bc714949818c9f2a926b34f0f.
  Windows remains 6,937,299 bytes, SHA-256
  1265e71f1fad8503887dd8c37b5e6144da7e9834a0ce0647efe13ea75b59f99c.
  Native ARM64/16 KiB engine, six JNI exports, private provider, retained v2/v3
  signer and paired 247,351-byte/22-file plugin are verified and unchanged.
  Evidence: .mdo/E104-native-current.json, E104-product-current.json,
  E104-android-receipt.json, E104-final-identities.log and E104 test/build logs.
- Restore the sole headless service: PID 28620, HTTP 200 on 63625, about 3,441.6
  MiB. Goal active. Next connect the private control kind/commit proof to original
  UI navigation records and shared JS/C observation/waiting; empty control ACKs
  must not be treated as goto download metadata. DOM/load readiness remains a
  separate check. Same-location no-op qualification, exact request/download
  association, remaining transfers and documented full-goal verification remain.
  Phone/installed-provider/main-UI checks stay deferred at the user's request;
  device/framework/automation flags remain false. No online publication, provider
  update or formal-data modification occurred.

## E105 — join history/reload UI records and shared navigation waiting (2026-10-31)

- Route back/forward/reload through the original armed physical owner's private
  worker admission. Retain the original action, control-vs-goto kind, opaque
  navigation reference and budget. No caller URL/entry/loader is forwarded for a
  control, and native dispatch failure cannot fall back to WebView mutation.
  Human navigation under manual control retains its ordinary WebView path.
- Keep empty control ACKs fenced until the E104 native proof is current. Join
  real WebView callbacks when present. If native cross-document commitment comes
  first, retire the old document/input references and advance the UI epoch before
  releasing realm admission. The named document committed method releases realm
  admission only; shared observation still proves DOM readiness independently.
  Recheck owner, profile, channel, epoch and native proof around the join. A
  same-document control cannot adopt a changed UI epoch. UI adoption exceptions
  and impossible control metadata remain fixed retained errors without replay.
- Carry the exact control kind/action through the shared workbench dispatcher
  used by JS and C navigation. Reject old Android control hosts before navigation;
  require an original receipt after dispatch. Do not treat absent goto loader or
  download fields as control completion. Wait for original commitment and DOM
  readiness, and keep its reference on every observation subread and final guard.
  A reload cannot claim same-document completion. Wrong kind/action, changed
  record/entry/document, cancellation and takeover cannot publish another page's
  observation. Native controls return the observed URL rather than a stale
  toolbar value; preserve the existing legacy Windows fallback behavior.
- Project all three exact final native route replacements into the product JVM
  fixture, plus actual documentNavigation/documentLoaded fragments. Test normal
  callback order, no-callback commitment, same-document history, original timeout,
  cancellation, takeover, reentrant channel change, UI adoption failure, invalid
  ACK metadata, native dispatch failure and retained manual navigation. Require
  the new native_control_ui_join coverage marker in the gate. All 647 assertions
  pass. Actual Java world/document/profile/lifetime checks also pass (35 world,
  85 document checks); their seven source hashes match current bytes.
- All 368 JS tests pass sequentially in 23.271 seconds, including four new shared
  navigation tests across back/forward/reload. Cover delayed proof/DOM, unchanged
  UI epoch with native proof, wrong or missing receipt kind/action/reference,
  impossible download/loader metadata, old hosts, no-effect native rejection,
  post-read revocation, takeover and timeout. Python passes 173/175 in 48.972
  seconds, with the two existing Windows symlink-privilege skips.
- Full ASan/UBSan/API26 and JVM/JNI/JSON/socket gates pass; all 103 native input
  hashes and 13 product input hashes match. Twelve actual packed Windows runtime
  checks and seven final APK contracts pass. Windows artifact: 6,937,556 bytes,
  SHA-256 40dbcfd48f0ad13734d1fa879747a94499832c38f4a8325f173ea10baa893923.
  Full Android QA APK: 6,473,025 bytes, SHA-256
  e1debd961d92fc650245ca7e6d728741929747a6305df18edbed2507c4684987.
  Actual DEX definitions, private provider, six JNI exports, retained v2/v3
  signer, source/archive identity and ARM64/16 KiB engine checks pass. Native
  engine and paired 247,351-byte/22-file plugin remain unchanged. Evidence:
  .mdo/E105-native-current.json, E105-product-current.json,
  E105-world-current.json, E105-android-receipt.json, E105-final-identities.log
  and E105 test/build logs. No new runtime dependency, worker or deployment copy.
- Restore the sole headless service: PID 20704, HTTP 200 on 63625, about 3,441.8
  MiB. Goal active. This is a code/component/packaging milestone, not installed
  Android end-to-end qualification. Public Android admission and all three
  qualification flags remain false. Next pursue exact original request/download
  association and remaining transfer integration; same-location no-op behavior,
  installed provider/main-UI qualification and documented full-goal work remain.
  Phone/provider update checks stay deferred. No online publication or formal
  data modification occurred.

## E106 — join original native request and retained download identities (2026-10-31)

- Add a private root-Document request ledger. Link the original typed goto
  download ACK/loader to native Network.requestWillBeSent request identity and
  frozen Fetch.networkId. Publish only an opaque nr reference; no protocol
  selector, request URL, headers or body is retained by the ledger. Enable fixed
  Network metadata with zero requested buffer/post-data budgets before Fetch.
  Root frame, owner, request-admission revision and original ACK must agree.
  Controls with empty ACKs have no inferred download identity.
- Bound the ledger to 4,096 requests, 32 redirects per request and 32 deferred
  response links. Reject identity/loader rebinding, malformed root metadata and
  quota exhaustion without replaying effects or abandoning original stream
  cleanup. Already proven original facts remain inspectable. Links hold original
  immutable protocol evidence, not a binding, UI callback or current-navigation
  lookup. Delay resolution when metadata follows Fetch or even body completion.
- Extend native/UI download snapshots and shared JS/C navigation handling with
  the original opaque request reference. After a typed download ACK, select only
  its one retained response; missing/ambiguous proof cannot borrow a concurrent
  same-browser download. Preserve the pre-ACK browser_scope permission boundary:
  native ACK may wait for original body cleanup, so exact attribution cannot be
  required before the user can decide. No body is read before explicit approval.
- Reproduce late metadata against the pre-fix compiled ledger: the new regression
  fails at request identity check 66 (.mdo/E106-requests-late-before-fix.log).
  The fixed ledger passes 105 assertions. Broker tests pass 595 assertions,
  including five ACK/Fetch/metadata/body orderings and an unrelated response.
  The real native socket/JNI/strict-JSON fixture retains the request link while
  the download ACK is withheld until original stream/request cleanup. Full
  binding coverage passes 2,518 assertions. These controlled peers do not qualify
  an installed provider.
- Correct two development integration failures: aggregate frame/request events
  under the existing single observer (.mdo/E106-binding-first-failure.log), and
  include the new Java class in the actual APK copy and source-hash inventories
  (.mdo/E106-android-package-missing-class.log). The final real APK compiles and
  its DEX definition gate requires the ledger, Receipt and Link implementations.
- All 372 JS tests pass in 23.548 seconds. Python passes 173/175 in 46.665 seconds
  with two existing Windows symlink-privilege skips. Native ASan/UBSan/API26 and
  JVM/JNI/socket gates pass; all 105 native and 13 product source hashes match.
  Product UI projection passes 647 assertions. E105 world/document evidence is
  reused only after verifying its seven unchanged hashes. Seven APK contracts
  and 12 actual packed Windows runtime checks pass.
- Windows: 6,937,880 bytes, SHA-256
  35bdd50f041729a2002e9e15b81b540fe9a5ed11461dea41da0e9b0e5d034179.
  Full Android QA APK: 6,477,121 bytes, SHA-256
  d68f5b43831ceb8e72648fca9105c0b20e9ae4820d13e0d904aa8cc8098452a1.
  Native ARM64/16 KiB engine, six JNI exports, private provider, retained v2/v3
  signer and paired 247,351-byte/22-file plugin are verified. Evidence:
  .mdo/E106-native-current.json, E106-product-current.json,
  E106-android-receipt.json, E106-final-identities.log and E106 test/build logs.
- Restore the sole headless service: PID 32920, HTTP 200 on 63625, about 3,442.1
  MiB. Goal active. Late responses first retained only after a newer navigation
  replaced the captured command may remain unassociated; no false exact claim is
  made. Remaining transfer integration, same-location no-op behavior, installed
  provider/main-UI checks and full-goal verification remain. Phone/provider
  changes stay deferred, public Android admission and all qualification flags
  remain false. No publication, model call or production data edit occurred.

## E107 — retain old navigation candidates for late original responses (2026-10-31)

- Reproduce E106's known false negative using the current compiled production
  classes: after two original goto ACKs, retain the older response for the first
  time. Its source reference never resolves (.mdo/E107-overlap-before-fix.log).
  This is missing attribution, not evidence of an incorrect file or approval.
- Replace the binding's lastNavigation lookup with a bounded private history of
  32 original typed goto commands. Each response freezes only the candidates
  present when it is retained; later commands cannot acquire it. Use original
  admission revision, root, loader and settled typed ACKs to prove exactly one
  candidate. Do not choose between conflicting or unresolved eligible ACKs.
  Failed/non-download commands can be pruned; eviction can leave an unretained
  very old response unassociated, while already retained links preserve their
  immutable candidates. No WebView, binding/UI callback, URL or body is added to
  these command records. Allocate a new command before mutating history slots.
- Add three broker cases with older metadata before/after the newer command and
  after response retention. All keep the older reference, claim no body before
  explicit approval, finish exact bytes and drain file/source cleanup. New ledger
  cases cover pending ACKs, conflicting loaders, candidate freezing, duplicate
  admission, the 32-command boundary and uncached retained facts after eviction.
  The first draft fixture tried two simultaneously pending native navigations;
  correct it to respect the existing one-pending-command contract rather than
  changing production admission (.mdo/E107-fixture-pending-command.log).
- Full native ASan/UBSan/API26/JVM/JNI/strict-JSON/socket gates pass with 105 current
  input hashes. Request identity passes 132 assertions; broker navigation passes
  622. Require their new history/overlap coverage markers. Python passes 173/175
  in 57.072 seconds with two existing Windows symlink-privilege skips. No JS or
  packed Windows app bytes changed: reuse E106's 372 JS and 12 actual Windows
  checks after source/archive/hash verification. Reuse E106 product 647 and E105
  world/document evidence only after confirming their 13/seven unchanged hashes.
- Build the full Android QA APK again and require the actual private Command
  implementation in DEX, not just a class reference. Current APK is 6,477,121
  bytes, SHA-256
  3f450f13370d2550723f07b2213f3ba6bcc32b532c57a6f8771a8fe2f200ad01.
  Source/archive/native-engine/paired-plugin identities, private provider, six
  JNI exports, ARM64/16 KiB layout and retained signer pass. Evidence:
  .mdo/E107-native-current.json, E107-android-receipt.json,
  E107-final-identities.log and E107 test/build logs. Windows and paired plugin
  retain the exact E106 identities. No new runtime dependency or deployment copy.
- Restore the sole headless service: PID 17356, HTTP 200 on 63625, about 3,442.2
  MiB. Goal active. Remaining transfer integration, same-location no-op handling,
  installed provider/main-UI checks and documented full-goal verification remain.
  The user deferred the phone; no reconnection/provider update was attempted.
  Public Android admission and all qualification flags stay false. No model
  calls, publication or production-data modifications occurred.

## W5 — revalidate current Windows frame behavior and desktop availability (2026-10-31)

- Run the actual WebView2 cross-site/OOPIF frame workflow in the sole deployment
  using local fixtures. All 260 assertions pass, including original-world input,
  Unicode nested fill, input/drag interruption, source-retirement recovery and
  private file-picker handoff. Final input is idle, cleanup fences are clear,
  private-cache reservations are zero and no native exception diagnostic is new.
  Preserve .mdo/W5-frames-current.log and W5-frames-current.json. This run did not
  reproduce the historical E68 nested-fill/child-navigation-drag interruptions;
  it does not identify their original cause or establish permanent resolution.
- Read the current Computer Use skill/guidance/confirmation rules and recheck its
  supported node_repl/@oai/sky initialization entry. It still fails before app
  inventory with kernel_assets_unavailable: cannot write kernel assets because
  the system cannot find the specified path (OS error 3). No window action was
  issued. Record .mdo/W5-desktop-state.json. Normal-desktop screenshots and
  permission-prompt pixels remain unverified; the native API result is separate
  evidence, not a replacement for those pixels. Do not keep retrying this
  unchanged helper error.
- No Windows product code or release changed. The test restored this project's
  headless service. Phone/provider testing remains deferred and the full goal
  remains active; continue independently testable navigation/transfer work.

## E108 — prove an unchanged-location goto without a synthetic event (2026-10-31)

- Reproduce the missing-event wait using the E107 production classes and a new
  controlled native-worker regression: successful original typed non-download
  ACK, absent loader, unchanged exact URL/fragment and no navigation event never
  produce a current original page (.mdo/E108-noop-before-fix.log). This establishes
  the adapter's missing proof path, not an installed-provider observation.
- Keep one private SHA-256 location digest for the root, derived from original
  native bootstrap/frame/within-document metadata. Store no URL text. Retain its
  original document/position and quiet-root scope before dispatch. An unresolved
  prior root start blocks eligibility. A later start, position move, document
  replacement or closure revokes that original scope without resurrection.
  Digest ownership is independent of bounded commit-history entries.
- Following only the original explicit non-download ACK without loader, prefer
  the existing matching same-document event proof. Otherwise require an eligible
  original location and one fresh native root/frame/loader/destination snapshot,
  within the original budget, while that exact scope remains current. Missing,
  malformed, foreign or unreachable-page metadata proves nothing. Send no script,
  input, reload or repeated navigation. Keep initial warmup and history/reload
  requirements unchanged. Shared JS/C waiting still proves DOM readiness and
  validates the original native record on every read.
- Cover 26 controlled cases: combined and split fragments, absent prior metadata,
  different targets, incomplete/download/cross-document ACKs, changed URL/loader/
  frame, malformed/error metadata, prior/during/later starts, document.open,
  position movement away and back, unrelated child events, cancellation and
  guarded-reader mismatches. Also navigate normally and within-document first,
  then issue the exact unchanged-location goto. Require one qualifying snapshot,
  one send per requested action and no post-send Runtime evaluation. Guarded
  operations execute only under the exact current proof. All 817 broker assertions
  pass; require same_location_noop_proof in the full gate.
- Final review catches a draft allowing another snapshot after an unrelated
  child event advanced the ledger revision. The new case first supplies a
  contradictory root snapshot and then an unrelated child event; the old draft
  incorrectly completes after a second snapshot (check 816,
  .mdo/E108-noop-single-snapshot-red.log). Permit exactly one snapshot attempt
  for that original action. A later coincidental match cannot revive its proof.
  Full native and Python gates are rerun against this final implementation.
- Full ASan/UBSan/API26 and JVM/JNI/strict-JSON/socket gates pass, with all 105
  native source hashes verified. Python passes 173/175 in 55.892 seconds; two
  existing Windows symlink-privilege skips remain. E106 JS 372 and packed Windows
  12-check evidence are reused only with unchanged JS and executable/archive
  identities. E106 product UI 647 and E105 world/document evidence retain their
  exact 13/seven current hashes; no unavailable framework evidence is counted.
- Build the actual Android development APK and verify current source/archive,
  DEX implementations, private provider, retained signer, six JNI exports and
  ARM64/16 KiB native payload. APK: 6,477,121 bytes, SHA-256
  d7a1d3fd29d0f84f77558a3221f0510a77f9a8f14ac11c6b5ce61b6c0b9fdd43.
  Windows and paired plugin retain the exact E106 identities. Evidence:
  .mdo/E108-native-current.json, E108-android-receipt.json,
  E108-final-identities.log and E108 test/build logs. No runtime dependency or
  deployment/source/Home copy was added.
- Restore the sole headless service: PID 5064, HTTP 200 on 63625, about 3,437.6
  MiB. Goal active. This completes the strictly proven same-location component
  path, not provider qualification or an unconditional no-event fallback.
  Remaining transfer/platform integration, installed provider/main-UI checks,
  desktop visual availability, historical Windows fault attribution and full-goal
  verification remain. Phone/provider changes stay deferred, public Android
  admission and all qualification flags stay false. No model call, online
  publication or production-data modification occurred.

## E109 — retain original navigation epochs for late download responses (2026-10-31)

- Confirm a product-component defect against E108 production classes: retain an
  original download request/ACK, replace the root document, then deliver that old
  response for the first time. It has the correct original nr reference but
  incorrectly reports the new document as its current source. The regression
  fails at check 825 (.mdo/E109-late-epoch-before-fix.log). No installed provider
  or production incident is inferred from this controlled reproduction.
- Capture the original root document Origin with each private native goto
  admission. Freeze the response's still-possible original candidates, filtering
  by native request/loader evidence when available. Before ACK/metadata, use only
  a document common to those candidates; never borrow a replacement document.
  Pin that epoch once on the retained Offer and use it for controller acceptance,
  worker advancement, source-current metadata and cancellation. Original Origin
  tokens contain no WebView, binding/UI callback, URL, body or execution handle.
- Old revoked sources fail with native_frame/download_frame_changed and no body
  claim/read. Mixed original documents and correlation faults create an explicitly
  unproven document (source_document:null) and a retained failed download record.
  Cancel its exact source and keep its cause/cleanup receipt. This rejection is
  per record; it does not set the controller's permanent failure fence. A later
  explicit, well-proven action can proceed without repeating the failed request.
- Report native.source_epoch_kind as response_frame, navigation_candidates,
  original_navigation or unproven. A later exact nr proof can refine the label,
  never the frozen epoch. response_frame remains only a response-time frame
  snapshot, not proof of the initiating document; broader non-goto attribution
  remains open. Existing EOF/file-verification rules retain completed original
  bytes across later navigation. Same-document movement does not revoke a valid
  download's document epoch.
- Seven broker cases cover metadata before/after commit/retention, ACK after
  stale-source cancellation, same-document movement, completed bytes after a
  replacement, mixed-document rejection and a fresh successful action. All 915
  assertions pass; require original_download_epoch in the gate. Raw socket/JNI/
  strict-JSON download progression also proves the common original epoch before
  its delayed ACK and the same epoch afterward (243 navigation assertions).
- The first full run hits the native descriptor baseline assertion after this
  new metadata fixture creates its first UUID. Preserve
  .mdo/E109-native-metadata-fd-before-fix.log. Existing entropy instrumentation
  confirms the JDK opens two process-wide /dev/random and /dev/urandom descriptors
  (0 to 2). Initialize that metadata before sampling the per-session baseline,
  as the existing commit fixture does. Do not weaken native Fds()==VmBaseline,
  allocation balance or socket-slot assertions. The focused and full rerun pass;
  RLIMIT_CORE remains zero and no extra core/diagnostic file was created.
- Full ASan/UBSan/API26/JVM/JNI/socket gates pass with 105 current source hashes.
  Python passes 173/175 in 53.121 seconds; two existing Windows symlink skips
  remain. Reuse E106 JS 372/packed Windows 12 checks only after unchanged source/
  artifact verification; E106 product 647 and E105 world/document checks retain
  their exact 13/seven current input hashes. Seven APK contracts run in the full
  Python suite. These results do not qualify installed Android/provider behavior.
- Actual Android QA APK: 6,477,121 bytes, SHA-256
  751b83a640f469c259873f222619cfe4f026af9f4b2afbcb15ce3e1f8ac73837.
  Current Java/source archive, actual DEX classes, private provider, retained
  signer, six JNI exports and ARM64/16 KiB native payload verify. Windows and
  paired plugin keep their E106 identities. Evidence: .mdo/E109-native-current.json,
  E109-android-receipt.json, E109-final-identities.log and E109 test/build logs.
  No runtime dependency or extra app/Home/source deployment was added.
- Final cancellation audit withholds the exact native abort ACK. The previous
  draft incorrectly exposes pending/null cause while cleanup is in progress
  (check 902, .mdo/E109-cancelling-cause-before-fix.log). Publish cancelling with
  the actual download_frame_unproven cause immediately and preserve that same
  cause for an early accept decision. The focused regression and final full gates
  pass; no body is claimed or read while that cancellation is pending.
- Restore the sole headless service: PID 26100, HTTP 200 on 63625, about 3,437.9
  MiB. Goal active. Next inspect released deferred-request-link lifetime and
  remaining transfer/platform and non-goto provenance work. Phone/provider
  testing stays deferred, all qualification flags/public Android admission stay
  false, desktop visual availability and historical Windows attribution remain
  unresolved. No model calls, online release or production-data changes occurred.

## E110 — reclaim released deferred download associations (2026-10-31)

- Confirm a component resource-lifetime defect against E109 production classes:
  sequentially retain a response with missing Network metadata, cancel its exact
  source, confirm cleanup and explicitly release its record. The old ledger keeps
  that deferred Link in its 32-slot pending set. Response 33 incorrectly trips
  cdp_request_pending_limit and loses correlation despite zero live record/file
  reservations. The integration regression fails at check 1,047 in
  .mdo/E110-released-links-before-fix.log. This is controlled component evidence,
  not a claim about an installed provider or reported production incident.
- Retire only the metadata association when its record is actually retired,
  abandoned before publication or the owner closes. Mark it with a volatile flag
  from the receipt/UI thread; only the original worker changes its pending array
  while processing metadata or admitting the next Link. Retiring the association
  performs no native I/O and does not bypass source abort/close or file cleanup.
  Explicit release still requires terminal state and confirmed original-source
  cleanup, followed by cache cleanup before its record reservation is returned.
- Cancelling alone does not retire an inspectable record's association. Preserve
  E106/E109 late metadata/ACK handling after cancellation or body completion until
  release. Known original references remain inspectable. Released snapshots stay
  frozen; an old association cannot acquire newly arriving metadata after its
  retirement has been observed by the original worker. Active pending limits,
  native identity/loader validation and frozen source epochs are unchanged.
- Both cancelled/released and completed/released paths pass 40 sequential cycles
  with one original native goto, zero file/record reservations between cycles,
  followed by a fresh exactly associated response. The broker passes 1,247
  assertions. The identity gate passes 176, including foreign receipt-thread,
  repeated retirement, late metadata and retained-reference cases; the existing
  active 32-link overflow rejection remains covered. Require released_request_links
  in the complete native gate. No new execution/stream/permission authority exists.
- Full ASan/UBSan/API26/JVM/JNI/socket gates pass with 105 current source hashes.
  Python passes 173/175 in 50.954 seconds, with two existing Windows symlink
  privilege skips. Reuse E106 JS 372 and packed Windows 12 checks only after exact
  source/artifact verification. E106 product 647 and E105 world/document evidence
  retain their current 13/seven input hashes. No framework result is inferred.
- Actual refreshed Android APK: 6,477,121 bytes, SHA-256
  474af41bde569562284df197d9cd6672ca719926e7cd0f12e51d383235926fbc.
  Current source, actual DEX, private provider, retained signer, six JNI exports
  and ARM64/16 KiB native payload verify. Windows and paired plugin retain their
  E106 identities. Evidence: .mdo/E110-native-current.json,
  E110-android-receipt.json, E110-final-identities.py/log and E110 gate/build logs.
  No runtime dependency or extra application/source/Home deployment was added.
- Restore the sole headless service: PID 25048, HTTP 200 on 63625, about 3,438.0
  MiB. Goal active. Remaining non-goto provenance, transfer/platform integration,
  installed-provider/main-UI checks, desktop visual availability, historical
  Windows attribution and whole-goal qualification remain open. Phone/provider
  changes stay deferred and public Android admission/all qualification flags stay
  false. No model call, publication or production-data modification occurred.

## W6 — repeat actual cross-process input and interrupted-drag recovery (2026-10-31)

- Extend the existing native frame regression in the sole project deployment
  with --input-cycles 1..8 (default one). The picker-only gate remains separate;
  combining it with repetition is rejected before any fixture/service change.
  Scale the finite whole-probe observation budget with the requested work and
  require the terminal browser report to confirm both exact completed counts.
  There is no operation retry or assertion bypass on a failing cycle.
- Repeat native Unicode nested fills in the existing cross-site/OOPIF hierarchy,
  deliberately moving focus back to the root between cycles. Verify each exact
  final field value and field_state receipt; retain its native input count.
  Preserve the original downstream selection-time focus-change regression and
  its no-replacement-text assertion.
- Repeat the actual admitted pointer drag interrupted by an original child
  document replacement. Give each replacement a distinct fixture URL, retain
  uncertainty, wait for input cleanup, prove zero clicks in the replacement, then
  issue one explicit recovery click. Require exactly one native drag and one
  recovery input per cycle. The final source stays stale for the existing
  capture-owner/old-document checks. Preserve original-observer failure,
  quarantine, positive-source-retirement recovery and capture-privacy checks.
- Run python tools/native_frames_probe.py --input-cycles 6 against real WebView2
  with loopback-only fixtures. All 301 assertions pass: six fills each issue their
  three expected native input phases and verify the exact Unicode field value;
  all six interrupted drags retain action_uncertain/effect_may_have_occurred and
  then complete their single later recovery click. Final input_busy,
  drag_cleanup_failed, upload_cleanup_failed and process_failed are false, with
  no world error, new native diagnostic or private-upload reservation.
- Preserve .mdo/W6-frames-stress.log and W6-frames-stress.json, including individual
  cycle receipts and six current fixture/runtime/host-input hashes. The zero-cycle
  CLI is rejected with exit 2 before service or native input; evidence is in
  W6-invalid-cycles.log. The actual probe performs its JavaScript syntax gate.
  Product C/JS/Java and native binaries are unchanged, so full product suites are
  not needlessly repeated. Verify the current reviewed app archive, Android
  source hash and all Windows/APK/plugin artifact hashes against E110 receipts.
- Restore headless HTTP 200 on 63625, PID 26784, about 3,439.3 MiB. No second
  application/Home/browser, model call, external page or release is introduced.
  This did not reproduce the historical E68 interruptions or identify their
  original cause. Desktop-pixel and Android-provider qualification remain
  separate; do not retry the unchanged unavailable Computer Use helper. Goal
  active; continue remaining integration and qualification work. Phone/WebView
  testing stays deferred without another request to reconnect.
