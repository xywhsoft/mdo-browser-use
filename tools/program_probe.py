"""Boot the packed program and use its actual UI/native browser in the same Home."""
import base64
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
import subprocess
import sys
import threading
import time

import dev
import package_program
from sdk_probe import wait_receipt, SCOPE

HTML = """<!doctype html><meta charset=utf-8><title>Packed Browser Use</title>
<h1>Packaged native browser fixture</h1><label>Value<input id=value></label>
<button id=run>Apply</button><output id=result></output><script>
document.querySelector('#run').onclick=e=>document.querySelector('#result').textContent=
'Trusted:'+e.isTrusted+':'+document.querySelector('#value').value;</script>""".encode()


class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(HTML)))
        self.end_headers()
        self.wfile.write(HTML)

    def log_message(self, *args):
        pass


def main():
    original = dev.state()
    if original.get("pid"):
        raise RuntimeError("Stop the single development service before starting the packaged program")
    artifact = package_program.OUTPUT
    receipt_path = artifact.with_suffix(".receipt.json")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    if package_program.sha(artifact) != receipt["artifact"]["sha256"]:
        raise RuntimeError("Packaged program hash no longer matches its receipt")
    if (artifact.parent / "xs.json").exists() or (artifact.parent / "generated").exists():
        raise RuntimeError("Artifact directory contains unpacked app inputs; preserve them and inspect")
    receipt["runtime_verified"] = False
    receipt["runtime_checks"] = []
    receipt_path.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    report_path = dev.ENV / "program-runtime-result.json"
    report = {"state": "running", "artifact_sha256": receipt["artifact"]["sha256"],
              "same_home": True, "checks": []}
    def mark(value):
        report["checks"].append(value)
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(value, flush=True)
    (dev.ENV / "program-runtime-before.json").write_text(json.dumps(original, indent=2) + "\n", encoding="utf-8")
    server = HTTPServer(("127.0.0.1", 0), Fixture)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    child = browser = None
    try:
        with (dev.ENV / "program-runtime.log").open("wb") as log:
            child = subprocess.Popen([str(artifact), "--port", str(original["port"]),
                                      "--", "--home", str(dev.HOME)], cwd=artifact.parent,
                                     env=dict(os.environ, USE_WEBVIEW="1"), stdout=log, stderr=log,
                                     creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        current = dict(original, host=str(artifact), active_config="--port", pid=child.pid, window=True)
        dev.save(current)
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            if child.poll() is not None:
                raise RuntimeError("Packed program exited before readiness; inspect its local log")
            try:
                if dev.request("GET", "/api/v1/bootstrap")[0] == 200:
                    break
            except OSError:
                pass
            time.sleep(0.2)
        else:
            raise RuntimeError("Packed program did not become ready")
        info = dev.call("runtime.info")
        from pathlib import Path
        if Path(info["application_root"]).resolve() != artifact.parent.resolve():
            raise RuntimeError("Packed program did not use its own archive root")
        if Path(info["home"]).resolve() != dev.HOME.resolve():
            raise RuntimeError("Packed program did not reuse the existing development Home")
        mark("self_contained_archive_boot_same_home")
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            clients = dev.call("ui.clients")
            if clients:
                break
            time.sleep(0.2)
        else:
            raise RuntimeError("Packed frontend did not register an SDK client")
        mark("packed_ui_registered")
        plugin = dev.call("plugin.get", {"id": "mdo.browser-use"})
        if not plugin["enabled"]:
            raise RuntimeError("Portable plugin is not enabled in the existing Home")
        browser = dev.call("browseruse.tabs", {"action": "create", "client": clients[0]["id"],
            "url": "http://127.0.0.1:" + str(server.server_port) + "/fixture", "key": "packed-program-smoke"}, SCOPE)["id"]
        def execute(method, params):
            request = dev.call(method, {"browser": browser, **params}, SCOPE)
            try:
                return wait_receipt(request["id"])["result"]
            finally:
                dev.call("browseruse.result", {"id": request["id"], "release": True}, SCOPE)
        observed = execute("browseruse.observe", {})
        if not observed.get("ok") or "Packaged native browser fixture" not in observed.get("text", ""):
            raise RuntimeError("Packed native observation did not read the loopback fixture")
        mark("packed_c_js_native_observation")
        doc = observed["document"]
        filled = execute("browseruse.act", {"document": doc, "op": "fill", "target": {"css": "#value"}, "text": "packaged 🦉"})
        if not filled.get("ok") or filled.get("verification") != "field_state":
            raise RuntimeError("Packed native field verification failed")
        clicked = execute("browseruse.act", {"document": doc, "op": "click", "target": {"css": "#run"}})
        if not clicked.get("ok") or "Trusted:true:packaged 🦉" not in clicked.get("observation", {}).get("text", ""):
            raise RuntimeError("Packed native trusted input failed")
        mark("packed_native_unicode_fill_and_trusted_click")
        captured = execute("browseruse.screenshot", {"document": doc, "max_width": 800, "max_height": 600})
        image = captured.get("image", {})
        image_bytes = base64.b64decode(image.get("base64", ""), validate=True)
        if not captured.get("ok") or image.get("media_type") != "image/png" or not image_bytes.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Packed native screenshot failed")
        report["synthetic_screenshot_bytes"] = len(image_bytes)
        mark("packed_native_synthetic_capture")
        dev.call("browseruse.tabs", {"action": "close", "browser": browser}, SCOPE)
        browser = None
        mark("packed_browser_closed")
        report["state"] = "browser_checks_passed"
    except Exception as error:
        report.update(state="failed", error=str(error))
        raise
    finally:
        if browser:
            try:
                dev.call("browseruse.tabs", {"action": "close", "browser": browser}, SCOPE)
            except (OSError, RuntimeError):
                report["browser_cleanup"] = "service_shutdown_required"
        try:
            if child:
                dev.stop()
                child.wait(timeout=10)
            dev.save(dict(original, pid=None))
            dev.start()
            report["headless_restored"] = True
        except Exception as error:
            report.update(state="failed", cleanup_error=str(error), headless_restored=False)
            raise
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=5)
            report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    report["state"] = "passed"
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    receipt["runtime_verified"] = True
    receipt["runtime_checks"] = list(report["checks"])
    receipt_path.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"state": report["state"], "checks": len(report["checks"]),
                      "headless_restored": report["headless_restored"]}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
