package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Original dispatcher/codec/JNI and local kernel peers; no provider, download
 * activation, external network or Android framework is qualified here. */
public final class CdpControlProbe {
    private static int checks,realSessions;
    private interface Operation {void run() throws Exception;}
    private interface Send {void sent(JSONObject command) throws IOException;}
    private static void check(boolean value){if(!value)throw new AssertionError("Flow control check "+(checks+1));checks++;}
    private static MdoBrowserCdpSession.Failure failure(String code,boolean effect,Operation operation)throws Exception{
        try{operation.run();throw new AssertionError("Missing flow control failure: "+code);}
        catch(MdoBrowserCdpSession.Failure problem){check(code.equals(problem.getMessage()));check(problem.effectMayHaveOccurred==effect);check(problem.getCause()==null);return problem;}
    }
    private static JSONObject object(String key,Object value)throws IOException{
        try{return new JSONObject().put(key,value);}catch(org.json.JSONException error){throw new IOException(error);}
    }
    private static String reply(int id,String child)throws IOException{
        try{JSONObject row=object("id",id).put("result",new JSONObject());if(child!=null)row.put("sessionId",child);return row.toString();}
        catch(org.json.JSONException error){throw new IOException(error);}
    }
    private static String paused(String id,String child,boolean response)throws IOException{
        try{JSONObject params=object("requestId",id);if(response)params.put("responseStatusCode",200);
            JSONObject row=object("method","Fetch.requestPaused").put("params",params);if(child!=null)row.put("sessionId",child);return row.toString();}
        catch(org.json.JSONException error){throw new IOException(error);}
    }
    private static String rejection(int id,int code,String child)throws IOException{
        try{JSONObject row=object("id",id).put("error",object("code",code).put("message","private-provider-data").put("data","private-url"));if(child!=null)row.put("sessionId",child);return row.toString();}
        catch(org.json.JSONException error){throw new IOException(error);}
    }
    private static final class Wire implements MdoBrowserCdpSession.Wire,MdoBrowserCdpSession.Clock{
        final ArrayDeque<byte[]> queued=new ArrayDeque<>();final List<byte[]> buffers=new ArrayList<>();
        final List<JSONObject> sent=new ArrayList<>();final List<Integer> budgets=new ArrayList<>();
        long now;int closes;boolean clockFailed;Send afterSend;
        public long now(){if(clockFailed)throw new OutOfMemoryError("private-clock-detail");return now;}
        void add(String data){queued.add(data.getBytes(StandardCharsets.UTF_8));}
        public void send(byte[] data,int timeout)throws IOException{
            buffers.add(data);budgets.add(timeout);
            try{JSONObject command=new JSONObject(new String(data,StandardCharsets.UTF_8));sent.add(command);if(afterSend!=null)afterSend.sent(command);}
            catch(org.json.JSONException error){throw new IOException(error);}
        }
        public byte[] receive(int timeout){byte[] data=queued.poll();if(data==null)now+=timeout;else buffers.add(data);return data;}
        public void close(){closes++;}
        void cleared(){for(byte[] data:buffers){boolean zero=true;for(byte item:data)if(item!=0)zero=false;check(zero);}}
    }
    private static final class Context implements AutoCloseable{
        final Wire wire=new Wire();final AtomicBoolean current=new AtomicBoolean(true);
        final List<MdoBrowserCdpSession.ControlReceipt> receipts=new ArrayList<>();
        MdoBrowserCdpSession client;MdoBrowserCdpSession.Events action;
        Context(){client=MdoBrowserCdpSession.owned(wire,current::get,(m,s,p)->{
            if(action!=null)action.event(m,s,p);else receipts.add(client.continuePausedRequest());
        },wire);}
        void call()throws IOException{client.call("Runtime.evaluate",null,null,100,true);}
        void ackControls(){wire.afterSend=command->{try{if(command.getString("method").startsWith("Fetch."))wire.add(reply(command.getInt("id"),command.optString("sessionId",null)));}
            catch(org.json.JSONException error){throw new IOException(error);}};}
        public void close(){client.close();wire.cleared();}
    }
    private static void settled(MdoBrowserCdpSession.ControlReceipt receipt,boolean ok,String code,boolean effect){
        MdoBrowserCdpSession.ControlResult result=receipt.result();check(result!=null&&!receipt.pending());check(result.ok==ok);
        check(java.util.Objects.equals(result.code,code));check(result.effectMayHaveOccurred==effect&&receipt.effectMayHaveOccurred()==effect);
    }
    private static void successAndOrdering()throws Exception{
        try(Context c=new Context()){
            String original="interception-job-7.0|quote\"slash\\";
            c.wire.add(paused(original,null,true));c.wire.add(reply(2,null));c.wire.add(reply(1,null));
            c.action=(m,s,p)->{try{
                p.put("requestId","retargeted");p.put("responseStatusCode",404);p.put("sessionId","foreign");
                c.receipts.add(c.client.continuePausedRequest());
                try{c.client.abortPausedRequest();throw new AssertionError("Event replay allowed");}
                catch(MdoBrowserCdpSession.EventFailure expected){check(expected.code.equals("cdp_control_duplicate"));}
                try{c.client.call("Runtime.enable",null,null,100,false);throw new AssertionError("Ordinary reentry allowed");}
                catch(MdoBrowserCdpSession.Failure expected){check(expected.getMessage().equals("cdp_session_busy"));}
            }catch(org.json.JSONException error){throw new IOException(error);}};
            c.call();check(c.wire.sent.size()==2);JSONObject command=c.wire.sent.get(1),params=command.getJSONObject("params");
            check(command.getInt("id")==2&&command.getString("method").equals("Fetch.continueRequest"));
            check(!command.has("sessionId")&&params.length()==1&&params.getString("requestId").equals(original));
            settled(c.receipts.get(0),true,null,true);check(c.client.pendingControls()==0);
            c.wire.add(reply(3,null));c.call();check(c.wire.sent.get(2).getInt("id")==3);
            failure("cdp_control_not_in_event",false,()->c.client.continuePausedRequest());
        }
        for(boolean poll:new boolean[]{false,true})try(Context c=new Context()){
            c.wire.add(paused("interception-job-1.0",null,true));c.wire.add(reply(1,null));c.call();
            MdoBrowserCdpSession.ControlReceipt r=c.receipts.get(0);check(r.pending()&&r.effectMayHaveOccurred()&&c.client.pendingControls()==1);
            c.wire.add(reply(2,null));if(poll)check(c.client.poll(100));else{c.wire.add(reply(3,null));c.call();}
            settled(r,true,null,true);check(c.client.pendingControls()==0);
        }
        try(Context c=new Context()){
            c.client.admitChild("child-001");c.ackControls();
            c.wire.add(paused("same",null,false));c.wire.add(paused("same",null,true));c.wire.add(paused("same","child-001",true));
            // Ordinary completion is appended only after all three controls.
            Send ack=c.wire.afterSend;c.wire.afterSend=cmd->{ack.sent(cmd);if(c.wire.sent.size()==4)c.wire.add(reply(1,null));};
            c.call();check(c.receipts.size()==3&&c.client.pendingControls()==0);
            for(MdoBrowserCdpSession.ControlReceipt r:c.receipts)settled(r,true,null,true);
            check(c.wire.sent.get(3).getString("sessionId").equals("child-001"));
            c.wire.add(paused("same",null,true));failure("cdp_control_duplicate",true,c::call);check(c.wire.sent.size()==5);
        }
        try(Context c=new Context()){
            c.client.admitChild("child-001");c.action=(m,s,p)->c.receipts.add(c.client.abortPausedRequest());
            c.wire.add(paused("original","child-001",true));c.wire.add(reply(2,"child-001"));c.wire.add(reply(1,null));c.call();
            JSONObject cmd=c.wire.sent.get(1),p=cmd.getJSONObject("params");
            check(cmd.getString("method").equals("Fetch.failRequest")&&cmd.getString("sessionId").equals("child-001"));
            check(p.length()==2&&p.getString("requestId").equals("original")&&p.getString("errorReason").equals("Aborted"));
            settled(c.receipts.get(0),true,null,true);
        }
    }
    private static void invalidAndNegative()throws Exception{
        String[] params={"{}","{\"requestId\":null}","{\"requestId\":7}","{\"requestId\":\"\"}","{\"requestId\":\"has space\"}",
            "{\"requestId\":\"line\\n\"}","{\"requestId\":\"\\u0080\"}","{\"requestId\":\"id\",\"responseStatusCode\":99}",
            "{\"requestId\":\"id\",\"responseStatusCode\":600}","{\"requestId\":\"id\",\"responseStatusCode\":200.0}",
            "{\"requestId\":\"id\",\"responseErrorReason\":null}","{\"requestId\":\"id\",\"responseErrorReason\":\"\"}"};
        for(String p:params)try(Context c=new Context()){
            c.wire.add("{\"method\":\"Fetch.requestPaused\",\"params\":"+p+"}");failure("cdp_control_event_invalid",true,c::call);check(c.wire.sent.size()==1&&c.receipts.isEmpty());
        }
        for(int length:new int[]{256,257})try(Context c=new Context()){
            c.wire.add(paused(new String(new char[length]).replace('\0','x'),null,false));
            if(length==256){c.wire.add(reply(2,null));c.wire.add(reply(1,null));c.call();settled(c.receipts.get(0),true,null,true);}
            else{failure("cdp_control_event_invalid",true,c::call);check(c.wire.sent.size()==1);}
        }
        try(Context c=new Context()){
            c.wire.add("{\"method\":\"Runtime.event\",\"params\":{}}");
            c.action=(m,s,p)->{try{c.client.continuePausedRequest();throw new AssertionError("Unrelated event accepted");}
                catch(MdoBrowserCdpSession.Failure expected){check(expected.getMessage().equals("cdp_control_not_in_event"));}};
            c.wire.add(reply(1,null));c.call();check(c.wire.sent.size()==1);
        }
        for(int code:new int[]{-32601,-32602,0})try(Context c=new Context()){
            c.wire.add(paused("id",null,true));c.wire.add(rejection(2,code,null));
            String expected=code==-32601?"cdp_control_method_unsupported":"cdp_control_rejected";
            MdoBrowserCdpSession.Failure f=failure(expected,true,c::call);check(f.protocolCode==code);
            settled(c.receipts.get(0),false,expected,code!=-32601);check(c.client.pendingControls()==0&&c.wire.sent.size()==2);
        }
        try(Context c=new Context()){
            c.client.admitChild("child-001");c.wire.add(paused("id","child-001",true));c.wire.add(reply(2,null));
            failure("cdp_control_reply_mismatch",true,c::call);settled(c.receipts.get(0),false,"cdp_control_reply_mismatch",true);
        }
        try(Context c=new Context()){
            c.wire.add(paused("id",null,true));c.wire.add(reply(2,null));c.wire.add(reply(2,null));
            failure("cdp_reply_mismatch",true,c::call);settled(c.receipts.get(0),true,null,true);check(c.wire.sent.size()==2);
        }
        try(Context c=new Context()){
            c.wire.add(paused("id",null,true));c.action=(m,s,p)->{throw new MdoBrowserCdpSession.Failure("private-code",0,false);};
            failure("cdp_event_failed",true,c::call);check(c.wire.sent.size()==1);
        }
    }
    private static void cancellationAndClocks()throws Exception{
        try(Context c=new Context()){
            c.wire.add(paused("id",null,true));c.wire.afterSend=cmd->{try{if(cmd.getString("method").startsWith("Fetch."))throw new IOException("private-wire-detail");}
                catch(org.json.JSONException error){throw new IOException(error);}};
            failure("cdp_control_send_failed",true,c::call);settled(c.receipts.get(0),false,"cdp_control_send_failed",true);check(c.wire.sent.size()==2);
        }
        try(Context c=new Context()){
            c.wire.add(paused("id",null,true));c.wire.afterSend=cmd->{try{if(cmd.getString("method").startsWith("Fetch."))c.current.set(false);}
                catch(org.json.JSONException error){throw new IOException(error);}};
            failure("cdp_owner_changed",true,c::call);settled(c.receipts.get(0),false,"cdp_control_cancelled",true);
        }
        try(Context c=new Context()){
            CountDownLatch sending=new CountDownLatch(1),closed=new CountDownLatch(1);
            Thread ui=new Thread(()->{try{check(sending.await(2,TimeUnit.SECONDS));c.client.close();closed.countDown();}catch(InterruptedException error){throw new AssertionError(error);}},"controlled-ui-close");
            c.wire.afterSend=cmd->{try{if(cmd.getString("method").startsWith("Fetch.")){sending.countDown();check(closed.await(2,TimeUnit.SECONDS));}}
                catch(org.json.JSONException|InterruptedException error){throw new IOException(error);}};
            c.wire.add(paused("id",null,true));ui.start();failure("cdp_session_closed",true,c::call);ui.join(2000);check(!ui.isAlive());
            settled(c.receipts.get(0),false,"cdp_control_cancelled",true);check(c.client.pendingControls()==0&&c.wire.sent.size()==2);
            failure("cdp_session_closed",false,()->c.client.poll(100));
        }
        try(Context c=new Context()){
            c.client.admitChild("child-001");c.wire.add(paused("id","child-001",true));c.wire.add(reply(1,null));c.call();
            c.client.retireChild("child-001");settled(c.receipts.get(0),false,"cdp_control_cancelled",true);
            c.wire.add(reply(2,"child-001"));c.wire.add(reply(3,null));c.call();check(c.wire.sent.size()==3&&c.client.pendingControls()==0);
            failure("cdp_session_invalid",false,()->c.client.admitChild("child-001"));
        }
        for(int mode=0;mode<4;mode++)try(Context c=new Context()){
            c.wire.add(paused("id",null,true));c.wire.add(reply(1,null));c.call();
            if(mode==0)c.wire.now=5000;else if(mode==1)c.wire.now=-1;else if(mode==2)c.wire.clockFailed=true;
            else{c.wire.now=4970;check(!c.client.poll(20));c.wire.now=5000;}
            failure(mode==2?"cdp_control_clock_failed":"cdp_control_timeout",false,c::call);
            settled(c.receipts.get(0),false,mode==2?"cdp_control_cancelled":"cdp_control_timeout",true);
            check(c.wire.sent.size()==2&&c.client.pendingControls()==0);
        }
        try(Context c=new Context()){
            c.action=(m,s,p)->{c.wire.now=81;c.receipts.add(c.client.continuePausedRequest());};
            c.wire.add(paused("id",null,false));c.wire.add(reply(2,null));c.wire.add(reply(1,null));c.call();
            check(c.wire.budgets.get(1)==19);settled(c.receipts.get(0),true,null,true);
        }
        try(Context c=new Context()){
            c.action=(m,s,p)->{c.wire.now=91;c.receipts.add(c.client.continuePausedRequest());};c.wire.add(paused("id",null,false));
            failure("cdp_event_failed",true,c::call);check(c.wire.sent.size()==1&&c.receipts.isEmpty());
        }
    }
    private static void cooperation()throws Exception{
        try(Context page=new Context();Context network=new Context();Context nested=new Context()){
            network.wire.add(paused("interception-job-progress.0",null,true));network.wire.add(reply(1,null));nested.wire.add(reply(1,null));
            final int[] turns={0};
            try(MdoBrowserCdpSession.Cooperation scope=MdoBrowserCdpSession.cooperate(()->{
                try{
                    if(++turns[0]==1)nested.call();
                    if(network.client.idle())network.client.poll(20,100);
                    if(!network.receipts.isEmpty()&&!network.receipts.get(0).pending())page.wire.add(reply(1,null));
                }catch(IOException problem){throw new IllegalStateException(problem);}
            })){
                page.call();check(turns[0]==2&&page.wire.sent.size()==1&&network.wire.sent.size()==1);
                settled(network.receipts.get(0),true,null,true);
            }
            // The scheduler is scoped to this worker and no longer runs after
            // close; nested protocol progress never recursively calls it.
            page.wire.add(reply(2,null));page.call();check(turns[0]==2);
        }
        try(Context page=new Context()){
            try(MdoBrowserCdpSession.Cooperation scope=MdoBrowserCdpSession.cooperate(()->page.wire.now=95)){
                failure("cdp_command_timeout",true,page::call);check(page.wire.sent.size()==1);
            }
        }
        try(Context network=new Context()){
            network.action=(m,s,p)->{network.wire.now=60;network.receipts.add(network.client.continuePausedRequest());};
            network.wire.add(paused("interception-job-slice.0",null,true));check(network.client.poll(20,100));
            check(network.wire.budgets.get(0)==40);network.wire.add(reply(1,null));check(network.client.poll(20,100));
            settled(network.receipts.get(0),true,null,true);
        }
        try(Context network=new Context()){
            network.action=(m,s,p)->network.wire.now=100;network.wire.add(paused("interception-job-late.0",null,true));
            failure("cdp_command_timeout",false,()->network.client.poll(20,100));check(network.wire.sent.isEmpty());
        }
    }
    private static void limits()throws Exception{
        try(Context c=new Context()){
            for(int i=0;i<257;i++)c.wire.add(paused("pending-"+i,null,true));failure("cdp_control_limit",true,c::call);
            check(c.wire.sent.size()==257&&c.receipts.size()==256&&c.client.pendingControls()==0);
            for(MdoBrowserCdpSession.ControlReceipt r:c.receipts)settled(r,false,"cdp_control_cancelled",true);
        }
        try(Context c=new Context()){
            c.ackControls();Send ack=c.wire.afterSend;
            // ACKed IDs retain lifetime tombstones; command boundaries cannot
            // reset replay/resource admission. Each RPC stays below event cap.
            for(int round=0;round<64;round++){
                final int first=c.wire.sent.size()+1;
                c.wire.afterSend=cmd->{ack.sent(cmd);try{if(cmd.getInt("id")==first+64)c.wire.add(reply(first,null));}
                    catch(org.json.JSONException error){throw new IOException(error);}};
                for(int j=0;j<64;j++)c.wire.add(paused("history-"+(round*64+j),null,true));c.call();check(c.client.pendingControls()==0);
            }
            check(c.receipts.size()==4096);c.wire.add(paused("over-limit",null,true));failure("cdp_control_limit",true,c::call);
            check(c.wire.sent.size()==4161);for(MdoBrowserCdpSession.ControlReceipt r:c.receipts)settled(r,true,null,true);
        }
        try(Context c=new Context()){
            Field serial=MdoBrowserCdpSession.class.getDeclaredField("serial");serial.setAccessible(true);serial.setInt(c.client,Integer.MAX_VALUE-1);
            c.wire.add(paused("id",null,true));failure("cdp_control_limit",true,c::call);check(c.wire.sent.size()==1);
        }
    }
    private static void realWire()throws Exception{
        for(int mode=28;mode<=33;mode++){
            final int selected=mode;CdpNativeProbe.fixtureStart(mode);List<MdoBrowserCdpSession.ControlReceipt> receipts=new ArrayList<>();
            final MdoBrowserCdpSession[] holder=new MdoBrowserCdpSession[1];
            try{
                holder[0]=MdoBrowserCdpSession.open("test-001",500,()->true,(m,s,p)->receipts.add(selected==31?holder[0].abortPausedRequest():holder[0].continuePausedRequest()));
                if(mode==30||mode==31)holder[0].admitChild("child-001");
                if(mode==30)failure("cdp_control_reply_mismatch",true,()->holder[0].call("Runtime.evaluate",null,null,500,true));
                else if(mode==31)failure("cdp_control_method_unsupported",true,()->holder[0].call("Runtime.evaluate",null,null,500,true));
                else if(mode==32)failure("cdp_command_timeout",true,()->holder[0].call("Runtime.evaluate",null,null,80,true));
                else{holder[0].call("Runtime.evaluate",null,null,500,true);
                    if(mode==29){check(receipts.get(0).pending());check(holder[0].poll(500));}}
                check(receipts.size()==1);settled(receipts.get(0),mode==28||mode==29||mode==33,
                    mode==30?"cdp_control_reply_mismatch":mode==31?"cdp_control_method_unsupported":mode==32?"cdp_control_cancelled":null,mode!=31);
                check(holder[0].pendingControls()==0);
            }finally{if(holder[0]!=null)holder[0].close();check(CdpNativeProbe.fixtureFinish()==(1+(2<<8)));}
            realSessions++;
        }
    }
    private static void realCooperation()throws Exception{
        CdpNativeProbe.fixtureStart(34);final MdoBrowserCdpSession[] network={null};List<MdoBrowserCdpSession.ControlReceipt> receipts=new ArrayList<>();
        MdoBrowserCdpSession page=null;
        try{
            page=MdoBrowserCdpSession.open("test-001",500,()->true,(m,s,p)->{});
            network[0]=MdoBrowserCdpSession.open("test-001",500,()->true,(m,s,p)->receipts.add(network[0].continuePausedRequest()));
            try(MdoBrowserCdpSession.Cooperation scope=MdoBrowserCdpSession.cooperate(()->{
                try{if(network[0].idle())network[0].poll(20,250);}catch(IOException error){throw new IllegalStateException(error);}
            })){
                page.call("Runtime.evaluate",null,null,1000,true);
            }
            check(receipts.size()==1);
            if(receipts.get(0).pending())check(network[0].poll(20,250));
            settled(receipts.get(0),true,null,true);
        }finally{if(page!=null)page.close();if(network[0]!=null)network[0].close();check(CdpNativeProbe.fixtureFinish()==(1+(2<<8)));}
    }
    public static void main(String[] args)throws Exception{
        Looper.getMainLooper();System.loadLibrary("xs");ExecutorService worker=Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{successAndOrdering();invalidAndNegative();cancellationAndClocks();cooperation();limits();if(args.length==0){realWire();realCooperation();}}
            catch(Exception problem){throw new RuntimeException(problem);}}).get(45,TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(5,TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_control_sessions\":"+realSessions+",\"real_cooperative_pairs\":"+(args.length==0?1:0)+",\"real_jvm_jni_json\":true,\"provider_qualified\":false,\"downloads_activated\":false}");
    }
}
