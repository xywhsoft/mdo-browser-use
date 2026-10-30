# Android Browser Use adapter

The Android implementation is in progress. Reviewed overlays extend the pinned
manual WebView with private isolated native-frame execution, guarded native input,
capture and retained dialogs. These compile and have deterministic contract
checks; actual framework and full host-UI qualification remain incomplete.
Frames, transfers and remaining input capabilities still need implementation
or verification. Overall Android Browser Use admission stays disabled, including
on providers that advertise isolated execution worlds. Existing manual browsing
remains available.

Native file choosers also have bounded original-callback retention, listing and
cancellation. A private native staging/URI backend and human document picking
are implemented. E69 adds a native nonce/original-callback/file-lease gate and
E70 adds native assigned-data retention independent of transient input leases.
E71 joins the original native frame/world verifier to fixed private picker
steps; E72 preserves an idle original connection for strictly scoped chooser
metadata while ordinary operations remain fenced. E73 adds an internal UI
coordinator joining the original native callback, file queue and fixed-world
steps. E74 gives each physical native browser an independent file-data owner,
retired on actual view destruction. E75 wires the product target/ticket assignment
route, saved receipts and retained status/cancel/release across input-scope loss.
Product/provider qualification still gates this route; automatic assignment is
not enabled in the main interface on the unqualified Android adapter.
E76a adds a fixed native file-clear worker primitive and explicit verification
of the original empty FileList. E76b joins it to the product upload route with
original operation keys, UI mutation reservations, cancellation and saved
receipts. The ordinary frame reader still refuses that mutation. Its capability
follows the overall qualified adapter, so it stays false in this unqualified APK.
Automatic chooser assignment remains unavailable and these components do not
enable automation. E77 aligns private worker/session/binding/chooser/upload and
kernel-transport deadlines with suspend-inclusive elapsed time; its controlled
sleep-clock tests do not qualify actual device sleep or Doze recovery.

## Complete development host APK (E46/E88)

`dist/mdo-browser-use-dev-android-arm64-v8a.apk` now packages the actual mdo
Activity/service, ARM64 engine/command worker, reviewed browser/chooser bridges
and private upload provider. This is a **complete host with the lite tool
edition**, not a tiny diagnostic Activity and not a production release.

Current APK: 6,436,161 bytes, SHA-256
`77939136f0ba98ebe6446e6e9aa19b969ce8cdfe72c521bd2f9b83c4f753c13f`.
Use the adjacent receipt. It checks the actual DEX classes, exact native/app
payload hashes, ARM64/16 KiB ELF layout, zip alignment, v2/v3 APK signatures and
the compiled private provider attributes. The DEX gate also requires the new
`MdoBrowserCdpFileInput$BeforeClear`, `MdoBrowser$ClearAction` and native
`MdoBrowserCdpSession$ControlReceipt`/`ControlResult` and
`MdoBrowserCdpBinding$Network`, network ledger/slot/result and cooperative
scheduler definitions. No account, Home, QA page or signing
key is included. The app archive is extracted from the verified W4 Windows
artifact and checked against every current reviewed source file; no
application staging copy is created. Android selects its lite edition before
the desktop macro in that platform-neutral source archive.

```powershell
python tools/setup_android_client.py --native
python tools/prepare_android_cdp.py
python tools/build_android_native.py
python tools/build_android_program.py
```

This build needs the current verified Windows program/receipt and paired
portable plugin already in `dist/`. The last command verifies/reuses the native
host, current compiler/source receipts, existing SDK/JDK and persistent QA signer.
It does not download a browser or bundle command-line toolpacks. Import the
paired Browser Use extension JSON separately through mdo's extension installer.

The app uses the existing QA ID `org.xleaves.mdo.browseruse.dev`, its development
certificate and installation serial 1. Installing it replaces the tiny QA app's
launcher while retaining that QA package's data. Installing the tiny probe APK
again switches back to its diagnostic launcher. The user's formal
`org.xleaves.mdo` package/data are separate. No production App Link or native
update handler is registered by this development build.

Build/ABI/manifest checks passed; installed main-UI, provider and browser behavior
are **not yet qualified**. The last ADB inventory has no device and the user has
deferred connecting it. Preserve their current WebView. The overall automation gate remains false
even on providers advertising isolated worlds. Existing framework/automatic
assignment/frame/transfer and broader goal gaps are still open. Evidence:
`.mdo/E64-android-build.log`, `.mdo/E46-device-inventory.json`, the artifact
receipt and `.mdo/E64-python-full.log` (165/167 pass, two existing symlink skips).

## Private local DevTools transport foundation (E47)

The current provider-boundary adapter cannot yet supply all trusted frame and
transfer contracts. A local DevTools backend is being implemented to connect
the existing WebView to the shared Browser Use semantics. It adds no browser,
AndroidX, Node or Playwright runtime. The installed provider is not changed.

