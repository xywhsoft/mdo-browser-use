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
        this(view,owner,document,false);
    }
    private MdoBrowserCdpView(WebView view,Owner owner,MdoBrowserCdpMarker.DocumentQuota document,boolean network) {
        if(view==null||owner==null||Looper.myLooper()!=Looper.getMainLooper())throw new IllegalArgumentException("View marker requires its UI owner");
        WeakReference<WebView> weak=new WeakReference<>(view);Handler handler=new Handler(Looper.getMainLooper());
        MdoBrowserCdpMarker.Ui ui=new MdoBrowserCdpMarker.Ui(){
            public boolean onThread(){return Looper.myLooper()==Looper.getMainLooper();}
            public boolean post(Runnable task){return handler.post(task);}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        };MdoBrowserCdpMarker.View target=new MdoBrowserCdpMarker.View(){
            public boolean current(){WebView current=weak.get();return current!=null&&owner.current(current)&&current.getSettings().getJavaScriptEnabled();}
            public void evaluate(String source,MdoBrowserCdpMarker.Reply reply){
                WebView current=weak.get();if(current==null||!owner.current(current))throw new IllegalStateException("Native marker owner changed");
                current.evaluateJavascript(source,reply==null?null:reply::done);
            }
        };
        marker=network?MdoBrowserCdpMarker.network(ui,target,CALLBACKS,document):new MdoBrowserCdpMarker(ui,target,CALLBACKS,document);
    }
    static MdoBrowserCdpView network(WebView view,Owner owner,MdoBrowserCdpMarker.DocumentQuota document){return new MdoBrowserCdpView(view,owner,document,true);}
    MdoBrowserCdpMarker marker(){return marker;}
    @Override public void close(){marker.close();}
}
