package org.xserver.android;

import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.ProviderInfo;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Real bytes/worker/provider/proxy; Android UID, Resolver, Looper and FD calls
 * are doubles. Run each outcome in its own JVM to retain original app statics. */
public final class AndroidUploadUriFixtureContractTest {
    private static int checks;
    private static void check(boolean value,String code){checks++;if(!value)throw new AssertionError(code);}
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);String mode=args[1];Files.createDirectory(root);Handler handler=new Handler();
        MdoBrowserUploadQueue queue=new MdoBrowserUploadQueue(()->{
            MdoBrowserUploadFiles value=new MdoBrowserUploadFiles(root);
            try{value.acquireCacheLease();return value;}catch(Exception|Error error){value.close();throw error;}
        },handler::post,SystemClock::elapsedRealtime,()->android.os.Looper.myLooper()!=android.os.Looper.getMainLooper());
        MdoBrowserUploadRuntime.queue=queue;Context context=new Context();ProviderInfo info=new ProviderInfo();
        info.authority=MdoBrowserUploadUriRuntime.authority(context);MdoBrowserUploadProvider provider=new MdoBrowserUploadProvider();provider.attachInfo(context,info);
        context.resolver=new ContentResolver(provider);ParcelFileDescriptor.fault=mode;ContentResolver.block="resolver_cancel".equals(mode);
        boolean[] done={false,false};String[] result={null};int[] count={0};
        BrowserUseUploadUriProbe probe=new BrowserUseUploadUriProbe(context,handler,(ok,code,n)->{
            check(!done[0],"Duplicate fixture completion");done[0]=true;done[1]=ok;result[0]=code;count[0]=n;
        });
        try{
            if("cancel_before".equals(mode)){probe.close();probe.run();handler.drain();check(!done[0]&&queue.activeFiles()==0,"Closed fixture started");}
            else{
                probe.run();long deadline=System.nanoTime()+12000000000L;boolean cancelled=false;
                while(!done[0]&&System.nanoTime()<deadline){
                    handler.drain();boolean cancel="cancel_read".equals(mode)&&ParcelFileDescriptor.reading.getCount()==0||"resolver_cancel".equals(mode)&&ContentResolver.entered.getCount()==0;
                    if(cancel&&!cancelled){probe.close();ParcelFileDescriptor.resume.countDown();cancelled=true;}
                    if(cancelled&&probe.consumerStopped()){handler.drain();break;}Thread.sleep(5);
                }
                if(mode.startsWith("cancel_")||"resolver_cancel".equals(mode))check(cancelled&&!done[0]&&probe.consumerStopped(),"Cancelled consumer did not retire");
                else{
                    check(done[0],"Fixture missing terminal result");
                    String expected="success".equals(mode)?"native_upload_uri_proxy_verified":"corrupt".equals(mode)?"upload_uri_fixture_payload_changed":"upload_uri_fixture_cleanup_unconfirmed";
                    check(expected.equals(result[0])&&done[1]=="success".equals(mode),"Incorrect fixture outcome");
                    if("success".equals(mode))check(count[0]>35,"Fixture skipped its contracts");
                }
            }
            probe.close();long deadline=System.nanoTime()+3000000000L;
            while((!probe.consumerStopped()||queue.activeFiles()!=0||queue.reservedBytes()!=0||queue.unfinishedDeliveries()!=0)&&System.nanoTime()<deadline){handler.drain();Thread.sleep(5);}
            check(probe.consumerStopped()&&ParcelFileDescriptor.active.get()==0,"Consumer read left running");
            check(ParcelFileDescriptor.openCount()==0,"Owned descriptor left open");
            check(queue.activeFiles()==0&&queue.reservedBytes()==0&&!queue.cleanupFailed()&&queue.unfinishedDeliveries()==0,"Owned file state not clean");
            MdoBrowserUploadUris registry=MdoBrowserUploadUriRuntime.existing(context);
            if(registry!=null){
                if("no_ack".equals(mode))check(registry.descriptors()>0,"Unacknowledged sessions retired early");
                ParcelFileDescriptor.acknowledge();check(registry.grants()==0&&registry.descriptors()==0&&registry.uncertain()==0,"Original sessions not retired");
            }
        }finally{probe.close();ParcelFileDescriptor.resume.countDown();queue.close();check(queue.awaitStopped(5000),"Worker still running");handler.drain();}
        Path marker=root.resolve(MdoBrowserUploadFiles.OWNER_FILE);if(Files.exists(marker))Files.delete(marker);Files.delete(root);
        System.out.println("{\"passed\":true,\"mode\":\""+mode+"\",\"checks\":"+(checks+count[0])+",\"fixture_checks\":"+count[0]+"}");
    }
}
