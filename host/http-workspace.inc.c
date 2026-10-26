/* UI continuations carry the native caller's frozen task workspace. A project
 * default is insufficient when a session was created with its own root. The
 * local SDK is already authorized to read native paths; this is path context,
 * not an authorization override. Never accept a relative replacement root. */
    const xvalue* FrozenWorkspace=SdkGet(Scope,"workspace");
    if(FrozenWorkspace){
        cstr Frozen=SdkText(FrozenWorkspace);bool Valid=Frozen&&strlen(Frozen)<=4096;
        if(Valid)for(cstr At=Frozen;*At;At++)if((unsigned char)*At<32||(unsigned char)*At==127){Valid=false;break;}
        if(Valid&&Frozen[0])Valid=xrtPathIsRooted(Frozen);
#if defined(_WIN32)
        if(Valid&&Frozen[0]){
            bool Drive=((Frozen[0]>='A'&&Frozen[0]<='Z')||(Frozen[0]>='a'&&Frozen[0]<='z'))&&Frozen[1]==':'&&(Frozen[2]=='\\'||Frozen[2]=='/');
            bool Unc=(Frozen[0]=='\\'&&Frozen[1]=='\\')||(Frozen[0]=='/'&&Frozen[1]=='/');
            Valid=Drive||Unc; /* C:relative and \root depend on process state. */
        }
#endif
        if(!Valid){xrtFree(Workspace);MdoApiJsonBodyUnit(&Body);return MdoApiReplyError(C,422,"sdk_context_invalid","Workspace context must be an absolute path without control characters",NULL);}
        if(Frozen[0]){char* Resolved=xrtPathAbs(Frozen);if(!Resolved){xrtFree(Workspace);MdoApiJsonBodyUnit(&Body);return MdoApiReplyError(C,500,"sdk_context_unavailable","Cannot resolve the original task workspace",NULL);}xrtFree(Workspace);Workspace=Resolved;}
    }
