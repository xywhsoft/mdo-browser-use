"""Actual SDK path context for frozen native-to-UI continuations."""
import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class WorkspaceContextTests(unittest.TestCase):
    def test_explicit_workspace_and_ui_continuation_preserve_native_task_root(self):
        root=dev.ENV/'qa';root.mkdir(exist_ok=True)
        path=root/'frozen-workspace.bin';path.write_bytes(b'frozen task source')
        scope={'extension':'workspace-continuation-qa','project':'default','session':'workspace-task','workspace':str(root)}
        self.assertEqual(Path(dev.call('sdk.context',context=scope)['workspace']),root)
        snapshot=dev.call('files.digest',{'path':path.name,'space':'workspace'},scope)
        self.assertEqual(snapshot['sha256'],hashlib.sha256(path.read_bytes()).hexdigest())
        client='workspace-continuation-client';dev.call('client.hello',{'client':client,'context':{}})
        browser=dev.call('browseruse.tabs',{'action':'create','client':client},scope)['id']
        try:
            receipt=dev.call('browseruse.observe',{'browser':browser},scope)
            queued=next(row for row in dev.call('client.poll',{'client':client}) if row['id']==receipt['id'])
            self.assertEqual(Path(queued['context']['workspace']),root)
            self.assertEqual(queued['context']['session'],scope['session'])
            self.assertEqual(dev.call('files.digest',{'path':path.name},queued['context'])['sha256'],snapshot['sha256'])
            dev.call('browseruse.result',{'id':receipt['id'],'cancel':True},scope)
            dev.call('browseruse.result',{'id':receipt['id'],'release':True},scope)
        finally:dev.call('browseruse.tabs',{'action':'close','browser':browser},scope)

    def test_invalid_workspace_never_falls_back_to_a_different_root(self):
        for workspace in ('relative','C:relative','\\root-relative',True,123,{},'C:\\valid\x00hidden','C:\\valid\nother','x'*4097):
            with self.subTest(workspace=workspace):
                status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'sdk.context',
                    'context':{'extension':'workspace-continuation-qa','workspace':workspace}})
                self.assertEqual(status,422,json.loads(raw));self.assertEqual(json.loads(raw)['error']['code'],'sdk_context_invalid')
        self.assertEqual(dev.call('sdk.context',context={'workspace':''})['workspace'],dev.call('sdk.context')['workspace'])


if __name__=='__main__':unittest.main()
