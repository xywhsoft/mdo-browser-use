package org.xserver.android;

/** Production channel identity/URL lease gate. No Android message-port or
 * JavaScript execution behavior is claimed by this JVM test. */
public final class AndroidChannelContractTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Throwing {void run();}
    static void rejects(Throwing action){try{action.run();throw new AssertionError("Invalid channel admitted");}
        catch(IllegalStateException|IllegalArgumentException expected){checks++;}}
    static class Port {
        @Override public boolean equals(Object other){return other instanceof Port;}
        @Override public int hashCode(){return 1;}
    }
    public static void main(String[] args)throws Exception {
        MdoBrowserChannel<Port> channel=new MdoBrowserChannel<>();Port first=new Port(),next=new Port();
        check(channel.current()==null&&!channel.active(null,"http://127.0.0.1/"),"Empty channel was active");
        MdoBrowserChannel<Port>.Lease original=channel.attach("http://127.0.0.1/app#one",first);
        check(channel.owns(original,first,"http://127.0.0.1/app#two"),"Same-document fragment rejected");
        check(!channel.owns(original,next,"http://127.0.0.1/app"),"Equal-looking foreign port admitted");
        for(String url:new String[]{null,"http://127.0.0.1/other","http://127.0.0.1/app?query=1","https://127.0.0.1/app","http://127.0.0.1/app/"})
            check(!channel.active(original,url),"Different top document admitted");
        MdoBrowserChannel<Port>.Lease replacement=channel.attach("http://127.0.0.1/app",next);
        check(channel.current()==replacement&&channel.active(replacement,"http://127.0.0.1/app"),"Replacement binding missing");
        check(!channel.active(original,"http://127.0.0.1/app"),"Old async reply/handshake crossed a same-URL reload");
        check(!channel.owns(replacement,first,"http://127.0.0.1/app"),"Old incoming port crossed a reconnect");
        MdoBrowserChannel<Port>.Lease repeated=channel.attach("http://127.0.0.1/app",next);
        check(!channel.active(replacement,"http://127.0.0.1/app")&&channel.active(repeated,"http://127.0.0.1/app"),"Connection identity reduced to port/URL equality");
        MdoBrowserChannel<Port> foreign=new MdoBrowserChannel<>();MdoBrowserChannel<Port>.Lease other=foreign.attach("http://127.0.0.1/app",next);
        check(!channel.active(other,"http://127.0.0.1/app"),"Foreign channel lease admitted");
        rejects(()->channel.attach(null,first));rejects(()->channel.attach("",first));rejects(()->channel.attach("http://127.0.0.1/",null));
        check(channel.current()==repeated,"Invalid attach discarded the current connection");
        channel.invalidate();channel.invalidate();check(channel.current()==null&&!channel.active(repeated,"http://127.0.0.1/app"),"Main-document start did not invalidate a same-URL connection");
        MdoBrowserChannel<Port>.Lease afterStart=channel.attach("http://127.0.0.1/app",next);
        check(!channel.active(repeated,"http://127.0.0.1/app")&&channel.active(afterStart,"http://127.0.0.1/app"),"Reconnect revived an old pending handshake/reply");
        channel.close();channel.close();check(channel.current()==null&&!channel.active(repeated,"http://127.0.0.1/app"),"Destroyed channel retained a reply route");
        rejects(()->channel.attach("http://127.0.0.1/app",first));
        final boolean[] owner={false};Thread thread=new Thread(()->{try{foreign.current();}catch(IllegalStateException expected){owner[0]=true;}});
        thread.start();thread.join();check(owner[0],"Foreign thread accessed the UI channel");foreign.close();
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"android_device_verified\":false}");
    }
}
