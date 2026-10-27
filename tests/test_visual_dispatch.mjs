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
  const state={input:[],drags:[],native:[],pins:null,reads:0,area,after:null,capability:true,dragCapability:true,viewNavigation:'7'};
  const args={browser:'browser',params:{op:'click',document:doc,target:{image:reference.id,x:20,y:30}},store,action:{id:'visual-fixture'},
    deadline:performance.now()+10000,
    view:{async execute(command,params){state.native.push({command,params});return{};}},
    read:async(method,options)=>method==='captureArea'?(++state.reads>1&&state.after||state.area):method==='visualPoint'?{ok:true,document:doc,point:options.point,frame_chain:[{frame:'f0',document:fd}]}:{ok:true,document:doc},
    guard:async()=>({state:{frame_snapshot_input:state.capability,frame_snapshot_drag:state.dragCapability,navigation:state.viewNavigation}}),
    send:async params=>{state.input.push(params);return null;},
    sendDrag:async params=>{state.drags.push(params);return{drag_kind:'pointer'};},
    fail:(code,message)=>({handled:true,result:{ok:false,error:{code,message,effect_may_have_occurred:state.input.length+state.drags.length>0}}}),
    setSnapshot:(snapshot,root)=>{state.pins={snapshot,root};},
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
