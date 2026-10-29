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
    subprocess.run(['gcc','-std=gnu11','-Wall','-Wextra','-Werror','-pthread','-O1','-g',
        '-fno-omit-frame-pointer','-fsanitize=address,undefined','-I'+str(java/'include'),'-I'+str(java/'include/linux'),
        str(fixture),'-o',str(binary)],cwd=ROOT,check=True)
    environment=dict(os.environ,ASAN_OPTIONS='detect_leaks=1:halt_on_error=1',UBSAN_OPTIONS='halt_on_error=1')
    text=subprocess.check_output([str(binary)],cwd=ROOT,env=environment,text=True,timeout=120)
    result=json.loads(text)
    if not result.get('passed') or result.get('allocations')!=result.get('releases'):
        raise RuntimeError('The native transport gate did not confirm balanced cleanup')
    # Reuse the pinned host's own XRT source and this fixed output directory.
    # This is a small protocol test executable, not another application/engine.
    include=ROOT/'.mdo/native-source/lib'
    flags=['-std=gnu11','-Wall','-Wextra','-Werror','-pthread','-O1','-g',
           '-fno-omit-frame-pointer','-fsanitize=address,undefined','-I'+str(include)]
    xrt=output/'xrt-websocket.o'
    subprocess.run(['gcc',*flags,'-c',str(ROOT/'tests/fixtures/android-cdp-xrt.c'),'-o',str(xrt)],cwd=ROOT,check=True)
    websocket=output/'websocket-test'
    subprocess.run(['gcc',*flags,str(ROOT/'tests/fixtures/android-cdp-websocket.c'),str(xrt),'-o',str(websocket)],cwd=ROOT,check=True)
    wire=json.loads(subprocess.check_output([str(websocket)],cwd=ROOT,env=environment,text=True,timeout=120))
    if not wire.get('passed') or wire.get('allocations')!=wire.get('releases'):
        raise RuntimeError('The native WebSocket gate did not confirm balanced cleanup')
    result['websocket']=wire
    lock=json.loads((ROOT/'.mdo/native-source/tools/android/toolchain.lock.json').read_text())
    clang=ROOT/'.mdo/android-client-tools/sdk/ndk'/lock['ndk']/'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'
    subprocess.run([str(clang),f"--target=aarch64-linux-android{lock['api']}",'-std=gnu11','-Wall','-Wextra','-Werror','-O2','-fPIC',
        '-c',str(ROOT/'host/android/MdoBrowserCdpSocket.c'),'-o',str(output/'android-socket.o')],cwd=ROOT,check=True)
    subprocess.run([str(clang),f"--target=aarch64-linux-android{lock['api']}",'-std=gnu11','-Wall','-Wextra','-Werror','-O2','-fPIC',
        '-DXRT_MODULE_WEBSOCKET_HANDSHAKE','-DXRT_MODULE_WEBSOCKET_MESSAGE','-DXRT_MODULE_RANDOM_SECURE','-I'+str(include),
        '-c',str(ROOT/'host/android/MdoBrowserCdpWebSocket.c'),'-o',str(output/'android-websocket.o')],cwd=ROOT,check=True)
    inputs=('host/android/MdoBrowserCdpSocket.c','host/android/MdoBrowserCdpSocket.h','host/android/MdoBrowserCdpJni.inc.c',
            'host/android/MdoBrowserCdpWebSocket.c','host/android/MdoBrowserCdpWebSocket.h',
            'tests/fixtures/android-cdp-socket.c','tests/fixtures/android-cdp-websocket.c','tests/fixtures/android-cdp-xrt.c')
    result.update(source_sha256={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in inputs},
                  address_sanitizer=True,undefined_sanitizer=True,android_api_compiled=lock['api'],
                  android_device_qualified=False,provider_qualified=False,debugging_enabled=False,model_calls=0)
    (ROOT/'.mdo/android-cdp-socket-result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2),flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
