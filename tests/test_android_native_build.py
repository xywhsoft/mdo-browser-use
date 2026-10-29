"""A partial build or changed input/binary must not qualify for native reuse."""
import hashlib
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import build_android_native as native


class NativeReuseTests(unittest.TestCase):
    def setUp(self):
        self.root=native.ENV/'qa/android-native-reuse'
        if self.root.is_symlink() or self.root.resolve()!=self.root.absolute():
            raise RuntimeError('Linked native receipt fixture preserved')
        owner=self.root/'.browser-use-owner.json';expected={'purpose':'mdo-browser-use/android-native-reuse-test'}
        if self.root.exists() and (not owner.is_file() or json.loads(owner.read_text())!=expected):
            raise RuntimeError('Unknown native receipt fixture preserved')
        self.root.mkdir(parents=True,exist_ok=True);owner.write_text(json.dumps(expected)+'\n')
        self.file=self.root/'synthetic-data.bin'
        if self.file.exists():raise RuntimeError('Interrupted native receipt fixture preserved')
        self.file.write_bytes(b'fixture')
        self.inputs={'source':'one','compiler':'one','profile':'one'}
        self.record=dict(phase='verified',inputs=self.inputs,binary_sha256=hashlib.sha256(b'fixture').hexdigest())

    def tearDown(self):
        if self.file.is_symlink() or self.file.resolve().parent!=self.root.resolve():
            raise RuntimeError('Changed native receipt fixture preserved')
        self.file.unlink()

    def test_partial_build_and_input_drift_cannot_be_reused(self):
        native.validate_build_state(self.record,self.inputs,self.file)
        for record in ({**self.record,'phase':'building'},
                       {**self.record,'inputs':{**self.inputs,'source':'changed'}},
                       {**self.record,'inputs':{**self.inputs,'compiler':'changed'}},
                       {**self.record,'inputs':{**self.inputs,'profile':'changed'}},
                       {**self.record,'binary_sha256':'0'*64}):
            with self.subTest(record=record),self.assertRaises(RuntimeError):
                native.validate_build_state(record,self.inputs,self.file)

    def test_modified_binary_cannot_be_reused(self):
        self.file.write_bytes(b'modified')
        with self.assertRaises(RuntimeError):native.validate_build_state(self.record,self.inputs,self.file)


if __name__=='__main__':unittest.main()
