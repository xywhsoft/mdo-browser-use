"""Exercise the actual native upload fragment with real worker/file behavior."""
import hashlib
import json
import subprocess
import sys

import dev

OUTPUT=dev.ENV/'android-upload-ui-result.json'
MOCKS={
 'android/webkit/WebView.java':'package android.webkit; public final class WebView {public String getUrl(){return "http://127.0.0.1/";}}',
 'android/webkit/WebMessagePort.java':'package android.webkit; public final class WebMessagePort {}',
 'android/os/SystemClock.java':'package android.os; public final class SystemClock {public static long elapsedRealtime(){return 1000;}}',
 'org/json/JSONException.java':'package org.json; public class JSONException extends Exception {public JSONException(String text){super(text);}}',
 'org/json/JSONObject.java':'''package org.json; import java.util.*;
public class JSONObject {private final Map<String,Object> data=new LinkedHashMap<>();
 public JSONObject put(String key,Object value)throws JSONException {data.put(key,value);return this;}
 public Object get(String key)throws JSONException {if(!data.containsKey(key))throw new JSONException("Missing field");return data.get(key);}
 public Object opt(String key){return data.get(key);}public boolean has(String key){return data.containsKey(key);}
 public String getString(String key)throws JSONException {return String.valueOf(get(key));}
 public JSONArray getJSONArray(String key)throws JSONException {Object v=get(key);if(!(v instanceof JSONArray))throw new JSONException("Expected array");return (JSONArray)v;}
 public Iterator<String> keys(){return data.keySet().iterator();}
}''',
 'org/json/JSONArray.java':'''package org.json;import java.util.*;public class JSONArray {
 private final List<Object> values=new ArrayList<>();public JSONArray put(Object value){values.add(value);return this;}
 public int length(){return values.size();}public JSONObject getJSONObject(int index)throws JSONException {Object value=values.get(index);if(!(value instanceof JSONObject))throw new JSONException("Expected object");return (JSONObject)value;}
}''',
 'org/xserver/android/MdoBrowserUploadRuntime.java':'package org.xserver.android;final class MdoBrowserUploadRuntime {static MdoBrowserUploadQueue queue;static MdoBrowserUploadQueue get(Object context){return queue;}}',
}


def main():
    OUTPUT.write_text('{"passed":false,"state":"running","framework_verified":false}\n')
    directory=dev.ENV/'qa/android-upload-ui';classes=directory/'classes';marker=directory/'.browser-use-owner.json'
    identity={'purpose':'mdo-browser-use/android-upload-ui'}
    if directory.resolve()!=directory.absolute() or classes.resolve().parent!=directory.resolve() or classes.is_symlink():raise RuntimeError('Linked upload UI output preserved')
    if directory.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):raise RuntimeError('Unknown upload UI output preserved')
    classes.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')
    root=directory/'files'
    if root.exists():raise RuntimeError('Interrupted native upload UI fixture preserved')
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    sources=[dev.ROOT/name for name in ('host/android/MdoBrowserUploadFiles.java','host/android/MdoBrowserUploadStore.java',
        'host/android/MdoBrowserUploadQueue.java','host/android/MdoBrowserChannel.java')]
    harness=(dev.ROOT/'tests/fixtures/AndroidUploadUiContractTest.java').read_text(encoding='utf-8')
    fragment=(dev.ROOT/'host/android/browser-native-upload.inc.java').read_text(encoding='utf-8')
    if harness.count('// NATIVE_UPLOAD_FRAGMENT')!=1:raise RuntimeError('Native upload harness insertion changed')
    generated=directory/'src/org/xserver/android/AndroidUploadUiContractTest.java';generated.parent.mkdir(parents=True,exist_ok=True)
    generated.write_text(harness.replace('// NATIVE_UPLOAD_FRAGMENT',fragment),encoding='utf-8');sources.append(generated)
    for name,text in MOCKS.items():
        path=directory/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(path) for path in sources]],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidUploadUiContractTest',linux(root)],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    result=json.loads(run.stdout.strip());result.update(scope='production-upload-fragment-with-ui-and-json-doubles',
        compiled_source_sha256=hashlib.sha256(b''.join(path.read_bytes() for path in sources)).hexdigest())
    OUTPUT.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as failure:
        OUTPUT.write_text('{"passed":false,"state":"failed","framework_verified":false}\n')
        print((failure.stderr or '')[-2200:] if isinstance(failure,subprocess.CalledProcessError) else failure,file=sys.stderr);raise SystemExit(1)
