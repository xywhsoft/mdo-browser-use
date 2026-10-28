"""Compile the production creation receipt/deferral ownership helpers."""
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class NativeCreationTests(unittest.TestCase):
    def test_close_settles_original_once_without_waiting_for_provider(self):
        directory=dev.ENV/'qa/native-creation';directory.mkdir(parents=True,exist_ok=True)
        source=directory/'receipt.cpp';binary=directory/'receipt.exe'
        helper=(dev.ROOT/'host/native-creation.inc.hpp').read_text(encoding='utf-8')
        prefix=r'''
#include <memory>
#include <string>
#include <functional>
#include <vector>
#include <stdexcept>
#include <utility>
#include <cassert>
struct Popup{int completed=0;std::function<void()> callback;void complete(){completed++;if(callback)callback();}};
struct View{std::string creationRequest;std::shared_ptr<Popup> opening;};
struct Host{
  struct Reply{std::string id,result,error;};std::vector<Reply> replies;std::function<void()> callback;bool fail=false;
  void reply(const std::string& id,const std::string& result,const std::string& error){
    if(fail)throw std::runtime_error("Synthetic acknowledgement loss");
    replies.push_back({id,result,error});if(callback)callback();
  }
};
'''
        program=r'''
int main(){
 auto host=std::make_shared<Host>(),emptyHost=std::make_shared<Host>();auto original=std::make_shared<View>();original->creationRequest="original";
 auto popup=std::make_shared<Popup>();original->opening=popup;
 host->callback=[&]{assert(original->creationRequest.empty());assert(!xb_creation_settle(host,original,"late success"));};
 popup->callback=[&]{assert(!original->opening);assert(!xb_creation_settle(host,original,"late deferral callback"));};
 xb_creation_cancel(host,original);
 assert(host->replies.size()==1&&host->replies[0].id=="original"&&host->replies[0].result.empty());
 assert(host->replies[0].error=="Browser creation was cancelled"&&popup->completed==1&&!original->opening);
 xb_creation_cancel(host,original);assert(!xb_creation_settle(host,original,"late environment callback"));
 assert(!xb_creation_settle(host,original,"","late failure"));assert(host->replies.size()==1&&popup->completed==1);
 auto replacement=std::make_shared<View>();replacement->creationRequest="replacement";host->callback=nullptr;
 assert(!xb_creation_settle(host,original,"old controller success"));assert(replacement->creationRequest=="replacement");
 assert(xb_creation_settle(host,replacement,"created"));assert(!xb_creation_settle(host,replacement,"duplicate"));
 xb_creation_cancel(host,replacement);assert(host->replies.size()==2&&host->replies.back().id=="replacement"&&host->replies.back().result=="created");
 auto failed=std::make_shared<View>();failed->creationRequest="failed";
 assert(xb_creation_settle(host,failed,"","environment failed"));xb_creation_cancel(host,failed);assert(host->replies.size()==3);
 auto lost=std::make_shared<View>();lost->creationRequest="lost";auto release=std::make_shared<Popup>();lost->opening=release;emptyHost->fail=true;
 try{xb_creation_cancel(emptyHost,lost);assert(false);}catch(const std::runtime_error&){}
 assert(lost->creationRequest.empty()&&!lost->opening&&release->completed==1);
 emptyHost->fail=false;assert(!xb_creation_settle(emptyHost,lost,"replay"));assert(emptyHost->replies.empty());
 return 0;
}
'''
        source.write_text(prefix+helper+program,encoding='utf-8',newline='\n')
        build=subprocess.run(['E:/software/w64devkit/bin/g++.exe','-std=c++17','-O1','-Wall','-Wextra','-Werror',str(source),'-o',str(binary)],
            cwd=dev.ROOT,capture_output=True,text=True,timeout=45)
        self.assertEqual(build.returncode,0,build.stderr[-2400:])
        run=subprocess.run([str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=10)
        self.assertEqual(run.returncode,0,run.stderr[-1000:])


if __name__=='__main__':unittest.main()
