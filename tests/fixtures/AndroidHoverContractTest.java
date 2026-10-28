package org.xserver.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Production sequencer and guards, not an Android framework event pass. */
public final class AndroidHoverContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Clock implements MdoBrowserInput.Scheduler {
        long time;final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public long now(){return time;}public void post(Runnable task,long delay){tasks.put(task,time+delay);}
        public void remove(Runnable task){tasks.remove(task);}
        void tick(){for(Runnable task:new ArrayList<>(tasks.keySet()))if(tasks.get(task)<=time){tasks.remove(task);task.run();}}
    }
    static final class Surface implements MdoBrowserInput.Target {
        long epoch=7;int width=640,height=480,moves,other;boolean visible=true,accept=true,oom;
        float x,y;Runnable reenter;
        public long document(){return epoch;}public int width(){return width;}public int height(){return height;}
        public boolean interactive(){return visible;}public boolean focused(){return false;}
        public boolean hover(long time,float x,float y){moves++;this.x=x;this.y=y;
            if(reenter!=null){Runnable action=reenter;reenter=null;action.run();}
            if(oom)throw new OutOfMemoryError("Synthetic hover allocation failure");return accept;}
        public boolean acquireFocus(){other++;return false;}
        public boolean touch(int action,long down,long time,float x,float y){other++;return false;}
        public boolean key(boolean down,long started,long time,String name,int modifiers){other++;return false;}
        public boolean text(String value){other++;return false;}
    }
    static final class Case {
        final Clock clock=new Clock();final Surface target=new Surface();final List<MdoBrowserInput.Result> results=new ArrayList<>();
        final List<MdoBrowserInput.Checked> pending=new ArrayList<>();
        final MdoBrowserInput input=new MdoBrowserInput(target,clock);
        final MdoBrowserInput.Guard guard=(remaining,complete)->{check(remaining>0&&remaining<=500,"Hover borrowed a new deadline");pending.add(complete);};
        void begin()throws Exception{input.hover(20,30,guard,results::add);clock.tick();}
        void clean(){check(!input.busy()&&input.cleanupConfirmed()&&clock.tasks.isEmpty(),"Hover did not drain");check(target.other==0,"Hover emitted touch, key, text or focus");}
        void failed(String code,boolean effects){check(results.size()==1,"Hover completion count");MdoBrowserInput.Result result=results.get(0);
            check(!result.ok&&result.code.equals(code)&&result.effectMayHaveOccurred==effects,"Hover refusal/effect changed");}
    }
    public static void main(String[] args)throws Exception {
        Case normal=new Case();normal.begin();check(normal.target.moves==0&&normal.results.isEmpty(),"Hover preceded its isolated guard");
        normal.pending.get(0).complete(null);check(normal.results.size()==1&&normal.results.get(0).ok&&normal.results.get(0).deliveredSteps==1,"Hover acknowledgement missing");
        check(normal.target.moves==1&&normal.target.x==20&&normal.target.y==30,"Hover was replayed or changed its coordinates");normal.clean();
        normal.input.cancel();normal.input.close();check(normal.target.moves==1,"Settled hover acquired a release event");
        Case cancel=new Case();cancel.begin();MdoBrowserInput.Checked late=cancel.pending.get(0);cancel.input.cancel();late.complete(null);
        cancel.failed("input_cancelled",false);check(cancel.target.moves==0,"Late hover guard sent input after cancellation");cancel.clean();
        for(int field=0;field<4;field++){
            Case stale=new Case();stale.begin();String code;
            if(field==0){stale.target.epoch++;code="frame_changed";}else if(field==1){stale.target.width++;code="input_viewport_changed";}
            else if(field==2){stale.target.visible=false;code="input_view_unavailable";}else{stale.clock.time=501;code="input_timed_out";}
            stale.pending.get(0).complete(null);stale.failed(code,false);check(stale.target.moves==0,"Stale hover witness sent input");stale.clean();
        }
        for(float value:new float[]{-1,640,Float.NaN,Float.POSITIVE_INFINITY}){
            Case invalid=new Case();try{invalid.input.hover(value,1,invalid.guard,invalid.results::add);throw new AssertionError("Invalid hover admitted");}
            catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_coordinate_invalid"),"Hover coordinate refusal changed");}
            check(invalid.results.isEmpty()&&invalid.target.moves==0,"Invalid hover had effects");invalid.clean();
        }
        Case moved=new Case();moved.begin();moved.pending.get(0).complete("input_target_changed");moved.failed("input_target_changed",false);moved.clean();
        Case rejected=new Case();rejected.target.accept=false;rejected.begin();rejected.pending.get(0).complete(null);
        rejected.failed("input_not_accepted",true);check(rejected.target.moves==1,"Rejected native hover was replayed");rejected.clean();
        Case oom=new Case();oom.target.oom=true;oom.begin();oom.pending.get(0).complete(null);oom.failed("input_dispatch_failed",true);
        check(oom.target.moves==1,"Native hover OOM replayed input");oom.clean();
        Case reentry=new Case();reentry.target.reenter=()->reentry.input.cancel();reentry.begin();reentry.pending.get(0).complete(null);
        reentry.failed("input_cancelled",true);check(reentry.target.moves==1,"Reentrant hover cancellation replayed input");reentry.clean();
        Case replacement=new Case();replacement.begin();MdoBrowserInput.Checked original=replacement.pending.get(0);replacement.input.cancel();
        replacement.input.hover(40,50,replacement.guard,replacement.results::add);replacement.clock.tick();original.complete(null);
        check(replacement.target.moves==0,"Old guard sent input into replacement hover");replacement.pending.get(1).complete(null);
        check(replacement.results.size()==2&&replacement.results.get(1).ok&&replacement.target.moves==1&&replacement.target.x==40,"Replacement hover lost its own identity");replacement.clean();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
    }
}
