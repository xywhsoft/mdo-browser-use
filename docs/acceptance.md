# Browser Use acceptance and remaining work

The whole Browser Use goal is **not complete**. This record separates current
Windows integration, source-bound earlier results and unavailable qualification.
The matrix covers the original environment, shared C/JS API, native browser
operations, transfers, permissions/control/recovery, Android and distribution.
It does not redefine completion as a successful subset of tests.

E253 now verifies covered real same-account two-device authentication/control
flows against the user-authorized company Windows device: production ticket/
relay handshake, exact target/member identity, real server-side view rejection,
owned resource write/readback/stale-update/cleanup, consumed-ticket HTTP401 and
released peer occupancy. This is stronger than the earlier same-host bridge
fixture, but it is not full remote Browser Use. The company runs0.9.2 and the
current plugin/host is pinned to0.7.0; actual target replies/source confirm the
old JSON extension format is retired. E254 records the ZIP/host migration
boundary without changing the pin or company program. See
[physical-device acceptance](dual-device-acceptance.md).

E251 completes fresh17-case Windows acceptance against558 source inputs and152
applied outputs, with562 sequential JS checks,15 installed C plugin checks and12
packed-program checks. E250's real native tab-activation regression is included:
an existing keyed panel activates without changing its document/content revision;
background reuse and ordinary reports retain selection. Matching Windows and
signed Android development packages are rebuilt and their actual app/native
payloads verified. The portable C plugin is unchanged. No live inference or
publication occurs. The phone is no longer detected after E248/E249 manual
acceptance, so the new Android candidate is not installed/device-qualified;
the earlier physical results remain bound to the previously installed full APK.

E247 completes the requested Windows main-window human interaction checklist.
The user confirms every step: visible local page, takeover, typing Windows E247,
physical button action, return to Agent, retained page/input/result after hiding
and showing the workbench, new-tab navigation/switch/close, and maximize/restore.
The subsequent real Browser Use observation succeeds on the original document
and reads `Local action verified` at827x710. Record this as human-confirmed
presentation and physical interaction, not an automated framebuffer capture:
Computer Use capture timed out twice and pointer injection had no geometry.
The exact local test resources are cleaned and the same headless runtime is
restored. Product/artifact bytes are unchanged.

E248/E249 subsequently passes all eight current Android diagnostic component
gates on the unchanged WebView138, restores/verifies the installed current full
QA host and observes actual main/manual-browser pixels. Human typing/button
action and short background/lock UI retention pass, including new typing after
unlock on the same application process. The initial button-failure report is
clarified by the user: the expected result text was already present. A separate
background HTTP/ADB stall is retained as unqualified transport evidence, without
a product-root-cause claim. Clean all exact test resources/mappings and retain
the installed full host. Guarded host automation is still unavailable on this
provider; background service/full Doze and authenticated physical two-device
checks remain open. See docs/android.md for the exact qualification boundary.

E245/E246 now qualifies the human browser-open/page-display flow. The distinct
original request succeeds, creates its browser and retains one exact start/done
pair without an agent approval action or diagnostic reveal. After stopping
Computer Use with Escape, the user confirms that the page had displayed and
that exiting was intentional. Record that scoped manual acceptance as passed;
do not mistake the exit for a browser failure. This does not establish zero
additional window/tab interaction, enclosing desktop pixels or physical input
inside the page. The same headless runtime is restored with no test resources.

E243 completes the current E242 repair qualification: all16 acceptance cases pass
with554 source inputs/150 deployed outputs unchanged during the run, including
the new create-only display gate. Matching Windows/Android/portable-plugin builds,
557 sequential JS checks,14 installed C plugin checks and12 packed checks are
verified together. E240/E242 focused results below precede this full refresh.
The user-confirmed E240 approval remains separate from E241's expired retest;
repaired automatic display has native evidence but no new human confirmation.
Android physical qualification and the remaining full-goal gates stay open.

The user later reports that the E241 prompt was not seen. Its accessible heading
and buttons are not proof of visible pixels or human discoverability. Retain
that report with the expired original request; do not attribute it solely to
late feedback. E244 prepares the same development window at the exact balanced
QA conversation with no approval/deadline, and asks the user to confirm the
window first. No new permission request is started without that confirmation.

E240's actual human retest confirms one allow click and the original successful
tool terminal pair, but no page appears: `browser_open(create)` retained only a
suspended browser record, with zero display panels. An explicit reveal of that
same record loads the fixture and the user confirms seeing it. This qualifies
the human approval, not automatic display or enclosing desktop pixels. Fix the
shared C service to publish an activating owned display panel during create,
retaining download and required-automation admission. The new regression fails
against the original installed plugin and passes after the fix. All13 installed
C plugin checks,60 browser-component checks and the real managed/native workflow
pass; the latter now checks displayed/ready positive geometry before any observe
or followup browser command. This focused E240 result precedes E243's complete
full16 and matching-deliverable refresh; E236 evidence/builds are historical.

E242 additionally repairs older keyed browser/panel reuse: retain required
automation in the same owned panel and honor it before native create. Both the
actual SDK reuse and component admission regressions first fail, then pass with
all14 C plugin tests and557 sequential JS checks. The distinct E241 human retest
expires without a response and with success:false/effect:false; it does not
qualify automatic display. E240's actual human approval remains verified.

E235 additionally fixes native viewport collapse while a browser pane is hidden:
actual controller bounds changed827x710 ->1x1 ->827x710 during a decision. Retain
the original meaningful bounds for hide/disposal and accept a new visible size
when shown again. Fresh553 sequential JS checks and the actual native viewport
regression pass; the pending controller now stays827x710. E236 completes fresh
acceptance16, including the preserved native dimensions, and rebuilds matching
Windows/Android artifacts. E234 full16/artifacts are historical.

