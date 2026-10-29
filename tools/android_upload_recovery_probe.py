"""Actual abrupt JVM exits and bounded staging recovery; no second app/Home."""
import hashlib
import json
import subprocess
import sys

import dev

OUTPUT=dev.ENV/'android-upload-recovery-result.json'


def main():
    OUTPUT.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    directory=dev.ENV/'qa/android-upload-recovery';classes=directory/'classes';marker=directory/'.browser-use-owner.json'
    identity={'purpose':'mdo-browser-use/android-upload-recovery'}
    if directory.resolve()!=directory.absolute() or classes.resolve().parent!=directory.resolve() or classes.is_symlink():raise RuntimeError('Linked upload recovery output preserved')
    if directory.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):raise RuntimeError('Unknown upload recovery output preserved')
    classes.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')
    root=directory/'files'
    if root.exists():raise RuntimeError('Interrupted upload recovery filesystem fixture preserved')
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserUploadFiles.java','host/android/MdoBrowserUploadStore.java',
        'host/android/MdoBrowserUploadQueue.java','tests/fixtures/AndroidUploadRecoveryContractTest.java')]
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(path) for path in sources]],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidUploadRecoveryContractTest',linux(root)],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    result=json.loads(run.stdout.strip());result.update(scope='abrupt-jvm-private-staging-recovery',production_java_target=8,
        compiled_source_sha256=hashlib.sha256(b''.join(path.read_bytes() for path in sources)).hexdigest())
    OUTPUT.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as failure:
        OUTPUT.write_text('{"passed":false,"state":"failed","framework_verified":false,"process_restart_verified":false}\n')
        print((failure.stderr or '')[-2400:] if isinstance(failure,subprocess.CalledProcessError) else failure,file=sys.stderr);raise SystemExit(1)
