package org.xserver.android;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Eight native physical owners on the existing protocol worker. One bounded
 * pump, short receive slices and cooperative turns during ordinary RPC waits;
 * no second executor, UI I/O, caller-runs fallback or automatic reconnect. */
final class MdoBrowserCdpNetworks implements AutoCloseable {
    interface Connector {MdoBrowserCdpBinding.Network bind(MdoBrowserCdpMarker marker,int timeout,MdoBrowserCdpSession.Events events)throws IOException;}
    interface Events {
        void event(MdoBrowserCdpBinding.Network connection,String method,String child,JSONObject params)throws IOException;
        default void turn(MdoBrowserCdpBinding.Network connection)throws IOException {}
        default boolean navigationAllowed(){return true;}
    }
    interface Completion {void done(Result result);}
    static final class Result {
        final boolean ready,enabledMayHaveOccurred,cleanupConfirmed,responsesDisabled;final String code;
        Result(boolean ready,String code,boolean enabled,boolean clean,boolean disabled){this.ready=ready;this.code=code;enabledMayHaveOccurred=enabled;cleanupConfirmed=clean;responsesDisabled=disabled;}
    }
    /** Native-only UI admission/observation, no view or callback retention and
     * no UI I/O. Cancellation never upgrades an issued uncertain operation. */
    static final class Navigation implements MdoBrowserCdpWorker.Guard {
        private static final MdoBrowserCdpSession.NavigationResult CANCELLED=MdoBrowserCdpSession.navigationFailure("cdp_navigation_cancelled",false);
        private static final MdoBrowserCdpSession.NavigationResult TIMED_OUT=MdoBrowserCdpSession.navigationFailure("cdp_navigation_timeout",false);
        private static final MdoBrowserCdpSession.NavigationResult CLOSED_UNSENT=MdoBrowserCdpSession.navigationFailure("cdp_navigation_connection_closed",false);
        private static final MdoBrowserCdpSession.NavigationResult CLOSED_SENT=MdoBrowserCdpSession.navigationFailure("cdp_navigation_connection_closed",true);
        private String url;private final int timeout;private final long born;
        private final AtomicReference<MdoBrowserCdpSession.NavigationResult> outcome=new AtomicReference<>();
        private volatile MdoBrowserCdpBinding.Network.Navigation source;private volatile boolean issued;
        private volatile MdoBrowserCdpNetworkFrames.Origin committed;
        private volatile boolean cancelled;
        private Navigation(String url,int timeout,long born){this.url=url;this.timeout=timeout;this.born=born;}
        MdoBrowserCdpSession.NavigationResult result(){MdoBrowserCdpBinding.Network.Navigation original=source;
            if(original!=null&&original.result()!=null)finish(original.result());return outcome.get();}
        boolean dispatched(){MdoBrowserCdpSession.NavigationResult result=result();return result==null?issued:result.effectMayHaveOccurred;}
        boolean pageCommitted(){return committed!=null;}
        boolean sameDocument(){MdoBrowserCdpBinding.Network.Navigation original=source;return original!=null&&original.sameDocument();}
        boolean pageCurrent(){MdoBrowserCdpNetworkFrames.Origin original=committed;return original!=null&&original.current();}
        String pageDocument(){MdoBrowserCdpNetworkFrames.Origin original=committed;return original==null?null:original.document;}
        public void check(MdoBrowserCdpBinding document,MdoBrowserCdpWorker.Deadline deadline)throws IOException {
            MdoBrowserCdpBinding.Network.Navigation original=source;
            if(cancelled)throw new IOException("cdp_navigation_cancelled");
            if(original==null||!pageCurrent())throw new IOException("cdp_navigation_document_unproven");
            original.checkDocument(document,deadline);
            if(cancelled)throw new IOException("cdp_navigation_cancelled");
            if(!pageCurrent())throw new IOException("cdp_navigation_document_changed");
        }
        synchronized void cancel(){cancelled=true;if(!issued){url=null;finish(CANCELLED);}}
        private void finish(MdoBrowserCdpSession.NavigationResult result){outcome.compareAndSet(null,result);}
        private void service(MdoBrowserCdpBinding.Network connection,Events events,LongSupplier clock,boolean retiring)throws IOException {
            String target;int budget;
            MdoBrowserCdpBinding.Network.Navigation original=source;
            if(original!=null){result();if(committed==null)committed=connection.navigationOrigin(original);return;}
            if(result()!=null||issued)return;
            boolean allowed=events.navigationAllowed();
            synchronized(this){
                if(result()!=null||issued)return;
                long now=clock.getAsLong();
                long age=now-born;
                if(cancelled||retiring){cancel();return;}
                if(age<0||age>timeout-100){url=null;finish(TIMED_OUT);return;}
                if(!allowed){url=null;finish(MdoBrowserCdpSession.navigationFailure("cdp_navigation_download_pending",false));return;}
                target=url;url=null;issued=true;budget=(int)(timeout-age);
            }
            // Never hold a UI decision/observation monitor during socket I/O.
            try{source=connection.navigateRoot(target,budget);result();}
            catch(MdoBrowserCdpSession.Failure failure){finish(MdoBrowserCdpSession.navigationFailure(failure.getMessage(),failure.effectMayHaveOccurred));}
            catch(IOException|RuntimeException|Error failure){finish(MdoBrowserCdpSession.navigationFailure("cdp_navigation_dispatch_failed",true));}
        }
        private synchronized void disconnected(){url=null;if(result()==null)finish(issued?CLOSED_SENT:CLOSED_UNSENT);}
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
    private static String safe(String code){return code!=null&&code.matches("(?:cdp|download_frame)_[a-z_]{1,80}")?code:"cdp_network_worker_failed";}
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
    void retire(MdoBrowserCdpMarker.Lease lease){
        thread();Slot found=null;synchronized(gate){for(Slot slot:slots)if(slot!=null&&slot.lease==lease){found=slot;break;}}
        if(found==null)return;
        if(found.stage!=READY){cancel(lease);return;}found.retire();schedule();
    }
    Navigation navigate(MdoBrowserCdpMarker.Lease lease,String url,int timeout)throws IOException {
        thread();if(closed.get())throw new IOException("cdp_worker_closed");
        if(!MdoBrowserCdpSession.navigationUrl(url)||timeout<100||timeout>600000)throw new IOException("cdp_navigation_arguments_invalid");
        Navigation request=new Navigation(url,timeout,clock.getAsLong());
        synchronized(gate){Slot owner=null;for(Slot slot:slots)if(slot!=null&&slot.lease==lease){owner=slot;break;}
            if(owner==null||owner.stage!=READY||owner.retiring.get()||owner.cancelled.get()||!lease.current())throw new IOException("cdp_network_owner_unavailable");
            if(owner.navigation!=null&&owner.navigation.result()==null)throw new IOException("cdp_navigation_pending");owner.navigation=request;
        }
        schedule();return request;
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
                if(connection.idle()){
                    if(selected.retiring.get()){
                        selected.retirementRemaining();if(!selected.retirementStarted){connection.cancelTransfers();selected.retirementStarted=true;}
                    }
                    selected.service(connection);
                    connection.serviceBodies(100);connection.poll(20,250);
                    if(connection.idle()){
                        selected.service(connection);connection.serviceBodies(100);
                        if(selected.retiring.get()&&connection.transfersDrained()){
                            connection.disableResponses(selected.retirementRemaining());
                            while(connection.pendingControls()!=0)connection.drainDisabledControls(20,selected.retirementRemaining());
                            selected.finish("cdp_network_retired");
                        }
                    }
                }
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
        final AtomicBoolean retiring=new AtomicBoolean();long retirementBorn;boolean retirementStarted;
        volatile int stage=QUEUED;volatile MdoBrowserCdpBinding.Network connection;
        volatile boolean enabledMayHaveOccurred,cleanupFailed;volatile String terminalCode;
        volatile Navigation navigation;
        private Events events;private Completion complete;private boolean posted,delivering,readyDelivered,terminalDelivered;
        synchronized void retire(){if(retiring.get())return;retirementBorn=clock.getAsLong();retiring.set(true);}
        int retirementRemaining()throws IOException {
            long used=clock.getAsLong()-retirementBorn;if(used<0||used>4990)throw new IOException("cdp_network_retirement_timeout");return (int)Math.min(250,5000-used);
        }
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
            if(handler==null)throw new IOException("cdp_network_cancelled");
            if(retiring.get()&&"Fetch.requestPaused".equals(method))original.continuePausedRequest();else handler.event(original,method,child,params);
            if(closed.get()||cancelled.get()||!lease.current())throw new IOException("cdp_network_cancelled");
        }
        private void service(MdoBrowserCdpBinding.Network original)throws IOException {
            if(closed.get()||cancelled.get()||!lease.current())throw new IOException("cdp_network_cancelled");
            Events handler;synchronized(this){handler=events;}
            if(handler==null)throw new IOException("cdp_network_cancelled");handler.turn(original);
            Navigation request=navigation;if(request!=null)request.service(original,handler,clock,retiring.get());
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
            finally{if(connection!=null&&!connection.responsesMayHaveOccurred())enabledMayHaveOccurred=false;
                if(failure!=null)finish(failure);else if(closed.get())finish("cdp_network_cancelled");}
        }
        private void closeNative(){
            MdoBrowserCdpBinding.Network bound=connection;
            if(bound!=null)try{bound.close();}catch(RuntimeException|Error error){cleanupFailed=true;}
            if(bound!=null&&!bound.cleanupConfirmed())cleanupFailed=true;
            Navigation request=navigation;if(request!=null)request.disconnected();
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
                result=new Result(!terminal,terminal?terminalCode:null,enabledMayHaveOccurred,terminal&&!cleanupFailed,connection!=null&&connection.responsesDisabled());
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
