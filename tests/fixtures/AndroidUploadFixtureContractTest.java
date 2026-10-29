package org.xserver.android;

import android.os.Handler;
import android.os.SystemClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Execute the exact device fixture with a doubled UI/runtime authority only.
 * Actual file channels, ticket store and worker are retained. */
public final class AndroidUploadFixtureContractTest {
    private static void negative(Handler handler,boolean unsupported)throws Exception {
        MdoBrowserUploadQueue queue=new MdoBrowserUploadQueue(()->{
            if(unsupported)throw new MdoBrowserUploadFiles.Failure("upload_secure_directory_unsupported");
            throw new java.io.IOException("Synthetic private path must not reach the receipt");
        },handler::post,SystemClock::elapsedRealtime);MdoBrowserUploadRuntime.queue=queue;
        boolean[] done={false,false};String[] code={null};
        BrowserUseUploadProbe probe=new BrowserUseUploadProbe(new android.content.Context(),handler,(ok,value,count)->{
            if(done[0])throw new AssertionError("Duplicate failed completion");done[0]=true;done[1]=ok;code[0]=value;
        });
        try{
            probe.run();long deadline=System.nanoTime()+5000000000L;
            while(!done[0]&&System.nanoTime()<deadline){handler.drain();Thread.sleep(5);}
            String expected="upload_fixture_begin_"+(unsupported?"upload_secure_directory_unsupported":"upload_io_failed");
            if(!done[0]||done[1]||!expected.equals(code[0])||queue.activeFiles()!=0||queue.reservedBytes()!=0||queue.unfinishedDeliveries()!=0)
                throw new AssertionError("Incorrect fixture failure receipt: "+code[0]);
        }finally{probe.close();queue.close();if(!queue.awaitStopped(5000))throw new AssertionError("Failed fixture worker did not stop");handler.drain();}
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);Files.createDirectory(root);Handler handler=new Handler();
        MdoBrowserUploadQueue queue=new MdoBrowserUploadQueue(()->{
            MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root);
            try{files.acquireCacheLease();return files;}catch(Exception|Error failure){files.close();throw failure;}
        },handler::post,SystemClock::elapsedRealtime);
        MdoBrowserUploadRuntime.queue=queue;
        boolean[] done={false,false};int[] checks={0};String[] result={null};
        BrowserUseUploadProbe probe=new BrowserUseUploadProbe(new android.content.Context(),handler,(ok,code,count)->{
            if(done[0])throw new AssertionError("Duplicate fixture completion");done[0]=true;done[1]=ok;result[0]=code;checks[0]=count;
        });
        try{
            probe.run();long deadline=System.nanoTime()+12000000000L;
            while(!done[0]&&System.nanoTime()<deadline){handler.drain();Thread.sleep(5);}
            if(!done[0]||!done[1]||!"native_upload_staging_verified".equals(result[0]))throw new AssertionError("Fixture did not pass: "+result[0]);
            if(queue.activeFiles()!=0||queue.reservedBytes()!=0||queue.cleanupFailed()||queue.unfinishedDeliveries()!=0)throw new AssertionError("Unreleased fixture state");
        }finally{probe.close();queue.close();if(!queue.awaitStopped(5000))throw new AssertionError("Fixture worker did not stop");handler.drain();}
        Files.delete(root.resolve(MdoBrowserUploadFiles.OWNER_FILE));Files.delete(root);
        negative(handler,true);negative(handler,false);
        System.out.println("{\"passed\":true,\"checks\":"+(checks[0]+2)+",\"device_fixture_checks\":"+checks[0]+",\"negative_checks\":2,\"framework_verified\":false,\"native_cache_authority_verified\":false,\"uri_assignment_verified\":false}");
    }
}
