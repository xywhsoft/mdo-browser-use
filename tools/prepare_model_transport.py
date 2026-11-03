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


def reviewed_source(record, before, directory):
    if "changes" not in record:
        return wanted_source(record, before, (directory / record["replacement"]).read_text(encoding="utf-8"))
    if digest(before.encode()) != record["before_sha256"]:
        raise RuntimeError("Pinned model transport baseline changed; preserve it")
    after = before
    for change in record["changes"]:
        if after.count(change["before"]) != 1:
            raise RuntimeError("Model transport anchor is not unique; preserve it")
        replacement = change.get("after")
        if "after_file" in change:
            replacement = (directory / change["after_file"]).read_text(encoding="utf-8") + change.get("suffix", "")
        after = after.replace(change["before"], replacement, 1)
    if digest(after.encode()) != record["after_sha256"]:
        raise RuntimeError("Reviewed model transport replacement changed")
    return after


def safe_path(root, path):
    if not path.resolve().is_relative_to(root.resolve()):
        raise RuntimeError("Model transport input escaped the deployment")
    for ancestor in (path, *path.parents):
        if ancestor.is_symlink() or ancestor.exists() and getattr(ancestor.lstat(), "st_file_attributes", 0) & 0x400:
            raise RuntimeError("Native model transport link/reparse point preserved")
        if ancestor == root:
            break


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
    safe_path(dev.SOURCE, manifest)
    records = json.loads(manifest.read_text(encoding="utf-8"))["files"]
    allowed = {"lib/xllm/src/dialect_responses.c", "lib/xllm/src/dialect_completions.c"}
    if len(records) != len(allowed) or {r["path"] for r in records} != allowed:
        raise RuntimeError("Unexpected native transport targets preserved")
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=NATIVE, text=True).strip()
    if revision != lock["xserver"]["commit"]:
        raise RuntimeError("Pinned native revision changed; preserve it")
    prepared = []
    # Validate the entire set, including staging paths, before any mutation.
    for record in records:
        target = NATIVE / record["path"]
        pending = target.with_name(target.name + ".model.pending")
        safe_path(NATIVE, target)
        safe_path(NATIVE, pending)
        replacements = [record["replacement"]] if "replacement" in record else [
            c["after_file"] for c in record["changes"] if "after_file" in c]
        for name in replacements:
            if Path(name).name != name:
                raise RuntimeError("Unexpected model transport replacement path")
            safe_path(dev.SOURCE, manifest.parent / name)
        before = subprocess.check_output(["git", "show", "HEAD:" + record["path"]], cwd=NATIVE).decode().replace("\r\n", "\n")
        after = reviewed_source(record, before, manifest.parent)
        current = target.read_text(encoding="utf-8")
        if current not in (before, after):
            raise RuntimeError("Unreviewed native model transport edit preserved")
        if pending.exists() or pending.is_symlink():
            raise RuntimeError("Interrupted native model transport staging preserved")
        if check and current != after:
            raise RuntimeError("Prepare the reviewed native model transport before building")
        prepared.append((target, pending, current, after))
    if not check:
        for target, pending, current, after in prepared:
            if current == after:
                continue
            with pending.open("x", encoding="utf-8", newline="\n") as stream:
                stream.write(after)
            pending.replace(target)
    receipt = {"revision": revision, "manifest_sha256": spec["sha256"], "files": [
        {k: record[k] for k in ("path", "before_sha256", "after_sha256")} for record in records]}
    if not check:
        (dev.ENV / "native-model-transport-overlays.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print("Reviewed model transport " + ("verified" if check else "prepared"), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    apply(args.check)
