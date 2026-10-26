"""Apply reviewed enhancements only to the one pinned project mdo checkout."""
import json
from pathlib import Path
import sys

import dev


def apply():
    if dev.state().get("pid"):
        raise RuntimeError("Stop the project development service before applying host changes")
    if dev.run("git", "rev-parse", "HEAD", cwd=dev.SOURCE) != dev.LOCK["commit"]:
        raise RuntimeError("Host source identity does not match the pinned baseline")
    spec = json.loads((dev.ROOT / "host/overlays.json").read_text(encoding="utf-8"))
    staged = {}
    for change in spec["changes"]:
        path = (dev.SOURCE / change["path"]).resolve()
        if not path.is_relative_to(dev.SOURCE):
            raise RuntimeError("Overlay path leaves the project mdo source")
        current = staged.get(path, path.read_text(encoding="utf-8"))
        old, new = change["before"], change["after"]
        if current.count(new) == 1:
            continue
        if current.count(old) != 1:
            raise RuntimeError("Host overlay precondition changed: " + change["path"])
        staged[path] = current.replace(old, new, 1)
    # Check every precondition before writing any of the reviewed changes.
    for path, text in staged.items():
        path.write_text(text, encoding="utf-8", newline="\n")
    for row in spec["files"]:
        source = (dev.ROOT / row["source"]).resolve()
        target = (dev.SOURCE / row["target"]).resolve()
        if not source.is_relative_to(dev.ROOT) or not target.is_relative_to(dev.SOURCE):
            raise RuntimeError("Overlay file leaves the project")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
    code = (
        "import sys; from pathlib import Path; "
        "sys.path.insert(0, str(Path(sys.argv[1])/'tools')); "
        "import build_mdo; build_mdo.prepare(build_mdo.load_object(build_mdo.LOCK_PATH))"
    )
    dev.run(sys.executable, "-c", code, str(dev.SOURCE))
    print("Reviewed host overlays applied to the single project deployment")


if __name__ == "__main__":
    try:
        apply()
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
