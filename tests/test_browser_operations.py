"""Real C operation adapter and UI broker admission, without a second Home."""
import json
import sys
import time
import unittest
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import dev


class BrowserOperationTests(unittest.TestCase):
    def setUp(self):
        self.scope = {"extension": "browser-use-admission-check", "session": "admission-task"}
        self.client = "browser-use-admission-window"
        dev.call("client.hello", {"client": self.client, "context": {}})
        self.browser = dev.call("browseruse.tabs", {"action": "create", "client": self.client}, self.scope)["id"]
        self.operation = "sdk-" + uuid.uuid4().hex
        self.submit = {"action": "submit", "operation": self.operation, "method": "browseruse.act",
                       "arguments": {"browser": self.browser, "document": "fixture", "op": "click", "target": {"ref": "e1"}}}
        self.started = False

    def tearDown(self):
        if self.started:
            for action in ("cancel", "get", "release"):
                dev.call("browseruse.operation", {"action": action, "operation": self.operation}, self.scope)
        dev.call("browseruse.tabs", {"action": "close", "browser": self.browser}, self.scope)

    def pending(self):
        for _ in range(100):
            state = dev.call("browseruse.operation", {"action": "get", "operation": self.operation}, self.scope)
            if state.get("id"):
                return state
            time.sleep(0.02)
        self.fail("Admission did not return its UI receipt")

    def test_repeated_submission_uses_one_ui_action_and_released_identity_is_not_replayed(self):
        dev.call("browseruse.operation", self.submit, self.scope); self.started = True
        original = self.pending()
        repeated = dev.call("browseruse.operation", self.submit, self.scope)
        self.assertEqual(repeated["id"], original["id"])
        self.assertNotIn("params", repeated)
        self.assertNotIn("context", repeated)
        queued = [r for r in dev.call("client.poll", {"client": self.client}) if r["id"] == original["id"]]
        self.assertEqual(len(queued), 1)
        dev.call("client.ack", {"client": self.client, "id": original["id"], "success": True,
                               "result": {"ok": True, "fixture_delivered": True}})
        completed = dev.call("browseruse.operation", {"action": "get", "operation": self.operation}, self.scope)
        self.assertTrue(completed["result"]["fixture_delivered"])
        dev.call("browseruse.operation", {"action": "release", "operation": self.operation}, self.scope)
        expired = dev.call("browseruse.operation", self.submit, self.scope)
        self.assertEqual(expired["state"], "uncertain")
        self.assertEqual(expired["error"]["code"], "result_expired")
        self.assertFalse(dev.call("client.poll", {"client": self.client}))

    def test_changed_arguments_conflict_and_foreign_task_cannot_recover(self):
        dev.call("browseruse.operation", self.submit, self.scope); self.started = True
        self.pending()
        changed = dict(self.submit, arguments=dict(self.submit["arguments"], target={"ref": "e2"}))
        status, _, raw = dev.request("POST", "/api/v1/application/call", {"method": "browseruse.operation", "params": changed, "context": self.scope})
        self.assertEqual(status, 409, json.loads(raw))
        self.assertEqual(json.loads(raw)["error"]["code"], "sdk_identity_conflict")
        status, _, raw = dev.request("POST", "/api/v1/application/call", {"method": "browseruse.operation",
            "params": {"action": "get", "operation": self.operation}, "context": dict(self.scope, session="foreign")})
        self.assertEqual(status, 409, json.loads(raw))
        self.assertEqual(json.loads(raw)["error"]["code"], "context_mismatch")

    def test_world_recovery_advice_and_effect_uncertainty_survive_original_receipt_recovery(self):
        for possible in (False, True):
            self.operation = "sdk-" + uuid.uuid4().hex
            submit = dict(self.submit, operation=self.operation)
            dev.call("browseruse.operation", submit, self.scope); self.started = True
            original = self.pending()
            queued = [row for row in dev.call("client.poll", {"client": self.client}) if row["id"] == original["id"]]
            self.assertEqual(len(queued), 1)
            detail = {"code": "action_uncertain" if possible else "browser_world_limit",
                      "message": "Use manual control or explicitly close this browser; do not replay the action",
                      "effect_may_have_occurred": possible, "world_reason": "world_identity_limit",
                      "recovery": {"action": "manual_or_close", "automatic_retry": False}}
            dev.call("client.ack", {"client": self.client, "id": original["id"], "success": True,
                                   "result": {"ok": False, "error": detail}})
            for method, params in (("browseruse.operation", {"action": "get", "operation": self.operation}),
                                   ("browseruse.operation", submit), ("browseruse.result", {"id": original["id"]})):
                recovered = dev.call(method, params, self.scope)
                self.assertEqual(recovered["state"], "failed")
                self.assertEqual(recovered["error"], detail)
                self.assertEqual(recovered["result"]["error"], detail)
            self.assertFalse(dev.call("client.poll", {"client": self.client}), "Recovery requeued the failed browser action")
            dev.call("browseruse.operation", {"action": "release", "operation": self.operation}, self.scope)

    def test_proven_argument_failure_is_distinct_from_an_uncertain_effect(self):
        request = dict(self.submit, method="browseruse.tabs", arguments={"action": "create"})
        dev.call("browseruse.operation", request, self.scope); self.started = True
        for _ in range(100):
            result = dev.call("browseruse.operation", {"action": "get", "operation": self.operation}, self.scope)
            if result["state"] != "pending":
                break
            time.sleep(0.02)
        self.assertEqual(result["state"], "failed")
        self.assertEqual(result["error"]["code"], "client_required")
        self.assertFalse(result["error"]["effect_may_have_occurred"])


if __name__ == "__main__":
    unittest.main()
