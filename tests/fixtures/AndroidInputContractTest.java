package org.xserver.android;

import java.util.*;

/** Production sequencer with deterministic framework/scheduler fault seams.
 * This gate is not an Android framework or trusted-event device test. */
public final class AndroidInputContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Throwing {void run()throws Exception;}
    static void denied(String code,Throwing action)throws Exception {
        try{action.run();throw new AssertionError("Expected "+code);}catch(MdoBrowserInput.Failure failure){check(failure.code.equals(code),"Wrong rejection: "+failure.code);}
    }
    static class Clock implements MdoBrowserInput.Scheduler {
        long time=100;boolean failPost,failRemove,failNow,oomPost,oomRemove;final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public long now(){if(failNow)throw new IllegalStateException();return time;}
        public void post(Runnable task,long delay){if(oomPost)throw new OutOfMemoryError("Synthetic timer allocation failure");if(failPost)throw new IllegalStateException();tasks.put(task,time+delay);}
        public void remove(Runnable task){if(oomRemove)throw new OutOfMemoryError("Synthetic timer removal failure");if(failRemove)throw new IllegalStateException();tasks.remove(task);}
        void advance(long delta){time+=delta;for(Runnable task:new ArrayList<>(tasks.keySet()))if(tasks.get(task)<=time){tasks.remove(task);task.run();}}
        void drain(){for(int i=0;!tasks.isEmpty();i++){if(i>200)throw new AssertionError("Unbounded input timer");advance(Math.max(0,Collections.min(tasks.values())-time));}}
    }
    static class Surface implements MdoBrowserInput.Target {
        long doc=7;int w=640,h=480;boolean visible=true,focus=true,accept=true,clean=true,throwDispatch,oomDispatch,oomCleanup,oomDocument;
        final List<String> calls=new ArrayList<>();Runnable reenter;
        public long document(){if(oomDocument)throw new OutOfMemoryError("Synthetic document lookup failure");return doc;}public int width(){return w;}public int height(){return h;}
        public boolean interactive(){return visible;}public boolean focused(){return focus;}
        private boolean send(String value){calls.add(value);if(reenter!=null){Runnable run=reenter;reenter=null;run.run();}
            if(oomDispatch)throw new OutOfMemoryError("Synthetic native dispatch allocation failure");if(throwDispatch)throw new IllegalStateException();return accept;}
        public boolean touch(int action,long down,long now,float x,float y){check(down<=now,"Non-monotonic native input");
            if(action==MdoBrowserInput.CANCEL){calls.add("cancel");if(oomCleanup)throw new OutOfMemoryError("Synthetic touch cleanup allocation failure");return clean;}return send("touch:"+action+":"+x+":"+y);}
        public boolean key(boolean down,long started,long now,String name,int modifiers){
            if(!down){calls.add("key:up:"+name+":"+modifiers);if(oomCleanup)throw new OutOfMemoryError("Synthetic key cleanup allocation failure");return clean;}return send("key:down:"+name+":"+modifiers);}
        public boolean text(String value){return send("text:"+value);}
    }
    static class Case {
        final Clock clock=new Clock();final Surface target=new Surface();final List<MdoBrowserInput.Result> results=new ArrayList<>();
        final MdoBrowserInput input=new MdoBrowserInput(target,clock);
        void failed(String code,boolean effects){check(results.size()==1,"Completion count");MdoBrowserInput.Result result=results.get(0);
            check(!result.ok&&code.equals(result.code)&&result.effectMayHaveOccurred==effects,"Failure/effect receipt: "+result.code);}
        void clean(){check(!input.busy()&&input.cleanupConfirmed()&&clock.tasks.isEmpty(),"Input did not drain");}
    }
    public static void main(String[] args)throws Exception {
        Case tap=new Case();tap.input.tap(20,30,tap.results::add);check(tap.input.busy()&&tap.target.calls.isEmpty(),"Input ran before timer admission");
        tap.clock.advance(0);check(tap.target.calls.size()==1&&tap.results.isEmpty(),"Tap did not keep a bounded down/up interval");tap.clock.advance(48);
        check(tap.results.get(0).ok&&tap.results.get(0).deliveredSteps==2,"Tap acknowledgement");tap.clean();
        Case swipe=new Case();float[] path={10,10,30,20,70,30,100,40};swipe.input.gesture(path,120,swipe.results::add);path[2]=9000;
        swipe.clock.drain();check(swipe.results.get(0).ok&&swipe.target.calls.size()==4&&swipe.target.calls.get(1).contains(":30.0:"),"Gesture points were not frozen");swipe.clean();
        Case input=new Case();input.input.text("中文🙂\n",input.results::add);input.clock.drain();check(input.results.get(0).ok&&input.target.calls.get(0).equals("text:中文🙂\n"),"Unicode text changed");input.clean();
        Case key=new Case();key.input.key("A",3,key.results::add);key.clock.drain();check(key.results.get(0).ok&&key.target.calls.equals(Arrays.asList("key:down:A:3","key:up:A:3")),"Key/modifier order");key.clean();
        Case invalid=new Case();
        for(float value:new float[]{-1,640,Float.NaN,Float.POSITIVE_INFINITY})denied("input_coordinate_invalid",()->invalid.input.tap(value,0,invalid.results::add));
        denied("input_coordinate_invalid",()->invalid.input.tap(0,480,invalid.results::add));
        denied("input_gesture_invalid",()->invalid.input.gesture(new float[130],100,invalid.results::add));
        denied("input_gesture_invalid",()->invalid.input.gesture(new float[3],100,invalid.results::add));
        denied("input_gesture_invalid",()->invalid.input.gesture(new float[4],5001,invalid.results::add));
        for(String name:new String[]{"Back","Power","Meta","a","F1",""})denied("input_key_invalid",()->invalid.input.key(name,0,invalid.results::add));
        denied("input_key_invalid",()->invalid.input.key("A",8,invalid.results::add));
        for(String text:new String[]{"\ud800","\udc00","a\0b",String.join("",Collections.nCopies(8193,"a")),String.join("",Collections.nCopies(6000,"中"))})
            denied("input_text_invalid",()->invalid.input.text(text,invalid.results::add));
        check(invalid.results.isEmpty()&&invalid.target.calls.isEmpty(),"Invalid input had effects");invalid.clean();
        Case busy=new Case();busy.input.tap(0,0,busy.results::add);denied("input_busy",()->busy.input.text("x",busy.results::add));busy.input.cancel();busy.failed("input_cancelled",false);busy.clean();
        Case cancelled=new Case();cancelled.input.tap(4,5,cancelled.results::add);cancelled.clock.advance(0);Runnable old=cancelled.clock.tasks.keySet().iterator().next();
        cancelled.input.cancel();cancelled.failed("input_cancelled",true);check(cancelled.target.calls.get(1).equals("cancel"),"Cancelled gesture was not released");
        old.run();check(cancelled.results.size()==1&&cancelled.target.calls.size()==2,"Late callback replayed input");cancelled.clean();
        Case closed=new Case();closed.input.key("Enter",0,closed.results::add);closed.clock.advance(0);closed.input.close();closed.input.close();closed.failed("input_closed",true);
        check(closed.target.calls.get(1).startsWith("key:up"),"Close left a held key");denied("input_closed",()->closed.input.tap(0,0,closed.results::add));closed.clean();
        for(int state=0;state<4;state++){
            Case changed=new Case();changed.input.key("Tab",0,changed.results::add);changed.clock.advance(0);
            String code;if(state==0){changed.target.doc++;code="frame_changed";}else if(state==1){changed.target.w++;code="input_viewport_changed";}
            else if(state==2){changed.target.visible=false;code="input_view_unavailable";}else{changed.target.focus=false;code="input_focus_lost";}
            changed.clock.drain();changed.failed(code,true);check(changed.target.calls.size()==2&&changed.target.calls.get(1).startsWith("key:up"),"Changed target got new input");changed.clean();
        }
        Case focus=new Case();focus.target.focus=false;denied("input_focus_required",()->focus.input.text("x",focus.results::add));focus.target.visible=false;
        denied("input_view_unavailable",()->focus.input.tap(0,0,focus.results::add));focus.clean();
        Case deadline=new Case();deadline.input.tap(1,1,deadline.results::add);deadline.clock.advance(600);deadline.failed("input_timed_out",false);check(deadline.target.calls.isEmpty(),"Expired queued operation was dispatched");deadline.clean();
        Case post=new Case();post.clock.failPost=true;denied("input_timer_unavailable",()->post.input.tap(0,0,post.results::add));check(post.target.calls.isEmpty()&&post.results.isEmpty(),"Timer rejection ran input");post.clean();
        Case timer=new Case();timer.input.tap(0,0,timer.results::add);timer.clock.failPost=true;timer.clock.advance(0);timer.failed("input_timer_unavailable",true);
        check(timer.target.calls.get(1).equals("cancel"),"Failed reschedule kept pressed pointer");timer.clean();
        Case noClock=new Case();noClock.clock.failNow=true;denied("input_timer_unavailable",()->noClock.input.tap(0,0,noClock.results::add));noClock.clean();
        Case clockLoss=new Case();clockLoss.input.text("x",clockLoss.results::add);clockLoss.clock.failNow=true;clockLoss.clock.advance(0);clockLoss.failed("input_dispatch_failed",false);clockLoss.clean();
        Case rejected=new Case();rejected.target.accept=false;rejected.input.tap(0,0,rejected.results::add);rejected.clock.drain();rejected.failed("input_not_accepted",true);rejected.clean();
        Case exception=new Case();exception.target.throwDispatch=true;exception.input.tap(0,0,exception.results::add);exception.clock.drain();exception.failed("input_dispatch_failed",true);exception.clean();
        Case poisoned=new Case();poisoned.input.tap(0,0,poisoned.results::add);poisoned.clock.advance(0);poisoned.target.clean=false;poisoned.input.cancel();poisoned.failed("input_cancelled",true);
        check(!poisoned.input.cleanupConfirmed(),"Unconfirmed cleanup was reported clean");denied("input_cleanup_failed",()->poisoned.input.tap(0,0,poisoned.results::add));
        Case reentrant=new Case();reentrant.target.reenter=()->reentrant.input.invalidate("frame_changed");reentrant.input.tap(0,0,reentrant.results::add);reentrant.clock.drain();reentrant.failed("frame_changed",true);reentrant.clean();
        Case last=new Case();last.target.reenter=()->last.target.doc++;last.input.text("x",last.results::add);last.clock.drain();last.failed("frame_changed",true);last.clean();
        Case finishing=new Case();finishing.input.tap(0,0,result->{finishing.results.add(result);try{finishing.input.text("next",finishing.results::add);}catch(Exception failure){throw new AssertionError(failure);}});
        finishing.clock.drain();check(finishing.results.size()==2&&finishing.results.get(1).ok,"Completion callback could not admit the next operation");finishing.clean();
        Case removed=new Case();removed.input.tap(0,0,removed.results::add);removed.clock.failRemove=true;removed.input.cancel();removed.failed("input_cancelled",false);
        check(!removed.input.cleanupConfirmed(),"Uncertain timer removal was hidden");removed.clock.failRemove=false;removed.clock.drain();check(removed.target.calls.isEmpty(),"Unremoved timer dispatched after cancellation");
        Case thread=new Case();final boolean[] owner={false};Thread other=new Thread(()->{try{thread.input.cancel();}catch(IllegalStateException expected){owner[0]=true;}});other.start();other.join();check(owner[0],"Cross-thread input admitted");thread.clean();
        for(String kind:new String[]{"tap","key","text"}){
            Case oom=new Case();oom.target.oomDispatch=true;
            if(kind.equals("tap"))oom.input.tap(1,2,oom.results::add);
            else if(kind.equals("key"))oom.input.key("Enter",0,oom.results::add);else oom.input.text("once",oom.results::add);
            boolean escaped=false;try{oom.clock.drain();}catch(OutOfMemoryError error){escaped=true;}
            check(!escaped,"Post-admission native OOM escaped its retained transaction");oom.failed("input_dispatch_failed",true);oom.clean();
            check(oom.target.calls.size()==(kind.equals("text")?1:2),"Native dispatch OOM replayed input or omitted held-state cleanup");
        }
        for(boolean keyCleanup:new boolean[]{false,true}){
            Case oom=new Case();if(keyCleanup)oom.input.key("Enter",0,oom.results::add);else oom.input.tap(1,2,oom.results::add);oom.clock.advance(0);
            oom.target.oomCleanup=true;boolean escaped=false;try{oom.input.cancel();}catch(OutOfMemoryError error){escaped=true;}
            check(!escaped,"Native cleanup OOM escaped before settling its transaction");oom.failed("input_cancelled",true);
            check(!oom.input.busy()&&!oom.input.cleanupConfirmed(),"Native cleanup OOM was claimed clean or left pending");
            oom.input.cancel();oom.input.close();check(oom.target.calls.size()==2,"Uncertain native cleanup was replayed");
        }
        Case oomTimer=new Case();oomTimer.input.tap(1,2,oomTimer.results::add);oomTimer.clock.oomPost=true;oomTimer.clock.advance(0);
        oomTimer.failed("input_timer_unavailable",true);check(oomTimer.target.calls.get(1).equals("cancel"),"Reschedule OOM retained a pressed touch");oomTimer.clean();
        Case oomRemove=new Case();oomRemove.input.tap(1,2,oomRemove.results::add);oomRemove.clock.oomRemove=true;oomRemove.input.cancel();
        oomRemove.failed("input_cancelled",false);check(!oomRemove.input.busy()&&!oomRemove.input.cleanupConfirmed(),"Timer removal OOM lost cleanup uncertainty");
        oomRemove.clock.oomRemove=false;oomRemove.clock.drain();check(oomRemove.target.calls.isEmpty(),"Stale timer sent input after removal OOM");
        Case oomGuard=new Case();oomGuard.input.tap(1,2,(remaining,complete)->{throw new OutOfMemoryError("Synthetic world guard allocation failure");},oomGuard.results::add);
        oomGuard.clock.drain();oomGuard.failed("input_guard_failed",false);oomGuard.clean();
        Case oomDocument=new Case();oomDocument.input.tap(1,2,oomDocument.results::add);oomDocument.clock.advance(0);oomDocument.target.oomDocument=true;
        oomDocument.clock.drain();oomDocument.failed("input_dispatch_failed",true);oomDocument.clean();
        Case oomReply=new Case();final int[] completionCalls={0};oomReply.input.text("once",result->{completionCalls[0]++;throw new OutOfMemoryError("Synthetic reply allocation failure");});
        oomReply.clock.drain();check(completionCalls[0]==1&&oomReply.target.calls.size()==1&&!oomReply.input.busy()&&!oomReply.input.cleanupConfirmed(),"Reply OOM was replayed or reported clean");
        denied("input_cleanup_failed",()->oomReply.input.text("unsafe",oomReply.results::add));
        Case oomReentry=new Case();oomReentry.input.text("once",result->{
            try{oomReentry.input.text("must not run",oomReentry.results::add);}catch(MdoBrowserInput.Failure error){throw new AssertionError(error);}
            throw new OutOfMemoryError("Synthetic reentrant reply allocation failure");
        });oomReentry.clock.drain();oomReentry.failed("input_cleanup_failed",false);
        check(oomReentry.target.calls.equals(Collections.singletonList("text:once"))&&!oomReentry.input.busy(),"Reply OOM let reentrantly admitted input run");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"android_device_verified\":false}");
    }
}
