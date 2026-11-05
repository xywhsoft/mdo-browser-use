// Actual production account/TLS relay, not the synthetic native bridge fixture.
import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import https from 'node:https';
import http from 'node:http';
import {createRemoteTransport} from '../.mdo/source/app/web/js/api/remote-transport.js';

let raw=''; for await (const chunk of process.stdin) raw+=chunk;
const input=JSON.parse(raw); raw='';
const tag=value=>createHash('sha256').update(String(value)).digest('hex').slice(0,16);
const report={phase:'starting',passed:false,direction:input.direction,mode:input.mode,
  account_tag:input.accountTag,cases:[],real_model_calls:0,relay_used:true,
  android_controller_ui_qualified:false,full_browser_use_qualified:false};
const save=()=>writeFile(input.output,JSON.stringify(report,null,2)+'\n');
let transport,wireProbe=false;
const sockets=[];
const fetch=async(path,options={})=>{
  const response=await transport.fetch(path,{...options,signal:AbortSignal.timeout(12000)});
  return {response,body:await response.json()};
};
try{
  await save();
  transport=createRemoteTransport({socket:(url,protocols)=>{
    const socket=new WebSocket(url,protocols);sockets.push(socket);
    const send=socket.send.bind(socket);
    socket.send=data=>{
      if(wireProbe && typeof data==='string'){
        const value=JSON.parse(data);
        if(value.type==='request' && value.path==='/api/v1/application/call'){
          // One empty, invalid, non-mutating request exercises the real target
          // view guard independently of the controller's normal local guard.
          value.method='POST';value.sequence=1;wireProbe=false;data=JSON.stringify(value);
        }
      }
      return send(data);
    };
    return socket;
  }});
  const hello=await transport.connect(input);
  assert.equal(hello.mode,input.mode);
  report.runtime_tag=tag(hello.runtime_id);
  report.cases.push('real-account-ticket-and-target-handshake');
  const bootstrap=await fetch('/api/v1/bootstrap');assert.equal(bootstrap.response.status,200);
  report.target_version=bootstrap.body.data.version;
  const remote=await fetch('/api/v1/remote');assert.equal(remote.response.status,200);
  assert.equal(remote.body.data.device_id,input.deviceId);
  assert.equal(String(remote.body.data.member_id),input.owner);
  assert.notEqual(input.deviceId,input.localDeviceId);
  report.target_device_tag=tag(input.deviceId);
  report.cases.push('same-account-distinct-actual-device-identity');
  if(input.mode==='view'){
    await assert.rejects(()=>fetch('/api/v1/application/call',{method:'POST',body:'{}'}),e=>e.code==='read_only');
    report.cases.push('controller-view-write-denied');
    wireProbe=true;let code;
    try{const result=await fetch('/api/v1/application/call');code=result.body.error?.code;}
    catch(error){code=error.code;}
    assert.equal(code,'read_only');report.cases.push('actual-target-view-write-denied');
  }else{
    const token=bootstrap.response.headers.get('X-Mdo-Write-Token');assert.ok(token);
    const call=async(method,params={})=>{
      const result=await fetch('/api/v1/application/call',{method:'POST',
        headers:{'Content-Type':'application/json','X-Mdo-Write-Token':token},
        body:JSON.stringify({method,params,context:{extension:'browser-use-physical-auth-qa',project:'default'}})});
      if(result.response.status!==200 || !result.body.ok)
        throw Object.assign(new Error('Target SDK request failed'),{code:result.body.error?.code});
      return result.body.data;
    };
    const runtime=await call('runtime.info');report.sdk_version=runtime.sdk_version;
    assert.equal(runtime.version,report.target_version);
    report.cases.push('control-sdk-through-real-relay');
    const clients=await call('ui.clients');report.target_display_client_count=clients.length;
    const marker={probe:'browser-use-phone-desktop-auth',nonce:crypto.randomUUID()};
    const id='physical-auth-'+crypto.randomUUID();let created;
    try{
      created=await call('resource.put',{id,revision:0,title:'Browser Use 双设备认证测试',kind:'document',value:marker});
      const read=await call('resource.get',{id});assert.deepEqual(read.value,marker);
      report.cases.push('owned-resource-write-readback');
      await assert.rejects(()=>call('resource.put',{id,revision:0,title:'must-not-apply',kind:'document',value:marker}),e=>e.code==='revision_conflict');
      assert.deepEqual((await call('resource.get',{id})).value,marker);
      report.cases.push('stale-revision-rejected-original-preserved');
    }finally{
      if(created){
        const current=await call('resource.get',{id});assert.deepEqual(current.value,marker);
        assert.equal(current.revision,created.revision);
        await call('resource.remove',{id,revision:current.revision});
        await assert.rejects(()=>call('resource.get',{id}),e=>e.code==='resource_missing');
        report.owned_resource_removed=true;
      }
    }
    const replay=await new Promise((resolve,reject)=>{
      const url=new URL(input.origin);url.pathname=input.ticket.path;
      const key=Buffer.from(crypto.getRandomValues(new Uint8Array(16))).toString('base64');
      const req=(url.protocol==='https:'?https:http).request(url,{headers:{Upgrade:'websocket',Connection:'Upgrade',
        'Sec-WebSocket-Version':'13','Sec-WebSocket-Key':key,
        'Sec-WebSocket-Protocol':input.ticket.protocol+', xadmin.ticket.'+input.ticket.ticket}},res=>{
        res.resume();resolve({http_status:res.statusCode,upgraded:false});
      });
      req.on('upgrade',(_res,socket)=>{socket.destroy();resolve({http_status:101,upgraded:true});});
      req.on('error',reject);req.setTimeout(5000,()=>req.destroy(Object.assign(new Error('Replay timeout'),{code:'replay_timeout'})));req.end();
    });
    report.consumed_ticket_replay=replay;assert.equal(replay.upgraded,false);
    assert.ok([401,403,409].includes(replay.http_status));
    report.cases.push('consumed-relay-ticket-rejected');
  }
  transport.close();
  await assert.rejects(()=>fetch('/api/v1/bootstrap'),e=>e.code==='remote_offline');
  report.cases.push('closed-transport-does-not-fall-back-to-local');
  report.passed=true;report.phase='verified';await save();
  console.log(JSON.stringify({passed:true,direction:input.direction,mode:input.mode,
    cases:report.cases.length,target_version:report.target_version,runtime_tag:report.runtime_tag}));
}catch(error){
  report.phase='failed';report.error_code=/^[a-zA-Z0-9_:-]{1,100}$/.test(error.code||'')?error.code:error.name;
  await save();console.log(JSON.stringify({passed:false,direction:input.direction,mode:input.mode,error_code:report.error_code}));
  process.exitCode=1;
}finally{
  transport?.close();input.ticket=null;input.owner=null;
  await new Promise(resolve=>setTimeout(resolve,300));
  for(const socket of sockets)if(socket.readyState<2)socket.close();
}
