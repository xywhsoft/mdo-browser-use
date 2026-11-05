import { createBrowserUseClient } from './sdk/client.mjs';
import { createLocalApplicationClient } from '/js/api/application.js';
import { targetState, requestTargetSwitch } from '/js/api/target.js';
import { connectorRequest } from '/js/api/connector.js';
import config from './config.js';

// This QA-only frontend is installed into the original Android application.
// All browser calls below execute in its real WebView with the product SDK and
// selected-target transport. No Node controller or substitute relay is used.
export function activate(sdk) {
  const panel=document.createElement('section');
  panel.style.cssText='position:fixed;z-index:1800;top:90px;left:12px;right:12px;padding:12px;background:#182b37;color:white;border:1px solid #80caff;border-radius:10px;font:16px sans-serif';
  const label=document.createElement('strong'),status=document.createElement('p'),button=document.createElement('button');
  label.textContent='双设备 Browser Use 验收';button.style.cssText='padding:12px;font:inherit';
  panel.append(label,status,button);document.body.append(panel);
  const local=createLocalApplicationClient(()=>config.localContext);
  let revision=0, running=false, retired=false;
  const marker=config.id+'.started';
  const report={passed:false,state:'ready',stage:'ready',cases:[],model_calls:0,
    controller_engine:'actual-android-webview',native_target:'actual-windows-webview2'};
  function check(value,code){if(!value)throw Object.assign(new Error(code),{code});}
  async function publish(){
    status.textContent=report.stage;
    const row=await local.call('resource.put',{id:config.id,revision,title:'Physical Browser Use QA',kind:'document',value:report});
    revision=row.revision;
  }
  async function mark(name){report.stage=name;report.cases.push(name);await publish();}
  const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  button.textContent=targetState().selected?.id===config.device ? '开始跨设备浏览器验收' : '连接验收电脑';
  status.textContent=sessionStorage.getItem(marker)?'已发起过；请读取原收据，不会自动重放':'仅使用专用测试页面，不调用模型';
  button.disabled=Boolean(sessionStorage.getItem(marker));
  button.onclick=async()=>{
    if(['passed','failed'].includes(report.state)){await requestTargetSwitch(null);return;}
    if(running||retired)return;
    if(targetState().selected?.id!==config.device){
      try{
        const account=await connectorRequest('/account');check(account.state==='signed_in','controller_not_signed_in');
        await requestTargetSwitch({id:config.device,name:'Browser Use 本机测试',mode:'control',owner:String(account.profile.id)});
      }catch(error){status.textContent=error.code||error.message;}
      return;
    }
    running=true;button.disabled=true;sessionStorage.setItem(marker,'started');
    const operations=new Set();let client,browser,ticket,download;
    const scoped=sdk.bind(config.context);
    const api={async call(method,params={},options){
      if(method==='browseruse.operation'&&params.action==='submit')operations.add(params.operation);
      const result=await scoped.call(method,params,options);
      if(method==='browseruse.operation'&&params.action==='release')operations.delete(params.operation);
      return result;
    }};
    try{
      check(/Android/.test(navigator.userAgent),'controller_is_not_android');
      const state=targetState();check(state.connected&&state.selected?.id===config.device&&state.selected.mode==='control','wrong_target');
      report.controller_origin=location.origin;report.route=state.route||'relay';
      await publish();
      const task=await api.call('session.create',{body:{project_id:'default',client_session_id:config.session,
        title:'Browser Use 双设备浏览器验收',permission_profile:'full-access',workspace_root:config.context.workspace}});
      check(task.data.permission_profile==='full-access','test_task_binding_failed');
      client=createBrowserUseClient(api);
      const clients=(await client.tabs({action:'clients'})).clients;
      check(clients.some(row=>row.id===config.client),'original_windows_display_missing');
      check(config.client!==sdk.clientId,'browser_would_execute_on_controller');
      report.controller_client=sdk.clientId;report.target_client=config.client;
      await mark('actual_android_selected_target_and_original_windows_display');
      const created=await client.tabs({action:'create',client:config.client,key:config.id,url:config.page});browser=created.id;
      report.browser=browser;await publish();
      let page=await client.observe({browser});check(page.ok&&page.text.includes('Readable fixture text'),'remote_observation_failed');
      await mark('android_to_remote_c_js_native_observation');
      const fill=await client.act({browser,document:page.document,op:'fill',target:{css:'#order'},text:'Phone E262 雨🦉'});
      check(fill.verification==='field_state','remote_fill_unverified');
      const click=await client.act({browser,document:page.document,op:'click',target:{css:'#search'}});
      check(click.observation.text.includes('Trusted click 1'),'trusted_click_not_exactly_once');
      page=await client.observe({browser});check(page.text.includes('Trusted click 1'),'remote_readback_failed');
      await mark('remote_unicode_fill_and_exactly_one_trusted_click');
      const image=await client.screenshot({browser,document:page.document,max_width:640,max_height:480});
      const bytes=Uint8Array.from(atob(image.image.base64),c=>c.charCodeAt(0));
      check(image.image.media_type==='image/png'&&bytes[0]===137&&bytes[1]===80,'remote_image_invalid');
      const hash=await crypto.subtle.digest('SHA-256',bytes);
      report.capture={bytes:bytes.length,sha256:[...new Uint8Array(hash)].map(x=>x.toString(16).padStart(2,'0')).join('')};
      const img=new Image();img.src='data:image/png;base64,'+image.image.base64;await img.decode();
      check(img.naturalWidth>0&&img.naturalHeight>0,'android_image_decode_failed');
      report.capture.width=img.naturalWidth;report.capture.height=img.naturalHeight;
      img.style.cssText='max-width:100%;max-height:120px';panel.append(img);
      await mark('remote_synthetic_capture_delivered_and_decoded_on_android');
      const controller=new AbortController();
      const pending=client.waitFor({browser,until:'visible',target:{css:'#never-physical-qa'},timeout_ms:10000},{signal:controller.signal})
        .then(()=>null,error=>error);
      await pause(600);controller.abort();const interrupted=await pending;
      check(interrupted?.code==='cancelled','remote_wait_not_cancelled');
      if(interrupted.receipt){await client.result({id:interrupted.receipt,release:true});}
      check((await client.observe({browser})).text.includes('Trusted click 1'),'cancel_replayed_click_or_lost_page');
      await mark('original_wait_cancelled_and_browser_continues_without_replay');
      const previous=page.document;
      page=(await client.navigate({browser,action:'goto',url:config.upload})).observation;
      check(page.document!==previous&&page.text.includes('Browser Use upload fixture'),'remote_navigation_failed');
      let stale;try{await client.act({browser,document:previous,op:'click',target:{css:'#search'}});}catch(error){stale=error;}
      check(stale?.code==='stale_document','stale_remote_action_admitted');
      await mark('remote_navigation_and_stale_document_denial');
      const assigned=await client.upload({browser,action:'set',document:page.document,target:{css:'#single'},
        files:[{path:config.uploadPath,space:'workspace',name:'physical-upload.bin'}],timeout_ms:15000});
      ticket=assigned.transfer.ticket;check(assigned.verified&&assigned.transfer.retained,'remote_upload_not_verified');
      const read=await client.waitFor({browser,until:'text',text:config.hash,timeout_ms:15000});
      check(read.observation.text.includes(config.hash),'page_upload_hash_mismatch');
      await mark('remote_workspace_upload_and_page_full_hash');
      page=(await client.navigate({browser,action:'goto',url:config.download})).observation;
      await client.upload({browser,action:'release',ticket,confirmed:true});ticket=null;
      const posted=await client.act({browser,document:page.document,op:'click',target:{css:'#post'}});
      check(['download_pending','download_in_progress'].includes(posted.outcome),'post_download_not_retained');
      const end=performance.now()+10000;
      while(performance.now()<end){download=(await client.download({browser,action:'list'})).downloads.find(row=>row.state==='pending');if(download)break;await pause(50);}
      check(download,'pending_download_missing');
      await client.download({browser,action:'accept',download:download.id,max_bytes:1048576});
      check((await client.download({browser,action:'wait',download:download.id,timeout_ms:10000})).completed,'download_incomplete');
      const saved=await client.save({browser,download:download.id,path:config.savePath});
      check(saved.saved&&saved.file.sha256===config.hash&&saved.file.size===config.bytes,'remote_saved_bytes_differ');
      await client.download({browser,action:'release',download:download.id});download=null;
      await mark('retained_post_download_and_exact_remote_workspace_save');
      await client.tabs({action:'close',browser});browser=null;
      await mark('original_remote_browser_closed');report.passed=true;
    }catch(error){report.error={code:error.code||error.name||'failed'};}
    finally{
      try{
        if(ticket&&browser)await client.upload({browser,action:'release',ticket,confirmed:true});
        if(browser)await client.tabs({action:'close',browser});
        for(const id of operations){
          let row=await scoped.call('operation.get',{id});if(!row.terminal)await scoped.call('operation.cancel',{id});
          const end=performance.now()+5000;
          while(!row.terminal&&performance.now()<end){await pause(50);row=await scoped.call('operation.get',{id});}
          check(row.terminal,'remote_operation_cleanup_unconfirmed');await scoped.call('operation.release',{id});
        }
      }catch(error){report.passed=false;report.cleanup_error=error.code||error.name||'cleanup_unconfirmed';}
      report.state=report.passed?'passed':'failed';report.stage=report.state;await publish();
      button.disabled=false;button.textContent='返回本机并结束验收';
    }
  };
  return()=>{retired=true;panel.remove();};
}
