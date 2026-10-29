import hashlib
import io
import json
from pathlib import Path
import shutil
import stat
import sys
from contextlib import nullcontext
from types import SimpleNamespace
import unittest
from unittest.mock import Mock,patch
import zipfile

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import android_tooling


class Response(io.BytesIO):
    def __init__(self,value,length=None):
        super().__init__(value);self.headers={} if length is None else {'Content-Length':str(length)}


class AndroidArchiveTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.owner=Path(__file__).resolve().parents[1]/'.mdo/qa/android-tooling'
        marker=cls.owner/'.browser-use-owner.json';identity={'purpose':'mdo-browser-use/android-tooling-tests'}
        if cls.owner.is_symlink() or cls.owner.resolve()!=cls.owner.absolute():raise RuntimeError('Linked Android test output preserved')
        if cls.owner.exists() and (not marker.is_file() or json.loads(marker.read_text())!=identity):raise RuntimeError('Unknown Android test output preserved')
        cls.owner.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity)+'\n')

    def setUp(self):
        self.root=self.owner/'files'
        if self.root.exists():raise RuntimeError('Interrupted Android archive fixture preserved')
        self.root.mkdir();memory=io.BytesIO()
        with zipfile.ZipFile(memory,'w',compression=zipfile.ZIP_DEFLATED) as archive:archive.writestr('fixture/data.txt',b'x'*4096)
        self.data=memory.getvalue();self.row=dict(url='https://dl.google.com/android/repository/fixture.zip',size=len(self.data),sha1=hashlib.sha1(self.data).hexdigest())

    def tearDown(self):
        if self.root.is_symlink() or self.root.resolve()!=self.owner.resolve()/'files':raise RuntimeError('Changed Android fixture output preserved')
        shutil.rmtree(self.root)

    def acquire(self,data=None,length=None,limit=8192):
        return android_tooling.archive(self.root,self.row,limit,lambda *a,**k:Response(self.data if data is None else data,length))

    def test_verified_publication_and_cache_reuse(self):
        target,receipt=self.acquire(length=len(self.data))
        self.assertEqual(target.read_bytes(),self.data)
        self.assertEqual(receipt['archive_sha256'],hashlib.sha256(self.data).hexdigest())
        self.assertEqual(receipt['expanded_bytes'],4096)
        self.assertFalse(target.with_suffix('.partial').exists())
        same,_=android_tooling.archive(self.root,self.row,8192,lambda *a,**k:self.fail('Cached archive downloaded again'))
        self.assertEqual(same,target)

    def test_short_oversized_and_wrong_digest_are_not_published(self):
        for data in (self.data[:-1],self.data+b'x',b'0'*len(self.data)):
            with self.assertRaises(RuntimeError):self.acquire(data=data)
            self.assertFalse((self.root/'downloads/fixture.zip').exists())
            self.assertFalse((self.root/'downloads/fixture.partial').exists())
        with self.assertRaises(RuntimeError):self.acquire(length=len(self.data)+1)

    def test_unknown_cache_and_partial_are_preserved(self):
        cache=self.root/'downloads';cache.mkdir();target=cache/'fixture.zip';target.write_bytes(b'foreign')
        with self.assertRaises(RuntimeError):self.acquire()
        self.assertEqual(target.read_bytes(),b'foreign');target.unlink()
        pending=cache/'fixture.partial';pending.write_bytes(b'foreign-partial')
        with self.assertRaises(RuntimeError):self.acquire()
        self.assertEqual(pending.read_bytes(),b'foreign-partial')

    def test_extraction_budget_is_checked_before_install(self):
        with self.assertRaisesRegex(RuntimeError,'extraction budget'):self.acquire(limit=4095)
        self.assertEqual((self.root/'downloads/fixture.zip').read_bytes(),self.data)

    def test_foreign_replacement_during_download_is_preserved(self):
        fixture=self
        class Replacing(Response):
            first=True;rename_refused=False
            def read(self,n=-1):
                if self.first:
                    self.first=False;pending=fixture.root/'downloads/fixture.partial'
                    try:pending.rename(pending.with_name('original.partial'))
                    except PermissionError:self.rename_refused=True
                    else:pending.write_bytes(b'foreign-replacement')
                return super().read(n)
        response=Replacing(self.data)
        try:target,_=android_tooling.archive(self.root,self.row,8192,lambda *a,**k:response)
        except RuntimeError as failure:
            self.assertIn('identity changed',str(failure));self.assertFalse(response.rename_refused)
            self.assertEqual((self.root/'downloads/fixture.partial').read_bytes(),b'foreign-replacement')
            self.assertFalse((self.root/'downloads/fixture.zip').exists())
        else:
            self.assertTrue(response.rename_refused);self.assertEqual(target.read_bytes(),self.data)

    def test_nonofficial_package_url_is_rejected_without_network(self):
        for url in ('http://dl.google.com/fixture.zip','https://example.com/fixture.zip','https://name:secret@dl.google.com/fixture.zip'):
            row={**self.row,'url':url}
            with self.assertRaises(RuntimeError):android_tooling.archive(self.root,row,8192,lambda *a,**k:self.fail('Unsafe package URL fetched'))

    def test_disk_accounting_skips_regular_and_directory_reparse_points(self):
        entries=[]
        def entry(path,mode,size,attributes=0):
            result=Mock(path=path,stat=Mock(return_value=SimpleNamespace(st_mode=mode,st_size=size,st_file_attributes=attributes)))
            entries.append(result);return result
        top=[entry('file',stat.S_IFREG,7),entry('nested',stat.S_IFDIR,0),
             entry('wsl-link',stat.S_IFREG,1000,0x400),entry('junction',stat.S_IFDIR,0,0x400),
             entry('symlink',stat.S_IFLNK,1000)]
        nested=[entry('nested/file',stat.S_IFREG,11)]
        def scan(path):
            self.assertIn(path,('root','nested'));return nullcontext(iter(top if path=='root' else nested))
        with patch.object(android_tooling.os,'scandir',side_effect=scan) as scans:
            self.assertEqual(android_tooling.regular_bytes('root'),18)
        self.assertEqual(scans.call_count,2)
        for value in entries:value.stat.assert_called_once_with(follow_symlinks=False)

    def test_disk_accounting_counts_actual_regular_files(self):
        (self.root/'nested').mkdir();(self.root/'first').write_bytes(b'123')
        (self.root/'nested/second').write_bytes(b'4567')
        self.assertEqual(android_tooling.regular_bytes(self.root),7)


if __name__=='__main__':unittest.main()
