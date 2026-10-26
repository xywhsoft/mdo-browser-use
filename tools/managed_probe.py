"""Run Browser Use through mdo's actual managed tool and permission executor."""
import json
import base64
import struct
import sys
import time
import uuid

import dev

SCOPE = {"extension": "browser-use-managed-qa", "project": "default"}
FULL = "b0a00000000000000000000000000001"
READ_ONLY = "b0a00000000000000000000000000002"

def read_operation_result(identity, scope):
    result = dev.call("operation.result", {"id": identity}, scope)
    if result.get("encoding") != "utf-8-json":
        return result
    total = result["bytes"]
    if not 0 < total <= 2097152:
        raise RuntimeError("Managed probe result exceeds its bounded reader")
    output = bytearray()
    while len(output) < total:
        page = dev.call("operation.result", {"id": identity, "offset": len(output), "limit": 16384}, scope)
        data = base64.b64decode(page["base64"], validate=True)
        if not data or page["next"] != len(output) + len(data) or page["next"] > total:
            raise RuntimeError("Managed result range was inconsistent")
        output.extend(data)
    return json.loads(output)


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
            try:
                return read_operation_result(job["id"], scope)
            finally:
                try:
                    dev.call("operation.release", {"id": job["id"]}, scope)
                except (OSError, RuntimeError):
                    pass
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
    (dev.SOURCE / "app/web/__browser_use_popup__.html").write_bytes((dev.ROOT / "tests/fixtures/popup.html").read_bytes())
    (dev.SOURCE / "app/web/__browser_use_drag__.html").write_bytes((dev.ROOT / "tests/fixtures/drag.html").read_bytes())
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
        options=tool("browser_observe",{"browser":browser,"mode":"options","document":page["document"],"target":{"label":"Delivery method"}})
        assert options["success"] and value(options)["options"][3]["label"]=="Express"
        invalid_choice=tool("browser_act",{"browser":browser,"document":page["document"],"op":"select",
            "target":{"label":"Delivery method"},"choices":[{"index":2}]})
        assert invalid_choice["success"] is False and "option_disabled" in invalid_choice["text"]
        selected=tool("browser_act",{"browser":browser,"document":page["document"],"op":"select",
            "target":{"label":"Delivery method"},"choices":[{"label":"Express"}]})
        assert selected["success"] and value(selected)["verified"] and value(selected)["events_trusted"] is False
        assert "Selection events 1/1" in value(selected)["observation"]["text"]
        readonly_scope=dict(owner,session=READ_ONLY)
        readonly_browser=dev.call("browser.create",{"client":client,"url":f"http://127.0.0.1:{dev.state()['port']}/__browser_use_page__.html"},readonly_scope)["id"]
        try:
            readonly_page=value(tool("browser_observe",{"browser":readonly_browser},READ_ONLY))
            readonly_options=tool("browser_observe",{"browser":readonly_browser,"mode":"options","document":readonly_page["document"],"target":{"label":"Delivery method"}},READ_ONLY)
            assert readonly_options["success"] and value(readonly_options)["values_exposed"] is False
            assert tool("browser_act",{"browser":readonly_browser,"document":readonly_page["document"],"op":"select",
                "target":{"label":"Delivery method"},"choices":[{"index":3}]},READ_ONLY)["success"] is False
        finally:dev.call("browser.close",{"id":readonly_browser},readonly_scope)
        failed = tool("browser_act", {"browser": browser, "document": "stale-document", "op": "click", "target": {"name": "查询"}})
        assert failed["success"] is False
        assert "stale_document" in failed["text"]
        resumed = tool("browser_observe", {"browser": browser})
        assert resumed["success"], "A normal tool failure prevented the next managed tool"
        filled = tool("browser_act", {"browser": browser, "document": value(resumed)["document"],
                      "op": "fill", "target": {"label": "订单号"}, "text": "Managed native input"})
        assert filled["success"] and value(filled)["verification"] == "field_state"
        for operation, expected in (("double_click", "dblclick:0:true"),
                                    ("right_click", "contextmenu:2:true"),
                                    ("middle_click", "auxclick:1:true")):
            gesture = tool("browser_act", {"browser": browser, "document": page["document"],
                           "op": operation, "target": {"name": "Mouse gestures"}})
            assert gesture["success"] and expected in value(gesture)["observation"]["text"]
        captured = tool("browser_screenshot", {"browser": browser, "document": value(resumed)["document"],
                        "format": "png", "max_width": 800, "max_height": 600})
        assert captured["success"] and captured["image"]["media_type"] == "image/png"
        png = base64.b64decode(captured["image"]["base64"], validate=True)
        width, height = struct.unpack(">II", png[16:24])
        assert 0 < width <= 800 and 0 < height <= 600
        assert value(captured)["image"]["bytes"] == len(png)
        assert "base64" not in captured["text"], "Screenshot bytes were repeated in tool text"
        cropped = tool("browser_screenshot", {"browser": browser, "document": value(resumed)["document"],
                       "target": {"name": "查询"}, "format": "jpeg", "max_bytes": 16384})
        assert cropped["success"] and cropped["image"]["media_type"] == "image/jpeg"
        jpeg = base64.b64decode(cropped["image"]["base64"], validate=True)
        assert jpeg.startswith(b"\xff\xd8") and jpeg.endswith(b"\xff\xd9")
        assert value(cropped)["image"]["bytes"] == len(jpeg)
        assert tool("browser_wait", {"browser": browser, "until": "visible", "target": {"name": "查询"}})["success"]
        checkpoint = tool("browser_act", {"browser": browser, "document": value(resumed)["document"],
                          "op": "click", "target": {"name": "Confirm dialog"}})
        assert checkpoint["success"]
        boundary = value(checkpoint)
        assert boundary["outcome"] == "dialog_open" and boundary["completed"] is False
        pending = value(tool("browser_dialog", {"browser": browser, "action": "list"}))["dialogs"]
        assert len(pending) == 1 and pending[0]["kind"] == "confirm"
        denied_dialog = tool("browser_dialog_respond", {"browser": browser, "action": "respond",
                             "dialog": pending[0]["id"], "accept": True}, READ_ONLY)
        assert denied_dialog["success"] is False, "Read-only tool answered a dialog"
        answered = tool("browser_dialog_respond", {"browser": browser, "action": "respond",
                        "dialog": pending[0]["id"], "accept": False})
        assert answered["success"]
        assert tool("browser_wait", {"browser": browser, "until": "text", "text": "confirm:false"})["success"]
        popup = tool("browser_act", {"browser": browser, "document": value(resumed)["document"],
                     "op": "click", "target": {"name": "Open popup"}})
        assert popup["success"] and value(popup)["outcome"] == "popup_open"
        child = None
        for _ in range(100):
            child = next((row for row in value(tool("browser_tabs", {"action": "list"}))["tabs"]
                          if row.get("opener") == browser), None)
            if child:
                break
            time.sleep(0.05)
        assert child is not None, "Managed tool did not receive its scoped child tab"
        assert "Independent popup" in value(tool("browser_observe", {"browser": child["id"]}))["text"]
        foreign = value(tool("browser_tabs", {"action": "list"}, READ_ONLY))["tabs"]
        assert not any(row["id"] == child["id"] for row in foreign), "A different managed task could see the popup"
        assert tool("browser_open", {"action": "close", "browser": child["id"]})["success"]
        assert tool("browser_navigate", {"browser": browser, "action": "reload"})["success"]
        dragged_page=value(tool("browser_navigate",{"browser":browser,"action":"goto","url":f"http://127.0.0.1:{dev.state()['port']}/__browser_use_drag__.html"}))["observation"]
        for source,kind,expected in (("Pointer handle","pointer","Pointer release true"),("HTML handle","html5","HTML drop verified")):
            dragged=tool("browser_act",{"browser":browser,"document":dragged_page["document"],"op":"drag",
                "target":{"name":source},"destination":{"name":"Drop target"}})
            assert dragged["success"] and value(dragged)["drag_kind"]==kind
            assert expected in value(dragged)["observation"]["text"]
        readonly_drag=dev.call("browser.create",{"client":client,"url":f"http://127.0.0.1:{dev.state()['port']}/__browser_use_drag__.html"},readonly_scope)["id"]
        try:
            readonly_page=value(tool("browser_observe",{"browser":readonly_drag},READ_ONLY))
            denied_drag=tool("browser_act",{"browser":readonly_drag,"document":readonly_page["document"],"op":"drag",
                "target":{"name":"HTML handle"},"destination":{"name":"Drop target"}},READ_ONLY)
            assert denied_drag["success"] is False
            assert "context_mismatch" not in denied_drag["text"],"Foreign ownership hid the permission check"
        finally:dev.call("browser.close",{"id":readonly_drag},readonly_scope)
        assert tool("browser_open", {"action": "close", "browser": browser})["success"]
        browser = None
        report = {"passed": True, "backend": "actual-managed-tool-webview2", "registered_tools": 9,
                  "readonly_read_allowed": True, "readonly_write_denied": True,
                  "full_access_without_extra_prompt": True, "tool_failure_then_recovery": True,
                  "native_dialog_checkpoint_and_response": True, "readonly_dialog_response_denied": True,
                  "managed_popup_ownership": True, "native_screenshot_image": True,
                  "native_mouse_gestures": ["double_click", "right_click", "middle_click"],
                  "exact_selection_and_discovery":True,"readonly_select_denied":True,"native_drag":True}
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
    except Exception as error:
        output.write_text(json.dumps({"passed": False, "error": str(error)[:512]}) + "\n", encoding="utf-8")
        raise
    finally:
        if browser:
            try:
                for child in dev.call("browseruse.tabs", {"action": "list"}, owner)["tabs"]:
                    if child.get("opener") == browser:
                        dev.call("browseruse.tabs", {"action": "close", "browser": child["id"]}, owner)
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
