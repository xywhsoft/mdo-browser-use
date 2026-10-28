package org.xserver.android;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.UUID;

/** One installed isolated world for an exact native content view. It does not
 * enable the product's unfinished input/capture/frame/transfer capabilities. */
final class MdoBrowserDocument implements AutoCloseable {
    static final int CHANNEL=1,BACKGROUND=2,MANUAL=4,DIALOG=8,NAVIGATION=16;
    static final String HELLO="mdo-browser-document-v1";
    static final String BOOTSTRAP="(()=>{if(self!==top||globalThis.__mdoBrowserDocumentReady)return;"+
        "Object.defineProperty(globalThis,'__mdoBrowserDocumentReady',{value:true});"+
        MdoBrowserWorld.LISTENER+".postMessage('"+HELLO+"')})()";
    private final MdoBrowserWorld world;
    private final BooleanSupplier owned;
    private final LongSupplier epoch;
    private final Thread owner=Thread.currentThread();
    private MdoBrowserWorld.Peer candidate,active;
    private long activeEpoch,serial;
    private int blockers;
    private boolean loading=true,closed,profile;
    private String fault,nativeDocument;

    MdoBrowserDocument(Object view,ClassLoader loader,MdoBrowserWorld.Scheduler scheduler,
                       BooleanSupplier owned,LongSupplier epoch,String profile) throws MdoBrowserWorld.Failure {
        this(view,loader,scheduler,owned,epoch,profile,new MdoBrowserWorld.Budget(16));
    }
    MdoBrowserDocument(Object view,ClassLoader loader,MdoBrowserWorld.Scheduler scheduler,
                       BooleanSupplier owned,LongSupplier epoch,String profile,MdoBrowserWorld.Budget budget) throws MdoBrowserWorld.Failure {
        if(owned==null||epoch==null)throw new MdoBrowserWorld.Failure("world_arguments_invalid");
        this.owned=owned;this.epoch=epoch;
        world=MdoBrowserWorld.open(view,loader,scheduler,new MdoBrowserWorld.Receiver(){
            public void message(MdoBrowserWorld.Peer peer,String text){receive(peer,text);}
            public void fault(String code){failed(code);}
        },budget);
        try{
            if(profile!=null){world.profile(profile);this.profile=true;}
            world.install(BOOTSTRAP);
        }catch(MdoBrowserWorld.Failure failure){world.close();throw failure;}
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Document requires its owning UI thread");}
    private void receive(MdoBrowserWorld.Peer peer,String text){
        thread();if(closed||!owned.getAsBoolean())return;
        if(!HELLO.equals(text)){failed("world_message_invalid");return;}
        candidate=peer;if(active==null)adopt();
    }
    private void adopt(){
        if(!closed&&fault==null&&!loading&&blockers==0&&owned.getAsBoolean()&&world.current(candidate)){
            // A replacement peer may precede onPageStarted. A blocker release
            // must not adopt it and let the later start retire the new peer.
            if(active!=null&&active!=candidate)return;
            long next=epoch.getAsLong();
            String identity=active!=candidate||activeEpoch!=next||nativeDocument==null?"fd-"+UUID.randomUUID():nativeDocument;
            active=candidate;activeEpoch=next;nativeDocument=identity;
        }
    }
    private void failed(String code){
        thread();if(closed)return;fault=code;serial++;active=null;nativeDocument=null;
        world.cancelPending(code);
    }
    void navigation(){
        thread();if(closed)return;loading=true;blockers&=~NAVIGATION;serial++;nativeDocument=null;
        MdoBrowserWorld.Peer previous=active;active=null;
        if(candidate==previous)candidate=null;
        // A new native peer may already have arrived. Retire the previously
        // admitted peer specifically, never an unrelated replacement peer.
        world.invalidate(previous);world.cancelPending("frame_changed");
    }
    void loaded(){thread();if(!closed){loading=false;adopt();}}
    void block(int mask,boolean value){
        thread();if(closed)return;
        if(mask!=CHANNEL&&mask!=BACKGROUND&&mask!=MANUAL&&mask!=DIALOG&&mask!=NAVIGATION)throw new IllegalArgumentException("Invalid document blocker");
        int next=value?blockers|mask:blockers&~mask;
        if(next==blockers)return;blockers=next;
        if(value){serial++;world.cancelPending(reason());}else adopt();
    }
    private String reason(){
        if(closed)return"world_closed";
        if(fault!=null)return fault;
        if(!owned.getAsBoolean())return"frame_changed";
        if((blockers&CHANNEL)!=0)return"main_document_changed";
        if((blockers&BACKGROUND)!=0)return"world_view_unavailable";
        if((blockers&MANUAL)!=0)return"user_taken_control";
        if((blockers&DIALOG)!=0)return"dialog_open";
        if((blockers&NAVIGATION)!=0)return"navigation_pending";
        if(loading||nativeDocument==null||!world.current(active)||activeEpoch!=epoch.getAsLong())return"world_not_ready";
        return null;
    }
    boolean ready(){thread();return reason()==null;}
    String identity(){thread();return reason()==null?nativeDocument:null;}
    String phase(){thread();String code=reason();return code==null?"ready":code;}
    int pending(){thread();return world.pending();}
    int outstanding(){thread();return world.outstanding();}
    int sharedOutstanding(){thread();return world.sharedOutstanding();}
    int sharedLimit(){thread();return world.sharedLimit();}
    boolean profileAssigned(){thread();return profile;}
    void cancel(){thread();if(!closed){serial++;world.cancelPending("world_cancelled");}}
    void evaluate(String expression,long timeout,MdoBrowserWorld.Completion complete) throws MdoBrowserWorld.Failure {
        evaluate(null,expression,timeout,complete);
    }
    void evaluate(String expected,String expression,long timeout,MdoBrowserWorld.Completion complete) throws MdoBrowserWorld.Failure {
        thread();if(complete==null)throw new MdoBrowserWorld.Failure("world_arguments_invalid");
        String code=reason();if(code!=null)throw new MdoBrowserWorld.Failure(code);
        if(expected!=null&&!expected.equals(nativeDocument))throw new MdoBrowserWorld.Failure("frame_changed");
        final MdoBrowserWorld.Peer peer=active;final long generation=serial,document=activeEpoch;
        final String identity=nativeDocument;
        peer.evaluate(expression,timeout,result->{
            if(closed){
                complete.complete(new MdoBrowserWorld.Result(false,null,"world_closed",result.evaluationMayHaveOccurred));
            }else if(!result.ok)complete.complete(result);
            else if(generation!=serial||document!=epoch.getAsLong()||!identity.equals(nativeDocument)||!owned.getAsBoolean()||!world.current(peer)){
                complete.complete(new MdoBrowserWorld.Result(false,null,"frame_changed",result.evaluationMayHaveOccurred));
            }else complete.complete(result);
        });
    }
    @Override public void close(){
        thread();if(!closed){closed=true;serial++;active=null;candidate=null;nativeDocument=null;}world.close();
    }
    boolean cleanupConfirmed(){thread();return closed&&world.cleanupConfirmed();}
    void viewDestroyed(Object original){thread();if(!closed)throw new IllegalStateException("Close the document before native teardown");world.viewDestroyed(original);}
}
