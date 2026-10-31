package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class CdpNativeProbe {
    private static native void start(int mode);
    private static native int finish();
    private static native void fault(int allocation,boolean random);
    private static native void awaitRead();
    static void fixtureStart(int mode){start(mode);}
    static int fixtureFinish(){return finish();}
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("JNI gate check "+(checks+1));checks++;}
    private interface Operation {void run() throws Exception;}
    private static void failure(String code,Operation operation) throws Exception {
        try {operation.run();throw new AssertionError("Expected static error: "+code);}
        catch(IOException exception){check(code.equals(exception.getMessage()));}
    }
    private static <T>T on(ExecutorService worker,Callable<T> operation) throws Exception {
        return worker.submit(operation).get(10,TimeUnit.SECONDS);
    }
    private static void text(byte[] bytes,String expected) {
        try {check(bytes!=null&&expected.equals(new String(bytes,StandardCharsets.UTF_8)));}
        finally {if(bytes!=null)Arrays.fill(bytes,(byte)0);}
    }
    public static void main(String[] ignored) throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");
        failure("cdp_requires_background_thread",()->MdoBrowserCdpNative.open("test-001",100));
        failure("cdp_requires_background_thread",()->MdoBrowserCdpNative.discover(100));
        failure("cdp_requires_background_thread",()->MdoBrowserCdpNative.browserEndpoint(100));
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            start(48);on(worker,()->{
                check(MdoBrowserCdpNative.browserEndpoint(1000).path.equals("/devtools/browser/00000000-0000-4000-8000-000000000001"));return null;
            });check(finish()==1);
            on(worker,()->{
                failure("cdp_arguments_invalid",()->MdoBrowserCdpNative.browserEndpoint(0));
                failure("cdp_browser_unavailable",()->MdoBrowserCdpNative.browserEndpointFromJson("{}".getBytes(StandardCharsets.UTF_8)));
                for(String url:new String[]{"","https://localhost/devtools/browser","wss://localhost/devtools/browser","ws://remote.invalid/devtools/browser","ws://localhost:80/devtools/browser","ws://user@localhost/devtools/browser","ws://localhost/devtools/page/test-001","ws://localhost/devtools/browser/","ws://localhost/devtools/browser/a","ws://localhost/devtools/browser?x=1","ws://localhost/devtools/browser#x","ws://localhost/devtools/browser/../../page/x","ws://localhost/devtools/browser/%30%30","ws://localhost.evil/devtools/browser","ws://localhost\\@evil/devtools/browser","WS://localhost/devtools/browser"}){
                    byte[] bytes=new org.json.JSONObject().put("webSocketDebuggerUrl",url).toString().getBytes(StandardCharsets.UTF_8);
                    try{failure("cdp_browser_endpoint_invalid",()->MdoBrowserCdpNative.browserEndpointFromJson(bytes));}finally{Arrays.fill(bytes,(byte)0);}
                }
                for(String json:new String[]{"[]","{\"webSocketDebuggerUrl\":17}","{\"webSocketDebuggerUrl\":true}"}){
                    byte[] bytes=json.getBytes(StandardCharsets.UTF_8);try{failure("cdp_browser_endpoint_invalid",()->MdoBrowserCdpNative.browserEndpointFromJson(bytes));}finally{Arrays.fill(bytes,(byte)0);}
                }
                byte[] plain="{\"webSocketDebuggerUrl\":\"ws://localhost/devtools/browser\"}".getBytes(StandardCharsets.UTF_8);
                try{check(MdoBrowserCdpNative.browserEndpointFromJson(plain).path.equals("/devtools/browser"));}finally{Arrays.fill(plain,(byte)0);}
                byte[] duplicate="{\"webSocketDebuggerUrl\":\"ws://localhost/devtools/browser\",\"webSocketDebuggerUrl\":\"ws://evil\"}".getBytes(StandardCharsets.UTF_8);
                try{failure("cdp_json_invalid",()->MdoBrowserCdpNative.browserEndpointFromJson(duplicate));}finally{Arrays.fill(duplicate,(byte)0);}
                return null;
            });
            on(worker,()->{
                for(String id:new String[]{null,"","https://host/page","a/b","a.b","a\r\nb","\u4f60"})
                    failure("cdp_arguments_invalid",()->MdoBrowserCdpNative.open(id,100));
                failure("cdp_arguments_invalid",()->MdoBrowserCdpNative.open("test-001",0));
                failure("cdp_local_unavailable",()->MdoBrowserCdpNative.open("test-001",100));return null;
            });
            start(0);
            on(worker,()->{
                try(MdoBrowserCdpNative.Channel channel=MdoBrowserCdpNative.open("test-001",1000)) {
                    channel.send("{\"id\":1}".getBytes(StandardCharsets.US_ASCII),1000);
                    text(channel.receive(1000),"\u4f60!");text(channel.receive(1000),"second");
                    failure("cdp_session_closed",()->channel.receive(1000));
                }
                return null;
            });
            check(finish()==(1+(3<<8)));
            for(int cycle=0;cycle<16;cycle++) {
                start(3);MdoBrowserCdpNative.Channel channel=on(worker,()->MdoBrowserCdpNative.open("test-001",1000));
                failure("cdp_requires_background_thread",()->channel.receive(10));
                ExecutorService wrong=Executors.newSingleThreadExecutor();
                try {on(wrong,()->{failure("cdp_wrong_worker",()->channel.receive(10));return null;});}
                finally {wrong.shutdownNow();check(wrong.awaitTermination(2,TimeUnit.SECONDS));}
                Future<?> reading=worker.submit(()->{try {failure("cdp_session_closed",()->channel.receive(5000));}catch(Exception error){throw new RuntimeException(error);}});
                awaitRead();long started=System.nanoTime();channel.close();channel.close();
                reading.get(2,TimeUnit.SECONDS);check(System.nanoTime()-started<TimeUnit.SECONDS.toNanos(1));
                on(worker,()->{failure("cdp_session_closed",()->channel.send(new byte[]{'x'},100));return null;});
                check(finish()==1);
            }
            start(6);on(worker,()->{
                try(MdoBrowserCdpNative.Channel channel=MdoBrowserCdpNative.open("test-001",1000)) {
                    check(channel.receive(20)==null);
                    failure("cdp_arguments_invalid",()->channel.send(new byte[]{(byte)0xC0,(byte)0x80},100));
                    check(channel.receive(20)==null);
                }
                return null;
            });check(finish()==1);
            start(10);on(worker,()->{
                try(MdoBrowserCdpNative.Channel channel=MdoBrowserCdpNative.open("test-001",1000)) {
                    text(channel.receive(1000),"");text(channel.receive(1000),"next");
                }
                return null;
            });check(finish()==1);
            start(3);on(worker,()->{
                try(MdoBrowserCdpNative.Channel channel=MdoBrowserCdpNative.open("test-001",1000)) {
                    fault(1,false);failure("cdp_allocation_failed",()->channel.send(new byte[]{'x'},100));
                    failure("cdp_session_closed",()->channel.receive(100));
                }
                return null;
            });check(finish()==1);
        } finally {worker.shutdownNow();check(worker.awaitTermination(2,TimeUnit.SECONDS));}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_jvm_jni\":true,\"read_only_browser_discovery\":true,\"android_looper_doubled\":true,\"cancel_cycles\":16,\"android_device_qualified\":false}");
    }
}
