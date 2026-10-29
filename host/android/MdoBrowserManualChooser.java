package org.xserver.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.function.BooleanSupplier;

/** One human document picker per Activity. No Agent URI assignment or private
 * provider access. Cancelled native launches keep their original result slot. */
final class MdoBrowserManualChooser implements AutoCloseable {
    private static final int FIRST=0x6000,LAST=0x7fff;private static int next=FIRST;
    private static final String SAVED="mdo.browser.manual.chooser.next";
    private static final class Pending {
        final int code;final long deadline;final boolean multiple;WebView view;ValueCallback<Uri[]> reply;BooleanSupplier original;Runnable unblock;Runnable timer;
        Pending(int code,WebView view,ValueCallback<Uri[]> reply,BooleanSupplier original,Runnable unblock,boolean multiple){
            this.code=code;this.view=view;this.reply=reply;this.original=original;this.unblock=unblock;this.multiple=multiple;deadline=SystemClock.uptimeMillis()+120000;
        }
    }
    private static final class Timeout implements Runnable {
        final WeakReference<MdoBrowserManualChooser> owner;final int code;
        Timeout(MdoBrowserManualChooser owner,int code){this.owner=new WeakReference<>(owner);this.code=code;}
        public void run(){MdoBrowserManualChooser value=owner.get();if(value!=null)value.expire(code);}
    }
    private final Activity activity;private final Handler handler=new Handler(Looper.getMainLooper());
    private Pending pending;private boolean closed,failed,resumed;
    MdoBrowserManualChooser(Activity activity){this.activity=activity;}
    private void ui(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Manual chooser requires UI");}
    void restore(Bundle saved){ui();if(saved!=null){int value=saved.getInt(SAVED,next);next=value<FIRST||value>LAST+1?LAST+1:Math.max(next,value);}}
    void save(Bundle saved){ui();saved.putInt(SAVED,next);}
    void resume(){ui();resumed=true;}
    void pause(){ui();resumed=false;} // Returning from our system picker is allowed before Activity.onResume.
    boolean busy(WebView view){ui();return pending!=null&&pending.view==view&&pending.reply!=null;}
    boolean ready(){ui();return !closed&&!failed&&pending==null;}
    boolean clean(){ui();return !failed;}
    boolean awaitingResult(){ui();return pending!=null&&pending.reply==null;}
    private void complete(Pending value,Uri[] uris){
        ValueCallback<Uri[]> reply=value.reply;Runnable unblock=value.unblock;value.reply=null;value.view=null;value.original=null;value.unblock=null;
        if(value.timer!=null){try{handler.removeCallbacks(value.timer);}catch(RuntimeException|OutOfMemoryError ignored){}value.timer=null;}
        if(unblock!=null)try{unblock.run();}catch(RuntimeException|OutOfMemoryError error){failed=true;uris=null;}
        if(reply!=null)try{reply.onReceiveValue(uris);}catch(RuntimeException|OutOfMemoryError error){failed=true;}
    }
    private boolean valid(Pending value){return !closed&&!failed&&value.reply!=null&&SystemClock.uptimeMillis()<value.deadline&&value.original!=null&&value.original.getAsBoolean();}
    private void expire(int code){ui();Pending value=pending;if(value==null||value.code!=code||value.reply==null)return;
        try{if(SystemClock.uptimeMillis()<value.deadline){if(handler.postAtTime(value.timer,value.deadline))return;failed=true;}}
        catch(RuntimeException|OutOfMemoryError failure){failed=true;}complete(value,null);
    }
    void cancel(WebView view){ui();if(pending!=null&&pending.view==view)complete(pending,null);}
    void invalidate(){ui();if(pending!=null)complete(pending,null);}
    boolean open(WebView view,ValueCallback<Uri[]> reply,WebChromeClient.FileChooserParams params,BooleanSupplier original,Runnable block,Runnable unblock){
        ui();if(reply==null||view==null||params==null)return false;
        if(closed||failed||!resumed||pending!=null||next>LAST){reject(reply);return true;}
        Pending value=null;boolean entered=false;
        try{
            if(!original.getAsBoolean()){reject(reply);return true;}
            int mode=params.getMode();if(mode!=0&&mode!=1){reject(reply);return true;}
            String[] accepts=params.getAcceptTypes();if(accepts!=null&&accepts.length>16){reject(reply);return true;}
            LinkedHashSet<String> types=new LinkedHashSet<>();
            if(accepts!=null)for(String raw:accepts){if(raw!=null&&raw.length()>256)throw new IllegalArgumentException("Chooser metadata too large");if(raw==null||raw.trim().isEmpty())continue;String type=raw.trim().toLowerCase(java.util.Locale.ROOT);
                if(type.length()>128||!type.matches("(?:[a-z0-9!#$&^_.+-]+|\\*)/(?:[a-z0-9!#$&^_.+-]+|\\*)")){types.clear();types.add("*/*");break;}types.add(type);}
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(types.size()==1?types.iterator().next():"*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,mode==1);
            if(types.size()>1)intent.putExtra(Intent.EXTRA_MIME_TYPES,types.toArray(new String[0]));
            value=new Pending(next++,view,reply,original,unblock,mode==1);value.timer=new Timeout(this,value.code);pending=value;
            block.run();if(!valid(value))throw new IllegalStateException("Manual chooser owner changed");
            if(!handler.postAtTime(value.timer,value.deadline))throw new IllegalStateException("Manual chooser scheduler stopped");
            entered=true;activity.startActivityForResult(intent,value.code);return true;
        }catch(ActivityNotFoundException absent){
            if(value==null)reject(reply);else if(value.reply!=null){if(pending==value){pending=null;complete(value,null);}else{value.reply=null;reject(reply);}}return true;
        }
        catch(RuntimeException|OutOfMemoryError failure){
            // A launch may have occurred before a platform failure. Never reuse
            // its result slot or route that late result into a new callback.
            if(value==null)reject(reply);else if(value.reply!=null){if(pending==value){if(!entered)pending=null;complete(value,null);}else{value.reply=null;reject(reply);}}return true;
        }
    }
    private void reject(ValueCallback<Uri[]> reply){try{reply.onReceiveValue(null);}catch(RuntimeException|OutOfMemoryError failure){failed=true;}}
    private boolean permitted(Uri uri){
        if(uri==null||uri.toString().length()>8192||!"content".equals(uri.getScheme()))return false;String authority=uri.getAuthority();
        if(authority==null||authority.length()>256||!authority.matches("[A-Za-z0-9_.-]+"))return false;
        ProviderInfo provider=activity.getPackageManager().resolveContentProvider(authority,0);
        if(provider==null||provider.applicationInfo==null||provider.applicationInfo.uid==Process.myUid())return false;
        return activity.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)==PackageManager.PERMISSION_GRANTED;
    }
    private Uri[] selected(Pending value,int result,Intent data){
        if(result!=Activity.RESULT_OK||data==null)return null;LinkedHashSet<Uri> selected=new LinkedHashSet<>();ClipData clip=data.getClipData();
        if(clip!=null){int size=clip.getItemCount();if(size>16||size<1||!value.multiple&&size>1)return null;
            for(int at=0;at<size;at++){Uri uri=clip.getItemAt(at).getUri();if(!permitted(uri))return null;selected.add(uri);}}
        if(selected.isEmpty()){Uri uri=data.getData();if(!permitted(uri))return null;selected.add(uri);}
        return selected.toArray(new Uri[0]);
    }
    boolean result(int request,int result,Intent data){
        ui();if(request<FIRST||request>LAST)return false;Pending value=pending;
        if(value==null||value.code!=request)return true;pending=null;
        Uri[] selected=null;try{if(valid(value))selected=selected(value,result,data);}catch(RuntimeException|OutOfMemoryError invalid){}
        complete(value,selected);return true;
    }
    @Override public void close(){ui();if(closed)return;closed=true;resumed=false;invalidate();}
}
