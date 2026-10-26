"""Verify admission survives a real restart of the same project host/Home."""
import json
import sys
import time
import uuid

import dev

SCOPE = {"extension": "browser-use-restart-qa", "project": "default", "session": "restart-task"}
CLIENT = "browser-use-restart-window"


def operation(action, identity, **fields):
    return dev.call("browseruse.operation", {"action": action, "operation": identity, **fields}, SCOPE)


def main():
    if dev.state().get("window"):
        raise RuntimeError("Finish the current native UI probe before the single-host restart check")
    output = dev.ENV / "restart-result.json"
    output.write_text('{"passed":false,"state":"running"}\n', encoding="utf-8")
    identity = "sdk-" + uuid.uuid4().hex
    request = {"method": "browseruse.tabs", "arguments": {"action": "create", "client": CLIENT}}
    browser = None
    try:
        dev.call("client.hello", {"client": CLIENT, "context": {}})
        operation("submit", identity, **request)
        for _ in range(100):
            state = operation("get", identity)
            if state["state"] != "pending":
                break
            time.sleep(0.02)
        assert state["state"] == "succeeded", state["state"]
        browser = state["result"]["id"]
        before = [row["id"] for row in dev.call("browseruse.tabs", {"action": "list"}, SCOPE)["tabs"]]
        assert browser in before
        dev.stop()
        dev.start()
        # Make an accidental replay executable, so a missing client cannot
        # disguise a broken admission journal as a successful recovery test.
        dev.call("client.hello", {"client": CLIENT, "context": {}})
        recovered = operation("submit", identity, **request)
        assert recovered["state"] == "uncertain"
        assert recovered["error"]["code"] == "result_expired"
        after = [row["id"] for row in dev.call("browseruse.tabs", {"action": "list"}, SCOPE)["tabs"]]
        assert after == before, "Restart recovery repeated tab creation"
        report = {"passed": True, "backend": "actual-host-restart", "same_home": True,
                  "admission_preserved": True, "unavailable_result_explicit": True, "duplicate_tabs": 0}
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
    except Exception as error:
        output.write_text(json.dumps({"passed": False, "error": str(error)[:512]}) + "\n", encoding="utf-8")
        raise
    finally:
        if browser:
            dev.call("browseruse.tabs", {"action": "close", "browser": browser}, SCOPE)
        operation("release", identity)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, AssertionError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
