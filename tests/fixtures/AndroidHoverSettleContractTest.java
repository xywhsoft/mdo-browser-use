package org.xserver.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reproduce delayed native tap feedback overwriting a later mouse hover.
 * Framework timing and pixels are checked separately on the installed phone. */
public final class AndroidHoverSettleContractTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Touch/hover settle check "+checks);}
    static final class Clock implements MdoBrowserInput.Scheduler {
        long time=100;boolean failPost;final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public long now(){return time;}public void post(Runnable task,long delay){if(failPost)throw new IllegalStateException();tasks.put(task,time+delay);}
        public void remove(Runnable task){tasks.remove(task);}
        void advance(long milliseconds){time+=milliseconds;for(Runnable task:new ArrayList<>(tasks.keySet())){Long due=tasks.get(task);if(due!=null&&due<=time){tasks.remove(task);task.run();}}}
        void drain(){for(int i=0;!tasks.isEmpty();i++){check(i<10);advance(Math.max(0,java.util.Collections.min(tasks.values())-time));}}
    }
    static final class Surface implements MdoBrowserInput.Target {
        long epoch=1,quietUntil;int width=640,moves,other;boolean visible=true,hovered,failTiming,oomTiming;Long forced;Runnable reenter;
        public long document(){return epoch;}public int width(){return width;}public int height(){return 480;}
        public boolean interactive(){return visible;}public boolean focused(){return false;}
        public long hoverDelay(long now){if(reenter!=null){Runnable original=reenter;reenter=null;original.run();}if(failTiming)throw new IllegalStateException();if(oomTiming)throw new OutOfMemoryError();return forced!=null?forced:Math.max(0,quietUntil-now);}
        public boolean hover(long time,float x,float y){moves++;hovered=true;return true;}
        public boolean touch(int action,long down,long time,float x,float y){other++;if(action==MdoBrowserInput.UP)quietUntil=time+300;return true;}
        public boolean key(boolean down,long started,long time,String name,int modifiers){throw new AssertionError("Unexpected key");}
        public boolean text(String value){throw new AssertionError("Unexpected text");}
    }
    static final class Case {
        final Clock clock=new Clock();final Surface target=new Surface();final List<MdoBrowserInput.Result> results=new ArrayList<>();
        final MdoBrowserInput input=new MdoBrowserInput(target,clock);final List<MdoBrowserInput.Checked> guard=new ArrayList<>();
        void tap()throws Exception{input.tap(10,20,results::add);clock.drain();check(results.size()==1&&results.get(0).ok&&target.other==2);}
        void hover(boolean guarded)throws Exception{input.hover(30,40,guarded?(remaining,complete)->{check(remaining>0&&remaining<=500);guard.add(complete);}:null,results::add);}
        void clean(){check(!input.busy()&&input.cleanupConfirmed()&&clock.tasks.isEmpty());}
        void failed(String code){MdoBrowserInput.Result r=results.get(results.size()-1);check(!r.ok&&r.code.equals(code)&&!r.effectMayHaveOccurred&&r.deliveredSteps==0);}
    }
    public static void main(String[] args)throws Exception {
        Case cold=new Case();cold.hover(false);cold.clock.advance(0);check(cold.target.moves==1&&cold.results.get(0).ok);cold.clean();
        Case warm=new Case();warm.tap();warm.hover(false);warm.clock.advance(0);check(warm.input.busy()&&warm.target.moves==0);
        try{warm.input.tap(10,20,warm.results::add);throw new AssertionError("Touch interleaved with pending hover");}catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_busy"));}
        warm.clock.advance(150);warm.target.hovered=false;check(warm.target.moves==0);warm.clock.advance(150);
        check(warm.target.moves==1&&warm.target.hovered&&warm.results.size()==2&&warm.results.get(1).ok&&warm.results.get(1).deliveredSteps==1);check(warm.target.other==2);warm.clean();
        for(int variant=0;variant<4;variant++){
            Case stale=new Case();stale.tap();stale.hover(true);stale.clock.advance(0);check(stale.guard.isEmpty());String code;
            if(variant==0){stale.target.epoch++;code="frame_changed";}else if(variant==1){stale.target.width++;code="input_viewport_changed";}
            else if(variant==2){stale.target.visible=false;code="input_view_unavailable";}else{stale.input.cancel();code="input_cancelled";}
            stale.clock.drain();stale.failed(code);check(stale.target.moves==0&&stale.guard.isEmpty());stale.clean();
        }
        Case canceled=new Case();canceled.tap();canceled.hover(false);Runnable old=canceled.clock.tasks.entrySet().stream().min(Map.Entry.comparingByValue()).get().getKey();
        canceled.input.cancel();canceled.failed("input_cancelled");canceled.clock.advance(1000);canceled.hover(false);canceled.clock.advance(0);old.run();
        check(canceled.target.moves==1&&canceled.results.size()==3&&canceled.results.get(2).ok);canceled.clean();
        Case timeout=new Case();timeout.tap();timeout.hover(true);timeout.clock.advance(300);check(timeout.guard.size()==1&&timeout.target.moves==0);
        timeout.clock.advance(501);timeout.guard.get(0).complete(null);timeout.failed("input_timed_out");check(timeout.target.moves==0);timeout.clean();
        for(long delay:new long[]{-1,1001,Long.MAX_VALUE}){
            Case invalid=new Case();invalid.target.forced=delay;try{invalid.hover(false);throw new AssertionError("Invalid timing admitted");}
            catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_timing_invalid"));}check(invalid.results.isEmpty()&&invalid.target.moves==0);invalid.clean();
        }
        for(boolean oom:new boolean[]{false,true}){
            Case invalid=new Case();invalid.target.failTiming=!oom;invalid.target.oomTiming=oom;try{invalid.hover(false);throw new AssertionError("Failed timing admitted");}
            catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_target_failed"));}check(invalid.results.isEmpty());invalid.clean();
        }
        Case reentry=new Case();reentry.target.reenter=()->reentry.input.cancel();reentry.hover(false);reentry.failed("input_cancelled");check(reentry.target.moves==0);reentry.clean();
        Case replace=new Case();replace.target.reenter=()->{replace.input.cancel();try{replace.hover(false);}catch(Exception error){throw new AssertionError(error);}};
        replace.hover(false);replace.clock.advance(0);check(replace.results.size()==2&&!replace.results.get(0).ok&&replace.results.get(1).ok&&replace.target.moves==1);replace.clean();
        Case maximum=new Case();maximum.target.forced=1000L;maximum.hover(true);maximum.clock.advance(999);check(maximum.guard.isEmpty()&&maximum.target.moves==0);
        maximum.clock.advance(1);check(maximum.guard.size()==1);maximum.guard.get(0).complete(null);check(maximum.results.size()==1&&maximum.results.get(0).ok&&maximum.target.moves==1);maximum.clean();
        Case post=new Case();post.target.quietUntil=400;post.clock.failPost=true;try{post.hover(true);throw new AssertionError("Failed scheduler admitted");}
        catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_timer_unavailable"));}check(post.target.moves==0&&post.results.isEmpty());post.clean();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
    }
}
