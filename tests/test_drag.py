"""Actual C validation and ownership for the shared drag action."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import dev


class DragTests(unittest.TestCase):
    scope={"extension":"drag-sdk-qa","session":"drag-task"}

    def test_drag_rejects_missing_or_imprecise_destinations_before_admission(self):
        base={"browser":"unused","document":"fresh","op":"drag","target":{"ref":"source"}}
        for fields,code in [({},"destination_required"),({"destination":{}},"destination_required"),
            ({"destination":{"frame":"f0"}},"destination_required"),
            ({"destination":{"ref":"drop","name":"Other"}},"target_invalid"),
            ({"destination":{"script":"1"}},"arguments_invalid"),
            ({"destination":{"name":False}},"arguments_invalid"),
            ({"op":"click","destination":{"ref":"drop"}},"arguments_invalid")]:
            with self.subTest(fields=fields):
                status,_,raw=dev.request("POST","/api/v1/application/call",{
                    "method":"browseruse.act","params":dict(base,**fields),"context":self.scope})
                self.assertEqual(status,422,json.loads(raw))
                self.assertEqual(json.loads(raw)["error"]["code"],code)

    def test_valid_drag_keeps_both_targets_and_the_original_task(self):
        client="drag-sdk-client";dev.call("client.hello",{"client":client,"context":{}})
        browser=dev.call("browseruse.tabs",{"action":"create","client":client},self.scope)["id"]
        receipt=None
        try:
            destination={"name":"Drop target","role":"button"}
            receipt=dev.call("browseruse.act",{"browser":browser,"document":"fresh","op":"drag",
                "target":{"ref":"source"},"destination":destination},self.scope)
            queued=next(row for row in dev.call("client.poll",{"client":client}) if row["id"]==receipt["id"])
            self.assertEqual(queued["params"]["destination"],destination)
            self.assertEqual(queued["params"]["target"],{"ref":"source"})
            self.assertEqual(queued["context"]["session"],self.scope["session"])
            status,_,raw=dev.request("POST","/api/v1/application/call",{
                "method":"browseruse.result","params":{"id":receipt["id"],"cancel":True},
                "context":dict(self.scope,session="foreign")})
            self.assertEqual(status,409,json.loads(raw))
        finally:
            if receipt:
                dev.call("browseruse.result",{"id":receipt["id"],"cancel":True},self.scope)
                dev.call("browseruse.result",{"id":receipt["id"],"release":True},self.scope)
            dev.call("browseruse.tabs",{"action":"close","browser":browser},self.scope)

    def test_visual_drag_preserves_one_image_and_both_points_in_the_shared_receipt(self):
        client='visual-drag-sdk-qa';dev.call('client.hello',{'client':client,'context':{}})
        browser=dev.call('browseruse.tabs',{'action':'create','client':client},self.scope)['id'];receipt=None
        try:
            source={'image':'vs-'+'a'*48,'x':10,'y':20};destination={**source,'x':200,'y':120}
            receipt=dev.call('browseruse.act',{'browser':browser,'document':'fresh','op':'drag','target':source,'destination':destination},self.scope)
            queued=next(row for row in dev.call('client.poll',{'client':client}) if row['id']==receipt['id'])
            self.assertEqual(queued['params']['target'],source);self.assertEqual(queued['params']['destination'],destination)
            self.assertEqual(queued['context']['session'],self.scope['session'])
        finally:
            if receipt:
                dev.call('browseruse.result',{'id':receipt['id'],'cancel':True},self.scope)
                dev.call('browseruse.result',{'id':receipt['id'],'release':True},self.scope)
            dev.call('browseruse.tabs',{'action':'close','browser':browser},self.scope)


if __name__=="__main__":unittest.main()
