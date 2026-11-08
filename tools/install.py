"""Install/update this plugin in the single project mdo deployment."""
import json
import hashlib
import sys

import dev
from package_plugin import build_directory

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
    if package is not None:
        raise ValueError("Use a complete directory ZIP; legacy JSON packages are retired")
    name, data, receipt = build_directory()
    def request(method, path, body=None, headers=None):
        status, _, raw = dev.request(method, path, body, headers=headers)
        value = json.loads(raw)
        if status != 200 or not value.get("ok"):
            raise RuntimeError("Directory install failed: HTTP " + str(status) + " " + value.get("error", {}).get("code", "failed") + ": " + value.get("error", {}).get("message", ""))
        return value["data"]
    task = request("POST", "/api/v1/extension-tasks", {
        "kind": "plugins", "purpose": "install", "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    path = "/api/v1/extension-tasks/" + task["id"]
    try:
        for at in range(0, len(data), 1048576):
            request("PUT", path + "/chunks/" + str(at), data[at:at + 1048576],
                    {"Content-Type": "application/octet-stream"})
        request("POST", path + "/seal", {})
        status, _, raw = dev.request("GET", "/api/v1/extensions/plugins/mdo.browser-use")
        previous = json.loads(raw).get("data", {})
        if status not in (200, 404) or (status == 200 and previous.get("id") not in ("", "plugin:mdo.browser-use")):
            raise RuntimeError("An unrelated extension identity is preserved")
        request("POST", path + "/install", {"id": "mdo.browser-use", "trust_code": True, "enabled": False,
                                             "expected_revision": previous.get("revision", "")})
    finally:
        request("DELETE", path)
    identity = "plugin:mdo.browser-use"
    result = enable(identity)
    print(json.dumps({"plugin": identity, "files": len(receipt["files"]),
                      "pending": result.get("pending", False),
                      "restart_required": result.get("restart_required", False)}, indent=2))
    return result


if __name__ == "__main__":
    try:
        install()
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
