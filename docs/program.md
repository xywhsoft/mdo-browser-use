# Enhanced Windows development program

`dist/mdo-browser-use-dev-windows-x86_64.exe` is a self-contained development
build of the pinned mdo 0.7.0 program plus this repository's reviewed host/native
enhancements. Use it together with `mdo-browser-use-0.1.0.mdo-extension.json`.
The executable supplies the native browser and shared host services; the
portable plugin supplies the official Browser Use tools/SDK and Skill.
It is not an online release or an update-channel publication.

The current refreshed preview is **6,876,009 bytes**, SHA-256
`e63290e76ad033c01fa610e9f25149568b182a71fd3358d261293faaed29db16`.
Its native inventory contains 747 files, including runtime 19, the shared
document/capture driver, E12/E16's native input/capture failure handling and E17's
lossless-source privacy fix. Included module
hashes match the reviewed deployment. The actual packed-program gate passes all
six runtime checks. This is
Windows verification; Android framework qualification remains unavailable.

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
and browser close against a synthetic loopback page. It then restores the
headless service. It does not send a model request or test external websites.

The executable receipt records inventory verification separately from runtime
verification; a newly built artifact is not marked runtime-verified until that
gate passes. Private build/runtime/recovery receipts remain under `.mdo/`.

`python tools/package_lifecycle_probe.py` additionally verifies native-plugin
uninstall/restart/reinstall and interrupted import recovery in the same Home.
Uninstall retains immutable plugin generations for existing code/UI and drops
the installation receipt. Reinstall reuses retained bytes only when their full
SHA-256 matches; an edited file remains a conflict. The gate includes embedded
NUL/non-UTF-8 binary data, a changed retained file, preservation of an existing
browser record, removal of C services after restart, and recovery of a deliberately
incomplete payload/receipt transaction. It restores the ordinary 22-file plugin.
