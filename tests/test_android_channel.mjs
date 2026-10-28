import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {test} from 'node:test';

// Extract the script actually compiled into the applied Android host, rather
// than a rewritten fixture. This is a JS protocol gate, not a WebView test.
const java=await readFile(new URL('../.mdo/source/tools/android/MdoBrowser.java',import.meta.url),'utf8');
const fragment=java.match(/String script=([\s\S]*?);\s*final Uri origin=/)?.[1];
assert.ok(fragment,'Applied Android main-document bootstrap was not found');
const script=[...fragment.matchAll(/"(?:\\.|[^"\\])*"/g)].map(value=>JSON.parse(value[0])).join('');

function fixture(){
  const listeners=new Set(),events=[];
  const window={
    addEventListener(name,callback){assert.equal(name,'message');listeners.add(callback);},
    removeEventListener(name,callback){assert.equal(name,'message');listeners.delete(callback);},
    dispatchEvent(event){events.push(event);},
  };
  const context=vm.createContext({window,Event:class{constructor(type){this.type=type;}},
    CustomEvent:class{constructor(type,init){this.type=type;this.detail=init.detail;}}});
  return{window,events,listeners,install(){vm.runInContext(script,context);},
    emit(data,ports,source=null){for(const listener of [...listeners])listener({data,ports,source});}};
}

test('repeated native bootstrap keeps one listener and admits only a native main-document transfer',()=>{
  const f=fixture(),sent=[],port={postMessage:value=>sent.push(value)};
  for(let i=0;i<128;i++){f.install();assert.equal(f.listeners.size,1);}
  f.emit('mdo-browser-init',[port],{});f.emit('wrong-tag',[port]);f.emit('mdo-browser-init',[]);f.emit('mdo-browser-init',[port,port]);
  assert.equal(f.window.XsBrowser,undefined);assert.equal(f.listeners.size,1);assert.equal(f.events.length,0);
  f.emit('mdo-browser-init',[port]);assert.equal(f.listeners.size,0);assert.equal(f.window.__mdoBrowserInitListener,undefined);
  assert.deepEqual(f.events.map(event=>event.type),['mdo-browser-ready']);
  f.window.XsBrowser.request('{"id":"original"}');assert.deepEqual(sent,['{"id":"original"}']);
  port.onmessage({data:'original-result'});assert.equal(f.events[1].type,'mdo-browser-result');assert.equal(f.events[1].detail,'original-result');
  f.emit('mdo-browser-init',[{postMessage(){assert.fail('Already consumed bootstrap received a second port');}}]);
  assert.equal(f.events.length,2);
});

test('a fresh bootstrap replaces the old JS route without accumulating callbacks',()=>{
  const f=fixture(),old=[],fresh=[];f.install();f.emit('mdo-browser-init',[{postMessage:value=>old.push(value)}]);
  f.window.XsBrowser.request('first');f.install();assert.equal(f.listeners.size,1);
  f.emit('mdo-browser-init',[{postMessage:value=>fresh.push(value)}]);f.window.XsBrowser.request('second');
  assert.deepEqual(old,['first']);assert.deepEqual(fresh,['second']);assert.equal(f.listeners.size,0);
  assert.equal(f.events.filter(event=>event.type==='mdo-browser-ready').length,2);
});
