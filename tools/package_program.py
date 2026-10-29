"""Pack the enhanced Windows program in place; retain and restore QA inputs."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
from unittest.mock import patch

import apply_host
import dev
from package_plugin import linked

APP = dev.SOURCE / "app"
WORK = dev.ENV / "qa/program-package"
STATE = dev.ENV / "program-package-state.json"
OUTPUT = dev.ROOT / "dist/mdo-browser-use-dev-windows-x86_64.exe"
UNITY = "generated/mdo_unity.c"
PROGRAM_PREFIX = b'#define MDO_PRODUCT_EDITION "desktop"\n#define MDO_BROWSER_USE_DEVELOPMENT 1\n'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(value):
    STATE.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def path_at(root, relative):
    if not relative or any(part in ("", ".", "..") for part in relative.split("/")) or "\\" in relative or ":" in relative:
        raise ValueError("Invalid packaging journal path")
    path = root / relative
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError("Packaging path left its owned directory")
    for parent in [path, *path.parents]:
        if parent.exists() and linked(parent.lstat()):
            raise ValueError("Packaging path is a link/reparse point")
        if parent == root:
            break
    return path


def restore():
    if not STATE.exists():
        return
    state = json.loads(STATE.read_text(encoding="utf-8"))
    if state.get("phase") == "restored":
        return
    original = WORK / "unity.original.c"
    unity = path_at(APP, UNITY)
    if sha(original) != state["unity_before"] or sha(unity) not in (state["unity_before"], state["unity_packed"]):
        raise RuntimeError("Unity changed during packaging; preserve both files and inspect the journal")
    # Validate the complete restoration before modifying any destination.
    pending = []
    for row in state["excluded"]:
        source = path_at(WORK / "excluded", row["path"])
        target = path_at(APP, row["path"])
        if source.exists():
            if target.exists() or sha(source) != row["sha256"]:
                raise RuntimeError("Excluded file changed during packaging: " + row["path"])
            pending.append((source, target))
        elif not target.exists() or sha(target) != row["sha256"]:
            raise RuntimeError("Excluded file cannot be restored: " + row["path"])
    for source, target in pending:
        target.parent.mkdir(parents=True, exist_ok=True)
        source.rename(target)
    unity.write_bytes(original.read_bytes())
    if sha(unity) != state["unity_before"]:
        raise RuntimeError("Unity restoration failed")
    state["phase"] = "restored"
    save(state)


def allowed_files():
    paths = subprocess.check_output(["git", "ls-files", "-z", "--", "app"], cwd=dev.SOURCE).decode().split("\0")
    allowed = {path[4:] for path in paths if path.startswith("app/")}
    overlay = json.loads((dev.ROOT / "host/overlays.json").read_text(encoding="utf-8"))
    reviewed = {row["path"] for row in overlay["changes"]}
    reviewed.update(row["target"] for row in overlay["files"])
    # This checked-in JS surface is generated from the reviewed contract
    # overlay. Reject drift without permitting the generator to overwrite it.
    sys.path.insert(0, str(dev.SOURCE / "tools"))
    import workbench_contracts
    with patch.object(Path, "write_text", side_effect=RuntimeError("Generated SDK differs from its reviewed generator")):
        workbench_contracts.generate(dev.SOURCE)
    reviewed.add("app/web/js/api/workbench-services.js")
    allowed.update(path[4:] for path in reviewed if path.startswith("app/"))
    changed = subprocess.check_output(["git", "diff", "--name-only", "HEAD", "--", "app"], cwd=dev.SOURCE,
                                      stderr=subprocess.PIPE).decode().splitlines()
    if set(changed) - reviewed:
        raise RuntimeError("Unreviewed tracked app changes retained; refusing to package")
    allowed.update({UNITY, "generated/deps.lock", "generated/application-contracts.inc.h",
                    "generated/application-native.inc.h", "generated/application-routes.inc.h",
                    "generated/application-sdk-fingerprint.inc.h", "generated/native/windows/cli-forwarder.exe"})
    allowed.update("generated/module-sdk/mdo/" + path.name for path in (dev.SOURCE / "include/mdo").glob("*.h"))
    allowed.update("generated/module-sdk/mdo/native/" + path.name for path in (APP / "include/mdo").glob("*.h")
                   if path.name != "application.h")
    return allowed


def main(recover=False):
    if dev.state().get("pid"):
        raise RuntimeError("Stop the single development service before packing/restoring")
    restore()
    if recover:
        print("Packaging inputs restored")
        return
    apply_host.apply(check=True)
    if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=dev.SOURCE).decode().strip() != dev.LOCK["commit"]:
        raise RuntimeError("Unexpected mdo baseline")
    allowed_files()  # Reject unrelated tracked changes before generating anything.
    for name in ("xs.exe", "xsw.exe"):
        binary = dev.ENV / "host" / name
        record = json.loads(binary.with_name(name + ".build.json").read_text(encoding="utf-8"))
        if sha(binary) != record["binary_sha256"]:
            raise RuntimeError("Enhanced host build receipt changed: " + name)
    sys.path.insert(0, str(dev.SOURCE / "tools"))
    import build_mdo
    import build_cli_forwarder
    lock = build_mdo.load_object(build_mdo.LOCK_PATH)
    # The pinned generator removes old bundled provider keys. Do not let an
    # unexpected local key enter that path or the product archive.
    if build_mdo.BUILTIN_KEY_PATH.exists():
        raise RuntimeError("Unexpected legacy bundled key retained; inspect it before packaging")
    build_mdo.prepare(lock)
    build_cli_forwarder.build(dev.ENV / "native-source", APP / "generated/native/windows/cli-forwarder.exe",
                              shutil.which("gcc") or "E:/software/w64devkit/bin/gcc.exe")
    allowed = allowed_files()
    included, excluded = {}, []
    for path in sorted(APP.rglob("*")):
        if linked(path.lstat()):
            raise RuntimeError("App link/reparse point retained; refusing to package")
        if path.is_dir():
            continue
        relative = path.relative_to(APP).as_posix()
        if not path.is_file():
            raise RuntimeError("Non-regular app source retained")
        if relative in allowed:
            included[relative] = {"bytes": path.stat().st_size, "sha256": sha(path)}
        elif (relative in {".browser-use.dev.json", ".browser-use.run.json", "generated/native/windows/cli-forwarder.build.json"}
              or re.fullmatch(r"web/__browser_use_[a-z0-9_]+__\.html", relative)):
            excluded.append({"path": relative, "sha256": sha(path)})
        else:
            raise RuntimeError("Unknown app file retained; refusing to package: " + relative)
    config = json.loads((APP / "xs.json").read_text(encoding="utf-8"))
    service = config["services"][0]
    if service["class"] != "app" or service["port"] != 0 or service["ip"] != "127.0.0.1":
        raise RuntimeError("Product configuration is not the portable loopback GUI")
    unity = APP / UNITY
    original = unity.read_bytes()
    packed = PROGRAM_PREFIX + original
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / "unity.original.c").write_bytes(original)
    state = {"format": "mdo.browser-use.program-package-state.v1", "phase": "prepared",
             "excluded": excluded, "unity_before": hashlib.sha256(original).hexdigest(),
             "unity_packed": hashlib.sha256(packed).hexdigest()}
    save(state)
    OUTPUT.parent.mkdir(exist_ok=True)
    if linked(OUTPUT.parent.lstat()):
        raise RuntimeError("Artifact output directory is a link/reparse point")
    temporary = OUTPUT.with_name(OUTPUT.stem + ".pending.exe")
    if temporary.exists():
        raise RuntimeError("Previous pending artifact retained; inspect it before retrying")
    try:
        for row in excluded:
            target = path_at(WORK / "excluded", row["path"])
            if target.exists():
                raise RuntimeError("Previous excluded packaging file retained")
            target.parent.mkdir(parents=True, exist_ok=True)
            path_at(APP, row["path"]).rename(target)
        unity.write_bytes(packed)
        included[UNITY] = {"bytes": len(packed), "sha256": hashlib.sha256(packed).hexdigest()}
        state["phase"] = "packing"
        save(state)
        packer = dev.ENV / "host/xsw.exe"
        subprocess.run([str(packer), "pack", str(APP), "-o", str(temporary)], check=True, cwd=dev.ROOT)
        # The GUI host redirects stdout to its log; use the same native pack
        # provider in the console host to obtain the inventory for comparison.
        listing = subprocess.check_output([str(dev.ENV / "host/xs.exe"), "pack", "--list", str(temporary)], cwd=dev.ROOT).decode()
        actual = {match[2]: int(match[1]) for line in listing.splitlines()
                  if (match := re.fullmatch(r"\s*(\d+) (.+)", line))}
        if actual != {name: row["bytes"] for name, row in included.items()}:
            raise RuntimeError("Native pack provider inventory differs from reviewed inputs")
        for name, row in included.items():
            if sha(path_at(APP, name)) != row["sha256"]:
                raise RuntimeError("App source changed while packing: " + name)
        temporary.replace(OUTPUT)
        result = {"format": "mdo.browser-use.program-receipt.v1", "baseline": dev.LOCK["commit"],
                  "artifact": {"name": OUTPUT.name, "bytes": OUTPUT.stat().st_size, "sha256": sha(OUTPUT)},
                  "native_host_sha256": sha(packer), "files": included,
                  "excluded_qa_files": len(excluded), "application_copies": 0,
                  "native_inventory_verified": True, "runtime_verified": False,
                  "official_update_channel": "disabled_development_preview"}
        OUTPUT.with_suffix(".receipt.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result["artifact"], indent=2))
    finally:
        restore()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--restore", action="store_true", help="Recover interrupted in-place packaging")
    args = parser.parse_args()
    try:
        main(args.restore)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
