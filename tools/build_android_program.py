"""Package the actual enhanced mdo Activity/engine under the existing QA app ID.

Reuse the verified portable app archive and one ARM64 build. Do not pack Home,
QA pages, accounts or signing material. This is a development APK, not a release.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import struct
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

import build_android_client as client
import build_android_native as native
import dev
import package_plugin
import package_program as program

ROOT=dev.ROOT
WORK=dev.ENV/'host/android-program'
OUTPUT=ROOT/'dist/mdo-browser-use-dev-android-arm64-v8a.apk'
PACKAGE=client.PACKAGE
RECEIPT=OUTPUT.with_suffix('.receipt.json')
HOST_CLASSES=('XsActivity','XsService','MdoBrowser','MdoBrowser$ClearAction','MdoBrowser$NetworkEntry','MdoRuntime','MdoBrowserWorld','MdoBrowserWorld$Failure','MdoBrowserDocument','MdoBrowserManualChooser','MdoBrowserPickerUris',
              'MdoBrowserUploadProvider','MdoBrowserCdpNative','MdoBrowserCdpNative$Channel','MdoBrowserCdpJson','MdoBrowserCdpSession',
              'MdoBrowserCdpSession$ControlReceipt','MdoBrowserCdpSession$ControlResult','MdoBrowserCdpResponse',
              'MdoBrowserCdpSession$Body','MdoBrowserCdpSession$BodyResult','MdoBrowserCdpSession$BodyChunk',
              'MdoBrowserCdpSession$Offer','MdoBrowserCdpSession$OfferDecision','MdoBrowserCdpSession$OfferResult',
              'MdoBrowserCdpMarker','MdoBrowserCdpView','MdoBrowserCdpBinding','MdoBrowserCdpBinding$Network','MdoBrowserCdpRegistry','MdoBrowserCdpWorker',
              'MdoBrowserCdpNetworks','MdoBrowserCdpNetworks$Slot','MdoBrowserCdpNetworks$Result','MdoBrowserCdpSession$Cooperation','MdoBrowserCdpFrames','MdoBrowserCdpWorlds',
              'MdoBrowserCdpOwnerScripts','MdoBrowserCdpFrameRequest','MdoBrowserCdpFrameChain','MdoBrowserCdpInput','MdoBrowserCdpCapture','MdoBrowserCdpFileInput','MdoBrowserCdpFileInput$BeforeClear','MdoBrowserCdpChoosers','MdoBrowserCdpUploadScripts','MdoBrowserCdpUpload','MdoBrowserCdpUpload$Ready','MdoBrowserCdpUpload$Receipt','MdoBrowserUploadCoordinator','MdoBrowserUploadCoordinator$Result','MdoBrowserCdpWorlds$Pin','MdoBrowserCapture','MdoBrowserPrivatePicker','MdoBrowserPrivatePickerReply',
              'MdoBrowserUploadStore$RetentionOwner','MdoBrowserUploadStore$Retention','MdoBrowserUploadStore$PickerGrant',
              'MdoBrowserDownloadFiles','MdoBrowserDownloadFiles$Writer','MdoBrowserDownloadFiles$Failure',
              'MdoBrowserDownloadQueue','MdoBrowserDownloadQueue$Ticket','MdoBrowserDownloadQueue$Snapshot',
              'MdoBrowserCdpDownloads','MdoBrowserCdpDownloads$Snapshot','MdoBrowserCdpDownloads$Release','MdoBrowserCdpDownloads$Budget','MdoBrowserCdpDownloads$Row')


def sha(path):return native.sha(path)


def owner():
    if WORK.is_symlink() or not WORK.resolve().is_relative_to(dev.ENV.resolve()):
        raise RuntimeError('Android program output left its owned deployment')
    marker=WORK/'.browser-use-owner.json';expected={'purpose':'mdo-browser-use/android-program'}
    if WORK.exists() and (not marker.is_file() or json.loads(marker.read_text())!=expected):
        raise RuntimeError('Unknown Android program output preserved')
    WORK.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(expected)+'\n')


def app_archive_identity():
    """Verify current reviewed inputs against the already exercised app archive."""
    import apply_host
    apply_host.apply(check=True)
    artifact=program.OUTPUT
    receipt=json.loads(artifact.with_suffix('.receipt.json').read_text())
    if (receipt.get('format')!='mdo.browser-use.program-receipt.v1' or receipt.get('baseline')!=dev.LOCK['commit']
            or not receipt.get('native_inventory_verified') or not receipt.get('runtime_verified')
            or receipt.get('official_update_channel')!='disabled_development_preview'
            or sha(artifact)!=receipt['artifact']['sha256'] or artifact.stat().st_size!=receipt['artifact']['bytes']):
        raise RuntimeError('The verified Windows app archive is missing/changed; rebuild and exercise it first')
    allowed=program.allowed_files();files=receipt['files'];seen=set()
    if set(files)-allowed:raise RuntimeError('Application receipt contains unreviewed paths')
    for path in program.APP.rglob('*'):
        if package_plugin.linked(path.lstat()):raise RuntimeError('Application source link preserved')
        if path.is_dir():continue
        relative=path.relative_to(program.APP).as_posix()
        if relative in files:
            data=path.read_bytes()
            if relative==program.UNITY:data=program.PROGRAM_PREFIX+data
            if len(data)!=files[relative]['bytes'] or hashlib.sha256(data).hexdigest()!=files[relative]['sha256']:
                raise RuntimeError('App archive is stale: '+relative)
            seen.add(relative)
        elif not (relative in {'.browser-use.dev.json','.browser-use.run.json','generated/native/windows/cli-forwarder.build.json'}
                  or re.fullmatch(r'web/__browser_use_[a-z0-9_]+__\.html',relative)):
            raise RuntimeError('Unknown application file retained: '+relative)
    if seen!=set(files):raise RuntimeError('App archive source is incomplete')
    listing=subprocess.check_output([str(dev.ENV/'host/xs.exe'),'pack','--list',str(artifact)],cwd=ROOT,text=True)
    inventory={match[2]:int(match[1]) for line in listing.splitlines() if (match:=re.fullmatch(r'\s*(\d+) (.+)',line))}
    if inventory!={name:row['bytes'] for name,row in files.items()}:raise RuntimeError('Native app archive inventory changed')
    return receipt['artifact']


def elf_facts(data):
    if len(data)<64 or data[:7]!=b'\x7fELF\x02\x01\x01' or struct.unpack_from('<HH',data,16)!=(3,183):
        raise RuntimeError('APK native executable must be ARM64 little-endian ELF64/PIE')
    offset=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
    if size!=56 or not 1<=count<=128 or offset+count*size>len(data):raise RuntimeError('Invalid native ELF program headers')
    loads=[]
    for index in range(count):
        row=offset+index*size
        if struct.unpack_from('<I',data,row)[0]!=1:continue
        start,address=struct.unpack_from('<QQ',data,row+8);alignment=struct.unpack_from('<Q',data,row+48)[0]
        if alignment<16384 or alignment&(alignment-1) or (address-start)%alignment:
            raise RuntimeError('Native ELF does not satisfy 16 KiB page alignment')
        loads.append(alignment)
    if not loads:raise RuntimeError('Native ELF has no loadable segment')
    return {'machine':'AArch64','bits':64,'minimum_segment_alignment':min(loads)}


def verify_private_provider(xml):
    """Check the actual aapt2-compiled provider, including its own booleans."""
    providers=[];block=None;indent=0
    for line in xml.splitlines():
        depth=len(line)-len(line.lstrip())
        if block is not None and line.strip() and depth<=indent:
            providers.append('\n'.join(block));block=None
        if re.match(r'^\s*E: provider(?:\s|$)',line):block=[line];indent=depth
        elif block is not None:block.append(line)
    if block is not None:providers.append('\n'.join(block))
    if len(providers)!=1:raise RuntimeError('Expected exactly one private upload provider')
    block=providers[0]
    for name,value in (('name','org.xserver.android.MdoBrowserUploadProvider'),('authorities',PACKAGE+'.mdo.browseruse.upload')):
        if not re.search(r':'+name+r'\([^)]*\)="'+re.escape(value)+r'"',block):
            raise RuntimeError('Compiled private upload provider identity changed')
    for name in ('exported','grantUriPermissions'):
        if not re.search(r':'+name+r'\([^)]*\)=false\s*$',block,re.M):
            raise RuntimeError('Compiled upload provider is not private')


def verify_dex_definitions(report):
    """Descriptor strings can be references. Require SDK-reported definitions."""
    if len(report)>16*1024**2:raise RuntimeError('DEX report exceeds inspection budget')
    try:root=ET.fromstring(report)
    except ET.ParseError as error:raise RuntimeError('Invalid SDK DEX class-definition report') from error
    if root.tag!='api':raise RuntimeError('Unexpected SDK DEX report root')
    names=set()
    for package in root.findall('package'):
        for definition in package.findall('class'):
            name=package.get('name','')+'.'+definition.get('name','')
            if name in names or len(names)>=4096:raise RuntimeError('Invalid/oversized DEX class definitions')
            names.add(name)
    if not {'org.xserver.android.'+name for name in HOST_CLASSES}.issubset(names):
        raise RuntimeError('Actual host class definition missing from APK DEX')
    if any(name.startswith(('android.','org.json.','androidx.','org.chromium.')) for name in names):
        raise RuntimeError('APK unexpectedly bundles platform/test/browser runtime classes')
    return names


def apk_contents(plan):
    with zipfile.ZipFile(OUTPUT) as apk:
        entries=apk.infolist();names=[entry.filename for entry in entries]
        if len(names)!=len(set(names)) or len(names)>1000 or sum(entry.file_size for entry in entries)>32*1024**2:
            raise RuntimeError('Unexpected APK duplicate/entry/expanded-size budget')
        if apk.testzip() is not None:raise RuntimeError('APK content checksum failure')
        required={'AndroidManifest.xml','classes.dex','assets/app.xrtpack','assets/mdo-runtime.json',
                  'assets/licenses/tcc-LGPL.txt','lib/arm64-v8a/libxs.so','lib/arm64-v8a/libmdo_command_worker.so'}
        if not required.issubset(names):raise RuntimeError('The full mdo Activity/engine APK is incomplete')
        if set(names)-required-{'resources.arsc'}-{name for name in names if name.startswith(('res/','META-INF/'))}:
            raise RuntimeError('Unexpected APK payload; do not ship local data or extra runtimes')
        for name,expected in (('assets/app.xrtpack',plan['pack_sha256']),('lib/arm64-v8a/libxs.so',plan['native_sha256'])):
            if hashlib.sha256(apk.read(name)).hexdigest()!=expected:raise RuntimeError('APK contains a changed native/app payload')
        runtime=json.loads(apk.read('assets/mdo-runtime.json'))
        if runtime.get('edition')!='lite' or runtime.get('build_id')!='1' or runtime.get('files')!=[]:
            raise RuntimeError('Development APK unexpectedly bundles command-line toolpacks')
        setup=json.loads((dev.ENV/'android-client-tools/receipt.json').read_text(encoding='utf-8'))
        dexdump=dev.ENV/'android-client-tools/sdk/build-tools'/setup['build_tools']/'dexdump'
        report=subprocess.check_output([str(dexdump),'-l','xml',str(OUTPUT)],text=True,timeout=30)
        verify_dex_definitions(report)
        return {name:elf_facts(apk.read('lib/arm64-v8a/'+name)) for name in ('libxs.so','libmdo_command_worker.so')}


def assemble():
    owner();plan=json.loads((WORK/'inputs.json').read_text())
    library=native.OUTPUT/'libxs.so';pack=WORK/'app.xrtpack'
    if (plan.get('baseline')!=dev.LOCK['commit'] or plan.get('builder_sha256')!=sha(Path(__file__))
            or plan.get('android_source_sha256')!=client.source_hash() or plan.get('native_sha256')!=sha(library)
            or plan.get('pack_sha256')!=sha(pack)):
        raise RuntimeError('Android packaging inputs changed before assembly')
    tooling=dev.ENV/'android-client-tools';setup=json.loads((tooling/'receipt.json').read_text())
    sdk=tooling/'sdk';java=Path(setup['java_home']);tools=sdk/'build-tools'/setup['build_tools']
    toolchain=json.loads((native.NATIVE/'tools/android/toolchain.lock.json').read_text(encoding='utf-8'))
    nm=sdk/'ndk'/toolchain['ndk']/'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm'
    symbols=subprocess.check_output([str(nm),'--dynamic','--defined-only',str(library)],text=True)
    for entry in ('discoverNative','openNative','sendNative','receiveNative','closeNative','validateNative'):
        if not re.search(r'\bT Java_org_xserver_android_MdoBrowserCdpNative_'+entry+r'$',symbols,re.M):
            raise RuntimeError('The Android host lacks a matching private CDP JNI entry')
    if plan['toolchain_lock_sha256']!=setup['lock_sha256']:raise RuntimeError('Android tooling lock changed')
    keystore=dev.ENV/'host/android-client/development.p12'
    if keystore.is_symlink() or not keystore.is_file():raise RuntimeError('Existing QA development signer is required; it will not be replaced')
    os.environ.update(MDO_ANDROID_XSERVER_ROOT=str(native.NATIVE),MDO_ANDROID_EDITION='lite',
                      XS_BUILD_COMMIT=plan['native_revision'],MDO_BROWSER_USE_DEV_SIGNING_PASSWORD='android')
    sys.path.insert(0,str(dev.SOURCE/'tools/android'))
    builder=runpy.run_path(str(dev.SOURCE/'tools/android/build_apk.py'))
    builder['build'](sdk,java,library,pack,OUTPUT,PACKAGE,'Mdo Browser Use Dev',dev.LOCK['version'],1,keystore,
        True,'mdo-home',dev.SOURCE/'assets/branding/android/res',[],False,'browser-use-development',
        'MDO_BROWSER_USE_DEV_SIGNING_PASSWORD','MDO_BROWSER_USE_DEV_SIGNING_PASSWORD')
    facts=apk_contents(plan)
    badging=subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(OUTPUT)],text=True)
    if (f"package: name='{PACKAGE}' versionCode='1'" not in badging or
            "launchable-activity: name='org.xserver.android.XsActivity'" not in badging or
            'application-debuggable' not in badging or 'android.permission.REQUEST_INSTALL_PACKAGES' in badging):
        raise RuntimeError('Development APK identity/main Activity/permissions differ from the plan')
    xml=subprocess.check_output([str(tools/'aapt2'),'dump','xmltree',str(OUTPUT),'--file','AndroidManifest.xml'],text=True)
    verify_private_provider(xml)
    if 'android:autoVerify' in xml or 'org.xserver.android.PackageInstall$ResultReceiver' in xml:
        raise RuntimeError('Development APK provider/App Links/update handler differ from the plan')
    signer=[str(java/'bin/java'),'-jar',str(tools/'lib/apksigner.jar'),'verify','--print-certs']
    certs=[]
    for file in (dev.ENV/'host/android-client/mdo-browser-use-dev.apk',OUTPUT):
        output=subprocess.check_output([*signer,str(file)],text=True)
        values=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$',output,re.M)
        if len(values)!=1:raise RuntimeError('Expected one development signing certificate')
        certs.append(values[0])
    if certs[0]!=certs[1]:raise RuntimeError('QA signing identity changed')
    if client.source_hash()!=plan['android_source_sha256']:raise RuntimeError('Android sources changed during packaging')
    record=dict(format='mdo.browser-use.android-program-receipt.v1',phase='verified',package=PACKAGE,version_code=1,
                artifact={'name':OUTPUT.name,'bytes':OUTPUT.stat().st_size,'sha256':sha(OUTPUT)},inputs=plan,
                certificate_sha256=certs[0],native_elf=facts,production_activity_packaged=True,
                command_worker_packaged=True,private_upload_provider_packaged=True,private_upload_manifest_verified=True,private_cdp_discovery_packaged=True,application_copies=0,
                private_cdp_session_packaged=True,private_cdp_dispatcher_packaged=True,private_fixed_native_controls_packaged=True,private_physical_network_binding_packaged=True,private_cooperative_network_pump_packaged=True,actual_dex_definitions_verified=True,
                test_json_sources_bundled=False,runtime_dependencies_added=False,device_qualified=False,framework_qualified=False,automation_qualified=False)
    RECEIPT.write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps(record['artifact'],indent=2),flush=True)


def main():
    if os.name!='nt':assemble();return
    subprocess.run([sys.executable,str(ROOT/'tools/build_android_native.py'),'--reuse'],cwd=ROOT,check=True)
    owner();app=app_archive_identity()
    if OUTPUT.is_symlink() or OUTPUT.parent.is_symlink():raise RuntimeError('Linked Android artifact output preserved')
    if OUTPUT.exists():
        old=json.loads(RECEIPT.read_text())
        if old.get('format')!='mdo.browser-use.android-program-receipt.v1' or old['artifact']['sha256']!=sha(OUTPUT):
            raise RuntimeError('Unknown/changed Android development APK preserved')
    plugin_name,_,plugin=package_plugin.build()
    if sha(ROOT/'dist'/plugin_name)!=plugin['artifact']['sha256']:raise RuntimeError('Paired portable plugin artifact is stale')
    sys.path.insert(0,str(dev.SOURCE/'tools'))
    import build_android
    pack=WORK/'app.xrtpack'
    build_android.extract_pack(program.OUTPUT,pack)
    native_record=json.loads((native.OUTPUT/'browser-use-build.json').read_text())
    setup=json.loads((dev.ENV/'android-client-tools/receipt.json').read_text())
    plan=dict(baseline=dev.LOCK['commit'],builder_sha256=sha(Path(__file__)),source_archive=app,
              native_sha256=native_record['binary_sha256'],native_revision=native_record['inputs']['source']['revision'],
              pack_sha256=sha(pack),android_source_sha256=client.source_hash(),toolchain_lock_sha256=setup['lock_sha256'],
              paired_plugin=plugin['artifact'],source_edition_note='Android __ANDROID__ selects lite before the desktop edition macro')
    (WORK/'inputs.json').write_text(json.dumps(plan,indent=2)+'\n')
    script=Path(__file__).resolve().as_posix();script='/mnt/'+script[0].lower()+script[2:]
    subprocess.run(['wsl.exe','--exec','python3',script],cwd=ROOT,check=True)
    result=json.loads(RECEIPT.read_text())
    if result['phase']!='verified' or result['artifact']['sha256']!=sha(OUTPUT) or client.source_hash()!=plan['android_source_sha256'] or app_archive_identity()!=app:
        raise RuntimeError('APK output/source changed after assembly')
    print('Verified complete Android development APK; device qualification remains false',flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,KeyError,subprocess.SubprocessError,zipfile.BadZipFile) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
