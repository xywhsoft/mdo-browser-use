/* Legacy browser root recovery reads the original persisted task, never the
 * selected chat/project default. Lookup is read-only; adoption is explicit,
 * revision checked and fill-only. Keep a physical pin for subsequent saves. */
#include "../../include/mdo/sessions.h"
static bool BuWorkspaceBound(const mdo_sdk_context_v1* C,const xvalue* Row,mdo_error_v1* E){
    const xvalue* Scope=SdkGet(Row,"context");
    if(!Row||!WbBound(C,Row)||!WbEqual(C->Namespace,SdkField(Scope,"namespace"))||!WbEqual(C->MemberId,SdkField(Scope,"member")))
        return WbFail(E,"context_mismatch","Browser recovery belongs to its original owner, device, task and private member",409);
    const cstr TextKeys[]={"workspace","namespace","member"};
    for(size_t I=0;I<3;I++)if(SdkGet(Scope,TextKeys[I])&&!SdkField(Scope,TextKeys[I]))
        return WbFail(E,"browser_context_invalid","Preserve this browser: its original context is not readable",409);
    cstr Workspace=SdkField(Scope,"workspace");
    if(Workspace&&Workspace[0])return WbFail(E,"browser_workspace_bound","This browser already has a workspace; it cannot be rebound",409);
    return true;
}
static bool BuWorkspaceNamed(cstr Path,const xfileinfo* Opened){
    xfileinfo Named={0};return xrtPathStat(Path,false,&Named)&&Named.Type==XFILE_TYPE_DIRECTORY&&
        (Named.Available&XFILE_INFO_IDENTITY)&&Named.Device==Opened->Device&&Named.Identity==Opened->Identity;
}
static bool BuBrowserWorkspace(const mdo_sdk_context_v1* C,cstr Method,const xvalue* P,xvalue** R,mdo_error_v1* E){
    bool Adopt=!strcmp(Method,"browser.workspace.adopt"),Confirmed=false,Ok=false,Locked=false,Changed=false;
    cstr Id=SdkField(P,"id");xvalue *Seed=NULL,*Candidate=NULL,*Metadata=NULL,*Next=NULL,*Reply=NULL;
    xroot Root=NULL;MdoSessionCatalog* Catalog=NULL;xfileinfo Directory={0};MdoSessionInfo Session={0};
    if(!SdkIdValid(Id))return WbFail(E,"browser_arguments_invalid","Supply an owned browser identity",422);
    if(Adopt&&(!xrtValueGetBool(SdkGet(P,"confirmed"),&Confirmed)||!Confirmed))
        return WbFail(E,"browser_workspace_confirmation","Review and explicitly confirm the original task directory before binding it",422);
    xrtMutexLock(Workbench.Lock);Locked=true;
    const xvalue* Initial=WbFind(Workbench.State,"browsers",Id);
    if(!BuWorkspaceBound(C,Initial,E))goto done;
    Seed=xrtValueClone(Initial);xrtMutexUnlock(Workbench.Lock);Locked=false;if(!Seed)goto done;
    if(!C->SessionId||!SdkIdValid(C->SessionId)){WbFail(E,"browser_workspace_task_missing","This legacy browser has no original task; no directory was inferred",409);goto done;}
    xwork_error Error;Catalog=MdoSessionCatalogSnapshot(&Error);
    if(!Catalog){WbFail(E,"browser_workspace_task_unavailable","Cannot read the original task catalog; no model was opened",503);goto done;}
    bool Found=false;
    for(size_t I=0;I<MdoSessionCatalogCount(Catalog);I++){
        Session.Size=sizeof(Session);
        if(!MdoSessionCatalogAt(Catalog,I,&Session))goto done;
        if(WbEqual(Session.ProjectId,C->ProjectId&&C->ProjectId[0]?C->ProjectId:"default")&&WbEqual(Session.Id,C->SessionId)){Found=true;break;}
    }
    MdoSessionCatalogRelease(Catalog);Catalog=NULL;
    if(!Found||Session.Status==MDO_SESSION_TRASH||!Session.WorkspaceRoot[0]||!xrtPathIsAbs(Session.WorkspaceRoot)){
        WbFail(E,"browser_workspace_task_missing","The original task directory cannot be established; no current-chat fallback is allowed",409);goto done;
    }
    if(!BuFileContinue(C,E))goto done;
    Root=xrtRootOpen(Session.WorkspaceRoot);
    if(!Root||!xrtRootStat(Root,".",false,&Directory)||Directory.Type!=XFILE_TYPE_DIRECTORY||!(Directory.Available&XFILE_INFO_IDENTITY)||
       !BuWorkspaceNamed(Session.WorkspaceRoot,&Directory)){
        WbFail(E,"browser_workspace_unavailable","Restore the original task directory before binding it; no directory was created",409);goto done;
    }
    Metadata=SdkFileInfo(&Directory);cstr Snapshot=SdkField(Metadata,"revision");uint64 Revision=0;
    if(!Snapshot||!BuPublishRootMatches(Snapshot,&Directory)||!SdkUInt(SdkGet(Seed,"revision"),&Revision))goto done;
    Candidate=xrtValueObject();
    if(!Candidate||!SdkPut(Candidate,"id",Id)||!SdkPutUInt(Candidate,"revision",Revision)||!SdkPut(Candidate,"project",Session.ProjectId)||
       !SdkPut(Candidate,"session",Session.Id)||!SdkPutUInt(Candidate,"session_revision",Session.Revision)||
       !SdkPut(Candidate,"workspace",Session.WorkspaceRoot)||!SdkPut(Candidate,"root_snapshot",Snapshot))goto done;
    if(!Adopt){Reply=Candidate;Candidate=NULL;Ok=true;goto done;}
    uint64 ExpectedSession=0;
    if(!SdkUInt(SdkGet(P,"session_revision"),&ExpectedSession)||ExpectedSession!=Session.Revision||
       !WbEqual(SdkField(P,"workspace"),Session.WorkspaceRoot)||!BuPublishRootMatches(SdkField(P,"root_snapshot"),&Directory)){
        WbFail(E,"browser_workspace_conflict","The reviewed task or physical directory changed; read the original task again",412);goto done;
    }
    xrtMutexLock(Workbench.Lock);Locked=true;
    const xvalue* Current=WbFind(Workbench.State,"browsers",Id);
    if(!BuWorkspaceBound(C,Current,E)||!WbRevision(P,Current,E)||!BuFileContinue(C,E))goto done;
    if(!BuWorkspaceNamed(Session.WorkspaceRoot,&Directory)){WbFail(E,"browser_workspace_conflict","The reviewed physical directory changed; binding was not saved",412);goto done;}
    uint64 CurrentRevision=0,StateRevision=0;
    if(!SdkUInt(SdkGet(Current,"revision"),&CurrentRevision)||CurrentRevision==UINT64_MAX||
       !SdkUInt(SdkGet(Workbench.State,"revision"),&StateRevision)||StateRevision==UINT64_MAX)goto done;
    Next=xrtValueClone(Workbench.State);if(!Next)goto done;
    xvalue* Row=WbFind(Next,"browsers",Id),*Scope=(xvalue*)SdkGet(Row,"context");
    if(!Scope||!SdkPut(Scope,"workspace",Session.WorkspaceRoot)||!SdkPut(Scope,"workspace_snapshot",Snapshot)||
       !SdkPutUInt(Row,"revision",CurrentRevision+1)||!SdkPutUInt(Next,"revision",StateRevision+1))goto done;
    Reply=xrtValueClone(Row);if(!Reply)goto done;
    if(!WbSave(Next)){WbFail(E,"workbench_save_failed","Browser workspace binding could not be saved; no in-memory binding changed",503);goto done;}
    xrtValueRelease(Workbench.State);Workbench.State=Next;Next=NULL;Ok=true;Changed=true;
done:
    if(Locked)xrtMutexUnlock(Workbench.Lock);
    if(Root&&!xrtRootClose(Root)&&!Ok&&!E->Code[0])WbFail(E,"browser_workspace_unavailable","Cannot close the inspected task root",503);
    if(Catalog)MdoSessionCatalogRelease(Catalog);
    xrtValueRelease(Seed);xrtValueRelease(Candidate);xrtValueRelease(Metadata);xrtValueRelease(Next);
    if(Ok){*R=Reply;if(Changed){xvalue* Facts=xrtValueObject();if(Facts&&SdkPut(Facts,"method",Method)&&SdkPut(Facts,"id",Id))MdoApplicationEvent("workbench.changed",C,Facts);xrtValueRelease(Facts);}}
    else{xrtValueRelease(Reply);if(!E->Code[0])WbFail(E,"browser_workspace_failed","Cannot prepare a complete workspace recovery receipt; no binding changed",503);}
    return Ok;
}
