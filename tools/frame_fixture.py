"""Loopback-only two-site frame fixture; synthetic content, one HTTP worker."""
from http.server import BaseHTTPRequestHandler,HTTPServer
import threading
import re
import time
from pathlib import Path
from urllib.parse import urlsplit


class FrameFixture:
    def __init__(self):
        fixture=self;self.counts={}
        version=re.search(r'const version = (\d+);',(Path(__file__).resolve().parents[1]/'page/runtime.js').read_text(encoding='utf-8'))
        if not version:raise RuntimeError('Cannot pin the fixture spoof to the current runtime version')
        runtime_version=version.group(1)
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def setup(self):
                super().setup()
                # Chromium may preconnect without sending a request. Bound that
                # idle socket so this single HTTP worker can serve the rest.
                self.connection.settimeout(.5)
            def handle(self):
                try:super().handle()
                except (BrokenPipeError,ConnectionResetError):pass
            def do_POST(self):
                if urlsplit(self.path).path!='/drag-download':self.send_error(404);return
                fixture.counts['POST /drag-download']=fixture.counts.get('POST /drag-download',0)+1
                length=int(self.headers.get('Content-Length','0'))
                if length>4096:self.send_error(413);return
                self.rfile.read(length);time.sleep(.15)
                data=b'browser-use-original-drag-response\x00\xff'
                self.send_response(200);self.send_header('Content-Type','application/octet-stream');self.send_header('Content-Disposition','attachment; filename="drag-lifetime.bin"');self.send_header('Content-Length',str(len(data)));self.send_header('Connection','close');self.end_headers()
                try:self.wfile.write(data)
                except (BrokenPipeError,ConnectionResetError):pass
            def do_GET(self):
                path=urlsplit(self.path).path;fixture.counts[path]=fixture.counts.get(path,0)+1
                fake="<script>for(const marker of ['__mdo_browser_use_runtime_v1','__mdo_browser_use_local_frame_v1'])Object.defineProperty(window,marker,{value:{version:"+runtime_version+",documentNode:document,observe:()=>({forged:'forged-frame-observation'})},configurable:false})</script>"
                if path=='/root':
                    body=f'<h1>Root site A</h1><button>Root action</button><button>Shared action</button><iframe id="same" name="same" src="/same"></iframe><iframe id="cross" name="cross" src="{fixture.other}/cross"></iframe>'
                elif path=='/cross':
                    body=f'<h1>Cross site B</h1><label>Cross field<input id="cross-input" value="synthetic-private-value"></label><button>Cross action</button><button>Shared action</button><iframe id="nested" name="nested" src="{fixture.origin}/nested"></iframe>'
                elif path=='/same':body='<h1>Same site child</h1><button>Same action</button>'
                elif path=='/nested':body='<h1>Nested site A</h1><button>Nested action</button>'
                elif path=='/cross-next':body='<h1>Cross site next document</h1><button>Next action</button>'
                elif path=='/foreign':body='<h1>Foreign browser content</h1>'
                elif path=='/many':body='<h1>Bounded frame fixture</h1>'+''.join('<iframe srcdoc="<button>bounded</button>"></iframe>' for _ in range(70))
                elif path=='/many-cross':body='<h1>Bounded OOPIF fixture</h1>'+''.join(f'<iframe src="{fixture.other}/leaf?frame={index}"></iframe>' for index in range(70))
                elif path=='/leaf':body='<h1>Bounded cross-site child</h1>'
                elif path in ('/dom-key-root','/dom-key-child'):
                    root=path=='/dom-key-root';name='Root' if root else 'Child'
                    body='<style>body{margin:12px}label,#editor{display:block;margin:8px;width:240px}#editor{height:35px;border:1px solid}iframe{display:block;width:600px;height:280px;margin-top:12px}#stats output{display:block;font-size:12px}#shadow{display:block;margin:8px}</style>'
                    body+=f'<h1>{name} DOM keyboard</h1><button id="activate">{name} activate</button><output id="activations">{name} activations 0</output><label>{name} field<input id="field"></label><label>{name} sink<input id="sink"></label><div id="editor" role="textbox" aria-label="{name} editor" contenteditable="true"></div>'
                    if root:body+=f'<div id="shadow"></div><iframe id="child" src="{fixture.other}/dom-key-child"></iframe><form id="download" action="/drag-download" method="post"><input type="hidden" name="fixture" value="focus-original"></form>'
                    body+='<div id="stats"></div><script>const name='+repr(name)+',query=new URLSearchParams(location.search),stats=document.querySelector("#stats");let activations=0,once=true;'
                    body+='document.querySelector("#activate").onclick=e=>{document.querySelector("#activations").textContent=name+" activations "+(++activations)+" "+(e.isTrusted?"trusted":"synthetic")};'
                    body+='function instrument(control,label){const outputs={};for(const key of ["focuses","clicks","inputs","keydowns"]){outputs[key]=document.createElement("output");stats.append(outputs[key])}let focuses=0,clicks=0,inputs=0,keydowns=0;const text=(key,value)=>outputs[key].textContent=label+" "+key+" "+value;text("focuses",0);text("clicks",0);text("inputs",0);text("keydowns",0);'
                    body+='control.addEventListener("click",()=>text("clicks",++clicks));control.addEventListener("input",e=>text("inputs",(++inputs)+" length "+(control.isContentEditable?control.textContent:control.value).length+" "+(e.isTrusted?"trusted":"synthetic")));'
                    body+='control.addEventListener("keydown",e=>{text("keydowns",++keydowns);if(control.id==="field"&&query.has("selection")&&e.ctrlKey&&e.key.toLowerCase()==="a")document.querySelector("#sink").focus()});'
                    body+='control.addEventListener("focus",()=>{text("focuses",++focuses);if(control.id!=="field")return;if(query.has("hijack"))document.querySelector("#sink").focus();if(query.has("replace")){const replacement=control.cloneNode();replacement.id="replacement";control.replaceWith(replacement);instrument(replacement,name+" replacement")}if(query.has("dialog"))alert("DOM keyboard focus dialog");if(once&&query.has("sibling")){once=false;document.querySelector("#child").src="/foreign"}if(once&&query.has("download")){once=false;document.querySelector("#download").submit()}});}'
                    body+='for(const id of ["field","sink","editor"])instrument(document.querySelector("#"+id),name+" "+id);'
                    if root:body+='const shadow=document.querySelector("#shadow").attachShadow({mode:"open"});shadow.innerHTML="<input>";const input=shadow.querySelector("input");input.setAttribute("aria-label","Root shadow");instrument(input,"Root shadow");'
                    body+='</script>'
                elif path in ('/visual-key-root','/visual-key-child'):
                    root=path=='/visual-key-root';name='Root' if root else 'Child'
                    body='<style>body{margin:0}canvas{position:absolute;left:20px;top:20px;width:160px;height:80px;outline:none}iframe{position:absolute;left:240px;top:10px;width:300px;height:180px;border:2px solid}#status{position:absolute;top:260px}#sink{position:absolute;left:500px;top:220px;width:100px}#shadow{position:absolute;left:20px;top:150px;width:160px;height:80px}</style>'
                    body+='<canvas id="keys" tabindex="0" width="160" height="80"></canvas><input id="sink"><div id="status"><output id="counts">'+name+' keys 0</output><output id="ups">'+name+' keyups 0</output><output id="clicks">'+name+' clicks 0</output><output id="focuses">'+name+' focuses 0</output></div>'
                    if root:body+=f'<iframe src="{fixture.other}/visual-key-child"></iframe><div id="shadow"></div><output id="shadow-status" style="position:absolute;top:320px">Shadow keys 0</output><button id="activate" style="position:absolute;left:400px;top:220px;width:80px;height:30px">Activate</button><output id="activations" style="position:absolute;top:350px">Activations 0</output>'
                    body+='<script>const query=new URLSearchParams(location.search);let keys=0,ups=0,clicks=0,focuses=0;const canvas=document.querySelector("#keys"),context=canvas.getContext("2d");context.fillStyle="#4b9";context.fillRect(0,0,160,80);context.fillStyle="#111";context.fillText("Keyboard canvas",20,30);'
                    body+=f'canvas.onkeydown=e=>{{e.preventDefault();document.querySelector("#counts").textContent="{name} keys "+(++keys)+" "+e.key+" "+(e.isTrusted?"trusted":"synthetic")}};canvas.onkeyup=e=>{{document.querySelector("#ups").textContent="{name} keyups "+(++ups)+" "+(e.isTrusted?"trusted":"synthetic")}};'
                    body+=f'canvas.onclick=e=>{{document.querySelector("#clicks").textContent="{name} clicks "+(++clicks);if(query.get("hijack"))document.querySelector("#sink").focus()}};'
                    body+=f'canvas.onfocus=()=>{{document.querySelector("#focuses").textContent="{name} focuses "+(++focuses);if(query.get("hijack"))document.querySelector("#sink").focus();if(query.get("dialog"))alert("Keyboard focus dialog")}};'
                    if root:
                        body+='const shadow=document.querySelector("#shadow").attachShadow({mode:"open"});shadow.innerHTML="<canvas tabindex=0 width=160 height=80 style=outline:none></canvas>";const sc=shadow.querySelector("canvas"),sctx=sc.getContext("2d");sctx.fillStyle="#ab5";sctx.fillRect(0,0,160,80);sctx.fillStyle="#111";sctx.fillText("Shadow canvas",20,30);let sn=0;sc.onkeydown=e=>{e.preventDefault();document.querySelector("#shadow-status").textContent="Shadow keys "+(++sn)+" "+e.key+" "+(e.isTrusted?"trusted":"synthetic")};'
                        body+='let an=0;document.querySelector("#activate").onclick=e=>{document.querySelector("#activations").textContent="Activations "+(++an)+" "+(e.isTrusted?"trusted":"synthetic")};'
                    body+='</script>'
                elif path=='/geometry':
                    body=f'<style>body{{margin:0}}iframe{{display:block;margin:150px 0 0 80px;width:520px;height:340px;border:3.5px solid;padding:5.25px}}#cover{{position:fixed;inset:0;z-index:9999;background:#555}}</style><button id="root-focus">Root focus action</button><iframe id="outer" src="{fixture.other}/geometry-cross"></iframe><div id="cover" hidden></div>'
                elif path=='/geometry-cross':
                    body=f'<style>body{{margin:0}}iframe{{display:block;margin:20px;width:360px;height:200px;border:2.5px solid;padding:4.25px}}</style><iframe id="inner" src="{fixture.origin}/geometry-leaf"></iframe>'
                elif path=='/scroll-root':
                    body=f'<style>body{{margin:0}}iframe{{display:block;margin:1800px 0 1200px 50px;width:520px;height:340px;border:3.5px solid;padding:5.25px}}</style><h1>Offscreen root</h1><iframe src="{fixture.other}/scroll-cross"></iframe>'
                elif path=='/scroll-cross':
                    body=f'<style>body{{margin:0}}iframe{{display:block;margin:1400px 20px 800px;width:360px;height:200px;border:2.5px solid;padding:4.25px}}</style><h1>Offscreen parent</h1><iframe src="{fixture.origin}/scroll-leaf"></iframe>'
                elif path=='/scroll-leaf':
                    body='<style>body{margin:0}button{display:block;margin:1000px 15px 500px}</style><button id="target">Offscreen nested action</button><output id="result"></output><script>let n=0;document.querySelector("#target").onclick=e=>{document.querySelector("#result").textContent=e.isTrusted?"Trusted nested scroll count "+(++n):"Synthetic input"}</script>'
                elif path in ('/visual-root','/visual-child'):
                    root=path=='/visual-root';name='Root' if root else 'Nested';width=400 if root else 280;height=200 if root else 140
                    body=f'<style>body{{margin:0}}canvas{{display:block}}iframe{{display:block;margin:24px 20px;border:3.5px solid;padding:4.25px;width:360px;height:220px}}</style><canvas id="{"root" if root else "child"}-canvas" width="{width}" height="{height}" aria-label="{name} visual canvas"></canvas>'
                    if root:body+='<button id="change-scene">Change canvas</button>'
                    body+=f'<output id="visual-status">{name} visual clicks 0</output>'
                    if root:body+=f'<iframe src="{fixture.other}/visual-child"></iframe>'
                    body+='<script>const canvas=document.querySelector("canvas"),context=canvas.getContext("2d"),status=document.querySelector("#visual-status");let count=0,alternate=false;'
                    body+='function draw(){context.fillStyle="#eee";context.fillRect(0,0,canvas.width,canvas.height);context.fillStyle=alternate?"#2255ee":"#22aa55";context.fillRect(20,20,140,70);context.fillStyle="#111";context.font="20px sans-serif";context.fillText("Canvas action",24,60)}draw();'
                    body+=f'canvas.onclick=e=>{{if(e.offsetX>=20&&e.offsetX<160&&e.offsetY>=20&&e.offsetY<90)status.textContent="{name} visual clicks "+(++count)+(e.isTrusted?" trusted":" synthetic");else status.textContent="Missed canvas region"}};'
                    for event,label in (('contextmenu','context'),('auxclick','auxiliary'),('mousemove','hover'),('wheel','wheel')):
                        body+=f'canvas.addEventListener("{event}",e=>{{e.preventDefault();status.textContent="{name} {label} "+(e.isTrusted?"trusted":"synthetic")}},{{passive:false}});'
                    if root:body+='document.querySelector("#change-scene").onclick=()=>{alternate=!alternate;draw()};'
                    body+='</script>'
                elif path in ('/visual-drag-root','/visual-drag-child'):
                    root=path=='/visual-drag-root';name='Root' if root else 'Child'
                    body='<style>body{margin:0}canvas,#drop{position:absolute;left:20px;width:140px;height:60px;background:#ccc;user-select:none}#pointer{top:20px}#html{top:100px}#drop{top:200px}output{display:block}#results{position:absolute;top:340px}iframe{position:absolute;left:220px;top:0;width:300px;height:310px;border:2px solid;padding:0}</style><canvas id="pointer" width="140" height="60"></canvas><canvas id="html" width="140" height="60" draggable="true"></canvas><div id="drop">Drop region</div><div id="results"><output id="pointer-result"></output><output id="drop-result"></output><output id="source-result"></output></div>'
                    if root:body+=f'<iframe id="child" src="{fixture.other}/visual-drag-child"></iframe>'
                    interrupt=urlsplit(self.path).query if root else ''
                    body+='<output id="scene-result" style="position:absolute;top:410px"></output><script>const pointer=document.querySelector("#pointer"),html=document.querySelector("#html"),drop=document.querySelector("#drop");let held=false,releases=0,drops=0,starts=0,ends=0,requested=false;'
                    if interrupt:body+='document.querySelector("#child").onload=()=>{if(requested)document.querySelector("#scene-result").textContent="Child load while held "+held};'
                    body+=f'const name="{name}";for(const [canvas,label] of [[pointer,"Pointer canvas"],[html,"HTML canvas"]]){{const c=canvas.getContext("2d");c.fillStyle="#228855";c.fillRect(0,0,140,60);c.fillStyle="#fff";c.font="15px sans-serif";c.fillText(label,5,30)}};'
                    body+='pointer.onpointerdown=e=>{e.preventDefault();held=true;pointer.setPointerCapture(e.pointerId);document.body.dataset.held="true";'
                    if interrupt=='interrupt=child':body+=f'requested=true;document.querySelector("#child").src="{fixture.other}/foreign";'
                    if interrupt=='interrupt=srcdoc':body+='requested=true;document.querySelector("#child").srcdoc="<h1>Replacement child document</h1>";'
                    body+='};for(const type of ["pointerup","pointercancel"])pointer.addEventListener(type,e=>{if(!held)return;held=false;document.body.dataset.held="false";document.querySelector("#pointer-result").textContent=name+" pointer releases "+(++releases)+(e.isTrusted?" trusted":" synthetic")});'
                    body+='html.ondragstart=e=>{starts++;e.dataTransfer.effectAllowed="copyMove";e.dataTransfer.setData("text/plain","visual-drag-fixture");document.querySelector("#source-result").textContent=name+" HTML starts "+starts+" ends "+ends};html.ondragend=()=>{ends++;document.querySelector("#source-result").textContent=name+" HTML starts "+starts+" ends "+ends};'
                    body+='drop.ondragover=e=>{e.preventDefault();e.dataTransfer.dropEffect="move"};drop.ondrop=e=>{e.preventDefault();document.querySelector("#drop-result").textContent=name+" drops "+(++drops)+(e.isTrusted?" trusted ":" synthetic ")+e.dataTransfer.getData("text/plain")};</script>'
                elif path in ('/drag-download-root','/drag-navigation-root'):
                    body='<style>body{margin:0}#source,#destination{position:absolute;top:30px;width:120px;height:60px;background:#ddd}#source{left:20px}#destination{left:230px}</style><div id="source" role="button" aria-label="Lifetime drag source">Source</div><div id="destination" role="button" aria-label="Lifetime drag destination">Destination</div><form id="download" method="post" action="/drag-download"><input name="fixture" value="original" type="hidden"></form><script>document.body.dataset.lifetime="original";document.body.dataset.downs="0";document.body.dataset.ups="0";let once=true,held=false;const source=document.querySelector("#source");source.onpointerdown=e=>{e.preventDefault();held=true;source.setPointerCapture(e.pointerId);document.body.dataset.downs=String(Number(document.body.dataset.downs)+1);if(once){once=false;document.querySelector("#download").submit()}};for(const type of ["pointerup","pointercancel"])source.addEventListener(type,e=>{if(!held)return;held=false;document.body.dataset.ups=String(Number(document.body.dataset.ups)+1)});</script>'
                    if path=='/drag-navigation-root':
                        body=body.replace('let once=true,held=false','let once=window.name!=="mdo-drag-lifetime-once",held=false').replace('document.querySelector("#download").submit()','window.name="mdo-drag-lifetime-once";location.href="/foreign"')
                elif path=='/geometry-leaf':
                    body='<style>body{margin:0}button{margin:20px}</style><button id="target">Nested geometry action</button><output id="result"></output><label>Nested field<input id="field"></label><label>Nested choice<select id="choice"><option>First</option><option>Second</option></select></label><input type="file" id="files" hidden aria-label="Hidden nested upload"><script>document.body.dataset.geometryClicks="0";document.querySelector("#target").onclick=e=>{document.body.dataset.geometryClicks=String(Number(document.body.dataset.geometryClicks)+1);document.querySelector("#result").textContent=e.isTrusted?"trusted":"synthetic"};document.body.dataset.uploadEvents="[]";for(const type of ["input","change"])document.querySelector("#files").addEventListener(type,async e=>{const events=JSON.parse(document.body.dataset.uploadEvents);events.push({type,trusted:e.isTrusted});document.body.dataset.uploadEvents=JSON.stringify(events);if(type!=="change")return;const file=e.target.files[0];document.body.dataset.uploadHash=file?Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",await file.arrayBuffer())),n=>n.toString(16).padStart(2,"0")).join(""):""})</script>'
                    body+='<button id="choose-file" onclick="document.querySelector(\'#files\').click()">Choose nested file</button><script>document.body.dataset.cancelEvents="[]";document.querySelector("#files").addEventListener("cancel",e=>{const events=JSON.parse(document.body.dataset.cancelEvents);events.push(e.isTrusted);document.body.dataset.cancelEvents=JSON.stringify(events)})</script>'
                    body+='<output id="upload-digest"></output><script>document.querySelector("#files").addEventListener("change",async e=>{const file=e.target.files[0];document.querySelector("#upload-digest").textContent=file?Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",await file.arrayBuffer())),n=>n.toString(16).padStart(2,"0")).join(""):""})</script>'
                else:self.send_error(404);return
                payload=('<!doctype html><meta charset="utf-8"><title>Frame fixture</title>'+body+fake).encode()
                self.send_response(200);self.send_header('Content-Type','text/html;charset=utf-8');self.send_header('Content-Length',str(len(payload)));self.send_header('Connection','close');self.end_headers();self.wfile.write(payload)
        class Server(HTTPServer):request_queue_size=64
        self.server=Server(('127.0.0.1',0),Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,kwargs={'poll_interval':.05},daemon=True)
    @property
    def origin(self):return f'http://127.0.0.1:{self.server.server_port}'
    @property
    def other(self):return f'http://localhost:{self.server.server_port}'
    def start(self):self.thread.start();return self
    def close(self):self.server.shutdown();self.server.server_close();self.thread.join(5)
