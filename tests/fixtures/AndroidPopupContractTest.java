package org.xserver.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AndroidPopupContractTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Popup check "+checks);}
    static void denied(Runnable action){try{action.run();throw new AssertionError("Expected popup rejection");}catch(IllegalStateException expected){checks++;}}
    static final class Clock implements MdoBrowserPopups.Scheduler {
        long now;boolean fail;final Map<Runnable,Long> jobs=new LinkedHashMap<>();
        public long now(){return now;}
        public void at(Runnable task,long time){if(fail)throw new IllegalStateException("Scheduler stopped");jobs.put(task,time);}
        public void remove(Runnable task){jobs.remove(task);}
        void advance(long time){now=time;for(Runnable task:new ArrayList<>(jobs.keySet()))if(jobs.containsKey(task)&&jobs.get(task)<=time){jobs.remove(task);task.run();}}
    }
    static final class Native implements MdoBrowserPopups.Transport<Object> {
        final Object token=new Object();Object child;int sends;Runnable consumed;boolean fail,oom;
        public Object token(){return token;}
        public void send(Object child,Runnable consumed){this.child=child;this.consumed=consumed;sends++;if(oom)throw new OutOfMemoryError();if(fail)throw new IllegalStateException("Dispatch lost");}
        void ack(){if(consumed!=null)consumed.run();}
    }
    static final class Harness {
        final Object view=new Object();final Clock clock=new Clock();final MdoBrowserPopups.Budget budget;
        final List<Boolean> blocked=new ArrayList<>();final List<String> results=new ArrayList<>();
        long epoch=1;boolean owned=true;final MdoBrowserPopups<Object> core;
        Harness(){this(new MdoBrowserPopups.Budget(8));}
        Harness(MdoBrowserPopups.Budget budget){this.budget=budget;core=new MdoBrowserPopups<>(view,clock,budget,()->owned,()->epoch,blocked::add);}
        String id(){MdoBrowserPopups.Snapshot row=core.snapshot();return row==null?null:row.id;}
        void adopt(String id,Object child){core.adopt(id,child,(ok,code,may)->results.add(ok+":"+code+":"+may));}
    }
    public static void main(String[] args)throws Exception {
        Harness a=new Harness();Native first=new Native();check(a.core.open(first,true));String id=a.id();
        check(id.length()==36&&a.budget.used()==1&&a.core.busy()&&a.core.clean());
        check(a.core.snapshot().epoch==1&&a.core.snapshot().userInitiated);check(a.core.open(first,false));check(id.equals(a.id()));
        Native second=new Native();check(!a.core.open(second,false)&&second.sends==0);
        denied(()->a.adopt("wrong",new Object()));check(first.sends==0);
        denied(()->a.adopt(id,a.view));check(first.sends==0);
        Object child=new Object();a.adopt(id,child);check(first.sends==1&&first.child==child&&a.results.isEmpty());
        check(a.id()==null&&a.core.busy()&&a.budget.used()==1);denied(()->a.adopt(id,child));check(!a.core.dismiss(id));
        first.ack();check(a.results.equals(java.util.Arrays.asList("true:null:true")));check(!a.core.busy()&&a.core.clean()&&a.budget.used()==0);
        first.ack();check(a.results.size()==1&&a.budget.used()==0);check(a.blocked.equals(java.util.Arrays.asList(true,false)));
        check(a.core.open(second,false));String secondId=a.id();check(!secondId.equals(id));first.ack();check(secondId.equals(a.id()));
        check(a.core.dismiss(secondId)&&second.sends==1&&second.child==null&&a.budget.used()==1);check(!a.core.dismiss(secondId));
        second.ack();check(!a.core.busy()&&a.budget.used()==0&&a.clock.jobs.isEmpty());

        Harness navigation=new Harness();Native old=new Native();check(navigation.core.open(old,false));navigation.epoch++;
        check(navigation.id()==null&&old.sends==1&&old.child==null);old.ack();check(navigation.core.clean());
        Native replacement=new Native();check(navigation.core.open(replacement,false));String next=navigation.id();old.ack();check(next.equals(navigation.id()));
        navigation.core.cancel();replacement.ack();check(navigation.budget.used()==0);

        Harness changed=new Harness();Native sent=new Native();check(changed.core.open(sent,false));changed.adopt(changed.id(),new Object());
        changed.epoch++;changed.core.cancel();sent.ack();check(changed.results.equals(java.util.Arrays.asList("false:popup_source_changed:true")));
        check(changed.budget.used()==0&&sent.sends==1);
        Harness hidden=new Harness();Native hiddenNative=new Native();check(hidden.core.open(hiddenNative,false));hidden.owned=false;
        check(hidden.id()==null&&hiddenNative.sends==1&&hiddenNative.child==null);hiddenNative.ack();check(hidden.budget.used()==0);
        check(!hidden.core.open(new Native(),false));

        Harness expired=new Harness();Native timeout=new Native();check(expired.core.open(timeout,false));expired.clock.now=15001;
        check(expired.id()==null&&timeout.sends==1&&timeout.child==null);check(expired.core.busy()&&expired.budget.used()==1);
        expired.clock.advance(20002);check(!expired.core.clean()&&expired.budget.used()==1);check(!expired.core.open(new Native(),false));
        timeout.ack();check(expired.budget.used()==0&&!expired.core.clean());expired.core.viewDestroyed(new Object());check(!expired.core.clean());
        expired.core.viewDestroyed(expired.view);check(expired.core.clean()&&!expired.core.busy());

        Harness lost=new Harness();Native uncertain=new Native();check(lost.core.open(uncertain,false));lost.adopt(lost.id(),new Object());
        lost.clock.advance(5000);check(lost.results.equals(java.util.Arrays.asList("false:popup_transport_unconfirmed:true")));
        check(lost.budget.used()==1&&!lost.core.clean());lost.core.close();check(uncertain.sends==1&&lost.budget.used()==1);
        lost.core.viewDestroyed(lost.view);check(lost.budget.used()==0&&lost.core.clean());uncertain.ack();check(lost.results.size()==1);

        for(boolean oom:new boolean[]{false,true}){
            Harness failure=new Harness();Native nativeFailure=new Native();nativeFailure.fail=!oom;nativeFailure.oom=oom;
            check(failure.core.open(nativeFailure,false));failure.adopt(failure.id(),new Object());
            check(nativeFailure.sends==1&&failure.results.equals(java.util.Arrays.asList("false:popup_transport_unconfirmed:true")));
            check(!failure.core.clean()&&failure.budget.used()==1);failure.core.cancel();failure.core.close();check(nativeFailure.sends==1);
            nativeFailure.ack();check(failure.budget.used()==0&&failure.results.size()==1);
        }
        Harness scheduler=new Harness();scheduler.clock.fail=true;Native rejected=new Native();check(scheduler.core.open(rejected,false));
        check(rejected.sends==1&&rejected.child==null&&!scheduler.core.clean()&&scheduler.budget.used()==1);
        rejected.ack();check(scheduler.budget.used()==0);

        MdoBrowserPopups.Budget budget=new MdoBrowserPopups.Budget(2);Harness one=new Harness(budget),two=new Harness(budget),three=new Harness(budget);
        Native n1=new Native(),n2=new Native(),n3=new Native();check(one.core.open(n1,false));check(two.core.open(n2,false));check(!three.core.open(n3,false));
        one.core.close();check(n1.sends==1&&n1.child==null&&budget.used()==2);n1.ack();check(three.core.open(n3,false)&&budget.used()==2);
        one.core.viewDestroyed(one.view);check(budget.used()==2);two.core.viewDestroyed(two.view);three.core.viewDestroyed(three.view);check(budget.used()==0);
        check(n2.sends==0&&n3.sends==0);check(!one.core.open(new Native(),false));
        final boolean[] foreign={false};Thread other=new Thread(()->{try{a.core.snapshot();}catch(IllegalStateException expected){foreign[0]=true;}});other.start();other.join();check(foreign[0]);
        System.out.println("{\"passed\":true,\"checks\":"+checks+"}");
    }
}
