package org.xserver.android;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.webkit.WebView;

public final class AndroidPopupTargetContractTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Window target check "+checks);}
    static void invalid(Runnable action){try{action.run();throw new AssertionError("Expected transport rejection");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
    static final class Binding implements MdoBrowserWindowTarget.Windows {
        int creates,closes;MdoBrowserPopups.Transport<WebView> transport;boolean gesture;
        public boolean create(WebView source,boolean userGesture,MdoBrowserPopups.Transport<WebView> transport){creates++;gesture=userGesture;this.transport=transport;return true;}
        public void close(WebView source){closes++;}
    }
    public static void main(String[] args){
        WebView source=new WebView(),other=new WebView();boolean[] owned={true};Binding callbacks=new Binding();
        MdoBrowserWindowTarget target=new MdoBrowserWindowTarget(source,()->owned[0],()->1,blocked->{},epoch->{},callbacks);
        Handler handler=new Handler(Looper.getMainLooper());WebView.WebViewTransport nativeTransport=new WebView.WebViewTransport();Message message=new Message(handler,nativeTransport);
        check(!target.onCreateWindow(other,false,true,message)&&callbacks.creates==0);
        check(target.onCreateWindow(source,false,true,message)&&callbacks.creates==1&&callbacks.gesture);
        check(callbacks.transport.token()==message&&handler.messages==0&&nativeTransport.sets==0);
        final int[] ack={0};WebView child=new WebView();callbacks.transport.send(child,()->{check(handler.processed==1);ack[0]++;});
        check(nativeTransport.child==child&&nativeTransport.sets==1&&handler.messages==1&&ack[0]==0);
        invalid(()->callbacks.transport.send(child,()->{}));check(handler.messages==1&&nativeTransport.sets==1);
        handler.drain();check(ack[0]==1&&handler.processed==1);
        target.onCloseWindow(other);check(callbacks.closes==0);target.onCloseWindow(source);check(callbacks.closes==1);
        owned[0]=false;check(!target.onCreateWindow(source,false,false,new Message(handler,new WebView.WebViewTransport()))&&callbacks.creates==1);owned[0]=true;
        check(!target.onCreateWindow(source,false,false,null));check(!target.onCreateWindow(source,false,false,new Message(handler,new Object())));
        check(!target.onCreateWindow(source,false,false,new Message(null,new WebView.WebViewTransport())));
        check(!target.onCreateWindow(source,false,false,new Message(new Handler(new Looper()),new WebView.WebViewTransport())));
        Looper.current=new Looper();check(!target.onCreateWindow(source,false,false,new Message(new Handler(Looper.current),new WebView.WebViewTransport())));Looper.current=Looper.MAIN;
        WebView.WebViewTransport rejected=new WebView.WebViewTransport();MdoBrowserWindowTarget.Transport rejection=new MdoBrowserWindowTarget.Transport(new Message(handler,rejected));
        rejection.send(null,()->ack[0]++);check(rejected.child==null&&rejected.sets==1&&ack[0]==1);handler.drain();check(ack[0]==2);
        Handler stopped=new Handler(Looper.MAIN);stopped.failSend=true;MdoBrowserWindowTarget.Transport lost=new MdoBrowserWindowTarget.Transport(new Message(stopped,new WebView.WebViewTransport()));
        invalid(()->lost.send(child,()->{}));invalid(()->lost.send(child,()->{}));check(stopped.messages==0);
        Handler noBarrier=new Handler(Looper.MAIN);noBarrier.failPost=true;MdoBrowserWindowTarget.Transport pending=new MdoBrowserWindowTarget.Transport(new Message(noBarrier,new WebView.WebViewTransport()));
        invalid(()->pending.send(child,()->{}));check(noBarrier.messages==1);invalid(()->pending.send(child,()->{}));noBarrier.drain();check(noBarrier.processed==1);
        target.close();target.viewDestroyed();
        System.out.println("{\"passed\":true,\"checks\":"+checks+"}");
    }
}
