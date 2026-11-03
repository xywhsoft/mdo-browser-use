# Browser Use acceptance and remaining work

The whole Browser Use goal is **not complete**. This record separates current
Windows integration, source-bound earlier results and unavailable qualification.
The matrix covers the original environment, shared C/JS API, native browser
operations, transfers, permissions/control/recovery, Android and distribution.
It does not redefine completion as a successful subset of tests.

## Reproduce Windows integration

Run in this repository with the existing development deployment started headless:

```powershell
python tools/acceptance_probe.py
python tools/acceptance_probe.py --check
```

The first command runs fourteen **sequential** cases in the same `.mdo/source`,
native host and Home. It imports, uninstalls/reinstalls and recovers only this
known plugin revision, restarts the same test service and opens local synthetic
pages. The renderer case stops exactly one newly created QA content renderer,
after checking original process ancestry and creation identity. It never stops
an unrelated browser process. No phone/provider change, live model, account
relay, external website, online publication or extra application copy is used.
One case adds forty-two loopback static protocol requests through the actual
Agent loop; they are not LLM inference or provider calls. It requires absent
`config/models.json` and its backup, preserves an existing override by refusing
the check, and removes only its exact newly created nonsecret fixture file.
Existing settings hashes and the headless runtime must be restored.
Logs reuse `.mdo/qa/acceptance/`; the bounded summary is
`.mdo/acceptance-result.json`. Preserve a failed log before rerunning.

The report binds 538 authoritative source inputs, all
149 applied host outputs, each log/receipt, actual host
binary/config/listener and remote C bridge dependencies. Inputs must stay fixed
while a case runs. A contradictory/nonterminal result cannot pass. `--check`
revalidates these unchanged hashes and the live runtime; it explicitly reports
reused evidence and does **not** claim a fresh execution or broader qualification.
The tool is development-only and adds no shipped Python/Node/browser dependency.

## Requirements and evidence

| Requirement | Inspected implementation and authoritative evidence | Current conclusion |
| --- | --- | --- |
| One reusable pinned environment | `mdo.lock.json`, `tools/dev.py`, reviewed overlays; exact PID/executable/config/listener and HTTP200 before/after acceptance | Verified in this project; no new source/Home/app copy |
| Shared C/JS API and Agent tools | Shared contracts, 13 installed C services and 16 managed tools; E208 actual Agent turns in six vision/text-only cohorts across Chat, Responses and Anthropic static streams, with strict Chat tool schema/order checks | Local continuation and original-image delivery/projection verified; real-provider acceptance/model quality are not qualified |
| Observation, exact references, waiting and native input | E202 current-code SDK19/navigation4/wait5/JS526; fresh managed execution plus 26 public frame operations using two actual out-of-process frames, nested Unicode fill and selection | Verified stated Windows operations; DOM observation remains a bounded subset, not full accessibility-tree equivalence |
| Screenshots, visual actions and privacy | Fresh managed native canvas click/drag; read-only screenshots allowed while input/drag denied; six actual public fixed-scene PNG/JPEG integer/fractional/downscaled comparisons retain identical delivered bytes after private values change | Verified these capture/permission cases; separate desktop framebuffer presentation is not claimed |
| Tabs, popups, dialogs, uploads and downloads | Fresh managed popup/dialog checks, public nested frame picker/file/directory hash checks, original-task remote upload and one original POST/download/save with 131075 exact bytes | Verified actual local native and production-bridge workflows; not arbitrary OS dialogs or website business acceptance |
| Permissions and failure recovery | Fresh nine managed ask-decision cases with ten original durable tool terminal pairs (236 reported assertions), cancellation/late decision/stale document/run-grant isolation; actual owned renderer failure then explicit reload; durable admission survives same-Home restart without duplicate tab | Backend decisions/native recovery verified; E200 normal-main-UI accessibility confirms the scoped SDK prompt; actual user button decision and normal-window pixels remain unverified |
| Human control and resource lifetime | Source-bound E204 native647/cleanup7137/worker300/create850/async77; Android source and Android overlay composition unchanged, although the global manifest adds the model repair; exact frontend20/world inputs retained; fresh Windows control28 | Android proofs are explicitly reused with the manifest difference disclosed; peers remain controlled and do not qualify a physical WebView |
| Remote ownership and transfer | Fresh production C bridge -> real headed WebView2, eight cases including original-task binding, cancel/recovery, stale input denial, upload full hash and retained POST save; native bridge drains | Verified same-host protocol integration; account relay authentication and two-device networking remain unverified |
| Plugin installation and deliverables | Fresh portable import/export/trust/stale-plan8, lifecycle/recovery10 and packed-program12; E208 Windows/Android native engines and matching artifacts rebuilt, actual APK DEX/signature/payload checked; portable plugin unchanged | Matching development artifacts are verified; Android device and official/store release are not claimed |
| Android adaptation and explicit downgrade | Native classes/actual APK DEX, API26/JNI/source/lifetime gates, explicit capabilities/preflight refusal and retained manual browsing | Implementation and controlled checks continue; installed Activity/provider/device qualification and overall automation admission are incomplete |
| Bounded Windows performance and repeated lifecycle | E203 actual main-window/public C path,16 cycles on1,024 paragraphs, verified native fill/trusted click and bounded PNG; original broker inventories and native creation reservations restored | Local latency/resource envelope measured; memory samples remain observational, not a leak-absence or production performance guarantee |

