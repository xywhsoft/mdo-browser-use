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

E93 connects download provenance to a physical-target native frame ledger.
Root and natively joined nested frames have independent navigation receipts;
parent navigation/removal revokes unfinished responses, while completed source
bytes may finish independent file validation and remain readable. This joins
E90-E92's real Activity controls, bounded private reads and frozen display
metadata. Separate child-process interception/popup adoption and installed
provider/main-UI qualification remain incomplete; overall admission stays false.

E94 adds bounded user-takeover retirement on the original common worker.
Ordinary frame execution leases are revoked immediately; only fixed original
source cleanup remains admitted until policy disable/close completes. Completed
bytes and known error causes survive this transition. Explicit same-page rearm
and retained popup/download-only ownership are still in progress.

## Complete development host APK (current: E234)

`dist/mdo-browser-use-dev-android-arm64-v8a.apk` now packages the actual mdo
Activity/service, ARM64 engine/command worker, reviewed browser/chooser bridges
and private upload provider. This is a **complete host with the lite tool
edition**, not a tiny diagnostic Activity and not a production release.

Current APK: 6,612,289 bytes, SHA-256
`9b6e2b8594be83697a915ccfb4cc30fdf859ffa1a5cc85c06cd4c7d1f7478f82`.
E234 adds the shared decision-priority UI from E233 to the matching current
Windows app archive. Signing, alignment, private provider, production DEX
definitions and app/native payload checks pass. Java/native sources remain
unchanged; the full658 native entry point is not rerun and physical device
qualification stays false. The following E232 record describes the prior build.
E232 rebuilds the signed APK with the shared ended-resource panel recovery UI
from E231 and the current Windows app archive. It verifies the packaged app and
ARM64 payload, production DEX definitions, signature and alignment. Java/native
sources and the diagnostic APK remain unchanged; full658 is not rerun. The
original recorded manifest changes only for the two panel-manager.js overlays;
frontend20 source/class/library evidence remains exact unchanged-source reuse.
No device installation, provider update or physical input qualification occurs.
E214 shares the original document's live-readable predicate between isolated
world state and navigation page_ready. Root/default realm loss clears both
public readiness fields even when the native document and Activity epoch stay
current. An explicit observation may recover the same navigation without replay.
Actual DEX verifies both status-to-helper calls and the original worker fence,
alongside E213's volatile/atomic publication chain. The
ARM64 engine retains E208's Responses multipart repair and strict Chat media
projection, paired with the same verified Windows archive. This does not qualify
device/provider automation or change the disabled admission gate.
Use the adjacent receipt. It checks the actual DEX classes, exact native/app
payload hashes, ARM64/16 KiB ELF layout, zip alignment, v2/v3 APK signatures and
the compiled private provider attributes. The DEX gate also requires the new
`MdoBrowserCdpFileInput$BeforeClear`, `MdoBrowser$ClearAction` and native
`MdoBrowserCdpSession$ControlReceipt`/`ControlResult` and
`MdoBrowserCdpBinding$Network`, network ledger/slot/result and cooperative
scheduler definitions and the private `MdoBrowserCdpNative$BrowserEndpoint`.
The fixed original-event cleanup metadata `MdoBrowserCdpSession$AttachedTarget`
must also be an actual DEX definition.
E116 also requires the private process broker, its registry/candidate/connector/
release classes and the original physical Network.PopupSource definition.
E117 requires seven actual PopupWorker/interface/request/result/job definitions
for private preparation and original-UI result delivery on the existing worker.
E118 additionally requires the original native-source join and data-only
MdoBrowserPopups.Stop receipt definitions.
All eight matching JNI exports are checked in the actual ARM64 host.
No account, Home, QA page or signing
key is included. The app archive is extracted from the verified matching Windows
artifact and checked against every current reviewed source file; no
application staging copy is created. Android selects its lite edition before
the desktop macro in that platform-neutral source archive.

E197 connects the actual native main-request takeover branch to a bounded
original-owner coordinator. It cancels real in-flight resources, waits for the
independent root command acknowledgement, native detach and source cleanup, and
returns once on the original channel. A physical manual handoff remains true
even when later protocol cleanup fails; it never restores old Agent authority.
Stop, pause, close, elapsed timeout and replaced channel/view/profile cannot
publish stale success or destroy a replacement page. Physical destruction also
retains the original profile until its native destruction acknowledgement.
Assigned original upload bytes survive takeover until actual native view close.
The packaged DEX must define this coordinator, immutable request identity and
previous-profile destruction field alongside all prior native gates. Native
main-branch/JNI tests use controlled Activity/provider peers; fresh shared UI and
Windows tests do not qualify Android framework/provider/device automation.
Overall Android admission remains disabled. E220 implements a bounded post-touch
hover candidate after measuring late tap feedback overwriting hover. Its final
device regression and installation await the user-deferred phone. E219 remains
the last completed main/7-of-8 component evidence. The WebView is unchanged;
guarded host qualification remains incomplete. See [progress](progress.md).

E199 updates the shared browser UI: an ordinary loading live announcement stays
available to accessibility while no longer changing the native viewport size.
Manual/control/error guidance stays visible. The complete APK is rebuilt from
the matching current Windows archive. Through E203, E197's647 JNI modes were reused with133
exact native-input hashes plus verified
equivalence of the changed aggregate overlay's native entries; its new entries
only affect approval and SDK C/state/UI. This is explicit reuse, not a new physical
Android run. Actual DEX definitions, payload/package/signature and old-runtime
gates pass. E200 additionally binds managed approval scope to the original validated
project/session, selects before the response limit and resets reads on selection.
Windows normal-main-UI accessibility now presents the SDK run_id0 prompt with
strict session isolation. Android uses the same shared implementation; it is not
a new Activity/provider/device pass.
E201 additionally closes a drained SDK executor's observed failed tool record
without inventing an Agent turn or model result. Owned metadata/callback lifetimes
and effect uncertainty are preserved. The shared C path is verified in the
Windows source and actual packed executable; matching Android archive inclusion
and APK/DEX checks are not a physical Android execution pass.
Phone/WebView, global admission and provider/device qualification stay unchanged.

