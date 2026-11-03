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

The first command runs twelve **sequential** cases in the same `.mdo/source`,
native host and Home. It imports, uninstalls/reinstalls and recovers only this
known plugin revision, restarts the same test service and opens local synthetic
pages. The renderer case stops exactly one newly created QA content renderer,
after checking original process ancestry and creation identity. It never stops
an unrelated browser process. No phone/provider change, live model, account
relay, external website, online publication or extra application copy is used.
Logs reuse `.mdo/qa/acceptance/`; the bounded summary is
`.mdo/acceptance-result.json`. Preserve a failed log before rerunning.

The report binds 523 authoritative source inputs, all
141 applied host outputs, each log/receipt, actual host
binary/config/listener and remote C bridge dependencies. Inputs must stay fixed
while a case runs. A contradictory/nonterminal result cannot pass. `--check`
revalidates these unchanged hashes and the live runtime; it explicitly reports
reused evidence and does **not** claim a fresh execution or broader qualification.
The tool is development-only and adds no shipped Python/Node/browser dependency.

## Requirements and evidence

| Requirement | Inspected implementation and authoritative evidence | Current conclusion |
| --- | --- | --- |
| One reusable pinned environment | `mdo.lock.json`, `tools/dev.py`, reviewed overlays; exact PID/executable/config/listener and HTTP200 before/after acceptance | Verified in this project; no new source/Home/app copy |
| Shared C/JS API and Agent tools | `contracts.json` generates C registration/header and JS declarations; actual installed C services match all 13 schemas, plugin exports match every payload byte; 16 managed tools register | Verified Windows registration and shared contracts, not merely manifest presence |
| Observation, exact references, waiting and native input | E201 current-code SDK19/navigation4/wait5/JS520; fresh managed execution plus 26 public frame operations using two actual out-of-process frames, nested Unicode fill and selection | Verified stated Windows operations; DOM observation remains a bounded subset, not full accessibility-tree equivalence |
| Screenshots, visual actions and privacy | Fresh managed native canvas click/drag; read-only screenshots allowed while input/drag denied; six actual public fixed-scene PNG/JPEG integer/fractional/downscaled comparisons retain identical delivered bytes after private values change | Verified these capture/permission cases; separate desktop framebuffer presentation is not claimed |
| Tabs, popups, dialogs, uploads and downloads | Fresh managed popup/dialog checks, public nested frame picker/file/directory hash checks, original-task remote upload and one original POST/download/save with 131075 exact bytes | Verified actual local native and production-bridge workflows; not arbitrary OS dialogs or website business acceptance |
| Permissions and failure recovery | Fresh nine managed ask-decision cases with ten original durable tool terminal pairs (236 reported assertions), cancellation/late decision/stale document/run-grant isolation; actual owned renderer failure then explicit reload; durable admission survives same-Home restart without duplicate tab | Backend decisions/native recovery verified; E200 normal-main-UI accessibility confirms the scoped SDK prompt; actual user button decision and normal-window pixels remain unverified |
| Human control and resource lifetime | E197 actual main native request, current-source647 JVM/JNI modes, original cleanup7137, worker300, ordinary create850, frontend20 and fresh actual WebView2 control28; byte retention and independent ACK/native detach joins | Native/create proofs reuse E197:133 exact input hashes plus verified unchanged native entries in the aggregate overlay; fresh E199 mounted-control checks remain separate. Android peers remain controlled and do not qualify a physical WebView |
| Remote ownership and transfer | Fresh production C bridge -> real headed WebView2, eight cases including original-task binding, cancel/recovery, stale input denial, upload full hash and retained POST save; native bridge drains | Verified same-host protocol integration; account relay authentication and two-device networking remain unverified |
| Plugin installation and deliverables | Fresh portable import/export/trust/stale-plan8 and uninstall/reinstall/conflict/startup recovery10; exact current installed revision restored. Fresh E201 Windows/Android and unchanged E169 portable package hashes checked | Fresh matching development builds are verified; no official/store release is claimed |
| Android adaptation and explicit downgrade | Native classes/actual APK DEX, API26/JNI/source/lifetime gates, explicit capabilities/preflight refusal and retained manual browsing | Implementation and controlled checks continue; installed Activity/provider/device qualification and overall automation admission are incomplete |

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
