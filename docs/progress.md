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
