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
    private static void standalone(ClassLoader loader)throws Exception{
        invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"MULTI_PROFILE"});mode("success");
        int worlds=calls("getJavaScriptWorld"),scripts=calls("addJavaScriptOnEvent"),listeners=calls("addWebMessageListener"),creates=calls("create"),sets=calls("setProfile"),gets=calls("getProfile");
        Object parentView=view();rejects("isolated_world_unsupported",()->open(parentView,loader));check(calls("create")==creates,"Unqualified world instantiated a provider");
        MdoBrowserProfile parent=MdoBrowserProfile.open(parentView,loader);
        check(parent.supported()&&parent.name()==null&&calls("getProfile")==gets&&calls("setProfile")==sets,"Opening a profile locked or mutated the default association");
        parent.assign("qa.native-original");parent.assign("qa.native-original");parent.seal();
        check(parent.name().equals("qa.native-original")&&calls("setProfile")==sets+1&&calls("getProfile")==gets+1,"Original standalone profile was not acknowledged exactly once");
        Object aView=view(),bView=view();MdoBrowserProfile a=MdoBrowserProfile.inherit(aView,loader,parent),b=MdoBrowserProfile.inherit(bView,loader,parent);
        check(a.name().equals(parent.name())&&b.name().equals(parent.name())&&calls("setProfile")==sets+3&&calls("getProfile")==gets+3,"Native children did not inherit the original confirmed namespace");
        check("qa.native-original".equals(invoke("profileName",new Class<?>[]{Object.class},aView))&&"qa.native-original".equals(invoke("profileName",new Class<?>[]{Object.class},bView)),"Inheritance guessed profile identity without native readback");
        parent.close();check(parent.name()==null&&parent.cleanupConfirmed()&&a.name().equals("qa.native-original")&&b.name().equals("qa.native-original"),"Closing the opener retired independent child profiles");
        rejects("world_profile_order",()->a.assign("qa.other"));a.close();a.close();check(a.cleanupConfirmed()&&b.name().equals("qa.native-original"),"Child close changed another native owner");
        check("qa.native-original".equals(invoke("profileName",new Class<?>[]{Object.class},aView)),"Logical close reset or deleted native profile data");b.close();
        check(calls("getJavaScriptWorld")==worlds&&calls("addJavaScriptOnEvent")==scripts&&calls("addWebMessageListener")==listeners,"Profile-only inheritance granted script/world/listener access");
        for(java.lang.reflect.Field field:MdoBrowserProfile.class.getDeclaredFields())check(field.getType()!=MdoBrowserProfile.class,"Child retained its opener profile owner");
        invoke("features",new Class<?>[]{String[].class},(Object)new String[]{});creates=calls("create");sets=calls("setProfile");
        rejects("browser_profile_unsupported",()->MdoBrowserProfile.open(view(),loader));check(calls("create")==creates&&calls("setProfile")==sets,"Missing profile support fell back to native/default mutation");
    }
    private static void inheritanceFailures(ClassLoader loader)throws Exception{
        invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"MULTI_PROFILE"});mode("success");
        Object parentView=view();MdoBrowserProfile parent=MdoBrowserProfile.open(parentView,loader);int creates=calls("create");
        rejects("browser_profile_source_invalid",()->MdoBrowserProfile.inherit(view(),loader,parent));parent.assign("qa.original");
        rejects("browser_profile_source_invalid",()->MdoBrowserProfile.inherit(parentView,loader,parent));
        rejects("browser_profile_source_invalid",()->MdoBrowserProfile.inherit(view(),ClassLoader.getPlatformClassLoader(),parent));
        rejects("browser_profile_source_invalid",()->MdoBrowserProfile.inherit(view(),loader,null));check(calls("create")==creates,"Invalid inheritance source allocated a provider");
        java.util.concurrent.atomic.AtomicReference<Throwable> foreign=new java.util.concurrent.atomic.AtomicReference<>();
        Thread thread=new Thread(()->{try{parent.name();}catch(Throwable error){foreign.set(error);}});thread.start();thread.join();check(foreign.get() instanceof IllegalStateException,"Profile ownership crossed the original UI thread");
        for(String fault:new String[]{"profile_ignore","profile_set_before_throw","profile_set_after_throw","profile_get_throw","profile_no_handler","profile_null","profile_name_throw","profile_name_null","profile_name_number","profile_mismatch"}){
            int sets=calls("setProfile"),worlds=calls("getJavaScriptWorld");Object child=view();mode(fault);
            rejects("browser_profile_unconfirmed",()->MdoBrowserProfile.inherit(child,loader,parent));
            check(calls("setProfile")==sets+1&&calls("getJavaScriptWorld")==worlds&&parent.name().equals("qa.original"),"Failed child profile retried, granted a world or damaged the source");mode("success");
        }
        Object original=view();MdoBrowserProfile direct=MdoBrowserProfile.open(original,loader);mode("profile_set_after_throw");int sets=calls("setProfile");
        rejects("browser_profile_unconfirmed",()->direct.assign("qa.direct"));direct.close();check(!direct.cleanupConfirmed()&&direct.name()==null,"Acknowledgment loss was reported clean");
        try{direct.viewDestroyed(view());throw new AssertionError("Foreign native destruction released uncertainty");}catch(IllegalStateException expected){check(true,"exact destruction");}
        direct.viewDestroyed(original);check(direct.cleanupConfirmed()&&calls("setProfile")==sets+1,"Exact native teardown retried or retained profile uncertainty");mode("success");
        Object child=view();sets=calls("setProfile");int gets=calls("getProfile");invoke("profileHook",new Class<?>[]{Runnable.class},(Runnable)parent::close);
        rejects("browser_profile_owner_changed",()->MdoBrowserProfile.inherit(child,loader,parent));
        check(calls("setProfile")==sets+1&&calls("getProfile")==gets+1&&"qa.original".equals(invoke("profileName",new Class<?>[]{Object.class},child)),"Changed opener hid or replayed a completed child assignment");
        creates=calls("create");rejects("browser_profile_source_invalid",()->MdoBrowserProfile.inherit(view(),loader,parent));check(calls("create")==creates,"Closed opener admitted a replacement child");
        MdoBrowserProfile before=MdoBrowserProfile.open(view(),loader);before.assign("qa.before");sets=calls("setProfile");
        invoke("providerHook",new Class<?>[]{Runnable.class},(Runnable)before::close);
        rejects("browser_profile_owner_changed",()->MdoBrowserProfile.inherit(view(),loader,before));check(calls("setProfile")==sets,"Retired opener assigned a child after provider allocation");
        invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"JS_INJECTION_IN_FRAME_AND_WORLD","MULTI_PROFILE"});
    }
    private static void borrowedWorld(ClassLoader loader)throws Exception{
        mode("success");invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"MULTI_PROFILE","JS_INJECTION_IN_FRAME_AND_WORLD"});
        Object original=view();MdoBrowserProfile profile=MdoBrowserProfile.open(original,loader);profile.assign("qa.borrowed");profile.seal();
        MdoBrowserWorld.Peer[] observed={null};int sets=calls("setProfile"),creates=calls("create");MdoBrowserWorld.Receiver sink=new MdoBrowserWorld.Receiver(){public void message(MdoBrowserWorld.Peer peer,String text){observed[0]=peer;}public void fault(String code){throw new AssertionError(code);}};
        AndroidDocumentContractTest.Clock clock=new AndroidDocumentContractTest.Clock();MdoBrowserWorld.Budget budget=new MdoBrowserWorld.Budget(2);
        rejects("browser_profile_source_invalid",()->MdoBrowserWorld.borrow(view(),loader,clock,sink,budget,profile));
        rejects("browser_profile_source_invalid",()->MdoBrowserWorld.borrow(original,ClassLoader.getPlatformClassLoader(),clock,sink,budget,profile));
        MdoBrowserWorld borrowed=MdoBrowserWorld.borrow(original,loader,clock,sink,budget,profile);borrowed.install("fixed borrowed bootstrap");
        check(calls("setProfile")==sets&&calls("create")==creates,"Borrowing rebound profile or recreated native provider");
        borrowed.close();check(borrowed.cleanupConfirmed()&&profile.name().equals("qa.borrowed")&&!profile.cleanupConfirmed(),"Realm cleanup retired the physical profile owner");borrowed.viewDestroyed(original);
        MdoBrowserWorld again=MdoBrowserWorld.borrow(original,loader,clock,sink,budget,profile);again.install("fixed live borrowed world");
        int nativePeer=(Integer)invoke("newPeer",new Class<?>[]{});invoke("emit",new Class<?>[]{Object.class,boolean.class,String.class,int.class},original,true,"fixed",nativePeer);
        check(observed[0]!=null&&again.current(observed[0]),"Live borrowed peer was not admitted");
        List<MdoBrowserWorld.Result> delayed=new ArrayList<>();mode("defer");observed[0].evaluate("fixed pending call",100,delayed::add);mode("success");
        MdoBrowserWorld uninstalled=MdoBrowserWorld.borrow(original,loader,clock,sink,budget,profile);profile.close();int worlds=calls("getJavaScriptWorld"),dispatches=calls("peer:executeJavaScript");
        check(!again.current(observed[0]),"Closed physical owner retained an admitted borrowed peer");
        rejects("frame_changed",()->observed[0].evaluate("must not execute",100,result->{}));check(calls("peer:executeJavaScript")==dispatches,"Closed physical profile dispatched borrowed native execution");
        rejects("frame_changed",()->observed[0].post("must not post"));invoke("late",new Class<?>[]{});
        check(delayed.size()==1&&!delayed.get(0).ok&&delayed.get(0).value==null&&"frame_changed".equals(delayed.get(0).code)&&again.pending()==0&&again.outstanding()==0,"Retired borrowed owner published a late result or leaked its native call");
        rejects("world_profile_order",()->uninstalled.install("refused revoked physical owner"));check(calls("getJavaScriptWorld")==worlds,"Closed physical profile granted an execution world");again.close();uninstalled.close();profile.viewDestroyed(original);
    }
    private static void physicalController(ClassLoader loader)throws Exception{
        mode("success");invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"MULTI_PROFILE"});
        Object untouched=view();MdoBrowserDocument pending=MdoBrowserDocument.prepare(untouched,()->true,()->1);int[] completed={0};
        pending.whenInitialized(()->completed[0]++);int before=calls("create");
        rejects("world_arguments_invalid",()->pending.initialize(null,new AndroidDocumentContractTest.Clock(),"qa.validation",new MdoBrowserWorld.Budget(2),()->true));
        rejects("world_arguments_invalid",()->pending.initialize(loader,null,"qa.validation",new MdoBrowserWorld.Budget(2),()->true));
        rejects("world_arguments_invalid",()->pending.initialize(loader,new AndroidDocumentContractTest.Clock(),"qa.validation",null,()->true));
        rejects("world_arguments_invalid",()->pending.initialize(loader,new AndroidDocumentContractTest.Clock(),"qa.validation",new MdoBrowserWorld.Budget(2),null));
        check(calls("create")==before&&completed[0]==0&&!pending.initializing(),"Invalid initialization touched the provider or consumed completion");
        pending.initialize(loader,new AndroidDocumentContractTest.Clock(),"qa.validation",new MdoBrowserWorld.Budget(2),()->true);
        check(completed[0]==1&&pending.profileAssigned(),"Corrected initialization did not complete exactly once");
        rejects("world_install_order",()->pending.initialize(loader,new AndroidDocumentContractTest.Clock(),"qa.validation",new MdoBrowserWorld.Budget(2),()->true));
        pending.close();pending.viewDestroyed(untouched);check(completed[0]==1&&pending.cleanupConfirmed(),"Closing replayed the initialization callback");
        MdoBrowserDocument abandoned=MdoBrowserDocument.prepare(view(),()->true,()->1);abandoned.close();abandoned.whenInitialized(()->completed[0]++);
        check(completed[0]==2&&abandoned.cleanupConfirmed(),"Already closed preparation retained its completion callback");
        Object original=view();int sets=calls("setProfile"),creates=calls("create"),worlds=calls("getJavaScriptWorld");
        MdoBrowserDocument controller=new MdoBrowserDocument(original,loader,new AndroidDocumentContractTest.Clock(),()->true,()->1,"qa.native-controller");
        check(controller.profileAssigned()&&!controller.ready()&&!controller.worldInstalled()&&controller.identity()==null&&controller.phase().equals("isolated_world_unsupported"),"Physical-only controller invented or lost profile/world authority");
        check(calls("setProfile")==sets+1&&calls("create")==creates+1&&calls("getJavaScriptWorld")==worlds,"Profile-only controller created a realm or repeated provider binding");
        controller.navigation();controller.committed();controller.block(MdoBrowserDocument.MANUAL,true);controller.block(MdoBrowserDocument.MANUAL,false);
        check(controller.profileAssigned()&&controller.pending()==0&&controller.outstanding()==0,"Document lifecycle revoked physical profile association");controller.close();check(controller.cleanupConfirmed()&&!controller.profileAssigned(),"Physical controller close did not release logical ownership");controller.viewDestroyed(original);
        invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"MULTI_PROFILE","JS_INJECTION_IN_FRAME_AND_WORLD"});
        Object shared=view();sets=calls("setProfile");creates=calls("create");MdoBrowserDocument full=new MdoBrowserDocument(shared,loader,new AndroidDocumentContractTest.Clock(),()->true,()->1,"qa.full-controller");
        check(full.profileAssigned()&&full.worldInstalled()&&calls("setProfile")==sets+1&&calls("create")==creates+1,"Controller did not borrow its single confirmed native profile");full.close();check(full.cleanupConfirmed(),"Shared profile/world controller cleanup failed");full.viewDestroyed(shared);
        for(boolean close:new boolean[]{false,true}){
            java.util.concurrent.atomic.AtomicBoolean admitted=new java.util.concurrent.atomic.AtomicBoolean(true);Object candidate=view();MdoBrowserDocument preparing=MdoBrowserDocument.prepare(candidate,()->true,()->1);
            int[] completions={0};preparing.whenInitialized(()->{check(!preparing.initializing(),"Initialization callback preceded native return");completions[0]++;});
            invoke("profileHook",new Class<?>[]{Runnable.class},(Runnable)()->{check(preparing.initializing()&&!preparing.cleanupConfirmed(),"Native profile call was not charged during callback");if(close)preparing.close();else admitted.set(false);});
            rejects(close?"browser_profile_unconfirmed":"frame_changed",()->preparing.initialize(loader,new AndroidDocumentContractTest.Clock(),"qa.cancelled-controller",new MdoBrowserWorld.Budget(2),admitted::get));
            check(completions[0]==1&&!preparing.initializing()&&preparing.cleanupConfirmed()==!close,"Cancelled native initialization retained callback ownership");preparing.viewDestroyed(candidate);check(preparing.cleanupConfirmed(),"Exact native destruction did not settle cancelled profile initialization");
        }
    }
    private static void inheritedController(ClassLoader loader)throws Exception{
        mode("success");invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"MULTI_PROFILE"});
        Object opener=view();MdoBrowserDocument parent=new MdoBrowserDocument(opener,loader,new AndroidDocumentContractTest.Clock(),()->true,()->1,"qa.popup-source");
        Object nativeChild=view();MdoBrowserDocument child=MdoBrowserDocument.prepare(nativeChild,()->true,()->1);
        int creates=calls("create"),sets=calls("setProfile"),worlds=calls("getJavaScriptWorld"),scripts=calls("addJavaScriptOnEvent"),listeners=calls("addWebMessageListener");
        rejects("browser_profile_source_invalid",()->child.initializeInheritedProfile(loader,parent,view(),()->true));
        rejects("browser_profile_source_invalid",()->child.initializeInheritedProfile(ClassLoader.getPlatformClassLoader(),parent,opener,()->true));
        check(calls("create")==creates,"Invalid physical source allocated a child provider");
        int[] completed={0};child.whenInitialized(()->{check(!child.initializing(),"Inherited completion preceded native return");completed[0]++;});
        child.initializeInheritedProfile(loader,parent,opener,()->true);
        check(child.profileAssigned()&&!child.worldInstalled()&&!child.ready()&&"popup_world_unqualified".equals(child.phase())&&completed[0]==1,"Inherited physical controller granted a realm or lost acknowledgment");
        check(calls("create")==creates+1&&calls("setProfile")==sets+1&&"qa.popup-source".equals(invoke("profileName",new Class<?>[]{Object.class},nativeChild)),"Native child did not read back the original profile once");
        check(calls("getJavaScriptWorld")==worlds&&calls("addJavaScriptOnEvent")==scripts&&calls("addWebMessageListener")==listeners,"Pre-transport inheritance granted script access");
        parent.close();check(child.profileAssigned(),"Independent physical child retained its parent lifetime");child.close();child.viewDestroyed(nativeChild);parent.viewDestroyed(opener);
        for(java.lang.reflect.Field field:MdoBrowserDocument.class.getDeclaredFields())check(field.getType()!=MdoBrowserDocument.class,"Child controller retained its opener");
        for(String mode:new String[]{"child-close","parent-close","provider-close","uncertain","denied"}){
            Object rootView=view(),candidate=view();MdoBrowserDocument root=new MdoBrowserDocument(rootView,loader,new AndroidDocumentContractTest.Clock(),()->true,()->1,"qa.popup-failure");
            MdoBrowserDocument pending=MdoBrowserDocument.prepare(candidate,()->true,()->1);int[] terminal={0};pending.whenInitialized(()->{check(!pending.initializing(),"Inherited completion remained in flight");terminal[0]++;});
            creates=calls("create");sets=calls("setProfile");
            if(mode.equals("provider-close"))invoke("providerHook",new Class<?>[]{Runnable.class},(Runnable)root::close);
            if(mode.equals("child-close")||mode.equals("parent-close"))invoke("profileHook",new Class<?>[]{Runnable.class},(Runnable)()->{check(pending.initializing()&&!pending.cleanupConfirmed(),"Inherited setter lost its in-flight owner");if(mode.equals("child-close"))pending.close();else root.close();});
            if(mode.equals("uncertain"))mode("profile_set_after_throw");
            rejects(mode.equals("child-close")||mode.equals("uncertain")?"browser_profile_unconfirmed":mode.equals("denied")?"frame_changed":"browser_profile_owner_changed",()->pending.initializeInheritedProfile(loader,root,rootView,()->!mode.equals("denied")));
            check(terminal[0]==1&&!pending.profileAssigned()&&!pending.initializing(),"Failed inherited setup retained admission/completion");
            check(pending.cleanupConfirmed()==!(mode.equals("child-close")||mode.equals("uncertain")),"Inherited native uncertainty was lost or invented");
            check(calls("create")==creates+(mode.equals("denied")?0:1)&&calls("setProfile")==sets+(mode.equals("denied")||mode.equals("provider-close")?0:1),"Failed inherited setup retried native configuration");
            pending.viewDestroyed(candidate);check(pending.cleanupConfirmed(),"Exact inherited native teardown did not settle uncertainty");mode("success");root.close();root.viewDestroyed(rootView);
        }
    }
    public static void main(String[] args)throws Exception{
        try(URLClassLoader loader=new URLClassLoader(new URL[]{Paths.get(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            remote=Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil",true,loader);
            check(remote.getClassLoader()!=AndroidProfileContractTest.class.getClassLoader(),"Provider loader was not foreign");
            mode("success");unconfirmed(loader);confirmed(loader);validation(loader);document(loader);reentrant(loader);standalone(loader);inheritanceFailures(loader);borrowedWorld(loader);physicalController(loader);inheritedController(loader);
            System.out.println("{\"passed\":true,\"checks\":"+checks+",\"foreign_classloader\":true,\"inherited_physical_controller\":true,\"borrowed_world_and_physical_controller\":true,\"standalone_profile_inheritance\":true,\"original_view_readback\":true,\"no_profile_retry\":true,\"framework_verified\":false}");
        }
    }
}