E233 reproduces an additional visibility defect at the default1100px test
window: the workbench overlays the conversation at widths up to1100px; a
maximized workbench also hides it on wide screens. A selected approval can
therefore exist in accessibility while its conversation is covered. Decisions
now temporarily yield the covering workbench without changing saved tab order,
resource identity or maximization preference. Explicit inspection remains
available; new decisions take priority again. The same native document returns
after cancellation, without input or navigation replay. Fresh551 sequential JS
checks and the actual native before/after regression pass. E234 completes fresh
acceptance16 and rebuilds both matching development artifacts; E232 is historical.
This does not qualify an actual human approval or enclosing desktop pixels.

E231 updates the shared panel error UI after the human reports every visible
old tab as unavailable. An exact `resource_missing`/`browser_missing`404 now
explains that the page ended and offers a scoped close, without automatically
reopening a URL or retrying an absent resource. Transient, ownership and unrelated
404 errors keep their ordinary retry. Fresh96 component checks and the actual
deleted-resource/main-browser UI path pass; the newly packed Windows program
passes12 runtime checks. E232 subsequently passes the complete current15 again
and rebuilds the matching Android app archive; E226 full15 is historical.
The original human request expired after300 seconds with a failed tool result
and exactly one start/done pair; no browser was created and human permission
acceptance remains unqualified. No claim is made about why the user missed the
earlier prompt. See E231 in the progress record for the current verification scope.

## Reproduce Windows integration

Run in this repository with the existing development deployment started headless:

```powershell
python tools/acceptance_probe.py
python tools/acceptance_probe.py --check
```

The first command runs seventeen **sequential** cases in the same `.mdo/source`,
native host and Home. It imports, uninstalls/reinstalls and recovers only this
known plugin revision, restarts the same test service and opens local synthetic
pages. The renderer case stops exactly one newly created QA content renderer,
after checking original process ancestry and creation identity. It never stops
an unrelated browser process. No phone/provider change, live model, account
relay, external website, online publication or extra application copy is used.
Two cases add eighty-four loopback static protocol requests through the actual
Agent loop (single and grouped calls); they are not LLM inference or provider
calls. Each requires absent
`config/models.json` and its backup, preserves an existing override by refusing
the check, and removes only its exact newly created nonsecret fixture file.
Existing settings hashes and the headless runtime must be restored.
Logs reuse `.mdo/qa/acceptance/`; the bounded summary is
`.mdo/acceptance-result.json`. Preserve a failed log before rerunning.

The report binds 558 authoritative source inputs, all
152 applied host outputs, each log/receipt, actual host
binary/config/listener and remote C bridge dependencies. Inputs must stay fixed
while a case runs. A contradictory/nonterminal result cannot pass. `--check`
revalidates these unchanged hashes and the live runtime; it explicitly reports
reused evidence and does **not** claim a fresh execution or broader qualification.
The tool is development-only and adds no shipped Python/Node/browser dependency.

## Requirements and evidence

| Requirement | Inspected implementation and authoritative evidence | Current conclusion |
| --- | --- | --- |
| One reusable pinned environment | E251 fresh acceptance17 binds558 current inputs/152 applied outputs and exact host/config/Home/listener before/after; no extra application copy | Verified in this project; same headless deployment restored |
| Shared C/JS API and Agent tools | E251 actual installed13 C services/16 managed tools;12 single/grouped cohorts across Chat, Responses and Anthropic,84 local static requests/90 original tool terminal pairs;12 ordinary failures recover | Current local tool continuation/image projection verified; real-provider/model quality is not qualified |
| Observation, exact references, waiting and native input | E251 managed tools/public frame operations through actual Windows native views; E226 Android/JNI evidence retained separately below | Stated Windows operations verified; bounded DOM subset, not complete accessibility-tree equivalence |
| Screenshots, visual actions and privacy | E251 managed visual operations and six public fixed-scene PNG/JPEG integer/fractional/downscaled comparisons; original dimensions equal and complete delivered bytes unchanged after private edits | Verified these actual capture/permission cases; enclosing desktop framebuffer remains unqualified |
| Tabs, popups, dialogs, uploads and downloads | E251 real local managed/native flows and eight remote-bridge cases; exact131075-byte save and one original POST; E226 native-source378 assertions are prior source-bound evidence | Original local/bridge ownership and transfers verified; arbitrary OS/site/provider business flows are not claimed |
| Permissions and failure recovery | E251 nine managed ask-decision cases/237 assertions/ten original terminal pairs; selected-decision native hide/restore preserves827x710 without a grant; explicit owned-renderer and same-Home restart recovery | Backend decisions and native presentation verified; human browser-open/page-display confirmed in E245/E246 and full human main-window/input checklist in E247; automated framebuffer capture remains unavailable |
| Human control and resource lifetime | E226 fresh full658 JNI workflow modes,7137 attached-release assertions, current source/clock/input/capture/file/native-owner checks, fresh20 frontend recovery modes; native Android UI/provider peers are controlled | Earlier controlled integration retained; E248/E249 current device components and human short UI recovery pass; guarded host/full Doze qualification remains incomplete |
| Remote ownership and transfer | E251 production C bridge to actual headed WebView2: eight cases, original task binding, cancellation/recovery, upload digest and retained save | Same-host protocol integration verified; authenticated physical two-device routing remains unverified |
| Plugin installation and deliverables | E251 real portable import/export/trust/stale-plan8 and lifecycle/recovery10; current Windows packed12 and signed Android archive match current app inputs | Matching development artifacts retained; no new Android-device or official release claim |
| Android adaptation and explicit downgrade | E226 full native/JNI/ASan/UBSan/API26 and frontend20 evidence retained; E250 changes shared panel activation only, not Android Java/native source | Current components/previous-candidate manual UI pass; latest candidate device retest, guarded host and full Doze remain open; automation stays disabled |
| Bounded Windows performance and repeated lifecycle | E203 historical16-cycle actual main/public C measurement; E251 verifies current functional lifecycles | Earlier bounded measurement retained; no new performance or production leak-absence claim |

