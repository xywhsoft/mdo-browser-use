"""Actual legacy metadata recovery in the single existing development Home."""
import hashlib
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class BrowserWorkspaceTests(unittest.TestCase):
    session='b0a00000000000000000000000000003'
    client='browser-legacy-root-qa'

    @classmethod
    def setUpClass(cls):
        cls.directory=dev.ENV/'qa/legacy-root';cls.directory.mkdir(parents=True,exist_ok=True)
        cls.original=cls.directory/'original';cls.original.mkdir(exist_ok=True)
        cls.other=cls.directory/'current-chat';cls.other.mkdir(exist_ok=True)
        cls.scope={'extension':'plugin.mdo.browser-use','device':'local','project':'default','session':cls.session,
                   'workspace':str(cls.other),'namespace':'legacy-private','member':'legacy-worker'}
        created=dev.call('session.create',{'body':{'project_id':'default','client_session_id':cls.session,'title':'Browser Use legacy root QA',
          'workspace_root':str(cls.original),'permission_profile':'full-access'}},cls.scope)['data']
        if Path(created['workspace_root'])!=cls.original:raise RuntimeError('Persistent QA task changed; refusing to overwrite it')
        dev.call('client.hello',{'client':cls.client})
        cls.rows={};cls.scopes={}
        for name in unittest.defaultTestLoader.getTestCaseNames(cls):
            scope={**cls.scope}
            if name=='test_missing_original_task_has_no_default_fallback':scope['session']='b0a00000000000000000000000000009'
            row=dev.call('browser.create',{'client':cls.client,'key':name},scope)
            cls.rows[name]=row['id'];cls.scopes[name]=scope
        # Create actual pre-upgrade rows by removing only this fixture's root
        # fields while the sole host is stopped. No copied Home or fake service.
        dev.stop()
        try:
            path=dev.HOME/'data/workbench.json';before=path.read_bytes();state=json.loads(before)
            ids=set(cls.rows.values());edited=set()
            for row in state['browsers']:
                if row['id'] not in ids:continue
                if row['owner']!=cls.scope['extension']:raise RuntimeError('Unexpected legacy fixture owner')
                if row['key']!='test_existing_binding_cannot_be_replaced':row['context'].pop('workspace',None)
                edited.add(row['id'])
            if edited!=ids or path.read_bytes()!=before:raise RuntimeError('Workbench changed before legacy fixture preparation')
            receipt={'fixture_browsers':sorted(ids),'before_sha256':hashlib.sha256(before).hexdigest()}
            (dev.ENV/'legacy-root-fixture.json').write_text(json.dumps(receipt)+'\n',encoding='utf-8')
            temporary=path.with_suffix('.legacy-qa.tmp');temporary.write_text(json.dumps(state,ensure_ascii=False)+'\n',encoding='utf-8');temporary.replace(path)
        finally:dev.start()
        dev.call('client.hello',{'client':cls.client})

    @classmethod
    def tearDownClass(cls):
        for name,identity in cls.rows.items():
            dev.call('browser.close',{'id':identity},cls.scopes[name])

    def setUp(self):
        self.identity=self.rows[self._testMethodName];self.owner=self.scopes[self._testMethodName]

    def raw(self,method,params,scope=None):
        status,_,body=dev.request('POST','/api/v1/application/call',{'method':method,'params':params,'context':scope or self.owner})
        return status,json.loads(body)

    def lookup(self):return dev.call('browser.workspace.lookup',{'id':self.identity},self.owner)

    def adopt(self,**overrides):return self.raw('browser.workspace.adopt',{**self.lookup(),'confirmed':True,**overrides})

    def get(self):return dev.call('browser.get',{'id':self.identity},self.owner)

    def move_root(self):
        held=self.directory/'retained-original'
        if held.exists():raise RuntimeError('Previous renamed-root evidence exists')
        if self.original.resolve().parent!=self.directory.resolve() or held.resolve().parent!=self.directory.resolve():raise RuntimeError('Root fixture leaves its directory')
        self.original.rename(held);return held

    def test_lookup_uses_original_task_without_changing_browser(self):
        before=self.get();candidate=self.lookup();after=self.get()
        self.assertEqual(before,after);self.assertNotIn('workspace',after['context'])
        self.assertEqual(Path(candidate['workspace']),self.original);self.assertNotEqual(Path(candidate['workspace']),Path(self.owner['workspace']))
        self.assertEqual(candidate['session'],self.session);self.assertTrue(candidate['root_snapshot'].startswith('f-'))
        self.assertEqual(dev.call('client.poll',{'client':self.client}),[])

    def test_adoption_fills_only_root_and_keeps_private_task(self):
        before=self.get();status,result=self.adopt();self.assertEqual(status,200,result);row=result['data'];self.assertEqual(row,self.get())
        self.assertEqual(row['revision'],before['revision']+1);self.assertEqual(Path(row['context']['workspace']),self.original)
        self.assertTrue(row['context']['workspace_snapshot'].startswith('f-'))
        self.assertEqual({key:value for key,value in row['context'].items() if key not in ('workspace','workspace_snapshot')},before['context'])
        self.assertEqual(row.get('url'),before.get('url'));self.assertEqual(dev.call('client.poll',{'client':self.client}),[])

    def test_explicit_confirmation_and_all_review_preconditions_are_required(self):
        before=self.get();candidate=self.lookup()
        for change in ({'confirmed':False},{'confirmed':None},{'revision':0},{'session_revision':0},{'workspace':str(self.other)}, {'root_snapshot':'f-0-0-0-0'}):
            with self.subTest(change=change):
                status,result=self.raw('browser.workspace.adopt',{**candidate,'confirmed':True,**change})
                self.assertNotEqual(status,200,result);self.assertEqual(self.get(),before)

    def test_foreign_owner_task_namespace_and_member_cannot_recover(self):
        for key,value in [('extension','foreign-owner'),('session','other-session'),('namespace','other-private'),('member','other-worker')]:
            with self.subTest(key=key):
                status,result=self.raw('browser.workspace.lookup',{'id':self.identity},{**self.owner,key:value})
                self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'context_mismatch')

    def test_existing_binding_cannot_be_replaced(self):
        before=self.get();status,result=self.raw('browser.workspace.lookup',{'id':self.identity})
        self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'browser_workspace_bound')
        self.assertEqual(self.get(),before)

    def test_missing_original_task_has_no_default_fallback(self):
        status,result=self.raw('browser.workspace.lookup',{'id':self.identity})
        self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'browser_workspace_task_missing')
        self.assertNotIn('workspace',self.get()['context'])

    def test_missing_directory_is_not_automatically_created(self):
        held=self.move_root()
        try:
            status,result=self.raw('browser.workspace.lookup',{'id':self.identity});self.assertEqual(status,409,result)
            self.assertEqual(result['error']['code'],'browser_workspace_unavailable');self.assertFalse(self.original.exists())
        finally:held.rename(self.original)

    def test_replaced_reviewed_root_is_rejected(self):
        candidate=self.lookup();held=self.move_root();self.original.mkdir()
        try:
            status,result=self.raw('browser.workspace.adopt',{**candidate,'confirmed':True});self.assertEqual(status,412,result)
            self.assertEqual(result['error']['code'],'browser_workspace_conflict');self.assertNotIn('workspace',self.get()['context'])
        finally:self.original.rmdir();held.rename(self.original)

    def test_directory_content_changes_preserve_identity_and_adoption(self):
        candidate=self.lookup();file=self.original/'directory-time-fixture.bin'
        if file.exists():raise RuntimeError('Previous directory-time evidence exists')
        file.write_bytes(b'directory timestamp may change')
        try:
            status,result=self.raw('browser.workspace.adopt',{**candidate,'confirmed':True});self.assertEqual(status,200,result)
        finally:file.unlink()

    def test_adopted_root_replacement_is_rejected_before_save_or_browser_input(self):
        status,result=self.adopt();self.assertEqual(status,200,result);held=self.move_root();self.original.mkdir()
        try:
            status,result=self.raw('browseruse.save',{'browser':self.identity,'download':'d-00000000-0000-0000-0000-000000000000','path':'new.bin'},
              {**self.owner,'workspace':str(self.original)})
            self.assertEqual(status,412,result);self.assertEqual(result['error']['code'],'workspace_snapshot_changed')
            self.assertEqual(dev.call('client.poll',{'client':self.client}),[]);self.assertFalse((self.original/'new.bin').exists())
        finally:self.original.rmdir();held.rename(self.original)


if __name__=='__main__':unittest.main()
