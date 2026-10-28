import {browserRuntime} from './automation-runtime.js';

const uuid='[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}';
const domId=new RegExp(`^d-${uuid}$`,'i'),nativeId=new RegExp(`^fd-${uuid}$`,'i'),elementId=/^e[1-9][0-9]{0,12}$/;
const allowed=new Set(['describe','observe','locate','prepare','verify','optionList','selectOptions']);
const inputs=new Set(['click','check','fill','type','key','select']);
const scope={frame_scope:'main_document',frame_routing:'android-main-document-v1',limitations:Object.freeze(['child_frames_unavailable'])};
const issue=(code,message)=>Object.assign(new Error(message),{code});
const publicUrl=value=>{try{const url=new URL(value);url.username='';url.password='';return url.href.slice(0,2048);}catch{return '';}};

// The Android bridge owns one canonical main-frame peer. Do not invent a complete
// native frame tree or route page-provided aliases to other execution worlds.
export function createDocumentDriver({execute,checkpoint=async()=>{}}){
  async function read(method,options={},control={}){
    let possibleEffect=false,interruption=null;
    const deadline=performance.now()+Math.min(60000,Math.max(1,Number(options.timeout_ms)||10000));
    const expiry=issue('timed_out','The main-document operation exceeded its original deadline');
    const within=async operation=>{
      const remaining=deadline-performance.now();if(remaining<=0)throw expiry;
      let timer;
      try{return await new Promise((resolve,reject)=>{
        timer=setTimeout(()=>reject(expiry),Math.ceil(remaining));
        Promise.resolve().then(()=>{if(performance.now()>=deadline)throw expiry;return operation();}).then(resolve,reject);
      });}finally{clearTimeout(timer);}
    };
    const check=async()=>{
      try{await within(checkpoint);if(control.checkpoint)await within(()=>control.checkpoint());}
      catch(error){if(error!==expiry)interruption=error;throw error;}
    };
    const call=async(command,params)=>{
      await check();try{return await within(()=>execute(command,params));}finally{await check();}
    };
    async function state(){
      const value=await call('state');const world=value?.isolated_world;
      if(value?.platform!=='android-webview'||world?.adapter!=='android-main-document-v1'||
         world.installed!==true||value.browser_use?.available!==true)
        throw issue('unsupported_capability','This Android browser has not enabled its main-document automation adapter');
      if(typeof value.loading!=='boolean')throw issue('document_response_invalid','The native readiness flag is invalid');
      if(value.loading||world.ready!==true)throw issue(world.phase==='world_not_ready'||value.loading?'frame_not_ready':world.phase||'frame_not_ready',
        'The original main document is not ready for this operation');
      if(!Number.isSafeInteger(value.navigation)||value.navigation<1||!nativeId.test(world.native_document))
        throw issue('document_response_invalid','The native main-document identity is invalid');
      return{native:world.native_document,navigation:value.navigation,state:value};
    }
    async function evaluate(pin,name,args){
      await check();if(name==='selectOptions')possibleEffect=true;
      const result=await call('automation',{agent:true,native_document:pin.native,navigation:pin.navigation,
        timeout_ms:Math.max(1,Math.min(10000,Math.ceil(deadline-performance.now()))),
        script:`(${browserRuntime.toString()})({local_frame:true}).${name}(${JSON.stringify(args)})`});
      if(result?.ok===false&&/^[a-z][a-z0-9_]{0,63}$/.test(result.error?.code||'')){
        if(result.error.evaluation_may_have_occurred===false)possibleEffect=false;
        throw issue(result.error.code,'The native main-document executor rejected or could not confirm the operation');
      }
      if(result?.ok!==true||result.native_document!==pin.native||result.navigation!==pin.navigation||typeof result.value?.ok!=='boolean')
        throw issue('document_response_invalid','The isolated reply does not match the original native document');
      if(name==='selectOptions'&&result.value.error?.effect_may_have_occurred===false)possibleEffect=false;
      return result.value;
    }
    async function revalidate(pin){
      const current=await state();
      if(current.native!==pin.native||current.navigation!==pin.navigation)
        throw issue('stale_document','The original main document changed during the operation');
    }
    function qualify(row,pin,document){
      if(!elementId.test(row?.ref)||row.frame!=='f0')throw issue('document_response_invalid','The element does not belong to the original main document');
      return{...row,ref:`r1|f0|${pin.native}|${document}|${row.ref}`};
    }
    function target(value,pin,document){
      const out={...value};if(out.frame&&out.frame!=='f0')throw issue('unsupported_capability','Child-frame operations are unavailable in this Android adapter');
      delete out.frame;
      if(out.ref){
        if(Object.keys(out).some(key=>key!=='ref')||typeof out.ref!=='string'||out.ref.length>256)
          throw issue('target_invalid','Use one original element reference or semantic selectors');
        const parts=out.ref.split('|');
        if(parts.length!==5||parts[0]!=='r1'||parts[1]!=='f0'||!nativeId.test(parts[2])||!domId.test(parts[3])||!elementId.test(parts[4]))
          throw issue('target_invalid','The main-document element reference is malformed');
        if(parts[2]!==pin.native||parts[3]!==document)throw issue('stale_document','The element reference belongs to another main document');
        out.ref=parts[4];
      }
      return out;
    }
    function bounded(value){
      if(new TextEncoder().encode(JSON.stringify(value)).length>262144)
        throw issue('document_result_limit','The main-document result exceeds 256 KiB; narrow the observation');
      return value;
    }
    try{
      if(!allowed.has(method))throw issue('unsupported_capability','This Android adapter does not provide the requested operation');
      if(method==='prepare'&&!inputs.has(options.op))throw issue('unsupported_capability','The requested Android native input kind is not implemented');
      if(!['observe','describe'].includes(method)&&!domId.test(options.document))
        throw issue('stale_document','Observe the original main document before acting');
      const pin=await state();
      if(method==='prepare'&&pin.state.native_scale_ready!==true)throw issue('frame_not_ready','The original native scale has not been reported');
      if(method==='prepare'&&pin.state.input_cleanup_confirmed===false&&!pin.state.input_busy)
        throw issue('input_cleanup_failed','Native input cleanup is unconfirmed; end this browser before further input');
      const meta=await evaluate(pin,'describe',{});
      if(!meta.ok||!domId.test(meta.document))throw issue('document_response_invalid','The isolated main-document identity is invalid');
      if(options.document&&options.document!==meta.document)throw issue('stale_document','The observed main document changed');
      let value;
      if(method==='describe')value=meta;
      else{
        const args={...options,document:meta.document};delete args.timeout_ms;
        if(method!=='observe')args.target=target(options.target||{},pin,meta.document);
        value=await evaluate(pin,method,args);
      }
      await revalidate(pin);
      if(!value.ok){
        if(!/^[a-z][a-z0-9_]{0,63}$/.test(value.error?.code||''))throw issue('document_response_invalid','The isolated error receipt is invalid');
        const error={...value.error,effect_may_have_occurred:possibleEffect};
        if(error.candidates){if(!Array.isArray(error.candidates)||error.candidates.length>8)throw issue('document_response_invalid','The candidate list is invalid');
          error.candidates=error.candidates.map(row=>qualify(row,pin,meta.document));}
        return bounded({...value,...scope,error});
      }
      if(value.document!==meta.document)throw issue('stale_document','The isolated main document changed during the operation');
      const out={...value,...scope};
      if(value.element)out.element=qualify(value.element,pin,meta.document);
      if(value.ref)out.ref=qualify({ref:value.ref,frame:value.frame},pin,meta.document).ref;
      if(method==='describe'||method==='observe'){
        out.url=publicUrl(value.url);out.title=String(value.title||'').slice(0,512);
        out.frames=[{id:'f0',document:pin.native,parent:null,dom:true,url:out.url}];
      }
      if(method==='observe'){
        if(!Array.isArray(value.elements)||value.elements.length>400||!Array.isArray(value.frames)||value.frames.length!==1||value.frames[0]?.id!=='f0')
          throw issue('document_response_invalid','The bounded main-document observation is malformed');
        out.elements=value.elements.map(row=>qualify(row,pin,meta.document));
      }
      if(method==='prepare'){
        if(!out.element||!value.point||![value.point.x,value.point.y].every(n=>Number.isFinite(n)&&n>=0&&n<=32768)||
           !Array.isArray(value.geometry)||value.geometry.length<6||value.geometry.length>16||!value.geometry.every(Number.isFinite))
          throw issue('document_response_invalid','The main-document input geometry is invalid');
        const ref=value.element.ref;
        out.frame_chain=[{frame:'f0',document:pin.native}];out.native_target={document:meta.document,ref};
      }
      return bounded(out);
    }catch(error){
      if(interruption)throw interruption;
      return{ok:false,...scope,error:{code:typeof error?.code==='string'?error.code:'document_unavailable',
        message:typeof error?.code==='string'?String(error.message).slice(0,256):'The original main-document operation was not confirmed',effect_may_have_occurred:possibleEffect}};
    }
  }
  return Object.freeze({read});
}
