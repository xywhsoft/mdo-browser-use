"""Run the page engine in the project's real WebView2, then reuse headless mode."""
import json
from pathlib import Path
import sys
import time

import dev

ROOT = Path(__file__).resolve().parents[1]
REPORT = {"extension": "browser-use-qa"}
REPORT_PATH = "qa/page-observation.json"

SCRIPT = r'''
import { createNativeBrowserBridge } from '/js/features/workbench/native-browser.js';
const runtimeSource = RUNTIME_SOURCE;
const bridge = createNativeBrowserBridge(), browser = 'browser-use-page-probe';
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let checks=0;
const assert = (test, message) => { checks++; if (!test) throw Error(message); };
let report;
async function evaluate(expression) {
  const result = await bridge.call('evaluate', { browser, script: expression });
  assert(result.ok, 'Native evaluation failed');
  return result.value;
}
const engine = expression => evaluate(`(${runtimeSource})().${expression}`);
try {
  await bridge.call('create', { browser, url:location.origin+'/__browser_use_page__.html',
    visible:true, bounds:{ x:10,y:70,width:1000,height:650,scale:devicePixelRatio } });
  let ready = false;
  for (let i=0;i<100;i++) {
    const snapshot = await bridge.call('snapshot', {browser});
    if (snapshot.value?.text?.includes('Browser Use fixture')) {
      const complete = await evaluate('document.readyState');
      if (complete === 'complete') { ready = true; break; }
    }
    await pause(100);
  }
  assert(ready,'Page did not load');
  const first = await engine('observe({})');
  assert(first.ok && first.document,'No structured observation');
  assert(first.text.includes('Readable fixture text'),'Missing readable text');
  assert(!JSON.stringify(first).includes('fixture-private-value'),'Password value was exposed');
  assert(!JSON.stringify(first).includes('Hidden action'),'Hidden action was exposed');
  assert(first.elements.some(e=>e.role==='textbox'&&e.name==='订单号'),'Label-based accessible name missing');
  assert(first.elements.some(e=>e.name==='Shadow action'),'Open shadow root was omitted');
  assert(first.elements.some(e=>e.name==='Frame action'&&e.frame!=='f0'),'Same-origin iframe was omitted');
  assert(first.frames.some(f=>!f.dom),'Opaque frame capability was misreported');
  const query=first.elements.find(e=>e.name==='查询');
  assert(query?.ref,'Missing query reference');
  const located=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:query.ref}})})`);
  assert(located.ok && located.element.bounds.width>0,'Reference lookup failed');
  const ambiguous=await engine(`locate(${JSON.stringify({document:first.document,target:{role:'button',name:'Duplicate'}})})`);
  assert(ambiguous.error?.code==='element_ambiguous','Duplicate locator silently selected an element');
  const limited=await engine('observe({limit:2})');
  assert(limited.truncated && limited.elements.length===2,'Budget truncation was not explicit');
  await evaluate(`document.body.appendChild(document.createElement('p')).textContent='Later text'`);
  const stable=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:query.ref}})})`);
  assert(stable.ok,'Unrelated DOM update invalidated a stable reference');
  const oldFrame=first.elements.find(e=>e.name==='Frame action');
  await evaluate(`document.querySelector('iframe').srcdoc='<button>New frame action</button>'`);
  let changedFrame;
  for(let i=0;i<50;i++) {
    changedFrame=await engine('observe({})');
    if(changedFrame.elements.some(e=>e.name==='New frame action'))break;
    await pause(50);
  }
  assert(changedFrame.elements.some(e=>e.name==='New frame action'),'Frame did not reload');
  const staleFrame=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:oldFrame.ref}})})`);
  assert(['stale_frame','element_detached'].includes(staleFrame.error?.code),'An old iframe document reference was reused');
  assert(changedFrame.document===first.document,'Frame navigation invalidated the whole top document');
  await evaluate(`document.querySelector('#search').remove()`);
  const removed=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:query.ref}})})`);
  assert(removed.error?.code==='element_detached','Detached element reference was reused');
  const image=await bridge.call('screenshot',{browser});
  assert(image.data?.length>1000,'Native screenshot was empty');
  await bridge.call('navigate',{browser,url:location.origin+'/__browser_use_page__.html?next=1'});
  let second;
  for(let i=0;i<100;i++) {
    await pause(100);
    try { second=await engine('observe({})'); if(second.document!==first.document&&second.ready==='complete')break; }
    catch { /* Execution context can disappear during navigation. */ }
  }
  assert(second?.document!==first.document,'Navigation reused the previous document identity');
  const stale=await engine(`locate(${JSON.stringify({document:first.document,target:{ref:query.ref}})})`);
  assert(stale.error?.code==='stale_document','Navigation did not reject an old reference');
  report={passed:true,elements:first.elements.length,frames:first.frames.length,
    screenshot_base64_bytes:image.data.length,checks,backend:'actual-webview2'};
} catch(error) { report={passed:false,error:String(error.stack||error)}; }
finally {
  try { await bridge.call('close',{browser}); } catch {}
  bridge.dispose();
}
try {
  const bootstrap=await fetch('/api/v1/bootstrap'); const token=bootstrap.headers.get('x-mdo-write-token');
  await bootstrap.arrayBuffer();
  const response=await fetch('/api/v1/application/call',{method:'POST',
    headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
    body:JSON.stringify({method:'storage.write',context:{extension:'browser-use-qa'},
      params:{path:'qa/page-observation.json',text:JSON.stringify(report)}})});
  if(!response.ok)throw Error('Cannot save native report');
  document.querySelector('#status').textContent=report.passed?'Native checks passed':report.error;
} catch(error) { document.querySelector('#status').textContent=String(error); }
'''


