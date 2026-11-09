# Browser Use development package

This is a development preview of `mdo.browser-use`, not a production release.
The current installation package targets Windows x86-64 with this project's
updated mdo 0.9.6 or newer host, including the plugin prompt-contribution
mechanism. Install only with a compatible program; this is a development
preview and does not publish or join the official automatic-update channel.

Android development continues separately. The tested current-version device's
WebView lacks isolated execution worlds; automation remains explicitly disabled.
The Android native adapter is also incomplete. This package does not advertise
Android automation, and no provider upgrade is required for development.

## Build and install

From this repository, run `python tools/package_plugin.py`. It creates
`dist/mdo-browser-use-0.1.0.zip`, `dist/mdo-browser-use-skill-0.1.0.zip`
and their SHA-256 inventory receipts. Install the Skill first; the plugin declares
its typed Skill dependency. The Skill is a real extension directory, rather than
an override of a legacy default-home file that the new catalog cannot discover.
The artifact is a complete directory ZIP, with `extension.json` metadata and
`plugin.json` execution recipe. Native/frontend entry paths are identical in
both files and all declared sources are included. Legacy embedded JSON imports are retired. It contains one
modern plugin, its C implementation, shared C/JS SDK, page runtime, Skill,
these instructions, and the MIT license. No Python or Node runtime is needed
by the installed plugin.

In the matching enhanced mdo, open Settings → Extensions → Plugins, choose
the installation-file import action, and select the matching ZIP files. Review the
source/code trust prompt and current directory fingerprint. Enable the plugin after import
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

`python tools/install.py` uploads bounded chunks, seals the archive, installs it
with the exact current directory fingerprint, and enables the typed plugin.
It requires the running development service and never contacts an online catalog
or model. A running native plugin must be disabled and the idle development
service restarted before replacing its files; restart again to activate the new
native entry. The packed-program probe checks every installed archive file hash.
The old `package_probe.py` and `package_lifecycle_probe.py` cover the retired JSON
installer and are historical until ported; they are not current ZIP qualification.

The normal native probes verify actual browser operations separately; a
successful package import alone does not establish native browser support.
The complete cross-platform Browser Use goal remains in progress. No store,
website download or automatic-update release is published by these commands.
## Automatic capability instructions

The current development package declares three bounded, tool-gated prompt
contributions: browser workflow, JavaScript execution, and file transfers. Each
Agent/SubAgent receives only the parts matching its own actual tools. Full
workflows remain in the on-demand browser-use Skill; installing that Skill is
still a package dependency, not automatic preloading of its full text.

These declarations require mdo's plugin prompt-contribution host update,
included in the official 0.9.6 client. Earlier hosts do not support the new recipe
field. This package has not been published to the online extension store.