The Android [debugging API](https://developer.android.com/develop/ui/views/layout/webapps/debug-chrome-devtools)
affects all application WebViews. Activation policy and installed-provider
qualification are still pending; this stage **does not enable debugging or
automation**. Pin the examined Chromium 138 endpoint/authentication sources in
`host/android/cdp-protocol.json`. No upstream implementation is copied.

`MdoBrowserCdpSocket.c` connects only to this process's abstract Unix endpoint.
Check kernel peer PID and UID before sending any request, never follow a TCP URL
or redirect, and issue only `GET /json/list`. Bound the complete operation by one
10–5,000 ms monotonic deadline, headers by 8 KiB and body by 256 KiB. Reject
ambiguous/truncated HTTP framing and non-JSON media types. Close every owned
descriptor and clear response/header buffers; do not log or persist target URLs.

```powershell
python tools/android_cdp_socket_probe.py
```

The original C transport gate passed 291 real Linux abstract-socket checks under ASan
and UBSan, including wrong-process refusal before request bytes, fragmented
responses, a slow-trickle absolute deadline, oversized/invalid headers/body,
allocation failure, full-allocation clearing and 64 repeated connections with
balanced descriptors. All 71 allocations are freed. It also compiles with the
locked ARM64/API 26 compiler (`.mdo/E47-cdp-socket.log`). These are transport and
compile results, not a real WebView/CDP session, frame or Android device pass.

## Private discovery JNI packaged in the actual host (E48)

The actual ARM64 `libxs.so` now exports the matching private discovery JNI
entry; the complete APK includes `MdoBrowserCdpNative` in its DEX. The Java
facade refuses UI-thread calls and out-of-range timeouts. It accepts no address,
URL or effect command, returns private bytes only to its internal caller and
does not change debugging settings. Owned target routing and the WebSocket/CDP
session layer still need integration before this becomes an automation backend.

JNI clears the native response on success and Java allocation/copy failures,
and returns bounded error codes without payloads. Extend the exact C kernel
gate to 310 checks/74 balanced allocations, using the real JNI ABI declarations
and four Java function-table fault seams. The Android/JVM runtime is not
simulated as a passing device. ASan/UBSan and the actual ARM64 build pass:
`.mdo/E48-cdp-jni.log`, `.mdo/E48-native-build-final.log`.

The native library is 3,539,600 bytes, SHA-256
`88ad2aed743e22defc6706c49606567da3716a672450a1720c08139ac7f2ef9b`.
The full-APK builder verifies its dynamic JNI export as well as the actual
Java descriptor and exact library bytes; all previous package/ABI/manifest/
signature checks still pass. It reuses the N5 app archive and existing QA
signer. `prepare_android_cdp.py` preflights/records reviewed changes in the one
native checkout; unreviewed source or interrupted files are preserved. WSL uses
Windows Git for this checkout's Windows object alternates, without rewriting
them or making another checkout.

## Persistent native-worker sessions (E49/E50)

`MdoBrowserCdpWebSocket.c` adds persistent private connections using the pinned
XRT RFC 6455 primitives. A target ID becomes a fixed page path; no caller URL,
address, redirect or TCP fallback is accepted. The same-process kernel check
precedes every opening request. Each connection has a fresh secure nonce;
reject extensions, subprotocols and ambiguous upgrade/framing responses. Mask
all client frames and validate incoming framing, fragmented text and UTF-8.

Eight slots cover opening, live and draining sessions. Handles never repeat.
One worker owns each channel's I/O; another reader/writer gets BUSY before
touching the stream. Cancellation invalidates the handle and wakes blocked I/O
with shutdown; close/free wait for every borrowed reference. Publish retirement
before releasing worker ownership. Failed sends retire the connection, even
when delivery may be partial; never replay an effect automatically.

Bound outbound text to 256 KiB and inbound messages to 4 MiB. Idle reads return
null to Java without publishing partial data. Keep decoder state across short
polls, but enforce an independent absolute ten-second assembly deadline; fresh
polls, fragments and control frames cannot extend it. Support bounded ping/pong
and close processing. Native buffers are cleared at full allocated capacity.
The caller must also clear Java byte arrays after parsing and keep protocol
content/target lists out of logs, persistent storage and Agent results.

The private `Channel` is AutoCloseable and thread-bound. Allocate its wrapper
before opening so Java OOM cannot lose a native handle. Its close method may
run on the cancelling/UI thread and does not wait for the I/O worker. If a JNI
result allocation/copy fails, retire the session because the dispatcher has
lost a reply/event. JNI send-copy failures clear native copies and close the
channel. Real Android controller/epoch teardown still needs to call this API.

The C gate passes over 1,000 assertions under ASan/UBSan, with 129 balanced
allocations, real ten-second assembly expiry, forced partial-send backpressure,
native/JNI conversion faults, stale/eight draining handles and 64 cancel/read
cycles. The separate discovery gate passes 310 checks. An actual desktop JVM
loads the exact JNI implementation, executes the actual Java facade under
`-Xcheck:jni`, and passes 138 checks/16 more cancellation cycles. Only Android's
Looper identity is doubled there; this is not Android framework/provider proof.
Evidence: `.mdo/E50-jni.log`, `.mdo/android-cdp-socket-result.json`.

The actual ARM64 host packages all five matching JNI exports and the Java
facade/Channel. Library: 3,546,560 bytes, SHA-256
`7f5d050ee5011efc62743cb55f2ddef34b22eabee8d568a691fb3bc29f7f3170`.
Exact pre/post native source/compiler/ABI checks, actual APK payload/DEX,
private manifest, alignment and signatures pass (`.mdo/E50-native-build.log`,
`.mdo/E50-android-build.log`). Reuse the N5 app archive and existing QA signer.
The tiny diagnostic APK remains at its earlier source stamp and is not rebuilt.

This transport does **not** establish that a discovered target belongs to an
owned content WebView. Owned-target/epoch binding, the CDP dispatcher, trusted
frame/transfer integration and debugging activation policy remain pending.
The production command router does not call this private session API yet.
No debugging setting, provider or automation-admission flag is changed. Retain
the deferred phone/current WebView; device/provider qualification remains false.

## Private command/event dispatcher (E51)

`MdoBrowserCdpSession` serializes one outstanding RPC on its native worker.
Interleaved events are delivered inline to the private protocol controller;
that callback cannot reenter I/O. No additional command/completion/event queue
is created. The eventual Android UI controller must dispatch work off the UI
thread and supply a thread-safe ownership/epoch snapshot as its fence.

Allocate monotonically increasing IDs, match both reply ID and child session,
and enforce one total command deadline across encoding, sending, events and
receiving. Bound each RPC to 4,096 incoming packets. Unknown child sessions or
mismatched replies retire the stream; never reconnect or replay a command.
Report possible effects after a send attempt fails. Complete protocol errors
retain stream validity, while public failure text contains only static codes,
the numeric protocol error and an effect flag; provider message/data are omitted.

Explicit trusted-controller admission allows at most 64 active child sessions
and remembers at most 256 IDs over the connection lifetime. Retired IDs cannot
be rebound. Drain known retired-child traffic without exposing it to a new
owner or disrupting a different active operation. A detach of the child being
operated on ends that operation instead of silently retargeting it. Ownership
loss/cancellation also clears late byte arrays before any result/event delivery.

`MdoBrowserCdpJson` validates bounded strict JSON through the pinned XRT reader
before using Android's existing platform JSON objects. Reject duplicate keys at
every depth, comments/trailing input, ambiguous IDs/envelopes, invalid session
tokens and non-finite numbers. Limit depth to 64, values to 65,536 and container
items to 8,192. Outgoing traversal also bounds nodes/characters and rejects
cycles/unknown Java value types before stringification, avoiding unbounded
serialization or an arbitrary object's `toString` call. Clear native validation
copies on parse/allocation/JNI-copy errors; command routing still owns the
original Java array and clears it on every exit.

The desktop JVM gate compiles six small unmodified, hash-pinned
[AOSP API-26 JSON sources](https://android.googlesource.com/platform/libcore/+/android-8.0.0_r1/json/src/main/java/org/json/)
only as test references. They retain their Apache-2.0 headers in the one SDK
cache; they are never packaged or used as an Android runtime dependency.
The real JNI/local kernel peer gate passes 4,303 dispatcher assertions, including
seven native transport sessions with **synthetic CDP responses**, plus timeout,
event-flood, duplicate JSON, reentrancy, stale-child, owner-loss and failure
cases. The separate native gate passes 1,141 checks/195 balanced transport-copy
allocations under ASan/UBSan, including strict-reader/JNI faults. Earlier
310-check discovery and 138-check real-JVM worker gates also pass. These do not
qualify an installed Chromium provider (`.mdo/E51-dispatcher.log`).

The current E77 ARM64 library is 3,547,104 bytes, SHA-256
`af06385599860d01df28e3c20c052c06e7006d020f408ce502e87487b06536dc`.
The complete development APK checks all six JNI exports and the SDK dexdump's
actual class definitions, including the private Channel/codec/dispatcher.
Class-name references cannot stand in for missing definitions. Reject bundled
`org.json`, AndroidX or Chromium definitions while allowing platform references.
The actual package passes exact payload, manifest, ABI, alignment/signature and
no-extra-runtime gates (`.mdo/E51-native-build.log`, `.mdo/E51-android-build.log`).
Full Python regression passes 162/164 with two existing symlink skips.

Owned content-WebView target/epoch binding, UI/host command routing, trusted
frames/transfers, activation policy and actual device/provider verification
remain pending. The production router does not call these private sessions.
Keep debugging settings/current WebView unchanged and Android automation false.

## Native-view binding core and UI adapter (E52)

`MdoBrowserCdpView` adapts public Handler/WebView calls to the private binding
core. A future UI lifecycle registry must retain **one adapter per exact native
view/document generation**, with its original host-channel/epoch predicate, and
close it before navigation, teardown, takeover or any admission loss. Protocol
workers read only the adapter's atomic/weak lease. They never query UI objects.
This registry/command-router integration is still pending; these classes alone
do not enable Android Browser Use.

The native UI writes a fresh random lexical identifier and independent random
primitive value in the exact primary view. Both use 128 random bits. The fixed
housekeeping expression calls no page function, creates no global-object/DOM
property and changes no URL/navigation. Its fixed source label contains neither
random value. The original native callback must confirm the expected primitive
under the original ownership predicate.

Discovery retains only bounded page IDs. The returned titles, URLs, geometry and
debugger URLs do not establish identity and never become connection addresses.
Probe at most 64 candidates sequentially over the same-PID transport. Before a
match, use only Runtime context metadata, the engine's root frame and native
global lexical-name enumeration. The random identifier is not sent in an
expression to a nonmatching candidate. This lookup uses the V8 primitive pinned
in `cdp-protocol.json`; it does not enable Debugger or pause candidate pages.

Require a root default context with a unique ID. After finding the identifier
in engine metadata, first run a harmless negative unique-context probe and
require the exact known static rejection tag. Then confirm a harmless expression
with the real unique ID before querying the primitive marker. A provider that
ignores the unique-ID parameter cannot silently fall back to its current
default document. The marker read requests side-effect checking. Successful
binding clears the marker value through its proven unique context and checks
the root frame **and loader** again before publishing a binding. Context loss,
document replacement, wrong value or uncertain cleanup cannot grant admission.

Failure cleanup is one bounded attempt on an already proven unique context
only. Never send cleanup to a default UI document or resend uncertain cleanup.
If the context cannot be proven or the channel is lost, the small private value
may remain until document replacement. JavaScript lexical slots cannot be
deleted by normal code, so empty names also remain; compiled source may remain
under engine GC control.
Bound declarations to 16 per native document rather than reloading a page or
growing these slots without limit. Do not reset that counter by recreating an
adapter in the same document. Keep at most 16 queued/retained native callbacks
across adapters. A provider may retain its callback even after throwing or
after cancellation: charge the weak callback slot until actual completion or
actual callback reclamation. Retained callbacks hold only a small ticket and a
weak owner, and cannot admit a late result into a replacement view. Failed
cleanup is not reported as confirmed cleanup.

Binding ownership moves atomically from the provisional peer to the completed
binding. UI invalidation closes that binding, clears the forwarded controller
reference and wakes I/O. Private errors distinguish missing protocol primitives
from absent targets; pre-match metadata failures can skip an unavailable
candidate. Never retry a proof failure against another target or replay a user
operation. The overall binding budget is 100–15,000 ms, with each native phase
at most 5,000 ms.

The actual JVM/JNI strict reader, adapter/marker/binder/dispatcher pass 474 checks
with **doubled Android UI calls and synthetic protocol peers**. Cover a wrong
candidate with no identifier exposure, unavailable candidates, ignored unique
IDs, wrong values, missing APIs/unique context, context/loader replacement,
ownership loss, queued cancellation, late callbacks, retained-then-throw faults,
callback quota and declaration exhaustion. Earlier native/worker/dispatcher
gates also pass (`.mdo/E52-binding.log`). These prove the core state machine and
JNI reader, not actual WebView execution or provider binding.

The full APK compiles the real public-framework adapter against the locked
Android SDK and verifies actual Marker/View/Binding DEX definitions. Fake
`android.*`, JSON, AndroidX and Chromium class definitions cannot ship. The
six existing native exports/library, N5 app archive and QA signer are reused;
no native C rebuild is needed. Actual package/ABI/payload/manifest/alignment/
signature checks pass (`.mdo/E52-android-build.log`); full Python regression
passes 162/164 with two existing skips. Device/provider/automation flags stay
false. Next integrate the UI ownership registry and asynchronous host routing,
then isolated frame worlds/transfers, activation/profile policy and real-device
verification while preserving the current WebView and deferred phone.

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

## Preserve the original world when navigation is cancelled (E21)

The old host sequence retired the isolated peer before calling the native
navigation API. A contract reproduction of that sequence leaves the unchanged
page in `world_not_ready` after cancelling before-unload, even after a loaded
notification. Preserve `.mdo/E21-navigation-before.log` as the failing evidence.

Typed navigation now blocks new execution and cancels outstanding work without
retiring the original peer. Only actual `onPageStarted` retires it and advances
the native epoch. A confirmed before-unload cancellation clears only the same
view/epoch's pending request; the original DOM/peer identity becomes usable
again after the modal blocker clears. An early replacement peer cannot become
usable through cancellation and remains fenced until its actual native start.
Stop, synchronous failure and main-frame/TLS errors settle the request barrier;
new navigation cannot bypass a still-pending request or uncertain modal cleanup.
An empty back/forward history does not invalidate the world.

The actual document gate passes 81 checks, native-budget 18, dialog 83 and the
existing input/lifecycle gates (including 85 lifecycle checks). The 17 targeted
JS document/dispatch checks pass. The actual pinned Activity/browser compiles in
the same SDK; the updated tiny QA APK is 172,435 bytes, SHA-256
`e361fa79ccb5e18b010ca34cf78cdf111550cccc8562a09ac30918396f0dca50`.
Logs: `.mdo/E21-world-contract.log`, `.mdo/E21-dialog-contract.log`,
`.mdo/E21-input-contract.log`, `.mdo/E21-js-document.log` and
`.mdo/E21-android-build.log`. These are deterministic callback-order and compile
gates. Actual framework before-unload/host-UI qualification is still pending;
Android automation stays disabled and no provider change is requested.

## Native dialog framework fixture (E22)

`python tools/android_device_probe.py --mode native-dialog` runs the actual
`MdoBrowserDialogTarget` and controller on a local, no-network WebView fixture.
It covers alert, confirm accept/reject, prompt default/empty/Unicode replies,
stale modal IDs and close cancellation. A native touchscreen gesture activates
the page before an `about:blank` navigation requests before-unload; cancellation
must resume the original DOM/URL/epoch with balanced modal blockers. The fixture
uses page-world script only to create/read synthetic test data; this is not a
production execution fallback. Its global deadline, background refusal and
exact-view teardown remain bounded.

The gate records `native_dialog_verified` and `before_unload_framework_verified`
separately. `host_dialog_routing_verified`, world/document qualification and
overall automation remain false even on a native-only pass. The actual pinned
Activity, hooks and fixture compile in the existing SDK. The reused QA APK is
188,819 bytes, SHA-256
`17f7dd8241ef590de5365d897c737c3a0e8b17922601d885723d548d7d1fd874`.
Build log: `.mdo/E22-android-build.log`. Current ADB has no device; the receipt
`.mdo/android-device-dialog-result.json` is unavailable with every verification
flag false, and `.mdo/E22-device-dialog.log` preserves that result. No install,
system-provider change or full Android qualification is claimed.

## Retire late native dialog callbacks after exact teardown (E23)

The deterministic callback gate reproduced a cleanup bug in the actual
WebChromeClient target/controller: after exact view destruction, a fresh late
callback still called native `cancel`; a thrown acknowledgement could poison
cleanup that had already completed. `.mdo/E23-dialog-target-before.log` preserves
the failing assertion. Ignore callbacks after exact teardown, and do not let a
rejected result's reentrant teardown exception undo that acknowledgement. Closed
but still-native views continue their necessary result cancellation.

`android_dialog_probe.py` now also compiles/runs the actual target against small
framework signature fixtures in its existing QA output. Its 43 callback checks
cover owned/foreign views, result subtypes, prompts/defaults/Unicode, original
before-unload epochs, duplicate/late callbacks, timer admission/expiry and lost
acknowledgement. The existing 83 controller checks still pass. This is callback
fault evidence, not execution of Android framework classes. The separate actual
SDK/Activity compile remains required and passes; the tiny QA APK is 188,819 bytes,
SHA-256 `ddb803dbbb733082cced434a525a015177932c24df4704534823a486cd069b0e`.
Logs: `.mdo/E23-dialog-target-after.log`, `.mdo/E23-android-build.log` and
`.mdo/E23-device-dialog.log`. The current actual-device gate remains unavailable
with all verification flags false. Keep Android automation disabled and preserve
the installed provider; full host UI/platform qualification remains pending.

## Allocation-failure cleanup and shared admission (E24)

Deterministic resource faults reproduced a timer-allocation OOM escaping native
dialog admission and leaving the modal retained without a timer. They also show
that the shared dispatcher previously prepared a new page action despite an
explicit native dialog-cleanup failure. Before logs:
`.mdo/E24-dialog-oom-before.log` and `.mdo/E24-dialog-guard-before.log`.

Handle OOM at liveness/metadata/timer/blocker/native-result boundaries. Rejected
or unscheduled results are cancelled once; native acknowledgement loss remains
uncertain until exact view teardown. Timer removal failures leave weak exact-ID
callbacks inert. One allocation-free result tombstone protects reentrant cleanup
before optional recent-history allocation; tracking failure blocks further
admission. Exact teardown clears the retained metadata.

The shared C/JS dispatcher now refuses new effects/captures before document
preparation when native dialog cleanup is unconfirmed. Read-only observation
and existing upload-release cleanup remain possible. The actual core passes 95
checks and native target 43; complete JS regression passes 251. This is injected
allocation-failure evidence, not whole-process heap-exhaustion or Android
framework qualification. The actual Activity/hooks compile with Java 8/API 36,
minimum 26. Current tiny QA APK: 188,819 bytes, SHA-256
`97c667f18ed6765dc8905b2c4534cd5f59757c4f19b3fde16e59fcc3d9813a74`.
Logs: `.mdo/E24-dialog-contract-final.log`, `.mdo/E24-js-full.log` and
`.mdo/E24-android-build.log`. The device gate is still unavailable with all flags
false in `.mdo/E24-device-dialog.log`. Keep the current provider and disabled
global Android automation admission; remaining qualification stays pending.

## Callback admission before response allocation (E25)

The actual WebChromeClient target can fail while reading its original navigation
or allocating the response closure, before the controller owns the result. The
deterministic gate reproduced that escaped OOM in
`.mdo/E25-native-admission-before.log`. The fallback now claims the original result
through the allocation-free tombstone and directly cancels once. It cannot borrow
a replacement epoch, replay a settled/active native result or touch an exactly
destroyed view. An unproven admission requires exact teardown before new work;
reentrant teardown remains acknowledged even when native cancellation throws.

All 95 core and 68 actual-target callback checks pass in
`.mdo/E25-native-admission-after.log`. Actual SDK Activity/hooks compile separately;
the reused QA APK is 188,819 bytes, SHA-256
`2a13a71d8e181e81c69de60c70beaf018f921f3851900ee6607c87b080aea039`.
See `.mdo/E25-android-build.log`. These are fault-injection/compile checks; the
current device gate is unavailable with every verification flag false in
`.mdo/E25-device-dialog.log`. Keep the current provider and disabled overall
Android automation; actual framework and full host-UI qualification remain open.

## Retained input allocation faults after admission (E26)

`.mdo/E26-native-input-oom-before.log` reproduces an OOM escaping native dispatch
after its transaction has begun. Post-admission document/guard/dispatch/timer
allocation faults now settle the original transaction, releasing held touch/key
state once and preserving effect uncertainty. Failed cleanup poisons further
admission; unremoved callbacks stay inert. Result-allocation or completion loss
does not replay a response, and cancels any reentrantly admitted work before it
can send input. The pre-effect admission OOM contract remains unchanged.

The actual core passes 177 input, 85 lifecycle, 22 channel, 63 guard and 66 wheel
checks in `.mdo/E26-native-input-oom-after.log`. These are bounded injected faults,
not a claim about process-wide heap exhaustion. Actual SDK Activity/hooks compile
in `.mdo/E26-android-build.log`. The current reused tiny QA APK is 188,819 bytes,
SHA-256 `d847e16d857669b18ef15838008248ad86c427ea45f581815e737433054a13db`.
The native-input device gate remains unavailable, all flags false, as recorded
in `.mdo/E26-device-input.log`. Keep the installed provider and disabled overall
automation. Framework, supported-provider and full host-UI qualification remain
pending; Windows F9 is unchanged by this Android-only milestone.

## Guarded main-document native hover (E27)

The shared C/JS action path now prepares and pins an Android `hover` target and
sends one private native `move`. An explicit `hover_input` capability is required
before preparation; the independent overall readiness gate remains disabled.
Native input revalidates the original root document/ref, unobscured CSS point,
scale/viewport/scroll and absence of a child-frame hit before dispatching one
zero-button mouse `ACTION_HOVER_MOVE` to the owned WebView. It adds no touch,
focus or click and retains no held button. Pointer position persists normally.

Reference sources and hashes are in
[`hover-protocol.json`](../host/android/hover-protocol.json). AOSP dispatch routes
hover through the hover callback; the pinned Chromium provider forwards a move
to its native mouse path while ignoring enter/exit as mouse events. Source review
does not certify an installed provider or substitute for trusted-event testing.

The production sequencer passes 82 new hover assertions and the existing 413
input/lifecycle/channel/guard/wheel assertions in `.mdo/E27-hover-contract.log`.
All 255 sequential JS checks pass (`.mdo/E27-js-full.log`). The actual SDK Activity,
hooks and extended native input fixture compile (`.mdo/E27-android-build.log`).
Current tiny QA APK: 192,915 bytes, SHA-256
`8eb85f17d39053228f713f0750c90c08d75d75e66761d102efa149fc893c0a45`.

The actual-framework fixture requires exactly one trusted move with matching
CSS coordinates/target, zero buttons, active CSS hover styling, unchanged DOM
focus and no additional touch/click. It records `native_hover_verified`
separately and uses page-world script only to create/read synthetic fixture data.
Current ADB has no authorized device; `.mdo/E27-device-input.log` and its receipt
remain unavailable with `native_hover_verified:false` and every automation/
framework flag false. Preserve the current provider and disabled overall
admission. This is implementation/compile evidence, not a framework hover pass.

### Native popup transport (E30; framework qualification outstanding)

Manual browser views now enable multiple windows through the original Android
`WebViewTransport`. The shared C/JS popup admission API is unchanged. Android's
callback provides no trustworthy requested URL or source frame: native discovery
reports `url:"about:blank", url_known:false`. Creation ignores a caller URL for
that ticket and never performs `loadUrl` to reconstruct a popup; this preserves
the original contents/POST/document-write semantics pending framework proof.

There is one pending request per exact opener, eight globally and eight owned
views. Pending requests expire after 15s; accepted transport dispatch has a 5s
same-queue barrier deadline. A barrier is callback-consumption evidence only.
Dispatch or acknowledgement loss cannot release native budget early or replay
the original message. Navigation, hiding, pause, detach, takeover and close
invalidate the source; only exact view destruction resolves remaining native
uncertainty. `onCloseWindow` uses the existing browser/panel close reconciliation.

Run `python tools/android_popup_probe.py` for production Java controller/Chrome
transport checks with deterministic queue doubles. They currently pass 103
assertions; the actual Activity and SDK compile as well. Neither is a framework
pass. The initial native popup navigation has no public pre-adoption URL gate,
and replacing the child's native contents has not qualified isolated-world
installation. Popup views therefore report `popup_world_unqualified`, and
overall Android Browser Use automation stays unavailable. Preserve the current
installed WebView. The reviewed contracts/references are in
`host/android/popup-protocol.json`.

The tiny SDK client also has a `native-popup` mode. After building, run
`python tools/android_device_probe.py --mode native-popup` with the authorized
device. This does not need an isolated execution world or a provider upgrade.
It uses only synthetic loopback-origin data: write contents into the original
pending `window.open`, adopt an attached unnavigated child, verify the exact
contents and opener, close through the native callback, reject another retained
request, then cancel a pending popup by actually navigating the opener. It
checks original-ticket rejection, resource release and callback counts.

The mounted 1px child qualifies transport/content only, not pixels, Agent input,
host panel layout/routing or an isolated popup world. Those flags stay separate.
The actual Activity and fixture compile; QA APK is now 225,683 bytes, SHA-256
`f633c927fb3df4107a83df7bf29f1fe45f7611c1eda10f9c519f2533de22e431`.
Current ADB has no device: `.mdo/android-device-popup-result.json` records
`android_device_unavailable` and all popup/framework/automation flags false.

## Retained native file chooser (E32)

`MdoBrowserChoosers` owns one original native callback per exact UI-thread view.
`MdoBrowserChooserTarget` uses public `onShowFileChooser`; interception is off by
default, preserving normal manual browsing. When explicitly armed, list/cancel
uses an opaque original identity, navigation epoch and immutable metadata:
mode/multiple/directory/capture, at most 16 accept entries of 256 UTF-8 bytes.
There is a 120s monotonic lease. Cancelling sends only `null` to the original
callback. No file URI, content URI, filename hint or script-generated path can
grant access through this controller.

The native host exposes `chooser` actions `set_interception`, `list` and `cancel`.
It reports `assignment_supported:false` and
`frame_scope:"owned-webview-unidentified-frame"`; the callback identifies the
owned WebView but does not prove its originating subframe. Other chooser actions
are explicitly unsupported. The public C/JS Android automation gate stays off.

Pending callbacks block the document, native input and capture. Lifecycle loss
or disarm cancels once. Retired live callbacks are remembered by identity with
bounded retention. A native response exception retains possible-effect
uncertainty without replay, and exact view destruction is required to resolve
unclean state. Readiness refreshes expiry before evaluating cleanup and remains
false while a reentrant response is still returning.

Run `python tools/android_chooser_probe.py` for 471 controller and 34 Chrome
target contract checks. They include metadata/Unicode limits, duplicates, wrong
identities/views/threads, timer/result/OOM faults, callback retention capacity,
ownership/navigation and reentrant teardown. These use framework doubles, not
the installed provider. Reviewed references and limits are in
`host/android/chooser-protocol.json`. Actual production Activity/hooks compile
against API 36. E32's tiny QA APK is 237,971 bytes, SHA-256
`583d92abd26845ae06e5271f3906e02a8d81e70fbea9c15de3b8760a68c495df`.
Framework, source-frame identity and URI-transfer checks remain outstanding.
Preserve the installed WebView and disabled overall automation admission.

The tiny QA client also provides `native-chooser` (E33):

```powershell
python tools/build_android_client.py
python tools/android_device_probe.py --mode native-chooser
```

It exercises the original Chrome callback using trusted native taps on a fixed
synthetic input: cancel one single-file chooser, disarm a multiple-file chooser,
then navigate with another pending chooser. It requires fresh identities,
matching epochs/modes, trusted click counts, empty selections and balanced
cleanup. No URI is granted, no user file is selected, and no system picker is
opened. Its page-world reads are restricted to fixture verification.

This callback gate does not require isolated execution worlds or a provider
upgrade. Its framework result is separate from host UI routing, source-frame
identity, assignment and overall automation. The SDK compile passes; current
QA APK is 250,259 bytes, SHA-256
`ab061d51d6b53a43893578e7e5c396a4447454783e00786de35ff0cefadf812f`.
Current ADB has no device, so `.mdo/android-device-chooser-result.json` records
`android_device_unavailable` with all qualification flags false. Keep the current
installed WebView and existing mdo data.

## Native upload file primitives (E34)

`MdoBrowserUploadFiles` supplies worker-confined staging using Android API 26's
public `SecureDirectoryStream` interface. Its native caller must provide an
app-private cache directory. It does not resolve SDK workspace paths, accept a
wire path, publish a URI or claim a chooser/frame binding. The pending native
runtime must attest cache authority before exposing any upload capability.

Each file uses an opaque exclusive name inside the pinned directory, with
no-follow relative access. The root's original identity must remain current
before create/write/read/seal. A foreign leaf is preserved rather than deleted;
cleanup failure retains its slot and byte reservation. The primitive limits
files to 32 MiB, stores to 128 MiB/16 files and chunks/ranges to 64 KiB. Sequential
writes are verified against complete SHA-256 before sealing into a read-only
channel. A separate full revalidation is required before future native grants.

Run `python tools/android_upload_probe.py` for the 63 real-filesystem primitive
checks. This uses the project's existing WSL JDK and QA directory. WSL/NTFS
cannot perform the open-child directory rename case; that flag remains false.
It also does not verify Android app-cache privacy, native ticket lifecycle,
URI grants, website file consumption or framework integration. The current
primitive is compiled into the reviewed Activity/QA build, with all those runtime
capabilities still disabled. References/limits are recorded in
`host/android/upload-protocol.json`. No provider update or dependency is needed.

E34's tiny QA APK is 262,547 bytes, SHA-256
`ca4194bf99c954af593a2d2c1f885eb143b69e0107dbaf102ae2e6bc6efb9e1e`.
It is not a complete mdo Android client. The remaining native transfer bridge,
provider handoff and actual-device checks stay in the active development goal.

## Native upload staging transactions (E35)

The reviewed host now routes `upload` actions `begin`, `chunk`, `seal`, `status`,
`cancel` and `release` through one persistent worker. Only the native application
Context supplies cache authority: canonical app cache/data paths, the process
UID, a dedicated private directory and an exclusive locked owner marker. The
worker holds no Activity or WebView. Unknown directory contents, invalid owner
markers and orphaned staging are preserved; orphan recovery is still pending.

Each original WebView/navigation owns a non-serializable cancellation scope.
The native response also retains its original main-document channel lease;
late callbacks cannot attach to a replacement view or connection with reused
IDs. Navigation, hiding, takeover, detach, close and destruction cancel scopes.
Expiry cleanup runs independently of UI callbacks. A maximum of eight queued
requests and nine unfinished deliveries bounds memory even if the UI stalls.

The native commands accept only strict metadata and canonical base64 chunks.
They reject source paths, frame targets, chooser identities and assignment
actions. A manifest contains 1–16 files, at most 64 MiB total, with safe display
names, exact sizes and SHA-256 hashes. The original deadline is at most 120s;
repeating `begin` with the same key/manifest does not extend it. Identical
last-chunk retries return metadata without rewriting bytes. A 64-entry ledger
retains terminal operation identities until the original scope is cancelled.
Writes/seals with uncertain outcomes cannot be resumed as successful uploads.

Responses contain only ticket/file metadata, byte counts and cleanup state.
They report `staged_only:true`, `assignment_supported:false` and no possible
webpage effect. Cache paths and file bytes are never returned. `release`
requires explicit confirmation; expiry/cancel/lifecycle loss also release
staging. The existing public SDK gate still refuses Android upload before
reading task files. Private cache authority, URI grants, source-frame binding
and complete Android automation are separate, currently false qualifications.

Verification:

- `python tools/android_upload_probe.py`: 63 real-filesystem primitive checks.
- `python tools/android_upload_queue_probe.py`: 89 real filesystem/worker
  checks, including lease exclusion, scope/expiry cleanup, ticket reuse,
  manifest/hash failures and bounded undelivered UI replies.
- `python tools/android_upload_ui_probe.py`: 37 production-fragment checks
  with UI/JSON/runtime-factory doubles. The file store and worker are real;
  this does not verify the Android framework or native Context authority.
- The actual pinned Activity and reviewed hooks compile against API 36 with
  Java 8/min API 26. E35's tiny QA APK is 283,027 bytes, SHA-256
  `34962643858912852ee6479e25adf076e42aeef0a5363304338dd51605a405b4`.

Evidence is in `.mdo/E35-upload-files.log`, `.mdo/E35-upload-queue.log`,
`.mdo/E35-upload-ui.log` and `.mdo/E35-android-build.log`. This APK is a QA client,
not full mdo, and compilation is not a device pass. Preserve the current
WebView. Remaining work includes orphan recovery, native URI/provider handoff,
verified source/chooser/frame ownership and actual-device qualification.

## Installed-runtime upload staging fixture (E36)

The tiny QA client provides a `native-upload` mode:

```powershell
python tools/build_android_client.py
python tools/android_device_probe.py --mode native-upload
```

It calls the production native application-Context cache factory and worker
with fixed synthetic bytes only. No task file, chooser, system picker, content
URI, webpage upload or network endpoint is used. It checks a Unicode name,
zero-byte file, a full 64 KiB chunk plus tail, begin/last-chunk deduplication,
conflicting chunks, foreign scopes, sealing, explicit release, retired keys,
autonomous scope/expiry cleanup and a rejected full-hash mismatch. Final file,
byte and unfinished UI-delivery counts must be zero. Backgrounding or closing
the Activity cancels its scopes rather than continuing the check silently.

Successful real-device execution would qualify native private-cache staging
only. It cannot qualify host command routing, chooser source-frame identity,
URI assignment or overall Android automation. Missing devices and every other
preflight state retain distinct false flags. The fixture preserves stable
failure phase/cause codes; arbitrary platform exception messages and cache
paths do not enter its receipt.

`python tools/android_upload_fixture_probe.py` passes 44 checks: the device
flow's 42 checks using UI/Context doubles with real files/worker, plus two
negative factory cases. These confirm unsupported secure-directory operations
and generic I/O failures produce failed receipts, without exposing raw platform
messages. This is not native Context authority or Android framework evidence.
Log: `.mdo/E36-upload-fixture.log`.

The pinned actual Activity/hooks and fixture compile. Current tiny QA APK:
299,411 bytes, SHA-256
`261c5cbbe54292a2a0f53f35230613ff6e1344781e66b477b508eef3712c8ca8`.
Its exact source/APK receipts match. Logs: `.mdo/E36-android-build.log` and
`.mdo/E36-device-upload.log`. The device gate is currently unavailable because
ADB reports no authorized phone; `.mdo/android-device-upload-result.json`
records `android_device_unavailable` and all qualification flags false. Keep
the installed WebView and existing mdo apps/data. This is still a small QA app,
not a complete Android mdo deliverable.

## Cache owner replacement fence (E37)

An actual filesystem regression reproduced two live stores holding locks on
different owner-marker inodes after the original marker was moved/replaced.
The older store could still create a file. The cache lease now retains its
original marker identity; every admitted create/write/seal/verify/read also
requires the marker to remain that exact regular file and its original lock
and channel to remain valid. Missing/replaced/linked markers produce
`upload_cache_owner_changed` before further I/O. Cleanup can still release
the original owned leaves through the original pinned directory.

The worker/filesystem gate now passes 99 checks, including replacement by a
second live owner, marker removal while receiving/sealed files exist, refused
reads/writes and successful exact-leaf cleanup. Evidence:
`.mdo/E37-owner-before.log` and `.mdo/E37-owner-after-final.log`. The initial
post-fix fixture checked renamed-marker metadata while its old handle remained
open on WSL/NTFS; that check failed. The final preservation check runs after
closing that handle. Its evidence is kept in `.mdo/E37-owner-after.log`.

The 63 primitive, 37 production-fragment and 44 device-fixture-double checks
also pass. The actual pinned Activity/hooks compile. E37's tiny QA APK is
299,411 bytes, SHA-256
`65a9e6489bbc63ca9b9926a63f9e43408fcc265e893b2f21432ddf022b902203`.
Logs: `.mdo/E37-upload-files.log`, `.mdo/E37-upload-ui.log`,
`.mdo/E37-upload-fixture.log`, `.mdo/E37-android-build.log`. None is an actual
Android cache-authority or URI-assignment qualification. Keep the installed
WebView and the overall automation gate off. Durable orphan cleanup remains
the next upload recovery work; this fence does not claim to implement it.

## Recover recorded ungranted staging after process exit (E38)

New cache-managed leaves carry a fixed 1 KiB record before their payload. Force
that complete record before accepting any source byte. It binds the original
cache directory/file identities, opaque leaf name, expected payload size and
SHA-256. A SHA-256 checksum detects damaged/torn metadata; it is not a MAC or
authentication boundary. Native Context/UID/private-directory authority and the
cache lease remain required. The reviewed persistent key encoding is limited
to Android/OpenJDK's `sun.nio.fs.UnixFileKey`; other providers fail closed.
See the pinned platform source in `host/android/upload-protocol.json`.

Startup under the exclusive owner lease first validates the complete inventory:
at most 16 leaves, 128 MiB declared payload plus at most 16 KiB of records.
Before each unlink it rechecks the original owner marker and exact leaf record,
identity and physical length through the pinned directory. Fully recorded
partial, complete and zero-byte ungranted files can be removed. Interrupted
cleanup is repeatable. It never resumes an old operation, restores a ticket,
replays a webpage effect or follows a cache symlink.

Unknown entries, changed identities/names/directories, malformed records, legacy
raw leaves and records interrupted before their complete header remain intact
with an explicit recovery error. Incomplete headers contain no accepted source
bytes. Reclaiming these unproved entries still requires separate explicit
recovery; this implementation does not broaden deletion to guess ownership.

`Leaf.size/read/seal/verify` continue to operate on payload only. Sealing and
pre-grant revalidation also check the original record. No raw cache path may be
passed as an upload URI because it contains private metadata before the payload.
Future provider handoff must use the bounded payload API and establish retained
ownership before granting access; these ungranted records cannot be reused for
published URIs. Assignment and URI grants are still unavailable.

The worker exposes metadata-only `upload_staging_recovered_files` and
`upload_staging_recovered_bytes`. Bytes count actual removed payload bytes,
excluding record overhead and unwritten declared capacity. No source file data,
cache path, record or persistent file identifier enters those receipts.

`python tools/android_upload_recovery_probe.py` passes 154 checks, with 12 actual
JVM `halt` exits and a separately live child holding the cache lease. It checks
worker startup recovery followed by a fresh ticket, interrupted cleanup, maximum
leaf count, bounded record parsing, checksum-valid invalid versions/lengths,
payload-only reads, live metadata corruption and preservation of replacements,
links, copied/renamed entries, changed directories, unknown/legacy/partial data.
Evidence: `.mdo/E38-recovery-final.log`. This is real filesystem/process/worker
evidence, not Android framework, native app privacy or actual device evidence.

All 63 primitive, 99 queue, 37 production-fragment and 44 device-fixture-double
checks also pass. Actual pinned Activity/hooks and tiny QA APK compile:
307,603 bytes, SHA-256
`8b53472aeeb6af85077a5fc5d6b72d89a82497adbf913bca4e2a23f178f7849f`.
Logs: `.mdo/E38-upload-files.log`, `.mdo/E38-upload-queue.log`,
`.mdo/E38-upload-ui.log`, `.mdo/E38-upload-fixture.log` and
`.mdo/E38-android-build.log`. Preserve the current WebView. Native cache authority,
source/chooser/frame ownership, provider URI handoff, real-device qualification
and full Android automation stay separate and unqualified.

## Native sealed read leases and bounded worker replies (E39)

Add a native-only `ReadLease` after full sealed-payload/record revalidation. Its
authority is the original store object identity, original native cancellation
scope, ticket/file and original deadline. The lease owns no Activity, WebView,
thread or file descriptor. Repeating preparation returns the same live object;
revocation cannot resurrect it or extend its deadline. The object is never
serialized. The public JSON bridge still accepts only the six staging actions
and explicitly rejects both `access` and `read`.

Only the original worker can read payload ranges through that lease. Check
scope/deadline before and after I/O; revoke leases before releasing their files,
even if cleanup later fails. Read errors retain the existing exact-file cleanup
and reservation rules. Bad ranges do not poison a valid lease, and original
store identity prevents cross-instance reads. Source hashes are revalidated
before issuing the lease, including after a same-size cache mutation.

Native background callers can request at most 64 KiB with one monotonic wait
budget of at most five seconds. The inbox stays bounded to eight shared tasks;
four unfinished native reads are counted independently of the nine UI replies.
Timeout/interruption abandons the result but keeps its reservation until the
actual worker retires it. Late results are discarded. Body replies do not depend
on UI posting or UI receipt credits, so a stalled UI cannot create an unbounded
read-result inbox. Worker self-wait is rejected. The actual Android runtime also
binds a Looper predicate that rejects blocking on its main thread.

This is the read backend for the planned seekable, read-only native proxy
descriptor/ContentProvider adapter. That adapter is not implemented here. The
platform requires exact requested bytes unless EOF; it must assemble bounded
ranges and enforce a whole-callback deadline. Closing a future FD session must
not implicitly revoke a still-live shared file lease. URI publication,
original chooser/source-frame proof and assignment remain unavailable. See
[the proxy callback contract](https://developer.android.com/reference/android/os/ProxyFileDescriptorCallback)
and [StorageManager's proxy descriptor API](https://developer.android.com/reference/android/os/storage/StorageManager#openProxyFileDescriptor(int,%20android.os.ProxyFileDescriptorCallback,%20android.os.Handler)).

`python tools/android_upload_read_probe.py` passes 318 real file/worker checks:
payload-only ranges/EOF/zero files, unsealed refusal, immutable/native ownership,
revocation/release/expiry, full pre-access hash verification, range/wait limits,
UI wait gate, worker self-wait, four timed-out outcomes held until retirement,
full shared inbox, nine stalled UI replies, interrupted readers, close during
read, failed exact-file cleanup and an injected worker Error. Even an unexpected
worker unwind retires its current read waiter in `finally`. UI posting is a
double; these checks do not qualify Android's actual UI, FUSE or provider.
Evidence: `.mdo/E39-native-read-final.log`.

The 99 queue, 154 restart/recovery, 39 production-fragment and 44 device-fixture
checks also pass. Actual pinned Activity/hooks compile. Tiny QA APK:
311,699 bytes, SHA-256
`96d53b15ce5afe775fd16d0193a883f162efb8a120fc202918076b5318348385`.
Logs: `.mdo/E39-upload-queue.log`, `.mdo/E39-recovery.log`,
`.mdo/E39-upload-ui.log`, `.mdo/E39-upload-fixture.log`,
`.mdo/E39-android-build.log`. This is not a full Android mdo build or a real-device
pass. Keep the current WebView, false URI/assignment/automation qualifications
and the full development goal.

## Private URI provider and original proxy descriptor ownership (E40)

Implement the native ContentProvider, URI registry and `ProxyFileDescriptorCallback`
adapter over E39's original read leases. Source-object authority stays native:
URI publication accepts only the original queue's live object. Bind a canonical
`content://<application-id>.mdo.browseruse.upload/v1/<random-uuid>` to it;
queries/fragments/encoded aliases/foreign authorities cannot mint access. Tokens
are memory-only and never enter public tool/UI JSON or logs. There is no raw
cache path or raw cache descriptor handoff.

Both the actual mdo packager and tiny QA manifest register the provider with
`exported:false` and `grantUriPermissions:false` in the app's default process.
The provider validates that declaration at attach time and independently
enforces the calling UID on every public entry. It exposes display-name/size
and MIME metadata, exact `r` mode and read-only virtual descriptors. Writes,
insert/update/delete and noncanonical URIs are refused. Do not publish this
stateful object registry in another Android process.

Bound the registry to 16 file permissions and 64 original descriptor sessions.
One lazy application callback thread handles native FD requests. Each request
is at most 1 MiB and assembles exact bounded 64 KiB worker ranges under one
five-second callback budget; EOF is the only permitted short successful reply.
Failure or revocation zeroes the reply region. Closing an FD retires only that
session, allowing later reads of the same still-live file permission. Original
scope loss/release/expiry revokes data access and discards URI entries.

Keep unknown native effects accounted for. Before the platform open is entered,
an aborted reservation can be retired. After entry, an exception/null native
result keeps a closed, uncertain session until that original callback's
`onRelease`. Further opens are refused while uncertainty remains. A known FD is
closed on cancellation or handoff failure; close failures likewise keep the
reservation until the release acknowledgment. Duplicate/late old callbacks
cannot retire a replacement session. Listener detachment is completed while
the descriptor is still locally owned, and Handler allocation can recover on
the same original live callback thread without spawning a second one.

The data backend still owns the staged bytes while the proxy permission is
live. No raw cached file FD is transferred or permission restored after process
exit; old URI/queue/callback object identities are not reconstructed. Recorded
logical staging can therefore be recovered by E38 after the old process ends.
Previously delivered bytes are not made unread by permission revocation.

`python tools/android_upload_uri_probe.py` passes 418 checks through the actual
registry, provider, runtime and proxy code. Native files/worker are real;
Context/UID, Binder, storage, descriptor, cursor, Looper and cancellation APIs
are doubles. Cover full/partial/seek/empty ranges, descriptor capacity, retained
file permission after FD close, original callback reuse, cross-queue authority,
expiry, 16-file grants, partial-result wiping, deadlines, wrong UID, metadata,
writes/modes/aliases, cancellation/listener failures, failed/null/early-release
native opens, FD-close failures and allocation recovery. Evidence:
`.mdo/E40-uri-contract-final.log`. This is not an Android/FUSE/device pass or a
webpage assignment. References: [ContentProvider](https://developer.android.com/reference/android/content/ContentProvider),
[proxy callback](https://developer.android.com/reference/android/os/ProxyFileDescriptorCallback)
and [seekable proxy API](https://developer.android.com/reference/android/os/storage/StorageManager#openProxyFileDescriptor(int,%20android.os.ProxyFileDescriptorCallback,%20android.os.Handler)).

All 318 read, 154 recovery, 99 queue, 39 native-fragment and 44 device-fixture
checks pass. All 145/147 Python checks pass with two existing symlink-privilege
skips. The actual pinned Activity/hooks and new provider compile. The real mdo
packager's manifest expression is rendered and checked, and the private QA
manifest is packaged. Tiny QA APK: 323,987 bytes, SHA-256
`f79cd265db0f6ae545404487602f9c66f0fec6f4789a277144306487cee8ac5e`.
Logs: `.mdo/E40-native-read.log`, `.mdo/E40-recovery.log`,
`.mdo/E40-upload-queue.log`, `.mdo/E40-upload-ui.log`,
`.mdo/E40-upload-fixture.log`, `.mdo/E40-python-full.log`,
`.mdo/E40-android-build.log`. Keep the installed WebView and false framework,
chooser/source-frame, assignment and full-automation qualifications. A real
ContentResolver/seek/revocation device fixture and chooser handoff remain next.

### Installed private URI test entry (E41)

The existing tiny QA APK now includes `native-upload-uri`. It runs through the
actual app cache factory and private provider, without constructing a WebView;
the current installed System WebView can remain unchanged. Run
`python tools/android_device_probe.py --mode native-upload-uri` with exactly one
authorized device. The runner verifies the current source/build/APK receipt,
touches only its recognized development package, and saves the nonce-bound
result to `.mdo/android-device-upload-uri-result.json`. URI tokens, data paths
and platform exception messages never enter the report.

The fixture stages only 150,001 deterministic bytes and an empty file. One
background consumer uses ContentResolver metadata and read-only seekable FDs
to verify exact payload, Unicode name, MIME, size, EOF, chunk-boundary/random
seek and close/reopen. Writes and URI aliases must be refused. Cancel the exact
original scope, refuse subsequent metadata/opens, and require a newly opened,
previously unread descriptor to fail its first read. Already delivered bytes
are outside this revocation claim. A successful receipt also requires actual
original release callbacks and zero descriptor/grant/file/byte/UI-receipt
reservations. Pause/destroy/timeout cancel the original operation and close its
exact owned stream; late completion is ignored, with no UI join/read.

`native_upload_uri_proxy_verified`, `uri_proxy_framework_verified` and
`upload_uri_revocation_verified` are separate from chooser assignment, original
source/frame identity, host routing and full automation. The latter gates stay
false even if this isolated provider fixture succeeds. A missing device is an
explicit unavailable result with all qualification fields false.

`python tools/android_upload_uri_fixture_probe.py` executes the exact fixture
against the real worker/staging/provider/proxy and doubled Android Resolver,
descriptor, UID and Looper APIs. Six original-static JVM runs pass 168 checks:
success (49 fixture checks), corrupted bytes, missing release acknowledgment,
closed-before-start, cancellation during read and cancellation during open.
These doubles verify fixture outcomes and owned cleanup; they do not establish
Android kernel/FUSE, cache authority or webpage upload behavior.

The current QA APK compiles against the existing SDK: 340,371 bytes, SHA-256
`964579f4eca1d42dac9eb16320d86503546ee7f68656b607b694fe5b368a8d57`.
The actual pinned Activity/hooks and provider also compile. All 145/147 Python
checks pass with two existing symlink-privilege skips. Evidence:
`.mdo/E41-uri-fixture.log`, `.mdo/E41-android-build.log`,
`.mdo/E41-python-full.log`. The device attempt is unavailable (zero ADB devices),
recorded in `.mdo/E41-device-uri.log`; no installed-device qualification, mdo
release or Windows package change is claimed.

### Human document picker for the manual browser (E42)

The child WebView now routes unintercepted file requests to its own native
`ACTION_OPEN_DOCUMENT` picker. Previously it returned the default response;
Android's default cancels the request, rather than delegating to the main
WebView's Activity picker. See the [WebChromeClient contract](https://developer.android.com/reference/android/webkit/WebChromeClient#onShowFileChooser(android.webkit.WebView,%20android.webkit.ValueCallback%3Candroid.net.Uri[]%3E,%20android.webkit.WebChromeClient.FileChooserParams)).
This repair does not depend on isolated worlds or a WebView upgrade.

There is one pending system picker per Activity. Its callback stays bound to
the original native WebView object, navigation and host channel. Preserve the
callback during the system picker's pause/return (results can arrive before
`onResume`); cancellation, hide, navigation, stop, close, takeover, channel loss,
timeout and destroy cannot redirect it to a replacement document. Original
callback identity is claimed once by the target. A two-minute uptime deadline
can cancel that callback, but a launched/uncertain native result still retains
its slot until the original result returns. Further native picks/interception
are refused while that slot is outstanding. State reports
`manual_chooser_open` / `manual_chooser_awaiting_result` and cleanup uncertainty,
with no URI or file path. Callback or unblock failures require Activity teardown.

The request namespace is `0x6000..0x7fff`, separate from the pinned shell's
main picker/export requests. Do not reuse codes within the process; only its
monotonic counter is saved/restored. Callback and URI permissions are never
restored. Orphan/old results in this namespace are consumed without delivery;
malformed/exhausted counters refuse new requests. Selection accepts at most 16
read-permitted content URIs from external-UID providers, with bounded MIME/URI
metadata. App-private providers, file/data URLs and invalid/malformed/oversized
selections are rejected as a whole. No model-facing URI assignment or automatic
folder/save/camera capability is enabled.

`python tools/android_manual_chooser_probe.py` passes 106 checks through the
production controller with Android Activity/Intent/permission/Looper doubles.
`python tools/android_chooser_probe.py` passes 471 controller and 42 native
target-callback checks. URI/provider (418), exact device-URI fixture with doubles
(168), native upload fragment (39), and Python (145/147, two existing symlink
skips) pass. The actual pinned Activity/hooks and new controller compile.
The QA APK is 348,563 bytes, SHA-256
`ddc1e1b94f1a646406c15cf86598a9f11b1c9ab36561d30863f87117b262eeba`.
Evidence is in `.mdo/E42-*.log`. It contains diagnostic fixtures, not a complete
Android mdo client. `manual_chooser_framework_verified` remains false; a real
picker/URI/FUSE run is still required. Keep the current WebView and the false
source/frame/automatic-assignment/global-automation gates.

### Shared human URI ingress and result handoff (E43)

Both human-picker entry points now use `MdoBrowserPickerUris`: the trusted host
WebView and content WebViews. The exact pinned shell parser only checked the
`content` scheme/authority and assumed there were no private providers. It
accepts the synthetic same-UID URI in `.mdo/E43-picker-baseline.log`. This
demonstrates the parser gap; it does not demonstrate an external app learning a
live private token or obtaining its payload. E40 introduced a private provider,
so no human picker may nominate that provider, another alias with our UID, or
any other same-UID provider.

Require an explicit URI read grant to the actual app PID/UID, in addition to
the external provider identity. General provider access is intentionally not a
substitute: [Context.checkUriPermission](https://developer.android.com/reference/android/content/Context#checkUriPermission(android.net.Uri,%20int,%20int,%20int))
checks explicit grants, while [WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient#onShowFileChooser(android.webkit.WebView,%20android.webkit.ValueCallback%3Candroid.net.Uri[]%3E,%20android.webkit.WebChromeClient.FileChooserParams))
leaves returned-URI validation to the app. Bound 16 raw selected entries,
8,192 URI characters and 256 authority characters. Refuse mixed invalid/private
selections as a whole; do not fall back from invalid ClipData to a different
Intent URI. Lookup/permission failures or metadata allocation errors cancel the
selection. Cross-user (`user@authority`) URIs remain unsupported. The validator
opens no file/URL and mints/persists/logs no URI grant or path.

Standard host modes 0/1 use the original callback controller with the exact
host/channel identity and its native result namespace. Nonstandard host modes
retain the pinned shell's existing launch route, now under the same URI result
filter; its export/save request handler is separate. No new folder/save/camera
automation is implied. Track at most 128 live callback identities with weak
references; duplicates cannot relaunch or receive a second reply. If identity
recording runs out of capacity/memory, preserve the original identity and
require teardown rather than silently reusing it.

The result handler retains its original request while URI metadata/permission
checks run, so cancellation/close can still find it. Ignore duplicate reentrant
results. Recheck the original owner after metadata and after releasing the
chooser's block, immediately before handing non-null URIs to the native
callback. A source change or normal cancellation drops the selected URI and
replies once with null. `.mdo/E43-picker-handoff-before.log` retains the failing
pre-fix ownership-loss case. All 554 controller/ingress checks pass after repair
(`.mdo/E43-picker-policy-final.log`), using the actual policy/controller and
pinned old parser with doubled Android Activity/permission/UID/Looper calls.
These are not main-UI/device/framework qualifications.

The actual pinned Activity/hooks and shared policy compile. Tiny QA APK:
348,563 bytes, SHA-256
`feeaaf4a1b6936cae6868d3ee9e8696764f80f5a35416bd95529016a5e90b14a`.
Evidence: `.mdo/E43-android-build-final.log`. Main-picker and manual-framework
qualifications remain false; the packaged diagnostic Activity is not the full
mdo Android app. All 513 chooser, 418 URI/provider, 168 URI-fixture and 39 upload
fragment checks pass; Python passes 145/147 with two existing symlink skips.
No installed-device, automatic private-URI assignment or full-automation pass
is claimed, and the existing WebView is retained.

### Private CDP lifecycle and asynchronous dispatch (E54–E55)

`MdoBrowserCdpRegistry` now follows exact managed native view/document objects,
not reusable tab names. The native input observer invalidates its lease before
completing input cancellation, including navigation, hide, takeover, background,
zoom, stop, dialogs, choosers, popups and close. Host channel blockers invalidate
all leases. Retiring a marker drops its UI view/predicate reference. Rebinding
after a short admission loss retains the same 16-declaration document quota;
only observed navigation or native view destruction retires that quota. Manual
browsing does not activate CDP or create a protocol worker thread.

`MdoBrowserCdpWorker` is a private typed-operation bridge: one persistent worker
owns cached protocol bindings. At most eight operations are retained, including
results awaiting the UI, with one operation per lease and no caller-runs policy.
The 100–15,000 ms operation budget includes queue/binding time. Native effects
are never implicitly reconnected/replayed. UI lifecycle retirement cancels queued
work, wakes native binding/I/O through the lease, and posts a single cancellation
receipt rather than reentering a controller during teardown. Work that has
started an effect is conservatively reported as potentially effective.

UI results are bounded to 1 MiB each and valid only during their synchronous
completion callback; copy/parse a required value within that callback. Buffers
are cleared on delivery, cancellation, stale-owner discard, oversize, callback
failure and shutdown. A failed UI scheduler retires the worker and all of its
leases, clears pending callbacks/results and refuses further submission. A
throwing UI completion retires its binding without breaking other operations.

These private pieces are compiled into the actual development APK, but no raw
protocol/endpoint/source command or debug activation switch is exposed. The
typed isolated-frame automation backend and its profile/transfer policy are
still under development. `android_automation_enabled`, installed framework and
provider qualification remain false. The 107 registry and 167 worker checks use
actual Java controllers/binding/strict JNI with doubled Android UI and synthetic
protocol peers; they are not real-device passes. Keep the user's current WebView.

### Native frame graph and process-session routing (E56)

The private binding now routes explicit child sessions through the same strict,
single-worker dispatcher. Only the owned native controller admits sessions;
Agent/page callers cannot choose raw frame, target, session or context IDs.
Root-context lifetime checks remain separate from child execution-context events.

`MdoBrowserCdpFrames` reconstructs one WebView's hierarchy using `Page.getFrameTree`
from each recursively auto-attached related iframe source. The pinned Chromium
implementation omits non-local children from each renderer's snapshot. A child
source's native target/frame ID and parent must join an existing frame inside
the original auto-attachment source's subtree. The root frame and loader remain
bound to the native-view proof. URLs, titles and opener hints are not proofs.
Type-filter attachment to workers, unrelated ancestry, ambiguous sources,
duplicate IDs, malformed trees and reused session IDs are rejected.

Bound 32 frames, 64 sources including the root, 256 lifetime child-session IDs
and ancestry depth 32. Publish opaque `f0`/`f-UUID` and `fd-UUID` aliases, retaining
native IDs/loaders privately. A committed document/open event replaces its
document identity and retires descendant references immediately; subtree detach
also fences references before final removal. Process swap and reverse migration
cannot reuse old document refs. Loading without a document commit temporarily
blocks routing while preserving a proven unchanged loader/document afterward.
Queued traffic from retired sessions is drained by the dispatcher, never handed
to another owner. Discard raced metadata replies and bound read-only refresh;
never replay an effect. Missing/empty loader identities cannot admit a route.

This stage is metadata only: inventory continues to report `isolated:false`.
It adds no page execution or Browser Use admission path, no arbitrary target
attachment, universal-origin permission, browser-wide discovery or debug switch.
The next stage must prove/create each exact frame's isolated unique context,
then connect fixed execution/owner/transfer operations and activation policy.
The frame gate exercises the actual Java graph, worker, binding, dispatcher and
strict JNI reader with 172 checks, doubled Android UI and synthetic protocol peers; it does
not qualify an installed provider. Preserve the current WebView and deferred
phone, and keep global Android automation disabled.

### Private isolated frame execution (E57)

`MdoBrowserCdpWorlds` now implements the private execution core through the
existing single-worker dispatcher. A successful world must match the original
native route, source session, creation reply, native frame/type/name metadata
and unique execution-context identity. Before any page expression, each source
must reject a deliberately invalid unique context; each world must also return
the expected harmless result in its real unique context. Do not evaluate by a
numeric context ID or silently fall back to a default/main world. All frames
continue to use their original native ancestry and document/loader guards.

Use a fresh namespace for each binding, with a separate random name per proven
native source/frame/loader tuple. The pinned implementation reuses existing
named wrapper worlds before initializing their security origin; a new native
document must therefore not adopt an old or potentially privileged named world.
Create only with `grantUniveralAccess:false` (the protocol's historical spelling).
Rechecking the same unchanged native document reuses its name, including an
admission-reference fence that did not replace its loader. Keep a shared limit
of 256 name reservations per UI-owned document generation across binding/lease
replacement, without refunding uncertain creation. Actual navigation/view
callback semantics remain subject to the deferred framework qualification.

Keep at most 32 current world routes, 256 active context records and 1,024
lifetime session/context keys. Unknown origins, URLs and unrelated world names
are not retained as metadata; initial root identities are minimal ID/unique-ID
pairs captured during native binding. Missing/ambiguous metadata, reused keys,
default/unrelated worlds and duplicate unique IDs cannot admit execution.
Metadata that arrives after the creation reply has a bounded 500 ms wait under
the original job deadline. Document and ancestor changes revoke old routes;
destruction of an admitted world fences its exact document/descendants even
before Page events. An old context's late destruction cannot retire a newer
loader/document. Source detach retires its contexts without retargeting them.

Return only by-value JSON/finite primitives in an envelope with opaque frame/
document aliases. Bound source to 192 KiB UTF-8 and the result envelope to 1 MiB;
reject unserializable/undefined/exception replies without exposing raw exception
details. DOM expressions cannot use `throwOnSideEffect` as a permission policy;
that flag protects only the fixed capability probes. Existing broker policy and
the operation's effect classification remain required. Effect uncertainty
survives navigation, context/transport loss and lost replies; no script is
automatically replayed. Java strings/JSON values remain bounded GC-managed data;
the existing worker clears owned result bytes on delivery/discard.

The new private inventory can prepare worlds and report actual `isolated:true`
only for current proved routes. The private evaluator first performs a native
metadata barrier, then validates the original aliases/ancestry around every
phase. These APIs are not yet wired into Agent admission. The 539 isolated-world
and 375 registry/resource checks use actual Java frame/binding/session/worker
and strict JNI with doubled Android UI/synthetic evaluation peers. Real DOM
execution, owner geometry, native input/transfer integration, activation/profile
policy and installed-provider/framework qualification remain. Keep the current
WebView, deferred phone and global Android automation disabled.

### Shared frame commands and parent-owner queries (E58)

The actual native `frames` command now routes list/evaluate/owner/upload-policy/
capture-owner requests through the bounded worker and proved native graph.
Product/profile admission still runs first and stays disabled; there is no
request flag to enable debugging or bypass it. Require literal `agent:true`,
opaque frame/document aliases and action-specific fields. Reject caller-picked
target/session/context/object IDs and unknown fields. Copy script/point data
before queueing. Preserve the original host channel and view epoch when replying;
cancelled or retired views cannot deliver a success for a replacement view.

Replies distinguish stale frames, cancellation, timeout, unsupported capability
and resource limits while retaining a bounded static `native_code`. They never
expose raw provider exception details or protocol identifiers. Trusted-host
script evaluation conservatively retains effect uncertainty; read-only owner
metadata queries do not claim a website mutation. This command bridge implements
the shared JS/C transport shape, not completed Agent/product qualification.

Owner queries resolve the child frame's native owner node **on the pinned parent
source**. DOM node resolution needs a numeric context parameter; that number is
never used to select the function's execution realm. Instead call functions in
the parent's proved unique context and pass the node as an object argument. The
pinned engine checks that argument's context/isolate before running the function.
Require negative unique-ID rejection and a harmless same-world argument proof
before DOM helper execution. Never use an object's own/default realm as fallback.
Guard both child and parent around every phase, including cleanup.

Geometry, parent upload policy and capture-owner helpers come from the existing
reviewed `host/native-frames.inc.hpp` bodies, using generated Java constants.
`python tools/update_android_owner_scripts.py --check` runs before protocol tests
and Android source hashing/build. Missing/ambiguous/oversize/stale helpers fail
the check; unknown generated edits/pending writes are preserved. This adds no
runtime generator/dependency and leaves the Windows helper implementation intact.

Every query has one random temporary object group. Release it on the exact
original source before publishing a result. On uncertain cleanup or lost budget,
retire the binding, retain timeout/cancellation causes and withhold success; do
not extend cleanup deadlines, retarget or replay a DOM query. The 451 owner and
49 command-grammar checks use actual Java graph/world/worker/strict JNI with
modeled DOM and native argument-world checks. Three Python generation contracts
check shared-source parity. These are not Android DOM, UI or provider passes.
Next complete frame-chain native input/capture, transfers and activation/profile
policy, plus installed main-UI/shared JS/C testing. Review Windows' existing
numeric/object realm selection against the same unique-context invariant during
the cross-platform completion audit; no Windows realm qualification is inferred
from Android's synthetic gate.

### Native frame-chain input bridge (E59)

The actual host input route now uses `MdoBrowserCdpInput` and the native frame
map, replacing the main-document-only evaluation bridge. Copy and validate the
kind, target, focus, coordinates, text/keys, native chain and optional complete
frame snapshot before admission. Reject caller code, protocol IDs and unknown
fields. The immutable chain must end at the original root; prove every native
parent/document and (when supplied) the exact complete ready isolated-frame set.
Caller aliases or a syntactically valid ancestry alone grant no authority.

Before each native input step, the existing single protocol worker checks that
chain, the root layout/scale/scroll/visual viewport and the original local
runtime document/ref. Fixed scripts prepare the exact target without scrolling,
reject a child-frame hit on a local ancestor element, and map its point through
proved parent owners. Click/hover/wheel must still map to the requested root CSS
point. Keyboard/focus additionally require exact DOM focus and every parent
iframe to retain focus. Wheel guards reverse-map the root point through pinned
owner geometry, verify the local original hit and map it forward again. The
shared Agent subframe scroll dispatcher still needs to supply that complete pin;
this native implementation alone does not claim that UI flow is finished.

Only the original WebView receives framework events, through the existing
sequencer. Recheck native UI layout/lease after asynchronous guards; do not send
CDP input, target another view, use a default/page-world fallback or replay a
possibly delivered step. Held touch/key cleanup remains the sequencer's cancel/
key-up path. If an input times out or is cancelled with a queued/running guard,
close that exact worker lease before completing; late callbacks cannot continue
a gesture or leave a queued focus script to run against a replacement view.
Focus retains effect uncertainty even when its later DOM acknowledgement fails.
Return typed pre-dispatch failures and the ordinary accepted/effect/cleanup/
delivered-step receipt for failed input admission as well as completed sequences.

The 300-check actual Java sequencer/frame/world/worker/strict-JNI gate uses
modeled native DOM/protocol peers and Android event targets. It covers root and
nested process frames for all six input kinds, copied requests, wrong ancestry/
snapshots, changed root points, failed focus, user takeover, navigation after
pointer-down, touch cancel, queued cancellation and watchdog expiry with no late
DOM focus or input continuation (`.mdo/E59-input-core.log`). Nine Node tests run
the exact fixed Java scripts and a shared-runtime iframe-hit fixture; full Node
regression passes 283/283 (`.mdo/E59-node-full.log`). The real SDK/D8/package/ABI/
signature build passes (`.mdo/E59-android-build.log`). These are not installed
provider geometry, trusted event or framework lifetime qualifications. Keep
Agent automation disabled and the deferred phone/current WebView unchanged.

### Shared visual snapshot root-parent normalization (E61)

The shared visual dispatcher emits an empty root-parent string, matching the
existing Windows input wire contract. Native inventories use JSON null. Accept
both only for the explicitly named `f0` snapshot row and normalize them to the
same immutable null parent before native comparison. Child parents still must
be exact frame aliases and form a complete rooted native tree; missing parents,
empty child parents and caller-picked root ancestors remain invalid.

The Android input gate now includes a snapshot in the actual shared-dispatch
wire shape, queues it through the real worker/frame/isolated-world/sequencer
core and verifies a later caller mutation cannot replace its admitted pins.
This fixes a shared-to-Android format mismatch; it does not enable Android
Agent admission or qualify installed framework input. Evidence is retained in
`.mdo/E61-snapshot-wire.log`; development APK refresh follows with the shared
scroll-driver stage.

### Shared native subframe scroll routing (E63)

Runtime 20 lets the trusted frame driver obtain only an exact local iframe owner
reference when a scroll point hits one. The default/main-document helper still
refuses that boundary. Never read child contentWindow/contentDocument, URL or
name to choose a native route. The driver joins that reference to the native
parent-owner record, translates into the original child's visible content,
requires its own local document identity, and repeats down the bounded tree.
At a leaf, re-map the point through the native owners and validate the original
root point, original frame/documents and complete chain. Pass its local hit ref
and document unchanged to Android input. Reject incomplete/duplicate/wrong-root
chains, changed points/documents and malformed hit references before a wheel.

The shared point route also admits visible portions of partially clipped frames:
content origins may be finite/bounded negative coordinates, while the selected
point must remain inside its proved content and the native forward map must
still land in the original viewport. This is not permission to enter hidden,
transformed, inert or unrelated frames. Read-only routing never focuses or
clicks an element and never reads field values.

Full sequential Node regression passes 292/292, including routed child identity,
immutable native pins, changed point/doc rejection and clipped-frame geometry.
The real Windows WebView2 fixture passes 224 checks, including a three-level
native/OOPIF scroll hit retaining its original leaf document/reference without
changing focus. The Android private input gate passes 315 checks, including the
shared visual snapshot root-parent representation. These complement each other;
they are not an installed Android provider/geometry/event qualification.

The refreshed full development APK uses the verified 12-check Windows archive,
paired runtime-20 plugin and existing native ARM64 engine, with the E62 explicit
development update-channel prefix. SDK/D8 definitions, package/native ABI,
alignment/private-provider attributes and signatures pass. Keep the original
provider, deferred phone and overall Android automation gate unchanged. Continue
frame-chain capture, transfers, activation/profile policy and platform audits.

### Two-phase screenshot admission and final validation (E64)

The native capture transaction invokes its original isolated guard before native
visual readiness and again after bitmap encoding, before transferring any image
bytes to the caller. Both inherit the same original absolute deadline. Keep the
encoded bytes in the original bounded transaction while its final guard waits;
release the bitmap but retain the shared capture reservation through final
validation. Other views cannot accumulate unacknowledged private images outside
that quota. Successful output ownership transfers to the completion callback.

Each guard callback carries its exact weak lease, phase and monotonically
changing ticket. Pre-capture or duplicate callbacks cannot authorize/reject the
final phase, retain the cancelled Job/target or affect a newer capture. Recheck
active Job/phase after calling the target's snapshot supplier: reentrant
cancellation cannot request another visual callback, allocate a raster or retain
an already encoded image. A received native-ready callback still settles its
original work acknowledgement even if that snapshot call cancelled the job.

Clear owned encoded bytes on final-guard rejection, navigation, cancellation,
close/deadline, lost UI dispatch, failed delivery/allocation, invalid encoding or
cleanup failure. A lost UI delivery releases its already terminal resource
reservation after clearing the bytes; unconfirmed bitmap cleanup retains its
existing poisoned reservation. Final validation never replays a draw/encode or
extends a deadline. E64 first used this path with the main-document guard; E65
below replaces that guard with the private complete native-frame proof. Overall
Android Agent admission stays disabled.

`python tools/android_capture_probe.py` passes 304 actual Java-core checks with
synthetic targets/schedulers/raster encoders. Added coverage includes final-guard
withholding, old/duplicate callbacks, original remaining deadline, all final
cancel/error paths, byte clearing, shared private-image quotas and cancellation
reentrant from snapshots at each phase. Existing worker/native callback lifetime,
OOM, cleanup and buffer limits remain covered (`.mdo/E64-capture-guard.log`).
The actual SDK/D8/signature/ABI host build passes (`.mdo/E64-android-build.log`);
full Python regression passes 165/167 with two existing symlink skips in 65.829 s
(`.mdo/E64-python-full.log`). The current provider/phone remain deferred and
framework drawing, installed-provider geometry and full frame capture are still
unqualified. Windows program/paired plugin remain at the exercised E63 versions.

### Native frame-graph screenshot transaction (E65)

The shared frame driver now freezes the Android native navigation owner and
passes an `android-native-frames-v1` capture context: exact root native/local
documents, viewport/scroll/DPR and the complete composed frame stamp. Windows
keeps its existing wire format. The trusted renderer retains its original
per-frame masks, including opaque geometry/ancestors and unreadable subtrees;
the native guard does not remove masks or infer child identity from URLs/names.

`MdoBrowserCdpCapture` copies the admitted primitives, validates rooted unique
frame rows and bounds the embedded stamp before recursive JSON parsing (65,536
characters, depth 8, 512 containers, 32 frames, no second/trailing value). Caller
scripts, raw protocol IDs, old context adapters and lossy sources are rejected.
Native and context navigation use the same positive JavaScript-safe integer.
The original visible native viewport must match the root CSS scale/scroll.
Offscreen/full-page regions fail explicitly; no hidden scrolling is performed.
The source remains PNG, at most 262,144 encoded bytes and 4,194,304 output pixels.

Before drawing and again after encoding, the existing bounded worker checks the
complete native graph, exact unique isolated world/local document of each
readable frame, parent-owned local iframe reference, geometry and viewport/scroll.
Opaque geometry must remain the same known unsupported geometry. Recheck root
world and graph at each terminal barrier; retain one original deadline. Bind
delivery to the original native view, epoch and host channel. Closing/cancelling
the guard cancels its exact pending native lease; no draw/input is replayed.
E64's core withholds/clears private PNG bytes on rejection and releases its
bounded reservation only after final validation. The host clears delivered bytes
after base64 handoff; shared mask composition and postcapture scanning still run.

The complete Android frame adapter can issue the existing scoped visual reference;
the legacy main-document adapter still cannot. Subsequent image actions carry
that exact native capture context and require its echoed receipt. Their private
validation source now always requests PNG/262,144 bytes, independently of the
public JPEG or small output limit. Lossy/changed/failed receipts dispatch no input.

Verification: actual production Java/JNI/graph/world/owner/worker plus capture
transaction pass 161 checks (`.mdo/E65-native-frame-capture-final.log`). This uses
real JNI/JSON/socket transport, but protocol peers, UI/scheduler and raster are
synthetic; it does not qualify an Android framework/provider. Coverage includes
child document/owner changes before/after encode, opaque descendants, immutable
caller data, cancellation, byte clearing, original deadlines/cleanup and bounded
malformed/deep/trailing stamps. The existing core's 304 two-phase/lifetime checks
remain in E64. Node passes 299/299; Python passes 165/167 with two symlink skips;
final Android contracts pass 20/20. Actual Windows WebView2 frame regression
passes 224, package roundtrip 8, refreshed packed Windows runtime 12. The complete
SDK/D8/ABI/payload/private-provider/alignment/signature APK build passes
`.mdo/E65-android-build.log`; the artifact hash is recorded at the top of this file.

No device/provider update or installation took place. Native chooser assignment,
download/source transfers, activation/profile policy, installed DOM/drawing/input/
main-UI checks and the cross-platform unique-context audit remain outstanding.
Android Agent admission/debugging stay disabled, with the existing manual
browser and formal package/data preserved. No model calls or online publication.

### Native file-input target inspection before source access (E66)

The Android shared frame driver now inspects direct and intercepted-picker
upload targets through the private `frames/file_input` route before returning a
target to the upload dispatcher. Windows retains its existing path. The request
copies the exact native leaf/ancestor chain, local document and retained element
reference, and inherits only the remaining read deadline. No script, file path,
raw CDP/backend node ID or caller-selected execution world is admitted.

`MdoBrowserCdpFileInput` checks the native chain and every ancestor's upload
policy before and after local object inspection, followed by one final native
chain barrier. In the already proved unique isolated world, fixed helpers obtain
only the runtime's exact `uploadNode` and compare it again with `uploadInfo`.
An empty-unique-context negative test and native same-world argument proof
precede the fixed DOM helper. Native `DOM.describeNode` must describe an input
element with a positive backend node ID; that ID is never serialized or retained
as caller authority. Each random object group is released on the original
source, or its binding is retired when cleanup cannot be confirmed.

Only exact document/reference and boolean multiple/directory/hidden metadata
reach the trusted driver. A changed echo, flag, frame chain or authoritative
cancellation withholds the target. Native disabled/inert and cleanup errors
retain specific public codes and their bounded native cause. An integration
test joins the actual frame driver to the actual upload dispatcher and proves
that rejected native binding causes zero source SDK reads, upload calls or
dispatch markers. Inspection itself never reads selected files, assigns a
value, publishes a file URI or dispatches an input/change event.

The actual Java/JNI/JSON/worker/graph/world/owner route passes 625 checks with
synthetic protocol DOM peers (`.mdo/E66-native-final.log`). Cases include root,
same-process and OOPIF targets, immutable queued pins, wrong node/realm/unique
context, malformed metadata/raw IDs, disabled/inert ancestors both before and
after inspection, document changes at each boundary, cancellation/timeouts,
cleanup failure and no private native ID in replies. Production helper strings
are separately executed by Node VM tests, including a selected-files getter and
assignment/event methods that throw if touched. Full sequential Node passes
305/305; Python passes 165/167 with two existing symlink skips. Actual Windows
frame checks pass 224, and refreshed packed runtime checks pass 12. The full
Android SDK/D8/DEX/ABI/payload/private-provider/alignment/signature build passes
`.mdo/E66-android-build.log`; artifact hashes are at the top of this document.

This completes native target preflight, not Android file delivery. Original
chooser identity/callback assignment, native payload/URI delivery and download
integration remain in progress. Android Agent/debug admission stays disabled;
the phone/current WebView and formal package/data remain unchanged. Installed
DOM/geometry/file-event/UI qualification remains outstanding. No model calls,
external page browsing, extra app/Home/source copies or online release.

### Native chooser event and input-node evidence (E67)

Private `frames` commands now include `chooser_arm`, `chooser_list`,
`chooser_disarm` and `chooser_target`. Request native events on every proved
renderer session, including OOPIF sources, with
`Page.enable(enableFileChooserOpenedEvent=true)`. This does not set native CDP
interception/suppression or answer any Android callback. An acknowledged enable
request alone is not proof that a provider emits the event: listings distinguish
`events_requested` from a positively observed `event_verified` candidate.

Retain at most one original `Page.fileChooserOpened` candidate per native binding.
Validate its frame/source membership, native loader/ancestor route, positive
backend node and single/multiple mode. Only a random `nc-` handle and safe native
frame aliases/metadata leave the worker. Events while arming and repeated events
refuse without replacing the candidate. An idempotent arm preserves the original
120-second monotonic lease; it never renews an existing candidate. Expired,
changed-source, regressed-clock and malformed events cannot provide a target.
Disarm retires this metadata/listening state; it does not claim to cancel an
Android file chooser callback. These handles cannot be restored across bindings.

Resolve the event's native backend node on its original renderer into the proved
frame world. The protocol's numeric-context resolve operation is followed by
empty-unique-context rejection and a native same-world argument proof before
any fixed DOM helper runs. Re-describe the node and require the exact backend
identity, then pin only that connected file input through the local runtime.
Check native ancestry/upload policy before and after, plus the original chooser
and native chain at delivery. Return bounded input flags/reference only; no raw
object/context/backend/session IDs, arbitrary scripts or paths are admitted.
The result explicitly retains `assignment_supported:false`.

Event rejection initially lost its cause in `cdp_event_failed`. E67D introduces
an internal typed failure with six reviewed static codes. The dispatcher retires
the original stream and preserves only those codes and the original effect flag;
ordinary exceptions and provider messages remain generically sanitized. Its
actual JVM/JNI/session/strict JSON suite passes 4,310 checks across seven sessions.

The native event/target path passes 1,624 actual Java/JNI/graph/world/owner checks
with synthetic event/DOM peers (`.mdo/E67-native-full.log`): root/same-process/
OOPIF routes, transient getFrameTree event ordering, single/multiple selection,
wrong source/realm/node, duplicate and arming events, immutable queued pins,
expiration/clock overflow/regression, cancellation, cleanup and original-ID
non-disclosure. Production pin helpers execute in Node VM tests without reading
selected files, assigning values or dispatching events. Full sequential Node
passes 306/306; Python passes 165/167 with two existing symlink skips. Full
SDK/D8/DEX/native ABI/payload/private-provider/alignment/signature build passes
`.mdo/E67-android-build.log`. The unchanged E66 Windows archive and paired plugin
are reused only after current-source/receipt validation; no extra app copy.

This is native event/node evidence, **not authority to answer an Android
ValueCallback**. Original callback association and URI delivery are still open;
installed provider/frame/file-event/main-UI qualification remains false. In
particular, do not join event/callback authority using timing, URL or active-frame
hints alone. The next delivery stage must prove a private nonce-bound native
callback and preserve exact target identity, cancellation and data cleanup.
Pinned Chromium source fingerprints are recorded in `chooser-protocol.json`:
its Page agent supports event reporting without suppressing the native chooser,
while direct path-based file assignment derives the default filename from the
path basename. Existing opaque URI names and header-bearing staging files must
not be passed directly as complete, correctly named payload files.

Android automation/debug admission and actual file assignment remain disabled.
No phone/WebView/formal data changes, model calls or online release occurred.

### Private native picker and FileList handoff helper (E68)

`host/upload-picker-broker.js` is one self-contained isolated-world helper,
generated into the private `MdoBrowserCdpUploadScripts.INSTALL` Java constant.
The generator checks source shape/size and preserves unknown edits. Both the
actual native browser fixture and Android compilation consume the same installer.
There is no added runtime dependency and no new public Agent/UI command.

Prepare binds one current local document/reference and exact file-name/size
manifest, captures the original target object and its initial File objects,
and creates a plain input inside a closed shadow root. A fresh 128-bit nonce in
its custom MIME accept string is returned **only to the future native upload
controller**. The private node getter has the same restriction. Never publish
either through frame actions, tool results, persistent receipts or logs. Ordinary
page DOM traversal and page-world prototype overrides cannot expose this input.
Status/commit/dispose never contain the nonce, raw native IDs or filesystem paths.

One operation keeps its original monotonic deadline (at most 120 seconds), at
most 16 files/32 MiB each/64 MiB total, and one active reservation. Retired keys
remain retired; a frame world retains at most 64 operation records. The exact
connected target/document/ref, capacity/disabled/inert state and original File
objects are checked before assignment and after each event. Key reuse cannot
change the target/manifest or renew the lease. Native picker activation errors
have static codes; failed/uncertain opening or assignment is not replayed.

Keep the browser-selected native `FileList`; do not rebuild bytes as Blob/File
or treat the opaque provider URI/staging pathname as a correctly named payload.
Use the native files setter and exactly one synthetic input/change pair,
explicitly reporting `events_trusted:false`. Verification is local File object
handoff, not website acceptance. Page handlers that clear/replace the target
produce an accepted/dispatched but unverified partial result, and no remaining
event or assignment is repeated.

Actual Chromium testing caught two shared-FileList hazards: clearing the private
input's value also clears the real target; and comparing two live FileLists can
falsely verify a page handler clearing their shared backing list. Pin a separate
array of the original native File objects for verification. At cleanup, detach
the private input onto a fresh native empty input's FileList before removing its
original host; never clear the shared selected list. Failed cleanup poisons that
reservation until exact cleanup succeeds. Native staging/URI permissions must
remain governed by their separate original leases/consumption receipts.

The loopback-only actual WebView2 fixture passes 30 focused and 254 full native
checks (`.mdo/E68-windows-broker-only.log`, `.mdo/E68-windows-final.log`). It uses
the **trusted WebView2 host evaluation** as its activation source; it is not an
Android Runtime.evaluate/userGesture or hardware-input qualification. It verifies
the real intercepted native picker, closed-shadow/nonce privacy under page-world
overrides, original Unicode/delimiter basename and all 131,075 payload bytes by
SHA-256, event trust/count, shared-list-safe cleanup, page reset detection, receipt
replay protection and zero retained staging reservations. Controlled VM tests
cover failure/cancellation/deadline/resource paths and execute the generated
Java installer too; they do not replace actual browser/framework checks.

Final JS passes 319/319. Python passes 167/169 with two existing symlink skips,
and generated-script checks pass again after the final helper changes. Actual
JVM/JNI/protocol/worker tests, SDK/D8/DEX/native ABI/payload/private-provider/
alignment/signatures pass (`.mdo/E68-native-full.log`, `.mdo/E68-android-build.log`).
The complete development APK above contains the helper, **but its original
Android callback/URI delivery controller is not joined or admitted yet**. E67
native parent/chain/world checks must still surround that future controller;
this local DOM helper cannot replace them. Provider/device/main-UI checks and
Windows unique-context/realm audit remain open. Earlier full native runs also
had intermittent input-focus/drag-cleanup interruptions; retain their logs and
investigate rather than treating one subsequent full pass as their root-cause fix.
Android automation/debug admission stays disabled; phone/WebView/formal data,
model services and online releases are unchanged.

### Original private callback rendezvous and native URI permission gate (E69)

The actual Chrome target now routes the reserved private MIME namespace through
`MdoBrowserPrivatePicker`, sharing the existing chooser's once-only callback
identity ledger. Ordinary manual picking and cancellation retain their own
routes. A pending native-only lease admits exactly one original view/callback,
one exact nonce/mode, no capture, and a single accept type. Unexpected/late,
malformed and duplicate distinct callbacks receive no file data; old callback
identities are not answered twice. Native metadata errors are cancelled through
the same retained response path. Expose only pending/cleanup state, not the nonce,
callback, raw URI, path or serialized permission.

`begin` requires actual sealed read-lease objects owned by the original queue and
identical scope, an original-owner predicate and a native frame/world verifier.
Clone the source array and retain immutable file specifications. The verifier
must succeed before URI publication and before the native response. A synchronous
success is held until check submission itself returns, so success followed by an
exception grants nothing. Revalidate ownership, native epoch, queue/scope/files
and the original monotonic deadline. The deadline is clamped to the earliest file
expiry, never renewed, and scheduled from elapsed realtime onto the UI uptime
queue. All callbacks/acknowledgements remain on their original UI thread.

A read lease has a once-only picker claim containing a plain native identity,
never a View/Activity/controller. A later operation cannot steal or revoke an
earlier grant, even through another gate with the same numeric epoch. Partial
claim/publication failure revokes only this operation's newly owned permissions.
Native SDK replies mint private provider URIs from those exact leases, not caller
paths/URI strings. The real provider supplies original name/size and header-free
bytes. URI preparation failure that explicitly returned null is a known clean
cancellation; it is distinct from an uncertain native response. Uncertain sends
and completion failures are never replayed and require teardown when the native
view still exists.

Cancellation is itself a native response: defer the completion until its return,
including reentrant close/cancel/destroy and exceptions. Weak original-ID timers
and result callbacks cannot retain a retired UI owner. Cap records at 64 per view
and share the existing 128-live-callback ledger. Lifecycle cancellation/close/
destruction also retire this gate. Generic chooser readiness stays blocked while
the private rendezvous is pending; no broad CDP admission exception is added.

`.mdo/E69-private-picker-final.log` passes 2,046 private callback checks plus 418
existing URI checks. Production queue, file channels, read leases, provider and
proxy are real; Android types, system calls and the native DOM verifier are
controlled doubles. The production Chrome adapter receives the exact original
test callback. Checks include a Unicode/delimiter basename and all 131,075 bytes/
SHA-256, zero-size files, partial publication rollback preserving older grants,
scope/epoch/clock/deadline losses, mismatched metadata, duplicate/late callbacks,
native response/completion/allocation faults, cancellation ACK ordering,
reentrant teardown, thread rejection and both lifetime quotas. This **does not
prove actual Android WebView association, selection, DOM assignment or URI
consumer behavior**. Framework/device/URI-assignment flags remain false.

Regression passes chooser lifetime/Chrome callback 471/42 checks, manual chooser
554 checks, and the device-URI fixture with resolver/FD/looper doubles 168 checks
(`.mdo/E69-chooser-final.log`, `.mdo/E69-manual-chooser.log`,
`.mdo/E69-uri-fixture.log`). JS passes 319/319; final Python passes 167/169 with two
existing symlink skips. The full SDK/D8/DEX/ABI/payload/private-provider/alignment/
v2/v3 signature build passes `.mdo/E69-android-final.log`; use the current complete
development APK/receipt above. Windows and plugin artifacts are unchanged and
reused only after current-source/archive verification.

The next coordinator must freeze the original view/channel/epoch/UI action and
file-source owner; supply actual native parent/graph/world validation; execute
the fixed E68 prepare/open/status/commit/dispose operations; keep its own CDP lease
admitted while fencing ordinary actions; and deliver/recover only the original
operation's results. Nonce extraction stays private, and userGesture is permitted
only for the fixed native private-picker opening. Existing source/input scopes
and read leases cancel/expire within 120 seconds: assigned-data lifetime must be
separated where the common upload contract requires retention across navigation,
cancellation and staging expiry until confirmed release or browser close. Do not
enable upload capability or treat the current gate as a complete upload path
before these joins and retention rules are implemented and verified. The Windows
realm audit, broader intermittent input diagnostics, Android downloads/profile/
activation and installed-provider/main-UI qualification also remain open. No
phone/provider/formal data change, model call, external-page operation or release.

### Native assigned-data retention, separate from transient input (E70)

After the original private callback returns successfully, its result carries a
native-only `PickerGrant` for the exact queue-owned, claimed read-lease objects.
The worker verifies the entire ordered sealed manifest, source scope and hashes
before returning a provisional `Retention`. Neither object has a JSON action or
serialized reconstruction path. Equal numeric navigation values, another queue,
another source proof or another data owner cannot adopt or release that grant.

Preparation remains bound to the original scope and staging deadline. The
future coordinator must freeze one `RetentionOwner` per physical browser
generation, verify the original frame/channel/action and call `adopt()` directly
before possible DOM dispatch. Adoption uses its native monotonic clock and
serializes with transient picker cancellation. Once adopted, file bytes and
provider permissions survive input cancellation, navigation and staging expiry;
the existing file/byte budgets remain charged. A missing/uncertain effect reply
must not replay the assignment or discard these possibly consumed bytes.

Explicit confirmed release, actual browser/data-owner destruction or worker
shutdown revokes permissions and reclaims the original owned leaves. Merely
closing a transient input gate preserves adopted data. Native status/release
can use the original object after its input scope expires, and a successful
cleanup receipt remains inspectable after the worker reaps the ticket. Snapshot
arrays are copied; no live UI/Activity/channel reference is retained in the data
capability. Failed native UI handoff reclaims an unowned provisional grant, while
failed duplicate/status delivery cannot revoke an already owned/adopted grant.
OOM at the post-preparation result boundary follows the same rule.

`.mdo/E70-retention-complete.log` passes the 418 existing URI and 2,046 private
callback checks plus 410 recorded retention assertions. The latter include
concurrent adoption/cancellation, dropped/throwing/refused/late UI completion,
snapshot allocation faults, original-owner/queue rejection, Unicode/delimiter
names, all 131,075 bytes/SHA-256 and a multi-file grant containing an empty file.
The production file queue/provider/proxy are real; Android types/system calls
and DOM verification are doubles. No actual renderer assignment is qualified.
`.mdo/E70-recovery.log` passes 161 checks and 13 actual abrupt JVM exits,
including an adopted file whose input scope is already cancelled. Process
restart discards native authority and reclaims only verified owned cache data.
It does not restore an old provider grant or prove Android framework behavior.

Read/worker regressions pass 318/99 checks; chooser lifetime/Chrome callbacks
471/42, manual chooser 554, resolver/FD fixture 168, upload UI fragment 39.
Sequential JS passes 319/319; Python passes 167/169 with two existing Windows
symlink-privilege skips (`.mdo/E70-node-final.log`, `.mdo/E70-python-full.log`).
Full SDK/D8/DEX/ABI/payload/provider/alignment/v2/v3 signature checks pass
`.mdo/E70-android-build.log`; use the current APK/receipt above. The first JS
attempt in `.mdo/E70-node-full.log` ran while the development service was stopped
and failed with loopback connection refusal; the final complete run used the
restored same deployment. Windows executable and plugin payloads are unchanged.

The retention API is not yet wired to a product upload coordinator or its
physical browser/data-owner lifecycle. Own-operation CDP admission, fixed private
picker opening/selection/DOM handoff/cleanup and original frame/channel/action
proofs still need joining. Android automation/debug admission remains false;
keep installed-provider/main-UI/activation/profile/URI checks unqualified until
device verification is possible. Windows realm/intermittent-input work and
Android downloads remain open. No phone, provider, formal data or release change.

### Fixed private picker steps in the original native world (E71)

`MdoBrowserCdpUpload` now supplies the native UI coordinator with immutable,
worker-only prepare/open/status/commit/dispose steps. Snapshot the original local
file-input ref/document, native frame chain and complete file manifest. Inspect
the actual native node and ancestor upload policy before preparation and inspect
again before giving the UI its private nonce/mode. The nonce is a native `Ready`
object field, never part of the ordinary result, JSON command grammar or Agent
request. No caller supplies a script, activation flag, native context/node ID,
URI or device path to this component.

A native world `Pin` binds the exact world manager, immutable frame route,
original world object and unique context. Every later step uses that original
binding/worker/realm and native parent/chain guards, without creating a world or
adopting another connection. Equal aliases/loader-looking metadata are not a
replacement proof. Wrong-worker/connection attempts cannot mutate a surviving
operation. Execution remains by unique context after the existing negative
capability proof. Only the fixed native `open` expression uses `userGesture:true`;
ordinary frame evaluation, installation, status, commit and cleanup do not.

After the original target is pinned, disarm native chooser event reporting before
opening the private picker, so its event is not a second original page candidate.
The Android callback itself is still a separate UI responsibility. Open and
assignment are once-only; a not-yet-selected input is only a bounded status wait.
Preserve partial synthetic-event handoff results and possible effects after any
lost/invalid reply, cancellation, native clock regression, original deadline,
frame/parent change or world loss. Cleanup ignores expired staging/disabled
ancestors, but still requires the original binding and world: no reconnect,
replacement-world cleanup or invented confirmation is allowed. Its successful
receipt is immutable and idempotent. An unconfirmed cleanup requires the UI
coordinator's explicit reconciliation, not automatic assignment replay.

The final handoff result is also retained as an immutable native `Receipt` before
the worker can lose its completion. The original UI can read a fresh JSON copy
without dispatching anything; later confirmed cleanup updates only its cleanup
flag. A successful handoff followed by worker timeout therefore has one actual
commit and a readable original result. This receipt grants no file permission
and cannot restore a lost owner. The UI must adopt E70's exact data grant before
submitting commit and preserve it when dispatch or reply is uncertain.

`.mdo/E71-native-complete.log` passes the existing real JVM/JNI/strict JSON/
worker/graph/world/owner/input/capture/file/chooser gates, plus 1,530 private
upload-step assertions. These cover five root/local/related iframe routes,
complete and partial results, native-only nonce/gesture, metadata/activation
errors, failed cleanup, pending selection, duplicate effect refusal, wrong
thread/binding, original deadline and clock loss, parent/context changes and
lost assignment replies. Native protocol DOM behavior is synthetic; the original
Android callback, URI consumer and renderer file assignment are **not joined or
qualified** by this test. ASan/UBSan transport gates and Android API 26 compilation
pass. No new runtime dependencies, app/Home/source copies or browser installs.

Actual SDK/D8/DEX/ABI/app payload/private-provider/alignment/v2/v3 signature checks
pass `.mdo/E71-android-build.log`; see the current complete APK above. JS passes
319/319 in 19.819 s and Python 167/169 in 64.158 s, with two existing Windows
symlink-privilege skips (`.mdo/E71-node-full.log`, `.mdo/E71-python-full.log`).
Windows executable and paired plugin remain unchanged after archive verification;
the same headless service is restored.

Next join the original UI view/channel/epoch/action/file-source owner and retained
Android callback to these steps, including the exact own-operation admission
while ordinary input is fenced, early native callback ordering, original chooser
cancellation, data-owner lifecycle and conservative cleanup/lost-reply recovery.
A coordinator should attempt fixed cleanup in the original worker operation
before propagating a step failure when possible: the generic worker retires a
binding on an escaping exception. A replaced/lost realm must never be recreated
to clean up an old operation. Native partial/gone/uncertain cases still require
explicit UI lifetime decisions that preserve possibly consumed file bytes.
Overall Android automation/debug admission stays false. Installed provider/main
UI/profile/activation/URI behavior, Android downloads and Windows realm/input
diagnostics remain open; the user has deferred phone verification.

### Preserve an idle original chooser connection without widening admission (E72)

The old input invalidation observer always closed the CDP binding when an
intercepted native file chooser opened. This discarded the already proved
frame/world/reference/event state before target inspection could continue.
Keep that idle binding only when the exact physical view, native epoch and
original host channel still match; native single/multiple chooser state is clean,
cancelable and not capture/directory; and visibility, loading, dialog, popup,
manual-picker, manual-control and foreground checks all hold. Native pressed
input is still cancelled/released. If a CDP job is running or awaiting UI delivery,
ordinary cancellation still retires its binding. An unknown/pending effect is
not authority for a continuation.

The exact production outer dialog gate and frame router admit only the strict
typed `chooser_list`, `chooser_target` and `chooser_disarm` grammar through this
scope, and only after input cleanup. General frame listing, evaluation, owner
queries, file inspection, capture, input and new chooser arming remain blocked.
An extra script/gesture/socket/raw-node field cannot turn metadata into an effect.
Product/profile admission remains above the gate. No setting, caller flag,
global debugging change or replacement world is introduced.

All three chooser continuations require an already bound original marker, even
after the native callback is cancelled. `MdoBrowserCdpRegistry.existing` accepts
only the exact native view/epoch/channel and never creates a marker. The worker's
`submitExisting` captures the original binding object, checks it again on its
worker and never invokes the connector if it is lost. A missing/retired binding
returns an unavailable/cancelled result; it cannot recreate old candidate state.
Navigation, stop, manual takeover, background, scale/visibility changes, dialog,
popup and physical close retain ordinary invalidation semantics. Cancelling a
clean chooser resumes the same realm without resetting lexical/world quotas.

`.mdo/E72-native-final.log` passes 357 checks against the exact production
admission/invalidation/router fragments, actual native chooser ledger and real
JVM/JNI/CDP worker: five frame routes, stopped pressed input, retained target refs,
read-only fencing, extra-field denial, no reconnect under missing/changed owners,
and cancellation of an in-flight job. Registry/worker additions pass 388/184
checks; existing frame/world/owner/input/capture/file/chooser/private-upload gates
also pass. The Android UI surroundings and protocol DOM are synthetic. This
does **not** associate an Android callback with a CDP event by timing or grant a
URI; `android_callback_frame_association:false`, `files_granted:0`, and framework/
provider/automatic-assignment qualifications remain false.

Fix a test-only socket fault-injection race found during this run: a 30,000-byte
message could require only two grows when its first full 8,192-byte payload batch
rounded capacity (including NUL) to 16,384. Use a 66,000-byte fragmented synthetic
message so all three injected grow failures are reached regardless of socket
header/payload coalescing. No production socket change or relaxed timeout.
Preserve `.mdo/E72-native-initial.log` and the fixture compile correction in
`.mdo/E72-native-review.log`; the final ASan/UBSan/native/API 26 gate passes.

Also include E71's new upload Java file in both reviewed and deployed source
fingerprints; DEX presence alone did not cover a mid-build edit of that file.
The full SDK/D8/DEX/ABI/app payload/private-provider/alignment/v2/v3 signature gate
passes `.mdo/E72-android-build.log` (current APK above). JS passes 319/319 in
20.848 s; Python 167/169 in 61.853 s with the two existing Windows symlink skips
(`.mdo/E72-node-full.log`, `.mdo/E72-python-full.log`). The unchanged Windows
executable and paired plugin are verified/reused, and the same service restored.

Next join the UI upload coordinator to the E69 callback, E70 native file retention
and E71 original-world steps. Preserve early-callback ordering, exact native
operation/source identity, physical data-owner lifetime and conservative
cleanup/gone/lost-reply handling. This read-only scope is not a callback/frame
association or completed upload path. Android automation/debug admission stays
off; installed-provider/main-UI/profile/activation/URI/download and Windows
realm/intermittent-input work remain. No phone/provider/formal data or release
changes, external page operations, model calls or extra app/Home/source copies.

### Original UI upload coordinator core (E73)

`MdoBrowserUploadCoordinator` now joins the original native file queue/scope,
private picker and E71 fixed-world steps. It is an internal object API, not a
public assignment route. The host must still prove exact native view/channel/
epoch/operation ownership and provide the physical browser data owner's lifetime.
The main-interface automatic upload flags remain false.

A sealed source gets a native object claim: another coordinator cannot consume
it or cancel its owner. The claim carries no View/Activity/channel reference or
serializable authority. The coordinator captures immutable original target and
deadline, uses existing-binding-only worker submissions, registers the nonce
callback before opening, and defers an early callback's verifier until the open
operation completes. Only the original frame/world verifier can approve files.
After selection, retain the original callback grant, adopt immediately before a
single commit, and preserve adopted data on cancellation or uncertain delivery.
Do not retry a lost handoff or create another isolated world to recover it.

The coordinator bounds a missing file/provisional-retention UI completion and
source cleanup acknowledgement. An unconfirmed cleanup is reported as such;
a second claimant cannot cancel the original staging job. Native handoff and
cleanup evidence survives lost worker replies. Final UI callback exceptions/OOM
do not revoke adopted data. Data-owner destruction/explicit release still govern
retained files; transient input expiry is not permission to delete them.

The combined actual JVM/JNI/CDP/graph/world/file-worker/native-callback-core gate
passes 426 assertions (`.mdo/E73-native-final.log`): five frame routes, early
callback ordering, duplicate claims, mode/response/completion faults, partial
handoff, cancellation and dropped file/retention replies. Real Unicode-named and
empty file contents remain readable after input cancellation/staging expiry,
and explicit native release returns reservations to zero. Android callback/DOM
surroundings are synthetic. No ContentProvider/installed-WebView assignment is
qualified by this combined fixture. Separate URI/provider/proxy/private-picker/
retention doubles pass 418/2,046/409, read 318, queue 99, staging UI 39 and recovery
161/13 abrupt JVM exits. The new dropped-retention fixture initially advanced its
fake clock between worker cleanup and UI timer registration; preserve the failure
log `.mdo/E73-native-full.log`. Waiting for actual timer registration fixes the
fixture, with no product timeout change. ASan/UBSan/native/API 26 gates pass.

Include the coordinator and immutable result in overlay/fingerprint/DEX checks.
Full SDK/D8/native/app/provider/alignment/signature checks pass
`.mdo/E73-android-build.log` (current APK above); reuse unchanged Windows and paired
plugin artifacts. JS 319/319 and Python 167/169 (two existing Windows symlink skips)
pass `.mdo/E73-node-full.log`/`.mdo/E73-python-full.log`. The same service is restored.
Next implement the product route and physical data owner lifecycle, intercepted
chooser mapping and outstanding platform qualification. Phone remains deferred,
current provider/formal data unchanged, Android automation/debugging disabled.
No model calls, external page actions, online release or extra app/Home copies.

### Physical native browser owns retained file data (E74)

The actual upload host now associates each exact WebView/browser generation with
one native `RetentionOwner`, separate from every transient upload/document scope.
Only the UI registry holds the view. The file worker receives a plain atomic
lifetime flag and cannot retain an Activity, WebView, channel or controller.
Navigation, input cancellation, channel loss and background cancellation of
staging scopes do not close this owner or delete potentially consumed files.

After the original physical view is destroyed, both the individual close/crash
path and complete-host shutdown loop call exact-object retirement. It cancels
remaining transient scopes, closes only that view's retained-data owner and lets
the existing file worker reclaim files/quotas. A stale view close cannot revoke
another view with the same browser key, and a fresh owner cannot revive old file
leases. Limit the registry to eight physical views; reject aliasing and unretired
key replacement. The original SDK close hooks compile in the actual mdo host.

The exact production upload fragment with UI/JSON doubles and real file worker
passes 80 checks (`.mdo/E74-upload-ui-initial.log`). A controlled native grant
adopts actual file bytes under the host owner. Its original scope is cancelled,
navigation rotates, and the next scope retains the same physical data owner.
Actual file reads survive, foreign/stale retirement is harmless, exact physical
retirement reclaims data, and a replacement view receives a distinct owner.
This is not installed Android framework/private-context/URI qualification.

All native transport/JVM/JNI/CDP/world/callback/coordinator gates, ASan/UBSan and
API 26 compile pass `.mdo/E74-native-full.log`. Complete SDK/D8/DEX/native/app/
provider/alignment/signature checks pass `.mdo/E74-android-build.log` (current APK
above). JS 319/319 and Python 167/169 (two existing Windows symlink skips) pass
`.mdo/E74-node-full.log`/`.mdo/E74-python-full.log`; restore the same service.
Next join actual assignment/target admission, ordinary-input fencing and retained
ticket status/release across transient scope loss. Automatic upload and Android
automation/debug admission remain off; phone/provider/main-UI qualification stays
deferred. No model calls, external pages, formal data changes or online release.

### Product target/ticket assignment and retained controls (E75)

The actual `browser-upload-assignment.inc.java` route now joins the native upload
coordinator. A target-bound `begin` freezes the complete local ref/document/frame
chain together with the original transient source, native channel and already
bound marker. Reject caller JSON changes, target removal/addition on an existing
source key and replacement bindings. The native worker still revalidates the
actual graph/world/input and ancestors before any grant or assignment. Plain
staging remains available without pretending to bind a target or grant files.
Records/reservations stay bounded to 64 per physical browser.

| Native operation | Ownership and lifetime |
| --- | --- |
| Target-bound begin | Freeze original target/source/channel/binding; no native picker or file grant yet. |
| Assign | Exact saved target/ticket and live original scope; one coordinator per view, no effect replay. |
| Repeated assign | Return only pending/saved original receipt; never reconnect, reopen or commit again. |
| Status/cancel | Native historical record on the same physical view may outlive navigation/input/channel scope; cancellation keeps adopted data. |
| Confirmed release | Use the saved native retention object, refuse a pending assignment, and permit idempotent release after scope loss. |
| Physical view close | Retire the original data owner; old view events cannot affect a replacement. |

Ordinary commands and the frame router are fenced while the private assignment
is active. Native input invalidation cancels that exact operation. Navigation,
stop, takeover and physical close remain valid cancellation paths. Snapshot
records before native cancellation, which can synchronously reenter teardown.
Queries expose `assignment_ok`/`assignment_error` separately from their own result;
a historical failed assignment must not make successful status/release look like
a failed query. Replies keep their original native channel/view provenance;
a late status response cannot report itself as belonging to a new view.
Receipts identify the original navigation and are marked `receipt_only`.

Fix a confirmed deadline bug separately in commit `dc6766d`: the coordinator used
elapsed realtime but started its native steps on `System.nanoTime`. Pass the same
original clock to the native steps, so device suspend cannot renew their budget.
The queued-worker-expiry regression fails against the previous code (check 340,
`.mdo/E75-clock-before-fix.log`), then passes with no picker opening or file commit.
This is controlled clock regression, not physical Doze qualification.

The exact product-fragment/real JNI/CDP/frame/world/file-queue/native-callback-core
gate passes 129 checks (`.mdo/E75-native-final.log`). It covers five frame routes,
source/target mutation, duplicates, fencing, lost product replies, changed scopes,
retained byte consumption after navigation/reconnect/expiry, explicit release,
reentrant view teardown during native delivery and late metadata. Existing native
gates and ASan/UBSan/API 26 compilation pass; coordinator 444 and read-only chooser
continuation 357 pass. The separate staging-fragment gate stays 80. Android UI/
DOM peers are controlled; this does not verify installed-framework/ContentProvider/
renderer URI assignment. The old staging-only fixture explicitly stubs assignment
hooks, and only the new combined fixture qualifies the actual product fragments.

Initial logs preserve fixture agent/admission/cache/engine-initialization errors
(`.mdo/E75-native-initial.log`, `.mdo/E75-native-review.log`) and the inaccurate
busy reply found in `.mdo/E75-host-focused.log`. Use one unbound synthetic peer
for the actual original host binding. Do not relax the native protocol grammar,
product admission, original-binding requirement or deadlines to make these pass.
Full SDK/D8/DEX/ABI/app/provider/alignment/signing passes
`.mdo/E75-android-final.log` (current APK above). JS 319/319 and Python 167/169
(two existing Windows symlink skips) pass `.mdo/E75-node-full.log` and
`.mdo/E75-python-full.log`; the same service is restored.

The global product/provider qualification remains false, so the main interface
still cannot perform automatic Android assignment. No flag enables that gate.
Intercepted-chooser mapping, clear/directory/download/profile/activation and actual
provider/main-UI/URI delivery remain outstanding. Phone testing remains deferred;
no provider/formal data changes, model calls, external page actions, extra copies
or online release. The unchanged Windows executable and paired plugin are reused.

### Native file-clear primitive and product adapter (E76a/E76b)

The private worker grammar now parses an immutable `file_clear` target using the
same native frame chain, local document and element ref as file inspection. It
is effectful and never qualifies as a read-only chooser continuation. Caller
paths, file arrays, scripts, object IDs and protocol/session selectors are
rejected; the only native assignment here is a fixed empty file list against
the unchanged opaque node on its original physical source.

Before assignment, require the original isolated-world/node proof, native INPUT
description, exact runtime ref/document and enabled original iframe ancestry.
Recheck that ancestry and original context around assignment. A fixed function
in the original unique world uses the native HTMLInputElement FileList getter
to confirm the same connected target is empty. A shadowing wrapper property,
changed ref/document, missing getter, nonempty list or malformed result cannot
confirm success. Object groups are disposed on the original source; unconfirmed
cleanup closes that binding. The worker never replays a failed native clear.

The exact JNI/worker/graph/world/ancestor/command methods pass 287 controlled
checks (`.mdo/E76-native-final.log`/`.json`), including five renderer/frame routes,
navigation/cancellation/expiry before and after the native step, parent policy
changes, wrong realm/node/ref, malformed confirmation, cleanup failure and
immutable request grammar. The actual fixed scripts pass seven combined
inspection/clear tests. DOM/Android peers are controlled; selected-file events
and installed provider/framework behavior remain unqualified.

The product frame router refuses `file_clear` with
`cdp_file_clear_requires_upload_route` before any binding or mutation. Only the
dedicated upload-clear path owns the following mutation reservation and receipt
lifecycle. All existing global/provider gates and the user's deferred-phone /
current-WebView preference remain intact.

`upload.clear` freezes the original physical browser/data owner, input scope,
channel, native marker and exact target. Its key comes from the original shared
UI action. A repeated key only reads its original result; changed arguments
conflict. Keep at most 64 records per physical browser, including released
tombstones, until actual destruction. The bounded single index avoids partial
key/ticket publication; ticket queries scan those same records. No file worker
request, source read, grant or implicit release of prior upload data is added.

The native reservation joins `upload_busy`, so other input, frame work and capture
cannot start during the mutation. Existing input or busy/unclean capture also
refuses admission. Queued cancellation can retire immediately; an already
running worker must drain before its callback releases the reservation. Navigation,
channel replacement, takeover, backgrounding and physical close cancel the
original operation. A late reply cannot become success for a new scope.

Status/cancel by original key and status/confirmed release by ticket are native
record operations. They require no new world or provider qualification and stay
available across transient scope loss. Successful queries report historical
failure separately as `assignment_error`; they cannot replay the failed effect.
After clean cancellation, a fresh observation and explicit new key can operate;
the old key retains its saved failure. An unconfirmed object-group cleanup remains
fenced even if a primary operation failed first. Physical destruction retires
that owner's records without affecting a replacement browser.

The latest full native gate passes 307 core-clear, 234 exact product-clear, 186
worker, 625 inspection, 444 upload-coordinator and 129 product-upload checks
(`.mdo/E76-product-native-final.log`/`.json`). The resume fixture scopes its
private namespace to each connection, requires a different namespace on a new
connection, and tracks inspection/verification per object group rather than
assuming every failed clear reached verification. The actual C broker and shared
JS facade also preserve the Android-style original receipt and cleanup guidance.
JS 331/331 and Python 171/173 pass, with the two existing Windows symlink skips.
The complete E76b APK compiles, aligns, signs and verifies; installed framework,
provider/URI/event behavior and the remaining Android features are unqualified.

### Suspend-inclusive native deadlines (E77)

Private worker admission, protocol sessions, binding/marker waits, isolated-world
setup, chooser leases and standalone upload steps now use Android elapsed realtime.
The upload coordinator keeps its original injected clock. A queued expired job
cannot begin discovery or dispatch input; an expired discovery cannot ask the UI
for a marker. Marker waits check elapsed time before accepting a callback and use
short uptime waits to recheck promptly after resume. A late event-poll packet is
rejected before native delivery; an ordinary idle poll remains a normal empty
result. No connection replacement or action replay is introduced.

The private C transport uses `CLOCK_BOOTTIME`, slices relative `poll` waits to
at most 20 ms and rechecks that original deadline on readiness or interruption.
This prevents in-progress socket/assembly waits from ignoring time in suspend.
See [Android's clock contract](https://developer.android.com/reference/android/os/SystemClock).

Controlled regressions fail against the previous worker, session/poll and C clock
paths. With the fixes, the complete native gate passes 317 socket, 1,141 WebSocket,
4,324 protocol-dispatcher (eight real local sessions), 477 binding, 191 worker,
571 world, 1,682 chooser, 1,532 upload-worker, 444 upload-coordinator, 129 product
upload, 307 core-clear and 234 product-clear checks, plus the other native gates,
ASan/UBSan and ARM64 API 26 compilation (`.mdo/E77-bootclock-native-full.log`/`.json`).
Clock jumps and Android/DOM peers are controlled; real provider, sleep/Doze and
installed UI/URI/event behavior remain unqualified.

Rebuild the same ARM64 output and complete E77 APK with current source/compiler,
ABI/DEX/provider/alignment/v2-v3 signing checks. JS 331/331 and Python 171/173 pass
(two existing Windows symlink privilege skips). Logs are
`.mdo/E77-android-native-build.log`, `.mdo/E77-android-program-build.log`,
`.mdo/E77-node-full.log` and `.mdo/E77-python-full.log`. The Windows W4 app and
paired plugin are unchanged. One source/Home/native output is reused, and the
headless service is restored to HTTP 200. Phone connection and WebView updates
remain deferred; global Android automation stays disabled.

### Original profile confirmation and native retirement (E78)

An automation profile now requires one `setProfile` on the original native
provider followed by that provider's `getProfile().getName()` matching the exact
requested non-default name. Readback happens after setting: inspecting the old
profile first would lock it. Manual views neither inspect nor change their
existing profile. The adapter pins the Profile boundary signature at the same
reviewed Chromium revision and adds no AndroidX/runtime package. See
[Android's profile contract](https://developer.android.com/reference/androidx/webkit/WebViewCompat).

Before installation, a repeated confirmed same-name request is idempotent.
Changing its name or setting a profile after any native world-install attempt
is refused. Missing/malformed/throwing readback, reentrant retirement or native
allocation failure leaves the assignment uncertain: no world/script installation,
another setter or default-profile fallback. Closing the adapter cannot prove
unknown profile state clean; only destruction of its exact original view settles
that native ownership. This does not delete a confirmed persistent profile.

The product create failure handler now owns exactly one native retirement.
Reserve the original view before input/file cancellation or lifecycle removal;
cleanup callbacks cannot create a replacement or retire that same view twice.
A confirmed retirement releases that reservation for a later explicit creation.
If detach/destroy/cleanup fails, expose a sticky `native_view_cleanup_failed`
fence and refuse new creation or reattachment for this native host lifecycle.
An erased UI map is not native release evidence. Repeated and reentrant close
therefore keep `world_cleanup_confirmed:false` while cleanup is unknown/active.
The original chooser-cleanup predicate is preserved as well.

Actual world/document source passes 35/85 checks, with 190 profile and 18 shared
native-budget checks (`.mdo/E78-profile-lifetime.log`, `.mdo/E78-world-current.json`).
The exact product create/bind/close/retire fragments plus real input/lifecycle
cores pass 73 checks (`.mdo/E78-create-overlay-final.log`,
`.mdo/E78-create-current.json`). Cover separate view profiles, readback faults,
manual browsing, one-time retirement, clean explicit recovery, failed cleanup/OOM,
callback reentrance, eight-view quota, and uncertain close receipts. Provider/UI
peers are controlled; actual Android profile/cookie/storage/resource behavior
remains unqualified.

All 112 overlays compose against the pinned baseline using the new read-only
`python tools/apply_host.py --validate`, including while service state is marked
running. Actual apply keeps its stop and previous-hash guards. An older redundant
close-body patch was caught before writes, merged into the authoritative source
and removed. Two integration tests cover non-mutating validation and a broken
anchor (`.mdo/E78-overlay-plan-tests.log`).

The final current-source native gate passes `.mdo/E78-native-current.log`/`.json`
with real JVM/JNI/local protocol and controlled Android/DOM peers, ASan/UBSan and
API 26 compilation. The complete E78 APK is compiled/DEX/manifest/ABI/alignment/
v2-v3-signature verified (`.mdo/E78-android-verified.log`), including actual World,
Failure and Document definitions. JS 331/331 and Python 173/175 pass (the two
existing Windows symlink privilege skips). Source/test/build hashes are checked
against the final artifact; reuse the unchanged E77 ARM64 host, W4 Windows app,
paired plugin and one source/Home. The headless service is restored to HTTP 200.
No device install, WebView update, model call, external page action or online
release. Global Android automation remains disabled; installed provider/main-UI/
URI/event verification and remaining transfer/activation work are still pending.

## Fixed native paused-event flow control (E79a)

The private dispatcher can send exactly one fixed continuation or abort for its
current original `Fetch.requestPaused` event, even while an ordinary RPC waits
for completion. It freezes the source/request/stage before invoking native
callbacks; callback JSON edits cannot retarget it. Continuation includes only
the original request ID; abort adds fixed `Aborted`. Ordinary callback RPC
reentry and caller-supplied wire selectors/overrides remain forbidden.

Control ACKs use distinct command IDs and the original native session. They can
arrive before/after the ordinary result and be drained by a later call/poll.
Native-only read-only receipts retain their first terminal outcome across close,
ownership loss or child retirement. The receipt carries no request/page data or
authority. A known unsupported control can have no effect while an outstanding
ordinary command remains uncertain; the two outcomes are deliberately separate.
Unknown, duplicate or wrong-source replies fail closed without replay.

Limits: 256 pending controls, 4096 lifetime request/stage/source keys, remaining
outer send budget and a separate 5000 ms boot-elapsed ACK deadline. Polling and
ordinary command boundaries cannot reset these bounds. Exact production Java,
strict JSON and actual JNI/local kernel-peer tests cover six request/response
flows, including a peer that withholds its page result until it receives the
control; complete native regressions pass `.mdo/E79-native-full.log` and the
source-hashed `.mdo/E79-native-current.json`. These are protocol/ownership tests,
not an Android provider or download qualification.

This foundation does not enable Fetch interception, debugging, original-body
downloads or public Agent methods. Persistent pre-navigation network ownership,
event pumping, response classification/stream handling, safe storage and
accept/cancel integration still need implementation and qualification. The user
has deferred the phone; preserve their WebView and keep overall automation off.

E79's complete development APK includes this private foundation. Actual receipt
and result DEX definitions are mandatory; a method signature referencing a
missing class is insufficient. The seven packaging-contract tests and all 175
Python regressions pass (173 executed, two existing Windows symlink skips),
`.mdo/E79-apk-contract.log` and `.mdo/E79-python-full.log`. Complete source/ABI/
manifest/DEX/alignment/v2-v3-signature verification passes
`.mdo/E79-android-build.log`. Current source/builder/native/paired-plugin hashes
match their receipts. The same headless service is restored; no installation,
provider change, external page/model action or online publication was performed.

## Separate physical-network target proof (E80a)

Ordinary document execution still invalidates its lease on navigation. A native
owner can separately construct a physical-network marker and prove the exact
original target using the same independent native nonce/unique-context/cleanup/
loader checks. It cannot upgrade an ordinary marker or expose frame execution.
One proof attempt per physical marker prevents implicit reconnect/rebinding.

After proof, the private facade disables Runtime metadata and discards its
context maps; subsequent document contexts do not grant authority or accumulate
over the view lifetime. Only a fixed response-stage Fetch enable, exact native
paused-event controls and bounded event/ACK polling are exposed. Auth handling,
arbitrary wire methods, caller targets and request/header/body rewriting are
absent. A failed enable closes the connection without replay. Original-owner
close, Inspector detach, renderer crash or post-crash reload retire it; a reused
target ID is not permission to recover automatically.

The exact Java/JSON/JNI gate passes 1,978 assertions, with controlled UI and
protocol metadata peers, including document replacement and simultaneous native
continuation ACKs during activation. Full original dispatcher/native-control/
frame/world/upload/file-clear and ASan/UBSan/API 26 gates pass
`.mdo/E80-native-current.log` with source-hashed `.mdo/E80-native-current.json`.
This proves the private connection core only. The product still needs the
physical-owner registry, initial-navigation barrier, persistent event pump,
original body/store and accept/cancel joins. Closing a protocol connection alone
is not proof that a paused response was denied. Actual provider/framework
qualification and global Android automation remain false; the phone stays deferred.

The E80 complete development APK now contains this private facade, confirmed
by its actual DEX definition and current compiler/source/binary receipts
(`.mdo/E80-android-build.log`). The seven packaging contracts explicitly reject
a missing Network definition even if method signatures reference it
(`.mdo/E80-apk-contract.log`). Reuse the same verified ARM64 engine, platform-
neutral W4 app archive and paired plugin; no extra application or Home is
created. The same headless deployment is restored to HTTP 200. No install,
provider change, model/external page action or online release occurs here.

## Cooperative private network owner ledger and event pump (E81a)

The existing protocol worker now hosts eight private physical-network owners in
addition to its eight ordinary jobs. Both include pending UI results in their
quotas; one shared bounded queue admits at most those starts plus a single pump.
Ordinary RPC waits cooperatively service another idle connection in round-robin
order, without thread creation, busy-session reentry or deadline renewal. A
20 ms receive slice retains 250 ms for native event/fixed-control handling.
Readiness waits for original enable and inline control ACKs under the original
queue-inclusive startup deadline.

Each owner reports at most ready then terminal on its original UI callback, with
one coalesced pending runnable. UI cancellation closes the exact connection and
waits for running I/O to drain before reporting terminal/freeing its slot; queued
ready cannot win after cancellation or an observed connection failure. A failed
close remains a sticky reservation with cleanupConfirmed:false. This confirms
protocol cleanup only, not WebView destruction or response denial. UI posting
failure retires the common worker; callback faults retire their own connection.

The current full native gate passes `.mdo/E81-native-full.log` with source-hashed
`.mdo/E81-native-current.json`. The owner gate's 148 assertions use controlled
Android/metadata peers with real Java/JSON/JNI; an additional actual two-socket
WebSocket/JNI pair proves a page command can wait for another connection's
continuation on that same worker, with balanced native handles/FDs/allocations.
Original transport, dispatcher, frames/worlds, uploads and clear regressions
also pass. Actual installed provider/framework and product routing remain
unqualified. Next integrate sufficient native connection capacity, physical
view/profile registration, pre-navigation readiness and original response/body/
store/accept/cancel. No public Agent method or global Android automation is enabled.

E81's process-wide native capacity is now 17 handles including draining:
eight physical network connections, eight document bindings and one shared
proof/retirement overlap. Earlier phase records describing eight transport slots
are historical. Per-message/send/assembly limits are unchanged. The current
kernel-socket gate fills all slots, refuses the next, retains the bound during
draining and verifies exact final cleanup (1,195 assertions, 204 allocations /
204 releases, 64 cancellation cycles). Full native regressions pass
`.mdo/E81-native-capacity.log` with source-hashed `.mdo/E81-native-capacity.json`.

Rebuilt ARM64 engine: 3,547,120 bytes, SHA-256
`d079b2bec8e68661a36921398771b198174f17951282a5958c4d1c0ed5abb4ac`,
verified source/compiler/profile/ABI receipt `.mdo/E81-arm64-native.log`.
The complete APK includes actual network/slot/result/cooperation DEX definitions,
the six original JNI exports and current engine bytes; source/ABI/private-provider/
manifest/alignment/v2-v3-signature verification passes `.mdo/E81-android-build.log`.
Seven packaging contracts pass, and Python passes 173/175 in 57.949 s with only
the two existing Windows symlink privilege skips (`.mdo/E81-python-full.log`).
Current source and artifact hashes match; the same headless service is restored
to HTTP 200. Existing W4 Windows/paired plugin are reused, with one source/Home.
No installation, provider update, formal data change, model/external page action
or online publication. Product view/profile registration, pre-navigation barrier
and original-response/body/store/accept/cancel still need integration; global
Android automation and provider/framework qualification remain false.

## Product physical ownership and first-navigation barrier (E82a)

The reviewed create route now registers the exact native view, confirmed managed
profile document and original host channel before the initial target is sent.
It loads an owned blank document, checks its observed epoch/native URL, starts
the original native binding and waits for enable/control acknowledgements. Only
then does it dispatch the target once. Pending creation cannot navigate another
URL. Duplicate ready and changed channel/epoch/URL never reload. Manual browsing
keeps its existing path; an existing manual view cannot become an intercepted
Agent view by reattachment. Managed popup transport remains unqualified.

Takeover revokes atomic worker authority before chooser/world cancellation.
Channel/background loss, initial stop, native close and destruction are joined
to the same entry; normal document navigation preserves physical ownership.
Protocol drain and view destruction release separate resources. In-progress
retirement fences new view creation and close confirmation. Observation expiry
alone keeps that pending reservation; it is not an irreversible native cleanup
fault. An authoritative late drain can reclaim an unsent startup view without
changing its old uncertain receipt. Actual unconfirmed native close stays a
sticky fault. Created blank views and possibly sent target requests are retained.

The shared C/JS action path receives a bounded native_create failure receipt with
created/navigation uncertainty and protocol/view cleanup flags. Canonical native
protocol reasons survive; provider strings do not. Invalid receipts remain
uncertain. UI guidance distinguishes clean failure before a URL from uncertain
navigation and never automatically retries. Product-fragment tests pass 138
assertions with controlled UI/provider peers; full JS and native-core gates pass
the E82 logs recorded in progress. These do not qualify an installed provider.
Overall Android automation and initial-download-interception admission stay off.
The response callback currently continues original responses; body retention,
download storage/accept/cancel, explicit rearm and managed popup networking still
need implementation/qualification before activation.

## Original paused-response metadata (E83a)

The private dispatcher freezes a bounded immutable classification before an
original Fetch event reaches its native callback. The physical-network facade
can read that snapshot only inside the original event; supplied request IDs,
later JSON edits and retained metadata cannot select/claim a request. The result
contains only kind, canonical reason, HTTP status, declared content length and
E84b's derived untransformed-text-length proof flag.
No URL, method/body, header list, cookie, credential or filename is retained.

Explicit attachments and three known binary media types in successful full-body
Document responses become candidates. Request-stage events, failed responses,
redirect/error statuses, HEAD/CONNECT, 204/205/206 and non-document subresources
are not captured. Candidate metadata must pass bounded token/parameter/quoted
value and integer validation. Duplicate disposition/type/length, invalid values,
overflow and header budgets return an invalid classification, never a body claim.
Limits: 128 headers, 256-character names, 8,192-code-unit values, 65,536 UTF-8
bytes of names/values and 32 parameters per disposition/type. No filename/path
decoding, URL-extension heuristic, MIME sniffing or second request occurs.

The [Fetch protocol](https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/master/pdl/domains/Fetch.pdl)
defines the original event fields and distinguishes response stages and redirect
responses. Classification is a conservative native policy, not proof of every
browser download intent. The current product still continues original events;
stream claim/body storage and accept/cancel must be connected before activation.
The 295-check metadata gate and 1,986-check physical-binding gate use the exact
dispatcher/codec/JNI validator with controlled event/provider peers. They do not
qualify an installed WebView, framework UI or full Android download behavior.

## Original response body permits and sequential reads (E84a)

The private physical-network facade can claim a classified original response
inside its exact paused callback. This fixed inline operation uses the frozen
native request/source; it accepts no supplied wire selector. Each original
event is single-use across claim/continue/abort. The asynchronous ACK must
provide one bounded native stream handle. Duplicate lifetime handles, malformed
replies, mismatched sources and lost ACKs retire the connection without replay.
Only the original root session is supported by this private body path.

An opaque native Body retains that original association, at most eight per
connection. The owning worker reads sequentially with fixed IO.read parameters
and no offset. Chunks are at most 64 KiB, byte budgets at most 64 MiB, and the
unchanged 1,000–600,000 ms boot-elapsed deadline includes claim waiting and all
reads. Canonical base64 or valid UTF-8 is required; unknown result fields,
oversized/ambiguous chunks and invalid EOF flags are rejected. Returned native
buffers must be erased by their caller. Content-Length remains declared
metadata; actual validated byte counts do not claim HTTP representation equality.

EOF alone is insufficient for success. Independently bounded cleanup requires
ACKs for closing the exact stream and aborting its original intercepted request.
The [original stream protocol](https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/master/pdl/domains/Fetch.pdl)
prevents unchanged continuation after claiming. The pinned Chromium interceptor
also accepts aborting the taken original response; this path never reissues its
URL, request body or consumed read. Connection close alone does not confirm
stream closure or request denial. Cancel only revokes admission until that
worker cleanup returns. First terminal receipts retain separate uncertainty,
EOF/validated-byte count and both cleanup flags; late activity cannot rewrite
them. A cancelled/retired source cannot publish another chunk.

Exact Java/JSON/JNI checks and three local kernel-peer sessions cover binary/
Unicode chunks, exact byte boundaries, active capacity, scoped ownership,
callback reentry, UI cancellation, timeouts, malformed/provider replies and
lost read/cleanup ACKs. These do not verify installed-provider behavior. The
product still continues original responses: private cache/storage/hash checks,
automatic idle expiry/service pumping, permission/accept/cancel/list/save joins
and device qualification remain unfinished; overall automation stays disabled.

## Detect native text-stream byte loss before success (E84b)

Pinned Chromium's Fetch handler chooses stream base64 mode from MIME type.
Its [stream pipe](https://raw.githubusercontent.com/chromium/chromium/138.0.7204.0/content/browser/devtools/devtools_stream_pipe.cc)
cuts text by byte count. Its [JSON String8 writer](https://raw.githubusercontent.com/chromium/chromium/138.0.7204.0/third_party/inspector_protocol/crdtp/json.cc)
can skip truncated/invalid UTF-8, so valid returned JSON is insufficient to prove
that the original text bytes survived. Their exact reference hashes are pinned
in cdp-protocol.json. This is source evidence and a controlled wire-boundary
reconstruction, not a reproduction on the deferred installed Android provider.

Keep a constant base64 flag for original payload chunks, with E85b's empty
terminal-EOF exception below. Base64 chunks remain exact
binary data. Non-binary text additionally requires a known Content-Length with
no content transformation or transfer coding, and its complete UTF-8 byte count
must match that original length at EOF. Only ASCII identity coding is admitted
as an explicit identity declaration. A split multibyte character lost by native
serialization now produces a length-mismatch failure; unknown/compressed text
without a usable integrity proof is explicitly unsupported. Both take the
original bounded stream/request cleanup path and never return a successful file
or issue another request. General lossless unknown-length/transformed text needs
a different proven original-body transport or explicit platform degradation;
this guard does not reconstruct bytes the provider has already discarded.

The final exact metadata/body gates pass 306/670 assertions, including three real
local kernel sessions. Controlled text cases include correct Unicode, the split
prefix/suffix loss, absent length, content coding and changed per-stream encoding.
Existing native/frame/world/upload/clear gates remain green. Device/provider and
actual download cache/product UI qualification remain open.

## Nonblocking body service on the original protocol worker (E85a)

The physical-network pump now services fixed body operations without waiting
for their replies. A read uses its original body lifetime, not a new five-second
ordinary-RPC deadline. Requesting a chunk and taking its completed buffer perform
no socket I/O; one bounded send is serviced per body turn. The common worker can
continue other native jobs/connections while a body reply is outstanding.
E84's blocking helpers now use this same asynchronous machine only as diagnostic
convenience; product consumers use requestBodyRead/takeBodyChunk/serviceBodies.

Each body holds at most one unconsumed 64 KiB chunk, including final EOF. The
consumer must take the last chunk before successful cleanup can begin. Idle
expiry and atomic UI cancellation are observed by the existing pump; staged
close/abort operations retain their original five-second cleanup budget.
Pending read replies remain associated with their original rows until both
cleanup ACKs. E85b then retires that exact read; late cancelled data is discarded.
Buffered bytes are erased on cancellation,
expiry, failure or owner close. No UI gate is held over socket send/receive.

855 checks and an actual two-connection JNI/kernel pair verify that the original
read can wait for an independent page command on the same worker without
deadlock. Controlled clocks cover a read beyond five seconds, idle expiry and
the original cleanup deadline. Full native/frame/world/upload/clear gates pass.
These remain transport/worker checks, not installed-provider qualification.
Pinned stream EOF flag behavior and cancellation replies receive the finishing
compatibility pass in E85b below. Cache/product UI joins remain
unfinished; the existing E84 artifact has not yet been replaced at this commit.

## Finish original EOF and cancellation without a stale read wait (E85b)

The pinned stream's Read fast path reports an empty final EOF with binary=false,
even after binary chunks. Accept only that zero-payload terminal exception after
an already-proven binary stream; payload encoding changes and unproven text
remain rejected. The real kernel body fixture now includes separate binary and
Unicode payload chunks followed by this actual empty EOF shape.

After both exact stream-close and original-request-abort ACKs, a cancelled read
need not answer again. Retire its single native root row into a bounded set and
release the body reservation. The set can have at most 4,096 IDs because each
body claim consumes one original lifetime key and can retire only one read.
Monotonic command IDs are never reused. A late root reply is discarded once,
without publishing data or changing the old receipt; a wrong source still fails
closed. Missing cleanup ACKs retain uncertainty and the original timeout fence.
No request/read is replayed and no unrelated pending command can inherit the row.

The 900-check body gate includes three original body sessions, the two-connection
asynchronous pair, and a real kernel cancellation session that answers the old
read only after another page command arrives. It verifies that confirmed cleanup
can complete before that old reply while preserving source/receipt ownership.
Full current native/worker/frame/world/upload/clear gates and seven APK contracts
pass. Cache/product joins and installed-provider/main-UI qualification remain
unfinished; overall automation stays disabled.


## Original-byte download storage (E86a)

`MdoBrowserDownloadFiles` reuses the pinned-directory upload backend with a
separate native purpose, lease, owner marker and immutable pending leaf header.
Limits are 64 MiB/file, 128 MiB reserved payload and 32 leaves. The native maximum
is reserved before creation; successful sealing shrinks its charge to actual
bytes. Crash recovery validates the whole bounded inventory first and accounts
physical payload bytes; unknown entries are preserved before any deletion.

The file worker supplies ordered original chunks. An incremental source SHA-256
must agree with independent flushed-descriptor and reopened-read-only hashes.
Only sealed writers provide hashes or range reads. A successful file seal is
not network completion: the product coordinator must also require the original
BodyResult success, EOF and both cleanup acknowledgements. No filename, path or
source digest is accepted from a webpage. Native Context/UID authority and the
body/file-worker/product join remain outstanding, so Android automation stays
disabled.

The shared leased-file creator now reopens and verifies its unique original
header before admitting the file identity. Merely sampling a fileKey after
CREATE_NEW is insufficient if the pathname was replaced. An unproven replacement
is retained with its reservation; cleanup cannot unlink the foreign file.

`tools/android_download_files_probe.py` exercises actual directory-relative I/O,
63 assertions and abrupt child-JVM recovery in the existing reusable QA output.
It covers both upload/download replacement races, tampering, budgets, zero-byte
completion, unknown inventory and ownership/thread boundaries. Current upload
primitive/recovery/queue/read regressions and the full native gate also pass.
Android private-cache/framework/provider qualification remains false; these
Linux/JVM filesystem results do not qualify the installed WebView or main UI.


## Original body / bounded file-worker handoff (E87a)

`MdoBrowserDownloadQueue` owns one file thread, 32 retained native tickets and
one outstanding buffer per ticket. A ticket consumes one original Body once;
BodyChunk retains source and offset provenance and can be transferred once.
On successful offer the worker owns/erases the buffer. On rejection the caller
still owns it. Polling snapshots and handing off buffers perform no disk I/O.

The worker reserves the original native byte budget, hashes/writes off the
common protocol thread and applies the source's original suspend-inclusive
lifetime. EOF alone produces finishing. Completed requires the same successful
BodyResult, exact byte count, both native cleanup ACKs and independent file
sealing. No caller-supplied IDs, paths, digests or success booleans participate.

Release erases owned storage but remains releasing until the original response
has a terminal receipt. Cancellation/expiry discard partial bytes; their native
source result remains explicit. Close requests never wait on disk; awaitStopped
separately proves actual worker termination. Closed tickets do not imply that
an outstanding original response has already acknowledged cleanup.

The 126-check JNI/strict-codec/original-session/real-files gate verifies these
boundaries, including protocol progress during an actually blocked file opener.
The response peer is controlled; Android Context/UID authority, product actions,
read/save transport and installed provider/UI qualification remain outstanding.
The product still continues original responses and does not activate downloads.


## Hold the original response before acceptance (E88a)

`retainPausedResponse` creates a native Offer only in the exact original root
candidate event. Its snapshot contains bounded metadata, not a wire selector.
At most eight pending offers share the session's 4,096 lifetime request keys.
Retaining consumes the event once but sends no claim, continuation or body read.
The original request, including its POST/body/cookies, is never reconstructed.

Offer.accept/cancel are atomic native decisions without UI socket I/O. The
existing common pump applies accepted limits and claims the original response
later. Its transfer lifetime starts at native dispatch. Permission waiting has
its own original 120-second lifetime; page RPCs and polling do not renew it.
Expired/cancelled offers emit only an exact fixed abort. A known Content-Length
above the requested maximum rejects that acceptance without consuming the offer.
The caller can choose an allowed larger maximum or explicitly cancel.

Only a valid claim ACK returns the original Body. That is acceptance evidence,
not a successful download. BodyResult, both cleanup ACKs and verified cache
bytes are still required. Cancellation during an in-flight claim revokes its
body without reading; uncertain/malformed/lost replies remain explicit.
Physical connection closure does not prove that a held request was aborted.

The full 153-check native-offer gate includes two real JNI/kernel sessions and
strict exact command sequences, plus atomic UI decisions, frozen metadata,
absolute deadlines, cross-event duplicate protection, bounded admission and
fair scheduling. Provider/product activation remains false. Public download
list/accept/cancel/read/save integration and installed Android qualification
remain outstanding; this change alone does not enable Android Browser Use.
