import assert from 'node:assert/strict';
import {test} from 'node:test';
import {executeVisualAction} from '../host/visual-dispatch.js';
import {createVisualSnapshotStore,hashVisualRaster} from '../host/visual-snapshot.js';

const doc='d-'+ '1'.repeat(8)+'-1111-1111-1111-'+ '1'.repeat(12),fd='fd-'+ '2'.repeat(8)+'-2222-2222-2222-'+ '2'.repeat(12);
async function fixture(){
  const data=new Uint8ClampedArray(128*128*4);data.fill(42);
  const canvas={width:128,height:128,getContext:()=>({getImageData(left,top,width,height){
    const pixels=new Uint8ClampedArray(width*height*4);
    for(let y=0;y<height;y++)pixels.set(data.subarray(((top+y)*128+left)*4,((top+y)*128+left+width)*4),y*width*4);
    return{data:pixels};
  }})};
  const area={ok:true,document:doc,frame_stamp:JSON.stringify([['f0',fd,null,false,true,'readable',doc,{x:100,y:200},{width:128,height:128},null]]),
    region:{x:100,y:200,width:128,height:128},scroll:{x:100,y:200},viewport:{width:128,height:128},device_pixel_ratio:1,redactions:[]};
  const proof={document:doc,navigation:'7',frame_stamp:area.frame_stamp,region:area.region,redactions:[],options:{mode:'viewport',document:doc},
    device_pixel_ratio:1,capture:{format:'png',max_width:128,max_height:128,max_bytes:131072}};
  const store=createVisualSnapshotStore(),reference=store.issue('browser',proof,await hashVisualRaster(canvas));
  const state={input:[],drags:[],native:[],pins:null,reads:0,area,after:null,capability:true,dragCapability:true,keyboardCapability:true,focused:true,viewNavigation:'7'};
  const args={browser:'browser',params:{op:'click',document:doc,target:{image:reference.id,x:20,y:30}},store,action:{id:'visual-fixture'},
    deadline:performance.now()+10000,
    view:{async execute(command,params){state.native.push({command,params});return{};}},
    read:async(method,options)=>method==='captureArea'?(++state.reads>1&&state.after||state.area):method==='visualPoint'?{ok:true,document:doc,point:options.point,frame_chain:[{frame:'f0',document:fd}],...(options.op==='key'?{target:{ref:`r1|f0|${fd}|${doc}|e1`},native_target:{document:doc,ref:'e1'}}:{})}:{ok:true,document:doc,focused:state.focused},
    guard:async()=>({state:{frame_snapshot_input:state.capability,frame_snapshot_drag:state.dragCapability,frame_snapshot_keyboard:state.keyboardCapability,navigation:state.viewNavigation}}),
    send:async params=>{state.input.push(params);return null;},
    sendDrag:async params=>{state.drags.push(params);return{drag_kind:'pointer'};},
    fail:(code,message)=>({handled:true,result:{ok:false,error:{code,message,effect_may_have_occurred:state.input.length+state.drags.length>0}}}),
    setSnapshot:(snapshot,root,chain)=>{state.pins={snapshot,root,chain};},
    render:async(_native,_area,_after,_params,inspect)=>{await inspect.raster(canvas);return{};}};
  return{args,state,store,reference,data,proof,canvas,run:overrides=>executeVisualAction({...args,...overrides})};
}

test('a validated image point maps through crop/scroll and consumes its exact lease once',async()=>{
  const f=await fixture(),out=await f.run();assert(out.result.ok&&out.result.scene_validated);
  assert.equal(out.result.verification,'native_delivery');assert.deepEqual(f.state.input[0],{kind:'click',x:20,y:30,button:'left',click_count:1,dx:0,dy:0});
  assert.deepEqual(f.state.pins.snapshot,[{frame:'f0',document:fd,parent:''}]);assert.equal(f.state.pins.root.frame,'f0');
  assert.equal((await f.run()).result.error.code,'visual_reference_expired');assert.equal(f.state.input.length,1);
  assert(!JSON.stringify(out).includes('hashes'));assert(!JSON.stringify(out).includes('frame_stamp'));
});

