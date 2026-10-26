/* Called with SdkUi.Lock held. Cancellation stops future work; it cannot undo
 * a browser input which has already reached the renderer. */
static bool BuUiCancel(const mdo_sdk_context_v1* C,SdkUiAction* A,xvalue** R,mdo_error_v1* E){
    if(!A)return MdoApplicationError(E,"sdk_ui_receipt_expired","UI receipt expired; inspect effects before retrying","ui",410,true);
    const xvalue* Scope=SdkGet(A->Value,"context");
    const char* Project=SdkField(Scope,"project");
    if(strcmp(C->ExtensionId?C->ExtensionId:"",SdkField(Scope,"extension")?SdkField(Scope,"extension"):"")||
       strcmp(C->DeviceId?C->DeviceId:"",SdkField(Scope,"device")?SdkField(Scope,"device"):"")||
       strcmp(C->ProjectId&&*C->ProjectId?C->ProjectId:"default",Project&&*Project?Project:"default")||
       strcmp(C->SessionId?C->SessionId:"",SdkField(Scope,"session")?SdkField(Scope,"session"):""))
        return MdoApplicationError(E,"context_mismatch","Cannot cancel another task's UI action","ui",409,false);
    if(!A->Terminal){A->Terminal=true;SdkPut(A->Value,"state","cancelled");
        SdkPut(A->Value,"error","Cancelled; an input already dispatched may have completed. Inspect the page before retrying.");}
    *R=xrtValueClone(A->Value);return *R!=NULL;
}
