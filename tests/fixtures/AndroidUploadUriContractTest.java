package org.xserver.android;

import android.content.Context;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.OsConstants;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Android types/system calls are doubles. File channels, worker, original
 * leases and production URI/provider/proxy logic are real. No URI is logged. */
public final class AndroidUploadUriContractTest {
    interface Checked {void run()throws Exception;}
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("URI check "+checks+" failed");}
    private static void refused(String code,Checked action)throws Exception {try{action.run();throw new AssertionError("Expected "+code);}catch(MdoBrowserUploadFiles.Failure failure){check(code.equals(failure.code));}}
    private static void errno(int code,Checked action)throws Exception {try{action.run();throw new AssertionError("Expected errno");}catch(ErrnoException failure){check(failure.errno==code);}}
    private static void absent(Checked action)throws Exception {try{action.run();throw new AssertionError("Expected absent URI");}catch(FileNotFoundException expected){check(expected.getMessage().equals("Upload resource unavailable"));}}
    private static void denied(Checked action)throws Exception {try{action.run();throw new AssertionError("Expected private boundary");}catch(SecurityException expected){checks++;}}
    private static String sha(byte[] bytes)throws Exception {StringBuilder result=new StringBuilder(64);for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();}
    private static byte[] data(){byte[] bytes=new byte[150001];for(int at=0;at<bytes.length;at++)bytes[at]=(byte)(at*17+9);return bytes;}
    private static final class Fixture implements AutoCloseable {
        final Path root;final ConcurrentLinkedQueue<Runnable> ui=new ConcurrentLinkedQueue<>();final AtomicReference<Runnable> readHook=new AtomicReference<>();final MdoBrowserUploadQueue queue;
        MdoBrowserUploadStore.Scope scope;String ticket;MdoBrowserUploadStore.ReadLease lease;int epoch;
        Fixture(Path root)throws Exception {
            this.root=root;Files.createDirectory(root);
            queue=new MdoBrowserUploadQueue(()->{MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root);try{files.acquireCacheLease();return files;}catch(Exception|Error error){try{files.close();}catch(Exception ignored){}throw error;}},
                task->{ui.add(task);return true;},SystemClock::elapsedRealtime,()->{Runnable hook=readHook.get();if(hook!=null)hook.run();return true;});
        }
        MdoBrowserUploadQueue.Outcome call(MdoBrowserUploadQueue.Request request)throws Exception {
            AtomicReference<MdoBrowserUploadQueue.Outcome> result=new AtomicReference<>();check(queue.submit(scope,request,result::set));long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(result.get()==null&&System.nanoTime()<deadline){Runnable next=ui.poll();if(next!=null)next.run();else Thread.sleep(5);}check(result.get()!=null);return result.get();
        }
        void prepare(byte[] bytes)throws Exception {
            scope=new MdoBrowserUploadStore.Scope(++epoch);MdoBrowserUploadStore.Spec[] specs={new MdoBrowserUploadStore.Spec("fixture-\u4e2d.txt",bytes.length,sha(bytes))};
            MdoBrowserUploadQueue.Outcome start=call(MdoBrowserUploadQueue.Request.begin("uri-"+UUID.randomUUID(),specs,SystemClock.now+5000));check(start.ok());ticket=start.value.ticket;
            for(int at=0;at<bytes.length;at+=65536)check(call(MdoBrowserUploadQueue.Request.chunk(ticket,0,at,Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,at,Math.min(bytes.length,at+65536))))).ok());
            check(call(MdoBrowserUploadQueue.Request.ticket("seal",ticket)).value.sealed);lease=call(MdoBrowserUploadQueue.Request.access(ticket,0)).reader;check(lease!=null);
        }
        void retire()throws Exception {
            readHook.set(null);if(ticket!=null){
                if(scope.live())check(call(MdoBrowserUploadQueue.Request.ticket("cancel",ticket)).value.cleanupConfirmed);
                else{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(queue.activeFiles()!=0&&System.nanoTime()<deadline)Thread.sleep(5);}
                scope.cancel();ticket=null;
            }check(queue.activeFiles()==0&&queue.reservedBytes()==0&&queue.unfinishedReads()==0);
        }
        @Override public void close()throws Exception {retire();queue.close();check(queue.awaitStopped(5000));Runnable next;while((next=ui.poll())!=null)next.run();check(queue.unfinishedDeliveries()==0);Files.delete(root.resolve(MdoBrowserUploadFiles.OWNER_FILE));Files.delete(root);}
    }
    private static MdoBrowserUploadUris.Session opened(MdoBrowserUploadUris state,String token)throws Exception {MdoBrowserUploadUris.Session session=state.reserve(token);check(session.enter());check(!session.enter());check(session.returned()&&session.returned());return session;}
    private static void stateRanges(Fixture f)throws Exception {
        byte[] bytes=data();f.prepare(bytes);MdoBrowserUploadUris state=new MdoBrowserUploadUris(f.queue,SystemClock::elapsedRealtime);String token=state.publish(f.lease);
        check(MdoBrowserUploadUris.token(token)&&token.equals(state.publish(f.lease))&&state.grants()==1);
        check(state.info(token).size==bytes.length&&state.info(token).name.equals("fixture-\u4e2d.txt"));
        MdoBrowserUploadUris.Session reserved=state.reserve(token);refused("upload_uri_revoked",reserved::size);reserved.beforeOpenFailure();check(state.descriptors()==0);
        MdoBrowserUploadUris.Session session=opened(state,token);MdoBrowserUploadProxy proxy=new MdoBrowserUploadProxy(session);check(proxy.onGetSize()==bytes.length);
        byte[] output=new byte[131072];check(proxy.onRead(0,output.length,output)==output.length&&Arrays.equals(output,Arrays.copyOf(bytes,output.length)));
        Arrays.fill(output,(byte)85);int tail=proxy.onRead(131072,output.length,output);check(tail==bytes.length-131072&&Arrays.equals(Arrays.copyOf(output,tail),Arrays.copyOfRange(bytes,131072,bytes.length)));
        check(proxy.onRead(bytes.length,1,output)==0&&proxy.onRead(Long.MAX_VALUE,1,output)==0);
        byte[] cross=new byte[10];check(proxy.onRead(65534,10,cross)==10&&Arrays.equals(cross,Arrays.copyOfRange(bytes,65534,65544)));
        errno(OsConstants.EINVAL,()->proxy.onRead(-1,1,output));errno(OsConstants.EINVAL,()->proxy.onRead(0,output.length+1,output));errno(OsConstants.EBADF,()->proxy.onWrite(0,1,output));errno(OsConstants.EBADF,proxy::onFsync);
        proxy.onRelease();proxy.onRelease();check(state.descriptors()==0&&state.grants()==1&&f.lease.live(SystemClock.now));errno(OsConstants.EIO,proxy::onGetSize);
        MdoBrowserUploadUris.Session next=opened(state,token);proxy.onRelease();check(state.descriptors()==1);check(next.read(0,1,output,1000)==1&&output[0]==bytes[0]);next.released();
        state.revoke(token);check(state.grants()==0&&!f.lease.live(SystemClock.now));refused("upload_read_lease_revoked",()->state.publish(f.lease));f.retire();state.close();
    }
    private static void boundsAndFailure(Fixture f)throws Exception {
        f.prepare(new byte[]{1});MdoBrowserUploadUris state=new MdoBrowserUploadUris(f.queue,SystemClock::elapsedRealtime);String token=state.publish(f.lease);
        MdoBrowserUploadUris.Session[] sessions=new MdoBrowserUploadUris.Session[MdoBrowserUploadUris.DESCRIPTORS];
        for(int at=0;at<sessions.length;at++)sessions[at]=state.reserve(token);check(state.descriptors()==64);final MdoBrowserUploadUris full=state;refused("upload_proxy_limit",()->full.reserve(token));
        for(int at=0;at<32;at++){check(sessions[at].enter());check(sessions[at].returned());}state.close();check(state.grants()==0&&state.descriptors()==32);
        for(MdoBrowserUploadUris.Session value:sessions)value.released();check(state.descriptors()==0);f.retire();
        f.prepare(new byte[]{2});state=new MdoBrowserUploadUris(f.queue,SystemClock::elapsedRealtime);String id=state.publish(f.lease);MdoBrowserUploadUris.Session unknown=state.reserve(id);check(unknown.enter());unknown.openFailure();
        check(state.descriptors()==1&&state.uncertain()==1);final MdoBrowserUploadUris current=state;refused("upload_proxy_unclean",()->current.reserve(id));
        unknown.released();check(state.descriptors()==0&&state.uncertain()==0);MdoBrowserUploadUris.Session fresh=opened(state,id);unknown.released();check(state.descriptors()==1);fresh.released();f.retire();state.close();
    }
    private static void partialReply(Fixture f,boolean timeout)throws Exception {
        f.prepare(data());MdoBrowserUploadUris state=new MdoBrowserUploadUris(f.queue,SystemClock::elapsedRealtime);String token=state.publish(f.lease);MdoBrowserUploadUris.Session session=opened(state,token);
        AtomicInteger chunks=new AtomicInteger();f.readHook.set(()->{if(chunks.incrementAndGet()==2){if(timeout)try{Thread.sleep(50);}catch(InterruptedException e){throw new IllegalStateException(e);}else f.lease.revoke();}});
        byte[] output=new byte[131072];Arrays.fill(output,(byte)85);
        refused(timeout?"upload_read_timeout":"upload_read_lease_revoked",()->session.read(0,output.length,output,timeout?20:1000));check(Arrays.equals(output,new byte[output.length])&&f.queue.unfinishedReads()==0);
        session.released();f.retire();state.close();
    }
    private static void foreign(Fixture f,Path base)throws Exception {
        f.prepare(new byte[]{3});try(Fixture other=new Fixture(base.resolve("foreign"))){other.prepare(new byte[]{4});MdoBrowserUploadUris state=new MdoBrowserUploadUris(f.queue,SystemClock::elapsedRealtime);refused("upload_read_lease_invalid",()->state.publish(other.lease));check(state.grants()==0);state.close();}f.retire();
    }
    private static void expiryAndCapacity(Fixture f)throws Exception {
        f.prepare(new byte[0]);MdoBrowserUploadUris state=new MdoBrowserUploadUris(f.queue,SystemClock::elapsedRealtime);String token=state.publish(f.lease);MdoBrowserUploadUris.Session session=opened(state,token);
        check(session.read(0,1,new byte[1],1000)==0&&session.size()==0);long deadline=f.lease.expires;SystemClock.now=deadline;
        check(state.grants()==0&&state.descriptors()==1);refused("upload_uri_not_found",()->state.info(token));refused("upload_uri_revoked",session::size);session.released();check(state.descriptors()==0);f.retire();state.close();
        f.scope=new MdoBrowserUploadStore.Scope(++f.epoch);MdoBrowserUploadStore.Spec[] files=new MdoBrowserUploadStore.Spec[16];for(int at=0;at<files.length;at++)files[at]=new MdoBrowserUploadStore.Spec("file-"+at+".txt",0,sha(new byte[0]));
        MdoBrowserUploadQueue.Outcome start=f.call(MdoBrowserUploadQueue.Request.begin("uri-full-capacity",files,SystemClock.now+5000));check(start.ok());f.ticket=start.value.ticket;check(f.call(MdoBrowserUploadQueue.Request.ticket("seal",f.ticket)).value.sealed);
        MdoBrowserUploadUris capacity=new MdoBrowserUploadUris(f.queue,SystemClock::elapsedRealtime);java.util.Set<String> unique=new java.util.HashSet<>();
        for(int at=0;at<16;at++){MdoBrowserUploadStore.ReadLease lease=f.call(MdoBrowserUploadQueue.Request.access(f.ticket,at)).reader;check(lease!=null);unique.add(capacity.publish(lease));}
        check(capacity.grants()==16&&unique.size()==16);capacity.close();check(capacity.grants()==0&&capacity.descriptors()==0);f.retire();
    }
    private static ProviderInfo info(Context context){ProviderInfo value=new ProviderInfo();value.authority=MdoBrowserUploadUriRuntime.authority(context);return value;}
    private static void provider(Fixture f)throws Exception {
        f.prepare(data());MdoBrowserUploadRuntime.queue=f.queue;Context context=new Context();MdoBrowserUploadProvider provider=new MdoBrowserUploadProvider();ProviderInfo unsafe=info(context);unsafe.exported=true;
        denied(()->new MdoBrowserUploadProvider().attachInfo(context,unsafe));unsafe.exported=false;unsafe.grantUriPermissions=true;denied(()->new MdoBrowserUploadProvider().attachInfo(context,unsafe));
        ProviderInfo wrong=info(context);wrong.authority="wrong";denied(()->new MdoBrowserUploadProvider().attachInfo(context,wrong));provider.attachInfo(context,info(context));
        Uri uri=MdoBrowserUploadUriRuntime.publish(context,f.lease);check(uri.toString().equals(MdoBrowserUploadUriRuntime.publish(context,f.lease).toString()));MdoBrowserUploadUris state=MdoBrowserUploadUriRuntime.existing(context);
        Cursor cursor=provider.query(uri,null,null,null,null);check(cursor!=null&&cursor.moveToFirst()&&cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)).equals("fixture-\u4e2d.txt")&&cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))==150001);cursor.close();check("text/plain".equals(provider.getType(uri)));
        Binder.uid=10001;try{denied(()->provider.query(uri,null,null,null,null));denied(()->provider.getType(uri));denied(()->provider.openFile(uri,"r"));}finally{Binder.uid=10000;}
        for(String mode:new String[]{"w","rw","wa","rwt","rt",""})absent(()->provider.openFile(uri,mode));
        denied(()->provider.insert(uri,new ContentValues()));denied(()->provider.update(uri,new ContentValues(),null,null));denied(()->provider.delete(uri,null,null));
        for(String suffix:new String[]{"?x=1","#x","/","%2f"}){Uri invalid=Uri.parse(uri.toString()+suffix);check(provider.getType(invalid)==null);absent(()->provider.openFile(invalid,"r"));}
        CancellationSignal already=new CancellationSignal();already.cancel();int calls=context.storage.opens;
        try{provider.openFile(uri,"r",already);throw new AssertionError("Cancelled open admitted");}catch(OperationCanceledException expected){check(context.storage.opens==calls&&state.descriptors()==0);}
        Handler.failNext=true;absent(()->provider.openFile(uri,"r"));check(state.descriptors()==0&&HandlerThread.starts==1);
        for(int failures:new int[]{1,2}){
            CancellationSignal detach=new CancellationSignal();detach.failDetach=failures;absent(()->provider.openFile(uri,"r",detach));check(state.descriptors()==0&&context.storage.fd.closes==1);
            ParcelFileDescriptor replacement=provider.openFile(uri,"r");detach.cancel();check(state.descriptors()==1&&replacement.callback.onGetSize()==150001);replacement.close();
        }
        ParcelFileDescriptor first=provider.openFile(uri,"r");check(first.callback.onGetSize()==150001&&context.storage.handler.getLooper()!=Looper.getMainLooper());first.close();check(state.descriptors()==0&&state.grants()==1);
        ParcelFileDescriptor second=provider.openFile(uri,"r");byte[] output=new byte[131072];check(second.callback.onRead(0,output.length,output)==output.length&&Arrays.equals(output,Arrays.copyOf(data(),output.length)));second.close();check(state.descriptors()==0&&HandlerThread.starts==1);
        for(String behavior:new String[]{"throw","null"}){
            context.storage.behavior=behavior;absent(()->provider.openFile(uri,"r"));check(state.descriptors()==1&&state.uncertain()==1);android.os.ProxyFileDescriptorCallback old=context.storage.last;
            int before=context.storage.opens;absent(()->provider.openFile(uri,"r"));check(before==context.storage.opens);old.onRelease();check(state.descriptors()==0&&state.uncertain()==0);
            context.storage.behavior="ok";ParcelFileDescriptor fresh=provider.openFile(uri,"r");old.onRelease();check(state.descriptors()==1);fresh.close();
        }
        context.storage.behavior="early_release";absent(()->provider.openFile(uri,"r"));check(state.descriptors()==0&&state.uncertain()==0);
        context.storage.behavior="fatal";try{provider.openFile(uri,"r");throw new IllegalStateException("Fatal open lost");}catch(AssertionError expected){checks++;}
        check(state.descriptors()==1&&state.uncertain()==1);context.storage.last.onRelease();check(state.descriptors()==0&&state.uncertain()==0);
        for(String close:new String[]{"ok","throw","release_then_throw"}){
            CancellationSignal signal=new CancellationSignal();context.storage.signal=signal;context.storage.behavior="cancel";context.storage.closeBehavior=close;
            try{provider.openFile(uri,"r",signal);throw new AssertionError("Native cancellation lost");}catch(OperationCanceledException expected){checks++;}
            check(state.descriptors()==("throw".equals(close)?1:0));if("throw".equals(close)){check(state.uncertain()==1);context.storage.last.onRelease();}check(state.descriptors()==0&&state.uncertain()==0);
        }
        context.storage.behavior="ok";context.storage.closeBehavior="ok";context.storage.signal=null;ParcelFileDescriptor retained=provider.openFile(uri,"r");
        f.scope.cancel();check(provider.query(uri,null,null,null,null)==null&&provider.getType(uri)==null);errno(OsConstants.EIO,retained.callback::onGetSize);errno(OsConstants.EIO,()->retained.callback.onRead(0,1,new byte[1]));retained.close();check(state.descriptors()==0&&state.grants()==0);f.retire();
    }
    public static void main(String[] args)throws Exception {
        Path base=Paths.get(args[0]);Files.createDirectory(base);try(Fixture fixture=new Fixture(base.resolve("owned"))){stateRanges(fixture);boundsAndFailure(fixture);partialReply(fixture,false);partialReply(fixture,true);foreign(fixture,base);expiryAndCapacity(fixture);provider(fixture);}Files.delete(base);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false,\"private_cache_authority_verified\":false,\"uri_assignment_verified\":false,\"android_automation_enabled\":false}");
    }
}
