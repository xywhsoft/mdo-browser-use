"""Actual host cancellation while the managed executor awaits permission."""
import hashlib
import json
from pathlib import Path
import sys
import time
import unittest
import uuid

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev
from managed_probe import tool


class ManagedPermissionCancellationTests(unittest.TestCase):
    def test_cancelled_permission_is_not_a_generic_executor_failure(self):
        session=hashlib.sha256(b'mdo-browser-use/ask-permission-qa/v1').hexdigest()[:32]
        scope={'extension':'browser-use-managed-qa','project':'default','session':session}
        owner=dict(scope,extension='plugin.mdo.browser-use')
        row=dev.call('session.create',{'body':{'project_id':'default','client_session_id':session,
            'title':'Browser Use ask-permission QA','permission_profile':'balanced','workspace_root':str(dev.ROOT)}},scope)
        self.assertEqual(row['data']['permission_profile'],'balanced')
        before=dev.call('browseruse.tabs',{'action':'list'},owner)
        identity='sdk-'+uuid.uuid4().hex;job=None
        try:
            job=dev.call('operation.start',{'id':identity,'method':'tool.call','params':{'project':'default','session':session,
                'tool':'browser_open','arguments':{'action':'create','client':'owned-permission-qa-no-ui','url':'http://127.0.0.1/'}}},scope)
            deadline=time.monotonic()+5;approval=None
            while time.monotonic()<deadline:
                status,_,body=dev.request('GET','/api/v1/approvals');self.assertEqual(status,200)
                approval=next((r for r in json.loads(body)['data']['items'] if r.get('tool_call_id')==identity),None)
                if approval:break
                time.sleep(.05)
            self.assertIsNotNone(approval,'Original managed approval was not observed')
            dev.call('operation.cancel',{'id':identity},scope)
            deadline=time.monotonic()+5;current={}
            while time.monotonic()<deadline:
                current=dev.call('operation.get',{'id':identity},scope)
                if current.get('terminal'):break
                time.sleep(.05)
            self.assertTrue(current.get('terminal'))
            self.assertEqual(current['state'],'cancelled')
            self.assertEqual(current['error']['code'],'sdk_cancelled')
            self.assertEqual(current['error']['status'],499)
            self.assertFalse(current['error']['may_have_completed'])
            self.assertEqual(dev.call('browseruse.tabs',{'action':'list'},owner),before)
            status,_,body=dev.request('GET','/api/v1/approvals');self.assertEqual(status,200)
            self.assertFalse(any(r.get('tool_call_id')==identity for r in json.loads(body)['data']['items']))
            self.assertTrue(tool('browser_tabs',{'action':'list'},session)['success'])
        finally:
            if job:
                current=dev.call('operation.get',{'id':identity},scope)
                if not current.get('terminal'):dev.call('operation.cancel',{'id':identity},scope)
                deadline=time.monotonic()+5
                while time.monotonic()<deadline and not dev.call('operation.get',{'id':identity},scope).get('terminal'):time.sleep(.05)
                if dev.call('operation.get',{'id':identity},scope).get('terminal'):dev.call('operation.release',{'id':identity},scope)


if __name__=='__main__':unittest.main()
