"""Host reuse/seed trust guards; no application, Home or dependency fixture copy."""
import hashlib
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import dev


class DevSetupTests(unittest.TestCase):
    def fixture(self, exists, data=b"reviewed host"):
        host, base, env = (MagicMock(spec=Path) for _ in range(3))
        host.exists.return_value = exists
        host.read_bytes.return_value = data
        bootstrap = base.__truediv__.return_value
        bootstrap.read_bytes.return_value = b"pinned bootstrap"
        receipt = env.__truediv__.return_value
        receipt.exists.return_value = True
        receipt.read_text.return_value = json.dumps({"binary_sha256": hashlib.sha256(data).hexdigest()})
        return host, base, env, bootstrap, receipt

    def test_reviewed_local_host_does_not_depend_on_a_changed_or_missing_external_bootstrap(self):
        host, base, env, _, _ = self.fixture(True)
        base.__truediv__.side_effect = AssertionError("Do not inspect an external build when reusing a reviewed local host")
        with patch.object(dev, "ENV", env), patch.object(dev.shutil, "copy2") as copy:
            dev.prepare_host(base, host)
            copy.assert_not_called()
        host.parent.mkdir.assert_not_called()

    def test_existing_pinned_bootstrap_needs_neither_external_build_nor_overlay_receipt(self):
        host, base, env, _, _ = self.fixture(True, b"pinned bootstrap")
        with patch.dict(dev.LOCK, bootstrap_host_sha256=hashlib.sha256(b"pinned bootstrap").hexdigest()), \
             patch.object(dev, "ENV", env), patch.object(dev.shutil, "copy2") as copy:
            dev.prepare_host(base, host)
            copy.assert_not_called()
        env.__truediv__.assert_not_called()
        base.__truediv__.assert_not_called()

    def test_unknown_or_unreceipted_local_host_is_preserved_before_any_external_read(self):
        for missing in (False, True):
            host, base, env, _, receipt = self.fixture(True)
            receipt.exists.return_value = not missing
            receipt.read_text.return_value = json.dumps({"binary_sha256": "foreign"})
            with patch.object(dev, "ENV", env), patch.object(dev.shutil, "copy2") as copy:
                with self.assertRaisesRegex(RuntimeError, "Unrecognized project host preserved"):
                    dev.prepare_host(base, host)
                copy.assert_not_called()
            base.__truediv__.assert_not_called()
            host.parent.mkdir.assert_not_called()

    def test_new_host_can_only_be_seeded_from_the_pinned_original(self):
        host, base, env, bootstrap, _ = self.fixture(False)
        with patch.dict(dev.LOCK, bootstrap_host_sha256=hashlib.sha256(b"pinned bootstrap").hexdigest()), \
             patch.object(dev, "ENV", env), patch.object(dev.shutil, "copy2") as copy:
            dev.prepare_host(base, host)
            copy.assert_called_once_with(bootstrap, host)
        host.parent.mkdir.assert_called_once_with(exist_ok=True)
        host.read_bytes.assert_not_called()

    def test_new_host_rejects_a_changed_external_build_without_copying_or_overwriting(self):
        host, base, env, _, _ = self.fixture(False)
        with patch.object(dev, "ENV", env), patch.object(dev.shutil, "copy2") as copy:
            with self.assertRaisesRegex(RuntimeError, "Bootstrap host hash changed"):
                dev.prepare_host(base, host)
            copy.assert_not_called()
        host.parent.mkdir.assert_not_called()


if __name__ == "__main__":
    unittest.main()
