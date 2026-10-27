            const auto activate=[host,view,id,browser,url,popup](bool safe,const std::string& cause){
              auto reject=[&](const std::string& message){
                const auto current=host->views.find(browser);
                if(host->alive&&!view->closed&&current!=host->views.end()&&current->second==view){
                  view->closed=true;xb_download_close_view(host,view);xb_chooser_close(view);xb_cancel_popups(view);
                  if(view->opening)view->opening->complete();view->opening.reset();
                  if(view->controller)view->controller->Close();if(view->window)ShowWindow(view->window,SW_HIDE);host->views.erase(browser);
                }
                host->reply(id,"",message);
              };
              if(!host->alive||view->closed||!host->views.count(browser)||host->views.at(browser)!=view||(popup&&popup->completed)){reject("Browser creation was cancelled before navigation");return;}
              if(!safe){reject(cause.empty()?"Cannot start this browser without download interception":cause);return;}
              view->creating=false;
              const auto flags=std::string(",\"downloads_intercepted\":")+(view->downloads&&view->downloads->armed?"true":"false")+",\"manual\":"+(view->manual?"true":"false");
              if(popup){
                view->loading=url!="about:blank";const auto adopted=popup->complete(view->core);view->opening.reset();
                if(FAILED(adopted)){reject("Cannot adopt the requested popup");return;}
                host->reply(id,"{\"created\":true,\"popup\":true"+flags+"}");return;
              }
              const auto navigationResult=view->core->Navigate(webview::detail::widen_string(url).c_str());
              if(FAILED(navigationResult)){view->initialNavigationPending=false;view->loading=false;view->error="Browser rejected the initial navigation";view->errorCode=static_cast<int>(navigationResult);}
              host->reply(id,"{\"created\":true"+flags+"}");
            };
            if(interceptDownloads&&!view->manual)xb_download_arm(host,view,activate);else activate(true,{});
            return S_OK;
