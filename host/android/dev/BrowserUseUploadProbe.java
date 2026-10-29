package org.xserver.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/** Installed-runtime staging gate. Uses only fixed synthetic bytes and the
 * production app-cache factory/worker. Never assigns a URI or opens a picker. */
final class BrowserUseUploadProbe implements AutoCloseable {
    interface Done {void complete(boolean ok,String code,int checks);}
    interface Next {void complete(MdoBrowserUploadQueue.Outcome value)throws IOException;}
    private static final class CheckFailure extends RuntimeException {
        final String code;CheckFailure(String code){this.code=code;}
    }
    private final Handler handler;private final Done done;private final MdoBrowserUploadQueue queue;
    private final MdoBrowserUploadStore.Scope original=new MdoBrowserUploadStore.Scope(1),foreign=new MdoBrowserUploadStore.Scope(1),
        cancelled=new MdoBrowserUploadStore.Scope(2),expired=new MdoBrowserUploadStore.Scope(3),mismatch=new MdoBrowserUploadStore.Scope(4);
    private boolean closed,finished;private int checks,generation;private Runnable step;private long waitUntil,originalDeadline;
    private String ticket,expiredTicket;private byte[] data;private MdoBrowserUploadStore.Spec[] manifest;
    BrowserUseUploadProbe(Context context,Handler handler,Done done){this.handler=handler;this.done=done;queue=MdoBrowserUploadRuntime.get(context);}
    private void check(boolean value,String code){if(!value)throw new CheckFailure(code);checks++;}
    private static String reason(Throwable failure,String fallback){
        if(failure instanceof CheckFailure)return ((CheckFailure)failure).code;
        if(failure instanceof MdoBrowserUploadFiles.Failure)return ((MdoBrowserUploadFiles.Failure)failure).code;
        return fallback; // Never expose a platform exception's path or message.
    }
    private static String sha(byte[] bytes){
        try{StringBuilder value=new StringBuilder(64);for(byte item:MessageDigest.getInstance("SHA-256").digest(bytes))value.append(String.format(java.util.Locale.ROOT,"%02x",item&255));return value.toString();}
        catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException("upload_fixture_hash_unavailable");}
    }
    private void call(MdoBrowserUploadStore.Scope scope,MdoBrowserUploadQueue.Request request,Next next){
        if(closed||finished)return;final int identity=++generation;
        if(!queue.submit(scope,request,value->{
            if(closed||finished||identity!=generation)return;generation++;
            try{check(Looper.myLooper()==Looper.getMainLooper(),"upload_fixture_reply_off_ui");next.complete(value);}
            catch(IOException|RuntimeException|LinkageError|OutOfMemoryError failure){end(false,reason(failure,"upload_fixture_contract_failed"));}
        }))end(false,"upload_fixture_worker_busy");
    }
    private MdoBrowserUploadQueue.Request command(String action,String id)throws IOException{return MdoBrowserUploadQueue.Request.ticket(action,id);}
    private MdoBrowserUploadQueue.Request begin(String key,MdoBrowserUploadStore.Spec[] files,long expires)throws IOException{return MdoBrowserUploadQueue.Request.begin(key,files,expires);}
    private MdoBrowserUploadStore.Spec[] small(String name,byte[] bytes)throws IOException{
        return new MdoBrowserUploadStore.Spec[]{new MdoBrowserUploadStore.Spec(name,bytes.length,sha(bytes))};
    }
    private void later(Runnable next){
        if(closed||finished)return;if(step!=null)handler.removeCallbacks(step);
        step=()->{try{next.run();}catch(RuntimeException|LinkageError|OutOfMemoryError failure){end(false,reason(failure,"upload_fixture_poll_failed"));}};
        if(!handler.postDelayed(step,25))end(false,"upload_fixture_scheduler_unavailable");
    }
    void run(){
        try{
            check(Looper.myLooper()==Looper.getMainLooper(),"upload_fixture_not_ui");
            check(queue.activeFiles()==0&&queue.reservedBytes()==0&&!queue.cleanupFailed(),"upload_fixture_initial_cache_not_clean");
            data=new byte[65539];for(int at=0;at<data.length;at++)data[at]=(byte)(at*31+7);
            manifest=new MdoBrowserUploadStore.Spec[]{new MdoBrowserUploadStore.Spec("fixture-\u4e2d.bin",data.length,sha(data)),new MdoBrowserUploadStore.Spec("empty.txt",0,sha(new byte[0]))};
            originalDeadline=SystemClock.elapsedRealtime()+60000;
            call(original,begin("native-staging",manifest,originalDeadline),value->{
                if(!value.ok())throw new CheckFailure("upload_fixture_begin_"+(value.code!=null&&value.code.matches("[a-z_]{1,80}")?value.code:"failed"));
                check(value.ok()&&value.value!=null&&value.value.navigation==1&&value.value.files.length==2&&!value.value.sealed,"upload_fixture_begin_failed");
                check(queue.ready()&&queue.activeFiles()==2&&queue.reservedBytes()==data.length,"upload_fixture_private_cache_not_active");
                ticket=value.value.ticket;
                call(original,begin("native-staging",manifest,originalDeadline+1000),duplicate->{
                    check(duplicate.ok()&&ticket.equals(duplicate.value.ticket)&&duplicate.value.expires==originalDeadline,"upload_fixture_identity_changed");
                    call(foreign,command("status",ticket),denied->{
                        check(!denied.ok()&&"upload_ticket_not_found".equals(denied.code)&&queue.activeFiles()==2,"upload_fixture_foreign_ticket_accepted");
                        invalidChunk();
                    });
                });
            });
        }catch(IOException|RuntimeException|LinkageError|OutOfMemoryError failure){end(false,reason(failure,"upload_fixture_start_failed"));}
    }
    private void invalidChunk()throws IOException {
        call(original,MdoBrowserUploadQueue.Request.chunk(ticket,0,0,"AB=="),value->{
            check(!value.ok()&&"upload_chunk_invalid".equals(value.code),"upload_fixture_noncanonical_chunk_accepted");
            chunk(false);
        });
    }
    private void chunk(boolean duplicate)throws IOException {
        call(original,MdoBrowserUploadQueue.Request.chunk(ticket,0,0,Base64.getEncoder().encodeToString(Arrays.copyOf(data,65536))),value->{
            check(value.ok()&&value.value.received[0]==65536&&value.value.received[1]==0,"upload_fixture_chunk_failed");
            if(!duplicate){chunk(true);return;}
            byte[] changed=Arrays.copyOf(data,65536);changed[0]^=1;
            call(original,MdoBrowserUploadQueue.Request.chunk(ticket,0,0,Base64.getEncoder().encodeToString(changed)),conflict->{
                check(!conflict.ok()&&"upload_chunk_conflict".equals(conflict.code),"upload_fixture_duplicate_conflict_accepted");
                call(original,MdoBrowserUploadQueue.Request.chunk(ticket,0,65536,Base64.getEncoder().encodeToString(Arrays.copyOfRange(data,65536,data.length))),tail->{
                    check(tail.ok()&&tail.value.received[0]==data.length,"upload_fixture_tail_failed");
                    call(original,command("seal",ticket),sealed->{
                        check(sealed.ok()&&sealed.value.sealed&&"sealed".equals(sealed.value.state)&&sealed.value.files[0].sha256.equals(manifest[0].sha256),"upload_fixture_seal_failed");
                        release();
                    });
                });
            });
        });
    }
    private void release()throws IOException {
        call(original,command("release",ticket),value->{
            check(value.ok()&&value.value.cleanupConfirmed&&queue.activeFiles()==0&&queue.reservedBytes()==0,"upload_fixture_release_unconfirmed");
            call(original,command("status",ticket),retired->{
                check(retired.ok()&&"released".equals(retired.value.state),"upload_fixture_terminal_receipt_missing");
                call(original,begin("native-staging",manifest,originalDeadline),replayed->{
                    check(!replayed.ok()&&"upload_operation_retired".equals(replayed.code),"upload_fixture_terminal_key_replayed");
                    cancellation();
                });
            });
        });
    }
    private void cancellation()throws IOException {
        call(cancelled,begin("native-cancel",small("cancel.txt",new byte[]{1}),SystemClock.elapsedRealtime()+60000),value->{
            check(value.ok()&&queue.activeFiles()==1,"upload_fixture_cancel_begin_failed");
            cancelled.cancel();waitUntil=SystemClock.elapsedRealtime()+3500;later(this::awaitCancellation);
        });
    }
    private void awaitCancellation(){
        if(queue.activeFiles()!=0||queue.reservedBytes()!=0){
            if(SystemClock.elapsedRealtime()>=waitUntil){end(false,"upload_fixture_scope_cleanup_unconfirmed");return;}
            later(this::awaitCancellation);return;
        }
        try{
            checks++;
            call(expired,begin("native-expiry",small("expiry.txt",new byte[]{2}),SystemClock.elapsedRealtime()+1000),value->{
                check(value.ok()&&queue.activeFiles()==1,"upload_fixture_expiry_begin_failed");
                expiredTicket=value.value.ticket;waitUntil=SystemClock.elapsedRealtime()+3500;later(this::awaitExpiry);
            });
        }catch(IOException failure){end(false,"upload_fixture_expiry_start_failed");}
    }
    private void awaitExpiry(){
        if(queue.activeFiles()!=0||queue.reservedBytes()!=0){
            if(SystemClock.elapsedRealtime()>=waitUntil){end(false,"upload_fixture_expiry_cleanup_unconfirmed");return;}
            later(this::awaitExpiry);return;
        }
        try{
            checks++;call(expired,command("status",expiredTicket),value->{
                check(value.ok()&&value.value.cleanupConfirmed&&"released".equals(value.value.state),"upload_fixture_expiry_receipt_missing");
                hashMismatch();
            });
        }catch(IOException failure){end(false,"upload_fixture_expiry_status_failed");}
    }
    private void hashMismatch()throws IOException {
        call(mismatch,begin("native-hash-mismatch",small("mismatch.txt",new byte[]{1}),SystemClock.elapsedRealtime()+60000),value->{
            check(value.ok(),"upload_fixture_mismatch_begin_failed");String id=value.value.ticket;
            call(mismatch,MdoBrowserUploadQueue.Request.chunk(id,0,0,"Ag=="),written->{
                check(written.ok(),"upload_fixture_mismatch_write_failed");
                call(mismatch,command("seal",id),denied->{
                    check(!denied.ok()&&"upload_hash_mismatch".equals(denied.code)&&queue.activeFiles()==0&&queue.reservedBytes()==0,"upload_fixture_bad_hash_not_released");
                    later(()->{
                        check(queue.ready()&&!queue.cleanupFailed()&&queue.activeFiles()==0&&queue.reservedBytes()==0&&queue.unfinishedDeliveries()==0,"upload_fixture_final_cleanup_unconfirmed");
                        end(true,"native_upload_staging_verified");
                    });
                });
            });
        });
    }
    private void end(boolean ok,String code){
        if(closed||finished)return;finished=true;generation++;cancelScopes();if(step!=null)handler.removeCallbacks(step);
        if(!handler.post(()->{if(!closed)done.complete(ok,code,checks);}))done.complete(false,"upload_fixture_scheduler_unavailable",checks);
    }
    private void cancelScopes(){original.cancel();foreign.cancel();cancelled.cancel();expired.cancel();mismatch.cancel();}
    @Override public void close(){if(closed)return;closed=true;generation++;cancelScopes();if(step!=null)handler.removeCallbacks(step);data=null;manifest=null;}
}
