/* A policy acknowledgement must precede any new URL/adoption. The timer and
 * generation fence also cover missing, duplicate and late protocol callbacks. */
static constexpr UINT_PTR xb_download_policy_timer=0xBADD8;
static const std::string xb_download_policy_empty,xb_download_policy_changed="Browser control changed before download interception was ready",xb_download_policy_expired="Native download interception was not acknowledged before its deadline or control changed",xb_download_policy_stopped="The browser host stopped before download interception was ready";
using xb_download_arm_done=std::function<void(bool,const std::string&)>;
struct xb_download_arm_job;
static std::map<HWND,std::map<xb_view*,std::shared_ptr<xb_download_arm_job>>> xb_download_policies;
struct xb_download_arm_job:std::enable_shared_from_this<xb_download_arm_job>{
  std::weak_ptr<xb_host> owner;std::weak_ptr<xb_view> target;HWND window{};
  xb_view* targetKey=nullptr;std::string browser;ULONGLONG expires=0;bool finished=false;xb_download_arm_done done;
  void finish(bool ok,const std::string& cause){
    if(finished)return;finished=true;auto host=owner.lock();auto view=target.lock();
    auto state=view?view->downloads:nullptr;auto callback=std::move(done);
    const bool live=host&&host->alive&&view&&!view->closed&&!view->requestedClose&&!view->failed&&!view->manual&&state&&
      host->views.count(browser)&&host->views.at(browser)==view;
    ok=ok&&live;
    const std::string& error=ok?xb_download_policy_empty:cause.empty()?xb_download_policy_changed:cause;
    if(state){state->arming=false;state->arm.reset();if(ok)state->armed=state->protocolReady=true;else state->armed=false;}
    auto group=xb_download_policies.find(window);if(group!=xb_download_policies.end()){
      auto at=group->second.find(targetKey);if(at!=group->second.end()&&at->second.get()==this)group->second.erase(at);
      if(group->second.empty()){KillTimer(window,xb_download_policy_timer);xb_download_policies.erase(group);}
    }
    if(state&&!ok)try{state->error=error;}catch(...){state->error.clear();}
    // Cleanup is complete before any allocation or publication can fail.
    if(callback)try{callback(ok,error);}catch(...){}
  }
};
static void CALLBACK xb_download_policy_tick(HWND window,UINT,UINT_PTR,DWORD){
  auto group=xb_download_policies.find(window);if(group==xb_download_policies.end()){KillTimer(window,xb_download_policy_timer);return;}
  std::shared_ptr<xb_download_arm_job> expired[8];size_t count=0;
  for(const auto& item:group->second){auto host=item.second->owner.lock();auto view=item.second->target.lock();
    if((!host||!host->alive||!view||view->closed||view->requestedClose||view->failed||view->manual||GetTickCount64()>=item.second->expires)&&count<8)expired[count++]=item.second;
  }
  for(size_t i=0;i<count;i++)expired[i]->finish(false,xb_download_policy_expired);
}
static void xb_download_abort_arm(const std::shared_ptr<xb_view>& view,const char* reason){if(view->downloads)if(auto job=view->downloads->arm.lock()){try{job->finish(false,reason);}catch(...){job->finish(false,xb_download_policy_changed);}}}
template<class Done> static void xb_download_arm(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,Done&& done){
  auto state=view->downloads;
  if(!host->alive||view->closed||view->requestedClose||view->failed||view->manual||!state||!state->supported||!state->beginSubscribed||!state->progressSubscribed){done(false,"Native download interception is unavailable or controlled by the user");return;}
  const std::string* browser=nullptr;for(const auto& item:host->views)if(item.second==view){browser=&item.first;break;}
  if(!browser){done(false,"The native browser identity changed before policy admission");return;}
  if(state->arming){done(false,"Native download interception is already being acknowledged");return;}
  if(state->protocolReady){state->armed=true;done(true,{});return;}
  auto group=xb_download_policies.find(host->window);if(group!=xb_download_policies.end()&&group->second.size()>=8){done(false,"Native download policy limit reached");return;}
  const bool needsTimer=group==xb_download_policies.end();std::shared_ptr<xb_download_arm_job> job;
  try{
    job=std::make_shared<xb_download_arm_job>();job->owner=host;job->target=view;job->targetKey=view.get();job->window=host->window;job->expires=GetTickCount64()+5000;job->done=std::forward<Done>(done);job->browser=*browser;
    xb_download_policies[host->window][view.get()]=job;state->arming=true;state->arm=job;
    if(needsTimer&&!SetTimer(host->window,xb_download_policy_timer,50,xb_download_policy_tick)){job->finish(false,"Cannot retain the native download policy deadline");return;}
    xb_cdp(view,L"Page.enable","{}",[job](bool ok,const std::string& result){
      if(job->finished)return;const auto error=xb_get(result,"error");
      job->finish(ok&&error.empty(),ok&&error.empty()?std::string{}:"Cannot enable scoped page download identity events: "+xb_get(error,"message").substr(0,256));
    });
  }catch(...){
    if(job&&job->finished)return;
    const bool stored=job&&static_cast<bool>(job->done);
    if(job)job->finish(false,"Cannot allocate a bounded native download policy request");
    if(!stored)done(false,"Cannot allocate a bounded native download policy request");
  }
}
static void xb_download_policy_shutdown(const std::shared_ptr<xb_host>& host){
  auto group=xb_download_policies.find(host->window);if(group==xb_download_policies.end())return;
  std::shared_ptr<xb_download_arm_job> pending[8];size_t count=0;for(const auto& item:group->second)if(count<8)pending[count++]=item.second;
  for(size_t i=0;i<count;i++)pending[i]->finish(false,xb_download_policy_stopped);
  KillTimer(host->window,xb_download_policy_timer);xb_download_policies.erase(host->window);
}
