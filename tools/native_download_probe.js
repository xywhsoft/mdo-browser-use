async function runDownloadProbe({bridge,assert,pause,runtimeSource,origin}){
  const browser='browser-use-download-probe',url=origin+'/page';let state;
  const call=(action,params={})=>bridge.call('downloads',{browser,agent:true,action,...params});
  const read=async script=>{const result=await bridge.call('evaluate',{browser,script});assert(result.ok,'Download fixture evaluation failed');return result.value;};
  const engine=async expression=>{const result=await bridge.call('automation',{browser,agent:true,script:`(${runtimeSource})().${expression}`});assert(result.ok,'Download isolated evaluation failed');return result.value;};
  const ready=async()=>{for(let i=0;i<150;i++){state=await bridge.call('state',{browser});if(!state.loading&&await read('globalThis.fixtureReady===true'))return;await pause(20);}assert(false,'Download fixture did not load: '+JSON.stringify({state,document:await read('({ready:document.readyState,url:location.href,fixture:globalThis.fixtureReady===true})')}));};
  const reject=async(promise,message)=>{let rejected=false;try{await promise;}catch{rejected=true;}assert(rejected,message);};
  const click=async css=>{const doc=await engine('observe({})'),prepared=await engine(`prepare(${JSON.stringify({document:doc.document,op:'click',target:{css},scroll:true})})`);
    assert(prepared.ok,'Download control is not actionable: '+css);state=await bridge.call('state',{browser});
    await bridge.call('input',{browser,agent:true,kind:'click',navigation:state.navigation,...prepared.point});
  };
  const offer=async(css)=>{const before=new Set((await call('list')).downloads.map(row=>row.id));await click(css);
    for(let i=0;i<150;i++){const next=(await call('list')).downloads.find(row=>!before.has(row.id));if(next)return next;await pause(20);}assert(false,'No download offer for '+css);
  };
  const terminal=async(id,request=call)=>{let next;for(let i=0;i<1000;i++){next=await request('get',{download:id});if(['completed','cancelled','failed'].includes(next.state))return next;await pause(20);}assert(false,'Download did not become terminal: '+JSON.stringify({current:next,all:await call('list')}));};
  const release=async(id,request=call)=>{let requested=false;for(let i=0;i<150;i++){try{const done=await request('release',{download:id});assert(done.released,'Native release has no confirmation');return;}catch(error){
    if(requested&&!(await request('list')).downloads.some(row=>row.id===id))return;requested=true;if(i===149)throw error;await pause(20);
  }}};
  const digest=async data=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',data))].map(x=>x.toString(16).padStart(2,'0')).join('');
  const bytes=Uint8Array.from({length:131075},(_,i)=>i%251),sha=await digest(bytes);
  const consume=async(job,expected=bytes,request=call)=>{
    assert(job.state==='completed'&&job.size===expected.length&&job.sha256===await digest(expected),'Download size/hash was not fully verified: '+JSON.stringify(job));
    const data=new Uint8Array(job.size);let offset=0;
    do{const chunk=await request('read',{download:job.id,offset,limit:65536});assert(chunk.offset===offset&&chunk.next===offset+chunk.bytes&&chunk.sha256===job.sha256,'Download range lost its verified identity');
      const decoded=Uint8Array.from(atob(chunk.base64),c=>c.charCodeAt(0));assert(decoded.length===chunk.bytes,'Download byte count does not match Base64');data.set(decoded,offset);offset=chunk.next;
      if(chunk.eof)break;assert(chunk.bytes>0,'Download range made no progress');
    }while(offset<job.size);
    assert(await digest(data)===job.sha256,'Read download ranges differ from verified bytes');
    const eof=await request('read',{download:job.id,offset:job.size});assert(eof.eof&&eof.bytes===0&&eof.base64==='','Empty EOF range is not exact');
  };
  try{
    await bridge.call('create',{browser,url,visible:true,bounds:{x:10,y:70,width:1000,height:650,scale:devicePixelRatio}});await ready();
    assert(state.download_capability&&!state.downloads_armed,'Native download capability/default state is incorrect');assert((await call('arm')).armed,'Cannot arm native downloads');
    await reject(call('arm',{timeout_ms:0}),'Invalid offer deadline was accepted');
    for(const css of ['#binary','#empty','#blob','#post','#unknown']){
      const pending=await offer(css);assert(pending.state==='pending'&&pending.id.startsWith('d-'),'Download was not intercepted before acceptance');
      if(css==='#binary'){const observed=await call('list');assert(observed.protocol_ready&&observed.protocol_begin_count>0,'Page download identities were not observable before acceptance: '+JSON.stringify(observed));}
      await pause(100);const navigationState=await bridge.call('state',{browser});assert(!navigationState.error,'Intercepted response poisoned page navigation: '+JSON.stringify({css,error:navigationState.error,error_code:navigationState.error_code,navigation:navigationState.navigation,navigation_events:navigationState.navigation_events}));
      const encoded=JSON.stringify(pending);assert(!encoded.includes('fixture-query')&&!encoded.includes('127.0.0.1')&&!/[A-Z]:\\/.test(encoded),'Download metadata leaked URI or cache path');
      await reject(call('read',{download:pending.id,offset:0}),'Pending download bytes could be read');
      const accepted=await call('accept',{download:pending.id});assert(['receiving','verifying','completed'].includes(accepted.state),'Native download did not start: '+JSON.stringify(accepted));
      const saved=await terminal(pending.id);await consume(saved,css==='#empty'?new Uint8Array():bytes);
      await reject(call('read',{download:saved.id,offset:saved.size+1}),'Out-of-file download range was accepted');await reject(call('read',{download:saved.id,offset:0,limit:65537}),'Oversized native read range was accepted');
      assert((await call('accept',{download:saved.id})).sha256===saved.sha256,'Repeated native acceptance did not retain its original result');
      await release(saved.id);await reject(call('get',{download:saved.id}),'Released download remained accessible');
    }
    const retained=await offer('#binary');await call('accept',{download:retained.id});await consume(await terminal(retained.id));
    await bridge.call('navigate',{browser,url:origin+'/broken-navigation'});
    let disconnected;for(let i=0;i<250;i++){disconnected=await bridge.call('state',{browser});if(!disconnected.loading&&disconnected.error)break;await pause(20);}
    assert(Boolean(disconnected.error)&&disconnected.error_code!==14,'A retained older download masked a real failed navigation: '+JSON.stringify(disconnected));
    assert((await call('get',{download:retained.id})).state==='completed','Network failure invalidated a completed response');await release(retained.id);
    await bridge.call('navigate',{browser,url});await ready();
    const pairA=await offer('#binary'),pairB=await offer('#binary');assert(pairA.id!==pairB.id,'Identical URLs reused an opaque download identity');
    const foreign='browser-use-download-foreign';
    await bridge.call('create',{browser:foreign,url,visible:false});
    try{
      for(let i=0;i<150;i++){if(!(await bridge.call('state',{browser:foreign})).loading)break;await pause(20);}
      for(const [action,fields] of [['get',{}],['cancel',{}],['accept',{}],['release',{}],['read',{offset:0}]])
        await reject(bridge.call('downloads',{browser:foreign,agent:true,action,download:pairA.id,...fields}),'Another browser could '+action+' this download');
      assert((await call('get',{download:pairA.id})).state==='pending','Foreign commands changed the owned download');
    }finally{await bridge.call('close',{browser:foreign});}
    await call('accept',{download:pairA.id});await call('accept',{download:pairB.id});
    await consume(await terminal(pairA.id));await consume(await terminal(pairB.id));await release(pairA.id);await release(pairB.id);
    const large=await offer('#large'),denied=await call('accept',{download:large.id,max_bytes:1048576});assert(denied.state==='failed'&&denied.error==='download_limit','Known oversized download was not rejected before writing');await release(large.id);
    const unknown=await offer('#unknown');await call('accept',{download:unknown.id,max_bytes:65536});const over=await terminal(unknown.id);assert(over.state==='failed'&&over.error==='download_limit','Unknown-length download exceeded its completion limit');await reject(call('read',{download:unknown.id,offset:0}),'Unverified oversize bytes were exposed');await release(unknown.id);
    const cancelled=await offer('#slow');await call('accept',{download:cancelled.id});
    for(let i=0;i<150;i++){if((await call('get',{download:cancelled.id})).received>0)break;await pause(20);}
    await call('cancel',{download:cancelled.id});const stopped=await terminal(cancelled.id);assert(stopped.state==='cancelled','Native cancellation did not become terminal');await release(cancelled.id);
    const expired=await offer('#slow');await call('accept',{download:expired.id,timeout_ms:1000});const timed=await terminal(expired.id);assert(timed.state==='failed'&&timed.error==='download_timed_out','Download timeout was not a non-replayed terminal result');await release(expired.id);
    const interrupted=await offer('#interrupt');await call('accept',{download:interrupted.id});const failed=await terminal(interrupted.id);assert(failed.state==='failed'&&failed.error==='download_interrupted'&&failed.interrupt_reason>0,'Native interruption lost its actual reason: '+JSON.stringify(failed));await release(interrupted.id);
    const truncated=await offer('#truncated');await call('accept',{download:truncated.id,timeout_ms:3000});const incomplete=await terminal(truncated.id);
    assert(incomplete.state==='failed'&&['download_interrupted','download_timed_out'].includes(incomplete.error),'Truncated response was claimed complete or never bounded: '+JSON.stringify(incomplete));
    assert(incomplete.failure_source==='page_download_progress','Truncated response did not retain its authoritative page failure: '+JSON.stringify(incomplete));
    assert((await call('list')).downloads.length===1,'Browser resumption created another unclaimed download ticket');
    await reject(call('read',{download:truncated.id,offset:0}),'Incomplete response bytes were exposed');await release(truncated.id);
    await call('arm',{timeout_ms:1000});const lease=await offer('#empty');const leased=await terminal(lease.id);assert(leased.error==='download_offer_expired','Unaccepted download did not expire');await release(lease.id);await call('arm');
    const navigating=await offer('#binary');await call('accept',{download:navigating.id});await bridge.call('navigate',{browser,url:url+'?next=1'});await ready();const navigated=await terminal(navigating.id);await consume(navigated);await release(navigating.id);
    const manual=await offer('#slow');await call('accept',{download:manual.id});await bridge.call('takeover',{browser});const taken=await terminal(manual.id);assert(taken.state==='failed'&&taken.error==='download_taken_over','Takeover did not stop its owned download');
    assert(!(await call('list')).armed,'Takeover left Agent downloads armed');await reject(call('accept',{download:manual.id}),'Agent started a download during manual control');
    await bridge.call('release',{browser});await release(manual.id);await call('arm');
    assert((await bridge.call('capabilities')).initial_download_interception,'Host does not declare pre-navigation interception');
    const initial='browser-use-initial-download';
    const initialCall=(action,params={})=>bridge.call('downloads',{browser:initial,agent:true,action,...params});
    const awaitOffer=async request=>{for(let i=0;i<200;i++){const rows=(await request('list')).downloads;const pending=rows.find(row=>row.state==='pending');if(pending)return pending;await pause(20);}assert(false,'Initial/popup response was not intercepted: '+JSON.stringify(await request('list')));};
    for(const route of ['/binary?initial-url=1','/auto-download']){
      try{
        const created=await bridge.call('create',{browser:initial,url:origin+route,visible:false,intercept_downloads:true});
        assert(created.downloads_intercepted&&!created.manual,'Initial URL ran without policy acknowledgement');
        const pending=await awaitOffer(initialCall);assert(pending.received===0,'Initial response was accepted without a caller decision');
        await initialCall('accept',{download:pending.id});await consume(await terminal(pending.id,initialCall),bytes,initialCall);await release(pending.id,initialCall);
        const attached=await bridge.call('create',{browser:initial,url:origin+'/binary?must-not-replay=1',visible:false,intercept_downloads:true});assert(attached.attached,'Attaching an existing browser replayed its initial navigation');
      }finally{await bridge.call('close',{browser:initial});}
    }
    await click('#post-popup');let popup;
    for(let i=0;i<150;i++){popup=(await bridge.call('popups',{browser,action:'list'})).popups[0];if(popup)break;await pause(20);}
    assert(Boolean(popup),'POST popup did not provide a native admission');
    const child='browser-use-download-child',childCall=(action,params={})=>bridge.call('downloads',{browser:child,agent:true,action,...params});
    try{
      const adopted=await bridge.call('create',{browser:child,popup:popup.id,opener:browser,visible:false});
      assert(adopted.popup&&adopted.downloads_intercepted,'Popup adoption did not inherit acknowledged interception');
      const pending=await awaitOffer(childCall);await childCall('accept',{download:pending.id});await consume(await terminal(pending.id,childCall),bytes,childCall);
      let retainedState;for(let i=0;i<100;i++){retainedState=await bridge.call('state',{browser:child});if(retainedState.close_requested)break;await pause(20);}
      assert(retainedState.close_requested,'The native popup-close regression did not exercise a page close request');
      assert(!retainedState.closed_by_page,'The popup requested automatic cleanup before its completed response was released: '+JSON.stringify({closed_by_page:retainedState.closed_by_page,navigation:retainedState.navigation,transfer:await childCall('get',{download:pending.id})}));
      await reject(bridge.call('input',{browser:child,agent:true,kind:'key',key:'Enter',navigation:retainedState.navigation}),'A retained closing popup accepted another page input');
      await reject(bridge.call('navigate',{browser:child,agent:true,url:origin+'/binary?closed-must-not-navigate=1'}),'A retained closing popup accepted another Agent navigation');
      await release(pending.id,childCall);
      let releasedState;for(let i=0;i<100;i++){releasedState=await bridge.call('state',{browser:child});if(releasedState.closed_by_page)break;await pause(20);}
      assert(releasedState.closed_by_page,'Releasing the final response suppressed the original page close request');
      assert(!(await call('list')).downloads.length,'A popup response leaked into the opener download inventory');
    }finally{await bridge.call('close',{browser:child});}
    const closed=await offer('#slow');await call('accept',{download:closed.id});await bridge.call('close',{browser});await pause(250);
    await bridge.call('create',{browser,url,visible:false});await ready();assert(!(await call('list')).downloads.length&&!(await call('list')).armed,'Recreated browser inherited old download state');
    return{closed_download:closed.id,truncated_download:truncated.id};
  }finally{await bridge.call('close',{browser}).catch(()=>{});}
}
