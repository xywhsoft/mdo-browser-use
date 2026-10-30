"""Exercise exact product create/bind/destroy fragments with controlled peers.

Uses one project-owned JVM output, no app/Home/engine copy or installed device.
The production world/profile contract has its separate foreign-loader gate.
"""
import hashlib
import json
import subprocess
import sys
import dev


def main():
    output=dev.ENV/'qa/android-create'
    if output.is_symlink() or not output.resolve().is_relative_to(dev.ENV.resolve()):
        raise RuntimeError('Linked native-create output preserved')
    marker=output/'.browser-use-owner.json';expected={'purpose':'mdo-browser-use/android-create'}
    if output.exists() and (not marker.is_file() or json.loads(marker.read_text(encoding='utf-8'))!=expected):
        raise RuntimeError('Unknown native-create output preserved')
    output.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(expected)+'\n',encoding='utf-8')
    template=dev.ROOT/'tests/fixtures/AndroidCreateHost.java.in'
    source=template.read_text(encoding='utf-8')
    document=(dev.ROOT/'host/android/browser-document.inc.java').read_text(encoding='utf-8')
    fragments={
        '@@BIND@@':document[document.index('    private void bindDocument'):document.index('    private boolean closeDocument')],
        '@@CLOSE@@':document[document.index('    private boolean closeAndDestroyDocument'):document.index('    private void documentNavigation')],
        '@@CREATE@@':(dev.ROOT/'host/android/browser-create.inc.java').read_text(encoding='utf-8'),
    }
    for key,value in fragments.items():
        if source.count(key)!=1:raise RuntimeError('Creation fixture injection marker changed')
        source=source.replace(key,value)
    generated=output/'AndroidCreateHost.java';generated.write_text(source,encoding='utf-8')
    mocks={
        'android/os/Build.java':'package android.os; public final class Build {public static class VERSION {public static int SDK_INT=35;}}',
        'android/os/Looper.java':'package android.os; public final class Looper {public static Looper getMainLooper(){return null;}}',
        'android/os/Handler.java':'package android.os; public final class Handler {public Handler(Looper ignored){}public boolean postDelayed(Runnable task,long delay){return true;}public void removeCallbacks(Runnable task){}}',
        'android/os/SystemClock.java':'package android.os; public final class SystemClock {public static long uptimeMillis(){return 100;}}',
        'android/net/http/SslError.java':'package android.net.http; public final class SslError {public int getPrimaryError(){return 1;}}',
    }
    paths=[]
    for name,text in mocks.items():
        path=output/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8');paths.append(path)
    production=[dev.ROOT/'host/android/MdoBrowserLifecycle.java',dev.ROOT/'host/android/MdoBrowserInput.java',dev.ROOT/'host/android/MdoBrowserWheel.java']
    fixture=dev.ROOT/'tests/fixtures/AndroidCreateContractTest.java';paths.extend([*production,generated,fixture])
    classes=output/'classes';classes.mkdir(exist_ok=True)
    def linux(path):
        path=path.resolve().as_posix();return '/mnt/'+path[0].lower()+path[2:]
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*map(linux,paths)],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=45)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidCreateContractTest'],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    result=json.loads(run.stdout);result['source_sha256']={path.relative_to(dev.ROOT).as_posix():hashlib.sha256(path.read_bytes()).hexdigest() for path in
        [*production,template,fixture,dev.ROOT/'host/android/browser-document.inc.java',dev.ROOT/'host/android/browser-create.inc.java']}
    (dev.ENV/'android-create-result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-3200:],file=sys.stderr);raise SystemExit(1)
    except (OSError,RuntimeError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
