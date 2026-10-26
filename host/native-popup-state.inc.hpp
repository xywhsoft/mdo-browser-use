static void xb_popup_cleanup(HWND window);
struct xb_popup {
  ICoreWebView2NewWindowRequestedEventArgs* args=nullptr;
  ICoreWebView2Deferral* deferral=nullptr;
  std::string id,url;ULONGLONG expires=0;bool claimed=false,completed=false,userInitiated=false;
  HRESULT complete(ICoreWebView2* window=nullptr){
    if(completed||!args)return E_ABORT;completed=true;
    HRESULT status=window?args->put_NewWindow(window):S_OK;
    if(deferral){HRESULT end=deferral->Complete();if(SUCCEEDED(status))status=end;deferral->Release();deferral=nullptr;}
    args->Release();args=nullptr;return status;
  }
  ~xb_popup(){complete();}
};
