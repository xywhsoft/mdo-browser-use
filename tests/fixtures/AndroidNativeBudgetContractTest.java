package org.xserver.android;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** A client deadline is not proof that the provider released its callback. */
public final class AndroidNativeBudgetContractTest {
    static Class<?> remote;static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Object invoke(String name,Class<?>[] types,Object... args)throws Exception{return remote.getMethod(name,types).invoke(null,args);}
    static final class Clock implements MdoBrowserWorld.Scheduler {
        long time;Error postError;Map<Runnable,Long> tasks=new HashMap<>();public long now(){return time;}
        public void post(Runnable task,long delay){tasks.put(task,time+delay);if(postError!=null)throw postError;}public void remove(Runnable task){tasks.remove(task);}
        void expire(){time+=101;for(Runnable task:new ArrayList<>(tasks.keySet()))task.run();}
    }
    public static void main(String[] args)throws Exception{
        try(URLClassLoader loader=new URLClassLoader(new URL[]{Paths.get(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            remote=Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil",true,loader);
            Object view=invoke("newView",new Class<?>[]{});Clock clock=new Clock();
            MdoBrowserWorld.Peer[] peer={null};int[] completed={0};
            MdoBrowserWorld world=MdoBrowserWorld.open(view,loader,clock,new MdoBrowserWorld.Receiver(){
                public void message(MdoBrowserWorld.Peer value,String text){peer[0]=value;}
                public void fault(String code){throw new AssertionError(code);}
            });
            world.install("fixed fixture");int index=(Integer)invoke("newPeer",new Class<?>[]{});
            invoke("emit",new Class<?>[]{Object.class,boolean.class,String.class,int.class},view,true,"hello",index);
            int before=(Integer)invoke("calls",new Class<?>[]{String.class},"peer:executeJavaScript");
            OutOfMemoryError exhausted=new OutOfMemoryError("Synthetic timer allocation failure");clock.postError=exhausted;
            try{peer[0].evaluate("must not dispatch",100,result->{throw new AssertionError("Failed admission completed");});throw new AssertionError("OOM swallowed");}
            catch(OutOfMemoryError expected){check(expected==exhausted,"Timer admission replaced the original OOM");}
            clock.postError=null;
            check(world.pending()==0&&world.outstanding()==0&&world.sharedOutstanding()==0&&clock.tasks.isEmpty(),"Timer OOM leaked reservations or tasks");
            check((Integer)invoke("calls",new Class<?>[]{String.class},"peer:executeJavaScript")==before,"Timer OOM dispatched native work");
            invoke("mode",new Class<?>[]{String.class},"defer");
            int accepted=0;String refused=null;
            try{
                for(int count=0;count<17;count++){
                    try{peer[0].evaluate("fixed",100,result->{if(result.ok)throw new AssertionError();completed[0]++;});accepted++;}
                    catch(MdoBrowserWorld.Failure failure){refused=failure.code;break;}
                    clock.expire();
                }
                check(accepted==16&&"world_job_limit".equals(refused)&&completed[0]==16&&world.pending()==0,"Client timeouts released unfinished native reservations");
                check(world.outstanding()==16&&world.sharedOutstanding()==16,"Outstanding native diagnostics are incorrect");
                world.close();check(!world.cleanupConfirmed(),"Closing the listener fabricated native completion");
                try{world.viewDestroyed(invoke("newView",new Class<?>[]{}));throw new AssertionError();}
                catch(IllegalStateException expected){check(world.outstanding()==16,"Foreign view teardown released reservations");}
                world.viewDestroyed(view);check(world.cleanupConfirmed()&&world.sharedOutstanding()==0,"Exact teardown did not release all reservations");
                invoke("late",new Class<?>[]{});check(completed[0]==16&&world.sharedOutstanding()==0,"Late callbacks replied twice or decremented another reservation");
            }finally{world.close();world.viewDestroyed(view);}
            MdoBrowserWorld.Budget shared=new MdoBrowserWorld.Budget(32);
            MdoBrowserWorld[] worlds=new MdoBrowserWorld[3];Object[] views=new Object[3];MdoBrowserWorld.Peer[] peers=new MdoBrowserWorld.Peer[3];
            Clock sharedClock=new Clock();int[] replies={0};
            for(int n=0;n<3;n++){
                final int slot=n;views[n]=invoke("newView",new Class<?>[]{});
                worlds[n]=MdoBrowserWorld.open(views[n],loader,sharedClock,new MdoBrowserWorld.Receiver(){
                    public void message(MdoBrowserWorld.Peer peer,String text){peers[slot]=peer;}
                    public void fault(String code){throw new AssertionError(code);}
                },shared);worlds[n].install("fixed fixture");int p=(Integer)invoke("newPeer",new Class<?>[]{});
                invoke("emit",new Class<?>[]{Object.class,boolean.class,String.class,int.class},views[n],true,"hello",p);
            }
            for(int n=0;n<2;n++)for(int call=0;call<16;call++)peers[n].evaluate("fixed",100,result->replies[0]++);
            check(shared.used()==32,"Shared window budget did not count all native worlds");
            worlds[0].cancelPending("world_cancelled");worlds[1].cancelPending("world_cancelled");
            check(replies[0]==32&&shared.used()==32,"Client cancellation released provider work");
            try{peers[2].evaluate("overflow",100,result->{});throw new AssertionError();}
            catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("world_shared_job_limit"),"Shared budget did not reject before dispatch");}
            worlds[0].close();worlds[0].viewDestroyed(views[0]);
            check(shared.used()==16,"Native view teardown affected another world");
            peers[2].evaluate("recovered",100,result->replies[0]++);check(shared.used()==17,"Released native capacity was not reusable");
            invoke("late",new Class<?>[]{});check(replies[0]==33&&shared.used()==0,"Late original callbacks released replacement work or replayed completion");
            for(int n=0;n<3;n++){worlds[n].close();worlds[n].viewDestroyed(views[n]);check(worlds[n].cleanupConfirmed(),"Native world did not drain");}
            System.out.println("{\"passed\":true,\"checks\":"+checks+",\"native_dispatches\":16,\"client_pending\":0,\"shared_limit\":32,\"native_outstanding\":0,\"framework_verified\":false}");
        }
    }
}
