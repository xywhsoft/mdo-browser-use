static unsigned validations=0;
static void reject(const xb_upload_tree& tree){validations++;assert(!xb_upload_tree_valid(tree));}
static void validation_checks(){
  xb_upload_tree base;base.name="selected";base.directories={"nested","nested/empty"};
  base.files={{"root.bin",std::string(64,'a'),0},{"nested/a.txt",std::string(64,'b'),3}};base.size=3;
  assert(xb_upload_tree_valid(base));
  xb_upload_tree parsed;const auto json=xb_upload_tree_json(base);
  assert(xb_upload_tree_parse(json,parsed)&&xb_upload_tree_json(parsed)==json);
  for(const auto& path:{"..",".","/root","../escape","a//b","a/../b","a/","C:/file","a\\file","CON","NUL.txt","LPT1.log","trail.","trail ","bad*name"}){
    auto bad=base;bad.files[0].path=path;reject(bad);
  }
  for(const auto& name:{"..","CON","a/b","a\\b","a:","trailing.","trailing "}){auto bad=base;bad.name=name;reject(bad);}
  {auto bad=base;bad.name=std::string("a\0b",3);reject(bad);}
  {auto bad=base;bad.files[0].path=std::string(241,'a');reject(bad);}
  {auto bad=base;bad.files[0].path=std::string(1025,'a');reject(bad);}
  {auto bad=base;bad.files[0].path="absent/a.txt";reject(bad);}
  {auto bad=base;bad.files[1].path="NESTED/a.txt";reject(bad);}
  {auto bad=base;bad.files[1].path="ROOT.BIN";reject(bad);}
  {auto bad=base;bad.files[0].path="nested";reject(bad);}
  {auto bad=base;bad.directories.push_back("NESTED");reject(bad);}
  {auto bad=base;bad.files[0].path=u8"ä.txt";bad.files[1].path=u8"Ä.TXT";reject(bad);}
  {auto bad=base;bad.files.clear();reject(bad);}
  {auto bad=base;bad.files.resize(129);reject(bad);}
  {auto bad=base;bad.directories.resize(129);reject(bad);}
  {auto bad=base;bad.files[0].sha=std::string(64,'A');reject(bad);}
  {auto bad=base;bad.files[0].size=33554433;bad.size+=33554433;reject(bad);}
  {auto bad=base;bad.size=4;reject(bad);}
  for(const auto& replacement:{"123","{}","null","true"}){
    auto bad=json;const auto at=bad.find("\"selected\"");bad.replace(at,10,replacement);assert(!xb_upload_tree_parse(bad,parsed));validations++;
  }
  {auto bad=json;const auto at=bad.find("\"size\":3");bad.replace(at,8,"\"size\":\"3\"");assert(!xb_upload_tree_parse(bad,parsed));validations++;}
  {auto bad=json;const auto at=bad.find("\"nested\"");bad.replace(at,8,"42");assert(!xb_upload_tree_parse(bad,parsed));validations++;}
  {auto many=base;many.directories.clear();std::string path;
    for(int i=0;i<16;i++){path+=(path.empty()?"":"/")+std::string("d");many.directories.push_back(path);}
    many.files={{path+"/a",std::string(64,'a'),0}};many.size=0;assert(xb_upload_tree_valid(many));
    many.directories.push_back(path+"/deeper");reject(many);
  }
  {xb_upload_tree edge;edge.name="edge";for(int i=0;i<128;i++)edge.files.push_back({"f"+std::to_string(i),std::string(64,'a'),0});
    assert(xb_upload_tree_valid(edge));edge.files.push_back({"overflow",std::string(64,'a'),0});reject(edge);
  }
  {xb_upload_tree dense;dense.name="dense";std::string prefix;
    for(char c:{'a','b','c'}){prefix+=(prefix.empty()?"":"/")+std::string(240,c);dense.directories.push_back(prefix);}
    for(int i=0;i<125;i++)dense.directories.push_back(prefix+"/"+std::string(230,'d')+std::to_string(i));
    for(int i=0;i<128;i++)dense.files.push_back({prefix+"/"+std::string(235,'f')+std::to_string(i),std::string(64,'a'),0});
    assert(xb_upload_tree_valid(dense));const auto large=xb_upload_tree_json(dense);assert(large.size()>245760);
    assert(!xb_upload_tree_parse(large,parsed));validations++;
  }
}
static void make_file(const std::wstring& path,const char* bytes,DWORD size){
  HANDLE file=CreateFileW(path.c_str(),GENERIC_READ|GENERIC_WRITE,FILE_SHARE_READ,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,nullptr);
  assert(file!=INVALID_HANDLE_VALUE);DWORD written=0;assert(WriteFile(file,bytes,size,&written,nullptr)&&written==size);assert(CloseHandle(file));
}
static void matching_checks(){
  xb_upload_tree tree;tree.name=u8"selected-中文";tree.directories={"a","b"};
  tree.files={{"a/same.txt",std::string(64,'a'),3},{"b/same.txt",std::string(64,'b'),0}};tree.size=3;
  const auto first="{\"name\":\"same.txt\",\"path\":"+xb_quote(tree.name+"/a/same.txt")+",\"size\":3}";
  const auto second="{\"name\":\"same.txt\",\"path\":"+xb_quote(tree.name+"/b/same.txt")+",\"size\":0}";
  assert(xb_upload_tree_matches(tree,"["+first+","+second+"]"));
  assert(xb_upload_tree_matches(tree,"["+second+","+first+"]"));
  for(const auto& files:std::vector<std::string>{"[]","["+first+"]","["+first+","+first+"]","["+first+","+second+","+first+"]"})assert(!xb_upload_tree_matches(tree,files));
  for(const auto& path:{"a/same.txt",u8"selected-中文/A/same.txt",u8"selected-中文/b/same.txt",u8"selected-中文/a/SAME.txt",u8"selected-中文\\a\\same.txt"}){
    const auto wrong="{\"name\":\"same.txt\",\"path\":"+xb_quote(path)+",\"size\":3}";
    assert(!xb_upload_tree_matches(tree,"["+wrong+","+second+"]"));
  }
  for(const auto& value:{"4","\"3\"","-1","null"}){
    const auto wrong="{\"name\":\"same.txt\",\"path\":"+xb_quote(tree.name+"/a/same.txt")+",\"size\":"+value+"}";
    assert(!xb_upload_tree_matches(tree,"["+wrong+","+second+"]"));
  }
  const auto wrongName="{\"name\":\"other.txt\",\"path\":"+xb_quote(tree.name+"/a/same.txt")+",\"size\":3}";
  assert(!xb_upload_tree_matches(tree,"["+wrongName+","+second+"]"));
}
int wmain(int argc,wchar_t** argv){
  assert(argc==2);validation_checks();matching_checks();const auto base=xb_upload_cache_path(argv[1]);assert(!base.empty());
  assert(CreateDirectoryW(base.c_str(),nullptr));make_file(base+L"\\.lease","",0);
  xb_upload_tree tree;tree.name="chosen";tree.directories={"nested","nested/empty"};
  tree.files={{"root.bin",std::string(64,'a'),0},{"nested/a.txt",std::string(64,'b'),3}};tree.size=3;assert(xb_upload_tree_valid(tree));
  assert(CreateDirectoryW((base+L"\\tree").c_str(),nullptr));xb_upload_tree_inventory inventory;
  assert(!xb_upload_tree_inspect(base,tree,inventory,true));assert(xb_upload_tree_cleanup(base,tree));
  for(const auto& path:xb_upload_tree_directories(tree))assert(CreateDirectoryW(xb_upload_tree_native(base,path).c_str(),nullptr));
  const auto first=xb_upload_tree_native(base,"tree/chosen/root.bin"),second=xb_upload_tree_native(base,"tree/chosen/nested/a.txt");
  make_file(first,"",0);make_file(second,"abc",3);
  assert(xb_upload_tree_inspect(base,tree,inventory,true)&&inventory.files.size()==2&&inventory.directories.size()==4);
  const auto unknown=xb_upload_tree_native(base,"tree/chosen/nested/unexpected.txt");make_file(unknown,"keep",4);
  assert(!xb_upload_tree_cleanup(base,tree));assert(GetFileAttributesW(first.c_str())!=INVALID_FILE_ATTRIBUTES);
  assert(GetFileAttributesW(second.c_str())!=INVALID_FILE_ATTRIBUTES&&GetFileAttributesW(unknown.c_str())!=INVALID_FILE_ATTRIBUTES);
  assert(DeleteFileW(unknown.c_str()));
  const auto unknownDirectory=xb_upload_tree_native(base,"tree/chosen/unknown-directory");assert(CreateDirectoryW(unknownDirectory.c_str(),nullptr));
  assert(!xb_upload_tree_cleanup(base,tree)&&GetFileAttributesW(first.c_str())!=INVALID_FILE_ATTRIBUTES);
  assert(RemoveDirectoryW(unknownDirectory.c_str()));
  const auto renamed=xb_upload_tree_native(base,"tree/chosen/nested/renamed.txt");assert(MoveFileExW(second.c_str(),renamed.c_str(),0));
  assert(!xb_upload_tree_cleanup(base,tree)&&GetFileAttributesW(first.c_str())!=INVALID_FILE_ATTRIBUTES);
  assert(MoveFileExW(renamed.c_str(),second.c_str(),0));
  unsigned failures=0;bool completed=false;
  for(long fault=0;fault<2048;fault++){
    DWORD before=0,after=0;assert(GetProcessHandleCount(GetCurrentProcess(),&before));const auto heap=liveAllocations;
    failAt=fault;attempts=0;triggered=false;
    try{completed=xb_upload_tree_cleanup(base,tree);}catch(const std::bad_alloc&){assert(triggered);failures++;}
    failAt=-1;assert(GetProcessHandleCount(GetCurrentProcess(),&after)&&before==after);assert(liveAllocations==heap);
    if(completed)break;
    assert(GetFileAttributesW(first.c_str())!=INVALID_FILE_ATTRIBUTES&&GetFileAttributesW(second.c_str())!=INVALID_FILE_ATTRIBUTES);
  }
  assert(completed&&failures>30&&validations>40);
  assert(GetFileAttributesW((base+L"\\tree").c_str())==INVALID_FILE_ATTRIBUTES);
  assert(DeleteFileW((base+L"\\.lease").c_str())&&RemoveDirectoryW(base.c_str()));
  std::printf("%u adversarial validations and %u allocation failure positions preserved complete tree ownership and closed all enumeration handles\n",validations,failures);
  return 0;
}
