package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared native command grammar through the real private worker/frame/world/
 * owner/strict JNI core. No installed main-UI or product admission claim. */
public final class CdpFrameRequestProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean value){if(!value)throw new AssertionError("Frame request check "+(checks.get()+1));checks.incrementAndGet();}
    private interface Checked {void run()throws Exception;}
    private static void rejects(String code,Checked operation)throws Exception {
        try{operation.run();throw new AssertionError("Missing frame grammar rejection");}catch(IOException error){check(code.equals(error.getMessage()));}
    }
    private static JSONObject header(String action)throws Exception {
        return new JSONObject().put("channel","mdo-browser").put("id","00000000-0000-4000-8000-000000000001").put("browser","fixture-browser")
            .put("command","frames").put("agent",true).put("action",action);
    }
    private static JSONObject target(String action)throws Exception {
        return header(action).put("frame","f0").put("document","fd-00000000-0000-4000-8000-000000000002");
    }
    private static JSONObject run(CdpOwnerProbe.Case sample,MdoBrowserCdpFrameRequest request)throws Exception {
        return sample.host.run(request.effect,(binding,deadline)->{
            byte[] value=request.run(binding,deadline);try{return new JSONObject(new String(value,StandardCharsets.UTF_8));}finally{Arrays.fill(value,(byte)0);}
        });
    }
    private static void roundtrip()throws Exception {
        CdpOwnerProbe.Case sample=new CdpOwnerProbe.Case();try{
            JSONObject inventory=run(sample,MdoBrowserCdpFrameRequest.parse(header("list")));JSONArray rows=inventory.getJSONArray("frames");check(rows.length()==5&&!sample.host.lastEffect);
            JSONObject leaf=null;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);boolean hasChild=false;for(int j=0;j<rows.length();j++)if(row.getString("id").equals(rows.getJSONObject(j).opt("parent")))hasChild=true;
                if(!hasChild&&!row.getString("id").equals("f0")&&!row.getString("parent").equals("f0"))leaf=row;
            }
            check(leaf!=null);JSONObject evaluate=header("evaluate").put("frame",leaf.getString("id")).put("document",leaf.getString("document")).put("script","({ok:true,text:'fixture'})");
            JSONObject result=run(sample,MdoBrowserCdpFrameRequest.parse(evaluate));check(result.getJSONObject("value").getBoolean("ok")&&sample.host.lastEffect);
            JSONObject owner=header("owner").put("frame",leaf.getString("id")).put("document",leaf.getString("document")).put("point",new JSONObject().put("x",4).put("y",6));
            MdoBrowserCdpFrameRequest copied=MdoBrowserCdpFrameRequest.parse(owner);owner.getJSONObject("point").put("x",9999);owner.put("frame","not-an-alias");
            JSONObject geometry=run(sample,copied);check(geometry.getJSONObject("value").getJSONObject("point").getDouble("x")==14&&!sample.host.lastEffect);
            for(String action:new String[]{"upload_policy","capture_owner"}){
                JSONObject request=header(action).put("frame",leaf.getString("id")).put("document",leaf.getString("document"));
                check(run(sample,MdoBrowserCdpFrameRequest.parse(request)).getJSONObject("value").getBoolean("ok"));
            }
            check(sample.server.objects.isEmpty()&&sample.host.peers.size()==1);
        }finally{sample.close();}
    }
    private static void grammar()throws Exception {
        for(String key:new String[]{"sessionId","targetId","executionContextId","uniqueContextId","objectId","worldName","url","script","frame","point","debug","profile"}){
            JSONObject invalid=header("list").put(key,"untrusted");rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(invalid));
        }
        for(Object value:new Object[]{false,"true",1,JSONObject.NULL}){
            JSONObject request=header("list").put("agent",value);rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(request));
        }
        for(String action:new String[]{"unknown","DOM.resolveNode","Runtime.evaluate"})rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(header(action)));
        rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(target("evaluate").put("script","")));
        rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(target("evaluate").put("script","\uD800")));
        rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(target("capture_owner").put("frame","raw-native-frame")));
        rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(target("capture_owner").put("document","fd-not-valid")));
        rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(header("list").put("id","not-a-request-id")));
        rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(header("list").put("channel","page-content")));
        for(JSONObject point:new JSONObject[]{new JSONObject().put("x",-1).put("y",0),new JSONObject().put("x","4").put("y",0),new JSONObject().put("x",4).put("y",0).put("extra",true)}){
            JSONObject request=target("owner").put("point",point);rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(request));
        }
        check(MdoBrowserCdpFrameRequest.parse(header("list").put("timeout_ms",60000)).timeout==15000);
        check(MdoBrowserCdpFrameRequest.parse(header("list").put("timeout_ms",100.9)).timeout==100);
        rejects("cdp_worker_timeout",()->MdoBrowserCdpFrameRequest.parse(header("list").put("timeout_ms",99)));
        rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(header("list").put("timeout_ms","1000")));
        rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(header("list").put("timeout_ms",0)));
    }
    private static void receipts()throws Exception {
        String[][] codes={{"cdp_worker_timeout","timed_out"},{"cdp_frame_stale","stale_frame"},{"cdp_owner_unique_context_unsupported","unsupported_capability"},
            {"cdp_worker_cancelled","operation_cancelled"},{"cdp_world_document_limit","resource_limit"},{"cdp_frame_request_invalid","frame_request_invalid"}};
        for(String[] row:codes){JSONObject error=MdoBrowserCdpFrameRequest.failure(row[0],true).getJSONObject("error");check(error.getString("code").equals(row[1])&&error.getString("native_code").equals(row[0])&&error.getBoolean("evaluation_may_have_occurred"));}
        JSONObject safe=MdoBrowserCdpFrameRequest.failure("private/provider/detail",false);check(!safe.toString().contains("private/provider/detail"));
        JSONObject bad=MdoBrowserCdpFrameRequest.response(new MdoBrowserCdpWorker.Result(null,true,"ambiguous".getBytes(StandardCharsets.UTF_8)));check(!bad.getBoolean("ok")&&bad.getJSONObject("error").getBoolean("evaluation_may_have_occurred"));
        JSONObject cancelled=MdoBrowserCdpFrameRequest.response(new MdoBrowserCdpWorker.Result("cdp_worker_cancelled",true,null));check(cancelled.getJSONObject("error").getString("code").equals("operation_cancelled"));
    }
    public static void main(String[] ignored)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");grammar();receipts();roundtrip();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"native_ui_calls_doubled\":true,\"protocol_peers_synthetic\":true,\"provider_qualified\":false}");
    }
}
