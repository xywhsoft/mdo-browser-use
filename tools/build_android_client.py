"""Build one tiny Java device gate using the pinned minimal SDK and existing JDK.

No native host, engine, mdo Home, release signer or runtime dependency is copied.
"""
import hashlib
import ast
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

ROOT=Path(__file__).resolve().parents[1]
INPUTS=('host/android/MdoBrowserWorld.java','host/android/dev/BrowserUseDevActivity.java',
        'host/android/dev/AndroidManifest.xml','tools/build_android_client.py','host/android/browser-capabilities.inc.java',
        'host/android/dev/BrowserUseInputProbe.java','host/android/MdoBrowserInput.java','host/android/MdoBrowserInputTarget.java',
        'host/android/MdoBrowserLifecycle.java','host/android/MdoBrowserChannel.java','host/android/MdoBrowserDocument.java',
        'host/android/MdoBrowserNativeInput.java','host/android/MdoBrowserWheel.java','host/android/MdoBrowserCapture.java',
        'host/android/MdoBrowserCaptureBuffer.java','host/android/MdoBrowserCaptureRuntime.java','host/android/MdoBrowserCaptureTarget.java',
        'host/android/MdoBrowserNativeCapture.java','host/android/dev/BrowserUseCaptureProbe.java',
        'host/android/MdoBrowserDialogs.java','host/android/MdoBrowserDialogTarget.java','host/android/dev/BrowserUseDialogProbe.java',
        'host/android/MdoBrowserPopups.java','host/android/MdoBrowserWindowTarget.java','host/android/dev/BrowserUsePopupProbe.java',
        'host/android/MdoBrowserChoosers.java','host/android/MdoBrowserChooserTarget.java','host/android/MdoBrowserPrivatePicker.java','host/android/MdoBrowserPrivatePickerReply.java','host/android/browser-native-chooser.inc.java',
        'host/android/dev/BrowserUseChooserProbe.java','host/android/MdoBrowserUploadFiles.java','host/android/MdoBrowserDownloadFiles.java','host/android/MdoBrowserDownloadQueue.java',
        'host/android/MdoBrowserUploadStore.java','host/android/MdoBrowserUploadQueue.java','host/android/MdoBrowserUploadRuntime.java',
        'host/android/browser-native-upload.inc.java','host/android/browser-upload-assignment.inc.java','host/android/dev/BrowserUseUploadProbe.java',
        'host/android/MdoBrowserUploadUris.java','host/android/MdoBrowserUploadProxy.java','host/android/MdoBrowserUploadUriRuntime.java',
        'host/android/MdoBrowserUploadProvider.java','host/android/browser-manifest.inc.py',
        'host/android/dev/BrowserUseUploadUriProbe.java','host/android/MdoBrowserManualChooser.java','host/android/browser-manual-chooser.inc.java',
        'host/android/MdoBrowserPickerUris.java','host/android/MdoBrowserCdpNative.java',
        'host/android/MdoBrowserCdpJson.java','host/android/MdoBrowserCdpResponse.java','host/android/MdoBrowserCdpSession.java','host/android/MdoBrowserCdpMarker.java',
        'host/android/MdoBrowserCdpView.java','host/android/MdoBrowserCdpBinding.java','host/android/MdoBrowserCdpRegistry.java','host/android/MdoBrowserCdpWorker.java','host/android/MdoBrowserCdpNetworks.java','host/android/MdoBrowserCdpFrames.java','host/android/MdoBrowserCdpWorlds.java',
        'host/android/MdoBrowserCdpOwnerScripts.java','host/android/MdoBrowserCdpFrameRequest.java','host/android/MdoBrowserCdpFrameChain.java','host/android/MdoBrowserCdpInput.java','host/android/MdoBrowserCdpCapture.java','host/android/MdoBrowserCdpFileInput.java','host/android/MdoBrowserCdpChoosers.java',
        'host/android/browser-frame-router.inc.java','host/android/browser-network.inc.java','tools/update_android_owner_scripts.py',
        'host/android/MdoBrowserCdpUploadScripts.java','host/android/MdoBrowserCdpUpload.java','host/android/MdoBrowserUploadCoordinator.java','host/upload-picker-broker.js','tools/update_android_upload_broker.py')
