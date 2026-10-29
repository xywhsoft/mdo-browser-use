"""Build/reuse one pinned ARM64 host; no extra source, Home or engine checkout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
ENV=ROOT/'.mdo'
SOURCE=ENV/'source'
NATIVE=ENV/'native-source'
OUTPUT=ENV/'host/android-native'


def sha(path):
    with path.open('rb') as value:return hashlib.file_digest(value,'sha256').hexdigest()


def source_identity(directory):
    # Keep exact file-byte hashing while avoiding thousands of DrvFS realpath
    # RPCs. Windows reads this same checkout; no staging/source copy is made.
    windows_python=shutil.which('python.exe') if os.name!='nt' and str(ROOT).startswith('/mnt/') else None
    if windows_python:
        script=subprocess.check_output(['wslpath','-w',str(Path(__file__).resolve())],text=True).strip()
        return json.loads(subprocess.check_output([windows_python,script,'--source-identity'],cwd=ROOT,text=True))
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=directory,text=True).strip()
    tracked=subprocess.check_output(['git','ls-files','-z'],cwd=directory).split(b'\0')
    added=subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z'],cwd=directory).split(b'\0')
    digest=hashlib.sha256();count=0
    for raw in sorted(set(tracked+added)-{b''}):
        path=directory/os.fsdecode(raw)
        if path.is_symlink() or not path.resolve().is_relative_to(directory.resolve()) or not path.is_file():
            raise RuntimeError('Unexpected native source link/missing file preserved: '+os.fsdecode(raw))
        digest.update(raw+b'\0'+sha(path).encode()+b'\0');count+=1
    return dict(revision=head,tree_sha256=digest.hexdigest(),files=count)


def validate_build_state(record,inputs,library):
    if record.get('phase')!='verified' or record.get('inputs')!=inputs or record.get('binary_sha256')!=sha(library):
        raise RuntimeError('Native host differs from its verified source/compiler build; rebuild it')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reuse',action='store_true',help='verify the existing native host without compiling')
    parser.add_argument('--source-identity',action='store_true',help=argparse.SUPPRESS)
    args=parser.parse_args()
    if args.source_identity:
        if os.name!='nt':raise RuntimeError('The source identity helper must run on Windows')
        print(json.dumps(source_identity(NATIVE)));return
    if os.name=='nt':
        script=Path(__file__).resolve().as_posix();script='/mnt/'+script[0].lower()+script[2:]
        subprocess.run(['wsl.exe','--exec','python3',script,*sys.argv[1:]],cwd=ROOT,check=True);return
    sys.path.insert(0,str(SOURCE/'tools'))
    import build_mdo
    lock=build_mdo.load_object(build_mdo.LOCK_PATH)
    sdk_lock_path=NATIVE/'tools/android/toolchain.lock.json';sdk_lock=json.loads(sdk_lock_path.read_text())
    tooling=ENV/'android-client-tools'
    setup=json.loads((tooling/'receipt.json').read_text())
    compiler=json.loads((tooling/'native-receipt.json').read_text())
    if setup.get('lock_sha256')!=sha(sdk_lock_path) or not setup.get('ndk_installed') or compiler.get('lock_sha256')!=sha(sdk_lock_path) or compiler.get('ndk')!=sdk_lock['ndk']:
        raise RuntimeError('Prepare the pinned Android native tooling before building')
    sdk=tooling/'sdk';llvm=sdk/'ndk'/sdk_lock['ndk']/'toolchains/llvm/prebuilt/linux-x86_64/bin'
    for name in ('clang','llvm-ar','llvm-nm','llvm-strip'):
        file=(llvm/name).resolve()
        if not file.is_relative_to((sdk/'ndk'/sdk_lock['ndk']).resolve()) or not file.is_file() or compiler['binaries'].get(name)!=sha(file):
            raise RuntimeError('Native compiler identity changed: '+name)
    # Verify dependency trees directly; never let SDK discovery create another
    # patched checkout or relabel an old library to satisfy the current profile.
    build_mdo.verify_dependencies(NATIVE,lock)
    print('Checking exact native source/compiler identity',flush=True)
    identity=source_identity(NATIVE)
    if identity['revision']!=lock['xserver']['commit']:
        raise RuntimeError('Pinned native revision changed; refusing to reset it')
    inputs=dict(source=identity,profile_sha256=build_mdo.host_profile_digest(False),
                deps_lock_sha256=sha(build_mdo.LOCK_PATH),toolchain_lock_sha256=sha(sdk_lock_path),
                compiler=compiler['binaries'],sdk_overlay_sha256=lock['sdk_overlay']['sha256'])
    if OUTPUT.is_symlink() or not OUTPUT.resolve().is_relative_to(ENV.resolve()):
        raise RuntimeError('Native build output escaped the shared deployment')
    owner=OUTPUT/'.browser-use-owner.json';expected={'purpose':'mdo-browser-use/android-native'}
    if OUTPUT.exists() and (not owner.is_file() or json.loads(owner.read_text())!=expected):
        raise RuntimeError('Unknown native Android output preserved')
    OUTPUT.mkdir(parents=True,exist_ok=True);owner.write_text(json.dumps(expected)+'\n')
    state=OUTPUT/'browser-use-build.json';library=OUTPUT/'libxs.so'
    if args.reuse:
        validate_build_state(json.loads(state.read_text()),inputs,library)
        build_mdo.verify_host_receipt(library,lock,False)
        print('Verified reusable ARM64 host: '+sha(library),flush=True);return
    if shutil.disk_usage(ROOT).free<11*1024**3:
        raise RuntimeError('Native build requires preserving 10 GiB plus its bounded staging budget')
    state.write_text(json.dumps(dict(phase='building',inputs=inputs),indent=2)+'\n')
    os.environ['XS_BUILD_COMMIT']=identity['revision']
    builder=runpy.run_path(str(NATIVE/'tools/android/build_native.py'))
    print('Compiling one ARM64 native host',flush=True)
    result=builder['build'](sdk,OUTPUT,lock['xserver']['required_extensions'],'gcc',build_mdo.HOST_PROFILE_PATH)
    print('Compiler finished; rechecking source and ABI receipt',flush=True)
    if result!=library or source_identity(NATIVE)!=identity:
        raise RuntimeError('Native source changed during compilation; output is not qualified')
    build_mdo.stamp_host_overlay(library,lock)
    build_mdo.verify_host_receipt(library,lock,False)
    record=dict(phase='verified',inputs=inputs,binary_sha256=sha(library),binary_bytes=library.stat().st_size,
                application_copies=0,native_checkouts=1,device_qualified=False,framework_qualified=False)
    state.write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps({key:record[key] for key in ('phase','binary_sha256','binary_bytes','device_qualified','framework_qualified')},indent=2),flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,KeyError,subprocess.SubprocessError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
