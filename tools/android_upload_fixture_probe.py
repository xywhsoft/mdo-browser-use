"""Execute the production device staging fixture with UI/Context doubles only."""
import hashlib
import json
import subprocess
import sys

import dev

OUTPUT=dev.ENV/'android-upload-fixture-result.json'
MOCKS={
 'android/content/Context.java':'package android.content;public final class Context {}',
 'android/os/Looper.java':'''package android.os;public final class Looper {
 private static final Thread OWNER=Thread.currentThread();private static final Looper MAIN=new Looper();
 public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return Thread.currentThread()==OWNER?MAIN:null;}
 }''',
 'android/os/SystemClock.java':'package android.os;public final class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000L;}}',
 'android/os/Handler.java':'''package android.os;import java.util.*;public final class Handler {
 private static final class Task {final Runnable run;final long when;Task(Runnable r,long w){run=r;when=w;}}
 private final List<Task> pending=new ArrayList<>();
 public synchronized boolean post(Runnable r){return postDelayed(r,0);}
 public synchronized boolean postDelayed(Runnable r,long ms){pending.add(new Task(r,SystemClock.elapsedRealtime()+ms));return true;}
 public synchronized void removeCallbacks(Runnable r){pending.removeIf(t->t.run==r);}
 public void drain(){if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Not UI");
  while(true){Task next=null;synchronized(this){for(Task t:pending)if(t.when<=SystemClock.elapsedRealtime()&&(next==null||t.when<next.when))next=t;if(next!=null)pending.remove(next);}
   if(next==null)return;next.run.run();}}
 }''',
 'org/xserver/android/MdoBrowserUploadRuntime.java':'package org.xserver.android;final class MdoBrowserUploadRuntime {static MdoBrowserUploadQueue queue;static MdoBrowserUploadQueue get(Object context){return queue;}}',
}


def main():
    OUTPUT.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    directory=dev.ENV/'qa/android-upload-fixture';classes=directory/'classes';marker=directory/'.browser-use-owner.json'
    identity={'purpose':'mdo-browser-use/android-upload-fixture'}
    if directory.resolve()!=directory.absolute() or classes.resolve().parent!=directory.resolve() or classes.is_symlink():raise RuntimeError('Linked upload fixture output preserved')
    if directory.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):raise RuntimeError('Unknown upload fixture output preserved')
    classes.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')
    root=directory/'files'
    if root.exists():raise RuntimeError('Interrupted upload fixture preserved')
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserUploadFiles.java','host/android/MdoBrowserUploadStore.java',
        'host/android/MdoBrowserUploadQueue.java','host/android/dev/BrowserUseUploadProbe.java','tests/fixtures/AndroidUploadFixtureContractTest.java')]
    for name,text in MOCKS.items():
        path=directory/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(path) for path in sources]],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidUploadFixtureContractTest',linux(root)],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    result=json.loads(run.stdout.strip());result.update(scope='production-device-fixture-with-ui-and-context-doubles',
        compiled_source_sha256=hashlib.sha256(b''.join(path.read_bytes() for path in sources)).hexdigest())
    OUTPUT.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as failure:
        OUTPUT.write_text('{"passed":false,"state":"failed","framework_verified":false,"native_cache_authority_verified":false}\n')
        print((failure.stderr or '')[-2200:] if isinstance(failure,subprocess.CalledProcessError) else failure,file=sys.stderr);raise SystemExit(1)