The old standalone `.mdo/managed-visual-result.json` was false with an empty
error at `readonly-owned-canvas`. Its bytes are retained privately as
`E198-managed-visual-prior-failure.json`. E197's full public visual run already
covered that path; the fresh E198 standalone managed run now also passes. This
does not establish the original failure's root cause retroactively.

## Capture comparison qualification

An intermediate full rerun failed the small fractional JPEG byte comparison.
That old receipt lacked the second region/dimensions, so its exact cause cannot
be established retrospectively. Preserve it rather than declare a product fix.
Instrumentation subsequently reproduced a different-output case while public
viewport height changed from695 to673; output width changed from457 to472.
The old test had not established its required invariant that only private pixels
changed. This is an unqualified comparison, not evidence by itself of mask leakage.

The synthetic fixture now owns one fixed640×480 scene. The default public
screenshot captures that exact element and requires identical original regions
and encoded dimensions before comparing complete bytes. PNG/JPEG and integer/
fractional/small output cases stay strict; there is no automatic retry or allowed
byte difference. Thirty fixed-scene pairs and the final six acceptance pairs pass.
`--viewport` retains the old scope and fails qualification on any public geometry
change. `--iterations` is bounded to1–20 and stops at the first failed comparison.
`--capture-evidence` stores at most the failed pair of already masked synthetic
outputs under `.mdo/qa/capture-privacy/`, by content hash; no user's page or raw
native source is saved. The final acceptance command enables this evidence.
No product mask/runtime code or privacy gate was changed for this fixture repair.

## Current main-interface findings (E199–E200)

The actual mounted browser component reproduced a loading layout defect:
native webpage height714 became692 and top86 became108 when its ordinary
loading status row appeared. Keep the red report. E199 uses the existing global
screen-reader-only style for this routine live announcement; tab/stop controls
still show loading. Manual, pending control and recovery/error notices remain
visible. Actual DOM/native bounds now remain identical during loading, and the
original viewport privacy matrix also passes thirty pairs. That is independent
evidence; it does not retroactively prove the cause of E198's incomplete receipt.

