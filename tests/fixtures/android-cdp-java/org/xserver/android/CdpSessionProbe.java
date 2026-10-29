package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CdpSessionProbe {
    private static int checks;
    private interface Operation {void run() throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Protocol gate check "+(checks+1));checks++;}
    private static MdoBrowserCdpSession.Failure failure(String code,boolean effect,Operation operation) throws Exception {
        try {operation.run();throw new AssertionError("Missing protocol failure: "+code);}
        catch(MdoBrowserCdpSession.Failure problem){check(code.equals(problem.getMessage()));check(problem.effectMayHaveOccurred==effect);check(problem.getCause()==null);return problem;}
    }
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static void codecRejected(String text) throws Exception {
        byte[] data=bytes(text);
        try {MdoBrowserCdpJson.decode(data);throw new AssertionError("Accepted invalid envelope");}
        catch(IOException expected){check(expected.getMessage().startsWith("cdp_"));check(expected.getCause()==null);}
        finally {Arrays.fill(data,(byte)0);}
    }
    private static final class Wire implements MdoBrowserCdpSession.Wire,MdoBrowserCdpSession.Clock {
        final ArrayDeque<byte[]> packets=new ArrayDeque<>();final List<byte[]> delivered=new ArrayList<>();
        int sends,closes;byte[] sent;long clock;boolean failSend,failRead,failClock;Runnable afterRead;
        Wire(String... messages){for(String message:messages)packets.add(bytes(message));}
        public long now(){if(failClock)throw new OutOfMemoryError("private-allocation-detail");return clock;}
        public void send(byte[] data,int timeout) throws IOException {sends++;sent=data;if(failSend)throw new IOException("private-wire-detail");}
        public byte[] receive(int timeout) throws IOException {
            if(failRead)throw new IOException("private-wire-detail");
            byte[] next=packets.pollFirst();if(next==null)clock+=timeout;else delivered.add(next);
            if(afterRead!=null)afterRead.run();return next;
        }
        public void close(){closes++;}
        private void zero(byte[] data){boolean clear=true;for(byte b:data)if(b!=0)clear=false;check(clear);}
        void cleared(){if(sent!=null)zero(sent);for(byte[] message:delivered)zero(message);}
    }
    private static MdoBrowserCdpSession session(Wire wire,AtomicBoolean alive,MdoBrowserCdpSession.Events events) {
        return MdoBrowserCdpSession.owned(wire,alive::get,events,wire);
    }
    private static final MdoBrowserCdpSession.Events IGNORE=(method,session,params)->{};
    private static void codec() throws Exception {
        for(String text:new String[]{"", "[]", "null", "{}", "{id:1,result:{}}", "{'id':1,'result':{}}", "/*x*/{\"id\":1,\"result\":{}}",
            "{\"id\":1,\"result\":{},}", "{\"id\":1,\"id\":2,\"result\":{}}", "{\"id\":1,\"result\":{\"x\":1,\"x\":2}}",
            "{\"id\":1.0,\"result\":{}}", "{\"id\":0,\"result\":{}}", "{\"id\":2147483648,\"result\":{}}",
            "{\"id\":1,\"result\":null}", "{\"id\":1,\"result\":{},\"error\":{}}", "{\"id\":1,\"result\":{},\"extra\":1}",
            "{\"method\":\"Runtime.enable\",\"id\":1}", "{\"method\":\"Runtime.enable\",\"params\":[]}", "{\"method\":\"Runtime.enable\",\"params\":null}",
            "{\"method\":\"Runtime\"}", "{\"method\":\"Runtime.bad.name\"}", "{\"method\":\"1Runtime.bad\"}",
            "{\"method\":\"Runtime.event\",\"sessionId\":\"\"}", "{\"method\":\"Runtime.event\",\"sessionId\":null}",
            "{\"id\":1,\"error\":{\"code\":\"-32601\",\"message\":\"x\"}}", "{\"id\":1,\"error\":{\"code\":-32601,\"message\":1}}",
            "{\"id\":1,\"result\":{\"n\":NaN}}", "{\"id\":1,\"result\":{}} true", "\ufeff{\"id\":1,\"result\":{}}"})codecRejected(text);
        String deep="0";for(int i=0;i<65;i++)deep="["+deep+"]";codecRejected("{\"id\":1,\"result\":{\"deep\":"+deep+"}}");
        byte[] data=bytes("{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"id\":7}}}");
        MdoBrowserCdpJson.Message event=MdoBrowserCdpJson.decode(data);check(event.event()&&event.id==0&&event.value.getJSONObject("context").getInt("id")==7);Arrays.fill(data,(byte)0);
        data=MdoBrowserCdpJson.encode(1,"Runtime.evaluate",new JSONObject().put("expression","x='\u4f60'"),null);
        JSONObject request=new JSONObject(new String(data,StandardCharsets.UTF_8));check(request.getInt("id")==1&&request.getJSONObject("params").getString("expression").equals("x='\u4f60'"));Arrays.fill(data,(byte)0);
        JSONObject cyclic=new JSONObject();cyclic.put("self",cyclic);
        Object hostile=new Object(){@Override public String toString(){throw new AssertionError("Untrusted toString was invoked");}};
        for(JSONObject invalid:new JSONObject[]{cyclic,new JSONObject().put("unknown",hostile),new JSONObject().put("big",new String(new char[262145]).replace('\0','x'))}) {
            try {MdoBrowserCdpJson.encode(1,"Runtime.enable",invalid,null);throw new AssertionError("Unbounded arguments accepted");}
            catch(IOException expected){check(expected.getMessage().equals("cdp_arguments_invalid"));}
        }
    }
    private static void deterministic() throws Exception {
        AtomicBoolean alive=new AtomicBoolean(true);
        Wire wire=new Wire("{\"method\":\"Runtime.event\",\"params\":{}}","{\"id\":1,\"result\":{\"ok\":true}}");
        final MdoBrowserCdpSession[] holder=new MdoBrowserCdpSession[1];final int[] events={0};
        holder[0]=session(wire,alive,(method,child,params)->{
            events[0]++;
            try {holder[0].call("Runtime.enable",null,null,100,false);throw new AssertionError("Reentrant RPC accepted");}
            catch(MdoBrowserCdpSession.Failure expected){check(expected.getMessage().equals("cdp_session_busy"));}
        });
        check(holder[0].call("Runtime.enable",null,null,100,false).getBoolean("ok"));check(events[0]==1&&wire.sends==1);wire.cleared();holder[0].close();
        wire=new Wire();final Wire timed=wire;MdoBrowserCdpSession client=session(wire,alive,IGNORE);
        final MdoBrowserCdpSession timeout=client;failure("cdp_command_timeout",true,()->timeout.call("Runtime.evaluate",null,null,50,true));
        check(timed.sends==1&&timed.closes>0&&timed.clock<=50);timed.cleared();
        failure("cdp_session_closed",false,()->timeout.call("Runtime.evaluate",null,null,100,true));check(timed.sends==1);
        wire=new Wire("{\"id\":9,\"result\":{}}");final MdoBrowserCdpSession mismatch=session(wire,alive,IGNORE);
        failure("cdp_reply_mismatch",true,()->mismatch.call("Runtime.evaluate",null,null,100,true));check(wire.sends==1&&wire.closes>0);wire.cleared();
        wire=new Wire("{\"method\":\"Runtime.event\",\"sessionId\":\"foreign\",\"params\":{}}");final MdoBrowserCdpSession foreign=session(wire,alive,IGNORE);
        failure("cdp_session_mismatch",true,()->foreign.call("Runtime.evaluate",null,null,100,true));wire.cleared();
        wire=new Wire("{\"id\":1,\"result\":{}}");wire.afterRead=()->alive.set(false);final MdoBrowserCdpSession changed=session(wire,alive,IGNORE);
        failure("cdp_owner_changed",true,()->changed.call("Runtime.evaluate",null,null,100,true));wire.cleared();check(wire.closes>0);alive.set(true);
        wire=new Wire("{\"method\":\"Runtime.event\",\"params\":{}}");wire.afterRead=()->alive.set(false);final MdoBrowserCdpSession pollChanged=session(wire,alive,IGNORE);
        failure("cdp_owner_changed",false,()->pollChanged.poll(100));wire.cleared();alive.set(true);
        wire=new Wire();wire.failSend=true;final MdoBrowserCdpSession failedSend=session(wire,alive,IGNORE);
        failure("cdp_transport_failed",true,()->failedSend.call("Runtime.evaluate",null,null,100,true));check(wire.sends==1&&wire.closes>0);wire.cleared();
        wire=new Wire();wire.failRead=true;final MdoBrowserCdpSession failedRead=session(wire,alive,IGNORE);
        failure("cdp_transport_failed",false,()->failedRead.call("Runtime.enable",null,null,100,false));check(wire.sends==1&&wire.closes>0);wire.cleared();
        wire=new Wire("{\"method\":\"Runtime.event\",\"params\":{}}");final MdoBrowserCdpSession callback=session(wire,alive,(m,s,p)->{throw new IOException("private-event-detail");});
        failure("cdp_event_failed",true,()->callback.call("Runtime.evaluate",null,null,100,true));wire.cleared();
        wire=new Wire("{\"method\":\"Page.fileChooserOpened\",\"params\":{}}");final MdoBrowserCdpSession typedEvent=session(wire,alive,(m,s,p)->{throw new MdoBrowserCdpSession.EventFailure("cdp_chooser_ambiguous");});
        failure("cdp_chooser_ambiguous",false,()->typedEvent.call("Page.getFrameTree",null,null,100,false));wire.cleared();check(wire.closes>0);
        try{new MdoBrowserCdpSession.EventFailure("cdp_chooser_private_provider_detail");throw new AssertionError("Unreviewed event code escaped");}
        catch(IllegalArgumentException expected){check(expected.getMessage().equals("unsupported_native_event_code"));}
        wire=new Wire("{\"id\":1,\"result\":{}}");final MdoBrowserCdpSession arguments=session(wire,alive,IGNORE);
        failure("cdp_arguments_invalid",false,()->arguments.call("bad",null,null,100,true));check(wire.sends==0&&wire.closes==0);
        // Unsent rejected requests still consume an ID; it can never be reused.
        wire.packets.clear();wire.packets.add(bytes("{\"id\":2,\"result\":{}}"));arguments.call("Runtime.enable",null,null,100,false);check(wire.sends==1);arguments.close();
        wire=new Wire();final MdoBrowserCdpSession admission=session(wire,alive,IGNORE);
        failure("cdp_session_invalid",false,()->admission.call("Runtime.enable",null,"unowned",100,false));check(wire.sends==0&&wire.closes==0);
        for(int i=0;i<64;i++)admission.admitChild("child-"+i);
        failure("cdp_session_invalid",false,()->admission.admitChild("child-64"));
        failure("cdp_session_invalid",false,()->admission.admitChild("child-0"));
        admission.retireChild("child-0");failure("cdp_session_invalid",false,()->admission.admitChild("child-0"));
        admission.admitChild("replacement");admission.close();
        wire=new Wire("{\"method\":\"Runtime.event\",\"sessionId\":\"retired\",\"params\":{}}","{\"id\":1,\"result\":{}}");
        final int[] lateEvents={0};MdoBrowserCdpSession retired=session(wire,alive,(m,s,p)->lateEvents[0]++);
        retired.admitChild("retired");retired.retireChild("retired");retired.call("Runtime.enable",null,null,100,false);
        check(lateEvents[0]==0&&wire.closes==0);wire.cleared();retired.close();
        wire=new Wire("{\"method\":\"Target.detachedFromTarget\",\"params\":{}}");final MdoBrowserCdpSession[] detachedHolder={null};
        detachedHolder[0]=session(wire,alive,(m,s,p)->detachedHolder[0].retireChild("retired"));detachedHolder[0].admitChild("retired");
        failure("cdp_session_detached",true,()->detachedHolder[0].call("Runtime.evaluate",null,"retired",100,true));wire.cleared();
        wire=new Wire("{\"id\":1,\"result\":{}}");final MdoBrowserCdpSession unsolicited=session(wire,alive,IGNORE);
        failure("cdp_reply_mismatch",false,()->unsolicited.poll(100));wire.cleared();
        wire=new Wire("{\"id\":1,\"result\":{}}");final Wire failedClock=wire;wire.afterRead=()->failedClock.failClock=true;
        final MdoBrowserCdpSession oom=session(wire,alive,IGNORE);failure("cdp_dispatch_failed",true,()->oom.call("Runtime.evaluate",null,null,100,true));check(wire.closes>0);wire.cleared();
        wire=new Wire();wire.clock=100;final Wire backwards=wire;wire.afterRead=()->backwards.clock=0;
        final MdoBrowserCdpSession clock=session(wire,alive,IGNORE);failure("cdp_command_timeout",true,()->clock.call("Runtime.evaluate",null,null,100,true));check(wire.sends==1);
        wire=new Wire();for(int i=0;i<4097;i++)wire.packets.add(bytes("{\"method\":\"Runtime.event\",\"params\":{}}"));
        final MdoBrowserCdpSession flood=session(wire,alive,IGNORE);failure("cdp_event_limit",true,()->flood.call("Runtime.evaluate",null,null,5000,true));check(wire.sends==1);wire.cleared();
    }
    private static void nativeSession(int mode) throws Exception {
        CdpNativeProbe.fixtureStart(mode);final AtomicBoolean alive=new AtomicBoolean(true);final int[] events={0};
        try(MdoBrowserCdpSession session=MdoBrowserCdpSession.open("test-001",1000,alive::get,(method,child,params)->{
            check(method.equals("Runtime.executionContextCreated"));events[0]++;
        })) {
            if(mode==20) {
                session.call("Runtime.enable",new JSONObject(),null,1000,false);
                JSONObject result=session.call("Runtime.evaluate",new JSONObject(),null,1000,true);
                check(result.getJSONObject("result").getInt("value")==2&&events[0]==1);
            } else if(mode==22) {
                check(failure("cdp_method_unsupported",false,()->session.call("Runtime.evaluate",new JSONObject(),null,1000,true)).protocolCode==-32601);
                session.call("Runtime.enable",new JSONObject(),null,1000,false);
                check(failure("cdp_command_rejected",true,()->session.call("Runtime.evaluate",new JSONObject(),null,1000,true)).protocolCode==0);
                session.call("Runtime.enable",new JSONObject(),null,1000,false);
            } else if(mode==24) {
                session.admitChild("child-001");session.call("Runtime.enable",new JSONObject(),"child-001",1000,false);check(events[0]==1);
            } else {
                String code=mode==21?"cdp_reply_mismatch":mode==23?"cdp_session_mismatch":mode==26?"cdp_command_timeout":"cdp_transport_failed";
                failure(code,true,()->session.call("Runtime.evaluate",new JSONObject(),null,mode==26?50:1000,true));
                failure("cdp_session_closed",false,()->session.call("Runtime.evaluate",new JSONObject(),null,100,true));
            }
        }
        int frames=mode==20?2:mode==22?4:1;check(CdpNativeProbe.fixtureFinish()==(1+(frames<<8)));
    }
    public static void main(String[] ignored) throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");ExecutorService worker=Executors.newSingleThreadExecutor();
        try {worker.submit(()->{try {
            codec();deterministic();for(int mode=20;mode<=26;mode++)nativeSession(mode);
        }catch(Exception problem){throw new RuntimeException(problem);}}).get(25,TimeUnit.SECONDS);}
        finally {worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_jvm_jni\":true,\"real_protocol_sessions\":7,\"android_provider_qualified\":false}");
    }
}
