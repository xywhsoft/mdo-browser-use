import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import {interruptedToolNotice} from '../host/tool-recovery.js';
// Actual applied timeline projection and real durable SDK cancellation receipt.
// Run the Python managed-terminal regression first, as in the sequential suite.
const source=readFileSync(new URL('../.mdo/source/app/web/js/features/chat/timeline.js',import.meta.url),'utf8');
const project=new Function('t','interruptedToolNotice',source.replace(/^import .+;\r?$/gm,'').replace(/^export /gm,'')+';return eventsToTimeline;')
  ((key,values,fallback)=>fallback||key,interruptedToolNotice);
const receipt=JSON.parse(readFileSync(new URL('../.mdo/managed-tool-terminal-result.json',import.meta.url),'utf8'));
test('actual durable cancelled SDK call projects to one failed tool with an output',()=>{
  assert.deepEqual(receipt.events.map(r=>r.kind),['tool_start','tool_done']);
  const rows=project(receipt.events).filter(r=>r.kind==='tool');
  assert.equal(rows.length,1);assert.equal(rows[0].meta,receipt.operation);
  assert.equal(rows[0].state,'failed');assert.match(rows[0].outputText,/cancel/i);
  assert.equal(rows[0].outputEventId,receipt.events[1].event_id);
  assert(receipt.events.every(r=>r.run_id===0));
});
test('closing one original SDK call cannot settle a different call in the same run zero',()=>{
  const [start,end]=receipt.events;
  const foreign={...start,event_id:start.event_id+1,tool_call_id:'other-sdk-call'};
  const rows=project([start,foreign,{...end,event_id:start.event_id+2}]).filter(r=>r.kind==='tool');
  assert.equal(rows.find(r=>r.meta===receipt.operation).state,'failed');
  assert.equal(rows.find(r=>r.meta==='other-sdk-call').state,'running');
});
