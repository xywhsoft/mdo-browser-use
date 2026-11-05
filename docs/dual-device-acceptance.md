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
