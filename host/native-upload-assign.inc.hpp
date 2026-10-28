/* One typed file-input assignment. Only the isolated runtime's pinned element
 * can receive our sealed paths; no path/script/DOM object ID comes from an Agent. */
struct xb_upload_assignment:std::enable_shared_from_this<xb_upload_assignment>{
  std::shared_ptr<xb_host> host;std::shared_ptr<xb_view> view;std::shared_ptr<xb_upload_job> job;
  std::string id,document,ref,group,object,error,session,frameRaw,frameKey;xb_input_frame_guard frameGuard;int contextId=0;ULONGLONG deadline=0;
  bool done=false,stopped=false,replied=false,inFlight=false,dispatched=false;
  std::string clearMeta()const{return job->clear?",\"input_method\":\"dom_file_clear\",\"events_trusted\":false":"";}
  const char* marker()const{return frameGuard.empty()?"__mdo_browser_use_runtime_v1":"__mdo_browser_use_local_frame_v1";}
  void protocol(const wchar_t* method,const std::string& params,xb_cdp_result done){
    if(frameGuard.empty())xb_cdp(view,method,params,std::move(done));else xb_frame_cdp(view,session,method,params,std::move(done));
  }
  void releaseGroup()noexcept{
    if(group.empty()||view->closed||!host->alive)return;
    if(!session.empty()&&(!view->frames||!view->frames->sessions.count(session)))return; // Destroyed session owns no live object group.
    try{std::weak_ptr<xb_view> weak=view;protocol(L"Runtime.releaseObjectGroup","{\"objectGroup\":"+xb_quote(group)+"}",[weak](bool ok,const std::string& result){if(!ok||!xb_get(result,"error").empty())if(auto active=weak.lock())active->uploadCleanupFailed=true;});}catch(...){view->uploadCleanupFailed=true;}
  }
  ~xb_upload_assignment(){
    if(done)return;
    job->assigning=false;if(!job->retained)job->closing=true;
    if(view->uploadAssignmentKey==id){view->uploadBusy=false;view->uploadAssignment.reset();view->uploadAssignmentKey.clear();}
    releaseGroup();
  }
  bool live()const{return !done&&!stopped&&!job->closing&&xb_input_frames_live(view,frameGuard)&&(dispatched||xb_chooser_upload_ready(view,job->chooser,document,ref,frameKey))&&xb_automation_live(host,view,job->navigation)&&!view->loading&&!view->initialNavigationPending&&GetTickCount64()<deadline;}
  std::string failure(const std::string& code,const std::string& message)const{
    return "{\"accepted\":false,\"ticket\":"+xb_quote(job->ticket)+clearMeta()+",\"dispatched\":"+(dispatched?"true":"false")+",\"retained\":"+(job->retained?"true":"false")+
      ",\"error\":{\"code\":"+xb_quote(code)+",\"message\":"+xb_quote(message)+",\"effect_may_have_occurred\":"+(dispatched?"true":"false")+"}}";
  }
  void reply(const std::string& result){job->assignmentResult=result;if(replied)return;replied=true;host->reply(id,result);}
  void finish(const std::string& result){
    if(done)return;reply(result);done=true;job->assigning=false;
    if(view->uploadAssignmentKey==id){view->uploadBusy=false;view->uploadAssignment.reset();view->uploadAssignmentKey.clear();}
    releaseGroup();
  }
  void stop(const std::string& reason){
    if(done)return;stopped=true;error=reason;reply(failure(dispatched?"action_uncertain":"upload_stopped",reason));
    if(!inFlight||view->closed||!host->alive)finish(failure(dispatched?"action_uncertain":"upload_stopped",reason));
  }
  void boundary(bool popup){
    if(done)return;
    if(!dispatched){stop("A dialog or new window interrupted upload preparation; inspect the page");return;}
    reply("{\"accepted\":true,\"ticket\":"+xb_quote(job->ticket)+clearMeta()+",\"dispatched\":true,\"retained\":"+(job->retained?"true":"false")+",\"completed\":false,\"outcome\":"+xb_quote(popup?"popup_open":"dialog_open")+"}");
  }
  void tick(){
    if(done)return;
    if(dispatched&&!view->closed&&!view->manual&&!view->failed&&view->navigation==job->navigation&&GetTickCount64()<deadline&&(view->dialog||xb_popup_blocked(view))){boundary(xb_popup_blocked(view));return;}
    if(!live())stop("Upload control, document or deadline changed; inspect any already assigned files");
  }
  void call(const wchar_t* method,const std::string& params,std::function<void(const std::string&)> next){
    if(!live()){stop("Upload context changed before native delivery");return;}
    inFlight=true;auto self=shared_from_this();
    protocol(method,params,[self,next](bool ok,const std::string& result){
      self->inFlight=false;if(self->done)return;
      if(self->stopped){self->finish(self->failure(self->dispatched?"action_uncertain":"upload_stopped",self->error));return;}
      if(!self->live()){self->stop("Upload context changed during native delivery; inspect its effects");return;}
      if(!ok||!xb_get(result,"error").empty()||!xb_get(result,"exceptionDetails").empty()){
        self->finish(self->failure(self->dispatched?"action_uncertain":"upload_target_changed","Native file-input delivery was not confirmed; inspect before repeating"));return;
      }
      next(result);
    });
  }
  void verify(){
    auto self=shared_from_this();
    const std::string function="function(){return {valid:this.isConnected&&this.ownerDocument.defaultView?.document===this.ownerDocument&&this.localName==='input'&&this.type==='file',disabled:this.matches(':disabled')||this.getAttribute('aria-disabled')==='true'||!!this.closest('[inert]'),directory:!!this.webkitdirectory,files:Array.from({length:Math.min(this.files?.length||0,17)},(_,i)=>({name:this.files[i].name.slice(0,241),size:this.files[i].size}))}}";
    call(L"Runtime.callFunctionOn","{\"objectId\":"+xb_quote(object)+",\"functionDeclaration\":"+xb_quote(function)+",\"returnByValue\":true}",[self](const std::string& result){
      const auto value=xb_get(xb_get(result,"result"),"value"),files=xb_get(value,"files");bool verified=xb_get(value,"valid")=="true"&&xb_get(value,"disabled")!="true"&&(self->job->clear||xb_get(value,"directory")!="true");
      for(size_t i=0;verified&&i<self->job->files.size();i++){auto item=webview::detail::json_parse(files,"",static_cast<int>(i));uint64_t size=0;
        verified=!item.empty()&&xb_get(item,"name")==self->job->files[i]->name&&xb_upload_uint(xb_get(item,"size"),size)&&size==self->job->files[i]->size;
      }
      verified=verified&&webview::detail::json_parse(files,"",static_cast<int>(self->job->files.size())).empty();
      if(!verified){self->finish(self->failure("action_uncertain","The page changed the assigned file list; inspect before repeating"));return;}
      self->job->verified=true;
      self->finish("{\"accepted\":true,\"ticket\":"+xb_quote(self->job->ticket)+",\"dispatched\":true,\"retained\":"+(self->job->retained?"true":"false")+",\"cleared\":"+(self->job->clear?"true":"false")+",\"verified\":true,\"verification\":\"file_list\""+(self->job->clear?self->clearMeta():",\"input_method\":\"native_file_input\"")+"}");
    });
  }
  void dispatch(){
    if(!live()){stop("Upload context changed immediately before file assignment");return;}
    if(!xb_chooser_upload_consume(view,job->chooser,document,ref,frameKey)){stop("The exact file picker changed before assignment");return;}
    std::string paths="[";for(const auto& file:job->files){if(paths.size()>1)paths+=",";paths+=xb_quote(webview::detail::narrow_string(file->path));}paths+="]";
    auto self=shared_from_this();
    // Retain bytes before the native call: even a lost/error reply cannot prove
    // that the website did not obtain a File object for delayed submission.
    dispatched=true;job->dispatched=true;job->retained=!job->clear;job->assignDocument=document;job->assignRef=ref;job->assignFrameKey=frameKey;
    if(job->clear){
      const auto expression="globalThis."+std::string(marker())+".clearFiles({document:"+xb_quote(document)+",target:{ref:"+xb_quote(ref)+"}})";
      call(L"Runtime.evaluate","{\"expression\":"+xb_quote(expression)+",\"contextId\":"+std::to_string(contextId)+",\"returnByValue\":true}",[self](const std::string& output){
        const auto value=xb_get(xb_get(output,"result"),"value"),error=xb_get(value,"error");
        if(xb_get(value,"dispatched")=="false"||xb_get(error,"effect_may_have_occurred")=="false"){self->dispatched=false;self->job->dispatched=false;}
        if(xb_get(value,"ok")!="true"){self->finish(self->failure(self->dispatched?"action_uncertain":"upload_target_changed","The file selection change was not confirmed; inspect before repeating"));return;}
        if(!self->dispatched){self->job->verified=true;self->finish("{\"accepted\":true,\"ticket\":"+xb_quote(self->job->ticket)+self->clearMeta()+",\"dispatched\":false,\"retained\":false,\"cleared\":true,\"changed\":false,\"verified\":true,\"verification\":\"file_list\"}");return;}
        self->verify();
      });return;
    }
    call(L"DOM.setFileInputFiles","{\"objectId\":"+xb_quote(object)+",\"files\":"+paths+"}",[self](const std::string&){self->verify();});
  }
  void inspect(){
    auto self=shared_from_this();
    const std::string function="function(){return {valid:this.isConnected&&this.ownerDocument.defaultView?.document===this.ownerDocument&&this.localName==='input'&&this.type==='file',disabled:this.matches(':disabled')||this.getAttribute('aria-disabled')==='true'||!!this.closest('[inert]'),multiple:!!this.multiple,directory:!!this.webkitdirectory,count:this.files?.length||0}}";
    call(L"Runtime.callFunctionOn","{\"objectId\":"+xb_quote(object)+",\"functionDeclaration\":"+xb_quote(function)+",\"returnByValue\":true}",[self](const std::string& result){
      const auto value=xb_get(xb_get(result,"result"),"value");
      if(xb_get(value,"valid")!="true"||xb_get(value,"disabled")=="true"||!self->job->clear&&xb_get(value,"directory")=="true"||self->job->files.size()>1&&xb_get(value,"multiple")!="true"){
        self->finish(self->failure("upload_target_invalid","Choose an enabled regular file input with enough multiple-file capacity"));return;
      }
      if(self->job->clear&&xb_get(value,"count")=="0"){
        self->job->verified=true;self->finish("{\"accepted\":true,\"ticket\":"+xb_quote(self->job->ticket)+self->clearMeta()+",\"dispatched\":false,\"retained\":false,\"cleared\":true,\"changed\":false,\"verified\":true,\"verification\":\"file_list\"}");return;
      }self->dispatch();
    });
  }
  void resolveInput(){
    auto self=shared_from_this();
    const auto expression="(()=>{const r=globalThis."+std::string(marker())+";return r&&r.version>="+std::string(job->clear?"6":"5")+"&&r.document==="+xb_quote(document)+"?r.uploadNode({document:"+xb_quote(document)+",target:{ref:"+xb_quote(ref)+"}}):null})()";
    call(L"Runtime.evaluate","{\"expression\":"+xb_quote(expression)+",\"contextId\":"+std::to_string(contextId)+",\"objectGroup\":"+xb_quote(group)+",\"returnByValue\":false,\"timeout\":3000}",[self](const std::string& result){
      const auto node=xb_get(result,"result");self->object=xb_get(node,"objectId");
      if(self->object.empty()||xb_get(node,"subtype")!="node"){self->finish(self->failure("upload_target_changed","The pinned file input changed; observe it again"));return;}
      self->inspect();
    });
  }
  void start(){
    if(frameGuard.empty()){contextId=view->automationContext;resolveInput();return;}
    auto self=shared_from_this();
    call(L"Page.createIsolatedWorld","{\"frameId\":"+xb_quote(frameRaw)+",\"worldName\":\"mdo.browser-use.frames.v1\"}",[self](const std::string& result){
      try{self->contextId=std::stoi(xb_get(result,"executionContextId"));}catch(...){}
      if(self->contextId<=0){self->finish(self->failure("upload_target_changed","The exact frame world is no longer available"));return;}
      self->resolveInput();
    });
  }
};
static void xb_upload_assignment_tick(const std::shared_ptr<xb_upload_job>& job){if(auto assignment=job->assignment.lock())assignment->tick();}
static void xb_upload_boundary(const std::shared_ptr<xb_view>& view,bool popup){if(auto assignment=view->uploadAssignment.lock())assignment->boundary(popup);}
static void xb_upload_abort_view(const std::shared_ptr<xb_view>& view){if(auto assignment=view->uploadAssignment.lock())assignment->stop("The browser was closed during file assignment");}
static void xb_upload_cancel_assignment(const std::shared_ptr<xb_upload_job>& job){if(auto assignment=job->assignment.lock())assignment->stop("The originating upload was cancelled; inspect its effects");}
static void xb_upload_assign(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,const std::string& id,const std::string& json,const std::shared_ptr<xb_upload_job>& job){
  auto reject=[&](const char* code,const char* message){host->reply(id,"{\"accepted\":false,\"dispatched\":false,\"error\":{\"code\":"+xb_quote(code)+",\"message\":"+xb_quote(message)+",\"effect_may_have_occurred\":false}}");};
  if(!job){reject("upload_ticket_missing","The upload ticket expired or belongs to another browser");return;}
  if(job->tree){reject("unsupported_capability","Directory staging is available; verified directory assignment is not enabled yet");return;}
  const auto document=xb_get(json,"document"),ref=xb_get(json,"ref");uint64_t timeout=10000;
  xb_input_frame_guard frameGuard;
  if(!xb_input_read_frames(json,frameGuard)){reject("upload_arguments_invalid","The upload frame document chain is malformed");return;}
  const auto frameKey=xb_input_frame_key(frameGuard);
  if(document.empty()||document.size()>100||ref.empty()||ref.size()>100||(!xb_get(json,"timeout_ms").empty()&&!xb_upload_uint(xb_get(json,"timeout_ms"),timeout))||!timeout||timeout>60000){reject("upload_arguments_invalid","Choose a fresh document, pinned input reference and bounded deadline");return;}
  if(job->dispatched||job->clear&&!job->assignmentResult.empty()){
    if(job->assignDocument!=document||job->assignRef!=ref||job->assignFrameKey!=frameKey){reject("upload_identity_conflict","This upload already belongs to a different file input");return;}
    if(!job->assignmentResult.empty()){host->reply(id,job->assignmentResult);return;}
    if(job->assigning)host->reply(id,"{\"accepted\":true,\"pending\":true,\"dispatched\":true,\"retained\":"+std::string(job->retained?"true":"false")+"}");
    else host->reply(id,"{\"accepted\":false,\"dispatched\":true,\"retained\":"+std::string(job->retained?"true":"false")+",\"error\":{\"code\":\"action_uncertain\",\"message\":\"Inspect the page before another upload\",\"effect_may_have_occurred\":true}}");return;
  }
  if((!job->chooser.empty()||!job->frameGuard.empty())&&(job->assignDocument!=document||job->assignRef!=ref||job->assignFrameKey!=frameKey)){reject("upload_identity_conflict","This upload belongs to a different picker or frame target");return;}
  if(!job->sealed||job->closing||job->assigning||view->uploadBusy||view->uploadCleanupFailed||!xb_input_frames_live(view,frameGuard)||!xb_chooser_upload_ready(view,job->chooser,document,ref,frameKey)||!view->input.expired()||view->capture||view->drag||(frameGuard.empty()&&!view->automationContext)||view->loading||view->initialNavigationPending||
     xb_get(json,"navigation")!=std::to_string(job->navigation)||!xb_automation_live(host,view,job->navigation)){
    reject("upload_not_ready","The sealed upload, isolated document or native input slot is no longer ready");return;
  }
  auto assignment=std::make_shared<xb_upload_assignment>();assignment->host=host;assignment->view=view;assignment->job=job;
  assignment->id=id;assignment->document=document;assignment->ref=ref;assignment->group="mdo-upload-"+id;
  assignment->frameGuard=frameGuard;assignment->frameKey=frameKey;
  if(!frameGuard.empty()){
    auto node=xb_frame_alias(view->frames,frameGuard.front().first);
    if(!node){reject("upload_target_changed","The exact frame is no longer attached");return;}
    assignment->session=node->session;assignment->frameRaw=node->raw;
  }
  assignment->deadline=std::min<ULONGLONG>(GetTickCount64()+timeout,job->expires);
  if(!frameGuard.empty()){job->frameGuard=frameGuard;job->assignDocument=document;job->assignRef=ref;job->assignFrameKey=frameKey;}
  job->assignment=assignment;job->assigning=true;view->uploadAssignment=assignment;view->uploadAssignmentKey=id;view->uploadBusy=true;assignment->start();
}
/* Clearing has no filesystem reservation. Its bounded in-memory identity still
 * participates in the same input slot, cancellation timer and callback rules. */
