import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

// Run the actual shared authority guard. The visual consumer is a narrow seam
// that probes only the pending read-only capture checkpoint it was supplied.
let source=readFileSync(new URL('../host/automation-dispatch.js',import.meta.url),'utf8');
for(const name of ['capture-image','visual-snapshot','upload-dispatch','download-dispatch','download-save'])
  source=source.replace(`from "./${name}.js"`,`from ${JSON.stringify(new URL(`../host/${name}.js`,import.meta.url).href)}`);
source=source.replace('import { browserRuntime } from "./automation-runtime.js";','function browserRuntime(){}')
  .replace('import { executeVisualAction } from "./visual-dispatch.js";',`async function executeVisualAction(args){return globalThis.__visualCheckpointConsumer(args);}`);
const {executeBrowserAutomation}=await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);

test('owned capture waiting bypasses only busy admission and still checks every authoritative boundary',async()=>{
  for(const mode of ['settling','cancelled','manual','navigation','cleanup','deadline']){
    const state={capture_busy:false,navigation:1},calls=[];
    globalThis.__visualCheckpointConsumer=async({guard,captureCheckpoint})=>{
      state.capture_busy=true;assert.equal((await guard(true)).result.error.code,'browser_busy');
      if(mode==='cancelled')state.cancelled=true;if(mode==='manual')state.manual=true;
      if(mode==='navigation')state.navigation=2;if(mode==='cleanup')state.capture_cleanup_failed=true;
      if(mode==='deadline')await new Promise(resolve=>setTimeout(resolve,80));
      const waited=await captureCheckpoint();state.capture_busy=false;
      if(mode==='settling'){assert.equal(waited.handled,undefined);assert.equal((await guard(true)).handled,undefined);return{handled:true,result:{ok:true}};}
      return waited;
    };
    const out=await executeBrowserAutomation({command:'act',params:{op:'click',document:'d-fixture',target:{image:'visual-fixture',x:1,y:1},timeout_ms:mode==='deadline'?60:1000},
      view:{async execute(command,params){calls.push(command);if(command==='state')return{...state,automation:true,loading:false,download_capability:true,downloads_armed:true,
        chooser_capability:true,chooser_upload_capability:true,chooser_armed:true};if(command==='downloads')return{downloads:[]};throw Error('Unexpected native input');}},
      browser:'capture-checkpoint-'+mode,pageDriver:{},action:{id:'capture-checkpoint-'+mode},api:{async call(){return{state:state.cancelled?'cancelled':'pending'};}}});
    if(mode==='settling')assert(out.result.ok);else assert.equal(out.result.error.code,{cancelled:'cancelled',manual:'manual_control',navigation:'stale_document',cleanup:'capture_cleanup_failed',deadline:'timed_out'}[mode]);
    assert(!calls.includes('input'));
  }
  delete globalThis.__visualCheckpointConsumer;
});
