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


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--adb',type=Path,default=Path('D:/GIT/mdo/.build/android-platform-tools/platform-tools/adb.exe'))
    parser.add_argument('--mode',choices=('world','native-input'),default='world')
    args=parser.parse_args();directory=ROOT/'.mdo/host/android-client';apk=directory/'mdo-browser-use-dev.apk'
    build=json.loads((directory/'build.json').read_text(encoding='utf-8'))
    result_path=ROOT/'.mdo'/('android-device-result.json' if args.mode=='world' else 'android-device-input-result.json')
    result_path.write_text('{"state":"running","automation_verified":false,"production_document_controller_verified":false,"guarded_native_input_verified":false,"native_wheel_verified":false}\n',encoding='utf-8')
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
        result_path.write_text(json.dumps({'state':'unavailable','code':'android_device_unavailable',
            'automation_verified':False,'native_input_verified':False,'production_document_controller_verified':False,
            'guarded_native_input_verified':False,'native_wheel_verified':False,'existing_mdo_preserved':True})+'\n',encoding='utf-8')
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
            result_path.write_text('{"state":"awaiting_system_install_confirmation","automation_verified":false,"guarded_native_input_verified":false,"native_wheel_verified":false}\n',encoding='utf-8')
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
            'scope':'installed-provider-world-gate' if args.mode=='world' else 'native-input-framework-gate',
            'existing_mdo_preserved':True,'ndk_required':False,'guarded_native_input_verified':False})
        result.setdefault('native_wheel_verified',False)
        result_path.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
        if not result.get('probe_verified'):raise RuntimeError('Installed provider gate failed: '+str(result.get('code')))
    finally:adb('shell','am','force-stop',PACKAGE,check=False)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
