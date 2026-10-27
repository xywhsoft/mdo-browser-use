/* Saving is a separate workspace-write operation. Never infer a missing legacy
 * tab root from the currently selected chat, and never refetch a response URL. */
static bool BuSavePath(const char* Path){
    if(!Path||!Path[0]||Path[0]=='/'||strlen(Path)>4096||Path[strlen(Path)-1]=='/')return false;
    for(size_t I=0;Path[I];){
        size_t Start=I;while(Path[I]&&Path[I]!='/'){
            unsigned char Ch=(unsigned char)Path[I];if(Ch<32||Ch==127||strchr("\\:<>|?*\"",Ch))return false;I++;
        }
        size_t N=I-Start;if(!N||(N==1&&Path[Start]=='.')||(N==2&&Path[Start]=='.'&&Path[Start+1]=='.')||Path[I-1]=='.'||Path[I-1]==' ')return false;
        if(Path[I])I++;
    }return true;
}
static mdo_result BuSave(void* User,const mdo_sdk_context_v1* C,const xvalue* P,xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;
    if(!BuSavePath(BuText(C,P,"path")))return BuFail(E,"save_path_invalid","Choose a relative destination inside the original workspace without traversal, controls or ambiguous names",422);
    xvalue* Query=V->Object(),*Browser=NULL,*Snapshot=NULL,*Args=NULL;
    if(!Query||!BuPut(C,Query,"action","get")||!BuPut(C,Query,"browser",BuText(C,P,"browser"))){V->Release(Query);return BuFail(E,"allocation_failed","Cannot check the original browser workspace",500);}
    mdo_result Code=BuTabs(NULL,C,Query,&Browser,E);V->Release(Query);Query=NULL;if(Code!=MDO_RESULT_OK)goto done;
    const xvalue* Scope=V->Get(Browser,"context");const char* Workspace=BuText(C,Scope,"workspace");
    if(!Workspace||!Workspace[0]){Code=BuFail(E,"workspace_context_missing","This older tab has no frozen workspace; select an explicit workspace before saving, or use a newly created tab",409);goto done;}
    if(!BuEqual(C->Workspace,Workspace)||!BuEqual(C->Namespace,BuText(C,Scope,"namespace"))||!BuEqual(C->MemberId,BuText(C,Scope,"member"))){
        Code=BuFail(E,"context_mismatch","Save belongs to the original task workspace and private subtask",409);goto done;
    }
    Query=V->Object();if(!Query||!BuPut(C,Query,"path",".")){Code=BuFail(E,"allocation_failed","Cannot snapshot the original workspace",500);goto done;}
    Code=mdoCall(C,"files.stat",Query,&Snapshot,E);if(Code!=MDO_RESULT_OK)goto done;
    const char* Revision=BuText(C,Snapshot,"revision");
    if(!Revision||!Revision[0]||strlen(Revision)>160){Code=BuFail(E,"workspace_snapshot_invalid","The original workspace has no usable physical snapshot",409);goto done;}
    Args=V->Clone(P);if(!Args||!BuPut(C,Args,"root_snapshot",Revision)){Code=BuFail(E,"allocation_failed","Cannot retain the original workspace snapshot",500);goto done;}
    if(!V->Get(Args,"timeout_ms")){
        xvalue* Timeout=V->Int(60000);bool Stored=Timeout&&V->Set(Args,"timeout_ms",Timeout);V->Release(Timeout);
        if(!Stored){Code=BuFail(E,"allocation_failed","Cannot retain the save deadline",500);goto done;}
    }
    const char* Fields[]={"download","path","root_snapshot","timeout_ms"};
    Code=BuQueue(C,Args,"save_download",Fields,4,Result,E);
done:
    V->Release(Query);V->Release(Browser);V->Release(Snapshot);V->Release(Args);return Code;
}
