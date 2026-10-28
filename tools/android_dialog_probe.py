"""Compile the production dialog controller and exercise deterministic faults.

Uses the existing WSL JDK and one small QA output; no Android framework pass is
claimed and no installed app, WebView or Home is changed.
"""
import json
import subprocess
import sys
import dev


MOCKS={
    'android/os/Looper.java':'package android.os; public final class Looper {public static Looper getMainLooper(){return new Looper();}}',
    'android/os/SystemClock.java':'package android.os; public final class SystemClock {public static long time;public static long uptimeMillis(){return time;}}',
    'android/os/Handler.java':'''package android.os;
import java.util.*;
public final class Handler {
 public static final Map<Runnable,Long> tasks=new LinkedHashMap<>();public static boolean failAt;
 public Handler(Looper looper){}
 public boolean postAtTime(Runnable task,long time){if(failAt)return false;tasks.put(task,time);return true;}
 public void removeCallbacks(Runnable task){tasks.remove(task);}
 public static void reset(){tasks.clear();failAt=false;}
}''',
    'android/webkit/WebView.java':'package android.webkit; public final class WebView {}',
    'android/webkit/JsResult.java':'''package android.webkit;
public class JsResult {
 public int confirms,cancels;public boolean fail;public Runnable callback;
 public final void confirm(){confirms++;done();}public final void cancel(){cancels++;done();}
 private void done(){if(callback!=null)callback.run();if(fail)throw new IllegalStateException("Synthetic native acknowledgement loss");}
}''',
    'android/webkit/JsPromptResult.java':'''package android.webkit;
public class JsPromptResult extends JsResult {public String text;public void confirm(String value){text=value;confirm();}}''',
    'android/webkit/WebChromeClient.java':'''package android.webkit;
public class WebChromeClient {
 public boolean onJsAlert(WebView view,String url,String message,JsResult result){return false;}
 public boolean onJsConfirm(WebView view,String url,String message,JsResult result){return false;}
 public boolean onJsPrompt(WebView view,String url,String message,String initial,JsPromptResult result){return false;}
 public boolean onJsBeforeUnload(WebView view,String url,String message,JsResult result){return false;}
}''',
}


def main():
    classes=dev.ENV/'qa/android-dialog/classes';classes.mkdir(parents=True,exist_ok=True)
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    result=dev.ENV/'android-dialog-result.json'
    result.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserDialogs.java','host/android/MdoBrowserDialogTarget.java',
        'tests/fixtures/AndroidDialogContractTest.java','tests/fixtures/AndroidDialogTargetContractTest.java')]
    for name,source in MOCKS.items():
        path=classes.parent/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source,encoding='utf-8',newline='\n');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(source) for source in sources]],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=45)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidDialogContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report=json.loads(run.stdout.strip());report.update({'scope':'native-dialog-lifetime-contract','production_java_target':8,'framework_verified':False})
    native=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidDialogTargetContractTest'],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['native_callback_checks']=json.loads(native.stdout.strip())['checks']
    result.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-3200:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
