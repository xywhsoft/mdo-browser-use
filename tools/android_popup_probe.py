"""Compile production popup ownership and Chrome transport with queue doubles.

Uses the existing JDK/QA directory; no framework or device result is implied.
"""
import json
import subprocess
import sys
import dev
from android_dialog_probe import MOCKS as DIALOG_MOCKS


MOCKS={**DIALOG_MOCKS,
 'android/os/Looper.java':'''package android.os;
public final class Looper {public static final Looper MAIN=new Looper();public static Looper current=MAIN;
public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return current;}}''',
 'android/os/Message.java':'''package android.os;
public final class Message {public Object obj;private Handler target;public boolean sent;
public Message(Handler target,Object obj){this.target=target;this.obj=obj;}public Handler getTarget(){return target;}}''',
 'android/os/Handler.java':'''package android.os;
import java.util.*;
public final class Handler {
 public static final Map<Runnable,Long> tasks=new LinkedHashMap<>();public static boolean failAt;
 public final List<Runnable> queue=new ArrayList<>();public boolean failSend,failPost;public int messages,processed;public Message last;
 private final Looper looper;public Handler(Looper looper){this.looper=looper;}
 public Looper getLooper(){return looper;}
 public boolean postAtTime(Runnable task,long time){if(failAt)return false;tasks.put(task,time);return true;}
 public void removeCallbacks(Runnable task){tasks.remove(task);}
 public boolean sendMessage(Message message){if(failSend)return false;if(message.sent)throw new IllegalStateException("Message already sent");message.sent=true;messages++;last=message;queue.add(()->processed++);return true;}
 public boolean post(Runnable task){if(failPost)return false;queue.add(task);return true;}
 public void drain(){while(!queue.isEmpty())queue.remove(0).run();}
 public static void reset(){tasks.clear();failAt=false;}
}''',
 'android/webkit/WebView.java':'''package android.webkit;
public final class WebView {public static final class WebViewTransport {public WebView child;public int sets;
public void setWebView(WebView view){child=view;sets++;}}}''',
 'android/webkit/WebChromeClient.java':DIALOG_MOCKS['android/webkit/WebChromeClient.java'].replace(
   'public class WebChromeClient {','public class WebChromeClient {\n public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,android.os.Message result){return false;}\n public void onCloseWindow(WebView view){}')}


def main():
    classes=dev.ENV/'qa/android-popup/classes';classes.mkdir(parents=True,exist_ok=True)
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    result=dev.ENV/'android-popup-result.json'
    result.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserPopups.java','host/android/MdoBrowserWindowTarget.java',
        'host/android/MdoBrowserDialogs.java','host/android/MdoBrowserDialogTarget.java',
        'tests/fixtures/AndroidPopupContractTest.java','tests/fixtures/AndroidPopupTargetContractTest.java')]
    for name,source in MOCKS.items():
        path=classes.parent/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source,encoding='utf-8',newline='\n');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(source) for source in sources]],
        cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=45)
    report={'passed':True,'scope':'native-popup-ownership-and-transport-contract','production_java_target':8,'framework_verified':False}
    for target,key in [('AndroidPopupContractTest','checks'),('AndroidPopupTargetContractTest','native_callback_checks')]:
        run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.'+target],
            cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
        report[key]=json.loads(run.stdout.strip())['checks']
    result.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-3200:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
