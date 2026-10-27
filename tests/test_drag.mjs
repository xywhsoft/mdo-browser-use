import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const source=(await readFile(new URL("../host/automation-dispatch.js",import.meta.url),"utf8"))
  .replace('import { browserRuntime } from "./automation-runtime.js";','function browserRuntime() {}')
  .replace('import { prepareScreenshot } from "./capture-image.js";','async function prepareScreenshot() {}')
  .replace('import { executeBrowserDownload } from "./download-dispatch.js";', 'async function executeBrowserDownload(){throw Error("Unexpected download path")}')
  .replace('import { executeFileUpload } from "./upload-dispatch.js";', 'async function executeFileUpload(){throw Error("Unexpected upload path")}');
const {executeBrowserAutomation:dispatch}=await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const executeBrowserAutomation=options=>dispatch({...options,browser:options.browser||options.view,view:{async execute(command,params){
  if(command==='downloads')return{ok:true,armed:true,downloads:[]};
  const result=await options.view.execute(command,params);
  return command==='state'?{download_capability:true,downloads_armed:true,...result}:result;
}}});

function fixture(){
  const commands=[];let moving=false,samples=0,cancelled=false,active=false,reject;
  const view={async execute(command,params){commands.push({command,params});
    if(command==="state")return{loading:false,automation:true,drag_capability:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1"};
    if(command==="drag"){active=true;return{accepted:true,trusted_input:true,drag_kind:"html5",dispatched:true};}
    if(command==="drag_cancel"){reject?.(Error("Cancelled"));return{};}
    if(params.script.includes(".prepare(")){
      const destination=params.script.includes('"name":"Destination"')||params.script.includes('"ref":"destination"');
      const x=destination?200+(moving?++samples*10:0):20;
      return{ok:true,value:{ok:true,element:{ref:destination?"destination":"source"},point:{x,y:20},geometry:[x,0,20,40,x,20]}};
    }
    return{ok:true,value:{ok:true,text:"Drop verified"}};
  }};
  return{commands,view,move(){moving=true;},reset(){active=false;cancelled=false;},pending(){view.execute=async(command,params)=>{
    commands.push({command,params});
    if(command==="drag"){active=true;return new Promise((resolve,no)=>{reject=no;});}
    if(command==="drag_cancel"){reject?.(Error("Cancelled"));return{};}
    if(command==="state")return{loading:false,automation:true,drag_capability:true,chooser_capability:true,chooser_upload_capability:true,chooser_armed:true,navigation:"1"};
    const destination=params.script.includes('"name":"Destination"')||params.script.includes('"ref":"destination"'),x=destination?200:20;
    return{ok:true,value:{ok:true,element:{ref:destination?"destination":"source"},point:{x,y:20},geometry:[x,0,20,40,x,20]}};
  };cancelled=true;},execute:()=>executeBrowserAutomation({command:"act",params:{op:"drag",document:"d1",target:{name:"Source"},destination:{name:"Destination"},timeout_ms:300},
    view,browser:"test-browser",action:{id:"drag-receipt"},api:{async call(){return{state:cancelled&&active?"cancelled":"pending"};}}})};
}

test("drag pins two stable targets and sends one native gesture without data or script",async()=>{
  const f=fixture(),result=await f.execute(),sent=f.commands.filter(row=>row.command==="drag");
  assert.equal(result.result.input_method,"native_drag");assert.equal(result.result.verification,"native_delivery");assert.equal(sent.length,1);
  assert.equal(sent[0].params.drag_key,"drag-receipt");assert.equal(sent[0].params.navigation,"1");
  assert.equal(sent[0].params.x,20);assert.equal(sent[0].params.to_x,200);assert.equal(sent[0].params.data,undefined);assert.equal(sent[0].params.script,undefined);
});

test("moving drop targets never begin native input",async()=>{
  const f=fixture();f.move();const result=await f.execute();
  assert.equal(result.result.error.code,"timed_out");assert.equal(result.result.error.effect_may_have_occurred,false);
  assert.equal(f.commands.filter(row=>row.command==="drag").length,0);
});

test("receipt cancellation reaches the exact native gesture and releases the UI writer",async()=>{
  const f=fixture();f.pending();const result=await f.execute();
  assert.equal(result.result.error.code,"cancelled");assert.equal(result.result.error.effect_may_have_occurred,true);
  assert.ok(f.commands.some(row=>row.command==="drag_cancel"&&row.params.drag_key==="drag-receipt"));
  f.reset();
  f.view.execute=async()=>({loading:false,automation:true,drag_capability:false});
  const again=await f.execute();assert.equal(again.result.error.code,"unsupported_capability");
});

test("unsupported dragging fails before reading or scrolling page targets",async()=>{
  const f=fixture();let reads=0;
  f.view.execute=async command=>{if(command==="state")return{loading:false,automation:true};reads++;throw Error("Unsupported drag touched its page");};
  const result=await f.execute();assert.equal(result.result.error.code,"unsupported_capability");assert.equal(reads,0);
});
