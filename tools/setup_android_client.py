"""Prepare only pinned API/packaging tools for the Android Java dev client.

One project-owned cache, existing WSL JDK, no NDK/browser/application Home.
Use the pinned SDK's reviewed archive installer and reuse completed packages.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]


def main():
    if os.name=='nt':
        own=Path(__file__).resolve().as_posix();own='/mnt/'+own[0].lower()+own[2:]
        subprocess.run(['wsl.exe','--exec','python3',own],cwd=ROOT,check=True);return
    environment=(ROOT/'.mdo').resolve();root=(environment/'android-client-tools').resolve()
    if not root.is_relative_to(environment):raise RuntimeError('Android tooling path escaped the project deployment')
    if shutil.disk_usage(ROOT).free<11*1024**3:raise RuntimeError('Android tools require preserving at least 10 GiB of free space')
    source=environment/'native-source/tools/android';lock_bytes=(source/'toolchain.lock.json').read_bytes();lock=json.loads(lock_bytes)
    expected={f"platforms;android-{lock['compile_sdk']}",f"build-tools;{lock['build_tools']}"}
    packages=[row for row in lock['packages'] if row['package'] in expected and row['host'] in (None,'linux')]
    if len(packages)!=2 or {row['package'] for row in packages}!=expected or sum(row['size'] for row in packages)>140*1024**2:
        raise RuntimeError('The minimal Android client package set changed; refusing an expanded download')
    identity={'purpose':'mdo-browser-use/android-client-tools','lock_sha256':hashlib.sha256(lock_bytes).hexdigest()}
    marker=root/'.browser-use-owner.json'
    if root.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):
        raise RuntimeError('Unknown Android tooling directory preserved')
    root.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')
    installer={'__name__':'browser_use_android_installer','__file__':str(source/'setup.py')}
    exec(compile((source/'setup.py').read_text(),str(source/'setup.py'),'exec'),installer)
    print('Preparing only platform API and build-tools from the pinned SDK lock',flush=True)
    for row in packages:
        family,version=row['package'].split(';');destination=root/'sdk'/family/version;done=destination/'.xs-source-sha1'
        if not done.is_file() or done.read_text().strip()!=row['sha1']:installer['install'](root,row,destination)
        archive=root/'downloads'/row['url'].rsplit('/',1)[1]
        if archive.exists():
            if not archive.resolve().is_relative_to(root) or installer['digest'](archive)!=row['sha1']:
                raise RuntimeError('Unexpected SDK archive preserved')
            archive.unlink()
    java=shutil.which('javac')
    if not java:raise RuntimeError('The existing JDK is unavailable')
    size=sum(path.stat().st_size for path in root.rglob('*') if path.is_file())
    if size>1024**3:raise RuntimeError('Minimal Android tooling exceeded its 1 GiB disk budget')
    receipt={'sdk':str(root/'sdk'),'java_home':str(Path(java).resolve().parent.parent),'compile_sdk':lock['compile_sdk'],
      'build_tools':lock['build_tools'],'lock_sha256':identity['lock_sha256'],'ndk_installed':False,'download_bytes':sum(row['size'] for row in packages),
      'tooling_mib':round(size/1048576,1),'runtime_dependencies_added':False}
    (root/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2),flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
