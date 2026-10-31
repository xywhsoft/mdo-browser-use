package org.xserver.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Owns exact native view instances, not reusable tab names. UI-thread confined.
 * Lifecycle input cleanup runs before the caller detaches/destroys its view. */
final class MdoBrowserLifecycle<T> implements AutoCloseable {
    interface Factory<T> {MdoBrowserInput create(T view,LongSupplier epoch,BooleanSupplier admitted);}
    interface Invalidated<T> {void changed(String key,T view,long epoch,String reason);}
    /** Minted only by the UI registry at construction, never from a tab ID or
     * a currently blank view. Long-lived consumers retain data/weak refs only. */
    static final class Fresh<T> {
        private final WeakReference<MdoBrowserLifecycle<T>> owner;private final WeakReference<T> view,parent;
        private final String key,parentKey;private final long parentEpoch;
        private final AtomicBoolean live=new AtomicBoolean(true),destroyed=new AtomicBoolean();private boolean claimed;
        private Fresh(MdoBrowserLifecycle<T> owner,String key,T view,String parentKey,T parent,long epoch){
            this.owner=new WeakReference<>(owner);this.key=key;this.view=new WeakReference<>(view);this.parentKey=parentKey;this.parent=new WeakReference<>(parent);parentEpoch=epoch;
        }
        boolean current(){return live.get()&&!destroyed.get()&&owner.get()!=null&&view.get()!=null;}
        boolean destroyed(){return destroyed.get();}
        T claim(T opener){MdoBrowserLifecycle<T> core=owner.get();if(core==null)throw new IllegalStateException("popup_child_owner_lost");return core.popupView(this,opener,true);}
        T attached(T opener){MdoBrowserLifecycle<T> core=owner.get();if(core==null)throw new IllegalStateException("popup_child_owner_lost");return core.popupView(this,opener,false);}
    }
    private final class Entry {
        final T view;MdoBrowserInput input;Fresh<T> fresh;long epoch=1;double scale=Double.NaN;boolean hidden,manual,failed;
        Entry(T view){this.view=view;}
    }
    private final Map<String,Entry> entries=new LinkedHashMap<>();private final Factory<T> factory;private final Invalidated<T> invalidated;
    private final Map<T,Fresh<T>> retiring=new IdentityHashMap<>();private final List<WeakReference<T>> history=new ArrayList<>();
    private final Thread owner=Thread.currentThread();private boolean closed,cleanupFailed,background;
    MdoBrowserLifecycle(Factory<T> factory){this(factory,(key,view,epoch,reason)->{});}
    MdoBrowserLifecycle(Factory<T> factory,Invalidated<T> invalidated){
        if(factory==null||invalidated==null)throw new IllegalArgumentException("Missing native input factory/observer");
        this.factory=factory;this.invalidated=invalidated;
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Browser lifecycle requires its owning UI thread");}
    private Entry current(String key,T view){Entry entry=entries.get(key);return !closed&&entry!=null&&entry.view==view?entry:null;}
    boolean owns(String key,T view){thread();return current(key,view)!=null;}
    int size(){thread();return entries.size()+retiring.size();}
    T get(String key){thread();Entry entry=entries.get(key);return entry==null?null:entry.view;}
    List<T> values(){thread();List<T> result=new ArrayList<>();for(Entry entry:entries.values())result.add(entry.view);result.addAll(retiring.keySet());return result;}
    /** Call before the native constructor, including when an old tab name is
     * still reserved by a removed child whose destruction is not confirmed. */
    void checkNew(String key){
        thread();if(closed)throw new IllegalStateException("Browser lifecycle closed");
        if(key==null||key.isEmpty()||key.length()>100)throw new IllegalArgumentException("Invalid native view binding");
        if(entries.containsKey(key))throw new IllegalStateException("Browser identity is already owned");
        for(Fresh<T> child:retiring.values())if(child.key.equals(key))throw new IllegalStateException("Native browser identity is retiring");
        if(size()>=8)throw new IllegalStateException("Native browser view limit reached (8)");
    }
    private void remember(T view){
        if(view==null)throw new IllegalArgumentException("Invalid native view binding");
        for(Iterator<WeakReference<T>> at=history.iterator();at.hasNext();){T original=at.next().get();if(original==null)at.remove();else if(original==view)throw new IllegalStateException("Native view was already registered");}
        if(history.size()>=256)throw new IllegalStateException("Native view history limit reached");history.add(new WeakReference<>(view));
    }
    void put(String key,T view){
        put(key,view,null);
    }
    /** The host invokes this immediately after its private new-view constructor,
     * before configuration/navigation/native transport. It is not an API to
     * certify arbitrary preexisting unregistered framework views as new. */
    Fresh<T> putPopup(String key,T opener,T view){
        thread();String parentKey=null;Entry parent=null;for(Map.Entry<String,Entry> item:entries.entrySet())if(item.getValue().view==opener){parentKey=item.getKey();parent=item.getValue();break;}
        if(parent==null||opener==view)throw new IllegalStateException("popup_child_opener_invalid");
        Fresh<T> child=new Fresh<>(this,key,view,parentKey,opener,parent.epoch);put(key,view,child);return child;
    }
    private void put(String key,T view,Fresh<T> fresh){
        checkNew(key);remember(view);Entry entry=new Entry(view);entry.fresh=fresh;
        entry.input=factory.create(view,()->entry.epoch,()->current(key,view)==entry&&!background&&!entry.hidden&&!entry.manual&&!entry.failed);
        if(entry.input==null)throw new IllegalStateException("Missing native input binding");
        try{entry.input.observeInvalidation(reason->invalidated.changed(key,view,entry.epoch,reason));entries.put(key,entry);}
        catch(RuntimeException|Error failure){entry.input.close();throw failure;}
    }
    private T popupView(Fresh<T> child,T opener,boolean claim){
        thread();T view=child.view.get(),parent=child.parent.get();Entry value=current(child.key,view),source=current(child.parentKey,parent);
        if(child.owner.get()!=this||!child.current()||opener==null||parent!=opener||value==null||value.fresh!=child||source==null||source.epoch!=child.parentEpoch||
            source.failed||source.hidden||source.manual||value.failed||value.hidden||value.manual||background||claim&&(child.claimed||value.epoch!=1)||!claim&&!child.claimed)throw new IllegalStateException("popup_child_owner_lost");
        if(claim)child.claimed=true;return view;
    }
    /** A native post-destroy acknowledgement, not registry removal. The caller
     * invokes it only after destroying this exact returned framework instance. */
    void viewDestroyed(T exact){
        thread();for(Entry entry:entries.values())if(entry.view==exact)throw new IllegalStateException("Native view is still registered");
        Fresh<T> child=retiring.remove(exact);if(child!=null){child.live.set(false);child.destroyed.set(true);}
    }
    private void retire(Entry entry){if(entry.fresh!=null){entry.fresh.live.set(false);retiring.put(entry.view,entry.fresh);}}
    private void settle(Entry entry,Runnable operation){
        try{operation.run();}catch(RuntimeException error){entry.failed=true;cleanupFailed=true;}
        if(!entry.input.cleanupConfirmed()&&!entry.input.busy()){entry.failed=true;cleanupFailed=true;}
    }
    boolean navigation(String key,T view){
        thread();Entry entry=current(key,view);if(entry==null)return false;
        entry.epoch++;if(entry.fresh!=null&&!entry.fresh.claimed)entry.fresh.live.set(false);settle(entry,()->entry.input.invalidate("frame_changed"));return current(key,view)==entry;
    }
    void visibility(String key,T view,boolean visible){
        thread();Entry entry=current(key,view);if(entry==null)return;entry.hidden=!visible;
        if(!visible){if(entry.fresh!=null)entry.fresh.live.set(false);settle(entry,()->entry.input.invalidate("input_view_unavailable"));}
    }
    void manual(String key,T view,boolean value){
        thread();Entry entry=current(key,view);if(entry==null)return;entry.manual=value;
        if(value){if(entry.fresh!=null)entry.fresh.live.set(false);settle(entry,()->entry.input.invalidate("user_taken_control"));}
    }
    void stop(String key,T view){thread();Entry entry=current(key,view);if(entry!=null)settle(entry,entry.input::cancel);}
    void stopAll(){thread();for(Entry entry:new ArrayList<>(entries.values()))settle(entry,entry.input::cancel);}
    void background(boolean value){
        thread();if(closed)return;background=value;
        if(value)for(Entry entry:new ArrayList<>(entries.values())){if(entry.fresh!=null)entry.fresh.live.set(false);settle(entry,()->entry.input.invalidate("input_view_unavailable"));}
    }
    long epoch(String key,T view){thread();Entry entry=current(key,view);return entry==null?0:entry.epoch;}
    boolean inputBusy(String key,T view){thread();Entry entry=current(key,view);return entry!=null&&entry.input.busy();}
    boolean inputClean(String key,T view){thread();Entry entry=current(key,view);return entry!=null&&!entry.failed&&entry.input.cleanupConfirmed();}
    double scale(String key,T view){thread();Entry entry=current(key,view);return entry==null?Double.NaN:entry.scale;}
    void scaleChanged(String key,T view,double factor){
        thread();Entry entry=current(key,view);if(entry==null)return;
        double next=Double.isFinite(factor)&&factor>0&&factor<=32?factor:Double.NaN;
        if(Double.compare(next,entry.scale)==0)return;entry.scale=next;
        settle(entry,()->entry.input.invalidate("input_viewport_changed"));
    }
    MdoBrowserInput input(String key,T view){
        thread();Entry entry=current(key,view);if(entry==null||entry.failed)throw new IllegalStateException("Native input binding is unavailable");return entry.input;
    }
    T remove(String key){
        thread();Entry entry=entries.remove(key);if(entry==null)return null;
        retire(entry);settle(entry,entry.input::close);return entry.view;
    }
    boolean cleanupConfirmed(){
        thread();if(cleanupFailed)return false;for(Entry entry:entries.values())if(!entry.input.cleanupConfirmed())return false;return true;
    }
    @Override public void close(){
        thread();if(closed)return;closed=true;List<Entry> owned=new ArrayList<>(entries.values());entries.clear();
        for(Entry entry:owned){retire(entry);settle(entry,entry.input::close);}
    }
}
