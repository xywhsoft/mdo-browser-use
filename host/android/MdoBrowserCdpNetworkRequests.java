package org.xserver.android;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Private root-Document identity ledger. No URL/header/body or wire selector
 * crosses this boundary. Correlation failure disables correlation, not existing
 * transfer cleanup. Captured evidence grants no stream or permission. */
final class MdoBrowserCdpNetworkRequests implements MdoBrowserCdpSession.Events,AutoCloseable {
    private static final int LIMIT=4096,REDIRECTS=32;
    static final class Receipt {
        private final Object owner;final String loader;final long born;private volatile boolean valid=true;private int redirects;
        Receipt(Object owner,String loader,long born){this.owner=owner;this.loader=loader;this.born=born;}
    }
    /** Only immutable native evidence and the original typed ACK survive.
     * No binding, WebView, UI callback or current-navigation lookup is retained. */
    static final class Link {
        private volatile Receipt request;private final MdoBrowserCdpSession.Navigation navigation;private final String frame,reference,network;private final Object owner;private final long after;
        private final AtomicReference<String> proved=new AtomicReference<>();
        private Link(Object owner,String network,long after,Receipt request,MdoBrowserCdpSession.Navigation navigation,String frame,String reference){this.owner=owner;this.network=network;this.after=after;this.request=request;this.navigation=navigation;this.frame=frame;this.reference=reference;}
        private void resolve(Receipt original){if(original.owner==owner&&original.valid&&original.born>after)request=original;}
        private boolean awaiting(){MdoBrowserCdpSession.NavigationResult result=navigation.result();return request==null&&(result==null||result.acknowledged&&result.ok&&result.downloadKnown&&result.download&&result.loaderPresent);}
        String reference(){
            String original=proved.get();if(original!=null)return original;
            Receipt source=request;if(source!=null&&source.valid&&navigation.matchesDownloadLoader(frame,source.loader))proved.compareAndSet(null,reference);
            return proved.get();
        }
    }
    private final Thread worker=Thread.currentThread();private final Object owner=new Object();private final String root;
    private final Map<String,Receipt> requests=new LinkedHashMap<>(),loaders=new LinkedHashMap<>();
    private final Link[] pending=new Link[32];
    private long serial;private volatile boolean closed;private volatile String fault;
    MdoBrowserCdpNetworkRequests(String root){if(!MdoBrowserCdpNetworkFrames.identity(root))throw new IllegalArgumentException("Invalid original root");this.root=root;}
    private void current()throws IOException{if(Thread.currentThread()!=worker)throw new IOException("cdp_wrong_worker");if(closed)throw new IOException("cdp_request_owner_closed");}
    long revision()throws IOException{current();return serial;}
    String failure(){return fault;}
    private void disable(String code){fault=code;for(Receipt receipt:requests.values())receipt.valid=false;requests.clear();loaders.clear();java.util.Arrays.fill(pending,null);}
    @Override public void event(String method,String child,JSONObject params)throws IOException{
        current();if(fault!=null||!method.equals("Network.requestWillBeSent")||child!=null||!"Document".equals(params.opt("type"))||!root.equals(params.opt("frameId")))return;
        Object id=params.opt("requestId"),loader=params.opt("loaderId");
        if(!MdoBrowserCdpNetworkFrames.identity(id)||!MdoBrowserCdpNetworkFrames.identity(loader)){disable("cdp_request_metadata_invalid");return;}
        Receipt previous=requests.get((String)id);boolean redirected=params.has("redirectResponse");
        if(redirected){
            Object raw=params.opt("redirectResponse");Object status=raw instanceof JSONObject?((JSONObject)raw).opt("status"):null;
            if(!(status instanceof Number)||!java.util.Arrays.asList(301d,302d,303d,307d,308d).contains(((Number)status).doubleValue())){disable("cdp_request_redirect_invalid");return;}
        }
        if(previous!=null){
            if(!redirected||!previous.loader.equals(loader)||++previous.redirects>REDIRECTS){disable("cdp_request_identity_rebound");return;}
            serial++;return;
        }
        if(loaders.containsKey((String)loader)){disable("cdp_request_loader_rebound");return;}
        if(requests.size()>=LIMIT){disable("cdp_request_identity_limit");return;}
        Receipt receipt=new Receipt(owner,(String)loader,++serial);requests.put((String)id,receipt);loaders.put((String)loader,receipt);
        for(int i=0;i<pending.length;i++)if(pending[i]!=null&&pending[i].network.equals(id)){pending[i].resolve(receipt);pending[i]=null;}
    }
    Link link(String networkId,String frame,long after,MdoBrowserCdpSession.Navigation navigation,String reference)throws IOException{
        current();Receipt request=networkId==null?null:requests.get(networkId);
        if(fault!=null||!MdoBrowserCdpNetworkFrames.identity(networkId)||!root.equals(frame)||navigation==null||navigation.receiptOnly)return null;
        if(request!=null&&(request.owner!=owner||!request.valid||request.born<=after))return null;
        if(request!=null)return new Link(owner,networkId,after,request,navigation,root,reference);
        int slot=-1;for(int i=0;i<pending.length;i++){if(pending[i]!=null&&!pending[i].awaiting())pending[i]=null;if(pending[i]==null)slot=i;}
        if(slot<0){disable("cdp_request_pending_limit");return null;}
        Link link=new Link(owner,networkId,after,null,navigation,root,reference);pending[slot]=link;return link;
    }
    /** Retained original facts remain inspectable after native teardown. */
    @Override public void close(){closed=true;if(Thread.currentThread()==worker||!worker.isAlive()){requests.clear();loaders.clear();java.util.Arrays.fill(pending,null);}}
}
