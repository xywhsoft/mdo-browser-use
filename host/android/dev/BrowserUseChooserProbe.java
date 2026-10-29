package org.xserver.android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.SystemClock;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Actual framework chooser callbacks on synthetic data. Page-world scripts
 * only prepare/read this fixture, never provide a product execution fallback. */
final class BrowserUseChooserProbe implements AutoCloseable {
    interface Done {void complete(boolean ok,String code,int checks);}
    private final WebView view;private final Handler handler;private final Done done;
    private MdoBrowserChooserTarget target;private MdoBrowserInput input;
    private boolean closed,finished,started,blocked;private int checks,stage,blocks,releases,ticket;
    private long epoch=1,deadline,navigationFrom;private String previousId;private Runnable step;
    BrowserUseChooserProbe(WebView view,Handler handler,Done done){this.view=view;this.handler=handler;this.done=done;}
    void run(){
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setAllowFileAccess(false);view.getSettings().setAllowContentAccess(false);
        target=new MdoBrowserChooserTarget(view,()->!closed&&!finished&&view.isAttachedToWindow()&&view.isShown(),
            ()->epoch,value->{},original->{},null,value->{blocked=value;if(value)blocks++;else releases++;});
        target.choosers.interception(true);view.setWebChromeClient(target);
        // This qualifies the raw callback, not product admission. Its one
        // gesture may open a chooser synchronously while its UP is returning.
        input=new MdoBrowserInput(new MdoBrowserInputTarget(view,()->epoch,()->!closed&&!finished),new MdoBrowserInput.Scheduler(){
            public long now(){return SystemClock.uptimeMillis();}
            public void post(Runnable task,long delay){if(!handler.postDelayed(task,delay))throw new IllegalStateException("chooser_fixture_scheduler_unavailable");}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        });
        view.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView source,String url,Bitmap icon){
                if(source!=view||closed)return;epoch++;target.choosers.cancel();
            }
            @Override public void onPageFinished(WebView source,String url){
                if(source!=view||closed||finished||started)return;started=true;
                deadline=SystemClock.uptimeMillis()+2500;later(()->prepare(0));
            }
        });
        view.loadDataWithBaseURL("http://127.0.0.1/","<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<style>html,body{margin:0}input{position:fixed;left:0;top:0;width:100%;height:160px}</style>"+
            "<input id='file' type='file' accept='text/plain'><script>window.fixtureClicks=0;"+
            "document.getElementById('file').addEventListener('click',e=>{if(e.isTrusted)window.fixtureClicks++});</script>","text/html","UTF-8",null);
    }
    private void check(boolean value,String code){if(!value)throw new IllegalStateException(code);checks++;}
    private void later(Runnable next){
        if(closed||finished)return;if(step!=null)handler.removeCallbacks(step);
        step=()->{try{next.run();}catch(RuntimeException|OutOfMemoryError error){end(false,"chooser_fixture_callback_failed");}};
        if(!handler.postDelayed(step,25))end(false,"chooser_fixture_scheduler_unavailable");
    }
    private void read(String script,Runnable ready){
        if(closed||finished)return;final int original=++ticket;final long document=epoch;
        view.evaluateJavascript(script,value->{
            if(closed||finished||ticket!=original)return;
            ticket++; // A duplicate read reply must not admit another gesture.
            if(document!=epoch){end(false,"chooser_fixture_document_changed");return;}
            if(!"true".equals(value)){
                if(SystemClock.uptimeMillis()>=deadline)end(false,"chooser_fixture_page_verification_failed");
                else later(()->read(script,ready));return;
            }
            try{checks++;ready.run();}catch(RuntimeException error){end(false,error.getMessage());}
        });
    }
    private void prepare(int next){
        if(closed||finished)return;
        if(!view.isAttachedToWindow()||!view.isShown()||!view.hasWindowFocus()||view.getWidth()<2||view.getHeight()<2){
            if(SystemClock.uptimeMillis()>=deadline){end(false,"chooser_fixture_not_interactive");return;}
            later(()->prepare(next));return;
        }
        stage=next;deadline=SystemClock.uptimeMillis()+2500;
        read("(()=>{const f=document.getElementById('file');if(!f||f.files.length||f.value)return false;"+
            "f.multiple="+(next==1?"true":"false")+";return window.fixtureClicks==="+next+"})()",this::tap);
    }
    private void tap(){
        if(closed||finished)return;
        try{input.tap(view.getWidth()/2f,Math.min(80,view.getHeight()/4f),result->{
            if(closed||finished)return;
            if(!result.ok||!result.effectMayHaveOccurred||!result.cleanupConfirmed){end(false,"chooser_fixture_native_input_failed");return;}
            checks++;deadline=SystemClock.uptimeMillis()+2500;awaitChooser();
        });}catch(MdoBrowserInput.Failure|RuntimeException error){end(false,"chooser_fixture_native_input_failed");}
    }
    private void awaitChooser(){
        if(closed||finished)return;
        MdoBrowserChoosers.Snapshot pending=target.choosers.snapshot();
        if(pending==null){
            if(SystemClock.uptimeMillis()>=deadline){end(false,"native_chooser_not_observed");return;}
            later(this::awaitChooser);return;
        }
        try{
            check(blocked&&pending.cancelable&&pending.navigation==epoch,"native_chooser_not_retained");
            check(pending.mode==(stage==1?1:0)&&pending.multiple==(stage==1)&&!pending.directory&&!pending.capture,"native_chooser_mode_mismatch");
            check(pending.accept.contains("text/plain"),"native_chooser_accept_mismatch");
            check(previousId==null||!previousId.equals(pending.id),"native_chooser_identity_reused");
            check(!target.choosers.cancel(previousId==null?"chooser-foreign":previousId).ok&&target.choosers.snapshot()!=null,"native_chooser_stale_cancel_dispatched");
            previousId=pending.id;
            if(stage==2){
                navigationFrom=epoch;deadline=SystemClock.uptimeMillis()+2500;
                view.loadDataWithBaseURL("http://127.0.0.1/","<!doctype html><script>window.fixtureNext='next'</script>","text/html","UTF-8",null);
                later(this::awaitNavigation);return;
            }
            if(stage==0){
                MdoBrowserChoosers.Result cancelled=target.choosers.cancel(pending.id);
                check(cancelled.ok&&cancelled.effectMayHaveOccurred&&cancelled.cleanupConfirmed,"native_chooser_cancel_unconfirmed");
            }else target.choosers.interception(false);
            check(target.choosers.ready()&&!blocked&&blocks==releases,"native_chooser_not_released");
            check(!target.choosers.cancel(pending.id).ok,"native_chooser_replayed_cancel");
            read("document.getElementById('file').files.length===0&&document.getElementById('file').value===''&&window.fixtureClicks==="+(stage+1),()->{
                target.choosers.interception(true);deadline=SystemClock.uptimeMillis()+2500;later(()->prepare(stage+1));
            });
        }catch(RuntimeException error){end(false,error.getMessage());}
    }
    private void awaitNavigation(){
        if(closed||finished)return;
        if(epoch<=navigationFrom||!target.choosers.ready()){
            if(SystemClock.uptimeMillis()>=deadline){end(false,"native_chooser_navigation_cancel_unconfirmed");return;}
            later(this::awaitNavigation);return;
        }
        read("window.fixtureNext==='next'",()->{
            check(!blocked&&blocks==3&&releases==3,"native_chooser_blocker_count_mismatch");
            check(!target.choosers.cancel(previousId).ok,"native_chooser_navigation_cancel_replayed");
            target.close();check(target.choosers.ready(),"native_chooser_close_unclean");
            end(true,"native_chooser_retention_cancellation_verified");
        });
    }
    private void end(boolean ok,String code){
        if(closed||finished)return;finished=true;ticket++;if(step!=null)handler.removeCallbacks(step);
        if(!handler.post(()->{if(!closed)done.complete(ok,code,checks);}))done.complete(false,"chooser_fixture_scheduler_unavailable",checks);
    }
    @Override public void close(){if(closed)return;closed=true;ticket++;if(step!=null)handler.removeCallbacks(step);if(input!=null)input.close();if(target!=null)target.close();}
    void viewDestroyed(){if(target!=null)target.viewDestroyed();}
}
