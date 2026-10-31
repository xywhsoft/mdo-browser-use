package org.xserver.android;

import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Exact UI-owned physical view/native callback join. Workers see only the
 * original lease and a data-only native rejection receipt, never a WebView. */
final class MdoBrowserCdpPopupNative {
    /** UI-owned construction result. Only this class invokes the native new;
     * callers cannot certify a supplied/preexisting WebView as post-capture. */
    static final class Created {
        private final WeakReference<MdoBrowserCdpPopupNative> owner;private final WebView view;
        private final MdoBrowserLifecycle.Fresh<WebView> birth;private final MdoBrowserCdpPopupWorker.Allocation allocation;
        private Created(MdoBrowserCdpPopupNative owner,WebView view,MdoBrowserLifecycle.Fresh<WebView> birth,MdoBrowserCdpPopupWorker.Allocation allocation){this.owner=new WeakReference<>(owner);this.view=view;this.birth=birth;this.allocation=allocation;}
        WebView view(){thread();MdoBrowserCdpPopupNative original=owner.get();if(!birth.current()||!allocation.current()||original==null||!original.current())throw new IllegalStateException("cdp_popup_native_child_invalid");return view;}
    }
    /** Original consumed native handoff plus its exact physical child marker.
     * No child/target selector or UI predicate crosses to the shared worker. */
    static final class Child implements AutoCloseable {
        private final MdoBrowserPopups.Scope source;private final MdoBrowserCdpMarker.Lease opener;
        private final WeakReference<MdoBrowserCdpMarker> marker;private final MdoBrowserCdpMarker.Lease lease;
        private final MdoBrowserLifecycle.Fresh<WebView> birth;
        private final MdoBrowserCdpPopupWorker.Allocation allocation;
        private final AtomicBoolean live=new AtomicBoolean(true),queued=new AtomicBoolean(),attempted=new AtomicBoolean();
        private final AtomicReference<MdoBrowserCdpSession.ControlResult> proof=new AtomicReference<>();
        private Child(MdoBrowserPopups.Scope source,MdoBrowserCdpMarker.Lease opener,MdoBrowserCdpMarker marker,MdoBrowserLifecycle.Fresh<WebView> birth,MdoBrowserCdpPopupWorker.Allocation allocation){this.source=source;this.opener=opener;this.marker=new WeakReference<>(marker);lease=marker.lease();this.birth=birth;this.allocation=allocation;}
        boolean current(){return live.get()&&birth.current()&&source.current()&&opener.current()&&lease.current()&&(allocation==null||allocation.current());}
        boolean allocated(){return allocation!=null;}
        boolean includes(MdoBrowserCdpPopupBroker.Candidate original){return allocation!=null&&allocation.includes(original);}
        boolean nativeDestroyed(){return birth.destroyed();}
        boolean belongsTo(MdoBrowserCdpMarker.Lease exact){return opener==exact;}
        boolean ownedBy(MdoBrowserCdpBinding.Network.PopupSource source){return source!=null&&source.owns(opener);}
        MdoBrowserCdpMarker marker(){return marker.get();}
        boolean enqueue(){return current()&&queued.compareAndSet(false,true);}
        boolean claim(){return current()&&queued.get()&&attempted.compareAndSet(false,true);}
        MdoBrowserCdpSession.ControlResult proof(){return proof.get();}
        void finish(boolean ok,String code,int protocol,boolean effect){proof.compareAndSet(null,new MdoBrowserCdpSession.ControlResult(ok,code,protocol,effect));}
        @Override public void close(){live.set(false);finish(false,"cdp_popup_native_child_retired",0,attempted.get());MdoBrowserCdpMarker original=marker.get();if(original!=null)original.close();else lease.invalidate();}
    }
    private final MdoBrowserCdpMarker.Lease lease;private final WeakReference<WebView> view;
    private final WeakReference<MdoBrowserPopups<WebView>> owner;private final WeakReference<MdoBrowserCdpView> adapter;
    private boolean claimed,released;private volatile MdoBrowserPopups.Scope scope;
    private volatile MdoBrowserPopups.Stop stop;private volatile String failure;
    MdoBrowserCdpPopupNative(MdoBrowserCdpView adapter,WebView exact,MdoBrowserPopups<WebView> core)throws IOException {
        thread();if(adapter==null||core==null||!core.protocolSource(exact))throw new IOException("cdp_popup_native_source_invalid");
        if(!adapter.popupCurrent(exact))throw new IOException("cdp_popup_native_source_invalid");
        lease=adapter.physicalLease(exact);view=new WeakReference<>(exact);owner=new WeakReference<>(core);this.adapter=new WeakReference<>(adapter);
    }
    private static void thread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native popup source requires its UI owner");}
    MdoBrowserCdpMarker.Lease lease(){return lease;}
    void claim(MdoBrowserCdpMarker.Lease original)throws IOException {
        thread();if(original!=lease||!lease.current())throw new IOException("cdp_popup_native_source_invalid");
        if(claimed&&released)finish(true);
        if(claimed)throw new IOException("cdp_popup_native_source_busy");
        WebView exact=view.get();MdoBrowserPopups<WebView> core=owner.get();MdoBrowserCdpView nativeView=adapter.get();
        if(exact==null||core==null||nativeView==null||!nativeView.popupCurrent(exact))throw new IOException("cdp_popup_native_source_invalid");
        try{scope=core.claimProtocol(exact,()->nativeView.popupCurrent(exact));}
        catch(RuntimeException|OutOfMemoryError error){throw new IOException("cdp_popup_native_source_busy");}
        claimed=true;released=false;
    }
    void abandon(){
        thread();MdoBrowserPopups<WebView> core=owner.get();
        if(stop==null&&failure==null&&scope!=null&&core!=null&&core.abandonProtocol(scope)){claimed=false;scope=null;}
    }
    /** Called only on the original UI before exposing preparation/candidates. */
    boolean current(){thread();MdoBrowserPopups<WebView> core=owner.get();return claimed&&core!=null&&core.protocolCurrent(scope);}
    boolean ready(){thread();MdoBrowserPopups<WebView> core=owner.get();return claimed&&core!=null&&core.readyProtocol(scope);}
    boolean live(){MdoBrowserPopups.Scope value=scope;return value!=null&&value.current();}
    MdoBrowserPopups.Pending pending(){thread();MdoBrowserPopups<WebView> core=owner.get();return claimed&&core!=null?core.pendingProtocol(scope):null;}
    Created construct(MdoBrowserCdpPopupWorker.Allocation allocation,MdoBrowserLifecycle<WebView> registry,String key)throws IOException {
        thread();WebView opener=view.get();
        if(allocation==null||registry==null||opener==null||!current()||pending()!=allocation.pending)throw new IOException("cdp_popup_native_allocation_unavailable");
        try{registry.popupAdmission(key,opener);}catch(RuntimeException error){throw new IOException("cdp_popup_native_child_unavailable");}
        if(!allocation.claim(this))throw new IOException("cdp_popup_native_allocation_unavailable");
        WebView child=null;
        try{
            child=new WebView(opener.getContext());
            MdoBrowserLifecycle.Fresh<WebView> birth=registry.putPopup(key,opener,child);
            if(!current()||!allocation.current()||pending()!=allocation.pending)throw new IllegalStateException("cdp_popup_native_allocation_unavailable");
            return new Created(this,child,birth,allocation);
        }catch(RuntimeException|Error error){
            boolean clean=true;if(child!=null)try{
                if(!registry.nativeDestroyed(child)){if(registry.owns(key,child))registry.remove(key);child.destroy();registry.viewDestroyed(child);}
            }catch(RuntimeException|Error cleanup){clean=false;}
            reject();if(!clean)failure="cdp_popup_native_child_cleanup_unconfirmed";
            throw new IOException(clean?"cdp_popup_native_construct_failed":"cdp_popup_native_child_cleanup_unconfirmed");
        }
    }
    MdoBrowserPopups.Adoption adopt(Created original){
        thread();if(original==null||original.owner.get()!=this||!original.allocation.current())throw new IllegalStateException("popup_request_unavailable");
        return adopt(original.allocation.pending,original.birth);
    }
    MdoBrowserPopups.Adoption adopt(MdoBrowserPopups.Pending original,MdoBrowserLifecycle.Fresh<WebView> child){
        thread();MdoBrowserPopups<WebView> core=owner.get();
        if(!claimed||core==null||original==null||core.pendingProtocol(scope)!=original||!original.current()||child==null)throw new IllegalStateException("popup_request_unavailable");
        return core.adoptProtocol(scope,original,child.claim(view.get()));
    }
    Child child(MdoBrowserPopups.Adoption original,MdoBrowserLifecycle.Fresh<WebView> birth,MdoBrowserCdpView adapter)throws IOException {
        return child(original,birth,adapter,null);
    }
    Child child(MdoBrowserPopups.Adoption original,Created created,MdoBrowserCdpView adapter)throws IOException {
        thread();if(created==null||created.owner.get()!=this||!created.allocation.current())throw new IOException("cdp_popup_native_child_invalid");
        return child(original,created.birth,adapter,created.allocation);
    }
    private Child child(MdoBrowserPopups.Adoption original,MdoBrowserLifecycle.Fresh<WebView> birth,MdoBrowserCdpView adapter,MdoBrowserCdpPopupWorker.Allocation allocation)throws IOException {
        thread();try{MdoBrowserPopups<WebView> core=owner.get();
            WebView exact=birth==null?null:birth.attached(view.get());
            if(!claimed||core==null||adapter==null||!adapter.popupCurrent(exact)||adapter.physicalLease(exact).busy())throw new IOException("cdp_popup_native_child_invalid");
            Child child=new Child(scope,lease,adapter.marker(),birth,allocation);
            if(!core.claimAdopted(scope,original,exact))throw new IOException("cdp_popup_native_child_invalid");return child;
        }catch(RuntimeException|OutOfMemoryError error){throw new IOException("cdp_popup_native_child_invalid");}
    }
    void reject(){
        thread();if(stop!=null||failure!=null)return;
        WebView exact=view.get();MdoBrowserPopups<WebView> core=owner.get();
        if(!claimed||exact==null||core==null||!core.original(exact)){failure="cdp_popup_native_source_lost";return;}
        try{stop=core.stopForProtocol(scope);}catch(RuntimeException|OutOfMemoryError error){failure="cdp_popup_native_rejection_unconfirmed";}
    }
    boolean confirmed(){MdoBrowserPopups.Stop value=stop;return failure==null&&value!=null&&value.confirmed();}
    String failure(){
        if(failure!=null)return failure;MdoBrowserPopups.Stop value=stop;
        if(value==null||value.pending()||value.confirmed())return null;
        return "popup_native_already_adopted".equals(value.failure())?"cdp_popup_native_already_adopted":"cdp_popup_native_rejection_unconfirmed";
    }
    void finish(boolean sourceReleased){
        thread();if(!sourceReleased)return;released=true;
        MdoBrowserPopups.Stop value=stop;
        if(value==null){abandon();return;}
        if(confirmed()&&value.release()){claimed=false;stop=null;scope=null;}
    }
}
