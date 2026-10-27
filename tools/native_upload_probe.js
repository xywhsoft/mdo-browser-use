// Embedded into the single native probe document; never shipped as a tool.
async function runUploadProbe({bridge,assert,pause}){
  const browser="browser-use-upload-probe",foreign="browser-use-upload-foreign";
  const url=location.origin+"/__browser_use_page__.html";
  const create=async id=>{
    await bridge.call("create",{browser:id,url,visible:false});
    let state;const began=performance.now();
    for(let i=0;i<100;i++){state=await bridge.call("state",{browser:id});if(!state.loading&&state.url===url)break;await pause(20);}
    if(state.loading){
      const before=state,documentState=await bridge.call('evaluate',{browser:id,script:`({ready:document.readyState,frames:[...document.querySelectorAll('iframe')].map(frame=>{try{return{ready:frame.contentDocument?.readyState||'opaque'}}catch{return{ready:'opaque'}}}),timing:performance.getEntriesByType('navigation').map(entry=>({dom:entry.domContentLoadedEventEnd,load:entry.loadEventEnd,response:entry.responseEnd}))})`});
      await bridge.call('bounds',{browser:id,visible:true,bounds:{x:10,y:70,width:640,height:480,scale:devicePixelRatio}});
      for(let i=0;i<100;i++){state=await bridge.call('state',{browser:id});if(!state.loading)break;await pause(20);}
      assert(false,'Hidden upload navigation stayed pending: '+JSON.stringify({id,elapsed_ms:Math.round(performance.now()-began),before,documentState,after_show:state}));
    }
    assert(!state.loading&&state.upload_capability,"Native upload staging capability/readiness is missing: "+JSON.stringify({id,elapsed_ms:Math.round(performance.now()-began),state}));return state;
  };
  let state=await create(browser);await create(foreign);
  const upload=params=>bridge.call("upload",{browser,navigation:state.navigation,...params});
  const rejected=async(params,message,other=false)=>{
    let failed=false;try{await (other?bridge.call("upload",{browser:foreign,...params}):upload(params));}catch{failed=true;}
    assert(failed,message);
  };
  const hash=async bytes=>[...new Uint8Array(await crypto.subtle.digest("SHA-256",bytes))].map(value=>value.toString(16).padStart(2,"0")).join("");
  const encode=bytes=>{let text="";for(const value of bytes)text+=String.fromCharCode(value);return btoa(text);};
  const empty=await hash(new Uint8Array()),source=Uint8Array.from({length:131075},(_,i)=>i%251);
  const files=[{name:"source-中文.bin",size:source.length,sha256:await hash(source)},{name:"empty.txt",size:0,sha256:empty}];
  const begin=(key,files,extra={})=>upload({action:"begin",upload_key:key,files,...extra});
  try{
    for(const name of ["../escape","CON.txt","LPT9.bin","COM¹.txt","LPT².bin","com³.txt","CONIN$.txt","CON .txt","trailing.","trailing ","a\u0000b","/absolute","a:b"])
      await rejected({action:"begin",upload_key:"invalid-name",files:[{name,size:0,sha256:empty}]},"Native staging accepted an unsafe basename");
    await rejected({action:"begin",upload_key:"empty-manifest",files:[]},"An empty upload manifest was accepted");
    await rejected({action:"begin",upload_key:"too-many",files:Array.from({length:17},()=>({name:"file.txt",size:0,sha256:empty}))},"Native staging exceeded sixteen files");
    await rejected({action:"begin",upload_key:"large-file",files:[{name:"file.txt",size:33554433,sha256:empty}]},"Native staging exceeded its per-file reservation");
    await rejected({action:"begin",upload_key:"large-total",files:Array.from({length:3},()=>({name:"file.txt",size:33554432,sha256:empty}))},"Native staging exceeded its total reservation");
    const main=await begin("main-upload",files),ticket=main.ticket;
    assert((await begin("main-upload",files)).ticket===ticket,"An identical upload admission created another staging copy");
    await rejected({action:"begin",upload_key:"main-upload",files:[{name:"different.txt",size:0,sha256:empty}]},"An existing upload key accepted a different manifest");
    await rejected({action:"status",ticket},"A foreign browser inspected an upload ticket",true);
    await rejected({action:"cancel",ticket},"A foreign browser cancelled an upload ticket",true);
    await rejected({action:"seal",ticket},"Incomplete native staging was sealed");
    await rejected({action:"chunk",ticket,file:0,offset:1,base64:"eA=="},"An out-of-order upload chunk was accepted");
    await rejected({action:"chunk",ticket,file:0,offset:0,base64:"Zh=="},"Noncanonical Base64 reached native file storage");
    for(let offset=0;offset<source.length;offset+=65536){
      const base64=encode(source.subarray(offset,offset+65536)),received=Math.min(offset+65536,source.length);
      const once=await upload({action:"chunk",ticket,file:0,offset,base64});
      assert(once.files[0].received===received,"Native chunk receipt lost its exact byte cursor");
      const replay=await upload({action:"chunk",ticket,file:0,offset,base64});
      assert(replay.files[0].received===received,"A repeated chunk duplicated native file bytes");
    }
    const sealed=await upload({action:"seal",ticket});
    assert(sealed.sealed&&sealed.files[0].received===source.length&&sealed.files[1].received===0,"Complete binary/empty staging did not verify its SHA-256");
    assert(!JSON.stringify(sealed).includes("base64")&&!JSON.stringify(sealed).includes("directory"),"Native staging exposed file contents or private cache paths");
    assert((await upload({action:"seal",ticket})).sealed,"Repeating seal changed a verified native snapshot");
    await rejected({action:"chunk",ticket,file:0,offset:0,base64:"eA=="},"A sealed snapshot accepted another write");
    const reserved=[{name:"one.bin",size:33554432,sha256:empty},{name:"two.bin",size:33554432,sha256:empty}];
    const second=await begin("reserved-only",reserved);
    const slots=[];
    for(let i=0;i<14;i++)slots.push(await begin(`bounded-slot-${i}`,[{name:"empty.txt",size:0,sha256:empty}]));
    await rejected({action:"begin",upload_key:"seventeenth-owned",files:[{name:"empty.txt",size:0,sha256:empty}]},"One browser exceeded sixteen staging/retained reservations");
    for(const slot of slots)await upload({action:"cancel",ticket:slot.ticket});
    const foreignState=await bridge.call("state",{browser:foreign});
    await rejected({action:"begin",navigation:foreignState.navigation,upload_key:"global-budget",files:reserved},"Two browsers exceeded the global byte reservation",true);
    assert((await upload({action:"cancel",ticket:second.ticket})).cancelled,"Cancellation did not acknowledge its exact staging ticket");
    await rejected({action:"status",ticket:second.ticket},"Cancelled staging remained accessible");
    const wrong=await begin("bad-digest",[{name:"bad.txt",size:1,sha256:"0".repeat(64)}]);
    await upload({action:"chunk",ticket:wrong.ticket,file:0,offset:0,base64:"eA=="});
    await rejected({action:"seal",ticket:wrong.ticket},"Mismatched native bytes were sealed");
    await rejected({action:"status",ticket:wrong.ticket},"A failed digest retained an accessible native ticket");
    const conflicting=await begin("bad-replay",[{name:"replay.txt",size:1,sha256:await hash(new Uint8Array([120]))}]);
    await upload({action:"chunk",ticket:conflicting.ticket,file:0,offset:0,base64:"eA=="});
    await rejected({action:"chunk",ticket:conflicting.ticket,file:0,offset:0,base64:"eQ=="},"A conflicting replay replaced staged file bytes");
    await rejected({action:"status",ticket:conflicting.ticket},"A conflicting replay kept its native reservation usable");
    assert((await upload({action:"cancel",ticket})).cleanup_pending===false,"Verified staging cleanup remained pending");
    const expiring=await begin("expiry",[{name:"empty.txt",size:0,sha256:empty}],{timeout_ms:1000});
    const started=performance.now();let expired=false;
    for(let i=0;i<30;i++){await pause(100);try{await upload({action:"status",ticket:expiring.ticket});}catch{expired=true;break;}}
    assert(expired&&performance.now()-started>=800,"The real native staging lease did not expire at its bounded deadline");
    const manual=await begin("manual-cancel",[{name:"empty.txt",size:0,sha256:empty}]);
    await bridge.call("takeover",{browser});
    await rejected({action:"status",ticket:manual.ticket},"Manual takeover retained pending automatic staging");
    await bridge.call("release",{browser});
    const old=await begin("navigation-cancel",[{name:"empty.txt",size:0,sha256:empty}]);
    await bridge.call("navigate",{browser,url:url+"?upload=next"});
    for(let i=0;i<100;i++){const next=await bridge.call("state",{browser});if(!next.loading&&next.navigation!==state.navigation){state=next;break;}await pause(20);}
    await rejected({action:"status",ticket:old.ticket},"Navigation reused a previous document's staging ticket");
    const closing=await begin("close-cancel",[{name:"empty.txt",size:0,sha256:empty}]);
    await bridge.call("close",{browser});state=await create(browser);
    await rejected({action:"status",ticket:closing.ticket},"A replacement browser inherited a closed view's upload ticket");
    const replacement=await begin("replacement",[{name:"empty.txt",size:0,sha256:empty}]);
    assert((await upload({action:"seal",ticket:replacement.ticket})).sealed,"Closing an upload prevented fresh native staging");
    // Leave a sealed reservation for the actual close hook to reclaim below.
  }finally{try{await bridge.call("close",{browser});}catch{}try{await bridge.call("close",{browser:foreign});}catch{}}
}
