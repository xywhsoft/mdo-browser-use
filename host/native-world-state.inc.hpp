/* Private native execution-world identities. A numeric context ID is metadata,
 * never an execution selector. Retired pins cannot be rebound to a new realm. */
struct xb_world_context{
  std::string name,frame,loader,unique,session;int numeric=0;bool alive=true,registered=false,admitted=false,retired=false,inventoryLost=false,functions=false;
  std::shared_ptr<xb_frame_source_identity> sourceOwner;
};
struct xb_world_receiver{
  ICoreWebView2DevToolsProtocolEventReceiver* source=nullptr;EventRegistrationToken token{};bool subscribed=false;
  ~xb_world_receiver(){if(source){if(subscribed)source->remove_DevToolsProtocolEventReceived(token);source->Release();}}
};
struct xb_world_request;
struct xb_world_document{std::string loader,frame,session;unsigned created=0;};
struct xb_world_state{
  bool enabled=false,enabling=false,closed=false;std::string error;
  std::vector<std::shared_ptr<xb_world_receiver>> receivers;
  std::map<std::string,std::weak_ptr<xb_world_context>> names;
  std::map<std::string,bool> seen;unsigned created=0,documentWorlds=0;std::string documentProof;
  unsigned otherFrames=0,refreshes=0;
  std::shared_ptr<xb_world_context> original;
  std::shared_ptr<xb_world_request> pending;
  std::map<std::string,std::shared_ptr<xb_world_request>> framePending;
  std::map<std::string,std::shared_ptr<xb_world_context>> frameWorlds;
  std::map<std::string,xb_world_document> frameDocuments;
  std::map<std::string,std::weak_ptr<xb_frame_source_identity>> enabledSources;
};
