import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';
import {ensureNativeBrowser} from '../host/native-view.js';

const fragment=await readFile(new URL('../host/reveal-action-view.js',import.meta.url),'utf8');
const source=`const ensureNativeBrowser=${ensureNativeBrowser.toString()};
export async function reveal({command,id,params,action,api,bridge,browserViews,clientId}){
 const stopped=false,displayTarget='local',isRemoteTarget=()=>false;
 ${fragment}
 throw Error('Unsupported automation continued into page execution');
}`;
const {reveal}=await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);

test('unsupported provider reaches the shared UI receipt without creating or revealing a page',async()=>{
  for(const command of ['observe','act','navigate_to','capture','upload']){
    const calls=[];const browserViews=new Map();
    const result=await reveal({command,id:'android-tab',params:{op:'click',action:'set'},action:{id:'original-action',context:{}},browserViews,clientId:'android',
      api:{bind(){return{async call(method){calls.push(method);assert.equal(method,'browser.get');return{id:'android-tab',url:'https://example.com'};}};}},
      bridge:{async call(method){calls.push(method);assert.equal(method,'capabilities');return{browser_use:{available:false,reason:'isolated_world_unsupported'}};}}
    });
    assert.equal(result.ok,false);assert.equal(result.action,'original-action');assert.equal(result.error.code,'unsupported_capability');
    assert.equal(result.error.capability_reason,'isolated_world_unsupported');assert.equal(result.error.effect_may_have_occurred,false);
    assert.deepEqual(calls,['browser.get','capabilities']);assert.equal(browserViews.size,0);
  }
});
