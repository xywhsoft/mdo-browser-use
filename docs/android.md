# Android Browser Use adapter

The Android implementation is in progress. The existing `MdoBrowser.java`
provides manual content WebViews and page-world script operations. It does not
yet implement the Windows Browser Use input/capture/frame/transfer contract.
No Android automation capability is enabled by this milestone.

## Installed-WebView boundary foundation

`host/android/MdoBrowserWorld.java` is project-owned Java code which calls the
installed WebView's published support-library boundary. It adds no AndroidX
AAR, browser engine, JavaScript sandbox package or application runtime.
`host/android/protocol.json` pins the consulted Chromium boundary-interface
revision, source URLs and SHA-256. Upstream implementations are not vendored.

The adapter receives the provider class loader from its native caller, resolves
methods in that exact loader and uses invocation handlers directly across the
loader boundary. It does not use a hidden Android framework API. The eventual
Android caller must check the public class-loader API's OS support, create the
content WebView/profile before loading pages, and enable automation only when
all required provider capabilities and native adapters are ready.

- Detect `JS_INJECTION_IN_FRAME_AND_WORLD`; never guess support from the OS or
  WebView version, and never fall back to page-world execution. A separate
  `MULTI_PROFILE` check gates assignment of the content browser's profile.
- Register a named non-default execution world and a listener in that world.
  Only this exact native view's main-frame messages are admitted; subframes,
  foreign views, oversized messages and foreign/retired peers are rejected.
- Keep the provider's canonical reply peer for each original document. Execute
  only through that peer's frame/world API. Navigation retires its pending jobs;
  a new main-frame peer never receives a replay intended for the old document.
- Limit scripts to 128 KiB, results to 256 KiB, bootstrap messages to 16 KiB,
  pending jobs to 16 and each evaluation to 10 seconds. The Android UI caller
  supplies a monotonic scheduler. Failed timer admission sends no script;
  deadlines, late/duplicate callbacks and frame destruction settle once.
- A lost evaluation acknowledgement preserves possible-effect uncertainty.
  Close removes only the owned script/listener, drains pending jobs and reports
  unconfirmed cleanup. An uncertain script-registration acknowledgement requires
  destruction of the owned view; it is not silently reported as clean.

The reviewed Android overlay now connects provider feature inspection and
automation admission to the actual `MdoBrowser.java`. It does not enable its
automation commands. Native trusted pointer/key input, navigation epochs,
capture/privacy and dialogs/pickers/transfers must be integrated and verified
before enabling Android automation.

## Verification

```powershell
python tools/android_world_probe.py
```

The existing WSL JDK compiles the actual adapter with a Java 8 production target.
A foreign provider class loader exercises the boundary signatures, scopes,
resource limits, deadlines, destroyed/retired frames, lost/duplicate/late replies,
cleanup retry and partial registration. All outputs reuse `.mdo/qa/android-world`.
This is a 34-check protocol/lifetime contract gate, not an Android framework,
APK or real-device pass. No extra application/Home is created.

## Minimal APK and actual provider gate

```powershell
python tools/setup_android_client.py
python tools/build_android_client.py
python tools/android_device_probe.py
```

The setup reuses the existing WSL JDK 17 and installs only the checksum-pinned
API 36 platform and Linux build-tools 36.0.0 into the single `.mdo/` deployment.
It downloads 129,615,669 bytes, occupies approximately 247.3 MiB, discards verified
installation archives and requires no NDK or downloaded browser. A 10 GiB free
disk margin and explicit owned-directory/lock receipts prevent accidental cache
expansion or replacement. No runtime dependency is added.

The reusable Java device client contains the actual world/input adapters,
reviewed `MdoBrowser.java`, one tiny Activity and a no-network-permission manifest. It is built with Java 8,
dexed, aligned and signed with a persistent private development key under
`.mdo/host/android-client`. The app ID is `org.xleaves.mdo.browseruse.dev`;
it does not contain a second mdo service, Home, account or session database.
The APK and source/toolchain hash receipts remain untracked. It is not a release
client or a replacement for the user's mdo installation.

The runner requires one authorized device. It reads only its own PID/tag's
nonce-bound terminal log, never clears global logs, and stops only this dev app
afterward. System installation confirmation can outlive an ADB wait; rerunning
verifies the installed APK bytes before reusing it, rather than requesting a
second installation. Unknown existing packages are preserved. No provider is
automatically installed, selected or upgraded.

On 2026-10-28 the user confirmed the earlier 45,459-byte client installation. The actual API 36
phone's `com.google.android.webview` 138.0.7204.179 reported 72 provider features:
`MULTI_PROFILE` and `WEB_MESSAGE_LISTENER` are present, but
`JS_INJECTION_IN_FRAME_AND_WORLD` is absent. The production adapter returns
`isolated_world_unsupported` before registering a world or evaluating a page.
The owned view is destroyed and the runner stops the dev app. The existing
`org.xleaves.mdo` and knowledge QA app/data remain unchanged.