E236 does not rerun the full658 native entry point. Its only changed recorded
input is overlays.json: the previous entries/files remain identical and the20
new entries target panel-manager.js, application-sdk.js and app.js only.
Java/native sources are unchanged. The additional browser-view.js UI edit is
outside that native report's input set and is covered by current553 JS checks,
actual native viewport checks and current16 Windows acceptance instead.
Frontend20 source/class/library hashes still match E226 and are reused, not a
fresh run. The rebuilt APK verifies its current signed app/native payload and
production DEX definitions; this does not qualify an installed physical device.


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

## Actual grouped native Agent calls (E209)

The earlier actual Agent matrix streamed one tool per reply. Add a second case
with original group sizes1/1/3/1/2/1, preserving every streamed call and its index/
item ID. Chat/Responses JSON arguments interleave with reverse second fragments;
Responses item completion also reverses. Anthropic uses separate indexed blocks.
The existing configuration/credential guard and same-Home restore path are reused.
This expands the fixture; it does not diagnose a new product bug.

Each real native Agent cohort executes nine tools. Two distinct-limits screenshot
calls surround a stale-document failure in the same reply. The next reply fills
the original field successfully; another group observes/captures, then closes
and finishes. Every later request checks all previous images. Six vision/text
cohorts pass42 requests/54 original start/done pairs and retain18 saved PNGs.
Call groups and original journal IDs/statuses/final events are independently
checked against disk; all wire vision PNG bytes match the saved originals.
This tests grouped replies through the actual decoder/executor/ledger, not
simultaneous native operation or real-provider acceptance.

Combined with single-call cohorts, fresh acceptance15 covers84 static requests,
90 original tool pairs and12 ordinary failures recovered. Fresh Python203/205
(two existing Windows symlink skips) passes. Five new negative tests reject lost,
duplicate/cross-associated images, collapsed groups and foreign IDs.543 inputs/
150 applied outputs are bound to the current proof. Product inputs/applied bytes
and both E208 artifacts remain exactly unchanged; JS526, SDK, control28, packed12
and native/Android checks are qualified reuse. The full goal's physical/device,
real-provider and actual main-window qualification limits are unchanged.

## Completion audit after E210

This audit starts from the full objective and current sources, not from a green
summary. It inspects mdo.lock.json, contracts.json, the generated C/JS contracts,
runtime/plugin.c, both SDK clients, host routing/capability sources, plugin and
package manifests, actual native/build/artifact receipts, current acceptance
journals and the remaining platform/UI/device evidence. Documentation commands
are not treated as successful runs merely because their scripts exist.

| Original requirement | Current authoritative evidence | Status and completion limit |
| --- | --- | --- |
| One reusable pinned project environment; single development stream; controlled disk use | Actual same-Home setup red then two green repetitions, exact host/config/settings/150 applied hashes, pinned Git/build receipts; one existing source/native/object tree | Verified for this existing deployment. New seed requires the pinned bootstrap; no clean-room app/Home copy is run or claimed. README now applies reviewed inputs before native build. |
| Official plugin and simultaneous C/JS API | Single contracts generator,13 registered services/16 tools, C registrar, sdk/browser_use.h and sdk/client.mjs; enabled installed plugin and actual C/JS/managed/native paths | Verified stated shared-contract and Windows flows. Static provider streams verify transport/continuation, not inference quality or real-provider approval. |
| Observation, locating, waits and native Windows input | Actual managed/public frames/readiness tests, exact original refs, field-state and trusted input receipts, unchanged-source SDK and JS regressions | Verified documented bounded DOM/frame scenarios. This is not a complete browser accessibility tree or arbitrary website business acceptance. |
| Tabs/popups/dialogs, screenshots, upload/download/save | Real local native/managed operations and privacy capture pairs; transfer hashes, original task/picker/source identity; durable journals and packaged code | Verified stated local workflows. Physical Android chooser/URI/provider/Activity behavior remains unqualified. |
| Permissions, takeover, cancellation and restart/renderer recovery | Actual decision API/native/managed checks, original terminal pairs and uncertain-effect retention, current control source hashes | Backend/control paths verified. E245/E246 adds human open/display and E247 adds human main-window/physical-input acceptance; automated framebuffer capture remains unavailable. |
| Android adaptation and explicit downgrade | Packaged API26 ARM64/DEX/native classes; actual MdoBrowser.browserUseSupport returns available:false and state reports automation:false; shared dispatcher refuses unsupported automation | Partly implemented/controlled-tested; incomplete platform qualification. Old-provider manual downgrade does not prove a capable-provider adapter, actual Activity/input/capture/URI grants/chooser/lifecycle/Doze. The user deferred phone/provider changes. |
| Device routing and ownership | Actual production C bridge to real native browser in the same host, original-task transfer and cancellation checks | Verified same-host bridge scope only. No current account-authenticated two-physical-device proof. |
| Ecosystem installation, lifecycle and documentation | Real local import/export/trust/stale-plan/uninstall/recovery cases, package allowlist and matching inventories;58 documented tool script paths exist | Local installation verified; updated setup/build instructions source-checked and existing setup actually rerun. Device-only commands are inventory, not passed qualification. Store/official update publication is not performed. |
| Deliverable and production stability claims | Actual current Windows/APK/portable hashes, host/source/ABI/payload/signature/DEX receipts, fresh15-case acceptance; source-bound resource/OOM/lifetime/performance tests | Matching development artifacts verified. Measurements retain their limited scope; no leak-absence, arbitrary-provider or whole-product completion claim is inferred. |

