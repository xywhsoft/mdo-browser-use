"""Actual Android UI -> account transport -> Windows Browser Use qualification.

Installs one QA-only frontend in the existing Android Home. A physical UI click
starts it; no Node substitute controller, model call or permission setting edit.
The original app/Home/provider and all unrelated plugins/resources are retained.
"""
import base64
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import uuid

import dev
from download_fixture import DownloadFixture, BODY
from physical_auth_probe import Endpoint

ID='mdo.browser-use-physical-qa'
ADB=['D:/GIT/mdo/tools/runtime/windows-x86_64/adb/adb.exe','-P','5038','-s','3dfc5195']
STATE=dev.ENV/'physical-browser-state.json'


def save(value):
    STATE.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')


def call(api,method,params=None,context=None):
    return api.data('POST','/api/v1/application/call',{'method':method,'params':params or {},'context':context or {}})


def adb(*args):
    return subprocess.check_output([*ADB,*args],text=True,timeout=12).strip()


def cleanup_test_session(state):
    """Trash only the unchanged synthetic task; preserve all other sessions."""
    if not state.get('session'):return
    route='/api/v1/projects/default/sessions/'+state['session']
    code,headers,raw=dev.request('GET',route)
    if code==404:
        state['test_session_absent']=True;return
    info=json.loads(raw).get('data',{})
    assert code==200 and info.get('id')==state['session']
    assert info.get('title')=='Browser Use 双设备浏览器验收'
    assert info.get('workspace_root')==str(dev.ROOT) and info.get('permission_profile')=='full-access'
    if info.get('status')=='active':
        assert info.get('revision')==1 and headers.get('etag')
        code,_,raw=dev.request('DELETE',route,headers={'If-Match':headers['etag']})
        info=json.loads(raw).get('data',{})
        assert code==200 and info.get('id')==state['session']
    assert info.get('status')=='trash' and info.get('revision')==2
    state['test_session_trashed']=True


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phone-install',type=Path,default=dev.ENV/'E262-install.json')
    args=parser.parse_args()
    if STATE.exists():raise RuntimeError('Original physical Browser Use state exists; inspect before another run')
    installed=json.loads(args.phone_install.read_text(encoding='utf-8'))['phone']
    assert installed['package']=='org.xleaves.mdo.browseruse.dev'
    assert adb('shell','pidof',installed['package'])==installed['pid']
    port=int(adb('forward','--no-rebind','tcp:0','tcp:'+str(installed['remote_port'])))
    phone=Endpoint(port,installed['remote_port']);desktop=Endpoint(dev.state()['port'],dev.state()['port'])
    state={'phase':'preparing','phone':{**installed,'local_port':port},'real_model_calls':0,'pages':[]}
    save(state);fixture=None;imported=False;scope=None;resource=None
    try:
        account=phone.data('GET','/api/v1/account');other=desktop.data('GET','/api/v1/account')
        assert account['state']==other['state']=='signed_in' and account['profile']['id']==other['profile']['id']
        account=other=None
        remote=desktop.data('GET','/api/v1/remote');own=phone.data('GET','/api/v1/remote')
        assert remote['allow_remote'] and remote['device_id']!=own['device_id']
        state['local_device_id']=remote['device_id'];state['phone_device_id']=own['device_id']
        clients=dev.call('ui.clients');assert len(clients)==1
        assert not dev.call('operation.list') and not dev.call('browser.list')
        plugins=call(phone,'plugin.list')['items'];assert not any(p['id']==ID for p in plugins)
        state['plugins_before']=[{'id':p['id'],'revision':p['revision'],'enabled':p['enabled']} for p in plugins]
        run='physical-'+uuid.uuid4().hex;session=uuid.uuid4().hex
        scope={'extension':'plugin.'+ID,'project':'default','session':session,'workspace':str(dev.ROOT),'namespace':run,'member':'physical-browser-qa'}
        local_scope={'extension':'physical-browser-qa','project':'default','namespace':run,'member':'phone-ui'}
        state.update(id=run,context=scope,local_context=local_scope,session=session,target_client=clients[0]['id'])
        fixture=DownloadFixture().start();upload=dev.ENV/'qa/physical-browser/upload.bin';upload.parent.mkdir(parents=True,exist_ok=True)
        assert not upload.exists();upload.write_bytes(BODY)
        destination=upload.with_name('saved-download.bin');assert not destination.exists()
        state.update(upload={'path':str(upload),'sha256':hashlib.sha256(BODY).hexdigest()},destination=str(destination))
        for name,target in [('page.html','__browser_use_physical__.html'),('upload.html','__browser_use_physical_upload__.html')]:
            path=dev.SOURCE/'app/web'/target;assert not path.exists()
            data=(dev.ROOT/'tests/fixtures'/name).read_bytes();path.write_bytes(data)
            state['pages'].append({'path':str(path),'sha256':hashlib.sha256(data).hexdigest()})
        config={'id':run,'session':session,'device':remote['device_id'],'client':clients[0]['id'],'context':scope,'localContext':local_scope,
                'page':f'http://127.0.0.1:{dev.state()["port"]}/__browser_use_physical__.html',
                'upload':f'http://127.0.0.1:{dev.state()["port"]}/__browser_use_physical_upload__.html','download':fixture.origin+'/page',
                'uploadPath':'.mdo/qa/physical-browser/upload.bin','savePath':'.mdo/qa/physical-browser/saved-download.bin',
                'bytes':len(BODY),'hash':hashlib.sha256(BODY).hexdigest()}
        manifest={'format':'mdo.plugin.v1','id':ID,'name':'Physical Browser Use QA','version':'0.1.0','description':'Temporary physical-controller acceptance',
                  'author':'mdo','license':'MIT','entry':{'frontend':'web/controller.js'}}
        files={'web/controller.js':(dev.ROOT/'tests/fixtures/physical-controller.js').read_bytes(),
               'web/config.js':('export default '+json.dumps(config,ensure_ascii=False)+';\n').encode(),
               'web/sdk/client.mjs':(dev.ROOT/'sdk/client.mjs').read_bytes(),'web/sdk/contracts.mjs':(dev.ROOT/'sdk/contracts.mjs').read_bytes()}
        package={'format':'mdo.extension.v2','manifest':{'slug':ID,'name':manifest['name'],'version':'0.1.0','description':manifest['description'],
            'readme':'QA-only frontend; removed after physical acceptance.','author':'mdo','license':'MIT','platforms':['android-arm64-v8a'],'min_build_id':70000049},
            'resources':[{'kind':'plugins','id':ID,'content':json.dumps(manifest),'files':[{'path':name,'base64':base64.b64encode(data).decode()} for name,data in files.items()]}]}
        plan=phone.data('POST','/api/v1/ecosystem',{'action':'plan_import','single_kind':'plugins','package':package})
        assert plan['can_install']
        phone.data('POST','/api/v1/ecosystem',{'action':'import','single_kind':'plugins','package':package,'trust_code':True,'expected_plan':plan['sha256']});imported=True
        changes=[{'id':ID,'enabled':True}];plan=call(phone,'plugin.plan',{'changes':changes});assert plan['allowed']
        call(phone,'plugin.commit',{'changes':changes,'expected_revision':plan['base_revision'],'expected_plan':plan['plan_revision']})
        state['phase']='waiting-for-physical-controller';save(state);print('QA frontend ready in the original phone app; waiting for physical UI action',flush=True)
        end=time.monotonic()+600;previous=None
        while time.monotonic()<end:
            abort=dev.ENV/'physical-browser-abort.json'
            if abort.exists() and json.loads(abort.read_text()).get('id')==run:
                state['phase']='harness-aborted';save(state);raise RuntimeError('Physical QA cancelled before further actions')
            code,_,envelope=phone.request('POST','/api/v1/application/call',{'method':'resource.get','params':{'id':run},'context':local_scope})
            if code==200 and envelope.get('ok'):
                resource=envelope['data'];report=resource['value'];state['report']=report;state['phase']=report['state'];save(state)
                if report['stage']!=previous:previous=report['stage'];print(previous,flush=True)
                if report['state'] in ('passed','failed'):break
            elif code!=404:raise RuntimeError('Physical controller receipt read failed: HTTP '+str(code))
            time.sleep(.5)
        else:raise RuntimeError('Physical controller action deadline exceeded')
        if report['passed']:
            assert report['controller_engine']=='actual-android-webview' and report['target_client']==clients[0]['id']
            assert fixture.counts.get('/post')==1 and destination.is_file() and destination.read_bytes()==BODY
            report['target_file_hash_verified']=hashlib.sha256(destination.read_bytes()).hexdigest();report['post_requests']=1
            state['report']=report;save(state)
        print('Terminal phone receipt retained. Return phone to local, then request cleanup.',flush=True)
        end=time.monotonic()+180;cleanup=dev.ENV/'physical-browser-cleanup.json'
        while time.monotonic()<end:
            if cleanup.exists() and json.loads(cleanup.read_text()).get('id')==run:break
            time.sleep(.5)
        else:raise RuntimeError('Cleanup acknowledgement not provided; original evidence retained')
    finally:
        if scope and state.get('report',{}).get('browser'):
            for browser in dev.call('browser.list',{},scope):
                if browser.get('key')==state.get('id') and browser.get('context',{}).get('namespace')==scope['namespace']:
                    dev.call('browser.close',{'id':browser['id']},scope)
        cleanup_test_session(state)
        if imported:
            changes=[{'id':ID,'enabled':False}];plan=call(phone,'plugin.plan',{'changes':changes});assert plan['allowed']
            call(phone,'plugin.commit',{'changes':changes,'expected_revision':plan['base_revision'],'expected_plan':plan['plan_revision']})
            end=time.monotonic()+10
            while time.monotonic()<end:
                if not call(phone,'plugin.list').get('pending'):break
                time.sleep(.5)
            phone.data('POST','/api/v1/ecosystem',{'action':'uninstall','entry':{'source':'local','slug':ID}})
            after=call(phone,'plugin.list')['items'];assert not any(p['id']==ID for p in after)
            assert [{'id':p['id'],'revision':p['revision'],'enabled':p['enabled']} for p in after]==state['plugins_before']
            state['qa_plugin_removed']=True
        if resource:call(phone,'resource.remove',{'id':state['id'],'revision':resource['revision']},state['local_context'])
        for row in state['pages']:
            path=Path(row['path']);assert hashlib.sha256(path.read_bytes()).hexdigest()==row['sha256'];path.unlink()
        if state.get('upload'):
            path=Path(state['upload']['path']);assert hashlib.sha256(path.read_bytes()).hexdigest()==state['upload']['sha256'];path.unlink()
            path=Path(state['destination'])
            if path.exists():assert path.read_bytes()==BODY;path.unlink()
        if fixture:state['fixture_post_requests']=fixture.counts.get('/post',0);fixture.close()
        adb('forward','--remove','tcp:'+str(port));state['usb_forward_removed']=True;state['cleaned']=True;save(state)
    if not state.get('report',{}).get('passed'):raise RuntimeError('Actual Android controller workflow failed; inspect sanitized receipt')
    print(json.dumps({'passed':True,'cases':len(state['report']['cases']),'physical_controller':'android','physical_browser':'windows','model_calls':0}),flush=True)


if __name__=='__main__':
    try:main()
    except (AssertionError,RuntimeError,ValueError,OSError,subprocess.SubprocessError) as error:
        print(type(error).__name__+': physical acceptance stopped; inspect the project state receipt',file=sys.stderr);raise SystemExit(1)