This is a verified **unsupported-provider outcome**, not an Android automation
pass. The supported-device path contains a local HTML isolation fixture, but
it has not run on this phone. Full Android Browser Use and supported-provider
regression remain outstanding. Latest receipt: `.mdo/android-device-result.json`.

## Product capability and safe degradation

Native `capabilities` includes `browser_use:{available,reason,
isolated_world_feature,profile_feature}`. Installed-provider support and product
readiness remain separate. A missing execution-world feature reports
`isolated_world_unsupported`; a supporting provider still reports
`android_adapter_incomplete` until the native adapter is integrated. A manual
view's state explicitly advertises `automation:false`. Legacy manual browsing
capabilities are not a claim of safe Agent automation.

The shared workbench checks this descriptor before sending an automation URL,
creating its native view or revealing an action panel. C/JS callers receive a
structured `unsupported_capability` result with an allowlisted capability reason
and `effect_may_have_occurred:false`. An existing manual tab also refuses page
evaluation/input, with guidance that repeating the tool cannot add the missing
capability. Native admission independently rejects automation/intercepted-download
creation and legacy Agent page-world operations. Manual browsing, user takeover,
stop/close and cleanup remain available. There is no page-world fallback.

The current device also runs the actual product capability/admission methods:
three automation requests are rejected before creation, while manual and cleanup
admission remain available. This does not exercise Android's complete Browser Use
transport or automation. A single dedicated workbench test verifies structured
preflight results for observe/action/navigation/capture/upload without a create
or reveal command. The user elected to retain the current system WebView; no
provider-update request is pending.

## Native input foundation (E3)

`MdoBrowserInput.java` serializes bounded native input on its owning UI thread.
`MdoBrowserInputTarget.java` dispatches touchscreen events and allowlisted content
keys only to the owned WebView; Unicode text uses its current `InputConnection`.
It never dispatches global/system input, executes a DOM click or assigns a DOM
input value. The WebView/IME retains ownership of the input connection.

Each operation pins the original document epoch and view dimensions. Hidden,
detached, background/unfocused windows are rejected; keys/text additionally
require native focus. Changes before/after each dispatch terminate the sequence.
The higher layer must still verify isolated-world DOM focus, frame identity and
CSS-to-view geometry before admitting an operation. These classes are deployed
through the reviewed Android overlay; product automation remains disabled until
that higher layer and the remaining Android contracts are integrated.

An operation has at most 64 frozen touch points, five seconds of gesture time
and a 500 ms dispatch allowance; only one can run per adapter. Text is bounded
to 16 KiB of valid UTF-8 (8,192 UTF-16 units), with no NUL/unpaired surrogate.
Timer admission precedes the first event. Cancellation, navigation, takeover,
lost focus and close release an owned press with CANCEL/key-up, then drain its
timer and complete once. A lost/failed dispatch preserves possible effects;
failed cleanup poisons that adapter until its view is destroyed. Successful
dispatch receipts are not proof of a page's eventual business result.

`python tools/android_input_probe.py` compiles the actual sequencer as Java 8
and passes 125 deterministic protocol/lifetime assertions, including stale
queued work, reentrant invalidation, late callbacks, timer failures, viewport/
document/focus changes, held-input cleanup and Unicode/coordinate/key limits.
This is not an Android framework or real-device pass. The existing world gate
still passes 34 assertions, and the shared unsupported-provider UI gate passes.

The E3 74,131-byte dev APK added a separate synthetic input fixture:
`python tools/android_device_probe.py --mode native-input`. It checks trusted
touch/input/key events, Unicode commit, selection replacement, cancelled-touch
nonactivation, swiping and hidden-view refusal. Its page-world reads are solely
assertions on the owned test fixture, never a product automation fallback.
`--mode world` retains the existing provider gate. Both use the same app and
separate receipts. No second Home, engine or provider is installed.

That APK compiles/aligns/signs successfully against API 36, Java 8/min API 26,
with no added runtime dependency. ADB currently reports no device, so the input
framework receipt is explicitly `unavailable`, `native_input_verified:false`.
The new APK has not been installed or run on the phone. The user's current
WebView remains unchanged and no update request is pending.

## Owned view/input lifecycle integration (E4)

The actual `MdoBrowser.java` now owns its views through
`MdoBrowserLifecycle.java`. A reused tab name is not a native view identity:
navigation, completion, error/TLS/HTTP and renderer-exit callbacks first check
the original WebView instance. The previous key-only callbacks could overwrite
or remove a replacement view after closing/recreating the same tab. TLS failures
still cancel their own handler even when the originating view has retired.
Legacy asynchronous script replies are additionally pinned to that view's epoch;
a changed document returns an unconfirmed result instead of stale success.

