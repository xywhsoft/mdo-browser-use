/* Private browser-device staging. The wire carries bounded bytes, never a
 * task-host path. Cache names and handles are not exposed to page/SDK callers. */
static constexpr UINT_PTR xb_upload_timer=0xBADD5;
static constexpr uint64_t xb_upload_total_limit=128ull*1024*1024;
struct xb_upload_handle{
  HANDLE value=INVALID_HANDLE_VALUE;
  xb_upload_handle()=default;xb_upload_handle(const xb_upload_handle&)=delete;xb_upload_handle& operator=(const xb_upload_handle&)=delete;
  ~xb_upload_handle(){if(value&&value!=INVALID_HANDLE_VALUE)CloseHandle(value);}
};
struct xb_upload_local{
  HLOCAL value=nullptr;
  explicit xb_upload_local(HLOCAL owned):value(owned){}
  xb_upload_local(const xb_upload_local&)=delete;xb_upload_local& operator=(const xb_upload_local&)=delete;
  ~xb_upload_local(){if(value)LocalFree(value);}
};
struct xb_upload_provider{HCRYPTPROV value=0;xb_upload_provider()=default;xb_upload_provider(const xb_upload_provider&)=delete;xb_upload_provider& operator=(const xb_upload_provider&)=delete;~xb_upload_provider(){if(value)CryptReleaseContext(value,0);}};
struct xb_upload_hasher{HCRYPTHASH value=0;xb_upload_hasher()=default;xb_upload_hasher(const xb_upload_hasher&)=delete;xb_upload_hasher& operator=(const xb_upload_hasher&)=delete;~xb_upload_hasher(){if(value)CryptDestroyHash(value);}};
struct xb_upload_finder{HANDLE value=INVALID_HANDLE_VALUE;xb_upload_finder()=default;xb_upload_finder(const xb_upload_finder&)=delete;xb_upload_finder& operator=(const xb_upload_finder&)=delete;~xb_upload_finder(){if(value!=INVALID_HANDLE_VALUE)FindClose(value);}};
static bool xb_upload_uint(const std::string& text,uint64_t& out){
  if(text.empty()||text.size()>20)return false;out=0;
  for(unsigned char c:text){if(c<'0'||c>'9'||out>(UINT64_MAX-(c-'0'))/10)return false;out=out*10+c-'0';}return true;
}
static bool xb_upload_name(const std::string& name){
  if(name.empty()||name.size()>240||name.back()=='.'||name.back()==' ')return false;
  for(unsigned char c:name)if(c<32||c==127||std::string("<>:\"/\\|?*").find(c)!=std::string::npos)return false;
  auto base=name.substr(0,name.find('.'));for(char& c:base)if(c>='a'&&c<='z')c-=32;
  if((!base.empty()&&base.back()==' ')||base=="CON"||base=="PRN"||base=="AUX"||base=="NUL"||base=="CONIN$"||base=="CONOUT$"||base=="CLOCK$")return false;
  auto wide=webview::detail::widen_string(name),stem=webview::detail::widen_string(base);
  if(stem.size()==4&&(stem.rfind(L"COM",0)==0||stem.rfind(L"LPT",0)==0)&&
    ((stem[3]>=L'0'&&stem[3]<=L'9')||stem[3]==0x00B9||stem[3]==0x00B2||stem[3]==0x00B3))return false;
  return !wide.empty()&&webview::detail::narrow_string(wide)==name;
}
static bool xb_upload_hash(const std::string& hash){
  if(hash.size()!=64)return false;for(unsigned char c:hash)if(!((c>='0'&&c<='9')||(c>='a'&&c<='f')))return false;return true;
}
static bool xb_upload_decode(const std::string& text,std::vector<unsigned char>& out){
  if(text.empty()||text.size()>87384||text.size()%4)return false;out.clear();out.reserve(text.size()/4*3);
  auto digit=[](unsigned char c)->int{return c>='A'&&c<='Z'?c-'A':c>='a'&&c<='z'?c-'a'+26:c>='0'&&c<='9'?c-'0'+52:c=='+'?62:c=='/'?63:-1;};
  for(size_t i=0;i<text.size();i+=4){
    int a=digit(text[i]),b=digit(text[i+1]),c=text[i+2]=='='?0:digit(text[i+2]),d=text[i+3]=='='?0:digit(text[i+3]);
    bool pad2=text[i+2]=='=',pad1=text[i+3]=='=';
    if(a<0||b<0||c<0||d<0||(pad2&&!pad1)||((pad1||pad2)&&i+4!=text.size())||(pad2&&(b&15))||(pad1&&!pad2&&(c&3)))return false;
    out.push_back(static_cast<unsigned char>((a<<2)|(b>>4)));if(!pad2)out.push_back(static_cast<unsigned char>((b<<4)|(c>>2)));if(!pad1)out.push_back(static_cast<unsigned char>((c<<6)|d));
  }return out.size()<=65536;
}
static bool xb_upload_remove_file(const std::wstring& path){return DeleteFileW(path.c_str())||GetLastError()==ERROR_FILE_NOT_FOUND||GetLastError()==ERROR_PATH_NOT_FOUND;}
static bool xb_upload_remove_directory(const std::wstring& path){return RemoveDirectoryW(path.c_str())||GetLastError()==ERROR_PATH_NOT_FOUND||GetLastError()==ERROR_FILE_NOT_FOUND;}
static bool xb_upload_plain_directory(const std::wstring& path){DWORD flags=GetFileAttributesW(path.c_str());return flags!=INVALID_FILE_ATTRIBUTES&&(flags&FILE_ATTRIBUTE_DIRECTORY)&&!(flags&FILE_ATTRIBUTE_REPARSE_POINT);}
static bool xb_upload_cleanup_directory(const std::wstring& path){DWORD flags=GetFileAttributesW(path.c_str());if(flags==INVALID_FILE_ATTRIBUTES)return GetLastError()==ERROR_FILE_NOT_FOUND||GetLastError()==ERROR_PATH_NOT_FOUND;return (flags&FILE_ATTRIBUTE_DIRECTORY)&&!(flags&FILE_ATTRIBUTE_REPARSE_POINT);}
static bool xb_upload_private_root(const std::wstring& path){
  xb_upload_handle token;DWORD bytes=0;LPWSTR sid=nullptr;PSECURITY_DESCRIPTOR descriptor=nullptr;bool ok=false;
  if(!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token.value))return false;
  GetTokenInformation(token.value,TokenUser,nullptr,0,&bytes);std::vector<unsigned char> data(bytes);
  if(bytes&&GetTokenInformation(token.value,TokenUser,data.data(),bytes,&bytes)&&ConvertSidToStringSidW(reinterpret_cast<TOKEN_USER*>(data.data())->User.Sid,&sid)){
    xb_upload_local ownedSid(sid);
    std::wstring sddl=L"D:P(A;OICI;FA;;;SY)(A;OICI;FA;;;"+std::wstring(sid)+L")";
    if(ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.c_str(),SDDL_REVISION_1,&descriptor,nullptr)){
      xb_upload_local ownedDescriptor(descriptor);
      SECURITY_ATTRIBUTES attributes{sizeof(attributes),descriptor,FALSE};
      ok=(CreateDirectoryW(path.c_str(),&attributes)||GetLastError()==ERROR_ALREADY_EXISTS)&&xb_upload_plain_directory(path)&&
        SetFileSecurityW(path.c_str(),DACL_SECURITY_INFORMATION|PROTECTED_DACL_SECURITY_INFORMATION,descriptor);
    }
  }
  return ok;
}
struct xb_upload_bytes:std::vector<unsigned char>{
  using std::vector<unsigned char>::vector;
  ~xb_upload_bytes(){if(!empty())SecureZeroMemory(data(),size());}
};
struct xb_upload_guard{
  HANDLE handle=INVALID_HANDLE_VALUE;
  explicit xb_upload_guard(const std::wstring& root){
    if(!xb_upload_plain_directory(root))return;
    handle=CreateFileW((root+L"\\.guard").c_str(),GENERIC_READ|GENERIC_WRITE,0,nullptr,OPEN_ALWAYS,FILE_ATTRIBUTE_HIDDEN|FILE_FLAG_OPEN_REPARSE_POINT,nullptr);
    BY_HANDLE_FILE_INFORMATION info{};
    if(handle!=INVALID_HANDLE_VALUE&&(!GetFileInformationByHandle(handle,&info)||(info.dwFileAttributes&(FILE_ATTRIBUTE_DIRECTORY|FILE_ATTRIBUTE_REPARSE_POINT)))){CloseHandle(handle);handle=INVALID_HANDLE_VALUE;}
  }
  ~xb_upload_guard(){if(handle!=INVALID_HANDLE_VALUE)CloseHandle(handle);}
};
struct xb_upload_file{
  std::string name,sha;std::wstring directory,path;uint64_t size=0,received=0;HANDLE handle=INVALID_HANDLE_VALUE;bool directoryOwned=false,pathOwned=false;
  ~xb_upload_file(){if(handle!=INVALID_HANDLE_VALUE)CloseHandle(handle);}
};
struct xb_upload_job{
  std::weak_ptr<xb_view> view;std::string ticket,key,chooser;std::wstring directory;UINT64 navigation=0;uint64_t size=0;
  ULONGLONG expires=0;HANDLE lease=INVALID_HANDLE_VALUE;bool prepared=false,sealed=false,closing=false,directoryOwned=false,leaseOwned=false,retained=false,assigning=false,verified=false,dispatched=false,clear=false;
  std::weak_ptr<xb_upload_assignment> assignment;std::string assignDocument,assignRef,assignFrameKey,assignmentResult;xb_input_frame_guard frameGuard;
  std::vector<std::shared_ptr<xb_upload_file>> files;
  bool cleanup(){
    closing=true;bool ok=true;
    std::unique_ptr<xb_upload_guard> guard;
    if(directoryOwned){guard=std::make_unique<xb_upload_guard>(directory.substr(0,directory.find_last_of(L'\\')));if(guard->handle==INVALID_HANDLE_VALUE||!xb_upload_cleanup_directory(directory))return false;}
    for(const auto& file:files)if(file->directoryOwned&&!xb_upload_cleanup_directory(file->directory))return false;
    for(auto& file:files){if(file->handle!=INVALID_HANDLE_VALUE){CloseHandle(file->handle);file->handle=INVALID_HANDLE_VALUE;}if(file->pathOwned&&!xb_upload_remove_file(file->path))ok=false;if(file->directoryOwned&&!xb_upload_remove_directory(file->directory))ok=false;}
    if(!ok)return false;
    if(lease!=INVALID_HANDLE_VALUE){CloseHandle(lease);lease=INVALID_HANDLE_VALUE;}
    if(directoryOwned)return (!leaseOwned||xb_upload_remove_file(directory+L"\\.lease"))&&xb_upload_remove_directory(directory);
    return true;
  }
  ~xb_upload_job(){try{cleanup();}catch(...){/* Startup collector retains the private layout after allocation failure. */}if(lease!=INVALID_HANDLE_VALUE)CloseHandle(lease);}
};
static void xb_upload_assignment_tick(const std::shared_ptr<xb_upload_job>&);
static void xb_upload_cancel_assignment(const std::shared_ptr<xb_upload_job>&);
static void xb_upload_assign(const std::shared_ptr<xb_host>&,const std::shared_ptr<xb_view>&,const std::string&,const std::string&,const std::shared_ptr<xb_upload_job>&);
static void xb_upload_clear(const std::shared_ptr<xb_host>&,const std::shared_ptr<xb_view>&,const std::string&,const std::string&);
/* Only our fixed u-GUID / numeric-index / single-basename layout is collected.
 * A held lease or any unexpected/reparse entry is preserved, never traversed. */
