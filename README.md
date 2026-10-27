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
node --test --test-concurrency=1 tests/test_client.mjs tests/test_dispatch.mjs tests/test_popups.mjs tests/test_capture_image.mjs tests/test_frame_capture.mjs tests/test_visual_snapshot.mjs tests/test_drag.mjs tests/test_readiness.mjs tests/test_upload.mjs tests/test_downloads.mjs tests/test_download_save.mjs tests/test_download_user.mjs tests/test_frames.mjs tests/test_frame_driver.mjs tests/test_page_identity.mjs
python tools/native_probe.py
python tools/native_frames_probe.py
python tools/public_frames_probe.py
python tools/frame_scroll_probe.py
python tools/sdk_probe.py
python tools/managed_probe.py
python tools/restart_probe.py
python tools/upload_restart_probe.py
python tools/upload_probe.py
python tools/download_probe.py
python tools/save_probe.py
python tools/publication_recovery_probe.py
python tools/download_ui_probe.py
```

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
also covered by the public frame gate. Directory uploads and Android transfer
support remain in development.

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
