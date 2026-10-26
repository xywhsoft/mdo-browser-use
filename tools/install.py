"""Install/update this plugin in the single project mdo deployment."""
import json
from pathlib import Path
import sys

import dev

ROOT = Path(__file__).resolve().parents[1]


def install():
    manifest = json.loads((ROOT / "plugin.json").read_text(encoding="utf-8"))
    files = []
    for directory in ("runtime", "sdk", "overlay", "page"):
        for path in sorted((ROOT / directory).rglob("*")):
            if path.is_file():
                files.append({"path": path.relative_to(ROOT).as_posix(),
                              "text": path.read_text(encoding="utf-8")})
    dev.call("plugin.apply", {"id": manifest["id"], "manifest": manifest, "files": files})
    changes = [{"id": manifest["id"], "enabled": True}]
    plan = dev.call("plugin.plan", {"changes": changes})
    if not plan["allowed"]:
        raise RuntimeError("Plugin plan rejected: " + json.dumps(plan.get("issues")))
    result = dev.call("plugin.commit", {"changes": changes,
                                       "expected_revision": plan["base_revision"],
                                       "expected_plan": plan["plan_revision"]})
    print(json.dumps({"plugin": manifest["id"], "files": len(files),
                      "pending": result.get("pending", False),
                      "restart_required": result.get("restart_required", False)}, indent=2))


if __name__ == "__main__":
    try:
        install()
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
