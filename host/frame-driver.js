import {browserRuntime} from './automation-runtime.js';
import {createFrameReader} from './frame-reader.js';

const permitted=new Set(['observe','locate','prepare','verify','optionList','selectOptions','uploadInfo','chooserInfo','captureArea']);
const mutations=new Set(['selectOptions']);
const issue=(code,message)=>Object.assign(new Error(message),{code});
const pointValid=point=>point&&[point.x,point.y].every(number=>Number.isFinite(number)&&number>=0&&number<=32768);

// The final dispatcher uses fixed methods only. Script and process/context IDs
// cannot be supplied by a model. Route metadata is for native adapters alone.
export function createFrameDriver({execute,checkpoint=async()=>{}}){
  async function read(method,options={},control={}){
    let possibleEffect=false,interruption=null;
    const deadline=performance.now()+Math.min(60000,Math.max(1,Number(options.timeout_ms)||10000));
    const check=async()=>{
      try{await checkpoint();await control.checkpoint?.();}
      catch(error){interruption=error;throw error;}
      if(performance.now()>=deadline)throw issue('timed_out','The frame operation deadline expired');
    };
    const native=async(command,params)=>{await check();try{return await execute(command,params);}finally{await check();}};
    const reader=createFrameReader({execute:(command,params)=>native(command,params),checkpoint:check});
    const fromReader=async(name,args)=>{const result=await reader[name]({...args,timeout_ms:Math.max(1,deadline-performance.now())});if(interruption)throw interruption;return result;};
    async function evaluate(route,name,args){
      await check();
      // Read helpers can be retried by their caller. Mutations retain uncertainty
      // from the instant their request is sent, including lost/error replies.
      if(mutations.has(name))possibleEffect=true;
      let result;
      try{
        result=await execute('frames',{action:'evaluate',agent:true,frame:route.frame,document:route.frame_document,
          script:`(${browserRuntime.toString()})({local_frame:true}).${name}(${JSON.stringify(args)})`});
        if(!result?.ok&&result?.error?.evaluation_may_have_occurred===false)possibleEffect=false;
      }finally{await check();}
      if(!result?.ok){
        throw issue(result?.error?.code||'frame_unavailable','The pinned frame operation was not confirmed');
      }
      if(result.frame!==route.frame||result.frame_document!==route.frame_document||typeof result.value?.ok!=='boolean')
        throw issue('frame_response_invalid','The operation reply does not match its pinned frame');
      if(mutations.has(name)&&result.value.error?.effect_may_have_occurred===false)possibleEffect=false;
      return result.value;
    }
    async function pinChain(route){
      const tree=await native('frames',{action:'list',agent:true});
      if(!tree?.ok||tree.complete!==true||!Array.isArray(tree.frames)||tree.frames.length>32)
        throw issue(tree?.error?.code||'frame_not_ready','The current frame ancestry is not complete');
      const rows=new Map(tree.frames.map(row=>[row.id,row])),chain=[],seen=new Set();let row=rows.get(route.frame);
      if(row?.document!==route.frame_document)throw issue('stale_frame','The target frame changed before operation delivery');
      while(row&&chain.length<32){
        if(seen.has(row.id)||row.loading||!row.isolated)throw issue('stale_frame','The frame ancestry is no longer ready');
        seen.add(row.id);chain.push({frame:row.id,document:row.document});
        if(row.id==='f0'){if(row.parent!==null)throw issue('frame_response_invalid','The original root has an invalid parent');return chain;}
        row=rows.get(row.parent);
      }
      throw issue('stale_frame','The original frame ancestry is no longer present');
    }
    async function mapPoint(route,local){
      if(!pointValid(local.point)||!Array.isArray(local.geometry)||local.geometry.length>16||!local.geometry.every(Number.isFinite))
        throw issue('frame_response_invalid','The local target returned invalid CSS geometry');
      const chain=await pinChain(route);let point={...local.point},geometry=[...local.geometry],focused=true;
      for(let index=0;index<chain.length-1;index++){
        const current=chain[index],parent=chain[index+1];
        const owner=await native('frames',{action:'owner',agent:true,frame:current.frame,document:current.document,point});
        if(!owner?.ok)throw issue(owner?.error?.code||'frame_unavailable','The pinned iframe owner changed');
        if(owner.frame!==current.frame||owner.frame_document!==current.document||owner.parent!==parent.frame||owner.parent_document!==parent.document)
          throw issue('stale_frame','The iframe owner no longer belongs to the original parent');
        if(!owner.value?.ok)throw issue(owner.value?.error?.code||'frame_owner_unavailable','The iframe owner is not actionable');
        if(!pointValid(owner.value.point)||!Array.isArray(owner.value.geometry)||owner.value.geometry.length!==8||!owner.value.geometry.every(Number.isFinite))
          throw issue('frame_response_invalid','The parent returned invalid CSS geometry');
        point={...owner.value.point};geometry.push(...owner.value.geometry,point.x,point.y);focused&&=owner.value.focused===true;
      }
      const fresh=await pinChain(route);
      if(JSON.stringify(fresh)!==JSON.stringify(chain))throw issue('stale_frame','The frame ancestry changed during coordinate mapping');
      return{point,geometry,frame_chain:chain,parents_focused:focused};
    }
    async function uploadParents(route,chain){
      for(let index=0;index<chain.length-1;index++){
        const child=chain[index],parent=chain[index+1],owner=await native('frames',{action:'upload_policy',agent:true,frame:child.frame,document:child.document});
        if(!owner?.ok)throw issue(owner?.error?.code||'frame_unavailable','The upload frame owner could not be checked');
        if(owner.frame!==child.frame||owner.frame_document!==child.document||owner.parent!==parent.frame||owner.parent_document!==parent.document)
          throw issue('stale_frame','The upload frame owner changed during policy checking');
        if(!owner.value?.ok)throw issue(owner.value?.error?.code||'frame_owner_unavailable','A parent iframe is not enabled for upload');
      }
      if(JSON.stringify(await pinChain(route))!==JSON.stringify(chain))throw issue('stale_frame','The upload ancestry changed during policy checking');
    }
    try{
      if(!permitted.has(method))throw issue('frame_method_invalid','Only fixed Browser Use operations are allowed');
      if(method==='observe'||method==='locate')return await fromReader(method,options);
      if(method==='chooserInfo'){
        const page=await fromReader('describe',{document:options.document});
        if(!page.ok)return page;
        if(!options.document||page.document!==options.document)throw issue('stale_document','Observe the current root before resolving its file picker');
        const pickers=await native('chooser',{action:'list',agent:true}),picker=pickers.choosers?.find(row=>row.id===options.chooser);
        if(!pickers.ok||!picker?.supported||!picker.frame||!picker.frame_document)throw issue('chooser_changed','The exact frame-owned file picker is no longer available');
        const route={frame:picker.frame,frame_document:picker.frame_document},chain=await pinChain(route),meta=await evaluate(route,'describe',{});
        if(!meta.ok||!/^d-[a-f0-9-]{36}$/i.test(meta.document))throw issue('frame_response_invalid','The picker document identity is invalid');
        await uploadParents(route,chain);
        const target=await native('chooser',{action:'target',agent:true,chooser:picker.id,document:meta.document,frame_chain:chain});
        if(!target.ok)return target;
        if(target.document!==meta.document||!/^e[1-9][0-9]{0,12}$/.test(target.element?.ref)||JSON.stringify(await pinChain(route))!==JSON.stringify(chain))
          throw issue('target_changed','The picker no longer belongs to its original input and document');
        return{...target,document:options.document,element:{...target.element,frame:route.frame,ref:`r1|${route.frame}|${route.frame_document}|${meta.document}|${target.element.ref}`},
          native_target:{document:meta.document,ref:target.element.ref,frame_chain:chain}};
      }
      if(method==='captureArea'&&!options.target){
        const tree=await native('frames',{action:'list',agent:true}),root=tree.frames?.find(row=>row.id==='f0');
        if(!tree.ok||!root||root.loading||!root.isolated)throw issue('frame_not_ready','The original root is not ready for capture preparation');
        const route={frame:'f0',frame_document:root.document};
        return await evaluate(route,'captureArea',options);
      }
      const found=await fromReader('locate',{document:options.document,target:options.target,_upload:method==='uploadInfo'});
      if(!found.ok)return found;
      const route=found.route,localOptions={...options,document:route.document,target:{ref:route.ref}};
      if(method==='captureArea'&&route.frame!=='f0')throw issue('frame_capture_pending','Cross-frame screenshot mask aggregation is not ready; retain the whole-view privacy mask');
      const local=await evaluate(route,method,localOptions);
      if(!local.ok)return local;
      if(local.document!==route.document||local.element&&local.element.ref!==route.ref||local.ref&&local.ref!==route.ref)
        throw issue('target_changed','The operation returned a different target or document');
      const out={...local,document:options.document};
      if(local.element)out.element={...local.element,ref:found.element.ref,frame:route.frame};
      if(local.ref)out.ref=found.element.ref;
      if(Object.hasOwn(local,'frame'))out.frame=route.frame;
      if(method==='prepare')return{...out,...await mapPoint(route,local)};
      if(method==='uploadInfo'){
        const chain=await pinChain(route);await uploadParents(route,chain);
        return{...out,native_target:{document:route.document,ref:route.ref,frame_chain:chain}};
      }
      if(method==='verify'&&local.focused){
        const prepared=await evaluate(route,'prepare',{...localOptions,scroll:false});
        if(!prepared.ok)return{...out,focused:false};
        try{const mapped=await mapPoint(route,prepared);out.focused=mapped.parents_focused;}
        catch(error){if(interruption)throw interruption;if(!['frame_owner_hidden','frame_owner_obscured','frame_point_outside_viewport','unsupported_geometry'].includes(error.code))throw error;out.focused=false;}
      }
      return out;
    }catch(error){
      if(interruption)throw interruption;
      return{ok:false,error:{code:typeof error?.code==='string'?error.code:'frame_unavailable',
        message:String(error?.message||'Frame operation failed').slice(0,256),effect_may_have_occurred:possibleEffect}};
    }
  }
  return Object.freeze({read});
}
