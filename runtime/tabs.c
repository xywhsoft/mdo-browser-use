/* Included into the plugin's single C entry. No global borrowed SDK context. */
static bool BuOwned(const mdo_sdk_context_v1* C,const xvalue* Browser) {
    const mdo_values_v1* V=C->Api->Values;const xvalue* Scope=V->Get(Browser,"context");
    return BuEqual(C->ExtensionId,BuText(C,Browser,"owner"))&&
        BuEqual(C->DeviceId,BuText(C,Scope,"device"))&&
        BuEqual(C->ProjectId&&*C->ProjectId?C->ProjectId:"default",BuText(C,Scope,"project"))&&
        BuEqual(C->SessionId,BuText(C,Scope,"session"));
}
static bool BuUrl(const char* Url) {
    if(!Url)return true;
    const char* Authority=NULL;
    if(!strncmp(Url,"https://",8))Authority=Url+8;
    else if(!strncmp(Url,"http://",7))Authority=Url+7;
    else return false;
    const char* End=Authority+strcspn(Authority,"/?#");
    if(End==Authority)return false;
    for(const char* P=Url;*P;P++)if((unsigned char)*P<=32||*P=='\\')return false;
    for(const char* P=Authority;P<End;P++)if(*P=='@')return false;
    return true;
}
static mdo_result BuTabs(void* U,const mdo_sdk_context_v1* C,const xvalue* P,
                         xvalue** Result,mdo_error_v1* E) {
    (void)U;const mdo_values_v1* V=C->Api->Values;
    const char* Action=BuText(C,P,"action"),*Id=BuText(C,P,"browser"),*Client=BuText(C,P,"client");
    if(!strcmp(Action,"clients")){
        xvalue* Clients=NULL;mdo_result Code=mdoCall(C,"ui.clients",NULL,&Clients,E);
        if(Code!=MDO_RESULT_OK)return Code;
        *Result=V->Object();bool Ok=*Result&&V->Set(*Result,"clients",Clients);V->Release(Clients);
        return Ok?MDO_RESULT_OK:BuFail(E,"allocation_failed","Cannot collect display clients",500);
    }
    if(!strcmp(Action,"list")){
        xvalue* Rows=NULL; mdo_result Code=mdoCall(C,"browser.list",P,&Rows,E);
        if(Code!=MDO_RESULT_OK)return Code;
        xvalue* Out=V->Array();
        for(size_t I=0;I<V->Count(Rows);I++){
            const xvalue* Row=V->At(Rows,I);
            if(BuOwned(C,Row)&&!V->Append(Out,Row)){V->Release(Out);V->Release(Rows);return BuFail(E,"allocation_failed","Cannot collect browser tabs",500);}
        }
        V->Release(Rows);*Result=V->Object();bool Ok=*Result&&Out&&V->Set(*Result,"tabs",Out);
        V->Release(Out);return Ok?MDO_RESULT_OK:BuFail(E,"allocation_failed","Cannot collect browser tabs",500);
    }
    if(!strcmp(Action,"create")){
        if(!Client||!*Client)return BuFail(E,"client_required","Select an explicit active display client",422);
        if(!BuUrl(BuText(C,P,"url")))return BuFail(E,"url_invalid","Use an HTTP(S) URL without embedded credentials or control characters",422);
        xvalue* Clients=NULL;mdo_result Code=mdoCall(C,"ui.clients",NULL,&Clients,E);
        if(Code!=MDO_RESULT_OK)return Code;
        bool Found=false;for(size_t I=0;I<V->Count(Clients);I++)
            if(BuEqual(Client,BuText(C,V->At(Clients,I),"id")))Found=true;
        V->Release(Clients);
        if(!Found)return BuFail(E,"client_missing","The selected display client is not online",409);
        xvalue* Args=V->Object();const char* Fields[]={"client","url","key","title"};
        for(size_t I=0;I<4;I++){const xvalue* Field=V->Get(P,Fields[I]);if(Field)V->Set(Args,Fields[I],Field);}
        Code=mdoCall(C,"browser.create",Args,Result,E);V->Release(Args);return Code;
    }
    if(!Id||!*Id)return BuFail(E,"browser_required","Supply a browser identity",422);
    xvalue* Args=V->Object();BuPut(C,Args,"id",Id);
    xvalue* Browser=NULL;mdo_result Code=mdoCall(C,"browser.get",Args,&Browser,E);
    if(Code!=MDO_RESULT_OK){V->Release(Args);return Code;}
    if(!BuOwned(C,Browser)){V->Release(Args);V->Release(Browser);return BuFail(E,"context_mismatch","Browser belongs to another extension or task",409);}
    if(!strcmp(Action,"get")){*Result=Browser;V->Release(Args);return MDO_RESULT_OK;}
    if(!strcmp(Action,"close")){Code=mdoCall(C,"browser.close",Args,Result,E);V->Release(Args);V->Release(Browser);return Code;}
    V->Release(Args);Args=V->Object();
    BuPut(C,Args,"type","mdo.browser");BuPut(C,Args,"client",BuText(C,Browser,"client"));
    BuPut(C,Args,"key",Id);xvalue* Resource=V->Object();BuPut(C,Resource,"id",Id);
    V->Set(Args,"resource",Resource);V->Release(Resource);BuPut(C,Args,"reveal","activate");
    Code=mdoCall(C,"panel.open",Args,Result,E);V->Release(Args);V->Release(Browser);return Code;
}