def main():
    web = dev.SOURCE / "app/web"
    runtime = (ROOT / "page/runtime.js").read_text(encoding="utf-8")
    if runtime.count("export function browserRuntime()") != 1:
        raise RuntimeError("The injectable page entry changed")
    runtime = runtime.replace("export function browserRuntime()", "function browserRuntime()")
    script = SCRIPT.replace("RUNTIME_SOURCE", json.dumps(runtime).replace("<", "\\u003c"))
    (web / "__browser_use_page__.html").write_bytes((ROOT / "tests/fixtures/page.html").read_bytes())
    (web / "__browser_use_probe__.html").write_text(
        '<!doctype html><meta charset="utf-8"><h1 id="status">Browser Use native checks</h1>'
        '<script type="module">' + script + '</script>', encoding="utf-8")
    exclude = dev.SOURCE / ".git/info/exclude"
    text = exclude.read_text(encoding="utf-8")
    if "/app/web/__browser_use_" not in text:
        exclude.write_text(text + "/app/web/__browser_use_*.html\n", encoding="utf-8")
    # A stale result must never make this run appear to pass.
    dev.call("storage.write", {"path": REPORT_PATH, "text": "null"}, REPORT)
    dev.stop()
    result = None
    try:
        dev.start(window=True, page="/__browser_use_probe__.html")
        deadline = time.monotonic() + 70
        while time.monotonic() < deadline:
            try:
                saved = dev.call("storage.read", {"path": REPORT_PATH}, REPORT)
                result = json.loads(saved["text"])
                if result is not None:
                    break
            except (OSError, RuntimeError, ValueError, KeyError):
                pass
            time.sleep(0.2)
        if not result:
            raise RuntimeError("Native page checks did not return a result; inspect .mdo/server.log")
        if not result.get("passed"):
            raise RuntimeError(result.get("error", "Native page checks failed"))
        output = dev.ENV / "page-observation-result.json"
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result, indent=2))
        return 0
    finally:
        dev.stop()
        dev.start()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
