"""Public C service -> real mdo UI -> native WebView2 -> scoped SDK result."""
import json
import sys
import time

import dev

SCOPE = {"extension": "plugin.mdo.browser-use"}


def wait_receipt(identity, service="browseruse.result", timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        receipt = dev.call(service, {"id": identity}, SCOPE)
        if receipt["state"] in ("succeeded", "failed", "cancelled"):
            if receipt["state"] != "succeeded":
                raise RuntimeError("Native SDK action failed: " + str(receipt.get("error")))
            return receipt
        time.sleep(0.1)
    raise RuntimeError("Native SDK action did not reach a terminal state")


def main():
    fixture = dev.SOURCE / "app/web/__browser_use_page__.html"
    fixture.write_bytes((dev.ROOT / "tests/fixtures/page.html").read_bytes())
    dev.stop()
    browser = None
    try:
        dev.start(window=True)
        deadline = time.monotonic() + 30
        clients = []
        while time.monotonic() < deadline:
            clients = dev.call("ui.clients")
            if clients:
                break
            time.sleep(0.2)
        if not clients:
            raise RuntimeError("The actual mdo window did not register its SDK client")
        client = clients[0]["id"]
        browser = dev.call("browseruse.tabs", {"action": "create", "client": client,
                           "url": f"http://127.0.0.1:{dev.state()['port']}/__browser_use_page__.html",
                           "key": "public-sdk-observe"}, SCOPE)["id"]
        # The existing native ready/wait path is exercised before the page
        # read. Automatic readiness is a separate pending implementation stage.
        ready = dev.call("browser.command", {"id": browser, "command": "wait", "timeout_ms": 15000}, SCOPE)
        wait_receipt(ready["id"], "browser.result")
        dev.call("browser.release", {"id": ready["id"]}, SCOPE)
        queued = dev.call("browseruse.observe", {"browser": browser, "limit": 100}, SCOPE)
        assert queued["state"] == "pending", queued
        received = wait_receipt(queued["id"])
        assert "params" not in received and "context" not in received
        page = received["result"]
        assert page["ok"] and "Readable fixture text" in page["text"], page.get("error")
        assert any(e["name"] == "查询" for e in page["elements"])
        assert any(e["name"] == "Shadow action" for e in page["elements"])
        assert any(e["name"] == "Frame action" for e in page["elements"])
        dev.call("browseruse.result", {"id": queued["id"], "release": True}, SCOPE)
        report = {"passed": True, "backend": "actual-mdo-ui-webview2",
                  "public_service": "browseruse.observe", "elements": len(page["elements"]),
                  "frames": len(page["frames"]), "request_bytes_in_ui_queue": len(json.dumps(queued["params"]).encode())}
        (dev.ENV / "public-sdk-result.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
        return 0
    finally:
        if browser:
            try:
                dev.call("browseruse.tabs", {"action": "close", "browser": browser}, SCOPE)
            except (OSError, RuntimeError):
                pass
        dev.stop()
        dev.start()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, ValueError, AssertionError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
