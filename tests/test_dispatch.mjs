import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const source = (await readFile(new URL("../host/automation-dispatch.js", import.meta.url), "utf8"))
  .replace('import { executeDownloadSave, downloadSaveState } from "./download-save.js";',`import {executeDownloadSave,downloadSaveState} from ${JSON.stringify(new URL('../host/download-save.js',import.meta.url).href)};`)
  .replace('import { visualSnapshots, hashVisualRaster } from "./visual-snapshot.js";',`import {visualSnapshots,hashVisualRaster} from ${JSON.stringify(new URL('../host/visual-snapshot.js',import.meta.url).href)};`)
  .replace('import { executeVisualAction } from "./visual-dispatch.js";',`import {executeVisualAction} from ${JSON.stringify(new URL('../host/visual-dispatch.js',import.meta.url).href)};`)
  .replace('import { browserRuntime } from "./automation-runtime.js";', 'function browserRuntime() {}')
  .replace('import { prepareScreenshot } from "./capture-image.js";', 'async function prepareScreenshot(){throw Error("An unapproved image path was reached")}')
  .replace('import { executeBrowserDownload } from "./download-dispatch.js";', 'async function executeBrowserDownload(){throw Error("Unexpected download path")}')
  .replace('import { executeFileUpload } from "./upload-dispatch.js";', 'async function executeFileUpload(){throw Error("Unexpected upload path")}');
const {executeBrowserAutomation:dispatch}=await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const executeBrowserAutomation=options=>dispatch({...options,browser:options.browser||options.view,view:{async execute(command,params){
  if(command==='downloads')return options.downloads?options.downloads(params):{ok:true,armed:true,downloads:[]};
  const result=await options.view.execute(command,params);
  return command==='state'?{download_capability:true,downloads_armed:true,...result}:result;
}}});

const keyboardDoc='d-00000000-0000-4000-8000-000000000001',keyboardFd='fd-00000000-0000-4000-8000-000000000001';
const keyboardPins=[{frame:'f0',document:keyboardFd}],keyboardRef=`r1|f0|${keyboardFd}|${keyboardDoc}|e1`;
function keyboardDriver({prepare=()=>({}),verify=()=>({ok:true,focused:true,verified:true})}={}){
  return{async read(method,options,control){await control.checkpoint();
    if(method==='prepare')return{ok:true,element:{ref:keyboardRef},point:{x:10,y:10},geometry:[10,10,20,20],frame_chain:keyboardPins,
      native_target:{document:keyboardDoc,ref:'e1'},...prepare(options)};
    if(method==='verify')return verify(options);
    return{ok:true,document:options.document,ready:'complete',text:'Fixture'};
  }};
}

function nativeNavigationFixture(mode){
  const calls=[],reference='nv-original';let sent=false,states=0;
  const options={command:'navigate_to',params:{action:'goto',url:'https://fixture.invalid/',timeout_ms:500},action:{id:'native-navigation-'+mode},
    api:{async call(){return{state:'pending'};}},downloads(){return{ok:true,armed:true,downloads:sent&&mode==='download'?[{id:'old',state:'completed'},{id:'new',state:'completed'}]:[{id:'old',state:'completed'}]};},
    view:{async execute(command,params){calls.push([command,params]);
      if(command==='navigate'){sent=true;assert(params.timeout_ms>=100&&params.timeout_ms<=500);return{accepted:true,navigation_ref:reference};}
      if(command==='navigation_cancel')return{cancelled:true};
      if(command==='dialog')return{ok:true,dialogs:[{id:'dialog'}]};
      if(command==='state'){
        if(sent&&mode==='transport')throw Error('Fixture lost transport');
        const native={id:reference,phase:'acknowledged',acknowledged:true,ok:true,dispatched:true,download_known:true,download:false,loader_present:mode==='new-document',page_commit_supported:true,page_committed:true,page_current:true,page_document:'nd-original',same_document_supported:true,same_document:mode==='same-document'};
        if(sent){states++;if(mode==='cancel')native.phase='sent';if(mode==='unknown')native.download_known=false;
          if(mode==='download')native.download=true;
          if(mode==='unsupported')Object.assign(native,{phase:'failed',acknowledged:true,ok:false,dispatched:false,code:'cdp_navigation_unsupported'});
          if(mode==='replaced')native.id='nv-replacement';}
        return{loading:false,automation:true,navigation:sent&&mode==='new-document'?'2':'1',url:'https://fixture.invalid/',
          ...(sent?{native_navigation:native}:{}),manual:sent&&mode==='manual',dialog_open:sent&&mode==='dialog'};
      }
      if(command==='automation')return{ok:true,value:{ok:true,ready:'complete',document:'fixture-document'}};
      throw Error('Unexpected native navigation command '+command);
    }}};
  options.pageDriver={async read(method,params,control){
    assert.equal(method,'observe');await control.checkpoint();
    assert.equal(control.navigation_ref,['new-document','same-document'].includes(mode)?reference:undefined);
    return(await options.view.execute('automation',{script:'fixture-observe'})).value;
  }};
  if(mode==='cancel')options.alive=()=>states===0;
  return{options,calls,reference};
}
test('original initial native failures are inspectable without evaluating or reopening the target',async()=>{
  for(const mode of ['dns','closed','unknown','download-unproven']){
    let reads=0;
    const native={initial:true,id:'nv-original',phase:mode==='unknown'?'acknowledged':'failed',ok:mode==='unknown',acknowledged:mode!=='closed',
      dispatched:true,download_known:false,loader_present:false,code:mode==='download-unproven'?'cdp_navigation_download_unproven':mode==='dns'?'cdp_navigation_failed':'cdp_navigation_connection_closed',native_error:'net::ERR_NAME_NOT_RESOLVED'};
    const result=await executeBrowserAutomation({command:'observe',params:{},action:{id:'initial-'+mode},api:{async call(){return{state:'pending'};}},
      view:{async execute(command){assert.equal(command,'state');reads++;return{loading:false,automation:true,initial_navigation_pending:true,native_navigation:native,error:mode==='dns'?'Legacy page error':''};}}});
    assert.equal(result.result.ok,false);assert.equal(result.result.error.code,mode==='dns'?'navigation_failed':mode==='download-unproven'?'download_identity_unavailable':'navigation_uncertain');
    assert.equal(result.result.error.initial_navigation.effect_may_have_occurred,true);assert.equal(result.result.error.effect_may_have_occurred,false);assert.equal(reads,1);
  }
});
const initialRef='nv-00000000-0000-4000-8000-000000000011';
function initialObservationFixture(mode='ready'){
  const state={polls:0,reads:0,scripts:0,controls:[],commands:[]};
  const options={command:'observe',params:{timeout_ms:700},action:{id:'initial-observe-'+mode},api:{async call(){return{state:mode==='cancelled'&&state.polls>=1?'cancelled':'pending'};}},
    pageDriver:{async read(method,params,control){assert.equal(method,'observe');if(mode==='cancelled')await control.checkpoint();state.reads++;state.controls.push(control.navigation_ref);
      assert(state.polls>=3||['already-ready','ordinary-later'].includes(mode));
      return{ok:true,ready:'complete',document:'target-document',text:mode==='wait-replaced'?'not the requested text':'Target fixture'};}},
    view:{async execute(command,params){state.commands.push(command);
      if(command==='downloads')return{ok:true,armed:true,downloads:mode==='download-retained'?[{id:'download-original',state:'pending'}]:[]};
      if(command==='automation'){state.scripts++;throw Error('Unconstrained initial page script was reached');}
      assert.equal(command,'state');const poll=++state.polls,done=poll>=3||['already-ready','ordinary-later','download-retained','download-missing'].includes(mode);
      const native={initial:true,id:initialRef,phase:done?'acknowledged':'sent',acknowledged:done,ok:done,download_known:done,download:false,
        loader_present:done,page_commit_supported:true,page_committed:done,page_current:done,page_document:done?'nd-original':null};
      if(mode==='invalid-ref')native.id='nv-malformed';
      if(mode==='foreign-failure'&&poll>=2)Object.assign(native,{id:'nv-00000000-0000-4000-8000-000000000012',phase:'failed',acknowledged:true,ok:false,code:'cdp_navigation_failed',native_error:'net::ERR_NAME_NOT_RESOLVED'});
      if(mode==='replaced-waiting'&&poll>=2||mode==='replaced-reading'&&state.reads||mode==='wait-replaced'&&state.reads)native.id='nv-00000000-0000-4000-8000-000000000012';
      if(mode==='missing-record'&&state.reads)return{loading:false,automation:true,navigation:'2'};
      if(mode==='revoked-reading'&&state.reads)native.scope_revoked=true;
      if(mode==='alias-reading'&&state.reads)native.page_document='nd-new-proof';
      if(mode==='proof-reading'&&state.reads||mode==='ordinary-later')native.page_current=false;
      if(mode==='unproven')native.page_committed=false;
      if(mode==='unsupported-proof')native.page_commit_supported=false;
      if(mode==='download-retained'||mode==='download-missing')Object.assign(native,{phase:'acknowledged',acknowledged:true,ok:true,download_known:true,download:true,loader_present:false,page_current:false,page_committed:false,page_document:null});
      return{loading:false,automation:true,navigation:mode==='ui-reading'&&state.reads?'3':done?'2':'1',initial_navigation_pending:!done,
        native_navigation:native,manual:mode==='manual'&&poll>=2};
    }}};
  options.downloads=()=>({ok:true,armed:true,downloads:mode==='download-retained'?[{id:'download-original',state:'pending'}]:[]});
  if(mode==='no-driver')delete options.pageDriver;
  if(mode==='wait-replaced'){options.command='wait_for';options.params.until='text';options.params.text='Unseen requested target';}
  return{state,options};
}

