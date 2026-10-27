import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';
import {executeBrowserDownload} from '../host/download-dispatch.js';
const source=(await readFile(new URL('../host/automation-dispatch.js',import.meta.url),'utf8'))
  .replace('import { executeDownloadSave, downloadSaveState } from "./download-save.js";',`import {executeDownloadSave,downloadSaveState} from ${JSON.stringify(new URL('../host/download-save.js',import.meta.url).href)};`)
  .replace('import { browserRuntime } from "./automation-runtime.js";','function browserRuntime(){}')
  .replace('import { prepareScreenshot } from "./capture-image.js";','function prepareScreenshot(){}')
  .replace('import { executeFileUpload } from "./upload-dispatch.js";',`import {executeFileUpload} from ${JSON.stringify(new URL('../host/upload-dispatch.js',import.meta.url).href)};`)
  .replace('import { executeBrowserDownload } from "./download-dispatch.js";',`import {executeBrowserDownload} from ${JSON.stringify(new URL('../host/download-dispatch.js',import.meta.url).href)};`);
const {executeBrowserAutomation}=await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
test('response interception must be acknowledged before navigation',async()=>{
  let navigations=0;
  const result=await executeBrowserAutomation({command:'navigate_to',params:{action:'goto',url:'https://example.com/'},action:{id:'no-download-host'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command){
      if(command==='state')return{loading:false,automation:true,download_capability:true};
      if(command==='downloads')return{ok:true,armed:false};navigations++;throw Error('Unsafe navigation');
    }}});
  assert.equal(result.result.error.code,'download_unavailable');assert.equal(navigations,0);
});
test('a download checkpoint fences repeated input even when native navigation is aborted',async()=>{
  let inputs=0;
  const result=await executeBrowserAutomation({command:'act',params:{op:'click'},action:{id:'pending-download'},api:{async call(){return{state:'pending'};}},view:{async execute(command){
    if(command==='state')return{downloads_armed:true,error:'ERR_ABORTED'};
    if(command==='downloads')return{downloads:[{id:'owned',state:'pending'}]};inputs++;throw Error('A second click was sent');
  }}});
  assert.equal(result.result.outcome,'download_pending');assert.equal(result.result.completed,false);assert.equal(result.result.dispatched,false);assert.equal(inputs,0);
});
test('download metadata remains readable during takeover and mutation is denied',async()=>{
  let mutations=0;
  const base={action:{id:'manual-download'},api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
    if(command==='state')return{manual:true,download_capability:true};
    if(params.action!=='list')mutations++;return{downloads:[]};
  }}};
  assert.deepEqual((await executeBrowserAutomation({...base,command:'download',params:{action:'list'}})).result.downloads,[]);
  assert.equal((await executeBrowserAutomation({...base,command:'download',params:{action:'accept',download:'owned'}})).result.error.code,'manual_control');assert.equal(mutations,0);
});

test('workspace saves obey manual control and display-target cancellation before file access',async()=>{
  let files=0,native=0;
  const base={command:'save_download',params:{download:'owned',path:'new.bin'},action:{id:'save-policy'},
    api:{async call(method){if(method!=='ui.result'){files++;throw Error('File service reached');}return{state:'pending'};}},
    view:{async execute(command){native++;assert.equal(command,'state');return{manual:true,download_capability:true};}}};
  assert.equal((await executeBrowserAutomation(base)).result.error.code,'manual_control');assert.equal(files,0);
  native=0;assert.equal((await executeBrowserAutomation({...base,alive:()=>false})).result.error.code,'context_changed');assert.equal(native,0);assert.equal(files,0);
});

