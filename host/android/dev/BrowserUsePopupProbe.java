package org.xserver.android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Actual WebView transport gate, independent of isolated-world support.
 * Page-world JS only produces/reads synthetic fixture contents, never Agent
 * execution. No remote page, model, application Home or alternate provider. */
final class BrowserUsePopupProbe implements AutoCloseable {
    interface Done {void complete(boolean ok,String code,int checks);}
    private final WebView view;private final Handler handler;private final Done done;
    private final MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(8);
    private MdoBrowserPopups<WebView> core;private MdoBrowserWindowTarget target,childTarget;
    private WebView child;private boolean closed,finished,started,blocked,childClosed;
    private long epoch=1,deadline,cancelEpoch;private int checks,creates,closes,accepts;private String previous;
    private Runnable step;
    BrowserUsePopupProbe(WebView view,Handler handler,Done done){this.view=view;this.handler=handler;this.done=done;}
    void run(){
        core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
            public long now(){return SystemClock.uptimeMillis();}
            public void at(Runnable task,long time){if(!handler.postAtTime(task,time))throw new IllegalStateException("Fixture scheduler stopped");}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        },budget,()->!closed&&!finished&&view.isAttachedToWindow()&&view.isShown(),()->epoch,value->blocked=value);
        target=new MdoBrowserWindowTarget(view,()->!closed&&!finished,()->epoch,value->{},original->{},new MdoBrowserWindowTarget.Windows(){
            public boolean create(WebView source,boolean gesture,MdoBrowserPopups.Transport<WebView> transport){
                if(source!=view)return false;creates++;return core.open(transport,gesture);
            }
            public void close(WebView source){end(false,"popup_fixture_opener_closed");}
        });
        view.getSettings().setJavaScriptEnabled(true);view.getSettings().setSupportMultipleWindows(true);
        view.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);view.getSettings().setAllowFileAccess(false);view.getSettings().setAllowContentAccess(false);
        view.setWebChromeClient(target);view.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView source,String url,Bitmap icon){if(source==view&&!closed){epoch++;core.cancel();}}
            @Override public void onPageFinished(WebView source,String url){
                if(source==view&&!closed&&!started){started=true;handler.post(()->open("fixtureChild",true,()->awaitTicket(BrowserUsePopupProbe.this::adopt)));}
            }
        });
        view.loadDataWithBaseURL("http://127.0.0.1/","<!doctype html><title>Native popup fixture</title>","text/html","UTF-8",null);
    }
    private void check(boolean value,String code){if(!value)throw new IllegalStateException(code);checks++;}
    private void later(Runnable next){
        if(closed||finished)return;if(step!=null)handler.removeCallbacks(step);step=next;
        if(!handler.postDelayed(next,25))end(false,"popup_fixture_scheduler_stopped");
    }
    private void open(String name,boolean content,Runnable next){
        if(closed||finished)return;deadline=SystemClock.uptimeMillis()+2500;
        String script="window."+name+"=window.open('about:blank');"+(content?
            "if(window."+name+"){window."+name+".document.write('<!doctype html><body>native-popup-original</body>');"+
            "window."+name+".document.close();window."+name+".fixtureMarker='original-contents';}":"")+"true";
        view.evaluateJavascript(script,ignored->{});later(next);
    }
    private void awaitTicket(Runnable next){
        if(closed||finished)return;MdoBrowserPopups.Snapshot pending=core.snapshot();
        if(pending!=null){
            try{check(blocked&&budget.used()==1&&core.clean(),"native_popup_not_retained");
                check(!pending.userInitiated&&pending.epoch==epoch,"native_popup_source_metadata_changed");
                check(previous==null||!previous.equals(pending.id),"native_popup_identity_reused");next.run();
            }catch(RuntimeException error){end(false,error.getMessage());}return;
        }
        if(SystemClock.uptimeMillis()>=deadline){end(false,"native_popup_not_observed");return;}later(()->awaitTicket(next));
    }
    private void adopt(){
        try{
            MdoBrowserPopups.Snapshot pending=core.snapshot();previous=pending.id;
            final WebView created=new WebView(view.getContext());child=created;
            try{core.adopt("foreign-popup",created,(ok,code,may)->{});throw new IllegalStateException("stale_popup_adopted");}
            catch(IllegalStateException expected){check("popup_request_unavailable".equals(expected.getMessage()),"stale_popup_admitted");}
            created.getSettings().setJavaScriptEnabled(true);created.getSettings().setAllowFileAccess(false);created.getSettings().setAllowContentAccess(false);
            childTarget=new MdoBrowserWindowTarget(created,()->!closed&&!finished&&child==created,()->1,value->{},original->{},new MdoBrowserWindowTarget.Windows(){
                public boolean create(WebView source,boolean gesture,MdoBrowserPopups.Transport<WebView> transport){return false;}
                public void close(WebView source){if(source==created&&child==created&&!closed){childClosed=true;closes++;}}
            });created.setWebChromeClient(childTarget);
            // A mounted 1px fixture is sufficient for transport/content checks;
            // this explicitly makes no pixel, input or panel-layout claim.
            ((ViewGroup)view.getParent()).addView(created,new ViewGroup.LayoutParams(1,1));
            check(created.getUrl()==null,"popup_child_was_navigated");
            core.adopt(pending.id,created,(ok,code,may)->{
                if(closed||finished)return;
                try{check(ok&&may&&core.clean()&&!core.busy()&&!blocked&&budget.used()==0,"native_popup_transport_unconfirmed");
                    accepts++;deadline=SystemClock.uptimeMillis()+2500;verifyContents();
                }catch(RuntimeException error){end(false,error.getMessage());}
            });
        }catch(RuntimeException error){end(false,error.getMessage());}
    }
    private void verifyContents(){
        if(closed||finished||child==null)return;
        child.evaluateJavascript("document.body?.textContent==='native-popup-original'&&window.fixtureMarker==='original-contents'&&!!window.opener",value->{
            if(closed||finished)return;
            if(!"true".equals(value)){
                if(SystemClock.uptimeMillis()>=deadline){end(false,"native_popup_original_contents_lost");return;}later(this::verifyContents);return;
            }
            checks++;child.evaluateJavascript("window.close();true",ignored->{});deadline=SystemClock.uptimeMillis()+2500;later(this::awaitClose);
        });
    }
    private void awaitClose(){
        if(closed||finished)return;
        if(!childClosed){if(SystemClock.uptimeMillis()>=deadline){end(false,"native_popup_close_not_observed");return;}later(this::awaitClose);return;}
        view.evaluateJavascript("window.fixtureChild.closed",value->{
            if(closed||finished)return;
            if(!"true".equals(value)){if(SystemClock.uptimeMillis()>=deadline)end(false,"native_popup_close_mismatch");else later(this::awaitClose);return;}
            try{check("true".equals(value)&&closes==1,"native_popup_close_mismatch");destroyChild();
                open("fixtureRejected",false,()->awaitTicket(()->{previous=core.snapshot().id;check(core.dismiss(previous),"native_popup_reject_failed");later(this::awaitRejected);}));
            }catch(RuntimeException error){end(false,error.getMessage());}
        });
    }
    private void awaitRejected(){
        if(closed||finished)return;
        if(core.busy()){if(SystemClock.uptimeMillis()>=deadline){end(false,"native_popup_reject_not_consumed");return;}later(this::awaitRejected);return;}
        view.evaluateJavascript("window.fixtureRejected===null||window.fixtureRejected.closed",value->{
            if(closed||finished)return;
            if(!"true".equals(value)){if(SystemClock.uptimeMillis()>=deadline)end(false,"native_popup_reject_unconfirmed");else later(this::awaitRejected);return;}
            try{check("true".equals(value)&&budget.used()==0&&!blocked&&core.clean(),"native_popup_reject_unconfirmed");
                open("fixtureAbandoned",false,()->awaitTicket(()->{
                    cancelEpoch=epoch;previous=core.snapshot().id;deadline=SystemClock.uptimeMillis()+2500;
                    view.loadDataWithBaseURL("http://127.0.0.1/","<!doctype html><script>window.fixtureNext='next'</script>","text/html","UTF-8",null);
                    later(this::awaitNavigation);
                }));
            }catch(RuntimeException error){end(false,error.getMessage());}
        });
    }
    private void awaitNavigation(){
        if(closed||finished)return;
        if(core.busy()||epoch<=cancelEpoch){if(SystemClock.uptimeMillis()>=deadline){end(false,"native_popup_navigation_cancel_unconfirmed");return;}later(this::awaitNavigation);return;}
        view.evaluateJavascript("window.fixtureNext==='next'",value->{
            if(closed||finished)return;
            if(!"true".equals(value)){if(SystemClock.uptimeMillis()>=deadline)end(false,"native_popup_navigation_not_ready");else later(this::awaitNavigation);return;}
            try{check(core.clean()&&!blocked&&budget.used()==0&&core.snapshot()==null,"native_popup_resource_not_released");
                check(creates==3&&accepts==1&&closes==1,"native_popup_callback_count_mismatch");end(true,"native_popup_transport_verified");
            }catch(RuntimeException error){end(false,error.getMessage());}
        });
    }
    private void destroyChild(){
        if(child==null)return;WebView retiring=child;child=null;MdoBrowserWindowTarget chrome=childTarget;childTarget=null;
        if(chrome!=null)chrome.close();retiring.stopLoading();if(retiring.getParent() instanceof ViewGroup)((ViewGroup)retiring.getParent()).removeView(retiring);
        retiring.destroy();if(chrome!=null)chrome.viewDestroyed();
    }
    private void end(boolean ok,String code){if(closed||finished)return;finished=true;done.complete(ok,code,checks);}
    @Override public void close(){if(closed)return;closed=true;if(step!=null)handler.removeCallbacks(step);if(core!=null)core.close();if(target!=null)target.close();destroyChild();}
    void viewDestroyed(){if(core!=null)core.viewDestroyed(view);if(target!=null)target.viewDestroyed();}
}
