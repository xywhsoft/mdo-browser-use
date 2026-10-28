# mdo Browser Use

Official Browser Use plugin, shared C/JavaScript services, and the native browser
enhancements needed by mdo. Development is active; see [progress](docs/progress.md).

## Development deployment

```powershell
python tools/dev.py setup --mdo-source D:\GIT\mdo
python tools/dev.py start
python tools/dev.py status
python tools/dev.py stop
```

The setup preserves this repository's origin. A **single** sparse mdo source
checkout and one reusable development Home live under `.mdo/`. Local Git objects
are shared with the provided mdo repository, and a hash-checked existing host is
reused. No model credentials, existing user Home, test application copies, or
downloaded browser engines are copied. The local HTTP service listens only on
loopback; its port is recorded in `.mdo/environment.json`.

The default service is headless for SDK checks. Native browser verification will
use the same deployment in window mode, not a second application installation.
Running `setup` again reuses the same files. Stop the service before rebuilding.

For the current Windows automation backend, build the enhanced host once:

```powershell
python tools/dev.py stop
python tools/build_native.py --source D:\GIT\xserver
```

This uses one pinned SDK checkout and one object directory under `.mdo/`, reusing
local Git objects. GCC/MinGW is needed for development, not for the shipped plugin.

Install the current development plugin into this deployment, then restart the
same process when the installer reports a native restart requirement:

```powershell
python tools/contracts.py --check
python tools/dev.py stop
python tools/apply_host.py
python tools/dev.py start
python tools/install.py
python tools/dev.py stop
python tools/dev.py start
python -m unittest discover -s tests -v
node --test --test-concurrency=1 (Get-ChildItem tests -Filter 'test_*.mjs' -File | Sort-Object Name | Select-Object -ExpandProperty FullName)
python tools/native_probe.py
python tools/creation_probe.py
python tools/native_frames_probe.py
python tools/drag_lifetime_probe.py
python tools/public_frames_probe.py
python tools/frame_scroll_probe.py
python tools/visual_probe.py
python tools/keyboard_probe.py
python tools/capture_readiness_probe.py
python tools/capture_privacy_probe.py
python tools/capture_privacy_public_probe.py
python tools/dom_keyboard_probe.py
python tools/android_world_probe.py
python tools/android_dialog_probe.py
python tools/android_device_probe.py --mode native-dialog
python tools/sdk_probe.py
python tools/managed_probe.py
python tools/restart_probe.py
python tools/upload_restart_probe.py
python tools/upload_restart_probe.py --directory
python tools/upload_probe.py
python tools/upload_tree_probe.py
python tools/upload_directory_probe.py
python tools/upload_admission_probe.py
python tools/upload_admission_probe.py --about-blank
python tools/download_probe.py
python tools/save_probe.py
python tools/publication_recovery_probe.py
python tools/download_ui_probe.py
```

Generate the portable development plugin with `python tools/package_plugin.py`.
The allowlisted JSON and SHA-256 inventory go to `dist/`; no app staging tree,
Home, provider settings or browser profile is included. The installer uses the
same local ecosystem plan/import and enable flow as the UI. A separately applied
old development plugin intentionally conflicts with ecosystem ownership;
`python tools/package_probe.py` migrates only this project's exact known payload
and verifies import/restart/export in the same Home. See
[distribution requirements and limits](docs/distribution.md). The preview needs
this project's enhanced Windows host; stock 0.7.0 and Android automation are not
advertised as compatible. This does not publish a release.

The matching enhanced Windows development executable is produced by
`python tools/package_program.py` after stopping this same service.
`python tools/program_probe.py` starts that artifact, exercises the real
C → JS → native browser path and restores the headless service, using the same
Home. See [program packaging and recovery](docs/program.md).
`python tools/package_lifecycle_probe.py` verifies uninstall/reinstall,
retained-generation conflicts and actual startup recovery in that same Home.

