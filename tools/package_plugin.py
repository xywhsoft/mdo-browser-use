"""Build a deterministic, allowlisted portable plugin without copying an app."""
from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import sys

ROOT = Path(__file__).resolve().parents[1]
MAX_BYTES = 64 * 1024 * 1024
MAX_FILES = 128


def encode(value):
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")


def linked(info):
    return stat.S_ISLNK(info.st_mode) or bool(getattr(info, "st_file_attributes", 0) & 0x400)


def read_text(root, relative):
    """Read only explicit portable regular files, never follow a source link."""
    if (not isinstance(relative, str) or not relative or len(relative) > 220
            or not re.fullmatch(r"[A-Za-z0-9._/-]+", relative)):
        raise ValueError("Invalid package path")
    parts = relative.split("/")
    for part in parts:
        stem = part.split(".", 1)[0].lower()
        if (not part or part in (".", "..") or part.endswith((".", " "))
                or stem in {"con", "prn", "aux", "nul"}
                or re.fullmatch(r"(?:com|lpt)[1-9]", stem)):
            raise ValueError("Invalid package path: " + relative)
    root = root.resolve()
    path = root
    for index, part in enumerate(parts):
        path = path / part
        info = path.lstat()
        if linked(info):
            raise ValueError("Package source link/reparse point: " + relative)
        if index < len(parts) - 1 and not stat.S_ISDIR(info.st_mode):
            raise ValueError("Package parent is not a directory: " + relative)
    if not stat.S_ISREG(info.st_mode) or info.st_size > MAX_BYTES:
        raise ValueError("Package source is not a bounded regular file: " + relative)
    data = path.read_bytes()
    text = data.decode("utf-8").replace("\r\n", "\n")
    if "\0" in text or "\r" in text:
        raise ValueError("Package text contains unsupported control bytes: " + relative)
    return text.encode("utf-8")


def snapshot(root=ROOT):
    names = json.loads(read_text(root, "package-files.json"))
    if not isinstance(names, list) or not 1 <= len(names) <= MAX_FILES:
        raise ValueError("Invalid package file count")
    files, seen, total = [], set(), 0
    for name in names:
        # Keep executable/dev configuration and environment files out, even if
        # accidentally added to the allowlist. New payload types need review.
        if (not isinstance(name, str) or name.casefold() in seen
                or not (name == "LICENSE" or name == "docs/distribution.md"
                        or (name.startswith(("runtime/", "sdk/", "page/", "overlay/"))
                            and PurePosixPath(name).suffix in {".c", ".h", ".js", ".mjs", ".md"}))):
            raise ValueError("Unexpected or duplicate package source")
        data = read_text(root, name)
        seen.add(name.casefold())
        total += len(data)
        if total > MAX_BYTES:
            raise ValueError("Package decoded bytes exceed the host limit")
        files.append((name, data))
    files.sort(key=lambda item: item[0])
    manifest = json.loads(read_text(root, "plugin.json"))
    if manifest.get("format") != "mdo.plugin.v1" or manifest.get("id") != "mdo.browser-use":
        raise ValueError("Unexpected plugin identity")
    version = manifest.get("version", "")
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", version):
        raise ValueError("Invalid plugin version")
    required = [manifest.get("entry", {}).get("native")]
    required.extend(row.get("source") for row in manifest.get("overrides", []))
    if not required or any(name not in dict(files) for name in required):
        raise ValueError("Plugin entry or override source missing from package")
    if not {"LICENSE", "docs/distribution.md"}.issubset(dict(files)):
        raise ValueError("Package license or instructions missing")
    # The host counts descriptor text as well as attached decoded files.
    if total + len(encode(manifest)) > MAX_BYTES:
        raise ValueError("Package decoded bytes exceed the host limit")
    return manifest, files


def build(root=ROOT):
    manifest, files = snapshot(root)
    package = {"format": "mdo.extension.v2", "manifest": {
        "slug": manifest["id"], "name": manifest["name"], "version": manifest["version"],
        "description": manifest["description"],
        "readme": dict(files)["docs/distribution.md"].decode("utf-8"),
        "author": manifest["author"], "license": manifest["license"],
        "platforms": ["windows-x86_64"], "min_build_id": 70000049,
    }, "resources": [{"kind": "plugins", "id": manifest["id"],
        "content": encode(manifest).decode("utf-8").rstrip("\n"),
        "files": [{"path": name, "base64": base64.b64encode(data).decode("ascii")}
                  for name, data in files]}]}
    data = encode(package)
    name = "mdo-browser-use-" + manifest["version"] + ".mdo-extension.json"
    receipt = {"format": "mdo.browser-use.package-receipt.v1", "artifact": {
        "name": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()},
        "plugin": {"id": manifest["id"], "version": manifest["version"],
                   "files": len(files), "decoded_bytes": sum(len(data) for _, data in files)},
        "files": [{"path": path, "bytes": len(content),
                   "sha256": hashlib.sha256(content).hexdigest()} for path, content in files]}
    return name, data, receipt


def main():
    name, data, receipt = build()
    destination = ROOT / "dist"
    destination.mkdir(exist_ok=True)
    if linked(destination.lstat()):
        raise ValueError("Artifact directory is a link/reparse point")
    # Only two fixed generated files; no application staging tree or Home copy.
    for path, content in ((destination / name, data),
                          (destination / (name + ".receipt.json"), encode(receipt))):
        temporary = path.with_name(path.name + ".tmp")
        if temporary.exists() or temporary.is_symlink():
            raise ValueError("Unrecognized temporary artifact retained: " + temporary.name)
        with temporary.open("xb") as target:
            target.write(content)
        temporary.replace(path)
    print(json.dumps(receipt["artifact"], indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