test('initial target observation retains its original native proof through readiness and page reading',async()=>{
  for(const mode of ['ready','already-ready']){
    const f=initialObservationFixture(mode),out=await executeBrowserAutomation(f.options);
    assert.equal(out.result.document,'target-document');assert.equal(f.state.reads,1);assert.deepEqual(f.state.controls,[initialRef]);assert.equal(f.state.scripts,0);
  }
});
test('initial observation discards changed or unproven original records without adopting another target',async()=>{
  for(const [mode,code] of [['foreign-failure','context_changed'],['replaced-waiting','context_changed'],['replaced-reading','context_changed'],['missing-record','context_changed'],
      ['revoked-reading','context_changed'],['alias-reading','stale_document'],['proof-reading','stale_document'],['ui-reading','stale_document'],
      ['unproven','navigation_unproven'],['unsupported-proof','navigation_unproven'],['invalid-ref','navigation_uncertain']]){
    const f=initialObservationFixture(mode),out=await executeBrowserAutomation(f.options);assert.equal(out.result.ok,false,mode);assert.equal(out.result.error.code,code,mode);
    assert.equal(out.result.error.effect_may_have_occurred,false,mode);assert.equal(out.result.document,undefined);assert.equal(out.result.text,undefined);
    assert.equal(f.state.scripts,0);assert(f.state.reads<=1);assert(!f.state.commands.includes('navigation_cancel'));
    if(mode==='foreign-failure')assert.equal(out.result.error.native_error,undefined);
  }
});
test('initial observation respects cancellation and manual control without cancelling the original create operation',async()=>{
  for(const [mode,code] of [['cancelled','cancelled'],['manual','manual_control']]){
    const f=initialObservationFixture(mode),out=await executeBrowserAutomation(f.options);assert.equal(out.result.error.code,code);
    assert.equal(f.state.reads,0);assert.equal(f.state.scripts,0);assert(!f.state.commands.includes('navigation_cancel'));
  }
});
test('initial observation cannot fall back to an unconstrained legacy page script',async()=>{
  const f=initialObservationFixture('no-driver'),out=await executeBrowserAutomation(f.options);
  assert.equal(out.result.error.code,'unsupported_capability');assert.equal(f.state.scripts,0);
});
test('a fresh observation after an ordinary later navigation is not bound to the old initial page',async()=>{
  const f=initialObservationFixture('ordinary-later'),out=await executeBrowserAutomation(f.options);
  assert.equal(out.result.document,'target-document');assert.deepEqual(f.state.controls,[undefined]);
});
test('waiting for initial page text never switches to another original navigation',async()=>{
  const f=initialObservationFixture('wait-replaced'),out=await executeBrowserAutomation(f.options);
  assert.equal(out.result.error.code,'context_changed');assert.equal(f.state.reads,1);assert.equal(out.result.observation,undefined);
});
test('initial download acknowledgement is never observed as a blank committed page',async()=>{
  for(const mode of ['download-retained','download-missing']){
    const f=initialObservationFixture(mode),out=await executeBrowserAutomation(f.options);assert.equal(f.state.reads,0);assert.equal(f.state.scripts,0);
    if(mode==='download-retained'){assert.equal(out.result.outcome,'download_pending');assert.equal(out.result.navigation_ref,initialRef);assert.equal(out.result.source_association,'browser_scope');}
    else assert.equal(out.result.error.code,'download_identity_unavailable');
  }
});

test('original native navigation ACK distinguishes same-document and committed page completion',async()=>{
  for(const mode of ['same-document','new-document']){
    const fixture=nativeNavigationFixture(mode),result=await executeBrowserAutomation(fixture.options);
    assert.equal(result.result.ok,true);assert.equal(Boolean(result.result.same_document),mode==='same-document');
    assert.equal(fixture.calls.filter(([command])=>command==='navigate').length,1);
    assert(!fixture.calls.some(([command])=>command==='navigation_cancel'));
  }
});
test('native cross-document navigation waits for its loader match and rechecks the same page after reading',async()=>{
  for(const mode of ['delayed','replaced','during-read','alias-changed','old-host','bad-alias','timeout','source-revoked']){
    const fixture=nativeNavigationFixture('new-document'),execute=fixture.options.view.execute;let polls=0,read=false;
    fixture.options.view.execute=async(command,params)=>{
      const result=await execute(command,params);
      if(command==='automation')read=true;
      if(command==='state'&&result.native_navigation){const native=result.native_navigation;polls++;
        if(mode==='delayed')native.page_committed=polls>=3;
        if(mode==='replaced'||mode==='during-read'&&read)native.page_current=false;
        if(mode==='alias-changed'&&read)native.page_document='nd-new-document';
        if(mode==='old-host')delete native.page_commit_supported;
        if(mode==='bad-alias')native.page_document='raw invalid frame ID';
        if(mode==='timeout')native.page_error='cdp_navigation_commit_timeout';
        if(mode==='source-revoked')native.page_error='cdp_navigation_commit_revoked';
      }return result;
    };
    const result=await executeBrowserAutomation(fixture.options);
    assert.equal(result.result.ok,mode==='delayed',mode);
    if(mode==='delayed')assert(polls>=3&&read);else assert.equal(result.result.error.code,['replaced','during-read','alias-changed','source-revoked'].includes(mode)?'stale_document':'navigation_uncertain',mode);
    assert.equal(fixture.calls.filter(([command])=>command==='navigate').length,1);
    assert(!fixture.calls.some(([command])=>command==='reload'||command==='stop'));
    if(['old-host','bad-alias','replaced','timeout'].includes(mode))assert.equal(read,false);
  }
});
test('native download ACK returns only newly retained browser-scope records',async()=>{
  const fixture=nativeNavigationFixture('download'),result=await executeBrowserAutomation(fixture.options);
  assert.equal(result.result.outcome,'download_pending');assert.equal(result.result.navigation_ref,fixture.reference);
  assert.equal(result.result.source_association,'browser_scope');assert.deepEqual(result.result.downloads.map(row=>row.id),['new']);
  assert(!fixture.calls.some(([command])=>command==='automation'||command==='navigation_cancel'));
});
test('native unsent rejection preserves definite no-effect receipt',async()=>{
  const fixture=nativeNavigationFixture('unsupported'),result=await executeBrowserAutomation(fixture.options);
  assert.equal(result.result.error.native_code,'cdp_navigation_unsupported');assert.equal(result.result.error.effect_may_have_occurred,false);
  assert(!fixture.calls.some(([command])=>command==='navigation_cancel'));
});
test('lost original scope or ambiguous provider completion cancels only its original queue',async()=>{
  for(const mode of ['unknown','replaced','cancel','manual','transport']){
    const fixture=nativeNavigationFixture(mode),result=await executeBrowserAutomation(fixture.options);
    assert.equal(result.result.ok,false,mode);assert.equal(result.result.error.effect_may_have_occurred,true,mode);
    const cleanup=fixture.calls.filter(([command])=>command==='navigation_cancel');assert.equal(cleanup.length,1,mode);
    assert.deepEqual(cleanup[0][1],{agent:true,navigation_ref:fixture.reference});
    assert.equal(fixture.calls.filter(([command])=>command==='navigate').length,1);
    assert(!fixture.calls.some(([command])=>command==='stop'||command==='reload'));
  }
});
test('native dialog checkpoint keeps original pending navigation available for explicit response',async()=>{
  const fixture=nativeNavigationFixture('dialog'),result=await executeBrowserAutomation(fixture.options);
  assert.equal(result.result.outcome,'dialog_open');assert.equal(result.result.completed,false);
  assert(!fixture.calls.some(([command])=>command==='navigation_cancel'));
});

