import assert from 'node:assert/strict';
import test from 'node:test';
import {createHash} from 'node:crypto';
import {ensureNativeBrowser} from '../host/native-view.js';
import {readFile} from 'node:fs/promises';
const source=async name=>(await readFile(new URL('../host/'+name+'.js',import.meta.url),'utf8'))
  .replace(/from (["'])\.\/automation-runtime\.js\1/g,`from ${JSON.stringify(new URL('../page/runtime.js',import.meta.url).href)}`);
const moduleUrl=code=>`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`;
const nativeImport=name=>JSON.stringify(new URL('../host/'+name+'.js',import.meta.url).href);
let dispatcher=await source('automation-dispatch');
for(const name of ['capture-image','visual-snapshot','visual-dispatch','upload-dispatch','download-dispatch','download-save'])
  dispatcher=dispatcher.replace(`from "./${name}.js"`,`from ${nativeImport(name)}`);
const {executeBrowserAutomation}=await import(moduleUrl(dispatcher));
const reader=moduleUrl(await source('frame-reader'));
const driver=(await source('frame-driver')).replace("from './frame-reader.js'",`from ${JSON.stringify(reader)}`)
  .replace("from './frame-capture.js'",`from ${nativeImport('frame-capture')}`);
const {createFrameDriver}=await import(moduleUrl(driver));
const {createDocumentDriver}=await import(moduleUrl(await source('document-driver')));

const guid='00000000-0000-4000-8000-000000000091',navigation='nv-'+guid,fd='fd-'+guid,document='d-'+guid;
function fixture(mode){
  const trace={states:0,observations:0,native:[],scripts:0};
  const execute=async(command,params)=>{
    if(command==='state'){
      const ready=++trace.states>=3;
      return{loading:false,automation:true,navigation:ready?2:1,initial_navigation_pending:!ready,
        native_navigation:{id:mode==='ui-replaced'&&trace.observations?'nv-00000000-0000-4000-8000-000000000092':navigation,
          initial:true,phase:ready?'acknowledged':'sent',acknowledged:ready,ok:ready,dispatched:true,download_known:ready,download:false,
          loader_present:ready,page_commit_supported:true,page_committed:ready,page_current:ready,page_document:ready?'nd-original':null}};
    }
    if(command==='automation'){trace.scripts++;throw Error('An unguarded native script was reached');}
    assert.equal(command,'frames');assert.equal(params.agent,true);assert.equal(params.navigation_ref,mode==='ordinary-later'?undefined:navigation);trace.native.push(params.action);
    if(mode==='native-rejected')return{ok:false,error:{code:'stale_document',native_code:'cdp_navigation_document_changed',evaluation_may_have_occurred:false}};
    if(params.action==='list')return{ok:true,complete:true,frames:[{id:'f0',document:mode==='tree-changed'&&trace.observations?'fd-00000000-0000-4000-8000-000000000092':fd,parent:null,isolated:true,loading:false,url:'https://fixture.invalid/'}]};
    assert.equal(params.action,'evaluate');assert.equal(params.frame,'f0');assert.equal(params.document,fd);
    assert(!params.script.includes(navigation));
    const tail=params.script.split('({local_frame:true}).').at(-1),method=tail.slice(0,tail.indexOf('('));
    let value={ok:true,document,ready:'complete',url:'https://fixture.invalid/',title:'Initial target',viewport:{width:320,height:240}};
    assert(['describe','observe'].includes(method));
    if(method==='observe'){trace.observations++;value={...value,elements:[],frames:[{id:'f0',dom:true}],text:'Original target page',visited:1,truncated:false};}
    return{ok:true,frame:'f0',frame_document:fd,value};
  };
  const options={command:'observe',params:{timeout_ms:1000},browser:'initial-frame-'+mode,action:{id:'initial-read-'+mode},
    api:{async call(){return{state:'pending'};}},view:{execute},
    pageDriver:mode==='document-fallback'?createDocumentDriver({execute}):createFrameDriver({execute})};
  return{trace,options};
}

test('initial observation traverses the actual dispatcher and frame reader with one original native navigation proof',async()=>{
  const f=fixture('ready'),out=await executeBrowserAutomation(f.options);
  assert(out.result.ok);assert.equal(out.result.document,document);assert.equal(out.result.text,'Original target page');
  assert.equal(f.trace.observations,1);assert(f.trace.native.filter(name=>name==='list').length>=2);assert.equal(f.trace.scripts,0);
});
test('initial native guard refusal withholds all page data through the actual frame coordinator',async()=>{
  const f=fixture('native-rejected'),out=await executeBrowserAutomation(f.options);
  assert.equal(out.result.error.code,'stale_document');assert.equal(out.result.error.effect_may_have_occurred,false);
  assert.equal(out.result.text,undefined);assert.equal(out.result.document,undefined);assert.equal(f.trace.observations,0);
});
test('initial payload is discarded when native tree or UI ownership changes during actual frame reading',async()=>{
  for(const [mode,code] of [['tree-changed','stale_document'],['ui-replaced','context_changed']]){
    const f=fixture(mode),out=await executeBrowserAutomation(f.options);assert.equal(out.result.error.code,code);
    assert.equal(out.result.text,undefined);assert.equal(out.result.document,undefined);assert.equal(f.trace.observations,1);
  }
});
test('actual legacy document driver cannot bypass the required initial loader proof',async()=>{
  const f=fixture('document-fallback'),out=await executeBrowserAutomation(f.options);
  assert.equal(out.result.error.code,'unsupported_capability');assert.equal(f.trace.native.length,0);assert.equal(f.trace.scripts,0);
});


// Read the exact reviewed projection used by the packaged workbench. Skip only
// unrelated DOM layout/reveal blocks; execute its admission and driver routing.
async function productReadRoute(){
  const relative='app/web/js/features/workbench/panel-manager.js';
  const panel=(await readFile(new URL('../.mdo/source/'+relative,import.meta.url),'utf8')).replace(/\r\n/g,'\n');
  const receipt=JSON.parse(await readFile(new URL('../.mdo/host-overlays.json',import.meta.url),'utf8'));
  assert.equal(createHash('sha256').update(panel).digest('hex'),receipt.files[relative]);
  const start=panel.indexOf('      const { command, id, ...params } = action.params;');
  const end=panel.indexOf('if (command === "act" || command === "navigate_to"',start);
  const view=panel.indexOf('      let view = browserViews.get(id);',end),finish=panel.indexOf('      if (command === "wait") {',view);
  assert(start>=0&&end>start&&view>end&&finish>view);
  const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
  return new AsyncFunction('action','ensureNativeBrowser','browserViews','api','bridge','clientId','isRemoteTarget','stopped','displayTarget','targetState',
    'createFrameDriver','createDocumentDriver','executeBrowserAutomation',panel.slice(start,end)+panel.slice(view,finish));
}
test('actual workbench creation receipt constrains observation even before the first native state read',async()=>{
  const run=await productReadRoute();
  for(const mode of ['ready','before-first','record-replaced','record-missing','ordinary-later']){
    const f=fixture(mode),browserViews=new Map();let created=0;f.trace.states=3;
    const bridge={async call(command,params){
      if(command==='capabilities')return{browser_use:{available:true},initial_download_interception:true};
      if(command==='create'){created++;return{created:true,downloads_intercepted:true,initial_navigation_pending:true,navigation_ref:navigation};}
      if(command==='state'){
        const value=await f.options.view.execute(command,params);Object.assign(value,{frame_automation:true,platform:'android-webview',navigation:2});
        if(mode==='before-first'||mode==='ordinary-later')value.native_navigation.page_current=false;
        if(mode==='record-replaced')value.native_navigation.id='nv-00000000-0000-4000-8000-000000000092';
        if(mode==='record-missing')delete value.native_navigation;
        return value;
      }
      return f.options.view.execute(command,params);
    }};
    const row={id:'original-tab',url:'https://fixture.invalid/',client:'fixture'};
    const api={bind(){return this;},async call(command){if(command==='browser.get')return row;if(command==='ui.result')return{state:'pending'};throw Error('Unexpected SDK call '+command);}};
    if(mode==='ordinary-later')browserViews.set(row.id,{row,execute:(command,params)=>bridge.call(command,params)});
    const out=await run({id:'workbench-'+mode,params:{command:'observe',id:row.id,timeout_ms:1000}},ensureNativeBrowser,browserViews,api,bridge,'fixture',()=>false,false,'local',()=>({}),
      createFrameDriver,createDocumentDriver,executeBrowserAutomation);
    assert.equal(created,mode==='ordinary-later'?0:1);
    if(mode==='ready'||mode==='ordinary-later'){assert(out.ok);assert.equal(out.text,'Original target page');assert.equal(f.trace.observations,1);}
    else{assert.equal(out.error.code,mode==='before-first'?'stale_document':'context_changed');assert.equal(f.trace.native.length,0);assert.equal(out.text,undefined);}
    assert.equal(f.trace.scripts,0);
  }
});
