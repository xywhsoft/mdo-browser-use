package org.xserver.android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.SystemClock;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;

/** Actual framework dialogs on synthetic data only. Page-world JavaScript is
 * fixture input, never a production Agent execution fallback or host-UI gate. */
final class BrowserUseDialogProbe implements AutoCloseable {
    interface Done {void complete(boolean ok,String code,int checks);}
    private final WebView view;private final Handler handler;private final Done done;
    private MdoBrowserDialogTarget target;private MdoBrowserInput input;
    private boolean closed,finished,started,blocked,beforeUnloadVerified;
    private long epoch=1,deadline,beforeEpoch;private int checks,stage,ticket,callbacks,blocks,releases,beforeCancels;
    private String previousId,expected,beforeUrl;private Runnable step;
    BrowserUseDialogProbe(WebView view,Handler handler,Done done){this.view=view;this.handler=handler;this.done=done;}
    void run(){
        view.getSettings().setJavaScriptEnabled(true);
        target=new MdoBrowserDialogTarget(view,()->!closed&&!finished&&view.isAttachedToWindow()&&view.isShown(),()->epoch,value->{
            blocked=value;if(value)blocks++;else releases++;
        },original->{if(original==epoch)beforeCancels++;});
        view.setWebChromeClient(target);
        view.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView source,String url,Bitmap icon){
                if(source!=view||closed)return;epoch++;target.dialogs.cancel();
            }
            @Override public void onPageFinished(WebView source,String url){
                if(source==view&&!closed&&!started){started=true;handler.post(()->dialogCase(0));}
            }
        });
        view.loadDataWithBaseURL("http://127.0.0.1/","<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<style>html,body{margin:0}button{position:fixed;left:0;top:0;width:100%;height:160px}</style>"+
            "<button id='activate' onclick=\"if(!event.isTrusted)return;document.body.dataset.activated='yes';"+
            "window.onbeforeunload=e=>{e.preventDefault();return e.returnValue='fixture';}\">Activate fixture</button>","text/html","UTF-8",null);
    }
    private void later(Runnable next){
        if(step!=null)handler.removeCallbacks(step);step=next;
        if(!handler.postDelayed(next,25))end(false,"dialog_fixture_scheduler_unavailable");
    }
    private void check(boolean value,String code){if(!value)throw new IllegalStateException(code);checks++;}
    private void dialogCase(int index){
        if(closed||finished)return;stage=index;callbacks=0;deadline=SystemClock.uptimeMillis()+2000;
        final int original=++ticket;String script;
        switch(index){
            case 0:script="(()=>{alert('local alert');return 'alert-ok'})()";expected=JSONObject.quote("alert-ok");break;
            case 1:script="confirm('local reject')";expected="false";break;
            case 2:script="confirm('local accept')";expected="true";break;
            case 3:script="prompt('local default','fixture-default')";expected=JSONObject.quote("fixture-default");break;
            case 4:script="prompt('local empty','fixture-default')";expected=JSONObject.quote("");break;
            case 5:script="prompt('local Unicode','fixture-default')";expected=JSONObject.quote("\u4e2d\u6587 \ud83d\ude42 \"quote\"");break;
            default:activate();return;
        }
        view.evaluateJavascript(script,result->{
            if(closed||finished||ticket!=original)return;callbacks++;
            try{check(callbacks==1&&expected.equals(result),"native_dialog_result_mismatch");
                check(target.dialogs.snapshot()==null&&!blocked&&target.dialogs.clean(),"native_dialog_not_released");
                handler.post(()->dialogCase(index+1));
            }catch(RuntimeException error){end(false,"native_dialog_result_verification_failed");}
        });
        awaitDialog(index==0?"alert":index<3?"confirm":"prompt",()->{
            MdoBrowserDialogs.Snapshot pending=target.dialogs.snapshot();
            try{
                check(pending!=null&&blocked&&callbacks==0&&pending.respondable,"native_dialog_not_retained");
                check(previousId==null||!previousId.equals(pending.id),"native_dialog_reused_id");
                MdoBrowserDialogs.Result stale=target.dialogs.respond(previousId==null?"dialog-foreign":previousId,true,false,null);
                check(!stale.ok&&!stale.effectMayHaveOccurred&&target.dialogs.snapshot()!=null,"native_dialog_stale_response_dispatched");
                previousId=pending.id;
                MdoBrowserDialogs.Result reply=target.dialogs.respond(pending.id,index!=1,index==4||index==5,
                    index==4?"":index==5?"\u4e2d\u6587 \ud83d\ude42 \"quote\"":null);
                check(reply.ok&&reply.cleanupConfirmed,"native_dialog_response_unconfirmed");
                awaitCallback(original);
            }catch(RuntimeException error){end(false,"native_dialog_response_verification_failed");}
        });
    }
    private void awaitDialog(String kind,Runnable ready){
        if(closed||finished)return;
        MdoBrowserDialogs.Snapshot pending=target.dialogs.snapshot();
        if(pending!=null){if(!kind.equals(pending.kind)){end(false,"native_dialog_kind_mismatch");return;}ready.run();return;}
        if(SystemClock.uptimeMillis()>=deadline){end(false,"native_dialog_not_observed");return;}
        later(()->awaitDialog(kind,ready));
    }
    private void awaitCallback(int original){
        if(closed||finished||original!=ticket||callbacks==1)return;
        if(SystemClock.uptimeMillis()>=deadline){end(false,"native_dialog_callback_timed_out");return;}
        later(()->awaitCallback(original));
    }
    private void activate(){
        if(closed||finished)return;
        input=new MdoBrowserInput(new MdoBrowserInputTarget(view,()->epoch,()->!closed&&!finished),new MdoBrowserInput.Scheduler(){
            public long now(){return SystemClock.uptimeMillis();}
            public void post(Runnable task,long delay){if(!handler.postDelayed(task,delay))throw new IllegalStateException();}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        });
        try{input.tap(view.getWidth()/2f,Math.min(80,view.getHeight()/4f),result->{
            if(closed||finished)return;
            if(!result.ok||!result.effectMayHaveOccurred||!result.cleanupConfirmed){end(false,"native_dialog_activation_input_failed");return;}
            checks++;deadline=SystemClock.uptimeMillis()+2000;activationReady();
        });}catch(MdoBrowserInput.Failure|RuntimeException error){end(false,"native_dialog_activation_input_failed");}
    }
    private void activationReady(){
        if(closed||finished)return;
        view.evaluateJavascript("document.body.dataset.activated==='yes'",result->{
            if(closed||finished)return;
            if(!"true".equals(result)){
                if(SystemClock.uptimeMillis()>=deadline){end(false,"native_dialog_activation_not_trusted");return;}
                later(this::activationReady);return;
            }
            checks++;beforeEpoch=epoch;beforeUrl=view.getUrl();deadline=SystemClock.uptimeMillis()+2000;
            view.loadUrl("about:blank");awaitDialog("beforeunload",this::cancelBeforeUnload);
        });
    }
    private void cancelBeforeUnload(){
        if(closed||finished)return;
        try{
            MdoBrowserDialogs.Snapshot pending=target.dialogs.snapshot();
            check(epoch==beforeEpoch&&blocked&&pending!=null,"native_beforeunload_context_changed");
            MdoBrowserDialogs.Result reply=target.dialogs.respond(pending.id,false,false,null);
            check(reply.ok&&reply.cleanupConfirmed&&beforeCancels==1,"native_beforeunload_cancel_unconfirmed");
            view.evaluateJavascript("document.body.dataset.activated==='yes'&&Boolean(document.getElementById('activate'))",result->{
                if(closed||finished)return;
                try{check("true".equals(result)&&epoch==beforeEpoch&&beforeUrl!=null&&beforeUrl.equals(view.getUrl())&&
                    target.dialogs.snapshot()==null&&!blocked,"native_beforeunload_original_document_not_restored");
                    beforeUnloadVerified=true;handler.post(this::closePending);
                }catch(RuntimeException error){end(false,"native_beforeunload_recovery_failed");}
            });
        }catch(RuntimeException error){end(false,"native_beforeunload_cancel_failed");}
    }
    private void closePending(){
        if(closed||finished)return;callbacks=0;deadline=SystemClock.uptimeMillis()+2000;final int original=++ticket;
        view.evaluateJavascript("confirm('close fixture')",result->{
            if(closed||finished||ticket!=original)return;callbacks++;
            try{check(callbacks==1&&"false".equals(result)&&target.dialogs.snapshot()==null&&!blocked&&target.dialogs.clean(),"native_dialog_close_not_cancelled");
                check(blocks==releases&&blocks==8,"native_dialog_blockers_not_balanced");
                end(true,"native_dialog_framework_verified");
            }catch(RuntimeException error){end(false,"native_dialog_close_verification_failed");}
        });
        awaitDialog("confirm",()->{target.close();awaitCallback(original);});
    }
    private void end(boolean ok,String code){
        if(closed||finished)return;finished=true;if(step!=null)handler.removeCallbacks(step);
        handler.post(()->done.complete(ok,code,checks));
    }
    boolean beforeUnloadVerified(){return beforeUnloadVerified;}
    void viewDestroyed(){if(target!=null)target.viewDestroyed();}
    @Override public void close(){closed=true;if(step!=null)handler.removeCallbacks(step);if(input!=null)input.close();if(target!=null)target.close();}
}