test('a download-only popup keeps receipts while refusing further page operations',async()=>{
  const row={id:'retained-response',state:'completed',size:5,sha256:'a'.repeat(64)};
  let pageCalls=0;
  const base={action:{id:'closed-download-popup'},api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
    if(command==='state')return{close_requested:true,closed_by_page:false,download_capability:true,downloads_armed:true};
    if(command==='downloads')return params.action==='list'?{downloads:[row]}:row;
    pageCalls++;throw Error('A closing popup received page input or evaluation');
  }}};
  for(const command of ['observe','act','navigate_to','wait_for','capture','dialog','chooser','upload']){
    const result=(await executeBrowserAutomation({...base,command,params:{action:'cancel'}})).result;
    assert.equal(result.outcome,'download_retained');assert.equal(result.page_closed,true);assert.equal(result.completed,false);
    assert.equal(result.dispatched,false);assert.deepEqual(result.downloads,[row]);
  }
  const completed=(await executeBrowserAutomation({...base,command:'download',params:{action:'wait'}})).result;
  assert.equal(completed.verified,true);assert.deepEqual(completed.download,row);assert.equal(pageCalls,0);
});

test('a closing popup without retained responses is a closed checkpoint',async()=>{
  let pageCalls=0;
  const result=await executeBrowserAutomation({command:'act',params:{op:'click'},action:{id:'closed-empty-popup'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command){
      if(command==='state')return{close_requested:true,closed_by_page:false,download_capability:true};
      if(command==='downloads')return{downloads:[]};pageCalls++;throw Error('Input reached a closed page');
    }}});
  assert.equal(result.result.outcome,'closed');assert.equal(result.result.dispatched,false);assert.equal(pageCalls,0);
});

for(const interruption of ['navigation','evaluation'])test(`POST ${interruption} before download identity does not hide the retained response`,async()=>{
  let input=0,navigation='1',offeredAt=Infinity;
  const row={id:'original-post-response',state:'pending'};
  const result=await executeBrowserAutomation({command:'act',params:{op:'click',document:'d1',target:{ref:'e1'}},action:{id:'post-'+interruption},
    api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,navigation,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,download_capability:true,downloads_armed:true};
      if(command==='downloads')return{downloads:performance.now()>=offeredAt?[row]:[]};
      if(command==='input'){input++;if(interruption==='navigation'){navigation='2';offeredAt=performance.now()+60;}return{accepted:true,trusted_input:true};}
      if(params.script.includes('.observe(')){navigation='2';offeredAt=performance.now()+60;throw Error('Browser control or document changed during evaluation');}
      return{ok:true,value:{ok:true,element:{ref:'e1'},point:{x:10,y:10},geometry:[10,10,20,20]}};
    }}});
  assert.equal(result.result.outcome,'download_pending',JSON.stringify(result.result));assert.equal(result.result.completed,false);assert.equal(result.result.dispatched,true);
  assert.deepEqual(result.result.downloads,[row]);assert.equal(input,1);
});

test('post-input identity coalescing obeys cancellation without another input',async()=>{
  let input=0,checks=0;
  const result=await executeBrowserAutomation({command:'act',params:{op:'click',document:'d1',target:{ref:'e1'}},action:{id:'cancel-post'},
    api:{async call(){return{state:input&&++checks>1?'cancelled':'pending'};}},view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,navigation:input?'2':'1',chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,download_capability:true,downloads_armed:true};
      if(command==='downloads')return{downloads:[]};
      if(command==='input'){input++;return{accepted:true,trusted_input:true};}
      return{ok:true,value:{ok:true,element:{ref:'e1'},point:{x:10,y:10},geometry:[10,10,20,20]}};
    }}});
  assert.equal(result.result.error.code,'cancelled');assert.equal(result.result.error.effect_may_have_occurred,true);assert.equal(input,1);
});

