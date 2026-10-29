package org.xserver.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

public final class AndroidManualChooserContractTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Manual picker check "+checks+" failed");}
    private static final class Reply implements ValueCallback<Uri[]> {int calls;Uri[] value;boolean fail;public void onReceiveValue(Uri[] v){calls++;value=v;if(fail)throw new IllegalStateException("Synthetic callback failure");}}
    private static final class Params extends WebChromeClient.FileChooserParams {int mode;String[] types={"image/png"};public int getMode(){return mode;}public String[] getAcceptTypes(){return types;}public boolean isCaptureEnabled(){return false;}}
    private static final class Fixture implements AutoCloseable {
        final Activity activity=new Activity();final WebView view=new WebView();final MdoBrowserManualChooser picker=new MdoBrowserManualChooser(activity);final Params params=new Params();int blocks;boolean owner=true;
        Fixture(){Handler.reset();SystemClock.time=0;picker.resume();}
        Reply open(){Reply reply=new Reply();check(picker.open(view,reply,params,()->owner,()->blocks++,()->blocks--));return reply;}
        boolean result(Uri uri){Intent value=new Intent("result");value.data=uri;return picker.result(activity.code,Activity.RESULT_OK,value);}
        public void close(){picker.close();check(blocks==0);}
    }
    private static Uri uri(String value){return Uri.parse(value);}
    private static void normal(){
        try(Fixture f=new Fixture()){
            Reply reply=f.open();check(reply.calls==0&&f.activity.launches==1&&f.picker.busy(f.view)&&!f.picker.ready());
            check(Intent.ACTION_OPEN_DOCUMENT.equals(f.activity.intent.action)&&Intent.CATEGORY_OPENABLE.equals(f.activity.intent.category)&&"image/png".equals(f.activity.intent.type));
            int old=f.activity.code;f.picker.pause();check(f.result(uri("content://docs/image")));check(reply.calls==1&&reply.value.length==1&&f.blocks==0&&f.picker.ready());
            Reply paused=f.open();check(paused.calls==1&&paused.value==null&&f.activity.launches==1);f.picker.resume();
            Reply next=f.open();check(f.activity.code>old);check(f.picker.result(old,Activity.RESULT_OK,new Intent("old"))&&next.calls==0);
            f.result(uri("content://docs/next"));check(next.calls==1&&next.value!=null&&!f.picker.result(1,0,null));
        }
        try(Fixture f=new Fixture()){
            f.params.mode=1;f.params.types=new String[]{"image/png","text/plain"};Reply reply=f.open();
            check(f.activity.intent.multiple&&f.activity.intent.mimes.length==2&&"*/*".equals(f.activity.intent.type));
            Intent result=new Intent("result");result.clip=new ClipData(uri("content://docs/1"),uri("content://docs/2"),uri("content://docs/1"));
            f.picker.result(f.activity.code,Activity.RESULT_OK,result);check(reply.calls==1&&reply.value.length==2);
        }
    }
    private static void bounds(){
        for(String invalid:new String[]{"file:///private/path","data:text/plain,none","content://private/object","content://missing/1","content://invalid/1","content://denied/1","content://user@docs/1"}){
            try(Fixture f=new Fixture()){Reply reply=f.open();f.result(uri(invalid));check(reply.calls==1&&reply.value==null&&f.picker.ready());}
        }
        for(int fault=0;fault<5;fault++)try(Fixture f=new Fixture()){
            f.params.mode=fault==0?2:0;f.params.types=fault==1?new String[17]:new String[]{"image/png"};
            if(fault==2)f.owner=false;if(fault==3)Handler.failAt=true;if(fault==4)f.activity.fault="missing";
            Reply reply=f.open();check(reply.calls==1&&reply.value==null&&f.blocks==0&&f.picker.ready());
        }
        for(int count:new int[]{2,17})try(Fixture f=new Fixture()){
            f.params.mode=count==17?1:0;Reply reply=f.open();Uri[] uris=new Uri[count];java.util.Arrays.fill(uris,uri("content://docs/1"));
            Intent value=new Intent("result");value.clip=new ClipData(uris);f.picker.result(f.activity.code,Activity.RESULT_OK,value);check(reply.calls==1&&reply.value==null);
        }
        try(Fixture f=new Fixture()){f.params.types=new String[]{new String(new char[257]).replace('\0','x')};Reply reply=f.open();check(reply.calls==1&&reply.value==null&&f.activity.launches==0&&f.picker.ready());}
        try(Fixture f=new Fixture()){Reply reply=f.open();f.result(uri("content://docs/"+new String(new char[8192]).replace('\0','x')));check(reply.calls==1&&reply.value==null);}
    }
    private static void cancellation(){
        try(Fixture f=new Fixture()){
            Reply reply=f.open();int original=f.activity.code;Reply overlap=f.open();check(overlap.calls==1&&overlap.value==null&&reply.calls==0&&f.activity.launches==1);
            f.picker.cancel(f.view);check(reply.calls==1&&reply.value==null&&f.picker.awaitingResult()&&!f.picker.ready());
            Reply blocked=f.open();check(blocked.calls==1&&f.activity.launches==1);f.result(uri("content://docs/late"));check(reply.calls==1&&f.picker.ready());
            Reply fresh=f.open();check(f.activity.code!=original);f.picker.result(original,Activity.RESULT_OK,null);check(fresh.calls==0);f.picker.invalidate();
        }
        try(Fixture f=new Fixture()){Reply reply=f.open();f.owner=false;f.result(uri("content://docs/old"));check(reply.calls==1&&reply.value==null);}
        try(Fixture f=new Fixture()){
            Reply reply=f.open();SystemClock.time=120001;for(Runnable timer:Handler.tasks.keySet().toArray(new Runnable[0]))timer.run();
            check(reply.calls==1&&reply.value==null&&f.picker.awaitingResult()&&f.blocks==0);f.result(uri("content://docs/late"));check(reply.calls==1&&f.picker.ready());
        }
        try(Fixture f=new Fixture()){
            f.activity.fault="uncertain";Reply reply=f.open();check(reply.calls==1&&reply.value==null&&f.picker.awaitingResult()&&!f.picker.ready());
            f.result(uri("content://docs/late"));check(reply.calls==1&&f.picker.ready());
        }
        // Synchronous original completion followed by a platform exception must not reply twice.
        for(String fault:new String[]{"uncertain","missing"})try(Fixture f=new Fixture()){
            f.activity.fault=fault;f.activity.during=()->f.result(uri("content://docs/early"));Reply reply=f.open();check(reply.calls==1&&reply.value!=null&&f.picker.ready());
        }
        try(Fixture f=new Fixture()){
            Reply reply=f.open();reply.fail=true;f.result(uri("content://docs/1"));check(reply.calls==1&&!f.picker.clean()&&!f.picker.ready());
            Reply denied=f.open();check(denied.calls==1&&denied.value==null&&f.activity.launches==1);
        }
    }
    private static void restored(){
        Bundle saved=new Bundle();int old;
        try(Fixture f=new Fixture()){f.open();old=f.activity.code;f.picker.save(saved);}
        try(Fixture f=new Fixture()){f.picker.restore(saved);Reply reply=f.open();check(f.activity.code>old);f.picker.result(old,Activity.RESULT_OK,null);check(reply.calls==0);}
        try(Fixture f=new Fixture()){Bundle invalid=new Bundle();invalid.putInt("mdo.browser.manual.chooser.next",-1);f.picker.restore(invalid);Reply reply=f.open();check(reply.calls==1&&reply.value==null&&f.activity.launches==0);}
    }
    public static void main(String[] args){normal();bounds();cancellation();restored();System.out.println("{\"passed\":true,\"checks\":"+checks+"}");}
}
