package org.xserver.android;

/** Main-document native port ownership. A repeated URL/tab ID is not the same
 * connection. This class never creates, transfers or closes someone else's port. */
final class MdoBrowserChannel<P> implements AutoCloseable {
    final class Lease {
        private final P port;private final String document;
        private Lease(P port,String document){this.port=port;this.document=document;}
    }
    private final Thread owner=Thread.currentThread();private Lease current;private boolean closed;
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Browser channel requires its owning UI thread");}
    private static String document(String url){if(url==null)return null;int hash=url.indexOf('#');return hash<0?url:url.substring(0,hash);}
    Lease attach(String url,P port){
        thread();if(closed)throw new IllegalStateException("Browser channel closed");
        if(url==null||url.isEmpty()||url.length()>8192||port==null)throw new IllegalArgumentException("Invalid browser channel binding");
        return current=new Lease(port,document(url));
    }
    Lease current(){thread();return current;}
    boolean owns(Lease lease,P source,String url){
        thread();return !closed&&lease!=null&&lease==current&&source==lease.port&&lease.document.equals(document(url));
    }
    boolean active(Lease lease,String url){thread();return lease!=null&&owns(lease,lease.port,url);}
    void invalidate(){thread();current=null;}
    @Override public void close(){thread();closed=true;current=null;}
}
