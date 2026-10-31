package org.xserver.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Actual dispatcher/codec plus original local-kernel JNI navigation/body
 * pipeline. The native browser/provider/Android callbacks remain unqualified. */
public final class CdpNavigationProbe {
    private static int checks,realSessions,realCommitSessions,realControlSessions,entropyBefore,entropyAfter;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Navigation check "+(checks+1));checks++;}
    private static void refused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected "+code);}catch(IOException failure){check(code.equals(failure.getMessage())&&failure.getCause()==null);}
    }
    private static JSONObject ack(int id,JSONObject value)throws Exception{return new JSONObject().put("id",id).put("result",value);}
    private static JSONObject reply()throws Exception{return new JSONObject().put("frameId","frame-owned");}
    private static final class Case implements AutoCloseable {
        final CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();final AtomicBoolean live=new AtomicBoolean(true);
        final MdoBrowserCdpSession session;int request;MdoBrowserCdpSession.Events events;
        Case(){session=MdoBrowserCdpSession.owned(wire,live::get,(method,child,params)->{if(events!=null)events.event(method,child,params);},wire);
            wire.extra=command->{if(command.getString("method").equals("Page.navigate")){request=command.getInt("id");check(command.length()==3&&command.getJSONObject("params").length()==2&&command.getJSONObject("params").getString("frameId").equals("frame-owned"));return true;}return false;};}
        MdoBrowserCdpSession.Navigation begin()throws Exception{return session.navigateRoot("about:blank","frame-owned",1000);}
        void answer(JSONObject value)throws Exception{wire.add(ack(request,value));session.poll(20,100);}
        public void close(){session.close();wire.cleared();}
    }
    private static void nativeClassification()throws Exception{
        for(int mode=0;mode<7;mode++)try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.begin();check(navigation.pending()&&navigation.effectMayHaveOccurred()&&c.session.pendingControls()==1&&c.session.idle());
            JSONObject value=reply();if(mode<2)value.put("isDownload",mode==0);if(mode==2||mode==0)value.put("errorText","net::ERR_ABORTED");
            if(mode==3)value.put("loaderId","new-loader");
            if(mode==4)value.put("isDownload",true).put("errorText","net::ERR_CONNECTION_RESET");
            if(mode==5)value.put("isDownload",false).put("errorText","private-provider-description-with-url");
            if(mode==6)value.put("errorText","");
            c.answer(value);MdoBrowserCdpSession.NavigationResult result=navigation.result();
            check(result.loaderPresent==(mode==3));
            check(result.acknowledged&&!navigation.pending()&&result.effectMayHaveOccurred&&c.session.pendingControls()==0);
            check(result.downloadKnown==(mode<2||mode==4||mode==5)&&result.download==(mode==0||mode==4));
            check(result.ok==(mode==0||mode==1||mode==3||mode==6)&&java.util.Objects.equals(result.code,result.ok?null:mode==2?"cdp_navigation_download_unproven":"cdp_navigation_failed"));
            check(java.util.Objects.equals(result.nativeError,mode==0||mode==2?"net::ERR_ABORTED":mode==4?"net::ERR_CONNECTION_RESET":null));
            c.session.close();check(navigation.result()==result);
        }
        for(int code:new int[]{-32601,-32602,-32000})try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.begin();c.wire.add(new JSONObject().put("id",c.request).put("error",new JSONObject().put("code",code).put("message","private-provider-error")));c.session.poll(100);
            MdoBrowserCdpSession.NavigationResult result=navigation.result();check(result.acknowledged&&!result.ok&&!result.downloadKnown&&!result.download&&result.effectMayHaveOccurred==(code!=-32601));
            check(result.code.equals(code==-32601?"cdp_navigation_unsupported":"cdp_navigation_rejected")&&c.session.idle());
            check(c.begin().pending());
        }
    }
    private static void admission()throws Exception{
        try(Case c=new Case()){
            for(String url:new String[]{"javascript:void(0)","file:///private","data:text/html,x","http://u:p@host/","https://host/\n", "https://host/\uD800","https://host:65536/","https://host:-2/","about:srcdoc","",null})
                refused("cdp_navigation_arguments_invalid",()->c.session.navigateRoot(url,"frame-owned",1000));
            refused("cdp_navigation_arguments_invalid",()->c.session.navigateRoot("about:blank","caller invalid frame",1000));
            refused("cdp_navigation_arguments_invalid",()->c.session.navigateRoot("about:blank","frame-owned",99));
            refused("cdp_navigation_arguments_invalid",()->c.session.navigateRoot("about:blank","frame-owned",600001));
            check(c.wire.commands.isEmpty());MdoBrowserCdpSession.Navigation first=c.begin();
            refused("cdp_navigation_pending",c::begin);check(c.wire.commands.size()==1&&first.pending());
            c.events=(method,child,value)->refusedEvent(c);c.wire.add(new JSONObject().put("method","Page.frameNavigated").put("params",new JSONObject()));c.session.poll(100);
            check(c.wire.commands.size()==1&&first.pending());
            c.answer(reply().put("isDownload",false));
            java.lang.reflect.Field count=c.session.getClass().getDeclaredField("navigationCount");count.setAccessible(true);count.setInt(c.session,4096);
            refused("cdp_navigation_limit",c::begin);check(c.wire.commands.size()==1);
        }
        try(Case c=new Case()){
            final String[] code={null};Thread thread=new Thread(()->{try{c.begin();}catch(Exception failure){code[0]=failure.getMessage();}});thread.start();thread.join();check("cdp_wrong_worker".equals(code[0])&&c.wire.commands.isEmpty());
        }
    }
    private static void refusedEvent(Case c)throws IOException{
        try{c.begin();throw new AssertionError("Navigation reentered an event");}catch(IOException error){check(error.getMessage().equals("cdp_session_busy"));}catch(Exception error){throw new IOException(error);}
    }
    private static void invalidReplies()throws Exception{
        for(int mode=0;mode<7;mode++)try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.begin();JSONObject value=reply();
            if(mode==0)value.put("frameId","other-root");if(mode==1)value.put("frameId",JSONObject.NULL);if(mode==2)value.put("isDownload","true");
            if(mode==3)value.put("loaderId",JSONObject.NULL);if(mode==4)value.put("loaderId","bad loader");if(mode==5)value.put("errorText",1);
            if(mode==6){c.session.admitChild("child");c.wire.add(ack(c.request,value).put("sessionId","child"));}
            else c.wire.add(ack(c.request,value));
            refused(mode==6?"cdp_navigation_reply_mismatch":"cdp_navigation_reply_invalid",()->c.session.poll(100));
            check(!navigation.result().acknowledged&&!navigation.result().ok&&navigation.result().effectMayHaveOccurred&&navigation.result().code.equals("cdp_navigation_reply_invalid"));
        }
    }
    private static void failureAndOrdering()throws Exception{
        try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.begin();c.wire.now=1000;
            refused("cdp_navigation_timeout",()->c.session.poll(100));MdoBrowserCdpSession.NavigationResult terminal=navigation.result();
            check(terminal.code.equals("cdp_navigation_timeout")&&!terminal.acknowledged&&terminal.effectMayHaveOccurred&&c.wire.commands.size()==1);
            c.wire.add(ack(c.request,reply().put("isDownload",true)));refused("cdp_session_closed",()->c.session.poll(100));check(navigation.result()==terminal);
        }
        try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.begin();c.wire.now=-1;refused("cdp_navigation_timeout",()->c.session.poll(100));check(navigation.result().code.equals("cdp_navigation_timeout"));
        }
        try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.begin();c.session.close();check(navigation.result().code.equals("cdp_navigation_connection_closed")&&navigation.result().effectMayHaveOccurred&&!navigation.result().acknowledged);
        }
        try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.begin();CdpBodyProbe.Send previous=c.wire.extra;
            c.wire.extra=command->{if(command.getString("method").equals("Runtime.evaluate"))c.wire.add(ack(c.request,reply().put("isDownload",false)));return previous.run(command);};
            c.session.call("Runtime.evaluate",new JSONObject(),null,100,false);check(navigation.result().acknowledged&&navigation.result().ok&&c.session.pendingControls()==0);
        }
        for(boolean close:new boolean[]{false,true})try(Case c=new Case()){
            final MdoBrowserCdpSession.Navigation[] original={null};
            c.wire.extra=command->{java.lang.reflect.Field field=c.session.getClass().getDeclaredField("navigation");field.setAccessible(true);original[0]=(MdoBrowserCdpSession.Navigation)field.get(c.session);
                if(close){c.session.close();return true;}throw new IOException("private-partial-send-detail");};
            refused(close?"cdp_session_closed":"cdp_navigation_send_failed",c::begin);
            check(original[0]!=null&&original[0].result().effectMayHaveOccurred&&!original[0].result().acknowledged&&original[0].result().code.equals(close?"cdp_navigation_connection_closed":"cdp_navigation_send_failed"));
            check(c.wire.commands.size()==1&&c.session.pendingControls()==0);
        }
        try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.begin();for(int i=0;i<4;i++){c.session.poll(20,100);check(navigation.pending());}
            c.wire.now=999;c.session.poll(20,100);throw new AssertionError("A poll extended the original navigation deadline");
        }catch(MdoBrowserCdpSession.Failure expected){check(expected.getMessage().equals("cdp_navigation_timeout"));}
        try(Case c=new Case()){
            MdoBrowserCdpSession.Navigation navigation=c.session.navigateRoot("about:blank","frame-owned",600000);
            c.wire.now=120001;c.session.poll(20,100);check(navigation.pending()&&c.session.pendingControls()==1);
            c.wire.now=599999;refused("cdp_navigation_timeout",()->c.session.poll(20,100));check(navigation.result().effectMayHaveOccurred&&navigation.result().code.equals("cdp_navigation_timeout"));
        }
    }
    private static void realWire(int mode)throws Exception{
        CdpNativeProbe.fixtureStart(mode);final MdoBrowserCdpSession[] session={null};List<MdoBrowserCdpSession.Body> bodies=new ArrayList<>();
        try{
            session[0]=MdoBrowserCdpSession.open("test-001",500,()->true,(m,s,p)->bodies.add(session[0].takePausedBody(64,15000)));
            MdoBrowserCdpSession.Navigation navigation=session[0].navigateRoot("about:blank","frame-owned",15000);check(navigation.pending()&&session[0].idle());
            if(mode==42){
                for(int i=0;i<20&&bodies.isEmpty();i++)session[0].poll(20,100);check(bodies.size()==1);MdoBrowserCdpSession.Body body=bodies.get(0);
                for(int i=0;i<20&&!body.ready();i++)session[0].poll(20,100);check(body.ready()&&navigation.pending());
                try(MdoBrowserCdpSession.BodyChunk bytes=session[0].readBody(body,1000)){check(bytes.eof&&bytes.data.length==1&&bytes.data[0]==9);}
                check(body.result().ok&&body.result().streamCloseConfirmed&&body.result().requestAbortConfirmed);
                for(int i=0;i<20&&navigation.pending();i++)session[0].poll(20,100);
                check(navigation.result().acknowledged&&navigation.result().ok&&navigation.result().downloadKnown&&navigation.result().download);
                session[0].call("Runtime.evaluate",null,null,1000,false);
            }else{
                session[0].call("Runtime.evaluate",null,null,1000,false);check(!navigation.pending()&&navigation.result().acknowledged&&!navigation.result().ok&&!navigation.result().downloadKnown&&!navigation.result().download&&navigation.result().nativeError.equals("net::ERR_NAME_NOT_RESOLVED"));
            }
            check(session[0].pendingBodies()==0&&session[0].pendingControls()==0);realSessions++;
        }finally{if(session[0]!=null)session[0].close();check(CdpNativeProbe.fixtureFinish()==(1+((mode==42?6:2)<<8)));}
    }
    private static MdoBrowserCdpSession.Navigation control(Case c,boolean reload,int timeout)throws Exception{
        c.wire.extra=command->{
            String method=command.getString("method");if(!(method.equals("Page.reload")||method.equals("Page.navigateToHistoryEntry")))return false;
            c.request=command.getInt("id");JSONObject params=command.getJSONObject("params");check(command.length()==3&&params.length()==1);
            check(reload?method.equals("Page.reload")&&params.getString("loaderId").equals("original-loader"):method.equals("Page.navigateToHistoryEntry")&&params.getInt("entryId")==17);return true;
        };
        return reload?c.session.reloadRoot("frame-owned","original-loader",timeout):c.session.historyRoot("frame-owned",17,timeout);
    }
    private static void traversalReceipts()throws Exception{
        for(boolean reload:new boolean[]{false,true}){
            try(Case c=new Case()){
                MdoBrowserCdpSession.Navigation original=control(c,reload,1000);check(original.receiptOnly&&original.pending());
                int sent=c.wire.commands.size();refused("cdp_navigation_pending",()->control(c,!reload,1000));check(c.wire.commands.size()==sent);
                c.wire.add(ack(c.request,new JSONObject()));c.session.poll(100);MdoBrowserCdpSession.NavigationResult result=original.result();
                check(result.acknowledged&&result.ok&&result.effectMayHaveOccurred&&!result.loaderPresent&&!result.downloadKnown&&!result.download);
                check(!original.matchesSameDocument("frame-owned")&&!original.matchesCommittedLoader("frame-owned","original-loader"));
                control(c,reload,1000);c.session.close();check(original.result()==result);
            }
            for(int mode=0;mode<5;mode++)try(Case c=new Case()){
                MdoBrowserCdpSession.Navigation original=control(c,reload,1000);
                if(mode==0){c.wire.add(ack(c.request,new JSONObject().put("frameId","frame-owned")));refused("cdp_navigation_reply_invalid",()->c.session.poll(100));check(!original.result().ok);}
                if(mode==1){c.session.admitChild("foreign");c.wire.add(new JSONObject().put("id",c.request).put("sessionId","foreign").put("result",new JSONObject()));refused("cdp_navigation_reply_mismatch",()->c.session.poll(100));}
                if(mode==2){c.wire.now=1001;refused("cdp_navigation_timeout",()->c.session.poll(100));check(original.result().code.equals("cdp_navigation_timeout"));}
                if(mode==3){c.live.set(false);try{c.session.poll(100);throw new AssertionError("A revoked traversal was polled");}catch(IOException expected){check(original.result()!=null&&!original.result().ok);}}
                if(mode==4){c.wire.add(new JSONObject().put("id",c.request).put("error",new JSONObject().put("code",-32601).put("message","private-provider-error")));c.session.poll(100);check(original.result().acknowledged&&!original.result().ok&&!original.result().effectMayHaveOccurred&&original.result().code.equals("cdp_navigation_unsupported"));}
            }
        }
        try(Case c=new Case()){
            refused("cdp_navigation_arguments_invalid",()->c.session.reloadRoot("frame-owned","",1000));
            refused("cdp_navigation_arguments_invalid",()->c.session.historyRoot("",17,1000));
            refused("cdp_navigation_arguments_invalid",()->c.session.historyRoot("frame-owned",17,99));check(c.wire.commands.isEmpty());
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{nativeClassification();admission();invalidReplies();failureAndOrdering();traversalReceipts();if(args.length==0){realWire(42);realWire(43);realCommitWire(44);realCommitWire(45);realControlWire(46);realControlWire(47);}}catch(Exception failure){throw new RuntimeException(failure);}}).get(30,TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(3,TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"original_navigation_receipts\":true,\"same_connection_body_progress\":"+(realSessions==2)+",\"real_navigation_sessions\":"+realSessions+",\"real_control_sessions\":"+realControlSessions+",\"fixed_control_ack_not_completion\":true,\"real_commit_sessions\":"+realCommitSessions+",\"jvm_entropy_fds_before\":"+entropyBefore+",\"jvm_entropy_fds_after\":"+entropyAfter+",\"framework_verified\":false,\"provider_qualified\":false,\"product_navigation_activated\":false}");
    }
    private static void realCommitWire(int mode)throws Exception{
        // UUID's process-wide entropy descriptors are initialized before the
        // strict per-session native allocation/descriptor baseline is sampled.
        int before=entropyDescriptors();MdoBrowserCdpNetworkFrames frames=new MdoBrowserCdpNetworkFrames("frame-owned","initial-loader");
        int after=entropyDescriptors();if(mode==44){entropyBefore=before;entropyAfter=after;}check(after>=before&&after-before<=2);
        CdpNativeProbe.fixtureStart(mode);MdoBrowserCdpSession session=null;
        try(MdoBrowserCdpNetworkFrames originalFrames=frames){
            frames.initialize(new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","initial-loader")),frames.revision());
            long revision=frames.revision();session=MdoBrowserCdpSession.open("test-001",500,()->true,frames);
            MdoBrowserCdpSession.Navigation navigation=session.navigateRoot("about:blank","frame-owned",15000);
            for(int i=0;i<30&&(navigation.pending()||frames.navigationOrigin(navigation,revision)==null);i++)session.poll(20,100);
            MdoBrowserCdpNetworkFrames.Origin original=frames.navigationOrigin(navigation,revision);
            check(original!=null&&original.current()&&original.document.startsWith("nd-")&&navigation.result().acknowledged);
            check(!navigation.matchesCommittedLoader("other-root","native-commit-loader")&&!navigation.matchesCommittedLoader("frame-owned","other-loader"));
            session.call("Runtime.evaluate",null,null,1000,false);
            check(!original.current()&&frames.navigationOrigin(navigation,revision)==original&&navigation.result().ok);
            realCommitSessions++;
        }finally{if(session!=null)session.close();check(CdpNativeProbe.fixtureFinish()==(1+(2<<8)));}
    }
    private static void realControlWire(int mode)throws Exception{
        MdoBrowserCdpNetworkFrames frames=new MdoBrowserCdpNetworkFrames("frame-owned","initial-loader");
        CdpNativeProbe.fixtureStart(mode);MdoBrowserCdpSession session=null;
        try(MdoBrowserCdpNetworkFrames originalFrames=frames){
            frames.initialize(new JSONObject().put("frame",new JSONObject().put("id","frame-owned").put("loaderId","initial-loader")),frames.revision());
            long revision=frames.revision();session=MdoBrowserCdpSession.open("test-001",500,()->true,frames);
            MdoBrowserCdpSession.Navigation navigation=mode==46?session.historyRoot("frame-owned",17,15000):session.reloadRoot("frame-owned","initial-loader",15000);
            for(int i=0;i<30&&navigation.pending();i++)session.poll(20,100);
            check(navigation.receiptOnly&&navigation.result()!=null&&navigation.result().ok&&navigation.result().acknowledged);
            check(!navigation.result().downloadKnown&&!navigation.result().loaderPresent&&!navigation.matchesSameDocument("frame-owned"));
            session.call("Runtime.evaluate",null,null,1000,false);
            check(!navigation.matchesCommittedLoader("frame-owned","native-control-loader")&&frames.navigationOrigin(navigation,revision)==null);
            check(frames.rootLoader().equals("native-control-loader"));realControlSessions++;
        }finally{if(session!=null)session.close();check(CdpNativeProbe.fixtureFinish()==(1+(2<<8)));}
    }
    private static int entropyDescriptors()throws IOException{
        try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.list(java.nio.file.Paths.get("/proc/self/fd"))){
            return(int)paths.filter(path->{try{String target=java.nio.file.Files.readSymbolicLink(path).toString();return target.equals("/dev/random")||target.equals("/dev/urandom");}catch(IOException gone){return false;}}).count();
        }
    }
}
