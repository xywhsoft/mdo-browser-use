# Bounded Windows performance verification

Run from this repository with its original development service started headless:

```powershell
python tools/performance_probe.py --iterations 16
```

This development-only tool reuses the original `.mdo/source`, native host and
Home. It restarts that same host with its actual main window, opens one owned
local page at a time, and restores headless mode. It adds no shipped Python,
Node, browser engine, application installation or Home copy. It calls no model
and navigates no external website. Iterations are limited to1–16; the default is8.

The fixed page has1,024 paragraphs plus an input and button. Each cycle creates
a broker record, observes the real native page twice, fills the field, verifies
exactly one trusted click, captures a PNG and closes the original browser. No
failed action is automatically retried. The tool checks the actual native frame
driver, the verified field state, the click result and delivered PNG bytes and
dimensions. Observation allows at most4,096 text bytes and100 elements; capture
allows at most800×600 pixels and131,072 bytes.

The normal UI has a two-second broker reconciliation interval. After each close,
the probe waits2.2 seconds before sampling. Native creation reservations must be
zero, and the original scoped broker browser/panel inventories must return to
their prior sets. Source and deployed hashes must remain unchanged during the
run. These gates do not alone prove destruction of every native/renderer object.

Results reuse `.mdo/performance-result.json`; retain a failed receipt before a
new run. Each phase records end-to-end elapsed time including loopback calls,
polls and result release. `decoded_result_json_bytes` describes the compact JSON
representation, not HTTP/TLS wire bytes. Image bytes stay in memory and are not
written to the report. Original public receipts have bounded waits; failed work
is cancelled/released before the same development runtime is restored. A cleanup
failure makes the report fail rather than silently qualifying the run.

## E203 measured run

Windows10 build19045, AMD64,12 logical processors;16 sequential cycles in97.6s.
The fixture and all531 authoritative inputs/146 deployed outputs are hash-bound
in the receipt. The ordinary main UI and its pre-existing QA tabs remain part
of this workload; there is no isolated benchmark instance or forced renderer GC.

| Phase | Median (ms) | Nearest-rank p95 (ms) |
| --- | ---: | ---: |
| Initial observation, including native page startup | 587.1 | 684.4 |
| Repeat observation | 133.1 | 178.2 |
| Native fill, including visible panel admission | 778.4 | 944.4 |
| Native trusted click | 633.6 | 718.6 |
| Bounded native screenshot | 413.5 | 485.7 |

There are only16 samples per phase, so this nearest-rank p95 equals that sample's
maximum. These are local observations, not a production latency guarantee or a
comparison with another product. All delivered images are52,454-byte PNGs and
meet the configured pixel/byte limits. All cycles pass the correctness gates;
there is no latency-threshold substitution for a failed action.

The sampled host/descendant process count remains8. Aggregate private bytes rise
from276.9MiB before the first cycle to323.9MiB after it, peak at350.4MiB and finish
at339.7MiB. The increase after the first completed cycle is15.8MiB. Samples fall
in cycles11/12; handles finish at4,200 and remain there in the final four samples.
The original broker inventories are restored and native creation reservations
are zero after every cycle.

Working set, private bytes and handles are point-in-time process-tree samples.
They include WebView2 allocators, caches, startup and existing UI activity, and
child processes can retire while sampled. A remaining private-byte difference
does not establish a product leak, and these16 cycles do not prove leak absence
or long-duration resource stability. The report deliberately keeps
`memory_qualification:observational_only`.

An earlier eight-cycle measurement is retained privately; its tool revision and
startup conditions differ, so it is not merged into the current percentile
series. Current E202 Windows/Android binaries and the portable plugin are
byte-identical, with their source/archive identity checked separately. E203
adds the development probe/fixture and records evidence; it does not modify the
product runtime or claim a new performance fix.
