package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.lang.ref.WeakReference;

/** Public-framework adapter. The UI controller keeps one instance for an exact
 * managed view/document generation, with an ownership predicate capturing that
 * generation and original host channel. Close before its admission changes.
 * Neither protocol workers nor retained provider callbacks read a UI object. */
final class MdoBrowserCdpView implements AutoCloseable {
    interface Owner {boolean current(WebView view);}
    private static final MdoBrowserCdpMarker.Budget CALLBACKS=new MdoBrowserCdpMarker.Budget(16);
    private final MdoBrowserCdpMarker marker;
    MdoBrowserCdpView(WebView view,Owner owner) {
        this(view,owner,new MdoBrowserCdpMarker.DocumentQuota());
    }
    MdoBrowserCdpView(WebView view,Owner owner,MdoBrowserCdpMarker.DocumentQuota document) {
        if(view==null||owner==null||Looper.myLooper()!=Looper.getMainLooper())throw new IllegalArgumentException("View marker requires its UI owner");
        WeakReference<WebView> weak=new WeakReference<>(view);Handler handler=new Handler(Looper.getMainLooper());
        marker=new MdoBrowserCdpMarker(new MdoBrowserCdpMarker.Ui(){
            public boolean onThread(){return Looper.myLooper()==Looper.getMainLooper();}
            public boolean post(Runnable task){return handler.post(task);}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        },new MdoBrowserCdpMarker.View(){
            public boolean current(){WebView current=weak.get();return current!=null&&owner.current(current)&&current.getSettings().getJavaScriptEnabled();}
            public void evaluate(String source,MdoBrowserCdpMarker.Reply reply){
                WebView current=weak.get();if(current==null||!owner.current(current))throw new IllegalStateException("Native marker owner changed");
                current.evaluateJavascript(source,reply==null?null:reply::done);
            }
        },CALLBACKS,document);
    }
    MdoBrowserCdpMarker marker(){return marker;}
    @Override public void close(){marker.close();}
}
