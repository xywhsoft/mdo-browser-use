/* Called while Workbench.Lock is held. Browser commands enqueue under the same
 * lock, so no command can be admitted between successful closure and cleanup. */
static bool WbIsBrowserPanel(const xvalue* State,const xvalue* Panel){
    const xvalue* Type=WbFind(State,"types",SdkField(Panel,"type"));
    return WbEqual(SdkField(Type,"renderer"),"browser");
}
static bool WbPanelBrowserReference(const mdo_sdk_context_v1* C,const xvalue* State,const xvalue* Panel,mdo_error_v1* E){
    if(!WbIsBrowserPanel(State,Panel))return true;
    const xvalue* Resource=SdkGet(Panel,"resource");
    if(!SdkGet(Resource,"id"))return true; /* Empty panel creates its own tab. */
    cstr Id=SdkField(Resource,"id");
    const xvalue* Browser=WbFind(State,"browsers",Id);
    if(!Browser)return WbFail(E,"browser_missing","Browser session is unavailable; its panel cannot be reopened",404);
    return WbBound(C,Browser)||WbFail(E,"context_mismatch","Browser belongs to another extension or context",409);
}
static bool WbCloseBrowserPanels(const mdo_sdk_context_v1* C,xvalue* State,cstr Id,mdo_error_v1* E){
    xvalue* Kept=xrtValueArray();if(!Kept)return WbFail(E,"allocation_failed","Cannot prepare browser panel cleanup",500);
    const xvalue* Panels=SdkGet(State,"panels");
    for(size_t I=0;I<xrtValueCount(Panels);I++){
        const xvalue* Panel=xrtValueArrayGet(Panels,I);
        if(WbBound(C,Panel)&&WbIsBrowserPanel(State,Panel)&&WbEqual(SdkField(SdkGet(Panel,"resource"),"id"),Id))continue;
        if(!xrtValueArrayAppend(Kept,Panel)){xrtValueRelease(Kept);return WbFail(E,"allocation_failed","Cannot retain other panels",500);}
    }
    return xrtValueObjectSetNew(State,XRT_STR_LITERAL("panels"),Kept);
}
