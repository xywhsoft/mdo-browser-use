/* The existing SDK admission journal provides durable at-most-once identity.
 * This adapter never recreates an expired/interrupted operation to recover it. */
static bool BuOperationId(const char* Id){
    if(!Id||strlen(Id)!=36||strncmp(Id,"sdk-",4))return false;
    for(size_t I=4;I<36;I++)if(!((Id[I]>='0'&&Id[I]<='9')||(Id[I]>='a'&&Id[I]<='f')))return false;
    return true;
}
static const BuService* BuOperationTarget(const char* Method){
    if(!Method||(!BuEqual(Method,"browseruse.act")&&!BuEqual(Method,"browseruse.navigate")&&!BuEqual(Method,"browseruse.tabs")&&!BuEqual(Method,"browseruse.dialog")))return NULL;
    for(size_t I=0;I<sizeof(BuServices)/sizeof(*BuServices);I++)if(BuEqual(Method,BuServices[I].Id))return &BuServices[I];
    return NULL;
}
static mdo_result BuOperationState(const mdo_sdk_context_v1* C,const char* Id,const char* State,
                                     const char* Code,const char* Message,xvalue** Result){
    const mdo_values_v1* V=C->Api->Values;*Result=V->Object();
    BuPut(C,*Result,"operation",Id);BuPut(C,*Result,"state",State);
    if(Code){xvalue* Error=V->Object();BuPut(C,Error,"code",Code);BuPut(C,Error,"message",Message);
        xvalue* Possible=V->Bool(!BuEqual(State,"failed"));V->Set(Error,"effect_may_have_occurred",Possible);V->Release(Possible);
        V->Set(*Result,"error",Error);V->Release(Error);}
    return *Result?MDO_RESULT_OK:MDO_RESULT_ERROR;
}
static mdo_result BuOperation(void* User,const mdo_sdk_context_v1* C,const xvalue* P,xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;
    const char* Id=BuText(C,P,"operation"),*Action=BuText(C,P,"action");
    if(!BuOperationId(Id))return BuFail(E,"operation_invalid","Use sdk- followed by 32 lowercase hexadecimal digits",422);
    xvalue* Args=V->Object(),*Info=NULL,*Value=NULL;BuPut(C,Args,"id",Id);
    mdo_result Code;
    if(BuEqual(Action,"submit")){
        const char* Method=BuText(C,P,"method");const BuService* Service=BuOperationTarget(Method);
        if(!Service){V->Release(Args);return BuFail(E,"method_invalid","Only supported Browser Use operations can be submitted",422);}
        const xvalue* Input=V->Get(P,"arguments");xvalue* Schema=V->Parse(Service->Input);
        bool Valid=Schema&&BuValidate(C,Schema,Input,0);V->Release(Schema);
        if(!Valid){V->Release(Args);return BuFail(E,"arguments_invalid","Submitted arguments do not match the shared service schema",422);}
        BuPut(C,Args,"method",Method);V->Set(Args,"params",Input);
        Code=mdoCall(C,"operation.start",Args,&Info,E);
    }else Code=mdoCall(C,"operation.get",Args,&Info,E);
    if(Code!=MDO_RESULT_OK){V->Release(Args);V->Release(Info);return Code;}
    V->Release(Args);Args=V->Object();BuPut(C,Args,"id",Id);
    const char* Method=BuText(C,Info,"method");
    if(!BuOperationTarget(Method)){V->Release(Args);V->Release(Info);return BuFail(E,"method_invalid","This receipt is not a Browser Use operation",409);}
    bool Terminal=false;V->Boolean(V->Get(Info,"terminal"),&Terminal);
    if(!Terminal){
        if(BuEqual(Action,"cancel")){xvalue* Unused=NULL;Code=mdoCall(C,"operation.cancel",Args,&Unused,E);V->Release(Unused);if(Code!=MDO_RESULT_OK)goto done;}
        if(BuEqual(Action,"release")){Code=BuFail(E,"operation_pending","Cancel and wait for completion before releasing",409);goto done;}
        Code=BuOperationState(C,Id,"pending",NULL,NULL,Result);goto done;
    }
    if(!BuEqual(BuText(C,Info,"state"),"succeeded")){
        const xvalue* Error=V->Get(Info,"error");
        bool Possible=true;bool Known=V->Boolean(V->Get(Error,"may_have_completed"),&Possible);
        const char* State=BuEqual(BuText(C,Info,"state"),"cancelled")?"cancelled":
            BuEqual(BuText(C,Info,"state"),"failed")&&Known&&!Possible?"failed":"uncertain";
        Code=BuOperationState(C,Id,State,
            BuText(C,Error,"code")?BuText(C,Error,"code"):"operation_interrupted",
            BuText(C,Error,"message")?BuText(C,Error,"message"):"The operation did not retain a confirmed outcome; inspect its effects",Result);
        goto release;
    }
    bool Available=false;V->Boolean(V->Get(Info,"result_available"),&Available);
    if(!Available){Code=BuOperationState(C,Id,"uncertain","result_expired","The admission remains, but its result is unavailable. Inspect the tab; this identity will never be replayed",Result);goto release;}
    Code=mdoCall(C,"operation.result",Args,&Value,E);if(Code!=MDO_RESULT_OK)goto done;
    if(BuEqual(Method,"browseruse.tabs")){
        Code=BuOperationState(C,Id,"succeeded",NULL,NULL,Result);V->Set(*Result,"result",Value);goto release;
    }
    const char* Receipt=BuText(C,Value,"id");
    if(!Receipt||V->Get(Value,"encoding")){Code=BuOperationState(C,Id,"uncertain","receipt_unavailable","The browser receipt is unavailable; inspect the tab before another effect",Result);goto release;}
    xvalue* Query=V->Object();BuPut(C,Query,"id",Receipt);
    if(BuEqual(Action,"cancel")){xvalue* Yes=V->Bool(true);V->Set(Query,"cancel",Yes);V->Release(Yes);}
    Code=BuResult(NULL,C,Query,Result,E);
    if(Code==MDO_RESULT_OK){BuPut(C,*Result,"operation",Id);
        if(BuEqual(Action,"release")){xvalue* Yes=V->Bool(true),*Unused=NULL;V->Set(Query,"release",Yes);V->Release(Yes);
            Code=BuResult(NULL,C,Query,&Unused,E);V->Release(Unused);}}
    V->Release(Query);if(Code!=MDO_RESULT_OK)goto done;
release:
    if(BuEqual(Action,"release")){xvalue* Unused=NULL;Code=mdoCall(C,"operation.release",Args,&Unused,E);V->Release(Unused);}
done:
    V->Release(Value);V->Release(Info);V->Release(Args);return Code;
}
