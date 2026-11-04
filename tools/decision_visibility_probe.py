"""Verify native workbench visibility around one unapproved local QA operation.

Uses the existing balanced QA conversation/Home. Never grants a permission,
clicks the native fixture button, calls a model or substitutes for human QA.
"""
import hashlib
import json
import time
import uuid
from pathlib import Path

import acceptance_probe
import dev

SESSION=hashlib.sha256(b'mdo-browser-use/ask-permission-qa/v1').hexdigest()[:32]
SCOPE={'extension':'browser-use-managed-qa','project':'default','session':SESSION}
OWNER={'extension':'plugin.mdo.browser-use','project':'default','session':SESSION}
RESULT=dev.ENV/'decision-visibility-result.json'


def require(condition,message):
    if not condition:raise RuntimeError(message)


def wait(predicate,message,seconds=12):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        value=predicate()
        if value:return value
        time.sleep(.05)
    raise RuntimeError(message)


def approvals():
    status,_,body=dev.request('GET','/api/v1/approvals')
    require(status==200,'Approval snapshot unavailable')
    return json.loads(body)['data']['items']


def invoke(method,params,result='browseruse.result'):
    pending=dev.call(method,params,OWNER)
    try:
        row=wait(lambda: terminal(result,pending['id']),'Original UI operation did not terminate')
        require(row['state']=='succeeded','Original UI operation failed: '+str(row.get('error'))[:300])
        return row['result']
    finally:
        row=dev.call(result,{'id':pending['id']},OWNER)
        if row['state'] not in ('succeeded','failed','cancelled'):
            if result=='browser.result':dev.call('ui.cancel',{'id':pending['id']},OWNER)
            else:dev.call(result,{'id':pending['id'],'cancel':True},OWNER)
            wait(lambda:terminal(result,pending['id']),'UI cancellation did not drain')
        dev.call('browser.release' if result=='browser.result' else result,{'id':pending['id'],**({} if result=='browser.result' else {'release':True})},OWNER)


def terminal(method,identity):
    row=dev.call(method,{'id':identity},OWNER)
    return row if row['state'] in ('succeeded','failed','cancelled') else None


