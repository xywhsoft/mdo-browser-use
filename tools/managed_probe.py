"""Run Browser Use through mdo's actual managed tool and permission executor."""
import json
import sys
import time
import uuid

import dev

SCOPE = {"extension": "browser-use-managed-qa", "project": "default"}
FULL = "b0a00000000000000000000000000001"
READ_ONLY = "b0a00000000000000000000000000002"


def tool(name, arguments, session=FULL):
    scope = dict(SCOPE, session=session)
    job = dev.call("operation.start", {"id": "sdk-" + uuid.uuid4().hex, "method": "tool.call",
                   "params": {"project": "default", "session": session, "tool": name,
                              "arguments": arguments}}, scope)
    end = time.monotonic() + 25
    while time.monotonic() < end:
        current = dev.call("operation.get", {"id": job["id"]}, scope)
        if current.get("terminal"):
            if current["state"] != "succeeded":
                raise RuntimeError("Managed tool transport failed: " + current.get("error", {}).get("code", "unknown"))
            return dev.call("operation.result", {"id": job["id"]}, scope)
        time.sleep(0.05)
    dev.call("operation.cancel", {"id": job["id"]}, scope)
    raise RuntimeError("Managed tool did not finish in its bounded probe")


def value(result):
    text = result["text"]
    return json.loads(text[text.index("{"):])


def main():
    output = dev.ENV / "managed-tools-result.json"
    output.write_text('{"passed":false,"state":"running"}\n', encoding="utf-8")
    for identity, permission in ((FULL, "full-access"), (READ_ONLY, "read-only")):
        created = dev.call("session.create", {"body": {"project_id": "default", "client_session_id": identity,
                           "title": "Browser Use managed QA", "permission_profile": permission,
                           "workspace_root": str(dev.ROOT)}}, SCOPE)
        if created["data"]["permission_profile"] != permission:
            raise RuntimeError("The persistent QA session permission changed; refusing to overwrite it")
    (dev.SOURCE / "app/web/__browser_use_page__.html").write_bytes((dev.ROOT / "tests/fixtures/page.html").read_bytes())
    dev.stop()
    browser = None
    owner = {"extension": "plugin.mdo.browser-use", "project": "default", "session": FULL}
    try:
        dev.start(window=True)
        end = time.monotonic() + 25
        while time.monotonic() < end:
            clients = value(tool("browser_tabs", {"action": "clients"}))["clients"]
            if clients:
                break
            time.sleep(0.1)
        if not clients:
            raise RuntimeError("The actual mdo UI did not register its display client")
        client = clients[0]["id"]
        assert tool("browser_tabs", {"action": "list"}, READ_ONLY)["success"]
        denied = tool("browser_open", {"action": "create", "client": client,
                      "url": f"http://127.0.0.1:{dev.state()['port']}/__browser_use_page__.html"}, READ_ONLY)
        assert denied["success"] is False, "Read-only session performed a write"
        created = tool("browser_open", {"action": "create", "client": client, "key": "managed-browser",
                       "url": f"http://127.0.0.1:{dev.state()['port']}/__browser_use_page__.html"})
        assert created["success"]
        browser = value(created)["id"]
        observed = tool("browser_observe", {"browser": browser})
        assert observed["success"]
        page = value(observed)
        assert "Readable fixture text" in page["text"]
        failed = tool("browser_act", {"browser": browser, "document": "stale-document", "op": "click", "target": {"name": "查询"}})
        assert failed["success"] is False
        assert "stale_document" in failed["text"]
        resumed = tool("browser_observe", {"browser": browser})
        assert resumed["success"], "A normal tool failure prevented the next managed tool"
        filled = tool("browser_act", {"browser": browser, "document": value(resumed)["document"],
                      "op": "fill", "target": {"label": "订单号"}, "text": "Managed native input"})
        assert filled["success"] and value(filled)["verification"] == "field_state"
        assert tool("browser_wait", {"browser": browser, "until": "visible", "target": {"name": "查询"}})["success"]
        assert tool("browser_navigate", {"browser": browser, "action": "reload"})["success"]
        assert tool("browser_open", {"action": "close", "browser": browser})["success"]
        browser = None
        report = {"passed": True, "backend": "actual-managed-tool-webview2", "registered_tools": 6,
                  "readonly_read_allowed": True, "readonly_write_denied": True,
                  "full_access_without_extra_prompt": True, "tool_failure_then_recovery": True}
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
    except Exception as error:
        output.write_text(json.dumps({"passed": False, "error": str(error)[:512]}) + "\n", encoding="utf-8")
        raise
    finally:
        if browser:
            try:
                dev.call("browseruse.tabs", {"action": "close", "browser": browser}, owner)
            except (OSError, RuntimeError):
                pass
        dev.stop()
        dev.start()


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, AssertionError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
