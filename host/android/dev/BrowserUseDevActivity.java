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
import android.widget.TextView;
import java.lang.reflect.InvocationHandler;
import org.json.JSONObject;

/** Reusable, network-free device gate. Never uses another app's files/profile. */
public final class BrowserUseDevActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final JSONObject report=new JSONObject();
    private WebView view;private MdoBrowserWorld world;private MdoBrowserWorld.Peer peer;
    private TextView status;private String nonce;private boolean finished,loaded,started;
    private final Runnable timeout=()->finishProbe(false,"device_probe_timed_out");
    private void put(String key,Object value){try{report.put(key,value);}catch(Exception error){throw new IllegalStateException(error);}}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);nonce=getIntent().getStringExtra("nonce");
        if(nonce==null||!nonce.matches("[a-f0-9]{32}")){finished=true;finish();return;}
        put("nonce",nonce);put("api",Build.VERSION.SDK_INT);put("automation_verified",false);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(this);status.setText("Checking the installed WebView provider…");layout.addView(status);
        setContentView(layout);handler.postDelayed(timeout,15000);
        try{
            view=new WebView(this);layout.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
            if(Build.VERSION.SDK_INT<28){finishProbe(true,"provider_loader_unsupported");return;}
            PackageInfo info=WebView.getCurrentWebViewPackage();
            if(info!=null){put("provider_package",info.packageName);put("provider_version",info.versionName);}
            ClassLoader loader=WebView.getWebViewClassLoader();features(loader);
            world=MdoBrowserWorld.open(view,loader,new MdoBrowserWorld.Scheduler(){
                public long now(){return SystemClock.uptimeMillis();}
                public void post(Runnable task,long milliseconds){if(!handler.postDelayed(task,milliseconds))throw new IllegalStateException("UI scheduler stopped");}
                public void remove(Runnable task){handler.removeCallbacks(task);}
            },new MdoBrowserWorld.Receiver(){
                public void fault(String code){finishProbe(false,code);}
                public void message(MdoBrowserWorld.Peer value,String text){
                    if(finished)return;
                    if(!text.equals("hello:"+nonce)){finishProbe(false,"unexpected_world_message");return;}
                    peer=value;maybeEvaluate();
                }
            });
            if(world.supportsProfile())world.profile("mdo.browseruse.dev");
            world.install("globalThis.__mdoPrivateMarker="+JSONObject.quote(nonce)+";"+
                MdoBrowserWorld.LISTENER+".postMessage('hello:'+globalThis.__mdoPrivateMarker);");
            view.getSettings().setJavaScriptEnabled(true);
            view.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView web,String url){loaded=true;maybeEvaluate();}});
            String html="<!doctype html><meta name='viewport' content='width=device-width'><body>Browser Use device fixture"+
                "<script>globalThis.pageMarker='page';document.body.dataset.isolated="+
                "String(typeof globalThis.__mdoPrivateMarker==='undefined'&&typeof globalThis.__mdoBrowserWorld==='undefined');</script>";
            view.loadDataWithBaseURL("http://127.0.0.1/",html,"text/html","UTF-8",null);
        }catch(MdoBrowserWorld.Failure error){
            finishProbe(error.code.equals("isolated_world_unsupported"),error.code);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){finishProbe(false,"device_provider_probe_failed");}
    }
    private void features(ClassLoader loader)throws ReflectiveOperationException{
        Class<?> glue=Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil",false,loader);
        InvocationHandler factory=(InvocationHandler)glue.getDeclaredMethod("createWebViewProviderFactory").invoke(null);
        Class<?> boundary=Class.forName("org.chromium.support_lib_boundary.WebViewProviderFactoryBoundaryInterface",false,loader);
        Object value;
        try{value=factory.invoke(null,boundary.getMethod("getSupportedFeatures"),new Object[]{});}
        catch(Throwable error){throw new ReflectiveOperationException("Feature query failed");}
        if(!(value instanceof String[])||((String[])value).length>512)throw new ReflectiveOperationException("Feature query invalid");
        String[] names=(String[])value;boolean isolated=false,profile=false,listener=false;
        for(String name:names){isolated|="JS_INJECTION_IN_FRAME_AND_WORLD".equals(name);profile|="MULTI_PROFILE".equals(name);listener|="WEB_MESSAGE_LISTENER".equals(name);}
        put("provider_feature_count",names.length);put("isolated_world_feature",isolated);put("profile_feature",profile);put("message_listener_feature",listener);
    }
    private void maybeEvaluate(){
        if(finished||!loaded||peer==null||started)return;started=true;
        String expression="globalThis.__mdoPrivateMarker==="+JSONObject.quote(nonce)+
            "&&typeof globalThis.pageMarker==='undefined'&&document.body.dataset.isolated==='true'";
        try{peer.evaluate(expression,4000,result->{
            if(!result.ok){finishProbe(false,result.code);return;}
            boolean valid="true".equals(result.value);put("automation_verified",valid);put("world_isolation_verified",valid);
            finishProbe(valid,valid?"isolated_world_verified":"world_isolation_failed");
        });}catch(MdoBrowserWorld.Failure error){finishProbe(false,error.code);}
    }
    private void finishProbe(boolean verified,String code){
        if(finished)return;finished=true;handler.removeCallbacks(timeout);
        put("probe_verified",verified);put("code",code);
        if(world!=null){world.close();put("cleanup_confirmed",world.cleanupConfirmed());}
        if(view!=null){view.stopLoading();if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);view.destroy();view=null;}
        put("owned_view_destroyed",true);status.setText(report.optBoolean("automation_verified")?"Isolated world verified.":"Provider result: "+code);
        Log.i("MdoBrowserUseProbe",report.toString());
    }
    @Override protected void onDestroy(){
        if(!finished)finishProbe(false,"activity_destroyed");handler.removeCallbacksAndMessages(null);super.onDestroy();
    }
}