HOST_INPUTS=('tools/android/MdoBrowser.java','tools/android/MdoBrowserInput.java','tools/android/MdoBrowserInputTarget.java',
    'tools/android/MdoBrowserLifecycle.java','tools/android/MdoBrowserChannel.java','tools/android/MdoBrowserDocument.java',
    'tools/android/MdoBrowserNativeInput.java','tools/android/MdoBrowserWheel.java','tools/android/MdoBrowserCapture.java',
    'tools/android/MdoBrowserCaptureBuffer.java','tools/android/MdoBrowserCaptureRuntime.java','tools/android/MdoBrowserCaptureTarget.java',
    'tools/android/MdoBrowserNativeCapture.java','tools/android/MdoBrowserDialogs.java','tools/android/MdoBrowserDialogTarget.java',
    'tools/android/MdoBrowserPopups.java','tools/android/MdoBrowserWindowTarget.java',
    'tools/android/MdoBrowserChoosers.java','tools/android/MdoBrowserChooserTarget.java','tools/android/MdoBrowserPrivatePicker.java','tools/android/MdoBrowserPrivatePickerReply.java','tools/android/MdoBrowserUploadFiles.java','tools/android/MdoBrowserDownloadFiles.java','tools/android/MdoBrowserDownloadQueue.java',
    'tools/android/MdoBrowserUploadStore.java','tools/android/MdoBrowserUploadQueue.java','tools/android/MdoBrowserUploadRuntime.java',
    'tools/android/MdoBrowserUploadUris.java','tools/android/MdoBrowserUploadProxy.java','tools/android/MdoBrowserUploadUriRuntime.java',
    'tools/android/MdoBrowserUploadProvider.java','tools/android/MdoBrowserManualChooser.java','tools/android/MdoBrowserPickerUris.java',
    'tools/android/MdoBrowserCdpNative.java','tools/android/MdoBrowserCdpJson.java','tools/android/MdoBrowserCdpResponse.java','tools/android/MdoBrowserCdpSession.java',
    'tools/android/MdoBrowserCdpMarker.java','tools/android/MdoBrowserCdpView.java','tools/android/MdoBrowserCdpBinding.java','tools/android/MdoBrowserCdpRegistry.java','tools/android/MdoBrowserCdpWorker.java','tools/android/MdoBrowserCdpNetworks.java','tools/android/MdoBrowserCdpFrames.java','tools/android/MdoBrowserCdpWorlds.java',
    'tools/android/MdoBrowserCdpOwnerScripts.java','tools/android/MdoBrowserCdpFrameRequest.java','tools/android/MdoBrowserCdpFrameChain.java','tools/android/MdoBrowserCdpInput.java','tools/android/MdoBrowserCdpCapture.java','tools/android/MdoBrowserCdpFileInput.java','tools/android/MdoBrowserCdpChoosers.java','tools/android/MdoBrowserCdpUploadScripts.java','tools/android/MdoBrowserCdpUpload.java','tools/android/MdoBrowserUploadCoordinator.java')
HOST_SCRIPTS=('tools/android/browser_overlay.py','tools/android/build_apk.py')
NATIVE_INPUTS=tuple('android/src/org/xserver/android/'+name+'.java' for name in
    ('XsActivity','XsService','PageLocation','PackageInstall','NativeHost','FileChooserResult','BackgroundLeases'))
PACKAGE='org.xleaves.mdo.browseruse.dev'


def verify_provider_manifest(text,package):
    root=ET.fromstring(text);android='{http://schemas.android.com/apk/res/android}'
    providers=root.findall('./application/provider')
    rows=[p for p in providers if p.get(android+'name')=='org.xserver.android.MdoBrowserUploadProvider']
    if len(rows)!=1 or rows[0].get(android+'authorities')!=package+'.mdo.browseruse.upload' or rows[0].get(android+'exported')!='false' or rows[0].get(android+'grantUriPermissions')!='false':
        raise RuntimeError('Upload provider manifest must remain private and package-bound')


