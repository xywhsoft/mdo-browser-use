/* Compile the production fragment against deterministic allocation/filesystem
 * fault seams. Actual XRT/OS semantics are covered by test_directory_snapshots. */
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
typedef const char* cstr;
typedef uint64_t uint64;typedef uint32_t uint32;typedef uint8_t uint8;
typedef struct {const char* Data;size_t Size;} xstrview;
#define XRT_STR_LITERAL(S) ((xstrview){S,sizeof(S)-1})
#define XRT_SHA256_SIZE 32
#define XFILE_TYPE_FILE 1
#define XFILE_TYPE_DIRECTORY 2
#define XFILE_TYPE_LINK 3
#define XFILE_INFO_IDENTITY 64
#define XFILE_INFO_SIZE 1
#define XFILE_INFO_MODIFY_TIME 8
#define XFILE_INFO_CHANGE_TIME 32
#define XFILE_READ 1
#define XFILE_NOFOLLOW 2
#define XFILE_SHARE_READ 1
#define XDIR_ENTRY_UTF8 1
typedef struct {unsigned Type,Available;uint64 Size,Device,Identity;int64_t Modified,Changed;} xfileinfo;
typedef struct {unsigned Flags,Share;} xfileoptions;
typedef struct Handle {unsigned Node,At;} *xroot,*xfile,*xdir;
typedef struct {xstrview Name;unsigned Flags;} xdirentry;
typedef enum {XDIR_NEXT_ERROR=-1,XDIR_NEXT_END=0,XDIR_NEXT_ITEM=1} xdirnext;
typedef struct {char Code[80];unsigned Status;bool MayHaveCompleted;} mdo_error_v1;
typedef struct {unsigned unused;} mdo_sdk_context_v1;
typedef struct {uint64 Value;} xsha256;
typedef struct Value xvalue;
typedef struct Field {char Key[48];xvalue* Value;struct Field* Next;} Field;
struct Value {unsigned References,Kind;uint64 Number;char* Text;Field* Fields;};
static long FailAt=-1,Attempts=0,Live=0;static unsigned Handles=0,Scenario=0,Continues=0,Reads=0,PathStats=0;
static bool Triggered=false,DirectoryOpened=false;
static void* xrtMalloc(size_t N){
    if(Attempts++==FailAt){Triggered=true;return NULL;}void* P=malloc(N?N:1);if(P)Live++;return P;
}
static void* xrtCalloc(size_t N,size_t S){void* P=xrtMalloc(N*S);if(P)memset(P,0,N*S);return P;}
static void xrtFree(void* P){if(P){assert(Live>0);Live--;free(P);}}
static void xrtSecureZero(void* P,size_t N){memset(P,0,N);}
static xstrview view(cstr S){return (xstrview){S,strlen(S)};}
static bool xrtUtf8Valid(xstrview S,void* Ignore){(void)Ignore;return S.Data&&S.Size;}
static xvalue* new_value(unsigned Kind){xvalue* V=xrtCalloc(1,sizeof(*V));if(V){V->References=1;V->Kind=Kind;}return V;}
static xvalue* xrtValueObject(void){return new_value(1);}
static xvalue* xrtValueArray(void){return new_value(2);}
static xvalue* xrtValueInt(int64_t N){xvalue* V=new_value(3);if(V)V->Number=(uint64)N;return V;}
static xvalue* string_value(cstr S){
    xvalue* V=new_value(4);if(!V)return NULL;V->Text=xrtMalloc(strlen(S)+1);
    if(!V->Text){xrtFree(V);return NULL;}strcpy(V->Text,S);return V;
}
static void xrtValueRelease(xvalue* V){
    if(!V)return;assert(V->References);if(--V->References)return;
    for(Field *F=V->Fields,*Next;F;F=Next){Next=F->Next;xrtValueRelease(F->Value);xrtFree(F);}
    xrtFree(V->Text);xrtFree(V);
}
static bool xrtValueObjectSet(xvalue* V,xstrview Key,xvalue* Item){
    if(!V||!Item||Key.Size>=48)return false;Field* F=xrtCalloc(1,sizeof(*F));if(!F)return false;
    memcpy(F->Key,Key.Data,Key.Size);F->Value=Item;Item->References++;F->Next=V->Fields;V->Fields=F;return true;
}
static bool xrtValueObjectSetNew(xvalue* V,xstrview Key,xvalue* Item){bool Ok=xrtValueObjectSet(V,Key,Item);xrtValueRelease(Item);return Ok;}
static bool xrtValueArrayAppendNew(xvalue* V,xvalue* Item){return xrtValueObjectSetNew(V,view("item"),Item);}
static const xvalue* SdkGet(const xvalue* V,cstr Key){for(Field* F=V?V->Fields:NULL;F;F=F->Next)if(!strcmp(F->Key,Key))return F->Value;return NULL;}
static cstr SdkField(const xvalue* V,cstr Key){const xvalue* F=SdkGet(V,Key);return F?F->Text:NULL;}
static bool SdkUInt(const xvalue* V,uint64* Out){if(!V||V->Kind!=3)return false;*Out=V->Number;return true;}
static bool SdkPut(xvalue* V,cstr K,cstr S){return xrtValueObjectSetNew(V,view(K),string_value(S));}
static bool SdkPutUInt(xvalue* V,cstr K,uint64 N){return xrtValueObjectSetNew(V,view(K),xrtValueInt((int64_t)N));}
static bool MdoApplicationError(mdo_error_v1* E,cstr Code,cstr Message,cstr Stage,unsigned Status,bool Completed){
    (void)Message;(void)Stage;strcpy(E->Code,Code);E->Status=Status;E->MayHaveCompleted=Completed;return false;
}
static bool SdkNativeFailure(mdo_error_v1* E,cstr Stage){return MdoApplicationError(E,"native",Stage,Stage,500,false);}
static bool BuFileContinue(const mdo_sdk_context_v1* C,mdo_error_v1* E){
    (void)C;Continues++;if(Scenario==6&&Continues>=5)return MdoApplicationError(E,"cancelled","","",499,false);return true;
}
static char* SdkNativePath(const mdo_sdk_context_v1* C,const xvalue* P,bool Storage,mdo_error_v1* E){
    (void)C;(void)P;(void)Storage;(void)E;char* S=xrtMalloc(9);if(S)strcpy(S,"/fixture");return S;
}
static void xrtSha256Init(xsha256* S){S->Value=5381;}
static bool xrtSha256Update(xsha256* S,const void* Bytes,size_t N){const unsigned char* B=Bytes;for(size_t I=0;I<N;I++)S->Value=S->Value*33+B[I];return true;}
static bool xrtSha256Final(xsha256* S,uint8* Out){for(unsigned I=0;I<32;I++)Out[I]=(uint8)(S->Value>>(I%8)*8);return true;}
static xfileinfo info(unsigned Node){
    xfileinfo I={0};I.Available=XFILE_INFO_IDENTITY|XFILE_INFO_SIZE|XFILE_INFO_MODIFY_TIME|XFILE_INFO_CHANGE_TIME;
    I.Type=Node<2?XFILE_TYPE_DIRECTORY:XFILE_TYPE_FILE;I.Identity=Node+1;I.Device=1;I.Modified=7;I.Changed=9;I.Size=Node==2?3:0;
    if(Scenario==9&&Node==0)I.Available=0;
    if(Scenario==10&&Node==0)I.Type=XFILE_TYPE_LINK;
    if(Scenario==11&&Node==2)I.Type=XFILE_TYPE_LINK;
    if(Scenario==12&&Node==2)I.Size=2;
    return I;
}
static struct Handle* handle(unsigned Node){struct Handle* H=xrtCalloc(1,sizeof(*H));if(H){H->Node=Node;Handles++;}return H;}
static bool close_handle(struct Handle* H){assert(H&&Handles);Handles--;xrtFree(H);return true;}
static xroot xrtRootOpen(cstr P){(void)P;return handle(0);}
static xroot xrtRootOpenIn(xroot Parent,cstr Name){assert(Parent->Node==0&&!strcmp(Name,"nest"));DirectoryOpened=true;return handle(1);}
static bool xrtRootClose(xroot Root){unsigned Node=Root->Node;close_handle(Root);return !(Scenario==15&&Node==1);}
static bool xrtPathStat(cstr P,bool Follow,xfileinfo* Out){
    (void)P;assert(!Follow);*Out=info(0);if(Scenario==1&&PathStats++)Out->Identity=77;return true;
}
static bool xrtRootStat(xroot Root,cstr Name,bool Follow,xfileinfo* Out){
    assert(!Follow);unsigned Node=Root->Node;
    if(strcmp(Name,".")){
        if(!strcmp(Name,"nest"))Node=1;else if(!strcmp(Name,"b.bin"))Node=2;else {assert(!strcmp(Name,"a.bin"));Node=3;}
    }
    *Out=info(Node);
    if(Scenario==2&&Node==1&&DirectoryOpened&&!strcmp(Name,"."))Out->Identity=77;
    if(Scenario==4&&DirectoryOpened&&!strcmp(Name,"nest"))Out->Type=XFILE_TYPE_LINK;
    if(Scenario==5&&Node==0&&Reads)Out->Modified++;
    return true;
}
static xdir xrtRootDirOpen(xroot Root,cstr Path,unsigned Flags){assert(!strcmp(Path,".")&&!Flags);return handle(Root->Node);}
static xdirnext xrtDirNext(xdir Dir,xdirentry* Out){
    static const char* Names[]={"b.bin","nest","a.bin"};
    if(Dir->Node==1&&Scenario==7)return XDIR_NEXT_ERROR;
    if(Dir->At>=(Dir->Node?1:2))return XDIR_NEXT_END;
    Out->Name=view(Names[Dir->Node?2:Dir->At]);Dir->At++;Out->Flags=Scenario==13?0:XDIR_ENTRY_UTF8;return XDIR_NEXT_ITEM;
}
static bool xrtDirClose(xdir Dir){close_handle(Dir);return Scenario!=14;}
static void xrtFileOptionsInit(xfileoptions* O){memset(O,0,sizeof(*O));}
static xfile xrtRootFileOpen(xroot Parent,cstr Name,const xfileoptions* O){
    assert(O->Flags==(XFILE_READ|XFILE_NOFOLLOW)&&O->Share==XFILE_SHARE_READ);
    if(!strcmp(Name,"b.bin")){assert(Parent->Node==0);return handle(2);}assert(Parent->Node==1&&!strcmp(Name,"a.bin"));return handle(3);
}
static bool xrtFileStat(xfile F,xfileinfo* Out){*Out=info(F->Node);if(Scenario==3&&Reads)Out->Modified++;return true;}
static bool xrtRead(xfile F,void* Bytes,size_t Limit,size_t* Read){
    assert(Limit>=3);*Read=F->Node==2&&!F->At?3:0;memcpy(Bytes,"abc",*Read);F->At++;Reads++;return true;
}
static bool xrtClose(xfile F){unsigned Node=F->Node;close_handle(F);return !(Scenario==8&&Node==2);}
#include "../../host/files-tree.inc.c"

