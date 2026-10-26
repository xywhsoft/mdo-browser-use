"""Real broker close semantics, including queued work and panel admission."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import dev


class BrowserLifecycleTests(unittest.TestCase):
    scope = {"extension": "browser-close-qa", "session": "close-task"}
    client = "browser-close-client"

    def setUp(self):
        self.browsers, self.panels, self.receipts = [], [], []
        dev.call("client.hello", {"client": self.client, "context": {}})
        for kind in ("browser", "custom"):
            dev.call("panel.register", {"id": "close-qa-" + kind,
                     "renderer": kind, "title": "Lifecycle QA"}, self.scope)

    def tearDown(self):
        for receipt, scope in self.receipts:
            dev.call("ui.cancel", {"id": receipt}, scope)
            dev.call("ui.release", {"id": receipt}, scope)
        for panel in self.panels:
            self.optional("panel.close", {"id": panel})
        for browser in self.browsers:
            self.optional("browser.close", {"id": browser})

    def optional(self, method, params):
        status, _, raw = dev.request("POST", "/api/v1/application/call",
            {"method": method, "params": params, "context": self.scope})
        self.assertIn(status, (200, 404), json.loads(raw))

    def browser(self):
        row = dev.call("browser.create", {"client": self.client}, self.scope)
        self.browsers.append(row["id"])
        return row["id"]

    def panel(self, browser, kind="browser"):
        row = dev.call("panel.open", {"type": "close-qa-" + kind,
            "client": self.client, "resource": {"id": browser}}, self.scope)
        self.panels.append(row["id"])
        return row["id"]

    def pending(self, browser, scope=None):
        scope = scope or self.scope
        result = dev.call("ui.invoke", {"client": self.client,
            "action": "workbench.browser", "params": {"id": browser, "command": "state"}}, scope)
        self.receipts.append((result["id"], scope))
        return result["id"]

    def test_close_removes_only_browser_panels_and_prevents_reopening_stale_reference(self):
        browser, other = self.browser(), self.browser()
        bound = self.panel(browser)
        kept = {self.panel(other), self.panel(browser, "custom")}
        dev.call("browser.close", {"id": browser}, self.scope)
        live = {row["id"] for row in dev.call("panel.list", {}, self.scope)}
        self.assertNotIn(bound, live, "Closing a browser left a dangling browser panel")
        self.assertTrue(kept.issubset(live), "Closing erased unrelated panels")
        status, _, raw = dev.request("POST", "/api/v1/application/call", {
            "method": "panel.open", "params": {"type": "close-qa-browser",
            "client": self.client, "resource": {"id": browser}}, "context": self.scope})
        reply = json.loads(raw)
        if status == 200:
            self.panels.append(reply["data"]["id"])
        self.assertEqual(status, 404, reply)
        self.assertEqual(reply["error"]["code"], "browser_missing")
        # Custom renderers may use this ID as arbitrary data. A browser
        # renderer must reject a stale reference even on a later update.
        browser_panel = next(row for row in dev.call("panel.list", {}, self.scope)
                             if row["resource"]["id"] == other)
        status, _, raw = dev.request("POST", "/api/v1/application/call", {
            "method": "panel.update", "params": {"id": browser_panel["id"],
            "revision": browser_panel["revision"], "resource": {"id": browser}}, "context": self.scope})
        self.assertEqual(status, 404, json.loads(raw))

    def test_close_cancels_queued_work_without_accepting_late_success(self):
        browser, other = self.browser(), self.browser()
        closed = self.pending(browser)
        unrelated = self.pending(other)
        foreign_scope = dict(self.scope, session="another-task")
        foreign = self.pending(browser, foreign_scope)
        dev.call("browser.close", {"id": browser}, self.scope)
        state = dev.call("ui.result", {"id": closed}, self.scope)
        self.assertEqual(state["state"], "cancelled", "Closed browser work remained deliverable")
        self.assertIn("closed", state["error"].lower())
        late = dev.call("client.ack", {"client": self.client, "id": closed,
                        "success": True, "result": {"late": True}})
        self.assertEqual(late["state"], "cancelled")
        self.assertNotIn("result", late)
        polled = {row["id"] for row in dev.call("client.poll", {"client": self.client})}
        self.assertNotIn(closed, polled)
        self.assertTrue({unrelated, foreign}.issubset(polled))

    def test_public_receipt_is_cancelled_and_closed_browser_rejects_new_commands(self):
        browser = self.browser()
        receipt = dev.call("browseruse.wait", {"browser": browser,
                           "until": "text", "text": "never", "timeout_ms": 60000}, self.scope)
        self.receipts.append((receipt["id"], self.scope))
        dev.call("browseruse.tabs", {"action": "close", "browser": browser}, self.scope)
        self.assertEqual(dev.call("browseruse.result", {"id": receipt["id"]}, self.scope)["state"], "cancelled")
        status, _, raw = dev.request("POST", "/api/v1/application/call", {
            "method": "browser.command", "params": {"id": browser, "command": "state"},
            "context": self.scope})
        self.assertEqual(status, 404, json.loads(raw))


if __name__ == "__main__":
    unittest.main()