E204 fixes the ordinary main-host world scheduler and its native pre-dispatch
boundary. Android uptime excludes deep sleep; elapsed realtime includes it, while
Handler delays themselves use uptime. See the [Android clock contract](https://developer.android.com/reference/android/os/SystemClock).
The main scheduler now uses elapsedRealtime, and timer checks use at most50ms
scheduled uptime slices against the original deadline. They do not renew it or
claim to execute while the device is asleep. A success arriving after that budget
cannot turn into a valid result. If registration already completed/retired the
original request, no native JavaScript call is sent and its unused budget is freed.
Client timeout of sent work retains its native quota until authentic cleanup.

Nine controlled timing/registration cases with27 checks pass, including elapsed
sleep before a late reply, exact deadline equality, pre-deadline success, expiry/
close/navigation during registration, a held provider, an early timer check and
rejected timer rearm. The actual evaluator and main-host clock expression are
used, with fake clocks and foreign provider callbacks. Existing world35/document86/
profile396/native-budget18 remain green. Fresh native647 with134 exact hashes,
frontend20, create850/async77, public integration13 and current APK/DEX/source/
signature/old-runtime gates pass. Public admission is still false; no physical
provider, Activity, sleep/Doze or new device result is inferred. The old standalone
diagnostic Activity's scheduler remains a separate caller to align next.

E205 aligns that independent diagnostic caller. The shared world gate now
extracts the actual main-host and diagnostic scheduler expressions into separate
cohorts in the same output. E205-world-red.json/log preserves three failures in
the old diagnostic cohort while the main-host cohort passes. With the diagnostic
Activity using elapsedRealtime, all18 scenarios/54 control checks pass alongside
world35/document86/profile396/native-budget18. These are controlled clock/provider
contracts, not installed framework or sleep/Doze qualification.

`python tools/build_android_client.py` rebuilds the existing standalone diagnostic
APK without connecting/installing a device. Current output is
`.mdo/host/android-client/mdo-browser-use-dev.apk`,991,635 bytes SHA-256
`3b018230288a2b7ff6ba3d522ac0680f6f9f0cfba59a969e62e03d73a489d2d1`.
E220 includes bounded post-touch hover sequencing; its final-source device
qualification and main-host installation are deferred by the user.
Its actual DEX World.Scheduler.now() instruction invokes elapsedRealtime and the
ordinary-world deadline method is defined. This package is a diagnostic variant;
both artifacts share the development package ID, and device qualification selects
the required variant. The full host has no diagnostic
Activity. At E205, its DEX/application archive/native host bytes were unchanged;
the APK was rebuilt and source/signature-checked because the common source fingerprint
also includes diagnostic inputs. Native647/frontend20/create850 evidence is
exact-input E204 reuse; fresh Python/JS/integration and both build gates do not
enable public Android automation or qualify an installed provider. E212's fresh
checks and state correction are described below.

E163 routes a protected popup's native screenshot fences through its original
attached document and shared connection. The native capture transaction obtains
that session from the product controller; it cannot accidentally discover a new
ordinary target for the child. Page/navigation/channel visibility, chooser
admission, original proof and cancellation are checked before and after each
fence. Closing the opener leaves the independently handed-off child usable.
In-flight read-only fences may finish after a navigation, but their result is
rejected before starting the native raster step. Explicit cancellation, including
cancellation inside an admission callback, cannot submit another fence.
Eight controlled product-route scenarios pass 314 checks. They exercise the real
session/worker/JNI/frame graph with synthetic protocol/UI peers; they do not draw
Android pixels or qualify a provider. The APK definition gate additionally requires
the native capture class, its binding interface and the capture session. See the
current verification scope and remaining gates in [progress](progress.md).

E164 prepares an already handed-off popup as an opener on the existing broker.
The controller retains the original physical Source proof from bootstrap;
registration derives the target from that broker's original native attachment.
The UI and worker check the exact Handoff, lease, document epoch and caller
channel. No ordinary-network lookup, discovery, new connection or replacement
Source proof is used for this path. Existing eight-request limits still apply.

Retiring preparation cancels input and old frame pins. Only an explicit same-page
inventory may restore them, after the original preparation has settled. An
in-flight evaluation preserves its possible-effect result and is never replayed.
Exact source destruction can reconcile a native release only with the original
worker release/delivery receipts and all native dependencies settled; it neither
upgrades a failed Stop nor substitutes for the original protocol detach.
Thirteen E164 controlled cases pass 551 checks. These include actual frame evaluation
through the typed route, cancellation and later reuse, using synthetic protocol/UI
peers. At E164, native touch/key routing, full nested popup adoption, public
Android state integration and installed-provider/device qualification remained open.

E165 connects private protected-popup native input to that original document.
Hover, click, wheel, key, text and focus retain the PopupPage marker/Handoff/
Document proof and use the existing worker's attached-document route. A foreign
proof pair, stale document, hidden view or blocked chooser cannot fall back to
ordinary binding. Scope/geometry preflight runs before native focus; if that
check synchronously cancels its input, the sequencer stops before native effects.
The later native-frame/world/DOM guards remain required. Sixteen controlled
product cases exercise the real sequencer and JNI graph with synthetic input
events/geometry, including CANCEL after DOWN and no old result on a new channel.
They are not installed-provider, Android framework-gesture or device evidence.
Protected upload/navigation/takeover, public state and complete nested adoption
still need integration; public Android automation stays disabled.
The complete native matrix passes 406 popup-worker modes plus the ordinary
input core (318 reported checks), transport/session/frame/capture/upload/download
gates, ASan/UBSan and Android API26 compilation. All 130 native input hashes match
the final sources. Actual DEX checks cover 94 definition groups, including the
typed input proof fields and both guard preflight definitions. These remain
controlled protocol/UI checks; see `.mdo/E165-verification.json` and progress
for the full evidence scope and remaining qualification work.

E166 traces the earlier loading-timeout cleanup difference to source retirement
between scope admission and original frame/request-ledger reads. A private
Closed receipt must match that ledger's original identity and the same source's
closed intent before it becomes a retirement event. Bounded paused-event
retention keeps the original connection/event/attachment checks while the active
command unwinds through its own cancellation guard. It grants no body access or
continuation and does not reopen the wire. Real ledger faults and same-text
ordinary listener errors still fail and retain creation fences.

Six new controlled cases pass for idle/active close races and the two negative
fault cases. The original loading-timeout gate now requires real detach and
eventual Browser-disable cleanup; ten focused repetitions and the full matrix
pass. The full matrix has412 worker modes, ledger tests133 checks and130 verified
input hashes. Actual DEX inspection verifies96 definition groups, including both
data-only ledger receipts. These results resolve the observed controlled close
race; they do not qualify Android framework/provider/device behavior or enable
the public Android adapter. See `.mdo/E166-verification.json`.

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
`.mdo/E116-android-package.log`, `.mdo/E46-device-inventory.json`, the artifact
receipt and `.mdo/E116-python-full.log` (173/175 pass, two existing symlink skips).
The old inventory is historical; no phone reconnection or provider update was
attempted in E106-E116. Native/component evidence is recorded separately below.

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


## Native download controller and cooperative consumer turns (E89)

MdoBrowserCdpDownloads ties a physical Network to original Offer, accepted Body
and guarded file ticket identities. Its immutable records expose pending,
receiving, verifying, cancelling, completed, cancelled and failed states. A
shared metadata budget permits 32 retained records across controllers; the file
queue independently bounds retained payload bytes/slots. No public wire ID,
request URL, credential or payload enters status JSON. Name/MIME values remain
placeholders until frozen display metadata parsing is joined.

The existing physical-network worker invokes a bounded consumer turn before and
after its short poll. The controller requests fixed reads without blocking,
then transfers one source-bound chunk only when its file ticket is writable.
Other physical owners and page RPCs can continue. UI list/get/decisions inspect
atomic snapshots and do no disk or socket waits; confirmed release can settle
from file snapshots after the protocol connection retires.

Disconnect preserves failure receipts instead of dropping identities. Completed
bytes remain retained. If the original Body already has successful EOF, stream
close and request-abort receipts, disconnect permits ongoing independent file
validation to finish. It never treats the source proof alone as a completed
file. Partial responses fail and clean their owned staging data; unknown native
cleanup cannot be reported as confirmed release. Explicit owner close reclaims
its records and requests file retirement separately.

The 174-check actual controller/shared-worker/strict-JNI/real-files gate covers
these transitions and pauses a real readonly file reopen across disconnect.
The old implementation discards that complete source; the corrected one retains
valid bytes and rejects altered payloads. Native source errors survive generic
queue cancellation, and hash/write failures retain cache origins. The file queue
also parks in WAITING when empty or fully retained (130-check queue gate).

The main Activity still continues original events until native Context/UID
service ownership and its lifecycle/query/action routing are joined. Private
read/save, original navigation/popup associations and unsupported text/blob
handling remain open. Android automation/provider/framework qualification stays
false; these controlled-peer desktop JVM results do not qualify the installed
WebView or public main UI.


## Application-owned download control routes (E90)

The actual Activity now forwards original response events and cooperative turns
to the physical-owner controller. A process service retains only the native
Application Context, shares the 32-record budget and file queue, and resolves
cache directories/UID/mode authority on the file worker. The Android SDK build
checks these real implementations; installed Context/UID/private-cache authority
has not yet been qualified on a device.

Native list/get/accept/cancel/release routes are connected. Acceptance checks
strict integer limits before the original response effect. Release replies wait
for the original opaque file/source receipt and exact channel lease. Changed
channels, replaced owners and a bounded five-second cleanup wait cannot produce
success. File-path replacement preserves the foreign file and reports the native
cache failure. Read remains explicitly unsupported pending the private byte-read
adapter. The full download capability and overall automation remain false.

Pending downloads fence subsequent agent mutations. Destroying a view requests
file retirement separately from protocol closure; admission and repeated close
observe outstanding files. Disconnection can still retain verified completed
bytes, while actual view destruction requests reclamation. Application shutdown
cancels response watchers without replying through a replacement channel.

Verification uses exact injected product fragments: creation/lifecycle 155 checks
and native download-host 30 checks, plus the complete source-hashed native,
ASan/UBSan/API26 gates and Python 173/175 passes (two existing Windows symlink
privilege skips). The complete Activity/service APK, actual Runtime/Release DEX,
private provider, six JNI exports, signing and payload identities also pass.
Evidence: `.mdo/E90-create-current.json`, `.mdo/E90-native-current.json`,
`.mdo/E90-python-full.log`, `.mdo/E90-android-build.log` and the adjacent APK
receipt. These controlled native peers and SDK builds are not installed-provider
or main-UI passes. Phone/WebView remain deferred; no installation or publication.


## Private verified download ranges (E91)

The application file queue now accepts four bounded one-shot range receipts,
with at most 65,536 bytes each. Only a fully verified original ticket may be
read. No caller-supplied file path, descriptor, source identity or digest is
accepted. The same file worker alternates range work with source/write/cleanup
work; the UI and common protocol worker never wait for filesystem access.
Original file identity and size are checked on every range. Offset zero and
EOF additionally rehash the original sealed descriptor. The existing save
receiver independently hashes the complete incoming stream before publication;
range metadata alone is not a substitute for that receiver check.

A range is consumed once. Closing it, releasing its file, destroying its owner
or shutting down the queue erases retained bytes. An already running descriptor
read remains accounted until the worker returns. Read-time integrity failure
revokes other retained ranges and invalidates the old completed-file state;
the native cache error remains available instead of returning unverified bytes.

The actual Activity read command returns the bounded id/offset/next/bytes/eof/
sha256/base64 envelope expected by the shared save transport. It observes the
original view and UI channel lease, five-second timeout and failed UI scheduling;
obsolete callbacks cannot reply through a replacement channel. Returned native
arrays are wiped after encoding. Verified retained data remains readable after
protocol disconnect, until explicit release or actual view destruction.

Actual JNI/source/file queue checks now pass 237 assertions, including a blocked
original descriptor read while the protocol continues, release during that read,
buffer erasure, four-slot admission, cross-owner rejection, multiple ranges and
same-size payload tampering. The exact injected product command gate passes 73
checks including changed channel, removed view, stopped host, timeout, watcher
cancellation and scheduler refusal. Full native/ASan/UBSan/API26 gates and Python
173/175 (two existing Windows symlink-privilege skips) pass. The complete QA APK
requires actual Read/DownloadRead DEX definitions and passes payload/signature/
private-provider checks. See `.mdo/E91-native-current.json`,
`.mdo/E91-python-full.log`, `.mdo/E91-android-build.log` and the adjacent receipt.
Full download/automation admission is still false pending original navigation/
popup association, display metadata, other transfer boundaries and installed
provider/main-UI qualification. The deferred phone and WebView are unchanged.


## Frozen advisory download names and media types (E92)

Original paused metadata now retains a sanitized advisory basename and normalized
media type. It never retains request URLs, payloads, arbitrary headers, MIME
parameters or caller-owned JSON, and does not derive filenames from a URL. The
native Offer and each controller snapshot retain the same immutable display
values, including after the original event JSON is changed or discarded.

Prefer a valid unquoted filename* over filename, with strict UTF-8 decoding and
legacy ISO-8859-1 support. Unsupported charsets, malformed escapes and invalid
UTF-8 fall back to filename. Ordinary filename text is not percent-decoded and
plus signs stay literal. Strip path segments, bound the resulting name to
200 UTF-16 units/240 UTF-8 bytes, and use download.bin for unsafe controls,
format characters, reserved device names, invalid basename characters or dot
suffixes. Invalid/unsafe display hints cannot authorize a path or mark content
trusted; explicit destination/root selection and receiver hashing still apply.
Media types are normalized without parameters and bounded to 128 characters.

The implementation uses the advisory-name rules and filename* preference in
[RFC 6266 section 4.3](https://www.rfc-editor.org/rfc/rfc6266.html#section-4.3)
and the extended value encoding/error handling in
[RFC 8187 section 3.2.1](https://www.rfc-editor.org/rfc/rfc8187.html#section-3.2.1).
This is a bounded display subset, not a claim to implement every historical
Content-Disposition variant or infer native download intent from MIME alone.

The exact metadata/parser/native dispatcher gate passes 347 checks, including
Unicode, escaping, quoted semicolons, precedence, legacy encoding, malformed
UTF-8/percent escapes, path stripping, reserved/control names, bounds and original
snapshot immutability. Deferred offers pass 154 checks; file queue 237,
controller 186 and exact product commands 73 remain green. Full current native,
ASan/UBSan/API26, Python 173/175 (two existing symlink-privilege skips) and seven
final APK contracts pass. Evidence: `.mdo/E92-native-current.json`,
`.mdo/E92-python-full.log`, `.mdo/E92-android-build.log` and the current receipt.
The complete APK compiles these actual classes; installed provider/main-UI and
full transfer/automation qualifications remain false. No phone/provider change.


## Physical-target frame provenance across navigation (E93)

The previous download controller excluded child CDP sessions but did not prove
a same-session candidate's native frameId. The corrected physical network facade
uses the frame ID frozen before event callbacks can mutate JSON. Page.enable and
a bounded native frame-tree snapshot precede Fetch.enable. At most eight stable
snapshot attempts share the original startup deadline; an intervening native
frame event causes a reread, not acceptance of a stale tree or replay of input.
A bootstrap failure records that interception was never dispatched.

The ledger follows native parent/attach/detach/commit events independently of
document execution worlds. It admits at most 32 frames and 32 ancestor levels,
with a lifetime budget of 4,096 associations. Root and joined nested frames get
opaque network-only frame/document tokens, with immutable ancestor scopes.
Parent commit/removal revokes descendant tokens; swap evidence alone cannot
requalify a process route. A separate child CDP session is still unqualified for
download interception and does not silently become a root-frame response.

Candidate acceptance and common-worker turns require the record's retained scope.
E109 pins native goto candidates to their original admission document. Other
response_frame records describe the frame at retention, not proof of the request's
initiating document; keep that weaker basis explicit.
Pending or partial stale downloads cancel only their original request/body and
retain native_frame causes and cleanup receipts. A successful original EOF plus
stream-close and request-abort receipts may already exist while independent file
validation runs. Navigation then permits verification to finish, without
promoting source proof alone to completed bytes. Same-size tampering still fails.
Completed retained bytes remain inspectable/readable after navigation; source
current status is independent of file/source cleanup. The metadata exposes only
opaque source_frame/source_document/source_is_root/source_current diagnostics.

Native ledger faults revoke old scopes, use a finite trusted event-code whitelist
and survive the worker and actual Activity error receipt. Arbitrary callback or
provider messages remain sanitized. The marker lease now owns the entire network
facade, so UI teardown also immediately revokes frame provenance without iterating
worker-owned frame maps from another thread. No document execution/input route
is granted by network ancestry.

Relevant protocol fields are the original
[Fetch.requestPaused frameId](https://chromedevtools.github.io/devtools-protocol/tot/Fetch/#event-requestPaused)
and native [Page frame events](https://chromedevtools.github.io/devtools-protocol/tot/Page/#event-frameAttached).
URL/title equality is not a provenance proof in this implementation.

Verification: metadata 350, native ancestry 50, physical binding 2,022, common
network worker 195, actual download controller/files 275, exact download commands
73 and product creation/lifecycle 159 checks pass. Include snapshot races,
unproved/missing frames, caller-JSON mutation, nested parents, limits, physical
close, pending/partial cancellation and paused real file validation across
navigation with valid/tampered data. Full native/ASan/UBSan/API26 and Python
173/175 (two existing symlink-privilege skips) pass. The complete APK requires the
actual NetworkFrames/Origin/Frame/Scope DEX implementations; final payload,
private-provider, six-JNI, alignment/signing and seven APK contracts pass.
Evidence: `.mdo/E93-native-current.json`, `.mdo/E93-create-current.json`,
`.mdo/E93-python-full.log`, `.mdo/E93-android-build.log` and the adjacent receipt.
These are controlled native/JVM/filesystem and SDK build checks, not an installed
provider/framework/main-UI pass. Phone/WebView remain deferred and automation
qualification remains false.


## Orderly takeover retirement and independent cleanup receipts (E94)

Takeover first invalidates the ordinary document/frame marker and cancels pending
UI input, chooser, popup and capture work. The original physical network scope
remains admitted only for retirement. The UI immediately records manual control
and a draining phase; neither a second takeover nor polling restarts its deadline.
Manual page use can continue without a URL reload or replay of prior input.

The existing common worker cancels retained original offers and unfinished body
permits, continues future manual responses without capturing new Agent downloads,
and drains fixed source/stream cleanup ACKs. No new offer, body claim or body read
is admitted on a retiring network facade. Once all original source/control slots
are drained, issue Fetch.disable once and observe its reply before socket close.
The five-second retirement deadline and bounded control wait are separate from
the UI's 5.5-second fallback observation. Missing/rejected replies never imply
responses_disabled; native protocol close is a separate cleanup receipt. The
Fetch lifecycle constraints are defined in the primary
[CDP Fetch protocol](https://github.com/ChromeDevTools/devtools-protocol/blob/master/pdl/domains/Fetch.pdl).

A complete source may still be undergoing independent file verification. It is
not cancelled merely because the user takes over: valid bytes finish and altered
bytes fail their hash. Completed files remain readable/releasable after network
retirement. Partial/pending records get actual cancelled receipts only after
original cleanup proof; an unconfirmed source cannot be released as clean.

A targeted cache-open fault reproduced two layers of cause masking: takeover
changed a prior storage failure to user cancellation, and final disconnect could
replace it with generic native body cancellation. Preserve the prior cache,
timeout, frame or policy cause through cleanup; a later user decision does not
erase the original failure. Evidence includes
`.mdo/E94-prior-cache-failure-repro.log`, the native-mask intermediate log and
`.mdo/E94-prior-cache-failure-fixed.log`. The factory fault is controlled; this
is not an installed Android filesystem failure reproduction.

Exact retirement/common-worker/native source/real guarded-file checks pass 58,
including empty, pending, partial, completed and genuinely blocked validation
cases, altered bytes, late/manual responses, lost disable ACKs, original-source
timeouts and prior failure preservation. The product create/takeover/lifecycle
fragments pass 175 checks, including immediate ordinary frame-lease revocation,
manual cleanup-only authority, repeated calls, no reload/destroy, bounded fallback
and honest disabled-state reporting. Full current native/ASan/UBSan/API26,
Python 173/175 (two existing symlink privilege skips) and seven final APK
contracts pass. See `.mdo/E94-native-current.json`, `.mdo/E94-create-current.json`,
`.mdo/E94-python-full.log`, `.mdo/E94-android-build.log` and the current receipt.

At E94, returning control kept manual state with network_retiring or
network_rearm_required. E95 below replaces the latter with an explicit fresh
proof transaction; a retired connection is never silently reused. Popup/
cross-process and installed provider/main-UI qualification remain open. Phone/
WebView are unchanged, and overall download/automation qualification stays false.


## Explicit same-page return-control with retained download generations (E95)

Human release on the original owned page now starts an asynchronous rearming
phase. Require confirmed old native cleanup, clean retained source receipts,
the original assigned profile/channel and a stable loaded page. Keep manual
input/document blocking until a fresh physical marker, native frame bootstrap
and Fetch.enable acknowledgment complete. The same WebView, page, profile and
download controller remain; no URL is loaded and no previous input or request
is reconstructed. Navigation, channel/profile loss, startup failure or the
original fifteen-second deadline leave the page manual, without page destruction
or a closed-page flag. Agent release cannot end takeover. Repeated release does
not mint another connection or reset the outstanding deadline.

Each connection receives an opaque callback ticket. Old events/turns cannot act
on the replacement native connection; a late old disconnect cannot retire it.
Rearm requires a terminal native cleanup receipt even if no initial protocol
turn ever ran. Retained rows keep their original source authority, IDs, sequence,
private file tickets and shared 32-record budget. Old-generation turns only
summarize file progress and cannot read old streams through the new connection.
Completed data and independently verifying complete sources survive; altered
files still fail. Already pending private reads/releases follow the exact
physical view and controller across connection replacement, preserving their
original channel lease, deadline and cleanup receipts.

Rearm is UI-owner-only and bounded to 4,096 generations per controller. Reuse
the existing physical owner's lexical quota conservatively, including after
manual navigation; release never resets the sixteen marker-declaration limit
or closes the quota while the actual view survives. Exhaustion returns a native
failure with manual state retained, with no hidden reload workaround.

The actual same-view/controller/common-worker/JNI/guarded-file gate passes 111
checks, including stale tickets, in-progress verification, same-size tampering,
old source cleanup refusal, shared admission limits and sixteen successive
same-DOM proofs followed by a refused seventeenth. Exact product commands pass
81 checks, including a genuinely blocked file reader and queued release across
replacement. Product creation/takeover/rearm/lifecycle fragments pass 237 with
failure, navigation, channel, scheduler and timeout cases. Complete native/
ASan/UBSan/API26 and Python gates pass; Python 173/175 with only two existing
Windows symlink-privilege skips. All 99 native and 11 product input hashes match.

The full QA Activity/service/lite-tools APK is rebuilt and verifies actual DEX
definitions, including the connection ticket, reviewed source/native/app
identity, private provider, six JNI exports, ARM64/16 KiB layout and v2-v3
signing. Seven final APK contracts pass. Evidence is in `.mdo/E95-native-current.json`,
`.mdo/E95-create-current.json`, `.mdo/E95-python-full.log`,
`.mdo/E95-android-build.log` and `.mdo/E95-final-identities.log`. These remain
controlled SDK/JVM/native/file checks, not installed Android qualification.
Phone/WebView stay deferred; full download and automation admission stay false.


## Original root navigation acknowledgments and download classification (E96)

Add a fixed native Page.navigate primitive to the original physical network
facade. The binding supplies its proven root frame; callers supply only a
bounded HTTP(S)/about:blank URL and deadline, with no frame/session selectors,
headers or request reconstruction. One original asynchronous navigation can
coexist with the ordinary RPC and original Fetch/body machine. Paused sources
and stream/request cleanup progress on that same connection before navigation
acknowledgment, avoiding a blocking wait between those operations.

The opaque receipt distinguishes native acknowledgment, acceptance/failure,
known/unknown download classification and possible dispatched effects. Only
the original typed isDownload flag classifies a download; abort text, missing
loader, URL and attachment candidates do not. A native ACK proves neither page
load/commit nor file completion. Parse and validate the expected original frame
and optional loader/download/error types, then retain only bounded result flags,
fixed diagnoses and a finite allowlist of 22 known net error identifiers.
This preserves DNS/TLS/connection causes without returning provider prose or
URL-bearing error text. Invalid/mismatched replies retire the connection.

There is one pending navigation per session and at most 4,096 lifetime issues.
The configurable original deadline is 100..600,000 ms; short polls/other calls
never renew it. Command sending remains bounded to 100 ms. Close, lost replies,
partial sends and timeout preserve uncertain effects; terminal outcomes cannot
be upgraded by late replies, and no command is automatically retried. A known
unsupported-method reply records no dispatched navigation effect. Retirement
also observes the outstanding original navigation acknowledgment.

Primary-source review found an explicit compatibility boundary: Chromium
138.0.7204.0's Page.navigate result lacks isDownload. Current protocol makes it
optional. A missing flag with ERR_ABORTED returns
cdp_navigation_download_unproven, retaining the safe native error category.
Ordinary WebView error callbacks filter several abort cases, including stop,
embedder interception and no-content handling; these are not download proof.
Pinned sources and decoded hashes are recorded in
`host/android/cdp-protocol.json`, including the current
[Page definition](https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/a45ae55ca46eeb4c6768011da4470ec0f103d01c/pdl/domains/Page.pdl)
and the [older complete protocol](https://chromium.googlesource.com/chromium/src/+/138.0.7204.0/third_party/blink/public/devtools_protocol/browser_protocol.pdl).

The actual dispatcher/codec gate passes 149 checks and two original local-kernel
JNI/WebSocket sessions. One real socket pipeline withholds the navigation reply
until original body/stream/request cleanup finishes on the same connection;
another delivers the original DNS failure while an ordinary RPC is outstanding.
Both end with balanced native allocation/descriptor checks. Controlled metadata
tests cover absent/false/true flags, errors, wrong frames/children/types, pending
and lifetime limits, worker/event reentry, partial sends, close races, clock
reversal and original ten-minute deadlines. Native physical binding passes
2,040 checks, including root-only dispatch and retirement admission. Existing
rearm/download/worker regressions, ASan/UBSan/API26 and Python pass; all 101 native
and 11 creation hashes match. Full QA APK DEX requires both Navigation and
NavigationResult definitions, and seven final APK contracts pass.

This stage packages the native primitive. The product coordinator still needs
to join exact UI request ownership, original native acknowledgments and download
state before replacing the legacy WebView navigation route. Existing main-UI
error handling is not bypassed. `product_navigation_activated`, device/framework
and overall automation qualifications remain false. Phone/current WebView stay
deferred; installed-provider and public JS/C end-to-end checks remain required.


## Managed product navigation admission and original outcome joining (E97)

For an armed physical owner, the actual product goto route now queues one fixed
native Page.navigate and immediately returns a navigation_ref. State exposes
only the opaque reference, queued/sent/acknowledged/failed phase, owner revocation,
dispatched status and typed bounded result flags. The common native protocol
worker continues Fetch, source-body and other-view work while the original ACK
is pending. No UI socket I/O, second executor or implicit loadUrl replay is added.

One original timeout covers UI admission and native dispatch. Queued cancellation
removes the URL and records no effect; once sending starts, cancellation cannot
claim no effect or erase the original native receipt. An exact private
navigation_cancel cleanup route is usable across human takeover, but requires the
same original alias/channel and cannot stop, reload or retarget the current page.
Human takeover releases the original UI reservation so manual navigation remains
available even while the native ACK is pending; old outcomes cannot restore the
manually opened page. The shared JS dispatcher uses exact cleanup on cancellation,
context loss or uncertain exit.
Incomplete dialog/download/chooser checkpoints retain the pending operation for
an explicit follow-up decision instead of poisoning it with automatic cleanup.

The original pre-navigation reservation includes physical view, epoch, profile
and channel ownership. Only its exact terminal result may restore the unchanged
original page's loading/input fence. Unknown dispatched effects remain fenced;
old callbacks cannot overwrite later page state. A known same-document receipt
(download false, no loader) can settle without inventing a new document epoch.
Missing classifier/loader metadata stays uncertain. A known download returns new
retained records as browser-scope candidates, not as proven per-request matches.
Completed files retained from earlier calls are excluded from that candidate set.

Native broker/controller/file regression passes 27 assertions, the lower original
navigation gate passes 156 (two actual local JNI/socket sessions), and 335 actual
product-fragment lifecycle assertions cover stop/takeover/channel/new-page and
exact-reference cleanup. All 338 JS tests and 173/175 Python cases pass, with two
existing Windows symlink-privilege skips. Native ASan/UBSan/API26 and all 103 native
plus 13 product input hashes pass. Full Windows packaging passes 12 actual
C/JS/native runtime checks; the complete Android APK contains the actual new
broker/UI definitions, matching source/archive identities and retained signer.
See .mdo/E97-final-identities.log and docs/progress.md for exact artifact evidence.

Initial target creation and history/reload remain on their legacy route. This
stage does not qualify installed Android WebView/main UI, exact download-to-request
association or the complete public JS/C path. Device/framework/automation flags
remain false, and phone/current WebView remain deferred. Continue the remaining
navigation, transfer-lifetime and public-device work without upgrading the provider
or replacing formal application data.


## Initial target admission through the original coordinator (E98)

After native blank warmup and original Fetch activation, managed initial HTTP(S)
targets use the same original Page.navigate coordinator as goto. An about:blank
target reuses the warmup and does not issue a second navigation. Creation returns
created/downloads_intercepted plus navigation_ref/initial_navigation_pending;
these acknowledge physical creation and queue admission, not page or file
completion. Manual/unmanaged browsing retains its existing route and unsupported
provider handling; managed popup network admission is still unqualified.

The initial native ticket receives the remainder of the original 15-second
startup budget. Epoch/profile/channel ownership is rechecked after input cleanup
and after setting the fence, before queueing. Reentrant scope loss cannot issue
the original target or clear a newer reservation. Cancellation after display loss
uses only its valid original reference, is bounded on the JS side, and never
closes or stops a later user's view. Native retirement obtains cleanup-only
authority before releasing the original UI reservation on human takeover.

The initial pending state requires original native outcome and actual target
transition. Late blank completion and early page callbacks cannot invent the
missing half. In particular, a non-download result without loader metadata must
not release the initial blank reservation as though it were ordinary same-
document navigation. A dedicated regression reproduces that false completion
and verifies waiting until a real target-page event. Known download, original
failure and uncertain dispatched effects remain distinct. Native causes are
retained ahead of generic page-error text in shared JS; malformed creation
receipts remain uncertain and never trigger an automatic target retry.

The final exact product gate passes 383 assertions, all 343 JS cases pass, and
full native ASan/UBSan/API26 gates pass with 103 native/13 product input hashes
matching the final source. Python passes 173/175 with two existing Windows
symlink-privilege skips; the final Java regression separately passes the product
gate. Windows passes 12 actual packed C/JS/native runtime checks. The final full
QA Android APK verifies actual definitions, private provider, source/archive
identity, retained signer and seven package contracts. Exact hashes and local
evidence locations are recorded in docs/progress.md and
.mdo/E98-final-identities.log.

This is compiled/source-controlled route coverage, not installed Android
provider/main-UI or full public JS/C qualification. Device/framework/automation
flags remain false. Page completion currently joins the original ACK to retained
UI reservation/epoch state; exact native loader-to-page association remains
unqualified. History/reload, exact download association, uncertain-outcome
UI presentation, remaining transfer lifetime/public save and other documented
full-goal work are still open. Phone/current WebView remain deferred.


## Original native loader and committed-root association (E99)

Privately retain Page.navigate's validated loader in its original native receipt.
A physical-network wrapper adds exact owner identity and the pre-dispatch native
frame revision. Its ledger retains at most 32 validated root frameNavigated
commits. Match root plus loader only after that revision; a child event,
documentOpened event, pre-admission observation, foreign network wrapper or
evicted record cannot grant proof. ACK/event reordering can establish a past
commit even when its origin has already been superseded. The public state carries
only opaque native document identity, commit/current flags and fixed outcomes.

This follows the pinned
[primary Page definition](https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/a45ae55ca46eeb4c6768011da4470ec0f103d01c/pdl/domains/Page.pdl):
loader metadata identifies cross-document navigation, frameNavigated associates
the frame with the new loader, and documentOpened is a separate event. The
original ACK proves neither full load nor file completion. Private history and
raw loader values are bounded and remain in memory; no raw wire ID, complete
reply or request reconstruction is returned to the model.

UI admission joins this native origin to its retained reservation/epoch. Commit
waits share the original budget. Missing initial loader evidence is explicit
commit_unproven; lost current proof is commit_revoked, with no invented claim
that the physical page necessarily navigated. Closing its native source also
revokes proof. Preserve the original ACK and diagnostic alias. A completed initial
join stays completed through later ordinary page activity, and an old watch cannot
mutate a new explicit operation. Shared JS validates and checks the same original
opaque identity before/after observation, without any retry, stop or reload.

The lower native gate passes 166 checks including two original body/ACK sessions
and two real local JNI/socket commit sessions in opposite orders. Broker passes
86, physical binding 2,058, and exact product lifecycle 397. Native allocation,
per-session descriptor and socket-slot checks remain strict. JVM UUID's first
entropy descriptors are measured separately (0 to 2) before that session baseline;
this fixes test startup accounting and does not claim a production leak repair.
All 344 JS cases pass, Python passes 173/175 with two existing symlink-privilege
skips, and all 103 native/13 product hashes match. Full native ASan/UBSan/API26,
12 actual packed Windows runtime checks and seven final Android package contracts
pass. Both programs are refreshed; see docs/progress.md and
.mdo/E99-final-identities.log for their exact identities and evidence.

This stage proves the private original commit association and client status
checks. FrameDriver's separate evaluation connection still needs a direct
original-loader guard; two event-stream snapshots do not alone prove every
observed payload's identity. Same-document event correlation, history/reload,
exact download association, remaining transfers/public save and full public
Android verification remain open. Device/framework/automation qualification is
false. Phone and current WebView remain deferred.

## Original-loader guard on the reading connection (E100)

Cross-document `navigate_to` now constrains its final FrameDriver observation to
the original native navigation ticket. This closes E99's separate-connection gap
for that path. The native command grammar accepts a bounded opaque `nv-` reference
only on inventory/evaluation; callers cannot supply a physical target, loader or
CDP session. FrameDriver sends the reference on all native observation subreads,
including final tree revalidation, without exposing it in page script arguments.

The UI resolves its exact current navigation under the original view, channel,
profile, transition and manual-control fences. Only the native ticket crosses to
the existing shared worker. The worker checks the original native commit and the
reading binding's discovered physical target, frame and loader before the page
operation and after it, including a fresh native root-tree check. The final UI
delivery revalidates the same original record. A different target cannot borrow
matching frame/loader strings. Pre-operation rejection reports no page evaluation;
post-operation rejection clears bytes and retains evaluation uncertainty.

The reflection-based main-document adapter cannot establish that CDP loader proof
and explicitly rejects constrained reads. The dispatcher cannot fall back to an
unconstrained script or keep repeating a failed constrained observation. Ordinary
later observations are not pinned to an old completed goto. Initial-create
observation and same-document navigation still need their own event association;
this stage does not claim those paths are guarded.

Native broker assertions increase to 160 and native grammar to 61. Tests cover
foreign target identity, wrong loader, post-read replacement, cancellation, source
closure, payload clearing and no-evaluation rejection. Exact product lifecycle
passes 432 with 35 new UI proof checks; all 349 JS cases pass and Python passes
173/175 with two existing Windows symlink-privilege skips. Full native sanitizer/
API26 and input-hash checks, all 12 packed Windows runtime checks, actual APK DEX
definitions and seven package contracts pass. Both development programs are
refreshed; exact identities are in docs/progress.md and
.mdo/E100-final-identities.log. The original native engine and paired plugin remain
unchanged. Installed Android/provider/main-UI/public JS-C end-to-end verification
is deferred; device/framework/automation flags remain false.

## Initial-create observation carries its original receipt (E101)

Initial `observe` and `wait_for` now use the same original-loader guard as E100's
cross-document goto observation. The workbench retains the opaque navigation
reference returned by its validated create call in the original action context.
It passes that reference directly into the dispatcher, covering page replacement
before even the first native state read. An action that attaches during readiness
also pins its original record and does not adopt a replacement while waiting.

Before reading, during each FrameDriver subread and before returning the payload,
the original record must still identify the committed native document and UI
navigation. A changed/missing/revoked record or proof discards the payload. The
legacy document adapter remains explicitly unsupported for constrained reads.
Replacements are checked before reporting their failures, preventing another
navigation's error from being assigned to the original. Cancellation or manual
control stops reading; read cancellation does not revoke an independent create
operation. A later explicit observation after ordinary navigation reads the current
page without inheriting an old action's startup constraint.

An initial download ACK returns only a download boundary with explicit browser-
scope association, or reports missing identity. It cannot produce a successful
blank-page observation. Exact request/download association is still pending.
Tests couple the actual dispatcher, frame coordinator and page helper, and execute
the exact reviewed workbench admission/routing projection with the native-create
helper. Successful creation and pre-first-read/during-read replacement, missing
records, native refusal, legacy fallback, cancellation and fresh later reads are
covered with controlled native/UI peers. The actual Android framework/provider is
not qualified by these tests.

All 362 JS cases, full native sanitizer/API26 and source-hash gates, 432 product
lifecycle assertions, 12 actual packed Windows runtime checks and seven final APK
contracts pass. Python passes 173/175 with two existing Windows privilege skips.
Both development programs are refreshed; exact identities and evidence are in
docs/progress.md and .mdo/E101-final-identities.log. Same-document event correlation,
history/reload, exact downloads and remaining full-goal work remain open. Installed
Android/provider/main-UI/public JS-C qualification remains deferred; all three
qualification flags stay false. The phone and installed WebView were not changed.

## Same-document event and destination proof (E102)

A successful same-document goto now needs its original typed non-download ACK,
a matching root within-document destination event after the worker admission
revision, and the unchanged original native document/loader. The private history
remains bounded to 32 entries and holds destination equality digests rather than
URL text. A separate position scope prevents later fragment/history API movement
from reusing the original same-document proof. The ordinary download document
origin remains valid until the actual document changes. Document.open with a
reused loader does not count as the original document.

Before and after reading, the separate document connection must also match the
actual root destination. Native `url` and optional `urlFragment` are combined only
when unambiguous; malformed/missing/conflicting metadata is not completion proof.
The UI epoch must still belong to the original same-document reservation. The
client waits for proof, guards its observation and cannot use a legacy page-script
fallback. Input URLs are canonical HTTP(S) before admission, with credentials and
encoded over-limit destinations rejected without input effects. A later event
cannot resurrect an already retained revoked proof.

The pinned Page definition states the same-document loader/event/fragment shape:
https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/a45ae55ca46eeb4c6768011da4470ec0f103d01c/pdl/domains/Page.pdl.
Chromium's PageHandler::DispatchNavigateCallback, inspected on 2026-10-31, supplies
IsDownload() as the native flag:
https://raw.githubusercontent.com/chromium/chromium/main/content/browser/devtools/protocol/page_handler.cc.
That current implementation is reference evidence, not an installed-provider pass.

Full native ASan/UBSan/API26 checks pass, including 370 navigation, 66 ancestry/
position, 63 grammar and 191 worker assertions. Product lifecycle passes 450;
all 364 JS cases pass and Python passes 173/175 with two existing privilege skips.
Final native/product input hashes, seven APK contracts and 12 actual packed Windows
runtime checks pass. A controlled Wire cleanup race found during testing is fixed
with atomic queue removal; zero-pending cleanup assertions remain unchanged and
the failed log/root are preserved. Both development programs are refreshed; see
docs/progress.md and .mdo/E102-final-identities.log for identities and evidence.

Missing-event same-location/no-op behavior, native history/reload, precise download
association and remaining full-goal work still need completion. Installed Android/
provider/main-UI/public JS-C verification remains deferred. Device/framework/
automation qualification stays false; the phone and installed WebView are unchanged.

## Original request/download association (E106)

The private adapter now joins the original typed goto download ACK/loader,
root-Document Network request identity and the retained Fetch response's frozen
networkId. It exposes an opaque nr reference, never caller-selected wire IDs.
The metadata ledger stores no request URL, headers or body. It is bounded to
4,096 request identities, 32 redirects per request and 32 deferred response
links. Malformed/rebound/ambiguous metadata disables new unproven associations;
it does not replay a request, approve a body or abandon transfer cleanup.

Metadata can arrive after the paused response or after its body completes. A
retained original response may then acquire its reference when the original ACK
and loader prove it. Shared JS/C results select one matching response and report
source_association:original_navigation. Other concurrent downloads cannot mask
that result. Before the ACK, the existing pending permission boundary remains
explicitly browser_scope; requiring exact attribution there could prevent the
decision needed to drain the original response and obtain its ACK. History and
reload ACKs without loader proof cannot claim an exact download association.

Five controlled ordering cases, a real native socket/JNI/JSON download sequence,
105 request-ledger assertions, 595 broker assertions and complete packaging checks
pass. These are component/packaging results; installed Android/provider/main-UI
qualification remains deferred. Late responses first retained after a newer
navigation replaces the captured command may remain unassociated. Full transfer
and broader goal work remains open. E103-E105 history/control progress is recorded
in docs/progress.md; earlier pending notes above are historical snapshots.

## Late responses after another navigation (E107)

The private request ledger now retains 32 original goto command candidates.
At response retention it freezes that set rather than looking up the last
navigation. An old response first arriving after a newer command can still prove
its original root/loader/ACK. New commands cannot be attached retroactively.
Unknown eligible ACKs or conflicting matches cannot select a winner. Retained
links survive command-history eviction; a response first seen after its original
command is evicted may remain unassociated. This improves provenance without
granting body permission or replaying navigation.

Request/broker assertions pass 132/622, including metadata before/after the new
command and after response retention, frozen candidates and history limits.
Full native gates and the actual refreshed APK pass; current evidence is in
.mdo/E107-final-identities.log. Product/provider/main-UI qualification remains
deferred and public Android admission is still disabled.

## Unchanged-location goto proof (E108)

An exact unchanged-location goto can now qualify without fabricating a missing
within-document event. Before dispatch, retain the original known native URL
equality digest and unchanged document/position under a quiet root. After its
original explicit non-download/no-loader ACK, require one fresh matching native
frame/loader/location snapshot while that original scope remains current.
The existing same-document event proof still takes priority when available.

This is stricter than comparing the toolbar URL after an ACK. Absent or malformed
prior metadata, unreachable pages, a prior unresolved start, a new start, changed
position/document/loader or scope loss cannot establish this proof. Movement away
and back cannot revive the old scope. Root location storage is one SHA-256 digest,
independent of retained commit digests; no URL, script or body is retained for it.
There is no navigation retry, injected script/input, reload shortcut or relaxed
initial-create transition. DOM readiness remains a separate shared JS/C check.

The broker covers 26 normal/adversarial scenarios and passes 817 assertions,
including an ordinary or within-document navigation followed by the unchanged
goto, original read guards and cancellation. Full native gates and the actual
refreshed APK pass. These remain controlled component/packaging results; installed
Android/provider/main-UI qualification is deferred and public admission stays off.

Only one completion-snapshot attempt is allowed for that original action. A
contradictory/malformed/missing snapshot cannot be retried after an unrelated
child event to manufacture an eventual match. The regression fails the prior
draft and passes the final implementation.

## Original navigation epochs for late download responses (E109)

An nr association identifies a request; it must not cause a response arriving
after replacement to adopt the current page's document. Native goto admission
now captures its original root Origin. At retention, the response uses only a
common original document among its frozen eligible candidates, with native
request/loader evidence filtering settled candidates. This works before a delayed
ACK when all possible sources share the same original document. The epoch is
bound once, independently of later nr evidence and frame inventory.

A revoked original epoch cancels its own source without claiming/reading a body.
Ambiguous documents or correlation faults retain a failed record with
download_frame_unproven and native.source_document:null. That record has a blocked
scope and cannot gain permission from a fresh current page. Its cancellation is
not a global controller failure; after cleanup, a new well-proven operation can
proceed. Known causes survive cleanup and later user decisions. Existing completed
EOF/file-verified bytes remain readable after navigation, while same-document
position movement does not invalidate their document origin.

native.source_epoch_kind exposes the basis: response_frame is a response-time
frame snapshot, navigation_candidates is the common original command epoch before
exact attribution, original_navigation additionally has the exact nr proof, and
unproven grants no document authority. A stronger label never replaces the pinned
epoch. Initiating-document proof for other response_frame paths remains open.

The broker passes 915 assertions across seven new scope/order/recovery cases;
the real socket/JSON/JNI pipeline also checks its original epoch before/after a
body-dependent ACK. Full native gates and the actual refreshed APK pass. Device,
provider and main-UI qualification remain deferred, with public admission off.

Holding the native abort ACK also proves that a rejected source immediately
reports cancelling with download_frame_unproven. An early accept retains that
same known cause instead of reporting a generic frame change. Cleanup confirmation
remains required before releasing the exact original source.

## Released deferred request associations (E110)

Explicitly releasing a download now ends its deferred native-request metadata
association as well as its record/file ownership. The old implementation leaked
those pending Link slots after confirmed cancellation/release; the 33rd sequential
missing-metadata response incorrectly disabled correlation. The original worker
now prunes retired Links before new admission or late metadata delivery. UI/receipt
threads mark association retirement only and never mutate worker-owned arrays.

Cancellation alone preserves an inspectable record and its late-metadata path.
Source abort/close confirmation and cache cleanup requirements remain unchanged;
retiring metadata grants no I/O, stream or execution authority. Known references
and released snapshots retain their original facts. The existing 32-live-Link
limit and native identity checks remain in force.

Cancel/release and complete/release each pass 40 sequential cycles, followed by a
fresh exact association, with zero file/record reservations between cycles.
The broker/request gates pass 1,247/176 assertions; full native gates and the
refreshed actual APK pass. These component/packaging results do not qualify an
installed Android provider, actual Activity UI or deferred device recovery.

## Shared JS/C save integration with original Android response bytes (E111)

`python tools/android_save_probe.py` now exercises the previously separate
components together: the exact product download command fragment, real native
JVM/JNI protocol/controller/file queue, shared download-save.js producer and the
running development C host's scoped stream/receiver/publication services. The
Java peer receives only developer pipe commands and synthetic renderer bodies;
it is not packaged into the app or exposed as a public/raw browser API.

The original 131,075-byte response is claimed once, consumed through five native
body reads and independently verified by the native file worker. Saving reads
three exact cached ranges through the product command handler; the C task host
streams, verifies and atomically publishes the identical full SHA-256/bytes.
Additional cases preserve an existing destination, reject a deliberately foreign
range before publication, cancel the real receiving operation before publication,
and save completed retained bytes after their original network connection exits.
The latter follows the shared human-save producer path; it does not imply that
Agent writes are allowed during manual takeover.

All 47 integration assertions pass. Original-source claims stay at one, no
refetch/repeated admission occurs, and native records/range leases/file quotas,
pending host read/release waiters and C receiver staging are cleaned up. The
gate requires current native-input hashes, checks its own six inputs before and
after execution, and records the exact original native-gate receipt hash. It
reuses fixed directories and the same application/Home without another deployment.

Scope remains component integration: Java runs on the existing Linux development
JVM/JNI harness, renderer/UI/framework peers are controlled, and the real C file
host in this run is the Windows development service. It does not prove Android
ARM64 filesystem/framework behavior, installed-provider network interception,
main-UI routing or complete public SDK admission on a phone. Those qualifications
remain false/deferred; the product gate and current WebView are unchanged. Current
evidence is in .mdo/E111-android-save-current.json and E111-android-save-final.log.

## Page-close download retention and automatic reaping (E112)

The original Android page-close callback now retires its original network
connection immediately. It keeps completed original source bytes and their
controller records; unfinished sources retain their existing cleanup/failure
rules. Native state distinguishes close_requested from closed_by_page, which is
the shared popup manager's permission to automatically remove the native owner.

Automatic reaping requires confirmed original protocol retirement, no retained
download records or pending file cleanup, and no original-controller read/release
reply waiter. Known unconfirmed network cleanup blocks this permission. No socket
or disk I/O is performed by this UI observation. A closed page's verified bytes
remain readable before explicit release; releasing still cancels unfinished
reads according to the original file-lifetime rules. Waiting for reply delivery
after file/record cleanup prevents automatic removal from swallowing that result.
The shared manager separately waits for the final broker/UI acknowledgement.

The exact product popup-state and download-command fragments are tested together
with the real controller/file worker. The previous implementation fails check 82
with a retained verified original response. The final 101 assertions cover its
read after page close, blocked worker/read/release ordering, zero records with
still-pending replies, final reaping, protocol/global cleanup fences, ordinary
unmanaged page close and an unclosed view. Full native gates cover 109 current
inputs; shared popup/download JS checks pass 29, the JS/C save integration passes
47, and Python passes 173/175 with the two existing privilege skips.

The actual APK is rebuilt and verified. These are component/packaging results:
the callback's framework/provider/main-UI qualification remains deferred and
protected popup network adoption stays explicitly unqualified. No provider or
public Android automation gate is enabled by the new close-state semantics.
See .mdo/E112-page-close-before-fix.log, E112-native-current.json,
E112-save-current.json and E112-final-identities.log for current evidence.

## Browser target metadata for popup coordination (E113)

The private native worker can read the fixed /json/version endpoint through the
same process-and-UID checked abstract Unix socket. It enforces a 16 KiB body cap,
strict JSON/duplicate-key validation and a typed localhost/browser-path parser.
Only /devtools/browser or that path with one canonical UUID segment is retained.
No redirects, TCP/port/credential selectors or caller URLs are accepted. Raw
provider/version metadata and native/JNI byte copies are discarded/cleared.
Discovery does not open a target, establish original WebView ownership or enable
debugging/automation. The existing page-list discovery remains unchanged.

This is a prerequisite for the protected-popup coordinator: Chromium's
[onCreateWindow implementation](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/docs/how-does-on-create-window-work.md)
replaces the temporary child's native contents. A listener attached to the blank
child cannot by itself prove interception of the resumed original popup. The
version-pinned [Target handler](https://raw.githubusercontent.com/chromium/chromium/138.0.7204.179/content/browser/devtools/protocol/target_handler.cc)
also requires a Browser target for autoAttachRelated. Reference identities are
recorded in host/android/cdp-protocol.json; these source facts are not installed
provider qualification. Browser opening, exact opener scope, pause/Fetch/transport
ownership and bounded resume/cleanup still need to be joined before admission.

Actual local socket/JVM/JNI tests cover fixed GET, foreign-process rejection
before bytes, fragmented reads, body cap, redirects, allocation/conversion
failures, deadlines, descriptor balance and adversarial URLs/types/duplicates.
C passes 401 checks with 93 balanced allocations, JVM worker 164; full native
ASan/UBSan/API26 gates retain 109 current source inputs. Shared JS/C saving passes
47 and Python passes 173/175 with the two existing privilege skips. The actual
ARM64 host and APK are rebuilt; actual DEX and all seven JNI exports verify.
See .mdo/E113-native-current.json, E113-save-current.json,
E113-android-receipt.json and E113-final-identities.log. No phone/provider change
or public Android admission occurs; framework/provider/automation flags stay false.

## Private browser transport and original-worker cleanup (E114)

The checked BrowserEndpoint now opens a private browser WebSocket through the
same native transport as page connections. C independently validates the fixed
browser namespace; no arbitrary URL, page path, TCP or fallback is accepted.
Browser/page sessions share eight opening/live/draining slots, same-process UID/
PID checks before any request, one absolute boot-clock handshake budget, strict
RFC6455, bounded frames, monotonic handles and sensitive-buffer clearing.
The actual Java worker/session facade retains UI/wrong-worker refusal and
thread-safe cancellation. If original ownership changes during opening, its
post-open guard closes that exact handle. No retry/replay is introduced.

The real local socket/JVM/JNI tests exercise plain/UUID and fragmented handshakes,
fixed read-only version RPCs through the actual codec, path/type/copy/allocation
faults, invalid accepts/redirects, foreign-process rejection before bytes, shared
quota and blocking-receive cleanup. Native WebSocket passes 1,307 assertions with
234 balanced allocations and two browser close cycles; JVM worker passes 232,
including eight browser cancellation cycles and before/after-open owner loss.
Full native gates retain 109 current inputs, JS/C saving passes 47 and Python
passes 173/175 with two existing privilege skips. Actual ARM64 and refreshed APK
verify all eight JNI exports, actual DEX and source/signer/payload identities.
See .mdo/E114-final-identities.log and E114 native/save/APK receipts.

This private connection alone grants no original opener/WebView/popup ownership.
It does not auto-attach, pause or resume any renderer in the product route.
Those joins and native transport proof still precede protected-popup admission.
Controlled protocol/UI peers and ARM64 packaging do not qualify installed
provider/framework behavior. Public Android automation/provider flags stay false;
the phone and installed WebView are left unchanged.

## Process-broker prerequisite: release an original unclaimed wait (E115)

The next popup coordinator must account for a pinned Chromium distinction:
[ShouldWaitForDebuggerInWindowOpen](https://raw.githubusercontent.com/chromium/chromium/138.0.7204.179/content/browser/devtools/devtools_instrumentation.cc)
consults Browser handlers' [ShouldThrottlePopups](https://raw.githubusercontent.com/chromium/chromium/138.0.7204.179/content/browser/devtools/protocol/target_handler.cc),
which returns auto_attach rather than the separate autoAttachRelated registry.
Consequently the earlier opener-related attachment plan alone is not proof of
first-request suspension. A process-owned broker must cover new targets while
classifying them against actual native opener owners and promptly releasing
unclaimed waits. The exact four source identities are in cdp-protocol.json;
these source facts do not establish installed-provider behavior.

A private Browser-only fixed release is implemented as a prerequisite. It
freezes the original top-level attachedToTarget's typed metadata before callback
mutation and accepts no caller selector/script/URL. Metadata is not task or
WebView ownership. For that original waiting session it sends only an empty
Runtime.runIfWaitingForDebugger, waits for the original empty ACK, then detaches
that exact original session through the same Browser connection. Only both ACKs
complete its opaque receipt; late child traffic is retired. The shared dispatcher
can progress these fixed controls during another ordinary RPC without reentry.
Writes run outside the control gate, preserving nonblocking cancellation.

Both stages retain original deadlines, capacity/lifetime limits, terminal failure
and no-replay behavior. A confirmed resume remains an effect even if detach is
unsupported or rejected. Body access, Fetch enablement, task admission and global
auto-attach are absent from this release primitive. Only a future broker can
classify managed targets and join their original native transport/owner proof.

The gate passes 2,595 assertions plus two real local browser-WebSocket/JVM/JNI
sessions covering exact commands and both ACK orders. Limits, mutation, invalid
events, duplicates, cancellation, timeout and both-step failure checks pass;
full native gates retain 110 source hashes. Shared saving passes 47 and Python
passes 173/175 (two existing privilege skips). Actual DEX/APK/signature checks pass
with the unchanged verified ARM64 C host and eight JNI exports. Evidence is in
.mdo/E115-final-identities.log and E115 native/save/APK receipts. Renderer and
UI metadata peers are controlled; installed popup behavior, process-broker
integration and overall Android provider/automation qualification remain false.

## Private process broker and physical opener registration (E116)

The process controller is implemented as MdoBrowserCdpPopupBroker. It acquires
one process claim before native browser discovery/opening and fixed page-only,
flattened, waiting auto-attachment. Registration takes only the private source of
an enabled original physical Network proof, retains its original lifetime and
exposes no caller-selected target. Related metadata is held as a candidate for
future native popup transport/child proof; it enables no body/Fetch/Agent action.
The component uses its original protocol worker and adds no executor or engine.

Unclaimed existing targets detach without a synthetic resume; waiting unclaimed
targets use the original resume-then-detach chain. Disable first drains old
controls. Late targets during disable are recorded as retired, without repeated
commands, and only the original empty disable ACK releases the process claim.
Native candidates block graceful disable pending their original native handling.
Unconfirmed closure/cancellation/expiry/opener loss leaves an explicit small
process fence; socket shutdown is not presented as remote cleanup. The component
has no automatic replay/rearm after such uncertainty. Native integration and
recovery still need to complete before public adoption is enabled.

Bounds include eight registered openers, seven held candidates, 64 release
waiters, 256 lifetime targets and 15-second candidate expiry. Seven broker modes
pass 167 assertions using actual marker/binding/dispatcher code with controlled
UI/renderer metadata. One mode includes actual native discovery plus browser
WebSocket/JNI, verifies all five exact enable/release/disable commands and a late
disable event. Full native gates cover 112 current inputs, shared JS/C saving
passes 47, and Python passes 173/175 with two existing privilege skips. Actual
DEX/APK/signature checks pass. The changed overlay hash also triggers fresh
product-fragment 647 and actual Windows frame/input 301 runs; old receipts are
preserved rather than relabeled. See .mdo/E116-final-identities-complete.log.

This component is packaged but not activated by the product route. Worker/UI
scheduling, original native callback/child association, managed response routing
and qualified resume/adoption remain incomplete. Framework/provider/public
Android automation qualification is false. Current phone/WebView deferral stays
in force; local kernel/fixture evidence does not establish installed behavior.

## Original-worker popup preparation and UI lifetime (E117)

The private preparePopups method now joins the process broker to the existing
protocol executor. Its input is only an already-ready original physical network
lease. It does not take caller debugger/target IDs and the public product does
not call it yet. Eight request slots include pending terminal UI callbacks;
duplicate openers are refused. Opening and fixed broker commands share the old
worker/queue, and its network pump also services popup events during cooperative
document RPC waits. No second executor, application copy or engine is added.

Ready/state/candidate notifications return through the original UI owner.
Cancellation or lost leases suppress delayed ready notifications. Candidate
delivery is once-only and still proves no native child/transport/body ownership.
Request cancellation only sets data and wakes the worker. It releases one
registration without disabling a connection used by other openers. The result
distinguishes sourceReleased from processCleanupConfirmed: only the final
original empty disable ACK proves process-wide cleanup. UI callback failure is
contained and cancels that request without crashing ordinary document work.

A retained native candidate cannot yet be safely rejected/adopted by this
coordinator. Cancelling it reports cdp_popup_native_cancel_required and explicit
cleanup uncertainty; the original process fence prevents a replacement attempt.
Host close also preserves unconfirmed cleanup while waking I/O without waiting
on UI. The missing native callback/child/transport association, managed first-
response interception and scoped body/resume joins remain necessary before any
public protected-popup route can be enabled.

Three controlled JVM modes pass 82 new assertions (63/12/7), including original
worker/UI ownership, two-openers sharing, queued cancellation/expiry, delayed
callbacks, cancellation during arming, cooperative waits, candidate invalidation,
callback failures and process fencing. Full native gates cover 114 input hashes;
broker 167, fixed release 2,595 and shared JS/C saving 47 remain passing. Python
passes 173/175 with two Windows symlink privilege skips. Fresh product UI 647 and
actual Windows frame/input 301 gates bind current inputs. The rebuilt actual
APK verifies required DEX definitions, eight JNI exports and signer/source/
payload identities. See .mdo/E117-final-identities.log and E117 receipts/logs.
These checks do not qualify an installed Android provider or the public mdo UI;
framework/provider/automation flags remain false and device checks stay deferred.


## Original native cancellation and full worker exit (E118)

Private prepareNativePopups takes an original physical WebView/marker lease
joined on UI to its exact native popup controller. It does not accept caller
protocol IDs and does not prove the held child's native WebView/transport. The
new Stop receipt holds fresh native window admission while sending that original
pending null transport once, or joining its already-sent rejection. An adopted
child cannot be converted into a successful rejection. Consumption, timeout,
send failure and late callbacks remain distinct; uncertain results do not replay
or reopen native admission.

The original native consumption barrier is only one half of cancellation. The
worker also requires detach events for all of that opener's original candidates.
A five-second combined bound ends unconfirmed cleanup explicitly. Only both
proofs release the source, and the last source still requires the original
process-wide disable ACK. The UI releases its native admission hold only after
that source proof. There is no unproven child resume, body access or extra thread.
The old preparation overload without native proof keeps its conservative failure.
A request already terminal cannot gain a fresh cancellation effect merely because
its UI result is delayed until host close.

Full regression also exposes premature exit reporting: the pool task loop can
be terminated before the enclosing native-retirement finally. A regression locks
the actual retirement gate, fails the old implementation deterministically and
passes after tracking completion of the outer native workers. Two further
checks cover reclamation of never-started factory records, for eight in total. This
preserves the meaning of no remaining worker I/O rather than changing the test
into a delayed retry. The first failure and interrupted fixture evidence remain.

Native popup ownership/transport passes 110 plus 25 callback checks. Six shared-
worker modes pass 206 assertions, including both notification orders, missing
barrier/detach, send failure and late outcomes. Full ASan/UBSan/API26 gates cover
116 source inputs. JS/C saving passes 47; Python passes 173/175 with two existing
Windows privilege skips. Fresh product UI 647 and actual Windows frame/input 301
checks pass; the latter now records its actual development host binary hash and
verifies it unchanged during execution. The actual rebuilt APK verifies Stop/
native-source DEX definitions, eight JNI exports, signatures and exact payloads.
See .mdo/E118-final-identities.log, E118-exit-before.log/after.log and E118 receipts.

This joins private original cancellation, not child adoption or managed popup
network/body routing. The public product still refuses protected unqualified
Android popups; provider/framework/automation qualification remains false. Phone
and installed WebView verification stay deferred as requested.


## Original-target protective interception before adoption (E119)

An original retained waiting attachment can now request fixed protective Fetch
setup. The shared worker requires its exact native source and performs at most
one setup RPC per cooperative turn. Request and Response patterns are fixed,
authentication interception is off and the exact original empty ACK is required.
The original current UI receives a once-only protection result. This does not
resume the child or prove its native WebView/transport/task ownership.

The protocol stage distinction follows the pinned
[Fetch definition](https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/1c3e94f6cfa36eb231c03b1553faee7f649aa29a/pdl/domains/Fetch.pdl).
Request-stage events retain only private original IDs, including events before
the setup ACK. They do not continue, evaluate a page or claim response bytes.
The per-target limit is 32 plus the existing session history/candidate/deadline
bounds. Duplicate, wrong-scope and premature response events fail closed; detach
and close clear the queue. A successful setup grants no body/download authority.

Matched unsupported setup can end through the existing original native rejection,
all-candidate detach and final-disable acknowledgement path. Lost/partial sends,
invalid replies and other unknown effects retain uncertainty and original native
rejection rather than replay. Known precise native error codes survive; arbitrary
provider messages are omitted. Cancellation suppresses late successful callbacks.

Broker checks pass 177 across seven modes and two actual native discovery/socket/
JVM/JNI flows, including the exact child-scoped setup command and requests before/
after ACK. Fourteen shared-worker modes pass 444 checks; eight new protection
modes contribute 232. Controls never resume an unproven child, continue a request
or read a body. Full native gates cover 116 source inputs; JS/C saving passes 47
and Python passes 173/175 with two Windows privilege skips. Unchanged E118 product
UI/native popup/actual Windows and E105 world inputs are rechecked before reuse.
Actual rebuilt DEX/APK/signature/payload checks pass. See
.mdo/E119-final-identities.log and the E119 native/save/Python/package receipts/logs.

The public Android popup route remains disabled. Exact native child transport,
fresh ownership/takeover lineage, bootstrap/resume and scoped body routing still
need their original-proof joins and qualification. No installed provider behavior
is inferred from controlled metadata, and phone/WebView checks stay deferred.


## Original native callback ownership and one-time handoff (E120)

A physical network lease may remain valid while a task is losing control and
retiring its original interception. That is insufficient authority to send a
native popup Message. The original UI now claims a separate opaque action scope,
with the exact opener/controller/navigation epoch and a fresh owner predicate.
The main host keeps the original adapter and supplies an action predicate that
requires its ready, current, nonretired, nondraining, nonmanual entry and original
host channel. Cleanup keeps its original weaker physical lease.

Accepted native callbacks retain their original scope. Cancellation and owner
loss are sticky; a later manual callback cannot be sent, invalidated or released
by an old task's scoped rejection. A same-owner canceled scope continues to hold
native admission until its original cleanup finishes. No callback is admitted
for that action before its original preparation-ready UI delivery. The worker
checks current ownership before delivering ready/candidate/protection results.

The private pending handle and adoption receipt bind one original native send
and its queue-consumption barrier. Wrong/foreign/expired/replayed handles and the
ordinary ID-based adoption path cannot bypass that scope. Only weak native child
references escape the controller; these handles contain no UI owner predicate or
native transport. Send failure, timeout and owner loss retain uncertainty, even
if a barrier arrives later. A consumed Message does not prove a fresh unowned
child, its CDP target, document, isolated world, response or body authorization.
Those joins and the public protected-popup route remain pending/disabled.

Original source/controller checks pass 120; fourteen shared-worker modes pass
491. Exact main-host fragments pass 657, including cleanup-only takeover and
fresh rearm. Existing popup ownership/callback checks remain 110/25; that gate
now hashes its eight original inputs before/after execution. Full native gates
cover 117 inputs; JS/C saving passes 47; Python passes 173/175 with two existing
Windows symlink privilege skips. See .mdo/E120-final-identities.log and E120
native/product/popup/save/package receipts. Actual APK/DEX includes Scope,
Pending and Adoption definitions and retains the verified ARM64 host and signer.
Phone/provider/framework checks remain deferred, with all qualification flags
false. Windows/plugin artifacts and their rechecked applicable evidence are
unchanged; no model, external browser page or publication was used.


## Fixed native-view marker matched to retained targets (E121)

The private original UI source can now create one opaque child handle from a
successful native send/consumption receipt and the exact physical child adapter.
Wrong/foreign/unconsumed/replayed receipts fail before protocol proof. The handle
keeps weak marker ownership and data leases, so a queued proof cannot retain its
native view or UI owner after retirement.

The existing worker accepts at most seven child proof requests and performs one
bounded proof per turn. It freezes the known protected, waiting candidates of
the original opener and uses one native marker ticket to match the physical view
across that set. The caller supplies no target ID. A decoy peer sees no native
marker expression. The existing negative unique-context control, native value
check and exact-context cleanup remain required. A separate per-target private
connection performs the proof; the original Browser connection then validates
its exact retained/protected attachment after an ordered Target.getTargetInfo
reply and preceding detach/pause events. Native proof emits no debugger resume,
request continuation, navigation, world installation or body command.

Cancellation is checked before queued native marker execution, while awaiting
its callback, before each candidate connection and before result publication.
Closing the marker ticket drops its operation guard. A new regression fails
before the connection guard and passes afterward: cancellation following a
nonmatching peer must not open the next candidate. Pending document RPCs can
also service the proof on the same worker without ordinary session reentry or
resetting their deadline. Success notifications are suppressed after cancel.

The native frame script entry and new-window page wait are distinct mechanisms;
see the pinned Chromium
[native script handler](https://raw.githubusercontent.com/chromium/chromium/138.0.7204.179/third_party/blink/renderer/core/frame/local_frame_mojo_handler.cc)
and [page-wait implementation](https://raw.githubusercontent.com/chromium/chromium/138.0.7204.179/third_party/blink/renderer/core/exported/web_dev_tools_agent_impl.cc).
Their bytes/hashes are retained in cdp-protocol.json. An installed provider's
ability to complete this marker while paused remains unverified, not inferred
from those sources or controlled metadata.

Twenty-six shared-worker modes pass 899 checks; the twelve new proof modes add
408, including decoys, foreign opener handles, absent guards, marker failure,
ignored unique contexts, wrong barriers, detach, owner loss, cancellation between
candidates, stale UI and cooperative RPC waits. Native-source/controller checks
pass 134. Full ASan/UBSan/API26/JVM/JNI gates cover 117 input hashes; existing popup
ownership/callback checks remain 110/25. JS/C saving passes 47; Python passes
173/175 with the two existing Windows privilege skips. Actual rebuilt APK/DEX
includes Child and verifies the retained native binary, signer and exact payloads.
See .mdo/E121-final-identities.log and E121 native/save/popup/package evidence.

This proves a private marker-to-retained-target association under the tested
scope. Fresh child registry integration, confirmation of the original native
contents swap, complete pending-target capture, guarded bootstrap/resume, body
routing and installed-provider qualification still remain. A failed proof does
not undo an earlier native handoff, and closing the proof handle does not destroy
the native view. Exact UI child teardown and original target detachment remain
separate cleanup obligations. The public protected Android popup route and all
provider/framework/automation qualification flags remain false. Phone and WebView
changes stay deferred; Windows/plugin artifacts remain unchanged.

## Native consumption and child retirement (E127)

The private native source now records a typed original-Pending adoption intent
before entering the native transport. Dispatch is once-only; a prepared intent
that was never sent cannot become a consumed-child receipt through a later null
rejection. Foreign, duplicate and stale receipts do not acquire another callback.

Created.close is UI-owned. Before native handoff it disposes an unsent instance
through the exact product callback. After dispatch it immediately revokes the
child's data-only proof permission, but waits for the original native consumption
receipt before destruction. Returning from close can therefore mean retirement
was requested, not that physical cleanup is complete. The UI protocol book
retains that obligation and handles a synchronous or late consumption callback.
Only exact post-destroy acknowledgement completes the native retirement; the
worker additionally requires the original retained targets to detach before
releasing the action. Human takeover remains preserved.

Native consumption still proves neither a physical target association nor child
world/profile/readiness/body authority. The original protected capture and marker
proof remain separate. A cleanup failure fences future native sends, including a
previously allocated permit: current source state is checked again before handoff
and child proof. Closing a child proof handle alone still does not destroy its
native view.

Controlled regressions cover delayed/reentrant consumption, destruction before
acknowledgement, target-detach ordering, duplicate close/acknowledgement, failed
destruction, missing consumption and original-opener loss. A late acknowledgement
may clean the exact child after a global failure, but cannot rewrite an already
failed terminal result as confirmed cleanup. Multi-source/global failure
quiescence and the complete product preparation/adoption/promotion/body-routing
coordinator remain unfinished. Public protected Android popup admission and
provider/framework/automation qualification remain false; these tests do not
qualify an installed WebView. Current counts and exact artifacts are in progress.md.

## Retained UI owners and parent destruction (E128)

Native preparation now reserves one of eight UI retention records before worker
admission. That record strongly owns the original controller, scope and UI book;
the source's links to the record/book remain weak and the worker receives no
new strong UI object. This deliberate, bounded UI ownership survives the host
removing a closed opener from its normal window map.

Destroying an opener does not certify consumption of an outstanding original
native callback. A retained controller keeps that callback, budget and timers
until the real acknowledgement. Terminal or parent-destroyed records are released
only when the original callback and all known owned children have settled.
Unknown constructor/callback state stays charged instead of being erased; no
polling thread, fake acknowledgement or automatic replay is introduced. The
native capabilities diagnostic native_popup_owners reports the current count.

Host teardown notifies this owner pool after exact native destruction and the
registry acknowledgement. Created handles remove themselves from their captured
original book, rather than a later claim of the same source. Product block
callbacks likewise verify the physical view before changing document block or
capture state: an old callback cannot unblock a replacement using the same tab
key. The failed-before/fixed-after callback regression and controller/worker
lifetime cases are retained with the E128 evidence.

This closes the tested UI ownership/late-callback gap, not the complete product
popup coordinator, global failure quiescence, promotion/bootstrap/body routing
or installed-provider validation. Protected Android popup admission and all
provider/framework/automation qualification remain false. Phone and WebView
changes remain deferred.

E128 did not yet join full Activity teardown to the native Message consumption
barrier. The E129 host-destructor integration below addresses that gap; it does
not qualify an installed provider or enable the protected product route.

## Deferred physical host destruction (E129)

Single-view close and Activity shutdown now share an exact UI-owned destruction
record. Registration revokes control without invoking SDK cleanup. Whole-host
shutdown captures and registers all physical views before cancelling controllers,
then prepares all records before activating any physical destructor. Reentrant
callbacks reuse the captured record, including when closing a sibling. Resources
are captured by physical identity, not looked up later through a reused tab key.

The native UI owner permits physical destruction only after both the original
parent's child-adoption Message is consumed and this view's own outstanding
popup Messages are consumed. A synchronous acknowledgement before preparation
does not bypass logical teardown. A missing acknowledgement keeps the record and
native_view_retiring reservation charged, with cleanup unconfirmed. The exact
post-destroy registry/document/dialog/capture/upload/network notifications run
once, after the physical SDK call. A failed destructor remains fenced without
automatic replay. Explicitly closing a human-owned tab can retire it after native
consumption; task cancellation alone still preserves human ownership.

The product-fragment gate now executes the actual whole-host destroy method as
well as single-view close. Actual native controller/shared-worker gates cover
late and inline acknowledgements, duplicate close, human ownership, destructor
failure and a child that also owns a pending native popup, in both callback
orders. The APK contains verified method definitions for the new host/native
destruction interfaces. These are controlled SDK seams and native/JNI tests,
not an installed renderer/provider pass. Current counts and artifacts are in
progress.md.

The full product preparation/action/adoption coordinator, independent child
promotion, global multi-source failure quiescence, guarded bootstrap/resume and
response/body routing remain unfinished. E130 below adds the asynchronous product
close receipt. Public protected Android popup admission and provider/framework/
automation qualification remain false. Phone and WebView changes stay deferred;
unavailable isolated-world support does not permit main-world Agent execution.

## Final product close receipts (E130)

The native product close command now captures its original channel/request and
physical destruction, document, dialog and network/download owners before
retirement. It waits for that exact physical view and its original protocol and
private-file cleanup, then returns one final scoped receipt. Another tab's pending
native retirement remains visible in host diagnostics without replacing this
tab's cleanup proof. Repeated observations can share one physical destructor.

Observation uses the existing UI Handler at 100-ms intervals, for at most five
seconds, with a limit of 32 outstanding requests. It creates no worker/thread or
runtime dependency. A timeout, invalid clock interval or rejected scheduler does
not certify cleanup or replay destruction. The returned observation-expired and
observation-failed flags distinguish those cases. A late native/network receipt
can still clean the original resource, but cannot rewrite an already delivered
terminal result. A fresh explicit close may observe the retained original record.

Channel loss drops the old observation; it cannot send a result to a replacement
channel. Activity shutdown cancels the reply watchers while actual native
retirement continues under E129. Immediate physical failures also use the original
reply channel instead of falling through to an unscoped current-port error.
Metadata cleanup checks physical ownership again after reentrant UI callbacks.

The exact product route, watcher and whole-host shutdown are exercised together
with controlled SDK/provider peers. The APK has verified close-route/watcher and
BrowserClose constructor definitions. These tests do not qualify an installed
provider or enable protected popup admission. E131 below joins the recursive
Created.close/product-disposer/native-activation path. The full protected product
coordinator and installed-provider qualification remain separate requirements.

## Product disposer and native reentry (E131)

A joint regression reproduced a cleanup defect that the earlier separate gates
did not execute: Created.close entered the product disposer, which registered a
host destructor and called native activation. Activation tried Created.close
again, but its closing guard returned without executing the host destructor. The
missing physical acknowledgement was then classified as cleanup failure.

Native activation can now complete that same exact registered host callback while
Created is already closing, once the callback is ready and both native-consumption
barriers permit it. It does not reenter Created.close or replay a running native
destructor. Missing physical acknowledgement without a known pending native
obligation remains a failure. Host destruction has an explicit failed state, so
failed cleanup cannot be treated as a still-unprepared callback.

The exact product disposer requests retirement and lets the native controller
join its physical acknowledgement. A child that is also an opener can therefore
wait for its original native callback rather than fail because retirement is
pending. Unrelated network retirement is not physical-view destruction failure;
original network/process cleanup still needs its own receipts. Configuration or
actual native teardown failure keeps the existing sticky process fence.

The new joint gate compiles the real product constructor/disposer and physical
teardown fragments against the actual native source, lifecycle registry, popup
controller and shared worker. Controlled document/network/SDK peers remain; no
installed renderer is qualified. Nine scenarios cover unsent/sent/consumed
cleanup, configuration rollback, unrelated network retirement, physical/logical
failure, a child with its own pending native popup and reentrant close. Before/
after evidence is bound to the original and current production source identities.
Public protected popup admission and provider/framework/automation qualification
stay false. Continue full preparation/action/adoption coordination, promotion,
global failure quiescence, guarded bootstrap/resume and response/body routing.

## Retirement when UI result delivery is lost (E132)

A rejected UI result post could previously leave an original native UI record
retained after its worker terminated. Returning to the foreground did not retry
that cleanup. Internal native retirement now has its own bounded, original-scope
token and does not depend on a result listener. Terminal publication records the
retirement intent first. The existing UI delivery path, foreground resume and
native creation admission can deliver it; no extra worker, timer, UI I/O or
caller-runs fallback is introduced. The host diagnostic native_popup_retirements
counts intents still waiting for UI delivery, not unconsumed native callbacks.

The token has only an immutable data scope and weak links to its original UI
record and bookkeeping notification. An old request cannot allocate/prove a new
claim's child or reject that replacement claim. Request cancellation and process
creation failure also revoke previously issued allocation/proof permits. A
listenerless terminal job can release its bookkeeping once UI delivery succeeds.

Delivery is not a native consumption or cleanup acknowledgement. Original Messages
and sent children retain capacity until their real callbacks/destructors complete.
The first terminal result and release permission are immutable; late cleanup
cannot upgrade a failed result. Actual teardown failure keeps the process fence
and does not replay the failed destructor automatically.

Eight worker scenarios cover lost delivery, a rejected-once post, recovery through
creation admission, queued cancellation, multiple independent owners, sent/unsent
children and a stale request after a new claim. The joint test executes the actual
host resume/creation fragments with the native source and shared worker. Further
controller checks exercise weak notification failure, duplicate binding and the
separation of delivery from consumption. These are controlled SDK/provider tests,
not installed-provider qualification. All-source native quiescence before global
broker teardown remains outstanding; recording all affected cancellation intents
before close is only one part of that work. Protected Android popup admission
remains disabled, and the user's phone/WebView deferral is unchanged.

## Registered native owners and browser wire cancellation (E133)

Session-internal failure could close the browser transport before the worker's
failure handler requested native rejection. A joint two-source regression
reproduces that order. The worker now installs one close barrier before the first
browser command and registers each original native retirement with that barrier
before registration/arming. Logical session close still immediately cancels
protocol admission, replies and retained request authority. Its local wire
cancellation waits for all registered original native Messages and task-owned
children to settle. A socket/renderer that has already failed cannot be revived
by this barrier; unknown outcomes remain fenced.

The barrier revokes every affected permit before any weak UI notification. The
existing UI delivery/recovery paths reject each original scope independently.
Consumption of one source does not release another source's charge. Timeout,
worker exit or lost UI delivery does not stand in for native consumption. Failed
destructors and failed transport cancellation are not replayed automatically.
Process-wide pause cleanup and local transport cancellation are distinct receipts;
late cancellation cannot rewrite an earlier terminal result.

The eight-slot barrier reuses only settled slots. It retains a weak wire identity
and a cancellation credential, not the wire's worker Thread/session/broker graph.
The production credential is allocated before native open and contains only the
shared AtomicLong handle. It uses the existing nonblocking native close primitive;
there is no UI send/receive, second executor, timer, reconnect or command replay.
Completion of this cancellation call is distinct from draining an already running
reader; the existing worker-exit fence still applies.

Eight shared-worker scenarios cover internal failure, explicit shutdown, lost UI
delivery, elapsed timeout, a sent child, physical teardown failure, reentrant
close and cancellation failure. Native-controller checks cover two-source
revocation before notification, original binding, duplicate close, callback
failure and sixteen sequential actions through the bounded slots. Actual JNI
tests verify the data-only credential in twelve cancellation cycles (four browser
cycles), and join a real blocked browser read to the original native popup
consumption callback. The rebuilt APK's credential fields and twenty actual class/
method definitions are checked, rather than inferred from class-name strings.

This proves the registered-participant local browser-wire path with controlled
SDK/provider peers and real JNI transport. Full product action/adoption routing,
independent child-session/promotion/body routing, complete process membership and
installed-provider qualification remain outstanding. Protected Android popup
admission stays disabled. Phone/WebView changes remain deferred.

## Native ownership promotion (E134; protocol handoff outstanding)

The product's private promoteProtocolPopup seam now transfers a consumed,
physically proved child into independent native ownership. It requires the exact
constructed child, its original allocated proof, successful native-marker result,
consumed native adoption, matching registry/key and a current close barrier. It
does not accept a supplied WebView or mint ownership from a tab ID. No document
world, profile, navigation, body handling or protocol promotion is granted.

The registry prepares an independent receipt before committing it. Recheck exact
view/opener identities, parent generation, own generation and input cleanup before
commit. Legacy fresh/adoption proof becomes unusable at commit. The independent
receipt describes physical ownership only; it has weak view/registry links and no
opener lease. Own removal/destruction still retires it. Visibility and human
takeover remain native host policy, not an automatic grant of Agent admission.

Native ownership moves from the parent's construction book to an eight-slot UI
pool. A data-only dependency keeps the original browser close barrier charged
until this independent task-owned child is destroyed or explicitly handed to its
human owner. The parent's UI record can release without rooting its controller
through this dependency. Construction allocation and weak parent links are
cleared; the old child proof connection and its opener/allocation links retire.
The new opaque proof identity contains no old Child, marker lease or network.
The shared host/registry/disposer remain UI-owned for actual native destruction.

Explicit close, global quiescence and recovery use the exact existing product
destructor. The independent window can itself have an outstanding native Message;
that callback must settle before physical destruction. Rejected UI posting keeps
the owner for resume/creation recovery. Failures do not replay SDK destruction or
upgrade a historical failure after later physical acknowledgement. Taking human
control preserves the window while retiring task authority; physical destruction
is reported separately. Diagnostics include native_promoted_owners.

Ten joint product/native/shared-worker scenarios cover parent close, explicit
close, parent-scope reuse, global shutdown, lost UI posting, cleanup failure,
human takeover, denied promotion, two children and a nested native callback.
Runtime field checks verify old parent references are cleared. Additional registry
checks cover stale preparation, old-proof revocation, parent removal, visibility,
human ownership and reserved identity until post-destroy ACK. The APK has checked
independent/promotion field shapes and 23 actual class/method definitions.

The joint fixture explicitly controls the native ownership terminal, without
certifying a Browser-session handoff. The original paused attachment, first
request/response controls and independent document/world bootstrap still need
coordinator routing. Native view ownership alone is insufficient for any of those
permissions. The public protected-popup route and provider/framework/automation
qualification remain disabled; the user's phone/WebView deferral is preserved.


### E135: independent retention of the original protected attachment

After the worker proves the exact native child against its original protected
Browser attachment, it creates a single-use Handoff reservation. Its seven fields
carry opaque proof identity, clock/stop data and the native ownership receipt;
there is no broker, session, old Child/allocation, registration or SDK field.
The production clock is the static elapsedRealtime source. The private product
handoffProtocolPopup seam reserves that receipt before committing native registry
ownership. A missing, cancelled, stale or replayed reservation cannot promote a
child. The separate E134 native-only seam does not claim a protocol handoff.

Once committed, the original attachment's lifetime follows the independent native
receipt rather than its opener registration. The worker releases legacy parent,
Child and proof-network references. The same Browser connection, original target,
debugger wait, protective Fetch interception and retained request IDs remain;
there is no reattach, debugger resume, continuation, Fetch disable, page script,
response/body exposure or UI publication. The original 15-second capture deadline
and 32-request limit still apply while document/bootstrap admission is unfinished.
An unqualified first response still fails closed.

The original preparation may naturally return sourceReleased=true while reporting
processCleanupConfirmed=false: an independent protected attachment still uses the
process service. Its historical result is not upgraded later. Parent native close
after that terminal and a new preparation on the original source preserve the
child. An original detach ACK retires the exact attachment and native owner;
expiration, response-before-admission, resource overflow, shutdown, lost UI posting
and failed native cleanup retain the existing fail-closed/quiescence requirements.

Ten actual product/native/shared-worker modes pass 428 assertions. They exercise
reservation cancellation, natural request terminal, subsequent parent native
close, parent-source reuse, expiry, response rejection, request limit, global
close, UI recovery and disposal failure. No controlled executor/native terminal
substitutes for parent completion in these modes. The actual APK contains the
handoff/product/worker methods and checked seven-field receipt structure. This is
paused attachment ownership only; complete child-session execution, first-response
controls, independent document/world bootstrap, body routing and installed-provider
qualification remain outstanding. Protected public admission remains disabled.


### E136: guarded debugger resume and original-request flow

The private product activateProtocolPopup seam accepts only the exact committed
native owner and its Handoff. It queues a single-use intent; UI code performs no
protocol I/O. The existing process worker first sends fixed
Runtime.runIfWaitingForDebugger on the original protected session. An empty ACK
confirms resume without detaching the session. It then sends fixed
Fetch.continueRequest with each original retained request ID, one command per pump
turn. It accepts no caller target/request selector, replacement URL, method,
headers or body. The cursor rotates across the bounded child list; full multi-child
coordinator qualification remains separate.

Each command checks the independent native owner before dispatch and throughout
all receive/event callbacks. A lost owner, timeout, failed/partial send, mismatched
reply or rejection never reconnects or retries. Invalid arguments and callback
reentry are rejected before consuming one-shot state. The original resume result
is historical acknowledgement; a later flow failure is recorded separately and
neither result substitutes for current ownership. Eleven data-only Handoff fields
carry identity, clock/stop/activation flags, native receipt, static-code results and
an atomic continuation count. No session, original Child/allocation or SDK field
is added to the receipt.

Fetch interception stays enabled. After explicit protected resume begins, first
responses are retained privately, including a response arriving before the matching
request-continuation ACK. No metadata/body is exposed or response released. Request
and opaque response retention share 32 slots; duplicate IDs, invalid sessions and
resource overflow fail closed. Pre-activation response events still fail closed.
The original capture deadline remains in force while bootstrap is unfinished.
This phase does not create or publish a document/world, authorize Agent execution,
release responses or connect the body/download pipeline.

Seventeen joint product/native/shared-worker modes pass 571 assertions: successful
original/next-request flow, parent close, expiry, ownership loss, unsupported or
rejected resume, nonempty/mismatched reply, timeout, partial send, rejected or
malformed continuation, foreign session, response overflow and cancellation during
continuation. Separate original-attachment checks reject invalid/null/dead-owner
arguments without I/O or consumed state, then successfully use the same retained
request; callback reentry cannot issue a second command. Actual APK inspection
checks the new methods and eleven-field receipt. Installed-provider/framework and
public automation qualification remain false. Continue independent child protocol,
document/world bootstrap, response release/body routing and full product
coordination before enabling the protected route.


### E137: document, frame and isolated-world access on the original channel

Physical ownership does not identify a current DOM. The private product
protocolDocumentMarker obtains a marker from the existing exact-view/epoch/quota
registry. Native Promotion.document additionally checks the marker's actual
native-view identity on UI, rejecting foreign views, network-only markers and
replayed claims. Its three-field Document receipt carries only the native owner,
thread-safe lease and opaque attachment identity. An attached lease cannot fall
back to ordinary discovery/binding.

submitDocument dispatches through the existing worker and process broker. The
original Browser DocumentPeer selects its already captured root internally and
normalizes that root to the existing frame controller's null source. It opens no
connection and runs no target discovery. The ordinary document binding still
performs its native document witness, default-context negative/positive unique-ID
checks, cleanup and final frame/loader validation, all on the original channel.
Its native housekeeping witness is not Agent execution in the default world.
Existing frame inventories and isolated-world creation/unique-context proofs then
run unchanged, including related out-of-process iframe trees. Unsupported or
malformed isolation cannot fall back to default-world Agent code.

Only an armed related native iframe event can admit a nested session; a supplied
root/raw/foreign session is not an addressable peer. The broker keeps bounded
nested membership and maps original detach events to their captured parent. Epoch
loss retires local document work and clears stale output without closing a valid
physical owner or silently reconnecting. Document callback faults stay local when
the transport remains ordered. Physical ownership/transport failures still use the
original process/native quiescence guard.

Explicit document rebind first disables the original page session's related-frame
auto-attachment and Runtime reporting, consumes their ordered acknowledgements,
and retires old nested IDs. It then repeats document proof on the same connection
with the UI registry's document quota. This does not disable the Browser process
broker or Fetch interception. First responses remain privately paused; there is no
response/body transfer or complete public product admission in this phase.

Eleven joint product/native/shared-worker modes pass 481 assertions. They cover
five-frame isolated inventory, isolated evaluation, repeated work on the same
binding, two forms of rebind, navigation during evaluation, physical-owner loss,
unsupported/malformed isolation, foreign markers, raw-session refusal and generic
fallback refusal. Rebind with nested iframes uses fresh native session IDs. Actual
APK checks include the three-field document receipt, marker attachment fence and
native-view identity override. Installed Android/provider qualification remains
false, with the phone/WebView deferral preserved.

### E138: original first-response decisions and bounded body transfer

The private attached-document binding can select the next original retained
response only after native document identity and isolated-world readiness of both
the root and the response's exact frame have been proved. Keep validated frame
identity for ordinary HTML/subresource/status responses as well as download
candidates; previously only candidates retained that identity. No target, request,
stream, URL, header or replacement body is selected by a caller. A response with
an unproven frame remains paused, rather than becoming a generic pass-through.

AttachedResponse is a private receipt for that exact paused event. It permits
one fixed continueRequest for a passable response, one fixed failRequest abort,
or conversion of a candidate into the existing Offer table. Invalid arguments do
not consume the decision. Pass/Offer conversion recheck the originating document
and worlds; abort can still clean up the original event after document loss when
the physical owner remains current. Never rediscover/reconnect or use default-
world Agent code. Successful frame admission ends the short capture-only expiry;
physical ownership, cancellation, process lifetime and existing resource/time
limits still apply. An unadmitted attachment retains its 15-second expiry.

Accepted original-body transfers reuse the existing eight Offer/eight Body slots,
64-MiB ceiling, bounded reads, buffer erasure, EOF validation and close/abort
acknowledgements. The frozen source is tied to its physical native owner, not a
replacement DOM. The receipt rejects another response's Body even on the same
Browser connection. Stream handle reuse history includes the original child
session; identical provider handle strings in different sessions are independent.
Retired outstanding read IDs retain their exact session, so a late cancelled read
can be discarded without accepting another session's bytes. A mismatched late
reply fails the original channel closed. The existing broker pump services bodies
cooperatively on its original executor.

Nine product/native/shared-worker modes pass 409 assertions: pass/replay refusal,
invalid arguments, abort, cancelled Offer, unproved frame, document loss followed
by abort, actual queue/file/hash/readback completion, pending-read cancellation
and correct/foreign late responses. The original-attachment fixture adds 53
checks for denied admission, two child sessions with the same request/stream
names, cross-response Body rejection and confirmed independent cleanup. It still
declares that protocol fixtures do not prove native/task/provider ownership.
Full native evidence passes 123 current input hashes, 134 worker modes/5,012
checks, original attachment 2,686/two real native sessions, controller 360 and JNI
264. Fresh product, popup, save and Python checks pass. The actual rebuilt APK
contains 41 inspected class/method definition groups, a seven-field response
receipt, twelve-field handoff, scoped retired-read map and Body/Offer source
ownership callbacks; signing, ZIP/alignment, ARM64/JNI gates also pass.

This remains an internal component join. Full preparation/action/adoption/task-
policy coordination, source-epoch/download UI admission, nested OOPIF response
routing and process/navigation recovery are still outstanding. Public protected
admission and installed-provider/framework/automation qualification stay false.
The phone/WebView deferral remains in force; there was no device or external-
website test, model call, new runtime dependency or production change.

### E139: share the product download controller with attached popup responses

The original popup broker can now feed MdoBrowserCdpDownloads.Connection, using
the existing shared file worker, receipt budget, pending/accept/cancel/read/release
state machine and immutable UI snapshots. Its authority is the exact physical
Handoff, not a replaceable document binding. Related new document bindings can
explicitly attach the same connection; every existing row retains its own original
AttachedResponse for body reads. There is no new socket, executor or alternate
download state protocol. Candidate retirement disconnects that original controller
without inventing stream/request cleanup confirmation.

Proved attached frames issue data-only download provenance: opaque frame/document
aliases, a weak handle to the original document Lease, and atomic graph/ancestor
epoch fences.
Document close, graph failure, frame replacement/removal and frame/ancestor loading
revoke the original fences. Recreating a frame or binding cannot reactivate them.
UI snapshots never read a worker-owned frame graph or invoke a worker callback.
Decided responses drop their document-admission closure; terminal transfers drop
the row's original-response handle. Retained files therefore do not keep an old
binding alive through that closure or a strong provenance Lease.
Without an original navigation-request ledger, provenance remains response_frame
with a null source request reference; it never fabricates original_navigation.

The broker pumps pending rows cooperatively on its original executor. Bytes still
need explicit native UI acceptance, the existing eight pending-Offer/32-receipt
budgets and the ordinary byte/time/EOF/close/abort checks. Stale permissions cancel
unfinished sources/cache writes. A fully completed source and verified file remain
readable after a document or physical page closes, with their own original IDs,
hash and ordering. An interrupted/unconfirmed source is not reported completed or
normally releasable. New/replacement controllers cannot borrow another record.

Attached pass/abort acknowledgement waits run outside the download UI snapshot
monitor. Normal document retirement is handled locally; an already selected exact
event may be aborted under its still-current physical owner. Transport failures,
protocol corruption and loss of physical ownership keep their original failure
and quiescence handling. There is no broad catch-and-retry or weaker-world fallback.

Fifteen joint worker/native/controller/file modes pass 856 checks: actual verified
binary readback and release, explicit cancellation, size policy, frame/ancestor/
document revocation, old UI acceptance, a stale binding callback, native detach,
pass-through, both shared receipt and pending-Offer saturation, competing controller
refusal, document rebind and completed-file reads after close. Every native
response/body I/O in these modes also asserts that the download UI monitor is not
held; they also inspect weak provenance and cleared document/response references.
Controlled renderers/real native protocol/file components do not qualify an
installed Android framework/provider.

Complete preparation/action/adoption and task-policy coordination, product UI
publication/lifetime, nested OOPIF response routing and full process/navigation/
takeover recovery remain required. Public protected admission and installed-
provider/framework/automation qualification stay false. Phone/WebView testing
remains deferred; no external browser/model or production/account change is made.

Final native evidence passes 123 current hashes, 149 worker modes/5,868 checks,
original attachment 2,686/two actual native sessions, controller 360 and JNI 264.
Fresh product/popup/save checks and Python 173/175 pass; two existing Windows
symlink-privilege cases are skipped. The rebuilt development APK passes signing,
ARM64/JNI/16-KiB/ZIP and 47 actual class/method definition groups, including the
six-field weak provenance, atomic graph/node fences and per-row original response
release.
E139-final-identities.py/log binds those source/artifact identities and explicitly
scoped unchanged Windows/world evidence. No installed-provider result is inferred.
The next coordinator tests must cover initial frame loading while the first
response is paused, document readiness and original navigation provenance; these
controlled renderer modes do not establish that real provider ordering.

### E140: a protected network source does not require a ready document

A paused response can precede a ready isolated document. The private host now
issues separate exact-native Source and Document receipts. Bootstrap Source on
the original proved Browser attachment before activation, using the root/frame
metadata from the native-child ownership proof. Fixed Page/Network setup and
bounded consistent frame-tree reads preserve pending-navigation events without
enabling Runtime, creating worlds, navigating or replacing Fetch interception.
See the pinned [Chromium Page handler](https://github.com/chromium/chromium/blob/138.0.7204.179/content/browser/devtools/protocol/page_handler.cc#L622-L642)
for the pending navigation event emitted during Page.enable.

submitSource shares the existing job quotas, worker and binding cache. Source
rejects arbitrary RPC and frame/world operations before I/O; DOM/input still
requires its separately admitted isolated document. A document lease can retire
without retiring a valid physical network source. Duplicate/foreign source
proofs, binding after activation, unsupported methods, unexpected reply contents
and unstable bootstrap metadata grant no fallback or replacement connection.

Freeze response ancestry and original request correlation at receipt, then reuse
the existing product download controller, explicit UI decisions, file queue and
bounded Body/Offer state machines. Selection cannot borrow a replacement page's
epoch. Pending/stale sources are aborted, and bytes require explicit acceptance.
Normal responses can pass while the document is still loading. Full product
policy/UI coordination and original navigation provenance remain outstanding.

Twelve new controlled source modes pass 632 checks, including a valid pending
navigation during bootstrap, response decisions while loading, binary readback,
original capture invalidation before selection, subsequent epoch change, document
lease independence, competing/duplicate sources and bounded bootstrap failures.
Final native passes 124 current inputs and 161 worker modes/6,500 checks; fresh
Windows WebView2 passes 301 checks/six input cycles. Product/popup/save pass and
Python passes 174/176, with two existing symlink-privilege skips. Reviewed source
copy/hash/compile manifests are checked together. Actual APK signing/ARM64/JNI/
16-KiB/ZIP and 50 class/method definition groups pass, including data-only Source
ownership and absence of UI references from the worker facade.

E140-final-identities.py/log records current hashes and explicitly scoped E105
world reuse. This is component integration evidence, not installed-provider or
framework/automation qualification. Public protected popup admission stays off;
phone/WebView tests remain deferred. Next recovery checks include isolated-world
loss versus graph-issued download epoch fences, in-flight source revocation,
native product UI/policy coordination, OOPIF and multi-child/process recovery.

### E141: isolated-world destruction revokes document-issued download epochs

Controlled execution reproduced a stale epoch: Runtime.executionContextDestroyed
removed the exact admitted isolated world and rotated document references, but
an existing download's atomic fence remained current. worldLost now revokes
the original node and descendant fences before creating fresh ones. Pending or
receiving document-issued sources therefore cancel and retain their own cleanup
receipts. Explicit isolation reconstruction permits new references; it never
revives an old download. Unknown context IDs do not retire unrelated sources.
This document-bound fence is separate from the physical network Source lifetime.

Six joined controller/file cases pass 383 checks for root/local/ancestor loss,
unaccepted download cancellation, reconstruction/new download and unknown context
events. Three physical-source cases pass 194 checks for replacement during a
read, explicit cancellation and physical-owner loss. Late retired reads cannot
publish bytes. An unconfirmed physical close remains unverified and cannot be
normally released. Fixture recovery uses new native stream handles rather than
weakening handle-reuse validation. E141-red.log/json preserves the original
failure, and E141-final-identities.py/log verifies its source against E140.

Final native passes 124 current inputs and 170 worker modes/7,077 checks. Fresh
saving and Python 174/176 pass; two existing Windows symlink-privilege cases
remain skipped. Current hashes permit explicit reuse of E140 product/popup/
Windows and E105 world evidence. The rebuilt APK passes signing/ARM64/JNI/
16-KiB/ZIP and 50 actual definition groups, including the corrected worldLost.
These results do not qualify installed Android or enable public automation.
The next integration remains product preparation/action/task policy and UI
lifetime, followed by original-navigation, OOPIF and multi-child recovery.


### E142: explicit preparation before original-page operations

The actual MdoBrowser now owns a bounded preparation scope per original physical
view, document epoch, network entry and UI channel. The shared dispatcher asks
for `popup_prepare` before isolated evaluation (which may scroll or invoke page
events), native input and option selection. Preparation returns an independent
acknowledgment; the dispatcher rechecks the originating task, deadline, document
and manual-control state before page work. An absent or malformed acknowledgment
never dispatches input. A cancelled second preparation preserves an earlier
focus effect rather than reporting the whole action as unsent. Hosts without
the advertised requirement keep their existing route.

The existing native worker/process broker performs preparation. Its event
forwarder holds only a weak reference to the UI component. A ready scope can be
reused without another physical connection; stop, navigation, takeover and
network retirement revoke it. Replacements require actual original-source
release, not the historical stop confirmation. Terminal worker failure causes
survive derived owner-loss observations. Uncoordinated native view construction
is fenced while this process-wide protection is active; the existing typed,
single-use protected constructor still checks its original allocation proof.
Native agent input and the frame evaluation route reject missing preparation.

Seventeen joined cases pass 192 assertions, including actual product RPC and
input refusal, cancellation, original-channel/document changes, reentrancy,
callback failure, unsupported guards, timeout and explicit restart. Existing
chooser/upload/download harnesses retain controlled admission/state peers;
those seams do not qualify preparation or an Android framework. Full native
passes 128 inputs and 187 worker modes/7,269 checks, retaining every E141 mode's
count. Shared JS passes 377 tests. Fresh product, popup and saving gates pass;
Python passes 174/176 with two existing Windows symlink privilege skips.

Both development programs were rebuilt. Windows passes twelve packed runtime
checks and fresh real WebView2 passes 301 assertions/six input cycles. Actual
Android DEX inspection verifies 57 definition groups and the weak-only forwarder;
APK signing, ARM64/JNI, 16-KiB layout and ZIP checks pass. Evidence is retained in
`.mdo/E142-*.log`, `E142-dex-methods.json` and `E142-final-identities.py/log`.
These are transport/controlled-JVM/build and Windows results. They do not qualify
an installed Android provider or enable the public Android automation gate.
Complete native popup adoption, task policy/UI, profile inheritance,
physical-vs-document navigation lifetime, OOPIF/multi-child/process recovery and
deferred phone/WebView verification remain required.


### E143: existing native owners remain usable under popup protection

Two controlled regressions against E142 reproduced native_popup_protection_busy:
the actual preparation component retained its active source while the product
attempted a proved child handoff, and the exact product create route attempted
to reattach an existing native view. The original creation check ran in both
paths even though neither operation constructed an unrelated WebView.

Separate nativeOwnerAdmission (all original host/native/network/download
cleanup barriers) from nativeCreationAdmission (those barriers plus the active
process-protection fence). Typed protected construction, handoff/promotion,
activation and document/physical-source helpers use the common barriers and
still validate their exact native receipts. The ordinary create route uses
construction admission only when no view already exists. Existing popup identity
checks, no-reload reattachment behavior, new-constructor rejection and cleanup
uncertainty remain intact. No generic boolean bypass is needed.

Five cases join the actual E142 preparation component, original process worker,
protected native allocation, transport consumption, child proof, handoff,
activation and independent cleanup. They pass 159 assertions, including three
cleanup barriers at handoff and later operations plus foreign source/document/
activation receipts. The test-only fixture reflects the actual component's
original source/request instead of forging worker receipts or adding production
getters. The exact product create fixture adds nine reattachment assertions, including
closure during original-owner reconciliation. Recheck exact view ownership after
that callback boundary before touching bounds or reporting attachment.
Neither fixture claims an installed Android framework/provider result.

Full native passes 128 current inputs and 192 worker modes/7,428 checks, retaining
all 187 E142 mode counts. Product creation passes 798, saving passes 47 and
Python passes 174/176 with two existing privilege skips. Rebuilt APK passes
signing/layout/JNI/source/archive and 57 actual definition groups, including
nativeOwnerAdmission. E142 Windows real-frame, packed-runtime, JS and popup
results are explicitly scoped unchanged reuse. E143-red.json/log and
E143-reattach-red.json/log retain both original failures; E143-final-identities.py
binds their source hashes to E142 evidence and checks current sources/artifacts.
The public Android gate remains disabled. Actual popup create/policy/UI/profile
coordination, physical/document navigation lifetime, OOPIF/multi-child/process
recovery and deferred phone/WebView qualification still require work.


### E144: native profile ownership independent of isolated script worlds

MdoBrowserProfile owns one exact native view's acknowledged profile association.
Its standalone open/inherit path requires MULTI_PROFILE without requiring
JS_INJECTION_IN_FRAME_AND_WORLD or message listeners. The existing world adapter
now delegates to this same implementation; its duplicated profile state was
removed. Both paths reuse the existing bounded feature inspection and strict
foreign-classloader method binding. No browser/runtime library was added.

A profile is assigned once before other WebView use, then read back on that
exact native provider. Sealing forbids later reassignment. Acknowledgment loss
stays uncertain after logical close and is settled only by the exact original
view's destruction; no setter replay/default-profile fallback is allowed.
Inheritance accepts only a confirmed original, another view and the same
provider classloader. Recheck the source after provider allocation and after
assignment, so retirement during either native callback cannot publish a child.
The returned child is sealed and retains no parent owner. Closing one owner
neither closes siblings nor resets/deletes the native named profile data.

These handles acknowledge configuration, not native window freshness, page
identity, permissions or transport ownership. Callers still own fresh native
construction and teardown, including when inheritance fails after a setter.
Root/network and protected-popup creation must next use the independent profile
handle before settings, navigation or transport; they still depend on the
unfinished product coordinator. Public Android automation remains disabled.
This change does not claim that the installed deferred WebView supports profiles.

The actual world/profile code passes Java 8 compilation against controlled
published boundary signatures in a foreign loader. Eighty new inheritance checks
cover profile-only capability, no world/listener grants, source/provider/thread
mismatch, parent close, independent siblings, failures before/after setter,
readback errors and ownership loss during provider allocation/assignment. Profile
checks total 270; existing world/document/native-budget checks remain 35/85/18.
Full native passes 128 current inputs and 192 modes/7,428 checks. Fresh product
798, popup 110/25/7, saving 47, Python 174/176 and real WebView2 301/six cycles
pass. Two existing symlink privilege cases remain skipped. Windows program/paired
plugin bytes are unchanged; packed-runtime evidence is scoped hash-checked reuse.

The rebuilt full APK passes signing/layout/source/JNI and 59 actual class/method
definition groups. Inspect the profile definition, its absence of a parent field
and the world's single delegate with no parallel legacy profile state. The
existing provider/framework/automation flags remain false. E144-world.log,
E144-native.log, E144-frames-current.json, E144-dex-methods.json and
E144-final-identities.py/log retain current evidence. Phone/WebView work remains
deferred; complete product policy/UI/profile/lifetime and recovery qualification
is still required before enabling the public adapter.


### E145: physical root profiles and deferred initialization teardown

The actual root create/network path now uses the independent profile owner
before ordinary settings, navigation or transport. MdoBrowserDocument owns the
physical profile and optionally borrows it into MdoBrowserWorld using the same
exact native view, provider and classloader. Borrowing never repeats provider
creation or the profile setter, and closing that world alone does not close the
physical association. Missing boundary-world capability is explicitly unavailable
while a confirmed MULTI_PROFILE association remains usable by the existing
private CDP adapter. This is not device/CDP/public-automation qualification.
Once native world installation starts, uncertainty is not downgraded to an
optional-capability result or retried. Realm faults, page navigation and manual
takeover do not themselves erase the physical configuration.

The original profile's close revokes borrowed peer admission, posting and
evaluation. A retained native call may still terminate, but it cannot deliver a
success payload after that owner closes; its terminal callback releases the
original pending/native budget without replaying an action. The failing draft
regression is retained in E145-borrowed-owner-red.log.

Root creation publishes the prepared controller before invoking native provider
callbacks. Original channel/view admission is rechecked after binding and around
settings/view publication. Close retires logical ownership immediately but holds
native destruction until initialization returns; its one-shot completion then
resumes the original captured destruction row. Cleanup cannot be acknowledged
while native initialization is active. Invalid initialization parameters have
no provider effect. Fatal allocation/thread errors remain fatal through cleanup.

The new joined root test reproduced a double native destroy against e737074:
callback close completed destruction, but outer creation rollback did not find
its acknowledgment. MdoBrowserLifecycle now retains an exact weak-history ACK
for plain roots, as already required for fresh popup children. Registry removal,
an equal object or a reused key never substitutes for native destruction. The
red log/hash receipt is preserved before the fix; current close/host/channel/
configuration cancellation paths assert one destroy and no late navigation.

Fresh actual profile/world/document/budget checks are 319/35/86/18, production
Java target 8. Product root checks are 827 plus asynchronous close 77; lifecycle
checks are 167. Full native passes 128 source hashes, 192 modes/7,428 assertions,
attachment 2,686/two native sessions, controller 360 and JNI 264, including
ASan/UBSan/API26. Fresh popup, saving, Python 174/176 and Windows 301/six input
cycles pass; two existing symlink privilege tests remain skipped. The same
deployment's rebuilt APK passes 60 actual definition groups, including the
physical controller, borrowed world flag and plain-root acknowledgment field.
E145-final-identities.py/log bind the final artifacts, source hashes, red
regression and scoped unchanged Windows/JS evidence.

Protected popup profile inheritance before transport still needs actual task
policy/UI coordination, physical/document network lifetime and navigation/OOPIF/
multi-child/process recovery. All Android provider/framework/device/automation
qualification flags remain false and the public adapter stays disabled.
Phone/WebView validation remains deferred; no device/provider/production change
or release was made. Windows program and paired plugin bytes are unchanged.


### E146: inherited profiles before protected popup settings and transport

constructProtocolPopup now reads the exact original opener from the native
source and requires its current acknowledged physical profile controller. This
check precedes child allocation and never accepts a default/manual association.
The configured native child receives a published physical controller before
provider callbacks. Its one-time profile assignment and exact native readback
reuse the original namespace and provider classloader. No script world/listener,
navigation, tab publication or transport is granted by this initialization.
Original parent/controller/source/channel and exact child are rechecked before
and after common settings. A changed owner cannot revive an allocated child.

Root and inherited initialization share the same in-flight/completion/cleanup
logic. The child controller retains the physical profile object before calling
its setter, so uncertain native results remain chargeable until exact view
destruction. Parent loss during provider allocation prevents the setter;
retirement during/after binding cannot publish a child. Known children retain no
parent controller/profile owner and keep their independent profile association
after parent close. Binding failure uses the existing native disposer and never
replays construction, the setter or transport as a fallback.

Actual provider/controller tests pass 396 checks (77 new); world/document/budget
checks remain 35/86/18 under Java 8. Product root/child creation passes 848 checks
(21 new) and asynchronous close 77. Four joint actual native/product cases add
102 assertions for inheritance, pre-allocation refusal, in-flight close and
original-channel replacement. Full native passes 128 current source hashes,
196 modes/7,530 checks with all prior modes unchanged, controller 360, attachment
2,686/two native sessions and JNI 264, plus ASan/UBSan/API26. Popup, saving and
Python 174/176 pass; two existing symlink privilege cases stay skipped.

The rebuilt APK passes 60 actual definition groups, including native opener,
inherited-controller setup, common cleanup and the two inheritance overloads.
No parent controller field is present. E146-final-identities.py/log bind all
current artifacts and fresh evidence. E145 Windows frames, packed Windows runtime
and JS checks are scoped unchanged reuse, rather than claimed fresh execution.
Actual task permission/UI publication, original physical/document network scopes
and navigation/OOPIF/multi-child/process recovery still need integration. Public
Android/provider/framework/device qualification remains false; phone/WebView
checks remain deferred at the user's request. No release or device change was
made, and the Windows program/paired plugin/native binaries remain unchanged.


### E147: shared popup/task UI lifetime and refreshed program payloads

The shared popup manager snapshots original scope before native state reads and
checks exact opener/child instances and task/client ownership around asynchronous
admission, creation, settlement and publication. Page-close callbacks cannot
remove a replacement. Actual panel adapters forward child lifetime guards, and
open/close check them plus original row identity after backend replies. Known
adopted children retain independent ownership. Uncertain effects/admissions stay
retained; stale callbacks never automatically recreate/close a replacement, and
already-issued backend operations are not claimed cancelled.

E147-popup-red.json/log retain twelve reproduced baseline failures; applied panel
open/close adds five red cases. Thirty-nine focused tests pass. A full JS run
also exposes the old bootstrap extractor's dependency on Java field ordering;
strict constant-token parsing replaces that brittle boundary while retaining
actual bootstrap behavior checks. Earlier E145/E146 JS377 scoped reuse omitted
this Java dependency and is superseded by fresh 402/402 JS results, with expanded
before/after source hashes. Fresh actual SDK popup display/close and Python
174/176 pass. Full native passes 128 inputs/196 modes/7,530 checks, and current
creation/popup/saving/Windows frames/packed runtime gates pass. The unchanged
eight-input E146 Java world gate is scoped reuse, not a fresh provider run.

Both development programs are rebuilt from the same source/Home. The APK uses
the newly validated Windows app archive and passes 60 actual DEX definition
groups plus signing/layout/native/source checks; use the current hash near the
top of this document. E147-final-identities.py/log binds final artifacts and
evidence. Shared UI fixes do not enable the incomplete Android automation route.
Provider/framework/device qualification remains false. Actual protected popup
task/UI publication and network/document lifetime integration remain required,
and phone/WebView checks stay deferred at the user's request.


### E148: original browser mount, end and title-update ownership

The shared UI wrapper now uses the original mount/entry/task/resource and prior
registry instance, rather than only an AbortSignal, when registering a browser
or handling its end/title callback. Older asynchronous work cannot overwrite or
remove a newer instance. The shared browser-ownership module keeps popup and
mount identity normalization consistent. Panel update checks row/lifetime again
after backend replies while retaining the saved-result contract for current
callers. Normal preview and metadata refresh still work. Aborting a UI mount
does not close/revoke the task's native dispatcher or erase its registry record.

Fifteen red failures are retained with baseline/applied-source hashes; final
verification reconstructs the original applied wrapper. Twenty-two new wrapper/
update tests plus the prior popup suite pass. Fresh full JS passes 424/424,
Python 174/176 and actual SDK popup/display/close pass; two existing symlink
privilege cases remain skipped. Fresh native passes 128 inputs/196 modes/7,530
checks, with creation, popup, saving, Windows frames and packed program gates
passing. E146's unchanged eight-input Java world gate is scoped reuse.

The same deployment includes 127 reviewed files and the new shared module.
Both previews are rebuilt and Windows verifies all 751 payloads; the matching
APK passes signing/layout/source/native checks and 60 actual DEX groups. Use
the current hash near the top. E148-final-identities.py/log binds final artifacts
and evidence. This wrapper does not yet qualify browser-view.js internal
initialization/recovery/poll/viewport/end owner propagation, actual protected
Android task/UI/network coordination or installed-provider behavior. Those
remain in progress; public Android/provider/framework/device qualification is
false and phone/WebView checks remain deferred. No release or device change
was made.

### E149/E150: original UI ownership and fresh frame inventory

The full development APK contains the same reviewed E149/E150 component and
reader as the Windows archive. Native creation, recovery, polling, controls,
quotations and cleanup check their original UI owner. The independent native
dispatcher remains task-owned; an initializing pane does not advertise a ready
Agent dispatcher. Only a fresh, unpinned read can discard a navigation-invalidated
inventory and repeat under its existing deadline. Pinned document/element
operations retain stale rejection.

Fresh deterministic regressions reproduce 18 actual-component failures and four
inventory failures before their fixes. Full JS 466, Python 174/176 (two existing
Windows privilege skips), actual Windows C/JS/native SDK popup tests, native
transport/worker 7,530 checks, product creation 848, popup 110/25/7 and save 47
pass. Windows frame 301/six-cycle and packed runtime 12 gates pass. Both previews
are rebuilt; all 751 Windows payloads and 60 APK DEX definition groups are
verified against current inputs and their shared source archive. The unchanged
E146 world/document/profile/budget gate remains scoped reuse with eight exact
input hashes, not a new provider execution.

The original E149 blank-popup failure and its `stale_frame` receipt are retained;
E150's successful actual SDK run follows the targeted read-only fix. Evidence is
under `.mdo/E149-*` and `.mdo/E150-*`. No Android install, ADB/provider probe or
WebView update was attempted. Device/framework/public automation remain false;
protected-popup network/UI adoption and the other full-goal gaps remain open.

### E151: original pending allocation and native proof UI delivery

The product preparation scope now owns one pending-allocation wait and at most
seven native-proof deliveries. The original pending callback is captured before
waiting; pre-birth guarded targets may arrive later, but replacement/replay does
not authorize new construction. Waiting uses the existing UI handler and
suspend-inclusive time, capped at 3 seconds. Product construction goes through
the inherited physical profile and common configuration. Proof callbacks run
only on the original worker's UI event, never by polling its atomic result, and
can commit the exact native handoff. No new runtime dependency/thread or UI
protocol I/O is added. Failure delivery is once-only, with possible native
effects preserved and original retirement required before reuse.

Sixteen new JNI/worker/product scenarios pass 348 checks. Full native validation
passes 212 modes/7,878 checks, retaining all 196 prior mode counts. Full JS 466,
Python 174/176, creation848/close77, popup110/25/7 and save47 pass. The source
fingerprint covers 479 inputs including `.in` templates and tools. The rebuilt
APK is signed by the same QA signer and verifies 65 actual DEX definition
groups. Windows is byte-identical E150 (751 current payloads); its runtime/frame/
SDK evidence is explicitly scoped reuse. E146 world/document/profile/budget
evidence is unchanged eight-input reuse. See `.mdo/E151-final-identities.log`.

The complete protected-create route still refuses `popup_network_unqualified`:
network-source/download binding, document/world activation and final publication
are not joined yet. The new product seams remain private. Device/framework/
provider qualification and public Android automation remain false. No phone,
ADB inventory, install or WebView update was attempted.

### E152: native consumption delivered to product adoption

The product preparation can now send the exact configured child once and await
its original native consumption notification before continuing to proof/handoff.
The original deadline spans both steps (maximum 3 seconds; seven bounded waits).
Native Created records retain subscription handles weakly; UI ownership keeps
them alive only while waiting. Inline confirmation before subscription, delayed
confirmation and cancellation/expiry use the same original Adoption identity.
No receipt polling, new URL/view, replacement subscription or extra thread is
used to recover. Native close retires the subscription; callback exceptions keep
the original possible-effect outcome.

Nineteen new controlled modes pass 385 checks; full native validation passes
231 modes/8,263 checks with all earlier 212 counts unchanged. Full JS466,
Python174/176, creation848/close77, popup110/25/7 and save47 pass. The rebuilt APK
is signed by the existing QA signer and verifies 69 actual DEX definition groups,
including weak ownership field shapes. The 479 input hashes include templates
and tools. E150 Windows artifact/payload/runtime/frame/SDK and E146 world checks
are unchanged scoped reuse. Evidence: `.mdo/E152-final-identities.log`.

Two negative modes explicitly distinguish native destruction from protocol
release: opener loss and send failure may leave `sourceReleased:false` even when
the child is destroyed. They remain unsettled and block new construction in the
original host; Activity/process recovery is not qualified by this gate. The full
protected-create network/download/source/world/publication coordinator is still
not joined, and `popup_network_unqualified` remains enforced. Device/framework/
provider/public automation remain unqualified/false; no phone, ADB, install or
WebView update was attempted.

### E153 product handoff and original close receipts

An internal product transaction now joins original allocation, inherited-profile
construction, native consumption, physical proof and independent handoff. It
owns the captured native callback and child key, rejects duplicate admission and
uses one original total deadline across all phases. It stops before network
activation or UI publication. Cancellation and callback exceptions retain their
native possible-effect and cleanup outcomes; a failed callback closes the exact
promoted child rather than replaying native send or navigation.

Explicitly closing a committed child previously caused opener-loss failure
before the Browser wire could read its original detach. The existing worker now
drains that exact receipt for at most five seconds, with all page-operation and
data authority already revoked. It does not resume paused requests or forward
closing-page data. Repeated close cannot renew the deadline. Missing receipts,
clock rollback and failed native disposal remain unconfirmed and fenced. The
process protection fence also blocks unrelated construction after the original
parent preparation has settled. Native destruction alone cannot clear it.

Twenty-two new modes/472 checks and the full native253/8,884 checks pass. The 56
document/response/transfer/source modes verify original close receipts and
acknowledged cleanup rather than relying on premature wire failure. JS466,
Python174/176, creation848/close77, popup110/25/7 and save47 pass. The signed APK
verifies 71 actual DEX definition groups and 479 current source hashes; see
`.mdo/E153-final-identities.log`. Windows E150 and world E146 are unchanged scoped
reuse. Full public create/network/download/world/publication integration and
device/provider/recovery qualification remain pending; protected public creation
is still disabled. The current phone and WebView were left untouched.

### E154 original physical source and download attachment

The product now joins original allocation/adoption/proof/handoff to a real
physical network entry and download controller on the same protected Browser
connection. One total deadline spans the phases. Source setup does not reload
the native child, resume it, replay navigation, grant a world or publish a tab.
The existing worker receives only a data controller and a weak UI completion.
Product retirement/destruction/watch/resume routes retain exact ownership and
original cleanup evidence. Protected navigation and takeover/rearm are still
fenced pending their typed implementation.

Fix cooperative idle and read-cancellation handling so a deliberately closing
child cannot poison the shared Browser connection. Only fixed pre-activation
Page/Network metadata bootstrap reads may abandon their local attachment;
late packets remain isolated, and native detach is a separate authenticated
receipt. Peer detach during bootstrap is likewise scoped to that child. Preserve
the original sanitized protocol failure before cleanup can replace it with a
generic view-close error. Clearing download rows cannot fabricate missing
stream-close/response-abort receipts, including during later resume.

Ordinary network closure now distinguishes pending native close from completion.
Its durable success/failure survives repeated close and worker-first shutdown;
post-exit reconciliation performs no native I/O. Deterministic delayed-close
tests pass ten sequential 268-check runs. Full native271/9,371 includes 18 new
product modes/477 checks and original-connection downloads with verified bytes.
Current JS466, Python174/176, creation848/close77, popup110/25/7, save47 and APK
gates pass. The separate ordinary-create fixture type update reuses the passed
native gate only after verifying all 128 unchanged inputs; 479 overall hashes
and 78 actual DEX groups are checked. See `.mdo/E154-final-identities.log`.

Complete activation/world/publication, protected navigation/takeover/nested and
concurrent-scheduling qualification remain pending, as do initialization rollback
with native-close failure and externally gated Browser-wire completion. Device/
framework/provider/public qualification remain false. Existing Windows E150 and
world E146 evidence is unchanged scoped reuse; the phone/WebView were untouched.

### E155 concurrent source admission on the shared Browser connection

A real product-coordinator regression withheld source one's Page-enable reply
while allowing the original cooperative receive loop to capture source two.
The popup worker attempted protection on that busy Browser connection, and
the resulting reentry failure closed both handoffs with `cdp_session_closed`.
The worker now waits for the original session to be idle before protection or
before taking a queued native proof. It keeps cancellation/capture processing
and the original deadlines; no second executor, endpoint, replay or premature
debugger/request continuation is introduced.

Three new controlled product modes/153 checks cover waiting protection,
waiting proof and authenticated detach of one child while the other awaits a
source reply. Both sources use their original native ownership and exact-once
destruction; closing one keeps the other current and the process guard armed.
The full fresh native gate passes 274 modes/9,524 checks with all 271 earlier
mode counts unchanged. JS466, Python174/176, creation848/close77, popup110/25/7,
save47 and the signed APK pass; all 128 native/479 overall input hashes and 78
actual DEX definition groups are checked. The APK is 6,567,233 bytes, SHA-256
`6584ece52f7a3c6634c17e24de0e3bd31189acd99d6f0cf04f4d1cd6b3183291`.
See `.mdo/E155-final-identities.log` and the retained original failure evidence.

This covers three two-source scheduling scenarios, not Android framework,
device/provider, nested-popup or general concurrency qualification. Existing
Windows E150/world E146 receipts are unchanged scoped reuse. Complete
activation/world/publication, protected navigation/takeover and the other
documented recovery gaps remain pending; public protected creation still
rejects `popup_network_unqualified`. The phone/current WebView remain deferred.

### E156 original activation and captured root-world startup

The private product coordinator now carries one original handoff through network/
download attachment, once-only activation, its native control receipt and root
isolated-world warmup on the same Browser connection. All phases share the
original maximum nine-second total. A field-free worker operation uses the
existing frame/world manager; only a fixed readiness status reaches the weak
UI completion. Profile/channel/native-owner and explicit document-epoch checks
fence late results. Loading metadata may be refreshed on that same binding.
This certifies only the captured root document and does not publish a tab.

A retained current download offer from that original physical root can complete
startup as download-only. DOM/world readiness is not required, and the user
retains the download decision. Actual streaming/verified-byte tests still use
the existing controller, source receipts and guarded file worker. Activation
errors retain their original sanitized reason before subsequent owner loss.

Twenty new product modes/611 checks cover both outcomes, parent-first closure,
same-world wait, native epoch changes, original control/world failures,
cancellation, owner loss and timeout. Full fresh validation passes 294 popup
worker modes/10,135 checks, all 274 earlier mode counts unchanged, plus
JS466/Python174 of176, creation848/close77, popup110/25/7 and save47. The old
retirement test now waits separately for the original cache-cancellation
terminal before checking unconfirmed source release; its 59-check probe passes
twenty sequential runs without weakening existing cleanup assertions.

All 128 native/479 overall hashes and 82 actual DEX definition groups are
checked. The signed development APK is 6,567,233 bytes, SHA-256
`9c64e1266bb4eb831f451e117eb3ae7947352ea784f1203dab8614d23ac5da25`.
See `.mdo/E156-final-identities.log`; Windows E150/world E146 are unchanged
hash-verified scoped reuse.

New per-case evidence distinguishes cleanup confirmation from retained fencing.
Nine negative cases intentionally remain unconfirmed. Scoped world cancellation
and some control-failure isolation still need to preserve the shared wire;
fencing is not successful recovery. UI publication/public command routing,
navigation/takeover/nested and other documented rollback/platform gates remain
open. Android available/framework/device/provider/public qualification remain
false, and public protected create still rejects `popup_network_unqualified`.
The phone/current WebView remain deferred.

### E157 original document/control retirement without cross-page wire loss

Closing one original page during a document or managed control call now drains
only that handoff under its original bounded retirement. The global connection
guard still runs first, possible effects remain observable, known nested sessions
are retired locally, and native detach must be authenticated separately. A
pending required retirement delays new document admission within its original
total budget. Expired retirement and global/unknown protocol errors fail closed.
Remove the unused unscoped attached-document call facade.

The receive/cancel race is also corrected: bytes already received must be decoded
under global connection ownership before the original command's cancellation is
applied. This preserves other-page events and original detach receipts, and does
not silently erase foreign packets. Original peer/document/frame scopes and
reentry rejection remain in force; no queue, connection or deadline is added.

Seventeen new modes/1,007 checks cover two original sources, root/nested startup,
reads/effects/resets, controls, native detach, receive/cancel races and negative
foreign/expired cases. Two older owner-loss modes now strengthen actual detach/
final-disable assertions. Seven earlier page cases now confirm cleanup; only
unsupported/malformed activation remain unconfirmed and fenced. Stream/IO
cleanup and broader recovery are independent outstanding gates.

Fresh full native311/11,141, JS466, Python174 of176, creation848/close77,
popup110/25/7, save47 and the full signed APK pass. All 128 native/479 overall
input hashes and 82 actual DEX definition groups verify current sources. The
6,567,233-byte APK SHA-256 is
`18d01dc2bd2056bda8a0e1cb1ad9141e87874f7a3b7eae809d112d8d07f6cab5`.
See `.mdo/E157-final-identities.log`. Windows E150/world E146 remain unchanged
scoped reuse; the E156 twenty-run retirement evidence is not a fresh E157 run.

Visible-tab publication/public command routing, navigation/takeover/nested and
the documented rollback/stream/platform gates remain pending. Public protected
create still rejects `popup_network_unqualified`, all Android public/provider/
device qualification remains false, and the phone/current WebView stay deferred.

### E158 actual frame-command routing on the original protected page

The product `executeFrames` path now selects the exact prepared popup document
and its existing marker/native proof, then dispatches on its original protected
Browser connection. The previous ordinary marker/discovery path failed with
`cdp_binding_failed`. A stale/missing/download-only document does not fall back
or replace its original marker. Existing chooser continuation requires its
original busy marker/binding. Effectful scripts retain the independent popup-
preparation requirement; protected-source nested preparation is still pending.

Current UI admission is checked both before dispatch and before normal result
delivery. An original reply received after the view is hidden cannot be returned
just because its startup document proof remains valid. View/epoch/handoff/channel
ownership, request grammar and result-buffer clearing remain in force.

Fourteen new actual-product modes/530 checks cover root/nested/repeated reads,
opener closure, stale/hidden/manual/channel/download-only admission, blocked
effects, busy requests, malformed raw session fields, and late navigation/
visibility changes. Full fresh native325/11,671 retains every earlier 311 mode
count and other JVM probe count. JS466, Python174 of176, creation848/close77,
popup110/25/7, save47 and the signed APK pass. All 128 native/479 overall input
hashes and 82 actual DEX groups verify current sources. Ordinary-only chooser/
upload fixture dependencies were extended without changing their existing checks.

The 6,567,233-byte APK SHA-256 is
`72820c3402faf3adc449389b7d7e7d1f9aa9c1d627878fd37d218b4a780600a9`.
See `.mdo/E158-final-identities.log` and retained before/after failures. Windows
E150/world E146 remain unchanged scoped reuse. No device/provider/UI-publication
qualification follows from these controlled protocol tests.

Public protected creation/UI publication, protected-source effect preparation,
navigation/takeover/nested popup and documented rollback/stream/platform gates
remain open. Malformed/unsupported activation still retain unconfirmed cleanup
fences. Android availability/qualification remain false and public protected create
still rejects `popup_network_unqualified`. The phone/current WebView remain deferred.

### E159 protected create/publication and transfer retirement receipts

Protected create now joins the original pending native popup to the existing
source/activation/world coordinator and attaches that exact view after readiness.
It uses the native address, copied bounded layout and original total deadline;
there is no URL replay, replacement target, connection or renewed budget.
Ordinary unrelated creation retains its guard. Global automation admission still
rejects public use before this product route.

A close during preparation cancels and observes the original handoff even before
allocation completes. Final close reports `popup_cleanup_confirmed` alongside
native/network cleanup; a removed map row alone is insufficient. Failed UI/layout
attachment, expired publication, changed channel/document and reentrant close do
not produce a success reply or resurrect a tab. Hidden creation retains physical
ownership but revokes document admission. Reshow/rearm remains pending.

Target retirement now retains bounded in-flight offer/body associations and drains
only their exact registered receipts through strict ID/session matching. This
fixes valid stream/abort acknowledgements being discarded as retired traffic.
Broker shutdown also drains original offers/bodies before disabling protection,
including a claim reply that arrives after native detach. It does not read more
body data or fabricate cleanup from native destruction. Wrong, rejected and
missing acknowledgements retain unconfirmed-source fencing.

Twenty-two new actual-product modes/723 checks cover publication, immutable layout,
original-budget expiry, native cancellation, UI failures and claim/read/abort
retirement. Framework/ViewGroup/provider peers remain controlled. The active-read
case discards a late original packet. Three negative receipt cases remain
unconfirmed; their passing tests prove containment rather than recovery. The old
transfer-detach case now strengthens source/cache/stream/abort/final-disable
confirmation (+10 checks). The other 324 earlier mode counts are unchanged.

Full fresh native347/12,404, JS466, Python174 of176, creation850/close77,
popup110/25/7, save47 and the signed APK pass. All 129 native/479 overall hashes and
82 actual DEX definition groups verify current sources. The 6,571,329-byte APK
SHA-256 is `6964abbc0b07b575341adebd2becfb2bafdcc3bedf14aa89261e05c66b860e69`.
See `.mdo/E159-final-identities.log`. Windows E150/world E146 are unchanged scoped
reuse. No installed Android UI/provider/device qualification is claimed.

Public document/input routing, hidden rearm, protected navigation/takeover/nested
preparation and remaining rollback/stream/platform gates remain open. Public
availability/automation admission stay disabled; the product create path is wired
behind that gate. The phone and current WebView remain deferred, without ADB,
external model/page access or production publication.


### E160 explicit protected-document rearm and native-close ordering

The actual frame router can now restore a UI-suspended protected popup on an
explicit fresh inventory read. It retains the exact native view, navigation epoch,
original handoff/connection and document quota. Pinned reads, effects, unknown
cancellation, manual takeover, changed channel/epoch and download-only popups
cannot create a replacement realm. A still-running cancelled job must deliver
its original terminal receipt before rearm. Concurrent inventory requests share
the new marker and ordinary worker-busy result.

An issued proof is not world readiness: the UI waits for a validated unique root
isolated world in the original inventory. A loading inventory may be read again
explicitly under the same marker; there is no background retry or URL/navigation
replay. Repeated failure cannot silently allocate more worlds, and the original
16-declaration quota survives UI cycles. Late replies cannot make a hidden,
closed or replaced document ready.

The stale-reference regression also reproduced a native close race: registering
destruction revoked the native proof before the worker could see deliberate
attachment retirement. Split data-only authority retirement from native close,
and perform that first half before proof revocation. It uses the existing close
budget and cannot synchronously reenter the disposer. The controlled observer
checks this exact production boundary before native registration.

Sixteen new actual-product modes/772 checks pass with controlled UI/provider
peers: reshow, repeated cycles, hidden refusal, stale pins, viewport invalidation,
parent-first retirement, cancellation, changed epoch/channel, manual ownership,
unsupported/unique-context failure, concurrent reads, late reply, in-flight close
and quota exhaustion. Evidence: `.mdo/E160-hidden-restore-red.log`,
`.mdo/E160-restore-matrix.log` and `.mdo/E160-restore-final-matrix.log`.
Fresh full native363/13,176, JS466, Python174 of176 (two existing Windows
symlink-privilege skips), creation850/close77, popup110/25/7, save47 and signed APK
checks pass. All 347 earlier popup mode counts remain unchanged. All 129 native
and 479 overall input hashes plus 82 actual DEX definition groups verify the
current code, including rearm state/validation and data-only retirement methods.
The APK is 6,571,329 bytes, SHA-256
`f68320393cea1d248582f1694b74031be07266d3f16ca72c55d9604f7e46d289`.
See `.mdo/E160-final-identities.log`. Windows E150/world E146 are unchanged,
hash-verified scoped reuse; this stage does not claim fresh Windows execution.
Public Android automation remains disabled; generic observation/input routing,
protected navigation/takeover/nested preparation, remaining recovery and native
concurrency qualification, and installed-provider/UI qualification remain open.
Phone/WebView checks remain deferred; no ADB or production action was used.


### E161 concurrent retirement snapshots and original close clocks

Two deterministic scheduling regressions reproduced a remaining check/read race
in the actual original popup broker. If deliberate authority retirement occurred
inside the current-owner read, the preceding retirement check was already stale.
Polling failed with `cdp_popup_broker_opener_lost`; event delivery failed with
`cdp_popup_candidate_retired`. Both unnecessarily closed the shared original wire.

The worker now checks deliberate retirement after the current-owner snapshot,
using a fresh clock sample for its already-recorded close deadline. It keeps the
same closeBorn and five-second budget; an earlier poll timestamp cannot make a
just-started close appear to move backwards. Real owner loss, foreign sessions,
missing cleanup and expired budgets retain their original rejection paths.

The controlled scheduler is the existing clock dependency: it trips only inside
the worker's exact `Handoff.eligible` call in polling or event delivery. It invokes
only the data-only retirement half, advances the original clock and then proves
that the still-live native child/shared wire is not closed, followed by actual
product close and exact native detach/cleanup. Two new modes/74 checks pass.
Evidence: `.mdo/E161-retirement-prune-red.log`,
`.mdo/E161-retirement-event-red.log` and `.mdo/E161-retirement-current.log`.
Fresh native365/13,250, JS466, Python174 of176 (two existing Windows symlink
privilege skips), creation850/close77, popup110/25/7, save47 and signed APK gates
pass. All 363 earlier mode counts remain unchanged. The 129 native/479 overall
source hashes and 82 actual DEX definition groups verify the current code,
including the new retirement snapshot helper. The APK is 6,571,329 bytes, SHA-256
`ae1797d07a1d5b260d8201dc69f172ee7750ae0a3ea9d78f84350cd6cbe4538f`.
See `.mdo/E161-final-identities.log`. Windows E150/world E146 are unchanged,
hash-verified scoped reuse rather than fresh Windows/provider runs. This is
controlled scheduling evidence, not Android framework/provider or concurrent
hardware qualification. Public Android automation, generic observation/input
routing, protected navigation/takeover/nested preparation and broader recovery
gates remain open. Phone and current WebView checks remain deferred; no ADB,
live model, external page or production action was used.

### Protected popup opening another popup (private route)

An original `onCreateWindow` callback invalidates its opener's input and document
references. It must not also discard the already prepared physical source needed
to consume that same native Message. The private popup route now retains only an
exact UI-owned preparation receipt when the original protected page, physical
source, native pending callback, document epoch and channel still match. A plain
`popup_pending` reason without that native receipt cannot preserve authority.

The receipt admits native allocation, consumption and proof through the existing
shared worker; it does not admit document execution or input. Explicit inventory
can restore a fresh document only after that original native turn has settled.
Old frame/document pins stay invalid. Navigation, cancellation, manual ownership,
channel loss and unrelated invalidation keep their existing rejection paths.
Already published nested children retain independent native ownership when their
opener closes. Each child still requires its own native destroy, original target
detach and network cleanup before a successful close receipt.

The controlled fixture drives actual product create/frame/input/close fragments,
original native transports and marker proofs, including parent close, stale pins,
blocked reads/input, wrong pending IDs and cancellation before native consumption.
This is private host integration evidence, not Android framework, provider or
device qualification. Public automation remains disabled and phone/current
WebView checks remain deferred.

### Protected document status and dispatcher selection

The `state` reply identifies an original protected popup with
`frame_automation:true` and `isolated_world.adapter:"android-native-frames-v1"`.
This also applies during its original native handoff before UI publication, and
while its physical owner is retiring. It selects the existing native frame
driver; it is not public automation permission. `browser_use.available` and the
global product admission remain disabled until qualification is complete.

This descriptor reports the original UI-owned page's current readiness rather
than the inherited profile's legacy document world. `native_document` is null:
frame/document references must come from the native frame inventory. `pending`
is a bounded document-worker job count (zero or one), with
`pending_scope:"document_worker"`; legacy callback and world-budget counters are
not fabricated for this different adapter. Ordinary pages keep their existing
`android-main-document-v1` state contract.

Status reads only observe existing handoff/network/document/input state. They
do not prepare a popup, submit a job, restore a document, construct a world or
consume a pending native Message. A hidden/suspended page remains unready after
visibility returns until an explicit fresh inventory restores its original
epoch. Startup, busy/restoring, download-only, owner loss and document change
remain distinguishable. Protected popups reject the legacy `automation` command
before looking up or evaluating a legacy world, including during construction.

These contracts are controlled product integration evidence. Native navigation,
upload/takeover integration, public recovery/arming and provider/device
qualification remain separate work; the new routing flag does not enable them.

### Recovery before page-expression preparation

The shared frame driver now lets fixed native inventory/warmup restore a
suspended original document before asking for popup protection. Each subsequent
page expression waits for the originating action's preparation acknowledgment
and a fresh cancellation/ownership/deadline checkpoint. Both the frame reader
and direct frame methods use this gate. Legacy single-document drivers retain
their earlier preparation order. This changes no native admission or stale-pin
rules and never retries a page mutation.

The internal driver declares `evaluationPreparation:"per_expression"` and
honors the read control's `beforeEvaluate` hook. Its synchronous `onDispatch`
notification applies only at an actual selection expression, after the gate.
Thus cancellation during selection lookup/preparation is still un-dispatched;
lost results after the actual mutation retain uncertainty. Callbacks are trusted
host controls, never model parameters or page-world functions.

The cross-layer development probe uses actual dispatcher/driver/reader code,
actual product state/frame/preparation/close fragments and JNI envelope
validation. Four sequential cases cover the old-order refusal, restored
observation/waiting and cancellation with zero page expressions. Each uses its
original Browser wire/native proof and confirms actual close cleanup. Android
UI, protocol transport and page values remain controlled; framework/provider/
device qualification and public admission remain false.


### E162 scope-issued network-source retirement receipts

The original event/capture router now recognizes a narrowly typed retirement
receipt issued only by the original network source's scope guard. A close that
occurs after the broker's current/retirement snapshot, but inside that source
guard, no longer becomes a global `cdp_event_failed`. The event is discarded; a
paused request is retained on its exact original attachment without continuing
it, claiming a body or replaying an action. The original retirement helper still
checks the original closeBorn and native owner; unrelated or expired failures
are not accepted.

Only the new private `MdoBrowserCdpPopupSource.Retired` subtype is caught. The
existing native error code is unchanged. A renderer crash can emit the same text
through an ordinary IOException, but must still report failure. Generic listener,
metadata, wire, request and protocol exceptions are not swallowed. The actual APK
definition gate now requires this private data-only exception class.

Four new controlled product modes/151 checks pass: close inside source event
admission, close inside paused-request capture, close inside managed flow, and a
reported renderer crash.
The three concurrent-close modes preserve the original wire and finish actual
product/native detach cleanup. The crash intentionally retains an unconfirmed original attachment,
reports failed cleanup and fences new creation; its passing test is containment,
not recovery or Android renderer-process qualification. Earlier draft evidence
incorrectly treated every unconfirmed case as an active-download abort; only that
fixture assumption was corrected, preserving all existing download expectations.
Evidence: `.mdo/E162-source-event-red.log`,
`.mdo/E162-source-capture-red.log` and `.mdo/E162-source-retirement-final.log`.
The first full matrix stopped on the earlier stale-publication case with
`cdp_popup_candidate_retired`. Five focused repetitions passed without a fix,
so the observed failure was not dismissed as a consistently reproducible UI
failure. A new deterministic clock boundary exposed the same stale retirement
snapshot in `serviceManaged`, outside its scoped error handler. Read the native
current snapshot before deliberate retirement there too. The new case and five
post-fix stale-publication repetitions pass. See
`.mdo/E162-native-first-failure.log`, `.mdo/E162-managed-retirement-red.log` and
`.mdo/E162-source-managed-final.log`. The corrected production source then passed a complete fresh matrix; no failed
prefix is claimed as a full pass.

Fresh native369/13,401, JS466, Python174 of176 (two existing Windows symlink
privilege skips), creation850/close77, popup110/25/7, save47 and signed APK gates
pass. All 365 earlier mode counts remain unchanged. The 129 native/479 overall
source hashes and 83 actual DEX definition groups verify current code. The new
exception definition extends IOException and has no instance/outer-owner fields.
The APK is 6,571,329 bytes, SHA-256
`c09899ee5f6322b3fb49f8793b47aeaaa8c383112e91da03125370ae54f169ac`.
See `.mdo/E162-final-identities.log`; Windows E150/world E146 remain unchanged,
hash-verified scoped reuse, not fresh Windows/framework runs.

Broader public Android document/input, protected navigation/takeover/nested
preparation and platform qualification remain open; Windows end-to-end completion
audit and final deliverables also remain open. Phone/current WebView checks stay
deferred. No ADB, live model, external page or production action was used.

### Existing native document continuations (E170)

The upload target reservation, assignment, file-input clear and chooser
continuation paths now share `existingDocumentMarker`. An ordinary document's
registry owner is the UI channel; a protected popup's owner is its original
native handoff. Looking up a popup with the channel always rejects its otherwise
valid binding. The helper selects the exact prepared popup document, verifies
its handoff registry entry, marker and document lease, and requires the original
physical view, navigation, visible state and active UI channel. It never creates,
discovers, restores or reconnects a document. A pending protected handoff cannot
fall back to an ordinary document lookup.

The upload and clear controllers recheck that same original marker through their
UI lifecycle. Explicit observation may restore a suspended page, but the new
marker cannot renew an older upload action's authority. Chooser continuation
retains only an already bound original realm with no pending native job; other
invalidations keep the existing cancellation behavior. Native operations still
use `submitExisting`, so loss of the original binding cannot reconnect or replay
an upload effect. Receipt/status and retained-file lifetime policies are unchanged.

The popup continuation gate exercises actual product state, handoff, registry,
worker and JNI envelope validation with controlled UI and renderer peers. It
does not qualify Android URI permission grants, an installed provider's FileList
assignment or protected-popup upload end to end. Public Android automation stays
disabled pending that integration and platform qualification. The phone and its
current WebView remain deferred.

### Protected popup file-flow integration (E171)

The product popup harness now joins the actual upload staging and assignment
fragments, original document continuation, upload coordinator, private picker,
file queue, native data retention and URI read registry on the original proved
popup worker/connection. The existing frame-owner, file-node, picker and clear
protocol peers can attach to an already running document without constructing
another protocol worker or a replacement view. Ordinary-page tests continue to
use the same peers and assertions.

Controlled integration cases cover root and nested-frame upload, cancellation
before a native grant, native file-input clear, cancellation after clear dispatch,
reading the exact granted bytes after staging expiry, explicit release, and
closing the popup during a pending upload. Repeated assignment/clear requests
must keep the original receipt and cannot send a second effect. Each case checks
the original native create, connection, detach, physical-destruction and file
worker cleanup receipts. Only the verified ownership marker remains in the one
reused QA cache; no per-case application or Home is created.

The framework view/controller, native chooser response and renderer DOM are
still controlled peers. URI registry/proxy-range reads run the actual file queue,
but this does not qualify Android URI permission delivery, a Binder descriptor,
an installed provider's actual FileList or Android public automation. Those
flags remain false. No provider upgrade, ADB or phone action is required by this
test. Protected navigation/takeover and overall platform admission remain open.


### Explicit native document recovery (E175-E177)

Natural protected-page navigation can be recovered by an explicit fresh unpinned
inventory, using the original physical Source's guarded current document. Native
navigation completion separately reports whether its same committed document is
readable; the dispatcher waits for that state rather than reading a restoring
realm or repeating navigation.

A document binding can also lose its execution contexts without advancing the UI
navigation epoch. A new explicit fresh read can replace that unavailable binding
only after the original Source, Handoff, native frame/loader and settled root are
proved. It revokes old marker/frame references and retains the existing document's
resource quota. A settled original popup-protection continuation survives rearm;
ongoing popup turns, hidden/manual views and unproven roots remain denied. A loss
during an already running request is reported; that operation is not replayed.

Current qualification includes 18 actual JS/driver/JVM recovery paths and the 477-mode
native matrix with controlled renderer/UI peers. These checks do not establish
Android framework, installed provider, URI/Binder, gesture or device qualification.
Public Android automation remains disabled; phone/current-WebView checks are
deferred. See the current [progress and retained evidence](progress.md).


### Same-document/history cleanup boundaries (E178)

A native navigation retires the old action's popup preparation before invalidating
its input/realm. Its new committed document waits for that original preparation
to settle. This includes fragment/history transitions that keep the UI document
epoch, and BFCache restores whose original loader can return without a new network
request. Typed native document/destination proofs and stale navigation-reference
denials still apply; no command is replayed.

Fresh passive reads wait before their first inventory while the host explicitly
reports preparation cleanup as unconfirmed. Once reading starts, a cleanup change
discards successful data rather than restarting the read. A known native denial
keeps its original cause while cancellation, owner and deadline checks still win.
Preparation retirement during temporary admission loss leaves only retry intent;
future inventory must prove the original retained Source and current document.

The host-close regression also forces transport close to race its original
worker. The unfinished receipt retains `cdp_worker_closed`; unconfirmed cleanup
and source release remain unconfirmed. Current qualification includes 20 actual
frontend/JVM paths and the 478-mode native matrix with controlled renderer/UI
peers. Installed-provider/framework/device qualification, complete protected
takeover/release and public Android automation remain unavailable. See
[progress and evidence](progress.md).


### Shared pane mounting recovery (W6)

The app archive now includes the shared UI fix for a task dispatcher superseding
a pending native pane mount. Only the preferred, still-scoped pane can request a
fresh mount after registration loses ownership. The task dispatcher remains
usable until the replacement UI binds; stale callbacks cannot hide/close it.
Fresh Windows and deterministic wrapper checks pass. All20 original JVM/frontend
flows remain current, and the unchanged E178 native478 matrix is rehashed reuse.
These are not installed Android provider/framework/device qualification; public
automation remains disabled and the current phone/WebView checks stay deferred.


### Guarded second navigation and download-only transition (E179 coverage)

Controlled actual product-fragment/common-worker/JNI checks now include a second
navigation while the first committed document still awaits its world. A second
send before the native ACK is denied; after commit, a replacement navigation can
finish without accepting the old world reply or old navigation reference.

A download-only popup can transition to HTML after an explicit original download
decision and confirmed source cleanup. An undecided offer blocks navigation;
the terminal original receipt is retained and no response body is claimed by
this navigation path. These two acceptance cases required no product change.
The fresh native480 matrix and existing20 frontend/JVM paths pass. Runtime,
Windows and signed APK qualification is unchanged hash-verified W6 reuse;
device/provider/framework checks and public Android automation remain deferred.


### Original protocol detach primitive (E180)

The internal Session can now issue one fixed detach for its exact retained,
protected, activated attachment under an internal quiescence permit. Outstanding
requests/responses, offers, streams, cleanup controls or navigation on that
attachment block it; an unrelated page's independent body does not. The matching
empty ACK is recorded separately from native detach/source/stream cleanup.
Uncertain or failed commands are not resent, and old protected authority cannot
be reused. The caller must separately prove physical human ownership and
descendant cleanup. This is the foundation for protected popup takeover/release,
which remains unqualified; this change does not enable public Android automation.
Fresh controlled Session and existing full JNI/transport/product regressions,
the actual signed APK and DEX checks pass. Installed-provider/framework/device
qualification and phone/current WebView checks remain deferred.


### Exact physical human-transfer transaction (E181)

The committed original Handoff can now transfer only its exact native Promotion
to human ownership on the UI thread. Typed retirement precedes manual assignment;
old automation authority is revoked. Both new and already-queued native close
notifications are deferred during that transaction. Success preserves the actual
page while ending the old Promotion; an explicit host destruction can still win
and yields failure rather than a fabricated successful handover. Old authority
never revives after reversing a manual flag.

Eight controlled actual native/product/worker/JNI paths and the full488-mode
matrix pass, including UI notification failure and final explicit human-page
close. This is physical-ownership integration, not yet the public protected
takeover/return coordinator. Protocol/descendant/source cleanup, authentic detach
event and fresh return proof remain required. Provider/framework/device checks
and public Android automation remain unavailable; preserve the current WebView.


### Original human leaf retirement and preserved downloads (E182)

The exact native transfer now has an internal product/worker coordinator. It
quiesces unfinished original downloads before source retirement, then requires
the human native ownership/end receipt and independently drained source/body
cleanup before one fixed leaf detach on the original Browser wire. An empty ACK
and matching authenticated native detached event are separate required receipts.
Unsupported/rejected/malformed/missing replies and wrong events cannot complete
that join or replay the detach. Nested document sessions are still explicitly
unqualified and fenced before the command is sent.

The human page and original download catalog/IDs/quota remain. Completed bytes
stay verified/readable even after uncertain protocol cleanup; pending and held
stream sources retain user cancellation and real abort/I/O close receipts. A
later actual physical page close frees the retained cache independently of a
still uncertain protocol guard. Fourteen controlled product/native-lifecycle/worker
JVM cases, the full 502-mode regression, existing JNI/transport gates and actual
signed APK/DEX checks pass. The native
event guard-removal witness reproduces premature ACK-only completion.

A real physical close that wins before detach dispatch now drains its own
authenticated native event without sending an extra detach or poisoning the
shared wire. Its regression failed before the correction and passes afterward;
it does not fabricate a human ACK/cleanup receipt for a command never sent.

The public protected takeover/return gate remains closed. The download controller
can rearm after the joined receipts, but full browser return requires fresh
exact-view marker/frame/Fetch proof; old popup/world authority stays retired.
Full Activity/UI/provider, descendant cleanup and in-flight public operation joins
remain required. Phone/current WebView stay deferred and public Android automation
remains unavailable.


### Same-view return with fresh proof and completion admission (E183)

An internally transferred protected popup can now join the ordinary native
network rearm only after exact human/native/protocol/source receipts. It keeps
the same physical view, profile, channel/quota and download controller/catalog;
no blank navigation, URL reload or replacement view is used. A fresh native
network marker and frame/Fetch proof must finish before manual state clears.
A later ordinary document read creates a fresh isolated world, and old protected
references/Promotion/Handoff authority stay revoked.

Return admission checks input, dialog/picker, capture/upload, pending popup and
host cleanup state both before proof and on completion. Changed ownership or
admission keeps manual control and drains the new connection. The original
network_owner_changed cause survives; explicit retries after known rejection or
stop use another fresh proof instead of replay. Completed original bytes remain
readable, and a new download after return uses the new Connection.

Twenty-seven controlled product/native-lifecycle/worker JVM cases and the full
529-mode gate, existing frontend/JNI/transport gates and actual APK/DEX checks
pass. The input cleanup-failure completion witness fails when the final checks
are deliberately removed and passes with current source. UI/profile/renderer
seams remain controlled; this does not qualify a real Android provider.

Public protected takeover, in-flight/descendant joins, structured toolbar error
presentation, unacknowledged-startup cancellation recovery and full Activity/UI/
device qualification remain open. Public Android automation remains unavailable;
the phone and current WebView stay deferred. Overall feature goal remains active.


### Shared control toolbar receipt handling (E184)

The shared UI now treats failed network-return receipts as control notices,
preserving the displayed page and manual ownership. Canonical refusals and typed
network cleanup get useful bounded text. Malformed replies cannot grant control;
pending original receipts and older polls cannot overwrite UI authority. Dialog
and picker controls share the validation. Component bridge tests and actual
Windows native success/fault-injected UI checks pass. The signed APK contains
this updated UI; Android Activity/provider/device verification remains deferred.
The E183 native 529/frontend 20 evidence is unchanged hash-checked reuse. Public
Android automation and protected takeover gates remain unavailable.


### Pre-acknowledgement startup cancellation (E185)

Ten additional product/native-worker cases cancel the same-view return while
native marker proof or Fetch.enable acknowledgement is still pending. Stop,
close, channel loss, queued late ACK, retained bytes, explicit fresh retry and
uncertain native close are covered. No product defect was reproduced; existing
code preserves manual control and denies retry when exact source cleanup is
unconfirmed. Native close receipts do not fabricate a Fetch.disable ACK.

Fresh native 539 and frontend 20 gates pass. E184 product/artifacts and Windows
runtime checks are unchanged hash-checked reuse; the APK above stays current.
Controlled peers do not qualify an installed Android provider. Public protected
takeover/in-flight/descendant integration and Activity/device checks remain open;
the phone/current WebView stay deferred and Android automation remains unavailable.


### Protected takeover releases its old UI navigation reservation (E186)

After native human ownership commits, the protected page now explicitly retires
its exact unfinished UI navigation authority and releases the owned loading/
navigation reservation. The native result remains retained and is not upgraded.
Six queued/sent/settled and same-view return cases preserve original data and
reject old references. Deferred startup-cancellation callbacks also now return
their genuine original token, held only in memory, rather than an invalid value.

Fresh native 545, frontend 20, Python and the signed APK/DEX gates pass. Shared
UI/Windows evidence is unchanged E184 reuse. The earlier existing UI-loss test's
two retained generic network owners have not been explained despite 50 targeted
passes; its failure and additional exit diagnostics remain preserved. This is
not claimed fixed by the navigation repair. Public protected takeover, other
in-flight/descendant joins and full Activity/provider/device qualification remain
open; the phone/current WebView stay deferred and Android automation stays gated.


### Confirmed terminal-source release after UI shutdown (E187)

An actual worker-shutdown/reentry witness reproduces a confirmed connection row
left behind when native worker exit finishes before the UI abandons delivery.
The UI drop now always revisits the existing guarded release predicate. Active
or failed native cleanup stays retained; two deterministic success/failure
variants, old pending-close cases and the complete generic worker gate pass.

Fresh native 545/generic-worker 300/frontend 20 and signed APK/DEX checks pass.
Windows/shared UI results remain E184 reuse. This fixes a proven defect matching
the earlier retained-source symptom, without an exclusive cause claim for that
single uninstrumented observation. Public takeover/descendant/provider/device
qualification and the full goal remain open; Android automation stays gated and
the phone/current WebView stay deferred.


### Original native descendant cleanup (E188)

Document cancellation and physical native detachment now have separate bounded
records. The original native root receipt cannot release a protected attachment
while any original iframe session still lacks its own native detach receipt.
Parent-first events retain their cleanup routing until children also detach;
late native attachments on a closing document gain no DOM/RPC authority. Invalid
ancestry, attachments from a detached parent and the 64-session native limit fail
closed. Authentic child detach can terminate its pending attached RPC without
closing a different page's shared wire; stale replies cannot revive it.

Ten new controlled cases pass 604 checks. The complete
fresh native gate passes 555 modes / 23,053
reported popup checks plus generic worker 300, API26/JNI/transport and ASan/UBSan;
prior 545 business outcomes are unchanged. Actual frontend/JVM 20 and Python
178/180 pass (two existing symlink-privilege skips). The complete signed current
APK above includes the new Session and Broker implementations, with actual DEX,
source, payload and signature checks. No phone/provider qualification is claimed.

Human takeover still needs fixed original-root auto-attach disable/ACK joining
after data cancellation and the public input/capture/chooser teardown join.
The existing public takeover gate and overall Android automation unavailability
remain. Current phone/WebView verification is deferred; this phase does not
constitute a production release or device installation.


### Grandchild RPC cancellation on ancestor detach (E189)

An authentic native ancestor detach now fences ordinary RPC/data admission for
its document descendants as well. A pending grandchild RPC ends with its
original failure and possible-effect bit while the root physical owner remains
current. The leaf still needs its own native detach receipt before cleanup can
be confirmed. Late replies cannot alter the old result or corrupt another
page's current RPC; no operation is replayed.

Two new original-worker read/effect cases pass 152 checks,
including a late original reply delivered during another page's pending RPC and
the separate leaf native cleanup receipt. The fresh native gate passes
557 modes / 23,205 reported popup checks,
generic worker 300, API26/JNI/transport and ASan/UBSan. Actual frontend/JVM 20 and
Python 178/180 pass (two existing symlink-privilege skips). The signed APK above
includes these current Session/Broker implementations with actual DEX/source/
payload/signature checks. Public takeover/automation gates and deferred current
phone/WebView verification remain unchanged; full framework/provider/device
qualification is still required.


### Native root retirement after data cancellation (E190)

Cancelling a root data call no longer erases its retained native identity or
paused records. The fixed original detach can finish under independent native
ownership while ordinary Runtime/DOM/source access remains revoked. Actual ACK,
native event and transfer cleanup receipts remain independent; uncertain cleanup
does not permit reuse. Five new controlled root cases pass 317
checks, including completed/cancelled transfers and a held body read. The direct
control gate passes 3205 with ordinary RPC denial,
original-identity/owner/replay checks and pending-pause retention after data loss.

Fresh native verification passes 562 modes /
23,523 reported popup checks plus generic worker 300,
API26/JNI/transport and ASan/UBSan. Actual frontend/JVM 20 and Python 178/180 pass
(two existing symlink-privilege skips). The signed current APK above passes
actual DEX/source/payload/signature checks. Root-specific auto-attach disable,
remaining descendant/control/public teardown joins and full Activity/provider/
device qualification remain open. Automation/takeover gates and the deferred
phone/WebView arrangement remain in force.

### Fixed original-root auto-attach retirement primitive (E191)

The native Session now has one private, fixed Target.setAutoAttach(false) cleanup
operation addressed to the exact retained original root, after ordinary data
admission is fenced. Its typed command context binds the native original,
internally constructed parameters, original session and serialized command ID.
Only its matching reply can cross the data fence; ordinary Runtime/DOM/source
RPC stays unavailable, and another original page retains its separate admission.
It does not send a Browser-global disable. Wrong/reused native identities,
reentry and replay are rejected; actual native end also forbids a new dispatch.

The fixed ACK is not a descendant detach, source abort/stream close or final root
detach receipt. Existing native pauses stay charged after success and still
prevent premature root retirement. Thirty-six new direct worker cases cover both
live-data and already-fenced originals, ACK/native-event ordering, late original
data and child attachment events, peer isolation, ownership loss, protocol/send/
timeout failures and no replay. The direct control gate now passes 4,639 checks
(1,434 added); its two real native transport sessions are unchanged.

Fresh full native verification passes 562 modes / 23,523 reported popup checks,
generic worker 300, 134 current input hashes, API26/JNI/transport and ASan/UBSan.
All prior 562 business results match with no semantic or timing differences in
this run. Actual frontend/JVM 20 and Python 178/180 pass (two existing Windows
symlink-privilege skips). The rebuilt signed APK above has the actual fixed
cleanup method, native context class/private constructor and original/params/ID
fields, with source/payload/package/signature checks. This is controlled worker
coverage, not Android framework/provider/device qualification.

The broker has not yet adopted this primitive in its human-retirement path.
Joining all original descendants and retiring raw paused requests/in-flight
continuation receipts remain required before that path can be enabled. Public
protected takeover and overall Android automation stay gated; phone/current
WebView checks remain deferred. Windows/shared resources are unchanged and their
prior verified results are explicitly reused. The overall goal remains active.

### Original paused-flow receipts survive data cancellation (E192)

An actual Session defect was reproduced: cancelling a sent original Fetch
continuation caused its eventual native ACK to be discarded as old data, leaving
the original pause permanently charged. Original request/response continuation
and abort now have a separate immutable native receipt on the existing bounded
control dispatcher. That exact ID/session receipt survives data and attachment
retirement, while the cancelled data outcome remains terminal. A matching empty
success retires only its frozen pause; protocol rejection retains that obligation,
and corruption/timeouts retain the existing connection-failure behavior. The old
deadline is never renewed and no sent operation is replayed.

The private abortProtectedPause primitive can only abort an exact original raw
pause. It cannot continue/read/claim a response or accept a caller-selected ID.
It waits already-issued native controls, preserves independent body/stream
cleanup, and does not repeat a sent abort. Pre-send cancellation leaves a proved
NOT_SENT outcome rather than a permanently pending receipt. Late pause metadata
is retained only for the exact protected, data-fenced original root; ordinary
DOM/source operations remain unavailable. This primitive is not yet wired into
the broker's human-retirement coordinator.

Sixty-seven new direct cases cover late ACK/errors/timeout/native retirement,
peer RPC isolation, fixed raw request/response aborts and unsent cancellation.
The direct gate passes 7,048 checks (2,409 added), with two real native transport
sessions unchanged. Two actual controlled product cases also verify the original
pause receipt before emitting root native detach and preserve the other page.
Fresh full native verification passes 562 modes / 23,515 reported popup checks,
generic worker 300, 134 current input hashes, API26/JNI/kernel transport and
ASan/UBSan; prior business outputs have no semantic/timing differences in this
run. Actual frontend/JVM 20 and Python 178/180 pass (two existing Windows symlink
privilege skips). The signed APK above passes actual native-flow class/constructor/
method/field, source/payload/package/signature checks. Windows/shared resources
remain unchanged with explicit current-hash reuse.

Full original-root auto-attach/descendant/raw-pause joining, final original
control receipts in the coordinator, public input/capture/chooser teardown and
Activity/provider/device qualification remain open. Android automation and
public protected takeover remain unavailable; phone/current WebView verification
is still deferred. The overall goal remains active.


### Original human-root retirement joins (E193)

The internal human-retirement coordinator now joins the original source and
transfer cleanup, fixed root auto-attach stop, authentic descendant ends, fixed
raw-pause aborts, root detach ACK and native root end. It preserves the manual
physical view, original profile/catalog/quota and completed file bytes. Pending
bodies still require their original abort/stream-close receipts; no DOM, body,
world or navigation authority is reopened. The public takeover gate remains.

Two reproduced defects are repaired. A valid raw pause arriving during fixed
root detach no longer invents owner loss: initial busy admission stays strict,
root data is fenced before sending, and the pause remains until authentic native
end. Conversely, native root end immediately revokes data but cannot acknowledge
an outstanding original Fetch/body/offer control. The broker waits those actual
receipts before reporting detached; root ACK remains independent too.

Twenty new controlled product modes cover those orders and child-retirement
ACKs, late children, pending bodies, raw pauses, rejected/missing/malformed stops
and incorrect ancestry. Two previously unqualified internal descendant cases now
finish with their genuine stop/child/root receipts. Exact prior business outputs
are compared, allowing only those intended changes. The direct cleanup gate has
7,137 checks, including two new late-pause cases, with two native transports.

A frontend fixture race was separately proved and repaired without changing
product recovery behavior. Its old receive counter advanced before dispatch
and even on empty polling. The test now holds the exact native context event,
proves that premature condition, releases it and waits actual event dispatch.
Seven context-loss modes and the complete twenty-mode frontend gate pass with
explicit dispatch-join proof; earlier failure logs remain available privately.

Full Activity/input/capture/chooser/profile teardown, ongoing native-origin
readiness and provider/device qualification remain outstanding. These controlled
native-worker/product checks do not qualify Android automation. The current
WebView and deferred-phone arrangement remain unchanged; the full goal is active.


### Native control admission precedes cancellation (E194)

A reproduced control-button defect revoked the protected page's document before
reporting popup_takeover_unqualified. The native owner remained live and manual
mode was false, yet the old Agent document/read authority had already been lost.
A pure networkTakeoverAdmission check now runs before document invalidation. The
actual network takeover also rechecks it; already-admitted ordinary takeover
still fences document data before source retirement and native cleanup.

The exact deployed takeover/release branch and its real network prelude now run
in the existing JNI/product harness. Four refused-control cases preserve the
same marker, frame/document, profile/input/manual state, pending original read
and completed download bytes across two refusals. Six more cases run the actual
release branch after an internally qualified human transfer: success, preserved
bytes, failed native proof, lost channel, duplicate return and capture preflight.
Fresh native proof/Fetch ACK/UI delivery must precede manual release; old frame
references remain invalid. The current ordinary creation/control gate also
passes with explicit protected peers that throw if entered by that fixture.

These are controlled native/product checks, not Activity/provider qualification.
Accepted protected takeover still needs the full outer input/capture/upload/
chooser cancellation and original-cleanup join. Its public gate and overall
Android automation remain unavailable; phone/current WebView checks stay deferred.


### Bounded original human-control preparation (E195)

A private UI-only HumanControl receipt now reserves a single native handoff
before revoking the original attachment. Reservation does not close document
leases or cancel user work. Start retires that exact Handoff before the real
PopupNetwork coordinator fences its document/source leases. Commit requires
its same physical view and clean original input; it cannot be replayed.
The receipt retains only a weak native view and has one suspend-inclusive
five-second budget, including preparation, cancellation and commit. A rejected
timer leaves the original page live. Cancellation before start releases only
the reservation; after start it retires the original owner instead of restoring
old Agent authority. Direct late start reports the original expiry even when
its uptime callback has not run; resume reconciliation observes the same budget.

The page coordinator exposes internal beginControl/controlReady/completeControl
steps. It preserves completed download bytes, cancels original reads and waits
for its own worker/input/capture/upload/chooser cleanup plus the exact physical
owner, profile and channel before native commit. Protocol retirement still
requires its original root command acknowledgement, native detach event and
source/body/descendant receipts independently; a native transfer is no substitute.

Sixteen new JNI/product cases cover preparation cancellation/expiry/resume,
duplicate or wrong-view reservation, timer rejection, cancellation/expiry/close
or channel loss after start, actual input DOWN/CANCEL, in-flight original reads,
retained file bytes and a controlled pending capture peer. The last peer tests
the gate only, not a real retained encoder lease. No new view, execution world
or navigation is introduced by these tests. Existing immediate internal callers
retain their clean-native-input requirement and independent protocol cleanup.

The public main-button protected takeover remains popup_takeover_unqualified.
Its actual outer resource cancellation and final UI/reply publication are not
yet wired to this staged coordinator. No Activity/provider/device qualification
or Android automation capability is inferred. The current phone/WebView checks
remain deferred by the user.


### Actual original resource cancellation (E196)

The internal PopupNetwork.beginControlAndCancel coordinator captures its original
capture/dialog/popup/upload bindings before starting the typed human receipt.
After original Handoff retirement and document/source fencing, it invalidates the
original CDP registry, stops native input and cancels capture, transient upload,
chooser and pending popup resources. It rechecks the exact physical owner,
profile/channel, deadline and every captured resource binding before each effect.
Reentrant replacement or channel loss stops further cancellation. A foreign
capture/dialog/upload binding is refused before revoking document authority.
No adopted file-data owner is retired by takeover; only physical destruction does
that. JS dialogs retain the existing manual-user handling behavior.

Cancellation does not assert cleanup. The existing controlReady gate still
requires original worker jobs, native input, actual capture busy/cleanup status,
upload/chooser/popup receipts and native ownership. Real MdoBrowserCapture core
cases now exercise pending guards, visual callbacks, queued/running encoders,
encoded bytes, final guard, failed raster release and expiry/close while encoding.
A held original encoder keeps its real capture-budget slot through cancellation;
late encoded bytes are zeroed and the original callback stays terminal once.
A failed raster release remains unconfirmed and its budget slot stays reserved.
Actual input DOWN/CANCEL, private-picker callback cancellation and transient upload
scope versus physical data-owner lifetime are joined in the same product harness.

Only Target/Raster/native callback timing is controlled; there are no framework
pixels or installed provider guarantees. New upload checks prove actual scope and
owner flags, not a new combined renderer-assigned URI/byte grant. Existing file
flows remain separate evidence. The public main-button route still refuses
protected takeover; next work is the bounded outer request, command exclusion,
independent protocol retirement and guarded final manual/reply publication.
Global Android automation and Activity/provider/device qualification stay open.

### Readiness after native root-context loss (E212)

A dispatched Runtime.executionContextsCleared event can invalidate the original
native root/default context before the Activity reports a new navigation epoch.
The old document-state getter still advertised installed/ready in that interval.
The getter now consults the existing worker's concurrent/volatile unavailable
fence. It neither sends protocol requests nor creates a world or silently rebinds;
fresh explicit inventory retains its original-source recovery requirement.

New cleared/default-context-destroyed cases report document_unavailable, while
an unrelated destroyed context ID preserves readiness. These use the actual
native worker and product state getter with controlled UI/provider peers. All16
state cases, full native650, frontend/JVM/JNI recovery20, ordinary create/control
850 and acceptance15 pass. No old647 business output changes (assertion counters
excluded). Both Android artifacts above are rebuilt, with the actual main DEX
state-method instruction checked for its worker-fence call. This does not qualify
all per-frame isolated realms or ongoing native-origin transitions. Physical
Activity/provider/lifecycle/device checks and overall Android automation remain
unqualified; the user's phone/current WebView deferral remains in effect.

### Original atomic root readiness (E213)

The original isolated root realm can disappear while its default context and
Activity epoch remain live. A native-world destruction test reproduces the
remaining ready:true snapshot after E212. Frame/world handling already revokes
the original Node epoch. Publish that same AtomicBoolean only after a successful
worker snapshot proves the root settled and isolated; read it alongside the
graph's live flag from the UI. The binding's graph and published-root references
are volatile. UI status never traverses graph/world maps or creates a realm.

Root isolated loss and loading revoke reported readiness. A loading-stop event
alone cannot revive the old publication. An unrelated child realm loss keeps
root readiness; its own original frame references remain independently fenced.
Fresh explicit inventory recovers with the original native document/source
proof. A first recovery fixture lacked that proof and correctly got
navigation_unproven; its positive setup now emits and joins an original native
root commit instead of weakening admission.

All21 state modes and full native655 pass, with285 assertions in five new modes.
Fresh frontend/JVM/JNI20 and acceptance15 pass. The old650 business outcomes
match, with two established loading-timeout scheduling diagnostics separately
retained; final detach/destruction/source-release requirements are unchanged.
Ordinary create/control850 is exact-input reuse. Both Android packages are
rebuilt; actual main DEX defines the volatile fields and the atomic-only reader,
snapshot publication and binding call. Provider/Activity/lifecycle/device and
whole-goal qualification remain incomplete; Android automation stays disabled.

### Navigation and execution-world readable state (E214)

An original committed/settled navigation can retain page_current:true while its
root execution world disappears. The old navigation state incorrectly reported
page_ready:true after isolated_world.ready had become false. Reproduce this
through actual product/JNI routes and native context events; retain the red log.
Both public states now use PopupPage.documentReadable, which preserves the UI
bootstrap, exact proof ownership/currentness and original published worker fence.
Status reads do not create worlds, rebind documents or issue/retry navigation.

Three new cases cover isolated-root loss, all/default-context clearing and
explicit guarded inventory recovery with the original navigation reference.
They preserve the original native document, Activity epoch and navigation ID;
recovery creates a fresh realm without replaying the single navigation command.
The internal documentReady bootstrap flag has a different purpose and remains
unchanged. Existing navigation association/recovery needs no product repair.

The first full run exposes an old activation test's thread-order assumption:
transport close notification precedes disarm's process-admission release. Join
the actual final cleanup in its bounded wait; do not weaken product cleanup.
Fresh frontend20, full native658, ordinary create850/async77, Python208/210 (two
existing Windows symlink skips) and acceptance15 pass. Both Android packages are
rebuilt; actual DEX links each public state to the shared original-worker check.
Windows/native/app payloads and the portable plugin remain unchanged. Android
framework/provider/physical qualification and the full objective remain open.

### Current phone and profile-only terminal state (E218)

The current RMX5090/API36/WebView138 phone is authorized and tested. The
physical-only profile is an intentional terminal state without world readiness;
the diagnostic now verifies that state and reports isolated_world_unsupported
instead of waiting for a timeout. Actual device gates complete with6pass and
2unresolved: hover has a trusted move but no CSS hover, and capture lacks an
initial scale callback. Failure evidence is retained without weakening checks.
The full QA main Activity and its manual native browser are visually observed.
No guarded Android automation or broader host/lifecycle qualification is claimed.
See the E218 section in [acceptance](acceptance.md) for connection setup, exact
phone results, APK identities, cleanup and remaining work.

### Initial native scale (E219)

The current phone exposes a missing initial onScaleChanged notification. The
common listener now samples a weak original native geometry candidate after a
visual barrier, with native callbacks taking precedence, bounded outstanding
callbacks and navigation/close/duplicate/thread fences. Original capture privacy
and geometry guards remain unchanged. Actual diagnostic pixels pass21 and the
complete main manual-browser state reports native_scale_ready:true. Component
results are7pass/1unresolved (CSS hover); overall automation remains disabled.
See the E219 section in [acceptance](acceptance.md) for final artifact hashes,
fresh source-bound regressions, exact scope and cleanup.

### Touch feedback and mouse hover (E220)

Cold hover works on the current phone; after touch, hover first matches then
is overwritten by delayed tap feedback. The owned target records accepted
touch release and the sequencer defers only a recent post-touch mouse hover,
then obtains fresh guards before one native event. The delay is bounded and
cancelable; cold hover stays immediate. Focus, touch, replay and global admission
are unchanged. Fresh84 timing checks, JNI input/cancel and frontend20 pass;
both APKs rebuild. Final-source device and main-install checks are deferred by
the user after USB disappearance, so the candidate remains device-unqualified.
See [acceptance](acceptance.md) for source inference, evidence and limits.

### Native input deadlines across suspend (E222)

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

### Capture deadlines across suspend (E223)

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

### Original popup acknowledgement expiry (E224)

The original native callback now checks its saved5000ms deadline directly, even
when the overdue UI timer has not run. Late or unprovable acknowledgements keep
popup_transport_unconfirmed; actual native consumption still drains its original
reservation and cannot authorize a child-world binding or a successful Stop.
Fresh44 controller and18 actual native-source deadline assertions pass, alongside
focused original JNI lifetimes. Current APK DEX verifies the direct clock check
before the real private Budget release. No transport replay or capability gate
change. Phone qualification remains deferred. See [E224 progress](progress.md).

### Combined current software integration (E226)

The complete current native/JNI entry point passes all658 workflow modes plus
its following frame/input/capture/file/chooser/cleanup checks, under ASan/UBSan
and API26 compilation. Fresh frontend20 uses the same current classes/library.
The input fixture's false clock acceleration is independently checked (12
assertions); production deadlines are unchanged. Source/host hashes match the
original pre-run snapshot. Delivered E224 APKs and their source/signature/DEX
proofs remain byte-identical. These are current combined software results with
controlled UI/provider peers, not physical Android qualification. Global Agent
admission stays disabled. Final device install/hover/guarded host/URI/lifecycle/
Doze and restoration remain user-deferred. See [current acceptance](acceptance.md).
