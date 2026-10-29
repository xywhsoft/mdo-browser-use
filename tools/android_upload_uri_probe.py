"""Real file/worker URI state plus production provider/callback with Android doubles."""
import hashlib
import json
import subprocess
import sys

import dev
from android_chooser_probe import MOCKS as CHOOSER_MOCKS

OUTPUT=dev.ENV/'android-upload-uri-result.json'
MOCKS={
 'android/net/Uri.java':'package android.net;public final class Uri {private final String value;private Uri(String value){this.value=value;}public static Uri parse(String value){return new Uri(value);}public String toString(){return value;}}',
 'android/content/Context.java':'''package android.content;public class Context {
 public String name="org.xleaves.mdo.browseruse.dev";public android.os.storage.StorageManager storage=new android.os.storage.StorageManager();
 public Context getApplicationContext(){return this;}public String getPackageName(){return name;}
 public <T>T getSystemService(Class<T> type){return type.cast(storage);}}''',
 'android/content/pm/ProviderInfo.java':'package android.content.pm;public class ProviderInfo {public boolean exported,grantUriPermissions;public String authority;}',
 'android/content/ContentValues.java':'package android.content;public class ContentValues {}',
 'android/content/ContentProvider.java':'''package android.content;import android.net.Uri;import android.database.Cursor;import android.os.*;import java.io.*;
 public abstract class ContentProvider {private Context context;public void attachInfo(Context value,android.content.pm.ProviderInfo info){context=value;onCreate();}public Context getContext(){return context;}
 public abstract boolean onCreate();public abstract Cursor query(Uri uri,String[] p,String s,String[] a,String o);public abstract String getType(Uri uri);
 public abstract ParcelFileDescriptor openFile(Uri u,String m)throws FileNotFoundException;public ParcelFileDescriptor openFile(Uri u,String m,CancellationSignal c)throws FileNotFoundException{return openFile(u,m);}
 public abstract Uri insert(Uri u,ContentValues v);public abstract int update(Uri u,ContentValues v,String s,String[] a);public abstract int delete(Uri u,String s,String[] a);}''',
 'android/database/Cursor.java':'package android.database;public interface Cursor extends AutoCloseable {boolean moveToFirst();int getColumnIndexOrThrow(String c);String getString(int c);long getLong(int c);void close();}',
 'android/database/MatrixCursor.java':'''package android.database;import java.util.*;public final class MatrixCursor implements Cursor {
 private final String[] columns;private final List<Object> row=new ArrayList<>();public MatrixCursor(String[] c,int n){columns=c;}
 public void addRow(Iterable<?> values){for(Object v:values)row.add(v);}public boolean moveToFirst(){return true;}
 public int getColumnIndexOrThrow(String c){for(int i=0;i<columns.length;i++)if(c.equals(columns[i]))return i;throw new IllegalArgumentException("Unknown column");}
 public String getString(int c){return (String)row.get(c);}public long getLong(int c){return ((Number)row.get(c)).longValue();}public void close(){}}''',
 'android/provider/OpenableColumns.java':'package android.provider;public interface OpenableColumns {String DISPLAY_NAME="_display_name",SIZE="_size";}',
 'android/os/Binder.java':'package android.os;public final class Binder {public static int uid=10000;public static int getCallingUid(){return uid;}}',
 'android/os/Process.java':'package android.os;public final class Process {public static int myUid(){return 10000;}}',
 'android/os/SystemClock.java':'package android.os;public final class SystemClock {public static volatile long now=1000;public static long elapsedRealtime(){return now;}}',
 'android/os/Looper.java':'package android.os;public final class Looper {private static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return null;}}',
 'android/os/HandlerThread.java':'''package android.os;public final class HandlerThread extends Thread {public static int starts;private final Looper looper=new Looper();
 public HandlerThread(String n){super(n);setDaemon(true);}public synchronized void start(){starts++;super.start();}public Looper getLooper(){return looper;}
 public void run(){try{new java.util.concurrent.CountDownLatch(1).await();}catch(InterruptedException end){}}}''',
 'android/os/Handler.java':'package android.os;public final class Handler {public static boolean failNext;private final Looper looper;public Handler(Looper l){if(failNext){failNext=false;throw new OutOfMemoryError("Synthetic handler allocation");}looper=l;}public Looper getLooper(){return looper;}}',
 'android/os/OperationCanceledException.java':'package android.os;public class OperationCanceledException extends RuntimeException {}',
 'android/os/CancellationSignal.java':'''package android.os;public final class CancellationSignal {public interface OnCancelListener {void onCancel();}private boolean cancelled;private OnCancelListener listener;public int failDetach;
 public synchronized boolean isCanceled(){return cancelled;}public void throwIfCanceled(){if(isCanceled())throw new OperationCanceledException();}
 public void setOnCancelListener(OnCancelListener value){boolean call;synchronized(this){if(value==null&&failDetach>0){failDetach--;throw new IllegalStateException("Synthetic listener detach");}listener=value;call=cancelled&&value!=null;}if(call)value.onCancel();}
 public void cancel(){OnCancelListener value;synchronized(this){if(cancelled)return;cancelled=true;value=listener;}if(value!=null)value.onCancel();}}''',
 'android/system/OsConstants.java':'package android.system;public final class OsConstants {public static final int EIO=5,EINVAL=22,EINTR=4,EBADF=9;}',
 'android/system/ErrnoException.java':'package android.system;public class ErrnoException extends Exception {public final int errno;public ErrnoException(String name,int value){super(name);errno=value;}}',
 'android/os/ProxyFileDescriptorCallback.java':'''package android.os;import android.system.*;public abstract class ProxyFileDescriptorCallback {
 public long onGetSize()throws ErrnoException {throw new ErrnoException("stub",9);}public int onRead(long o,int n,byte[] b)throws ErrnoException {throw new ErrnoException("stub",9);}
 public int onWrite(long o,int n,byte[] b)throws ErrnoException {throw new ErrnoException("stub",9);}public void onFsync()throws ErrnoException {throw new ErrnoException("stub",9);}public abstract void onRelease();}''',
 'android/os/ParcelFileDescriptor.java':'''package android.os;import java.io.*;public final class ParcelFileDescriptor implements AutoCloseable {
 public static final int MODE_READ_ONLY=0x10000000;public final ProxyFileDescriptorCallback callback;public final String behavior;public int closes;private boolean closed;
 public ParcelFileDescriptor(ProxyFileDescriptorCallback c,String b){callback=c;behavior=b;}
 public void close()throws IOException {closes++;if(closed)return;if("throw".equals(behavior))throw new IOException("Synthetic close failure");closed=true;callback.onRelease();if("release_then_throw".equals(behavior))throw new IOException("Synthetic acknowledged close failure");}}''',
 'android/os/storage/StorageManager.java':'''package android.os.storage;import android.os.*;import java.io.*;public final class StorageManager {
 public int opens;public String behavior="ok",closeBehavior="ok";public CancellationSignal signal;public ProxyFileDescriptorCallback last;public ParcelFileDescriptor fd;public Handler handler;
 public ParcelFileDescriptor openProxyFileDescriptor(int mode,ProxyFileDescriptorCallback cb,Handler h)throws IOException {
 opens++;last=cb;handler=h;if(mode!=ParcelFileDescriptor.MODE_READ_ONLY)throw new AssertionError("Writable proxy");
 if("throw".equals(behavior))throw new IOException("Synthetic native open failure");if("fatal".equals(behavior))throw new AssertionError("Synthetic native open Error");if("null".equals(behavior))return null;
 fd=new ParcelFileDescriptor(cb,closeBehavior);if("early_release".equals(behavior))cb.onRelease();if("cancel".equals(behavior)&&signal!=null)signal.cancel();return fd;}}''',
 'android/webkit/MimeTypeMap.java':'package android.webkit;public final class MimeTypeMap {private static final MimeTypeMap MAP=new MimeTypeMap();public static MimeTypeMap getSingleton(){return MAP;}public String getMimeTypeFromExtension(String e){return "txt".equals(e)?"text/plain":null;}}',
 'org/xserver/android/MdoBrowserUploadRuntime.java':'package org.xserver.android;final class MdoBrowserUploadRuntime {static MdoBrowserUploadQueue queue;static MdoBrowserUploadQueue get(android.content.Context c){return queue;}}',
}
# Keep the existing URI framework doubles and add the actual Chrome callback
# adapter. The original URI tests run with no UI looper; the picker fixture binds
# its exact UI Thread explicitly. No emulator/device qualification is implied.
for _name in ('android/os/Message.java','android/webkit/JsResult.java','android/webkit/JsPromptResult.java',
              'android/webkit/WebChromeClient.java','android/webkit/ValueCallback.java'):
    MOCKS[_name]=CHOOSER_MOCKS[_name]