E210 fixes a demonstrated setup reuse defect: an existing reviewed host no
longer depends on another repository's current build output. An unknown local
host and a changed first bootstrap are still refused. The actual setup passes
twice with preserved state and original runtime restoration. Five trust guards,
fresh Python208/210 (two existing Windows symlink skips) and acceptance15 pass;
544 inputs/150 outputs are bound to the current proof. Shipped code and E208
artifacts are unchanged, so existing JS526/SDK/control28/packed12/native/Android
proofs are qualified reuse. No new platform admission is enabled.

The full objective is **not achieved**. Current evidence leaves Android physical
qualification, actual main-interface decision/pixels and authenticated physical
device routing incomplete. Real-model/provider/site business acceptance also
remains untested and is not asserted. Keep the original scope and deferred
user preferences; do not turn these missing proofs into successful completion.

## Main-window helper quota and capture recovery limits (E211)

Actual normal-main accessibility exposes the selected QA session, native local
fixture and browser toolbar. The first ordinary sidebar-button attempt fails
with CreateWindowExW/0x80070486. Read-only GetGuiResources identifies the exact
Computer Use helper at 10,000 USER objects; the development host has23. Resetting
the supported JavaScript session leaves the same helper and quota unchanged.
This proves the immediate helper resource failure, not an mdo allocation leak
or the cause of all earlier capture timeouts.

After explicit user authorization, verify the helper PID/path/start time/hash,
stop only that process, and let the supported sky import initialize its managed
replacement. Its initial USER count is3. Reopen the original dev main window
with the same Home/session and a new local fixture browser. Native observation
and normal-main accessibility succeed. Capture fails first with FrameArrived
timeout, then after fresh selection/activation with window-capture timeout.
The one subsequent ordinary sidebar-button attempt reports coordinate input
geometry unavailable. No screenshot or successful UI input is qualified. Stop
capture retries; the quota repair does not establish a rendering repair.

The final managed helper is below quota at266 USER objects. This is a bounded
snapshot, not proof of a leak-free helper. Close only the newly owned fixture
browser/panel and restore the same headless Home/listener/config/host. Both
pre-restart and recovered-attempt reports remain private under .mdo/E211-*.
The interrupted initial preparation found its saved dev PID already absent and
no listener, before any mutation; standard identity-guarded stop/start restores
the deployment without changing product sources or user data.

Fresh --check accepts all15 previous integration receipts against current source
and deployed hashes. It is unchanged-input evidence reuse, not a fresh suite.
Windows/APK/plugin bytes stay unchanged. Main pixels/button interaction, physical
Android and authenticated physical-device routing remain open. The Computer Use
guidance also forbids acting on security/privacy permission requests; an actual
main permission decision must be performed by a human, not substituted by an
API decision or synthetic DOM click.

## Original native context loss must revoke reported readiness (E212)

A controlled original JNI/worker event reproduces a product state defect:
Runtime.executionContextsCleared is fully dispatched, the native binding has
lost its root context witness, and the UI navigation epoch is unchanged, yet
protocolPopupDocumentState still returns installed:true/ready:true/phase:ready.
Keep .mdo/E212-context-ready-red.log. Actual execution keeps its existing native
guards; this finding concerns a contradictory readiness snapshot, not an
unproved successful effect through stale authority.

The state getter now reads the original worker's concurrent/volatile unavailable
fence before reporting installed/ready. This performs no protocol request,
rebind, world creation or automatic retry. Explicit inventory retains the
existing original-source recovery path. New cleared/default-context-destroyed
cases require document_unavailable; a foreign context ID leaves ready unchanged.
All16 state cases pass; the three new cases preserve the UI epoch and prove
read-only status queries. Fresh
native650 passes27,655 reported popup assertions; the old647 business outputs
are identical, excluding assertion counters. Actual dispatcher/frame driver to
JVM/JNI recovery passes20 modes, and ordinary creation/control850 passes.

Fresh Python208/210 (two existing Windows symlink skips) and acceptance15 pass,
binding544 inputs/150 outputs with84 static requests/90 original tool pairs and
12 ordinary failures recovered. JS526, SDK/control and packed12 retain their
unchanged-input scope. The Windows archive and ARM64 engine are exactly reused;
both Android packages are rebuilt/source/signature checked. Inspect actual main
DEX method instructions, not just descriptor strings: the original state getter
contains its worker-fence call. The main APK remains6,608,193B with SHA-256
68d832861f6e634b3d8cf168a3bbffa09cf60606e70a9bf5775e553a8d72c45f.
Native/app payload hashes stay unchanged; Java/DEX changes. The original Home,
headless service identity and configuration are restored.

These checks cover the original native root/default witness and clearing events,
not every per-frame isolated realm or ongoing native-origin transition. That
review, actual main-window input/pixels, physical Android/provider/lifecycle and
authenticated physical-device routing remain open. No phone/provider change,
real inference, external website, additional app/Home/native copy or official
publication occurs. Overall Android automation and whole-goal qualification
remain false.

## Isolated root and loading readiness publication (E213)

The prior default-context fence did not observe destruction of the root's
isolated execution realm. A new original JNI/native-world event reproduces the
same ready:true snapshot with the UI epoch and default context still unchanged.
Keep E213-isolated-ready-red.log. Frame routing already invalidates its original
Node epoch; publish that same atomic flag through a volatile root reference at
the end of a successful worker snapshot. The UI reader checks only graph-live
and original-epoch flags. The binding's graph reference is also volatile; no
worker-owned graph/world map is accessed by UI status and no world is created.

Five new cases cover root isolated loss, independent child loss, explicit root
recovery, native loading and loading-stop without automatic readiness revival.
The first positive recovery attempt correctly refused unproved native document
provenance. Preserve both failure logs; supply and join an authentic original
root commit in the fixture before testing recovery. All21 state modes and native
655 pass with27,948 reported popup assertions. Compare old650 business outputs:
only the previously reviewed loading-timeout transport-close stack and frozen
worker cleanup observation differ. Final original detach, native destruction,
source release and unblocked cleanup requirements remain identical.

