import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

// Execute the actual Java constant, rather than maintaining another copy of
// its script in the test. This checks protocol JS, not WebView world isolation.
const source=readFileSync(new URL('../host/android/MdoBrowserDocument.java',import.meta.url),'utf8');
const world=readFileSync(new URL('../host/android/MdoBrowserWorld.java',import.meta.url),'utf8');
const hello=JSON.parse(source.match(/\bHELLO=("(?:\\.|[^"\\])*")/)[1]);
const listener=JSON.parse(world.match(/\bLISTENER=("(?:\\.|[^"\\])*")/)[1]);
const expression=source.match(/\bBOOTSTRAP=([\s\S]+?);\s*private final/)[1];
const tokens=[];let offset=0;
for(const match of expression.matchAll(/"(?:\\.|[^"\\])*"|MdoBrowserWorld\.LISTENER|HELLO|\+/g)){
  assert.equal(expression.slice(offset,match.index).trim(),'');tokens.push(match[0]);offset=match.index+match[0].length;
}
assert.equal(expression.slice(offset).trim(),'');
const bootstrap=tokens.filter(token=>token!=='+').map(token=>token==='HELLO'?hello:
  token==='MdoBrowserWorld.LISTENER'?listener:JSON.parse(token)).join('');

test('Android document bootstrap announces once and fixes its private marker',()=>{
  const messages=[];const context=vm.createContext({[listener]:{postMessage(value){messages.push(value);}}});
  vm.runInContext('self=globalThis;top=globalThis',context);
  for(let count=0;count<128;count++)vm.runInContext(bootstrap,context);
  assert.deepEqual(messages,[hello]);
  assert.equal(vm.runInContext('Object.getOwnPropertyDescriptor(globalThis,"__mdoBrowserDocumentReady").writable',context),false);
  assert.equal(vm.runInContext('Object.getOwnPropertyDescriptor(globalThis,"__mdoBrowserDocumentReady").configurable',context),false);
});

test('Android document bootstrap sends no subframe announcement or marker',()=>{
  const messages=[];const context=vm.createContext({[listener]:{postMessage(value){messages.push(value);}}});
  vm.runInContext('self=globalThis;top={}',context);
  vm.runInContext(bootstrap,context);
  assert.deepEqual(messages,[]);
  assert.equal(vm.runInContext('Object.hasOwn(globalThis,"__mdoBrowserDocumentReady")',context),false);
});
