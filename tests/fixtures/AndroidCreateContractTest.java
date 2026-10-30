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
    public static void main(String[] args)throws Exception{
        profileFailure();ordinaryFailures();manualAndPopup();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"exact_product_fragments\":true,\"framework_verified\":false,\"network_calls\":0}");
    }
}
