"""Apply reviewed enhancements only to the one pinned project mdo checkout."""
import json
import argparse
import hashlib
from pathlib import Path
import subprocess
import sys

import dev


def digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def apply(check=False):
    if dev.state().get("pid") and not check:
        raise RuntimeError("Stop the project development service before applying host changes")
    if dev.run("git", "rev-parse", "HEAD", cwd=dev.SOURCE) != dev.LOCK["commit"]:
        raise RuntimeError("Host source identity does not match the pinned baseline")
    spec = json.loads((dev.ROOT / "host/overlays.json").read_text(encoding="utf-8"))
    receipt_path = dev.ENV / "host-overlays.json"
    receipt = json.loads(receipt_path.read_text(encoding="utf-8")) if receipt_path.exists() else {}
    previous = receipt.get("files", {}) if receipt.get("baseline") == dev.LOCK["commit"] else {}
    staged = {}
    for relative in dict.fromkeys(row["path"] for row in spec["changes"]):
        path = (dev.SOURCE / relative).resolve()
        if not path.is_relative_to(dev.SOURCE):
            raise RuntimeError("Overlay path leaves the project mdo source")
        baseline = subprocess.check_output(
            ["git", "show", dev.LOCK["commit"] + ":" + relative], cwd=dev.SOURCE).decode("utf-8").replace("\r\n", "\n")
        wanted = baseline
        for change in (row for row in spec["changes"] if row["path"] == relative):
            if wanted.count(change["before"]) != 1:
                raise RuntimeError("Overlay does not match the pinned source: " + relative)
            wanted = wanted.replace(change["before"], change["after"], 1)
        current = path.read_text(encoding="utf-8")
        if current not in (baseline, wanted) and previous.get(relative) != digest(current):
            raise RuntimeError("Unreviewed host edit preserved: " + relative)
        staged[path] = wanted
    for row in spec["files"]:
        source = (dev.ROOT / row["source"]).resolve()
        target = (dev.SOURCE / row["target"]).resolve()
        if not source.is_relative_to(dev.ROOT) or not target.is_relative_to(dev.SOURCE):
            raise RuntimeError("Overlay file leaves the project")
        wanted = source.read_text(encoding="utf-8")
        if target.exists():
            current = target.read_text(encoding="utf-8")
            if current != wanted and previous.get(row["target"]) != digest(current):
                raise RuntimeError("Unreviewed generated host file preserved: " + row["target"])
        staged[target] = wanted
    # Materialize from the pinned baseline each time, never repeatedly patch an
    # already patched text. Previous hashes permit reviewed overlay upgrades,
    # while an unrelated local edit is rejected before any file is written.
    if check:
        for path, wanted in staged.items():
            if not path.exists() or path.read_text(encoding="utf-8") != wanted:
                raise RuntimeError("Host deployment needs updating: " + str(path.relative_to(dev.SOURCE)))
        print("All deployed host enhancements match the reviewed source")
        return
    for path, text in staged.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(path.suffix + ".bu.tmp")
        temporary.write_text(text, encoding="utf-8", newline="\n")
        temporary.replace(path)
    code = (
        "import sys; from pathlib import Path; "
        "sys.path.insert(0, str(Path(sys.argv[1])/'tools')); "
        "import build_mdo; build_mdo.prepare(build_mdo.load_object(build_mdo.LOCK_PATH))"
    )
    dev.run(sys.executable, "-c", code, str(dev.SOURCE))
    receipt_path.write_text(json.dumps({"baseline": dev.LOCK["commit"], "files": {
        path.relative_to(dev.SOURCE).as_posix(): digest(text) for path, text in staged.items()
    }}, indent=2) + "\n", encoding="utf-8")
    print("Reviewed host overlays applied to the single project deployment")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        apply(args.check)
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
