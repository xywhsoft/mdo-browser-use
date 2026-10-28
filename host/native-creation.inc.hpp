// Creation owns its original reply identity until one terminal receipt. Close
// settles it before waiting for a provider callback, and callbacks may not replay
// that receipt after cancellation or touch a replacement's request identity.
template<class Host,class View>
static bool xb_creation_settle(const Host& host,const View& view,const std::string& result,const std::string& error=""){
  if(view->creationRequest.empty())return false;
  const auto request=std::move(view->creationRequest);view->creationRequest.clear();
  host->reply(request,result,error);return true;
}
template<class Host,class View>
static void xb_creation_cancel(const Host& host,const View& view){
  auto opening=std::move(view->opening);
  try{xb_creation_settle(host,view,"","Browser creation was cancelled");}
  catch(...){if(opening)opening->complete();throw;}
  if(opening)opening->complete();
}
