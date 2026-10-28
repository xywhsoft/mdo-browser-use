package org.xserver.android;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Actual production document controller against a foreign native boundary. */
public final class AndroidDocumentContractTest {
    static int checks;static Class<?> remote;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Object invoke(String name,Class<?>[] types,Object... args)throws Exception{return remote.getMethod(name,types).invoke(null,args);}
    static void mode(String value)throws Exception{invoke("mode",new Class<?>[]{String.class},value);}
    static int calls(String value)throws Exception{return(Integer)invoke("calls",new Class<?>[]{String.class},value);}
    static int peer()throws Exception{return(Integer)invoke("newPeer",new Class<?>[]{});}
    static void emit(Object view,int peer)throws Exception{emit(view,true,MdoBrowserDocument.HELLO,peer);}
    static void emit(Object view,boolean main,String text,int peer)throws Exception{
        invoke("emit",new Class<?>[]{Object.class,boolean.class,String.class,int.class},view,main,text,peer);
    }
    static final class Clock implements MdoBrowserWorld.Scheduler {
        long time;Map<Runnable,Long> tasks=new HashMap<>();public long now(){return time;}
        public void post(Runnable task,long delay){tasks.put(task,time+delay);}public void remove(Runnable task){tasks.remove(task);}
    }
    static void denied(MdoBrowserDocument document,String expected)throws Exception{
        int before=calls("peer:executeJavaScript");
        try{document.evaluate("fixed",100,result->{});throw new AssertionError("Unexpected dispatch");}
        catch(MdoBrowserWorld.Failure failure){check(expected.equals(failure.code),"Wrong refusal: "+failure.code);}
        check(calls("peer:executeJavaScript")==before,"Refused document dispatched native JavaScript");
    }
    public static void main(String[] args)throws Exception{
        try(URLClassLoader loader=new URLClassLoader(new URL[]{Paths.get(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            remote=Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil",true,loader);
            Object view=invoke("newView",new Class<?>[]{});Clock clock=new Clock();
            AtomicBoolean owned=new AtomicBoolean(true);AtomicLong epoch=new AtomicLong(1);
            int profiles=calls("setProfile");MdoBrowserDocument document=new MdoBrowserDocument(view,loader,clock,owned::get,epoch::get,null);
            check(!document.profileAssigned()&&calls("setProfile")==profiles,"Manual view's existing profile was changed");
            denied(document,"world_not_ready");int first=peer();
            emit(view,false,MdoBrowserDocument.HELLO,first);document.loaded();denied(document,"world_not_ready");
            emit(invoke("newView",new Class<?>[]{}),first);denied(document,"world_not_ready");
            emit(view,first);check(document.ready(),"Main frame did not become ready");
            String firstIdentity=document.identity();
            check(firstIdentity!=null&&firstIdentity.matches("fd-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"),"Native document identity was not assigned");
            document.loaded();check(firstIdentity.equals(document.identity()),"Duplicate load rotated the native document identity");
            int nativeBefore=calls("peer:executeJavaScript");
            try{document.evaluate("fd-foreign","must not execute",100,result->{throw new AssertionError();});throw new AssertionError();}
            catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("frame_changed"),"Foreign document identity was not explicit");}
            check(calls("peer:executeJavaScript")==nativeBefore,"Foreign document identity dispatched native code");
            int[] identityReplies={0};document.evaluate(firstIdentity,"fixed",100,result->{check(result.ok,"Exact native identity failed");identityReplies[0]++;});
            check(identityReplies[0]==1,"Exact native identity did not settle once");
            List<MdoBrowserWorld.Result> results=new ArrayList<>();mode("twice");document.evaluate("fixed",100,results::add);
            check(results.size()==1&&results.get(0).ok&&document.pending()==0,"Duplicate native result was forwarded");
            mode("defer");document.evaluate("old document",100,results::add);
            int replacement=peer();emit(view,replacement);
            check(results.size()==2&&!results.get(1).ok,"New native peer did not cancel the old evaluation");
            denied(document,"world_not_ready");
            epoch.incrementAndGet();document.navigation();
            check(!document.ready(),"Navigation was ready before completion");document.loaded();
            check(document.ready(),"Early replacement peer was retired by a later start callback");
            check(!firstIdentity.equals(document.identity()),"Replacement document reused the original native identity");
            int completed=results.size();invoke("late",new Class<?>[]{});
            check(results.size()==completed,"Late old result escaped document retirement");
            mode("defer");document.evaluate("reentrant navigation",100,result->{
                results.add(result);try{denied(document,"world_not_ready");}catch(Exception error){throw new AssertionError(error);}
            });
            epoch.incrementAndGet();document.navigation();
            check(results.size()==completed+1,"Navigation callback was not settled once");
            int reentrantReplacement=peer();emit(view,reentrantReplacement);document.loaded();
            check(document.ready(),"Reentrant cancellation prevented fresh document recovery");
            document.block(MdoBrowserDocument.BACKGROUND,true);
            int beforeStart=peer();emit(view,beforeStart);
            document.block(MdoBrowserDocument.BACKGROUND,false);
            check(document.identity()==null,"Resume exposed a peer before its original native start");
            epoch.incrementAndGet();document.navigation();document.loaded();
            check(document.ready(),"Resume retired an early replacement peer before its native navigation start");
            completed=results.size();
            String resumedIdentity=document.identity();
            mode("defer");document.evaluate("paused",100,results::add);
            document.block(MdoBrowserDocument.BACKGROUND,true);
            check(results.size()==completed+1&&results.get(completed).code.equals("world_view_unavailable"),"Background cancellation lost its reason");
            check(clock.tasks.isEmpty(),"Cancelled document left a timer");denied(document,"world_view_unavailable");
            check(document.identity()==null,"A blocked document retained externally usable identity");
            document.block(MdoBrowserDocument.MANUAL,true);document.block(MdoBrowserDocument.BACKGROUND,false);
            denied(document,"user_taken_control");document.block(MdoBrowserDocument.MANUAL,false);check(document.ready(),"Independent blockers did not clear");
            check(resumedIdentity.equals(document.identity()),"Returning to the same document rotated its native identity");
            document.block(MdoBrowserDocument.DIALOG,true);denied(document,"dialog_open");
            document.block(MdoBrowserDocument.MANUAL,true);document.block(MdoBrowserDocument.DIALOG,false);
            denied(document,"user_taken_control");document.block(MdoBrowserDocument.MANUAL,false);
            check(resumedIdentity.equals(document.identity()),"Dialog settlement rotated or retired the original document");
            completed=results.size();document.evaluate("main connection",100,results::add);document.block(MdoBrowserDocument.CHANNEL,true);
            check(results.get(completed).code.equals("main_document_changed"),"Main connection cancellation lost its reason");
            denied(document,"main_document_changed");document.block(MdoBrowserDocument.CHANNEL,false);
            check(document.ready(),"Fresh main connection could not use the retained document");
            completed=results.size();document.evaluate("stopped",100,results::add);document.cancel();
            check(results.get(completed).code.equals("world_cancelled")&&document.ready(),"Stop replayed or destroyed a document");
            completed=results.size();document.evaluate("epoch changed",100,results::add);epoch.incrementAndGet();invoke("late",new Class<?>[]{});
            check(results.size()==completed+1&&!results.get(completed).ok,"Native epoch change accepted a late success");
            denied(document,"world_not_ready");document.navigation();int third=peer();emit(view,third);document.loaded();check(document.ready(),"Fresh navigation could not recover");
            completed=results.size();document.evaluate("owner changed",100,results::add);owned.set(false);invoke("late",new Class<?>[]{});
            check(results.size()==completed+1&&!results.get(completed).ok,"Retired native view accepted a late success");denied(document,"frame_changed");
            owned.set(true);document.close();check(document.cleanupConfirmed()&&clock.tasks.isEmpty(),"Owned document cleanup failed");denied(document,"world_closed");
            check(document.identity()==null,"Closed native document identity remained usable");
            mode("success");Object nextView=invoke("newView",new Class<?>[]{});
            MdoBrowserDocument isolated=new MdoBrowserDocument(nextView,loader,new Clock(),()->true,()->1,"qa.content");
            check(isolated.profileAssigned()&&calls("setProfile")==profiles+1,"Explicit profile was not assigned before use");
            isolated.loaded();int fourth=peer();emit(nextView,true,"foreign payload",fourth);denied(isolated,"world_message_invalid");isolated.close();check(isolated.cleanupConfirmed(),"Failed document did not clean up");
            invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"MULTI_PROFILE"});int creates=calls("create");
            try{new MdoBrowserDocument(nextView,loader,new Clock(),()->true,()->1,null);throw new AssertionError();}
            catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("isolated_world_unsupported"),"Unsupported provider was not explicit");}
            check(calls("create")==creates,"Unsupported provider created an isolated view");
            System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
        }
    }
}
