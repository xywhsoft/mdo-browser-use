"""Do not accept green permission reports without the terminal-pair gate."""
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import acceptance_probe


class ToolTerminalReceiptTests(unittest.TestCase):
    def test_permission_gate_rejects_missing_or_incomplete_terminal_evidence(self):
        report={'passed':True,'state':'passed','headless_restored':True,'model_calls':0,
            'visual_prompt_verified':False,'tool_terminals_verified':10,'cases':[
                'cancelled_prompt_cannot_accept_late_grant_and_next_tool_works',
                'dispatched_cancel_retains_uncertainty_drains_ui_and_next_tool_works',
                'run_scoped_grant_does_not_leak_to_next_native_drive',
                'approval_preserves_original_document_and_failure_is_recoverable']}
        acceptance_probe.validate('permission-decisions',report)
        for count in [None,0,9,True,'10',10.0]:
            with self.subTest(count=count),self.assertRaisesRegex(RuntimeError,'terminal pairs'):
                acceptance_probe.validate('permission-decisions',dict(report,tool_terminals_verified=count))


if __name__=='__main__':unittest.main()
