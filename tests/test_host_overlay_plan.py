"""Validate every reviewed overlay anchor without modifying the live deployment."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import apply_host
import dev


@unittest.skipUnless((dev.SOURCE/'.git').exists(),'Pinned development source is not set up')
class HostOverlayPlanTests(unittest.TestCase):
    def observed(self):
        return {path:(path.stat().st_mtime_ns,hashlib.sha256(path.read_bytes()).hexdigest()) for path in
                (dev.SOURCE/'tools/android/MdoBrowser.java',dev.ENV/'host-overlays.json')}

    def test_current_plan_composes_with_running_service_state_without_deployment_writes(self):
        before=self.observed();run=dev.run
        def read_only(*args,**kwargs):
            self.assertEqual(args,('git','rev-parse','HEAD'),'Validation invoked a deployment/build helper')
            return run(*args,**kwargs)
        with patch.object(dev,'state',return_value={'pid':123}),patch.object(dev,'run',side_effect=read_only),\
                patch.object(Path,'write_text',side_effect=AssertionError('Validation wrote a file')),\
                patch.object(Path,'replace',side_effect=AssertionError('Validation replaced a file')),\
                contextlib.redirect_stdout(io.StringIO()) as output:
            apply_host.apply(validate=True)
        self.assertIn('composition verified',output.getvalue());self.assertEqual(self.observed(),before)

    def test_a_broken_anchor_fails_before_any_deployment_write(self):
        before=self.observed();spec=dev.ROOT/'host/overlays.json';original=Path.read_text
        broken=json.loads(original(spec,encoding='utf-8'));broken['changes'][0]['before']='missing reviewed anchor for regression'
        def read(path,*args,**kwargs):
            return json.dumps(broken) if path==spec else original(path,*args,**kwargs)
        with patch.object(dev,'state',return_value={'pid':123}),patch.object(Path,'read_text',new=read),\
                patch.object(Path,'write_text',side_effect=AssertionError('Bad plan wrote a file')),\
                self.assertRaisesRegex(RuntimeError,'Overlay does not match'):
            apply_host.apply(validate=True)
        self.assertEqual(self.observed(),before)
