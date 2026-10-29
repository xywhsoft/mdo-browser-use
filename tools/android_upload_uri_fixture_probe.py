"""Run the exact device URI fixture with Android descriptor/Resolver doubles.

Real staging files, leases, worker and production Provider/proxy are retained.
This exercises fixture outcomes and teardown, never Android/FUSE qualification.
"""
import hashlib
import json
import subprocess
import sys

import dev
from android_upload_uri_probe import MOCKS as URI_MOCKS

OUTPUT=dev.ENV/'android-upload-uri-fixture-result.json'
MOCKS={**URI_MOCKS,
 'android/os/SystemClock.java':'package android.os;public final class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000L;}}',
 'android/os/Looper.java':'''package android.os;public final class Looper {
 private static final Thread OWNER=Thread.currentThread();private static final Looper MAIN=new Looper();
 public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return Thread.currentThread()==OWNER?MAIN:null;}}''',
 'android/os/Handler.java':'''package android.os;import java.util.concurrent.*;public final class Handler {
 private final ConcurrentLinkedQueue<Runnable> pending=new ConcurrentLinkedQueue<>();private final Looper looper;
 public Handler(){this(Looper.getMainLooper());}public Handler(Looper l){looper=l;}public Looper getLooper(){return looper;}
 public boolean post(Runnable task){pending.add(task);return true;}
 public void drain(){if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Not UI");Runnable next;while((next=pending.poll())!=null)next.run();}}''',
 'android/content/Context.java':'''package android.content;public final class Context {
 public final android.os.storage.StorageManager storage=new android.os.storage.StorageManager();public ContentResolver resolver;
 public Context getApplicationContext(){return this;}public String getPackageName(){return "org.xleaves.mdo.browseruse.dev";}
 public ContentResolver getContentResolver(){return resolver;}public <T>T getSystemService(Class<T> type){return type.cast(storage);}}''',
 'android/content/ContentResolver.java':'''package android.content;import android.net.Uri;import android.database.Cursor;import android.os.*;import java.io.*;
 public final class ContentResolver {private final ContentProvider provider;public static boolean block;public static final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);
 public ContentResolver(ContentProvider p){provider=p;}
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){return provider.query(u,p,s,a,o);}public String getType(Uri u){return provider.getType(u);}
 public ParcelFileDescriptor openFileDescriptor(Uri u,String m,CancellationSignal c)throws FileNotFoundException {
 if(block){entered.countDown();while(!c.isCanceled())try{Thread.sleep(5);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new OperationCanceledException();}}
 return provider.openFile(u,m,c);}}''',
 'android/system/OsConstants.java':'package android.system;public final class OsConstants {public static final int EIO=5,EINVAL=22,EINTR=4,EBADF=9,SEEK_SET=0;}',
 'android/system/Os.java':'''package android.system;import java.io.*;public final class Os {
 public static long lseek(FileDescriptor fd,long n,int whence)throws ErrnoException {android.os.ParcelFileDescriptor value=android.os.ParcelFileDescriptor.lookup(fd);if(value==null||whence!=OsConstants.SEEK_SET||n<0)throw new ErrnoException("seek",9);value.position=n;return n;}}''',
 'android/os/ParcelFileDescriptor.java':'''package android.os;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
 public final class ParcelFileDescriptor implements AutoCloseable {
 public static final int MODE_READ_ONLY=0x10000000;private static final Map<FileDescriptor,ParcelFileDescriptor> VALUES=new IdentityHashMap<>();
 private static final List<ParcelFileDescriptor> RETAINED=new ArrayList<>();public static volatile String fault="none";
 public static final CountDownLatch reading=new CountDownLatch(1),resume=new CountDownLatch(1);public static final AtomicInteger active=new AtomicInteger();
 public final ProxyFileDescriptorCallback callback;public final String behavior;public final FileDescriptor fd=new FileDescriptor();public long position;private boolean closed;
 public ParcelFileDescriptor(ProxyFileDescriptorCallback c,String b){callback=c;behavior=b;synchronized(VALUES){VALUES.put(fd,this);}}
 public static ParcelFileDescriptor lookup(FileDescriptor fd){synchronized(VALUES){return VALUES.get(fd);}}
 public FileDescriptor getFileDescriptor(){return fd;}public long getStatSize(){try{return callback.onGetSize();}catch(android.system.ErrnoException e){return -1;}}
 public synchronized void close()throws IOException {if(closed)return;closed=true;synchronized(VALUES){VALUES.remove(fd);}if("no_ack".equals(fault)){synchronized(RETAINED){RETAINED.add(this);}}else callback.onRelease();}
 public static void acknowledge(){synchronized(RETAINED){for(ParcelFileDescriptor p:RETAINED)p.callback.onRelease();RETAINED.clear();}}
 public static int openCount(){synchronized(VALUES){return VALUES.size();}}
 public static final class AutoCloseInputStream extends InputStream {private final ParcelFileDescriptor owner;
 public AutoCloseInputStream(ParcelFileDescriptor p){owner=p;}public int read()throws IOException {byte[] b=new byte[1];int n=read(b,0,1);return n<0?-1:b[0]&255;}
 public int read(byte[] b,int o,int n)throws IOException {if(n==0)return 0;active.incrementAndGet();try{
 if("cancel_read".equals(fault)){reading.countDown();try{if(!resume.await(5,TimeUnit.SECONDS))throw new IOException("fixture wait");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("interrupted");}}
 if(owner.closed)throw new IOException("closed");byte[] bytes=new byte[n];int used=owner.callback.onRead(owner.position,n,bytes);if(used==0)return -1;
 if("corrupt".equals(fault))bytes[0]^=1;System.arraycopy(bytes,0,b,o,used);owner.position+=used;return used;
 }catch(android.system.ErrnoException e){throw new IOException("proxy read denied");}finally{active.decrementAndGet();}}
 public void close()throws IOException {owner.close();}}
 }''',
}


