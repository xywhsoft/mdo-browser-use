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

    def test_actual_create_get_list_reveal_and_close(self):
        created = self.invoke({"action": "create", "client": self.client,
                               "url": "https://example.com/", "key": "sdk-tab"})
        identity = created["id"]
        try:
            self.assertEqual(created["state"], "suspended")
            self.assertEqual(self.invoke({"action": "get", "browser": identity})["id"], identity)
            rows = self.invoke({"action": "list", "client": self.client})["tabs"]
            self.assertIn(identity, [r["id"] for r in rows])
            panel = self.invoke({"action": "reveal", "browser": identity})
            self.assertEqual(panel["resource"]["id"], identity)
            dev.call("panel.close", {"id": panel["id"]}, self.scope)
        finally:
            self.invoke({"action": "close", "browser": identity})

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


if __name__ == "__main__":
    unittest.main()
