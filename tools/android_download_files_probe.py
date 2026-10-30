"""Exercise actual guarded download files in one reusable project QA directory."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

import dev

OUTPUT=dev.ENV/'android-download-files-result.json'
SOURCES=('host/android/MdoBrowserUploadFiles.java','host/android/MdoBrowserDownloadFiles.java',
         'tests/fixtures/AndroidDownloadFilesContractTest.java')


def main():
    directory=dev.ENV/'qa/android-download-files';classes=directory/'classes'
    expected={'purpose':'mdo-browser-use/android-download-files'};marker=directory/'.browser-use-owner.json'
    if directory.is_symlink() or classes.is_symlink() or directory.resolve()!=directory.absolute():
        raise RuntimeError('Linked/escaped download QA output preserved')
    if directory.exists() and (not marker.is_file() or json.loads(marker.read_text(encoding='utf-8'))!=expected):
        raise RuntimeError('Unknown download QA output preserved')
    root=directory/'files'
    if root.exists() or root.is_symlink():raise RuntimeError('Interrupted download filesystem evidence preserved for inspection')
    classes.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(expected)+'\n',encoding='utf-8')
    OUTPUT.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    source=[dev.ROOT/name for name in SOURCES]
    subprocess.run(['wsl.exe','--exec','/usr/lib/jvm/java-17-openjdk-amd64/bin/javac','--release','8','-encoding','UTF-8',
                    '-d',linux(classes),*[linux(p) for p in source]],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    run=subprocess.run(['wsl.exe','--exec','/usr/lib/jvm/java-17-openjdk-amd64/bin/java','-cp',linux(classes),
                        'org.xserver.android.AndroidDownloadFilesContractTest',linux(root)],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=60)
    result=json.loads(run.stdout);result.update(source_sha256={name:hashlib.sha256((dev.ROOT/name).read_bytes()).hexdigest() for name in SOURCES},
        android_automation_enabled=False,network_calls=0,application_copies=0)
    OUTPUT.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as failure:
        OUTPUT.write_text('{"passed":false,"state":"failed","framework_verified":false}\n',encoding='utf-8')
        print((getattr(failure,'stderr',None) or str(failure))[-3000:],file=sys.stderr);raise SystemExit(1)
