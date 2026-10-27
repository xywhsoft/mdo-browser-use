#include <stdlib.h>

static mdo_result BuScreenshot(void* User,const mdo_sdk_context_v1* C,const xvalue* P,xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;const xvalue* Target=V->Get(P,"target");const char* Mode=BuText(C,P,"mode");
    if(Target){
        if(!V->Get(P,"document"))return BuFail(E,"document_required","Observe the current document before an element screenshot",422);
        if(Mode&&!BuEqual(Mode,"element"))return BuFail(E,"target_invalid","An element target requires element screenshot mode",422);
        bool Selector=false;const char* Keys[]={"role","name","label","placeholder","text","testid","css","ref"};
        for(size_t I=0;I<8;I++)if(V->Get(Target,Keys[I]))Selector=true;
        if(!Selector||(V->Get(Target,"ref")&&V->Count(Target)>(V->Get(Target,"frame")?2:1)))return BuFail(E,"target_invalid","Choose one precise screenshot target",422);
    }else if(BuEqual(Mode,"element"))return BuFail(E,"target_required","Select one observed screenshot target",422);
    const char* Fields[]={"document","target","mode","format","max_width","max_height","max_bytes","timeout_ms"};
    return BuQueue(C,P,"capture",Fields,8,Result,E);
}
static int BuBase64Digit(unsigned char C){
    if(C>='A'&&C<='Z')return C-'A';if(C>='a'&&C<='z')return C-'a'+26;
    if(C>='0'&&C<='9')return C-'0'+52;if(C=='+')return 62;if(C=='/')return 63;return -1;
}
static unsigned char* BuImageDecode(const char* Text,size_t* Bytes){
    *Bytes=0;if(!Text)return NULL;size_t N=strlen(Text);if(N<32||N>349528||N%4)return NULL;
    size_t Size=N/4*3-(Text[N-1]=='=')-(Text[N-2]=='=');if(Size<24||Size>262144)return NULL;
    unsigned char* Out=malloc(Size);if(!Out)return NULL;size_t Used=0;
    for(size_t I=0;I<N;I+=4){
        int A=BuBase64Digit(Text[I]),B=BuBase64Digit(Text[I+1]);
        int D2=Text[I+2]=='='?-2:BuBase64Digit(Text[I+2]),D3=Text[I+3]=='='?-2:BuBase64Digit(Text[I+3]);
        bool Last=I+4==N;
        if(A<0||B<0||D2== -1||D3== -1||(!Last&&(D2<0||D3<0))||
           (D2== -2&&(D3!= -2||(B&15)))||(D2>=0&&D3== -2&&(D2&3))){free(Out);return NULL;}
        if(Used>=Size){free(Out);return NULL;}Out[Used++]=(unsigned char)((A<<2)|(B>>4));
        if(D2>=0){if(Used>=Size){free(Out);return NULL;}Out[Used++]=(unsigned char)((B<<4)|(D2>>2));
            if(D3>=0){if(Used>=Size){free(Out);return NULL;}Out[Used++]=(unsigned char)((D2<<6)|D3);}}
    }
    if(Used!=Size){free(Out);return NULL;}*Bytes=Size;return Out;
}
static uint32_t BuImageBig(const unsigned char* P){return ((uint32_t)P[0]<<24)|((uint32_t)P[1]<<16)|((uint32_t)P[2]<<8)|P[3];}
static bool BuImageSize(const unsigned char* P,size_t N,const char* Mime,uint32_t* Width,uint32_t* Height){
    *Width=*Height=0;
    if(BuEqual(Mime,"image/png")){
        static const unsigned char Magic[]={137,80,78,71,13,10,26,10};
        if(N<33||memcmp(P,Magic,8)||memcmp(P+12,"IHDR",4)||BuImageBig(P+8)!=13)return false;
        *Width=BuImageBig(P+16);*Height=BuImageBig(P+20);
    }else if(BuEqual(Mime,"image/jpeg")){
        if(N<24||P[0]!=255||P[1]!=216||P[N-2]!=255||P[N-1]!=217)return false;
        size_t I=2;while(I+3<N){
            if(P[I++]!=255)return false;while(I<N&&P[I]==255)I++;if(I>=N)return false;
            unsigned Marker=P[I++];if(Marker==216||Marker==217||(Marker>=208&&Marker<=215))continue;
            if(I+2>N)return false;size_t Size=(size_t)P[I]*256+P[I+1];if(Size<2||Size>N-I)return false;
            if((Marker>=192&&Marker<=195)||(Marker>=197&&Marker<=199)||(Marker>=201&&Marker<=203)||(Marker>=205&&Marker<=207)){
                if(Size<7)return false;*Height=(uint32_t)P[I+3]*256+P[I+4];*Width=(uint32_t)P[I+5]*256+P[I+6];break;}
            I+=Size;
        }
    }else return false;
    return *Width&&*Height&&*Width<=2048&&*Height<=2048&&(uint64_t)(*Width)*(*Height)<=4194304;
}
static mdo_result BuToolImage(const mdo_sdk_context_v1* C,const xvalue* Value,mdo_result_writer_v1* Writer,char* Error,size_t Capacity){
    const mdo_values_v1* V=C->Api->Values;const xvalue* Image=V->Get(Value,"image");
    const xvalue* Visual=V->Get(Value,"visual_reference");
    const char* Mime=BuText(C,Image,"media_type"),*Document=BuText(C,Value,"document");
    uint64_t Expected=0,W=0,H=0;size_t Size=0;uint32_t Width=0,Height=0;
    unsigned char* Bytes=BuImageDecode(BuText(C,Image,"base64"),&Size);
    bool Valid=Bytes&&Document&&Document[0]&&strlen(Document)<=100&&V->Unsigned(V->Get(Image,"bytes"),&Expected)&&Expected==Size&&
        V->Unsigned(V->Get(Image,"width"),&W)&&V->Unsigned(V->Get(Image,"height"),&H)&&BuImageSize(Bytes,Size,Mime,&Width,&Height)&&W==Width&&H==Height;
    if(Visual){
        const char* Id=BuText(C,Visual,"id");uint64_t VW=0,VH=0,Lease=0;
        Valid=Valid&&Id&&strlen(Id)==51&&!strncmp(Id,"vs-",3)&&BuEqual(BuText(C,Visual,"coordinates"),"image_pixels")&&
            V->Unsigned(V->Get(Visual,"width"),&VW)&&V->Unsigned(V->Get(Visual,"height"),&VH)&&VW==W&&VH==H&&
            V->Unsigned(V->Get(Visual,"expires_in_ms"),&Lease)&&Lease>0&&Lease<=60000;
        if(Valid)for(size_t I=3;I<51;I++)if(!((Id[I]>='0'&&Id[I]<='9')||(Id[I]>='a'&&Id[I]<='f')))Valid=false;
    }
    if(!Valid){free(Bytes);snprintf(Error,Capacity,"screenshot_invalid: Invalid screenshot image envelope");
        Writer->WriteText(Writer->Context,"{\"ok\":false,\"error\":{\"code\":\"screenshot_invalid\",\"message\":\"Invalid screenshot image envelope\"}}");Writer->SetSuccess(Writer->Context,false);return MDO_RESULT_ERROR;}
    xvalue* Meta=V->Object(),*Info=V->Object(),*Yes=V->Bool(true);V->Set(Meta,"ok",Yes);V->Release(Yes);BuPut(C,Meta,"document",Document);
    BuPut(C,Info,"media_type",Mime);const char* Keys[]={"width","height","bytes"};for(size_t I=0;I<3;I++)V->Set(Info,Keys[I],V->Get(Image,Keys[I]));
    V->Set(Meta,"image",Info);V->Release(Info);
    if(Visual){
        xvalue* Reference=V->Object();const char* Fields[]={"id","width","height","coordinates","expires_in_ms"};
        for(size_t I=0;I<5;I++)V->Set(Reference,Fields[I],V->Get(Visual,Fields[I]));
        V->Set(Meta,"visual_reference",Reference);V->Release(Reference);
    }
    char* Json=V->Json(Meta);V->Release(Meta);
    bool Ok=Json&&Writer->WriteText(Writer->Context,Json)&&Writer->SetImage&&Writer->SetImage(Writer->Context,Bytes,Size,Mime)&&Writer->SetSuccess(Writer->Context,true);
    free(Bytes);V->Free(Json);if(!Ok){snprintf(Error,Capacity,"browser_result_limit: Cannot publish screenshot bytes");return MDO_RESULT_LIMIT;}return MDO_RESULT_OK;
}
