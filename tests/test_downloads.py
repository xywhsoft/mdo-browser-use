"""Exercise download validation and original-task UI receipt ownership in C."""
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev

class DownloadTests(unittest.TestCase):
    scope={'extension':'download-shared-qa','project':'default','session':'download-shared-task'}
    def test_conditional_contract_is_enforced_before_browser_access(self):
        for fields,code in [({'action':'get'},'download_required'),({'action':'accept'},'download_required'),
            ({'action':'list','download':'d-owned'},'arguments_invalid'),
            ({'action':'get','download':'d-owned','max_bytes':100},'arguments_invalid'),
            ({'action':'cancel','download':'d-owned','transfer_timeout_ms':1000},'arguments_invalid'),
            ({'action':'accept','download':'d-owned','path':'evil.bin'},'arguments_invalid'),
            ({'action':'accept','download':'d-owned','max_bytes':67108865},'arguments_invalid')]:
            status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'browseruse.download','params':dict(browser='absent',**fields),'context':self.scope})
            value=json.loads(raw);self.assertEqual(status,422,value);self.assertEqual(value['error']['code'],code)
        contract=next(row for row in json.loads((dev.ROOT/'contracts.json').read_text(encoding='utf-8'))['services'] if row['id']=='browseruse.download')
        self.assertEqual(dev.call('sdk.describe',{'id':'browseruse.download'})['input_schema'],contract['input'])

    def test_queued_metadata_and_budget_keep_the_original_scope(self):
        client='download-shared-client';dev.call('client.hello',{'client':client,'context':{}})
        browser=dev.call('browseruse.tabs',{'action':'create','client':client},self.scope)['id']
        try:
            for fields in [{'action':'list'},{'action':'get','download':'d-owned'},
                {'action':'accept','download':'d-owned','max_bytes':131075,'transfer_timeout_ms':5000}]:
                receipt=dev.call('browseruse.download',dict(browser=browser,**fields),self.scope)
                try:
                    queued=next(row for row in dev.call('client.poll',{'client':client}) if row['id']==receipt['id'])
                    self.assertEqual(queued['params']['command'],'download')
                    self.assertEqual(queued['context']['session'],self.scope['session'])
                    self.assertEqual(queued['context']['extension'],self.scope['extension'])
                    for key,value in fields.items():self.assertEqual(queued['params']['download_max_bytes' if key=='max_bytes' else key],value)
                    status,_,_=dev.request('POST','/api/v1/application/call',{'method':'browseruse.result','params':{'id':receipt['id']},'context':dict(self.scope,session='foreign')})
                    self.assertEqual(status,409)
                finally:
                    dev.call('browseruse.result',{'id':receipt['id'],'cancel':True},self.scope)
                    dev.call('browseruse.result',{'id':receipt['id'],'release':True},self.scope)
        finally:dev.call('browseruse.tabs',{'action':'close','browser':browser},self.scope)

if __name__=='__main__':unittest.main()
