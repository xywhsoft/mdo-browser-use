"""One reusable mdo deployment, directly inside this plugin project."""
from __future__ import annotations

import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
ENV = ROOT / ".mdo"
SOURCE = ENV / "source"
HOME = ENV / "home"
STATE = ENV / "environment.json"
LOCK = json.loads((ROOT / "mdo.lock.json").read_text(encoding="utf-8"))


def run(*args: str, cwd: Path = ROOT) -> str:
    result = subprocess.run(args, cwd=cwd, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result.stdout.strip()


def save(state: dict) -> None:
    ENV.mkdir(exist_ok=True)
    path = STATE.with_suffix(".tmp")
    path.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    path.replace(STATE)


def state() -> dict:
    return json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else {}


def request(method: str, path: str, body: dict | None = None,
            *, headers: dict | None = None) -> tuple[int, dict, bytes]:
    current = state()
    connection = http.client.HTTPConnection("127.0.0.1", current["port"], timeout=15)
    outgoing = dict(headers or {})
    if body is not None:
        outgoing["Content-Type"] = "application/json"
    if method not in ("GET", "HEAD", "OPTIONS"):
        _, response_headers, _ = request("GET", "/api/v1/bootstrap")
        token = response_headers.get("x-mdo-write-token")
        if token:
            outgoing["X-Mdo-Write-Token"] = token
    try:
        connection.request(method, path,
                           json.dumps(body).encode() if body is not None else None,
                           outgoing)
        response = connection.getresponse()
        return response.status, dict((k.lower(), v) for k, v in response.getheaders()), response.read()
    finally:
        connection.close()


def call(method: str, params: dict | None = None, context: dict | None = None):
    code, _, raw = request("POST", "/api/v1/application/call",
                           {"method": method, "params": params or {},
                            "context": context or {}})
    result = json.loads(raw)
    if code != 200:
        error = result.get("error", {})
        raise RuntimeError(f"{method}: HTTP {code}: {error.get('code')}: {error.get('message')}")
    return result["data"]


def setup(base: Path) -> None:
    base = base.resolve()
    ENV.mkdir(exist_ok=True)
    if state().get("pid"):
        raise RuntimeError("Stop the development process before setting up the environment")
    if not SOURCE.exists():
        run("git", "clone", "--shared", "--no-checkout", str(base), str(SOURCE))
        run("git", "sparse-checkout", "init", "--cone", cwd=SOURCE)
        run("git", "sparse-checkout", "set", "app", "include", "tools", "assets", cwd=SOURCE)
        run("git", "checkout", "--detach", LOCK["commit"], cwd=SOURCE)
        run("git", "remote", "set-url", "origin", LOCK["repository"], cwd=SOURCE)
    if run("git", "rev-parse", "HEAD", cwd=SOURCE) != LOCK["commit"]:
        raise RuntimeError("Development mdo checkout is not at the pinned commit; refusing to reset it")
    original_host = base / ".build/host/xs.exe"
    host = ENV / "host/xs.exe"
    if hashlib.sha256(original_host.read_bytes()).hexdigest() != LOCK["bootstrap_host_sha256"]:
        raise RuntimeError("Bootstrap host hash changed; review mdo.lock.json before reuse")
    host.parent.mkdir(exist_ok=True)
    if not host.exists() or hashlib.sha256(host.read_bytes()).hexdigest() != LOCK["bootstrap_host_sha256"]:
        shutil.copy2(original_host, host)
    # Generate through the upstream source generator without materializing
    # another xs dependency checkout just to prepare C/SDK source files.
    prepare = (
        "import sys; from pathlib import Path; "
        "sys.path.insert(0, str(Path(sys.argv[1])/'tools')); "
        "import build_mdo; build_mdo.prepare(build_mdo.load_object(build_mdo.LOCK_PATH))"
    )
    run(sys.executable, "-c", prepare, str(SOURCE))
    current = state()
    if not current.get("port"):
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            current["port"] = listener.getsockname()[1]
    config = {"engine": {"workers": 1}, "services": [{
        "enabled": True, "class": "http", "name": "browser-use-dev",
        "ip": "127.0.0.1", "port": current["port"],
        "recv_limit": 8388608, "body_limit": 8388608,
        "host_default": {"enabled": True, "name": "mdo",
                         "path": str(SOURCE / "app/web"), "devlang": "c",
                         "devfile": str(SOURCE / "app/generated/mdo_unity.c")}
    }]}
    (ENV / "dev.json").write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    current.update({"source_commit": LOCK["commit"], "bootstrap_source": str(base),
                    "host": str(host), "home": str(HOME), "pid": None})
    save(current)
    print("Prepared one pinned mdo deployment in", ENV)


def start() -> None:
    current = state()
    if not current:
        raise RuntimeError("Run setup first")
    if current.get("pid"):
        raise RuntimeError("A development PID is recorded; use status/stop before starting again")
    log_path = ENV / "server.log"
    with log_path.open("wb") as log:
        child = subprocess.Popen(
            [current["host"], str(ENV / "dev.json"), "--", "--home", str(HOME)],
            cwd=SOURCE, env=dict(os.environ, USE_WEBVIEW="0"), stdout=log, stderr=log,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    current["pid"] = child.pid
    save(current)
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if child.poll() is not None:
            current["pid"] = None
            save(current)
            raise RuntimeError("mdo startup failed; inspect .mdo/server.log")
        try:
            code, _, _ = request("GET", "/api/v1/bootstrap")
            if code == 200:
                print(f"mdo ready at http://127.0.0.1:{current['port']} (PID {child.pid})")
                return
        except OSError:
            pass
        time.sleep(0.2)
    child.terminate()
    child.wait(timeout=10)
    current["pid"] = None
    save(current)
    raise RuntimeError("mdo startup timed out; inspect .mdo/server.log")


def stop() -> None:
    current = state()
    pid = current.get("pid")
    if pid:
        # Check the process image and exact configuration argument before using
        # a saved PID, which might otherwise refer to a recycled unrelated process.
        if os.name == "nt":
            script = (
                f"$p = Get-CimInstance Win32_Process -Filter 'ProcessId={int(pid)}'; "
                "if ($p) { $p | Select-Object ExecutablePath,CommandLine | ConvertTo-Json -Compress }"
            )
            output = run("powershell", "-NoProfile", "-Command", script)
            if output:
                process = json.loads(output)
                if Path(process["ExecutablePath"]).resolve() != Path(current["host"]).resolve() or str(ENV / "dev.json") not in process["CommandLine"]:
                    raise RuntimeError("Recorded PID identity does not match this development deployment")
                run("taskkill", "/PID", str(pid), "/T", "/F")
        else:
            raise RuntimeError("Native Linux development process control is not implemented yet")
        current["pid"] = None
        save(current)
    print("Development service stopped")


def status() -> None:
    current = state()
    if not current:
        print("Not configured")
        return
    try:
        code, _, _ = request("GET", "/api/v1/bootstrap")
    except OSError:
        code = 0
    size = sum(p.stat().st_size for p in ENV.rglob("*") if p.is_file())
    print(json.dumps({"pid": current.get("pid"), "port": current["port"],
                      "http_status": code, "deployment_mib": round(size / 1048576, 1),
                      "source_commit": current["source_commit"]}, indent=2))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("setup", "start", "stop", "status"))
    parser.add_argument("--mdo-source", type=Path, default=Path("D:/GIT/mdo"))
    args = parser.parse_args()
    try:
        if args.command == "setup":
            setup(args.mdo_source)
        else:
            globals()[args.command]()
        return 0
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        print("Development environment:", error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
