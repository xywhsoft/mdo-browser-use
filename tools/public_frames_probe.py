"""Normal C SDK -> workbench -> OOPIF input/transfers, with real UI receipts."""
import hashlib
import json
import sys
import time

import dev
from frame_fixture import FrameFixture
from upload_cache_probe import inspect_cache
from upload_source_fixture import source_tree
from managed_probe import FULL,READ_ONLY,SCOPE as MANAGED_SCOPE,tool,value


def main():
    output=dev.ENV/'public-frames-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    fixture=FrameFixture().start();browser=None;managed_browser=None;readonly_browser=None;stage='startup';count=0
    scope={'extension':'plugin.mdo.browser-use','workspace':str(dev.ROOT)}
    owner={'extension':'plugin.mdo.browser-use','project':'default','session':FULL};readonly_owner=dict(owner,session=READ_ONLY)
    def service(method,params):
        nonlocal count
        queued=dev.call('browseruse.'+method,params,scope);end=time.monotonic()+35
        while time.monotonic()<end:
            receipt=dev.call('browseruse.result',{'id':queued['id']},scope)
            if receipt['state'] in ('succeeded','failed','cancelled'):
                if receipt['state']!='succeeded':raise RuntimeError(str(receipt.get('error'))[:512])
                count+=1;return receipt['result']
            time.sleep(.08)
        raise RuntimeError('Public frame operation did not terminate')
    source=dev.ENV/'qa/frame-upload-qa.bin';data=bytes(index%251 for index in range(131075));source.parent.mkdir(exist_ok=True)
    if source.exists() and source.read_bytes()!=data:raise RuntimeError('Unknown frame upload fixture contents preserved')
    source.write_bytes(data);sha=hashlib.sha256(data).hexdigest()
    tree,expected=source_tree()
    def directory_rows(page,marker,name):
        text=page['text'];rows,_=json.JSONDecoder().raw_decode(text[text.index(marker)+len(marker):])
        assert len(rows)==len(expected)
        for relative,entry in expected.items():
            matches=[row for row in rows if row['path']==name+'/'+relative]
            assert len(matches)==1 and matches[0]['size']==entry['size'] and matches[0]['sha256']==entry['sha256']
    try:
        for identity,permission in ((FULL,'full-access'),(READ_ONLY,'read-only')):
            created=dev.call('session.create',{'body':{'project_id':'default','client_session_id':identity,'title':'Browser Use managed QA',
                'permission_profile':permission,'workspace_root':str(dev.ROOT)}},MANAGED_SCOPE)
            if created['data']['permission_profile']!=permission:raise RuntimeError('The persistent QA permission changed; refusing to overwrite it')
        dev.stop();dev.start(window=True);end=time.monotonic()+30;clients=[]
        while time.monotonic()<end:
            clients=dev.call('ui.clients')
            if clients:break
            time.sleep(.1)
        if not clients:raise RuntimeError('The normal UI did not register its display client')
        browser=dev.call('browseruse.tabs',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/geometry','key':'public-frame-integration'},scope)['id']
        stage='observation';page=service('observe',{'browser':browser})
        assert page['frame_routing']=='native-isolated-v1' and len(page['frames'])==3
        assert sum(row['separate_process'] for row in page['frames'])==2
        field=next(row for row in page['elements'] if row['name']=='Nested field');assert field['ref'].startswith('r1|') and field['frame']!='f0'
        stage='input';filled=service('act',{'browser':browser,'document':page['document'],'op':'fill','target':{'ref':field['ref']},'text':'Public C iframe 🦉'})
        assert filled['verification']=='field_state'
        options=service('observe',{'browser':browser,'mode':'options','document':page['document'],'target':{'label':'Nested choice'}})
        assert options['ref'].startswith('r1|') and len(options['options'])==2
        assert service('act',{'browser':browser,'document':page['document'],'op':'select','target':{'ref':options['ref']},'choices':[{'index':1}]})['verified']
        stage='capture';image=service('screenshot',{'browser':browser,'document':page['document'],'mode':'element','target':{'ref':field['ref']},'max_width':400,'max_height':200})
        assert image['image']['media_type']=='image/png' and image['redacted_regions']>0
        stage='direct-upload';upload={'browser':browser,'document':page['document'],'target':{'name':'Hidden nested upload'}}
        assigned=service('upload',dict(upload,action='set',files=[{'space':'workspace','path':'.mdo/qa/frame-upload-qa.bin','name':'public-frame.bin'}],timeout_ms=15000))
        assert assigned['verified'] and assigned['transfer']['retained']
        assert service('wait',{'browser':browser,'until':'text','text':sha})['matched']
        cleared=service('upload',dict(upload,action='clear'));assert cleared['cleared'] and cleared['events_trusted'] is False
        assert service('upload',{'browser':browser,'action':'release','ticket':assigned['transfer']['ticket'],'confirmed':True})['released']
        stage='picker-upload';opened=service('act',{'browser':browser,'document':page['document'],'op':'click','target':{'name':'Choose nested file'}})
        assert opened['outcome']=='file_chooser_open'
        picker=service('chooser',{'browser':browser,'action':'list'})['choosers'][0]
        assert picker['frame']==field['frame']
        picked=service('upload',{'browser':browser,'document':page['document'],'action':'set','chooser':picker['id'],
            'files':[{'space':'workspace','path':'.mdo/qa/frame-upload-qa.bin','name':'public-picker.bin'}],'timeout_ms':15000})
        assert picked['verified'] and service('wait',{'browser':browser,'until':'text','text':sha})['matched']
        assert service('upload',{'browser':browser,'action':'release','ticket':picked['transfer']['ticket'],'confirmed':True})['released']
        assert service('act',{'browser':browser,'document':page['document'],'op':'click','target':{'name':'Choose nested file'}})['outcome']=='file_chooser_open'
        picker=service('chooser',{'browser':browser,'action':'list'})['choosers'][0]
        cancelled=service('chooser',{'browser':browser,'action':'cancel','chooser':picker['id']});assert cancelled['cancel_event_sent'] and cancelled['events_trusted'] is False
        stage='directory-oopif'
        folder=service('upload',{'browser':browser,'action':'set','document':page['document'],'target':{'name':'Hidden nested directory upload'},
            'directory':{'path':'.mdo/qa/public-upload-directory','space':'workspace','name':'frame-directory'},'timeout_ms':15000})
        assert folder['verification']=='relative_file_list' and folder['transfer']['retained']
        complete=service('wait',{'browser':browser,'until':'text','text':'DIR1:'})
        directory_rows(complete['observation'],'DIR1:','frame-directory')
        assert service('upload',{'browser':browser,'action':'release','ticket':folder['transfer']['ticket'],'confirmed':True})['released']
        stage='directory-oopif-picker'
        assert service('act',{'browser':browser,'document':page['document'],'op':'click','target':{'name':'Choose nested directory'}})['outcome']=='file_chooser_open'
        picker=service('chooser',{'browser':browser,'action':'list'})['choosers'][0];assert picker['frame']==field['frame']
        folder=service('upload',{'browser':browser,'action':'set','document':page['document'],'chooser':picker['id'],
            'directory':{'path':str(tree),'name':'frame-picker'},'timeout_ms':15000})
        assert folder['verification']=='relative_file_list'
        directory_rows(service('wait',{'browser':browser,'until':'text','text':'DIR2:'})['observation'],'DIR2:','frame-picker')
        assert not service('chooser',{'browser':browser,'action':'list'})['choosers']
        assert service('upload',{'browser':browser,'action':'release','ticket':folder['transfer']['ticket'],'confirmed':True})['released']
        dev.call('browseruse.tabs',{'action':'close','browser':browser},scope);browser=None
        stage='managed-frames';created=tool('browser_open',{'action':'create','client':clients[0]['id'],'url':fixture.origin+'/geometry','key':'managed-frame-integration'})
        assert created['success'];managed_browser=value(created)['id']
        managed_page=value(tool('browser_observe',{'browser':managed_browser}));assert managed_page['frame_routing']=='native-isolated-v1'
        managed_field=next(row for row in managed_page['elements'] if row['name']=='Nested field');assert managed_field['frame']!='f0'
        managed_fill=tool('browser_act',{'browser':managed_browser,'document':managed_page['document'],'op':'fill','target':{'ref':managed_field['ref']},'text':'Managed nested 🦉'})
        assert managed_fill['success'] and value(managed_fill)['verification']=='field_state'
        managed_image=tool('browser_screenshot',{'browser':managed_browser,'document':managed_page['document'],'mode':'element','target':{'ref':managed_field['ref']}})
        assert managed_image['success'] and managed_image['image']['media_type']=='image/png' and 'base64' not in managed_image['text']
        managed_visual=value(managed_image)['visual_reference'];assert managed_visual['id'].startswith('vs-') and managed_visual['coordinates']=='image_pixels'
        assert (managed_visual['width'],managed_visual['height'])==(value(managed_image)['image']['width'],value(managed_image)['image']['height'])
        stage='managed-directory-oopif'
        folder=tool('browser_upload',{'browser':managed_browser,'action':'set','document':managed_page['document'],
            'target':{'name':'Hidden nested directory upload'},'directory':{'path':str(tree),'name':'managed-frame-directory'},'timeout_ms':15000})
        assert folder['success'],folder['text'];selected=value(folder);assert selected['verification']=='relative_file_list'
        completion=tool('browser_wait',{'browser':managed_browser,'until':'text','text':'DIR1:'});assert completion['success'],completion['text']
        directory_rows(value(completion)['observation'],'DIR1:','managed-frame-directory')
        assert tool('browser_upload',{'browser':managed_browser,'action':'release','ticket':selected['transfer']['ticket'],'confirmed':True})['success']
        dev.call('browser.close',{'id':managed_browser},owner);managed_browser=None
        stage='readonly-owned-frame';readonly_browser=dev.call('browser.create',{'client':clients[0]['id'],'url':fixture.origin+'/geometry'},readonly_owner)['id']
        readonly_page=value(tool('browser_observe',{'browser':readonly_browser},READ_ONLY));assert readonly_page['frame_routing']=='native-isolated-v1'
        readonly_field=next(row for row in readonly_page['elements'] if row['name']=='Nested field')
        denied=tool('browser_act',{'browser':readonly_browser,'document':readonly_page['document'],'op':'fill','target':{'ref':readonly_field['ref']},'text':'Must not be written'},READ_ONLY)
        assert not denied['success'] and 'context_mismatch' not in denied['text']
        readonly_image=tool('browser_screenshot',{'browser':readonly_browser,'document':readonly_page['document'],'mode':'element','target':{'ref':readonly_field['ref']}},READ_ONLY)
        assert readonly_image['success'] and readonly_image['image']['media_type']=='image/png'
        denied_directory=tool('browser_upload',{'browser':readonly_browser,'action':'set','document':readonly_page['document'],
            'target':{'name':'Hidden nested directory upload'},'directory':{'path':str(tree)}},READ_ONLY)
        assert not denied_directory['success'] and 'context_mismatch' not in denied_directory['text']
        dev.call('browser.close',{'id':readonly_browser},readonly_owner);readonly_browser=None
        cache=inspect_cache(dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers');assert cache['remaining_reservations']==0
        report={'passed':True,'backend':'normal-public-c-workbench-oopif','operations':count,'native_process_frames':2,
            'qualified_references':True,'nested_unicode_fill':True,'nested_select':True,'nested_private_element_image':True,
            'workspace_upload_complete_sha256':True,'frame_picker_upload_cancel':True,'managed_frame_fill_image':True,
            'owned_readonly_frame_fill_denied_image_allowed':True,'directory_oopif_relative_paths_and_hashes':True,
            'directory_oopif_picker':True,'managed_oopif_directory':True,'owned_readonly_directory_denied':True,'cache':cache,'model_calls':0}
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'stage':stage,'operations':count,'error':str(error)[:1024]})+'\n',encoding='utf-8');raise
    finally:
        for identity,context in ((managed_browser,owner),(readonly_browser,readonly_owner)):
            if identity:
                try:dev.call('browser.close',{'id':identity},context)
                except (RuntimeError,OSError):pass
        if browser:
            try:dev.call('browseruse.tabs',{'action':'close','browser':browser},scope)
            except (RuntimeError,OSError):pass
        dev.stop();dev.start();fixture.close()


if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError,AssertionError) as error:print(error,file=sys.stderr);raise SystemExit(1)
