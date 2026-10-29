package org.xserver.android;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Installed-provider gate. Synthetic bytes only; no chooser, page or source
 * file. URI capabilities and platform error messages never enter its receipt. */
final class BrowserUseUploadUriProbe implements AutoCloseable {
    interface Done {void complete(boolean ok,String code,int checks);}
    interface Next {void complete(MdoBrowserUploadQueue.Outcome value)throws IOException;}
    private static final class Failure extends RuntimeException {final String code;Failure(String code){this.code=code;}}
    private static final class Open implements AutoCloseable {
        final ParcelFileDescriptor fd;final ParcelFileDescriptor.AutoCloseInputStream stream;
        private final AtomicBoolean closed=new AtomicBoolean();
        Open(ParcelFileDescriptor fd){this.fd=fd;stream=new ParcelFileDescriptor.AutoCloseInputStream(fd);}
        @Override public void close()throws IOException {if(closed.compareAndSet(false,true))stream.close();}
    }
    private final Context context;private final Handler handler;private final Done done;private final MdoBrowserUploadQueue queue;
    private final MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(1);
    private final CancellationSignal cancellation=new CancellationSignal();private final AtomicReference<Open> owned=new AtomicReference<>();
    private final AtomicBoolean closeFailed=new AtomicBoolean();
    private volatile boolean closed,finished;private boolean started;private int checks,generation;private String ticket;
    private byte[] data;private Uri file,empty;private Thread consumer;
    BrowserUseUploadUriProbe(Context context,Handler handler,Done done){this.context=context.getApplicationContext();this.handler=handler;this.done=done;queue=MdoBrowserUploadRuntime.get(this.context);}
    private void check(boolean value,String code){if(!value)throw new Failure(code);checks++;}
    private static String reason(Throwable failure,String fallback){
        if(failure instanceof Failure)return ((Failure)failure).code;
        if(failure instanceof MdoBrowserUploadFiles.Failure)return ((MdoBrowserUploadFiles.Failure)failure).code;
        return fallback;
    }
    private static String sha(byte[] bytes){
        try{StringBuilder result=new StringBuilder(64);for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();}
        catch(java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("upload_uri_fixture_hash_unavailable");}
    }
    private void call(MdoBrowserUploadQueue.Request request,Next next){
        if(closed||finished)return;final int identity=++generation;
        if(!queue.submit(scope,request,result->{
            if(closed||finished||identity!=generation)return;generation++;
            try{check(Looper.myLooper()==Looper.getMainLooper(),"upload_uri_fixture_reply_off_ui");
                if(!result.ok())throw new Failure("upload_uri_fixture_stage_"+(result.code!=null&&result.code.matches("[a-z_]{1,80}")?result.code:"failed"));next.complete(result);}
            catch(IOException|RuntimeException|LinkageError|OutOfMemoryError failure){end(false,reason(failure,"upload_uri_fixture_stage_failed"));}
        }))end(false,"upload_uri_fixture_worker_busy");
    }
    void run(){
        if(started||closed||finished)return;started=true;
        try{
            check(Looper.myLooper()==Looper.getMainLooper(),"upload_uri_fixture_not_ui");
            check(queue.activeFiles()==0&&queue.reservedBytes()==0&&!queue.cleanupFailed(),"upload_uri_fixture_initial_cache_not_clean");
            data=new byte[150001];for(int at=0;at<data.length;at++)data[at]=(byte)(at*17+9);
            MdoBrowserUploadStore.Spec[] manifest={new MdoBrowserUploadStore.Spec("fixture-\u4e2d.txt",data.length,sha(data)),new MdoBrowserUploadStore.Spec("empty.txt",0,sha(new byte[0]))};
            call(MdoBrowserUploadQueue.Request.begin("native-uri",manifest,SystemClock.elapsedRealtime()+60000),result->{
                check(result.value!=null&&!result.value.sealed&&result.value.files.length==2,"upload_uri_fixture_begin_failed");ticket=result.value.ticket;chunk(0);
            });
        }catch(IOException|RuntimeException|LinkageError|OutOfMemoryError failure){end(false,reason(failure,"upload_uri_fixture_start_failed"));}
    }
    private void chunk(int offset)throws IOException {
        if(offset==data.length){
            call(MdoBrowserUploadQueue.Request.ticket("seal",ticket),result->{
                check(result.value!=null&&result.value.sealed,"upload_uri_fixture_seal_failed");
                call(MdoBrowserUploadQueue.Request.access(ticket,0),first->{
                    check(first.reader!=null,"upload_uri_fixture_lease_missing");file=MdoBrowserUploadUriRuntime.publish(context,first.reader);
                    call(MdoBrowserUploadQueue.Request.access(ticket,1),second->{
                        check(second.reader!=null,"upload_uri_fixture_empty_lease_missing");empty=MdoBrowserUploadUriRuntime.publish(context,second.reader);
                        consumer=new Thread(this::consume,"mdo-upload-uri-fixture");consumer.start();
                    });
                });
            });return;
        }
        int end=Math.min(data.length,offset+65536);
        call(MdoBrowserUploadQueue.Request.chunk(ticket,0,offset,Base64.getEncoder().encodeToString(Arrays.copyOfRange(data,offset,end))),result->{
            check(result.value!=null&&result.value.received[0]==end,"upload_uri_fixture_chunk_failed");chunk(end);
        });
    }
    private void live(){if(closed||finished||cancellation.isCanceled())throw new Failure("upload_uri_fixture_cancelled");}
    private Open open(Uri uri)throws IOException {
        live();ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"r",cancellation);
        if(fd==null)throw new Failure("upload_uri_fixture_null_fd");Open value;
        try{value=new Open(fd);}catch(RuntimeException|Error failure){try{fd.close();}catch(IOException|RuntimeException ignored){closeFailed.set(true);}throw failure;}
        if(!owned.compareAndSet(null,value)){value.close();throw new Failure("upload_uri_fixture_overlapping_owner");}
        try{live();return value;}catch(RuntimeException failure){release(value);throw failure;}
    }
    private void release(Open value)throws IOException {owned.compareAndSet(value,null);try{value.close();}catch(IOException|RuntimeException failure){closeFailed.set(true);throw failure;}}
    private void metadata(ContentResolver resolver,Uri uri,String name,long size){
        live();try(Cursor result=resolver.query(uri,null,null,null,null)){
            check(result!=null&&result.moveToFirst(),"upload_uri_fixture_metadata_missing");
            check(name.equals(result.getString(result.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))),"upload_uri_fixture_name_changed");
            check(result.getLong(result.getColumnIndexOrThrow(OpenableColumns.SIZE))==size,"upload_uri_fixture_size_changed");
        }
        check("text/plain".equals(resolver.getType(uri)),"upload_uri_fixture_type_changed");
    }
    private void absent(ContentResolver resolver,Uri uri){
        live();try(Cursor result=resolver.query(uri,null,null,null,null)){check(result==null,"upload_uri_fixture_revoked_metadata_visible");}
        check(resolver.getType(uri)==null,"upload_uri_fixture_revoked_type_visible");
        boolean denied=false;try{Open value=open(uri);release(value);}catch(FileNotFoundException expected){denied=true;}catch(IOException other){throw new Failure("upload_uri_fixture_open_unconfirmed");}
        check(denied,"upload_uri_fixture_revoked_open_accepted");
    }
    private void slice(Open value,int offset,int length)throws IOException,ErrnoException {
        live();check(Os.lseek(value.fd.getFileDescriptor(),offset,OsConstants.SEEK_SET)==offset,"upload_uri_fixture_seek_failed");
        byte[] bytes=new byte[length];int used=0;
        while(used<length){live();int count=value.stream.read(bytes,used,length-used);if(count<=0)throw new Failure("upload_uri_fixture_short_read");used+=count;}
        check(Arrays.equals(bytes,Arrays.copyOfRange(data,offset,offset+length)),"upload_uri_fixture_payload_changed");
    }
    private void awaitClean(MdoBrowserUploadUris registry)throws InterruptedException {
        long deadline=SystemClock.elapsedRealtime()+4000;
        while(registry.descriptors()!=0||registry.grants()!=0||queue.activeFiles()!=0||queue.reservedBytes()!=0||queue.unfinishedDeliveries()!=0){
            live();if(SystemClock.elapsedRealtime()>=deadline)throw new Failure("upload_uri_fixture_cleanup_unconfirmed");Thread.sleep(20);
        }
        check(!closeFailed.get()&&!queue.cleanupFailed()&&registry.uncertain()==0&&queue.ready(),"upload_uri_fixture_cleanup_failed");
    }
    private void consume(){
        boolean ok=false;String code="upload_uri_fixture_framework_failed";
        try{
            check(Looper.myLooper()!=Looper.getMainLooper(),"upload_uri_fixture_consumer_on_ui");ContentResolver resolver=context.getContentResolver();
            MdoBrowserUploadUris registry=MdoBrowserUploadUriRuntime.existing(context);check(registry!=null&&registry.grants()==2,"upload_uri_fixture_registry_missing");
            metadata(resolver,file,"fixture-\u4e2d.txt",data.length);metadata(resolver,empty,"empty.txt",0);
            Open first=open(file);try{
                check(first.fd.getStatSize()==data.length,"upload_uri_fixture_stat_size_changed");
                slice(first,0,data.length);check(first.stream.read()==-1,"upload_uri_fixture_eof_failed");
                slice(first,65521,31);slice(first,data.length-17,17);
            }finally{release(first);}
            // Closing one stream retires only its original descriptor, not the file lease.
            Open second=open(file);try{slice(second,0,19);}finally{release(second);}
            Open zero=open(empty);try{check(zero.fd.getStatSize()==0&&zero.stream.read()==-1,"upload_uri_fixture_empty_failed");}finally{release(zero);}
            boolean writeDenied=false;try(ParcelFileDescriptor unexpected=resolver.openFileDescriptor(file,"rw",cancellation)){check(unexpected==null,"upload_uri_fixture_write_accepted");}
            catch(FileNotFoundException expected){writeDenied=true;}check(writeDenied,"upload_uri_fixture_write_not_refused");
            absent(resolver,Uri.parse(file.toString()+"?alias=1"));
            Open unread=open(file);try{
                // Never read this descriptor before revocation: previously delivered bytes cannot be retracted.
                scope.cancel();absent(resolver,file);absent(resolver,empty);
                boolean readDenied=false;try{unread.stream.read();}catch(IOException expected){readDenied=true;}
                check(readDenied,"upload_uri_fixture_revoked_descriptor_readable");
            }finally{release(unread);}
            awaitClean(registry);ok=true;code="native_upload_uri_proxy_verified";
        }catch(IOException|ErrnoException|InterruptedException|RuntimeException|LinkageError|OutOfMemoryError failure){
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();code=reason(failure,code);
        }finally{
            scope.cancel();closeOwned();final boolean passed=ok;final String result=code;
            // Exactly one original consumer posts completion; no UI join/blocking read.
            if(!handler.post(()->{if(!closed&&!finished)end(passed,result);}))close();
        }
    }
    private void end(boolean ok,String code){
        if(closed||finished)return;finished=true;generation++;scope.cancel();closeOwned();done.complete(ok&&!closeFailed.get(),closeFailed.get()?"upload_uri_fixture_close_unconfirmed":code,checks);
    }
    private void closeOwned(){Open value=owned.getAndSet(null);if(value!=null)try{value.close();}catch(IOException|RuntimeException failure){closeFailed.set(true);}}
    boolean consumerStopped(){Thread value=consumer;return value==null||!value.isAlive();}
    @Override public void close(){if(closed)return;closed=true;generation++;scope.cancel();cancellation.cancel();closeOwned();}
}