static bool xb_upload_stale(const std::wstring& root){
  WIN32_FIND_DATAW entry{};xb_upload_finder find;find.value=FindFirstFileW((root+L"\\u-*").c_str(),&entry);
  if(find.value==INVALID_HANDLE_VALUE)return GetLastError()==ERROR_FILE_NOT_FOUND;
  bool ok=true;unsigned count=0;
  do{
    if(++count>64){ok=false;break;}std::wstring name=entry.cFileName;
    if(name.size()!=38||name.rfind(L"u-",0)!=0||!(entry.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY)||(entry.dwFileAttributes&FILE_ATTRIBUTE_REPARSE_POINT)){ok=false;continue;}
    bool guid=true;for(size_t i=2;i<38;i++){auto c=name[i];bool dash=i==10||i==15||i==20||i==25;if(dash?c!=L'-':!((c>=L'0'&&c<=L'9')||(c>=L'a'&&c<=L'f')||(c>=L'A'&&c<=L'F')))guid=false;}if(!guid){ok=false;continue;}
    auto job=root+L"\\"+name,leasePath=job+L"\\.lease";
    xb_upload_handle lease;lease.value=CreateFileW(leasePath.c_str(),GENERIC_READ,0,nullptr,OPEN_EXISTING,FILE_FLAG_OPEN_REPARSE_POINT,nullptr);
    if(lease.value==INVALID_HANDLE_VALUE){auto error=GetLastError();if(error==ERROR_SHARING_VIOLATION)continue;if(error==ERROR_FILE_NOT_FOUND&&xb_upload_remove_directory(job))continue;ok=false;continue;}
    BY_HANDLE_FILE_INFORMATION info{};bool valid=GetFileType(lease.value)==FILE_TYPE_DISK&&GetFileInformationByHandle(lease.value,&info)&&
      !(info.dwFileAttributes&(FILE_ATTRIBUTE_DIRECTORY|FILE_ATTRIBUTE_REPARSE_POINT))&&!info.nFileSizeHigh&&info.nFileSizeLow<=16384;
    std::string inventory;DWORD read=0;
    if(valid){inventory.resize(info.nFileSizeLow);valid=ReadFile(lease.value,inventory.data(),info.nFileSizeLow,&read,nullptr)&&read==info.nFileSizeLow;}
    std::vector<std::string> expected;
    const auto manifest=xb_get(inventory,"files");
    valid=valid&&xb_get(inventory,"format")=="mdo.browser-upload.v1"&&!manifest.empty()&&manifest.front()=='[';
    if(valid)for(int i=0;i<17;i++){auto item=webview::detail::json_parse(manifest,"",i);if(item.empty())break;auto name=xb_get(item,"name");uint64_t size=0;
      if(i==16||!xb_upload_name(name)||!xb_upload_hash(xb_get(item,"sha256"))||!xb_upload_uint(xb_get(item,"size"),size)||size>33554432){valid=false;break;}expected.push_back(name);
    }
    valid=valid&&!expected.empty();bool removed=true;unsigned inspected=0;
    std::vector<std::wstring> paths,directories;
    WIN32_FIND_DATAW child{};xb_upload_finder children;children.value=FindFirstFileW((job+L"\\*").c_str(),&child);
    if(children.value!=INVALID_HANDLE_VALUE){do{
      std::wstring index=child.cFileName;if(index==L"."||index==L".."||index==L".lease")continue;
      uint64_t number=0;
      if(++inspected>16||!valid||!xb_upload_uint(webview::detail::narrow_string(index),number)||number>=expected.size()||std::to_wstring(number)!=index||!(child.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY)||(child.dwFileAttributes&FILE_ATTRIBUTE_REPARSE_POINT)){removed=false;continue;}
      auto directory=job+L"\\"+index;WIN32_FIND_DATAW data{};xb_upload_finder files;files.value=FindFirstFileW((directory+L"\\*").c_str(),&data);
      unsigned names=0;bool clean=true;
      if(files.value!=INVALID_HANDLE_VALUE){do{std::wstring file=data.cFileName;if(file==L"."||file==L"..")continue;
        if(++names>1||(data.dwFileAttributes&(FILE_ATTRIBUTE_DIRECTORY|FILE_ATTRIBUTE_REPARSE_POINT))||webview::detail::narrow_string(file)!=expected[number])clean=false;
        else paths.push_back(directory+L"\\"+file);
      }while(FindNextFileW(files.value,&data));if(GetLastError()!=ERROR_NO_MORE_FILES)clean=false;}
      else if(GetLastError()!=ERROR_FILE_NOT_FOUND)clean=false;
      if(!clean)removed=false;else directories.push_back(directory);
    }while(FindNextFileW(children.value,&child));if(GetLastError()!=ERROR_NO_MORE_FILES)removed=false;}else removed=false;
    if(children.value!=INVALID_HANDLE_VALUE){FindClose(children.value);children.value=INVALID_HANDLE_VALUE;}
    // No delete occurs until the entire inventory/layout has been inspected.
    // A crash during inventory creation can leave only the reserved lease file.
    if(!valid&&inspected)removed=false;
    if(removed){for(const auto& path:paths)if(!xb_upload_remove_file(path))removed=false;for(const auto& directory:directories)if(!xb_upload_remove_directory(directory))removed=false;}
    CloseHandle(lease.value);lease.value=INVALID_HANDLE_VALUE;
    if(!removed||!xb_upload_remove_file(leasePath)||!xb_upload_remove_directory(job))ok=false;
  }while(FindNextFileW(find.value,&entry));if(GetLastError()!=ERROR_NO_MORE_FILES)ok=false;return ok;
}
struct xb_upload_store{
  std::weak_ptr<xb_host> host;std::wstring root;std::map<std::string,std::shared_ptr<xb_upload_job>> jobs;bool initialized=false;
  uint64_t reserved()const{uint64_t total=0;for(const auto& item:jobs)total+=item.second->size;return total;}
  size_t fileCount()const{size_t count=0;for(const auto& item:jobs)count+=item.second->files.size();return count;}
  size_t fileJobs()const{size_t count=0;for(const auto& item:jobs)count+=!item.second->clear;return count;}
  bool initialize(const std::shared_ptr<xb_host>& owner){
    if(initialized)return true;
    host=owner;root=owner->profile+L"\\mdo-browser-use-transfers";
    if(!xb_upload_private_root(root))return false;
    xb_upload_guard guard(root);if(guard.handle==INVALID_HANDLE_VALUE||!xb_upload_stale(root))return false;
    initialized=true;return true;
  }
  void collect(){
    auto owner=host.lock();bool watching=false;
    for(auto at=jobs.begin();at!=jobs.end();){auto job=at->second;auto view=job->view.lock();
      if(job->assigning)xb_upload_assignment_tick(job);
      if(!job->prepared||!owner||!view||view->closed||(!job->retained&&!job->assigning&&(!xb_automation_live(owner,view,job->navigation)||!xb_input_frames_live(view,job->frameGuard)||GetTickCount64()>=job->expires||!job->chooser.empty()&&!xb_chooser_upload_ready(view,job->chooser,job->assignDocument,job->assignRef))))job->closing=true;
      if(job->closing&&!job->assigning&&job->cleanup())at=jobs.erase(at);else{watching|=!job->retained||job->assigning||job->closing;++at;}
    }
    if(!watching&&owner)KillTimer(owner->window,xb_upload_timer);
  }
};
static std::map<HWND,std::weak_ptr<xb_upload_store>> xb_upload_stores;
static void CALLBACK xb_upload_tick(HWND window,UINT,UINT_PTR,DWORD){auto at=xb_upload_stores.find(window);if(at==xb_upload_stores.end()){KillTimer(window,xb_upload_timer);return;}if(auto store=at->second.lock())store->collect();else{KillTimer(window,xb_upload_timer);xb_upload_stores.erase(at);}}
static void xb_upload_close_view(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view){xb_upload_abort_view(view);if(!host->uploads)return;for(auto& item:host->uploads->jobs)if(item.second->view.lock()==view)item.second->closing=true;host->uploads->collect();}
static void xb_upload_shutdown(const std::shared_ptr<xb_host>& host){KillTimer(host->window,xb_upload_timer);xb_upload_stores.erase(host->window);if(host->uploads){for(auto& item:host->uploads->jobs)item.second->cleanup();host->uploads.reset();}}
static std::string xb_upload_status(const std::shared_ptr<xb_upload_job>& job){
  std::string files="[";for(const auto& file:job->files){if(files.size()>1)files+=",";files+="{\"name\":"+xb_quote(file->name)+",\"size\":"+std::to_string(file->size)+",\"received\":"+std::to_string(file->received)+"}";}files+="]";
  return "{\"ticket\":"+xb_quote(job->ticket)+",\"clear\":"+(job->clear?"true":"false")+",\"sealed\":"+(job->sealed?"true":"false")+",\"closing\":"+(job->closing?"true":"false")+
    ",\"retained\":"+(job->retained?"true":"false")+",\"dispatched\":"+(job->dispatched?"true":"false")+",\"assigning\":"+(job->assigning?"true":"false")+",\"verified\":"+(job->verified?"true":"false")+
    (job->assignmentResult.empty()?"":",\"assignment_result\":"+job->assignmentResult)+",\"files\":"+files+"}";
}
static bool xb_upload_seal(const std::shared_ptr<xb_upload_job>& job){
  xb_upload_provider provider;if(!CryptAcquireContextW(&provider.value,nullptr,nullptr,PROV_RSA_AES,CRYPT_VERIFYCONTEXT))return false;
  bool ok=true;
  for(auto& file:job->files){
    CloseHandle(file->handle);file->handle=CreateFileW(file->path.c_str(),GENERIC_READ,FILE_SHARE_READ,nullptr,OPEN_EXISTING,FILE_FLAG_OPEN_REPARSE_POINT,nullptr);
    xb_upload_hasher sha;BY_HANDLE_FILE_INFORMATION info{};
    if(file->handle==INVALID_HANDLE_VALUE||!GetFileInformationByHandle(file->handle,&info)||(info.dwFileAttributes&(FILE_ATTRIBUTE_DIRECTORY|FILE_ATTRIBUTE_REPARSE_POINT))||
       (static_cast<uint64_t>(info.nFileSizeHigh)<<32|info.nFileSizeLow)!=file->size||!CryptCreateHash(provider.value,CALG_SHA_256,0,0,&sha.value)){ok=false;break;}
    uint64_t total=0;xb_upload_bytes buffer(65536);DWORD bytes=0;
    for(;;){if(GetTickCount64()>=job->expires||!ReadFile(file->handle,buffer.data(),static_cast<DWORD>(buffer.size()),&bytes,nullptr)){ok=false;break;}if(!bytes)break;total+=bytes;if(total>file->size||!CryptHashData(sha.value,buffer.data(),bytes,0)){ok=false;break;}}
    unsigned char digest[32]{};DWORD length=32;std::string hex;
    if(ok&&total==file->size&&CryptGetHashParam(sha.value,HP_HASHVAL,digest,&length,0)&&length==32){static const char chars[]="0123456789abcdef";for(auto c:digest){hex+=chars[c>>4];hex+=chars[c&15];}ok=hex==file->sha;}else ok=false;
    if(!ok)break;
  }
  return ok;
}
static void xb_upload_request(const std::shared_ptr<xb_host>& host,const std::shared_ptr<xb_view>& view,const std::string& id,const std::string& json){
  auto fail=[&](const char* error){host->reply(id,"",error);};auto action=xb_get(json,"action");
  if(!host->uploads)host->uploads=std::make_shared<xb_upload_store>();auto store=host->uploads;store->host=host;
  if(action=="begin"&&!store->initialize(host)){fail("Private browser transfer cache is unavailable or has uncollected entries");return;}
  store->collect();
  if(action=="clear"){xb_upload_clear(host,view,id,json);return;}
  if(action=="clear_status"){
    const auto key=xb_get(json,"upload_key");for(const auto& item:store->jobs)if(item.second->clear&&item.second->key==key&&item.second->view.lock()==view){host->reply(id,xb_upload_status(item.second));return;}
    fail("Clear-operation receipt expired or belongs to another browser");return;
  }
  if(action=="clear_cancel"){
    const auto key=xb_get(json,"upload_key");for(auto& item:store->jobs)if(item.second->clear&&item.second->key==key&&item.second->view.lock()==view){xb_upload_cancel_assignment(item.second);item.second->closing=true;break;}
    store->collect();host->reply(id,"{\"cancelled\":true}");return;
  }
  if(action=="begin"){
    const auto key=xb_get(json,"upload_key"),manifest=xb_get(json,"files");uint64_t timeout=60000;
    if(view->uploadCleanupFailed||!xb_chooser_upload_ready(view,xb_get(json,"chooser"),xb_get(json,"document"),xb_get(json,"ref"))||key.empty()||key.size()>100||(!xb_get(json,"timeout_ms").empty()&&!xb_upload_uint(xb_get(json,"timeout_ms"),timeout))||timeout<1000||timeout>120000||manifest.empty()||manifest.front()!='['||
       xb_get(json,"navigation")!=std::to_string(view->navigation)||view->loading||view->initialNavigationPending||!xb_automation_live(host,view,view->navigation)){fail("Upload needs a live document, exact key and bounded file manifest");return;}
    auto job=std::make_shared<xb_upload_job>();job->view=view;job->key=key;job->navigation=view->navigation;job->expires=GetTickCount64()+timeout;
    if(!xb_input_pin_frames(view,json,job->frameGuard)){fail("Upload frame documents changed before byte admission");return;}
    if(!job->frameGuard.empty()){
      job->assignDocument=xb_get(json,"document");job->assignRef=xb_get(json,"ref");job->assignFrameKey=xb_input_frame_key(job->frameGuard);
      if(job->assignDocument.empty()||job->assignDocument.size()>100||job->assignRef.empty()||job->assignRef.size()>100){fail("Framed uploads require their exact local document and reference");return;}
    }
    job->chooser=xb_get(json,"chooser");if(!job->chooser.empty()){job->assignDocument=xb_get(json,"document");job->assignRef=xb_get(json,"ref");}
    for(int n=0;n<17;n++){auto item=webview::detail::json_parse(manifest,"",n);if(item.empty())break;auto file=std::make_shared<xb_upload_file>();file->name=xb_get(item,"name");file->sha=xb_get(item,"sha256");
      if(n==16||!xb_upload_name(file->name)||!xb_upload_hash(file->sha)||!xb_upload_uint(xb_get(item,"size"),file->size)||file->size>32u*1024u*1024u){fail("Upload manifest needs at most sixteen safe names, complete SHA-256 and 32 MiB files");return;}
      job->size+=file->size;job->files.push_back(file);
    }
    if(job->files.empty()||job->size>64u*1024u*1024u){fail("Upload exceeds its file count or 64 MiB byte budget");return;}
    unsigned owned=0;for(const auto& item:store->jobs)if(item.second->view.lock()==view){owned+=!item.second->clear;if(item.second->key==key){
      auto old=item.second;bool same=!old->closing&&old->navigation==job->navigation&&old->files.size()==job->files.size();
      same=same&&!old->clear&&old->chooser==job->chooser&&old->assignFrameKey==job->assignFrameKey&&((job->chooser.empty()&&job->frameGuard.empty())||(old->assignDocument==job->assignDocument&&old->assignRef==job->assignRef));
      for(size_t i=0;same&&i<job->files.size();i++)same=old->files[i]->name==job->files[i]->name&&old->files[i]->size==job->files[i]->size&&old->files[i]->sha==job->files[i]->sha;
      if(same)host->reply(id,xb_upload_status(old));else fail("Upload identity conflicts with its original manifest");return;
    }}
    if(owned>=16||store->fileJobs()>=32||job->files.size()>128-store->fileCount()||job->size>xb_upload_total_limit-store->reserved()){fail("Browser transfer cache resource limit reached; release completed staging first");return;}
    GUID guid{};wchar_t identifier[40]{};if(FAILED(CoCreateGuid(&guid))||!StringFromGUID2(guid,identifier,40)){fail("Cannot allocate a private upload identity");return;}
    xb_upload_guard guard(store->root);if(guard.handle==INVALID_HANDLE_VALUE){fail("Another browser is updating its transfer cache; retry admission after checking its receipt");return;}
    if(!xb_upload_stale(store->root)){fail("Private browser transfer cache has uncollected entries; inspect them before another admission");return;}
    job->ticket="u-"+webview::detail::narrow_string(std::wstring(identifier+1,36));job->directory=store->root+L"\\"+webview::detail::widen_string(job->ticket);
    if(!CreateDirectoryW(job->directory.c_str(),nullptr)){fail("Cannot reserve a private upload directory");return;}job->directoryOwned=true;
    job->lease=CreateFileW((job->directory+L"\\.lease").c_str(),GENERIC_READ|GENERIC_WRITE,0,nullptr,CREATE_NEW,FILE_ATTRIBUTE_HIDDEN,nullptr);
    if(job->lease==INVALID_HANDLE_VALUE){fail("Cannot lock the private upload reservation");return;}job->leaseOwned=true;
    store->jobs[job->ticket]=job;
    auto reject=[&](const char* error){job->closing=true;store->collect();fail(error);};
    std::string inventory="{\"format\":\"mdo.browser-upload.v1\",\"files\":[";
    for(const auto& file:job->files){if(inventory.back()!='[')inventory+=",";inventory+="{\"name\":"+xb_quote(file->name)+",\"size\":"+std::to_string(file->size)+",\"sha256\":"+xb_quote(file->sha)+"}";}inventory+="]}";
    DWORD written=0;if(!WriteFile(job->lease,inventory.data(),static_cast<DWORD>(inventory.size()),&written,nullptr)||written!=inventory.size()){reject("Cannot retain the private upload cleanup inventory");return;}
    for(size_t i=0;i<job->files.size();i++){auto file=job->files[i];file->directory=job->directory+L"\\"+std::to_wstring(i);file->path=file->directory+L"\\"+webview::detail::widen_string(file->name);
      if(!CreateDirectoryW(file->directory.c_str(),nullptr)){reject("Cannot reserve an upload file directory");return;}file->directoryOwned=true;
      file->handle=CreateFileW(file->path.c_str(),GENERIC_READ|GENERIC_WRITE,FILE_SHARE_READ,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,nullptr);
      if(file->handle==INVALID_HANDLE_VALUE||GetFileType(file->handle)!=FILE_TYPE_DISK){reject("Cannot create a regular private upload file");return;}file->pathOwned=true;
    }
    xb_upload_stores[host->window]=store;
    if(!SetTimer(host->window,xb_upload_timer,250,xb_upload_tick)){job->closing=true;store->collect();fail("Cannot retain the upload expiry lease");return;}
    job->prepared=true;
    host->reply(id,xb_upload_status(job));return;
  }
  auto at=store->jobs.find(xb_get(json,"ticket"));
  if(action=="assign"){xb_upload_assign(host,view,id,json,at!=store->jobs.end()&&at->second->view.lock()==view?at->second:std::shared_ptr<xb_upload_job>{});return;}
  if(at==store->jobs.end()||at->second->view.lock()!=view){fail("Upload ticket expired or belongs to another browser");return;}auto job=at->second;
  if(action=="cancel"){xb_upload_cancel_assignment(job);if(!job->retained)job->closing=true;store->collect();host->reply(id,"{\"cancelled\":true,\"retained\":"+std::string(job->retained?"true":"false")+",\"cleanup_pending\":"+std::string(!job->retained&&store->jobs.count(job->ticket)?"true":"false")+"}");return;}
  if(action=="release"){
    if(xb_get(json,"confirmed")!="true"||job->assigning){fail("Confirm website file consumption and wait for assignment before releasing retained bytes");return;}
    job->closing=true;store->collect();host->reply(id,"{\"released\":true,\"cleanup_pending\":"+std::string(store->jobs.count(job->ticket)?"true":"false")+"}");return;
  }
  if(action=="status"){host->reply(id,xb_upload_status(job));return;}
  if(job->closing||!xb_input_frames_live(view,job->frameGuard)||!xb_chooser_upload_ready(view,job->chooser,job->assignDocument,job->assignRef)||xb_get(json,"navigation")!=std::to_string(job->navigation)||!xb_automation_live(host,view,job->navigation)){fail("Upload document, picker or control changed; inspect before retrying");return;}
  if(action=="seal"){
    if(job->sealed){host->reply(id,xb_upload_status(job));return;}
    for(const auto& file:job->files)if(file->received!=file->size){fail("Upload is incomplete; no file was assigned");return;}
    if(!xb_upload_seal(job)){job->closing=true;store->collect();fail("Upload bytes did not match the complete manifest or deadline; staging was cancelled");return;}
    job->sealed=true;host->reply(id,xb_upload_status(job));return;
  }
  if(action=="chunk"){
    uint64_t index=0,offset=0;if(job->sealed||!xb_upload_uint(xb_get(json,"file"),index)||index>=job->files.size()||!xb_upload_uint(xb_get(json,"offset"),offset)){fail("Invalid upload file, offset or sealed ticket");return;}
    xb_upload_bytes bytes;if(!xb_upload_decode(xb_get(json,"base64"),bytes)){fail("Upload chunks require canonical Base64 of at most 64 KiB");return;}
    auto file=job->files[index];if(offset>file->received||offset>file->size||bytes.size()>file->size-offset){fail("Upload chunk exceeds the ordered file range");return;}
    LARGE_INTEGER point{};point.QuadPart=static_cast<LONGLONG>(offset);bool ok=SetFilePointerEx(file->handle,point,nullptr,FILE_BEGIN)!=FALSE;DWORD count=0;
    if(offset<file->received){xb_upload_bytes previous(bytes.size());ok=ok&&bytes.size()<=file->received-offset&&ReadFile(file->handle,previous.data(),static_cast<DWORD>(previous.size()),&count,nullptr)&&count==previous.size()&&previous==bytes;}
    else{ok=ok&&WriteFile(file->handle,bytes.data(),static_cast<DWORD>(bytes.size()),&count,nullptr)&&count==bytes.size();if(ok)file->received+=count;}
    if(!ok){job->closing=true;store->collect();fail("Upload chunk write or replay verification failed; staging was cancelled");return;}
    host->reply(id,xb_upload_status(job));return;
  }
  fail("Unsupported native upload staging action");
}
