package org.xserver.android;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

public final class AndroidChooserTargetContractTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Chooser target check "+checks+" failed");}
    static final class Reply implements ValueCallback<Uri[]> {
        int calls;boolean fail,oom;Uri[] received;
        public void onReceiveValue(Uri[] value){calls++;received=value;if(oom)throw new OutOfMemoryError("Synthetic native failure");if(fail)throw new IllegalStateException("Synthetic native failure");}
    }
    static final class Params extends WebChromeClient.FileChooserParams {
        int mode;String[] types={"image/png"};boolean capture,fail,oom;
        public int getMode(){if(oom)throw new OutOfMemoryError("Synthetic metadata failure");if(fail)throw new IllegalStateException("Synthetic metadata failure");return mode;}
        public String[] getAcceptTypes(){return types;}
        public boolean isCaptureEnabled(){return capture;}
    }
    static MdoBrowserChooserTarget target(WebView view){return new MdoBrowserChooserTarget(view,()->true,()->1,value->{},value->{},null,value->{});}
    static void normal(){
        WebView view=new WebView();MdoBrowserChooserTarget target=target(view);Reply reply=new Reply();Params params=new Params();params.mode=1;params.capture=true;
        check(!target.onShowFileChooser(view,reply,params)&&reply.calls==0);target.choosers.interception(true);
        check(!target.onShowFileChooser(new WebView(),reply,params)&&reply.calls==0);
        check(target.onShowFileChooser(view,reply,params));MdoBrowserChoosers.Snapshot value=target.choosers.snapshot();
        check(value.multiple&&value.capture&&value.accept.get(0).equals("image/png"));
        params.fail=true;check(target.onShowFileChooser(view,reply,params)&&reply.calls==0);
        check(target.choosers.cancel(value.id).ok&&reply.calls==1&&reply.received==null);
        target.choosers.interception(false);check(target.onShowFileChooser(view,reply,params)&&reply.calls==1);
        check(!target.onShowFileChooser(view,new Reply(),params));
        target.close();target.viewDestroyed();check(target.choosers.clean());
    }
    static void failures(){
        for(int fault=0;fault<5;fault++){
            Handler.reset();SystemClock.time=0;WebView view=new WebView();MdoBrowserChooserTarget target=target(view);target.choosers.interception(true);
            Reply reply=new Reply();Params params=new Params();params.fail=fault==0;params.oom=fault==1||fault==2;
            reply.oom=fault==2;reply.fail=fault==3;Handler.failAt=fault==4;
            check(target.onShowFileChooser(view,reply,params));
            MdoBrowserChoosers.Snapshot snapshot=target.choosers.snapshot();if(snapshot!=null)target.choosers.cancel(snapshot.id);
            check(reply.calls==1&&reply.received==null);check(target.onShowFileChooser(view,reply,params)&&reply.calls==1);
            if(fault==2||fault==3)check(!target.choosers.clean());
            target.close();target.viewDestroyed();check(target.choosers.clean()&&reply.calls==1);
        }
        Handler.reset();WebView view=new WebView();MdoBrowserChooserTarget target=target(view);target.choosers.interception(true);
        Reply reply=new Reply();Looper.current=new Looper();check(!target.onShowFileChooser(view,reply,new Params())&&reply.calls==0);Looper.current=Looper.MAIN;
        check(!target.onShowFileChooser(view,null,new Params()));check(!target.onShowFileChooser(view,reply,null));target.close();target.viewDestroyed();
    }
    static void manual(){
        Handler.reset();WebView view=new WebView();int[] counts={0,0};boolean[] fail={false};
        MdoBrowserChooserTarget target=new MdoBrowserChooserTarget(view,()->true,()->1,value->{},value->{},null,value->{},new MdoBrowserChooserTarget.Manual(){
            public boolean open(ValueCallback<Uri[]> callback,WebChromeClient.FileChooserParams params){counts[0]++;if(fail[0])throw new IllegalStateException("Synthetic manual admission failure");callback.onReceiveValue(null);return true;}
            public void cancel(){counts[1]++;}
        });
        Reply reply=new Reply();check(target.onShowFileChooser(view,reply,new Params())&&reply.calls==1&&counts[0]==1);
        Params changed=new Params();changed.fail=true;check(target.onShowFileChooser(view,reply,changed)&&reply.calls==1&&counts[0]==1);
        target.choosers.interception(true);Reply intercepted=new Reply();check(target.onShowFileChooser(view,intercepted,new Params())&&intercepted.calls==0&&counts[0]==1);
        target.choosers.cancel();check(intercepted.calls==1);target.choosers.interception(false);
        fail[0]=true;Reply failed=new Reply();check(target.onShowFileChooser(view,failed,new Params())&&failed.calls==1);
        check(target.onShowFileChooser(view,failed,new Params())&&failed.calls==1&&counts[0]==2);
        target.close();check(counts[1]==1);Reply closed=new Reply();check(!target.onShowFileChooser(view,closed,new Params())&&closed.calls==0);target.viewDestroyed();
    }
    public static void main(String[] args){normal();failures();manual();System.out.println("{\"passed\":true,\"checks\":"+checks+"}");}
}
