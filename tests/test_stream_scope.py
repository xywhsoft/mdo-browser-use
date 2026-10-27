"""Actual SDK streams must preserve the original task and bounded byte flow."""
import base64
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev

class StreamScopeTests(unittest.TestCase):
    def setUp(self):
        self.owner={'extension':'browser-use-stream-scope-qa','project':'default','session':'original-stream-task'}
        self.handle=dev.call('stream.open',{'capacity':64},self.owner)['handle']
        self.payload=b'original task bytes\x00\xff'
        dev.call('stream.write',{'handle':self.handle,'base64':base64.b64encode(self.payload).decode()},self.owner)

    def tearDown(self):
        dev.call('stream.release',{'handle':self.handle},self.owner)

    def test_foreign_task_cannot_read_write_close_inspect_or_release_a_live_stream(self):
        for foreign in (dict(self.owner,extension='another-extension'),dict(self.owner,session='another-task')):
            for method,params in (('read',{'limit':4}),('write',{'text':'foreign'}),('close',{}),('stat',{}),('release',{})):
                with self.subTest(method=method,scope=foreign):
                    status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'stream.'+method,'params':{'handle':self.handle,**params},'context':foreign})
                    self.assertEqual(status,409,json.loads(raw));self.assertEqual(json.loads(raw)['error']['code'],'context_mismatch')
            self.assertEqual(dev.call('stream.stat',{'handle':self.handle},self.owner)['buffered'],len(self.payload))
        read=dev.call('stream.read',{'handle':self.handle,'limit':64},self.owner)
        self.assertEqual(base64.b64decode(read['base64']),self.payload)
        self.assertFalse(read['closed'])

    def test_normalized_default_project_and_new_invocations_share_the_owned_stream(self):
        inherited=dict(self.owner,project='',run='another-run-in-the-same-task')
        dev.call('stream.close',{'handle':self.handle},inherited)
        current=dev.call('stream.read',{'handle':self.handle,'limit':64},inherited)
        self.assertTrue(current['eof']);self.assertEqual(base64.b64decode(current['base64']),self.payload)

    def test_namespace_and_member_cannot_be_lost_in_a_ui_continuation(self):
        owner=dict(self.owner,namespace='private-subtask',member='worker-1')
        handle=dev.call('stream.open',{},owner)['handle']
        try:
            for foreign in (self.owner,dict(owner,namespace='other'),dict(owner,member='worker-2')):
                status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'stream.stat','params':{'handle':handle},'context':foreign})
                self.assertEqual(status,409,json.loads(raw));self.assertEqual(json.loads(raw)['error']['code'],'context_mismatch')
            self.assertEqual(dev.call('stream.stat',{'handle':handle},owner)['buffered'],0)
        finally:dev.call('stream.release',{'handle':handle},owner)

    def test_backpressure_and_explicit_eof_do_not_change_under_scoped_ownership(self):
        before=dev.call('stream.stat',{'handle':self.handle},self.owner)
        status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'stream.write','params':{'handle':self.handle,'text':'x'*64},'context':self.owner})
        self.assertEqual(status,409,json.loads(raw));self.assertEqual(json.loads(raw)['error']['code'],'sdk_stream_would_block')
        self.assertEqual(dev.call('stream.stat',{'handle':self.handle},self.owner),before)
        dev.call('stream.close',{'handle':self.handle},self.owner)
        dev.call('stream.read',{'handle':self.handle,'limit':64},self.owner)
        eof=dev.call('stream.read',{'handle':self.handle,'limit':64},self.owner)
        self.assertTrue(eof['eof']);self.assertEqual(eof['bytes'],0)

    def test_wrapped_ring_and_acknowledgements_match_exact_committed_bytes(self):
        dev.call('stream.read',{'handle':self.handle,'limit':64},self.owner)
        first=bytes(range(64));second=bytes(range(255,239,-1))
        written=dev.call('stream.write',{'handle':self.handle,'base64':base64.b64encode(first).decode()},self.owner)
        self.assertEqual(written['written'],64);self.assertEqual(written['buffered'],64)
        read=dev.call('stream.read',{'handle':self.handle,'limit':16},self.owner)
        self.assertEqual(base64.b64decode(read['base64']),first[:16]);self.assertEqual(read['buffered'],48)
        dev.call('stream.write',{'handle':self.handle,'base64':base64.b64encode(second).decode()},self.owner)
        closed=dev.call('stream.close',{'handle':self.handle},self.owner)
        self.assertTrue(closed['closed']);self.assertFalse(closed['eof'])
        drained=dev.call('stream.read',{'handle':self.handle,'limit':64},self.owner)
        self.assertEqual(base64.b64decode(drained['base64']),first[16:]+second)
        self.assertEqual(drained['bytes'],64);self.assertEqual(drained['buffered'],0);self.assertTrue(drained['eof'])
        empty=dev.call('stream.read',{'handle':self.handle,'limit':64},self.owner)
        self.assertEqual(empty['base64'],'');self.assertEqual(empty['bytes'],0);self.assertTrue(empty['eof'])

if __name__=='__main__':unittest.main()
