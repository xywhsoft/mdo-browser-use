/* Files are read only after the displaying client confirms native capability.
 * This C boundary validates shape/scope, then freezes the original SDK context. */
static mdo_result BuUpload(void* User,const mdo_sdk_context_v1* C,const xvalue* P,xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;const char* Action=BuText(C,P,"action");
    if(BuEqual(Action,"set")||BuEqual(Action,"clear")){
        const xvalue* Target=V->Get(P,"target");bool Precise=false;
        const char* Keys[]={"ref","role","name","label","placeholder","text","testid","css"};
        for(size_t I=0;I<8;I++)if(V->Get(Target,Keys[I]))Precise=true;
        if(!V->Get(P,"document")||!Precise||BuEqual(Action,"set")&&!V->Count(V->Get(P,"files")))return BuFail(E,"upload_target_required","Choose a fresh document, exact file input and files when setting a selection",422);
        if(BuEqual(Action,"clear")&&V->Get(P,"files"))return BuFail(E,"arguments_invalid","Clearing a selection does not take source files",422);
        if(V->Get(Target,"ref")&&V->Count(Target)>(V->Get(Target,"frame")?2:1))return BuFail(E,"target_invalid","Use a reference or semantic selectors",422);
        if(V->Get(P,"ticket")||V->Get(P,"confirmed"))return BuFail(E,"arguments_invalid","Changing a file selection cannot reuse a native transfer ticket or release confirmation",422);
    }else{
        if(!V->Get(P,"ticket"))return BuFail(E,"upload_ticket_required","Select the exact transfer ticket returned by file assignment",422);
        if(V->Get(P,"document")||V->Get(P,"target")||V->Get(P,"files"))return BuFail(E,"arguments_invalid","Transfer status/release takes its ticket rather than a new file selection",422);
        bool Confirmed=false;V->Boolean(V->Get(P,"confirmed"),&Confirmed);
        if(BuEqual(Action,"release")&&!Confirmed)return BuFail(E,"upload_confirmation_required","Confirm the website has consumed its files before releasing retained bytes",422);
        if(BuEqual(Action,"status")&&V->Get(P,"confirmed"))return BuFail(E,"arguments_invalid","Status does not consume or release files",422);
    }
    const char* Fields[]={"action","document","target","files","ticket","confirmed","timeout_ms"};
    return BuQueue(C,P,"upload",Fields,7,Result,E);
}
