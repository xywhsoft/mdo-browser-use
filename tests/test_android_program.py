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


if __name__=='__main__':unittest.main()
