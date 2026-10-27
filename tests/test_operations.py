"""Verify actual admission receipts do not cross task/extension boundaries."""
import json
import sys
import time
import unittest
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import dev


class OperationTests(unittest.TestCase):
    def setUp(self):
        self.scope = {"extension": "browser-use-operation-check", "session": "receipt-owner"}
        self.request = {"id": "sdk-" + uuid.uuid4().hex, "method": "runtime.sleep", "params": {"milliseconds": 1}}
        self.identity = dev.call("operation.start", self.request, self.scope)["id"]
        for _ in range(100):
            if dev.call("operation.get", {"id": self.identity}, self.scope).get("terminal"):
                return
            time.sleep(0.02)
        self.fail("QA operation did not finish")

    def tearDown(self):
        dev.call("operation.release", {"id": self.identity}, self.scope)

    def reject(self, method, params, scope):
        status, _, raw = dev.request("POST", "/api/v1/application/call", {"method": method, "params": params, "context": scope})
        self.assertEqual(status, 409, json.loads(raw))
        self.assertEqual(json.loads(raw)["error"]["code"], "context_mismatch")

    def test_foreign_context_cannot_read_cancel_release_or_list_live_receipt(self):
        for foreign in [dict(self.scope, extension="other-extension"), dict(self.scope, session="other-session"),
                        dict(self.scope,namespace="other-namespace"),dict(self.scope,member="other-member")]:
            for method in ("operation.get", "operation.result", "operation.cancel", "operation.release"):
                with self.subTest(method=method, scope=foreign):
                    self.reject(method, {"id": self.identity}, foreign)
            self.assertNotIn(self.identity, [row["id"] for row in dev.call("operation.list", {}, foreign)])
        self.assertEqual(dev.call("operation.get", {"id": self.identity}, self.scope)["state"], "succeeded")

    def test_released_admission_remains_scoped_and_is_never_reexecuted(self):
        dev.call("operation.release", {"id": self.identity}, self.scope)
        replay = dev.call("operation.start", self.request, self.scope)
        self.assertTrue(replay["terminal"])
        self.assertFalse(replay["result_available"])
        self.reject("operation.get", {"id": self.identity}, dict(self.scope, session="foreign"))
        self.reject("operation.start", self.request, dict(self.scope, extension="foreign"))
        status, _, raw = dev.request("POST", "/api/v1/application/call", {
            "method": "operation.result", "params": {"id": self.identity}, "context": self.scope})
        self.assertEqual(status, 410, json.loads(raw))

    def test_serial_fast_operations_never_advertise_a_pending_result_as_ready(self):
        for _ in range(12):
            identity=dev.call('operation.start',dict(self.request,id='sdk-'+uuid.uuid4().hex),self.scope)['id']
            try:
                for attempt in range(100):
                    info=dev.call('operation.get',{'id':identity},self.scope)
                    if info.get('terminal'):break
                    self.assertEqual(info['state'],'running')
                    self.assertFalse(info.get('result_available'))
                    time.sleep(.001)
                self.assertTrue(info.get('terminal'))
                dev.call('operation.result',{'id':identity},self.scope)
                listed=next(row for row in dev.call('operation.list',{},self.scope) if row['id']==identity)
                self.assertTrue(listed['terminal'])
            finally:
                dev.call('operation.release',{'id':identity},self.scope)


if __name__ == "__main__":
    unittest.main()
