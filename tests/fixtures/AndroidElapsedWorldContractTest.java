package org.xserver.android;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Production world evaluator and the actual host scheduler clock expression.
 * Android elapsed/awake clocks and provider delivery are controlled, not a
 * physical sleep/Doze or installed-provider qualification. */
public final class AndroidElapsedWorldContractTest {
    static final class Clock implements MdoBrowserWorld.Scheduler {
        final Map<Runnable,Long> tasks = new HashMap<>();
        Runnable afterPost;
        boolean rejectNextPost;
        public long now(){return @@UI_CLOCK@@;}
        public void post(Runnable task,long delay){
            if(rejectNextPost){rejectNextPost=false;throw new IllegalStateException("Controlled timer rejection");}
            tasks.put(task,android.os.SystemClock.uptimeMillis()+delay);
            Runnable hook=afterPost;afterPost=null;if(hook!=null)hook.run();
        }
        public void remove(Runnable task){tasks.remove(task);}
        void advanceAwake(long duration){
            android.os.SystemClock.awake+=duration;android.os.SystemClock.elapsed+=duration;
            for(Runnable task:new ArrayList<>(tasks.keySet()))if(tasks.containsKey(task)&&tasks.get(task)<=android.os.SystemClock.awake)task.run();
        }
    }
    static final class Sink implements MdoBrowserWorld.Receiver {
        MdoBrowserWorld.Peer peer;
        public void message(MdoBrowserWorld.Peer value,String text){peer=value;}
        public void fault(String code){throw new AssertionError(code);}
    }
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static boolean test(String name,long sleep,int reentry)throws Exception {
        android.os.SystemClock.awake=1000;android.os.SystemClock.elapsed=1000;
        Clock clock=new Clock();Sink sink=new Sink();List<MdoBrowserWorld.Result> results=new ArrayList<>();
        Object view=AndroidWorldContractTest.invoke("newView",new Class<?>[]{});
        MdoBrowserWorld world=MdoBrowserWorld.open(view,AndroidWorldContractTest.remote.getClassLoader(),clock,sink);
        boolean passed=false;int dispatches=0;MdoBrowserWorld.Result result=null;
        try {
            world.profile("qa.elapsed-world");world.install("fixed clock fixture bootstrap");
            AndroidWorldContractTest.emit(view,true,"hello",AndroidWorldContractTest.peer());
            AndroidWorldContractTest.mode("defer");
            if(reentry!=0)clock.afterPost=()->{
                if(reentry==1){android.os.SystemClock.awake+=101;android.os.SystemClock.elapsed+=101;}
                else if(reentry==2)world.close();
                else world.invalidate();
            };
            int before=AndroidWorldContractTest.calls("peer:executeJavaScript");
            sink.peer.evaluate("fixed original request",100,results::add);
            dispatches=AndroidWorldContractTest.calls("peer:executeJavaScript")-before;
            if(reentry==0){
                check(clock.tasks.size()==1,"The original uptime timer did not remain pending");
                android.os.SystemClock.elapsed+=sleep;
                check(android.os.SystemClock.awake==1000,"Sleep unexpectedly advanced the UI timer clock");
            }
            AndroidWorldContractTest.invoke("late",new Class<?>[]{});
            if(!results.isEmpty())result=results.get(0);
            String expected=reentry>=2?"frame_changed":sleep>=100||reentry==1?"world_timed_out":null;
            passed=results.size()==1&&dispatches==(reentry==0?1:0)&&result!=null&&
                result.ok==(expected==null)&&java.util.Objects.equals(result.code,expected)&&
                result.evaluationMayHaveOccurred==(reentry==0)&&world.pending()==0&&world.outstanding()==0&&clock.tasks.isEmpty();
            AndroidWorldContractTest.invoke("late",new Class<?>[]{});
            check(results.size()==1,"A duplicate provider callback changed the original outcome");
        } finally {
            world.close();world.viewDestroyed(view);
            check(world.cleanupConfirmed()&&world.sharedOutstanding()==0,"Original world resources did not retire");
        }
        System.out.println("{\"case\":\""+name+"\",\"passed\":"+passed+",\"dispatches\":"+dispatches+
            ",\"completions\":"+results.size()+",\"ok\":"+(result!=null&&result.ok)+",\"code\":"+
            (result==null||result.code==null?"null":"\""+result.code+"\"")+",\"may_have_completed\":"+
            (result!=null&&result.evaluationMayHaveOccurred)+"}");
        return passed;
    }
    static boolean timer(String name,boolean expired,boolean rejected)throws Exception {
        android.os.SystemClock.awake=1000;android.os.SystemClock.elapsed=1000;
        Clock clock=new Clock();Sink sink=new Sink();List<MdoBrowserWorld.Result> results=new ArrayList<>();
        Object view=AndroidWorldContractTest.invoke("newView",new Class<?>[]{});
        MdoBrowserWorld world=MdoBrowserWorld.open(view,AndroidWorldContractTest.remote.getClassLoader(),clock,sink);
        boolean passed=false;
        try {
            world.profile("qa.elapsed-timer");world.install("fixed timer fixture bootstrap");
            AndroidWorldContractTest.emit(view,true,"hello",AndroidWorldContractTest.peer());
            AndroidWorldContractTest.mode("defer");sink.peer.evaluate("original held evaluation",100,results::add);
            if(expired)android.os.SystemClock.elapsed+=1000;
            clock.rejectNextPost=rejected;clock.advanceAwake(50);
            if(expired||rejected){
                String code=expired?"world_timed_out":"world_timer_unavailable";
                passed=results.size()==1&&!results.get(0).ok&&code.equals(results.get(0).code)&&results.get(0).evaluationMayHaveOccurred&&
                    world.pending()==0&&world.outstanding()==1&&world.sharedOutstanding()==1&&clock.tasks.isEmpty();
            }else passed=results.isEmpty()&&world.pending()==1&&world.outstanding()==1&&clock.tasks.size()==1;
            // A client timeout cannot release a provider-owned native callback.
            AndroidWorldContractTest.invoke("late",new Class<?>[]{});
            check(results.size()==1,"Timer/provider completion was lost or duplicated");
            check(world.pending()==0&&world.outstanding()==0&&world.sharedOutstanding()==0,"Late native completion did not release its original budget");
        }finally {world.close();world.viewDestroyed(view);check(world.cleanupConfirmed(),"Timer case retained the original world");}
        System.out.println("{\"case\":\""+name+"\",\"passed\":"+passed+",\"completed_once\":"+(results.size()==1)+"}");
        return passed;
    }
    public static void main(String[] args)throws Exception {
        boolean passed=true;
        try(URLClassLoader loader=new URLClassLoader(new URL[]{Paths.get(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            AndroidWorldContractTest.remote=Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil",true,loader);
            passed&=test("late-after-sleep",101,0);
            passed&=test("at-original-deadline",100,0);
            passed&=test("before-original-deadline",99,0);
            passed&=test("expires-during-timer-registration",0,1);
            passed&=test("close-during-timer-registration",0,2);
            passed&=test("navigation-during-timer-registration",0,3);
            passed&=timer("held-provider-after-sleep",true,false);
            passed&=timer("timer-check-before-deadline",false,false);
            passed&=timer("timer-rearm-rejected",false,true);
        }
        System.out.println("{\"passed\":"+passed+",\"checks\":"+checks+",\"framework_qualified\":false}");
        if(!passed)throw new AssertionError("Original UI world clock or pre-dispatch ownership/deadline was not preserved");
    }
}
