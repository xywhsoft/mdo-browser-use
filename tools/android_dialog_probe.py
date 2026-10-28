"""Compile the production dialog controller and exercise deterministic faults.

Uses the existing WSL JDK and one small QA output; no Android framework pass is
claimed and no installed app, WebView or Home is changed.
"""
import json
import subprocess
import sys
import dev


def main():
    classes=dev.ENV/'qa/android-dialog/classes';classes.mkdir(parents=True,exist_ok=True)
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    result=dev.ENV/'android-dialog-result.json'
    result.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserDialogs.java','tests/fixtures/AndroidDialogContractTest.java')]
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(source) for source in sources]],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=45)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidDialogContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report=json.loads(run.stdout.strip());report.update({'scope':'native-dialog-lifetime-contract','production_java_target':8,'framework_verified':False})
    result.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-3200:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
