/* These identities refer to existing browser responses, never copied URLs. */
static mdo_result BuDownload(void* User,const mdo_sdk_context_v1* C,const xvalue* P,xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;const char* Action=BuText(C,P,"action");
    if(BuEqual(Action,"list")){
        if(V->Get(P,"download"))return BuFail(E,"arguments_invalid","Listing downloads does not select an individual response",422);
    }else if(!V->Get(P,"download"))return BuFail(E,"download_required","Select the exact intercepted download identity",422);
    if(!BuEqual(Action,"accept")&&(V->Get(P,"max_bytes")||V->Get(P,"transfer_timeout_ms")))
        return BuFail(E,"arguments_invalid","Byte and transfer-time budgets belong only to download acceptance",422);
    const char* Fields[]={"action","download","download_max_bytes","transfer_timeout_ms","timeout_ms"};
    xvalue* Args=V->Clone(P);if(!Args)return BuFail(E,"allocation_failed","Cannot queue download metadata",500);
    if(V->Get(P,"max_bytes")&&!V->Set(Args,"download_max_bytes",V->Get(P,"max_bytes"))){V->Release(Args);return BuFail(E,"allocation_failed","Cannot retain the explicit download byte budget",500);}
    mdo_result Code=BuQueue(C,Args,"download",Fields,5,Result,E);V->Release(Args);return Code;
}
