package org.xserver.android;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** One protocol dispatcher on its owning native worker, never on Android UI.
 * One outstanding RPC bounds state without a second asynchronous request queue.
 * Events are delivered inline to the native controller, which may update owned
 * protocol state but may not reenter I/O. UI delivery belongs to that controller.
 * No implicit reconnect/replay. A timed-out/mismatched stream is retired. */
final class MdoBrowserCdpSession implements AutoCloseable {
    interface Wire extends AutoCloseable {
        void send(byte[] bytes,int timeoutMs) throws IOException;
        byte[] receive(int timeoutMs) throws IOException;
        @Override void close();
    }
    interface Clock {long now();}
    interface Events {void event(String method,String session,JSONObject params) throws IOException;}
    /** Only explicit native event diagnoses may cross the event callback.
     * Ordinary exception/provider messages keep the generic sanitized result. */
    static final class EventFailure extends IOException {
        final String code;
        static boolean allowed(String code){return Arrays.asList("cdp_chooser_arming_event","cdp_chooser_ambiguous","cdp_chooser_metadata_invalid",
            "cdp_chooser_clock_invalid","cdp_chooser_frame_unproven","cdp_chooser_frame_unavailable").contains(code);}
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
    void retireChild(String child) throws Failure {guard(false);children.remove(child);}
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
        try {events.event(message.method,message.session,message.value);}
        catch(EventFailure failure){close();throw failure(failure.code,effect);}
        catch(IOException|RuntimeException|Error failure){close();throw failure("cdp_event_failed",effect);}
        guard(effect);
    }
    /** Serialized RPC. A sent effect may have occurred even if its reply is
     * lost. Failure never retries. Rejected caller arguments leave I/O alone. */
    JSONObject call(String method,JSONObject params,String session,int timeoutMs,boolean effect) throws IOException {
        enter(timeoutMs);byte[] outgoing=null;boolean attempted=false,replied=false;
        try {
            long started=clock.now();int packetsSeen=0;
            if(!admitted(session))throw failure("cdp_session_invalid",false);
            if(serial==Integer.MAX_VALUE){close();throw failure("cdp_id_exhausted",false);}
            outgoing=MdoBrowserCdpJson.encode(++serial,method,params,session);
            int budget=remaining(started,timeoutMs,false);attempted=true;wire.send(outgoing,budget);
            Arrays.fill(outgoing,(byte)0);outgoing=null;
            for(;;) {
                budget=remaining(started,timeoutMs,effect);
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
                if(message.id!=serial||!Objects.equals(message.session,session))throw failure("cdp_reply_mismatch",effect);
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
    /** Read one unsolicited event on this worker. No pending-reply queue. */
    boolean poll(int timeoutMs) throws IOException {
        enter(timeoutMs);byte[] bytes=null;
        try {
            long started=clock.now();bytes=wire.receive(timeoutMs);guard(false);if(bytes==null)return false;
            // An ordinary idle poll is not a broken stream. A packet delivered
            // after the original poll budget must not reach native event state.
            remaining(started,timeoutMs,false);
            MdoBrowserCdpJson.Message message=packet(bytes,false);
            bytes=null;
            if(message==null)return false;
            if(!message.event())throw failure("cdp_reply_mismatch",false);
            event(message,false);return true;
        } catch(Failure failure){close();throw failure;}
        catch(IOException|RuntimeException|Error failure){close();throw failure("cdp_event_stream_failed",false);}
        finally {if(bytes!=null)Arrays.fill(bytes,(byte)0);busy=false;}
    }
    @Override public void close() {
        if(closed.compareAndSet(false,true)) {Wire connection=wire;if(connection!=null)connection.close();}
    }
}
