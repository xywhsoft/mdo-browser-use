package org.xserver.android;

import android.webkit.WebView;

/** Uses the production UI registry; only the input surface/clock is synthetic.
 * An explicit viewDestroyed call simulates a host post-destroy receipt. */
final class PopupLifecycleFixture {
    static MdoBrowserLifecycle<WebView> registry(){
        return registry(()->false);
    }
    static MdoBrowserLifecycle<WebView> registry(java.util.function.BooleanSupplier fail){
        return registry(fail,()->{});
    }
    static MdoBrowserLifecycle<WebView> registry(java.util.function.BooleanSupplier fail,Runnable constructing){
        return new MdoBrowserLifecycle<>((view,epoch,admitted)->{
            constructing.run();
            if(fail.getAsBoolean())throw new IllegalStateException("Controlled input allocation failure");
            return new MdoBrowserInput(new MdoBrowserInput.Target(){
            public long document(){return epoch.getAsLong();}public int width(){return 100;}public int height(){return 100;}
            public boolean interactive(){return admitted.getAsBoolean();}public boolean focused(){return true;}
            public boolean touch(int action,long down,long time,float x,float y){return true;}
            public boolean key(boolean down,long started,long time,String key,int modifiers){return true;}
            public boolean text(String value){return true;}
        },new MdoBrowserInput.Scheduler(){
            public long now(){return 100;}public void post(Runnable task,long delay){}public void remove(Runnable task){}
        });});
    }
    static void destroyAll(MdoBrowserLifecycle<WebView> views){
        java.util.List<WebView> exact=views.values();views.close();for(WebView view:exact)views.viewDestroyed(view);
    }
}
