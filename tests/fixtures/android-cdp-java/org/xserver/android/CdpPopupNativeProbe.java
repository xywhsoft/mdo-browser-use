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
        final WebView view=new WebView();boolean actionOwned=true,ownerFailure;long epoch=1,now;
        final MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(8);
        final List<Runnable> timers=new ArrayList<>();
        final MdoBrowserCdpView adapter=MdoBrowserCdpView.network(view,v->v==view,v->{if(ownerFailure)throw new IllegalStateException("controlled-owner-failure");return v==view&&actionOwned;},new MdoBrowserCdpMarker.DocumentQuota());
        final MdoBrowserPopups<WebView> core=new MdoBrowserPopups<>(view,new MdoBrowserPopups.Scheduler(){
            public long now(){return now;}public void at(Runnable task,long at){timers.add(task);}public void remove(Runnable task){timers.remove(task);}
        },budget,()->true,()->epoch,blocked->{});
        final MdoBrowserCdpPopupNative source;
        Case()throws Exception{source=new MdoBrowserCdpPopupNative(adapter,view,core);source.claim(adapter.marker().lease());}
        public void close(){core.close();core.viewDestroyed(view);adapter.close();check(budget.used()==0&&timers.isEmpty());}
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
            denied(()->c.source.adopt(wrong,new WebView()));denied(()->c.source.adopt(pending,c.view));denied(()->c.source.adopt(pending,null));
            denied(()->c.core.adopt(pending.id,new WebView(),(ok,code,may)->{}));check(original.sends==0&&pending.current());
            WebView child=new WebView();MdoBrowserPopups.Adoption receipt=c.source.adopt(pending,child);
            check(original.sends==1&&original.child==child&&!pending.current()&&c.source.pending()==null);
            check(receipt.pending()&&!receipt.ok()&&!receipt.nativeConsumed()&&receipt.mayHaveOccurred());
            check(c.core.adoptedTo(receipt,child)&&!c.core.adoptedTo(receipt,new WebView())&&!foreign.core.adoptedTo(receipt,child));
            denied(()->c.source.adopt(pending,new WebView()));original.ack();original.ack();
            check(!receipt.pending()&&receipt.ok()&&receipt.nativeConsumed()&&receipt.failure()==null&&original.sends==1&&c.budget.used()==0);
            c.source.reject();check(c.source.confirmed());c.source.finish(true);
            c.source.claim(c.adapter.marker().lease());check(c.source.ready());
            Transport replacement=new Transport();check(c.core.open(replacement,false));MdoBrowserPopups.Pending next=c.source.pending();
            check(next!=null&&next!=pending&&!pending.current());denied(()->c.source.adopt(pending,new WebView()));check(replacement.sends==0);
            c.source.reject();replacement.ack();check(!next.current()&&c.source.confirmed());c.source.finish(true);
            foreign.source.reject();other.ack();foreign.source.finish(true);
        }
        for(int mode=0;mode<5;mode++)try(Case c=new Case()){
            check(c.source.ready());Transport original=new Transport();check(c.core.open(original,false));MdoBrowserPopups.Pending pending=c.source.pending();
            original.fail=mode==0;original.oom=mode==1;original.inline=mode==4;
            MdoBrowserPopups.Adoption receipt=c.source.adopt(pending,new WebView());
            if(mode<2){check(!receipt.pending()&&!receipt.ok()&&receipt.failure().equals("popup_transport_unconfirmed"));original.ack();check(!receipt.ok()&&receipt.nativeConsumed());}
            else if(mode==2){c.actionOwned=false;original.ack();check(!receipt.ok()&&receipt.nativeConsumed()&&receipt.failure().equals("popup_source_changed"));}
            else if(mode==3){c.now=5001;for(Runnable timer:new ArrayList<>(c.timers))timer.run();check(!receipt.ok()&&receipt.failure().equals("popup_transport_unconfirmed"));original.ack();check(!receipt.ok());}
            else check(receipt.ok()&&receipt.nativeConsumed()&&!receipt.pending());
            check(original.sends==1&&!pending.current());denied(()->c.source.adopt(pending,new WebView()));
        }
    }
    private static void stale()throws Exception{
        try(Case c=new Case()){
            check(c.source.ready());Transport nativeWindow=new Transport();check(c.core.open(nativeWindow,false));MdoBrowserPopups.Pending pending=c.source.pending();
            c.actionOwned=false;check(!c.source.current()&&!c.source.live()&&!pending.current());c.actionOwned=true;
            check(!c.source.current()&&!c.source.ready());denied(()->c.source.adopt(pending,new WebView()));check(nativeWindow.sends==0);
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
            c.now=15000;denied(()->c.source.adopt(expired,new WebView()));
            check(original.sends==1&&original.child==null&&!expired.current());original.ack();c.source.reject();check(c.source.confirmed());c.source.finish(true);
        }
        try(Case c=new Case()){
            check(c.source.ready());c.ownerFailure=true;Transport late=new Transport();
            check(!c.core.open(late,true)&&late.sends==0&&!c.core.clean());c.source.reject();
            check(!c.source.confirmed()&&c.source.failure().equals("cdp_popup_native_rejection_unconfirmed"));c.source.finish(true);
            c.ownerFailure=false;check(!c.core.open(late,true)&&late.sends==0);
        }
    }
    public static void main(String[] args)throws Exception{
        ownership();adoption();stale();cancellation();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"original_callback_ownership\":true,\"typed_native_consumption\":true,\"native_popup_adoption_qualified\":false,\"provider_qualified\":false}");
    }
}
