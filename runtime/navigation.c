static mdo_result BuNavigate(void* User,const mdo_sdk_context_v1* C,const xvalue* P,
                             xvalue** Result,mdo_error_v1* E){
    (void)User;
    if(BuEqual(BuText(C,P,"action"),"goto")&&(!BuText(C,P,"url")||!BuUrl(BuText(C,P,"url"))))
        return BuFail(E,"url_invalid","Navigate to an explicit HTTP(S) URL without credentials",422);
    const char* Fields[]={"action","url","timeout_ms"};
    return BuQueue(C,P,"navigate_to",Fields,3,Result,E);
}
static mdo_result BuWait(void* User,const mdo_sdk_context_v1* C,const xvalue* P,
                        xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;const char* Until=BuText(C,P,"until");
    if(!BuEqual(Until,"ready")&&!BuEqual(Until,"text")&&!V->Get(P,"target"))return BuFail(E,"target_required","This wait condition requires an element target",422);
    if(BuEqual(Until,"text")&&!V->Get(P,"text"))return BuFail(E,"text_required","Specify text to wait for",422);
    if(V->Get(V->Get(P,"target"),"ref")&&!V->Get(P,"document"))return BuFail(E,"document_required","A reference requires its observed document identity",422);
    const char* Fields[]={"until","target","document","text","timeout_ms"};
    return BuQueue(C,P,"wait_for",Fields,5,Result,E);
}
