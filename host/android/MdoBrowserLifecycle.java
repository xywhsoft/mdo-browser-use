package org.xserver.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Owns exact native view instances, not reusable tab names. UI-thread confined.
 * Lifecycle input cleanup runs before the caller detaches/destroys its view. */
final class MdoBrowserLifecycle<T> implements AutoCloseable {
    interface Factory<T> {MdoBrowserInput create(T view,LongSupplier epoch,BooleanSupplier admitted);}
    private final class Entry {
        final T view;MdoBrowserInput input;long epoch=1;boolean hidden,manual,failed;
        Entry(T view){this.view=view;}
    }
    private final Map<String,Entry> entries=new LinkedHashMap<>();private final Factory<T> factory;
    private final Thread owner=Thread.currentThread();private boolean closed,cleanupFailed,background;
    MdoBrowserLifecycle(Factory<T> factory){if(factory==null)throw new IllegalArgumentException("Missing native input factory");this.factory=factory;}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Browser lifecycle requires its owning UI thread");}
    private Entry current(String key,T view){Entry entry=entries.get(key);return !closed&&entry!=null&&entry.view==view?entry:null;}
    boolean owns(String key,T view){thread();return current(key,view)!=null;}
    int size(){thread();return entries.size();}
    T get(String key){thread();Entry entry=entries.get(key);return entry==null?null:entry.view;}
    List<T> values(){thread();List<T> result=new ArrayList<>();for(Entry entry:entries.values())result.add(entry.view);return result;}
    void put(String key,T view){
        thread();if(closed)throw new IllegalStateException("Browser lifecycle closed");
        if(key==null||key.isEmpty()||key.length()>100||view==null)throw new IllegalArgumentException("Invalid native view binding");
        if(entries.containsKey(key))throw new IllegalStateException("Browser identity is already owned");
        if(entries.size()>=8)throw new IllegalStateException("Native browser view limit reached (8)");
        for(Entry entry:entries.values())if(entry.view==view)throw new IllegalStateException("Native view is already owned");
        Entry entry=new Entry(view);
        entry.input=factory.create(view,()->entry.epoch,()->current(key,view)==entry&&!background&&!entry.hidden&&!entry.manual&&!entry.failed);
        if(entry.input==null)throw new IllegalStateException("Missing native input binding");entries.put(key,entry);
    }
    private void settle(Entry entry,Runnable operation){
        try{operation.run();}catch(RuntimeException error){entry.failed=true;cleanupFailed=true;}
        if(!entry.input.cleanupConfirmed()&&!entry.input.busy()){entry.failed=true;cleanupFailed=true;}
    }
    boolean navigation(String key,T view){
        thread();Entry entry=current(key,view);if(entry==null)return false;
        entry.epoch++;settle(entry,()->entry.input.invalidate("frame_changed"));return current(key,view)==entry;
    }
    void visibility(String key,T view,boolean visible){
        thread();Entry entry=current(key,view);if(entry==null)return;entry.hidden=!visible;
        if(!visible)settle(entry,()->entry.input.invalidate("input_view_unavailable"));
    }
    void manual(String key,T view,boolean value){
        thread();Entry entry=current(key,view);if(entry==null)return;entry.manual=value;
        if(value)settle(entry,()->entry.input.invalidate("user_taken_control"));
    }
    void stop(String key,T view){thread();Entry entry=current(key,view);if(entry!=null)settle(entry,entry.input::cancel);}
    void background(boolean value){
        thread();if(closed)return;background=value;
        if(value)for(Entry entry:new ArrayList<>(entries.values()))settle(entry,()->entry.input.invalidate("input_view_unavailable"));
    }
    long epoch(String key,T view){thread();Entry entry=current(key,view);return entry==null?0:entry.epoch;}
    MdoBrowserInput input(String key,T view){
        thread();Entry entry=current(key,view);if(entry==null||entry.failed)throw new IllegalStateException("Native input binding is unavailable");return entry.input;
    }
    T remove(String key){
        thread();Entry entry=entries.remove(key);if(entry==null)return null;
        settle(entry,entry.input::close);return entry.view;
    }
    boolean cleanupConfirmed(){
        thread();if(cleanupFailed)return false;for(Entry entry:entries.values())if(!entry.input.cleanupConfirmed())return false;return true;
    }
    @Override public void close(){
        thread();if(closed)return;closed=true;List<Entry> owned=new ArrayList<>(entries.values());entries.clear();
        for(Entry entry:owned)settle(entry,entry.input::close);
    }
}
