"""Real C/SDK point validation must reject malformed input before browser lookup."""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class VisualAdmissionTests(unittest.TestCase):
    def test_visual_target_shape_and_operation_are_validated_before_browser_access(self):
        point={'image':'vs-'+'a'*48,'x':10,'y':20};scope={'extension':'visual-admission-qa'}
        cases=[({'target':{'image':point['image']}},'target_invalid'),
               ({'target':{**point,'ref':'e1'}},'target_invalid'),
               ({'target':{'x':10,'y':20,'name':'Mixed'}},'target_invalid'),
               ({'target':{**point,'image':'vs-'+'Z'*48}},'target_invalid'),
               ({'target':{**point,'image':'guess'}},'arguments_invalid'),
               ({'target':{**point,'x':-1}},'arguments_invalid'),
               ({'target':{**point,'y':2048}},'arguments_invalid'),
               ({'target':{**point,'x':1.5}},'arguments_invalid'),
               ({'target':{**point,'y':True}},'arguments_invalid')]
        cases.extend(({'op':op,'target':point},'unsupported_action') for op in ('fill','type','check','select'))
        cases.append(({'op':'key','target':point},'key_required'))
        cases.extend([
            ({'op':'drag','target':point},'destination_required'),
            ({'op':'drag','target':point,'destination':{'ref':'e1'}},'target_invalid'),
            ({'op':'drag','target':{'ref':'e1'},'destination':point},'target_invalid'),
            ({'op':'drag','target':point,'destination':{**point,'ref':'e1'}},'target_invalid'),
            ({'op':'drag','target':point,'destination':{**point,'image':'vs-'+'b'*48}},'visual_image_mismatch'),
            ({'op':'drag','target':point,'destination':{**point,'x':1.5}},'arguments_invalid'),
            ({'op':'drag','target':{'ref':'e1'},'destination':{'name':'Drop','x':10,'y':10}},'target_invalid')])
        for change,code in cases:
            with self.subTest(change=change):
                status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'browseruse.act',
                    'params':{'browser':'missing-visual-browser','document':'fixture','op':'click',**change},'context':scope})
                reply=json.loads(raw);self.assertEqual(status,422,reply);self.assertEqual(reply['error']['code'],code,reply)
        schema=dev.call('sdk.describe',{'id':'browseruse.act'})['input_schema']['properties']['target']['properties']
        self.assertEqual(schema['x'],{'type':'integer','minimum':0,'maximum':2047});self.assertEqual(schema['image']['maxLength'],51)


if __name__=='__main__':unittest.main()
