# Browser Use development

- Work in this repository on `master`; retain the existing origin.
- Develop and run checks sequentially. Do not spawn subagents or parallel builds.
- Use the single `.mdo/` development deployment. Do not make temporary application
  copies, isolated test Homes, or per-test dependency checkouts.
- Reuse the pinned mdo source and native host. Keep generated files and local
  account/session/browser data out of Git. Never print or persist test secrets.
- Commit each tested, reviewable feature or bug fix. Update `docs/progress.md`
  with verification and outstanding work. An unavailable platform check is not a pass.
- Run tests from this project and reuse their output directories. Preserve logs
  needed to investigate failures; do not accumulate dumps or downloaded browsers.
- Changes to the mdo host must be represented by reviewed files/patches here and
  applied only to `.mdo/source`; do not edit the user's other repositories.
- Use C for the plugin, JavaScript for page logic/UI, and the existing native
  WebView2/Android integration. Do not add Node/Python/Playwright runtime requirements.
