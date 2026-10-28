package org.xserver.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deferred isolated-world checks must not outlive the admitted native action. */
public final class AndroidInputGuardContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Clock implements MdoBrowserInput.Scheduler {
        long time;int posts,failAt;Error failure;
        final Map<Runnable,Long> tasks=new HashMap<>();
        public long now(){return time;}
        public void post(Runnable task,long delay){tasks.put(task,time+delay);if(++posts==failAt)throw failure;}
        public void remove(Runnable task){tasks.remove(task);}
        void advance(long amount){time+=amount;
            for(int turns=0;turns<40;turns++){
                Runnable next=null;for(Map.Entry<Runnable,Long> item:tasks.entrySet())if(item.getValue()<=time){next=item.getKey();break;}
                if(next==null)return;tasks.remove(next);next.run();
            }throw new AssertionError("Unbounded scheduler loop");
        }
    }
    static final class Surface implements MdoBrowserInput.Target {
        long epoch=7;int width=640,height=480;boolean visible=true,focus=true;
        final List<String> calls=new ArrayList<>();
        public long document(){return epoch;}public int width(){return width;}public int height(){return height;}
        public boolean interactive(){return visible;}public boolean focused(){return focus;}
        public boolean acquireFocus(){calls.add("focus");focus=true;return true;}
        public boolean touch(int action,long down,long now,float x,float y){calls.add("touch:"+action);return true;}
        public boolean key(boolean down,long started,long now,String name,int modifiers){calls.add("key:"+down);return true;}
        public boolean text(String text){calls.add("text:"+text);return true;}
    }
    static final class Gate implements MdoBrowserInput.Guard {
        final List<MdoBrowserInput.Checked> waiting=new ArrayList<>();
        public void check(long remaining,MdoBrowserInput.Checked complete){
            AndroidInputGuardContractTest.check(remaining>0&&remaining<=5500,"Guard received an unbounded deadline");waiting.add(complete);
        }
    }
    static final class Case {
        final Clock clock=new Clock();final Surface surface=new Surface();final Gate gate=new Gate();
        final List<MdoBrowserInput.Result> results=new ArrayList<>();
        final MdoBrowserInput input=new MdoBrowserInput(surface,clock);
        void begin()throws Exception{input.tap(20,30,gate,results::add);clock.advance(0);}
        void failed(String code,boolean sent){check(results.size()==1&&!results.get(0).ok&&code.equals(results.get(0).code)&&
            results.get(0).effectMayHaveOccurred==sent,"Incorrect guarded failure receipt");}
        void clean(){check(!input.busy()&&input.cleanupConfirmed()&&clock.tasks.isEmpty(),"Guarded input did not drain");}
    }
    public static void main(String[] args)throws Exception {
        Case held=new Case();held.begin();check(held.surface.calls.isEmpty()&&held.input.busy(),"Input preceded isolated verification");
        held.clock.advance(549);held.failed("input_timed_out",false);held.gate.waiting.get(0).complete(null);
        check(held.surface.calls.isEmpty(),"Late verification replayed timed-out input");held.clean();

        Case success=new Case();success.begin();MdoBrowserInput.Checked first=success.gate.waiting.get(0);
        first.complete(null);first.complete(null);check(success.surface.calls.size()==1,"Duplicate guard callback repeated a press");
        success.clock.advance(48);check(success.gate.waiting.size()==2&&success.surface.calls.size()==1,"Release skipped its fresh guard");
        first.complete("old failure");success.gate.waiting.get(1).complete(null);
        check(success.results.size()==1&&success.results.get(0).ok&&success.surface.calls.size()==2,"Original check disturbed a later step");success.clean();

        Case lost=new Case();lost.begin();lost.gate.waiting.get(0).complete(null);lost.clock.advance(48);
        lost.gate.waiting.get(1).complete("input_focus_lost");lost.failed("input_focus_lost",true);
        check(lost.surface.calls.get(1).equals("touch:3"),"Rejected release did not cancel the original held touch");lost.clean();

        for(int field=0;field<4;field++){
            Case changed=new Case();changed.begin();String code;
            if(field==0){changed.surface.epoch++;code="frame_changed";}
            else if(field==1){changed.surface.width++;code="input_viewport_changed";}
            else if(field==2){changed.surface.visible=false;code="input_view_unavailable";}
            else{changed.clock.time=549;code="input_timed_out";}
            changed.gate.waiting.get(0).complete(null);changed.failed(code,false);
            check(changed.surface.calls.isEmpty(),"Changed target accepted a delayed guard");changed.clean();
        }

        Case replacement=new Case();replacement.begin();MdoBrowserInput.Checked old=replacement.gate.waiting.get(0);
        replacement.input.cancel();replacement.failed("input_cancelled",false);
        replacement.input.text("fresh",replacement.gate,replacement.results::add);replacement.clock.advance(0);old.complete(null);
        check(replacement.surface.calls.isEmpty(),"Old guard acted on replacement input");replacement.gate.waiting.get(1).complete(null);
        check(replacement.results.size()==2&&replacement.results.get(1).ok&&replacement.surface.calls.get(0).equals("text:fresh"),"Replacement did not complete independently");replacement.clean();

        Case focused=new Case();focused.surface.focus=false;
        focused.input.focus(focused.gate,focused.results::add);focused.clock.advance(0);
        check(focused.surface.calls.size()==1&&focused.surface.calls.get(0).equals("focus")&&focused.results.isEmpty(),"Native focus was not held pending DOM confirmation");
        focused.gate.waiting.get(0).complete(null);check(focused.results.get(0).ok,"Verified focus did not complete");focused.clean();

        Case key=new Case();key.input.key("Tab",0,key.gate,key.results::add);key.clock.advance(0);key.gate.waiting.get(0).complete(null);
        key.clock.advance(16);key.surface.focus=false;key.gate.waiting.get(1).complete(null);key.failed("input_focus_lost",true);
        check(key.surface.calls.size()==2&&key.surface.calls.get(1).equals("key:false"),"Focus loss left a held native key");key.clean();

        Case waitingKey=new Case();waitingKey.input.key("Enter",0,waitingKey.gate,waitingKey.results::add);waitingKey.clock.advance(0);
        waitingKey.gate.waiting.get(0).complete(null);waitingKey.clock.advance(16);waitingKey.clock.advance(501);
        waitingKey.failed("input_timed_out",true);waitingKey.gate.waiting.get(1).complete(null);
        check(waitingKey.surface.calls.size()==2&&waitingKey.surface.calls.get(1).equals("key:false"),"An unresponsive guard left a key pressed or replayed its release");waitingKey.clean();

        Case reentry=new Case();reentry.input.tap(1,2,(remaining,complete)->{reentry.input.cancel();complete.complete(null);},reentry.results::add);
        reentry.clock.advance(0);reentry.failed("input_cancelled",false);check(reentry.surface.calls.isEmpty(),"Reentrant guard cancellation dispatched input");reentry.clean();

        Case throwing=new Case();throwing.input.text("denied",(remaining,complete)->{throw new IllegalStateException("Synthetic world failure");},throwing.results::add);
        throwing.clock.advance(0);throwing.failed("input_guard_failed",false);throwing.clean();

        for(int at=1;at<=2;at++){
            Case oom=new Case();oom.clock.failAt=at;oom.clock.failure=new OutOfMemoryError("Synthetic input timer allocation failure");
            try{oom.input.tap(0,0,oom.gate,oom.results::add);throw new AssertionError("OOM swallowed");}
            catch(OutOfMemoryError failure){check(failure==oom.clock.failure,"Timer admission replaced its original OOM");}
            check(oom.results.isEmpty()&&oom.surface.calls.isEmpty(),"Failed admission had effects");oom.clean();
        }
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
    }
}
