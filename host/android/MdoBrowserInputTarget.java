package org.xserver.android;

import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.text.InputType;
import android.webkit.WebView;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Dispatch only to the owned WebView. Coordinates are Android view pixels;
 * isolated-world CSS-to-view geometry/focus validation belongs to the caller. */
final class MdoBrowserInputTarget implements MdoBrowserInput.Target {
    private final WebView view;private final LongSupplier epoch;private final BooleanSupplier admitted;
    MdoBrowserInputTarget(WebView view,LongSupplier epoch,BooleanSupplier admitted){
        if(view==null||epoch==null||admitted==null)throw new IllegalArgumentException("Missing input binding");
        this.view=view;this.epoch=epoch;this.admitted=admitted;
    }
    public long document(){return epoch.getAsLong();}
    public int width(){return view.getWidth();}public int height(){return view.getHeight();}
    public boolean interactive(){return admitted.getAsBoolean()&&view.isAttachedToWindow()&&view.isShown()&&view.hasWindowFocus();}
    public boolean focused(){return view.hasFocus();}
    public boolean acquireFocus(){return interactive()&&view.requestFocus();}
    public boolean touch(int action,long down,long time,float x,float y){
        MotionEvent event=MotionEvent.obtain(down,time,action,x,y,0);
        try{event.setSource(InputDevice.SOURCE_TOUCHSCREEN);return view.dispatchTouchEvent(event);}
        finally{event.recycle();}
    }
    public boolean key(boolean down,long started,long time,String name,int modifiers){
        int code;
        if(name.length()==1)code=name.charAt(0)>='A'?KeyEvent.KEYCODE_A+name.charAt(0)-'A':KeyEvent.KEYCODE_0+name.charAt(0)-'0';
        else switch(name){
            case "Enter":code=KeyEvent.KEYCODE_ENTER;break;case "Tab":code=KeyEvent.KEYCODE_TAB;break;
            case "Backspace":code=KeyEvent.KEYCODE_DEL;break;case "Delete":code=KeyEvent.KEYCODE_FORWARD_DEL;break;
            case "Escape":code=KeyEvent.KEYCODE_ESCAPE;break;case "Space":code=KeyEvent.KEYCODE_SPACE;break;
            case "ArrowLeft":code=KeyEvent.KEYCODE_DPAD_LEFT;break;case "ArrowRight":code=KeyEvent.KEYCODE_DPAD_RIGHT;break;
            case "ArrowUp":code=KeyEvent.KEYCODE_DPAD_UP;break;case "ArrowDown":code=KeyEvent.KEYCODE_DPAD_DOWN;break;
            case "Home":code=KeyEvent.KEYCODE_MOVE_HOME;break;case "End":code=KeyEvent.KEYCODE_MOVE_END;break;
            case "PageUp":code=KeyEvent.KEYCODE_PAGE_UP;break;case "PageDown":code=KeyEvent.KEYCODE_PAGE_DOWN;break;
            default:throw new IllegalArgumentException("Unsupported content key");
        }
        int meta=((modifiers&1)!=0?KeyEvent.META_SHIFT_ON:0)|((modifiers&2)!=0?KeyEvent.META_CTRL_ON:0)|((modifiers&4)!=0?KeyEvent.META_ALT_ON:0);
        return view.dispatchKeyEvent(new KeyEvent(started,time,down?KeyEvent.ACTION_DOWN:KeyEvent.ACTION_UP,
            code,0,meta,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD));
    }
    public boolean text(String value){
        EditorInfo info=new EditorInfo();InputConnection connection=view.onCreateInputConnection(info);
        // The WebView/IME owns this connection. Do not close it or retain it
        // across turns/documents, and never fall back to DOM value assignment.
        return connection!=null&&info.inputType!=InputType.TYPE_NULL&&connection.commitText(value,1);
    }
}
