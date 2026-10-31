package org.xserver.android;

import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.lang.ref.WeakReference;

/** Exact UI-owned physical view/native callback join. Workers see only the
 * original lease and a data-only native rejection receipt, never a WebView. */
final class MdoBrowserCdpPopupNative {
    private final MdoBrowserCdpMarker.Lease lease;private final WeakReference<WebView> view;
    private final WeakReference<MdoBrowserPopups<WebView>> owner;private boolean claimed;
    private volatile MdoBrowserPopups.Stop stop;private volatile String failure;
    MdoBrowserCdpPopupNative(MdoBrowserCdpView adapter,WebView exact,MdoBrowserPopups<WebView> core)throws IOException {
        thread();if(adapter==null||core==null||!core.protocolSource(exact))throw new IOException("cdp_popup_native_source_invalid");
        lease=adapter.physicalLease(exact);view=new WeakReference<>(exact);owner=new WeakReference<>(core);
    }
    private static void thread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native popup source requires its UI owner");}
    MdoBrowserCdpMarker.Lease lease(){return lease;}
    void claim(MdoBrowserCdpMarker.Lease original)throws IOException {
        thread();if(original!=lease||!lease.current())throw new IOException("cdp_popup_native_source_invalid");
        if(claimed)throw new IOException("cdp_popup_native_source_busy");claimed=true;
    }
    void abandon(){thread();if(stop==null&&failure==null)claimed=false;}
    void reject(){
        thread();if(stop!=null||failure!=null)return;
        WebView exact=view.get();MdoBrowserPopups<WebView> core=owner.get();
        if(!claimed||exact==null||core==null||!core.original(exact)){failure="cdp_popup_native_source_lost";return;}
        try{stop=core.stopForProtocol();}catch(RuntimeException|OutOfMemoryError error){failure="cdp_popup_native_rejection_unconfirmed";}
    }
    boolean confirmed(){MdoBrowserPopups.Stop value=stop;return failure==null&&value!=null&&value.confirmed();}
    String failure(){
        if(failure!=null)return failure;MdoBrowserPopups.Stop value=stop;
        if(value==null||value.pending()||value.confirmed())return null;
        return "popup_native_already_adopted".equals(value.failure())?"cdp_popup_native_already_adopted":"cdp_popup_native_rejection_unconfirmed";
    }
    void finish(boolean sourceReleased){
        thread();if(!sourceReleased)return;
        MdoBrowserPopups.Stop value=stop;
        if(value==null){abandon();return;}
        if(confirmed()&&value.release()){claimed=false;stop=null;}
    }
}
