# Enhanced Windows development program

`dist/mdo-browser-use-dev-windows-x86_64.exe` is a self-contained development
build of the pinned mdo 0.7.0 program plus this repository's reviewed host/native
enhancements. Use it together with `mdo-browser-use-0.1.0.mdo-extension.json`.
The executable supplies the native browser and shared host services; the
portable plugin supplies the official Browser Use tools/SDK and Skill.
It is not an online release or an update-channel publication.

Development archives explicitly define `MDO_BROWSER_USE_DEVELOPMENT=1`. In that
build only, the updater reports `status:development`/`enabled:false` and does not
load, alter, install from or become blocked by the official update channel.
The fixed 0.7.0 preview must not claim to be an official newer version or be
replaced by a stock package that lacks these enhancements. Existing official
policy caches and version/build identifiers are preserved. Ordinary builds
without the preview flag retain the original updater behavior. The actual
packed-program gate checks the development status and unchanged cache hash.
The Android development archive verifies and reuses this exact reviewed prefix.

The current refreshed preview (W4, including W3b/W3a/W2/W1/E66/F21) is **6,934,261 bytes**, SHA-256
`26bc29244e682f42cbbc30ee1658f652ac9b2494b334f045ea30ff182fa13481`.
Its native inventory contains 750 files, including runtime 20, the shared
document/capture driver, E12/E16's native input/capture failure handling and E17's
lossless-source privacy fix, N1's explicit page-error navigation recovery and
E24's shared admission guard for uncertain native dialog cleanup, plus E27's
shared Android hover capability/preparation path and E28/E29's reviewed default
workspace SDK/download UI, E30's shared popup cleanup guard and F15's retained
screenshot/callback ownership and allocation-failure cleanup, plus P2's accurate
managed-tool cancellation and retained permission-admission marker, and N3's
confirmed-new-navigation recovery after a content renderer exit. Included module
hashes match the reviewed deployment. E60 also fixes visual validation rejecting
its own pending screenshot; E63 adds exact native subframe scroll-point routing
and admits visible content in partially clipped iframe owners. The actual
packed-program gate passes all twelve runtime checks, including actual default-directory SDK review, its packed C
header/discovery contract, connection-reset reporting and recovery
navigation from the error card. This is
Windows verification; Android framework qualification remains unavailable.

W1 also recovers an inert native drag quarantined solely for an unconfirmed
original-source observer. Native mouse/interception cleanup must have drained
without an unacknowledged result, and a changed nonempty native loader must prove
the original frame document was replaced. A metadata refresh, rotated public
alias, missing inventory or active callback is insufficient. Recovery releases
only the completed input fence: it sends no protocol work, does not replay the
failed drag and does not turn its saved failure into success. Real WebView2
regression passes 260 checks, including the controlled observer fault, retained
fence before retirement, replacement-document recovery and exactly one later
explicit click; the packaged program passes its twelve runtime checks.
Earlier intermittent nested-fill/child-navigation-drag failures and the Windows
realm audit remain open; this scoped recovery is not proof of their full cause.

W2 binds classic root-page script execution to its original native unique
context, with private randomized world names, native metadata correlation and
negative/positive provider capability probes. Numeric renderer-local context IDs
remain metadata on this path and cannot select a replacement execution world.
Same-name worlds instantiated in same-origin children do not overwrite or retire
the root pin; an identical native refresh cannot revive a retired pin. A
surviving original response/download reuses its world only after a fresh native
frame/loader match, with bounded preparation and world-name history.
The actual-method fault gate passes 45 checks, including numeric reuse before
its native event arrives and no execution fallback/replay. Real WebView2 passes
14 root-world checks, 1,376 full browser checks and 260 frame checks; the actual
packed program passes its twelve checks. W3b extends the original-realm binding
to frame evaluation/focus/owner, retained drag observation and upload/chooser
scripts. Android's separate build and qualification state is tracked in
[the Android adapter record](android.md); the earlier intermittent Windows input
failures remain open.

W3b shares admitted private worlds by original native source/frame/loader, with
an independent native-session incarnation token for OOPIFs. A newly created
frame world must still match its captured native loader before caller code is
admitted, including when a navigation event has not reached the UI yet. Both
evaluation and node functions use the original unique context. Root file inputs
retain their existing same-origin iframe/Shadow DOM support by checking the
original runtime ref/document rather than assuming the node belongs to the root
global's document or prototype. Fixed observer cleanup may use a retained unique
pin after reporting metadata is lost; that loss cannot authorize new input or
prove the observer was destroyed. Routed error receipts and callback ownership
are also fixed. Verification: 80 controlled world checks, 17 routed callback
checks, 14 actual root checks, 470 file-input lifecycle checks, 1,375 full browser
checks, 260 frame checks and twelve actual packed-program checks. JS 319/319 and
Python 169/171 pass, with the two existing Windows symlink privilege skips.

