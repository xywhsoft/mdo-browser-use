import {prepareScreenshot} from './capture-image.js';
import {visualSnapshots,visualPointUnchanged} from './visual-snapshot.js';

const supported=new Set(['click','double_click','right_click','middle_click','hover','scroll']);
const regionMatches=(a,b)=>a&&b&&['x','y','width','height'].every(key=>Number.isFinite(a[key])&&Number.isFinite(b[key])&&Math.abs(a[key]-b[key])<=.01);
const frame=/^(?:f0|f-[a-f0-9-]{36})$/i,document=/^fd-[a-f0-9-]{36}$/i;

// Fixed operation after shared C admission. Neither scripts nor native frame
// pins can be supplied by the model; proofs come from a completed image only.
export async function executeVisualAction({browser,params,view,read,guard,send,fail,deadline,action,setSnapshot,
  store=visualSnapshots,render=prepareScreenshot}){
  let key=null;
  try{
    if(!supported.has(params.op))return fail('unsupported_action','This visual path supports pointer gestures and wheel scrolling; use observed DOM targets for fields');
    const checked=await guard(true);if(checked.handled)return checked;
    if(checked.state.frame_snapshot_input!==true)return fail('unsupported_capability','This browser lacks native complete-frame visual input fences');
    if(checked.state.input_busy||checked.state.capture_busy)return fail('browser_busy','Native input or capture is still completing; inspect its receipt before visual input');
    const saved=store.get(browser,params.target.image),proof=saved.context,{x,y}=params.target;
    if(params.document!==proof.document||checked.state.navigation!==proof.navigation)return fail('visual_context_changed','The screenshot no longer belongs to this document and navigation');
    if(!Number.isInteger(x)||!Number.isInteger(y)||x<0||y<0||x>=saved.raster.width||y>=saved.raster.height)
      return fail('visual_point_invalid','Choose an integer point inside the delivered image dimensions');
    const area=await read('captureArea',proof.options);if(!area.ok)return{handled:true,result:area};
    const same=other=>other.frame_stamp===proof.frame_stamp&&regionMatches(other.region,proof.region)&&other.device_pixel_ratio===proof.device_pixel_ratio;
    if(!same(area))return fail('visual_context_changed','The document, frame layout, scroll or screenshot crop changed; request a fresh image');
    const documentPoint={x:proof.region.x+x*proof.region.width/saved.raster.width,y:proof.region.y+y*proof.region.height/saved.raster.height};
    const marginX=2*proof.region.width/saved.raster.width,marginY=2*proof.region.height/saved.raster.height;
    const masked=boxes=>boxes.some(box=>documentPoint.x>=box.x-marginX&&documentPoint.x<=box.x+box.width+marginX&&documentPoint.y>=box.y-marginY&&documentPoint.y<=box.y+box.height+marginY);
    if(masked(proof.redactions)||masked(area.redactions))return fail('visual_region_masked','This point is hidden by screenshot privacy masking; use an exact observed DOM target');
    const point={x:documentPoint.x-area.scroll.x,y:documentPoint.y-area.scroll.y};
    if(point.x<0||point.y<0||point.x>=area.viewport.width||point.y>=area.viewport.height)
      return fail('visual_point_outside_viewport','Scroll with a supported operation, then capture a fresh viewport image before clicking this point');
    const rows=JSON.parse(proof.frame_stamp);
    if(!Array.isArray(rows)||!rows.length||rows.length>32||rows.some(row=>!frame.test(row[0])||!document.test(row[1])||
       row[2]!==null&&!frame.test(row[2])||row[3]!==false||row[4]!==true)||new Set(rows.map(row=>row[0])).size!==rows.length)
      return fail('visual_frame_not_ready','The screenshot needs a complete ready native frame tree before visual input');
    const snapshot=rows.map(row=>({frame:row[0],document:row[1],parent:row[2]||''}));
    const root=snapshot.find(row=>row.frame==='f0');if(!root||root.parent)return fail('visual_frame_not_ready','The screenshot has no original native root');
    const capture=proof.capture;if(!capture)return fail('visual_reference_expired','Request a new screenshot with its capture parameters before visual input');
    const scale=Math.min(1,capture.max_width/(area.region.width*area.device_pixel_ratio),capture.max_height/(area.region.height*area.device_pixel_ratio));
    let settled=false,native,failure;key=action.id+'-visual';
    const pending=view.execute('capture',{agent:true,navigation:proof.navigation,capture_key:key,clip:{...area.region,scale},
      format:capture.format,max_bytes:capture.max_bytes,timeout_ms:Math.max(1,Math.ceil(deadline-performance.now()))})
      .then(value=>{native=value;settled=true;},error=>{failure=error;settled=true;});
    while(!settled){
      await Promise.race([pending,new Promise(resolve=>setTimeout(resolve,40))]);const boundary=await guard(true);if(boundary.handled)return boundary;
    }
    if(failure)return fail('visual_validation_failed','The current native screenshot could not be validated; no visual input was sent');
    const after=await read('captureArea',proof.options);if(!after.ok)return{handled:true,result:after};
    if(!same(after)||masked(after.redactions))return fail('visual_context_changed','The scene or privacy coverage changed during visual validation; request a fresh image');
    let unchanged=false;
    await render(native,area,after,capture,{raster:async canvas=>{unchanged=await visualPointUnchanged(canvas,saved.raster,x,y);}});
    if(!unchanged)return fail('visual_scene_changed','The selected image neighborhood changed; request a fresh screenshot before visual input');
    const boundary=await guard(true);if(boundary.handled)return boundary;
    store.consume(browser,params.target.image);setSnapshot(snapshot,root);
    const delivered=await send({kind:params.op==='hover'?'move':params.op==='scroll'?'wheel':'click',...point,
      button:params.op==='right_click'?'right':params.op==='middle_click'?'middle':'left',click_count:params.op==='double_click'?2:1,dx:params.dx||0,dy:params.dy||0});
    if(delivered)return delivered;
    if(['click','double_click','middle_click'].includes(params.op))await new Promise(resolve=>setTimeout(resolve,Math.min(150,Math.max(0,deadline-performance.now()-25))));
    const stopped=await guard(true);if(stopped.handled)return stopped;
    return{handled:true,result:{ok:true,action:action.id,dispatched:true,verification:'native_delivery',visual_reference:params.target.image,
      scene_validated:true,observation:await read('observe',{limit:100,max_bytes:4096})}};
  }catch(error){
    if(error.browserBoundary)throw error;
    if(['visual_reference_expired','visual_raster_invalid'].includes(error.code))return fail(error.code,error.message);
    throw error;
  }finally{if(key)try{await view.execute('capture_cancel',{capture_key:key});}catch{}}
}
