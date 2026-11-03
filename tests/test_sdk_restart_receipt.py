from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import acceptance_probe

class SdkRestartReceiptTests(unittest.TestCase):
    def test_missing_restart_evidence_is_rejected_despite_a_green_report(self):
        good={'passed':True,'state':'passed','headless_restored':True,'same_home':True,'model_calls':0,'native_effects':0,
            'no_replay':True,'original_history_unchanged':True,'receipt_unchanged':True,'actual_target_preserved':True,
            'foreign_metadata_rejected':True,'caller_session':'caller','target_session':'target',
            'events':[{'kind':'tool_start','tool_outcome':{'may_have_completed':True}}]}
        acceptance_probe.validate('managed-restart',good)
        for key in ('no_replay','original_history_unchanged','receipt_unchanged','actual_target_preserved','foreign_metadata_rejected'):
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'evidence is incomplete'):
                acceptance_probe.validate('managed-restart',dict(good,**{key:False}))
