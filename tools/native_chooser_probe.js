// Real trusted clicks must suppress OS dialogs and retain exact picker targets.
async function runChooserProbe({bridge,assert,pause,runtimeSource}){
  const browser='browser-use-chooser-probe',url=location.origin+'/__browser_use_chooser__.html';let state;
  const call=(action,params={})=>bridge.call('chooser',{browser,agent:true,action,...params});
  const read=async script=>{const result=await bridge.call('evaluate',{browser,script});assert(result.ok,'Chooser fixture evaluation failed');return result.value;};
  const engine=async expression=>{const result=await bridge.call('automation',{browser,agent:true,script:`(${runtimeSource})().${expression}`});assert(result.ok,'Chooser isolated evaluation failed');return result.value;};
  const ready=async()=>{
    for(let i=0;i<200;i++){state=await bridge.call('state',{browser});if(!state.loading&&await read('globalThis.fixtureReady===true'))return;await pause(20);}
    assert(false,'Chooser fixture did not become ready');
  };
  const idle=async()=>{for(let i=0;i<250;i++){state=await bridge.call('state',{browser});if(!state.input_busy)return;await pause(20);}assert(false,'Chooser input/policy operation did not drain');};
  const rejected=async(promise,message)=>{let failed=false;try{const value=await promise;failed=value.ok===false||value.accepted===false;}catch{failed=true;}assert(failed,message);};
  const listed=()=>call('list');
  const ticket=async()=>{for(let i=0;i<150;i++){const value=await listed();if(value.choosers.length)return value.choosers[0];await pause(10);}assert(false,'Trusted click did not expose its intercepted chooser');};
  const click=async css=>{
    const doc=await engine('observe({})'),point=await engine(`prepare(${JSON.stringify({document:doc.document,op:'click',target:{css},scroll:true})})`);
    assert(point.ok,'Picker trigger is not actionable: '+css+' '+JSON.stringify(point.error));state=await bridge.call('state',{browser});
    return bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,...point.point});
  };
  const cancel=async pending=>{const result=await call('cancel',{chooser:pending.id});assert(result.ok&&result.cancelled&&result.events_trusted===false,'Chooser cancellation was not explicit/untrusted');await idle();return result;};
  let retained;
  try{
    await bridge.call('create',{browser,url,visible:true,bounds:{x:10,y:70,width:1000,height:650,scale:devicePixelRatio}});await ready();
    assert(state.chooser_capability&&!state.chooser_armed&&!state.chooser_open,'Chooser capability/default interception state is incorrect');
    assert((await listed()).choosers.length===0,'New browser retained another picker');
    const observed=await engine('observe({})'),target=await engine(`uploadInfo({document:${JSON.stringify(observed.document)},target:{css:'#file'}})`);
    const bytes=Uint8Array.of(0,1,2,255),digest=[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(x=>x.toString(16).padStart(2,'0')).join('');
    const upload=params=>bridge.call('upload',{browser,agent:true,navigation:state.navigation,...params});
    retained=(await upload({action:'begin',upload_key:'chooser-existing-file',files:[{name:'picker.bin',size:4,sha256:digest}]})).ticket;
    await upload({action:'chunk',ticket:retained,file:0,offset:0,base64:btoa(String.fromCharCode(...bytes))});await upload({action:'seal',ticket:retained});
    assert((await upload({action:'assign',ticket:retained,document:observed.document,ref:target.element.ref})).verified,'Cannot prepare preexisting chooser FileList');
    await read('globalThis.retainedFile=document.querySelector("#file").files[0]');
    assert((await call('arm')).armed,'Chooser interception was not acknowledged');
    const input=await click('#open'),pending=await ticket();await idle();
    assert(pending.supported&&!pending.multiple&&pending.id.startsWith('chooser-'),'Opaque single-file picker metadata is invalid');
    // CDP can acknowledge mouseReleased before forwarding fileChooserOpened.
    // The shared dispatcher must inspect chooser state after input/verification;
    // a low-level accepted input alone is not a completed picker operation.
    assert(input.accepted&&input.trusted_input,'Picker click was not accepted as native input');
    const serialized=JSON.stringify(await listed());assert(!/backendNodeId|frameId|picker\.bin|[A-Z]:\\/.test(serialized),'Picker metadata exposed private protocol/path values');
    await rejected(bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,x:10,y:10}),'Pending picker allowed another native input');
    await rejected(bridge.call('drag',{browser,agent:true,drag_key:'pending-chooser',navigation:state.navigation,x:10,y:10,to_x:20,to_y:20}),'Pending picker allowed drag');
    await rejected(upload({action:'clear',upload_key:'blocked-picker-clear',document:observed.document,ref:target.element.ref}),'Pending picker allowed a competing clear');
    await rejected(call('cancel',{chooser:'foreign-picker'}),'A foreign picker identity cancelled the pending picker');
    const stalePin=await call('target',{chooser:pending.id,document:'obsolete-document'});assert(!stalePin.ok&&stalePin.error.code==='stale_document','Stale picker document was accepted');await idle();
    const pinned=await call('target',{chooser:pending.id,document:observed.document});
    assert(pinned.ok&&pinned.element.ref===target.element.ref,'Chooser did not resolve the actual preexisting hidden input');
    await idle();await rejected(call('target',{chooser:pending.id,document:'another-document'}),'Chooser target silently changed document identity');
    await cancel(pending);
    assert(await read('document.querySelector("#file").files.length')===1,'Cancel cleared the previously selected FileList');
    const event=await read('chooserEvents.at(-1)');assert(event.id==='file'&&event.count===1&&!event.trusted,'Cancellation event lost selection or falsely claimed trust');
    assert(await read('retainedFile.size')===4,'Cancel lost the previously retained File');
    await rejected(call('cancel',{chooser:pending.id}),'Consumed picker could be cancelled twice');
    assert((await listed()).choosers.length===0,'Cancelled picker remained listed');
    await click('#multi');const firstPending=await ticket();await idle();
    await read('(()=>{document.querySelector("#file").click();return true})()');
    const flood=await listed();assert(flood.choosers.length===1&&flood.choosers[0].id===firstPending.id,'A later picker replaced the first retained ticket');
    await cancel(firstPending);
    await click('#multi');const accepting=await ticket();await idle();
    const acceptedTarget=await call('target',{chooser:accepting.id,document:observed.document});assert(acceptedTarget.ok,'Cannot resolve the accepted picker');await idle();
    const manifest={action:'begin',upload_key:'chooser-accept-files',chooser:accepting.id,document:observed.document,ref:acceptedTarget.element.ref,files:[{name:'picker.bin',size:4,sha256:digest}]};
    await rejected(upload({...manifest,chooser:'foreign-picker'}),'Foreign picker admitted source bytes');
    const acceptingTicket=(await upload(manifest)).ticket;
    await upload({action:'chunk',ticket:acceptingTicket,file:0,offset:0,base64:btoa(String.fromCharCode(...bytes))});await upload({action:'seal',ticket:acceptingTicket});
    await rejected(upload({action:'assign',ticket:acceptingTicket,document:observed.document,ref:target.element.ref}),'Picker-bound bytes were assigned to another input');
    const cancelCount=await read('chooserEvents.length');
    const assigned=await upload({action:'assign',ticket:acceptingTicket,document:observed.document,ref:acceptedTarget.element.ref});
    assert(assigned.verified&&assigned.retained,'Picker-bound assignment was not verified: '+JSON.stringify(assigned));
    for(let i=0;i<150;i++){if(await read('chooserSelections.some(row=>row.id==="many"&&row.files[0]?.hex==="000102ff")'))break;await pause(20);}
    assert(await read('chooserSelections.some(row=>row.id==="many"&&row.files[0]?.hex==="000102ff")'),'Website could not read accepted picker bytes');
    assert(!(await listed()).choosers.length,'Successful assignment did not consume its exact picker');
    assert(await read('chooserEvents.length')===cancelCount,'Accepting files emitted an additional cancel event');
    assert((await upload({action:'assign',ticket:acceptingTicket,document:observed.document,ref:acceptedTarget.element.ref})).verified,'Accepted picker assignment lost its idempotent receipt');
    await upload({action:'release',ticket:acceptingTicket,confirmed:true});
    for(const [css,id,multiple] of [['#show','file',false],['#multi','many',true],['#shadow-open','shadowed',false],['#frame-open','framed',false]]){
      await click(css);const next=await ticket();assert(next.id!==pending.id&&next.multiple===multiple,'Picker identities or modes were reused');await idle();
      const pin=await call('target',{chooser:next.id,document:observed.document});assert(pin.ok&&pin.element.input_type==='file','Actual shadow/frame picker could not be pinned: '+JSON.stringify(pin));await idle();
      await cancel(next);assert((await read('chooserEvents.at(-1)')).id===id,'Cancel targeted a different picker input');
    }
    // A page-world replacement must not intercept the native cancellation code.
    await read("(()=>{globalThis.originalDispatch=EventTarget.prototype.dispatchEvent;EventTarget.prototype.dispatchEvent=function(){throw Error('main-world poison')};return true})()");
    await click('#open');await cancel(await ticket());
    await read('(()=>{EventTarget.prototype.dispatchEvent=originalDispatch;return true})()');
    assert((await read('chooserEvents.at(-1)')).id==='file','Page-world override hijacked isolated cancellation');
    for(const mode of ['dialog','popup']){
      await read('globalThis.cancelMode='+JSON.stringify(mode));await click('#open');const next=await ticket();await idle();
      const checkpoint=await call('cancel',{chooser:next.id});assert(checkpoint.outcome===mode+'_open'&&checkpoint.completed===false,'Cancel handler did not yield its '+mode+' checkpoint: '+JSON.stringify(checkpoint));
      const response=await bridge.call(mode==='dialog'?'dialog':'popups',{browser,action:'list'}),items=response[mode==='dialog'?'dialogs':'popups'];assert(items.length===1,'Chooser cancellation checkpoint disappeared');
      if(mode==='dialog')await bridge.call('dialog',{browser,action:'respond',dialog:items[0].id,accept:true});
      else await bridge.call('popups',{browser,action:'dismiss',popup:items[0].id});
      await idle();await read("globalThis.cancelMode=''");
    }
    await call('arm',{timeout_ms:1000});await click('#open');const expiring=await ticket();await idle();
    for(let i=0;i<150;i++){if(!(await listed()).choosers.length&&!(await bridge.call('state',{browser})).input_busy)break;await pause(20);}
    assert(!(await listed()).choosers.length,'Expired picker did not release its bounded reservation');
    await rejected(call('target',{chooser:expiring.id,document:observed.document}),'Expired chooser was retargeted');
    await call('arm');await click('#open');const manual=await ticket();await idle();
    await bridge.call('takeover',{browser});
    for(let i=0;i<150;i++){const value=await listed();if(!value.armed&&!value.changing&&!value.choosers.length)break;await pause(20);}
    assert(!(await listed()).armed&&!(await listed()).changing,'Manual takeover did not disable interception and drain cancellation');
    await rejected(call('arm'),'Agent rearmed interception during manual control');await rejected(call('cancel',{chooser:manual.id}),'Agent cancelled a picker after manual takeover');
    assert(await read('document.querySelector("#file").files.length')===1,'Takeover cleared old selected files');
    await rejected(bridge.call('release',{browser,agent:true}),'Agent returned control without the user');await bridge.call('release',{browser});
    assert(!(await listed()).armed,'Returning control silently replayed the old picker request');
    await call('arm');await click('#open');const obsolete=await ticket();await idle();
    await bridge.call('navigate',{browser,url:url+'?next=1'});await ready();
    assert(!(await listed()).choosers.length,'Navigation retained an obsolete picker');
    await rejected(call('cancel',{chooser:obsolete.id}),'Old document chooser reached the next document');
    assert((await read('chooserEvents')).length===0,'Navigation cancellation dispatched an event into the next document');
    await upload({action:'release',ticket:retained,confirmed:true});retained=null;
    await call('disarm');assert(!(await listed()).armed,'Explicit interception disarm did not complete');
    let manualControls=0;const uiErrors=[];
    const ui=createBrowserChoosers({execute:(command,params={})=>bridge.call(command,{browser,...params}),onControl:()=>{manualControls++;},onError:error=>uiErrors.push(String(error))});
    document.body.append(ui.element);
    try{
      await call('arm');await click('#multi');await ticket();await idle();await ui.refresh(await bridge.call('state',{browser}));
      assert(!ui.element.hidden&&ui.element.textContent.includes('多个文件'),'Visible picker controls did not show the current picker mode');
      await ui.refresh({...await bridge.call('state',{browser}),dialog_open:true});assert(ui.element.hidden,'Picker buttons remained actionable over a modal checkpoint');
      await ui.refresh(await bridge.call('state',{browser}));assert(!ui.element.hidden,'Picker controls did not resume after a modal checkpoint');
      ui.element.querySelector('button').click();
      for(let i=0;i<150;i++){if(!(await listed()).choosers.length&&ui.element.hidden)break;await pause(20);}
      assert(ui.element.hidden&&!manualControls,'The cancel UI did not hide, or took over without being asked');
      await idle();await click('#multi');await ticket();await idle();await ui.refresh(await bridge.call('state',{browser}));
      ui.element.querySelectorAll('button')[1].click();
      for(let i=0;i<150;i++){const current=await bridge.call('state',{browser});if(current.manual&&!current.input_busy&&!current.chooser_armed&&!current.chooser_open&&ui.element.hidden)break;await pause(20);}
      assert(ui.element.hidden&&manualControls===1&&!uiErrors.length,'Picker takeover UI failed or hid a native error: '+JSON.stringify(uiErrors));
      assert((await bridge.call('state',{browser})).manual,'The takeover button did not return browser control to the user');
    }finally{ui.dispose();ui.element.remove();}
    let resolveLate;
    const late=createBrowserChoosers({execute:()=>new Promise(resolve=>{resolveLate=resolve;})});
    const refreshing=late.refresh({chooser_open:true});late.dispose();resolveLate({choosers:[{id:'obsolete'}]});await refreshing;
    assert(late.element.hidden,'An obsolete picker refresh revived disposed UI');
    await bridge.call('release',{browser});
    await call('arm');await click('#multi');await ticket();await idle();await bridge.call('close',{browser});
    await bridge.call('create',{browser,url,visible:false});await ready();assert(!(await listed()).armed&&!(await listed()).choosers.length,'Recreated browser inherited the previous picker or interception');
  }finally{await bridge.call('close',{browser}).catch(()=>{});}
}