test('a slow POST response stays fenced until native navigation settles, without reading the new document',async()=>{
  let inputs=0,started=Infinity,readsAfterInput=0;
  const row={id:'slow-original-post',state:'pending'};
  const result=await executeBrowserAutomation({command:'act',params:{op:'click',document:'d1',target:{ref:'e1'},timeout_ms:4000},action:{id:'slow-post'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
      if(command==='state')return{loading:inputs>0&&performance.now()-started<1350,automation:true,navigation:inputs?'2':'1',
        chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,download_capability:true,downloads_armed:true};
      if(command==='downloads')return{downloads:performance.now()-started>=1400?[row]:[]};
      if(command==='input'){inputs++;started=performance.now();return{accepted:true,trusted_input:true};}
      if(inputs)readsAfterInput++;
      return{ok:true,value:{ok:true,element:{ref:'e1'},point:{x:10,y:10},geometry:[10,10,20,20]}};
    }}});
  assert.equal(result.result.outcome,'download_pending',JSON.stringify(result.result));assert.equal(inputs,1);assert.equal(readsAfterInput,0);
});
test('a response callback after the final page read is still returned without another click',async()=>{
  let pending=false,inputs=0;
  const result=await executeBrowserAutomation({command:'act',params:{op:'click',document:'d1',target:{ref:'e1'}},action:{id:'late-response'},
    api:{async call(){return{state:'pending'};}},view:{async execute(command,params){
      if(command==='state')return{loading:false,automation:true,navigation:'1',download_capability:true,downloads_armed:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};
      if(command==='downloads')return{downloads:pending?[{id:'response',state:'pending'}]:[]};
      if(command==='input'){inputs++;return{accepted:true,trusted_input:true};}
      if(params.script.includes('.observe(')){setTimeout(()=>{pending=true;},30);return{ok:true,value:{ok:true,ready:'complete',document:'d1'}};}
      return{ok:true,value:{ok:true,element:{ref:'e1'},point:{x:10,y:10},geometry:[10,10,20,20]}};
    }}});
  assert.equal(result.result.outcome,'download_pending');assert.equal(result.result.completed,false);assert.equal(inputs,1);
});
function request(params,native,extra={}){
  let dispatched=false;
  return executeBrowserDownload({params,deadline:performance.now()+1000,guard:async()=>({state:{download_capability:true}}),
    view:{execute:(_,fields)=>native(fields)},setDispatched:value=>{dispatched=value;},
    fail:(code,message)=>({handled:true,result:{ok:false,error:{code,message,effect_may_have_occurred:dispatched}}}),...extra});
}
test('waiting never refetches or accepts a pending response',async()=>{
  const result=await request({action:'wait',download:'owned'},async fields=>{assert.equal(fields.action,'get');return{id:'owned',state:'pending'};},{deadline:performance.now()+10});
  assert.equal(result.result.completed,false);assert.equal(result.result.outcome,'download_pending');
});
test('download wait requires verified terminal completion and retains native failure',async()=>{
  const completed=await request({action:'wait',download:'owned'},async()=>({id:'owned',state:'completed',sha256:'a'.repeat(64)}));
  assert.equal(completed.result.verified,true);
  const failed=await request({action:'wait',download:'owned'},async()=>({id:'owned',state:'failed',error:'download_interrupted',interrupt_reason:13}));
  assert.equal(failed.result.ok,false);assert.equal(failed.result.error.code,'download_interrupted');assert.equal(failed.result.download.interrupt_reason,13);
});
test('lost acceptance does not retry and preserves its failing stage and cause',async()=>{
  let starts=0;
  const result=await request({action:'accept',download:'owned',download_max_bytes:1234,transfer_timeout_ms:5678},async fields=>{
    starts++;assert.equal(fields.max_bytes,1234);assert.equal(fields.timeout_ms,5678);throw Object.assign(Error('Synthetic lost native acknowledgement'),{code:'test_transport'});
  });
  assert.equal(starts,1);assert.equal(result.result.error.code,'action_uncertain');assert.equal(result.result.error.stage,'accept');assert.equal(result.result.error.cause.code,'test_transport');
});
test('a rejected acceptance is a tool failure with its terminal receipt',async()=>{
  const result=await request({action:'accept',download:'owned'},async()=>({id:'owned',state:'failed',error:'download_limit'}));
  assert.equal(result.result.ok,false);assert.equal(result.result.error.code,'download_limit');assert.equal(result.result.download.id,'owned');
});
