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
    capability=(dev.ROOT/'host/android/browser-capabilities.inc.java').read_text(encoding='utf-8')
    overlay=dev.ROOT/'host/overlays.json'
    closes=[row['after'] for row in json.loads(overlay.read_text(encoding='utf-8'))['changes'] if row.get('path')=='tools/android/MdoBrowser.java' and row.get('before')=='if(web!=null){views.remove(key);root.removeView(web);web.destroy();}']
    if len(closes)!=1:raise RuntimeError('Expected the exact reviewed native close-command route')
    takeovers=[row['after'] for row in json.loads(overlay.read_text(encoding='utf-8'))['changes'] if row.get('path')=='tools/android/MdoBrowser.java' and row.get('before','').startswith('manualChooser.cancel(web);MdoBrowserDocument document=')]
    if len(takeovers)!=1:raise RuntimeError('Expected original network revocation before takeover cancellation')
    updates=[row for row in json.loads(overlay.read_text(encoding='utf-8'))['changes'] if row.get('path')=='tools/android/MdoBrowser.java' and row.get('before','').startswith('if("takeover".equals(command))networkRetire(key,web,')]
    if len(updates)!=1 or takeovers[0].count(updates[0]['before'])!=1:raise RuntimeError('Expected final orderly network takeover route')
    takeover=takeovers[0].replace(updates[0]['before'],updates[0]['after'])
    navigates=[row['after'] for row in json.loads(overlay.read_text(encoding='utf-8'))['changes'] if row.get('before')=='performNavigation(key,web,"navigate",target);']
    if len(navigates)!=1:raise RuntimeError('Expected one original native goto route')
    control_routes={}
    for action in ('back','forward','reload'):
        routes=[row['after'] for row in json.loads(overlay.read_text(encoding='utf-8'))['changes'] if row.get('before')==f'performNavigation(key,web,"{action}",null);']
        if len(routes)!=1:raise RuntimeError('Expected one exact original native control route: '+action)
        control_routes['@@NATIVE_'+action.upper()+'_ROUTE@@']=routes[0]
    fragments={
        '@@NATIVE_NAVIGATE_ROUTE@@':navigates[0],
        **control_routes,
        '@@DOCUMENT_NAVIGATION@@':document[document.index('    private void documentNavigation'):document.index('    private void documentLoaded')],
        '@@DOCUMENT_LOADED@@':document[document.index('    private void documentLoaded'):document.index('    private void blockDocuments')],
        '@@NAVIGATION@@':(dev.ROOT/'host/android/browser-navigation.inc.java').read_text(encoding='utf-8'),
        '@@NATIVE_NAVIGATION@@':(dev.ROOT/'host/android/browser-network-navigation.inc.java').read_text(encoding='utf-8'),
        '@@NETWORK_TAKEOVER_PREFIX@@':takeover,
        '@@NETWORK@@':(dev.ROOT/'host/android/browser-network.inc.java').read_text(encoding='utf-8'),
        '@@CLOSE_COMMAND@@':closes[0],
        '@@CREATION_GUARD@@':document[document.index('    private boolean nativeViewCleanupFailed'):document.index('    private final Map<String,MdoBrowserDocument>')],
        '@@CAPABILITIES@@':capability[capability.index('    JSONObject capabilities()'):capability.index('    void automationAdmission')],
        '@@BIND@@':document[document.index('    private void bindDocument'):document.index('    private boolean closeDocument')],
        '@@CLOSE_DOCUMENT@@':document[document.index('    private boolean closeDocument'):document.index('    private boolean closeAndDestroyDocument')],
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
        'android/os/Handler.java':'''package android.os; import java.util.*; public final class Handler {
            public static boolean fail;private static final Map<Runnable,Long> tasks=new LinkedHashMap<>();public Handler(Looper ignored){}
            public boolean postDelayed(Runnable task,long delay){if(fail)return false;tasks.put(task,SystemClock.elapsedRealtime()+delay);return true;}
            public void removeCallbacks(Runnable task){tasks.remove(task);}public static void reset(){tasks.clear();fail=false;SystemClock.now=100;}
            public static void tick(){for(Map.Entry<Runnable,Long> row:new ArrayList<>(tasks.entrySet()))if(row.getValue()<=SystemClock.now&&tasks.remove(row.getKey())!=null)row.getKey().run();}
            public static int pending(){return tasks.size();}}
        ''',
        'android/os/SystemClock.java':'package android.os; public final class SystemClock {public static long now=100;public static long uptimeMillis(){return now;}public static long elapsedRealtime(){return now;}}',
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
    result=json.loads(run.stdout)
    if not result.get('passed') or not result.get('native_control_ui_join') or result.get('framework_verified') is not False:
        raise RuntimeError('Original native history/reload UI admission contract did not pass')
    result['source_sha256']={path.relative_to(dev.ROOT).as_posix():hashlib.sha256(path.read_bytes()).hexdigest() for path in
        [*production,template,fixture,overlay,dev.ROOT/'tools/android_create_probe.py',dev.ROOT/'host/android/browser-network.inc.java',dev.ROOT/'host/android/browser-network-navigation.inc.java',dev.ROOT/'host/android/browser-navigation.inc.java',dev.ROOT/'host/android/browser-document.inc.java',dev.ROOT/'host/android/browser-create.inc.java',dev.ROOT/'host/android/browser-capabilities.inc.java']}
    (dev.ENV/'android-create-result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-3200:],file=sys.stderr);raise SystemExit(1)
    except (OSError,RuntimeError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
