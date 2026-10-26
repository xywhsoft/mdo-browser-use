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
node --test tests/test_client.mjs
python tools/native_probe.py
python tools/sdk_probe.py
```

The native probe reuses this exact deployment, briefly opens its real WebView2
window with local fixtures, and restores headless mode afterwards. No live model
or external website is needed. Its latest result is `.mdo/page-observation-result.json`.

`sdk_probe.py` instead opens the normal mdo interface and sends a real public C
service call through its workbench to WebView2. Host overlays are recorded in
`host/overlays.json` and applied only to the single `.mdo/source` checkout.
The raw C services return UI receipts; `createBrowserUseClient(sdk).observe()`
waits for the terminal result. C callers read `browseruse.result` and release the
receipt after completion. Pending/accepted delivery is never page success.

## Scope

- Plugin and SDK entry points in C; page observation/UI in JavaScript.
- Real input, frame/window/dialog and transfer support through native WebViews.
- Existing mdo model loop, live permissions, side pane and plugin store.
- Windows full browser path; Android shared API with explicitly declared limits.
- Every tested development milestone is committed on `master`.

The plugin is not yet advertised as a finished browser automation implementation.

## License

MIT. The separately checked-out mdo source retains its own license and notices.
