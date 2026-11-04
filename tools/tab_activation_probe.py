"""Verify keyed activation in the real main window without recreating its page."""
import json
import time

import acceptance_probe as acceptance
import dev
import sdk_probe
from managed_probe import FULL

SCOPE = {"extension": "plugin.mdo.browser-use", "project": "default", "session": FULL}
REPORT = dev.ENV / "tab-activation-result.json"


def main():
    before = acceptance.runtime_identity()
    assert not dev.call("operation.list") and not dev.call("browser.list") and not dev.call("panel.list")
    assert json.loads(dev.request("GET", "/api/v1/runs")[2])["data"]["active_runs"] == 0
    session = dev.call("session.create", {"body": {"project_id": "default", "client_session_id": FULL,
        "title": "Browser Use managed QA", "permission_profile": "full-access",
        "workspace_root": str(dev.ROOT)}}, {"extension": "browser-use-managed-qa"})
    assert session["data"]["permission_profile"] == "full-access"
    fixture = dev.SOURCE / "app/web/__browser_use_activation__.html"
    wanted = (dev.ROOT / "tests/fixtures/page.html").read_bytes()
    assert not fixture.exists() or fixture.read_bytes() == wanted
    fixture.write_bytes(wanted)
    report = {"passed": False, "cases": [], "real_model_calls": 0, "before": before}
    browsers = []

    def save():
        REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    def rows():
        return {row["resource"]["id"]: row for row in dev.call("panel.list", {}, SCOPE)
                if row.get("resource", {}).get("id") in browsers}

    def visible(identity):
        end = time.monotonic() + 8
        current = {}
        while time.monotonic() < end:
            current = rows()
            state = current.get(identity, {}).get("runtime", {})
            if state.get("visible") and state.get("status") == "ready":
                return current
            time.sleep(.05)
        raise AssertionError("Requested existing panel stayed hidden: " + identity)

    def observe(identity):
        pending = dev.call("browseruse.observe", {"browser": identity}, SCOPE)
        try:
            result = sdk_probe.wait_receipt(pending["id"])["result"]
            assert result["ok"] and "Readable fixture text" in result["text"]
            return result["document"]
        finally:
            dev.call("browseruse.result", {"id": pending["id"], "release": True}, SCOPE)

    save()
    dev.stop()
    try:
        dev.start(window=True, page="/#/projects/default/sessions/" + FULL)
        end = time.monotonic() + 15
        clients = []
        while time.monotonic() < end:
            clients = dev.call("ui.clients")
            if clients:
                break
            time.sleep(.1)
        assert len(clients) == 1
        client = clients[0]["id"]
        args = [{"action": "create", "client": client, "key": "activation-" + name,
                 "url": f"http://127.0.0.1:{dev.state()['port']}/__browser_use_activation__.html"}
                for name in ("first", "second")]
        sdk_probe.SCOPE = SCOPE
        documents = {}
        for params in args:
            browser = dev.call("browseruse.tabs", params, SCOPE)
            browsers.append(browser["id"])
            visible(browser["id"])
            documents[browser["id"]] = observe(browser["id"])
        first, second = browsers
        original = rows()
        assert original[second]["runtime"]["visible"] and not original[first]["runtime"]["visible"]
        report["before_reveal"] = original
        save()
        panel = dev.call("browseruse.tabs", {"action": "reveal", "browser": first}, SCOPE)
        assert panel["id"] == original[first]["id"]
        current = visible(first)  # No observe/input may incidentally repair activation.
        assert not current[second]["runtime"]["visible"]
        assert observe(first) == documents[first]
        report["cases"].append("reveal-original-mounted-page")
        save()
        repeated = dev.call("browseruse.tabs", args[1], SCOPE)
        assert repeated["id"] == second
        current = visible(second)
        assert not current[first]["runtime"]["visible"] and observe(second) == documents[second]
        report["cases"].append("keyed-create-activates-original-page")
        # Background reuse must not steal focus, and later runtime metadata must
        # not repeatedly apply a previously consumed activation request.
        dev.call("panel.open", {"client": client, "type": "mdo.browser", "key": first,
            "resource": {"id": first}, "reveal": "background"}, SCOPE)
        for _ in range(12):
            current = rows()
            assert current[second]["runtime"]["visible"] and not current[first]["runtime"]["visible"]
            time.sleep(.05)
        assert len(current) == 2
        assert all(current[key]["id"] == original[key]["id"] and
                   current[key]["revision"] == original[key]["revision"] for key in browsers)
        report["cases"].append("background-reuse-and-reports-preserve-selection")
        report.update(passed=True, original_document_retained=True,
                      original_panel_revision_retained=True, after_activation=current)
        save()
    except Exception as error:
        report["error"] = str(error)
        save()
        raise
    finally:
        for identity in browsers:
            if any(row["id"] == identity for row in dev.call("browser.list", {}, SCOPE)):
                dev.call("browser.close", {"id": identity}, SCOPE)
        assert not dev.call("operation.list") and not dev.call("browser.list") and not dev.call("panel.list")
        dev.stop()
        dev.start()
        after = acceptance.runtime_identity()
        for key in ("port", "executable", "executable_sha256", "config", "config_sha256", "home"):
            assert before[key] == after[key], key
        report.update(headless_restored=True, after=after)
        save()
    print(json.dumps({"passed": True, "cases": len(report["cases"]), "headless_restored": True}))


if __name__ == "__main__":
    main()
