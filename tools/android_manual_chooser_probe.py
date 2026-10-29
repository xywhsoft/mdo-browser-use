"""Actual manual picker controller with Android Activity/Intent/permission doubles."""
import hashlib
import json
import subprocess
import sys

import dev
from android_chooser_probe import MOCKS as CHOOSER_MOCKS

OUTPUT=dev.ENV/'android-manual-chooser-result.json'
MOCKS={**CHOOSER_MOCKS,
 'android/net/Uri.java':'''package android.net;public final class Uri {private final String raw;private Uri(String s){raw=s;}public static Uri parse(String s){return new Uri(s);}
 public String getScheme(){return raw.split(":",2)[0];}public String getAuthority(){int at=raw.indexOf("://");if(at<0)return null;String rest=raw.substring(at+3);int slash=rest.indexOf('/');return slash<0?rest:rest.substring(0,slash);}public String toString(){return raw;}
 public boolean equals(Object other){return other instanceof Uri&&raw.equals(((Uri)other).raw);}public int hashCode(){return raw.hashCode();}}''',
 'android/os/Bundle.java':'package android.os;public final class Bundle {private final java.util.Map<String,Integer> values=new java.util.HashMap<>();public int getInt(String k,int d){return values.getOrDefault(k,d);}public void putInt(String k,int v){values.put(k,v);}}',
 'android/os/Process.java':'package android.os;public final class Process {public static int myUid(){return 10000;}public static int myPid(){return 123;}}',
 'android/content/pm/ProviderInfo.java':'package android.content.pm;public final class ProviderInfo {public ApplicationInfo applicationInfo=new ApplicationInfo();public static final class ApplicationInfo {public int uid;}}',
 'android/content/pm/PackageManager.java':'''package android.content.pm;public final class PackageManager {public static final int PERMISSION_GRANTED=0;
 public ProviderInfo resolveContentProvider(String a,int flags){if(a.equals("missing"))return null;ProviderInfo p=new ProviderInfo();if(a.equals("invalid"))p.applicationInfo=null;else p.applicationInfo.uid=a.equals("private")?10000:20000;return p;}}''',
 'android/content/ActivityNotFoundException.java':'package android.content;public final class ActivityNotFoundException extends RuntimeException {}',
 'android/content/Intent.java':'''package android.content;import android.net.Uri;public final class Intent {
 public static final String ACTION_OPEN_DOCUMENT="open_document",CATEGORY_OPENABLE="openable",EXTRA_ALLOW_MULTIPLE="multiple",EXTRA_MIME_TYPES="mimes";public static final int FLAG_GRANT_READ_URI_PERMISSION=1;
 public final String action;public String category,type;public boolean multiple;public String[] mimes;public Uri data;public ClipData clip;
 public Intent(String a){action=a;}public Intent addCategory(String v){category=v;return this;}public Intent setType(String v){type=v;return this;}
 public Intent putExtra(String k,boolean v){multiple=v;return this;}public Intent putExtra(String k,String[] v){mimes=v;return this;}public Uri getData(){return data;}public ClipData getClipData(){return clip;}}''',
 'android/content/ClipData.java':'package android.content;import android.net.Uri;public final class ClipData {public final Uri[] items;public ClipData(Uri... values){items=values;}public int getItemCount(){return items.length;}public Item getItemAt(int i){return new Item(items[i]);}public static final class Item {final Uri uri;Item(Uri u){uri=u;}public Uri getUri(){return uri;}}}',
 'android/app/Activity.java':'''package android.app;import android.content.*;import android.content.pm.*;import android.net.Uri;public final class Activity {
 public static final int RESULT_OK=-1;public int launches,code;public Intent intent;public String fault="none";public Runnable during;private final PackageManager pm=new PackageManager();
 public void startActivityForResult(Intent i,int c){launches++;intent=i;code=c;if(during!=null)during.run();if(fault.equals("missing"))throw new ActivityNotFoundException();if(fault.equals("uncertain"))throw new IllegalStateException("Uncertain platform launch");}
 public PackageManager getPackageManager(){return pm;}public int checkUriPermission(Uri u,int pid,int uid,int f){return "denied".equals(u.getAuthority())?-1:0;}}''',
}


def main():
    OUTPUT.write_text('{"passed":false,"state":"running","framework_verified":false}\n')
    classes=dev.ENV/'qa/android-manual-chooser/classes';classes.mkdir(parents=True,exist_ok=True)
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    sources=[dev.ROOT/'host/android/MdoBrowserManualChooser.java',dev.ROOT/'tests/fixtures/AndroidManualChooserContractTest.java']
    for name,value in MOCKS.items():
        path=classes.parent/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value,encoding='utf-8',newline='\n');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(p) for p in sources]],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidManualChooserContractTest'],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    result=json.loads(run.stdout.strip());result.update(scope='production-manual-chooser-with-android-activity-permission-doubles',framework_verified=False,uri_assignment_available=False,
        compiled_source_sha256=hashlib.sha256(b''.join(p.read_bytes() for p in sources)).hexdigest())
    OUTPUT.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as failure:
        OUTPUT.write_text('{"passed":false,"state":"failed","framework_verified":false}\n')
        print((failure.stderr or '')[-2200:] if isinstance(failure,subprocess.CalledProcessError) else failure,file=sys.stderr);raise SystemExit(1)
