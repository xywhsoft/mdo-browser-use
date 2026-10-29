"""Compile and exercise real directory-relative upload staging in the project.

Existing WSL JDK only. This does not certify Android cache privacy or a WebView
file grant, and does not create another application, Home or engine checkout.
"""
import json
import hashlib
import subprocess
import sys

import dev
OUTPUT=dev.ENV/'android-upload-files-result.json'


def main():
    OUTPUT.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    directory=dev.ENV/'qa/android-upload';classes=directory/'classes'
    if directory.resolve()!=directory.absolute() or classes.resolve().parent!=directory.resolve() or classes.is_symlink():
        raise RuntimeError('Linked upload QA output preserved')
    marker=directory/'.browser-use-owner.json';identity={'purpose':'mdo-browser-use/android-upload-files'}
    if directory.exists() and (not marker.is_file() or json.loads(marker.read_text(encoding='utf-8'))!=identity):raise RuntimeError('Unknown upload QA directory preserved')
    classes.mkdir(parents=True,exist_ok=True)
    marker.write_text(json.dumps(identity)+'\n',encoding='utf-8')
    root=directory/'files'
    if root.exists():raise RuntimeError('Interrupted upload filesystem fixture preserved for inspection')
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserUploadFiles.java','tests/fixtures/AndroidUploadFilesContractTest.java')]
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(path) for path in sources]],
        cwd=dev.ROOT,check=True,capture_output=True,text=True)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidUploadFilesContractTest',linux(root)],
        cwd=dev.ROOT,check=True,capture_output=True,text=True)
    result=json.loads(run.stdout.strip());result.update(scope='directory-relative-streaming-upload-files',production_java_target=8,
        uri_assignment_available=False,android_automation_enabled=False,
        compiled_source_sha256=hashlib.sha256(b''.join(path.read_bytes() for path in sources)).hexdigest())
    OUTPUT.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))


def failed():
    OUTPUT.write_text('{"passed":false,"state":"failed","framework_verified":false,"uri_assignment_available":false,"android_automation_enabled":false}\n',encoding='utf-8')


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:failed();print((error.stderr or '')[-2000:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:failed();print(error,file=sys.stderr);raise SystemExit(1)
