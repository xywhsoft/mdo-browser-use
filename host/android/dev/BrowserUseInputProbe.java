package org.xserver.android;

import android.os.Handler;
import android.os.SystemClock;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import org.json.JSONObject;

/** Device-only synthetic fixture. Its page-world reads are test assertions,
 * never an alternative to the product isolated-world automation contract. */
final class BrowserUseInputProbe implements AutoCloseable {
    interface Completion {void complete(boolean ok,String code,int checks);}
    private final WebView view;private final Handler handler;private final Completion completion;
    private final MdoBrowserInput input;private final MdoBrowserLifecycle<WebView> owners;
    private int checks;private boolean closed,started,wheelVerified,hoverVerified;
    boolean wheelVerified(){return wheelVerified;}
    boolean hoverVerified(){return hoverVerified;}
    private final java.util.Set<Runnable> timers=new java.util.HashSet<>();
    BrowserUseInputProbe(WebView view,Handler handler,Completion completion){
        this.view=view;this.handler=handler;this.completion=completion;
        owners=new MdoBrowserLifecycle<>((owned,epoch,admitted)->new MdoBrowserInput(new MdoBrowserInputTarget(owned,epoch,admitted),new MdoBrowserInput.Scheduler(){
            public long now(){return SystemClock.uptimeMillis();}
            public void post(Runnable task,long delay){if(!handler.postDelayed(task,delay))throw new IllegalStateException("Input scheduler stopped");}
            public void remove(Runnable task){handler.removeCallbacks(task);}
        }));owners.put("fixture",view);input=owners.input("fixture",view);
    }
    void run(){
        view.getSettings().setJavaScriptEnabled(true);
        view.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView web,String url,android.graphics.Bitmap icon){owners.navigation("fixture",web);}
            @Override public void onPageFinished(WebView web,String url){if(!started&&!closed){started=true;later(150,()->coordinates("button",(x,y)->tapButton(x,y)));}}
        });
        view.loadDataWithBaseURL("http://127.0.0.1/","<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<style>body{margin:0;height:2400px}button,input{position:absolute;left:20px;width:220px;height:60px}button{top:20px}input{top:110px}"+
            "#hoverArea{position:absolute;left:20px;top:180px;width:220px;height:30px}#hoverArea:hover{background:rgb(12,34,56)}"+
            "#wheelArea{position:absolute;left:10px;top:220px;width:calc(100vw - 20px);height:calc(100vh - 240px);overflow:scroll}#wheelBody{width:2400px;height:3600px}</style>"+
            "<button id='button'>Native input fixture</button><input id='editor'><div id='hoverArea'>Native hover fixture</div><div id='wheelArea'><div id='wheelBody'></div></div>"+
            "<script>globalThis.fixture={clicks:0,touch:true,input:true,key:false,downs:0,wheels:[],hovers:[]};"+
            "button.addEventListener('click',e=>{fixture.clicks++;fixture.touch=fixture.touch&&e.isTrusted});"+
            "document.addEventListener('touchstart',()=>fixture.downs++);document.addEventListener('mousedown',()=>fixture.downs++);"+
            "document.addEventListener('wheel',e=>fixture.wheels.push({trusted:e.isTrusted,x:e.clientX,y:e.clientY,dx:e.deltaX,dy:e.deltaY,mode:e.deltaMode,target:e.target.id}),{passive:true});"+
            "document.addEventListener('mousemove',e=>{if(e.target.id==='hoverArea'&&fixture.hovers.length<8)fixture.hovers.push({trusted:e.isTrusted,x:e.clientX,y:e.clientY,buttons:e.buttons,target:e.target.id})},{passive:true});"+
            "editor.addEventListener('input',e=>fixture.input=fixture.input&&e.isTrusted);"+
            "editor.addEventListener('keydown',e=>{if(e.key.toLowerCase()==='a'&&e.ctrlKey)fixture.key=e.isTrusted});</script>","text/html","UTF-8",null);
    }
    private void later(long delay,Runnable action){
        Runnable task=new Runnable(){public void run(){timers.remove(this);if(!closed)try{action.run();}catch(RuntimeException error){fail("device_input_probe_failed");}}};
        timers.add(task);if(!handler.postDelayed(task,delay)){timers.remove(task);fail("device_input_probe_timer_failed");}
    }
    interface Coordinates {void accept(float x,float y);}
    interface Read {void accept(JSONObject value)throws Exception;}
    private void read(String expression,Read read){
        if(closed)return;
        view.evaluateJavascript(expression,result->{if(closed)return;try{read.accept(new JSONObject(result));}catch(Exception error){fail("device_input_assertion_failed");}});
    }
    private void coordinates(String id,Coordinates use){
        read("(()=>{const r=document.getElementById('"+id+"').getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth}})()",box->{
            float scale=(float)(view.getWidth()/box.getDouble("width"));use.accept((float)box.getDouble("x")*scale,(float)box.getDouble("y")*scale);
        });
    }
    private boolean accepted(MdoBrowserInput.Result result){
        if(!result.ok||!result.cleanupConfirmed){fail(result.code==null?"device_input_cleanup_failed":result.code);return false;}checks++;return true;
    }
    private void tapButton(float x,float y){
        try{input.tap(x,y,result->{if(accepted(result))later(120,()->read("({clicks:fixture.clicks,trusted:fixture.touch})",state->{
            if(state.getInt("clicks")!=1||!state.getBoolean("trusted")){fail("device_touch_not_trusted");return;}checks++;
            hover();
        }));});}catch(MdoBrowserInput.Failure error){fail(error.code);}
    }
    private void hover(){
        read("(()=>{const r=hoverArea.getBoundingClientRect();return{width:innerWidth,x:r.left+r.width/2,y:r.top+r.height/2,focus:document.activeElement.id,downs:fixture.downs}})()",before->{
            final double x=before.getDouble("x"),y=before.getDouble("y"),scale=view.getWidth()/before.getDouble("width");
            try{input.hover((float)(x*scale),(float)(y*scale),null,result->{if(accepted(result))later(120,()->
                read("({events:fixture.hovers,hover: hoverArea.matches(':hover'),color:getComputedStyle(hoverArea).backgroundColor,focus:document.activeElement.id,clicks:fixture.clicks,downs:fixture.downs})",state->{
                    if(state.getJSONArray("events").length()!=1||!state.getBoolean("hover")||!state.getString("color").equals("rgb(12, 34, 56)")||
                       !state.getString("focus").equals(before.getString("focus"))||state.getInt("clicks")!=1||state.getInt("downs")!=before.getInt("downs")){
                        fail("device_hover_clicked_focused_or_replayed");return;
                    }
                    JSONObject event=state.getJSONArray("events").getJSONObject(0);
                    if(!event.getBoolean("trusted")||event.getInt("buttons")!=0||!event.getString("target").equals("hoverArea")||
                       Math.abs(event.getDouble("x")-x)>2||Math.abs(event.getDouble("y")-y)>2){fail("device_hover_geometry_or_trust_failed");return;}
                    hoverVerified=true;checks++;wheel();
                }));
            });}catch(MdoBrowserInput.Failure failure){fail(failure.code);}
        });
    }
    private void wheel(){
        read("(()=>{const r=wheelArea.getBoundingClientRect();return{width:innerWidth,px:r.left+r.width/2,py:r.top+r.height/2,"+
            "x:wheelArea.scrollLeft,y:wheelArea.scrollTop,windowX:scrollX,windowY:scrollY,downs:fixture.downs}})()",before->{
            final double x=before.getDouble("px"),y=before.getDouble("py");
            final float scale=(float)(view.getWidth()/before.getDouble("width"));
            final int downs=before.getInt("downs");
            try{input.wheel((float)x*scale,(float)y*scale,97*scale,131*scale,null,result->{
                if(accepted(result))later(200,()->read("({wheels:fixture.wheels,downs:fixture.downs,clicks:fixture.clicks,"+
                    "x:wheelArea.scrollLeft,y:wheelArea.scrollTop,windowX:scrollX,windowY:scrollY})",state->{
                    if(state.getJSONArray("wheels").length()!=1||state.getInt("downs")!=downs||state.getInt("clicks")!=1){fail("device_wheel_replayed_or_clicked");return;}
                    JSONObject event=state.getJSONArray("wheels").getJSONObject(0);
                    if(!event.getBoolean("trusted")||event.getInt("mode")!=0||!event.getString("target").equals("wheelBody")||
                       Math.abs(event.getDouble("x")-x)>2||Math.abs(event.getDouble("y")-y)>2||
                       Math.abs(event.getDouble("dx")-97)>2||Math.abs(event.getDouble("dy")-131)>2||
                       state.getDouble("x")<=before.getDouble("x")||state.getDouble("y")<=before.getDouble("y")||
                       state.getDouble("windowX")!=before.getDouble("windowX")||state.getDouble("windowY")!=before.getDouble("windowY")){
                        fail("device_wheel_geometry_or_units_failed");return;
                    }
                    wheelVerified=true;checks++;
                    // Fixture reset only, after the native movement was verified.
                    read("(()=>{wheelArea.scrollTo(0,0);return{reset:true}})()",reset->later(150,()->coordinates("editor",this::focusEditor)));
                }));
            });}catch(MdoBrowserInput.Failure failure){fail(failure.code);}
        });
    }
    private void focusEditor(float x,float y){
        try{input.tap(x,y,result->{if(accepted(result))later(180,()->{
            try{input.text("中文🙂",typed->{if(accepted(typed))later(150,()->read("({value:editor.value,trusted:fixture.input})",state->{
                if(!state.getString("value").equals("中文🙂")||!state.getBoolean("trusted")){fail("device_native_text_failed");return;}checks++;selectAll();
            }));});}catch(MdoBrowserInput.Failure error){fail(error.code);}
        });});}catch(MdoBrowserInput.Failure error){fail(error.code);}
    }
    private void selectAll(){
        try{input.key("A",2,result->{if(accepted(result))later(120,()->{
            try{input.text("替换",typed->{if(accepted(typed))later(150,()->read("({value:editor.value,key:fixture.key})",state->{
                if(!state.getString("value").equals("替换")||!state.getBoolean("key")){fail("device_native_key_failed");return;}checks++;
                coordinates("button",this::cancelTap);
            }));});}catch(MdoBrowserInput.Failure error){fail(error.code);}
        });});}catch(MdoBrowserInput.Failure error){fail(error.code);}
    }
    private void cancelTap(float x,float y){
        try{input.tap(x,y,result->{
            if(result.ok||!"input_cancelled".equals(result.code)||!result.effectMayHaveOccurred||!result.cleanupConfirmed){fail("device_input_cancel_failed");return;}checks++;
            later(120,()->read("({clicks:fixture.clicks})",state->{
                if(state.getInt("clicks")!=1){fail("device_cancel_activated_click");return;}checks++;swipe();
            }));
        });later(8,()->owners.stop("fixture",view));}catch(MdoBrowserInput.Failure error){fail(error.code);}
    }
    private void swipe(){
        float x=view.getWidth()-20,y=view.getHeight()-40,end=Math.max(30,y-240);float[] points=new float[18];
        for(int i=0;i<9;i++){points[i*2]=x;points[i*2+1]=y+(end-y)*i/8;}
        try{input.gesture(points,240,result->{if(accepted(result))later(200,()->read("({scrolled:scrollY>0})",state->{
            if(!state.getBoolean("scrolled")){fail("device_native_swipe_failed");return;}checks++;checkHidden();
        }));});}catch(MdoBrowserInput.Failure error){fail(error.code);}
    }
    private void checkHidden(){
        owners.visibility("fixture",view,false);view.setVisibility(View.INVISIBLE);
        try{input.tap(1,1,result->fail("device_hidden_input_dispatched"));fail("device_hidden_input_admitted");}
        catch(MdoBrowserInput.Failure error){
            view.setVisibility(View.VISIBLE);owners.visibility("fixture",view,true);
            if(!error.code.equals("input_view_unavailable")||!input.cleanupConfirmed()){fail("device_hidden_input_failed");return;}
            checks++;completion.complete(true,"native_input_verified",checks);
        }
    }
    private void fail(String code){if(!closed)completion.complete(false,code,checks);}
    void pause(){owners.background(true);}void resume(){owners.background(false);}
    @Override public void close(){if(closed)return;closed=true;for(Runnable task:timers)handler.removeCallbacks(task);timers.clear();owners.close();}
}
