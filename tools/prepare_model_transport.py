"""Materialize the reviewed transport repair in the one existing native source.

All authoritative inputs are applied first to .mdo/source by apply_host.py.
The native checkout is an existing build input, not another application/Home.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

import dev

NATIVE = dev.ENV / "native-source"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def wanted_source(record, before, replacement):
    if digest(before.encode()) != record["before_sha256"] or before.count(record["before"]) != 1:
        raise RuntimeError("Pinned model transport baseline changed; preserve it")
    after = before.replace(record["before"], replacement, 1)
    if digest(after.encode()) != record["after_sha256"]:
        raise RuntimeError("Reviewed model transport replacement changed")
    return after


def apply(check=False):
    # Reuse the Windows Git object store rather than create a WSL checkout.
    python = shutil.which("python.exe") if os.name != "nt" and str(dev.ROOT).startswith("/mnt/") else None
    if python:
        script = subprocess.check_output(["wslpath", "-w", str(Path(__file__).resolve())], text=True).strip()
        subprocess.run([python, script, *(["--check"] if check else [])], cwd=dev.ROOT, check=True)
        return
    if not check and dev.state().get("pid"):
        raise RuntimeError("Stop the exact development runtime before preparing its native transport")
    lock = json.loads((dev.SOURCE / "deps.lock").read_text(encoding="utf-8"))
    spec = lock["browser_use_model_transport"]
    manifest = dev.SOURCE / spec["path"]
    if digest(manifest.read_bytes()) != spec["sha256"]:
        raise RuntimeError("Reviewed model transport manifest changed")
    record = json.loads(manifest.read_text(encoding="utf-8"))
    if record["path"] != "lib/xllm/src/dialect_responses.c":
        raise RuntimeError("Unexpected native transport target preserved")
    target = NATIVE / record["path"]
    replacement_path = manifest.parent / record["replacement"]
    for root, path in ((NATIVE, target), (dev.SOURCE, manifest), (dev.SOURCE, replacement_path)):
        if not path.resolve().is_relative_to(root.resolve()):
            raise RuntimeError("Model transport input escaped the deployment")
        for ancestor in (path, *path.parents):
            if ancestor.is_symlink() or ancestor.exists() and getattr(ancestor.lstat(), "st_file_attributes", 0) & 0x400:
                raise RuntimeError("Native model transport link/reparse point preserved")
            if ancestor == root:
                break
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=NATIVE, text=True).strip()
    if revision != lock["xserver"]["commit"]:
        raise RuntimeError("Pinned native revision changed; preserve it")
    before = subprocess.check_output(["git", "show", "HEAD:" + record["path"]], cwd=NATIVE).decode().replace("\r\n", "\n")
    after = wanted_source(record, before, replacement_path.read_text(encoding="utf-8"))
    current = target.read_text(encoding="utf-8")
    if current not in (before, after):
        raise RuntimeError("Unreviewed native model transport edit preserved")
    if check:
        if current != after:
            raise RuntimeError("Prepare the reviewed native model transport before building")
    elif current != after:
        pending = target.with_name(target.name + ".model.pending")
        if pending.exists() or pending.is_symlink():
            raise RuntimeError("Interrupted native model transport staging preserved")
        pending.write_text(after, encoding="utf-8", newline="\n")
        pending.replace(target)
    receipt = {"revision": revision, "manifest_sha256": spec["sha256"], "path": record["path"],
               "before_sha256": record["before_sha256"], "after_sha256": record["after_sha256"]}
    if not check:
        (dev.ENV / "native-model-transport-overlays.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print("Reviewed model transport " + ("verified" if check else "prepared"), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    apply(args.check)
