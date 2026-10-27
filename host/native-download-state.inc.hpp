struct xb_download_store;
struct xb_download_protocol{std::string url,frame,name,state;bool bound=false,retired=false;unsigned begins=0;ULONGLONG updated=0;};
struct xb_download_state{
  ICoreWebView2_4* source=nullptr;EventRegistrationToken token{};
  ICoreWebView2DevToolsProtocolEventReceiver* beginReceiver=nullptr;ICoreWebView2DevToolsProtocolEventReceiver* progressReceiver=nullptr;EventRegistrationToken beginToken{},progressToken{};
  bool subscribed=false,supported=false,armed=false,protocolReady=false,arming=false,beginSubscribed=false,progressSubscribed=false;unsigned offerTimeout=120000,activeProtocol=0;uint64_t sequence=0,protocolBegins=0,identityRejected=0;std::string error;
  std::map<std::string,xb_download_protocol> protocol;
  void retire(xb_download_protocol& item){if(!item.url.empty()&&activeProtocol)activeProtocol--;item.bound=item.retired=true;item.url.clear();item.frame.clear();item.name.clear();}
  void unsubscribe(){if(beginReceiver){if(beginSubscribed)beginReceiver->remove_DevToolsProtocolEventReceived(beginToken);beginReceiver->Release();beginReceiver=nullptr;}if(progressReceiver){if(progressSubscribed)progressReceiver->remove_DevToolsProtocolEventReceived(progressToken);progressReceiver->Release();progressReceiver=nullptr;}if(source){if(subscribed)source->remove_DownloadStarting(token);source->Release();source=nullptr;}subscribed=beginSubscribed=progressSubscribed=false;}
  ~xb_download_state(){unsubscribe();}
};
