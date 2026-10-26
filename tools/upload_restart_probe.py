"""Crash/restart this same deployment with a real live native upload reservation."""
import json
import ctypes
from ctypes import wintypes
import re
import subprocess
import sys
import time

import dev
from upload_cache_probe import inspect_cache

SCOPE={"extension":"browser-use-qa"}
SAVED="qa/upload-restart.json"
BODY="browser-use-upload-crash-fixture"

SCRIPT=r'''
import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
const bridge=createNativeBrowserBridge(),browser='browser-use-upload-restart';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function save(value){
  const boot=await fetch('/api/v1/bootstrap'),token=boot.headers.get('x-mdo-write-token');await boot.arrayBuffer();
  const response=await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
    body:JSON.stringify({method:'storage.write',context:{extension:'browser-use-qa'},params:{path:'qa/upload-restart.json',text:JSON.stringify(value)}})});
  if(!response.ok)throw Error('Cannot save native upload restart evidence');
}
try{
  await bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html',visible:false});
  let state;for(let i=0;i<100;i++){state=await bridge.call('state',{browser});if(!state.loading)break;await pause(20);}
  if(state.loading||!state.upload_capability)throw Error('Native upload restart fixture not ready');
  const body=BODY_VALUE,bytes=new TextEncoder().encode(body);
  const sha=[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(v=>v.toString(16).padStart(2,'0')).join('');
  const call=params=>bridge.call('upload',{browser,navigation:state.navigation,...params});
  const begin=()=>call({action:'begin',upload_key:'restart-upload',timeout_ms:120000,files:[{name:'crash-fixture.txt',size:bytes.length,sha256:sha}]});
  if(['unexpected','renamed','junction'].includes(PHASE_VALUE)){
    let blocked=false;try{await begin();}catch(error){blocked=error.message.includes('uncollected entries');}
    if(!blocked)throw Error('Unexpected cache data was not preserved as an explicit admission failure');
    await bridge.call('close',{browser});await save({passed:true,phase:'unexpected-preserved'});
  }else{
    const job=await begin();
    await call({action:'chunk',ticket:job.ticket,file:0,offset:0,base64:btoa(body)});
    if(!(await call({action:'seal',ticket:job.ticket})).sealed)throw Error('Native upload restart fixture did not seal');
    if(PHASE_VALUE!=='stage'){
      await call({action:'cancel',ticket:job.ticket});await bridge.call('close',{browser});
      await save({passed:true,phase:'recovered'});
    }else await save({passed:true,phase:'staged',ticket:job.ticket});
  }
}catch(error){await save({passed:false,error:String(error.message||error)});}
'''


