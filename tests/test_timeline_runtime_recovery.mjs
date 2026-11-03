import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import test from 'node:test';
import {createResourceStore} from '../.mdo/source/app/web/js/state/store.js';
import {createRequestRecovery} from '../.mdo/source/app/web/js/api/request-recovery.js';
import {createSessionCache} from '../.mdo/source/app/web/js/features/chat/session-cache.js';
import {decodeConversationPage} from '../.mdo/source/app/web/js/features/chat/conversation-page.js';
import {mergeConversationTurns,summarizeConversationEvents} from '../.mdo/source/app/web/js/features/chat/conversation-history.js';

test('runtime replacement requalifies unchanged event IDs instead of keeping a stale running cache',async()=>{
  const source=readFileSync(new URL('../.mdo/source/app/web/js/features/chat/timeline-store.js',import.meta.url),'utf8');
  const receipt=JSON.parse(readFileSync(new URL('../.mdo/sdk-tool-restart-result.json',import.meta.url),'utf8'));
  const session=receipt.target_session,epoch='e'.repeat(64),trace=[];let changed=false,listener,cache;
  const sha=text=>createHash('sha256').update(text).digest('hex');
  const api={get:async path=>{
    trace.push(path);const event={...receipt.events[0],projection_epoch:epoch,
      node_id:`${session}:${epoch}:${receipt.events[0].event_id}`,content_hash:sha(receipt.events[0].text)};
    if(!changed)delete event.tool_outcome;
    const items_json=JSON.stringify([event]);
    return{data:{project_id:'default',session_id:session,epoch,items_json,items_hash:sha(items_json),
      delta:false,has_more:false,next_cursor:event.event_id,latest_event_id:event.event_id,
      next_before:event.event_id,history_lost:false}};
  }};
  const live={subscribe:fn=>{listener=fn;},select(){},isConnected:()=>true};
  const timers=new Map();let timer=0;
  const window={addEventListener(){},setTimeout(fn){timers.set(++timer,fn);return timer;},clearTimeout(id){timers.delete(id);}};
  const document={hidden:false,addEventListener(){}};
  const names=['liveConnection','api','resourceId','isTransientReadError','createRequestRecovery','createResourceStore',
    'mergeConversationTurns','summarizeConversationEvents','t','targetState','subscribeTarget','createSessionCache',
    'decodeConversationPage','window','document'];
  const factory=new Function(...names,source.replace(/^import .+;\r?$/gm,'').replace(/^export /gm,'')+';return {reloadTimeline,timelineStore};');
  const instance=factory(live,api,value=>value,()=>false,createRequestRecovery,
    (value,options)=>createResourceStore(value,{...options,pageEvents:window}),mergeConversationTurns,summarizeConversationEvents,
    (key,args,fallback)=>fallback||key,()=>({selected:null}),()=>{},options=>(cache=createSessionCache(options)),decodeConversationPage,window,document);
  await instance.reloadTimeline('default',session);
  assert.equal(trace.length,1);assert.equal(instance.timelineStore.get().data.events[0].tool_outcome,undefined);
  changed=true;listener({type:'runtime_changed',reason:'restart'});
  assert.equal(cache.usage().entries,0,'A forced restart reload reinserted the old running snapshot');
  for(let i=0;i<20&&!instance.timelineStore.get().data.events[0]?.tool_outcome;i++)await new Promise(setImmediate);
  assert.equal(trace.length,2);assert(trace.every(path=>path.endsWith('/conversation?limit=1')));
  const updated=instance.timelineStore.get().data.events[0];
  assert.equal(updated.event_id,receipt.events[0].event_id);assert.equal(updated.tool_outcome.state,'interrupted');
  assert.equal(instance.timelineStore.get().data.initializing,false);
});
