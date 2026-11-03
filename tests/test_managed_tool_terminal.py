"""Actual SDK cancellation must close its exact durable UI tool record."""
import hashlib
import json
from pathlib import Path
import sys
import time
import unittest
import uuid

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev

SESSION=hashlib.sha256(b'mdo-browser-use/approval-scope-qa/5').hexdigest()[:32]
SCOPE={'extension':'browser-use-managed-qa','project':'default','session':SESSION}


class ManagedToolTerminalTests(unittest.TestCase):
    def events(self,after=0):
        status,_,body=dev.request('GET',f'/api/v1/projects/default/sessions/{SESSION}/events?after={after}&limit=32')
        envelope=json.loads(body)
        if after==0 and status==404 and envelope.get('error',{}).get('code')=='session_events_unavailable':
            return {'latest_event_id':0,'items':[]}
        self.assertEqual(status,200)
        return envelope['data']

    def test_cancelled_approval_has_one_original_terminal_tool_event(self):
        dev.call('session.create',{'body':{'project_id':'default','client_session_id':SESSION,
            'title':'Browser Use approval scope QA 5','permission_profile':'balanced',
            'workspace_root':str(dev.ROOT)}},SCOPE)
        after=self.events()['latest_event_id'];identity='sdk-'+uuid.uuid4().hex;created=False
        try:
            dev.call('operation.start',{'id':identity,'method':'tool.call','params':{'project':'default',
                'session':SESSION,'tool':'browser_open','arguments':{'action':'create',
                    'client':'terminal-qa-no-ui','url':'http://127.0.0.1/'}}},SCOPE);created=True
            deadline=time.monotonic()+5;approval=None
            while time.monotonic()<deadline:
                status,_,body=dev.request('GET',f'/api/v1/approvals?project_id=default&session_id={SESSION}')
                self.assertEqual(status,200)
                approval=next((r for r in json.loads(body)['data']['items'] if r['tool_call_id']==identity),None)
                if approval:break
                time.sleep(.03)
            self.assertIsNotNone(approval)
            before_cancel=[r for r in self.events(after)['items'] if r['tool_call_id']==identity]
            self.assertEqual([r['kind'] for r in before_cancel],['tool_start'])
            dev.call('operation.cancel',{'id':identity},SCOPE)
            deadline=time.monotonic()+5;current={}
            while time.monotonic()<deadline:
                current=dev.call('operation.get',{'id':identity},SCOPE)
                if current.get('terminal'):break
                time.sleep(.03)
            self.assertTrue(current.get('terminal'))
            self.assertEqual(current['state'],'cancelled')
            self.assertFalse(current['error']['may_have_completed'])
            rows=[r for r in self.events(after)['items'] if r['tool_call_id']==identity]
            evidence={'operation':identity,'session':SESSION,'state':current['state'],
                'may_have_completed':current['error']['may_have_completed'],'events':rows,'model_calls':0}
            (dev.ENV/'managed-tool-terminal-result.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
            self.assertEqual([r['kind'] for r in rows],['tool_start','tool_done'])
            start,end=rows
            self.assertEqual((start['agent_id'],start['run_id'],start['tool_name']),
                             (end['agent_id'],end['run_id'],end['tool_name']))
            self.assertFalse(end['success'])
            self.assertFalse(end['effect_applied'])
            self.assertIn('cancel',end['text'].lower())
            self.assertGreaterEqual(end['time'],start['time'])
        finally:
            if created:
                current=dev.call('operation.get',{'id':identity},SCOPE)
                if not current.get('terminal'):dev.call('operation.cancel',{'id':identity},SCOPE)
                deadline=time.monotonic()+5
                while time.monotonic()<deadline and not dev.call('operation.get',{'id':identity},SCOPE).get('terminal'):time.sleep(.03)
                self.assertTrue(dev.call('operation.get',{'id':identity},SCOPE).get('terminal'))
                dev.call('operation.release',{'id':identity},SCOPE)


if __name__=='__main__':unittest.main()
