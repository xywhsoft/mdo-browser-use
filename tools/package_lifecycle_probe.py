"""Uninstall/reinstall and recover interrupted import in the same project Home."""
import base64
import hashlib
import json
from pathlib import Path
import sys

import dev
import install
import package_plugin
from package_probe import inventory, installed_inventory, plugin_present, restart

ID = "mdo.browser-use"
RESULT = dev.ENV / "package-lifecycle-result.json"
SCOPE = {"extension": "package-lifecycle-qa", "project": "default", "session": "package-lifecycle"}
BINARY = bytes(range(256)) * 65


def atomic(path, data):
    temporary = path.with_name(path.name + ".browser-use-qa.tmp")
    if temporary.exists():
        raise RuntimeError("Previous package fault temporary file retained")
    with temporary.open("xb") as output:
        output.write(data)
    temporary.replace(path)


def receipts():
    status, _, raw = dev.request("GET", "/api/v1/ecosystem")
    if status != 200:
        raise RuntimeError("Cannot read local installation receipts")
    return json.loads(raw)["data"]


def main():
    if dev.state().get("window") or dev.request("GET", "/api/v1/bootstrap")[0] != 200:
        raise RuntimeError("Use the same running headless development service")
    package = json.loads(package_plugin.build()[1])
    manifest, source = package_plugin.snapshot()
    expected = inventory(source)
    report = {"state": "running", "same_home": True, "checks": []}
    def mark(check):
        report["checks"].append(check)
        RESULT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(check, flush=True)
    browser = None
    try:
        if not plugin_present():
            install.install(package)
            restart()
        current = dev.call("plugin.get", {"id": ID})
        if current["manifest"] != manifest or installed_inventory(current) != expected or current.get("source") != "local":
            raise RuntimeError("Unexpected installed revision preserved; inspect before the lifecycle gate")
        before = receipts()
        backup = dev.ENV / "package-lifecycle-before.json"
        if not backup.exists():
            backup.write_text(json.dumps({"plugin": current, "receipts": before}, indent=2) + "\n", encoding="utf-8")
        dev.call("client.hello", {"client": "package-lifecycle-window", "context": {}})
        browser = dev.call("browseruse.tabs", {"action": "create", "client": "package-lifecycle-window",
            "key": "package-lifecycle"}, SCOPE)["id"]
        def verify_loaded(wanted):
            item = dev.call("plugin.get", {"id": ID})
            if not item["enabled"] or item["manifest"] != manifest or installed_inventory(item) != wanted:
                raise RuntimeError("Reloaded portable plugin differs from the expected inventory")
            if dev.call("browser.get", {"id": browser}, SCOPE)["id"] != browser:
                raise RuntimeError("Uninstall/reinstall lost the existing browser record")
            return item
        def cycle(candidate, wanted, binary=False):
            item = verify_loaded(wanted)
            relative = f"plugins/{ID}/revisions/{item['revision']}/files/"
            retained = [(dev.HOME / relative / row["path"], row["sha256"]) for row in item["files"]]
            install.ecosystem("uninstall", entry={"source": "local", "slug": ID})
            listing = dev.call("plugin.list")
            if plugin_present() or not listing["restart_required"]:
                raise RuntimeError("Native plugin removal did not request a restart")
            if "local:" + ID in receipts():
                raise RuntimeError("Uninstalled package still has an installation receipt")
            if any(hashlib.sha256(path.read_bytes()).hexdigest() != digest for path, digest in retained):
                raise RuntimeError("Uninstall removed a retained immutable plugin generation")
            mark("binary_uninstall_retains_generation" if binary else "uninstall_retains_generation_and_requests_restart")
            restart()
            services = {row["id"] for row in dev.call("sdk.services")}
            if any(name.startswith("browseruse.") for name in services):
                raise RuntimeError("Uninstalled C services remain registered after restart")
            dev.call("browser.get", {"id": browser}, SCOPE)
            mark("binary_uninstalled_services_retired" if binary else "uninstalled_services_retired_browser_data_preserved")
            if binary:
                target = dev.HOME / relative / "qa/binary-fixture.bin"
                if target.read_bytes() != BINARY:
                    raise RuntimeError("Binary fixture changed before conflict verification")
                corrupt = b"!" + BINARY[1:]
                atomic(target, corrupt)
                try:
                    status, _, raw = dev.request("POST", "/api/v1/ecosystem", {
                        "action": "plan_import", "single_kind": "plugins", "package": candidate})
                    if status != 409 or target.read_bytes() != corrupt or plugin_present():
                        raise RuntimeError("Changed retained file was not preserved by rejected import")
                    if json.loads(raw).get("error", {}).get("code") != "ecosystem_import_failed":
                        raise RuntimeError("Changed retained file refusal has the wrong error")
                    mark("changed_retained_binary_rejected_without_overwrite")
                finally:
                    if target.read_bytes() != corrupt:
                        raise RuntimeError("Binary fixture changed during cleanup; preserve it")
                    atomic(target, BINARY)
            install.install(candidate)
            restart()
            verify_loaded(wanted)
            mark("binary_same_revision_reimport" if binary else "same_revision_reimport_from_retained_files")
        cycle(package, expected)
        binary_package = json.loads(json.dumps(package))
        binary_package["resources"][0]["files"].append({"path": "qa/binary-fixture.bin",
            "base64": base64.b64encode(BINARY).decode()})
        binary_expected = dict(expected, **{"qa/binary-fixture.bin": {
            "bytes": len(BINARY), "sha256": hashlib.sha256(BINARY).hexdigest()}})
        install.install(binary_package)
        restart()
        cycle(binary_package, binary_expected, binary=True)
        install.install(package)
        restart()
        current = verify_loaded(expected)
        mark("original_portable_package_restored")
        # Synthesize one documented interrupted transaction, not a process
        # crash guessed from a timeout. The actual startup recovery runs next.
        relative = f"plugins/{ID}/revisions/{current['revision']}/files/LICENSE"
        target = dev.HOME / relative
        original = target.read_bytes()
        before = receipts()
        pending = dev.HOME / "data/extensions/pending.json"
        installed = dev.HOME / "data/extensions/installed.json"
        if pending.exists():
            raise RuntimeError("An existing ecosystem recovery journal is retained")
        altered = original + b"\nsynthetic interrupted package transaction\n"
        journal = {"files": [{"path": relative, "old": base64.b64encode(original).decode(),
            "new": base64.b64encode(altered).decode(), "sha256": hashlib.sha256(altered).hexdigest()}], "receipts": before}
        dev.stop()
        atomic(pending, json.dumps(journal, separators=(",", ":")).encode())
        atomic(target, altered)
        atomic(installed, b"{}")
        dev.start()
        if pending.exists() or target.read_bytes() != original or receipts() != before:
            raise RuntimeError("Actual startup did not restore the interrupted package transaction")
        verify_loaded(expected)
        mark("actual_startup_restores_partial_payload_and_receipts")
        restart()
        if pending.exists() or target.read_bytes() != original or receipts() != before:
            raise RuntimeError("Second startup changed recovered package state")
        verify_loaded(expected)
        mark("recovery_is_idempotent_on_second_startup")
        report["state"] = "passed"
    except Exception as error:
        report.update(state="failed", error=str(error))
        raise
    finally:
        if browser and dev.state().get("pid"):
            try:
                dev.call("browser.close", {"id": browser}, SCOPE)
                report["fixture_browser_closed"] = True
            except (OSError, RuntimeError):
                report["fixture_browser_closed"] = False
        RESULT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"state": report["state"], "checks": len(report["checks"])}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
