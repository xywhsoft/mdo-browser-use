static mdo_result BuEvaluate(void* User,const mdo_sdk_context_v1* C,const xvalue* P,
                             xvalue** Result,mdo_error_v1* E){
    (void)User;const char* Script=BuText(C,P,"script"),*Frame=BuText(C,P,"frame");
    if(!Script||!Script[0]||strlen(Script)>65536)
        return BuFail(E,"script_arguments_invalid","JavaScript must be a function body of at most 64 KiB UTF-8",422);
    if(Frame&&!BuEqual(Frame,"f0")&&!BuText(C,P,"frame_document"))
        return BuFail(E,"frame_document_required","An iframe requires its observed native document identity",422);
    const char* Fields[]={"document","script","frame","frame_document","world","timeout_ms","max_bytes"};
    return BuQueue(C,P,"execute_js",Fields,7,Result,E);
}
