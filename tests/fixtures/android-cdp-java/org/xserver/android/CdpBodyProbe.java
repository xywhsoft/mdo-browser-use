package org.xserver.android;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual native session/strict codec/JNI validation, controlled response peer.
 * No provider, cache/save/permission UI or real Internet download is qualified. */
public final class CdpBodyProbe {
    private static int checks,realSessions,realAsyncPairs;
    private interface Work {void run()throws Exception;}
    private interface Event {void run(JSONObject params)throws IOException;}
    private interface Send {boolean run(JSONObject command)throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Body check "+(checks+1));checks++;}
    private static IOException rejected(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Missing body failure "+code);}catch(IOException failure){check(code.equals(failure.getMessage()));check(failure.getCause()==null);return failure;}
    }
    private static JSONObject result(int id,JSONObject value)throws Exception{return new JSONObject().put("id",id).put("result",value);}
    private static JSONObject data(String text,boolean base64,boolean eof)throws Exception{return new JSONObject().put("data",text).put("base64Encoded",base64).put("eof",eof);}
    private static JSONObject paused(String id)throws Exception{
        return new JSONObject().put("requestId",id).put("responseStatusCode",200).put("resourceType","Document")
            .put("request",new JSONObject().put("method","POST").put("url","https://private.invalid/?token=discarded").put("postData","private-request"))
            .put("responseHeaders",new JSONArray().put(new JSONObject().put("name","Content-Disposition").put("value","attachment; filename=private.bin")));
    }
    private static final class Wire implements MdoBrowserCdpSession.Wire,MdoBrowserCdpSession.Clock{
        final ArrayDeque<byte[]> incoming=new ArrayDeque<>();final List<byte[]> buffers=new ArrayList<>();
        final ArrayDeque<JSONObject> chunks=new ArrayDeque<>();final List<JSONObject> commands=new ArrayList<>();
        long now;int closes,stream;Send extra;
        public long now(){return now;}
        void add(JSONObject packet){incoming.add(packet.toString().getBytes(StandardCharsets.UTF_8));}
        public void send(byte[] bytes,int timeout)throws IOException{
            buffers.add(bytes);try{
                JSONObject command=new JSONObject(new String(bytes,StandardCharsets.UTF_8));commands.add(command);
                if(extra!=null&&extra.run(command))return;
                String method=command.getString("method");JSONObject value=new JSONObject();
                if(method.equals("Fetch.takeResponseBodyAsStream"))value.put("stream","stream-"+(++stream));
                else if(method.equals("IO.read"))value=chunks.isEmpty()?data("",false,true):chunks.removeFirst();
                else check(method.equals("IO.close")||method.equals("Fetch.failRequest")||method.equals("Fetch.continueRequest")||method.equals("Runtime.evaluate"));
                add(result(command.getInt("id"),value));
            }catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException("controlled_wire_invalid");}
        }
        public byte[] receive(int timeout){byte[] bytes=incoming.poll();if(bytes==null)now+=timeout;else buffers.add(bytes);return bytes;}
        public void close(){closes++;while(!incoming.isEmpty())Arrays.fill(incoming.removeFirst(),(byte)0);}
        long count(String method){return commands.stream().filter(row->method.equals(row.opt("method"))).count();}
        void cleared(){for(byte[] bytes:buffers){boolean zero=true;for(byte value:bytes)if(value!=0)zero=false;check(zero);}}
    }
    private static final class Context implements AutoCloseable{
        final Wire wire=new Wire();final AtomicBoolean live=new AtomicBoolean(true);final MdoBrowserCdpSession client;
        final List<MdoBrowserCdpSession.Body> bodies=new ArrayList<>();Event action;long limit=65536,declared=-1;String coding;int timeout=3000,serial;
        Context(){client=MdoBrowserCdpSession.owned(wire,live::get,(method,child,params)->{
            if(action!=null)action.run(params);else bodies.add(client().takePausedBody(limit,timeout));
        },wire);}
        MdoBrowserCdpSession client(){return client;}
        void pause(JSONObject value)throws Exception{wire.add(new JSONObject().put("method","Fetch.requestPaused").put("params",value));check(client.poll(100));}
        MdoBrowserCdpSession.Body claim()throws Exception{
            JSONObject value=paused("interception-job-"+(++serial)+".0");JSONArray headers=value.getJSONArray("responseHeaders");
            if(declared>=0)headers.put(new JSONObject().put("name","Content-Length").put("value",Long.toString(declared)));
            if(coding!=null)headers.put(new JSONObject().put("name","Content-Encoding").put("value",coding));
            pause(value);MdoBrowserCdpSession.Body body=bodies.get(bodies.size()-1);check(!body.ready());check(client.poll(100));check(body.ready());return body;
        }
        public void close(){client.close();wire.cleared();}
    }
    private static void success()throws Exception{
        try(Context c=new Context()){
            c.action=p->{try{p.put("requestId","forged");p.put("responseHeaders",new JSONArray());c.bodies.add(c.client.takePausedBody(65536,3000));}catch(org.json.JSONException failure){throw new IOException("controlled_edit");}};
            MdoBrowserCdpSession.Body body=c.claim();check(c.client.pendingBodies()==1);
            byte[] binary={0,1,2,(byte)255,(byte)128};String unicode="中\u0000🙂";
            c.wire.chunks.add(data(java.util.Base64.getEncoder().encodeToString(binary),true,false));c.wire.chunks.add(data(java.util.Base64.getEncoder().encodeToString(unicode.getBytes(StandardCharsets.UTF_8)),true,true));
            MdoBrowserCdpSession.BodyChunk first=c.client.readBody(body,100);check(!first.eof&&Arrays.equals(first.data,binary));first.close();for(byte b:first.data)check(b==0);
            try(MdoBrowserCdpSession.BodyChunk last=c.client.readBody(body,100)){check(last.eof&&Arrays.equals(last.data,unicode.getBytes(StandardCharsets.UTF_8)));}
            MdoBrowserCdpSession.BodyResult r=body.result();check(r.ok&&r.code==null&&r.eof&&r.claimMayHaveOccurred&&r.streamCloseConfirmed&&r.requestAbortConfirmed);
            check(r.bytes==binary.length+unicode.getBytes(StandardCharsets.UTF_8).length&&c.client.pendingBodies()==0&&!body.ready());
            check(c.wire.count("Fetch.takeResponseBodyAsStream")==1&&c.wire.count("IO.read")==2&&c.wire.count("IO.close")==1&&c.wire.count("Fetch.failRequest")==1);
            int read=0;for(JSONObject command:c.wire.commands){JSONObject p=command.getJSONObject("params");String m=command.getString("method");
                check(!command.has("sessionId"));if(m.equals("IO.read"))check(p.length()==2&&p.getInt("size")==65536-(read++==0?0:binary.length)&&p.getString("handle").equals("stream-1")&&!p.has("offset"));
                else if(m.equals("IO.close"))check(p.length()==1&&p.getString("handle").equals("stream-1"));
                else check(p.getString("requestId").equals("interception-job-1.0")&&p.length()==(m.equals("Fetch.failRequest")?2:1));
            }
            long before=c.wire.commands.size();rejected("cdp_body_terminal",()->c.client.readBody(body,100));rejected("cdp_body_terminal",()->c.client.cancelBody(body,100));
            body.cancel();check(body.result()==r&&c.wire.commands.size()==before);
        }
    }
    private static void admission()throws Exception{
        try(Context c=new Context()){
            rejected("cdp_control_not_in_event",()->c.client.takePausedBody(10,1000));check(c.wire.commands.isEmpty());
            c.action=p->{try{rejected("cdp_body_arguments_invalid",()->c.client.takePausedBody(0,1000));rejected("cdp_body_arguments_invalid",()->c.client.takePausedBody(1,999));
                c.client.continuePausedRequest();}catch(Exception failure){throw new IOException("controlled_admission_failure");}};
            c.pause(paused("id"));check(c.wire.count("Fetch.continueRequest")==1&&c.wire.count("Fetch.takeResponseBodyAsStream")==0);
        }
        try(Context c=new Context()){
            JSONObject value=paused("id");value.getJSONArray("responseHeaders").put(new JSONObject().put("name","Content-Length").put("value","100"));
            c.action=p->{try{rejected("cdp_body_limit",()->c.client.takePausedBody(99,1000));c.client.abortPausedRequest();}catch(Exception failure){throw new IOException("controlled_admission_failure");}};
            c.pause(value);check(c.wire.count("Fetch.failRequest")==1&&c.wire.count("Fetch.takeResponseBodyAsStream")==0);
        }
        try(Context c=new Context()){
            c.wire.add(new JSONObject().put("method","Fetch.requestPaused").put("params",paused("id").put("responseStatusCode",206)));
            rejected("cdp_body_not_candidate",()->c.client.poll(100));check(c.wire.commands.isEmpty());
        }
        try(Context c=new Context();Context other=new Context()){
            MdoBrowserCdpSession.Body body=c.claim();rejected("cdp_body_owner_mismatch",()->other.client.readBody(body,100));rejected("cdp_body_owner_mismatch",()->other.client.cancelBody(body,100));check(other.wire.commands.isEmpty());
            Throwable[] error=new Throwable[1];Thread foreign=new Thread(()->{try{c.client.readBody(body,100);}catch(Throwable failure){error[0]=failure;}});foreign.start();foreign.join();
            check(error[0]!=null&&error[0].getMessage().equals("cdp_wrong_worker"));check(c.wire.count("IO.read")==0);c.client.cancelBody(body,100);check(body.result().requestAbortConfirmed);
        }
    }
    private static void pendingAndQuota()throws Exception{
        try(Context c=new Context()){
            c.pause(paused("id"));MdoBrowserCdpSession.Body body=c.bodies.get(0);rejected("cdp_body_not_ready",()->c.client.readBody(body,100));check(c.wire.count("IO.read")==0);
            body.cancel();check(body.result()==null);check(c.client.poll(100));check(body.ready());c.client.cancelBody(body,100);
            check(!body.result().ok&&body.result().code.equals("cdp_body_cancelled")&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed);
        }
        try(Context c=new Context()){
            MdoBrowserCdpSession.Body original=c.claim();c.action=p->{try{rejected("cdp_session_busy",()->c.client.readBody(original,100));c.client.continuePausedRequest();}
                catch(Exception failure){throw new IOException("controlled_reentry_failure");}};
            c.pause(paused("another"));check(c.wire.count("IO.read")==0);check(c.client.poll(100));c.client.cancelBody(original,100);
        }
        try(Context c=new Context()){
            for(int i=0;i<8;i++)c.claim();check(c.client.pendingBodies()==8);
            c.action=p->{try{rejected("cdp_body_limit",()->c.client.takePausedBody(65536,3000));c.client.abortPausedRequest();}catch(Exception failure){throw new IOException("controlled_quota_failure");}};
            c.pause(paused("over-limit"));check(c.client.poll(100));check(c.client.pendingBodies()==8&&c.wire.count("Fetch.takeResponseBodyAsStream")==8);
            c.client.cancelBody(c.bodies.get(0),100);check(c.client.pendingBodies()==7);c.action=null;c.claim();check(c.client.pendingBodies()==8);
        }
    }
    private static void claimFailures()throws Exception{
        for(JSONObject value:new JSONObject[]{new JSONObject(),new JSONObject().put("stream",12),new JSONObject().put("stream",""),new JSONObject().put("stream","private\nvalue"),new JSONObject().put("stream","id").put("extra",true)}){
            try(Context c=new Context()){
                c.wire.extra=command->{if(!command.getString("method").equals("Fetch.takeResponseBodyAsStream"))return false;c.wire.add(result(command.getInt("id"),value));return true;};
                c.pause(paused("id"));MdoBrowserCdpSession.Body body=c.bodies.get(0);rejected("cdp_body_claim_reply_invalid",()->c.client.poll(100));
                check(body.result()!=null&&!body.result().ok&&body.result().claimMayHaveOccurred&&!body.result().requestAbortConfirmed&&!body.ready());
                check(c.wire.count("Fetch.takeResponseBodyAsStream")==1&&c.wire.count("IO.read")==0);
            }
        }
        try(Context c=new Context()){
            c.wire.extra=command->{c.wire.add(new JSONObject().put("id",command.getInt("id")).put("error",new JSONObject().put("code",-32601).put("message","private-provider-detail")));return true;};
            c.pause(paused("id"));MdoBrowserCdpSession.Body body=c.bodies.get(0);rejected("cdp_control_method_unsupported",()->c.client.poll(100));
            check(!body.result().claimMayHaveOccurred&&!body.result().ok&&body.result().code.equals("cdp_control_method_unsupported"));
        }
        try(Context c=new Context()){
            c.wire.extra=command->true;c.pause(paused("id"));MdoBrowserCdpSession.Body body=c.bodies.get(0);c.wire.now=5001;
            rejected("cdp_control_timeout",()->c.client.poll(100));check(body.result()!=null&&!body.result().ok&&!body.result().requestAbortConfirmed);
        }
        try(Context c=new Context()){
            c.claim();c.wire.extra=command->{c.wire.add(result(command.getInt("id"),new JSONObject().put("stream","stream-1")));return true;};
            c.pause(paused("second"));rejected("cdp_body_stream_reused",()->c.client.poll(100));check(c.bodies.get(0).result()!=null&&c.bodies.get(1).result()!=null);
        }
    }
    private static void dataFailures()throws Exception{
        for(int size:new int[]{65536,65537})try(Context c=new Context()){
            byte[] payload=new byte[size];for(int i=0;i<size;i++)payload[i]=(byte)(i*17);MdoBrowserCdpSession.Body body=c.claim();
            c.wire.chunks.add(data(java.util.Base64.getEncoder().encodeToString(payload),true,true));
            if(size==65536){try(MdoBrowserCdpSession.BodyChunk chunk=c.client.readBody(body,100)){check(Arrays.equals(chunk.data,payload)&&chunk.eof);}check(body.result().ok&&body.result().bytes==size);}
            else{rejected("cdp_body_data_invalid",()->c.client.readBody(body,100));check(!body.result().ok);}
        }
        List<JSONObject> bad=new ArrayList<>();bad.add(new JSONObject().put("data",1).put("eof",true));bad.add(new JSONObject().put("data","x"));
        bad.add(data("x",false,true).put("base64Encoded",1));bad.add(data("A==",true,true));bad.add(data("Zh==",true,true));bad.add(data("%%%%",true,true));
        bad.add(data("中",false,true));bad.add(data("ok",false,true).put("extra",true));
        for(JSONObject value:bad){try(Context c=new Context()){
            c.limit=2;MdoBrowserCdpSession.Body body=c.claim();c.wire.chunks.add(value);rejected("cdp_body_data_invalid",()->c.client.readBody(body,100));
            check(body.result()!=null&&!body.result().ok&&!body.result().requestAbortConfirmed&&c.wire.count("IO.read")==1);
        }}
        try(Context c=new Context()){
            c.limit=4;c.declared=4;MdoBrowserCdpSession.Body body=c.claim();c.wire.chunks.add(data("1234",false,false));c.wire.chunks.add(data("",false,true));
            try(MdoBrowserCdpSession.BodyChunk chunk=c.client.readBody(body,100)){check(chunk.data.length==4&&!chunk.eof);}
            try(MdoBrowserCdpSession.BodyChunk chunk=c.client.readBody(body,100)){check(chunk.eof&&chunk.data.length==0);}
            check(body.result().ok&&body.result().bytes==4);check(c.wire.commands.stream().filter(row->row.optString("method").equals("IO.read")).map(row->row.optJSONObject("params").optInt("size")).toArray()[1].equals(1));
        }
        try(Context c=new Context()){
            c.limit=1;c.declared=1;MdoBrowserCdpSession.Body body=c.claim();c.wire.chunks.add(data("a",false,false));c.wire.chunks.add(data("b",false,true));
            c.client.readBody(body,100).close();rejected("cdp_body_limit",()->c.client.readBody(body,100));check(!body.result().ok&&body.result().bytes==1);
        }
    }
    private static void cancellationAndTimeout()throws Exception{
        try(Context c=new Context()){
            c.timeout=1000;MdoBrowserCdpSession.Body body=c.claim();c.wire.now=1001;
            rejected("cdp_body_timeout",()->c.client.readBody(body,100));check(c.wire.count("IO.read")==0&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed);
            check(!body.result().ok&&body.result().code.equals("cdp_body_timeout"));c.claim();
        }
        try(Context c=new Context()){
            MdoBrowserCdpSession.Body body=c.claim();c.wire.extra=command->{if(command.getString("method").equals("IO.read")){Thread ui=new Thread(body::cancel);ui.start();ui.join();}return false;};
            rejected("cdp_body_cancelled",()->c.client.readBody(body,100));check(body.result()!=null&&!body.result().ok&&body.result().requestAbortConfirmed&&body.result().streamCloseConfirmed);
        }
        for(String drop:new String[]{"IO.read","IO.close","Fetch.failRequest"}){
            try(Context c=new Context()){
                c.declared=0;
                MdoBrowserCdpSession.Body body=c.claim();c.wire.extra=command->command.getString("method").equals(drop);
                rejected(drop.equals("IO.read")?"cdp_command_timeout":"cdp_body_cleanup_unconfirmed",()->c.client.readBody(body,100));
                check(body.result()!=null&&!body.result().ok&&body.result().claimMayHaveOccurred&&!body.result().requestAbortConfirmed);
                check(c.wire.count(drop)==1&&body.result().streamCloseConfirmed==drop.equals("Fetch.failRequest"));
                MdoBrowserCdpSession.BodyResult first=body.result();c.client.close();body.cancel();check(body.result()==first);
            }
        }
        try(Context c=new Context()){
            MdoBrowserCdpSession.Body body=c.claim();c.live.set(false);rejected("cdp_owner_changed",()->c.client.readBody(body,100));
            check(body.result()!=null&&!body.result().ok&&!body.result().requestAbortConfirmed&&c.wire.count("IO.read")==0);
        }
        for(boolean allocation:new boolean[]{false,true})try(Context c=new Context()){
            MdoBrowserCdpSession.Body body=c.claim();c.wire.extra=command->{if(command.getString("method").equals("IO.read")){if(allocation)throw new OutOfMemoryError("private-allocation-detail");throw new IOException("private-send-detail");}return false;};
            rejected(allocation?"cdp_dispatch_failed":"cdp_transport_failed",()->c.client.readBody(body,100));
            check(body.result()!=null&&!body.result().ok&&c.wire.count("IO.read")==1&&!body.result().requestAbortConfirmed);
        }
    }
    private static void textIntegrity()throws Exception{
        try(Context c=new Context()){
            c.declared=5;MdoBrowserCdpSession.Body body=c.claim();c.wire.chunks.add(data("ab",false,false));c.wire.chunks.add(data("中",false,true));
            c.client.readBody(body,100).close();try(MdoBrowserCdpSession.BodyChunk last=c.client.readBody(body,100)){check(Arrays.equals(last.data,"中".getBytes(StandardCharsets.UTF_8)));}
            check(body.result().ok&&body.result().bytes==5);
        }
        // Pinned Chromium stream pipe cuts by bytes; its JSON String8 writer
        // drops a truncated UTF-8 prefix/suffix. These are its possible wire
        // results for "ab E4" then "B8 AD". Do not report a successful file.
        try(Context c=new Context()){
            c.declared=5;MdoBrowserCdpSession.Body body=c.claim();c.wire.chunks.add(data("ab",false,false));c.wire.chunks.add(data("",false,false));c.wire.chunks.add(data("",false,true));
            c.client.readBody(body,100).close();c.client.readBody(body,100).close();rejected("cdp_body_length_mismatch",()->c.client.readBody(body,100));
            check(!body.result().ok&&body.result().eof&&body.result().bytes==2&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed);
            check(c.wire.count("IO.read")==3&&c.wire.count("Fetch.takeResponseBodyAsStream")==1);c.claim();
        }
        for(boolean encoded:new boolean[]{false,true})try(Context c=new Context()){
            if(encoded){c.declared=10;c.coding="gzip";}MdoBrowserCdpSession.Body body=c.claim();c.wire.chunks.add(data("text",false,true));
            rejected("cdp_body_text_integrity_unavailable",()->c.client.readBody(body,100));
            check(!body.result().ok&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed&&c.wire.count("IO.read")==1);
        }
        try(Context c=new Context()){
            c.declared=3;MdoBrowserCdpSession.Body body=c.claim();c.wire.chunks.add(data("AQ==",true,false));c.wire.chunks.add(data("ab",false,true));
            c.client.readBody(body,100).close();rejected("cdp_body_encoding_changed",()->c.client.readBody(body,100));check(!body.result().ok&&body.result().bytes==1);
        }
    }
    private static void realWire()throws Exception{
        for(int mode=35;mode<=37;mode++){
            CdpNativeProbe.fixtureStart(mode);final MdoBrowserCdpSession[] client={null};List<MdoBrowserCdpSession.Body> bodies=new ArrayList<>();
            try{
                client[0]=MdoBrowserCdpSession.open("test-001",500,()->true,(m,s,p)->{
                    try{p.put("requestId","forged");p.remove("responseHeaders");bodies.add(client[0].takePausedBody(64,3000));}
                    catch(org.json.JSONException failure){throw new IOException("controlled_edit");}
                });
                client[0].call("Runtime.evaluate",null,null,1000,false);check(bodies.size()==1);MdoBrowserCdpSession.Body body=bodies.get(0);check(body.ready());
                if(mode==36){client[0].cancelBody(body,500);check(!body.result().ok&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed);}
                else if(mode==37){rejected("cdp_command_timeout",()->client[0].readBody(body,80));check(!body.result().ok&&!body.result().requestAbortConfirmed);}
                else{
                    try(MdoBrowserCdpSession.BodyChunk first=client[0].readBody(body,500)){check(!first.eof&&Arrays.equals(first.data,new byte[]{0,1,(byte)255,(byte)128}));}
                    try(MdoBrowserCdpSession.BodyChunk last=client[0].readBody(body,500)){check(last.eof&&Arrays.equals(last.data,"中".getBytes(StandardCharsets.UTF_8)));}
                    check(body.result().ok&&body.result().bytes==7&&body.result().eof&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed);
                }
                realSessions++;
            }finally{if(client[0]!=null)client[0].close();check(CdpNativeProbe.fixtureFinish()==(1+((mode==35?6:mode==36?4:3)<<8)));}
        }
    }
    private static void tick(Context c)throws Exception{c.client.serviceBodies(100);c.client.poll(20,100);}
    private static void finish(Context c,MdoBrowserCdpSession.Body body)throws Exception{
        for(int i=0;i<12&&body.result()==null;i++)tick(c);check(body.result()!=null);
    }
    private static void asynchronous()throws Exception{
        try(Context c=new Context();Context page=new Context()){
            c.timeout=15000;MdoBrowserCdpSession.Body body=c.claim();int[] read={0};
            c.wire.extra=command->{if(command.getString("method").equals("IO.read")){read[0]=command.getInt("id");return true;}return false;};
            c.client.requestBodyRead(body);check(c.wire.count("IO.read")==0);check(c.client.serviceBodies(100));
            check(c.client.takeBodyChunk(body)==null&&c.wire.now==0);rejected("cdp_body_read_pending",()->c.client.requestBodyRead(body));
            page.client.call("Runtime.evaluate",null,null,100,false);check(page.wire.count("Runtime.evaluate")==1);
            c.wire.now=6000;check(!c.client.poll(20,100));check(body.result()==null&&c.client.pendingControls()==1&&c.wire.count("IO.read")==1);
            c.wire.add(result(read[0],data("AAE=",true,true)));check(c.client.poll(100));check(body.result()==null);
            check(!c.client.serviceBodies(100)&&c.wire.count("IO.close")==0); // Consumer backpressure includes EOF.
            try(MdoBrowserCdpSession.BodyChunk chunk=c.client.takeBodyChunk(body)){check(chunk.eof&&Arrays.equals(chunk.data,new byte[]{0,1}));}
            finish(c,body);check(body.result().ok&&c.wire.count("IO.read")==1);
        }
        for(boolean failed:new boolean[]{false,true})try(Context c=new Context()){
            c.timeout=15000;MdoBrowserCdpSession.Body body=c.claim();int[] read={0};
            c.wire.extra=command->{if(command.getString("method").equals("IO.read")){read[0]=command.getInt("id");return true;}return false;};
            c.client.requestBodyRead(body);c.client.serviceBodies(100);Thread ui=new Thread(body::cancel);ui.start();ui.join();
            tick(c);tick(c);check(body.result()==null&&c.client.pendingBodies()==1&&c.client.pendingControls()==1);
            check(c.client.takeBodyChunk(body)==null&&c.wire.count("IO.close")==1&&c.wire.count("Fetch.failRequest")==1);
            if(failed)c.wire.add(new JSONObject().put("id",read[0]).put("error",new JSONObject().put("code",-32000).put("message","private-cancelled-read")));
            else c.wire.add(result(read[0],data("private-late-data",false,true)));
            check(c.client.poll(100));check(!body.result().ok&&body.result().code.equals("cdp_body_cancelled")&&body.result().bytes==0);
            check(body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed&&c.client.pendingBodies()==0&&c.client.pendingControls()==0);
            c.claim(); // Draining the original late row keeps the connection usable.
        }
        try(Context c=new Context()){
            c.timeout=15000;MdoBrowserCdpSession.Body body=c.claim();c.wire.extra=command->command.getString("method").equals("IO.read");
            c.client.requestBodyRead(body);c.client.serviceBodies(100);body.cancel();tick(c);tick(c);check(body.result()==null);
            c.wire.now+=5001;rejected("cdp_body_cleanup_unconfirmed",()->c.client.serviceBodies(100));
            MdoBrowserCdpSession.BodyResult original=body.result();check(!original.ok&&original.streamCloseConfirmed&&original.requestAbortConfirmed);
            check(c.client.pendingBodies()==0&&c.client.pendingControls()==0);body.cancel();check(body.result()==original);
        }
        try(Context c=new Context()){
            c.timeout=1000;MdoBrowserCdpSession.Body body=c.claim();c.wire.now=1001;finish(c,body);
            check(body.result().code.equals("cdp_body_timeout")&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed&&c.wire.count("IO.read")==0);
        }
        try(Context c=new Context()){
            MdoBrowserCdpSession.Body body=c.claim();c.wire.chunks.add(data("AQ==",true,false));c.client.requestBodyRead(body);tick(c);
            java.lang.reflect.Field field=body.getClass().getDeclaredField("chunk");field.setAccessible(true);MdoBrowserCdpSession.BodyChunk buffered=(MdoBrowserCdpSession.BodyChunk)field.get(body);check(buffered.data[0]==1);
            check(!c.client.serviceBodies(100)&&!c.client.serviceBodies(100)&&c.wire.count("IO.read")==1);
            body.cancel();finish(c,body);check(buffered.data[0]==0&&body.result().code.equals("cdp_body_cancelled"));
        }
        try(Context c=new Context()){
            MdoBrowserCdpSession.Body body=c.claim();boolean[] closed={false};
            c.wire.extra=command->{if(command.getString("method").equals("IO.read")){
                Thread ui=new Thread(()->{c.client.close();closed[0]=true;});ui.start();ui.join(500);check(!ui.isAlive()&&closed[0]);return true;
            }return false;};
            c.client.requestBodyRead(body);c.client.serviceBodies(100);check(body.result()!=null&&!body.result().ok&&c.wire.closes==1);
        }
        try(Context c=new Context()){
            c.timeout=15000;MdoBrowserCdpSession.Body body=c.claim();int[] read={0};
            c.wire.extra=command->{if(command.getString("method").equals("IO.read")){read[0]=command.getInt("id");return true;}
                if(command.getString("method").equals("Runtime.evaluate"))c.wire.add(result(read[0],data("AQ==",true,true)));return false;};
            c.client.requestBodyRead(body);c.client.serviceBodies(100);c.client.call("Runtime.evaluate",null,null,100,false);
            try(MdoBrowserCdpSession.BodyChunk chunk=c.client.takeBodyChunk(body)){check(chunk.eof&&chunk.data[0]==1);}finish(c,body);check(body.result().ok);
        }
    }
    private static void realAsyncPair()throws Exception{
        CdpNativeProbe.fixtureStart(38);MdoBrowserCdpSession[] network={null};MdoBrowserCdpSession page=null;
        List<MdoBrowserCdpSession.Body> bodies=new ArrayList<>();MdoBrowserCdpSession.BodyChunk chunk=null;
        try{
            network[0]=MdoBrowserCdpSession.open("test-001",500,()->true,(m,s,p)->bodies.add(network[0].takePausedBody(64,15000)));
            page=MdoBrowserCdpSession.open("test-001",500,()->true,(m,s,p)->{});
            network[0].call("Runtime.evaluate",null,null,1000,false);check(bodies.size()==1);MdoBrowserCdpSession.Body body=bodies.get(0);
            network[0].requestBodyRead(body);check(network[0].serviceBodies(100));check(network[0].takeBodyChunk(body)==null);
            check(!network[0].poll(20,100));long started=System.nanoTime();
            try(MdoBrowserCdpSession.Cooperation scope=MdoBrowserCdpSession.cooperate(()->{
                try{if(network[0].idle()){network[0].serviceBodies(100);network[0].poll(20,100);}}
                catch(IOException failure){throw new IllegalStateException(failure);}
            })){page.call("Runtime.evaluate",null,null,1000,false);}
            check(System.nanoTime()-started<1500000000L);
            for(int i=0;i<20&&chunk==null;i++){chunk=network[0].takeBodyChunk(body);if(chunk==null)network[0].poll(20,100);}
            check(chunk!=null&&chunk.eof&&Arrays.equals(chunk.data,new byte[]{0,1,(byte)255,(byte)128}));chunk.close();chunk=null;
            for(int i=0;i<12&&body.result()==null;i++){network[0].serviceBodies(100);network[0].poll(20,100);}
            check(body.result()!=null&&body.result().ok&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed);realAsyncPairs++;
        }finally{if(chunk!=null)chunk.close();if(network[0]!=null)network[0].close();if(page!=null)page.close();check(CdpNativeProbe.fixtureFinish()==(1+(6<<8)));}
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{success();admission();pendingAndQuota();claimFailures();dataFailures();cancellationAndTimeout();textIntegrity();asynchronous();if(args.length==0){realWire();realAsyncPair();}}catch(Exception failure){throw new RuntimeException(failure);}}).get(30,java.util.concurrent.TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));}
        System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("original_body_permits",true).put("real_body_sessions",realSessions).put("real_async_body_pairs",realAsyncPairs).put("provider_qualified",false).put("product_downloads_activated",false));
    }
}
