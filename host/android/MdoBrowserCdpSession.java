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
 * One ordinary RPC; only fixed native Fetch flow control/body claims may be sent inline
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
    /** Native-only original-body permit. No caller-provided stream/request ID.
     * cancel() only revokes admission; the owning worker must drain it. */
    static final class Body {
        private final MdoBrowserCdpSession owner;private PausedEvent original;
        private final long born,limit;private final int timeout;
        private final ControlReceipt claim=new ControlReceipt();
        private final AtomicReference<BodyResult> outcome=new AtomicReference<>();
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private String stream;private long received;private boolean eof,streamClosed,requestAborted,claimMayHaveOccurred;
        private Body(MdoBrowserCdpSession owner,PausedEvent original,long born,long limit,int timeout){
            this.owner=owner;this.original=original;this.born=born;this.limit=limit;this.timeout=timeout;
        }
        BodyResult result(){return outcome.get();}
        boolean ready(){synchronized(owner.controlGate){return outcome.get()==null&&stream!=null;}}
        void cancel(){synchronized(owner.controlGate){if(outcome.get()==null)cancelled.set(true);}}
    }
    static final class BodyResult {
        final boolean ok,claimMayHaveOccurred,eof,streamCloseConfirmed,requestAbortConfirmed;final String code;final long bytes;
        private BodyResult(boolean ok,String code,Body body){this.ok=ok;this.code=code;claimMayHaveOccurred=body.claimMayHaveOccurred;
            eof=body.eof;streamCloseConfirmed=body.streamClosed;requestAbortConfirmed=body.requestAborted;bytes=body.received;}
        private BodyResult(){ok=false;code="cdp_body_outcome_unavailable";claimMayHaveOccurred=true;eof=false;streamCloseConfirmed=false;requestAbortConfirmed=false;bytes=-1;}
    }
    private static final BodyResult BODY_UNKNOWN=new BodyResult();
    /** Caller owns this bounded buffer and must erase it after storing/using it. */
    static final class BodyChunk implements AutoCloseable {
        final byte[] data;final boolean eof;
        private BodyChunk(byte[] data,boolean eof){this.data=data;this.eof=eof;}
        @Override public void close(){Arrays.fill(data,(byte)0);}
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
        final int id;final String session;final long born;final ControlReceipt receipt;final Body body;
        Control(int id,String session,long born,ControlReceipt receipt,Body body){this.id=id;this.session=session;this.born=born;this.receipt=receipt;this.body=body;}
    }
    /** Only explicit native event diagnoses may cross the event callback.
     * Ordinary exception/provider messages keep the generic sanitized result. */
    static final class EventFailure extends IOException {
        final String code;
        static boolean allowed(String code){return Arrays.asList("cdp_chooser_arming_event","cdp_chooser_ambiguous","cdp_chooser_metadata_invalid",
            "cdp_chooser_clock_invalid","cdp_chooser_frame_unproven","cdp_chooser_frame_unavailable",
            "cdp_control_event_invalid","cdp_control_duplicate","cdp_control_limit","cdp_body_not_candidate","cdp_body_limit","cdp_body_source_unsupported").contains(code);}
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
    private final Body[] bodies=new Body[8];private final Set<String> streamIds=new HashSet<>();
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
        return issuePaused(abort,null).receipt;
    }
    Body takePausedBody(long maxBytes,int timeoutMs)throws IOException {
        guard(false);PausedEvent original=paused;
        if(!busy||original==null)throw failure("cdp_control_not_in_event",false);
        if(original.request==null)throw new EventFailure("cdp_control_event_invalid");
        if(original.session!=null)throw new EventFailure("cdp_body_source_unsupported");
        if(!original.response.kind.equals("candidate"))throw new EventFailure("cdp_body_not_candidate");
        if(maxBytes<1||maxBytes>64L*1024*1024||timeoutMs<1000||timeoutMs>600000)throw failure("cdp_body_arguments_invalid",false);
        if(original.response.declaredLength>maxBytes)throw new EventFailure("cdp_body_limit");
        Body body=new Body(this,original,clock.now(),maxBytes,timeoutMs);
        issuePaused(false,body);return body;
    }
    private Control issuePaused(boolean abort,Body body)throws IOException {
        guard(false);PausedEvent original=paused;
        if(!busy||original==null)throw failure("cdp_control_not_in_event",false);
        if(original.request==null||!admitted(original.session))throw new EventFailure("cdp_control_event_invalid");
        if(original.used)throw new EventFailure("cdp_control_duplicate");
        byte[] bytes=null;Control row;int budget;
        try{
            synchronized(controlGate){
                guard(false);
                if(controlKeys.contains(original.key))throw new EventFailure("cdp_control_duplicate");
                if(controlCount>=CONTROL_LIMIT||controlKeys.size()>=CONTROL_HISTORY||serial==Integer.MAX_VALUE)throw new EventFailure("cdp_control_limit");
                int bodySlot=-1;if(body!=null){for(int i=0;i<bodies.length;i++)if(bodies[i]==null){bodySlot=i;break;}if(bodySlot<0)throw new EventFailure("cdp_body_limit");}
                org.json.JSONObject params=new org.json.JSONObject().put("requestId",original.request);
                if(abort)params.put("errorReason","Aborted");
                ControlReceipt receipt=body==null?new ControlReceipt():body.claim;row=new Control(++serial,original.session,clock.now(),receipt,body);
                bytes=MdoBrowserCdpJson.encode(row.id,body!=null?"Fetch.takeResponseBodyAsStream":abort?"Fetch.failRequest":"Fetch.continueRequest",params,original.session);
                budget=remaining(activeStarted,activeTimeout,false);original.used=true;controlKeys.add(original.key);
                int slot=0;while(controls[slot]!=null)slot++;controls[slot]=row;controlCount++;receipt.issued=true;
                if(body!=null){bodies[bodySlot]=body;body.claimMayHaveOccurred=true;}
            }
            try{wire.send(bytes,budget);}
            catch(IOException|RuntimeException|Error failure){row.receipt.finish(CONTROL_SEND_FAILED);controlFault="cdp_control_send_failed";close();}
            return row;
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
                if(row.body!=null){
                    Body body=row.body;
                    if(result.ok){Object stream=message.value.opt("stream");
                        if(message.value.length()!=1||!(stream instanceof String)||!streamHandle((String)stream))
                            result=new ControlResult(false,"cdp_body_claim_reply_invalid",0,true);
                        else if(!streamIds.add((String)stream))result=new ControlResult(false,"cdp_body_stream_reused",0,true);
                        else body.stream=(String)stream;
                    }
                    if(!result.ok){if(result.protocolCode==-32601)body.claimMayHaveOccurred=false;finishBody(body,false,result.code);}
                }
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
    int pendingBodies()throws Failure {
        if(Thread.currentThread()!=owner)throw failure("cdp_wrong_worker",false);
        synchronized(controlGate){int count=0;for(Body body:bodies)if(body!=null)count++;return count;}
    }
    boolean idle()throws Failure {guard(false);return !busy;}
    private static boolean streamHandle(String handle){
        if(handle.isEmpty()||handle.length()>256)return false;
        for(int i=0;i<handle.length();i++)if(handle.charAt(i)<33||handle.charAt(i)>126)return false;return true;
    }
    private void bodyGuard(Body body)throws Failure {
        guard(false);if(body==null||body.owner!=this)throw failure("cdp_body_owner_mismatch",false);
        synchronized(controlGate){
            if(body.outcome.get()!=null)throw failure("cdp_body_terminal",false);
            boolean found=false;for(Body item:bodies)if(item==body)found=true;
            if(!found||body.original==null)throw failure("cdp_body_owner_mismatch",false);
            if(body.stream==null)throw failure("cdp_body_not_ready",false);
        }
        if(busy)throw failure("cdp_session_busy",false);
    }
    private int bodyRemaining(Body body,int timeout)throws Failure {
        guard(false);
        long used=clock.now()-body.born;
        if(used<0||used>body.timeout-10)throw failure("cdp_body_timeout",false);
        return (int)Math.min(timeout,body.timeout-used);
    }
    private void finishBody(Body body,boolean success,String code){
        synchronized(controlGate){
            if(body.outcome.get()!=null)return;
            if(body.cancelled.get()){success=false;code="cdp_body_cancelled";}
            BodyResult result;try{result=new BodyResult(success,code,body);}catch(RuntimeException|Error unavailable){result=BODY_UNKNOWN;}
            body.outcome.compareAndSet(null,result);body.stream=null;body.original=null;
            for(int i=0;i<bodies.length;i++)if(bodies[i]==body)bodies[i]=null;
        }
    }
    private static IOException bodyDataInvalid(){return new IOException("cdp_body_data_invalid");}
    private static byte[] bodyData(JSONObject reply,int maximum)throws IOException {
        for(java.util.Iterator<String> keys=reply.keys();keys.hasNext();){String key=keys.next();if(!key.equals("data")&&!key.equals("eof")&&!key.equals("base64Encoded"))throw bodyDataInvalid();}
        Object data=reply.opt("data"),eof=reply.opt("eof"),base64=reply.opt("base64Encoded");
        if(!(data instanceof String)||!(eof instanceof Boolean)||reply.has("base64Encoded")&&!(base64 instanceof Boolean))throw bodyDataInvalid();
        String text=(String)data;
        if(Boolean.TRUE.equals(base64)){
            if(text.length()>((maximum+2)/3)*4||text.length()%4!=0)throw bodyDataInvalid();
            byte[] bytes=null;try{
                bytes=java.util.Base64.getDecoder().decode(text);
                if(bytes.length>maximum||!java.util.Base64.getEncoder().encodeToString(bytes).equals(text))throw bodyDataInvalid();
                byte[] valid=bytes;bytes=null;return valid;
            }catch(IllegalArgumentException failure){throw bodyDataInvalid();}
            finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
        }
        if(text.length()>maximum)throw bodyDataInvalid();int size=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)){if(i+1==text.length()||!Character.isLowSurrogate(text.charAt(++i)))throw bodyDataInvalid();size+=4;}
            else if(Character.isLowSurrogate(c))throw bodyDataInvalid();else size+=c<128?1:c<2048?2:3;
            if(size>maximum)throw bodyDataInvalid();
        }
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    /** Sequential fixed read on this exact claim. A lost reply advances an
     * uncertain stream and therefore retires it; never resend the read. */
    BodyChunk readBody(Body body,int timeoutMs)throws IOException {
        if(timeoutMs<10||timeoutMs>5000)throw failure("cdp_body_arguments_invalid",false);
        bodyGuard(body);byte[] data=null;
        try{
            if(body.cancelled.get()){cleanupBody(body,5000,false,"cdp_body_cancelled");throw failure("cdp_body_cancelled",false);}
            int budget=bodyRemaining(body,timeoutMs);int size=(int)Math.min(65536,Math.max(1,body.limit-body.received));
            JSONObject params=new JSONObject().put("handle",body.stream).put("size",size);
            JSONObject reply=call("IO.read",params,body.original.session,budget,true);
            bodyGuard(body);bodyRemaining(body,10);data=bodyData(reply,size);
            if(data.length>body.limit-body.received)throw new IOException("cdp_body_limit");
            synchronized(controlGate){bodyGuard(body);body.received+=data.length;body.eof=reply.optBoolean("eof",false);}
            BodyChunk chunk=new BodyChunk(data,body.eof);
            if(body.cancelled.get()){cleanupBody(body,5000,false,"cdp_body_cancelled");throw failure("cdp_body_cancelled",true);}
            if(body.eof){cleanupBody(body,5000,true,null);if(body.outcome.get()==null||!body.outcome.get().ok)throw failure("cdp_body_cleanup_unconfirmed",true);}
            synchronized(controlGate){
                guard(true);if(!body.eof){bodyGuard(body);if(body.cancelled.get())throw failure("cdp_body_cancelled",true);}
                data=null;return chunk;
            }
        }catch(Failure failure){
            if(body.outcome.get()==null){if("cdp_body_timeout".equals(failure.getMessage())||"cdp_body_cancelled".equals(failure.getMessage()))try{cleanupBody(body,5000,false,failure.getMessage());}catch(IOException|RuntimeException|Error ignored){}
                if(body.outcome.get()==null){finishBody(body,false,failure.getMessage());close();}}
            throw failure;
        }catch(IOException failure){finishBody(body,false,"cdp_body_data_invalid".equals(failure.getMessage())?"cdp_body_data_invalid":"cdp_body_limit");close();throw failure;}
        catch(org.json.JSONException|RuntimeException|Error failure){finishBody(body,false,"cdp_body_read_failed");close();throw failure("cdp_body_read_failed",true);}
        finally{if(data!=null)Arrays.fill(data,(byte)0);}
    }
    void cancelBody(Body body,int timeoutMs)throws IOException {
        if(timeoutMs<10||timeoutMs>5000)throw failure("cdp_body_arguments_invalid",false);
        bodyGuard(body);body.cancel();cleanupBody(body,timeoutMs,false,"cdp_body_cancelled");
    }
    /** Independent bounded cleanup on the original stream/request. ACKs for
     * both operations are required; connection close alone proves neither. */
    private void cleanupBody(Body body,int timeout,boolean success,String code)throws IOException {
        bodyGuard(body);long started=clock.now();
        try{
            JSONObject closed=call("IO.close",new JSONObject().put("handle",body.stream),body.original.session,remaining(started,timeout,true),true);
            if(closed.length()!=0)throw new IOException("cdp_body_cleanup_invalid");body.streamClosed=true;
            JSONObject aborted=call("Fetch.failRequest",new JSONObject().put("requestId",body.original.request).put("errorReason","Aborted"),body.original.session,remaining(started,timeout,true),true);
            if(aborted.length()!=0)throw new IOException("cdp_body_cleanup_invalid");body.requestAborted=true;
            finishBody(body,success,code);
        }catch(IOException|org.json.JSONException|RuntimeException|Error failure){finishBody(body,false,"cdp_body_cleanup_unconfirmed");close();
            throw failure("cdp_body_cleanup_unconfirmed",true);}
    }
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
            for(Body body:bodies)if(body!=null)finishBody(body,false,"cdp_body_connection_closed");streamIds.clear();
        }
        if(first){Wire connection=wire;if(connection!=null)connection.close();}
    }
}
