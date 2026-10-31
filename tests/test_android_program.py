"""Do not qualify a compiled upload provider using another element's flags."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import build_android_program as program

# Shape and attributes observed from the actual pinned aapt2 APK xmltree.
PROVIDER='''          E: provider (line=29)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)="org.xserver.android.MdoBrowserUploadProvider"
            A: http://schemas.android.com/apk/res/android:exported(0x01010010)=false
            A: http://schemas.android.com/apk/res/android:authorities(0x01010018)="org.xleaves.mdo.browseruse.dev.mdo.browseruse.upload"
            A: http://schemas.android.com/apk/res/android:grantUriPermissions(0x0101001b)=false
'''


class CompiledManifestTests(unittest.TestCase):
    def test_compiled_private_provider_is_accepted(self):
        program.verify_private_provider('E: manifest\n'+PROVIDER)

    def test_exposed_or_different_provider_is_rejected(self):
        for xml in (PROVIDER.replace('=false','=true',1),
                    PROVIDER.replace('grantUriPermissions(0x0101001b)=false','grantUriPermissions(0x0101001b)=true'),
                    PROVIDER.replace(program.PACKAGE+'.mdo.browseruse.upload','other.app.upload'),
                    PROVIDER.replace('org.xserver.android.MdoBrowserUploadProvider','other.Provider'),
                    PROVIDER+PROVIDER,'E: manifest'):
            with self.subTest(xml=xml),self.assertRaises(RuntimeError):program.verify_private_provider(xml)

    def test_peer_element_cannot_supply_missing_privacy_flags(self):
        missing='\n'.join(line for line in PROVIDER.splitlines() if ':exported(' not in line)+'\n'
        peer='''          E: service (line=30)
            A: http://schemas.android.com/apk/res/android:exported(0x01010010)=false
'''
        with self.assertRaises(RuntimeError):program.verify_private_provider(missing+peer)


class ActualDexClassesTests(unittest.TestCase):
    def report(self,names=None,extra=''):
        if names is None:names=program.HOST_CLASSES
        return '<api><package name="org.xserver.android">'+''.join('<class name="'+name+'"/>' for name in names)+extra+'</package></api>'

    def test_platform_references_are_allowed_without_bundled_definitions(self):
        report=self.report(extra='<class name="Helper"><method return="org.json.JSONObject"/></class>')
        names=program.verify_dex_definitions(report)
        self.assertIn('org.xserver.android.MdoBrowserCdpSession',names)
        self.assertNotIn('org.json.JSONObject',names)

    def test_class_reference_cannot_substitute_for_implementation(self):
        for required in ('MdoBrowserCdpWorker$Guard','MdoBrowser$NetworkEntry','MdoBrowser$NativeNavigation','MdoBrowser$NavigationRequest','MdoBrowserCdpNetworks$Navigation','MdoBrowserCdpSession','MdoBrowserCdpSession$ControlReceipt','MdoBrowserCdpSession$ControlResult','MdoBrowserCdpSession$Navigation','MdoBrowserCdpSession$NavigationResult','MdoBrowserCdpBinding$Network','MdoBrowserCdpBinding$Network$Navigation','MdoBrowserCdpBinding$Network$Selection','MdoBrowserCdpNetworkRequests','MdoBrowserCdpNetworkRequests$Command','MdoBrowserCdpNetworkRequests$Receipt','MdoBrowserCdpNetworkRequests$Link','MdoBrowserCdpNetworkFrames','MdoBrowserCdpNetworkFrames$Origin','MdoBrowserCdpNetworkFrames$Commit','MdoBrowserCdpNetworkFrames$Start','MdoBrowserCdpNetworkFrames$Traversal','MdoBrowserCdpNetworkFrames$Frame','MdoBrowserCdpNetworkFrames$Scope',
                         'MdoBrowserCdpNetworks','MdoBrowserCdpNetworks$Slot','MdoBrowserCdpNetworks$Result','MdoBrowserCdpSession$Cooperation','MdoBrowserCdpResponse',
                         'MdoBrowserCdpSession$Body','MdoBrowserCdpSession$BodyResult','MdoBrowserCdpSession$BodyChunk',
                         'MdoBrowserCdpSession$Offer','MdoBrowserCdpSession$OfferDecision','MdoBrowserCdpSession$OfferResult',
                         'MdoBrowserDownloadFiles','MdoBrowserDownloadFiles$Writer','MdoBrowserDownloadFiles$Failure',
                         'MdoBrowserDownloadQueue','MdoBrowserDownloadQueue$Ticket','MdoBrowserDownloadQueue$Snapshot','MdoBrowserDownloadQueue$Read',
                         'MdoBrowserCdpDownloads','MdoBrowserCdpDownloads$Connection','MdoBrowserCdpDownloads$Snapshot','MdoBrowserCdpDownloads$Release','MdoBrowserCdpDownloads$Budget','MdoBrowserCdpDownloads$Row',
                         'MdoBrowserDownloadRuntime','MdoBrowser$DownloadRelease','MdoBrowser$DownloadRead'):
            self.assertIn(required,program.HOST_CLASSES)
            report=self.report([name for name in program.HOST_CLASSES if name!=required],
                               '<class name="Helper"><method return="org.xserver.android.'+required+'"/></class>')
            with self.subTest(required=required),self.assertRaises(RuntimeError):program.verify_dex_definitions(report)

    def test_test_json_and_browser_runtime_definitions_cannot_ship(self):
        for package in ('android.os','org.json','androidx.test','org.chromium'):
            report=self.report().replace('</api>','<package name="'+package+'"><class name="Unexpected"/></package></api>')
            with self.subTest(package=package),self.assertRaises(RuntimeError):program.verify_dex_definitions(report)

    def test_invalid_or_duplicate_definition_reports_are_rejected(self):
        for report in ('not XML','<api/>',self.report(extra='<class name="MdoBrowser"/>')):
            with self.subTest(report=report),self.assertRaises(RuntimeError):program.verify_dex_definitions(report)


if __name__=='__main__':unittest.main()
