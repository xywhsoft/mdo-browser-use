"""Pin small API-26 AOSP JSON sources for the desktop JVM gate only.

The APK uses the existing platform org.json, never these compilation fixtures.
Keep one bounded dependency cache and its unmodified Apache-2.0 source headers.
"""
import base64
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT=Path(__file__).resolve().parents[1]


def prepare():
    spec=json.loads((ROOT/'host/android/json-test-sources.json').read_text(encoding='utf-8'))
    directory=ROOT/'.mdo/android-client-tools/json-test'
    marker=directory/'.browser-use-owner.json';expected={'purpose':spec['purpose']}
    if directory.is_symlink() or not directory.resolve().is_relative_to((ROOT/'.mdo').resolve()):
        raise RuntimeError('Linked/escaped test JSON cache preserved')
    if directory.exists() and (not marker.is_file() or json.loads(marker.read_text(encoding='utf-8'))!=expected):
        raise RuntimeError('Unknown test JSON cache preserved')
    directory.mkdir(parents=True,exist_ok=True)
    if not marker.exists():marker.write_text(json.dumps(expected)+'\n',encoding='utf-8')
    allowed=set(spec['files'])|{marker.name}
    if any(p.name not in allowed or p.is_symlink() or not p.is_file() for p in directory.iterdir()):
        raise RuntimeError('Unexpected test JSON cache input preserved')
    result=[]
    for name,digest in spec['files'].items():
        path=directory/name
        if path.exists():
            if hashlib.sha256(path.read_bytes()).hexdigest()!=digest:raise RuntimeError('Changed test JSON source preserved')
        else:
            with urllib.request.urlopen(spec['base_url']+name+'?format=TEXT',timeout=25) as response:
                raw=response.read(131073)
            if len(raw)>131072:raise RuntimeError('Test JSON download limit')
            source=base64.b64decode(raw,validate=True)
            if len(source)>65536 or hashlib.sha256(source).hexdigest()!=digest:raise RuntimeError('Test JSON source checksum mismatch')
            pending=path.with_name(name+'.pending')
            if pending.exists() or pending.is_symlink():raise RuntimeError('Interrupted test JSON source preserved')
            with pending.open('xb') as output:output.write(source)
            pending.replace(path)
        result.append(path)
    return result


if __name__=='__main__':
    prepare();print('Verified pinned test-only AOSP JSON sources')
