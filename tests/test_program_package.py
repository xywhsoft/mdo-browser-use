"""Interrupted in-place packaging must preserve changed inputs and recover."""
import hashlib
import json
from pathlib import Path
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import dev
import package_program as pack


class ProgramPackagingTests(unittest.TestCase):
    def test_restore_preflights_all_destinations_and_is_repeatable(self):
        # Three synthetic files in the reusable QA directory, not an app/Home
        # copy. No real app state is touched by this injected interruption.
        root = dev.ENV / "qa/program-package/fault-fixture"
        app, work = root / "inputs", root / "journal"
        (app / "generated").mkdir(parents=True, exist_ok=True)
        (work / "excluded/web").mkdir(parents=True, exist_ok=True)
        (app / "web").mkdir(parents=True, exist_ok=True)
        original, packed = b"synthetic original", b"synthetic packed"
        unity = app / pack.UNITY
        unity.write_bytes(packed)
        (work / "unity.original.c").write_bytes(original)
        rows = []
        for name in ("first.html", "second.html"):
            data = name.encode()
            source = work / "excluded/web" / name
            source.write_bytes(data)
            target = app / "web" / name
            if target.exists():
                target.unlink()  # Only the fixed previous synthetic fixture.
            rows.append({"path": "web/" + name, "sha256": hashlib.sha256(data).hexdigest()})
        state = root / "state.json"
        state.write_text(json.dumps({"phase": "packing", "excluded": rows,
            "unity_before": hashlib.sha256(original).hexdigest(),
            "unity_packed": hashlib.sha256(packed).hexdigest()}), encoding="utf-8")
        conflict = app / "web/second.html"
        conflict.write_bytes(b"new unrelated contents")
        with mock.patch.multiple(pack, APP=app, WORK=work, STATE=state):
            with self.assertRaisesRegex(RuntimeError, "changed"):
                pack.restore()
            self.assertEqual(unity.read_bytes(), packed)
            self.assertFalse((app / "web/first.html").exists())
            self.assertEqual(conflict.read_bytes(), b"new unrelated contents")
            self.assertTrue((work / "excluded/web/first.html").exists())
            conflict.unlink()
            pack.restore()
            self.assertEqual(unity.read_bytes(), original)
            for row in rows:
                self.assertEqual(pack.sha(app / row["path"]), row["sha256"])
            self.assertEqual(json.loads(state.read_text(encoding="utf-8"))["phase"], "restored")
            pack.restore()
            self.assertEqual(unity.read_bytes(), original)

    def test_journal_paths_cannot_leave_owned_roots(self):
        for value in ("../outside", "/outside", "C:/outside", "web/../../outside", "web\\outside", "web//outside"):
            with self.subTest(path=value), self.assertRaises(ValueError):
                pack.path_at(dev.ENV / "qa/program-package", value)


if __name__ == "__main__":
    unittest.main()