test('visual validation keeps a lossless private source for public JPEG and small output limits',async()=>{
  for(const format of ['png','jpeg']){
    const f=await fixture(),proof={...f.proof,capture:{...f.proof.capture,format,max_bytes:8192}};
    const reference=f.store.issue('browser',proof,await hashVisualRaster(f.canvas));f.args.params.target.image=reference.id;
    const result=await f.run();assert(result.result.ok);const capture=f.state.native.find(row=>row.command==='capture');
    assert.equal(capture.params.format,'png');assert.equal(capture.params.max_bytes,262144);
    assert.equal(proof.capture.format,format);assert.equal(proof.capture.max_bytes,8192);
  }
});

test('Android visual validation carries its exact native frame context and refuses changed or failed receipts',async()=>{
  for(const mode of ['ok','document','native_document','navigation','capture_key','failed','lossy']){
    const f=await fixture(),context={adapter:'android-native-frames-v1',native_document:fd,document:doc,navigation:7,
      viewport:f.state.area.viewport,scroll:f.state.area.scroll,device_pixel_ratio:1,frame_stamp:f.state.area.frame_stamp};
    f.state.area.native_capture=context;f.args.view.execute=async(command,params)=>{
      f.state.native.push({command,params});if(command!=='capture')return{};assert.deepEqual(params.capture_context,context);
      const reply={ok:true,document:doc,native_document:fd,navigation:7,capture_key:params.capture_key,format:'png'};
      if(['document','native_document','capture_key'].includes(mode))reply[mode]='changed';
      if(mode==='navigation')reply.navigation=8;if(mode==='failed'){reply.ok=false;reply.error={code:'capture_changed'};}
      if(mode==='lossy')reply.format='jpeg';return reply;
    };
    const out=await f.run();assert.equal(out.result.ok,mode==='ok',mode);assert.equal(f.state.input.length,mode==='ok'?1:0);
    if(mode!=='ok')assert.equal(out.result.error.code,mode==='lossy'?'capture_source_invalid':'capture_changed');
  }
});

test('foreign, expired, wrong-document, unsupported and out-of-image points dispatch no input',async()=>{
  for(const mode of ['foreign','expired','document','navigation','unsupported','negative','edge','fractional','field']){
    const f=await fixture();let overrides={};
    if(mode==='foreign')overrides.browser='foreign';if(mode==='expired')f.store.release('browser');
    if(mode==='document')f.args.params.document='wrong';if(mode==='navigation')f.state.viewNavigation='8';
    if(mode==='unsupported')f.state.capability=false;if(mode==='negative')f.args.params.target.x=-1;
    if(mode==='edge')f.args.params.target.x=128;if(mode==='fractional')f.args.params.target.y=2.5;if(mode==='field')f.args.params.op='fill';
    const out=await f.run(overrides);assert.equal(out.result.ok,false,mode);assert.equal(out.result.error.effect_may_have_occurred,false);assert.equal(f.state.input.length,0,mode);
  }
});

test('scroll/layout/crop/DPR changes and source privacy masks are fenced before capture',async()=>{
  for(const mode of ['stamp','region','dpr','mask','outside']){
    const f=await fixture();
    if(mode==='stamp')f.state.area={...f.state.area,frame_stamp:'changed'};
    if(mode==='region')f.state.area={...f.state.area,region:{...f.state.area.region,x:102}};
    if(mode==='dpr')f.state.area={...f.state.area,device_pixel_ratio:2};
    if(mode==='mask')f.state.area={...f.state.area,redactions:[{x:120,y:230,width:10,height:10}]};
    if(mode==='outside')f.state.area={...f.state.area,scroll:{x:500,y:200}};
    const out=await f.run();assert.equal(out.result.ok,false,mode);assert.equal(f.state.input.length,0);assert(!f.state.native.some(row=>row.command==='capture'),mode);
  }
});

