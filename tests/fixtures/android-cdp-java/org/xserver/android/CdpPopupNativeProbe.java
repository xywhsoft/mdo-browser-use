package org.xserver.android;

import android.webkit.WebView;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

/** Actual native-source/controller code with controlled Android UI callbacks.
 * No renderer, installed provider, protocol-target or child-world proof. */
public final class CdpPopupNativeProbe {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Native popup ownership check "+(checks+1));checks++;}
    private interface Work {void run()throws Exception;}
    private static void denied(Work work)throws Exception{try{work.run();throw new AssertionError("Unexpected native popup adoption");}catch(IllegalStateException expected){checks++;}}
    private static void unavailable(String code,Work work)throws Exception{try{work.run();throw new AssertionError("Unexpected native popup admission");}catch(IOException expected){check(code.equals(expected.getMessage())&&expected.getCause()==null);}}
    private static final class Transport implements MdoBrowserPopups.Transport<WebView> {
        final Object token=new Object();int sends;WebView child;Runnable consumed;boolean fail,oom,inline;
        public Object token(){return token;}
        public void send(WebView child,Runnable consumed){this.child=child;this.consumed=consumed;sends++;if(oom)throw new OutOfMemoryError();if(fail)throw new IllegalStateException("controlled-native-send-failure");if(inline)consumed.run();}
        void ack(){consumed.run();}
    }
    private static final class Case implements AutoCloseable {
        final WebView view=new WebView();boolean actionOwned=true,ownerFailure;long epoch=1,now;int births;
        final MdoBrowserLifecycle<WebView> views=PopupLifecycleFixture.registry();
        final MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(8);
        final List<Runnable> timers=new ArrayList<>();
        final MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,v->{if(ownerFailure)throw new IllegalStateException("controlled-owner-failure");return v==view&&actionOwned;},new MdoBrowserCdpMarker.DocumentQuota());
        final MdoBrowserPopups<WebView> core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
            public long now(){return now;}public void at(Runnable task,long at){timers.add(task);}public void remove(Runnable task){timers.remove(task);}
        },budget,()->true,()->epoch,blocked->{});
        final MdoBrowserCdpPopupNative source;
        Case()throws Exception{views.put("root",view);source=new MdoBrowserCdpPopupNative(adapter,view,core);source.claim(adapter.marker().lease());}
        MdoBrowserLifecycle.Fresh<WebView> fresh(WebView child){return views.putPopup("child"+(++births),view,child);}
        public void close(){PopupLifecycleFixture.destroyAll(views);core.close();core.viewDestroyed(view);adapter.close();check(budget.used()==0&&timers.isEmpty());}
    }
    private static void ownership()throws Exception{
        try(Case c=new Case()){
            check(c.source.ready());
            Transport original=new Transport();check(c.core.open(original,false));String id=c.core.snapshot().id;
            check(c.core.dismiss(id));original.ack();check(original.sends==1&&c.budget.used()==0);
            c.actionOwned=false;Transport manual=new Transport();check(c.core.open(manual,true));String manualId=c.core.snapshot().id;
            check(c.adapter.physicalLease(c.view).current()&&!c.adapter.popupCurrent(c.view));
            c.source.reject();
            check(manual.sends==0&&manualId.equals(c.core.snapshot().id));
            check(c.source.confirmed());c.source.finish(true);
            check(manual.sends==0&&manualId.equals(c.core.snapshot().id)&&c.budget.used()==1);
            check(c.core.dismiss(manualId));manual.ack();check(manual.sends==1&&c.budget.used()==0);
        }
    }
    private static void adoption()throws Exception{
        try(Case c=new Case();Case foreign=new Case()){
            Transport premature=new Transport();check(!c.core.open(premature,false)&&premature.sends==0);
            check(c.source.ready()&&foreign.source.ready());
            Transport original=new Transport(),other=new Transport();check(c.core.open(original,false)&&foreign.core.open(other,true));
            MdoBrowserPopups.Pending pending=c.source.pending(),wrong=foreign.source.pending();
            check(pending!=null&&pending==c.source.pending()&&pending.current()&&!pending.userInitiated&&pending.epoch==1);
            check(wrong!=null&&wrong.userInitiated);
            MdoBrowserLifecycle.Fresh<WebView> foreignChild=foreign.fresh(new WebView());
            denied(()->c.source.adopt(pending,foreignChild));check(foreignChild.claim(foreign.view)!=null&&original.sends==0);
            denied(()->c.source.adopt(wrong,c.fresh(new WebView())));denied(()->c.source.adopt(pending,c.fresh(c.view)));denied(()->c.source.adopt(pending,null));
            denied(()->c.core.adopt(pending.id,new WebView(),(ok,code,may)->{}));check(original.sends==0&&pending.current());
            WebView child=new WebView();MdoBrowserPopups.Adoption receipt=c.source.adopt(pending,c.fresh(child));
            check(original.sends==1&&original.child==child&&!pending.current()&&c.source.pending()==null);
            check(receipt.pending()&&!receipt.ok()&&!receipt.nativeConsumed()&&receipt.mayHaveOccurred());
            check(c.core.adoptedTo(receipt,child)&&!c.core.adoptedTo(receipt,new WebView())&&!foreign.core.adoptedTo(receipt,child));
            denied(()->c.source.adopt(pending,c.fresh(new WebView())));original.ack();original.ack();
            check(!receipt.pending()&&receipt.ok()&&receipt.nativeConsumed()&&receipt.failure()==null&&original.sends==1&&c.budget.used()==0);
            c.source.reject();check(c.source.confirmed());c.source.finish(true);
            c.source.claim(c.adapter.marker().lease());check(c.source.ready());
            Transport replacement=new Transport();check(c.core.open(replacement,false));MdoBrowserPopups.Pending next=c.source.pending();
            check(next!=null&&next!=pending&&!pending.current());denied(()->c.source.adopt(pending,c.fresh(new WebView())));check(replacement.sends==0);
            c.source.reject();replacement.ack();check(!next.current()&&c.source.confirmed());c.source.finish(true);
            foreign.source.reject();other.ack();foreign.source.finish(true);
        }
        for(int mode=0;mode<5;mode++)try(Case c=new Case()){
            check(c.source.ready());Transport original=new Transport();check(c.core.open(original,false));MdoBrowserPopups.Pending pending=c.source.pending();
            original.fail=mode==0;original.oom=mode==1;original.inline=mode==4;
            MdoBrowserPopups.Adoption receipt=c.source.adopt(pending,c.fresh(new WebView()));
            if(mode<2){check(!receipt.pending()&&!receipt.ok()&&receipt.failure().equals("popup_transport_unconfirmed"));original.ack();check(!receipt.ok()&&receipt.nativeConsumed());}
            else if(mode==2){c.actionOwned=false;original.ack();check(!receipt.ok()&&receipt.nativeConsumed()&&receipt.failure().equals("popup_source_changed"));}
            else if(mode==3){c.now=5001;for(Runnable timer:new ArrayList<>(c.timers))timer.run();check(!receipt.ok()&&receipt.failure().equals("popup_transport_unconfirmed"));original.ack();check(!receipt.ok());}
            else check(receipt.ok()&&receipt.nativeConsumed()&&!receipt.pending());
            check(original.sends==1&&!pending.current());denied(()->c.source.adopt(pending,c.fresh(new WebView())));
        }
    }
    private static void stale()throws Exception{
        try(Case c=new Case()){
            check(c.source.ready());Transport nativeWindow=new Transport();check(c.core.open(nativeWindow,false));MdoBrowserPopups.Pending pending=c.source.pending();
            c.actionOwned=false;check(!c.source.current()&&!c.source.live()&&!pending.current());c.actionOwned=true;
            check(!c.source.current()&&!c.source.ready());denied(()->c.source.adopt(pending,c.fresh(new WebView())));check(nativeWindow.sends==0);
            c.source.reject();check(nativeWindow.sends==1&&nativeWindow.child==null);nativeWindow.ack();check(c.source.confirmed());c.source.finish(true);
            c.source.claim(c.adapter.marker().lease());check(c.source.ready());
            c.epoch++;check(!c.source.current());c.source.reject();check(c.source.confirmed());c.source.finish(true);
        }
        try(Case c=new Case()){
            c.actionOwned=false;unavailable("cdp_popup_native_source_invalid",()->new MdoBrowserCdpPopupNative(c.adapter,c.view,c.core));
            check(!c.source.ready());c.source.finish(true);c.actionOwned=true;
            Transport manual=new Transport();check(c.core.open(manual,true));
            unavailable("cdp_popup_native_source_busy",()->c.source.claim(c.adapter.marker().lease()));check(manual.sends==0);
            check(c.core.dismiss(c.core.snapshot().id));manual.ack();c.source.claim(c.adapter.marker().lease());check(c.source.ready());
            c.source.reject();c.source.finish(true);
        }
    }
    private static void cancellation()throws Exception{
        for(boolean withPending:new boolean[]{false,true})try(Case c=new Case()){
            check(c.source.ready());Transport original=new Transport(),late=new Transport();
            if(withPending)check(c.core.open(original,false));c.core.cancel();check(!c.source.live());
            check(!c.core.open(late,false)&&late.sends==0);
            c.source.reject();check(!c.core.open(late,true));
            if(withPending){check(original.sends==1&&original.child==null&&!c.source.confirmed());original.ack();}
            check(c.source.confirmed());c.source.finish(true);check(c.core.open(late,true));
            check(c.core.dismiss(c.core.snapshot().id));late.ack();check(late.sends==1&&c.budget.used()==0);
        }
        try(Case c=new Case()){
            check(c.source.ready());Transport original=new Transport();check(c.core.open(original,false));MdoBrowserPopups.Pending expired=c.source.pending();
            c.now=15000;denied(()->c.source.adopt(expired,c.fresh(new WebView())));
            check(original.sends==1&&original.child==null&&!expired.current());original.ack();c.source.reject();check(c.source.confirmed());c.source.finish(true);
        }
        try(Case c=new Case()){
            check(c.source.ready());c.ownerFailure=true;Transport late=new Transport();
            check(!c.core.open(late,true)&&late.sends==0&&!c.core.clean());c.source.reject();
            check(!c.source.confirmed()&&c.source.failure().equals("cdp_popup_native_rejection_unconfirmed"));c.source.finish(true);
            c.ownerFailure=false;check(!c.core.open(late,true)&&late.sends==0);
        }
    }
    private static void childMarker()throws Exception{
        try(Case c=new Case();Case other=new Case()){
            check(c.source.ready()&&other.source.ready());Transport original=new Transport();check(c.core.open(original,false));WebView view=new WebView();
            MdoBrowserLifecycle.Fresh<WebView> fresh=c.fresh(view);
            MdoBrowserPopups.Adoption adoption=c.source.adopt(c.source.pending(),fresh);
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota())){
                unavailable("cdp_popup_native_child_invalid",()->c.source.child(adoption,fresh,adapter));
                original.ack();check(adoption.ok());
                unavailable("cdp_popup_native_child_invalid",()->other.source.child(adoption,fresh,adapter));
                unavailable("cdp_popup_native_child_invalid",()->c.source.child(adoption,c.fresh(new WebView()),adapter));
                try(MdoBrowserCdpPopupNative.Child child=c.source.child(adoption,fresh,adapter)){
                    check(child.current()&&child.belongsTo(c.source.lease())&&!child.belongsTo(other.source.lease())&&child.proof()==null&&view.evaluations==0);
                    unavailable("cdp_popup_native_child_invalid",()->c.source.child(adoption,fresh,adapter));
                    java.util.concurrent.atomic.AtomicBoolean permit=new java.util.concurrent.atomic.AtomicBoolean(true);
                    MdoBrowserCdpMarker.Ticket ticket=child.marker().request(permit::get);permit.set(false);android.os.Handler.drain();
                    check(!ticket.pending()&&!ticket.confirmed()&&ticket.failure().equals("cdp_binding_owner_changed")&&view.evaluations==0);ticket.close();
                    c.core.cancel();check(!child.current());child.close();check(!child.current()&&!child.proof().ok&&!child.proof().effectMayHaveOccurred);
                    check(!child.nativeDestroyed());c.views.remove("child1");check(!child.nativeDestroyed());
                    c.views.viewDestroyed(new WebView());check(!child.nativeDestroyed());c.views.viewDestroyed(view);check(child.nativeDestroyed());
                }
            }
            c.source.reject();check(c.source.confirmed());c.source.finish(true);other.source.reject();other.source.finish(true);
        }
    }
    private static void birthInvalidation()throws Exception{
        for(int mode=0;mode<4;mode++)try(Case c=new Case()){
            check(c.source.ready());Transport original=new Transport();check(c.core.open(original,false));WebView view=new WebView();
            MdoBrowserLifecycle.Fresh<WebView> fresh=c.fresh(view);MdoBrowserPopups.Adoption adoption=c.source.adopt(c.source.pending(),fresh);original.ack();
            try(MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,new MdoBrowserCdpMarker.DocumentQuota());
                MdoBrowserCdpPopupNative.Child child=c.source.child(adoption,fresh,adapter)){
                check(child.current()&&c.source.live()&&adapter.popupCurrent(view));
                if(mode==0){c.views.manual("child1",view,true);c.views.manual("child1",view,false);}
                if(mode==1){c.views.visibility("child1",view,false);c.views.visibility("child1",view,true);}
                if(mode==2){c.views.background(true);c.views.background(false);}
                if(mode==3)c.views.remove("child1");
                check(c.source.live()&&adapter.popupCurrent(view)&&!child.current()&&!child.enqueue()&&!child.nativeDestroyed());
                // Neither a live source/marker nor closing its proof can revive
                // a stale registry birth or certify physical destruction.
                child.close();check(!child.nativeDestroyed());
                if(mode==3){c.views.viewDestroyed(view);check(child.nativeDestroyed());}
            }
            c.source.reject();check(c.source.confirmed());c.source.finish(true);
        }
    }
    public static void main(String[] args)throws Exception{
        closeBarriers();retirementDelivery();deferredRoots();retainedOwners();preparedRetirement();ownership();adoption();stale();cancellation();childMarker();birthInvalidation();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"original_callback_ownership\":true,\"typed_native_consumption\":true,\"transport_quiescence_identity\":true,\"retirement_delivery_identity\":true,\"fresh_child_registry_gate\":true,\"native_destruction_receipt\":true,\"native_popup_adoption_qualified\":false,\"provider_qualified\":false}");
    }
    private static final class CloseWire implements MdoBrowserCdpSession.Wire {
        int closes;public void send(byte[] bytes,int timeout){throw new AssertionError("UI protocol send");}public byte[] receive(int timeout){throw new AssertionError("UI protocol receive");}
        public void close(){closes++;}
    }
    private static void closeBarriers()throws Exception{
        CloseWire ordinaryWire=new CloseWire();MdoBrowserCdpSession ordinary=MdoBrowserCdpSession.owned(ordinaryWire,()->true,(m,s,p)->{},()->0L);
        unavailable("cdp_popup_close_scope_invalid",()->ordinary.deferPopupClose(new MdoBrowserCdpPopupNative.CloseGate()));ordinary.close();check(ordinaryWire.closes==1);
        CloseWire browserWire=new CloseWire();MdoBrowserCdpSession browser=MdoBrowserCdpSession.ownedBrowser(browserWire,()->true,(m,s,p)->{},()->0L);
        MdoBrowserCdpPopupNative.CloseGate empty=new MdoBrowserCdpPopupNative.CloseGate();browser.deferPopupClose(empty);
        unavailable("cdp_popup_close_scope_invalid",()->browser.deferPopupClose(empty));browser.close();browser.close();check(browserWire.closes==1&&empty.complete());
        unavailable("cdp_session_closed",()->browser.deferPopupClose(empty));
        MdoBrowserCdpPopupNative.CloseGate gate=new MdoBrowserCdpPopupNative.CloseGate();CloseWire wire=new CloseWire();
        gate.bind(wire,wire.cancellation());unavailable("cdp_popup_close_scope_invalid",()->gate.bind(wire,wire.cancellation()));
        try(Case a=new Case();Case b=new Case()){
            check(a.source.ready()&&b.source.ready());
            a.source.retainUiOwner();b.source.retainUiOwner();MdoBrowserCdpPopupNative.Retirement one=a.source.retirement(),two=b.source.retirement();
            gate.add(one);gate.add(one);gate.add(two);unavailable("cdp_popup_close_scope_invalid",()->new MdoBrowserCdpPopupNative.CloseGate().add(one));
            Transport at=new Transport(),bt=new Transport();check(a.core.open(at,false)&&b.core.open(bt,false));
            one.onQuiescence(()->{check(!one.current()&&!two.current());MdoBrowserCdpPopupNative.reconcileRetirements();});
            two.onQuiescence(()->{throw new IllegalStateException("controlled_weak_notification_failure");});gate.close(wire);
            check(gate.pending()&&!gate.complete()&&!gate.failed()&&wire.closes==0&&at.sends==1&&bt.sends==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==2);
            unavailable("cdp_popup_close_scope_invalid",()->gate.add(one));
            one.request(true);two.request(true);MdoBrowserCdpPopupNative.reconcileRetirements();at.ack();check(wire.closes==0&&b.budget.used()==1);
            bt.ack();check(wire.closes==1&&gate.complete()&&!gate.pending()&&!gate.failed()&&MdoBrowserCdpPopupNative.retainedUiOwners()==0);
            gate.close(wire);at.ack();bt.ack();check(wire.closes==1&&MdoBrowserCdpPopupNative.creationConfirmed());
            try{gate.close(new CloseWire());throw new AssertionError("Replacement wire was accepted");}catch(IllegalStateException expected){check(true);}
        }
        MdoBrowserCdpPopupNative.CloseGate reusable=new MdoBrowserCdpPopupNative.CloseGate();CloseWire next=new CloseWire();
        reusable.bind(next,next.cancellation());
        try(Case c=new Case()){
            for(int i=0;i<16;i++){
                if(i!=0)c.source.claim(c.adapter.marker().lease());check(c.source.ready());
                c.source.retainUiOwner();MdoBrowserCdpPopupNative.Retirement action=c.source.retirement();reusable.add(action);action.request(true);action.deliver();
                check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&next.closes==0);
            }
            reusable.close(next);check(next.closes==1&&reusable.complete());
        }
    }
    private static void preparedRetirement()throws Exception{
        WebView root=new WebView(),other=new WebView(),child=new WebView(),foreignChild=new WebView();
        MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(8);
        MdoBrowserPopups.Scheduler scheduler=new MdoBrowserPopups.Scheduler(){public long now(){return 100;}public void at(Runnable task,long at){}public void remove(Runnable task){}};
        MdoBrowserPopups<WebView> a=new MdoBrowserPopups<>(root,scheduler,budget,()->true,()->1,blocked->{});
        MdoBrowserPopups<WebView> b=new MdoBrowserPopups<>(other,scheduler,budget,()->true,()->1,blocked->{});
        MdoBrowserPopups.Scope scope=a.claimProtocol(root,()->true),foreign=b.claimProtocol(other,()->true);
        final int[] callbacks={0};a.attachProtocolUi(scope,receipt->{check(receipt.nativeConsumed());callbacks[0]++;});
        check(a.readyProtocol(scope)&&b.readyProtocol(foreign));Transport first=new Transport(),second=new Transport();check(a.open(first,false)&&b.open(second,false));
        MdoBrowserPopups.Pending pending=a.pendingProtocol(scope),wrong=b.pendingProtocol(foreign);
        MdoBrowserPopups.Adoption prepared=a.prepareProtocolAdoption(scope,pending,child),foreignReceipt=b.prepareProtocolAdoption(foreign,wrong,foreignChild);
        check(!prepared.dispatched()&&!prepared.mayHaveOccurred()&&!prepared.nativeConsumed()&&first.sends==0&&pending.current());
        denied(()->a.prepareProtocolAdoption(scope,pending,new WebView()));
        denied(()->a.nativeChildDestroyed(prepared,child));
        denied(()->a.stopForProtocol(scope,prepared));check(scope.current()&&first.sends==0);
        denied(()->a.sendProtocolAdoption(foreign,prepared));denied(()->a.sendProtocolAdoption(scope,foreignReceipt));
        check(pending.current()&&first.sends==0&&second.sends==0);
        a.sendProtocolAdoption(scope,prepared);check(first.sends==1&&first.child==child&&prepared.dispatched()&&prepared.mayHaveOccurred());
        denied(()->a.sendProtocolAdoption(scope,prepared));check(first.sends==1&&!prepared.nativeConsumed());
        denied(()->a.nativeChildDestroyed(prepared,null));denied(()->a.nativeChildDestroyed(prepared,new WebView()));denied(()->a.nativeChildDestroyed(foreignReceipt,foreignChild));
        denied(()->a.stopForProtocol(scope,foreignReceipt));check(scope.current());
        MdoBrowserPopups.Stop stop=a.stopForProtocol(scope,prepared);check(stop.pending()&&!stop.confirmed());
        child.destroy();a.nativeChildDestroyed(prepared,child);check(stop.pending()&&!prepared.nativeConsumed());
        first.ack();check(stop.confirmed()&&prepared.nativeConsumed()&&!prepared.ok()&&callbacks[0]==1&&child.destroyed==1);
        first.ack();check(callbacks[0]==1&&first.sends==1&&stop.release());
        MdoBrowserPopups.Stop rejected=b.stopForProtocol(foreign);check(second.sends==1&&second.child==null&&!foreignReceipt.dispatched());
        second.ack();check(rejected.confirmed()&&!foreignReceipt.nativeConsumed()&&!foreignReceipt.ok()&&!foreignReceipt.mayHaveOccurred()&&rejected.release());
        denied(()->b.sendProtocolAdoption(foreign,foreignReceipt));check(second.sends==1&&budget.used()==0);
        a.close();b.close();a.viewDestroyed(root);b.viewDestroyed(other);
    }
    private static void deferredRoots()throws Exception{
        for(int mode=0;mode<3;mode++)try(Case c=new Case()){
            c.source.retainUiOwner();check(c.source.ready());Transport original=new Transport();original.inline=mode==2;check(c.core.open(original,false));
            final boolean[] prepared={false};final int[] destroys={0};
            MdoBrowserCdpPopupNative.Destruction close=new MdoBrowserCdpPopupNative.Destruction(){
                public boolean ready(){return prepared[0];}
                public void destroy(){check(prepared[0]&&c.budget.used()==0);if(destroys[0]++!=0)throw new AssertionError("Repeated root SDK destroy");c.views.remove("root");c.view.destroy();c.views.viewDestroyed(c.view);MdoBrowserCdpPopupNative.viewDestroyed(c.view);c.core.viewDestroyed(c.view);}
            };
            check(MdoBrowserCdpPopupNative.registerDestruction(c.view,close)&&!c.source.current()&&original.sends==0);
            MdoBrowserCdpPopupNative.activateDestruction(c.view,close);check(original.sends==1&&original.child==null&&c.view.destroyed==0);
            if(mode==1){original.ack();check(destroys[0]==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==1);}
            prepared[0]=true;MdoBrowserCdpPopupNative.activateDestruction(c.view,close);
            if(mode==0){check(c.view.destroyed==0&&c.budget.used()==1);original.ack();}
            check(c.view.destroyed==1&&destroys[0]==1&&c.budget.used()==0&&MdoBrowserCdpPopupNative.retainedUiOwners()==0);
            original.ack();MdoBrowserCdpPopupNative.activateDestruction(c.view,close);check(c.view.destroyed==1&&original.sends==1);
        }
    }
    private static void retirementDelivery()throws Exception{
        try(Case c=new Case()){
            c.source.retainUiOwner();MdoBrowserCdpPopupNative.Retirement old=c.source.retirement();c.source.abandon();check(MdoBrowserCdpPopupNative.retainedUiOwners()==0);
            c.source.claim(c.adapter.marker().lease());c.source.retainUiOwner();check(c.source.ready());Transport fresh=new Transport();check(c.core.open(fresh,false));
            old.request(true);old.deliver();old.deliver();check(fresh.sends==0&&c.source.current()&&c.budget.used()==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==1);
            MdoBrowserCdpPopupNative.Retirement original=c.source.retirement();original.request(false);original.request(true);
            check(MdoBrowserCdpPopupNative.pendingRetirements()==1&&original.pending());MdoBrowserCdpPopupNative.reconcileRetirements();
            check(!original.pending()&&fresh.sends==1&&fresh.child==null&&c.budget.used()==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==1);
            fresh.ack();fresh.ack();check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&c.budget.used()==0);
            unavailable("cdp_popup_native_source_busy",()->c.source.claim(c.adapter.marker().lease()));c.source.finish(true);
        }
        try(Case c=new Case()){
            c.source.retainUiOwner();MdoBrowserCdpPopupNative.Retirement original=c.source.retirement();final int[] delivered={0};
            Runnable notification=()->{delivered[0]++;throw new IllegalStateException("controlled_observer_failure");};c.timers.add(notification);original.onDelivered(notification);
            denied(()->original.onDelivered(notification));original.request(true);MdoBrowserCdpPopupNative.reconcileRetirements();original.deliver();
            check(delivered[0]==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==0&&MdoBrowserCdpPopupNative.creationConfirmed());c.timers.remove(notification);
        }
    }
    private static void retainedOwners()throws Exception{
        check(MdoBrowserCdpPopupNative.retainedUiOwners()==0);
        for(int mode=0;mode<4;mode++)try(Case c=new Case()){
            c.source.retainUiOwner();c.source.retainUiOwner();check(MdoBrowserCdpPopupNative.retainedUiOwners()==1&&c.source.ready());
            Transport original=new Transport();original.inline=mode==3;check(c.core.open(original,false));
            MdoBrowserPopups.Adoption adoption=null;
            if(mode==1)adoption=c.source.adopt(c.source.pending(),c.fresh(new WebView()));
            if(mode==0||mode==1)c.source.reject();else c.core.close();
            check(original.sends==1);c.core.close();c.views.remove("root");c.view.destroy();c.views.viewDestroyed(c.view);
            MdoBrowserCdpPopupNative.viewDestroyed(c.view);c.core.viewDestroyed(c.view);c.core.viewDestroyed(c.view);c.source.finish(false);
            check(c.view.destroyed==1);
            if(mode!=3){
                check(MdoBrowserCdpPopupNative.retainedUiOwners()==1&&c.budget.used()==1&&!c.source.confirmed());
                if(adoption!=null)check(!adoption.nativeConsumed());original.ack();
                if(adoption!=null)check(adoption.nativeConsumed()&&!adoption.ok());
            }
            check(MdoBrowserCdpPopupNative.retainedUiOwners()==0&&c.budget.used()==0&&c.timers.isEmpty());
            original.ack();check(original.sends==1&&MdoBrowserCdpPopupNative.retainedUiOwners()==0);
        }
        Case[] held=new Case[8];
        try{
            for(int i=0;i<held.length;i++){held[i]=new Case();held[i].source.retainUiOwner();check(MdoBrowserCdpPopupNative.retainedUiOwners()==i+1);}
            try(Case ninth=new Case()){unavailable("cdp_popup_native_owner_limit",ninth.source::retainUiOwner);check(MdoBrowserCdpPopupNative.retainedUiOwners()==8);ninth.source.abandon();}
        }finally{for(Case c:held)if(c!=null){c.source.reject();c.source.finish(true);c.close();}}
        check(MdoBrowserCdpPopupNative.retainedUiOwners()==0);
        try(Case c=new Case()){c.source.retainUiOwner();c.source.abandon();check(MdoBrowserCdpPopupNative.retainedUiOwners()==0);}
    }
}
