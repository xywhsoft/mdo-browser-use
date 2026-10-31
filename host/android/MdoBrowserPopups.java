package org.xserver.android;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    /** Opaque original action generation. Worker-visible fields contain no UI
     * predicate, native transport, view or owning controller reference. */
    static final class Scope {
        private final WeakReference<MdoBrowserPopups<?>> owner;private final long epoch;
        private final AtomicBoolean live=new AtomicBoolean(true);private boolean ready;
        private Scope(MdoBrowserPopups<?> owner,long epoch){this.owner=new WeakReference<>(owner);this.epoch=epoch;}
        boolean current(){return live.get();}
    }
    static final class Pending {
        private final Scope scope;private final AtomicBoolean live=new AtomicBoolean(true);
        final String id;final long epoch;final boolean userInitiated;
        private Pending(Scope scope,Snapshot value){this.scope=scope;id=value.id;epoch=value.epoch;userInitiated=value.userInitiated;}
        boolean current(){return live.get()&&scope.current();}
    }
    /** One original native send/consumption receipt, not a CDP target, fresh
     * child ownership, document readiness or isolated-world authorization. */
    static final class Adoption {
        private final Pending original;private final WeakReference<Object> child;
        private final AtomicInteger state=new AtomicInteger();private volatile String code;private volatile boolean consumed;
        private Adoption(Pending original,Object child){this.original=original;this.child=new WeakReference<>(child);}
        boolean pending(){return state.get()==0;}
        boolean ok(){return state.get()==1;}
        boolean nativeConsumed(){return consumed;}
        String failure(){return code;}
        boolean mayHaveOccurred(){return true;}
        private void finish(boolean ok,String reason){if(state.get()!=0)return;code=reason;state.compareAndSet(0,ok?1:2);}
    }
    private final class Protocol {
        final Scope scope;final BooleanSupplier owned;
        Protocol(Scope scope,BooleanSupplier owned){this.scope=scope;this.owned=owned;}
    }
    /** Data-only rejection evidence for the protocol worker. Only this native
     * controller can complete it; a queue barrier is not debugger detachment. */
    static final class Stop {
        private final WeakReference<MdoBrowserPopups<?>> owner;private final AtomicInteger state=new AtomicInteger();
        private volatile String code;private final Scope scope;
        private Stop(MdoBrowserPopups<?> owner,Scope scope){this.owner=new WeakReference<>(owner);this.scope=scope;}
        boolean pending(){return state.get()==0;}
        boolean confirmed(){return state.get()==1;}
        String failure(){return code==null?"popup_native_rejection_unconfirmed":code;}
        private void confirm(){state.compareAndSet(0,1);}
        private void fail(String value){code=value;state.compareAndSet(0,2);}
        boolean release(){MdoBrowserPopups<?> core=owner.get();return core==null||core.releaseStop(this);}
    }
    private final class Entry {
        final Transport<T> transport;final Snapshot snapshot;final Pending pending;long expires;final Runnable timer,consumed;
        Completion completion;Stop stop;Adoption adoption;boolean sent,adopted,invalidated,timedOut;
        Entry(Transport<T> transport,long epoch,boolean userInitiated,Scope scope){
            this.transport=transport;snapshot=new Snapshot(UUID.randomUUID().toString(),epoch,userInitiated);
            pending=scope==null?null:new Pending(scope,snapshot);
            expires=scheduler.now()+15000;
            WeakReference<MdoBrowserPopups<T>> weak=new WeakReference<>(MdoBrowserPopups.this);
            String id=snapshot.id;
            timer=()->{MdoBrowserPopups<T> core=weak.get();if(core!=null)core.expire(id);};
            consumed=()->{MdoBrowserPopups<T> core=weak.get();if(core!=null)core.consumed(id);};
        }
    }
    private final T opener;private final Scheduler scheduler;private final Budget budget;
    private final BooleanSupplier owned;private final LongSupplier navigation;private final Listener listener;
    private final Thread owner=Thread.currentThread();private Entry entry;private Protocol protocol;private Stop protocolStop;private boolean protocolStopping,closed,faulted,destroyed;
    MdoBrowserPopups(T opener,Scheduler scheduler,Budget budget,BooleanSupplier owned,LongSupplier navigation,Listener listener){
        if(opener==null||scheduler==null||budget==null||owned==null||navigation==null||listener==null)throw new IllegalArgumentException("Missing popup binding");
        this.opener=opener;this.scheduler=scheduler;this.budget=budget;this.owned=owned;this.navigation=navigation;this.listener=listener;
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Popup requires its owning UI thread");}
    private boolean current(Entry value){
        // After admission the caller must still return true to WebView. A host
        // ownership query can fail; do not escape as a fresh framework rejection
        // while retaining a timer that could later consume another native popup.
        try{return !closed&&!destroyed&&owned.getAsBoolean()&&navigation.getAsLong()==value.snapshot.epoch&&
            (value.pending==null||protocolCurrent(value.pending.scope));}
        catch(RuntimeException|OutOfMemoryError error){faulted=true;return false;}
    }
    private void blocked(boolean value){try{listener.blocked(value);}catch(RuntimeException|OutOfMemoryError error){faulted=true;}}
    /** False leaves the unaccepted transport with WebView's own rejection path. */
    boolean open(Transport<T> transport,boolean userInitiated){
        thread();if(transport==null)return false;
        if(entry!=null&&entry.transport.token()==transport.token())return true;
        if(closed||destroyed||faulted||protocolStopping||entry!=null||!owned.getAsBoolean())return false;
        long epoch=navigation.getAsLong();if(epoch<=0)return false;
        Scope scope=null;
        if(protocol!=null){
            boolean actionOwned=protocolOwner(protocol.scope);if(faulted)return false;
            if(actionOwned){scope=protocol.scope;if(!scope.current()||!scope.ready)return false;}
            else protocol.scope.live.set(false);
        }
        Entry next;
        try{next=new Entry(transport,epoch,userInitiated,scope);}catch(RuntimeException|OutOfMemoryError error){return false;}
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
        if(child==null||child==opener||pending==null||!pending.id.equals(id)||completion==null||entry.pending!=null)
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
        value.sent=true;value.adopted=child!=null;if(value.pending!=null)value.pending.live.set(false);
        value.expires=scheduler.now()+5000;
        try{scheduler.remove(value.timer);scheduler.at(value.timer,value.expires);}
        catch(RuntimeException|OutOfMemoryError error){faulted=true;}
        try{value.transport.send(child,value.consumed);}
        catch(RuntimeException|OutOfMemoryError error){faulted=true;if(value.stop!=null)value.stop.fail("popup_transport_unconfirmed");finish(value,false,"popup_transport_unconfirmed",child!=null);}
    }
    private void finish(Entry value,boolean ok,String code,boolean mayHaveOccurred){
        if(value.adoption!=null)value.adoption.finish(ok,code);
        Completion callback=value.completion;value.completion=null;
        if(callback!=null)try{callback.complete(ok,code,mayHaveOccurred);}catch(RuntimeException|OutOfMemoryError error){faulted=true;}
    }
    private void consumed(String id){
        thread();Entry value=entry;if(value==null||!value.snapshot.id.equals(id)||!value.sent)return;
        if(value.adoption!=null)value.adoption.consumed=true;
        boolean valid=!value.invalidated&&!value.timedOut&&current(value)&&!faulted;
        entry=null;budget.release();
        try{scheduler.remove(value.timer);}catch(RuntimeException|OutOfMemoryError error){faulted=true;valid=false;}
        blocked(false);valid=valid&&!faulted;
        if(value.stop!=null){if(!value.adopted&&!value.timedOut&&!faulted)value.stop.confirm();else value.stop.fail("popup_native_rejection_unconfirmed");}
        finish(value,valid,valid?null:"popup_source_changed",true);
    }
    private void expire(String id){
        thread();Entry value=entry;if(value==null||!value.snapshot.id.equals(id))return;
        if(scheduler.now()<value.expires){try{scheduler.at(value.timer,value.expires);}catch(RuntimeException|OutOfMemoryError error){faulted=true;reject(value);}return;}
        if(!value.sent){reject(value);return;}
        // A missing barrier is uncertainty, not permission to return its native
        // resource reservation or replay a popup. Exact teardown can release it.
        value.timedOut=true;faulted=true;if(value.stop!=null)value.stop.fail("popup_transport_unconfirmed");finish(value,false,"popup_transport_unconfirmed",true);
    }
    boolean protocolSource(T exact){thread();return exact==opener&&!closed&&!destroyed&&!faulted&&!protocolStopping;}
    boolean original(T exact){thread();return exact==opener;}
    Scope claimProtocol(T exact,BooleanSupplier actionOwner){
        thread();if(!protocolSource(exact)||protocol!=null||entry!=null||actionOwner==null)throw new IllegalStateException("popup_native_scope_unavailable");
        long epoch=navigation.getAsLong();if(epoch<=0||!owned.getAsBoolean()||!actionOwner.getAsBoolean())throw new IllegalStateException("popup_source_changed");
        Scope next=new Scope(this,epoch);protocol=new Protocol(next,actionOwner);return next;
    }
    boolean protocolCurrent(Scope scope){
        thread();if(scope==null||scope.owner.get()!=this||protocol==null||protocol.scope!=scope||!scope.current())return false;
        boolean valid=protocolOwner(scope);if(!valid)scope.live.set(false);return valid;
    }
    private boolean protocolOwner(Scope scope){
        boolean valid;try{valid=!closed&&!destroyed&&!faulted&&owned.getAsBoolean()&&navigation.getAsLong()==scope.epoch&&protocol.owned.getAsBoolean();}
        catch(RuntimeException|OutOfMemoryError error){faulted=true;valid=false;}
        return valid;
    }
    boolean readyProtocol(Scope scope){thread();if(!protocolCurrent(scope))return false;scope.ready=true;return true;}
    boolean abandonProtocol(Scope scope){
        thread();if(scope==null||protocol==null||protocol.scope!=scope||protocolStop!=null||entry!=null&&entry.pending!=null&&entry.pending.scope==scope)return false;
        scope.live.set(false);protocol=null;return true;
    }
    Pending pendingProtocol(Scope scope){
        thread();if(!protocolCurrent(scope)||!scope.ready)return null;
        Snapshot snapshot=snapshot();return snapshot!=null&&entry.pending!=null&&entry.pending.scope==scope?entry.pending:null;
    }
    Adoption adoptProtocol(Scope scope,Pending pending,T child){
        thread();Pending original=pendingProtocol(scope);
        if(pending==null||pending!=original||!pending.current()||child==null||child==opener)throw new IllegalStateException("popup_request_unavailable");
        Entry value=entry;Adoption receipt=new Adoption(pending,child);value.adoption=receipt;send(value,child);return receipt;
    }
    boolean adoptedTo(Adoption original,T exact){
        thread();return original!=null&&original.original.scope.owner.get()==this&&original.child.get()==exact;
    }
    Stop stopForProtocol(Scope scope){
        thread();if(scope==null||protocol==null||protocol.scope!=scope||protocolStop!=null)throw new IllegalStateException("popup_native_scope_unavailable");
        boolean stillOwned=protocolOwner(scope);scope.live.set(false);
        Entry value=entry;boolean original=value!=null&&value.pending!=null&&value.pending.scope==scope;
        // A retired task must never send a later human callback's Message.
        protocolStopping=stillOwned||original;Stop stop=new Stop(this,scope);protocolStop=stop;
        if(faulted||destroyed){stop.fail("popup_native_rejection_unconfirmed");return stop;}
        if(!original){stop.confirm();return stop;}
        stopEntry(value,stop);return stop;
    }
    Stop stopForProtocol(){
        thread();if(protocolStopping||protocol!=null)throw new IllegalStateException("popup_native_stop_busy");
        // Keep admission held even if allocating the receipt itself fails.
        protocolStopping=true;Stop stop=new Stop(this,null);protocolStop=stop;Entry value=entry;
        if(faulted||destroyed){stop.fail("popup_native_rejection_unconfirmed");return stop;}
        if(value==null){stop.confirm();return stop;}
        stopEntry(value,stop);return stop;
    }
    private void stopEntry(Entry value,Stop stop){
        value.stop=stop;
        if(value.sent&&value.adopted){stop.fail("popup_native_already_adopted");return;}
        value.invalidated=true;if(!value.sent)reject(value);
    }
    private boolean releaseStop(Stop stop){
        thread();if(protocolStop!=stop||!stop.confirmed()||entry!=null&&(stop.scope==null||entry.pending!=null&&entry.pending.scope==stop.scope))return false;
        if(stop.scope!=null){if(protocol==null||protocol.scope!=stop.scope)return false;protocol=null;}
        protocolStop=null;protocolStopping=false;return true;
    }
    void cancel(){thread();if(protocol!=null)protocol.scope.live.set(false);if(entry!=null){entry.invalidated=true;if(!entry.sent)reject(entry);}}
    boolean clean(){thread();return !faulted;}
    boolean busy(){thread();return entry!=null;}
    @Override public void close(){thread();if(closed)return;closed=true;if(protocol!=null)protocol.scope.live.set(false);cancel();}
    void viewDestroyed(T exact){
        thread();if(exact!=opener||destroyed)return;destroyed=true;closed=true;
        Entry value=entry;entry=null;if(protocol!=null){protocol.scope.live.set(false);protocol=null;}
        if(protocolStop!=null)protocolStop.fail("popup_source_destroyed");
        if(value!=null){if(value.pending!=null)value.pending.live.set(false);budget.release();try{scheduler.remove(value.timer);}catch(RuntimeException|OutOfMemoryError ignored){}
            finish(value,false,"popup_source_destroyed",value.sent);}
        faulted=false;blocked(false);
    }
}