E199 normal main-window UI Automation identified the real selected QA
conversation and its ask-mode composer. The screenshot channel timed out twice;
normal-window framebuffer/pixel presentation remains unverified. A real SDK
managed browser_open request was pending with run_id0 and no project/session
fields in its approval descriptor, but the conversation had no approval heading.
The prior frontend filtered approvals through registered Agent run identities.
This confirmed a missing SDK-to-conversation association. The
exact QA request was cancelled/released, its approval disappeared and no browser
was created. No UI permission button was clicked; no model request was made.
Do not display every run_id0 request in every conversation. Carry original,
validated ownership into approval data and retain strict selected-scope filtering.
E200 repairs this defect: the durable session manager binds immutable scope
ownership before runtime admission; snapshots deep-copy the origin; scoped API
reads select before the existing four-item budget. The UI uses exact explicit
ownership, cancels obsolete reads and refuses mismatched scope responses. It
never admits an unscoped run_id0 through the legacy run-ID fallback.

Two real red regressions reproduce missing origin and six-global-versus-one-
selected results. Six simultaneously pending managed SDK operations now return
only the last session's own request even when it is absent from the global first
four. Invalid/partial/duplicate scopes fail closed. All six operations cancel and
release without native effects. Six JS cases cover selection races, foreign or
partial identity, nonzero legacy runs and response scope validation.

In the actual normal main window, accessibility now exposes exactly one
"需要确认" heading plus its allow/deny controls for the selected QA conversation
while five foreign approvals wait. After cancelling the selected request, that
heading disappears; foreign requests do not appear. Only read-only accessibility
inspection was used; no permission UI button was clicked. The same development
Home/runtime is restored headless. Screenshot pixels and an actual user's button
decision are still not qualified.

## SDK tool termination (E201)

A new managed browser_open cancellation reproduces the historical stuck tool
label: the operation is terminal/cancelled with no dispatched effects, but the
durable journal has only its TOOL_START. xwork's cancellation gate suppresses the
later TOOL_DONE; ordinary model runs have an enclosing terminal event, direct SDK
drivers do not. Preserve E201-terminal-red.log/json; do not infer this solely from
old rows or patch ambiguous historical calls.

After the synchronous driver drains, retain only the original call's owned IDs
and scalar event identity. If its start was durably observed without an end,
publish one failed TOOL_DONE through that same session's existing event sink,
before releasing the Agent/session. Existing normal terminal receipts are never
duplicated. No Agent terminal, model message or tool retry is fabricated. Retained
descendant ownership keeps callbacks and the effect boundary alive. Cancelled or
timed out dispatch remains uncertain; failed sink publication is surfaced and is
not automatically repeated.

The original live SDK regression now has one exact start/end pair. Production C
helper checks37 cover identity mismatch, deep-owned IDs, retained lifetime, OOM,
ordinary completion, timeout uncertainty and sink failure. Ten original tool
pairs are additionally checked across the nine native permission/recovery cases;
the acceptance validator rejects reports that omit this gate. The actual applied
timeline projects the cancelled record to one failed tool while a separate call
sharing run_id0 remains running. Normal-main-window accessibility shows the failed
summary; detail expansion was unavailable (desktop input geometry), so pixels and
that expansion are not qualified. The original headless deployment is restored.

The freshly packed executable separately runs that same SDK cancellation test
against its own archive and existing Home, then passes all twelve unchanged
packed-program gates. Its delivered event pair passes the two timeline checks.
Matching Windows/Android archive hashes, APK DEX/signature/payload checks and all
523 source inputs/141 applied outputs are bound in the current evidence. All test
and build handles are terminal before documentation changes. No model/external
website, additional app/Home/dependency copy, phone or publication was used.

## SDK restart outcome recovery (E202)

A forced same-Home restart reproduces terminal/interrupted operation metadata
without uncertainty details and a conversation that still displays the original
tool as running. A separate red cache test shows the forced reload reinserting
its prior running snapshot. Retain both red records.

Persist the validated actual tool target before execution, separately from caller
scope and without arguments. Recovery reports sdk_operation_interrupted/HTTP409,
outcome:uncertain and may_have_completed:true. Only a matching versioned original
target may enrich its TOOL_START with a read-only tool_outcome notice. A physical
retained job takes precedence; real durable tool ends retain their original result.
No raw event/receipt/model result is rewritten, no end is fabricated and no effect
is replayed. Unknown historical records remain unqualified. Runtime changes clear
stale caches and force a fresh selected conversation read; local JS errors preserve
the uncertainty details.

