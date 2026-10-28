"""Exercise actual XRT-backed file snapshots used by browser transfer staging."""
import base64
import hashlib
import json
import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import dev


class FileSnapshotTests(unittest.TestCase):
    scope={"extension":"browser-transfer-qa","session":"file-snapshot-qa"}

    @classmethod
    def setUpClass(cls):
        cls.directory=dev.ENV/"qa";cls.directory.mkdir(exist_ok=True)
        cls.path=cls.directory/"upload-snapshot-中文.bin"
        cls.empty=cls.directory/"upload-snapshot-empty.bin"
        cls.data=bytes(range(256))*1031+b"end"
        cls.path.write_bytes(cls.data);cls.empty.write_bytes(b"")

    def error(self,method,params,status,code=None):
        actual,_,raw=dev.request("POST","/api/v1/application/call",{
            "method":method,"params":params,"context":self.scope})
        parsed=json.loads(raw)
        self.assertEqual(actual,status,parsed)
        if code:self.assertEqual(parsed["error"]["code"],code)
        self.assertNotIn("base64",parsed);self.assertNotIn("text",parsed)
        return parsed

    def test_complete_hash_and_empty_file_have_no_content_in_result(self):
        for path,data in ((self.path,self.data),(self.empty,b"")):
            with self.subTest(size=len(data)):
                result=dev.call("files.digest",{"path":str(path)},self.scope)
                self.assertEqual(result["sha256"],hashlib.sha256(data).hexdigest())
                self.assertEqual(result["size"],len(data))
                self.assertEqual(result["revision"],dev.call("files.stat",{"path":str(path)},self.scope)["revision"])
                self.assertNotIn("text",result);self.assertNotIn("base64",result)
                self.assertEqual(path.read_bytes(),data)
        description=dev.call("sdk.describe",{"id":"files.digest"},self.scope)
        self.assertEqual(description["input_schema"]["properties"]["max_bytes"]["maximum"],67108864)
        self.assertIn("revision",dev.call("sdk.describe",{"id":"fs.read"},self.scope)["input_schema"]["properties"])
        self.assertNotIn("revision",dev.call("sdk.describe",{"id":"vfs.read"},self.scope)["input_schema"]["properties"])

    def test_revision_ranges_reconstruct_exact_snapshot_and_bound_eof(self):
        snapshot=dev.call("files.digest",{"path":str(self.path)},self.scope)
        assembled=bytearray();offset=0
        while offset<len(self.data):
            chunk=dev.call("files.read",{"path":str(self.path),"revision":snapshot["revision"],
                "offset":offset,"limit":65536},self.scope)
            self.assertEqual(chunk["revision"],snapshot["revision"])
            value=base64.b64decode(chunk["base64"],validate=True)
            self.assertEqual(chunk["bytes"],len(value));self.assertEqual(chunk["next"],offset+len(value))
            assembled.extend(value);offset=chunk["next"]
        self.assertEqual(hashlib.sha256(assembled).hexdigest(),snapshot["sha256"])
        eof=dev.call("files.read",{"path":str(self.path),"revision":snapshot["revision"],"offset":len(self.data)},self.scope)
        self.assertEqual(eof["bytes"],0)
        self.error("files.read",{"path":str(self.path),"revision":snapshot["revision"],"offset":len(self.data)+1},416,"sdk_file_range")

    def test_replacement_cannot_satisfy_old_revision_even_with_same_size_and_time(self):
        selected=self.directory/"upload-snapshot-replace.bin"
        replacement=self.directory/"upload-snapshot-replace-new.bin"
        selected.write_bytes(b"original")
        try:
            stat=selected.stat();old=dev.call("files.digest",{"path":str(selected)},self.scope)
            replacement.write_bytes(b"replaced");os.utime(replacement,ns=(stat.st_atime_ns,stat.st_mtime_ns));os.replace(replacement,selected)
            for method in ("files.digest","files.read","fs.read"):
                self.error(method,{"path":str(selected),"revision":old["revision"]},412,"sdk_file_conflict")
            new=dev.call("files.digest",{"path":str(selected)},self.scope)
            self.assertNotEqual(old["revision"],new["revision"])
            self.assertEqual(new["sha256"],hashlib.sha256(b"replaced").hexdigest())
        finally:
            selected.unlink(missing_ok=True);replacement.unlink(missing_ok=True)

    def test_budget_types_and_directories_fail_before_any_partial_digest(self):
        self.error("files.digest",{"path":str(self.path),"max_bytes":1},413,"sdk_file_limit")
        self.error("files.digest",{"path":str(self.directory)},422,"sdk_file_type")
        for value in (0,-1,67108865,True,"100"):
            self.error("files.digest",{"path":str(self.path),"max_bytes":value},400)
        self.error("files.read",{"path":str(self.path),"revision":""},422,"sdk_arguments_invalid")

    def test_symlink_is_not_an_upload_snapshot(self):
        link=self.directory/"upload-snapshot-link.bin"
        try:
            try:link.symlink_to(self.path)
            except OSError as error:self.skipTest("Symlink unavailable: "+str(error.winerror))
            self.error("files.digest",{"path":str(link)},422,"sdk_file_type")
        finally:link.unlink(missing_ok=True)

    @unittest.skipUnless(os.name=="nt","Actual Windows extended source paths")
    def test_long_source_root_and_leaf_support_digest_tree_and_revision_reads(self):
        root=self.directory/('d'*240)
        self.assertTrue(root.absolute().is_relative_to(dev.ENV))
        native=Path('\\\\?\\'+str(root.absolute()))
        self.assertFalse(native.exists(),'Preserve an interrupted/unknown long source fixture')
        native.mkdir()
        leaf='n'*240;selected=native/leaf;data=b'complete long source bytes'
        try:
            selected.write_bytes(data);path=str(root/leaf)
            snapshot=dev.call("files.digest",{"path":path},self.scope)
            self.assertEqual(snapshot["sha256"],hashlib.sha256(data).hexdigest())
            tree=dev.call("files.tree",{"path":str(root)},self.scope)
            self.assertEqual(tree["files"][0]["path"],leaf)
            self.assertEqual(tree["files"][0]["sha256"],snapshot["sha256"])
            scope=dict(self.scope,workspace=str(root))
            for source,context in ((path,self.scope),('./'+leaf,scope)):
                read=dev.call("files.read",{"path":source,"revision":snapshot["revision"],"limit":65536},context)
                self.assertEqual(base64.b64decode(read["base64"],validate=True),data)
            selected.write_bytes(b'changed source size')
            self.error("files.read",{"path":path,"revision":snapshot["revision"]},412,"sdk_file_conflict")
        finally:
            selected.unlink(missing_ok=True);native.rmdir()

    @unittest.skipUnless(os.name=="nt","Windows file sharing gate")
    def test_active_writer_cannot_be_hashed_or_read_as_a_stable_snapshot(self):
        selected=self.directory/"upload-snapshot-held.bin";selected.write_bytes(b"snapshot")
        try:
            old=dev.call("files.digest",{"path":str(selected)},self.scope)
            with selected.open("r+b"):
                for method,extra in (("files.digest",{}),("files.read",{"revision":old["revision"]})):
                    actual,_,raw=dev.request("POST","/api/v1/application/call",{
                        "method":method,"params":{"path":str(selected),**extra},"context":self.scope})
                    result=json.loads(raw)
                    self.assertGreaterEqual(actual,400,result)
                    self.assertEqual(result["error"]["code"],"sdk_native_failed")
                    self.assertNotIn("sha256",result);self.assertNotIn("base64",result)
            self.assertEqual(dev.call("files.digest",{"path":str(selected)},self.scope)["sha256"],old["sha256"])
        finally:selected.unlink(missing_ok=True)


if __name__=="__main__":unittest.main()
