package org.xserver.android;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.function.BooleanSupplier;

/** Fixed isolated-document proof for a single visible native capture request. */
final class MdoBrowserNativeCapture {
    static final String FENCE="(p=>{try{"+
        "const bad=code=>({ok:false,code}),r=globalThis.__mdo_browser_use_local_frame_v1,v=globalThis.visualViewport;"+
        "if(self!==top||!r||r.documentNode!==document||r.document!==p.document)return bad('frame_changed');"+
        "if(!v||![innerWidth,innerHeight,scrollX,scrollY,devicePixelRatio,v.width,v.height,v.scale,v.offsetLeft,v.offsetTop].every(Number.isFinite)||v.scale<=0)return bad('capture_viewport_unsupported');"+
        "if(Math.abs(v.width-innerWidth)>1||Math.abs(v.height-innerHeight)>1||Math.abs(v.offsetLeft)>0.01||Math.abs(v.offsetTop)>0.01)return bad('capture_viewport_unsupported');"+
        "if(Math.abs(innerWidth-p.viewport.width)>0.05||Math.abs(innerHeight-p.viewport.height)>0.05||Math.abs(scrollX-p.scroll.x)>0.05||Math.abs(scrollY-p.scroll.y)>0.05||Math.abs(devicePixelRatio-p.device_pixel_ratio)>0.001)return bad('capture_changed');"+
        "return {ok:true};}catch{return {ok:false,code:'capture_guard_failed'}}})";
    final MdoBrowserCapture.Plan plan;final String captureKey,nativeDocument,document;
    final long navigation;final double reportedScale;
    private final MdoBrowserDocument world;private final MdoBrowserCaptureTarget target;private final BooleanSupplier owned;private final String expression;
    private static double number(JSONObject object,String key)throws JSONException {
        Object value=object.get(key);if(!(value instanceof Number))throw new IllegalArgumentException("capture_request_invalid");
        double result=((Number)value).doubleValue();if(!Double.isFinite(result)||Math.abs(result)>1e8)throw new IllegalArgumentException("capture_request_invalid");return result;
    }
    private static String text(JSONObject object,String key)throws JSONException {
        Object value=object.get(key);if(!(value instanceof String))throw new IllegalArgumentException("capture_request_invalid");return (String)value;
    }
    private static double navigation(JSONObject object)throws JSONException {
        Object value=object.get("navigation");if(!(value instanceof Number))throw new IllegalArgumentException("capture_request_invalid");
        double result=((Number)value).doubleValue();if(!Double.isFinite(result)||result<1||result>9007199254740991d||result!=Math.rint(result))
            throw new IllegalArgumentException("capture_request_invalid");return result;
    }
    MdoBrowserNativeCapture(MdoBrowserDocument world,MdoBrowserCaptureTarget target,BooleanSupplier owned,JSONObject request)throws JSONException {
        this.world=world;this.target=target;this.owned=owned;
        captureKey=text(request,"capture_key");if(!captureKey.matches("[a-zA-Z0-9._:-]{1,128}"))throw new IllegalArgumentException("capture_request_invalid");
        JSONObject context=request.getJSONObject("capture_context"),clip=request.getJSONObject("clip");
        if(!text(context,"adapter").equals("android-main-document-v1"))throw new IllegalArgumentException("capture_request_invalid");
        nativeDocument=text(context,"native_document");document=text(context,"document");
        if(!document.matches("d-[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw new IllegalArgumentException("capture_request_invalid");
        MdoBrowserCapture.Snapshot snapshot=target.snapshot();if(snapshot==null)throw new IllegalStateException("capture_view_unavailable");
        if(!owned.getAsBoolean()||!nativeDocument.equals(world.identity())||!nativeDocument.equals(snapshot.document)||
           navigation(request)!=snapshot.navigation||navigation(context)!=snapshot.navigation)
            throw new IllegalStateException("frame_changed");
        navigation=snapshot.navigation;
        JSONObject viewport=context.getJSONObject("viewport"),scroll=context.getJSONObject("scroll");
        double vw=number(viewport,"width"),vh=number(viewport,"height"),sx=number(scroll,"x"),sy=number(scroll,"y"),dpr=number(context,"device_pixel_ratio"),
            x=number(clip,"x"),y=number(clip,"y"),width=number(clip,"width"),height=number(clip,"height"),ratio=number(clip,"scale"),factor=snapshot.scale;
        if(vw<=0||vh<=0||vw>32768||vh>32768||dpr<=0||dpr>16||width<=0||height<=0||width>32768||height>32768||
           width*height>16777216||width*height*dpr*dpr>33554432||ratio<=0||ratio>1||
           x<sx-1||y<sy-1||x+width>sx+vw+1||y+height>sy+vh+1)
            throw new IllegalArgumentException("capture_request_invalid");
        if(Math.abs(vw*factor-snapshot.width)>2||Math.abs(vh*factor-snapshot.height)>2||
           Math.abs(sx*factor-snapshot.scrollX)>1||Math.abs(sy*factor-snapshot.scrollY)>1)
            throw new IllegalStateException("capture_changed");
        double maximum=request.has("max_bytes")?number(request,"max_bytes"):131072,timeout=request.has("timeout_ms")?number(request,"timeout_ms"):10000;
        if(maximum<1||maximum>262144||maximum!=Math.rint(maximum)||timeout<1||timeout>60000||timeout!=Math.rint(timeout))
            throw new IllegalArgumentException("capture_request_invalid");
        String requestedFormat=request.has("format")?text(request,"format"):"png";
        if(!requestedFormat.equals("png")&&!requestedFormat.equals("jpeg"))throw new IllegalArgumentException("capture_request_invalid");
        // Preserve source pixels losslessly until trusted host-side redaction.
        // The requested public codec/byte budget applies AFTER those masks.
        plan=new MdoBrowserCapture.Plan(snapshot,(x-sx)*factor,(y-sy)*factor,width*factor,height*factor,
            Math.min(1,ratio*dpr/factor),"png",262144,(long)Math.min(10000,timeout));
        reportedScale=Math.min(plan.outputWidth/width,plan.outputHeight/height)/dpr;
        if(!Double.isFinite(reportedScale)||reportedScale<=0)throw new IllegalArgumentException("capture_request_invalid");
        expression=FENCE+"("+new JSONObject().put("document",document).put("viewport",new JSONObject().put("width",vw).put("height",vh))
            .put("scroll",new JSONObject().put("x",sx).put("y",sy)).put("device_pixel_ratio",dpr).toString()+")";
    }
    private String current(){return owned.getAsBoolean()&&nativeDocument.equals(world.identity())&&plan.snapshot.same(target.snapshot())?null:"capture_changed";}
    void check(long remaining,MdoBrowserCapture.Checked complete){
        String code=current();if(code!=null){complete.complete(code);return;}
        try{world.evaluate(nativeDocument,expression,Math.min(10000,remaining),result->{
            if(!result.ok){complete.complete(result.code);return;}
            String failure;
            try{JSONObject value=new JSONObject(result.value);failure=Boolean.TRUE.equals(value.opt("ok"))?current():value.optString("code","capture_guard_failed");}
            catch(JSONException error){failure="capture_guard_failed";}
            complete.complete(failure);
        });}catch(MdoBrowserWorld.Failure failure){complete.complete(failure.code);}
    }
}
