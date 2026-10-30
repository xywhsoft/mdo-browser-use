package org.xserver.android;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Eight native physical owners on the existing protocol worker. One bounded
 * pump, short receive slices and cooperative turns during ordinary RPC waits;
 * no second executor, UI I/O, caller-runs fallback or automatic reconnect. */
final class MdoBrowserCdpNetworks implements AutoCloseable {
    interface Connector {MdoBrowserCdpBinding.Network bind(MdoBrowserCdpMarker marker,int timeout,MdoBrowserCdpSession.Events events)throws IOException;}
    interface Events {void event(MdoBrowserCdpBinding.Network connection,String method,String child,JSONObject params)throws IOException;}
    interface Completion {void done(Result result);}
    static final class Result {
        final boolean ready,enabledMayHaveOccurred,cleanupConfirmed;final String code;
        Result(boolean ready,String code,boolean enabled,boolean clean){this.ready=ready;this.code=code;enabledMayHaveOccurred=enabled;cleanupConfirmed=clean;}
    }
    private static final int LIMIT=8,QUEUED=0,STARTING=1,READY=2,TERMINAL=3;
    private final MdoBrowserCdpWorker.Ui ui;private final Connector connector;private final LongSupplier clock;
    private final ThreadPoolExecutor executor;private final Object gate=new Object();private final Slot[] slots=new Slot[LIMIT];
    private final Runnable unavailable;
    private final AtomicBoolean closed=new AtomicBoolean(),pumpQueued=new AtomicBoolean();
    private int cursor;private boolean servicing;
    private final Runnable pump=()->{pumpQueued.set(false);if(closed.get())return;turn();schedule();};
    MdoBrowserCdpNetworks(MdoBrowserCdpWorker.Ui ui,Connector connector,LongSupplier clock,ThreadPoolExecutor executor,Runnable unavailable){
        if(ui==null||connector==null||clock==null||executor==null||unavailable==null||!ui.onThread())throw new IllegalArgumentException("Network owners require their UI worker");
        this.ui=ui;this.connector=connector;this.clock=clock;this.executor=executor;this.unavailable=unavailable;
    }
    private void thread(){if(!ui.onThread())throw new IllegalStateException("Network owner dispatch requires its UI thread");}
    private static String safe(String code){return code!=null&&code.matches("cdp_[a-z_]{1,80}")?code:"cdp_network_worker_failed";}
    void start(MdoBrowserCdpMarker marker,int timeout,Events events,Completion complete)throws IOException {
        thread();if(closed.get())throw new IOException("cdp_worker_closed");
        if(marker==null||!marker.lease().current()||!marker.lease().physicalNetwork()||events==null||complete==null||timeout<100||timeout>15000)
            throw new IOException("cdp_network_arguments_invalid");
        Slot slot=new Slot(marker,timeout,events,complete);int index=-1;
        synchronized(gate){
            for(int i=0;i<LIMIT;i++){if(slots[i]!=null&&slots[i].lease==slot.lease)throw new IOException("cdp_network_owner_busy");if(slots[i]==null)index=i;}
            if(index<0)throw new IOException("cdp_network_owner_limit");slots[index]=slot;
        }
        try{executor.execute(slot);}
        catch(RejectedExecutionException error){slot.drop();remove(slot);throw new IOException("cdp_worker_unavailable");}
        catch(RuntimeException|Error error){slot.drop();remove(slot);throw error;}
    }
    private void remove(Slot slot){synchronized(gate){for(int i=0;i<LIMIT;i++)if(slots[i]==slot){slots[i]=null;return;}}}
    void cancel(MdoBrowserCdpMarker.Lease lease){
        thread();Slot found=null;synchronized(gate){for(Slot slot:slots)if(slot!=null&&slot.lease==lease){found=slot;break;}}
        if(found==null)return;found.cancel();if(executor.remove(found))found.finish("cdp_network_cancelled");else schedule();
    }
    int pending(){thread();int count=0;synchronized(gate){for(Slot slot:slots)if(slot!=null)count++;}return count;}
    boolean pending(MdoBrowserCdpMarker.Lease lease){thread();synchronized(gate){for(Slot slot:slots)if(slot!=null&&slot.lease==lease)return true;}return false;}
    private void schedule(){
        if(closed.get())return;boolean needed=false;
        synchronized(gate){for(Slot slot:slots)if(slot!=null&&slot.stage==READY){needed=true;break;}}
        if(!needed||!pumpQueued.compareAndSet(false,true))return;
        try{executor.execute(pump);}catch(RuntimeException|Error rejected){pumpQueued.set(false);closeAll();unavailable.run();}
    }
    /** Called only on the common protocol worker, including inside another
     * connection's RPC wait. Busy/current connections are skipped, not reentered. */
    void turn(){
        if(closed.get()||servicing)return;servicing=true;
        try{
            Slot selected=null;
            synchronized(gate){for(int i=0;i<LIMIT;i++){
                int next=cursor++%LIMIT;Slot row=slots[next];
                if(row!=null&&row.stage==READY){selected=row;break;}
            }cursor%=LIMIT;}
            if(selected==null)return;
            try{
                if(selected.cancelled.get()||!selected.lease.current()){selected.finish("cdp_network_cancelled");return;}
                MdoBrowserCdpBinding.Network connection=selected.connection;
                if(connection==null)throw new IOException("cdp_network_binding_invalid");
                if(connection.idle())connection.poll(20,250);
            }catch(IOException error){selected.finish(safe(error.getMessage()));}
            catch(RuntimeException|Error error){selected.finish("cdp_network_worker_failed");}
        }finally{servicing=false;}
    }
    private void closeAll(){
        if(!closed.compareAndSet(false,true))return;
        for(int i=0;i<LIMIT;i++){Slot slot;synchronized(gate){slot=slots[i];}if(slot!=null)slot.drop();}
        executor.remove(pump);pumpQueued.set(false);
    }
    @Override public void close(){thread();closeAll();}
    void closeWorker(){closeAll();}
    /** The common executor thread has exited: no original I/O can still be
     * running. This is distinct from an earlier UI shutdown request. */
    void ownerTerminated(){
        closeAll();for(int i=0;i<LIMIT;i++){Slot slot;synchronized(gate){slot=slots[i];}if(slot!=null)slot.finish("cdp_network_cancelled");}
    }
    private final class Slot implements Runnable {
        final WeakReference<MdoBrowserCdpMarker> marker;final MdoBrowserCdpMarker.Lease lease;final int timeout;final long born;
        final AtomicBoolean cancelled=new AtomicBoolean();final Runnable delivery=this::deliver;
        volatile int stage=QUEUED;volatile MdoBrowserCdpBinding.Network connection;
        volatile boolean enabledMayHaveOccurred,cleanupFailed;volatile String terminalCode;
        private Events events;private Completion complete;private boolean posted,delivering,readyDelivered,terminalDelivered;
        Slot(MdoBrowserCdpMarker owner,int timeout,Events events,Completion complete){
            marker=new WeakReference<>(owner);lease=owner.lease();this.timeout=timeout;this.events=events;this.complete=complete;born=clock.getAsLong();
        }
        private int remaining(int minimum)throws IOException {
            if(closed.get()||cancelled.get()||!lease.current())throw new IOException("cdp_network_cancelled");
            long used=clock.getAsLong()-born;if(used<0||used>timeout-minimum)throw new IOException("cdp_network_start_timeout");
            return (int)Math.min(5000,timeout-used);
        }
        private void event(String method,String child,JSONObject params)throws IOException {
            if(closed.get()||cancelled.get()||!lease.current())throw new IOException("cdp_network_cancelled");
            MdoBrowserCdpBinding.Network original=connection;
            if(original==null){if(method.startsWith("Fetch."))throw new IOException("cdp_network_bootstrap_event_invalid");return;}
            Events handler;synchronized(this){handler=events;}
            if(handler==null)throw new IOException("cdp_network_cancelled");handler.event(original,method,child,params);
            if(closed.get()||cancelled.get()||!lease.current())throw new IOException("cdp_network_cancelled");
        }
        @Override public void run(){
            stage=STARTING;String failure=null;
            try{
                MdoBrowserCdpMarker original=marker.get();if(original==null)throw new IOException("cdp_network_cancelled");
                connection=connector.bind(original,remaining(100),this::event);
                if(connection==null)throw new IOException("cdp_network_binding_invalid");
                int left=remaining(10);enabledMayHaveOccurred=true;connection.enableResponses(left);
                // Initial navigation is not ready until original activation
                // and every inline continuation acknowledgement have drained.
                while(connection.pendingControls()!=0){left=remaining(10);connection.poll(Math.min(20,left),left);}
                remaining(10);stage=READY;post();schedule();
            }catch(MdoBrowserCdpSession.Failure error){if(error.protocolCode==-32601)enabledMayHaveOccurred=false;failure=safe(error.getMessage());}
            catch(IOException error){failure=safe(error.getMessage());}
            catch(RuntimeException|Error error){failure="cdp_network_worker_failed";}
            finally{if(failure!=null)finish(failure);else if(closed.get())finish("cdp_network_cancelled");}
        }
        private void closeNative(){
            MdoBrowserCdpBinding.Network bound=connection;
            if(bound!=null)try{bound.close();}catch(RuntimeException|Error error){cleanupFailed=true;}
            if(bound!=null&&!bound.cleanupConfirmed())cleanupFailed=true;
        }
        void cancel(){
            cancelled.set(true);MdoBrowserCdpMarker original=marker.get();
            try{if(original!=null)original.close();else lease.invalidate();}catch(RuntimeException|Error error){cleanupFailed=true;}
            closeNative();
        }
        void finish(String code){
            if(stage==TERMINAL){release();return;}
            closeNative();terminalCode=cancelled.get()||!lease.current()?"cdp_network_cancelled":code;
            stage=TERMINAL;post();release();
        }
        private void post(){
            synchronized(this){if(posted||delivering||terminalDelivered||complete==null||closed.get())return;posted=true;}
            boolean accepted;try{accepted=ui.post(delivery);}catch(RuntimeException|Error error){accepted=false;}
            if(!accepted){closeAll();unavailable.run();}
        }
        private void deliver(){
            thread();Completion callback;Result result;boolean terminal;
            synchronized(this){
                posted=false;if(terminalDelivered||complete==null||closed.get())return;terminal=stage==TERMINAL;
                // A cancelled running owner has not drained yet. Its eventual
                // terminal notification, not a queued ready message, completes it.
                if(!terminal&&(cancelled.get()||!lease.current()||stage!=READY||readyDelivered))return;
                delivering=true;callback=complete;
                if(terminal){terminalDelivered=true;complete=null;events=null;}else readyDelivered=true;
                result=new Result(!terminal,terminal?terminalCode:null,enabledMayHaveOccurred,terminal&&!cleanupFailed);
            }
            try{callback.done(result);}catch(RuntimeException|Error failure){cancel();synchronized(this){complete=null;events=null;terminalDelivered=true;}}
            finally{
                synchronized(this){delivering=false;}
                if(stage==TERMINAL){release();if(!terminalDelivered)post();}else if(cancelled.get())schedule();
            }
        }
        private void release(){synchronized(this){if(stage!=TERMINAL||!terminalDelivered||cleanupFailed)return;}remove(this);}
        void drop(){
            cancel();synchronized(this){complete=null;events=null;terminalDelivered=true;}
            try{ui.remove(delivery);}catch(RuntimeException|Error ignored){}
            if(stage==QUEUED&&executor.remove(this)){stage=TERMINAL;release();}
        }
    }
}
