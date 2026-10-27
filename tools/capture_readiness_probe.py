"""Measure first public screenshots in the same normal UI, without a passing retry."""
import json
import sys
import time

import dev
from sdk_probe import SCOPE,wait_receipt


def main():
    output=dev.ENV/'capture-readiness-result.json'
    output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    (dev.SOURCE/'app/web/__browser_use_page__.html').write_bytes((dev.ROOT/'tests/fixtures/page.html').read_bytes())
    cases=[];browser=None
    try:
        dev.stop();dev.start(window=True)
        end=time.monotonic()+30;clients=[]
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The normal UI did not register its display client')
        for index in range(3):
            browser=dev.call('browseruse.tabs',{'action':'create','client':clients[0]['id'],
                'url':f"http://127.0.0.1:{dev.state()['port']}/__browser_use_page__.html?capture-start={index}",
                'key':'capture-readiness-'+str(index)},SCOPE)['id']
            queued=dev.call('browseruse.observe',{'browser':browser},SCOPE)
            page=wait_receipt(queued['id'])['result']
            if page.get('frame_routing')!='native-isolated-v1':raise RuntimeError('First capture did not use the normal frame driver')
            started=time.monotonic()
            queued=dev.call('browseruse.screenshot',{'browser':browser,'document':page['document'],'max_width':800,'max_height':600},SCOPE)
            receipt=wait_receipt(queued['id'],allow_failure=True)
            row={'case':index,'initial_state':receipt['state'],'elapsed_ms':round((time.monotonic()-started)*1000),
                'viewport_before':page.get('viewport'),'frames_before':[{key:frame.get(key) for key in ('id','document','dom','loading','reason')} for frame in page.get('frames',[])]}
            if receipt['state']!='succeeded':
                row['error']=receipt.get('error')
                current=wait_receipt(dev.call('browseruse.observe',{'browser':browser},SCOPE)['id'])['result']
                row['viewport_after']=current.get('viewport')
                row['frames_after']=[{key:frame.get(key) for key in ('id','document','dom','loading','reason')} for frame in current.get('frames',[])]
                row['recovery_state']=wait_receipt(dev.call('browseruse.screenshot',{'browser':browser,'document':current['document'],'max_width':800,'max_height':600},SCOPE)['id'],allow_failure=True)['state']
            else:
                result=receipt['result'];visual=result['visual_reference']
                assert visual['id'].startswith('vs-') and visual['coordinates']=='image_pixels'
                assert (visual['width'],visual['height'])==(result['image']['width'],result['image']['height'])
                assert set(visual)=={'id','width','height','coordinates','expires_in_ms'}
                row['visual_reference_bound']=True
            cases.append(row)
            dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE);browser=None
        report={'passed':all(row['initial_state']=='succeeded' for row in cases),'backend':'actual-normal-public-capture-startup',
                'initial_failures':sum(row['initial_state']!='succeeded' for row in cases),'cases':cases,'model_calls':0}
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
        if not report['passed']:raise RuntimeError('A first screenshot was rejected; recovery is evidence, not a passing initial result')
    finally:
        if browser:
            try:dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE)
            except (RuntimeError,OSError):pass
        dev.stop();dev.start()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError) as error:print(error,file=sys.stderr);raise SystemExit(1)
