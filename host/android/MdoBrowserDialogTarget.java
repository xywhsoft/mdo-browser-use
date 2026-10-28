package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Native callbacks for one owned WebView. A callback URL is display metadata,
 * never a root/subframe identity or permission to execute in a different view. */
final class MdoBrowserDialogTarget extends WebChromeClient implements AutoCloseable {
    final WebView view;final MdoBrowserDialogs dialogs;
    MdoBrowserDialogTarget(WebView view,BooleanSupplier owned,LongSupplier navigation,MdoBrowserDialogs.Listener listener){
        this.view=view;
        final Handler handler=new Handler(Looper.getMainLooper());
        dialogs=new MdoBrowserDialogs(view,new MdoBrowserDialogs.Scheduler(){
            public long now(){return SystemClock.uptimeMillis();}
            public void at(Runnable task,long uptime){if(!handler.postAtTime(task,uptime))throw new IllegalStateException("Dialog scheduler stopped");}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        },owned,navigation,listener);
    }
    private boolean receive(WebView source,String kind,String url,String message,String initial,JsResult result){
        if(source!=view)return false;
        if(result==null)return true;
        dialogs.open(result,(accept,text)->{
            if(!accept)result.cancel();
            else if(result instanceof JsPromptResult)((JsPromptResult)result).confirm(text);
            else result.confirm();
        },kind,url,message,initial);
        return true;
    }
    @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){return receive(view,"alert",url,message,null,result);}
    @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){return receive(view,"confirm",url,message,null,result);}
    @Override public boolean onJsPrompt(WebView view,String url,String message,String initial,JsPromptResult result){return receive(view,"prompt",url,message,initial,result);}
    @Override public boolean onJsBeforeUnload(WebView view,String url,String message,JsResult result){return receive(view,"beforeunload",url,message,null,result);}
    @Override public void close(){dialogs.close();}
    void viewDestroyed(){dialogs.viewDestroyed(view);}
}
