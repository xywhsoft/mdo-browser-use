package org.xserver.android;

/** Exact product fragments with controlled UI/native dependencies. */
public final class AndroidCreateContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static AndroidCreateHost.JSONObject request(boolean automation)throws Exception{return new AndroidCreateHost.JSONObject().put("automation",automation).put("url","https://fixture.invalid/");}
    static void profileFailure()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();
        try{host.create("tab",request(true));throw new AssertionError("Unconfirmed profile opened");}
        catch(UnsupportedOperationException expected){check("browser_profile_unconfirmed".equals(expected.getMessage()),"Original profile failure was hidden");}
        AndroidCreateHost.WebView original=host.created.get(0);
        check(original.destroys==1,"Profile failure destroyed the original native view more than once");
        check(original.loads==0&&original.touches==0&&host.replies==0,"Failed profile touched settings, loaded a page or replied success");
        check(host.views.size()==0&&host.documents.isEmpty()&&host.documentFailures.isEmpty()&&host.root.children.isEmpty(),"Failed create kept UI bindings");
        check(host.profileRequests.size()==1&&"mdo.browser-use".equals(host.profileRequests.get(0))&&host.retired==1,"Failure changed profile or retired the wrong data owner");
    }
    static void ordinaryFailures()throws Exception{
        for(String fault:new String[]{"input","settings","attach","bounds","load","reply"}){
            AndroidCreateHost host=new AndroidCreateHost();host.fault=fault;
            try{host.create("tab",request(false));throw new AssertionError("Fixture create fault ignored");}
            catch(RuntimeException|AndroidCreateHost.JSONException expected){check(true,"expected failure");}
            AndroidCreateHost.WebView original=host.created.get(0);check(original.destroys==1,"Create fault did not retire exactly one original view: "+fault);
            check(host.views.size()==0&&host.documents.isEmpty()&&host.dialogs.isEmpty()&&host.root.children.isEmpty(),"Create fault retained active bindings: "+fault);
            host.fault="";host.create("explicit-retry",request(false));
            check(host.created.size()==2&&!Boolean.TRUE.equals(host.capabilities().data.get("native_view_cleanup_failed")),"Clean failure cleanup poisoned explicit new creation: "+fault);
        }
    }
    static void manualAndPopup()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();host.fault="manual_world";host.create("tab",request(false));
        AndroidCreateHost.WebView original=host.created.get(0);
        check(original.loads==1&&original.destroys==0&&host.views.size()==1,"Unsupported isolated world broke manual browsing");
        check(host.profileRequests.size()==1&&host.profileRequests.get(0)==null,"Manual view requested an automation profile");
        host.create("tab",request(false));check(host.created.size()==1&&original.loads==1,"Reattachment recreated or reloaded the page");
        AndroidCreateHost.MdoBrowserPopups<AndroidCreateHost.WebView> parent=new AndroidCreateHost.MdoBrowserPopups<>();parent.deferred=true;host.windows.put("opener",parent);
        host.create("child",new AndroidCreateHost.JSONObject().put("popup","popup").put("opener","opener"));AndroidCreateHost.WebView child=host.created.get(1);
        check(parent.adopts==1&&child.loads==0&&child.destroys==0&&host.popupLinks.containsKey("child"),"Pending native popup was replayed or destroyed");
        parent.pending.done(true,null,true);check(host.popupLinks.get("child").ready&&child.loads==0,"Popup confirmation navigated to another page");
    }
    static void failedCleanup()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();host.fault="destroy";
        try{host.create("failed",request(true));throw new AssertionError("Unconfirmed native destruction ignored");}
        catch(IllegalStateException expected){
            check("native_view_cleanup_failed".equals(expected.getMessage()),"Cleanup uncertainty was not preserved as a typed result");
            check(expected.getCause() instanceof UnsupportedOperationException&&expected.getSuppressed().length==1,"Original profile/cleanup reasons were lost");
        }
        check(host.created.get(0).destroys==1&&host.views.size()==0,"Failed cleanup replayed destruction or left a usable view");
        check(Boolean.TRUE.equals(host.capabilities().data.get("native_view_cleanup_failed"))&&Integer.valueOf(0).equals(host.capabilities().data.get("native_view_retiring")),"Cleanup capability was hidden or kept a phantom running retirement");
        host.fault="";
        try{host.create("replacement",request(false));throw new AssertionError("Unknown native release admitted another physical view");}
        catch(IllegalStateException expected){check("native_view_cleanup_failed".equals(expected.getMessage()),"Uncertain resource fence was not sticky");}
        check(host.created.size()==1,"Erased UI map reset the native resource count");
        check(!host.closeCommand("failed"),"Repeated close reported unknown native cleanup as confirmed");
    }
    static void reentrantCleanup()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();final int[] refused={0};
        host.root.beforeRemove=()->{
            try{check(Integer.valueOf(1).equals(host.capabilities().data.get("native_view_retiring")),"Active native retirement was not visible");}catch(Exception error){throw new AssertionError(error);}
            check(!host.closeCommand("failed"),"Reentrant empty-map close reported native retirement complete");
            try{host.create("reentrant",request(false));throw new AssertionError("New view created during native retirement");}
            catch(IllegalStateException expected){check("native_view_retirement_busy".equals(expected.getMessage()),"Wrong native retirement fence");refused[0]++;}
            catch(Exception unexpected){throw new AssertionError(unexpected);}
        };
        try{host.create("failed",request(true));throw new AssertionError("Profile failure disappeared");}
        catch(UnsupportedOperationException expected){check("browser_profile_unconfirmed".equals(expected.getMessage()),"Cleanup reentrance hid the original failure");}
        check(refused[0]==1&&host.created.size()==1&&host.views.size()==0&&host.created.get(0).destroys==1,"Native retirement reservation did not cover UI callbacks");
        host.create("after",request(false));check(host.created.size()==2&&host.views.size()==1,"Clean retirement poisoned an explicit later creation");
    }
    static void cleanupFaultsAndQuota()throws Exception{
        for(String cleanup:new String[]{"detach","destroy","document","oom"}){
            AndroidCreateHost host=new AndroidCreateHost();host.fault="load";host.cleanupFault=cleanup;
            try{host.create("tab",request(false));throw new AssertionError("Unconfirmed retirement was ignored: "+cleanup);}
            catch(IllegalStateException expected){check(!cleanup.equals("oom")&&"native_view_cleanup_failed".equals(expected.getMessage()),"Wrong cleanup priority: "+cleanup);}
            catch(OutOfMemoryError expected){check(cleanup.equals("oom"),"Unexpected cleanup allocation failure");}
            check(Boolean.TRUE.equals(host.capabilities().data.get("native_view_cleanup_failed")),"Native cleanup failure was not latched: "+cleanup);
            host.fault=host.cleanupFault="";
            try{host.create("new",request(false));throw new AssertionError("Failed cleanup admitted new view");}
            catch(IllegalStateException expected){check("native_view_cleanup_failed".equals(expected.getMessage()),"Resource fence was reset");}
            check(host.created.size()==1,"Unknown native release reset the view limit");
        }
        AndroidCreateHost host=new AndroidCreateHost();for(int i=0;i<8;i++)host.create("tab"+i,request(false));
        try{host.create("overflow",request(false));throw new AssertionError("Native quota was ignored");}catch(IllegalStateException expected){check(true,"bounded");}
        check(host.created.size()==8&&host.views.size()==8,"Quota checked after native allocation");
        AndroidCreateHost.WebView original=host.created.get(0);host.closeCommand("tab0");host.create("after",request(false));
        check(original.destroys==1&&host.created.size()==9&&host.views.size()==8,"Confirmed native release did not free exactly one slot");
        host.stopped=true;try{host.create("after",request(false));throw new AssertionError("Stopped owner reattached a view");}
        catch(IllegalStateException expected){check("native_browser_closed".equals(expected.getMessage()),"Stopped owner was not fenced");}
        AndroidCreateHost chooser=new AndroidCreateHost();chooser.create("tab",request(false));chooser.views.remove("tab");chooser.cleanupFault="chooser";
        check(!chooser.closeCommand("tab"),"Logical close lost the existing chooser-cleanup predicate");
    }
    static void inputRetirementCallback()throws Exception{
        AndroidCreateHost host=new AndroidCreateHost();host.create("tab",request(false));AndroidCreateHost.WebView original=host.created.get(0);final int[] callbacks={0};
        host.views.input("tab",original).tap(1,1,result->{
            callbacks[0]++;
            try{host.create("during-input-close",request(false));throw new AssertionError("Input close escaped the native reservation");}
            catch(IllegalStateException expected){check("native_view_retirement_busy".equals(expected.getMessage()),"Input callback lost retirement reservation");}
            catch(Exception error){throw new AssertionError(error);}
            check(!host.retire("tab",original),"Recursive close of the same native view was accepted");
        });
        check(host.closeCommand("tab"),"Clean input retirement was unconfirmed");
        check(callbacks[0]==1&&host.created.size()==1&&original.destroys==1&&host.views.size()==0,"Native input cleanup reentered creation or destroyed twice");
        host.create("explicit-after",request(false));check(host.views.size()==1,"Completed input retirement retained a phantom reservation");
    }
    public static void main(String[] args)throws Exception{
        profileFailure();ordinaryFailures();manualAndPopup();failedCleanup();reentrantCleanup();cleanupFaultsAndQuota();inputRetirementCallback();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"exact_product_fragments\":true,\"framework_verified\":false,\"network_calls\":0}");
    }
}
