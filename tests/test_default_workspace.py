"""Canonical default-root preparation in the one existing QA Home.

Rename, never copy, that Home's exact workspace only for bounded missing-root
cases. Restore its original physical directory, retaining all existing contents.
"""
from contextlib import contextmanager
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class DefaultWorkspaceTests(unittest.TestCase):
    session='e2800000000000000000000000000001'
    client='browser-default-workspace-qa'

    @classmethod
    def setUpClass(cls):
        cls.root=Path(dev.call('sdk.context',context={'project':'default'})['workspace'])
        if cls.root.resolve()!=(dev.HOME/'workspace').resolve():raise RuntimeError('Default-root gate must stay in this QA Home')
        cls.scope={'extension':'plugin.mdo.browser-use','device':'local','project':'default','session':cls.session,
                   'workspace':str(cls.root),'namespace':'default-root-private','member':'default-root-worker'}
        task=dev.call('session.create',{'body':{'project_id':'default','client_session_id':cls.session,
            'title':'Browser Use default root QA','permission_profile':'full-access'}},cls.scope)['data']
        if Path(task['workspace_root'])!=cls.root or task['permission_profile']!='full-access':
            raise RuntimeError('Persistent QA task changed; preserve its original data')
        dev.call('client.hello',{'client':cls.client});cls.rows={};cls.scopes={}
        for name in unittest.defaultTestLoader.getTestCaseNames(cls):
            scope=dict(cls.scope)
            if name=='test_missing_task_cannot_prepare_a_default_directory':scope['session']='e280000000000000000000000000000f'
            cls.rows[name]=dev.call('browser.create',{'client':cls.client,'key':name},scope)['id'];cls.scopes[name]=scope
        legacy=cls.rows['test_legacy_empty_default_binding_can_be_prepared']
        # This is a real legacy record in the same Home, not a replacement app.
        dev.stop()
        try:
            path=dev.HOME/'data/workbench.json';before=path.read_bytes();state=json.loads(before)
            row=next(row for row in state['browsers'] if row['id']==legacy)
            if row['owner']!=cls.scope['extension'] or row['context']['session']!=cls.session:
                raise RuntimeError('Legacy preparation fixture lost its original owner')
            row['context'].pop('workspace',None)
            if path.read_bytes()!=before:raise RuntimeError('Workbench changed before fixture preparation')
            temporary=path.with_suffix('.default-root-qa.tmp');temporary.write_text(json.dumps(state)+'\n',encoding='utf-8');temporary.replace(path)
        finally:subprocess.run([sys.executable,'tools/dev.py','start'],cwd=dev.ROOT,check=True,capture_output=True)
        dev.call('client.hello',{'client':cls.client})

    @classmethod
    def tearDownClass(cls):
        for name,identity in cls.rows.items():dev.call('browser.close',{'id':identity},cls.scopes[name])

    def setUp(self):self.identity=self.rows[self._testMethodName];self.owner=self.scopes[self._testMethodName]
    def raw(self,method,params,scope=None):
        status,_,body=dev.request('POST','/api/v1/application/call',{'method':method,'params':params,'context':scope or self.owner})
        return status,json.loads(body)
    def get(self):return dev.call('browser.get',{'id':self.identity},self.owner)
    def review(self):return dev.call('browser.workspace.lookup',{'id':self.identity,'prepare_default':True},self.owner)
    def prepare(self,candidate,**changes):
        params={key:candidate[key] for key in ('id','revision','workspace','root_snapshot','session_revision','root_exists') if key in candidate}
        return self.raw('browser.workspace.prepare',{**params,'confirmed':True,**changes})

    @contextmanager
    def missing_root(self):
        held=dev.HOME/'workspace.browser-use-prepare-held'
        if held.exists() or held.is_symlink():raise RuntimeError('Prior held directory evidence exists; preserve it')
        if self.root.is_symlink() or getattr(self.root,'is_junction',lambda:False)():raise RuntimeError('QA root is a link')
        if held.resolve().parent!=dev.HOME.resolve() or self.root.resolve().parent!=dev.HOME.resolve():raise RuntimeError('QA move escaped Home')
        original=os.stat(self.root,follow_symlinks=False);self.root.rename(held)
        try:yield
        finally:
            if self.root.is_symlink() or getattr(self.root,'is_junction',lambda:False)():raise RuntimeError('Unexpected root link preserved with held directory')
            if self.root.exists():
                if self.root.is_dir():self.root.rmdir()  # Empty only; preserve any unknown contents.
                elif self.root.is_file() and self.root.read_bytes()==b'root-must-not-be-overwritten':self.root.unlink()
                else:raise RuntimeError('Unexpected replacement root preserved with held directory')
            held.rename(self.root)
            restored=os.stat(self.root,follow_symlinks=False)
            if (original.st_dev,original.st_ino)!=(restored.st_dev,restored.st_ino):raise RuntimeError('Original QA directory identity changed')

    def test_readonly_review_reports_missing_root_without_creating_it(self):
        before=self.get()
        with self.missing_root():
            candidate=self.review();self.assertTrue(candidate['prepare_default']);self.assertFalse(candidate['root_exists'])
            self.assertNotIn('root_snapshot',candidate);self.assertFalse(self.root.exists());self.assertEqual(self.get(),before)
            self.assertEqual(Path(candidate['workspace']),self.root);self.assertEqual(candidate['session'],self.session)

    def test_missing_root_is_created_and_physically_pinned_after_confirmation(self):
        before=self.get()
        with self.missing_root():
            candidate=self.review();status,result=self.prepare(candidate);self.assertEqual(status,200,result);row=result['data']
            self.assertTrue(self.root.is_dir());self.assertEqual(row,self.get());self.assertEqual(row['revision'],before['revision']+1)
            self.assertEqual(Path(row['context']['workspace']),self.root);self.assertTrue(row['context']['workspace_snapshot'].startswith('f-'))
            self.assertEqual(row['context']['namespace'],self.owner['namespace']);self.assertEqual(row['context']['member'],self.owner['member'])
            self.assertEqual(row.get('url'),before.get('url'))

    def test_confirmation_browser_task_path_and_reviewed_existence_are_required(self):
        before=self.get()
        with self.missing_root():
            candidate=self.review()
            for changes in ({'confirmed':False},{'revision':0},{'session_revision':candidate['session_revision']+1},
                            {'workspace':str(dev.ENV/'qa/not-default')},{'root_exists':True},{'root_snapshot':'f-1-2-0-0'}):
                with self.subTest(changes=changes):
                    status,result=self.prepare(candidate,**changes);self.assertNotEqual(status,200,result)
                    self.assertFalse(self.root.exists());self.assertEqual(self.get(),before)

    def test_existing_root_needs_its_reviewed_physical_identity(self):
        candidate=self.review();self.assertTrue(candidate['root_exists']);before=self.get()
        with self.missing_root():
            self.root.mkdir()
            status,result=self.prepare(candidate);self.assertEqual(status,412,result)
            self.assertEqual(result['error']['code'],'browser_workspace_conflict');self.assertEqual(self.get(),before)

    def test_pinned_root_cannot_be_reconstructed_or_rebound(self):
        status,result=self.prepare(self.review());self.assertEqual(status,200,result);before=self.get()
        with self.missing_root():
            for method,params in [('browser.workspace.lookup',{'id':self.identity,'prepare_default':True}),
                                  ('browser.workspace.prepare',{'id':self.identity,'confirmed':True,'revision':before['revision'],
                                   'workspace':str(self.root),'session_revision':1,'root_exists':False})]:
                status,result=self.raw(method,params);self.assertEqual(status,409,result)
                self.assertEqual(result['error']['code'],'browser_workspace_bound');self.assertFalse(self.root.exists())
            self.assertEqual(self.get(),before)

    def test_foreign_scope_cannot_inspect_or_prepare(self):
        candidate=self.review();params={key:candidate[key] for key in ('id','revision','workspace','root_snapshot','session_revision','root_exists')}
        for key,value in [('extension','foreign'),('session','other-session'),('namespace','foreign'),('member','foreign'),('workspace',str(dev.ENV/'qa/foreign-root'))]:
            with self.subTest(key=key):
                owner={**self.owner,key:value}
                for method,p in [('browser.workspace.lookup',{'id':self.identity,'prepare_default':True}),('browser.workspace.prepare',{**params,'confirmed':True})]:
                    status,result=self.raw(method,p,owner);self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'context_mismatch')

    def test_missing_task_cannot_prepare_a_default_directory(self):
        with self.missing_root():
            status,result=self.raw('browser.workspace.lookup',{'id':self.identity,'prepare_default':True})
            self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'browser_workspace_task_missing');self.assertFalse(self.root.exists())

    def test_legacy_empty_default_binding_can_be_prepared(self):
        self.assertFalse(self.get()['context'].get('workspace'))
        with self.missing_root():
            status,result=self.prepare(self.review());self.assertEqual(status,200,result)
            self.assertEqual(Path(result['data']['context']['workspace']),self.root)
            self.assertTrue(result['data']['context']['workspace_snapshot'].startswith('f-'))

    def test_file_at_default_root_is_preserved(self):
        before=self.get()
        with self.missing_root():
            self.root.write_bytes(b'root-must-not-be-overwritten')
            status,result=self.raw('browser.workspace.lookup',{'id':self.identity,'prepare_default':True})
            self.assertEqual(status,409,result);self.assertEqual(result['error']['code'],'browser_workspace_unavailable')
            self.assertEqual(self.root.read_bytes(),b'root-must-not-be-overwritten');self.assertEqual(self.get(),before)

    def test_prepare_is_shared_by_generated_c_js_and_discovery(self):
        schema=dev.call('sdk.describe',{'id':'browser.workspace.prepare'})['input_schema']
        self.assertIn('root_exists',schema['required']);self.assertEqual(schema['properties']['confirmed'],{'type':'boolean'})
        header=(dev.SOURCE/'include/mdo/workbench.h').read_text(encoding='utf-8')
        javascript=(dev.SOURCE/'app/web/js/api/workbench-services.js').read_text(encoding='utf-8')
        self.assertIn('mdoBrowserWorkspacePrepare',header);self.assertIn('browser.workspace.prepare',javascript)


if __name__=='__main__':unittest.main()
