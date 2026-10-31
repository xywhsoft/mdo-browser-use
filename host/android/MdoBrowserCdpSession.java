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
 * One ordinary RPC and one fixed asynchronous native root navigation; only
 * fixed Fetch flow control/body claims may be sent inline from their paused
 * event. Ordinary/navigation callback RPC reentry remains forbidden.
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
    /** Acknowledgement of one original root navigation, not page-load or file
     * completion. Optional native download metadata is never inferred from an
     * abort, URL, response candidate or missing loader. No raw reply survives. */
    static final class NavigationResult {
        final boolean acknowledged,ok,downloadKnown,download,effectMayHaveOccurred,loaderPresent;
        final String code,nativeError;
        private NavigationResult(boolean acknowledged,boolean ok,boolean known,boolean download,String code,boolean sent){
            this(acknowledged,ok,known,download,code,sent,null);
        }
        private NavigationResult(boolean acknowledged,boolean ok,boolean known,boolean download,String code,boolean sent,String nativeError){
            this(acknowledged,ok,known,download,code,sent,nativeError,false);
        }
        private NavigationResult(boolean acknowledged,boolean ok,boolean known,boolean download,String code,boolean sent,String nativeError,boolean loaderPresent){
            this.acknowledged=acknowledged;this.ok=ok;downloadKnown=known;this.download=download;this.code=code;effectMayHaveOccurred=sent;this.nativeError=nativeError;this.loaderPresent=loaderPresent;
        }
    }
    static NavigationResult navigationFailure(String code,boolean sent){
        if(code==null||!code.matches("cdp_[a-z_]{1,80}"))code="cdp_navigation_dispatch_failed";
        return new NavigationResult(false,false,false,false,code,sent);
    }
    static final class Navigation {
        private final int id,timeout;private final long born;private final String frame;
        final boolean receiptOnly;
        private final AtomicReference<NavigationResult> outcome=new AtomicReference<>();private volatile boolean issued;private volatile String loader;
        private Navigation(int id,String frame,long born,int timeout,boolean receiptOnly){this.id=id;this.frame=frame;this.born=born;this.timeout=timeout;this.receiptOnly=receiptOnly;}
        NavigationResult result(){return outcome.get();}
        boolean pending(){return result()==null;}
        boolean effectMayHaveOccurred(){NavigationResult result=result();return result==null?issued:result.effectMayHaveOccurred;}
        boolean matchesCommittedLoader(String frame,String loader){NavigationResult result=result();return result!=null&&result.acknowledged&&result.ok&&result.loaderPresent&&
            !(result.downloadKnown&&result.download)&&this.frame.equals(frame)&&this.loader!=null&&this.loader.equals(loader);}
        boolean matchesSameDocument(String frame){NavigationResult result=result();return result!=null&&result.acknowledged&&result.ok&&
            result.downloadKnown&&!result.download&&!result.loaderPresent&&this.frame.equals(frame);}
        boolean matchesDownloadLoader(String frame,String loader){NavigationResult result=result();return !receiptOnly&&result!=null&&result.acknowledged&&result.ok&&result.downloadKnown&&result.download&&result.loaderPresent&&this.frame.equals(frame)&&this.loader!=null&&this.loader.equals(loader);}
        private void finish(NavigationResult result){outcome.compareAndSet(null,result);}
    }
    private static final NavigationResult NAVIGATION_NOT_SENT=new NavigationResult(false,false,false,false,"cdp_navigation_not_sent",false);
    private static final NavigationResult NAVIGATION_CLOSED=new NavigationResult(false,false,false,false,"cdp_navigation_connection_closed",true);
    private static final NavigationResult NAVIGATION_SEND_FAILED=new NavigationResult(false,false,false,false,"cdp_navigation_send_failed",true);
    private static final NavigationResult NAVIGATION_TIMED_OUT=new NavigationResult(false,false,false,false,"cdp_navigation_timeout",true);
    private static final NavigationResult NAVIGATION_REPLY_INVALID=new NavigationResult(false,false,false,false,"cdp_navigation_reply_invalid",true);
    /** Native-only original-body permit. No caller-provided stream/request ID.
     * cancel() only revokes admission; the owning worker must drain it. */
    static final class Body {
        private final MdoBrowserCdpSession owner;private PausedEvent original;
        private final long born,limit;private final int timeout;
        private final ControlReceipt claim=new ControlReceipt();
        private final AtomicReference<BodyResult> outcome=new AtomicReference<>();
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private final AtomicBoolean downloadClaimed=new AtomicBoolean();
        private String stream;private long received;private boolean eof,streamClosed,requestAborted,claimMayHaveOccurred;
        private boolean encodingKnown,binary;
        private boolean readRequested,readPending,closePending,abortPending,cleaning;
        private long cleanupBorn;private String cleanupCode;private int readSize;
        private BodyChunk chunk;
        private Body(MdoBrowserCdpSession owner,PausedEvent original,long born,long limit,int timeout){
            this.owner=owner;this.original=original;this.born=born;this.limit=limit;this.timeout=timeout;
        }
        BodyResult result(){return outcome.get();}
        boolean ready(){synchronized(owner.controlGate){return outcome.get()==null&&stream!=null;}}
        void cancel(){if(outcome.get()==null)cancelled.set(true);}
        long maximumBytes(){return limit;}
        boolean expired(long now){long elapsed=now-born;return elapsed<0||elapsed>=timeout;}
        boolean claimDownload(){return outcome.get()==null&&!cancelled.get()&&downloadClaimed.compareAndSet(false,true);}
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
        final byte[] data;final boolean eof;final long offset;private final Body source;
        private final AtomicBoolean downloadClaimed=new AtomicBoolean(),erased=new AtomicBoolean();
        private BodyChunk(Body source,byte[] data,boolean eof){this.source=source;this.data=data;this.eof=eof;offset=source.received-data.length;}
        boolean claimDownload(Body body){return source==body&&!erased.get()&&downloadClaimed.compareAndSet(false,true);}
        @Override public void close(){erased.set(true);Arrays.fill(data,(byte)0);}
    }
    /** Native original response held before permission/transfer admission.
     * Decisions are atomic and perform no I/O. The owning protocol worker
     * applies the first acceptance to this frozen event, never a supplied ID. */
    static final class Offer {
        private final MdoBrowserCdpSession owner;private PausedEvent original;private final MdoBrowserCdpResponse metadata;
        private final String networkId;private MdoBrowserCdpNetworkRequests.Link requestLink;private boolean associated;
        private MdoBrowserCdpNetworkFrames.Origin sourceEpoch;private String epochKind;
        private final long born;private final AtomicReference<OfferDecision> decision=new AtomicReference<>();
        private final AtomicBoolean cancelled=new AtomicBoolean();private final AtomicReference<OfferResult> outcome=new AtomicReference<>();
        private volatile Body claimed;private boolean issued;private String abortCode;
        private Offer(MdoBrowserCdpSession owner,PausedEvent original,long born){this.owner=owner;this.original=original;metadata=original.response;networkId=original.networkId;this.born=born;}
        String networkIdentity(){return networkId;}
        void associateRequest(MdoBrowserCdpNetworkRequests.Link link){if(associated)throw new IllegalStateException("Original response already associated");associated=true;requestLink=link;}
        String sourceRequestReference(){return requestLink==null?null:requestLink.reference();}
        /** End metadata waiting only; source cancellation/cleanup is independent. */
        void retireRequestAssociation(){if(requestLink!=null)requestLink.retire();}
        void bindSourceEpoch(MdoBrowserCdpNetworkFrames.Origin epoch,String kind){
            if(sourceEpoch!=null||epoch==null||!("response_frame".equals(kind)||"navigation_candidates".equals(kind)||"unproven".equals(kind)))throw new IllegalStateException("Invalid original source epoch");
            sourceEpoch=epoch;epochKind=kind;
        }
        MdoBrowserCdpNetworkFrames.Origin sourceEpoch(){return sourceEpoch;}
        String sourceEpochKind(){return epochKind;}
        MdoBrowserCdpResponse metadata(){return metadata;}
        OfferResult result(){return outcome.get();}
        void accept(long maximum,int timeoutMs)throws IOException {
            if(maximum<1||maximum>64L*1024*1024||timeoutMs<1000||timeoutMs>600000)throw new IOException("cdp_offer_arguments_invalid");
            if(metadata.declaredLength>maximum)throw new IOException("cdp_offer_byte_limit");
            if(owner.closed.get()||outcome.get()!=null||cancelled.get())throw new IOException("cdp_offer_unavailable");
            long elapsed=owner.clock.now()-born;
            if(elapsed<0||elapsed>=120000){cancelled.set(true);throw new IOException(elapsed<0?"cdp_offer_clock_invalid":"cdp_offer_expired");}
            if(!decision.compareAndSet(null,new OfferDecision(maximum,timeoutMs)))throw new IOException("cdp_offer_already_decided");
        }
        void cancel(){cancelled.set(true);Body body=claimed;if(body!=null)body.cancel();}
    }
    private static final class OfferDecision {
        final long maximum;final int timeout;
        OfferDecision(long maximum,int timeout){this.maximum=maximum;this.timeout=timeout;}
    }
    static final class OfferResult {
        final boolean accepted,claimMayHaveOccurred,requestAbortConfirmed;final String code;final Body body;
        private OfferResult(Offer offer,boolean accepted,boolean aborted,String code){
            this.accepted=accepted;requestAbortConfirmed=aborted;this.code=code;body=offer.claimed;
            claimMayHaveOccurred=body!=null&&body.claimMayHaveOccurred;
        }
        private OfferResult(){accepted=false;claimMayHaveOccurred=true;requestAbortConfirmed=false;code="cdp_offer_outcome_unavailable";body=null;}
    }
    private static final OfferResult OFFER_UNKNOWN=new OfferResult();
    private static final int CONTROL_LIMIT=256,CONTROL_HISTORY=4096,CONTROL_TIMEOUT=5000;
    private static final int ATTACH_DETACH=-2,ATTACH_RESUME=-1,FLOW=0,CLAIM=1,BODY_READ=2,BODY_CLOSE=3,BODY_ABORT=4;
    private static final ControlResult CONTROL_CANCELLED=new ControlResult(false,"cdp_control_cancelled",0,true);
    private static final ControlResult CONTROL_TIMEOUT_RESULT=new ControlResult(false,"cdp_control_timeout",0,true);
    private static final ControlResult CONTROL_SEND_FAILED=new ControlResult(false,"cdp_control_send_failed",0,true);
    private static final class PausedEvent {
        final String request,session,key,networkId;final MdoBrowserCdpResponse response;boolean used;
        PausedEvent(MdoBrowserCdpJson.Message message){
            response=MdoBrowserCdpResponse.inspect(message.value);
            Object network=message.value.opt("networkId");networkId="Document".equals(message.value.opt("resourceType"))&&MdoBrowserCdpNetworkFrames.identity(network)?(String)network:null;
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
    /** Native metadata only, never proof of task/WebView ownership. The wire
     * session stays private; a fixed release can only use this frozen event. */
    static final class AttachedTarget {
        final String target,type,opener;final boolean waiting;
        private final MdoBrowserCdpSession owner;
        private final String session;private final boolean valid;private boolean used;
        private volatile boolean resumed,detached;
        boolean resumeConfirmed(){return resumed;}boolean detachConfirmed(){return detached;}
        private AttachedTarget(MdoBrowserCdpSession owner,MdoBrowserCdpJson.Message message){
            this.owner=owner;
            Object child=message.value.opt("sessionId"),info=message.value.opt("targetInfo"),wait=message.value.opt("waitingForDebugger");
            JSONObject details=info instanceof JSONObject?(JSONObject)info:null;
            Object id=details==null?null:details.opt("targetId"),kind=details==null?null:details.opt("type"),parent=details==null?null:details.opt("openerId");
            boolean good=message.session==null&&child instanceof String&&MdoBrowserCdpJson.token((String)child)&&
                id instanceof String&&MdoBrowserCdpJson.token((String)id)&&kind instanceof String&&MdoBrowserCdpJson.token((String)kind)&&wait instanceof Boolean&&
                (parent==null||parent instanceof String&&MdoBrowserCdpJson.token((String)parent));
            valid=good;session=good?(String)child:null;target=good?(String)id:null;type=good?(String)kind:null;
            opener=good?(String)parent:null;waiting=good&&(Boolean)wait;
        }
        boolean matchesNativeSession(Object value){return valid&&session.equals(value);}
    }
    private static final class Control {
        final int id;final String session;final long born;final ControlReceipt receipt;final Body body;final int kind,timeout;final Offer offer;final AttachedTarget attachment;
        Control(int id,String session,long born,ControlReceipt receipt,Body body,int kind,int timeout){this(id,session,born,receipt,body,kind,timeout,null);}
        Control(int id,String session,long born,ControlReceipt receipt,Body body,int kind,int timeout,Offer offer){this(id,session,born,receipt,body,kind,timeout,offer,null);}
        Control(int id,String session,long born,ControlReceipt receipt,Body body,int kind,int timeout,Offer offer,AttachedTarget attachment){this.id=id;this.session=session;this.born=born;this.receipt=receipt;this.body=body;this.kind=kind;this.timeout=timeout;this.offer=offer;this.attachment=attachment;}
    }
    /** Only explicit native event diagnoses may cross the event callback.
     * Ordinary exception/provider messages keep the generic sanitized result. */
    static final class EventFailure extends IOException {
        final String code;
        static boolean allowed(String code){return Arrays.asList("cdp_chooser_arming_event","cdp_chooser_ambiguous","cdp_chooser_metadata_invalid",
            "cdp_chooser_clock_invalid","cdp_chooser_frame_unproven","cdp_chooser_frame_unavailable",
            "cdp_control_event_invalid","cdp_control_duplicate","cdp_control_limit","cdp_body_not_candidate","cdp_body_limit","cdp_body_source_unsupported",
            "cdp_attach_browser_required","cdp_attach_event_invalid","cdp_attach_not_waiting","cdp_attach_duplicate","cdp_attach_limit","cdp_attach_session_reused",
            "download_frame_metadata_invalid","download_frame_parent_unproven","download_frame_root_changed","download_frame_limit","download_frame_lifetime_limit","download_frame_session_unproven").contains(code);}
        private static String checked(String code){if(!allowed(code))throw new IllegalArgumentException("unsupported_native_event_code");return code;}
        EventFailure(String code){super(checked(code));this.code=code;}
    }
    static final class Failure extends IOException {
        final int protocolCode;final boolean effectMayHaveOccurred;boolean uniqueContextRejected;
        Failure(String code,int protocolCode,boolean effect) {super(code);this.protocolCode=protocolCode;effectMayHaveOccurred=effect;}
    }
    private final Thread owner=Thread.currentThread();
    private final boolean browserTarget;
    private final BooleanSupplier current;
    private final Events events;
    private final Clock clock;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final Set<String> children=new HashSet<>();
    private final Set<String> seenChildren=new HashSet<>();
    private final Object controlGate=new Object();private final Control[] controls=new Control[CONTROL_LIMIT];
    private final Set<String> controlKeys=new HashSet<>();private int controlCount;
    private final Body[] bodies=new Body[8];private final Set<String> streamIds=new HashSet<>();
    private final Offer[] offers=new Offer[8];
    private final Set<Integer> retiredBodyReads=new HashSet<>();
    private PausedEvent paused;private AttachedTarget attached;private long activeStarted;private int activeTimeout;private String controlFault;
    private Navigation navigation;private int navigationCount;
    private volatile Wire wire;
    private int serial,bodyCursor,offerCursor;private boolean busy;

    private MdoBrowserCdpSession(BooleanSupplier current,Events events,Clock clock){this(current,events,clock,false);}
    private MdoBrowserCdpSession(BooleanSupplier current,Events events,Clock clock,boolean browserTarget) {
        this.browserTarget=browserTarget;
        this.current=Objects.requireNonNull(current);this.events=Objects.requireNonNull(events);this.clock=Objects.requireNonNull(clock);
    }
    static MdoBrowserCdpSession open(String target,int timeoutMs,BooleanSupplier current,Events events) throws IOException {
        MdoBrowserCdpSession session=new MdoBrowserCdpSession(current,events,android.os.SystemClock::elapsedRealtime);
        try {
            session.guard(false);session.wire=MdoBrowserCdpNative.open(target,timeoutMs);
            session.guard(false);return session;
        } catch(IOException|RuntimeException|Error failure){session.close();throw failure;}
    }
    /** Private browser connection; carries no page/popup ownership by itself. */
    static MdoBrowserCdpSession openBrowser(MdoBrowserCdpNative.BrowserEndpoint endpoint,int timeoutMs,BooleanSupplier current,Events events)throws IOException {
        MdoBrowserCdpSession session=new MdoBrowserCdpSession(current,events,android.os.SystemClock::elapsedRealtime,true);
        try{
            session.guard(false);session.wire=MdoBrowserCdpNative.openBrowser(endpoint,timeoutMs);
            session.guard(false);return session;
        }catch(IOException|RuntimeException|Error failure){session.close();throw failure;}
    }
    /* Package-private deterministic seam; real-JVM tests also use open/native. */
    static MdoBrowserCdpSession owned(Wire wire,BooleanSupplier current,Events events,Clock clock) {
        MdoBrowserCdpSession session=new MdoBrowserCdpSession(current,events,clock);session.wire=Objects.requireNonNull(wire);return session;
    }
    static MdoBrowserCdpSession ownedBrowser(Wire wire,BooleanSupplier current,Events events,Clock clock){
        MdoBrowserCdpSession session=new MdoBrowserCdpSession(current,events,clock,true);session.wire=Objects.requireNonNull(wire);return session;
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
        AttachedTarget previousAttached=attached;
        try {
            paused="Fetch.requestPaused".equals(message.method)?new PausedEvent(message):null;
            attached="Target.attachedToTarget".equals(message.method)?new AttachedTarget(this,message):null;
            events.event(message.method,message.session,message.value);
        }
        catch(EventFailure failure){close();throw failure(failure.code,effect);}
        catch(IOException|RuntimeException|Error failure){close();throw failure(controlFault==null?"cdp_event_failed":controlFault,effect);}
        finally{paused=previous;attached=previousAttached;}
        if(controlFault!=null){close();throw failure(controlFault,effect);}
        guard(effect);
    }
    /** Fixed native continuation: exact frozen event ID/source, no URL, method,
     * header/body replacement, supplied selector or ordinary RPC reentry. */
    ControlReceipt continuePausedRequest()throws IOException{return pausedControl(false);}
    ControlReceipt abortPausedRequest()throws IOException{return pausedControl(true);}
    AttachedTarget attachedTarget()throws IOException {
        guard(false);if(!busy||attached==null)throw failure("cdp_control_not_in_event",false);
        if(!browserTarget)throw new EventFailure("cdp_attach_browser_required");
        if(!attached.valid)throw new EventFailure("cdp_attach_event_invalid");return attached;
    }
    /** Undo only our original native debugger wait. This carries no Agent
     * script, URL, target selector, page ownership or ordinary RPC reentry. */
    ControlReceipt resumeAttachedTarget()throws IOException {
        AttachedTarget original=attachedTarget();
        if(!original.waiting)throw new EventFailure("cdp_attach_not_waiting");
        return releaseAttachedTarget(original);
    }
    /** Existing targets need detachment only; never manufacture a debugger
     * resume when the original native event said it was not waiting. */
    ControlReceipt releaseUnclaimedAttachedTarget()throws IOException{return releaseAttachedTarget(attachedTarget());}
    AttachedTarget retainAttachedTarget()throws IOException {
        AttachedTarget original=attachedTarget();if(!original.waiting)throw new EventFailure("cdp_attach_not_waiting");
        synchronized(controlGate){
            guard(false);if(original.used||seenChildren.contains(original.session))throw new EventFailure("cdp_attach_duplicate");
            if(children.size()>=64||seenChildren.size()>=256||controlKeys.size()>=CONTROL_HISTORY)throw new EventFailure("cdp_attach_limit");
            seenChildren.add(original.session);children.add(original.session);controlKeys.add("H|"+original.session);original.used=true;return original;
        }
    }
    /** Only while the process broker waits for its own disable ACK. Record
     * late sessions as retired without resuming or detaching them again. */
    void ignoreAttachedTargetDuringDisable()throws IOException {
        AttachedTarget original=attachedTarget();guard(false);
        if(original.used||seenChildren.contains(original.session))throw new EventFailure("cdp_attach_duplicate");
        if(seenChildren.size()>=256)throw new EventFailure("cdp_attach_limit");
        seenChildren.add(original.session);original.used=true;
    }
    void retireAttachedTarget(AttachedTarget original)throws IOException {
        guard(false);if(original==null||original.owner!=this||!original.used)throw failure("cdp_attach_event_invalid",false);retireChild(original.session);
    }
    private ControlReceipt releaseAttachedTarget(AttachedTarget original)throws IOException {
        byte[] bytes=null;Control row;int budget;
        try{
            synchronized(controlGate){
                guard(false);String key="A|"+original.session;
                if(original.used||controlKeys.contains(key))throw new EventFailure("cdp_attach_duplicate");
                if(controlCount>=CONTROL_LIMIT||controlKeys.size()>=CONTROL_HISTORY||serial==Integer.MAX_VALUE)throw new EventFailure("cdp_attach_limit");
                if(!children.contains(original.session)){
                    if(seenChildren.contains(original.session))throw new EventFailure("cdp_attach_session_reused");
                    if(children.size()>=64||seenChildren.size()>=256)throw new EventFailure("cdp_attach_limit");
                }
                row=new Control(++serial,original.waiting?original.session:null,clock.now(),new ControlReceipt(),null,original.waiting?ATTACH_RESUME:ATTACH_DETACH,CONTROL_TIMEOUT,null,original);
                try{bytes=MdoBrowserCdpJson.encode(row.id,original.waiting?"Runtime.runIfWaitingForDebugger":"Target.detachFromTarget",
                    original.waiting?null:new JSONObject().put("sessionId",original.session),original.waiting?original.session:null);}
                catch(org.json.JSONException invalid){throw new EventFailure("cdp_attach_event_invalid");}
                budget=remaining(activeStarted,activeTimeout,false);
                if(!children.contains(original.session)){seenChildren.add(original.session);children.add(original.session);}
                controlKeys.add(key);original.used=true;
                int slot=0;while(controls[slot]!=null)slot++;controls[slot]=row;controlCount++;row.receipt.issued=true;
            }
            try{wire.send(bytes,budget);}
            catch(IOException|RuntimeException|Error failure){row.receipt.finish(CONTROL_SEND_FAILED);controlFault="cdp_control_send_failed";close();}
            return row.receipt;
        }finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    }
    private void detachResumedTarget(Control resumed,boolean effect)throws IOException {
        byte[] bytes=null;Control row;int budget;
        try{
            synchronized(controlGate){
                guard(effect);
                if(controlCount>=CONTROL_LIMIT||serial==Integer.MAX_VALUE)throw failure("cdp_control_limit",effect);
                row=new Control(++serial,null,clock.now(),resumed.receipt,null,ATTACH_DETACH,CONTROL_TIMEOUT,null,resumed.attachment);
                bytes=MdoBrowserCdpJson.encode(row.id,"Target.detachFromTarget",new JSONObject().put("sessionId",resumed.attachment.session),null);
                budget=remaining(activeStarted,activeTimeout,effect);
                int slot=0;while(controls[slot]!=null)slot++;controls[slot]=row;controlCount++;
            }
            try{wire.send(bytes,budget);}
            catch(IOException|RuntimeException|Error failure){row.receipt.finish(CONTROL_SEND_FAILED);controlFault="cdp_control_send_failed";close();throw failure(controlFault,effect);}
        }catch(org.json.JSONException failure){resumed.receipt.finish(CONTROL_CANCELLED);close();throw failure("cdp_control_event_invalid",effect);}
        catch(IOException|RuntimeException|Error failure){resumed.receipt.finish(CONTROL_CANCELLED);close();throw failure;}
        finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    }
    /** The snapshot was frozen before the event callback could modify JSON.
     * It contains no wire selector and cannot itself continue/claim a body. */
    MdoBrowserCdpResponse pausedResponse()throws IOException {
        guard(false);if(!busy||paused==null)throw failure("cdp_control_not_in_event",false);
        if(paused.request==null||!admitted(paused.session))throw new EventFailure("cdp_control_event_invalid");
        return paused.response;
    }
    Offer retainPausedResponse()throws IOException {
        guard(false);PausedEvent original=paused;
        if(!busy||original==null)throw failure("cdp_control_not_in_event",false);
        if(original.request==null||!admitted(original.session))throw new EventFailure("cdp_control_event_invalid");
        if(original.session!=null)throw new EventFailure("cdp_body_source_unsupported");
        if(!original.response.kind.equals("candidate"))throw new EventFailure("cdp_body_not_candidate");
        synchronized(controlGate){
            guard(false);if(original.used||controlKeys.contains(original.key))throw new EventFailure("cdp_control_duplicate");
            if(controlKeys.size()>=CONTROL_HISTORY)throw new EventFailure("cdp_control_limit");
            int slot=0;while(slot<offers.length&&offers[slot]!=null)slot++;if(slot==offers.length)throw new EventFailure("cdp_body_limit");
            Offer offer=new Offer(this,original,clock.now());controlKeys.add(original.key);original.used=true;offers[slot]=offer;return offer;
        }
    }
    /** Native retirement decision only. Fixed source cleanup still runs on
     * this dispatcher; complete source receipts are not revoked. */
    void cancelTransfers()throws Failure {
        guard(false);synchronized(controlGate){
            for(Offer offer:offers)if(offer!=null)offer.cancel();
            for(Body body:bodies)if(body!=null)body.cancel();
        }
    }
    int pendingOffers()throws Failure {
        if(Thread.currentThread()!=owner)throw failure("cdp_wrong_worker",false);
        synchronized(controlGate){int count=0;for(Offer offer:offers)if(offer!=null)count++;return count;}
    }
    private void finishOffer(Offer offer,boolean accepted,boolean aborted,String code){
        synchronized(controlGate){
            if(offer.outcome.get()!=null)return;OfferResult result;
            try{result=new OfferResult(offer,accepted,aborted,code);}catch(RuntimeException|Error failure){result=OFFER_UNKNOWN;}
            offer.outcome.compareAndSet(null,result);offer.original=null;
            for(int i=0;i<offers.length;i++)if(offers[i]==offer)offers[i]=null;
        }
    }
    private boolean serviceOffers(int timeoutMs)throws IOException {
        Offer selected=null;OfferDecision decision=null;
        synchronized(controlGate){
            long now=clock.now();for(int count=0;count<offers.length;count++){
                int index=offerCursor;offerCursor=(offerCursor+1)%offers.length;Offer offer=offers[index];if(offer==null||offer.issued)continue;
                long elapsed=now-offer.born;OfferDecision next=offer.decision.get();
                if(offer.cancelled.get()||elapsed<0||elapsed>=120000||next!=null){
                    selected=offer;
                    if(elapsed<0)offer.abortCode="cdp_offer_clock_invalid";
                    else if(elapsed>=120000)offer.abortCode="cdp_offer_expired";
                    else if(offer.cancelled.get())offer.abortCode="cdp_offer_cancelled";
                    else decision=next;break;
                }
            }
        }
        if(selected==null)return false;
        sendOffer(selected,decision,timeoutMs);return true;
    }
    private void sendOffer(Offer offer,OfferDecision decision,int timeoutMs)throws IOException {
        byte[] bytes=null;Control row=null;boolean attempted=false;busy=true;
        try{int left;synchronized(controlGate){
            guard(false);if(offer.owner!=this||offer.original==null||offer.issued)throw failure("cdp_offer_unavailable",false);
            if(serial==Integer.MAX_VALUE||controlCount>=CONTROL_LIMIT)throw failure("cdp_control_limit",false);
            long now=clock.now(),elapsed=now-offer.born;
            if(decision!=null&&(elapsed<0||elapsed>=120000)){offer.abortCode=elapsed<0?"cdp_offer_clock_invalid":"cdp_offer_expired";decision=null;}
            int bodySlot=0;while(bodySlot<bodies.length&&bodies[bodySlot]!=null)bodySlot++;
            if(decision!=null&&offer.cancelled.get()){offer.abortCode="cdp_offer_cancelled";decision=null;}
            if(decision!=null&&bodySlot==bodies.length){offer.abortCode="cdp_body_limit";decision=null;}
            PausedEvent original=offer.original;
            Body body=decision==null?null:new Body(this,original,now,decision.maximum,decision.timeout);
            JSONObject params=new JSONObject().put("requestId",original.request);if(body==null)params.put("errorReason","Aborted");
            ControlReceipt receipt=body==null?new ControlReceipt():body.claim;
            row=new Control(++serial,original.session,now,receipt,body,body==null?FLOW:CLAIM,CONTROL_TIMEOUT,offer);
            bytes=MdoBrowserCdpJson.encode(row.id,body==null?"Fetch.failRequest":"Fetch.takeResponseBodyAsStream",params,original.session);
            left=remaining(now,timeoutMs,false);int slot=0;while(controls[slot]!=null)slot++;
            controls[slot]=row;controlCount++;row.receipt.issued=true;offer.issued=true;
            if(body!=null){bodies[bodySlot]=body;body.claimMayHaveOccurred=true;offer.claimed=body;if(offer.cancelled.get())body.cancel();}
        }guard(false);attempted=true;wire.send(bytes,left);
        }catch(IOException|org.json.JSONException|RuntimeException|Error failure){
            if(row!=null)row.receipt.finish(CONTROL_SEND_FAILED);finishOffer(offer,false,false,"cdp_offer_dispatch_failed");close();throw failure("cdp_offer_dispatch_failed",attempted);
        }finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);busy=false;}
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
                ControlReceipt receipt=body==null?new ControlReceipt():body.claim;row=new Control(++serial,original.session,clock.now(),receipt,body,body==null?FLOW:CLAIM,CONTROL_TIMEOUT);
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
        expireNavigation(effect);
        boolean expired=false;
        try{synchronized(controlGate){
            if(controlCount==0)return;long now=clock.now();
            for(Control row:controls)if(row!=null){long used=now-row.born;if(used<0||used>=row.timeout){
                if(row.kind==BODY_READ&&used>=0){beginBodyCleanup(row.body,"cdp_body_timeout",now);continue;}
                row.receipt.finish(CONTROL_TIMEOUT_RESULT);expired=true;
                if(row.kind>=BODY_CLOSE){finishBody(row.body,false,"cdp_body_cleanup_unconfirmed");controlFault="cdp_body_cleanup_unconfirmed";}
            }}
        }}catch(RuntimeException|Error error){controlFault="cdp_control_clock_failed";close();throw failure(controlFault,effect);}
        if(expired){if(controlFault==null)controlFault="cdp_control_timeout";close();throw failure(controlFault,effect);}
    }
    private void expireNavigation(boolean effect)throws Failure {
        synchronized(controlGate){if(navigation==null)return;
            long elapsed=clock.now()-navigation.born;
            if(elapsed<0||elapsed>=navigation.timeout){navigation.finish(NAVIGATION_TIMED_OUT);close();throw failure("cdp_navigation_timeout",effect);}
        }
    }
    /** Fixed root-only native command. One asynchronous receipt leaves the
     * worker free to service paused offers/body cleanup before its reply.
     * The trusted physical binding supplies frame identity; never the caller. */
    Navigation navigateRoot(String url,String frame,int timeoutMs)throws IOException {
        guard(false);
        if(timeoutMs<100||timeoutMs>600000||!MdoBrowserCdpNetworkFrames.identity(frame)||!navigationUrl(url))throw failure("cdp_navigation_arguments_invalid",false);
        try{return navigationCommand("Page.navigate",new JSONObject().put("url",url).put("frameId",frame),frame,timeoutMs,false);}
        catch(org.json.JSONException invalid){throw failure("cdp_navigation_arguments_invalid",false);}
    }
    /** Native history selection supplies the entry; no public raw-ID route. */
    Navigation historyRoot(String frame,int entryId,int timeoutMs)throws IOException {
        guard(false);
        if(timeoutMs<100||timeoutMs>600000||!MdoBrowserCdpNetworkFrames.identity(frame))throw failure("cdp_navigation_arguments_invalid",false);
        try{return navigationCommand("Page.navigateToHistoryEntry",new JSONObject().put("entryId",entryId),frame,timeoutMs,true);}
        catch(org.json.JSONException invalid){throw failure("cdp_navigation_arguments_invalid",false);}
    }
    /** Keep the original loader precondition; never silently retry unguarded. */
    Navigation reloadRoot(String frame,String loader,int timeoutMs)throws IOException {
        guard(false);
        if(timeoutMs<100||timeoutMs>600000||!MdoBrowserCdpNetworkFrames.identity(frame)||!MdoBrowserCdpNetworkFrames.identity(loader))throw failure("cdp_navigation_arguments_invalid",false);
        try{return navigationCommand("Page.reload",new JSONObject().put("loaderId",loader),frame,timeoutMs,true);}
        catch(org.json.JSONException invalid){throw failure("cdp_navigation_arguments_invalid",false);}
    }
    private Navigation navigationCommand(String method,JSONObject params,String frame,int timeoutMs,boolean receiptOnly)throws IOException {
        enter(Math.min(5000,timeoutMs));byte[] bytes=null;Navigation ticket=null;boolean attempted=false;
        try{
            synchronized(controlGate){
                guard(false);if(navigation!=null)throw failure("cdp_navigation_pending",false);
                if(navigationCount>=4096)throw failure("cdp_navigation_limit",false);
                if(serial==Integer.MAX_VALUE){close();throw failure("cdp_id_exhausted",false);}
                int id=serial+1;
                bytes=MdoBrowserCdpJson.encode(id,method,params,null);
                ticket=new Navigation(id,frame,clock.now(),timeoutMs,receiptOnly);serial=id;navigationCount++;navigation=ticket;ticket.issued=true;attempted=true;
            }
            wire.send(bytes,Math.min(100,timeoutMs));guard(true);expireNavigation(true);return ticket;
        }catch(Failure failure){if(attempted){if(ticket!=null)ticket.finish(NAVIGATION_SEND_FAILED);close();}throw failure;}
        catch(IOException|RuntimeException|Error failure){if(attempted){if(ticket!=null)ticket.finish(NAVIGATION_SEND_FAILED);close();}
            throw failure(attempted?"cdp_navigation_send_failed":"cdp_navigation_dispatch_failed",attempted);
        }finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);busy=false;}
    }
    static boolean navigationUrl(String url){
        if(url==null||url.isEmpty()||url.length()>8192)return false;
        for(int i=0;i<url.length();i++){char c=url.charAt(i);if(c<=32||c==127)return false;
            if(Character.isHighSurrogate(c)){if(++i==url.length()||!Character.isLowSurrogate(url.charAt(i)))return false;}
            else if(Character.isLowSurrogate(c))return false;
        }
        if(url.equals("about:blank"))return true;
        try{java.net.URI parsed=new java.net.URI(url);String scheme=parsed.getScheme();
            return ("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))&&parsed.getHost()!=null&&parsed.getRawUserInfo()==null&&parsed.getPort()>=-1&&parsed.getPort()<=65535;
        }catch(java.net.URISyntaxException failure){return false;}
    }
    /** Private bounded destination equality; never retain or return URL text. */
    static byte[] navigationDigest(String url)throws IOException {
        if(!navigationUrl(url))throw new IOException("cdp_navigation_destination_invalid");
        byte[] bytes=url.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try{return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(java.security.NoSuchAlgorithmException failure){throw new IOException("cdp_navigation_digest_unavailable");}
        finally{Arrays.fill(bytes,(byte)0);}
    }
    static byte[] navigationFrameDigest(JSONObject root)throws IOException {
        if(root==null)throw new IOException("cdp_navigation_document_unproven");
        Object url=root.opt("url"),fragment=root.opt("urlFragment");
        if(!(url instanceof String)||root.has("urlFragment")&&(!(fragment instanceof String)||!((String)fragment).isEmpty()&&!((String)fragment).startsWith("#")||((String)url).contains("#")))
            throw new IOException("cdp_navigation_document_unproven");
        return navigationDigest((String)url+(fragment instanceof String?(String)fragment:""));
    }
    private boolean navigationReply(MdoBrowserCdpJson.Message message,boolean effect)throws Failure {
        synchronized(controlGate){
            Navigation original=navigation;if(original==null||message.id!=original.id)return false;
            if(message.session!=null){original.finish(NAVIGATION_REPLY_INVALID);close();throw failure("cdp_navigation_reply_mismatch",effect);}
            if(message.failed){original.finish(new NavigationResult(true,false,false,false,message.error==-32601?"cdp_navigation_unsupported":"cdp_navigation_rejected",message.error!=-32601));navigation=null;return true;}
            if(original.receiptOnly){
                if(message.value.length()!=0){original.finish(NAVIGATION_REPLY_INVALID);close();throw failure("cdp_navigation_reply_invalid",effect);}
                original.finish(new NavigationResult(true,true,false,false,null,true));navigation=null;return true;
            }
            JSONObject value=message.value;Object frame=value.opt("frameId"),loader=value.opt("loaderId"),download=value.opt("isDownload"),error=value.opt("errorText");
            if(!original.frame.equals(frame)||value.has("loaderId")&&!MdoBrowserCdpNetworkFrames.identity(loader)||
                value.has("isDownload")&&!(download instanceof Boolean)||value.has("errorText")&&(!(error instanceof String)||((String)error).length()>4096)){
                original.finish(NAVIGATION_REPLY_INVALID);close();throw failure("cdp_navigation_reply_invalid",effect);
            }
            boolean known=download instanceof Boolean,isDownload=Boolean.TRUE.equals(download);
            boolean failed=error instanceof String&&!((String)error).isEmpty()&&!(isDownload&&error.equals("net::ERR_ABORTED"));
            String code=failed?(!known&&"net::ERR_ABORTED".equals(error)?"cdp_navigation_download_unproven":"cdp_navigation_failed"):null;
            if(!failed&&loader instanceof String)original.loader=(String)loader;
            original.finish(new NavigationResult(true,!failed,known,isDownload,code,true,navigationError(error),value.has("loaderId")));navigation=null;return true;
        }
    }
    private static String navigationError(Object error){
        // Keep precise known network causes, never arbitrary provider prose,
        // URL-bearing messages or unrecognized future protocol text.
        if(!(error instanceof String))return null;
        switch((String)error){
            case "net::ERR_ABORTED":case "net::ERR_NAME_NOT_RESOLVED":case "net::ERR_NAME_RESOLUTION_FAILED":
            case "net::ERR_CONNECTION_REFUSED":case "net::ERR_CONNECTION_RESET":case "net::ERR_CONNECTION_TIMED_OUT":case "net::ERR_TIMED_OUT":
            case "net::ERR_INTERNET_DISCONNECTED":case "net::ERR_ADDRESS_UNREACHABLE":case "net::ERR_TUNNEL_CONNECTION_FAILED":
            case "net::ERR_SSL_PROTOCOL_ERROR":case "net::ERR_CERT_AUTHORITY_INVALID":case "net::ERR_CERT_DATE_INVALID":case "net::ERR_CERT_COMMON_NAME_INVALID":case "net::ERR_CERT_REVOKED":
            case "net::ERR_TOO_MANY_REDIRECTS":case "net::ERR_BLOCKED_BY_CLIENT":case "net::ERR_BLOCKED_BY_RESPONSE":case "net::ERR_INVALID_URL":case "net::ERR_UNKNOWN_URL_SCHEME":
            case "net::ERR_HTTP_RESPONSE_CODE_FAILURE":case "net::ERR_CONTENT_LENGTH_MISMATCH":return (String)error;
            default:return null;
        }
    }
    private boolean controlReply(MdoBrowserCdpJson.Message message,boolean effect)throws IOException {
        ControlResult result=null;Control resumed=null;
        synchronized(controlGate){
            guard(effect);
            if(retiredBodyReads.contains(message.id)){
                if(message.session!=null){controlFault="cdp_body_retired_reply_mismatch";close();throw failure(controlFault,effect);}
                retiredBodyReads.remove(message.id);return true;
            }
            for(int i=0;i<controls.length;i++){
                Control row=controls[i];if(row==null||row.id!=message.id)continue;
                if(!Objects.equals(row.session,message.session)){
                    result=new ControlResult(false,"cdp_control_reply_mismatch",0,true);
                }else result=new ControlResult(!message.failed,message.failed?(message.error==-32601?"cdp_control_method_unsupported":"cdp_control_rejected"):null,
                        message.failed?message.error:0,!message.failed||message.error!=-32601);
                if((row.kind==ATTACH_RESUME||row.kind==ATTACH_DETACH)&&result.ok&&message.value.length()!=0)result=new ControlResult(false,"cdp_control_reply_invalid",0,true);
                // The first effect was positively acknowledged. A rejected
                // detach cannot downgrade that fact to "nothing happened".
                if(row.kind==ATTACH_DETACH&&row.attachment.resumed)result=new ControlResult(result.ok,result.code,result.protocolCode,true);
                if(row.kind==ATTACH_RESUME&&result.ok){
                    controls[i]=null;controlCount--;row.attachment.resumed=true;resumed=row;break;
                }
                if(row.kind==ATTACH_DETACH&&result.ok){row.attachment.detached=true;children.remove(row.attachment.session);}
                if(row.kind>=BODY_READ&&Objects.equals(row.session,message.session)){
                    controls[i]=null;controlCount--;row.receipt.finish(result);
                    bodyReply(row,message,result,effect);return true;
                }
                if(row.kind==CLAIM){
                    Body body=row.body;
                    if(result.ok){Object stream=message.value.opt("stream");
                        if(message.value.length()!=1||!(stream instanceof String)||!streamHandle((String)stream))
                            result=new ControlResult(false,"cdp_body_claim_reply_invalid",0,true);
                        else if(!streamIds.add((String)stream))result=new ControlResult(false,"cdp_body_stream_reused",0,true);
                        else body.stream=(String)stream;
                    }
                    if(!result.ok){if(result.protocolCode==-32601)body.claimMayHaveOccurred=false;finishBody(body,false,result.code);}
                }
                if(row.offer!=null&&row.kind==FLOW&&result.ok&&message.value.length()!=0)result=new ControlResult(false,"cdp_control_reply_invalid",0,true);
                row.receipt.finish(result);
                if(row.offer!=null)finishOffer(row.offer,row.kind==CLAIM&&result.ok,row.kind==FLOW&&result.ok,
                    result.ok?(row.kind==CLAIM?(row.offer.cancelled.get()?"cdp_offer_cancelled":null):row.offer.abortCode):result.code);
                controls[i]=null;controlCount--;break;
            }
        }
        if(resumed!=null){detachResumedTarget(resumed,effect);return true;}
        if(result==null)return false;
        if(!result.ok){controlFault=result.code;close();throw new Failure(result.code,result.protocolCode,effect);}
        return true;
    }
    int pendingControls()throws Failure {
        if(Thread.currentThread()!=owner)throw failure("cdp_wrong_worker",false);
        synchronized(controlGate){return controlCount+(navigation==null?0:1);}
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
            if(body.cancelled.get()){success=false;if(code==null||"cdp_body_connection_closed".equals(code))code="cdp_body_cancelled";}
            if(body.chunk!=null){body.chunk.close();body.chunk=null;}
            BodyResult result;try{result=new BodyResult(success,code,body);}catch(RuntimeException|Error unavailable){result=BODY_UNKNOWN;}
            body.outcome.compareAndSet(null,result);body.stream=null;body.original=null;
            for(int i=0;i<bodies.length;i++)if(bodies[i]==body)bodies[i]=null;
        }
    }
    private void beginBodyCleanup(Body body,String code,long now){
        if(body.outcome.get()!=null||body.cleaning)return;
        body.cleaning=true;body.cleanupBorn=now;body.cleanupCode=code;body.readRequested=false;
        if(body.chunk!=null){body.chunk.close();body.chunk=null;}
    }
    private void acceptBodyData(Body body,JSONObject reply)throws IOException {
        byte[] data=null;
        try{
            data=bodyData(reply,body.readSize);boolean binary=Boolean.TRUE.equals(reply.opt("base64Encoded"));
            // Pinned Chromium's Read() fast EOF path returns false here even
            // for a binary stream. Only this payload-free terminal exception
            // can inherit the previously proven binary mode.
            boolean emptyBinaryEof=body.encodingKnown&&body.binary&&!binary&&data.length==0&&reply.optBoolean("eof",false);
            if(body.encodingKnown&&body.binary!=binary&&!emptyBinaryEof)throw new IOException("cdp_body_encoding_changed");
            binary|=emptyBinaryEof;
            body.encodingKnown=true;body.binary=binary;
            if(!binary&&!body.original.response.exactTextLength)throw new IOException("cdp_body_text_integrity_unavailable");
            if(data.length>body.limit-body.received)throw new IOException("cdp_body_limit");
            body.received+=data.length;body.eof=reply.optBoolean("eof",false);
            if(body.eof&&!binary&&body.received!=body.original.response.declaredLength)throw new IOException("cdp_body_length_mismatch");
            body.chunk=new BodyChunk(body,data,body.eof);data=null;
        }finally{if(data!=null)Arrays.fill(data,(byte)0);}
    }
    private void bodyReply(Control row,MdoBrowserCdpJson.Message message,ControlResult result,boolean effect)throws Failure {
        Body body=row.body;
        if(body.outcome.get()!=null){controlFault="cdp_body_reply_after_terminal";close();throw failure(controlFault,effect);}
        if(row.kind==BODY_READ){
            body.readPending=false;
            if(!body.cleaning&&!body.cancelled.get()){
                if(!result.ok)beginBodyCleanup(body,result.protocolCode==-32601?"cdp_body_read_unsupported":"cdp_body_read_rejected",clock.now());
                else try{acceptBodyData(body,message.value);}
                catch(IOException failure){String code=failure.getMessage();
                    if(code.equals("cdp_body_text_integrity_unavailable")||code.equals("cdp_body_length_mismatch")||code.equals("cdp_body_limit"))beginBodyCleanup(body,code,clock.now());
                    else{finishBody(body,false,code);controlFault=code;close();throw failure(code,effect);}
                }
            }
        }else{
            if(row.kind==BODY_CLOSE)body.closePending=false;else body.abortPending=false;
            if(!result.ok||message.value.length()!=0){finishBody(body,false,"cdp_body_cleanup_unconfirmed");controlFault="cdp_body_cleanup_unconfirmed";close();throw failure(controlFault,effect);}
            if(row.kind==BODY_CLOSE)body.streamClosed=true;else body.requestAborted=true;
        }
        if(body.cleaning&&body.streamClosed&&body.requestAborted){
            if(body.readPending)retireBodyRead(body);
            finishBody(body,body.cleanupCode==null,body.cleanupCode);
        }
    }
    /** Both exact cleanup ACKs already arrived. Retire the one outstanding read
     * association without waiting for a callback the closed provider may drop.
     * At most one such row per body; claims share the 4096 lifetime-key bound. */
    private void retireBodyRead(Body body)throws Failure {
        for(int i=0;i<controls.length;i++){
            Control row=controls[i];if(row==null||row.body!=body||row.kind!=BODY_READ)continue;
            if(row.session!=null||retiredBodyReads.size()>=CONTROL_HISTORY){controlFault="cdp_body_retired_read_limit";close();throw failure(controlFault,true);}
            retiredBodyReads.add(row.id);row.receipt.finish(CONTROL_CANCELLED);controls[i]=null;controlCount--;body.readPending=false;return;
        }
        controlFault="cdp_body_read_row_missing";close();throw failure(controlFault,true);
    }
    /** Request/consume at most one chunk. Neither call performs socket I/O.
     * Native consumers must wait for BodyResult as well as final chunk EOF. */
    void requestBodyRead(Body body)throws IOException {
        bodyGuard(body);synchronized(controlGate){
            if(body.cancelled.get())throw failure("cdp_body_cancelled",false);
            if(body.cleaning||body.eof)throw failure("cdp_body_finishing",false);
            if(body.readRequested||body.readPending||body.chunk!=null)throw failure("cdp_body_read_pending",false);
            body.readRequested=true;
        }
    }
    BodyChunk takeBodyChunk(Body body)throws IOException {
        guard(false);if(body==null||body.owner!=this)throw failure("cdp_body_owner_mismatch",false);
        if(busy)throw failure("cdp_session_busy",false);
        synchronized(controlGate){
            guard(false);BodyResult result=body.outcome.get();
            if(result!=null){if(!result.ok)throw failure(result.code,result.claimMayHaveOccurred);return null;}
            if(body.cancelled.get()||body.cleaning&&body.cleanupCode!=null)return null;
            BodyChunk chunk=body.chunk;body.chunk=null;return chunk;
        }
    }
    /** One bounded send per turn, no response wait. A stalled read remains
     * associated with its exact row while other connections/jobs can progress. */
    boolean serviceBodies(int sendTimeoutMs)throws IOException {
        guard(false);if(busy)throw failure("cdp_session_busy",false);
        if(sendTimeoutMs<10||sendTimeoutMs>5000)throw failure("cdp_body_arguments_invalid",false);
        try{if(serviceOffers(sendTimeoutMs))return true;Body selected=null;int kind=0;synchronized(controlGate){
            long now=clock.now();
            for(Body body:bodies)if(body!=null){
                long used=now-body.born;
                if(used<0){finishBody(body,false,"cdp_body_clock_invalid");close();throw failure("cdp_body_clock_invalid",true);}
                if(body.cancelled.get())beginBodyCleanup(body,"cdp_body_cancelled",now);
                else if(used>body.timeout-10)beginBodyCleanup(body,"cdp_body_timeout",now);
                else if(body.eof&&body.chunk==null&&!body.readPending)beginBodyCleanup(body,null,now);
                if(body.cleaning&&(now<body.cleanupBorn||now-body.cleanupBorn>=5000)){
                    finishBody(body,false,"cdp_body_cleanup_unconfirmed");close();throw failure("cdp_body_cleanup_unconfirmed",true);
                }
            }
            for(int n=0;n<bodies.length;n++){
                int index=bodyCursor;bodyCursor=(bodyCursor+1)%bodies.length;Body body=bodies[index];if(body==null||body.stream==null)continue;
                if(body.cleaning){
                    if(!body.streamClosed&&!body.closePending){selected=body;kind=BODY_CLOSE;break;}
                    if(body.streamClosed&&!body.requestAborted&&!body.abortPending){selected=body;kind=BODY_ABORT;break;}
                }else if(body.readRequested&&!body.readPending&&body.chunk==null){selected=body;kind=BODY_READ;break;}
            }
        }if(selected==null)return false;sendBody(selected,kind,sendTimeoutMs);return true;}
        catch(Failure failure){throw failure;}
        catch(IOException|RuntimeException|Error failure){close();throw failure("cdp_body_service_failed",true);}
    }
    private void sendBody(Body body,int kind,int timeoutMs)throws IOException {
        byte[] bytes=null;Control row=null;boolean attempted=false;busy=true;
        try{int left;synchronized(controlGate){
            guard(false);if(serial==Integer.MAX_VALUE||controlCount>=CONTROL_LIMIT)throw failure("cdp_body_control_limit",false);
            if(kind==BODY_READ&&body.cancelled.get()){beginBodyCleanup(body,"cdp_body_cancelled",clock.now());return;}
            long now=clock.now();int ttl=kind==BODY_READ?bodyRemaining(body,body.timeout):(int)(5000-(now-body.cleanupBorn));
            if(ttl<10)throw failure("cdp_body_cleanup_unconfirmed",false);
            JSONObject params=new JSONObject();String method;
            if(kind==BODY_READ){body.readSize=(int)Math.min(65536,Math.max(1,body.limit-body.received));params.put("handle",body.stream).put("size",body.readSize);method="IO.read";}
            else if(kind==BODY_CLOSE){params.put("handle",body.stream);method="IO.close";}
            else{params.put("requestId",body.original.request).put("errorReason","Aborted");method="Fetch.failRequest";}
            row=new Control(++serial,body.original.session,now,new ControlReceipt(),body,kind,ttl);
            bytes=MdoBrowserCdpJson.encode(row.id,method,params,row.session);
            left=remaining(now,Math.min(ttl,timeoutMs),false);
            int slot=0;while(controls[slot]!=null)slot++;controls[slot]=row;controlCount++;row.receipt.issued=true;
            if(kind==BODY_READ){body.readPending=true;body.readRequested=false;}
            else if(kind==BODY_CLOSE)body.closePending=true;else body.abortPending=true;
        }guard(false);attempted=true;wire.send(bytes,left);
        }catch(IOException|org.json.JSONException|RuntimeException|Error failure){
            String code=kind==BODY_READ?(failure instanceof IOException?"cdp_transport_failed":"cdp_dispatch_failed"):"cdp_body_cleanup_unconfirmed";
            if(row!=null)row.receipt.finish(CONTROL_SEND_FAILED);finishBody(body,false,code);close();throw failure(code,attempted);
        }finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);busy=false;}
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
    /** Convenience drain on top of the asynchronous machine. Product pumps use
     * requestBodyRead/takeBodyChunk/serviceBodies and never wait in this loop. */
    BodyChunk readBody(Body body,int timeoutMs)throws IOException {
        if(timeoutMs<10||timeoutMs>5000)throw failure("cdp_body_arguments_invalid",false);
        bodyGuard(body);BodyChunk chunk=null;
        try{
            if(body.cancelled.get()){cancelBody(body,5000);throw failure("cdp_body_cancelled",false);}
            requestBodyRead(body);long started=clock.now();
            for(;;){
                chunk=takeBodyChunk(body);if(chunk!=null)break;
                bodyStep(body,started,timeoutMs,false);
            }
            if(chunk.eof){BodyResult result=awaitBody(body,5000);if(!result.ok)throw failure(result.code,result.claimMayHaveOccurred);}
            guard(true);BodyChunk valid=chunk;chunk=null;return valid;
        }finally{if(chunk!=null)chunk.close();}
    }
    void cancelBody(Body body,int timeoutMs)throws IOException {
        if(timeoutMs<10||timeoutMs>5000)throw failure("cdp_body_arguments_invalid",false);
        bodyGuard(body);body.cancel();BodyResult result=awaitBody(body,timeoutMs);
        if(!result.streamCloseConfirmed||!result.requestAbortConfirmed||!"cdp_body_cancelled".equals(result.code))
            throw failure(result.code==null?"cdp_body_cleanup_unconfirmed":result.code,result.claimMayHaveOccurred);
    }
    private BodyResult awaitBody(Body body,int timeoutMs)throws IOException {
        long started=clock.now();BodyResult result;
        while((result=body.outcome.get())==null)bodyStep(body,started,timeoutMs,true);return result;
    }
    private int bodyStepRemaining(Body body,long started,int timeout,boolean cleanup)throws Failure {
        long used=clock.now()-started;
        if(used<0||used>timeout-10){String code=cleanup?"cdp_body_cleanup_unconfirmed":"cdp_command_timeout";
            finishBody(body,false,code);close();throw failure(code,true);}
        return timeout-(int)used;
    }
    private void bodyStep(Body body,long started,int timeout,boolean cleanup)throws IOException {
        int left=bodyStepRemaining(body,started,timeout,cleanup);serviceBodies(Math.min(100,left));
        if(body.outcome.get()!=null||!cleanup&&body.chunk!=null)return;
        left=bodyStepRemaining(body,started,timeout,cleanup);poll(Math.min(20,left),Math.min(250,left));
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
                if(navigationReply(message,effect)||controlReply(message,effect))continue;
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
            if(!message.event()){if(navigationReply(message,false)||controlReply(message,false))return true;throw failure("cdp_reply_mismatch",false);}
            event(message,false);remaining(started,timeoutMs,false);return true;
        } catch(Failure failure){close();throw failure;}
        catch(IOException|RuntimeException|Error failure){close();throw failure("cdp_event_stream_failed",false);}
        finally {if(bytes!=null)Arrays.fill(bytes,(byte)0);busy=false;}
    }
    @Override public void close() {
        boolean first;
        synchronized(controlGate){
            first=closed.compareAndSet(false,true);
            if(navigation!=null){navigation.finish(navigation.issued?NAVIGATION_CLOSED:NAVIGATION_NOT_SENT);navigation=null;}
            for(int i=0;i<controls.length;i++)if(controls[i]!=null){controls[i].receipt.finish(CONTROL_CANCELLED);controls[i]=null;}
            controlCount=0;controlKeys.clear();
            for(Offer offer:offers)if(offer!=null)finishOffer(offer,false,false,"cdp_offer_connection_closed");
            for(Body body:bodies)if(body!=null)finishBody(body,false,"cdp_body_connection_closed");streamIds.clear();retiredBodyReads.clear();
        }
        if(first){Wire connection=wire;if(connection!=null)connection.close();}
    }
}
