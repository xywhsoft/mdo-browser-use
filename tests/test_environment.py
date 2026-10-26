"""Probe the real, single project deployment without copying the application."""
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("browser_use_dev", ROOT / "tools/dev.py")
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)


class EnvironmentTests(unittest.TestCase):
    def test_actual_tcc_product_and_sdk_are_available(self):
        status, _, body = dev.request("GET", "/api/v1/bootstrap")
        self.assertEqual(status, 200)
        self.assertIsInstance(json.loads(body), dict)
        services = dev.call("sdk.services")
        names = {row["id"] for row in services}
        for required in ("plugin.apply", "plugin.list", "browser.create", "browser.command",
                         "browser.result", "sdk.describe", "operation.start"):
            self.assertIn(required, names)
        description = dev.call("sdk.describe", {"id": "browser.command"})
        self.assertEqual(description["id"], "browser.command")
        self.assertTrue(dev.call("runtime.info")["application_root"])
        self.assertIsInstance(dev.call("plugin.list")["items"], list)

    def test_deployment_uses_pinned_source_and_this_project_home(self):
        current = dev.state()
        self.assertEqual(Path(current["home"]).resolve(), ROOT / ".mdo/home")
        revision = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=dev.SOURCE, text=True).strip()
        self.assertEqual(revision, dev.LOCK["commit"])
        self.assertEqual(Path(current["config"]).parent, dev.SOURCE / "app")
        config = json.loads(Path(current["config"]).read_text(encoding="utf-8"))
        self.assertEqual(config["engine"]["workers"], 1)
        self.assertEqual(config["services"][0]["ip"], "127.0.0.1")
        self.assertEqual(len(config["services"]), 1)


if __name__ == "__main__":
    unittest.main()