Current-source frontend/JVM/JNI20 passes; its full-compile class hashes exactly
match the focused run, so it is not rerun solely after recompiling identical
classes. Ordinary create850/async77 has exact unchanged input hashes and is
reused. Fresh Python208/210 (two existing Windows symlink skips) and acceptance15
pass;544 inputs/150 outputs bind84 static requests/90 tool pairs/12 recovered
ordinary failures. Existing JS526/SDK/control/packed12 remain qualified reuse.

Both Android packages are rebuilt/source/signature checked. Inspect actual DEX
definitions and instructions: volatile publishedRoot and frames references,
two atomic get calls in the map-free frame reader, one binding-to-frame reader
call and the snapshot publication store. Main APK6,608,193B has SHA-256
86e8a74e28c45e21bda4b71c3fb05977d630e5030c8c67aa6134f7d19eaa40cd.
The exercised Windows archive, ARM64 engine and native/app payload bytes remain
unchanged; Android DEX changes. Restore the same Home/headless host/config/port.
Physical Android/provider/lifecycle, actual main-window pixels/human decision,
authenticated physical-device and full native-origin qualification remain open.
No phone/provider change, live inference, external site, extra app/Home/native
copy or official publication occurs; overall Android automation remains false.


## Shared native navigation readable state (E214)

The actual product route reproduces page_ready:true after native root realm
loss even though isolated_world.ready is false. The same original native
document, Activity epoch and navigation ID remain current. Both public states
now use the original document's live-readable predicate; no status query rebinds
a world or replays a navigation. Three native cases (145 assertions) prove
isolated loss, default-context clearing and explicit same-reference recovery.
Existing navigation association already preserves that recovery and is unchanged.

The first full run fails in an existing activation cleanup assertion: a test
reads the transport-close counter before disarm's finally releases process
admission. Preserve that log and join original final cleanup in the bounded
wait; product cleanup is unchanged. Focused owner-loss cases, fresh frontend20,
full native658/28089 reported popup assertions, create850/async77, Python208/210
(two existing Windows symlink skips) and acceptance15 pass. Two already qualified loading-timeout scheduler diagnostics vary; their exact values are retained and final detach/destruction/source-release/admission remains unchanged.
Acceptance binds544 inputs/150 applied outputs,84 static requests/90 original
tool pairs and12 recovered ordinary failures. JS526/SDK/control28/packed12 use
only unchanged-input evidence. Main APK 6,608,193B has SHA-256
975f4ed8df9a3ceb1803b89133fab4d38f5cd17d7c643e028da61b52cbb01651.
Actual DEX verifies both shared-reader calls, the original worker fence and
retained atomic publication. The Windows archive/ARM64 engine/app payload and
portable plugin remain exact. Both Android packages are rebuilt, source/signature
checked; provider/framework/device flags stay false. Same-Home headless service
identity is restored; no inference, phone change, extra app copy or publication.
The remaining physical/UI/device completion gates below are still open.


## Current-phone qualification and profile-only diagnostic repair (E218)

The user authorizes the current phone on 2026-11-04. Windows sees RMX5090,
Android16/API36, WebView138.0.7204.179. Port5037 belongs to wslrelay and its ADB
server reports Linux/native USB, so Windows adb devices is empty despite a healthy
Windows WinUSB phone interface. Use the existing hash-matching Windows ADB37.0.1
on an unoccupied5038 port; the same phone is immediately authorized. Do not stop
the shared WSL server, change drivers or copy another SDK. Select this server for
the probe process with ADB_SERVER_SOCKET=tcp:127.0.0.1:5038 and pass --adb explicitly.

The actual diagnostic reports profiles:true/isolated_world:false and product
admission correctly refuses three automation requests before creation, retaining
manual/cleanup access. The old diagnostic nevertheless waits15s and reports
device_probe_timed_out: MdoBrowserDocument intentionally retains a physical-only
profile instead of throwing when optional isolation is unavailable. Handle that
actual terminal phase immediately, requiring original profile assignment, no
installed world and no readiness. The physical regression changes from timeout
to isolated_world_unsupported with probe_verified:true and confirmed cleanup;
automation/world isolation remain false. Only the diagnostic code changes.

Fresh same-APK device gates:6pass/2unresolved. Explicit downgrade, native JS
dialogs, popup transport, chooser retention/cancellation, private upload cache
and URI seek/revoke pass. These component results do not prove host integration,
automatic file assignment or an Agent operation. Hover produces exactly one
trusted zero-button move at the expected target, preserving focus/click/press
counts, but CSS :hover remains false; keep the full controlled before/after
receipt and do not relax the assertion. Capture's view is attached/shown with
nonzero size, but no initial scale callback was observed; retain null scale and
capture_scale_or_view_unavailable. Neither issue has a confirmed product root
cause or product repair here. Add bounded fixture-only failure evidence to both.

Install the matching complete QA host (same dedicated package/signing identity,
formal mdo untouched), cold-start its real main Activity and inspect actual
Android screenshots. Its local service registers one client; a manually owned
native browser visibly renders a local fixture via USB reverse. Close that exact
browser/panel, remove only the owned forward/reverse mappings and stop only the
QA package; retain installation/data for reuse. Installed main APK SHA-256
dedb25501eb777d90588348f00f705a8b840b77c945a4c5317bad9e196459e65 matches the build. Main UI/manual browser pixels are
observed on Android, not a Windows-main or guarded screenshot qualification.
A read timed out while another app was foreground; after bringing the same
QA Activity forward it succeeds without process restart. This is a bounded
observation, not a diagnosed background/Doze failure or completed lifecycle gate.

