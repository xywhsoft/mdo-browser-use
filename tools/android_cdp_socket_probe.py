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
    import setup_android_json_test
    json_sources=setup_android_json_test.prepare()
    subprocess.run([str(java/'bin/javac'),'--release','8','-d',str(classes),
                    *[str(ROOT/'host/android'/name) for name in ('MdoBrowserCdpNative.java','MdoBrowserCdpJson.java','MdoBrowserCdpSession.java',
                                                               'MdoBrowserCdpMarker.java','MdoBrowserCdpView.java','MdoBrowserCdpBinding.java',
                                                               'MdoBrowserCdpRegistry.java','MdoBrowserCdpWorker.java','MdoBrowserCdpFrames.java','MdoBrowserInput.java','MdoBrowserWheel.java','MdoBrowserLifecycle.java')],
                    *map(str,javas),*map(str,json_sources)],cwd=ROOT,check=True)
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
            'host/android/MdoBrowserCdpRegistry.java','host/android/MdoBrowserCdpWorker.java','host/android/MdoBrowserCdpFrames.java','host/android/MdoBrowserInput.java','host/android/MdoBrowserWheel.java','host/android/MdoBrowserLifecycle.java',
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
