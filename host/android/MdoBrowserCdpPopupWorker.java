package org.xserver.android;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** UI admission/results and process popup service on the existing executor.
 * No caller-runs fallback, extra thread, UI I/O or managed-child authority. */
final class MdoBrowserCdpPopupWorker {
    interface Factory {MdoBrowserCdpPopupBroker create(BooleanSupplier current,LongSupplier clock);}
    interface Source {MdoBrowserCdpBinding.Network resolve(MdoBrowserCdpMarker.Lease lease)throws IOException;}
    interface Events {void state(Result result);void candidate(MdoBrowserCdpPopupBroker.Candidate candidate);}
    static final class Result {
        final boolean ready,terminal,processMayHaveBeenEnabled,sourceReleased,processCleanupConfirmed;final String code;
        private Result(boolean ready,boolean terminal,String code,boolean enabled,boolean released,boolean clean){
            this.ready=ready;this.terminal=terminal;this.code=code;processMayHaveBeenEnabled=enabled;sourceReleased=released;processCleanupConfirmed=clean;
        }
    }
    static final class Request implements AutoCloseable {
        private final AtomicBoolean cancelled=new AtomicBoolean();private final AtomicReference<Result> result=new AtomicReference<>();private volatile Runnable wake;
        private Request(Runnable wake){this.wake=wake;}
        Result result(){return result.get();}
        @Override public void close(){Result value=result.get();if(value!=null&&value.terminal)return;cancelled.set(true);Runnable notify=wake;if(notify!=null)notify.run();}
    }
    private static final int QUEUED=0,STARTING=1,READY=2,CANCELLING=3,TERMINAL=4;
    private final MdoBrowserCdpWorker.Ui ui;private final ThreadPoolExecutor executor;private final Source source;private final Factory factory;private final LongSupplier clock;
    private final Runnable wake,unavailable;private final Object gate=new Object();private final Job[] jobs=new Job[8];
    private final AtomicBoolean closed=new AtomicBoolean();private final AtomicInteger active=new AtomicInteger();
    private volatile MdoBrowserCdpPopupBroker broker;private boolean operating;private String poisoned;
    MdoBrowserCdpPopupWorker(MdoBrowserCdpWorker.Ui ui,ThreadPoolExecutor executor,Source source,Factory factory,LongSupplier clock,Runnable wake,Runnable unavailable){
        if(ui==null||executor==null||source==null||factory==null||clock==null||wake==null||unavailable==null||!ui.onThread())throw new IllegalArgumentException("Popup dispatch requires its UI owner");
        this.ui=ui;this.executor=executor;this.source=source;this.factory=factory;this.clock=clock;this.wake=wake;this.unavailable=unavailable;
    }
    private void thread(){if(!ui.onThread())throw new IllegalStateException("Popup admission requires its UI thread");}
    private static String safe(String code){return code!=null&&code.matches("cdp_[a-z_]{1,80}")?code:"cdp_popup_worker_failed";}
    Request start(MdoBrowserCdpMarker.Lease lease,int timeout,Events events)throws IOException {
        thread();if(closed.get())throw new IOException("cdp_worker_closed");
        if(lease==null||!lease.current()||!lease.physicalNetwork()||events==null||timeout<100||timeout>15000)throw new IOException("cdp_popup_prepare_arguments_invalid");
        Job job=new Job(lease,timeout,events);
        synchronized(gate){int index=-1;for(int i=0;i<jobs.length;i++){
            if(jobs[i]!=null&&jobs[i].lease==lease)throw new IOException("cdp_popup_prepare_busy");if(jobs[i]==null)index=i;
        }if(index<0)throw new IOException("cdp_popup_prepare_limit");jobs[index]=job;active.incrementAndGet();}
        try{executor.execute(job);}catch(RejectedExecutionException error){remove(job);active.decrementAndGet();throw new IOException("cdp_worker_unavailable");}
        catch(RuntimeException|Error error){remove(job);active.decrementAndGet();throw error;}
        return job.request;
    }
    private void remove(Job job){synchronized(gate){for(int i=0;i<jobs.length;i++)if(jobs[i]==job){jobs[i]=null;return;}}}
    private List<Job> snapshot(){synchronized(gate){List<Job> found=new ArrayList<>();for(Job job:jobs)if(job!=null)found.add(job);return found;}}
    boolean needed(){MdoBrowserCdpPopupBroker current=broker;return !closed.get()&&(active.get()!=0||current!=null&&current.armed());}
    int pending(){thread();synchronized(gate){int count=0;for(Job job:jobs)if(job!=null)count++;return count;}}
    private boolean clean(){MdoBrowserCdpPopupBroker current=broker;return current==null||current.cleanupConfirmed();}
    private boolean enabled(){MdoBrowserCdpPopupBroker current=broker;return current!=null&&current.enabledMayHaveOccurred();}
    private void failAll(String code){
        MdoBrowserCdpPopupBroker current=broker;if(current!=null)current.close();boolean clean=clean();
        if(!clean)poisoned="cdp_popup_cleanup_unconfirmed";
        for(Job job:snapshot())if(job.stage!=QUEUED&&job.stage!=TERMINAL)job.finish(safe(code),clean||job.registration==null,clean);
        if(clean)broker=null;
    }
    /** The existing network pump calls this, also from cooperative RPC waits. */
    void turn(){
        if(closed.get()||operating||!needed())return;operating=true;
        try{
            MdoBrowserCdpPopupBroker current=broker;
            if(current==null){for(Job job:snapshot())if(job.stage==READY||job.stage==CANCELLING)job.finish("cdp_popup_worker_failed",false,true);return;}
            for(Job job:snapshot())if(job.stage==READY&&(job.request.cancelled.get()||!job.lease.current())){
                for(MdoBrowserCdpPopupBroker.Candidate candidate:current.pending())if(candidate.belongsTo(job.registration))throw new IOException("cdp_popup_native_cancel_required");
                job.registration.close();job.registration=null;job.stage=CANCELLING;
            }
            if(current.armed())current.turn(10,250);
            for(Job job:snapshot()){
                if(job.stage==CANCELLING){job.finish("cdp_popup_prepare_cancelled",true,current.cleanupConfirmed());continue;}
                if(job.stage!=READY)continue;
                if(!current.armed())throw new IOException("cdp_popup_broker_closed");
                for(MdoBrowserCdpPopupBroker.Candidate candidate:current.pending())if(candidate.belongsTo(job.registration)&&job.notified.add(candidate.id))job.offer(candidate);
            }
            if(!current.armed()&&current.cleanupConfirmed())broker=null;
        }catch(IOException error){failAll(safe(error.getMessage()));}
        catch(RuntimeException|Error error){failAll("cdp_popup_worker_failed");}
        finally{operating=false;}
    }
    void close(){
        if(!closed.compareAndSet(false,true))return;MdoBrowserCdpPopupBroker current=broker;if(current!=null)current.close();
        for(Job job:snapshot()){
            job.request.cancelled.set(true);executor.remove(job);MdoBrowserCdpPopupBroker.Registration registration=job.registration;if(registration!=null)registration.close();
            job.finish("cdp_worker_closed",registration==null&&job.stage!=STARTING,clean());
        }
    }
    void ownerTerminated(){close();for(Job job:snapshot())job.finish("cdp_worker_closed",false,clean());}
    private final class Job implements Runnable {
        final MdoBrowserCdpMarker.Lease lease;final int timeout;final long born;final Request request=new Request(()->{wake.run();});
        final Set<String> notified=new HashSet<>();final ArrayDeque<MdoBrowserCdpPopupBroker.Candidate> candidates=new ArrayDeque<>();
        final Runnable delivery=this::deliver;volatile int stage=QUEUED;volatile MdoBrowserCdpPopupBroker.Registration registration;
        private Events events;private boolean posted,delivering,readyDelivered,terminalDelivered;
        Job(MdoBrowserCdpMarker.Lease lease,int timeout,Events events){this.lease=lease;this.timeout=timeout;this.events=events;born=clock.getAsLong();}
        private int remaining()throws IOException {
            if(closed.get()||request.cancelled.get()||!lease.current())throw new IOException("cdp_popup_prepare_cancelled");
            long used=clock.getAsLong()-born;if(used<0||used>timeout-10)throw new IOException("cdp_popup_prepare_timeout");return (int)Math.min(5000,timeout-used);
        }
        @Override public void run(){
            synchronized(this){if(stage==TERMINAL)return;stage=STARTING;}
            if(operating){finish("cdp_popup_worker_failed",true,clean());return;}operating=true;
            try{
                remaining();if(poisoned!=null)throw new IOException(poisoned);
                MdoBrowserCdpBinding.Network original=source.resolve(lease);remaining();
                if(broker==null)broker=factory.create(()->!closed.get(),clock);
                if(broker==null)throw new IOException("cdp_popup_broker_connection_invalid");
                registration=broker.register(original);if(!broker.armed())broker.arm(remaining());
                remaining();synchronized(this){if(stage==TERMINAL)return;stage=READY;request.result.set(new Result(true,false,null,enabled(),false,false));}post();
            }catch(IOException error){
                if(registration!=null&&broker!=null&&broker.armed()&&(!lease.current()||request.cancelled.get())){
                    registration.close();registration=null;if(stage!=TERMINAL)stage=CANCELLING;
                }else if(registration==null)finish(safe(error.getMessage()),true,clean());
                else failAll(safe(error.getMessage()));
            }catch(RuntimeException|Error error){failAll("cdp_popup_worker_failed");}
            finally{operating=false;wake.run();}
        }
        void offer(MdoBrowserCdpPopupBroker.Candidate candidate){
            synchronized(this){if(stage!=READY||request.cancelled.get())return;if(candidates.size()>=7){request.cancelled.set(true);return;}candidates.add(candidate);}post();
        }
        void finish(String code,boolean released,boolean clean){
            synchronized(this){if(stage==TERMINAL)return;stage=TERMINAL;candidates.clear();request.result.set(new Result(false,true,code,enabled(),released,clean));request.wake=null;active.decrementAndGet();}post();
        }
        private void post(){
            synchronized(this){if(events==null){if(stage==TERMINAL)remove(this);return;}if(posted||delivering||terminalDelivered)return;posted=true;}
            boolean accepted;try{accepted=ui.post(delivery);}catch(RuntimeException|Error error){accepted=false;}
            if(!accepted){synchronized(this){posted=false;events=null;}request.cancelled.set(true);if(stage==TERMINAL)remove(this);unavailable.run();}
        }
        private void deliver(){
            thread();Events action;Result value;List<MdoBrowserCdpPopupBroker.Candidate> pending;
            synchronized(this){
                posted=false;if(delivering||terminalDelivered||events==null)return;delivering=true;action=events;value=request.result.get();
                if(value!=null&&value.terminal){terminalDelivered=true;pending=new ArrayList<>();}
                else if(stage==READY&&!request.cancelled.get()&&lease.current()){pending=new ArrayList<>(candidates);candidates.clear();if(readyDelivered)value=null;else readyDelivered=true;}
                else{value=null;pending=new ArrayList<>();candidates.clear();}
            }
            try{if(value!=null&&(value.terminal||!request.cancelled.get()&&lease.current()&&stage==READY))action.state(value);for(MdoBrowserCdpPopupBroker.Candidate candidate:pending)if(!request.cancelled.get()&&lease.current()&&candidate.current())action.candidate(candidate);}
            catch(RuntimeException|Error error){request.cancelled.set(true);wake.run();}
            finally{boolean again; synchronized(this){delivering=false;if(terminalDelivered)events=null;Result latest=request.result.get();again=latest!=null&&latest.terminal||!candidates.isEmpty();}
                if(terminalDelivered)remove(this);else if(again)post();}
        }
    }
}
