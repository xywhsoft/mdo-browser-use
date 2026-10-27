"""Actual single-deployment SDK stream reception, publication and cleanup."""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import unittest
import uuid

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class FileReceiveTests(unittest.TestCase):
    def setUp(self):
        self.root=dev.ENV/'qa'/'receive';self.root.mkdir(parents=True,exist_ok=True)
        self.scope={'extension':'browser-receive-qa','project':'default','session':self._testMethodName,
                    'workspace':str(self.root),'namespace':'download-subtask','member':'original-worker'}
        self.jobs=[];self.streams=[];self.extra=[];self.requests={}
        self.root_snapshot=dev.call('files.stat',{'path':'.'},self.scope)['revision']

    def tearDown(self):
        for job in self.jobs:
            try:
                info=dev.call('operation.get',{'id':job},self.scope)
                if not info['terminal']:dev.call('operation.cancel',{'id':job},self.scope)
                self.wait(job)
                status,_,_=self.raw('files.receive.get',{'id':job})
                if status==200:dev.call('files.receive.release',{'id':job},self.scope)
                dev.call('operation.release',{'id':job},self.scope)
            except RuntimeError:pass
        for handle in self.streams:
            try:dev.call('stream.release',{'handle':handle},self.scope)
            except RuntimeError:pass
        for path in self.extra:path.unlink(missing_ok=True)

    def raw(self,method,params,scope=None):
        status,headers,body=dev.request('POST','/api/v1/application/call',{
            'method':method,'params':params,'context':scope or self.scope})
        return status,headers,json.loads(body)

    def open(self):
        handle=dev.call('stream.open',{'capacity':65536},self.scope)['handle'];self.streams.append(handle);return handle

    def start(self,data,handle=None,**overrides):
        handle=handle or self.open();identity='sdk-'+uuid.uuid4().hex
        params={'handle':handle,'root_snapshot':self.root_snapshot,'size':len(data),
                'sha256':hashlib.sha256(data).hexdigest(),'timeout_ms':10000,'idle_timeout_ms':3000,**overrides}
        self.requests[identity]={'id':identity,'method':'files.receive.start','params':params}
        receipt=dev.call('operation.start',self.requests[identity],self.scope)
        self.assertEqual(receipt['id'],identity);self.jobs.append(identity)
        return identity,handle

    def feed(self,handle,data,close=True):
        for offset in range(0,len(data),16384):
            chunk=data[offset:offset+16384];end=time.monotonic()+5
            while True:
                status,_,result=self.raw('stream.write',{'handle':handle,'base64':base64.b64encode(chunk).decode()})
                if status==200:
                    self.assertEqual(result['data']['written'],len(chunk));break
                self.assertEqual(result['error']['code'],'sdk_stream_would_block',result)
                if time.monotonic()>end:self.fail('bounded receiver producer did not drain')
                time.sleep(0.01)
        if close:dev.call('stream.close',{'handle':handle},self.scope)

    def wait(self,identity):
        result=dev.call('operation.wait',{'id':identity,'timeout_ms':5000},self.scope)
        self.assertTrue(result['terminal'],result);return result

    def test_binary_text_and_empty_streams_become_verified_staging_with_no_model_bytes(self):
        for data in (bytes(range(256))*1031,b'plain text bytes',b''):
            with self.subTest(size=len(data)):
                identity,handle=self.start(data);self.feed(handle,data)
                self.assertEqual(self.wait(identity)['state'],'succeeded')
                result=dev.call('operation.result',{'id':identity},self.scope)
                self.assertEqual(result['state'],'ready');self.assertEqual(result['size'],len(data))
                self.assertEqual(result['sha256'],hashlib.sha256(data).hexdigest())
                self.assertEqual((self.root/result['source']).read_bytes(),data)
                self.assertNotIn('base64',result);self.assertNotIn('text',result)
                retained=dev.call('files.receive.get',{'id':identity},self.scope)
                self.assertFalse(retained['active']);self.assertEqual(retained['source_revision'],result['source_revision'])
                # The same root snapshot predates staging creation. Publish is
                # the separate explicit workspace-write gate, never URL replay.
                target='received-'+identity+'.bin';self.extra.append(self.root/target)
                published=dev.call('files.publish',{'source':result['source'],'path':target,
                    'source_revision':result['source_revision'],'root_snapshot':self.root_snapshot,
                    'size':len(data),'sha256':result['sha256']},self.scope)
                self.assertTrue(published['published'])
                self.assertTrue(dev.call('files.receive.release',{'id':identity},self.scope)['released'])
                self.assertFalse((self.root/result['source']).exists());self.assertEqual((self.root/target).read_bytes(),data)
                self.assertNotIn(identity,[row['id'] for row in dev.call('files.receive.list',{},self.scope)])

    def test_wrong_hash_short_eof_and_extra_bytes_fail_without_leaking_parts(self):
        data=b'expected exact bytes'
        for supplied,change,code in ((data,{'sha256':'0'*64},'sdk_file_receive_digest'),
                                     (data[:-1],{},'sdk_file_receive_size'),
                                     (data+b'overflow',{},'sdk_file_receive_size')):
            with self.subTest(change=change,supplied=len(supplied)):
                identity,handle=self.start(data,**change);self.feed(handle,supplied)
                final=self.wait(identity);self.assertEqual(final['state'],'failed',final)
                self.assertEqual(final['error']['code'],code);self.assertFalse(final['error']['may_have_completed'])
                self.assertFalse((self.root/('.mdo-receive-'+identity[4:]+'.part')).exists())
                self.assertEqual(self.raw('files.receive.get',{'id':identity})[0],404)

    def test_cancel_and_missing_explicit_eof_clean_partial_data(self):
        for cancel in (True,False):
            with self.subTest(cancel=cancel):
                identity,handle=self.start(b'partial',idle_timeout_ms=150)
                self.feed(handle,b'part',close=False)
                if cancel:dev.call('operation.cancel',{'id':identity},self.scope)
                final=self.wait(identity)
                self.assertEqual(final['error']['code'],'sdk_cancelled' if cancel else 'sdk_file_receive_timeout',final)
                self.assertFalse(final['error']['may_have_completed'])
                self.assertFalse((self.root/('.mdo-receive-'+identity[4:]+'.part')).exists())

    def test_foreign_tasks_and_workspace_changes_cannot_read_or_release_staging(self):
        identity,handle=self.start(b'owned bytes');self.feed(handle,b'owned bytes');self.assertEqual(self.wait(identity)['state'],'succeeded')
        result=dev.call('files.receive.get',{'id':identity},self.scope);path=self.root/result['source']
        for foreign in (dict(self.scope,extension='other-extension'),dict(self.scope,session='other-session'),
                        dict(self.scope,namespace='other-private-task'),dict(self.scope,member='other-worker'),
                        dict(self.scope,workspace=str(self.root.parent))):
            for method in ('get','release'):
                status,_,error=self.raw('files.receive.'+method,{'id':identity},foreign)
                self.assertEqual(status,409,error);self.assertEqual(error['error']['code'],'context_mismatch')
            self.assertNotIn(identity,[row['id'] for row in dev.call('files.receive.list',{},foreign)])
            self.assertEqual(path.read_bytes(),b'owned bytes')

    def test_active_release_is_rejected_before_removing_or_mutating_staging(self):
        identity,handle=self.start(b'still waiting')
        end=time.monotonic()+3
        while time.monotonic()<end:
            status,_,result=self.raw('files.receive.get',{'id':identity})
            if status==200 and result['data']['state']=='receiving':break
            time.sleep(0.01)
        self.assertEqual(status,200,result);self.assertTrue(result['data']['active'])
        status,_,result=self.raw('files.receive.release',{'id':identity})
        self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'sdk_file_receive_busy')
        self.feed(handle,b'still waiting');self.assertEqual(self.wait(identity)['state'],'succeeded')

    def test_direct_start_without_a_durable_operation_is_rejected(self):
        handle=self.open()
        status,_,result=self.raw('files.receive.start',{'handle':handle,'root_snapshot':self.root_snapshot,
            'size':0,'sha256':hashlib.sha256(b'').hexdigest()})
        self.assertEqual(status,422,result);self.assertEqual(result['error']['code'],'sdk_arguments_invalid')

    def test_reservations_bound_total_disk_bytes_before_large_data_is_received(self):
        maximum=64*1024*1024
        for _ in range(2):
            identity,_=self.start(b'',size=maximum,idle_timeout_ms=10000)
            end=time.monotonic()+2
            while time.monotonic()<end:
                status,_,row=self.raw('files.receive.get',{'id':identity})
                if status==200 and row['data']['state']=='receiving':break
                time.sleep(0.01)
            self.assertEqual(status,200,row)
        rejected,_=self.start(b'',size=1)
        final=self.wait(rejected);self.assertEqual(final['error']['code'],'sdk_file_receive_limit',final)
        rows=dev.call('files.receive.list',{},self.scope)
        self.assertEqual(sum(row['size'] for row in rows),128*1024*1024)
        self.assertEqual(sum((self.root/row['source']).stat().st_size for row in rows),0)

    def test_retained_empty_files_also_obey_the_eight_receipt_limit(self):
        for _ in range(8):
            handle=self.open();self.feed(handle,b'')
            identity,_=self.start(b'',handle=handle);self.assertEqual(self.wait(identity)['state'],'succeeded')
            dev.call('operation.release',{'id':identity},self.scope)
        rejected,_=self.start(b'')
        final=self.wait(rejected);self.assertEqual(final['error']['code'],'sdk_file_receive_limit',final)
        self.assertEqual(len(dev.call('files.receive.list',{},self.scope)),8)

    def test_replaced_staging_is_preserved_until_original_identity_is_restored(self):
        identity,handle=self.start(b'owned staging');self.feed(handle,b'owned staging');self.wait(identity)
        row=dev.call('files.receive.get',{'id':identity},self.scope)
        source=self.root/row['source'];owned=self.root/(row['source']+'.owned');self.extra.append(owned)
        source.rename(owned);source.write_bytes(b'unknown replacement')
        try:
            status,_,result=self.raw('files.receive.release',{'id':identity})
            self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'sdk_file_receive_cleanup')
            self.assertEqual(source.read_bytes(),b'unknown replacement');self.assertEqual(owned.read_bytes(),b'owned staging')
        finally:
            source.unlink();owned.rename(source)
        self.assertTrue(dev.call('files.receive.release',{'id':identity},self.scope)['released'])

    def test_unknown_inventory_entries_block_new_bytes_without_deletion(self):
        directory=dev.HOME/'data/application/receivers';directory.mkdir(parents=True,exist_ok=True)
        unknown=directory/'unknown-qa-entry.txt';self.assertFalse(unknown.exists());unknown.write_bytes(b'preserve synthetic unknown entry')
        try:
            identity,_=self.start(b'')
            final=self.wait(identity);self.assertEqual(final['error']['code'],'sdk_file_receive_inventory',final)
            self.assertEqual(unknown.read_bytes(),b'preserve synthetic unknown entry')
            self.assertFalse((self.root/('.mdo-receive-'+identity[4:]+'.part')).exists())
        finally:unknown.unlink()

    def test_replaced_workspace_root_cannot_redirect_staging_cleanup(self):
        slot=self.root/'cleanup-root';owned=self.root/'cleanup-root-owned';slot.mkdir()
        self.scope['workspace']=str(slot)
        self.root_snapshot=dev.call('files.stat',{'path':'.'},self.scope)['revision']
        identity,handle=self.start(b'original root bytes');self.feed(handle,b'original root bytes');self.wait(identity)
        row=dev.call('files.receive.get',{'id':identity},self.scope)
        slot.rename(owned);slot.mkdir();replacement=slot/row['source'];replacement.write_bytes(b'other root file')
        try:
            status,_,result=self.raw('files.receive.release',{'id':identity})
            self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'sdk_file_receive_cleanup')
            self.assertEqual(replacement.read_bytes(),b'other root file')
            self.assertEqual((owned/row['source']).read_bytes(),b'original root bytes')
        finally:
            replacement.unlink();slot.rmdir();owned.rename(slot)
        self.assertTrue(dev.call('files.receive.release',{'id':identity},self.scope)['released']);slot.rmdir()

    def test_restart_retains_ready_files_and_reaps_only_identified_incomplete_parts(self):
        ready,handle=self.start(b'ready survives process exit');self.feed(handle,b'ready survives process exit');self.wait(ready)
        retained=dev.call('files.receive.get',{'id':ready},self.scope);ready_path=self.root/retained['source']
        partial,handle=self.start(b'p'*32768,idle_timeout_ms=10000)
        self.feed(handle,b'p'*16384,close=False)
        part=self.root/('.mdo-receive-'+partial[4:]+'.part');end=time.monotonic()+2
        while time.monotonic()<end:
            if part.exists() and part.stat().st_size==16384:break
            time.sleep(0.01)
        self.assertEqual(part.stat().st_size,16384)
        dev.stop()  # Verified identity; hard stop of this same Home.
        subprocess.run([sys.executable,str(dev.ROOT/'tools/dev.py'),'start'],check=True,capture_output=True)
        interrupted=dev.call('files.receive.get',{'id':partial},self.scope)
        self.assertEqual(interrupted['state'],'interrupted');self.assertFalse(interrupted['active'])
        self.assertEqual(dev.call('operation.get',{'id':partial},self.scope)['state'],'interrupted')
        self.assertEqual(ready_path.read_bytes(),b'ready survives process exit')
        self.assertEqual(dev.call('files.receive.get',{'id':ready},self.scope)['state'],'ready')
        fresh,handle=self.start(b'after restart');self.feed(handle,b'after restart');self.assertEqual(self.wait(fresh)['state'],'succeeded')
        self.assertFalse(part.exists());self.assertEqual(self.raw('files.receive.get',{'id':partial})[0],404)
        self.assertEqual(ready_path.read_bytes(),b'ready survives process exit')
        self.assertEqual(dev.call('operation.get',{'id':partial},self.scope)['state'],'interrupted')
        replay=dev.call('operation.start',self.requests[partial],self.scope)
        self.assertEqual(replay['state'],'interrupted');self.assertFalse(replay['result_available'])
        self.assertFalse(part.exists())


if __name__=='__main__':unittest.main()
