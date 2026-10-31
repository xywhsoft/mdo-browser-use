package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Fixed original-event release, not task ownership or installed popup support. */
public final class CdpAttachedTargetProbe {
    private static int checks,realSessions;
    private interface Work {void run()throws Exception;}
    private interface Send {void sent(JSONObject command)throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Attached release check "+(checks+1));checks++;}
    private static void failure(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected "+code);}catch(IOException error){String actual=error.getMessage();
            if(!code.equals(actual))throw new AssertionError("Expected "+code+", received "+(actual!=null&&actual.matches("cdp_[a-z_]+")?actual:"non-static error"));check(true);check(error.getCause()==null);}
    }
    private static JSONObject attached(String child)throws Exception{
        return new JSONObject().put("method","Target.attachedToTarget").put("params",new JSONObject().put("sessionId",child)
            .put("targetInfo",new JSONObject().put("targetId","target-"+child).put("type","page").put("openerId","foreign-opener").put("url","https://private.invalid/do-not-read"))
            .put("waitingForDebugger",true));
    }
    private static JSONObject ack(int id,String session)throws Exception{
        JSONObject value=new JSONObject().put("id",id).put("result",new JSONObject());if(session!=null)value.put("sessionId",session);return value;
    }
    private static final class Wire implements MdoBrowserCdpSession.Wire,MdoBrowserCdpSession.Clock {
        final ArrayDeque<byte[]> queued=new ArrayDeque<>();final List<byte[]> buffers=new ArrayList<>();final List<JSONObject> sent=new ArrayList<>();
        long now;int closes;Send after;
        void add(JSONObject packet){queued.add(packet.toString().getBytes(StandardCharsets.UTF_8));}
        public long now(){return now;}
        public void send(byte[] bytes,int timeout)throws IOException{
            buffers.add(bytes);try{JSONObject value=new JSONObject(new String(bytes,StandardCharsets.UTF_8));sent.add(value);if(after!=null)after.sent(value);}
            catch(IOException error){throw error;}catch(RuntimeException|Error error){throw error;}catch(Exception error){throw new IOException("fixture_send_failed");}
        }
        public byte[] receive(int timeout){byte[] bytes=queued.poll();if(bytes!=null)buffers.add(bytes);else now+=timeout;return bytes;}
        public void close(){closes++;}
        void cleared(){for(byte[] bytes:buffers){boolean zero=true;for(byte b:bytes)if(b!=0)zero=false;check(zero);}}
    }
    private static final class Case implements AutoCloseable {
        final Wire wire=new Wire();final AtomicBoolean alive=new AtomicBoolean(true);
        final List<MdoBrowserCdpSession.ControlReceipt> receipts=new ArrayList<>();final List<MdoBrowserCdpSession.AttachedTarget> metadata=new ArrayList<>();
        MdoBrowserCdpSession client;MdoBrowserCdpSession.Events action;int lateEvents;
        Case(){this(true);}
        Case(boolean browser){MdoBrowserCdpSession.Events events=(m,s,p)->{if(action!=null)action.event(m,s,p);else if(m.equals("Target.attachedToTarget")){
            MdoBrowserCdpSession.AttachedTarget original=client.attachedTarget();metadata.add(original);
            check(original.waiting&&original.type.equals("page")&&original.opener.equals("foreign-opener"));
            try{p.put("sessionId","forged");p.put("waitingForDebugger",false);p.getJSONObject("targetInfo").put("targetId","forged");}catch(Exception error){throw new IOException("fixture_mutation_failed");}
            receipts.add(client.resumeAttachedTarget());
        }else if(m.equals("Runtime.executionContextCreated"))lateEvents++;};
            client=browser?MdoBrowserCdpSession.ownedBrowser(wire,alive::get,events,wire):MdoBrowserCdpSession.owned(wire,alive::get,events,wire);
        }
        void call()throws IOException{client.call("Target.getTargets",null,null,100,false);}
        void drain()throws IOException{for(int i=0;i<32&&client.pendingControls()>0;i++)client.poll(10,100);check(client.pendingControls()==0);}
        public void close(){client.close();check(wire.closes==1);wire.cleared();}
    }
    private static void automatic(Case test,boolean earlyOuter)throws Exception{
        test.wire.after=command->{String method=command.getString("method");int id=command.getInt("id");
            if(method.equals("Target.getTargets")){test.wire.add(attached("popup-1"));if(earlyOuter)test.wire.add(ack(id,null));}
            else if(method.equals("Runtime.runIfWaitingForDebugger")){
                check(command.getString("sessionId").equals("popup-1")&&command.getJSONObject("params").length()==0&&command.length()==4);
                test.wire.add(ack(id,"popup-1"));
            }else if(method.equals("Target.detachFromTarget")){
                check(!command.has("sessionId")&&command.getJSONObject("params").length()==1&&command.getJSONObject("params").getString("sessionId").equals("popup-1"));
                test.wire.add(new JSONObject().put("method","Target.detachedFromTarget").put("params",new JSONObject().put("sessionId","popup-1")));
                test.wire.add(ack(id,null));
                test.wire.add(new JSONObject().put("method","Runtime.executionContextCreated").put("sessionId","popup-1").put("params",new JSONObject()));
                if(!earlyOuter)test.wire.add(ack(1,null));
            }else throw new AssertionError("Non-fixed command");
        };
    }
    private static void success()throws Exception{
        for(boolean early:new boolean[]{false,true})try(Case test=new Case()){
            automatic(test,early);test.call();test.drain();
            check(test.receipts.size()==1&&test.metadata.size()==1&&test.wire.sent.size()==3);
            MdoBrowserCdpSession.ControlReceipt receipt=test.receipts.get(0);check(receipt.result()!=null&&receipt.result().ok&&receipt.effectMayHaveOccurred());
            check(test.metadata.get(0).target.equals("target-popup-1")&&test.metadata.get(0).resumeConfirmed()&&test.metadata.get(0).detachConfirmed());
            test.client.poll(10,100);check(test.lateEvents==0);
            failure("cdp_control_not_in_event",()->test.client.resumeAttachedTarget());
        }
    }
    private static void refusedEvents()throws Exception{
        for(int mode=0;mode<9;mode++)try(Case test=new Case(mode!=0)){
            JSONObject event=attached("popup-1"),params=event.getJSONObject("params");String code="cdp_attach_event_invalid";
            if(mode==0)code="cdp_attach_browser_required";
            if(mode==1){params.put("waitingForDebugger",false);code="cdp_attach_not_waiting";}
            if(mode==2)params.put("sessionId","a/b");if(mode==3)params.remove("targetInfo");
            if(mode==4)params.put("waitingForDebugger","true");if(mode==5)params.getJSONObject("targetInfo").put("targetId",4);
            if(mode==6)params.getJSONObject("targetInfo").put("openerId",JSONObject.NULL);
            if(mode==7){test.client.admitChild("parent-1");event.put("sessionId","parent-1");}
            if(mode==8){event=new JSONObject().put("method","Runtime.consoleAPICalled").put("params",new JSONObject());code="cdp_event_failed";}
            test.action=(m,s,p)->{test.client.resumeAttachedTarget();};test.wire.add(event);failure(code,()->test.call());check(test.wire.sent.size()==1);
        }
        try(Case test=new Case()){
            automatic(test,false);test.action=(m,s,p)->{if(m.equals("Target.attachedToTarget")){test.receipts.add(test.client.resumeAttachedTarget());try{failure("cdp_attach_duplicate",()->test.client.resumeAttachedTarget());}catch(IOException error){throw error;}catch(Exception error){throw new IOException("fixture_duplicate_failed");}}};
            test.call();test.drain();check(test.wire.sent.size()==3);
            test.wire.add(attached("popup-1"));failure("cdp_attach_duplicate",()->test.client.poll(10,100));check(test.wire.sent.size()==3);
        }
    }
    private static void faults()throws Exception{
        for(int mode=0;mode<9;mode++)try(Case test=new Case()){
            final int chosen=mode;
            test.wire.after=command->{String method=command.getString("method");int id=command.getInt("id");
                if(method.equals("Target.getTargets"))test.wire.add(attached("popup-1"));
                else if(method.equals("Runtime.runIfWaitingForDebugger")){
                    if(chosen==0)throw new IOException("private-send-detail");
                    JSONObject reply=ack(id,chosen==1?null:"popup-1");
                    if(chosen==2)reply.getJSONObject("result").put("extra",true);
                    if(chosen==3){reply.remove("result");reply.put("error",new JSONObject().put("code",-32601).put("message","private-provider-detail"));}
                    test.wire.add(reply);
                }else if(method.equals("Target.detachFromTarget")){
                    if(chosen==4)throw new IOException("private-detach-detail");
                    JSONObject reply=ack(id,chosen==5?"popup-1":null);
                    if(chosen==6)reply.getJSONObject("result").put("extra",true);
                    if(chosen==7||chosen==8){reply.remove("result");reply.put("error",new JSONObject().put("code",chosen==8?-32601:-32602).put("message","private-provider-detail"));}
                    test.wire.add(reply);
                }
            };
            String expected=mode==0||mode==4?"cdp_control_send_failed":mode==1||mode==5?"cdp_control_reply_mismatch":mode==2||mode==6?"cdp_control_reply_invalid":mode==3||mode==8?"cdp_control_method_unsupported":"cdp_control_rejected";
            failure(expected,()->test.call());check(test.receipts.size()==1&&test.receipts.get(0).result()!=null&&!test.receipts.get(0).result().ok);
            check(test.receipts.get(0).effectMayHaveOccurred()==(mode!=3));
            check(test.metadata.get(0).resumeConfirmed()==(mode>=4)&&!test.metadata.get(0).detachConfirmed());
            check(test.wire.sent.size()==(mode<4?2:3)&&test.client.pendingControls()==0);
        }
    }
    private static void cancellationAndLimits()throws Exception{
        for(int mode=0;mode<4;mode++)try(Case test=new Case()){
            final int chosen=mode;
            test.wire.after=command->{String method=command.getString("method");int id=command.getInt("id");
                if(method.equals("Target.getTargets"))test.wire.add(attached("popup-1"));
                else if(method.equals("Runtime.runIfWaitingForDebugger")){
                    if(chosen==1)test.wire.add(ack(id,"popup-1"));test.wire.add(ack(1,null));
                }
            };
            test.call();if(mode==1)test.client.poll(10,100);
            if(mode<2){test.wire.now+=5001;failure("cdp_control_timeout",()->test.client.poll(10,100));}
            else if(mode==2){test.alive.set(false);failure("cdp_owner_changed",()->test.client.poll(10,100));}
            else{Thread closer=new Thread(test.client::close);closer.start();closer.join(1000);check(!closer.isAlive());}
            check(test.receipts.get(0).result()!=null&&!test.receipts.get(0).result().ok&&test.receipts.get(0).effectMayHaveOccurred());
            check(test.metadata.get(0).resumeConfirmed()==(mode==1)&&!test.metadata.get(0).detachConfirmed()&&test.client.pendingControls()==0);
        }
        try(Case test=new Case()){
            for(int i=0;i<64;i++){test.wire.add(attached("p-"+i));test.client.poll(10,100);}
            check(test.receipts.size()==64&&test.client.pendingControls()==64);
            test.wire.add(attached("over-limit"));failure("cdp_attach_limit",()->test.client.poll(10,100));
            check(test.wire.sent.size()==64);for(MdoBrowserCdpSession.ControlReceipt receipt:test.receipts)check(receipt.result()!=null&&!receipt.result().ok);
        }
        try(Case test=new Case()){
            test.wire.after=command->{String method=command.getString("method");int id=command.getInt("id");
                if(method.equals("Runtime.runIfWaitingForDebugger"))test.wire.add(ack(id,command.getString("sessionId")));
                else if(method.equals("Target.detachFromTarget"))test.wire.add(ack(id,null));else throw new AssertionError("Unexpected lifetime command");
            };
            for(int i=0;i<256;i++){test.wire.add(attached("life-"+i));test.client.poll(10,100);test.drain();check(test.receipts.get(i).result().ok);}
            test.wire.add(attached("life-over-limit"));failure("cdp_attach_limit",()->test.client.poll(10,100));check(test.wire.sent.size()==512);
        }
    }
    private static void nativeSessions()throws Exception{
        for(int mode:new int[]{54,55}){
            CdpNativeProbe.fixtureStart(mode);MdoBrowserCdpSession[] session={null};MdoBrowserCdpSession.ControlReceipt[] receipt={null};int[] late={0};
            byte[] endpoint="{\"webSocketDebuggerUrl\":\"ws://localhost/devtools/browser\"}".getBytes(StandardCharsets.US_ASCII);
            try{
                session[0]=MdoBrowserCdpSession.openBrowser(MdoBrowserCdpNative.browserEndpointFromJson(endpoint),1000,()->true,(m,s,p)->{
                    if(m.equals("Target.attachedToTarget")){try{p.put("sessionId","forged");}catch(Exception error){throw new IOException("fixture_mutation_failed");}receipt[0]=session[0].resumeAttachedTarget();}
                    else if(m.equals("Runtime.executionContextCreated"))late[0]++;
                });
                session[0].call("Target.getTargets",null,null,1000,false);
                for(int i=0;i<20&&(receipt[0]==null||receipt[0].pending());i++)session[0].poll(20,1000);
                check(receipt[0]!=null&&receipt[0].result()!=null&&receipt[0].result().ok&&session[0].pendingControls()==0);
                session[0].poll(20,1000);check(late[0]==0);
            }finally{Arrays.fill(endpoint,(byte)0);if(session[0]!=null)session[0].close();}
            check(CdpNativeProbe.fixtureFinish()==(1+(3<<8)));realSessions++;
        }
    }
    public static void main(String[] ignored)throws Exception{
        Looper.getMainLooper();java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{success();refusedEvents();faults();cancellationAndLimits();nativeSessions();return null;}catch(Exception error){throw new RuntimeException(error);}}).get(40,TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_native_sessions\":"+realSessions+",\"fixed_original_release\":true,\"native_resume_then_detach\":true,\"task_or_popup_ownership_proved\":false,\"provider_qualified\":false}");
    }
}
