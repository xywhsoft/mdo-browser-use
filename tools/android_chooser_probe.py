"""Production chooser lifetime and Chrome callback contract, existing JDK only."""
import json
import subprocess
import sys

import dev
from android_popup_probe import MOCKS as POPUP_MOCKS


MOCKS={**POPUP_MOCKS,
 'android/net/Uri.java':'package android.net; public final class Uri {}',
 'android/webkit/ValueCallback.java':'package android.webkit; public interface ValueCallback<T>{void onReceiveValue(T value);}',
}
MOCKS['android/webkit/WebChromeClient.java']=MOCKS['android/webkit/WebChromeClient.java'].rsplit('}',1)[0]+'''
 public static abstract class FileChooserParams {
  public abstract int getMode();public abstract String[] getAcceptTypes();public abstract boolean isCaptureEnabled();
 }
 public boolean onShowFileChooser(WebView view,ValueCallback<android.net.Uri[]> result,FileChooserParams params){return false;}
}
'''


def main():
    classes=dev.ENV/'qa/android-chooser/classes';classes.mkdir(parents=True,exist_ok=True)
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    output=dev.ENV/'android-chooser-result.json'
    output.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserChoosers.java','host/android/MdoBrowserChooserTarget.java',
        'host/android/MdoBrowserDialogs.java','host/android/MdoBrowserDialogTarget.java','host/android/MdoBrowserPopups.java',
        'host/android/MdoBrowserWindowTarget.java','tests/fixtures/AndroidChooserContractTest.java','tests/fixtures/AndroidChooserTargetContractTest.java')]
    for name,text in MOCKS.items():
        path=classes.parent/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8',newline='\n');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(path) for path in sources]],
        cwd=dev.ROOT,check=True,capture_output=True,text=True)
    results=[]
    for name in ('AndroidChooserContractTest','AndroidChooserTargetContractTest'):
        run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.'+name],
            cwd=dev.ROOT,check=True,capture_output=True,text=True)
        results.append(json.loads(run.stdout.strip()))
    report={'passed':all(row['passed'] for row in results),'controller_checks':results[0]['checks'],'native_callback_checks':results[1]['checks'],
            'scope':'native-chooser-retention-cancellation-contract','production_java_target':8,'uri_assignment_available':False,'framework_verified':False}
    output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-2400:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
