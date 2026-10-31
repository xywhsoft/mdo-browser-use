package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Actual binder/marker/dispatcher/JNI; controlled renderer and UI metadata.
 * A separate JVM for each poisoned-process case mirrors the real fence, not an
 * application/Home/provider copy or an unsafe test-only reset of that fence. */
public final class CdpPopupBrokerProbe {
    private static ExecutorService worker;private static int checks,realSessions;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Popup broker check "+(checks+1));checks++;}
    private static void failure(String code,Work action)throws Exception{
        try{action.run();throw new AssertionError("Missing "+code);}catch(IOException error){String actual=error.getMessage();
            if(!code.equals(actual))throw new AssertionError("Expected "+code+", received "+(actual!=null&&actual.matches("cdp_[a-z_]+")?actual:"non-static error"));check(error.getCause()==null);}
    }
    private static <T>T on(Callable<T> work)throws Exception{
        java.util.concurrent.Future<T> future=worker.submit(work);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!future.isDone()&&System.nanoTime()<end){Handler.drain();Thread.sleep(1);}
        try{return future.get(2,TimeUnit.SECONDS);}catch(ExecutionException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw error;}
    }
    private static final class Source implements AutoCloseable {
        final CdpNetworkBindingProbe.Context context=new CdpNetworkBindingProbe.Context(true);
        final MdoBrowserCdpBinding.Network network;
        Source(String target,boolean enabled)throws Exception{
            network=on(()->MdoBrowserCdpBinding.bindNetwork(context.adapter.marker(),1500,(m,s,p)->{},t->Arrays.asList(target),
                (id,timeout,current,events)->context.peer.open("owned",timeout,current,events)));
            context.network=network;if(enabled)on(()->{network.enableResponses(1000);return null;});
        }
        public void close(){context.close();}
    }
    private static JSONObject attached(String session,String target,String opener,boolean waiting)throws Exception{
        JSONObject info=new JSONObject().put("targetId",target).put("type","page").put("url","https://private.invalid/not-requested");if(opener!=null)info.put("openerId",opener);
        return new JSONObject().put("method","Target.attachedToTarget").put("params",new JSONObject().put("sessionId",session).put("targetInfo",info).put("waitingForDebugger",waiting));
    }
    private static JSONObject ack(int id,String session)throws Exception{JSONObject result=new JSONObject().put("id",id).put("result",new JSONObject());if(session!=null)result.put("sessionId",session);return result;}
    private static final class Wire implements MdoBrowserCdpSession.Wire,MdoBrowserCdpSession.Clock {
        final ArrayDeque<byte[]> incoming=new ArrayDeque<>();final List<byte[]> buffers=new ArrayList<>();final List<JSONObject> sent=new ArrayList<>();
        long time=100;int opens,closes;boolean unsupported,rejectDisable,lateDisable=true,existing=true,throwArm;
        void add(JSONObject value){incoming.add(value.toString().getBytes(StandardCharsets.UTF_8));}
        public long now(){return time;}
        MdoBrowserCdpSession open(int timeout,java.util.function.BooleanSupplier current,MdoBrowserCdpSession.Events events){opens++;return MdoBrowserCdpSession.ownedBrowser(this,current,events,this);}
        public void send(byte[] bytes,int timeout)throws IOException{
            buffers.add(bytes);try{
                JSONObject command=new JSONObject(new String(bytes,StandardCharsets.UTF_8));sent.add(command);String method=command.getString("method");int id=command.getInt("id");JSONObject params=command.getJSONObject("params");
                if(method.equals("Target.setAutoAttach")){
                    check(!command.has("sessionId")&&params.getBoolean("flatten"));boolean enable=params.getBoolean("autoAttach");
                    check(params.getBoolean("waitForDebuggerOnStart")==enable);
                    if(enable){check(params.length()==4&&params.getJSONArray("filter").length()==2);check(params.getJSONArray("filter").getJSONObject(0).getString("type").equals("page"));
                        if(throwArm)throw new IOException("private-partial-write");
                        if(unsupported){add(new JSONObject().put("id",id).put("error",new JSONObject().put("code",-32601).put("message","private-provider-detail")));return;}
                        if(existing)add(attached("existing-1","existing-ui",null,false));
                    }else{check(params.length()==3);if(rejectDisable){add(new JSONObject().put("id",id).put("error",new JSONObject().put("code",-32602).put("message","private-provider-detail")));return;}
                        if(lateDisable){add(attached("late-1","late-popup","registered-root",true));add(new JSONObject().put("method","Runtime.executionContextCreated").put("sessionId","late-1").put("params",new JSONObject()));}
                    }add(ack(id,null));
                }else if(method.equals("Runtime.runIfWaitingForDebugger")){check(params.length()==0);add(ack(id,command.getString("sessionId")));}
                else if(method.equals("Target.detachFromTarget")){check(!command.has("sessionId")&&params.length()==1);add(ack(id,null));}
                else throw new AssertionError("Unapproved broker command");
            }catch(IOException error){throw error;}catch(RuntimeException|Error error){throw error;}catch(Exception error){throw new IOException("fixture_send_failed");}
        }
        public byte[] receive(int timeout){byte[] bytes=incoming.poll();if(bytes==null)time+=timeout;else buffers.add(bytes);return bytes;}
        public void close(){closes++;}
        void cleared(){for(byte[] bytes:buffers){boolean zero=true;for(byte b:bytes)if(b!=0)zero=false;check(zero);}check(opens==closes);}
    }
    private static MdoBrowserCdpPopupBroker broker(Wire wire){return new MdoBrowserCdpPopupBroker(()->true,wire::now,wire::open);}
    private static void turns(MdoBrowserCdpPopupBroker broker,int count)throws Exception{for(int i=0;i<count;i++)broker.turn(10,250);}
    private static void suite(Source source)throws Exception{
        Wire wire=new Wire();MdoBrowserCdpPopupBroker first=broker(wire);first.register(source.network);
        failure("cdp_popup_opener_busy",()->first.register(source.network));failure("cdp_arguments_invalid",()->first.arm(0));check(wire.opens==0);
        first.arm(1000);check(first.armed()&&!first.cleanupConfirmed()&&first.enabledMayHaveOccurred());
        check(wire.sent.size()==2&&wire.sent.get(1).getString("method").equals("Target.detachFromTarget"));
        Wire other=new Wire();MdoBrowserCdpPopupBroker second=broker(other);second.register(source.network);
        failure("cdp_popup_broker_process_busy",()->second.arm(1000));check(other.opens==0);second.close();
        wire.add(attached("free-1","foreign-popup","foreign-root",true));turns(first,3);check(first.pending().isEmpty());check(wire.sent.size()==4);
        wire.add(attached("managed-1","new-managed","registered-root",true));turns(first,1);
        List<MdoBrowserCdpPopupBroker.Candidate> pending=first.pending();check(pending.size()==1&&pending.get(0).current()&&!pending.get(0).nativeChildProven());
        int before=wire.sent.size();failure("cdp_popup_broker_candidates_pending",()->first.disarm(1000));check(wire.sent.size()==before&&first.armed());
        wire.add(new JSONObject().put("method","Target.detachedFromTarget").put("params",new JSONObject().put("sessionId","managed-1")));turns(first,1);
        check(first.pending().isEmpty()&&!pending.get(0).current());first.disarm(1000);check(first.cleanupConfirmed()&&!first.armed()&&wire.sent.size()==before+1);first.close();wire.cleared();
        // A confirmed disable releases the actual process claim; no test reset.
        Wire later=new Wire();MdoBrowserCdpPopupBroker clean=broker(later);clean.register(source.network);clean.arm(1000);clean.disarm(1000);later.cleared();
        Wire old=new Wire();old.unsupported=true;MdoBrowserCdpPopupBroker unsupported=broker(old);unsupported.register(source.network);
        failure("cdp_method_unsupported",()->unsupported.arm(1000));check(unsupported.cleanupConfirmed()&&!unsupported.enabledMayHaveOccurred());old.cleared();
    }
    private static void nativeBroker(Source source)throws Exception{
        CdpNativeProbe.fixtureStart(56);MdoBrowserCdpPopupBroker original=new MdoBrowserCdpPopupBroker(()->true);
        try{original.register(source.network);original.arm(2000);check(original.armed());turns(original,3);original.disarm(2000);check(original.cleanupConfirmed());}
        finally{original.close();}
        check(CdpNativeProbe.fixtureFinish()==(1+(5<<8)));realSessions++;
    }
    private static void nativeProtection(Source source)throws Exception{
        CdpNativeProbe.fixtureStart(57);MdoBrowserCdpPopupBroker original=new MdoBrowserCdpPopupBroker(()->true);
        try{
            original.register(source.network);original.arm(2000);check(original.pending().size()==1);
            MdoBrowserCdpPopupBroker.Candidate candidate=original.pending().get(0);check(candidate.requestProtection()&&!candidate.requestProtection());
            failure("cdp_arguments_invalid",()->original.protect(candidate,9));
            original.protect(candidate,2000);check(candidate.protection().ok&&candidate.protection().effectMayHaveOccurred&&!candidate.nativeChildProven()&&candidate.retainedRequestCount()==1);
            failure("cdp_popup_protection_replayed",()->original.protect(candidate,500));
            original.turn(10,1000);check(candidate.retainedRequestCount()==2);original.turn(10,1000);
            check(!candidate.current()&&candidate.retainedRequestCount()==0&&original.pending().isEmpty());
            failure("cdp_popup_candidate_retired",candidate::requestProtection);original.disarm(2000);check(original.cleanupConfirmed());
        }finally{original.close();}
        check(CdpNativeProbe.fixtureFinish()==(1+(3<<8)));realSessions++;
    }
    private static void registrationLimits(Source original)throws Exception{
        List<Source> added=new ArrayList<>();
        try{
            for(int i=0;i<8;i++)added.add(new Source("scope-"+i,true));
            on(()->{Wire wire=new Wire();MdoBrowserCdpPopupBroker broker=broker(wire);broker.register(original.network);
                for(int i=0;i<7;i++)broker.register(added.get(i).network);
                failure("cdp_popup_opener_limit",()->broker.register(added.get(7).network));check(wire.opens==0);broker.close();
                Wire clock=new Wire();clock.time=-1;MdoBrowserCdpPopupBroker invalid=broker(clock);failure("cdp_popup_broker_clock_invalid",()->invalid.register(original.network));check(clock.opens==0);return null;
            });
        }finally{for(Source source:added)source.close();}
        try(Source inactive=new Source("not-enabled",false)){
            on(()->{Wire wire=new Wire();MdoBrowserCdpPopupBroker broker=broker(wire);failure("cdp_network_not_enabled",()->broker.register(inactive.network));check(wire.opens==0);broker.close();return null;});
        }
    }
    private static void poison(Source source,String mode)throws Exception{
        Wire wire=new Wire();wire.existing=false;MdoBrowserCdpPopupBroker original=broker(wire);
        MdoBrowserCdpPopupBroker.Registration registration=original.register(source.network);
        if(mode.equals("partial-arm")){wire.throwArm=true;failure("cdp_transport_failed",()->original.arm(1000));}
        else{
            original.arm(1000);
            if(mode.equals("disable-failure")){wire.rejectDisable=true;failure("cdp_command_rejected",()->original.disarm(1000));}
            else if(mode.equals("host-cancel")){Thread ui=new Thread(original::close);ui.start();ui.join(1000);check(!ui.isAlive());}
            else if(mode.equals("candidate-limit")){
                for(int i=0;i<7;i++){wire.add(attached("managed-"+i,"held-"+i,"registered-root",true));turns(original,1);}
                check(original.pending().size()==7);wire.add(attached("excess","held-excess","registered-root",true));
                failure("cdp_popup_broker_candidate_limit",()->original.turn(10,250));
            }else{
                wire.add(attached("managed-1","held-popup","registered-root",true));turns(original,1);MdoBrowserCdpPopupBroker.Candidate held=original.pending().get(0);
                if(mode.equals("expiry")){wire.time+=15000;failure("cdp_popup_broker_candidate_expired",()->original.turn(10,250));}
                else{registration.close();failure("cdp_popup_broker_opener_lost",()->original.turn(10,250));}
                check(!held.current()&&!held.nativeChildProven());
            }
        }
        check(!original.armed()&&!original.cleanupConfirmed());original.close();wire.cleared();
        Wire replacement=new Wire();MdoBrowserCdpPopupBroker blocked=broker(replacement);blocked.register(source.network);
        failure("cdp_popup_cleanup_unconfirmed",()->blocked.arm(1000));check(replacement.opens==0);blocked.close();
    }
    public static void main(String[] args)throws Exception{
        Looper.getMainLooper();worker=Executors.newSingleThreadExecutor();String mode=args.length==0?"suite":args[0];
        try(Source source=new Source("registered-root",true)){
            if(mode.equals("suite"))registrationLimits(source);
            on(()->{if(mode.equals("suite")){suite(source);nativeBroker(source);nativeProtection(source);}else poison(source,mode);return null;});
        }finally{worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"mode\":\""+mode+"\",\"real_native_sessions\":"+realSessions+",\"physical_opener_binding\":true,\"single_process_claim\":true,\"qualified_popup_adoption\":false,\"provider_qualified\":false}");
    }
}
