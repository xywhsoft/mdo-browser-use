"""Test the exact private discovery transport over real Linux kernel sockets.

One project-owned data/output directory; no browser/app/Home/source copy. This
is a transport gate, not evidence of an Android provider or CDP command pass.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]


def prepare_popup_product(output):
    source=(ROOT/'tests/fixtures/android-cdp-java/PopupProductHost.java.in').read_text(encoding='utf-8')
    document=(ROOT/'host/android/browser-document.inc.java').read_text(encoding='utf-8')
    view=(ROOT/'host/android/browser-view.inc.java').read_text(encoding='utf-8')
    upload=(ROOT/'host/android/browser-native-upload.inc.java').read_text(encoding='utf-8')
    lifecycle=(ROOT/'host/android/browser-destroy.inc.java').read_text(encoding='utf-8')
    network=(ROOT/'host/android/browser-network.inc.java').read_text(encoding='utf-8')
    capture=(ROOT/'host/android/browser-native-capture.inc.java').read_text(encoding='utf-8')
    chooser=(ROOT/'host/android/browser-native-chooser.inc.java').read_text(encoding='utf-8')
    chooser=chooser[:chooser.index('    private void cancelChooser')].replace('private boolean chooserReady(', 'private boolean productChooserReady(',1)
    # Preserve the pre-existing controlled close-failure/counter seams while
    # running the real upload lifetime and physical native destruction hooks.
    if upload.count('private void cancelUpload(String key,WebView expected){')!=1 or upload.count('private void retireUploadData(WebView view){')!=1:
        raise RuntimeError('Popup upload lifetime fixture seam changed')
    popup_upload=upload.replace('private void cancelUpload(String key,WebView expected){', 'private void cancelUpload(String key,WebView expected){if(failClose)throw new IllegalStateException("controlled_logical_cleanup_failure");',1)
    popup_upload=popup_upload.replace('private void retireUploadData(WebView view){', 'private void retireUploadData(WebView view){retired++;',1)
    lifetime=network[network.index('    private void networkRetire(NetworkEntry'):network.index('    private void networkReplyFailure')]
    # The previously controlled root connection stays a fixture dependency;
    # the real popup registry/current/retire/watch/destruction paths run here.
    lifetime=lifetime.replace('        retireProtocolPopupPreparation(entry.key,entry.view,', '        if(entry.popup==null)return;\n        retireProtocolPopupPreparation(entry.key,entry.view,',1)
    destruction=network[network.index('    private void networkDestroyed(String'):network.index('    private String networkProtocolCode')]
    destruction=destruction.replace('private void networkDestroyed(WebView view){for(', 'private void networkDestroyed(WebView view){networkDestroyed++;for(',1)
    fragments={
        '@@POPUP_LINK@@':(ROOT/'host/android/browser-native-popup.inc.java').read_text(encoding='utf-8').split('    private final Map<String,MdoBrowserPopups')[0],
        '@@BROWSER_CLOSE@@':(ROOT/'host/android/browser-close.inc.java').read_text(encoding='utf-8'),
        '@@NETWORK_ENTRY@@':network[network.index('    private final class NetworkEntry'):network.index('    private boolean networkCurrent')],
        '@@NETWORK_CURRENT@@':network[network.index('    private boolean networkCurrent'):network.index('    private int networkRetiring')],
        '@@NETWORK_LIFETIME@@':lifetime,
        '@@NETWORK_DESTROY@@':destruction,
        '@@CREATION_GUARD@@':document[document.index('    private void nativeCreationAdmission'):document.index('    private final Map<String,MdoBrowserDocument>')],
        '@@DESTRUCTION@@':document[document.index('    private final class NativeDestruction'):document.index('    private void documentNavigation')],
        '@@PREPARE_DOCUMENT@@':document[document.index('    private MdoBrowserDocument prepareDocument'):document.index('    private boolean closeDocument')],
        '@@CONSTRUCT@@':view[view.index('    private MdoBrowserCdpPopupNative.Created constructProtocolPopup'):view.index('    /** Common native policy')],
        '@@PREPARATION@@':(ROOT/'host/android/browser-popup-preparation.inc.java').read_text(encoding='utf-8'),
        '@@CHOOSER@@':chooser,
        '@@FRAME_ENVIRONMENT@@':document[document.index('    private boolean cdpEnvironmentCurrent'):document.index('    private MdoBrowserCdpMarker cdpMarker')],
        '@@FRAME_ROUTER@@':(ROOT/'host/android/browser-frame-router.inc.java').read_text(encoding='utf-8'),
        '@@DOCUMENT_STATE@@':document[document.index('    private JSONObject documentState'):document.index('    private void documentFailure')]+network[network.index('    private JSONObject networkState'):],
        '@@LEGACY_DOCUMENT@@':document[document.index('    private void documentFailure'):],
        '@@CAPTURE_SESSION@@':capture[capture.index('    private MdoBrowserCdpCapture.Session captureDocumentSession'):capture.index('    private void executeNativeCapture')],
        '@@INPUT@@':(ROOT/'host/android/browser-native-input.inc.java').read_text(encoding='utf-8'),
        '@@UPLOAD@@':popup_upload,
        '@@ASSIGNMENT@@':(ROOT/'host/android/browser-upload-assignment.inc.java').read_text(encoding='utf-8'),
        '@@CAPTURE_FENCE@@':capture[capture.index('    private boolean captureMutationReady'):capture.index('    private CaptureEntry captureEntry')],
        '@@RESUME@@':lifecycle[lifecycle.index('    void resume()'):lifecycle.index('    void destroy()')],
    }
    for marker,value in fragments.items():
        if marker=='@@DESTRUCTION@@':
            boundary='row.controlled=MdoBrowserCdpPopupNative.registerDestruction(view,row);return row;'
            if value.count(boundary)!=1:raise RuntimeError('Native destruction boundary changed')
            value=value.replace(boundary,'observeNativeProofRevocation(view);'+boundary)
        if source.count(marker)!=1:raise RuntimeError('Popup product fixture marker changed')
        source=source.replace(marker,value)
    generated=output/'PopupProductHost.java';generated.write_text(source,encoding='utf-8');return generated


def main():
    if os.name=='nt':
        script=Path(__file__).resolve().as_posix();script='/mnt/'+script[0].lower()+script[2:]
        subprocess.run(['wsl.exe','--exec','python3',script],cwd=ROOT,check=True);return
    import resource
    resource.setrlimit(resource.RLIMIT_CORE,(0,0))
    output=ROOT/'.mdo/qa/android-cdp-native';marker=output/'.browser-use-owner.json'
    expected={'purpose':'mdo-browser-use/android-cdp-native'}
    if output.is_symlink() or not output.resolve().is_relative_to((ROOT/'.mdo').resolve()):
        raise RuntimeError('Linked/escaped native transport output preserved')
    if output.exists() and (not marker.is_file() or json.loads(marker.read_text())!=expected):
        raise RuntimeError('Unknown native transport output preserved')
    output.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(expected)+'\n')
    fixture=ROOT/'tests/fixtures/android-cdp-socket.c';binary=output/'transport-test'
    setup=json.loads((ROOT/'.mdo/android-client-tools/receipt.json').read_text(encoding='utf-8'));java=Path(setup['java_home'])
    environment=dict(os.environ,ASAN_OPTIONS='detect_leaks=1:halt_on_error=1',UBSAN_OPTIONS='halt_on_error=1')
    # Reuse the pinned host's own XRT source and this fixed output directory.
    # This is a small protocol test executable, not another application/engine.
    include=ROOT/'.mdo/native-source/lib'
    flags=['-std=gnu11','-Wall','-Wextra','-Werror','-pthread','-O1','-g',
           '-fno-omit-frame-pointer','-fsanitize=address,undefined','-I'+str(include),
           '-I'+str(java/'include'),'-I'+str(java/'include/linux')]
    xrt=output/'xrt-websocket.o'
    subprocess.run(['gcc',*flags,'-c',str(ROOT/'tests/fixtures/android-cdp-xrt.c'),'-o',str(xrt)],cwd=ROOT,check=True)
    subprocess.run(['gcc',*flags,str(fixture),str(xrt),'-o',str(binary)],cwd=ROOT,check=True)
    result=json.loads(subprocess.check_output([str(binary)],cwd=ROOT,env=environment,text=True,timeout=120))
    if not result.get('passed') or not result.get('read_only_browser_discovery') or result.get('allocations')!=result.get('releases'):
        raise RuntimeError('The native transport gate did not confirm balanced cleanup')
    websocket=output/'websocket-test'
    subprocess.run(['gcc',*flags,str(ROOT/'tests/fixtures/android-cdp-websocket.c'),str(xrt),'-o',str(websocket)],cwd=ROOT,check=True)
    wire=json.loads(subprocess.check_output([str(websocket)],cwd=ROOT,env=environment,text=True,timeout=120))
    if not wire.get('passed') or not wire.get('browser_transport') or wire.get('allocations')!=wire.get('releases'):
        raise RuntimeError('The native WebSocket gate did not confirm balanced cleanup')
    result['websocket']=wire
    # Run the actual private Java facade through actual JNI on the existing
    # desktop JVM. Only Android's Looper identity is doubled. A small fixture
    # library supplies the local peer; there is no Android framework/provider.
    vmflags=['-std=gnu11','-Wall','-Wextra','-Werror','-pthread','-O1','-g','-fPIC',
             '-fsanitize=undefined','-I'+str(include),'-I'+str(java/'include'),'-I'+str(java/'include/linux')]
    vmxrt=output/'xrt-websocket-jvm.o'
    subprocess.run(['gcc',*vmflags,'-c',str(ROOT/'tests/fixtures/android-cdp-xrt.c'),'-o',str(vmxrt)],cwd=ROOT,check=True)
    subprocess.run(['gcc',*vmflags,'-DMDO_CDP_JVM_FIXTURE','-shared',str(ROOT/'tests/fixtures/android-cdp-websocket.c'),
                    str(vmxrt),'-o',str(output/'libxs.so')],cwd=ROOT,check=True)
    classes=output/'classes';classes.mkdir(exist_ok=True)
    javas=sorted((ROOT/'tests/fixtures/android-cdp-java').rglob('*.java'))
    template=ROOT/'tests/fixtures/android-cdp-java/ChooserContinuationHost.java.in'
    source=template.read_text(encoding='utf-8')
    document=(ROOT/'host/android/browser-document.inc.java').read_text(encoding='utf-8')
    document=document[document.index('    private boolean cdpEnvironmentCurrent'):document.index('    private void bindDocument')]
    chooser=(ROOT/'host/android/browser-native-chooser.inc.java').read_text(encoding='utf-8')
    chooser=chooser[:chooser.index('    private void cancelChooser')]
    overlay=json.loads((ROOT/'host/overlays.json').read_text(encoding='utf-8'))
    gates=[line for row in overlay['changes'] for line in row.get('after','').splitlines() if 'dialog_pending_or_unclean' in line]
    if len(gates)!=1 or 'chooserFramesAllowed' not in gates[0]:raise RuntimeError('Expected one reviewed chooser request gate')
    for key,value in {'@@DOCUMENT@@':document,'@@CHOOSER@@':chooser,'@@ROUTER@@':(ROOT/'host/android/browser-frame-router.inc.java').read_text(encoding='utf-8'),'@@DIALOG_GATE@@':gates[0]}.items():
        if source.count(key)!=1:raise RuntimeError('Chooser continuation fixture marker changed')
        source=source.replace(key,value)
    generated=output/'ChooserContinuationHost.java';generated.write_text(source,encoding='utf-8')
    upload_source=(ROOT/'tests/fixtures/android-cdp-java/UploadAssignmentHost.java.in').read_text(encoding='utf-8')
    capture_source=(ROOT/'host/android/browser-native-capture.inc.java').read_text(encoding='utf-8')
    capture_guard=capture_source[capture_source.index('    private boolean captureMutationReady'):capture_source.index('    private CaptureEntry captureEntry')]
    upload_fences=[line for row in overlay['changes'] for line in row.get('after','').splitlines() if 'if(uploadBusy(key,web)' in line]
    if len(upload_fences)!=1:raise RuntimeError('Expected the reviewed product upload fence')
    for key,value in {'@@DOCUMENT@@':document,'@@CHOOSER@@':chooser,'@@ROUTER@@':(ROOT/'host/android/browser-frame-router.inc.java').read_text(encoding='utf-8'),
                      '@@DIALOG_GATE@@':gates[0],'@@UPLOAD_FENCE@@':upload_fences[0],
                      '@@UPLOAD@@':(ROOT/'host/android/browser-native-upload.inc.java').read_text(encoding='utf-8'),
                      '@@ASSIGNMENT@@':(ROOT/'host/android/browser-upload-assignment.inc.java').read_text(encoding='utf-8'),'@@CAPTURE_FENCE@@':capture_guard}.items():
        if upload_source.count(key)!=1:raise RuntimeError('Product upload fixture marker changed')
        upload_source=upload_source.replace(key,value)
    upload_generated=output/'UploadAssignmentHost.java';upload_generated.write_text(upload_source,encoding='utf-8')
    download_template=ROOT/'tests/fixtures/android-cdp-java/DownloadCommandHost.java.in'
    download_source=download_template.read_text(encoding='utf-8')
    if download_source.count('@@DOWNLOAD_COMMANDS@@')!=1:raise RuntimeError('Download product command fixture marker changed')
    download_source=download_source.replace('@@DOWNLOAD_COMMANDS@@',(ROOT/'host/android/browser-downloads.inc.java').read_text(encoding='utf-8'))
    popup_source=(ROOT/'host/android/browser-native-popup.inc.java').read_text(encoding='utf-8')
    popup_state=popup_source[popup_source.index('    private JSONObject popupState('):popup_source.index('    private void executePopups(')]
    if download_source.count('@@POPUP_STATE@@')!=1:raise RuntimeError('Download product popup-state fixture marker changed')
    download_source=download_source.replace('@@POPUP_STATE@@',popup_state)
    download_generated=output/'DownloadCommandHost.java';download_generated.write_text(download_source,encoding='utf-8')
    popup_product=prepare_popup_product(output)
    import setup_android_json_test
    import update_android_owner_scripts
    update_android_owner_scripts.verify()
    import update_android_upload_broker
    update_android_upload_broker.verify()
    json_sources=setup_android_json_test.prepare()
    subprocess.run([str(java/'bin/javac'),'--release','8','-encoding','UTF-8','-d',str(classes),
                    *[str(ROOT/'host/android'/name) for name in ('MdoBrowserCdpNative.java','MdoBrowserCdpJson.java','MdoBrowserCdpResponse.java','MdoBrowserCdpSession.java',
                                                               'MdoBrowserCdpMarker.java','MdoBrowserCdpView.java','MdoBrowserCdpBinding.java','MdoBrowserCdpNetworkFrames.java','MdoBrowserCdpNetworkRequests.java',
                                                               'MdoBrowserCdpRegistry.java','MdoBrowserCdpWorker.java','MdoBrowserCdpNetworks.java','MdoBrowserCdpPopupBroker.java','MdoBrowserCdpPopupWorker.java','MdoBrowserCdpPopupNative.java','MdoBrowserCdpPopupSource.java','MdoBrowserCdpPopupPreparation.java','MdoBrowserCdpFrames.java','MdoBrowserCdpWorlds.java',
                                                               'MdoBrowserCdpOwnerScripts.java','MdoBrowserCdpFrameRequest.java','MdoBrowserCdpFrameChain.java','MdoBrowserCdpInput.java','MdoBrowserNativeInput.java','MdoBrowserCdpCapture.java','MdoBrowserCdpFileInput.java','MdoBrowserCdpChoosers.java','MdoBrowserCdpUploadScripts.java','MdoBrowserCdpUpload.java','MdoBrowserUploadCoordinator.java','MdoBrowserUploadStore.java','MdoBrowserUploadFiles.java','MdoBrowserUploadUris.java','MdoBrowserDownloadFiles.java','MdoBrowserDownloadQueue.java','MdoBrowserCdpDownloads.java','MdoBrowserUploadQueue.java','MdoBrowserChoosers.java','MdoBrowserPrivatePicker.java','MdoBrowserChannel.java','MdoBrowserCapture.java',
                                                               'MdoBrowserInput.java','MdoBrowserWheel.java','MdoBrowserLifecycle.java','MdoBrowserPopups.java')],
                    str(generated),str(upload_generated),str(download_generated),str(popup_product),*map(str,javas),*map(str,json_sources)],cwd=ROOT,check=True)
    vm=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNativeProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not vm.get('passed') or not vm.get('read_only_browser_discovery') or not vm.get('browser_transport') or not vm.get('real_jvm_jni') or not vm.get('native_popup_close_join') or vm.get('data_only_cancel_cycles')!=12 or vm.get('data_only_browser_cancel_cycles')!=4:
        raise RuntimeError('The actual JVM/JNI worker gate did not pass')
    result['jvm_worker']=vm
    dispatch=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpSessionProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not dispatch.get('passed') or dispatch.get('real_protocol_sessions')!=8:
        raise RuntimeError('The actual JNI protocol-session gate did not pass')
    result['jvm_dispatcher']=dispatch
    controls=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpControlProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if not controls.get('passed') or controls.get('real_control_sessions')!=6 or controls.get('real_cooperative_pairs')!=1 or controls.get('provider_qualified') is not False or controls.get('downloads_activated') is not False:
        raise RuntimeError('The fixed native paused-event control gate did not pass')
    result['jvm_native_controls']=controls
    attached=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpAttachedTargetProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if (not attached.get('passed') or attached.get('real_native_sessions')!=2 or not attached.get('fixed_original_release')
            or not attached.get('native_resume_then_detach') or attached.get('task_or_popup_ownership_proved') is not False or attached.get('provider_qualified') is not False):
        raise RuntimeError('The fixed original attached-target release gate did not pass')
    result['jvm_attached_release']=attached
    attached_navigation=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpAttachedNavigationProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if (not attached_navigation.get('passed') or attached_navigation.get('original_attachment_navigation_receipts') is not True
            or attached_navigation.get('independent_source_quarantine') is not True or attached_navigation.get('late_receipts_drained') is not True
            or attached_navigation.get('real_jvm_jni_json') is not True or attached_navigation.get('native_frame_or_ownership_proved') is not False
            or attached_navigation.get('provider_qualified') is not False):
        raise RuntimeError('Original protected attachment navigation receipts lost scope, retirement or bounded routing')
    result['jvm_attached_navigation']=attached_navigation
    popup_results=[]
    for mode in ('suite','capture','expiry','owner-loss','disable-failure','disable-race','waiting-type','host-cancel','partial-arm','candidate-limit'):
        popup=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                        '-cp',str(classes),'org.xserver.android.CdpPopupBrokerProbe',mode],cwd=ROOT,env=environment,text=True,timeout=40))
        if (not popup.get('passed') or not popup.get('physical_opener_binding') or not popup.get('single_process_claim')
                or popup.get('mode')!=mode or popup.get('qualified_popup_adoption') is not False or popup.get('provider_qualified') is not False
                or popup.get('real_native_sessions')!=(2 if mode=='suite' else 0)):
            raise RuntimeError('The original-opener process popup broker gate did not pass')
        popup_results.append(popup)
    result['jvm_popup_broker']=popup_results
    popup_worker_results=[]
    for mode in ('suite','native-cancel','host-close','native-ack-timeout','native-detach-timeout','native-send-failure','protection','protection-limit','protection-response','protection-duplicate','protection-timeout','protection-send-failure','protection-reply','protection-nonempty',
                 'native-proof','native-proof-missing','native-proof-unique','native-proof-owner','native-proof-cancel','native-proof-barrier','native-proof-detach','native-proof-no-guard','native-proof-marker','native-proof-late-ui','native-proof-between','native-proof-cooperative','native-proof-no-opener','native-proof-foreign-opener','native-proof-multiple','native-proof-post-birth','allocation','allocation-cleanup-failure','ownership','ownership-constructor-failure','ownership-disposal-failure','ownership-disposal-no-ack','retirement','retirement-disposal-failure','retirement-disposal-no-ack','retirement-no-consumption','retirement-opener-loss','admission-fence','owner-lifetime-unsent','owner-lifetime-sent',
                 'host-destruction','host-destruction-unsent','host-destruction-inline','host-destruction-human','host-destruction-human-inline','host-destruction-failure','host-destruction-nested-parent','host-destruction-nested-child',
                 'product-disposal','product-disposal-sent','product-disposal-consumed','product-disposal-configuration','product-disposal-network','product-disposal-failure','product-disposal-logical-failure','product-disposal-nested','product-disposal-reentrant',
                 'product-disposal-profile','product-disposal-profile-denied','product-disposal-profile-close','product-disposal-profile-channel',
                 'delivery-loss','delivery-loss-retry','delivery-loss-create','delivery-loss-queued','delivery-loss-multiple','delivery-loss-sent','delivery-loss-unsent','delivery-loss-scope',
                 'quiescence-failure','quiescence-shutdown','quiescence-ui-loss','quiescence-timeout','quiescence-sent','quiescence-physical-failure','quiescence-reentrant','quiescence-close-failure',
                 'promotion-parent-close','promotion-request-close','promotion-parent-reuse','promotion-global','promotion-ui-loss','promotion-failure','promotion-human','promotion-denied','promotion-multiple','promotion-nested',
                 'handoff-denied','handoff-request-close','handoff-parent-close','handoff-parent-reuse','handoff-expiry','handoff-response','handoff-limit','handoff-global','handoff-ui-loss','handoff-disposal-failure',
                 'activation-success','activation-parent-close','activation-expiry','activation-owner-loss',
                 'activation-resume-unsupported','activation-resume-rejected','activation-resume-nonempty','activation-resume-mismatch','activation-resume-timeout','activation-resume-send-failure',
                 'activation-flow-rejected','activation-flow-nonempty','activation-flow-foreign','activation-flow-timeout','activation-flow-send-failure','activation-flow-limit','activation-flow-owner-loss',
                 'document-inventory','document-evaluate','document-rebind','document-navigation','document-owner-loss','document-unique','document-metadata','document-no-fallback','document-reuse','document-scope','document-rebind-frames',
                 'response-pass','response-offer-cancel','response-arguments','response-abort','response-frame','response-document-loss','response-body','response-body-cancel','response-late-foreign',
                 'transfer-complete','transfer-cancel','transfer-limit','transfer-epoch','transfer-detach','transfer-pass','transfer-budget','transfer-other-controller','transfer-rebind','transfer-pending-limit','transfer-pending-epoch','transfer-close-retain','transfer-frame-epoch','transfer-ancestor','transfer-retired-binding',
                 'transfer-world-root','transfer-world-local','transfer-world-ancestor','transfer-world-pending','transfer-world-recover','transfer-world-unrelated',
                 'source-complete','source-pass','source-cancel','source-epoch','source-controller','source-race','source-nonempty','source-changed','source-unsupported','source-activated','source-captured-epoch','source-replay','source-stream-epoch','source-stream-cancel','source-stream-owner',
                 'source-navigation-flow','source-navigation-cancel','source-navigation-rejected','source-navigation-no-commit','source-navigation-pending',
                 'preparation-ready','preparation-restart','preparation-cancel','preparation-owner','preparation-channel','preparation-epoch','preparation-failure','preparation-throw','preparation-reentrant','preparation-rpc','preparation-rpc-invalid','preparation-protection','preparation-protection-failure','preparation-timeout',
                 'preparation-input-unready','preparation-input-invalid','preparation-input-retired',
                 'preparation-child-ready','preparation-child-delayed','preparation-child-timeout','preparation-child-owner','preparation-child-channel','preparation-child-request','preparation-child-replaced','preparation-child-timer',
                 'preparation-child-allocation-throw','preparation-child-reentrant','preparation-child-proof-timeout','preparation-child-proof-owner','preparation-child-proof-failure','preparation-child-proof-replay','preparation-child-proof-timer','preparation-child-proof-throw',
                 'preparation-adoption-ready','preparation-adoption-inline','preparation-adoption-timeout','preparation-adoption-cancel','preparation-adoption-owner','preparation-adoption-channel','preparation-adoption-epoch','preparation-adoption-timer',
                 'preparation-adoption-native-close','preparation-adoption-replay','preparation-adoption-subscription-replay','preparation-adoption-wrong-owner','preparation-adoption-physical-owner','preparation-adoption-callback-throw','preparation-adoption-unretained','preparation-adoption-arguments',
                 'preparation-adoption-late-ack','preparation-adoption-inline-expired','preparation-adoption-send-failure',
                 'preparation-coordinator-ready','preparation-coordinator-inline','preparation-coordinator-parent-close','preparation-coordinator-arguments','preparation-coordinator-busy','preparation-coordinator-key-conflict',
                 'preparation-coordinator-cancel-allocation','preparation-coordinator-cancel-adoption','preparation-coordinator-cancel-proof','preparation-coordinator-profile-cancel','preparation-coordinator-allocation-owner','preparation-coordinator-adoption-owner','preparation-coordinator-proof-owner',
                 'preparation-coordinator-deadline-adoption','preparation-coordinator-deadline-proof','preparation-coordinator-callback-throw','preparation-coordinator-configuration-failure','preparation-coordinator-proof-failure',
                 'preparation-coordinator-close-parent-active','preparation-coordinator-close-no-parent','preparation-coordinator-close-timeout','preparation-coordinator-close-clock',
                 'preparation-network-ready','preparation-network-inline','preparation-network-parent-close','preparation-network-duplicate','preparation-network-download','preparation-network-download-stream',
                 'preparation-network-cancel-source','preparation-network-owner-channel','preparation-network-owner-profile','preparation-network-deadline-source','preparation-network-callback-throw','preparation-network-schedule-failure',
                 'preparation-network-unsupported','preparation-network-nonempty','preparation-network-frame-race','preparation-network-peer-detach','preparation-network-close-timeout','preparation-network-download-unconfirmed',
                  'preparation-network-concurrent-ready','preparation-network-concurrent-proof','preparation-network-concurrent-detach',
                  'preparation-page-ready','preparation-page-parent-close','preparation-page-download','preparation-page-download-stream','preparation-page-document-change',
                  'preparation-page-activation-unsupported','preparation-page-activation-malformed','preparation-page-world-unique','preparation-page-world-metadata','preparation-page-callback-throw',
                  'preparation-page-scheduler-failure','preparation-page-loading-timeout','preparation-page-cancel-activation','preparation-page-channel-activation','preparation-page-deadline-activation',
                  'preparation-page-world-document-change','preparation-page-world-channel-change','preparation-page-cancel-world','preparation-page-world-wait',
                  'preparation-page-parent-before-world',
                  'preparation-page-cancel-world-isolated',
                  'preparation-page-route-list',
                  'preparation-page-route-repeat',
                  'preparation-page-route-nested',
                  'preparation-page-route-parent',
                  'preparation-page-route-stale',
                  'preparation-page-route-channel',
                  'preparation-page-route-hidden',
                  'preparation-page-route-manual',
                  'preparation-page-route-download',
                  'preparation-page-route-effect',
                  'preparation-page-route-busy',
                  'preparation-page-route-malformed',
                  'preparation-page-route-late',
                  'preparation-page-route-late-hidden',
                  'preparation-page-capture-ok','preparation-page-capture-parent','preparation-page-capture-cancel',
                  'preparation-page-capture-hidden','preparation-page-capture-late','preparation-page-capture-stale','preparation-page-capture-reentrant',
                  'preparation-page-capture-chooser',
                  'preparation-page-opener-ready','preparation-page-opener-effect','preparation-page-opener-effect-cancel',
                  'preparation-page-opener-reuse','preparation-page-opener-parent','preparation-page-opener-queued-cancel',
                  'preparation-page-opener-hidden','preparation-page-opener-chooser','preparation-page-opener-rearm','preparation-page-opener-foreign',
                  'preparation-page-opener-source-close','preparation-page-opener-queued-source-close','preparation-page-opener-retire-busy',
                  'preparation-page-input-hover','preparation-page-input-click','preparation-page-input-wheel','preparation-page-input-key',
                  'preparation-page-input-text','preparation-page-input-focus','preparation-page-input-parent','preparation-page-input-hidden',
                  'preparation-page-input-chooser','preparation-page-input-stale','preparation-page-input-cancel','preparation-page-input-late',
                  'preparation-page-input-afterdown','preparation-page-input-focus-timeout','preparation-page-input-channel','preparation-page-input-focus-retired',
                  'preparation-publication-ready',
                  'preparation-publication-files-upload','preparation-publication-files-nested','preparation-publication-files-cancel',
                  'preparation-publication-files-clear','preparation-publication-files-clear-cancel','preparation-publication-files-retention','preparation-publication-files-page-close',
                  'preparation-publication-continuation-ready','preparation-publication-continuation-loss',
                  'preparation-publication-continuation-parent','preparation-publication-continuation-restore',
                  'preparation-publication-hidden',
                  'preparation-publication-duplicate',
                  'preparation-publication-parent',
                  'preparation-publication-download',
                  'preparation-publication-close-add',
                  'preparation-publication-channel-add',
                  'preparation-publication-stale-add',
                  'preparation-publication-add-fail',
                  'preparation-publication-bounds-fail',
                  'preparation-publication-close-before-allocation',
                  'preparation-publication-close-world',
                  'preparation-publication-invalid-layout',
                  'preparation-publication-foreign-pending',
                  'preparation-publication-download-delayed-abort',
                  'preparation-publication-download-abort-mismatch',
                  'preparation-publication-download-abort-missing',
                  'preparation-publication-download-abort-rejected',
                  'preparation-publication-download-close-claim',
                  'preparation-publication-download-close-read',
                  'preparation-publication-deadline-add','preparation-publication-deadline-bounds',
                  'preparation-publication-retirement-prune','preparation-publication-retirement-event',
                  'preparation-publication-retirement-source-event','preparation-publication-retirement-source-capture','preparation-publication-source-crash',
                  'preparation-publication-retirement-ledger-frame','preparation-publication-retirement-ledger-request',
                  'preparation-publication-retirement-ledger-frame-active','preparation-publication-retirement-ledger-request-active',
                  'preparation-publication-retirement-ledger-fault','preparation-publication-retirement-ledger-listener',
                  'preparation-publication-retirement-managed',
                  'preparation-publication-nested-ready','preparation-publication-nested-blocked','preparation-publication-nested-duplicate',
                  'preparation-publication-nested-foreign','preparation-publication-nested-old-pin','preparation-publication-nested-parent-close',
                  'preparation-publication-nested-unrelated','preparation-publication-nested-navigation','preparation-publication-nested-manual',
                  'preparation-publication-nested-invalidated','preparation-publication-nested-cancelled',
                  'preparation-publication-state-ready','preparation-publication-state-starting','preparation-publication-state-legacy','preparation-publication-state-hidden',
                  'preparation-publication-state-viewport','preparation-publication-state-busy','preparation-publication-state-restoring',
                  'preparation-publication-state-navigation','preparation-publication-state-manual','preparation-publication-state-channel',
                  'preparation-publication-state-retiring','preparation-publication-state-pending','preparation-publication-state-download',
                  'preparation-restore-ready','preparation-restore-repeat','preparation-restore-hidden','preparation-restore-old-pin',
                  'preparation-restore-viewport','preparation-restore-parent','preparation-restore-cancelled','preparation-restore-stale',
                  'preparation-restore-manual','preparation-restore-channel','preparation-restore-unsupported','preparation-restore-unique',
                  'preparation-restore-busy','preparation-restore-late','preparation-restore-close','preparation-restore-quota',
                  'preparation-isolated-world','preparation-isolated-nested','preparation-isolated-detach','preparation-isolated-read','preparation-isolated-effect','preparation-isolated-detach-nested',
                  'preparation-isolated-foreign','preparation-isolated-expired','preparation-isolated-reset-attach','preparation-isolated-reset-runtime',
                  'preparation-isolated-receive-other','preparation-isolated-receive-foreign','preparation-isolated-receive-detach',
                  'preparation-isolated-control-resume','preparation-isolated-control-request','preparation-isolated-control-response',
                 'prepared-admission','prepared-admission-view','prepared-admission-network','prepared-admission-retiring','prepared-admission-mismatch'):
        popup_worker=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                        '-cp',str(classes),'org.xserver.android.CdpPopupWorkerProbe',mode,str(output/('popup-controller-files' if mode.startswith('transfer') else 'popup-body-files'))],cwd=ROOT,env=environment,text=True,timeout=60))
        if (not popup_worker.get('passed') or popup_worker.get('mode')!=mode or not popup_worker.get('one_original_executor')
                or not popup_worker.get('ui_delivery') or not popup_worker.get('cooperative_popup_pump')
                or popup_worker.get('protective_interception_scope_tested') is not mode.startswith('protection')
                or popup_worker.get('retained_target_marker_scope_tested') is not mode.startswith('native-proof')
                or popup_worker.get('product_disposer_join_tested') is not mode.startswith('product-disposal')
                or popup_worker.get('product_popup_preparation_tested') is not mode.startswith('preparation')
                or popup_worker.get('product_prepared_admission_tested') is not mode.startswith('prepared-admission')
                or popup_worker.get('product_page_startup_tested') is not mode.startswith('preparation-page-')
                or popup_worker.get('product_document_retirement_tested') is not mode.startswith('preparation-isolated-')
                or popup_worker.get('product_frame_route_tested') is not mode.startswith('preparation-page-route-')
                or popup_worker.get('product_publication_tested') is not mode.startswith(('preparation-publication-','preparation-restore-'))
                or popup_worker.get('product_document_restore_tested') is not mode.startswith('preparation-restore-')
                or popup_worker.get('product_capture_fence_tested') is not mode.startswith('preparation-page-capture-')
                or popup_worker.get('product_protected_opener_tested') is not mode.startswith('preparation-page-opener-')
                or popup_worker.get('product_protected_input_tested') is not mode.startswith('preparation-page-input-')
                or popup_worker.get('product_nested_adoption_tested') is not mode.startswith('preparation-publication-nested-')
                or popup_worker.get('product_upload_continuation_tested') is not mode.startswith('preparation-publication-continuation-')
                or popup_worker.get('product_protected_file_flow_tested') is not mode.startswith('preparation-publication-files-')
                or popup_worker.get('product_document_state_tested') is not mode.startswith('preparation-publication-state-')
                or popup_worker.get('product_frontend_recovery_tested') is not mode.startswith('preparation-publication-frontend-')
                or popup_worker.get('retirement_delivery_scope_tested') is not mode.startswith('delivery-loss')
                or popup_worker.get('transport_quiescence_tested') is not mode.startswith('quiescence')
                or popup_worker.get('protected_document_binding_tested') is not mode.startswith('document')
                or popup_worker.get('protected_response_routing_tested') is not mode.startswith('response')
                or popup_worker.get('protected_network_source_tested') is not mode.startswith('source')
                or popup_worker.get('attached_download_controller_tested') is not mode.startswith('transfer')
                or popup_worker.get('protected_attachment_activation_tested') is not mode.startswith('activation')
                or popup_worker.get('protected_attachment_handoff_tested') is not mode.startswith('handoff')
                or popup_worker.get('native_ui_promotion_tested') is not mode.startswith('promotion') or popup_worker.get('protocol_promotion_qualified') is not False
                or popup_worker.get('native_popup_adoption_qualified') is not False or popup_worker.get('provider_qualified') is not False):
            raise RuntimeError('The shared popup executor/UI lifecycle gate did not pass')
        if mode.startswith(('preparation-publication-','preparation-restore-')):
            published=popup_worker.get('product_publication')
            clean=mode not in {'preparation-publication-source-crash','preparation-publication-retirement-ledger-fault','preparation-publication-retirement-ledger-listener'} and mode not in {'preparation-publication-download-abort-'+x for x in ('mismatch','missing','rejected')}
            if (not isinstance(published,dict) or published.get('actual_product_creation') is not True or published.get('cleanup_confirmed') is not clean or published.get('unconfirmed_creation_fenced') is not (not clean)
                    or published.get('close_replies')!=1 or published.get('create_replies') not in {0,1} or published.get('browser_connections')!=1
                    or published.get('native_proof_connections') not in ({1,2} if mode.startswith('preparation-publication-nested-') else {0,1}) or published.get('no_url_replay') is not True
                    or published.get('framework_qualified') is not False or published.get('provider_qualified') is not False):
                raise RuntimeError('The original product publication and close receipts did not pass')
            if mode.startswith('preparation-publication-retirement-') and published.get('concurrent_retirement') is not True:
                raise RuntimeError('The exact concurrent retirement scheduler boundary was not exercised')
            if mode=='preparation-publication-source-crash' and published.get('source_crash_contained') is not True:
                raise RuntimeError('A renderer crash was incorrectly treated as deliberate retirement')
            if mode.startswith('preparation-publication-retirement-ledger-') and published.get('ledger_fault_contained') is not mode.endswith(('-fault','-listener')):
                raise RuntimeError('A source-ledger fault or ordinary listener error was swallowed as retirement')
        if mode.startswith('preparation-publication-files-'):
            receipt=popup_worker.get('product_frame_route');variant=mode[len('preparation-publication-'):]
            clearing=variant.startswith('files-clear');granted=variant in {'files-upload','files-nested','files-retention'}
            if (not isinstance(receipt,dict) or receipt.get('actual_product_file_flow') is not True or receipt.get('original_file_worker') is not True
                    or receipt.get('native_grants')!=int(granted) or receipt.get('native_responses')!=int(granted)
                    or receipt.get('opens')!=int(not clearing) or receipt.get('commits')!=int(granted) or receipt.get('clears')!=int(clearing)
                    or receipt.get('retained_read_verified') is not granted or receipt.get('nested') is not (variant=='files-nested')
                    or receipt.get('staging_expiry_tested') is not (variant=='files-retention')
                    or receipt.get('framework_grant_qualified') is not False or receipt.get('provider_qualified') is not False):
                raise RuntimeError('Protected popup upload/clear did not retain exact original native file and document ownership')
        if mode.startswith('preparation-publication-continuation-'):
            receipt=popup_worker.get('product_frame_route')
            if (not isinstance(receipt,dict) or receipt.get('original_document_continuation') is not True
                    or receipt.get('old_channel_lookup_refused') is not True or receipt.get('expressions')!=0
                    or receipt.get('worlds')!=(2 if mode.endswith('-restore') else 1)
                    or receipt.get('provider_assignment_qualified') is not False):
                raise RuntimeError('Upload continuation did not retain its exact native document without restore or fallback')
        if mode.startswith('preparation-publication-nested-'):
            proof=popup_worker.get('product_frame_route');variant=mode[len('preparation-publication-nested-'):]
            public=variant in {'ready','blocked','duplicate','foreign','old-pin','parent-close'}
            sent=0 if variant=='unrelated' else 1
            if (not isinstance(proof,dict) or proof.get('actual_publication') is not public or proof.get('opener_document_restored') is not public
                    or proof.get('negative_checked') is not (variant not in {'ready','duplicate','parent-close'})
                    or proof.get('independent_opener_close') is not (variant=='parent-close')
                    or proof.get('native_sends')!=sent or proof.get('native_consumptions')!=sent
                    or proof.get('native_destroy_count')!=(1 if public or variant=='cancelled' else 0)
                    or proof.get('native_view_closed') is not True or proof.get('original_prepare_settled') is not True
                    or proof.get('browser_connections')!=1 or proof.get('native_proof_connections')!=(2 if public else 1)
                    or proof.get('provider_qualified') is not False):
                raise RuntimeError('The original nested native transport, document revocation, publication or exact close was not confirmed')
        if mode.startswith('preparation-publication-state-'):
            proof=popup_worker.get('product_frame_route');variant=mode[len('preparation-publication-state-'):]
            phase={'ready':'ready','starting':'ready','legacy':'ready','hidden':'suspended','viewport':'suspended','busy':'busy','restoring':'restoring',
                   'navigation':'document_changed','manual':'owner_unavailable','channel':'owner_unavailable','retiring':'owner_unavailable','pending':'popup_pending','download':'download_only'}[variant]
            if (not isinstance(proof,dict) or proof.get('native_adapter') is not True or proof.get('read_only') is not True or proof.get('legacy_world_queries')!=0
                    or proof.get('phase_checked')!=phase or proof.get('ready_checked') is not (variant in {'ready','starting','legacy'}) or proof.get('startup_checked') is not (variant=='starting')
                    or proof.get('explicit_restore_checked') is not (variant in {'hidden','viewport','restoring'}) or proof.get('provider_qualified') is not False):
                raise RuntimeError('The original popup document-state route, readiness or read-only boundary did not pass')
        if mode.startswith('preparation-restore-'):
            restore=popup_worker['product_publication'].get('document_restore')
            if (not isinstance(restore,dict) or restore.get('actual_frame_router') is not True or restore.get('reads',0)<1
                    or restore.get('browser_connections')!=1 or restore.get('native_proof_connections')!=1 or restore.get('native_effects')!=0
                    or restore.get('same_document_quota') is not True or restore.get('no_url_replay') is not True
                    or restore.get('framework_qualified') is not False or restore.get('provider_qualified') is not False
                    or popup_worker['product_publication'].get('protected_retirement_boundaries',0)<1):
                raise RuntimeError('The original popup document restore and retirement boundaries did not pass')
        if mode.startswith('preparation-page-route-'):
            route=popup_worker.get('product_frame_route')
            expected=mode.rsplit('-',1)[1] in {'list','repeat','nested','parent','busy'}
            if (not isinstance(route,dict) or route.get('actual_product_frame_route') is not True or route.get('reply_ok') is not expected
                    or route.get('browser_connections')!=1 or route.get('native_proof_connections')!=1 or route.get('native_effects')!=0
                    or route.get('commands') not in {1,2} or route.get('provider_qualified') is not False or route.get('ui_published') is not False):
                raise RuntimeError('Original product frame command routing was not confirmed')
        if mode.startswith('preparation-page-capture-'):
            proof=popup_worker.get('product_frame_route');variant=mode.rsplit('-',1)[1]
            if not isinstance(proof,dict) or proof.get('raster_started') is not False:
                raise RuntimeError('Protected capture fence lost its explicit native-raster qualification boundary')
            if variant in {'hidden','chooser'}:
                if proof.get('refused_before_protocol') is not True:
                    raise RuntimeError('A hidden/chooser-blocked protected popup admitted capture work')
            elif (proof.get('original_document_capture_fence') is not True or proof.get('success') is not (variant in {'ok','parent'})
                    or proof.get('terminal_callbacks')!=1 or proof.get('browser_connections')!=1 or proof.get('native_proof_connections')!=1
                    or proof.get('world_fences')!=(2 if variant in {'ok','parent','late'} else 0) or proof.get('provider_qualified') is not False):
                raise RuntimeError('Protected capture used a foreign/reopened document or lost cancellation/epoch guards')
        if mode.startswith('preparation-page-opener-'):
            opener=popup_worker.get('product_frame_route');variant=mode[len('preparation-page-opener-'):]
            refused=variant in {'hidden','chooser','foreign'} or variant.startswith('queued-')
            if (not isinstance(opener,dict) or opener.get('browser_connections')!=1 or opener.get('native_proof_connections')!=1
                    or opener.get('provider_qualified') is not False or opener.get('native_effects')!=(1 if variant.startswith('effect') else 0)
                    or opener.get('ready_callbacks')!=(0 if refused else 2 if variant in {'reuse','rearm'} else 1)
                    or opener.get('failure_callbacks')!=(1 if refused else 0)):
                raise RuntimeError('Protected opener preparation lost its original ownership/callback/effect scope')
            if variant.endswith('source-close'):
                if opener.get('original_source_retired') is not True or opener.get('original_detach_pending') is not True:
                    raise RuntimeError('Native source release bypassed the original protocol detach fence')
            elif opener.get('original_opener_prepared') is not (not refused) or opener.get('document_restored_after_retirement') is not True:
                raise RuntimeError('Protected opener preparation lost its explicit document recovery')
        if mode.startswith('preparation-page-input-'):
            receipt=popup_worker.get('product_frame_route');variant=mode[len('preparation-page-input-'):]
            success=variant in {'hover','click','wheel','key','text','focus','parent'}
            steps=2 if variant in {'click','key','afterdown'} else 1 if success or variant=='focus-timeout' else 0
            if (not isinstance(receipt,dict) or receipt.get('success') is not success or receipt.get('native_steps')!=steps
                    or receipt.get('terminal_callbacks')!=(0 if variant=='channel' else 1) or receipt.get('browser_connections')!=1
                    or receipt.get('native_proof_connections')!=1 or receipt.get('provider_qualified') is not False):
                raise RuntimeError('Protected native input lost its exact document, cancellation, no-replay or original connection scope')
        if mode.startswith('preparation-page-'):
            evidence=popup_worker.get('product_page')
            if (not isinstance(evidence,dict) or type(evidence.get('cleanup_confirmed')) is not bool
                    or evidence.get('ui_published') is not False or evidence.get('provider_qualified') is not False
                    or not evidence['cleanup_confirmed'] and (evidence.get('unconfirmed_creation_fenced') is not True or evidence.get('failure_callbacks')!=1)):
                raise RuntimeError('Protected page startup lost its actual cleanup/publication evidence')
            if mode=='preparation-page-loading-timeout':
                retirement=popup_worker.get('loading_retirement')
                if (evidence['cleanup_confirmed'] is not True or evidence.get('unconfirmed_creation_fenced') is not False
                        or not isinstance(retirement,dict) or retirement.get('original_detach_emitted') is not True
                        or retirement.get('native_destroyed')!=1 or retirement.get('broker_creation_blocked') is not False
                        or retirement.get('event_failure') is not None or retirement.get('source_released') is not True):
                    raise RuntimeError('Loading timeout failed to retain its original pause and drain real detach/disable cleanup')
        if mode.startswith('preparation-isolated-'):
            evidence=popup_worker.get('product_document_retirement');preserved=mode not in ('preparation-isolated-foreign','preparation-isolated-receive-foreign','preparation-isolated-expired')
            if (not isinstance(evidence,dict) or evidence.get('shared_wire_preserved') is not preserved
                    or evidence.get('other_original_document_verified') is not preserved
                    or evidence.get('browser_connections')!=1 or evidence.get('native_proof_connections')!=2
                    or preserved and (evidence.get('native_detach_confirmed') is not True or evidence.get('effects_preserved') is not True)
                    or evidence.get('provider_qualified') is not False or evidence.get('ui_published') is not False):
                raise RuntimeError('Original document retirement isolation was not proved')
        if mode.startswith('source-navigation-'):
            proof=popup_worker.get('source_navigation');commands=4 if mode.endswith('-flow') else 0 if mode.endswith('-pending') else 1
            if (not isinstance(proof,dict) or proof.get('original_source') is not True or proof.get('shared_ledgers') is not True
                    or proof.get('navigation_commands')!=commands or proof.get('commit_proved') is not mode.endswith('-flow')
                    or proof.get('guard_replacements')!=0 or proof.get('scope_denials_checked') is not True
                    or proof.get('browser_connections')!=1 or proof.get('native_proof_connections')!=1
                    or proof.get('ui_navigation_enabled') is not False or proof.get('provider_qualified') is not False):
                raise RuntimeError('Protected source navigation replaced its original scope, guard or commit proof')
        popup_worker_results.append(popup_worker)
    result['jvm_popup_worker']=popup_worker_results
    popup_native=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpPopupNativeProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if (not popup_native.get('passed') or not popup_native.get('original_callback_ownership') or not popup_native.get('typed_native_consumption') or not popup_native.get('retirement_delivery_identity') or not popup_native.get('transport_quiescence_identity') or not popup_native.get('independent_registry_receipts')
            or popup_native.get('native_popup_adoption_qualified') is not False or popup_native.get('provider_qualified') is not False):
        raise RuntimeError('The original native popup callback ownership/consumption gate did not pass')
    result['jvm_popup_native']=popup_native
    worker_exit=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpWorkerExitProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not worker_exit.get('passed') or not worker_exit.get('original_worker_exit_fence') or worker_exit.get('provider_qualified') is not False:
        raise RuntimeError('The original native-worker exit fence did not pass')
    result['jvm_worker_exit']=worker_exit
    navigation=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNavigationProbe'],cwd=ROOT,env=environment,text=True,timeout=40))
    if (not navigation.get('passed') or not navigation.get('original_navigation_receipts') or not navigation.get('same_connection_body_progress')
            or navigation.get('real_native_download_request_join')!=1 or navigation.get('real_control_event_candidates')!=2 or navigation.get('real_control_sessions')!=2 or not navigation.get('fixed_control_ack_not_completion')
            or navigation.get('real_navigation_sessions')!=2 or navigation.get('real_commit_sessions')!=2 or navigation.get('framework_verified') is not False
            or navigation.get('provider_qualified') is not False or navigation.get('product_navigation_activated') is not False):
        raise RuntimeError('The fixed original native navigation/async body-pipeline gate did not pass')
    result['jvm_navigation_receipts']=navigation
    requests=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpRequestProbe'],cwd=ROOT,env=environment,text=True,timeout=40))
    if not requests.get('passed') or not requests.get('native_request_identity_chain') or not requests.get('bounded_metadata_only') or not requests.get('late_request_metadata') or not requests.get('bounded_command_history') or requests.get('provider_qualified') is not False:
        raise RuntimeError('Original root request identity gate did not pass')
    result['jvm_request_identity']=requests
    responses=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpResponseProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not responses.get('passed') or not responses.get('original_event_snapshot') or responses.get('provider_qualified') is not False or responses.get('body_retention_activated') is not False:
        raise RuntimeError('The bounded original-response metadata gate did not pass')
    result['jvm_response_metadata']=responses
    ancestry=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNetworkFramesProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if (not ancestry.get('passed') or not ancestry.get('actual_network_ancestry') or not ancestry.get('native_navigation_epochs') or not ancestry.get('root_control_event_proofs')
            or ancestry.get('framework_verified') is not False or ancestry.get('provider_qualified') is not False):
        raise RuntimeError('The physical network frame ancestry gate did not pass')
    if not ancestry.get('same_document_epoch_separate'):raise RuntimeError('Same-document position must not replace the original download document epoch')
    result['jvm_network_ancestry']=ancestry
    bodies=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpBodyProbe'],cwd=ROOT,env=environment,text=True,timeout=45))
    if not bodies.get('passed') or not bodies.get('original_body_permits') or bodies.get('real_body_sessions')!=3 or bodies.get('real_async_body_pairs')!=1 or bodies.get('real_retired_read_sessions')!=1 or bodies.get('provider_qualified') is not False or bodies.get('product_downloads_activated') is not False:
        raise RuntimeError('The original paused-response stream gate did not pass')
    result['jvm_response_body']=bodies
    offers=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpOfferProbe'],cwd=ROOT,env=environment,text=True,timeout=45))
    if (not offers.get('passed') or not offers.get('original_deferred_offers') or offers.get('real_deferred_offer_sessions')!=2
            or offers.get('provider_qualified') is not False or offers.get('product_downloads_activated') is not False):
        raise RuntimeError('The original deferred response offer gate did not pass')
    result['jvm_deferred_offers']=offers
    downloads=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpDownloadQueueProbe',str(output/'download-queue-files')],cwd=ROOT,env=environment,text=True,timeout=30))
    if (not downloads.get('passed') or not downloads.get('original_native_body_joined') or not downloads.get('real_guarded_files')
            or not downloads.get('blocked_file_worker_page_progress') or downloads.get('framework_verified') is not False
            or downloads.get('provider_qualified') is not False or downloads.get('product_downloads_activated') is not False):
        raise RuntimeError('The original body/file-worker join gate did not pass')
    result['jvm_download_queue']=downloads
    controller=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpDownloadsProbe',str(output/'download-controller-files')],cwd=ROOT,env=environment,text=True,timeout=45))
    if (not controller.get('passed') or not controller.get('actual_download_controller') or not controller.get('shared_protocol_worker')
            or not controller.get('real_guarded_files') or not controller.get('native_receipts_preserved')
            or controller.get('framework_verified') is not False or controller.get('provider_qualified') is not False
            or controller.get('product_downloads_activated') is not False):
        raise RuntimeError('The native download controller/physical worker/files gate did not pass')
    result['jvm_download_controller']=controller
    retirement=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNetworkRetireProbe',str(output/'network-retire-files')],cwd=ROOT,env=environment,text=True,timeout=45))
    if (not retirement.get('passed') or not retirement.get('original_retirement_receipts') or not retirement.get('shared_protocol_worker')
            or not retirement.get('real_guarded_files') or retirement.get('framework_verified') is not False or retirement.get('provider_qualified') is not False):
        raise RuntimeError('The original response retirement and policy-disable gate did not pass')
    result['jvm_network_retirement']=retirement
    rearm=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNetworkRearmProbe',str(output/'network-rearm-files')],cwd=ROOT,env=environment,text=True,timeout=45))
    if (not rearm.get('passed') or not rearm.get('same_physical_view_rearm') or not rearm.get('retained_original_downloads')
            or not rearm.get('stale_callbacks_fenced') or not rearm.get('same_document_quota') or not rearm.get('real_guarded_files')
            or rearm.get('framework_verified') is not False or rearm.get('provider_qualified') is not False):
        raise RuntimeError('The same-page native rearm/download generations gate did not pass')
    result['jvm_network_rearm']=rearm
    navigation_owner=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNetworkNavigationProbe',str(output/'network-navigation-files')],cwd=ROOT,env=environment,text=True,timeout=45))
    if (not navigation_owner.get('passed') or not navigation_owner.get('original_ui_admission') or not navigation_owner.get('shared_worker_body_progress')
            or not navigation_owner.get('released_request_links') or not navigation_owner.get('original_download_epoch') or not navigation_owner.get('same_location_noop_proof') or not navigation_owner.get('overlapping_navigation_downloads') or not navigation_owner.get('late_metadata_after_body_completion') or not navigation_owner.get('original_request_download_join') or not navigation_owner.get('history_completion_guard') or not navigation_owner.get('bfcache_and_same_document_history')
            or not navigation_owner.get('same_document_destination_guard') or not navigation_owner.get('exact_queued_cancellation') or not navigation_owner.get('original_native_commit_matching') or not navigation_owner.get('bounded_commit_history') or not navigation_owner.get('real_guarded_files')
            or navigation_owner.get('framework_verified') is not False or navigation_owner.get('provider_qualified') is not False):
        raise RuntimeError('The original UI navigation admission/shared native worker gate did not pass')
    result['jvm_network_navigation']=navigation_owner
    download_host=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpDownloadsHostProbe',str(output/'download-host-files')],cwd=ROOT,env=environment,text=True,timeout=45))
    if (not download_host.get('passed') or not download_host.get('closed_page_retains_downloads') or not download_host.get('exact_product_commands') or not download_host.get('real_native_controller_files')
            or download_host.get('framework_verified') is not False or download_host.get('provider_qualified') is not False
            or download_host.get('product_downloads_activated') is not False):
        raise RuntimeError('The exact native product download command/receipt gate did not pass')
    result['jvm_download_host']=download_host
    binding=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpBindingProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not binding.get('passed') or not binding.get('real_jvm_jni_json'):
        raise RuntimeError('The private native-view binding core gate did not pass')
    result['jvm_binding']=binding
    network_binding=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNetworkBindingProbe'],cwd=ROOT,env=environment,text=True,timeout=45))
    if not network_binding.get('passed') or not network_binding.get('physical_scope_separate') or not network_binding.get('native_history_selection') or not network_binding.get('history_ack_not_completion') or not network_binding.get('post_history_native_proof_failures') or network_binding.get('provider_qualified') is not False or network_binding.get('product_downloads_activated') is not False:
        raise RuntimeError('The private physical-network target proof gate did not pass')
    result['jvm_network_binding']=network_binding
    network_worker=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpNetworkWorkerProbe'],cwd=ROOT,env=environment,text=True,timeout=45))
    if not network_worker.get('passed') or not network_worker.get('shared_protocol_worker') or not network_worker.get('cooperative_page_wait') or network_worker.get('provider_qualified') is not False:
        raise RuntimeError('The private cooperative physical-network worker gate did not pass')
    result['jvm_network_worker']=network_worker
    registry=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpRegistryProbe'],cwd=ROOT,env=environment,text=True,timeout=30))
    if not registry.get('passed') or not registry.get('native_ui_calls_doubled'):
        raise RuntimeError('The private CDP lifecycle registry gate did not pass')
    result['jvm_registry']=registry
    worker_dispatch=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpWorkerProbe'],cwd=ROOT,env=environment,text=True,timeout=40))
    if not worker_dispatch.get('passed') or not worker_dispatch.get('real_jvm_jni_json'):
        raise RuntimeError('The bounded private CDP worker dispatch gate did not pass')
    result['jvm_worker_dispatch']=worker_dispatch
    frames=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFramesProbe'],cwd=ROOT,env=environment,text=True,timeout=45))
    if not frames.get('passed') or not frames.get('real_jvm_jni_json'):
        raise RuntimeError('The private native frame graph gate did not pass')
    result['jvm_frames']=frames
    worlds=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpWorldsProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not worlds.get('passed') or not worlds.get('real_jvm_jni_json'):
        raise RuntimeError('The private isolated frame-world gate did not pass')
    result['jvm_worlds']=worlds
    owner=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpOwnerProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not owner.get('passed') or not owner.get('real_jvm_jni_json'):
        raise RuntimeError('The private frame-owner scope gate did not pass')
    result['jvm_owner']=owner
    request=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFrameRequestProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not request.get('passed') or not request.get('real_jvm_jni_json'):
        raise RuntimeError('The shared frame command grammar gate did not pass')
    result['jvm_frame_requests']=request
    input_guard=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpInputProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not input_guard.get('passed') or not input_guard.get('native_input_sequencer'):
        raise RuntimeError('The native frame-chain input gate did not pass')
    result['jvm_frame_input']=input_guard
    capture_guard=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpCaptureProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not capture_guard.get('passed') or not capture_guard.get('native_capture_transaction') or not capture_guard.get('real_jvm_jni_json'):
        raise RuntimeError('The native frame-chain capture transaction gate did not pass')
    result['jvm_frame_capture']=capture_guard
    file_input=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFileInputProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not file_input.get('passed') or not file_input.get('native_file_input_inspection') or file_input.get('files_delivered')!=0:
        raise RuntimeError('The native file input inspection gate did not pass')
    result['jvm_file_input']=file_input
    file_clear=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFileClearProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if not file_clear.get('passed') or not file_clear.get('native_file_clear') or not file_clear.get('real_jvm_jni_json') or file_clear.get('files_delivered')!=0 or file_clear.get('provider_qualified') is not False:
        raise RuntimeError('The native file clear operation gate did not pass')
    result['jvm_file_clear']=file_clear
    chooser=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpChooserProbe'],cwd=ROOT,env=environment,text=True,timeout=55))
    if not chooser.get('passed') or not chooser.get('real_jvm_jni_json') or not chooser.get('native_chooser_events') or chooser.get('files_delivered')!=0 or chooser.get('original_android_callback_joined') is not False:
        raise RuntimeError('The native chooser event/target gate did not pass')
    result['jvm_chooser']=chooser
    upload=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpUploadProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if not upload.get('passed') or not upload.get('real_jvm_jni_json') or not upload.get('fixed_native_picker_steps') or upload.get('android_callback_joined') is not False:
        raise RuntimeError('The original-world private upload worker gate did not pass')
    result['jvm_private_upload']=upload
    continuation=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpChooserContinuationProbe'],cwd=ROOT,env=environment,text=True,timeout=60))
    if not continuation.get('passed') or not continuation.get('exact_host_fragments') or not continuation.get('read_only_chooser_continuation') or continuation.get('files_granted')!=0:
        raise RuntimeError('The exact native chooser continuation scope gate did not pass')
    result['jvm_chooser_continuation']=continuation
    coordinator=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpUploadCoordinatorProbe',str(output/'coordinator-files')],cwd=ROOT,env=environment,text=True,timeout=90))
    if not coordinator.get('passed') or not coordinator.get('real_jvm_jni_file_queue') or not coordinator.get('retained_bytes_verified') or coordinator.get('framework_verified') is not False:
        raise RuntimeError('The original native callback/file/CDP coordinator gate did not pass')
    result['jvm_upload_coordinator']=coordinator
    upload_host=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpUploadHostProbe',str(output/'upload-host-files')],cwd=ROOT,env=environment,text=True,timeout=120))
    if not upload_host.get('passed') or not upload_host.get('exact_product_fragments') or not upload_host.get('no_effect_replay') or upload_host.get('framework_verified') is not False:
        raise RuntimeError('The exact original product upload route gate did not pass')
    result['jvm_upload_host']=upload_host
    clear_host=json.loads(subprocess.check_output([str(java/'bin/java'),'-Xcheck:jni','-Djava.library.path='+str(output),
                    '-cp',str(classes),'org.xserver.android.CdpFileClearHostProbe',str(output/'clear-host-files')],cwd=ROOT,env=environment,text=True,timeout=120))
    if not clear_host.get('passed') or not clear_host.get('exact_product_fragments') or not clear_host.get('no_effect_replay') or clear_host.get('source_files_read')!=0 or clear_host.get('framework_verified') is not False:
        raise RuntimeError('The exact native product clear reservation/receipt gate did not pass')
    result['jvm_clear_host']=clear_host
    lock=json.loads((ROOT/'.mdo/native-source/tools/android/toolchain.lock.json').read_text())
    clang=ROOT/'.mdo/android-client-tools/sdk/ndk'/lock['ndk']/'toolchains/llvm/prebuilt/linux-x86_64/bin/clang'
    subprocess.run([str(clang),f"--target=aarch64-linux-android{lock['api']}",'-std=gnu11','-Wall','-Wextra','-Werror','-O2','-fPIC',
        '-c',str(ROOT/'host/android/MdoBrowserCdpSocket.c'),'-o',str(output/'android-socket.o')],cwd=ROOT,check=True)
    subprocess.run([str(clang),f"--target=aarch64-linux-android{lock['api']}",'-std=gnu11','-Wall','-Wextra','-Werror','-O2','-fPIC',
        '-DXRT_MODULE_WEBSOCKET_HANDSHAKE','-DXRT_MODULE_WEBSOCKET_MESSAGE','-DXRT_MODULE_RANDOM_SECURE','-I'+str(include),
        '-c',str(ROOT/'host/android/MdoBrowserCdpWebSocket.c'),'-o',str(output/'android-websocket.o')],cwd=ROOT,check=True)
    inputs=('host/android/MdoBrowserNativeCapture.java','host/android/cdp-protocol.json','host/android/MdoBrowserCdpSocket.c','host/android/MdoBrowserCdpSocket.h','host/android/MdoBrowserCdpJni.inc.c',
            'host/android/MdoBrowserCdpWebSocket.c','host/android/MdoBrowserCdpWebSocket.h',
            'host/android/MdoBrowserCdpNative.java','host/android/MdoBrowserCdpJson.java','host/android/MdoBrowserCdpResponse.java','host/android/MdoBrowserCdpSession.java',
            'host/android/MdoBrowserCdpMarker.java','host/android/MdoBrowserCdpView.java','host/android/MdoBrowserCdpBinding.java','host/android/MdoBrowserCdpNetworkFrames.java','host/android/MdoBrowserCdpNetworkRequests.java',
            'host/android/MdoBrowserCdpRegistry.java','host/android/MdoBrowserCdpWorker.java','host/android/MdoBrowserCdpNetworks.java','host/android/MdoBrowserCdpPopupBroker.java','host/android/MdoBrowserCdpPopupWorker.java','host/android/MdoBrowserCdpPopupNative.java','host/android/MdoBrowserCdpPopupSource.java','host/android/MdoBrowserCdpPopupPreparation.java','host/android/MdoBrowserCdpFrames.java','host/android/MdoBrowserCdpWorlds.java',
            'host/android/MdoBrowserCdpOwnerScripts.java','host/android/MdoBrowserCdpFrameRequest.java','host/android/MdoBrowserCdpFrameChain.java','host/android/MdoBrowserCdpInput.java','host/android/MdoBrowserNativeInput.java','host/android/MdoBrowserCdpCapture.java','host/android/MdoBrowserCdpFileInput.java','host/android/MdoBrowserCdpChoosers.java','host/android/MdoBrowserCdpUploadScripts.java','host/android/MdoBrowserCdpUpload.java','host/android/MdoBrowserUploadCoordinator.java','host/android/MdoBrowserUploadStore.java','host/android/MdoBrowserUploadFiles.java','host/android/MdoBrowserUploadUris.java','host/android/MdoBrowserDownloadFiles.java','host/android/MdoBrowserDownloadQueue.java','host/android/MdoBrowserCdpDownloads.java','host/android/MdoBrowserUploadQueue.java','host/android/MdoBrowserChoosers.java','host/android/MdoBrowserPrivatePicker.java','host/android/MdoBrowserChannel.java','host/android/MdoBrowserCapture.java',
            'host/android/browser-network-navigation.inc.java','host/android/browser-destroy.inc.java','host/android/browser-document.inc.java','host/android/browser-view.inc.java','host/android/browser-popup-preparation.inc.java','host/android/browser-close.inc.java','host/android/browser-native-input.inc.java','host/android/browser-downloads.inc.java','tests/fixtures/android-cdp-java/DownloadCommandHost.java.in','host/android/browser-native-capture.inc.java','host/android/browser-native-upload.inc.java','host/android/browser-upload-assignment.inc.java','tests/fixtures/android-cdp-java/UploadAssignmentHost.java.in','host/android/browser-native-chooser.inc.java','host/android/browser-frame-router.inc.java','host/overlays.json','tests/fixtures/android-cdp-java/ChooserContinuationHost.java.in',
            'host/native-frames.inc.hpp','tools/update_android_owner_scripts.py','host/upload-picker-broker.js','tools/update_android_upload_broker.py',
            'host/android/MdoBrowserInput.java','host/android/MdoBrowserWheel.java','host/android/MdoBrowserLifecycle.java','host/android/MdoBrowserPopups.java','host/android/browser-native-popup.inc.java','host/android/popup-protocol.json',
            'tools/android_cdp_socket_probe.py','tests/fixtures/android-cdp-java/PopupProductHost.java.in','tests/fixtures/android-cdp-java/org/xserver/android/CdpAttachedNavigationProbe.java','host/android/json-test-sources.json','tools/setup_android_json_test.py','tests/fixtures/android-cdp-socket.c',
            'tests/fixtures/android-cdp-websocket.c','tests/fixtures/android-cdp-xrt.c',
            *[p.relative_to(ROOT).as_posix() for p in javas])
    result.update(source_sha256={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in inputs},
                  address_sanitizer=True,undefined_sanitizer=True,android_api_compiled=lock['api'],
                  android_device_qualified=False,provider_qualified=False,debugging_enabled=False,model_calls=0)
    (ROOT/'.mdo/android-cdp-socket-result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2),flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
