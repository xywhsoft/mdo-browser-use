"""Real public C/JS/native screenshots of a loopback private-pixel fixture.

The report contains only dimensions/equality, never image data. Reuses the one
deployment, Home, existing fixture page and current native host.
"""
import base64
import json
import sys
import time
import dev
from sdk_probe import SCOPE,wait_receipt


def main():
    output=dev.ENV/'capture-privacy-public-result.json';browser=None;cases=[];stage='startup'
    output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    (dev.SOURCE/'app/web/__browser_use_page__.html').write_bytes((dev.ROOT/'tests/fixtures/capture-privacy.html').read_bytes())
    try:
        dev.stop();dev.start(window=True);clients=[];end=time.monotonic()+30
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The actual workbench did not register')
        url=f"http://127.0.0.1:{dev.state()['port']}/__browser_use_page__.html"
        browser=dev.call('browseruse.tabs',{'action':'create','client':clients[0]['id'],'url':url+'?variant=0','key':'capture-privacy-public'},SCOPE)['id']
        def execute(method,params):
            queued=dev.call(method,{'browser':browser,**params},SCOPE)
            try:return wait_receipt(queued['id'])['result']
            finally:dev.call('browseruse.result',{'id':queued['id'],'release':True},SCOPE)
        observed=execute('browseruse.observe',{});last_document=observed['document']
        for fmt in ('png','jpeg'):
            images=[]
            for variant in (0,1):
                stage=f'{fmt}-variant-{variant}'
                page=execute('browseruse.navigate',{'action':'goto','url':url+f'?variant={variant}'})['observation']
                if page.get('title')!='Capture privacy fixture '+str(variant) or page.get('frame_routing')!='native-isolated-v1':
                    raise RuntimeError('The intended isolated fixture document was not observed')
                if page['document']==last_document:raise RuntimeError('Navigation did not rotate the original document identity')
                last_document=page['document']
                captured=execute('browseruse.screenshot',{'document':page['document'],'format':fmt,'max_width':512,'max_height':384,'max_bytes':8192})
                image=captured.get('image',{});data=base64.b64decode(image.get('base64',''),validate=True)
                if not captured.get('ok') or image.get('media_type')!='image/'+fmt or not data or len(data)>8192 or captured.get('redacted_regions',0)<3:
                    raise RuntimeError('Public screenshot format, mask or byte proof failed')
                images.append((image['width'],image['height'],data,captured['redacted_regions']))
            equal=images[0][:3]==images[1][:3]
            cases.append({'public_format':fmt,'encoded_images_equal':equal,'width':images[0][0],'height':images[0][1],
                          'first_bytes':len(images[0][2]),'second_bytes':len(images[1][2]),'redacted_regions':min(images[0][3],images[1][3])})
            if not equal:raise RuntimeError('Changing only the private fixture pixels changed the public screenshot')
        report={'passed':True,'backend':'actual-public-c-js-webview2-capture','cases':cases,'model_calls':0,'external_sites':0}
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'stage':stage,'error':str(error)[:512],'cases':cases},indent=2)+'\n',encoding='utf-8');raise
    finally:
        if browser:
            try:dev.call('browseruse.tabs',{'action':'close','browser':browser},SCOPE)
            except (RuntimeError,OSError):pass
        dev.stop();dev.start()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError) as error:print(error,file=sys.stderr);raise SystemExit(1)