def main():
    OUTPUT.write_text('{"passed":false,"state":"running","framework_verified":false}\n',encoding='utf-8')
    directory=dev.ENV/'qa/android-upload-uri-fixture';classes=directory/'classes';marker=directory/'.browser-use-owner.json'
    identity={'purpose':'mdo-browser-use/android-upload-uri-fixture'}
    if directory.resolve()!=directory.absolute() or classes.resolve().parent!=directory.resolve() or classes.is_symlink():raise RuntimeError('Linked URI fixture output preserved')
    if directory.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):raise RuntimeError('Unknown URI fixture output preserved')
    classes.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')
    root=directory/'files'
    if root.exists():raise RuntimeError('Interrupted URI device fixture preserved')
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    names=('MdoBrowserUploadFiles','MdoBrowserUploadStore','MdoBrowserUploadQueue','MdoBrowserUploadUris','MdoBrowserUploadProxy','MdoBrowserUploadUriRuntime','MdoBrowserUploadProvider')
    sources=[dev.ROOT/'host/android'/f'{name}.java' for name in names]+[dev.ROOT/'host/android/dev/BrowserUseUploadUriProbe.java',dev.ROOT/'tests/fixtures/AndroidUploadUriFixtureContractTest.java']
    for name,value in MOCKS.items():
        path=directory/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value,encoding='utf-8');sources.append(path)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-encoding','UTF-8','-d',linux(classes),*[linux(p) for p in sources]],cwd=dev.ROOT,check=True,capture_output=True,text=True)
    results=[]
    for mode in ('success','corrupt','no_ack','cancel_before','cancel_read','resolver_cancel'):
        run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidUploadUriFixtureContractTest',linux(root),mode],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=20)
        results.append(json.loads(run.stdout.strip()))
    result={'passed':all(r['passed'] for r in results),'scenarios':results,'checks':sum(r['checks'] for r in results),
        'scope':'production-device-uri-fixture-with-android-resolver-fd-looper-doubles',
        'framework_verified':False,'native_cache_authority_verified':False,'uri_assignment_verified':False,
        'compiled_source_sha256':hashlib.sha256(b''.join(p.read_bytes() for p in sources)).hexdigest()}
    OUTPUT.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as failure:
        OUTPUT.write_text('{"passed":false,"state":"failed","framework_verified":false,"uri_assignment_verified":false}\n')
        print((failure.stderr or '')[-2200:] if isinstance(failure,subprocess.CalledProcessError) else failure,file=sys.stderr);raise SystemExit(1)
