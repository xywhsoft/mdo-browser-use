import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createBrowserUseClient} from '../sdk/client.mjs';

const operation='sdk-'+'a'.repeat(32);
test('JavaScript evaluation uses durable admission and retains exact iframe arguments',async()=>{
  const calls=[],params={browser:'original-browser',document:'original-root',frame:'original-frame',frame_document:'original-frame-document',world:'page',script:'return 42;'};
  const client=createBrowserUseClient({async call(name,args){calls.push({name,...args});
    if(args.action==='get')return{state:'succeeded',result:{ok:true,value:42}};
    return{};}});
  assert.equal((await client.evaluate(params,{operation})).value,42);
  assert.deepEqual(calls.map(call=>call.action),['submit','get','release']);
  assert.equal(calls[0].method,'browseruse.evaluate');assert.deepEqual(calls[0].arguments,params);
});
test('a lost evaluation admission reply is recovered by its original identity without resubmission',async()=>{
  let submissions=0,reads=0;
  const client=createBrowserUseClient({async call(name,args){
    if(args.action==='submit'){submissions++;throw Error('Controlled lost reply');}
    if(args.action==='get'){reads++;return{state:'succeeded',result:{ok:true,value:1}};}
    return{};}});
  await assert.rejects(client.evaluate({browser:'b',document:'d',script:'return 1;'},{operation}),error=>error.operation===operation);
  assert.equal((await client.recover(operation)).value,1);assert.equal(submissions,1);assert.equal(reads,1);
});
