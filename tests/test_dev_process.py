"""Process identity and a real Windows tree-termination race regression."""
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import dev


class DevelopmentProcessTests(unittest.TestCase):
    def state(self):
        return {"pid":12345,"host":str(dev.ENV/"host/xs.exe"),
                "active_config":str(dev.SOURCE/"app/.browser-use.run.json")}

    def process(self,state):
        return json.dumps({"ExecutablePath":state["host"],
                           "CommandLine":state["host"]+' "'+state["active_config"]+'"'})

    def test_failed_tree_termination_accepts_only_a_missing_root(self):
        state=self.state()
        with patch.object(dev,"state",return_value=state),patch.object(dev,"save") as save,patch.object(dev,"run",
            side_effect=[self.process(state),RuntimeError("A child exited during tree enumeration"),""]) as run:
            dev.stop()
            save.assert_called_once();self.assertIsNone(save.call_args.args[0]["pid"])
            self.assertEqual(run.call_args_list[1].args,("taskkill","/PID","12345","/T","/F"))

    def test_failed_tree_termination_preserves_a_live_root(self):
        state=self.state()
        with patch.object(dev,"state",return_value=state),patch.object(dev,"save") as save,patch.object(dev,"run",
            side_effect=[self.process(state),RuntimeError("Still running"),self.process(state)]):
            with self.assertRaisesRegex(RuntimeError,"Still running"):dev.stop()
            save.assert_not_called();self.assertEqual(state["pid"],12345)

    def test_recycled_foreign_pid_is_never_terminated(self):
        state=self.state()
        foreign=json.dumps({"ExecutablePath":state["host"],"CommandLine":"another application configuration"})
        with patch.object(dev,"state",return_value=state),patch.object(dev,"save") as save,patch.object(dev,"run",return_value=foreign) as run:
            with self.assertRaisesRegex(RuntimeError,"identity"):dev.stop()
            self.assertEqual(run.call_count,1);save.assert_not_called()


if __name__=="__main__":unittest.main()
