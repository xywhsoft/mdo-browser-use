// Real File objects, native events and protected-cache retention in WebView2.
async function runFileInputProbe({bridge,assert,pause,runtimeSource}){
  const browser='browser-use-file-input-probe',url=location.origin+'/__browser_use_upload__.html';
  let state,sequence=0;
  const read=async script=>{const result=await bridge.call('evaluate',{browser,script});assert(result.ok,'Cannot inspect upload fixture');return result.value;};
  const engine=async expression=>{const result=await bridge.call('automation',{browser,agent:true,script:`(${runtimeSource})().${expression}`});assert(result.ok,'Cannot inspect isolated file input');return result.value;};
  const create=async()=>{
    await bridge.call('create',{browser,url,visible:false});
    for(let i=0;i<150;i++){state=await bridge.call('state',{browser});if(!state.loading&&state.url===url&&await read('globalThis.fixtureReady===true'))break;await pause(20);}
    assert(!state.loading&&state.file_input_capability,'Native file-input capability is missing');
  };
  const upload=params=>bridge.call('upload',{browser,navigation:state.navigation,...params});
  const hash=async bytes=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(x=>x.toString(16).padStart(2,'0')).join('');
  const bytes=Uint8Array.from({length:131075},(_,i)=>i%251),empty=new Uint8Array();
  const encode=data=>{let text='';for(const value of data)text+=String.fromCharCode(value);return btoa(text);};
  const source=[{name:'file-中文.bin',data:bytes},{name:'empty.txt',data:empty}];
  for(const item of source)item.sha256=await hash(item.data);
  const stage=async(count=1,extra={})=>{
    const files=source.slice(0,count),job=await upload({action:'begin',upload_key:`file-input-${++sequence}`,
      files:files.map(f=>({name:f.name,size:f.data.length,sha256:f.sha256})),...extra});
    for(let file=0;file<files.length;file++)for(let offset=0;offset<files[file].data.length;offset+=65536)
      await upload({action:'chunk',ticket:job.ticket,file,offset,base64:encode(files[file].data.subarray(offset,offset+65536))});
    assert((await upload({action:'seal',ticket:job.ticket})).sealed,'File-input snapshot failed verification');return job.ticket;
  };
  const target=async css=>{
    const doc=await engine('observe({})');const found=await engine(`uploadInfo({document:${JSON.stringify(doc.document)},target:{css:${JSON.stringify(css)}}})`);
    assert(found.ok,'File input was not discoverable: '+css);return {document:doc.document,ref:found.element.ref,element:found.element};
  };
  const assign=(ticket,pinned,extra={})=>upload({action:'assign',ticket,document:pinned.document,ref:pinned.ref,...extra});
  const release=ticket=>upload({action:'release',ticket,confirmed:true});
  const rejected=async(params,message)=>{let failed=false;try{await upload(params);}catch{failed=true;}assert(failed,message);};
  const consumed=async(id,expected)=>{
    let values;for(let i=0;i<120;i++){values=await read(`globalThis.uploadReads.filter(x=>x.id===${JSON.stringify(id)})`);if(values.length>=expected)break;await pause(30);}
    const result=values.at(-1);assert(result&&!result.error,'The browser could not read its actual native File bytes: '+JSON.stringify(result));
    for(let i=0;i<result.files.length;i++)assert(result.files[i].sha256===source[i].sha256&&result.files[i].size===source[i].data.length,'Assigned bytes differ from their verified snapshot');
    return result;
  };
  const idle=async()=>{for(let i=0;i<150;i++){if(!(await bridge.call('state',{browser})).input_busy)return;await pause(20);}assert(false,'Native file assignment did not drain its input slot');};
  try{
    await create();
    const observation=await engine('observe({})'),hidden=observation.elements.find(x=>x.name==='Hidden attachment');
    assert(hidden?.input_type==='file'&&hidden.hidden&&hidden.selected_count===0,'Observation omitted hidden file input metadata');
    const multiple=await target('#multiple');assert(multiple.element.multiple&&multiple.element.accept==='.bin,.txt','File input constraints were lost');
    const pair=await stage(2);
    const single=await target('#single');
    let result=await assign(pair,single);assert(!result.accepted&&!result.dispatched&&result.error.code==='upload_target_invalid','Multiple files reached a single input');
    const doc=await engine('observe({})');
    for(const css of ['#disabled','#directory','#wrong']){
      const pinned=await engine(`locate({document:${JSON.stringify(doc.document)},target:{css:${JSON.stringify(css)}}})`);
      assert(pinned.ok,'Invalid-target fixture is missing');
      result=await assign(pair,{document:doc.document,ref:pinned.element.ref});assert(!result.accepted&&!result.dispatched,'Invalid input received file assignment: '+css);
    }
    result=await assign(pair,{...multiple,document:'stale-document'});assert(!result.accepted&&!result.dispatched,'Stale document received file assignment');
    result=await assign(pair,multiple);assert(result.accepted&&result.verified&&result.retained&&result.input_method==='native_file_input','Exact native multi-file assignment failed: '+JSON.stringify(result));
    const actual=await consumed('multiple',1);assert(actual.files.length===2&&actual.files[1].size===0,'The empty/multiple FileList was not preserved');
    const events=await read('uploadEvents.filter(x=>x.id==="multiple")');
    assert(events.length===2&&events.every(x=>x.trusted)&&events.map(x=>x.type).join(',')==='input,change','Native file input/change events were not delivered exactly once as trusted');
    assert((await assign(pair,multiple)).verified,'Idempotent assignment lost its saved receipt');
    assert((await read('uploadEvents.filter(x=>x.id==="multiple")')).length===2,'Repeating assignment dispatched duplicate events');
    result=await assign(pair,single);assert(result.error.code==='upload_identity_conflict'&&!result.dispatched,'A retained ticket was reassigned to another input');
    await rejected({action:'release',ticket:pair},'Unconfirmed release deleted bytes still owned by the page');
    assert((await release(pair)).released,'Confirmed upload release failed');
    await rejected({action:'status',ticket:pair},'Released upload retained an accessible cache ticket');
    const delayed=await stage(1,{timeout_ms:2000}),hiddenTarget=await target('#hidden');
    await read('document.querySelector("#hidden").dataset.mode="delayed"');
    assert((await assign(delayed,hiddenTarget)).verified,'Hidden native file input could not receive a snapshot');
    await consumed('hidden',1);
    assert((await upload({action:'status',ticket:delayed})).retained,'Staging expiry deleted bytes after native assignment');
    await bridge.call('takeover',{browser});
    assert((await upload({action:'cancel',ticket:delayed})).retained,'Cancellation deleted the website\'s retained File bytes');
    assert((await upload({action:'status',ticket:delayed})).retained,'Manual takeover deleted assigned file bytes');
    await bridge.call('release',{browser});await release(delayed);
    for(const [css,id] of [['#shadowed','shadowed'],['#framed','framed']]){
      const ticket=await stage(),pinned=await target(css);assert((await assign(ticket,pinned)).verified,'Shadow/frame file assignment failed');await consumed(id,1);await release(ticket);
    }
    const changing=await stage();await read('document.querySelector("#single").dataset.mode="clear"');
    result=await assign(changing,await target('#single'));
    assert(!result.accepted&&result.dispatched&&result.retained&&result.error.effect_may_have_occurred,'Page-cleared file list was reported as successful or replayable');
    await consumed('single',1);await release(changing);
    const interrupted=await stage();await read('document.querySelector("#single").dataset.mode="busy"');
    const pending=assign(interrupted,await target('#single'));
    for(let i=0;i<100;i++){if((await upload({action:'status',ticket:interrupted})).retained)break;await pause(5);}
    assert((await upload({action:'status',ticket:interrupted})).retained,'Cancellation fixture did not reach native assignment');
    assert((await bridge.call('state',{browser})).input_busy,'Upload did not retain its native input slot');
    let failed=0;
    for(const [command,params] of [['input',{kind:'click',x:10,y:10}],['capture',{format:'png',capture_key:'during-upload',clip:{x:0,y:0,width:10,height:10}}],['drag',{drag_key:'during-upload',x:10,y:10,to_x:20,to_y:20}]]){
      try{await bridge.call(command,{browser,navigation:state.navigation,agent:true,...params});}catch{failed++;}
    }
    assert(failed===3,'A competing input/capture/drag overlapped native file assignment');
    await rejected({action:'release',ticket:interrupted,confirmed:true},'An in-flight upload released its files');
    const cancelled=await upload({action:'cancel',ticket:interrupted});result=await pending;await idle();
    assert(cancelled.retained&&result.dispatched&&result.error.effect_may_have_occurred,'Cancellation lost the uncertain website effect');
    await consumed('single',2);await release(interrupted);
    const dialog=await stage();await read('document.querySelector("#single").dataset.mode="dialog"');
    result=await assign(dialog,await target('#single'));
    assert(result.outcome==='dialog_open'&&result.dispatched&&result.retained,'File change dialog did not return a resumable checkpoint');
    const dialogs=await bridge.call('dialog',{browser,action:'list'});assert(dialogs.dialogs.length===1,'File upload dialog was lost');
    await bridge.call('dialog',{browser,action:'respond',dialog:dialogs.dialogs[0].id,accept:true});await idle();await consumed('single',3);await release(dialog);
    const popup=await stage();await read('document.querySelector("#multiple").dataset.mode="popup"');
    result=await assign(popup,await target('#multiple'));
    assert(result.outcome==='popup_open'&&result.dispatched&&result.retained,'File change popup did not return a resumable checkpoint');
    const popups=await bridge.call('popups',{browser,action:'list'});assert(popups.popups.length===1,'File upload popup was lost');
    await bridge.call('popups',{browser,action:'dismiss',popup:popups.popups[0].id});await idle();await consumed('multiple',2);await release(popup);
    const navigated=await stage();await read('document.querySelector("#single").dataset.mode=""');
    assert((await assign(navigated,await target('#single'))).verified,'Pre-navigation assignment failed');await consumed('single',4);
    await bridge.call('navigate',{browser,url:url+'?next=1'});
    for(let i=0;i<150;i++){const next=await bridge.call('state',{browser});if(!next.loading&&next.navigation!==state.navigation){state=next;break;}await pause(20);}
    assert((await upload({action:'status',ticket:navigated})).retained,'Navigation prematurely deleted already assigned file bytes');await release(navigated);
    const timed=await stage();await read('document.querySelector("#hidden").dataset.mode="busy"');
    result=await assign(timed,await target('#hidden'),{timeout_ms:250});
    assert(!result.accepted&&result.dispatched&&result.retained&&result.error.effect_may_have_occurred,'Expired native assignment lost its uncertain page effect');
    await idle();await consumed('hidden',1);
    assert((await assign(timed,await target('#hidden'))).error.effect_may_have_occurred,'Repeating a timed-out assignment became replayable');
    assert((await read('uploadEvents.filter(x=>x.id==="hidden"&&x.type==="change")')).length===1,'Timeout recovery repeated the native file assignment');
    await release(timed);
    const closing=await stage(),closeTarget=await target('#single');await read('document.querySelector("#single").dataset.mode="busy"');
    const closed=assign(closing,closeTarget);for(let i=0;i<100;i++){if((await bridge.call('state',{browser})).input_busy)break;await pause(5);}
    await bridge.call('close',{browser});await closed;await create();await pause(100);
    assert(!(await bridge.call('state',{browser})).input_busy,'Closed assignment poisoned a replacement browser');
    await rejected({action:'status',ticket:closing},'Closed browser leaked an assigned file ticket');
    const replacement=await stage();assert((await assign(replacement,await target('#single'))).verified,'Replacement browser could not assign fresh files');await consumed('single',1);await release(replacement);
  }finally{try{await bridge.call('close',{browser});}catch{}}
}
