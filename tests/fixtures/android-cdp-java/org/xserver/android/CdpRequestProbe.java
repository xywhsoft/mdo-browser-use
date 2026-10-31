package org.xserver.android;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Actual native JSON dispatcher and bounded ledger; no provider qualification. */
public final class CdpRequestProbe {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Request identity check "+(checks+1));checks++;}
    private static JSONObject request(String id,String loader)throws Exception{return new JSONObject().put("requestId",id).put("loaderId",loader).put("frameId","root").put("type","Document").put("request",new JSONObject().put("url","https://private.invalid/?never-retain").put("headers",new JSONObject().put("Authorization","fixture-value")));}
    private static void cases()throws Exception{
        for(int mode=0;mode<16;mode++)try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();wire.extra=command->true;
            try(MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,()->true,(m,s,p)->{},wire)){
                if(mode==5)requests.event("Network.requestWillBeSent",null,request("network","loader"));long before=requests.revision();
                MdoBrowserCdpSession.Navigation navigation=session.navigateRoot("https://private.invalid/","root",1000);JSONObject metadata=request("network","loader");
                if(mode==1)metadata.put("type","XHR");if(mode==2)metadata.put("frameId","child");if(mode==3)metadata.put("requestId",17);
                if(mode!=0&&mode!=5)requests.event("Network.requestWillBeSent",mode==4?"child":null,metadata);
                if(mode==6)requests.event("Network.requestWillBeSent",null,request("network","loader"));
                if(mode==7)requests.event("Network.requestWillBeSent",null,request("other","loader"));
                if(mode==8)requests.event("Network.requestWillBeSent",null,request("network","other-loader").put("redirectResponse",new JSONObject().put("status",302)));
                if(mode==9)requests.event("Network.requestWillBeSent",null,request("network","loader").put("redirectResponse",new JSONObject().put("status",200)));
                if(mode==10||mode==11)for(int i=0;i<(mode==10?32:33);i++)requests.event("Network.requestWillBeSent",null,request("network","loader").put("redirectResponse",new JSONObject().put("status",302)));
                metadata.put("requestId","mutated").put("loaderId","mutated");
                MdoBrowserCdpNetworkRequests.Link link=requests.link("network","root",before,navigation,"nr-original");check(link==null||link.reference()==null);
                JSONObject reply=new JSONObject().put("frameId","root").put("loaderId",mode==12?"wrong-loader":"loader").put("isDownload",mode!=13);
                if(mode==14)reply.remove("isDownload");if(mode==15)reply.remove("loaderId");
                wire.add(new JSONObject().put("id",1).put("result",reply));session.poll(100);
                boolean valid=mode==10;check((link!=null&&"nr-original".equals(link.reference()))==valid);
                check(requests.link("network","child",before,navigation,"wrong")==null);
                if(valid){requests.event("Network.requestWillBeSent",null,request("network","other-loader"));check(requests.failure().equals("cdp_request_identity_rebound")&&link.reference().equals("nr-original"));}
                check((requests.failure()!=null)==(mode==3||mode>=6&&mode<=9||mode==11||mode==10));
            }wire.cleared();
        }
        try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            CdpBodyProbe.Wire wire=new CdpBodyProbe.Wire();wire.extra=command->true;
            try(MdoBrowserCdpSession session=MdoBrowserCdpSession.owned(wire,()->true,(m,s,p)->{},wire)){
                MdoBrowserCdpSession.Navigation navigation=session.navigateRoot("https://private.invalid/","root",1000);
                MdoBrowserCdpNetworkRequests.Link late=requests.link("network","root",0,navigation,"nr-late");check(late!=null&&late.reference()==null);
                wire.add(new JSONObject().put("id",1).put("result",new JSONObject().put("frameId","root").put("loaderId","loader").put("isDownload",true)));session.poll(100);check(late.reference()==null);
                requests.event("Network.requestWillBeSent",null,request("network","loader"));check(late.reference().equals("nr-late"));
                for(int i=0;i<32;i++)check(requests.link("pending-"+i,"root",0,navigation,"nr-pending")!=null);
                check(requests.link("overflow","root",0,navigation,"nr-overflow")==null&&requests.failure().equals("cdp_request_pending_limit"));check(late.reference().equals("nr-late"));
            }wire.cleared();
        }
        try(MdoBrowserCdpNetworkRequests requests=new MdoBrowserCdpNetworkRequests("root")){
            for(int i=0;i<4096;i++)requests.event("Network.requestWillBeSent",null,request("r-"+i,"l-"+i));check(requests.failure()==null);
            requests.event("Network.requestWillBeSent",null,request("overflow","overflow"));check(requests.failure().equals("cdp_request_identity_limit"));
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{cases();}catch(Exception failure){throw new RuntimeException(failure);}}).get(20,TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"native_request_identity_chain\":true,\"bounded_metadata_only\":true,\"late_request_metadata\":true,\"provider_qualified\":false}");
    }
}
