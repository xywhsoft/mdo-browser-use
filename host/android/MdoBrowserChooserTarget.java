package org.xserver.android;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Retains/cancels a framework chooser. It does not grant unvalidated URIs,
 * identify a subframe from a callback URL, or enable global automation. */
final class MdoBrowserChooserTarget extends MdoBrowserWindowTarget {
    final MdoBrowserChoosers choosers;
    MdoBrowserChooserTarget(WebView view,BooleanSupplier owned,LongSupplier navigation,MdoBrowserDialogs.Listener dialogs,
            LongConsumer beforeUnloadCancelled,Windows windows,MdoBrowserChoosers.Listener listener){
        super(view,owned,navigation,dialogs,beforeUnloadCancelled,windows);
        final Handler handler=new Handler(Looper.getMainLooper());
        choosers=new MdoBrowserChoosers(view,new MdoBrowserChoosers.Scheduler(){
            public long now(){return SystemClock.uptimeMillis();}
            public void at(Runnable task,long uptime){if(!handler.postAtTime(task,uptime))throw new IllegalStateException("Chooser scheduler stopped");}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        },owned,navigation,listener);
    }
    @Override public boolean onShowFileChooser(WebView source,ValueCallback<Uri[]> callback,WebChromeClient.FileChooserParams params){
        if(source!=view||callback==null||Looper.myLooper()!=Looper.getMainLooper())return false;
        if(choosers.known(callback))return true;
        if(!choosers.intercepting()||params==null)return false;
        try{return choosers.open(callback,()->callback.onReceiveValue(null),params.getMode(),params.getAcceptTypes(),params.isCaptureEnabled());}
        catch(RuntimeException|OutOfMemoryError error){
            if(choosers.known(callback))return true;
            if(!choosers.rejectOnce(callback))return false;
            try{callback.onReceiveValue(null);}catch(RuntimeException|OutOfMemoryError ignored){choosers.rejectionFailed();}
            return true;
        }
    }
    @Override public void close(){choosers.close();super.close();}
    @Override void viewDestroyed(){choosers.viewDestroyed(view);super.viewDestroyed();}
}
