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

The report binds 509 authoritative source inputs, all
127 applied host outputs, each log/receipt, actual host
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
| Observation, exact references, waiting and native input | E199 current-code SDK19/navigation4/wait5/JS512; fresh managed execution plus 26 public frame operations using two actual out-of-process frames, nested Unicode fill and selection | Verified stated Windows operations; DOM observation remains a bounded subset, not full accessibility-tree equivalence |
| Screenshots, visual actions and privacy | Fresh managed native canvas click/drag; read-only screenshots allowed while input/drag denied; six actual public fixed-scene PNG/JPEG integer/fractional/downscaled comparisons retain identical delivered bytes after private values change | Verified these capture/permission cases; separate desktop framebuffer presentation is not claimed |
| Tabs, popups, dialogs, uploads and downloads | Fresh managed popup/dialog checks, public nested frame picker/file/directory hash checks, original-task remote upload and one original POST/download/save with 131075 exact bytes | Verified actual local native and production-bridge workflows; not arbitrary OS dialogs or website business acceptance |
| Permissions and failure recovery | Fresh nine managed ask-decision cases (194 reported assertions), cancellation/late decision/stale document/run-grant isolation; actual owned renderer failure then explicit reload; durable admission survives same-Home restart without duplicate tab | Backend decisions and native recovery verified; rendered permission prompt still unverified |
| Human control and resource lifetime | E197 actual main native request, current-source647 JVM/JNI modes, original cleanup7137, worker300, ordinary create850, frontend20 and fresh actual WebView2 control28; byte retention and independent ACK/native detach joins | Native/create proofs are E197 source-hash-verified reuse; fresh E199 mounted-control checks remain separate. Android peers remain controlled and do not qualify a physical WebView |
| Remote ownership and transfer | Fresh production C bridge -> real headed WebView2, eight cases including original-task binding, cancel/recovery, stale input denial, upload full hash and retained POST save; native bridge drains | Verified same-host protocol integration; account relay authentication and two-device networking remain unverified |
| Plugin installation and deliverables | Fresh portable import/export/trust/stale-plan8 and uninstall/reinstall/conflict/startup recovery10; exact current installed revision restored. Fresh E199 Windows/Android and unchanged E169 portable package hashes checked | Fresh matching development builds are verified; no official/store release is claimed |
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

## Current main-interface findings (E199)

The actual mounted browser component reproduced a loading layout defect:
native webpage height714 became692 and top86 became108 when its ordinary
loading status row appeared. Keep the red report. E199 uses the existing global
screen-reader-only style for this routine live announcement; tab/stop controls
still show loading. Manual, pending control and recovery/error notices remain
visible. Actual DOM/native bounds now remain identical during loading, and the
original viewport privacy matrix also passes thirty pairs. That is independent
evidence; it does not retroactively prove the cause of E198's incomplete receipt.

Normal main-window UI Automation successfully identifies the real selected QA
conversation and its ask-mode composer. The screenshot channel timed out twice;
normal-window framebuffer/pixel presentation remains unverified. A real SDK
managed browser_open request remains pending in the backend with run_id0 and no
project/session fields in its approval descriptor, but this conversation has no
approval heading. The frontend filters approvals through registered Agent run
identities. This is a confirmed missing SDK-to-conversation association. The
exact QA request was cancelled/released, its approval disappeared and no browser
was created. No UI permission button was clicked; no model request was made.
Do not display every run_id0 request in every conversation. Carry original,
validated ownership into approval data and retain strict selected-scope filtering.
This defect is not repaired by E199 and remains a required next implementation.

## Remaining completion gates

- Verify the installed Android main Activity/browser, actual provider input,
  capture/URI grants/chooser and lifecycle. The user deferred the phone and kept
  the current WebView. Overall Android automation remains disabled, including
  on otherwise feature-capable providers until the adapter is qualified. Never
  substitute page-world Agent execution or enable the gate from JVM doubles.
- Fix the confirmed SDK approval association defect, then exercise the actual
  main-interface permission prompt and its user decision,
  rather than only the decision API/managed worker. Current reports keep
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
