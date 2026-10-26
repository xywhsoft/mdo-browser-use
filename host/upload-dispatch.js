// Bytes cross the original task SDK boundary; browser-device paths never do.
export async function executeFileUpload({params,view,api,action,guard,read,deadline,setDispatched,fail}){
  let ticket=null,navigation,stage='capability',sourceIndex=null,requested=false,cleanupRequested=false,retained=false,settled=false,completed=false;
  const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const remaining=()=>Math.max(1,Math.ceil(deadline-performance.now()));
  const transfer=result=>{
    if(ticket)result.result.transfer={ticket,retained,assignment_requested:requested};
    return result;
  };
  const stopped=async()=>{const checked=await guard(true);if(checked.handled)return transfer(checked);
    return navigation!==undefined&&checked.state.navigation!==navigation?transfer(fail('stale_document','The document changed during file transfer')):checked;};
  const native=params=>view.execute('upload',params);
  try{
    let checked=await stopped();if(checked.handled)return checked;
    if(!checked.state.file_input_capability||!checked.state.upload_capability)
      return fail('unsupported_capability','This displaying device cannot receive verified native file inputs; no source files were read');
    if(params.action==='status'||params.action==='release'){
      stage='transfer_'+params.action;
      if(params.action==='release'){cleanupRequested=true;setDispatched(true);}
      const result=await native({action:params.action,ticket:params.ticket,confirmed:params.confirmed});
      return {handled:true,result:{ok:true,action:action.id,...result}};
    }
    if(checked.state.upload_cleanup_failed)return fail('upload_cleanup_failed','End this browser before another file transfer; native cleanup was not confirmed');
    while(checked.state.loading||checked.state.input_busy||checked.state.capture_busy){
      await pause(30);checked=await stopped();if(checked.handled)return checked;
    }
    navigation=checked.state.navigation;
    let pinned=await read('uploadInfo',{document:params.document,target:params.target});
    if(!pinned.ok)return{handled:true,result:pinned};
    if(pinned.element.directory||params.files.length>1&&!pinned.element.multiple)
      return fail('upload_target_invalid','Choose a regular file input with enough multiple-file capacity');
    const snapshots=[];let total=0;
    for(const file of params.files){
      sourceIndex=snapshots.length;stage='source_snapshot';
      checked=await stopped();if(checked.handled)return checked;
      if(typeof file.path!=='string'||/[\u0000-\u001f\u007f]/.test(file.path))return fail('upload_path_invalid','Choose an unambiguous source path without control characters');
      const name=file.name??file.path.split(/[\\/]/).at(-1);
      const stem=(name||'').split('.')[0].toUpperCase();
      if(!name||new TextEncoder().encode(name).length>240||/[<>:"/\\|?*\u0000-\u001f\u007f]/.test(name)||/[. ]$/.test(name)||/ $/.test(stem)||/^(CON|PRN|AUX|NUL|CONIN\$|CONOUT\$|CLOCK\$|COM[0-9¹²³]|LPT[0-9¹²³])$/.test(stem))
        return fail('upload_name_invalid','Choose a safe file basename or provide an explicit safe name');
      const snapshot=await api.call('files.digest',{path:file.path,...(file.space?{space:file.space}:{}),max_bytes:33554432});
      if(!Number.isSafeInteger(snapshot.size)||snapshot.size<0||snapshot.size>33554432||typeof snapshot.revision!=='string'||!snapshot.revision||!/^[a-f0-9]{64}$/.test(snapshot.sha256))
        return fail('upload_snapshot_invalid','The task host did not return a complete bounded source snapshot');
      total+=snapshot.size;if(total>67108864)return fail('upload_budget_exceeded','The upload exceeds its 64 MiB total byte budget');
      snapshots.push({source:file,name,size:snapshot.size,sha256:snapshot.sha256,revision:snapshot.revision});
    }
    checked=await stopped();if(checked.handled)return checked;
    if(checked.state.navigation!==navigation)return fail('stale_document','The document changed before transfer admission');
    sourceIndex=null;stage='native_admission';
    const admitted=await native({action:'begin',navigation,upload_key:action.id,timeout_ms:Math.min(120000,remaining()+5000),
      files:snapshots.map(file=>({name:file.name,size:file.size,sha256:file.sha256}))});
    if(typeof admitted.ticket!=='string'||!/^u-[a-f0-9-]{36}$/i.test(admitted.ticket))return fail('upload_receipt_invalid','The browser did not return a valid staging ticket');
    ticket=admitted.ticket;
    for(let file=0;file<snapshots.length;file++){
      sourceIndex=file;
      const snapshot=snapshots[file];let offset=0;
      while(offset<snapshot.size){
        checked=await stopped();if(checked.handled)return checked;
        if(checked.state.navigation!==navigation)return transfer(fail('stale_document','The document changed during byte transfer'));
        stage='source_read';const limit=Math.min(65536,snapshot.size-offset),chunk=await api.call('files.read',{
          path:snapshot.source.path,...(snapshot.source.space?{space:snapshot.source.space}:{}),revision:snapshot.revision,offset,limit});
        if(chunk.revision!==snapshot.revision||chunk.bytes!==limit||chunk.next!==offset+limit||typeof chunk.base64!=='string'||chunk.base64.length>87384)
          return transfer(fail('upload_snapshot_changed','The source range did not match its pinned revision; no file was assigned'));
        checked=await stopped();if(checked.handled)return checked;
        stage='native_chunk';await native({action:'chunk',ticket,navigation,file,offset,base64:chunk.base64});offset=chunk.next;
      }
    }
    checked=await stopped();if(checked.handled)return checked;
    sourceIndex=null;stage='native_seal';const sealed=await native({action:'seal',ticket,navigation});
    if(!sealed.sealed)return transfer(fail('upload_snapshot_invalid','The receiving browser could not verify complete file bytes'));
    // Revalidate the pinned reference, never a newly matching replacement node.
    stage='target_revalidation';pinned=await read('uploadInfo',{document:params.document,target:{ref:pinned.element.ref}});
    if(!pinned.ok)return transfer({handled:true,result:pinned});
    checked=await stopped();if(checked.handled)return checked;
    if(checked.state.navigation!==navigation)return transfer(fail('stale_document','The document changed before file assignment'));
    let result,error;stage='native_assignment';requested=true;retained=true;setDispatched(true);
    const pending=native({action:'assign',ticket,navigation,document:params.document,ref:pinned.element.ref,timeout_ms:remaining()})
      .then(value=>{result=value;settled=true;},value=>{error=value;settled=true;});
    while(!settled){
      await Promise.race([pending,pause(40)]);
      // Native assignment itself reports dialog/new-window checkpoints. A
      // cancelled UI action must also stop preparation before native delivery.
      if(!settled){checked=await stopped();if(checked.handled)return checked;}
    }
    if(error)throw error;
    if(result.dispatched===false){setDispatched(false);retained=false;requested=false;}
    if(!result.accepted)return transfer(fail(result.error?.code||'action_uncertain',result.error?.message||'Inspect the page before another upload'));
    completed=true;retained=result.retained===true;
    return transfer({handled:true,result:{ok:true,action:action.id,...result}});
  }catch(error){
    const result=transfer(fail(requested||cleanupRequested?'action_uncertain':error?.code||'upload_transfer_failed',requested?
      'File assignment was not confirmed; inspect the page and retained transfer before repeating':
      cleanupRequested?'Transfer release was not confirmed; inspect its ticket before retrying':
      params.action==='status'?'The transfer status request failed; inspect its stage and cause':
      'The file transfer stopped before native assignment; inspect the failing stage and cause'));
    result.result.error.stage=stage;
    if(sourceIndex!==null)result.result.error.source_index=sourceIndex;
    result.result.error.cause={code:String(error?.code||'transport_failed').slice(0,128),message:String(error?.message||error).slice(0,512)};
    return result;
  }finally{
    if(ticket&&!completed)try{await native({action:'cancel',ticket});}catch{}
  }
}
