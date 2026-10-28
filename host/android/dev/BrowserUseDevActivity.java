package org.xserver.android;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Reusable, network-free device gate. Never uses another app's files/profile. */
public final class BrowserUseDevActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final JSONObject report=new JSONObject();
    private WebView view;private MdoBrowserDocument world;private long documentEpoch=1;
    private BrowserUseInputProbe inputProbe;
    private BrowserUseCaptureProbe captureProbe;
    private TextView status;private String nonce;private boolean finished,loaded,started;
    private final Runnable timeout=()->finishProbe(false,"device_probe_timed_out");
    private final Runnable readiness=()->maybeEvaluate();
    private void put(String key,Object value){try{report.put(key,value);}catch(Exception error){throw new IllegalStateException(error);}}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);nonce=getIntent().getStringExtra("nonce");
        if(nonce==null||!nonce.matches("[a-f0-9]{32}")){finished=true;finish();return;}
        put("nonce",nonce);put("api",Build.VERSION.SDK_INT);put("automation_verified",false);put("native_wheel_verified",false);
        put("native_capture_verified",false);put("guarded_native_capture_verified",false);put("capture_privacy_verified",false);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(this);status.setText("Checking the installed WebView provider…");layout.addView(status);
        setContentView(layout);handler.postDelayed(timeout,15000);
        try{
            view=new WebView(this);layout.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
            PackageInfo info=WebView.getCurrentWebViewPackage();
            if(info!=null){put("provider_package",info.packageName);put("provider_version",info.versionName);}
            if("native-capture".equals(getIntent().getStringExtra("mode"))){
                captureProbe=new BrowserUseCaptureProbe(view,handler,(ok,code,checks)->{
                    put("native_capture_checks",checks);put("native_capture_verified",ok);finishProbe(ok,code);
                });captureProbe.run();return;
            }
            if("native-input".equals(getIntent().getStringExtra("mode"))){
                getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                inputProbe=new BrowserUseInputProbe(view,handler,(ok,code,checks)->{
                    put("native_input_checks",checks);put("native_input_verified",ok);put("native_wheel_verified",inputProbe!=null&&inputProbe.wheelVerified());finishProbe(ok,code);
                });inputProbe.run();return;
            }
            if(Build.VERSION.SDK_INT<28){finishProbe(true,"provider_loader_unsupported");return;}
            ClassLoader loader=WebView.getWebViewClassLoader();MdoBrowserWorld.Features support=features(loader);
            final WebView ownedView=view;
            world=new MdoBrowserDocument(view,loader,new MdoBrowserWorld.Scheduler(){
                public long now(){return SystemClock.uptimeMillis();}
                public void post(Runnable task,long milliseconds){if(!handler.postDelayed(task,milliseconds))throw new IllegalStateException("UI scheduler stopped");}
                public void remove(Runnable task){handler.removeCallbacks(task);}
            },()->!finished&&view==ownedView,()->documentEpoch,support.profiles?"mdo.browseruse.dev":null);
            view.getSettings().setJavaScriptEnabled(true);
            view.setWebViewClient(new WebViewClient(){
                @Override public void onPageStarted(WebView web,String url,android.graphics.Bitmap icon){
                    if(finished||web!=view)return;documentEpoch++;loaded=false;world.navigation();
                }
                @Override public void onPageFinished(WebView web,String url){
                    if(finished||web!=view)return;loaded=true;world.loaded();maybeEvaluate();
                }
            });
            String html="<!doctype html><meta name='viewport' content='width=device-width'><body>Browser Use device fixture"+
                "<script>globalThis.pageMarker='page';document.body.dataset.isolated="+
                "String(typeof globalThis.__mdoPrivateMarker==='undefined'&&typeof globalThis.__mdoBrowserWorld==='undefined');</script>";
            view.loadDataWithBaseURL("http://127.0.0.1/",html,"text/html","UTF-8",null);
        }catch(MdoBrowserWorld.Failure error){
            finishProbe(error.code.equals("isolated_world_unsupported"),error.code);
        }catch(org.json.JSONException|RuntimeException|LinkageError error){finishProbe(false,"device_provider_probe_failed");}
    }
    private MdoBrowserWorld.Features features(ClassLoader loader)throws MdoBrowserWorld.Failure,org.json.JSONException{
        MdoBrowserWorld.Features support=MdoBrowserWorld.inspect(loader);
        put("provider_feature_count",support.count);put("isolated_world_feature",support.isolatedWorld);put("profile_feature",support.profiles);put("message_listener_feature",support.messageListener);
        MdoBrowser browser=new MdoBrowser(this,new FrameLayout(this),view);
        try{
            JSONObject descriptor=browser.capabilities().getJSONObject("browser_use");put("product_browser_use",descriptor);
            int denied=0;
            for(JSONObject request:new JSONObject[]{new JSONObject().put("command","create").put("automation",true),
                new JSONObject().put("command","create").put("intercept_downloads",true),new JSONObject().put("command","click").put("agent",true)}){
                try{browser.automationAdmission(request);}
                catch(UnsupportedOperationException error){if(descriptor.getString("reason").equals(error.getMessage()))denied++;}
            }
            if(denied!=3||descriptor.getBoolean("available"))throw new IllegalStateException("Unsafe product admission");
            browser.automationAdmission(new JSONObject().put("command","create"));
            browser.automationAdmission(new JSONObject().put("command","close").put("agent",true));
            browser.automationAdmission(new JSONObject().put("command","capture_cancel").put("agent",true));
            put("product_admission_denied_before_create",denied);put("manual_and_cleanup_admission",true);
        }finally{browser.destroy();}return support;
    }
    private void maybeEvaluate(){
        if(finished||!loaded||world==null||started)return;
        handler.removeCallbacks(readiness);
        if(!world.ready()){handler.postDelayed(readiness,25);return;}started=true;
        final WebView owned=view;final long epoch=documentEpoch;
        String expression="(()=>{globalThis.__mdoPrivateMarker="+JSONObject.quote(nonce)+
            ";return typeof globalThis.pageMarker==='undefined'&&document.body.dataset.isolated==='true'})()";
        try{world.evaluate(expression,4000,result->{
            if(!result.ok){finishProbe(false,result.code);return;}
            if(!"true".equals(result.value)){finishProbe(false,"world_isolation_failed");return;}
            // Fixture-only page-world read checks that the isolated marker did
            // not leak. Production automation never evaluates in page world.
            owned.evaluateJavascript("typeof globalThis.__mdoPrivateMarker==='undefined'&&document.body.dataset.isolated==='true'",value->{
                boolean valid=!finished&&view==owned&&documentEpoch==epoch&&world.ready()&&"true".equals(value);
                put("automation_verified",valid);put("world_isolation_verified",valid);put("production_document_controller_verified",valid);
                finishProbe(valid,valid?"isolated_world_verified":"world_isolation_failed");
            });
        });}catch(MdoBrowserWorld.Failure error){finishProbe(false,error.code);}
    }
    private void finishProbe(boolean verified,String code){
        if(finished)return;finished=true;handler.removeCallbacks(timeout);handler.removeCallbacks(readiness);
        put("probe_verified",verified);put("code",code);
        if(inputProbe!=null){inputProbe.close();inputProbe=null;}
        if(captureProbe!=null)captureProbe.close();
        if(world!=null){world.close();put("cleanup_confirmed",world.cleanupConfirmed());}
        if(view!=null){view.stopLoading();if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);view.destroy();if(captureProbe!=null)captureProbe.viewDestroyed();if(world!=null){world.viewDestroyed(view);put("cleanup_confirmed",world.cleanupConfirmed());}view=null;}
        put("owned_view_destroyed",true);status.setText(report.optBoolean("automation_verified")?"Isolated world verified.":"Provider result: "+code);
        Log.i("MdoBrowserUseProbe",report.toString());
    }
    @Override protected void onPause(){if(captureProbe!=null&&!finished)finishProbe(false,"capture_activity_backgrounded");if(inputProbe!=null)inputProbe.pause();if(world!=null&&!finished)world.block(MdoBrowserDocument.BACKGROUND,true);super.onPause();}
    @Override protected void onResume(){super.onResume();if(inputProbe!=null)inputProbe.resume();if(world!=null&&!finished){world.block(MdoBrowserDocument.BACKGROUND,false);maybeEvaluate();}}
    @Override protected void onDestroy(){
        if(!finished)finishProbe(false,"activity_destroyed");handler.removeCallbacksAndMessages(null);super.onDestroy();
    }
}
