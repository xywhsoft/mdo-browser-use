struct xb_input_sequence;
static void xb_dialog_cleanup(HWND window);
struct xb_dialog {
  ICoreWebView2ScriptDialogOpeningEventArgs* args=nullptr;
  ICoreWebView2Deferral* deferral=nullptr;
  std::string id;ULONGLONG expires=0;
  ~xb_dialog(){if(deferral){deferral->Complete();deferral->Release();}if(args)args->Release();}
};
