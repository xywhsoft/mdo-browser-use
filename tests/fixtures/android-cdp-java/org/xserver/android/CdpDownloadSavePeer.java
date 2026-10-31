package org.xserver.android;

import android.os.Handler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Pipe-only developer fixture joining exact product download commands to the
 * real native controller/files. Renderer, UI and framework peers are controlled. */
public final class CdpDownloadSavePeer {
    private static void await(BooleanSupplier condition,CdpDownloadsProbe.Case test)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(!condition.getAsBoolean()){
            if(System.nanoTime()>=end)throw new AssertionError("Android save peer wait expired");
            Handler.drain();test.host.ui.drain();Thread.sleep(2);
        }
    }
    private static void send(int id,JSONObject result)throws Exception{
        System.out.println(new JSONObject().put("id",id).put("result",result));System.out.flush();
    }
    private static JSONObject stats(CdpDownloadsProbe.Case test,CdpDownloadsProbe.Source source,DownloadCommandHost host)throws Exception{
        return new JSONObject().put("claims",source.claims.get()).put("source_reads",source.reads.get()).put("aborts",source.aborts.get())
            .put("records",source.controller.list().length).put("record_reservations",test.budget.used())
            .put("file_reservations",test.files.reservations()).put("reserved_bytes",test.files.reservedBytes())
            .put("retained_reads",test.files.retainedReads()).put("pending_reads",host.pendingReads()).put("pending_releases",host.pendingReleases());
    }
    public static void main(String[] args)throws Exception{
        android.os.Looper.getMainLooper();Path root=Paths.get(args[0]);
        if(Files.exists(root))throw new AssertionError("Interrupted Android save evidence preserved");
        Files.createDirectory(root);CdpDownloadsProbe.ownBase(root);JSONObject finalState=null;int quit=0;
        try(CdpDownloadsProbe.Case test=new CdpDownloadsProbe.Case("shared-save")){
            CdpDownloadsProbe.Source source=test.source();DownloadCommandHost host=new DownloadCommandHost();host.add("tab",source.context.view,source.controller);
            String download=null;boolean seeded=false;
            send(0,new JSONObject().put("ready",true).put("framework_verified",false).put("provider_qualified",false));
            BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));String line;
            while((line=input.readLine())!=null){
                if(line.length()>262144)throw new AssertionError("Oversized fixture command");
                JSONObject request=new JSONObject(line);int id=request.getInt("id");String command=request.getString("command");
                if(id<=0)throw new AssertionError("Invalid fixture command identity");
                if(command.equals("seed")){
                    if(seeded)throw new AssertionError("Original response cannot be repeated");seeded=true;
                    for(int offset=0;offset<131075;offset+=32768){
                        int count=Math.min(32768,131075-offset);byte[] bytes=new byte[count];for(int i=0;i<count;i++)bytes[i]=(byte)((offset+i)%251);
                        source.chunks.add(new JSONObject().put("data",Base64.getEncoder().encodeToString(bytes)).put("base64Encoded",true).put("eof",offset+count==131075));java.util.Arrays.fill(bytes,(byte)0);
                    }
                    download=source.offer("original-save-response");send(id,new JSONObject().put("download",download));continue;
                }
                if(command.equals("stats")){send(id,stats(test,source,host));continue;}
                if(command.equals("retire")){
                    test.host.worker.cancelNetwork(source.context.adapter.marker().lease());await(()->test.host.worker.networkPending()==0,test);
                    host.networkViews.get("tab").retired=true;send(id,new JSONObject().put("retired",true));continue;
                }
                if(command.equals("quit")){
                    finalState=stats(test,source,host);
                    for(String key:new String[]{"records","record_reservations","file_reservations","reserved_bytes","retained_reads","pending_reads","pending_releases"})
                        if(finalState.getLong(key)!=0)throw new AssertionError("Unreleased save resource: "+key);
                    host.cancelWaiters();quit=id;break;
                }
                if(!command.equals("execute")||download==null)throw new AssertionError("Unknown fixture command");
                JSONObject params=request.getJSONObject("params");String key="save-command-"+id;
                try{
                    host.execute("tab",key,params);await(()->host.replies.containsKey(key),test);
                    DownloadCommandHost.Reply reply=host.replies.remove(key);
                    if(reply.error!=null)System.out.println(new JSONObject().put("id",id).put("error",reply.error));
                    else send(id,(JSONObject)reply.result);
                }catch(Exception failure){
                    String code=failure.getMessage();
                    if(code==null||!code.matches("(?:cdp|download)_[a-z_]{1,80}"))throw failure;
                    System.out.println(new JSONObject().put("id",id).put("error",code));
                }
                System.out.flush();
            }
            if(quit==0)throw new AssertionError("Save peer closed without its cleanup receipt");
        }
        Files.delete(root);send(quit,finalState.put("closed",true));
    }
}
