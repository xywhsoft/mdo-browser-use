"""Fault the actual final publication journal, then restart the same Home.

The fixture streams 32 MiB with a 64 KiB producer buffer. It locks only its own
receiver ledger after intent is persisted, blocking replacement of the final
receipt while allowing the real no-overwrite file publication to complete.
No test hooks, other application copies or live model calls are used.
"""
import base64
import ctypes
from ctypes import wintypes
import hashlib
import json
import subprocess
import sys
import time
import uuid
import dev

def main():
    root=dev.ENV/'qa'/'publication-recovery';root.mkdir(parents=True,exist_ok=True)
    output=dev.ENV/'publication-recovery-result.json';output.write_text('{"passed":false,"state":"running"}\n',encoding='utf-8')
    target=root/'saved-after-interruption.bin'
    if target.exists():raise RuntimeError('Previous recovery evidence exists; inspect it before rerunning')
    scope={'extension':'publication-recovery-qa','project':'default','session':'publication-recovery','namespace':'private-fault','member':'fault-worker','workspace':str(root)}
    chunk=bytes(range(256))*256;size=32*1024*1024;hasher=hashlib.sha256()
    for _ in range(size//len(chunk)):hasher.update(chunk)
    expected=hasher.hexdigest();encoded=base64.b64encode(chunk).decode();handle=None;receiver=publication=None;held=None;passed=False
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.CreateFileW.argtypes=[wintypes.LPCWSTR,wintypes.DWORD,wintypes.DWORD,ctypes.c_void_p,wintypes.DWORD,wintypes.DWORD,wintypes.HANDLE]
    kernel.CreateFileW.restype=wintypes.HANDLE;kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    invalid=ctypes.c_void_p(-1).value
    def wait(identity):
        result=dev.call('operation.wait',{'id':identity,'timeout_ms':10000},scope)
        if not result['terminal']:raise RuntimeError('Tracked recovery operation is still running')
        return result
    try:
        snapshot=dev.call('files.stat',{'path':'.'},scope)['revision']
        handle=dev.call('stream.open',{'capacity':65536},scope)['handle'];receiver='sdk-'+uuid.uuid4().hex
        dev.call('operation.start',{'id':receiver,'method':'files.receive.start','params':{'handle':handle,'root_snapshot':snapshot,'destination':target.name,'size':size,'sha256':expected,'timeout_ms':120000}},scope)
        end=time.monotonic()+60
        for _ in range(size//len(chunk)):
            while True:
                status,_,raw=dev.request('POST','/api/v1/application/call',{'method':'stream.write','context':scope,'params':{'handle':handle,'base64':encoded}})
                result=json.loads(raw)
                if status==200:
                    if result['data']['written']!=len(chunk):raise RuntimeError('Uncertain fixture byte write')
                    break
                if result['error']['code']!='sdk_stream_would_block':raise RuntimeError(result['error']['code'])
                if time.monotonic()>end:raise RuntimeError('Fixture transfer exceeded its bounded deadline')
                time.sleep(.002)
        dev.call('stream.close',{'handle':handle},scope);ready=wait(receiver)
        if ready['state']!='succeeded':raise RuntimeError('Fixture receiver did not complete')
        row=dev.call('files.receive.get',{'id':receiver},scope);source=root/row['source']
        dev.call('operation.release',{'id':receiver},scope);dev.call('stream.release',{'handle':handle},scope);handle=None
        publication='sdk-'+uuid.uuid4().hex;request={'id':publication,'method':'files.receive.publish','params':{'id':receiver,'path':target.name}}
        dev.call('operation.start',request,scope)
        ledger=dev.HOME/'data/application/receivers'/(receiver+'.json');end=time.monotonic()+5
        while time.monotonic()<end:
            current=json.loads(ledger.read_text(encoding='utf-8'))
            if current['state']=='publishing':
                held=kernel.CreateFileW(str(ledger),0x80000000,0x1|0x2,None,3,0,None)
                if held==invalid:held=None;raise ctypes.WinError(ctypes.get_last_error())
                if json.loads(ledger.read_text(encoding='utf-8'))['state']!='publishing':raise RuntimeError('Final receipt committed before the fault was armed; no injected pass claimed')
                break
            if current['state']=='published':raise RuntimeError('The publication fault window was missed; no injected pass claimed')
            time.sleep(.001)
        if not held:raise RuntimeError('Could not arm the exact final-receipt lock')
        failed=wait(publication)
        if failed['state']!='failed' or failed.get('error',{}).get('code')!='sdk_file_receive_receipt' or not failed['error']['may_have_completed']:
            raise RuntimeError('Publication did not fail at the injected final receipt: '+json.dumps(failed.get('error',{})))
        if not target.exists() or json.loads(ledger.read_text(encoding='utf-8'))['state']!='publishing':raise RuntimeError('The intended uncertain publication state was not captured')
        before=target.stat();kernel.CloseHandle(held);held=None
        dev.stop();subprocess.run([sys.executable,str(dev.ROOT/'tools/dev.py'),'start'],check=True,capture_output=True)
        retained=dev.call('files.receive.get',{'id':receiver},scope)
        if retained['state']!='publishing' or retained['destination']!=target.name or retained['publication']!=publication:raise RuntimeError('Original publication intent did not survive restart')
        # Force ordinary admission collection: a complete uncertain source must
        # not be mistaken for an abandoned partial from the previous process.
        empty=hashlib.sha256(b'').hexdigest();fresh_handle=dev.call('stream.open',{},scope)['handle'];fresh='sdk-'+uuid.uuid4().hex
        dev.call('stream.close',{'handle':fresh_handle},scope)
        dev.call('operation.start',{'id':fresh,'method':'files.receive.start','params':{'handle':fresh_handle,'root_snapshot':snapshot,'size':0,'sha256':empty}},scope)
        if wait(fresh)['state']!='succeeded':raise RuntimeError('Fresh admission failed')
        dev.call('files.receive.release',{'id':fresh},scope);dev.call('operation.release',{'id':fresh},scope);dev.call('stream.release',{'handle':fresh_handle},scope)
        if not source.exists():raise RuntimeError('Fresh admission deleted uncertain completed bytes')
        reconcile='sdk-'+uuid.uuid4().hex
        dev.call('operation.start',{'id':reconcile,'method':'files.receive.reconcile','params':{'id':receiver}},scope)
        verified=wait(reconcile)
        if verified['state']!='succeeded':raise RuntimeError('Existing file could not be reconciled: '+json.dumps(verified.get('error',{})))
        row=dev.call('files.receive.get',{'id':receiver},scope)
        if row['state']!='published' or row['publication_verdict']!='verified' or row['publication_file']['sha256']!=expected:raise RuntimeError('Reconciliation did not verify the original complete bytes')
        if target.stat().st_ino!=before.st_ino:raise RuntimeError('Inspection recreated the destination')
        replay=dev.call('operation.start',request,scope)
        if replay['state']!='failed' or replay.get('error',{}).get('code')!='sdk_file_receive_receipt':raise RuntimeError('The interrupted publication identity was replayed')
        dev.call('files.receive.release',{'id':receiver},scope)
        if source.exists() or target.stat().st_ino!=before.st_ino or dev.call('files.digest',{'path':target.name,'max_bytes':size},scope)['sha256']!=expected:
            raise RuntimeError('Staging cleanup did not preserve the exact saved file')
        dev.call('operation.release',{'id':reconcile},scope)
        if dev.call('files.receive.list',{},scope):raise RuntimeError('Fault fixture retained an unexpected receiver')
        report={'passed':True,'bytes':size,'injected_final_receipt_failure':True,'destination_and_publication_identity_survived_restart':True,
                'fresh_admission_preserved_uncertain_bytes':True,'reconciled_without_file_recreation':True,'old_identity_not_replayed':True,
                'full_hash_verified':True,'staging_cleaned_saved_file_retained':True}
        output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2));passed=True
    except Exception as error:
        output.write_text(json.dumps({'passed':False,'error':str(error)[:1024],'receiver':receiver,'publication':publication,'target_exists':target.exists()})+'\n',encoding='utf-8');raise
    finally:
        if held:kernel.CloseHandle(held)
        if handle:
            try:dev.call('stream.release',{'handle':handle},scope)
            except (OSError,RuntimeError):pass
        if passed:target.unlink()
        # On failure retain this one bounded fixture and its private metadata.

if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError) as error:print(error,file=sys.stderr);raise SystemExit(1)
