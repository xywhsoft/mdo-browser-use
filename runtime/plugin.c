#include "values.h"
#include "tabs.c"
#include "observe.c"

typedef struct BuService {
    const char *Id,*Description,*Input,*Output;
    mdo_tool_effects Effects;
    mdo_service_execute_v1 Execute;
} BuService;
#include "contracts.inc.h"

static mdo_result BuDispatch(void* User,const mdo_sdk_context_v1* C,const xvalue* P,
                             xvalue** Result,mdo_error_v1* E) {
    const BuService* Service=User;const mdo_values_v1* V=C->Api->Values;
    xvalue* Schema=V->Parse(Service->Input);
    bool Valid=Schema&&BuValidate(C,Schema,P,0);V->Release(Schema);
    if(!Valid)return BuFail(E,"arguments_invalid","Arguments do not match the Browser Use service contract",422);
    return Service->Execute(NULL,C,P,Result,E);
}
static mdo_result BuRegister(const mdo_host_services_v1* Host,
                             const mdo_registrar_v1* Registrar,void** Data,char* Error,size_t Capacity) {
    (void)Host;(void)Data;
    for(size_t I=0;I<sizeof(BuServices)/sizeof(BuServices[0]);I++){
        const BuService* Spec=&BuServices[I];
        mdo_service_v1 Service={sizeof(Service),MDO_SDK_VERSION,Spec->Id,Spec->Description,
            Spec->Input,Spec->Output,Spec->Effects,(void*)Spec,BuDispatch,0};
        mdo_result Code=Registrar->AddService(Registrar->Context,&Service,Error,Capacity);
        if(Code!=MDO_RESULT_OK)return Code;
    }
    return MDO_RESULT_OK;
}
static const mdo_module_v1 BuModule={MDO_V1_HEADER(mdo_module_v1),
    MDO_PLUGIN_ID,"Browser Use","Browser tools and shared application services",MDO_PLUGIN_VERSION,
    MDO_CAPABILITY_APPLICATION,NULL,0,BuRegister,NULL};
const mdo_module_v1* mdoModuleEntry(void) {return &BuModule;}
