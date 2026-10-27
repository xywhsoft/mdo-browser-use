"""Actual XRT root-relative staging publication, with no application copies."""
import hashlib
import json
import os
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class FilePublishTests(unittest.TestCase):
    def setUp(self):
        self.root=dev.ENV/'qa'/'publish';self.root.mkdir(parents=True,exist_ok=True)
        self.scope={'extension':'browser-publish-qa','session':'file-publish-task',
                    'workspace':str(self.root),'namespace':'download-task','member':'original-member'}
        self.prefix=self._testMethodName
        self.source=self.root/(self.prefix+'-暂存.bin')
        self.target=self.root/(self.prefix+'-下载.bin')
        self.paths=[self.target,self.source]
        self.data=bytes(range(256))*521+b'complete\x00\xff'
        self.root_snapshot=dev.call('files.stat',{'path':'.'},self.scope)['revision']
        self.source.write_bytes(self.data)

    def tearDown(self):
        for path in self.paths:path.unlink(missing_ok=True)

    def params(self,**override):
        snapshot=dev.call('files.stat',{'path':self.source.name},self.scope)
        return {'source':self.source.name,'path':self.target.name,
                'source_revision':snapshot['revision'],'root_snapshot':self.root_snapshot,
                'size':len(self.data),'sha256':hashlib.sha256(self.data).hexdigest(),**override}

    def reject(self,params,status,code=None,scope=None):
        actual,_,raw=dev.request('POST','/api/v1/application/call',{
            'method':'files.publish','params':params,'context':scope or self.scope})
        result=json.loads(raw);self.assertEqual(actual,status,result)
        if code:self.assertEqual(result['error']['code'],code)
        self.assertFalse(result['error']['details']['may_have_completed'],result)
        self.assertEqual(self.source.read_bytes(),self.data)
        return result

    def test_binary_empty_and_unicode_names_publish_verified_bytes_and_retain_source(self):
        for data in (self.data,b''):
            with self.subTest(size=len(data)):
                self.data=data;self.source.write_bytes(data)
                result=dev.call('files.publish',self.params(),self.scope)
                self.assertTrue(result['published']);self.assertTrue(result['source_retained'])
                self.assertEqual(result['size'],len(data))
                self.assertEqual(result['sha256'],hashlib.sha256(data).hexdigest())
                self.assertEqual(result['revision'],dev.call('files.stat',{'path':self.target.name},self.scope)['revision'])
                self.assertEqual(self.source.read_bytes(),data);self.assertEqual(self.target.read_bytes(),data)
                self.assertTrue(os.path.samefile(self.source,self.target))
                self.assertNotIn('base64',result);self.assertNotIn('text',result)
                self.target.unlink()
        description=dev.call('sdk.describe',{'id':'files.publish'},self.scope)
        self.assertEqual(description['input_schema']['properties']['size']['maximum'],67108864)
        self.assertIn('root_snapshot',description['input_schema']['required'])

    def test_complete_hash_size_and_source_revision_are_preconditions(self):
        params=self.params()
        for change,code in (({'sha256':'0'*64},'sdk_file_digest_conflict'),
                            ({'size':len(self.data)-1},'sdk_file_conflict'),
                            ({'source_revision':'f-1-2-3-4'},'sdk_file_conflict')):
            with self.subTest(change=change):
                self.reject({**params,**change},412,code);self.assertFalse(self.target.exists())

    def test_existing_target_and_source_alias_are_never_overwritten_or_removed(self):
        self.target.write_bytes(b'keep existing destination')
        self.reject(self.params(),409,'sdk_file_exists')
        self.assertEqual(self.target.read_bytes(),b'keep existing destination')
        self.target.unlink();os.link(self.source,self.target)
        self.reject(self.params(),409,'sdk_file_exists')
        self.assertEqual(self.target.read_bytes(),self.data)
        if os.name=='nt':
            self.reject(self.params(path=self.source.name.upper()),409,'sdk_file_exists')
            self.assertTrue(self.source.exists())

    def test_physical_root_identity_is_required_but_directory_time_changes_are_allowed(self):
        other=dev.call('files.stat',{'path':str(self.root.parent)},self.scope)['revision']
        self.reject(self.params(root_snapshot=other),412,'sdk_workspace_conflict')
        self.assertFalse(self.target.exists())
        # The root snapshot was captured before the staging entry was created.
        self.assertTrue(dev.call('files.publish',self.params(),self.scope)['published'])

    def test_replaced_root_cannot_satisfy_a_previous_task_snapshot(self):
        slot=self.root/'root-slot';old=self.root/'root-slot-old'
        slot.mkdir();context=dict(self.scope,workspace=str(slot))
        try:
            root_snapshot=dev.call('files.stat',{'path':'.'},context)['revision']
            slot.rename(old);slot.mkdir();replacement=slot/'source.bin';replacement.write_bytes(self.data)
            snapshot=dev.call('files.stat',{'path':'source.bin'},context)['revision']
            params=self.params(source='source.bin',path='target.bin',source_revision=snapshot,root_snapshot=root_snapshot)
            self.reject(params,412,'sdk_workspace_conflict',context)
            self.assertFalse((slot/'target.bin').exists());self.assertEqual(replacement.read_bytes(),self.data)
        finally:
            (slot/'source.bin').unlink(missing_ok=True)
            slot.rmdir();old.rmdir()

    def test_invalid_paths_types_and_hashes_do_not_create_entries(self):
        params=self.params()
        for key in ('source','path'):
            for value in ('../escape.bin','/absolute.bin','C:/absolute.bin','folder\\name','file/','file\x00suffix','file\nname','a//b','./file'):
                with self.subTest(key=key,value=value):self.reject({**params,key:value},400 if '\x00' in value else 422,'sdk_arguments_invalid')
        for key,value in (('sha256','A'*64),('sha256','f'*63),('source_revision',None),('root_snapshot',None)):
            self.reject({**params,key:value},400 if value is None else 422,'sdk_arguments_invalid')
        for value in (-1,67108865,True,'12'):
            self.reject({**params,'size':value},400,'sdk_arguments_invalid')
        for value in ('f-18446744073709551616-1-0-0','f--1-1-0-0','f-01-1-0-0','f-1-1-0-0junk','f-1-1-0-9223372036854775808'):
            self.reject({**params,'root_snapshot':value},412,'sdk_workspace_conflict')
        self.assertFalse(self.target.exists())

    def test_symlink_source_is_rejected_without_following_it(self):
        link=self.root/(self.prefix+'-link.bin');self.paths.insert(0,link)
        try:link.symlink_to(self.source)
        except OSError as error:self.skipTest('Symlink unavailable: '+str(getattr(error,'winerror',error.errno)))
        self.reject(self.params(source=link.name),422,'sdk_file_type')
        self.assertFalse(self.target.exists())

    @unittest.skipUnless(os.name=='nt','Windows file sharing gate')
    def test_existing_writer_blocks_verification_before_publication(self):
        params=self.params()
        with self.source.open('r+b'):
            actual,_,raw=dev.request('POST','/api/v1/application/call',{
                'method':'files.publish','params':params,'context':self.scope})
            result=json.loads(raw);self.assertGreaterEqual(actual,400,result)
            self.assertEqual(result['error']['code'],'sdk_native_failed')
            self.assertFalse(result['error']['details']['may_have_completed'],result)
        self.assertFalse(self.target.exists());self.assertEqual(self.source.read_bytes(),self.data)


if __name__=='__main__':unittest.main()