The native probe reuses this exact deployment, briefly opens its real WebView2
window with local fixtures, and restores headless mode afterwards. No live model
or external website is needed. Its latest result is `.mdo/page-observation-result.json`.
Use `python tools/native_probe.py --dialog-expiry` for the longer lifecycle gate
that also waits for the real 120-second native dialog lease.
Use `python tools/native_probe.py --download-only` for native download identity,
response bytes, cancellation and cache lifecycle checks in the same Home.
`--startup-only` measures four sequential hidden/visible native starts. It retains
DOM/load timing when slow, and showing a delayed view cannot turn a startup
that exceeded the default readiness bound into a passing measurement.

`creation_probe.py` measures eight sequential cancel/recreate cases in the same
native profile, and deliberately loses one creation reply with a one-millisecond
test deadline. It observes the same native identity until ready, without another
create or navigation. This synthetic deadline is not the production timeout.
Closing a creating native view now settles its original request before the close
acknowledgement, without waiting for a provider callback; late callbacks cannot
reply twice. The gate also closes a creating popup and verifies immediate opener
deferral release, with nine distinct cancellation replies in the real host.
Creating views expose read-only phase/elapsed diagnostics. The shared dispatcher
waits within the original deadline and cancellation scope instead of treating
initialization as missing automation. An independent 16-slot native creation
budget also counts cancelled work still retained by a provider callback; the
ordinary eight-live-view limit remains separate. The focused gate checks the
actual waiting dispatcher and eventual reservation drain. Deterministic C++
fault seams exercise budget saturation without hanging or duplicating a browser.
The receipt is `.mdo/native-creation-result.json`. The full native gate also
retains read-only post-timeout creation observations; late readiness keeps the
original gate failed. An earlier unreproduced production-budget creation timeout
remains recorded for investigation.

`native_frames_probe.py` verifies the private native frame/session foundation:
two loopback sites, real cross-process iframes, nested ancestry, isolated reads,
stale document/view rejection and resource-limit recovery. It uses the same
Home and one HTTP worker, and restores headless mode. It also checks the internal
combined observer/locator with globally qualified references and ambiguity.
Nested OOPIF owner geometry is checked against real trusted input, including
fractional border/padding, overlays, hidden/moved and unsupported transformed owners.
The native gate now uses the real dispatcher with the internal frame driver for
observe/wait, nested fill, option discovery/selection, focus and cancellation.
It also verifies ticketed hidden OOPIF uploads and clears with exact byte hashes,
event counts, document-chain cancellation and completed receipts without replay.
Picker interception, target resolution and cancel are also verified in their
own process, including takeover, child navigation and newly attached OOPIFs.
It verifies nested pointer/HTML5 drags, root/OOPIF delivery in both directions,
receipt cancellation and child navigation while a mouse button is held.
It also checks composed mask pixels, exact nested screenshot crops and image
withholding when a child document changes between capture/privacy scans.
The regular workbench now enables the frame driver when its native content view
advertises `frame_automation`. Older backends retain their existing adapter.
`public_frames_probe.py` verifies real C SDK and managed Agent calls through the
normal workbench against nested OOPIFs, including uploads/pickers, exact hashes,
private element images and owned read-only permission denial. The private routed gate supplies controlled
UI-receipt status; it does not replace managed C/JS registration/permission gates.
`frame_scroll_probe.py` verifies the existing browser scroll-into-view traversal
through three offscreen document levels and two OOPIFs, with one trusted click
and stable element references. Drag still requires both endpoints visible together.
`visual_probe.py` covers six image-point pointer/HTML5 drag combinations across
the root and a real OOPIF, durable recovery without replay, exactly one source
termination and document-change release. It also runs actual Agent tools with
full-access/read-only permissions. Focused `--drag-only`,
`--drag-navigation-only` and `--managed-only` gates explicitly label their scope;
they reuse the same deployment/Home. The last mode writes its separate
`.mdo/managed-visual-result.json` receipt.

