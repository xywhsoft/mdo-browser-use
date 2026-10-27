"""Public C service -> real mdo UI -> native WebView2 -> scoped SDK result."""
import json
import base64
import sys
import subprocess
import time

import dev

SCOPE = {"extension": "plugin.mdo.browser-use"}


def wait_receipt(identity, service="browseruse.result", timeout=30, allow_failure=False):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        receipt = dev.call(service, {"id": identity}, SCOPE)
        if receipt["state"] in ("succeeded", "failed", "cancelled"):
            if receipt["state"] != "succeeded" and not allow_failure:
                raise RuntimeError("Native SDK action failed: " + str(receipt.get("error")))
            return receipt
        time.sleep(0.1)
    raise RuntimeError("Native SDK action did not reach a terminal state")


def main():
    report_path = dev.ENV / "public-sdk-result.json"
    report_path.write_text(json.dumps({"passed": False, "state": "running"}) + "\n", encoding="utf-8")
    fixture = dev.SOURCE / "app/web/__browser_use_page__.html"
    fixture.write_bytes((dev.ROOT / "tests/fixtures/page.html").read_bytes())
    (dev.SOURCE / "app/web/__browser_use_popup__.html").write_bytes((dev.ROOT / "tests/fixtures/popup.html").read_bytes())
    (dev.SOURCE / "app/web/__browser_use_drag__.html").write_bytes((dev.ROOT / "tests/fixtures/drag.html").read_bytes())
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
        # Observe must wait for its newly created native page itself. The
        # probe deliberately does not call a separate low-level ready/wait.
        queued = dev.call("browseruse.observe", {"browser": browser, "limit": 100}, SCOPE)
        assert queued["state"] == "pending", queued
        received = wait_receipt(queued["id"])
        assert "params" not in received and "context" not in received
        page = received["result"]
        if not page.get("ok") or "Readable fixture text" not in page.get("text",""):
            report_path.write_text(json.dumps({"passed":False,"phase":"initial-observation","page":{
                key:page.get(key) for key in ("document","url","title","ready","error")},
                "text_bytes":len(page.get("text","").encode()),"elements":len(page.get("elements",[]))})+"\n",encoding="utf-8")
        assert page["ok"] and "Readable fixture text" in page["text"], page.get("error")
        assert page.get('frame_routing')=='native-isolated-v1','The public C path did not activate the normal frame driver'
        assert all(row['ref'].startswith('r1|') for row in page['elements'])
        assert any(e["name"] == "查询" for e in page["elements"])
        assert any(e["name"] == "Shadow action" for e in page["elements"])
        screenshot = dev.call("browseruse.screenshot", {"browser": browser, "document": page["document"],
                              "max_width": 800, "max_height": 600}, SCOPE)
        screenshot_result = wait_receipt(screenshot["id"])["result"]
        assert screenshot_result["ok"] and screenshot_result["image"]["media_type"] == "image/png"
        assert len(base64.b64decode(screenshot_result["image"]["base64"], validate=True)) <= 131072
        dev.call("browseruse.result", {"id": screenshot["id"], "release": True}, SCOPE)
        assert any(e["name"] == "Frame action" for e in page["elements"])
        dev.call("browseruse.result", {"id": queued["id"], "release": True}, SCOPE)
        actions = 0
        def act(op, target=None, **options):
            nonlocal actions
            report_path.write_text(json.dumps({"passed": False, "state": "running", "phase": "act:" + op, "completed_actions": actions}) + "\n", encoding="utf-8")
            request = {"browser": browser, "document": page["document"], "op": op, **options}
            if target is not None:
                request["target"] = target
            queued_action = dev.call("browseruse.act", request, SCOPE)
            try:
                outcome = wait_receipt(queued_action["id"])["result"]
                assert outcome["ok"], outcome.get("error")
                actions += 1
                return outcome
            finally:
                dev.call("browseruse.result", {"id": queued_action["id"], "release": True}, SCOPE)
        assert act("fill", {"label": "订单号"}, text="订单 🦉 42")["verification"] == "field_state"
        assert act("type", {"testid": "order-field"}, text="++")["dispatched"]
        assert act("fill", {"css": "#order"}, text="")["verification"] == "field_state"
        assert act("fill", {"name": "Frame input"}, text="Frame native input")["verification"] == "field_state"
        assert act("check", {"name": "Agree"}, checked=True)["verification"] == "field_state"
        assert act("check", {"name": "Agree"}, checked=True)["changed"] is False
        assert act("hover", {"name": "Shadow action"})["dispatched"]
        clicked = act("click", {"name": "查询"})
        assert "Trusted click" in clicked["observation"]["text"]
        for operation, expected in (("double_click", "dblclick:0:true"),
                                    ("right_click", "contextmenu:2:true"),
                                    ("middle_click", "auxclick:1:true")):
            gesture = act(operation, {"name": "Mouse gestures"})
            assert expected in gesture["observation"]["text"]
        assert act("key", {"testid": "order-field"}, key="Tab")["dispatched"]
        assert act("scroll", dy=150)["dispatched"]
        listed=dev.call("browseruse.observe",{"browser":browser,"mode":"options","document":page["document"],
                       "target":{"label":"Delivery method"},"limit":2},SCOPE)
        try:
            listing=wait_receipt(listed["id"])["result"]
            assert listing["truncated"] and listing["next_offset"]==2 and listing["values_exposed"] is False
        finally:dev.call("browseruse.result",{"id":listed["id"],"release":True},SCOPE)
        selection=act("select",{"label":"Delivery method"},choices=[{"label":"Express"}])
        assert selection["verified"] and selection["events_trusted"] is False
        assert "Selection events 1/1" in selection["observation"]["text"]
        assert act("select",{"label":"Delivery method"},choices=[{"value":"express"}])["changed"] is False
        assert act("select",{"label":"Colors"},choices=[{"index":0},{"label":"Blue"}])["verified"]
        assert act("select",{"label":"Colors"},choices=[])["verified"]
        waits = 0
        def complete(service, fields, failure=None):
            queued_call = dev.call(service, {"browser": browser, **fields}, SCOPE)
            try:
                receipt = wait_receipt(queued_call["id"], allow_failure=bool(failure))
                if failure:
                    assert receipt["state"] == "failed", receipt["state"]
                    assert receipt["error"]["code"] == failure, receipt.get("error")
                    return receipt
                return receipt["result"]
            finally:
                dev.call("browseruse.result", {"id": queued_call["id"], "release": True}, SCOPE)
        for fields in [
            {"until": "text", "text": "Trusted click"},
            {"until": "visible", "target": {"label": "订单号"}},
            {"until": "hidden", "target": {"css": "#does-not-exist"}},
            {"until": "enabled", "target": {"name": "Agree"}},
            {"until": "ready"}
        ]:
            assert complete("browseruse.wait", fields)["matched"]
            waits += 1
        failed_wait = complete("browseruse.wait", {"until": "visible", "target": {"css": "#never-created"}, "timeout_ms": 250}, "timed_out")
        assert failed_wait["error"]["effect_may_have_occurred"] is False
        invalid_selection=complete("browseruse.act",{"document":page["document"],"op":"select",
            "target":{"label":"Delivery method"},"choices":[{"index":2}]},"option_disabled")
        assert invalid_selection["error"]["effect_may_have_occurred"] is False
        navigated = complete("browseruse.navigate", {"action": "goto", "url": f"http://127.0.0.1:{dev.state()['port']}/__browser_use_page__.html?navigation=2"})
        assert navigated["observation"]["document"] != page["document"]
        stale = complete("browseruse.act", {"document": page["document"], "op": "click", "target": {"name": "查询"}}, "stale_document")
        assert stale["error"].get("effect_may_have_occurred", False) is False
        for navigation in ["back", "forward", "reload"]:
            assert complete("browseruse.navigate", {"action": navigation})["observation"]["ready"] == "complete"
        drag_page=complete("browseruse.navigate",{"action":"goto","url":f"http://127.0.0.1:{dev.state()['port']}/__browser_use_drag__.html"})["observation"]
        for source,kind,expected in (("Pointer handle","pointer","Pointer release true"),("HTML handle","html5","HTML drop verified")):
            gesture=complete("browseruse.act",{"document":drag_page["document"],"op":"drag","target":{"name":source},"destination":{"name":"Drop target"}})
            assert gesture["input_method"]=="native_drag" and gesture["drag_kind"]==kind
            assert expected in gesture["observation"]["text"]
            actions+=1
        subprocess.run(["node", "tools/client_probe.mjs"], cwd=dev.ROOT, check=True)
        report = {"passed": True, "backend": "actual-mdo-ui-webview2",
                  "public_services": ["browseruse.observe", "browseruse.act", "browseruse.navigate", "browseruse.wait", "browseruse.screenshot"],
                  "actions": actions, "navigations": 4, "wait_conditions": waits,
                  "automatic_readiness": True, "elements": len(page["elements"]),
                  "frames": len(page["frames"]), "qualified_frame_driver":True,"public_receipt_bytes": len(json.dumps(queued).encode())}
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
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
        path=dev.ENV/"public-sdk-result.json"
        try:report=json.loads(path.read_text(encoding="utf-8"))
        except (OSError,ValueError):report={}
        report.update({"passed":False,"state":"failed","error":str(error)[:1024]})
        path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
        print(error, file=sys.stderr)
        raise SystemExit(1)
