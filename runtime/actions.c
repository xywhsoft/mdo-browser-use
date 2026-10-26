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
    const xvalue* Choices=V->Get(P,"choices");
    if(BuEqual(Op,"select")){
        if(!Choices)return BuFail(E,"choices_required","Specify the exact select choices; an empty array clears a multi-select",422);
        for(size_t I=0;I<V->Count(Choices);I++)if(V->Count(V->At(Choices,I))!=1)
            return BuFail(E,"choice_invalid","Each choice requires exactly one label, value or index",422);
    }else if(Choices)return BuFail(E,"arguments_invalid","Choices apply only to the select action",422);
    const xvalue* Destination=V->Get(P,"destination");
    if(BuEqual(Op,"drag")){
        if(!Destination||!V->Count(Destination))return BuFail(E,"destination_required","Select an exact observed destination before dragging",422);
        bool Selector=false;const char* Keys[]={"role","name","label","placeholder","text","testid","css","ref"};
        for(size_t I=0;I<8;I++)if(V->Get(Destination,Keys[I]))Selector=true;
        if(!Selector)return BuFail(E,"destination_required","A frame alone is not a drop target",422);
        if(V->Get(Destination,"ref")&&V->Count(Destination)>(V->Get(Destination,"frame")?2:1))return BuFail(E,"target_invalid","Use a destination reference or semantic selectors",422);
    }else if(Destination)return BuFail(E,"arguments_invalid","Destination applies only to dragging",422);
    const char* Fields[]={"op","document","target","text","key","checked","dx","dy","timeout_ms","choices","destination"};
    return BuQueue(C,P,"act",Fields,11,Result,E);
}
