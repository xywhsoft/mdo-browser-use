"""Run the actual Java native input sequencer with deterministic lifetime faults.

One project QA output, existing WSL JDK, no app/Home/provider changes. Framework
binding is compiled separately by build_android_client.py, not simulated here.
"""
import json
import hashlib
import subprocess
import sys
import dev


def main():
    directory=dev.ENV/'qa/android-input';classes=directory/'classes';classes.mkdir(parents=True,exist_ok=True)
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    result=dev.ENV/'android-input-result.json';result.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    template=dev.ROOT/'tests/fixtures/AndroidFinishContractTest.java.in'
    overlay=dev.ROOT/'host/overlays.json'
    guards=[row['after'] for row in json.loads(overlay.read_text(encoding='utf-8'))['changes']
        if row.get('path')=='tools/android/MdoBrowser.java' and row.get('before')=='        if (stopped || url == null) return;']
    if len(guards)!=1:raise RuntimeError('Expected exact product main-document attach guard')
    generated=directory/'AndroidFinishContractTest.java'
    generated.write_text(template.read_text(encoding='utf-8').replace('@@ATTACH_GUARD@@',guards[0]),encoding='utf-8')
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserInput.java','host/android/MdoBrowserWheel.java','host/android/MdoBrowserLifecycle.java',
        'host/android/MdoBrowserChannel.java','tests/fixtures/AndroidInputContractTest.java',
        'tests/fixtures/AndroidLifecycleContractTest.java','tests/fixtures/AndroidChannelContractTest.java',
        'tests/fixtures/AndroidInputGuardContractTest.java','tests/fixtures/AndroidWheelContractTest.java',
        'tests/fixtures/AndroidHoverContractTest.java','tests/fixtures/AndroidInitialScaleContractTest.java','tests/fixtures/AndroidHoverSettleContractTest.java',
        'tests/fixtures/AndroidInputElapsedContractTest.java')]+[generated]
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(source) for source in sources]],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=45)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidInputContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report=json.loads(run.stdout.strip());report.update({'scope':'native-input-lifetime-contract','production_java_target':8,'framework_verified':False})
    lifetime=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidLifecycleContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['lifecycle_checks']=json.loads(lifetime.stdout.strip())['checks']
    channel=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidChannelContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['channel_checks']=json.loads(channel.stdout.strip())['checks']
    finish=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidFinishContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['document_finish_checks']=json.loads(finish.stdout.strip())['checks']
    guard=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidInputGuardContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['guard_checks']=json.loads(guard.stdout.strip())['checks']
    wheel=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidWheelContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['wheel_checks']=json.loads(wheel.stdout.strip())['checks']
    hover=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidHoverContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['hover_checks']=json.loads(hover.stdout.strip())['checks']
    initial=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidInitialScaleContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['initial_scale_checks']=json.loads(initial.stdout.strip())['checks']
    settle=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidHoverSettleContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['hover_settle_checks']=json.loads(settle.stdout.strip())['checks']
    elapsed=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidInputElapsedContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['elapsed_input_checks']=json.loads(elapsed.stdout.strip())['checks']
    report['source_sha256']={source.relative_to(dev.ROOT).as_posix():hashlib.sha256(source.read_bytes()).hexdigest() for source in [*sources,template,overlay,dev.ROOT/'tools/android_input_probe.py']}
    result.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-2400:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
