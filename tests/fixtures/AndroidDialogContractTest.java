package org.xserver.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual production controller, deterministic native result/timer faults. */
public final class AndroidDialogContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static String repeat(String text,int count){StringBuilder out=new StringBuilder();for(int i=0;i<count;i++)out.append(text);return out.toString();}
    static final class Clock implements MdoBrowserDialogs.Scheduler {
        long time;boolean failAt,failRemove,oomAt,oomRemove,oomNow;long slowAt;Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public long now(){if(oomNow)throw new OutOfMemoryError("Synthetic clock allocation failure");return time;}
        public void at(Runnable task,long deadline){tasks.put(task,deadline);time+=slowAt;if(oomAt)throw new OutOfMemoryError("Synthetic timer allocation failure");if(failAt)throw new IllegalStateException("Synthetic timer admission loss");}
        public void remove(Runnable task){if(oomRemove)throw new OutOfMemoryError("Synthetic removal allocation failure");if(failRemove)throw new IllegalStateException("Synthetic removal loss");tasks.remove(task);}
        void advance(long duration){time+=duration;for(Runnable task:new ArrayList<>(tasks.keySet()))if(tasks.get(task)<=time)task.run();}
    }
    static final class Sink implements MdoBrowserDialogs.Response {
        int calls;boolean accepted,fail,oom;String text;Runnable callback;
        public void send(boolean accept,String text){calls++;accepted=accept;this.text=text;
            if(callback!=null)callback.run();if(oom)throw new OutOfMemoryError("Synthetic native result allocation failure");if(fail)throw new IllegalStateException("Private native exception must not escape");}
    }
    static final class Fixture {
        final Object view=new Object();final Clock clock=new Clock();final List<Boolean> changes=new ArrayList<>();
        boolean owned=true,listenerFail;int listenerOom;long epoch=1;MdoBrowserDialogs dialogs;
        Fixture(){dialogs=new MdoBrowserDialogs(view,clock,()->owned,()->epoch,value->{changes.add(value);if(listenerOom==(value?1:2))throw new OutOfMemoryError("Synthetic blocker allocation failure");if(listenerFail)throw new IllegalStateException();});}
        Sink open(String kind){Sink sink=new Sink();check(dialogs.open(sink,sink,kind,"https://fixture.invalid/","Private message","initial"),"Native dialog was not retained");return sink;}
        String id(){return dialogs.snapshot().id;}
    }
    public static void main(String[] args)throws Exception {
        for(String kind:new String[]{"alert","confirm","prompt","beforeunload"}){
            Fixture f=new Fixture();Sink sink=f.open(kind);MdoBrowserDialogs.Snapshot snapshot=f.dialogs.snapshot();
            check(snapshot.id.matches("dialog-[0-9a-f-]{36}")&&snapshot.kind.equals(kind)&&snapshot.navigation==1,"Dialog metadata identity");
            check(snapshot.respondable&&sink.calls==0&&f.changes.size()==1&&f.changes.get(0),"Opening dialog confirmed native result");
            check(f.dialogs.respond("dialog-foreign",true,false,null).code.equals("dialog_not_pending")&&sink.calls==0,"Foreign dialog received response");
            MdoBrowserDialogs.Result out=f.dialogs.respond(snapshot.id,true,false,null);
            check(out.ok&&out.effectMayHaveOccurred&&out.cleanupConfirmed&&sink.calls==1&&sink.accepted,"Native result was not confirmed once");
            check(kind.equals("prompt")?"initial".equals(sink.text):sink.text==null,"Prompt default or nonprompt payload changed");
            check(f.dialogs.snapshot()==null&&f.clock.tasks.isEmpty()&&f.changes.size()==2&&!f.changes.get(1),"Settlement retained native modal/timer");
            check(!f.dialogs.respond(snapshot.id,false,false,null).ok&&sink.calls==1,"Repeated response replayed native result");
            f.dialogs.open(sink,sink,kind,"url","message","initial");check(sink.calls==1,"Duplicate native callback replayed a settled result");
        }
        Fixture f=new Fixture();Sink first=f.open("confirm"),second=new Sink();String original=f.id();
        check(!f.dialogs.open(second,second,"confirm","url","message",null)&&second.calls==1&&!second.accepted,"Concurrent modal was not dismissed");
        check(f.id().equals(original)&&first.calls==0,"Second modal changed original ownership");
        check(f.dialogs.open(first,first,"confirm","url","message",null)&&first.calls==0,"Repeated original callback dispatched native result");
        f.dialogs.cancel();f.dialogs.cancel();check(first.calls==1&&!first.accepted&&f.dialogs.snapshot()==null,"Cancellation did not settle exactly once");
        Sink next=f.open("prompt");String nextId=f.id();check(!nextId.equals(original),"Native result reused retired identity");
        f.dialogs.respond(original,true,true,"must not enter the replacement");check(next.calls==0,"Stale response entered replacement modal");
        f.dialogs.respond(nextId,true,true,"");check(next.calls==1&&"".equals(next.text),"Explicit empty prompt became default");

        f=new Fixture();Sink unicode=f.open("prompt");String unicodeId=f.id();String maximum=repeat("\ud83d\ude42",2048);
        check(!f.dialogs.respond(unicodeId,true,true,maximum+"\ud83d\ude42").ok&&unicode.calls==0,"Multibyte oversized prompt was sent");
        check(!f.dialogs.respond(unicodeId,true,true,null).ok&&unicode.calls==0,"Null explicit prompt was sent");
        check(f.dialogs.respond(unicodeId,true,true,maximum).ok&&maximum.equals(unicode.text),"Bounded Unicode prompt changed");
        Sink huge=new Sink();check(!f.dialogs.open(huge,huge,"prompt","url","message",maximum+"x")&&huge.calls==1&&!huge.accepted,"Oversized private prompt default was retained");
        Sink bounded=new Sink();check(f.dialogs.open(bounded,bounded,"alert",repeat("\ud83d\ude42",1024),repeat("\ud83d\ude42",2048),null),"Bounded metadata admission");
        check(f.dialogs.snapshot().url.length()==1024&&f.dialogs.snapshot().message.length()==2048,"Page metadata exceeded UTF-8 byte bound");f.dialogs.cancel();

        Fixture expiry=new Fixture();Sink expires=expiry.open("beforeunload");Runnable oldTimer=expiry.clock.tasks.keySet().iterator().next();
        expiry.clock.advance(119999);check(expires.calls==0,"Dialog expired early");
        expiry.clock.time++;check(expiry.dialogs.snapshot()==null&&expires.calls==1&&!expires.accepted,"Snapshot bypassed absolute expiry");
        Sink fresh=expiry.open("alert");oldTimer.run();check(fresh.calls==0,"Late original timer cancelled replacement modal");expiry.dialogs.close();
        Fixture late=new Fixture();late.clock.slowAt=120001;Sink lateResult=new Sink();
        check(!late.dialogs.open(lateResult,lateResult,"confirm","url","message",null)&&lateResult.calls==1,"Slow timer admission retained an already expired dialog");
        Fixture early=new Fixture();Sink earlyResult=early.open("confirm");Runnable earlyTimer=early.clock.tasks.keySet().iterator().next();
        earlyTimer.run();check(earlyResult.calls==0&&early.clock.tasks.size()==1,"Early callback dismissed current result");early.clock.advance(120000);check(earlyResult.calls==1,"Rearmed absolute deadline did not expire");

        Fixture nav=new Fixture();Sink old=nav.open("prompt");String oldId=nav.id();nav.epoch++;
        check(!nav.dialogs.respond(oldId,true,true,"stale").ok&&old.calls==1&&!old.accepted,"Navigation allowed stale confirm");
        Sink detached=nav.open("confirm");nav.owned=false;check(nav.dialogs.snapshot()==null&&detached.calls==1&&!detached.accepted,"Removed view kept modal pending");
        Sink foreign=new Sink();check(!nav.dialogs.open(foreign,foreign,"confirm","url","message",null)&&foreign.calls==1,"Foreign view retained result");
        Fixture timer=new Fixture();timer.clock.failAt=true;Sink noTimer=new Sink();
        check(!timer.dialogs.open(noTimer,noTimer,"alert","url","message",null)&&noTimer.calls==1&&timer.dialogs.snapshot()==null,"Failed timer admission leaked modal");
        timer.clock.failAt=false;timer.clock.failRemove=true;Sink remove=timer.open("alert");Runnable removed=timer.clock.tasks.keySet().iterator().next();timer.dialogs.cancel();removed.run();check(remove.calls==1,"Failed timer removal replayed result");

        Fixture lost=new Fixture();Sink unconfirmed=lost.open("prompt");String lostId=lost.id();unconfirmed.fail=true;
        MdoBrowserDialogs.Result uncertain=lost.dialogs.respond(lostId,true,true,"answer");
        check(!uncertain.ok&&uncertain.effectMayHaveOccurred&&!uncertain.cleanupConfirmed&&uncertain.code.equals("dialog_response_uncertain"),"Lost native acknowledgement was treated as success");
        check(!lost.dialogs.clean()&&!lost.dialogs.snapshot().respondable,"Unconfirmed delivery admitted more input");
        lost.dialogs.cancel();lost.dialogs.respond(lostId,false,false,null);lost.clock.advance(120000);lost.dialogs.close();
        check(unconfirmed.calls==1&&!lost.dialogs.clean(),"Unconfirmed confirm/cancel was replayed");
        try{lost.dialogs.viewDestroyed(new Object());throw new AssertionError();}catch(IllegalStateException expected){check(!lost.dialogs.clean(),"Foreign teardown released native result");}
        lost.dialogs.viewDestroyed(lost.view);check(lost.dialogs.clean()&&lost.dialogs.snapshot()==null,"Exact view teardown did not release retained modal");
        Fixture reentrant=new Fixture();Sink reply=reentrant.open("confirm");reply.callback=reentrant.dialogs::close;
        check(reentrant.dialogs.respond(reentrant.id(),true,false,null).ok&&reply.calls==1,"Reentrant lifecycle replayed result");
        Fixture destroyed=new Fixture();Sink destroys=destroyed.open("confirm");destroys.fail=true;
        destroys.callback=()->{destroyed.dialogs.close();destroyed.dialogs.viewDestroyed(destroyed.view);};
        MdoBrowserDialogs.Result gone=destroyed.dialogs.respond(destroyed.id(),true,false,null);
        check(!gone.ok&&gone.cleanupConfirmed&&destroyed.dialogs.clean()&&destroys.calls==1,"Real reentrant teardown was lost after response exception");
        Fixture callback=new Fixture();callback.listenerFail=true;Sink failure=new Sink();
        check(!callback.dialogs.open(failure,failure,"confirm","url","message",null)&&failure.calls==1,"Failed blocker left native modal pending");
        check(!callback.dialogs.clean(),"Failed blocker cleanup was hidden");
        Fixture timerOom=new Fixture();timerOom.clock.oomAt=true;Sink noMemory=new Sink();boolean escaped=false;
        try{timerOom.dialogs.open(noMemory,noMemory,"confirm","url","message",null);}catch(OutOfMemoryError error){escaped=true;}
        check(!escaped&&noMemory.calls==1&&!noMemory.accepted&&timerOom.dialogs.snapshot()==null,"Timer allocation OOM escaped and retained its native modal");
        check(timerOom.clock.tasks.isEmpty()&&timerOom.dialogs.clean(),"Timer OOM left queued work or fabricated uncertain cleanup");
        Fixture admissionOom=new Fixture();admissionOom.clock.oomNow=true;Sink refusedMemory=new Sink();
        check(!admissionOom.dialogs.open(refusedMemory,refusedMemory,"alert","url","message",null)&&refusedMemory.calls==1,"Clock OOM retained an unadmitted native result");
        Fixture removalOom=new Fixture();Sink withTimer=removalOom.open("confirm");Runnable inert=removalOom.clock.tasks.keySet().iterator().next();removalOom.clock.oomRemove=true;
        removalOom.dialogs.cancel();inert.run();check(withTimer.calls==1&&removalOom.dialogs.snapshot()==null,"Timer removal OOM leaked or replayed native cleanup");
        Fixture blockerOom=new Fixture();blockerOom.listenerOom=1;Sink failedBlocker=new Sink();
        check(!blockerOom.dialogs.open(failedBlocker,failedBlocker,"confirm","url","message",null)&&failedBlocker.calls==1&&blockerOom.dialogs.clean(),"Blocker admission OOM left a native modal");
        Fixture unblockOom=new Fixture();Sink delivered=unblockOom.open("confirm");unblockOom.listenerOom=2;
        MdoBrowserDialogs.Result unblockFailure=unblockOom.dialogs.respond(unblockOom.id(),true,false,null);
        check(!unblockFailure.ok&&unblockFailure.code.equals("dialog_cleanup_failed")&&!unblockOom.dialogs.clean()&&delivered.calls==1,"Unblock OOM admitted new native work");
        Fixture nativeOom=new Fixture();Sink allocated=nativeOom.open("prompt");allocated.oom=true;String allocatedId=nativeOom.id();
        MdoBrowserDialogs.Result allocationLost=nativeOom.dialogs.respond(allocatedId,true,true,"answer");
        check(!allocationLost.ok&&allocationLost.effectMayHaveOccurred&&!allocationLost.cleanupConfirmed&&allocationLost.code.equals("dialog_response_uncertain"),"Native result OOM lost effect uncertainty");
        nativeOom.dialogs.cancel();nativeOom.dialogs.close();check(allocated.calls==1,"Native OOM replayed confirmation or cancellation");
        nativeOom.dialogs.viewDestroyed(nativeOom.view);check(nativeOom.dialogs.clean(),"Native OOM retained resources after exact teardown");
        Fixture thread=new Fixture();Sink waiting=thread.open("alert");boolean[] wrong={false};
        Thread worker=new Thread(()->{try{thread.dialogs.cancel();}catch(IllegalStateException expected){wrong[0]=true;}});worker.start();worker.join();
        check(wrong[0]&&waiting.calls==0,"Worker thread touched UI-owned native result");thread.dialogs.close();thread.dialogs.viewDestroyed(thread.view);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false,\"runtime_dependencies_added\":false}");
    }
}