test('the final scan fences newly masked scrolled-document points and changed child documents',async()=>{
  for(const mode of ['mask','frame']){
    const f=await fixture();f.state.after={...f.state.area,...(mode==='mask'?{redactions:[{x:120,y:230,width:10,height:10}]}:{frame_stamp:'child-changed'})};
    assert.equal((await f.run()).result.error.code,'visual_context_changed');assert.equal(f.state.input.length,0);
  }
});

test('the selected tile must still match, while distant animation need not block a point',async()=>{
  const local=await fixture();local.data[(30*128+20)*4]=99;
  assert.equal((await local.run()).result.error.code,'visual_scene_changed');assert.equal(local.state.input.length,0);
  const distant=await fixture();distant.data[(100*128+110)*4]=99;assert((await distant.run()).result.ok);assert.equal(distant.state.input.length,1);
});

test('cancellation drains the original validation capture without publishing or sending input',async()=>{
  const f=await fixture();let capture=false,complete;
  f.args.view.execute=async(command,params)=>{f.state.native.push({command,params});if(command==='capture'){capture=true;return new Promise(resolve=>{complete=resolve;});}
    if(command==='capture_cancel')complete?.({});return{};};
  f.args.guard=async()=>capture?f.args.fail('cancelled','Synthetic originating cancellation'):{state:{frame_snapshot_input:true,navigation:'7'}};
  assert.equal((await f.run()).result.error.code,'cancelled');assert.equal(f.state.input.length,0);
  assert(f.state.native.some(row=>row.command==='capture_cancel'));assert(f.store.get('browser',f.reference.id));
});

test('a slow owned validation capture uses wait checkpoints and ordinary guards before input',async()=>{
  const f=await fixture();let pending=false,waits=0,ordinaryDuringCapture=0;
  const original=f.args.guard;
  f.args.guard=async()=>{if(pending){ordinaryDuringCapture++;return f.args.fail('browser_busy','Foreign native capture');}return original();};
  f.args.captureCheckpoint=async()=>{waits++;return original();};
  f.args.view.execute=async(command,params)=>{f.state.native.push({command,params});if(command==='capture'){
    pending=true;await new Promise(resolve=>setTimeout(resolve,100));pending=false;return{};}return{};};
  const out=await f.run();assert(out.result.ok);assert(waits>=2);assert.equal(ordinaryDuringCapture,0);assert.equal(f.state.input.length,1);
});

test('owned capture wait cancellation still drains its original request without consuming the image',async()=>{
  const f=await fixture();let pending=false,resolve;
  f.args.view.execute=async(command,params)=>{f.state.native.push({command,params});if(command==='capture'){pending=true;return new Promise(done=>{resolve=done;});}
    if(command==='capture_cancel'){pending=false;resolve?.({});}return{};};
  f.args.captureCheckpoint=async()=>pending?f.args.fail('cancelled','Original call cancelled'):f.args.guard();
  const out=await f.run();assert.equal(out.result.error.code,'cancelled');assert.equal(f.state.input.length,0);assert.equal(pending,false);
  assert(f.store.get('browser',f.reference.id));assert.equal(f.state.native.filter(row=>row.command==='capture_cancel').length,1);
});

test('visual drag validates two image neighborhoods and sends one fully pinned gesture',async()=>{
  const f=await fixture();f.args.params.op='drag';f.args.params.destination={image:f.reference.id,x:100,y:100};
  const result=await f.run();assert(result.result.ok&&result.result.scene_validated);assert.equal(result.result.drag_kind,'pointer');
  assert.equal(f.state.input.length,0);assert.equal(f.state.drags.length,1);
  assert.deepEqual(f.state.drags[0],{x:20,y:30,to_x:100,to_y:100,
    source_frame_chain:[{frame:'f0',document:fd}],destination_frame_chain:[{frame:'f0',document:fd}],frame_snapshot:[{frame:'f0',document:fd,parent:''}]});
  assert.equal(f.state.native.filter(row=>row.command==='capture').length,1);
  assert.equal((await f.run()).result.error.code,'visual_reference_expired');assert.equal(f.state.drags.length,1);
});

