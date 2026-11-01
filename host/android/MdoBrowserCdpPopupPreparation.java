package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;

/** UI-owned preparation for one exact physical opener/document. The native
 * worker keeps only Forward's weak handle; effects run once on the original UI
 * after the existing process broker has acknowledged its protection setup. */
final class MdoBrowserCdpPopupPreparation implements AutoCloseable {
    interface Failure {void failed(String code,boolean effectMayHaveOccurred);}
    interface Lost {void lost(String code);}
    private static final int NEW=0,STARTING=1,READY=2,RETIRING=3,TERMINAL=4;
    private static final class Pending {
        final Runnable effect;final Failure failure;
        Pending(Runnable effect,Failure failure){this.effect=effect;this.failure=failure;}
    }
    private static final class Forward implements MdoBrowserCdpPopupWorker.Events {
        private final WeakReference<MdoBrowserCdpPopupPreparation> owner;
        Forward(MdoBrowserCdpPopupPreparation owner){this.owner=new WeakReference<>(owner);}
        public void state(MdoBrowserCdpPopupWorker.Result result){MdoBrowserCdpPopupPreparation value=owner.get();if(value!=null)value.state(result);}
        public void candidate(MdoBrowserCdpPopupBroker.Candidate candidate){MdoBrowserCdpPopupPreparation value=owner.get();if(value!=null)value.candidate(candidate);}
        public void protection(MdoBrowserCdpPopupBroker.Candidate candidate){MdoBrowserCdpPopupPreparation value=owner.get();if(value!=null)value.protection(candidate);}
    }
    private final MdoBrowserCdpPopupNative source;
    private final BooleanSupplier owner;private final Lost lost;
    private MdoBrowserCdpPopupWorker.Request request;private Pending pending;
    private int phase=NEW;private String code;private boolean notified,everReady,running;
    MdoBrowserCdpPopupPreparation(MdoBrowserCdpPopupNative source,BooleanSupplier owner,Lost lost){
        thread();if(source==null||owner==null||lost==null)throw new IllegalArgumentException("Missing original popup preparation owner");this.source=source;this.owner=owner;this.lost=lost;
    }
    private static void thread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Popup preparation requires its original UI");}
    private boolean owned(){try{return owner.getAsBoolean()&&source.current();}catch(RuntimeException|Error error){return false;}}
    void start(MdoBrowserCdpWorker worker,int timeout)throws IOException {
        thread();if(phase!=NEW||worker==null)throw new IOException("cdp_popup_preparation_replayed");phase=STARTING;
        try{if(!owner.getAsBoolean())throw new IOException("cdp_popup_preparation_owner_changed");request=worker.prepareNativePopups(source,timeout,new Forward(this));}
        catch(IOException|RuntimeException|Error error){phase=TERMINAL;code=error instanceof IOException?safe(error.getMessage()):"cdp_popup_preparation_failed";throw error;}
    }
    private static String safe(String value){return value!=null&&value.matches("cdp_[a-z_]{1,80}")?value:"cdp_popup_preparation_failed";}
    void await(Runnable effect,Failure failure)throws IOException {
        thread();if(effect==null||failure==null)throw new IOException("cdp_arguments_invalid");if(pending!=null||running)throw new IOException("cdp_popup_preparation_busy");
        if(phase!=STARTING&&phase!=READY){failure.failed(code==null?"cdp_popup_preparation_retiring":code,false);return;}
        pending=new Pending(effect,failure);if(phase==READY)runPending();
    }
    private void runPending(){
        if(pending==null)return;if(!owned()){close("cdp_popup_preparation_owner_changed");return;}
        Pending original=pending;pending=null;running=true;
        try{original.effect.run();}catch(RuntimeException|Error error){close("cdp_popup_action_failed");original.failure.failed("cdp_popup_action_failed",true);}finally{running=false;}
    }
    private void state(MdoBrowserCdpPopupWorker.Result result){
        thread();if(result.terminal){phase=TERMINAL;
            if(code==null||java.util.Arrays.asList("cdp_popup_preparation_owner_changed","cdp_popup_preparation_failed","cdp_popup_protection_unavailable").contains(code)&&result.code!=null&&!result.code.endsWith("_cancelled"))code=result.code;
            failPending(code==null?"cdp_popup_preparation_cancelled":code);notifyLost();return;}
        if(!result.ready||phase!=STARTING)return;
        if(!owned()){close("cdp_popup_preparation_owner_changed");return;}phase=READY;everReady=true;runPending();
    }
    private void candidate(MdoBrowserCdpPopupBroker.Candidate candidate){
        thread();if(phase!=READY||!owned()){close("cdp_popup_preparation_owner_changed");return;}
        try{candidate.requestProtection();}catch(IOException|RuntimeException|Error error){close("cdp_popup_preparation_failed");}
    }
    private void protection(MdoBrowserCdpPopupBroker.Candidate candidate){thread();MdoBrowserCdpSession.ControlResult result=candidate.protection();if(result==null||!result.ok)close("cdp_popup_protection_unavailable");}
    private void failPending(String reason){Pending original=pending;pending=null;if(original!=null)original.failure.failed(reason,false);}
    private void notifyLost(){if(everReady&&!notified){notified=true;lost.lost(code==null?"cdp_popup_preparation_retired":code);}}
    boolean ready(){thread();if(phase==READY&&!owned())close("cdp_popup_preparation_owner_changed");return phase==READY;}
    boolean settled(){thread();if(phase!=TERMINAL)return false;return request==null?!source.live():request.result()!=null&&request.result().sourceReleased&&source.released();}
    String phase(){thread();return new String[]{"new","preparing","ready","retiring","terminal"}[phase];}
    String failure(){thread();return code;}
    void close(String reason){
        thread();if(phase==TERMINAL)return;if(code==null)code=safe(reason);phase=RETIRING;
        if(request!=null)request.close();else phase=TERMINAL;failPending(code);notifyLost();
    }
    @Override public void close(){close("cdp_popup_preparation_cancelled");}
}
