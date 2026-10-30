"""Test the exact private discovery transport over real Linux kernel sockets.

One project-owned data/output directory; no browser/app/Home/source copy. This
is a transport gate, not evidence of an Android provider or CDP command pass.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]


def main():
    if os.name=='nt':
        script=Path(__file__).resolve().as_posix();script='/mnt/'+script[0].lower()+script[2:]
        subprocess.run(['wsl.exe','--exec','python3',script],cwd=ROOT,check=True);return
    import resource
    resource.setrlimit(resource.RLIMIT_CORE,(0,0))
    output=ROOT/'.mdo/qa/android-cdp-native';marker=output/'.browser-use-owner.json'
    expected={'purpose':'mdo-browser-use/android-cdp-native'}
    if output.is_symlink() or not output.resolve().is_relative_to((ROOT/'.mdo').resolve()):
        raise RuntimeError('Linked/escaped native transport output preserved')
    if output.exists() and (not marker.is_file() or json.loads(marker.read_text())!=expected):
        raise RuntimeError('Unknown native transport output preserved')
    output.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(expected)+'\n')
    fixture=ROOT/'tests/fixtures/android-cdp-socket.c';binary=output/'transport-test'
    setup=json.loads((ROOT/'.mdo/android-client-tools/receipt.json').read_text(encoding='utf-8'));java=Path(setup['java_home'])
    environment=dict(os.environ,ASAN_OPTIONS='detect_leaks=1:halt_on_error=1',UBSAN_OPTIONS='halt_on_error=1')
    # Reuse the pinned host's own XRT source and this fixed output directory.
    # This is a small protocol test executable, not another application/engine.
    include=ROOT/'.mdo/native-source/lib'
    flags=['-std=gnu11','-Wall','-Wextra','-Werror','-pthread','-O1','-g',
           '-fno-omit-frame-pointer','-fsanitize=address,undefined','-I'+str(include),
           '-I'+str(java/'include'),'-I'+str(java/'include/linux')]
    xrt=output/'xrt-websocket.o'
    subprocess.run(['gcc',*flags,'-c',str(ROOT/'tests/fixtures/android-cdp-xrt.c'),'-o',str(xrt)],cwd=ROOT,check=True)
    subprocess.run(['gcc',*flags,str(fixture),str(xrt),'-o',str(binary)],cwd=ROOT,check=True)
    result=json.loads(subprocess.check_output([str(binary)],cwd=ROOT,env=environment,text=True,timeout=120))
    if not result.get('passed') or result.get('allocations')!=result.get('releases'):
        raise RuntimeError('The native transport gate did not confirm balanced cleanup')
    websocket=output/'websocket-test'
    subprocess.run(['gcc',*flags,str(ROOT/'tests/fixtures/android-cdp-websocket.c'),str(xrt),'-o',str(websocket)],cwd=ROOT,check=True)
    wire=json.loads(subprocess.check_output([str(websocket)],cwd=ROOT,env=environment,text=True,timeout=120))
    if not wire.get('passed') or wire.get('allocations')!=wire.get('releases'):
        raise RuntimeError('The native WebSocket gate did not confirm balanced cleanup')
    result['websocket']=wire
    # Run the actual private Java facade through actual JNI on the existing
    # desktop JVM. Only Android's Looper identity is doubled. A small fixture
    # library supplies the local peer; there is no Android framework/provider.
    vmflags=['-std=gnu11','-Wall','-Wextra','-Werror','-pthread','-O1','-g','-fPIC',
             '-fsanitize=undefined','-I'+str(include),'-I'+str(java/'include'),'-I'+str(java/'include/linux')]
    vmxrt=output/'xrt-websocket-jvm.o'
    subprocess.run(['gcc',*vmflags,'-c',str(ROOT/'tests/fixtures/android-cdp-xrt.c'),'-o',str(vmxrt)],cwd=ROOT,check=True)
    subprocess.run(['gcc',*vmflags,'-DMDO_CDP_JVM_FIXTURE','-shared',str(ROOT/'tests/fixtures/android-cdp-websocket.c'),
                    str(vmxrt),'-o',str(output/'libxs.so')],cwd=ROOT,check=True)
    classes=output/'classes';classes.mkdir(exist_ok=True)
    javas=sorted((ROOT/'tests/fixtures/android-cdp-java').rglob('*.java'))
    template=ROOT/'tests/fixtures/android-cdp-java/ChooserContinuationHost.java.in'
    source=template.read_text(encoding='utf-8')
    document=(ROOT/'host/android/browser-document.inc.java').read_text(encoding='utf-8')
    document=document[document.index('    private boolean cdpEnvironmentCurrent'):document.index('    private void bindDocument')]
    chooser=(ROOT/'host/android/browser-native-chooser.inc.java').read_text(encoding='utf-8')
    chooser=chooser[:chooser.index('    private void cancelChooser')]
    overlay=json.loads((ROOT/'host/overlays.json').read_text(encoding='utf-8'))
    gates=[line for row in overlay['changes'] for line in row.get('after','').splitlines() if 'dialog_pending_or_unclean' in line]
    if len(gates)!=1 or 'chooserFramesAllowed' not in gates[0]:raise RuntimeError('Expected one reviewed chooser request gate')
    for key,value in {'@@DOCUMENT@@':document,'@@CHOOSER@@':chooser,'@@ROUTER@@':(ROOT/'host/android/browser-frame-router.inc.java').read_text(encoding='utf-8'),'@@DIALOG_GATE@@':gates[0]}.items():
        if source.count(key)!=1:raise RuntimeError('Chooser continuation fixture marker changed')
        source=source.replace(key,value)
    generated=output/'ChooserContinuationHost.java';generated.write_text(source,encoding='utf-8')
    upload_source=(ROOT/'tests/fixtures/android-cdp-java/UploadAssignmentHost.java.in').read_text(encoding='utf-8')
    capture_source=(ROOT/'host/android/browser-native-capture.inc.java').read_text(encoding='utf-8')
    capture_guard=capture_source[capture_source.index('    private boolean captureMutationReady'):capture_source.index('    private CaptureEntry captureEntry')]
    upload_fences=[line for row in overlay['changes'] for line in row.get('after','').splitlines() if 'if(uploadBusy(key,web)' in line]
    if len(upload_fences)!=1:raise RuntimeError('Expected the reviewed product upload fence')
    for key,value in {'@@DOCUMENT@@':document,'@@CHOOSER@@':chooser,'@@ROUTER@@':(ROOT/'host/android/browser-frame-router.inc.java').read_text(encoding='utf-8'),
                      '@@DIALOG_GATE@@':gates[0],'@@UPLOAD_FENCE@@':upload_fences[0],
                      '@@UPLOAD@@':(ROOT/'host/android/browser-native-upload.inc.java').read_text(encoding='utf-8'),
                      '@@ASSIGNMENT@@':(ROOT/'host/android/browser-upload-assignment.inc.java').read_text(encoding='utf-8'),'@@CAPTURE_FENCE@@':capture_guard}.items():
        if upload_source.count(key)!=1:raise RuntimeError('Product upload fixture marker changed')
        upload_source=upload_source.replace(key,value)
    upload_generated=output/'UploadAssignmentHost.java';upload_generated.write_text(upload_source,encoding='utf-8')
    import setup_android_json_test
    import update_android_owner_scripts
    update_android_owner_scripts.verify()
    import update_android_upload_broker
    update_android_upload_broker.verify()
    json_sources=setup_android_json_test.prepare()
    subprocess.run([str(java/'bin/javac'),'--release','8','-encoding','UTF-8','-d',str(classes),
                    *[str(ROOT/'host/android'/name) for name in ('MdoBrowserCdpNative.java','MdoBrowserCdpJson.java','MdoBrowserCdpSession.java',
                                                               'MdoBrowserCdpMarker.java','MdoBrowserCdpView.java','MdoBrowserCdpBinding.java',
                                                               'MdoBrowserCdpRegistry.java','MdoBrowserCdpWorker.java','MdoBrowserCdpFrames.java','MdoBrowserCdpWorlds.java',
                                                               'MdoBrowserCdpOwnerScripts.java','MdoBrowserCdpFrameRequest.java','MdoBrowserCdpFrameChain.java','MdoBrowserCdpInput.java','MdoBrowserCdpCapture.java','MdoBrowserCdpFileInput.java','MdoBrowserCdpChoosers.java','MdoBrowserCdpUploadScripts.java','MdoBrowserCdpUpload.java','MdoBrowserUploadCoordinator.java','MdoBrowserUploadStore.java','MdoBrowserUploadFiles.java','MdoBrowserUploadQueue.java','MdoBrowserChoosers.java','MdoBrowserPrivatePicker.java','MdoBrowserChannel.java','MdoBrowserCapture.java',
                                                               'MdoBrowserInput.java','MdoBrowserWheel.java','MdoBrowserLifecycle.java')],
                    str(generated),str(upload_generated),*map(str,javas),*map(str,json_sources)],cwd=ROOT,check=True)
    vm=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNativeProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not vm.get('passed') or not vm.get('real_jvm_jni'):
        raise RuntimeError('The actual JVM/JNI worker gate did not pass')
    result['jvm_worker']=vm
    dispatch=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpSessionProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not dispatch.get('passed') or dispatch.get('real_protocol_sessions')!=7:
        raise RuntimeError('The actual JNI protocol-session gate did not pass')
    result['jvm_dispatcher']=dispatch
    binding=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpBindingProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not binding.get('passed') or not binding.get('real_jvm_jni_json'):
        raise RuntimeError('The private native-view binding core gate did not pass')
    result['jvm_binding']=binding
    registry=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpRegistryProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not registry.get('passed') or not registry.get('native_ui_calls_doubled'):
        raise RuntimeError('The private CDP lifecycle registry gate did not pass')
    result['jvm_registry']=registry
    worker_dispatch=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpWorkerProbe'],cwd=ROOT,env=environment,text=True,timeout=40))
    if not worker_dispatch.get('passed') or not worker_dispatch.get('real_jvm_jni_json'):
        raise RuntimeError('The bounded private CDP worker dispatch gate did not pass')
    result['jvm_worker_dispatch']=worker_dispatch
    frames=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFramesProbe'],cwd=ROOT,env=environment,text=True,timeout=45))
    if not frames.get('passed') or not frames.get('real_jvm_jni_json'):
        raise RuntimeError('The private native frame graph gate did not pass')
    result['jvm_frames']=frames
    worlds=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpWorldsProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not worlds.get('passed') or not worlds.get('real_jvm_jni_json'):
        raise RuntimeError('The private isolated frame-world gate did not pass')
    result['jvm_worlds']=worlds
    owner=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpOwnerProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not owner.get('passed') or not owner.get('real_jvm_jni_json'):
        raise RuntimeError('The private frame-owner scope gate did not pass')
    result['jvm_owner']=owner
    request=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFrameRequestProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not request.get('passed') or not request.get('real_jvm_jni_json'):
        raise RuntimeError('The shared frame command grammar gate did not pass')
    result['jvm_frame_requests']=request
    input_guard=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpInputProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not input_guard.get('passed') or not input_guard.get('native_input_sequencer'):
        raise RuntimeError('The native frame-chain input gate did not pass')
    result['jvm_frame_input']=input_guard
    capture_guard=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpCaptureProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not capture_guard.get('passed') or not capture_guard.get('native_capture_transaction') or not capture_guard.get('real_jvm_jni_json'):
        raise RuntimeError('The native frame-chain capture transaction gate did not pass')
    result['jvm_frame_capture']=capture_guard
    file_input=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFileInputProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not file_input.get('passed') or not file_input.get('native_file_input_inspection') or file_input.get('files_delivered')!=0:
        raise RuntimeError('The native file input inspection gate did not pass')
    result['jvm_file_input']=file_input
    file_clear=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFileClearProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if not file_clear.get('passed') or not file_clear.get('native_file_clear') or not file_clear.get('real_jvm_jni_json') or file_clear.get('files_delivered')!=0 or file_clear.get('provider_qualified') is not False:
        raise RuntimeError('The native file clear operation gate did not pass')
    result['jvm_file_clear']=file_clear
    chooser=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpChooserProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not chooser.get('passed') or not chooser.get('real_jvm_jni_json') or not chooser.get('native_chooser_events') or chooser.get('files_delivered')!=0 or chooser.get('original_android_callback_joined') is not False:
        raise RuntimeError('The native chooser event/target gate did not pass')
    result['jvm_chooser']=chooser
    upload=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpUploadProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if not upload.get('passed') or not upload.get('real_jvm_jni_json') or not upload.get('fixed_native_picker_steps') or upload.get('android_callback_joined') is not False:
        raise RuntimeError('The original-world private upload worker gate did not pass')
    result['jvm_private_upload']=upload
    continuation=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpChooserContinuationProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if not continuation.get('passed') or not continuation.get('exact_host_fragments') or not continuation.get('read_only_chooser_continuation') or continuation.get('files_granted')!=0:
        raise RuntimeError('The exact native chooser continuation scope gate did not pass')
    result['jvm_chooser_continuation']=continuation
    coordinator=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpUploadCoordinatorProbe',str(output/'coordinator-files')],cwd=ROOT,env=environment,text=True,timeout=90))
    if not coordinator.get('passed') or not coordinator.get('real_jvm_jni_file_queue') or not coordinator.get('retained_bytes_verified') or coordinator.get('framework_verified') is not False:
        raise RuntimeError('The original native callback/file/CDP coordinator gate did not pass')
    result['jvm_upload_coordinator']=coordinator
    upload_host=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpUploadHostProbe',str(output/'upload-host-files')],cwd=ROOT,env=environment,text=True,timeout=120))
    if not upload_host.get('passed') or not upload_host.get('exact_product_fragments') or not upload_host.get('no_effect_replay') or upload_host.get('framework_verified') is not False:
        raise RuntimeError('The exact original product upload route gate did not pass')
    result['jvm_upload_host']=upload_host
    clear_host=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFileClearHostProbe',str(output/'clear-host-files')],cwd=ROOT,env=environment,text=True,timeout=120))
    if not clear_host.get('passed') or not clear_host.get('exact_product_fragments') or not clear_host.get('no_effect_replay') or clear_host.get('source_files_read')!=0 or clear_host.get('framework_verified') is not False:
        raise RuntimeError('The exact native product clear reservation/receipt gate did not pass')
    result['jvm_clear_host']=clear_host
    lock=json.loads((ROOT/'.mdo/native-source/tools/android/toolchain.lock.json').read_text())
    clang=ROOT/'.mdo/android-client-tools/sdk/ndk'/lock['ndk']/'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'
    subprocess.run([str(clang),f"--target=aarch64-linux-android{lock['api']}",'-std=gnu11','-Wall','-Wextra','-Werror','-O2','-fPIC',
        '-c',str(ROOT/'host/android/MdoBrowserCdpSocket.c'),'-o',str(output/'android-socket.o')],cwd=ROOT,check=True)
    subprocess.run([str(clang),f"--target=aarch64-linux-android{lock['api']}",'-std=gnu11','-Wall','-Wextra','-Werror','-O2','-fPIC',
        '-DXRT_MODULE_WEBSOCKET_HANDSHAKE','-DXRT_MODULE_WEBSOCKET_MESSAGE','-DXRT_MODULE_RANDOM_SECURE','-I'+str(include),
        '-c',str(ROOT/'host/android/MdoBrowserCdpWebSocket.c'),'-o',str(output/'android-websocket.o')],cwd=ROOT,check=True)
    inputs=('host/android/MdoBrowserCdpSocket.c','host/android/MdoBrowserCdpSocket.h','host/android/MdoBrowserCdpJni.inc.c',
            'host/android/MdoBrowserCdpWebSocket.c','host/android/MdoBrowserCdpWebSocket.h',
            'host/android/MdoBrowserCdpNative.java','host/android/MdoBrowserCdpJson.java','host/android/MdoBrowserCdpSession.java',
            'host/android/MdoBrowserCdpMarker.java','host/android/MdoBrowserCdpView.java','host/android/MdoBrowserCdpBinding.java',
            'host/android/MdoBrowserCdpRegistry.java','host/android/MdoBrowserCdpWorker.java','host/android/MdoBrowserCdpFrames.java','host/android/MdoBrowserCdpWorlds.java',
            'host/android/MdoBrowserCdpOwnerScripts.java','host/android/MdoBrowserCdpFrameRequest.java','host/android/MdoBrowserCdpFrameChain.java','host/android/MdoBrowserCdpInput.java','host/android/MdoBrowserCdpCapture.java','host/android/MdoBrowserCdpFileInput.java','host/android/MdoBrowserCdpChoosers.java','host/android/MdoBrowserCdpUploadScripts.java','host/android/MdoBrowserCdpUpload.java','host/android/MdoBrowserUploadCoordinator.java','host/android/MdoBrowserUploadStore.java','host/android/MdoBrowserUploadFiles.java','host/android/MdoBrowserUploadQueue.java','host/android/MdoBrowserChoosers.java','host/android/MdoBrowserPrivatePicker.java','host/android/MdoBrowserChannel.java','host/android/MdoBrowserCapture.java',
            'host/android/browser-document.inc.java','host/android/browser-native-capture.inc.java','host/android/browser-native-upload.inc.java','host/android/browser-upload-assignment.inc.java','tests/fixtures/android-cdp-java/UploadAssignmentHost.java.in','host/android/browser-native-chooser.inc.java','host/android/browser-frame-router.inc.java','host/overlays.json','tests/fixtures/android-cdp-java/ChooserContinuationHost.java.in',
            'host/native-frames.inc.hpp','tools/update_android_owner_scripts.py','host/upload-picker-broker.js','tools/update_android_upload_broker.py',
            'host/android/MdoBrowserInput.java','host/android/MdoBrowserWheel.java','host/android/MdoBrowserLifecycle.java',
            'host/android/json-test-sources.json','tools/setup_android_json_test.py','tests/fixtures/android-cdp-socket.c',
            'tests/fixtures/android-cdp-websocket.c','tests/fixtures/android-cdp-xrt.c',
            *[p.relative_to(ROOT).as_posix() for p in javas])
    result.update(source_sha256={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in inputs},
                  address_sanitizer=True,undefined_sanitizer=True,android_api_compiled=lock['api'],
                  android_device_qualified=False,provider_qualified=False,debugging_enabled=False,model_calls=0)
    (ROOT/'.mdo/android-cdp-socket-result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2),flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
