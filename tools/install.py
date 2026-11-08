"""Install/update this plugin in the single project mdo deployment."""
import json
import hashlib
import sys

import dev
from package_plugin import build_directory, build_skill_directory

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


def install_directory(kind, identity, package):
    if (kind, identity) not in {("plugins", "mdo.browser-use"), ("skills", "browser-use")}:
        raise ValueError("Only the two owned Browser Use directories may be installed")
    name, data, receipt = package
    def request(method, path, body=None, headers=None):
        status, _, raw = dev.request(method, path, body, headers=headers)
        value = json.loads(raw)
        if status != 200 or not value.get("ok"):
            raise RuntimeError("Directory install failed: HTTP " + str(status) + " " + value.get("error", {}).get("code", "failed") + ": " + value.get("error", {}).get("message", ""))
        return value["data"]
    task = request("POST", "/api/v1/extension-tasks", {
        "kind": kind, "purpose": "install", "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    path = "/api/v1/extension-tasks/" + task["id"]
    try:
        for at in range(0, len(data), 1048576):
            request("PUT", path + "/chunks/" + str(at), data[at:at + 1048576],
                    {"Content-Type": "application/octet-stream"})
        request("POST", path + "/seal", {})
        status, _, raw = dev.request("GET", "/api/v1/extensions/" + kind + "/" + identity)
        previous = json.loads(raw).get("data", {})
        logical = "plugin:mdo.browser-use" if kind == "plugins" else "skill:mdo.browser-use"
        if status not in (200, 404) or (status == 200 and previous.get("id") not in (("", logical) if kind == "plugins" else (logical,))):
            raise RuntimeError("An unrelated extension identity is preserved")
        request("POST", path + "/install", {"id": identity, "trust_code": True, "enabled": kind != "plugins",
                                             "expected_revision": previous.get("revision", "")})
    finally:
        request("DELETE", path)
    return receipt


def install(package=None):
    if package is not None:
        raise ValueError("Use complete directory ZIPs; legacy JSON packages are retired")
    install_directory("skills", "browser-use", build_skill_directory())
    receipt = install_directory("plugins", "mdo.browser-use", build_directory())
    result = enable("plugin:mdo.browser-use")
    print(json.dumps({"plugin": "plugin:mdo.browser-use", "files": len(receipt["files"]),
                      "pending": result.get("pending", False),
                      "restart_required": result.get("restart_required", False)}, indent=2))
    return result


if __name__ == "__main__":
    try:
        install()
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