Each actual content view receives the input adapter at registration. Navigation
intent/start advances its epoch and cancels owned input before loading; hiding,
user takeover, stop and close also invalidate/close its pending sequence. Input
cleanup precedes detachment/destruction. The pinned Activity's pause/resume hooks
cancel held input on pause and admit only fresh work after returning; they never
replay cancelled work or erase a view's separate hidden/manual state. Destroy
drains all input before destroying the corresponding WebViews. Foreign/retired
views cannot alter the replacement's epoch, visibility or manual ownership.

The actual registry and sequencer pass **67 lifecycle assertions** in addition
to the existing 125 input assertions. Gates exercise equal-looking but distinct
view instances, stale callbacks after same-ID recreation, navigation/hidden/
takeover/background/stop/close, slot limits, exact ownership, reentrant
replacement and a throwing completion while other views still need cleanup.
The synthetic native surface does not prove Android framework behavior.

`build_android_client.py` now applies the actual reviewed browser hooks to the
pinned `XsActivity.java` and compiles it with its real Java dependencies and
the actual mdo adapters. This compile gate reuses the existing owned build
directory and adds no APK/service/Home/engine. Those Activity classes are not
packaged into the tiny fixture APK. Native Java templates and the applied hook
script are covered by its source-hash receipt.

The E4 tiny APK was **82,323 bytes**, compiled/aligned/signed with
`production_activity_compiled:true`. Its input fixture now uses the same
production lifecycle registry. The shared unsupported-provider preflight still
passes, reviewed overlays match, and the original headless development service
is restored. ADB still reports no device, so this new APK has not been installed
or run; the native input framework result remains unavailable. Full Android
world/focus/frame/geometry/capture/transfer integration and supported-provider
regression remain pending. The current WebView is retained and automation
remains explicitly disabled.

## Main-document channel and asynchronous replies (E5)

`MdoBrowserChannel.java` gives each connection an exact native port/document
lease. A repeated URL, reused port value or same tab name cannot substitute for
that original connection. The actual Activity invalidates its channel at main
navigation start and renderer loss, including same-URL reloads before another
`onPageFinished` arrives. Detachment cancels all owned content input. A callback
from an old port is ignored, and a late initialization closes only its own
untransferred endpoints. Successful transfers keep the page-owned endpoint's
ownership with the page. Destroy permanently invalidates the channel.

Legacy asynchronous script replies now retain both their original content-view
epoch and original main-document connection. A reply from a retired connection
is dropped instead of being sent on the replacement port, even if an identifier
or URL repeats. The bootstrap replaces an earlier waiting initialization
listener, keeping one pending listener per document and one transfer per
handshake; iframe-originated messages still cannot claim the native port.

`android_input_probe.py` passes the actual Java channel's **22 ownership
assertions**, the updated registry's **69 lifecycle assertions**, and the
existing **125 input assertions**. Two additional Node tests extract the script
from the applied Android Java source and exercise 128 repeated installations,
foreign/invalid transfers, listener cleanup, result forwarding and a fresh port
replacing the old JS route. These are JVM/JS protocol tests, not real Android
message-port/input tests. Run the overlay check with them so the deployed source
must match the reviewed host enhancements.

The actual Activity/hooks and the tiny **86,419-byte** fixture APK compile;
the APK aligns/signs with the existing private development key and adds no
runtime dependency. Full JS regression passes **190 tests**; Python regression
passes **122/124**, with two existing Windows symlink-privilege skips. Reviewed
host overlays and generated shared C/JS contracts match. The same headless
loopback service is healthy again. ADB remains unavailable: no new APK has been
installed/run, and the current WebView is unchanged. Android automation remains
disabled until its remaining world/focus/frame/capture/transfer integration and
supported-provider verification are complete.

