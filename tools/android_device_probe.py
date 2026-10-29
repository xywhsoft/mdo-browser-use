"""Install/run only the owned tiny dev client; collect one nonce-bound result.

Existing mdo packages and data stay untouched. No global log clearing/dumping,
provider upgrade, application Home, external page or model request is used.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import build_android_client

ROOT=build_android_client.ROOT
PACKAGE=build_android_client.PACKAGE
MODES={
    'world':('android-device-result.json','installed-provider-world-gate'),
    'native-input':('android-device-input-result.json','native-input-framework-gate'),
    'native-capture':('android-device-capture-result.json','native-capture-pixel-framework-gate'),
    'native-dialog':('android-device-dialog-result.json','native-dialog-framework-gate'),
    'native-popup':('android-device-popup-result.json','native-popup-transport-framework-gate'),
    'native-chooser':('android-device-chooser-result.json','native-chooser-cancellation-framework-gate'),
    'native-upload':('android-device-upload-result.json','native-private-cache-staging-framework-gate'),
    'native-upload-uri':('android-device-upload-uri-result.json','native-private-provider-seek-revoke-framework-gate'),
}


def unverified(state,**fields):
    result=dict.fromkeys(('automation_verified','native_input_verified','production_document_controller_verified',
        'world_isolation_verified','guarded_native_input_verified','native_wheel_verified','native_hover_verified',
        'native_capture_verified','guarded_native_capture_verified','capture_privacy_verified','native_dialog_verified',
        'before_unload_framework_verified','host_dialog_routing_verified','native_popup_verified','host_popup_routing_verified',
        'popup_world_qualified','native_chooser_verified','chooser_framework_verified','host_chooser_routing_verified',
        'chooser_assignment_verified','chooser_frame_identity_verified','native_upload_staging_verified',
        'private_upload_cache_authority_verified','host_upload_routing_verified','upload_uri_assignment_verified',
        'native_upload_uri_proxy_verified','uri_proxy_framework_verified','upload_uri_revocation_verified','manual_chooser_framework_verified'),False)
    result.update(state=state,existing_mdo_preserved=True);result.update(fields);return result


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--adb',type=Path,default=Path('D:/GIT/mdo/.build/android-platform-tools/platform-tools/adb.exe'))
    parser.add_argument('--mode',choices=tuple(MODES),default='world')
    args=parser.parse_args();directory=ROOT/'.mdo/host/android-client';apk=directory/'mdo-browser-use-dev.apk'
    build=json.loads((directory/'build.json').read_text(encoding='utf-8'))
    result_path=ROOT/'.mdo'/MODES[args.mode][0]
    result_path.write_text(json.dumps(unverified('running'))+'\n',encoding='utf-8')
    if build.get('state')!='built' or build.get('package')!=PACKAGE or build.get('source_sha256')!=build_android_client.source_hash() or build.get('apk_sha256')!=hashlib.sha256(apk.read_bytes()).hexdigest():
        raise RuntimeError('Android dev client build/source receipt is stale')
    def adb(*values,timeout=30,check=True):
        run=subprocess.run([str(args.adb),*values],cwd=ROOT,text=True,capture_output=True,timeout=timeout)
        if check and run.returncode:
            code=re.search(r'INSTALL_FAILED_[A-Z_]+',run.stdout+run.stderr)
            raise RuntimeError('ADB '+values[0]+' failed'+(': '+code.group() if code else ''))
        return run.stdout.strip()
    rows=[row.split() for row in adb('devices').splitlines()[1:] if row.strip()]
    if len(rows)!=1 or len(rows[0])<2 or rows[0][1]!='device':
        result_path.write_text(json.dumps(unverified('unavailable',code='android_device_unavailable'))+'\n',encoding='utf-8')
        raise RuntimeError('Exactly one authorized Android device is required')
    install_receipt=directory/'device-install.json'
    existing=adb('shell','pm','path',PACKAGE,check=False)
    if existing and (not install_receipt.is_file() or json.loads(install_receipt.read_text()).get('package')!=PACKAGE):
        raise RuntimeError('An unrecognized existing dev package is preserved')
    matches=False
    if existing:
        # A system confirmation may finish after the first ADB wait times out.
        # Verify the actual installed bytes before reusing it, avoiding another
        # installation request or an unverified "installed" receipt.
        lines=existing.splitlines()
        if len(lines)!=1 or not lines[0].startswith('package:/data/app/') or not lines[0].endswith('/base.apk'):
            raise RuntimeError('Unexpected dev package layout preserved')
        installed_apk=directory/'installed.apk'
        try:
            adb('pull',lines[0][8:],str(installed_apk))
            matches=hashlib.sha256(installed_apk.read_bytes()).hexdigest()==build['apk_sha256']
        finally:
            if installed_apk.is_file():installed_apk.unlink()
    if not matches:
        install_receipt.write_text(json.dumps({'package':PACKAGE,'state':'install_requested','apk_sha256':build['apk_sha256']})+'\n',encoding='utf-8')
        try:installed=adb('install','-r','-t',str(apk),timeout=90)
        except subprocess.TimeoutExpired:
            result_path.write_text(json.dumps(unverified('awaiting_system_install_confirmation'))+'\n',encoding='utf-8')
            raise RuntimeError('Phone system installation confirmation is still pending; confirm Mdo Browser Use Dev and rerun this gate')
        if not installed.endswith('Success'):raise RuntimeError('Android dev package installation did not complete')
    install_receipt.write_text(json.dumps({'package':PACKAGE,'state':'installed','apk_sha256':build['apk_sha256']})+'\n',encoding='utf-8')
    nonce=secrets.token_hex(16);adb('shell','am','force-stop',PACKAGE)
    try:
        adb('shell','am','start','-W','-n',PACKAGE+'/org.xserver.android.BrowserUseDevActivity','--es','nonce',nonce,'--es','mode',args.mode)
        deadline=time.monotonic()+25;result=None
        while time.monotonic()<deadline:
            pid=adb('shell','pidof',PACKAGE,check=False)
            if re.fullmatch(r'[0-9]+',pid):
                raw=adb('logcat','-d','-v','raw','--pid='+pid,'-s','MdoBrowserUseProbe:I','*:S')
                for row in raw.splitlines():
                    if not row.startswith('{'):continue
                    try:entry=json.loads(row)
                    except ValueError:continue
                    if entry.get('nonce')==nonce:result=entry;break
            if result:break
            time.sleep(0.4)
        if not result:raise RuntimeError('Device provider probe returned no terminal receipt')
        result.pop('nonce');result.update({'state':'complete','apk_sha256':build['apk_sha256'],
            'scope':MODES[args.mode][1],
            'existing_mdo_preserved':True,'ndk_required':False,'guarded_native_input_verified':False,
            'guarded_native_capture_verified':False,'capture_privacy_verified':False,'host_dialog_routing_verified':False})
        result.setdefault('native_wheel_verified',False)
        result.setdefault('native_hover_verified',False)
        result.setdefault('native_capture_verified',False)
        result.setdefault('native_dialog_verified',False)
        result.setdefault('before_unload_framework_verified',False)
        result.setdefault('native_popup_verified',False)
        result.setdefault('native_chooser_verified',False)
        result.setdefault('chooser_framework_verified',False)
        result.update({'host_popup_routing_verified':False,'popup_world_qualified':False})
        result.update({'host_chooser_routing_verified':False,'chooser_assignment_verified':False,'chooser_frame_identity_verified':False})
        result['manual_chooser_framework_verified']=False
        result.update({'host_upload_routing_verified':False,'upload_uri_assignment_verified':False})
        result.setdefault('native_upload_staging_verified',False)
        result.setdefault('private_upload_cache_authority_verified',False)
        result.setdefault('native_upload_uri_proxy_verified',False)
        result.setdefault('uri_proxy_framework_verified',False)
        result.setdefault('upload_uri_revocation_verified',False)
        if args.mode in ('native-dialog','native-popup','native-chooser','native-upload','native-upload-uri'):
            result.update({'automation_verified':False,'production_document_controller_verified':False,'world_isolation_verified':False})
        result_path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
        if not result.get('probe_verified'):raise RuntimeError('Installed provider gate failed: '+str(result.get('code')))
    finally:adb('shell','am','force-stop',PACKAGE,check=False)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
