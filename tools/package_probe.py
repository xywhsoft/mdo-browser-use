"""Verify local portable import/restart/roundtrip in the one development Home."""
import base64
import hashlib
import json
from pathlib import Path
import sys

import apply_host
import dev
import install
import package_plugin

IDENTITY = "mdo.browser-use"
RESULT = dev.ENV / "package-install-result.json"


def inventory(files):
    return {name: {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
            for name, data in files}


def installed_inventory(item):
    return {row["path"]: {"bytes": row["bytes"], "sha256": row["sha256"]}
            for row in item["files"]}


def plugin_present():
    listing = dev.call("plugin.list")
    return any(row["id"] == IDENTITY for row in listing["items"])


def rejected_import(package, **fields):
    status, _, raw = dev.request("POST", "/api/v1/ecosystem", {
        "action": "import", "single_kind": "plugins", "package": package, **fields})
    error = json.loads(raw).get("error", {})
    if status != 409 or error.get("code") != "ecosystem_import_failed":
        raise RuntimeError("Expected local import refusal was not observed")
    return error["message"]


def restart():
    dev.stop()
    dev.start()


def probe():
    if dev.state().get("window") or dev.request("GET", "/api/v1/bootstrap")[0] != 200:
        raise RuntimeError("Start the same headless development service before this gate")
    apply_host.apply(check=True)
    receipt = json.loads((dev.ENV / "host/browser-use-build.json").read_text())
    if hashlib.sha256(Path(dev.state()["host"]).read_bytes()).hexdigest() != receipt["binary_sha256"]:
        raise RuntimeError("Enhanced native host no longer matches its build receipt")
    name, data, artifact = package_plugin.build()
    package = json.loads(data)
    manifest, files = package_plugin.snapshot()
    expected = inventory(files)
    report = {"state": "running", "artifact": artifact["artifact"], "checks": []}
    def mark(check):
        report["checks"].append(check)
        RESULT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(check, flush=True)

    # One-time migration of our exact old development installation, never an
    # arbitrary plugin with the same ID. Existing revisions remain in Home.
    if plugin_present():
        old = dev.call("plugin.get", {"id": IDENTITY})
        if not old.get("source"):
            previous = installed_inventory(old)
            legacy = {key: value for key, value in expected.items()
                      if key not in {"LICENSE", "docs/distribution.md"}}
            if old["manifest"] != manifest or previous not in (expected, legacy):
                raise RuntimeError("Unexpected development plugin preserved; inspect its revision before migration")
            saved = {"format": "mdo.browser-use.development-migration.v1", "original": old,
                     "artifact_sha256": artifact["artifact"]["sha256"]}
            backup = dev.ENV / "package-development-migration.json"
            if not backup.exists():
                backup.write_text(json.dumps(saved, indent=2) + "\n", encoding="utf-8")
            elif json.loads(backup.read_text()).get("original", {}).get("revision") != old["revision"]:
                raise RuntimeError("A different previous migration receipt is retained")
            if dev.call("plugin.get", {"id": IDENTITY}) != old:
                raise RuntimeError("Development plugin changed before migration")
            dev.call("plugin.remove", {"id": IDENTITY})
            mark("known_development_revision_unregistered_retained_on_disk")

    plan = install.ecosystem("plan_import", package=package)
    if not plan.get("can_install") or len(plan.get("sha256", "")) != 64:
        raise RuntimeError("Real native importer did not accept the portable package")
    mark("native_portable_format_plan")
    before = dev.call("plugin.list")
    rejected_import(package, trust_code=True, expected_plan="0" * 64)
    if dev.call("plugin.list") != before:
        raise RuntimeError("Rejected stale plan changed plugin configuration")
    mark("stale_plan_rejected_without_configuration_change")
    message = rejected_import(package, expected_plan=plan["sha256"])
    if "approval" not in message.lower() or dev.call("plugin.list") != before:
        raise RuntimeError("Explicit C source trust was not enforced")
    mark("untrusted_code_rejected_without_configuration_change")
    install.install(package)
    mark("local_import_enabled")
    restart()
    current = dev.call("plugin.get", {"id": IDENTITY})
    if not current["enabled"] or current["manifest"] != manifest or installed_inventory(current) != expected:
        raise RuntimeError("Restarted plugin does not match the portable inventory")
    mark("same_home_restart_exact_inventory")
    for row in json.loads((dev.ROOT / "contracts.json").read_text())["services"]:
        described = dev.call("sdk.describe", {"id": row["id"]})
        if described["input_schema"] != row["input"] or described["output_schema"] != row["output"]:
            raise RuntimeError("Installed C service differs from shared contract: " + row["id"])
    dev.call("browseruse.tabs", {"action": "list"})
    mark("installed_c_services_match_shared_contracts")
    exported = install.ecosystem("export", references=[{"kind": "plugins", "id": IDENTITY}])["resources"]
    if len(exported) != 1 or json.loads(exported[0]["content"]) != manifest:
        raise RuntimeError("Native export changed the plugin descriptor")
    exported_files = [(row["path"], base64.b64decode(row["base64"], validate=True))
                      for row in exported[0]["files"]]
    if inventory(exported_files) != expected:
        raise RuntimeError("Native import/export roundtrip changed payload bytes")
    mark("native_export_all_decoded_bytes_match")
    # Reinstall the same portable revision through the existing receipt rather
    # than bypassing ecosystem ownership with plugin.apply.
    revision = current["revision"]
    install.install(package)
    if dev.call("plugin.get", {"id": IDENTITY})["revision"] != revision:
        raise RuntimeError("Identical package unexpectedly changed its revision")
    mark("same_package_reinstall_uses_existing_receipt")
    report.update(state="passed", plugin_revision=revision, services=len(json.loads(
        (dev.ROOT / "contracts.json").read_text())["services"]), files=len(files),
        external_calls=0, existing_home_reused=True)
    RESULT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"state": report["state"], "checks": len(report["checks"]),
                      "artifact": name, "files": len(files)}, indent=2))


if __name__ == "__main__":
    try:
        probe()
    except Exception as error:
        if RESULT.exists():
            report = json.loads(RESULT.read_text())
            report.update(state="failed", error=str(error))
            RESULT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(error, file=sys.stderr)
        raise SystemExit(1)
