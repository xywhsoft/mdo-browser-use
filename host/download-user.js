import {executeDownloadSave,cancelDownloadSave,downloadSaveState} from './download-save.js';

// Trusted chrome actions only. The page and model cannot supply actor flags or
// replace this frozen browser context with the currently selected conversation.
export function createDownloadUserActions({browser,sdk,execute,signal,alive=()=>true}){
  const context=Object.freeze({...browser.context,extension:browser.owner}),api=sdk.bind(context);
  let stopped=false,active=null;
  const valid=()=>!stopped&&!signal?.aborted&&alive();
  const error=(code,message)=>Object.assign(new Error(message),{code});
  const check=()=>{if(!valid())throw error('context_changed','浏览器所属窗口或设备已改变');};
  const operationId=()=>`sdk-${crypto.randomUUID().replaceAll('-','')}`;
  const cancel=async()=>{
    const current=active;if(!current)return false;current.abort.abort();
    if(current.kind==='save')return cancelDownloadSave(browser.id);
    const timeout=new AbortController(),timer=setTimeout(()=>timeout.abort(),1500);
    try{await api.call('operation.cancel',{id:current.id},{signal:timeout.signal});}catch{}finally{clearTimeout(timer);}return true;
  };
  const inspect=async()=>{check();if(!context.workspace)throw error('workspace_context_missing','此标签没有原任务目录，请从所属任务重新创建浏览器');
    return api.call('files.receive.list',{}, {signal});};
  async function save(download,path){
    check();if(active||downloadSaveState(browser.id))throw error('browser_busy','当前浏览器仍在保存文件');
    if(!context.workspace)throw error('workspace_context_missing','此标签没有原任务目录，请从所属任务重新创建浏览器');
    const current={kind:'save',abort:new AbortController()};active=current;
    const deadline=performance.now()+60000;let dispatched=false;
    const fail=(code,message)=>({handled:true,result:{ok:false,error:{code,message,effect_may_have_occurred:dispatched}}});
    const guard=async()=>{
      if(!valid()||current.abort.signal.aborted)return fail('cancelled','保存已停止，请检查已保存文件与恢复记录');
      if(performance.now()>=deadline)return fail('timed_out','保存超时，请检查恢复记录');
      return{state:await execute('state')}; // Manual control intentionally allowed.
    };
    try{
      const root=await api.call('files.stat',{path:'.'},{signal});check();
      if(current.abort.signal.aborted)throw error('cancelled','保存已取消');
      const result=await executeDownloadSave({browser:browser.id,params:{download,path,root_snapshot:root.revision},view:{execute},api,
        action:{id:`uia-${crypto.randomUUID().replaceAll('-','')}`,context},guard,deadline,nativeAgent:false,
        setDispatched:value=>{dispatched=value;},fail});
      return result.result;
    }finally{if(active===current)active=null;}
  }
  async function recover(method,params){
    check();if(active||downloadSaveState(browser.id))throw error('browser_busy','请先等待或取消当前保存');
    const current={kind:'recovery',id:operationId(),abort:new AbortController()};active=current;
    const abort=()=>current.abort.abort();signal?.addEventListener('abort',abort,{once:true});
    const timer=setTimeout(abort,60000),options={signal:current.abort.signal};let terminal=false;
    try{
      await api.call('operation.start',{id:current.id,method,params},options);
      for(;;){
        check();if(current.abort.signal.aborted)throw error('cancelled','核对或发布已停止，请刷新恢复记录');
        const state=await api.call('operation.get',{id:current.id},options);
        if(state.terminal){terminal=true;
          if(state.state!=='succeeded')throw error(state.error?.code||'file_operation_failed','任务目录操作未能完成，请核对恢复记录');
          return await api.call('operation.result',{id:current.id},options);
        }
        await new Promise(resolve=>setTimeout(resolve,50));
      }
    }catch(cause){throw Object.assign(error(cause?.code||'operation_uncertain','操作未确认，请刷新并核对记录后继续'),{operation:current.id});}
    finally{
      clearTimeout(timer);signal?.removeEventListener('abort',abort);
      const cleanup=new AbortController(),timeout=setTimeout(()=>cleanup.abort(),1500);
      try{await api.call(terminal?'operation.release':'operation.cancel',{id:current.id},{signal:cleanup.signal});}catch{}finally{clearTimeout(timeout);}
      if(active===current)active=null;
    }
  }
  const stop=()=>{stopped=true;void cancel();};signal?.addEventListener('abort',stop,{once:true});
  return Object.freeze({context,save,inspect,cancel,
    reconcile:id=>recover('files.receive.reconcile',{id}),
    publish:(id,path)=>recover('files.receive.publish',{id,path}),
    async release(id){check();if(active||downloadSaveState(browser.id))throw error('browser_busy','请先等待或取消当前保存');return api.call('files.receive.release',{id},{signal});},
    get busy(){return Boolean(active);},
    dispose(){stop();signal?.removeEventListener('abort',stop);}
  });
}
