package org.xserver.android;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** One protocol dispatcher on its owning native worker, never on Android UI.
 * One ordinary RPC; only fixed native Fetch flow control may be sent inline
 * from its exact paused event. Ordinary callback RPC reentry remains forbidden.
 * Native control receipts contain no browser/request authority or page data.
 * No implicit reconnect/replay. A timed-out/mismatched stream is retired. */
final class MdoBrowserCdpSession implements AutoCloseable {
    interface Wire extends AutoCloseable {
        void send(byte[] bytes,int timeoutMs) throws IOException;
        byte[] receive(int timeoutMs) throws IOException;
        @Override void close();
    }
    interface Clock {long now();}
    interface Events {void event(String method,String session,JSONObject params) throws IOException;}
    interface Cooperator {void turn();}
    /** One explicit worker-owned cooperative scheduler. It may service other
     * idle connections, never reenter this session or reset its deadline. */
    static final class Cooperation implements AutoCloseable {
        private final Thread owner=Thread.currentThread();private final Cooperator previous;private boolean closed;
        private Cooperation(Cooperator next){previous=COOPERATOR.get();COOPERATOR.set(Objects.requireNonNull(next));}
        @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("CDP cooperation requires its worker");
            if(!closed){closed=true;if(previous==null)COOPERATOR.remove();else COOPERATOR.set(previous);}}
    }
    private static final ThreadLocal<Cooperator> COOPERATOR=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> COOPERATING=new ThreadLocal<>();
    static Cooperation cooperate(Cooperator scheduler){return new Cooperation(scheduler);}
    private static void progress(){
        Cooperator scheduler=COOPERATOR.get();if(scheduler==null||Boolean.TRUE.equals(COOPERATING.get()))return;
        COOPERATING.set(true);try{scheduler.turn();}finally{COOPERATING.remove();}
    }
    static final class ControlResult {
        final boolean ok,effectMayHaveOccurred;final String code;final int protocolCode;
        ControlResult(boolean ok,String code,int protocolCode,boolean sent){this.ok=ok;this.code=code;this.protocolCode=protocolCode;effectMayHaveOccurred=sent;}
    }
    /** A native-only observation token, never a wire request selector. Terminal
     * results cannot be upgraded by an acknowledgement after close/retirement. */
    static final class ControlReceipt {
        private final AtomicReference<ControlResult> outcome=new AtomicReference<>();
        private volatile boolean issued;
        ControlResult result(){return outcome.get();}
        boolean pending(){return outcome.get()==null;}
        boolean effectMayHaveOccurred(){ControlResult result=outcome.get();return result==null?issued:result.effectMayHaveOccurred;}
        private void finish(ControlResult result){outcome.compareAndSet(null,result);}
    }
    private static final int CONTROL_LIMIT=256,CONTROL_HISTORY=4096,CONTROL_TIMEOUT=5000;
    private static final ControlResult CONTROL_CANCELLED=new ControlResult(false,"cdp_control_cancelled",0,true);
    private static final ControlResult CONTROL_TIMEOUT_RESULT=new ControlResult(false,"cdp_control_timeout",0,true);
    private static final ControlResult CONTROL_SEND_FAILED=new ControlResult(false,"cdp_control_send_failed",0,true);
    private static final class PausedEvent {
        final String request,session,key;final MdoBrowserCdpResponse response;boolean used;
        PausedEvent(MdoBrowserCdpJson.Message message){
            response=MdoBrowserCdpResponse.inspect(message.value);
            session=message.session;Object raw=message.value.opt("requestId");String id=raw instanceof String?(String)raw:null;
            boolean valid=id!=null&&!id.isEmpty()&&id.length()<=256;
            if(valid)for(int i=0;i<id.length();i++)if(id.charAt(i)<33||id.charAt(i)>126){valid=false;break;}
            Object status=message.value.opt("responseStatusCode"),error=message.value.opt("responseErrorReason");
            if(message.value.has("responseStatusCode")&&(!(status instanceof Integer)||(Integer)status<100||(Integer)status>599))valid=false;
            if(message.value.has("responseErrorReason")&&(!(error instanceof String)||((String)error).isEmpty()||((String)error).length()>64))valid=false;
            request=valid?id:null;boolean response=message.value.has("responseStatusCode")||message.value.has("responseErrorReason");
            key=valid?(response?"S|":"Q|")+(session==null?"0|":"1|"+session+"|")+id:null;
        }
    }
    private static final class Control {
        final int id;final String session;final long born;final ControlReceipt receipt;
        Control(int id,String session,long born,ControlReceipt receipt){this.id=id;this.session=session;this.born=born;this.receipt=receipt;}
    }
    /** Only explicit native event diagnoses may cross the event callback.
     * Ordinary exception/provider messages keep the generic sanitized result. */
    static final class EventFailure extends IOException {
        final String code;
        static boolean allowed(String code){return Arrays.asList("cdp_chooser_arming_event","cdp_chooser_ambiguous","cdp_chooser_metadata_invalid",
            "cdp_chooser_clock_invalid","cdp_chooser_frame_unproven","cdp_chooser_frame_unavailable",
            "cdp_control_event_invalid","cdp_control_duplicate","cdp_control_limit").contains(code);}
        private static String checked(String code){if(!allowed(code))throw new IllegalArgumentException("unsupported_native_event_code");return code;}
        EventFailure(String code){super(checked(code));this.code=code;}
    }
    static final class Failure extends IOException {
        final int protocolCode;final boolean effectMayHaveOccurred;boolean uniqueContextRejected;
        Failure(String code,int protocolCode,boolean effect) {super(code);this.protocolCode=protocolCode;effectMayHaveOccurred=effect;}
    }
    private final Thread owner=Thread.currentThread();
    private final BooleanSupplier current;
    private final Events events;
    private final Clock clock;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final Set<String> children=new HashSet<>();
    private final Set<String> seenChildren=new HashSet<>();
    private final Object controlGate=new Object();private final Control[] controls=new Control[CONTROL_LIMIT];
    private final Set<String> controlKeys=new HashSet<>();private int controlCount;
    private PausedEvent paused;private long activeStarted;private int activeTimeout;private String controlFault;
    private volatile Wire wire;
    private int serial;private boolean busy;

    private MdoBrowserCdpSession(BooleanSupplier current,Events events,Clock clock) {
        this.current=Objects.requireNonNull(current);this.events=Objects.requireNonNull(events);this.clock=Objects.requireNonNull(clock);
    }
    static MdoBrowserCdpSession open(String target,int timeoutMs,BooleanSupplier current,Events events) throws IOException {
        MdoBrowserCdpSession session=new MdoBrowserCdpSession(current,events,android.os.SystemClock::elapsedRealtime);
        try {
            session.guard(false);session.wire=MdoBrowserCdpNative.open(target,timeoutMs);
            session.guard(false);return session;
        } catch(IOException|RuntimeException|Error failure){session.close();throw failure;}
    }
    /* Package-private deterministic seam; real-JVM tests also use open/native. */
    static MdoBrowserCdpSession owned(Wire wire,BooleanSupplier current,Events events,Clock clock) {
        MdoBrowserCdpSession session=new MdoBrowserCdpSession(current,events,clock);session.wire=Objects.requireNonNull(wire);return session;
    }
    private Failure failure(String code,boolean effect){return new Failure(code,0,effect);}
    private void guard(boolean effect) throws Failure {
        if(Thread.currentThread()!=owner)throw failure("cdp_wrong_worker",false);
        if(closed.get())throw failure("cdp_session_closed",effect);
        boolean alive;
        try {alive=current.getAsBoolean();}catch(RuntimeException|Error failure){close();throw failure("cdp_owner_changed",effect);}
        if(!alive){close();throw failure("cdp_owner_changed",effect);}
    }
    private void enter(int timeoutMs) throws Failure {
        guard(false);
        if(timeoutMs<10||timeoutMs>5000)throw failure("cdp_arguments_invalid",false);
        if(busy)throw failure("cdp_session_busy",false);
        expireControls(false);
        busy=true;
    }
    private int remaining(long started,int timeoutMs,boolean effect) throws Failure {
        guard(effect);long elapsed=clock.now()-started;
        if(elapsed<0||elapsed>timeoutMs-10){close();throw failure("cdp_command_timeout",effect);}
        return timeoutMs-(int)elapsed;
    }
    /** Trusted frame controller calls this only after validating the native
     * parent/attached-target relationship. Discovery/event IDs alone do not
     * grant admission. Unknown child-session traffic retires the connection. */
    void admitChild(String child) throws Failure {
        guard(false);
        if(!MdoBrowserCdpJson.token(child)||seenChildren.contains(child)||children.size()>=64||seenChildren.size()>=256)throw failure("cdp_session_invalid",false);
        seenChildren.add(child);children.add(child);
    }
    void retireChild(String child) throws Failure {
        guard(false);children.remove(child);
        synchronized(controlGate){for(int i=0;i<controls.length;i++)if(controls[i]!=null&&Objects.equals(child,controls[i].session)){
            controls[i].receipt.finish(CONTROL_CANCELLED);controls[i]=null;controlCount--;
        }}
    }
    private boolean admitted(String session){return session==null||children.contains(session);}
    private MdoBrowserCdpJson.Message packet(byte[] bytes,boolean effect) throws IOException {
        try {
            guard(effect);
            MdoBrowserCdpJson.Message message=MdoBrowserCdpJson.decode(bytes);
            if(!admitted(message.session)) {
                // Drain late traffic from an explicitly retired child; it
                // cannot be rebound to a new target under the same ID.
                if(seenChildren.contains(message.session))return null;
                throw failure("cdp_session_mismatch",effect);
            }
            return message;
        } finally {Arrays.fill(bytes,(byte)0);}
    }
    private void event(MdoBrowserCdpJson.Message message,boolean effect) throws IOException {
        guard(effect);
        PausedEvent previous=paused;
        try {
            paused="Fetch.requestPaused".equals(message.method)?new PausedEvent(message):null;
            events.event(message.method,message.session,message.value);
        }
        catch(EventFailure failure){close();throw failure(failure.code,effect);}
        catch(IOException|RuntimeException|Error failure){close();throw failure(controlFault==null?"cdp_event_failed":controlFault,effect);}
        finally{paused=previous;}
        if(controlFault!=null){close();throw failure(controlFault,effect);}
        guard(effect);
    }
    /** Fixed native continuation: exact frozen event ID/source, no URL, method,
     * header/body replacement, supplied selector or ordinary RPC reentry. */
    ControlReceipt continuePausedRequest()throws IOException{return pausedControl(false);}
    ControlReceipt abortPausedRequest()throws IOException{return pausedControl(true);}
    /** The snapshot was frozen before the event callback could modify JSON.
     * It contains no wire selector and cannot itself continue/claim a body. */
    MdoBrowserCdpResponse pausedResponse()throws IOException {
        guard(false);if(!busy||paused==null)throw failure("cdp_control_not_in_event",false);
        if(paused.request==null||!admitted(paused.session))throw new EventFailure("cdp_control_event_invalid");
        return paused.response;
    }
    private ControlReceipt pausedControl(boolean abort)throws IOException {
        guard(false);PausedEvent original=paused;
        if(!busy||original==null)throw failure("cdp_control_not_in_event",false);
        if(original.request==null||!admitted(original.session))throw new EventFailure("cdp_control_event_invalid");
        if(original.used)throw new EventFailure("cdp_control_duplicate");original.used=true;
        byte[] bytes=null;Control row;int budget;
        try{
            synchronized(controlGate){
                guard(false);
                if(controlKeys.contains(original.key))throw new EventFailure("cdp_control_duplicate");
                if(controlCount>=CONTROL_LIMIT||controlKeys.size()>=CONTROL_HISTORY||serial==Integer.MAX_VALUE)throw new EventFailure("cdp_control_limit");
                org.json.JSONObject params=new org.json.JSONObject().put("requestId",original.request);
                if(abort)params.put("errorReason","Aborted");
                ControlReceipt receipt=new ControlReceipt();row=new Control(++serial,original.session,clock.now(),receipt);
                bytes=MdoBrowserCdpJson.encode(row.id,abort?"Fetch.failRequest":"Fetch.continueRequest",params,original.session);
                budget=remaining(activeStarted,activeTimeout,false);controlKeys.add(original.key);
                int slot=0;while(controls[slot]!=null)slot++;controls[slot]=row;controlCount++;receipt.issued=true;
            }
            try{wire.send(bytes,budget);}
            catch(IOException|RuntimeException|Error failure){row.receipt.finish(CONTROL_SEND_FAILED);controlFault="cdp_control_send_failed";close();}
            return row.receipt;
        }catch(org.json.JSONException failure){throw new EventFailure("cdp_control_event_invalid");}
        finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    }
    private void expireControls(boolean effect)throws Failure {
        boolean expired=false;
        try{synchronized(controlGate){
            if(controlCount==0)return;long now=clock.now();
            for(Control row:controls)if(row!=null){long used=now-row.born;if(used<0||used>=CONTROL_TIMEOUT){row.receipt.finish(CONTROL_TIMEOUT_RESULT);expired=true;}}
        }}catch(RuntimeException|Error error){controlFault="cdp_control_clock_failed";close();throw failure(controlFault,effect);}
        if(expired){controlFault="cdp_control_timeout";close();throw failure(controlFault,effect);}
    }
    private boolean controlReply(MdoBrowserCdpJson.Message message,boolean effect)throws Failure {
        ControlResult result=null;
        synchronized(controlGate){
            guard(effect);
            for(int i=0;i<controls.length;i++){
                Control row=controls[i];if(row==null||row.id!=message.id)continue;
                if(!Objects.equals(row.session,message.session)){
                    result=new ControlResult(false,"cdp_control_reply_mismatch",0,true);
                }else result=new ControlResult(!message.failed,message.failed?(message.error==-32601?"cdp_control_method_unsupported":"cdp_control_rejected"):null,
                        message.failed?message.error:0,!message.failed||message.error!=-32601);
                row.receipt.finish(result);controls[i]=null;controlCount--;break;
            }
        }
        if(result==null)return false;
        if(!result.ok){controlFault=result.code;close();throw new Failure(result.code,result.protocolCode,effect);}
        return true;
    }
    int pendingControls()throws Failure {
        if(Thread.currentThread()!=owner)throw failure("cdp_wrong_worker",false);
        synchronized(controlGate){return controlCount;}
    }
    boolean idle()throws Failure {guard(false);return !busy;}
    /** Serialized RPC. A sent effect may have occurred even if its reply is
     * lost. Failure never retries. Rejected caller arguments leave I/O alone. */
    JSONObject call(String method,JSONObject params,String session,int timeoutMs,boolean effect) throws IOException {
        enter(timeoutMs);byte[] outgoing=null;boolean attempted=false,replied=false;
        try {
            long started=clock.now();activeStarted=started;activeTimeout=timeoutMs;int packetsSeen=0;
            if(!admitted(session))throw failure("cdp_session_invalid",false);
            if(serial==Integer.MAX_VALUE){close();throw failure("cdp_id_exhausted",false);}
            final int commandId=++serial;outgoing=MdoBrowserCdpJson.encode(commandId,method,params,session);
            int budget=remaining(started,timeoutMs,false);attempted=true;wire.send(outgoing,budget);
            Arrays.fill(outgoing,(byte)0);outgoing=null;
            for(;;) {
                progress();
                expireControls(effect);budget=remaining(started,timeoutMs,effect);
                byte[] bytes=wire.receive(Math.min(20,budget));if(bytes==null)continue;
                MdoBrowserCdpJson.Message message=packet(bytes,effect);
                remaining(started,timeoutMs,effect);
                if(++packetsSeen>4096)throw failure("cdp_event_limit",effect);
                if(message==null)continue;
                if(message.event()) {
                    event(message,effect);
                    if(!admitted(session))throw failure("cdp_session_detached",effect);
                    continue;
                }
                if(controlReply(message,effect))continue;
                if(message.id!=commandId||!Objects.equals(message.session,session))throw failure("cdp_reply_mismatch",effect);
                guard(effect);
                replied=true;
                if(message.failed) {
                    // A protocol rejection is a complete reply, not a corrupt
                    // stream. Its provider message/data are intentionally omitted.
                    Failure problem=new Failure(message.error==-32601?"cdp_method_unsupported":"cdp_command_rejected",
                                                message.error,effect&&message.error!=-32601);
                    problem.uniqueContextRejected=message.uniqueContextRejected;throw problem;
                }
                return message.value;
            }
        } catch(Failure failure) {
            if(attempted&&!replied)close();throw failure;
        } catch(IOException failure) {
            if(attempted)close();
            String code=!attempted&&("cdp_arguments_invalid".equals(failure.getMessage())||"cdp_json_invalid".equals(failure.getMessage()))?
                "cdp_arguments_invalid":"cdp_transport_failed";
            throw failure(code,attempted&&effect);
        } catch(RuntimeException|Error failure) {
            close();throw failure("cdp_dispatch_failed",attempted&&effect);
        } finally {if(outgoing!=null)Arrays.fill(outgoing,(byte)0);busy=false;}
    }
    /** Read one event or fixed native continuation acknowledgement. Ordinary
     * unsolicited replies still fail; idle time does not renew control TTL. */
    boolean poll(int timeoutMs) throws IOException {
        return poll(timeoutMs,timeoutMs);
    }
    /** A short kernel receive slice can retain a larger original callback/
     * control-send budget. Both remain fixed; idle polling renews no ACK TTL. */
    boolean poll(int receiveMs,int timeoutMs) throws IOException {
        if(receiveMs<10||receiveMs>timeoutMs)throw failure("cdp_arguments_invalid",false);
        enter(timeoutMs);byte[] bytes=null;
        try {
            long started=clock.now();activeStarted=started;activeTimeout=timeoutMs;
            bytes=wire.receive(receiveMs);guard(false);expireControls(false);if(bytes==null)return false;
            // An ordinary idle poll is not a broken stream. A packet delivered
            // after the original poll budget must not reach native event state.
            remaining(started,timeoutMs,false);
            MdoBrowserCdpJson.Message message=packet(bytes,false);
            bytes=null;
            remaining(started,timeoutMs,false);
            if(message==null)return false;
            expireControls(false);
            if(!message.event()){if(controlReply(message,false))return true;throw failure("cdp_reply_mismatch",false);}
            event(message,false);remaining(started,timeoutMs,false);return true;
        } catch(Failure failure){close();throw failure;}
        catch(IOException|RuntimeException|Error failure){close();throw failure("cdp_event_stream_failed",false);}
        finally {if(bytes!=null)Arrays.fill(bytes,(byte)0);busy=false;}
    }
    @Override public void close() {
        boolean first;
        synchronized(controlGate){
            first=closed.compareAndSet(false,true);
            for(int i=0;i<controls.length;i++)if(controls[i]!=null){controls[i].receipt.finish(CONTROL_CANCELLED);controls[i]=null;}
            controlCount=0;controlKeys.clear();
        }
        if(first){Wire connection=wire;if(connection!=null)connection.close();}
    }
}