References: [execution worlds and message listeners](https://developer.android.com/reference/androidx/webkit/WebViewCompat),
[per-frame reply execution](https://chromium.googlesource.com/chromium/src.git/+/HEAD/android_webview/java/src/org/chromium/android_webview/JsReplyProxy.java),
[boundary architecture](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/webkit/webkit/docs/feature_checking.md),
[native gesture cancellation](https://developer.android.com/reference/android/view/MotionEvent#ACTION_CANCEL),
[text commit contract](https://developer.android.com/reference/android/view/inputmethod/InputConnection#commitText(java.lang.CharSequence,%20int)),
[message-port ownership and closing](https://developer.android.com/reference/android/webkit/WebMessagePort).

## Content document integration (E6)

The production `MdoBrowser.java` now binds a `MdoBrowserDocument` to every exact
content WebView before its first requested navigation. It installs the existing
`MdoBrowserWorld` adapter when the provider supports it. Ordinary manual views
retain their existing profile; an explicit automation profile is assigned only
for an automation request, which the unfinished product still refuses.

The document controller accepts its fixed bootstrap announcement only through
the adapter's native main-frame peer. The bootstrap sends once per document
and does not announce from child frames. Execution uses that peer's frame/world
API, not a URL-selected or page-world fallback. This follows the published
[reply proxy frame/world contract](https://developer.android.com/reference/androidx/webkit/JavaScriptReplyProxy)
and [main-thread message-listener lifecycle](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge).

Navigation suspends document admission before invoking input cleanup. A new peer
that arrived before the native start callback is retained while the previously
admitted peer is retired. Epoch/view changes reject late success. Main-channel
replacement, backgrounding, manual takeover and stop drain original jobs without
replay; their blockers are independent. Native replies retain the original
main-document connection. Close removes the installed world before destroying
the view and returns a separate world-cleanup confirmation. Failure metadata
is removed with its view rather than accumulating closed tab IDs.

The private native `automation` command is wired to this isolated executor.
Its per-view `isolated_world` state describes installation/readiness/phase/jobs
and actual profile assignment. These are lower-level diagnostics, **not product
readiness**: `browser_use.available` and Agent admission remain disabled until
the rest of the Android contracts are integrated and verified.

Verification uses the actual production classes: 34 boundary assertions and
47 document assertions with a foreign loader, plus the existing 125 input,
69 lifecycle and 22 channel assertions. Document cases include early replacement
peers, callback reentry during navigation, independent blockers, cancellation
causes, old epochs/views, cleanup and unsupported providers. Five targeted Node
tests cover the extracted Java bootstrap, channel handshake and unsupported
product preflight. These are protocol/lifetime tests, **not framework passes**.

The real Activity/hooks compile with API 36 and Java target 8; the same tiny
dev package builds/signs to 94,611 bytes without NDK or new runtime dependencies.
Its world device gate now uses this production document controller. The first
Activity compile caught its missing helper copy; the reviewed actual Android
overlay now includes it, and the build receipt covers the helper inputs.
ADB reports no device, so this APK has not been installed/run; document/world
and native-input framework receipts remain explicitly unavailable. The current
system provider and existing mdo installation/data are unchanged.

DOM/native focus and geometry, frame/capture/privacy and transfer integration,
native callback retention/resource budgets, and supported-provider device
qualification remain outstanding before enabling Android automation.

## Retained native evaluations and shared admission (E7)

A deferred-callback fixture reproduced a resource-limit defect: after each
client deadline the old implementation removed its client job and admitted
another evaluation, reaching 17 native dispatches through a nominal 16-job
limit while all client jobs had finished. The original failure receipt/log are
retained under `.mdo/android-native-budget-first-failure.json` and
`.mdo/android-native-budget-before.log`. This is a production-Java protocol
failure; it is not attributed to the installed Android provider.

Unfinished provider callbacks now retain their original reservation after
timeout, cancellation, navigation, listener removal or lost dispatch
acknowledgement. Admission is limited to 16 per content view and 32 across the
browser's views. Client jobs and native outstanding work are separate state
fields. A terminal callback releases only its own reservation; late or repeated
callbacks cannot complete a client twice or decrement a replacement's work.
Only a confirmed `WebView.destroy()` of that exact closed view can release work
whose provider never acknowledges completion. Closing a listener alone is not
such proof. Detached callbacks retain a weak world reference and the small
shared budget, not the completed UI/session callback.

The native-budget fixture passes 18 assertions, including shared admission,
exact-view destruction, late callbacks and timer-allocation OOM before native
dispatch. Existing 35 boundary, 47 document, 125 input, 69 lifecycle and 22
channel assertions pass, as do all 196 JavaScript tests. These are protocol and
lifetime checks, not Android framework results. The real Activity and the same
tiny QA package compile; its APK is 98,707 bytes with SHA-256
`5412d32b1fef1740a1645eee38706ee91248801bf7803d46e05cc30c3a0b650e`.
No device is currently available, both device receipts remain unavailable, and
the current system WebView is preserved. Android automation remains disabled
pending the remaining integration and supported-provider qualification.

## Guarded main-document native input (E8)

The private `input` bridge now connects the real document controller and native
input sequencer. It requires `navigation`, `input_document` and `input_ref` from
the original isolated runtime. Focus, text and key requests also require the
same `focus_document`/`focus_ref`; click coordinates are CSS viewport pixels.
Native state exposes the actual navigation epoch, per-view input busy/cleanup
status and whether its scale is known, including failed-input diagnostics.
Each step checks that exact main-document reference and focus or hit point in
the isolated world, then rechecks native view ownership, epoch, viewport,
scroll, scale and visibility before dispatching native input. Native focus and
DOM focus confirmation share one reserved action slot. Click, text and key
delivery use WebView touch, input-connection and key APIs, without a synthetic
DOM input fallback.

Native scale comes only from the exact content view's `onScaleChanged` callback;
unknown scale rejects as `input_scale_not_ready`. This follows Android's warning
that [`getScale()` can race rendering](https://developer.android.com/reference/android/webkit/WebView#getScale())
and its recommended [`onScaleChanged` notification](https://developer.android.com/reference/android/webkit/WebViewClient#onScaleChanged(android.webkit.WebView,%20float,%20float)).
A changed scale cancels original held input. Old view callbacks cannot set a
replacement's scale. The isolated viewport must also fit the native dimensions;
offset pinch viewports and nonzero padding reject. Initial callback delivery,
rounding, IME resize and scale timing still require actual provider tests.

An independent watchdog bounds asynchronous checks, including a provider that
never replies. Timeout, cancellation, close, changed focus or rejected geometry
settle once and release held native input. Late or duplicate checks cannot
dispatch a replacement action. Scheduler-allocation OOM removes both queued
tasks and the reservation before any input. Client expiry does not release the
underlying world's native reservation; E7's independent budget still applies.

This initial private bridge supports a root-document left single click, focus,
text and the native key subset. Frame chains/snapshots, mouse variants and other
input kinds refuse. Shared Android frame-driver/Agent wiring, capture/privacy,
transfers and supported-provider framework qualification remain unfinished.
Product Browser Use admission stays disabled. This is not Android end-to-end
qualification or an API to enable unsupported providers.

Verification: 63 deferred-guard, 81 lifecycle, 125 input and 22 channel Java
assertions pass. Four Node tests execute the actual Java fence constant; the
full JS suite passes 200 tests. The real Activity and bridge compile, and the
same QA APK is 110,995 bytes with SHA-256
`68c100a30ca85ffb16c3e071854ba7cbb62c9fae4b25c74baa32abb85f58afc2`.
Both device receipts are unavailable because ADB currently has no device.
`guarded_native_input_verified` remains false even if the older low-level input
fixture is later run. The current provider, existing mdo package/data and
qualified Windows development artifacts are preserved.

## Native document identity and resume ordering (E9)

Each newly admitted main document now owns an independent `fd-` identity in
native state. The state omits a usable identity while loading, blocked, closed
or unowned. A duplicate load or release of a blocker on the same document does
not rotate it; navigation/replacement does. A private `automation` request can
pin `native_document` and numeric `navigation`. Mismatches reject before the
provider receives code, and successful replies include both original values.
This identity is a document alias, not authority to execute arbitrary model
scripts; the shared driver still selects fixed operations and Agent admission
remains disabled.

A regression fixture also reproduced an early-peer race during resume: the
replacement's announcement could arrive before its native navigation start.
Adopting it on resume made the later start retire the replacement permanently.
The controller now retains that candidate for the navigation transition.
Its failing log is preserved. The production classes pass 59 document, 35
boundary and 18 budget assertions; nine Android JS/protocol checks pass.
The actual Activity/bridge compile and the same QA APK is 115,091 bytes, SHA-256
`d83a3fa49612cdcb37a7f5cafb5fa513dc15b452fca98a7e53c003b527c70836`.
ADB has no device; no framework pass or WebView replacement is claimed.

## Shared main-document driver (E10)

The real workbench selects `document-driver.js` for the native
`android-main-document-v1` descriptor. It uses the same C/JS dispatcher as the
Windows frame driver, but explicitly reports `frame_scope:main_document` and
`child_frames_unavailable`. It does not manufacture a complete native frame
inventory. Original references use the existing qualified form
`r1|f0|native-document|isolated-document|element`.

The driver supports fixed observation, location, preparation, verification,
option-listing and selection methods. Each request pins native document/epoch;
successful replies must match both. Stale references, unready or replaced
documents, malformed results and original deadline/checkpoint expiry prevent
further work. A timed-out selection retains possible effects and is never
replayed. Pinned native failure receipts carry a refusal code and evaluation
uncertainty; page exception text is not forwarded as a native error.

Shared input now forwards the prepared local document/reference alongside its
native frame chain. The Android bridge requires exactly one original root
chain and the local-frame runtime namespace; old native identity rejects as
`frame_changed`. This supersedes E8's temporary empty-chain private interface.
Subframes, captures/visual snapshots, transfers, native scrolling and hover
remain unfinished. The original first-navigation interception checks and global
Android automation flag are retained. This integration does not enable Agent
operation on either the unsupported current provider or an unqualified provider.

Thirteen new JS tests exercise the production driver and actual shared
dispatcher with synthetic native/interception acknowledgements. They verify
reads/waits and click/fill/key metadata, not Android framework behavior. The
full JS suite passes 213 tests. Actual Java gates pass 59 document, 35 boundary,
18 budget, 125 input, 81 lifecycle, 22 channel and 63 guarded-input assertions.
The actual Activity/bridge compile; the same QA APK is 115,091 bytes, SHA-256
`b06ba67c02d33f2cd2070662be1345428b2445073c685d83133bc1290323fcd9`.
Both device receipts remain unavailable because ADB has no device; the provider
and existing mdo/data remain unchanged. The actual Windows C/JS/WebView2 DOM
keyboard gate passes with the added input metadata, including root/child,
shadow/contenteditable, focus-loss, permission and original-download cases.

## Guarded native scrolling (E11)

The private main-document input bridge now accepts `wheel`. The shared driver
pins the actual rendered element under the original CSS viewport point, then
the isolated guard checks that same element/document, native frame, viewport,
scroll and scale immediately before dispatch. Open shadow roots are inspected;
child-frame hits refuse explicitly. Viewport and element scrolling both use
this pin. The runtime's retained-reference bound also applies to scroll targets.
Android hover, child-frame input, capture/privacy and transfers remain pending.

The native target emits one `ACTION_SCROLL` mouse event with no pressed buttons;
it does not tap to focus or simulate a touch stroke. Public deltas use CSS pixels
with positive right/down, converted by the admitted native scale. Chromium's
Android wheel path uses the Activity theme's `listPreferredItemHeight` physical
pixel factor for both axes, with both tick signs inverted relative to public
CDP deltas. Reviewed primary-source references and hashes are recorded in
`host/android/scroll-protocol.json`; this is not an installed-provider version
gate or a claim of framework compatibility. Failed/unknown conversion refuses
without a DOM or touch fallback. Cancellation and timeout settle the original
action once; late checks cannot dispatch new input.

Verification: 66 Java wheel assertions pass alongside 125 input, 81 lifecycle,
22 channel and 63 deferred-guard assertions. The complete JS suite passes
220/220, including real runtime hit tests and coupled shared-driver routing.
The actual Android Activity and native wheel bridge compile. The same QA APK
is 123,283 bytes, SHA-256
`1de0ef332c0ecb0907470fae09a9806f707f755e3fd11f471270e226baffc31c`.
Its nested scrolling fixture checks both pixel directions, target coordinates,
trusted wheel events and absence of extra clicks when a device is available.
ADB currently has no device: both device gates are unavailable and
`native_wheel_verified` remains false. Overall Android automation stays disabled,
and the current WebView and installed mdo/data are preserved.

The actual Windows C/JS/WebView2 DOM keyboard gate also passes with runtime 18,
including original POST download bytes. The running headless deployment is
healthy and reviewed overlays match. The previously qualified packed Windows
preview predates E11; it has not been refreshed or published in this milestone.

## Reject child-frame hits inside a parent click target (E13)

The Android main-document fence now checks the actual prepared click point with
the isolated runtime's `visualPoint` helper. A containing parent can pass normal
target hit testing while its center lies on an iframe; native clicking that
point would enter an unsupported child document. Such hits now reject as
`android_frames_unavailable` before native input. Missing rendered hits reject
as `input_target_changed`. Ordinary descendants remain valid, including an
inspected open shadow tree; an iframe inside that tree still rejects.

The regression executes the actual runtime and the actual Java fence constant,
first demonstrating that parent preparation succeeds for the problematic point.
Both targeted cases fail before the fix and pass after it. All seven fence
checks and the full 227-test JS suite pass. The actual Activity/bridge compiles;
the same 123,283-byte QA APK now has SHA-256
`1f03c2948f09cec06104dc423576fb01bda0db31b367e1b19748081329a018fa`.
ADB still has no device; the refreshed world and input gate receipts are
unavailable with verification flags false. This is a boundary regression,
not actual Android framework qualification; Agent admission remains disabled
and the user's current WebView is preserved.

## Main-document screenshot preparation (E14)

The shared document driver now prepares visible-viewport and visible-element
capture regions through the original isolated runtime. It checks the original
native/isolated document, known native scale, settled input and cleanup, bounded
region/density/node/mask data, and unique opaque iframe owner references. It
keeps every child-frame mask opaque; this adapter does not enter child worlds
or grant unmasking based on coincident rectangle coordinates.

The returned internal `native_capture` context pins the native document/epoch,
isolated document, CSS viewport and scroll. The shared screenshot executor sends
that context to the native `capture` request and requires its original request
and document identities on reply before decoding any pixels. Post-capture
viewport, density, scroll or opaque owner changes invalidate the image. Before
and after masks are applied by the trusted host canvas before publishing.
Cancellation discards late bytes and cancels the original native capture key;
it does not replay capture. No private context or raw mask owner ID is exposed
in the resulting public screenshot.

Full-page and offscreen captures remain unsupported in this stage. Screenshots
declare child-frame masking and do not issue image-action references, because
Android image actions are unfinished. The actual native draw/encode/cancel
implementation and its framework/privacy qualification are still pending.
The old manual `screenshot` command is not substituted for Browser Use capture.
Overall Android automation remains disabled and the current provider is kept.

Nine new protocol/coupling checks pass, including malformed privacy proofs,
opaque frame changes, native reply replacement and cancelled/late images. The
complete JS suite passes 236/236. The actual Windows C/JS/WebView2 public capture
gate also passes three first-screenshot cases with zero initial failures and
correct visual-reference bindings. These Windows results do not qualify Android
drawing or prove full Android privacy coverage. No new Android/native build is
needed for this shared JS stage. Receipts: `.mdo/E14-document-capture.log`,
`.mdo/E14-js-full.log`, `.mdo/E14-windows-capture.log` and
`.mdo/capture-readiness-result.json`.

## Native capture transaction core (E15)

`MdoBrowserCapture` adds the UI-owned capture transaction which the native
draw/encode adapter will use. It binds the exact native document/navigation,
viewport, scroll and scale snapshot; validates a visible crop; bounds raster
allocation to 4,194,304 pixels/4,096 per side and compressed output to 256 KiB;
and uses the original monotonic deadline. `MdoBrowserCaptureBuffer` enforces the
compressed byte limit before its buffer grows. Both compile with Java target 8.

The transaction waits for the original isolated guard and native visual-ready
acknowledgement before drawing. Its scheduler/target run on the owning UI
thread; an injected worker owns the raster after submission. No native bitmap
codec is called by this core. Cancellation can discard queued work once, but
cannot recycle a raster being encoded. Ambiguous submission, duplicate/late
callbacks, post-callback exceptions, timer failure, reentrant cancellation,
view replacement and lost UI replies cannot replay drawing or encoding.

One controller retains its outstanding native callback after client timeout or
close; only the actual callback or acknowledged exact-view destruction retires
that reservation. A shared bounded budget counts independent controllers.
Destroying the view does not release a worker-owned raster prematurely. A failed
raster release poisons the controller and retains that shared slot until the
runtime resets, rather than claiming cleanup or allowing repeated allocations.
Callbacks retain only a weak controller reference. Returned pixels are dropped
after cancellation and are never persisted by this component.

Run `python tools/android_capture_probe.py` for the production-core contract.
Its 182 assertions include an actual background-thread ownership check, plus
deterministic native/worker callback faults, allocation/encoding/cleanup errors,
resource bounds and replacement isolation. An adversarial foreign-thread ready
callback exposed a lease-state transition before the thread check; it is now
refused before state changes and the original view can drain its reservation.
The failing log is `.mdo/E15-callback-thread-before.log`; the final result is
`.mdo/E15-capture-contract-final.log` and `.mdo/android-capture-result.json`.

This is a tested native transaction component, not an Android bitmap/framework
or APK pass. It is not yet connected to `MdoBrowser.java`: the real
`postVisualStateCallback`/draw target, worker codec, channel reply, lifecycle
hooks, pixel/privacy fixtures and supported-provider qualification are the next
integration steps. Overall automation remains disabled; no WebView, installed
package, Home, signing key or runtime dependency is changed.

## Native visible capture integration (E16)

The actual `MdoBrowser.java` now owns the capture transaction per exact content
WebView, routes `capture`/`capture_cancel`, and reports busy/cleanup/shared-budget
state. Navigation, resize/hide, scale changes, backgrounding, channel replacement,
takeover and stop cancel the original request. Close/destroy acknowledge the
exact destroyed view; a still-running encoder retains its raster until terminal
cleanup. Native input cannot bypass an active or unclean capture reservation.
Cancelling an old request key cannot cancel a new request.

`MdoBrowserNativeCapture` checks original native/isolated document, navigation,
viewport/scroll/density and crop bounds. Its fixed isolated guard has no script
input from a model and no page-world fallback. `MdoBrowserCaptureTarget` waits
for the visual-state callback to return before queueing a draw of the owned
visible attached view into one bounded cropped bitmap. It never captures the
parent window. The process has one encoder thread, one queued task and two
shared capture reservations; cancelled queued tasks are removed by exact identity.
Encoding runs outside the UI thread. These choices follow Android's
[visual-state drawing guarantee](https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,%20android.webkit.WebView.VisualStateCallback))
and [bitmap compression guidance](https://developer.android.com/reference/android/graphics/Bitmap#compress(android.graphics.Bitmap.CompressFormat,%20int,%20java.io.OutputStream)).

Android raw source images stay lossless PNG, bounded to 256 KiB. The trusted
host decodes that source, applies the before/after privacy masks, then encodes
the requested public PNG/JPEG within its original byte budget. Source images
are not persisted. Videos, objects and embedded content stay opaque because
their complete rendered state is not guaranteed by this native path. An initial
fitted viewport is allowed only when native geometry and the full visual viewport
agree; offset/cropped pinch viewports still reject. Runtime 19 carries these
fixed native-viewport/media scan options. Malformed, wrong-document or failed
native replies publish no pixels and preserve bounded failure codes.

A delayed timer-registration fixture also reproduced a deadline bug: already
expired admission could start a new guard and pass the full original timeout.
The watchdog now uses absolute uptime and the guard inherits only remaining
time. The failing log is `.mdo/E16-deadline-before.log`; the actual core passes
194 Java assertions in `.mdo/E16-capture-contract-final.log`. Complete JS
regression passes 243/243. The actual Windows WebView2 gate passes 1,369 checks,
including PNG/JPEG, redaction, crop/full-page, cancel, input, dialogs, frames and
transfers, with no diagnostics or retained upload/download reservations.
Receipts/logs: `.mdo/E16-js-full-final.log`, `.mdo/E16-windows-native.log` and
`.mdo/page-observation-result.json`.

The actual Activity/hooks, capture target/bridge and same tiny QA APK compile
with Java 8, API 36/minimum 26. The APK is 160,147 bytes, SHA-256
`2674a22ed6b02a87fae6cc7d2f7604e6e51b69f219076352a24454499a9b936c`.
`python tools/android_device_probe.py --mode native-capture` selects a local,
network-free crop/PNG/JPEG/cancel pixel fixture using the actual core/target.
It deliberately does not qualify isolated execution, shared C/JS masking or
Agent operation: `guarded_native_capture_verified` and `capture_privacy_verified`
remain false even if that lower-level pixel fixture passes.

ADB currently has no device, so its new receipt is unavailable with all capture
verification fields false. No package is installed and no provider is changed.
Actual clipping/drawing/encoding, privacy coverage and supported-provider full
qualification remain pending. Full-page/offscreen capture, Android image input,
child frames, dialogs/pickers and transfers still need work. Global Browser Use
admission stays disabled; no production release is made.

## Retained native dialog controller (E19)

`MdoBrowserDialogs` owns one modal result for one exact native view and navigation
epoch. It uses opaque IDs and a 120-second absolute uptime deadline. Source URL
and message metadata are bounded; prompt defaults/replies stay private and obey
an 8 KiB UTF-8 bound. No frame identity is inferred from a callback URL.
Confirm/cancel delivery occurs once. A lost native acknowledgement keeps admission
blocked and cannot be replayed; only exact view destruction confirms that cleanup.
Timers retain a weak controller and exact modal ID, so a late callback cannot
cancel a replacement dialog or keep a closed view alive.

The controller follows Android's retained-result contract for
[WebChromeClient dialogs](https://developer.android.com/reference/android/webkit/WebChromeClient#onJsAlert(android.webkit.WebView,%20java.lang.String,%20java.lang.String,%20android.webkit.JsResult))
and [prompt replies](https://developer.android.com/reference/android/webkit/JsPromptResult#confirm(java.lang.String)).
Its Java 8 contract gate passes 83 checks, including delayed/failed timers,
navigation, stale IDs, duplicate callbacks, Unicode bounds, reentrant teardown,
lost acknowledgement and foreign-thread/view refusal. Receipt/log:
`.mdo/android-dialog-result.json` and `.mdo/E19-dialog-contract.log`.
This milestone is the controller only; native WebChromeClient/channel/UI wiring
and actual-device qualification remain pending. It does not enable automation.

## Actual native dialog hooks (E20)

The reviewed `MdoBrowser.java` now installs `MdoBrowserDialogTarget` on each owned
content WebView. Alert/confirm/prompt/before-unload callbacks retain the actual
result through the controller. The existing trusted host UI reads `dialog` list
and responds by opaque ID; defaults stay private and reply types are checked.
Metadata explicitly says `frame_scope:"owned-webview"`; no frame ID is invented.

A modal blocks the isolated document, cancels capture and drains held native
input. Navigation, hiding, stop, host-channel detach, background and exact view
close cancel the original result. Manual takeover retains the dialog so the
user can respond through the trusted UI; an Agent cannot respond during takeover.
An uncertain cleanup blocks new input/capture. Exact view destruction settles
the retained result resources. The host reports dialog capability/open/cleanup
separately from overall Browser Use readiness, which remains false.

The actual pinned Activity, browser hooks and target compile with Java 8/API 36,
minimum API 26. The reused QA APK is 172,435 bytes, SHA-256
`7d89de1dd50d36214f49ed2e065691bb046a3e2bf17252fd353b1bb6d4c1a65a`.
Dialog core 83, document 64 and the existing world/input/lifecycle/channel/wheel
contract checks pass; all 245 JS checks pass. Logs:
`.mdo/E20-dialog-contract.log`, `.mdo/E20-world-contract.log`,
`.mdo/E20-input-contract.log`, `.mdo/E20-js-full.log` and
`.mdo/E20-android-build.log`. Current ADB inventory is empty, so this is compilation
and deterministic contract evidence, not actual dialog/UI/before-unload recovery
qualification. No installation or WebView change occurs. The next framework gate
must distinguish these native dialogs from isolated-world/full Agent support.