`keyboard_probe.py` checks actual shared C/JS and Agent image-key calls on root,
OOPIF and open-shadow controls. It verifies trusted keys, no focus clicks, exactly
one Enter activation, focus-hijack and dialog checkpoints without key delivery,
durable recovery without replay, privacy masks and owned read-only denial before
focus. The same native view/controller and isolated runtime provide focus and
the immediate pre-key focus/document checks; no extra browser engine is used.

`dom_keyboard_probe.py` verifies ordinary C/JS/Agent input without click-to-focus:
one Enter activation, Unicode fill/append/clear, open-shadow/contenteditable and
OOPIF targets, lost/replaced/selection-hijacked focus, dialogs and original POST
downloads without input replay/refetch. It also checks unrelated sibling updates,
explicit new-action recovery and read-only denial before focus. The same Home,
synthetic loopback fixture and native transfer cache are reused.

`capture_readiness_probe.py` checks three first public screenshot receipts after
opening the normal workbench. A failed first public call remains a failure even
if a later recovery succeeds. The native frame gate separately forces a viewport
resize between privacy scans and verifies two native captures in one original
operation, one stable image and an empty native capture slot. Unit gates bound
continuous resizing and check cancellation, takeover, navigation, privacy and DPR
changes without image publication.

`capture_privacy_probe.py` uses actual Chromium canvas codecs and the production
shared capture executor to compare synthetic images differing only inside three
private regions. It reproduces JPEG influence spreading outside masks when a
lossy source is admitted. `capture_privacy_public_probe.py` independently checks
the actual public C → JS → native WebView2 path using a loopback page: PNG/JPEG
results remain byte-identical across the private-pixel variants, with the final
8 KiB budget enforced. The public gate also covers fractional CSS bounds and
downscaling to an 87×73 output. These gates cover this compression boundary, not all
possible privacy layouts. Their reports store equality/dimensions, not pixels.

`sdk_probe.py` instead opens the normal mdo interface and sends a real public C
service call through its workbench to WebView2. Host overlays are recorded in
`host/overlays.json` and applied only to the single `.mdo/source` checkout.
Raw C services return UI receipts; C callers read `browseruse.result` and release
the receipt after completion. JavaScript completion helpers wait for the same
execution results. Download acceptance starts receiving and remains distinct
from verified file completion. Pending/accepted delivery is never page success.

`managed_probe.py` exercises the actual Agent tool executor and live read-only/
full-access permissions, without requesting model output. It reuses named QA
sessions in this deployment. Read-only inspection and browser mutations are
separate tools; ordinary browser errors remain tool feedback.

`upload_probe.py` verifies actual C/JS/managed file-input uploads and exact file
bytes, duplicate-operation recovery, scope and read-only denial. Its sources are
small synthetic files in the existing `.mdo/qa`; it restores headless mode and
checks zero remaining private cache reservations. It also covers automatic
picker interception, opaque chooser uploads, cancellation, repeat-click fencing
and read-only picker inspection versus cancellation. Nested OOPIF uploads are
also covered by the public frame gate. Directory sources, complete relative
paths/hashes, original-workspace resolution, directory pickers and managed
permissions are covered through both root and OOPIF public gates. Android
transfer support remains in development.

`upload_admission_probe.py` verifies an actual failed idle native admission
reclaims its reservation while the browser remains open, without another
upload/status/close command before independent inspection. The `--about-blank`
variant checks the empty native document too. Its I/O fault temporarily denies
subdirectory creation only in newly created reservation directories in this
empty private QA cache. It restores the original ACL in `finally`, verifies
exception unwinding also restores it, and confirms a new admission works after
the fault. No system settings or other paths are changed.

