static mdo_result BuDialog(void* User,const mdo_sdk_context_v1* C,const xvalue* P,xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;
    if(BuEqual(BuText(C,P,"action"),"respond")&&(!V->Get(P,"dialog")||!V->Get(P,"accept")))
        return BuFail(E,"dialog_response_required","Respond with the pending dialog identity and an explicit boolean accept value",422);
    const char* Fields[]={"action","dialog","accept","text","timeout_ms"};
    return BuQueue(C,P,"dialog",Fields,5,Result,E);
}
