import assert from 'node:assert/strict';
import {test} from 'node:test';
import {createDownloadUserActions} from '../host/download-user.js';

function fixture(options={}){
  const calls=[],context={extension:'owner',device:'local',project:'original',session:'original-session',workspace:'D:\\original',namespace:'subtask',member:'worker'};
  const hash='e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
  let bound,receive,publish,destination;const controller=new AbortController();
  const api={async call(method,p={}){
    calls.push({...p,method,target_method:p.method});
    if(method==='files.stat')return{revision:'root'};
    if(method==='sdk.context')return context;
    if(method==='stream.open')return{handle:'str-'+'1'.repeat(32)};
    if(method==='operation.start'){
      if(p.method==='files.receive.start'){receive=p.id;destination=p.params.destination;}
      else publish=p.id;
      if(options.lostStart)throw Object.assign(Error('private path should not be shown'),{code:'connection_lost'});
      return{id:p.id};
    }
    if(method==='operation.get')return{terminal:true,state:'succeeded'};
    if(method==='operation.result')return p.id===receive?{id:receive,state:'ready',source:'.mdo-receive-'+receive.slice(4)+'.part',
      workspace:context.workspace,root_snapshot:'root',destination,source_revision:'source',size:0,sha256:hash}:
      {published:true,path:destination,size:0,sha256:hash,revision:'file',verdict:'verified'};
    if(method==='files.receive.list')return[];
    return{};
  }};
  const sdk={bind(scope){bound={...scope};return api;}};
  const execute=async(command,p={})=>{
    calls.push({native:command,...p});
    if(command==='state')return{manual:true,download_capability:true};
    return{id:'download',state:'completed',size:0,sha256:hash};
  };
  const browser={id:'manual-ui-qa',owner:context.extension,context:{...context,...options.context}};
  return{calls,context,controller,get bound(){return bound;},actions:createDownloadUserActions({browser,sdk,execute,signal:controller.signal,alive:options.alive})};
}

test('trusted manual save stays in its original owner/task/private workspace and never takes over',async()=>{
  const f=fixture();try{
    assert.deepEqual(f.bound,f.context);const result=await f.actions.save('download','empty.bin');assert.equal(result.saved,true);
    assert(f.calls.filter(c=>c.native==='downloads').every(c=>c.agent===false));assert(!f.calls.some(c=>c.native==='takeover'));
    assert.equal(f.calls.find(c=>c.target_method==='files.receive.start').params.destination,'empty.bin');
  }finally{f.actions.dispose();}
});

test('missing legacy roots and changed displaying devices fail before a file lookup',async()=>{
  for(const options of [{context:{workspace:''}},{alive:()=>false}]){
    const f=fixture(options);try{await assert.rejects(f.actions.save('download','empty.bin'));assert(!f.calls.some(c=>c.method==='files.stat'));}
    finally{f.actions.dispose();}
  }
});

test('review uses a fresh explicit SDK identity and does not replay publication',async()=>{
  const f=fixture();try{
    await f.actions.reconcile('sdk-'+'a'.repeat(32));await f.actions.reconcile('sdk-'+'a'.repeat(32));
    const jobs=f.calls.filter(c=>c.method==='operation.start');assert.equal(jobs.length,2);assert.notEqual(jobs[0].id,jobs[1].id);
    assert(jobs.every(c=>c.target_method==='files.receive.reconcile'));assert(!f.calls.some(c=>c.target_method==='files.receive.publish'));
  }finally{f.actions.dispose();}
});

test('lost recovery admission is cancelled by exact identity without retry or leaked paths',async()=>{
  const f=fixture({lostStart:true});try{
    await assert.rejects(f.actions.publish('sdk-'+'a'.repeat(32),'new.bin'),cause=>cause.code==='connection_lost'&&!cause.message.includes('private path'));
    const start=f.calls.filter(c=>c.method==='operation.start');assert.equal(start.length,1);
    assert.equal(f.calls.find(c=>c.method==='operation.cancel').id,start[0].id);assert.equal(f.actions.busy,false);
  }finally{f.actions.dispose();}
});

test('disposed panels cannot inspect or publish retained task data',async()=>{
  const f=fixture();f.actions.dispose();await assert.rejects(f.actions.inspect(),{code:'context_changed'});
  await assert.rejects(f.actions.publish('sdk-'+'a'.repeat(32),'new.bin'),{code:'context_changed'});assert.equal(f.calls.length,0);
});
