struct xb_chooser_operation;
struct xb_world_context;
struct xb_chooser_pending{
  std::string id,frame,session,loader,document,ref,bindingKey;std::vector<std::pair<std::string,std::string>> frameGuard;
  std::shared_ptr<xb_world_context> world,rootWorld;
  int backend=0;bool multiple=false;UINT64 navigation=0;ULONGLONG expires=0;
};
struct xb_chooser_state{
  ICoreWebView2DevToolsProtocolEventReceiver* receiver=nullptr;EventRegistrationToken token{};
  bool subscribed=false,supported=false,enabled=false,wanted=false,changing=false,policyFailed=false,objectFailed=false,cleanupWanted=false;
  unsigned lease=120000,ignored=0;uint64_t epoch=0;std::string error;
  std::shared_ptr<xb_chooser_pending> pending,deferredCancel;std::weak_ptr<xb_chooser_operation> operation;
  void unsubscribe(){if(receiver){if(subscribed)receiver->remove_DevToolsProtocolEventReceived(token);receiver->Release();receiver=nullptr;}subscribed=false;}
  ~xb_chooser_state(){unsubscribe();}
};
