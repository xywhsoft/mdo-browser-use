"""Save C boundary, original workspace capture and private receipt ownership."""
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev

class SaveTests(unittest.TestCase):
    def test_paths_are_rejected_before_browser_or_workspace_access(self):
        for path in ('../outside','/absolute','C:\\absolute','a//b','a/./b','a/../b','a/','a.','a ','file\x00hidden','file\nname','a:b'):
            status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'browseruse.save','params':{'browser':'absent','download':'d-owned','path':path}})
            result=json.loads(raw);self.assertEqual(status,422,result);self.assertIn(result['error']['code'],('save_path_invalid','arguments_invalid'))
        document=json.loads((dev.ROOT/'contracts.json').read_text(encoding='utf-8'))
        row=next(row for row in document['services'] if row['id']=='browseruse.save')
        self.assertEqual(dev.call('sdk.describe',{'id':row['id']})['input_schema'],row['input'])
        self.assertIn('MDO_TOOL_EFFECT_WORKSPACE_WRITE',next(row for row in document['tools'] if row['id']=='browser_save_download')['effects'])

    def test_queue_keeps_physical_root_private_task_and_destination(self):
        root=dev.ENV/'qa'/'saved';root.mkdir(parents=True,exist_ok=True)
        scope={'extension':'save-contract-qa','project':'default','session':'save-task','namespace':'private-save','member':'worker','workspace':str(root)}
        client='save-contract-client';dev.call('client.hello',{'client':client,'context':{}})
        browser=dev.call('browseruse.tabs',{'action':'create','client':client},scope)['id']
        try:
            receipt=dev.call('browseruse.save',{'browser':browser,'download':'d-owned','path':'export.bin'},scope)
            try:
                action=next(row for row in dev.call('client.poll',{'client':client}) if row['id']==receipt['id'])
                self.assertEqual(action['params']['command'],'save_download');self.assertEqual(action['params']['path'],'export.bin')
                self.assertEqual(action['params']['root_snapshot'],dev.call('files.stat',{'path':'.'},scope)['revision'])
                self.assertEqual(action['params']['timeout_ms'],60000)
                self.assertEqual(action['context']['workspace'],str(root));self.assertEqual(action['context']['namespace'],scope['namespace'])
                for foreign in (dict(scope,namespace='foreign'),dict(scope,member='foreign'),dict(scope,workspace=str(root.parent))):
                    for method,params in (('browseruse.save',{'browser':browser,'download':'d-owned','path':'export.bin'}),
                                          ('browseruse.result',{'id':receipt['id']}),('browseruse.result',{'id':receipt['id'],'cancel':True})):
                        status,_,raw=dev.request('POST','/api/v1/application/call',{'method':method,'params':params,'context':foreign})
                        self.assertEqual(status,409,json.loads(raw));self.assertEqual(json.loads(raw)['error']['code'],'context_mismatch')
            finally:
                dev.call('browseruse.result',{'id':receipt['id'],'cancel':True},scope)
                dev.call('browseruse.result',{'id':receipt['id'],'release':True},scope)
        finally:dev.call('browseruse.tabs',{'action':'close','browser':browser},scope)

if __name__=='__main__':unittest.main()
