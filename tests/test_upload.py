"""Validate the actual shared C upload service and original task UI context."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class UploadTests(unittest.TestCase):
    scope={'extension':'upload-shared-qa','project':'default','session':'upload-shared-task'}

    def reject(self,params,code):
        status,_,raw=dev.request('POST','/api/v1/application/call',{
            'method':'browseruse.upload','params':params,'context':self.scope})
        result=json.loads(raw);self.assertEqual(status,422,result);self.assertEqual(result['error']['code'],code)

    def test_upload_contract_validates_before_task_file_or_browser_access(self):
        base={'browser':'absent','action':'set'}
        exact=dict(base,document='fresh',target={'ref':'e1'},files=[{'path':'not-read.bin'}])
        for params,code in [(base,'upload_target_required'),(dict(exact,files=[]),'arguments_invalid'),
            (dict(exact,target={'frame':'f0'}),'upload_target_required'),
            (dict(exact,target={'ref':'e1','name':'other'}),'target_invalid'),
            (dict(exact,files=[{'path':'x','script':'evil'}]),'arguments_invalid'),
            (dict(exact,files=[{'path':'x','space':'vfs'}]),'arguments_invalid'),
            (dict(exact,ticket='u-old'),'arguments_invalid'),
            ({'browser':'absent','action':'status'},'upload_ticket_required'),
            ({'browser':'absent','action':'status','ticket':'u-owned','files':[{'path':'x'}]},'arguments_invalid'),
            ({'browser':'absent','action':'status','ticket':'u-owned','confirmed':True},'arguments_invalid'),
            ({'browser':'absent','action':'release','ticket':'u-owned'},'upload_confirmation_required')]:
            with self.subTest(params=params):self.reject(params,code)
        expected=next(row for row in json.loads((dev.ROOT/'contracts.json').read_text(encoding='utf-8'))['services'] if row['id']=='browseruse.upload')
        self.assertEqual(dev.call('sdk.describe',{'id':'browseruse.upload'})['input_schema'],expected['input'])

    def test_files_are_queued_in_the_original_scope_without_being_read(self):
        client='upload-shared-client';dev.call('client.hello',{'client':client,'context':{}})
        browser=dev.call('browseruse.tabs',{'action':'create','client':client},self.scope)['id']
        try:
            for args in ({'action':'set','document':'fresh','target':{'ref':'e1'},'files':[{'path':'missing-is-not-read-yet.bin','space':'workspace','name':'snapshot.bin'}]},
                         {'action':'status','ticket':'u-ticket'}, {'action':'release','ticket':'u-ticket','confirmed':True}):
                receipt=dev.call('browseruse.upload',dict(browser=browser,**args),self.scope)
                queued=next(row for row in dev.call('client.poll',{'client':client}) if row['id']==receipt['id'])
                self.assertEqual(queued['params']['command'],'upload')
                self.assertEqual(queued['context']['session'],self.scope['session'])
                self.assertEqual(queued['context']['extension'],self.scope['extension'])
                for key,value in args.items():self.assertEqual(queued['params'][key],value)
                dev.call('browseruse.result',{'id':receipt['id'],'cancel':True},self.scope)
                dev.call('browseruse.result',{'id':receipt['id'],'release':True},self.scope)
        finally:dev.call('browseruse.tabs',{'action':'close','browser':browser},self.scope)


if __name__=='__main__':unittest.main()