test('invalid, masked, changed or unpinned drop points cannot start a visual drag',async()=>{
  for(const mode of ['different-image','outside','same-point','old-host','mask','late-mask','changed-tile','wrong-chain']){
    const f=await fixture();f.args.params.op='drag';f.args.params.destination={image:f.reference.id,x:100,y:100};
    if(mode==='different-image')f.args.params.destination.image='vs-'+'f'.repeat(48);
    if(mode==='outside')f.args.params.destination.x=128;
    if(mode==='same-point')f.args.params.destination={...f.args.params.target};
    if(mode==='old-host')f.state.dragCapability=false;
    if(mode==='mask')f.state.area={...f.state.area,redactions:[{x:200,y:300,width:5,height:5}]};
    if(mode==='late-mask')f.state.after={...f.state.area,redactions:[{x:200,y:300,width:5,height:5}]};
    if(mode==='changed-tile')f.data[(100*128+100)*4]=99;
    if(mode==='wrong-chain'){const read=f.args.read;f.args.read=async(method,options)=>method==='visualPoint'?{ok:true,document:doc,point:options.point,frame_chain:[{frame:'f0',document:'wrong'}]}:read(method,options);}
    const result=await f.run();assert.equal(result.result.ok,false,mode);assert.equal(result.result.error.effect_may_have_occurred,false,mode);
    assert.equal(f.state.input.length+f.state.drags.length,0,mode);
  }
});

test('visual key consumes one image and pins its exact isolated focus target',async()=>{
  const f=await fixture();f.args.params.op='key';f.args.params.key='ArrowRight';
  const out=await f.run();assert(out.result.ok&&out.result.scene_validated);assert.equal(out.result.input_method,'native_key');
  assert.deepEqual(f.state.input.map(row=>row.kind),['focus','key']);
  assert.deepEqual(f.state.input[1],{kind:'key',key:'ArrowRight',focus_document:doc,focus_ref:'e1'});
  assert.deepEqual(f.state.pins.chain,[{frame:'f0',document:fd}]);
  assert(!JSON.stringify(out).includes('native_target'));assert(!JSON.stringify(out).includes('focus_ref'));
  assert.equal((await f.run()).result.error.code,'visual_reference_expired');assert.equal(f.state.input.length,2);
});

test('visual key refuses old hosts and invalid focus proofs before focusing',async()=>{
  for(const mode of ['old-host','missing-target','wrong-native','wrong-frame']){
    const f=await fixture();f.args.params.op='key';f.args.params.key='Enter';
    if(mode==='old-host')f.state.keyboardCapability=false;
    else{const read=f.args.read;f.args.read=async(method,options)=>{const value=await read(method,options);if(method==='visualPoint'){
      if(mode==='missing-target')delete value.target;if(mode==='wrong-native')value.native_target.ref='e99';if(mode==='wrong-frame')value.frame_chain[0].document='wrong';
    }return value;};}
    const out=await f.run();assert.equal(out.result.ok,false,mode);assert.equal(f.state.input.length,0,mode);assert.equal(out.result.error.effect_may_have_occurred,false);
  }
});

test('focus hijacking and cancellation after visual focus never dispatch its key',async()=>{
  for(const mode of ['focus','cancel','native']){
    const f=await fixture();f.args.params.op='key';f.args.params.key='Enter';
    if(mode==='focus')f.state.focused=false;
    if(mode==='cancel'){const read=f.args.read;f.args.read=async(method,options)=>method==='verify'?{ok:false,error:{code:'cancelled'}}:read(method,options);}
    if(mode==='native')f.args.send=async params=>{f.state.input.push(params);return f.args.fail('cancelled','Stopped after native focus acquisition');};
    const out=await f.run();assert(!out.result.ok);assert.equal(out.result.error.effect_may_have_occurred,true);assert.deepEqual(f.state.input.map(row=>row.kind),['focus']);
    assert.throws(()=>f.store.get('browser',f.reference.id),{code:'visual_reference_expired'});
  }
});
