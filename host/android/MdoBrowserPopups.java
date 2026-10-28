package org.xserver.android;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** One retained native pending window per exact opener, as required by WebView.
 * UI-thread confined. Sending a transport is irreversible; a same-queue barrier
 * acknowledges consumption, not successful navigation or document readiness. */
final class MdoBrowserPopups<T> implements AutoCloseable {
    interface Scheduler {long now();void at(Runnable task,long uptime);void remove(Runnable task);}
    interface Transport<T> {Object token();void send(T child,Runnable consumed);}
    interface Completion {void complete(boolean ok,String code,boolean mayHaveOccurred);}
    interface Listener {void blocked(boolean value);}
    static final class Budget {
        private int used;final int limit;
        Budget(int limit){if(limit<1||limit>8)throw new IllegalArgumentException("Invalid popup budget");this.limit=limit;}
        private boolean take(){if(used>=limit)return false;used++;return true;}
        private void release(){if(used<=0)throw new IllegalStateException("Popup budget underflow");used--;}
        int used(){return used;}
    }
    static final class Snapshot {
        final String id;final long epoch;final boolean userInitiated;
        Snapshot(String id,long epoch,boolean userInitiated){this.id=id;this.epoch=epoch;this.userInitiated=userInitiated;}
    }
    private final class Entry {
        final Transport<T> transport;final Snapshot snapshot;long expires;final Runnable timer,consumed;
        Completion completion;boolean sent,invalidated,timedOut;
        Entry(Transport<T> transport,long epoch,boolean userInitiated){
            this.transport=transport;snapshot=new Snapshot(UUID.randomUUID().toString(),epoch,userInitiated);
            expires=scheduler.now()+15000;
            WeakReference<MdoBrowserPopups<T>> weak=new WeakReference<>(MdoBrowserPopups.this);
            String id=snapshot.id;
            timer=()->{MdoBrowserPopups<T> core=weak.get();if(core!=null)core.expire(id);};
            consumed=()->{MdoBrowserPopups<T> core=weak.get();if(core!=null)core.consumed(id);};
        }
    }
    private final T opener;private final Scheduler scheduler;private final Budget budget;
    private final BooleanSupplier owned;private final LongSupplier navigation;private final Listener listener;
    private final Thread owner=Thread.currentThread();private Entry entry;private boolean closed,faulted,destroyed;
    MdoBrowserPopups(T opener,Scheduler scheduler,Budget budget,BooleanSupplier owned,LongSupplier navigation,Listener listener){
        if(opener==null||scheduler==null||budget==null||owned==null||navigation==null||listener==null)throw new IllegalArgumentException("Missing popup binding");
        this.opener=opener;this.scheduler=scheduler;this.budget=budget;this.owned=owned;this.navigation=navigation;this.listener=listener;
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Popup requires its owning UI thread");}
    private boolean current(Entry value){
        // After admission the caller must still return true to WebView. A host
        // ownership query can fail; do not escape as a fresh framework rejection
        // while retaining a timer that could later consume another native popup.
        try{return !closed&&!destroyed&&owned.getAsBoolean()&&navigation.getAsLong()==value.snapshot.epoch;}
        catch(RuntimeException|OutOfMemoryError error){faulted=true;return false;}
    }
    private void blocked(boolean value){try{listener.blocked(value);}catch(RuntimeException|OutOfMemoryError error){faulted=true;}}
    /** False leaves the unaccepted transport with WebView's own rejection path. */
    boolean open(Transport<T> transport,boolean userInitiated){
        thread();if(transport==null)return false;
        if(entry!=null&&entry.transport.token()==transport.token())return true;
        if(closed||destroyed||faulted||entry!=null||!owned.getAsBoolean())return false;
        long epoch=navigation.getAsLong();if(epoch<=0)return false;
        Entry next;
        try{next=new Entry(transport,epoch,userInitiated);}catch(RuntimeException|OutOfMemoryError error){return false;}
        if(!budget.take())return false;
        entry=next;blocked(true);
        try{scheduler.at(next.timer,next.expires);}catch(RuntimeException|OutOfMemoryError error){faulted=true;reject(next);return true;}
        if(faulted||!current(next))reject(next);
        return true;
    }
    Snapshot snapshot(){
        thread();if(entry==null)return null;
        if(!entry.sent&&(!current(entry)||scheduler.now()>=entry.expires))reject(entry);
        return entry==null||entry.sent?null:entry.snapshot;
    }
    void adopt(String id,T child,Completion completion){
        thread();Snapshot pending=snapshot();
        if(child==null||child==opener||pending==null||!pending.id.equals(id)||completion==null)
            throw new IllegalStateException("popup_request_unavailable");
        Entry value=entry;value.completion=completion;send(value,child);
    }
    boolean dismiss(String id){
        thread();if(entry==null||!entry.snapshot.id.equals(id)||entry.sent)return false;reject(entry);return true;
    }
    private void reject(Entry value){if(entry==value&&!value.sent){value.invalidated=true;send(value,null);}}
    private void send(Entry value,T child){
        // Claim before entering framework code or application callbacks. Neither
        // exceptions nor reentrancy authorize sending this Message a second time.
        value.sent=true;
        value.expires=scheduler.now()+5000;
        try{scheduler.remove(value.timer);scheduler.at(value.timer,value.expires);}
        catch(RuntimeException|OutOfMemoryError error){faulted=true;}
        try{value.transport.send(child,value.consumed);}
        catch(RuntimeException|OutOfMemoryError error){faulted=true;finish(value,false,"popup_transport_unconfirmed",child!=null);}
    }
    private void finish(Entry value,boolean ok,String code,boolean mayHaveOccurred){
        Completion callback=value.completion;value.completion=null;
        if(callback!=null)try{callback.complete(ok,code,mayHaveOccurred);}catch(RuntimeException|OutOfMemoryError error){faulted=true;}
    }
    private void consumed(String id){
        thread();Entry value=entry;if(value==null||!value.snapshot.id.equals(id)||!value.sent)return;
        boolean valid=!value.invalidated&&!value.timedOut&&current(value)&&!faulted;
        entry=null;budget.release();
        try{scheduler.remove(value.timer);}catch(RuntimeException|OutOfMemoryError error){faulted=true;valid=false;}
        blocked(false);valid=valid&&!faulted;finish(value,valid,valid?null:"popup_source_changed",true);
    }
    private void expire(String id){
        thread();Entry value=entry;if(value==null||!value.snapshot.id.equals(id))return;
        if(scheduler.now()<value.expires){try{scheduler.at(value.timer,value.expires);}catch(RuntimeException|OutOfMemoryError error){faulted=true;reject(value);}return;}
        if(!value.sent){reject(value);return;}
        // A missing barrier is uncertainty, not permission to return its native
        // resource reservation or replay a popup. Exact teardown can release it.
        value.timedOut=true;faulted=true;finish(value,false,"popup_transport_unconfirmed",true);
    }
    void cancel(){thread();if(entry!=null){entry.invalidated=true;if(!entry.sent)reject(entry);}}
    boolean clean(){thread();return !faulted;}
    boolean busy(){thread();return entry!=null;}
    @Override public void close(){thread();if(closed)return;closed=true;cancel();}
    void viewDestroyed(T exact){
        thread();if(exact!=opener||destroyed)return;destroyed=true;closed=true;
        Entry value=entry;entry=null;
        if(value!=null){budget.release();try{scheduler.remove(value.timer);}catch(RuntimeException|OutOfMemoryError ignored){}
            finish(value,false,"popup_source_destroyed",value.sent);}
        faulted=false;blocked(false);
    }
}
