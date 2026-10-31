package org.xserver.android;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exact physical-target ancestry ledger; protocol/UI/provider peers controlled. */
public final class CdpNetworkFramesProbe {
    private static int checks;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Network frame check "+(checks+1));checks++;}
    private static void refused(String code,Work work)throws Exception{try{work.run();throw new AssertionError("Expected "+code);}catch(IOException failure){check(code.equals(failure.getMessage())&&failure.getCause()==null);}}
    private static JSONObject frame(String raw,String parent,String loader)throws Exception{
        JSONObject result=new JSONObject().put("id",raw).put("loaderId",loader).put("url","https://private.invalid/never-retain");
        if(parent!=null)result.put("parentId",parent);return result;
    }
    private static JSONObject tree(String raw,String parent,String loader)throws Exception{return new JSONObject().put("frame",frame(raw,parent,loader));}
    private static MdoBrowserCdpNetworkFrames open()throws Exception{
        MdoBrowserCdpNetworkFrames frames=new MdoBrowserCdpNetworkFrames("root","load-root");frames.initialize(tree("root",null,"load-root"),frames.revision());return frames;
    }
    private static void attach(MdoBrowserCdpNetworkFrames frames,String raw,String parent)throws Exception{frames.event("Page.frameAttached",null,new JSONObject().put("frameId",raw).put("parentFrameId",parent));}
    private static void detach(MdoBrowserCdpNetworkFrames frames,String raw,String reason)throws Exception{frames.event("Page.frameDetached",null,new JSONObject().put("frameId",raw).put("reason",reason));}
    private static void commit(MdoBrowserCdpNetworkFrames frames,String raw,String parent,String loader)throws Exception{frames.event("Page.frameNavigated",null,new JSONObject().put("frame",frame(raw,parent,loader)));}
    private static void ancestry()throws Exception{
        try(MdoBrowserCdpNetworkFrames frames=open()){
            MdoBrowserCdpNetworkFrames.Origin root=frames.origin("root");check(root.root&&root.current()&&root.frame.startsWith("nf-")&&root.document.startsWith("nd-"));
            refused("download_frame_unproven",()->frames.origin("unknown"));attach(frames,"middle","root");attach(frames,"leaf","middle");
            MdoBrowserCdpNetworkFrames.Origin middle=frames.origin("middle"),leaf=frames.origin("leaf");check(!leaf.root&&leaf.current()&&!leaf.frame.equals(root.frame));
            JSONObject unrelated=new JSONObject().put("frameId","leaf").put("parentFrameId","middle");frames.event("Page.frameAttached",null,unrelated);unrelated.put("parentFrameId","unowned");check(leaf.current());
            commit(frames,"middle","root","middle-new");check(root.current()&&!middle.current()&&!leaf.current());refused("download_frame_unproven",()->frames.origin("leaf"));
            MdoBrowserCdpNetworkFrames.Origin changed=frames.origin("middle");check(changed.frame.equals(middle.frame)&&!changed.document.equals(middle.document)&&changed.current());
            attach(frames,"leaf","middle");MdoBrowserCdpNetworkFrames.Origin fresh=frames.origin("leaf");check(fresh.current()&&!fresh.frame.equals(leaf.frame));
            frames.event("Page.documentOpened",null,new JSONObject().put("frame",frame("leaf",null,"document-opened")));check(!fresh.current()&&frames.origin("leaf").current());
            commit(frames,"root",null,"root-new");check(!root.current()&&!changed.current()&&frames.origin("root").current());
            refused("download_frame_unproven",()->frames.origin("middle"));
            AtomicReference<Throwable> error=new AtomicReference<>();Thread foreign=new Thread(()->{try{frames.origin("root");}catch(Throwable failure){error.set(failure);}});foreign.start();foreign.join();
            check(error.get() instanceof IOException&&error.get().getMessage().equals("cdp_wrong_worker"));check(frames.origin("root").current());
        }
    }
    private static void removalAndSwap()throws Exception{
        try(MdoBrowserCdpNetworkFrames frames=open()){
            attach(frames,"child","root");MdoBrowserCdpNetworkFrames.Origin old=frames.origin("child");detach(frames,"child","swap");
            check(!old.current());refused("download_frame_unproven",()->frames.origin("child"));attach(frames,"child","root");refused("download_frame_unproven",()->frames.origin("child"));
            commit(frames,"child","root","returned-process");MdoBrowserCdpNetworkFrames.Origin returned=frames.origin("child");check(returned.current()&&!returned.document.equals(old.document));
            frames.event("Page.frameSubtreeWillBeDetached",null,new JSONObject().put("frameId","child"));check(!returned.current());refused("download_frame_unproven",()->frames.origin("child"));
            detach(frames,"child","remove");MdoBrowserCdpNetworkFrames.Origin root=frames.origin("root");Thread ui=new Thread(frames::close);ui.start();ui.join();
            check(!root.current());refused("download_frame_owner_closed",()->frames.origin("root"));
        }
    }
    private static void bootstrap()throws Exception{
        try(MdoBrowserCdpNetworkFrames frames=new MdoBrowserCdpNetworkFrames("root","load-root")){
            refused("download_frame_unproven",()->frames.origin("root"));long before=frames.revision();attach(frames,"child","root");
            refused("download_frame_bootstrap_changed",()->frames.initialize(tree("root",null,"load-root"),before));
            JSONObject snapshot=tree("root",null,"load-root").put("childFrames",new JSONArray().put(tree("child","root","load-child")));
            frames.initialize(snapshot,frames.revision());snapshot.getJSONArray("childFrames").getJSONObject(0).getJSONObject("frame").put("id","forged");
            check(frames.origin("child").current());refused("download_frame_bootstrap_replayed",()->frames.initialize(snapshot,frames.revision()));
        }
        for(int mode=0;mode<5;mode++)try(MdoBrowserCdpNetworkFrames frames=new MdoBrowserCdpNetworkFrames("root","load-root")){
            JSONObject snapshot=tree("root",null,"load-root");String error="download_frame_metadata_invalid";
            if(mode==0){snapshot=tree("foreign",null,"load-root");error="download_frame_root_changed";}
            if(mode==1){snapshot=tree("root",null,"wrong-loader");error="download_frame_root_changed";}
            if(mode==2){snapshot.put("childFrames",new JSONArray().put(tree("child","foreign","x")));error="download_frame_parent_unproven";}
            if(mode==3)snapshot.put("childFrames",new JSONArray().put(tree("child","root","x")).put(tree("child","root","x")));
            if(mode==4)snapshot.put("childFrames",new JSONObject());
            JSONObject bad=snapshot;refused(error,()->frames.initialize(bad,frames.revision()));refused("download_frame_unproven",()->frames.origin("root"));
        }
    }
    private static void limitsAndFaults()throws Exception{
        try(MdoBrowserCdpNetworkFrames frames=open()){
            for(int i=0;i<31;i++)attach(frames,"child-"+i,"root");MdoBrowserCdpNetworkFrames.Origin root=frames.origin("root");
            refused("download_frame_limit",()->attach(frames,"excess","root"));check(!root.current());refused("download_frame_owner_closed",()->frames.origin("root"));
        }
        try(MdoBrowserCdpNetworkFrames frames=open()){
            String parent="root";for(int i=0;i<31;i++){String child="depth-"+i;attach(frames,child,parent);parent=child;}
            check(frames.origin(parent).current());String last=parent;refused("download_frame_parent_unproven",()->attach(frames,"too-deep",last));
        }
        try(MdoBrowserCdpNetworkFrames frames=open()){
            MdoBrowserCdpNetworkFrames.Origin first=null;
            for(int i=0;i<4094;i++){attach(frames,"child","root");MdoBrowserCdpNetworkFrames.Origin next=frames.origin("child");
                if(i==0)first=next;else if(i==1)check(!first.current()&&!next.frame.equals(first.frame));detach(frames,"child","remove");}
            refused("download_frame_lifetime_limit",()->attach(frames,"extra","root"));
        }
        for(int mode=0;mode<4;mode++)try(MdoBrowserCdpNetworkFrames frames=open()){
            MdoBrowserCdpNetworkFrames.Origin root=frames.origin("root");final int scenario=mode;
            refused(mode==0?"download_frame_parent_unproven":mode==1?"download_frame_root_changed":mode==2?"download_frame_session_unproven":"download_frame_metadata_invalid",()->{
                if(scenario==0)attach(frames,"child","unproved");
                if(scenario==1)detach(frames,"root","remove");
                if(scenario==2)frames.event("Page.frameAttached","foreign-session",new JSONObject().put("frameId","child").put("parentFrameId","root"));
                if(scenario==3)frames.event("Page.frameAttached",null,new JSONObject().put("frameId",true).put("parentFrameId","root"));
            });
            check(!root.current());
        }
    }
    private static void withinDocumentLedger()throws Exception{
        try(MdoBrowserCdpNetworkFrames frames=open()){
            MdoBrowserCdpNetworkFrames.Origin original=frames.origin("root");String document=original.document;long before=frames.revision();
            for(String type:new String[]{"fragment","historyApi","other"}){
                frames.event("Page.navigatedWithinDocument",null,new JSONObject().put("frameId","root").put("url","https://private.invalid/#"+type).put("navigationType",type));
                check(original.current()&&frames.origin("root").document.equals(document)&&frames.rootLoader().equals("load-root"));
            }
            check(frames.revision()==before+3);frames.event("Page.navigatedWithinDocument",null,new JSONObject().put("frameId","child").put("url",true));check(original.current()&&frames.revision()==before+3);
            frames.event("Page.navigatedWithinDocument",null,new JSONObject().put("frameId","root").put("url","data:text/plain,private"));check(original.current());
        }
        for(int mode=0;mode<4;mode++)try(MdoBrowserCdpNetworkFrames frames=open()){
            MdoBrowserCdpNetworkFrames.Origin original=frames.origin("root");JSONObject params=new JSONObject().put("frameId","root").put("url","https://private.invalid/#valid");
            if(mode==0)params.put("url",true);if(mode==1)params.put("navigationType","foreign");if(mode==2)params.put("url",new String(new char[8193]).replace('\0','x'));
            final int scenario=mode;refused(mode==3?"download_frame_session_unproven":"download_frame_metadata_invalid",()->frames.event("Page.navigatedWithinDocument",scenario==3?"foreign-session":null,params));check(!original.current());
        }
        byte[] one=MdoBrowserCdpSession.navigationDigest("https://private.invalid/#%E4%BD%A0"),two=MdoBrowserCdpSession.navigationDigest("https://private.invalid/#%E4%BD%A0"),other=MdoBrowserCdpSession.navigationDigest("https://private.invalid/#other");
        check(one.length==32&&java.security.MessageDigest.isEqual(one,two)&&!java.security.MessageDigest.isEqual(one,other));
        refused("cdp_navigation_destination_invalid",()->MdoBrowserCdpSession.navigationDigest("https://user:private@private.invalid/"));
    }
    private static void traversalEvents()throws Exception{
        String destination="https://history.invalid/target";byte[] digest=MdoBrowserCdpSession.navigationDigest(destination);
        for(int mode=0;mode<17;mode++)try(MdoBrowserCdpNetworkFrames frames=open()){
            final int scenario=mode;MdoBrowserCdpNetworkFrames.Origin previous=frames.origin("root");
            CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();wire.extra=command->true;
            try(MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,()->true,frames,wire)){
                String action=mode==12?"reload":"back";MdoBrowserCdpSession.Navigation ticket=mode==12?session.reloadRoot("root","load-root",5000):session.historyRoot("root",17,5000);
                int id=wire.commands.get(0).getInt("id");long before=frames.revision();
                JSONObject start=new JSONObject().put("frameId",mode==9?"child":"root").put("loaderId",mode==11?"wrong-loader":mode==13?"load-root":"fresh-loader").put("url",mode==6?"https://history.invalid/wrong":destination).put("navigationType",mode==7?"differentDocument":mode==12?"reload":"historyDifferentDocument");
                if(mode!=0)frames.event("Page.frameStartedNavigating",null,start);
                if(mode==4)start.put("url","https://history.invalid/changed-input").put("loaderId","changed-input");
                if(mode==8)before=frames.revision();
                if(mode!=1){JSONObject committed=frame("root",null,mode==13||mode==16?"load-root":"fresh-loader");frames.event(mode==10?"Page.documentOpened":"Page.frameNavigated",null,new JSONObject().put("frame",committed).put("type",mode==16?"BackForwardCacheRestore":"Navigation"));}
                check(frames.traversal(ticket,before,action,"load-root",previous,digest)==null);
                if(mode!=2){wire.add(mode==3?new JSONObject().put("id",id).put("error",new JSONObject().put("code",-32601).put("message","private-error")):new JSONObject().put("id",id).put("result",new JSONObject()));session.poll(100);}
                if(mode==14)frames.event("Page.frameStartedNavigating",null,new JSONObject().put("frameId","root").put("loaderId","next-loader").put("url",destination).put("navigationType","historyDifferentDocument"));
                if(mode==15)for(int i=0;i<32;i++)frames.event("Page.frameNavigated",null,new JSONObject().put("frame",frame("root",null,"unrelated-"+i)));
                MdoBrowserCdpNetworkFrames.Traversal proof=frames.traversal(ticket,before,action,"load-root",previous,digest);
                boolean valid=mode==4||mode==5||mode==12||mode==13||mode==16;
                check((proof!=null)==valid);
                if(proof!=null){
                    check(proof.origin.current()&&!proof.sameDocument);MdoBrowserCdpNetworkFrames.Origin retained=proof.origin;
                    if(mode==5)frames.event("Page.navigatedWithinDocument",null,new JSONObject().put("frameId","root").put("url",destination));
                    else frames.event("Page.frameStartedNavigating",null,new JSONObject().put("frameId","root").put("loaderId","future").put("url",destination).put("navigationType","historyDifferentDocument"));
                    check(!retained.current());
                }
            }wire.cleared();
        }
        for(int mode=0;mode<5;mode++)try(MdoBrowserCdpNetworkFrames frames=open()){
            MdoBrowserCdpNetworkFrames.Origin previous=frames.origin("root");JSONObject start=new JSONObject().put("frameId","root").put("loaderId","next").put("url",destination).put("navigationType","reload");
            if(mode==0)start.put("url",17);if(mode==1)start.put("navigationType","unknown");if(mode==2)start.put("loaderId",true);if(mode==3)start.put("url",String.join("",java.util.Collections.nCopies(8193,"a")));
            final int scenario=mode;refused(mode==4?"download_frame_session_unproven":"download_frame_metadata_invalid",()->frames.event("Page.frameStartedNavigating",scenario==4?"foreign-session":null,start));check(!previous.current());
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{ancestry();removalAndSwap();bootstrap();limitsAndFaults();withinDocumentLedger();traversalEvents();}catch(Exception failure){throw new RuntimeException(failure);}}).get(20,java.util.concurrent.TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"actual_network_ancestry\":true,\"native_navigation_epochs\":true,\"same_document_epoch_separate\":true,\"root_control_event_proofs\":true,\"framework_verified\":false,\"provider_qualified\":false}");
    }
}
