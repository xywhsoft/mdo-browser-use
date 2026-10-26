/* Picker tickets are native view/document identities, never filesystem paths. */
static mdo_result BuChooser(void* User,const mdo_sdk_context_v1* C,const xvalue* P,xvalue** Result,mdo_error_v1* E){
    (void)User;const mdo_values_v1* V=C->Api->Values;bool Cancel=BuEqual(BuText(C,P,"action"),"cancel");
    if(Cancel&&!V->Get(P,"chooser"))return BuFail(E,"chooser_required","Choose the exact pending picker identity",422);
    if(!Cancel&&V->Get(P,"chooser"))return BuFail(E,"arguments_invalid","Listing pickers does not cancel a ticket",422);
    const char* Fields[]={"action","chooser","timeout_ms"};
    return BuQueue(C,P,"chooser",Fields,3,Result,E);
}
