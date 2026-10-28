package org.xserver.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Real conversion/sequencer with a synthetic target, not an Android pass. */
public final class AndroidWheelContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Clock implements MdoBrowserInput.Scheduler {
        long time;final Map<Runnable,Long> tasks=new HashMap<>();
        public long now(){return time;}public void post(Runnable task,long delay){tasks.put(task,time+delay);}public void remove(Runnable task){tasks.remove(task);}
        void advance(long amount){time+=amount;
            for(int turn=0;turn<30;turn++){Runnable next=null;
                for(Map.Entry<Runnable,Long> entry:tasks.entrySet())if(entry.getValue()<=time){next=entry.getKey();break;}
                if(next==null)return;tasks.remove(next);next.run();
            }throw new AssertionError("Unbounded wheel timers");
        }
    }
    static final class Surface implements MdoBrowserInput.Target {
        long epoch=1;int width=640,height=480,events,touches,keys,texts;boolean interactive=true,accept=true;Runnable hook;
        float x,y,dx,dy;
        public long document(){return epoch;}public int width(){return width;}public int height(){return height;}
        public boolean interactive(){return interactive;}public boolean focused(){return false;}
        public boolean touch(int action,long down,long time,float x,float y){touches++;return true;}
        public boolean key(boolean down,long started,long time,String name,int modifiers){keys++;return true;}
        public boolean text(String value){texts++;return true;}
        public boolean wheel(long time,float x,float y,float dx,float dy){events++;this.x=x;this.y=y;this.dx=dx;this.dy=dy;
            if(hook!=null)hook.run();return accept;}
    }
    static final class Case {
        final Clock clock=new Clock();final Surface target=new Surface();final List<MdoBrowserInput.Result> results=new ArrayList<>();
        final MdoBrowserInput input=new MdoBrowserInput(target,clock);MdoBrowserInput.Checked pending;
        void begin()throws Exception{input.wheel(20,30,96,-48,(remaining,complete)->{check(remaining>0&&remaining<=500,"Unbounded wheel guard");pending=complete;},results::add);clock.advance(0);}
        void clean(){check(!input.busy()&&input.cleanupConfirmed()&&clock.tasks.isEmpty(),"Wheel job did not drain");
            check(target.touches==0&&target.keys==0&&target.texts==0,"Wheel used a touch/key/text fallback");}
        void failed(String code,boolean sent){check(results.size()==1&&!results.get(0).ok&&code.equals(results.get(0).code)&&results.get(0).effectMayHaveOccurred==sent,"Wrong wheel failure receipt");}
    }
    public static void main(String[] args)throws Exception{
        float[] ticks=MdoBrowserWheel.ticks(96,-48,48);check(ticks[0]==-2&&ticks[1]==1,"CDP-compatible wheel signs/factor differ");
        ticks=MdoBrowserWheel.ticks(1.5f,3,72);check(Math.abs(ticks[0]*72+1.5)<0.00001&&Math.abs(ticks[1]*72+3)<0.00001,"Fractional ticks lost pixel precision");
        ticks=MdoBrowserWheel.ticks(0,0,48);check(ticks[0]==0&&ticks[1]==0,"Zero wheel delta changed");
        for(float factor:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,Float.MIN_VALUE}){
            try{MdoBrowserWheel.ticks(96,48,factor);throw new AssertionError("Invalid factor admitted");}
            catch(IllegalArgumentException expected){checks++;}
        }
        Case good=new Case();good.begin();check(good.target.events==0,"Wheel preceded isolated admission");
        good.pending.complete(null);good.pending.complete(null);check(good.target.events==1&&good.results.size()==1&&good.results.get(0).ok,"Wheel dispatched/completed twice");
        check(good.target.x==20&&good.target.y==30&&good.target.dx==96&&good.target.dy==-48,"Native pixel geometry changed");
        check(good.results.get(0).deliveredSteps==1&&good.results.get(0).effectMayHaveOccurred,"Wheel receipt is not native single delivery");good.clean();
        for(int change=0;change<4;change++){
            Case stale=new Case();stale.begin();String code;
            if(change==0){stale.target.epoch++;code="frame_changed";}else if(change==1){stale.target.height++;code="input_viewport_changed";}
            else if(change==2){stale.target.interactive=false;code="input_view_unavailable";}else{stale.clock.time=501;code="input_timed_out";}
            stale.pending.complete(null);stale.failed(code,false);check(stale.target.events==0,"Stale wheel guard dispatched input");stale.clean();
        }
        Case expired=new Case();expired.begin();expired.clock.advance(501);expired.failed("input_timed_out",false);expired.pending.complete(null);
        check(expired.target.events==0,"Expired wheel was replayed");expired.clean();
        Case cancelled=new Case();cancelled.begin();MdoBrowserInput.Checked old=cancelled.pending;cancelled.input.cancel();cancelled.failed("input_cancelled",false);
        cancelled.input.wheel(40,50,12,18,(remaining,complete)->cancelled.pending=complete,cancelled.results::add);cancelled.clock.advance(0);old.complete(null);
        check(cancelled.target.events==0,"Old guard reached replacement wheel");cancelled.pending.complete(null);check(cancelled.target.events==1&&cancelled.results.size()==2,"Replacement wheel did not settle independently");cancelled.clean();
        Case reentry=new Case();reentry.target.hook=reentry.input::cancel;reentry.begin();reentry.pending.complete(null);reentry.failed("input_cancelled",true);
        check(reentry.target.events==1,"Reentrant cancellation repeated wheel");reentry.clean();
        Case resize=new Case();resize.target.hook=()->resize.target.width++;resize.begin();resize.pending.complete(null);resize.failed("input_viewport_changed",true);resize.clean();
        Case refused=new Case();refused.target.accept=false;refused.begin();refused.pending.complete(null);refused.failed("input_not_accepted",true);refused.clean();
        Case invalid=new Case();
        for(float delta:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,MdoBrowserWheel.MAX_DELTA+1}){
            try{invalid.input.wheel(0,0,delta,0,null,invalid.results::add);throw new AssertionError();}
            catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_scroll_invalid"),"Invalid scroll delta refusal");}
        }
        try{invalid.input.wheel(640,0,0,1,null,invalid.results::add);throw new AssertionError();}
        catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_coordinate_invalid"),"Invalid scroll point refusal");}
        check(invalid.target.events==0&&invalid.results.isEmpty(),"Invalid scroll changed native state");invalid.clean();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
    }
}
