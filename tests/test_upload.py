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
            (dict(exact,directory={'path':'.'}),'arguments_invalid'),
            (dict(base,document='fresh',target={'ref':'e1'},directory={}),'arguments_invalid'),
            (dict(base,document='fresh',target={'ref':'e1'},directory={'path':'.','files':['escape']}),'arguments_invalid'),
            (dict(base,document='fresh',target={'ref':'e1'},directory={'path':'.','space':'vfs'}),'arguments_invalid'),
            (dict(exact,ticket='u-old'),'arguments_invalid'),
            (dict(base,action='clear'),'upload_target_required'),
            (dict(exact,action='clear'),'arguments_invalid'),
            (dict(base,action='clear',document='fresh',target={'ref':'e1'},directory={'path':'.'}),'arguments_invalid'),
            ({'browser':'absent','action':'status'},'upload_ticket_required'),
            ({'browser':'absent','action':'status','ticket':'u-owned','files':[{'path':'x'}]},'arguments_invalid'),
            ({'browser':'absent','action':'status','ticket':'u-owned','directory':{'path':'.'}},'arguments_invalid'),
            ({'browser':'absent','action':'status','ticket':'u-owned','confirmed':True},'arguments_invalid'),
            ({'browser':'absent','action':'release','ticket':'u-owned'},'upload_confirmation_required')]:
            with self.subTest(params=params):self.reject(params,code)
        expected=next(row for row in json.loads((dev.ROOT/'contracts.json').read_text(encoding='utf-8'))['services'] if row['id']=='browseruse.upload')
        self.assertEqual(dev.call('sdk.describe',{'id':'browseruse.upload'})['input_schema'],expected['input'])

    def test_android_style_clear_receipt_survives_the_shared_c_result_without_requeueing(self):
        client='upload-clear-shared-client';dev.call('client.hello',{'client':client,'context':{}})
        browser=dev.call('browseruse.tabs',{'action':'create','client':client},self.scope)['id'];identity=None
        try:
            identity=dev.call('browseruse.upload',{'browser':browser,'action':'clear','document':'original-document','target':{'ref':'e7'}},self.scope)['id']
            queued=next(row for row in dev.call('client.poll',{'client':client}) if row['id']==identity)
            self.assertEqual(queued['params']['action'],'clear');self.assertNotIn('files',queued['params'])
            payload={'ok':True,'accepted':True,'completed':True,'cleared':True,'verified':True,'dispatched':True,'retained':False,
                     'ticket':'u-00000000-0000-4000-8000-000000000001','upload_key':identity,'navigation':1,
                     'receipt_only':True,'native_job_drained':True,'cleanup_confirmed':True,'input_method':'native_file_clear'}
            dev.call('client.ack',{'client':client,'id':identity,'success':True,'result':payload})
            for _ in range(2):
                result=dev.call('browseruse.result',{'id':identity},self.scope)
                self.assertEqual(result['state'],'succeeded');self.assertEqual(result['result'],payload)
            self.assertFalse(any(row['id']==identity for row in dev.call('client.poll',{'client':client})))
        finally:
            if identity:dev.call('browseruse.result',{'id':identity,'release':True},self.scope)
            dev.call('browseruse.tabs',{'action':'close','browser':browser},self.scope)

    def test_files_are_queued_in_the_original_scope_without_being_read(self):
        client='upload-shared-client';dev.call('client.hello',{'client':client,'context':{}})
        browser=dev.call('browseruse.tabs',{'action':'create','client':client},self.scope)['id']
        try:
            for args in ({'action':'set','document':'fresh','target':{'ref':'e1'},'files':[{'path':'missing-is-not-read-yet.bin','space':'workspace','name':'snapshot.bin'}]},
                         {'action':'set','document':'fresh','target':{'ref':'e1'},'directory':{'path':'missing-is-not-scanned-yet','space':'workspace','name':'selected'}},
                         {'action':'set','document':'fresh','chooser':'directory-picker','directory':{'path':'.'}},
                         {'action':'clear','document':'fresh','target':{'ref':'e1'}},
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
