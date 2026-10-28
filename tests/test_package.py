"""Portable artifact integrity, disclosure boundaries and source completeness."""
import base64
import hashlib
import json
from pathlib import Path
import stat
import sys
from types import SimpleNamespace
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import package_plugin as package


class PackageTests(unittest.TestCase):
    def test_artifact_is_self_consistent_and_contains_only_reviewed_payload(self):
        name, data, receipt = package.build()
        value = json.loads(data)
        self.assertEqual(value["format"], "mdo.extension.v2")
        self.assertEqual(value["manifest"]["platforms"], ["windows-x86_64"])
        self.assertIn("Stock mdo 0.7.0", value["manifest"]["readme"])
        self.assertIn("incomplete", value["manifest"]["readme"])
        self.assertEqual(len(value["resources"]), 1)
        resource = value["resources"][0]
        files = {row["path"]: base64.b64decode(row["base64"], validate=True)
                 for row in resource["files"]}
        self.assertEqual(set(files), set(json.loads((package.ROOT / "package-files.json").read_text())))
        self.assertEqual(receipt["artifact"], {"name": name, "bytes": len(data),
                         "sha256": hashlib.sha256(data).hexdigest()})
        self.assertEqual(receipt["plugin"]["decoded_bytes"], sum(map(len, files.values())))
        for row in receipt["files"]:
            self.assertEqual(row["bytes"], len(files[row["path"]]))
            self.assertEqual(row["sha256"], hashlib.sha256(files[row["path"]]).hexdigest())
        self.assertIn("MIT License", files["LICENSE"].decode())
        descriptor = json.loads(resource["content"])
        self.assertEqual(descriptor["version"], value["manifest"]["version"])
        self.assertIn(descriptor["entry"]["native"], files)
        # All current runtime/SDK inputs must be listed; don't silently ship a
        # partial C include closure when a future source file is introduced.
        for root in ("runtime", "sdk", "page", "overlay"):
            for source in (package.ROOT / root).rglob("*"):
                if source.is_file() and source.suffix in {".c", ".h", ".js", ".mjs", ".md"}:
                    self.assertIn(source.relative_to(package.ROOT).as_posix(), files)

    def test_reordered_allowlist_and_crlf_have_identical_artifact_bytes(self):
        expected = package.build()
        original = package.read_text
        def reordered(root, path):
            content = original(root, path)
            if path == "package-files.json":
                return json.dumps(list(reversed(json.loads(content)))).encode()
            return content
        with mock.patch.object(package, "read_text", side_effect=reordered):
            self.assertEqual(package.build(), expected)
        source = "alpha\r\nbeta\r\n".encode()
        with mock.patch.object(Path, "read_bytes", return_value=source):
            self.assertEqual(original(package.ROOT, "LICENSE"), b"alpha\nbeta\n")

    def test_allowlist_cannot_disclose_dev_data_or_alias_other_sources(self):
        original = package.read_text
        for bad in (".mdo/home/config/account.json", "runtime/.env", "runtime/private.exe",
                    "runtime/../LICENSE", "runtime//plugin.c", "runtime/CON.c", "/runtime/plugin.c",
                    "runtime/aux.c", "runtime/plugin.c.", "runtime\\plugin.c"):
            reads = []
            def source(root, path):
                reads.append(path)
                return json.dumps([bad]).encode() if path == "package-files.json" else original(root, path)
            with self.subTest(path=bad), mock.patch.object(package, "read_text", side_effect=source):
                with self.assertRaises((ValueError, OSError)):
                    package.snapshot()
                if bad.startswith(".mdo/"):
                    self.assertEqual(reads, ["package-files.json"])
        names = json.loads(original(package.ROOT, "package-files.json"))
        with mock.patch.object(package, "read_text", side_effect=lambda root, path:
                               json.dumps(names + ["RUNTIME/plugin.c"]).encode()
                               if path == "package-files.json" else original(root, path)):
            with self.assertRaisesRegex(ValueError, "duplicate"):
                package.snapshot()

    def test_missing_entry_reparse_and_invalid_text_abort_before_output(self):
        original = package.read_text
        def omitted(root, path):
            content = original(root, path)
            return json.dumps([name for name in json.loads(content) if name != "runtime/plugin.c"]).encode() if path == "package-files.json" else content
        with mock.patch.object(package, "read_text", side_effect=omitted):
            with self.assertRaisesRegex(ValueError, "entry"):
                package.build()
        info = SimpleNamespace(st_mode=stat.S_IFREG, st_size=0, st_file_attributes=0x400)
        with mock.patch.object(Path, "lstat", return_value=info), mock.patch.object(Path, "read_bytes") as read:
            with self.assertRaisesRegex(ValueError, "reparse"):
                package.read_text(package.ROOT, "LICENSE")
            read.assert_not_called()
        for invalid in (b"nul\0tail", b"lone\rreturn", b"\xff"):
            with mock.patch.object(Path, "read_bytes", return_value=invalid):
                with self.assertRaises(ValueError):
                    package.read_text(package.ROOT, "LICENSE")


if __name__ == "__main__":
    unittest.main()
