package org.xserver.android;

import android.webkit.WebView;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Private main-frame bridge. Product/Agent admission stays disabled until the
 * remaining Android contracts and real provider have been qualified. */
final class MdoBrowserNativeInput {
    static final String FENCE="(p=>{try{"+
        "const bad=code=>({ok:false,code}),r=globalThis.__mdo_browser_use_local_frame_v1;"+
        "if(self!==top||!r||r.documentNode!==document||r.document!==p.document)return bad('frame_changed');"+
        "const geometry=()=>{const v=globalThis.visualViewport,s=p.scale;"+
        "if(!v||![innerWidth,innerHeight,scrollX,scrollY,v.width,v.height,v.scale,v.offsetLeft,v.offsetTop,s].every(Number.isFinite)||s<=0||v.scale<=0)return 'input_viewport_unsupported';"+
        "if(Math.abs(v.width-innerWidth)>1||Math.abs(v.height-innerHeight)>1||Math.abs(v.offsetLeft)>0.01||Math.abs(v.offsetTop)>0.01)return 'input_viewport_unsupported';"+
        "return Math.abs(innerWidth*s-p.width)>2||Math.abs(innerHeight*s-p.height)>2||Math.abs(scrollX*s-p.scrollX)>1||Math.abs(scrollY*s-p.scrollY)>1?'input_viewport_changed':null};"+
        "let code=geometry();if(code)return bad(code);"+
        "const options={document:p.document,target:{ref:p.ref},op:'key'},found=r.locate(options);"+
        "if(!found.ok)return bad('input_target_changed');if(found.element.frame!=='f0')return bad('android_frames_unavailable');"+
        "if(p.kind==='click'){const checked=r.prepare({...options,op:'click',scroll:false});"+
        "if(!checked.ok||checked.element.ref!==p.ref||Math.abs(checked.point.x-p.x)>0.5||Math.abs(checked.point.y-p.y)>0.5)return bad('input_target_changed');"+
        "}else{const checked=p.kind==='focus'?r.focus(options):r.verify(options);"+
        "if(!checked.ok||!checked.focused||!document.hasFocus())return bad('input_focus_lost');}"+
        "if(r.documentNode!==document||r.document!==p.document)return bad('frame_changed');"+
        "code=geometry();return code?bad(code):{ok:true};}catch{return {ok:false,code:'input_guard_failed'}}})";

    private final WebView view;private final MdoBrowserDocument document;private final BooleanSupplier owned;private final DoubleSupplier nativeScale;
    private final int width,height,scrollX,scrollY;private final double scale;
    private final String kind,expression,nativeDocument;

    MdoBrowserNativeInput(WebView view,MdoBrowserDocument document,BooleanSupplier owned,DoubleSupplier nativeScale,JSONObject request)throws JSONException {
        if(view==null||document==null||owned==null||nativeScale==null)throw new IllegalArgumentException("Missing native input owner");
        this.view=view;this.document=document;this.owned=owned;this.nativeScale=nativeScale;
        nativeDocument=document.identity();if(nativeDocument==null)throw new IllegalStateException("world_not_ready");
        kind=request.getString("kind");
        if(!kind.matches("focus|click|key|text"))throw new UnsupportedOperationException("android_input_kind_unsupported");
        JSONArray frames=request.optJSONArray("frame_chain"),snapshot=request.optJSONArray("frame_snapshot");
        if(frames==null||frames.length()!=1||!frames.getJSONObject(0).getString("frame").equals("f0")||
           request.has("frame_snapshot")&&(snapshot==null||snapshot.length()!=0))
            throw new UnsupportedOperationException("android_frames_unavailable");
        if(!frames.getJSONObject(0).getString("document").equals(nativeDocument))throw new IllegalStateException("frame_changed");
        String id=request.getString("input_document"),ref=request.getString("input_ref");
        if(!id.matches("d-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")||!ref.matches("e[1-9][0-9]{0,12}"))
            throw new IllegalArgumentException("input_target_invalid");
        if(!kind.equals("click")&&(!id.equals(request.getString("focus_document"))||!ref.equals(request.getString("focus_ref"))))
            throw new IllegalArgumentException("input_focus_target_mismatch");
        width=view.getWidth();height=view.getHeight();scrollX=view.getScrollX();scrollY=view.getScrollY();scale=nativeScale.getAsDouble();
        if(!Double.isFinite(scale)||scale<=0||scale>32)throw new IllegalStateException("input_scale_not_ready");
        if(width<1||height<1||width>16384||height>16384||
           view.getPaddingLeft()!=0||view.getPaddingTop()!=0||view.getPaddingRight()!=0||view.getPaddingBottom()!=0)
            throw new IllegalArgumentException("input_viewport_unsupported");
        JSONObject witness=new JSONObject().put("document",id).put("ref",ref).put("kind",kind).put("scale",scale)
            .put("width",width).put("height",height).put("scrollX",scrollX).put("scrollY",scrollY);
        if(kind.equals("click")){
            if(!request.optString("button","left").equals("left")||request.optInt("click_count",1)!=1)
                throw new UnsupportedOperationException("android_input_kind_unsupported");
            double x=request.getDouble("x"),y=request.getDouble("y");
            if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||y<0||x*scale>=width||y*scale>=height)
                throw new IllegalArgumentException("input_coordinate_invalid");
            witness.put("x",x).put("y",y);
        }
        expression=FENCE+"("+witness.toString()+")";
    }
    private String current(){
        if(!owned.getAsBoolean()||!nativeDocument.equals(document.identity()))return "frame_changed";
        if(view.getWidth()!=width||view.getHeight()!=height||view.getScrollX()!=scrollX||view.getScrollY()!=scrollY||
           nativeScale.getAsDouble()!=scale||view.getPaddingLeft()!=0||view.getPaddingTop()!=0||view.getPaddingRight()!=0||view.getPaddingBottom()!=0)return "input_viewport_changed";
        return null;
    }
    private void check(long remaining,MdoBrowserInput.Checked complete){
        String stale=current();if(stale!=null){complete.complete(stale);return;}
        try{document.evaluate(nativeDocument,expression,Math.min(10000,remaining),result->{
            if(!result.ok){complete.complete(result.code);return;}
            String code;
            try{JSONObject value=new JSONObject(result.value);code=value.optBoolean("ok")?current():value.optString("code","input_guard_failed");}
            catch(JSONException error){code="input_guard_failed";}
            complete.complete(code);
        });}catch(MdoBrowserWorld.Failure failure){complete.complete(failure.code);}
    }
    void run(MdoBrowserInput input,JSONObject request,MdoBrowserInput.Completion complete)throws JSONException,MdoBrowserInput.Failure {
        String stale=current();if(stale!=null)throw new MdoBrowserInput.Failure(stale);
        switch(kind){
            case "focus":input.focus(this::check,complete);break;
            case "click":input.tap((float)(request.getDouble("x")*scale),(float)(request.getDouble("y")*scale),this::check,complete);break;
            case "text":input.text(request.getString("text"),this::check,complete);break;
            case "key":
                String key=request.getString("key");int modifiers=0;
                if(key.startsWith("Control+")){modifiers=2;key=key.substring(8);
                    if(!key.matches("A|Home|End|ArrowUp|ArrowDown|Space"))throw new MdoBrowserInput.Failure("input_key_invalid");}
                else if(key.equals("Shift+Tab")){modifiers=1;key="Tab";}
                input.key(key,modifiers,this::check,complete);break;
            default:throw new MdoBrowserInput.Failure("input_key_invalid");
        }
    }
}
