"""Run the Windows integration acceptance flow in the one development deployment.

This is not whole-product/platform qualification: Android/device, account relay
authentication and the rendered permission prompt remain separate evidence.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import sys

import apply_host
import dev

RESULT = dev.ENV / "acceptance-result.json"
WORK = dev.ENV / "qa/acceptance"
CASES = (
    ("contracts", "tools/contracts.py", ("--check",), None),
    ("portable-install", "tools/package_probe.py", (), "package-install-result.json"),
    ("package-recovery", "tools/package_lifecycle_probe.py", (), "package-lifecycle-result.json"),
    ("managed-tools", "tools/managed_probe.py", (), "managed-tools-result.json"),
    ("managed-visual", "tools/visual_probe.py", ("--managed-only",), "managed-visual-result.json"),
    ("public-frames", "tools/public_frames_probe.py", (), "public-frames-result.json"),
    ("capture-privacy", "tools/capture_privacy_public_probe.py", ("--capture-evidence",), "capture-privacy-public-result.json"),
    ("renderer-recovery", "tools/renderer_recovery_probe.py", (), "renderer-recovery-result.json"),
    ("permission-decisions", "tools/ask_permission_probe.py", (), "ask-permission-result.json"),
    ("remote-bridge-build", "tools/build_remote_bridge_probe.py", (), "qa/remote-bridge/build.json"),
    ("remote-browser", "tools/remote_browser_probe.py", (), "remote-browser-result.json"),
    ("restart-recovery", "tools/restart_probe.py", (), "restart-result.json"),
)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def source_inputs():
    paths = [dev.ROOT / name for name in ("contracts.json", "plugin.json", "package-files.json", "mdo.lock.json")]
    for name in ("host", "runtime", "page", "sdk", "overlay", "tools", "tests"):
        paths.extend(p for p in (dev.ROOT / name).rglob("*")
                     if p.is_file() and "__pycache__" not in p.parts and p.suffix != ".pyc")
    require(all(not p.is_symlink() for p in paths), "Acceptance input is a link")
    return {p.relative_to(dev.ROOT).as_posix(): sha(p) for p in sorted(set(paths))}


def runtime_identity():
    state = dev.state()
    require(not state.get("window"), "Finish the active window check before acceptance")
    require(type(state.get("pid")) is int and state["pid"] > 0, "Start the headless development service before acceptance")
    require(Path(state["home"]).resolve() == dev.HOME.resolve(), "The runtime uses a different development Home")
    config = read(Path(state["active_config"]))
    require(config["services"][0]["class"] == "http" and config["services"][0]["enabled"] is True
            and config["services"][0]["port"] == state["port"] and config["services"][0]["ip"] == "127.0.0.1",
            "The runtime config is not the expected headless loopback service")
    pid = int(state["pid"])
    port = int(state["port"])
    query = (f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}'; "
             "$p | Select-Object ExecutablePath,CommandLine | ConvertTo-Json -Compress")
    process = json.loads(subprocess.check_output(["powershell", "-NoProfile", "-Command", query], text=True))
    require(process and Path(process["ExecutablePath"]).resolve() == Path(state["host"]).resolve(),
            "The deployment PID no longer owns the expected host")
    require(state["active_config"] in process["CommandLine"], "The runtime config changed")
    query = (f"@(Get-NetTCPConnection -LocalPort {port} -State Listen -ErrorAction SilentlyContinue | "
             "Select-Object -ExpandProperty OwningProcess) | ConvertTo-Json -Compress")
    owners = json.loads(subprocess.check_output(["powershell", "-NoProfile", "-Command", query], text=True))
    require(pid in (owners if isinstance(owners, list) else [owners]), "Listener belongs to another process")
    build = read(dev.ENV / "host/browser-use-build.json")
    digest = sha(Path(state["host"]))
    require(digest == build["binary_sha256"], "The running host differs from its native build receipt")
    require(dev.request("GET", "/api/v1/bootstrap")[0] == 200, "Development HTTP service is unavailable")
    return {"pid": pid, "port": port, "executable": state["host"], "executable_sha256": digest,
            "config": state["active_config"], "config_sha256": sha(Path(state["active_config"])),
            "home": str(dev.HOME), "listener_matched": True, "http_status": 200, "headless": True}


def deployed_inputs():
    apply_host.apply(check=True)
    overlay = read(dev.ENV / "host-overlays.json")
    require(overlay["baseline"] == dev.LOCK["commit"], "Applied source revision changed")
    for name, digest in overlay["files"].items():
        require(sha(dev.SOURCE / name) == digest, "Applied host file changed: " + name)
    return dict(overlay["files"])


def validate(name, receipt):
    terminal = receipt.get("state")
    require((receipt.get("passed") is True and terminal in (None, "passed")) or
            ("passed" not in receipt and terminal == "passed"), name + " did not pass")
    if name == "portable-install":
        require(receipt["services"] == 13 and receipt["existing_home_reused"] and receipt["external_calls"] == 0,
                "Portable installation did not verify the shared C services in this Home")
        require({"stale_plan_rejected_without_configuration_change", "untrusted_code_rejected_without_configuration_change",
                 "native_export_all_decoded_bytes_match", "same_package_reinstall_uses_existing_receipt"}
                <= set(receipt["checks"]), "Portable installation lacks trust/roundtrip/idempotence evidence")
    elif name == "package-recovery":
        require(receipt["same_home"] and receipt["fixture_browser_closed"], "Package lifecycle lost browser/Home cleanup")
        require({"uninstalled_services_retired_browser_data_preserved", "changed_retained_binary_rejected_without_overwrite",
                 "original_portable_package_restored", "actual_startup_restores_partial_payload_and_receipts",
                 "recovery_is_idempotent_on_second_startup"} <= set(receipt["checks"]), "Package recovery evidence is incomplete")
    elif name == "managed-tools":
        require(receipt["registered_tools"] == 16, "Managed tool registration is incomplete")
        for key in ("readonly_read_allowed", "readonly_write_denied", "full_access_without_extra_prompt",
                    "tool_failure_then_recovery", "native_dialog_checkpoint_and_response", "readonly_dialog_response_denied",
                    "managed_popup_ownership", "native_screenshot_image", "exact_selection_and_discovery",
                    "readonly_select_denied", "native_drag"):
            require(receipt.get(key) is True, "Missing managed evidence: " + key)
    elif name == "managed-visual":
        require(receipt["scope"] == "managed-visual-permissions-only" and receipt["model_calls"] == 0,
                "Unexpected managed visual scope")
        for key in ("managed_visual_click", "managed_visual_drag", "owned_readonly_image_allowed_point_denied",
                    "owned_readonly_visual_drag_denied", "full_access_no_extra_prompt"):
            require(receipt.get(key) is True, "Missing visual permission evidence: " + key)
    elif name == "permission-decisions":
        require(receipt["headless_restored"] and receipt["model_calls"] == 0 and not receipt["visual_prompt_verified"],
                "Permission API checks cannot qualify the rendered prompt")
        require(type(receipt.get("tool_terminals_verified")) is int and receipt["tool_terminals_verified"] >= 10,
                "Managed permission calls lack their original durable tool terminal pairs")
        require({"cancelled_prompt_cannot_accept_late_grant_and_next_tool_works",
                 "dispatched_cancel_retains_uncertainty_drains_ui_and_next_tool_works",
                 "run_scoped_grant_does_not_leak_to_next_native_drive",
                 "approval_preserves_original_document_and_failure_is_recoverable"} <= set(receipt["cases"]),
                "Permission cancellation/scope/recovery evidence is incomplete")
    elif name == "public-frames":
        require(receipt["native_process_frames"] == 2 and receipt["operations"] >= 26 and receipt["model_calls"] == 0,
                "Native OOPIF integration did not execute its complete workflow")
        for key in ("qualified_references", "nested_unicode_fill", "nested_select", "nested_private_element_image",
                    "workspace_upload_complete_sha256", "frame_picker_upload_cancel", "managed_frame_fill_image",
                    "owned_readonly_frame_fill_denied_image_allowed", "directory_oopif_relative_paths_and_hashes",
                    "directory_oopif_picker", "managed_oopif_directory", "owned_readonly_directory_denied"):
            require(receipt.get(key) is True, "Missing public frame evidence: " + key)
        require(receipt["cache"]["remaining_reservations"] == 0, "Native frame transfers retained reservations")
    elif name == "capture-privacy":
        expected = {(fmt, layout, width, height) for fmt in ("png", "jpeg")
                    for layout, width, height in (("integer", 512, 384), ("fractional", 512, 384), ("fractional", 97, 73))}
        rows = receipt["cases"]
        require(len(rows) == 6 and {(r["public_format"], r["layout"], r["maximum_width"], r["maximum_height"]) for r in rows} == expected,
                "Encoded capture privacy matrix is incomplete")
        require(all(r["capture_mode"] == "element" and r["geometry_matches"] is True and r["encoded_images_equal"] is True and r["redacted_regions"] >= 3 for r in rows)
                and receipt["model_calls"] == receipt["external_sites"] == 0, "Private values affected delivered capture bytes")
    elif name == "renderer-recovery":
        require(receipt["phase"] == "complete" and receipt["manual_reload_recovered"] and receipt["isolated_execution_recovered"]
                and receipt["owned_renderer_stopped"] and receipt["model_calls"] == receipt["external_sites"] == 0,
                "Actual owned renderer failure did not recover after explicit reload")
        require(not receipt["desktop_pixels_verified"], "Renderer protocol recovery does not qualify desktop pixels")
    elif name == "remote-browser":
        require(receipt["headless_restored"] and receipt["model_calls"] == 0 and receipt["post_requests"] == 1,
                "Remote browser replayed the request or failed cleanup")
        require(len(receipt["cases"]) == 8 and receipt["exact_saved_bytes"] == 131075,
                "Remote upload/download/save evidence is incomplete")
        require(not any(receipt[k] for k in ("account_authentication_verified", "physical_devices_verified", "desktop_pixels_verified")),
                "Loopback native bridge does not qualify account relay, two devices or remote desktop pixels")
    elif name == "restart-recovery":
        require(receipt["same_home"] and receipt["admission_preserved"] and receipt["unavailable_result_explicit"]
                and receipt["duplicate_tabs"] == 0, "Restart replayed native tab creation")


def verify_bridge(receipt):
    require(receipt["application_copies"] == 0 and not receipt["account_authentication_verified"],
            "Unexpected remote bridge qualification scope")
    require(sha(dev.ROOT / receipt["binary"]) == receipt["sha256"], "Remote bridge executable changed")
    require(receipt["app_revision"] == dev.LOCK["commit"], "Remote bridge uses a different application revision")
    for name, digest in receipt["sources"].items():
        require(sha(dev.ROOT / name) == digest, "Remote C bridge dependency changed: " + name)


def checked_report():
    report = read(RESULT)
    require(report.get("passed") is True, "The last acceptance run did not pass")
    require(report["source_sha256"] == source_inputs(), "Acceptance inputs changed since the run")
    require(report["deployed_sha256"] == deployed_inputs(), "Applied acceptance inputs changed")
    require([case["name"] for case in report["cases"]] == [case[0] for case in CASES], "Acceptance cases are incomplete")
    for case in report["cases"]:
        require(sha(dev.ROOT / case["log"]) == case["log_sha256"], "Acceptance log changed: " + case["name"])
        if case.get("receipt"):
            path = dev.ROOT / case["receipt"]
            require(sha(path) == case["receipt_sha256"], "Acceptance receipt changed: " + case["name"])
            if case["name"] == "remote-bridge-build":
                verify_bridge(read(path))
            else:
                validate(case["name"], read(path))
    current = runtime_identity()
    for key in ("port", "executable", "executable_sha256", "config", "config_sha256", "home"):
        require(current[key] == report["runtime_after"][key], "The accepted runtime identity changed: " + key)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Revalidate unchanged input/receipt hashes; do not rerun probes")
    args = parser.parse_args()
    require(sys.platform == "win32", "This acceptance command targets the current Windows development deployment")
    if args.check:
        report = checked_report()
        print(json.dumps({"passed": True, "cases": len(report["cases"]), "reused_unchanged_evidence": True}))
        return
    WORK.mkdir(parents=True, exist_ok=True)
    report = {"format": "mdo.browser-use.acceptance.v1", "passed": False, "state": "running",
              "scope": "windows-same-home-plugin-frames-private-capture-managed-permissions-loopback-remote-and-recovery",
              "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=dev.ROOT, text=True).strip(),
              "started_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "source_sha256": source_inputs(), "deployed_sha256": deployed_inputs(), "runtime_before": runtime_identity(),
              "cases": [], "application_copies": 0, "model_calls": 0,
              "android_device_qualified": False, "account_relay_qualified": False,
              "two_physical_devices_qualified": False, "rendered_permission_prompt_qualified": False}
    def save():
        RESULT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    save()
    try:
        for name, script, options, receipt in CASES:
            print("Starting " + name, flush=True)
            runtime_identity()
            log = WORK / (name + ".log")
            with log.open("w", encoding="utf-8") as output:
                subprocess.run([sys.executable, "-X", "utf8", script, *options], cwd=dev.ROOT, stdout=output, stderr=subprocess.STDOUT, check=True)
            row = {"name": name, "log": log.relative_to(dev.ROOT).as_posix(), "log_sha256": sha(log)}
            if receipt:
                path = dev.ENV / receipt
                if name == "remote-bridge-build":
                    verify_bridge(read(path))
                else:
                    validate(name, read(path))
                row.update(receipt=path.relative_to(dev.ROOT).as_posix(), receipt_sha256=sha(path))
            require(report["source_sha256"] == source_inputs(), "Source changed while an acceptance probe was live")
            require(report["deployed_sha256"] == deployed_inputs(), "Applied host changed during acceptance")
            report["cases"].append(row)
            save()
            print("Passed " + name, flush=True)
        report["runtime_after"] = runtime_identity()
        for key in ("port", "executable", "executable_sha256", "config", "config_sha256", "home"):
            require(report["runtime_before"][key] == report["runtime_after"][key], "The deployment changed during acceptance: " + key)
        report.update(passed=True, state="passed", completed_at_utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
        save()
        checked_report()
        print(json.dumps({"passed": True, "cases": len(report["cases"]), "application_copies": 0,
                          "model_calls": 0, "android_device_qualified": False}))
    except Exception as error:
        report.update(passed=False, state="failed", error=str(error)[:512])
        save()
        raise


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, AssertionError, subprocess.SubprocessError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
