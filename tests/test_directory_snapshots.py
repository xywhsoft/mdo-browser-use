"""Exercise the actual C/XRT directory manifest service in the one deployment."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class DirectorySnapshotTests(unittest.TestCase):
    scope={'extension':'browser-tree-qa','session':'directory-snapshot-qa'}

    def setUp(self):
        self.parent=dev.ENV/'qa';self.parent.mkdir(exist_ok=True)
        self.root=self.parent/('tree-'+self._testMethodName)
        self.assertFalse(self.root.exists(),'Preserve unexpected fixture from an interrupted run')
        self.root.mkdir()
        self.addCleanup(self.cleanup)

    def cleanup(self):
        # Verify the absolute recursive target before removing this test's tree.
        target=self.root.resolve();parent=self.parent.resolve()
        if target.parent!=parent or not target.name.startswith('tree-test_'):
            raise RuntimeError('Refusing cleanup outside this known fixture directory')
        if self.root.exists():shutil.rmtree(self.root)

    def snapshot(self,**extra):
        return dev.call('files.tree',{'path':str(self.root),**extra},self.scope)

    def failure(self,params,status,code=None):
        actual,_,raw=dev.request('POST','/api/v1/application/call',{
            'method':'files.tree','params':{'path':str(self.root),**params},'context':self.scope})
        result=json.loads(raw);self.assertEqual(actual,status,result)
        if code:self.assertEqual(result['error']['code'],code)
        self.assertNotIn('data',result);self.assertNotIn('files',result)
        self.assertNotIn('sha256',result);self.assertNotIn('base64',result)
        self.assertFalse(result['error']['details']['may_have_completed'])
        return result

    def sample(self):
        contents={'b.bin':bytes(range(256))*257,'a.txt':b'selected bytes',
                  'nested/zero.bin':b'','nested/中文.txt':'测试内容'.encode()}
        (self.root/'nested'/'empty').mkdir(parents=True);(self.root/'空目录').mkdir()
        for relative,data in contents.items():(self.root/relative).write_bytes(data)
        return contents

    def test_complete_deterministic_tree_preserves_empty_directories_and_unicode(self):
        contents=self.sample();result=self.snapshot();again=self.snapshot(revision=result['revision'])
        self.assertEqual(result,again);self.assertEqual(result['format'],'mdo.file-tree.v1')
        self.assertEqual(result['file_count'],4);self.assertEqual(result['directory_count'],3)
        self.assertEqual(result['total_bytes'],sum(map(len,contents.values())))
        self.assertEqual(result['root']['path'],'.');self.assertRegex(result['revision'],r'^t1-[0-9a-f]{64}$')
        self.assertEqual([row['path'] for row in result['files']],sorted(contents))
        self.assertEqual([row['path'] for row in result['directories']],['nested','nested/empty','空目录'])
        for row in result['files']:
            data=contents[row['path']];self.assertEqual(row['sha256'],hashlib.sha256(data).hexdigest())
            self.assertEqual(row['size'],len(data));self.assertNotIn('base64',row);self.assertNotIn('text',row)
            digest=dev.call('files.digest',{'path':str(self.root/row['path'])},self.scope)
            self.assertEqual(row['revision'],digest['revision']);self.assertEqual(row['sha256'],digest['sha256'])
        scoped=dev.call('files.tree',{'path':'.','space':'workspace'},{**self.scope,'workspace':str(self.root)})
        self.assertEqual(scoped,result)
        description=dev.call('sdk.describe',{'id':'files.tree'},self.scope)
        self.assertEqual(description['input_schema']['properties']['max_depth']['maximum'],16)
        self.assertIn('directories',description['output_schema']['required'])

    def test_empty_root_is_a_complete_manifest(self):
        result=self.snapshot(max_directories=0,max_depth=0,max_bytes=1)
        self.assertEqual(result['files'],[]);self.assertEqual(result['directories'],[])
        self.assertEqual(result['total_bytes'],0);self.assertEqual(result['file_count'],0)
        self.assertEqual(result['revision'],self.snapshot()['revision'])

    def test_changed_content_even_with_restored_size_and_mtime_invalidates_revision(self):
        path=self.root/'same.bin';path.write_bytes(b'first bytes');before=self.snapshot();stat=path.stat()
        path.write_bytes(b'other bytes');os.utime(path,ns=(stat.st_atime_ns,stat.st_mtime_ns))
        current=self.snapshot();self.assertNotEqual(before['revision'],current['revision'])
        self.assertEqual(current['files'][0]['sha256'],hashlib.sha256(b'other bytes').hexdigest())
        self.failure({'revision':before['revision']},412,'sdk_tree_conflict')

    def test_add_remove_rename_and_empty_directory_changes_invalidate_manifest(self):
        contents=self.sample();previous=self.snapshot()
        for source,destination in (('a.txt','renamed.txt'),('nested/empty','nested/renamed')):
            (self.root/source).rename(self.root/destination)
            self.failure({'revision':previous['revision']},412,'sdk_tree_conflict');previous=self.snapshot()
        (self.root/'new-empty').mkdir()
        self.failure({'revision':previous['revision']},412,'sdk_tree_conflict');previous=self.snapshot()
        (self.root/'b.bin').unlink()
        self.failure({'revision':previous['revision']},412,'sdk_tree_conflict')
        self.assertEqual(self.snapshot()['file_count'],len(contents)-1)

    def test_all_budgets_fail_without_a_partial_manifest(self):
        self.sample()
        for extra in ({'max_files':3},{'max_directories':2},{'max_depth':1},{'max_bytes':1},{'max_file_bytes':1}):
            with self.subTest(extra=extra):self.failure(extra,413,'sdk_tree_limit')
        for field,value in (('max_files',129),('max_files',True),('max_directories',-1),('max_depth',17),
                            ('max_bytes',67108865),('max_file_bytes',33554433)):
            with self.subTest(field=field,value=value):self.failure({field:value},400)
        self.failure({'revision':'f-not-a-tree'},422,'sdk_arguments_invalid')
        self.assertEqual(self.snapshot()['file_count'],4)

    def test_count_and_depth_boundaries_are_enforced(self):
        for index in range(128):(self.root/f'{index:03}.bin').touch()
        self.assertEqual(self.snapshot()['file_count'],128)
        extra=self.root/'overflow.bin';extra.touch();self.failure({},413,'sdk_tree_limit');extra.unlink()
        child=self.root
        for _ in range(16):child=child/'d';child.mkdir()
        self.assertEqual(self.snapshot()['directory_count'],16)
        (child/'overflow').mkdir();self.failure({},413,'sdk_tree_limit')

    def test_regular_file_and_missing_root_fail_explicitly(self):
        file=self.root/'plain.bin';file.write_bytes(b'not a directory')
        self.failure({'path':str(file)},422,'sdk_tree_type')
        self.failure({'path':str(self.root/'missing')},404,'sdk_native_failed')

    def test_directory_count_file_size_and_name_byte_limits(self):
        for index in range(128):(self.root/f'd{index:03}').mkdir()
        self.assertEqual(self.snapshot()['directory_count'],128)
        extra=self.root/'overflow';extra.mkdir();self.failure({},413,'sdk_tree_limit');extra.rmdir()
        file=self.root/'oversized.bin'
        with file.open('wb') as stream:stream.truncate(33554433)
        self.failure({},413,'sdk_tree_limit');file.unlink()
        long_name=self.root/('n'*241)
        # Python's legacy Win32 path limit must not prevent creation of the
        # valid NTFS entry used to test the service's UTF-8 component budget.
        native_name=Path('\\\\?\\'+str(long_name)) if os.name=='nt' else long_name
        native_name.touch()
        try:self.failure({},422,'sdk_tree_path')
        finally:native_name.unlink()
        self.assertEqual(self.snapshot()['file_count'],0)

    @unittest.skipUnless(os.name=='nt','Windows unprivileged junction and sharing checks')
    def test_directory_reparse_entries_are_not_followed_or_silently_omitted(self):
        # Both roots are this test's synthetic data. No external/user path is read.
        target=self.root/'target';target.mkdir();(target/'secret.bin').write_bytes(b'fixture only')
        link=self.root/'alias'
        created=subprocess.run(['cmd.exe','/d','/c','mklink','/J',str(link),str(target)],capture_output=True)
        self.assertEqual(created.returncode,0,created.stderr.decode(errors='replace'))
        try:
            self.failure({},422,'sdk_tree_type');self.failure({'path':str(link)},422,'sdk_tree_type')
        finally:
            # Remove only the reparse entry; never recursively traverse its target.
            os.rmdir(link)
        self.assertEqual(self.snapshot()['file_count'],1)

    @unittest.skipUnless(os.name=='nt','Windows deny-write snapshot handles')
    def test_held_writer_does_not_produce_a_partial_hash_or_leak_snapshot_handles(self):
        selected=self.root/'held.bin';selected.write_bytes(b'fixture')
        with selected.open('r+b'):
            for _ in range(3):
                actual,_,raw=dev.request('POST','/api/v1/application/call',{
                    'method':'files.tree','params':{'path':str(self.root)},'context':self.scope})
                result=json.loads(raw);self.assertGreaterEqual(actual,400,result)
                self.assertEqual(result['error']['code'],'sdk_native_failed');self.assertNotIn('data',result)
        self.assertEqual(self.snapshot()['file_count'],1)
        selected.rename(self.root/'released.bin')
        self.assertEqual(self.snapshot()['files'][0]['path'],'released.bin')


if __name__=='__main__':unittest.main()