static bool execute(long Fault,unsigned Mode){
    assert(!Live&&!Handles);FailAt=Fault;Attempts=0;Triggered=false;Scenario=Mode;
    Continues=Reads=PathStats=0;DirectoryOpened=false;
    xvalue Path={.References=1,.Kind=4,.Text="/fixture"};Field Param={.Key="path",.Value=&Path};
    xvalue Input={.References=1,.Kind=1,.Fields=&Param};mdo_sdk_context_v1 Context={0};mdo_error_v1 Error={0};xvalue* Result=NULL;
    bool Ok=BuFileTree(&Context,&Input,&Result,&Error);
    if(Ok){
        assert(Result&&!Error.Code[0]&&!Triggered&&!Mode);
        const xvalue* Count=SdkGet(Result,"file_count");assert(Count&&Count->Number==2);
        assert(strlen(SdkField(Result,"revision"))==67);
    }else{
        if(Result||!Error.Code[0]||Error.MayHaveCompleted){fprintf(stderr,"Incomplete failure: allocation=%ld scenario=%u\n",Fault,Mode);abort();}
        if(!Mode)assert(Triggered);
    }
    xrtValueRelease(Result);assert(!Handles&&!Live);return Ok;
}
int main(void){
    unsigned Failures=0;bool Succeeded=false;
    for(long At=0;At<2048;At++){if(execute(At,0)){Succeeded=true;break;}Failures++;}
    assert(Succeeded&&Failures>100);
    for(unsigned Mode=1;Mode<=15;Mode++)assert(!execute(-1,Mode));
    printf("%u allocation failure positions and 15 replacement/cancellation/cleanup faults returned no partial manifest and leaked no handles\n",Failures);
    return 0;
}