The actual restart probe passes46 checks across distinct caller/target sessions,
four foreign metadata variants, unchanged raw history/admission bytes, no duplicate
tab/approval and no model/native effects. The packed executable independently
checks that same original interrupted admission; eight projection/cache/SDK checks
pass against the delivered implementation. Fresh integration13, Python183/185 plus
two existing skips, JS526, frontend20, control28, SDK/visual and matching signed
Windows/Android artifacts pass. Native647/create850 and E201 main-window accessibility
are explicitly source-checked reuse; physical Android and normal-window pixels
remain unqualified.

The first full run's popup assertion was empty. The original tool end establishes
target_changed at the final pre-input recheck, rather than a lost SDK outcome.
Its exact movement/change cause is not established. Preserve the failed receipt
and bounded E202-popup-guard.json; the standalone follow-up and complete rerun
pass. Probe failures now include line/type and that popup's tool reason. The
product input guard is unchanged and no automatic action retry was added.

A separate actual-main-window close audit confirms one unique owned fixture
tab/native document through read-only accessibility, closes that exact browser
through its original broker scope, then confirms both the displayed tab and its
associated backend panel are removed. The suspected dangling-pane condition is
not reproduced, so no product close change is made. The private setup needed
explicit panel.open after observe (observation alone does not open a display
panel). E202-main-close-result.json records this setup limit and the bounded
before/after accessibility hashes. Restore the same Home headless; normal-window
pixels, permission-button and physical Android qualification remain open.

## Bounded performance evidence (E203)

`tools/performance_probe.py --iterations 16` measures the actual public C/native
path in the same main window and Home, without model calls, external pages or
action retries. All16 cycles pass the frame-driver, observation budget, native
field/click and encoded PNG limits, with original broker resources restored.
Current inputs531/deployed146 and runtime identity are bound in the report.
See [performance](performance.md) for the measured latency table, process-memory
series, exact scope and reproduction command. Private bytes after the first
cycle increase15.8MiB, with later decreases; memory leak absence remains unproven.

Fresh Python183/185 (two existing Windows symlink skips), JS526 and all13 sequential
acceptance cases pass. Product/native sources and E202 Windows/Android/plugin
artifacts remain unchanged and hash-checked; native647/frontend20/control28 and
full SDK/packed/DEX results are explicitly reused rather than freshly rerun.

A fresh actual main window and owned local native page were prepared for pixel
inspection. The Windows screenshot helper returned
`window capture timed out: timed out waiting on channel` on both the first
attempt and one attempt after refreshing the returned window selection. Record
E203-main-view-result.json; pixel qualification remains false. This is not a
native webpage-load failure or a passing presentation check. No permission UI
button/input was used, and the same original Home was restored headless.

## Ordinary Android world timing (E204)

The actual main-host scheduler and production evaluator reproduce two deadline
failures: sleep-excluding time accepts a provider success after the original
elapsed budget, and timer-registration reentry can complete/retire a request
before an unconditional subsequent native dispatch. A partial fix also reproduces
a held-provider timeout waiting the remaining uptime delay after resume.
Retain E204-world-red.json/log and E204-timer-red.json/log.

Use elapsed time in that main scheduler, check original ownership/deadline after
registration before native entry, and check the original deadline through bounded
timer slices. Rejecting a never-sent request frees only its unused reservation;
sent callbacks keep native budget until genuine return/destruction. No timeout
or late native reply is replayed as a new effect. Nine controlled cases/27 checks,
world35/document86/profile396/native-budget18 and Java8 compilation pass.

Fresh full native647 has134 exact current input hashes, including ASan/UBSan/API26
and the original cleanup7137/worker300 gates. Fresh frontend20, create850/async77,
Python183/185 plus two existing skips, JS526 and integration13 pass. The new signed
APK and actual DEX Pending.checkDeadline/ownership methods pass source/archive/
payload/old-runtime checks, paired with unchanged Windows app bytes. The public
Android gate stays disabled, and physical provider/Activity/sleep/Doze remain
unqualified. The standalone diagnostic Activity's older scheduler is a distinct
follow-up; this stage repairs the main-host path. Full goal is not complete.

