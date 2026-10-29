// Self-contained isolated-world helper. Only a private native upload controller
// may consume prepare().nonce or node(); neither belongs in an Agent/UI result.
export function uploadPickerBroker(){
  const marker='__mdo_browser_use_upload_picker_v1',doc=document,version=1,descriptor=Object.getOwnPropertyDescriptor(globalThis,marker);
  if(descriptor){const existing=descriptor.value;
    if(!descriptor.get&&!descriptor.set&&descriptor.writable===false&&descriptor.configurable===false&&existing?.version===version&&existing.documentNode===doc&&Object.isFrozen(existing))return existing;
    throw Error('upload_picker_runtime_conflict');}
  let active=null;const history=new Map();
  const nativeFiles=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'files');
  const showPicker=HTMLInputElement.prototype.showPicker,dispatch=EventTarget.prototype.dispatchEvent;
  const append=Node.prototype.appendChild,remove=Node.prototype.removeChild;
  const create=Document.prototype.createElement,shadow=Element.prototype.attachShadow;
  const on=EventTarget.prototype.addEventListener,off=EventTarget.prototype.removeEventListener;
  const EventType=Event,stop=Event.prototype.stopPropagation,clock=performance.now.bind(performance),random=crypto.getRandomValues.bind(crypto);
  const fileName=Object.getOwnPropertyDescriptor(File.prototype,'name').get,fileSize=Object.getOwnPropertyDescriptor(Blob.prototype,'size').get;
  const relative=Object.getOwnPropertyDescriptor(File.prototype,'webkitRelativePath')?.get;
  const getFiles=element=>nativeFiles.get.call(element),setFiles=(element,files)=>nativeFiles.set.call(element,files);
  const frozen=value=>Object.freeze(value),failure=(code,effect=false)=>frozen({ok:false,dispatched:effect,error:frozen({code,effect_may_have_occurred:effect})});
  const nameOK=value=>{
    if(typeof value!=='string'||!value||/[\u0000-\u001f\u007f<>:"/\\|?*]/.test(value)||/[\ud800-\udfff]/u.test(value)||/[. ]$/.test(value)||new TextEncoder().encode(value).length>240)return false;
    const stem=value.split('.')[0].toUpperCase();return !/^(?:CON|PRN|AUX|NUL|CONIN\$|CONOUT\$|CLOCK\$|COM[0-9¹²³]|LPT[0-9¹²³])$/.test(stem)&&!stem.endsWith(' ');
  };
  const optionsOK=p=>{
    if(!p||typeof p!=='object'||Object.keys(p).some(k=>!['key','document','ref','files','timeout_ms'].includes(k))||
      typeof p.key!=='string'||!/^[a-zA-Z0-9._:-]{1,100}$/.test(p.key)||typeof p.document!=='string'||!/^d-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(p.document)||typeof p.ref!=='string'||!/^e[1-9][0-9]{0,12}$/.test(p.ref)||
      !Number.isSafeInteger(p.timeout_ms)||p.timeout_ms<1||p.timeout_ms>120000||!Array.isArray(p.files)||p.files.length<1||p.files.length>16)return false;
    let total=0;return p.files.every(file=>file&&Object.keys(file).length===2&&nameOK(file.name)&&Number.isSafeInteger(file.size)&&file.size>=0&&file.size<=33554432&&(total+=file.size)<=67108864);
  };
  function runtime(){const value=globalThis.__mdo_browser_use_local_frame_v1;return value?.documentNode===doc&&value.documentNode===document?value:null;}
  function valid(job,selected=false){
    if(active!==job||job.closed)return 'upload_picker_cancelled';
    const now=clock();if(!Number.isFinite(now)||now<job.last)return 'upload_picker_clock_changed';job.last=now;if(now>=job.expires)return 'upload_picker_expired';
    const r=runtime();if(!r||r.document!==job.document||!job.target.isConnected||job.target.ownerDocument!==doc||!job.host.isConnected||job.host.ownerDocument!==doc||
      !job.input.isConnected||job.input.ownerDocument!==doc||r.uploadNode({document:job.document,target:{ref:job.ref}})!==job.target)return 'upload_target_changed';
    const info=r.uploadInfo({document:job.document,target:{ref:job.ref}});
    if(!info.ok||info.element.multiple!==job.multiple||info.element.directory!==false)return 'upload_target_changed';
    const files=getFiles(job.target),expected=selected?job.selected:job.before;
    if(files.length!==expected.length||Array.from(files).some((file,index)=>file!==expected[index]))return 'upload_target_changed';
    return null;
  }
  function selection(job){
    if(!job.opened)return {ready:false};
    const files=getFiles(job.input);if(!files.length)return {ready:false};
    if(files.length!==job.files.length)return {error:'upload_picker_manifest_changed'};
    for(let i=0;i<files.length;i++)if(fileName.call(files[i])!==job.files[i].name||fileSize.call(files[i])!==job.files[i].size||relative?.call(files[i]))return {error:'upload_picker_manifest_changed'};
    return {ready:true,files};
  }
  function metadata(job){return frozen({ok:true,key:job.key,document:job.document,ref:job.ref,phase:job.phase,dispatched:job.dispatched,
    opened:job.opened,events_trusted:false,input_method:'native_picker_dom_filelist',remaining_ms:Math.max(0,Math.floor(job.expires-clock()))});}
  function jobFor(p){return p&&typeof p.key==='string'&&active?.key===p.key?active:null;}
  function failJob(job,code){job.phase='failed';return job.result=failure(code,job.dispatched);}
  function prepare(p){
    let claimed=null,record=null;
    try{
      if(!optionsOK(p))return failure('upload_picker_arguments_invalid');
      if(active){if(active.key!==p.key)return failure('upload_picker_busy');
        if(active.closed)return failure('upload_picker_cleanup_failed',active.dispatched);
        if(active.document!==p.document||active.ref!==p.ref||active.files.length!==p.files.length||active.files.some((file,i)=>file.name!==p.files[i].name||file.size!==p.files[i].size))return failure('upload_picker_key_conflict');
        if(active.result)return active.result;
        const bad=valid(active);if(bad)return failJob(active,bad);return frozen({...metadata(active),nonce:active.nonce});}
      if(history.has(p.key))return failure('upload_picker_key_retired',!!history.get(p.key).status?.dispatched);
      if(history.size>=64)return failure('upload_picker_resource_limit');
      if(typeof nativeFiles?.get!=='function'||typeof nativeFiles?.set!=='function'||typeof shadow!=='function'||typeof showPicker!=='function')return failure('upload_picker_unsupported');
      const r=runtime();if(!r||r.document!==p.document)return failure('stale_document');
      const info=r.uploadInfo({document:p.document,target:{ref:p.ref}}),target=r.uploadNode({document:p.document,target:{ref:p.ref}});
      if(!info.ok||!target)return failure(info.error?.code||'upload_target_changed');
      if(info.element.directory)return failure('upload_directory_unsupported');if(p.files.length>1&&!info.element.multiple)return failure('upload_target_invalid');
      const beforeFiles=getFiles(target);if(beforeFiles.length>16)return failure('upload_picker_resource_limit');const before=Array.from(beforeFiles);
      const bytes=new Uint8Array(16);random(bytes);const nonce='application/x-mdo-browseruse-'+Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');bytes.fill(0);
      const born=clock();if(!Number.isFinite(born)||born<0||born>Number.MAX_SAFE_INTEGER-120000)return failure('upload_picker_clock_changed');
      record={status:null,cleanup:null};history.set(p.key,record);
      const host=create.call(doc,'div'),root=shadow.call(host,{mode:'closed'}),input=create.call(doc,'input');
      input.type='file';input.multiple=info.element.multiple;input.accept=nonce;input.hidden=true;
      for(const [name,value] of Object.entries({position:'fixed',width:'1px',height:'1px',opacity:'0','pointer-events':'none',overflow:'hidden'}))host.style.setProperty(name,value,'important');
      const job={key:p.key,document:p.document,ref:p.ref,target,host,input,before,files:p.files.map(file=>frozen({name:file.name,size:file.size})),
        nonce,born,last:born,expires:born+p.timeout_ms,multiple:info.element.multiple,selected:null,phase:'preparing',dispatched:false,opened:false,closed:false,result:null};
      active=job;claimed=job;
      job.stop=event=>{stop.call(event);if(event.type==='cancel'&&active===job&&!job.closed&&!job.result){job.phase='cancelled';job.result=failure('upload_picker_cancelled',job.dispatched);}};
      for(const name of ['input','change','cancel'])on.call(input,name,job.stop);
      append.call(root,input);append.call(doc.documentElement,host);job.phase='prepared';
      const bad=valid(job);if(bad){const result=failJob(job,bad);dispose({key:job.key});return result;}
      return frozen({...metadata(job),nonce});
    }catch{
      if(claimed&&active===claimed){failJob(claimed,'upload_picker_prepare_failed');dispose({key:claimed.key});}
      const result=frozen({...failure('upload_picker_prepare_failed'),cleanup_confirmed:!claimed||active!==claimed});
      if(record&&!record.status)record.status=result;return result;
    }
  }
  function node(p){const job=jobFor(p);if(!job||job.result)return null;
    try{const bad=valid(job);if(bad){failJob(job,bad);return null;}return job.input;}catch{failJob(job,'upload_picker_status_failed');return null;}}
  function open(p){
    const job=jobFor(p);if(!job)return failure('upload_picker_changed');
    if(job.opened)return job.result||metadata(job);
    if(job.result)return job.result;
    try{const bad=valid(job);if(bad)return failJob(job,bad);job.opened=true;job.phase='opening';showPicker.call(job.input);
      const changed=valid(job);return changed?failJob(job,changed):metadata(job);
    }catch(error){const code=error?.name==='NotAllowedError'?'upload_picker_activation_required':error?.name==='InvalidStateError'?'upload_picker_not_mutable':error?.name==='SecurityError'?'upload_picker_security_denied':null;
      return failJob(job,code||'upload_picker_open_unconfirmed');}
  }
  function status(p){
    const job=jobFor(p);if(!job)return history.get(p?.key)?.status||failure('upload_picker_changed');
    if(job.result)return job.result;
    try{const bad=valid(job);if(bad)return failJob(job,bad);const selected=selection(job);if(selected.error)return failJob(job,selected.error);
      if(selected.ready)job.phase='selected';return frozen({...metadata(job),selected:selected.ready});
    }catch{return failJob(job,'upload_picker_status_failed');}
  }
  function commit(p){
    const job=jobFor(p);if(!job)return history.get(p?.key)?.status||failure('upload_picker_changed');if(job.result)return job.result;
    const complete=(completed,outcome)=>job.result=frozen({ok:true,accepted:true,key:job.key,document:job.document,ref:job.ref,dispatched:true,completed,verified:completed,
      verification:'file_list_handoff',events_trusted:false,input_method:'native_picker_dom_filelist',outcome});
    try{
      const bad=valid(job);if(bad)return failJob(job,bad);const selected=selection(job);if(selected.error)return failJob(job,selected.error);if(!selected.ready)return failure('upload_picker_pending');
      // Blink shares the FileList itself; pin its original File objects so a
      // page clearing that shared list cannot masquerade as a verified handoff.
      job.selected=Array.from(selected.files);job.phase='committing';job.dispatched=true;setFiles(job.target,selected.files);
      if(valid(job,true))return complete(false,'target_changed_after_assignment');
      dispatch.call(job.target,new EventType('input',{bubbles:true,composed:true}));
      if(valid(job,true))return complete(false,'target_changed_after_input');
      dispatch.call(job.target,new EventType('change',{bubbles:true}));
      if(valid(job,true))return complete(false,'target_changed_after_change');
      job.phase='committed';return complete(true,'file_list_confirmed');
    }catch{job.phase='uncertain';job.result=failure('upload_assignment_uncertain',job.dispatched);return job.result;}
  }
  function dispose(p){
    const job=jobFor(p);if(!job)return history.get(p?.key)?.cleanup||failure('upload_picker_changed');
    job.closed=true;job.phase='disposing';let clean=true;
    // Never clear value on the shared selected FileList: that would also clear
    // the real target. Detach the private input onto a fresh native empty list.
    try{const empty=create.call(doc,'input');empty.type='file';const list=getFiles(empty);if(list.length)throw Error('upload_picker_cleanup_failed');
      setFiles(job.input,list);if(getFiles(job.input).length)clean=false;}catch{clean=false;}
    try{for(const name of ['input','change','cancel'])off.call(job.input,name,job.stop);}catch{clean=false;}
    try{if(job.host.parentNode)remove.call(job.host.parentNode,job.host);if(job.host.isConnected)clean=false;}catch{clean=false;}
    const result=frozen({ok:clean,key:job.key,disposed:clean,cleanup_confirmed:clean,dispatched:job.dispatched,effect_may_have_occurred:job.dispatched});
    if(!clean)return result;
    const record=history.get(job.key);record.cleanup=result;record.status=frozen({...job.result,ok:job.result?.ok??true,key:job.key,phase:'disposed',disposed:true,cleanup_confirmed:true,dispatched:job.dispatched});
    job.input=null;job.host=null;job.target=null;job.before=null;job.selected=null;job.files=null;job.nonce=null;active=null;return result;
  }
  const api=frozen({version,documentNode:doc,prepare,node,open,status,commit,dispose});
  Object.defineProperty(globalThis,marker,{value:api,writable:false,configurable:false});return api;
}