Public upload checks include actual page reads of 240-byte ASCII and 238-byte
Unicode filenames through long private cache paths. The restart gate uses the
240-byte name while checking orphan collection, held leases, unexpected/renamed
entries and a real project-local junction. Native path unit checks also perform
long Windows file I/O; UNC normalization is checked without accessing a share.

See [the shared API](docs/api.md) for C/JS calls, operation identities and
recovery. The SDK probe also runs the actual JS client against WebView2;
`restart_probe.py` restarts this same project host and verifies that retained
admission prevents re-execution after an ephemeral result is lost.

## Scope

- Plugin and SDK entry points in C; page observation/UI in JavaScript.
- Real input, frame/window/dialog and transfer support through native WebViews.
- Existing mdo model loop, live permissions, side pane and plugin store.
- Windows full browser path; Android shared API with explicitly declared limits.
- Every tested development milestone is committed on `master`.

The plugin is not yet advertised as a finished browser automation implementation.
Android adapter status and verification boundaries are tracked in
[the Android notes](docs/android.md).
The minimal Java device gate uses `python tools/setup_android_client.py`,
`python tools/build_android_client.py` and `python tools/android_device_probe.py`.
It reuses the existing WSL JDK, requires no NDK, and preserves installed mdo apps.
The last connected phone reported no isolated-world feature; this is an
explicit unsupported-provider receipt, not an Android automation pass.
`tools/android_input_probe.py` checks the native Java sequencer's bounded
lifecycle and the production view ownership registry; the actual Activity hooks
and main-document connection/reply ownership are compiled/checked as well.
`node --test tests/test_android_channel.mjs` checks the applied main-page bootstrap
(pair it with `python tools/apply_host.py --check`).
`tools/android_device_probe.py --mode native-input` separately tests
the actual WebView input target using the same tiny QA app. That new framework
gate is currently unavailable because ADB reports no phone. The current provider
is retained, and Android product automation remains disabled pending integration.

`drag_lifetime_probe.py` exercises an original POST download during a held
pointer, preserving the source DOM, then verifies observer removal and the next
trusted gesture. It checks exact response bytes/hash, one POST, one release per
press, real navigation/history recovery and empty native transfer reservations.
It uses the same loopback fixture and Home; BFCache restoration is reported
separately and was not confirmed by the current run.

## License

MIT. The separately checked-out mdo source retains its own license and notices.


`download_probe.py` verifies the actual C/JS/managed download APIs against one
local HTTP fixture in this deployment: POST/blob/binary/unknown-length responses,
verified hashes, durable acceptance/cancellation, direct navigation checkpoints,
foreign-scope denial and read-only permissions. It performs no live model calls.
Initial URL, automatic page-script and original POST-popup downloads are also
intercepted before navigation/adoption. `--initial-only` focuses on these paths.
The completed response stays in private browser cache until explicit release.
`browser_save_download` / shared C/JS `browseruse.save` saves its existing bytes
to a new path in the frozen task workspace, with size/hash verification and no
overwrite. `save_probe.py` verifies actual binary/blob/POST/unknown-length/popup
saves, exact file bytes, duplicate identities, conflicts and managed permissions.
The browser toolbar's download button opens a compact tray for accepting,
cancelling and saving retained responses. The tray also lists the original task's
recoverable staging records for explicit verification, publication and cleanup.
It does not switch to the currently selected chat's directory or refetch URLs.

`download_ui_probe.py` mounts the actual browser view in the same WebView2 host
and checks manual save bytes, focus retention, native viewport resizing,
original-task legacy-root review/adoption, recovery controls and empty final
cache inventory. Binding does not reopen or refetch the retained response.
Normal-main-window screenshot
inspection remains unverified because the desktop capture helper timed out.

`publication_recovery_probe.py` injects a final receipt replacement failure after
publishing 32 MiB, hard-stops and restarts this same Home, then verifies retained
destination/operation intent, exact bytes and reconciliation without recreating
the file. Test data is removed after success; no extra deployment is created.
