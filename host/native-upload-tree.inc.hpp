/* Directory reservations contain only a validated, relative manifest. Native
 * cache paths are derived here; task-host paths never enter this structure. */
struct xb_upload_tree_file{std::string path,sha;uint64_t size=0;};
struct xb_upload_tree{
  std::string name;std::vector<std::string> directories;std::vector<xb_upload_tree_file> files;uint64_t size=0;
};
static bool xb_upload_tree_path_valid(const std::string& path,unsigned depth){
  if(path.empty()||path.size()>1024||path.back()=='/')return false;
  unsigned count=0;size_t at=0;
  while(at<path.size()){
    const auto end=path.find('/',at);const auto part=path.substr(at,end==std::string::npos?path.size()-at:end-at);
    if(++count>depth||!xb_upload_name(part))return false;
    if(end==std::string::npos)break;at=end+1;
  }return true;
}
static bool xb_upload_tree_valid(const xb_upload_tree& tree){
  if(!xb_upload_name(tree.name)||tree.directories.size()>128||tree.files.empty()||tree.files.size()>128)return false;
  std::vector<std::string> paths;paths.reserve(tree.directories.size()+tree.files.size());uint64_t size=0;
  for(const auto& path:tree.directories){if(!xb_upload_tree_path_valid(path,16))return false;paths.push_back(path);}
  for(const auto& file:tree.files){
    if(!xb_upload_tree_path_valid(file.path,17)||!xb_upload_hash(file.sha)||file.size>33554432||file.size>67108864-size)return false;
    size+=file.size;paths.push_back(file.path);
  }
  std::vector<std::wstring> wide;wide.reserve(paths.size());
  for(size_t i=0;i<paths.size();i++){
    const auto parent=paths[i].find_last_of('/');
    if(parent!=std::string::npos&&std::find(tree.directories.begin(),tree.directories.end(),paths[i].substr(0,parent))==tree.directories.end())return false;
    auto left=webview::detail::widen_string(paths[i]);
    for(size_t j=0;j<i;j++){
      const int order=CompareStringOrdinal(left.data(),static_cast<int>(left.size()),wide[j].data(),static_cast<int>(wide[j].size()),TRUE);
      if(!order||order==CSTR_EQUAL)return false; // Include Unicode case aliases and file/directory collisions.
    }
    wide.push_back(std::move(left));
  }return size==tree.size;
}
// TREE_JSON: raw tokens retain their JSON types instead of coercing strings,
// numbers or objects into a superficially valid relative path/size.
static std::string xb_upload_tree_raw(const std::string& json,const char* key,int index=0){
  const char* value=nullptr;size_t size=0;
  webview::detail::json_parse_c(json.data(),json.size(),key,key?std::strlen(key):static_cast<size_t>(index),&value,&size);
  return value?std::string(value,size):std::string{};
}
static bool xb_upload_tree_string(const std::string& json,const char* key,std::string& value,int index=0){
  const auto raw=xb_upload_tree_raw(json,key,index);if(raw.size()<2||raw.front()!='"'||raw.back()!='"')return false;
  value=key?xb_get(json,key):webview::detail::json_parse(json,"",index);return !value.empty();
}
static bool xb_upload_tree_parse(const std::string& json,xb_upload_tree& out){
  if(json.size()>245760||json.empty()||json.front()!='{'||json.back()!='}')return false;
  xb_upload_tree tree;if(!xb_upload_tree_string(json,"name",tree.name))return false;
  const auto directories=xb_upload_tree_raw(json,"directories"),files=xb_upload_tree_raw(json,"files");
  if(directories.empty()||directories.front()!='['||directories.back()!=']'||files.empty()||files.front()!='['||files.back()!=']')return false;
  for(int i=0;i<129;i++){
    const auto raw=xb_upload_tree_raw(directories,nullptr,i);if(raw.empty())break;
    std::string path;if(i==128||!xb_upload_tree_string(directories,nullptr,path,i))return false;tree.directories.push_back(path);
  }
  for(int i=0;i<129;i++){
    const auto raw=xb_upload_tree_raw(files,nullptr,i);if(raw.empty())break;
    xb_upload_tree_file file;
    if(i==128||raw.front()!='{'||raw.back()!='}'||!xb_upload_tree_string(raw,"path",file.path)||!xb_upload_tree_string(raw,"sha256",file.sha))return false;
    const auto size=xb_upload_tree_raw(raw,"size");
    if(!xb_upload_uint(size,file.size)||std::to_string(file.size)!=size||file.size>67108864-tree.size)return false;
    tree.size+=file.size;tree.files.push_back(file);
  }
  if(!xb_upload_tree_valid(tree))return false;out=std::move(tree);return true;
}
static std::string xb_upload_tree_json(const xb_upload_tree& tree){
  std::string json="{\"name\":"+xb_quote(tree.name)+",\"directories\":[";
  for(const auto& path:tree.directories){if(json.back()!='[')json+=",";json+=xb_quote(path);}json+="],\"files\":[";
  for(const auto& file:tree.files){if(json.back()!='[')json+=",";json+="{\"path\":"+xb_quote(file.path)+",\"size\":"+std::to_string(file.size)+",\"sha256\":"+xb_quote(file.sha)+"}";}return json+"]}";
}
static std::vector<std::string> xb_upload_tree_directories(const xb_upload_tree& tree){
  std::vector<std::string> paths={"tree","tree/"+tree.name};
  for(const auto& path:tree.directories)paths.push_back("tree/"+tree.name+"/"+path);
  std::sort(paths.begin(),paths.end(),[](const std::string& a,const std::string& b){return a.size()==b.size()?a<b:a.size()<b.size();});return paths;
}
static std::wstring xb_upload_tree_native(const std::wstring& base,const std::string& relative){
  auto path=webview::detail::widen_string(relative);std::replace(path.begin(),path.end(),L'/',L'\\');return base+L"\\"+path;
}
struct xb_upload_tree_inventory{std::vector<std::wstring> files,directories;};
static bool xb_upload_tree_inspect(const std::wstring& base,const xb_upload_tree& tree,xb_upload_tree_inventory& out,bool complete=false){
  const auto allowedDirectories=xb_upload_tree_directories(tree);std::vector<std::string> allowedFiles,queue={""},seenFiles;
  for(const auto& file:tree.files)allowedFiles.push_back("tree/"+tree.name+"/"+file.path);
  xb_upload_tree_inventory inventory;size_t inspected=0;
  for(size_t i=0;i<queue.size();i++){
    if(queue.size()>131)return false;
    const auto directory=queue[i].empty()?base:xb_upload_tree_native(base,queue[i]);const DWORD flags=GetFileAttributesW(directory.c_str());
    if(flags==INVALID_FILE_ATTRIBUTES){if(i==0&&!complete&&(GetLastError()==ERROR_FILE_NOT_FOUND||GetLastError()==ERROR_PATH_NOT_FOUND)){out=std::move(inventory);return true;}return false;}
    if(!(flags&FILE_ATTRIBUTE_DIRECTORY)||(flags&FILE_ATTRIBUTE_REPARSE_POINT))return false;
    WIN32_FIND_DATAW entry{};xb_upload_finder find;find.value=FindFirstFileW((directory+L"\\*").c_str(),&entry);
    if(find.value==INVALID_HANDLE_VALUE){if(GetLastError()==ERROR_FILE_NOT_FOUND)continue;return false;}
    do{
      const std::wstring wide=entry.cFileName;if(wide==L"."||wide==L"..")continue;
      if(++inspected>260||(entry.dwFileAttributes&FILE_ATTRIBUTE_REPARSE_POINT))return false;
      const auto name=webview::detail::narrow_string(wide),relative=queue[i].empty()?name:queue[i]+"/"+name;
      if(queue[i].empty()&&name==".lease"){if(entry.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY)return false;continue;}
      const auto native=directory+L"\\"+wide;
      if(entry.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY){
        if(std::find(allowedDirectories.begin(),allowedDirectories.end(),relative)==allowedDirectories.end()||std::find(queue.begin(),queue.end(),relative)!=queue.end())return false;
        queue.push_back(relative);inventory.directories.push_back(native);
      }else{
        const auto at=std::find(allowedFiles.begin(),allowedFiles.end(),relative);
        if(at==allowedFiles.end()||std::find(seenFiles.begin(),seenFiles.end(),relative)!=seenFiles.end())return false;
        const auto size=static_cast<uint64_t>(entry.nFileSizeHigh)<<32|entry.nFileSizeLow;
        const auto expected=tree.files[static_cast<size_t>(at-allowedFiles.begin())].size;
        // Enumeration sizes may lag open writers. The seal separately checks
        // exact sizes and hashes on pinned file handles; this scan proves layout.
        if(size>expected)return false;
        seenFiles.push_back(relative);inventory.files.push_back(native);
      }
    }while(FindNextFileW(find.value,&entry));if(GetLastError()!=ERROR_NO_MORE_FILES)return false;
  }
  if(complete&&(inventory.directories.size()!=allowedDirectories.size()||inventory.files.size()!=allowedFiles.size()))return false;
  std::sort(inventory.directories.begin(),inventory.directories.end(),[](const std::wstring& a,const std::wstring& b){return a.size()>b.size();});
  out=std::move(inventory);return true;
}
static bool xb_upload_tree_cleanup(const std::wstring& base,const xb_upload_tree& tree){
  xb_upload_tree_inventory inventory;if(!xb_upload_tree_inspect(base,tree,inventory))return false;
  bool ok=true; // Do not delete any known entry until the entire layout passed.
  for(const auto& file:inventory.files)if(!xb_upload_remove_file(file))ok=false;
  if(!ok)return false;
  for(const auto& directory:inventory.directories)if(!xb_upload_remove_directory(directory))ok=false;
  return ok;
}
