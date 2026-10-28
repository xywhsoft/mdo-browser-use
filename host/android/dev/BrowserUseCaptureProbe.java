package org.xserver.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.SystemClock;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Fixture-only native pixel gate. It does NOT qualify the isolated guard,
 * shared C/JS masking, an Agent operation or a production Android screenshot. */
final class BrowserUseCaptureProbe implements AutoCloseable {
    interface Done {void complete(boolean ok,String code,int checks);}
    private final WebView view;private final Handler handler;private final Done done;
    private boolean closed,started;private double scale=Double.NaN;private long epoch=1,end;private int checks,draws;
    private MdoBrowserCapture capture;private MdoBrowserCaptureTarget target;
    private final Runnable readiness=this::begin;
    BrowserUseCaptureProbe(WebView view,Handler handler,Done done){this.view=view;this.handler=handler;this.done=done;}
    void run(){
        view.getSettings().setJavaScriptEnabled(true);
        view.setWebViewClient(new WebViewClient(){
            @Override public void onScaleChanged(WebView web,float before,float after){scale=after;}
            @Override public void onPageStarted(WebView web,String url,Bitmap icon){epoch++;}
            @Override public void onPageFinished(WebView web,String url){if(!started&&!closed){end=SystemClock.uptimeMillis()+4000;handler.post(readiness);}}
        });
        view.loadDataWithBaseURL("http://127.0.0.1/","<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<style>html,body{margin:0;height:100%;background:rgb(12,34,56)}#block{position:absolute;left:20px;top:40px;width:120px;height:80px;background:rgb(34,170,68)}</style>"+
            "<div id='block'></div>","text/html","UTF-8",null);
    }
    private void begin(){
        if(closed||started)return;
        if(!Double.isFinite(scale)||!view.isAttachedToWindow()||!view.isShown()||view.getWidth()<1||view.getHeight()<1){
            if(SystemClock.uptimeMillis()<end){handler.postDelayed(readiness,25);return;}fail("capture_scale_or_view_unavailable");return;
        }
        started=true;
        target=new MdoBrowserCaptureTarget(view,()->"fd-00000000-0000-4000-8000-000000000001",()->epoch,()->scale,()->!closed);
        MdoBrowserCapture.Target counted=new MdoBrowserCapture.Target(){
            public MdoBrowserCapture.Snapshot snapshot(){return target.snapshot();}
            public void ready(Runnable complete){target.ready(complete);}
            public MdoBrowserCapture.Raster draw(MdoBrowserCapture.Plan plan){draws++;return target.draw(plan);}
        };
        capture=new MdoBrowserCapture(counted,MdoBrowserCaptureRuntime.scheduler(),MdoBrowserCaptureRuntime.WORKER,MdoBrowserCaptureRuntime.BUDGET);
        image(0);
    }
    private MdoBrowserCapture.Plan plan(int index){
        return new MdoBrowserCapture.Plan(target.snapshot(),(index==0?20:170)*scale,(index==0?40:160)*scale,
            (index==0?120:60)*scale,(index==0?80:40)*scale,.5,index==0?"png":"jpeg",262144,3000);
    }
    private void image(int index){
        try{capture.capture(plan(index),(remaining,complete)->complete.complete(null),result->{
            if(closed)return;
            if(!result.ok){fail(result.code);return;}
            Bitmap pixels=null;
            try{
                pixels=BitmapFactory.decodeByteArray(result.data,0,result.data.length);
                if(pixels==null||pixels.getWidth()!=result.plan.outputWidth||pixels.getHeight()!=result.plan.outputHeight)
                    throw new IllegalStateException("capture_pixel_dimensions");checks++;
                int expected=index==0?Color.rgb(34,170,68):Color.rgb(12,34,56),tolerance=index==0?3:12;
                for(int x:new int[]{pixels.getWidth()/4,pixels.getWidth()/2,pixels.getWidth()*3/4})
                    for(int y:new int[]{pixels.getHeight()/4,pixels.getHeight()/2,pixels.getHeight()*3/4}){
                        int actual=pixels.getPixel(x,y);
                        if(Color.alpha(actual)!=255||Math.abs(Color.red(actual)-Color.red(expected))>tolerance||
                           Math.abs(Color.green(actual)-Color.green(expected))>tolerance||Math.abs(Color.blue(actual)-Color.blue(expected))>tolerance)
                            throw new IllegalStateException("capture_native_pixels_mismatch");checks++;
                    }
                if(index==0)handler.post(()->image(1));else handler.post(this::cancelledGuard);
            }catch(RuntimeException|OutOfMemoryError error){fail("capture_pixel_verification_failed");}
            finally{if(pixels!=null)pixels.recycle();}
        });}catch(MdoBrowserCapture.Failure|RuntimeException|OutOfMemoryError error){fail("capture_fixture_request_failed");}
    }
    private void cancelledGuard(){
        if(closed)return;
        final MdoBrowserCapture.Checked[] guard={null};final boolean[] cancelled={false};
        try{
            capture.capture(plan(0),(remaining,complete)->guard[0]=complete,result->cancelled[0]=!result.ok&&"capture_cancelled".equals(result.code)&&result.data==null);
            capture.cancel();guard[0].complete(null);
            if(!cancelled[0]||draws!=2||capture.busy()||!capture.cleanupConfirmed())throw new IllegalStateException("Capture cancellation did not drain");
            checks++;done.complete(true,"native_capture_pixels_verified",checks);
        }catch(MdoBrowserCapture.Failure|RuntimeException error){fail("capture_cancel_verification_failed");}
    }
    private void fail(String code){if(!closed)done.complete(false,code,checks);}
    void viewDestroyed(){if(capture!=null)capture.viewDestroyed();}
    @Override public void close(){closed=true;handler.removeCallbacks(readiness);if(capture!=null)capture.close();}
}
