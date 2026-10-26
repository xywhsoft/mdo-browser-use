import assert from "node:assert/strict";
import { test } from "node:test";
import { prepareScreenshot } from "../host/capture-image.js";

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
