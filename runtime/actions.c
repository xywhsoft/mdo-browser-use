static mdo_result BuAct(void* User,const mdo_sdk_context_v1* C,const xvalue* P,
                        xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;const char* Op=BuText(C,P,"op");
    const xvalue* Target=V->Get(P,"target");
    if(!BuEqual(Op,"scroll")&&(!Target||!V->Count(Target)))return BuFail(E,"target_required","Select one observed target before acting",422);
    if(Target){
        bool Selector=false;const char* Keys[]={"role","name","label","placeholder","text","testid","css","ref"};
        for(size_t I=0;I<8;I++)if(V->Get(Target,Keys[I]))Selector=true;
        if(!Selector)return BuFail(E,"target_required","A frame alone is not an element target",422);
        if(V->Get(Target,"ref")&&V->Count(Target)>(V->Get(Target,"frame")?2:1))return BuFail(E,"target_invalid","Use either a reference or semantic selectors",422);
    }
    if((BuEqual(Op,"fill")||BuEqual(Op,"type"))&&!V->Get(P,"text"))return BuFail(E,"text_required","Text is required for fill or type",422);
    if(BuEqual(Op,"key")&&!V->Get(P,"key"))return BuFail(E,"key_required","A supported key is required",422);
    if(BuEqual(Op,"check")&&!V->Get(P,"checked"))return BuFail(E,"checked_required","Specify the desired checked state",422);
    const char* Fields[]={"op","document","target","text","key","checked","dx","dy","timeout_ms"};
    return BuQueue(C,P,"act",Fields,9,Result,E);
}
