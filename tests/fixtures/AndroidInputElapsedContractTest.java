package org.xserver.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native event timestamps use uptime; operation expiry must include suspend. */
public final class AndroidInputElapsedContractTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Clock implements MdoBrowserInput.Scheduler {
        long uptime=100,elapsed=100000;boolean failElapsed,failPost;Runnable readingElapsed;
        final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public long now(){return uptime;}
        public long elapsed(){if(readingElapsed!=null){Runnable action=readingElapsed;readingElapsed=null;action.run();}
            if(failElapsed)throw new IllegalStateException("Unavailable clock");return elapsed;}
        public void post(Runnable task,long delay){if(failPost)throw new IllegalStateException("Unavailable timer");tasks.put(task,uptime+delay);}
        public void remove(Runnable task){tasks.remove(task);}
        void advance(long awake,long asleep){uptime+=awake;elapsed+=awake+asleep;
            for(int i=0;i<30;i++){
                Runnable next=null;long first=Long.MAX_VALUE;
                for(Map.Entry<Runnable,Long> row:tasks.entrySet())if(row.getValue()<=uptime&&row.getValue()<first){next=row.getKey();first=row.getValue();}
                if(next==null)return;tasks.remove(next);next.run();
            }throw new AssertionError("Unbounded input timers");
        }
    }
    static final class Surface implements MdoBrowserInput.Target {
        final List<String> calls=new ArrayList<>();long down,time;long hoverDelay;
        public long document(){return 1;}public int width(){return 640;}public int height(){return 480;}
        public boolean interactive(){return true;}public boolean focused(){return true;}
        public boolean acquireFocus(){calls.add("focus");return true;}
        public long hoverDelay(long now){return hoverDelay;}
        public boolean hover(long now,float x,float y){calls.add("hover");time=now;return true;}
        public boolean touch(int action,long started,long now,float x,float y){calls.add("touch:"+action);down=started;time=now;return true;}
        public boolean key(boolean pressed,long started,long now,String key,int modifiers){calls.add("key:"+pressed);down=started;time=now;return true;}
        public boolean text(String value){calls.add("text");return true;}
    }
    static final class Case {
        final Clock clock=new Clock();final Surface target=new Surface();final List<MdoBrowserInput.Result> results=new ArrayList<>();
        final List<MdoBrowserInput.Checked> guards=new ArrayList<>();final List<Long> remaining=new ArrayList<>();
        final MdoBrowserInput input=new MdoBrowserInput(target,clock);
        final MdoBrowserInput.Guard guard=(budget,complete)->{remaining.add(budget);guards.add(complete);};
        void failed(boolean sent){check(results.size()==1&&!results.get(0).ok&&results.get(0).code.equals("input_timed_out")&&
            results.get(0).effectMayHaveOccurred==sent&&results.get(0).cleanupConfirmed,"Suspend expiry lost its original outcome");}
        void clean(){check(!input.busy()&&input.cleanupConfirmed()&&clock.tasks.isEmpty(),"Input reservation did not drain");}
    }
    public static void main(String[] args)throws Exception {
        Case queued=new Case();queued.target.hoverDelay=300;queued.input.hover(20,30,queued.guard,queued.results::add);
        queued.clock.advance(300,10000);queued.failed(false);
        check(queued.guards.isEmpty()&&queued.target.calls.isEmpty(),"Expired hover started a guard or native effect after resume");queued.clean();

        Case callback=new Case();callback.input.text("local fixture",callback.guard,callback.results::add);callback.clock.advance(0,0);
        check(callback.guards.size()==1&&callback.remaining.get(0)==500,"Guard clock domains were mixed");
        callback.clock.advance(0,10000);callback.guards.get(0).complete(null);callback.failed(false);
        check(callback.target.calls.isEmpty(),"Late guard typed after suspend expiry");callback.clean();

        Case touch=new Case();touch.input.tap(20,30,touch.results::add);touch.clock.advance(0,0);
        check(touch.target.calls.size()==1&&touch.target.down==100&&touch.target.time==100,"Native DOWN used an elapsed timestamp");
        touch.clock.advance(48,10000);touch.failed(true);
        check(touch.target.calls.equals(java.util.Arrays.asList("touch:0","touch:3"))&&touch.target.down==100&&touch.target.time==148,
            "Expired held touch was replayed or cleanup used the wrong clock");touch.clean();

        Case key=new Case();key.input.key("Enter",0,key.guard,key.results::add);key.clock.advance(0,0);key.guards.get(0).complete(null);
        key.clock.advance(0,10000);key.guards.get(0).complete(null);key.clock.advance(50,0);key.failed(true);
        check(key.target.calls.equals(java.util.Arrays.asList("key:true","key:false")),"Resume timeout left a held key or replayed a step");key.clean();

        Case focus=new Case();focus.input.focus(focus.guard,focus.results::add);focus.clock.advance(0,10000);focus.failed(false);
        check(focus.target.calls.isEmpty()&&focus.guards.isEmpty(),"Expired queue acquired native focus");focus.clean();

        Case watch=new Case();watch.input.text("local fixture",watch.guard,watch.results::add);watch.clock.advance(0,0);
        MdoBrowserInput.Checked late=watch.guards.get(0);watch.clock.advance(50,10000);watch.failed(false);late.complete(null);
        check(watch.target.calls.isEmpty()&&watch.results.size()==1,"Resume watchdog did not retire the exact pending guard");watch.clean();

        Case ordinary=new Case();ordinary.input.tap(20,30,ordinary.results::add);ordinary.clock.advance(0,0);ordinary.clock.advance(48,0);
        check(ordinary.results.size()==1&&ordinary.results.get(0).ok&&ordinary.target.down==100&&ordinary.target.time==148,
            "Ordinary gesture cadence changed with independent clock epochs");ordinary.clean();

        Case budget=new Case();budget.target.hoverDelay=300;budget.input.hover(20,30,budget.guard,budget.results::add);
        budget.clock.advance(300,100);check(budget.remaining.size()==1&&budget.remaining.get(0)==400,"Sleep during hover renewed the guard budget");
        budget.guards.get(0).complete(null);check(budget.results.get(0).ok&&budget.target.time==400,"Eligible hover used the deadline clock as native time");budget.clean();

        Case reentry=new Case();reentry.input.text("local fixture",reentry.guard,reentry.results::add);reentry.clock.advance(0,0);
        reentry.clock.readingElapsed=reentry.input::cancel;reentry.guards.get(0).complete(null);
        check(reentry.results.size()==1&&reentry.results.get(0).code.equals("input_cancelled")&&reentry.target.calls.isEmpty(),
            "Deadline sampling acted after reentrant cancellation");reentry.clean();

        Case repost=new Case();repost.input.text("local fixture",repost.guard,repost.results::add);repost.clock.advance(0,0);
        repost.clock.failPost=true;repost.clock.advance(50,0);repost.guards.get(0).complete(null);
        check(repost.results.size()==1&&repost.results.get(0).code.equals("input_timer_unavailable")&&repost.target.calls.isEmpty(),
            "Failed deadline recheck left an executable guard");repost.clean();

        Case unavailable=new Case();unavailable.clock.failElapsed=true;
        try{unavailable.input.hover(20,30,null,unavailable.results::add);throw new AssertionError("Failed deadline clock admitted input");}
        catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_timer_unavailable"),"Wrong deadline clock refusal");}
        check(unavailable.results.isEmpty()&&unavailable.target.calls.isEmpty(),"Failed clock generated a receipt/effect");unavailable.clean();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"device_suspend_verified\":false}");
    }
}
