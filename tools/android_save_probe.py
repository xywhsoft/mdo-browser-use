"""Join exact Android download commands to the shared JS/C save pipeline.

Use the same project deployment, native fixture classes and fixed output roots.
This qualifies component integration, never an installed Android provider/UI.
"""
import hashlib
import json
import subprocess
import sys
import dev
import apply_host

def main():
    output=dev.ENV/'android-save-result.json'
    apply_host.apply(check=True)
    native=json.loads((dev.ENV/'android-cdp-socket-result.json').read_text())
    if not native.get('passed'):
        raise RuntimeError('Run the complete original native gate before this integration check')
    for name,expected in native['source_sha256'].items():
        if hashlib.sha256((dev.ROOT/name).read_bytes()).hexdigest()!=expected:
            raise RuntimeError('Native fixture inputs changed: '+name)
    classes=dev.ENV/'qa/android-cdp-native/classes';source=dev.ROOT/'tests/fixtures/android-cdp-java/org/xserver/android/CdpDownloadSavePeer.java'
    names=('tools/android_save_probe.py','tools/android_save_client_probe.mjs',source.relative_to(dev.ROOT).as_posix(),'host/download-save.js','host/android/browser-downloads.inc.java','tests/fixtures/android-cdp-java/DownloadCommandHost.java.in')
    hashes={name:hashlib.sha256((dev.ROOT/name).read_bytes()).hexdigest() for name in names}
    native_hash=hashlib.sha256((dev.ENV/'android-cdp-socket-result.json').read_bytes()).hexdigest()
    linux=lambda path:'/mnt/'+str(path)[0].lower()+str(path)[2:].replace('\\','/')
    subprocess.run(['wsl.exe','--exec','/usr/lib/jvm/java-17-openjdk-amd64/bin/javac','--release','8','-encoding','UTF-8','-cp',linux(classes),'-d',linux(classes),linux(source)],cwd=dev.ROOT,check=True)
    completed=subprocess.run(['node','tools/android_save_client_probe.mjs'],cwd=dev.ROOT,capture_output=True,text=True,encoding='utf-8',timeout=150)
    if completed.returncode:
        print(completed.stderr[-3000:],file=sys.stderr)
        raise RuntimeError('Android/shared/C save integration failed; preserve its receipt and original peer evidence')
    result=json.loads(output.read_text())
    if not result.get('passed') or result.get('original_source_claims')!=1 or result.get('framework_verified') is not False or result.get('provider_qualified') is not False:
        raise RuntimeError('The save integration did not confirm its exact scope')
    for name,expected in hashes.items():
        if hashlib.sha256((dev.ROOT/name).read_bytes()).hexdigest()!=expected:
            raise RuntimeError('Save integration inputs changed during the gate: '+name)
    if hashlib.sha256((dev.ENV/'android-cdp-socket-result.json').read_bytes()).hexdigest()!=native_hash:
        raise RuntimeError('Original native evidence changed during save integration')
    result.update(source_sha256=hashes,native_gate_receipt_sha256=native_hash)
    output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
