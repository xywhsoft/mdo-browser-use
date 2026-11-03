"""Compile the production capture core and exercise deterministic lifetime faults.

Uses the existing WSL JDK and one small QA class output inside the deployment.
This is not Android drawing, bitmap encoding or real-device qualification.
"""
import json
import hashlib
import subprocess
import sys
import dev


def main():
    classes=dev.ENV/'qa/android-capture/classes';classes.mkdir(parents=True,exist_ok=True)
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    result=dev.ENV/'android-capture-result.json'
    result.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserCapture.java','host/android/MdoBrowserCaptureBuffer.java',
        'host/android/MdoBrowserCaptureRuntime.java','tests/fixtures/AndroidCaptureContractTest.java',
        'tests/fixtures/AndroidCaptureElapsedContractTest.java')]
    # Compile the actual production scheduler against explicit Handler/clock
    # peers, in the same existing capture output. No Android/framework proof.
    stubs={
        'SystemClock.java':'''package android.os; public final class SystemClock {
 public static long awake,elapsed;public static long uptimeMillis(){return awake;}public static long elapsedRealtime(){return elapsed;}}
''',
        'Looper.java':'''package android.os; public final class Looper {
 private static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;}}
''',
        'Handler.java':'''package android.os;
import java.util.LinkedHashMap;import java.util.Map;
public final class Handler {
 public static boolean failAt;private static final Map<Runnable,Long> tasks=new LinkedHashMap<>();
 public Handler(Looper looper){if(looper!=Looper.getMainLooper())throw new AssertionError();}
 public boolean postDelayed(Runnable task,long delay){tasks.put(task,SystemClock.uptimeMillis()+delay);return true;}
 public boolean postAtTime(Runnable task,long uptime){if(failAt)return false;tasks.put(task,uptime);return true;}
 public void removeCallbacks(Runnable task){tasks.remove(task);}
 public static void reset(){if(!tasks.isEmpty())throw new AssertionError("Prior capture timers retained");failAt=false;}
 public static int pending(){return tasks.size();}
 public static void drain(){for(int i=0;i<128;i++){Runnable next=null;long due=Long.MAX_VALUE;
  for(Map.Entry<Runnable,Long> row:tasks.entrySet())if(row.getValue()<=SystemClock.uptimeMillis()&&row.getValue()<due){next=row.getKey();due=row.getValue();}
  if(next==null)return;tasks.remove(next);next.run();}throw new AssertionError("Unbounded capture timer queue");}
}
'''}
    for name,text in stubs.items():
        path=classes.parent/'stubs/android/os'/name;path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(text,encoding='utf-8',newline='\n');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(source) for source in sources]],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=45)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidCaptureContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report=json.loads(run.stdout.strip());report.update({'scope':'native-capture-lifetime-contract','production_java_target':8,'framework_verified':False})
    elapsed=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidCaptureElapsedContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['elapsed_capture_checks']=json.loads(elapsed.stdout.strip())['checks']
    report['source_sha256']={p.relative_to(dev.ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in [*sources,dev.ROOT/'tools/android_capture_probe.py']}
    result.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-3200:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
