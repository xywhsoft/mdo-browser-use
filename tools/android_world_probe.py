"""Compile the actual dependency-free Java boundary adapter with a foreign loader.

Uses the existing WSL JDK and one .mdo/qa output. This is a protocol/lifetime
contract gate, not an APK build, Android framework test or real-device pass.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import dev


SOURCES={
'android/webkit/WebView.java':'package android.webkit; public final class WebView {}',
'android/net/Uri.java':'package android.net; public final class Uri {}',
'org/chromium/support_lib_boundary/FeatureFlagHolderBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
public interface FeatureFlagHolderBoundaryInterface {String[] getSupportedFeatures();}''',
'org/chromium/support_lib_boundary/WebViewProviderFactoryBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
import android.webkit.WebView;import java.lang.reflect.InvocationHandler;
public interface WebViewProviderFactoryBoundaryInterface {String[] getSupportedFeatures();InvocationHandler createWebView(WebView view);}''',
'org/chromium/support_lib_boundary/WebViewProviderBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
import java.lang.reflect.InvocationHandler;
public interface WebViewProviderBoundaryInterface {
 void setProfile(String name);InvocationHandler getProfile();int getJavaScriptWorld(String name);
 void addWebMessageListener(String name,String[] origins,InvocationHandler listener,String world);
 InvocationHandler addJavaScriptOnEvent(String script,String[] origins,int event,String world);
 void removeWebMessageListener(String name,String world);
}''',
'org/chromium/support_lib_boundary/ProfileBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
public interface ProfileBoundaryInterface {String getName();}''',
'org/chromium/support_lib_boundary/IsomorphicObjectBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
import java.util.concurrent.Callable;public interface IsomorphicObjectBoundaryInterface {Object getOrCreatePeer(Callable<Object> create);}''',
'org/chromium/support_lib_boundary/JsReplyProxyBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
import java.lang.reflect.InvocationHandler;public interface JsReplyProxyBoundaryInterface extends IsomorphicObjectBoundaryInterface {
 void postMessage(String message);void executeJavaScript(String script,InvocationHandler callback);
}''',
'org/chromium/support_lib_boundary/WebMessageBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
public interface WebMessageBoundaryInterface extends FeatureFlagHolderBoundaryInterface {String getData();}''',
'org/chromium/support_lib_boundary/WebMessageListenerBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
import android.webkit.WebView;import android.net.Uri;import java.lang.reflect.InvocationHandler;
public interface WebMessageListenerBoundaryInterface extends FeatureFlagHolderBoundaryInterface {
 void onPostMessage(WebView view,InvocationHandler message,Uri origin,boolean main,InvocationHandler reply);
}''',
'org/chromium/support_lib_boundary/ExecuteJavaScriptCallbackBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
public interface ExecuteJavaScriptCallbackBoundaryInterface {void onSuccess(String result);void onFailure(int code,String message);}''',
'org/chromium/support_lib_boundary/ScriptHandlerBoundaryInterface.java':'''package org.chromium.support_lib_boundary;
public interface ScriptHandlerBoundaryInterface {void remove();}''',
'org/chromium/support_lib_glue/SupportLibReflectionUtil.java':r'''
package org.chromium.support_lib_glue;
import java.lang.reflect.*;import java.util.*;import java.util.concurrent.Callable;
import android.webkit.WebView;import android.net.Uri;import org.chromium.support_lib_boundary.*;
public final class SupportLibReflectionUtil {
 public static String mode="success";public static String[] features={"JS_INJECTION_IN_FRAME_AND_WORLD","MULTI_PROFILE"};
 public static final List<String> calls=new ArrayList<>();private static InvocationHandler listener;
 private static final Map<Object,String> profileNames=new IdentityHashMap<>();private static Runnable profileHook;
 private static final List<Reply> replies=new ArrayList<>();private static final List<InvocationHandler> delayed=new ArrayList<>();
 public static Object newView(){return new WebView();}public static void mode(String value){mode=value;}
 public static String profileName(Object view){return profileNames.getOrDefault(view,"Default");}
 public static void profileSeed(Object view,String name){profileNames.put(view,name);}
 public static void profileHook(Runnable hook){profileHook=hook;}
 public static void features(String[] value){features=value;}public static int calls(String prefix){int n=0;for(String value:calls)if(value.startsWith(prefix))n++;return n;}
 public static InvocationHandler createWebViewProviderFactory(){return(p,m,a)->{
  foreign(m);if(m.getName().equals("getSupportedFeatures"))return features;
  if(m.getName().equals("createWebView")){calls.add("create");return provider(a[0]);}throw new UnsupportedOperationException();};}
 private static void foreign(Method method){if(method.getDeclaringClass().getClassLoader()!=SupportLibReflectionUtil.class.getClassLoader())throw new AssertionError("Wrong boundary class loader");}
 private static InvocationHandler provider(Object view){return(p,m,a)->{foreign(m);calls.add(m.getName());
  switch(m.getName()){
   case "setProfile":
    if(mode.equals("profile_set_before_throw"))throw new IllegalStateException("private profile setter detail");
    if(!mode.equals("profile_ignore"))profileNames.put(view,(String)a[0]);
    if(mode.equals("profile_set_after_throw"))throw new IllegalStateException("private profile acknowledgement detail");
    if(mode.equals("profile_set_oom"))throw new OutOfMemoryError("synthetic profile allocation failure");
    if(!mode.equals("profile_get_reentrant")){Runnable hook=profileHook;profileHook=null;if(hook!=null)hook.run();}return null;
   case "getProfile":
    if(mode.equals("profile_get_throw"))throw new IllegalStateException("private profile getter detail");
    if(mode.equals("profile_no_handler"))return "not a profile";
    if(mode.equals("profile_null"))return null;
    if(mode.equals("profile_get_reentrant")){Runnable getHook=profileHook;profileHook=null;if(getHook!=null)getHook.run();}
    return(InvocationHandler)(q,n,b)->{
     foreign(n);calls.add("profile:"+n.getName());
     if(!n.getName().equals("getName"))throw new UnsupportedOperationException();
     if(mode.equals("profile_name_throw"))throw new IllegalStateException("private profile name detail");
     if(mode.equals("profile_name_null"))return null;
     if(mode.equals("profile_name_number"))return 17;
     return mode.equals("profile_mismatch")?"qa.other":profileName(view);
    };
   case "getJavaScriptWorld":if(!a[0].equals("mdo.browser-use.v1"))throw new AssertionError();return mode.equals("world_zero")?0:7;
   case "addWebMessageListener":if(!a[3].equals("mdo.browser-use.v1"))throw new AssertionError();listener=(InvocationHandler)a[2];return null;
   case "addJavaScriptOnEvent":if(!a[3].equals("mdo.browser-use.v1")||!a[2].equals(0))throw new AssertionError();
    if(mode.equals("install_fail"))throw new RuntimeException("Synthetic registration acknowledgement loss");
    return(InvocationHandler)(q,n,b)->{foreign(n);calls.add("removeScript");if(mode.equals("cleanup_fail"))throw new RuntimeException();return null;};
   case "removeWebMessageListener":if(!a[1].equals("mdo.browser-use.v1"))throw new AssertionError();calls.add("removeListener");return null;
  }throw new UnsupportedOperationException();};}
 private static class Reply implements InvocationHandler {
  Object peer;
  public Object invoke(Object p,Method m,Object[] a)throws Throwable{foreign(m);calls.add("peer:"+m.getName());
   switch(m.getName()){
    case "getOrCreatePeer":if(peer==null)peer=((Callable<?>)a[0]).call();return peer;
    case "postMessage":return null;
    case "executeJavaScript":InvocationHandler callback=(InvocationHandler)a[1];
     if(mode.equals("defer")){delayed.add(callback);return null;}
     if(mode.equals("throw")){delayed.add(callback);throw new RuntimeException("Synthetic dispatch acknowledgement loss");}
     if(mode.equals("frame")){failure(callback,1);return null;}
     success(callback,mode.equals("oversize")?"x".repeat(262145):"{\"fixture\":7}");
     if(mode.equals("twice"))success(callback,"duplicate");return null;
   }throw new UnsupportedOperationException();}
 }
 private static void success(InvocationHandler callback,String value)throws Throwable{callback.invoke(null,ExecuteJavaScriptCallbackBoundaryInterface.class.getMethod("onSuccess",String.class),new Object[]{value});}
 private static void failure(InvocationHandler callback,int code)throws Throwable{callback.invoke(null,ExecuteJavaScriptCallbackBoundaryInterface.class.getMethod("onFailure",int.class,String.class),new Object[]{code,"private page error must not escape"});}
 public static void late()throws Throwable{for(InvocationHandler callback:new ArrayList<>(delayed))success(callback,"late");delayed.clear();}
 public static int newPeer(){replies.add(new Reply());return replies.size()-1;}
 public static void emit(Object view,boolean main,String text,int index)throws Throwable{
  InvocationHandler message=(p,m,a)->{foreign(m);if(m.getName().equals("getData"))return text;return new String[0];};
  listener.invoke(null,WebMessageListenerBoundaryInterface.class.getMethod("onPostMessage",WebView.class,InvocationHandler.class,Uri.class,boolean.class,InvocationHandler.class),
   new Object[]{view,message,new Uri(),main,replies.get(index)});
 }
}
''',
'org/xserver/android/AndroidWorldContractTest.java':r'''
package org.xserver.android;
import java.lang.reflect.*;import java.net.*;import java.nio.file.*;import java.util.*;
public final class AndroidWorldContractTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static Class<?> remote;static Object invoke(String name,Class<?>[] types,Object...args)throws Exception{return remote.getMethod(name,types).invoke(null,args);}
 static void mode(String value)throws Exception{invoke("mode",new Class<?>[]{String.class},value);}
 static int calls(String value)throws Exception{return(Integer)invoke("calls",new Class<?>[]{String.class},value);}
 static class Clock implements MdoBrowserWorld.Scheduler {
  long time;boolean fail;Map<Runnable,Long> tasks=new HashMap<>();public long now(){return time;}
  public void post(Runnable run,long delay){if(fail)throw new IllegalStateException();tasks.put(run,time+delay);}
  public void remove(Runnable run){tasks.remove(run);}void advance(long duration){time+=duration;for(Runnable run:new ArrayList<>(tasks.keySet()))if(tasks.get(run)<=time)run.run();}
 }
 static class Sink implements MdoBrowserWorld.Receiver {MdoBrowserWorld.Peer peer;int messages;List<String> faults=new ArrayList<>();
  public void message(MdoBrowserWorld.Peer value,String text){peer=value;messages++;}public void fault(String code){faults.add(code);}}
 static void emit(Object view,boolean main,String text,int index)throws Exception{invoke("emit",new Class<?>[]{Object.class,boolean.class,String.class,int.class},view,main,text,index);}
 static int peer()throws Exception{return(Integer)invoke("newPeer",new Class<?>[]{});}
 public static void main(String[] args)throws Exception{
  try(URLClassLoader loader=new URLClassLoader(new URL[]{Paths.get(args[0]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
   remote=Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil",true,loader);
   check(remote.getClassLoader()!=AndroidWorldContractTest.class.getClassLoader(),"Provider was not in a foreign loader");
   Object view=invoke("newView",new Class<?>[]{});Clock clock=new Clock();Sink sink=new Sink();
   invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"MULTI_PROFILE"});
   int creates=calls("create");MdoBrowserWorld.Features support=MdoBrowserWorld.inspect(loader);
   check(!support.isolatedWorld&&support.profiles&&!support.messageListener&&support.count==1,"Independent installed-provider features");
   check(calls("create")==creates,"Feature inspection created a content adapter");
   try{MdoBrowserWorld.open(view,loader,clock,sink);throw new AssertionError();}catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("isolated_world_unsupported"),"Feature detection");}
   check(calls("create")==creates,"Unsupported world created a content adapter");
   invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"JS_INJECTION_IN_FRAME_AND_WORLD:dev","WEB_MESSAGE_LISTENER"});
   support=MdoBrowserWorld.inspect(loader);check(!support.isolatedWorld&&!support.profiles&&support.messageListener,"Development aliases must not enable a stable contract");
   invoke("features",new Class<?>[]{String[].class},(Object)new String[513]);
   try{MdoBrowserWorld.inspect(loader);throw new AssertionError();}catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("world_features_invalid"),"Oversize feature list");}
   invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"JS_INJECTION_IN_FRAME_AND_WORLD","MULTI_PROFILE"});
   MdoBrowserWorld world=MdoBrowserWorld.open(view,loader,clock,sink);check(world.supportsProfile(),"Profile capability");world.profile("qa.content");world.install("private fixture bootstrap");
   int first=peer();emit(view,false,"hello",first);emit(invoke("newView",new Class<?>[]{}),true,"hello",first);check(sink.messages==0,"Subframe/foreign view admission");
   emit(view,true,"x".repeat(16385),first);check(sink.messages==0&&sink.faults.contains("world_message_limit"),"Message bound");
   emit(view,true,"hello",first);MdoBrowserWorld.Peer original=sink.peer;emit(view,true,"again",first);check(original==sink.peer&&sink.messages==2,"Native peer identity");
   List<MdoBrowserWorld.Result> results=new ArrayList<>();mode("twice");original.evaluate("fixed operation",100,results::add);
   check(results.size()==1&&results.get(0).ok&&results.get(0).value.contains("fixture")&&world.pending()==0&&clock.tasks.isEmpty(),"Exactly-once completion");
   mode("oversize");original.evaluate("fixed",100,results::add);check(results.get(1).code.equals("world_result_limit"),"Result bound");
   mode("throw");original.evaluate("fixed",100,results::add);check(results.get(2).evaluationMayHaveOccurred&&!results.get(2).ok,"Lost acknowledgement uncertainty");
   mode("defer");original.evaluate("fixed",100,results::add);clock.advance(100);check(results.get(3).code.equals("world_timed_out")&&results.get(3).evaluationMayHaveOccurred,"Monotonic timeout");
   invoke("late",new Class<?>[]{});check(results.size()==4,"Late completion replayed result");
   original.evaluate("late deadline",100,results::add);clock.time+=101;invoke("late",new Class<?>[]{});
   check(results.get(results.size()-1).code.equals("world_timed_out"),"Late success bypassed monotonic deadline");
   int before=calls("peer:executeJavaScript");clock.fail=true;try{original.evaluate("fixed",100,results::add);throw new AssertionError();}catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("world_timer_unavailable"),"Timer admission");}clock.fail=false;
   check(world.pending()==0&&calls("peer:executeJavaScript")==before,"Failed timer dispatched script");
   for(int i=0;i<16;i++)original.evaluate("fixed",100,results::add);
   try{original.evaluate("overflow",100,results::add);throw new AssertionError();}catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("world_job_limit"),"Job quota");}
   world.invalidate();check(world.pending()==0&&clock.tasks.isEmpty(),"Navigation drained jobs");int completed=results.size();invoke("late",new Class<?>[]{});check(results.size()==completed,"Old navigation callback replayed");
   emit(view,true,"old hello",first);check(sink.peer==original,"Retired peer was reactivated");
   try{original.evaluate("fixed",100,results::add);throw new AssertionError();}catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("frame_changed"),"Old peer dispatch");}
   int next=peer();emit(view,true,"new hello",next);check(sink.peer!=original,"New document reused peer");
   mode("defer");sink.peer.evaluate("waiting",100,results::add);mode("frame");sink.peer.evaluate("destroyed",100,results::add);
   check(world.pending()==0&&results.get(results.size()-1).code.equals("frame_changed"),"Destroyed frame did not drain all jobs");
   int third=peer();emit(view,true,"last hello",third);mode("defer");sink.peer.evaluate("closing",100,results::add);mode("cleanup_fail");world.close();
   check(world.pending()==0&&!world.cleanupConfirmed(),"Unconfirmed cleanup was hidden");mode("success");world.close();
   check(!world.cleanupConfirmed()&&world.outstanding()>0,"Unreturned native work was reported cleaned");
   world.viewDestroyed(view);check(world.cleanupConfirmed()&&world.sharedOutstanding()==0,"Exact native teardown did not release retained work");
   completed=results.size();invoke("late",new Class<?>[]{});emit(view,true,"closed hello",third);check(results.size()==completed&&clock.tasks.isEmpty(),"Closed world accepted a callback");
   invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"JS_INJECTION_IN_FRAME_AND_WORLD"});
   MdoBrowserWorld noProfile=MdoBrowserWorld.open(view,loader,new Clock(),new Sink());check(!noProfile.supportsProfile(),"Missing profile feature was guessed");
   try{noProfile.profile("qa.content");throw new AssertionError();}catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("browser_profile_unsupported"),"Unsupported profile mutation");}noProfile.close();
   invoke("features",new Class<?>[]{String[].class},(Object)new String[]{"JS_INJECTION_IN_FRAME_AND_WORLD","MULTI_PROFILE"});
   MdoBrowserWorld wrongWorld=MdoBrowserWorld.open(view,loader,new Clock(),new Sink());mode("world_zero");
   try{wrongWorld.install("bootstrap");throw new AssertionError();}catch(MdoBrowserWorld.Failure failure){check(failure.code.equals("isolated_world_unsupported"),"Default page world was accepted");}wrongWorld.close();
   check(wrongWorld.cleanupConfirmed(),"Uninstalled world cleanup");
   Sink partialSink=new Sink();MdoBrowserWorld partial=MdoBrowserWorld.open(view,loader,new Clock(),partialSink);mode("install_fail");
   try{partial.install("fixed bootstrap");throw new AssertionError();}catch(MdoBrowserWorld.Failure failure){check(!partial.cleanupConfirmed(),"Lost script-registration acknowledgement was claimed clean");}
   System.out.println("{\"passed\":true,\"checks\":"+checks+",\"foreign_classloader\":true,\"runtime_dependencies_added\":false,\"android_device_verified\":false}");
  }
 }
}
'''
}


def main():
    directory=dev.ENV/'qa/android-world';source=directory/'src';classes=directory/'classes';classes.mkdir(parents=True,exist_ok=True)
    paths=[]
    for name,text in SOURCES.items():
        target=source/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text,encoding='utf-8',newline='\n');paths.append(target)
    production=[dev.ROOT/'host/android/MdoBrowserWorld.java',dev.ROOT/'host/android/MdoBrowserDocument.java']
    paths.extend([*production,dev.ROOT/'tests/fixtures/AndroidDocumentContractTest.java',dev.ROOT/'tests/fixtures/AndroidNativeBudgetContractTest.java',dev.ROOT/'tests/fixtures/AndroidProfileContractTest.java'])
    def linux(path):
        path=path.resolve().as_posix();return '/mnt/'+path[0].lower()+path[2:]
    result=dev.ENV/'android-world-result.json';result.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    subprocess.run(['wsl.exe','--exec','javac','--release','17','-d',linux(classes),*[linux(path) for path in paths]],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=45)
    subprocess.run(['wsl.exe','--exec','javac','--release','8','-d',linux(classes),*[linux(path) for path in production]],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    run=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidWorldContractTest',linux(classes)],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report=json.loads(run.stdout.strip());report['scope']='protocol-lifetime-contract-only';report['apk_built']=False;report['production_java_target']=8
    document=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidDocumentContractTest',linux(classes)],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['document_checks']=json.loads(document.stdout.strip())['checks'];report['document_framework_verified']=False
    profile=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidProfileContractTest',linux(classes)],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['profile']=json.loads(profile.stdout.strip())
    if not report['profile'].get('passed') or report['profile'].get('framework_verified') is not False:
        raise RuntimeError('Original-view profile assignment contract did not pass')
    budget=subprocess.run(['wsl.exe','--exec','java','-cp',linux(classes),'org.xserver.android.AndroidNativeBudgetContractTest',linux(classes)],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=30)
    report['native_budget']=json.loads(budget.stdout.strip())
    if not report['native_budget']['passed']:
        report['passed']=False;result.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        raise RuntimeError('Client deadlines admitted more native evaluations while provider callbacks remained outstanding')
    inputs=[*production,dev.ROOT/'host/android/protocol.json',dev.ROOT/'tools/android_world_probe.py',
            dev.ROOT/'tests/fixtures/AndroidDocumentContractTest.java',dev.ROOT/'tests/fixtures/AndroidNativeBudgetContractTest.java',dev.ROOT/'tests/fixtures/AndroidProfileContractTest.java']
    report['source_sha256']={path.relative_to(dev.ROOT).as_posix():hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}
    result.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-2400:],file=sys.stderr);raise SystemExit(1)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
