"""Actual screenshot service boundaries and managed C image output validation."""
import base64
import copy
import json
from pathlib import Path
import sys
import time
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import dev
from managed_probe import FULL, READ_ONLY, read_operation_result, value


class ScreenshotTests(unittest.TestCase):
    client = "browser-image-formatter-qa"
    owner = {"extension": "plugin.mdo.browser-use", "project": "default", "session": FULL}
    caller = {"extension": "browser-image-test", "project": "default", "session": FULL}
    png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZ7sAAAAASUVORK5CYII="

    @classmethod
    def setUpClass(cls):
        for identity, permission in ((FULL, "full-access"), (READ_ONLY, "read-only")):
            created = dev.call("session.create", {"body": {"project_id": "default", "client_session_id": identity,
                               "title": "Browser Use managed QA", "permission_profile": permission,
                               "workspace_root": str(dev.ROOT)}}, cls.caller)
            if created["data"]["permission_profile"] != permission:
                raise RuntimeError("The persistent QA permission changed; refusing to overwrite it")
        dev.call("client.hello", {"client": cls.client})

    def setUp(self):
        self.browser = dev.call("browser.create", {"client": self.client}, self.owner)["id"]
        self.extra = []

    def tearDown(self):
        for browser, scope in self.extra:
            dev.call("browser.close", {"id": browser}, scope)
        dev.call("browser.close", {"id": self.browser}, self.owner)

    def test_screenshot_validates_before_admission_and_receipts_remain_scoped(self):
        for fields, code in [
            ({"mode": "element"}, "target_required"),
            ({"target": {"name": "Query"}}, "document_required"),
            ({"target": {"name": "Query"}, "document": "d1", "mode": "full_page"}, "target_invalid"),
            ({"document": "d1", "target": {"ref": "e1", "css": "input"}}, "target_invalid"),
            ({"max_width": 4096}, "arguments_invalid"),
            ({"max_bytes": 1048576}, "arguments_invalid"),
            ({"format": "svg"}, "arguments_invalid")
        ]:
            with self.subTest(fields=fields):
                status, _, raw = dev.request("POST", "/api/v1/application/call", {
                    "method": "browseruse.screenshot", "params": {"browser": self.browser, **fields}, "context": self.owner})
                self.assertEqual(status, 422, json.loads(raw))
                self.assertEqual(json.loads(raw)["error"]["code"], code)
        receipt = dev.call("browseruse.screenshot", {"browser": self.browser}, self.owner)
        try:
            status, _, raw = dev.request("POST", "/api/v1/application/call", {
                "method": "browseruse.result", "params": {"id": receipt["id"]},
                "context": dict(self.owner, session="another-task")})
            self.assertEqual(status, 409, json.loads(raw))
            dev.call("browseruse.result", {"id": receipt["id"], "cancel": True}, self.owner)
        finally:
            dev.call("browseruse.result", {"id": receipt["id"], "release": True}, self.owner)

    def execute_image(self, image, session=FULL, visual=None):
        caller = dict(self.caller, session=session)
        browser = self.browser
        if session != FULL:
            scope = dict(self.owner, session=session)
            browser = dev.call("browser.create", {"client": self.client}, scope)["id"]
            self.extra.append((browser, scope))
        job = dev.call("operation.start", {"id": "sdk-" + uuid.uuid4().hex, "method": "tool.call",
                       "params": {"project": "default", "session": session, "tool": "browser_screenshot",
                                  "arguments": {"browser": browser}}}, caller)
        end = time.monotonic() + 5
        pending = None
        while time.monotonic() < end:
            actions = dev.call("client.poll", {"client": self.client})
            pending = next((row for row in actions if row.get("params", {}).get("id") == browser), None)
            if pending:
                break
            time.sleep(0.02)
        self.assertIsNotNone(pending, "The actual managed worker did not queue its screenshot")
        dev.call("client.ack", {"client": self.client, "id": pending["id"], "success": True,
                 "result": {"ok": True, "document": "d-image-fixture", "image": image,
                            **({'visual_reference':visual} if visual is not None else {})}})
        while time.monotonic() < end:
            state = dev.call("operation.get", {"id": job["id"]}, caller)
            if state.get("terminal"):
                self.assertEqual(state["state"], "succeeded", state.get("error"))
                try:
                    return read_operation_result(job["id"], caller)
                finally:
                    dev.call("operation.release", {"id": job["id"]}, caller)
            time.sleep(0.02)
        dev.call("operation.cancel", {"id": job["id"]}, caller)
        self.fail("The managed image formatter did not complete")

    def test_managed_image_validation_and_failure_recovery(self):
        image = {"media_type": "image/png", "base64": self.png, "width": 1, "height": 1,
                 "bytes": len(base64.b64decode(self.png, validate=True))}
        for changed in ({"base64": "!" + self.png[1:]}, {"width": 2}, {"height": 100000},
                        {"media_type": "image/svg+xml"}, {"bytes": 1}):
            with self.subTest(changed=list(changed)):
                invalid = copy.deepcopy(image)
                invalid.update(changed)
                result = self.execute_image(invalid)
                self.assertFalse(result["success"])
                self.assertIn("screenshot_invalid", result["text"])
                self.assertNotIn("image", result)
                self.assertNotIn(self.png, result["text"])
        result = self.execute_image(image)
        self.assertTrue(result["success"], result["text"])
        self.assertEqual(result["image"]["media_type"], "image/png")
        self.assertEqual(base64.b64decode(result["image"]["base64"], validate=True), base64.b64decode(self.png, validate=True))
        self.assertNotIn("base64", result["text"])
        readonly = self.execute_image(image, READ_ONLY)
        self.assertTrue(readonly["success"], readonly["text"])
        self.assertEqual(readonly["image"]["media_type"], "image/png")

    def test_visual_reference_matches_the_image_and_only_public_fields_reach_model_text(self):
        image={'media_type':'image/png','base64':self.png,'bytes':len(base64.b64decode(self.png)),'width':1,'height':1}
        visual={'id':'vs-'+'a'*48,'width':1,'height':1,'coordinates':'image_pixels','expires_in_ms':60000}
        result=self.execute_image(image,visual={**visual,'private_hashes':['must-not-reach-model']})
        self.assertTrue(result['success'],result['text'])
        self.assertEqual(value(result)['visual_reference'],visual)
        self.assertNotIn('private_hashes',result['text']);self.assertNotIn('must-not-reach-model',result['text'])
        for changed in ({'id':'vs-guess'},{'id':'vs-'+'Z'*48},{'width':2},{'height':0},{'coordinates':'css_pixels'},{'expires_in_ms':60001}):
            with self.subTest(changed=changed):
                failed=self.execute_image(image,visual={**visual,**changed})
                self.assertFalse(failed['success']);self.assertIn('screenshot_invalid',failed['text']);self.assertNotIn('image',failed)


if __name__ == "__main__":
    unittest.main()