def main():
    output=dev.ENV/"upload-restart-result.json"
    output.write_text('{"passed":false,"state":"running"}\n',encoding="utf-8")
    cache=dev.HOME/"data/cache/webview2-content/mdo-browser-use-transfers"
    web=dev.SOURCE/"app/web"
    (web/"__browser_use_page__.html").write_bytes((dev.ROOT/"tests/fixtures/page.html").read_bytes())
    before_diagnostics={p.name:(p.stat().st_size,p.stat().st_mtime_ns) for p in (dev.ENV/"host").glob("crash_*")}
    kernel=ctypes.WinDLL("kernel32",use_last_error=True)
    kernel.CreateFileW.argtypes=[wintypes.LPCWSTR,wintypes.DWORD,wintypes.DWORD,ctypes.c_void_p,wintypes.DWORD,wintypes.DWORD,wintypes.HANDLE]
    kernel.CreateFileW.restype=wintypes.HANDLE;kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    kernel.RemoveDirectoryW.argtypes=[wintypes.LPCWSTR]
    held=None;unexpected=None;renamed=None;junction=None;backup=None
    try:
        for phase in ("stage","live-lease","unexpected","renamed","junction","recover"):
            if not dev.state().get("pid"):dev.start()
            dev.call("storage.write",{"path":SAVED,"text":"null"},SCOPE)
            dev.stop()
            script=SCRIPT.replace("BODY_VALUE",json.dumps(BODY)).replace("PHASE_VALUE",json.dumps(phase))
            (web/"__browser_use_probe__.html").write_text('<!doctype html><meta charset="utf-8"><h1>Native upload recovery</h1><script type="module">'+script+'</script>',encoding="utf-8")
            dev.start(window=True,page="/__browser_use_probe__.html?upload="+phase)
            deadline=time.monotonic()+30;result=None
            while time.monotonic()<deadline:
                try:result=json.loads(dev.call("storage.read",{"path":SAVED},SCOPE)["text"])
                except (OSError,RuntimeError,ValueError):result=None
                if result is not None:break
                time.sleep(0.1)
            if not result or not result.get("passed"):raise RuntimeError("Native upload restart phase failed: "+str(result))
            if phase=="stage":
                if not re.fullmatch(r"u-[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}",result["ticket"]):raise RuntimeError("Invalid private staging identity")
                original=cache/result["ticket"]
                if (original/"0/crash-fixture.txt").read_bytes()!=BODY.encode():raise RuntimeError("Native staging bytes differ before forced termination")
                # Existing dev.stop verifies and force-stops this exact process
                # tree. The page deliberately does not close/cancel its ticket.
                dev.stop()
                if not original.is_dir():raise RuntimeError("The crash fixture did not retain an orphan for recovery")
                lease_deadline=time.monotonic()+3
                while True:
                    held=kernel.CreateFileW(str(original/".lease"),0x80000000,0,None,3,0x00200000,None)
                    if held!=ctypes.c_void_p(-1).value:break
                    held=None;error=ctypes.get_last_error()
                    if error!=32 or time.monotonic()>=lease_deadline:raise ctypes.WinError(error)
                    # Observe this same kernel lease while process handle
                    # teardown drains; do not restart or manufacture a ticket.
                    time.sleep(0.02)
            elif phase=="live-lease":
                if (original/"0/crash-fixture.txt").read_bytes()!=BODY.encode():raise RuntimeError("Collector changed a reservation whose lease was still held")
                kernel.CloseHandle(held);held=None
                unexpected=original/"0/aaa-unexpected-fixture-note.txt";unexpected.write_bytes(b"preserve-unknown-cache-entry")
            elif phase=="unexpected":
                if unexpected.read_bytes()!=b"preserve-unknown-cache-entry":raise RuntimeError("Collector changed an unexpected cache entry")
                if (original/"0/crash-fixture.txt").read_bytes()!=BODY.encode():raise RuntimeError("Collector deleted a known file before validating the whole directory")
                unexpected.unlink();unexpected=None
                renamed=original/"0/renamed-fixture.txt";(original/"0/crash-fixture.txt").rename(renamed)
            elif phase=="renamed":
                if renamed.read_bytes()!=BODY.encode():raise RuntimeError("Collector deleted a single file outside its immutable inventory")
                renamed.rename(original/"0/crash-fixture.txt");renamed=None
                target=dev.ENV/"qa/upload-junction-target";target.mkdir(exist_ok=True)
                marker=target/"crash-fixture.txt";marker.write_bytes(b"outside-cache-junction-fixture")
                backup=dev.ENV/"qa/upload-junction-backup";junction=original/"0"
                if backup.exists():raise RuntimeError("A prior junction fixture needs inspection before reuse")
                if not all(p.resolve().is_relative_to(dev.ENV.resolve()) for p in (backup,junction,target)):raise RuntimeError("Junction fixture left the project deployment")
                junction.rename(backup)
                created=subprocess.run(["cmd.exe","/c","mklink","/J",str(junction),str(target)],capture_output=True)
                if created.returncode or not junction.lstat().st_file_attributes&0x400:raise RuntimeError("Cannot create the real project-local junction fixture")
            elif phase=="junction":
                if marker.read_bytes()!=b"outside-cache-junction-fixture":raise RuntimeError("Collector followed a junction outside its private reservation")
                if not kernel.RemoveDirectoryW(str(junction)):raise ctypes.WinError(ctypes.get_last_error())
                backup.rename(junction);backup=None;junction=None
            else:
                if original.exists():raise RuntimeError("A new native process did not collect the abandoned reservation")
                inspection=inspect_cache(cache)
                if inspection["remaining_reservations"]:raise RuntimeError("Native upload recovery left reservations behind")
        changed=[p.name for p in (dev.ENV/"host").glob("crash_*") if before_diagnostics.get(p.name)!=(p.stat().st_size,p.stat().st_mtime_ns)]
        if changed:raise RuntimeError("Native upload recovery generated exception diagnostics")
        if not dev.call("runtime.info").get("application_root"):raise RuntimeError("Host was unhealthy after recovery")
        report={"passed":True,"backend":"actual-webview2-same-deployment","forced_restart_reclaimed_staging":True,
            "live_lease_preserved":True,"unexpected_entry_preserved":True,"immutable_inventory_enforced":True,"junction_not_followed":True,
            "private_acl":True,"remaining_reservations":0,"new_native_diagnostics":[]}
        output.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8");print(json.dumps(report,indent=2))
    except Exception as error:
        output.write_text(json.dumps({"passed":False,"phase":phase,"error":str(error)[:1024],"winerror":getattr(error,"winerror",None)})+"\n",encoding="utf-8");raise
    finally:
        if held is not None:kernel.CloseHandle(held)
        if unexpected is not None:unexpected.unlink(missing_ok=True)
        if renamed is not None and renamed.exists():renamed.rename(original/"0/crash-fixture.txt")
        if backup is not None and backup.exists():
            if junction.exists() and junction.lstat().st_file_attributes&0x400:kernel.RemoveDirectoryW(str(junction))
            if not junction.exists():backup.rename(junction)
        dev.stop();dev.start()


if __name__=="__main__":
    try:main()
    except (OSError,RuntimeError,ValueError) as error:print(error,file=sys.stderr);raise SystemExit(1)
