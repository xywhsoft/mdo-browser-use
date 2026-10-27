struct xb_frame_job;
struct xb_frame_receiver{
  ICoreWebView2DevToolsProtocolEventReceiver* source=nullptr;EventRegistrationToken token{};bool subscribed=false;
  ~xb_frame_receiver(){if(source){if(subscribed)source->remove_DevToolsProtocolEventReceived(token);source->Release();}}
};
struct xb_frame_node{
  std::string raw,parent,id,document,loader,url,name,session;bool loading=false;
};
struct xb_frame_session{
  std::string target,parent,error;bool ready=false;
};
struct xb_frame_state{
  ICoreWebView2_11* source=nullptr;bool ready=false,starting=false,closed=false;std::string root,error;
  uint64_t generation=0;std::vector<std::shared_ptr<xb_frame_receiver>> receivers;
  std::map<std::string,xb_frame_node> nodes;std::map<std::string,xb_frame_session> sessions;
  std::map<std::string,bool> loading;
  std::map<std::string,std::shared_ptr<xb_frame_job>> jobs;
  ~xb_frame_state(){receivers.clear();if(source)source->Release();}
};
