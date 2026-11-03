package org.xserver.android;

/** Actual lifecycle token, synthetic native getter. Android visual callbacks
 * and rendering are qualified separately on the installed device. */
public final class AndroidInitialScaleContractTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Initial scale check "+checks);}
    private static AndroidLifecycleContractTest.Case owner(){return new AndroidLifecycleContractTest.Case();}
    public static void main(String[] args)throws Exception {
        AndroidLifecycleContractTest.Case c=owner();AndroidLifecycleContractTest.View view=new AndroidLifecycleContractTest.View();c.views.put("tab",view);
        MdoBrowserLifecycle.InitialScale<AndroidLifecycleContractTest.View> first=c.views.initialScale("tab",view);
        check(first!=null&&first.forView(view));check(!first.forView(new AndroidLifecycleContractTest.View()));
        check(c.views.initialScale("tab",view)==null&&Double.isNaN(c.views.scale("tab",view)));
        final int[] reads={0};first.complete(exact->{check(exact==view);reads[0]++;return 3.5;});
        check(c.views.scale("tab",view)==3.5&&!first.forView(view));first.complete(exact->{reads[0]++;return 7;});check(reads[0]==1);
        check(c.views.initialScale("tab",view)==null&&view.events.isEmpty());c.views.close();

        for(int mode=0;mode<7;mode++){
            AndroidLifecycleContractTest.Case current=owner();AndroidLifecycleContractTest.View original=new AndroidLifecycleContractTest.View();current.views.put("tab",original);
            MdoBrowserLifecycle.InitialScale<AndroidLifecycleContractTest.View> ticket=current.views.initialScale("tab",original);final int[] samples={0};
            if(mode==0)current.views.scaleChanged("tab",original,2);
            if(mode==1)current.views.navigation("tab",original);
            if(mode==2){current.views.remove("tab");current.views.put("tab",new AndroidLifecycleContractTest.View());}
            if(mode==3)current.views.close();
            if(mode==4)ticket.cancel();
            if(mode==5)current.views.scaleChanged("tab",original,Double.NaN);
            if(mode==6){current.views.remove("tab");}
            ticket.complete(exact->{samples[0]++;return 4;});check(samples[0]==0&&!ticket.forView(original));
            if(mode==0)check(current.views.scale("tab",original)==2);
            current.views.close();
        }
        for(int mode=0;mode<5;mode++){
            AndroidLifecycleContractTest.Case current=owner();AndroidLifecycleContractTest.View original=new AndroidLifecycleContractTest.View();current.views.put("tab",original);
            MdoBrowserLifecycle.InitialScale<AndroidLifecycleContractTest.View> ticket=current.views.initialScale("tab",original);final int variant=mode;
            ticket.complete(exact->{
                if(variant==0)current.views.navigation("tab",original);
                if(variant==1)current.views.scaleChanged("tab",original,2);
                if(variant==2)throw new IllegalStateException("Synthetic getter failure");
                return variant==3?Double.POSITIVE_INFINITY:variant==4?-1:4;
            });
            check(!ticket.forView(original));check(mode==1?current.views.scale("tab",original)==2:Double.isNaN(current.views.scale("tab",original)));
            if(mode!=1){check(current.views.initialScale("tab",original)!=null);}
            current.views.close();
        }
        AndroidLifecycleContractTest.Case isolated=owner();AndroidLifecycleContractTest.View exact=new AndroidLifecycleContractTest.View();isolated.views.put("tab",exact);
        MdoBrowserLifecycle.InitialScale<AndroidLifecycleContractTest.View> ticket=isolated.views.initialScale("tab",exact);final boolean[] rejected={false};
        Thread other=new Thread(()->{try{ticket.forView(exact);}catch(IllegalStateException expected){rejected[0]=true;}});other.start();other.join();check(rejected[0]);
        rejected[0]=false;
        Thread cancelOther=new Thread(()->{try{ticket.cancel();}catch(IllegalStateException expected){rejected[0]=true;}});cancelOther.start();cancelOther.join();check(rejected[0]);
        check(ticket.forView(exact));ticket.complete(v->3.5);check(isolated.views.scale("tab",exact)==3.5);isolated.views.close();
        AndroidLifecycleContractTest.Case burst=owner();AndroidLifecycleContractTest.View burstView=new AndroidLifecycleContractTest.View();burst.views.put("tab",burstView);
        MdoBrowserLifecycle.InitialScale<AndroidLifecycleContractTest.View> pending=burst.views.initialScale("tab",burstView);check(pending.submit(burstView)&&!pending.submit(burstView));
        for(int i=0;i<100;i++){burst.views.navigation("tab",burstView);check(burst.views.initialScale("tab",burstView)==null);}
        MdoBrowserLifecycle.InitialScale<AndroidLifecycleContractTest.View> latest=pending.complete(v->{throw new AssertionError("Stale callback read a new document");});
        check(latest!=null&&latest.forView(burstView)&&latest.submit(burstView));latest.complete(v->3.5);check(burst.views.scale("tab",burstView)==3.5);burst.views.close();
        int weak=0;for(java.lang.reflect.Field field:MdoBrowserLifecycle.InitialScale.class.getDeclaredFields()){
            if(field.getType()==java.lang.ref.WeakReference.class)weak++;
            check(field.getType()!=MdoBrowserLifecycle.class&&field.getType()!=AndroidLifecycleContractTest.View.class);
        }check(weak==2);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
    }
}
