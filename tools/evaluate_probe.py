"""Actual managed C tool -> main UI -> exact native page/iframe JavaScript."""
import json
import time
import traceback
import dev
from frame_fixture import FrameFixture
from managed_probe import tool,FULL,READ_ONLY,SCOPE


def main():
    output=dev.ENV/'evaluate-result.json';output.write_text('{"passed":false,"state":"running"}\n')
    fixture=FrameFixture().start();browsers=[];checks=[]
    def invoke(name,params,success=True,session=FULL):
        result=tool(name,params,session)
        if result.get('success') is not success:
            raise RuntimeError(name+' unexpected result: '+str(result.get('text',''))[:1500])
        text=result.get('text') or '{}'
        value=json.loads(text[text.index('{'):]) if '{' in text else {'ok':False,'error':{'message':text}}
        return value
    def evaluate(browser,observation,script,**fields):
        return invoke('browser_evaluate',dict(browser=browser,document=observation['document'],script=script,**fields))
    def owner(session=FULL):return{'extension':'plugin.mdo.browser-use','project':'default','session':session}
    def sdk_result(params):
        receipt=dev.call('browseruse.evaluate',params,owner());identity=receipt['id'];end=time.monotonic()+20
        while time.monotonic()<end:
            result=dev.call('browseruse.result',{'id':identity},owner())
            if result['state']!='pending':
                dev.call('browseruse.result',{'id':identity,'release':True},owner());return result
            time.sleep(.05)
        raise RuntimeError('Original SDK evaluation did not settle; it was not replayed')
    try:
        for retained in dev.call('browser.list',{},owner()):
            if retained.get('key')=='evaluate-native-qa':
                dev.call('browser.close',{'id':retained['id']},owner())
        for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
            session=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,'title':'Browser Use managed QA','permission_profile':permission,'workspace_root':str(dev.ROOT)}},SCOPE)
            assert session['data']['permission_profile']==permission
        dev.stop();dev.start(window=True)
        end=time.monotonic()+45
        while True:
            status,_,raw=dev.request('GET','/api/v1/models');catalog=json.loads(raw).get('data',{})
            if status==200 and catalog.get('login_state')=='ready' and catalog.get('models'):break
            if time.monotonic()>end:raise RuntimeError('Original model catalog not ready; no inference or effectful retry attempted')
            time.sleep(.2)
        end=time.monotonic()+30
        while True:
            clients=dev.call('ui.clients')
            if clients:break
            if time.monotonic()>end:raise RuntimeError('Original main window did not register')
            time.sleep(.2)
        client=clients[0]['id']
        browser=invoke('browser_open',{'action':'create','client':client,'url':fixture.origin+'/root','key':'evaluate-native-qa'})['id'];browsers.append((browser,FULL))
        observed=invoke('browser_observe',{'browser':browser})
        page=evaluate(browser,observed,'globalThis.__mdoScriptFixture=17; return {heading:document.querySelector("h1").textContent,number:globalThis.__mdoScriptFixture};')
        assert page['value']=={'heading':'Root site A','number':17};checks.append('page_world_globals_and_json')
        isolated=evaluate(browser,observed,'return {page:typeof globalThis.__mdoScriptFixture,runtime:typeof globalThis.__mdo_browser_use_local_frame_v1};',world='isolated')
        assert isolated['value']=={'page':'undefined','runtime':'undefined'};checks.append('separate_script_world_and_trusted_runtime')
        async_result=evaluate(browser,observed,'await new Promise(resolve=>setTimeout(resolve,25));return 23;')
        assert async_result['value']==23;checks.append('async_promise_result')
        frames=[row for row in observed['frames'] if row['id']!='f0' and row.get('dom')]
        assert len(frames)>=3
        for row in frames:
            result=evaluate(browser,observed,'return {heading:document.querySelector("h1").textContent,origin:location.origin};',frame=row['id'],frame_document=row['document'])
            assert result['frame']==row['id'] and result['frame_document']==row['document']
            assert result['value']['heading'] in ('Same site child','Cross site B','Nested site A')
        cross=next(row for row in frames if row.get('name')=='cross');assert cross['separate_process'] is True
        checks.extend(['same_origin_iframe','cross_site_oopif','nested_cross_site_iframe'])
        result=evaluate(browser,observed,'return typeof globalThis.__mdo_browser_use_local_frame_v1;',frame=cross['id'],frame_document=cross['document'],world='isolated')
        assert result['value']=='undefined';checks.append('isolated_cross_site_script_world')
        for script in ['throw new Error("controlled script failure")','return 1n;','return "汉".repeat(200);']:
            rejected=invoke('browser_evaluate',{'browser':browser,'document':observed['document'],'script':script,'max_bytes':256},False)
            assert rejected['ok'] is False
        assert evaluate(browser,observed,'return globalThis.__mdoScriptFixture;')['value']==17
        checks.append('exceptions_json_limits_and_recovery')
        bad=invoke('browser_evaluate',{'browser':browser,'document':'d-00000000-0000-4000-8000-000000000000','script':'globalThis.__mdoScriptFixture=99;'},False)
        assert 'stale_document' in json.dumps(bad), json.dumps(bad)
        assert evaluate(browser,observed,'return globalThis.__mdoScriptFixture;')['value']==17;checks.append('stale_root_cannot_execute')
        evaluate(browser,observed,'document.getElementById("cross").src='+json.dumps(fixture.other+'/cross-next')+';return true;')
        invoke('browser_wait',{'browser':browser,'until':'text','text':'Cross site next document'})
        stale=invoke('browser_evaluate',{'browser':browser,'document':observed['document'],'frame':cross['id'],'frame_document':cross['document'],'script':'document.body.dataset.staleExecution="bad";'},False)
        assert any(code in json.dumps(stale) for code in ('stale_frame','frame_not_ready')), json.dumps(stale)
        checks.append('replaced_iframe_cannot_execute')
        observed=invoke('browser_observe',{'browser':browser})
        timed=sdk_result({'browser':browser,'document':observed['document'],'script':'globalThis.__mdoScriptTimeout=(globalThis.__mdoScriptTimeout||0)+1;await new Promise(()=>{});','timeout_ms':2000})
        assert timed['state']=='failed' and timed['error']['effect_may_have_occurred'] is True,json.dumps(timed)
        assert evaluate(browser,observed,'return globalThis.__mdoScriptTimeout;')['value']==1;checks.append('timeout_retains_uncertainty_without_replay')
        readonly=dev.call('browser.create',{'client':client,'url':fixture.origin+'/same'},owner(READ_ONLY))['id'];browsers.append((readonly,READ_ONLY))
        readonly_observation=invoke('browser_observe',{'browser':readonly},session=READ_ONLY)
        denied=invoke('browser_evaluate',{'browser':readonly,'document':readonly_observation['document'],'script':'return 1;'},False,READ_ONLY)
        assert denied.get('error') or denied.get('ok') is False;checks.append('managed_readonly_denial')
        report={'passed':True,'checks':checks,'backend':'actual-mainline-managed-webview2','tools':17,'services':14,'real_model_calls':0,'same_home':True}
        output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
    except Exception:
        output.write_text(json.dumps({'passed':False,'checks':checks,'error':traceback.format_exc()},indent=2)+'\n');raise
    finally:
        for browser,session in reversed(browsers):
            try:dev.call('browser.close',{'id':browser},owner(session))
            except (OSError,RuntimeError):pass
        dev.stop();dev.start();fixture.close()

if __name__=='__main__':main()
