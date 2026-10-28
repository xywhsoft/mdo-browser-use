"""Compile the production creation receipt/deferral ownership helpers."""
from pathlib import Path
import json
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class NativeCreationTests(unittest.TestCase):
    def test_reservations_survive_cancellation_bound_late_providers_and_drain_once(self):
        directory=dev.ENV/'qa/native-creation';directory.mkdir(parents=True,exist_ok=True)
        source=directory/'lifetime.cpp';binary=directory/'lifetime.exe'
        prefix=r'''
#include <memory>
#include <string>
#include <functional>
#include <vector>
#include <utility>
#include <type_traits>
#include <cassert>
#include <iostream>
'''
        state=(dev.ROOT/'host/native-creation-state.inc.hpp').read_text(encoding='utf-8')
        declarations=r'''
struct Popup{int completed=0;void complete(){completed++;}};
struct View{xb_creation_state creation;bool closed=false;std::string creationRequest;std::shared_ptr<Popup> opening;};
struct Host{std::shared_ptr<xb_creation_budget> budget=std::make_shared<xb_creation_budget>();unsigned replies=0;
 void reply(const std::string&,const std::string&,const std::string&){replies++;}};
'''
        helper=(dev.ROOT/'host/native-creation.inc.hpp').read_text(encoding='utf-8')
        program=r'''
int main(){
 static_assert(!std::is_copy_constructible<xb_creation_state>::value,"Reservation accidentally copyable");
 static_assert(!std::is_move_constructible<xb_creation_state>::value,"Reservation accidentally movable");
 auto host=std::make_shared<Host>();std::vector<std::function<void()>> provider;
 for(unsigned i=0;i<xb_creation_budget::limit;i++){
   auto view=std::make_shared<View>();assert(view->creation.acquire(host->budget,10));
   view->creationRequest="original"+std::to_string(i);view->closed=true;view->creation.cancel();xb_creation_cancel(host,view);
   assert(view->creation.reserved()&&std::string(view->creation.name())=="cancelled");
   provider.push_back([host,view]{assert(!xb_creation_finish(host,view,100,"","late provider failure"));});
 }
 assert(host->replies==16&&host->budget->pending()==16);
 auto denied=std::make_shared<View>();assert(!denied->creation.acquire(host->budget,11));assert(!denied->creation.reserved());
 assert(host->budget->pending()==16&&host->replies==16);
 provider[0]();assert(host->budget->pending()==15);provider[0]();assert(host->budget->pending()==15&&host->replies==16);
 auto recovery=std::make_shared<View>();assert(recovery->creation.acquire(host->budget,20));assert(!recovery->creation.acquire(host->budget,21));
 assert(!recovery->creation.advance(xb_creation_state::phase::interception));
 assert(recovery->creation.advance(xb_creation_state::phase::controller));assert(!recovery->creation.advance(xb_creation_state::phase::controller));
 assert(recovery->creation.advance(xb_creation_state::phase::interception));
 assert(recovery->creation.elapsed(19)==0&&recovery->creation.elapsed(25)==5);
 recovery->creationRequest="recovery";assert(xb_creation_finish(host,recovery,40,"created"));
 assert(!recovery->creation.reserved()&&host->budget->pending()==15&&host->replies==17);
 assert(std::string(recovery->creation.name())=="ready"&&recovery->creation.elapsed(400)==20);
 assert(!recovery->creation.advance(xb_creation_state::phase::controller));
 assert(!xb_creation_finish(host,recovery,99,"duplicate"));assert(recovery->creation.elapsed(500)==20&&host->budget->pending()==15);
 provider.clear();assert(host->budget->pending()==0); // Dropped callbacks release their retained original views.
 {xb_creation_state failed;assert(failed.acquire(host->budget,50));failed.finish(false,false,60);assert(std::string(failed.name())=="failed");}
 {xb_creation_state unstarted;assert(!unstarted.acquire(nullptr,1));assert(unstarted.elapsed(10)==0&&std::string(unstarted.name())=="none");}
 assert(host->budget->pending()==0);
 std::weak_ptr<xb_creation_budget> weak;
 {auto budget=std::make_shared<xb_creation_budget>();weak=budget;auto original=std::make_shared<View>();
  assert(original->creation.acquire(budget,1));budget.reset();assert(!weak.expired());original.reset();assert(weak.expired());}
 {auto original=std::make_shared<View>();assert(original->creation.acquire(host->budget,1));original->creation.cancel();original->creation.finish(false,true,2);
  assert(std::string(original->creation.name())=="cancelled"&&!original->creation.reserved());}
 assert(host->budget->pending()==0);
 std::cout<<"{\"creating\":false"<<xb_creation_json(recovery->creation,999)<<"}";
 return 0;
}
'''
        source.write_text(prefix+state+declarations+helper+program,encoding='utf-8',newline='\n')
        build=subprocess.run(['E:/software/w64devkit/bin/g++.exe','-std=c++17','-O1','-Wall','-Wextra','-Werror',str(source),'-o',str(binary)],
            cwd=dev.ROOT,capture_output=True,text=True,timeout=45)
        self.assertEqual(build.returncode,0,build.stderr[-2400:])
        run=subprocess.run([str(binary)],cwd=dev.ROOT,capture_output=True,text=True,timeout=10)
        self.assertEqual(run.returncode,0,run.stderr[-1000:])
        self.assertEqual(json.loads(run.stdout),{'creating':False,'creation':{'phase':'ready','elapsed_ms':20}})

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
