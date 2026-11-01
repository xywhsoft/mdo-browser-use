import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import {ensureNativeBrowser,createNativeBrowserExecutor} from '../host/native-view.js';
import {browserScopeKey} from '../host/browser-ownership.js';
import {browserLocation,browserPhase,safeBrowserUrl} from '../.mdo/source/app/web/js/features/workbench/model.js';

// Run the actual applied UI component with controlled bridge/broker delivery.
// The DOM scaffold supplies layout only; it does not reproduce component logic.
const source=readFileSync(new URL('../.mdo/source/app/web/js/features/workbench/browser-view.js',import.meta.url),'utf8');
const factory=new Function('ensureNativeBrowser','createNativeBrowserExecutor','createBrowserDownloads','createBrowserChoosers','createBrowserDialogs',
  'browserLocation','browserPhase','safeBrowserUrl','browserScopeKey','make','icon','iconButton','popupMenu','toast','isRemoteTarget',
  'document','navigator','ResizeObserver','devicePixelRatio','setTimeout','clearTimeout',
  source.replace(/^import .+;\r?$/gm,'').replace('export async function','async function')+';return mountBrowserView;');
function fixture(){
  const trace=[],nodes=[],timers=new Map(),controller=new AbortController();let serial=0,current=true,cleanup=true,hook=()=>{},menu,options;
  const node=(tag='',className='',text='')=>{
    const value={tag,className,textContent:text,hidden:false,style:{},dataset:{},children:[],value:'',
      classList:{add(){},toggle(){}},setAttribute(name,value){this[name]=value;},append(...values){this.children.push(...values);},
      replaceChildren(...values){this.children=values;},getBoundingClientRect:()=>({x:0,y:0,width:800,height:600}),getClientRects:()=>[{}],click(){return this.onclick?.();}};
    nodes.push(value);return value;
  };
  const row={id:'panel',resource:{id:'browser'},owner:'extension',client:'display',context:{device:'device',project:'project',session:'task'}};
  let browser={id:'browser',owner:row.owner,client:'display',context:{...row.context},revision:1,url:'https://example.test/',title:'Original'};
  let state={url:browser.url,title:browser.title,loading:false,visible:true,can_go_back:true,can_go_forward:true};
  const root=node('section'),mount={},sdk={async call(method,params){trace.push({method,params});await hook(method,params);return{...browser,context:{...browser.context}};}},
    bridge={available:()=>true,async call(method,params){trace.push({method,params});await hook(method,params);
      if(method==='state')return{...state};if(method==='snapshot')return{ok:true,value:{text:'Page contents'}};
      if(method==='create')return{created:true};if(method==='capabilities')return{initial_download_interception:true};return{};}};
  const helper=name=>value=>{const element=node('section');return{element,async refresh(){trace.push({method:name});await hook(name);},toggle(){return true;},dispose(){trace.push({method:name+'.dispose'});}};};
  const document={hidden:false,activeElement:null,querySelectorAll:()=>[]};
  const run=factory(ensureNativeBrowser,createNativeBrowserExecutor,helper('downloads'),helper('choosers'),helper('dialogs'),browserLocation,browserPhase,safeBrowserUrl,browserScopeKey,node,name=>node('span',name),
    (name,label,action,onError)=>{const button=node('button','',label);button.onclick=()=>Promise.resolve().then(action).catch(onError);return button;},
    (button,items)=>{menu=items;return{dispose(){}};},(...values)=>trace.push({method:'toast',values}),()=>false,document,
    {clipboard:{async writeText(value){trace.push({method:'clipboard',value});}}},class{observe(){}},1,
    callback=>{const id=++serial;timers.set(id,callback);return id;},id=>timers.delete(id));
  const start=()=>run(root,options={row,sdk,bridge,signal:controller.signal,mount,clientId:'display',shown:()=>true,alive:()=>current,
    canHide:()=>cleanup,onBind:view=>{trace.push({method:'bind',id:view.id});return current;},onRetired:()=>trace.push({method:'remount'}),
    updateTitle:async title=>{trace.push({method:'title',title});await hook('title');},openExternal:async url=>trace.push({method:'external',url}),
    onEnd:async id=>trace.push({method:'end',id}),addContext:value=>trace.push({method:'context',value})});
  return{trace,nodes,root,mount,controller,timers,start,row,options:()=>options,retire:()=>{current=false;},cleanup:value=>{cleanup=value;},
    hook:value=>{hook=value;},browser:value=>{browser={...browser,...value};},state:value=>{state={...state,...value};},
    button:label=>nodes.find(n=>n.tag==='button'&&n.textContent===label),menu:label=>menu().find(item=>item.label===label),
    async flush(){for(let i=0;i<30;i++)await Promise.resolve();},async tick(){const entry=[...timers][0];if(entry){timers.delete(entry[0]);entry[1]();}await this.flush();}};
}