def verify_mdo_manifest(overlay):
    # Render the actual pinned mdo packager expression, without building a
    # second application/native runtime or executing its packaging function.
    path=ROOT/'.mdo/source/tools/android/build_apk.py';module=ast.parse(path.read_text(encoding='utf-8'))
    build=next(node for node in module.body if isinstance(node,ast.FunctionDef) and node.name=='build')
    expressions=[node.value for node in ast.walk(build) if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='manifest' for t in node.targets)]
    if len(expressions)!=1:raise RuntimeError('Pinned mdo manifest expression changed')
    env=dict(package='org.xleaves.mdo',code=1,version='1',label='mdo',lock={'api':26,'compile_sdk':36},home_name='mdo-home',
        links_metadata='',debuggable=False,icon_attributes='',filters=[],package_install=True,SHARE_MANIFEST='',escape=escape,browser_manifest=overlay.manifest)
    expression=ast.Expression(expressions[0]);ast.fix_missing_locations(expression)
    text=eval(compile(expression,str(path),'eval'),env)
    verify_provider_manifest(text,env['package'])


def source_hash():
    import update_android_owner_scripts
    update_android_owner_scripts.verify()
    import update_android_upload_broker
    update_android_upload_broker.verify()
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
        ROOT/'host/android/dev/BrowserUseDialogProbe.java',ROOT/'host/android/dev/BrowserUsePopupProbe.java',
        ROOT/'host/android/dev/BrowserUseChooserProbe.java',ROOT/'host/android/dev/BrowserUseUploadProbe.java',
        ROOT/'host/android/dev/BrowserUseUploadUriProbe.java',*[ROOT/'.mdo/source'/name for name in HOST_INPUTS]]
    run(java/'javac','-encoding','UTF-8','-source','8','-target','8','-classpath',platform,'-d',classes,*sources)
    # Compile the actual pinned Activity with its reviewed hooks as well. These
    # classes are a compile gate only, never a second APK/service or runtime.
    activity_source=build/'activity';activity=activity_source/'org/xserver/android/XsActivity.java';activity.parent.mkdir(parents=True)
    shutil.copy2(ROOT/'.mdo/native-source'/NATIVE_INPUTS[0],activity)
    spec=importlib.util.spec_from_file_location('browser_use_android_overlay',ROOT/'.mdo/source'/HOST_SCRIPTS[0])
    overlay=importlib.util.module_from_spec(spec);spec.loader.exec_module(overlay);overlay.apply(activity_source)
    verify_mdo_manifest(overlay);verify_provider_manifest((ROOT/INPUTS[2]).read_text(encoding='utf-8'),PACKAGE)
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
        'production_capture_bridge_compiled':True,'production_dialog_bridge_compiled':True,'native_dialog_fixture_compiled':True,
        'production_popup_bridge_compiled':True,'native_popup_fixture_compiled':True,'popup_framework_verified':False,'popup_world_qualified':False,
        'production_chooser_bridge_compiled':True,'native_chooser_fixture_compiled':True,
        'production_manual_chooser_compiled':True,'manual_chooser_framework_verified':False,
        'production_shared_picker_uri_policy_compiled':True,'main_picker_framework_verified':False,
        'production_host_picker_read_routing_compiled':True,
        'chooser_framework_verified':False,'chooser_assignment_supported':False,
        'production_upload_staging_compiled':True,'production_upload_queue_compiled':True,'native_upload_fixture_compiled':True,
        'production_upload_uri_provider_compiled':True,'production_upload_uri_manifest_rendered':True,
        'native_upload_uri_fixture_compiled':True,'uri_proxy_framework_verified':False,'upload_uri_revocation_verified':False,
        'private_upload_cache_authority_verified':False,'upload_uri_assignment_verified':False}
    receipt_path.write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
