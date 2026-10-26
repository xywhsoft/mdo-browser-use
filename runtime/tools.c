/* Managed Agent tools use exactly the same public service contracts. They run
 * on mdo's existing tool worker, with its live permissions and cancellation. */
static mdo_result BuToolWrite(const mdo_sdk_context_v1* C,const xvalue* Value,bool Success,
                               mdo_result Code,mdo_result_writer_v1* Writer,char* Error,size_t Capacity,bool Image){
    if(Image&&Success&&C->Api->Values->Get(Value,"image"))return BuToolImage(C,Value,Writer,Error,Capacity);
    char* Json=C->Api->Values->Json(Value);
    bool Written=Json&&Writer->WriteText(Writer->Context,Json)&&Writer->SetSuccess(Writer->Context,Success);
    C->Api->Values->Free(Json);
    if(!Written){snprintf(Error,Capacity,"browser_result_limit: Cannot publish the bounded browser result");return MDO_RESULT_LIMIT;}
    return Code;
}
static mdo_result BuToolExecute(void* User,const mdo_tool_context_v1* Tool,const char* Json,
                                 mdo_result_writer_v1* Writer,char* Error,size_t Capacity){
    const BuToolSpec* Spec=User;const mdo_sdk_context_v1* Original=Tool?Tool->Application:NULL;
    if(!Original||!Original->Api){snprintf(Error,Capacity,"browser_sdk_missing: Managed application context is required");return MDO_RESULT_ERROR;}
    mdo_sdk_context_v1 Bound=*Original;const mdo_sdk_context_v1* C=&Bound;const mdo_values_v1* V=C->Api->Values;
    xvalue* Params=V->Parse(Json),*Schema=V->Parse(Spec->Input),*Value=NULL,*Args=NULL;
    bool Valid=Params&&Schema&&BuValidate(C,Schema,Params,0);V->Release(Schema);
    mdo_error_v1 E={0};mdo_result Code=MDO_RESULT_ERROR;char Receipt[65]={0};
    if(!Valid){BuFail(&E,"arguments_invalid","Tool arguments do not match the Browser Use contract",422);goto failed;}
    uint64_t Timeout=10000;V->Unsigned(V->Get(Params,"timeout_ms"),&Timeout);
    uint64_t Deadline=C->Api->MonotonicMicroseconds()+(Timeout+5000)*1000;
    if(!Bound.Deadline||Bound.Deadline>Deadline)Bound.Deadline=Deadline;
    Code=BuDispatch((void*)&BuServices[Spec->Service],C,Params,&Value,&E);
    V->Release(Params);Params=NULL;if(Code!=MDO_RESULT_OK)goto failed;
    if(!BuEqual(BuServices[Spec->Service].Id,"browseruse.tabs")){
        const char* Id=BuText(C,Value,"id");
        if(!Id||strlen(Id)>=sizeof(Receipt)){BuFail(&E,"receipt_invalid","The browser did not return an execution receipt",500);goto failed;}
        snprintf(Receipt,sizeof(Receipt),"%s",Id);V->Release(Value);Value=NULL;
        Args=V->Object();BuPut(C,Args,"id",Receipt);
        for(;;){
            if(C->Api->Cancelled(C)){
                bool Timed=C->Api->MonotonicMicroseconds()>=Bound.Deadline;
                Code=Timed?MDO_RESULT_TIMEOUT:MDO_RESULT_CANCELLED;
                BuFail(&E,Timed?"timed_out":"cancelled","Browser tool stopped; inspect any already dispatched effects before retrying",Timed?504:499);goto failed;
            }
            Code=BuResult(NULL,C,Args,&Value,&E);if(Code!=MDO_RESULT_OK)goto failed;
            const char* State=BuText(C,Value,"state");
            if(!BuEqual(State,"pending"))break;
            V->Release(Value);Value=NULL;xvalue* Pause=V->Parse("{\"milliseconds\":50}"),*Unused=NULL;
            Code=mdoCall(C,"runtime.sleep",Pause,&Unused,&E);V->Release(Pause);V->Release(Unused);
            if(Code!=MDO_RESULT_OK)goto failed;
        }
        bool Success=BuEqual(BuText(C,Value,"state"),"succeeded");
        xvalue* Outcome=V->Clone(V->Get(Value,"result"));
        if(!Success){
            const xvalue* Detail=V->Get(Value,"error");
            const char* Reason=BuText(C,Detail,"code"),*Message=BuText(C,Detail,"message");
            if(!Reason)Reason=BuText(C,Value,"state");
            if(!Message)Message=V->Text(Detail);
            BuFail(&E,Reason?Reason:"browser_failed",Message?Message:"Browser operation failed",409);
            Code=BuEqual(Reason,"cancelled")?MDO_RESULT_CANCELLED:BuEqual(Reason,"timed_out")?MDO_RESULT_TIMEOUT:MDO_RESULT_ERROR;
        }
        xvalue* Release=V->Bool(true),*Unused=NULL;V->Set(Args,"release",Release);V->Release(Release);
        mdo_error_v1 Cleanup={0};(void)BuResult(NULL,C,Args,&Unused,&Cleanup);V->Release(Unused);
        V->Release(Args);Args=NULL;V->Release(Value);Value=Outcome;
        if(!Success){if(!Value)goto failed;snprintf(Error,Capacity,"%s: %s",E.Code,E.Message);}
        Code=BuToolWrite(C,Value,Success,Success?MDO_RESULT_OK:Code,Writer,Error,Capacity,BuEqual(Spec->Id,"browser_screenshot"));V->Release(Value);return Code;
    }
    Code=BuToolWrite(C,Value,true,MDO_RESULT_OK,Writer,Error,Capacity,false);V->Release(Value);return Code;
failed:
    V->Release(Params);V->Release(Args);V->Release(Value);
    Value=V->Object();xvalue* Detail=V->Object(),*No=V->Bool(false);
    V->Set(Value,"ok",No);V->Release(No);BuPut(C,Detail,"code",E.Code[0]?E.Code:"browser_failed");
    BuPut(C,Detail,"message",E.Message[0]?E.Message:"Browser tool failed");
    xvalue* Possible=V->Bool(Receipt[0]!=0||E.MayHaveCompleted);V->Set(Detail,"effect_may_have_occurred",Possible);V->Release(Possible);
    V->Set(Value,"error",Detail);V->Release(Detail);if(Receipt[0])BuPut(C,Value,"receipt",Receipt);
    snprintf(Error,Capacity,"%s: %s",E.Code,E.Message);
    if(Code==MDO_RESULT_OK)Code=MDO_RESULT_ERROR;
    Code=BuToolWrite(C,Value,false,Code,Writer,Error,Capacity,false);V->Release(Value);return Code;
}