Fresh world35/document86/profile396/native-budget18 and both elapsed-clock
cohorts pass. Both Android APKs rebuild and retain actual DEX/signature/payload
checks. Diagnostic 987,539B has SHA-2564c9a4b85ec52a27c12dadeda7d1ee6391329645a981cb05dc2646bada8d59556.
Windows/native engine/app/plugin sources are unchanged. Prior Windows/SDK/
JS/native-protocol results are scoped unchanged-input reuse; aggregate E214
acceptance source hashes are historical after these diagnostic edits, not a
fresh source-qualified15-case run. No live model, provider upgrade, extra app
Home/source checkout or official publication. Overall Android automation,
Windows main permission/pixels, authenticated two-device and full-goal
qualification remain incomplete; current-phone deferral is superseded.


## Initial native scale on the current phone (E219)

The visible attached WebView can finish its first document without emitting
onScaleChanged. E218 consequently retains NaN and refuses capture. A controlled
initial-native-scale experiment produces3.5 and passes21 pixel/encode/cancel
checks. The hover-enter experiment still fails CSS hover and is reverted;
no speculative hover product change or relaxed assertion is retained.

The common production view listener now requests an original native visual-state
barrier when initial scale is missing. A weak exact-view/document-epoch token
samples a candidate only after that callback. Existing native scale callbacks
take precedence; navigation, close, replacement, duplicate callback and getter
reentrancy cannot publish stale data. One submitted callback per physical view
coalesces100 navigations; an ambiguous native-registration failure retains its
bounded slot until terminal callback or view retirement. Cross-thread cancel is
rejected before mutation (red regression retained). No polling, page execution,
new world or document/input admission is introduced.

