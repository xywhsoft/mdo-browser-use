"""One original managed SDK call across a forced same-Home development restart."""
import hashlib
import json
from pathlib import Path
import time
import uuid
import dev
import acceptance_probe

def main():
    report={'passed':False,'state':'running','model_calls':0,'native_effects':0,'checks':0}
    output=dev.ENV/'sdk-tool-restart-result.json'
    def save():output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    def check(value,message):
        report['checks']+=1
        if not value:raise RuntimeError(message)
    target=hashlib.sha256(b'mdo-browser-use/approval-scope-qa/5').hexdigest()[:32]
    caller=hashlib.sha256(b'mdo-browser-use/approval-scope-qa/4').hexdigest()[:32]
    scope={'extension':'browser-use-managed-qa','project':'default','session':caller,'workspace':str(dev.ROOT)}
    owner=dict(scope,extension='plugin.mdo.browser-use',session=target)
    def events(after):
        status,_,raw=dev.request('GET',f'/api/v1/projects/default/sessions/{target}/events?after={after}&limit=32')
        check(status==200,'Original event stream unavailable')
        return json.loads(raw)['data']
    identity='sdk-'+uuid.uuid4().hex
    request={'id':identity,'method':'tool.call','params':{'project':'default','session':target,'tool':'browser_open',
        'arguments':{'action':'create','client':'restart-tool-qa-no-ui','url':'http://127.0.0.1/'}}}
    receipt=dev.HOME/'data/application/operations'/f'{identity}.json'
    original=None;started=False;restarted=False;save()
    try:
        report['runtime_before']=acceptance_probe.runtime_identity()
        for index,session in ((4,caller),(5,target)):
            dev.call('session.create',{'body':{'project_id':'default','client_session_id':session,
                'title':f'Browser Use approval scope QA {index}','permission_profile':'balanced',
                'workspace_root':str(dev.ROOT)}},dict(scope,session=session))
        check(not dev.call('operation.list',{},scope),'Preserve foreign operations before restarting')
        tabs=dev.call('browseruse.tabs',{'action':'list'},owner)
        after=events(0)['latest_event_id']
        dev.call('operation.start',request,scope);started=True
        end=time.monotonic()+8;approval=None
        while time.monotonic()<end:
            status,_,raw=dev.request('GET',f'/api/v1/approvals?project_id=default&session_id={target}')
            check(status==200,'Target approval unavailable')
            approval=next((r for r in json.loads(raw)['data']['items'] if r['tool_call_id']==identity),None)
            if approval:break
            time.sleep(.04)
        check(approval is not None,'Original request did not reach the permission gate')
        raw_path=dev.HOME/'sessions/default'/target/'ui-events.jsonl'
        raw_hash=hashlib.sha256(raw_path.read_bytes()).hexdigest()
        pending=[r for r in events(after)['items'] if r['tool_call_id']==identity]
        check([r['kind'] for r in pending]==['tool_start'] and 'tool_outcome' not in pending[0],
              'A live physical owner was incorrectly marked interrupted')
        report.update(operation=identity,caller_session=caller,target_session=target);save()
        check(acceptance_probe.runtime_identity()['pid']==report['runtime_before']['pid'],'Runtime owner changed before restart')
        dev.stop();dev.start();restarted=True
        recovered=dev.call('operation.get',{'id':identity},scope)
        check(recovered['state']=='interrupted' and recovered['terminal'],'Old operation was resumed')
        check(recovered.get('outcome')=='uncertain' and recovered.get('error',{}).get('may_have_completed') is True,
              'Interrupted operation lost effect uncertainty')
        check(recovered['error']['code']=='sdk_operation_interrupted' and recovered['error']['retryable'] is False,
              'Interrupted operation lacks an explicit non-retryable outcome')
        target_info=recovered.get('tool_target')
        check(target_info=={'version':1,'project':'default','session':target,'tool':'browser_open'},
              'Actual target identity was replaced by caller scope or included arguments')
        original=receipt.read_bytes();check(not receipt.is_symlink(),'QA receipt is a link')
        check(receipt.resolve().is_relative_to(dev.HOME.resolve()),'QA receipt left original Home')
        rows=[r for r in events(after)['items'] if r['tool_call_id']==identity]
        check(len(rows)==1 and rows[0]['kind']=='tool_start','Recovery fabricated a durable tool end')
        check(rows[0].get('tool_outcome',{}).get('may_have_completed') is True,'UI did not expose interrupted status')
        report['events']=rows
        status,_,raw=dev.request('GET',f'/api/v1/projects/default/sessions/{target}/conversation?limit=1')
        check(status==200,'Conversation projection unavailable')
        projected=json.loads(json.loads(raw)['data']['items_json'])
        check(any(r['tool_call_id']==identity and r.get('tool_outcome',{}).get('state')=='interrupted' for r in projected),
              'Conversation snapshot omitted interrupted status')
        replay=dev.call('operation.start',request,scope)
        check(replay['state']=='interrupted' and replay['error']['may_have_completed'] is True,'Repeated identity lost its original uncertainty')
        status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'operation.result','params':{'id':identity},'context':scope})
        err=json.loads(raw).get('error',{})
        check(status==409 and err.get('code')=='sdk_operation_interrupted' and err.get('details',{}).get('may_have_completed') is True,
              'Result read concealed the original interrupted outcome')
        status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'operation.get','params':{'id':identity},'context':dict(scope,session=target)})
        check(status==409 and json.loads(raw).get('error',{}).get('code')=='context_mismatch','Target viewing scope stole caller operation ownership')
        # Only this test's exact non-live receipt is temporarily varied. Retain
        # original bytes in memory, restore after every assertion, never touch a
        # user's operation or restart during these deliberately invalid variants.
        def replace(data):
            temporary=receipt.with_suffix('.restart-qa.tmp')
            check(not temporary.exists(),'Preserve an unrelated QA temporary file')
            temporary.write_bytes(data);temporary.replace(receipt)
        for key,value in (('project','foreign'),('session',caller),('tool','different'),('version',2)):
            check(receipt.read_bytes()==original,'QA receipt changed externally')
            altered=json.loads(original);altered['tool_target'][key]=value
            try:
                replace((json.dumps(altered)+'\n').encode())
                wrong=[r for r in events(after)['items'] if r['tool_call_id']==identity]
                check(len(wrong)==1 and 'tool_outcome' not in wrong[0],'Foreign or invalid target metadata leaked into UI')
            finally:replace(original)
        check(receipt.read_bytes()==original,'Recovery read changed the admission receipt')
        check(hashlib.sha256(raw_path.read_bytes()).hexdigest()==raw_hash,'Recovery read rewrote original history')
        check(dev.call('browseruse.tabs',{'action':'list'},owner)==tabs,'Restart replayed browser creation')
        status,_,raw=dev.request('GET',f'/api/v1/approvals?project_id=default&session_id={target}')
        check(status==200 and not json.loads(raw)['data']['items'],'Restart recreated the approval')
        report.update(passed=True,state='passed',same_home=True,original_history_unchanged=True,
            receipt_unchanged=True,no_replay=True,actual_target_preserved=True,foreign_metadata_rejected=True,
            runtime_after=acceptance_probe.runtime_identity())
    except Exception as error:
        report.update(state='failed',error=str(error)[:512]);raise
    finally:
        if original is not None and receipt.read_bytes()!=original:
            temporary=receipt.with_suffix('.restart-qa.tmp');temporary.write_bytes(original);temporary.replace(receipt)
        if started:
            current=dev.call('operation.get',{'id':identity},scope)
            if not current.get('terminal'):
                dev.call('operation.cancel',{'id':identity},scope)
                end=time.monotonic()+8
                while time.monotonic()<end and not dev.call('operation.get',{'id':identity},scope).get('terminal'):time.sleep(.04)
            if dev.call('operation.get',{'id':identity},scope).get('terminal'):dev.call('operation.release',{'id':identity},scope)
        report['headless_restored']=not dev.state().get('window',True);save()
    print(json.dumps({k:v for k,v in report.items() if k not in ('events','runtime_before','runtime_after')},indent=2))

if __name__=='__main__':main()
