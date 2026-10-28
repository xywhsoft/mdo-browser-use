package org.xserver.android;

import java.util.*;

/** Actual lifetime registry + actual input sequencer; synthetic native surface.
 * No Android framework behavior is asserted by this JVM gate. */
public final class AndroidLifecycleContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Throwing {void run()throws Exception;}
    static void rejects(Throwing action){try{action.run();throw new AssertionError("Invalid lifetime operation admitted");}
        catch(IllegalStateException|IllegalArgumentException expected){checks++;}catch(Exception error){throw new AssertionError(error);}}
    static final class View {
        final List<Integer> events=new ArrayList<>();boolean clean=true;
        // Value equality must never stand in for the exact native instance.
        @Override public boolean equals(Object other){return other instanceof View;}
        @Override public int hashCode(){return 1;}
    }
    static final class Clock implements MdoBrowserInput.Scheduler {
        long time=100;final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public long now(){return time;}public void post(Runnable task,long delay){tasks.put(task,time+delay);}
        public void remove(Runnable task){tasks.remove(task);}
        void down(){for(Runnable task:new ArrayList<>(tasks.keySet()))if(tasks.get(task)<=time){tasks.remove(task);task.run();}}
    }
    static final class Case {
        final Clock clock=new Clock();final List<MdoBrowserInput.Result> results=new ArrayList<>();
        final MdoBrowserLifecycle<View> views=new MdoBrowserLifecycle<>((view,epoch,admitted)->new MdoBrowserInput(new MdoBrowserInput.Target(){
            public long document(){return epoch.getAsLong();}public int width(){return 300;}public int height(){return 200;}
            public boolean interactive(){return admitted.getAsBoolean();}public boolean focused(){return true;}
            public boolean touch(int action,long down,long time,float x,float y){view.events.add(action);return action!=MdoBrowserInput.CANCEL||view.clean;}
            public boolean key(boolean down,long started,long time,String name,int modifiers){return true;}
            public boolean text(String value){return true;}
        },clock));
        MdoBrowserInput input(View view){return views.input("tab",view);}
        void press(View view)throws Exception{input(view).tap(10,10,results::add);clock.down();check(view.events.equals(Arrays.asList(0)),"Pointer press missing");}
        void terminated(String code){check(results.size()==1&&code.equals(results.get(0).code)&&results.get(0).effectMayHaveOccurred,"Input lifetime receipt");
            check(clock.tasks.isEmpty(),"Lifetime retained input timer");}
    }
    public static void main(String[] args)throws Exception {
        Case replacement=new Case();View original=new View(),next=new View();replacement.views.put("tab",original);replacement.press(original);
        check(replacement.views.epoch("tab",original)==1,"Initial epoch");check(replacement.views.remove("tab")==original,"Wrong retired view");
        replacement.terminated("input_closed");check(original.events.equals(Arrays.asList(0,3)),"Old view was not cancelled before retirement");
        replacement.views.put("tab",next);check(!replacement.views.owns("tab",original)&&replacement.views.owns("tab",next),"Tab identity rebound by value equality");
        check(!replacement.views.navigation("tab",original)&&replacement.views.epoch("tab",next)==1,"Late old navigation changed replacement");
        replacement.views.visibility("tab",original,false);replacement.views.manual("tab",original,true);replacement.views.stop("tab",original);
        check(next.events.isEmpty()&&replacement.views.get("tab")==next,"Old lifecycle action touched replacement");
        rejects(()->replacement.views.input("tab",original));replacement.input(next).tap(2,2,result->{});replacement.clock.down();
        check(next.events.equals(Arrays.asList(0)),"Replacement was blocked by old visibility/manual state");replacement.views.close();
        check(next.events.equals(Arrays.asList(0,3))&&replacement.views.size()==0&&replacement.views.cleanupConfirmed(),"Replacement close did not drain");

        for(String transition:new String[]{"navigation","hidden","takeover","stop","background"}){
            Case sample=new Case();View view=new View();sample.views.put("tab",view);sample.press(view);
            if(transition.equals("navigation")){check(sample.views.navigation("tab",view),"Live navigation rejected");check(sample.views.epoch("tab",view)==2,"Navigation epoch did not advance");}
            else if(transition.equals("hidden"))sample.views.visibility("tab",view,false);
            else if(transition.equals("takeover"))sample.views.manual("tab",view,true);
            else if(transition.equals("background"))sample.views.background(true);
            else sample.views.stop("tab",view);
            sample.terminated(transition.equals("navigation")?"frame_changed":transition.equals("hidden")||transition.equals("background")?"input_view_unavailable":transition.equals("takeover")?"user_taken_control":"input_cancelled");
            check(view.events.equals(Arrays.asList(0,3))&&sample.views.cleanupConfirmed(),"Transition left a held pointer");
            if(transition.equals("hidden")||transition.equals("takeover")||transition.equals("background")){
                try{sample.input(view).tap(0,0,result->{});throw new AssertionError("Inactive view admitted input");}
                catch(MdoBrowserInput.Failure failure){check(failure.code.equals("input_view_unavailable"),"Inactive input failure");}
                sample.views.visibility("tab",view,true);sample.views.manual("tab",view,false);sample.views.background(false);
                sample.input(view).tap(0,0,result->{});check(view.events.size()==2,"Restore replayed input");sample.clock.down();check(view.events.size()==3,"Restored target did not admit fresh input");
            }
            sample.views.close();check(sample.clock.tasks.isEmpty(),"Destroy retained callbacks");
        }

        Case identity=new Case();View first=new View();identity.views.put("tab",first);rejects(()->identity.views.put("tab",new View()));
        rejects(()->identity.views.put("other",first));rejects(()->identity.views.put("",new View()));rejects(()->identity.views.put("null",null));
        for(int i=1;i<8;i++)identity.views.put("view"+i,new View());rejects(()->identity.views.put("overflow",new View()));
        List<View> snapshot=identity.views.values();snapshot.clear();check(identity.views.size()==8,"Caller mutated owned view inventory");
        identity.views.close();identity.views.close();check(identity.views.values().isEmpty()&&identity.views.cleanupConfirmed(),"Destroy not idempotent");
        rejects(()->identity.views.put("late",new View()));check(identity.views.remove("missing")==null,"Missing close not idempotent");

        Case poison=new Case();View broken=new View();poison.views.put("tab",broken);poison.press(broken);broken.clean=false;poison.views.stop("tab",broken);
        poison.terminated("input_cancelled");check(!poison.views.cleanupConfirmed(),"Unconfirmed cancellation was hidden");rejects(()->poison.input(broken));
        poison.views.remove("tab");View repaired=new View();poison.views.put("tab",repaired);poison.input(repaired).tap(1,1,result->{});poison.clock.down();
        check(repaired.events.size()==1,"Destroyed adapter poison leaked into a replacement");poison.views.close();

        Case callbacks=new Case();View failing=new View(),survivor=new View();callbacks.views.put("tab",failing);callbacks.views.put("other",survivor);
        callbacks.input(failing).tap(0,0,result->{throw new IllegalStateException("Synthetic completion loss");});callbacks.clock.down();
        callbacks.views.input("other",survivor).tap(0,0,result->{});callbacks.clock.down();callbacks.views.close();
        check(callbacks.views.size()==0&&callbacks.clock.tasks.isEmpty()&&survivor.events.equals(Arrays.asList(0,3)),"Throwing completion prevented other owned cleanup");
        check(!callbacks.views.cleanupConfirmed(),"Completion fault was hidden");

        Case disconnected=new Case();View a=new View(),b=new View();disconnected.views.put("tab",a);disconnected.views.put("other",b);
        disconnected.input(a).tap(0,0,disconnected.results::add);disconnected.views.input("other",b).tap(0,0,disconnected.results::add);disconnected.clock.down();
        disconnected.views.stopAll();check(disconnected.results.size()==2&&disconnected.clock.tasks.isEmpty(),"Main channel detachment did not drain all original input");
        check(a.events.equals(Arrays.asList(0,3))&&b.events.equals(Arrays.asList(0,3)),"Channel detachment left a native press");disconnected.views.close();

        Case reentrant=new Case();View old=new View(),newer=new View();reentrant.views.put("tab",old);
        reentrant.input(old).tap(0,0,result->{reentrant.views.remove("tab");reentrant.views.put("tab",newer);});reentrant.clock.down();
        check(!reentrant.views.navigation("tab",old)&&reentrant.views.get("tab")==newer,"Navigation callback mutated a reentrant replacement");reentrant.views.close();
        final boolean[] thread={false};Thread other=new Thread(()->{try{identity.views.get("tab");}catch(IllegalStateException expected){thread[0]=true;}});
        other.start();other.join();check(thread[0],"Cross-thread lifecycle admitted");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"android_device_verified\":false}");
    }
}
