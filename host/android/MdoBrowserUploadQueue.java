package org.xserver.android;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** One persistent worker, eight queued tasks, nine unfinished UI deliveries.
 * A stalled UI cannot turn completed worker replies into an unbounded inbox. */
final class MdoBrowserUploadQueue implements AutoCloseable {
    interface Factory {MdoBrowserUploadFiles open()throws IOException;}
    interface Ui {boolean post(Runnable task);}
    interface Completion {void complete(Outcome result);}
    static final class Request {
        final String action,key,ticket,base64;final MdoBrowserUploadStore.Spec[] specs;final long expires,offset;final int index;
        private Request(String action,String key,String ticket,String base64,MdoBrowserUploadStore.Spec[] specs,long expires,int index,long offset){
            this.action=action;this.key=key;this.ticket=ticket;this.base64=base64;this.specs=specs==null?null:specs.clone();this.expires=expires;this.index=index;this.offset=offset;
        }
        static Request begin(String key,MdoBrowserUploadStore.Spec[] specs,long expires)throws IOException {
            MdoBrowserUploadStore.manifest(specs);if(!MdoBrowserUploadStore.bounded(key,100))throw new MdoBrowserUploadFiles.Failure("upload_metadata_invalid");
            return new Request("begin",key,null,null,specs,expires,0,0);
        }
        static Request ticket(String action,String ticket)throws IOException {
            if(!java.util.Arrays.asList("seal","status","cancel","release").contains(action)||ticket==null||!ticket.matches("u-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
                throw new MdoBrowserUploadFiles.Failure("upload_ticket_invalid");
            return new Request(action,null,ticket,null,null,0,0,0);
        }
        static Request chunk(String ticket,int index,long offset,String base64)throws IOException {
            Request valid=ticket("status",ticket);
            if(index<0||index>=16||offset<0||offset>MdoBrowserUploadFiles.FILE_BYTES||base64==null||base64.isEmpty()||base64.length()>87384||base64.length()%4!=0)
                throw new MdoBrowserUploadFiles.Failure("upload_chunk_invalid");
            return new Request("chunk",null,valid.ticket,base64,null,0,index,offset);
        }
    }
    static final class Outcome {
        final String code,cause;final MdoBrowserUploadStore.Snapshot value;final boolean stagingMayHaveChanged;
        Outcome(String code,String cause,MdoBrowserUploadStore.Snapshot value,boolean changed){this.code=code;this.cause=cause;this.value=value;stagingMayHaveChanged=changed;}
        boolean ok(){return code==null;}
    }
    private static final Outcome FAILED=new Outcome("upload_worker_failed",null,null,true),CLOSED=new Outcome("upload_worker_closed",null,null,false);
    private final Factory factory;private final Ui ui;private final LongSupplier clock;
    private final ArrayBlockingQueue<Work> queue=new ArrayBlockingQueue<>(8);private final Semaphore deliveries=new Semaphore(9);
    private final CountDownLatch stopped=new CountDownLatch(1);private final Thread worker;
    private MdoBrowserUploadStore store;private volatile boolean closing,ready,cleanupFailed;private volatile int activeFiles;
    private volatile long reservedBytes;
    private final class Work {
        final MdoBrowserUploadStore.Scope scope;Request request;Completion completion;
        Work(MdoBrowserUploadStore.Scope scope,Request request,Completion completion){this.scope=scope;this.request=request;this.completion=completion;}
        void deliver(Outcome value){
            Completion callback=completion;completion=null;request=null;
            try{
                if(ui.post(()->{try{callback.complete(value);}catch(RuntimeException|OutOfMemoryError ignored){}finally{deliveries.release();}}))return;
            }catch(RuntimeException|OutOfMemoryError ignored){}
            deliveries.release();
        }
    }
    MdoBrowserUploadQueue(Factory factory,Ui ui,LongSupplier clock){
        if(factory==null||ui==null||clock==null)throw new IllegalArgumentException("Missing upload queue binding");
        this.factory=factory;this.ui=ui;this.clock=clock;
        worker=new Thread(this::loop,"mdo-browser-upload");worker.setDaemon(true);worker.start();
    }
    boolean submit(MdoBrowserUploadStore.Scope scope,Request request,Completion completion){
        if(scope==null||request==null||completion==null)throw new IllegalArgumentException("Missing upload request");
        if(closing||!deliveries.tryAcquire())return false;
        try{Work work=new Work(scope,request,completion);if(!closing&&queue.offer(work))return true;}
        catch(RuntimeException|Error failure){deliveries.release();throw failure;}
        deliveries.release();return false;
    }
    private Outcome execute(Work work)throws IOException {
        Request request=work.request;
        if(closing)return CLOSED;
        if(!work.scope.live())throw new MdoBrowserUploadFiles.Failure("upload_owner_changed");
        if(store==null){
            if(!"begin".equals(request.action))throw new MdoBrowserUploadFiles.Failure("upload_ticket_not_found");
            if(clock.getAsLong()>=request.expires)throw new MdoBrowserUploadFiles.Failure("upload_expired");
            MdoBrowserUploadFiles opened=factory.open();
            try{store=new MdoBrowserUploadStore(opened,clock);ready=true;}
            catch(RuntimeException|Error failure){try{opened.close();}catch(IOException ignored){}throw failure;}
        }
        MdoBrowserUploadStore.Snapshot value;
        switch(request.action){
            case "begin":value=store.begin(work.scope,request.key,request.specs,request.expires);break;
            case "chunk":
                byte[] bytes;
                try{bytes=Base64.getDecoder().decode(request.base64);}catch(IllegalArgumentException invalid){throw new MdoBrowserUploadFiles.Failure("upload_chunk_invalid");}
                if(bytes.length<1||bytes.length>65536||!Base64.getEncoder().encodeToString(bytes).equals(request.base64))throw new MdoBrowserUploadFiles.Failure("upload_chunk_invalid");
                value=store.chunk(work.scope,request.ticket,request.index,request.offset,bytes);break;
            case "seal":value=store.seal(work.scope,request.ticket);break;
            case "status":value=store.status(work.scope,request.ticket,clock.getAsLong());break;
            case "cancel":case "release":value=store.cancel(work.scope,request.ticket);break;
            default:throw new MdoBrowserUploadFiles.Failure("upload_action_unsupported");
        }
        return new Outcome(null,null,value,!"status".equals(request.action));
    }
    private void collect(){
        if(store==null)return;
        try{store.collect(clock.getAsLong());cleanupFailed=false;}catch(IOException|RuntimeException|OutOfMemoryError failure){cleanupFailed=true;}
        activeFiles=store.reservations();reservedBytes=store.reservedBytes();
    }
    private void loop(){
        try{
            while(!closing||!queue.isEmpty()){
                Work work=null;
                try{work=queue.poll(1000,TimeUnit.MILLISECONDS);}catch(InterruptedException interrupted){closing=true;}
                if(work!=null){
                    Outcome result=FAILED;
                    try{result=execute(work);}
                    catch(IOException failure){
                        try{result=new Outcome(failure instanceof MdoBrowserUploadFiles.Failure?((MdoBrowserUploadFiles.Failure)failure).code:"upload_io_failed",
                            failure.getCause() instanceof MdoBrowserUploadFiles.Failure?((MdoBrowserUploadFiles.Failure)failure.getCause()).code:null,null,true);}
                        catch(OutOfMemoryError ignored){}
                    }catch(RuntimeException|LinkageError|OutOfMemoryError ignored){}
                    collect();work.deliver(result);
                }else collect();
            }
        }finally{
            closing=true;
            Work pending;while((pending=queue.poll())!=null)pending.deliver(CLOSED);
            if(store!=null)try{store.close();activeFiles=0;reservedBytes=0;}catch(IOException|RuntimeException|OutOfMemoryError failure){cleanupFailed=true;}
            ready=false;stopped.countDown();
        }
    }
    boolean ready(){return ready&&!closing;}
    boolean cleanupFailed(){return cleanupFailed;}
    int activeFiles(){return activeFiles;}
    long reservedBytes(){return reservedBytes;}
    int unfinishedDeliveries(){return 9-deliveries.availablePermits();}
    @Override public void close(){closing=true;}
    boolean awaitStopped(long milliseconds)throws InterruptedException{return stopped.await(milliseconds,TimeUnit.MILLISECONDS);}
}
