async function runCapturePrivacyProbe({assert}){
  const {executeBrowserAutomation}=await import('/js/features/workbench/automation-dispatch.js');
  const doc='d-00000000-0000-4000-8000-000000000001',width=128,height=96,requests=[],cases=[];
  const boxes=[{x:13,y:13,width:11,height:9},{x:31,y:23,width:17,height:11},{x:61,y:43,width:19,height:13}];
  const area={ok:true,document:doc,region:{x:0,y:0,width,height},viewport:{width,height},scroll:{x:0,y:0},
    device_pixel_ratio:1,redactions:boxes,redaction:'form-controls-and-opaque-content',
    frame_stamp:JSON.stringify([['f0','fd-original',null,false,true,'readable',doc,{x:0,y:0},{width,height},null]])};
  async function capture(format,variant){
    const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;
    const drawing=canvas.getContext('2d',{alpha:false});drawing.fillStyle='#fff';drawing.fillRect(0,0,width,height);
    drawing.fillStyle=variant?'#fff':'#000';for(const box of boxes)drawing.fillRect(box.x,box.y,box.width,box.height);
    let original;
    const view={async execute(command,params){
      if(command==='state')return{loading:false,automation:true,capture_capability:true,navigation:1};
      assert(command==='capture','Unexpected native work in the codec/dispatcher fixture');
      requests.push({public_format:format,source_format:params.format,maximum_bytes:params.max_bytes});
      original=canvas.toDataURL('image/'+params.format,.8).split(',')[1];
      return{ok:true,data:original,format:params.format,scale:1};
    }};
    const out=await executeBrowserAutomation({command:'capture',params:{document:doc,format,max_bytes:8192,max_width:128,max_height:96},
      view,browser:view,action:{id:'privacy-'+format+'-'+variant},api:{async call(){return{state:'pending'};}},
      pageDriver:{async read(method){assert(method==='captureArea','Unexpected document mutation in screenshot fixture');return area;}}});
    assert(out.result.ok,'The actual shared screenshot did not complete: '+JSON.stringify(out.result));
    assert(out.result.image.media_type==='image/'+format&&out.result.image.bytes<=8192,'Public screenshot codec or byte limit changed');
    const image=out.result.image,bytes=Uint8Array.from(atob(image.base64),c=>c.charCodeAt(0)),bitmap=await createImageBitmap(new Blob([bytes],{type:image.media_type}));
    try{const pixels=document.createElement('canvas');pixels.width=bitmap.width;pixels.height=bitmap.height;
      const context=pixels.getContext('2d');context.drawImage(bitmap,0,0);return{original,pixels:context.getImageData(0,0,pixels.width,pixels.height).data,width:pixels.width,height:pixels.height};
    }finally{bitmap.close();}
  }
  for(const format of ['png','jpeg']){
    const before=await capture(format,0),after=await capture(format,1);
    assert(before.original!==after.original,'The synthetic private source pixels did not change');
    assert(before.width===after.width&&before.height===after.height,'Screenshot dimensions changed between variants');
    let different=0;for(let at=0;at<before.pixels.length;at+=4)
      if(before.pixels.slice(at,at+4).some((value,index)=>value!==after.pixels[at+index]))different++;
    cases.push({public_format:format,private_source_changed:true,different_public_pixels:different});
  }
  const passed=cases.every(row=>row.different_public_pixels===0)&&requests.every(row=>row.source_format==='png'&&row.maximum_bytes===262144);
  return{passed,backend:'actual-webview2-canvas-shared-capture',checks:cases.length,model_calls:0,external_sites:0,
    cases,source_requests:requests,...(passed?{}:{error:'Private source pixels influenced the masked output, or the shared path did not require lossless source capture'})};
}
