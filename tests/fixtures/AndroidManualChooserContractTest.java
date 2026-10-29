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
    private static boolean baselinePrivateUriAccepted(){
        // Compile the exact pinned native parser, not a copy/reimplementation.
        Intent data=new Intent("result");data.data=uri("content://private/object");
        Uri[] accepted=FileChooserResult.parse(Activity.RESULT_OK,data,false);
        check(accepted!=null&&accepted.length==1);return true;
    }
    private static void pickerUris(){
        Activity app=new Activity();Intent data=new Intent("result");data.data=uri("content://docs/1");
        check(MdoBrowserPickerUris.read(null,Activity.RESULT_OK,data,false)==null);
        check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,null,false)==null);
        check(MdoBrowserPickerUris.read(app,0,data,false)==null);
        Uri[] normal=MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,false);check(normal!=null&&normal.length==1&&normal[0].equals(data.data));
        for(String bad:new String[]{"content://private/object","content://private_alias/1","content://missing/1","content://invalid/1","content://denied/1","content://provider_failure/1","content://provider_oom/1","content://permission_failure/1","file:///private/data","data:text/plain,1","https://docs/1","content://10@docs/1","content:///1"}){
            data.data=uri(bad);check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,true)==null);
        }
        data.data=uri("content://docs/fallback");data.clip=new ClipData(uri("content://docs/1"),uri("content://private/2"));
        check(FileChooserResult.parse(Activity.RESULT_OK,data,true).length==2);check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,true)==null);
        data.clip=new ClipData(uri("content://docs/1"),uri("content://docs/2"),uri("content://docs/1"));
        check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,true).length==2);check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,false)==null);
        data.clip=new ClipData();check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,true)==null);
        data.clip=new ClipData((Uri)null);check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,true)==null);
        Uri[] many=new Uri[16];java.util.Arrays.fill(many,uri("content://docs/1"));data.clip=new ClipData(many);
        check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,true).length==1);
        many=new Uri[17];java.util.Arrays.fill(many,uri("content://docs/1"));data.clip=new ClipData(many);check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,true)==null);
        data.clip=null;data.data=uri("content://docs/"+new String(new char[8192]).replace('\0','x'));check(MdoBrowserPickerUris.read(app,Activity.RESULT_OK,data,true)==null);
    }
    private static void callbackIdentity(){
        try(Fixture f=new Fixture()){
            Reply reply=f.open();int launches=f.activity.launches;
            check(f.picker.open(f.view,reply,f.params,()->{throw new AssertionError("Duplicate owner evaluated");},()->{},()->{}));
            check(reply.calls==0&&f.activity.launches==launches);f.result(uri("content://docs/1"));check(reply.calls==1);
            check(f.picker.open(f.view,reply,f.params,()->false,()->{},()->{})&&reply.calls==1&&f.activity.launches==launches);
            f.picker.close();check(f.picker.open(f.view,reply,f.params,()->true,()->{},()->{})&&reply.calls==1);
        }
        try(Fixture f=new Fixture()){
            Reply[] retained=new Reply[128];for(int at=0;at<retained.length;at++){retained[at]=f.open();f.result(uri("content://docs/1"));check(retained[at].calls==1);}
            Reply refused=new Reply();check(!f.picker.open(f.view,refused,f.params,()->true,()->{},()->{})&&refused.calls==0&&f.activity.launches==128);
            check(!f.picker.clean()&&!f.picker.ready());
            for(Reply old:retained)check(f.picker.open(f.view,old,f.params,()->true,()->{},()->{})&&old.calls==1);
        }
    }
    private static void handoffOwnership(){
        for(int fault=0;fault<3;fault++)try(Fixture f=new Fixture()){
            Reply reply=f.open();final int kind=fault;
            f.activity.duringPermission=()->{if(kind==0)f.owner=false;else if(kind==1)f.picker.cancel(f.view);else f.picker.close();};
            f.result(uri("content://docs/original"));check(reply.calls==1&&reply.value==null&&f.blocks==0);
        }
        try(Fixture f=new Fixture()){
            Reply reply=new Reply();check(f.picker.open(f.view,reply,f.params,()->f.owner,()->f.blocks++,()->{f.blocks--;f.owner=false;}));
            f.result(uri("content://docs/original"));check(reply.calls==1&&reply.value==null&&f.blocks==0);
        }
        try(Fixture f=new Fixture()){
            Reply reply=f.open();f.activity.duringPermission=()->f.result(uri("content://docs/reentrant"));
            f.result(uri("content://docs/original"));check(reply.calls==1&&reply.value!=null&&reply.value[0].equals(uri("content://docs/original")));
        }
    }
    private static void hostRouting(){
        try(Fixture f=new Fixture()){
            Reply reply=new Reply();check(Boolean.TRUE.equals(f.picker.openHost(f.view,reply,f.params,()->f.owner)));
            int request=f.activity.code;f.result(uri("content://docs/host"));check(reply.calls==1&&reply.value!=null);
            f.params.mode=2;check(Boolean.TRUE.equals(f.picker.openHost(f.view,reply,f.params,()->false))&&reply.calls==1&&f.activity.code==request);
            check(Boolean.TRUE.equals(f.picker.openHost(f.view,reply,null,()->false))&&reply.calls==1);
            Reply legacy=new Reply();check(f.picker.openHost(f.view,legacy,f.params,()->true)==null&&legacy.calls==0&&f.activity.launches==1);
            f.params.mode=0;Reply fresh=new Reply();check(Boolean.TRUE.equals(f.picker.openHost(f.view,fresh,f.params,()->true))&&f.activity.launches==2);
            f.picker.cancel(f.view);check(fresh.calls==1&&fresh.value==null);f.result(uri("content://docs/late"));
        }
        try(Fixture f=new Fixture()){
            WebChromeClient.FileChooserParams bad=new WebChromeClient.FileChooserParams(){public int getMode(){throw new IllegalStateException("Synthetic metadata failure");}public String[] getAcceptTypes(){throw new AssertionError("Invalid mode admitted");}public boolean isCaptureEnabled(){return false;}};
            Reply reply=new Reply();check(Boolean.TRUE.equals(f.picker.openHost(f.view,reply,bad,()->true))&&reply.calls==1&&reply.value==null&&f.activity.launches==0);
            check(Boolean.TRUE.equals(f.picker.openHost(f.view,reply,bad,()->false))&&reply.calls==1);
            check(Boolean.FALSE.equals(f.picker.openHost(f.view,new Reply(),null,()->true)));
        }
    }
    public static void main(String[] args){boolean baseline=baselinePrivateUriAccepted();pickerUris();normal();bounds();cancellation();callbackIdentity();handoffOwnership();hostRouting();restored();System.out.println("{\"passed\":true,\"checks\":"+checks+",\"baseline_private_uri_accepted\":"+baseline+",\"shared_private_uri_blocked\":true}");}
}