W4 reports a known persistent native world failure before another page effect,
with explicit manual-control/close guidance rather than repeated observation.
An error recorded during the first failed admission is diagnosed from the same
original scope without repeating its script. Already dispatched input keeps its
uncertain outcome; original receipts/recovery never replay it. Native dialog,
popup and picker boundaries and retained transfer cleanup stay available.
The actual C receipt adapter and public JS recovery preserve the reason/advice.
The refreshed program passes all twelve packed runtime checks and fourteen
actual original-world checks; controlled fault tests cover the persistent-error
classification, which is not a new real-provider failure reproduction.

F20 also removes the fixed queue-delivery delay while the existing authenticated
live connection is available. A coalesced, data-free hint wakes a serialized
read of the original UI queue; fallback polling, frozen scope and intent
journaling remain in place. The new program includes the C notifier, live
decoder and guarded poll pump and passes all ten packed runtime checks. The
same local eight-cycle workload measured median observe 1,654 → 242 ms, click
1,932 → 500 ms and capture 1,712 → 247 ms. These include HTTP/receipt polling
and are not general production or normal-desktop visual qualification.

The paired plugin is **247,351 bytes**, SHA-256
`c6b74a2780f13d08570e61bdaf58cbcbc8c4678dc06ce0348de5e8a370ae5d41`.
It includes P3's whole-call JS transport timeout/cancellation and independent
bounded cleanup, preserving original recovery identities and known outcomes.
Its 22-file native import/export roundtrip passes. The program gate now refuses
a stale paired plugin before launching the app, verifies the actual installed
manifest/inventory in the same Home, and requires the packed host and plugin
`page/runtime.js` to have identical hashes. A same-size runtime alteration is
rejected without launching another app. Package and program integrity/runtime
verification remain separate from full production/platform qualification.

On a Windows x86-64 device with WebView2, place the executable in a writable
directory and start it. Its default `mdo-home` is beside the executable; an
explicit `-- --home <directory>` selects an existing Home. Import the plugin JSON
in the plugin settings, review the source-trust prompt, enable it, and restart
when requested. No Python, Node, Playwright or downloaded browser is required by
these artifacts. Keep the adjacent SHA-256 inventory receipts for integrity
checking; they are not publisher signatures.

Development preview limits still apply. Android automation is incomplete and
the current tested Android provider lacks isolated execution worlds. Browser
Use's remaining lifecycle/OOM/network/performance gates and cross-platform
delivery are still being completed. Do not interpret the Windows smoke gate as
an Android or full production qualification.

## Reproduce without an application staging copy

```powershell
python tools/package_plugin.py
python tools/package_probe.py
python tools/dev.py stop
python tools/package_program.py
python tools/program_probe.py
```

The packer uses the existing source/native-host deployment. It checks the pinned
baseline, reviewed overlays, source-generated SDK and both host binary receipts.
The pinned application generator and Windows command forwarder run in place.
Only tracked/reviewed files and known generated inputs enter the product.
Unknown files or unrelated tracked changes abort packaging.

The existing pack CLI has no exclusion option. A private transaction journal
therefore records hashes before temporarily moving only the known development
configs, QA fixture pages and command-forwarder build receipt out of the app.
The original unity file receives the normal desktop-edition define during
packing. All inputs are restored and hash-checked afterwards; no app/Home copy
is made. After an interrupted build, stop the development service and run
`python tools/package_program.py --restore`. A changed destination is preserved
and requires inspection rather than an overwrite.

The production native pack provider validates the artifact inventory. The
separate runtime gate starts the actual executable from `dist/`, with no
unpacked app configuration or generated tree there, reusing the same development
Home and loopback port. It verifies the archive root, real frontend client,
installed C plugin, page observation, Unicode fill, trusted click, screenshot
and browser close against a synthetic loopback page. A real loopback connection
reset must report failure, then an explicit navigation must recover the same tab
from its error card with a fresh observed document. It then restores the
headless service. It does not send a model request or test external websites.

The executable receipt records inventory verification separately from runtime
verification; a newly built artifact is not marked runtime-verified until that
gate passes. `paired_plugin_verified` and its source/inventory hash identify the
specific plugin verified with the program. Private build/runtime/recovery
receipts remain under `.mdo/`. Rebuild/reimport a changed plugin before running
the paired program gate; an earlier native smoke receipt does not establish
that a newer plugin payload was delivered.

`python tools/package_lifecycle_probe.py` additionally verifies native-plugin
uninstall/restart/reinstall and interrupted import recovery in the same Home.
Uninstall retains immutable plugin generations for existing code/UI and drops
the installation receipt. Reinstall reuses retained bytes only when their full
SHA-256 matches; an edited file remains a conflict. The gate includes embedded
NUL/non-UTF-8 binary data, a changed retained file, preservation of an existing
browser record, removal of C services after restart, and recovery of a deliberately
incomplete payload/receipt transaction. It restores the ordinary 22-file plugin.
