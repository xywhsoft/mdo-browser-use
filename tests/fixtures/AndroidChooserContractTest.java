package org.xserver.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class AndroidChooserContractTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Chooser check "+checks+" failed");}
    static final class Clock implements MdoBrowserChoosers.Scheduler {
        long now;boolean failAt,queuedFailure,failRemove,failNow;
        final Map<Runnable,Long> tasks=new LinkedHashMap<>();
        public long now(){if(failNow)throw new OutOfMemoryError("Synthetic clock failure");return now;}
        public void at(Runnable task,long time){if(queuedFailure)tasks.put(task,time);if(failAt)throw new IllegalStateException("Synthetic timer loss");tasks.put(task,time);}
        public void remove(Runnable task){if(failRemove)throw new IllegalStateException("Synthetic remove loss");tasks.remove(task);}
    }
    static final class Reply implements MdoBrowserChoosers.Response {
        int calls;boolean fail,oom;Runnable nested;
        public void cancel(){calls++;if(nested!=null)nested.run();if(oom)throw new OutOfMemoryError("Synthetic native result loss");if(fail)throw new IllegalStateException("Synthetic native result loss");}
    }
    static final class Binding {
        final Object view=new Object();final Clock clock=new Clock();final List<Boolean> changes=new ArrayList<>();
        long epoch=1;boolean owned=true,listenerFail;
        final MdoBrowserChoosers core=new MdoBrowserChoosers(view,clock,()->owned,()->epoch,value->{changes.add(value);if(listenerFail)throw new IllegalStateException("Synthetic listener loss");});
        Binding(){core.interception(true);}
        void destroy(){core.close();core.viewDestroyed(view);}
    }
    static void basics(){
        Binding b=new Binding();Reply first=new Reply();b.core.interception(false);
        check(!b.core.open(first,first,0,null,false)&&first.calls==0);b.core.interception(true);
        String[] types={" image/png ","image/png",".txt",".\u6587\u6863",".\ud83e\udd89"};
        check(b.core.open(first,first,1,types,true));MdoBrowserChoosers.Snapshot snap=b.core.snapshot();
        check(snap.id.startsWith("chooser-")&&snap.mode==1&&snap.multiple&&!snap.directory&&snap.capture&&snap.cancelable&&snap.navigation==1);
        check(snap.accept.size()==4&&snap.accept.get(0).equals("image/png")&&snap.accept.get(2).equals(".\u6587\u6863")&&snap.accept.get(3).equals(".\ud83e\udd89"));types[0]="changed";
        check(snap.accept.get(0).equals("image/png"));
        boolean immutable=false;try{snap.accept.add("changed");}catch(UnsupportedOperationException expected){immutable=true;}check(immutable);
        check(b.core.open(first,first,3,null,false)&&b.core.snapshot().id.equals(snap.id)&&first.calls==0);
        Reply second=new Reply();check(b.core.open(second,second,0,null,false)&&second.calls==1);
        check(b.core.open(second,second,0,null,false)&&second.calls==1&&b.core.snapshot().id.equals(snap.id));
        check(!b.core.cancel("another-chooser").ok&&first.calls==0);
        MdoBrowserChoosers.Result result=b.core.cancel(snap.id);
        check(result.ok&&result.cleanupConfirmed&&result.effectMayHaveOccurred&&first.calls==1);
        check(!b.core.cancel(snap.id).ok&&first.calls==1&&b.core.snapshot()==null&&b.clock.tasks.isEmpty());
        check(b.changes.size()==2&&b.changes.get(0)&&!b.changes.get(1));
        b.core.interception(false);check(b.core.open(first,first,0,null,false)&&first.calls==1);b.destroy();
        check(b.core.clean()&&!b.core.open(new Object(),new Reply(),0,null,false));
    }
    static void lifecycle(){
        Binding b=new Binding();Reply first=new Reply();b.core.open(first,first,0,null,false);
        Runnable stale=b.clock.tasks.keySet().iterator().next();String old=b.core.snapshot().id;
        b.epoch++;check(b.core.snapshot()==null&&first.calls==1);
        Reply next=new Reply();b.core.open(next,next,2,null,false);check(!b.core.snapshot().id.equals(old)&&b.core.snapshot().directory);
        stale.run();check(next.calls==0&&b.core.snapshot()!=null);
        b.owned=false;check(b.core.snapshot()==null&&next.calls==1);b.destroy();
        for(int reason=0;reason<3;reason++){
            Binding value=new Binding();Reply response=new Reply();value.core.open(response,response,0,null,false);
            if(reason==0)value.core.interception(false);else if(reason==1)value.core.cancel();else value.core.close();
            check(response.calls==1&&value.core.snapshot()==null&&value.clock.tasks.isEmpty());
            value.destroy();check(response.calls==1);
        }
    }
    static void deadline(){
        Binding b=new Binding();Reply reply=new Reply();b.core.open(reply,reply,0,null,false);
        Runnable timer=b.clock.tasks.keySet().iterator().next();b.clock.tasks.clear();b.clock.now=119999;timer.run();
        check(reply.calls==0&&b.clock.tasks.size()==1&&b.clock.tasks.get(timer)==120000);
        b.clock.now=120000;timer.run();timer.run();check(reply.calls==1&&b.core.snapshot()==null);b.destroy();
        for(int fault=0;fault<3;fault++){
            Binding value=new Binding();Reply response=new Reply();value.clock.failAt=true;value.clock.queuedFailure=fault>0;value.clock.failRemove=fault==2;
            check(value.core.open(response,response,0,null,false));check(response.calls==1&&value.core.snapshot()==null&&value.core.clean());
            for(Runnable task:new ArrayList<>(value.clock.tasks.keySet()))task.run();check(response.calls==1);value.destroy();
        }
    }
    static void uncertain(){
        for(boolean oom:new boolean[]{false,true}){
            Binding b=new Binding();Reply reply=new Reply();reply.fail=!oom;reply.oom=oom;b.core.open(reply,reply,0,null,false);
            String id=b.core.snapshot().id;MdoBrowserChoosers.Result result=b.core.cancel(id);
            check(!result.ok&&result.code.equals("chooser_response_uncertain")&&result.effectMayHaveOccurred&&!result.cleanupConfirmed);
            check(!b.core.clean()&&!b.core.snapshot().cancelable&&reply.calls==1);
            b.core.cancel(id);b.core.cancel();b.core.close();check(reply.calls==1);
            check(b.core.open(reply,reply,0,null,false)&&reply.calls==1);
            Reply other=new Reply();check(!b.core.open(other,other,0,null,false)&&other.calls==0);
            boolean wrong=false;try{b.core.viewDestroyed(new Object());}catch(IllegalStateException expected){wrong=true;}check(wrong&&!b.core.clean());
            b.core.viewDestroyed(b.view);check(b.core.clean()&&b.core.snapshot()==null&&b.clock.tasks.isEmpty());
        }
        Binding nested=new Binding();Reply response=new Reply();nested.core.open(response,response,0,null,false);String id=nested.core.snapshot().id;
        response.nested=()->{check(!nested.core.cancel(id).ok);check(nested.core.snapshot()!=null&&!nested.core.snapshot().cancelable);nested.destroy();};response.fail=true;
        MdoBrowserChoosers.Result result=nested.core.cancel(id);check(!result.ok&&result.cleanupConfirmed&&response.calls==1&&nested.core.clean());
    }
    static void readiness(){
        Binding b=new Binding();Reply reply=new Reply();check(b.core.ready());b.core.open(reply,reply,0,null,false);
        check(!b.core.ready());reply.nested=()->check(!b.core.ready());b.clock.now=120000;
        check(b.core.ready()&&reply.calls==1);b.destroy();
        Binding loss=new Binding();Reply second=new Reply();loss.core.open(second,second,0,null,false);
        // Refresh may retire the native callback but fail to clear its document
        // blocker. A pre-refresh cleanliness check must not certify readiness.
        loss.listenerFail=true;loss.clock.now=120000;
        check(!loss.core.ready()&&second.calls==1&&loss.core.snapshot()==null&&!loss.core.clean());
        loss.destroy();check(loss.core.ready());
    }
    static void metadata(){
        for(int fault=0;fault<9;fault++){
            Binding b=new Binding();Reply reply=new Reply();String[] types=null;int mode=0;
            if(fault==0)mode=4;if(fault==1)types=new String[17];if(fault==2)types=new String[]{"text/\u0000bad"};
            if(fault==3)types=new String[]{new String(new char[257]).replace('\0','a')};
            if(fault==4)b.epoch=0;if(fault==5)b.clock.now=Long.MAX_VALUE-10;if(fault==6)b.clock.failNow=true;
            if(fault==7)types=new String[]{new String(new char[90]).replace('\0','\u6587')};
            if(fault==8)types=new String[]{".\ud800"};
            check(b.core.open(reply,reply,mode,types,false));check(reply.calls==1&&b.core.snapshot()==null&&b.core.clean());b.destroy();
        }
        Binding listener=new Binding();listener.listenerFail=true;Reply reply=new Reply();listener.core.open(reply,reply,0,null,false);
        check(reply.calls==1&&!listener.core.clean());listener.destroy();check(listener.core.clean());
        Binding reject=new Binding();Object token=new Object();check(reject.core.rejectOnce(token));reject.core.rejectionFailed();
        check(!reject.core.rejectOnce(token)&&!reject.core.clean());reject.destroy();check(reject.core.clean());
    }
    static void retention(){
        Binding b=new Binding();List<Reply> retained=new ArrayList<>();
        for(int index=0;index<128;index++){
            Reply reply=new Reply();retained.add(reply);check(b.core.open(reply,reply,0,null,false));
            check(b.core.cancel(b.core.snapshot().id).ok&&reply.calls==1);
        }
        Reply overflow=new Reply();check(!b.core.open(overflow,overflow,0,null,false)&&overflow.calls==0&&!b.core.clean());
        for(Reply reply:retained)check(b.core.open(reply,reply,0,null,false)&&reply.calls==1);
        b.destroy();check(b.core.clean());
    }
    static void reentrantOwnership() throws Exception {
        Object view=new Object();Clock clock=new Clock();AtomicReference<MdoBrowserChoosers> holder=new AtomicReference<>();
        MdoBrowserChoosers core=new MdoBrowserChoosers(view,clock,()->{holder.get().close();holder.get().viewDestroyed(view);return true;},()->1,value->{});
        holder.set(core);core.interception(true);Reply reply=new Reply();check(core.open(reply,reply,0,null,false));check(reply.calls==0&&core.clean()&&core.snapshot()==null&&clock.tasks.isEmpty());
        Binding binding=new Binding();AtomicReference<Throwable> error=new AtomicReference<>();Thread wrong=new Thread(()->{try{binding.core.snapshot();}catch(Throwable value){error.set(value);}});
        wrong.start();wrong.join();check(error.get() instanceof IllegalStateException);binding.destroy();
    }
    public static void main(String[] args)throws Exception{
        basics();lifecycle();deadline();uncertain();readiness();metadata();retention();reentrantOwnership();
        System.out.println("{\"passed\":true,\"checks\":"+checks+"}");
    }
}
