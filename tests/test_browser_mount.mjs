import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createBrowserViewLifetime } from '../host/browser-ownership.js';

// Execute the applied host wrapper, not a maintained copy of its behavior.
const source=readFileSync(new URL('../.mdo/source/app/web/js/features/workbench/panel-manager.js',import.meta.url),'utf8');
const body=source.slice(source.indexOf('  async function browserView('),source.indexOf('  async function mount(row)'));
function fixture({preview=false}={}) {
  const row={id:'panel',type:preview?'mdo.preview':'mdo.browser',owner:'extension',client:'display',context:{device:'device',project:'project',session:'task'},...(preview?{params:{url:'https://example.test'}}:{resource:{id:'browser'}})};
  const nativeRow=preview?{...row,resource:{id:'browser'}}:row;
  const controller=new AbortController(),mount={abort:controller,browserId:'browser'},mounts=new Map([['panel',mount]]),entries=new Map([['panel',row]]),views=new Map(),closed=[],reconciled=[],titles=[];
  let resolve,options,currentTarget='local';
  const factory=new Function('mountBrowserView','bound','bridge','clientId','layout','navigation','update','layoutApi','addContext','browserViews','close','popupManager','isRemoteTarget','targetState','createBrowserViewLifetime','entries','mounts',
    `let stopped=false;const displayTarget='local';${body};return {browserView,stop(){stopped=true;}};`);
  const host=factory(async(root,value)=>{options=value;return new Promise(done=>{resolve=done;});},value=>({context:value.context}),{},'display',{open:true},{get:()=>({view:'chat'})},
    async(id,patch,current=()=>true)=>{if(current())titles.push({id,patch});},{call(){throw Error('No external navigation');}},()=>{},views,
    async(id,current=()=>true)=>{if(current())closed.push(id);},{reconcile:id=>reconciled.push(id)},()=>currentTarget!=='local',()=>({selected:{id:currentTarget}}),createBrowserViewLifetime,entries,mounts);
  const execute=async()=>({state:'original'}),view={id:'browser',execute};
  const pending=host.browserView({hidden:false},nativeRow,controller.signal,mount);
  return{...host,row,nativeRow,controller,mount,mounts,entries,views,view,pending,closed,reconciled,titles,options:()=>options,ready:()=>resolve(view),target:value=>{currentTarget=value;}};
}

for(const cause of ['mount','target','scope','resource','stop'])test(`late native mount cannot publish after ${cause} changes`,async()=>{
  const f=fixture();
  if(cause==='mount')f.mounts.set('panel',{abort:new AbortController()});
  if(cause==='target')f.target('another-device');if(cause==='scope')f.entries.set('panel',{...f.row,context:{...f.row.context,session:'foreign'}});
  if(cause==='resource')f.entries.set('panel',{...f.row,resource:{id:'replacement'}});if(cause==='stop')f.stop();
  f.ready();await f.pending;assert.equal(f.views.size,0);assert.equal(f.reconciled.length,0);assert(!f.options().alive());
});

test('late native mount preserves a newer view of the same task',async()=>{
  const f=fixture(),replacement={row:f.row,execute:async()=>({})};f.views.set('browser',replacement);f.ready();await f.pending;
  assert.equal(f.views.get('browser'),replacement);assert.equal(f.reconciled.length,0);
});

for(const cause of ['abort','mount','record','resource','scope','target','stop'])test(`stale end callback cannot close its ${cause} replacement`,async()=>{
  const f=fixture();f.ready();await f.pending;const original=f.views.get('browser');let expected=original;
  if(cause==='abort')f.controller.abort();if(cause==='mount')f.mounts.set('panel',{abort:new AbortController()});
  if(cause==='record'){expected={row:f.row,execute:async()=>({})};f.views.set('browser',expected);}
  if(cause==='resource')f.entries.set('panel',{...f.row,resource:{id:'replacement'}});
  if(cause==='scope')f.entries.set('panel',{...f.row,context:{...f.row.context,session:'foreign'}});
  if(cause==='target')f.target('another-device');if(cause==='stop')f.stop();await f.options().onEnd('browser');
  assert.equal(f.views.get('browser'),expected);assert.equal(f.closed.length,0);
});

test('ending one valid browser is idempotent and never accepts another native ID',async()=>{
  const f=fixture();f.ready();await f.pending;
  await f.options().onEnd('foreign');assert(f.views.has('browser'));assert.equal(f.closed.length,0);
  await f.options().onEnd('browser');await f.options().onEnd('browser');assert.equal(f.views.size,0);assert.deepEqual(f.closed,['panel']);assert(!f.options().alive());
});

test('preview and metadata refresh keep the original mounted owner',async()=>{
  for(const preview of [false,true]){
    const f=fixture({preview});f.entries.set('panel',{...f.row,title:'Refreshed',revision:8});f.ready();await f.pending;
    assert(f.views.has('browser'));assert(f.options().alive());assert.deepEqual(f.reconciled,['browser']);await f.options().onEnd('browser');assert.deepEqual(f.closed,['panel']);
  }
});

test('unmount revokes UI callbacks without revoking the shared native dispatcher',async()=>{
  const f=fixture();f.ready();await f.pending;const registered=f.views.get('browser');assert.deepEqual(Object.keys(registered).sort(),['execute','row']);
  f.controller.abort();assert(!f.options().alive());assert.equal(f.views.get('browser'),registered);assert.equal((await registered.execute('state')).state,'original');
  await f.options().updateTitle('stale');assert.equal(f.titles.length,0);assert.equal(f.closed.length,0);
});

test('a native result cannot register a different browser identity',async()=>{
  const f=fixture();f.view.id='foreign';f.ready();await f.pending;
  assert.equal(f.views.size,0);assert.equal(f.reconciled.length,0);await f.options().onEnd('foreign');assert.equal(f.closed.length,0);
});

function updating(){
  const text=source.slice(source.indexOf('  const update = async'),source.indexOf('  const handle ='));
  const original={id:'panel',revision:1},saved={id:'panel',revision:2,title:'Original result'},entries=new Map([['panel',original]]),effects=[];let resolve,requests=0;
  const factory=new Function('entries','bound','remember','render',`let stopped=false;${text};return{update,stop(){stopped=true;}};`);
  return{...factory(entries,row=>({call(method,params){assert.equal(row,original);assert.equal(method,'panel.update');assert.equal(params.revision,1);requests++;return new Promise(done=>{resolve=done;});}}),()=>effects.push('remember'),()=>effects.push('render')),
    entries,original,saved,effects,requests:()=>requests,resolve:()=>resolve(saved)};
}
for(const cause of ['replacement','owner','stop','removal'])test(`actual panel update ignores its late result after ${cause}`,async()=>{
  const f=updating();let current=true;const pending=f.update('panel',{title:'Original result'},()=>current);assert.equal(f.requests(),1);let expected=f.original;
  if(cause==='replacement'){expected={id:'panel',revision:2,title:'New owner'};f.entries.set('panel',expected);}
  if(cause==='owner')current=false;if(cause==='stop')f.stop();if(cause==='removal'){f.entries.delete('panel');expected=undefined;}
  f.resolve();assert.equal(await pending,undefined);assert.equal(f.entries.get('panel'),expected);assert.equal(f.effects.length,0);
});
test('actual panel update preserves its saved result for a current owner',async()=>{
  const f=updating(),pending=f.update('panel',{title:'Original result'},()=>true);f.resolve();assert.equal(await pending,f.saved);assert.equal(f.entries.get('panel'),f.saved);assert.deepEqual(f.effects,['remember','render']);
});
