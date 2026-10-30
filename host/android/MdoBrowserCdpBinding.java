package org.xserver.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Private worker binding to an exact native UI marker. No URL/title/geometry,
 * supplied debugger address or caller-picked target is an ownership proof.
 * Unbound candidates receive only context metadata/lexical-name enumeration;
 * the random marker name is never sent to a nonmatching candidate. */
final class MdoBrowserCdpBinding implements AutoCloseable {
    interface Targets {List<String> discover(int timeoutMs) throws IOException;}
    interface Peer extends AutoCloseable {
        JSONObject call(String method,JSONObject params,int timeoutMs) throws IOException;
        default JSONObject call(String method,JSONObject params,String child,int timeoutMs,boolean effect)throws IOException {
            if(child!=null||effect)throw new IOException("cdp_session_routing_unsupported");return call(method,params,timeoutMs);
        }
        default void admitChild(String child)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default void retireChild(String child)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default boolean poll(int timeoutMs)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default boolean poll(int receiveMs,int timeoutMs)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default boolean idle()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default MdoBrowserCdpSession.ControlReceipt continuePausedRequest()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default MdoBrowserCdpSession.ControlReceipt abortPausedRequest()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default MdoBrowserCdpResponse pausedResponse()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default MdoBrowserCdpSession.Offer retainPausedResponse()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default int pendingOffers()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default void cancelTransfers()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default MdoBrowserCdpSession.Body takePausedBody(long limit,int timeout)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default MdoBrowserCdpSession.BodyChunk readBody(MdoBrowserCdpSession.Body body,int timeout)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default void cancelBody(MdoBrowserCdpSession.Body body,int timeout)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default int pendingBodies()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default void requestBodyRead(MdoBrowserCdpSession.Body body)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default MdoBrowserCdpSession.BodyChunk takeBodyChunk(MdoBrowserCdpSession.Body body)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default boolean serviceBodies(int timeout)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default int pendingControls()throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        default MdoBrowserCdpSession.Navigation navigateRoot(String url,String frame,int timeout)throws IOException {throw new IOException("cdp_session_routing_unsupported");}
        @Override void close();
    }
    interface Factory {Peer open(String target,int timeoutMs,java.util.function.BooleanSupplier current,MdoBrowserCdpSession.Events events) throws IOException;}
    private static final Factory NATIVE=(target,timeout,current,events)->{
        MdoBrowserCdpSession session=MdoBrowserCdpSession.open(target,timeout,current,events);
        try {return new Peer(){
                public JSONObject call(String method,JSONObject params,int wait) throws IOException{return session.call(method,params,null,wait,false);}
                public JSONObject call(String method,JSONObject params,String child,int wait,boolean effect)throws IOException{return session.call(method,params,child,wait,effect);}
                public void admitChild(String child)throws IOException{session.admitChild(child);}
                public void retireChild(String child)throws IOException{session.retireChild(child);}
                public boolean poll(int wait)throws IOException{return session.poll(wait);}
                public boolean poll(int receive,int wait)throws IOException{return session.poll(receive,wait);}
                public boolean idle()throws IOException{return session.idle();}
                public MdoBrowserCdpSession.ControlReceipt continuePausedRequest()throws IOException{return session.continuePausedRequest();}
                public MdoBrowserCdpSession.ControlReceipt abortPausedRequest()throws IOException{return session.abortPausedRequest();}
                public MdoBrowserCdpResponse pausedResponse()throws IOException{return session.pausedResponse();}
                public MdoBrowserCdpSession.Offer retainPausedResponse()throws IOException{return session.retainPausedResponse();}
                public int pendingOffers()throws IOException{return session.pendingOffers();}
                public void cancelTransfers()throws IOException{session.cancelTransfers();}
                public MdoBrowserCdpSession.Body takePausedBody(long limit,int timeout)throws IOException{return session.takePausedBody(limit,timeout);}
                public MdoBrowserCdpSession.BodyChunk readBody(MdoBrowserCdpSession.Body body,int timeout)throws IOException{return session.readBody(body,timeout);}
                public void cancelBody(MdoBrowserCdpSession.Body body,int timeout)throws IOException{session.cancelBody(body,timeout);}
                public int pendingBodies()throws IOException{return session.pendingBodies();}
                public void requestBodyRead(MdoBrowserCdpSession.Body body)throws IOException{session.requestBodyRead(body);}
                public MdoBrowserCdpSession.BodyChunk takeBodyChunk(MdoBrowserCdpSession.Body body)throws IOException{return session.takeBodyChunk(body);}
                public boolean serviceBodies(int timeout)throws IOException{return session.serviceBodies(timeout);}
                public int pendingControls()throws IOException{return session.pendingControls();}
                public MdoBrowserCdpSession.Navigation navigateRoot(String url,String frame,int timeout)throws IOException{return session.navigateRoot(url,frame,timeout);}
                public void close(){session.close();}
            };
        }catch(RuntimeException|Error failure){session.close();throw failure;}
    };
    static List<String> targets(int timeoutMs) throws IOException {
        return parseTargets(MdoBrowserCdpNative.discover(timeoutMs));
    }
    static List<String> parseTargets(byte[] bytes) throws IOException {
        if(bytes==null)throw problem("cdp_binding_discovery_invalid");
        try {
            MdoBrowserCdpNative.validate(bytes,MdoBrowserCdpJson.SEND_MAX);
            JSONArray rows=new JSONArray(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            if(rows.length()>64)throw problem("cdp_binding_target_limit");
            List<String> ids=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);Object type=row.get("type");
                if(!(type instanceof String))throw problem("cdp_binding_discovery_invalid");
                if(!"page".equals(type))continue;
                Object id=row.get("id");
                if(!(id instanceof String)||!MdoBrowserCdpJson.token((String)id)||!seen.add((String)id))throw problem("cdp_binding_discovery_invalid");
                ids.add((String)id);
            }
            return ids;
        } catch(JSONException failure){throw problem("cdp_binding_discovery_invalid");}
        finally {Arrays.fill(bytes,(byte)0);}
    }
    private static IOException problem(String code){return new IOException(code);}
    private static boolean identity(Object value) {
        if(!(value instanceof String)||((String)value).isEmpty()||((String)value).length()>128)return false;
        for(char c:((String)value).toCharArray())if(!((c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||"_.:-".indexOf(c)>=0))return false;
        return true;
    }
    private static final class Context {
        final int id;final String unique,frame;String loader;
        Context(int id,String unique,String frame){this.id=id;this.unique=unique;this.frame=frame;}
    }
    private static final class Contexts implements MdoBrowserCdpSession.Events {
        final Map<Integer,Context> contexts=new HashMap<>();final Set<Integer> seen=new HashSet<>();
        final Map<Integer,String> identities=new HashMap<>();
        volatile boolean dirty;boolean unsupported,network;Context armed;volatile MdoBrowserCdpSession.Events forward;
        public void event(String method,String session,JSONObject params) throws IOException {
            try {
                if(network){
                    // Ownership was proved before promotion on the original
                    // connection. New document metadata grants no frame/world
                    // authority and must not accumulate over a view lifetime.
                    if("Inspector.detached".equals(method)||"Inspector.targetCrashed".equals(method)||"Inspector.targetReloadedAfterCrash".equals(method))dirty=true;
                    MdoBrowserCdpSession.Events receiver=forward;if(receiver!=null)receiver.event(method,session,params);return;
                }
                if(session!=null){MdoBrowserCdpSession.Events childReceiver=forward;
                    if(childReceiver==null)throw problem("cdp_binding_context_invalid");childReceiver.event(method,session,params);return;}
                if("Runtime.executionContextCreated".equals(method)) {
                    JSONObject value=params.getJSONObject("context");Object number=value.get("id");
                    if(!(number instanceof Integer)||((Integer)number)<=0||!seen.add((Integer)number)||seen.size()>256)throw problem("cdp_binding_context_invalid");
                    identities.put((Integer)number,identity(value.opt("uniqueId"))?(String)value.opt("uniqueId"):null);
                    JSONObject aux=value.optJSONObject("auxData");
                    if(aux!=null&&Boolean.TRUE.equals(aux.opt("isDefault"))) {
                        Object unique=value.opt("uniqueId"),frame=aux.opt("frameId");
                        if(!identity(unique)||!identity(frame)){unsupported=true;throw problem("cdp_binding_context_unsupported");}
                        if(contexts.size()>=64)throw problem("cdp_binding_context_limit");
                        Context context=new Context((Integer)number,(String)unique,(String)frame);contexts.put(context.id,context);
                        if(armed!=null&&armed.frame.equals(context.frame))dirty=true;
                    }
                } else if("Runtime.executionContextDestroyed".equals(method)) {
                    int id=params.getInt("executionContextId");contexts.remove(id);identities.remove(id);if(armed!=null&&armed.id==id)dirty=true;
                } else if("Runtime.executionContextsCleared".equals(method)) {contexts.clear();identities.clear();if(armed!=null)dirty=true;}
                else if("Inspector.detached".equals(method))dirty=true;
                MdoBrowserCdpSession.Events receiver=forward;if(receiver!=null)receiver.event(method,session,params);
            } catch(JSONException failure){throw problem("cdp_binding_context_invalid");}
        }
        Context root(String frame) throws IOException {
            Context root=null;for(Context context:contexts.values())if(frame.equals(context.frame)) {
                if(root!=null)throw problem("cdp_binding_context_invalid");root=context;
            }
            if(root==null)throw problem("cdp_binding_context_unsupported");armed=root;return root;
        }
        void network(){network=true;armed=null;contexts.clear();identities.clear();seen.clear();}
    }
    private final Peer peer;private final MdoBrowserCdpMarker.Lease lease;private final Contexts contexts;
    private final Thread owner=Thread.currentThread();private boolean controllerInstalled;private MdoBrowserCdpFrames frames;
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean cleanupFailed=new java.util.concurrent.atomic.AtomicBoolean();
    final String rootFrame,rootLoader,uniqueContext;final int contextId;
    // Fresh namespace per binding. Specific names belong to native frame/
    // loader proofs; old or possibly privileged worlds are never adopted.
    final String worldName="mdo.browser-use.frames.v1."+java.util.UUID.randomUUID();
    private MdoBrowserCdpBinding(Peer peer,MdoBrowserCdpMarker.Lease lease,Contexts contexts,Context root) {
        this.peer=peer;this.lease=lease;this.contexts=contexts;rootFrame=root.frame;rootLoader=root.loader;uniqueContext=root.unique;contextId=root.id;
    }
    static MdoBrowserCdpBinding bind(MdoBrowserCdpMarker marker,int timeoutMs,MdoBrowserCdpSession.Events events) throws IOException {
        return bind(marker,timeoutMs,events,MdoBrowserCdpBinding::targets,NATIVE);
    }
    static MdoBrowserCdpBinding bind(MdoBrowserCdpMarker marker,int timeoutMs,MdoBrowserCdpSession.Events events,Targets targets,Factory factory) throws IOException {
        return bind(marker,timeoutMs,events,targets,factory,false);
    }
    private static MdoBrowserCdpBinding bind(MdoBrowserCdpMarker marker,int timeoutMs,MdoBrowserCdpSession.Events events,Targets targets,Factory factory,boolean network) throws IOException {
        if(marker==null||events==null||targets==null||factory==null||timeoutMs<100||timeoutMs>15000)throw problem("cdp_binding_arguments_invalid");
        long started=android.os.SystemClock.elapsedRealtime();MdoBrowserCdpMarker.Lease lease=marker.lease();
        if(lease.physicalNetwork()!=network)throw problem("cdp_network_scope_invalid");
        if(lease.busy())throw problem("cdp_binding_busy");
        MdoBrowserCdpMarker.Ticket ticket=null;Peer peer=null;boolean success=false,cleanupAttempted=false;
        Context cleanupRoot=null;Contexts cleanupOwner=null;
        try {
            List<String> candidates=targets.discover(remaining(started,timeoutMs,lease));
            if(candidates.isEmpty()||candidates.size()>64||new HashSet<>(candidates).size()!=candidates.size())throw problem("cdp_binding_targets_unavailable");
            for(String id:candidates)if(!MdoBrowserCdpJson.token(id))throw problem("cdp_binding_discovery_invalid");
            remaining(started,timeoutMs,lease);ticket=marker.request();
            if(!ticket.await(remaining(started,timeoutMs,lease))) {
                remaining(started,timeoutMs,lease);
                throw problem(ticket.pending()?"cdp_binding_timeout":ticket.failure());
            }
            boolean unsupported=false;
            for(String id:candidates) {
                Contexts contexts=new Contexts();Context root;
                try {
                    peer=factory.open(id,remaining(started,timeoutMs,lease),()->lease.current()&&!contexts.dirty,contexts);
                    lease.track(peer);
                    root=match(peer,contexts,ticket,started,timeoutMs,lease);
                } catch(IOException|JSONException missing) {
                    unsupported|=contexts.unsupported||"cdp_binding_context_unsupported".equals(missing.getMessage());
                    unsupported|=missing instanceof MdoBrowserCdpSession.Failure&&((MdoBrowserCdpSession.Failure)missing).protocolCode==-32601;
                    if(peer!=null){lease.untrack(peer);peer.close();peer=null;}
                    remaining(started,timeoutMs,lease);continue;
                }
                if(root==null){lease.untrack(peer);peer.close();peer=null;continue;}
                // Prove the provider honors uniqueContextId before revealing the
                // random identifier in an expression. Unknown parameters must
                // never silently retarget to the current default document.
                boolean rejectsUnique=false;
                try {peer.call("Runtime.evaluate",evaluation("void 0","",true,remaining(started,timeoutMs,lease)),remaining(started,timeoutMs,lease));}
                catch(MdoBrowserCdpSession.Failure failure){rejectsUnique=failure.uniqueContextRejected;}
                if(!rejectsUnique)throw problem("cdp_binding_unique_context_unsupported");
                JSONObject check=peer.call("Runtime.evaluate",evaluation("void 0",root.unique,true,remaining(started,timeoutMs,lease)),remaining(started,timeoutMs,lease));
                if(check.has("exceptionDetails")||!"undefined".equals(check.getJSONObject("result").opt("type")))throw problem("cdp_binding_context_invalid");
                cleanupRoot=root;cleanupOwner=contexts;
                JSONObject proof=peer.call("Runtime.evaluate",evaluation(ticket.name,root.unique,true,remaining(started,timeoutMs,lease)),remaining(started,timeoutMs,lease));
                JSONObject value=proof.getJSONObject("result");
                if(proof.has("exceptionDetails")||!"string".equals(value.opt("type"))||!ticket.value.equals(value.opt("value")))throw problem("cdp_binding_proof_invalid");
                cleanupAttempted=true;
                JSONObject clean=peer.call("Runtime.evaluate",evaluation(ticket.name+"=void 0",root.unique,false,remaining(started,timeoutMs,lease)),remaining(started,timeoutMs,lease));
                if(clean.has("exceptionDetails")||!"undefined".equals(clean.getJSONObject("result").opt("type")))throw problem("cdp_binding_cleanup_failed");
                JSONObject finalFrame=frame(peer,started,timeoutMs,lease);
                if(!root.frame.equals(finalFrame.opt("id"))||!root.loader.equals(finalFrame.opt("loaderId")))throw problem("cdp_binding_context_changed");
                if(contexts.dirty||!ticket.confirmed())throw problem("cdp_binding_owner_changed");
                remaining(started,timeoutMs,lease);contexts.forward=events;
                MdoBrowserCdpBinding bound=new MdoBrowserCdpBinding(peer,lease,contexts,root);
                lease.transfer(peer,bound);
                if(network)contexts.network();
                ticket.close();success=true;return bound;
            }
            throw problem(unsupported?"cdp_binding_context_unsupported":"cdp_binding_target_not_found");
        } catch(JSONException failure){throw problem("cdp_binding_packet_invalid");}
        catch(RuntimeException|Error failure){throw problem("cdp_binding_failed");}
        finally {
            if(!success){
                if(peer!=null){
                    // One bounded attempt on this exact proven context only.
                    // Never resend uncertain cleanup or target a default view.
                    if(cleanupRoot!=null&&!cleanupAttempted&&lease.current()&&!cleanupOwner.dirty)try {
                        int left=remaining(started,timeoutMs,lease);
                        peer.call("Runtime.evaluate",evaluation(ticket.name+"=void 0",cleanupRoot.unique,false,left),left);
                    }catch(IOException|JSONException|RuntimeException|Error ignored){}
                    lease.untrack(peer);peer.close();
                }
                if(ticket!=null)marker.discard(ticket);
            }
        }
    }
    /** A separate physical-view proof, never a promotion of an ordinary
     * document binding. This private facade exposes no page/frame RPC. */
    static Network bindNetwork(MdoBrowserCdpMarker marker,int timeoutMs,MdoBrowserCdpSession.Events events)throws IOException {
        return bindNetwork(marker,timeoutMs,events,MdoBrowserCdpBinding::targets,NATIVE);
    }
    static Network bindNetwork(MdoBrowserCdpMarker marker,int timeoutMs,MdoBrowserCdpSession.Events events,Targets targets,Factory factory)throws IOException {
        long start=android.os.SystemClock.elapsedRealtime();MdoBrowserCdpBinding bound=bind(marker,timeoutMs,events,targets,factory,true);
        try{
            // Runtime metadata is only needed for the initial exact target
            // proof. Stop its stream before a long-lived network connection.
            bound.peer.call("Runtime.disable",new JSONObject(),remaining(start,timeoutMs,bound.lease));
            bound.current();return new Network(bound);
        }catch(IOException|RuntimeException|Error failure){bound.close();throw failure;}
    }
    static final class Network implements AutoCloseable {
        static final class Navigation {
            private final Network owner;private final long revision;private MdoBrowserCdpSession.Navigation original;
            private Navigation(Network owner,long revision){this.owner=owner;this.revision=revision;}
            MdoBrowserCdpSession.NavigationResult result(){return original==null?null:original.result();}
            boolean pending(){return result()==null;}
        }
        private final MdoBrowserCdpBinding bound;private final MdoBrowserCdpNetworkFrames frames;private boolean enableAttempted,responsesAttempted,enabled,retiring,disableAttempted,responsesDisabled;
        private Network(MdoBrowserCdpBinding bound)throws IOException {
            this.bound=bound;frames=new MdoBrowserCdpNetworkFrames(bound.rootFrame,bound.rootLoader);bound.observe(frames);bound.lease.transfer(bound,this);
        }
        void enableResponses(int timeoutMs)throws IOException {
            bound.current();if(enableAttempted)throw problem("cdp_network_enable_replayed");
            if(timeoutMs<10||timeoutMs>5000)throw problem("cdp_arguments_invalid");
            enableAttempted=true;
            try{
                long started=android.os.SystemClock.elapsedRealtime();
                bound.call("Page.enable",new JSONObject(),null,remaining(started,timeoutMs,bound.lease),false);
                boolean proved=false;
                for(int attempt=0;attempt<8;attempt++){
                    long before=frames.revision();JSONObject tree=bound.call("Page.getFrameTree",new JSONObject(),null,remaining(started,timeoutMs,bound.lease),false).getJSONObject("frameTree");
                    if(frames.revision()!=before)continue;frames.initialize(tree,before);proved=true;break;
                }
                if(!proved)throw problem("download_frame_bootstrap_changed");
                JSONObject pattern=new JSONObject().put("urlPattern","*").put("requestStage","Response");
                JSONObject params=new JSONObject().put("patterns",new JSONArray().put(pattern)).put("handleAuthRequests",false);
                int remaining=remaining(started,timeoutMs,bound.lease);responsesAttempted=true;
                bound.call("Fetch.enable",params,null,remaining,true);bound.current();enabled=true;
            }catch(JSONException failure){close();throw problem("cdp_network_arguments_invalid");}
            catch(IOException|RuntimeException|Error failure){close();throw failure;}
        }
        boolean poll(int timeoutMs)throws IOException {active();return bound.poll(timeoutMs);}
        boolean poll(int receiveMs,int timeoutMs)throws IOException {active();return bound.peer.poll(receiveMs,timeoutMs);}
        boolean idle()throws IOException {active();return bound.peer.idle();}
        private void active()throws IOException {bound.current();if(!enabled)throw problem("cdp_network_not_enabled");}
        private void control()throws IOException {bound.current();if(!responsesAttempted)throw problem("cdp_network_not_enabled");}
        MdoBrowserCdpSession.ControlReceipt continuePausedRequest()throws IOException {control();return bound.peer.continuePausedRequest();}
        MdoBrowserCdpSession.ControlReceipt abortPausedRequest()throws IOException {control();return bound.peer.abortPausedRequest();}
        MdoBrowserCdpResponse pausedResponse()throws IOException {control();return bound.peer.pausedResponse();}
        MdoBrowserCdpNetworkFrames.Origin pausedOrigin()throws IOException {control();return frames.origin(bound.peer.pausedResponse().frame);}
        MdoBrowserCdpSession.Offer retainPausedResponse()throws IOException {control();if(retiring)throw problem("cdp_network_retiring");pausedOrigin();return bound.peer.retainPausedResponse();}
        int pendingOffers()throws IOException {bound.current();return bound.peer.pendingOffers();}
        MdoBrowserCdpSession.Body takePausedBody(long maxBytes,int timeoutMs)throws IOException {control();if(retiring)throw problem("cdp_network_retiring");pausedOrigin();return bound.peer.takePausedBody(maxBytes,timeoutMs);}
        MdoBrowserCdpSession.BodyChunk readBody(MdoBrowserCdpSession.Body body,int timeoutMs)throws IOException {active();return bound.peer.readBody(body,timeoutMs);}
        void cancelBody(MdoBrowserCdpSession.Body body,int timeoutMs)throws IOException {active();bound.peer.cancelBody(body,timeoutMs);}
        int pendingBodies()throws IOException {bound.current();return bound.peer.pendingBodies();}
        void requestBodyRead(MdoBrowserCdpSession.Body body)throws IOException {active();if(retiring)throw problem("cdp_network_retiring");bound.peer.requestBodyRead(body);}
        MdoBrowserCdpSession.BodyChunk takeBodyChunk(MdoBrowserCdpSession.Body body)throws IOException {active();return bound.peer.takeBodyChunk(body);}
        boolean serviceBodies(int sendTimeoutMs)throws IOException {active();return bound.peer.serviceBodies(sendTimeoutMs);}
        int pendingControls()throws IOException {bound.current();return bound.peer.pendingControls();}
        Navigation navigateRoot(String url,int timeoutMs)throws IOException {
            active();if(retiring)throw problem("cdp_network_retiring");frames.origin(bound.rootFrame);
            Navigation ticket=new Navigation(this,frames.revision());ticket.original=bound.peer.navigateRoot(url,bound.rootFrame,timeoutMs);return ticket;
        }
        MdoBrowserCdpNetworkFrames.Origin navigationOrigin(Navigation ticket)throws IOException {
            bound.current();if(ticket==null||ticket.owner!=this)throw problem("cdp_navigation_owner_changed");
            return frames.navigationOrigin(ticket.original,ticket.revision);
        }
        boolean responsesMayHaveOccurred(){return responsesAttempted;}
        void cancelTransfers()throws IOException {active();retiring=true;bound.peer.cancelTransfers();}
        boolean transfersDrained()throws IOException {active();return bound.peer.pendingOffers()==0&&bound.peer.pendingBodies()==0&&bound.peer.pendingControls()==0;}
        void disableResponses(int timeoutMs)throws IOException {
            active();if(timeoutMs<10||timeoutMs>5000)throw problem("cdp_arguments_invalid");
            if(disableAttempted)throw problem("cdp_network_disable_replayed");
            if(!transfersDrained())throw problem("cdp_network_transfers_pending");disableAttempted=true;
            bound.call("Fetch.disable",new JSONObject(),null,timeoutMs,true);bound.current();responsesDisabled=true;enabled=false;
        }
        void drainDisabledControls(int receiveMs,int timeoutMs)throws IOException {
            bound.current();if(!responsesDisabled)throw problem("cdp_network_disable_unconfirmed");bound.peer.poll(receiveMs,timeoutMs);
        }
        boolean responsesDisabled(){return responsesDisabled;}
        boolean cleanupConfirmed(){return bound.closed.get()&&!bound.cleanupFailed.get();}
        @Override public void close(){frames.close();bound.lease.untrack(this);bound.close();}
    }
    private static Context match(Peer peer,Contexts contexts,MdoBrowserCdpMarker.Ticket ticket,long started,int timeout,MdoBrowserCdpMarker.Lease lease) throws IOException,JSONException {
        peer.call("Runtime.enable",new JSONObject(),remaining(started,timeout,lease));
        JSONObject snapshot=frame(peer,started,timeout,lease);Object frame=snapshot.get("id"),loader=snapshot.get("loaderId");
        if(!identity(frame)||!(loader instanceof String)||((String)loader).length()>128)throw problem("cdp_binding_context_invalid");
        Context root=contexts.root((String)frame);root.loader=(String)loader;
        JSONObject names=peer.call("Runtime.globalLexicalScopeNames",new JSONObject().put("executionContextId",root.id),remaining(started,timeout,lease));
        JSONArray entries=names.getJSONArray("names");if(entries.length()>8192)throw problem("cdp_binding_name_limit");
        int matches=0;
        for(int i=0;i<entries.length();i++){Object name=entries.get(i);if(!(name instanceof String))throw problem("cdp_binding_names_invalid");if(ticket.name.equals(name))matches++;}
        if(matches>1)throw problem("cdp_binding_names_invalid");return matches==1?root:null;
    }
    private static JSONObject frame(Peer peer,long started,int timeout,MdoBrowserCdpMarker.Lease lease)throws IOException,JSONException {
        return peer.call("Page.getFrameTree",new JSONObject(),remaining(started,timeout,lease)).getJSONObject("frameTree").getJSONObject("frame");
    }
    private static JSONObject evaluation(String expression,String unique,boolean readOnly,int timeoutMs) throws JSONException {
        return new JSONObject().put("expression",expression).put("uniqueContextId",unique).put("returnByValue",true)
            .put("silent",true).put("throwOnSideEffect",readOnly).put("timeout",timeoutMs);
    }
    private static int remaining(long start,int timeout,MdoBrowserCdpMarker.Lease lease) throws IOException {
        if(!lease.current())throw problem("cdp_binding_owner_changed");long used=android.os.SystemClock.elapsedRealtime()-start;
        if(used<0||used>timeout-10)throw problem("cdp_binding_timeout");return (int)Math.min(5000,timeout-used);
    }
    JSONObject call(String method,JSONObject params,int timeoutMs) throws IOException {
        current();
        return peer.call(method,params,timeoutMs);
    }
    void current()throws IOException {
        if(Thread.currentThread()!=owner)throw problem("cdp_wrong_worker");
        if(closed.get()||!lease.current()||contexts.dirty){close();throw problem("cdp_binding_owner_changed");}
    }
    JSONObject call(String method,JSONObject params,String child,int timeoutMs,boolean effect)throws IOException {
        current();return peer.call(method,params,child,timeoutMs,effect);
    }
    void admitChild(String child)throws IOException {current();peer.admitChild(child);}
    void retireChild(String child)throws IOException {current();peer.retireChild(child);}
    boolean poll(int timeoutMs)throws IOException {current();return peer.poll(timeoutMs);}
    boolean reserveWorldName()throws IOException {current();return lease.worldName();}
    Map<Integer,String> contextIdentities()throws IOException {current();return new HashMap<>(contexts.identities);}
    void observe(MdoBrowserCdpSession.Events controller)throws IOException {
        current();if(controller==null||controllerInstalled)throw problem("cdp_controller_busy");controllerInstalled=true;
        MdoBrowserCdpSession.Events original=contexts.forward;
        contexts.forward=(method,session,params)->{if(original!=null)original.event(method,session,params);controller.event(method,session,params);};
    }
    MdoBrowserCdpFrames frames()throws IOException {current();if(frames==null)frames=new MdoBrowserCdpFrames(this);return frames;}
    @Override public void close(){if(closed.compareAndSet(false,true)){
        contexts.forward=null;contexts.dirty=true;lease.untrack(this);lease.untrack(peer);
        try{peer.close();}catch(RuntimeException|Error failure){cleanupFailed.set(true);throw failure;}
    }}
}
