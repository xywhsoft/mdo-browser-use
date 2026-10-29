from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import update_android_owner_scripts as scripts


class SharedOwnerScriptsTests(unittest.TestCase):
    def test_committed_android_functions_match_the_authoritative_helpers(self):
        scripts.verify()

    def test_missing_duplicate_or_wrong_helper_body_is_rejected(self):
        baseline=scripts.SOURCE.read_text(encoding='utf-8')
        symbol=next(iter(scripts.FUNCTIONS.values()))
        for text in (baseline.replace(symbol,'missing_owner_helper'),
                     baseline+baseline,
                     baseline.replace('R"js(function(point){','R"js(not_a_function{')):
            with self.subTest(case=text[:16]):
                with self.assertRaises(RuntimeError):scripts.render(text)

    def test_oversized_owner_body_is_rejected(self):
        baseline=scripts.SOURCE.read_text(encoding='utf-8')
        with self.assertRaises(RuntimeError):scripts.render(baseline.replace('R"js(function(point){','R"js(function(point){'+(' ' * 16385)))


if __name__=='__main__':unittest.main()
