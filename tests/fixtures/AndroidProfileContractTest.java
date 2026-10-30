package org.xserver.android;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Actual profile/world/document code, published boundary signatures in a foreign
 * loader, controlled provider responses. No installed Android profile is used. */
public final class AndroidProfileContractTest {
    private static Class<?> remote;private static int checks;
    private interface Action {void run()throws Exception;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Object invoke(String name,Class<?>[] types,Object...args)throws Exception{return remote.getMethod(name,types).invoke(null,args);}
    private static void mode(String value)throws Exception{invoke("mode",new Class<?>[]{String.class},value);}
    private static int calls(String prefix)throws Exception{return(Integer)invoke("calls",new Class<?>[]{String.class},prefix);}
    private static Object view()throws Exception{return invoke("newView",new Class<?>[]{});}
    private static void rejects(String code,Action action)throws Exception{
        try{action.run();throw new AssertionError("Missing profile rejection: "+code);}
        catch(MdoBrowserWorld.Failure failure){check(code.equals(failure.code),"Wrong profile code: "+failure.code);check(failure.getCause()==null,"Provider details escaped");}
    }
    private static MdoBrowserWorld open(Object view,ClassLoader loader)throws Exception{
        return MdoBrowserWorld.open(view,loader,new AndroidDocumentContractTest.Clock(),new MdoBrowserWorld.Receiver(){
            public void message(MdoBrowserWorld.Peer peer,String text){}
            public void fault(String code){throw new AssertionError("Unexpected native fault: "+code);}
        });
    }
    private static void unconfirmed(ClassLoader loader)throws Exception{
        for(String fault:new String[]{"profile_ignore","profile_set_before_throw","profile_set_after_throw","profile_get_throw",
                "profile_no_handler","profile_null","profile_name_throw","profile_name_null","profile_name_number","profile_mismatch"}){
            Object original=view();MdoBrowserWorld world=open(original,loader);int sets=calls("setProfile"),worlds=calls("getJavaScriptWorld");mode(fault);
            try{
                rejects("browser_profile_unconfirmed",()->world.profile("qa.content"));
                check(calls("setProfile")==sets+1&&calls("getJavaScriptWorld")==worlds,"Failed profile installed a world or replayed the setter");
                rejects("browser_profile_unconfirmed",()->world.install("must not install"));
                rejects("browser_profile_unconfirmed",()->world.profile("qa.content"));
                rejects("browser_profile_unconfirmed",()->world.profile("qa.other"));
                world.close();world.close();check(!world.cleanupConfirmed(),"Unconfirmed profile was reported clean by close");
                try{world.viewDestroyed(view());throw new AssertionError("Foreign destruction accepted");}catch(IllegalStateException expected){check(true,"exact owner");}
                check(!world.cleanupConfirmed()&&calls("setProfile")==sets+1,"Cleanup retried or released the wrong view");
                world.viewDestroyed(original);check(world.cleanupConfirmed(),"Original destruction did not settle unknown profile state");
            }finally{mode("success");world.close();}
        }
    }
    private static void confirmed(ClassLoader loader)throws Exception{
        Object first=view(),second=view();MdoBrowserWorld a=open(first,loader),b=open(second,loader);
        int sets=calls("setProfile"),gets=calls("getProfile"),names=calls("profile:getName");
        a.profile("qa.content");b.profile("qa.second");
        check(calls("setProfile")==sets+2&&calls("getProfile")==gets+2&&calls("profile:getName")==names+2,"Profile association was not read back on each original view");
        check("qa.content".equals(invoke("profileName",new Class<?>[]{Object.class},first))&&"qa.second".equals(invoke("profileName",new Class<?>[]{Object.class},second)),"Native profiles crossed physical views");
        a.profile("qa.content");check(calls("setProfile")==sets+2&&calls("getProfile")==gets+2,"Same confirmed request replayed profile mutation");
        rejects("world_profile_order",()->a.profile("qa.second"));
        rejects("world_profile_order",()->a.profile(null));
        a.install("fixed original bootstrap");rejects("world_profile_order",()->a.profile("qa.content"));
        check(calls("setProfile")==sets+2,"Used view was retargeted");a.close();b.close();check(a.cleanupConfirmed()&&b.cleanupConfirmed(),"Confirmed association leaked registrations");
        rejects("world_profile_order",()->b.profile("qa.second"));
    }
    private static void validation(ClassLoader loader)throws Exception{
        MdoBrowserWorld world=open(view(),loader);int sets=calls("setProfile"),gets=calls("getProfile");
        for(String name:new String[]{null,"","Default","qa/other","qa\\other","qa other","\u5185\u5bb9",new String(new char[81]).replace('\0','x')})
            rejects("world_profile_invalid",()->world.profile(name));
        check(calls("setProfile")==sets&&calls("getProfile")==gets,"Rejected profile touched native state");world.profile("qa.content");world.close();
        invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"JS_INJECTION_IN_FRAME_AND_WORLD"});
        MdoBrowserWorld unsupported=open(view(),loader);sets=calls("setProfile");gets=calls("getProfile");
        rejects("browser_profile_unsupported",()->unsupported.profile("qa.content"));unsupported.close();
        check(calls("setProfile")==sets&&calls("getProfile")==gets&&unsupported.cleanupConfirmed(),"Unsupported profile touched native state");
        invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"JS_INJECTION_IN_FRAME_AND_WORLD","MULTI_PROFILE"});
        MdoBrowserWorld used=open(view(),loader);mode("world_zero");sets=calls("setProfile");
        rejects("isolated_world_unsupported",()->used.install("fixed bootstrap"));mode("success");
        rejects("world_profile_order",()->used.profile("qa.content"));
        rejects("world_install_order",()->used.install("do not retry native world setup"));
        check(calls("setProfile")==sets,"Failed native world setup allowed a later profile change");used.close();
    }
    private static void document(ClassLoader loader)throws Exception{
        int sets=calls("setProfile"),gets=calls("getProfile"),installs=calls("addJavaScriptOnEvent");Object original=view();
        invoke("profileSeed",new Class<?>[]{Object.class,String.class},original,"qa.manual-existing");
        MdoBrowserDocument manual=new MdoBrowserDocument(original,loader,new AndroidDocumentContractTest.Clock(),()->true,()->1,null);
        check(!manual.profileAssigned()&&calls("setProfile")==sets&&calls("getProfile")==gets,"Manual profile was mutated or locked by inspection");manual.close();
        check("qa.manual-existing".equals(invoke("profileName",new Class<?>[]{Object.class},original)),"Existing manual profile was replaced");
        MdoBrowserDocument bound=new MdoBrowserDocument(view(),loader,new AndroidDocumentContractTest.Clock(),()->true,()->1,"qa.content");
        check(bound.profileAssigned()&&calls("getProfile")==gets+1,"Document claimed isolation without native readback");bound.close();
        mode("profile_ignore");int worldCalls=calls("getJavaScriptWorld");
        rejects("browser_profile_unconfirmed",()->new MdoBrowserDocument(view(),loader,new AndroidDocumentContractTest.Clock(),()->true,()->1,"qa.content"));
        check(calls("getJavaScriptWorld")==worldCalls&&calls("addJavaScriptOnEvent")==installs+2,"Failed document fell back to default world/profile");mode("success");
    }
    private static void reentrant(ClassLoader loader)throws Exception{
        Object original=view();MdoBrowserWorld world=open(original,loader);List<String> refused=new ArrayList<>();int worlds=calls("getJavaScriptWorld");
        invoke("profileHook",new Class<?>[]{Runnable.class},(Runnable)()->{
            try{world.install("reentrant bootstrap");throw new AssertionError("Pending assignment installed a world");}
            catch(MdoBrowserWorld.Failure failure){refused.add(failure.code);}
            try{world.profile("qa.content");throw new AssertionError("Pending assignment replayed");}
            catch(MdoBrowserWorld.Failure failure){refused.add(failure.code);}
        });
        world.profile("qa.content");check(refused.size()==2&&refused.stream().allMatch("browser_profile_unconfirmed"::equals)&&calls("getJavaScriptWorld")==worlds,"Reentrant profile admission was not fenced");world.close();
        Object retired=view();MdoBrowserWorld closed=open(retired,loader);int gets=calls("getProfile");
        invoke("profileHook",new Class<?>[]{Runnable.class},(Runnable)closed::close);
        rejects("browser_profile_unconfirmed",()->closed.profile("qa.content"));
        check(!closed.cleanupConfirmed()&&calls("getProfile")==gets,"Closed profile operation continued readback or claimed clean");closed.viewDestroyed(retired);check(closed.cleanupConfirmed(),"Destroyed reentrant view was not settled");
        Object duringGet=view();MdoBrowserWorld readClosed=open(duringGet,loader);int names=calls("profile:getName");mode("profile_get_reentrant");
        invoke("profileHook",new Class<?>[]{Runnable.class},(Runnable)readClosed::close);
        rejects("browser_profile_unconfirmed",()->readClosed.profile("qa.content"));
        check(!readClosed.cleanupConfirmed()&&calls("profile:getName")==names,"Retired getter result continued profile inspection");readClosed.viewDestroyed(duringGet);mode("success");
        Object exhausted=view();MdoBrowserWorld oom=open(exhausted,loader);mode("profile_set_oom");
        try{oom.profile("qa.content");throw new AssertionError("Native allocation failure swallowed");}catch(OutOfMemoryError expected){check(true,"fatal preserved");}
        rejects("browser_profile_unconfirmed",()->oom.install("no fallback after allocation failure"));
        oom.close();check(!oom.cleanupConfirmed(),"Fatal native acknowledgement loss was reported clean");oom.viewDestroyed(exhausted);check(oom.cleanupConfirmed(),"Exact native destruction did not settle allocation failure");mode("success");
    }
    public static void main(String[] args)throws Exception{
        try(URLClassLoader loader=new URLClassLoader(new URL[]{Paths.get(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            remote=Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil",true,loader);
            check(remote.getClassLoader()!=AndroidProfileContractTest.class.getClassLoader(),"Provider loader was not foreign");
            mode("success");unconfirmed(loader);confirmed(loader);validation(loader);document(loader);reentrant(loader);
            System.out.println("{\"passed\":true,\"checks\":"+checks+",\"foreign_classloader\":true,\"original_view_readback\":true,\"no_profile_retry\":true,\"framework_verified\":false}");
        }
    }
}
