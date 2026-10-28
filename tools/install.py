"""Install/update this plugin in the single project mdo deployment."""
import json
import sys

import dev
from package_plugin import build

def ecosystem(action, **fields):
    # Only the local ecosystem authoring/import/export paths are used here.
    if action not in {"plan_import", "import", "export", "uninstall"}:
        raise ValueError("Not a local package action")
    status, _, raw = dev.request("POST", "/api/v1/ecosystem",
                                 {"action": action, "single_kind": "plugins", **fields})
    result = json.loads(raw)
    if status != 200:
        error = result.get("error", {})
        raise RuntimeError(f"Local package {action}: HTTP {status}: {error.get('code')}: {error.get('message')}")
    return result["data"]


def enable(identity):
    changes = [{"id": identity, "enabled": True}]
    plan = dev.call("plugin.plan", {"changes": changes})
    if not plan["allowed"]:
        raise RuntimeError("Plugin plan rejected: " + json.dumps(plan.get("issues")))
    result = dev.call("plugin.commit", {"changes": changes,
                                       "expected_revision": plan["base_revision"],
                                       "expected_plan": plan["plan_revision"]})
    return result


def install(package=None):
    if package is None:
        package = json.loads(build()[1])
    plan = ecosystem("plan_import", package=package)
    if not plan.get("can_install"):
        raise RuntimeError("Plugin import plan contains unresolved dependencies or conflicts")
    ecosystem("import", package=package, trust_code=True, expected_plan=plan["sha256"])
    identity = package["resources"][0]["id"]
    result = enable(identity)
    print(json.dumps({"plugin": identity, "files": len(package["resources"][0]["files"]),
                      "pending": result.get("pending", False),
                      "restart_required": result.get("restart_required", False)}, indent=2))
    return result


if __name__ == "__main__":
    try:
        install()
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