[Android getScale documentation](https://developer.android.com/reference/android/webkit/WebView#getScale())
warns of renderer/UI races. The sampled value is only a geometry candidate;
original native/isolated document, viewport/physical-size, scroll, scale and
privacy checks remain required before and after capture/encoding. The visual
barrier alone does not qualify capture or eliminate that API limitation.

Fresh lifecycle143 and exact common-host bridge28 checks pass, alongside
ordinary create850/async77, frontend20, full native658, world35/document86/
profile396/native-budget18 and both clock cohorts. The old658 business results
match (assertion counters excluded); even the separately inspected
loading-timeout scheduler fields are unchanged. Final native cleanup stays
unchanged. Python208/210 passes
with two existing Windows symlink skips. Acceptance15 binds
546 current inputs/150 applied outputs,
84 static requests,90 original tool pairs and12 recovered ordinary failures.

The final installed diagnostic completes8 component modes:7pass/1unresolved.
Capture passes21 with measured native scale3.5 using the actual production
visual callback. CSS hover still fails; world isolation is an explicit downgrade
on unchanged WebView138.0.7204.179. Guarded Android automation remains false.
The complete main APK's actual manual native browser now reports
native_scale_ready:true after loading; inspect its actual DEX callback/token
and preserve native/app payload bytes. Dispose exact browser/panel/actions,
remove owned USB routes and stop only the QA package. Formal mdo/data/provider
are untouched. This does not qualify full guarded Android automation or Doze.

Main APK:6,608,193B SHA-25648445e4c0264b498369368da426e66e5c1e79c98302bf48fe66ac9db52238d7c.
Diagnostic:987,539B SHA-2563ab5de0089ed690bdac9b1bcca2e96173e47b49d2b6ffb342f449c0fe6d85ce7.
Windows/native/app archive/portable plugin bytes remain unchanged. No live model,
additional app/Home/native copy, provider upgrade or publication. Windows main
human-permission/pixels, authenticated physical two-device and remaining Android
framework/host qualification stay open; the whole goal remains incomplete.


## Touch feedback and subsequent mouse hover (E220)

The current phone supplies new evidence before disconnection: a cold hover
passes, but hover shortly after a trusted tap initially matches :hover and then
loses it. The fixed fixture records true at0.7/16.2ms and false at50.3/100.3ms;
the earlier button becomes hovered again, with no additional input event, click,
focus change, active touch or active pointer. Delaying only the next hover by
100/250/350/1000ms passes all17 original native-input checks. Experimental results
are explicitly unqualified and their source/flags are reverted.

[Chromium138 EventHandler](https://chromium.googlesource.com/chromium/src/+/refs/tags/138.0.7204.179/third_party/blink/renderer/core/input/event_handler.cc)
retains a deferred tap element for a minimum150ms active feedback interval and
later updates its hover/active state. Attribution to that exact timer is an
inference from the pinned source plus the measured overwrite, not an instrumented
browser stack. The demonstrated integration defect is interleaving mouse hover
with unfinished touch feedback; it is not an inability to generate native hover.

After an accepted UP/CANCEL on the exact owned target, record native uptime.
For a subsequent mouse hover, wait the remaining platform
[double-tap gesture window](https://developer.android.com/reference/android/view/ViewConfiguration#getDoubleTapTimeout()),
with a150ms floor. Cold/settled hover has zero delay. The sequencer reserves its
original job during this asynchronous wait, rejects delays outside0..1000ms and
keeps the original500ms input/guard budget after it. No guard or coordinate
witness is cached across waiting. Cancellation, navigation, hidden/replaced view,
viewport change and deadline still prevent effects. No UI-thread sleep, extra
mouse move, focus event, touch or replay is introduced. This is a conservative
gesture window for the observed race, not universal provider/lifecycle proof.

Preserve the red early-hover regression. Fresh84 settle checks pass, including
native-feedback overwrite ordering, cold latency, busy exclusion, stale state,
cancel/late callback, OOM, bad timing, reentrant replacement and the full bounded
guard budget. Existing input177/lifecycle167/channel22/guard63/wheel66/hover82/
initial-scale143 and ordinary create850/async77/scale-bridge28 pass. Recompile
only the changed production Input class and nested classes in the existing JNI
output after verifying every other native source hash. Three fresh JNI frame-
input/held-resource/held-request cases and frontend20 pass with current class
hashes. Targeted Python1+8 pass. The old full658 native and acceptance15 aggregates
are historical after Input/Target changes, not newly source-qualified complete
runs; no unchanged broad suite is repeated. No extra source/app/Home/native copy.

Both Android packages rebuild and retain actual DEX/signature/private-provider/
native/app-payload checks. Actual DEX also proves the post-dispatch release clock,
platform timeout and bounded sequencer scheduling; it does not prove device
behavior. Main:6,608,193B SHA-25656dc13a4d1c4cfdb02a74f1837c09a2e72e32cd6162cdee2bb4bc9950ac1c509.
Diagnostic:991,635B SHA-2562352d6896299950da3a7c5e31f6c0a4dc901c840e9905ade8f9cb1e2a119ab7c.
Windows, native engine/app archive and portable plugin bytes remain unchanged.

The phone disappears before final-source device tests start; the user explicitly
defers reconnection and asks to continue other work. Final candidate hover/input,
current main installation and restoration from the last diagnostic variant are
pending. Last completed E219 cohort remains7pass/1unresolved and is not current-
candidate qualification. Current WebView138 is unchanged; isolated-world and
overall Android automation gates remain disabled. No live model or publication.
Windows main presentation/human decision, authenticated physical two-device and
remaining Android/provider/lifecycle qualification remain open; goal active.


## Current Windows main native-browser delivery (E221)

Reuse the existing QA session, exact local presentation page, deployment and
Home. Validate the live host/config/listener and idle work before starting the
actual main window. The first private preparation used an unsupported session
URL and failed before mutation; use the authoritative project/session route.
No new session, app copy, Home, provider, credential or model call is needed.

Current Computer Use identifies the exact development window and its native
page/toolbar through accessibility. Two fresh-selected/activated whole-window
captures fail with `window capture timed out: timed out waiting on channel`.
An indexed ordinary native-button click fails with `coordinate input geometry
is unavailable`. Its effect is not claimed. A supported UIA value setter does
change the exact synthetic native field, confirmed by fresh accessibility.
One Tab command is accepted but has no verified focus/effect transition; do not
count it as a successful keyboard action. No security/authentication UI is acted
on. A read-only inventory shows two helper processes at2282/56 USER objects,
below the earlier10000 quota failure. It does not establish which helper owns
this request, diagnose the capture cause or prove leak absence.
Do not restart the shared helper or invent an mdo rendering defect from this.

Separately exercise the product's own public screenshot service against that
same mounted browser/document, release its receipt and visually inspect the
delivered PNG. It visibly contains the fixture heading, native field/button and
the input's masked region. Image:714x600,
15,614B SHA-2567efdea55d1115a867e52b4f83db6b3cb71dd98fe88ec66813deb4bdd0a1d4092.
This is current actual-main native-browser image delivery, not a whole-window
framebuffer capture or a successful physical pointer/human permission decision.
Those qualifications remain false.

Close only this original owned browser/panel and verify its fixture disappears
from current main accessibility. Preserve legacy/foreign tabs. Revalidate exact
PID/image/window config/listener/idle work before restoring the original
headless config, host, Home and port. No product/artifact changes or new broad
suite are needed. E220's source-bound candidate tests remain exact-input reuse;
Android final-device/main-install checks remain deferred by the user. Complete
main presentation, human permission, Android lifecycle/provider and authenticated
physical two-device qualification remain open; the full goal stays active.

## E222 — expire native input across device suspend

A controlled two-clock regression fails on the previous input sequencer: uptime
stops during suspend, so a queued hover or late guard can still send input after
the original real-time budget has expired. This proves a local deadline defect;
it is not a new physical-phone or Doze observation.

Separate suspend-inclusive elapsed deadlines from uptime MotionEvent/KeyEvent
timestamps and gesture cadence. The common host and all four device probes use
elapsedRealtime explicitly. A bounded50ms uptime watchdog rechecks the same
original elapsed deadline after resume. Every pre-focus/guard/dispatch boundary
also checks that deadline. No waiting renews the budget, stale callbacks cannot
send effects, and an already-held touch/key is cancelled/released exactly once.
A reentrant retirement during clock sampling cannot continue native dispatch.
No event replay, UI-thread sleep, provider change or new dependency is added.

Preserve the failing regression log. Fresh32 two-clock cases/checks pass alongside
the existing input177/lifecycle167/channel22/guard63/wheel66/hover82/scale143 and
hover-settle99 assertions (the latter counter includes additional timer slices,
not15 new behavior cases). Three focused actual JNI frame/held-input cases pass
after recompiling only Input and its nested classes in the existing output.
Eight packaging tests pass. Both APKs rebuild; actual DEX verifies every shipped
input scheduler's explicit clocks, separate event/deadline reads and50ms watchdog,
plus the retained state/atomic/initial-scale/hover gates. Signatures, private
provider, native/app payload and old runtime checks pass. No full658 native,
frontend20 or acceptance15 rerun is claimed after these input-source changes.

Main APK:6608193B SHA-2560eee13702758fca7f9f83096024d9978e9c59cd2442e7787565644776a4fcc8d.
Diagnostic:991635B SHA-256d3b9a6814df596215c40d04ebb2608e27fd57605843e7380c35d5395405d8a53.
Windows/native engine/app archive/portable bytes remain unchanged. The same
headless host/config/Home/listener is restored and has no active operations or
runs. Phone reconnection, current candidate installation and physical sleep/
input/provider qualification remain user-deferred; the previous diagnostic is
still only last-observed installed. Android automation stays disabled. Main-window
human/pixels and authenticated two-device gates remain open; goal active.

## E223 — keep screenshot deadlines across device suspend

The actual capture runtime also used uptime for its operation budget. A new
regression compiles that production scheduler against separate elapsed/uptime
and Handler peers: the old code accepts a late pre-draw guard after the original
budget expires during simulated suspend. Preserve its failing log. This is a
local timeout defect, not an observed phone pixel leak or a Doze qualification.

Use elapsedRealtime for the capture budget. Translate the unchanged original
elapsed deadline into at-most50ms Handler uptime slices; each watchdog callback
checks expiry before rearming that same deadline. Existing pre-draw, native
readiness, encoded-result and final-guard checks now share that suspend-inclusive
clock. Expired results withhold/clear their encoded bytes; native/bitmap leases
still wait for their original acknowledgements and cleanup. No early watchdog
success, timeout renewal, native drawing replay or UI-thread sleep is introduced.

Fresh40 runtime-clock assertions and304 original capture lifetime checks pass.
They cover delayed guards/readiness/encoding, post-encode private-byte clearing,
resume watchdog, eligible captures, original deadline and timer rearm failure.
Incrementally rebuild only Capture/nested classes in the existing JNI output;
seven fresh frame/capture/takeover/resource cases pass and drain their original
reservations. E222 input tests are unchanged-source reuse; its source-bound
three input JNI cases precede the Capture change. Full658 native/frontend20/
acceptance15 are historical, not current whole-suite runs.

Both APKs rebuild with the same native engine, app archive and portable plugin.
Actual DEX verifies the production capture clock, Handler translation and bounded
watchdog without deadline writes, plus current input clocks and retained
state/atomic/initial-scale/hover gates. APK signatures, private provider and
native/app payload checks pass. Main:6608193B SHA-2568649026aa9d9c32a43940d6f021a83cc7005363b4329a5935c0eac7dfb01ec08.
Diagnostic:991635B SHA-2567fcd88e10b9c88b871b680e7854af07b5b819b039813bee30b4fccaab03196d5.
Windows bytes remain unchanged. Restore the same headless host/config/Home/port,
confirm no active runs/operations and preserve all account/session data.
No phone polling/install, provider change, real model call or publication occurs.
Final device candidate installation/hover/sleep qualification remains deferred;
Android automation stays disabled. Windows main human/pixels and authenticated
two-device gates remain open; the whole goal remains active.

## Current popup receipt deadline (E224)

The old original acknowledgement could beat its queued expiry and publish success
after5000ms. The production callback now validates that saved deadline before
publication/resource release. Actual consumption drains its own resource while
late/unprovable receipts retain failure and cannot bind a child or confirm Stop.
Fresh44 controller assertions and18 actual native-source assertions cover this
ordering, duplicate callbacks, clock failure and exact teardown. Ten focused JNI
modes pass; the native-source mode is refreshed with the new cases. Both APKs are
rebuilt and actual DEX/signatures/private-provider/payload gates pass. Historical
full native/frontend/acceptance aggregates are not current whole-suite results.
No phone/provider/model/publication action; full completion gates remain open.

## Intermediate combined regression failure (E225)

The attempted current full native run stops at protected hover input. Preserve
its failure: diagnosis finds501 virtual milliseconds in78 real milliseconds,
and a frozen-clock test reproduces timer-poll time acceleration. Correct only the
integration scheduler, retaining production deadlines and all original outcome
assertions. Fresh12 clock checks and18 focused native input/control modes pass.
The combined complete native/frontend/acceptance run is still pending; historical
full-suite receipts remain explicitly unqualified for these changed inputs.
Product sources and all E224 deliverable bytes are unchanged. Platform/human/
two-device gates remain open.

## Current combined integration qualification (E226)

The source-fixed complete native entry point now exits successfully:658 workflow
modes and all following native/worker/frame/input/capture/file/chooser/cleanup
gates pass, including the new12 frozen-clock assertions. The original E225
failure and501-vs78ms diagnosis remain retained. Fresh frontend20 then passes
against exactly those current classes/native library. No source or applied-host
input changed during either run.

Fresh Windows acceptance15 also passes with549 authoritative inputs/150 applied
outputs,84 static requests,90 original tool pairs and12 ordinary failure/recovery
cohorts. It verifies real local/native/public/remote C bridge paths and restores
the same headless host, config, Home and port with zero active runs/operations/
browsers/panels and no model override files. No real model, external test page,
phone/provider action, new application/Home or publication is involved. All
E224 development deliverable bytes remain unchanged. Rebuilding unchanged
packages or repeating unrelated passing suites is unnecessary for this phase.

This supersedes E225's pending combined software run, without overriding any
physical-platform gate. Actual Android current-candidate installation/hover/
guarded host/URI/lifecycle/Doze, actual human permission decision, complete
Windows main-window pixels/input and authenticated two-device routing remain
unverified. The user defers the phone; the Windows human-availability question
has no answer yet. No such decision is inferred and no permission UI is acted on.

## Remaining completion gates

- Complete Android guarded main-host input/capture/URI/chooser and lifecycle
  qualification. E248/E249 supersedes the old final-source device deferral and
  E2197pass/1unresolved with current8/8 component checks, verified current main
  installation and human short UI-lifecycle acceptance. Background service/full
  Doze and guarded host automation remain unqualified. Overall
  Android automation remains disabled, including
  on otherwise feature-capable providers until the adapter is qualified. Never
  substitute page-world Agent execution or enable the gate from JVM doubles.
- Complete native Browser Use operations through the actual company target.
  E253 qualifies covered same-account two-device authentication/control flows;
  E254 identifies the current0.9.2 directory-ZIP/host migration prerequisite.
  The earlier same-host bridge fixture remains distinct and does not claim
  physical-device authentication or remote desktop pixel transport.
- Windows normal main-window presentation and physical interaction are human
  qualified by E247. Automated whole-window capture remains unavailable and is
  not a substitute for, or an additional failure of, that completed human gate.
- Recheck the whole matrix against any subsequent product changes, complete
  remaining implementation/qualification work and deliver matching artifacts
  with accurate limitations. The current development builds are not production
  updates, and the whole goal stays active.
