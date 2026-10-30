package org.xserver.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Deferred permission decisions over exact native event/codec/JNI and kernel
 * responses. No URL replay, framework/provider or product-action qualification. */
public final class CdpOfferProbe {
    private static int checks,realSessions;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Offer check "+(checks+1));checks++;}
    private static void refused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected "+code);}catch(IOException failure){check(code.equals(failure.getMessage()));check(failure.getCause()==null);}
    }
    private static MdoBrowserCdpSession.Offer retain(CdpBodyProbe.Context context,String id)throws Exception{
        List<MdoBrowserCdpSession.Offer> offers=new ArrayList<>();context.action=p->{
            offers.add(context.client.retainPausedResponse());
            try{p.put("requestId","forged-selector");p.remove("responseHeaders");}catch(org.json.JSONException failure){throw new IOException("fixture_json_failed");}
        };
        context.pause(CdpBodyProbe.paused(id));check(offers.size()==1);return offers.get(0);
    }
    private static void drain(CdpBodyProbe.Context context,MdoBrowserCdpSession.Offer offer)throws Exception{
        for(int i=0;i<15&&offer.result()==null;i++){context.client.serviceBodies(100);context.client.poll(20,100);}check(offer.result()!=null);
    }
    private static void bodyDrain(CdpBodyProbe.Context context,MdoBrowserCdpSession.Body body)throws Exception{
        for(int i=0;i<15&&body.result()==null;i++){context.client.serviceBodies(100);context.client.poll(20,100);}check(body.result()!=null);
    }
    private static void pendingAndAcceptance()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            refused("cdp_control_not_in_event",context.client::retainPausedResponse);
            MdoBrowserCdpSession.Offer offer=retain(context,"original-response.1");check(offer.result()==null&&offer.metadata().kind.equals("candidate"));
            check(offer.metadata().name.equals("private.bin")&&offer.metadata().mime.isEmpty());
            check(context.client.pendingOffers()==1&&context.client.pendingBodies()==0&&context.client.pendingControls()==0&&context.wire.commands.isEmpty());
            for(int i=0;i<10;i++){check(!context.client.serviceBodies(100));context.client.call("Runtime.evaluate",null,null,100,false);}
            check(context.wire.count("Fetch.takeResponseBodyAsStream")==0&&context.wire.count("IO.read")==0);
            refused("cdp_offer_arguments_invalid",()->offer.accept(0,1000));refused("cdp_offer_arguments_invalid",()->offer.accept(4,999));
            context.wire.now=119000;AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread ui=new Thread(()->{try{offer.accept(4,1000);}catch(Throwable error){failure.set(error);}});ui.start();ui.join(500);
            check(!ui.isAlive()&&failure.get()==null&&context.wire.count("Fetch.takeResponseBodyAsStream")==0);
            refused("cdp_offer_already_decided",()->offer.accept(4,1000));check(context.client.serviceBodies(100));
            check(offer.result()==null&&context.client.pendingOffers()==1);check(context.client.poll(100));
            MdoBrowserCdpSession.OfferResult accepted=offer.result();check(accepted!=null&&accepted.accepted&&accepted.claimMayHaveOccurred&&!accepted.requestAbortConfirmed&&accepted.code==null);
            check(context.client.pendingOffers()==0&&accepted.body.ready()&&accepted.body.maximumBytes()==4&&context.client.pendingBodies()==1);
            JSONObject claim=context.wire.commands.get(context.wire.commands.size()-1);check(claim.getJSONObject("params").getString("requestId").equals("original-response.1"));
            context.wire.now=119900;context.wire.chunks.add(new JSONObject().put("data","AQIDBA==").put("base64Encoded",true).put("eof",true));
            try(MdoBrowserCdpSession.BodyChunk bytes=context.client.readBody(accepted.body,100)){check(bytes.eof&&Arrays.equals(bytes.data,new byte[]{1,2,3,4}));}
            check(accepted.body.result().ok&&accepted.body.result().bytes==4&&offer.result()==accepted);
            refused("cdp_offer_unavailable",()->offer.accept(8,1000));
        }
    }
    private static void cancellationAndExpiry()throws Exception{
        for(int mode=0;mode<4;mode++)try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            MdoBrowserCdpSession.Offer offer=retain(context,"original-response.2");
            if(mode==0)offer.cancel();
            else if(mode==1){context.wire.now=120000;refused("cdp_offer_expired",()->offer.accept(8,1000));}
            else if(mode==2){context.wire.now=-1;refused("cdp_offer_clock_invalid",()->offer.accept(8,1000));}
            else{context.wire.now=119999;offer.accept(8,1000);context.wire.now=120001;}
            check(context.wire.commands.isEmpty());drain(context,offer);MdoBrowserCdpSession.OfferResult result=offer.result();
            check(!result.accepted&&!result.claimMayHaveOccurred&&result.requestAbortConfirmed&&result.body==null);
            check(result.code.equals(mode==0?"cdp_offer_cancelled":mode==2?"cdp_offer_clock_invalid":"cdp_offer_expired"));
            check(context.wire.count("Fetch.failRequest")==1&&context.wire.count("Fetch.takeResponseBodyAsStream")==0&&context.wire.count("IO.read")==0);
            check(context.wire.commands.get(0).getJSONObject("params").getString("requestId").equals("original-response.2"));
            offer.cancel();check(!context.client.serviceBodies(100)&&context.client.pendingOffers()==0);refused("cdp_offer_unavailable",()->offer.accept(4,1000));
        }
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            MdoBrowserCdpSession.Offer offer=retain(context,"original-response.3");offer.accept(8,1000);
            context.wire.extra=command->{if(command.getString("method").equals("Fetch.takeResponseBodyAsStream")){
                Thread ui=new Thread(offer::cancel);ui.start();ui.join(500);check(!ui.isAlive());}return false;};
            drain(context,offer);check(offer.result().accepted&&offer.result().code.equals("cdp_offer_cancelled"));
            bodyDrain(context,offer.result().body);check(!offer.result().body.result().ok&&offer.result().body.result().streamCloseConfirmed&&offer.result().body.result().requestAbortConfirmed);
            check(context.wire.count("IO.read")==0&&context.wire.count("Fetch.takeResponseBodyAsStream")==1);
        }
    }
    private static void limitsAndFrozenMetadata()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            MdoBrowserCdpSession.Offer[] offer={null};context.action=p->{offer[0]=context.client.retainPausedResponse();try{p.remove("responseHeaders");}catch(Exception failure){throw new IOException("fixture_json_failed");}};
            JSONObject paused=CdpBodyProbe.paused("original-response.4");paused.getJSONArray("responseHeaders").put(new JSONObject().put("name","Content-Length").put("value","9"));context.pause(paused);
            check(offer[0].metadata().declaredLength==9);refused("cdp_offer_byte_limit",()->offer[0].accept(8,1000));
            check(offer[0].result()==null&&context.wire.commands.isEmpty());offer[0].accept(9,1000);drain(context,offer[0]);check(offer[0].result().body.maximumBytes()==9);
        }
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            List<MdoBrowserCdpSession.Offer> offers=new ArrayList<>();for(int i=0;i<8;i++)offers.add(retain(context,"original-offer-"+i));
            check(context.client.pendingOffers()==8&&context.wire.commands.isEmpty());
            refused("cdp_body_limit",()->retain(context,"original-offer-9"));for(MdoBrowserCdpSession.Offer offer:offers)check(offer.result()!=null&&!offer.result().requestAbortConfirmed&&!offer.result().accepted);
            check(context.client.pendingOffers()==0);
        }
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            MdoBrowserCdpSession.Offer offer=retain(context,"duplicate-original");refused("cdp_control_duplicate",()->retain(context,"duplicate-original"));
            check(!offer.result().requestAbortConfirmed&&offer.result().code.equals("cdp_offer_connection_closed"));
        }
    }
    private static void failedReceipts()throws Exception{
        for(int mode=0;mode<3;mode++)try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            final int which=mode;MdoBrowserCdpSession.Offer offer=retain(context,"original-response.5");
            context.wire.extra=command->{
                if(command.getString("method").equals(which==0?"Fetch.failRequest":"Fetch.takeResponseBodyAsStream")){
                    if(which==2)return true;
                    context.wire.add(new JSONObject().put("id",command.getInt("id")).put("result",which==0?new JSONObject().put("unexpected",true):new JSONObject().put("stream",7)));return true;
                }return false;
            };
            if(mode==0)offer.cancel();else offer.accept(8,1000);check(context.client.serviceBodies(100));
            if(mode==2){context.wire.now=5001;refused("cdp_control_timeout",()->context.client.poll(100));}
            else refused(mode==0?"cdp_control_reply_invalid":"cdp_body_claim_reply_invalid",()->context.client.poll(100));
            check(offer.result()!=null&&!offer.result().accepted&&!offer.result().requestAbortConfirmed&&context.client.pendingOffers()==0);
            if(mode!=0)check(offer.result().claimMayHaveOccurred&&offer.result().body.result()!=null&&!offer.result().body.result().ok);
        }
    }
    private static void fairness()throws Exception{
        try(CdpBodyProbe.Context context=new CdpBodyProbe.Context()){
            MdoBrowserCdpSession.Offer first=retain(context,"first"),older=retain(context,"older");first.accept(8,3000);older.accept(8,3000);
            check(context.client.serviceBodies(100)&&context.client.poll(100));check(first.result()!=null&&older.result()==null);
            MdoBrowserCdpSession.Offer newer=retain(context,"newer");newer.accept(8,3000);
            check(context.client.serviceBodies(100)&&context.client.poll(100));check(older.result()!=null&&newer.result()==null);
            drain(context,newer);check(context.wire.count("Fetch.takeResponseBodyAsStream")==3);
        }
    }
    private static void realWire()throws Exception{
        for(int mode=40;mode<=41;mode++){
            CdpNativeProbe.fixtureStart(mode);MdoBrowserCdpSession[] client={null};List<MdoBrowserCdpSession.Offer> offers=new ArrayList<>();
            try{
                client[0]=MdoBrowserCdpSession.open("test-001",500,()->true,(m,s,p)->offers.add(client[0].retainPausedResponse()));
                client[0].call("Runtime.evaluate",null,null,1000,false);check(offers.size()==1&&offers.get(0).result()==null&&client[0].pendingControls()==0);
                client[0].call("Runtime.evaluate",null,null,1000,false);check(offers.get(0).result()==null);
                MdoBrowserCdpSession.Offer offer=offers.get(0);if(mode==40)offer.accept(64,3000);else offer.cancel();
                for(int i=0;i<20&&offer.result()==null;i++){client[0].serviceBodies(100);client[0].poll(20,100);}check(offer.result()!=null);
                if(mode==40){
                    check(offer.result().accepted);MdoBrowserCdpSession.Body body=offer.result().body;
                    try(MdoBrowserCdpSession.BodyChunk chunk=client[0].readBody(body,500)){check(chunk.eof&&Arrays.equals(chunk.data,new byte[]{1,2,3}));}
                    check(body.result().ok&&body.result().bytes==3&&body.result().requestAbortConfirmed&&body.result().streamCloseConfirmed);
                }else check(!offer.result().accepted&&offer.result().requestAbortConfirmed);
                realSessions++;
            }finally{if(client[0]!=null)client[0].close();check(CdpNativeProbe.fixtureFinish()==(1+((mode==40?6:3)<<8)));}
        }
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{pendingAndAcceptance();cancellationAndExpiry();limitsAndFrozenMetadata();failedReceipts();fairness();if(args.length==0)realWire();}catch(Exception failure){throw new RuntimeException(failure);}}).get(30,TimeUnit.SECONDS);}
        finally{worker.shutdownNow();check(worker.awaitTermination(3,TimeUnit.SECONDS));}
        System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("original_deferred_offers",true).put("real_deferred_offer_sessions",realSessions).put("provider_qualified",false).put("product_downloads_activated",false));
    }
}
