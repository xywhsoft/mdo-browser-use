#ifndef BU_VALUES_H
#define BU_VALUES_H
#include "mdo/sdk.h"
#include <stdio.h>
#include <string.h>

static bool BuVisualId(const char* Id){
    if(!Id||strlen(Id)!=51||strncmp(Id,"vs-",3))return false;
    for(size_t I=3;I<51;I++)if(!((Id[I]>='0'&&Id[I]<='9')||(Id[I]>='a'&&Id[I]<='f')))return false;
    return true;
}

static const char* BuText(const mdo_sdk_context_v1* C,const xvalue* P,const char* Key) {
    return C->Api->Values->Text(C->Api->Values->Get(P,Key));
}
static bool BuPut(const mdo_sdk_context_v1* C,xvalue* P,const char* Key,const char* Text) {
    const mdo_values_v1* V=C->Api->Values;
    xvalue* Value=V->String(Text?Text:"");
    bool Ok=Value&&V->Set(P,Key,Value);
    V->Release(Value);return Ok;
}
static mdo_result BuFail(mdo_error_v1* E,const char* Code,const char* Message,unsigned Status) {
    if(E){memset(E,0,sizeof(*E));E->Size=sizeof(*E);E->Status=Status;
        snprintf(E->Code,sizeof(E->Code),"%s",Code);
        snprintf(E->Message,sizeof(E->Message),"%s",Message);
        snprintf(E->Stage,sizeof(E->Stage),"browser-use");}
    return MDO_RESULT_ERROR;
}
static bool BuEqual(const char* A,const char* B) {return !strcmp(A?A:"",B?B:"");}

/* Enforce the subset used by our generated contracts. Validation is at the C
 * boundary, not just in the UI. Unsupported schema shapes fail closed. */
static bool BuValidate(const mdo_sdk_context_v1* C,const xvalue* Schema,
                       const xvalue* Value,unsigned Depth) {
    const mdo_values_v1* V=C->Api->Values;
    const char* Type=BuText(C,Schema,"type");
    if(!Schema||Depth>8||!Type)return false;
    mdo_value_kind Kind=V->Kind(Value);
    if(!strcmp(Type,"object")){
        if(Kind!=MDO_VALUE_OBJECT)return false;
        const xvalue* Properties=V->Get(Schema,"properties"),*Required=V->Get(Schema,"required");
        bool Additional=true;V->Boolean(V->Get(Schema,"additionalProperties"),&Additional);
        for(size_t I=0;I<V->Count(Required);I++){
            const char* Key=V->Text(V->At(Required,I));
            if(!Key||!V->Get(Value,Key))return false;
        }
        for(size_t I=0;I<V->Count(Value);I++){
            const char* Key=V->Key(Value,I);const xvalue* Property=Key?V->Get(Properties,Key):NULL;
            if(!Property){if(!Additional)return false;continue;}
            if(!BuValidate(C,Property,V->Get(Value,Key),Depth+1))return false;
        }
        return true;
    }
    if(!strcmp(Type,"string")){
        const char* Text=V->Text(Value);uint64_t Min=0,Max=UINT64_MAX;
        if(Kind!=MDO_VALUE_STRING||!Text)return false;
        V->Unsigned(V->Get(Schema,"minLength"),&Min);V->Unsigned(V->Get(Schema,"maxLength"),&Max);
        size_t Bytes=strlen(Text);if(Bytes<Min||Bytes>Max)return false;
        const xvalue* Enum=V->Get(Schema,"enum");
        if(Enum){bool Found=false;for(size_t I=0;I<V->Count(Enum);I++)
            if(BuEqual(Text,V->Text(V->At(Enum,I))))Found=true;if(!Found)return false;}
        return true;
    }
    if(!strcmp(Type,"boolean"))return Kind==MDO_VALUE_BOOL;
    if(!strcmp(Type,"array")){
        uint64_t Min=0,Max=UINT64_MAX;
        if(Kind!=MDO_VALUE_ARRAY||!V->Get(Schema,"items"))return false;
        V->Unsigned(V->Get(Schema,"minItems"),&Min);V->Unsigned(V->Get(Schema,"maxItems"),&Max);
        if(V->Count(Value)<Min||V->Count(Value)>Max)return false;
        for(size_t I=0;I<V->Count(Value);I++)if(!BuValidate(C,V->Get(Schema,"items"),V->At(Value,I),Depth+1))return false;
        return true;
    }
    if(!strcmp(Type,"integer")){
        int64_t Number=0,Min=INT64_MIN,Max=INT64_MAX;
        if(!V->Integer(Value,&Number))return false;
        V->Integer(V->Get(Schema,"minimum"),&Min);V->Integer(V->Get(Schema,"maximum"),&Max);
        return Number>=Min&&Number<=Max;
    }
    return false;
}
#endif
