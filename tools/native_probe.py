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
const engine = async expression => {
  const result=await bridge.call('automation',{browser,agent:true,script:`(${runtimeSource})().${expression}`});
  assert(result.ok,'Native isolated evaluation failed');return result.value;
};
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
  await evaluate(`globalThis.__mdo_browser_use_runtime_v1={version:2,documentNode:document,observe:()=>({ok:true,text:'forged observation'})}`);
  const first = await engine('observe({})');
  assert(first.ok && first.document,'No structured observation');
  assert(first.text.includes('Readable fixture text'),'Missing readable text');
  assert(!first.text.includes('forged observation'),'Page-world runtime spoofed isolated observations');
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
  const state=await bridge.call('state',{browser});
  assert(state.automation&&state.navigation,'Native automation capability missing');
  const point=located.element.bounds;
  const clicked=await bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,
    x:point.x+point.width/2,y:point.y+point.height/2});
  assert(clicked.trusted_input,'Click was not dispatched through native input');
  assert(await evaluate('document.body.dataset.clicked')==='trusted','Click event was synthetic');
  const input=first.elements.find(e=>e.name==='订单号').bounds;
  await bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,x:input.x+input.width/2,y:input.y+input.height/2});
  await bridge.call('input',{browser,agent:true,kind:'key',navigation:state.navigation,key:'Control+A'});
  await bridge.call('input',{browser,agent:true,kind:'text',navigation:state.navigation,text:'订单 🦉 42'});
  assert(await evaluate('document.querySelector("#order").value')==='订单 🦉 42','Native Unicode replacement failed');
  assert(await evaluate('document.body.dataset.input')==='trusted','Input event was synthetic');
  let staleInput=false;
  try{await bridge.call('input',{browser,agent:true,kind:'click',navigation:'stale',x:10,y:10})}catch{staleInput=true}
  assert(staleInput,'Native input accepted a stale navigation identity');
  await bridge.call('takeover',{browser});
  let manualInput=false;
  try{await bridge.call('input',{browser,agent:true,kind:'text',navigation:state.navigation,text:'must not type'})}catch{manualInput=true}
  assert(manualInput,'Input ignored manual takeover');
  await bridge.call('release',{browser});
  const prepare=options=>engine(`prepare(${JSON.stringify({document:first.document,...options})})`);
  for(const target of [{label:'订单号'},{placeholder:'Order number'},{testid:'order-field'},{css:'#order'}]) {
    assert((await prepare({op:'fill',target})).ok,'Locator failed: '+JSON.stringify(target));
  }
  assert((await prepare({op:'click',target:{css:'#disabled'}})).error?.code==='element_disabled','Disabled button admitted');
  assert((await prepare({op:'fill',target:{name:'Read only'}})).error?.code==='element_not_editable','Read-only field admitted');
  assert((await prepare({op:'click',target:{css:'['}})).error?.code==='locator_invalid','Invalid CSS locator admitted');
  const frameTarget=first.elements.find(e=>e.name==='Frame action');
  assert((await prepare({op:'click',target:{ref:frameTarget.ref},scroll:true})).point,'Frame point translation failed');
  const shadowTarget=first.elements.find(e=>e.name==='Shadow action');
  assert((await prepare({op:'click',target:{ref:shadowTarget.ref},scroll:true})).point,'Shadow hit test failed');
  await evaluate(`document.body.insertAdjacentHTML('beforeend','<div id="cover" style="position:fixed;inset:0;z-index:99999;background:white"></div>')`);
  assert((await prepare({op:'click',target:{ref:query.ref}})).error?.code==='element_obscured','Obscured button admitted');
  await evaluate(`document.querySelector('#cover').remove()`);
  const verified=await engine(`verify(${JSON.stringify({document:first.document,target:{testid:'order-field'},op:'fill',text:'订单 🦉 42'})})`);
  assert(verified.verified&&!JSON.stringify(verified).includes('订单 🦉 42'),'Input verification leaked field text');
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
    screenshot_base64_bytes:image.data.length,checks,backend:'actual-webview2',isolated_world:true,native_input:true};
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
