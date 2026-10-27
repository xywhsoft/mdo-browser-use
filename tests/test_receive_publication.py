"""Actual journaled publication and read-only reconciliation in the one Home."""
import json
from pathlib import Path
import sys
import unittest
import uuid
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev
import test_file_receive as receive_helpers

class ReceivePublicationTests(unittest.TestCase):
    def setUp(self):
        self.f=receive_helpers.FileReceiveTests(methodName='runTest');self.f.setUp()
        self.f.scope['session']='publication-'+self._testMethodName
        self.data=b'completed original response\x00\xff'
        self.target=self.f.root/(self._testMethodName+'.bin');self.assertFalse(self.target.exists());self.f.extra.append(self.target)

    def tearDown(self):self.f.tearDown()

    def ready(self,**overrides):
        identity,handle=self.f.start(self.data,destination=self.target.name,**overrides)
        self.f.feed(handle,self.data);self.assertEqual(self.f.wait(identity)['state'],'succeeded')
        return identity,dev.call('files.receive.get',{'id':identity},self.f.scope)

    def job(self,method,params,scope=None):
        identity='sdk-'+uuid.uuid4().hex
        request={'id':identity,'method':method,'params':params}
        dev.call('operation.start',request,scope or self.f.scope)
        info=dev.call('operation.wait',{'id':identity,'timeout_ms':5000},scope or self.f.scope);self.assertTrue(info['terminal'],info)
        if scope:dev.call('operation.release',{'id':identity},scope)
        else:self.f.jobs.append(identity)
        return identity,info,request

    def test_ready_destination_and_confirmed_publication_survive_in_the_receiver(self):
        receiver,row=self.ready();self.assertEqual(row['destination'],self.target.name)
        publication,info,_=self.job('files.receive.publish',{'id':receiver,'path':self.target.name})
        self.assertEqual(info['state'],'succeeded',info)
        result=dev.call('operation.result',{'id':publication},self.f.scope)
        retained=dev.call('files.receive.get',{'id':receiver},self.f.scope)
        self.assertEqual(retained['state'],'published');self.assertEqual(retained['publication'],publication)
        self.assertEqual(retained['publication_file']['sha256'],row['sha256']);self.assertTrue(result['published'])
        self.assertEqual(self.target.read_bytes(),self.data)
        dev.call('files.receive.release',{'id':receiver},self.f.scope)
        self.assertEqual(self.target.read_bytes(),self.data)

    def test_conflict_keeps_exact_staging_and_inspection_does_not_replace_the_target(self):
        receiver,row=self.ready();self.target.write_bytes(b'keep another existing entry')
        _,info,_=self.job('files.receive.publish',{'id':receiver,'path':self.target.name})
        self.assertEqual(info['error']['code'],'sdk_file_exists');self.assertFalse(info['error']['may_have_completed'])
        _,review,_=self.job('files.receive.reconcile',{'id':receiver});self.assertEqual(review['state'],'succeeded',review)
        record=dev.call('files.receive.get',{'id':receiver},self.f.scope)
        self.assertEqual(record['state'],'ready');self.assertEqual(record['publication_verdict'],'different_file')
        self.assertEqual((self.f.root/row['source']).read_bytes(),self.data);self.assertEqual(self.target.read_bytes(),b'keep another existing entry')
        self.assertNotIn('publication_file',record)

    def test_missing_destination_is_only_inspected_never_created_or_replayed(self):
        receiver,row=self.ready();before=(self.f.root/row['source']).stat()
        _,info,_=self.job('files.receive.reconcile',{'id':receiver});self.assertEqual(info['state'],'succeeded',info)
        record=dev.call('files.receive.get',{'id':receiver},self.f.scope)
        self.assertEqual(record['state'],'ready');self.assertEqual(record['publication_verdict'],'missing')
        self.assertFalse(self.target.exists());self.assertEqual((self.f.root/row['source']).stat().st_ino,before.st_ino)

    def test_confirmed_target_is_reconciled_by_exact_identity_hash_without_link_creation(self):
        receiver,_=self.ready();publication,info,request=self.job('files.receive.publish',{'id':receiver,'path':self.target.name})
        self.assertEqual(info['state'],'succeeded');before=self.target.stat()
        _,checked,_=self.job('files.receive.reconcile',{'id':receiver});self.assertEqual(checked['state'],'succeeded',checked)
        row=dev.call('files.receive.get',{'id':receiver},self.f.scope)
        self.assertEqual(row['state'],'published');self.assertEqual(row['publication_verdict'],'verified');self.assertEqual(row['publication'],publication)
        self.assertEqual(self.target.stat().st_ino,before.st_ino);self.assertEqual(self.target.read_bytes(),self.data)
        again=dev.call('operation.start',request,self.f.scope);self.assertEqual(again['state'],'succeeded')
        self.assertEqual(self.target.stat().st_ino,before.st_ino)
        _,blocked,_=self.job('files.receive.publish',{'id':receiver,'path':self.target.name+'.other'})
        self.assertEqual(blocked['error']['code'],'sdk_file_receive_review');self.assertFalse((self.f.root/(self.target.name+'.other')).exists())

    def test_changed_published_bytes_are_preserved_without_a_new_verified_claim(self):
        receiver,row=self.ready();_,info,_=self.job('files.receive.publish',{'id':receiver,'path':self.target.name});self.assertEqual(info['state'],'succeeded')
        self.target.write_bytes(b'edited original saved entry')
        _,review,_=self.job('files.receive.reconcile',{'id':receiver})
        self.assertEqual(review['state'],'failed');self.assertIn(review['error']['code'],('sdk_file_receive_conflict','sdk_file_receive_digest'))
        self.assertEqual(self.target.read_bytes(),b'edited original saved entry');self.assertEqual((self.f.root/row['source']).read_bytes(),b'edited original saved entry')

    def test_private_task_cannot_publish_or_reconcile_another_receiver(self):
        receiver,row=self.ready();foreign=dict(self.f.scope,namespace='another-private-task')
        for method,params in (('files.receive.publish',{'id':receiver,'path':self.target.name}),('files.receive.reconcile',{'id':receiver})):
            _,info,_=self.job(method,params,foreign);self.assertEqual(info['error']['code'],'context_mismatch')
            self.assertFalse(self.target.exists());self.assertEqual((self.f.root/row['source']).read_bytes(),self.data)

if __name__=='__main__':unittest.main()
