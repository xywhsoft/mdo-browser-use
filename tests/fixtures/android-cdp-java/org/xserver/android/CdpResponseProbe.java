package org.xserver.android;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual parser/dispatcher/JNI validator, controlled event/wire peers. No
 * installed provider, response stream, external request or download is proven. */
public final class CdpResponseProbe {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Response check "+(checks+1));checks++;}
    private static JSONObject row(String name,Object value)throws Exception{return new JSONObject().put("name",name).put("value",value);}
    private static JSONObject event(int status,String method,String type,JSONArray headers)throws Exception{
        return new JSONObject().put("requestId","interception-job-7.0").put("responseStatusCode",status).put("resourceType",type)
            .put("request",new JSONObject().put("method",method).put("url","https://private.invalid/?token=never-retain").put("postData","private-body"))
            .put("responseHeaders",headers);
    }
    private static JSONObject document(String disposition,String type)throws Exception{
        JSONArray headers=new JSONArray();if(disposition!=null)headers.put(row("Content-Disposition",disposition));if(type!=null)headers.put(row("Content-Type",type));
        return event(200,"GET","Document",headers);
    }
    private static void classification(JSONObject event,String kind,String reason,long length){
        MdoBrowserCdpResponse r=MdoBrowserCdpResponse.inspect(event);check(r.kind.equals(kind));check(r.reason.equals(reason));check(r.declaredLength==length);
    }
    private static void invalid(JSONObject event){classification(event,"invalid","response_metadata_invalid",-1);}
    private static void displayMetadata()throws Exception{
        String[][] values={
            {"attachment","download.bin"},
            {"attachment; FILENAME=report.txt","report.txt"},
            {"attachment; filename=\"a; b.txt\"; foo=\"ignored;\\\"value\"","a; b.txt"},
            {"attachment; filename=\"../report.txt\"","report.txt"},
            {"attachment; filename=\"..\\\\report.txt\"","report.txt"},
            {"attachment; filename=legacy.txt; filename*=UTF-8''%E6%96%87%E4%BB%B6.txt","文件.txt"},
            {"attachment; filename*=utf-8'en-US'%e2%82%ac+rates.txt; filename=legacy.txt","€+rates.txt"},
            {"attachment; filename*=ISO-8859-1''caf%E9.txt","café.txt"},
            {"attachment; filename=\"100%20done.txt\"","100%20done.txt"},
            {"attachment; filename*=UTF-8''..%2Freport.txt","report.txt"},
            {"attachment; filename*=UTF-8''%F0%9F%93%84.txt","📄.txt"},
            {"attachment; filename=\"   report.txt  \"","report.txt"}
        };
        for(String[] value:values){MdoBrowserCdpResponse result=MdoBrowserCdpResponse.inspect(document(value[0],"Text/Plain; charset=utf-8"));
            check(result.kind.equals("candidate")&&result.name.equals(value[1])&&result.mime.equals("text/plain"));}
        for(String extended:new String[]{"UTF-16''report.txt","UTF-8''%GG","UTF-8''%","UTF-8''%C3","UTF-8''%C0%AF","UTF-8''a*b","UTF-8'bad_tag'a","\"UTF-8''x\""}){
            MdoBrowserCdpResponse result=MdoBrowserCdpResponse.inspect(document("attachment; filename=fallback.txt; filename*="+extended,null));
            check(result.kind.equals("candidate")&&result.name.equals("fallback.txt")&&result.mime.isEmpty());
        }
        for(String name:new String[]{".","..","CON.txt","lpt1.dat","COM¹.log","clock$.txt","x:stream","report?.txt","a\u202Etxt.exe","a\u0085b","x.",""}){
            MdoBrowserCdpResponse result=MdoBrowserCdpResponse.inspect(document("attachment; filename=\""+name+"\"",null));
            check(result.kind.equals("candidate")&&result.name.equals("download.bin"));
        }
        for(String encoded:new String[]{"%00secret.txt","%0Asecret.txt","%7Fsecret.txt","%E2%80%AEreport.txt"})
            check(MdoBrowserCdpResponse.inspect(document("attachment; filename=fallback.txt; filename*=UTF-8''"+encoded,null)).name.equals("download.bin"));
        StringBuilder longName=new StringBuilder();for(int i=0;i<81;i++)longName.append('文');
        check(MdoBrowserCdpResponse.inspect(document("attachment; filename=\""+longName+"\"",null)).name.equals("download.bin"));
        JSONObject event=document("attachment; filename=original.txt","Application/Pdf; test=discarded");MdoBrowserCdpResponse frozen=MdoBrowserCdpResponse.inspect(event);
        event.getJSONArray("responseHeaders").getJSONObject(0).put("value","attachment; filename=forged.txt");event.remove("request");
        check(frozen.name.equals("original.txt")&&frozen.mime.equals("application/pdf"));
        MdoBrowserCdpResponse generic=MdoBrowserCdpResponse.inspect(document(null,"Application/Octet-Stream"));check(generic.name.equals("download.bin")&&generic.mime.equals("application/octet-stream"));
    }
    private static void policy()throws Exception{
        classification(null,"invalid","response_metadata_invalid",-1);
        classification(new JSONObject(),"pass","request_stage",-1);
        classification(new JSONObject().put("responseErrorReason","Failed"),"pass","response_failed",-1);
        invalid(new JSONObject().put("responseErrorReason",true));invalid(new JSONObject().put("responseErrorReason","private\ntext"));
        invalid(new JSONObject().put("responseErrorReason","Failed").put("responseStatusCode",200));
        for(Object status:new Object[]{99,600,200.5,"200",JSONObject.NULL})invalid(document("attachment",null).put("responseStatusCode",status));
        for(int status:new int[]{100,101,301,302,303,304,307,308,400,401,404,407,500,599})
            classification(document("attachment","application/octet-stream").put("responseStatusCode",status),"pass","response_status",-1);
        for(int status:new int[]{204,205,206})classification(document("attachment",null).put("responseStatusCode",status),"pass","response_no_full_body",-1);
        for(String resource:new String[]{"XHR","Fetch","Image","Script","Stylesheet","Media","WebSocket","Other"})
            classification(document("attachment",null).put("resourceType",resource),"pass","subresource",-1);
        for(String method:new String[]{"HEAD","head","CONNECT"})classification(event(200,method,"Document",new JSONArray()),"pass","response_no_full_body",-1);
        for(String method:new String[]{"GET","POST","PUT","DELETE"})classification(event(201,method,"Document",new JSONArray().put(row("content-disposition","attachment"))),"candidate","attachment",-1);
        classification(document(" ATTACHMENT \t; filename=\"..\\\\private.bin\"; filename*=UTF-8''%e6%96%87.bin","text/html"),"candidate","attachment",-1);
        classification(document("attachment; filename=\"quoted\\\";semicolon\"",null),"candidate","attachment",-1);
        classification(document("inline; filename=attachment","text/plain; charset=utf-8"),"pass","document",-1);
        classification(document("attachmentx",null),"pass","document",-1);
        classification(document(null,"Application/Octet-Stream; charset=\"binary\""),"candidate","binary_document",-1);
        for(String type:new String[]{"application/x-download","application/force-download"})classification(document(null,type),"candidate","binary_document",-1);
        for(String type:new String[]{"text/html","application/pdf","image/png","application/json"})classification(document(null,type),"pass","document",-1);
        JSONObject candidate=document("attachment",null);candidate.getJSONArray("responseHeaders").put(row("content-length","\t 000123 "));
        classification(candidate,"candidate","attachment",123);
        for(String length:new String[]{"0","9223372036854775807"}){
            JSONObject e=document("attachment",null);e.getJSONArray("responseHeaders").put(row("Content-Length",length));classification(e,"candidate","attachment",Long.parseLong(length));
        }
        for(String length:new String[]{"","+1","-1","1.0","1,1","1e4","9223372036854775808"}){
            JSONObject e=document("attachment",null);e.getJSONArray("responseHeaders").put(row("Content-Length",length));invalid(e);
        }
        for(String name:new String[]{"Content-Length","Content-Disposition","Content-Type"}){
            JSONArray headers=new JSONArray().put(row(name,name.equals("Content-Length")?"1":name.equals("Content-Type")?"text/plain":"inline"));headers.put(headers.get(0));
            invalid(event(200,"GET","Document",headers));
        }
        for(String value:new String[]{"attachment;","attachment; filename","attachment; filename=","attachment; filename=\"unterminated","attachment; filename=a; FILENAME=b","attachment; filename=\"x\"trailing","attachment,inline"})invalid(document(value,null));
        invalid(document("attachment\r\nSet-Cookie: private",null));invalid(document("attachment; filename=\"\u0000\"",null));
        invalid(document("attachment; filename=\"\ud800\"",null));invalid(document("attachment; filename=\"\udc00\"",null));
        invalid(document(null,"text/html/invalid"));invalid(document(null,"application /octet-stream"));
        invalid(document(null,null).put("resourceType",42));invalid(document(null,null).put("request",true));
        invalid(document(null,null).put("request",new JSONObject().put("method","GET\n")));
        invalid(document(null,null).put("responseHeaders",new JSONObject()));
        invalid(event(200,"GET","Document",new JSONArray().put(row("invalid name","x"))));
        invalid(event(200,"GET","Document",new JSONArray().put(row("X-Test",true))));
        invalid(event(200,"GET","Document",new JSONArray().put(1)));
        StringBuilder huge=new StringBuilder();for(int i=0;i<8193;i++)huge.append('a');invalid(document("attachment; filename="+huge,null));
        JSONArray many=new JSONArray();for(int i=0;i<129;i++)many.put(row("X-Test",""));invalid(event(200,"GET","Document",many));
        StringBuilder unicode=new StringBuilder();for(int i=0;i<8000;i++)unicode.append('\u4e2d');
        JSONArray budget=new JSONArray();for(int i=0;i<3;i++)budget.put(row("X-Test",unicode.toString()));invalid(event(200,"GET","Document",budget));
        StringBuilder parameters=new StringBuilder("attachment");for(int i=0;i<33;i++)parameters.append("; x").append(i).append("=a");invalid(document(parameters.toString(),null));
        // A detached result keeps no JSON/header/name/value, even after input mutation.
        MdoBrowserCdpResponse frozen=MdoBrowserCdpResponse.inspect(candidate);candidate.getJSONArray("responseHeaders").put(row("Content-Disposition","inline"));
        check(frozen.kind.equals("candidate")&&frozen.declaredLength==123);
        check(frozen.exactTextLength);
        for(String coding:new String[]{"gzip","br","deflate","\u0130dentity"}){
            JSONObject e=event(200,"GET","Document",new JSONArray().put(row("Content-Disposition","attachment")).put(row("Content-Length","10")).put(row("Content-Encoding",coding)));
            check(!MdoBrowserCdpResponse.inspect(e).exactTextLength);
        }
        check(MdoBrowserCdpResponse.inspect(event(200,"GET","Document",new JSONArray().put(row("Content-Disposition","attachment")).put(row("Content-Length","0")).put(row("Content-Encoding","IDENTITY")))).exactTextLength);
        invalid(event(200,"GET","Document",new JSONArray().put(row("Content-Encoding","identity")).put(row("Content-Encoding","identity"))));
        check(!MdoBrowserCdpResponse.inspect(event(200,"GET","Document",new JSONArray().put(row("Content-Disposition","attachment")).put(row("Content-Length","10")).put(row("Transfer-Encoding","chunked")))).exactTextLength);
        for(java.lang.reflect.Field field:MdoBrowserCdpResponse.class.getDeclaredFields())check(java.lang.reflect.Modifier.isFinal(field.getModifiers())&&(field.getType()==String.class||field.getType()==int.class||field.getType()==long.class||field.getType()==boolean.class));
    }
    private static final class Wire implements MdoBrowserCdpSession.Wire{
        final ArrayDeque<byte[]> incoming=new ArrayDeque<>();final List<byte[]> touched=new ArrayList<>();final List<JSONObject> sent=new ArrayList<>();int closed;
        void add(JSONObject packet){incoming.add(packet.toString().getBytes(StandardCharsets.UTF_8));}
        public void send(byte[] bytes,int timeout)throws IOException{touched.add(bytes);try{JSONObject command=new JSONObject(new String(bytes,StandardCharsets.UTF_8));sent.add(command);add(new JSONObject().put("id",command.getInt("id")).put("result",new JSONObject()));}catch(Exception error){throw new IOException("controlled_wire_invalid");}}
        public byte[] receive(int timeout){byte[] value=incoming.poll();if(value!=null)touched.add(value);return value;}
        public void close(){closed++;}
    }
    private static void originalEvent()throws Exception{
        Wire wire=new Wire();AtomicBoolean current=new AtomicBoolean(true);MdoBrowserCdpSession[] original=new MdoBrowserCdpSession[1];List<MdoBrowserCdpResponse> seen=new ArrayList<>();
        original[0]=MdoBrowserCdpSession.owned(wire,current::get,(method,child,params)->{
            try{
                params.put("requestId","caller-chosen");params.put("responseHeaders",new JSONArray());params.put("responseStatusCode",404);
                MdoBrowserCdpResponse frozen=original[0].pausedResponse();seen.add(frozen);
                check(frozen.kind.equals("candidate")&&frozen.status==200&&frozen.reason.equals("attachment"));
                original[0].continuePausedRequest();check(original[0].pausedResponse()==frozen);
            }catch(org.json.JSONException error){throw new IOException("controlled_mutation_failed");}
        },()->1);
        wire.add(new JSONObject().put("method","Fetch.requestPaused").put("params",document("attachment; filename=\"private\"",null)));
        check(original[0].poll(100));check(original[0].pendingControls()==1);check(original[0].poll(100));check(original[0].pendingControls()==0);
        JSONObject sent=wire.sent.get(0);check(sent.getString("method").equals("Fetch.continueRequest"));
        check(sent.getJSONObject("params").length()==1&&sent.getJSONObject("params").getString("requestId").equals("interception-job-7.0"));
        try{original[0].pausedResponse();throw new AssertionError("Response authority escaped event");}catch(MdoBrowserCdpSession.Failure failure){check(failure.getMessage().equals("cdp_control_not_in_event")&&failure.getCause()==null);}
        Throwable[] failure=new Throwable[1];Thread thread=new Thread(()->{try{original[0].pausedResponse();}catch(Throwable error){failure[0]=error;}});thread.start();thread.join();
        check(failure[0] instanceof IOException&&failure[0].getMessage().equals("cdp_wrong_worker"));
        current.set(false);try{original[0].pausedResponse();throw new AssertionError("Retired scope used response");}catch(IOException error){check(error.getMessage().equals("cdp_owner_changed"));}
        check(seen.size()==1&&seen.get(0).kind.equals("candidate"));original[0].close();check(wire.closed==1);
        for(byte[] bytes:wire.touched){boolean cleared=true;for(byte value:bytes)if(value!=0)cleared=false;check(cleared);}
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();policy();displayMetadata();
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{originalEvent();}catch(Exception failure){throw new RuntimeException(failure);}}).get(15,java.util.concurrent.TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));}
        System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("original_event_snapshot",true)
            .put("provider_qualified",false).put("body_retention_activated",false).put("network_calls",0));
    }
}
