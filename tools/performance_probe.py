"""Measure bounded public Windows Browser Use work in the single deployment.

Metrics describe this local fixture/run, not a production latency or leak claim.
No model, external website, extra browser installation or copied Home is used.
"""
import argparse
import base64
import datetime
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import struct
import subprocess
import time

import acceptance_probe
import dev

SCOPE = {"extension": "plugin.mdo.browser-use", "project": "default",
         "session": "browser-performance-qa", "namespace": "performance", "member": "probe"}
OUTPUT = dev.ENV / "performance-result.json"


def percentiles(values):
    ordered = sorted(values)
    return {"samples": len(ordered), "p50_ms": ordered[math.ceil(len(ordered) * .5) - 1],
            "p95_ms": ordered[math.ceil(len(ordered) * .95) - 1], "max_ms": ordered[-1]}


def process_sample(root_pid):
    # Observation only: no process is stopped or modified. Children can retire
    # during sampling, so counts/memory are a point-in-time envelope, not a leak
    # oracle. Do not collect command lines, window titles or unrelated processes.
    script = r'''
$rows = @(Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId)
$owned = [System.Collections.Generic.HashSet[int]]::new()
[void]$owned.Add(ROOT_PID)
do {
  $added = 0
  foreach ($row in $rows) {
    if ($owned.Contains([int]$row.ParentProcessId) -and $owned.Add([int]$row.ProcessId)) { $added++ }
  }
} while ($added -gt 0)
$samples = @(foreach ($pidValue in $owned) {
  $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
  if ($process) { [pscustomobject]@{ pid=$process.Id; working_set=$process.WorkingSet64;
    private_bytes=$process.PrivateMemorySize64; handles=$process.HandleCount } }
})
ConvertTo-Json -InputObject $samples -Compress
'''.replace("ROOT_PID", str(int(root_pid)))
    rows = json.loads(subprocess.check_output(["powershell", "-NoProfile", "-Command", script],
                                             text=True, timeout=20))
    if not any(row["pid"] == root_pid for row in rows):
        raise RuntimeError("The measured original development process is gone")
    return {"processes": len(rows), "working_set_bytes": sum(r["working_set"] for r in rows),
            "private_bytes": sum(r["private_bytes"] for r in rows),
            "handles": sum(r["handles"] for r in rows)}


