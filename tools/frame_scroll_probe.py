"""Real normal-workbench offscreen OOPIF action; same deployment and Home."""
import json
import sys
import time

import dev
from frame_fixture import FrameFixture
from sdk_probe import SCOPE,wait_receipt


def main():
    output=dev.ENV/'frame-scroll-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    fixture=FrameFixture().start();browser=None;stage='startup'
    def service(method,params,allow_failure=False):
        return wait_receipt(dev.call('browseruse.'+method,params,SCOPE)['id'],allow_failure=allow_failure)
    try:
        dev.stop();dev.start(window=True);clients=[];end=time.monotonic()+30
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The normal UI did not register')
        browser=dev.call('browseruse.tabs',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/scroll-root','key':'frame-scroll-qa'},SCOPE)['id']
        stage='observe';page=service('observe',{'browser':browser})['result']
        assert page['frame_routing']=='native-isolated-v1' and len(page['frames'])==3
        assert sum(row['separate_process'] for row in page['frames'])==2
        assert page['viewport']['scroll_y']==0
        target=next(row for row in page['elements'] if row['name']=='Offscreen nested action')
        stage='click';clicked=service('act',{'browser':browser,'document':page['document'],'target':{'ref':target['ref']},'op':'click','timeout_ms':5000},True)
        if clicked['state']!='succeeded':
            output.write_text(json.dumps({'passed':False,'stage':stage,'receipt':clicked,'before_viewport':page['viewport']},indent=2)+'\n',encoding='utf-8')
            raise RuntimeError('The offscreen action was rejected; inspect its original receipt')
        stage='verify';after=service('observe',{'browser':browser})['result']
        assert 'Trusted nested scroll count 1' in after['text']
        assert after['viewport']['scroll_y']>1000
        assert next(row for row in after['elements'] if row['name']=='Offscreen nested action')['ref']==target['ref']
        report={'passed':True,'backend':'normal-public-c-oopif','offscreen_levels':3,'native_process_frames':2,
                'trusted_click_exactly_once':True,'stable_reference_after_scroll':True,'root_scroll_y':after['viewport']['scroll_y'],'model_calls':0}
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
    except Exception:
        if json.loads(output.read_text(encoding='utf-8')).get('state')=='running':
            output.write_text(json.dumps({'passed':False,'stage':stage})+'\n',encoding='utf-8')
        raise
    finally:
        if browser:
            try:dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE)
            except (RuntimeError,OSError):pass
        dev.stop();dev.start();fixture.close()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError) as error:print(error,file=sys.stderr);raise SystemExit(1)
