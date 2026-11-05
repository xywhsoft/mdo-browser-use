# Physical two-device acceptance

## E253: real same-account authentication and control

On2026-11-05 the user personally logs into the existing development environment
and authorizes the registered Windows device named 公司台式机. The controller
runs the pinned0.7.0 enhanced host; the actual company target reports0.9.2.
Both use the real account/relay service. Tickets are obtained through the local
connector and sent to the real TLS WebSocket endpoint, with production remote
transport code. No synthetic peer, authentication substitute or certificate
exception is used. Tickets/tokens remain in memory and are not written to logs.

| Check | Actual result |
| --- | --- |
| Same-account listing, target handshake and identity | The user-designated second device matches the requested device ID and authenticated member; local and target identities differ |
| Read-only role | Bootstrap and device metadata are readable; controller-side and actual target-side writes both fail with read_only |
| Control role | Actual remote SDK call and owned synthetic resource write/readback succeed |
| Stale mutation | A zero-revision overwrite of the created resource fails with revision_conflict; the original value remains intact |
| Resource cleanup | Only that exact resource and revision are removed; subsequent lookup confirms absence |
| Used relay ticket | A second correctly formed WebSocket upgrade with the consumed ticket returns HTTP401 |
| Connector ticket delivery | Taking the same completed connector ticket a second time returns HTTP409 |
| Reconnection/release | Fresh connections reach the same original runtime; after close, the company device has zero viewers and no control occupant |

These checks qualify the **covered same-account authentication/control flows**
on the two actual user-designated devices. They do not qualify arbitrary
cross-account authorization, device-wide revocation, direct UDP traversal or
full remote Browser Use. No real model, project/file mutation, account logout,
device permission change or program replacement occurs. The local logged-in
development window remains open. Existing company sessions/projects are kept.

After the user opens the company workbench, its actual display client registers
and acknowledges native capability retrieval. It reports native input, isolated
profiles, screenshot and snapshot support. No browseruse services/plugin are
registered, and this descriptor does not declare the enhanced Browser Use
automation contract. No page operation or screenshot of user content is sent.

## E254: package/host version boundary

An old-format package planning request is initially rejected with HTTP413.
A bounded request containing the same legacy field names then returns HTTP422
with extension_json_retired. The actual target application's read-only VFS
source confirms that its ecosystem route accepts small control metadata and
directory ZIP archives, and explicitly rejects the retired embedded JSON format.
This is a version/API mismatch, not evidence of a remote transport/body defect.
No package is installed and no limit is relaxed.

The current Browser Use project is locked to mdo commit
a7c736458df003954ba7b88ed191f4b985419686/version0.7.0. A read-only assessment of
the available0.9.2 source at a7a64ae477bcaacab23f3fc1ab331bed126fa82f finds32
overlay paths matching directly and five requiring migration:

- shell/application-sdk.js and workbench/panel-manager.js have changed contexts;
- state/approvals.js has changed context;
- api/ecosystem_install.inc.c has been removed by the directory/ZIP redesign;
- deps.lock has changed and needs native dependency/build review.

The next implementation stage must port the host/UI changes, replace the
retired JSON package/install tests with the current directory ZIP flow and
requalify native/SDK compatibility and actual page execution. It must preserve
the user-approved single deployment/Home, login and previous evidence, and must
not downgrade/overwrite the company's0.9.2 program to satisfy a test. This is a
reviewed migration boundary; the source pin and company program are unchanged
in E253/E254. Full real-device Browser Use remains incomplete.

## E255: actual Android phone and local Windows desktop

The current user-authorized RMX5090 phone and the existing Windows deployment
are tested through their actual account/native APIs and the production TLS
relay. The phone's old QA package is no longer installed at discovery. Install
the matching E251 complete development APK under the dedicated QA ID; verify
its installed SHA-256, preserving the separate formal mdo package and its data.
The desktop's previous process has exited; restart only this deployment with
its original Home. Its persisted account remains signed in. The user personally
logs into the new phone QA app and enables remote access on the local desktop.

Both directions pass view and control cohorts: 5 view checks and 7 control
checks per direction, 24 total. Each connects with an actual account-issued
ticket, verifies the exact distinct target device/member, and uses production
remote transport. The view cohort independently verifies controller and actual
target write denial. The control cohort calls the real SDK, writes/reads a
unique synthetic resource, rejects a stale revision, removes the exact original
resource and confirms absence. Consumed relay tickets return HTTP401; a second
local ticket delivery returns HTTP409. Closing a transport rejects subsequent
requests without falling back to the controller's local API. Fresh role
connections reach the same original runtime in each direction.

The first reverse preflight races initial desktop enabling: enabling assigns
its first device ID, unlike the earlier disabled snapshot. After refreshing the
identity, an initial ticket job fails with remote_service_unavailable. Its
original HTTP status was not captured, so the exact server-side reason is not
proven. Subsequent actual server discovery confirms online/enabled/unoccupied,
and fresh view/control requests pass. The reusable probe now joins discovery
before requesting a ticket and retains a sanitized terminal failure receipt.
No product root cause or repair is inferred from this setup transition.

Run `tools/physical_auth_probe.py` with the private USB-forward receipt, direction,
role and a new output path. Credentials stay in memory and reach Node through
stdin, never argv or result files. This qualifies the covered native-account/
relay flows on two real devices. The Node controller executes on the desktop,
including when the ticket originates from the phone's account API. It does not
qualify physical Android controller UI interactions, direct UDP traversal,
cross-account denial, device-wide revocation or full remote Browser Use.

Phone main-window/provider/lifecycle testing continues separately. Its current
capability receipt still reports isolated_world_unsupported; guarded Android
Agent automation remains disabled. No provider upgrade or page-world fallback
is used, and no real model request occurs.

After the actual20-second deep-idle phone check, fresh desktop discovery briefly
fails with status0; a phone-originated ticket is obtained, but the desktop Node
relay socket receives ECONNRESET before target handshake. Independent desktop
HTTPS also initially disconnects. A subsequent verified TLS1.3/public HTTP200
check and fresh desktop-to-phone control cohort pass7checks, with the same
original phone runtime. Original failed receipts are preserved. Final discovery
from both endpoints returns200, both targets are online/unoccupied and both
accounts remain signed in. Record31passed scoped checks in total, alongside
these transient failures; do not claim uninterrupted networking or a root-cause
repair. The Android manual page survives the real deep-idle interval; see
android.md for exact scope, current provider limitation and initial-page scaling
anomaly. All synthetic resources/connections/USB mappings are cleaned.
