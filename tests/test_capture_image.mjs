import assert from "node:assert/strict";
import { test } from "node:test";
import { prepareScreenshot } from "../host/capture-image.js";
import { readFile } from 'node:fs/promises';
import { webcrypto } from 'node:crypto';
import vm from 'node:vm';

test("invalid and oversized image envelopes are rejected before bitmap decoding",async()=>{
  let decodes=0;const previous=globalThis.createImageBitmap;globalThis.createImageBitmap=()=>{decodes++;throw Error("Must not decode oversized images");};
  try{
    await assert.rejects(prepareScreenshot({data:"!"},{},{},{format:"png"}),/invalid byte envelope/);
    const png=new Uint8Array(33);png.set([137,80,78,71,13,10,26,10]);
    const view=new DataView(png.buffer);view.setUint32(16,65535);view.setUint32(20,65535);
    await assert.rejects(prepareScreenshot({data:Buffer.from(png).toString("base64")},{},{},{format:"png"}),/raster budget/);
    assert.equal(decodes,0);
  }finally{globalThis.createImageBitmap=previous;}
});

test('rendered private controls remain masked when inert, aria-hidden or CSS overrides hidden',async()=>{
  const source=(await readFile(new URL('../page/runtime.js',import.meta.url),'utf8')).replace('export function browserRuntime(','function browserRuntime(');
  const document={defaultView:{getComputedStyle:()=>({display:'block',visibility:'visible',opacity:'1'})}},controls=[];
  for(let index=0;index<3;index++)controls.push({ownerDocument:document,isConnected:true,localName:'input',type:'text',
    closest:()=>({semanticHidden:true}),getAttribute:()=>null,hasAttribute:()=>false,
    getClientRects:()=>[{}],getBoundingClientRect:()=>({x:20,y:20+index*40,width:100,height:24})});
  document.createTreeWalker=()=>{let index=0;return{nextNode:()=>controls[index++]||null};};
  const context=vm.createContext({document,crypto:{getRandomValues:webcrypto.getRandomValues.bind(webcrypto)},performance,
    visualViewport:{scale:1},innerWidth:800,innerHeight:600,scrollX:0,scrollY:0,devicePixelRatio:1});
  const runtime=vm.runInContext('('+source+')',context)(),result=runtime.captureArea({mode:'viewport'});
  assert(result.ok);assert.equal(result.redactions.length,3);
  for(let index=0;index<3;index++)assert.equal(result.redactions[index].y,20+index*40);
});
