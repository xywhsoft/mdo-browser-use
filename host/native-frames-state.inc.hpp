struct xb_frame_job;
struct xb_frame_receiver{
  ICoreWebView2DevToolsProtocolEventReceiver* source=nullptr;EventRegistrationToken token{};bool subscribed=false;
  ~xb_frame_receiver(){if(source){if(subscribed)source->remove_DevToolsProtocolEventReceived(token);source->Release();}}
};
struct xb_frame_node{
  std::string raw,parent,id,document,loader,url,name,session;bool loading=false;
};
struct xb_frame_source_identity{bool alive=true;};
struct xb_script_budget{unsigned pending=0;};
struct xb_script_reservation{
  std::shared_ptr<xb_script_budget> budget;
  explicit xb_script_reservation(const std::shared_ptr<xb_script_budget>& value):budget(value){budget->pending++;}
  void release(){if(budget){if(budget->pending)budget->pending--;budget.reset();}}
  ~xb_script_reservation(){release();}
};
struct xb_script_context{
  std::string frame,session,unique;int numeric=0;bool alive=true;
  std::shared_ptr<xb_frame_source_identity> sourceOwner;
};
struct xb_frame_session{
  std::string target,parent,error;bool ready=false,chooserEnabled=false;
  std::shared_ptr<xb_frame_source_identity> identity=std::make_shared<xb_frame_source_identity>();
};
struct xb_frame_state{
  ICoreWebView2_11* source=nullptr;bool ready=false,starting=false,closed=false;std::string root,error;
  uint64_t generation=0;std::vector<std::shared_ptr<xb_frame_receiver>> receivers;
  std::map<std::string,xb_frame_node> nodes;std::map<std::string,xb_frame_session> sessions;
  std::map<std::string,xb_frame_node> previousNodes;
  std::map<std::string,bool> loading;
  std::map<std::string,std::shared_ptr<xb_script_context>> scriptContexts;
  std::shared_ptr<xb_script_budget> scriptBudget=std::make_shared<xb_script_budget>();
  std::map<std::string,std::shared_ptr<xb_frame_job>> jobs;
  ~xb_frame_state(){receivers.clear();if(source)source->Release();}
};
