package org.xserver.android;

/** Exact production visual-callback bridge with controlled native callbacks. */
public final class AndroidInitialScaleBridgeContractTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Initial scale bridge check "+checks);}
    public static void main(String[] args)throws Exception {
        for(int mode=0;mode<6;mode++){
            AndroidCreateHost host=new AndroidCreateHost();host.create("tab",AndroidCreateContractTest.request(false));
            AndroidCreateHost.WebView view=host.views.get("tab");
            if(mode==4)view.failVisual=true;
            host.startPage("tab","https://fixture.invalid/");host.finishPage("tab","https://fixture.invalid/");
            check(view.visuals.size()==1&&view.scaleReads==0&&Double.isNaN(host.views.scale("tab",view)));
            AndroidCreateHost.WebView.Visual first=view.visuals.get(0);
            if(mode==1)host.startPage("tab","https://changed.invalid/");
            if(mode==2)host.views.scaleChanged("tab",view,2);
            if(mode==3)host.closeCommand("tab");
            if(mode==5)view.beforeScale=()->host.startPage("tab","https://changed.invalid/");
            first.callback.onComplete(first.id);first.callback.onComplete(first.id);
            check(view.scaleReads==(mode==0||mode==5?1:0));
            if(mode==0)check(host.views.scale("tab",view)==3.5);
            else if(mode==2)check(host.views.scale("tab",view)==2);
            else check(Double.isNaN(host.views.scale("tab",view)));
            if(mode==1||mode==4||mode==5){
                view.failVisual=false;host.finishPage("tab","https://changed.invalid/");check(view.visuals.size()==2);
                AndroidCreateHost.WebView.Visual next=view.visuals.get(1);next.callback.onComplete(next.id);
                check(host.views.scale("tab",view)==3.5);
            }
            if(mode!=3)host.closeCommand("tab");
        }
        AndroidCreateHost host=new AndroidCreateHost();host.create("tab",AndroidCreateContractTest.request(false));AndroidCreateHost.WebView view=host.views.get("tab");
        host.finishPage("tab","https://fixture.invalid/");host.finishPage("tab","https://fixture.invalid/");check(view.visuals.size()==1);
        AndroidCreateHost.WebView.Visual pending=view.visuals.get(0);pending.callback.onComplete(pending.id+1);check(view.scaleReads==0&&Double.isNaN(host.views.scale("tab",view)));host.closeCommand("tab");
        Class<?> callback=Class.forName("org.xserver.android.AndroidCreateHost$InitialScaleCallback");
        check(java.lang.reflect.Modifier.isStatic(callback.getModifiers()));
        for(java.lang.reflect.Field field:callback.getDeclaredFields())check(field.getType()!=AndroidCreateHost.class&&field.getType()!=AndroidCreateHost.WebView.class);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false}");
    }
}
