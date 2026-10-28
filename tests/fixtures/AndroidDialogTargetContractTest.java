package org.xserver.android;

import android.os.Handler;
import android.os.SystemClock;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Actual WebChromeClient target/controller against deterministic framework
 * callbacks. Real Android API compatibility is compiled separately in the APK. */
public final class AndroidDialogTargetContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Fixture {
        final WebView view=new WebView();final AtomicBoolean owned=new AtomicBoolean(true);final AtomicLong epoch=new AtomicLong(7);
        final List<Boolean> blockers=new ArrayList<>();final List<Long> cancelled=new ArrayList<>();
        final MdoBrowserDialogTarget target=new MdoBrowserDialogTarget(view,owned::get,epoch::get,blockers::add,cancelled::add);
        Fixture(){Handler.reset();SystemClock.time=100;}
        String id(){return target.dialogs.snapshot().id;}
    }
    public static void main(String[] args)throws Exception {
        for(String kind:new String[]{"alert","confirm","prompt","beforeunload"}){
            Fixture f=new Fixture();JsPromptResult result=new JsPromptResult();boolean handled;
            // Chromium can hand a JsPromptResult to callbacks declared JsResult.
            switch(kind){
                case "alert":handled=f.target.onJsAlert(f.view,"https://source.invalid/","message",result);break;
                case "confirm":handled=f.target.onJsConfirm(f.view,"https://source.invalid/","message",result);break;
                case "prompt":handled=f.target.onJsPrompt(f.view,"https://source.invalid/","message","private-default",result);break;
                default:handled=f.target.onJsBeforeUnload(f.view,"https://source.invalid/","message",result);break;
            }
            check(handled&&result.confirms==0&&result.cancels==0,"Native callback was not deferred");
            MdoBrowserDialogs.Snapshot pending=f.target.dialogs.snapshot();
            check(pending.kind.equals(kind)&&pending.navigation==7&&pending.url.equals("https://source.invalid/"),"Callback metadata changed its owned view/epoch");
            MdoBrowserDialogs.Result reply=f.target.dialogs.respond(pending.id,true,false,null);
            check(reply.ok&&result.confirms==1&&result.cancels==0&&Handler.tasks.isEmpty(),"Native confirm or timer settlement failed");
            if(kind.equals("prompt"))check(result.text.equals("private-default"),"Prompt default was lost by native adapter");
            check(f.cancelled.isEmpty(),"Confirmation reported before-unload cancellation");
            check(f.blockers.size()==2&&f.blockers.get(0)&&!f.blockers.get(1),"Native blockers were not balanced");
            f.target.onJsConfirm(f.view,"url","duplicate",result);check(result.confirms==1&&result.cancels==0,"Duplicate framework result was replayed");
            f.target.close();f.target.viewDestroyed();
        }
        Fixture prompt=new Fixture();JsPromptResult empty=new JsPromptResult();prompt.target.onJsPrompt(prompt.view,"url","empty","default",empty);
        prompt.target.dialogs.respond(prompt.id(),true,true,"");check("".equals(empty.text)&&empty.confirms==1,"Explicit empty native prompt became default");
        JsPromptResult unicode=new JsPromptResult();prompt.target.onJsPrompt(prompt.view,"url","Unicode","default",unicode);
        prompt.target.dialogs.respond(prompt.id(),true,true,"\u4e2d\u6587 \ud83d\ude42");check("\u4e2d\u6587 \ud83d\ude42".equals(unicode.text),"Native prompt Unicode was changed");prompt.target.close();prompt.target.viewDestroyed();

        Fixture cancellation=new Fixture();JsPromptResult unload=new JsPromptResult();
        cancellation.target.onJsBeforeUnload(cancellation.view,"url","stay",unload);
        unload.callback=()->cancellation.epoch.incrementAndGet();
        check(cancellation.target.dialogs.respond(cancellation.id(),false,false,null).ok,"Before-unload cancellation failed");
        check(unload.cancels==1&&unload.confirms==0&&cancellation.cancelled.size()==1&&cancellation.cancelled.get(0)==7,
            "Native cancellation borrowed a replacement navigation epoch");
        JsResult plain=new JsResult();cancellation.target.onJsConfirm(cancellation.view,"url","no",plain);
        cancellation.target.dialogs.cancel();check(plain.cancels==1&&cancellation.cancelled.size()==1,"Ordinary cancel was treated as before-unload");
        cancellation.target.close();cancellation.target.viewDestroyed();

        Fixture foreign=new Fixture();JsResult other=new JsResult();
        check(!foreign.target.onJsConfirm(new WebView(),"https://source.invalid/","foreign",other),"Foreign view callback was admitted");
        check(other.confirms==0&&other.cancels==0&&foreign.target.dialogs.snapshot()==null,"Foreign view result was touched");
        foreign.owned.set(false);JsResult retired=new JsResult();check(foreign.target.onJsAlert(foreign.view,"url","retired",retired)&&retired.cancels==1,"Retired owned native result was left pending");
        check(foreign.blockers.isEmpty(),"Retired view changed active document blockers");foreign.target.close();foreign.target.viewDestroyed();

        Fixture timer=new Fixture();Handler.failAt=true;JsResult unscheduled=new JsResult();timer.target.onJsAlert(timer.view,"url","timer",unscheduled);
        check(unscheduled.cancels==1&&timer.target.dialogs.snapshot()==null&&Handler.tasks.isEmpty(),"Timer admission failure leaked native result");
        Handler.failAt=false;JsResult expiry=new JsResult();timer.target.onJsConfirm(timer.view,"url","deadline",expiry);
        Runnable callback=Handler.tasks.keySet().iterator().next();SystemClock.time+=120000;callback.run();
        check(expiry.cancels==1&&timer.target.dialogs.snapshot()==null,"Native absolute dialog deadline did not cancel");
        JsResult fresh=new JsResult();timer.target.onJsConfirm(timer.view,"url","fresh",fresh);callback.run();
        check(fresh.cancels==0&&timer.target.dialogs.snapshot()!=null,"Expired native callback cancelled replacement result");timer.target.close();timer.target.viewDestroyed();

        Fixture uncertain=new Fixture();JsResult lost=new JsResult();lost.fail=true;uncertain.target.onJsConfirm(uncertain.view,"url","lost",lost);
        MdoBrowserDialogs.Result reply=uncertain.target.dialogs.respond(uncertain.id(),true,false,null);
        check(!reply.ok&&reply.effectMayHaveOccurred&&!reply.cleanupConfirmed&&!uncertain.target.dialogs.clean(),"Lost native reply was claimed clean");
        uncertain.target.dialogs.cancel();uncertain.target.close();check(lost.confirms==1&&lost.cancels==0,"Native acknowledgement loss replayed a response");
        uncertain.target.viewDestroyed();check(uncertain.target.dialogs.clean(),"Exact native teardown did not acknowledge retained result cleanup");
        // A native callback arriving after exact teardown must not call back
        // into destroyed provider state, or poison the completed cleanup.
        JsResult late=new JsResult();late.fail=true;
        uncertain.target.onJsConfirm(uncertain.view,"url","late old view",late);
        check(late.confirms==0&&late.cancels==0&&uncertain.target.dialogs.clean(),"Late callback dispatched into a destroyed native view");
        check(uncertain.target.dialogs.snapshot()==null,"Late destroyed-view callback reactivated a dialog");
        Fixture rejected=new Fixture();rejected.owned.set(false);JsResult destroyedDuringCancel=new JsResult();destroyedDuringCancel.fail=true;
        destroyedDuringCancel.callback=()->{rejected.target.close();rejected.target.viewDestroyed();};
        rejected.target.onJsAlert(rejected.view,"url","retired result",destroyedDuringCancel);
        check(destroyedDuringCancel.cancels==1&&rejected.target.dialogs.clean(),"Rejected native result poisoned exact reentrant teardown");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
    }
}
