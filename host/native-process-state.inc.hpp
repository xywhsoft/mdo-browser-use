/* A failed renderer can recover only through a newly started, successfully
 * completed native navigation. A browser-process failure requires recreation.
 * This state never initiates navigation or replays a page operation. */
struct xb_process_failure_state {
  bool renderer=false;
  std::uint64_t failedNavigation=0,recoveryNavigation=0;
  void record(bool rendererExited,std::uint64_t navigation)noexcept{
    renderer=rendererExited;failedNavigation=navigation;recoveryNavigation=0;
  }
  void started(std::uint64_t navigation)noexcept{
    recoveryNavigation=renderer&&navigation&&navigation!=failedNavigation?navigation:0;
  }
  bool completed(std::uint64_t navigation,bool succeeded)noexcept{
    if(!renderer||!recoveryNavigation||navigation!=recoveryNavigation)return false;
    recoveryNavigation=0;
    if(!succeeded)return false;
    renderer=false;failedNavigation=0;return true;
  }
};
