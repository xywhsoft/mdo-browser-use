package org.xserver.android;

import android.os.Looper;
import android.webkit.WebView;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** Exact UI-owned physical view/native callback join. Workers see only the
 * original lease and a data-only native rejection receipt, never a WebView. */
final class MdoBrowserCdpPopupNative {
    interface Disposal {void destroy(String key,WebView exact);}
    interface Destruction {boolean ready();void destroy();}
    private static final AtomicBoolean CREATION_UNCERTAIN=new AtomicBoolean();
    private static final UiRetention[] UI_OWNERS=new UiRetention[8];
    static boolean creationConfirmed(){return !CREATION_UNCERTAIN.get();}
    static int retainedUiOwners(){thread();int count=0;for(UiRetention owner:UI_OWNERS)if(owner!=null)count++;return count;}
    static void viewDestroyed(WebView exact){thread();for(UiRetention owner:UI_OWNERS)if(owner!=null)owner.destroyed(exact);}
    private static boolean destructionAllowed(WebView exact){
        if(exact==null)return false;
        for(UiRetention owner:UI_OWNERS)if(owner!=null){
            if(owner.source.view.get()==exact&&owner.core.protocolHasObligations(owner.scope))return false;
            for(Created child:owner.book.constructions)if(child!=null&&child.view==exact&&child.sent&&(child.adoption==null||!child.adoption.nativeConsumed()))return false;
        }return true;
    }
    /** Register every host close before closing controllers can call back. */
    static boolean registerDestruction(WebView exact,Destruction destruction){
        thread();if(exact==null||destruction==null)throw new IllegalArgumentException("Native destruction requires its exact UI owner");boolean owned=false;
        for(UiRetention owner:UI_OWNERS)if(owner!=null){
            for(Created child:owner.book.constructions)if(child!=null&&child.view==exact&&!child.done){
                if(child.hostDestruction==null)child.hostDestruction=destruction;child.live.set(false);owned=true;
            }
            if(owner.source.view.get()==exact){if(owner.rootDestruction==null)owner.rootDestruction=destruction;owner.ending=true;owner.core.retireProtocolUi(owner.scope);owned=true;}
        }return owned;
    }
    static void activateDestruction(WebView exact,Destruction destruction)throws IOException {
        thread();for(UiRetention owner:UI_OWNERS)if(owner!=null){
            for(Created child:owner.book.constructions)if(child!=null&&child.view==exact&&child.hostDestruction==destruction)child.close();
            if(owner.source.view.get()==exact&&owner.rootDestruction==destruction){if(owner.source.scope==owner.scope)owner.source.reject();owner.sweep();}
        }
    }
    /** Bounded UI ownership survives a host removing its opener's controller.
     * Workers retain only weak links to this record, never its core/book/host. */
    private static final class UiRetention {
        final MdoBrowserCdpPopupNative source;final MdoBrowserPopups<WebView> core;final MdoBrowserPopups.Scope scope;final UiOwned book;
        boolean ending,released,sweeping,rootFailed;Destruction rootDestruction;
        UiRetention(MdoBrowserCdpPopupNative source,MdoBrowserPopups<WebView> core,MdoBrowserPopups.Scope scope,UiOwned book){this.source=source;this.core=core;this.scope=scope;this.book=book;}
        void ending(){ending=true;sweep();}
        void destroyed(WebView exact){
            if(released)return;
            if(source.view.get()==exact){ending=true;if(source.scope==scope)source.reject();else for(Created child:book.constructions)if(child!=null)try{child.close();}catch(IOException error){/* retained uncertainty */}}
            for(Created child:book.constructions)if(child!=null&&child.view==exact&&child.construction!=null&&child.construction.destroyed())try{child.close();}catch(IOException error){/* retained uncertainty */}
            sweep();
        }
        void sweep(){
            if(released||!ending||sweeping)return;sweeping=true;
            try{
                for(Created child:book.constructions)if(child!=null&&child.construction!=null&&child.construction.destroyed())try{child.close();}catch(IOException error){/* no false physical release */}
                if(core.protocolHasObligations(scope))return;
                if(rootDestruction!=null){
                    if(rootFailed||!rootDestruction.ready()||!destructionAllowed(source.view.get()))return;
                    try{rootDestruction.destroy();rootDestruction=null;}
                    catch(RuntimeException|Error error){rootFailed=true;CREATION_UNCERTAIN.set(true);if(source.failure==null)source.failure="cdp_popup_native_owner_cleanup_unconfirmed";return;}
                }
                for(Created child:book.constructions)if(child!=null)return;
                core.releaseProtocolUi(scope);released=true;for(int i=0;i<UI_OWNERS.length;i++)if(UI_OWNERS[i]==this)UI_OWNERS[i]=null;
                if(book.retention.get()==this)book.retention.clear();if(source.retention.get()==this)source.retention.clear();
            }finally{sweeping=false;}
        }
    }
    /** UI-owned construction result. Only this class invokes the native new;
     * callers cannot certify a supplied/preexisting WebView as post-capture. */
    static final class Created implements AutoCloseable {
        private final WeakReference<MdoBrowserCdpPopupNative> owner;private WebView view;
        private MdoBrowserLifecycle.Fresh<WebView> birth;private final MdoBrowserCdpPopupWorker.Allocation allocation;
        private final MdoBrowserLifecycle<WebView> registry;private final String key;private Disposal disposal;
        private final MdoBrowserPopups.Scope action;
        private final WeakReference<UiOwned> book;
        private final AtomicBoolean live=new AtomicBoolean(true);
        private Destruction hostDestruction;
        private MdoBrowserPopups.Adoption adoption;
        private MdoBrowserLifecycle.Construction<WebView> construction;private boolean sent,done,closing,cleanupFailed,retirementRequested,awaitingConsumption;
        private Created(MdoBrowserCdpPopupNative owner,UiOwned book,MdoBrowserLifecycle<WebView> registry,String key,MdoBrowserCdpPopupWorker.Allocation allocation,Disposal disposal){
            this.owner=new WeakReference<>(owner);this.book=new WeakReference<>(book);this.registry=registry;this.key=key;this.allocation=allocation;this.disposal=disposal;action=owner.scope;
        }
        void attachDisposal(Disposal cleanup){thread();MdoBrowserCdpPopupNative original=owner.get();if(cleanup==null||disposal!=null||sent||done||original==null||!original.current()||!allocation.current())throw new IllegalStateException("cdp_popup_native_child_invalid");disposal=cleanup;}
        WebView view(){thread();if(disposal==null)throw new IllegalStateException("cdp_popup_native_disposal_required");MdoBrowserCdpPopupNative original=owner.get();if(done||!live.get()||view==null||birth==null||!birth.current()||!allocation.current()||original==null||!original.current())throw new IllegalStateException("cdp_popup_native_child_invalid");return view;}
        private void finished(){done=true;UiOwned original=book.get();if(original!=null){for(int i=0;i<original.constructions.length;i++)if(original.constructions[i]==this){original.constructions[i]=null;break;}original.drained();}}
        private void cancelOwner(){MdoBrowserCdpPopupNative original=owner.get();if(original!=null&&original.scope==action&&original.rejecting.get()==0&&original.pending()==allocation.pending)original.reject();}
        private void awaitConsumption(){retirementRequested=true;if(!awaitingConsumption){awaitingConsumption=true;MdoBrowserCdpPopupNative source=owner.get();if(source!=null)source.retiring.incrementAndGet();}}
        private void consumed(MdoBrowserPopups.Adoption exact)throws IOException {if(exact==adoption&&retirementRequested)close();}
        private void clearConsumptionWait(){if(awaitingConsumption){awaitingConsumption=false;MdoBrowserCdpPopupNative source=owner.get();if(source!=null)source.retiring.decrementAndGet();}}
        private void acknowledgeDestruction(){MdoBrowserCdpPopupNative source=owner.get();MdoBrowserPopups<WebView> core=source==null?null:source.owner.get();if(adoption!=null&&adoption.dispatched()&&core!=null)core.nativeChildDestroyed(adoption,view);}
        @Override public void close()throws IOException {
            thread();if(done||closing)return;
            live.set(false);
            if(sent&&adoption!=null&&!adoption.dispatched())sent=false;
            if(birth!=null&&birth.humanOwned()&&hostDestruction==null){
                if(sent&&(adoption==null||!adoption.nativeConsumed())){retirementRequested=true;return;}
                clearConsumptionWait();finished();cancelOwner();return;
            }
            if(sent&&(adoption==null||!adoption.nativeConsumed())){awaitConsumption();return;}
            if(hostDestruction!=null&&(!hostDestruction.ready()||!destructionAllowed(view))){awaitConsumption();return;}
            if(construction!=null&&construction.destroyed()){clearConsumptionWait();acknowledgeDestruction();finished();if(!sent)cancelOwner();return;}
            if(cleanupFailed)throw new IOException(view==null?"cdp_popup_native_constructor_unconfirmed":"cdp_popup_native_child_cleanup_unconfirmed");
            closing=true;MdoBrowserCdpPopupNative source=owner.get();if(source!=null)source.disposing.incrementAndGet();
            try{
                clearConsumptionWait();
                if(construction==null){finished();cancelOwner();return;}
                if(!construction.started()){construction.cancelBeforeStart();finished();cancelOwner();return;}
                if(construction.destroyed()){finished();cancelOwner();return;}
                if(view==null)throw new IOException("cdp_popup_native_constructor_unconfirmed");
                if(hostDestruction!=null)hostDestruction.destroy();
                else if(disposal!=null)disposal.destroy(key,view);
                else{if(registry.owns(key,view))registry.remove(key);view.destroy();registry.viewDestroyed(view);}
                if(!construction.destroyed()&&!registry.nativeDestroyed(view))throw new IOException("cdp_popup_native_child_cleanup_unconfirmed");acknowledgeDestruction();finished();cancelOwner();
            }catch(IOException|RuntimeException|Error error){
                String code=view==null?"cdp_popup_native_constructor_unconfirmed":"cdp_popup_native_child_cleanup_unconfirmed";
                cleanupFailed=true;CREATION_UNCERTAIN.set(true);MdoBrowserCdpPopupNative original=owner.get();if(original!=null){if(original.failure==null)original.failure=code;MdoBrowserPopups<WebView> core=original.owner.get();if(core!=null&&adoption!=null)core.failChildRetirement(adoption);}cancelOwner();throw new IOException(code);
            }finally{closing=false;if(source!=null)source.disposing.decrementAndGet();}
        }
        void retryCleanup()throws IOException {thread();if(view==null&&construction!=null&&construction.started())throw new IOException("cdp_popup_native_constructor_unconfirmed");cleanupFailed=false;close();}
    }
    /** Original consumed native handoff plus its exact physical child marker.
     * No child/target selector or UI predicate crosses to the shared worker. */
    static final class Child implements AutoCloseable {
        private final MdoBrowserPopups.Scope source;private final MdoBrowserCdpMarker.Lease opener;
        private final WeakReference<MdoBrowserCdpMarker> marker;private final MdoBrowserCdpMarker.Lease lease;
        private final MdoBrowserLifecycle.Fresh<WebView> birth;
        private final AtomicBoolean constructionLive;
        private final MdoBrowserCdpPopupWorker.Allocation allocation;
        private final AtomicBoolean live=new AtomicBoolean(true),queued=new AtomicBoolean(),attempted=new AtomicBoolean();
        private final AtomicReference<MdoBrowserCdpSession.ControlResult> proof=new AtomicReference<>();
        private Child(MdoBrowserPopups.Scope source,MdoBrowserCdpMarker.Lease opener,MdoBrowserCdpMarker marker,MdoBrowserLifecycle.Fresh<WebView> birth,MdoBrowserCdpPopupWorker.Allocation allocation,AtomicBoolean constructionLive){this.source=source;this.opener=opener;this.marker=new WeakReference<>(marker);lease=marker.lease();this.birth=birth;this.allocation=allocation;this.constructionLive=constructionLive;}
        boolean current(){return creationConfirmed()&&live.get()&&(constructionLive==null||constructionLive.get())&&birth.current()&&source.current()&&opener.current()&&lease.current()&&(allocation==null||allocation.current());}
        boolean allocated(){return allocation!=null;}
        boolean includes(MdoBrowserCdpPopupBroker.Candidate original){return allocation!=null&&allocation.includes(original);}
        boolean nativeDestroyed(){return birth.destroyed();}
        boolean belongsTo(MdoBrowserCdpMarker.Lease exact){return opener==exact;}
        boolean ownedBy(MdoBrowserCdpBinding.Network.PopupSource source){return source!=null&&source.owns(opener);}
        MdoBrowserCdpMarker marker(){return marker.get();}
        boolean enqueue(){return current()&&queued.compareAndSet(false,true);}
        boolean claim(){return current()&&queued.get()&&attempted.compareAndSet(false,true);}
        MdoBrowserCdpSession.ControlResult proof(){return proof.get();}
        void finish(boolean ok,String code,int protocol,boolean effect){proof.compareAndSet(null,new MdoBrowserCdpSession.ControlResult(ok,code,protocol,effect));}
        @Override public void close(){live.set(false);finish(false,"cdp_popup_native_child_retired",0,attempted.get());MdoBrowserCdpMarker original=marker.get();if(original!=null)original.close();else lease.invalidate();}
    }
    private final MdoBrowserCdpMarker.Lease lease;private final WeakReference<WebView> view;
    private final WeakReference<MdoBrowserPopups<WebView>> owner;private final WeakReference<MdoBrowserCdpView> adapter;
    private boolean claimed,released;private volatile MdoBrowserPopups.Scope scope;
    private volatile MdoBrowserPopups.Stop stop;private volatile String failure;
    private final AtomicBoolean rejectStarted=new AtomicBoolean();
    private static final class UiOwned implements MdoBrowserPopups.ProtocolUi {
        final Created[] constructions=new Created[7];
        WeakReference<UiRetention> retention=new WeakReference<>(null);
        public void consumed(MdoBrowserPopups.Adoption exact){for(Created child:constructions)if(child!=null)try{child.consumed(exact);}catch(IOException error){/* sticky failure; never replay the native send */}}
        public void drained(){UiRetention owner=retention.get();if(owner!=null)owner.sweep();}
    }
    private WeakReference<UiOwned> uiOwned=new WeakReference<>(null);
    private WeakReference<UiRetention> retention=new WeakReference<>(null);
    private final AtomicInteger disposing=new AtomicInteger();
    private final AtomicInteger rejecting=new AtomicInteger();
    private final AtomicInteger retiring=new AtomicInteger();
    MdoBrowserCdpPopupNative(MdoBrowserCdpView adapter,WebView exact,MdoBrowserPopups<WebView> core)throws IOException {
        thread();if(adapter==null||core==null||!core.protocolSource(exact))throw new IOException("cdp_popup_native_source_invalid");
        if(!adapter.popupCurrent(exact))throw new IOException("cdp_popup_native_source_invalid");
        lease=adapter.physicalLease(exact);view=new WeakReference<>(exact);owner=new WeakReference<>(core);this.adapter=new WeakReference<>(adapter);
    }
    private static void thread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native popup source requires its UI owner");}
    MdoBrowserCdpMarker.Lease lease(){return lease;}
    void retainUiOwner()throws IOException {
        thread();UiRetention existing=retention.get();if(existing!=null&&!existing.released&&existing.scope==scope)return;
        MdoBrowserPopups<WebView> core=owner.get();UiOwned book=uiOwned.get();if(!current()||core==null||book==null)throw new IOException("cdp_popup_native_source_invalid");
        int slot=-1;for(int i=0;i<UI_OWNERS.length;i++)if(UI_OWNERS[i]==null){slot=i;break;}if(slot<0)throw new IOException("cdp_popup_native_owner_limit");
        UiRetention next=new UiRetention(this,core,scope,book);WeakReference<UiRetention> weak=new WeakReference<>(next);
        core.retainProtocolUi(scope);UI_OWNERS[slot]=next;retention=weak;book.retention=weak;
    }
    void claim(MdoBrowserCdpMarker.Lease original)throws IOException {
        thread();if(!creationConfirmed())throw new IOException("cdp_popup_native_creation_unconfirmed");if(original!=lease||!lease.current())throw new IOException("cdp_popup_native_source_invalid");
        if(claimed&&released)finish(true);
        if(claimed)throw new IOException("cdp_popup_native_source_busy");
        WebView exact=view.get();MdoBrowserPopups<WebView> core=owner.get();MdoBrowserCdpView nativeView=adapter.get();
        if(exact==null||core==null||nativeView==null||!nativeView.popupCurrent(exact))throw new IOException("cdp_popup_native_source_invalid");
        UiOwned ui=new UiOwned();
        try{scope=core.claimProtocol(exact,()->nativeView.popupCurrent(exact));core.attachProtocolUi(scope,ui);uiOwned=new WeakReference<>(ui);}
        catch(RuntimeException|OutOfMemoryError error){if(scope!=null)try{core.abandonProtocol(scope);}catch(RuntimeException|OutOfMemoryError ignored){}scope=null;throw new IOException("cdp_popup_native_source_busy");}
        rejectStarted.set(false);claimed=true;released=false;
    }
    void abandon(){
        thread();MdoBrowserPopups<WebView> core=owner.get();
        if(stop==null&&failure==null&&scope!=null&&core!=null&&core.abandonProtocol(scope)){UiRetention retained=retention.get();claimed=false;scope=null;if(retained!=null)retained.ending();}
    }
    /** Called only on the original UI before exposing preparation/candidates. */
    boolean current(){thread();MdoBrowserPopups<WebView> core=owner.get();return failure==null&&creationConfirmed()&&claimed&&core!=null&&core.protocolCurrent(scope);}
    boolean ready(){thread();MdoBrowserPopups<WebView> core=owner.get();return current()&&core!=null&&core.readyProtocol(scope);}
    boolean live(){MdoBrowserPopups.Scope value=scope;return failure==null&&creationConfirmed()&&value!=null&&value.current();}
    MdoBrowserPopups.Pending pending(){thread();MdoBrowserPopups<WebView> core=owner.get();return claimed&&core!=null?core.pendingProtocol(scope):null;}
    Created construct(MdoBrowserCdpPopupWorker.Allocation allocation,MdoBrowserLifecycle<WebView> registry,String key)throws IOException {
        return construct(allocation,registry,key,null);
    }
    Created construct(MdoBrowserCdpPopupWorker.Allocation allocation,MdoBrowserLifecycle<WebView> registry,String key,Disposal disposal)throws IOException {
        thread();WebView opener=view.get();
        if(!creationConfirmed()||allocation==null||registry==null||opener==null||!current()||pending()!=allocation.pending)throw new IOException("cdp_popup_native_allocation_unavailable");
        UiOwned ui=uiOwned.get();if(ui==null)throw new IOException("cdp_popup_native_allocation_unavailable");Created[] constructions=ui.constructions;
        int slot=-1;for(int i=0;i<constructions.length;i++){Created old=constructions[i];if(old!=null&&old.construction!=null&&old.construction.destroyed())constructions[i]=null;if(constructions[i]==null)slot=i;}
        if(slot<0)throw new IOException("cdp_popup_native_child_limit");
        final android.content.Context context=opener.getContext();Created created=new Created(this,ui,registry,key,allocation,disposal);
        try{created.construction=registry.reservePopup(key,opener);}catch(RuntimeException error){throw new IOException("cdp_popup_native_child_unavailable");}
        if(!allocation.claim(this)){created.construction.cancelBeforeStart();throw new IOException("cdp_popup_native_allocation_unavailable");}
        constructions[slot]=created;
        try{
            created.construction.start();created.view=new WebView(context);
            created.birth=registry.putPopup(created.construction,created.view);
            if(!current()||!allocation.current()||pending()!=allocation.pending)throw new IllegalStateException("cdp_popup_native_allocation_unavailable");
            return created;
        }catch(RuntimeException|Error error){
            String code="cdp_popup_native_construct_failed";try{created.close();}catch(IOException cleanup){code=cleanup.getMessage();}
            if(scope==created.action)reject();throw new IOException(code);
        }
    }
    MdoBrowserPopups.Adoption adopt(Created original){
        thread();if(original==null||original.owner.get()!=this||!original.allocation.current())throw new IllegalStateException("popup_request_unavailable");
        if(original.disposal==null)throw new IllegalStateException("cdp_popup_native_disposal_required");
        if(original.done||!original.live.get()||original.sent||scope!=original.action||!current())throw new IllegalStateException("popup_request_unavailable");
        MdoBrowserPopups<WebView> core=owner.get();if(!claimed||core==null||core.pendingProtocol(scope)!=original.allocation.pending)throw new IllegalStateException("popup_request_unavailable");
        original.adoption=core.prepareProtocolAdoption(scope,original.allocation.pending,original.birth.claim(view.get()));
        original.sent=true;core.sendProtocolAdoption(scope,original.adoption);return original.adoption;
    }
    MdoBrowserPopups.Adoption adopt(MdoBrowserPopups.Pending original,MdoBrowserLifecycle.Fresh<WebView> child){
        thread();MdoBrowserPopups<WebView> core=owner.get();
        if(!current()||core==null||original==null||core.pendingProtocol(scope)!=original||!original.current()||child==null)throw new IllegalStateException("popup_request_unavailable");
        return core.adoptProtocol(scope,original,child.claim(view.get()));
    }
    Child child(MdoBrowserPopups.Adoption original,MdoBrowserLifecycle.Fresh<WebView> birth,MdoBrowserCdpView adapter)throws IOException {
        return child(original,birth,adapter,null,null);
    }
    Child child(MdoBrowserPopups.Adoption original,Created created,MdoBrowserCdpView adapter)throws IOException {
        thread();if(created==null||created.owner.get()!=this||created.done||!created.live.get()||!created.sent||created.adoption!=original||!created.allocation.current())throw new IOException("cdp_popup_native_child_invalid");
        return child(original,created.birth,adapter,created.allocation,created.live);
    }
    private Child child(MdoBrowserPopups.Adoption original,MdoBrowserLifecycle.Fresh<WebView> birth,MdoBrowserCdpView adapter,MdoBrowserCdpPopupWorker.Allocation allocation,AtomicBoolean constructionLive)throws IOException {
        thread();try{MdoBrowserPopups<WebView> core=owner.get();
            WebView exact=birth==null?null:birth.attached(view.get());
            if(!current()||core==null||adapter==null||!adapter.popupCurrent(exact)||adapter.physicalLease(exact).busy())throw new IOException("cdp_popup_native_child_invalid");
            Child child=new Child(scope,lease,adapter.marker(),birth,allocation,constructionLive);
            if(!core.claimAdopted(scope,original,exact))throw new IOException("cdp_popup_native_child_invalid");return child;
        }catch(RuntimeException|OutOfMemoryError error){throw new IOException("cdp_popup_native_child_invalid");}
    }
    void reject(){
        thread();rejecting.incrementAndGet();rejectStarted.set(true);try{UiOwned ui=uiOwned.get();if(ui!=null)for(Created child:ui.constructions)if(child!=null)try{child.close();}catch(IOException error){/* sticky failure + exact retained resource */}
        if(stop!=null)return;
        WebView exact=view.get();MdoBrowserPopups<WebView> core=owner.get();
        if(!claimed||exact==null||core==null||!core.original(exact)){failure="cdp_popup_native_source_lost";return;}
        MdoBrowserPopups.Adoption adopted=null;if(ui!=null)for(Created child:ui.constructions)if(child!=null&&child.sent&&(child.birth==null||!child.birth.humanOwned())&&core.sentProtocolAdoption(scope,child.adoption)){adopted=child.adoption;break;}
        try{stop=core.stopForProtocol(scope,adopted);}catch(RuntimeException|OutOfMemoryError error){failure="cdp_popup_native_rejection_unconfirmed";}
        }finally{rejecting.decrementAndGet();}
    }
    boolean rejectionPending(){MdoBrowserPopups.Stop value=stop;return disposing.get()!=0||rejecting.get()!=0||retiring.get()!=0||!rejectStarted.get()&&(failure!=null||!creationConfirmed())||value!=null&&value.pending();}
    boolean confirmed(){MdoBrowserPopups.Stop value=stop;return failure==null&&!rejectionPending()&&value!=null&&value.confirmed();}
    String failure(){
        if(failure!=null)return failure;MdoBrowserPopups.Stop value=stop;
        if(value==null||value.pending()||value.confirmed())return null;
        return "popup_native_already_adopted".equals(value.failure())?"cdp_popup_native_already_adopted":"cdp_popup_native_rejection_unconfirmed";
    }
    void finish(boolean sourceReleased){
        thread();UiRetention retained=retention.get();if(retained!=null&&retained.scope==scope)retained.ending();if(!sourceReleased)return;released=true;
        MdoBrowserPopups.Stop value=stop;
        if(value==null){abandon();return;}
        if(confirmed()&&value.release()){claimed=false;stop=null;scope=null;}
    }
}
