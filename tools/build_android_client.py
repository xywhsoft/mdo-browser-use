"""Build one tiny Java device gate using the pinned minimal SDK and existing JDK.

No native host, engine, mdo Home, release signer or runtime dependency is copied.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[1]
INPUTS=('host/android/MdoBrowserWorld.java','host/android/dev/BrowserUseDevActivity.java',
        'host/android/dev/AndroidManifest.xml','tools/build_android_client.py','host/android/browser-capabilities.inc.java',
        'host/android/dev/BrowserUseInputProbe.java','host/android/MdoBrowserInput.java','host/android/MdoBrowserInputTarget.java',
        'host/android/MdoBrowserLifecycle.java','host/android/MdoBrowserChannel.java','host/android/MdoBrowserDocument.java',
        'host/android/MdoBrowserNativeInput.java','host/android/MdoBrowserWheel.java','host/android/MdoBrowserCapture.java',
        'host/android/MdoBrowserCaptureBuffer.java','host/android/MdoBrowserCaptureRuntime.java','host/android/MdoBrowserCaptureTarget.java',
        'host/android/MdoBrowserNativeCapture.java','host/android/dev/BrowserUseCaptureProbe.java',
        'host/android/MdoBrowserDialogs.java','host/android/MdoBrowserDialogTarget.java','host/android/dev/BrowserUseDialogProbe.java')
HOST_INPUTS=('tools/android/MdoBrowser.java','tools/android/MdoBrowserInput.java','tools/android/MdoBrowserInputTarget.java',
    'tools/android/MdoBrowserLifecycle.java','tools/android/MdoBrowserChannel.java','tools/android/MdoBrowserDocument.java',
    'tools/android/MdoBrowserNativeInput.java','tools/android/MdoBrowserWheel.java','tools/android/MdoBrowserCapture.java',
    'tools/android/MdoBrowserCaptureBuffer.java','tools/android/MdoBrowserCaptureRuntime.java','tools/android/MdoBrowserCaptureTarget.java',
    'tools/android/MdoBrowserNativeCapture.java','tools/android/MdoBrowserDialogs.java','tools/android/MdoBrowserDialogTarget.java')
HOST_SCRIPTS=('tools/android/browser_overlay.py',)
NATIVE_INPUTS=tuple('android/src/org/xserver/android/'+name+'.java' for name in
    ('XsActivity','XsService','PageLocation','PackageInstall','NativeHost','FileChooserResult','BackgroundLeases'))
PACKAGE='org.xleaves.mdo.browseruse.dev'


def source_hash():
    digest=hashlib.sha256()
    for name in INPUTS:digest.update(name.encode()+b'\0'+(ROOT/name).read_bytes()+b'\0')
    deployed=json.loads((ROOT/'.mdo/host-overlays.json').read_text(encoding='utf-8'))
    for name in HOST_INPUTS+HOST_SCRIPTS:
        data=(ROOT/'.mdo/source'/name).read_bytes()
        if deployed['files'].get(name)!=hashlib.sha256(data).hexdigest():raise RuntimeError('Reviewed Android source needs applying before building the dev client')
        digest.update(name.encode()+b'\0'+data+b'\0')
    for name in NATIVE_INPUTS:
        digest.update(name.encode()+b'\0'+(ROOT/'.mdo/native-source'/name).read_bytes()+b'\0')
    return digest.hexdigest()


def main():
    if os.name=='nt':
        subprocess.run([sys.executable,str(ROOT/'tools/apply_host.py'),'--check'],cwd=ROOT,check=True)
        path=Path(__file__).resolve().as_posix();path='/mnt/'+path[0].lower()+path[2:]
        subprocess.run(['wsl.exe','--exec','python3',path],cwd=ROOT,check=True);return
    environment=(ROOT/'.mdo').resolve();output=(environment/'host/android-client').resolve()
    if not output.is_relative_to(environment):raise RuntimeError('Client output escaped the project deployment')
    marker=output/'.browser-use-owner.json';identity={'purpose':'mdo-browser-use/android-java-dev-client','package':PACKAGE}
    if output.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):
        raise RuntimeError('Unknown Android client directory preserved')
    tools_receipt=json.loads((environment/'android-client-tools/receipt.json').read_text())
    sdk=Path(tools_receipt['sdk']);java=Path(tools_receipt['java_home'])/'bin'
    platform=sdk/'platforms'/f"android-{tools_receipt['compile_sdk']}"/'android.jar'
    tools=sdk/'build-tools'/tools_receipt['build_tools']
    if not all(path.is_file() for path in (platform,java/'javac',tools/'aapt2',tools/'lib/d8.jar',tools/'lib/apksigner.jar')):
        raise RuntimeError('Prepared Android API/packaging tools are incomplete')
    output.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')
    receipt_path=output/'build.json';receipt_path.write_text('{"state":"building"}\n')
    # Only remove this verified owned build directory, never the signing key/APK.
    build=output/'build'
    if not build.resolve().is_relative_to(output) or build.is_symlink():raise RuntimeError('Unknown Android build path preserved')
    if build.exists():shutil.rmtree(build)
    build.mkdir();classes=build/'classes';classes.mkdir();dex=build/'dex';dex.mkdir()
    def run(*args):
        result=subprocess.run(list(map(str,args)),cwd=ROOT,text=True,capture_output=True,timeout=120)
        if result.returncode:raise RuntimeError(f'{Path(str(args[0])).name} failed: '+(result.stderr or result.stdout)[-1800:])
    sources=[ROOT/INPUTS[0],ROOT/INPUTS[1],ROOT/'host/android/dev/BrowserUseInputProbe.java',ROOT/'host/android/dev/BrowserUseCaptureProbe.java',
        ROOT/'host/android/dev/BrowserUseDialogProbe.java',*[ROOT/'.mdo/source'/name for name in HOST_INPUTS]]
    run(java/'javac','-encoding','UTF-8','-source','8','-target','8','-classpath',platform,'-d',classes,*sources)
    # Compile the actual pinned Activity with its reviewed hooks as well. These
    # classes are a compile gate only, never a second APK/service or runtime.
    activity_source=build/'activity';activity=activity_source/'org/xserver/android/XsActivity.java';activity.parent.mkdir(parents=True)
    shutil.copy2(ROOT/'.mdo/native-source'/NATIVE_INPUTS[0],activity)
    spec=importlib.util.spec_from_file_location('browser_use_android_overlay',ROOT/'.mdo/source'/HOST_SCRIPTS[0])
    overlay=importlib.util.module_from_spec(spec);spec.loader.exec_module(overlay);overlay.apply(activity_source)
    activity_classes=build/'activity-classes';activity_classes.mkdir()
    activity_sources=[*sorted(activity.parent.glob('*.java')),*[ROOT/'.mdo/native-source'/name for name in NATIVE_INPUTS[1:]]]
    run(java/'javac','-encoding','UTF-8','-source','8','-target','8','-classpath',platform,'-d',activity_classes,*activity_sources)
    jar=build/'classes.jar'
    with zipfile.ZipFile(jar,'w') as archive:
        for path in sorted(classes.rglob('*.class')):archive.write(path,path.relative_to(classes).as_posix())
    run(java/'java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',platform,'--output',dex,jar)
    unsigned=build/'unsigned.apk'
    run(tools/'aapt2','link','-I',platform,'--manifest',ROOT/INPUTS[2],'-o',unsigned)
    with zipfile.ZipFile(unsigned,'a') as archive:archive.write(dex/'classes.dex','classes.dex')
    aligned=build/'aligned.apk';run(tools/'zipalign','-f','4',unsigned,aligned)
    key=output/'development.p12'
    if not key.exists():
        run(java/'keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','browser-use-development',
            '-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Browser Use Development,O=Local Development,C=CN')
        key.chmod(0o600)
    apk=output/'mdo-browser-use-dev.apk';signer=[java/'java','-jar',tools/'lib/apksigner.jar']
    run(*signer,'sign','--ks',key,'--ks-key-alias','browser-use-development','--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned)
    run(*signer,'verify','--min-sdk-version','26',apk)
    receipt={'state':'built','package':PACKAGE,'source_sha256':source_hash(),'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'apk_bytes':apk.stat().st_size,'java_target':8,'min_api':26,'compile_api':tools_receipt['compile_sdk'],
        'toolchain_lock_sha256':tools_receipt['lock_sha256'],'ndk_required':False,'runtime_dependencies_added':False,
        'production_activity_compiled':True,'production_native_input_bridge_compiled':True,'production_wheel_input_compiled':True,
        'production_capture_bridge_compiled':True,'production_dialog_bridge_compiled':True,'native_dialog_fixture_compiled':True}
    receipt_path.write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