test('interception is acknowledged before input and a late picker event is a checkpoint',async()=>{
  let armed=false,open=false;const commands=[];
  const result=await executeBrowserAutomation({command:'act',params:{op:'click',document:'d1',target:{ref:'e1'}},action:{id:'late-picker'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
      commands.push(command+(params?.action?':'+params.action:''));
      if(command==='state')return{loading:false,automation:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:armed,chooser_open:open};
      if(command==='chooser'){if(params.action==='arm'){armed=true;return{ok:true,armed:true};}return{ok:true,choosers:[{id:'pending-picker'}]};}
      if(command==='input'){assert(armed);return{accepted:true,trusted_input:true};}
      if(params.script.includes('.observe(')){open=true;return{ok:true,value:{ok:true,document:'d1',ready:'complete'}};}
      return{ok:true,value:{ok:true,element:{ref:'e1'},point:{x:10,y:10},geometry:[10,10,20,20]}};
    }}});
  assert.equal(result.result.outcome,'file_chooser_open');assert.equal(result.result.completed,false);assert.equal(result.result.dispatched,true);
  assert.equal(result.result.choosers[0].id,'pending-picker');assert(commands.indexOf('chooser:arm')<commands.indexOf('input'));
  assert.equal(commands.filter(x=>x==='input').length,1);
});
test('pending pickers stop another action but remain observable with a fresh document',async()=>{
  let input=0;
  const base={params:{},action:{id:'pending-picker'},api:{async call(){return{state:'pending'};}},view:{async execute(command){
    if(command==='state')return{loading:false,automation:true,chooser_open:true};
    if(command==='chooser')return{ok:true,choosers:[{id:'first-picker'}]};
    if(command==='automation')return{ok:true,value:{ok:true,document:'fresh-doc',ready:'complete'}};
    input++;throw Error('A pending picker allowed another input');
  }}};
  const checkpoint=await executeBrowserAutomation({...base,command:'act'});assert.equal(checkpoint.result.outcome,'file_chooser_open');assert.equal(checkpoint.result.dispatched,false);
  const observed=await executeBrowserAutomation({...base,command:'observe'});assert.equal(observed.result.document,'fresh-doc');assert.equal(observed.result.choosers[0].id,'first-picker');assert.equal(input,0);
});
test('picker listing is read-only during takeover while Agent cancellation is blocked',async()=>{
  let mutations=0;const base={action:{id:'manual-picker'},api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
    if(command==='state')return{manual:true,chooser_capability:true};if(params.action!=='list')mutations++;return{ok:true,choosers:[]};
  }}};
  assert.deepEqual((await executeBrowserAutomation({...base,command:'chooser',params:{action:'list'}})).result.choosers,[]);
  assert.equal((await executeBrowserAutomation({...base,command:'chooser',params:{action:'cancel',chooser:'pending'}})).result.error.code,'manual_control');assert.equal(mutations,0);
});
test('unsupported interception refuses Agent input before page preparation',async()=>{
  let evaluations=0;const result=await executeBrowserAutomation({command:'act',params:{op:'click'},action:{id:'unsafe-host'},api:{async call(){return{state:'pending'};}},view:{async execute(command){
    if(command==='state')return{loading:false,automation:true,navigation:'1',chooser_capability:false};evaluations++;throw Error('Unsafe host received input');
  }}});assert.equal(result.result.error.code,'unsupported_capability');assert.equal(evaluations,0);
});

test('an existing Android tab refuses automation without page evaluation or input',async()=>{
  for(const reason of ['isolated_world_unsupported','android_adapter_incomplete']){
    const result=await executeBrowserAutomation({command:'act',params:{op:'click',target:{ref:'e1'}},action:{id:'android-existing'},
      api:{async call(){return{state:'pending'};}},view:{async execute(command){
        assert.equal(command,'state');return{loading:false,automation:false,browser_use:{available:false,reason}};
      }}});
    assert.equal(result.result.error.code,'unsupported_capability');assert.equal(result.result.error.effect_may_have_occurred,false);
    assert.match(result.result.error.message,/repeating the tool/);
  }
});
test('a modal checkpoint is resolved before cancelling a still-pending picker',async()=>{
  let cancelled=0;const result=await executeBrowserAutomation({command:'chooser',params:{action:'cancel',chooser:'picker'},action:{id:'modal-picker'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command){
      if(command==='state')return{chooser_capability:true,dialog_open:true};
      if(command==='dialog')return{ok:true,dialogs:[{id:'modal'}]};cancelled++;throw Error('Picker cancellation reached a modal page');
    }}});
  assert.equal(result.result.outcome,'dialog_open');assert.equal(result.result.completed,false);assert.equal(cancelled,0);
});

async function observe(state, receipt = "pending", timeout_ms = 50) {
  let evaluations = 0;
  const result = await executeBrowserAutomation({
    command: "observe", params: { timeout_ms }, action: { id: "fixture" },
    api: { async call() { return { state: receipt }; } },
    view: { async execute(command) {
      if (command === "state") return state;
      evaluations++;
      return { ok: true, value: { ok: true, ready: "complete", text: "fixture" } };
    } }
  });
  return { ...result, evaluations };
}

test('fill rechecks focus after selection before sending replacement text',async()=>{
  let focused=true;const inputs=[];
  const result=await executeBrowserAutomation({command:'act',params:{op:'fill',document:'d1',target:{name:'Fixture'},text:'synthetic'},action:{id:'selection-focus'},api:{async call(){return{state:'pending'};}},
    pageDriver:keyboardDriver({verify:()=>({ok:true,focused,verified:true})}),
    view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,frame_target_keyboard:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='input'){inputs.push(params);if(params.kind==='key')focused=false;return{accepted:true,trusted_input:true,target_focused:params.kind==='focus'};}
      throw Error('Unexpected legacy evaluation');
    }}});
  assert.equal(result.result.error.code,'focus_changed');assert.equal(result.result.error.effect_may_have_occurred,true);
  assert.deepEqual(inputs.map(row=>row.kind),['focus','key']);
});

