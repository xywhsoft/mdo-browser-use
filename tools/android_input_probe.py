"""Run the actual Java native input sequencer with deterministic lifetime faults.

One project QA output, existing WSL JDK, no app/Home/provider changes. Framework
binding is compiled separately by build_android_client.py, not simulated here.
"""
import json
import subprocess
import sys
import dev


def main():
    directory=dev.ENV/'qa/android-input';classes=directory/'classes';classes.mkdir(parents=True,exist_ok=True)
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    result=dev.ENV/'android-input-result.json';result.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserInput.java','host/android/MdoBrowserLifecycle.java',
        'tests/fixtures/AndroidInputContractTest.java','tests/fixtures/AndroidLifecycleContractTest.java')]
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(source) for source in sources]],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=45)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidInputContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report=json.loads(run.stdout.strip());report.update({'scope':'native-input-lifetime-contract','production_java_target':8,'framework_verified':False})
    lifetime=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidLifecycleContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['lifecycle_checks']=json.loads(lifetime.stdout.strip())['checks']
    result.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-2400:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
