"""Real managed SDK approvals: select before four-row budget; no user grants."""
import hashlib
import json
from pathlib import Path
import sys
import time
import unittest
import uuid

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class ApprovalScopeQueueTests(unittest.TestCase):
    def read(self,query=''):
        status,_,body=dev.request('GET','/api/v1/approvals'+query)
        self.assertEqual(status,200)
        return json.loads(body)['data']

    def test_foreign_first_four_do_not_hide_the_selected_sdk_approval(self):
        self.assertEqual(self.read()['total'],0,'Preserve any foreign pending approvals')
        jobs=[]
        try:
            for index in range(6):
                session=hashlib.sha256(f'mdo-browser-use/approval-scope-qa/{index}'.encode()).hexdigest()[:32]
                scope={'extension':'browser-use-managed-qa','project':'default','session':session}
                dev.call('session.create',{'body':{'project_id':'default','client_session_id':session,
                    'title':f'Browser Use approval scope QA {index}','permission_profile':'balanced',
                    'workspace_root':str(dev.ROOT)}},scope)
                identity='sdk-'+uuid.uuid4().hex
                dev.call('operation.start',{'id':identity,'method':'tool.call','params':{
                    'project':'default','session':session,'tool':'browser_open','arguments':{
                        'action':'create','client':'scope-qa-no-ui','url':'http://127.0.0.1/'}}},scope)
                jobs.append((identity,scope))
                deadline=time.monotonic()+8
                while time.monotonic()<deadline and self.read()['total']<index+1:time.sleep(.03)
                self.assertEqual(self.read()['total'],index+1)
            global_rows=self.read()
            self.assertEqual(global_rows['limit'],4)
            self.assertTrue(global_rows['truncated'])
            self.assertEqual(len(global_rows['items']),4)
            identity,scope=jobs[-1]
            self.assertNotIn(identity,[r['tool_call_id'] for r in global_rows['items']])
            selected=self.read('?project_id=default&session_id='+scope['session'])
            self.assertEqual(selected['total'],1)
            self.assertEqual(selected['pending_total'],6)
            self.assertFalse(selected['truncated'])
            row=selected['items'][0]
            self.assertEqual((row['project_id'],row['session_id'],row['run_id'],row['tool_call_id']),
                             ('default',scope['session'],0,identity))
            self.assertEqual(self.read('?project_id=foreign&session_id='+scope['session'])['items'],[])
            for query in ['?project_id=default','?session_id='+scope['session'],
                          '?project_id=default&session_id=x&project_id=other',
                          '?project_id=default&session_id=x&','?project_id=..&session_id=x',
                          '?project_id=default&session_id=x&unknown=y',
                          '?project_id=default&session_id='+('x'*33)]:
                status,_,_=dev.request('GET','/api/v1/approvals'+query)
                self.assertEqual(status,400,query)
        finally:
            for identity,scope in jobs:
                if not dev.call('operation.get',{'id':identity},scope).get('terminal'):
                    dev.call('operation.cancel',{'id':identity},scope)
            for identity,scope in jobs:
                deadline=time.monotonic()+8
                while time.monotonic()<deadline and not dev.call('operation.get',{'id':identity},scope).get('terminal'):time.sleep(.03)
                current=dev.call('operation.get',{'id':identity},scope)
                self.assertTrue(current.get('terminal'))
                self.assertEqual(current['state'],'cancelled')
                self.assertFalse(current['error']['may_have_completed'])
                dev.call('operation.release',{'id':identity},scope)
            self.assertEqual(self.read()['total'],0)


if __name__=='__main__':unittest.main()
