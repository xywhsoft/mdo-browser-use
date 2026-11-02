package org.xserver.android;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.JSONException;
import org.json.JSONObject;

/** Network-only facade on the proved original Browser attachment. Its fixed
 * bootstrap never enables Runtime, creates worlds, resumes the debugger or
 * replaces the existing Request/Response Fetch guard. Document readiness is
 * deliberately independent of receiving/deciding a paused network response. */
final class MdoBrowserCdpPopupSource implements MdoBrowserCdpBinding.Peer,MdoBrowserCdpDownloads.AttachedSource {
    /** Only this scope guard can issue a retirement receipt. A renderer crash
     * or a listener/protocol error with the same text is not this receipt. */
    static final class Retired extends IOException {
        private Retired(){super("cdp_popup_source_retired");}
    }
    private final MdoBrowserCdpSession session;
    private final MdoBrowserCdpSession.AttachedTarget attachment;
    private final MdoBrowserCdpPopupBroker.Handoff handoff;
    private final MdoBrowserCdpPopupNative.Source proof;
    private final String root,initialLoader;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final BooleanSupplier scope;
    private final MdoBrowserCdpNetworkFrames frames;
    private final MdoBrowserCdpNetworkRequests requests;
    private MdoBrowserCdpSession.Events receiver;
    private MdoBrowserCdpDownloads.Connection connection;
    private boolean started,ready;