def main(iterations=8):
    if type(iterations) is not int or not 1 <= iterations <= 16:
        raise ValueError("Use 1 to 16 sequential iterations")
    report = {"passed": False, "state": "running", "iterations": iterations, "cases": [],
              "model_calls": 0, "external_sites": 0, "application_copies": 0,
              "automatic_action_retries": 0, "memory_qualification": "observational_only",
              "environment": {"system": platform.platform(), "machine": platform.machine(),
                              "logical_processors": os.cpu_count()},
              "workload": {"paragraphs": 1024, "observe_text_limit": 4096, "observe_elements_limit": 100,
                           "image_max_width": 800, "image_max_height": 600, "image_max_bytes": 131072},
              "started_at": datetime.datetime.now(datetime.timezone.utc).isoformat()}
    def save():
        OUTPUT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    save()
    browser = None
    changed_runtime = False
    live_receipts = set()
    phase = "runtime_identity"
    runtime_pid = None
    metrics = {}

    def receipt(method, params, row, label, service="browseruse.result"):
        nonlocal phase
        phase = label
        started = time.perf_counter()
        queued = dev.call(method, params, SCOPE)
        identity = queued["id"]
        live_receipts.add((identity, service))
        polls = 0
        deadline = time.monotonic() + 25
        while time.monotonic() < deadline:
            current = dev.call(service, {"id": identity}, SCOPE)
            polls += 1
            if current["state"] in ("succeeded", "failed", "cancelled"):
                break
            time.sleep(.05)
        else:
            raise RuntimeError("Original public receipt exceeded the bounded wait: " + label)
        result = current.get("result")
        if current["state"] != "succeeded" or not isinstance(result, dict) or result.get("ok") is False:
            error = current.get("error") or (result or {}).get("error")
            raise RuntimeError("Original public receipt failed: " + label + ": " + str(error)[:512])
        release = "ui.release" if service == "ui.result" else "browseruse.result"
        dev.call(release, {"id": identity, **({"release": True} if service != "ui.result" else {})}, SCOPE)
        live_receipts.remove((identity, service))
        elapsed = round((time.perf_counter() - started) * 1000, 3)
        row[label] = {"elapsed_ms": elapsed, "polls": polls,
                      "decoded_result_json_bytes": len(json.dumps(result, separators=(",", ":")).encode())}
        metrics.setdefault(label, []).append(elapsed)
        return result

    def capacity(client, row, label):
        result = receipt("ui.invoke", {"client": client, "action": "workbench.browser",
            "params": {"id": "performance-capabilities", "command": "capabilities"}}, row, label, "ui.result")
        value = result.get("creation_capacity")
        if not isinstance(value, dict) or value.get("limit") != 16 or value.get("pending") != 0:
            raise RuntimeError("Native creation reservations did not drain")
        return value

    try:
        report["runtime_before"] = acceptance_probe.runtime_identity()
        report["source_sha256"] = acceptance_probe.source_inputs()
        report["deployed_sha256"] = acceptance_probe.deployed_inputs()
        fixture = dev.ROOT / "tests/fixtures/performance.html"
        (dev.SOURCE / "app/web/__browser_use_performance__.html").write_bytes(fixture.read_bytes())
        report["fixture_sha256"] = hashlib.sha256(fixture.read_bytes()).hexdigest()
        dev.stop(); changed_runtime = True; dev.start(window=True)
        runtime_pid = dev.state()["pid"]
        end = time.monotonic() + 20
        clients = []
        while time.monotonic() < end:
            clients = dev.call("ui.clients")
            if clients:
                break
            time.sleep(.1)
        if len(clients) != 1:
            raise RuntimeError("Require exactly one actual development display client")
        client = clients[0]["id"]
        report["owned_browsers_before"] = [r["id"] for r in dev.call("browseruse.tabs", {"action": "list"}, SCOPE)["tabs"]]
        report["owned_panels_before"] = [r["id"] for r in dev.call("panel.list", {}, SCOPE)]
        baseline = {}
        report["capacity_before"] = capacity(client, baseline, "initial_capabilities")
        report["process_before"] = process_sample(runtime_pid)
        for index in range(iterations):
            row = {"iteration": index + 1}
            report["cases"].append(row)
            phase = "create"
            start = time.perf_counter()
            browser = dev.call("browseruse.tabs", {"action": "create", "client": client,
                "url": f"http://127.0.0.1:{dev.state()['port']}/__browser_use_performance__.html",
                "key": "bounded-performance-" + str(index)}, SCOPE)["id"]
            row["record_create_ms"] = round((time.perf_counter() - start) * 1000, 3)
            page = receipt("browseruse.observe", {"browser": browser, "limit": 100,
                "max_bytes": 4096, "timeout_ms": 10000}, row, "initial_observe")
            if page.get("frame_routing") != "native-isolated-v1" or "Bounded performance fixture" not in page.get("text", ""):
                raise RuntimeError("The actual native frame driver did not observe the fixed workload")
            bounded = receipt("browseruse.observe", {"browser": browser, "limit": 100,
                "max_bytes": 4096, "timeout_ms": 10000}, row, "repeat_observe")
            if len(bounded.get("text", "").encode()) > 4096 or len(bounded.get("elements", [])) > 100:
                raise RuntimeError("Observation exceeded its declared text/element budget")
            filled = receipt("browseruse.act", {"browser": browser, "document": page["document"],
                "op": "fill", "target": {"label": "Performance input"}, "text": "bounded native input",
                "timeout_ms": 10000}, row, "native_fill")
            if filled.get("verification") != "field_state":
                raise RuntimeError("Native field state was not verified")
            clicked = receipt("browseruse.act", {"browser": browser, "document": page["document"],
                "op": "click", "target": {"name": "Performance click"}, "timeout_ms": 10000}, row, "native_click")
            if "Performance clicks 1 trusted true" not in clicked.get("observation", {}).get("text", ""):
                raise RuntimeError("Original native click did not produce exactly one trusted effect")
            image = receipt("browseruse.screenshot", {"browser": browser, "document": page["document"],
                "format": "png", "max_width": 800, "max_height": 600, "max_bytes": 131072,
                "timeout_ms": 10000}, row, "screenshot")
            encoded = base64.b64decode(image["image"]["base64"], validate=True)
            if encoded[:8] != b"\x89PNG\r\n\x1a\n" or len(encoded) > 131072:
                raise RuntimeError("Capture did not respect the bounded PNG payload")
            width, height = struct.unpack(">II", encoded[16:24])
            if not 0 < width <= 800 or not 0 < height <= 600:
                raise RuntimeError("Delivered native image dimensions exceed their budget")
            row["screenshot"].update(image_bytes=len(encoded), width=width, height=height)
            phase = "close"
            start = time.perf_counter()
            dev.call("browseruse.tabs", {"action": "close", "browser": browser}, SCOPE)
            browser = None
            row["record_close_ms"] = round((time.perf_counter() - start) * 1000, 3)
            # The normal UI polls broker state every two seconds. Give its
            # native close path time to observe the original removal before
            # sampling the next steady-state process envelope.
            time.sleep(2.2)
            row["capacity"] = capacity(client, row, "post_close_capabilities")
            row["process"] = process_sample(runtime_pid)
            save()
        report["owned_browsers_after"] = [r["id"] for r in dev.call("browseruse.tabs", {"action": "list"}, SCOPE)["tabs"]]
        report["owned_panels_after"] = [r["id"] for r in dev.call("panel.list", {}, SCOPE)]
        if set(report["owned_browsers_after"]) != set(report["owned_browsers_before"]) or set(report["owned_panels_after"]) != set(report["owned_panels_before"]):
            raise RuntimeError("The original owned broker resources did not return to baseline")
        report["latency"] = {name: percentiles(values) for name, values in metrics.items()}
        report["process_after"] = report["cases"][-1]["process"]
        report["process_delta"] = {key: report["process_after"][key] - report["process_before"][key]
                                   for key in report["process_before"]}
        first = report["cases"][0]["process"]
        report["after_first_cycle_delta"] = {key: report["process_after"][key] - first[key] for key in first}
        if acceptance_probe.source_inputs() != report["source_sha256"] or acceptance_probe.deployed_inputs() != report["deployed_sha256"]:
            raise RuntimeError("Source or deployed inputs changed during the measurement")
        report.update(passed=True, state="passed", owned_resources_restored=True)
    except Exception as error:
        report.update(passed=False, state="failed", failure_phase=phase,
                      error_type=type(error).__name__, error=str(error)[:1024])
    finally:
        if browser:
            try:
                dev.call("browseruse.tabs", {"action": "close", "browser": browser}, SCOPE)
            except (RuntimeError, OSError) as error:
                report.setdefault("cleanup_errors", []).append(str(error)[:256])
        for identity, service in list(live_receipts):
            try:
                if service == "ui.result":
                    dev.call("ui.cancel", {"id": identity}, SCOPE)
                else:
                    dev.call("browseruse.result", {"id": identity, "cancel": True}, SCOPE)
                end = time.monotonic() + 5
                while time.monotonic() < end:
                    current = dev.call(service, {"id": identity}, SCOPE)
                    if current["state"] in ("succeeded", "failed", "cancelled"):
                        if service == "ui.result":
                            dev.call("ui.release", {"id": identity}, SCOPE)
                        else:
                            dev.call("browseruse.result", {"id": identity, "release": True}, SCOPE)
                        break
                    time.sleep(.05)
                else:
                    raise RuntimeError("Original cancelled receipt did not settle before cleanup")
            except (RuntimeError, OSError) as error:
                report.setdefault("cleanup_errors", []).append(str(error)[:256])
        if changed_runtime:
            try:
                dev.stop(); dev.start()
                report["runtime_after"] = acceptance_probe.runtime_identity()
                report["headless_restored"] = True
            except (RuntimeError, OSError) as error:
                report.setdefault("cleanup_errors", []).append(str(error)[:256])
        if report.get("cleanup_errors"):
            report.update(passed=False, state="failed")
        report["finished_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        save()
    print(json.dumps({key: value for key, value in report.items()
                      if key not in ("source_sha256", "deployed_sha256", "cases")}, indent=2))
    if not report["passed"]:
        raise RuntimeError("Bounded performance verification failed; inspect its original receipt")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--iterations", type=int, choices=range(1, 17), default=8)
    main(parser.parse_args().iterations)
