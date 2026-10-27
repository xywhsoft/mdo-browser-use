import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {test} from 'node:test';

const source=(await readFile(new URL('../page/runtime.js',import.meta.url),'utf8')).replace('export function browserRuntime(','function browserRuntime(');
test('page identities remain stable and cryptographically distinct without randomUUID',()=>{
  const context=vm.createContext({document:{},crypto:{getRandomValues:webcrypto.getRandomValues.bind(webcrypto)}});
  const runtime=vm.runInContext('('+source+')',context),first=runtime({local_frame:true});
  assert.equal(runtime({local_frame:true}),first);assert.equal(context.crypto.randomUUID,undefined);
  const ids=new Set([first.document]);
  for(let index=0;index<100;index++){
    context.document={};const current=runtime({local_frame:true});
    assert.match(current.document,/^d-[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/);
    assert(!ids.has(current.document));ids.add(current.document);
  }
});

test('absence of cryptographic randomness never falls back to a weak identity',()=>{
  const context=vm.createContext({document:{},crypto:{}}),runtime=vm.runInContext('('+source+')',context);
  assert.throws(()=>runtime({local_frame:true}));
});
