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
    /** Original consumed native handoff plus its exact physical child marker.
     * No child/target selector or UI predicate crosses to the shared worker. */
    static final class Child implements AutoCloseable {
        private final MdoBrowserPopups.Scope source;private final MdoBrowserCdpMarker.Lease opener;
        private final WeakReference<MdoBrowserCdpMarker> marker;private final MdoBrowserCdpMarker.Lease lease;
        private final AtomicBoolean live=new AtomicBoolean(true),queued=new AtomicBoolean(),attempted=new AtomicBoolean();
        private final AtomicReference<MdoBrowserCdpSession.ControlResult> proof=new AtomicReference<>();
        private Child(MdoBrowserPopups.Scope source,MdoBrowserCdpMarker.Lease opener,MdoBrowserCdpMarker marker){this.source=source;this.opener=opener;this.marker=new WeakReference<>(marker);lease=marker.lease();}
        boolean current(){return live.get()&&source.current()&&opener.current()&&lease.current();}
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
    MdoBrowserPopups.Adoption adopt(MdoBrowserPopups.Pending original,WebView child){
        thread();MdoBrowserPopups<WebView> core=owner.get();
        if(!claimed||core==null)throw new IllegalStateException("popup_request_unavailable");
        return core.adoptProtocol(scope,original,child);
    }
    Child child(MdoBrowserPopups.Adoption original,WebView exact,MdoBrowserCdpView adapter)throws IOException {
        thread();try{MdoBrowserPopups<WebView> core=owner.get();
            if(!claimed||core==null||adapter==null||!adapter.popupCurrent(exact)||adapter.physicalLease(exact).busy())throw new IOException("cdp_popup_native_child_invalid");
            Child child=new Child(scope,lease,adapter.marker());
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