def main():
    before=acceptance_probe.runtime_identity()
    require(not dev.call('operation.list') and not approvals(),'Preserve existing operations/approvals')
    require(not dev.call('browser.list') and not dev.call('panel.list'),'Finish existing owned browser/panel checks')
    status,_,body=dev.request('GET','/api/v1/projects/default/sessions/'+SESSION)
    require(status==200,'Existing balanced QA session is required')
    data=json.loads(body)['data'];session=data.get('session',data)
    require(session['title']=='Browser Use ask-permission QA' and session['permission_profile']=='balanced','QA profile changed; refusing to alter permissions')
    require((dev.SOURCE/'app/web/__browser_use_ask__.html').read_bytes()==(dev.ROOT/'tests/fixtures/ask-permission.html').read_bytes(),'Owned loopback fixture changed')
    report={'passed':False,'scope':'actual-main-native-decision-visibility','model_calls':0,'permission_grants':0,
            'human_permission_qualified':False,'desktop_pixels_qualified':False,'cases':[]}
    browser=None;operation=None;took=False
    def save():RESULT.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    def case(name):report['cases'].append(name);save();print(name,flush=True)
    def operation_terminal():
        row=dev.call('operation.get',{'id':operation},SCOPE)
        return row if row.get('terminal') else None
    try:
        took=True;dev.stop();dev.start(window=True,page='/#/projects/default/sessions/'+SESSION)
        clients=wait(lambda:dev.call('ui.clients'),'Main display client unavailable')
        require(len(clients)==1,'Expected exactly one actual display')
        browser=dev.call('browseruse.tabs',{'action':'create','client':clients[0]['id'],'key':'decision-visibility-fixture',
                    'url':f"http://127.0.0.1:{dev.state()['port']}/__browser_use_ask__.html"},OWNER)['id']
        page=invoke('browseruse.observe',{'browser':browser})
        require(page['ok'] and 'Applied 0 trusted:none' in page['text'],'Fixture already has an input effect')
        panel=dev.call('panel.open',{'type':'mdo.browser','client':clients[0]['id'],'key':browser,'resource':{'id':browser},'reveal':'activate'},OWNER)
        wait(lambda:dev.call('panel.get',{'id':panel['id']},OWNER).get('runtime',{}).get('visible'),'Original panel never became visible')
        def native_state():return invoke('browser.command',{'id':browser,'command':'state'},'browser.result')
        initial=native_state();bounds=initial.get('controller_bounds',{})
        require(initial['visible'] is True,'Original native browser is not visible')
        require(bounds.get('width',0)>1 and bounds.get('height',0)>1,'Original viewport is not qualified')
        report['native_before_bounds']=bounds;case('original_native_browser_visible')
        status,_,body=dev.request('GET',f'/api/v1/projects/default/sessions/{SESSION}/events?after=0&limit=1')
        require(status==200,'Original event cursor unavailable');cursor=json.loads(body)['data']['latest_event_id']
        operation='sdk-'+uuid.uuid4().hex
        args={'browser':browser,'document':page['document'],'op':'click','target':{'role':'button','name':'Apply fixture action'}}
        dev.call('operation.start',{'id':operation,'method':'tool.call','params':{'project':'default','session':SESSION,'tool':'browser_act','arguments':args}},SCOPE)
        owned=wait(lambda:next((row for row in approvals() if row['tool_call_id']==operation),None),'Original scoped approval missing')
        require(owned['tool']=='browser_act' and json.loads(owned['arguments_json'])==args,'Approval lost original arguments')
        require(owned['session_id']==SESSION and owned['project_id']=='default','Approval lost selected conversation')
        wait(lambda:dev.call('panel.get',{'id':panel['id']},OWNER).get('runtime',{}).get('visible') is False,'Covered workbench did not yield for the decision')
        hidden=native_state();report['native_pending_bounds']=hidden.get('controller_bounds',{});save()
        require(hidden['visible'] is False,'Original native child still covers the decision')
        require(all(report['native_pending_bounds'].get(key)==bounds[key] for key in ('width','height')),
                'Hiding the decision viewport resized the original native page')
        report['native_viewport_preserved']=True
        require(any(row['id']==panel['id'] for row in dev.call('panel.list',{},OWNER)),'Decision discarded the panel')
        require(any(row['id']==browser for row in dev.call('browser.list',{},OWNER)),'Decision discarded the browser')
        report['pending_native_visible']=False;case('pending_decision_hides_native_child_preserves_original_resources')
        dev.call('operation.cancel',{'id':operation},SCOPE);ended=wait(operation_terminal,'Original cancellation did not drain')
        require(ended['state']=='cancelled','The regression request was unexpectedly decided')
        require(not ended.get('error',{}).get('may_have_completed'),'Unapproved native input was dispatched')
        require(not any(row['tool_call_id']==operation for row in approvals()),'Cancelled approval retained')
        status,_,body=dev.request('GET',f'/api/v1/projects/default/sessions/{SESSION}/events?after={cursor}&limit=32')
        require(status==200,'Original terminal history missing')
        events=[row for row in json.loads(body)['data']['items'] if row['tool_call_id']==operation]
        require([row['kind'] for row in events]==['tool_start','tool_done'] and not events[-1]['success'],'Original tool terminal pair missing')
        dev.call('operation.release',{'id':operation},SCOPE);operation=None
        wait(lambda:dev.call('panel.get',{'id':panel['id']},OWNER).get('runtime',{}).get('visible'),'Original panel was not restored')
        require(native_state()['visible'] is True,'Original native child was not restored')
        after=invoke('browseruse.observe',{'browser':browser})
        require(after['ok'] and 'Applied 0 trusted:none' in after['text'],'Unapproved action changed the fixture')
        require(after['document']==page['document'],'Presentation replayed the original navigation')
        case('cancel_restores_same_native_document_without_input_or_navigation_replay')
        report['passed']=True
    finally:
        if operation:
            row=dev.call('operation.get',{'id':operation},SCOPE)
            if not row.get('terminal'):dev.call('operation.cancel',{'id':operation},SCOPE)
            wait(operation_terminal,'Original operation cleanup failed');dev.call('operation.release',{'id':operation},SCOPE)
        if browser:dev.call('browser.close',{'id':browser},OWNER)
        if took:
            require(not dev.call('operation.list') and not approvals(),'Active work remains; preserve the process')
            dev.stop();dev.start();restored=acceptance_probe.runtime_identity()
            for key in ('port','executable','executable_sha256','config','config_sha256','home'):
                require(restored[key]==before[key],'Original runtime changed: '+key)
            report['headless_restored']=True
        save()
    print(json.dumps(report,indent=2))


if __name__=='__main__':main()
