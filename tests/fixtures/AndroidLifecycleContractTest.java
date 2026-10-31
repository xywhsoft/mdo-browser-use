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
    static void freshChildren()throws Exception {
        Case c=new Case();View parent=new View(),ordinary=new View(),child=new View();
        c.views.put("root",parent);c.views.put("ordinary",ordinary);
        rejects(()->c.views.putPopup("ordinary-reused",parent,ordinary));
        rejects(()->c.views.putPopup("self",parent,parent));
        rejects(()->c.views.putPopup("foreign",new View(),new View()));
        MdoBrowserLifecycle.Fresh<View> birth=c.views.putPopup("child",parent,child);
        check(birth.current()&&!birth.destroyed(),"Fresh child has no original lifetime");
        rejects(()->birth.attached(parent));rejects(()->birth.claim(new View()));
        check(birth.claim(parent)==child&&birth.attached(parent)==child,"Birth did not claim exact child");
        rejects(()->birth.claim(parent));rejects(()->c.views.viewDestroyed(child));
        c.views.navigation("child",child);
        check(birth.current()&&birth.attached(parent)==child,"Legitimate native navigation invalidated adopted child");
        check(c.views.remove("child")==child&&!birth.current()&&!birth.destroyed(),"Removal fabricated native destruction");
        check(c.views.size()==3&&c.views.values().stream().anyMatch(view->view==child),"Retired child disappeared from native inventory");
        rejects(()->c.views.checkNew("child"));
        c.views.viewDestroyed(new View());check(!birth.destroyed(),"Value-equal callback released original child");
        c.views.viewDestroyed(child);check(birth.destroyed()&&c.views.size()==2,"Exact destruction did not release child capacity");
        View replacement=new View();c.views.put("child",replacement);c.views.viewDestroyed(child);
        check(c.views.owns("child",replacement),"Late destruction invalidated replacement");
        rejects(()->c.views.putPopup("reused",parent,child));
        c.views.close();

        for(String reason:new String[]{"navigation","manual","visibility","background","parent-navigation"}){
            Case lifetime=new Case();View root=new View(),popup=new View();lifetime.views.put("root",root);
            MdoBrowserLifecycle.Fresh<View> fresh=lifetime.views.putPopup("popup",root,popup);
            switch(reason){
                case "navigation":lifetime.views.navigation("popup",popup);break;
                case "manual":lifetime.views.manual("popup",popup,true);lifetime.views.manual("popup",popup,false);break;
                case "visibility":lifetime.views.visibility("popup",popup,false);lifetime.views.visibility("popup",popup,true);break;
                case "background":lifetime.views.background(true);lifetime.views.background(false);break;
                default:lifetime.views.navigation("root",root);
            }
            rejects(()->fresh.claim(root));
            check(!fresh.destroyed(),"Invalidation fabricated destruction");
            lifetime.views.close();check(lifetime.views.size()==1&&lifetime.views.values().get(0)==popup,"Shutdown lost unconfirmed native child");
            lifetime.views.viewDestroyed(popup);check(fresh.destroyed()&&lifetime.views.size()==0,"Shutdown did not accept exact final receipt");
        }
        Case capacity=new Case();View root=new View();capacity.views.put("root",root);
        List<View> children=new ArrayList<>();
        for(int i=0;i<7;i++){View popup=new View();children.add(popup);capacity.views.putPopup("popup"+i,root,popup);capacity.views.remove("popup"+i);}
        rejects(()->capacity.views.checkNew("overflow"));rejects(()->capacity.views.putPopup("overflow",root,new View()));
        capacity.views.viewDestroyed(children.get(0));capacity.views.putPopup("replacement",root,new View());
        check(capacity.views.size()==8,"Unconfirmed native children escaped capacity limit");
        List<View> owned=capacity.views.values();capacity.views.close();for(View view:owned)capacity.views.viewDestroyed(view);
        check(capacity.views.size()==0,"Bulk native acknowledgement leaked child capacity");

        Case reentrant=new Case();View owner=new View(),held=new View();reentrant.views.put("root",owner);
        MdoBrowserLifecycle.Fresh<View> pending=reentrant.views.putPopup("popup",owner,held);pending.claim(owner);
        boolean[] reserved={false};reentrant.views.input("popup",held).tap(1,1,result->{
            try{reentrant.views.put("popup",new View());}catch(IllegalStateException expected){reserved[0]=true;}
        });reentrant.clock.down();reentrant.views.remove("popup");
        check(reserved[0]&&!pending.destroyed()&&reentrant.views.size()==2,"Reentrant input callback stole pending child's identity");
        reentrant.views.viewDestroyed(held);reentrant.views.put("popup",new View());reentrant.views.close();

        Case dirty=new Case();View origin=new View(),unclean=new View();dirty.views.put("root",origin);
        MdoBrowserLifecycle.Fresh<View> retired=dirty.views.putPopup("popup",origin,unclean);
        dirty.views.input("popup",unclean).tap(1,1,result->{});dirty.clock.down();unclean.clean=false;dirty.views.remove("popup");dirty.views.viewDestroyed(unclean);
        check(retired.destroyed()&&!dirty.views.cleanupConfirmed(),"Native destruction masked independent input cleanup failure");dirty.views.close();

        Case cross=new Case();View opener=new View(),popup=new View();cross.views.put("root",opener);
        MdoBrowserLifecycle.Fresh<View> fresh=cross.views.putPopup("popup",opener,popup);boolean[] denied={false};
        Thread worker=new Thread(()->{try{fresh.claim(opener);}catch(IllegalStateException expected){denied[0]=true;}check(fresh.current()&&!fresh.destroyed(),"Data-only worker receipt changed");});
        worker.start();worker.join();check(denied[0]&&fresh.claim(opener)==popup,"Worker consumed UI birth token");
        cross.views.close();cross.views.viewDestroyed(popup);
    }
    public static void main(String[] args)throws Exception {
        freshChildren();
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
        Case scaled=new Case();View scaledView=new View();scaled.views.put("tab",scaledView);
        check(Double.isNaN(scaled.views.scale("tab",scaledView)),"Unknown native scale was guessed");
        check(!scaled.views.inputBusy("tab",scaledView)&&scaled.views.inputClean("tab",scaledView),"Fresh input diagnostics are incorrect");
        scaled.views.scaleChanged("tab",scaledView,2);check(scaled.views.scale("tab",scaledView)==2,"Native scale callback was not retained");
        scaled.press(scaledView);scaled.views.scaleChanged("tab",scaledView,2);
        check(scaled.views.inputBusy("tab",scaledView)&&!scaled.views.inputClean("tab",scaledView),"Held input diagnostics fabricated cleanup");
        check(scaled.results.isEmpty(),"Identical scale callback cancelled unchanged input");
        scaled.views.scaleChanged("tab",scaledView,3);scaled.terminated("input_viewport_changed");
        check(scaledView.events.equals(Arrays.asList(0,3)),"Native zoom left held input");
        scaled.views.remove("tab");View zoomReplacement=new View();scaled.views.put("tab",zoomReplacement);
        check(!scaled.views.inputBusy("tab",scaledView)&&!scaled.views.inputClean("tab",scaledView),"Old diagnostics referred to replacement input");
        scaled.views.scaleChanged("tab",scaledView,9);check(Double.isNaN(scaled.views.scale("tab",zoomReplacement)),"Old view scale changed a replacement");
        scaled.views.scaleChanged("tab",zoomReplacement,2);scaled.views.scaleChanged("tab",zoomReplacement,Double.NaN);
        check(Double.isNaN(scaled.views.scale("tab",zoomReplacement)),"Invalid scale remained usable");scaled.views.close();
        for(String reason:new String[]{"input_dialog_open","file_chooser_open","popup_pending"}){
            Case modal=new Case();View modalView=new View();modal.views.put("tab",modalView);modal.press(modalView);
            rejects(()->modal.input(modalView).invalidate("unknown_modal"));
            check(modal.results.isEmpty()&&modal.input(modalView).busy(),"Invalid reason partially cancelled input");
            modal.input(modalView).invalidate(reason);modal.terminated(reason);
            check(modalView.events.equals(Arrays.asList(0,3)),"Native modal left a held pointer");
            modal.input(modalView).invalidate(reason);check(modal.results.size()==1,"Modal cancellation completed twice");
            modal.clock.time+=1000;modal.clock.down();check(modalView.events.size()==2,"Cancelled input replayed after modal");
            modal.views.close();
        }
        final boolean[] thread={false};Thread other=new Thread(()->{try{identity.views.get("tab");}catch(IllegalStateException expected){thread[0]=true;}});
        other.start();other.join();check(thread[0],"Cross-thread lifecycle admitted");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"android_device_verified\":false}");
    }
}