test('frame driver target pins reach every focus/key/text step without a global image snapshot',async()=>{
  const pins=[{frame:'f1',document:'fd1'},{frame:'f0',document:'fd0'}],inputs=[];
  const pageDriver={async read(method,options,control){await control.checkpoint();
    if(method==='prepare')return{ok:true,element:{ref:`r1|f1|fd1|${keyboardDoc}|e1`},point:{x:15,y:25},geometry:[15,25,10,20],frame_chain:pins,native_target:{document:keyboardDoc,ref:'e1'}};
    if(method==='verify')return{ok:true,focused:true,verified:true};return{ok:true,document:'doc',ready:'complete',text:'Fixture'};
  }};
  const result=await executeBrowserAutomation({command:'act',params:{op:'fill',document:'doc',target:{name:'Fixture'},text:'synthetic'},pageDriver,action:{id:'frame-pin-forwarding'},api:{async call(){return{state:'pending'};}},
    view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,frame_target_keyboard:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='input'){inputs.push(params);return{accepted:true,trusted_input:true,target_focused:params.kind==='focus'};}throw Error('Unexpected legacy page evaluation');
    }}});
  assert(result.result.ok);assert.deepEqual(inputs.map(row=>row.kind),['focus','key','text']);assert(inputs.every(row=>JSON.stringify(row.frame_chain)===JSON.stringify(pins)));
  assert(inputs.every(row=>row.focus_document===keyboardDoc&&row.focus_ref==='e1'&&!Object.hasOwn(row,'frame_snapshot')));
});

test('keyboard capabilities and isolated target proof are required before any focus effect',async()=>{
  for(const mode of ['old-host','no-driver','wrong-ref','wrong-document','missing-chain']){
    const inputs=[];let reads=0;const driver=keyboardDriver({prepare:()=>{reads++;return mode==='wrong-ref'?{native_target:{document:keyboardDoc,ref:'e9'}}:
      mode==='wrong-document'?{native_target:{document:'d-replaced',ref:'e1'}}:mode==='missing-chain'?{frame_chain:[]}:{};}});
    const result=await executeBrowserAutomation({command:'act',params:{op:'key',document:'doc',target:{name:'Fixture'},key:'Enter'},pageDriver:mode==='no-driver'?null:driver,
      action:{id:'keyboard-proof-'+mode},api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
        if(command==='state')return{loading:false,automation:true,frame_target_keyboard:mode!=='old-host',navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
        inputs.push(params);throw Error('Unproven keyboard target received native work');
      }}});
    assert.equal(result.result.ok,false,mode);assert.equal(result.result.error.effect_may_have_occurred,false,mode);assert.equal(inputs.length,0,mode);
    if(['old-host','no-driver'].includes(mode)){assert.equal(result.result.error.code,'unsupported_capability');assert.equal(reads,0);}
    else assert.equal(result.result.error.code,'frame_response_invalid');
  }
});

test('focus alone cannot turn a key action into a click or skip its exact target verification',async()=>{
  const inputs=[];let focused=false;
  const result=await executeBrowserAutomation({command:'act',params:{op:'key',document:'doc',target:{name:'Button'},key:'Enter'},
    pageDriver:keyboardDriver({verify:()=>({ok:true,focused,verified:true})}),action:{id:'keyboard-focus-loss'},api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,frame_target_keyboard:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='input'){inputs.push(params);return{accepted:true,target_focused:true};}throw Error('Unexpected native read');
    }}});
  assert.equal(result.result.error.code,'focus_changed');assert.equal(result.result.error.effect_may_have_occurred,true);
  assert.deepEqual(inputs.map(row=>row.kind),['focus']);
});

test('focus-opened dialogs stop the original key without a synthetic activation',async()=>{
  const inputs=[];const result=await executeBrowserAutomation({command:'act',params:{op:'key',document:'doc',target:{name:'Button'},key:'Enter'},pageDriver:keyboardDriver(),
    action:{id:'keyboard-focus-dialog'},api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,frame_target_keyboard:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='input'){inputs.push(params);return{accepted:true,dialog_open:true};}
      if(command==='dialog')return{dialogs:[{id:'focus-dialog'}]};throw Error('Unexpected native work');
    }}});
  assert.equal(result.result.outcome,'dialog_open');assert.equal(result.result.completed,false);assert.deepEqual(inputs.map(row=>row.kind),['focus']);
});

test('a lost focus reply waits for its original response download without another focus or text',async()=>{
  const inputs=[];let navigation='1';
  const result=await dispatch({command:'act',browser:'focus-original-response',params:{op:'fill',document:'doc',target:{name:'Field'},text:'synthetic'},pageDriver:keyboardDriver(),
    action:{id:'focus-original-response'},api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,frame_target_keyboard:true,navigation,download_capability:true,downloads_armed:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='downloads')return{ok:true,downloads:navigation==='2'?[{id:'original-response',state:'pending'}]:[]};
      if(command==='input'){inputs.push(params);navigation='2';throw Error('Lost original focus acknowledgement');}
      throw Error('Unexpected native work');
    }}});
  assert.equal(result.result.outcome,'download_pending');assert.equal(result.result.completed,false);assert.deepEqual(inputs.map(row=>row.kind),['focus']);
});

test('native creation is awaited with state reads only, then the original browser is observed',async()=>{
  const commands=[];let reads=0;
  const result=await executeBrowserAutomation({command:'observe',params:{timeout_ms:350},action:{id:'native-create-wait'},
    api:{async call(method){assert.equal(method,'ui.result');return{state:'pending'};}},view:{async execute(command){
      commands.push(command);
      if(command==='state')return ++reads<3?{creating:true,loading:true,automation:false,creation:{phase:'controller',elapsed_ms:1}}:
        {creating:false,loading:false,automation:true,navigation:'original'};
      assert.equal(command,'automation');return{ok:true,value:{ok:true,ready:'complete',text:'Original fixture'}};
    }}});
  assert.equal(result.result.ok,true);assert.equal(result.result.text,'Original fixture');assert(reads>=3);
  assert.equal(commands.filter(command=>command==='automation').length,1);
  assert(commands.every(command=>command==='state'||command==='automation'));
});

test('native creation wait honors timeout, lost context, cancellation and manual takeover without page execution',async()=>{
  for(const mode of ['timeout','context','cancel','manual']){
    let reads=0,receipts=0,alive=true;
    const result=await executeBrowserAutomation({command:'observe',params:{timeout_ms:mode==='cancel'?500:70},action:{id:'create-wait-'+mode},alive:()=>alive,
      api:{async call(){receipts++;return{state:mode==='cancel'&&receipts>1?'cancelled':'pending'};}},view:{async execute(command){
        assert.equal(command,'state');reads++;if(mode==='context'&&reads===2)alive=false;
        return{creating:true,loading:true,automation:false,manual:mode==='manual'&&reads>1,creation:{phase:'environment',elapsed_ms:reads}};
      }}});
    assert.equal(result.result.error.code,{timeout:'timed_out',context:'context_changed',cancel:'cancelled',manual:'manual_control'}[mode]);
    assert.equal(result.result.error.effect_may_have_occurred,false);assert(reads>0);
  }
});

test('a late ready-state reply cannot cause page execution after the original action deadline',async()=>{
  let evaluations=0;
  const result=await executeBrowserAutomation({command:'observe',params:{timeout_ms:15},action:{id:'late-ready-state'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command){
      if(command==='state'){await new Promise(resolve=>setTimeout(resolve,30));return{loading:false,automation:true,navigation:'1'};}
      evaluations++;throw Error('Expired state reply allowed page execution');
    }}});
  assert.equal(result.result.error.code,'timed_out');assert.equal(result.result.error.effect_may_have_occurred,false);assert.equal(evaluations,0);
});

