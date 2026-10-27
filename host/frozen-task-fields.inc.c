/* Local UI continuations retain native subtask/member identity. A server-bound
 * ApplicationContext below remains authoritative for remote/native callers. */
    const cstr TaskKeys[]={"namespace","member"};const char* TaskValues[2]={NULL,NULL};
    for(size_t At=0;At<2;At++)if(SdkGet(Scope,TaskKeys[At])){
        TaskValues[At]=SdkText(SdkGet(Scope,TaskKeys[At]));bool Valid=TaskValues[At]&&strlen(TaskValues[At])<=512;
        if(Valid)for(cstr Ch=TaskValues[At];*Ch;Ch++)if((unsigned char)*Ch<32||(unsigned char)*Ch==127){Valid=false;break;}
        if(!Valid){MdoApiJsonBodyUnit(&Body);xrtFree(Workspace);return MdoApiReplyError(C,422,"sdk_context_invalid","Namespace and member context must be strings up to 512 bytes without control characters",NULL);}
    }
    Native.Namespace=TaskValues[0];Native.MemberId=TaskValues[1];
