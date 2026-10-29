"""Prepare pinned API/packaging tools and optional native compiler in one cache.

One project-owned cache and existing WSL JDK; --native adds the locked NDK.
Use the pinned SDK's reviewed archive installer and reuse completed packages.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from android_tooling import archive as prepare_archive,regular_bytes

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--native',action='store_true',help='prepare the pinned native compiler for a complete mdo APK')
    args=parser.parse_args()
    if os.name=='nt':
        own=Path(__file__).resolve().as_posix();own='/mnt/'+own[0].lower()+own[2:]
        subprocess.run(['wsl.exe','--exec','python3',own,*sys.argv[1:]],cwd=ROOT,check=True);return
    environment=(ROOT/'.mdo').resolve();root=(environment/'android-client-tools').resolve()
    if not root.is_relative_to(environment):raise RuntimeError('Android tooling path escaped the project deployment')
    if shutil.disk_usage(ROOT).free<(20 if args.native else 11)*1024**3:raise RuntimeError('Android tools require preserving at least 10 GiB of free space and the bounded staging budget')
    source=environment/'native-source/tools/android';lock_bytes=(source/'toolchain.lock.json').read_bytes();lock=json.loads(lock_bytes)
    expected={f"platforms;android-{lock['compile_sdk']}",f"build-tools;{lock['build_tools']}"}
    packages=[row for row in lock['packages'] if row['package'] in expected and row['host'] in (None,'linux')]
    if len(packages)!=2 or {row['package'] for row in packages}!=expected or sum(row['size'] for row in packages)>140*1024**2:
        raise RuntimeError('The minimal Android client package set changed; refusing an expanded download')
    native_rows=[row for row in lock['packages'] if row['package']=='ndk;'+lock['ndk'] and row['host']=='linux']
    if len(native_rows)!=1 or native_rows[0]['size']>800*1024**2:raise RuntimeError('Pinned native Android package set changed')
    if args.native:packages+=native_rows
    identity={'purpose':'mdo-browser-use/android-client-tools','lock_sha256':hashlib.sha256(lock_bytes).hexdigest()}
    marker=root/'.browser-use-owner.json'
    if root.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):
        raise RuntimeError('Unknown Android tooling directory preserved')
    root.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')
    installer={'__name__':'browser_use_android_installer','__file__':str(source/'setup.py')}
    exec(compile((source/'setup.py').read_text(),str(source/'setup.py'),'exec'),installer)
    print('Preparing '+('API, build-tools and native compiler' if args.native else 'only API and build-tools')+' from the pinned SDK lock',flush=True)
    native_archive=None;downloaded=0
    for row in packages:
        family,version=row['package'].split(';');destination=root/'sdk'/family/version;done=destination/'.xs-source-sha1'
        if destination.is_symlink() or not destination.resolve().is_relative_to(root):raise RuntimeError('Linked SDK package destination preserved')
        if not done.is_file() or done.read_text().strip()!=row['sha1']:
            if destination.exists():raise RuntimeError('Unknown SDK package directory preserved')
            _,details=prepare_archive(root,row,(8 if family=='ndk' else 1)*1024**3)
            downloaded+=details['downloaded_bytes']
            if family=='ndk':native_archive=details
            installer['install'](root,row,destination)
        cached=root/'downloads'/row['url'].rsplit('/',1)[1]
        if cached.exists():
            if cached.is_symlink() or not cached.resolve().is_relative_to(root) or installer['digest'](cached)!=row['sha1']:
                raise RuntimeError('Unexpected SDK archive preserved')
            cached.unlink()
    java=shutil.which('javac')
    if not java:raise RuntimeError('The existing JDK is unavailable')
    native=root/'sdk/ndk'/lock['ndk'];native_marker=native/'.xs-source-sha1'
    native_installed=native_marker.is_file() and native_marker.read_text().strip()==native_rows[0]['sha1']
    native_size=regular_bytes(native) if native_installed else 0
    size=regular_bytes(root)
    if size-native_size>1024**3 or native_size>8*1024**3:raise RuntimeError('Android tools exceeded the base/native disk budget')
    native_receipt=None
    if native_installed:
        native_receipt_path=root/'native-receipt.json'
        old=json.loads(native_receipt_path.read_text()) if native_receipt_path.is_file() else {}
        binaries={}
        for name in ('clang','llvm-ar','llvm-nm','llvm-strip'):
            binary=(native/'toolchains/llvm/prebuilt/linux-x86_64/bin'/name).resolve()
            if not binary.is_relative_to(native.resolve()) or not binary.is_file():raise RuntimeError('Incomplete/foreign native compiler preserved')
            value=hashlib.sha256(binary.read_bytes()).hexdigest();binaries[name]=value
            if old.get('binaries') and old['binaries'].get(name)!=value:raise RuntimeError('Native compiler differs from its prepared receipt')
        properties=(native/'source.properties').read_text()
        if ('Pkg.Revision = '+lock['ndk']) not in properties:raise RuntimeError('Native compiler revision differs from the lock')
        version=subprocess.check_output([str(native/'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'),'--version'],text=True).splitlines()[0]
        native_receipt=dict(ndk=lock['ndk'],source_sha1=native_rows[0]['sha1'],lock_sha256=identity['lock_sha256'],binaries=binaries,
            compiler_version=version,tooling_mib=round(native_size/1048576,1),archive=native_archive or old.get('archive'),runtime_dependencies_added=False)
        native_receipt_path.write_text(json.dumps(native_receipt,indent=2)+'\n')
    receipt={'sdk':str(root/'sdk'),'java_home':str(Path(java).resolve().parent.parent),'compile_sdk':lock['compile_sdk'],
      'build_tools':lock['build_tools'],'lock_sha256':identity['lock_sha256'],'ndk_installed':native_installed,'download_bytes':downloaded,
      'requested_package_bytes':sum(row['size'] for row in packages),
      'tooling_mib':round(size/1048576,1),'runtime_dependencies_added':False}
    (root/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2),flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