test('initial broker result cannot initialize a retired browser mount',async()=>{
  const f=fixture();f.hook(method=>{if(method==='browser.get')f.retire();});await f.start();await f.flush();
  assert(!f.trace.some(x=>['browser.attach','create','bounds','state','browser.report'].includes(x.method)));
  assert.equal(f.root.children.length,0);assert.equal(f.timers.size,0);
});
for(const changed of [{id:'foreign'},{context:{project:'project',session:'foreign'}},{owner:'other-extension'}])
test(`initial broker rejects foreign identity ${JSON.stringify(changed)}`,async()=>{
  const f=fixture();f.browser(changed);await assert.rejects(f.start(),/归属|identity/i);
  assert(!f.trace.some(x=>['browser.attach','create','bounds','state'].includes(x.method)));
});
test('attach result cannot initialize a retired mount',async()=>{
  const f=fixture();f.browser({client:'previous-display'});f.hook(method=>{if(method==='browser.attach')f.retire();});await f.start();await f.flush();
  assert.equal(f.trace.filter(x=>x.method==='browser.attach').length,1);assert(!f.trace.some(x=>x.method==='create'));
});
test('native capabilities cannot create after UI ownership changes',async()=>{
  const f=fixture();f.browser({download_policy:'intercept'});f.hook(method=>{if(method==='capabilities')f.retire();});await f.start();await f.flush();
  assert(!f.trace.some(x=>x.method==='create'));assert.equal(f.timers.size,0);
});
for(const phase of ['state','dialogs','choosers','downloads','browser.update','title','browser.report'])
test(`poll delivery stops at retired ${phase}`,async()=>{
  const f=fixture();await f.start();await f.flush();f.trace.length=0;f.state({url:'https://changed.test/',title:'Changed'});
  f.hook(method=>{if(method===phase)f.retire();});await f.tick();
  const at=f.trace.findIndex(x=>x.method===phase);assert(at>=0,`phase not exercised: ${phase}`);
  assert.equal(f.trace.slice(at+1).filter(x=>!x.method.endsWith('.dispose')).length,0,JSON.stringify(f.trace));assert.equal(f.timers.size,0);
});
test('foreign conflict refresh cannot redirect the retained dispatcher',async()=>{
  const f=fixture(),view=await f.start();await f.flush();f.state({url:'https://changed.test/',title:'Changed'});f.trace.length=0;
  f.hook(method=>{if(method==='browser.update')throw Object.assign(Error('Conflict'),{status:409});if(method==='browser.get')f.browser({id:'replacement'});});
  await f.tick();assert(!f.trace.some(x=>['title','browser.report'].includes(x.method)));
  assert.equal(f.timers.size,0);assert(!f.trace.some(x=>x.method==='bounds'));
  await view.execute('state',{browser:'replacement'});assert.equal(f.trace.at(-1).params.browser,'browser');
  await f.mount.visible(true);assert(!f.trace.some(x=>x.method==='remount'));
  f.trace.length=0;f.mount.dispose();await f.flush();assert(!f.trace.some(x=>x.method==='bounds'));
});
test('retired snapshot result cannot be added to another task',async()=>{
  const f=fixture();await f.start();await f.flush();f.trace.length=0;f.hook(method=>{if(method==='snapshot')f.retire();});
  await f.menu('引用页面文字到对话').execute().catch(()=>{});assert(!f.trace.some(x=>x.method==='context'));
});
test('end browser stops after broker ownership changes',async()=>{
  const f=fixture();await f.start();await f.flush();f.trace.length=0;f.hook(method=>{if(method==='browser.close')f.retire();});
  await f.menu('结束浏览').execute().catch(()=>{});assert(!f.trace.some(x=>['close','end'].includes(x.method)));
});
test('recovery reads current broker ownership before closing or recreating',async()=>{
  const f=fixture();f.state({error:'Lost process',process_failed:true});await f.start();await f.flush();f.trace.length=0;f.browser({client:'another-display'});
  await f.button('刷新').click();await f.flush();assert(f.trace.some(x=>x.method==='browser.get'));
  assert(!f.trace.some(x=>['close','create','navigate'].includes(x.method)));
});
test('retired layout callbacks neither hide nor control a newer browser',async()=>{
  const f=fixture();await f.start();await f.flush();f.trace.length=0;f.retire();f.cleanup(false);
  await f.mount.visible(true);await f.button('后退').click();await f.button('接管浏览器').click();f.mount.dispose();await f.flush();
  assert(!f.trace.some(x=>['bounds','back','takeover','toast'].includes(x.method)));
});
test('ordinary disposal hides its own surface but preserves the task dispatcher',async()=>{
  const f=fixture(),view=await f.start();await f.flush();f.trace.length=0;f.controller.abort();f.mount.dispose();await f.flush();
  assert.equal(f.trace.filter(x=>x.method==='bounds'&&x.params.visible===false).length,1);
  await view.execute('state');assert.equal(f.trace.at(-1).params.browser,'browser');assert.equal(f.timers.size,0);
});

