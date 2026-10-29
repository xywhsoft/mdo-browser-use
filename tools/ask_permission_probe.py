"""Actual managed permission callbacks + decision API + native fixture effects.

Uses only the fixed owned QA session, one existing Home and loopback content.
Decisions are synthetic test-user API actions, not a claim of visual prompt QA.
Never approves a request without its exact operation/tool/arguments/workspace.
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import time
import uuid

import dev
from managed_probe import read_operation_result,tool,value

SESSION=hashlib.sha256(b'mdo-browser-use/ask-permission-qa/v1').hexdigest()[:32]
SCOPE={'extension':'browser-use-managed-qa','project':'default','session':SESSION}
OWNER={'extension':'plugin.mdo.browser-use','project':'default','session':SESSION}


class Probe:
    def __init__(self):
        self.jobs={};self.browser=None;self.cases=[];self.stage='startup';self.checks=0;self.took_deployment=False
        self.output=dev.ENV/'ask-permission-result.json'
        self.report={'passed':False,'state':'running','scope':'managed-executor-decision-api-native-effects',
                     'visual_prompt_verified':False,'model_calls':0,'cases':self.cases}
        self.save()

    def save(self):
        self.report.update(stage=self.stage,checks=self.checks)
        self.output.write_text(json.dumps(self.report,indent=2)+'\n',encoding='utf-8')

    def check(self,condition,reason):
        self.checks+=1
        if not condition:raise RuntimeError(reason)

    def approvals(self):
        status,_,body=dev.request('GET','/api/v1/approvals')
        self.check(status==200,'Approval snapshot unavailable')
        result=json.loads(body)['data']
        self.check(not result['truncated'],'Foreign or excessive approval queue preserved')
        return result['items']

    def start(self,name,args):
        identity='sdk-'+uuid.uuid4().hex
        request={'id':identity,'method':'tool.call','params':{'project':'default','session':SESSION,'tool':name,'arguments':args}}
        job=dev.call('operation.start',request,SCOPE)
        self.check(job['id']==identity,'Managed operation changed its identity')
        self.jobs[identity]=request
        return identity

    def pending(self,identity,timeout=5):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            rows=[row for row in self.approvals() if row.get('tool_call_id')==identity]
            self.check(len(rows)<=1,'One operation exposed duplicate approval requests')
            if rows:
                row=rows[0];request=self.jobs[identity]['params']
                self.check(row['tool']==request['tool'] and json.loads(row['arguments_json'])==request['arguments'],
                           'Approval does not describe the original tool and arguments')
                self.check(Path(row['workspace_root']).resolve()==dev.ROOT.resolve(),'Approval workspace changed')
                self.check(row['expires_in_ms']>0,'Approval is already expired')
                self.check(any(item['kind']=='external_service' and item['resource']=='browser-use' and item['access']==['use']
                               for item in row['resources']),'Browser permission resource was not requested')
                return row
            current=dev.call('operation.get',{'id':identity},SCOPE)
            if current.get('terminal'):raise RuntimeError('Protected tool terminated without asking: '+str(read_operation_result(identity,SCOPE))[:240])
            time.sleep(.05)
        raise RuntimeError('Original managed approval was not observed')

    def decide(self,row,decision,expected=200):
        status,_,body=dev.request('PUT',f"/api/v1/approvals/{row['id']}",{'decision':decision})
        self.check(status==expected,f'Unexpected {decision} decision status: {status}')
        return json.loads(body)

    def finish(self,identity,expected='succeeded'):
        end=time.monotonic()+25
        while time.monotonic()<end:
            current=dev.call('operation.get',{'id':identity},SCOPE)
            if current.get('terminal'):
                self.check(current['state']==expected,'Managed operation ended in an unexpected state: '+current['state'])
                return read_operation_result(identity,SCOPE) if expected=='succeeded' else current
            time.sleep(.05)
        raise RuntimeError('Managed operation did not terminate')

    def release(self,identity):
        dev.call('operation.release',{'id':identity},SCOPE);self.jobs.pop(identity)

    def service(self,name,params):
        queued=dev.call('browseruse.'+name,params,OWNER)
        terminal=False
        try:
            end=time.monotonic()+30
            while time.monotonic()<end:
                receipt=dev.call('browseruse.result',{'id':queued['id']},OWNER)
                terminal=receipt['state'] in ('succeeded','failed','cancelled')
                if terminal:
                    if receipt['state']!='succeeded':raise RuntimeError('Native fixture operation failed: '+str(receipt.get('error'))[:240])
                    return receipt['result']
                time.sleep(.05)
            raise RuntimeError('Native fixture operation did not terminate')
        finally:
            try:
                if not terminal:
                    dev.call('browseruse.result',{'id':queued['id'],'cancel':True},OWNER)
                    end=time.monotonic()+5
                    while time.monotonic()<end:
                        receipt=dev.call('browseruse.result',{'id':queued['id']},OWNER)
                        terminal=receipt['state'] in ('succeeded','failed','cancelled')
                        if terminal:break
                        time.sleep(.05)
                if terminal:dev.call('browseruse.result',{'id':queued['id'],'release':True},OWNER)
                else:self.report['operation_cleanup_unconfirmed']=True
            except (RuntimeError,OSError):self.report['operation_cleanup_unconfirmed']=True

    def observe(self):
        return self.service('observe',{'browser':self.browser})

    def effects(self,expected):
        page=self.observe();match=re.search(r'Applied (\d+) trusted:(none|true|false)',page['text'])
        self.check(match is not None and int(match[1])==expected,'Native fixture effect count changed unexpectedly')
        if expected:self.check(match[2]=='true','Fixture effect was not from trusted native input')
        return page

    def action(self,page):
        return {'browser':self.browser,'document':page['document'],'op':'click',
                'target':{'role':'button','name':'Apply fixture action'}}

    def case(self,name):
        self.cases.append(name);self.save();print(name,flush=True)

    def run(self):
        self.check(not self.approvals(),'Existing pending approvals are preserved; finish them before this QA gate')
        created=dev.call('session.create',{'body':{'project_id':'default','client_session_id':SESSION,
                         'title':'Browser Use ask-permission QA','permission_profile':'balanced','workspace_root':str(dev.ROOT)}},SCOPE)
        self.check(created['data']['permission_profile']=='balanced','Persistent QA permission changed; refusing to overwrite it')
        (dev.SOURCE/'app/web/__browser_use_ask__.html').write_bytes((dev.ROOT/'tests/fixtures/ask-permission.html').read_bytes())
        self.took_deployment=True;dev.stop();dev.start(window=True)
        end=time.monotonic()+30;clients=[]
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        self.check(len(clients)==1,'Expected one actual workbench display client')
        args={'action':'create','client':clients[0]['id'],'key':'ask-permission-fixture',
              'url':f"http://127.0.0.1:{dev.state()['port']}/__browser_use_ask__.html"}
        initial=dev.call('browseruse.tabs',{'action':'list'},OWNER)['tabs']
        self.stage='deny-create';identity=self.start('browser_open',args);row=self.pending(identity)
        self.check(dev.call('browseruse.tabs',{'action':'list'},OWNER)['tabs']==initial,'Browser created before approval')
        self.decide(row,'deny');result=self.finish(identity)
        self.check(result['success'] is False and result['effect_applied'] is False,'Denied creation reported effects')
        self.check(dev.call('browseruse.tabs',{'action':'list'},OWNER)['tabs']==initial,'Denied tool created a browser')
        self.release(identity);self.case('creation_denied_before_browser_admission')

        self.stage='allow-create';identity=self.start('browser_open',args);row=self.pending(identity)
        self.check(dev.call('browseruse.tabs',{'action':'list'},OWNER)['tabs']==initial,'Creation admission bypassed its prompt')
        self.decide(row,'allow');result=self.finish(identity);self.check(result['success'],'Approved creation failed')
        self.browser=value(result)['id'];self.release(identity);page=self.effects(0)
        self.case('approved_creation_reaches_actual_native_view')

        self.stage='deny-input';identity=self.start('browser_act',self.action(page));row=self.pending(identity);self.effects(0)
        self.decide(row,'deny');result=self.finish(identity);self.check(result['success'] is False,'Denied input succeeded')
        self.decide(row,'deny');self.decide(row,'allow',409);self.effects(0);self.release(identity)
        self.case('denial_and_decision_replay_have_no_native_effect')

        self.stage='allow-input';page=self.effects(0);identity=self.start('browser_act',self.action(page));row=self.pending(identity)
        self.effects(0);self.decide(row,'allow');result=self.finish(identity);self.check(result['success'],'Approved native click failed')
        self.effects(1);self.decide(row,'allow');self.effects(1)
        repeated=dev.call('operation.start',self.jobs[identity],SCOPE)
        self.check(repeated.get('terminal') and repeated['state']=='succeeded','Original operation identity was executed again')
        self.effects(1);self.check(not self.approvals(),'Idempotent receipt replay requested another approval')
        self.release(identity);self.case('one_approved_trusted_click_no_decision_or_operation_replay')

        self.stage='cancel-approval';page=self.effects(1);identity=self.start('browser_act',self.action(page));row=self.pending(identity)
        self.effects(1);dev.call('operation.cancel',{'id':identity},SCOPE);self.finish(identity,'cancelled')
        end=time.monotonic()+5
        while time.monotonic()<end and any(item['tool_call_id']==identity for item in self.approvals()):time.sleep(.05)
        self.check(not any(item['tool_call_id']==identity for item in self.approvals()),'Cancelled approval was retained')
        self.decide(row,'allow',404);self.effects(1);self.release(identity)
        self.check(tool('browser_observe',{'browser':self.browser},SESSION)['success'],'Approval cancellation stopped following managed tools')
        self.case('cancelled_prompt_cannot_accept_late_grant_and_next_tool_works')

        self.stage='cancel-after-dispatch'
        identity=self.start('browser_wait',{'browser':self.browser,'until':'visible','target':{'css':'#never-created-qa'},'timeout_ms':10000})
        end=time.monotonic()+5;pending=None
        while time.monotonic()<end:
            rows=dev.call('client.poll',{'client':clients[0]['id']},OWNER)
            pending=next((item for item in rows if item.get('action')=='workbench.browser' and
                         item.get('params',{}).get('id')==self.browser and item['params'].get('command')=='wait_for'),None)
            if pending:break
            self.check(not dev.call('operation.get',{'id':identity},SCOPE).get('terminal'),'Native wait terminated before cancellation checkpoint')
            time.sleep(.05)
        self.check(pending is not None,'Managed tool did not dispatch its owned UI wait')
        self.check(not self.approvals(),'Read-only native wait unexpectedly requested approval')
        dev.call('operation.cancel',{'id':identity},SCOPE);result=self.finish(identity,'cancelled')
        self.check(result['error']['code']=='sdk_cancelled' and result['error']['may_have_completed'],
                   'Post-admission cancellation discarded dispatch uncertainty')
        end=time.monotonic()+5
        while time.monotonic()<end:
            rows=dev.call('client.poll',{'client':clients[0]['id']},OWNER)
            if not any(item['id']==pending['id'] for item in rows):break
            time.sleep(.05)
        self.check(not any(item['id']==pending['id'] for item in rows),'Cancelling the parent retained its UI wait')
        self.effects(1);self.release(identity)
        self.check(tool('browser_observe',{'browser':self.browser},SESSION)['success'],'Dispatched cancellation stopped following managed tools')
        self.case('dispatched_cancel_retains_uncertainty_drains_ui_and_next_tool_works')

        self.stage='run-grant';page=self.effects(1);identity=self.start('browser_act',self.action(page));row=self.pending(identity)
        self.decide(row,'allow_run');result=self.finish(identity);self.check(result['success'],'Run-scoped grant failed')
        self.effects(2);self.release(identity)
        identity=self.start('browser_act',self.action(self.effects(2)));row=self.pending(identity)
        self.effects(2);self.decide(row,'deny');self.check(self.finish(identity)['success'] is False,'Run grant leaked into another invocation')
        self.release(identity);self.case('run_scoped_grant_does_not_leak_to_next_native_drive')

        self.stage='stale-document';identity=self.start('browser_act',self.action(self.effects(2)));row=self.pending(identity)
        self.service('navigate',{'browser':self.browser,'action':'goto','url':args['url']+'?new-document=1'})
        self.effects(0);self.decide(row,'allow');result=self.finish(identity)
        self.check(result['success'] is False and 'stale_document' in result['text'],'Approval retargeted an action into a newer document')
        self.effects(0);self.release(identity)
        self.check(tool('browser_observe',{'browser':self.browser},SESSION)['success'],'Stale approved input stopped the next managed tool')
        self.case('approval_preserves_original_document_and_failure_is_recoverable')

        self.stage='recovery';identity=self.start('browser_act',self.action(self.effects(0)));row=self.pending(identity)
        self.decide(row,'allow');self.check(self.finish(identity)['success'],'New explicitly approved recovery action failed')
        self.effects(1);self.release(identity);self.case('fresh_approval_recovers_without_replaying_stale_action')
        self.check(not self.approvals(),'Owned approval queue did not drain')
        self.report.update(passed=True,state='checks_passed')

    def cleanup(self):
        for identity in list(self.jobs):
            try:
                current=dev.call('operation.get',{'id':identity},SCOPE)
                if not current.get('terminal'):dev.call('operation.cancel',{'id':identity},SCOPE)
                end=time.monotonic()+5
                while time.monotonic()<end and not dev.call('operation.get',{'id':identity},SCOPE).get('terminal'):time.sleep(.05)
                if dev.call('operation.get',{'id':identity},SCOPE).get('terminal'):self.release(identity)
            except (RuntimeError,OSError):self.report['operation_cleanup_unconfirmed']=True
        if self.browser:
            try:dev.call('browser.close',{'id':self.browser},OWNER)
            except (RuntimeError,OSError):self.report['browser_cleanup_unconfirmed']=True
        if self.took_deployment:
            dev.stop();dev.start();self.report['headless_restored']=True
        if self.jobs or any(self.report.get(key) for key in ('operation_cleanup_unconfirmed','browser_cleanup_unconfirmed')):
            self.report.update(passed=False,state='failed',error='Owned operation/browser cleanup was not confirmed')
        self.save()


def main():
    probe=Probe()
    try:probe.run()
    except (RuntimeError,OSError,ValueError,AssertionError) as error:
        probe.report.update(passed=False,state='failed',error=str(error)[:512]);raise
    finally:probe.cleanup()
    if not probe.report['passed']:raise RuntimeError(probe.report.get('error','Permission gate failed'))
    probe.report['state']='passed';probe.save();print(json.dumps(probe.report,indent=2))


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError) as error:print(error,file=sys.stderr);raise SystemExit(1)