test('native recreation after dispatched input stops instead of waiting or sending another effect',async()=>{
  let creating=false;const inputs=[];
  const result=await executeBrowserAutomation({command:'act',params:{op:'fill',document:'d1',target:{name:'Fixture'},text:'synthetic'},
    action:{id:'recreated-after-focus'},api:{async call(){return{state:'pending'};}},pageDriver:keyboardDriver(),
    view:{async execute(command,params){
      if(command==='state')return creating?{creating:true,loading:true,automation:false,creation:{phase:'controller',elapsed_ms:1}}:
        {loading:false,automation:true,frame_target_keyboard:true,navigation:'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='input'){inputs.push(params.kind);creating=true;return{accepted:true,trusted_input:true,target_focused:true};}
      throw Error('Unexpected page execution');
    }}});
  assert.equal(result.result.error.code,'stale_document');assert.equal(result.result.error.effect_may_have_occurred,true);assert.deepEqual(inputs,['focus']);
});

test("observe waits for readiness and reports bounded timeout", async () => {
  const result = await observe({ loading: true, automation: true });
  assert.equal(result.result.error.code, "timed_out");
  assert.equal(result.evaluations, 0);
  assert.equal((await observe({ loading: false, automation: true })).result.text, "fixture");
  const initial=await observe({loading:false,automation:true,initial_navigation_pending:true});
  assert.equal(initial.result.error.code,"timed_out");assert.equal(initial.evaluations,0);
});

test("manual takeover, cancellation and stopped renderer prevent page evaluation", async () => {
  for (const [state, receipt, code] of [
    [{ loading: false, manual: true }, "pending", "manual_control"],
    [{ loading: false }, "cancelled", "cancelled"],
    [{ loading: false, process_failed: true }, "pending", "browser_unavailable"]
  ]) {
    const result = await observe(state, receipt);
    assert.equal(result.result.error.code, code);
    assert.equal(result.evaluations, 0);
  }
});

function actionFixture() {
  let cancelled = false, moving = false, focus = true, samples = 0;
  const inputs = [];
  const view = { async execute(command, params) {
    if (command === "state") return { loading: false, automation: true, frame_target_keyboard:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true, navigation: "1" };
    if (command === "input") { inputs.push(params); cancelled = true; return { accepted: true, trusted_input: true,target_focused:params.kind==='focus' }; }
    if (params.script.includes(".prepare(")) {
      samples++;
      return { ok: true, value: { ok: true, element: { ref: "e1" }, point: { x: 10, y: 10 },
        geometry: [moving ? samples * 10 : 10, 10, 20, 20, 10, 10] } };
    }
    return { ok: true, value: { ok: true, focused: focus, verified: true } };
  } };
  return { view, inputs, setMoving() { moving = true; }, setBlurred() { focus = false; },
    execute: () => executeBrowserAutomation({ command: "act", params: { op: "fill", document: "d1", target: { ref: "e1" }, text: "fixture", timeout_ms: 250 },
      pageDriver:keyboardDriver({prepare:()=>({geometry:[moving?++samples*10:10,10,20,20,10,10]}),verify:()=>({ok:true,focused:focus,verified:true})}),
      view, action: { id: "action-fixture" }, api: { async call() { return { state: cancelled ? "cancelled" : "pending" }; } } }) };
}

test("cancellation after focus prevents subsequent keyboard input", async () => {
  const fixture = actionFixture();
  const result = await fixture.execute();
  assert.equal(result.result.error.code, "cancelled");
  assert.equal(result.result.error.effect_may_have_occurred, true);
  assert.deepEqual(fixture.inputs.map(input => input.kind), ["focus"]);
});

test("moving targets time out without input and concurrent writers are refused", async () => {
  const fixture = actionFixture(); fixture.setMoving();
  const first = fixture.execute();
  const second = await fixture.execute();
  assert.equal(second.result.error.code, "browser_busy");
  assert.equal((await first).result.error.code, "timed_out");
  assert.equal(fixture.inputs.length, 0);
});

test("changing the displaying device rejects work before the native bridge", async () => {
  const result = await executeBrowserAutomation({ command: "act", params: {}, action: { id: "old-device" },
    view: { execute() { throw Error("Native bridge must not be reached"); } },
    api: { call() { throw Error("Wrong device must not be contacted"); } }, alive: () => false });
  assert.equal(result.result.error.code, "context_changed");
  assert.equal(result.result.error.effect_may_have_occurred, false);
});

test("a pending dialog is a normal checkpoint and manual control protects responses", async () => {
  let responses = 0;
  const view = { async execute(command) {
    if (command === "state") return { loading: false, automation: true, dialog_capability: true, dialog_open: true, manual: false };
    if (command === "automation") throw Error("A modal page must not be evaluated");
    responses++; return { ok: true, dialogs: [{ id: "pending-dialog", kind: "confirm" }] };
  }};
  const base = { params: {}, view, action: { id: "dialog-boundary" }, api: { async call() { return { state: "pending" }; } } };
  const checkpoint = await executeBrowserAutomation({ ...base, command: "observe" });
  assert.equal(checkpoint.result.outcome, "dialog_open");
  assert.equal(checkpoint.result.completed, false);
  view.execute = async command => {
    if (command === "state") return { manual: true, dialog_capability: true };
    responses++; return { ok: true, dialogs: [{ id: "pending-dialog" }] };
  };
  assert.equal((await executeBrowserAutomation({ ...base, command: "dialog", params: { action: "list" } })).result.dialogs.length, 1);
  const before = responses;
  const blocked = await executeBrowserAutomation({ ...base, command: "dialog", params: { action: "respond" } });
  assert.equal(blocked.result.error.code, "manual_control");
  assert.equal(responses, before);
});

test("a dialog opening during isolated reading yields without retrying the script", async () => {
  let evaluations = 0;
  const result = await executeBrowserAutomation({ command: "observe", params: {}, action: { id: "read-dialog" },
    api: { async call() { return { state: "pending" }; } }, view: { async execute(command) {
      if (command === "state") return { loading: false, automation: true };
      if (command === "automation") { evaluations++; return { ok: true, value: { ok: true, blocked_by_dialog: true } }; }
      return { ok: true, dialogs: [{ id: "read-race-dialog" }] };
    } } });
  assert.equal(result.result.outcome, "dialog_open");
  assert.equal(evaluations, 1);
});

test("new-window boundaries stop page reading without retry or an ordinary tool error", async () => {
  const pending = await observe({ loading: false, automation: true, popup_open: true });
  assert.equal(pending.result.outcome, "popup_open");
  assert.equal(pending.result.completed, false); assert.equal(pending.evaluations, 0);
  let evaluations = 0;
  const raced = await executeBrowserAutomation({ command: "observe", params: {}, action: { id: "read-popup" },
    api: { async call() { return { state: "pending" }; } }, view: { async execute(command) {
      if (command === "state") return { loading: false, automation: true };
      evaluations++; return { ok: true, value: { ok: true, blocked_by_popup: true } };
    } } });
  assert.equal(raced.result.outcome, "popup_open"); assert.equal(evaluations, 1);
});

test("unsupported screenshots do not evaluate a page or return an image",async()=>{
  let evaluations=0;
  const result=await executeBrowserAutomation({command:"capture",params:{},action:{id:"unsupported-capture"},
    api:{async call(){return{state:"pending"};}},view:{async execute(command){
      if(command==="state")return{loading:false,automation:true,capture_capability:false};
      evaluations++;throw Error("Unsupported capture must not touch page content");
    }}});
  assert.equal(result.result.error.code,"unsupported_capability");assert.equal(evaluations,0);assert.equal(result.result.image,undefined);
});

test("unsupported mouse gestures fail before scrolling, input or page evaluation",async()=>{
  for(const op of ["double_click","right_click","middle_click"]){
    let evaluated=0;
    const result=await executeBrowserAutomation({command:"act",params:{op,document:"d1",target:{ref:"e1"}},action:{id:`unsupported-${op}`},
      api:{async call(){return{state:"pending"};}},view:{async execute(command){
        if(command==="state")return{loading:false,automation:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1"};
        evaluated++;throw Error("Unsupported mouse input reached the page");
      }}});
    assert.equal(result.result.error.code,"unsupported_capability");assert.equal(evaluated,0);
  }
});

test("mouse gestures deliver one typed sequence to the pinned fresh element",async()=>{
  for(const [op,button,click_count] of [["double_click","left",2],["right_click","right",1],["middle_click","middle",1]]){
    const inputs=[];
    const view={async execute(command,params){
      if(command==="state")return{loading:false,automation:true,advanced_mouse:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1"};
      if(command==="input"){inputs.push(params);return{accepted:true,trusted_input:true};}
      if(params.script.includes(".prepare("))return{ok:true,value:{ok:true,element:{ref:"fresh"},point:{x:20,y:30},geometry:[0,0,40,60,20,30]}};
      return{ok:true,value:{ok:true,text:"after gesture"}};
    }};
    const result=await executeBrowserAutomation({command:"act",params:{op,document:"d1",target:{ref:"fresh"}},view,action:{id:op},
      api:{async call(){return{state:"pending"};}}});
    assert.equal(result.result.verification,"native_delivery");assert.equal(inputs.length,1);
    assert.equal(inputs[0].button,button);assert.equal(inputs[0].click_count,click_count);
    assert.equal(inputs[0].navigation,"1");assert.equal(inputs[0].agent,true);
  }
});

test("selection uses one controlled transaction with fresh document, guard and pinned target",async()=>{
  let commits=0,inputs=0;
  const view={async execute(command,params){
    if(command==="state")return{loading:false,automation:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1"};
    if(command==="input"){inputs++;throw Error("Select must not click through intermediate choices");}
    if(params.script.includes(".prepare("))return{ok:true,value:{ok:true,element:{ref:"select-ref"},point:{x:20,y:30},geometry:[0,0,40,60,20,30]}};
    if(params.script.includes(".selectOptions(")){
      commits++;assert.ok(params.script.includes('"target":{"ref":"select-ref"}'));
      assert.ok(params.script.includes('"choices":[{"label":"Express"}]'));
      return{ok:true,value:{ok:true,changed:true,dispatched:true,verified:true,input_method:"dom_select",events_trusted:false}};
    }
    return{ok:true,value:{ok:true,text:"after selection"}};
  }};
  const result=await executeBrowserAutomation({command:"act",params:{op:"select",document:"d1",target:{name:"Delivery"},choices:[{label:"Express"}]},
    view,action:{id:"select-transaction"},api:{async call(){return{state:"pending"};}}});
  assert.equal(commits,1);assert.equal(inputs,0);assert.equal(result.result.verification,"field_state");
  assert.equal(result.result.events_trusted,false);assert.equal(result.result.input_method,"dom_select");
});

test("options observation is read-only and bypasses ordinary page text collection",async()=>{
  let calls=0;
  const result=await executeBrowserAutomation({command:"observe",params:{mode:"options",document:"d1",target:{ref:"select-ref"},offset:2},
    action:{id:"options-list"},api:{async call(){return{state:"pending"};}},view:{async execute(command,params){
      if(command==="state")return{loading:false,automation:true};
      assert.equal(command,"automation");assert.ok(params.script.includes(".optionList("));
      assert.ok(params.script.includes('"offset":2'));calls++;return{ok:true,value:{ok:true,options:[{index:2,label:"Express",disabled:false}]}};
    }}});
  assert.equal(result.result.options[0].label,"Express");assert.equal(calls,1);
});

test("cancellation or user takeover before selection prevents its transaction",async()=>{
  for(const boundary of ["cancelled","manual_control"]){
    let prepared=0,committed=0;
    const result=await executeBrowserAutomation({command:"act",params:{op:"select",document:"d1",target:{ref:"select"},choices:[{index:0}]},
      action:{id:boundary},api:{async call(){return{state:boundary==="cancelled"&&prepared>=3?"cancelled":"pending"};}},view:{async execute(command,params){
        if(command==="state")return{loading:false,automation:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1",manual:boundary==="manual_control"&&prepared>=3};
        if(params.script.includes(".prepare(")){prepared++;return{ok:true,value:{ok:true,element:{ref:"select"},point:{x:20,y:30},geometry:[0,0,40,60,20,30]}};}
        committed++;throw Error("A stopped selection reached the transaction");
      }}});
    assert.equal(result.result.error.code,boundary);assert.equal(result.result.error.effect_may_have_occurred,false);assert.equal(committed,0);
  }
});

test("screenshot cancellation stops the native request and releases its UI writer",async()=>{
  let started=false,stop;
  const commands=[];
  const view={async execute(command,params){commands.push(command);
    if(command==="state")return{loading:false,automation:true,capture_capability:true,navigation:"1"};
    if(command==="automation")return{ok:true,value:{ok:true,document:"d1",region:{x:0,y:0,width:100,height:100},redactions:[],device_pixel_ratio:1}};
    if(command==="capture"){started=true;return new Promise((resolve,reject)=>{stop=reject;});}
    if(command==="capture_cancel"){assert.equal(params.capture_key,"cancel-native-capture");stop(Error("Cancelled"));return{};}
    throw Error("Unexpected screenshot command");
  }};
  const result=await executeBrowserAutomation({command:"capture",params:{timeout_ms:1000},view,browser:"capture-browser",action:{id:"cancel-native-capture"},
    api:{async call(){return{state:started?"cancelled":"pending"};}}});
  assert.equal(result.result.error.code,"cancelled");assert.ok(commands.includes("capture_cancel"));assert.equal(result.result.image,undefined);
  view.execute=async()=>({loading:false,automation:true,capture_capability:false});
  const next=await executeBrowserAutomation({command:"capture",params:{},view,browser:"capture-browser",action:{id:"next-capture"},api:{async call(){return{state:"pending"};}}});
  assert.equal(next.result.error.code,"unsupported_capability");
});

test('changed capture scope reports only structural causes and withholds the image',async()=>{
  let reads=0;
  const stamp=value=>JSON.stringify([['f0','fd-root',null,false,true,'readable',value,{x:0,y:0},{width:100,height:100},null]]);
  const result=await executeBrowserAutomation({command:'capture',params:{document:'original'},browser:'capture-scope-qa',action:{id:'changed-image'},
    api:{async call(){return{state:'pending'};}},
    pageDriver:{async read(){return{ok:true,document:'original',device_pixel_ratio:1,region:{x:0,y:0,width:100,height:100},redactions:[],frame_stamp:stamp(reads++?'new-private-identity':'old-private-identity')};}},
    view:{async execute(command){if(command==='state')return{loading:false,automation:true,capture_capability:true,navigation:'1'};if(command==='capture')return{data:'private-raw-image'};throw Error('Unexpected capture command');}}});
  assert.equal(result.result.error.code,'capture_changed');assert(result.result.error.message.includes('isolated-document'));
  assert(!JSON.stringify(result).includes('private'));assert.equal(result.result.image,undefined);assert.equal(reads,2);
});

test('visual validation shares the global screenshot budget across different browsers',async()=>{
  let began,release;const started=new Promise(resolve=>{began=resolve;});let visualCalls=0;
  const pending=executeBrowserAutomation({command:'capture',params:{timeout_ms:2000},browser:'global-capture-budget',action:{id:'held-capture-budget'},
    api:{async call(){return{state:'pending'};}},
    view:{async execute(command){
      if(command==='state')return{loading:false,automation:true,capture_capability:true,navigation:'1'};
      if(command==='automation')return{ok:true,value:{ok:true,document:'d1',region:{x:0,y:0,width:100,height:100},redactions:[],device_pixel_ratio:1}};
      if(command==='capture'){began();return new Promise(resolve=>{release=resolve;});}
      return{};
    }}});
  await started;
  const blocked=await executeBrowserAutomation({command:'act',params:{op:'click',target:{image:'untrusted',x:1,y:1}},browser:'another-browser',action:{id:'visual-budget'},
    api:{async call(){throw Error('Admission was not fenced');}},view:{async execute(){visualCalls++;throw Error('Visual validation reached native capture');}}});
  assert.equal(blocked.result.error.code,'browser_busy');assert.equal(visualCalls,0);
  release({});await pending;
});


test('native original-loader observation never falls back to unconstrained page execution',async()=>{
  const f=nativeNavigationFixture('new-document');delete f.options.pageDriver;
  const out=await executeBrowserAutomation(f.options);
  assert.equal(out.result.error.code,'unsupported_capability');assert.equal(out.result.error.effect_may_have_occurred,true);
  assert(!f.calls.some(([command])=>command==='automation'));assert(f.calls.some(([command])=>command==='navigation_cancel'));
});
test('failed loader-constrained observation is returned without retry or partial page data',async()=>{
  const f=nativeNavigationFixture('new-document');let reads=0;
  f.options.pageDriver={async read(method,params,control){assert.equal(control.navigation_ref,f.reference);reads++;
    return{ok:false,error:{code:'stale_document',native_code:'cdp_navigation_document_changed',effect_may_have_occurred:false}};}};
  const out=await executeBrowserAutomation(f.options);assert.equal(reads,1);assert.equal(out.result.error.code,'stale_document');
  assert.equal(out.result.error.native_code,'cdp_navigation_document_changed');assert.equal(out.result.error.effect_may_have_occurred,true);
  assert.equal(out.result.observation,undefined);
});


test('initial reference supplied by creation cannot be replaced by the first state snapshot',async()=>{
  for(const mode of ['ordinary-later','replaced-reading']){
    const f=initialObservationFixture(mode);f.options.initialNavigation=initialRef;
    if(mode==='replaced-reading')f.state.reads=1;
    const out=await executeBrowserAutomation(f.options);
    assert.equal(out.result.error.code,mode==='ordinary-later'?'stale_document':'context_changed');assert.equal(f.state.scripts,0);
    assert.equal(f.state.reads,mode==='ordinary-later'?0:1);assert.equal(out.result.document,undefined);
  }
});
test('invalid creation navigation references execute no browser calls',async()=>{
  for(const initialNavigation of ['',5,{},'raw-loader','nv-invalid']){
    const f=initialObservationFixture();f.options.initialNavigation=initialNavigation;
    const out=await executeBrowserAutomation(f.options);assert.equal(out.result.error.code,'navigation_uncertain');assert.equal(f.state.polls,0);assert.equal(f.state.reads,0);
  }
});


test('same-document completion waits for its original destination proof and guards the returned observation',async()=>{
  for(const mode of ['delayed','old-host','not-same','revoked','after-read']){
    const f=nativeNavigationFixture('same-document'),execute=f.options.view.execute;let polls=0,read=false;
    f.options.view.execute=async(command,params)=>{
      const result=await execute(command,params);if(command==='automation')read=true;
      if(command==='state'&&result.native_navigation){const native=result.native_navigation;polls++;
        if(mode==='delayed')native.page_committed=polls>=3;
        if(mode==='old-host')delete native.same_document_supported;
        if(mode==='not-same')native.same_document=false;
        if(mode==='revoked'||mode==='after-read'&&read)native.page_current=false;
      }return result;
    };
    const out=await executeBrowserAutomation(f.options);assert.equal(out.result.ok,mode==='delayed');
    if(mode==='delayed'){assert(out.result.same_document);assert(polls>=3&&read);}
    else{assert.equal(out.result.error.code,mode==='revoked'||mode==='after-read'?'stale_document':'navigation_uncertain');assert.equal(out.result.observation,undefined);}
    assert.equal(f.calls.filter(([command])=>command==='navigate').length,1);
    if(['old-host','not-same','revoked'].includes(mode))assert.equal(read,false);
  }
});
test('goto canonicalizes the destination before admission without accepting credentials or oversized encoded URLs',async()=>{
  const f=nativeNavigationFixture('same-document');f.options.params.url='HTTP://Example.COM:80/a/../c#你好';
  const out=await executeBrowserAutomation(f.options);assert(out.result.ok);
  assert.equal(f.calls.find(([command])=>command==='navigate')[1].url,'http://example.com/c#%E4%BD%A0%E5%A5%BD');
  assert.equal(out.result.url,'http://example.com/c#%E4%BD%A0%E5%A5%BD');
  for(const url of ['not-a-url','javascript:alert(1)','https://user:private@fixture.invalid/','https://fixture.invalid/#'+'你'.repeat(1000)]){
    const bad=nativeNavigationFixture('same-document');bad.options.params.url=url;const failure=await executeBrowserAutomation(bad.options);
    assert.equal(failure.result.error.code,'url_invalid');assert.equal(failure.result.error.effect_may_have_occurred,false);assert(!bad.calls.some(([command])=>command==='navigate'));
    assert(!JSON.stringify(failure).includes('user:private'));
  }
});

function nativeControlFixture(action,mode='ready'){
  const calls=[],controls=[];let sent=false,polls=0,reads=0,changed=false;
  const options={command:'navigate_to',params:{action,timeout_ms:mode==='timeout'?160:700},action:{id:'control-'+action+'-'+mode},
    api:{async call(){return{state:'pending'};}},
    view:{async execute(command,params){calls.push([command,params]);
      if(command===action){sent=true;assert(!Object.hasOwn(params,'url'));assert(params.timeout_ms>=100);return{accepted:true,...(mode==='missing-ref'?{}:{navigation_ref:'nv-original'})};}
      if(command==='navigation_cancel')return{cancelled:true};
      if(command==='state'){
        if(sent)polls++;
        const native={id:'nv-original',action,receipt_kind:'control',control_supported:true,phase:'acknowledged',acknowledged:true,ok:true,dispatched:true,
          download_known:false,download:false,loader_present:false,same_document_supported:true,same_document:mode==='same-document',page_commit_supported:true,
          page_committed:mode!=='timeout'&&polls>=3,page_current:mode!=='timeout'&&polls>=3&&!changed,page_document:'nd-original'};
        if(mode==='wrong-action')native.action=action==='back'?'forward':'back';
        if(mode==='wrong-kind')native.receipt_kind='navigate';
        if(mode==='old-record')delete native.control_supported;
        if(mode==='wrong-loader')native.loader_present=true;
        if(mode==='wrong-download')native.download_known=true;
        if(mode==='replaced-record')native.id='nv-replacement';
        if(mode==='reload-same-document')native.same_document=true;
        if(mode==='native-rejected')Object.assign(native,{phase:'failed',ok:false,dispatched:false,code:'cdp_navigation_unsupported'});
        return{automation:true,platform:'android-webview',native_navigation_controls_supported:mode!=='old-host',loading:mode==='loading'&&polls<4,navigation:'original-ui-epoch',
          url:'https://stale-toolbar.invalid/',can_go_back:false,can_go_forward:false,...(sent?{native_navigation:native}:{}),manual:sent&&mode==='manual'};
      }
      throw Error('Unexpected native control command '+command);
    }},
    pageDriver:{async read(method,params,control){assert.equal(method,'observe');await control.checkpoint();reads++;controls.push(control.navigation_ref);
      assert(polls>=3);assert.equal(control.navigation_ref,'nv-original');if(mode==='during-read')changed=true;
      return{ok:true,ready:mode==='pending-dom'&&reads===1?'loading':'complete',document:'original-control-document',url:'https://observed-target.invalid/'};
    }}};
  return{options,calls,controls,get reads(){return reads;}};
}
test('native back forward reload wait for original commitment and DOM readiness with empty control ACKs',async()=>{
  for(const action of ['back','forward','reload'])for(const mode of ['ready','loading','pending-dom',...(action==='reload'?[]:['same-document'])]){
    const f=nativeControlFixture(action,mode),out=await executeBrowserAutomation(f.options);
    assert.equal(out.result.ok,true);assert.equal(out.result.url,'https://observed-target.invalid/');assert.equal(Boolean(out.result.same_document),mode==='same-document');
    assert.equal(f.calls.filter(([command])=>command===action).length,1);assert(!f.calls.some(([command])=>command==='navigation_cancel'));
    assert.equal(f.reads,mode==='pending-dom'?2:1);assert(f.controls.every(ref=>ref==='nv-original'));
  }
});
test('native controls reject wrong action kind metadata and missing original records without observation or replay',async()=>{
  for(const action of ['back','forward','reload'])for(const mode of ['wrong-action','wrong-kind','old-record','wrong-loader','wrong-download','replaced-record','missing-ref',...(action==='reload'?['reload-same-document']:[])]){
    const f=nativeControlFixture(action,mode),out=await executeBrowserAutomation(f.options);
    assert.equal(out.result.ok,false);assert.equal(out.result.error.code,'navigation_uncertain');assert.equal(out.result.error.effect_may_have_occurred,true);assert.equal(f.reads,0);
    assert.equal(f.calls.filter(([command])=>command===action).length,1);assert(!f.calls.some(([command])=>['navigate','stop','close'].includes(command)));
  }
});
test('older Android control hosts fail before navigation and native no-effect rejections retain the original receipt',async()=>{
  for(const action of ['back','forward','reload']){
    const old=nativeControlFixture(action,'old-host'),unsupported=await executeBrowserAutomation(old.options);
    assert.equal(unsupported.result.error.code,'unsupported_capability');assert.equal(unsupported.result.error.effect_may_have_occurred,false);assert.equal(old.reads,0);assert(!old.calls.some(([command])=>command===action));
    const rejected=nativeControlFixture(action,'native-rejected'),out=await executeBrowserAutomation(rejected.options);
    assert.equal(out.result.error.code,'navigation_failed');assert.equal(out.result.error.native_code,'cdp_navigation_unsupported');assert.equal(out.result.error.effect_may_have_occurred,false);assert.equal(rejected.reads,0);
    assert(!rejected.calls.some(([command])=>command==='navigation_cancel'));
  }
});
test('native control post-read staleness takeover and timeout cancel only the original record',async()=>{
  for(const action of ['back','forward','reload'])for(const mode of ['during-read','manual','timeout']){
    const f=nativeControlFixture(action,mode),out=await executeBrowserAutomation(f.options);
    assert.equal(out.result.ok,false);assert.equal(out.result.error.code,mode==='during-read'?'stale_document':mode==='manual'?'manual_control':'timed_out');
    assert.equal(out.result.error.effect_may_have_occurred,true);assert.equal(out.result.observation,undefined);assert.equal(f.reads,mode==='during-read'?1:0);
    const cleanup=f.calls.filter(([command])=>command==='navigation_cancel');assert.equal(cleanup.length,1);assert.equal(cleanup[0][1].navigation_ref,'nv-original');
    assert(!f.calls.some(([command])=>['navigate','stop','close'].includes(command)));
  }
});

const nativeRequestRef='nr-00000000-0000-4000-8000-000000000099';
function associatedDownloadFixture(mode){
  const f=nativeNavigationFixture('download'),execute=f.options.view.execute;let polls=0;
  f.options.params.timeout_ms=mode==='unmatched'?180:500;
  f.options.view.execute=async(command,params)=>{
    const out=await execute(command,params);if(command==='state'&&out.native_navigation){polls++;
      Object.assign(out.native_navigation,{loader_present:mode!=='missing-loader',request_association_supported:true,request_ref:mode==='bad-ref'?'nr-invalid':nativeRequestRef});
      if(mode==='before-ack')out.native_navigation.phase='sent';
    }return out;
  };
  f.options.downloads=()=>{
    if(!f.calls.some(([command])=>command==='navigate'))return{ok:true,armed:true,downloads:[{id:'old',state:'completed'}]};
    const rows=[{id:'old',state:'completed'},{id:'unrelated',state:'pending',native:{source_request_ref:'nr-unrelated'}}];
    if(mode!=='unmatched')rows.push({id:'original',state:mode==='completed'?'completed':'pending',native:{source_request_ref:mode==='delayed'&&polls<3?null:nativeRequestRef}});
    if(mode==='ambiguous')rows.push({id:'duplicate',state:'completed',native:{source_request_ref:nativeRequestRef}});
    return{ok:true,armed:true,downloads:rows};
  };
  return f;
}
test('native download identity selects only its original request despite concurrent pending responses',async()=>{
  for(const mode of ['pending','completed','delayed']){
    const f=associatedDownloadFixture(mode),out=await executeBrowserAutomation(f.options);
    assert.equal(out.result.ok,true);assert.equal(out.result.source_association,'original_navigation');assert.equal(out.result.navigation_ref,f.reference);
    assert.deepEqual(out.result.downloads.map(row=>row.id),['original']);assert(!f.calls.some(([command])=>['automation','input','navigation_cancel'].includes(command)));
  }
});
test('response permission before a native ACK remains explicit browser scope without deadlocking or claiming attribution',async()=>{
  const f=associatedDownloadFixture('before-ack'),out=await executeBrowserAutomation(f.options);
  assert.equal(out.result.outcome,'download_pending');assert.equal(out.result.completed,false);assert.equal(out.result.source_association,'browser_scope');assert.equal(out.result.navigation_ref,f.reference);
  assert(out.result.downloads.some(row=>row.id==='original'));assert(!f.calls.some(([command])=>['automation','input','navigation_cancel'].includes(command)));
});
test('invalid ambiguous or missing native download request proof never borrows another response',async()=>{
  for(const mode of ['bad-ref','missing-loader','ambiguous','unmatched']){
    const f=associatedDownloadFixture(mode),out=await executeBrowserAutomation(f.options);
    assert.equal(out.result.ok,false);assert.equal(out.result.error.code,'download_identity_unavailable');assert.equal(out.result.error.effect_may_have_occurred,true);
    assert.equal(out.result.downloads,undefined);assert(!f.calls.some(([command])=>['automation','input','navigation_cancel'].includes(command)));
  }
});
test('original creation observation uses the same exact download request identity filter',async()=>{
  const out=await executeBrowserAutomation({command:'observe',params:{timeout_ms:500},initialNavigation:initialRef,action:{id:'initial-original-request'},api:{async call(){return{state:'pending'};}},
    downloads:()=>({ok:true,armed:true,downloads:[{id:'other',state:'pending'},{id:'original',state:'pending',native:{source_request_ref:nativeRequestRef}}]}),
    view:{async execute(command){assert.equal(command,'state');return{loading:true,automation:true,initial_navigation_pending:true,native_navigation:{id:initialRef,initial:true,phase:'acknowledged',acknowledged:true,ok:true,
      dispatched:true,download_known:true,download:true,loader_present:true,request_association_supported:true,request_ref:nativeRequestRef}};}}});
  assert.equal(out.result.ok,true);assert.equal(out.result.source_association,'original_navigation');assert.deepEqual(out.result.downloads.map(row=>row.id),['original']);
});