static void xb_upload_clear(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,const std::string& id,const std::string& json){
  const auto key=xb_get(json,"upload_key"),document=xb_get(json,"document"),ref=xb_get(json,"ref");uint64_t timeout=10000;
  xb_input_frame_guard frameGuard;const bool framesValid=xb_input_read_frames(json,frameGuard);const auto frameKey=xb_input_frame_key(frameGuard);
  if(!framesValid||key.empty()||key.size()>100||document.empty()||document.size()>100||ref.empty()||ref.size()>100||
     (!xb_get(json,"timeout_ms").empty()&&!xb_upload_uint(xb_get(json,"timeout_ms"),timeout))||!timeout||timeout>60000||
     xb_get(json,"navigation")!=std::to_string(view->navigation)){
    host->reply(id,"{\"accepted\":false,\"dispatched\":false,\"error\":{\"code\":\"upload_arguments_invalid\",\"message\":\"Clear requires a fresh document, exact input, operation key and deadline\",\"effect_may_have_occurred\":false}}");return;
  }
  auto store=host->uploads;unsigned owned=0,total=0;
  for(const auto& item:store->jobs){auto old=item.second;if(old->clear)total++;
    if(old->view.lock()==view){owned+=old->clear;if(old->key==key){
      if(!old->clear||old->assignDocument!=document||old->assignRef!=ref||old->assignFrameKey!=frameKey||old->navigation!=view->navigation){
        host->reply(id,"{\"accepted\":false,\"dispatched\":false,\"error\":{\"code\":\"upload_identity_conflict\",\"message\":\"This identity belongs to another file operation\",\"effect_may_have_occurred\":false}}");return;
      }xb_upload_assign(host,view,id,json,old);return;
    }}
  }
  if(!xb_input_frames_live(view,frameGuard)){host->reply(id,"","The clear target frame document changed");return;}
  if(owned>=32||total>=64){host->reply(id,"","Clear-operation receipt limit reached; wait for previous deadlines or end the browser");return;}
  GUID guid{};wchar_t identifier[40]{};if(FAILED(CoCreateGuid(&guid))||!StringFromGUID2(guid,identifier,40)){host->reply(id,"","Cannot allocate a clear-operation identity");return;}
  auto job=std::make_shared<xb_upload_job>();job->view=view;job->key=key;job->navigation=view->navigation;
  job->clear=job->sealed=job->prepared=true;job->assignDocument=document;job->assignRef=ref;job->assignFrameKey=frameKey;job->frameGuard=frameGuard;job->expires=GetTickCount64()+timeout;
  job->ticket="c-"+webview::detail::narrow_string(std::wstring(identifier+1,36));store->jobs[job->ticket]=job;
  xb_upload_stores[host->window]=store;
  if(!SetTimer(host->window,xb_upload_timer,250,xb_upload_tick)){job->closing=true;store->collect();host->reply(id,"","Cannot retain clear-operation deadline");return;}
  xb_upload_assign(host,view,id,json,job);
}
