"""Actual broker admission: ownership, one identity and volatile native tickets."""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import dev


class PopupAdmissionTests(unittest.TestCase):
    scope = {"extension": "popup-admission-qa", "session": "source-task"}
    client = "popup-admission-client"
    ticket = "popup-{00000000-0000-0000-0000-000000000001}"

    def setUp(self):
        self.source = dev.call("browser.create", {"client": self.client}, self.scope)
        self.child = None

    def tearDown(self):
        if self.child:
            dev.call("browser.close", {"id": self.child["id"]}, self.scope)
        dev.call("browser.close", {"id": self.source["id"]}, self.scope)

    def admit(self, **fields):
        self.child = dev.call("browser.popup.admit", {"client": self.client, "popup": self.ticket,
                              "opener": self.source["id"], **fields}, self.scope)
        return self.child

    def reject(self, method, params, code, *, scope=None):
        status, _, raw = dev.request("POST", "/api/v1/application/call", {
            "method": method, "params": params, "context": scope or self.scope})
        result = json.loads(raw)
        self.assertIn(status, (409, 422), result)
        self.assertEqual(result["error"]["code"], code)

    def test_admit_is_idempotent_and_ticket_is_not_persisted_as_runtime(self):
        first = self.admit(url="https://example.com/popup")
        self.assertEqual(first["opener"], self.source["id"])
        self.assertEqual(first["context"], self.source["context"])
        self.assertEqual(first["owner"], self.scope["extension"])
        self.assertEqual(self.admit(url="https://example.com/popup")["id"], first["id"])
        row = dev.call("browser.get", {"id": first["id"]}, self.scope)
        self.assertEqual(row["runtime"]["popup_request"], {"id": self.ticket, "opener": self.source["id"]})
        saved = json.loads((dev.HOME / "data/workbench.json").read_text(encoding="utf-8"))
        stored = next(row for row in saved["browsers"] if row["id"] == first["id"])
        self.assertNotIn("runtime", stored)
        self.assertNotIn("popup_request", stored)
        params = {"id": first["id"], "client": self.client, "popup": self.ticket}
        self.assertTrue(dev.call("browser.popup.settle", params, self.scope)["settled"])
        self.assertTrue(dev.call("browser.popup.settle", params, self.scope)["settled"])
        recovered = self.admit(url="https://example.com/popup")
        self.assertNotIn("popup_request", recovered.get("runtime", {}), "Settled native admission was resurrected")

    def test_foreign_context_client_and_changed_opener_cannot_adopt(self):
        params = {"client": self.client, "popup": self.ticket, "opener": self.source["id"]}
        self.reject("browser.popup.admit", params, "context_mismatch", scope=dict(self.scope, session="foreign"))
        self.reject("browser.popup.admit", dict(params, client="another-client"), "context_mismatch")
        child = self.admit()
        settle = {"id": child["id"], "client": self.client, "popup": self.ticket}
        self.reject("browser.popup.settle", settle, "context_mismatch", scope=dict(self.scope, extension="other-extension"))
        self.reject("browser.popup.settle", dict(settle, popup=self.ticket.replace("0001}", "0002}")), "popup_identity_mismatch")
        other = dev.call("browser.create", {"client": self.client}, self.scope)
        try:
            self.reject("browser.popup.admit", dict(params, opener=other["id"]), "popup_identity_mismatch")
        finally:
            dev.call("browser.close", {"id": other["id"]}, self.scope)

    def test_invalid_ticket_and_unsafe_urls_fail_before_admission(self):
        params = {"client": self.client, "popup": self.ticket, "opener": self.source["id"]}
        self.reject("browser.popup.admit", dict(params, popup="forged-window"), "popup_arguments_invalid")
        for url in ("file:///private", "javascript:alert(1)", "https://u:p@example.com/", "http://", "http://x/\nheader", "http://x\\path"):
            with self.subTest(url=url):
                self.reject("browser.popup.admit", dict(params, url=url), "popup_url_invalid")
        child = self.admit(url="about:blank")
        self.assertNotIn("url", child)


if __name__ == "__main__":
    unittest.main()
