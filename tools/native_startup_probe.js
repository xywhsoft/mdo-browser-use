// Diagnostic measurements; no reload or extra native environment copy.
async function runStartupProbe({bridge,assert,pause}){
  const cases=[];
  for(const visible of [false,true,false,true]){
    const browser='browser-use-startup',started=performance.now();let snapshot,documentState;
    await bridge.call('create',{browser,url:location.origin+'/__browser_use_page__.html?startup='+cases.length,visible,
      bounds:{x:10,y:70,width:800,height:600,scale:devicePixelRatio}});
    try{
      while(performance.now()-started<15000){
        snapshot=await bridge.call('state',{browser});if(!snapshot.loading)break;
        if(!documentState&&performance.now()-started>2000){documentState=await bridge.call('evaluate',{browser,script:`({ready:document.readyState,frames:[...document.querySelectorAll('iframe')].map(frame=>{try{return{ready:frame.contentDocument?.readyState||'opaque'}}catch{return{ready:'opaque'}}}),timing:performance.getEntriesByType('navigation').map(entry=>({dom:entry.domContentLoadedEventEnd,load:entry.loadEventEnd,response:entry.responseEnd}))})`});}
        await pause(25);
      }
      const elapsed_ms=Math.round(performance.now()-started),row={visible,elapsed_ms,navigation:snapshot.navigation,navigation_events:snapshot.navigation_events,error:snapshot.error,documentState};
      if(snapshot.loading){
        await bridge.call('bounds',{browser,visible:true,bounds:{x:10,y:70,width:800,height:600,scale:devicePixelRatio}});
        const shown=performance.now();do{snapshot=await bridge.call('state',{browser});if(!snapshot.loading)break;await pause(25);}while(performance.now()-shown<5000);
        row.after_show={elapsed_ms:Math.round(performance.now()-shown),loading:snapshot.loading,navigation_events:snapshot.navigation_events};
      }
      cases.push(row);assert(!snapshot.loading,'Native startup stayed pending: '+JSON.stringify(cases));
    }finally{await bridge.call('close',{browser});}
  }
  return cases;
}
