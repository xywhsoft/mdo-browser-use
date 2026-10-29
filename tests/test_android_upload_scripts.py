from pathlib import Path
import json
import re
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import update_android_upload_broker as scripts


class SharedUploadPickerScriptsTests(unittest.TestCase):
    def test_generated_java_matches_the_canonical_private_helper(self):
        scripts.verify()
        text=scripts.SOURCE.read_text(encoding='utf-8')
        body=json.loads(re.search(r'INSTALL=(.*);',scripts.OUTPUT.read_text(encoding='utf-8'))[1])
        self.assertEqual(body,scripts.installer(text))
        self.assertNotIn('export function',body)
        self.assertLess(len(body.encode('utf-8')),32768)

    def test_missing_ambiguous_imported_truncated_or_oversized_helpers_are_rejected(self):
        baseline=scripts.SOURCE.read_text(encoding='utf-8')
        for text in (baseline.replace(scripts.EXPORT,'function wrong('),baseline+baseline,
                     baseline+'\nimport something from "private";',baseline[:-4],baseline+' '*32769,baseline+'\x00'):
            with self.subTest(case=text[:16]):
                with self.assertRaises(RuntimeError):scripts.render(text)


if __name__=='__main__':unittest.main()