test('UI binding precedes native create and popup reconciliation stays outside it',async()=>{
  const f=fixture();await f.start();await f.flush();const methods=f.trace.map(x=>x.method);
  assert(methods.indexOf('browser.get')<methods.indexOf('bind'));assert(methods.indexOf('bind')<methods.indexOf('create'));
  assert(methods.indexOf('create')<methods.indexOf('bounds'));
});
test('an initial native navigation survives UI retirement without replay or task cancellation',async()=>{
  const f=fixture();f.browser({download_policy:'intercept'});f.hook(method=>{if(method==='create')f.retire();});
  // Exercise the shared native helper receipt path rather than a plain create result.
  let live=true;const calls=[],native={async call(method){calls.push(method);if(method==='capabilities')return{initial_download_interception:true};
    if(method==='create'){live=false;return{created:true,downloads_intercepted:true,initial_navigation_pending:true,navigation_ref:'nv-original'};}}};
  await assert.rejects(ensureNativeBrowser({bridge:native,sdk:{call(){throw Error('No settlement after retirement');}},browser:{id:'browser',download_policy:'intercept'},
    clientId:'display',alive:()=>live,cancelOnDetach:false}),{name:'AbortError'});
  assert.deepEqual(calls,['capabilities','create']);await f.start();await f.flush();
  assert.equal(f.trace.filter(x=>x.method==='create').length,1);assert(!f.trace.some(x=>['state','bounds','navigation_cancel'].includes(x.method)));assert.equal(f.timers.size,0);
});
test('explicit recovery checks ownership again after native close',async()=>{
  const f=fixture();f.state({error:'Lost process',process_failed:true});await f.start();await f.flush();f.trace.length=0;
  f.hook(method=>{if(method==='close')f.browser({client:'another-display'});});await f.button('刷新').click();await f.flush();
  assert.equal(f.trace.filter(x=>x.method==='browser.get').length,2);assert.equal(f.trace.filter(x=>x.method==='close').length,1);
  assert(!f.trace.some(x=>['create','navigate','bounds'].includes(x.method)));assert.equal(f.timers.size,0);
});
test('valid recovery, navigation, page quotation and end still execute in order',async()=>{
  const f=fixture();f.state({error:'Lost process',process_failed:true});await f.start();await f.flush();f.trace.length=0;
  await f.button('刷新').click();await f.flush();const methods=f.trace.map(x=>x.method);
  assert(methods.indexOf('browser.get')<methods.indexOf('close'));assert(methods.indexOf('close')<methods.indexOf('create'));assert(methods.indexOf('create')<methods.indexOf('navigate'));
  await f.menu('引用页面文字到对话').execute();assert.equal(f.trace.at(-1).method,'context');assert.equal(f.trace.at(-1).value.context.session,'task');
  f.trace.length=0;await f.menu('结束浏览').execute();assert.deepEqual(f.trace.map(x=>x.method),['browser.close','close','end']);
});
test('retired pane asks the workbench to remount only on explicit visible layout',async()=>{
  const f=fixture();await f.start();await f.flush();f.retire();f.trace.length=0;
  await f.mount.visible(false);assert.equal(f.trace.length,0);await f.mount.visible(true);assert.deepEqual(f.trace.map(x=>x.method),['remount']);
});
test('initialization never advertises actionable UI before native creation completes',async()=>{
  const f=fixture();let finish;f.hook(method=>{if(method==='create')return new Promise(resolve=>{finish=resolve;});});
  const pending=f.start();await f.flush();assert(finish);f.trace.length=0;
  await f.button('后退').click();await f.button('刷新').click();await f.menu('结束浏览').execute().catch(()=>{});await f.flush();
  assert.equal(f.trace.length,0);finish();await pending;await f.flush();assert(f.trace.some(x=>x.method==='state'));
});
