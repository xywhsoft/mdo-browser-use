"""C selection schemas and read-only option discovery admission."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import dev


class SelectionTests(unittest.TestCase):
    scope={"extension":"selection-qa","session":"selection-task"}

    def reject(self,method,params,code):
        status,_,raw=dev.request("POST","/api/v1/application/call",{
            "method":method,"params":params,"context":self.scope})
        self.assertEqual(status,422,json.loads(raw))
        self.assertEqual(json.loads(raw)["error"]["code"],code)

    def test_choice_arrays_and_discovery_are_validated_before_queuing(self):
        base={"browser":"unused","document":"fresh","op":"select","target":{"ref":"e1"}}
        for fields,code in [({},"choices_required"),({"choices":"Express"},"arguments_invalid"),
            ({"choices":[True]},"arguments_invalid"),({"choices":[{}]},"choice_invalid"),
            ({"choices":[{"label":"Express","value":"x"}]},"choice_invalid"),
            ({"choices":[{"index":-1}]},"arguments_invalid"),({"choices":[{"index":256}]},"arguments_invalid"),
            ({"choices":[{"index":1.5}]},"arguments_invalid"),({"choices":[{"script":"1"}]},"arguments_invalid"),
            ({"choices":[{"value":"x"*257}]},"arguments_invalid"),
            ({"choices":[{"index":0}]*33},"arguments_invalid"),
            ({"op":"click","choices":[]},"arguments_invalid")]:
            with self.subTest(fields=fields):self.reject("browseruse.act",dict(base,**fields),code)
        discovery={"browser":"unused","mode":"options"}
        for fields,code in [({},"target_required"),({"document":"fresh"},"target_required"),
            ({"document":"fresh","target":{"frame":"f0"}},"target_required"),
            ({"document":"fresh","target":{"ref":"e1","name":"Other"}},"target_invalid"),
            ({"mode":"readable","offset":1},"arguments_invalid"),
            ({"document":"fresh","target":{"ref":"e1"},"offset":257},"arguments_invalid")]:
            with self.subTest(fields=fields):self.reject("browseruse.observe",dict(discovery,**fields),code)

    def test_exact_choices_and_paged_discovery_reach_scoped_ui_receipts(self):
        client="selection-sdk-client";dev.call("client.hello",{"client":client,"context":{}})
        browser=dev.call("browseruse.tabs",{"action":"create","client":client},self.scope)["id"]
        try:
            for choices in ([],[{"index":0}],[{"label":"Express"}],[{"value":""}]):
                receipt=dev.call("browseruse.act",{"browser":browser,"document":"fresh","op":"select",
                    "target":{"ref":"e1"},"choices":choices},self.scope)
                queued=next(row for row in dev.call("client.poll",{"client":client}) if row["id"]==receipt["id"])
                self.assertEqual(queued["params"]["choices"],choices)
                dev.call("browseruse.result",{"id":receipt["id"],"cancel":True},self.scope)
                dev.call("browseruse.result",{"id":receipt["id"],"release":True},self.scope)
            receipt=dev.call("browseruse.observe",{"browser":browser,"mode":"options","document":"fresh",
                "target":{"ref":"e1"},"offset":2,"limit":2},self.scope)
            queued=next(row for row in dev.call("client.poll",{"client":client}) if row["id"]==receipt["id"])
            self.assertEqual(queued["params"]["offset"],2)
            self.assertEqual(queued["params"]["mode"],"options")
            dev.call("browseruse.result",{"id":receipt["id"],"cancel":True},self.scope)
            dev.call("browseruse.result",{"id":receipt["id"],"release":True},self.scope)
        finally:dev.call("browseruse.tabs",{"action":"close","browser":browser},self.scope)


if __name__=="__main__":unittest.main()
