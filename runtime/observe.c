static mdo_result BuQueue(const mdo_sdk_context_v1* C,const xvalue* P,const char* Command,
                          const char** Fields,size_t FieldCount,xvalue** Result,mdo_error_v1* E) {
    const mdo_values_v1* V=C->Api->Values;
    xvalue* Get=V->Object();BuPut(C,Get,"action","get");
    BuPut(C,Get,"browser",BuText(C,P,"browser"));
    xvalue* Browser=NULL;mdo_result Code=BuTabs(NULL,C,Get,&Browser,E);V->Release(Get);
    if(Code!=MDO_RESULT_OK)return Code;
    xvalue* Args=V->Object();
    if(!Args){V->Release(Browser);return BuFail(E,"allocation_failed","Cannot queue observation",500);}
    BuPut(C,Args,"id",BuText(C,P,"browser"));BuPut(C,Args,"command",Command);
    for(size_t I=0;I<FieldCount;I++){const xvalue* Field=V->Get(P,Fields[I]);if(Field)V->Set(Args,Fields[I],Field);}
    uint64_t Timeout=10000;V->Unsigned(V->Get(P,"timeout_ms"),&Timeout);
    mdo_sdk_context_v1 Bound=*C;uint64_t Deadline=C->Api->MonotonicMicroseconds()+(Timeout+5000)*1000;
    if(!Bound.Deadline||Bound.Deadline>Deadline)Bound.Deadline=Deadline;
    xvalue* Receipt=NULL;Code=mdoCall(&Bound,"browser.command",Args,&Receipt,E);
    if(Code==MDO_RESULT_OK){
        *Result=V->Object();bool Ok=*Result&&BuPut(C,*Result,"id",BuText(C,Receipt,"id"))&&BuPut(C,*Result,"state",BuText(C,Receipt,"state"));
        if(!Ok){V->Release(*Result);*Result=NULL;Code=BuFail(E,"allocation_failed","Cannot publish the queued receipt",500);if(E)E->MayHaveCompleted=true;}
    }
    V->Release(Receipt);V->Release(Args);V->Release(Browser);return Code;
}
static mdo_result BuObserve(void* User,const mdo_sdk_context_v1* C,const xvalue* P,
                            xvalue** Result,mdo_error_v1* E) {
    (void)User;const char* Fields[]={"mode","limit","max_bytes","timeout_ms"};
    return BuQueue(C,P,"observe",Fields,4,Result,E);
}

static mdo_result BuResult(void* User,const mdo_sdk_context_v1* C,const xvalue* P,
                           xvalue** Result,mdo_error_v1* E) {
    (void)User;const mdo_values_v1* V=C->Api->Values;
    xvalue* Args=V->Object();BuPut(C,Args,"id",BuText(C,P,"id"));
    xvalue* Receipt=NULL;mdo_result Code=mdoCall(C,"ui.result",Args,&Receipt,E);
    if(Code!=MDO_RESULT_OK){V->Release(Args);return Code;}
    const xvalue* Scope=V->Get(Receipt,"context");
    const char* Action=BuText(C,Receipt,"action");
    const char* Project=BuText(C,Scope,"project");
    const char* Command=BuText(C,V->Get(Receipt,"params"),"command");
    bool Owned=BuEqual(Action,"workbench.browser")&&(BuEqual(Command,"observe")||BuEqual(Command,"act")||BuEqual(Command,"navigate_to")||BuEqual(Command,"wait_for")||BuEqual(Command,"dialog"))&&
        BuEqual(C->ExtensionId,BuText(C,Scope,"extension"))&&
        BuEqual(C->DeviceId,BuText(C,Scope,"device"))&&
        BuEqual(C->ProjectId&&*C->ProjectId?C->ProjectId:"default",Project&&*Project?Project:"default")&&
        BuEqual(C->SessionId,BuText(C,Scope,"session"));
    if(!Owned){V->Release(Args);V->Release(Receipt);return BuFail(E,"context_mismatch","Receipt belongs to another extension or task",409);}
    bool Release=false,Cancel=false;
    V->Boolean(V->Get(P,"release"),&Release);
    V->Boolean(V->Get(P,"cancel"),&Cancel);
    if(Release&&Cancel){V->Release(Args);V->Release(Receipt);return BuFail(E,"arguments_invalid","Choose cancel or release separately",422);}
    if(Cancel){xvalue* Cancelled=NULL;Code=mdoCall(C,"ui.cancel",Args,&Cancelled,E);
        V->Release(Receipt);Receipt=Cancelled;if(Code!=MDO_RESULT_OK){V->Release(Args);return Code;}}
    const char* State=BuText(C,Receipt,"state");
    if(Release){Code=mdoCall(C,"ui.release",Args,Result,E);V->Release(Args);V->Release(Receipt);return Code;}
    *Result=V->Object();BuPut(C,*Result,"id",BuText(C,Receipt,"id"));
    BuPut(C,*Result,"state",State);
    const xvalue* Value=V->Get(Receipt,"result");bool Ok=true;
    if(Value&&V->Boolean(V->Get(Value,"ok"),&Ok)&&!Ok)BuPut(C,*Result,"state","failed");
    bool Ephemeral=false;
    if(Value&&V->Boolean(V->Get(Value,"ephemeral"),&Ephemeral)&&Ephemeral){
        BuPut(C,*Result,"state","failed");xvalue* Missing=V->Object();
        BuPut(C,Missing,"code","result_expired");BuPut(C,Missing,"message","The page result was not retained; request a fresh read-only observation");
        V->Set(*Result,"error",Missing);V->Release(Missing);
    }
    if(Value)V->Set(*Result,"result",Value);
    const xvalue* Error=Value?V->Get(Value,"error"):NULL;
    if(Error)V->Set(*Result,"error",Error);
    else if(V->Get(Receipt,"error"))V->Set(*Result,"error",V->Get(Receipt,"error"));
    V->Release(Args);V->Release(Receipt);return MDO_RESULT_OK;
}
