// UI-thread creation reservations outlive tab removal until the provider's
// terminal callback or destruction. They never borrow a dead host's counter.
class xb_creation_budget {
  unsigned count_=0;friend class xb_creation_state;
public:
  static constexpr unsigned limit=16;
  unsigned pending()const{return count_;}
};
class xb_creation_state {
public:
  enum class phase {environment,controller,interception,ready,failed,cancelled};
private:
  std::shared_ptr<xb_creation_budget> reservation_;
  phase phase_=phase::environment;unsigned long long started_=0,ended_=0;bool begun_=false,finished_=false;
  void release(){auto held=std::move(reservation_);if(held)--held->count_;}
public:
  xb_creation_state()=default;
  xb_creation_state(const xb_creation_state&)=delete;xb_creation_state& operator=(const xb_creation_state&)=delete;
  ~xb_creation_state(){release();}
  bool acquire(const std::shared_ptr<xb_creation_budget>& budget,unsigned long long now){
    if(begun_||!budget||budget->count_>=xb_creation_budget::limit)return false;
    reservation_=budget;++budget->count_;begun_=true;started_=now;return true;
  }
  bool advance(phase next){
    if(!reservation_||finished_||phase_==phase::cancelled||
       (next!=phase::controller&&next!=phase::interception)||static_cast<int>(next)!=static_cast<int>(phase_)+1)return false;
    phase_=next;return true;
  }
  void cancel(){if(begun_&&!finished_)phase_=phase::cancelled;}
  void finish(bool closed,bool ok,unsigned long long now){
    if(!begun_||finished_)return;
    finished_=true;ended_=now;
    phase_=closed||phase_==phase::cancelled?phase::cancelled:ok?phase::ready:phase::failed;release();
  }
  bool reserved()const{return bool(reservation_);}
  const char* name()const{
    if(!begun_)return "none";
    switch(phase_){case phase::environment:return "environment";case phase::controller:return "controller";
      case phase::interception:return "interception";case phase::ready:return "ready";case phase::failed:return "failed";case phase::cancelled:return "cancelled";}
    return "failed";
  }
  unsigned long long elapsed(unsigned long long now)const{
    if(!begun_)return 0;
    const auto end=finished_?ended_:now;return end>=started_?end-started_:0;
  }
};
