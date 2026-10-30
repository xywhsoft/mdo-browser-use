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

test('failed native startup preserves navigation uncertainty and never exposes a browser view',async()=>{
  for(const mayHaveNavigated of [false,true]){
    const calls=[],browserViews=new Map();
    const native_create={created_may_have_occurred:true,navigation_may_have_occurred:mayHaveNavigated,network_cleanup_confirmed:true,view_cleanup_confirmed:!mayHaveNavigated};
    const result=await reveal({command:'act',id:'tab',params:{op:'click'},action:{id:'original-action',context:{}},browserViews,clientId:'android',
      api:{bind(){return{async call(method){calls.push(method);return{id:'tab',url:'https://fixture.invalid/'};}};}},
      bridge:{async call(method){calls.push(method);return method==='capabilities'?{browser_use:{available:true},initial_download_interception:true}:
        {ok:false,created:false,error:{code:mayHaveNavigated?'network_navigation_uncertain':'network_start_timeout',message:'private-provider-detail'},native_create};}}
    });
    assert.equal(result.ok,false);assert.equal(result.error.code,'native_create_failed');assert.equal(result.error.effect_may_have_occurred,mayHaveNavigated);
    assert.deepEqual(result.error.native_create,native_create);assert.equal(browserViews.size,0);assert(!JSON.stringify(result).includes('private-provider-detail'));
    assert.equal(result.error.message.includes('原标签'),mayHaveNavigated);assert.equal(result.error.message.includes('已清理'),!mayHaveNavigated);
    assert.deepEqual(calls,['browser.get','capabilities','create']);
  }
});

test('malformed native creation receipts remain uncertain and sanitize error details',async()=>{
  for(const native_create of [undefined,{}, {navigation_may_have_occurred:false}]){
    await assert.rejects(ensureNativeBrowser({browser:{id:'tab'},clientId:'android',sdk:{},bridge:{async call(){return{ok:false,error:{code:'private URL https://example.invalid/'},native_create};}}}),
      error=>error.code==='native_create_failed'&&error.creation_reason==='native_create_result_invalid'&&error.effect_may_have_occurred===true&&
        error.native_create.network_cleanup_confirmed===false&&Object.isFrozen(error.native_create));
  }
});

test('asynchronous initial navigation admission returns without claiming page completion',async()=>{
  const calls=[],receipt={created:true,downloads_intercepted:true,initial_navigation_pending:true,navigation_ref:'nv-original'};
  const result=await ensureNativeBrowser({browser:{id:'tab',download_policy:'intercept'},bridge:{async call(command){calls.push(command);return command==='capabilities'?{initial_download_interception:true}:receipt;}}});
  assert.equal(result,receipt);assert.deepEqual(calls,['capabilities','create']);
});

test('changed original display cancels only its original admitted navigation',async()=>{
  for(const rejectCleanup of [false,true]){
    let live=true;const calls=[];
    await assert.rejects(ensureNativeBrowser({browser:{id:'tab'},alive:()=>live,bridge:{async call(command,params,control){calls.push(command);
      if(command==='create'){live=false;return{created:true,downloads_intercepted:true,initial_navigation_pending:true,navigation_ref:'nv-original'};}
      assert.equal(command,'navigation_cancel');assert.deepEqual(params,{browser:'tab',agent:true,navigation_ref:'nv-original'});assert.equal(control.timeout,1000);
      if(rejectCleanup)throw Error('Cleanup transport is gone');return{cancelled:true};
    }}}),error=>error.name==='AbortError');
    assert.deepEqual(calls,['create','navigation_cancel']);
  }
});

test('inconsistent initial native admission is uncertain and never replays or guesses cleanup',async()=>{
  for(const fields of [{navigation_ref:''},{navigation_ref:'nv-unsafe URL'},{navigation_ref:'nv-original',created:false},{navigation_ref:'nv-original',downloads_intercepted:false},{initial_navigation_pending:true,navigation_ref:undefined}]){
    const calls=[];await assert.rejects(ensureNativeBrowser({browser:{id:'tab'},bridge:{async call(command){calls.push(command);return{created:true,downloads_intercepted:true,initial_navigation_pending:true,...fields};}}}),
      error=>error.code==='native_create_failed'&&error.creation_reason==='native_create_result_invalid'&&error.effect_may_have_occurred===true);
    assert.deepEqual(calls,['create']);
  }
});