    MdoBrowserCdpPopupSource(MdoBrowserCdpSession session,MdoBrowserCdpSession.AttachedTarget attachment,
            MdoBrowserCdpPopupBroker.Handoff handoff,MdoBrowserCdpPopupNative.Source proof,String root,String loader)throws IOException {
        this.session=session;this.attachment=attachment;this.handoff=handoff;this.proof=proof;this.root=root;initialLoader=loader;
        frames=new MdoBrowserCdpNetworkFrames(root,loader);requests=new MdoBrowserCdpNetworkRequests(root);
        // Body receipts keep only stop data and the exact native ownership
        // receipts, not this peer, the broker, a document graph or a WebView.
        final AtomicBoolean stop=closed;final MdoBrowserCdpPopupBroker.Handoff physical=handoff;final MdoBrowserCdpPopupNative.Source original=proof;
        scope=()->!stop.get()&&physical.current()&&original.current()&&physical.owns(original);
    }
    private void guard()throws IOException {if(!scope.getAsBoolean())throw new Retired();}
    boolean matches(MdoBrowserCdpPopupNative.Source original){return proof==original;}
    String frame()throws IOException {guard();return root;}
    String loader()throws IOException {guard();return initialLoader;}
    String target()throws IOException {guard();return attachment.target;}
    void navigationCurrent()throws IOException {guard();if(!ready)throw new IOException("cdp_popup_source_bootstrap_required");if(!attachment.resumeConfirmed())throw new IOException("cdp_popup_activation_required");}
    MdoBrowserCdpNetworkFrames navigationFrames()throws IOException {navigationCurrent();return frames;}
    MdoBrowserCdpNetworkRequests navigationRequests()throws IOException {navigationCurrent();return requests;}
    boolean ownsDocument(MdoBrowserCdpBinding.Peer document)throws IOException {navigationCurrent();return document instanceof MdoBrowserCdpPopupBroker.DocumentPeer&&((MdoBrowserCdpPopupBroker.DocumentPeer)document).belongsTo(handoff);}
    private void admitNavigation()throws IOException {
        navigationCurrent();if(connection==null)throw new IOException("cdp_popup_navigation_network_unavailable");if(!connection.navigationAllowed())throw new IOException("cdp_navigation_download_pending");
    }
    public MdoBrowserCdpSession.Navigation navigateRoot(String url,String frame,int timeout)throws IOException {
        admitNavigation();if(!root.equals(frame))throw new IOException("cdp_popup_navigation_root_invalid");return session.navigateAttached(attachment,scope,handoff::draining,url,root,timeout);
    }
    public MdoBrowserCdpSession.Navigation historyRoot(String frame,int entry,int timeout)throws IOException {
        admitNavigation();if(!root.equals(frame))throw new IOException("cdp_popup_navigation_root_invalid");return session.historyAttached(attachment,scope,handoff::draining,root,entry,timeout);
    }
    public MdoBrowserCdpSession.Navigation reloadRoot(String frame,String loader,int timeout)throws IOException {
        admitNavigation();if(!root.equals(frame)||!frames.rootLoader().equals(loader))throw new IOException("cdp_history_snapshot_changed");return session.reloadAttached(attachment,scope,handoff::draining,root,loader,timeout);
    }
    void observe(MdoBrowserCdpSession.Events next)throws IOException {guard();if(next==null||receiver!=null)throw new IOException("cdp_controller_busy");receiver=next;}
    private int remaining(long born,int timeout)throws IOException {
        guard();long used=android.os.SystemClock.elapsedRealtime()-born;if(used<0||used>timeout-10)throw new IOException("cdp_popup_source_timeout");return timeout-(int)used;
    }
    private JSONObject bootstrapCall(String method,JSONObject params,long born,int timeout)throws IOException {
        JSONObject reply=session.callAttachedBootstrap(attachment,scope,handoff::draining,method,params,Math.min(5000,remaining(born,timeout)));guard();return reply;
    }
    void bootstrap(int timeout)throws IOException {
        guard();if(started||receiver==null||attachment.resumeConfirmed()||timeout<100||timeout>15000)throw new IOException("cdp_popup_source_bootstrap_invalid");started=true;
        long born=android.os.SystemClock.elapsedRealtime();
        try{
            if(bootstrapCall("Page.enable",new JSONObject(),born,timeout).length()!=0)throw new IOException("cdp_popup_source_reply_invalid");
            boolean initialized=false;
            for(int attempt=0;attempt<3;attempt++){
                long before=frames.revision();JSONObject reply=bootstrapCall("Page.getFrameTree",new JSONObject(),born,timeout);
                if(reply.length()!=1||!(reply.opt("frameTree") instanceof JSONObject))throw new IOException("cdp_popup_source_reply_invalid");
                if(before!=frames.revision())continue;frames.initialize(reply.getJSONObject("frameTree"),before);initialized=true;break;
            }
            if(!initialized)throw new IOException("download_frame_bootstrap_changed");
            JSONObject params=new JSONObject().put("maxTotalBufferSize",0).put("maxResourceBufferSize",0).put("maxPostDataSize",0);
            if(bootstrapCall("Network.enable",params,born,timeout).length()!=0)throw new IOException("cdp_popup_source_reply_invalid");remaining(born,timeout);ready=true;
        }catch(JSONException error){IOException invalid=new IOException("cdp_popup_source_reply_invalid");handoff.sourceFailure(invalid);throw invalid;}
        catch(IOException|RuntimeException|Error error){handoff.sourceFailure(error);throw error;}
    }
    void event(String method,JSONObject params)throws IOException {
        if("Inspector.detached".equals(method)||"Inspector.targetCrashed".equals(method)||"Inspector.targetReloadedAfterCrash".equals(method)){close();throw new IOException("cdp_popup_source_retired");}
        guard();try{
            frames.event(method,null,params);requests.event(method,null,params);
            MdoBrowserCdpSession.Events action=receiver;if(action==null)throw new IOException("cdp_popup_source_listener_missing");action.event(method,null,params);
        }catch(MdoBrowserCdpNetworkFrames.Closed ended){if(closed.get()&&ended.issuedBy(frames))throw new Retired();throw ended;}
        catch(MdoBrowserCdpNetworkRequests.Closed ended){if(closed.get()&&ended.issuedBy(requests))throw new Retired();throw ended;}
    }
    void capture()throws IOException {
        guard();try{session.retainSourceRequest(attachment,frames,requests,root);}
        catch(MdoBrowserCdpNetworkFrames.Closed ended){if(closed.get()&&ended.issuedBy(frames))throw new Retired();throw ended;}
        catch(MdoBrowserCdpNetworkRequests.Closed ended){if(closed.get()&&ended.issuedBy(requests))throw new Retired();throw ended;}
    }
    public MdoBrowserCdpSession.AttachedResponse next()throws IOException {
        guard();if(!ready)throw new IOException("cdp_popup_source_bootstrap_required");
        if(!attachment.resumeConfirmed())return null;
        MdoBrowserCdpSession.AttachedResponse response=session.nextSourceResponse(attachment,scope,handoff::draining);if(response!=null)handoff.admitNetwork();return response;
    }
    public MdoBrowserCdpSession.Offer retain(MdoBrowserCdpSession.AttachedResponse response)throws IOException {
        guard();if(response==null||!session.ownsAttachedResponse(attachment,response))throw new IOException("cdp_attach_response_owner_invalid");return response.sourceOffer();
    }
    void downloads(MdoBrowserCdpDownloads.Connection next)throws IOException {
        guard();if(!ready||next==null)throw new IOException("cdp_popup_source_bootstrap_required");if(connection!=null&&connection!=next)throw new IOException("cdp_controller_busy");next.attach(handoff);connection=next;
    }
    boolean turn(int timeout)throws IOException {guard();return connection!=null&&connection.attachedTurn(handoff,this,timeout);}
    // Only fixed native navigation proof reads are added. The original Fetch
    // protection remains untouched; no Runtime, world, DOM or caller RPC route.
    public JSONObject call(String method,JSONObject params,int timeout)throws IOException {
        guard();if(params==null||params.length()!=0||!"Page.getFrameTree".equals(method)&&!"Page.getNavigationHistory".equals(method))throw new IOException("cdp_network_scope_invalid");
        navigationCurrent();JSONObject reply=session.callAttachedDocument(attachment,scope,handoff::draining,method,params,null,timeout,false);navigationCurrent();return reply;
    }
    public JSONObject call(String method,JSONObject params,String child,int timeout,boolean effect)throws IOException {if(child!=null||effect){guard();throw new IOException("cdp_network_scope_invalid");}return call(method,params,timeout);}
    public boolean poll(int timeout)throws IOException {guard();boolean progress=session.poll(timeout);guard();return progress;}
    public boolean poll(int receive,int timeout)throws IOException {guard();boolean progress=session.poll(receive,timeout);guard();return progress;}
    public boolean idle()throws IOException {guard();return session.idle();}
    @Override public void close(){if(closed.compareAndSet(false,true)){ready=false;receiver=null;frames.close();requests.close();MdoBrowserCdpDownloads.Connection original=connection;connection=null;if(original!=null)original.disconnect();handoff.close();}}
}
