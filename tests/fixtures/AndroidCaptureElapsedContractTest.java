package org.xserver.android;

import android.os.Handler;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;

/** Exercise the production runtime scheduler with independently controlled clocks. */
public final class AndroidCaptureElapsedContractTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Case {
        final AndroidCaptureContractTest.Surface target=new AndroidCaptureContractTest.Surface();
        final AndroidCaptureContractTest.Worker worker=new AndroidCaptureContractTest.Worker();
        final MdoBrowserCapture.Budget budget=new MdoBrowserCapture.Budget(2);
        final List<MdoBrowserCapture.Checked> guards=new ArrayList<>();final List<MdoBrowserCapture.Result> results=new ArrayList<>();
        final MdoBrowserCapture capture;
        Case(){Handler.reset();SystemClock.awake=100;SystemClock.elapsed=100000;
            capture=new MdoBrowserCapture(target,MdoBrowserCaptureRuntime.scheduler(),worker,budget);}
        void begin()throws Exception{capture.capture(new MdoBrowserCapture.Plan(target.snapshot(),10,20,200,100,.5,"png",128,100),
            (remaining,complete)->{check(remaining>0&&remaining<=100,"Capture renewed its deadline");guards.add(complete);},results::add);}
        void sleep(){SystemClock.elapsed+=10000;}
        void advance(long awake){SystemClock.awake+=awake;SystemClock.elapsed+=awake;Handler.drain();}
        void ready(){guards.get(0).complete(null);target.ready.run();}
        void encode(){worker.run();Handler.drain();}
        void failed(String code){check(results.size()==1&&!results.get(0).ok&&results.get(0).code.equals(code)&&results.get(0).data==null,"Expired capture did not withhold pixels");}
        void clean(){Handler.drain();check(capture.cleanupConfirmed()&&budget.pending()==0&&Handler.pending()==0,"Capture did not drain its original reservation");}
        void cleared(){for(byte value:target.encoder.data)check(value==0,"Expired private image bytes survived");}
    }
    public static void main(String[] args)throws Exception {
        Case before=new Case();before.begin();before.sleep();before.guards.get(0).complete(null);before.failed("capture_timed_out");
        check(before.target.ready==null&&before.target.draws==0,"Expired guard requested native drawing");before.clean();

        Case visual=new Case();visual.begin();visual.guards.get(0).complete(null);visual.sleep();visual.target.ready.run();visual.failed("capture_timed_out");
        check(visual.target.draws==0,"Late native readiness drew an expired page");visual.clean();

        Case encoder=new Case();encoder.begin();encoder.ready();encoder.sleep();encoder.encode();encoder.failed("capture_timed_out");
        check(encoder.target.encoder.encodes==1&&encoder.target.encoder.closes==1,"Original encoder lifetime changed");encoder.cleared();encoder.clean();

        Case after=new Case();after.begin();after.ready();after.encode();check(after.guards.size()==2&&after.results.isEmpty(),"Missing original post-encode guard");
        after.sleep();after.guards.get(1).complete(null);after.failed("capture_timed_out");after.cleared();after.clean();

        Case watch=new Case();watch.begin();watch.sleep();watch.advance(50);watch.failed("capture_timed_out");
        watch.guards.get(0).complete(null);check(watch.target.draws==0&&watch.results.size()==1,"Late guard escaped the resume watchdog");watch.clean();

        Case ordinary=new Case();ordinary.begin();ordinary.advance(50);check(ordinary.results.isEmpty()&&ordinary.budget.pending()==1,"Watchdog expired on its first slice");
        ordinary.advance(49);check(ordinary.results.isEmpty(),"Watchdog shortened the original budget");ordinary.advance(1);ordinary.failed("capture_timed_out");ordinary.clean();

        Case success=new Case();success.begin();success.advance(50);success.ready();success.encode();success.guards.get(1).complete(null);
        check(success.results.size()==1&&success.results.get(0).ok&&java.util.Arrays.equals(success.results.get(0).data,new byte[]{1,2,3}),"Eligible capture lost its original pixels");success.clean();

        Case failure=new Case();failure.begin();Handler.failAt=true;failure.advance(50);failure.failed("capture_timer_unavailable");
        failure.guards.get(0).complete(null);check(failure.target.draws==0,"Failed rearm left an executable guard");failure.clean();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false,\"device_suspend_verified\":false}");
    }
}
