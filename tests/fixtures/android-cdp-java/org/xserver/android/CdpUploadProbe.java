package org.xserver.android;

import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Production worker/graph/unique worlds/parents/JNI; native DOM and Android
 * UI peers are synthetic. No native callback is joined or URI data delivered. */
public final class CdpUploadProbe {
    private static final AtomicInteger checks=new AtomicInteger();
    private static final String DOC="d-00000000-0000-4000-8000-000000000001",NONCE="application/x-mdo-browseruse-00000000000000000000000000000001";
    private interface Checked {void run()throws Exception;}
    private interface Operation {JSONObject run(MdoBrowserCdpUpload upload,MdoBrowserCdpBinding binding,MdoBrowserCdpWorker.Deadline deadline)throws Exception;}
    private static void check(boolean value){if(!value)throw new AssertionError("Upload worker check "+(checks.get()+1));checks.incrementAndGet();}
    private static void rejects(String code,Checked task)throws Exception {
        try{task.run();throw new AssertionError("Missing upload worker rejection");}catch(IOException error){
            if(code!=null&&!code.equals(error.getMessage()))throw new AssertionError("Expected "+code+" but got "+(error.getMessage()!=null&&error.getMessage().matches("cdp_[a-z_]{1,80}")?error.getMessage():"untyped native error"));check(true);
        }
    }
    private static MdoBrowserUploadStore.Spec[] manifest()throws Exception {
        return new MdoBrowserUploadStore.Spec[]{new MdoBrowserUploadStore.Spec("native-\u5185\u5bb9 #% +.bin",131075,new String(new char[64]).replace('\0','a')),
            new MdoBrowserUploadStore.Spec("empty.bin",0,new String(new char[64]).replace('\0','b'))};
    }
    static final class Case implements AutoCloseable,CdpBindingProbe.Factory.Extra {
        final CdpFileInputProbe.Case peer;final AtomicLong time=new AtomicLong(1000);
        final List<String> calls=new ArrayList<>();final MdoBrowserCdpUpload upload;
        final java.util.Set<String> events=new java.util.HashSet<>();
        String failure="",hookAt="",nativeKey,source,realm;boolean selected=true,opened,assigned,cleaned;
        int installs,prepared,opens,statuses,commits,disposals,disabled;boolean failureCleanup;
        Runnable onOpen,onCommit;
        Case(String frame)throws Exception {this(frame,true);}
        Case(String frame,boolean initialize)throws Exception {
            peer=new CdpFileInputProbe.Case(initialize);
            peer.peer.host.plan.extra=this;peer.peer.server.worlds.evaluation=this::evaluate;
            if(!initialize){upload=null;return;}
            JSONObject request=peer.request(frame);MdoBrowserCdpFileInput target=MdoBrowserCdpFileInput.parse(request);MdoBrowserUploadStore.Spec[] specs=manifest();
            upload=new MdoBrowserCdpUpload(target,specs,120000,time::get);
            request.put("ref","e8");specs[0]=null; // Neither caller mutation changes the captured target/manifest.
        }
        private static JSONObject arguments(String expression)throws Exception {
            int start=expression.lastIndexOf("})(");check(start>=0&&expression.endsWith(")"));return new JSONObject(expression.substring(start+3,expression.length()-1));
        }
        private JSONObject metadata(String phase)throws Exception {
            return new JSONObject().put("ok",true).put("key",nativeKey).put("document",DOC).put("ref","e7").put("phase",phase)
                .put("opened",opened).put("dispatched",assigned).put("events_trusted",false).put("input_method","native_picker_dom_filelist").put("remaining_ms",10000);
        }
        private Object evaluate(CdpWorldsProbe.Context context,String expression)throws Exception {
            if(expression.equals("("+MdoBrowserCdpUploadScripts.INSTALL+",true)")){
                installs++;calls.add("install");if(failure.equals("install"))return false;return true;
            }
            JSONObject params=arguments(expression);String action;
            if(expression.contains("?b.prepare(p):"))action="prepare";
            else if(expression.contains("?b.open(p):"))action="open";
            else if(expression.contains("?b.status(p):"))action="status";
            else if(expression.contains("?b.commit(p):"))action="commit";
            else if(expression.contains("?b.dispose(p):"))action="dispose";
            else throw new AssertionError("Unexpected native upload expression");
            calls.add(action);JSONObject result;
            if(action.equals("prepare")){
                prepared++;nativeKey=params.getString("key");check(nativeKey.matches("up-[a-f0-9-]{36}"));
                check(params.length()==5&&params.getString("document").equals(DOC)&&params.getString("ref").equals("e7"));
                JSONArray files=params.getJSONArray("files");check(files.length()==2&&files.getJSONObject(0).length()==2&&files.getJSONObject(1).length()==2);
                check(files.getJSONObject(0).getString("name").equals(manifest()[0].name)&&files.getJSONObject(0).getLong("size")==131075&&files.getJSONObject(1).getLong("size")==0);
                check(params.getInt("timeout_ms")>0&&params.getInt("timeout_ms")<=120000&&!expression.contains(manifest()[0].sha256));
                result=metadata("prepared").put("nonce",NONCE);
                if(failure.equals("nonce"))result.put("nonce",NONCE+"x");if(failure.equals("ref"))result.put("ref","e8");
                if(failure.equals("prepare_failure"))result=new JSONObject().put("ok",false).put("error",new JSONObject().put("code","upload_target_changed"));
                if(failure.equals("prepare_multiple_changed"))peer.failure="single";
            }else{
                if(action.equals("dispose")&&nativeKey==null)return new JSONObject().put("ok",false).put("error",new JSONObject().put("code","upload_picker_changed"));
                check(params.length()==1&&params.getString("key").equals(nativeKey));
                if(action.equals("open")){
                    opens++;check(events.isEmpty()&&!opened);opened=true;result=metadata("opening");if(onOpen!=null)onOpen.run();
                    if(failure.equals("activation"))result=new JSONObject().put("ok",false).put("error",new JSONObject().put("code","upload_picker_activation_required"));
                }else if(action.equals("status")){statuses++;result=metadata(selected?"selected":"opening").put("selected",selected);}
                else if(action.equals("commit")){
                    commits++;check(opened&&selected&&!assigned);assigned=true;if(onCommit!=null)onCommit.run();boolean complete=!failure.equals("partial");
                    result=metadata("committed").put("accepted",true).put("completed",complete).put("verified",complete)
                        .put("verification","file_list_handoff").put("outcome",complete?"file_list_confirmed":"target_changed_after_input");
                    if(failure.equals("trusted"))result.put("events_trusted",true);
                    if(failure.equals("unknown_outcome"))result.put("outcome","private unreviewed page detail");
                    if(failure.equals("commit_failure"))result=new JSONObject().put("ok",false).put("dispatched",true).put("error",new JSONObject().put("code","upload_assignment_uncertain"));
                }else{
                    disposals++;cleaned=true;result=new JSONObject().put("ok",true).put("key",nativeKey).put("disposed",true).put("cleanup_confirmed",true).put("dispatched",assigned);
                    if(failure.equals("cleanup"))result.put("cleanup_confirmed",false);
                }
            }
            if(action.equals(hookAt)){
                if(failure.equals("timeout"))peer.peer.host.clockOffset.addAndGet(20000);
                if(failure.equals("cancel"))peer.peer.host.marker.close();
                if(failure.equals("lease_expired"))time.set(121001);
                if(failure.equals("clock_regression"))time.set(999);
            }
            return result;
        }
        @Override public boolean send(JSONObject request,CdpBindingProbe.Wire wire)throws Exception {
            String method=request.getString("method");JSONObject params=request.getJSONObject("params");
            if(method.equals("Page.enable")&&Boolean.FALSE.equals(params.opt("enableFileChooserOpenedEvent"))){disabled++;calls.add("disable_events");}
            if(method.equals("Page.enable")&&params.has("enableFileChooserOpenedEvent")){
                String source=request.optString("sessionId","");if(params.getBoolean("enableFileChooserOpenedEvent"))events.add(source);else events.remove(source);
            }
            if(method.equals("Runtime.evaluate")){
                String expression=params.getString("expression");
                boolean uploadExpression=expression.equals("("+MdoBrowserCdpUploadScripts.INSTALL+",true)")||expression.startsWith("(p=>{const b=globalThis.__mdo_browser_use_upload_picker_v1;");
                if(uploadExpression){
                    String currentSource=request.optString("sessionId",null),unique=params.getString("uniqueContextId");
                    check(!params.has("contextId")&&!params.has("executionContextId")&&!unique.isEmpty());
                    CdpWorldsProbe.Context context=null;for(CdpWorldsProbe.Context row:peer.peer.server.worlds.contexts.values())if(unique.equals(row.unique)&&Objects.equals(currentSource,row.source))context=row;
                    check(context!=null&&context.frame.equals(peer.raw));
                    if(realm==null){realm=unique;source=currentSource;}else check(realm.equals(unique)&&Objects.equals(source,currentSource));
                    boolean opening=expression.contains("?b.open(p):");check(opening?Boolean.TRUE.equals(params.opt("userGesture")):!params.has("userGesture"));
                    check(params.getBoolean("returnByValue")&&!params.getBoolean("awaitPromise")&&!params.getBoolean("throwOnSideEffect"));
                }else check(!params.has("userGesture"));
            }
            return peer.send(request,wire);
        }
        @Override public void closed(CdpBindingProbe.Wire wire){peer.closed(wire);}
        JSONObject run(boolean effect,Operation operation)throws Exception {
            return peer.peer.host.run(effect,(binding,deadline)->operation.run(upload,binding,deadline));
        }
        JSONObject prepare()throws Exception {return run(false,MdoBrowserCdpUpload::prepare);}
        JSONObject open()throws Exception {return run(true,MdoBrowserCdpUpload::open);}
        JSONObject status()throws Exception {return run(false,MdoBrowserCdpUpload::status);}
        JSONObject commit()throws Exception {return run(true,MdoBrowserCdpUpload::commit);}
        JSONObject dispose()throws Exception {return run(false,MdoBrowserCdpUpload::dispose);}
        void failure(String code,boolean effect,Operation operation)throws Exception {
            rejects(code,()->run(effect,(upload,binding,deadline)->{
                try{return operation.run(upload,binding,deadline);}catch(IOException error){
                    try{failureCleanup=upload.dispose(binding,deadline).getBoolean("cleanup_confirmed");}catch(IOException ignored){}
                    throw error;
                }
            }));
        }
        @Override public void close()throws Exception {peer.close();}
    }
    private static void publicValue(JSONObject value){
        check(Boolean.TRUE.equals(value.opt("ok")));
        for(String forbidden:Arrays.asList(NONCE,"nonce","sessionId","uniqueContextId","objectId","native-","frame-owned","leaf-session","up-"))check(!value.toString().contains(forbidden));
    }
    private static void fullFlow()throws Exception {
        for(String raw:Arrays.asList("frame-owned","root-local","child-frame","child-local","leaf-frame"))try(Case sample=new Case(raw)){
            sample.run(false,(upload,binding,deadline)->binding.frames().armChoosers(deadline));
            publicValue(sample.prepare());MdoBrowserCdpUpload.Ready ready=sample.upload.ready();check(ready!=null&&ready.nonce.equals(NONCE)&&ready.multiple);
            AtomicReference<Throwable> refused=new AtomicReference<>();Thread wrong=new Thread(()->{try{sample.upload.ready();}catch(Throwable error){refused.set(error);}});wrong.start();wrong.join(5000);check(!wrong.isAlive()&&refused.get() instanceof IllegalStateException);
            publicValue(sample.open());check(sample.upload.ready()==null&&sample.upload.openingMayHaveOccurred()&&!sample.upload.assignmentMayHaveOccurred());
            sample.selected=false;JSONObject status=sample.status();publicValue(status);check(!status.getBoolean("selected"));
            // A not-yet-selected input cannot dispatch assignment and remains
            // available for a bounded status check, without another open.
            sample.run(false,(upload,binding,deadline)->{rejects("cdp_upload_not_selected",()->upload.commit(binding,deadline));return new JSONObject().put("ok",true);});
            sample.selected=true;status=sample.status();publicValue(status);check(status.getBoolean("selected"));
            JSONObject committed=sample.commit();publicValue(committed);check(committed.getBoolean("completed")&&committed.getBoolean("verified")&&!committed.getBoolean("events_trusted"));
            committed.put("verified",false);publicValue(sample.upload.receipt().value());check(sample.upload.receipt().value().getBoolean("verified")&&!sample.upload.receipt().cleanupConfirmed);
            check(sample.upload.assignmentMayHaveOccurred());
            sample.run(false,(upload,binding,deadline)->{rejects("cdp_upload_operation_retired",()->upload.open(binding,deadline));rejects("cdp_upload_not_selected",()->upload.commit(binding,deadline));return new JSONObject().put("ok",true);});
            publicValue(sample.dispose());int calls=sample.calls.size();publicValue(sample.dispose());check(sample.calls.size()==calls);
            check(sample.upload.receipt().cleanupConfirmed&&sample.upload.receipt().value().getString("phase").equals("committed"));
            check(sample.installs==1&&sample.prepared==1&&sample.opens==1&&sample.statuses==2&&sample.commits==1&&sample.disposals==1&&sample.cleaned);
            check(sample.peer.peer.host.peers.size()==1&&sample.calls.indexOf("disable_events")<sample.calls.indexOf("open"));
        }
    }
    private static void preparationFailures()throws Exception {
        String[][] modes={{"install","cdp_upload_runtime_invalid"},{"nonce","cdp_upload_result_invalid"},{"ref","cdp_upload_result_invalid"},
            {"prepare_failure","cdp_upload_target_changed"},{"prepare_multiple_changed","cdp_upload_target_changed"}};
        for(String[] mode:modes)try(Case sample=new Case("leaf-frame")){
            sample.failure=mode[0];sample.failure(mode[1],false,MdoBrowserCdpUpload::prepare);
            check(sample.upload.ready()==null&&!sample.upload.openingMayHaveOccurred()&&!sample.upload.assignmentMayHaveOccurred());
            // Installation failure has not minted a broker record; cleanup is
            // unconfirmed rather than claiming an invented original record.
            if(!mode[0].equals("install"))check(sample.failureCleanup&&sample.disposals==1);
            check(sample.opens==0&&sample.commits==0);
        }
        try(Case sample=new Case("leaf-frame")){
            sample.peer.failure="blocked";sample.failure("cdp_file_input_element_disabled",false,MdoBrowserCdpUpload::prepare);check(sample.installs==0&&sample.failureCleanup);
        }
        try(Case sample=new Case("leaf-frame")){
            sample.peer.peer.server.ownerValue=(raw,value)->new JSONObject().put("ok",false).put("error",new JSONObject().put("code","element_disabled"));
            sample.failure("cdp_file_input_ancestor_blocked",false,MdoBrowserCdpUpload::prepare);check(sample.installs==0&&sample.opens==0);
        }
    }
    private static void assignmentFailures()throws Exception {
        try(Case sample=new Case("child-frame")){
            sample.prepare();sample.failure="activation";sample.failure("cdp_upload_picker_activation_required",true,MdoBrowserCdpUpload::open);
            check(sample.opens==1&&sample.commits==0&&sample.failureCleanup&&sample.upload.openingMayHaveOccurred()&&!sample.upload.assignmentMayHaveOccurred());
        }
        for(String mode:Arrays.asList("partial","trusted","unknown_outcome","commit_failure"))try(Case sample=new Case("child-local")){
            sample.prepare();sample.open();sample.status();sample.failure=mode;
            if(mode.equals("partial")){JSONObject result=sample.commit();publicValue(result);check(result.getBoolean("accepted")&&!result.getBoolean("verified")&&result.getString("outcome").equals("target_changed_after_input"));sample.dispose();}
            else sample.failure(mode.equals("commit_failure")?"cdp_upload_assignment_uncertain":"cdp_upload_result_invalid",true,MdoBrowserCdpUpload::commit);
            check(sample.upload.assignmentMayHaveOccurred()&&sample.opens==1&&sample.commits==1&&sample.disposals==1);
        }
        try(Case sample=new Case("leaf-frame")){
            sample.prepare();sample.open();sample.status();sample.commit();sample.failure="cleanup";
            sample.failure("cdp_upload_cleanup_unconfirmed",false,MdoBrowserCdpUpload::dispose);
            check(!sample.failureCleanup&&sample.disposals==1&&sample.upload.assignmentMayHaveOccurred());
        }
    }
    private static void ownershipAndDeadlines()throws Exception {
        for(String mode:Arrays.asList("lease_expired","clock_regression","timeout","cancel"))try(Case sample=new Case("leaf-frame")){
            sample.prepare();sample.failure=mode;sample.hookAt="open";
            sample.failure(mode.equals("lease_expired")?"cdp_upload_expired":mode.equals("clock_regression")?"cdp_upload_clock_changed":mode.equals("timeout")?"cdp_worker_timeout":"cdp_worker_cancelled",true,MdoBrowserCdpUpload::open);
            check(sample.opens==1&&sample.upload.openingMayHaveOccurred()&&!sample.upload.assignmentMayHaveOccurred());
            if(mode.equals("lease_expired")||mode.equals("clock_regression"))check(sample.failureCleanup&&sample.disposals==1);
        }
        for(String mode:Arrays.asList("destroy","clear","navigate","parent"))try(Case sample=new Case("leaf-frame")){
            sample.prepare();CdpWorldsProbe.Engine worlds=sample.peer.peer.server.worlds;int creations=worlds.createCalls;
            worlds.destroyDuringEval=mode.equals("destroy");worlds.clearDuringEval=mode.equals("clear");worlds.navigateDuringEval=mode.equals("navigate");worlds.parentDuringEval=mode.equals("parent");
            sample.failure(null,true,MdoBrowserCdpUpload::open);check(sample.opens==1&&sample.commits==0&&!sample.failureCleanup&&worlds.createCalls==creations);
        }
        try(Case original=new Case("leaf-frame");Case replacement=new Case("leaf-frame")){
            original.prepare();int calls=replacement.calls.size();
            rejects("cdp_upload_worker_changed",()->replacement.peer.peer.host.run(true,(binding,deadline)->original.upload.open(binding,deadline)));
            check(replacement.calls.size()==calls&&original.opens==0&&replacement.opens==0);
            check(original.upload.ready()!=null);original.open();original.status();original.commit();original.dispose();check(original.commits==1&&original.disposals==1);
        }
        try(Case sample=new Case("leaf-frame")){
            sample.prepare();int calls=sample.calls.size(),creations=sample.peer.peer.server.worlds.createCalls;
            rejects("cdp_fixture_binding_retired",()->sample.run(false,(upload,binding,deadline)->{throw new IOException("cdp_fixture_binding_retired");}));
            rejects("cdp_upload_binding_changed",sample::open);
            check(sample.calls.size()==calls&&sample.opens==0&&sample.peer.peer.server.worlds.createCalls==creations&&sample.peer.peer.host.peers.size()==2);
        }
    }
    private static void grammar()throws Exception {
        try(Case sample=new Case("frame-owned")){
            JSONObject request=sample.peer.request("frame-owned");MdoBrowserCdpFileInput target=MdoBrowserCdpFileInput.parse(request);
            rejects("cdp_upload_request_invalid",()->new MdoBrowserCdpUpload(target,null,1000));
            rejects("cdp_upload_request_invalid",()->new MdoBrowserCdpUpload(target,manifest(),0));
            rejects("cdp_upload_request_invalid",()->new MdoBrowserCdpUpload(target,manifest(),120001));
            rejects("cdp_upload_clock_invalid",()->new MdoBrowserCdpUpload(target,manifest(),1000,()->-1));
            rejects("cdp_upload_clock_invalid",()->new MdoBrowserCdpUpload(target,manifest(),1000,()->Long.MAX_VALUE));
            rejects("cdp_requires_background_thread",()->sample.upload.prepare(null,null));
            for(String action:Arrays.asList("private_upload","upload_prepare","upload_open","upload_commit")){
                request.put("action",action);rejects("cdp_frame_request_invalid",()->MdoBrowserCdpFrameRequest.parse(request));
            }
        }
    }
    private static void lostAssignmentReply()throws Exception {
        try(Case sample=new Case("leaf-frame")){
            check(sample.upload.receipt()==null);sample.prepare();sample.open();sample.status();
            rejects("cdp_worker_timeout",()->sample.run(true,(upload,binding,deadline)->{
                JSONObject result=upload.commit(binding,deadline);sample.peer.peer.host.clockOffset.addAndGet(20000);return result;
            }));
            check(sample.upload.assignmentMayHaveOccurred()&&sample.commits==1);
            int calls=sample.calls.size(),peers=sample.peer.peer.host.peers.size();
            MdoBrowserCdpUpload.Receipt receipt=sample.upload.receipt();check(receipt!=null&&!receipt.cleanupConfirmed);publicValue(receipt.value());
            check(receipt.value().getBoolean("verified")&&sample.calls.size()==calls&&sample.peer.peer.host.peers.size()==peers&&sample.commits==1);
        }
    }
    public static void main(String[] args)throws Exception {
        Looper.getMainLooper();System.loadLibrary("xs");fullFlow();preparationFailures();assignmentFailures();ownershipAndDeadlines();grammar();lostAssignmentReply();
        System.out.println("{\"passed\":true,\"checks\":"+checks.get()+",\"real_jvm_jni_json\":true,\"fixed_native_picker_steps\":true,\"android_callback_joined\":false,\"uri_assignment_verified\":false,\"framework_verified\":false,\"provider_qualified\":false}");
    }
}