## Diagnostic scheduler consistency (E205)

E205 also brings the older standalone diagnostic scheduler under the main host's
elapsed deadline contract. Both actual caller expressions use the same world
evaluator and regression fixture, in separate generated classes within the one
QA output. The old diagnostic cohort fails three sleep/deadline cases while the
main cohort stays green; final18 cases/54 control checks pass. Diagnostic APK DEX
independently confirms the real clock instruction. Fresh Python183/185 plus two
existing skips, JS526 and integration13 pass. Native647/frontend20/create850 are
checked E204 exact-input reuse. Main APK/source/signature gates pass with unchanged
DEX/app archive/native host contents, and the full host has no diagnostic Activity.
No device/Activity/provider/sleep/Doze or Android admission pass is inferred.

## Full Agent protocol continuation (E206)

`python tools/agent_protocol_probe.py` exercises the actual session/run API,
Agent executor, installed C Browser Use tools, normal main-interface display
client and native WebView2. The only model endpoint is a loopback static SSE
fixture. Its seven scripted responses per cohort cover create, observe, an
intentional stale-document fill failure, corrected native fill, screenshot,
close and final text. Streamed argument fragments, original call IDs, cumulative
tool results and exact request counts are checked; effects are not retried.

Both vision and text-only configurations finish successfully. All twelve tools
have one original start/end pair, including two failed calls; each Agent has
one successful terminal and no error terminal. The vision request's exact PNG
hash equals the original image in the saved conversation. Text-only requests
contain an explicit image-unavailable notice and no image bytes; their original
image remains durably saved. This covers actual model-boundary serialization,
not a direct SDK invocation pretending to be an Agent turn.

The fixture refuses existing model overrides and credentials in incoming model
requests, and serves only local test pages. It does not change the default model,
write the vault or copy account/provider credentials. Cleanup checks the exact
fixture bytes, all existing config-file hashes, original Home and restored
headless runtime. A bounded HTTP worker per connection prevents Chromium's idle
preconnections from blocking model responses; Agent turns and all tests run
sequentially. No new shipped runtime dependency is introduced.

Keep the intermediate test failures as evidence: a prior shared test page was
different, the synthetic profile initially omitted required capabilities, the
oracle initially expected structured rather than ordinary plain-text failures,
the event query used unsupported full-text pagination, and a single HTTP
connection worker was blocked by a native speculative connection. These are
fixture corrections, not confirmed product defects. Final Python190/192 with
two existing Windows symlink skips, JS526 and all14 integration cases pass.
Product/overlay sources and development artifacts are unchanged. Android,
real-model quality, other model protocols, actual permission-button and physical
two-device qualification are not inferred from this result.

## Responses tool-image repair and protocol matrix (E207)

The original Responses serializer handled one text part or ordinary content,
but otherwise selected the tool message's empty plain-content field. A real
native screenshot succeeded and its original9,358-byte PNG remained in the
saved conversation; the next Responses function_call_output contained neither
text metadata nor image. The red fixture stops at request6 with both counts0.
The synthetic endpoint then rejects that request; this is not evidence that a
real provider necessarily returns that same error. Keep E207-responses-red.

