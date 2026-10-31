package org.xserver.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One process-wide browser attachment controller on the existing CDP worker.
 * A related candidate is only held metadata, never native child/transport proof.
 * No page data, Fetch setup, debugger resume of a managed child or Agent code
 * is exposed here. The host must service turns and join native popup adoption. */
final class MdoBrowserCdpPopupBroker implements AutoCloseable {
    interface Connector {MdoBrowserCdpSession open(int timeout,BooleanSupplier current,MdoBrowserCdpSession.Events events)throws IOException;}
    private static final Object UNCERTAIN=new Object();
    private static final AtomicReference<Object> PROCESS=new AtomicReference<>();
    private static final int NEW=0,STARTING=1,ARMED=2,STOPPING=3,STOPPED=4,FAILED=5;
    private static final Connector NATIVE=(timeout,current,events)->{
        long born=android.os.SystemClock.elapsedRealtime();MdoBrowserCdpNative.BrowserEndpoint endpoint=MdoBrowserCdpNative.browserEndpoint(timeout);
        long used=android.os.SystemClock.elapsedRealtime()-born;
        if(used<0||used>timeout-10)throw new IOException("cdp_popup_broker_timeout");
        return MdoBrowserCdpSession.openBrowser(endpoint,timeout-(int)used,current,events);
    };
    static final class Registration implements AutoCloseable {
        private final MdoBrowserCdpBinding.Network.PopupSource source;private final String target;
        private final AtomicBoolean active=new AtomicBoolean(true);
        private Registration(MdoBrowserCdpBinding.Network.PopupSource source,String target){this.source=source;this.target=target;}
        boolean current(){return active.get()&&source.current();}
        @Override public void close(){active.set(false);}
    }
    static final class Candidate {
        final String id="popup-candidate-"+UUID.randomUUID();
        private final Registration opener;private final MdoBrowserCdpSession.AttachedTarget original;private final long born;private final AtomicBoolean brokerClosed;
        private volatile boolean retired;
        private Candidate(Registration opener,MdoBrowserCdpSession.AttachedTarget original,long born,AtomicBoolean brokerClosed){this.opener=opener;this.original=original;this.born=born;this.brokerClosed=brokerClosed;}
        boolean current(){return !retired&&!brokerClosed.get()&&opener.current();}
        boolean nativeChildProven(){return false;}
    }
    private static final class Release {
        final MdoBrowserCdpSession.ControlReceipt receipt;
        Release(MdoBrowserCdpSession.ControlReceipt receipt){this.receipt=receipt;}
    }
    private final Thread worker=Thread.currentThread();private final BooleanSupplier hostCurrent;private final LongSupplier clock;private final Connector connector;
    private final List<Registration> registrations=new ArrayList<>();private final List<Candidate> candidates=new ArrayList<>();
    private final List<Release> releases=new ArrayList<>();private final Set<String> seenTargets=new HashSet<>();
    private final AtomicBoolean closed=new AtomicBoolean();private volatile MdoBrowserCdpSession session;
    private volatile int state=NEW;private volatile boolean enabledMayHaveOccurred,cleanupConfirmed=true;
    private volatile String failure;private boolean attachedSeen;
    MdoBrowserCdpPopupBroker(BooleanSupplier hostCurrent){this(hostCurrent,android.os.SystemClock::elapsedRealtime,NATIVE);}
    MdoBrowserCdpPopupBroker(BooleanSupplier hostCurrent,LongSupplier clock,Connector connector){
        if(hostCurrent==null||clock==null||connector==null)throw new IllegalArgumentException("Popup broker requires its original worker");
        this.hostCurrent=hostCurrent;this.clock=clock;this.connector=connector;
    }
    private static IOException problem(String code){return new IOException(code);}
    private void thread()throws IOException{if(Thread.currentThread()!=worker)throw problem("cdp_wrong_worker");}
    private boolean current(){try{return !closed.get()&&hostCurrent.getAsBoolean();}catch(RuntimeException|Error error){return false;}}
    private void guard()throws IOException {
        thread();if(closed.get())throw problem(failure==null?"cdp_popup_broker_closed":failure);
        if(!current())throw fail("cdp_popup_broker_owner_changed");
    }
    private long now()throws IOException{try{long value=clock.getAsLong();if(value<0)throw fail("cdp_popup_broker_clock_invalid");return value;}catch(RuntimeException|Error error){throw fail("cdp_popup_broker_clock_invalid");}}
    private int remaining(long born,int timeout)throws IOException{
        guard();long used=now()-born;if(used<0||used>timeout-10)throw fail("cdp_popup_broker_timeout");return timeout-(int)used;
    }
    Registration register(MdoBrowserCdpBinding.Network owner)throws IOException {
        guard();if(state!=NEW&&state!=ARMED)throw problem("cdp_popup_broker_state_invalid");
        if(owner==null)throw problem("cdp_popup_opener_invalid");prune();
        MdoBrowserCdpBinding.Network.PopupSource source=owner.popupSource();String target=source.target();
        for(Registration original:registrations)if(original.source==source||original.target.equals(target))throw problem("cdp_popup_opener_busy");
        if(registrations.size()>=8)throw problem("cdp_popup_opener_limit");Registration added=new Registration(source,target);registrations.add(added);return added;
    }
    void arm(int timeout)throws IOException {
        guard();if(state!=NEW)throw problem("cdp_popup_broker_state_invalid");
        if(timeout<10||timeout>5000)throw problem("cdp_arguments_invalid");prune();if(registrations.isEmpty())throw problem("cdp_popup_opener_unavailable");
        if(!PROCESS.compareAndSet(null,this))throw problem(PROCESS.get()==UNCERTAIN?"cdp_popup_cleanup_unconfirmed":"cdp_popup_broker_process_busy");
        state=STARTING;long born=now();
        try{
            session=connector.open(remaining(born,timeout),this::current,this::event);guard();
            if(session==null)throw problem("cdp_popup_broker_connection_invalid");prune();if(registrations.isEmpty())throw problem("cdp_popup_opener_unavailable");
            JSONObject params=new JSONObject().put("autoAttach",true).put("waitForDebuggerOnStart",true).put("flatten",true)
                .put("filter",new JSONArray().put(new JSONObject().put("type","page").put("exclude",false)).put(new JSONObject().put("exclude",true)));
            cleanupConfirmed=false;enabledMayHaveOccurred=true;
            JSONObject reply=session.call("Target.setAutoAttach",params,null,remaining(born,timeout),true);
            if(reply.length()!=0)throw problem("cdp_popup_broker_reply_invalid");
            drain(born,timeout);remaining(born,timeout);prune();state=ARMED;
            if(registrations.isEmpty()){disarm(remaining(born,timeout));throw problem("cdp_popup_opener_unavailable");}
        }catch(MdoBrowserCdpSession.Failure error){
            if(error.protocolCode==-32601&&"cdp_method_unsupported".equals(error.getMessage())&&!attachedSeen){enabledMayHaveOccurred=false;cleanupConfirmed=true;}
            throw fail(safe(error.getMessage()));
        }catch(JSONException error){throw fail("cdp_popup_broker_arguments_invalid");}
        catch(IOException error){throw fail(safe(error.getMessage()));}
        catch(RuntimeException|Error error){throw fail("cdp_popup_broker_failed");}
    }
    private static String safe(String code){return code!=null&&code.matches("cdp_[a-z_]{1,80}")?code:"cdp_popup_broker_failed";}
    private void event(String method,String child,JSONObject params)throws IOException {
        guard();if(session==null)throw fail("cdp_popup_broker_connection_invalid");
        if("Target.attachedToTarget".equals(method)){
            attachedSeen=true;MdoBrowserCdpSession.AttachedTarget original=session.attachedTarget();
            if(state==STOPPING){session.ignoreAttachedTargetDuringDisable();return;}
            if(seenTargets.size()>=256||!seenTargets.add(original.target))throw fail("cdp_popup_broker_target_limit");
            Registration opener=null;
            if(state!=STOPPING&&original.waiting&&"page".equals(original.type)&&original.opener!=null)
                for(Registration item:registrations)if(item.target.equals(original.opener)&&item.current()){opener=item;break;}
            if(opener==null){
                if(releases.size()>=64)throw fail("cdp_popup_broker_release_limit");
                releases.add(new Release(session.releaseUnclaimedAttachedTarget()));return;
            }
            if(candidates.size()>=7)throw fail("cdp_popup_broker_candidate_limit");
            MdoBrowserCdpSession.AttachedTarget held=session.retainAttachedTarget();candidates.add(new Candidate(opener,held,now(),closed));return;
        }
        if("Target.detachedFromTarget".equals(method)&&child==null){
            Object id=params.opt("sessionId");
            for(int i=candidates.size()-1;i>=0;i--){Candidate candidate=candidates.get(i);if(candidate.original.matchesNativeSession(id)){
                candidate.retired=true;session.retireAttachedTarget(candidate.original);candidates.remove(i);
            }}return;
        }
        if(child!=null)throw fail("cdp_popup_broker_child_unproven");
        if(method.startsWith("Fetch."))throw fail("cdp_popup_broker_response_unproven");
    }
    private void prune()throws IOException{
        long time=now();for(Candidate candidate:candidates){long age=time-candidate.born;
            if(!candidate.current())throw fail("cdp_popup_broker_opener_lost");
            if(age<0||age>=15000)throw fail("cdp_popup_broker_candidate_expired");
        }
        for(int i=registrations.size()-1;i>=0;i--)if(!registrations.get(i).current())registrations.remove(i);
        for(int i=releases.size()-1;i>=0;i--){MdoBrowserCdpSession.ControlResult result=releases.get(i).receipt.result();
            if(result!=null){if(!result.ok)throw fail(result.code==null?"cdp_popup_broker_release_failed":safe(result.code));releases.remove(i);}
        }
    }
    private void drain(long born,int timeout)throws IOException{
        while(session.pendingControls()!=0){int left=remaining(born,timeout);session.poll(Math.min(20,left),left);prune();}prune();
    }
    boolean turn(int receiveMs,int timeout)throws IOException {
        guard();if(state!=ARMED)throw problem("cdp_popup_broker_state_invalid");
        if(receiveMs<10||receiveMs>20||timeout<receiveMs||timeout>5000)throw problem("cdp_arguments_invalid");
        try{prune();if(registrations.isEmpty()){disarm(timeout);return true;}if(!session.idle())return false;
            boolean progress=session.poll(receiveMs,timeout);prune();return progress;
        }catch(IOException error){throw fail(safe(error.getMessage()));}
        catch(RuntimeException|Error error){throw fail("cdp_popup_broker_failed");}
    }
    List<Candidate> pending()throws IOException{guard();prune();return new ArrayList<>(candidates);}
    void disarm(int timeout)throws IOException {
        guard();if(state!=ARMED)throw problem("cdp_popup_broker_state_invalid");
        if(timeout<10||timeout>5000)throw problem("cdp_arguments_invalid");prune();
        // The native popup owner must first cancel/adopt/retire these exact
        // candidates. Silently detaching would release an unproven response.
        if(!candidates.isEmpty())throw problem("cdp_popup_broker_candidates_pending");
        long born=now();
        try{drain(born,timeout);}catch(IOException error){throw fail(safe(error.getMessage()));}
        catch(RuntimeException|Error error){throw fail("cdp_popup_broker_failed");}
        if(!candidates.isEmpty())throw problem("cdp_popup_broker_candidates_pending");state=STOPPING;
        try{
            JSONObject reply=session.call("Target.setAutoAttach",new JSONObject().put("autoAttach",false).put("waitForDebuggerOnStart",false).put("flatten",true),null,remaining(born,timeout),true);
            if(reply.length()!=0)throw problem("cdp_popup_broker_reply_invalid");remaining(born,timeout);
            cleanupConfirmed=true;state=STOPPED;closed.set(true);
            try{session.close();}finally{PROCESS.compareAndSet(this,null);clear();}
        }catch(JSONException error){throw fail("cdp_popup_broker_arguments_invalid");}
        catch(IOException error){throw fail(safe(error.getMessage()));}
        catch(RuntimeException|Error error){throw fail("cdp_popup_broker_failed");}
    }
    boolean armed(){return state==ARMED&&!closed.get();}
    boolean cleanupConfirmed(){return cleanupConfirmed;}
    boolean enabledMayHaveOccurred(){return enabledMayHaveOccurred;}
    private IOException fail(String code){if(failure==null)failure=safe(code);state=FAILED;try{close();}finally{if(Thread.currentThread()==worker)clear();}return problem(failure);}
    private void clear(){for(Candidate candidate:candidates)candidate.retired=true;for(Registration registration:registrations)registration.close();candidates.clear();registrations.clear();releases.clear();seenTargets.clear();}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        // A socket close wakes the worker but is not an acknowledgement that
        // Chromium has removed its process-wide pause. Keep a small fence,
        // not this broker/its owners, until the process itself is replaced.
        PROCESS.compareAndSet(this,cleanupConfirmed?null:UNCERTAIN);
        MdoBrowserCdpSession original=session;if(original!=null)try{original.close();}catch(RuntimeException|Error error){if(failure==null)failure="cdp_popup_broker_close_failed";state=FAILED;}
    }
}
