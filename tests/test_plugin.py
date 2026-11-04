"""Exercise the actual installed C plugin in the reusable project deployment."""
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("browser_use_dev", ROOT / "tools/dev.py")
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)


class PluginTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = "browser-use-sdk-check"
        dev.call("client.hello", {"client": cls.client, "context": {}})
        # The headless SDK check models the type contribution normally made by
        # the real side pane. It does not claim a native browser is attached.
        if not any(r["id"] == "mdo.browser" for r in dev.call("panel.types")):
            dev.call("panel.register", {"id": "mdo.browser", "title": "Browser",
                                        "renderer": "browser"})
        cls.scope = {"extension": "browser-use-check", "session": "scope-a"}

    def invoke(self, params, *, scope=None):
        return dev.call("browseruse.tabs", params, scope or self.scope)

    def reject(self, params, status, code=None, *, scope=None):
        actual, _, raw = dev.request("POST", "/api/v1/application/call", {
            "method": "browseruse.tabs", "params": params, "context": scope or self.scope})
        result = json.loads(raw)
        self.assertEqual(actual, status, result)
        if code:
            self.assertEqual(result["error"]["code"], code)

    def test_shared_schema_is_published_by_native_registration(self):
        description = dev.call("sdk.describe", {"id": "browseruse.tabs"})
        contract = json.loads((ROOT / "contracts.json").read_text(encoding="utf-8"))["services"][0]
        self.assertEqual(description["input_schema"], contract["input"])
        self.assertEqual(description["output_schema"], contract["output"])

    def test_chooser_contract_and_conditional_fields_are_enforced_in_c(self):
        contract=next(row for row in json.loads((ROOT/'contracts.json').read_text(encoding='utf-8'))['services'] if row['id']=='browseruse.chooser')
        self.assertEqual(dev.call('sdk.describe',{'id':'browseruse.chooser'})['input_schema'],contract['input'])
        for method,params,code in (
            ('browseruse.chooser',{'browser':'unused','action':'cancel'},'chooser_required'),
            ('browseruse.chooser',{'browser':'unused','action':'list','chooser':'unused'},'arguments_invalid'),
            ('browseruse.upload',{'browser':'unused','action':'set','document':'d1','chooser':'picker','target':{'ref':'e1'},'files':[{'path':'file.bin'}]},'upload_target_required'),
            ('browseruse.upload',{'browser':'unused','action':'clear','document':'d1','chooser':'picker'},'arguments_invalid'),
        ):
            status,_,raw=dev.request('POST','/api/v1/application/call',{'method':method,'params':params,'context':self.scope})
            self.assertEqual(status,422,json.loads(raw));self.assertEqual(json.loads(raw)['error']['code'],code)

    def test_actual_create_get_list_reveal_and_close(self):
        created = self.invoke({"action": "create", "client": self.client,
                               "url": "https://example.com/", "key": "sdk-tab"})
        identity = created["id"]
        try:
            self.assertEqual(created["state"], "suspended")
            self.assertEqual(created["download_policy"],"intercept")
            self.assertEqual(self.invoke({"action": "get", "browser": identity})["id"], identity)
            rows = self.invoke({"action": "list", "client": self.client})["tabs"]
            self.assertIn(identity, [r["id"] for r in rows])
            panel = self.invoke({"action": "reveal", "browser": identity})
            self.assertEqual(panel["resource"]["id"], identity)
            dev.call("panel.close", {"id": panel["id"]}, self.scope)
        finally:
            self.invoke({"action": "close", "browser": identity})

    def test_create_requests_display_without_a_followup_browser_command(self):
        args = {"action": "create", "client": self.client,
                "url": "http://127.0.0.1/", "key": "create-visible-tab"}
        browser = self.invoke(args)
        try:
            def panels():
                return [row for row in dev.call("panel.list", {}, self.scope)
                        if row.get("resource", {}).get("id") == browser["id"]]
            rows = panels()
            self.assertEqual(len(rows), 1, "Create returned success without a display panel")
            panel = rows[0]
            self.assertEqual(panel["type"], "mdo.browser")
            self.assertEqual(panel["client"], self.client)
            self.assertEqual(panel["reveal"], "activate")
            self.assertEqual(panel["owner"], browser["owner"])
            self.assertEqual(panel["context"], browser["context"])
            # Publication requests display; a headless client cannot prove load.
            self.assertEqual(browser["state"], "suspended")
            self.assertIs(browser["automation_required"], True)
            self.assertNotIn("runtime", panel)
            self.assertEqual(self.invoke(args)["id"], browser["id"])
            self.assertEqual([row["id"] for row in panels()], [panel["id"]])
        finally:
            self.invoke({"action": "close", "browser": browser["id"]})

    def test_keyed_older_browser_and_panel_keep_required_automation_on_reuse(self):
        browser = dev.call("browser.create", {"client": self.client, "key": "older-create-policy",
                           "download_policy": "intercept"}, self.scope)
        try:
            original = dev.call("panel.open", {"client": self.client, "type": "mdo.browser",
                                "key": browser["id"], "resource": {"id": browser["id"]},
                                "params": {"retained": "original"}}, self.scope)
            reused = self.invoke({"action": "create", "client": self.client, "key": "older-create-policy"})
            self.assertEqual(reused["id"], browser["id"])
            panels = [row for row in dev.call("panel.list", {}, self.scope)
                      if row.get("resource", {}).get("id") == browser["id"]]
            self.assertEqual([row["id"] for row in panels], [original["id"]])
            self.assertIs(panels[0]["params"].get("automation_required"), True)
            self.assertEqual(panels[0]["params"]["retained"], "original")
        finally:
            self.invoke({"action": "close", "browser": browser["id"]})

    def test_keyed_activation_does_not_invalidate_panel_content(self):
        browser = self.invoke({"action": "create", "client": self.client,
                               "url": "http://127.0.0.1/", "key": "activation-revision"})
        try:
            panel = next(row for row in dev.call("panel.list", {}, self.scope)
                         if row.get("resource", {}).get("id") == browser["id"])
            revealed = self.invoke({"action": "reveal", "browser": browser["id"]})
            self.assertEqual(revealed["id"], panel["id"])
            self.assertEqual(revealed["revision"], panel["revision"])
            self.assertGreater(revealed["reveal_revision"], panel["reveal_revision"])
            reused = dev.call("panel.open", {"client": self.client, "type": "mdo.browser",
                "key": browser["id"], "resource": {"id": browser["id"]}, "reveal": "background"}, self.scope)
            self.assertEqual(reused["reveal_revision"], revealed["reveal_revision"])
            self.assertEqual(self.invoke({"action": "get", "browser": browser["id"]})["revision"], browser["revision"])
        finally:
            self.invoke({"action": "close", "browser": browser["id"]})

    def test_context_cannot_be_bypassed_by_an_explicit_client(self):
        created = self.invoke({"action": "create", "client": self.client, "key": "scoped-tab"})
        try:
            foreign = dict(self.scope, session="scope-b")
            rows = self.invoke({"action": "list", "client": self.client}, scope=foreign)["tabs"]
            self.assertNotIn(created["id"], [r["id"] for r in rows])
            self.reject({"action": "get", "browser": created["id"]}, 409,
                        "context_mismatch", scope=foreign)
            self.reject({"action": "close", "browser": created["id"]}, 409,
                        "context_mismatch", scope=foreign)
        finally:
            self.invoke({"action": "close", "browser": created["id"]})

    def test_native_boundary_rejects_invalid_and_unknown_arguments(self):
        for params in ({}, {"action": "unknown"}, {"action": "list", "script": "1"},
                       {"action": "create", "client": 1}, {"action": "get", "browser": ""}):
            with self.subTest(params=params):
                self.reject(params, 422, "arguments_invalid")
        self.reject({"action": "get"}, 422, "browser_required")
        self.reject({"action": "create"}, 422, "client_required")
        self.reject({"action": "create", "client": "not-online"}, 409, "client_missing")

    def test_unsafe_navigation_schemes_and_embedded_credentials_are_rejected(self):
        for url in ("file:///etc/passwd", "javascript:alert(1)", "https://user:pass@example.com",
                    "https://", "https://example.com/\nheader", "https://example.com\\foo"):
            with self.subTest(url=url):
                self.reject({"action": "create", "client": self.client, "url": url}, 422, "url_invalid")

    def test_observation_receipts_remain_scoped_and_delivery_is_not_page_success(self):
        browser = self.invoke({"action": "create", "client": self.client, "key": "receipt-scope"})
        receipt = dev.call("browseruse.observe", {"browser": browser["id"]}, self.scope)
        try:
            pending = dev.call("browseruse.result", {"id": receipt["id"]}, self.scope)
            self.assertEqual(pending["state"], "pending")
            self.assertNotIn("params", pending)
            self.assertNotIn("context", pending)
            status, _, raw = dev.request("POST", "/api/v1/application/call", {
                "method": "browseruse.result", "params": {"id": receipt["id"], "release": True},
                "context": dict(self.scope, session="other-session")})
            self.assertEqual(status, 409, json.loads(raw))
            dev.call("client.ack", {"client": self.client, "id": receipt["id"], "success": True,
                "result": {"ok": False, "error": {"code": "manual_control", "message": "User takeover"}}})
            failed = dev.call("browseruse.result", {"id": receipt["id"]}, self.scope)
            self.assertEqual(failed["state"], "failed")
            self.assertEqual(failed["error"]["code"], "manual_control")
        finally:
            dev.call("browseruse.result", {"id": receipt["id"], "release": True}, self.scope)
            self.invoke({"action": "close", "browser": browser["id"]})

    def test_cancel_stops_owned_receipt_and_late_ack_cannot_revive_it(self):
        browser = self.invoke({"action": "create", "client": self.client, "key": "cancel-receipt"})
        receipt = dev.call("browseruse.observe", {"browser": browser["id"]}, self.scope)
        try:
            status, _, raw = dev.request("POST", "/api/v1/application/call", {
                "method": "ui.cancel", "params": {"id": receipt["id"]},
                "context": dict(self.scope, session="foreign")})
            self.assertEqual(status, 409, json.loads(raw))
            cancelled = dev.call("browseruse.result", {"id": receipt["id"], "cancel": True}, self.scope)
            self.assertEqual(cancelled["state"], "cancelled")
            self.assertNotIn("params", cancelled)
            self.assertNotIn("context", cancelled)
            dev.call("client.ack", {"client": self.client, "id": receipt["id"], "success": True,
                                    "result": {"ok": True, "text": "late"}})
            self.assertEqual(dev.call("browseruse.result", {"id": receipt["id"]}, self.scope)["state"], "cancelled")
        finally:
            dev.call("browseruse.result", {"id": receipt["id"], "release": True}, self.scope)
            self.invoke({"action": "close", "browser": browser["id"]})

    def test_ui_origin_includes_empty_fields_and_cannot_inherit_another_selected_chat(self):
        scope = {"extension": "browser-use-empty-origin"}
        queued = dev.call("ui.invoke", {"client": self.client, "action": "ui.toast", "params": {"message": "fixture"},
            "context": {"project": "forged-project", "session": "forged-session", "run": "forged-run"}}, scope)
        try:
            frozen = queued["context"]
            self.assertEqual(frozen["project"], "default")
            for field in ("session", "run", "namespace", "member"):
                self.assertEqual(frozen[field], "")
            self.assertEqual(frozen["extension"], scope["extension"])
        finally:
            dev.call("ui.cancel", {"id": queued["id"]}, scope)
            dev.call("ui.release", {"id": queued["id"]}, scope)

    def test_action_arguments_fail_before_queueing(self):
        for fields, code in [
            ({"op": "click"}, "target_required"),
            ({"op": "double_click"}, "target_required"),
            ({"op": "right_click"}, "target_required"),
            ({"op": "middle_click"}, "target_required"),
            ({"op": "fill", "target": {"css": "input"}}, "text_required"),
            ({"op": "click", "target": {"ref": "e1", "name": "Other"}}, "target_invalid"),
            ({"op": "check", "target": {"name": "Agree"}}, "checked_required"),
            ({"op": "key", "target": {"name": "Field"}, "key": "arbitrary CDP"}, "arguments_invalid"),
            ({"op": "click", "target": {"script": "arbitrary JS"}}, "arguments_invalid")
        ]:
            with self.subTest(code=code, fields=fields):
                status, _, raw = dev.request("POST", "/api/v1/application/call", {
                    "method": "browseruse.act", "params": {"browser": "unused", "document": "test", **fields},
                    "context": self.scope})
                self.assertEqual(status, 422, json.loads(raw))
                self.assertEqual(json.loads(raw)["error"]["code"], code)

    def test_ephemeral_journal_placeholder_is_not_a_successful_observation(self):
        browser = self.invoke({"action": "create", "client": self.client, "key": "ephemeral-result"})
        receipt = dev.call("browseruse.observe", {"browser": browser["id"]}, self.scope)
        try:
            dev.call("client.ack", {"client": self.client, "id": receipt["id"], "success": True,
                                    "result": {"ephemeral": True}})
            result = dev.call("browseruse.result", {"id": receipt["id"]}, self.scope)
            self.assertEqual(result["state"], "failed")
            self.assertEqual(result["error"]["code"], "result_expired")
        finally:
            dev.call("browseruse.result", {"id": receipt["id"], "release": True}, self.scope)
            self.invoke({"action": "close", "browser": browser["id"]})

    def test_dialog_validation_and_scoped_receipts(self):
        for fields, code in [
            ({"action": "respond"}, "dialog_response_required"),
            ({"action": "respond", "dialog": "pending"}, "dialog_response_required"),
            ({"action": "respond", "dialog": "pending", "accept": "yes"}, "arguments_invalid"),
            ({"action": "list", "script": "arbitrary JS"}, "arguments_invalid")
        ]:
            with self.subTest(fields=fields):
                status, _, raw = dev.request("POST", "/api/v1/application/call", {
                    "method": "browseruse.dialog", "params": {"browser": "unused", **fields}, "context": self.scope})
                self.assertEqual(status, 422, json.loads(raw))
                self.assertEqual(json.loads(raw)["error"]["code"], code)
        browser = self.invoke({"action": "create", "client": self.client, "key": "dialog-scope"})
        receipt = dev.call("browseruse.dialog", {"browser": browser["id"], "action": "list"}, self.scope)
        try:
            status, _, raw = dev.request("POST", "/api/v1/application/call", {
                "method": "browseruse.result", "params": {"id": receipt["id"]},
                "context": dict(self.scope, session="foreign")})
            self.assertEqual(status, 409, json.loads(raw))
            dev.call("client.ack", {"client": self.client, "id": receipt["id"], "success": True,
                                    "result": {"ok": True, "dialogs": []}})
            result = dev.call("browseruse.result", {"id": receipt["id"]}, self.scope)
            self.assertEqual(result["state"], "succeeded")
            self.assertEqual(result["result"]["dialogs"], [])
        finally:
            dev.call("browseruse.result", {"id": receipt["id"], "release": True}, self.scope)
            self.invoke({"action": "close", "browser": browser["id"]})


if __name__ == "__main__":
    unittest.main()
