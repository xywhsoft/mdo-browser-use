"""Normal public C/JS and managed visual canvas input in one existing Home."""
import json
import argparse
import subprocess
import sys
import time

import dev
from frame_fixture import FrameFixture
from managed_probe import FULL,READ_ONLY,SCOPE,tool,value


def main():
    parser=argparse.ArgumentParser();group=parser.add_mutually_exclusive_group();group.add_argument('--drag-navigation-only',action='store_true');group.add_argument('--drag-only',action='store_true');group.add_argument('--managed-only',action='store_true');args=parser.parse_args()
    output=dev.ENV/('managed-visual-result.json' if args.managed_only else 'public-visual-result.json');output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    fixture=FrameFixture().start();browser=readonly=None;stage='startup';owner={'extension':'plugin.mdo.browser-use','project':'default','session':FULL};ro_owner=dict(owner,session=READ_ONLY)
    for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
        saved=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,'title':'Browser Use managed QA','permission_profile':permission,'workspace_root':str(dev.ROOT)}},SCOPE)['data']
        if saved['permission_profile']!=permission:raise RuntimeError('Persistent QA permission changed; refusing to overwrite it')
    try:
        dev.stop();dev.start(window=True);clients=[];end=time.monotonic()+30
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The normal workbench did not register')
        stage='public-c-js'
        if not args.managed_only:
            subprocess.run(['node','tools/visual_client_probe.mjs',fixture.origin+'/visual-root',*(['--drag-navigation-only'] if args.drag_navigation_only else ['--drag-only'] if args.drag_only else [])],cwd=dev.ROOT,check=True,capture_output=True,text=True,timeout=240)
            report=json.loads((dev.ENV/'visual-client-result.json').read_text(encoding='utf-8'));assert report['passed']
        else:report={'passed':True,'scope':'managed-visual-permissions-only','model_calls':0}
        if args.drag_navigation_only or args.drag_only:
            output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2));return
        stage='managed-canvas';opened=tool('browser_open',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/visual-root','key':'managed-visual'});assert opened['success'];browser=value(opened)['id']
        page=value(tool('browser_observe',{'browser':browser}));image=tool('browser_screenshot',{'browser':browser,'document':page['document'],'mode':'element','target':{'css':'#root-canvas'},'max_width':200,'max_height':120});assert image['success']
        meta=value(image);target={'image':meta['visual_reference']['id'],'x':30,'y':20}
        acted=tool('browser_act',{'browser':browser,'document':page['document'],'op':'click','target':target,'timeout_ms':15000})
        assert acted['success'] and value(acted)['scene_validated'],acted['text']
        assert tool('browser_wait',{'browser':browser,'until':'text','text':'Root visual clicks 1 trusted'})['success']
        stage='managed-visual-drag';assert tool('browser_navigate',{'browser':browser,'action':'goto','url':fixture.origin+'/visual-drag-root'})['success']
        page=value(tool('browser_observe',{'browser':browser}));image=tool('browser_screenshot',{'browser':browser,'document':page['document'],'max_width':1200,'max_height':800});assert image['success']
        meta=value(image)
        def pixel(x,y):return{'image':meta['visual_reference']['id'],'x':round(x*meta['visual_reference']['width']/page['viewport']['width']),'y':round(y*meta['visual_reference']['height']/page['viewport']['height'])}
        dragged=tool('browser_act',{'browser':browser,'document':page['document'],'op':'drag','target':pixel(70,50),'destination':pixel(70,230),'timeout_ms':15000})
        assert dragged['success'] and value(dragged)['scene_validated'] and value(dragged)['drag_kind']=='pointer',dragged['text']
        assert tool('browser_wait',{'browser':browser,'until':'text','text':'Root pointer releases 1 trusted'})['success']
        dev.call('browser.close',{'id':browser},owner);browser=None
        stage='readonly-owned-canvas';readonly=dev.call('browser.create',{'client':clients[0]['id'],'url':fixture.origin+'/visual-root'},ro_owner)['id']
        page=value(tool('browser_observe',{'browser':readonly},READ_ONLY));image=tool('browser_screenshot',{'browser':readonly,'document':page['document'],'mode':'element','target':{'css':'#root-canvas'},'max_width':200,'max_height':120},READ_ONLY);assert image['success']
        target={'image':value(image)['visual_reference']['id'],'x':30,'y':20}
        denied=tool('browser_act',{'browser':readonly,'document':page['document'],'op':'click','target':target},READ_ONLY)
        assert not denied['success'] and 'context_mismatch' not in denied['text'] and 'browser_missing' not in denied['text'],denied['text']
        assert 'Root visual clicks 0' in value(tool('browser_observe',{'browser':readonly},READ_ONLY))['text']
        dev.call('browser.close',{'id':readonly},ro_owner);readonly=None
        stage='readonly-owned-drag';readonly=dev.call('browser.create',{'client':clients[0]['id'],'url':fixture.origin+'/visual-drag-root'},ro_owner)['id']
        page=value(tool('browser_observe',{'browser':readonly},READ_ONLY));image=tool('browser_screenshot',{'browser':readonly,'document':page['document'],'max_width':1200,'max_height':800},READ_ONLY);assert image['success'];meta=value(image)
        denied=tool('browser_act',{'browser':readonly,'document':page['document'],'op':'drag','target':pixel(70,50),'destination':pixel(70,230)},READ_ONLY)
        assert not denied['success'] and 'context_mismatch' not in denied['text'] and 'browser_missing' not in denied['text'],denied['text']
        assert 'Root pointer releases' not in value(tool('browser_observe',{'browser':readonly},READ_ONLY))['text']
        dev.call('browser.close',{'id':readonly},ro_owner);readonly=None
        report.update(managed_visual_click=True,managed_visual_drag=True,owned_readonly_image_allowed_point_denied=True,owned_readonly_visual_drag_denied=True,full_access_no_extra_prompt=True)
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'stage':stage,'error':str(error)[:512]})+'\n',encoding='utf-8')
        if isinstance(error,subprocess.CalledProcessError):print((error.stderr or '')[-1800:],file=sys.stderr)
        raise
    finally:
        for identity,scope in ((browser,owner),(readonly,ro_owner)):
            if identity:
                try:dev.call('browser.close',{'id':identity},scope)
                except (RuntimeError,OSError):pass
        dev.stop();dev.start();fixture.close()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