MOCKS['android/webkit/WebView.java']=CHOOSER_MOCKS['android/webkit/WebView.java'].replace(
    'public android.content.Context getContext(){return new android.content.Context();}',
    'private final android.content.Context context;public WebView(){this(new android.content.Context());}public WebView(android.content.Context value){context=value;}public android.content.Context getContext(){return context;}')
MOCKS['android/os/Looper.java']='package android.os;public final class Looper {private static final Looper MAIN=new Looper();public static Thread ui;public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return Thread.currentThread()==ui?MAIN:null;}}'
MOCKS['android/os/SystemClock.java']=MOCKS['android/os/SystemClock.java'].replace('public static long elapsedRealtime()', 'public static long uptimeMillis(){return now;}public static long elapsedRealtime()')
MOCKS['android/os/Handler.java']=CHOOSER_MOCKS['android/os/Handler.java'].replace('public final class Handler {','public final class Handler {public static boolean failNext;').replace(
    'public Handler(Looper looper){this.looper=looper;}', 'public Handler(Looper looper){if(failNext){failNext=false;throw new OutOfMemoryError("Synthetic handler allocation");}this.looper=looper;}')


def main():
    OUTPUT.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    directory=dev.ENV/'qa/android-upload-uri';classes=directory/'classes';marker=directory/'.browser-use-owner.json'
    identity={'purpose':'mdo-browser-use/android-upload-uri'}
    if directory.resolve()!=directory.absolute() or classes.resolve().parent!=directory.resolve() or classes.is_symlink():raise RuntimeError('Linked URI output preserved')
    if directory.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):raise RuntimeError('Unknown URI output preserved')
    classes.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')
    root=directory/'files'
    if root.exists():raise RuntimeError('Interrupted URI fixture preserved')
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    names=('MdoBrowserUploadFiles','MdoBrowserUploadStore','MdoBrowserUploadQueue','MdoBrowserUploadUris','MdoBrowserUploadProxy','MdoBrowserUploadUriRuntime','MdoBrowserUploadProvider',
           'MdoBrowserChoosers','MdoBrowserChooserTarget','MdoBrowserPrivatePicker','MdoBrowserPrivatePickerReply','MdoBrowserDialogs','MdoBrowserDialogTarget','MdoBrowserPopups','MdoBrowserWindowTarget')
    sources=[dev.ROOT/'host/android'/f'{name}.java' for name in names]+[dev.ROOT/'tests/fixtures/AndroidUploadUriContractTest.java',dev.ROOT/'tests/fixtures/AndroidPrivatePickerContractTest.java']
    for name,value in MOCKS.items():
        path=directory/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value,encoding='utf-8');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(p) for p in sources]],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidUploadUriContractTest',linux(root)],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    result=json.loads(run.stdout.strip());result.update(scope='production-uri-state-provider-and-proxy-with-android-doubles',
        compiled_source_sha256=hashlib.sha256(b''.join(p.read_bytes() for p in sources)).hexdigest())
    picker=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidPrivatePickerContractTest',linux(root)],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=60)
    result['private_picker']=json.loads(picker.stdout.strip());result['passed']=result['passed'] and result['private_picker']['passed']
    OUTPUT.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as failure:
        OUTPUT.write_text('{"passed":false,"state":"failed","framework_verified":false,"uri_assignment_verified":false}\n')
        print((failure.stderr or '')[-2800:] if isinstance(failure,subprocess.CalledProcessError) else failure,file=sys.stderr);raise SystemExit(1)
