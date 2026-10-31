package org.xserver.android;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Actual native JSON dispatcher and bounded ledger; no provider qualification. */
public final class CdpRequestProbe {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Request identity check "+(checks+1));checks++;}
    private static JSONObject request(String id,String loader)throws Exception{return new JSONObject().put("requestId",id).put("loaderId",loader).put("frameId","root").put("type","Document").put("request",new JSONObject().put("url","https://private.invalid/?never-retain").put("headers",new JSONObject().put("Authorization","fixture-value")));}
    private static void ack(CdpBodyProbe.Wire wire,MdoBrowserCdpSession session,int id,String loader,boolean download)throws Exception{
        wire.add(new JSONObject().put("id",id).put("result",new JSONObject().put("frameId","root").put("loaderId",loader).put("isDownload",download)));session.poll(100);
    }
    private static void commandHistory()throws Exception{
        for(int mode=0;mode<5;mode++)try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();wire.extra=command->true;
            try(MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,()->true,(m,s,p)->{},wire)){
                MdoBrowserCdpSession.Navigation older=session.navigateRoot("https://private.invalid/older","root",1000);requests.admit(0,older,"nr-older");
                ack(wire,session,1,"older-loader",true);
                if(mode==0)requests.event("Network.requestWillBeSent",null,request("older","older-loader"));
                long before=requests.revision();MdoBrowserCdpSession.Navigation newer=session.navigateRoot("https://private.invalid/newer","root",1000);requests.admit(before,newer,"nr-newer");
                if(mode!=0&&mode!=2)requests.event("Network.requestWillBeSent",null,request("older","older-loader"));
                MdoBrowserCdpNetworkRequests.Link link=requests.link("older","root");check(link!=null&&(mode==0?"nr-older".equals(link.reference()):link.reference()==null));
                if(mode==2)requests.event("Network.requestWillBeSent",null,request("older","older-loader"));
                check(mode==0?"nr-older".equals(link.reference()):link.reference()==null);
                ack(wire,session,2,mode==3?"older-loader":"newer-loader",mode!=4);
                check(mode==3?link.reference()==null:"nr-older".equals(link.reference()));
                try{requests.admit(before,newer,"nr-replayed");throw new AssertionError("Repeated native command admitted");}catch(java.io.IOException failure){check(failure.getMessage().equals("cdp_request_command_replayed"));}
            }wire.cleared();
        }
        try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();wire.extra=command->true;
            try(MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,()->true,(m,s,p)->{},wire)){
                MdoBrowserCdpSession.Navigation older=session.navigateRoot("https://private.invalid/older","root",1000);requests.admit(0,older,"nr-frozen");
                MdoBrowserCdpNetworkRequests.Link frozen=requests.link("older","root");
                ack(wire,session,1,"loader",true);
                MdoBrowserCdpSession.Navigation future=session.navigateRoot("https://private.invalid/future","root",1000);requests.admit(0,future,"nr-future");
                requests.event("Network.requestWillBeSent",null,request("older","loader"));ack(wire,session,2,"loader",true);
                check(frozen.reference().equals("nr-frozen"));check(requests.link("older","root").reference()==null);
            }wire.cleared();
        }
        for(int retain=0;retain<2;retain++)try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();wire.extra=command->true;
            try(MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,()->true,(m,s,p)->{},wire)){
                MdoBrowserCdpNetworkRequests.Link retained=null;
                for(int i=0;i<33;i++){
                    long before=requests.revision();MdoBrowserCdpSession.Navigation navigation=session.navigateRoot("https://private.invalid/","root",1000);requests.admit(before,navigation,"nr-"+i);ack(wire,session,i+1,"loader-"+i,true);
                    if(i==0){requests.event("Network.requestWillBeSent",null,request("oldest","loader-0"));if(retain==1)retained=requests.link("oldest","root");}
                    if(i==31)check(requests.link("oldest","root").reference().equals("nr-0"));
                }
                check(requests.link("oldest","root")==null);if(retained!=null)check(retained.reference().equals("nr-0"));
            }wire.cleared();
        }
    }
    private static void cases()throws Exception{
        for(int mode=0;mode<16;mode++)try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();wire.extra=command->true;
            try(MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,()->true,(m,s,p)->{},wire)){
                if(mode==5)requests.event("Network.requestWillBeSent",null,request("network","loader"));long before=requests.revision();
                MdoBrowserCdpSession.Navigation navigation=session.navigateRoot("https://private.invalid/","root",1000);requests.admit(before,navigation,"nr-original");JSONObject metadata=request("network","loader");
                if(mode==1)metadata.put("type","XHR");if(mode==2)metadata.put("frameId","child");if(mode==3)metadata.put("requestId",17);
                if(mode!=0&&mode!=5)requests.event("Network.requestWillBeSent",mode==4?"child":null,metadata);
                if(mode==6)requests.event("Network.requestWillBeSent",null,request("network","loader"));
                if(mode==7)requests.event("Network.requestWillBeSent",null,request("other","loader"));
                if(mode==8)requests.event("Network.requestWillBeSent",null,request("network","other-loader").put("redirectResponse",new JSONObject().put("status",302)));
                if(mode==9)requests.event("Network.requestWillBeSent",null,request("network","loader").put("redirectResponse",new JSONObject().put("status",200)));
                if(mode==10||mode==11)for(int i=0;i<(mode==10?32:33);i++)requests.event("Network.requestWillBeSent",null,request("network","loader").put("redirectResponse",new JSONObject().put("status",302)));
                metadata.put("requestId","mutated").put("loaderId","mutated");
                MdoBrowserCdpNetworkRequests.Link link=requests.link("network","root");check(link==null||link.reference()==null);
                JSONObject reply=new JSONObject().put("frameId","root").put("loaderId",mode==12?"wrong-loader":"loader").put("isDownload",mode!=13);
                if(mode==14)reply.remove("isDownload");if(mode==15)reply.remove("loaderId");
                wire.add(new JSONObject().put("id",1).put("result",reply));session.poll(100);
                boolean valid=mode==10;check((link!=null&&"nr-original".equals(link.reference()))==valid);
                check(requests.link("network","child")==null);
                if(valid){requests.event("Network.requestWillBeSent",null,request("network","other-loader"));check(requests.failure().equals("cdp_request_identity_rebound")&&link.reference().equals("nr-original"));}
                check((requests.failure()!=null)==(mode==3||mode>=6&&mode<=9||mode==11||mode==10));
            }wire.cleared();
        }
        try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();wire.extra=command->true;
            try(MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,()->true,(m,s,p)->{},wire)){
                MdoBrowserCdpSession.Navigation navigation=session.navigateRoot("https://private.invalid/","root",1000);requests.admit(0,navigation,"nr-late");
                MdoBrowserCdpNetworkRequests.Link late=requests.link("network","root");check(late!=null&&late.reference()==null);
                wire.add(new JSONObject().put("id",1).put("result",new JSONObject().put("frameId","root").put("loaderId","loader").put("isDownload",true)));session.poll(100);check(late.reference()==null);
                requests.event("Network.requestWillBeSent",null,request("network","loader"));check(late.reference().equals("nr-late"));
                for(int i=0;i<32;i++)check(requests.link("pending-"+i,"root")!=null);
                check(requests.link("overflow","root")==null&&requests.failure().equals("cdp_request_pending_limit"));check(late.reference().equals("nr-late"));
            }wire.cleared();
        }
        commandHistory();
        try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            for(int i=0;i<4096;i++)requests.event("Network.requestWillBeSent",null,request("r-"+i,"l-"+i));check(requests.failure()==null);
            requests.event("Network.requestWillBeSent",null,request("overflow","overflow"));check(requests.failure().equals("cdp_request_identity_limit"));
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{cases();}catch(Exception failure){throw new RuntimeException(failure);}}).get(20,TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"native_request_identity_chain\":true,\"bounded_metadata_only\":true,\"late_request_metadata\":true,\"bounded_command_history\":true,\"provider_qualified\":false}");
    }
}
