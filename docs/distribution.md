# Browser Use development package

This is a development preview of `mdo.browser-use`, not a production release.
The current installation package targets Windows x86-64 with this project's
enhanced mdo host. Stock mdo 0.7.0 does not include all required native/host
services. The package's minimum build checks the plugin format, not the presence
of those enhancements. Install only with the matching enhanced program.

Android development continues separately. The tested current-version device's
WebView lacks isolated execution worlds; automation remains explicitly disabled.
The Android native adapter is also incomplete. This package does not advertise
Android automation, and no provider upgrade is required for development.

## Build and install

From this repository, run `python tools/package_plugin.py`. It creates
`dist/mdo-browser-use-0.1.0.mdo-extension.json` and a SHA-256 inventory receipt.
The artifact uses mdo's existing `mdo.extension.v2` format. It contains one
modern plugin, its C implementation, shared C/JS SDK, page runtime, Skill,
these instructions, and the MIT license. No Python or Node runtime is needed
by the installed plugin.

In the matching enhanced mdo, open Settings → Extensions → Plugins, choose
the installation-file import action, and select the JSON file. Review the
source/code trust prompt and installation plan. Enable the plugin after import
and restart when mdo requests it. An ID conflict with a separately installed
development copy is an intentional protection; do not replace unrelated files.

The artifact is built from the explicit `package-files.json` allowlist, with
UTF-8 text and LF newlines. Unlisted files, the development Home, account
settings, browser profiles, logs, QA pages and build outputs are never scanned
or included. Links/reparse points, duplicate/unsafe names, missing entry files,
and format limits abort packaging before publication. Identical source inputs
produce identical artifact bytes. The adjacent inventory detects corruption;
it is not a publisher signature or a substitute for trusting the source.

## Verification and limits

`python tools/package_probe.py` exercises the real local ecosystem importer,
enable/restart and exported-byte roundtrip using this project's single Home.
It requires the running headless development service and never contacts an
online catalog or model. It refuses to replace an unexpected plugin revision.
The importer uses a plan digest; an intervening modification must be replanned.

The normal native probes verify actual browser operations separately; a
successful package import alone does not establish native browser support.
The complete cross-platform Browser Use goal remains in progress. No store,
website download or automatic-update release is published by these commands.