Serialize multipart Responses tool output using the existing input-content
serializer. Text metadata and original image bytes stay under the same call_id;
ordinary strings/single-text output retain their previous representation.
This follows the [official function-output format](https://developers.openai.com/api/docs/guides/function-calling#formatting-results).
The repair is a reviewed, hash-checked native source overlay materialized from
the original Git baseline. apply_host first applies its inputs and updated
dependency tree identity to the existing .mdo/source. Build preparation accepts
only the exact original or repaired file in the one native checkout; foreign
edits/links/changed replacement hashes are preserved and rejected. No API/ABI,
model authority, plugin permission or browser capability is added.

Six actual Agent cohorts now pass42 local static stream requests and36 exact
tool terminal pairs. Responses and Anthropic preserve their native wire IDs,
fragmented arguments, successful PNG delivery and text-only projection; all
six intentionally failed tools recover to native fill and final completion.
The original images stay saved. The fixture does not supply absent image data
or fabricate successful tool results. Both native engines are rebuilt; actual
packed Windows .text/.rdata bytes match the rebuilt GUI host, packed12 and
main APK source/signature/native/payload/DEX checks pass. Fresh Python194/196
with two existing symlink skips, JS526, SDK, control28 and acceptance14 pass.

At E207, a separate Chat compatibility gap remained: tool images were put
inside role:tool, where the [official tool-message schema](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
permits only text. Its local fixture was permissive; the E206/E207 Chat results
proved data flow rather than strict standard-server acceptance. E208 repairs and
strictly tests that wire projection below. Real-provider acceptance remains open.

## Strict Chat tool-image transport (E208)

A fresh strict fixture rejects the sixth actual Agent request while preserving
its original saved PNG. The repaired native serializer retains text segments
and original call IDs in legal tool messages, then attaches media after all
results in the contiguous parallel group. The companion user message identifies
its untrusted tool source, original call ID and visible part order. Original PNG
bytes, URLs and detail values are retained; original text is not duplicated.
The request and saved ledger are unchanged. Media-bearing histories rebuild in
the reusable serialization buffer and retire the prefix key so arbitrary cuts,
retries or later text-only requests cannot reuse expanded stale wire bytes.
Text-only prefix caching remains enabled.

A compiled actual-source serializer/cache harness checks seven cuts, repeated
requests, media-to-text transitions, three parallel results, image-only/mixed
parts and nine xllm allocation failures with recovery and no instrumented leaks.
This is not whole-process/XRT OOM or ABI proof. The actual delivered native Agent
path separately passes all six protocol cohorts:42 static requests/36 tool pairs,
six ordinary failures recovered and six successful terminals. Strict Chat schema
and ordering are required on every Chat request, while all vision image hashes
match the originals. No real model or external site is contacted.

Fresh Python198/200 (two pre-existing Windows symlink skips), JS526, SDK,
control28, acceptance14, Windows/API26 ARM64 builds, packed12 and signed APK/
actual DEX/payload/native gates pass. Windows6,952,858B and main APK6,608,193B are
matching development artifacts; use their adjacent current hash receipts.
Android Java/DEX and diagnostic APK are unchanged. Historical native647/create850
reuse is qualified only with the verified unchanged Android composition; their
global overlay-manifest hash differs. Frontend20/world direct inputs are exact.
The original configuration/Home is restored. Physical Android, real-provider,
normal-window pixels, human permission-button and two-device gates remain open.

## Remaining completion gates

- Verify the installed Android main Activity/browser, actual provider input,
  capture/URI grants/chooser and lifecycle. The user deferred the phone and kept
  the current WebView. Overall Android automation remains disabled, including
  on otherwise feature-capable providers until the adapter is qualified. Never
  substitute page-world Agent execution or enable the gate from JVM doubles.
- Exercise an actual main-interface user decision. E200 confirms the prompt
  through normal-window accessibility; button action/pixel qualification remains
  separate from the decision API/managed worker. Current reports keep
  `rendered_permission_prompt_qualified:false`.
- Qualify account-authenticated two-device routing where required by the shared
  device workflow. Current native bridge tests intentionally keep account and
  physical-device flags false; they do not claim remote desktop pixel transport.
- Inspect normal main-window presentation alongside the verified mounted/native
  UI and actual delivered capture behavior. Current framebuffer/pixel flags are
  not evidence for that presentation.
- Recheck the whole matrix against any subsequent product changes, complete
  remaining implementation/qualification work and deliver matching artifacts
  with accurate limitations. The current development builds are not production
  updates, and the whole goal stays active.
