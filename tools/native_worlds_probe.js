import {createNativeBrowserBridge} from '/js/features/workbench/native-browser.js';
const bridge=createNativeBrowserBridge(),browser='browser-original-root-qa';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));let checks=0,report;
const assert=(value,message)=>{checks++;if(!value)throw Error(message);};
const call=(command,params={})=>bridge.call(command,{browser,...params});
const automation=script=>call('automation',{agent:true,script});
async function ready(url,previous){
  const end=performance.now()+10000;
  while(performance.now()<end){const state=await call('state');if(!state.loading&&state.url===url&&(previous===undefined||state.navigation!==previous))return state;await pause(20);}
  throw Error('The original requested document did not become ready');
}
try{
  const url=location.origin+'/__browser_use_page__.html';
  await call('create',{url,visible:true,bounds:{x:10,y:70,width:900,height:650,scale:devicePixelRatio}});
  await ready(url);
  for(const value of ['',null,false,0,'Native "JSON" \\ \n原生 🦉']){
    const result=await automation(JSON.stringify(value));assert(result.ok&&Object.is(result.value,value),'Unique-world evaluation changed a primitive value');
  }
  const admitted=await call('state');
  assert(admitted.automation_unique_world===true&&!admitted.automation_world_error,'The native root did not qualify unique-context execution: '+JSON.stringify(admitted));
  assert((await automation('globalThis.__mdo_unique_root_probe__="original"')).value==='original','Cannot store original isolated state');
  assert((await call('evaluate',{script:'typeof globalThis.__mdo_unique_root_probe__'})).value==='undefined','Isolated state leaked to the page world');
  assert((await automation('globalThis.__mdo_unique_root_probe__')).value==='original','A stable original root was recreated');
  const foreign=new URL(url);foreign.hostname=foreign.hostname==='127.0.0.1'?'localhost':'127.0.0.1';
  await call('navigate',{url:foreign.href});await ready(foreign.href,admitted.navigation);
  assert((await automation('typeof globalThis.__mdo_unique_root_probe__')).value==='undefined','Original isolated state was rebound after a cross-site navigation');
  const changed=await call('state');assert(changed.automation_unique_world===true&&!changed.automation_world_error,'Replacement root failed native unique-context qualification');
  assert((await automation('globalThis.__mdo_unique_root_probe__="replacement"')).value==='replacement','Explicit new-document execution failed');
  await call('reload');await ready(foreign.href,changed.navigation);
  assert((await automation('typeof globalThis.__mdo_unique_root_probe__')).value==='undefined','Reload revived retired original isolated state');
  const final=await call('state');assert(final.automation_unique_world===true&&!final.automation_world_error,'Reloaded root failed native unique-context qualification');
  report={passed:true,checks,backend:'actual-webview2-root-worlds',native_unique_world:true,cross_site_original_state_retired:true,
    reload_original_state_retired:true,page_world_isolation:true,numeric_reuse_fault_injection:'separate-production-method-gate'};
}catch(error){report={passed:false,checks,error:String(error.stack||error)};try{report.native_state=await call('state');}catch{}}
finally{try{await call('close');}catch{}bridge.dispose();}
const bootstrap=await fetch('/api/v1/bootstrap'),token=bootstrap.headers.get('x-mdo-write-token');await bootstrap.arrayBuffer();
const saved=await fetch('/api/v1/application/call',{method:'POST',headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
  body:JSON.stringify({method:'storage.write',context:{extension:'browser-use-qa'},params:{path:'qa/page-observation.json',text:JSON.stringify(report)}})});
document.querySelector('#status').textContent=saved.ok?JSON.stringify(report):'Cannot save root-world evidence';
