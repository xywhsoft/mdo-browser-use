package org.xserver.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Actual production capture transaction; framework pixels are not simulated. */
public final class AndroidCaptureContractTest {
    static int checks;
    static final String DOC="fd-12345678-1234-4123-8123-123456789abc";
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Attempt {void run()throws Exception;}
    static void refused(String code,Attempt run)throws Exception {
        try{run.run();throw new AssertionError("Missing refusal: "+code);}catch(MdoBrowserCapture.Failure error){check(code.equals(error.code),"Wrong refusal: "+error.code);}
    }
    static void invalid(Attempt run)throws Exception {
        try{run.run();throw new AssertionError("Accepted invalid geometry");}catch(IllegalArgumentException error){checks++;}
    }
    static final class Clock implements MdoBrowserCapture.Scheduler {
        long time,admissionDelay;int posts,failAt;boolean throwAfterPost,removeFailure;
        final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public synchronized long now(){return time;}
        public synchronized void post(Runnable task,long delay){
            if(++posts==1)time+=admissionDelay;queue(task,time+delay);
        }
        public synchronized void at(Runnable task,long deadline){if(++posts==1)time+=admissionDelay;queue(task,deadline);}
        private void queue(Runnable task,long deadline){
            if(posts==failAt){if(throwAfterPost)tasks.put(task,deadline);throw new OutOfMemoryError("Synthetic timer allocation");}
            tasks.put(task,deadline);
        }
        public synchronized void remove(Runnable task){tasks.remove(task);if(removeFailure)throw new IllegalStateException("Synthetic remove failure");}
        void advance(long milliseconds){time+=milliseconds;
            for(int turns=0;turns<100;turns++){
                Runnable selected=null;synchronized(this){for(Map.Entry<Runnable,Long> entry:tasks.entrySet())if(entry.getValue()<=time){selected=entry.getKey();break;}if(selected!=null)tasks.remove(selected);}
                if(selected==null)return;selected.run();
            }throw new AssertionError("Unbounded capture queue");
        }
    }
    static final class Encoder implements MdoBrowserCapture.Raster {
        int encodes,closes;String mode="ok";Runnable during;byte[] data;
        public byte[] encode(String format,int maximum){encodes++;check("png".equals(format)&&maximum==128,"Encoder received changed plan");
            if(during!=null)during.run();
            if(mode.equals("oom"))throw new OutOfMemoryError("Synthetic encoding OOM");
            if(mode.equals("failure"))throw new IllegalStateException("Private encoder failure");
            if(mode.equals("limit"))throw new MdoBrowserCapture.Limit();
            return data=mode.equals("empty")?new byte[0]:mode.equals("oversized")?new byte[129]:new byte[]{1,2,3};
        }
        public void close(){closes++;if(mode.equals("cleanup"))throw new IllegalStateException("Synthetic bitmap release failure");}
    }
    static final class Surface implements MdoBrowserCapture.Target {
        String document=DOC;long navigation=7;int width=640,height=480,scrollX=20,scrollY=40,draws;double scale=2;
        boolean visible=true,throwReady,afterReady,oomDraw,cleanupDraw;Runnable ready,duringDraw,duringSnapshot;final Encoder encoder=new Encoder();
        public MdoBrowserCapture.Snapshot snapshot(){Runnable callback=duringSnapshot;duringSnapshot=null;if(callback!=null)callback.run();
            if(!visible)return null;return new MdoBrowserCapture.Snapshot(document,navigation,width,height,scrollX,scrollY,scale);}
        public void ready(Runnable complete){ready=complete;if(throwReady&&!afterReady)throw new IllegalStateException("Native readiness acknowledgement lost");
            if(afterReady)complete.run();if(throwReady)throw new IllegalStateException("Post-callback exception");}
        public MdoBrowserCapture.Raster draw(MdoBrowserCapture.Plan plan){draws++;if(cleanupDraw)throw new MdoBrowserCapture.Cleanup();if(oomDraw)throw new OutOfMemoryError("Synthetic bitmap allocation");if(duringDraw!=null)duringDraw.run();return encoder;}
    }
    static final class Worker implements MdoBrowserCapture.Worker {
        final List<Runnable> tasks=new ArrayList<>(),removals=new ArrayList<>();boolean fail,runThenFail;
        public void execute(Runnable task){tasks.add(task);if(runThenFail)task.run();if(fail)throw new IllegalStateException("Worker admission unconfirmed");}
        public void remove(Runnable task){removals.add(task);}
        void run(){for(Runnable task:new ArrayList<>(tasks))task.run();}
    }
    static final class Case {
        final Surface surface=new Surface();final Clock clock=new Clock();final Worker worker=new Worker();
        final MdoBrowserCapture.Budget budget;final MdoBrowserCapture capture;
        final List<MdoBrowserCapture.Checked> guards=new ArrayList<>();final List<MdoBrowserCapture.Result> results=new ArrayList<>();
        Case(){this(new MdoBrowserCapture.Budget(2));}
        Case(MdoBrowserCapture.Budget budget){this.budget=budget;capture=new MdoBrowserCapture(surface,clock,worker,budget);}
        MdoBrowserCapture.Plan plan(){return new MdoBrowserCapture.Plan(surface.snapshot(),10,20,200,100,.5,"png",128,100);}
        void begin()throws Exception {capture.capture(plan(),(remaining,complete)->{check(remaining==100,"Original deadline changed");guards.add(complete);},results::add);}
        void ready(){guards.get(guards.size()-1).complete(null);surface.ready.run();}
        void encode(){worker.run();clock.advance(0);}
        void finish(){int before=guards.size();encode();if(guards.size()>before)guards.get(guards.size()-1).complete(null);}
        void failed(String code){check(results.size()==1&&!results.get(0).ok&&code.equals(results.get(0).code)&&results.get(0).data==null,"Wrong failed capture receipt: "+code);}
        void clean(){check(!capture.busy()&&capture.cleanupConfirmed()&&budget.pending()==0&&clock.tasks.isEmpty(),"Original capture did not drain");}
    }
    public static void main(String[] args)throws Exception {
        Case success=new Case();success.begin();check(success.surface.draws==0&&success.budget.pending()==1,"Capture drew before original guard");
        success.guards.get(0).complete(null);success.guards.get(0).complete(null);check(success.surface.draws==0,"Capture drew before visual readiness");
        success.surface.ready.run();success.surface.ready.run();check(success.surface.draws==1&&success.surface.encoder.encodes==0,"Duplicate native readiness drew twice or encoded on UI");
        success.finish();success.worker.run();success.clock.advance(0);
        check(success.results.size()==1&&success.results.get(0).ok&&Arrays.equals(success.results.get(0).data,new byte[]{1,2,3}),"Capture lost encoded bytes");
        check(success.results.get(0).plan.outputWidth==100&&success.results.get(0).plan.outputHeight==50,"Output dimensions changed");
        check(success.surface.encoder.encodes==1&&success.surface.encoder.closes==1,"Raster was reused or released twice");success.clean();

        Case post=new Case();post.begin();post.ready();post.encode();
        check(post.guards.size()==2&&post.results.isEmpty()&&post.capture.busy(),"Encoded bytes escaped before the post-capture guard");
        check(post.budget.pending()==1&&post.surface.encoder.closes==1,"Post guard lost its private-image reservation or retained the bitmap");
        post.guards.get(0).complete(null);post.guards.get(0).complete("frame_changed");check(post.results.isEmpty(),"Old pre-capture callback admitted or rejected the post-capture phase");
        post.guards.get(1).complete(null);post.guards.get(1).complete("late_failure");
        check(post.results.size()==1&&post.results.get(0).ok,"Post-capture acknowledgement did not settle exactly once");post.clean();
        for(String mode:new String[]{"reject","cancel","timeout","navigation","close"}){
            Case withheld=new Case();withheld.begin();withheld.ready();withheld.encode();
            byte[] held=withheld.surface.encoder.data;
            if(mode.equals("reject"))withheld.guards.get(1).complete("frame_changed");
            if(mode.equals("cancel"))withheld.capture.cancel();
            if(mode.equals("timeout"))withheld.clock.advance(100);
            if(mode.equals("navigation")){withheld.surface.navigation++;withheld.guards.get(1).complete(null);}
            if(mode.equals("close"))withheld.capture.close();
            withheld.failed(mode.equals("cancel")?"capture_cancelled":mode.equals("timeout")?"capture_timed_out":mode.equals("close")?"capture_closed":mode.equals("navigation")?"capture_changed":"frame_changed");
            for(byte value:held)check(value==0,"Withheld encoded screenshot was not cleared");
            withheld.guards.get(1).complete(null);check(withheld.results.size()==1,"Late post guard published a withheld screenshot");withheld.clean();
        }
        Case remainingPost=new Case();final List<Long> budgets=new ArrayList<>();
        remainingPost.capture.capture(remainingPost.plan(),(remaining,complete)->{budgets.add(remaining);remainingPost.guards.add(complete);},remainingPost.results::add);
        remainingPost.ready();remainingPost.clock.advance(40);remainingPost.encode();
        check(budgets.equals(Arrays.asList(100L,60L)),"Post guard extended the original capture deadline");
        remainingPost.clock.advance(60);remainingPost.failed("capture_timed_out");remainingPost.guards.get(1).complete(null);remainingPost.clean();
        Case resultFailure=new Case();resultFailure.capture.capture(resultFailure.plan(),(remaining,complete)->complete.complete(null),result->{throw new OutOfMemoryError("Synthetic result allocation failure");});
        resultFailure.surface.ready.run();resultFailure.encode();
        for(byte value:resultFailure.surface.encoder.data)check(value==0,"Lost result delivery retained private source bytes");resultFailure.clean();
        Case guardSnapshotCancel=new Case();guardSnapshotCancel.begin();guardSnapshotCancel.surface.duringSnapshot=guardSnapshotCancel.capture::cancel;
        guardSnapshotCancel.guards.get(0).complete(null);guardSnapshotCancel.failed("capture_cancelled");
        check(guardSnapshotCancel.surface.ready==null,"Reentrant snapshot cancellation still requested native readiness");guardSnapshotCancel.clean();
        Case readySnapshotCancel=new Case();readySnapshotCancel.begin();readySnapshotCancel.guards.get(0).complete(null);
        readySnapshotCancel.surface.duringSnapshot=readySnapshotCancel.capture::cancel;readySnapshotCancel.surface.ready.run();
        readySnapshotCancel.failed("capture_cancelled");check(readySnapshotCancel.surface.draws==0,"Reentrant readiness snapshot cancellation still allocated a raster");
        readySnapshotCancel.clock.advance(0);readySnapshotCancel.clean();
        Case encodedSnapshotCancel=new Case();encodedSnapshotCancel.begin();encodedSnapshotCancel.ready();encodedSnapshotCancel.worker.run();
        encodedSnapshotCancel.surface.duringSnapshot=encodedSnapshotCancel.capture::cancel;encodedSnapshotCancel.clock.advance(0);encodedSnapshotCancel.failed("capture_cancelled");
        for(byte value:encodedSnapshotCancel.surface.encoder.data)check(value==0,"Reentrant encoded snapshot cancellation retained source bytes");encodedSnapshotCancel.clean();
        Case finalSnapshotCancel=new Case();finalSnapshotCancel.begin();finalSnapshotCancel.ready();finalSnapshotCancel.encode();
        finalSnapshotCancel.surface.duringSnapshot=finalSnapshotCancel.capture::cancel;finalSnapshotCancel.guards.get(1).complete(null);finalSnapshotCancel.failed("capture_cancelled");
        for(byte value:finalSnapshotCancel.surface.encoder.data)check(value==0,"Reentrant final snapshot cancellation retained source bytes");finalSnapshotCancel.clean();

        Case noGuard=new Case();noGuard.begin();noGuard.clock.advance(100);noGuard.failed("capture_timed_out");
        noGuard.guards.get(0).complete(null);check(noGuard.surface.ready==null&&noGuard.surface.draws==0,"Late guard restarted timed-out capture");noGuard.clean();
        Case rejected=new Case();rejected.begin();rejected.guards.get(0).complete("frame_changed");rejected.failed("frame_changed");rejected.clean();
        Case privateError=new Case();privateError.begin();privateError.guards.get(0).complete("Private page exception\nwith content");privateError.failed("capture_guard_failed");privateError.clean();

        Case pending=new Case();pending.begin();pending.guards.get(0).complete(null);pending.capture.cancel();pending.failed("capture_cancelled");
        check(pending.capture.busy()&&pending.budget.pending()==1,"Cancellation released an unfinished native visual callback");
        refused("capture_busy",pending::begin);pending.surface.ready.run();check(pending.surface.draws==0,"Late ready callback drew cancelled capture");pending.clean();
        pending.begin();pending.ready();pending.finish();check(pending.results.size()==2&&pending.results.get(1).ok,"A deliberate later capture could not recover");pending.clean();

        for(boolean after:new boolean[]{false,true}){
            Case lost=new Case();lost.surface.throwReady=true;lost.surface.afterReady=after;lost.begin();lost.guards.get(0).complete(null);
            if(!after){lost.failed("capture_ready_unconfirmed");check(lost.capture.busy(),"Unconfirmed native readiness was treated as terminal");lost.surface.ready.run();lost.clean();}
            else{lost.finish();check(lost.results.get(0).ok,"Post-callback exception replaced the completed capture");lost.clean();}
        }
        Case destroyed=new Case();destroyed.begin();destroyed.guards.get(0).complete(null);destroyed.capture.close();
        check(destroyed.capture.busy()&&destroyed.budget.pending()==1,"Close alone claimed native view destruction");
        destroyed.capture.viewDestroyed();destroyed.surface.ready.run();destroyed.failed("capture_closed");destroyed.clean();
        refused("capture_closed",destroyed::begin);

        for(int field=0;field<7;field++){
            Case changed=new Case();changed.begin();changed.guards.get(0).complete(null);
            if(field==0)changed.surface.navigation++;if(field==1)changed.surface.document="fd-00000000-0000-4000-8000-000000000000";
            if(field==2)changed.surface.width++;if(field==3)changed.surface.height++;if(field==4)changed.surface.scrollX++;
            if(field==5)changed.surface.scale=3;if(field==6)changed.surface.visible=false;
            changed.surface.ready.run();changed.clock.advance(0);changed.failed("capture_changed");
            check(changed.surface.draws==0,"Changed original snapshot reached bitmap allocation");changed.clean();
        }
        Case duringDraw=new Case();duringDraw.surface.duringDraw=()->duringDraw.capture.cancel();duringDraw.begin();duringDraw.ready();
        duringDraw.clock.advance(0);duringDraw.failed("capture_cancelled");
        check(duringDraw.surface.encoder.closes==1&&duringDraw.worker.tasks.isEmpty(),"Reentrant draw cancellation leaked or encoded the raster");duringDraw.clean();
        Case changedDraw=new Case();changedDraw.surface.duringDraw=()->changedDraw.surface.navigation++;changedDraw.begin();changedDraw.ready();
        changedDraw.clock.advance(0);changedDraw.failed("capture_changed");check(changedDraw.surface.encoder.closes==1,"Changed draw raster not released");changedDraw.clean();

        Case queued=new Case();queued.begin();queued.ready();queued.capture.cancel();queued.failed("capture_cancelled");queued.worker.run();queued.clock.advance(0);
        check(queued.worker.removals.size()==1&&queued.worker.removals.get(0)==queued.worker.tasks.get(0),"Cancelled queue removal lost its exact task");
        check(queued.surface.encoder.encodes==0&&queued.surface.encoder.closes==1,"Cancelled queued work encoded or closed twice");queued.clean();
        Case submission=new Case();submission.worker.fail=true;submission.begin();submission.ready();submission.worker.run();submission.clock.advance(0);
        submission.failed("capture_worker_unavailable");check(submission.surface.encoder.encodes==0&&submission.surface.encoder.closes==1,"Late accepted work used a discarded bitmap");submission.clean();
        Case sync=new Case();sync.worker.fail=sync.worker.runThenFail=true;sync.begin();sync.ready();sync.clock.advance(0);
        sync.failed("capture_worker_unavailable");check(sync.surface.encoder.encodes==1&&sync.surface.encoder.closes==1,"Ambiguous completed submission double-freed bitmap");sync.clean();

        for(String mode:new String[]{"oom","failure","limit","empty","oversized","cleanup"}){
            Case failure=new Case();failure.surface.encoder.mode=mode;failure.begin();failure.ready();failure.finish();
            failure.failed(mode.equals("oom")?"capture_memory_limit":mode.equals("failure")?"capture_encode_failed":mode.equals("cleanup")?"capture_cleanup_failed":"capture_byte_limit");
            check(failure.surface.encoder.closes==1,"Encode failure skipped or repeated bitmap release");
            if(mode.equals("cleanup")){check(!failure.capture.cleanupConfirmed()&&failure.budget.pending()==1,"Unconfirmed raster release cleared its budget");refused("capture_cleanup_failed",failure::begin);}
            else failure.clean();
        }
        Case allocation=new Case();allocation.surface.oomDraw=true;allocation.begin();allocation.ready();allocation.clock.advance(0);
        allocation.failed("capture_memory_limit");check(allocation.worker.tasks.isEmpty(),"Allocation failure reached worker");allocation.clean();
        Case drawCleanup=new Case();drawCleanup.surface.cleanupDraw=true;drawCleanup.begin();drawCleanup.ready();drawCleanup.clock.advance(0);
        drawCleanup.failed("capture_cleanup_failed");check(drawCleanup.budget.pending()==1&&!drawCleanup.capture.cleanupConfirmed(),"Unconfirmed draw cleanup released its native reservation");
        refused("capture_cleanup_failed",drawCleanup::begin);
        for(boolean after:new boolean[]{false,true}){
            Case timer=new Case();timer.clock.failAt=1;timer.clock.throwAfterPost=after;timer.begin();timer.failed("capture_timer_unavailable");
            check(timer.guards.isEmpty(),"Failed timer admission executed a guard");timer.clean();
        }
        Case expiredAdmission=new Case();expiredAdmission.clock.admissionDelay=100;expiredAdmission.begin();
        check(expiredAdmission.guards.isEmpty(),"An expired timer admission started another native guard");expiredAdmission.failed("capture_timed_out");expiredAdmission.clean();
        Case slowAdmission=new Case();slowAdmission.clock.admissionDelay=40;
        slowAdmission.capture.capture(slowAdmission.plan(),(remaining,complete)->{
            check(remaining==60,"Guard did not inherit the remaining original deadline");slowAdmission.guards.add(complete);
        },slowAdmission.results::add);
        slowAdmission.clock.advance(60);slowAdmission.failed("capture_timed_out");slowAdmission.guards.get(0).complete(null);slowAdmission.clean();

        MdoBrowserCapture.Budget shared=new MdoBrowserCapture.Budget(2);Case a=new Case(shared),b=new Case(shared),c=new Case(shared);
        a.begin();a.guards.get(0).complete(null);a.capture.cancel();b.begin();b.guards.get(0).complete(null);b.capture.cancel();
        refused("capture_resource_limit",c::begin);check(shared.pending()==2,"Repeated cancellations exceeded the native callback budget");
        a.capture.viewDestroyed();c.begin();check(shared.pending()==2,"Exact view destruction did not release one native slot");
        a.surface.ready.run();check(shared.pending()==2,"An old callback released its replacement's budget");
        b.surface.ready.run();c.capture.cancel();check(shared.pending()==0,"Shared readiness budget failed to drain");

        MdoBrowserCapture.Budget privateImages=new MdoBrowserCapture.Budget(2);Case firstImage=new Case(privateImages),secondImage=new Case(privateImages),thirdImage=new Case(privateImages);
        firstImage.begin();firstImage.ready();firstImage.encode();secondImage.begin();secondImage.ready();secondImage.encode();
        refused("capture_resource_limit",thirdImage::begin);check(privateImages.pending()==2,"Post guards escaped the shared private-image quota");
        firstImage.guards.get(1).complete(null);thirdImage.begin();thirdImage.ready();thirdImage.finish();
        secondImage.capture.cancel();firstImage.clean();secondImage.clean();thirdImage.clean();
        Case removeFailure=new Case();removeFailure.begin();removeFailure.ready();removeFailure.encode();removeFailure.clock.removeFailure=true;
        removeFailure.guards.get(1).complete(null);removeFailure.failed("capture_timer_unavailable");
        for(byte value:removeFailure.surface.encoder.data)check(value==0,"Failed deadline removal retained source bytes");removeFailure.clock.removeFailure=false;removeFailure.clean();

        Case encoding=new Case();encoding.begin();encoding.ready();CountDownLatch entered=new CountDownLatch(1),exit=new CountDownLatch(1);
        encoding.surface.encoder.during=()->{entered.countDown();try{exit.await();}catch(InterruptedException error){throw new IllegalStateException(error);}};
        Thread thread=new Thread(encoding.worker.tasks.get(0));thread.setDaemon(true);thread.start();
        check(entered.await(5,TimeUnit.SECONDS),"Encoder never entered its bounded worker gate");encoding.capture.cancel();encoding.capture.viewDestroyed();
        check(encoding.budget.pending()==1&&encoding.surface.encoder.closes==0,"Cancel/destroy recycled an encoder's live bitmap");
        check(encoding.worker.removals.isEmpty(),"Cancellation tried to remove a running encoder's task");
        exit.countDown();thread.join(5000);check(!thread.isAlive(),"Encoder failed to acknowledge its terminal state");encoding.clock.advance(0);encoding.failed("capture_cancelled");
        check(encoding.surface.encoder.closes==1&&encoding.surface.encoder.encodes==1,"Worker ownership was not settled exactly once");encoding.clean();

        Case delayed=new Case();delayed.begin();delayed.ready();delayed.clock.advance(100);delayed.failed("capture_timed_out");delayed.worker.run();delayed.clock.advance(0);
        check(delayed.surface.encoder.encodes==0&&delayed.surface.encoder.closes==1,"Expired queued encoding ran");delayed.clean();
        Case resultChanged=new Case();resultChanged.begin();resultChanged.ready();resultChanged.worker.run();resultChanged.surface.navigation++;resultChanged.clock.advance(0);
        resultChanged.failed("capture_changed");resultChanged.clean();
        Case postFailed=new Case();postFailed.begin();postFailed.ready();postFailed.clock.failAt=2;postFailed.worker.run();
        check(postFailed.budget.pending()==0&&postFailed.surface.encoder.closes==1,"Lost UI reply leaked the finished native bitmap");
        postFailed.clock.advance(100);postFailed.failed("capture_timed_out");postFailed.clean();

        MdoBrowserCapture.Snapshot snapshot=new Surface().snapshot();
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1,1.1})invalid(()->new MdoBrowserCapture.Plan(snapshot,0,0,100,100,value,"png",128,100));
        invalid(()->new MdoBrowserCapture.Plan(snapshot,-33,0,100,100,1,"png",128,100));
        invalid(()->new MdoBrowserCapture.Plan(snapshot,0,0,10000,100,1,"png",128,100));
        invalid(()->new MdoBrowserCapture.Plan(snapshot,0,0,100,100,1,"webp",128,100));
        invalid(()->new MdoBrowserCapture.Plan(snapshot,0,0,100,100,1,"png",262145,100));
        invalid(()->new MdoBrowserCapture.Plan(snapshot,0,0,100,100,1,"png",128,10001));
        MdoBrowserCapture.Plan large=new MdoBrowserCapture.Plan(new MdoBrowserCapture.Snapshot(DOC,7,16384,16384,0,0,2),0,0,16384,16384,1,"jpeg",262144,10000);
        check(large.outputWidth<=4096&&large.outputHeight<=4096&&(long)large.outputWidth*large.outputHeight<=4194304,"Raster allocation exceeded its hard budget");
        MdoBrowserCaptureBuffer bytes=new MdoBrowserCaptureBuffer(4);bytes.write(new byte[]{1,2,3},0,3);bytes.write(4);
        check(Arrays.equals(bytes.toByteArray(),new byte[]{1,2,3,4}),"Bounded output changed bytes");
        try{bytes.write(5);throw new AssertionError("Byte limit accepted");}catch(MdoBrowserCapture.Limit expected){checks++;}
        try{bytes.write(new byte[]{1,2},0,2);throw new AssertionError("Block byte limit accepted");}catch(MdoBrowserCapture.Limit expected){checks++;}
        check(bytes.size()==4,"Oversized encoding grew before rejection");
        Case foreignReady=new Case();foreignReady.begin();foreignReady.guards.get(0).complete(null);
        AtomicReference<Throwable> badReady=new AtomicReference<>();Thread callback=new Thread(()->{try{foreignReady.surface.ready.run();}catch(Throwable error){badReady.set(error);}});
        callback.start();callback.join();check(badReady.get() instanceof IllegalStateException,"Foreign native readiness bypassed the UI-thread boundary");
        foreignReady.capture.viewDestroyed();foreignReady.clock.advance(0);foreignReady.failed("capture_closed");
        check(foreignReady.surface.draws==0,"Foreign native readiness drew a bitmap");foreignReady.clean();
        AtomicReference<Throwable> foreign=new AtomicReference<>();Thread other=new Thread(()->{try{success.capture.cancel();}catch(Throwable error){foreign.set(error);}});other.start();other.join();
        check(foreign.get() instanceof IllegalStateException,"Foreign thread mutated UI capture state");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
    }
}
